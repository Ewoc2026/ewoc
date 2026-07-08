package io.github.ewoc2026.ewoc

import io.github.ewoc2026.ewoc.ai.AiInputSnapshot
import io.github.ewoc2026.ewoc.ai.AiLiveMetrics
import io.github.ewoc2026.ewoc.ai.AiPhase
import io.github.ewoc2026.ewoc.ai.AiPolicyEvaluation
import io.github.ewoc2026.ewoc.ai.AiPresentationDecision
import io.github.ewoc2026.ewoc.ai.AiPresentationMessage
import io.github.ewoc2026.ewoc.ai.AiPresentedMessageRecord
import io.github.ewoc2026.ewoc.ai.AiReachability
import io.github.ewoc2026.ewoc.ai.AiRecentEmission
import io.github.ewoc2026.ewoc.ai.AiRecommendationCandidate
import io.github.ewoc2026.ewoc.ai.AiRecommendationType
import io.github.ewoc2026.ewoc.ai.AiSignalMeta
import io.github.ewoc2026.ewoc.ai.AiWearableNormalizationResult
import io.github.ewoc2026.ewoc.ai.AiWorkoutContext
import io.github.ewoc2026.ewoc.ai.shouldSuppressInRideCadenceCue
import io.github.ewoc2026.ewoc.ai.AiConnectivityQuality
import io.github.ewoc2026.ewoc.ai.AiContext
import kotlin.math.abs

/**
 * Configuration for AI refresh cadence and anti-spam retention.
 */
internal data class AiCoordinatorConfig(
    val sessionEvaluationMinIntervalMs: Long = 2_000L,
    val sessionMessageRetentionMs: Long = 45_000L,
    val recentHistoryRetentionMs: Long = 90_000L,
    val sessionCadenceGateTriggerRpm: Int = 5,
    val sessionCadenceGateRecoveryRpm: Int = 4,
    val sessionCadenceGateTriggerSustainMs: Long = 6_000L,
    val sessionCadenceGateRecoverySustainMs: Long = 3_000L,
) {
    init {
        require(sessionCadenceGateTriggerRpm > 0) {
            "sessionCadenceGateTriggerRpm must be > 0"
        }
        require(sessionCadenceGateRecoveryRpm in 0 until sessionCadenceGateTriggerRpm) {
            "sessionCadenceGateRecoveryRpm must be in [0, sessionCadenceGateTriggerRpm)"
        }
        require(sessionCadenceGateTriggerSustainMs >= 0L) {
            "sessionCadenceGateTriggerSustainMs must be >= 0"
        }
        require(sessionCadenceGateRecoverySustainMs >= 0L) {
            "sessionCadenceGateRecoverySustainMs must be >= 0"
        }
    }
}

/**
 * Encapsulates AI lifecycle orchestration and keeps ViewModel integration behavior-stable.
 *
 * The coordinator owns recommendation refresh cadence, policy/presentation pipeline,
 * and in-session suppression state.
 */
internal class AiCoordinator(
    private val uiState: AppUiState,
    private val evaluateRecommendations: (
        phase: AiPhase,
        snapshot: AiInputSnapshot,
        nowMillis: Long,
        recentSessionEmissions: List<AiRecentEmission>,
        wearableNormalization: AiWearableNormalizationResult?,
    ) -> List<AiRecommendationCandidate>,
    private val evaluatePolicy: (List<AiRecommendationCandidate>) -> AiPolicyEvaluation,
    private val adaptPresentation: (
        phase: AiPhase,
        evaluation: AiPolicyEvaluation,
        nowMillis: Long,
        recentPresented: List<AiPresentedMessageRecord>,
    ) -> AiPresentationDecision,
    private val logTelemetry: (
        snapshot: AiInputSnapshot,
        decision: AiPresentationDecision,
        timestampMillis: Long,
    ) -> Unit,
    private val formatMessage: (AiPresentationMessage) -> String,
    private val isErrorType: (AiRecommendationType) -> Boolean,
    private val trainerReachability: () -> AiReachability,
    private val heartRateReachability: () -> AiReachability,
    private val isHeartRateConnected: () -> Boolean,
    private val hasSelectedHrDevice: () -> Boolean,
    private val isMockTrainerModeActive: () -> Boolean,
    private val ftpWatts: () -> Int,
    private val config: AiCoordinatorConfig = AiCoordinatorConfig(),
    private val nowMillisProvider: () -> Long = System::currentTimeMillis,
) {
    private val recentSessionEmissions = mutableListOf<AiRecentEmission>()
    private val recentPresentedMessages = mutableListOf<AiPresentedMessageRecord>()
    private var lastSessionEvaluationMillis = 0L
    private var sessionMessageUpdatedAtMillis: Long? = null
    private var activeSessionMessage: AiPresentationMessage? = null
    private var cadenceGateEnabled = false
    private var cadenceGateDirection: SessionCadenceDirection? = null
    private var cadenceGateDeviationStartMillis: Long? = null
    private var cadenceGateRecoveryStartMillis: Long? = null
    private var cadenceGateViolationLatched = false
    private var cadenceGateRecovered = false

    fun refresh(
        phase: AiPhase?,
        force: Boolean = false,
        nowMillis: Long = nowMillisProvider(),
    ) {
        val currentPhase = phase ?: return
        if (!force && currentPhase == AiPhase.SESSION) {
            val elapsedSinceLast = nowMillis - lastSessionEvaluationMillis
            if (elapsedSinceLast in 0 until config.sessionEvaluationMinIntervalMs) {
                return
            }
        }
        if (currentPhase == AiPhase.SESSION) {
            lastSessionEvaluationMillis = nowMillis
        }

        pruneHistory(nowMillis)
        val wearableNormalization: AiWearableNormalizationResult? = null
        val snapshot = buildInputSnapshot(
            phase = currentPhase,
            nowMillis = nowMillis,
            wearableNormalization = wearableNormalization,
        )
        updateSessionCadenceGate(
            phase = currentPhase,
            snapshot = snapshot,
            nowMillis = nowMillis,
        )
        val evaluatedCandidates = evaluateRecommendations(
            currentPhase,
            snapshot,
            nowMillis,
            recentSessionEmissions.toList(),
            wearableNormalization,
        )
        val candidates = if (currentPhase == AiPhase.SESSION) {
            applySessionCadenceGate(evaluatedCandidates)
        } else {
            evaluatedCandidates
        }
        val policyEvaluation = evaluatePolicy(candidates)
        val decision = adaptPresentation(
            currentPhase,
            policyEvaluation,
            nowMillis,
            recentPresentedMessages.toList(),
        )
        logTelemetry(
            snapshot,
            decision,
            nowMillis,
        )
        applyPresentationDecision(
            phase = currentPhase,
            messages = decision.messages,
            keepExistingMessage = decision.keepExistingMessage,
            nowMillis = nowMillis,
            snapshot = snapshot,
        )
    }

    fun onSessionStarted() {
        clearPhaseMessage(AiPhase.SESSION)
        resetSessionCadenceGateState()
        recentSessionEmissions.clear()
        recentPresentedMessages.removeAll { record ->
            record.phase == AiPhase.SESSION
        }
    }

    fun onActivityUnbound() {
        resetSessionCadenceGateState()
    }

    fun onClosed() {
        resetSessionCadenceGateState()
    }

    fun clearPhaseMessage(phase: AiPhase) {
        uiState.aiAssistantUiState.clearPhaseMessage(phase)
        if (phase == AiPhase.SESSION) {
            sessionMessageUpdatedAtMillis = null
            activeSessionMessage = null
        }
    }

    private fun buildInputSnapshot(
        phase: AiPhase,
        nowMillis: Long,
        wearableNormalization: AiWearableNormalizationResult?,
    ): AiInputSnapshot {
        val bikeData = uiState.bikeData.value
        val runnerState = uiState.runner.value
        val selectedWorkout = uiState.selectedWorkout.value
        val selectedWorkoutName = selectedWorkout?.name?.trim()?.takeIf { it.isNotEmpty() }
            ?: uiState.selectedWorkoutFileName.value
                ?.substringAfterLast('/')
                ?.substringBeforeLast('.', missingDelimiterValue = "")
                ?.takeIf { it.isNotEmpty() }
        val summary = uiState.summary.value
        val plannedTss = uiState.selectedWorkoutPlannedTss.value
        val completionRatio = calculateCompletionRatio(
            plannedTss = plannedTss,
            summary = summary,
        )
        val effectiveHeartRate = uiState.heartRate.value ?: bikeData?.heartRateBpm

        return AiInputSnapshot(
            phase = phase,
            signalMeta = AiSignalMeta(
                captureTimestampMillis = nowMillis,
                maxExpectedAgeMillis = 5_000L,
            ),
            liveMetrics = AiLiveMetrics(
                actualPowerWatts = bikeData?.instantaneousPowerW,
                targetPowerWatts = runnerState.targetPowerWatts ?: uiState.lastTargetPower.value,
                targetCadenceRpm = runnerState.targetCadence,
                cadenceRpm = bikeData?.instantaneousCadenceRpm?.toInt(),
                speedKmh = bikeData?.instantaneousSpeedKmh,
                heartRateBpm = effectiveHeartRate,
                workoutElapsedSec = runnerState.workoutElapsedSec,
                workoutPaused = runnerState.paused,
            ),
            connectivityQuality = AiConnectivityQuality(
                trainerReachability = trainerReachability(),
                hrReachability = heartRateReachability(),
                trainerSignalStale = phase == AiPhase.SESSION && !uiState.ftmsReady.value,
                hrSignalStale = phase == AiPhase.SESSION && hasSelectedHrDevice() && !isHeartRateConnected(),
            ),
            context = AiContext(
                configuredFtpWatts = ftpWatts(),
                workout = AiWorkoutContext(
                    workoutId = uiState.selectedWorkoutFileName.value,
                    workoutName = selectedWorkoutName,
                    plannedTss = plannedTss,
                ),
                lastSessionActualTss = summary?.actualTss,
                lastSessionCompletionRatio = completionRatio,
                mockTrainerModeActive = isMockTrainerModeActive(),
            ),
            wearableSnapshot = wearableNormalization?.snapshot,
        )
    }

    private fun updateSessionCadenceGate(
        phase: AiPhase,
        snapshot: AiInputSnapshot,
        nowMillis: Long,
    ) {
        if (phase != AiPhase.SESSION || shouldSuppressInRideCadenceCue(snapshot.liveMetrics)) {
            resetSessionCadenceGateState()
            return
        }
        // If the current step defines no cadence target, any retained cadence cue is stale —
        // clear it immediately instead of keeping it alive via the message fallback in
        // effectiveCadenceTargetForGate.
        val liveStepCadenceTarget = snapshot.liveMetrics.targetCadenceRpm?.takeIf { it > 0 }
        if (liveStepCadenceTarget == null) {
            if (activeSessionMessage?.type == AiRecommendationType.CADENCE) {
                clearPhaseMessage(AiPhase.SESSION)
            }
            resetSessionCadenceGateState()
            return
        }
        val liveCadence = snapshot.liveMetrics.cadenceRpm
        val targetCadence = effectiveCadenceTargetForGate(snapshot)
        if (liveCadence == null || targetCadence == null) {
            resetSessionCadenceGateState()
            return
        }

        cadenceGateEnabled = true
        val wasLatched = cadenceGateViolationLatched
        val direction = resolveCadenceDirection(
            targetCadenceRpm = targetCadence,
            liveCadenceRpm = liveCadence,
        )
        when {
            direction != null -> {
                cadenceGateRecovered = false
                cadenceGateRecoveryStartMillis = null
                val sameDirection = cadenceGateDirection == direction
                if (!sameDirection || cadenceGateDeviationStartMillis == null) {
                    cadenceGateDirection = direction
                    cadenceGateDeviationStartMillis = nowMillis
                    cadenceGateViolationLatched = false
                } else {
                    val elapsedDeviation = nowMillis - requireNotNull(cadenceGateDeviationStartMillis)
                    if (!cadenceGateViolationLatched &&
                        elapsedDeviation >= config.sessionCadenceGateTriggerSustainMs
                    ) {
                        cadenceGateViolationLatched = true
                    }
                }
            }
            abs(liveCadence - targetCadence) <= config.sessionCadenceGateRecoveryRpm -> {
                cadenceGateDirection = null
                cadenceGateDeviationStartMillis = null
                val recoveryStart = cadenceGateRecoveryStartMillis ?: nowMillis.also {
                    cadenceGateRecoveryStartMillis = it
                }
                val elapsedRecovery = nowMillis - recoveryStart
                if (cadenceGateViolationLatched &&
                    elapsedRecovery >= config.sessionCadenceGateRecoverySustainMs
                ) {
                    cadenceGateViolationLatched = false
                    cadenceGateRecovered = true
                }
            }
            else -> {
                cadenceGateRecoveryStartMillis = null
                if (!cadenceGateViolationLatched) {
                    cadenceGateDirection = null
                    cadenceGateDeviationStartMillis = null
                }
            }
        }

        if (wasLatched && !cadenceGateViolationLatched) {
            resetSessionCadenceSuppressionHistory()
        }
    }

    private fun applySessionCadenceGate(
        candidates: List<AiRecommendationCandidate>,
    ): List<AiRecommendationCandidate> {
        if (!cadenceGateEnabled) return candidates
        return candidates.filter { candidate ->
            if (candidate.type != AiRecommendationType.CADENCE) {
                return@filter true
            }
            if (!cadenceGateViolationLatched) {
                return@filter false
            }
            val templateDirection = cadenceDirectionForTemplate(candidate.payload.templateKey)
            templateDirection == null || templateDirection == cadenceGateDirection
        }
    }

    private fun effectiveCadenceTargetForGate(snapshot: AiInputSnapshot): Int? {
        return snapshot.liveMetrics.targetCadenceRpm
            ?.takeIf { it > 0 }
            ?: activeSessionMessage?.templateArgs?.get("cadence_target_rpm")
                ?.toIntOrNull()
                ?.takeIf { it > 0 }
            ?: activeSessionMessage?.templateArgs?.get("cadence_threshold_rpm")
                ?.toIntOrNull()
                ?.takeIf { it > 0 }
    }

    private fun resolveCadenceDirection(
        targetCadenceRpm: Int,
        liveCadenceRpm: Int,
    ): SessionCadenceDirection? {
        val delta = liveCadenceRpm - targetCadenceRpm
        return when {
            delta <= -config.sessionCadenceGateTriggerRpm -> SessionCadenceDirection.BELOW
            delta >= config.sessionCadenceGateTriggerRpm -> SessionCadenceDirection.ABOVE
            else -> null
        }
    }

    private fun cadenceDirectionForTemplate(templateKey: String): SessionCadenceDirection? {
        return when (templateKey) {
            "ai.session.cadence_increase_slightly" -> SessionCadenceDirection.BELOW
            "ai.session.cadence_reduce_slightly" -> SessionCadenceDirection.ABOVE
            else -> null
        }
    }

    private fun resetSessionCadenceGateState() {
        cadenceGateEnabled = false
        cadenceGateDirection = null
        cadenceGateDeviationStartMillis = null
        cadenceGateRecoveryStartMillis = null
        cadenceGateViolationLatched = false
        cadenceGateRecovered = false
    }

    private fun applyPresentationDecision(
        phase: AiPhase,
        messages: List<AiPresentationMessage>,
        keepExistingMessage: Boolean,
        nowMillis: Long,
        snapshot: AiInputSnapshot,
    ) {
        if (messages.isNotEmpty()) {
            val primary = messages.first()
            setPhaseMessage(
                phase = phase,
                message = formatMessage(primary),
                isError = isErrorType(primary.type),
                nowMillis = nowMillis,
                menuTemplateKey = if (phase == AiPhase.MENU) primary.templateKey else null,
                sessionSourceMessage = if (phase == AiPhase.SESSION) primary else null,
            )
            recordPresentationMessages(
                messages = messages,
                nowMillis = nowMillis,
            )
            return
        }

        val shouldKeepSessionMessage =
            phase == AiPhase.SESSION &&
                keepExistingMessage &&
                sessionMessageUpdatedAtMillis?.let { updatedAt ->
                    nowMillis - updatedAt <= config.sessionMessageRetentionMs
                } == true
        if (shouldKeepSessionMessage) {
            maybeRefreshRetainedSessionMessage(
                snapshot = snapshot,
                nowMillis = nowMillis,
            )
            return
        }
        clearPhaseMessage(phase)
    }

    /**
     * Keeps retained in-session prompts aligned with live metrics while cooldown blocks re-emit.
     */
    private fun maybeRefreshRetainedSessionMessage(
        snapshot: AiInputSnapshot,
        nowMillis: Long,
    ) {
        when (activeSessionMessage?.type) {
            AiRecommendationType.CADENCE -> {
                maybeRefreshRetainedSessionCadenceMessage(
                    snapshot = snapshot,
                    nowMillis = nowMillis,
                )
            }
            else -> Unit
        }
    }

    /**
     * Keeps retained cadence prompts aligned with latest cadence while cooldown blocks re-emit.
     */
    private fun maybeRefreshRetainedSessionCadenceMessage(
        snapshot: AiInputSnapshot,
        nowMillis: Long,
    ) {
        val currentMessage = activeSessionMessage ?: return
        if (currentMessage.templateKey != "ai.session.cadence_increase_slightly" &&
            currentMessage.templateKey != "ai.session.cadence_reduce_slightly"
        ) {
            return
        }
        if (shouldSuppressInRideCadenceCue(snapshot.liveMetrics)) {
            clearPhaseMessage(AiPhase.SESSION)
            return
        }
        if (cadenceGateRecovered) {
            clearPhaseMessage(AiPhase.SESSION)
            return
        }

        val liveCadence = snapshot.liveMetrics.cadenceRpm ?: return
        val targetCadence = snapshot.liveMetrics.targetCadenceRpm
            ?.takeIf { it > 0 }
            ?: currentMessage.templateArgs["cadence_target_rpm"]?.toIntOrNull()
            ?: currentMessage.templateArgs["cadence_threshold_rpm"]?.toIntOrNull()
            ?: return
        val refreshedTargetCadence = targetCadence.toString()
        val displayedCadence = currentMessage.templateArgs["cadence_rpm"]?.toIntOrNull()
        val displayedTargetCadence = currentMessage.templateArgs["cadence_target_rpm"]?.toIntOrNull()
        val retentionDecision = if (cadenceGateEnabled) {
            resolveSessionCadenceRetention(
                targetRpm = targetCadence,
                recoveryToleranceRpm = -1,
                liveCadenceRpm = liveCadence,
                displayedCadenceRpm = displayedCadence,
                displayedTargetRpm = displayedTargetCadence,
            )
        } else {
            resolveSessionCadenceRetention(
                targetRpm = targetCadence,
                recoveryToleranceRpm = config.sessionCadenceGateRecoveryRpm,
                liveCadenceRpm = liveCadence,
                displayedCadenceRpm = displayedCadence,
                displayedTargetRpm = displayedTargetCadence,
            )
        }
        when (retentionDecision) {
            SessionCadenceRetentionDecision.CLEAR -> {
                clearPhaseMessage(AiPhase.SESSION)
                return
            }
            SessionCadenceRetentionDecision.KEEP -> return
            SessionCadenceRetentionDecision.REFRESH -> Unit
        }

        val refreshedTemplateKey = when (
            resolveCadenceDirection(
                targetCadenceRpm = targetCadence,
                liveCadenceRpm = liveCadence,
            )
        ) {
            SessionCadenceDirection.BELOW -> "ai.session.cadence_increase_slightly"
            SessionCadenceDirection.ABOVE -> "ai.session.cadence_reduce_slightly"
            null -> currentMessage.templateKey
        }
        val refreshedThreshold = when (refreshedTemplateKey) {
            "ai.session.cadence_increase_slightly" ->
                targetCadence - config.sessionCadenceGateTriggerRpm
            "ai.session.cadence_reduce_slightly" ->
                targetCadence + config.sessionCadenceGateTriggerRpm
            else -> targetCadence
        }
        val refreshedMessage = currentMessage.copy(
            templateKey = refreshedTemplateKey,
            templateArgs = currentMessage.templateArgs +
                mapOf(
                    "cadence_rpm" to liveCadence.toString(),
                    "cadence_target_rpm" to refreshedTargetCadence,
                    "cadence_threshold_rpm" to refreshedThreshold.toString(),
                ),
        )
        setPhaseMessage(
            phase = AiPhase.SESSION,
            message = formatMessage(refreshedMessage),
            isError = false,
            nowMillis = nowMillis,
            sessionSourceMessage = refreshedMessage,
        )
    }

    private fun recordPresentationMessages(
        messages: List<AiPresentationMessage>,
        nowMillis: Long,
    ) {
        messages.forEach { message ->
            recentPresentedMessages += AiPresentedMessageRecord(
                phase = message.phase,
                type = message.type,
                presentedAtMillis = nowMillis,
            )
            if (message.phase == AiPhase.SESSION) {
                recentSessionEmissions += AiRecentEmission(
                    type = message.type,
                    emittedAtMillis = nowMillis,
                )
            }
        }
        pruneHistory(nowMillis)
    }

    private fun pruneHistory(nowMillis: Long) {
        val historyWindowStart = nowMillis - config.recentHistoryRetentionMs
        recentPresentedMessages.removeAll { record ->
            record.presentedAtMillis < historyWindowStart
        }
        recentSessionEmissions.removeAll { emission ->
            emission.emittedAtMillis < historyWindowStart
        }
    }

    private fun setPhaseMessage(
        phase: AiPhase,
        message: String,
        isError: Boolean,
        nowMillis: Long,
        menuTemplateKey: String? = null,
        sessionSourceMessage: AiPresentationMessage? = null,
    ) {
        uiState.aiAssistantUiState.setPhaseMessage(
            phase = phase,
            message = message,
            isError = isError,
            menuTemplateKey = menuTemplateKey,
        )
        if (phase == AiPhase.SESSION) {
            sessionMessageUpdatedAtMillis = nowMillis
            activeSessionMessage = sessionSourceMessage
        }
    }

    private fun calculateCompletionRatio(
        plannedTss: Double?,
        summary: io.github.ewoc2026.ewoc.session.SessionSummary?,
    ): Double? {
        val planned = plannedTss?.takeIf { it > 0.0 } ?: return null
        val actual = summary?.actualTss ?: return null
        return (actual / planned).coerceAtLeast(0.0)
    }

    /**
     * Clears cadence-only anti-spam history after a true cadence recovery.
     */
    private fun resetSessionCadenceSuppressionHistory() {
        val filtered = filterSessionSuppressionHistoryByType(
            recentEmissions = recentSessionEmissions,
            recentPresented = recentPresentedMessages,
            type = AiRecommendationType.CADENCE,
        )
        recentSessionEmissions.clear()
        recentSessionEmissions += filtered.recentEmissions
        recentPresentedMessages.clear()
        recentPresentedMessages += filtered.recentPresented
    }
}

private enum class SessionCadenceDirection {
    BELOW,
    ABOVE,
}

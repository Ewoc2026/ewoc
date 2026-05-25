package com.example.ergometerapp.ai

import kotlin.math.abs
import kotlin.math.max

/**
 * Configurable thresholds for deterministic local AI recommendation rules.
 *
 * Values are intentionally conservative defaults and should be tuned only
 * after ride-based validation data is available.
 */
data class AiRuleConfig(
    val preRideLowReadinessScoreThreshold: Double = 50.0,
    val preRideLowRecoveryScoreThreshold: Double = 50.0,
    val preRideShortSleepMinutesThreshold: Int = 360,
    val preRideHighPlannedTssThreshold: Double = 80.0,
    val preRideRecentLoadRatioThreshold: Double = 1.25,
    val inRidePowerDeviationPctThreshold: Double = 7.0,
    val inRidePowerMonitoringEnabled: Boolean = false,
    val inRideLowCadenceRpmThreshold: Int = 75,
    val inRideCadenceTargetToleranceRpm: Int = 5,
    val inRideHighHeartRateBpmThreshold: Int = 175,
    val postRideHighTssDeltaThreshold: Double = 20.0,
    val postRideLowCompletionRatioThreshold: Double = 0.90,
    val inRideMaxRecommendationsPerMinute: Int = 1,
    val inRideSameTypeCooldownSec: Int = 60,
    val wearableFreshnessMaxAgeMillis: Long = 18 * 60 * 60 * 1000L,
) {
    init {
        require(inRideMaxRecommendationsPerMinute > 0) {
            "inRideMaxRecommendationsPerMinute must be > 0"
        }
        require(inRideSameTypeCooldownSec >= 0) {
            "inRideSameTypeCooldownSec must be >= 0"
        }
        require(inRideCadenceTargetToleranceRpm >= 0) {
            "inRideCadenceTargetToleranceRpm must be >= 0"
        }
        require(wearableFreshnessMaxAgeMillis > 0L) {
            "wearableFreshnessMaxAgeMillis must be > 0"
        }
    }
}

/**
 * A previously emitted recommendation used by in-ride rate limiting.
 */
data class AiRecentEmission(
    val type: AiRecommendationType,
    val emittedAtMillis: Long,
)

/**
 * Local rule-first recommendation engine for pre-ride, in-ride and post-ride.
 *
 * The engine is deterministic: equal inputs produce equal outputs.
 * Policy filtering and UI suppression are separate phases implemented later.
 */
class AiRecommendationEngine(
    private val config: AiRuleConfig = AiRuleConfig(),
) {

    /**
     * Evaluates rule candidates for the snapshot without in-ride rate limits.
     */
    fun evaluate(
        snapshot: AiInputSnapshot,
        nowMillis: Long = snapshot.signalMeta.captureTimestampMillis,
        wearableNormalization: AiWearableNormalizationResult? = null,
    ): List<AiRecommendationCandidate> {
        val effectiveWearableSnapshot = effectiveWearableSnapshot(
            snapshot = snapshot,
            wearableNormalization = wearableNormalization,
        )
        val baseCandidates = when (snapshot.phase) {
            AiPhase.MENU -> evaluatePreRide(snapshot, effectiveWearableSnapshot)
            AiPhase.SESSION -> evaluateInRide(snapshot)
            AiPhase.SUMMARY -> evaluatePostRide(snapshot, effectiveWearableSnapshot)
        }

        return baseCandidates
            .map { candidate ->
                candidate.copy(
                    confidence = applyConfidenceDowngrade(
                        baseConfidence = candidate.confidence,
                        snapshot = snapshot,
                        nowMillis = nowMillis,
                    ),
                )
            }
            .sortedWith(CANDIDATE_COMPARATOR)
    }

    /**
     * Evaluates rule candidates and applies in-ride rate limits when needed.
     */
    fun evaluateWithInRideLimits(
        snapshot: AiInputSnapshot,
        nowMillis: Long,
        recentEmissions: List<AiRecentEmission>,
        wearableNormalization: AiWearableNormalizationResult? = null,
    ): List<AiRecommendationCandidate> {
        val evaluated = evaluate(
            snapshot = snapshot,
            nowMillis = nowMillis,
            wearableNormalization = wearableNormalization,
        )
        if (snapshot.phase != AiPhase.SESSION) return evaluated
        return applyInRideRateLimits(
            candidates = evaluated,
            nowMillis = nowMillis,
            recentEmissions = recentEmissions,
        )
    }

    private fun evaluatePreRide(
        snapshot: AiInputSnapshot,
        wearableSnapshot: AiWearableSnapshot?,
    ): List<AiRecommendationCandidate> {
        val candidates = mutableListOf<AiRecommendationCandidate>()

        if (hasTrainerConnectivityRisk(snapshot)) {
            candidates += AiRecommendationCandidate(
                phase = AiPhase.MENU,
                type = AiRecommendationType.CONNECTIVITY,
                priority = AiRecommendationPriority.CRITICAL,
                confidence = AiQualityClass.HIGH,
                rationaleKeys = listOf("connectivity.trainer_unstable"),
                payload = AiRecommendationPayload(
                    templateKey = "ai.menu.connectivity_check_trainer",
                ),
            )
        }

        val wearable = wearableSnapshot
        val lowWearableRecovery = wearable != null && (
            (wearable.readinessScore != null &&
                wearable.readinessScore <= config.preRideLowReadinessScoreThreshold) ||
                (wearable.recoveryScore != null &&
                    wearable.recoveryScore <= config.preRideLowRecoveryScoreThreshold) ||
                (wearable.sleepDurationMinutes != null &&
                    wearable.sleepDurationMinutes < config.preRideShortSleepMinutesThreshold)
            )

        val highLoadAndHardWorkout = snapshot.context.workout.plannedTss?.let { plannedTss ->
            plannedTss >= config.preRideHighPlannedTssThreshold &&
                isRecentLoadHigh(snapshot.context.history)
        } ?: false

        if (lowWearableRecovery || highLoadAndHardWorkout) {
            val rationale = buildList {
                if (lowWearableRecovery) add("readiness.wearable_low")
                if (highLoadAndHardWorkout) add("load.recent_high_and_workout_hard")
            }
            candidates += AiRecommendationCandidate(
                phase = AiPhase.MENU,
                type = AiRecommendationType.READINESS,
                priority = AiRecommendationPriority.HIGH,
                confidence = AiQualityClass.HIGH,
                rationaleKeys = rationale,
                payload = AiRecommendationPayload(
                    templateKey = "ai.menu.readiness_reduce_intensity",
                ),
            )
        }

        return candidates
    }

    private fun evaluateInRide(snapshot: AiInputSnapshot): List<AiRecommendationCandidate> {
        val candidates = mutableListOf<AiRecommendationCandidate>()
        val live = snapshot.liveMetrics

        if (hasTrainerConnectivityRisk(snapshot) && !snapshot.context.mockTrainerModeActive) {
            candidates += AiRecommendationCandidate(
                phase = AiPhase.SESSION,
                type = AiRecommendationType.CONNECTIVITY,
                priority = AiRecommendationPriority.HIGH,
                confidence = AiQualityClass.HIGH,
                rationaleKeys = listOf("connectivity.session_unstable"),
                payload = AiRecommendationPayload(
                    templateKey = "ai.session.connectivity_stabilize",
                ),
            )
        }

        // TODO(ai): Re-enable pacing/power-drifting rule after cadence-priority interaction retuning.
        if (config.inRidePowerMonitoringEnabled) {
            val actualPower = live.actualPowerWatts
            val targetPower = live.targetPowerWatts
            if (actualPower != null && targetPower != null && targetPower > 0) {
                val deviationPct = abs(actualPower - targetPower) * 100.0 / targetPower
                if (deviationPct >= config.inRidePowerDeviationPctThreshold) {
                    candidates += AiRecommendationCandidate(
                        phase = AiPhase.SESSION,
                        type = AiRecommendationType.PACING,
                        priority = AiRecommendationPriority.MEDIUM,
                        confidence = AiQualityClass.HIGH,
                        rationaleKeys = listOf("power.deviation_threshold_crossed"),
                        payload = AiRecommendationPayload(
                            templateKey = "ai.session.pacing_hold_target",
                            templateArgs = mapOf("deviation_pct" to deviationPct.toInt().toString()),
                        ),
                    )
                }
            }
        }

        val cadence = live.cadenceRpm
        val cadenceTarget = explicitCadenceTargetRpm(live.targetCadenceRpm)
        if (!shouldSuppressInRideCadenceCue(live) && cadence != null && cadenceTarget != null) {
            val cadenceBandRpm = max(1, config.inRideCadenceTargetToleranceRpm)
            val cadenceDelta = cadence - cadenceTarget
            if (abs(cadenceDelta) >= cadenceBandRpm) {
                val guidance = resolveCadenceGuidance(cadenceDelta)
                val triggerBoundary = cadenceTarget + (guidance.direction.multiplier * cadenceBandRpm)
                candidates += AiRecommendationCandidate(
                    phase = AiPhase.SESSION,
                    type = AiRecommendationType.CADENCE,
                    priority = AiRecommendationPriority.MEDIUM,
                    confidence = AiQualityClass.HIGH,
                    rationaleKeys = listOf(guidance.rationaleKey),
                    payload = AiRecommendationPayload(
                        templateKey = guidance.templateKey,
                        templateArgs = mapOf(
                            "cadence_rpm" to cadence.toString(),
                            "cadence_target_rpm" to cadenceTarget.toString(),
                            "cadence_threshold_rpm" to triggerBoundary.toString(),
                        ),
                    ),
                )
            }
        }

        val heartRate = live.heartRateBpm
        if (heartRate != null && heartRate >= config.inRideHighHeartRateBpmThreshold) {
            candidates += AiRecommendationCandidate(
                phase = AiPhase.SESSION,
                type = AiRecommendationType.SAFETY,
                priority = AiRecommendationPriority.HIGH,
                confidence = AiQualityClass.HIGH,
                rationaleKeys = listOf("heart_rate.high_threshold"),
                payload = AiRecommendationPayload(
                    templateKey = "ai.session.safety_reduce_effort",
                    templateArgs = mapOf("hr_bpm" to heartRate.toString()),
                ),
            )
        }

        return candidates
    }

    private fun evaluatePostRide(
        snapshot: AiInputSnapshot,
        wearableSnapshot: AiWearableSnapshot?,
    ): List<AiRecommendationCandidate> {
        val candidates = mutableListOf<AiRecommendationCandidate>()
        val plannedTss = snapshot.context.workout.plannedTss
        val actualTss = snapshot.context.lastSessionActualTss
        val completionRatio = snapshot.context.lastSessionCompletionRatio

        val highTssDelta = plannedTss != null &&
            actualTss != null &&
            (actualTss - plannedTss) >= config.postRideHighTssDeltaThreshold

        val lowCompletion = completionRatio != null &&
            completionRatio < config.postRideLowCompletionRatioThreshold

        val wearable = wearableSnapshot
        val lowWearableRecovery = wearable != null && (
            (wearable.readinessScore != null &&
                wearable.readinessScore <= config.preRideLowReadinessScoreThreshold) ||
                (wearable.recoveryScore != null &&
                    wearable.recoveryScore <= config.preRideLowRecoveryScoreThreshold)
            )

        if (highTssDelta || lowCompletion || lowWearableRecovery) {
            val rationale = buildList {
                if (highTssDelta) add("summary.tss_delta_high")
                if (lowCompletion) add("summary.completion_low")
                if (lowWearableRecovery) add("summary.recovery_low")
            }
            candidates += AiRecommendationCandidate(
                phase = AiPhase.SUMMARY,
                type = AiRecommendationType.RECOVERY,
                priority = AiRecommendationPriority.HIGH,
                confidence = AiQualityClass.HIGH,
                rationaleKeys = rationale,
                payload = AiRecommendationPayload(
                    templateKey = "ai.summary.recovery_reduce_next_load",
                ),
            )
        }

        return candidates
    }

    private fun applyInRideRateLimits(
        candidates: List<AiRecommendationCandidate>,
        nowMillis: Long,
        recentEmissions: List<AiRecentEmission>,
    ): List<AiRecommendationCandidate> {
        if (candidates.isEmpty()) return emptyList()

        val oneMinuteWindowStart = nowMillis - 60_000L
        val recentInWindow = recentEmissions.filter { it.emittedAtMillis >= oneMinuteWindowStart }
        val availableSlots = (config.inRideMaxRecommendationsPerMinute - recentInWindow.size)
            .coerceAtLeast(0)
        if (availableSlots == 0) return emptyList()

        val cooldownMillis = config.inRideSameTypeCooldownSec * 1000L
        val allowedByCooldown = candidates.filter { candidate ->
            recentEmissions.none { emission ->
                emission.type == candidate.type &&
                    nowMillis - emission.emittedAtMillis < cooldownMillis
            }
        }

        return allowedByCooldown.take(availableSlots)
    }

    private fun applyConfidenceDowngrade(
        baseConfidence: AiQualityClass,
        snapshot: AiInputSnapshot,
        nowMillis: Long,
    ): AiQualityClass {
        var downgraded = baseConfidence
        if (hasStaleOrUnreachableSignals(snapshot)) {
            downgraded = downgraded.lowerOneStep()
        }
        if (nowMillis - snapshot.signalMeta.captureTimestampMillis >
            snapshot.signalMeta.maxExpectedAgeMillis
        ) {
            downgraded = downgraded.lowerOneStep()
        }
        val wearable = snapshot.wearableSnapshot
        if (wearable != null && nowMillis - wearable.syncedAtMillis > config.wearableFreshnessMaxAgeMillis) {
            downgraded = downgraded.lowerOneStep()
        }
        return downgraded
    }

    private fun hasTrainerConnectivityRisk(snapshot: AiInputSnapshot): Boolean {
        val quality = snapshot.connectivityQuality
        return quality.trainerSignalStale || quality.trainerReachability == AiReachability.UNREACHABLE
    }

    private fun hasStaleOrUnreachableSignals(snapshot: AiInputSnapshot): Boolean {
        val quality = snapshot.connectivityQuality
        return quality.trainerSignalStale ||
            quality.hrSignalStale ||
            quality.trainerReachability == AiReachability.UNREACHABLE ||
            quality.hrReachability == AiReachability.UNREACHABLE
    }

    private fun isRecentLoadHigh(history: AiHistoryAggregates): Boolean {
        val recent7d = history.recentActualTss7d ?: return false
        val recent28d = history.recentActualTss28d ?: return false
        if (recent28d <= 0.0) return false
        val baselinePerWeek = recent28d / 4.0
        return recent7d >= baselinePerWeek * config.preRideRecentLoadRatioThreshold
    }

    /**
     * Restricts cadence coaching to workouts that declare an explicit cadence target.
     *
     * Without a step-defined target cadence, generic in-ride cadence prompts can become
     * misleading, so cadence cues remain silent.
     */
    private fun explicitCadenceTargetRpm(targetCadenceRpm: Int?): Int? {
        return targetCadenceRpm?.takeIf { it > 0 }?.let { max(1, it) }
    }

    private fun resolveCadenceGuidance(deltaRpm: Int): CadenceGuidance {
        return if (deltaRpm < 0) {
            CadenceGuidance(
                direction = CadenceDirection.BELOW,
                templateKey = "ai.session.cadence_increase_slightly",
                rationaleKey = "cadence.below_target_band",
            )
        } else {
            CadenceGuidance(
                direction = CadenceDirection.ABOVE,
                templateKey = "ai.session.cadence_reduce_slightly",
                rationaleKey = "cadence.above_target_band",
            )
        }
    }

    private data class CadenceGuidance(
        val direction: CadenceDirection,
        val templateKey: String,
        val rationaleKey: String,
    )

    private enum class CadenceDirection(val multiplier: Int) {
        BELOW(-1),
        ABOVE(1),
    }

    private fun AiQualityClass.lowerOneStep(): AiQualityClass {
        return when (this) {
            AiQualityClass.HIGH -> AiQualityClass.MEDIUM
            AiQualityClass.MEDIUM -> AiQualityClass.LOW
            AiQualityClass.LOW -> AiQualityClass.LOW
        }
    }

    private companion object {
        val CANDIDATE_COMPARATOR: Comparator<AiRecommendationCandidate> =
            compareBy<AiRecommendationCandidate>(
                { it.priority.rank() },
                { it.type.name },
                { it.payload.templateKey },
            )
    }

    private fun effectiveWearableSnapshot(
        snapshot: AiInputSnapshot,
        wearableNormalization: AiWearableNormalizationResult?,
    ): AiWearableSnapshot? {
        if (wearableNormalization == null) {
            return snapshot.wearableSnapshot
        }
        return when (wearableNormalization.state) {
            AiWearableDataState.FRESH -> wearableNormalization.snapshot
            AiWearableDataState.STALE,
            AiWearableDataState.MISSING,
            -> null
        }
    }
}

/**
 * Suppresses cadence coaching when the rider is intentionally not pedaling yet.
 *
 * This avoids misleading "increase cadence" cues during the initial waiting state
 * (elapsed=0 and cadence=0) and while the workout is paused.
 */
internal fun shouldSuppressInRideCadenceCue(liveMetrics: AiLiveMetrics): Boolean {
    if (liveMetrics.workoutPaused) return true
    val elapsedSec = liveMetrics.workoutElapsedSec ?: return false
    val cadenceRpm = liveMetrics.cadenceRpm ?: return false
    return elapsedSec <= 0 && cadenceRpm <= 0
}

private fun AiRecommendationPriority.rank(): Int {
    return when (this) {
        AiRecommendationPriority.CRITICAL -> 0
        AiRecommendationPriority.HIGH -> 1
        AiRecommendationPriority.MEDIUM -> 2
        AiRecommendationPriority.LOW -> 3
    }
}

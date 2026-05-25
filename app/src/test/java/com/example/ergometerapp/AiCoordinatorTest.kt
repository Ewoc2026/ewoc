package com.example.ergometerapp

import com.example.ergometerapp.ai.AiPhase
import com.example.ergometerapp.ai.AiPolicyEvaluation
import com.example.ergometerapp.ai.AiPresentationDecision
import com.example.ergometerapp.ai.AiPresentationMessage
import com.example.ergometerapp.ai.AiPresentationSuppressedCandidate
import com.example.ergometerapp.ai.AiPresentationSurface
import com.example.ergometerapp.ai.AiPresentedMessageRecord
import com.example.ergometerapp.ai.AiQualityClass
import com.example.ergometerapp.ai.AiReachability
import com.example.ergometerapp.ai.AiRecommendationCandidate
import com.example.ergometerapp.ai.AiRecommendationPayload
import com.example.ergometerapp.ai.AiRecommendationPriority
import com.example.ergometerapp.ai.AiRecommendationType
import com.example.ergometerapp.ai.AiSuppressedRecommendation
import com.example.ergometerapp.ai.AiSuppressionReason
import com.example.ergometerapp.ftms.IndoorBikeData
import com.example.ergometerapp.workout.runner.RunnerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiCoordinatorTest {

    @Test
    fun refresh_sessionFlow_updatesUi_recordsHistory_and_logsTelemetry() {
        val uiState = AppUiState()
        val telemetryDecisions = mutableListOf<AiPresentationDecision>()
        val adaptRecentPresentedSizes = mutableListOf<Int>()

        val coordinator = createCoordinator(
            uiState = uiState,
            evaluateRecommendations = { _, _, _, _, _ ->
                listOf(candidate(AiRecommendationType.PACING, "ai.session.pacing_hold_target"))
            },
            evaluatePolicy = { candidates ->
                AiPolicyEvaluation(
                    allowed = candidates,
                    suppressed = emptyList(),
                )
            },
            adaptPresentation = { _, evaluation, _, recentPresented ->
                adaptRecentPresentedSizes += recentPresented.size
                AiPresentationDecision(
                    messages = evaluation.allowed.map { recommendation ->
                        message(
                            phase = recommendation.phase,
                            type = recommendation.type,
                            templateKey = recommendation.payload.templateKey,
                            templateArgs = recommendation.payload.templateArgs,
                        )
                    },
                    keepExistingMessage = false,
                    policySuppressed = evaluation.suppressed,
                    adapterSuppressed = emptyList(),
                )
            },
            logTelemetry = { _, decision, _ ->
                telemetryDecisions += decision
            },
        )

        coordinator.refresh(
            phase = AiPhase.SESSION,
            force = true,
            nowMillis = 1_000L,
        )
        coordinator.refresh(
            phase = AiPhase.SESSION,
            force = true,
            nowMillis = 2_000L,
        )

        assertEquals("formatted:ai.session.pacing_hold_target", uiState.aiSessionAssistantMessage.value)
        assertFalse(uiState.aiSessionAssistantIsError.value)
        assertEquals(listOf(0, 1), adaptRecentPresentedSizes)
        assertEquals(2, telemetryDecisions.size)
        assertEquals(1, telemetryDecisions.last().messages.size)
    }

    @Test
    fun refresh_keepExistingSessionMessage_preservesVisiblePromptWithinRetentionWindow() {
        val uiState = AppUiState()
        uiState.runner.value = RunnerState(
            running = true,
            paused = false,
            done = false,
            label = "Steady",
            targetPowerWatts = 210,
            targetCadence = 85,
            workoutElapsedSec = 120,
            stepRemainingSec = 60,
            intervalPart = null,
        )
        var adaptCallCount = 0

        val coordinator = createCoordinator(
            uiState = uiState,
            evaluateRecommendations = { _, _, _, _, _ ->
                listOf(candidate(AiRecommendationType.CADENCE, "ai.session.cadence_increase_slightly"))
            },
            evaluatePolicy = { candidates ->
                AiPolicyEvaluation(
                    allowed = candidates,
                    suppressed = emptyList(),
                )
            },
            adaptPresentation = { _, evaluation, _, _ ->
                adaptCallCount += 1
                if (adaptCallCount == 1) {
                    AiPresentationDecision(
                        messages = listOf(
                            message(
                                phase = AiPhase.SESSION,
                                type = AiRecommendationType.CADENCE,
                                templateKey = "ai.session.cadence_increase_slightly",
                                templateArgs = mapOf(
                                    "cadence_threshold_rpm" to "85",
                                    "cadence_target_rpm" to "85",
                                    "cadence_rpm" to "70",
                                ),
                            ),
                        ),
                        keepExistingMessage = false,
                        policySuppressed = emptyList(),
                        adapterSuppressed = emptyList(),
                    )
                } else {
                    AiPresentationDecision(
                        messages = emptyList(),
                        keepExistingMessage = true,
                        policySuppressed = emptyList(),
                        adapterSuppressed = emptyList(),
                    )
                }
            },
            logTelemetry = { _, _, _ -> },
        )

        coordinator.refresh(
            phase = AiPhase.SESSION,
            force = true,
            nowMillis = 10_000L,
        )
        val firstMessage = uiState.aiSessionAssistantMessage.value

        coordinator.refresh(
            phase = AiPhase.SESSION,
            force = true,
            nowMillis = 20_000L,
        )

        assertNotNull(firstMessage)
        assertEquals(firstMessage, uiState.aiSessionAssistantMessage.value)
        assertFalse(uiState.aiSessionAssistantIsError.value)
    }

    @Test
    fun refresh_forwardsSuppressionContextToTelemetryLogger() {
        val uiState = AppUiState()
        val telemetryDecisions = mutableListOf<AiPresentationDecision>()
        val suppressedCandidate = candidate(
            type = AiRecommendationType.PACING,
            templateKey = "ai.session.pacing_hold_target",
        )
        val expectedDecision = AiPresentationDecision(
            messages = emptyList(),
            keepExistingMessage = true,
            policySuppressed = listOf(
                AiSuppressedRecommendation(
                    candidate = suppressedCandidate,
                    reason = AiSuppressionReason.LOW_CONFIDENCE_PERFORMANCE_SUPPRESSED,
                ),
            ),
            adapterSuppressed = listOf(
                AiPresentationSuppressedCandidate(
                    candidate = suppressedCandidate,
                    reason = com.example.ergometerapp.ai.AiPresentationSuppressionReason.SESSION_RATE_LIMIT_WINDOW,
                ),
            ),
        )

        val coordinator = createCoordinator(
            uiState = uiState,
            evaluateRecommendations = { _, _, _, _, _ -> listOf(suppressedCandidate) },
            evaluatePolicy = {
                AiPolicyEvaluation(
                    allowed = emptyList(),
                    suppressed = expectedDecision.policySuppressed,
                )
            },
            adaptPresentation = { _, _, _, _ -> expectedDecision },
            logTelemetry = { _, decision, _ ->
                telemetryDecisions += decision
            },
        )

        coordinator.refresh(
            phase = AiPhase.SESSION,
            force = true,
            nowMillis = 3_000L,
        )

        assertEquals(1, telemetryDecisions.size)
        assertEquals(expectedDecision, telemetryDecisions.single())
        assertTrue(uiState.aiSessionAssistantMessage.value == null)
    }

    @Test
    fun refresh_sessionCadenceGate_requiresSustainedDeviationBeforeRendering() {
        val uiState = AppUiState().apply {
            runner.value = RunnerState(
                running = true,
                paused = false,
                done = false,
                label = "Steady",
                targetPowerWatts = 200,
                targetCadence = 85,
                workoutElapsedSec = 120,
                stepRemainingSec = 30,
                intervalPart = null,
            )
            bikeData.value = bikeData(cadenceRpm = 80.0)
        }
        val coordinator = createCoordinator(
            uiState = uiState,
            evaluateRecommendations = { _, _, _, _, _ ->
                listOf(candidate(AiRecommendationType.CADENCE, "ai.session.cadence_increase_slightly"))
            },
            evaluatePolicy = { candidates ->
                AiPolicyEvaluation(
                    allowed = candidates,
                    suppressed = emptyList(),
                )
            },
            adaptPresentation = { _, evaluation, _, _ ->
                AiPresentationDecision(
                    messages = evaluation.allowed.map { recommendation ->
                        message(
                            phase = recommendation.phase,
                            type = recommendation.type,
                            templateKey = recommendation.payload.templateKey,
                            templateArgs = recommendation.payload.templateArgs,
                        )
                    },
                    keepExistingMessage = evaluation.allowed.isEmpty(),
                    policySuppressed = emptyList(),
                    adapterSuppressed = emptyList(),
                )
            },
            logTelemetry = { _, _, _ -> },
        )

        coordinator.refresh(phase = AiPhase.SESSION, force = true, nowMillis = 10_000L)
        assertTrue(uiState.aiSessionAssistantMessage.value == null)

        coordinator.refresh(phase = AiPhase.SESSION, force = true, nowMillis = 14_000L)
        assertTrue(uiState.aiSessionAssistantMessage.value == null)

        coordinator.refresh(phase = AiPhase.SESSION, force = true, nowMillis = 16_000L)
        assertEquals("formatted:ai.session.cadence_increase_slightly", uiState.aiSessionAssistantMessage.value)
    }

    @Test
    fun refresh_sessionCadenceGate_clearsRetainedMessageAfterRecoveryHold() {
        val uiState = AppUiState().apply {
            runner.value = RunnerState(
                running = true,
                paused = false,
                done = false,
                label = "Steady",
                targetPowerWatts = 200,
                targetCadence = 85,
                workoutElapsedSec = 120,
                stepRemainingSec = 30,
                intervalPart = null,
            )
            bikeData.value = bikeData(cadenceRpm = 80.0)
        }
        val coordinator = createCoordinator(
            uiState = uiState,
            evaluateRecommendations = { _, _, _, _, _ ->
                listOf(candidate(AiRecommendationType.CADENCE, "ai.session.cadence_increase_slightly"))
            },
            evaluatePolicy = { candidates ->
                AiPolicyEvaluation(
                    allowed = candidates,
                    suppressed = emptyList(),
                )
            },
            adaptPresentation = { _, evaluation, _, _ ->
                AiPresentationDecision(
                    messages = evaluation.allowed.map { recommendation ->
                        message(
                            phase = recommendation.phase,
                            type = recommendation.type,
                            templateKey = recommendation.payload.templateKey,
                            templateArgs = recommendation.payload.templateArgs,
                        )
                    },
                    keepExistingMessage = evaluation.allowed.isEmpty(),
                    policySuppressed = emptyList(),
                    adapterSuppressed = emptyList(),
                )
            },
            logTelemetry = { _, _, _ -> },
        )

        coordinator.refresh(phase = AiPhase.SESSION, force = true, nowMillis = 10_000L)
        coordinator.refresh(phase = AiPhase.SESSION, force = true, nowMillis = 16_000L)
        assertEquals("formatted:ai.session.cadence_increase_slightly", uiState.aiSessionAssistantMessage.value)

        uiState.bikeData.value = bikeData(cadenceRpm = 84.0)
        coordinator.refresh(phase = AiPhase.SESSION, force = true, nowMillis = 18_000L)
        assertEquals("formatted:ai.session.cadence_increase_slightly", uiState.aiSessionAssistantMessage.value)

        coordinator.refresh(phase = AiPhase.SESSION, force = true, nowMillis = 21_000L)
        assertTrue(uiState.aiSessionAssistantMessage.value == null)
    }

    private fun createCoordinator(
        uiState: AppUiState,
        evaluateRecommendations: (
            phase: AiPhase,
            snapshot: com.example.ergometerapp.ai.AiInputSnapshot,
            nowMillis: Long,
            recentSessionEmissions: List<com.example.ergometerapp.ai.AiRecentEmission>,
            wearableNormalization: com.example.ergometerapp.ai.AiWearableNormalizationResult?,
        ) -> List<AiRecommendationCandidate>,
        evaluatePolicy: (List<AiRecommendationCandidate>) -> AiPolicyEvaluation,
        adaptPresentation: (
            phase: AiPhase,
            evaluation: AiPolicyEvaluation,
            nowMillis: Long,
            recentPresented: List<AiPresentedMessageRecord>,
        ) -> AiPresentationDecision,
        logTelemetry: (
            snapshot: com.example.ergometerapp.ai.AiInputSnapshot,
            decision: AiPresentationDecision,
            timestampMillis: Long,
        ) -> Unit,
        config: AiCoordinatorConfig = AiCoordinatorConfig(),
        nowMillisProvider: () -> Long = { System.currentTimeMillis() },
    ): AiCoordinator {
        return AiCoordinator(
            uiState = uiState,
            evaluateRecommendations = evaluateRecommendations,
            evaluatePolicy = evaluatePolicy,
            adaptPresentation = adaptPresentation,
            logTelemetry = logTelemetry,
            formatMessage = { message -> "formatted:${message.templateKey}" },
            isErrorType = { type -> type == AiRecommendationType.SAFETY },
            trainerReachability = { AiReachability.REACHABLE },
            heartRateReachability = { AiReachability.REACHABLE },
            isHeartRateConnected = { true },
            hasSelectedHrDevice = { true },
            isMockTrainerModeActive = { false },
            ftpWatts = { 250 },
            config = config,
            nowMillisProvider = nowMillisProvider,
        )
    }

    private fun candidate(
        type: AiRecommendationType,
        templateKey: String,
    ): AiRecommendationCandidate {
        return AiRecommendationCandidate(
            phase = AiPhase.SESSION,
            type = type,
            priority = AiRecommendationPriority.HIGH,
            confidence = AiQualityClass.HIGH,
            rationaleKeys = listOf("test"),
            payload = AiRecommendationPayload(
                templateKey = templateKey,
                templateArgs = mapOf("deviation_pct" to "4"),
            ),
        )
    }

    private fun message(
        phase: AiPhase,
        type: AiRecommendationType,
        templateKey: String,
        templateArgs: Map<String, String>,
    ): AiPresentationMessage {
        return AiPresentationMessage(
            phase = phase,
            surface = AiPresentationSurface.SESSION_ASSISTANT,
            type = type,
            confidence = AiQualityClass.HIGH,
            templateKey = templateKey,
            templateArgs = templateArgs,
        )
    }

    private fun bikeData(cadenceRpm: Double): IndoorBikeData {
        return IndoorBikeData(
            valid = true,
            instantaneousSpeedKmh = 30.0,
            averageSpeedKmh = null,
            instantaneousCadenceRpm = cadenceRpm,
            averageCadenceRpm = null,
            totalDistanceMeters = null,
            resistanceLevel = null,
            instantaneousPowerW = 200,
            averagePowerW = null,
            totalEnergyKcal = null,
            energyPerHourKcal = null,
            energyPerMinuteKcal = null,
            heartRateBpm = 130,
            metabolicEquivalent = null,
            elapsedTimeSeconds = null,
            remainingTimeSeconds = null,
        )
    }
}

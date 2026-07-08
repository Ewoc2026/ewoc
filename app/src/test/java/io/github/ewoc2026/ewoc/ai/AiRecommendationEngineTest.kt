package io.github.ewoc2026.ewoc.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiRecommendationEngineTest {

    @Test
    fun evaluate_isDeterministicForSameInput() {
        val engine = AiRecommendationEngine()
        val snapshot = sessionSnapshot(
            actualPowerWatts = 260,
            targetPowerWatts = 220,
            cadenceRpm = 72,
            heartRateBpm = 178,
        )

        val first = engine.evaluate(snapshot, nowMillis = 10_000L)
        val second = engine.evaluate(snapshot, nowMillis = 10_000L)

        assertEquals(first, second)
    }

    @Test
    fun preRide_readinessTriggersOnLowWearableScore() {
        val engine = AiRecommendationEngine()
        val snapshot = AiInputSnapshot(
            phase = AiPhase.MENU,
            signalMeta = AiSignalMeta(
                captureTimestampMillis = 20_000L,
                maxExpectedAgeMillis = 5_000L,
            ),
            wearableSnapshot = AiWearableSnapshot(
                sourceId = "health-connect",
                syncedAtMillis = 19_000L,
                readinessScore = 42.0,
            ),
        )

        val result = engine.evaluate(snapshot, nowMillis = 20_000L)
        assertEquals(1, result.size)
        assertEquals(AiRecommendationType.READINESS, result.single().type)
    }

    @Test
    fun preRide_readinessDoesNotTriggerWhenSignalsAreHealthy() {
        val engine = AiRecommendationEngine()
        val snapshot = AiInputSnapshot(
            phase = AiPhase.MENU,
            signalMeta = AiSignalMeta(
                captureTimestampMillis = 20_000L,
                maxExpectedAgeMillis = 5_000L,
            ),
            wearableSnapshot = AiWearableSnapshot(
                sourceId = "health-connect",
                syncedAtMillis = 19_000L,
                readinessScore = 80.0,
                recoveryScore = 82.0,
                sleepDurationMinutes = 430,
            ),
            context = AiContext(
                workout = AiWorkoutContext(plannedTss = 40.0),
                history = AiHistoryAggregates(
                    recentActualTss7d = 120.0,
                    recentActualTss28d = 600.0,
                ),
            ),
        )

        val result = engine.evaluate(snapshot, nowMillis = 20_000L)
        assertTrue(result.isEmpty())
    }

    @Test
    fun inRide_pacingIsDisabledByDefault() {
        val engine = AiRecommendationEngine(
            config = AiRuleConfig(inRidePowerDeviationPctThreshold = 10.0),
        )
        val snapshot = sessionSnapshot(
            actualPowerWatts = 220,
            targetPowerWatts = 200,
            cadenceRpm = 90,
            heartRateBpm = 140,
        )

        val result = engine.evaluate(snapshot, nowMillis = 10_000L)
        assertTrue(result.none { it.type == AiRecommendationType.PACING })
    }

    @Test
    fun inRide_pacingTriggersAtDeviationThresholdWhenEnabled() {
        val engine = AiRecommendationEngine(
            config = AiRuleConfig(
                inRidePowerDeviationPctThreshold = 10.0,
                inRidePowerMonitoringEnabled = true,
            ),
        )
        val snapshot = sessionSnapshot(
            actualPowerWatts = 220,
            targetPowerWatts = 200,
            cadenceRpm = 90,
            heartRateBpm = 140,
        )

        val result = engine.evaluate(snapshot, nowMillis = 10_000L)
        assertTrue(result.any { it.type == AiRecommendationType.PACING })
    }

    @Test
    fun inRide_cadenceDoesNotTriggerWithoutExplicitWorkoutTarget() {
        val engine = AiRecommendationEngine(
            config = AiRuleConfig(
                inRideLowCadenceRpmThreshold = 75,
                inRideCadenceTargetToleranceRpm = 5,
            ),
        )
        val trigger = sessionSnapshot(
            actualPowerWatts = 200,
            targetPowerWatts = 200,
            cadenceRpm = 72,
            heartRateBpm = 130,
        )
        val noTrigger = sessionSnapshot(
            actualPowerWatts = 200,
            targetPowerWatts = 200,
            cadenceRpm = 73,
            heartRateBpm = 130,
        )

        val triggerResult = engine.evaluate(trigger, nowMillis = 10_000L)
        val noTriggerResult = engine.evaluate(noTrigger, nowMillis = 10_000L)

        assertTrue(triggerResult.none { it.type == AiRecommendationType.CADENCE })
        assertTrue(noTriggerResult.none { it.type == AiRecommendationType.CADENCE })
    }

    @Test
    fun inRide_cadenceUsesWorkoutTargetBandAndIncreaseTemplate() {
        val engine = AiRecommendationEngine(
            config = AiRuleConfig(
                inRideLowCadenceRpmThreshold = 75,
                inRideCadenceTargetToleranceRpm = 5,
            ),
        )
        val trigger = sessionSnapshot(
            actualPowerWatts = 200,
            targetPowerWatts = 200,
            targetCadenceRpm = 90,
            cadenceRpm = 85,
            heartRateBpm = 130,
        )
        val noTrigger = sessionSnapshot(
            actualPowerWatts = 200,
            targetPowerWatts = 200,
            targetCadenceRpm = 90,
            cadenceRpm = 86,
            heartRateBpm = 130,
        )

        val triggerResult = engine.evaluate(trigger, nowMillis = 10_000L)
        val noTriggerResult = engine.evaluate(noTrigger, nowMillis = 10_000L)

        assertTrue(triggerResult.any { it.type == AiRecommendationType.CADENCE })
        assertTrue(noTriggerResult.none { it.type == AiRecommendationType.CADENCE })
        val cadenceCandidate = triggerResult.first { it.type == AiRecommendationType.CADENCE }
        assertEquals("ai.session.cadence_increase_slightly", cadenceCandidate.payload.templateKey)
        assertEquals("90", cadenceCandidate.payload.templateArgs["cadence_target_rpm"])
        assertEquals("85", cadenceCandidate.payload.templateArgs["cadence_threshold_rpm"])
    }

    @Test
    fun inRide_cadenceAboveBandUsesReduceTemplate() {
        val engine = AiRecommendationEngine(
            config = AiRuleConfig(
                inRideLowCadenceRpmThreshold = 75,
                inRideCadenceTargetToleranceRpm = 5,
            ),
        )
        val snapshot = sessionSnapshot(
            actualPowerWatts = 200,
            targetPowerWatts = 200,
            targetCadenceRpm = 90,
            cadenceRpm = 95,
            heartRateBpm = 130,
        )

        val result = engine.evaluate(snapshot, nowMillis = 10_000L)
        val cadenceCandidate = result.first { it.type == AiRecommendationType.CADENCE }

        assertEquals("ai.session.cadence_reduce_slightly", cadenceCandidate.payload.templateKey)
        assertEquals("95", cadenceCandidate.payload.templateArgs["cadence_threshold_rpm"])
    }

    @Test
    fun inRide_cadenceDoesNotTriggerDuringPausedWorkout() {
        val engine = AiRecommendationEngine(
            config = AiRuleConfig(inRideLowCadenceRpmThreshold = 75),
        )
        val pausedSnapshot = sessionSnapshot(
            actualPowerWatts = 200,
            targetPowerWatts = 200,
            cadenceRpm = 0,
            heartRateBpm = 130,
            workoutElapsedSec = 120,
            workoutPaused = true,
        )

        val result = engine.evaluate(pausedSnapshot, nowMillis = 10_000L)
        assertTrue(result.none { it.type == AiRecommendationType.CADENCE })
    }

    @Test
    fun inRide_cadenceDoesNotTriggerAtSessionStartBeforePedaling() {
        val engine = AiRecommendationEngine(
            config = AiRuleConfig(inRideLowCadenceRpmThreshold = 75),
        )
        val startSnapshot = sessionSnapshot(
            actualPowerWatts = 0,
            targetPowerWatts = 200,
            cadenceRpm = 0,
            heartRateBpm = 90,
            workoutElapsedSec = 0,
            workoutPaused = false,
        )

        val result = engine.evaluate(startSnapshot, nowMillis = 10_000L)
        assertTrue(result.none { it.type == AiRecommendationType.CADENCE })
    }

    @Test
    fun inRide_connectivityTriggersWhenTrainerRiskDetected() {
        val engine = AiRecommendationEngine()
        val snapshot = AiInputSnapshot(
            phase = AiPhase.SESSION,
            signalMeta = AiSignalMeta(
                captureTimestampMillis = 10_000L,
                maxExpectedAgeMillis = 5_000L,
            ),
            liveMetrics = AiLiveMetrics(
                actualPowerWatts = 200,
                targetPowerWatts = 200,
                cadenceRpm = 90,
                heartRateBpm = 130,
                workoutElapsedSec = 300,
                workoutPaused = false,
            ),
            connectivityQuality = AiConnectivityQuality(
                trainerReachability = AiReachability.UNREACHABLE,
            ),
        )

        val result = engine.evaluate(snapshot, nowMillis = 10_000L)
        assertTrue(result.any { it.type == AiRecommendationType.CONNECTIVITY })
    }

    @Test
    fun inRide_connectivityIsSuppressedWhenMockTrainerModeIsActive() {
        val engine = AiRecommendationEngine()
        val snapshot = AiInputSnapshot(
            phase = AiPhase.SESSION,
            signalMeta = AiSignalMeta(
                captureTimestampMillis = 10_000L,
                maxExpectedAgeMillis = 5_000L,
            ),
            liveMetrics = AiLiveMetrics(
                actualPowerWatts = 200,
                targetPowerWatts = 200,
                cadenceRpm = 90,
                heartRateBpm = 130,
                workoutElapsedSec = 300,
                workoutPaused = false,
            ),
            connectivityQuality = AiConnectivityQuality(
                trainerReachability = AiReachability.UNREACHABLE,
            ),
            context = AiContext(
                mockTrainerModeActive = true,
            ),
        )

        val result = engine.evaluate(snapshot, nowMillis = 10_000L)
        assertTrue(result.none { it.type == AiRecommendationType.CONNECTIVITY })
    }

    @Test
    fun postRide_recoveryTriggersWhenTssDeltaIsHigh() {
        val engine = AiRecommendationEngine(
            config = AiRuleConfig(postRideHighTssDeltaThreshold = 15.0),
        )
        val snapshot = AiInputSnapshot(
            phase = AiPhase.SUMMARY,
            signalMeta = AiSignalMeta(
                captureTimestampMillis = 50_000L,
                maxExpectedAgeMillis = 5_000L,
            ),
            context = AiContext(
                workout = AiWorkoutContext(plannedTss = 70.0),
                lastSessionActualTss = 92.0,
            ),
        )

        val result = engine.evaluate(snapshot, nowMillis = 50_000L)
        assertEquals(1, result.size)
        assertEquals(AiRecommendationType.RECOVERY, result.single().type)
    }

    @Test
    fun preRide_staleWearableNormalizationSuppressesWearableOnlyTrigger() {
        val engine = AiRecommendationEngine()
        val snapshot = AiInputSnapshot(
            phase = AiPhase.MENU,
            signalMeta = AiSignalMeta(
                captureTimestampMillis = 10_000L,
                maxExpectedAgeMillis = 5_000L,
            ),
            wearableSnapshot = AiWearableSnapshot(
                sourceId = "health-connect",
                syncedAtMillis = 9_000L,
                readinessScore = 30.0,
            ),
            context = AiContext(
                workout = AiWorkoutContext(plannedTss = 40.0),
                history = AiHistoryAggregates(
                    recentActualTss7d = 100.0,
                    recentActualTss28d = 600.0,
                ),
            ),
        )
        val staleNormalization = AiWearableNormalizationResult(
            snapshot = snapshot.wearableSnapshot,
            state = AiWearableDataState.STALE,
            confidenceOverride = AiQualityClass.LOW,
            reasonCode = "wearable.stale_snapshot",
        )

        val result = engine.evaluate(
            snapshot = snapshot,
            nowMillis = 10_000L,
            wearableNormalization = staleNormalization,
        )

        assertTrue(result.none { it.type == AiRecommendationType.READINESS })
    }

    @Test
    fun preRide_freshWearableNormalizationKeepsWearableTriggerEnabled() {
        val engine = AiRecommendationEngine()
        val snapshot = AiInputSnapshot(
            phase = AiPhase.MENU,
            signalMeta = AiSignalMeta(
                captureTimestampMillis = 10_000L,
                maxExpectedAgeMillis = 5_000L,
            ),
            wearableSnapshot = AiWearableSnapshot(
                sourceId = "health-connect",
                syncedAtMillis = 9_000L,
                readinessScore = 35.0,
            ),
        )
        val freshNormalization = AiWearableNormalizationResult(
            snapshot = snapshot.wearableSnapshot,
            state = AiWearableDataState.FRESH,
            confidenceOverride = AiQualityClass.HIGH,
            reasonCode = "wearable.fresh_snapshot",
        )

        val result = engine.evaluate(
            snapshot = snapshot,
            nowMillis = 10_000L,
            wearableNormalization = freshNormalization,
        )

        assertEquals(1, result.size)
        assertEquals(AiRecommendationType.READINESS, result.single().type)
    }

    @Test
    fun confidence_downgradesWithStaleSignalsAndStaleSnapshotAge() {
        val engine = AiRecommendationEngine()
        val snapshot = AiInputSnapshot(
            phase = AiPhase.SESSION,
            signalMeta = AiSignalMeta(
                captureTimestampMillis = 10_000L,
                maxExpectedAgeMillis = 1_000L,
            ),
            liveMetrics = AiLiveMetrics(
                actualPowerWatts = 260,
                targetPowerWatts = 220,
            ),
            connectivityQuality = AiConnectivityQuality(
                trainerSignalStale = true,
            ),
        )

        val result = engine.evaluate(snapshot, nowMillis = 15_000L)
        assertTrue(result.isNotEmpty())
        assertTrue(result.all { it.confidence == AiQualityClass.LOW })
    }

    @Test
    fun inRideRateLimit_isConfigurableByPerMinuteAndCooldown() {
        val strictEngine = AiRecommendationEngine(
            config = AiRuleConfig(
                inRideLowCadenceRpmThreshold = 80,
                inRideMaxRecommendationsPerMinute = 1,
                inRideSameTypeCooldownSec = 60,
            ),
        )
        val permissiveEngine = AiRecommendationEngine(
            config = AiRuleConfig(
                inRideLowCadenceRpmThreshold = 80,
                inRideMaxRecommendationsPerMinute = 2,
                inRideSameTypeCooldownSec = 5,
            ),
        )
        val snapshot = sessionSnapshot(
            actualPowerWatts = 250,
            targetPowerWatts = 200,
            targetCadenceRpm = 80,
            cadenceRpm = 70,
            heartRateBpm = 180,
        )

        val strictResult = strictEngine.evaluateWithInRideLimits(
            snapshot = snapshot,
            nowMillis = 100_000L,
            recentEmissions = emptyList(),
        )
        val permissiveResult = permissiveEngine.evaluateWithInRideLimits(
            snapshot = snapshot,
            nowMillis = 100_000L,
            recentEmissions = emptyList(),
        )

        assertEquals(1, strictResult.size)
        assertEquals(2, permissiveResult.size)

        val cooldownSuppressed = strictEngine.evaluateWithInRideLimits(
            snapshot = snapshot,
            nowMillis = 100_000L,
            recentEmissions = listOf(
                AiRecentEmission(
                    type = AiRecommendationType.CADENCE,
                    emittedAtMillis = 99_980L,
                ),
            ),
        )
        assertTrue(cooldownSuppressed.none { it.type == AiRecommendationType.CADENCE })
    }

    private fun sessionSnapshot(
        actualPowerWatts: Int,
        targetPowerWatts: Int,
        targetCadenceRpm: Int? = null,
        cadenceRpm: Int,
        heartRateBpm: Int,
        workoutElapsedSec: Int? = 300,
        workoutPaused: Boolean = false,
    ): AiInputSnapshot {
        return AiInputSnapshot(
            phase = AiPhase.SESSION,
            signalMeta = AiSignalMeta(
                captureTimestampMillis = 10_000L,
                maxExpectedAgeMillis = 5_000L,
            ),
            liveMetrics = AiLiveMetrics(
                actualPowerWatts = actualPowerWatts,
                targetPowerWatts = targetPowerWatts,
                targetCadenceRpm = targetCadenceRpm,
                cadenceRpm = cadenceRpm,
                heartRateBpm = heartRateBpm,
                workoutElapsedSec = workoutElapsedSec,
                workoutPaused = workoutPaused,
            ),
        )
    }
}

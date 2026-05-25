package com.example.ergometerapp.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiWearableNormalizerTest {

    @Test
    fun normalize_missingSnapshot_returnsMissingLowConfidence() {
        val normalizer = AiWearableNormalizer()

        val result = normalizer.normalize(
            snapshot = null,
            nowMillis = 100_000L,
            useCase = AiWearableUseCase.PRE_RIDE,
        )

        assertNull(result.snapshot)
        assertEquals(AiWearableDataState.MISSING, result.state)
        assertEquals(AiQualityClass.LOW, result.confidenceOverride)
        assertEquals("wearable.missing_snapshot", result.reasonCode)
    }

    @Test
    fun normalize_staleSnapshot_returnsStaleLowConfidence() {
        val normalizer = AiWearableNormalizer(
            config = AiWearableFreshnessConfig(
                preRideMaxAgeMillis = 1_000L,
                postRideMaxAgeMillis = 1_000L,
            ),
        )
        val snapshot = AiWearableSnapshot(
            sourceId = "health-connect",
            syncedAtMillis = 10_000L,
            readinessScore = 70.0,
        )

        val result = normalizer.normalize(
            snapshot = snapshot,
            nowMillis = 20_000L,
            useCase = AiWearableUseCase.PRE_RIDE,
        )

        assertEquals(AiWearableDataState.STALE, result.state)
        assertEquals(AiQualityClass.LOW, result.confidenceOverride)
        assertEquals("wearable.stale_snapshot", result.reasonCode)
        assertEquals(snapshot, result.snapshot)
    }

    @Test
    fun normalize_postRideUsesStricterAgeThresholdThanPreRide() {
        val thirteenHoursMs = 13 * 60 * 60 * 1000L
        val normalizer = AiWearableNormalizer()
        val snapshot = AiWearableSnapshot(
            sourceId = "health-connect",
            syncedAtMillis = 1_000L,
            readinessScore = 62.0,
        )
        val nowMillis = 1_000L + thirteenHoursMs

        val preRide = normalizer.normalize(
            snapshot = snapshot,
            nowMillis = nowMillis,
            useCase = AiWearableUseCase.PRE_RIDE,
        )
        val postRide = normalizer.normalize(
            snapshot = snapshot,
            nowMillis = nowMillis,
            useCase = AiWearableUseCase.POST_RIDE,
        )

        assertEquals(AiWearableDataState.FRESH, preRide.state)
        assertEquals(AiWearableDataState.STALE, postRide.state)
    }

    @Test
    fun normalize_freshSnapshot_preservesSnapshotAndHighConfidence() {
        val normalizer = AiWearableNormalizer()
        val snapshot = AiWearableSnapshot(
            sourceId = "health-connect",
            syncedAtMillis = 100_000L,
            sleepDurationMinutes = 430,
            readinessScore = 75.0,
        )

        val result = normalizer.normalize(
            snapshot = snapshot,
            nowMillis = 102_000L,
            useCase = AiWearableUseCase.POST_RIDE,
        )

        assertEquals(AiWearableDataState.FRESH, result.state)
        assertEquals(AiQualityClass.HIGH, result.confidenceOverride)
        assertEquals("wearable.fresh_snapshot", result.reasonCode)
        assertTrue(result.snapshot === snapshot)
    }
}

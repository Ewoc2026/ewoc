package io.github.ewoc2026.ewoc.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiDomainModelsTest {

    @Test
    fun inputSnapshot_defaultsKeepOptionalSignalsAbsent() {
        val snapshot = AiInputSnapshot(
            phase = AiPhase.SESSION,
            signalMeta = AiSignalMeta(
                captureTimestampMillis = 1_000L,
                maxExpectedAgeMillis = 5_000L,
            ),
        )

        assertEquals(AiPhase.SESSION, snapshot.phase)
        assertEquals(1_000L, snapshot.signalMeta.captureTimestampMillis)
        assertEquals(5_000L, snapshot.signalMeta.maxExpectedAgeMillis)
        assertNull(snapshot.liveMetrics.actualPowerWatts)
        assertNull(snapshot.liveMetrics.targetPowerWatts)
        assertNull(snapshot.liveMetrics.targetCadenceRpm)
        assertNull(snapshot.liveMetrics.cadenceRpm)
        assertNull(snapshot.liveMetrics.speedKmh)
        assertNull(snapshot.liveMetrics.heartRateBpm)
        assertEquals(AiReachability.UNKNOWN, snapshot.connectivityQuality.trainerReachability)
        assertEquals(AiReachability.UNKNOWN, snapshot.connectivityQuality.hrReachability)
        assertFalse(snapshot.connectivityQuality.trainerSignalStale)
        assertFalse(snapshot.connectivityQuality.hrSignalStale)
        assertNull(snapshot.context.configuredFtpWatts)
        assertNull(snapshot.wearableSnapshot)
    }

    @Test
    fun inputSnapshot_supportsWearableDataWithoutChangingCoreFields() {
        val wearable = AiWearableSnapshot(
            sourceId = "health-connect",
            syncedAtMillis = 20_000L,
            sleepDurationMinutes = 410,
            sleepQualityScore = 82.0,
            readinessScore = 75.0,
            recoveryScore = 71.0,
            hrvTrend = AiTrendMarker.UP,
            restingHrTrend = AiTrendMarker.STABLE,
            strainScore = 54.0,
        )
        val snapshot = AiInputSnapshot(
            phase = AiPhase.MENU,
            signalMeta = AiSignalMeta(
                captureTimestampMillis = 21_000L,
                maxExpectedAgeMillis = 10_000L,
            ),
            liveMetrics = AiLiveMetrics(actualPowerWatts = 220),
            wearableSnapshot = wearable,
        )

        assertEquals(AiPhase.MENU, snapshot.phase)
        assertEquals(220, snapshot.liveMetrics.actualPowerWatts)
        assertEquals("health-connect", snapshot.wearableSnapshot?.sourceId)
        assertEquals(410, snapshot.wearableSnapshot?.sleepDurationMinutes)
        assertEquals(AiTrendMarker.UP, snapshot.wearableSnapshot?.hrvTrend)
    }

    @Test
    fun recommendationCandidate_preservesRationaleAndTemplateArgs() {
        val candidate = AiRecommendationCandidate(
            phase = AiPhase.SUMMARY,
            type = AiRecommendationType.RECOVERY,
            priority = AiRecommendationPriority.HIGH,
            confidence = AiQualityClass.MEDIUM,
            rationaleKeys = listOf("recovery.low_readiness", "load.high_7d"),
            payload = AiRecommendationPayload(
                templateKey = "ai.summary.recovery_reduce_load",
                templateArgs = mapOf(
                    "readiness" to "67",
                    "tss_delta" to "+18.5",
                ),
            ),
        )

        assertEquals(AiPhase.SUMMARY, candidate.phase)
        assertEquals(AiRecommendationType.RECOVERY, candidate.type)
        assertEquals(AiRecommendationPriority.HIGH, candidate.priority)
        assertEquals(AiQualityClass.MEDIUM, candidate.confidence)
        assertEquals(2, candidate.rationaleKeys.size)
        assertTrue(candidate.rationaleKeys.contains("recovery.low_readiness"))
        assertEquals("ai.summary.recovery_reduce_load", candidate.payload.templateKey)
        assertEquals("67", candidate.payload.templateArgs["readiness"])
        assertEquals("+18.5", candidate.payload.templateArgs["tss_delta"])
    }

    @Test
    fun recommendationCandidate_copyRoundTripKeepsEquality() {
        val original = AiRecommendationCandidate(
            phase = AiPhase.SESSION,
            type = AiRecommendationType.PACING,
            priority = AiRecommendationPriority.MEDIUM,
            confidence = AiQualityClass.HIGH,
            rationaleKeys = listOf("power.deviation.sustained"),
            payload = AiRecommendationPayload(
                templateKey = "ai.session.pacing_hold_target",
                templateArgs = mapOf("deviation_pct" to "8"),
            ),
        )

        val copied = original.copy()
        assertEquals(original, copied)
        assertEquals(original.hashCode(), copied.hashCode())
    }
}

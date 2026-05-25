package com.example.ergometerapp.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiTelemetryLoggerTest {

    @Test
    fun logCycle_emitsStructuredEventsForRenderedAndSuppressedItems() {
        val events = mutableListOf<AiTelemetryEvent>()
        val logger = AiTelemetryLogger(
            config = AiTelemetryConfig(wearableFreshnessMaxAgeMillis = 1_000L),
            recordEvent = { event -> events += event },
        )
        val snapshot = AiInputSnapshot(
            phase = AiPhase.SESSION,
            signalMeta = AiSignalMeta(
                captureTimestampMillis = 10_000L,
                maxExpectedAgeMillis = 5_000L,
            ),
            connectivityQuality = AiConnectivityQuality(
                trainerReachability = AiReachability.REACHABLE,
                hrReachability = AiReachability.UNREACHABLE,
                trainerSignalStale = false,
                hrSignalStale = true,
            ),
            wearableSnapshot = AiWearableSnapshot(
                sourceId = "health-connect",
                syncedAtMillis = 5_000L,
            ),
        )
        val decision = AiPresentationDecision(
            messages = listOf(
                AiPresentationMessage(
                    phase = AiPhase.SESSION,
                    surface = AiPresentationSurface.SESSION_ASSISTANT,
                    type = AiRecommendationType.PACING,
                    confidence = AiQualityClass.MEDIUM,
                    templateKey = "ai.session.pacing_hold_target",
                    templateArgs = mapOf("deviation_pct" to "11"),
                ),
            ),
            keepExistingMessage = false,
            policySuppressed = listOf(
                AiSuppressedRecommendation(
                    candidate = candidate(
                        phase = AiPhase.SESSION,
                        type = AiRecommendationType.CADENCE,
                        confidence = AiQualityClass.LOW,
                        templateKey = "ai.session.cadence_increase_slightly",
                    ),
                    reason = AiSuppressionReason.LOW_CONFIDENCE_PERFORMANCE_SUPPRESSED,
                ),
            ),
            adapterSuppressed = listOf(
                AiPresentationSuppressedCandidate(
                    candidate = candidate(
                        phase = AiPhase.SESSION,
                        type = AiRecommendationType.SAFETY,
                        confidence = AiQualityClass.HIGH,
                        templateKey = "ai.session.safety_reduce_effort",
                    ),
                    reason = AiPresentationSuppressionReason.SESSION_RATE_LIMIT_WINDOW,
                ),
            ),
        )

        logger.logCycle(
            snapshot = snapshot,
            decision = decision,
            timestampMillis = 20_000L,
        )

        assertEquals(3, events.size)

        val emitted = events[0] as AiTelemetryEvent.RecommendationEmitted
        assertEquals(AiRecommendationType.PACING, emitted.type)
        assertEquals(AiQualityClass.MEDIUM, emitted.confidence)
        assertEquals("ai.session.pacing_hold_target", emitted.templateKey)
        assertTrue(emitted.qualityFlags.wearableSnapshotStale)
        assertTrue(emitted.qualityFlags.hrSignalStale)

        val policySuppressed = events[1] as AiTelemetryEvent.RecommendationSuppressed
        assertEquals("policy", policySuppressed.suppressionStage)
        assertEquals(
            AiSuppressionReason.LOW_CONFIDENCE_PERFORMANCE_SUPPRESSED.name,
            policySuppressed.suppressionReasonCode,
        )
        assertEquals(AiRecommendationType.CADENCE, policySuppressed.type)

        val adapterSuppressed = events[2] as AiTelemetryEvent.RecommendationSuppressed
        assertEquals("presentation", adapterSuppressed.suppressionStage)
        assertEquals(
            AiPresentationSuppressionReason.SESSION_RATE_LIMIT_WINDOW.name,
            adapterSuppressed.suppressionReasonCode,
        )
        assertEquals(AiRecommendationType.SAFETY, adapterSuppressed.type)
    }

    @Test
    fun defaultLogger_writesIntoTelemetryBuffer() {
        AiTelemetryBuffer.clear()
        val logger = AiTelemetryLogger()
        val decision = AiPresentationDecision(
            messages = listOf(
                AiPresentationMessage(
                    phase = AiPhase.MENU,
                    surface = AiPresentationSurface.MENU_ASSISTANT,
                    type = AiRecommendationType.READINESS,
                    confidence = AiQualityClass.HIGH,
                    templateKey = "ai.menu.readiness_reduce_intensity",
                    templateArgs = emptyMap(),
                ),
            ),
            keepExistingMessage = false,
            policySuppressed = emptyList(),
            adapterSuppressed = emptyList(),
        )

        logger.logCycle(
            snapshot = AiInputSnapshot(
                phase = AiPhase.MENU,
                signalMeta = AiSignalMeta(
                    captureTimestampMillis = 1_000L,
                    maxExpectedAgeMillis = 1_000L,
                ),
            ),
            decision = decision,
            timestampMillis = 2_000L,
        )

        val snapshot = AiTelemetryBuffer.snapshot()
        assertFalse(snapshot.isEmpty())
        assertTrue(snapshot.single() is AiTelemetryEvent.RecommendationEmitted)
    }

    private fun candidate(
        phase: AiPhase,
        type: AiRecommendationType,
        confidence: AiQualityClass,
        templateKey: String,
    ): AiRecommendationCandidate {
        return AiRecommendationCandidate(
            phase = phase,
            type = type,
            priority = AiRecommendationPriority.HIGH,
            confidence = confidence,
            rationaleKeys = listOf("test.rule"),
            payload = AiRecommendationPayload(templateKey = templateKey),
        )
    }
}

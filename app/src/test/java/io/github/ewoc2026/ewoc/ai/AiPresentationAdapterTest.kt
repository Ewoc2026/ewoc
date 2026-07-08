package io.github.ewoc2026.ewoc.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiPresentationAdapterTest {

    @Test
    fun adapt_mapsOnlyCurrentPhaseRecommendationsToSurface() {
        val adapter = AiPresentationAdapter()
        val evaluation = AiPolicyEvaluation(
            allowed = listOf(
                candidate(phase = AiPhase.MENU, type = AiRecommendationType.READINESS),
                candidate(phase = AiPhase.SUMMARY, type = AiRecommendationType.RECOVERY),
            ),
            suppressed = emptyList(),
        )

        val result = adapter.adapt(
            currentPhase = AiPhase.MENU,
            evaluation = evaluation,
            nowMillis = 10_000L,
        )

        assertEquals(1, result.messages.size)
        assertEquals(AiPresentationSurface.MENU_ASSISTANT, result.messages.single().surface)
        assertEquals(AiRecommendationType.READINESS, result.messages.single().type)
        assertEquals(1, result.adapterSuppressed.size)
        assertEquals(
            AiPresentationSuppressionReason.PHASE_MISMATCH,
            result.adapterSuppressed.single().reason,
        )
    }

    @Test
    fun adapt_enforcesSessionRateLimitWindow() {
        val adapter = AiPresentationAdapter(
            config = AiPresentationConfig(
                inRideMaxMessagesPerMinute = 1,
                inRideSameTypeCooldownSec = 60,
            ),
        )
        val evaluation = AiPolicyEvaluation(
            allowed = listOf(
                candidate(phase = AiPhase.SESSION, type = AiRecommendationType.PACING),
                candidate(phase = AiPhase.SESSION, type = AiRecommendationType.CADENCE),
            ),
            suppressed = emptyList(),
        )

        val first = adapter.adapt(
            currentPhase = AiPhase.SESSION,
            evaluation = evaluation,
            nowMillis = 100_000L,
        )
        val blockedByWindow = adapter.adapt(
            currentPhase = AiPhase.SESSION,
            evaluation = evaluation,
            nowMillis = 100_010L,
            recentPresented = listOf(
                AiPresentedMessageRecord(
                    phase = AiPhase.SESSION,
                    type = first.messages.single().type,
                    presentedAtMillis = 100_000L,
                ),
            ),
        )

        assertEquals(1, first.messages.size)
        assertTrue(blockedByWindow.messages.isEmpty())
        assertTrue(blockedByWindow.keepExistingMessage)
        assertTrue(
            blockedByWindow.adapterSuppressed.all {
                it.reason == AiPresentationSuppressionReason.SESSION_RATE_LIMIT_WINDOW
            },
        )
    }

    @Test
    fun adapt_enforcesSessionSameTypeCooldown() {
        val adapter = AiPresentationAdapter(
            config = AiPresentationConfig(
                inRideMaxMessagesPerMinute = 2,
                inRideSameTypeCooldownSec = 60,
            ),
        )
        val pacing = candidate(
            phase = AiPhase.SESSION,
            type = AiRecommendationType.PACING,
        )
        val cadence = candidate(
            phase = AiPhase.SESSION,
            type = AiRecommendationType.CADENCE,
        )
        val evaluation = AiPolicyEvaluation(
            allowed = listOf(pacing, cadence),
            suppressed = emptyList(),
        )

        val result = adapter.adapt(
            currentPhase = AiPhase.SESSION,
            evaluation = evaluation,
            nowMillis = 200_000L,
            recentPresented = listOf(
                AiPresentedMessageRecord(
                    phase = AiPhase.SESSION,
                    type = AiRecommendationType.PACING,
                    presentedAtMillis = 199_980L,
                ),
            ),
        )

        assertEquals(1, result.messages.size)
        assertEquals(AiRecommendationType.CADENCE, result.messages.single().type)
        assertEquals(1, result.adapterSuppressed.size)
        assertEquals(
            AiPresentationSuppressionReason.SESSION_TYPE_COOLDOWN,
            result.adapterSuppressed.single().reason,
        )
    }

    @Test
    fun adapt_keepsUiStableWhenOnlySuppressedRecommendationsExist() {
        val adapter = AiPresentationAdapter()
        val suppressedByPolicy = AiSuppressedRecommendation(
            candidate = candidate(
                phase = AiPhase.SESSION,
                type = AiRecommendationType.PACING,
            ),
            reason = AiSuppressionReason.LOW_CONFIDENCE_PERFORMANCE_SUPPRESSED,
        )
        val evaluation = AiPolicyEvaluation(
            allowed = emptyList(),
            suppressed = listOf(suppressedByPolicy),
        )

        val result = adapter.adapt(
            currentPhase = AiPhase.SESSION,
            evaluation = evaluation,
            nowMillis = 300_000L,
        )

        assertTrue(result.messages.isEmpty())
        assertTrue(result.keepExistingMessage)
        assertEquals(1, result.policySuppressed.size)
        assertEquals(AiSuppressionReason.LOW_CONFIDENCE_PERFORMANCE_SUPPRESSED, result.policySuppressed.single().reason)
    }

    private fun candidate(
        phase: AiPhase,
        type: AiRecommendationType,
    ): AiRecommendationCandidate {
        return AiRecommendationCandidate(
            phase = phase,
            type = type,
            priority = AiRecommendationPriority.HIGH,
            confidence = AiQualityClass.HIGH,
            rationaleKeys = listOf("test.rule"),
            payload = AiRecommendationPayload(
                templateKey = "ai.test.$type",
            ),
        )
    }
}

package com.example.ergometerapp.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiPolicyGuardrailsTest {

    @Test
    fun disallowedRecommendationType_isSuppressedWithReasonCode() {
        val guardrails = AiPolicyGuardrails(
            config = AiPolicyConfig(
                disallowedTypes = setOf(AiRecommendationType.READINESS),
                enforceSafetyPrecedence = false,
            ),
        )
        val candidate = candidate(
            type = AiRecommendationType.READINESS,
            confidence = AiQualityClass.HIGH,
            templateKey = "ai.menu.readiness_reduce_intensity",
        )

        val result = guardrails.evaluate(listOf(candidate))
        assertTrue(result.allowed.isEmpty())
        assertEquals(1, result.suppressed.size)
        assertEquals(
            AiSuppressionReason.DISALLOWED_RECOMMENDATION_TYPE,
            result.suppressed.single().reason,
        )
    }

    @Test
    fun lowConfidencePerformance_isSuppressedWithReasonCode() {
        val guardrails = AiPolicyGuardrails(
            config = AiPolicyConfig(enforceSafetyPrecedence = false),
        )
        val lowConfidencePacing = candidate(
            type = AiRecommendationType.PACING,
            confidence = AiQualityClass.LOW,
            templateKey = "ai.session.pacing_hold_target",
        )

        val result = guardrails.evaluate(listOf(lowConfidencePacing))
        assertTrue(result.allowed.isEmpty())
        assertEquals(1, result.suppressed.size)
        assertEquals(
            AiSuppressionReason.LOW_CONFIDENCE_PERFORMANCE_SUPPRESSED,
            result.suppressed.single().reason,
        )
    }

    @Test
    fun advisoryOnlyViolation_isSuppressedWithReasonCode() {
        val guardrails = AiPolicyGuardrails(
            config = AiPolicyConfig(enforceSafetyPrecedence = false),
        )
        val commandLikeCandidate = candidate(
            type = AiRecommendationType.PACING,
            confidence = AiQualityClass.HIGH,
            templateKey = "ftms.control.set_target_power",
        )

        val result = guardrails.evaluate(listOf(commandLikeCandidate))
        assertTrue(result.allowed.isEmpty())
        assertEquals(1, result.suppressed.size)
        assertEquals(
            AiSuppressionReason.ADVISORY_ONLY_VIOLATION,
            result.suppressed.single().reason,
        )
    }

    @Test
    fun safetyPrecedence_suppressesLowerPriorityRecommendations() {
        val guardrails = AiPolicyGuardrails(
            config = AiPolicyConfig(enforceSafetyPrecedence = true),
        )
        val safety = candidate(
            type = AiRecommendationType.SAFETY,
            confidence = AiQualityClass.HIGH,
            templateKey = "ai.session.safety_reduce_effort",
        )
        val pacing = candidate(
            type = AiRecommendationType.PACING,
            confidence = AiQualityClass.HIGH,
            templateKey = "ai.session.pacing_hold_target",
        )

        val result = guardrails.evaluate(listOf(pacing, safety))
        assertEquals(1, result.allowed.size)
        assertEquals(AiRecommendationType.SAFETY, result.allowed.single().type)
        assertEquals(1, result.suppressed.size)
        assertEquals(
            AiSuppressionReason.LOWER_PRIORITY_SUPPRESSED_BY_PRECEDENCE,
            result.suppressed.single().reason,
        )
        assertEquals(AiRecommendationType.PACING, result.suppressed.single().candidate.type)
    }

    @Test
    fun safeRecommendation_passesWhenNoPolicyViolationExists() {
        val guardrails = AiPolicyGuardrails(
            config = AiPolicyConfig(enforceSafetyPrecedence = false),
        )
        val recovery = candidate(
            type = AiRecommendationType.RECOVERY,
            confidence = AiQualityClass.HIGH,
            templateKey = "ai.summary.recovery_reduce_next_load",
        )

        val result = guardrails.evaluate(listOf(recovery))
        assertEquals(1, result.allowed.size)
        assertTrue(result.suppressed.isEmpty())
    }

    private fun candidate(
        type: AiRecommendationType,
        confidence: AiQualityClass,
        templateKey: String,
    ): AiRecommendationCandidate {
        return AiRecommendationCandidate(
            phase = AiPhase.SESSION,
            type = type,
            priority = AiRecommendationPriority.HIGH,
            confidence = confidence,
            rationaleKeys = listOf("test.rule"),
            payload = AiRecommendationPayload(templateKey = templateKey),
        )
    }
}

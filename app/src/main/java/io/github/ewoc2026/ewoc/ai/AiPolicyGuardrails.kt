package io.github.ewoc2026.ewoc.ai

/**
 * Machine-readable suppression reason codes for policy decisions.
 */
enum class AiSuppressionReason {
    DISALLOWED_RECOMMENDATION_TYPE,
    LOW_CONFIDENCE_PERFORMANCE_SUPPRESSED,
    ADVISORY_ONLY_VIOLATION,
    LOWER_PRIORITY_SUPPRESSED_BY_PRECEDENCE,
}

/**
 * One suppressed recommendation with explicit reason metadata.
 */
data class AiSuppressedRecommendation(
    val candidate: AiRecommendationCandidate,
    val reason: AiSuppressionReason,
)

/**
 * Output of policy evaluation before UI rendering.
 */
data class AiPolicyEvaluation(
    val allowed: List<AiRecommendationCandidate>,
    val suppressed: List<AiSuppressedRecommendation>,
)

/**
 * Policy switches for recommendation guardrails.
 */
data class AiPolicyConfig(
    val disallowedTypes: Set<AiRecommendationType> = emptySet(),
    val lowConfidenceSuppressedTypes: Set<AiRecommendationType> = setOf(
        AiRecommendationType.PACING,
        AiRecommendationType.CADENCE,
    ),
    val advisoryOnlyBlockedTemplatePrefixes: Set<String> = setOf(
        "ftms.control.",
        "trainer.command.",
        "control.write.",
    ),
    val enforceSafetyPrecedence: Boolean = true,
)

/**
 * Mandatory guardrails that filter recommendations before presentation.
 */
class AiPolicyGuardrails(
    private val config: AiPolicyConfig = AiPolicyConfig(),
) {

    /**
     * Evaluates candidates and returns explicit allowed/suppressed partitions.
     */
    fun evaluate(candidates: List<AiRecommendationCandidate>): AiPolicyEvaluation {
        val allowed = mutableListOf<AiRecommendationCandidate>()
        val suppressed = mutableListOf<AiSuppressedRecommendation>()

        for (candidate in candidates) {
            val reason = suppressionReason(candidate)
            if (reason != null) {
                suppressed += AiSuppressedRecommendation(
                    candidate = candidate,
                    reason = reason,
                )
            } else {
                allowed += candidate
            }
        }

        if (!config.enforceSafetyPrecedence || allowed.isEmpty()) {
            return AiPolicyEvaluation(
                allowed = allowed,
                suppressed = suppressed,
            )
        }

        val strongestRank = allowed.minOf { precedenceRank(it.type) }
        val byPrecedence = allowed.partition { precedenceRank(it.type) == strongestRank }
        val precedenceAllowed = byPrecedence.first
        val precedenceSuppressed = byPrecedence.second.map { candidate ->
            AiSuppressedRecommendation(
                candidate = candidate,
                reason = AiSuppressionReason.LOWER_PRIORITY_SUPPRESSED_BY_PRECEDENCE,
            )
        }

        return AiPolicyEvaluation(
            allowed = precedenceAllowed,
            suppressed = suppressed + precedenceSuppressed,
        )
    }

    private fun suppressionReason(candidate: AiRecommendationCandidate): AiSuppressionReason? {
        if (candidate.type in config.disallowedTypes) {
            return AiSuppressionReason.DISALLOWED_RECOMMENDATION_TYPE
        }
        if (candidate.confidence == AiQualityClass.LOW &&
            candidate.type in config.lowConfidenceSuppressedTypes
        ) {
            return AiSuppressionReason.LOW_CONFIDENCE_PERFORMANCE_SUPPRESSED
        }
        if (violatesAdvisoryOnly(candidate)) {
            return AiSuppressionReason.ADVISORY_ONLY_VIOLATION
        }
        return null
    }

    private fun violatesAdvisoryOnly(candidate: AiRecommendationCandidate): Boolean {
        val key = candidate.payload.templateKey.lowercase()
        return config.advisoryOnlyBlockedTemplatePrefixes.any { prefix ->
            key.startsWith(prefix.lowercase())
        }
    }

    private fun precedenceRank(type: AiRecommendationType): Int {
        return when (type) {
            AiRecommendationType.CONNECTIVITY -> 0
            AiRecommendationType.SAFETY -> 1
            AiRecommendationType.READINESS -> 2
            AiRecommendationType.RECOVERY -> 2
            AiRecommendationType.PACING -> 3
            AiRecommendationType.CADENCE -> 3
        }
    }
}

package io.github.ewoc2026.ewoc.ai

/**
 * UI surface target for recommendation rendering.
 */
enum class AiPresentationSurface {
    MENU_ASSISTANT,
    SESSION_ASSISTANT,
    SUMMARY_ASSISTANT,
}

/**
 * Adapter-level suppression reasons for candidates hidden before rendering.
 */
enum class AiPresentationSuppressionReason {
    PHASE_MISMATCH,
    SESSION_RATE_LIMIT_WINDOW,
    SESSION_TYPE_COOLDOWN,
}

/**
 * One rendered assistant message mapped from a recommendation candidate.
 */
data class AiPresentationMessage(
    val phase: AiPhase,
    val surface: AiPresentationSurface,
    val type: AiRecommendationType,
    val confidence: AiQualityClass,
    val templateKey: String,
    val templateArgs: Map<String, String>,
)

/**
 * Previously rendered message metadata used by anti-spam logic.
 */
data class AiPresentedMessageRecord(
    val phase: AiPhase,
    val type: AiRecommendationType,
    val presentedAtMillis: Long,
)

/**
 * Candidate suppressed by presentation adapter logic.
 */
data class AiPresentationSuppressedCandidate(
    val candidate: AiRecommendationCandidate,
    val reason: AiPresentationSuppressionReason,
)

/**
 * Result of one adapter pass.
 *
 * When [messages] is empty, [keepExistingMessage] remains true to avoid UI
 * flicker caused by fast suppression/recovery cycles.
 */
data class AiPresentationDecision(
    val messages: List<AiPresentationMessage>,
    val keepExistingMessage: Boolean,
    val policySuppressed: List<AiSuppressedRecommendation>,
    val adapterSuppressed: List<AiPresentationSuppressedCandidate>,
)

/**
 * Presentation-level anti-spam settings.
 */
data class AiPresentationConfig(
    val inRideMaxMessagesPerMinute: Int = 1,
    val inRideSameTypeCooldownSec: Int = 60,
) {
    init {
        require(inRideMaxMessagesPerMinute > 0) {
            "inRideMaxMessagesPerMinute must be > 0"
        }
        require(inRideSameTypeCooldownSec >= 0) {
            "inRideSameTypeCooldownSec must be >= 0"
        }
    }
}

/**
 * Adapter that maps policy-approved recommendations to concrete UI surfaces.
 */
class AiPresentationAdapter(
    private val config: AiPresentationConfig = AiPresentationConfig(),
) {

    /**
     * Maps policy output to phase-specific UI messages.
     */
    fun adapt(
        currentPhase: AiPhase,
        evaluation: AiPolicyEvaluation,
        nowMillis: Long,
        recentPresented: List<AiPresentedMessageRecord> = emptyList(),
    ): AiPresentationDecision {
        val adapterSuppressed = mutableListOf<AiPresentationSuppressedCandidate>()

        val phaseMatched = evaluation.allowed.filter { candidate ->
            val matches = candidate.phase == currentPhase
            if (!matches) {
                adapterSuppressed += AiPresentationSuppressedCandidate(
                    candidate = candidate,
                    reason = AiPresentationSuppressionReason.PHASE_MISMATCH,
                )
            }
            matches
        }

        val spamFiltered = if (currentPhase == AiPhase.SESSION) {
            applySessionAntiSpam(
                candidates = phaseMatched,
                nowMillis = nowMillis,
                recentPresented = recentPresented,
                suppressed = adapterSuppressed,
            )
        } else {
            phaseMatched
        }

        val mapped = spamFiltered.map { candidate ->
            AiPresentationMessage(
                phase = candidate.phase,
                surface = surfaceFor(candidate.phase),
                type = candidate.type,
                confidence = candidate.confidence,
                templateKey = candidate.payload.templateKey,
                templateArgs = candidate.payload.templateArgs,
            )
        }

        return AiPresentationDecision(
            messages = mapped,
            keepExistingMessage = mapped.isEmpty(),
            policySuppressed = evaluation.suppressed,
            adapterSuppressed = adapterSuppressed,
        )
    }

    private fun applySessionAntiSpam(
        candidates: List<AiRecommendationCandidate>,
        nowMillis: Long,
        recentPresented: List<AiPresentedMessageRecord>,
        suppressed: MutableList<AiPresentationSuppressedCandidate>,
    ): List<AiRecommendationCandidate> {
        if (candidates.isEmpty()) return emptyList()

        val oneMinuteWindowStart = nowMillis - 60_000L
        val recentSessionMessages = recentPresented.filter { presented ->
            presented.phase == AiPhase.SESSION &&
                presented.presentedAtMillis >= oneMinuteWindowStart
        }
        val availableSlots = (config.inRideMaxMessagesPerMinute - recentSessionMessages.size)
            .coerceAtLeast(0)
        if (availableSlots == 0) {
            candidates.forEach { candidate ->
                suppressed += AiPresentationSuppressedCandidate(
                    candidate = candidate,
                    reason = AiPresentationSuppressionReason.SESSION_RATE_LIMIT_WINDOW,
                )
            }
            return emptyList()
        }

        val cooldownMillis = config.inRideSameTypeCooldownSec * 1000L
        val afterCooldown = candidates.filter { candidate ->
            val blocked = recentPresented.any { presented ->
                presented.phase == AiPhase.SESSION &&
                    presented.type == candidate.type &&
                    nowMillis - presented.presentedAtMillis < cooldownMillis
            }
            if (blocked) {
                suppressed += AiPresentationSuppressedCandidate(
                    candidate = candidate,
                    reason = AiPresentationSuppressionReason.SESSION_TYPE_COOLDOWN,
                )
            }
            !blocked
        }

        return afterCooldown.take(availableSlots)
    }

    private fun surfaceFor(phase: AiPhase): AiPresentationSurface {
        return when (phase) {
            AiPhase.MENU -> AiPresentationSurface.MENU_ASSISTANT
            AiPhase.SESSION -> AiPresentationSurface.SESSION_ASSISTANT
            AiPhase.SUMMARY -> AiPresentationSurface.SUMMARY_ASSISTANT
        }
    }
}

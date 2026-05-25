package com.ewo.editor.desktop.state

import com.ewo.core.EwoEngine
import com.ewo.editor.desktop.EditorStrings

/**
 * Keeps desktop tag editing aligned with the canonical `.ewo` slug contract.
 *
 * The property inspector rejects impossible canonical input immediately so
 * authors are guided toward valid tags before export or re-open time.
 */
internal object CanonicalTagInputPolicy {
    private val partialTagRegex = Regex("^[a-z0-9_-]{0,32}$")

    sealed interface Evaluation {
        data class Accepted(
            val rawText: String,
            val tags: List<String>,
        ) : Evaluation

        data class Rejected(
            val message: String,
        ) : Evaluation
    }

    fun format(tags: List<String>): String = tags.joinToString(", ")

    fun guidanceText(): String {
        val rules = EwoEngine.canonicalTagRules
        return EditorStrings.tagFieldGuidance(rules.maxCount)
    }

    fun evaluate(proposedText: String): Evaluation {
        val segments = proposedText.split(",")
        val hasTrailingDraft = proposedText.isNotBlank() && segments.last().trim().isEmpty()
        val parsedTags = mutableListOf<String>()

        for ((index, segment) in segments.withIndex()) {
            val tag = segment.trim()
            if (tag.isEmpty()) {
                val isTrailingDraft = hasTrailingDraft && index == segments.lastIndex
                if (proposedText.isBlank() || isTrailingDraft) {
                    continue
                }
                return Evaluation.Rejected(EditorStrings.tagFieldErrorCommas)
            }
            if (!partialTagRegex.matches(tag)) {
                return Evaluation.Rejected(EditorStrings.tagFieldErrorAllowedChars)
            }
            parsedTags += tag
        }

        val rules = EwoEngine.canonicalTagRules
        if (hasTrailingDraft && parsedTags.size >= rules.maxCount) {
            return Evaluation.Rejected(EditorStrings.tagFieldErrorTooMany(rules.maxCount))
        }

        return when (val issue = EwoEngine.firstCanonicalTagIssue(parsedTags)) {
            null -> Evaluation.Accepted(
                rawText = proposedText,
                tags = parsedTags,
            )

            else -> Evaluation.Rejected(issue.message())
        }
    }

    private fun EwoEngine.CanonicalTagIssue.message(): String {
        return when (kind) {
            EwoEngine.CanonicalTagIssue.Kind.INVALID_TAG ->
                EditorStrings.tagFieldErrorInvalidTag(tag.orEmpty())

            EwoEngine.CanonicalTagIssue.Kind.TOO_MANY_TAGS ->
                EditorStrings.tagFieldErrorTooMany(EwoEngine.canonicalTagRules.maxCount)
        }
    }
}

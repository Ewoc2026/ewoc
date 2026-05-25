package com.ewo.core

/**
 * Single public facade for all EWO core operations.
 *
 * Editors and applications should use this interface rather than calling internal
 * parser/validator/compiler/sanity classes directly.
 */
object EwoEngine {

    /**
     * Canonical workout-tag rules shared across parse, import, and authoring flows.
     *
     * Editors can surface these rules before export so authors learn the stable
     * machine-friendly slug contract while they type.
     */
    data class CanonicalTagRules(
        val pattern: String,
        val maxCount: Int,
    )

    /**
     * First canonical-tag issue found while validating an editor-supplied tag list.
     *
     * Returning a structured issue keeps UI copy outside the core module while
     * still sharing the exact canonical acceptance rules.
     */
    data class CanonicalTagIssue(
        val kind: Kind,
        val tag: String? = null,
    ) {
        enum class Kind {
            INVALID_TAG,
            TOO_MANY_TAGS,
        }
    }

    /**
     * Parse helper for import/open intake where root workout tags are treated as
     * optional metadata rather than a hard blocker.
     *
     * The underlying canonical parser remains strict. This helper only retries
     * once after dropping invalid or surplus root `tags` items.
     */
    data class TagDroppingParseResult(
        val parseResult: EwoWorkoutParseResult,
        val droppedTags: List<String> = emptyList(),
    )

    val canonicalTagRules = CanonicalTagRules(
        pattern = EwoWorkoutTagPolicy.pattern,
        maxCount = EwoWorkoutTagPolicy.maxCount,
    )

    /**
     * Returns the first canonical-tag issue for an editor-provided tag list.
     *
     * The canonical parser still owns final enforcement; this helper exists so
     * authoring UIs can prevent invalid tags before export/import time.
     */
    fun firstCanonicalTagIssue(tags: List<String>): CanonicalTagIssue? {
        if (tags.size > canonicalTagRules.maxCount) {
            return CanonicalTagIssue(kind = CanonicalTagIssue.Kind.TOO_MANY_TAGS)
        }
        val invalidTag = tags.firstOrNull { !EwoWorkoutTagPolicy.isValid(it) }
            ?: return null
        return CanonicalTagIssue(
            kind = CanonicalTagIssue.Kind.INVALID_TAG,
            tag = invalidTag,
        )
    }

    /**
     * Reads a canonical `.ewo` JSON payload under its own version rules.
     * Does **not** silently migrate to a newer version.
     */
    fun parse(json: String, ftpWatts: Int? = null): EwoWorkoutParseResult {
        return EwoWorkoutParser.parse(json, EwoCompileContext(ftpWatts = ftpWatts))
    }

    /** Overload accepting a full compile context for HR-relative target resolution. */
    fun parse(json: String, context: EwoCompileContext): EwoWorkoutParseResult {
        return EwoWorkoutParser.parse(json, context)
    }

    /**
     * Import/open helper that tolerates malformed root `tags` metadata.
     *
     * This keeps metadata-only tag issues from blocking otherwise usable
     * workouts while preserving strict canonical parsing for all other fields.
     */
    fun parseDroppingInvalidTags(json: String, ftpWatts: Int? = null): TagDroppingParseResult {
        return parseDroppingInvalidTags(json, EwoCompileContext(ftpWatts = ftpWatts))
    }

    /**
     * Import/open helper that tolerates malformed root `tags` metadata.
     */
    fun parseDroppingInvalidTags(
        json: String,
        context: EwoCompileContext,
    ): TagDroppingParseResult {
        val strictResult = EwoWorkoutParser.parse(json, context)
        val strictFailure = strictResult as? EwoWorkoutParseResult.Failure
            ?: return TagDroppingParseResult(parseResult = strictResult)

        if (
            strictFailure.error.code != EwoWorkoutValidationErrorCode.INVALID_TAG &&
            strictFailure.error.code != EwoWorkoutValidationErrorCode.TOO_MANY_TAGS
        ) {
            return TagDroppingParseResult(parseResult = strictResult)
        }

        val sanitization = EwoWorkoutTagSanitizer.sanitize(json)
            ?: return TagDroppingParseResult(parseResult = strictResult)

        return TagDroppingParseResult(
            parseResult = EwoWorkoutParser.parse(sanitization.sanitizedJson, context),
            droppedTags = sanitization.droppedTags,
        )
    }

    /**
     * Composed operation: parse + validate + compile + sanity in one call.
     * Preferred for editor use. Does not mutate input; returns a structured result.
     *
     * This is semantically identical to [parse] today but exists as a separate entry point
     * so the editor can rely on a stable "give me everything" API even if the internal
     * pipeline evolves.
     */
    fun analyze(json: String, ftpWatts: Int? = null): EwoWorkoutParseResult {
        return EwoWorkoutParser.parse(json, EwoCompileContext(ftpWatts = ftpWatts))
    }

    /** Overload accepting a full compile context for HR-relative target resolution. */
    fun analyze(json: String, context: EwoCompileContext): EwoWorkoutParseResult {
        return EwoWorkoutParser.parse(json, context)
    }
}

package com.ewo.core.zwo

import com.ewo.core.ParsedEwoWorkoutFile

/**
 * Result of importing a `.zwo` file into the EWO domain.
 */
sealed class ZwoImportResult {

    /**
     * Import succeeded. [workout] is a canonical EWO parsed representation
     * suitable for serialization or further processing via [com.ewo.core.EwoEngine].
     *
     * [warnings] lists any source constructs that were partially supported,
     * dropped, or converted with lossy semantics.
     */
    data class Success(
        val workout: ParsedEwoWorkoutFile,
        val warnings: List<ZwoImportWarning>,
    ) : ZwoImportResult()

    /** Import failed due to malformed XML or an unrecoverable structural problem. */
    data class Failure(
        val reason: String,
    ) : ZwoImportResult()
}

/** Stable warning codes for ZWO import. */
enum class ZwoImportWarningCode(val stableCode: String) {
    /** A step was missing its Duration attribute and was skipped. */
    MISSING_DURATION("missing_duration"),

    /** A step was missing its power target attributes and was skipped. */
    MISSING_POWER_TARGET("missing_power_target"),

    /** A ZWO step type is not supported and was skipped. */
    UNSUPPORTED_STEP_TYPE("unsupported_step_type"),

    /** ZWO text events were present but not imported. */
    TEXT_EVENTS_NOT_IMPORTED("text_events_not_imported"),

    /** ZWO heart-rate metadata was present but not imported. */
    HEART_RATE_TARGETS_NOT_IMPORTED("heart_rate_targets_not_imported"),

    /** MaxEffort was mapped to a free-ride segment. */
    MAX_EFFORT_AS_FREE_RIDE("max_effort_as_free_ride"),

    /** An IntervalsT block was missing required attributes and was skipped. */
    INCOMPLETE_INTERVALS("incomplete_intervals"),
}

/** A single warning produced during ZWO import. */
data class ZwoImportWarning(
    val code: ZwoImportWarningCode,
    val message: String,
)

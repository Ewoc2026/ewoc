package com.ewo.core

/**
 * Stable validation error codes for canonical `.ewo` v1 parsing.
 */
enum class EwoWorkoutValidationErrorCode(val stableCode: String) {
    INVALID_JSON("invalid_json"),
    UNKNOWN_FIELD("unknown_field"),
    MISSING_REQUIRED_FIELD("missing_required_field"),
    INVALID_TYPE("invalid_type"),
    INVALID_FORMAT("invalid_format"),
    UNSUPPORTED_VERSION("unsupported_version"),
    EMPTY_TITLE("empty_title"),
    EMPTY_SEGMENTS("empty_segments"),
    UNKNOWN_SEGMENT_TYPE("unknown_segment_type"),
    INVALID_DURATION_SEC("invalid_duration_sec"),
    INVALID_REPEAT_COUNT("invalid_repeat_count"),
    REPEAT_SEGMENTS_TOO_SHORT("repeat_segments_too_short"),
    REPEAT_CHILD_TYPE_NOT_ALLOWED("repeat_child_type_not_allowed"),
    INVALID_TARGET_METRIC("invalid_target_metric"),
    INVALID_POWER_TARGET_VALUE("invalid_power_target_value"),
    INVALID_FTP_PERCENT_VALUE("invalid_ftp_percent_value"),
    INVALID_HEART_RATE_RANGE("invalid_heart_rate_range"),
    RAMP_MIXED_TARGET_METRICS("ramp_mixed_target_metrics"),
    RAMP_INVALID_TARGET_METRIC("ramp_invalid_target_metric"),
    FTP_REQUIRED_FOR_FTP_PERCENT("ftp_required_for_ftp_percent"),
    FTP_VALUE_OUT_OF_REASONABLE_RANGE("ftp_value_out_of_reasonable_range"),
    CONTROL_REQUIRED_FOR_HEART_RATE("control_required_for_heart_rate"),
    INVALID_CONTROL_BOUNDS("invalid_control_bounds"),
    CONTROL_VALUE_OUT_OF_BOUNDS("control_value_out_of_bounds"),
    INVALID_HR_UPPER_CAP_BPM("invalid_hr_upper_cap_bpm"),
    INVALID_MESSAGE_KIND("invalid_message_kind"),
    INVALID_MESSAGE_WHEN("invalid_message_when"),
    INVALID_MESSAGE_OFFSET_SEC("invalid_message_offset_sec"),
    INVALID_LOCALIZED_TEXT("invalid_localized_text"),
    INVALID_SEGMENT_ID("invalid_segment_id"),
    DUPLICATE_SEGMENT_ID("duplicate_segment_id"),
    TOTAL_DURATION_OVERFLOW("total_duration_overflow"),
    INVALID_CADENCE_RANGE("invalid_cadence_range"),
    INVALID_DIFFICULTY("invalid_difficulty"),
    INVALID_TAG("invalid_tag"),
    TOO_MANY_TAGS("too_many_tags"),
    INVALID_HR_RELATIVE_REFERENCE("invalid_hr_relative_reference"),
    INVALID_HR_RELATIVE_RANGE("invalid_hr_relative_range"),
}

/**
 * First stable validation error surfaced by the canonical parser.
 */
data class EwoWorkoutValidationError(
    val code: EwoWorkoutValidationErrorCode,
    val message: String,
    val fieldPath: String,
)

/**
 * Parser output keeps each incremental representation available for follow-up integration slices.
 */
sealed class EwoWorkoutParseResult {
    data class Failure(
        val error: EwoWorkoutValidationError,
    ) : EwoWorkoutParseResult()

    sealed class Success : EwoWorkoutParseResult() {
        abstract val parsed: ParsedEwoWorkoutFile
        abstract val normalized: NormalizedEwoWorkout
        abstract val sanityResult: SanityCheckResult

        data class Compiled(
            override val parsed: ParsedEwoWorkoutFile,
            override val normalized: NormalizedEwoWorkout,
            val compiled: CompiledEwoWorkout,
            override val sanityResult: SanityCheckResult,
        ) : Success()

        data class NeedsCompileContext(
            override val parsed: ParsedEwoWorkoutFile,
            override val normalized: NormalizedEwoWorkout,
            val compileErrors: List<EwoCompileError>,
            override val sanityResult: SanityCheckResult = SanityCheckResult.clean,
        ) : Success()
    }
}

data class ParsedEwoWorkoutFile(
    val format: String,
    val version: String,
    val uid: String?,
    val revision: Int?,
    val title: String,
    val description: String?,
    val titleLocalized: ParsedEwoLocalizedText?,
    val descriptionLocalized: ParsedEwoLocalizedText?,
    val difficulty: String?,
    val tags: List<String>,
    val control: ParsedEwoWorkoutControl?,
    val messages: List<ParsedEwoMessage>,
    val segments: List<ParsedEwoSegment>,
)

data class ParsedEwoCadenceRange(val low: Int, val high: Int)

data class ParsedEwoWorkoutControl(
    val initialPowerWatts: Int,
    val minPowerWatts: Int,
    val maxPowerWatts: Int,
    val signalLossPowerWatts: Int,
    val hrUpperCapBpm: Int,
)

data class ParsedEwoMessage(
    val kind: String,
    val timing: ParsedEwoMessageTiming,
    val text: ParsedEwoLocalizedText,
)

data class ParsedEwoMessageTiming(
    val anchor: String,
    val offsetSec: Int,
)

data class ParsedEwoLocalizedText(
    val defaultText: String,
    val translations: Map<String, String>,
)

sealed class ParsedEwoSegment {
    abstract val id: String
    abstract val label: String?
    abstract val note: String?
    abstract val messages: List<ParsedEwoMessage>

    data class Steady(
        override val id: String,
        override val label: String?,
        override val note: String?,
        override val messages: List<ParsedEwoMessage>,
        val durationSec: Int,
        val target: ParsedEwoTarget,
        val cadence: ParsedEwoCadenceRange?,
    ) : ParsedEwoSegment()

    data class Ramp(
        override val id: String,
        override val label: String?,
        override val note: String?,
        override val messages: List<ParsedEwoMessage>,
        val durationSec: Int,
        val fromTarget: ParsedEwoTarget,
        val toTarget: ParsedEwoTarget,
        val cadence: ParsedEwoCadenceRange?,
    ) : ParsedEwoSegment()

    data class FreeRide(
        override val id: String,
        override val label: String?,
        override val note: String?,
        override val messages: List<ParsedEwoMessage>,
        val durationSec: Int,
        val cadence: ParsedEwoCadenceRange?,
    ) : ParsedEwoSegment()

    data class Repeat(
        override val id: String,
        override val label: String?,
        override val note: String?,
        override val messages: List<ParsedEwoMessage>,
        val count: Int,
        val segments: List<ParsedEwoSegment>,
    ) : ParsedEwoSegment()
}

sealed class ParsedEwoTarget {
    data class Power(
        val value: Int,
    ) : ParsedEwoTarget()

    data class FtpPercent(
        val fraction: Double,
    ) : ParsedEwoTarget()

    data class HeartRateRange(
        val low: Int,
        val high: Int,
    ) : ParsedEwoTarget()

    data class HeartRateRelativeRange(
        val reference: String,
        val low: Double,
        val high: Double,
    ) : ParsedEwoTarget()
}

/** Reference basis for heart_rate_relative targets. */
enum class HrReference(val stableCode: String) {
    HR_MAX("hr_max"),
    HEART_RATE_RESERVE("heart_rate_reserve"),
    LTHR("lthr"),
    ;
    companion object {
        fun fromCode(code: String): HrReference? = entries.firstOrNull { it.stableCode == code }
    }
}

/** Athlete profile data needed to resolve relative targets at compile time. */
data class EwoCompileContext(
    val ftpWatts: Int? = null,
    val hrMaxBpm: Int? = null,
    val restingHrBpm: Int? = null,
    val lthrBpm: Int? = null,
)

/** Stable error codes for compile-time context resolution failures. */
enum class EwoCompileErrorCode(val stableCode: String) {
    MISSING_FTP("missing_ftp"),
    MISSING_HR_MAX("missing_hr_max"),
    MISSING_RESTING_HR("missing_resting_hr"),
    MISSING_LTHR("missing_lthr"),
}

/** Structured compile-time error for missing athlete profile data. */
data class EwoCompileError(
    val code: EwoCompileErrorCode,
    val message: String,
    val segmentId: String?,
)

data class EwoCadenceRange(val low: Int, val high: Int)

enum class EwoDifficulty { EASY, MODERATE, HARD, VERY_HARD }

enum class SanitySeverity { WARNING, ERROR }

enum class SanityIssueCode(val stableCode: String) {
    SUSTAINED_HIGH_INTENSITY("sustained_high_intensity"),
    SPRINT_RECOVERY_TOO_SHORT("sprint_recovery_too_short"),
    HR_INTERVAL_TOO_SHORT("hr_interval_too_short"),
    RAMP_TOO_STEEP_FOR_DURATION("ramp_too_steep_for_duration"),
    MISSING_WARMUP("missing_warmup"),
}

data class SanityIssue(
    val code: SanityIssueCode,
    val severity: SanitySeverity,
    val message: String,
    val segmentId: String?,
)

data class SanityCheckResult(
    val issues: List<SanityIssue>,
) {
    val hasErrors: Boolean get() = issues.any { it.severity == SanitySeverity.ERROR }
    companion object {
        val clean = SanityCheckResult(issues = emptyList())
    }
}

data class EwoWorkoutControl(
    val initialPowerWatts: Int,
    val minPowerWatts: Int,
    val maxPowerWatts: Int,
    val signalLossPowerWatts: Int,
    val hrUpperCapBpm: Int,
)

data class EwoMessage(
    val kind: EwoMessageKind,
    val timing: EwoMessageTiming,
    val text: EwoLocalizedText,
)

enum class EwoMessageKind {
    INTRO,
    INSTRUCTION,
    TRANSITION,
    WARNING,
    MOTIVATION,
}

enum class EwoMessageAnchor {
    START,
    END,
}

data class EwoMessageTiming(
    val anchor: EwoMessageAnchor,
    val offsetSec: Int,
)

data class EwoLocalizedText(
    val defaultText: String,
    val translations: Map<String, String>,
)

data class NormalizedEwoWorkout(
    val uid: String?,
    val revision: Int?,
    val title: String,
    val description: String?,
    val titleLocalized: EwoLocalizedText?,
    val descriptionLocalized: EwoLocalizedText?,
    val difficulty: EwoDifficulty?,
    val tags: List<String>,
    val control: EwoWorkoutControl?,
    val messages: List<EwoMessage>,
    val segments: List<NormalizedEwoWorkoutSegment>,
)

sealed class NormalizedEwoWorkoutSegment {
    abstract val id: String
    abstract val label: String?
    abstract val note: String?
    abstract val messages: List<EwoMessage>

    data class PowerSteady(
        override val id: String,
        override val label: String?,
        override val note: String?,
        override val messages: List<EwoMessage>,
        val durationSec: Int,
        val watts: Int,
        val cadence: EwoCadenceRange?,
    ) : NormalizedEwoWorkoutSegment()

    data class FtpPercentSteady(
        override val id: String,
        override val label: String?,
        override val note: String?,
        override val messages: List<EwoMessage>,
        val durationSec: Int,
        val fraction: Double,
        val cadence: EwoCadenceRange?,
    ) : NormalizedEwoWorkoutSegment()

    data class HeartRateSteady(
        override val id: String,
        override val label: String?,
        override val note: String?,
        override val messages: List<EwoMessage>,
        val durationSec: Int,
        val lowBpm: Int,
        val highBpm: Int,
        val cadence: EwoCadenceRange?,
    ) : NormalizedEwoWorkoutSegment()

    data class PowerRamp(
        override val id: String,
        override val label: String?,
        override val note: String?,
        override val messages: List<EwoMessage>,
        val durationSec: Int,
        val fromWatts: Int,
        val toWatts: Int,
        val cadence: EwoCadenceRange?,
    ) : NormalizedEwoWorkoutSegment()

    data class FtpPercentRamp(
        override val id: String,
        override val label: String?,
        override val note: String?,
        override val messages: List<EwoMessage>,
        val durationSec: Int,
        val fromFraction: Double,
        val toFraction: Double,
        val cadence: EwoCadenceRange?,
    ) : NormalizedEwoWorkoutSegment()

    data class HeartRateRelativeSteady(
        override val id: String,
        override val label: String?,
        override val note: String?,
        override val messages: List<EwoMessage>,
        val durationSec: Int,
        val reference: HrReference,
        val lowFraction: Double,
        val highFraction: Double,
        val cadence: EwoCadenceRange?,
    ) : NormalizedEwoWorkoutSegment()

    data class FreeRide(
        override val id: String,
        override val label: String?,
        override val note: String?,
        override val messages: List<EwoMessage>,
        val durationSec: Int,
        val cadence: EwoCadenceRange?,
    ) : NormalizedEwoWorkoutSegment()

    data class Repeat(
        override val id: String,
        override val label: String?,
        override val note: String?,
        override val messages: List<EwoMessage>,
        val count: Int,
        val segments: List<NormalizedEwoWorkoutRepeatSegment>,
    ) : NormalizedEwoWorkoutSegment()
}

sealed class NormalizedEwoWorkoutRepeatSegment {
    abstract val id: String
    abstract val label: String?
    abstract val note: String?
    abstract val messages: List<EwoMessage>

    data class PowerSteady(
        override val id: String,
        override val label: String?,
        override val note: String?,
        override val messages: List<EwoMessage>,
        val durationSec: Int,
        val watts: Int,
        val cadence: EwoCadenceRange?,
    ) : NormalizedEwoWorkoutRepeatSegment()

    data class FtpPercentSteady(
        override val id: String,
        override val label: String?,
        override val note: String?,
        override val messages: List<EwoMessage>,
        val durationSec: Int,
        val fraction: Double,
        val cadence: EwoCadenceRange?,
    ) : NormalizedEwoWorkoutRepeatSegment()

    data class HeartRateSteady(
        override val id: String,
        override val label: String?,
        override val note: String?,
        override val messages: List<EwoMessage>,
        val durationSec: Int,
        val lowBpm: Int,
        val highBpm: Int,
        val cadence: EwoCadenceRange?,
    ) : NormalizedEwoWorkoutRepeatSegment()

    data class HeartRateRelativeSteady(
        override val id: String,
        override val label: String?,
        override val note: String?,
        override val messages: List<EwoMessage>,
        val durationSec: Int,
        val reference: HrReference,
        val lowFraction: Double,
        val highFraction: Double,
        val cadence: EwoCadenceRange?,
    ) : NormalizedEwoWorkoutRepeatSegment()

    data class FreeRide(
        override val id: String,
        override val label: String?,
        override val note: String?,
        override val messages: List<EwoMessage>,
        val durationSec: Int,
        val cadence: EwoCadenceRange?,
    ) : NormalizedEwoWorkoutRepeatSegment()
}

data class CompiledEwoWorkout(
    val uid: String?,
    val revision: Int?,
    val title: String,
    val description: String?,
    val titleLocalized: EwoLocalizedText?,
    val descriptionLocalized: EwoLocalizedText?,
    val difficulty: EwoDifficulty?,
    val tags: List<String>,
    val messages: List<EwoMessage>,
    val steps: List<CompiledEwoWorkoutStep>,
    val totalDurationSec: Int,
)

sealed class CompiledEwoWorkoutStep {
    abstract val stepIndex: Int
    abstract val startOffsetSec: Int
    abstract val durationSec: Int
    abstract val messages: List<EwoMessage>
    abstract val origin: CompiledEwoSegmentOrigin

    data class PowerSteady(
        override val stepIndex: Int,
        override val startOffsetSec: Int,
        override val durationSec: Int,
        override val messages: List<EwoMessage>,
        override val origin: CompiledEwoSegmentOrigin,
        val watts: Int,
        val cadence: EwoCadenceRange?,
    ) : CompiledEwoWorkoutStep()

    data class PowerRamp(
        override val stepIndex: Int,
        override val startOffsetSec: Int,
        override val durationSec: Int,
        override val messages: List<EwoMessage>,
        override val origin: CompiledEwoSegmentOrigin,
        val fromWatts: Int,
        val toWatts: Int,
        val cadence: EwoCadenceRange?,
    ) : CompiledEwoWorkoutStep()

    data class HeartRateSteady(
        override val stepIndex: Int,
        override val startOffsetSec: Int,
        override val durationSec: Int,
        override val messages: List<EwoMessage>,
        override val origin: CompiledEwoSegmentOrigin,
        val lowBpm: Int,
        val highBpm: Int,
        val initialPowerWatts: Int,
        val minPowerWatts: Int,
        val maxPowerWatts: Int,
        val signalLossPowerWatts: Int,
        val hrUpperCapBpm: Int,
        val cadence: EwoCadenceRange?,
    ) : CompiledEwoWorkoutStep()

    data class FreeRide(
        override val stepIndex: Int,
        override val startOffsetSec: Int,
        override val durationSec: Int,
        override val messages: List<EwoMessage>,
        override val origin: CompiledEwoSegmentOrigin,
        val cadence: EwoCadenceRange?,
    ) : CompiledEwoWorkoutStep()
}

data class CompiledEwoSegmentOrigin(
    val sourceSegmentId: String,
    val sourceSegmentLabel: String?,
    val sourceSegmentNote: String?,
    val enclosingRepeatSegmentId: String?,
    val repeatIterationIndex: Int?,
)

class EwoWorkoutValidationException(
    val error: EwoWorkoutValidationError,
) : IllegalArgumentException(error.message)

/** Thrown when compilation fails due to missing athlete profile data. */
class EwoCompileContextException(
    val compileError: EwoCompileError,
) : IllegalArgumentException(compileError.message)

internal fun failEwoValidation(
    code: EwoWorkoutValidationErrorCode,
    message: String,
    fieldPath: String,
): Nothing {
    throw EwoWorkoutValidationException(
        error = EwoWorkoutValidationError(
            code = code,
            message = message,
            fieldPath = fieldPath,
        ),
    )
}

internal fun ewoChildPath(parentPath: String, fieldName: String): String = "$parentPath.$fieldName"

internal fun ewoIndexPath(parentPath: String, index: Int): String = "$parentPath[$index]"

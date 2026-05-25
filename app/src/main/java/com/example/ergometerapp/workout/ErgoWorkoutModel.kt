package com.example.ergometerapp.workout

/**
 * Stable validation error codes for frozen ergo_workout v0.1 parsing.
 */
internal enum class ErgoWorkoutValidationErrorCode(val stableCode: String) {
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
    REPEAT_STEPS_TOO_SHORT("repeat_steps_too_short"),
    REPEAT_CHILD_TYPE_NOT_ALLOWED("repeat_child_type_not_allowed"),
    INVALID_TARGET_METRIC("invalid_target_metric"),
    INVALID_POWER_TARGET_VALUE("invalid_power_target_value"),
    INVALID_HEART_RATE_RANGE("invalid_heart_rate_range"),
    RAMP_TARGET_MUST_BE_POWER("ramp_target_must_be_power"),
    CONTROL_REQUIRED_FOR_HEART_RATE("control_required_for_heart_rate"),
    INVALID_CONTROL_BOUNDS("invalid_control_bounds"),
    CONTROL_VALUE_OUT_OF_BOUNDS("control_value_out_of_bounds"),
    TOTAL_DURATION_OVERFLOW("total_duration_overflow"),
}

/**
 * First stable validation error surfaced by the frozen parser.
 */
internal data class ErgoWorkoutValidationError(
    val code: ErgoWorkoutValidationErrorCode,
    val message: String,
    val fieldPath: String,
)

/**
 * Parser output keeps each incremental representation available for future integration slices.
 */
internal sealed class ErgoWorkoutParseResult {
    data class Success(
        val parsed: ParsedErgoWorkoutFile,
        val normalized: NormalizedErgoWorkout,
        val compiled: CompiledErgoWorkout,
    ) : ErgoWorkoutParseResult()

    data class Failure(
        val error: ErgoWorkoutValidationError,
    ) : ErgoWorkoutParseResult()
}

internal data class ParsedErgoWorkoutFile(
    val format: String,
    val version: String,
    val title: String,
    val description: String?,
    val control: ParsedErgoWorkoutControl?,
    val segments: List<ParsedErgoWorkoutSegment>,
)

internal data class ParsedErgoWorkoutControl(
    val initialPowerWatts: Int,
    val minPowerWatts: Int,
    val maxPowerWatts: Int,
    val signalLossPowerWatts: Int,
)

internal sealed class ParsedErgoWorkoutSegment {
    data class Steady(
        val durationSec: Int,
        val target: ParsedErgoWorkoutTarget,
    ) : ParsedErgoWorkoutSegment()

    data class Ramp(
        val durationSec: Int,
        val fromTarget: ParsedErgoWorkoutTarget,
        val toTarget: ParsedErgoWorkoutTarget,
    ) : ParsedErgoWorkoutSegment()

    data class Repeat(
        val count: Int,
        val steps: List<ParsedErgoWorkoutSegment>,
    ) : ParsedErgoWorkoutSegment()
}

internal sealed class ParsedErgoWorkoutTarget {
    data class Power(
        val value: Int,
    ) : ParsedErgoWorkoutTarget()

    data class HeartRateRange(
        val low: Int,
        val high: Int,
    ) : ParsedErgoWorkoutTarget()
}

internal data class ErgoWorkoutControl(
    val initialPowerWatts: Int,
    val minPowerWatts: Int,
    val maxPowerWatts: Int,
    val signalLossPowerWatts: Int,
)

internal data class NormalizedErgoWorkout(
    val title: String,
    val description: String?,
    val control: ErgoWorkoutControl?,
    val segments: List<NormalizedErgoWorkoutSegment>,
)

internal sealed class NormalizedErgoWorkoutSegment {
    data class PowerSteady(
        val durationSec: Int,
        val watts: Int,
    ) : NormalizedErgoWorkoutSegment()

    data class HeartRateSteady(
        val durationSec: Int,
        val lowBpm: Int,
        val highBpm: Int,
    ) : NormalizedErgoWorkoutSegment()

    data class PowerRamp(
        val durationSec: Int,
        val fromWatts: Int,
        val toWatts: Int,
    ) : NormalizedErgoWorkoutSegment()

    data class Repeat(
        val count: Int,
        val steps: List<NormalizedErgoWorkoutRepeatStep>,
    ) : NormalizedErgoWorkoutSegment()
}

internal sealed class NormalizedErgoWorkoutRepeatStep {
    data class PowerSteady(
        val durationSec: Int,
        val watts: Int,
    ) : NormalizedErgoWorkoutRepeatStep()

    data class HeartRateSteady(
        val durationSec: Int,
        val lowBpm: Int,
        val highBpm: Int,
    ) : NormalizedErgoWorkoutRepeatStep()
}

internal data class CompiledErgoWorkout(
    val title: String,
    val description: String?,
    val steps: List<CompiledErgoWorkoutStep>,
    val totalDurationSec: Int,
)

internal sealed class CompiledErgoWorkoutStep {
    abstract val stepIndex: Int
    abstract val startOffsetSec: Int
    abstract val durationSec: Int

    data class PowerSteady(
        override val stepIndex: Int,
        override val startOffsetSec: Int,
        override val durationSec: Int,
        val watts: Int,
    ) : CompiledErgoWorkoutStep()

    data class PowerRamp(
        override val stepIndex: Int,
        override val startOffsetSec: Int,
        override val durationSec: Int,
        val fromWatts: Int,
        val toWatts: Int,
    ) : CompiledErgoWorkoutStep()

    data class HeartRateSteady(
        override val stepIndex: Int,
        override val startOffsetSec: Int,
        override val durationSec: Int,
        val lowBpm: Int,
        val highBpm: Int,
        val initialPowerWatts: Int,
        val minPowerWatts: Int,
        val maxPowerWatts: Int,
        val signalLossPowerWatts: Int,
    ) : CompiledErgoWorkoutStep()
}

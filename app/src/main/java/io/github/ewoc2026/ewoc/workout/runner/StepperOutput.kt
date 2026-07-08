package io.github.ewoc2026.ewoc.workout.runner

/**
 * Interval-part progression details when the active step is an interval.
 */
data class IntervalPartProgress(
    val phase: IntervalPartPhase,
    val repIndex: Int,
    val repTotal: Int,
    val remainingSec: Int,
)

/**
 * Current phase of the interval part.
 */
enum class IntervalPartPhase {
    ON,
    OFF,
}

/**
 * Output of a tick: targets and completion status.
 *
 * Edge cases:
 * - `targetPowerWatts` can be null for free ride or when power attributes are missing.
 * - `done` is true once all steps are completed; targets may be null in that state.
 * - `elapsedSec` tracks progressed workout time and is monotonic while running.
 * - `stepRemainingSec` is the remaining time in the active source step.
 * - `intervalPart` is populated only when the active source step is interval-based.
 */
data class StepperOutput(
    val targetPowerWatts: Int?,
    val targetCadence: Int?,
    val done: Boolean,
    val label: String,
    val elapsedSec: Int,
    val stepRemainingSec: Int?,
    val intervalPart: IntervalPartProgress?,
    val segmentType: RunnerSegmentType? = null,
    val sourceStepIndex: Int? = null,
)

/**
 * Stable runner-owned segment vocabulary for session-side control ownership.
 */
enum class RunnerSegmentType {
    STEADY,
    RAMP,
    HEART_RATE_STEADY,
    FREE_RIDE,
    INTERVALS,
    UNKNOWN,
}

package com.example.ergometerapp.workout.runner

/**
 * UI-facing snapshot of workout runner state.
 *
 * Invariants:
 * - `running=true` means the workout has started and not reached a terminal state.
 * - `paused=true` while `running=true` indicates the workout is paused in place.
 * - `done=true` is terminal and must pair with `running=false`, `paused=true`, and cleared targets.
 * - `workoutElapsedSec` is null outside an active workout lifecycle.
 * - `stepRemainingSec` is null when no active step is available.
 * - `intervalPart` is populated only during interval-origin step parts.
 */
data class RunnerState(
    val running: Boolean,
    val paused: Boolean,
    val done: Boolean,
    val label: String?,
    val targetPowerWatts: Int?,
    val targetCadence: Int?,
    val workoutElapsedSec: Int?,
    val stepRemainingSec: Int?,
    val intervalPart: IntervalPartProgress?,
    val segmentType: RunnerSegmentType? = null,
    val sourceStepIndex: Int? = null,
) {
    companion object {
        fun stopped(workoutElapsedSec: Int? = null): RunnerState = RunnerState(
            running = false,
            paused = true,
            done = true,
            label = "Done",
            targetPowerWatts = null,
            targetCadence = null,
            workoutElapsedSec = workoutElapsedSec,
            stepRemainingSec = null,
            intervalPart = null,
            segmentType = null,
            sourceStepIndex = null,
        )
    }
}

package com.example.ergometerapp.workout.runner

/**
 * Snapshot of step progression.
 *
 * Invariants:
 * - `stepIndex` is zero-based and may equal the number of steps when finished.
 * - `stepElapsedMs` is time elapsed within the current step or interval segment.
 * - `intervalRep` and `inOn` are only meaningful for interval steps and ignored otherwise.
 */
data class StepperState(
    val stepIndex: Int,
    val stepElapsedMs: Long,
    val intervalRep: Int,
    val inOn: Boolean,
    val paused: Boolean,
)

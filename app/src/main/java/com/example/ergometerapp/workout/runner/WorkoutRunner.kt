package com.example.ergometerapp.workout.runner

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.example.ergometerapp.ftms.FtmsTargetWriter

/**
 * Drives a [WorkoutStepper] on a fixed tick and forwards target power updates.
 *
 * Invariants and edge cases:
 * - Uses [SystemClock.uptimeMillis] deltas for deterministic progression across pauses.
 * - Applies target power only when non-null to avoid overriding free-ride steps.
 * - Stops ticking once the workout is done and clears targets via [targetWriter].
 */
class WorkoutRunner(
    private val stepper: WorkoutStepper,
    private val targetWriter: FtmsTargetWriter,
    private val onStateChanged: (RunnerState) -> Unit = {},
    private val tickIntervalMs: Long = 250L,
    private val nowUptimeMs: () -> Long = { SystemClock.uptimeMillis() },
    private val handler: Handler = Handler(Looper.getMainLooper()),
) {
    private var state = RunnerState.stopped()

    private val tickRunnable = object : Runnable {
        override fun run() {
            if (!state.running || state.paused || state.done) return
            val output = stepper.tick(nowUptimeMs())
            if (output.done) {
                stopInternal(
                    clearTarget = true,
                    stopStepper = false,
                    finalElapsedSec = output.elapsedSec,
                )
                return
            }
            val previousTarget = state.targetPowerWatts
            updateStateFromTick(output)
            if (!state.running || state.done) return
            output.targetPowerWatts?.let { targetWriter.setTargetWatts(it) }
                ?: run {
                    if (previousTarget != null) {
                        targetWriter.setTargetWatts(null)
                    }
                }
            handler.postDelayed(this, tickIntervalMs)
        }
    }

    fun start() {
        handler.removeCallbacks(tickRunnable)
        stepper.start()
        emitState(
            RunnerState(
                running = true,
                paused = false,
                done = false,
                label = null,
                targetPowerWatts = null,
                targetCadence = null,
                workoutElapsedSec = 0,
                stepRemainingSec = null,
                intervalPart = null,
                segmentType = null,
                sourceStepIndex = null,
            )
        )
        handler.post(tickRunnable)
    }

    fun pause() {
        if (!state.running || state.paused || state.done) return
        stepper.pause()
        handler.removeCallbacks(tickRunnable)
        emitState(state.copy(paused = true))
    }

    fun resume() {
        if (!state.running || !state.paused || state.done) return
        stepper.resume()
        emitState(state.copy(paused = false))
        handler.post(tickRunnable)
    }

    fun stop() {
        stopInternal(
            clearTarget = true,
            stopStepper = true,
            finalElapsedSec = null,
        )
    }

    fun restore(state: StepperState) {
        stepper.restore(state)
        handler.removeCallbacks(tickRunnable)
        emitState(
            this.state.copy(
                running = false,
                paused = state.paused,
                done = false,
                workoutElapsedSec = stepper.currentElapsedSec(),
                stepRemainingSec = null,
                intervalPart = null,
            )
        )
    }

    fun getState(): StepperState = stepper.getState()

    private fun stopInternal(
        clearTarget: Boolean,
        stopStepper: Boolean,
        finalElapsedSec: Int?,
    ) {
        handler.removeCallbacks(tickRunnable)
        if (stopStepper) {
            stepper.stop()
        }
        // Publish the terminal runner state before the target clear so downstream session
        // orchestration can distinguish "workout ended" from an in-workout FreeRide null target.
        emitState(RunnerState.stopped(workoutElapsedSec = finalElapsedSec))
        if (clearTarget) {
            targetWriter.setTargetWatts(null)
        }
    }

    private fun updateStateFromTick(output: StepperOutput) {
        val nextState = state.copy(
            running = true,
            paused = false,
            done = false,
            label = output.label,
            targetPowerWatts = output.targetPowerWatts,
            targetCadence = output.targetCadence,
            workoutElapsedSec = output.elapsedSec,
            stepRemainingSec = output.stepRemainingSec,
            intervalPart = output.intervalPart,
            segmentType = output.segmentType,
            sourceStepIndex = output.sourceStepIndex,
        )
        val labelTargetOrProgressChanged =
            nextState.label != state.label ||
                nextState.targetPowerWatts != state.targetPowerWatts ||
                nextState.targetCadence != state.targetCadence ||
                nextState.workoutElapsedSec != state.workoutElapsedSec ||
                nextState.stepRemainingSec != state.stepRemainingSec ||
                nextState.intervalPart != state.intervalPart
        state = nextState
        if (labelTargetOrProgressChanged) {
            onStateChanged(nextState)
        }
    }

    private fun emitState(nextState: RunnerState) {
        state = nextState
        onStateChanged(nextState)
    }
}

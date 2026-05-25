package com.example.ergometerapp.workout.runner

import com.example.ergometerapp.workout.ExecutionWorkout
import com.example.ergometerapp.workout.ExecutionSegment
import com.example.ergometerapp.workout.CadenceTarget
import com.example.ergometerapp.workout.IntervalPhase
import com.example.ergometerapp.workout.Step
import com.example.ergometerapp.workout.WorkoutFile
import kotlin.math.roundToInt

/**
 * Minimal, deterministic stepper for ZWO workouts.
 *
 * Invariants and edge cases:
 * - Advancement only uses deltas from successive `tick()` calls and stops while paused.
 * - Steps with missing required durations are skipped immediately for this minimal implementation.
 * - Unknown steps are treated as zero-duration to avoid blocking on unsupported formats.
 */
class WorkoutStepper private constructor(
    private val workout: WorkoutFile?,
    private val ftpWatts: Int?,
    private val executionWorkout: ExecutionWorkout?,
) {
    init {
        require((workout != null && ftpWatts != null) xor (executionWorkout != null))
    }

    constructor(
        workout: WorkoutFile,
        ftpWatts: Int,
    ) : this(
        workout = workout,
        ftpWatts = ftpWatts,
        executionWorkout = null,
    )

    constructor(executionWorkout: ExecutionWorkout) : this(
        workout = null,
        ftpWatts = null,
        executionWorkout = executionWorkout,
    )

    companion object {
        /**
         * Explicit entry point for the native execution model path.
         */
        fun fromExecutionWorkout(workout: ExecutionWorkout): WorkoutStepper {
            return WorkoutStepper(executionWorkout = workout)
        }
    }

    private var state = StepperState(
        stepIndex = 0,
        stepElapsedMs = 0L,
        intervalRep = 0,
        inOn = true,
        paused = true,
    )
    private var lastUptimeMs: Long? = null
    private var stopped = true
    private var executionCurrentSegmentIndex = 0
    private var executionSecondsIntoSegment = 0
    private var executionCurrentTargetWatts: Int? = null
    private var executionDone = true
    private var executionRemainderMs = 0L

    fun start() {
        state = StepperState(
            stepIndex = 0,
            stepElapsedMs = 0L,
            intervalRep = 0,
            inOn = true,
            paused = false,
        )
        lastUptimeMs = null
        stopped = false
        if (executionWorkout != null) {
            executionCurrentSegmentIndex = 0
            executionSecondsIntoSegment = 0
            executionRemainderMs = 0L
            executionDone = executionWorkout.segments.isEmpty()
            normalizeExecutionPosition()
            updateExecutionTargetWatts()
            syncStateFromExecution()
        }
    }

    fun pause() {
        if (!stopped) {
            state = state.copy(paused = true)
        }
    }

    fun resume() {
        if (!stopped) {
            state = state.copy(paused = false)
            lastUptimeMs = null
        }
    }

    fun stop() {
        stopped = true
        if (executionWorkout != null) {
            executionCurrentSegmentIndex = executionWorkout.segments.size
            executionSecondsIntoSegment = 0
            executionCurrentTargetWatts = null
            executionDone = true
            executionRemainderMs = 0L
        }
        state = state.copy(
            stepIndex = totalStepCount(),
            stepElapsedMs = 0L,
            intervalRep = 0,
            inOn = true,
            paused = true,
        )
        lastUptimeMs = null
    }

    fun getState(): StepperState = state

    /**
     * Returns progressed workout time in whole seconds.
     *
     * This reflects runner progression rather than wall-clock session duration,
     * so pauses and reconnect stalls do not advance the value.
     */
    fun currentElapsedSec(): Int {
        return (currentElapsedMs() / 1000L).toInt()
    }

    fun restore(state: StepperState) {
        this.state = state
        stopped = state.stepIndex >= totalStepCount()
        lastUptimeMs = null
        if (executionWorkout != null) {
            executionCurrentSegmentIndex = state.stepIndex.coerceAtLeast(0)
            executionSecondsIntoSegment = (state.stepElapsedMs / 1000L).toInt().coerceAtLeast(0)
            executionRemainderMs = state.stepElapsedMs.rem(1000L).coerceAtLeast(0L)
            executionDone = executionCurrentSegmentIndex >= executionWorkout.segments.size
            normalizeExecutionPosition()
            updateExecutionTargetWatts()
            syncStateFromExecution()
        }
    }

    private fun currentElapsedMs(): Long {
        val executionWorkout = executionWorkout
        if (executionWorkout != null) {
            return executionElapsedMs(executionWorkout)
        }
        val workout = requireLegacyWorkout()
        return legacyElapsedMs(workout)
    }

    private fun executionElapsedMs(workout: ExecutionWorkout): Long {
        val clampedIndex = state.stepIndex.coerceIn(0, workout.segments.size)
        var elapsedMs = 0L
        for (index in 0 until clampedIndex) {
            elapsedMs += workout.segments[index].durationSec.coerceAtLeast(0).toLong() * 1000L
        }
        if (clampedIndex < workout.segments.size) {
            val segmentDurationMs = workout.segments[clampedIndex].durationSec.coerceAtLeast(0).toLong() * 1000L
            elapsedMs += state.stepElapsedMs.coerceIn(0L, segmentDurationMs)
        }
        return elapsedMs
    }

    private fun legacyElapsedMs(workout: WorkoutFile): Long {
        val clampedStepIndex = state.stepIndex.coerceIn(0, workout.steps.size)
        var elapsedMs = 0L
        for (index in 0 until clampedStepIndex) {
            elapsedMs += stepTotalDurationMs(workout.steps[index])
        }
        if (clampedStepIndex >= workout.steps.size) {
            return elapsedMs
        }

        val currentStep = workout.steps[clampedStepIndex]
        return elapsedMs + currentStepElapsedMs(currentStep)
    }

    private fun currentStepElapsedMs(step: Step): Long {
        return when (step) {
            is Step.IntervalsT -> intervalsElapsedMs(step)
            else -> {
                val durationMs = stepDurationMs(step, state)?.coerceAtLeast(0L) ?: return 0L
                state.stepElapsedMs.coerceIn(0L, durationMs)
            }
        }
    }

    private fun intervalsElapsedMs(step: Step.IntervalsT): Long {
        val onMs = step.onDurationSec?.toLong()?.times(1000L)?.coerceAtLeast(0L) ?: return 0L
        val offMs = step.offDurationSec?.toLong()?.times(1000L)?.coerceAtLeast(0L) ?: return 0L
        val repeat = step.repeat?.coerceAtLeast(0) ?: return 0L
        if (repeat == 0) return 0L

        val completedCycles = state.intervalRep.coerceIn(0, repeat).toLong()
        val completedCycleMs = completedCycles * (onMs + offMs)

        val phaseElapsedMs = if (state.inOn) {
            state.stepElapsedMs.coerceIn(0L, onMs)
        } else {
            onMs + state.stepElapsedMs.coerceIn(0L, offMs)
        }

        val maxStepMs = repeat.toLong() * (onMs + offMs)
        return (completedCycleMs + phaseElapsedMs).coerceIn(0L, maxStepMs)
    }

    private fun stepTotalDurationMs(step: Step): Long {
        return when (step) {
            is Step.Warmup -> step.durationSec?.toLong()?.times(1000L)?.coerceAtLeast(0L) ?: 0L
            is Step.Cooldown -> step.durationSec?.toLong()?.times(1000L)?.coerceAtLeast(0L) ?: 0L
            is Step.SteadyState -> step.durationSec?.toLong()?.times(1000L)?.coerceAtLeast(0L) ?: 0L
            is Step.Ramp -> step.durationSec?.toLong()?.times(1000L)?.coerceAtLeast(0L) ?: 0L
            is Step.FreeRide -> step.durationSec?.toLong()?.times(1000L)?.coerceAtLeast(0L) ?: 0L
            is Step.IntervalsT -> {
                val onMs = step.onDurationSec?.toLong()?.times(1000L)?.coerceAtLeast(0L) ?: return 0L
                val offMs = step.offDurationSec?.toLong()?.times(1000L)?.coerceAtLeast(0L) ?: return 0L
                val repeat = step.repeat?.coerceAtLeast(0) ?: return 0L
                repeat.toLong() * (onMs + offMs)
            }
            is Step.Unknown -> 0L
        }
    }

    fun tick(nowUptimeMs: Long): StepperOutput {
        if (executionWorkout != null) {
            return tickExecution(nowUptimeMs)
        }
        val workout = requireLegacyWorkout()

        if (stopped) {
            return StepperOutput(
                targetPowerWatts = null,
                targetCadence = null,
                done = true,
                label = "Done",
                elapsedSec = currentElapsedSec(),
                stepRemainingSec = null,
                intervalPart = null,
                segmentType = null,
                sourceStepIndex = null,
            )
        }

        if (state.paused) {
            return currentOutput(workout = workout, done = false)
        }

        val last = lastUptimeMs
        lastUptimeMs = nowUptimeMs
        if (last == null) {
            return currentOutput(workout = workout, done = false)
        }

        var remainingDeltaMs = (nowUptimeMs - last).coerceAtLeast(0L)
        while (remainingDeltaMs > 0L) {
            if (state.stepIndex >= workout.steps.size) {
                stopped = true
                return StepperOutput(
                    targetPowerWatts = null,
                    targetCadence = null,
                    done = true,
                    label = "Done",
                    elapsedSec = currentElapsedSec(),
                    stepRemainingSec = null,
                    intervalPart = null,
                    segmentType = null,
                    sourceStepIndex = null,
                )
            }

            val step = workout.steps[state.stepIndex]
            val durationMs = stepDurationMs(step, state)
            if (durationMs == null || durationMs == 0L) {
                advanceStepOrInterval(workout = workout)
                continue
            }

            val stepRemaining = durationMs - state.stepElapsedMs
            if (remainingDeltaMs < stepRemaining) {
                state = state.copy(stepElapsedMs = state.stepElapsedMs + remainingDeltaMs)
                remainingDeltaMs = 0L
            } else {
                state = state.copy(stepElapsedMs = durationMs)
                remainingDeltaMs -= stepRemaining
                advanceStepOrInterval(workout = workout)
            }
        }

        return currentOutput(workout = workout, done = state.stepIndex >= workout.steps.size)
    }

    private fun currentOutput(workout: WorkoutFile, done: Boolean): StepperOutput {
        if (done || state.stepIndex >= workout.steps.size) {
            return StepperOutput(
                targetPowerWatts = null,
                targetCadence = null,
                done = true,
                label = "Done",
                elapsedSec = currentElapsedSec(),
                stepRemainingSec = null,
                intervalPart = null,
                segmentType = null,
                sourceStepIndex = null,
            )
        }
        val step = workout.steps[state.stepIndex]
        val label = when (step) {
            is Step.Warmup -> "Warmup"
            is Step.Cooldown -> "Cooldown"
            is Step.SteadyState -> "Steady"
            is Step.Ramp -> "Ramp"
            is Step.IntervalsT -> "Intervals"
            is Step.FreeRide -> "Free Ride"
            is Step.Unknown -> step.tagName
        }
        return StepperOutput(
            targetPowerWatts = targetPowerWatts(step, state),
            targetCadence = targetCadence(step),
            done = false,
            label = label,
            elapsedSec = currentElapsedSec(),
            stepRemainingSec = legacyStepRemainingSec(step),
            intervalPart = legacyIntervalPartProgress(step),
            segmentType = legacySegmentType(step),
            sourceStepIndex = state.stepIndex,
        )
    }

    private fun legacyStepRemainingSec(step: Step): Int? {
        val totalDurationMs = stepTotalDurationMs(step)
        if (totalDurationMs <= 0L) return null
        val elapsedMs = currentStepElapsedMs(step).coerceIn(0L, totalDurationMs)
        return remainingSec(
            durationMs = totalDurationMs,
            elapsedMs = elapsedMs,
        )
    }

    private fun legacyIntervalPartProgress(step: Step): IntervalPartProgress? {
        if (step !is Step.IntervalsT) return null
        val onDurationMs = step.onDurationSec?.toLong()?.times(1000L)?.takeIf { it > 0L } ?: return null
        val offDurationMs = step.offDurationSec?.toLong()?.times(1000L)?.takeIf { it > 0L } ?: return null
        val repTotal = step.repeat?.takeIf { it > 0 } ?: return null

        val phaseDurationMs = if (state.inOn) onDurationMs else offDurationMs
        val phaseElapsedMs = state.stepElapsedMs.coerceIn(0L, phaseDurationMs)
        val repIndex = (state.intervalRep + 1).coerceIn(1, repTotal)
        val phase = if (state.inOn) IntervalPartPhase.ON else IntervalPartPhase.OFF

        return IntervalPartProgress(
            phase = phase,
            repIndex = repIndex,
            repTotal = repTotal,
            remainingSec = remainingSec(
                durationMs = phaseDurationMs,
                elapsedMs = phaseElapsedMs,
            ),
        )
    }

    private fun targetCadence(step: Step): Int? = when (step) {
        is Step.Warmup -> step.cadence
        is Step.Cooldown -> step.cadence
        is Step.SteadyState -> step.cadence
        is Step.Ramp -> step.cadence
        is Step.IntervalsT -> step.cadence
        is Step.FreeRide -> step.cadence
        is Step.Unknown -> null
    }

    private fun targetPowerWatts(step: Step, state: StepperState): Int? {
        return when (step) {
            is Step.Warmup -> interpolatedWatts(
                step.powerLow,
                step.powerHigh,
                step.durationSec,
                state.stepElapsedMs
            )

            is Step.Cooldown -> {
                val descendingRange = cooldownDescendingRange(step.powerLow, step.powerHigh)
                if (descendingRange == null) {
                    null
                } else {
                    interpolatedWatts(
                        descendingRange.first,
                        descendingRange.second,
                        step.durationSec,
                        state.stepElapsedMs
                    )
                }
            }

            is Step.Ramp -> interpolatedWatts(
                step.powerLow,
                step.powerHigh,
                step.durationSec,
                state.stepElapsedMs
            )

            is Step.SteadyState -> ratioToWatts(step.power)
            is Step.IntervalsT -> {
                if (state.inOn) ratioToWatts(step.onPower) else ratioToWatts(step.offPower)
            }

            is Step.FreeRide -> null
            is Step.Unknown -> null
        }
    }

    private fun interpolatedWatts(
        low: Double?,
        high: Double?,
        durationSec: Int?,
        elapsedMs: Long,
    ): Int? {
        if (low == null || high == null || durationSec == null) return null
        val durationMs = durationSec.toLong() * 1000L
        if (durationMs <= 0L) return null
        val t = (elapsedMs.toDouble() / durationMs.toDouble()).coerceIn(0.0, 1.0)
        val ratio = low + (high - low) * t
        return ratioToWatts(ratio)
    }

    /**
     * Cooldown is always descending, regardless of source field ordering.
     */
    private fun cooldownDescendingRange(
        powerLow: Double?,
        powerHigh: Double?,
    ): Pair<Double, Double>? {
        if (powerLow == null || powerHigh == null) return null
        return maxOf(powerLow, powerHigh) to minOf(powerLow, powerHigh)
    }

    private fun ratioToWatts(ratio: Double?): Int? {
        if (ratio == null) return null
        val ftpWatts = requireLegacyFtpWatts()
        return (ratio * ftpWatts).roundToInt()
    }

    private fun stepDurationMs(step: Step, state: StepperState): Long? {
        return when (step) {
            is Step.Warmup -> step.durationSec?.toLong()?.times(1000L)
            is Step.Cooldown -> step.durationSec?.toLong()?.times(1000L)
            is Step.SteadyState -> step.durationSec?.toLong()?.times(1000L)
            is Step.Ramp -> step.durationSec?.toLong()?.times(1000L)
            is Step.FreeRide -> step.durationSec?.toLong()?.times(1000L)
            is Step.IntervalsT -> {
                val onMs = step.onDurationSec?.toLong()?.times(1000L)
                val offMs = step.offDurationSec?.toLong()?.times(1000L)
                if (onMs == null || offMs == null) return null
                if (state.inOn) onMs else offMs
            }

            is Step.Unknown -> null
        }
    }

    private fun advanceStepOrInterval(workout: WorkoutFile) {
        val step = workout.steps.getOrNull(state.stepIndex)
        if (step is Step.IntervalsT) {
            val repeat = step.repeat ?: 0
            if (repeat <= 0) {
                advanceStep()
                return
            }
            if (state.inOn) {
                state = state.copy(stepElapsedMs = 0L, inOn = false)
                return
            }
            val nextRep = state.intervalRep + 1
            if (nextRep >= repeat) {
                advanceStep()
            } else {
                state = state.copy(stepElapsedMs = 0L, inOn = true, intervalRep = nextRep)
            }
        } else {
            advanceStep()
        }
    }

    private fun advanceStep() {
        state = state.copy(
            stepIndex = state.stepIndex + 1,
            stepElapsedMs = 0L,
            intervalRep = 0,
            inOn = true,
        )
    }

    private fun totalStepCount(): Int {
        val workout = workout
        if (workout != null) {
            return workout.steps.size
        }
        val executionWorkout = executionWorkout
        if (executionWorkout != null) {
            return executionWorkout.segments.size
        }
        return 0
    }

    private fun requireLegacyWorkout(): WorkoutFile {
        return workout
            ?: throw UnsupportedOperationException(
                "Legacy WorkoutFile path is not available in execution mode.",
            )
    }

    private fun requireLegacyFtpWatts(): Int {
        return ftpWatts
            ?: throw UnsupportedOperationException(
                "Legacy FTP path is not available in execution mode.",
            )
    }

    private fun tickExecution(nowUptimeMs: Long): StepperOutput {
        if (stopped) {
            return StepperOutput(
                targetPowerWatts = null,
                targetCadence = null,
                done = true,
                label = "Done",
                elapsedSec = currentElapsedSec(),
                stepRemainingSec = null,
                intervalPart = null,
                segmentType = null,
                sourceStepIndex = null,
            )
        }

        if (state.paused) {
            return currentExecutionOutput()
        }

        val last = lastUptimeMs
        lastUptimeMs = nowUptimeMs
        if (last == null) {
            return currentExecutionOutput()
        }

        executionRemainderMs += (nowUptimeMs - last).coerceAtLeast(0L)
        while (executionRemainderMs >= 1000L && !executionDone) {
            executionRemainderMs -= 1000L
            executionSecondsIntoSegment += 1
            normalizeExecutionPosition()
            updateExecutionTargetWatts()
        }

        syncStateFromExecution()
        if (executionDone) {
            stopped = true
            return StepperOutput(
                targetPowerWatts = null,
                targetCadence = null,
                done = true,
                label = "Done",
                elapsedSec = currentElapsedSec(),
                stepRemainingSec = null,
                intervalPart = null,
                segmentType = null,
                sourceStepIndex = null,
            )
        }

        return currentExecutionOutput()
    }

    private fun currentExecutionOutput(): StepperOutput {
        if (executionDone) {
            return StepperOutput(
                targetPowerWatts = null,
                targetCadence = null,
                done = true,
                label = "Done",
                elapsedSec = currentElapsedSec(),
                stepRemainingSec = null,
                intervalPart = null,
                segmentType = null,
                sourceStepIndex = null,
            )
        }

        val segment = requireExecutionWorkout().segments[executionCurrentSegmentIndex]
        return StepperOutput(
            targetPowerWatts = executionCurrentTargetWatts,
            targetCadence = executionTargetCadence(segment),
            done = false,
            label = executionLabel(segment),
            elapsedSec = currentElapsedSec(),
            stepRemainingSec = executionStepRemainingSec(),
            intervalPart = executionIntervalPartProgress(segment),
            segmentType = executionSegmentType(segment),
            sourceStepIndex = segment.sourceStepIndex,
        )
    }

    private fun executionStepRemainingSec(): Int? {
        val segments = requireExecutionWorkout().segments
        if (executionCurrentSegmentIndex !in segments.indices) return null

        val currentSegment = segments[executionCurrentSegmentIndex]
        val currentDurationMs = currentSegment.durationSec.coerceAtLeast(0).toLong() * 1000L
        var totalRemainingSec = remainingSec(
            durationMs = currentDurationMs,
            elapsedMs = state.stepElapsedMs.coerceAtLeast(0L),
        )

        var nextIndex = executionCurrentSegmentIndex + 1
        while (nextIndex < segments.size &&
            segments[nextIndex].sourceStepIndex == currentSegment.sourceStepIndex
        ) {
            totalRemainingSec += segments[nextIndex].durationSec.coerceAtLeast(0)
            nextIndex += 1
        }
        return totalRemainingSec
    }

    private fun executionIntervalPartProgress(segment: ExecutionSegment): IntervalPartProgress? {
        val metadata = segment.intervalMetadata ?: return null
        val repTotal = metadata.repTotal.coerceAtLeast(1)
        val repIndex = metadata.repIndex.coerceIn(1, repTotal)
        val phase = when (metadata.phase) {
            IntervalPhase.ON -> IntervalPartPhase.ON
            IntervalPhase.OFF -> IntervalPartPhase.OFF
        }
        return IntervalPartProgress(
            phase = phase,
            repIndex = repIndex,
            repTotal = repTotal,
            remainingSec = remainingSec(
                durationMs = segment.durationSec.coerceAtLeast(0).toLong() * 1000L,
                elapsedMs = state.stepElapsedMs.coerceAtLeast(0L),
            ),
        )
    }

    private fun executionLabel(segment: ExecutionSegment): String {
        return when (segment) {
            is ExecutionSegment.Steady -> "Steady"
            is ExecutionSegment.Ramp -> "Ramp"
            is ExecutionSegment.HeartRateSteady -> "HR Steady"
            is ExecutionSegment.FreeRide -> "Free Ride"
        }
    }

    private fun executionSegmentType(segment: ExecutionSegment): RunnerSegmentType {
        return when (segment) {
            is ExecutionSegment.Steady -> RunnerSegmentType.STEADY
            is ExecutionSegment.Ramp -> RunnerSegmentType.RAMP
            is ExecutionSegment.HeartRateSteady -> RunnerSegmentType.HEART_RATE_STEADY
            is ExecutionSegment.FreeRide -> RunnerSegmentType.FREE_RIDE
        }
    }

    private fun legacySegmentType(step: Step): RunnerSegmentType {
        return when (step) {
            is Step.Warmup -> RunnerSegmentType.RAMP
            is Step.Cooldown -> RunnerSegmentType.RAMP
            is Step.SteadyState -> RunnerSegmentType.STEADY
            is Step.Ramp -> RunnerSegmentType.RAMP
            is Step.IntervalsT -> RunnerSegmentType.INTERVALS
            is Step.FreeRide -> RunnerSegmentType.FREE_RIDE
            is Step.Unknown -> RunnerSegmentType.UNKNOWN
        }
    }

    private fun executionTargetCadence(segment: ExecutionSegment): Int? {
        return when (val cadence = segment.cadence) {
            is CadenceTarget.FixedCadence -> cadence.rpm
            CadenceTarget.AnyCadence -> null
        }
    }

    private fun syncStateFromExecution() {
        state = state.copy(
            stepIndex = executionCurrentSegmentIndex,
            stepElapsedMs = executionSecondsIntoSegment.toLong() * 1000L + executionRemainderMs,
            intervalRep = 0,
            inOn = true,
        )
    }

    private fun normalizeExecutionPosition() {
        val segments = requireExecutionWorkout().segments
        while (executionCurrentSegmentIndex < segments.size) {
            val segment = segments[executionCurrentSegmentIndex]
            if (segment.durationSec <= 0 || executionSecondsIntoSegment >= segment.durationSec) {
                executionCurrentSegmentIndex += 1
                executionSecondsIntoSegment = 0
                continue
            }
            break
        }
        executionDone = executionCurrentSegmentIndex >= segments.size
        if (executionDone) {
            executionCurrentTargetWatts = null
        }
    }

    private fun updateExecutionTargetWatts() {
        if (executionDone) {
            executionCurrentTargetWatts = null
            return
        }

        val segment = requireExecutionWorkout().segments[executionCurrentSegmentIndex]
        executionCurrentTargetWatts = when (segment) {
            is ExecutionSegment.Steady -> segment.targetWatts
            is ExecutionSegment.Ramp -> {
                val t = executionSecondsIntoSegment.toDouble() / segment.durationSec.toDouble()
                (segment.startWatts + (segment.endWatts - segment.startWatts) * t).roundToInt()
            }
            is ExecutionSegment.HeartRateSteady -> segment.initialPowerWatts
            is ExecutionSegment.FreeRide -> null
        }
    }

    private fun requireExecutionWorkout(): ExecutionWorkout {
        return executionWorkout
            ?: throw UnsupportedOperationException(
                "ExecutionWorkout path is not available in legacy mode.",
            )
    }

    private fun remainingSec(durationMs: Long, elapsedMs: Long): Int {
        if (durationMs <= 0L) return 0
        val remainingMs = (durationMs - elapsedMs).coerceAtLeast(0L)
        return ((remainingMs + 999L) / 1000L).toInt()
    }
}

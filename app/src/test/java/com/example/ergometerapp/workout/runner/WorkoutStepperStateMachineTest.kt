package com.example.ergometerapp.workout.runner

import com.example.ergometerapp.workout.CadenceTarget
import com.example.ergometerapp.workout.ExecutionSegment
import com.example.ergometerapp.workout.ExecutionWorkout
import com.example.ergometerapp.workout.Step
import com.example.ergometerapp.workout.WorkoutFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutStepperStateMachineTest {

    @Test
    fun tickAdvancesThroughMultipleSegments() {
        val stepper = WorkoutStepper.fromExecutionWorkout(twoSteadySegments())

        stepper.start()
        val at0 = stepper.tick(0L)
        assertFalse(at0.done)
        assertEquals(200, at0.targetPowerWatts)

        val at2 = stepper.tick(2000L)
        assertFalse(at2.done)
        assertEquals(150, at2.targetPowerWatts)

        val at4 = stepper.tick(4000L)
        assertTrue(at4.done)
    }

    @Test
    fun tickAdvancesThroughMultipleLegacySteps() {
        val workout = WorkoutFile(
            name = "Two Steps",
            description = null,
            author = null,
            tags = emptyList(),
            steps = listOf(
                Step.SteadyState(durationSec = 2, power = 0.8, cadence = null),
                Step.SteadyState(durationSec = 2, power = 0.5, cadence = null),
            ),
        )
        val stepper = WorkoutStepper(workout = workout, ftpWatts = 200)

        stepper.start()
        val at0 = stepper.tick(0L)
        assertFalse(at0.done)
        assertEquals(160, at0.targetPowerWatts)

        val at2 = stepper.tick(2000L)
        assertFalse(at2.done)
        assertEquals(100, at2.targetPowerWatts)

        val at4 = stepper.tick(4000L)
        assertTrue(at4.done)
    }

    @Test
    fun intervalTransitionsOnToOffAndBack() {
        val workout = WorkoutFile(
            name = "Intervals",
            description = null,
            author = null,
            tags = emptyList(),
            steps = listOf(
                Step.IntervalsT(
                    onDurationSec = 2,
                    offDurationSec = 2,
                    onPower = 1.0,
                    offPower = 0.5,
                    repeat = 2,
                    cadence = null,
                ),
            ),
        )
        val stepper = WorkoutStepper(workout = workout, ftpWatts = 200)

        stepper.start()
        val at0 = stepper.tick(0L)
        assertEquals(200, at0.targetPowerWatts)
        assertEquals(IntervalPartPhase.ON, at0.intervalPart?.phase)

        val at2 = stepper.tick(2000L)
        assertEquals(100, at2.targetPowerWatts)
        assertEquals(IntervalPartPhase.OFF, at2.intervalPart?.phase)

        val at4 = stepper.tick(4000L)
        assertEquals(200, at4.targetPowerWatts)
        assertEquals(IntervalPartPhase.ON, at4.intervalPart?.phase)
        assertEquals(2, at4.intervalPart?.repIndex)

        val at8 = stepper.tick(8000L)
        assertTrue(at8.done)
    }

    @Test
    fun pauseStopsTimeAdvancement() {
        val stepper = WorkoutStepper.fromExecutionWorkout(steadySegment(durationSec = 10, watts = 200))

        stepper.start()
        val at0 = stepper.tick(0L)
        assertEquals(0, at0.elapsedSec)

        stepper.tick(2000L)
        stepper.pause()

        val afterPause = stepper.tick(12000L)
        assertEquals(2, afterPause.elapsedSec)

        stepper.resume()
        val baseline = stepper.tick(13000L)
        assertEquals(2, baseline.elapsedSec)

        val afterResume = stepper.tick(14000L)
        assertEquals(3, afterResume.elapsedSec)
    }

    @Test
    fun pauseWhileStoppedIsNoOp() {
        val stepper = WorkoutStepper.fromExecutionWorkout(steadySegment(durationSec = 5, watts = 200))

        stepper.start()
        stepper.tick(0L)
        val done = stepper.tick(5000L)
        assertTrue(done.done)

        stepper.pause()

        val afterPause = stepper.tick(6000L)
        assertTrue(afterPause.done)
    }

    @Test
    fun stopMarksDone() {
        val stepper = WorkoutStepper.fromExecutionWorkout(steadySegment(durationSec = 10, watts = 200))

        stepper.start()
        stepper.tick(0L)
        stepper.stop()

        val state = stepper.getState()
        assertTrue(state.paused)
    }

    @Test
    fun restoreResumesFromSavedState() {
        val workout = twoSteadySegments()
        val stepper1 = WorkoutStepper.fromExecutionWorkout(workout)

        stepper1.start()
        stepper1.tick(0L)
        stepper1.tick(1000L)
        val savedState = stepper1.getState()
        assertEquals(1, stepper1.currentElapsedSec())

        val stepper2 = WorkoutStepper.fromExecutionWorkout(workout)
        stepper2.restore(savedState)
        stepper2.resume()

        val baseline = stepper2.tick(2000L)
        assertEquals(1, baseline.elapsedSec)

        val output = stepper2.tick(3000L)
        assertFalse(output.done)
        assertEquals(2, output.elapsedSec)
    }

    @Test
    fun restoreFromTerminalStateIsDone() {
        val workout = steadySegment(durationSec = 2, watts = 200)
        val stepper1 = WorkoutStepper.fromExecutionWorkout(workout)

        stepper1.start()
        stepper1.tick(0L)
        val done = stepper1.tick(2000L)
        assertTrue(done.done)
        val savedState = stepper1.getState()

        val stepper2 = WorkoutStepper.fromExecutionWorkout(workout)
        stepper2.restore(savedState)

        val output = stepper2.tick(3000L)
        assertTrue(output.done)
    }

    @Test
    fun rampInterpolatesTargetPower() {
        val workout = ExecutionWorkout(
            name = "Ramp",
            description = "",
            author = "",
            tags = emptyList(),
            segments = listOf(
                ExecutionSegment.Ramp(
                    sourceStepIndex = 0,
                    durationSec = 4,
                    startWatts = 100,
                    endWatts = 200,
                    cadence = CadenceTarget.AnyCadence,
                ),
            ),
            totalDurationSec = 4,
        )
        val stepper = WorkoutStepper.fromExecutionWorkout(workout)

        stepper.start()
        val at0 = stepper.tick(0L)
        assertEquals(100, at0.targetPowerWatts)

        val at2 = stepper.tick(2000L)
        assertNotNull(at2.targetPowerWatts)
        assertTrue(at2.targetPowerWatts!! in 145..155)
    }

    @Test
    fun fixedCadenceTargetPassedThrough() {
        val workout = ExecutionWorkout(
            name = "Cadence",
            description = "",
            author = "",
            tags = emptyList(),
            segments = listOf(
                ExecutionSegment.Steady(
                    sourceStepIndex = 0,
                    durationSec = 2,
                    targetWatts = 200,
                    cadence = CadenceTarget.FixedCadence(90),
                ),
            ),
            totalDurationSec = 2,
        )
        val stepper = WorkoutStepper.fromExecutionWorkout(workout)

        stepper.start()
        val output = stepper.tick(0L)
        assertEquals(90, output.targetCadence)
    }

    @Test
    fun anyCadenceReturnsNullTarget() {
        val stepper = WorkoutStepper.fromExecutionWorkout(steadySegment(durationSec = 2, watts = 200))

        stepper.start()
        val output = stepper.tick(0L)
        assertNull(output.targetCadence)
    }

    @Test
    fun currentElapsedSecReflectsProgression() {
        val stepper = WorkoutStepper.fromExecutionWorkout(steadySegment(durationSec = 10, watts = 200))

        stepper.start()
        stepper.tick(0L)
        assertEquals(0, stepper.currentElapsedSec())

        stepper.tick(3000L)
        assertEquals(3, stepper.currentElapsedSec())
    }

    private fun steadySegment(durationSec: Int, watts: Int): ExecutionWorkout {
        return ExecutionWorkout(
            name = "Steady",
            description = "",
            author = "",
            tags = emptyList(),
            segments = listOf(
                ExecutionSegment.Steady(
                    sourceStepIndex = 0,
                    durationSec = durationSec,
                    targetWatts = watts,
                    cadence = CadenceTarget.AnyCadence,
                ),
            ),
            totalDurationSec = durationSec,
        )
    }

    private fun twoSteadySegments(): ExecutionWorkout {
        return ExecutionWorkout(
            name = "Two Segments",
            description = "",
            author = "",
            tags = emptyList(),
            segments = listOf(
                ExecutionSegment.Steady(
                    sourceStepIndex = 0,
                    durationSec = 2,
                    targetWatts = 200,
                    cadence = CadenceTarget.AnyCadence,
                ),
                ExecutionSegment.Steady(
                    sourceStepIndex = 1,
                    durationSec = 2,
                    targetWatts = 150,
                    cadence = CadenceTarget.AnyCadence,
                ),
            ),
            totalDurationSec = 4,
        )
    }
}

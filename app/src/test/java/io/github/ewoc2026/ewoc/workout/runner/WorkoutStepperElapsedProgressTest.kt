package io.github.ewoc2026.ewoc.workout.runner

import io.github.ewoc2026.ewoc.workout.CadenceTarget
import io.github.ewoc2026.ewoc.workout.ExecutionSegment
import io.github.ewoc2026.ewoc.workout.ExecutionWorkoutMapper
import io.github.ewoc2026.ewoc.workout.ExecutionWorkout
import io.github.ewoc2026.ewoc.workout.MappingResult
import io.github.ewoc2026.ewoc.workout.Step
import io.github.ewoc2026.ewoc.workout.WorkoutFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class WorkoutStepperElapsedProgressTest {
    @Test
    fun intervalsElapsedProgressMatchesExpandedTimeline() {
        val workout = WorkoutFile(
            name = "Intervals",
            description = null,
            author = null,
            tags = emptyList(),
            steps = listOf(
                Step.IntervalsT(
                    onDurationSec = 2,
                    offDurationSec = 3,
                    onPower = 0.8,
                    offPower = 0.5,
                    repeat = 2,
                    cadence = null,
                ),
            ),
        )
        val stepper = WorkoutStepper(workout = workout, ftpWatts = 200)

        val observed = runSecondBySecondElapsed(stepper)

        assertEquals((0..9).toList(), observed.activeElapsedSec)
        assertEquals(10, observed.doneElapsedSec)
    }

    @Test
    fun executionElapsedProgressMatchesSegmentDurations() {
        val workout = ExecutionWorkout(
            name = "Execution",
            description = "",
            author = "",
            tags = emptyList(),
            segments = listOf(
                ExecutionSegment.Steady(
                    sourceStepIndex = 0,
                    durationSec = 2,
                    targetWatts = 180,
                    cadence = CadenceTarget.AnyCadence,
                ),
                ExecutionSegment.Ramp(
                    sourceStepIndex = 1,
                    durationSec = 2,
                    startWatts = 180,
                    endWatts = 220,
                    cadence = CadenceTarget.AnyCadence,
                ),
            ),
            totalDurationSec = 4,
        )
        val stepper = WorkoutStepper.fromExecutionWorkout(workout)

        val observed = runSecondBySecondElapsed(stepper)

        assertEquals(listOf(0, 1, 2, 3), observed.activeElapsedSec)
        assertEquals(4, observed.doneElapsedSec)
    }

    @Test
    fun legacyIntervalsExposeStepAndPartRemaining() {
        val workout = WorkoutFile(
            name = "Intervals",
            description = null,
            author = null,
            tags = emptyList(),
            steps = listOf(
                Step.IntervalsT(
                    onDurationSec = 2,
                    offDurationSec = 3,
                    onPower = 0.8,
                    offPower = 0.5,
                    repeat = 2,
                    cadence = null,
                ),
            ),
        )
        val stepper = WorkoutStepper(workout = workout, ftpWatts = 200)

        stepper.start()
        val start = stepper.tick(0L)
        assertEquals(10, start.stepRemainingSec)
        assertNotNull(start.intervalPart)
        assertEquals(IntervalPartPhase.ON, start.intervalPart?.phase)
        assertEquals(1, start.intervalPart?.repIndex)
        assertEquals(2, start.intervalPart?.repTotal)
        assertEquals(2, start.intervalPart?.remainingSec)

        val second = stepper.tick(1000L)
        assertEquals(9, second.stepRemainingSec)
        assertEquals(IntervalPartPhase.ON, second.intervalPart?.phase)
        assertEquals(1, second.intervalPart?.remainingSec)

        val transitionToOff = stepper.tick(2000L)
        assertEquals(8, transitionToOff.stepRemainingSec)
        assertEquals(IntervalPartPhase.OFF, transitionToOff.intervalPart?.phase)
        assertEquals(3, transitionToOff.intervalPart?.remainingSec)
    }

    @Test
    fun executionIntervalsExposeStepAndPartRemaining() {
        val source = WorkoutFile(
            name = "Intervals",
            description = null,
            author = null,
            tags = emptyList(),
            steps = listOf(
                Step.IntervalsT(
                    onDurationSec = 2,
                    offDurationSec = 3,
                    onPower = 0.8,
                    offPower = 0.5,
                    repeat = 2,
                    cadence = null,
                ),
            ),
        )
        val mapped = ExecutionWorkoutMapper.map(source, ftp = 200) as MappingResult.Success
        val stepper = WorkoutStepper.fromExecutionWorkout(mapped.workout)

        stepper.start()
        val start = stepper.tick(0L)
        assertEquals(10, start.stepRemainingSec)
        assertEquals(IntervalPartPhase.ON, start.intervalPart?.phase)
        assertEquals(1, start.intervalPart?.repIndex)
        assertEquals(2, start.intervalPart?.repTotal)
        assertEquals(2, start.intervalPart?.remainingSec)

        val transitionToOff = stepper.tick(2000L)
        assertEquals(8, transitionToOff.stepRemainingSec)
        assertEquals(IntervalPartPhase.OFF, transitionToOff.intervalPart?.phase)
        assertEquals(1, transitionToOff.intervalPart?.repIndex)
        assertEquals(3, transitionToOff.intervalPart?.remainingSec)
    }

    @Test
    fun executionFreeRideEmitsNullTargetPower() {
        val workout = ExecutionWorkout(
            name = "Execution FreeRide",
            description = "",
            author = "",
            tags = emptyList(),
            segments = listOf(
                ExecutionSegment.FreeRide(
                    sourceStepIndex = 0,
                    durationSec = 3,
                    cadence = CadenceTarget.FixedCadence(85),
                ),
            ),
            totalDurationSec = 3,
        )
        val stepper = WorkoutStepper.fromExecutionWorkout(workout)

        stepper.start()
        val start = stepper.tick(0L)
        val second = stepper.tick(1000L)

        assertNull(start.targetPowerWatts)
        assertEquals(85, start.targetCadence)
        assertEquals("Free Ride", start.label)
        assertNull(second.targetPowerWatts)
    }

    private fun runSecondBySecondElapsed(stepper: WorkoutStepper): ObservedElapsed {
        val activeElapsed = mutableListOf<Int>()
        var doneElapsed = -1
        var nowMs = 0L
        val guardMaxSeconds = 120

        stepper.start()
        repeat(guardMaxSeconds) {
            val output = stepper.tick(nowMs)
            if (output.done) {
                doneElapsed = output.elapsedSec
                return ObservedElapsed(
                    activeElapsedSec = activeElapsed.toList(),
                    doneElapsedSec = doneElapsed,
                )
            }
            activeElapsed += output.elapsedSec
            nowMs += 1000L
        }

        throw AssertionError("Elapsed test exceeded guard limit without completion.")
    }

    private data class ObservedElapsed(
        val activeElapsedSec: List<Int>,
        val doneElapsedSec: Int,
    )
}

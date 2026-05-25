package com.example.ergometerapp.workout

import com.example.ergometerapp.workout.runner.WorkoutStepper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutRunnerDryRunTest {
    @Test
    fun steadyOnlyWorkoutEmitsTargetForEachSecond() {
        val workout = ExecutionWorkout(
            name = "Steady only",
            description = "",
            author = "",
            tags = emptyList(),
            segments = listOf(
                ExecutionSegment.Steady(
                    sourceStepIndex = 0,
                    durationSec = 60,
                    targetWatts = 150,
                    cadence = CadenceTarget.AnyCadence,
                ),
            ),
            totalDurationSec = 60,
        )

        val result = runDryRun(workout)

        assertEquals(60, result.totalDurationSec)
        assertEquals(60, result.targetWattsBySecond.size)
        assertTrue(result.targetWattsBySecond.all { it == 150 })
    }

    @Test
    fun steadyThenRampWorkoutEmitsExpectedRampTargetsAndDuration() {
        val workout = ExecutionWorkout(
            name = "Steady + ramp",
            description = "",
            author = "",
            tags = emptyList(),
            segments = listOf(
                ExecutionSegment.Steady(
                    sourceStepIndex = 0,
                    durationSec = 30,
                    targetWatts = 150,
                    cadence = CadenceTarget.AnyCadence,
                ),
                ExecutionSegment.Ramp(
                    sourceStepIndex = 1,
                    durationSec = 60,
                    startWatts = 150,
                    endWatts = 250,
                    cadence = CadenceTarget.AnyCadence,
                ),
            ),
            totalDurationSec = 90,
        )

        val result = runDryRun(workout)
        val rampTargets = result.targetWattsBySecond.drop(30)

        assertEquals(90, result.totalDurationSec)
        assertEquals(90, result.targetWattsBySecond.size)
        assertTrue(result.targetWattsBySecond.take(30).all { it == 150 })
        assertEquals(150, rampTargets.first())
        assertEquals(200, rampTargets[30])
        assertEquals(248, rampTargets.last())
        assertTrue(rampTargets.zipWithNext().all { (previous, next) -> next >= previous })
    }

    @Test
    fun executionStepperExposesFixedCadenceTarget() {
        val workout = ExecutionWorkout(
            name = "Cadence test",
            description = "",
            author = "",
            tags = emptyList(),
            segments = listOf(
                ExecutionSegment.Steady(
                    sourceStepIndex = 0,
                    durationSec = 30,
                    targetWatts = 180,
                    cadence = CadenceTarget.FixedCadence(92),
                ),
            ),
            totalDurationSec = 30,
        )

        val stepper = WorkoutStepper.fromExecutionWorkout(workout)
        stepper.start()
        val output = stepper.tick(0L)

        assertEquals(180, output.targetPowerWatts)
        assertEquals(92, output.targetCadence)
        assertTrue(!output.done)
    }

    private fun runDryRun(workout: ExecutionWorkout): DryRunResult {
        val stepper = WorkoutStepper.fromExecutionWorkout(workout)
        val targets = mutableListOf<Int>()
        var nowUptimeMs = 0L

        stepper.start()

        while (true) {
            val output = stepper.tick(nowUptimeMs)
            if (output.done) {
                return DryRunResult(
                    targetWattsBySecond = targets.toList(),
                    totalDurationSec = targets.size,
                )
            }

            targets += output.targetPowerWatts
                ?: throw AssertionError("Expected non-null target watts while dry-running.")
            nowUptimeMs += 1000L

            if (targets.size > workout.totalDurationSec + 5) {
                throw AssertionError("Dry-run exceeded expected duration guard.")
            }
        }
    }

    private data class DryRunResult(
        val targetWattsBySecond: List<Int>,
        val totalDurationSec: Int,
    )
}

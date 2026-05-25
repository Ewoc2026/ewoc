package com.example.ergometerapp.workout.runner

import com.example.ergometerapp.workout.Step
import com.example.ergometerapp.workout.WorkoutFile
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutStepperRampDirectionTest {
    @Test
    fun warmupRampsFromPowerLowToPowerHigh() {
        val stepper = WorkoutStepper(
            workout = workoutWithSingleStep(
                Step.Warmup(
                    durationSec = 4,
                    powerLow = 0.40,
                    powerHigh = 0.80,
                    cadence = null,
                ),
            ),
            ftpWatts = 200,
        )

        val targets = runSecondBySecond(stepper)

        assertEquals(listOf(80, 100, 120, 140), targets)
    }

    @Test
    fun cooldownRampsFromPowerHighToPowerLow() {
        val stepper = WorkoutStepper(
            workout = workoutWithSingleStep(
                Step.Cooldown(
                    durationSec = 4,
                    powerLow = 0.40,
                    powerHigh = 0.80,
                    cadence = null,
                ),
            ),
            ftpWatts = 200,
        )

        val targets = runSecondBySecond(stepper)

        assertEquals(listOf(160, 140, 120, 100), targets)
    }

    @Test
    fun cooldownStillDescendsWhenPowerFieldsAreSwapped() {
        val stepper = WorkoutStepper(
            workout = workoutWithSingleStep(
                Step.Cooldown(
                    durationSec = 4,
                    powerLow = 0.80,
                    powerHigh = 0.40,
                    cadence = null,
                ),
            ),
            ftpWatts = 200,
        )

        val targets = runSecondBySecond(stepper)

        assertEquals(listOf(160, 140, 120, 100), targets)
    }

    private fun runSecondBySecond(stepper: WorkoutStepper): List<Int> {
        val targets = mutableListOf<Int>()
        var now = 0L
        stepper.start()

        while (true) {
            val output = stepper.tick(now)
            if (output.done) {
                return targets
            }
            targets += output.targetPowerWatts
                ?: throw AssertionError("Expected target watts while workout is not done.")
            now += 1000L
        }
    }

    private fun workoutWithSingleStep(step: Step): WorkoutFile {
        return WorkoutFile(
            name = "Direction test",
            description = null,
            author = null,
            tags = emptyList(),
            steps = listOf(step),
        )
    }
}

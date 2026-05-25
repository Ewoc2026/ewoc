package com.example.ergometerapp.workout

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutExecutionStepCounterTest {
    @Test
    fun countUsesExecutionSegmentsWhenStrictMappingSucceeds() {
        val workout = workoutWithSteps(
            Step.Warmup(
                durationSec = 60,
                powerLow = 0.5,
                powerHigh = 0.7,
                cadence = null,
            ),
            Step.IntervalsT(
                onDurationSec = 10,
                offDurationSec = 20,
                onPower = 1.1,
                offPower = 0.5,
                repeat = 3,
                cadence = 95,
            ),
            Step.Cooldown(
                durationSec = 30,
                powerLow = 0.6,
                powerHigh = 0.3,
                cadence = null,
            ),
        )

        val count = WorkoutExecutionStepCounter.count(workout, ftpWatts = 240)

        assertEquals(8, count)
    }

    @Test
    fun countFallsBackToLegacyExpansionWhenMappingFails() {
        val workout = workoutWithSteps(
            Step.FreeRide(
                durationSec = 120,
                cadence = null,
            ),
            Step.IntervalsT(
                onDurationSec = 5,
                offDurationSec = 10,
                onPower = 0.9,
                offPower = 0.4,
                repeat = 2,
                cadence = null,
            ),
            Step.SteadyState(
                durationSec = 30,
                power = null,
                cadence = null,
            ),
        )

        val count = WorkoutExecutionStepCounter.count(workout, ftpWatts = 200)

        assertEquals(6, count)
    }

    @Test
    fun countIgnoresLegacyStepsThatCannotAdvanceTime() {
        val workout = workoutWithSteps(
            Step.Warmup(
                durationSec = 0,
                powerLow = 0.4,
                powerHigh = 0.6,
                cadence = null,
            ),
            Step.IntervalsT(
                onDurationSec = 10,
                offDurationSec = 10,
                onPower = 0.9,
                offPower = 0.5,
                repeat = 0,
                cadence = null,
            ),
            Step.FreeRide(
                durationSec = null,
                cadence = null,
            ),
            Step.Unknown(
                tagName = "Custom",
                attributes = mapOf("Duration" to "60"),
            ),
        )

        val count = WorkoutExecutionStepCounter.count(workout, ftpWatts = 200)

        assertEquals(0, count)
    }

    private fun workoutWithSteps(vararg steps: Step): WorkoutFile {
        return WorkoutFile(
            name = "Count test",
            description = null,
            author = null,
            tags = emptyList(),
            steps = steps.toList(),
        )
    }
}

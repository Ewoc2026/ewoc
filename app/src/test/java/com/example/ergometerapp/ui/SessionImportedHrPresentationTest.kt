package com.example.ergometerapp.ui

import com.example.ergometerapp.workout.ImportedErgoWorkout
import com.example.ergometerapp.workout.ImportedErgoWorkoutStep
import com.example.ergometerapp.workout.runner.RunnerSegmentType
import com.example.ergometerapp.workout.runner.RunnerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionImportedHrPresentationTest {

    @Test
    fun resolveActiveImportedHrStepReturnsHeartRateStepForActiveHrSegment() {
        val step = ImportedErgoWorkoutStep.HeartRateSteady(
            stepIndex = 1,
            startOffsetSec = 120,
            durationSec = 240,
            lowBpm = 145,
            highBpm = 155,
            initialPowerWatts = 210,
            minPowerWatts = 170,
            maxPowerWatts = 260,
            signalLossPowerWatts = 180,
        )
        val workout = ImportedErgoWorkout(
            title = "Imported HR Tempo Builder",
            description = null,
            steps = listOf(
                ImportedErgoWorkoutStep.PowerRamp(
                    stepIndex = 0,
                    startOffsetSec = 0,
                    durationSec = 120,
                    fromWatts = 150,
                    toWatts = 190,
                ),
                step,
            ),
            totalDurationSec = 360,
        )

        val resolved = resolveActiveImportedHrStep(
            workout = workout,
            runnerState = RunnerState(
                running = true,
                paused = false,
                done = false,
                label = "HR Steady",
                targetPowerWatts = 214,
                targetCadence = 90,
                workoutElapsedSec = 180,
                stepRemainingSec = 180,
                intervalPart = null,
                segmentType = RunnerSegmentType.HEART_RATE_STEADY,
                sourceStepIndex = 1,
            ),
        )

        assertEquals(step, resolved)
    }

    @Test
    fun resolveActiveImportedHrStepIgnoresNonHrRunnerSegments() {
        val workout = ImportedErgoWorkout(
            title = "Imported HR Tempo Builder",
            description = null,
            steps = listOf(
                ImportedErgoWorkoutStep.HeartRateSteady(
                    stepIndex = 0,
                    startOffsetSec = 0,
                    durationSec = 240,
                    lowBpm = 145,
                    highBpm = 155,
                    initialPowerWatts = 210,
                    minPowerWatts = 170,
                    maxPowerWatts = 260,
                    signalLossPowerWatts = 180,
                ),
            ),
            totalDurationSec = 240,
        )

        val resolved = resolveActiveImportedHrStep(
            workout = workout,
            runnerState = RunnerState(
                running = true,
                paused = false,
                done = false,
                label = "Warmup",
                targetPowerWatts = 180,
                targetCadence = 90,
                workoutElapsedSec = 30,
                stepRemainingSec = 90,
                intervalPart = null,
                segmentType = RunnerSegmentType.RAMP,
                sourceStepIndex = 0,
            ),
        )

        assertNull(resolved)
    }
}

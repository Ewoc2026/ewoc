package io.github.ewoc2026.ewoc.ui

import io.github.ewoc2026.ewoc.ui.components.SegmentKind
import io.github.ewoc2026.ewoc.ui.components.WorkoutProfileSegment
import io.github.ewoc2026.ewoc.workout.runner.RunnerSegmentType
import io.github.ewoc2026.ewoc.workout.runner.RunnerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionTargetPowerLabelTest {

    @Test
    fun stoppedImportedHrFallbackPrefersLastAppliedTargetOverPlannedSegmentTarget() {
        val label = sessionTargetPowerLabel(
            runnerState = RunnerState.stopped(),
            activeSegment = WorkoutProfileSegment(
                startSec = 0,
                durationSec = 60,
                startPowerRelFtp = 0.9,
                endPowerRelFtp = 0.9,
                kind = SegmentKind.STEADY,
            ),
            ftpWatts = 200,
            fallbackTargetPower = 150,
            unknown = "-",
        )

        assertEquals("150 W", label)
    }

    @Test
    fun importedHrDisplayTargetPrefersLastAppliedTargetOverRunnerAnchor() {
        val target = sessionDisplayedTargetPowerWatts(
            runnerState = RunnerState(
                running = true,
                paused = false,
                done = false,
                label = "HR control",
                targetPowerWatts = 150,
                targetCadence = 90,
                workoutElapsedSec = 15,
                stepRemainingSec = 585,
                intervalPart = null,
                segmentType = RunnerSegmentType.HEART_RATE_STEADY,
                sourceStepIndex = 1,
            ),
            fallbackTargetPower = 155,
        )

        assertEquals(155, target)
    }

    @Test
    fun nonHrDisplayTargetStillPrefersRunnerTarget() {
        val target = sessionDisplayedTargetPowerWatts(
            runnerState = RunnerState(
                running = true,
                paused = false,
                done = false,
                label = "Warmup",
                targetPowerWatts = 150,
                targetCadence = 90,
                workoutElapsedSec = 15,
                stepRemainingSec = 45,
                intervalPart = null,
                segmentType = RunnerSegmentType.RAMP,
                sourceStepIndex = 0,
            ),
            fallbackTargetPower = 155,
        )

        assertEquals(150, target)
    }

    @Test
    fun importedHrDisplayTargetFallsBackToRunnerWhenNoOverrideExists() {
        val target = sessionDisplayedTargetPowerWatts(
            runnerState = RunnerState(
                running = true,
                paused = false,
                done = false,
                label = "HR control",
                targetPowerWatts = 150,
                targetCadence = 90,
                workoutElapsedSec = 15,
                stepRemainingSec = 585,
                intervalPart = null,
                segmentType = RunnerSegmentType.HEART_RATE_STEADY,
                sourceStepIndex = 1,
            ),
            fallbackTargetPower = null,
        )

        assertEquals(150, target)
    }

    @Test
    fun displayTargetReturnsNullWhenNoTargetExists() {
        val target = sessionDisplayedTargetPowerWatts(
            runnerState = RunnerState.stopped(),
            fallbackTargetPower = null,
        )

        assertNull(target)
    }
}

package io.github.ewoc2026.ewoc.baseline

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BaselineFitnessTestResultCalculatorTest {
    @Test
    fun calculateReturnsCompletedResultWhenSixFullRampMinutesExist() {
        val result = BaselineFitnessTestResultCalculator.calculate(
            input = completedInput(
                stopReason = BaselineFitnessTestStopReason.MANUAL_STOP,
                rampSteps = listOf(
                    step(targetWatts = 100),
                    step(targetWatts = 120),
                    step(targetWatts = 140),
                    step(targetWatts = 160),
                    step(targetWatts = 180),
                    step(targetWatts = 200, maxSmoothedHeartRateBpm = 172),
                ),
                hrCoverageRatio = 0.92,
            ),
        )

        assertEquals(BaselineFitnessTestStatus.COMPLETED, result.status)
        assertEquals(6, result.validRampMinutes)
        assertEquals(200, result.lastFullStepWatts)
        assertEquals(150, result.ftpEstimateWatts)
        assertEquals(172, result.thresholdHrEstimateBpm)
        assertEquals(BaselineFitnessTestConfidence.LOW, result.confidence)
    }

    @Test
    fun calculateReturnsMediumAndHighConfidenceFromDurationAndGapRules() {
        val medium = BaselineFitnessTestResultCalculator.calculate(
            input = completedInput(
                stopReason = BaselineFitnessTestStopReason.POWER_SIGNAL_LOST,
                rampSteps = (0 until 8).map { index ->
                    step(targetWatts = 100 + index * 20, maxPowerGapSec = 3.0)
                },
                hrCoverageRatio = 0.0,
            ),
        )
        val high = BaselineFitnessTestResultCalculator.calculate(
            input = completedInput(
                stopReason = BaselineFitnessTestStopReason.CADENCE_DROP,
                rampSteps = (0 until 10).map { index ->
                    step(targetWatts = 100 + index * 20, maxPowerGapSec = 2.0)
                },
                hrCoverageRatio = 0.0,
            ),
        )

        assertEquals(BaselineFitnessTestConfidence.MEDIUM, medium.confidence)
        assertEquals(BaselineFitnessTestConfidence.HIGH, high.confidence)
    }

    @Test
    fun calculateCapsConfidenceAtLowWhenPowerGapsExceedThreshold() {
        val result = BaselineFitnessTestResultCalculator.calculate(
            input = completedInput(
                stopReason = BaselineFitnessTestStopReason.POWER_SIGNAL_LOST,
                rampSteps = (0 until 10).map { index ->
                    step(targetWatts = 100 + index * 20, maxPowerGapSec = 8.5)
                },
                hrCoverageRatio = 0.95,
            ),
        )

        assertEquals(BaselineFitnessTestStatus.COMPLETED, result.status)
        assertEquals(BaselineFitnessTestConfidence.LOW, result.confidence)
    }

    @Test
    fun calculateReturnsInvalidWhenRampEndsBeforeSixFullMinutes() {
        val result = BaselineFitnessTestResultCalculator.calculate(
            input = completedInput(
                stopReason = BaselineFitnessTestStopReason.CADENCE_DROP,
                rampSteps = listOf(
                    step(targetWatts = 100),
                    step(targetWatts = 120),
                    step(targetWatts = 140),
                    step(targetWatts = 160),
                    step(targetWatts = 180, completedSeconds = 35),
                ),
                hrCoverageRatio = 0.91,
            ),
        )

        assertEquals(BaselineFitnessTestStatus.INVALID, result.status)
        assertNull(result.ftpEstimateWatts)
        assertNull(result.confidence)
        assertNull(result.thresholdHrEstimateBpm)
    }

    @Test
    fun calculateReturnsCancelledForControlLossMidTest() {
        val result = BaselineFitnessTestResultCalculator.calculate(
            input = completedInput(
                stopReason = BaselineFitnessTestStopReason.CONTROL_LOST_MID_TEST,
                rampSteps = (0 until 9).map { index ->
                    step(targetWatts = 100 + index * 20)
                },
                hrCoverageRatio = 0.95,
            ),
        )

        assertEquals(BaselineFitnessTestStatus.CANCELLED, result.status)
        assertNull(result.ftpEstimateWatts)
        assertNull(result.confidence)
    }

    private fun completedInput(
        stopReason: BaselineFitnessTestStopReason,
        rampSteps: List<BaselineFitnessTestRampStepResult>,
        hrCoverageRatio: Double,
    ): BaselineFitnessTestComputationInput {
        return BaselineFitnessTestComputationInput(
            controlMode = BaselineFitnessTestControlMode.ERG,
            stopReason = stopReason,
            startedAt = Instant.parse("2026-03-15T10:00:00Z"),
            completedAt = Instant.parse("2026-03-15T10:16:00Z"),
            startWatts = 100,
            warmupCompleted = true,
            rampSteps = rampSteps,
            hrCoverageRatio = hrCoverageRatio,
            sensorProfile = BaselineFitnessTestSensorProfile(
                power = true,
                heartRate = true,
                cadence = true,
            ),
        )
    }

    private fun step(
        targetWatts: Int,
        completedSeconds: Int = BaselineFitnessTestProtocol.RAMP_STEP_DURATION_SEC,
        maxPowerGapSec: Double = 0.0,
        maxSmoothedHeartRateBpm: Int? = null,
    ): BaselineFitnessTestRampStepResult {
        return BaselineFitnessTestRampStepResult(
            targetWatts = targetWatts,
            completedSeconds = completedSeconds,
            maxPowerGapSec = maxPowerGapSec,
            maxSmoothedHeartRateBpm = maxSmoothedHeartRateBpm,
        )
    }
}

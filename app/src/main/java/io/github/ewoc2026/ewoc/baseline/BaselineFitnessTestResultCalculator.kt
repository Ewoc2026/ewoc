package io.github.ewoc2026.ewoc.baseline

/**
 * Computes the persisted outcome for a finished baseline test attempt.
 *
 * Cancellation stays explicit for user-aborted and control-loss paths so later FTP-promotion code
 * does not need to reverse-engineer intent from partial ramp data.
 */
internal object BaselineFitnessTestResultCalculator {
    fun calculate(input: BaselineFitnessTestComputationInput): BaselineFitnessTestResult {
        val validRampSteps = input.rampSteps.filter { step ->
            step.completedSeconds >= BaselineFitnessTestProtocol.RAMP_STEP_DURATION_SEC
        }
        val validRampMinutes = validRampSteps.size
        val lastFullStep = validRampSteps.maxByOrNull { step -> step.targetWatts }
        val lastFullStepWatts = lastFullStep?.targetWatts
        val maxPowerGapSec = input.rampSteps.maxOfOrNull { step -> step.maxPowerGapSec } ?: 0.0
        val normalizedHrCoverage = input.hrCoverageRatio.coerceIn(0.0, 1.0)

        val status = when {
            input.stopReason in cancelledStopReasons -> BaselineFitnessTestStatus.CANCELLED
            !input.warmupCompleted -> BaselineFitnessTestStatus.CANCELLED
            validRampMinutes >= BaselineFitnessTestProtocol.MINIMUM_VALID_RAMP_MINUTES &&
                lastFullStepWatts != null -> BaselineFitnessTestStatus.COMPLETED

            else -> BaselineFitnessTestStatus.INVALID
        }

        val ftpEstimateWatts = if (status == BaselineFitnessTestStatus.COMPLETED && lastFullStepWatts != null) {
            BaselineFitnessTestProtocol.roundToNearestFive(
                lastFullStepWatts * BaselineFitnessTestProtocol.FTP_FACTOR,
            )
        } else {
            null
        }
        val confidence = if (status == BaselineFitnessTestStatus.COMPLETED) {
            confidenceFor(
                validRampMinutes = validRampMinutes,
                maxPowerGapSec = maxPowerGapSec,
            )
        } else {
            null
        }
        val thresholdHrEstimateBpm =
            if (
                status == BaselineFitnessTestStatus.COMPLETED &&
                normalizedHrCoverage >= BaselineFitnessTestProtocol.HR_COVERAGE_THRESHOLD
            ) {
                lastFullStep?.maxSmoothedHeartRateBpm
            } else {
                null
            }

        return BaselineFitnessTestResult(
            testVersion = input.testVersion,
            status = status,
            stopReason = input.stopReason,
            controlMode = input.controlMode,
            startedAt = input.startedAt,
            completedAt = input.completedAt,
            startWatts = input.startWatts,
            validRampMinutes = validRampMinutes,
            lastFullStepWatts = lastFullStepWatts,
            ftpEstimateWatts = ftpEstimateWatts,
            peak1mPowerWatts = lastFullStepWatts,
            thresholdHrEstimateBpm = thresholdHrEstimateBpm,
            confidence = confidence,
            maxPowerGapSec = maxPowerGapSec,
            hrCoverageRatio = normalizedHrCoverage,
            sensorProfile = input.sensorProfile,
        )
    }

    private fun confidenceFor(
        validRampMinutes: Int,
        maxPowerGapSec: Double,
    ): BaselineFitnessTestConfidence {
        if (maxPowerGapSec > BaselineFitnessTestProtocol.POWER_SIGNAL_LOSS_THRESHOLD_SEC.toDouble()) {
            return BaselineFitnessTestConfidence.LOW
        }
        if (
            validRampMinutes >= BaselineFitnessTestProtocol.HIGH_CONFIDENCE_RAMP_MINUTES &&
            maxPowerGapSec <= 2.0
        ) {
            return BaselineFitnessTestConfidence.HIGH
        }
        if (validRampMinutes >= BaselineFitnessTestProtocol.MEDIUM_CONFIDENCE_RAMP_MINUTES) {
            return BaselineFitnessTestConfidence.MEDIUM
        }
        return BaselineFitnessTestConfidence.LOW
    }

    private val cancelledStopReasons = setOf(
        BaselineFitnessTestStopReason.CONTROL_GRANT_DECLINED,
        BaselineFitnessTestStopReason.CONTROL_LOST_MID_TEST,
        BaselineFitnessTestStopReason.USER_CANCEL,
    )
}

package io.github.ewoc2026.ewoc.baseline

import java.time.Instant

/**
 * Control ownership mode used while the baseline test is active.
 */
internal enum class BaselineFitnessTestControlMode {
    ERG,
    ADVISORY,
}

/**
 * Persisted terminal status for the latest baseline test attempt.
 */
internal enum class BaselineFitnessTestStatus {
    COMPLETED,
    INVALID,
    CANCELLED,
}

/**
 * Canonical stop reason vocabulary shared by the state machine and persistence payload.
 */
internal enum class BaselineFitnessTestStopReason {
    MANUAL_STOP,
    CADENCE_DROP,
    POWER_SIGNAL_LOST,
    DEVICE_DISCONNECT,
    CONTROL_GRANT_DECLINED,
    CONTROL_LOST_MID_TEST,
    USER_CANCEL,
}

/**
 * Confidence bucket for a completed FTP estimate.
 */
internal enum class BaselineFitnessTestConfidence {
    LOW,
    MEDIUM,
    HIGH,
}

/**
 * Feature-unavailable reason before an attempt can start.
 */
internal enum class BaselineFitnessTestUnavailableReason {
    POWER_TELEMETRY_UNAVAILABLE,
}

/**
 * Availability snapshot that later result summaries can persist without replaying raw telemetry.
 */
internal data class BaselineFitnessTestSensorProfile(
    val power: Boolean,
    val heartRate: Boolean,
    val cadence: Boolean,
)

/**
 * Aggregated data for one ramp minute.
 *
 * The coordinator is expected to provide 30-second-smoothed heart-rate maxima here when heart-rate
 * telemetry is available, so the result calculator can stay deterministic without owning sample
 * buffering or smoothing windows.
 */
internal data class BaselineFitnessTestRampStepResult(
    val targetWatts: Int,
    val completedSeconds: Int,
    val maxPowerGapSec: Double = 0.0,
    val maxSmoothedHeartRateBpm: Int? = null,
)

/**
 * Pure result-calculation input for one finished baseline test attempt.
 */
internal data class BaselineFitnessTestComputationInput(
    val controlMode: BaselineFitnessTestControlMode,
    val stopReason: BaselineFitnessTestStopReason,
    val startedAt: Instant,
    val completedAt: Instant,
    val startWatts: Int,
    val warmupCompleted: Boolean,
    val rampSteps: List<BaselineFitnessTestRampStepResult>,
    val hrCoverageRatio: Double,
    val sensorProfile: BaselineFitnessTestSensorProfile,
    val testVersion: String = BaselineFitnessTestProtocol.TEST_VERSION,
)

/**
 * Latest-only baseline test summary persisted alongside the active FTP.
 *
 * Nullable fields are deliberate because invalid and cancelled attempts still need one durable
 * summary shape without inventing a second payload schema.
 */
internal data class BaselineFitnessTestResult(
    val testVersion: String = BaselineFitnessTestProtocol.TEST_VERSION,
    val status: BaselineFitnessTestStatus,
    val stopReason: BaselineFitnessTestStopReason,
    val controlMode: BaselineFitnessTestControlMode,
    val startedAt: Instant,
    val completedAt: Instant,
    val startWatts: Int,
    val validRampMinutes: Int,
    val lastFullStepWatts: Int?,
    val ftpEstimateWatts: Int?,
    val peak1mPowerWatts: Int?,
    val thresholdHrEstimateBpm: Int?,
    val confidence: BaselineFitnessTestConfidence?,
    val maxPowerGapSec: Double,
    val hrCoverageRatio: Double,
    val sensorProfile: BaselineFitnessTestSensorProfile,
)

internal typealias BaselineFitnessTestAttemptResult = BaselineFitnessTestResult
internal typealias BaselineFitnessTestAttemptStatus = BaselineFitnessTestStatus

internal data class BaselineFitnessTestSettingsSnapshot(
    val ftpSource: String? = null,
    val ftpLastTestedAt: Instant? = null,
    val lastBaselineTest: BaselineFitnessTestAttemptResult? = null,
)

internal val BaselineFitnessTestAttemptResult.isPromotable: Boolean
    get() = status == BaselineFitnessTestStatus.COMPLETED && ftpEstimateWatts != null

/**
 * Power-zone descriptor derived from the current FTP instead of persisted zone tables.
 */
internal data class BaselineFitnessPowerZone(
    val code: String,
    val label: String,
    val minFractionFtpInclusive: Double,
    val maxFractionFtpInclusive: Double?,
    val minWatts: Int,
    val maxWattsInclusive: Int?,
)

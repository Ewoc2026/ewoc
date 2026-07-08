package io.github.ewoc2026.ewoc.baseline

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Canonical protocol constants and helper math for the V1 baseline fitness test.
 */
internal object BaselineFitnessTestProtocol {
    const val TEST_VERSION = "baseline_fitness_test_v1"
    const val WARMUP_DURATION_SEC = 300
    const val RAMP_STEP_DURATION_SEC = 60
    const val RAMP_INCREMENT_WATTS = 20
    const val MINIMUM_VALID_RAMP_MINUTES = 6
    const val MEDIUM_CONFIDENCE_RAMP_MINUTES = 8
    const val HIGH_CONFIDENCE_RAMP_MINUTES = 10
    const val CADENCE_AUTO_STOP_THRESHOLD_RPM = 30
    const val CADENCE_AUTO_STOP_HOLD_SEC = 10
    const val POWER_SIGNAL_LOSS_THRESHOLD_SEC = 8
    const val COOLDOWN_DURATION_SEC = 120
    const val HR_COVERAGE_THRESHOLD = 0.80
    const val FTP_FACTOR = 0.75
    private const val DEFAULT_START_WATTS = 100
    private const val MINIMUM_START_WATTS_WITH_PRIOR_FTP = 75

    /**
     * Resolves the warm-up and first-ramp-minute target from the active FTP, when available.
     */
    fun computeStartWatts(priorFtpWatts: Int?): Int {
        val priorFtp = priorFtpWatts?.takeIf { it > 0 } ?: return DEFAULT_START_WATTS
        val anchoredTarget = max(priorFtp * 0.45, MINIMUM_START_WATTS_WITH_PRIOR_FTP.toDouble())
        return roundToNearestFive(anchoredTarget)
    }

    /**
     * Returns the ramp target for the given zero-based ramp minute index.
     */
    fun targetWattsForRampMinute(
        startWatts: Int,
        rampMinuteIndex: Int,
    ): Int {
        return startWatts + rampMinuteIndex.coerceAtLeast(0) * RAMP_INCREMENT_WATTS
    }

    /**
     * Converts any watt value to the protocol's nearest-5-watts storage rule.
     */
    fun roundToNearestFive(watts: Double): Int {
        return (watts / 5.0).roundToInt() * 5
    }
}

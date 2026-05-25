package com.example.ergometerapp.session

import kotlin.math.pow
import kotlin.math.round

/**
 * Streaming calculator for session "actual TSS" from timestamped power samples.
 *
 * The implementation is memory-stable:
 * - power is treated as piecewise-constant between incoming FTMS samples
 * - a bounded rolling window is used for normalized-power style smoothing
 * - long telemetry gaps are handled conservatively by limiting hold duration;
 *   remaining gap seconds are treated as zero power
 * - only running aggregates are stored, never full-session sample arrays
 *
 * The rolling window starts immediately using available samples, then converges
 * to the configured full window size.
 */
class ActualTssAccumulator(
    ftpWatts: Int,
    private val rollingWindowSeconds: Int = DEFAULT_ROLLING_WINDOW_SECONDS,
    private val maxHoldSeconds: Int = DEFAULT_MAX_HOLD_SECONDS,
) {
    private val safeFtp = ftpWatts.coerceAtLeast(1).toDouble()
    private val rollingWindow = IntArray(rollingWindowSeconds.coerceAtLeast(1))
    private val safeMaxHoldSeconds = maxHoldSeconds.coerceAtLeast(0).toLong()

    private var rollingCount: Int = 0
    private var rollingIndex: Int = 0
    private var rollingSumWatts: Long = 0

    private var rollingAverageFourthSum: Double = 0.0
    private var rollingAverageSampleCount: Long = 0

    private var lastSampleSecond: Long? = null
    private var lastPowerWatts: Int = 0

    /**
     * Records one power sample. Timestamp is expected in wall-clock milliseconds.
     */
    fun recordPower(powerWatts: Int, timestampMillis: Long) {
        val sampleSecond = timestampMillis / 1000L
        val normalizedPower = powerWatts.coerceAtLeast(0)
        val previousSecond = lastSampleSecond

        if (previousSecond == null) {
            lastSampleSecond = sampleSecond
            lastPowerWatts = normalizedPower
            return
        }

        if (sampleSecond <= previousSecond) {
            // Keep the latest power for this second; integration happens only when time advances.
            lastPowerWatts = normalizedPower
            return
        }

        appendGapWithConservativeHold(
            heldPowerWatts = lastPowerWatts,
            fromExclusiveSecond = previousSecond,
            toInclusiveSecond = sampleSecond,
        )
        lastSampleSecond = sampleSecond
        lastPowerWatts = normalizedPower
    }

    /**
     * Finalizes and returns one-decimal TSS for the session duration, or null when unavailable.
     */
    fun calculateTss(durationSeconds: Int, stopTimestampMillis: Long): Double? {
        flushUntil(stopTimestampMillis = stopTimestampMillis)
        if (durationSeconds <= 0 || rollingAverageSampleCount <= 0L) {
            return null
        }

        val normalizedPower = (rollingAverageFourthSum / rollingAverageSampleCount.toDouble()).pow(0.25)
        if (!normalizedPower.isFinite()) {
            return null
        }

        val intensityFactor = normalizedPower / safeFtp
        val tss = durationSeconds.toDouble() / 3600.0 * intensityFactor * intensityFactor * 100.0
        if (!tss.isFinite()) {
            return null
        }
        return round(tss * 10.0) / 10.0
    }

    /**
     * Flushes held power only up to the pause boundary, then clears hold state so
     * paused wall-clock gaps do not extend the previous workload forward.
     */
    fun pauseAt(timestampMillis: Long) {
        flushUntil(stopTimestampMillis = timestampMillis)
        lastSampleSecond = null
        lastPowerWatts = 0
    }

    private fun flushUntil(stopTimestampMillis: Long) {
        val previousSecond = lastSampleSecond ?: return
        val stopSecond = stopTimestampMillis / 1000L
        if (stopSecond <= previousSecond) return

        appendGapWithConservativeHold(
            heldPowerWatts = lastPowerWatts,
            fromExclusiveSecond = previousSecond,
            toInclusiveSecond = stopSecond,
        )
        lastSampleSecond = stopSecond
    }

    private fun appendGapWithConservativeHold(
        heldPowerWatts: Int,
        fromExclusiveSecond: Long,
        toInclusiveSecond: Long,
    ) {
        if (toInclusiveSecond <= fromExclusiveSecond) return
        val gapSeconds = toInclusiveSecond - fromExclusiveSecond
        val holdSeconds = minOf(gapSeconds, safeMaxHoldSeconds)
        val holdEndSecond = fromExclusiveSecond + holdSeconds

        if (holdSeconds > 0L) {
            appendSeconds(
                powerWatts = heldPowerWatts,
                fromExclusiveSecond = fromExclusiveSecond,
                toInclusiveSecond = holdEndSecond,
            )
        }
        if (holdEndSecond < toInclusiveSecond) {
            appendSeconds(
                powerWatts = 0,
                fromExclusiveSecond = holdEndSecond,
                toInclusiveSecond = toInclusiveSecond,
            )
        }
    }

    private fun appendSeconds(
        powerWatts: Int,
        fromExclusiveSecond: Long,
        toInclusiveSecond: Long,
    ) {
        var second = fromExclusiveSecond + 1L
        while (second <= toInclusiveSecond) {
            appendOneSecond(powerWatts)
            second += 1L
        }
    }

    private fun appendOneSecond(powerWatts: Int) {
        if (rollingCount < rollingWindow.size) {
            rollingWindow[rollingCount] = powerWatts
            rollingCount += 1
            rollingSumWatts += powerWatts.toLong()
        } else {
            val outgoing = rollingWindow[rollingIndex]
            rollingSumWatts += powerWatts.toLong() - outgoing.toLong()
            rollingWindow[rollingIndex] = powerWatts
            rollingIndex = (rollingIndex + 1) % rollingWindow.size
        }

        if (rollingCount <= 0) return
        val rollingAverageWatts = rollingSumWatts.toDouble() / rollingCount.toDouble()
        rollingAverageFourthSum += rollingAverageWatts * rollingAverageWatts * rollingAverageWatts * rollingAverageWatts
        rollingAverageSampleCount += 1L
    }

    companion object {
        private const val DEFAULT_ROLLING_WINDOW_SECONDS = 30
        private const val DEFAULT_MAX_HOLD_SECONDS = 3
    }
}

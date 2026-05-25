package com.example.ergometerapp.ble

import android.bluetooth.le.ScanSettings

/**
 * Central guard for low-latency BLE scan start pacing.
 *
 * Android may reject scan registration when scans are restarted too frequently.
 * This gate combines three constraints:
 * - short restart cooldown after a stop
 * - temporary backoff after platform throttle failures
 * - rolling-window cap for low-latency scan starts
 */
internal class LowLatencyScanStartGate(
    private val restartCooldownMs: Long = DEFAULT_RESTART_COOLDOWN_MS,
    private val throttleBackoffMs: Long = DEFAULT_THROTTLE_BACKOFF_MS,
    private val startWindowMs: Long = DEFAULT_START_WINDOW_MS,
    private val maxStartsPerWindow: Int = DEFAULT_MAX_STARTS_PER_WINDOW,
) {
    companion object {
        const val DEFAULT_RESTART_COOLDOWN_MS = 1200L
        const val DEFAULT_THROTTLE_BACKOFF_MS = 6000L
        const val DEFAULT_START_WINDOW_MS = 30_000L
        const val DEFAULT_MAX_STARTS_PER_WINDOW = 3
    }

    internal enum class Reason(val key: String) {
        RESTART_COOLDOWN("restart_cooldown"),
        THROTTLE_BACKOFF("throttle_backoff"),
        WINDOW_GUARD("window_guard"),
    }

    internal data class Delay(
        val delayMs: Long,
        val reason: Reason? = null,
    )

    private var globalLastStopElapsedMs: Long = 0L
    private var globalThrottleBackoffUntilElapsedMs: Long = 0L
    private val lowLatencyStartHistoryElapsedMs = ArrayDeque<Long>(maxStartsPerWindow)

    /**
     * Calculates how long the caller should delay before attempting a scan start.
     */
    fun computeDelay(nowElapsedMs: Long, scanMode: Int): Delay {
        if (scanMode != ScanSettings.SCAN_MODE_LOW_LATENCY) {
            return Delay(delayMs = 0L)
        }

        pruneLowLatencyStartHistory(nowElapsedMs)

        val earliestRestartAtMs = globalLastStopElapsedMs + restartCooldownMs
        val cooldownDelayMs = (earliestRestartAtMs - nowElapsedMs).coerceAtLeast(0L)

        val backoffDelayMs = (globalThrottleBackoffUntilElapsedMs - nowElapsedMs).coerceAtLeast(0L)

        val windowDelayMs = if (lowLatencyStartHistoryElapsedMs.size < maxStartsPerWindow) {
            0L
        } else {
            val oldestStartMs = lowLatencyStartHistoryElapsedMs.first()
            (oldestStartMs + startWindowMs - nowElapsedMs).coerceAtLeast(0L)
        }

        val delayMs = maxOf(cooldownDelayMs, backoffDelayMs, windowDelayMs)
        val reason = when (delayMs) {
            0L -> null
            windowDelayMs -> Reason.WINDOW_GUARD
            backoffDelayMs -> Reason.THROTTLE_BACKOFF
            else -> Reason.RESTART_COOLDOWN
        }
        return Delay(delayMs = delayMs, reason = reason)
    }

    /**
     * Marks the time when a scan was stopped or cancelled.
     */
    fun markStop(nowElapsedMs: Long) {
        globalLastStopElapsedMs = maxOf(globalLastStopElapsedMs, nowElapsedMs)
    }

    /**
     * Marks scanner-throttle failure and applies bounded global backoff.
     */
    fun markThrottleFailure(nowElapsedMs: Long) {
        val backoffUntil = nowElapsedMs + throttleBackoffMs
        globalThrottleBackoffUntilElapsedMs =
            maxOf(globalThrottleBackoffUntilElapsedMs, backoffUntil)
    }

    /**
     * Records a successful low-latency scan start in the rolling window.
     *
     * Returns count of starts currently tracked within the active window.
     */
    fun recordSuccessfulStart(nowElapsedMs: Long, scanMode: Int): Int {
        if (scanMode != ScanSettings.SCAN_MODE_LOW_LATENCY) {
            return lowLatencyStartHistoryElapsedMs.size
        }
        pruneLowLatencyStartHistory(nowElapsedMs)
        lowLatencyStartHistoryElapsedMs.addLast(nowElapsedMs)
        return lowLatencyStartHistoryElapsedMs.size
    }

    private fun pruneLowLatencyStartHistory(nowElapsedMs: Long) {
        while (lowLatencyStartHistoryElapsedMs.isNotEmpty()) {
            val oldest = lowLatencyStartHistoryElapsedMs.first()
            if (nowElapsedMs - oldest <= startWindowMs) return
            lowLatencyStartHistoryElapsedMs.removeFirst()
        }
    }
}

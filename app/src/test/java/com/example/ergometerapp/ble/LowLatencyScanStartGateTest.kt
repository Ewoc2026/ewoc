package com.example.ergometerapp.ble

import android.bluetooth.le.ScanSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LowLatencyScanStartGateTest {
    @Test
    fun nonLowLatencyModeDoesNotDelay() {
        val gate = LowLatencyScanStartGate()
        gate.markStop(nowElapsedMs = 1_000L)
        gate.markThrottleFailure(nowElapsedMs = 1_100L)

        val delay = gate.computeDelay(
            nowElapsedMs = 1_200L,
            scanMode = ScanSettings.SCAN_MODE_BALANCED,
        )

        assertEquals(0L, delay.delayMs)
        assertNull(delay.reason)
    }

    @Test
    fun restartCooldownDelaysLowLatencyStart() {
        val gate = LowLatencyScanStartGate(restartCooldownMs = 1_200L)
        gate.markStop(nowElapsedMs = 1_000L)

        val delay = gate.computeDelay(
            nowElapsedMs = 1_500L,
            scanMode = ScanSettings.SCAN_MODE_LOW_LATENCY,
        )

        assertEquals(700L, delay.delayMs)
        assertEquals(LowLatencyScanStartGate.Reason.RESTART_COOLDOWN, delay.reason)
    }

    @Test
    fun throttleBackoffDelaysLowLatencyStart() {
        val gate = LowLatencyScanStartGate(throttleBackoffMs = 6_000L)
        gate.markThrottleFailure(nowElapsedMs = 10_000L)

        val delay = gate.computeDelay(
            nowElapsedMs = 12_500L,
            scanMode = ScanSettings.SCAN_MODE_LOW_LATENCY,
        )

        assertEquals(3_500L, delay.delayMs)
        assertEquals(LowLatencyScanStartGate.Reason.THROTTLE_BACKOFF, delay.reason)
    }

    @Test
    fun rollingWindowBlocksFourthStartWithinWindow() {
        val gate = LowLatencyScanStartGate(
            startWindowMs = 30_000L,
            maxStartsPerWindow = 3,
        )
        gate.recordSuccessfulStart(nowElapsedMs = 1_000L, scanMode = ScanSettings.SCAN_MODE_LOW_LATENCY)
        gate.recordSuccessfulStart(nowElapsedMs = 2_000L, scanMode = ScanSettings.SCAN_MODE_LOW_LATENCY)
        gate.recordSuccessfulStart(nowElapsedMs = 3_000L, scanMode = ScanSettings.SCAN_MODE_LOW_LATENCY)

        val delay = gate.computeDelay(
            nowElapsedMs = 4_000L,
            scanMode = ScanSettings.SCAN_MODE_LOW_LATENCY,
        )

        assertEquals(27_000L, delay.delayMs)
        assertEquals(LowLatencyScanStartGate.Reason.WINDOW_GUARD, delay.reason)
    }

    @Test
    fun rollingWindowExpiresAndAllowsNewStart() {
        val gate = LowLatencyScanStartGate(
            startWindowMs = 30_000L,
            maxStartsPerWindow = 3,
        )
        gate.recordSuccessfulStart(nowElapsedMs = 1_000L, scanMode = ScanSettings.SCAN_MODE_LOW_LATENCY)
        gate.recordSuccessfulStart(nowElapsedMs = 2_000L, scanMode = ScanSettings.SCAN_MODE_LOW_LATENCY)
        gate.recordSuccessfulStart(nowElapsedMs = 3_000L, scanMode = ScanSettings.SCAN_MODE_LOW_LATENCY)

        val delay = gate.computeDelay(
            nowElapsedMs = 31_100L,
            scanMode = ScanSettings.SCAN_MODE_LOW_LATENCY,
        )

        assertEquals(0L, delay.delayMs)
        assertNull(delay.reason)
    }
}

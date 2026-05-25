package com.example.ergometerapp.ble

import android.os.Handler
import kotlin.math.min

/**
 * Coordinates bounded reconnect attempts for the HR BLE link.
 *
 * Invariants:
 * - reconnect attempts are scheduled only for an active requested MAC
 * - explicit close suppresses all pending/future reconnect attempts
 * - backoff resets after a successful connection
 */
internal class HrReconnectCoordinator(
    private val handler: Handler,
    private val onReconnectRequested: (String) -> Unit,
    private val onDisconnected: () -> Unit,
    private val onReconnectExhausted: () -> Unit = {},
    private val reconnectPolicy: HrReconnectPolicy = HrReconnectPolicy(),
) {
    private var requestedMac: String? = null
    private var explicitCloseRequested = false
    private var reconnectAttempt = 0
    private var reconnectRunnable: Runnable? = null
    private var reconnectExhausted = false

    fun onConnectRequested(mac: String) {
        requestedMac = mac
        explicitCloseRequested = false
        reconnectAttempt = 0
        reconnectExhausted = false
        cancelPendingReconnect()
    }

    fun onConnected() {
        reconnectAttempt = 0
        reconnectExhausted = false
        cancelPendingReconnect()
    }

    fun onGattDisconnected() {
        onDisconnected()
        scheduleReconnectIfEligible()
    }

    fun onConnectAttemptFailed() {
        scheduleReconnectIfEligible()
    }

    fun onPermissionDenied() {
        reconnectAttempt = 0
        reconnectExhausted = false
        cancelPendingReconnect()
    }

    fun onCloseRequested() {
        explicitCloseRequested = true
        requestedMac = null
        reconnectAttempt = 0
        reconnectExhausted = false
        cancelPendingReconnect()
    }

    private fun scheduleReconnectIfEligible() {
        if (explicitCloseRequested) return
        if (reconnectRunnable != null) return

        val mac = requestedMac ?: return
        if (reconnectAttempt >= reconnectPolicy.maxReconnectAttempts) {
            if (!reconnectExhausted) {
                reconnectExhausted = true
                onReconnectExhausted()
            }
            return
        }

        reconnectAttempt += 1
        val delayMs = reconnectDelayForAttempt(reconnectAttempt)
        reconnectRunnable = Runnable {
            reconnectRunnable = null
            if (explicitCloseRequested) return@Runnable
            onReconnectRequested(mac)
        }
        handler.postDelayed(reconnectRunnable!!, delayMs)
    }

    private fun cancelPendingReconnect() {
        reconnectRunnable?.let { handler.removeCallbacks(it) }
        reconnectRunnable = null
    }

    private fun reconnectDelayForAttempt(attempt: Int): Long {
        val safeAttempt = attempt.coerceAtLeast(1)
        val exponent = (safeAttempt - 1).coerceAtMost(62)
        val multiplier = 1L shl exponent
        val maxBaseBeforeOverflow = Long.MAX_VALUE / multiplier
        val candidate = if (reconnectPolicy.baseDelayMs > maxBaseBeforeOverflow) {
            Long.MAX_VALUE
        } else {
            reconnectPolicy.baseDelayMs * multiplier
        }
        return min(candidate, reconnectPolicy.maxDelayMs)
    }
}

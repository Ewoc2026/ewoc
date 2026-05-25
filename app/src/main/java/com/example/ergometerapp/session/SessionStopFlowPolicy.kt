package com.example.ergometerapp.session

import android.os.Handler
import com.example.ergometerapp.AppUiState
import com.example.ergometerapp.StopFlowState

/**
 * Owns stop-flow state transitions and timeout watchdog scheduling.
 *
 * Invariants:
 * - `STOPPING_AWAIT_ACK` is the only in-progress stop-flow state.
 * - Completion is one-shot: only the first completion path may transition to idle.
 * - Resetting stop flow always clears pending timeout callbacks to avoid stale
 *   timeout execution after a newer session start.
 */
internal class SessionStopFlowPolicy(
    private val uiState: AppUiState,
    private val mainThreadHandler: Handler,
    private val stopFlowTimeoutMs: Long,
    private val onStopFlowTimeout: () -> Unit,
) {
    private var stopFlowTimeoutRunnable: Runnable? = null

    /**
     * Returns true while stop flow is waiting for acknowledgement or equivalent completion.
     */
    fun isStopFlowInProgress(): Boolean {
        return uiState.stopFlowState.value == StopFlowState.STOPPING_AWAIT_ACK
    }

    /**
     * Enters explicit stop-flow state before FTMS teardown/release actions.
     */
    fun enterStoppingAwaitAck() {
        uiState.stopFlowState.value = StopFlowState.STOPPING_AWAIT_ACK
    }

    /**
     * Cancels pending timeout and clears stop flow to idle.
     */
    fun resetToIdle() {
        cancelStopFlowTimeout()
        uiState.stopFlowState.value = StopFlowState.IDLE
    }

    /**
     * Completes stop flow exactly once by transitioning `STOPPING_AWAIT_ACK -> IDLE`.
     */
    fun completeToIdleIfInProgress(): Boolean {
        if (!isStopFlowInProgress()) return false
        resetToIdle()
        return true
    }

    /**
     * Arms timeout while stop flow is waiting for acknowledgement/disconnect.
     */
    fun startStopFlowTimeout() {
        cancelStopFlowTimeout()
        stopFlowTimeoutRunnable = Runnable {
            stopFlowTimeoutRunnable = null
            if (!isStopFlowInProgress()) return@Runnable
            onStopFlowTimeout()
        }
        mainThreadHandler.postDelayed(stopFlowTimeoutRunnable!!, stopFlowTimeoutMs)
    }

    /**
     * Cancels any pending timeout callback while preserving current flow state.
     */
    fun cancelStopFlowTimeout() {
        stopFlowTimeoutRunnable?.let { mainThreadHandler.removeCallbacks(it) }
        stopFlowTimeoutRunnable = null
    }
}

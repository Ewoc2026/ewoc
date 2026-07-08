package io.github.ewoc2026.ewoc.session

import android.os.Handler
import io.github.ewoc2026.ewoc.AppScreen
import io.github.ewoc2026.ewoc.AppUiState

/**
 * Owns CONNECTING-state transitions and timeout behavior for session start.
 *
 * This flow is intentionally small and explicit: it only controls navigation
 * between MENU/CONNECTING/SESSION and delegates failure/side effects to the
 * caller through callbacks.
 */
internal class SessionConnectionFlow(
    private val uiState: AppUiState,
    private val mainThreadHandler: Handler,
    private val connectFlowTimeoutMs: Long,
    private val onConnectFlowTimeoutElapsed: () -> Unit,
    private val onMockSessionConnected: () -> Unit,
    private val onSessionControlGranted: () -> Unit,
) {
    private var connectFlowTimeoutRunnable: Runnable? = null
    private var mockConnectTransitionRunnable: Runnable? = null

    /**
     * Enters CONNECTING and arms watchdog timeout.
     */
    fun enterConnectingState() {
        uiState.screen.value = AppScreen.CONNECTING
        startConnectFlowTimeout()
    }

    /**
     * Re-arms connect-flow watchdog while staying in CONNECTING.
     *
     * Used when the user explicitly chooses to keep waiting or retry.
     */
    fun restartConnectFlowTimeout() {
        if (uiState.screen.value != AppScreen.CONNECTING) return
        startConnectFlowTimeout()
    }

    /**
     * Schedules mock transition to SESSION on the main handler.
     */
    fun scheduleMockConnectTransition() {
        cancelMockConnectTransition()
        mockConnectTransitionRunnable = Runnable {
            mockConnectTransitionRunnable = null
            transitionFromConnectingToSessionInMockMode()
        }
        mainThreadHandler.post(mockConnectTransitionRunnable!!)
    }

    /**
     * Clears pending mock transition callbacks.
     */
    fun cancelMockConnectTransition() {
        mockConnectTransitionRunnable?.let { mainThreadHandler.removeCallbacks(it) }
        mockConnectTransitionRunnable = null
    }

    /**
     * Clears pending connect-flow timeout callbacks.
     */
    fun cancelConnectFlowTimeout() {
        connectFlowTimeoutRunnable?.let { mainThreadHandler.removeCallbacks(it) }
        connectFlowTimeoutRunnable = null
    }

    /**
     * Handles CONNECTING -> SESSION after FTMS request-control success.
     */
    fun transitionFromConnectingToSessionAfterControlGranted() {
        if (uiState.screen.value != AppScreen.CONNECTING) return
        cancelConnectFlowTimeout()
        onSessionControlGranted()
    }

    /**
     * Handles CONNECTING -> SESSION transition for mock trainer mode.
     */
    fun transitionFromConnectingToSessionInMockMode() {
        if (uiState.screen.value != AppScreen.CONNECTING) return
        cancelConnectFlowTimeout()
        onMockSessionConnected()
    }

    fun close() {
        cancelConnectFlowTimeout()
        cancelMockConnectTransition()
    }

    private fun startConnectFlowTimeout() {
        cancelConnectFlowTimeout()
        connectFlowTimeoutRunnable = Runnable {
            connectFlowTimeoutRunnable = null
            if (uiState.screen.value != AppScreen.CONNECTING) return@Runnable
            onConnectFlowTimeoutElapsed()
        }
        mainThreadHandler.postDelayed(connectFlowTimeoutRunnable!!, connectFlowTimeoutMs)
    }
}

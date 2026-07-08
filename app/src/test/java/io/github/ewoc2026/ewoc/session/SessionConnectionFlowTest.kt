package io.github.ewoc2026.ewoc.session

import io.github.ewoc2026.ewoc.AppScreen
import io.github.ewoc2026.ewoc.AppUiState
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionConnectionFlowTest {

    @Test
    fun enterConnectingStateArmsTimeoutAndCallsFailureWhenDeadlinePasses() {
        val uiState = AppUiState()
        val handler = ManualHandler()
        var timeoutCalls = 0
        val flow = SessionConnectionFlow(
            uiState = uiState,
            mainThreadHandler = handler,
            connectFlowTimeoutMs = 15_000L,
            onConnectFlowTimeoutElapsed = { timeoutCalls += 1 },
            onMockSessionConnected = {},
            onSessionControlGranted = {},
        )

        flow.enterConnectingState()
        assertEquals(AppScreen.CONNECTING, uiState.screen.value)

        handler.advanceBy(14_999L)
        assertEquals(0, timeoutCalls)

        handler.advanceBy(1L)
        assertEquals(1, timeoutCalls)
    }

    @Test
    fun controlGrantedTransitionCancelsTimeout() {
        val uiState = AppUiState()
        val handler = ManualHandler()
        var timeoutCalls = 0
        var sessionStartCalls = 0
        val flow = SessionConnectionFlow(
            uiState = uiState,
            mainThreadHandler = handler,
            connectFlowTimeoutMs = 15_000L,
            onConnectFlowTimeoutElapsed = { timeoutCalls += 1 },
            onMockSessionConnected = {},
            onSessionControlGranted = {
                sessionStartCalls += 1
                uiState.screen.value = AppScreen.SESSION
            },
        )

        flow.enterConnectingState()
        flow.transitionFromConnectingToSessionAfterControlGranted()

        assertEquals(1, sessionStartCalls)
        assertEquals(AppScreen.SESSION, uiState.screen.value)

        handler.advanceBy(15_000L)
        assertEquals(0, timeoutCalls)
    }

    @Test
    fun mockTransitionOnlyRunsFromConnectingState() {
        val uiState = AppUiState().apply {
            screen.value = AppScreen.MENU
        }
        val handler = ManualHandler()
        var mockCalls = 0
        val flow = SessionConnectionFlow(
            uiState = uiState,
            mainThreadHandler = handler,
            connectFlowTimeoutMs = 15_000L,
            onConnectFlowTimeoutElapsed = {},
            onMockSessionConnected = {
                mockCalls += 1
                uiState.screen.value = AppScreen.SESSION
            },
            onSessionControlGranted = {},
        )

        flow.scheduleMockConnectTransition()
        handler.advanceBy(0L)

        assertEquals(0, mockCalls)
        assertEquals(AppScreen.MENU, uiState.screen.value)
    }

    @Test
    fun mockTransitionFromConnectingCancelsTimeoutAndStartsSession() {
        val uiState = AppUiState()
        val handler = ManualHandler()
        var timeoutCalls = 0
        var mockCalls = 0
        val flow = SessionConnectionFlow(
            uiState = uiState,
            mainThreadHandler = handler,
            connectFlowTimeoutMs = 15_000L,
            onConnectFlowTimeoutElapsed = { timeoutCalls += 1 },
            onMockSessionConnected = {
                mockCalls += 1
                uiState.screen.value = AppScreen.SESSION
            },
            onSessionControlGranted = {},
        )

        flow.enterConnectingState()
        flow.scheduleMockConnectTransition()
        handler.advanceBy(0L)

        assertEquals(1, mockCalls)
        assertEquals(AppScreen.SESSION, uiState.screen.value)

        handler.advanceBy(15_000L)
        assertEquals(0, timeoutCalls)
    }

    @Test
    fun repeatedConnectingEntryReplacesPriorTimeoutInsteadOfFiringTwice() {
        val uiState = AppUiState()
        val handler = ManualHandler()
        var timeoutCalls = 0
        val flow = SessionConnectionFlow(
            uiState = uiState,
            mainThreadHandler = handler,
            connectFlowTimeoutMs = 15_000L,
            onConnectFlowTimeoutElapsed = { timeoutCalls += 1 },
            onMockSessionConnected = {},
            onSessionControlGranted = {},
        )

        flow.enterConnectingState()
        handler.advanceBy(10_000L)
        flow.enterConnectingState()

        handler.advanceBy(14_999L)
        assertEquals(0, timeoutCalls)

        handler.advanceBy(1L)
        assertEquals(1, timeoutCalls)
    }

    @Test
    fun repeatedMockTransitionSchedulingRunsOnlyLatestPendingTransition() {
        val uiState = AppUiState()
        val handler = ManualHandler()
        var mockCalls = 0
        val flow = SessionConnectionFlow(
            uiState = uiState,
            mainThreadHandler = handler,
            connectFlowTimeoutMs = 15_000L,
            onConnectFlowTimeoutElapsed = {},
            onMockSessionConnected = {
                mockCalls += 1
                uiState.screen.value = AppScreen.SESSION
            },
            onSessionControlGranted = {},
        )

        flow.enterConnectingState()
        flow.scheduleMockConnectTransition()
        flow.scheduleMockConnectTransition()
        handler.advanceBy(0L)

        assertEquals(1, mockCalls)
        assertEquals(AppScreen.SESSION, uiState.screen.value)
    }

    @Test
    fun closeCancelsPendingTimeoutAndMockTransition() {
        val uiState = AppUiState()
        val handler = ManualHandler()
        var timeoutCalls = 0
        var mockCalls = 0
        val flow = SessionConnectionFlow(
            uiState = uiState,
            mainThreadHandler = handler,
            connectFlowTimeoutMs = 15_000L,
            onConnectFlowTimeoutElapsed = { timeoutCalls += 1 },
            onMockSessionConnected = { mockCalls += 1 },
            onSessionControlGranted = {},
        )

        flow.enterConnectingState()
        flow.scheduleMockConnectTransition()
        flow.close()

        handler.advanceBy(20_000L)
        assertEquals(0, timeoutCalls)
        assertEquals(0, mockCalls)
        assertEquals(AppScreen.CONNECTING, uiState.screen.value)
    }

    @Test
    fun restartTimeoutReArmsWatchdogOnlyWhileConnecting() {
        val uiState = AppUiState().apply {
            screen.value = AppScreen.MENU
        }
        val handler = ManualHandler()
        var timeoutCalls = 0
        val flow = SessionConnectionFlow(
            uiState = uiState,
            mainThreadHandler = handler,
            connectFlowTimeoutMs = 15_000L,
            onConnectFlowTimeoutElapsed = { timeoutCalls += 1 },
            onMockSessionConnected = {},
            onSessionControlGranted = {},
        )

        flow.restartConnectFlowTimeout()
        handler.advanceBy(20_000L)
        assertEquals(0, timeoutCalls)

        flow.enterConnectingState()
        handler.advanceBy(15_000L)
        assertEquals(1, timeoutCalls)

        flow.restartConnectFlowTimeout()
        handler.advanceBy(15_000L)
        assertEquals(2, timeoutCalls)
    }

    private class ManualHandler : android.os.Handler(android.os.Looper.getMainLooper()) {
        private data class ScheduledRunnable(
            val runnable: Runnable,
            val runAtMs: Long,
            val order: Long,
        )

        private val queue = mutableListOf<ScheduledRunnable>()
        private var nextOrder = 0L
        private var nowMs = 0L

        override fun post(runnable: Runnable): Boolean {
            queue += ScheduledRunnable(
                runnable = runnable,
                runAtMs = nowMs,
                order = nextOrder++,
            )
            return true
        }

        override fun postDelayed(runnable: Runnable, delayMillis: Long): Boolean {
            queue += ScheduledRunnable(
                runnable = runnable,
                runAtMs = nowMs + delayMillis.coerceAtLeast(0L),
                order = nextOrder++,
            )
            return true
        }

        override fun removeCallbacks(runnable: Runnable) {
            queue.removeAll { it.runnable === runnable }
        }

        fun advanceBy(deltaMs: Long) {
            require(deltaMs >= 0L) { "Delta must be non-negative." }
            val targetMs = nowMs + deltaMs
            while (true) {
                val next = queue
                    .filter { it.runAtMs <= targetMs }
                    .minWithOrNull(compareBy<ScheduledRunnable> { it.runAtMs }.thenBy { it.order })
                    ?: break
                queue.remove(next)
                nowMs = next.runAtMs
                next.runnable.run()
            }
            nowMs = targetMs
        }
    }
}

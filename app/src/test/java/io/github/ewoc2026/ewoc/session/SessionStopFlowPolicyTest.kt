package io.github.ewoc2026.ewoc.session

import io.github.ewoc2026.ewoc.AppUiState
import io.github.ewoc2026.ewoc.StopFlowState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionStopFlowPolicyTest {

    @Test
    fun completeToIdleIfInProgressReturnsFalseOutsideStopFlow() {
        val uiState = AppUiState()
        val handler = ManualHandler()
        var timeoutCalls = 0
        val policy = SessionStopFlowPolicy(
            uiState = uiState,
            mainThreadHandler = handler,
            stopFlowTimeoutMs = 4_000L,
            onStopFlowTimeout = { timeoutCalls += 1 },
        )

        assertFalse(policy.completeToIdleIfInProgress())
        assertEquals(StopFlowState.IDLE, uiState.stopFlowState.value)
        assertEquals(0, timeoutCalls)
    }

    @Test
    fun startTimeoutTriggersCallbackOnlyAfterDeadlineWhenFlowIsActive() {
        val uiState = AppUiState()
        val handler = ManualHandler()
        var timeoutCalls = 0
        val policy = SessionStopFlowPolicy(
            uiState = uiState,
            mainThreadHandler = handler,
            stopFlowTimeoutMs = 4_000L,
            onStopFlowTimeout = { timeoutCalls += 1 },
        )

        policy.enterStoppingAwaitAck()
        policy.startStopFlowTimeout()

        handler.advanceBy(3_999L)
        assertEquals(0, timeoutCalls)
        assertTrue(policy.isStopFlowInProgress())

        handler.advanceBy(1L)
        assertEquals(1, timeoutCalls)
        assertTrue(policy.isStopFlowInProgress())
    }

    @Test
    fun completionCancelsPendingTimeoutAndResetsStateToIdle() {
        val uiState = AppUiState()
        val handler = ManualHandler()
        var timeoutCalls = 0
        val policy = SessionStopFlowPolicy(
            uiState = uiState,
            mainThreadHandler = handler,
            stopFlowTimeoutMs = 4_000L,
            onStopFlowTimeout = { timeoutCalls += 1 },
        )

        policy.enterStoppingAwaitAck()
        policy.startStopFlowTimeout()

        assertTrue(policy.completeToIdleIfInProgress())
        assertEquals(StopFlowState.IDLE, uiState.stopFlowState.value)
        assertFalse(policy.isStopFlowInProgress())

        handler.advanceBy(4_000L)
        assertEquals(0, timeoutCalls)
    }

    @Test
    fun resetToIdleCancelsTimeoutWithoutTriggeringCallback() {
        val uiState = AppUiState()
        val handler = ManualHandler()
        var timeoutCalls = 0
        val policy = SessionStopFlowPolicy(
            uiState = uiState,
            mainThreadHandler = handler,
            stopFlowTimeoutMs = 4_000L,
            onStopFlowTimeout = { timeoutCalls += 1 },
        )

        policy.enterStoppingAwaitAck()
        policy.startStopFlowTimeout()
        policy.resetToIdle()

        assertEquals(StopFlowState.IDLE, uiState.stopFlowState.value)
        assertFalse(policy.isStopFlowInProgress())

        handler.advanceBy(4_000L)
        assertEquals(0, timeoutCalls)
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

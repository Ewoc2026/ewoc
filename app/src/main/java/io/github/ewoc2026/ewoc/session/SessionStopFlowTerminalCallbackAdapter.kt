package io.github.ewoc2026.ewoc.session

/**
 * Routes stop-flow terminal callbacks through a single completion seam.
 *
 * Why this exists:
 * - Stop flow can finish from multiple entry points (ack/timeout/disconnect/mock).
 * - Some terminal paths must close BLE transport only when completion actually wins.
 *
 * Invariants:
 * - Completion remains one-shot and delegated to `completeStopFlowToSummary`.
 * - BLE close is triggered only for ack/timeout terminal paths when completion succeeds.
 * - Disconnect and mock-immediate paths preserve transport handling ownership in their callers.
 */
internal class SessionStopFlowTerminalCallbackAdapter(
    private val completeStopFlowToSummary: (reason: String) -> Boolean,
    private val closeBleTransport: () -> Unit,
    private val shouldCloseBleTransport: () -> Boolean,
    private val onBleCloseSkipped: (reason: String) -> Unit = {},
) {
    /**
     * Handles STOP acknowledgement terminal callback.
     */
    fun onStopAcknowledged(): Boolean {
        return completeWithBleClose(reason = STOP_ACK_REASON)
    }

    /**
     * Handles stop-flow timeout terminal callback.
     */
    fun onStopFlowTimeout(): Boolean {
        return completeWithBleClose(reason = STOP_TIMEOUT_REASON)
    }

    /**
     * Handles disconnect terminal callback while stop flow is active.
     */
    fun onBleDisconnectedDuringStopFlow(): Boolean {
        return completeStopFlowToSummary(DISCONNECT_DURING_STOP_FLOW_REASON)
    }

    /**
     * Handles mock mode terminal callback where stop flow completes immediately.
     */
    fun onMockStopFlowImmediate(): Boolean {
        return completeStopFlowToSummary(MOCK_STOP_FLOW_IMMEDIATE_REASON)
    }

    /**
     * Provides deterministic test-only STOP acknowledgement completion.
     */
    fun onStopAcknowledgedForTest(): Boolean {
        return completeWithBleClose(reason = STOP_ACK_FOR_TEST_REASON)
    }

    private fun completeWithBleClose(reason: String): Boolean {
        if (!completeStopFlowToSummary(reason)) return false
        if (shouldCloseBleTransport()) {
            closeBleTransport()
        } else {
            onBleCloseSkipped(reason)
        }
        return true
    }

    private companion object {
        const val STOP_ACK_REASON = "onStopAcknowledged"
        const val STOP_TIMEOUT_REASON = "stopFlowTimeout"
        const val DISCONNECT_DURING_STOP_FLOW_REASON = "bleOnDisconnectedDuringStopFlow"
        const val MOCK_STOP_FLOW_IMMEDIATE_REASON = "mockStopFlowImmediate"
        const val STOP_ACK_FOR_TEST_REASON = "onStopAcknowledgedForTest"
    }
}

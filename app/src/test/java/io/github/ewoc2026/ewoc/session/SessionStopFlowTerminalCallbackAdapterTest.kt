package io.github.ewoc2026.ewoc.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionStopFlowTerminalCallbackAdapterTest {

    @Test
    fun stopAcknowledgedCompletesAndClosesBleTransport() {
        val completionReasons = mutableListOf<String>()
        var closeBleTransportCalls = 0
        val adapter = SessionStopFlowTerminalCallbackAdapter(
            completeStopFlowToSummary = { reason ->
                completionReasons += reason
                true
            },
            closeBleTransport = { closeBleTransportCalls += 1 },
            shouldCloseBleTransport = { true },
        )

        val completed = adapter.onStopAcknowledged()

        assertTrue(completed)
        assertEquals(listOf("onStopAcknowledged"), completionReasons)
        assertEquals(1, closeBleTransportCalls)
    }

    @Test
    fun stopTimeoutDoesNotCloseBleTransportWhenCompletionIsRejected() {
        val completionReasons = mutableListOf<String>()
        var closeBleTransportCalls = 0
        val adapter = SessionStopFlowTerminalCallbackAdapter(
            completeStopFlowToSummary = { reason ->
                completionReasons += reason
                false
            },
            closeBleTransport = { closeBleTransportCalls += 1 },
            shouldCloseBleTransport = { true },
        )

        val completed = adapter.onStopFlowTimeout()

        assertFalse(completed)
        assertEquals(listOf("stopFlowTimeout"), completionReasons)
        assertEquals(0, closeBleTransportCalls)
    }

    @Test
    fun bleDisconnectedDuringStopFlowCompletesWithoutClosingBleTransport() {
        val completionReasons = mutableListOf<String>()
        var closeBleTransportCalls = 0
        val adapter = SessionStopFlowTerminalCallbackAdapter(
            completeStopFlowToSummary = { reason ->
                completionReasons += reason
                true
            },
            closeBleTransport = { closeBleTransportCalls += 1 },
            shouldCloseBleTransport = { true },
        )

        val completed = adapter.onBleDisconnectedDuringStopFlow()

        assertTrue(completed)
        assertEquals(listOf("bleOnDisconnectedDuringStopFlow"), completionReasons)
        assertEquals(0, closeBleTransportCalls)
    }

    @Test
    fun stopAcknowledgedForTestUsesDedicatedReasonAndClosesBleTransport() {
        val completionReasons = mutableListOf<String>()
        var closeBleTransportCalls = 0
        val adapter = SessionStopFlowTerminalCallbackAdapter(
            completeStopFlowToSummary = { reason ->
                completionReasons += reason
                true
            },
            closeBleTransport = { closeBleTransportCalls += 1 },
            shouldCloseBleTransport = { true },
        )

        val completed = adapter.onStopAcknowledgedForTest()

        assertTrue(completed)
        assertEquals(listOf("onStopAcknowledgedForTest"), completionReasons)
        assertEquals(1, closeBleTransportCalls)
    }

    @Test
    fun stopAcknowledgedCanSkipBleCloseWhenPolicyKeepsConnection() {
        val completionReasons = mutableListOf<String>()
        var closeBleTransportCalls = 0
        val skippedReasons = mutableListOf<String>()
        val adapter = SessionStopFlowTerminalCallbackAdapter(
            completeStopFlowToSummary = { reason ->
                completionReasons += reason
                true
            },
            closeBleTransport = { closeBleTransportCalls += 1 },
            shouldCloseBleTransport = { false },
            onBleCloseSkipped = { reason -> skippedReasons += reason },
        )

        val completed = adapter.onStopAcknowledged()

        assertTrue(completed)
        assertEquals(listOf("onStopAcknowledged"), completionReasons)
        assertEquals(0, closeBleTransportCalls)
        assertEquals(listOf("onStopAcknowledged"), skippedReasons)
    }
}

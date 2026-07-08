package io.github.ewoc2026.ewoc.compat

import org.junit.Assert.assertEquals
import org.junit.Test

class FtmsCompatibilityDeviceGatewayTest {

    @Test
    fun resolveCommandResult_returnsWriteNotStartedWhenCommandDidNotStart() {
        val result = resolveCompatibilityGatewayCommandResult(
            commandStarted = false,
            terminalOutcome = null,
            expectedOpcode = 0x05,
        )

        assertEquals(CompatibilityCommandStatus.WRITE_NOT_STARTED, result.status)
        assertEquals(0x05, result.controlPointTrace?.expectedOpcode)
        assertEquals("write_not_started", result.controlPointTrace?.stage)
    }

    @Test
    fun resolveCommandResult_mapsResponseSuccessToSuccess() {
        val result = resolveCompatibilityGatewayCommandResult(
            commandStarted = true,
            terminalOutcome = CompatibilityGatewayTerminalOutcome.Response(resultCode = 0x01),
            expectedOpcode = 0x00,
        )

        assertEquals(CompatibilityCommandStatus.SUCCESS, result.status)
        assertEquals("response", result.controlPointTrace?.stage)
        assertEquals(0x01, result.controlPointTrace?.resultCode)
    }

    @Test
    fun resolveCommandResult_mapsResponseFailureToRejected() {
        val result = resolveCompatibilityGatewayCommandResult(
            commandStarted = true,
            terminalOutcome = CompatibilityGatewayTerminalOutcome.Response(resultCode = 0x05),
        )

        assertEquals(CompatibilityCommandStatus.REJECTED, result.status)
    }

    @Test
    fun resolveCommandResult_mapsTimeoutToTimeout() {
        val result = resolveCompatibilityGatewayCommandResult(
            commandStarted = true,
            terminalOutcome = CompatibilityGatewayTerminalOutcome.Timeout,
            expectedOpcode = 0x08,
        )

        assertEquals(CompatibilityCommandStatus.TIMEOUT, result.status)
        assertEquals(0x08, result.controlPointTrace?.expectedOpcode)
        assertEquals("timeout", result.controlPointTrace?.stage)
    }

    @Test
    fun resolveCommandResult_mapsDisconnectToDisconnected() {
        val result = resolveCompatibilityGatewayCommandResult(
            commandStarted = true,
            terminalOutcome = CompatibilityGatewayTerminalOutcome.Disconnected,
        )

        assertEquals(CompatibilityCommandStatus.DISCONNECTED, result.status)
    }
}

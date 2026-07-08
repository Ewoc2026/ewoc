package io.github.ewoc2026.ewoc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DebugSessionValidationCommandParserTest {

    @Test
    fun parse_returnsNullWhenActionDoesNotMatch() {
        val parsed = DebugSessionValidationCommandParser.parse(
            action = "io.github.ewoc2026.ewoc.action.UNRELATED",
            scenarioExtra = "request_control_timeout",
            resultCodeExtra = null,
        )

        assertNull(parsed)
    }

    @Test
    fun parse_parsesRequestControlRejectedWithCustomResultCode() {
        val parsed = DebugSessionValidationCommandParser.parse(
            action = DebugSessionValidationCommandParser.ACTION_DEBUG_VALIDATE_REQUEST_CONTROL,
            scenarioExtra = "REQUEST_CONTROL_REJECTED",
            resultCodeExtra = 6,
        )

        requireNotNull(parsed)
        assertEquals(DebugSessionValidationCommand.Scenario.REQUEST_CONTROL_REJECTED, parsed.scenario)
        assertEquals(6, parsed.rejectedResultCode)
    }

    @Test
    fun parse_parsesRequestControlTimeout() {
        val parsed = DebugSessionValidationCommandParser.parse(
            action = DebugSessionValidationCommandParser.ACTION_DEBUG_VALIDATE_REQUEST_CONTROL,
            scenarioExtra = "request_control_timeout",
            resultCodeExtra = null,
        )

        requireNotNull(parsed)
        assertEquals(DebugSessionValidationCommand.Scenario.REQUEST_CONTROL_TIMEOUT, parsed.scenario)
    }

    @Test
    fun parse_returnsNullWhenScenarioMissing() {
        val parsed = DebugSessionValidationCommandParser.parse(
            action = DebugSessionValidationCommandParser.ACTION_DEBUG_VALIDATE_REQUEST_CONTROL,
            scenarioExtra = null,
            resultCodeExtra = null,
        )

        assertNull(parsed)
    }

    @Test
    fun parse_returnsNullWhenScenarioUnknown() {
        val parsed = DebugSessionValidationCommandParser.parse(
            action = DebugSessionValidationCommandParser.ACTION_DEBUG_VALIDATE_REQUEST_CONTROL,
            scenarioExtra = "unknown_scenario",
            resultCodeExtra = null,
        )

        assertNull(parsed)
    }
}

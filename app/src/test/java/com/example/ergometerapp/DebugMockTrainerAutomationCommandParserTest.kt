package com.example.ergometerapp

import com.example.ergometerapp.session.MockTrainerDebugScenario
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DebugMockTrainerAutomationCommandParserTest {

    @Test
    fun parse_returnsNullWhenActionDoesNotMatch() {
        val parsed = DebugMockTrainerAutomationCommandParser.parse(
            action = "io.github.ewoc2026.ewoc.action.UNRELATED",
            scenarioExtra = MockTrainerDebugScenario.WAITING_START_AND_PAUSE_CAPTURE.wireName,
        )

        assertNull(parsed)
    }

    @Test
    fun parse_parsesScenarioIgnoringCase() {
        val parsed = DebugMockTrainerAutomationCommandParser.parse(
            action = DebugMockTrainerAutomationCommandParser.ACTION_DEBUG_ARM_MOCK_TRAINER_SCENARIO,
            scenarioExtra = "WAITING_START_AND_PAUSE_CAPTURE",
        )

        requireNotNull(parsed)
        assertEquals(MockTrainerDebugScenario.WAITING_START_AND_PAUSE_CAPTURE, parsed.scenario)
    }

    @Test
    fun parse_returnsNullWhenScenarioMissing() {
        val parsed = DebugMockTrainerAutomationCommandParser.parse(
            action = DebugMockTrainerAutomationCommandParser.ACTION_DEBUG_ARM_MOCK_TRAINER_SCENARIO,
            scenarioExtra = null,
        )

        assertNull(parsed)
    }

    @Test
    fun parse_returnsNullWhenScenarioUnknown() {
        val parsed = DebugMockTrainerAutomationCommandParser.parse(
            action = DebugMockTrainerAutomationCommandParser.ACTION_DEBUG_ARM_MOCK_TRAINER_SCENARIO,
            scenarioExtra = "unknown_scenario",
        )

        assertNull(parsed)
    }
}

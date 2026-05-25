package com.example.ergometerapp

import android.content.Intent
import com.example.ergometerapp.session.MockTrainerDebugScenario
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MainActivityDebugIntentCoordinatorTest {
    @Test
    fun handle_ignoresAllCommandsWhenDebugBuildDisabled() {
        val events = mutableListOf<String>()
        val coordinator = MainActivityDebugIntentCoordinator(
            debugBuildEnabled = false,
            resolveIntentAction = { "ignored" },
            parseSessionValidationCommand = {
                events += "parseSession"
                null
            },
            executeSessionValidationCommand = {
                events += "executeSession"
                "session"
            },
            parseAutomationCommand = {
                events += "parseAutomation"
                null
            },
            executeAutomationCommand = {
                events += "executeAutomation"
                "automation"
            },
            parseMockTrainerAutomationCommand = {
                events += "parseMock"
                null
            },
            executeMockTrainerAutomationCommand = {
                events += "executeMock"
                "mock"
            },
            logValidationStatus = { status ->
                events += "log:$status"
            },
        )

        coordinator.handle(Intent("ignored"))

        assertTrue(events.isEmpty())
    }

    @Test
    fun handle_executesAndLogsBothSupportedCommandFamiliesIndependently() {
        val events = mutableListOf<String>()
        val validationCommand = DebugSessionValidationCommand(
            scenario = DebugSessionValidationCommand.Scenario.REQUEST_CONTROL_TIMEOUT,
            rejectedResultCode = 5,
        )
        val automationCommand = DebugAutomationCommand(
            type = DebugAutomationCommand.Type.DUMP_STATUS,
            targetWatts = null,
        )
        val mockCommand = DebugMockTrainerAutomationCommand(
            scenario = MockTrainerDebugScenario.WAITING_START_AND_PAUSE_CAPTURE,
        )
        val coordinator = MainActivityDebugIntentCoordinator(
            debugBuildEnabled = true,
            resolveIntentAction = { "debug" },
            parseSessionValidationCommand = {
                events += "parseSession"
                validationCommand
            },
            executeSessionValidationCommand = { command ->
                events += "executeSession:${command.scenario}"
                "session-status"
            },
            parseAutomationCommand = {
                events += "parseAutomation"
                automationCommand
            },
            executeAutomationCommand = { command ->
                events += "executeAutomation:${command.type}"
                "automation-status"
            },
            parseMockTrainerAutomationCommand = {
                events += "parseMock"
                mockCommand
            },
            executeMockTrainerAutomationCommand = { command ->
                events += "executeMock:${command.scenario.wireName}"
                "mock-status"
            },
            logValidationStatus = { status ->
                events += "log:$status"
            },
        )

        coordinator.handle(Intent("debug"))

        assertEquals(
            listOf(
                "log:Debug intent received: action=debug",
                "parseSession",
                "executeSession:REQUEST_CONTROL_TIMEOUT",
                "log:Debug session validation executed: session-status",
                "parseAutomation",
                "executeAutomation:DUMP_STATUS",
                "log:Debug automation executed: automation-status",
                "parseMock",
                "executeMock:waiting_start_and_pause_capture",
                "log:Debug mock automation executed: mock-status",
            ),
            events,
        )
    }

    @Test
    fun handle_skipsExecutionAndLoggingWhenParsersReturnNull() {
        val events = mutableListOf<String>()
        val coordinator = MainActivityDebugIntentCoordinator(
            debugBuildEnabled = true,
            resolveIntentAction = { "debug" },
            parseSessionValidationCommand = {
                events += "parseSession"
                null
            },
            executeSessionValidationCommand = {
                events += "executeSession"
                "session-status"
            },
            parseAutomationCommand = {
                events += "parseAutomation"
                null
            },
            executeAutomationCommand = {
                events += "executeAutomation"
                "automation-status"
            },
            parseMockTrainerAutomationCommand = {
                events += "parseMock"
                null
            },
            executeMockTrainerAutomationCommand = {
                events += "executeMock"
                "mock-status"
            },
            logValidationStatus = { status ->
                events += "log:$status"
            },
        )

        coordinator.handle(Intent("debug"))

        assertEquals(
            listOf(
                "log:Debug intent received: action=debug",
                "parseSession",
                "parseAutomation",
                "parseMock",
                "log:Debug intent finished without matching executable command",
            ),
            events,
        )
    }

    @Test
    fun handle_logsIgnoredMessageForIncompleteAutomationCommand() {
        val events = mutableListOf<String>()
        val coordinator = MainActivityDebugIntentCoordinator(
            debugBuildEnabled = true,
            resolveIntentAction = { DebugAutomationCommandParser.ACTION_DEBUG_AUTOMATION },
            parseSessionValidationCommand = {
                events += "parseSession"
                null
            },
            executeSessionValidationCommand = { "session-status" },
            parseAutomationCommand = {
                events += "parseAutomation"
                null
            },
            executeAutomationCommand = { "automation-status" },
            parseMockTrainerAutomationCommand = {
                events += "parseMock"
                null
            },
            executeMockTrainerAutomationCommand = { "mock-status" },
            logValidationStatus = { status ->
                events += "log:$status"
            },
        )

        coordinator.handle(Intent(DebugAutomationCommandParser.ACTION_DEBUG_AUTOMATION))

        assertEquals(
            listOf(
                "log:Debug intent received: action=io.github.ewoc2026.ewoc.action.DEBUG_AUTOMATION",
                "parseSession",
                "parseAutomation",
                "log:Debug automation ignored: invalid or incomplete extras",
                "parseMock",
                "log:Debug intent finished without matching executable command",
            ),
            events,
        )
    }
}

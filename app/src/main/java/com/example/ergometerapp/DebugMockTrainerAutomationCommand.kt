package com.example.ergometerapp

import android.content.Intent
import com.example.ergometerapp.session.MockTrainerDebugScenario

/**
 * Parsed debug-only command used to arm one-shot mock-trainer telemetry scenarios.
 */
internal data class DebugMockTrainerAutomationCommand(
    val scenario: MockTrainerDebugScenario,
)

/**
 * Converts adb activity intents into typed mock-trainer automation commands.
 *
 * Protocol:
 * - action must be [ACTION_DEBUG_ARM_MOCK_TRAINER_SCENARIO].
 * - [EXTRA_SCENARIO] chooses the telemetry script armed for the next mock session.
 */
internal object DebugMockTrainerAutomationCommandParser {
    internal const val ACTION_DEBUG_ARM_MOCK_TRAINER_SCENARIO =
        "io.github.ewoc2026.ewoc.action.DEBUG_ARM_MOCK_TRAINER_SCENARIO"
    internal const val EXTRA_SCENARIO = "scenario"

    fun parse(
        action: String?,
        scenarioExtra: String?,
    ): DebugMockTrainerAutomationCommand? {
        if (action != ACTION_DEBUG_ARM_MOCK_TRAINER_SCENARIO) {
            return null
        }
        val scenario = MockTrainerDebugScenario.fromWireName(scenarioExtra) ?: return null
        return DebugMockTrainerAutomationCommand(scenario = scenario)
    }

    fun fromIntent(intent: Intent?): DebugMockTrainerAutomationCommand? {
        return parse(
            action = intent?.action,
            scenarioExtra = intent?.getStringExtra(EXTRA_SCENARIO),
        )
    }
}

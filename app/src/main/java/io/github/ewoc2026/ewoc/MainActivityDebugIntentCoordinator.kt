package io.github.ewoc2026.ewoc

import android.content.Intent

/**
 * Owns MainActivity debug-intent handling so lifecycle hooks stay focused on dispatch timing.
 *
 * Invariants:
 * - Non-debug builds never execute adb automation commands even if matching actions arrive.
 * - Each supported debug intent is parsed and executed independently so one command family cannot
 *   block the other from handling the same Activity launch.
 * - Only successfully parsed commands emit validation logs, which keeps adb-driven assertions
 *   aligned with actual command execution.
 */
internal class MainActivityDebugIntentCoordinator(
    private val debugBuildEnabled: Boolean,
    private val resolveIntentAction: (Intent?) -> String? = { intent -> intent?.action },
    private val parseSessionValidationCommand: (Intent?) -> DebugSessionValidationCommand? =
        DebugSessionValidationCommandParser::fromIntent,
    private val executeSessionValidationCommand: (DebugSessionValidationCommand) -> String,
    private val parseAutomationCommand: (Intent?) -> DebugAutomationCommand? =
        DebugAutomationCommandParser::fromIntent,
    private val executeAutomationCommand: (DebugAutomationCommand) -> String,
    private val parseMockTrainerAutomationCommand: (Intent?) -> DebugMockTrainerAutomationCommand? =
        DebugMockTrainerAutomationCommandParser::fromIntent,
    private val executeMockTrainerAutomationCommand: (DebugMockTrainerAutomationCommand) -> String,
    private val logValidationStatus: (String) -> Unit,
) {
    fun handle(intent: Intent?) {
        if (!debugBuildEnabled) return

        val action = resolveIntentAction(intent)
        logValidationStatus("Debug intent received: action=${action ?: "none"}")

        var handled = false

        parseSessionValidationCommand(intent)
            ?.let { command ->
                handled = true
                val status = executeSessionValidationCommand(command)
                logValidationStatus("Debug session validation executed: $status")
            }
            ?: run {
                if (action == DebugSessionValidationCommandParser.ACTION_DEBUG_VALIDATE_REQUEST_CONTROL) {
                    logValidationStatus("Debug session validation ignored: invalid or incomplete extras")
                }
            }

        parseAutomationCommand(intent)
            ?.let { command ->
                handled = true
                val status = executeAutomationCommand(command)
                logValidationStatus("Debug automation executed: $status")
            }
            ?: run {
                if (action == DebugAutomationCommandParser.ACTION_DEBUG_AUTOMATION) {
                    logValidationStatus("Debug automation ignored: invalid or incomplete extras")
                }
            }

        parseMockTrainerAutomationCommand(intent)
            ?.let { command ->
                handled = true
                val status = executeMockTrainerAutomationCommand(command)
                logValidationStatus("Debug mock automation executed: $status")
            }
            ?: run {
                if (action == DebugMockTrainerAutomationCommandParser.ACTION_DEBUG_ARM_MOCK_TRAINER_SCENARIO) {
                    logValidationStatus("Debug mock automation ignored: invalid or incomplete extras")
                }
            }

        if (!handled) {
            logValidationStatus("Debug intent finished without matching executable command")
        }
    }
}

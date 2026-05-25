package com.example.ergometerapp

import android.content.Intent
import java.util.Locale

/**
 * Parsed debug-only command used to trigger deterministic request-control recovery paths.
 */
internal data class DebugSessionValidationCommand(
    val scenario: Scenario,
    val rejectedResultCode: Int,
) {
    internal enum class Scenario {
        REQUEST_CONTROL_REJECTED,
        REQUEST_CONTROL_TIMEOUT,
    }
}

/**
 * Converts adb activity intents into typed debug validation commands.
 *
 * Protocol:
 * - action must be [ACTION_DEBUG_VALIDATE_REQUEST_CONTROL].
 * - [EXTRA_SCENARIO] chooses the failure path to trigger.
 * - [EXTRA_RESULT_CODE] applies only to the `request_control_rejected` scenario.
 */
internal object DebugSessionValidationCommandParser {
    internal const val ACTION_DEBUG_VALIDATE_REQUEST_CONTROL =
        "io.github.ewoc2026.ewoc.action.DEBUG_VALIDATE_REQUEST_CONTROL"
    internal const val EXTRA_SCENARIO = "scenario"
    internal const val EXTRA_RESULT_CODE = "result_code"

    private const val DEFAULT_REJECTED_RESULT_CODE = 5
    private const val SCENARIO_REQUEST_CONTROL_REJECTED = "request_control_rejected"
    private const val SCENARIO_REQUEST_CONTROL_TIMEOUT = "request_control_timeout"

    fun parse(
        action: String?,
        scenarioExtra: String?,
        resultCodeExtra: Int?,
    ): DebugSessionValidationCommand? {
        if (action != ACTION_DEBUG_VALIDATE_REQUEST_CONTROL) {
            return null
        }
        val rawScenario = scenarioExtra
            ?.trim()
            ?.lowercase(Locale.US)
            ?: return null
        val scenario = when (rawScenario) {
            SCENARIO_REQUEST_CONTROL_REJECTED -> DebugSessionValidationCommand.Scenario.REQUEST_CONTROL_REJECTED
            SCENARIO_REQUEST_CONTROL_TIMEOUT -> DebugSessionValidationCommand.Scenario.REQUEST_CONTROL_TIMEOUT
            else -> return null
        }
        return DebugSessionValidationCommand(
            scenario = scenario,
            rejectedResultCode = resultCodeExtra ?: DEFAULT_REJECTED_RESULT_CODE,
        )
    }

    fun fromIntent(intent: Intent?): DebugSessionValidationCommand? {
        return parse(
            action = intent?.action,
            scenarioExtra = intent?.getStringExtra(EXTRA_SCENARIO),
            resultCodeExtra = intent?.extras?.let { bundle ->
                if (bundle.containsKey(EXTRA_RESULT_CODE)) {
                    bundle.getInt(EXTRA_RESULT_CODE)
                } else {
                    null
                }
            },
        )
    }
}

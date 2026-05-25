package com.example.ergometerapp

import android.content.Intent
import com.example.ergometerapp.ui.MenuSetupStep
import java.util.Locale

/**
 * Parsed debug-only automation command for bypassing brittle adb tap sequences.
 *
 * The command surface stays intentionally small and state-oriented so manual
 * validation can jump to a known setup state without introducing release-only
 * routing paths or unsafe mid-session mutations.
 */
internal data class DebugAutomationCommand(
    val type: Type,
    val menuStep: MenuSetupStep? = null,
    val workoutFileName: String? = null,
    val workoutFilePath: String? = null,
    val enabled: Boolean? = null,
    val mockScenario: com.example.ergometerapp.session.MockTrainerDebugScenario? = null,
    val targetWatts: Int? = null,
    val probeTitle: String? = null,
    val probeMessage: String? = null,
    val legacyAlias: String? = null,
) {
    internal enum class Type {
        OPEN_MENU_STEP,
        OPEN_NEW_EWO_EDITOR,
        SELECT_TELEMETRY_ONLY_MODE,
        SELECT_DOCUMENTS_WORKOUT,
        SELECT_WORKOUT_FILE_PATH,
        PREPARE_IMPORTED_HR_VALIDATION,
        ENABLE_MOCK_TRAINER_MODE,
        DISABLE_MOCK_TRAINER_MODE,
        PREPARE_TRAINER_WARM_CONNECTION,
        RELEASE_TRAINER_WARM_CONNECTION,
        OPEN_BASELINE_FITNESS_TEST,
        START_BASELINE_FITNESS_TEST,
        BACK_FROM_BASELINE_FITNESS_TEST,
        CONTINUE_RIDE_AFTER_WORKOUT_COMPLETE,
        CONTINUE_RIDE_VIA_HARD_CUTOVER_PROBE,
        DISCONNECT_POST_WORKOUT_FREERIDE_TRANSPORT,
        START_SESSION,
        START_SESSION_IF_READY,
        START_CLEAN_TELEMETRY_ONLY_SESSION,
        END_SESSION_AND_GO_TO_SUMMARY,
        FORCE_CLEAN_MENU_RESET,
        BACK_TO_MENU,
        REQUEST_TRAINER_CONTROL,
        SET_TRAINER_POWER,
        DISCONNECT_TRAINER_TRANSPORT,
        STOP_TRAINER_WORKOUT,
        RESET_TRAINER,
        SHOW_SESSION_DEBUG_PROBE,
        ARM_SESSION_DEBUG_PROBE_WHEN_PEDALING,
        HIDE_SESSION_DEBUG_PROBE,
        DUMP_STATUS,
    }
}

/**
 * Converts adb activity intents into typed debug automation commands.
 *
 * Protocol:
 * - action must be [ACTION_DEBUG_AUTOMATION].
 * - [EXTRA_COMMAND] chooses the automation command.
 * - [EXTRA_MENU_STEP] is required for `open_menu_step`.
 * - `open_new_ewo_editor` resets the Android editor to a blank document before
 *   opening it, so validation can assert layout hierarchy from a known state.
 * - [EXTRA_WORKOUT_FILE_NAME] is required for `select_documents_workout`.
 * - [EXTRA_WORKOUT_FILE_PATH] is required for `select_workout_file_path`.
 *   Relative paths resolve under the app-owned staged debug-workout
 *   directory, while absolute paths are accepted only when they already live
 *   under app-owned storage.
 * - [EXTRA_TARGET_WATTS] is required for `set_trainer_power`.
 * - `clear_trainer_power` is a legacy alias for `set_trainer_power 0W`, not a
 *   distinct FTMS clear/release command.
 * - [EXTRA_PROBE_MESSAGE] is required for `show_session_debug_probe`.
 * - [EXTRA_MOCK_SCENARIO] optionally arms a mock-trainer scenario for
 *   `prepare_imported_hr_validation`.
 */
internal object DebugAutomationCommandParser {
    internal const val ACTION_DEBUG_AUTOMATION =
        "io.github.ewoc2026.ewoc.action.DEBUG_AUTOMATION"
    internal const val EXTRA_COMMAND = "command"
    internal const val EXTRA_MENU_STEP = "menu_step"
    internal const val EXTRA_WORKOUT_FILE_NAME = "workout_file_name"
    internal const val EXTRA_WORKOUT_FILE_PATH = "workout_file_path"
    internal const val EXTRA_MOCK_SCENARIO = "mock_scenario"
    internal const val EXTRA_TARGET_WATTS = "target_watts"
    internal const val EXTRA_PROBE_TITLE = "probe_title"
    internal const val EXTRA_PROBE_MESSAGE = "probe_message"

    private const val COMMAND_OPEN_MENU_STEP = "open_menu_step"
    private const val COMMAND_OPEN_NEW_EWO_EDITOR = "open_new_ewo_editor"
    private const val COMMAND_SELECT_TELEMETRY_ONLY_MODE = "select_telemetry_only_mode"
    private const val COMMAND_SELECT_DOCUMENTS_WORKOUT = "select_documents_workout"
    private const val COMMAND_SELECT_WORKOUT_FILE_PATH = "select_workout_file_path"
    private const val COMMAND_PREPARE_IMPORTED_HR_VALIDATION = "prepare_imported_hr_validation"
    private const val COMMAND_ENABLE_MOCK_TRAINER_MODE = "enable_mock_trainer_mode"
    private const val COMMAND_DISABLE_MOCK_TRAINER_MODE = "disable_mock_trainer_mode"
    private const val COMMAND_PREPARE_TRAINER_WARM_CONNECTION = "prepare_trainer_warm_connection"
    private const val COMMAND_RELEASE_TRAINER_WARM_CONNECTION = "release_trainer_warm_connection"
    private const val COMMAND_OPEN_BASELINE_FITNESS_TEST = "open_baseline_fitness_test"
    private const val COMMAND_START_BASELINE_FITNESS_TEST = "start_baseline_fitness_test"
    private const val COMMAND_BACK_FROM_BASELINE_FITNESS_TEST = "back_from_baseline_fitness_test"
    private const val COMMAND_CONTINUE_RIDE_AFTER_WORKOUT_COMPLETE =
        "continue_ride_after_workout_complete"
    private const val COMMAND_CONTINUE_RIDE_VIA_HARD_CUTOVER_PROBE =
        "continue_ride_via_hard_cutover_probe"
    private const val COMMAND_DISCONNECT_POST_WORKOUT_FREERIDE_TRANSPORT =
        "disconnect_post_workout_freeride_transport"
    private const val COMMAND_START_SESSION = "start_session"
    private const val COMMAND_START_SESSION_IF_READY = "start_session_if_ready"
    private const val COMMAND_START_CLEAN_TELEMETRY_ONLY_SESSION =
        "start_clean_telemetry_only_session"
    private const val COMMAND_END_SESSION_AND_GO_TO_SUMMARY =
        "end_session_and_go_to_summary"
    private const val COMMAND_FORCE_CLEAN_MENU_RESET = "force_clean_menu_reset"
    private const val COMMAND_BACK_TO_MENU = "back_to_menu"
    private const val COMMAND_REQUEST_TRAINER_CONTROL = "request_trainer_control"
    private const val COMMAND_SET_TRAINER_POWER = "set_trainer_power"
    private const val COMMAND_CLEAR_TRAINER_POWER = "clear_trainer_power"
    private const val COMMAND_DISCONNECT_TRAINER_TRANSPORT = "disconnect_trainer_transport"
    private const val COMMAND_STOP_TRAINER_WORKOUT = "stop_trainer_workout"
    private const val COMMAND_RESET_TRAINER = "reset_trainer"
    private const val COMMAND_SHOW_SESSION_DEBUG_PROBE = "show_session_debug_probe"
    private const val COMMAND_ARM_SESSION_DEBUG_PROBE_WHEN_PEDALING =
        "arm_session_debug_probe_when_pedaling"
    private const val COMMAND_HIDE_SESSION_DEBUG_PROBE = "hide_session_debug_probe"
    private const val COMMAND_DUMP_STATUS = "dump_status"

    fun parse(
        action: String?,
        commandExtra: String?,
        menuStepExtra: String?,
        workoutFileNameExtra: String?,
        workoutFilePathExtra: String?,
        mockScenarioExtra: String?,
        targetWattsExtra: Int? = null,
        probeTitleExtra: String? = null,
        probeMessageExtra: String? = null,
    ): DebugAutomationCommand? {
        if (action != ACTION_DEBUG_AUTOMATION) {
            return null
        }
        val normalizedCommand = commandExtra
            ?.trim()
            ?.lowercase(Locale.US)
            ?: return null
        return when (normalizedCommand) {
            COMMAND_OPEN_MENU_STEP -> {
                val menuStep = parseMenuStep(menuStepExtra) ?: return null
                DebugAutomationCommand(
                    type = DebugAutomationCommand.Type.OPEN_MENU_STEP,
                    menuStep = menuStep,
                )
            }

            COMMAND_OPEN_NEW_EWO_EDITOR -> DebugAutomationCommand(
                type = DebugAutomationCommand.Type.OPEN_NEW_EWO_EDITOR,
            )

            COMMAND_SELECT_TELEMETRY_ONLY_MODE -> DebugAutomationCommand(
                type = DebugAutomationCommand.Type.SELECT_TELEMETRY_ONLY_MODE,
            )

            COMMAND_SELECT_DOCUMENTS_WORKOUT -> {
                val workoutFileName = workoutFileNameExtra
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?: return null
                DebugAutomationCommand(
                    type = DebugAutomationCommand.Type.SELECT_DOCUMENTS_WORKOUT,
                    workoutFileName = workoutFileName,
                )
            }

            COMMAND_SELECT_WORKOUT_FILE_PATH -> {
                val workoutFilePath = workoutFilePathExtra
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?: return null
                DebugAutomationCommand(
                    type = DebugAutomationCommand.Type.SELECT_WORKOUT_FILE_PATH,
                    workoutFilePath = workoutFilePath,
                )
            }

            COMMAND_PREPARE_IMPORTED_HR_VALIDATION -> {
                val workoutFileName = workoutFileNameExtra
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?: return null
                DebugAutomationCommand(
                    type = DebugAutomationCommand.Type.PREPARE_IMPORTED_HR_VALIDATION,
                    workoutFileName = workoutFileName,
                    menuStep = parseMenuStep(menuStepExtra) ?: MenuSetupStep.SUMMARY,
                    mockScenario = com.example.ergometerapp.session.MockTrainerDebugScenario
                        .fromWireName(mockScenarioExtra),
                )
            }

            COMMAND_ENABLE_MOCK_TRAINER_MODE -> DebugAutomationCommand(
                type = DebugAutomationCommand.Type.ENABLE_MOCK_TRAINER_MODE,
            )

            COMMAND_DISABLE_MOCK_TRAINER_MODE -> DebugAutomationCommand(
                type = DebugAutomationCommand.Type.DISABLE_MOCK_TRAINER_MODE,
            )

            COMMAND_PREPARE_TRAINER_WARM_CONNECTION -> DebugAutomationCommand(
                type = DebugAutomationCommand.Type.PREPARE_TRAINER_WARM_CONNECTION,
            )

            COMMAND_RELEASE_TRAINER_WARM_CONNECTION -> DebugAutomationCommand(
                type = DebugAutomationCommand.Type.RELEASE_TRAINER_WARM_CONNECTION,
            )

            COMMAND_OPEN_BASELINE_FITNESS_TEST -> DebugAutomationCommand(
                type = DebugAutomationCommand.Type.OPEN_BASELINE_FITNESS_TEST,
            )

            COMMAND_START_BASELINE_FITNESS_TEST -> DebugAutomationCommand(
                type = DebugAutomationCommand.Type.START_BASELINE_FITNESS_TEST,
            )

            COMMAND_BACK_FROM_BASELINE_FITNESS_TEST -> DebugAutomationCommand(
                type = DebugAutomationCommand.Type.BACK_FROM_BASELINE_FITNESS_TEST,
            )

            COMMAND_CONTINUE_RIDE_AFTER_WORKOUT_COMPLETE -> DebugAutomationCommand(
                type = DebugAutomationCommand.Type.CONTINUE_RIDE_AFTER_WORKOUT_COMPLETE,
            )

            COMMAND_CONTINUE_RIDE_VIA_HARD_CUTOVER_PROBE -> DebugAutomationCommand(
                type = DebugAutomationCommand.Type.CONTINUE_RIDE_VIA_HARD_CUTOVER_PROBE,
            )

            COMMAND_DISCONNECT_POST_WORKOUT_FREERIDE_TRANSPORT -> DebugAutomationCommand(
                type = DebugAutomationCommand.Type.DISCONNECT_POST_WORKOUT_FREERIDE_TRANSPORT,
            )

            COMMAND_START_SESSION -> DebugAutomationCommand(
                type = DebugAutomationCommand.Type.START_SESSION,
            )

            COMMAND_START_SESSION_IF_READY -> DebugAutomationCommand(
                type = DebugAutomationCommand.Type.START_SESSION_IF_READY,
            )

            COMMAND_START_CLEAN_TELEMETRY_ONLY_SESSION -> DebugAutomationCommand(
                type = DebugAutomationCommand.Type.START_CLEAN_TELEMETRY_ONLY_SESSION,
            )

            COMMAND_END_SESSION_AND_GO_TO_SUMMARY -> DebugAutomationCommand(
                type = DebugAutomationCommand.Type.END_SESSION_AND_GO_TO_SUMMARY,
            )

            COMMAND_FORCE_CLEAN_MENU_RESET -> DebugAutomationCommand(
                type = DebugAutomationCommand.Type.FORCE_CLEAN_MENU_RESET,
            )

            COMMAND_BACK_TO_MENU -> DebugAutomationCommand(
                type = DebugAutomationCommand.Type.BACK_TO_MENU,
            )

            COMMAND_REQUEST_TRAINER_CONTROL -> DebugAutomationCommand(
                type = DebugAutomationCommand.Type.REQUEST_TRAINER_CONTROL,
            )

            COMMAND_SET_TRAINER_POWER -> {
                val targetWatts = targetWattsExtra ?: return null
                DebugAutomationCommand(
                    type = DebugAutomationCommand.Type.SET_TRAINER_POWER,
                    targetWatts = targetWatts,
                )
            }

            COMMAND_CLEAR_TRAINER_POWER -> DebugAutomationCommand(
                type = DebugAutomationCommand.Type.SET_TRAINER_POWER,
                targetWatts = 0,
                legacyAlias = COMMAND_CLEAR_TRAINER_POWER,
            )

            COMMAND_DISCONNECT_TRAINER_TRANSPORT -> DebugAutomationCommand(
                type = DebugAutomationCommand.Type.DISCONNECT_TRAINER_TRANSPORT,
            )

            COMMAND_STOP_TRAINER_WORKOUT -> DebugAutomationCommand(
                type = DebugAutomationCommand.Type.STOP_TRAINER_WORKOUT,
            )

            COMMAND_RESET_TRAINER -> DebugAutomationCommand(
                type = DebugAutomationCommand.Type.RESET_TRAINER,
            )

            COMMAND_SHOW_SESSION_DEBUG_PROBE -> {
                val probeMessage = probeMessageExtra
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?: return null
                DebugAutomationCommand(
                    type = DebugAutomationCommand.Type.SHOW_SESSION_DEBUG_PROBE,
                    probeTitle = probeTitleExtra
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() },
                    probeMessage = probeMessage,
                )
            }

            COMMAND_ARM_SESSION_DEBUG_PROBE_WHEN_PEDALING -> {
                val probeMessage = probeMessageExtra
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?: return null
                DebugAutomationCommand(
                    type = DebugAutomationCommand.Type.ARM_SESSION_DEBUG_PROBE_WHEN_PEDALING,
                    probeTitle = probeTitleExtra
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() },
                    probeMessage = probeMessage,
                )
            }

            COMMAND_HIDE_SESSION_DEBUG_PROBE -> DebugAutomationCommand(
                type = DebugAutomationCommand.Type.HIDE_SESSION_DEBUG_PROBE,
            )

            COMMAND_DUMP_STATUS -> DebugAutomationCommand(
                type = DebugAutomationCommand.Type.DUMP_STATUS,
            )

            else -> null
        }
    }

    fun fromIntent(intent: Intent?): DebugAutomationCommand? {
        val targetWattsExtra = if (intent?.hasExtra(EXTRA_TARGET_WATTS) == true) {
            intent.getIntExtra(EXTRA_TARGET_WATTS, 0)
        } else {
            null
        }
        return parse(
            action = intent?.action,
            commandExtra = intent?.getStringExtra(EXTRA_COMMAND),
            menuStepExtra = intent?.getStringExtra(EXTRA_MENU_STEP),
            workoutFileNameExtra = intent?.getStringExtra(EXTRA_WORKOUT_FILE_NAME),
            workoutFilePathExtra = intent?.getStringExtra(EXTRA_WORKOUT_FILE_PATH),
            mockScenarioExtra = intent?.getStringExtra(EXTRA_MOCK_SCENARIO),
            targetWattsExtra = targetWattsExtra,
            probeTitleExtra = intent?.getStringExtra(EXTRA_PROBE_TITLE),
            probeMessageExtra = intent?.getStringExtra(EXTRA_PROBE_MESSAGE),
        )
    }

    private fun parseMenuStep(rawValue: String?): MenuSetupStep? {
        val normalized = rawValue
            ?.trim()
            ?.lowercase(Locale.US)
            ?: return null
        return when (normalized) {
            "profile" -> MenuSetupStep.PROFILE
            "devices" -> MenuSetupStep.DEVICES
            "file",
            "file_based",
            "file-mode",
            "file_mode" -> MenuSetupStep.FILE_BASED
            "ai",
            "ai_based",
            "ai-mode",
            "summary" -> MenuSetupStep.SUMMARY
            else -> null
        }
    }
}

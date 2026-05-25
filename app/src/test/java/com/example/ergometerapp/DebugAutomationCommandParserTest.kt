package com.example.ergometerapp

import com.example.ergometerapp.ui.MenuSetupStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DebugAutomationCommandParserTest {

    @Test
    fun parse_returnsNullWhenActionDoesNotMatch() {
        val parsed = DebugAutomationCommandParser.parse(
            action = "io.github.ewoc2026.ewoc.action.UNRELATED",
            commandExtra = "dump_status",
            menuStepExtra = null,
            workoutFileNameExtra = null,
            workoutFilePathExtra = null,
            mockScenarioExtra = null,
        )

        assertNull(parsed)
    }

    @Test
    fun parse_parsesOpenMenuStep() {
        val parsed = DebugAutomationCommandParser.parse(
            action = DebugAutomationCommandParser.ACTION_DEBUG_AUTOMATION,
            commandExtra = "OPEN_MENU_STEP",
            menuStepExtra = "file_mode",
            workoutFileNameExtra = null,
            workoutFilePathExtra = null,
            mockScenarioExtra = null,
        )

        requireNotNull(parsed)
        assertEquals(DebugAutomationCommand.Type.OPEN_MENU_STEP, parsed.type)
        assertEquals(MenuSetupStep.FILE_BASED, parsed.menuStep)
    }

    @Test
    fun parse_parsesOpenNewEwoEditor() {
        val parsed = DebugAutomationCommandParser.parse(
            action = DebugAutomationCommandParser.ACTION_DEBUG_AUTOMATION,
            commandExtra = "open_new_ewo_editor",
            menuStepExtra = null,
            workoutFileNameExtra = null,
            workoutFilePathExtra = null,
            mockScenarioExtra = null,
        )

        requireNotNull(parsed)
        assertEquals(DebugAutomationCommand.Type.OPEN_NEW_EWO_EDITOR, parsed.type)
    }

    @Test
    fun parse_parsesSelectTelemetryOnlyMode() {
        val parsed = DebugAutomationCommandParser.parse(
            action = DebugAutomationCommandParser.ACTION_DEBUG_AUTOMATION,
            commandExtra = "select_telemetry_only_mode",
            menuStepExtra = null,
            workoutFileNameExtra = null,
            workoutFilePathExtra = null,
            mockScenarioExtra = null,
        )

        requireNotNull(parsed)
        assertEquals(DebugAutomationCommand.Type.SELECT_TELEMETRY_ONLY_MODE, parsed.type)
    }

    @Test
    fun parse_parsesSelectDocumentsWorkout() {
        val parsed = DebugAutomationCommandParser.parse(
            action = DebugAutomationCommandParser.ACTION_DEBUG_AUTOMATION,
            commandExtra = "select_documents_workout",
            menuStepExtra = null,
            workoutFileNameExtra = "imported_hr_validation_unreachable_low.ewo",
            workoutFilePathExtra = null,
            mockScenarioExtra = null,
        )

        requireNotNull(parsed)
        assertEquals(DebugAutomationCommand.Type.SELECT_DOCUMENTS_WORKOUT, parsed.type)
        assertEquals("imported_hr_validation_unreachable_low.ewo", parsed.workoutFileName)
    }

    @Test
    fun parse_returnsNullWhenOpenMenuStepMissingStep() {
        val parsed = DebugAutomationCommandParser.parse(
            action = DebugAutomationCommandParser.ACTION_DEBUG_AUTOMATION,
            commandExtra = "open_menu_step",
            menuStepExtra = null,
            workoutFileNameExtra = null,
            workoutFilePathExtra = null,
            mockScenarioExtra = null,
        )

        assertNull(parsed)
    }

    @Test
    fun parse_returnsNullWhenSelectDocumentsWorkoutMissingFileName() {
        val parsed = DebugAutomationCommandParser.parse(
            action = DebugAutomationCommandParser.ACTION_DEBUG_AUTOMATION,
            commandExtra = "select_documents_workout",
            menuStepExtra = null,
            workoutFileNameExtra = " ",
            workoutFilePathExtra = null,
            mockScenarioExtra = null,
        )

        assertNull(parsed)
    }

    @Test
    fun parse_parsesPrepareImportedHrValidationWithDefaults() {
        val parsed = DebugAutomationCommandParser.parse(
            action = DebugAutomationCommandParser.ACTION_DEBUG_AUTOMATION,
            commandExtra = "prepare_imported_hr_validation",
            menuStepExtra = null,
            workoutFileNameExtra = "imported_hr_validation_unreachable_low.ewo",
            workoutFilePathExtra = null,
            mockScenarioExtra = "waiting_start_and_pause_capture",
        )

        requireNotNull(parsed)
        assertEquals(DebugAutomationCommand.Type.PREPARE_IMPORTED_HR_VALIDATION, parsed.type)
        assertEquals(MenuSetupStep.SUMMARY, parsed.menuStep)
        assertEquals("imported_hr_validation_unreachable_low.ewo", parsed.workoutFileName)
        assertEquals("waiting_start_and_pause_capture", parsed.mockScenario?.wireName)
    }

    @Test
    fun parse_parsesStartSessionIfReady() {
        val parsed = DebugAutomationCommandParser.parse(
            action = DebugAutomationCommandParser.ACTION_DEBUG_AUTOMATION,
            commandExtra = "start_session_if_ready",
            menuStepExtra = null,
            workoutFileNameExtra = null,
            workoutFilePathExtra = null,
            mockScenarioExtra = null,
        )

        requireNotNull(parsed)
        assertEquals(DebugAutomationCommand.Type.START_SESSION_IF_READY, parsed.type)
    }

    @Test
    fun parse_parsesStartCleanTelemetryOnlySession() {
        val parsed = DebugAutomationCommandParser.parse(
            action = DebugAutomationCommandParser.ACTION_DEBUG_AUTOMATION,
            commandExtra = "start_clean_telemetry_only_session",
            menuStepExtra = null,
            workoutFileNameExtra = null,
            workoutFilePathExtra = null,
            mockScenarioExtra = null,
        )

        requireNotNull(parsed)
        assertEquals(DebugAutomationCommand.Type.START_CLEAN_TELEMETRY_ONLY_SESSION, parsed.type)
    }

    @Test
    fun parse_parsesEndSessionAndGoToSummary() {
        val parsed = DebugAutomationCommandParser.parse(
            action = DebugAutomationCommandParser.ACTION_DEBUG_AUTOMATION,
            commandExtra = "end_session_and_go_to_summary",
            menuStepExtra = null,
            workoutFileNameExtra = null,
            workoutFilePathExtra = null,
            mockScenarioExtra = null,
        )

        requireNotNull(parsed)
        assertEquals(DebugAutomationCommand.Type.END_SESSION_AND_GO_TO_SUMMARY, parsed.type)
    }

    @Test
    fun parse_parsesArmSessionDebugProbeWhenPedaling() {
        val parsed = DebugAutomationCommandParser.parse(
            action = DebugAutomationCommandParser.ACTION_DEBUG_AUTOMATION,
            commandExtra = "arm_session_debug_probe_when_pedaling",
            menuStepExtra = null,
            workoutFileNameExtra = null,
            workoutFilePathExtra = null,
            mockScenarioExtra = null,
            probeTitleExtra = "Safety probe",
            probeMessageExtra = "Hold steady and wait.",
        )

        requireNotNull(parsed)
        assertEquals(
            DebugAutomationCommand.Type.ARM_SESSION_DEBUG_PROBE_WHEN_PEDALING,
            parsed.type,
        )
        assertEquals("Safety probe", parsed.probeTitle)
        assertEquals("Hold steady and wait.", parsed.probeMessage)
    }

    @Test
    fun parse_returnsNullWhenArmSessionDebugProbeWhenPedalingMissingMessage() {
        val parsed = DebugAutomationCommandParser.parse(
            action = DebugAutomationCommandParser.ACTION_DEBUG_AUTOMATION,
            commandExtra = "arm_session_debug_probe_when_pedaling",
            menuStepExtra = null,
            workoutFileNameExtra = null,
            workoutFilePathExtra = null,
            mockScenarioExtra = null,
            probeTitleExtra = "Safety probe",
            probeMessageExtra = " ",
        )

        assertNull(parsed)
    }

    @Test
    fun parse_parsesForceCleanMenuReset() {
        val parsed = DebugAutomationCommandParser.parse(
            action = DebugAutomationCommandParser.ACTION_DEBUG_AUTOMATION,
            commandExtra = "force_clean_menu_reset",
            menuStepExtra = null,
            workoutFileNameExtra = null,
            workoutFilePathExtra = null,
            mockScenarioExtra = null,
        )

        requireNotNull(parsed)
        assertEquals(DebugAutomationCommand.Type.FORCE_CLEAN_MENU_RESET, parsed.type)
    }

    @Test
    fun parse_parsesPrepareTrainerWarmConnection() {
        val parsed = DebugAutomationCommandParser.parse(
            action = DebugAutomationCommandParser.ACTION_DEBUG_AUTOMATION,
            commandExtra = "prepare_trainer_warm_connection",
            menuStepExtra = null,
            workoutFileNameExtra = null,
            workoutFilePathExtra = null,
            mockScenarioExtra = null,
        )

        requireNotNull(parsed)
        assertEquals(DebugAutomationCommand.Type.PREPARE_TRAINER_WARM_CONNECTION, parsed.type)
    }

    @Test
    fun parse_parsesEnableMockTrainerMode() {
        val parsed = DebugAutomationCommandParser.parse(
            action = DebugAutomationCommandParser.ACTION_DEBUG_AUTOMATION,
            commandExtra = "enable_mock_trainer_mode",
            menuStepExtra = null,
            workoutFileNameExtra = null,
            workoutFilePathExtra = null,
            mockScenarioExtra = null,
        )

        requireNotNull(parsed)
        assertEquals(DebugAutomationCommand.Type.ENABLE_MOCK_TRAINER_MODE, parsed.type)
    }

    @Test
    fun parse_parsesDisableMockTrainerMode() {
        val parsed = DebugAutomationCommandParser.parse(
            action = DebugAutomationCommandParser.ACTION_DEBUG_AUTOMATION,
            commandExtra = "disable_mock_trainer_mode",
            menuStepExtra = null,
            workoutFileNameExtra = null,
            workoutFilePathExtra = null,
            mockScenarioExtra = null,
        )

        requireNotNull(parsed)
        assertEquals(DebugAutomationCommand.Type.DISABLE_MOCK_TRAINER_MODE, parsed.type)
    }

    @Test
    fun parse_parsesReleaseTrainerWarmConnection() {
        val parsed = DebugAutomationCommandParser.parse(
            action = DebugAutomationCommandParser.ACTION_DEBUG_AUTOMATION,
            commandExtra = "release_trainer_warm_connection",
            menuStepExtra = null,
            workoutFileNameExtra = null,
            workoutFilePathExtra = null,
            mockScenarioExtra = null,
        )

        requireNotNull(parsed)
        assertEquals(DebugAutomationCommand.Type.RELEASE_TRAINER_WARM_CONNECTION, parsed.type)
    }

    @Test
    fun parse_parsesOpenBaselineFitnessTest() {
        val parsed = DebugAutomationCommandParser.parse(
            action = DebugAutomationCommandParser.ACTION_DEBUG_AUTOMATION,
            commandExtra = "open_baseline_fitness_test",
            menuStepExtra = null,
            workoutFileNameExtra = null,
            workoutFilePathExtra = null,
            mockScenarioExtra = null,
        )

        requireNotNull(parsed)
        assertEquals(DebugAutomationCommand.Type.OPEN_BASELINE_FITNESS_TEST, parsed.type)
    }

    @Test
    fun parse_parsesStartBaselineFitnessTest() {
        val parsed = DebugAutomationCommandParser.parse(
            action = DebugAutomationCommandParser.ACTION_DEBUG_AUTOMATION,
            commandExtra = "start_baseline_fitness_test",
            menuStepExtra = null,
            workoutFileNameExtra = null,
            workoutFilePathExtra = null,
            mockScenarioExtra = null,
        )

        requireNotNull(parsed)
        assertEquals(DebugAutomationCommand.Type.START_BASELINE_FITNESS_TEST, parsed.type)
    }

    @Test
    fun parse_parsesBackFromBaselineFitnessTest() {
        val parsed = DebugAutomationCommandParser.parse(
            action = DebugAutomationCommandParser.ACTION_DEBUG_AUTOMATION,
            commandExtra = "back_from_baseline_fitness_test",
            menuStepExtra = null,
            workoutFileNameExtra = null,
            workoutFilePathExtra = null,
            mockScenarioExtra = null,
        )

        requireNotNull(parsed)
        assertEquals(DebugAutomationCommand.Type.BACK_FROM_BASELINE_FITNESS_TEST, parsed.type)
    }

    @Test
    fun parse_parsesContinueRideAfterWorkoutComplete() {
        val parsed = DebugAutomationCommandParser.parse(
            action = DebugAutomationCommandParser.ACTION_DEBUG_AUTOMATION,
            commandExtra = "continue_ride_after_workout_complete",
            menuStepExtra = null,
            workoutFileNameExtra = null,
            workoutFilePathExtra = null,
            mockScenarioExtra = null,
        )

        requireNotNull(parsed)
        assertEquals(DebugAutomationCommand.Type.CONTINUE_RIDE_AFTER_WORKOUT_COMPLETE, parsed.type)
    }

    @Test
    fun parse_parsesContinueRideViaHardCutoverProbe() {
        val parsed = DebugAutomationCommandParser.parse(
            action = DebugAutomationCommandParser.ACTION_DEBUG_AUTOMATION,
            commandExtra = "continue_ride_via_hard_cutover_probe",
            menuStepExtra = null,
            workoutFileNameExtra = null,
            workoutFilePathExtra = null,
            mockScenarioExtra = null,
        )

        requireNotNull(parsed)
        assertEquals(DebugAutomationCommand.Type.CONTINUE_RIDE_VIA_HARD_CUTOVER_PROBE, parsed.type)
    }

    @Test
    fun parse_parsesDisconnectPostWorkoutFreerideTransport() {
        val parsed = DebugAutomationCommandParser.parse(
            action = DebugAutomationCommandParser.ACTION_DEBUG_AUTOMATION,
            commandExtra = "disconnect_post_workout_freeride_transport",
            menuStepExtra = null,
            workoutFileNameExtra = null,
            workoutFilePathExtra = null,
            mockScenarioExtra = null,
        )

        requireNotNull(parsed)
        assertEquals(
            DebugAutomationCommand.Type.DISCONNECT_POST_WORKOUT_FREERIDE_TRANSPORT,
            parsed.type,
        )
    }

    @Test
    fun parse_parsesSelectWorkoutFilePath() {
        val parsed = DebugAutomationCommandParser.parse(
            action = DebugAutomationCommandParser.ACTION_DEBUG_AUTOMATION,
            commandExtra = "select_workout_file_path",
            menuStepExtra = null,
            workoutFileNameExtra = null,
            workoutFilePathExtra = "/storage/emulated/0/Download/Ewoks/1 min Lunch Spin.ewo",
            mockScenarioExtra = null,
        )

        requireNotNull(parsed)
        assertEquals(DebugAutomationCommand.Type.SELECT_WORKOUT_FILE_PATH, parsed.type)
        assertEquals(
            "/storage/emulated/0/Download/Ewoks/1 min Lunch Spin.ewo",
            parsed.workoutFilePath,
        )
    }

    @Test
    fun parse_parsesRequestTrainerControl() {
        val parsed = DebugAutomationCommandParser.parse(
            action = DebugAutomationCommandParser.ACTION_DEBUG_AUTOMATION,
            commandExtra = "request_trainer_control",
            menuStepExtra = null,
            workoutFileNameExtra = null,
            workoutFilePathExtra = null,
            mockScenarioExtra = null,
        )

        requireNotNull(parsed)
        assertEquals(DebugAutomationCommand.Type.REQUEST_TRAINER_CONTROL, parsed.type)
    }

    @Test
    fun parse_parsesSetTrainerPowerIncludingZeroWatts() {
        val parsed = DebugAutomationCommandParser.parse(
            action = DebugAutomationCommandParser.ACTION_DEBUG_AUTOMATION,
            commandExtra = "set_trainer_power",
            menuStepExtra = null,
            workoutFileNameExtra = null,
            workoutFilePathExtra = null,
            mockScenarioExtra = null,
            targetWattsExtra = 0,
        )

        requireNotNull(parsed)
        assertEquals(DebugAutomationCommand.Type.SET_TRAINER_POWER, parsed.type)
        assertEquals(0, parsed.targetWatts)
    }

    @Test
    fun parse_returnsNullWhenSetTrainerPowerMissingWatts() {
        val parsed = DebugAutomationCommandParser.parse(
            action = DebugAutomationCommandParser.ACTION_DEBUG_AUTOMATION,
            commandExtra = "set_trainer_power",
            menuStepExtra = null,
            workoutFileNameExtra = null,
            workoutFilePathExtra = null,
            mockScenarioExtra = null,
        )

        assertNull(parsed)
    }

    @Test
    fun parse_mapsClearTrainerPowerAliasToZeroWattSetPower() {
        val parsed = DebugAutomationCommandParser.parse(
            action = DebugAutomationCommandParser.ACTION_DEBUG_AUTOMATION,
            commandExtra = "clear_trainer_power",
            menuStepExtra = null,
            workoutFileNameExtra = null,
            workoutFilePathExtra = null,
            mockScenarioExtra = null,
        )

        requireNotNull(parsed)
        assertEquals(DebugAutomationCommand.Type.SET_TRAINER_POWER, parsed.type)
        assertEquals(0, parsed.targetWatts)
        assertEquals("clear_trainer_power", parsed.legacyAlias)
    }

    @Test
    fun parse_parsesDisconnectTrainerTransport() {
        val parsed = DebugAutomationCommandParser.parse(
            action = DebugAutomationCommandParser.ACTION_DEBUG_AUTOMATION,
            commandExtra = "disconnect_trainer_transport",
            menuStepExtra = null,
            workoutFileNameExtra = null,
            workoutFilePathExtra = null,
            mockScenarioExtra = null,
        )

        requireNotNull(parsed)
        assertEquals(DebugAutomationCommand.Type.DISCONNECT_TRAINER_TRANSPORT, parsed.type)
    }

    @Test
    fun parse_parsesStopTrainerWorkout() {
        val parsed = DebugAutomationCommandParser.parse(
            action = DebugAutomationCommandParser.ACTION_DEBUG_AUTOMATION,
            commandExtra = "stop_trainer_workout",
            menuStepExtra = null,
            workoutFileNameExtra = null,
            workoutFilePathExtra = null,
            mockScenarioExtra = null,
        )

        requireNotNull(parsed)
        assertEquals(DebugAutomationCommand.Type.STOP_TRAINER_WORKOUT, parsed.type)
    }

    @Test
    fun parse_parsesResetTrainer() {
        val parsed = DebugAutomationCommandParser.parse(
            action = DebugAutomationCommandParser.ACTION_DEBUG_AUTOMATION,
            commandExtra = "reset_trainer",
            menuStepExtra = null,
            workoutFileNameExtra = null,
            workoutFilePathExtra = null,
            mockScenarioExtra = null,
        )

        requireNotNull(parsed)
        assertEquals(DebugAutomationCommand.Type.RESET_TRAINER, parsed.type)
    }

    @Test
    fun parse_parsesShowSessionDebugProbe() {
        val parsed = DebugAutomationCommandParser.parse(
            action = DebugAutomationCommandParser.ACTION_DEBUG_AUTOMATION,
            commandExtra = "show_session_debug_probe",
            menuStepExtra = null,
            workoutFileNameExtra = null,
            workoutFilePathExtra = null,
            mockScenarioExtra = null,
            probeTitleExtra = "Safety probe",
            probeMessageExtra = "Hold steady at 100 W",
        )

        requireNotNull(parsed)
        assertEquals(DebugAutomationCommand.Type.SHOW_SESSION_DEBUG_PROBE, parsed.type)
        assertEquals("Safety probe", parsed.probeTitle)
        assertEquals("Hold steady at 100 W", parsed.probeMessage)
    }

    @Test
    fun parse_returnsNullWhenShowSessionDebugProbeMissingMessage() {
        val parsed = DebugAutomationCommandParser.parse(
            action = DebugAutomationCommandParser.ACTION_DEBUG_AUTOMATION,
            commandExtra = "show_session_debug_probe",
            menuStepExtra = null,
            workoutFileNameExtra = null,
            workoutFilePathExtra = null,
            mockScenarioExtra = null,
            probeTitleExtra = "Safety probe",
            probeMessageExtra = " ",
        )

        assertNull(parsed)
    }

    @Test
    fun parse_parsesHideSessionDebugProbe() {
        val parsed = DebugAutomationCommandParser.parse(
            action = DebugAutomationCommandParser.ACTION_DEBUG_AUTOMATION,
            commandExtra = "hide_session_debug_probe",
            menuStepExtra = null,
            workoutFileNameExtra = null,
            workoutFilePathExtra = null,
            mockScenarioExtra = null,
        )

        requireNotNull(parsed)
        assertEquals(DebugAutomationCommand.Type.HIDE_SESSION_DEBUG_PROBE, parsed.type)
    }
}

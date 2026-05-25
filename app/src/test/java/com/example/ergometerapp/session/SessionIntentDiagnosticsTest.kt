package com.example.ergometerapp.session

import com.example.ergometerapp.AppScreen
import com.example.ergometerapp.SessionSetupMode
import com.example.ergometerapp.session.release.TrainerControlAuthority
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionIntentDiagnosticsTest {
    @Test
    fun telemetryOnlyReconnectUsesCleanReconnectExplanation() {
        val diagnostics = resolveSessionIntentDiagnostics(
            SessionIntentDebugFacts(
                screen = AppScreen.CONNECTING,
                setupMode = SessionSetupMode.TELEMETRY_ONLY,
                selectedWorkoutLabel = null,
                runnerRunning = false,
                runnerPaused = false,
                runnerDone = false,
                ftmsReady = false,
                ftmsControlGranted = false,
                trainerAuthority = TrainerControlAuthority.APP_CONTROLLED,
                preparedTrainerReusable = false,
                telemetryOnlyStartReconnectInProgress = true,
                telemetryOnlyStartShouldBypassControlRequest = true,
                postWorkoutFreerideModeActive = false,
                releaseFlowState = "idle",
            ),
        )

        assertEquals("telemetry_only", diagnostics.intentWire)
        assertEquals("clean_reconnect_without_control_request", diagnostics.policyWire)
        assertEquals("prior_target_write_requires_clean_epoch", diagnostics.whyWire)
        assertEquals("resume_rider_controlled_telemetry", diagnostics.nextWire)
    }

    @Test
    fun activeTelemetryOnlyExplainsRiderControlledPolicy() {
        val diagnostics = resolveSessionIntentDiagnostics(
            SessionIntentDebugFacts(
                screen = AppScreen.SESSION,
                setupMode = SessionSetupMode.TELEMETRY_ONLY,
                selectedWorkoutLabel = null,
                runnerRunning = true,
                runnerPaused = false,
                runnerDone = false,
                ftmsReady = true,
                ftmsControlGranted = false,
                trainerAuthority = TrainerControlAuthority.RIDER_CONTROLLED,
                preparedTrainerReusable = false,
                telemetryOnlyStartReconnectInProgress = false,
                telemetryOnlyStartShouldBypassControlRequest = false,
                postWorkoutFreerideModeActive = false,
                releaseFlowState = "idle",
            ),
        )

        assertEquals("rider_controlled_telemetry", diagnostics.policyWire)
        assertEquals("no_workout_targets_should_control_trainer", diagnostics.whyWire)
        assertEquals("manual_trainer_adjustment_should_work", diagnostics.nextWire)
    }

    @Test
    fun directedWorkoutExplainsAppControlledTargets() {
        val diagnostics = resolveSessionIntentDiagnostics(
            SessionIntentDebugFacts(
                screen = AppScreen.SESSION,
                setupMode = SessionSetupMode.FILE,
                selectedWorkoutLabel = "30 sec Lunch Spin",
                runnerRunning = true,
                runnerPaused = false,
                runnerDone = false,
                ftmsReady = true,
                ftmsControlGranted = true,
                trainerAuthority = TrainerControlAuthority.APP_CONTROLLED,
                preparedTrainerReusable = false,
                telemetryOnlyStartReconnectInProgress = false,
                telemetryOnlyStartShouldBypassControlRequest = false,
                postWorkoutFreerideModeActive = false,
                releaseFlowState = "idle",
            ),
        )

        assertEquals("Directed workout: 30 sec Lunch Spin", diagnostics.intentLabel)
        assertEquals("app_controlled_workout", diagnostics.policyWire)
        assertEquals("workout_targets_are_active", diagnostics.whyWire)
        assertEquals("continue_target_power_updates", diagnostics.nextWire)
    }

    @Test
    fun continueRideReleaseUsesSafetyExplanation() {
        val diagnostics = resolveSessionIntentDiagnostics(
            SessionIntentDebugFacts(
                screen = AppScreen.SESSION,
                setupMode = SessionSetupMode.TELEMETRY_ONLY,
                selectedWorkoutLabel = null,
                runnerRunning = false,
                runnerPaused = false,
                runnerDone = true,
                ftmsReady = true,
                ftmsControlGranted = true,
                trainerAuthority = TrainerControlAuthority.APP_CONTROLLED,
                preparedTrainerReusable = false,
                telemetryOnlyStartReconnectInProgress = false,
                telemetryOnlyStartShouldBypassControlRequest = false,
                postWorkoutFreerideModeActive = false,
                releaseFlowState = "release_ramp",
            ),
        )

        assertEquals("continue_ride", diagnostics.intentWire)
        assertEquals("release_workout_control_safely", diagnostics.policyWire)
        assertEquals("workout_end_must_finish_safe_release_first", diagnostics.whyWire)
        assertEquals("open_clean_telemetry_restart_window", diagnostics.nextWire)
    }
}

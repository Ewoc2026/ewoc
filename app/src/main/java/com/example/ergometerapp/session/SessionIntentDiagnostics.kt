package com.example.ergometerapp.session

import com.example.ergometerapp.AppScreen
import com.example.ergometerapp.SessionSetupMode
import com.example.ergometerapp.session.release.TrainerControlAuthority

/**
 * Stable semantic explanation of what session/runtime behavior the app currently believes it owes
 * the user.
 *
 * The point of this structure is not to mirror every internal flag. It translates the current
 * runtime situation into one operator-facing explanation and one machine-friendly dump context so
 * overlay text and `dump_status` stay aligned.
 */
internal data class SessionIntentDiagnostics(
    val intentLabel: String,
    val policyLabel: String,
    val whyLabel: String,
    val nextLabel: String,
    val intentWire: String,
    val policyWire: String,
    val whyWire: String,
    val nextWire: String,
) {
    fun overlaySummary(releaseSummary: String? = null): String {
        return buildString {
            append("Intent: ").append(intentLabel)
            append('\n').append("Policy: ").append(policyLabel)
            append('\n').append("Why: ").append(whyLabel)
            append('\n').append("Next: ").append(nextLabel)
            if (!releaseSummary.isNullOrBlank()) {
                append('\n').append(releaseSummary)
            }
        }
    }

    fun dumpContext(): Map<String, String> {
        return linkedMapOf(
            "intent" to intentWire,
            "policy" to policyWire,
            "why" to whyWire,
            "next" to nextWire,
        )
    }
}

/**
 * Minimal runtime facts needed to explain the app's current session intent semantically.
 */
internal data class SessionIntentDebugFacts(
    val screen: AppScreen,
    val setupMode: SessionSetupMode,
    val selectedWorkoutLabel: String?,
    val runnerRunning: Boolean,
    val runnerPaused: Boolean,
    val runnerDone: Boolean,
    val ftmsReady: Boolean,
    val ftmsControlGranted: Boolean,
    val trainerAuthority: TrainerControlAuthority,
    val preparedTrainerReusable: Boolean,
    val telemetryOnlyStartReconnectInProgress: Boolean,
    val telemetryOnlyStartShouldBypassControlRequest: Boolean,
    val postWorkoutFreerideModeActive: Boolean,
    val releaseFlowState: String,
)

internal fun resolveSessionIntentDiagnostics(
    facts: SessionIntentDebugFacts,
): SessionIntentDiagnostics {
    return when {
        facts.telemetryOnlyStartReconnectInProgress || facts.telemetryOnlyStartShouldBypassControlRequest ->
            SessionIntentDiagnostics(
                intentLabel = "Telemetry only",
                policyLabel = "Clean reconnect, no control request",
                whyLabel = "Prior app target write must be fully released first",
                nextLabel = "Resume rider-controlled telemetry",
                intentWire = "telemetry_only",
                policyWire = "clean_reconnect_without_control_request",
                whyWire = "prior_target_write_requires_clean_epoch",
                nextWire = "resume_rider_controlled_telemetry",
            )

        facts.releaseFlowState != "idle" -> resolveContinueRideDiagnostics(facts)
        facts.setupMode == SessionSetupMode.TELEMETRY_ONLY -> resolveTelemetryOnlyDiagnostics(facts)
        else -> resolveDirectedWorkoutDiagnostics(facts)
    }
}

private fun resolveContinueRideDiagnostics(
    facts: SessionIntentDebugFacts,
): SessionIntentDiagnostics {
    return when {
        facts.releaseFlowState == "post_workout_freeride" || facts.postWorkoutFreerideModeActive ->
            SessionIntentDiagnostics(
                intentLabel = "Continue ride",
                policyLabel = "Fresh telemetry segment",
                whyLabel = "Workout segment ended but the rider chose to keep riding",
                nextLabel = "Next directed workout will reacquire app control",
                intentWire = "continue_ride",
                policyWire = "fresh_telemetry_segment",
                whyWire = "workout_segment_completed_but_ride_continues",
                nextWire = "next_directed_workout_reacquires_control",
            )

        facts.releaseFlowState.startsWith("telemetry_reconnect") ||
            facts.releaseFlowState == "restart_window_open" ->
            SessionIntentDiagnostics(
                intentLabel = "Continue ride",
                policyLabel = "Fresh telemetry restart",
                whyLabel = "Trainer needs a clean restart after workout-held control",
                nextLabel = "Resume rider-controlled telemetry",
                intentWire = "continue_ride",
                policyWire = "fresh_telemetry_restart",
                whyWire = "trainer_requires_clean_restart_after_workout_control",
                nextWire = "resume_rider_controlled_telemetry",
            )

        else ->
            SessionIntentDiagnostics(
                intentLabel = "Continue ride",
                policyLabel = "Release workout control safely",
                whyLabel = "Workout just ended under app-controlled trainer state",
                nextLabel = "Open a clean restart window for telemetry-only riding",
                intentWire = "continue_ride",
                policyWire = "release_workout_control_safely",
                whyWire = "workout_end_must_finish_safe_release_first",
                nextWire = "open_clean_telemetry_restart_window",
            )
    }
}

private fun resolveTelemetryOnlyDiagnostics(
    facts: SessionIntentDebugFacts,
): SessionIntentDiagnostics {
    return when {
        facts.ftmsControlGranted || facts.trainerAuthority == TrainerControlAuthority.APP_CONTROLLED ->
            SessionIntentDiagnostics(
                intentLabel = "Telemetry only",
                policyLabel = "Release app control",
                whyLabel = "Telemetry-only should not own trainer load",
                nextLabel = "Return rider control to the trainer",
                intentWire = "telemetry_only",
                policyWire = "release_app_control",
                whyWire = "telemetry_only_must_not_hold_trainer_load",
                nextWire = "return_rider_control_to_trainer",
            )

        facts.screen == AppScreen.CONNECTING || !facts.ftmsReady ->
            SessionIntentDiagnostics(
                intentLabel = "Telemetry only",
                policyLabel = if (facts.preparedTrainerReusable) {
                    "Reuse warm link without control"
                } else {
                    "Connect without trainer control"
                },
                whyLabel = "Telemetry-only must preserve rider control",
                nextLabel = "Start rider-controlled telemetry",
                intentWire = "telemetry_only",
                policyWire = if (facts.preparedTrainerReusable) {
                    "reuse_warm_link_without_control"
                } else {
                    "connect_without_trainer_control"
                },
                whyWire = "telemetry_only_preserves_rider_control",
                nextWire = "start_rider_controlled_telemetry",
            )

        else ->
            SessionIntentDiagnostics(
                intentLabel = "Telemetry only",
                policyLabel = "Rider-controlled telemetry",
                whyLabel = "No workout targets should control the trainer",
                nextLabel = "Manual trainer adjustment should work now",
                intentWire = "telemetry_only",
                policyWire = "rider_controlled_telemetry",
                whyWire = "no_workout_targets_should_control_trainer",
                nextWire = "manual_trainer_adjustment_should_work",
            )
    }
}

private fun resolveDirectedWorkoutDiagnostics(
    facts: SessionIntentDebugFacts,
): SessionIntentDiagnostics {
    val selectedWorkoutLabel = facts.selectedWorkoutLabel
        ?.takeUnless { it.isBlank() || it == "none" }
    val intentLabel = selectedWorkoutLabel?.let { "Directed workout: $it" } ?: "Directed workout"
    return when {
        selectedWorkoutLabel == null && !facts.runnerRunning && !facts.runnerPaused && !facts.runnerDone ->
            SessionIntentDiagnostics(
                intentLabel = intentLabel,
                policyLabel = "Selection incomplete",
                whyLabel = "No workout is currently selected",
                nextLabel = "Choose a workout before Start",
                intentWire = "directed_workout",
                policyWire = "selection_incomplete",
                whyWire = "no_workout_selected",
                nextWire = "choose_workout_before_start",
            )

        facts.ftmsControlGranted || facts.trainerAuthority == TrainerControlAuthority.APP_CONTROLLED ->
            SessionIntentDiagnostics(
                intentLabel = intentLabel,
                policyLabel = "App-controlled workout",
                whyLabel = if (facts.runnerRunning || facts.runnerPaused) {
                    "Workout targets are active"
                } else {
                    "Workout start is allowed to own trainer load"
                },
                nextLabel = if (facts.runnerRunning || facts.runnerPaused) {
                    "Continue target power updates"
                } else {
                    "Start workout targets"
                },
                intentWire = "directed_workout",
                policyWire = "app_controlled_workout",
                whyWire = if (facts.runnerRunning || facts.runnerPaused) {
                    "workout_targets_are_active"
                } else {
                    "workout_start_may_own_trainer_load"
                },
                nextWire = if (facts.runnerRunning || facts.runnerPaused) {
                    "continue_target_power_updates"
                } else {
                    "start_workout_targets"
                },
            )

        facts.screen == AppScreen.CONNECTING || !facts.ftmsReady ->
            SessionIntentDiagnostics(
                intentLabel = intentLabel,
                policyLabel = if (facts.preparedTrainerReusable) {
                    "Reuse warm link and request control"
                } else {
                    "Connect and request control"
                },
                whyLabel = "Workout targets require app control",
                nextLabel = "Start target power updates",
                intentWire = "directed_workout",
                policyWire = if (facts.preparedTrainerReusable) {
                    "reuse_warm_link_and_request_control"
                } else {
                    "connect_and_request_control"
                },
                whyWire = "workout_targets_require_app_control",
                nextWire = "start_target_power_updates",
            )

        else ->
            SessionIntentDiagnostics(
                intentLabel = intentLabel,
                policyLabel = "Acquire trainer control",
                whyLabel = "Workout targets cannot run without control",
                nextLabel = "Request control on the current trainer link",
                intentWire = "directed_workout",
                policyWire = "acquire_trainer_control",
                whyWire = "workout_targets_cannot_run_without_control",
                nextWire = "request_control_on_current_link",
            )
    }
}

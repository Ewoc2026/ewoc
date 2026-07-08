package io.github.ewoc2026.ewoc.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import io.github.ewoc2026.ewoc.AppScreen
import io.github.ewoc2026.ewoc.R
import io.github.ewoc2026.ewoc.SessionLifecycleState
import io.github.ewoc2026.ewoc.SessionSetupMode
import io.github.ewoc2026.ewoc.session.SessionPhase
import io.github.ewoc2026.ewoc.workout.WorkoutFile
import io.github.ewoc2026.ewoc.workout.runner.RunnerState
import org.junit.Rule
import org.junit.Test

class SessionTelemetryOnlyScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun telemetryOnlySessionPrefersLiveDurationAndPhaseStateOverStaleWorkoutMetadata() {
        val modelState = mutableStateOf(
            MainActivityUiModelTestFactory.base(screen = AppScreen.SESSION).copy(
                phase = SessionPhase.RUNNING,
                sessionLifecycleState = SessionLifecycleState.RUNNING,
                runnerState = RunnerState.stopped(workoutElapsedSec = 999),
                sessionDurationSeconds = 142,
                menuState = MainActivityUiModelTestFactory.menuState().copy(
                    selectedSessionSetupMode = SessionSetupMode.TELEMETRY_ONLY,
                    selectedWorkout = staleWorkout(),
                    selectedWorkoutFileName = "stale_editor_workout.zwo",
                ),
            ),
        )

        composeRule.setContent {
            MainActivityContent(
                model = modelState.value,
                onSelectWorkoutFile = {},
                onFtpInputChanged = {},
                onHrProfileAgeInputChanged = {},
                onHrProfileSexSelected = {},
                onSearchFtmsDevices = {},
                onSearchHrDevices = {},
                onScannedDeviceSelected = {},
                onDismissDeviceSelection = {},
                onDismissConnectionIssue = {},
                onSearchFtmsDevicesFromConnectionIssue = {},
                onOpenAppSettingsFromConnectionIssue = {},
                onAiMenuAssistantAction = { false },
                onStartSession = {},
                onEndSession = {},
                onBackToMenu = {},
                onRequestSummaryFitExport = {},
            )
        }
        composeRule.waitForIdle()

        val telemetryOnlyLabel =
            composeRule.activity.getString(R.string.menu_setup_mode_telemetry_only_title)
        val runningLabel = composeRule.activity.getString(R.string.session_phase_running)
        val doneLabel = composeRule.activity.getString(R.string.session_workout_state_done)
        val unknownValue = composeRule.activity.getString(R.string.value_unknown)
        val elapsedLabel = composeRule.activity.getString(
            R.string.session_elapsed_of_total_value,
            "02:22",
            unknownValue,
        )

        composeRule.onNodeWithText(telemetryOnlyLabel).assertIsDisplayed()
        composeRule.onNodeWithText(runningLabel).assertIsDisplayed()
        composeRule.onNodeWithText(elapsedLabel).performScrollTo().assertIsDisplayed()
        composeRule.onAllNodesWithText(doneLabel).assertCountEquals(0)
        composeRule.onAllNodesWithText("Stale Editor Workout").assertCountEquals(0)
    }

    private fun staleWorkout(): WorkoutFile {
        return WorkoutFile(
            name = "Stale Editor Workout",
            description = "Should stay hidden in telemetry-only mode.",
            author = "Preview",
            tags = emptyList(),
            steps = emptyList(),
            textEvents = emptyList(),
        )
    }
}

package io.github.ewoc2026.ewoc.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.ewoc2026.ewoc.AppScreen
import io.github.ewoc2026.ewoc.R
import io.github.ewoc2026.ewoc.SessionLifecycleState
import io.github.ewoc2026.ewoc.SessionSetupMode
import io.github.ewoc2026.ewoc.session.SessionPhase
import io.github.ewoc2026.ewoc.session.SessionSample
import io.github.ewoc2026.ewoc.workout.Step
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

    @Test
    fun telemetryOnlyShortPortraitSplitKeepsHistogramAndQuitVisible() {
        setSessionContent(
            model = telemetryOnlyRunningModel(),
            width = 360.dp,
            height = 520.dp,
        )

        composeRule.onNodeWithTag("sessionLiveTelemetryChart").assertIsDisplayed()
        composeRule.onNodeWithTag("sessionQuitButton").assertIsDisplayed()
    }

    @Test
    fun structuredWorkoutShortLandscapeSplitKeepsChartAndQuitVisible() {
        setSessionContent(
            model = structuredWorkoutRunningModel(),
            width = 520.dp,
            height = 360.dp,
        )

        composeRule.onNodeWithTag("sessionWorkoutProfileChart").assertIsDisplayed()
        composeRule.onNodeWithTag("sessionQuitButton").assertIsDisplayed()
    }

    @Test
    fun telemetryOnlyAutoPausedByZeroCadenceUsesPausedStatusLabel() {
        setSessionContent(
            model = telemetryOnlyRunningModel().copy(autoPausedByZeroCadence = true),
            width = 360.dp,
            height = 520.dp,
        )

        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.session_workout_state_paused),
        ).assertIsDisplayed()
    }

    private fun setSessionContent(
        model: MainActivityUiModel,
        width: Dp,
        height: Dp,
    ) {
        composeRule.setContent {
            Box(modifier = Modifier.requiredSize(width = width, height = height)) {
                MainActivityContent(
                    model = model,
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
        }
        composeRule.waitForIdle()
    }

    private fun telemetryOnlyRunningModel(): MainActivityUiModel {
        return MainActivityUiModelTestFactory.base(screen = AppScreen.SESSION).copy(
            phase = SessionPhase.RUNNING,
            sessionLifecycleState = SessionLifecycleState.RUNNING,
            runnerState = RunnerState.stopped(workoutElapsedSec = 0),
            sessionDurationSeconds = 42,
            menuState = MainActivityUiModelTestFactory.menuState().copy(
                selectedSessionSetupMode = SessionSetupMode.TELEMETRY_ONLY,
            ),
            timelineSamples = listOf(
                SessionSample(
                    timestampMillis = 1_000L,
                    powerWatts = 140,
                    cadenceRpm = 84,
                    heartRateBpm = null,
                    distanceMeters = 10,
                    totalEnergyKcal = 1,
                ),
                SessionSample(
                    timestampMillis = 2_000L,
                    powerWatts = 150,
                    cadenceRpm = 86,
                    heartRateBpm = null,
                    distanceMeters = 20,
                    totalEnergyKcal = 1,
                ),
            ),
        )
    }

    private fun structuredWorkoutRunningModel(): MainActivityUiModel {
        return MainActivityUiModelTestFactory.base(screen = AppScreen.SESSION).copy(
            phase = SessionPhase.RUNNING,
            sessionLifecycleState = SessionLifecycleState.RUNNING,
            runnerState = RunnerState(
                running = true,
                paused = false,
                done = false,
                label = null,
                targetPowerWatts = 150,
                targetCadence = 88,
                workoutElapsedSec = 30,
                stepRemainingSec = 270,
                intervalPart = null,
            ),
            sessionDurationSeconds = 30,
            menuState = MainActivityUiModelTestFactory.menuState().copy(
                selectedSessionSetupMode = SessionSetupMode.FILE,
                selectedWorkout = compactWorkout(),
                selectedWorkoutFileName = "compact-split.ewo",
            ),
        )
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

    private fun compactWorkout(): WorkoutFile {
        return WorkoutFile(
            name = "Compact Split Workout",
            description = "Short split-screen fixture.",
            author = "Preview",
            tags = emptyList(),
            steps = listOf(
                Step.SteadyState(
                    durationSec = 300,
                    power = 0.7,
                    cadence = 88,
                ),
            ),
            textEvents = emptyList(),
        )
    }
}

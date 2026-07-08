package io.github.ewoc2026.ewoc

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import io.github.ewoc2026.ewoc.ftms.IndoorBikeData
import io.github.ewoc2026.ewoc.session.release.TrainerControlAuthority
import io.github.ewoc2026.ewoc.session.SessionSample
import io.github.ewoc2026.ewoc.session.SessionSummary
import io.github.ewoc2026.ewoc.workout.ImportedErgoWorkout
import io.github.ewoc2026.ewoc.workout.WorkoutFile
import io.github.ewoc2026.ewoc.workout.runner.RunnerState

/**
 * Explicit stop-flow state to avoid ambiguous combinations of stop-related flags.
 */
enum class StopFlowState {
    IDLE,
    STOPPING_AWAIT_ACK,
}

/**
 * Centralized holder for UI-observable state and related session flags.
 *
 * Keeping these fields grouped allows orchestration code to update a single
 * object instead of scattering mutable references across activity scope.
 */
class AppUiState {
    internal val sessionRuntimeUiState = SessionRuntimeUiState()
    internal val connectionRecoveryUiState = ConnectionRecoveryUiState()
    internal val workoutSelectionUiState = WorkoutSelectionUiState()
    internal val aiAssistantUiState = AiAssistantUiState()
    val screen: MutableState<AppScreen> = mutableStateOf(AppScreen.MENU)
    val heartRate: MutableState<Int?> = mutableStateOf(null)
    val bikeData: MutableState<IndoorBikeData?> = mutableStateOf(null)
    val summary: MutableState<SessionSummary?> = mutableStateOf(null)
    val session: MutableState<SessionState?> = mutableStateOf(null)
    val timelineSamples: MutableState<List<SessionSample>> = mutableStateOf(emptyList())
    val ftmsReady: MutableState<Boolean>
        get() = sessionRuntimeUiState.ftmsReady
    val ftmsControlGranted: MutableState<Boolean>
        get() = sessionRuntimeUiState.ftmsControlGranted
    val lastTargetPower: MutableState<Int?>
        get() = sessionRuntimeUiState.lastTargetPower
    internal val trainerControlAuthority: MutableState<TrainerControlAuthority>
        get() = sessionRuntimeUiState.trainerControlAuthority
    internal val lastAppControlledTargetPower: MutableState<Int?>
        get() = sessionRuntimeUiState.lastAppControlledTargetPower
    val runner: MutableState<RunnerState> = mutableStateOf(RunnerState.stopped())
    val selectedWorkout: MutableState<WorkoutFile?>
        get() = workoutSelectionUiState.selectedWorkoutState
    val selectedSessionSetupMode: MutableState<SessionSetupMode>
        get() = workoutSelectionUiState.selectedSessionSetupModeState
    val selectedImportedWorkout: MutableState<ImportedErgoWorkout?>
        get() = workoutSelectionUiState.selectedImportedWorkoutState
    val selectedWorkoutFileName: MutableState<String?>
        get() = workoutSelectionUiState.selectedWorkoutFileNameState
    val selectedWorkoutStepCount: MutableState<Int?>
        get() = workoutSelectionUiState.selectedWorkoutStepCountState
    val selectedWorkoutPlannedTss: MutableState<Double?>
        get() = workoutSelectionUiState.selectedWorkoutPlannedTssState
    val selectedWorkoutTotalDurationSec: MutableState<Int?>
        get() = workoutSelectionUiState.selectedWorkoutTotalDurationSecState
    val selectedWorkoutImportError: MutableState<String?>
        get() = workoutSelectionUiState.selectedWorkoutImportErrorState
    val workoutExecutionModeMessage: MutableState<String?>
        get() = workoutSelectionUiState.executionModeMessageState
    val workoutExecutionModeIsError: MutableState<Boolean>
        get() = workoutSelectionUiState.executionModeIsErrorState
    val aiMenuAssistantMessage: MutableState<String?>
        get() = aiAssistantUiState.menuMessageState
    val aiMenuAssistantIsError: MutableState<Boolean>
        get() = aiAssistantUiState.menuIsErrorState
    val aiMenuAssistantTemplateKey: MutableState<String?>
        get() = aiAssistantUiState.menuTemplateKeyState
    val aiSessionAssistantMessage: MutableState<String?>
        get() = aiAssistantUiState.sessionMessageState
    val aiSessionAssistantIsError: MutableState<Boolean>
        get() = aiAssistantUiState.sessionIsErrorState
    val aiSummaryAssistantMessage: MutableState<String?>
        get() = aiAssistantUiState.summaryMessageState
    val aiSummaryAssistantIsError: MutableState<Boolean>
        get() = aiAssistantUiState.summaryIsErrorState
    val workoutReady: MutableState<Boolean>
        get() = sessionRuntimeUiState.workoutReady
    val connectionIssueMessage: MutableState<String?>
        get() = connectionRecoveryUiState.connectionIssueMessageState
    val suggestTrainerSearchAfterConnectionIssue: MutableState<Boolean>
        get() = connectionRecoveryUiState.suggestTrainerSearchAfterConnectionIssueState
    val suggestOpenSettingsAfterConnectionIssue: MutableState<Boolean>
        get() = connectionRecoveryUiState.suggestOpenSettingsAfterConnectionIssueState
    val connectingTimeoutMessage: MutableState<String?>
        get() = connectionRecoveryUiState.connectingTimeoutMessageState
    val stopFlowState: MutableState<StopFlowState>
        get() = sessionRuntimeUiState.stopFlowState
    val postWorkoutContinuationHandoffVisible: MutableState<Boolean>
        get() = sessionRuntimeUiState.postWorkoutContinuationHandoffVisible
    val sessionDebugProbeVisible: MutableState<Boolean>
        get() = sessionRuntimeUiState.sessionDebugProbeVisible
    val sessionDebugProbeTitle: MutableState<String?>
        get() = sessionRuntimeUiState.sessionDebugProbeTitle
    val sessionDebugProbeMessage: MutableState<String?>
        get() = sessionRuntimeUiState.sessionDebugProbeMessage
    val sessionDebugProbeLastSignalLabel: MutableState<String?>
        get() = sessionRuntimeUiState.sessionDebugProbeLastSignalLabel
    val sessionDebugProbeLastSignalCount: MutableState<Int>
        get() = sessionRuntimeUiState.sessionDebugProbeLastSignalCount
    val sessionDebugProbeLastSignalAtEpochMs: MutableState<Long?>
        get() = sessionRuntimeUiState.sessionDebugProbeLastSignalAtEpochMs

    var pendingSessionStartAfterPermission: Boolean
        get() = sessionRuntimeUiState.pendingSessionStartAfterPermission
        set(value) {
            sessionRuntimeUiState.pendingSessionStartAfterPermission = value
        }

    var pendingCadenceStartAfterControlGranted: Boolean
        get() = sessionRuntimeUiState.pendingCadenceStartAfterControlGranted
        set(value) {
            sessionRuntimeUiState.pendingCadenceStartAfterControlGranted = value
        }

    var autoPausedByZeroCadence: Boolean
        get() = sessionRuntimeUiState.autoPausedByZeroCadence
        set(value) {
            sessionRuntimeUiState.autoPausedByZeroCadence = value
        }

    var postWorkoutFreerideModeActive: Boolean
        get() = sessionRuntimeUiState.postWorkoutFreerideModeActive
        set(value) {
            sessionRuntimeUiState.postWorkoutFreerideModeActive = value
        }

    var bikeDataLastUpdatedAtEpochMs: Long?
        get() = sessionRuntimeUiState.bikeDataLastUpdatedAtEpochMs
        set(value) {
            sessionRuntimeUiState.bikeDataLastUpdatedAtEpochMs = value
        }

    var heartRateLastUpdatedAtEpochMs: Long?
        get() = sessionRuntimeUiState.heartRateLastUpdatedAtEpochMs
        set(value) {
            sessionRuntimeUiState.heartRateLastUpdatedAtEpochMs = value
        }
}

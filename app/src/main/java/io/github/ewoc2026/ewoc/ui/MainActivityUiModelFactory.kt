package io.github.ewoc2026.ewoc.ui

import io.github.ewoc2026.ewoc.AppScreen
import io.github.ewoc2026.ewoc.BuiltInWorkoutOption
import io.github.ewoc2026.ewoc.DeviceSelectionKind
import io.github.ewoc2026.ewoc.DocumentsFolderWorkoutOption
import io.github.ewoc2026.ewoc.FitExportPreference
import io.github.ewoc2026.ewoc.HrProfileSex
import io.github.ewoc2026.ewoc.MainViewModel
import io.github.ewoc2026.ewoc.ScannedBleDevice
import io.github.ewoc2026.ewoc.SessionSetupMode
import io.github.ewoc2026.ewoc.SessionLifecycleState
import io.github.ewoc2026.ewoc.baseline.BaselineFitnessTestStatus
import io.github.ewoc2026.ewoc.ewoeditor.EwoEditorSnapshot
import io.github.ewoc2026.ewoc.ftms.IndoorBikeData
import io.github.ewoc2026.ewoc.session.SessionPhase
import io.github.ewoc2026.ewoc.session.SessionSample
import io.github.ewoc2026.ewoc.session.SessionSummary
import io.github.ewoc2026.ewoc.workout.ImportedErgoWorkout
import io.github.ewoc2026.ewoc.workout.WorkoutFile
import io.github.ewoc2026.ewoc.workout.runner.RunnerState
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Centralizes top-level UI-model assembly so production and test seams drift in fewer places.
 */
internal object MainActivityUiModelFactory {
    fun create(
        viewModel: MainViewModel,
        currentScreen: AppScreen,
        showMockTrainerControls: Boolean,
    ): MainActivityUiModel {
        val deviceSelectionUiState = viewModel.deviceSelectionUiState
        return create(
            MainActivityUiModelSnapshot(
                currentScreen = currentScreen,
                bikeData = viewModel.uiState.bikeData.value,
                heartRate = viewModel.uiState.heartRate.value,
                logicalDistanceMeters = viewModel.uiState.session.value?.logicalDistanceMeters,
                logicalTotalEnergyKcal = viewModel.uiState.session.value?.logicalTotalEnergyKcal,
                phase = viewModel.phase(),
                sessionLifecycleState = viewModel.sessionLifecycleState(),
                ftmsReady = viewModel.uiState.ftmsReady.value,
                ftmsControlGranted = viewModel.uiState.ftmsControlGranted.value,
                postWorkoutFreerideModeActive = viewModel.uiState.postWorkoutFreerideModeActive,
                autoPausedByZeroCadence = viewModel.uiState.autoPausedByZeroCadence,
                postWorkoutContinuationHandoffVisible = viewModel.uiState.postWorkoutContinuationHandoffVisible.value,
                sessionDebugProbeVisible = viewModel.uiState.sessionDebugProbeVisible.value,
                sessionDebugProbeTitle = viewModel.uiState.sessionDebugProbeTitle.value,
                sessionDebugProbeMessage = viewModel.uiState.sessionDebugProbeMessage.value,
                sessionDebugProbeDiagnostics = viewModel.sessionDebugProbeDiagnosticsForUi(),
                sessionDebugProbeLastSignalLabel = viewModel.uiState.sessionDebugProbeLastSignalLabel.value,
                sessionDebugProbeLastSignalCount = viewModel.uiState.sessionDebugProbeLastSignalCount.value,
                lastTargetPower = viewModel.uiState.lastTargetPower.value,
                runnerState = viewModel.uiState.runner.value,
                sessionDurationSeconds = viewModel.uiState.session.value?.durationSeconds ?: 0,
                timelineSamples = viewModel.uiState.timelineSamples.value,
                summary = viewModel.uiState.summary.value,
                connectingTimeoutMessage = viewModel.uiState.connectingTimeoutMessage.value,
                activeSetupStep = viewModel.activeMenuSetupStepState.value,
                selectedSessionSetupMode = viewModel.uiState.selectedSessionSetupMode.value,
                selectedWorkout = viewModel.uiState.selectedWorkout.value,
                selectedImportedWorkout = viewModel.uiState.selectedImportedWorkout.value,
                selectedWorkoutFileName = viewModel.uiState.selectedWorkoutFileName.value,
                selectedWorkoutStepCount = viewModel.uiState.selectedWorkoutStepCount.value,
                selectedWorkoutPlannedTss = viewModel.uiState.selectedWorkoutPlannedTss.value,
                selectedWorkoutTotalDurationSec = viewModel.uiState.selectedWorkoutTotalDurationSec.value,
                selectedWorkoutImportError = viewModel.uiState.selectedWorkoutImportError.value,
                startEnabled = viewModel.canStartSession(),
                showMockTrainerControls = showMockTrainerControls,
                mockTrainerModeEnabled = viewModel.mockTrainerModeEnabledState.value,
                ftpWatts = viewModel.ftpWattsState.intValue,
                ftpInputText = viewModel.ftpInputTextState.value,
                ftpInputError = viewModel.ftpInputErrorState.value,
                hrProfileAge = viewModel.hrProfileAgeState.value,
                hrProfileAgeInput = viewModel.hrProfileAgeInputState.value,
                hrProfileAgeError = viewModel.hrProfileAgeErrorState.value,
                hrProfileSex = viewModel.hrProfileSexState.value,
                ftmsDeviceName = deviceSelectionUiState.ftmsDevice.displayNameState.value,
                ftmsSelected = viewModel.hasSelectedFtmsDevice(),
                ftmsDeviceReachable = deviceSelectionUiState.ftmsDevice.reachableState.value,
                hrDeviceName = deviceSelectionUiState.hrDevice.displayNameState.value,
                hrSelected = viewModel.hasSelectedHrDevice(),
                hrDeviceConnected = deviceSelectionUiState.hrDevice.connectedState.value,
                hrDeviceReachable = deviceSelectionUiState.hrDevice.reachableState.value,
                workoutExecutionModeMessage = viewModel.uiState.workoutExecutionModeMessage.value,
                workoutExecutionModeIsError = viewModel.uiState.workoutExecutionModeIsError.value,
                aiMenuAssistantMessage = viewModel.uiState.aiMenuAssistantMessage.value,
                aiMenuAssistantIsError = viewModel.uiState.aiMenuAssistantIsError.value,
                aiMenuAssistantTemplateKey = viewModel.uiState.aiMenuAssistantTemplateKey.value,
                aiSessionAssistantMessage = viewModel.uiState.aiSessionAssistantMessage.value,
                aiSessionAssistantIsError = viewModel.uiState.aiSessionAssistantIsError.value,
                aiSummaryAssistantMessage = viewModel.uiState.aiSummaryAssistantMessage.value,
                aiSummaryAssistantIsError = viewModel.uiState.aiSummaryAssistantIsError.value,
                connectionIssueMessage = viewModel.uiState.connectionIssueMessage.value,
                suggestTrainerSearchAfterConnectionIssue = viewModel.uiState.suggestTrainerSearchAfterConnectionIssue.value,
                suggestOpenSettingsAfterConnectionIssue = viewModel.uiState.suggestOpenSettingsAfterConnectionIssue.value,
                activeDeviceSelectionKind = deviceSelectionUiState.activeSelectionKindState.value,
                scannedDevices = deviceSelectionUiState.scannedDevicesState.toList(),
                deviceScanInProgress = deviceSelectionUiState.scanInProgressState.value,
                deviceScanStatus = deviceSelectionUiState.scanStatusState.value,
                deviceScanStopEnabled = deviceSelectionUiState.scanStopEnabledState.value,
                fitExportPreference = viewModel.fitExportPreferenceState.value,
                compatibilityCheckInProgress = viewModel.compatibilityCheckInProgressState.value,
                compatibilityCheckStatusMessage = viewModel.compatibilityCheckStatusMessageState.value,
                documentsFolderReady = viewModel.documentsFolderReadyState.value,
                documentsFolderAccessLost = viewModel.documentsFolderAccessLostState.value,
                documentsFolderSummary = viewModel.documentsFolderSummaryState.value,
                documentsFolderStatusMessage = viewModel.documentsFolderStatusMessageState.value,
                documentsFolderStatusIsError = viewModel.documentsFolderStatusIsErrorState.value,
                documentsFolderWorkoutFiles = viewModel.documentsFolderWorkoutFilesState.toList(),
                builtInWorkoutFiles = viewModel.builtInWorkoutFilesState.toList(),
                ewoEditorSnapshot = viewModel.ewoEditorSnapshotState.value,
                summaryFitExportStatusMessage = viewModel.summaryFitExportStatusMessageState.value,
                summaryFitExportStatusIsError = viewModel.summaryFitExportStatusIsErrorState.value,
                baselineLastFtpWatts = viewModel.baselineLatestResultState.value
                    ?.takeIf { it.status == BaselineFitnessTestStatus.COMPLETED }
                    ?.ftpEstimateWatts,
                baselineLastTestedAt = viewModel.baselineLatestResultState.value
                    ?.takeIf { it.status == BaselineFitnessTestStatus.COMPLETED }
                    ?.completedAt
                    ?.let { instant ->
                        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                            .withZone(ZoneId.systemDefault())
                            .format(instant)
                    },
            ),
        )
    }

    fun create(snapshot: MainActivityUiModelSnapshot): MainActivityUiModel {
        val aiAssistantState = aiAssistantState(snapshot)
        return MainActivityUiModel(
            screen = effectiveScreen(snapshot),
            bikeData = snapshot.bikeData,
            heartRate = snapshot.heartRate,
            logicalDistanceMeters = snapshot.logicalDistanceMeters,
            logicalTotalEnergyKcal = snapshot.logicalTotalEnergyKcal,
            phase = snapshot.phase,
            sessionLifecycleState = snapshot.sessionLifecycleState,
            ftmsReady = snapshot.ftmsReady,
            ftmsControlGranted = snapshot.ftmsControlGranted,
            postWorkoutFreerideModeActive = snapshot.postWorkoutFreerideModeActive,
            autoPausedByZeroCadence = snapshot.autoPausedByZeroCadence,
            lastTargetPower = snapshot.lastTargetPower,
            runnerState = snapshot.runnerState,
            sessionDurationSeconds = snapshot.sessionDurationSeconds,
            summary = snapshot.summary,
            connectingTimeoutMessage = snapshot.connectingTimeoutMessage,
            postWorkoutContinuationHandoffVisible = snapshot.postWorkoutContinuationHandoffVisible,
            sessionDebugProbeVisible = snapshot.sessionDebugProbeVisible,
            sessionDebugProbeTitle = snapshot.sessionDebugProbeTitle,
            sessionDebugProbeMessage = snapshot.sessionDebugProbeMessage,
            sessionDebugProbeDiagnostics = snapshot.sessionDebugProbeDiagnostics,
            sessionDebugProbeReceipt = snapshot.sessionDebugProbeLastSignalLabel?.let { label ->
                "Received: $label #${snapshot.sessionDebugProbeLastSignalCount}"
            },
            menuState = MenuUiState(
                activeSetupStep = snapshot.activeSetupStep,
                selectedSessionSetupMode = snapshot.selectedSessionSetupMode,
                selectedWorkout = snapshot.selectedWorkout,
                selectedImportedWorkout = snapshot.selectedImportedWorkout,
                selectedWorkoutFileName = snapshot.selectedWorkoutFileName,
                selectedWorkoutStepCount = snapshot.selectedWorkoutStepCount,
                selectedWorkoutPlannedTss = snapshot.selectedWorkoutPlannedTss,
                selectedWorkoutTotalDurationSec = snapshot.selectedWorkoutTotalDurationSec,
                selectedWorkoutImportError = snapshot.selectedWorkoutImportError,
                startEnabled = snapshot.startEnabled,
                showMockTrainerControls = snapshot.showMockTrainerControls,
                mockTrainerModeEnabled = snapshot.mockTrainerModeEnabled,
                ftpWatts = snapshot.ftpWatts,
                ftpInputText = snapshot.ftpInputText,
                ftpInputError = snapshot.ftpInputError,
                hrProfileAge = snapshot.hrProfileAge,
                hrProfileAgeInput = snapshot.hrProfileAgeInput,
                hrProfileAgeError = snapshot.hrProfileAgeError,
                hrProfileSex = snapshot.hrProfileSex,
                ftmsDeviceName = snapshot.ftmsDeviceName,
                ftmsSelected = snapshot.ftmsSelected,
                ftmsConnected = snapshot.ftmsReady || snapshot.ftmsDeviceReachable == true,
                ftmsConnectionKnown = snapshot.ftmsReady || snapshot.ftmsDeviceReachable != null,
                hrDeviceName = snapshot.hrDeviceName,
                hrSelected = snapshot.hrSelected,
                hrConnected = snapshot.hrDeviceConnected || snapshot.hrDeviceReachable == true,
                hrConnectionKnown = snapshot.hrDeviceConnected || snapshot.hrDeviceReachable != null,
                workoutExecutionModeMessage = snapshot.workoutExecutionModeMessage,
                workoutExecutionModeIsError = snapshot.workoutExecutionModeIsError,
                aiAssistantMessage = aiAssistantState.message,
                aiAssistantIsError = aiAssistantState.isError,
                aiAssistantTemplateKey = aiAssistantState.templateKey,
                connectionIssueMessage = snapshot.connectionIssueMessage,
                suggestTrainerSearchAfterConnectionIssue = snapshot.suggestTrainerSearchAfterConnectionIssue,
                suggestOpenSettingsAfterConnectionIssue = snapshot.suggestOpenSettingsAfterConnectionIssue,
                activeDeviceSelectionKind = snapshot.activeDeviceSelectionKind,
                scannedDevices = snapshot.scannedDevices,
                deviceScanInProgress = snapshot.deviceScanInProgress,
                deviceScanStatus = snapshot.deviceScanStatus,
                deviceScanStopEnabled = snapshot.deviceScanStopEnabled,
                fitExportPreference = snapshot.fitExportPreference,
                compatibilityCheckInProgress = snapshot.compatibilityCheckInProgress,
                compatibilityCheckStatusMessage = snapshot.compatibilityCheckStatusMessage,
                documentsFolderReady = snapshot.documentsFolderReady,
                documentsFolderAccessLost = snapshot.documentsFolderAccessLost,
                documentsFolderSummary = snapshot.documentsFolderSummary,
                documentsFolderStatusMessage = snapshot.documentsFolderStatusMessage,
                documentsFolderStatusIsError = snapshot.documentsFolderStatusIsError,
                documentsFolderWorkoutFiles = snapshot.documentsFolderWorkoutFiles,
                builtInWorkoutFiles = snapshot.builtInWorkoutFiles,
                baselineLastFtpWatts = snapshot.baselineLastFtpWatts,
                baselineLastTestedAt = snapshot.baselineLastTestedAt,
            ),
            timelineSamples = snapshot.timelineSamples,
            ewoEditorSnapshot = snapshot.ewoEditorSnapshot,
            summaryFitExportStatusMessage = snapshot.summaryFitExportStatusMessage,
            summaryFitExportStatusIsError = snapshot.summaryFitExportStatusIsError,
        )
    }

    private fun aiAssistantState(
        snapshot: MainActivityUiModelSnapshot,
    ): AiAssistantState {
        return if (
            snapshot.currentScreen == AppScreen.MENU ||
            snapshot.currentScreen == AppScreen.EWO_EDITOR ||
            snapshot.currentScreen == AppScreen.CONNECTING ||
            snapshot.currentScreen == AppScreen.BASELINE_FITNESS_TEST
        ) {
            AiAssistantState(
                message = snapshot.aiMenuAssistantMessage,
                isError = snapshot.aiMenuAssistantIsError,
                templateKey = snapshot.aiMenuAssistantTemplateKey,
            )
        } else if (
            snapshot.currentScreen == AppScreen.SESSION ||
            snapshot.currentScreen == AppScreen.STOPPING
        ) {
            AiAssistantState(
                message = snapshot.aiSessionAssistantMessage,
                isError = snapshot.aiSessionAssistantIsError,
                templateKey = null,
            )
        } else {
            AiAssistantState(
                message = snapshot.aiSummaryAssistantMessage,
                isError = snapshot.aiSummaryAssistantIsError,
                templateKey = null,
            )
        }
    }

    private fun effectiveScreen(snapshot: MainActivityUiModelSnapshot): AppScreen {
        if (!snapshot.postWorkoutContinuationHandoffVisible) {
            return snapshot.currentScreen
        }
        return when (snapshot.currentScreen) {
            AppScreen.MENU,
            AppScreen.CONNECTING -> AppScreen.SESSION
            else -> snapshot.currentScreen
        }
    }

    private data class AiAssistantState(
        val message: String?,
        val isError: Boolean,
        val templateKey: String?,
    )
}

internal data class MainActivityUiModelSnapshot(
    val currentScreen: AppScreen,
    val bikeData: IndoorBikeData? = null,
    val heartRate: Int? = null,
    val logicalDistanceMeters: Int? = null,
    val logicalTotalEnergyKcal: Int? = null,
    val phase: SessionPhase = SessionPhase.RUNNING,
    val sessionLifecycleState: SessionLifecycleState = SessionLifecycleState.IDLE,
    val ftmsReady: Boolean = false,
    val ftmsControlGranted: Boolean = false,
    val postWorkoutFreerideModeActive: Boolean = false,
    val autoPausedByZeroCadence: Boolean = false,
    val lastTargetPower: Int? = null,
    val runnerState: RunnerState = RunnerState.stopped(workoutElapsedSec = 0),
    val sessionDurationSeconds: Int = 0,
    val timelineSamples: List<SessionSample> = emptyList(),
    val summary: SessionSummary? = null,
    val connectingTimeoutMessage: String? = null,
    val postWorkoutContinuationHandoffVisible: Boolean = false,
    val sessionDebugProbeVisible: Boolean = false,
    val sessionDebugProbeTitle: String? = null,
    val sessionDebugProbeMessage: String? = null,
    val sessionDebugProbeDiagnostics: String? = null,
    val sessionDebugProbeLastSignalLabel: String? = null,
    val sessionDebugProbeLastSignalCount: Int = 0,
    val activeSetupStep: MenuSetupStep? = null,
    val selectedSessionSetupMode: SessionSetupMode = SessionSetupMode.FILE,
    val selectedWorkout: WorkoutFile? = null,
    val selectedImportedWorkout: ImportedErgoWorkout? = null,
    val selectedWorkoutFileName: String? = null,
    val selectedWorkoutStepCount: Int? = null,
    val selectedWorkoutPlannedTss: Double? = null,
    val selectedWorkoutTotalDurationSec: Int? = null,
    val selectedWorkoutImportError: String? = null,
    val startEnabled: Boolean = false,
    val showMockTrainerControls: Boolean = false,
    val mockTrainerModeEnabled: Boolean = false,
    val ftpWatts: Int = 250,
    val ftpInputText: String = "250",
    val ftpInputError: String? = null,
    val hrProfileAge: Int? = 35,
    val hrProfileAgeInput: String = "35",
    val hrProfileAgeError: String? = null,
    val hrProfileSex: HrProfileSex? = HrProfileSex.MALE,
    val ftmsDeviceName: String = "Trainer",
    val ftmsSelected: Boolean = false,
    val ftmsDeviceReachable: Boolean? = null,
    val hrDeviceName: String = "HR",
    val hrSelected: Boolean = false,
    val hrDeviceConnected: Boolean = false,
    val hrDeviceReachable: Boolean? = null,
    val workoutExecutionModeMessage: String? = null,
    val workoutExecutionModeIsError: Boolean = false,
    val aiMenuAssistantMessage: String? = null,
    val aiMenuAssistantIsError: Boolean = false,
    val aiMenuAssistantTemplateKey: String? = null,
    val aiSessionAssistantMessage: String? = null,
    val aiSessionAssistantIsError: Boolean = false,
    val aiSummaryAssistantMessage: String? = null,
    val aiSummaryAssistantIsError: Boolean = false,
    val connectionIssueMessage: String? = null,
    val suggestTrainerSearchAfterConnectionIssue: Boolean = false,
    val suggestOpenSettingsAfterConnectionIssue: Boolean = false,
    val activeDeviceSelectionKind: DeviceSelectionKind? = null,
    val scannedDevices: List<ScannedBleDevice> = emptyList(),
    val deviceScanInProgress: Boolean = false,
    val deviceScanStatus: String? = null,
    val deviceScanStopEnabled: Boolean = true,
    val fitExportPreference: FitExportPreference? = null,
    val compatibilityCheckInProgress: Boolean = false,
    val compatibilityCheckStatusMessage: String? = null,
    val documentsFolderReady: Boolean = false,
    val documentsFolderAccessLost: Boolean = false,
    val documentsFolderSummary: String? = null,
    val documentsFolderStatusMessage: String? = null,
    val documentsFolderStatusIsError: Boolean = false,
    val documentsFolderWorkoutFiles: List<DocumentsFolderWorkoutOption> = emptyList(),
    val builtInWorkoutFiles: List<BuiltInWorkoutOption> = emptyList(),
    val ewoEditorSnapshot: EwoEditorSnapshot,
    val summaryFitExportStatusMessage: String? = null,
    val summaryFitExportStatusIsError: Boolean = false,
    val baselineLastFtpWatts: Int? = null,
    val baselineLastTestedAt: String? = null,
)

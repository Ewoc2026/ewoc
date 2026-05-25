package com.example.ergometerapp.ui

import androidx.compose.runtime.Composable
import com.example.ergometerapp.DeviceSelectionKind
import com.example.ergometerapp.FitExportPreference
import com.example.ergometerapp.HrProfileSex
import com.example.ergometerapp.SessionSetupMode
import com.example.ergometerapp.workout.ImportedErgoWorkout
import com.example.ergometerapp.workout.WorkoutFile

@Composable
private fun MenuScreenPreviewContent(
    activeDeviceSelectionKind: DeviceSelectionKind?,
    deviceScanInProgress: Boolean,
    deviceScanStatus: String?,
    startEnabled: Boolean,
    connectionIssueMessage: String?,
    suggestTrainerSearchAfterConnectionIssue: Boolean,
    selectedWorkout: WorkoutFile? = previewWorkout(),
    selectedImportedWorkout: ImportedErgoWorkout? = null,
    selectedWorkoutFileName: String = "sweet-spot-builder.zwo",
    selectedWorkoutStepCount: Int? = selectedWorkout?.steps?.size ?: selectedImportedWorkout?.steps?.size,
    selectedWorkoutPlannedTss: Double? = 71.6,
    selectedWorkoutTotalDurationSec: Int? = 3600,
    previewActiveSetupStep: MenuSetupStep? = null,
    selectedSessionSetupMode: SessionSetupMode = SessionSetupMode.FILE,
) {
    ScreenPreviewTheme {
        MenuScreen(
            state = MenuUiState(
                selectedSessionSetupMode = selectedSessionSetupMode,
                selectedWorkoutFileName = selectedWorkoutFileName,
                selectedWorkoutStepCount = selectedWorkoutStepCount,
                selectedWorkoutPlannedTss = selectedWorkoutPlannedTss,
                selectedWorkoutTotalDurationSec = selectedWorkoutTotalDurationSec,
                selectedWorkoutImportError = null,
                selectedWorkout = selectedWorkout,
                selectedImportedWorkout = selectedImportedWorkout,
                ftpWatts = 250,
                ftpInputText = "250",
                ftpInputError = null,
                hrProfileAge = 34,
                hrProfileAgeInput = "34",
                hrProfileAgeError = null,
                hrProfileSex = HrProfileSex.MALE,
                ftmsDeviceName = "Wahoo KICKR Core",
                ftmsSelected = startEnabled,
                ftmsConnected = startEnabled,
                ftmsConnectionKnown = true,
                hrDeviceName = "Polar H10",
                hrSelected = true,
                hrConnected = true,
                hrConnectionKnown = true,
                workoutExecutionModeMessage = "ERG mode will follow the workout targets.",
                workoutExecutionModeIsError = false,
                aiAssistantMessage = "Trainer looks ready. Start when you are clipped in.",
                aiAssistantIsError = false,
                aiAssistantTemplateKey = null,
                connectionIssueMessage = connectionIssueMessage,
                suggestTrainerSearchAfterConnectionIssue = suggestTrainerSearchAfterConnectionIssue,
                suggestOpenSettingsAfterConnectionIssue = false,
                activeDeviceSelectionKind = activeDeviceSelectionKind,
                scannedDevices = previewScannedDevices(),
                deviceScanInProgress = deviceScanInProgress,
                deviceScanStatus = deviceScanStatus,
                deviceScanStopEnabled = deviceScanInProgress,
                startEnabled = startEnabled,
                showMockTrainerControls = true,
                mockTrainerModeEnabled = false,
                fitExportPreference = FitExportPreference.ASK_EVERY_TIME,
                compatibilityCheckInProgress = false,
                compatibilityCheckStatusMessage = null,
                documentsFolderReady = true,
                documentsFolderAccessLost = false,
                documentsFolderSummary = "Workouts/Structured",
                documentsFolderStatusMessage = null,
                documentsFolderStatusIsError = false,
                documentsFolderWorkoutFiles = previewDocumentsFolderWorkouts(),
                builtInWorkoutFiles = previewBuiltInWorkouts(),
            ),
            onSelectWorkoutFile = {},
            onChooseDocumentsFolder = {},
            onRefreshDocumentsFolderWorkouts = {},
            onSelectDocumentsFolderWorkout = {},
            onSelectBuiltInWorkout = {},
            onOpenBuiltInWorkoutInEditor = {},
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
            onMockTrainerModeChanged = {},
            onFitExportPreferenceSelected = {},
            onQuitApp = {},
            onRunCompatibilityCheck = {},
            onStartSession = {},
            previewActiveSetupStep = previewActiveSetupStep,
        )
    }
}

@DestinationScreenPreviews
@Composable
private fun MenuScreenReadyPreview() {
    MenuScreenPreviewContent(
        activeDeviceSelectionKind = null,
        deviceScanInProgress = false,
        deviceScanStatus = null,
        startEnabled = true,
        connectionIssueMessage = null,
        suggestTrainerSearchAfterConnectionIssue = false,
    )
}

@DestinationScreenPreviews
@Composable
private fun MenuScreenDevicePickerPreview() {
    MenuScreenPreviewContent(
        activeDeviceSelectionKind = DeviceSelectionKind.FTMS,
        deviceScanInProgress = true,
        deviceScanStatus = "Scanning for trainers",
        startEnabled = false,
        connectionIssueMessage = "The previous trainer connection dropped. Pick a trainer again.",
        suggestTrainerSearchAfterConnectionIssue = true,
        previewActiveSetupStep = MenuSetupStep.DEVICES,
    )
}

@DestinationScreenPreviews
@Composable
private fun MenuScreenProfileStepPreview() {
    MenuScreenPreviewContent(
        activeDeviceSelectionKind = null,
        deviceScanInProgress = false,
        deviceScanStatus = null,
        startEnabled = true,
        connectionIssueMessage = null,
        suggestTrainerSearchAfterConnectionIssue = false,
        previewActiveSetupStep = MenuSetupStep.PROFILE,
    )
}

@DestinationScreenPreviews
@Composable
private fun MenuScreenFileModeStepPreview() {
    MenuScreenPreviewContent(
        activeDeviceSelectionKind = null,
        deviceScanInProgress = false,
        deviceScanStatus = null,
        startEnabled = true,
        connectionIssueMessage = null,
        suggestTrainerSearchAfterConnectionIssue = false,
        previewActiveSetupStep = MenuSetupStep.FILE_BASED,
    )
}

@DestinationScreenPreviews
@Composable
private fun MenuScreenTelemetryOnlyModeStepPreview() {
    MenuScreenPreviewContent(
        activeDeviceSelectionKind = null,
        deviceScanInProgress = false,
        deviceScanStatus = null,
        startEnabled = true,
        connectionIssueMessage = null,
        suggestTrainerSearchAfterConnectionIssue = false,
        selectedWorkout = null,
        selectedWorkoutFileName = "",
        selectedWorkoutStepCount = null,
        selectedWorkoutPlannedTss = null,
        selectedWorkoutTotalDurationSec = null,
        previewActiveSetupStep = MenuSetupStep.FILE_BASED,
        selectedSessionSetupMode = SessionSetupMode.TELEMETRY_ONLY,
    )
}

@DestinationScreenPreviews
@Composable
private fun MenuScreenSummaryStepPreview() {
    MenuScreenPreviewContent(
        activeDeviceSelectionKind = null,
        deviceScanInProgress = false,
        deviceScanStatus = null,
        startEnabled = true,
        connectionIssueMessage = null,
        suggestTrainerSearchAfterConnectionIssue = false,
        previewActiveSetupStep = MenuSetupStep.SUMMARY,
    )
}

@DestinationScreenPreviews
@Composable
private fun MenuScreenTelemetryOnlyReadyPreview() {
    MenuScreenPreviewContent(
        activeDeviceSelectionKind = null,
        deviceScanInProgress = false,
        deviceScanStatus = null,
        startEnabled = true,
        connectionIssueMessage = null,
        suggestTrainerSearchAfterConnectionIssue = false,
        selectedWorkout = null,
        selectedWorkoutFileName = "",
        selectedWorkoutStepCount = null,
        selectedWorkoutPlannedTss = null,
        selectedWorkoutTotalDurationSec = null,
        previewActiveSetupStep = MenuSetupStep.SUMMARY,
        selectedSessionSetupMode = SessionSetupMode.TELEMETRY_ONLY,
    )
}

@DestinationScreenPreviews
@Composable
private fun MenuScreenImportedErgoPreview() {
    val importedWorkout = previewImportedErgoWorkout()
    MenuScreenPreviewContent(
        activeDeviceSelectionKind = null,
        deviceScanInProgress = false,
        deviceScanStatus = null,
        startEnabled = true,
        connectionIssueMessage = null,
        suggestTrainerSearchAfterConnectionIssue = false,
        selectedWorkout = null,
        selectedImportedWorkout = importedWorkout,
        selectedWorkoutFileName = "imported_ewo_validation_power.ewo",
        selectedWorkoutStepCount = importedWorkout.steps.size,
        selectedWorkoutPlannedTss = 48.2,
        previewActiveSetupStep = MenuSetupStep.FILE_BASED,
    )
}

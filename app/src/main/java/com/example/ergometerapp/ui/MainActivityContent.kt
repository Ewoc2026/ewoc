package com.example.ergometerapp.ui

import androidx.compose.runtime.Composable
import com.example.ergometerapp.AppScreen
import com.example.ergometerapp.FitExportPreference
import com.example.ergometerapp.HrProfileSex
import com.example.ergometerapp.ScannedBleDevice
import com.example.ergometerapp.SessionDebugProbeSignal
import com.example.ergometerapp.SessionSetupMode
import com.example.ergometerapp.SessionLifecycleState
import com.example.ergometerapp.baseline.BaselineFitnessTestRuntimeSnapshot
import com.example.ergometerapp.ewoeditor.EwoEditorSnapshot
import com.example.ergometerapp.ftms.IndoorBikeData
import com.example.ergometerapp.session.SessionPhase
import com.example.ergometerapp.session.SessionSample
import com.example.ergometerapp.session.SessionSummary
import com.example.ergometerapp.ui.theme.EwocTheme
import com.example.ergometerapp.workout.runner.RunnerState

/**
 * Immutable model used by [MainActivityContent] to render top-level destinations.
 */
internal data class MainActivityUiModel(
    val screen: AppScreen,
    val bikeData: IndoorBikeData?,
    val heartRate: Int?,
    val logicalDistanceMeters: Int?,
    val logicalTotalEnergyKcal: Int?,
    val phase: SessionPhase,
    val sessionLifecycleState: SessionLifecycleState = SessionLifecycleState.IDLE,
    val ftmsReady: Boolean,
    val ftmsControlGranted: Boolean,
    val postWorkoutFreerideModeActive: Boolean,
    val lastTargetPower: Int?,
    val runnerState: RunnerState,
    val sessionDurationSeconds: Int = 0,
    val summary: SessionSummary?,
    val connectingTimeoutMessage: String?,
    val postWorkoutContinuationHandoffVisible: Boolean = false,
    val sessionDebugProbeVisible: Boolean = false,
    val sessionDebugProbeTitle: String? = null,
    val sessionDebugProbeMessage: String? = null,
    val sessionDebugProbeDiagnostics: String? = null,
    val sessionDebugProbeReceipt: String? = null,
    val menuState: MenuUiState,
    val ewoEditorSnapshot: EwoEditorSnapshot,
    val timelineSamples: List<SessionSample> = emptyList(),
    val summaryFitExportStatusMessage: String?,
    val summaryFitExportStatusIsError: Boolean,
)

/**
 * Renders the application root destinations.
 */
@Composable
internal fun MainActivityContent(
    model: MainActivityUiModel,
    onSelectWorkoutFile: () -> Unit,
    onChooseDocumentsFolder: () -> Unit = {},
    onRefreshDocumentsFolderWorkouts: () -> Unit = {},
    onSelectDocumentsFolderWorkout: (String) -> Unit = {},
    onSelectBuiltInWorkout: (String) -> Unit = {},
    onOpenBuiltInWorkoutInEditor: (String) -> Unit = {},
    onFtpInputChanged: (String) -> Unit,
    onHrProfileAgeInputChanged: (String) -> Unit,
    onHrProfileSexSelected: (HrProfileSex) -> Unit,
    onSearchFtmsDevices: () -> Unit,
    onSearchHrDevices: () -> Unit,
    onScannedDeviceSelected: (ScannedBleDevice) -> Unit,
    onDismissDeviceSelection: () -> Unit,
    onDismissConnectionIssue: () -> Unit,
    onSearchFtmsDevicesFromConnectionIssue: () -> Unit,
    onOpenAppSettingsFromConnectionIssue: () -> Unit,
    onAiMenuAssistantAction: (String) -> Boolean,
    onConnectingTimeoutKeepWaiting: () -> Unit = {},
    onConnectingTimeoutRetry: () -> Unit = {},
    onConnectingTimeoutBackToMenu: () -> Unit = {},
    onMockTrainerModeChanged: (Boolean) -> Unit = {},
    onQuitApp: () -> Unit = {},
    onStartSession: () -> Unit,
    onEndSession: () -> Unit,
    onWorkoutCompletePresented: () -> Unit = {},
    onContinueRideAfterWorkoutComplete: () -> Unit = {},
    onEndSessionAfterWorkoutComplete: () -> Unit = {},
    onSessionDebugProbeSignal: (SessionDebugProbeSignal) -> Unit = {},
    onBackToMenu: () -> Unit,
    onEwoEditorAction: (EwoEditorScreenAction) -> Unit = {},
    onOpenEwoEditor: () -> Unit = {},
    onOpenEwoFile: () -> Unit = {},
    onSaveEwoFile: () -> Unit = {},
    onRequestSummaryFitExport: () -> Unit,
    onRequestSummaryFitShare: () -> Unit = {},
    onRequestSummaryFitAutoExport: () -> Unit = {},
    onSummaryFitExportPreferenceSelected: (FitExportPreference) -> Unit = {},
    onRunCompatibilityCheck: () -> Unit = {},
    onSelectStarterWorkout: () -> Unit = {},
    onSelectTelemetryOnlyMode: () -> Unit = {},
    onSessionSetupModeSelected: (SessionSetupMode) -> Unit = {},
    onActiveMenuSetupStepChanged: (MenuSetupStep?) -> Unit = {},
    onOpenBaselineFitnessTest: () -> Unit = {},
    baselineFitnessTestRuntimeSnapshot: BaselineFitnessTestRuntimeSnapshot = BaselineFitnessTestRuntimeSnapshot(),
    onStartBaselineFitnessTest: () -> Unit = {},
    onStopBaselineFitnessTest: () -> Unit = {},
    onCancelBaselineFitnessTest: () -> Unit = {},
    onAcceptBaselineFitnessTestAdvisoryFallback: () -> Unit = {},
    onDeclineBaselineFitnessTestAdvisoryFallback: () -> Unit = {},
    onSkipBaselineFitnessTestCooldown: () -> Unit = {},
    onBackFromBaselineFitnessTest: () -> Unit = {},
) {
    EwocTheme {
        MainDestinationContent(
            model = model,
            onSelectWorkoutFile = onSelectWorkoutFile,
            onChooseDocumentsFolder = onChooseDocumentsFolder,
            onRefreshDocumentsFolderWorkouts = onRefreshDocumentsFolderWorkouts,
            onSelectDocumentsFolderWorkout = onSelectDocumentsFolderWorkout,
            onSelectBuiltInWorkout = onSelectBuiltInWorkout,
            onOpenBuiltInWorkoutInEditor = onOpenBuiltInWorkoutInEditor,
            onFtpInputChanged = onFtpInputChanged,
            onHrProfileAgeInputChanged = onHrProfileAgeInputChanged,
            onHrProfileSexSelected = onHrProfileSexSelected,
            onSearchFtmsDevices = onSearchFtmsDevices,
            onSearchHrDevices = onSearchHrDevices,
            onScannedDeviceSelected = onScannedDeviceSelected,
            onDismissDeviceSelection = onDismissDeviceSelection,
            onDismissConnectionIssue = onDismissConnectionIssue,
            onSearchFtmsDevicesFromConnectionIssue = onSearchFtmsDevicesFromConnectionIssue,
            onOpenAppSettingsFromConnectionIssue = onOpenAppSettingsFromConnectionIssue,
            onAiMenuAssistantAction = onAiMenuAssistantAction,
            onConnectingTimeoutKeepWaiting = onConnectingTimeoutKeepWaiting,
            onConnectingTimeoutRetry = onConnectingTimeoutRetry,
            onConnectingTimeoutBackToMenu = onConnectingTimeoutBackToMenu,
            onMockTrainerModeChanged = onMockTrainerModeChanged,
            onQuitApp = onQuitApp,
            onStartSession = onStartSession,
            onEndSession = onEndSession,
            onWorkoutCompletePresented = onWorkoutCompletePresented,
            onContinueRideAfterWorkoutComplete = onContinueRideAfterWorkoutComplete,
            onEndSessionAfterWorkoutComplete = onEndSessionAfterWorkoutComplete,
            onSessionDebugProbeSignal = onSessionDebugProbeSignal,
            onBackToMenu = onBackToMenu,
            onEwoEditorAction = onEwoEditorAction,
            onOpenEwoEditor = onOpenEwoEditor,
            onOpenEwoFile = onOpenEwoFile,
            onSaveEwoFile = onSaveEwoFile,
            onRequestSummaryFitExport = onRequestSummaryFitExport,
            onRequestSummaryFitShare = onRequestSummaryFitShare,
            onRequestSummaryFitAutoExport = onRequestSummaryFitAutoExport,
            onSummaryFitExportPreferenceSelected = onSummaryFitExportPreferenceSelected,
            onRunCompatibilityCheck = onRunCompatibilityCheck,
            onSelectStarterWorkout = onSelectStarterWorkout,
            onSelectTelemetryOnlyMode = onSelectTelemetryOnlyMode,
            onSessionSetupModeSelected = onSessionSetupModeSelected,
            onActiveMenuSetupStepChanged = onActiveMenuSetupStepChanged,
            onOpenBaselineFitnessTest = onOpenBaselineFitnessTest,
            baselineFitnessTestRuntimeSnapshot = baselineFitnessTestRuntimeSnapshot,
            onStartBaselineFitnessTest = onStartBaselineFitnessTest,
            onStopBaselineFitnessTest = onStopBaselineFitnessTest,
            onCancelBaselineFitnessTest = onCancelBaselineFitnessTest,
            onAcceptBaselineFitnessTestAdvisoryFallback = onAcceptBaselineFitnessTestAdvisoryFallback,
            onDeclineBaselineFitnessTestAdvisoryFallback = onDeclineBaselineFitnessTestAdvisoryFallback,
            onSkipBaselineFitnessTestCooldown = onSkipBaselineFitnessTestCooldown,
            onBackFromBaselineFitnessTest = onBackFromBaselineFitnessTest,
        )
    }
}

@Composable
private fun MainDestinationContent(
    model: MainActivityUiModel,
    onSelectWorkoutFile: () -> Unit,
    onChooseDocumentsFolder: () -> Unit = {},
    onRefreshDocumentsFolderWorkouts: () -> Unit = {},
    onSelectDocumentsFolderWorkout: (String) -> Unit = {},
    onSelectBuiltInWorkout: (String) -> Unit = {},
    onOpenBuiltInWorkoutInEditor: (String) -> Unit = {},
    onFtpInputChanged: (String) -> Unit,
    onHrProfileAgeInputChanged: (String) -> Unit,
    onHrProfileSexSelected: (HrProfileSex) -> Unit,
    onSearchFtmsDevices: () -> Unit,
    onSearchHrDevices: () -> Unit,
    onScannedDeviceSelected: (ScannedBleDevice) -> Unit,
    onDismissDeviceSelection: () -> Unit,
    onDismissConnectionIssue: () -> Unit,
    onSearchFtmsDevicesFromConnectionIssue: () -> Unit,
    onOpenAppSettingsFromConnectionIssue: () -> Unit,
    onAiMenuAssistantAction: (String) -> Boolean,
    onConnectingTimeoutKeepWaiting: () -> Unit = {},
    onConnectingTimeoutRetry: () -> Unit = {},
    onConnectingTimeoutBackToMenu: () -> Unit = {},
    onMockTrainerModeChanged: (Boolean) -> Unit = {},
    onQuitApp: () -> Unit = {},
    onStartSession: () -> Unit,
    onEndSession: () -> Unit,
    onWorkoutCompletePresented: () -> Unit = {},
    onContinueRideAfterWorkoutComplete: () -> Unit = {},
    onEndSessionAfterWorkoutComplete: () -> Unit = {},
    onSessionDebugProbeSignal: (SessionDebugProbeSignal) -> Unit = {},
    onBackToMenu: () -> Unit,
    onEwoEditorAction: (EwoEditorScreenAction) -> Unit = {},
    onOpenEwoEditor: () -> Unit = {},
    onOpenEwoFile: () -> Unit = {},
    onSaveEwoFile: () -> Unit = {},
    onRequestSummaryFitExport: () -> Unit,
    onRequestSummaryFitShare: () -> Unit = {},
    onRequestSummaryFitAutoExport: () -> Unit = {},
    onSummaryFitExportPreferenceSelected: (FitExportPreference) -> Unit = {},
    onRunCompatibilityCheck: () -> Unit = {},
    onSelectStarterWorkout: () -> Unit = {},
    onSelectTelemetryOnlyMode: () -> Unit = {},
    onSessionSetupModeSelected: (SessionSetupMode) -> Unit = {},
    onActiveMenuSetupStepChanged: (MenuSetupStep?) -> Unit = {},
    onOpenBaselineFitnessTest: () -> Unit = {},
    baselineFitnessTestRuntimeSnapshot: BaselineFitnessTestRuntimeSnapshot = BaselineFitnessTestRuntimeSnapshot(),
    onStartBaselineFitnessTest: () -> Unit = {},
    onStopBaselineFitnessTest: () -> Unit = {},
    onCancelBaselineFitnessTest: () -> Unit = {},
    onAcceptBaselineFitnessTestAdvisoryFallback: () -> Unit = {},
    onDeclineBaselineFitnessTestAdvisoryFallback: () -> Unit = {},
    onSkipBaselineFitnessTestCooldown: () -> Unit = {},
    onBackFromBaselineFitnessTest: () -> Unit = {},
) {
    when {
        model.screen == AppScreen.MENU -> {
            MenuScreen(
                state = model.menuState,
                onSelectWorkoutFile = onSelectWorkoutFile,
                onChooseDocumentsFolder = onChooseDocumentsFolder,
                onRefreshDocumentsFolderWorkouts = onRefreshDocumentsFolderWorkouts,
                onSelectDocumentsFolderWorkout = onSelectDocumentsFolderWorkout,
                onSelectBuiltInWorkout = onSelectBuiltInWorkout,
                onOpenBuiltInWorkoutInEditor = onOpenBuiltInWorkoutInEditor,
                onFtpInputChanged = onFtpInputChanged,
                onHrProfileAgeInputChanged = onHrProfileAgeInputChanged,
                onHrProfileSexSelected = onHrProfileSexSelected,
                onSearchFtmsDevices = onSearchFtmsDevices,
                onSearchHrDevices = onSearchHrDevices,
                onScannedDeviceSelected = onScannedDeviceSelected,
                onDismissDeviceSelection = onDismissDeviceSelection,
                onDismissConnectionIssue = onDismissConnectionIssue,
                onSearchFtmsDevicesFromConnectionIssue = onSearchFtmsDevicesFromConnectionIssue,
                onOpenAppSettingsFromConnectionIssue = onOpenAppSettingsFromConnectionIssue,
                onAiMenuAssistantAction = onAiMenuAssistantAction,
                onMockTrainerModeChanged = onMockTrainerModeChanged,
                onQuitApp = onQuitApp,
                onFitExportPreferenceSelected = onSummaryFitExportPreferenceSelected,
                onRunCompatibilityCheck = onRunCompatibilityCheck,
                onStartSession = onStartSession,
                onSelectStarterWorkout = onSelectStarterWorkout,
                onSelectTelemetryOnlyMode = onSelectTelemetryOnlyMode,
                onSessionSetupModeSelected = onSessionSetupModeSelected,
                onActiveSetupStepChanged = onActiveMenuSetupStepChanged,
                onOpenBaselineFitnessTest = onOpenBaselineFitnessTest,
                onOpenEwoEditor = onOpenEwoEditor,
            )
        }

        model.screen == AppScreen.EWO_EDITOR -> {
            EwoEditorScreen(
                snapshot = model.ewoEditorSnapshot,
                onAction = { action ->
                    when (action) {
                        is EwoEditorScreenAction.OpenFile -> onOpenEwoFile()
                        is EwoEditorScreenAction.SaveFile -> onSaveEwoFile()
                        else -> onEwoEditorAction(action)
                    }
                },
            )
        }

        model.screen == AppScreen.CONNECTING -> {
            ConnectingScreen(
                timeoutMessage = model.connectingTimeoutMessage,
                onKeepWaiting = onConnectingTimeoutKeepWaiting,
                onRetry = onConnectingTimeoutRetry,
                onBackToMenu = onConnectingTimeoutBackToMenu,
            )
        }

        model.screen == AppScreen.STOPPING -> {
            StoppingScreen()
        }

        model.screen == AppScreen.SESSION -> {
            SessionScreen(
                phase = model.phase,
                bikeData = model.bikeData,
                heartRate = model.heartRate,
                logicalDistanceMeters = model.logicalDistanceMeters,
                logicalTotalEnergyKcal = model.logicalTotalEnergyKcal,
                ftmsReady = model.ftmsReady,
                ftmsControlGranted = model.ftmsControlGranted,
                postWorkoutFreerideModeActive = model.postWorkoutFreerideModeActive,
                selectedSessionSetupMode = model.menuState.selectedSessionSetupMode,
                selectedWorkout = model.menuState.selectedWorkout,
                selectedImportedWorkout = model.menuState.selectedImportedWorkout,
                selectedWorkoutFileName = model.menuState.selectedWorkoutFileName,
                ftpWatts = model.menuState.ftpWatts,
                runnerState = model.runnerState,
                sessionDurationSeconds = model.sessionDurationSeconds,
                hrProfileAge = model.menuState.hrProfileAge,
                hrProfileSex = model.menuState.hrProfileSex,
                lastTargetPower = model.lastTargetPower,
                workoutExecutionModeMessage = model.menuState.workoutExecutionModeMessage,
                workoutExecutionModeIsError = model.menuState.workoutExecutionModeIsError,
                aiAssistantMessage = model.menuState.aiAssistantMessage,
                aiAssistantIsError = model.menuState.aiAssistantIsError,
                mockTrainerModeEnabled = model.menuState.mockTrainerModeEnabled,
                timelineSamples = model.timelineSamples,
                isProEntitled = true,
                postWorkoutContinuationHandoffVisible = model.postWorkoutContinuationHandoffVisible,
                sessionDebugProbeVisible = model.sessionDebugProbeVisible,
                sessionDebugProbeTitle = model.sessionDebugProbeTitle,
                sessionDebugProbeMessage = model.sessionDebugProbeMessage,
                sessionDebugProbeDiagnostics = model.sessionDebugProbeDiagnostics,
                sessionDebugProbeReceipt = model.sessionDebugProbeReceipt,
                onEndSession = onEndSession,
                onWorkoutCompletePresented = onWorkoutCompletePresented,
                onContinueRideAfterWorkoutComplete = onContinueRideAfterWorkoutComplete,
                onEndSessionAfterWorkoutComplete = onEndSessionAfterWorkoutComplete,
                onSessionDebugProbeSignal = onSessionDebugProbeSignal,
            )
        }

        model.screen == AppScreen.SUMMARY -> {
            SummaryScreen(
                summary = model.summary,
                fitExportStatusMessage = model.summaryFitExportStatusMessage,
                fitExportStatusIsError = model.summaryFitExportStatusIsError,
                fitExportPreference = model.menuState.fitExportPreference,
                aiAssistantMessage = model.menuState.aiAssistantMessage,
                aiAssistantIsError = model.menuState.aiAssistantIsError,
                onRequestFitExport = onRequestSummaryFitExport,
                onRequestFitShare = onRequestSummaryFitShare,
                onRequestFitAutoExport = onRequestSummaryFitAutoExport,
                onFitExportPreferenceSelected = onSummaryFitExportPreferenceSelected,
                onBackToMenu = onBackToMenu,
            )
        }

        else -> {
            BaselineFitnessTestScreen(
                snapshot = baselineFitnessTestRuntimeSnapshot,
                onStart = onStartBaselineFitnessTest,
                onStop = onStopBaselineFitnessTest,
                onCancel = onCancelBaselineFitnessTest,
                onAcceptAdvisoryFallback = onAcceptBaselineFitnessTestAdvisoryFallback,
                onDeclineAdvisoryFallback = onDeclineBaselineFitnessTestAdvisoryFallback,
                onSkipCooldown = onSkipBaselineFitnessTestCooldown,
                onBack = onBackFromBaselineFitnessTest,
            )
        }
    }

    if (shouldRenderGlobalSessionDebugProbe(model.screen, model.sessionDebugProbeVisible)) {
        SessionDebugProbeOverlayHost(
            title = model.sessionDebugProbeTitle,
            message = model.sessionDebugProbeMessage,
            diagnostics = model.sessionDebugProbeDiagnostics,
            lastSignalReceipt = model.sessionDebugProbeReceipt,
            onSignal = onSessionDebugProbeSignal,
        )
    }
}

internal fun shouldRenderGlobalSessionDebugProbe(
    screen: AppScreen,
    sessionDebugProbeVisible: Boolean,
): Boolean = sessionDebugProbeVisible && screen != AppScreen.SESSION

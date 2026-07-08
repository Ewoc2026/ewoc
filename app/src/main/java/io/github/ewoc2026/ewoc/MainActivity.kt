package io.github.ewoc2026.ewoc

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.core.view.WindowCompat
import io.github.ewoc2026.ewoc.ui.MainActivityUiModelFactory
import io.github.ewoc2026.ewoc.ui.MainActivityContent

/**
 * App entry point that binds lifecycle/permissions to UI and orchestration services.
 */
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private val safUtf8TextWriter: SafUtf8TextWriter by lazy {
        SafUtf8TextWriter { uri, mode -> contentResolver.openOutputStream(uri, mode) }
    }
    private val workoutImportMimeTypes: Array<String> by lazy {
        SafFileNamePolicy.workoutImportPickerMimeTypes()
    }
    private val isInstrumentationTestRuntime: Boolean by lazy {
        runCatching { Class.forName("androidx.test.platform.app.InstrumentationRegistry") }.isSuccess
    }
    private val documentPickerLaunchGate = MainActivityDocumentPickerLaunchGate(
        log = { message -> Log.d("SAF", message) },
    )
    private val documentFlowCoordinator: MainActivityDocumentFlowCoordinator by lazy {
        MainActivityDocumentFlowCoordinator(
            launchEwoSaveDocument = { fileName ->
                documentPickerLaunchGate.tryLaunch("saveEwoFile") {
                    saveEwoFile.launch(fileName)
                }
            },
            prepareSessionFitExport = viewModel::prepareSessionFitExport,
            tryExportPendingSessionFitToDocumentsFolder = viewModel::tryExportPendingSessionFitToDocumentsFolder,
            launchSessionFitExportDocument = { fileName ->
                documentPickerLaunchGate.tryLaunch("exportSessionFitFile") {
                    exportSessionFitFile.launch(fileName)
                }
            },
        )
    }
    private val debugIntentCoordinator: MainActivityDebugIntentCoordinator by lazy {
        MainActivityDebugIntentCoordinator(
            debugBuildEnabled = BuildConfig.DEBUG,
            executeSessionValidationCommand = viewModel::onDebugSessionValidationCommand,
            executeAutomationCommand = viewModel::onDebugAutomationCommand,
            executeMockTrainerAutomationCommand = viewModel::onDebugMockTrainerAutomationCommand,
            logValidationStatus = { status -> Log.i("DEBUG_VALIDATION", status) },
        )
    }
    private val summaryFitShareCoordinator: MainActivitySummaryFitShareCoordinator by lazy {
        MainActivitySummaryFitShareCoordinator(
            prepareShareIntent = viewModel::prepareSessionFitShareIntent,
            launchActivity = ::startActivity,
            onLaunchFailed = viewModel::onSessionFitShareLaunchFailed,
            logLaunchFailure = { error ->
                Log.w("MainActivity", "Session FIT share launch failed", error)
            },
            log = { message -> Log.d("SummaryFitShare", message) },
        )
    }
    private val ewoDocumentCoordinator: MainActivityEwoDocumentCoordinator by lazy {
        MainActivityEwoDocumentCoordinator(
            readDocumentUtf8 = { uri ->
                contentResolver.openInputStream(uri)
                    ?.bufferedReader(Charsets.UTF_8)
                    ?.use { reader -> reader.readText() }
            },
            onOpenDocumentLoaded = viewModel::onEwoEditorOpenFileResult,
            onOpenError = { message -> viewModel.onEwoEditorOpenError(message) },
            prepareExportJson = viewModel::getEwoEditorExportJson,
            writeDocumentUtf8 = safUtf8TextWriter::write,
            onSaveCompleted = { fileName ->
                viewModel.ewoEditorCoordinator.completeSave(fileName)
                viewModel.ewoEditorSnapshotState.value = viewModel.ewoEditorCoordinator.snapshot()
            },
        )
    }
    private val permissionCoordinator: MainActivityPermissionCoordinator by lazy {
        MainActivityPermissionCoordinator(
            isPermissionGranted = { permission ->
                checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
            },
            isInstrumentationTestRuntime = isInstrumentationTestRuntime,
            requestBluetoothConnectPermission = requestBluetoothConnectPermission::launch,
            requestBluetoothScanPermission = requestBluetoothScanPermission::launch,
            launchActivity = ::startActivity,
            packageNameProvider = { packageName },
        )
    }
    private val lifecycleCoordinator: MainActivityLifecycleCoordinator by lazy {
        MainActivityLifecycleCoordinator(
            unbindActivityCallbacks = viewModel::unbindActivityCallbacks,
            stopAndClose = viewModel::stopAndClose,
        )
    }
    private val screenWakeCoordinator: MainActivityScreenWakeCoordinator by lazy {
        MainActivityScreenWakeCoordinator(
            addWindowFlags = window::addFlags,
            clearWindowFlags = window::clearFlags,
        )
    }
    private val systemUiCoordinator = MainActivitySystemUiCoordinator()

    private val requestBluetoothConnectPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            viewModel.onBluetoothPermissionResult(granted, viewModel.pendingBluetoothPermissionRequestId)
        }
    private val requestBluetoothScanPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            viewModel.onBluetoothScanPermissionResult(granted)
        }
    private val selectWorkoutFile =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            documentPickerLaunchGate.onResultDelivered("selectWorkoutFile")
            viewModel.onWorkoutFileSelected(uri)
        }
    private val selectDocumentsFolder =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            documentPickerLaunchGate.onResultDelivered("selectDocumentsFolder")
            viewModel.onDocumentsFolderSelected(uri)
        }
    private val openEwoFile =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            documentPickerLaunchGate.onResultDelivered("openEwoFile")
            ewoDocumentCoordinator.handleOpenResult(uri)
        }
    private val saveEwoFile =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
            documentPickerLaunchGate.onResultDelivered("saveEwoFile")
            ewoDocumentCoordinator.handleSaveResult(uri)
        }
    private val exportSessionFitFile =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
            documentPickerLaunchGate.onResultDelivered("exportSessionFitFile")
            viewModel.onSessionFitExportTargetSelected(uri)
        }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        viewModel.bindActivityCallbacks(
            ensureBluetoothConnectPermission = permissionCoordinator::ensureBluetoothConnectPermission,
            ensureBluetoothScanPermission = permissionCoordinator::ensureBluetoothScanPermission,
            keepScreenOn = screenWakeCoordinator::keepScreenOn,
            allowScreenOff = screenWakeCoordinator::allowScreenOff,
        )
        debugIntentCoordinator.handle(intent)

        setContent {
            val currentScreen = viewModel.uiState.screen.value
            LaunchedEffect(currentScreen) {
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                systemUiCoordinator.applyForScreen(
                    currentScreen = currentScreen,
                    setSystemBarsBehavior = { behavior ->
                        insetsController.systemBarsBehavior = behavior
                    },
                    hideInsets = insetsController::hide,
                    showInsets = insetsController::show,
                )
            }
            MainActivityContent(
                model = MainActivityUiModelFactory.create(
                    viewModel = viewModel,
                    currentScreen = currentScreen,
                    showMockTrainerControls = BuildConfig.DEBUG,
                ),
                onSelectWorkoutFile = {
                    documentPickerLaunchGate.tryLaunch("selectWorkoutFile") {
                        selectWorkoutFile.launch(workoutImportMimeTypes)
                    }
                },
                onChooseDocumentsFolder = {
                    documentPickerLaunchGate.tryLaunch("selectDocumentsFolder") {
                        selectDocumentsFolder.launch(null)
                    }
                },
                onRefreshDocumentsFolderWorkouts = {
                    viewModel.onRefreshDocumentsFolderWorkoutsRequested()
                },
                onSelectDocumentsFolderWorkout = { uriString ->
                    viewModel.onDocumentsFolderWorkoutSelected(uriString)
                },
                onSelectBuiltInWorkout = { assetPath ->
                    viewModel.onSelectBuiltInWorkout(assetPath)
                },
                onOpenBuiltInWorkoutInEditor = { assetPath ->
                    viewModel.onOpenBuiltInWorkoutInEwoEditor(assetPath)
                },
                onFtpInputChanged = { input -> viewModel.onFtpInputChanged(input) },
                onHrProfileAgeInputChanged = { input -> viewModel.onHrProfileAgeInputChanged(input) },
                onHrProfileSexSelected = { sex -> viewModel.onHrProfileSexSelected(sex) },
                onSearchFtmsDevices = { viewModel.onSearchFtmsDevicesRequested() },
                onSearchHrDevices = { viewModel.onSearchHrDevicesRequested() },
                onScannedDeviceSelected = { device -> viewModel.onScannedDeviceSelected(device) },
                onDismissDeviceSelection = { viewModel.onDismissDeviceSelection() },
                onDismissConnectionIssue = { viewModel.clearConnectionIssuePrompt() },
                onSearchFtmsDevicesFromConnectionIssue = { viewModel.onSearchFtmsDevicesFromConnectionIssue() },
                onOpenAppSettingsFromConnectionIssue = {
                    viewModel.clearConnectionIssuePrompt()
                    permissionCoordinator.openAppSettings()
                },
                onAiMenuAssistantAction = { actionKey ->
                    viewModel.onAiMenuAssistantAction(actionKey)
                },
                onConnectingTimeoutKeepWaiting = {
                    viewModel.onConnectingTimeoutKeepWaiting()
                },
                onConnectingTimeoutRetry = {
                    viewModel.onConnectingTimeoutRetry()
                },
                onConnectingTimeoutBackToMenu = {
                    viewModel.onConnectingTimeoutBackToMenu()
                },
                onMockTrainerModeChanged = { enabled ->
                    viewModel.onMockTrainerModeChanged(enabled)
                },
                onQuitApp = {
                    viewModel.stopAndClose()
                    finishAndRemoveTask()
                },
                onStartSession = { viewModel.onStartSession() },
                onEndSession = { viewModel.onEndSessionAndGoToSummary() },
                onWorkoutCompletePresented = { viewModel.onWorkoutCompletePresented() },
                onContinueRideAfterWorkoutComplete = {
                    viewModel.onContinueRideAfterWorkoutComplete()
                },
                onEndSessionAfterWorkoutComplete = {
                    viewModel.onEndSessionAfterWorkoutComplete()
                },
                onSessionDebugProbeSignal = { signal ->
                    viewModel.onSessionDebugProbeSignal(signal)
                },
                onBackToMenu = { viewModel.onBackToMenu() },
                onEwoEditorAction = { action -> viewModel.onEwoEditorAction(action) },
                onOpenEwoEditor = { viewModel.onOpenEwoEditor() },
                onOpenEwoFile = {
                    documentPickerLaunchGate.tryLaunch("openEwoFile") {
                        openEwoFile.launch(workoutImportMimeTypes)
                    }
                },
                onSaveEwoFile = {
                    documentFlowCoordinator.requestEwoSave(viewModel.ewoEditorCoordinator.document.title)
                },
                onRequestSummaryFitExport = { documentFlowCoordinator.requestSummaryFitExport() },
                onRequestSummaryFitShare = { summaryFitShareCoordinator.requestShare() },
                onRequestSummaryFitAutoExport = {
                    viewModel.tryAutoExportSummaryFitIfNeeded()
                },
                onSummaryFitExportPreferenceSelected = { preference ->
                    viewModel.onSummaryFitExportPreferenceSelected(preference)
                },
                onRunCompatibilityCheck = {
                    viewModel.onRunCompatibilityCheckRequested()
                },
                onSelectStarterWorkout = { viewModel.onSelectStarterWorkout() },
                onSelectTelemetryOnlyMode = { viewModel.onSelectTelemetryOnlyMode() },
                onSessionSetupModeSelected = { mode -> viewModel.onSessionSetupModeSelected(mode) },
                onActiveMenuSetupStepChanged = { step -> viewModel.onActiveMenuSetupStepChanged(step) },
                onOpenBaselineFitnessTest = { viewModel.onOpenBaselineFitnessTestRequested() },
                baselineFitnessTestRuntimeSnapshot = viewModel.baselineFitnessTestRuntimeSnapshotState.value,
                onStartBaselineFitnessTest = { viewModel.onStartBaselineFitnessTestRequested() },
                onStopBaselineFitnessTest = { viewModel.onStopBaselineFitnessTestRequested() },
                onCancelBaselineFitnessTest = { viewModel.onCancelBaselineFitnessTestRequested() },
                onAcceptBaselineFitnessTestAdvisoryFallback = {
                    viewModel.onAcceptBaselineFitnessTestAdvisoryFallbackRequested()
                },
                onDeclineBaselineFitnessTestAdvisoryFallback = {
                    viewModel.onDeclineBaselineFitnessTestAdvisoryFallbackRequested()
                },
                onSkipBaselineFitnessTestCooldown = {
                    viewModel.onSkipBaselineFitnessTestCooldownRequested()
                },
                onBackFromBaselineFitnessTest = { viewModel.onBackFromBaselineFitnessTest() },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        debugIntentCoordinator.handle(intent)
    }

    override fun onResume() {
        super.onResume()
        summaryFitShareCoordinator.onHostResumed()
        viewModel.onActivityResumed()
    }

    override fun onPause() {
        viewModel.onActivityPaused()
        super.onPause()
    }

    override fun onDestroy() {
        lifecycleCoordinator.handleOnDestroy(
            isChangingConfigurations = isChangingConfigurations,
            isFinishing = isFinishing,
        )
        super.onDestroy()
    }
}

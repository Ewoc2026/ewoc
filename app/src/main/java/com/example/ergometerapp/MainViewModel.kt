package com.example.ergometerapp

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import com.example.ergometerapp.ai.AiPhase
import com.example.ergometerapp.ai.AiPolicyGuardrails
import com.example.ergometerapp.ai.AiPresentationAdapter
import com.example.ergometerapp.ai.AiPresentedMessageRecord
import com.example.ergometerapp.ai.AiRecentEmission
import com.example.ergometerapp.ai.AiRecommendationEngine
import com.example.ergometerapp.ai.AiRecommendationType
import com.example.ergometerapp.ai.AiRuleConfig
import com.example.ergometerapp.ai.AiTelemetryLogger
import com.example.ergometerapp.baseline.BaselineFitnessTestActiveFtpStatePort
import com.example.ergometerapp.baseline.BaselineFitnessTestCoordinator
import com.example.ergometerapp.baseline.BaselineFitnessTestLiveMetricsSnapshot
import com.example.ergometerapp.baseline.BaselineFitnessTestResult
import com.example.ergometerapp.baseline.BaselineFitnessTestResultPromotionCoordinator
import com.example.ergometerapp.baseline.BaselineFitnessTestRuntimePort
import com.example.ergometerapp.baseline.BaselineFitnessTestRuntimeSnapshot
import com.example.ergometerapp.baseline.BaselineFitnessTestSettingsStorage
import com.example.ergometerapp.baseline.BaselineFitnessTestTrainerPreparationState
import com.example.ergometerapp.baseline.BaselineFitnessTestTrainerControlRequestOutcome
import com.example.ergometerapp.billing.EwocBillingFacade
import com.example.ergometerapp.ble.BleDeviceScanner
import com.example.ergometerapp.ble.HrBleClient
import com.example.ergometerapp.compat.CompatibilityCheckOrchestrator
import com.example.ergometerapp.compat.CompatibilityFailureCode
import com.example.ergometerapp.compat.CompatibilityRunArtifacts
import com.example.ergometerapp.compat.CompatibilityRunArtifactsStorage
import com.example.ergometerapp.compat.FtmsCompatibilityDeviceGateway
import com.example.ergometerapp.compat.quirks.QuirksRegistry
import com.example.ergometerapp.logging.AppLog
import com.example.ergometerapp.session.MockTrainerDebugScenario
import com.example.ergometerapp.session.ExternalTrainerControlRequestOutcome
import com.example.ergometerapp.session.ExternalTrainerPreparationState
import com.example.ergometerapp.session.SessionManager
import com.example.ergometerapp.session.SessionOrchestrator
import com.example.ergometerapp.session.export.FitExportFailureReason
import com.example.ergometerapp.session.export.FitExportResult
import com.example.ergometerapp.session.export.FitExportService
import com.example.ergometerapp.session.export.SessionExportSnapshot
import com.example.ergometerapp.ewoeditor.EwoEditorCoordinator
import com.example.ergometerapp.ui.EwoEditorScreenAction
import com.example.ergometerapp.ui.MenuSetupStep
import com.example.ergometerapp.workout.BundledWorkoutAssetCatalog
import com.example.ergometerapp.workout.StarterWorkoutProvider
import com.example.ergometerapp.workout.WorkoutImportService
import java.io.File
import java.time.Instant
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.roundToInt

internal const val AI_MENU_CONNECTIVITY_CHECK_TRAINER_ACTION = "ai.menu.connectivity_check_trainer"
internal const val BASELINE_FITNESS_TEST_TICK_INTERVAL_MS = 1_000L
internal const val CONTINUE_RIDE_HARD_CUTOVER_POLL_INTERVAL_MS = 250L
internal const val CONTINUE_RIDE_HARD_CUTOVER_TIMEOUT_MS = 15_000L
internal const val DEBUG_HARD_CUTOVER_POLL_INTERVAL_MS = 250L
internal const val DEBUG_HARD_CUTOVER_TIMEOUT_MS = 15_000L
internal const val SESSION_DEBUG_PROBE_ARM_POLL_INTERVAL_MS = 250L

internal enum class PostWorkoutCompletionChoice {
    CONTINUE,
    SUMMARY,
}

internal fun requiresPreparedPostWorkoutExitWindow(choice: PostWorkoutCompletionChoice): Boolean {
    return choice == PostWorkoutCompletionChoice.CONTINUE
}

/**
 * Holds app/session state across Activity recreation.
 *
 * The ViewModel owns long-lived session services so orientation changes do not
 * tear down active BLE connections or workout execution.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application.applicationContext
    private val appFailureStrings = AppFailureStringResolver.fromContext(appContext)
    private val defaultFtpWatts = BuildConfig.DEFAULT_FTP_WATTS.coerceAtLeast(1)
    private val ftpInputMaxLength = 4
    private val hrProfileAgeInputMaxLength = 3
    private val ftmsServiceUuid: UUID = UUID.fromString("00001826-0000-1000-8000-00805f9b34fb")
    private val hrServiceUuid: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
    private val errorToneDurationMs = 120
    private val trainerStatusProbeIntervalMs = 10_000L
    private val trainerStatusProbeDurationMs = 1_500L
    private val hrStatusProbeIntervalMs = 30_000L
    private val hrStatusProbeDurationMs = 1_500L
    private val statusProbeScanMode = DeviceScanPolicy.scanModeFor(DeviceScanPolicy.Purpose.MENU_PROBE)
    private val pickerScanMode = DeviceScanPolicy.scanModeFor(DeviceScanPolicy.Purpose.PICKER)
    private val pickerScanRetryDelayMs = 1_500L
    private val pickerStopButtonLockDurationMs = 3_000L
    private val scannedDeviceSortThrottleMs = 300L
    private val statusProbeResumeDelayAfterPickerMs = 2_000L
    private val hrStatusMissThreshold = 2
    private val hrStatusStaleTimeoutMs = 75_000L
    private val workoutEditorMaxTextLength = 180
    private val workoutEditorMaxDescriptionLength = 400
    private val safWorkoutMimeType = "application/octet-stream"
    private val safFitMimeType = "application/octet-stream"
    private val summaryFitShareCacheDirectoryName = "fit-share"
    private var continueRideHardCutoverRunnable: Runnable? = null
    private var debugHardCutoverProbeRunnable: Runnable? = null
    private var sessionDebugProbeArmRunnable: Runnable? = null
    private var postWorkoutCompletionChoiceRunnable: Runnable? = null
    private var postWorkoutContinuationHandoffCompletionRunnable: Runnable? = null
    private var armedSessionDebugProbeTitle: String? = null
    private var armedSessionDebugProbeMessage: String? = null
    private val sessionDebugProbeEventQueue = SessionDebugProbeEventQueue(appContext)
    private var sessionDebugProbeLastEventSequence: Long? = null

    val uiState = AppUiState()
    internal val activeMenuSetupStepState = mutableStateOf<MenuSetupStep?>(null)
    private val profileSettingsUiState = ProfileSettingsUiState(defaultFtpWatts)
    val ftpWattsState get() = profileSettingsUiState.ftpWattsState
    val ftpInputTextState get() = profileSettingsUiState.ftpInputTextState
    val ftpInputErrorState get() = profileSettingsUiState.ftpInputErrorState
    val hrProfileAgeState get() = profileSettingsUiState.hrProfileAgeState
    val hrProfileAgeInputState get() = profileSettingsUiState.hrProfileAgeInputState
    val hrProfileAgeErrorState get() = profileSettingsUiState.hrProfileAgeErrorState
    val hrProfileSexState get() = profileSettingsUiState.hrProfileSexState
    private val sessionStartEligibilityUiState = SessionStartEligibilityUiState()
    internal val deviceSelectionUiState = DeviceSelectionUiState(
        ftmsSelectedMacState = sessionStartEligibilityUiState.ftmsSelection.selectedMacState,
        hrSelectedMacState = sessionStartEligibilityUiState.hrSelection.selectedMacState,
    )

    // Public forwarding properties for instrumentation-test access (preserve pre-WS1 API surface).
    val deviceScanInProgressState get() = deviceSelectionUiState.scanInProgressState
    val activeDeviceSelectionKindState get() = deviceSelectionUiState.activeSelectionKindState
    val deviceScanStatusState get() = deviceSelectionUiState.scanStatusState
    val ftmsDeviceNameState get() = deviceSelectionUiState.ftmsDevice.displayNameState
    val selectedFtmsDeviceMacState get() = sessionStartEligibilityUiState.ftmsSelection.selectedMacState

    // --- Canonical EWO editor ---
    internal val ewoEditorCoordinator = EwoEditorCoordinator(getApplication())
    private val ewoEditorOpenCoordinator = EwoEditorOpenCoordinator()
    internal val ewoEditorSnapshotState = mutableStateOf(ewoEditorCoordinator.snapshot())

    private val debugAutomationUiState = DebugAutomationUiState()
    private val documentsFolderUiState = DocumentsFolderUiState()
    val summaryFitExportStatusMessageState get() = documentsFolderUiState.fitExportStatusMessageState
    val summaryFitExportStatusIsErrorState get() = documentsFolderUiState.fitExportStatusIsErrorState
    private val summaryFitUiState = SummaryFitUiState()
    val fitExportPreferenceState get() = summaryFitUiState.preferenceState
    private val compatibilityModeUiState = CompatibilityModeUiState()
    val compatibilityCheckInProgressState get() = compatibilityModeUiState.checkInProgressState
    val compatibilityCheckStatusMessageState get() = compatibilityModeUiState.statusMessageState
    val mockTrainerModeEnabledState get() = sessionStartEligibilityUiState.mockTrainerModeEnabledState
    val documentsFolderReadyState get() = documentsFolderUiState.readyState
    val documentsFolderAccessLostState get() = documentsFolderUiState.accessLostState
    val documentsFolderSummaryState get() = documentsFolderUiState.summaryState
    val documentsFolderStatusMessageState get() = documentsFolderUiState.statusMessageState
    val documentsFolderStatusIsErrorState get() = documentsFolderUiState.statusIsErrorState
    val documentsFolderWorkoutFilesState get() = documentsFolderUiState.workoutFilesState
    private val builtInWorkoutsState = androidx.compose.runtime.mutableStateListOf<BuiltInWorkoutOption>()
    val builtInWorkoutFilesState get() = builtInWorkoutsState
    internal val baselineFitnessTestRuntimeSnapshotState =
        mutableStateOf(BaselineFitnessTestRuntimeSnapshot())
    internal val baselineLatestResultState =
        mutableStateOf(BaselineFitnessTestSettingsStorage.loadLatestResult(appContext))

    private val activityCallbackBridge = ActivityCallbackBridge()
    private var closed = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private var baselineFitnessTestTickRunnable: Runnable? = null
    private val menuStatusProbeStatePort = RealMenuStatusProbeStatePort(
        uiState = uiState,
        deviceSelectionUiState = deviceSelectionUiState,
        currentFtmsDeviceMacProvider = ::currentFtmsDeviceMac,
        currentHrDeviceMacProvider = ::currentHrDeviceMac,
    )
    private val menuStatusProbeFacade: MenuStatusProbeFacade = buildMenuStatusProbeFacade(
        appContext = appContext,
        statePort = menuStatusProbeStatePort,
        appHandler = mainHandler,
        hasBluetoothScanPermission = ::hasBluetoothScanPermission,
        refreshMenuRecommendations = {
            refreshAiAssistantRecommendations(
                forcePhase = AiPhase.MENU,
                force = true,
            )
        },
        isClosed = { closed },
        ftmsServiceUuid = ftmsServiceUuid,
        hrServiceUuid = hrServiceUuid,
        statusProbeScanMode = statusProbeScanMode,
        trainerStatusProbeIntervalMs = trainerStatusProbeIntervalMs,
        trainerStatusProbeDurationMs = trainerStatusProbeDurationMs,
        hrStatusProbeIntervalMs = hrStatusProbeIntervalMs,
        hrStatusProbeDurationMs = hrStatusProbeDurationMs,
        statusProbeResumeDelayAfterPickerMs = statusProbeResumeDelayAfterPickerMs,
        hrStatusMissThreshold = hrStatusMissThreshold,
        hrStatusStaleTimeoutMs = hrStatusStaleTimeoutMs,
    )
    private var errorToneGenerator: ToneGenerator? = null
    private val aiRecommendationEngine = AiRecommendationEngine(
        config = AiRuleConfig(),
    )
    private val aiPolicyGuardrails = AiPolicyGuardrails()
    private val aiPresentationAdapter = AiPresentationAdapter()
    private val aiTelemetryLogger = AiTelemetryLogger()
    private val compatibilityBackgroundExecutor: ExecutorService =
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "CompatibilityWorker").apply { isDaemon = true }
        }
    private val compatibilityCheckExecutor: CompatibilityCheckExecutor =
        RealCompatibilityCheckExecutor(
            nowEpochMillis = System::currentTimeMillis,
            androidManufacturer = { Build.MANUFACTURER },
            androidModel = { Build.MODEL },
            resolveQuirks = QuirksRegistry::resolve,
            runCheck = { trainerMacAddress, quirks ->
                val gateway = FtmsCompatibilityDeviceGateway(
                    context = appContext,
                    trainerMacAddress = trainerMacAddress,
                )
                CompatibilityCheckOrchestrator(deviceGateway = gateway).run(quirks)
            },
            persist = { artifacts -> CompatibilityRunArtifactsStorage.persist(appContext, artifacts) },
        )
    private val compatibilityCheckCoordinator = CompatibilityCheckCoordinator(
        resolveFailureReasonMessage = { failureReasonKey, failureCode ->
            resolveCompatibilityFailureReasonMessage(
                failureReasonKey,
                failureCode,
                appContext::getString,
            )
        },
    )
    private val compatibilityCheckLaunchCoordinator = CompatibilityCheckLaunchCoordinator()
    private val compatibilityCheckRunFacade = CompatibilityCheckRunFacade(
        launchCoordinator = compatibilityCheckLaunchCoordinator,
        checkCoordinator = compatibilityCheckCoordinator,
        executor = compatibilityCheckExecutor,
        runOnBackgroundThread = { task -> compatibilityBackgroundExecutor.execute(task) },
        runOnMainThread = { task -> mainHandler.post(task) },
        isClosed = { closed },
    )
    private val compatibilityFeatureStatePort = object : CompatibilityFeatureStatePort,
        CompatibilityCheckStatePort by compatibilityModeUiState.checkStatePort {
        override var latestRunArtifacts: CompatibilityRunArtifacts?
            get() = compatibilityModeUiState.latestRunArtifacts
            set(value) {
                compatibilityModeUiState.latestRunArtifacts = value
            }

    }
    private val compatibilityFeatureFacade = CompatibilityFeatureFacade(
        statePort = compatibilityFeatureStatePort,
        currentTrainerMac = ::currentFtmsDeviceMac,
        currentTrainerAlias = { deviceSelectionUiState.ftmsDevice.displayNameState.value },
        hasBluetoothConnectPermission = {
            activityCallbackBridge.hasBluetoothConnectPermission(defaultWhenUnbound = true)
        },
        runCompatibilityCheck = compatibilityCheckRunFacade::startRun,
        resolveRequiresTrainerMessage = {
            appContext.getString(R.string.compatibility_check_requires_trainer)
        },
        resolveRequiresPermissionMessage = {
            appContext.getString(R.string.compatibility_check_requires_permission)
        },
        resolveRunningStatusMessage = {
            appContext.getString(R.string.compatibility_check_status_running)
        },
        resolvePassStatusMessage = {
            appContext.getString(R.string.compatibility_check_status_pass)
        },
        resolveFailStatusMessage = { reason ->
            appContext.getString(R.string.compatibility_check_status_fail, reason)
        },
        resolvePersistFailureStatusMessage = { baseMessage ->
            appContext.getString(
                R.string.compatibility_check_status_fail_persist_suffix,
                baseMessage,
            )
        },
    )
    private val summaryFitExportStatePort = documentsFolderUiState.summaryFitExportStatePort
    private val summaryFitShareStatePort = documentsFolderUiState.summaryFitShareStatePort
    private val summaryFitExportPreferenceStatePort = summaryFitUiState.exportPreferenceStatePort(
        statusMessageState = documentsFolderUiState.fitExportStatusMessageState,
        statusIsErrorState = documentsFolderUiState.fitExportStatusIsErrorState,
    )
    private val summaryFitAutoExportStatePort =
        summaryFitUiState.autoExportStatePort(summaryProvider = { uiState.summary.value })
    private val summaryExitStatePort = RealSummaryExitStatePort(
        uiState = uiState,
        documentsFolderUiState = documentsFolderUiState,
        compatibilityModeUiState = compatibilityModeUiState,
        summaryFitUiState = summaryFitUiState,
    )
    private val aiAssistantStatePort = RealAiAssistantStatePort(
        ftmsReadyProvider = { uiState.ftmsReady.value },
        ftmsReachableProvider = { deviceSelectionUiState.ftmsDevice.reachableState.value },
        hrConnectedProvider = { deviceSelectionUiState.hrDevice.connectedState.value },
        hrReachableProvider = { deviceSelectionUiState.hrDevice.reachableState.value },
    )
    private val aiAssistantIntegration = buildAiAssistantIntegration(
        appContext = appContext,
        uiState = uiState,
        aiAssistantStatePort = aiAssistantStatePort,
        currentScreen = { uiState.screen.value },
        currentHrConnected = { deviceSelectionUiState.hrDevice.connectedState.value },
        hasSelectedHrDevice = this::hasSelectedHrDevice,
        isMockTrainerModeActive = this::isMockTrainerModeActive,
        ftpWatts = { profileSettingsUiState.ftpWattsState.intValue },
        recommendationEngine = aiRecommendationEngine,
        policyGuardrails = aiPolicyGuardrails,
        presentationAdapter = aiPresentationAdapter,
        telemetryLogger = aiTelemetryLogger,
    )
    private val aiCoordinator = aiAssistantIntegration.coordinator
    private val aiAssistantFacade = aiAssistantIntegration.facade
    internal val billingFacade = EwocBillingFacade(
        context = appContext,
        currentActivityProvider = activityCallbackBridge::currentBillingActivity,
    )

    private val sessionManager = SessionManager(
        context = appContext,
        onStateUpdated = { state ->
            uiState.session.value = state
            aiAssistantFacade.refresh()
        },
        onTimelineSampleRecorded = { samples ->
            uiState.timelineSamples.value = samples
        },
    )

    private val hrClient = HrBleClient(
        context = appContext,
        onHeartRate = { bpm ->
            uiState.heartRate.value = bpm
            uiState.heartRateLastUpdatedAtEpochMs = System.currentTimeMillis()
            sessionManager.updateHeartRate(bpm)
            notifyExternalHeartRateTelemetryUpdated()
        },
        onConnected = {
            deviceSelectionUiState.hrDevice.connectedState.value = true
            deviceSelectionUiState.hrDevice.permissionDeniedState.value = false
            deviceSelectionUiState.hrDevice.reachableState.value = true
            deviceSelectionUiState.hrDevice.consecutiveMisses = 0
            deviceSelectionUiState.hrDevice.lastSeenElapsedMs = SystemClock.elapsedRealtime()
            clearHrPermissionDeniedStatus()
            aiAssistantFacade.refresh(force = true)
        },
        onDisconnected = {
            deviceSelectionUiState.hrDevice.connectedState.value = false
            deviceSelectionUiState.hrDevice.permissionDeniedState.value = false
            uiState.heartRate.value = null
            uiState.heartRateLastUpdatedAtEpochMs = null
            sessionManager.updateHeartRate(null)
            notifyExternalHeartRateTelemetryUpdated()
            aiAssistantFacade.refresh(force = true)
        },
        onPermissionDenied = {
            deviceSelectionUiState.hrDevice.connectedState.value = false
            deviceSelectionUiState.hrDevice.permissionDeniedState.value = true
            uiState.heartRate.value = null
            uiState.heartRateLastUpdatedAtEpochMs = null
            sessionManager.updateHeartRate(null)
            notifyExternalHeartRateTelemetryUpdated()
            deviceSelectionUiState.scanStatusState.value =
                appContext.getString(R.string.menu_hr_connect_permission_required)
            aiAssistantFacade.refresh(force = true)
        },
        onReconnectExhausted = {
            deviceSelectionUiState.hrDevice.connectedState.value = false
            deviceSelectionUiState.hrDevice.permissionDeniedState.value = false
            deviceSelectionUiState.hrDevice.reachableState.value = false
            uiState.heartRate.value = null
            uiState.heartRateLastUpdatedAtEpochMs = null
            sessionManager.updateHeartRate(null)
            notifyExternalHeartRateTelemetryUpdated()
            aiAssistantFacade.refresh(force = true)
        },
    )

    private val deviceScanUiPort = RealDeviceScanUiPort(
        deviceSelectionUiState = deviceSelectionUiState,
    )

    private val deviceSelectionApplyStatePort = deviceSelectionUiState.applyStatePort()

    private val deviceSelectionApplyCoordinator = DeviceSelectionApplyCoordinator(
        statePort = deviceSelectionApplyStatePort,
        saveFtmsDeviceMac = { macAddress -> DeviceSettingsStorage.saveFtmsDeviceMac(appContext, macAddress) },
        saveFtmsDeviceName = { deviceName -> DeviceSettingsStorage.saveFtmsDeviceName(appContext, deviceName) },
        saveHrDeviceMac = { macAddress -> DeviceSettingsStorage.saveHrDeviceMac(appContext, macAddress) },
        saveHrDeviceName = { deviceName -> DeviceSettingsStorage.saveHrDeviceName(appContext, deviceName) },
        releaseWarmTrainerConnection = { sessionOrchestrator.releaseTrainerWarmConnectionInMenu() },
        prepareWarmTrainerConnection = { sessionOrchestrator.prepareTrainerWarmConnectionInMenu() },
        cancelTrainerStatusProbeScan = ::cancelTrainerStatusProbeScan,
        cancelHrStatusProbeScan = ::cancelHrStatusProbeScan,
        probeTrainerAvailabilityNow = ::probeTrainerAvailabilityNow,
        probeHrAvailabilityNow = ::probeHrAvailabilityNow,
        refreshAiAssistantRecommendations = {
            refreshAiAssistantRecommendations(force = true)
        },
    )

    private val deviceSelectionPreScanStatePort =
        deviceSelectionUiState.preScanStatePort(heartRateState = uiState.heartRate)

    private val deviceSelectionPreScanCoordinator = DeviceSelectionPreScanCoordinator(
        statePort = deviceSelectionPreScanStatePort,
        cancelPendingStatusProbeResume = ::cancelPendingStatusProbeResume,
        cancelTrainerStatusProbeScan = ::cancelTrainerStatusProbeScan,
        cancelHrStatusProbeScan = ::cancelHrStatusProbeScan,
        closeHeartRateClient = ::closeHrClientForPickerScan,
        updateSessionHeartRate = { bpm -> sessionManager.updateHeartRate(bpm) },
    )

    private val deviceSelectionFacade: DeviceSelectionFacade = RealDeviceSelectionFacade(
        uiPort = deviceScanUiPort,
        scanEngine = BleDeviceScanEngine(BleDeviceScanner(appContext, scannerLabel = "picker")),
        handler = mainHandler,
        messages = DeviceScanMessages(
            scanning = { appContext.getString(R.string.menu_device_scan_status_scanning) },
            retrying = { appContext.getString(R.string.menu_device_scan_status_retrying) },
            noResults = { appContext.getString(R.string.menu_device_scan_status_no_results) },
            done = { count -> appContext.getString(R.string.menu_device_scan_status_done, count) },
            failed = { appContext.getString(R.string.menu_device_scan_status_failed) },
            permissionRequired = { appContext.getString(R.string.menu_device_scan_permission_required) },
        ),
        ftmsServiceUuid = ftmsServiceUuid,
        hrServiceUuid = hrServiceUuid,
        pickerScanMode = pickerScanMode,
        pickerScanRetryDelayMs = pickerScanRetryDelayMs,
        pickerStopButtonLockDurationMs = pickerStopButtonLockDurationMs,
        scannedDeviceSortThrottleMs = scannedDeviceSortThrottleMs,
        ensureBluetoothScanPermission = activityCallbackBridge::hasBluetoothScanPermission,
        onBeforeScanRequest = deviceSelectionPreScanCoordinator::onBeforeScanRequest,
        onBeforeScanStart = deviceSelectionPreScanCoordinator::onBeforeScanStart,
        onAfterPickerDismissed = ::suppressStatusProbesTemporarily,
        applyFtmsSelection = deviceSelectionApplyCoordinator::applyFtmsDeviceSelection,
        applyHrSelection = deviceSelectionApplyCoordinator::applyHrDeviceSelection,
        clearConnectionIssuePrompt = ::clearConnectionIssuePrompt,
        refreshAiAssistantRecommendations = {
            refreshAiAssistantRecommendations(force = true)
        },
    )
    private val connectionIssuePromptStatePort =
        uiState.connectionRecoveryUiState.connectionIssuePromptStatePort
    private val connectionIssuePromptCoordinator = ConnectionIssuePromptCoordinator(
        statePort = connectionIssuePromptStatePort,
        refreshAiAssistantRecommendations = {
            refreshAiAssistantRecommendations(force = true)
        },
        onSearchFtmsDevicesRequested = ::onSearchFtmsDevicesRequested,
    )
    private val profileSettingsStatePort = profileSettingsUiState.statePort
    private val baselineFitnessTestActiveFtpStatePort = object : BaselineFitnessTestActiveFtpStatePort {
        override var ftpWatts: Int
            get() = profileSettingsUiState.ftpWattsState.intValue
            set(value) {
                profileSettingsUiState.ftpWattsState.intValue = value
            }

        override var ftpInputText: String
            get() = profileSettingsUiState.ftpInputTextState.value
            set(value) {
                profileSettingsUiState.ftpInputTextState.value = value
            }

        override var ftpInputError: String?
            get() = profileSettingsUiState.ftpInputErrorState.value
            set(value) {
                profileSettingsUiState.ftpInputErrorState.value = value
            }
    }
    private val profileSettingsCoordinator = ProfileSettingsCoordinator(
        statePort = profileSettingsStatePort,
        ftpInputMaxLength = ftpInputMaxLength,
        hrProfileAgeInputMaxLength = hrProfileAgeInputMaxLength,
        ftpInvalidInputErrorMessage = {
            appContext.getString(R.string.menu_ftp_error_invalid)
        },
        hrProfileAgeInvalidInputErrorMessage = {
            appContext.getString(R.string.menu_hr_profile_age_error)
        },
        saveFtpWatts = { watts ->
            FtpSettingsStorage.saveFtpWatts(appContext, watts)
            BaselineFitnessTestSettingsStorage.clearActiveFtpSourceMetadata(appContext)
        },
        onFtpWattsSaved = { sessionOrchestrator.onFtpWattsChanged() },
        saveHrProfileAge = { age -> HrProfileSettingsStorage.saveAge(appContext, age) },
        saveHrProfileSex = { sex -> HrProfileSettingsStorage.saveSex(appContext, sex) },
        refreshAiAssistantRecommendations = {
            refreshAiAssistantRecommendations(force = true)
        },
    )
    private val baselineFitnessTestResultPromotionCoordinator = BaselineFitnessTestResultPromotionCoordinator(
        statePort = baselineFitnessTestActiveFtpStatePort,
        saveFtpWatts = { watts -> FtpSettingsStorage.saveFtpWatts(appContext, watts) },
        saveLatestResult = { result ->
            BaselineFitnessTestSettingsStorage.saveLatestResult(appContext, result)
        },
        onFtpWattsPromoted = { sessionOrchestrator.onFtpWattsChanged() },
        refreshAfterPromotion = {
            refreshAiAssistantRecommendations(force = true)
        },
    )
    private val baselineFitnessTestRuntimePort by lazy {
        object : BaselineFitnessTestRuntimePort {
            override fun prepareTrainer(): Boolean {
                return sessionOrchestrator.prepareTrainerForExternalUse()
            }

            override fun releaseTrainer() {
                sessionOrchestrator.releaseTrainerForExternalUse()
            }

            override fun trainerPreparationState(): BaselineFitnessTestTrainerPreparationState {
                return when (sessionOrchestrator.externalTrainerPreparationStateForExternalUse()) {
                    ExternalTrainerPreparationState.IDLE ->
                        BaselineFitnessTestTrainerPreparationState.IDLE

                    ExternalTrainerPreparationState.PENDING ->
                        BaselineFitnessTestTrainerPreparationState.PENDING

                    ExternalTrainerPreparationState.READY ->
                        BaselineFitnessTestTrainerPreparationState.READY

                    ExternalTrainerPreparationState.FAILED ->
                        BaselineFitnessTestTrainerPreparationState.FAILED
                }
            }

            override fun requestTrainerControl(): Boolean {
                return sessionOrchestrator.requestTrainerControlForExternalUse()
            }

            override fun setErgTarget(watts: Int) {
                sessionOrchestrator.setExternalTargetPower(watts)
            }

            override fun resetTrainerToIdle() {
                sessionOrchestrator.resetExternalTrainerToIdle()
            }

            override fun consumeTrainerControlRequestOutcome(): BaselineFitnessTestTrainerControlRequestOutcome? {
                return when (sessionOrchestrator.consumeExternalTrainerControlRequestOutcome()) {
                    ExternalTrainerControlRequestOutcome.GRANTED ->
                        BaselineFitnessTestTrainerControlRequestOutcome.GRANTED

                    ExternalTrainerControlRequestOutcome.FAILED ->
                        BaselineFitnessTestTrainerControlRequestOutcome.FAILED

                    null -> null
                }
            }
        }
    }
    private val baselineFitnessTestCoordinator by lazy {
        BaselineFitnessTestCoordinator(
            runtimePort = baselineFitnessTestRuntimePort,
            onResultRecorded = ::onBaselineFitnessTestResultRecorded,
            onSnapshotChanged = { snapshot ->
                baselineFitnessTestRuntimeSnapshotState.value = snapshot
            },
        )
    }
    private val sessionStartEligibilityStatePort = sessionStartEligibilityUiState.statePort(
        workoutReadyProvider = { uiState.workoutReady.value },
        selectedSessionSetupModeProvider = { uiState.selectedSessionSetupMode.value },
    )
    private val sessionStartEligibilityCoordinator = SessionStartEligibilityCoordinator(
        statePort = sessionStartEligibilityStatePort,
        isDebugBuild = { BuildConfig.DEBUG },
        normalizeBluetoothMac = BluetoothMacAddress::normalizeOrNull,
        saveMockTrainerModeEnabled = { enabled ->
            MockTrainerSettingsStorage.saveEnabled(appContext, enabled)
        },
        clearConnectionIssuePrompt = connectionIssuePromptCoordinator::clearPrompt,
    )
    private val documentsFolderStatePort = documentsFolderUiState.documentsFolderStatePort
    private val documentsFolderCoordinator = DocumentsFolderCoordinator(
        statePort = documentsFolderStatePort,
        hasReadWriteAccess = { uri -> SafTreeFileService.hasReadWriteAccess(appContext, uri) },
        resolveFolderLabel = { uri -> SafTreeFileService.resolveFolderLabel(appContext, uri) },
        resolveAccessState = ::resolveDocumentsFolderAccessState,
        notConfiguredStatusMessage = {
            appContext.getString(R.string.menu_documents_folder_not_configured)
        },
        accessLostStatusMessage = {
            appContext.getString(R.string.menu_documents_folder_access_lost)
        },
        unconfiguredSummary = {
            appContext.getString(R.string.menu_documents_folder_unconfigured)
        },
        accessLostSummary = {
            appContext.getString(R.string.menu_documents_folder_access_lost)
        },
        unknownFolderLabel = {
            appContext.getString(R.string.menu_documents_folder_unknown)
        },
        readySummary = { folderLabel ->
            appContext.getString(R.string.menu_documents_folder_ready, folderLabel)
        },
    )
    private val documentsFolderWriteCoordinator = DocumentsFolderWriteCoordinator(
        ensureFolderReadyForFileOperations = ::ensureDocumentsFolderReadyForFileOperations,
        isFolderAccessLost = { documentsFolderUiState.accessLostState.value },
        consumeDebugWriteFailureOnce = ::consumeDebugDocumentsFolderWriteFailureOnce,
        setDocumentsFolderStatus = ::setDocumentsFolderStatus,
        documentsFolderAccessLostMessage = {
            appContext.getString(R.string.menu_documents_folder_access_lost)
        },
        documentsFolderNotConfiguredMessage = {
            appContext.getString(R.string.menu_documents_folder_not_configured)
        },
        documentsFolderWriteFailedMessage = {
            appContext.getString(R.string.menu_documents_folder_write_failed)
        },
    )
    private val documentsFolderWriteExecutionCoordinator = DocumentsFolderWriteExecutionCoordinator(
        createWritableDocumentUri = { treeUri, preferredFileName, mimeType, existingFilePolicy ->
            SafTreeFileService.createWritableDocumentUri(
                context = appContext,
                treeUri = treeUri,
                preferredFileName = preferredFileName,
                mimeType = mimeType,
                existingFilePolicy = existingFilePolicy,
            )
        },
        resolveFallbackDecision = { shouldFallback, allowPickerFallback, onPickerFallbackBlocked ->
            documentsFolderWriteCoordinator.resolveFallbackDecision(
                shouldFallback = shouldFallback,
                allowPickerFallback = allowPickerFallback,
                onPickerFallbackBlocked = onPickerFallbackBlocked,
            )
        },
    )
    private val safUtf8TextWriter = SafUtf8TextWriter { uri, mode ->
        appContext.contentResolver.openOutputStream(uri, mode)
    }
    private val documentsFolderImportCoordinator = DocumentsFolderImportCoordinator(
        statePort = documentsFolderStatePort,
        persistReadWritePermission = { uri ->
            runCatching {
                appContext.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
                true
            }.getOrElse {
                false
            }
        },
        persistReadOnlyPermission = { uri ->
            runCatching {
                appContext.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
                true
            }.getOrElse {
                false
            }
        },
        releasePersistedPermission = { uri ->
            runCatching {
                appContext.contentResolver.releasePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
        },
        saveTreeUri = { uri ->
            SafSharedFolderSettingsStorage.saveTreeUri(appContext, uri)
        },
        refreshState = ::refreshDocumentsFolderState,
        ensureFolderReadyForFileOperations = ::ensureDocumentsFolderReadyForFileOperations,
        listWorkoutFiles = { folderUri ->
            SafTreeFileService.listWorkoutFiles(appContext, folderUri).map { file ->
                DocumentsFolderWorkoutOption(
                    uriString = file.uri.toString(),
                    displayName = file.displayName,
                )
            }
        },
        parseUri = { uriString ->
            runCatching { Uri.parse(uriString) }.getOrNull()
        },
        setDocumentsFolderStatus = ::setDocumentsFolderStatus,
        clearDocumentsFolderStatus = ::clearDocumentsFolderStatus,
        importWorkoutFromUri = ::onWorkoutFileSelected,
        bindFailedMessage = {
            appContext.getString(R.string.menu_documents_folder_bind_failed)
        },
        boundMessage = {
            appContext.getString(R.string.menu_documents_folder_bound)
        },
        noWorkoutFilesMessage = {
            appContext.getString(R.string.menu_documents_folder_no_zwo_files)
        },
    )
    private val summaryFitShareCoordinator = SummaryFitShareCoordinator(
        suggestFileName = { snapshot ->
            FitExportService.suggestedFileName(snapshot.summary)
        },
        noSummaryMessage = {
            AppFailureUserMessageMapper.toUserMessage(
                failure = AppFailureFactory.exportFailure(FitExportFailureReason.NO_SUMMARY),
                strings = appFailureStrings,
            )
        },
        exportFailureMessage = { failure ->
            AppFailureUserMessageMapper.toUserMessage(
                failure = AppFailureFactory.exportFailure(
                    reason = failure.reason,
                    detail = failure.detail,
                ),
                strings = appFailureStrings,
            )
        },
        shareUriUnavailableMessage = {
            appContext.getString(R.string.summary_fit_share_uri_failed)
        },
        launchFailureMessage = {
            appContext.getString(R.string.summary_fit_share_launch_failed)
        },
        cacheDirectoryProvider = {
            File(appContext.cacheDir, summaryFitShareCacheDirectoryName)
        },
        buildFitBytes = FitExportService::buildFitBytes,
        resolveShareUri = { cacheFile ->
            runCatching {
                FileProvider.getUriForFile(
                    appContext,
                    "${appContext.packageName}.fileprovider",
                    cacheFile,
                )
            }.getOrNull()
        },
        createChooserIntent = { shareUri ->
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = safFitMimeType
                putExtra(Intent.EXTRA_STREAM, shareUri)
                clipData = ClipData.newRawUri("session_fit", shareUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            Intent.createChooser(
                sendIntent,
                appContext.getString(R.string.summary_fit_share_chooser_title),
            )
        },
        clearDocumentsFolderStatus = ::clearDocumentsFolderStatus,
    )
    private val summaryFitExportCoordinator = SummaryFitExportCoordinator(
        suggestFileName = { snapshot ->
            FitExportService.suggestedFileName(snapshot.summary)
        },
        noSummaryMessage = {
            AppFailureUserMessageMapper.toUserMessage(
                failure = AppFailureFactory.exportFailure(FitExportFailureReason.NO_SUMMARY),
                strings = appFailureStrings,
            )
        },
        exportFailureMessage = { failure ->
            AppFailureUserMessageMapper.toUserMessage(
                failure = AppFailureFactory.exportFailure(
                    reason = failure.reason,
                    detail = failure.detail,
                ),
                strings = appFailureStrings,
            )
        },
        exportSuccessMessage = {
            appContext.getString(R.string.summary_fit_export_success)
        },
        autoSaveFailedMessage = {
            appContext.getString(R.string.summary_fit_auto_save_failed)
        },
        prepareTreeWrite = documentsFolderWriteCoordinator::prepareTreeWrite,
        prepareWriteTarget = { folderUri, suggestedFileName, requiredExtension, mimeType, allowPickerFallback, onPickerFallbackBlocked ->
            documentsFolderWriteExecutionCoordinator.prepareWriteTarget(
                folderUri = folderUri,
                suggestedFileName = suggestedFileName,
                requiredExtension = requiredExtension,
                mimeType = mimeType,
                allowPickerFallback = allowPickerFallback,
                onPickerFallbackBlocked = onPickerFallbackBlocked,
            )
        },
        resolvePostWriteDecision = { result, allowPickerFallback, shouldFallback, onPickerFallbackBlocked ->
            documentsFolderWriteExecutionCoordinator.resolvePostWriteDecision(
                result = result,
                allowPickerFallback = allowPickerFallback,
                shouldFallback = shouldFallback,
                onPickerFallbackBlocked = onPickerFallbackBlocked,
            )
        },
        exportToUri = { uri, snapshot ->
            FitExportService.exportToUri(appContext, uri, snapshot)
        },
        clearDocumentsFolderStatus = ::clearDocumentsFolderStatus,
    )
    private val summaryFitExportPreferenceCoordinator = SummaryFitExportPreferenceCoordinator(
        statePort = summaryFitExportPreferenceStatePort,
        savePreference = { preference ->
            FitExportSettingsStorage.savePreference(appContext, preference)
        },
        autoSaveEnabledMessage = {
            appContext.getString(R.string.summary_fit_auto_save_enabled)
        },
        askEveryTimeEnabledMessage = {
            appContext.getString(R.string.summary_fit_ask_every_time_enabled)
        },
        doNotSaveEnabledMessage = {
            appContext.getString(R.string.summary_fit_do_not_save_enabled)
        },
    )
    private val summaryFitAutoExportCoordinator = SummaryFitAutoExportCoordinator(
        createFingerprint = ::autoExportFingerprint,
        prepareExport = ::prepareSessionFitExport,
        tryExportPendingToDocumentsFolder = ::tryExportPendingSessionFitToDocumentsFolder,
    )
    private val summaryExitCoordinator = SummaryExitCoordinator(
        clearSummaryAiMessage = { aiAssistantFacade.clearPhaseMessage(AiPhase.SUMMARY) },
    )

    private val sessionOrchestrator = SessionOrchestrator(
        context = appContext,
        uiState = uiState,
        sessionManager = sessionManager,
        ensureBluetoothPermission = activityCallbackBridge::hasBluetoothConnectPermission,
        connectHeartRate = {
            val hrMac = currentHrDeviceMac()
            if (hrMac != null) {
                hrClient.connect(hrMac)
            }
        },
        closeHeartRate = { hrClient.close() },
        keepScreenOn = activityCallbackBridge::keepScreenOn,
        allowScreenOff = activityCallbackBridge::allowScreenOff,
        onExecutionMappingFailure = { playExecutionFailureTone() },
        isMockTrainerModeEnabled = sessionStartEligibilityCoordinator::isMockTrainerModeActive,
        currentFtmsDeviceMac = sessionStartEligibilityCoordinator::currentFtmsDeviceMac,
        currentFtpWatts = { profileSettingsUiState.ftpWattsState.intValue },
        currentEwoCompileContext = ::currentRiderProfileCompileContext,
        consumeMockTrainerDebugScenario = debugAutomationUiState::consumeMockTrainerScenario,
    )
    private val sessionCoordinator: SessionStartStopUseCase = createSessionStartStopUseCase(
        dependencies = SessionStartStopDependencies(
            canStartSession = sessionStartEligibilityCoordinator::canStartSession,
            hooks = createSessionStartStopHooks(
                onSessionStarted = { aiCoordinator.onSessionStarted() },
                cancelTrainerStatusProbeScan = ::cancelTrainerStatusProbeScan,
                cancelHrStatusProbeScan = ::cancelHrStatusProbeScan,
                clearSessionFitExportStatus = ::clearSessionFitExportStatus,
                refreshSummaryRecommendations = {
                    aiAssistantFacade.refresh(
                        forcePhase = AiPhase.SUMMARY,
                        force = true,
                    )
                },
                refreshRecommendationsAfterPermission = {
                    aiAssistantFacade.refresh(force = true)
                },
            ),
            sessionControlPort = sessionOrchestrator,
        ),
    )

    private fun notifyExternalHeartRateTelemetryUpdated() {
        sessionOrchestrator.onExternalHeartRateTelemetryUpdated()
    }

    init {
        sessionDebugProbeEventQueue.reset()
        val storedFtpWatts = FtpSettingsStorage.loadFtpWatts(appContext, defaultFtpWatts)
        val storedHrProfileAge = HrProfileSettingsStorage.loadAge(appContext)
        val storedHrProfileSex = HrProfileSettingsStorage.loadSex(appContext)
        val storedFtmsMac = DeviceSettingsStorage.loadFtmsDeviceMac(appContext)
        val storedHrMac = DeviceSettingsStorage.loadHrDeviceMac(appContext)
        val storedFtmsName = DeviceSettingsStorage.loadFtmsDeviceName(appContext).orEmpty()
        val storedHrName = DeviceSettingsStorage.loadHrDeviceName(appContext).orEmpty()
        val storedMockTrainerModeEnabled = MockTrainerSettingsStorage.loadEnabled(appContext)
        val storedDocumentsTreeUri = SafSharedFolderSettingsStorage.loadTreeUri(appContext)
        val storedFitExportPreference = FitExportSettingsStorage.loadPreference(appContext)
        val storedCompatibilityRunArtifacts = CompatibilityRunArtifactsStorage.loadLatest(appContext)

        profileSettingsUiState.restoreStoredSettings(
            ftpWatts = storedFtpWatts,
            hrProfileAge = storedHrProfileAge,
            hrProfileSex = storedHrProfileSex,
        )
        deviceSelectionUiState.loadStoredSelections(
            ftmsMac = storedFtmsMac,
            ftmsName = storedFtmsName,
            hrMac = storedHrMac,
            hrName = storedHrName,
        )
        sessionStartEligibilityUiState.restoreMockTrainerMode(
            enabled = storedMockTrainerModeEnabled,
            isDebugBuild = BuildConfig.DEBUG,
        )
        documentsFolderUiState.restoreTreeUri(storedDocumentsTreeUri)
        summaryFitUiState.preferenceState.value = storedFitExportPreference
        compatibilityModeUiState.restoreLatestRunArtifacts(storedCompatibilityRunArtifacts)
        refreshBuiltInWorkoutLibrary()
        refreshDocumentsFolderState(clearStatusMessage = true)
        restoreWorkoutSelection()
        sessionOrchestrator.initialize()
        baselineFitnessTestCoordinator.refreshLiveMetrics(
            now = Instant.now(),
            liveMetrics = currentBaselineFitnessTestLiveMetrics(),
        )
        aiAssistantFacade.refresh(
            forcePhase = AiPhase.MENU,
            force = true,
        )
        billingFacade.refresh()
    }

    /**
     * Binds Activity-specific callbacks after recreation.
     */
    fun bindActivityCallbacks(
        ensureBluetoothConnectPermission: () -> Boolean,
        ensureBluetoothScanPermission: () -> Boolean,
        keepScreenOn: () -> Unit,
        allowScreenOff: () -> Unit,
        currentBillingActivity: () -> Activity?,
    ) {
        activityCallbackBridge.bind(
            ensureBluetoothConnectPermission = ensureBluetoothConnectPermission,
            ensureBluetoothScanPermission = ensureBluetoothScanPermission,
            keepScreenOn = keepScreenOn,
            allowScreenOff = allowScreenOff,
            currentBillingActivity = currentBillingActivity,
        )
        if (uiState.screen.value == AppScreen.SESSION || uiState.screen.value == AppScreen.STOPPING) {
            keepScreenOn()
        }
        startTrainerStatusPolling()
        startHrStatusPolling()
        probeTrainerAvailabilityNow()
        probeHrAvailabilityNow()
        refreshDocumentsFolderState(clearStatusMessage = false)
        aiAssistantFacade.refresh(
            forcePhase = AiPhase.MENU,
            force = true,
        )
        billingFacade.refresh()
    }

    /**
     * Unbinds Activity callbacks before a configuration-driven teardown.
     */
    fun unbindActivityCallbacks() {
        activityCallbackBridge.unbind()
        aiCoordinator.onActivityUnbound()
        stopTrainerStatusPolling()
        stopHrStatusPolling()
    }

    fun onActivityResumed() {
        aiAssistantFacade.refresh(force = true)
        billingFacade.refresh()
    }

    fun onActivityPaused() {
    }

    /**
     * Stops runtime services exactly once when app flow is finishing.
     */
    fun stopAndClose() {
        if (closed) return
        closed = true
        baselineFitnessTestTickRunnable?.let(mainHandler::removeCallbacks)
        baselineFitnessTestTickRunnable = null
        cancelPendingContinueRideHardCutover()
        cancelPendingPostWorkoutCompletionChoice()
        cancelPostWorkoutContinuationHandoffCompletion()
        hidePostWorkoutContinuationHandoff()
        cancelPendingHardCutoverContinueRideProbe()
        deviceSelectionFacade.close()
        menuStatusProbeFacade.close()
        releaseErrorTone()
        deviceSelectionUiState.resetTransientConnectionState()
        compatibilityBackgroundExecutor.shutdownNow()
        billingFacade.close()
        aiCoordinator.onClosed()
        sessionOrchestrator.stopAndClose()
    }

    /**
     * Returns the current session phase for rendering.
     */
    fun phase() = sessionManager.getPhase()

    /**
     * Returns the current derived high-level session lifecycle without exposing
     * the underlying flag combinations to the UI layer.
     */
    fun sessionLifecycleState(): SessionLifecycleState = uiState.deriveSessionLifecycleState()

    val pendingBluetoothPermissionRequestId: Long
        get() = sessionCoordinator.pendingBluetoothPermissionRequestId

    fun onBluetoothPermissionResult(granted: Boolean, requestId: Long) {
        sessionCoordinator.onBluetoothPermissionResult(granted, requestId)
    }

    /**
     * Continues pending device scan request after runtime BLUETOOTH_SCAN result.
     */
    fun onBluetoothScanPermissionResult(granted: Boolean) {
        val result = deviceSelectionFacade.onBluetoothScanPermissionResult(granted)
        when (result) {
            ScanPermissionResult.STARTED_PENDING_SCAN -> return
            ScanPermissionResult.DENIED,
            ScanPermissionResult.GRANTED_NO_PENDING -> {
                if (granted) {
                    probeTrainerAvailabilityNow()
                    probeHrAvailabilityNow()
                }
                aiAssistantFacade.refresh(force = true)
            }
        }
    }

    fun onWorkoutFileSelected(uri: Uri?) {
        sessionOrchestrator.onWorkoutFileSelected(uri)
        if (uri != null) {
            tryTakePersistableReadPermission(uri)
            WorkoutSelectionSettingsStorage.save(
                appContext, SessionSetupMode.FILE, uri,
                uiState.selectedWorkoutFileName.value,
            )
            documentsFolderUiState.workoutFilesState.clear()
            clearDocumentsFolderStatus()
        }
        aiAssistantFacade.refresh(force = true)
    }

    /**
     * Persists a SAF documents folder and keeps access across app restarts.
     *
     * Write persistence may be rejected by some providers even when read persistence is allowed.
     * Read access is sufficient for folder-based workout import, so the fallback path treats
     * successful read-permission persistence as a successful bind.
     */
    fun onDocumentsFolderSelected(uri: Uri?) {
        documentsFolderImportCoordinator.onFolderSelected(uri)
    }

    /**
     * Loads supported workout import files (`.zwo`, `.xml`, and `.ewo`) from the selected SAF folder.
     */
    fun onRefreshDocumentsFolderWorkoutsRequested() {
        documentsFolderImportCoordinator.onRefreshWorkoutFilesRequested()
    }

    /**
     * Imports one workout file selected from the bound SAF documents folder.
     */
    fun onDocumentsFolderWorkoutSelected(uriString: String) {
        documentsFolderImportCoordinator.onWorkoutFileSelected(uriString)
        val uri = runCatching { Uri.parse(uriString) }.getOrNull()
        if (uri != null) {
            WorkoutSelectionSettingsStorage.save(
                appContext, SessionSetupMode.FILE, uri,
                uiState.selectedWorkoutFileName.value,
            )
        }
    }

    fun onSelectStarterWorkout() {
        sessionOrchestrator.onStarterWorkoutSelected()
        WorkoutSelectionSettingsStorage.save(
            context = appContext,
            mode = SessionSetupMode.FILE,
            uri = null,
            fileName = StarterWorkoutProvider.sourceName,
            bundledWorkoutAssetPath = StarterWorkoutProvider.assetPath,
        )
    }

    fun onSelectBuiltInWorkout(assetPath: String) {
        val option = resolveBuiltInWorkoutOption(assetPath)
        sessionOrchestrator.onBundledWorkoutSelected(
            assetPath = assetPath,
            sourceName = option?.fileName ?: BundledWorkoutAssetCatalog.fileNameFromAssetPath(assetPath),
        )
        WorkoutSelectionSettingsStorage.save(
            context = appContext,
            mode = SessionSetupMode.FILE,
            uri = null,
            fileName = option?.fileName ?: uiState.selectedWorkoutFileName.value,
            bundledWorkoutAssetPath = assetPath,
        )
        aiAssistantFacade.refresh(force = true)
    }

    fun onOpenBuiltInWorkoutInEwoEditor(assetPath: String) {
        val option = resolveBuiltInWorkoutOption(assetPath)
        val fileName = option?.fileName ?: BundledWorkoutAssetCatalog.fileNameFromAssetPath(assetPath)
        val content = try {
            BundledWorkoutAssetCatalog.loadText(appContext, assetPath)
        } catch (e: Exception) {
            ewoEditorCoordinator.reportOpenError(e.message ?: "Failed reading bundled workout.")
            ewoEditorSnapshotState.value = ewoEditorCoordinator.snapshot()
            return
        }
        syncEwoEditorProfileContext()
        if (ewoEditorCoordinator.openJson(content, fileName, openedFromBundledLibrary = true)) {
            ewoEditorSnapshotState.value = ewoEditorCoordinator.snapshot()
            uiState.screen.value = AppScreen.EWO_EDITOR
        } else {
            ewoEditorSnapshotState.value = ewoEditorCoordinator.snapshot()
        }
    }

    fun onSelectTelemetryOnlyMode() {
        sessionOrchestrator.onTelemetryOnlyModeSelected()
        WorkoutSelectionSettingsStorage.save(
            appContext, SessionSetupMode.TELEMETRY_ONLY, null, null,
        )
    }

    fun onSessionSetupModeSelected(mode: SessionSetupMode) {
        sessionOrchestrator.onSessionSetupModeSelected(mode)
    }

    internal fun onActiveMenuSetupStepChanged(step: MenuSetupStep?) {
        activeMenuSetupStepState.value = step
    }

    fun onStartSession() {
        sessionCoordinator.onStartSession()
    }

    fun onEndSessionAndGoToSummary() {
        sessionCoordinator.onEndSessionAndGoToSummary()
    }

    fun onWorkoutCompletePresented() {
        logPostWorkoutContinuationTestMarker(
            event = "workout_complete_dialog_presented",
        )
    }

    /**
     * Continues an already-finished structured workout as rider-controlled free ride.
     */
    fun onContinueRideAfterWorkoutComplete() {
        val preparedExitWindow = sessionOrchestrator.hasPreparedPostWorkoutExitWindow()
        logPostWorkoutContinuationTestMarker(
            event = "continue_ride_tapped",
            context = mapOf(
                "preparedExitWindow" to preparedExitWindow.toString(),
            ),
        )
        if (preparedExitWindow ||
            sessionOrchestrator.preparePostWorkoutExitWindowAfterCompletion()
        ) {
            armPreparedPostWorkoutCompletionChoice(PostWorkoutCompletionChoice.CONTINUE)
            return
        }
        armContinueRideHardCutover()
    }

    fun onEndSessionAfterWorkoutComplete() {
        val preparedExitWindow = sessionOrchestrator.hasPreparedPostWorkoutExitWindow()
        logPostWorkoutContinuationTestMarker(
            event = "workout_complete_summary_tapped",
            context = mapOf(
                "preparedExitWindow" to preparedExitWindow.toString(),
                "requiresPreparedExitWindow" to
                    requiresPreparedPostWorkoutExitWindow(PostWorkoutCompletionChoice.SUMMARY).toString(),
            ),
        )
        if (preparedExitWindow &&
            requiresPreparedPostWorkoutExitWindow(PostWorkoutCompletionChoice.SUMMARY)
        ) {
            armPreparedPostWorkoutCompletionChoice(PostWorkoutCompletionChoice.SUMMARY)
            return
        }
        hidePostWorkoutContinuationHandoff()
        sessionOrchestrator.endWorkoutCompleteSessionToSummaryKeepingConnection()
    }

    /**
     * Continues CONNECTING flow after user acknowledges timeout prompt.
     */
    fun onConnectingTimeoutKeepWaiting() {
        sessionOrchestrator.onConnectingTimeoutKeepWaiting()
        aiAssistantFacade.refresh(force = true)
    }

    /**
     * Restarts CONNECTING flow after timeout prompt.
     */
    fun onConnectingTimeoutRetry() {
        sessionOrchestrator.onConnectingTimeoutRetry()
        aiAssistantFacade.refresh(force = true)
    }

    /**
     * Exits CONNECTING flow to MENU from timeout prompt.
     */
    fun onConnectingTimeoutBackToMenu() {
        hidePostWorkoutContinuationHandoff()
        sessionOrchestrator.onConnectingTimeoutBackToMenu()
        aiAssistantFacade.refresh(
            forcePhase = AiPhase.MENU,
            force = true,
        )
    }

    /**
     * Executes a debug-only request-control validation command from adb intent extras.
     *
     * If the app is not already in CONNECTING/SESSION, this seeds CONNECTING first so
     * rollback paths can be verified without requiring an active BLE transport.
     */
    internal fun onDebugSessionValidationCommand(command: DebugSessionValidationCommand): String {
        if (!BuildConfig.DEBUG) {
            return "Ignored debug session validation command in non-debug build."
        }

        val startFlowActive = when (uiState.screen.value) {
            AppScreen.CONNECTING,
            AppScreen.SESSION -> true

            else -> false
        }
        if (!startFlowActive) {
            sessionOrchestrator.beginConnectFlowForTest()
        }

        val scenarioLabel = when (command.scenario) {
            DebugSessionValidationCommand.Scenario.REQUEST_CONTROL_REJECTED -> {
                sessionOrchestrator.simulateRequestControlRejectedForTest(command.rejectedResultCode)
                "request_control_rejected(resultCode=${command.rejectedResultCode})"
            }

            DebugSessionValidationCommand.Scenario.REQUEST_CONTROL_TIMEOUT -> {
                sessionOrchestrator.simulateRequestControlTimeoutForTest()
                "request_control_timeout"
            }
        }

        aiAssistantFacade.refresh(
            forcePhase = AiPhase.MENU,
            force = true,
        )
        return "Debug session validation command executed: $scenarioLabel"
    }

    /**
     * Executes a debug-only UI automation command from adb intent extras.
     *
     * This prefers state-based mutations over coordinate-driven taps so validation can move
     * directly to the intended menu/setup state while still using the production selection paths.
     */
    internal fun onDebugAutomationCommand(command: DebugAutomationCommand): String {
        if (!BuildConfig.DEBUG) {
            return "Ignored debug automation command in non-debug build."
        }

        return when (command.type) {
            DebugAutomationCommand.Type.OPEN_MENU_STEP -> {
                val requestedStep = requireNotNull(command.menuStep)
                val menuOpen = ensureMenuAvailableForDebugAutomation()
                if (!menuOpen) {
                    "Debug automation refused while active session screen=${uiState.screen.value}"
                } else {
                    when (requestedStep) {
                        MenuSetupStep.FILE_BASED -> onSessionSetupModeSelected(SessionSetupMode.FILE)
                        else -> Unit
                    }
                    activeMenuSetupStepState.value = requestedStep
                    "Debug automation opened menu step: ${requestedStep.name}"
                }
            }

            DebugAutomationCommand.Type.OPEN_NEW_EWO_EDITOR -> {
                val menuOpen = ensureMenuAvailableForDebugAutomation()
                if (!menuOpen) {
                    "Debug automation refused editor open while active session screen=${uiState.screen.value}"
                } else {
                    ewoEditorCoordinator.newDocument()
                    ewoEditorSnapshotState.value = ewoEditorCoordinator.snapshot()
                    onOpenEwoEditor()
                    "Debug automation opened a new EWO editor document."
                }
            }

            DebugAutomationCommand.Type.SELECT_TELEMETRY_ONLY_MODE -> {
                val menuOpen = ensureMenuAvailableForDebugAutomation()
                if (!menuOpen) {
                    "Debug automation refused telemetry-only selection while active session screen=${uiState.screen.value}"
                } else {
                    onSelectTelemetryOnlyMode()
                    activeMenuSetupStepState.value = MenuSetupStep.FILE_BASED
                    "Debug automation selected telemetry-only mode."
                }
            }

            DebugAutomationCommand.Type.SELECT_DOCUMENTS_WORKOUT -> {
                val requestedFileName = requireNotNull(command.workoutFileName)
                val menuOpen = ensureMenuAvailableForDebugAutomation()
                if (!menuOpen) {
                    "Debug automation refused workout selection while active session screen=${uiState.screen.value}"
                } else if (!documentsFolderUiState.readyState.value) {
                    "Debug automation could not select documents workout because no folder is bound."
                } else {
                    onRefreshDocumentsFolderWorkoutsRequested()
                    val selectedOption = resolveDocumentsWorkoutOptionByFileName(requestedFileName)
                    if (selectedOption == null) {
                        debugAutomationWorkoutNotFoundMessage(requestedFileName)
                    } else {
                        onDocumentsFolderWorkoutSelected(selectedOption.uriString)
                        onSessionSetupModeSelected(SessionSetupMode.FILE)
                        activeMenuSetupStepState.value = MenuSetupStep.FILE_BASED
                        "Debug automation selected documents workout: ${selectedOption.displayName}"
                    }
                }
            }

            DebugAutomationCommand.Type.SELECT_WORKOUT_FILE_PATH -> {
                val workoutFilePath = requireNotNull(command.workoutFilePath)
                val menuOpen = ensureMenuAvailableForDebugAutomation()
                if (!menuOpen) {
                    "Debug automation refused file-path workout selection while active session screen=${uiState.screen.value}"
                } else {
                    val stagingDirectory = appContext.getExternalFilesDir(
                        DebugWorkoutAutomationFiles.STAGING_DIRECTORY_NAME,
                    )
                    val resolvedTarget = DebugWorkoutAutomationFiles.resolveSelectionTarget(
                        requestedPath = workoutFilePath,
                        stagingDirectory = stagingDirectory,
                        appOwnedRoots = listOfNotNull(
                            appContext.filesDir,
                            appContext.cacheDir,
                            appContext.externalCacheDir,
                            appContext.getExternalFilesDir(null),
                            stagingDirectory,
                        ),
                    )
                    val resolvedFile = when (resolvedTarget) {
                        DebugWorkoutSelectionTarget.InvalidRequest -> {
                            return "Debug automation refused workout selection because the staged path was blank."
                        }

                        DebugWorkoutSelectionTarget.MissingStagingDirectory -> {
                            return "Debug automation could not access the staged workout directory."
                        }

                        is DebugWorkoutSelectionTarget.UnsupportedAbsolutePath -> {
                            return "Debug automation refuses raw shared-storage paths. " +
                                "Stage the workout under ${DebugWorkoutAutomationFiles.STAGING_DIRECTORY_NAME}/ " +
                                "or pass an app-owned absolute path."
                        }

                        is DebugWorkoutSelectionTarget.Resolved -> resolvedTarget.file
                    }
                    if (!resolvedFile.isFile) {
                        return "Debug automation could not find staged workout file: ${resolvedFile.name}"
                    }
                    val workoutFileUri = Uri.fromFile(resolvedFile)
                    onWorkoutFileSelected(workoutFileUri)
                    onSessionSetupModeSelected(SessionSetupMode.FILE)
                    activeMenuSetupStepState.value = MenuSetupStep.FILE_BASED
                    val displayName = resolvedFile.name
                    "Debug automation selected workout from file path: $displayName"
                }
            }

            DebugAutomationCommand.Type.PREPARE_IMPORTED_HR_VALIDATION -> {
                val requestedFileName = requireNotNull(command.workoutFileName)
                val requestedStep = command.menuStep ?: MenuSetupStep.SUMMARY
                val menuOpen = ensureMenuAvailableForDebugAutomation()
                if (!menuOpen) {
                    "Debug automation refused imported-HR prepare while active session screen=${uiState.screen.value}"
                } else if (!documentsFolderUiState.readyState.value) {
                    "Debug automation could not prepare imported-HR validation because no folder is bound."
                } else {
                    onRefreshDocumentsFolderWorkoutsRequested()
                    val selectedOption = resolveDocumentsWorkoutOptionByFileName(requestedFileName)
                    if (selectedOption == null) {
                        debugAutomationWorkoutNotFoundMessage(requestedFileName)
                    } else {
                        command.mockScenario?.let { scenario ->
                            onMockTrainerModeChanged(true)
                            debugAutomationUiState.armMockTrainerScenario(scenario)
                        }
                        onDocumentsFolderWorkoutSelected(selectedOption.uriString)
                        onSessionSetupModeSelected(SessionSetupMode.FILE)
                        activeMenuSetupStepState.value = requestedStep
                        "Debug automation prepared imported-HR validation workout=${selectedOption.displayName} " +
                            "menuStep=${requestedStep.name} " +
                            "mockScenario=${command.mockScenario?.wireName ?: "none"}"
                    }
                }
            }

            DebugAutomationCommand.Type.PREPARE_TRAINER_WARM_CONNECTION -> {
                val menuOpen = ensureMenuAvailableForDebugAutomation()
                if (!menuOpen) {
                    "Debug automation refused trainer warm prepare while active session screen=${uiState.screen.value}"
                } else if (!hasSelectedFtmsDevice()) {
                    "Debug automation could not prepare trainer warm connection because no FTMS trainer is selected."
                } else {
                    activeMenuSetupStepState.value = MenuSetupStep.DEVICES
                    val accepted = sessionOrchestrator.prepareTrainerWarmConnectionInMenu()
                    if (accepted) {
                        "Debug automation requested trainer warm connection for ${deviceSelectionUiState.ftmsDevice.displayNameState.value}."
                    } else {
                        "Debug automation failed to prepare trainer warm connection for ${deviceSelectionUiState.ftmsDevice.displayNameState.value}."
                    }
                }
            }

            DebugAutomationCommand.Type.ENABLE_MOCK_TRAINER_MODE -> {
                onMockTrainerModeChanged(true)
                "Debug automation enabled mock trainer mode."
            }

            DebugAutomationCommand.Type.DISABLE_MOCK_TRAINER_MODE -> {
                onMockTrainerModeChanged(false)
                "Debug automation disabled mock trainer mode."
            }

            DebugAutomationCommand.Type.RELEASE_TRAINER_WARM_CONNECTION -> {
                val menuOpen = ensureMenuAvailableForDebugAutomation()
                if (!menuOpen) {
                    "Debug automation refused trainer warm release while active session screen=${uiState.screen.value}"
                } else {
                    activeMenuSetupStepState.value = MenuSetupStep.DEVICES
                    sessionOrchestrator.releaseTrainerWarmConnectionInMenu()
                    "Debug automation released trainer warm connection."
                }
            }

            DebugAutomationCommand.Type.OPEN_BASELINE_FITNESS_TEST -> {
                if (uiState.screen.value == AppScreen.CONNECTING ||
                    uiState.screen.value == AppScreen.SESSION ||
                    uiState.screen.value == AppScreen.STOPPING
                ) {
                    "Debug automation refused baseline open while active session screen=${uiState.screen.value}"
                } else {
                    onOpenBaselineFitnessTestRequested()
                    "Debug automation opened baseline fitness test."
                }
            }

            DebugAutomationCommand.Type.START_BASELINE_FITNESS_TEST -> {
                if (uiState.screen.value != AppScreen.BASELINE_FITNESS_TEST) {
                    "Debug automation start_baseline_fitness_test requires BASELINE_FITNESS_TEST screen, current=${uiState.screen.value}"
                } else {
                    onStartBaselineFitnessTestRequested()
                    "Debug automation requested baseline fitness test start."
                }
            }

            DebugAutomationCommand.Type.BACK_FROM_BASELINE_FITNESS_TEST -> {
                if (uiState.screen.value != AppScreen.BASELINE_FITNESS_TEST) {
                    "Debug automation back_from_baseline_fitness_test requires BASELINE_FITNESS_TEST screen, current=${uiState.screen.value}"
                } else {
                    onBackFromBaselineFitnessTest()
                    "Debug automation returned from baseline fitness test to menu."
                }
            }

            DebugAutomationCommand.Type.CONTINUE_RIDE_AFTER_WORKOUT_COMPLETE -> {
                if (uiState.screen.value != AppScreen.SESSION) {
                    "Debug automation continue_ride_after_workout_complete requires SESSION screen, current=${uiState.screen.value}"
                } else {
                    val accepted = sessionOrchestrator.continueRideAfterWorkoutComplete()
                    if (accepted) {
                        "Debug automation continued ride after workout completion."
                    } else {
                        "Debug automation continue_ride_after_workout_complete was ignored because the workout was not in a completed session state."
                    }
                }
            }

            DebugAutomationCommand.Type.CONTINUE_RIDE_VIA_HARD_CUTOVER_PROBE -> {
                if (uiState.screen.value != AppScreen.SESSION) {
                    "Debug automation continue_ride_via_hard_cutover_probe requires SESSION screen, current=${uiState.screen.value}"
                } else if (!uiState.runner.value.done) {
                    "Debug automation continue_ride_via_hard_cutover_probe requires a completed workout session."
                } else {
                    armHardCutoverContinueRideProbe()
                }
            }

            DebugAutomationCommand.Type.DISCONNECT_POST_WORKOUT_FREERIDE_TRANSPORT -> {
                if (uiState.screen.value != AppScreen.SESSION) {
                    "Debug automation disconnect_post_workout_freeride_transport requires SESSION screen, current=${uiState.screen.value}"
                } else {
                    val accepted = sessionOrchestrator.disconnectPostWorkoutFreerideTransportForDebug()
                    if (accepted) {
                        "Debug automation disconnected FTMS transport during post-workout freeride."
                    } else {
                        "Debug automation disconnect_post_workout_freeride_transport was ignored because post-workout freeride FTMS transport was not active."
                    }
                }
            }

            DebugAutomationCommand.Type.START_SESSION -> {
                if (uiState.screen.value != AppScreen.MENU) {
                    "Debug automation start_session requires MENU screen, current=${uiState.screen.value}"
                } else {
                    activeMenuSetupStepState.value = MenuSetupStep.SUMMARY
                    onStartSession()
                    "Debug automation requested session start from summary."
                }
            }

            DebugAutomationCommand.Type.START_SESSION_IF_READY -> {
                if (uiState.screen.value != AppScreen.MENU) {
                    "Debug automation start_session_if_ready requires MENU screen, current=${uiState.screen.value}"
                } else if (!canStartSession()) {
                    "Debug automation start_session_if_ready blocked: ${debugAutomationStatusSummary(refreshDocumentsFolder = false)}"
                } else {
                    activeMenuSetupStepState.value = MenuSetupStep.SUMMARY
                    onStartSession()
                    "Debug automation started session because setup was ready."
                }
            }

            DebugAutomationCommand.Type.START_CLEAN_TELEMETRY_ONLY_SESSION -> {
                val menuOpen = ensureMenuAvailableForDebugAutomation()
                if (!menuOpen) {
                    "Debug automation refused clean telemetry-only start while active session screen=${uiState.screen.value}"
                } else {
                    onSelectTelemetryOnlyMode()
                    activeMenuSetupStepState.value = MenuSetupStep.SUMMARY
                    if (!canStartSession()) {
                        "Debug automation clean telemetry-only start blocked: ${debugAutomationStatusSummary(refreshDocumentsFolder = false)}"
                    } else {
                        onStartSession()
                        "Debug automation started a clean telemetry-only session."
                    }
                }
            }

            DebugAutomationCommand.Type.END_SESSION_AND_GO_TO_SUMMARY -> {
                when (uiState.screen.value) {
                    AppScreen.SESSION -> {
                        onEndSessionAndGoToSummary()
                        "Debug automation requested end_session_and_go_to_summary."
                    }

                    AppScreen.STOPPING -> {
                        "Debug automation end_session_and_go_to_summary ignored because stop flow is already active."
                    }

                    else -> {
                        "Debug automation end_session_and_go_to_summary requires SESSION screen, current=${uiState.screen.value}"
                    }
                }
            }

            DebugAutomationCommand.Type.FORCE_CLEAN_MENU_RESET -> {
                forceResetToCleanMenuForDebugAutomation()
            }

            DebugAutomationCommand.Type.BACK_TO_MENU -> {
                val currentScreen = uiState.screen.value
                when {
                    currentScreen == AppScreen.EWO_EDITOR -> {
                        uiState.screen.value = AppScreen.MENU
                        activeMenuSetupStepState.value = null
                        aiAssistantFacade.refresh(
                            forcePhase = AiPhase.MENU,
                            force = true,
                        )
                        "Debug automation forced menu from $currentScreen."
                    }

                    currentScreen == AppScreen.MENU -> {
                        activeMenuSetupStepState.value = null
                        "Debug automation already at menu."
                    }

                    currentScreen == AppScreen.SUMMARY -> {
                        onBackToMenu()
                        activeMenuSetupStepState.value = null
                        "Debug automation returned from summary to menu."
                    }

                    currentScreen == AppScreen.BASELINE_FITNESS_TEST -> {
                        onBackFromBaselineFitnessTest()
                        "Debug automation returned from baseline fitness test to menu."
                    }

                    else -> {
                        "Debug automation refused to leave active session screen=$currentScreen"
                    }
                }
            }

            DebugAutomationCommand.Type.REQUEST_TRAINER_CONTROL -> {
                val allowed = allowTrainerProbeAutomationFromCurrentScreen()
                if (!allowed) {
                    "Debug automation request_trainer_control requires MENU or SESSION screen, current=${uiState.screen.value}"
                } else {
                    val accepted = sessionOrchestrator.requestTrainerControlForExternalUse()
                    if (accepted) {
                        "Debug automation requested trainer control."
                    } else {
                        "Debug automation request_trainer_control blocked: ${debugAutomationStatusSummary(refreshDocumentsFolder = false)}"
                    }
                }
            }

            DebugAutomationCommand.Type.SET_TRAINER_POWER -> {
                val targetWatts = requireNotNull(command.targetWatts)
                val allowed = allowTrainerProbeAutomationFromCurrentScreen()
                if (!allowed) {
                    "Debug automation set_trainer_power requires MENU or SESSION screen, current=${uiState.screen.value}"
                } else if (!uiState.ftmsReady.value) {
                    "Debug automation set_trainer_power blocked because the FTMS link is not ready."
                } else if (!uiState.ftmsControlGranted.value) {
                    "Debug automation set_trainer_power requires granted trainer control; send request_trainer_control first."
                } else {
                    sessionOrchestrator.setExternalTargetPower(targetWatts)
                    if (command.legacyAlias == "clear_trainer_power") {
                        "Debug automation legacy alias clear_trainer_power sent trainer power target: 0W. Use set_trainer_power --ei target_watts 0 because no distinct clear/release FTMS command exists on this path."
                    } else {
                        "Debug automation sent trainer power target: ${targetWatts}W."
                    }
                }
            }

            DebugAutomationCommand.Type.DISCONNECT_TRAINER_TRANSPORT -> {
                val allowed = allowTrainerProbeAutomationFromCurrentScreen()
                if (!allowed) {
                    "Debug automation disconnect_trainer_transport requires MENU or SESSION screen, current=${uiState.screen.value}"
                } else if (mockTrainerModeEnabledState.value) {
                    "Debug automation disconnect_trainer_transport is unavailable in mock trainer mode."
                } else {
                    val accepted = sessionOrchestrator.disconnectTrainerTransportForDebug()
                    if (accepted) {
                        "Debug automation started a direct FTMS transport disconnect."
                    } else {
                        "Debug automation disconnect_trainer_transport blocked: ${debugAutomationStatusSummary(refreshDocumentsFolder = false)}"
                    }
                }
            }

            DebugAutomationCommand.Type.STOP_TRAINER_WORKOUT -> {
                val allowed = allowTrainerProbeAutomationFromCurrentScreen()
                if (!allowed) {
                    "Debug automation stop_trainer_workout requires MENU or SESSION screen, current=${uiState.screen.value}"
                } else if (mockTrainerModeEnabledState.value) {
                    "Debug automation stop_trainer_workout is unavailable in mock trainer mode."
                } else {
                    val accepted = sessionOrchestrator.stopTrainerWorkoutForDebug()
                    if (accepted) {
                        "Debug automation sent direct FTMS STOP."
                    } else {
                        "Debug automation stop_trainer_workout blocked: ${debugAutomationStatusSummary(refreshDocumentsFolder = false)}"
                    }
                }
            }

            DebugAutomationCommand.Type.RESET_TRAINER -> {
                val allowed = allowTrainerProbeAutomationFromCurrentScreen()
                if (!allowed) {
                    "Debug automation reset_trainer requires MENU or SESSION screen, current=${uiState.screen.value}"
                } else if (mockTrainerModeEnabledState.value) {
                    "Debug automation reset_trainer is unavailable in mock trainer mode."
                } else {
                    val accepted = sessionOrchestrator.resetTrainerForDebug()
                    if (accepted) {
                        "Debug automation sent direct FTMS RESET."
                    } else {
                        "Debug automation reset_trainer blocked: ${debugAutomationStatusSummary(refreshDocumentsFolder = false)}"
                    }
                }
            }

            DebugAutomationCommand.Type.SHOW_SESSION_DEBUG_PROBE -> {
                if (uiState.screen.value != AppScreen.SESSION) {
                    "Debug automation show_session_debug_probe requires SESSION screen, current=${uiState.screen.value}"
                } else {
                    clearArmedSessionDebugProbe()
                    showSessionDebugProbeOverlay(
                        title = command.probeTitle,
                        message = requireNotNull(command.probeMessage),
                    )
                    "Debug automation showed session debug probe overlay."
                }
            }

            DebugAutomationCommand.Type.ARM_SESSION_DEBUG_PROBE_WHEN_PEDALING -> {
                armSessionDebugProbeWhenPedaling(
                    title = command.probeTitle,
                    message = requireNotNull(command.probeMessage),
                )
            }

            DebugAutomationCommand.Type.HIDE_SESSION_DEBUG_PROBE -> {
                clearArmedSessionDebugProbe()
                hideSessionDebugProbeOverlay(reason = "debug_command")
                "Debug automation hid session debug probe overlay."
            }

            DebugAutomationCommand.Type.DUMP_STATUS -> debugAutomationStatusSummary(refreshDocumentsFolder = true)
        }
    }

    /**
     * Forces debug validation back to the same baseline menu state that a cold launch exposes.
     *
     * Device selections and folder grants stay intact, but session/runtime state, summary
     * remnants, and workout selections are cleared so the next automation step starts from a
     * deterministic menu baseline.
     */
    private fun forceResetToCleanMenuForDebugAutomation(): String {
        cancelPendingContinueRideHardCutover()
        cancelPendingPostWorkoutCompletionChoice()
        cancelPostWorkoutContinuationHandoffCompletion()
        hidePostWorkoutContinuationHandoff()
        clearArmedSessionDebugProbe()
        hideSessionDebugProbeOverlay(reason = "force_clean_menu_reset")
        cancelPendingHardCutoverContinueRideProbe()
        sessionOrchestrator.forceResetToCleanMenuForDebug()
        summaryExitCoordinator.resetForMenuReturn(summaryExitStatePort)
        uiState.summary.value = null
        uiState.session.value = null
        uiState.timelineSamples.value = emptyList()
        uiState.screen.value = AppScreen.MENU
        uiState.selectedSessionSetupMode.value = SessionSetupMode.FILE
        uiState.selectedWorkout.value = null
        uiState.selectedImportedWorkout.value = null
        uiState.selectedWorkoutFileName.value = null
        uiState.selectedWorkoutStepCount.value = null
        uiState.selectedWorkoutPlannedTss.value = null
        uiState.selectedWorkoutTotalDurationSec.value = null
        uiState.selectedWorkoutImportError.value = null
        uiState.workoutExecutionModeMessage.value = null
        uiState.workoutExecutionModeIsError.value = false
        activeMenuSetupStepState.value = null
        aiAssistantFacade.refresh(
            forcePhase = AiPhase.MENU,
            force = true,
        )
        return "Debug automation forced a clean menu reset."
    }

    /**
     * Replaces the finished workout session with a fresh telemetry-only trainer session.
     *
     * The trainer behavior proved reliable only when the structured workout is ended through
     * the normal stop-flow and the continuation starts as a brand-new telemetry-only session.
     * SessionManager carryover keeps summary/export continuity across that hard cutover.
     */
    private fun armContinueRideHardCutover() {
        cancelPendingContinueRideHardCutover()
        cancelPendingPostWorkoutCompletionChoice()
        cancelPostWorkoutContinuationHandoffCompletion()
        hidePostWorkoutContinuationHandoff()
        logPostWorkoutContinuationTestMarker(
            event = "continue_ride_hard_cutover_fallback_started",
        )
        val completedSummary = uiState.summary.value ?: sessionManager.lastSummary
        if (uiState.screen.value != AppScreen.SESSION || !uiState.runner.value.done) {
            return
        }
        sessionManager.armContinuationCheckpoint()
        onEndSessionAndGoToSummary()
        val deadlineElapsedMs = SystemClock.elapsedRealtime() + CONTINUE_RIDE_HARD_CUTOVER_TIMEOUT_MS
        val runnable = object : Runnable {
            var carryoverPrepared = false

            override fun run() {
                when (uiState.screen.value) {
                    AppScreen.STOPPING,
                    AppScreen.SESSION -> {
                        if (SystemClock.elapsedRealtime() >= deadlineElapsedMs) {
                            continueRideHardCutoverRunnable = null
                            return
                        }
                        mainHandler.postDelayed(this, CONTINUE_RIDE_HARD_CUTOVER_POLL_INTERVAL_MS)
                    }

                    AppScreen.SUMMARY -> {
                        if (!carryoverPrepared) {
                            sessionManager.promoteStoppedSessionToContinuationCarryover()
                            carryoverPrepared = true
                        }
                        onBackToMenu()
                        mainHandler.postDelayed(this, CONTINUE_RIDE_HARD_CUTOVER_POLL_INTERVAL_MS)
                    }

                    AppScreen.MENU -> {
                        if (!sessionOrchestrator.continueRideRestartWindowOpen()) {
                            if (SystemClock.elapsedRealtime() >= deadlineElapsedMs) {
                                continueRideHardCutoverRunnable = null
                                return
                            }
                            mainHandler.postDelayed(this, CONTINUE_RIDE_HARD_CUTOVER_POLL_INTERVAL_MS)
                            return
                        }
                        continueRideHardCutoverRunnable = null
                        val carryoverMetrics = completedSummary ?: sessionManager.lastSummary
                        logPostWorkoutContinuationTestMarker(
                            event = "continue_ride_hard_cutover_restart_window_ready",
                        )
                        sessionManager.armNextStartAsContinuationSegment()
                        carryoverMetrics?.let { summary ->
                            sessionManager.bridgeCumulativeTrainerMetrics(
                                distanceMeters = summary.distanceMeters,
                                totalEnergyKcal = summary.totalEnergyKcal,
                            )
                        }
                        onSelectTelemetryOnlyMode()
                        activeMenuSetupStepState.value = MenuSetupStep.SUMMARY
                        if (canStartSession()) {
                            onStartSession()
                        }
                    }

                    else -> {
                        continueRideHardCutoverRunnable = null
                    }
                }
            }
        }
        continueRideHardCutoverRunnable = runnable
        mainHandler.postDelayed(runnable, CONTINUE_RIDE_HARD_CUTOVER_POLL_INTERVAL_MS)
    }

    private fun cancelPendingContinueRideHardCutover() {
        continueRideHardCutoverRunnable?.let(mainHandler::removeCallbacks)
        continueRideHardCutoverRunnable = null
    }

    /**
     * Consumes the hidden workout-complete exit prep once the trainer window is ready.
     *
     * This keeps the visible dialog one-step while allowing the trainer to spend the
     * rider's decision time on its own exit/dwell requirements.
     */
    private fun armPreparedPostWorkoutCompletionChoice(choice: PostWorkoutCompletionChoice) {
        cancelPendingContinueRideHardCutover()
        cancelPendingPostWorkoutCompletionChoice()
        if (uiState.screen.value != AppScreen.SESSION || !uiState.runner.value.done) {
            return
        }
        logPostWorkoutContinuationTestMarker(
            event = "post_workout_choice_armed",
            context = mapOf("choice" to choice.name.lowercase()),
        )

        val deadlineElapsedMs = SystemClock.elapsedRealtime() + CONTINUE_RIDE_HARD_CUTOVER_TIMEOUT_MS
        val runnable = object : Runnable {
            override fun run() {
                if (preparedPostWorkoutChoiceWindowOpen(choice)) {
                    postWorkoutCompletionChoiceRunnable = null
                    logPostWorkoutContinuationTestMarker(
                        event = "post_workout_choice_window_ready",
                        context = mapOf(
                            "choice" to choice.name.lowercase(),
                            "gate" to preparedPostWorkoutChoiceGateLabel(choice),
                        ),
                    )
                    when (choice) {
                        PostWorkoutCompletionChoice.CONTINUE -> {
                            showPostWorkoutContinuationHandoff()
                            logPostWorkoutContinuationTestMarker(
                                event = "continue_ride_handoff_overlay_shown",
                            )
                            val carryoverSummary = finalizePreparedWorkoutCompletionForContinuation()
                            returnToMenu(preserveSessionDebugProbe = true)
                            sessionManager.armNextStartAsContinuationSegment()
                            carryoverSummary?.let { summary ->
                                sessionManager.bridgeCumulativeTrainerMetrics(
                                    distanceMeters = summary.distanceMeters,
                                    totalEnergyKcal = summary.totalEnergyKcal,
                                )
                            }
                            onSelectTelemetryOnlyMode()
                            activeMenuSetupStepState.value = MenuSetupStep.SUMMARY
                            if (canStartSession()) {
                                onStartSession()
                            }
                            completePostWorkoutContinuationHandoffWhenSessionStarts()
                        }

                        PostWorkoutCompletionChoice.SUMMARY -> {
                            hidePostWorkoutContinuationHandoff()
                            finalizePreparedWorkoutCompletionToSummary()
                        }
                    }
                    return
                }

                if (SystemClock.elapsedRealtime() >= deadlineElapsedMs) {
                    postWorkoutCompletionChoiceRunnable = null
                    hidePostWorkoutContinuationHandoff()
                    logPostWorkoutContinuationTestMarker(
                        event = "post_workout_choice_window_timeout",
                        context = mapOf(
                            "choice" to choice.name.lowercase(),
                            "gate" to preparedPostWorkoutChoiceGateLabel(choice),
                        ),
                    )
                    when (choice) {
                        PostWorkoutCompletionChoice.CONTINUE -> armContinueRideHardCutover()
                        PostWorkoutCompletionChoice.SUMMARY -> onEndSessionAndGoToSummary()
                    }
                    return
                }

                mainHandler.postDelayed(this, CONTINUE_RIDE_HARD_CUTOVER_POLL_INTERVAL_MS)
            }
        }
        postWorkoutCompletionChoiceRunnable = runnable
        mainHandler.post(runnable)
    }

    /**
     * Uses the strict reconnect gate only for Continue ride; SUMMARY can finish earlier.
     */
    private fun preparedPostWorkoutChoiceWindowOpen(choice: PostWorkoutCompletionChoice): Boolean {
        return when (choice) {
            PostWorkoutCompletionChoice.CONTINUE -> sessionOrchestrator.continueRideRestartWindowOpen()
            PostWorkoutCompletionChoice.SUMMARY -> sessionOrchestrator.preparedPostWorkoutSummaryWindowOpen()
        }
    }

    private fun preparedPostWorkoutChoiceGateLabel(choice: PostWorkoutCompletionChoice): String {
        return when (choice) {
            PostWorkoutCompletionChoice.CONTINUE -> "continue_restart_window"
            PostWorkoutCompletionChoice.SUMMARY -> "summary_window"
        }
    }

    private fun cancelPendingPostWorkoutCompletionChoice() {
        postWorkoutCompletionChoiceRunnable?.let(mainHandler::removeCallbacks)
        postWorkoutCompletionChoiceRunnable = null
    }

    private fun cancelPostWorkoutContinuationHandoffCompletion() {
        postWorkoutContinuationHandoffCompletionRunnable?.let(mainHandler::removeCallbacks)
        postWorkoutContinuationHandoffCompletionRunnable = null
    }

    private fun showPostWorkoutContinuationHandoff() {
        uiState.postWorkoutContinuationHandoffVisible.value = true
    }

    private fun hidePostWorkoutContinuationHandoff() {
        if (uiState.postWorkoutContinuationHandoffVisible.value) {
            logPostWorkoutContinuationTestMarker(
                event = "continue_ride_handoff_overlay_hidden",
            )
        }
        cancelPostWorkoutContinuationHandoffCompletion()
        uiState.postWorkoutContinuationHandoffVisible.value = false
    }

    /**
     * Keeps the post-workout handoff overlay visible until the fresh telemetry-only session
     * has replaced the completed workout session in the UI.
     */
    private fun completePostWorkoutContinuationHandoffWhenSessionStarts() {
        cancelPostWorkoutContinuationHandoffCompletion()
        val deadlineElapsedMs = SystemClock.elapsedRealtime() + CONTINUE_RIDE_HARD_CUTOVER_TIMEOUT_MS
        val runnable = object : Runnable {
            override fun run() {
                val handoffReady = isPostWorkoutContinuationHandoffReady(
                    screen = uiState.screen.value,
                    selectedSessionSetupMode = uiState.selectedSessionSetupMode.value,
                    ftmsReady = uiState.ftmsReady.value,
                    ftmsControlGranted = uiState.ftmsControlGranted.value,
                    runnerDone = uiState.runner.value.done,
                )
                if (handoffReady) {
                    postWorkoutContinuationHandoffCompletionRunnable = null
                    logPostWorkoutContinuationTestMarker(
                        event = "continue_ride_handoff_session_ready",
                    )
                    hidePostWorkoutContinuationHandoff()
                    return
                }
                if (SystemClock.elapsedRealtime() >= deadlineElapsedMs ||
                    uiState.screen.value == AppScreen.SUMMARY ||
                    uiState.connectionIssueMessage.value != null
                ) {
                    postWorkoutContinuationHandoffCompletionRunnable = null
                    logPostWorkoutContinuationTestMarker(
                        event = "continue_ride_handoff_aborted",
                        context = mapOf("screen" to uiState.screen.value.name),
                    )
                    hidePostWorkoutContinuationHandoff()
                    return
                }
                mainHandler.postDelayed(this, CONTINUE_RIDE_HARD_CUTOVER_POLL_INTERVAL_MS)
            }
        }
        postWorkoutContinuationHandoffCompletionRunnable = runnable
        mainHandler.post(runnable)
    }

    private fun logPostWorkoutContinuationTestMarker(
        event: String,
        context: Map<String, String> = emptyMap(),
    ) {
        AppLog.testMarker(
            event = event,
            context = context + mapOf(
                "screen" to uiState.screen.value.name,
                "handoffVisible" to uiState.postWorkoutContinuationHandoffVisible.value.toString(),
            ),
        )
    }

    internal fun onSessionDebugProbeSignal(signal: SessionDebugProbeSignal) {
        if (!canAcceptSessionDebugProbeSignal(
                probeVisible = uiState.sessionDebugProbeVisible.value,
                priorSignalCount = uiState.sessionDebugProbeLastSignalCount.value,
            )
        ) {
            recordSessionDebugProbeEvent(
                event = "signal_ignored",
                title = uiState.sessionDebugProbeTitle.value,
                message = uiState.sessionDebugProbeMessage.value,
                signal = signal.wireName,
                signalLabel = sessionDebugProbeSignalLabel(signal),
                signalCount = uiState.sessionDebugProbeLastSignalCount.value,
                reason = if (uiState.sessionDebugProbeVisible.value) {
                    "probe_locked_after_first_signal"
                } else {
                    "probe_not_visible"
                },
                powerW = uiState.bikeData.value?.instantaneousPowerW,
                cadenceRpm = uiState.bikeData.value?.instantaneousCadenceRpm?.roundToInt(),
            )
            AppLog.testMarker(
                event = "session_debug_probe_signal_ignored",
                context = mapOf(
                    "signal" to signal.wireName,
                    "screen" to uiState.screen.value.name,
                    "visible" to uiState.sessionDebugProbeVisible.value.toString(),
                    "priorSignalCount" to uiState.sessionDebugProbeLastSignalCount.value.toString(),
                ),
            )
            return
        }
        val bikeData = uiState.bikeData.value
        val signalLabel = sessionDebugProbeSignalLabel(signal)
        val signalCount = uiState.sessionDebugProbeLastSignalCount.value + 1
        val powerW = bikeData?.instantaneousPowerW
        val cadenceRpm = bikeData?.instantaneousCadenceRpm?.roundToInt()
        val signalAtEpochMs = System.currentTimeMillis()
        uiState.sessionDebugProbeLastSignalLabel.value = signalLabel
        uiState.sessionDebugProbeLastSignalCount.value = signalCount
        uiState.sessionDebugProbeLastSignalAtEpochMs.value = signalAtEpochMs
        recordSessionDebugProbeEvent(
            event = "signal",
            title = uiState.sessionDebugProbeTitle.value,
            message = uiState.sessionDebugProbeMessage.value,
            signal = signal.wireName,
            signalLabel = signalLabel,
            signalCount = signalCount,
            powerW = powerW,
            cadenceRpm = cadenceRpm,
        )
        AppLog.testMarker(
            event = "session_debug_probe_signal",
            context = mapOf(
                "signal" to signal.wireName,
                "signalLabel" to signalLabel,
                "signalCount" to signalCount.toString(),
                "screen" to uiState.screen.value.name,
                "title" to (uiState.sessionDebugProbeTitle.value ?: "none"),
                "message" to (uiState.sessionDebugProbeMessage.value ?: "none"),
                "powerW" to (powerW?.toString() ?: "none"),
                "cadenceRpm" to (cadenceRpm?.toString() ?: "none"),
            ),
        )
    }

    private fun showSessionDebugProbeOverlay(
        title: String?,
        message: String,
    ) {
        clearArmedSessionDebugProbe()
        uiState.sessionDebugProbeTitle.value = title
        uiState.sessionDebugProbeMessage.value = message
        uiState.sessionDebugProbeLastSignalLabel.value = null
        uiState.sessionDebugProbeLastSignalCount.value = 0
        uiState.sessionDebugProbeLastSignalAtEpochMs.value = null
        uiState.sessionDebugProbeVisible.value = true
        recordSessionDebugProbeEvent(
            event = "shown",
            title = title,
            message = message,
            powerW = uiState.bikeData.value?.instantaneousPowerW,
            cadenceRpm = uiState.bikeData.value?.instantaneousCadenceRpm?.roundToInt(),
        )
        AppLog.testMarker(
            event = "session_debug_probe_shown",
            context = mapOf(
                "screen" to uiState.screen.value.name,
                "title" to (title ?: "none"),
                "message" to message,
            ),
        )
    }

    private fun hideSessionDebugProbeOverlay(reason: String) {
        val wasVisible = uiState.sessionDebugProbeVisible.value
        val priorTitle = uiState.sessionDebugProbeTitle.value
        val priorMessage = uiState.sessionDebugProbeMessage.value
        val priorSignalLabel = uiState.sessionDebugProbeLastSignalLabel.value
        val priorSignalCount = uiState.sessionDebugProbeLastSignalCount.value
        val priorBikeData = uiState.bikeData.value
        uiState.sessionDebugProbeVisible.value = false
        uiState.sessionDebugProbeTitle.value = null
        uiState.sessionDebugProbeMessage.value = null
        uiState.sessionDebugProbeLastSignalLabel.value = null
        uiState.sessionDebugProbeLastSignalCount.value = 0
        uiState.sessionDebugProbeLastSignalAtEpochMs.value = null
        if (!wasVisible) {
            return
        }
        recordSessionDebugProbeEvent(
            event = "hidden",
            title = priorTitle,
            message = priorMessage,
            signalLabel = priorSignalLabel,
            signalCount = priorSignalCount,
            reason = reason,
            powerW = priorBikeData?.instantaneousPowerW,
            cadenceRpm = priorBikeData?.instantaneousCadenceRpm?.roundToInt(),
        )
        AppLog.testMarker(
            event = "session_debug_probe_hidden",
            context = mapOf(
                "screen" to uiState.screen.value.name,
                "reason" to reason,
                "title" to (priorTitle ?: "none"),
                "message" to (priorMessage ?: "none"),
                "lastSignal" to (priorSignalLabel ?: "none"),
                "lastSignalCount" to priorSignalCount.toString(),
            ),
        )
    }

    /**
     * Arms a probe overlay to appear only after the app is back on a live session-ready surface.
     *
     * This removes fragile chat timing from live-trainer testing. The current gate intentionally
     * waits for the session screen plus a ready FTMS link, but it does not require a fresh
     * cadence transition because some validation prompts must also work while telemetry is idle.
     */
    private fun armSessionDebugProbeWhenPedaling(
        title: String?,
        message: String,
    ): String {
        clearArmedSessionDebugProbe()
        armedSessionDebugProbeTitle = title
        armedSessionDebugProbeMessage = message
        val autoShowGate = currentSessionDebugProbeAutoShowGate()
        recordSessionDebugProbeEvent(
            event = "armed",
            title = title,
            message = message,
            reason = autoShowGate.blocker.wireName,
            powerW = uiState.bikeData.value?.instantaneousPowerW,
            cadenceRpm = uiState.bikeData.value?.instantaneousCadenceRpm?.roundToInt(),
        )
        AppLog.testMarker(
            event = "session_debug_probe_armed",
            context = mapOf(
                "screen" to uiState.screen.value.name,
                "title" to (title ?: "none"),
                "message" to message,
                "autoShowReady" to autoShowGate.ready.toString(),
                "autoShowBlocker" to autoShowGate.blocker.wireName,
            ),
        )
        val runnable = object : Runnable {
            override fun run() {
                if (!hasArmedSessionDebugProbe()) {
                    sessionDebugProbeArmRunnable = null
                    return
                }
                if (sessionDebugProbeAutoShowReady()) {
                    val armedTitle = armedSessionDebugProbeTitle
                    val armedMessage = armedSessionDebugProbeMessage
                    sessionDebugProbeArmRunnable = null
                    showSessionDebugProbeOverlay(
                        title = armedTitle,
                        message = requireNotNull(armedMessage),
                    )
                    recordSessionDebugProbeEvent(
                        event = "auto_shown",
                        title = armedTitle,
                        message = armedMessage,
                        powerW = uiState.bikeData.value?.instantaneousPowerW,
                        cadenceRpm = uiState.bikeData.value?.instantaneousCadenceRpm?.roundToInt(),
                    )
                    AppLog.testMarker(
                        event = "session_debug_probe_auto_shown",
                        context = mapOf(
                            "screen" to uiState.screen.value.name,
                            "title" to (armedTitle ?: "none"),
                            "message" to armedMessage,
                            "cadenceRpm" to (
                                uiState.bikeData.value?.instantaneousCadenceRpm?.roundToInt()
                                    ?.toString() ?: "none"
                            ),
                        ),
                    )
                    return
                }
                mainHandler.postDelayed(this, SESSION_DEBUG_PROBE_ARM_POLL_INTERVAL_MS)
            }
        }
        sessionDebugProbeArmRunnable = runnable
        mainHandler.post(runnable)
        return "Debug automation armed session debug probe for next live pedaling state."
    }

    private fun sessionDebugProbeAutoShowReady(): Boolean {
        return currentSessionDebugProbeAutoShowGate().ready
    }

    private fun currentSessionDebugProbeAutoShowGate(): SessionDebugProbeAutoShowGate {
        return evaluateSessionDebugProbeAutoShowGate(
            probeArmed = hasArmedSessionDebugProbe(),
            screen = uiState.screen.value,
            ftmsReady = uiState.ftmsReady.value,
        )
    }

    private fun hasArmedSessionDebugProbe(): Boolean {
        return armedSessionDebugProbeMessage != null
    }

    private fun clearArmedSessionDebugProbe() {
        sessionDebugProbeArmRunnable?.let(mainHandler::removeCallbacks)
        sessionDebugProbeArmRunnable = null
        armedSessionDebugProbeTitle = null
        armedSessionDebugProbeMessage = null
    }

    private fun recordSessionDebugProbeEvent(
        event: String,
        title: String? = null,
        message: String? = null,
        signal: String? = null,
        signalLabel: String? = null,
        signalCount: Int? = null,
        reason: String? = null,
        powerW: Int? = null,
        cadenceRpm: Int? = null,
    ) {
        sessionDebugProbeLastEventSequence = sessionDebugProbeEventQueue.append(
            SessionDebugProbeEventQueue.AppendRequest(
                event = event,
                screen = uiState.screen.value.name,
                title = title,
                message = message,
                signal = signal,
                signalLabel = signalLabel,
                signalCount = signalCount,
                reason = reason,
                powerW = powerW,
                cadenceRpm = cadenceRpm,
            ),
        )
    }

    private fun sessionDebugProbeSignalLabel(signal: SessionDebugProbeSignal): String =
        signal.wireName.replaceFirstChar { character ->
            if (character.isLowerCase()) character.titlecase(Locale.US) else character.toString()
        }

    private fun finalizePreparedWorkoutCompletionForContinuation():
        com.example.ergometerapp.session.SessionSummary? {
        sessionManager.armContinuationCheckpoint()
        sessionManager.stopSession()
        sessionManager.promoteStoppedSessionToContinuationCarryover()
        sessionOrchestrator.finishPreparedPostWorkoutExit(
            reason = "post_workout_completion_choice_continue",
        )
        return sessionManager.lastSummary
    }

    private fun finalizePreparedWorkoutCompletionToSummary() {
        hidePostWorkoutContinuationHandoff()
        sessionManager.stopSession()
        sessionOrchestrator.finishPreparedPostWorkoutExit(
            reason = "post_workout_completion_choice_summary",
        )
        uiState.summary.value = sessionManager.lastSummary
        activityCallbackBridge.allowScreenOff()
        uiState.screen.value = AppScreen.SUMMARY
        aiAssistantFacade.refresh(
            forcePhase = AiPhase.SUMMARY,
            force = true,
        )
    }

    /**
     * Drives the proven manual fallback path for post-workout continuation:
     * finish the workout to summary, return to menu, then start a fresh telemetry-only session.
     *
     * This remains debug-only on purpose so we can validate the trainer behavior before
     * committing the same sequence to the user-facing Continue ride product flow.
     */
    private fun armHardCutoverContinueRideProbe(): String {
        cancelPendingHardCutoverContinueRideProbe()
        onEndSessionAndGoToSummary()
        val deadlineElapsedMs = SystemClock.elapsedRealtime() + DEBUG_HARD_CUTOVER_TIMEOUT_MS
        val runnable = object : Runnable {
            override fun run() {
                when (uiState.screen.value) {
                    AppScreen.STOPPING,
                    AppScreen.SESSION -> {
                        if (SystemClock.elapsedRealtime() >= deadlineElapsedMs) {
                            debugHardCutoverProbeRunnable = null
                            return
                        }
                        mainHandler.postDelayed(this, DEBUG_HARD_CUTOVER_POLL_INTERVAL_MS)
                    }

                    AppScreen.SUMMARY -> {
                        returnToMenu(preserveSessionDebugProbe = true)
                        mainHandler.postDelayed(this, DEBUG_HARD_CUTOVER_POLL_INTERVAL_MS)
                    }

                    AppScreen.MENU -> {
                        debugHardCutoverProbeRunnable = null
                        onSelectTelemetryOnlyMode()
                        activeMenuSetupStepState.value = MenuSetupStep.SUMMARY
                        if (canStartSession()) {
                            onStartSession()
                        }
                    }

                    else -> {
                        debugHardCutoverProbeRunnable = null
                    }
                }
            }
        }
        debugHardCutoverProbeRunnable = runnable
        mainHandler.postDelayed(runnable, DEBUG_HARD_CUTOVER_POLL_INTERVAL_MS)
        return "Debug automation armed continue_ride_via_hard_cutover_probe."
    }

    private fun cancelPendingHardCutoverContinueRideProbe() {
        debugHardCutoverProbeRunnable?.let(mainHandler::removeCallbacks)
        debugHardCutoverProbeRunnable = null
    }

    /**
     * Arms a one-shot mock-trainer telemetry scenario for the next debug mock session start.
     */
    internal fun onDebugMockTrainerAutomationCommand(
        command: DebugMockTrainerAutomationCommand,
    ): String {
        if (!BuildConfig.DEBUG) {
            return "Ignored debug mock trainer automation command in non-debug build."
        }
        debugAutomationUiState.armMockTrainerScenario(command.scenario)
        return "Debug mock trainer automation armed for next mock session: ${command.scenario.wireName}"
    }

    private fun tryTakePersistableReadPermission(uri: Uri) {
        runCatching {
            appContext.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }

    /**
     * Restores the last workout selection from persistent storage after process death.
     *
     * File-based selections are re-imported from the stored URI. If the URI is no longer
     * readable (expired SAF permission), the restore is silently skipped.
     * The bundled Free-tier workout is re-created from the packaged provider.
     * Telemetry-only mode is restored directly.
     * Editor mode cannot be restored without serialized workout payload and is skipped.
     */
    private fun restoreWorkoutSelection() {
        val storedMode = WorkoutSelectionSettingsStorage.loadSessionSetupMode(appContext) ?: return
        when (storedMode) {
            SessionSetupMode.FILE -> {
                val uri = WorkoutSelectionSettingsStorage.loadWorkoutUri(appContext)
                val bundledAssetPath = WorkoutSelectionSettingsStorage.loadBundledWorkoutAssetPath(appContext)
                if (uri != null) {
                    sessionOrchestrator.onWorkoutFileSelected(uri)
                } else if (bundledAssetPath != null) {
                    if (bundledAssetPath == StarterWorkoutProvider.assetPath) {
                        sessionOrchestrator.onStarterWorkoutSelected()
                    } else {
                        sessionOrchestrator.onBundledWorkoutSelected(bundledAssetPath)
                    }
                } else {
                    sessionOrchestrator.onStarterWorkoutSelected()
                }
            }
            SessionSetupMode.TELEMETRY_ONLY -> {
                sessionOrchestrator.onTelemetryOnlyModeSelected()
            }
            SessionSetupMode.EDITOR -> {
                // Cannot restore without serialized workout payload; skip silently.
            }
        }
    }

    private fun clearHrPermissionDeniedStatus() {
        val message = appContext.getString(R.string.menu_hr_connect_permission_required)
        if (deviceSelectionUiState.scanStatusState.value == message) {
            deviceSelectionUiState.scanStatusState.value = null
        }
    }

    fun onBackToMenu() {
        returnToMenu()
    }

    private fun returnToMenu(
        preserveSessionDebugProbe: Boolean = false,
    ) {
        val keepSessionDebugProbe =
            shouldPreserveSessionDebugProbeAcrossInternalMenuReturn(
                preserveRequested = preserveSessionDebugProbe,
                postWorkoutContinuationHandoffVisible = uiState.postWorkoutContinuationHandoffVisible.value,
                probeVisible = uiState.sessionDebugProbeVisible.value,
                probeArmed = hasArmedSessionDebugProbe(),
            )
        if (!keepSessionDebugProbe) {
            clearArmedSessionDebugProbe()
            hideSessionDebugProbeOverlay(reason = "back_to_menu")
        }
        summaryExitCoordinator.resetForMenuReturn(summaryExitStatePort)
        uiState.screen.value = AppScreen.MENU
        activeMenuSetupStepState.value = null
        activityCallbackBridge.allowScreenOff()
        probeTrainerAvailabilityNow()
        probeHrAvailabilityNow()
        aiAssistantFacade.refresh(
            forcePhase = AiPhase.MENU,
            force = true,
        )
    }

    private fun ensureMenuAvailableForDebugAutomation(): Boolean {
        val currentScreen = uiState.screen.value
        if (currentScreen == AppScreen.EWO_EDITOR) {
            uiState.screen.value = AppScreen.MENU
            activeMenuSetupStepState.value = null
            aiAssistantFacade.refresh(
                forcePhase = AiPhase.MENU,
                force = true,
            )
            return true
        }
        return when (currentScreen) {
            AppScreen.MENU -> true
            AppScreen.SUMMARY -> {
                onBackToMenu()
                true
            }

            AppScreen.BASELINE_FITNESS_TEST -> {
                uiState.screen.value = AppScreen.MENU
                activeMenuSetupStepState.value = null
                aiAssistantFacade.refresh(
                    forcePhase = AiPhase.MENU,
                    force = true,
                )
                true
            }

            AppScreen.CONNECTING,
            AppScreen.SESSION,
            AppScreen.STOPPING -> false
        }
    }

    private fun resolveDocumentsWorkoutOptionByFileName(requestedFileName: String): DocumentsFolderWorkoutOption? {
        return documentsFolderWorkoutFilesState.firstOrNull { option ->
            option.displayName.equals(requestedFileName, ignoreCase = true)
        }
    }

    private fun refreshBuiltInWorkoutLibrary() {
        builtInWorkoutsState.clear()
        builtInWorkoutsState.addAll(BundledWorkoutAssetCatalog.listBuiltInWorkouts(appContext))
    }

    private fun resolveBuiltInWorkoutOption(assetPath: String): BuiltInWorkoutOption? {
        return builtInWorkoutFilesState.firstOrNull { option -> option.assetPath == assetPath }
    }

    private fun allowTrainerProbeAutomationFromCurrentScreen(): Boolean {
        val currentScreen = uiState.screen.value
        if (currentScreen == AppScreen.EWO_EDITOR) {
            return false
        }
        return when (currentScreen) {
            AppScreen.MENU,
            AppScreen.SESSION -> true

            AppScreen.SUMMARY -> {
                onBackToMenu()
                true
            }

            AppScreen.BASELINE_FITNESS_TEST,
            AppScreen.CONNECTING,
            AppScreen.STOPPING -> false
        }
    }

    private fun debugAutomationWorkoutNotFoundMessage(requestedFileName: String): String {
        val knownFiles = documentsFolderWorkoutFilesState
            .joinToString(separator = ", ") { it.displayName }
            .ifBlank { "none" }
        return "Debug automation workout not found: $requestedFileName. Available: $knownFiles"
    }

    private fun debugAutomationStatusSummary(refreshDocumentsFolder: Boolean): String {
        if (refreshDocumentsFolder && documentsFolderUiState.readyState.value) {
            onRefreshDocumentsFolderWorkoutsRequested()
        }
        val bikeData = uiState.bikeData.value
        val sessionIntentDiagnostics = sessionOrchestrator.sessionIntentDiagnosticsForDebug()
        val trainerReleaseSummary = sessionOrchestrator.trainerReleaseSummaryForDebug()
        val sessionDebugProbeAutoShowGate = currentSessionDebugProbeAutoShowGate()
        val knownDocumentsWorkouts = documentsFolderWorkoutFilesState
            .joinToString(separator = ", ") { it.displayName }
            .ifBlank { "none" }
        val selectedWorkoutLabel = uiState.selectedWorkout.value?.name
            ?: uiState.selectedImportedWorkout.value?.title
            ?: uiState.selectedWorkoutFileName.value
            ?: "none"
        return buildString {
            append("Debug automation status: ")
            append("screen=").append(uiState.screen.value)
            append(", menuStep=").append(activeMenuSetupStepState.value ?: "hub")
            append(", setupMode=").append(uiState.selectedSessionSetupMode.value)
            append(", selectedWorkout=").append(selectedWorkoutLabel)
            append(", selectedWorkoutFile=").append(uiState.selectedWorkoutFileName.value ?: "none")
            append(", startEnabled=").append(canStartSession())
            append(", ftmsReady=").append(uiState.ftmsReady.value)
            append(", ftmsControlGranted=").append(uiState.ftmsControlGranted.value)
            append(", postWorkoutFreeride=").append(uiState.postWorkoutFreerideModeActive)
            append(", runnerRunning=").append(uiState.runner.value.running)
            append(", runnerPaused=").append(uiState.runner.value.paused)
            append(", runnerDone=").append(uiState.runner.value.done)
            append(", workoutElapsedSec=").append(uiState.runner.value.workoutElapsedSec ?: "none")
            append(", ftmsSelected=").append(hasSelectedFtmsDevice())
            append(", ftmsConnected=").append(uiState.ftmsReady.value)
            append(", ftmsDevice=").append(deviceSelectionUiState.ftmsDevice.displayNameState.value)
            append(", trainerPreparationOwner=").append(sessionOrchestrator.trainerPreparationOwnerForDebug())
            append(", trainerPreparationState=").append(
                sessionOrchestrator.externalTrainerPreparationStateForExternalUse(),
            )
            append(", preparedTrainerMac=").append(
                sessionOrchestrator.preparedTrainerDeviceMacForDebug() ?: "none",
            )
            append(", preparedTrainerReusable=").append(
                sessionOrchestrator.preparedTrainerConnectionReusableForDebug(),
            )
            append(", hrSelected=").append(hasSelectedHrDevice())
            append(", hrConnected=").append(deviceSelectionUiState.hrDevice.connectedState.value)
            append(", hrDevice=").append(deviceSelectionUiState.hrDevice.displayNameState.value)
            append(", bikePowerW=").append(bikeData?.instantaneousPowerW ?: "none")
            append(", bikeCadenceRpm=").append(bikeData?.instantaneousCadenceRpm ?: "none")
            append(", bikeSpeedKmh=").append(bikeData?.instantaneousSpeedKmh ?: "none")
            append(", bikeDistanceM=").append(bikeData?.totalDistanceMeters ?: "none")
            append(", bikeEnergyKcal=").append(bikeData?.totalEnergyKcal ?: "none")
            append(", mockTrainer=").append(mockTrainerModeEnabledState.value)
            append(", documentsFolderReady=").append(documentsFolderUiState.readyState.value)
            append(", documentsFolderAccessLost=").append(documentsFolderUiState.accessLostState.value)
            append(", executionMessage=").append(uiState.workoutExecutionModeMessage.value ?: "none")
            append(", executionMessageIsError=").append(uiState.workoutExecutionModeIsError.value)
            append(", sessionIntent=").append('{')
            append(
                sessionIntentDiagnostics.dumpContext().entries.joinToString(separator = " ") { (key, value) ->
                    "$key=$value"
                },
            )
            append('}')
            append(", trainerRelease=").append('{').append(trainerReleaseSummary).append('}')
            append(", sessionDebugProbeVisible=").append(uiState.sessionDebugProbeVisible.value)
            append(", sessionDebugProbeTitle=").append(uiState.sessionDebugProbeTitle.value ?: "none")
            append(", sessionDebugProbeMessage=").append(uiState.sessionDebugProbeMessage.value ?: "none")
            append(", sessionDebugProbeArmed=").append(hasArmedSessionDebugProbe())
            append(", sessionDebugProbeArmedTitle=").append(armedSessionDebugProbeTitle ?: "none")
            append(", sessionDebugProbeArmedMessage=").append(armedSessionDebugProbeMessage ?: "none")
            append(", sessionDebugProbeAutoShowReady=").append(sessionDebugProbeAutoShowGate.ready)
            append(", sessionDebugProbeAutoShowBlocker=").append(
                sessionDebugProbeAutoShowGate.blocker.wireName,
            )
            append(", sessionDebugProbeLastSignal=").append(
                uiState.sessionDebugProbeLastSignalLabel.value ?: "none",
            )
            append(", sessionDebugProbeSignalCount=").append(uiState.sessionDebugProbeLastSignalCount.value)
            append(", sessionDebugProbeLastSignalAtMs=").append(
                uiState.sessionDebugProbeLastSignalAtEpochMs.value ?: "none",
            )
            append(", sessionDebugProbeLastEventSeq=").append(
                sessionDebugProbeLastEventSequence ?: "none",
            )
            append(", sessionDebugProbeEventLogPath=").append(
                sessionDebugProbeEventQueue.absolutePath(),
            )
            append(", documentsWorkouts=[").append(knownDocumentsWorkouts).append(']')
        }
    }

    internal fun sessionDebugProbeDiagnosticsForUi(): String {
        return sessionOrchestrator.sessionIntentOverlaySummaryForDebug()
    }

    /**
     * Handles MENU AI CTA taps with strict screen-phase guardrails.
     */
    fun onAiMenuAssistantAction(actionKey: String): Boolean {
        val resolution = resolveAiMenuAssistantAction(
            actionKey = actionKey,
            currentScreen = uiState.screen.value,
        )
        return resolution.openDevicesSetup
    }

    /**
     * Runs a bounded Compatibility Mode check on a background worker.
     */
    fun onRunCompatibilityCheckRequested() {
        compatibilityFeatureFacade.onRunRequested()
    }

    /**
     * Prepares a summary FIT export by capturing the current summary snapshot.
     *
     * Returns a suggested filename for create-document flows when export can continue.
     */
    fun prepareSessionFitExport(): String? {
        return summaryFitExportCoordinator.prepareExport(
            snapshot = sessionOrchestrator.getSessionExportSnapshot(),
            statePort = summaryFitExportStatePort,
        )
    }

    /**
     * Prepares a share chooser intent by exporting the current summary FIT into app cache.
     */
    fun prepareSessionFitShareIntent(): Intent? {
        return summaryFitShareCoordinator.prepareShareIntent(
            snapshot = sessionOrchestrator.getSessionExportSnapshot(),
            statePort = summaryFitShareStatePort,
        )
    }

    /**
     * Reports chooser launch failure after a prepared summary FIT share intent was returned.
     */
    fun onSessionFitShareLaunchFailed() {
        summaryFitShareCoordinator.onShareLaunchFailed(
            statePort = summaryFitShareStatePort,
        )
    }

    /**
     * Updates persistent summary FIT export preference selected by user.
     */
    fun onSummaryFitExportPreferenceSelected(preference: FitExportPreference) {
        summaryFitExportPreferenceCoordinator.onPreferenceSelected(preference)
    }

    /**
     * Attempts default FIT export into the bound SAF folder.
     *
     * Returns true when request handling is complete and no fallback picker is needed.
     */
    fun tryExportPendingSessionFitToDocumentsFolder(
        suggestedFileName: String,
        allowPickerFallback: Boolean = true,
    ): Boolean {
        return summaryFitExportCoordinator.tryExportPendingToDocumentsFolder(
            suggestedFileName = suggestedFileName,
            allowPickerFallback = allowPickerFallback,
            mimeType = safFitMimeType,
            statePort = summaryFitExportStatePort,
        )
    }

    /**
     * Runs automatic summary FIT export once per summary fingerprint when auto-save is enabled.
     */
    fun tryAutoExportSummaryFitIfNeeded() {
        summaryFitAutoExportCoordinator.tryAutoExportIfNeeded(summaryFitAutoExportStatePort)
    }

    /**
     * Completes FIT export after the user selects the target document URI.
     */
    fun onSessionFitExportTargetSelected(uri: Uri?) {
        summaryFitExportCoordinator.completeDocumentExport(
            targetUri = uri,
            fallbackSnapshot = sessionOrchestrator.getSessionExportSnapshot(),
            statePort = summaryFitExportStatePort,
        )
    }

    // --- Canonical EWO editor actions ---

    internal fun onEwoEditorAction(action: EwoEditorScreenAction) {
        when (action) {
            is EwoEditorScreenAction.Back -> uiState.screen.value = AppScreen.MENU
            is EwoEditorScreenAction.NewDocument -> ewoEditorCoordinator.newDocument()
            is EwoEditorScreenAction.Undo -> ewoEditorCoordinator.undo()
            is EwoEditorScreenAction.Redo -> ewoEditorCoordinator.redo()
            is EwoEditorScreenAction.Dispatch -> ewoEditorCoordinator.dispatch(action.command)
            is EwoEditorScreenAction.DispatchView -> ewoEditorCoordinator.dispatchView(action.action)
            is EwoEditorScreenAction.SetFtp -> ewoEditorCoordinator.setFtpWatts(action.value)
            is EwoEditorScreenAction.SetHrMax -> ewoEditorCoordinator.setHrMaxBpm(action.value)
            is EwoEditorScreenAction.SetRestingHr -> ewoEditorCoordinator.setRestingHrBpm(action.value)
            is EwoEditorScreenAction.SetLthr -> ewoEditorCoordinator.setLthrBpm(action.value)
            is EwoEditorScreenAction.CopySegment -> ewoEditorCoordinator.copySegment(action.nodeId)
            is EwoEditorScreenAction.PasteSegment -> ewoEditorCoordinator.pasteSegment()
            is EwoEditorScreenAction.MoveSegment -> {
                val cmd = com.ewo.editor.commands.computeMoveTarget(
                    ewoEditorCoordinator.document.segments,
                    action.nodeId,
                    action.direction,
                )
                if (cmd != null) ewoEditorCoordinator.dispatch(cmd)
            }
            is EwoEditorScreenAction.OpenFile -> { /* handled by MainActivity file picker */ }
            is EwoEditorScreenAction.SaveFile -> { /* handled by MainActivity file picker */ }
        }
        ewoEditorSnapshotState.value = ewoEditorCoordinator.snapshot()
    }

    internal fun onEwoEditorOpenFileResult(json: String, fileName: String) {
        syncEwoEditorProfileContext()
        ewoEditorOpenCoordinator.openDocument(
            content = json,
            fileName = fileName,
            ftpWatts = profileSettingsUiState.ftpWattsState.intValue,
            statePort = ewoEditorOpenStatePort,
        )
        ewoEditorSnapshotState.value = ewoEditorCoordinator.snapshot()
    }

    internal fun onEwoEditorOpenError(message: String) {
        ewoEditorCoordinator.reportOpenError(message)
        ewoEditorSnapshotState.value = ewoEditorCoordinator.snapshot()
    }

    internal fun onOpenEwoEditor() {
        syncEwoEditorProfileContext()
        ewoEditorSnapshotState.value = ewoEditorCoordinator.snapshot()
        uiState.screen.value = AppScreen.EWO_EDITOR
    }

    internal fun getEwoEditorExportJson(): String? {
        return ewoEditorCoordinator.exportCanonicalJson()
    }

    private fun currentRiderProfileCompileContext() = riderProfileCompileContext(
        ftpWatts = profileSettingsUiState.ftpWattsState.intValue,
        hrProfileAge = profileSettingsUiState.hrProfileAgeState.value,
        hrProfileSex = profileSettingsUiState.hrProfileSexState.value,
    )

    private fun syncEwoEditorProfileContext() {
        val context = currentRiderProfileCompileContext()
        ewoEditorCoordinator.setFtpWatts(context.ftpWatts)
        ewoEditorCoordinator.setHrMaxBpm(context.hrMaxBpm)
    }

    private val ewoEditorOpenStatePort = object : EwoEditorOpenStatePort {
        override fun openCanonicalJson(json: String, fileName: String): Boolean {
            return ewoEditorCoordinator.openJson(json, fileName)
        }

        override fun openLegacyWorkout(
            workoutFile: com.example.ergometerapp.workout.WorkoutFile,
            fileName: String,
        ) {
            ewoEditorCoordinator.openZwo(workoutFile, fileName)
        }

        override fun reportOpenError(message: String) {
            ewoEditorCoordinator.reportOpenError(message)
        }
    }
    /**
     * Starts FTMS device discovery flow for trainer selection.
     */
    fun onSearchFtmsDevicesRequested() {
        deviceSelectionFacade.requestFtmsScan()
    }

    /**
     * Starts heart-rate device discovery flow for optional strap selection.
     */
    fun onSearchHrDevicesRequested() {
        deviceSelectionFacade.requestHrScan()
    }

    /**
     * Arms a one-shot debug override that forces the next tree-folder write to use fallback flow.
     */
    fun armDebugDocumentsFolderWriteFailureOnce() {
        if (!BuildConfig.DEBUG) return
        debugAutomationUiState.armDocumentsFolderWriteFailure()
        setDocumentsFolderStatus(
            message = "Debug override armed. The next folder write will use system picker fallback.",
            isError = false,
        )
    }

    /**
     * Exports a debug-only snapshot of recent session diagnostics.
     */
    /**
     * Closes device picker and stops active scan.
     */
    fun onDismissDeviceSelection() {
        deviceSelectionFacade.dismissSelection()
    }

    /**
     * Persists selected scanned device to FTMS/HR settings based on active picker.
     */
    fun onScannedDeviceSelected(device: ScannedBleDevice) {
        deviceSelectionFacade.onScannedDeviceSelected(device)
    }

    /**
     * Dismisses connection-failure prompt shown after failed trainer auto-connect.
     */
    fun clearConnectionIssuePrompt() {
        connectionIssuePromptCoordinator.clearPrompt()
    }

    /**
     * Dismisses failure prompt and opens trainer device discovery immediately.
     */
    fun onSearchFtmsDevicesFromConnectionIssue() {
        connectionIssuePromptCoordinator.onSearchFtmsDevicesFromPrompt()
    }

    /**
     * Accepts only positive numeric FTP input and persists the latest valid value.
     */
    fun onFtpInputChanged(rawInput: String) {
        profileSettingsCoordinator.onFtpInputChanged(rawInput)
    }

    internal fun onBaselineFitnessTestResultRecorded(result: BaselineFitnessTestResult) {
        baselineFitnessTestResultPromotionCoordinator.recordResult(result)
        baselineLatestResultState.value = result
    }

    fun onOpenBaselineFitnessTestRequested() {
        uiState.screen.value = AppScreen.BASELINE_FITNESS_TEST
    }

    fun onBackFromBaselineFitnessTest() {
        baselineFitnessTestCoordinator.cancelAttempt(Instant.now())
        sessionOrchestrator.releaseTrainerForExternalUse()
        baselineFitnessTestCoordinator.resetToIdle()
        syncBaselineFitnessTestTicking()
        uiState.screen.value = AppScreen.MENU
    }

    fun onStartBaselineFitnessTestRequested() {
        baselineFitnessTestCoordinator.start(
            priorFtpWatts = profileSettingsUiState.ftpWattsState.intValue,
            now = Instant.now(),
            liveMetrics = currentBaselineFitnessTestLiveMetrics(),
        )
        syncBaselineFitnessTestTicking()
    }

    fun onAcceptBaselineFitnessTestAdvisoryFallbackRequested() {
        baselineFitnessTestCoordinator.acceptAdvisoryFallback(Instant.now())
        syncBaselineFitnessTestTicking()
    }

    fun onDeclineBaselineFitnessTestAdvisoryFallbackRequested() {
        baselineFitnessTestCoordinator.declineAdvisoryFallback(Instant.now())
        syncBaselineFitnessTestTicking()
    }

    fun onStopBaselineFitnessTestRequested() {
        baselineFitnessTestCoordinator.stopRamp(Instant.now())
        syncBaselineFitnessTestTicking()
    }

    fun onCancelBaselineFitnessTestRequested() {
        baselineFitnessTestCoordinator.cancelAttempt(Instant.now())
        syncBaselineFitnessTestTicking()
    }

    fun onSkipBaselineFitnessTestCooldownRequested() {
        baselineFitnessTestCoordinator.skipCooldown(Instant.now())
        syncBaselineFitnessTestTicking()
    }

    /**
     * Accepts profile age in years for HR-zone estimation.
     */
    fun onHrProfileAgeInputChanged(rawInput: String) {
        profileSettingsCoordinator.onHrProfileAgeInputChanged(rawInput)
    }

    /**
     * Persists selected biological sex for HR-zone estimation formula.
     */
    fun onHrProfileSexSelected(sex: HrProfileSex) {
        profileSettingsCoordinator.onHrProfileSexSelected(sex)
    }

    /**
     * Toggles debug-only mock trainer mode used for trainerless development sessions.
     */
    fun onMockTrainerModeChanged(enabled: Boolean) {
        sessionStartEligibilityCoordinator.onMockTrainerModeChanged(enabled)
    }

    /**
     * Start is allowed when workout import is valid and either:
     * - a trainer MAC is selected, or
     * - debug mock trainer mode is enabled.
     */
    fun canStartSession(): Boolean {
        return sessionStartEligibilityCoordinator.canStartSession()
    }

    /**
     * Returns whether trainer selection contains a valid persisted FTMS MAC.
     */
    fun hasSelectedFtmsDevice(): Boolean = sessionStartEligibilityCoordinator.hasSelectedFtmsDevice()

    /**
     * Returns whether optional HR selection contains a valid persisted MAC.
     */
    fun hasSelectedHrDevice(): Boolean = sessionStartEligibilityCoordinator.hasSelectedHrDevice()

    private fun currentBaselineFitnessTestLiveMetrics(): BaselineFitnessTestLiveMetricsSnapshot {
        val bikeData = uiState.bikeData.value
        val bikeDataRecordedAt = uiState.bikeDataLastUpdatedAtEpochMs?.let(Instant::ofEpochMilli)
        val externalHeartRate = uiState.heartRate.value
        val externalHeartRateRecordedAt =
            uiState.heartRateLastUpdatedAtEpochMs?.let(Instant::ofEpochMilli)
        val effectiveHeartRate = externalHeartRate ?: bikeData?.heartRateBpm
        val effectiveHeartRateRecordedAt = when {
            externalHeartRate != null -> externalHeartRateRecordedAt
            bikeData?.heartRateBpm != null -> bikeDataRecordedAt
            else -> null
        }

        return BaselineFitnessTestLiveMetricsSnapshot(
            powerWatts = bikeData?.instantaneousPowerW,
            powerRecordedAt = bikeData?.instantaneousPowerW?.let { bikeDataRecordedAt },
            cadenceRpm = bikeData?.instantaneousCadenceRpm,
            heartRateBpm = effectiveHeartRate,
            heartRateRecordedAt = effectiveHeartRate?.let { effectiveHeartRateRecordedAt },
            trainerReady = uiState.ftmsReady.value,
            controlGranted = uiState.ftmsControlGranted.value,
        )
    }

    private fun syncBaselineFitnessTestTicking() {
        val requiresTicking = when (baselineFitnessTestRuntimeSnapshotState.value.phase) {
            com.example.ergometerapp.baseline.BaselineFitnessTestUiPhase.PRECHECK,
            com.example.ergometerapp.baseline.BaselineFitnessTestUiPhase.REQUESTING_CONTROL,
            com.example.ergometerapp.baseline.BaselineFitnessTestUiPhase.WARMUP,
            com.example.ergometerapp.baseline.BaselineFitnessTestUiPhase.RAMP_ACTIVE,
            com.example.ergometerapp.baseline.BaselineFitnessTestUiPhase.COOLDOWN -> true

            else -> false
        }
        if (!requiresTicking) {
            baselineFitnessTestTickRunnable?.let(mainHandler::removeCallbacks)
            baselineFitnessTestTickRunnable = null
            return
        }
        if (baselineFitnessTestTickRunnable != null) return
        baselineFitnessTestTickRunnable = object : Runnable {
            override fun run() {
                baselineFitnessTestCoordinator.tick(
                    now = Instant.now(),
                    liveMetrics = currentBaselineFitnessTestLiveMetrics(),
                )
                if (baselineFitnessTestTickRunnable !== this) {
                    return
                }
                if (when (baselineFitnessTestRuntimeSnapshotState.value.phase) {
                    com.example.ergometerapp.baseline.BaselineFitnessTestUiPhase.PRECHECK,
                    com.example.ergometerapp.baseline.BaselineFitnessTestUiPhase.REQUESTING_CONTROL,
                    com.example.ergometerapp.baseline.BaselineFitnessTestUiPhase.WARMUP,
                    com.example.ergometerapp.baseline.BaselineFitnessTestUiPhase.RAMP_ACTIVE,
                    com.example.ergometerapp.baseline.BaselineFitnessTestUiPhase.COOLDOWN -> true

                    else -> false
                }) {
                    mainHandler.postDelayed(this, BASELINE_FITNESS_TEST_TICK_INTERVAL_MS)
                } else {
                    baselineFitnessTestTickRunnable = null
                }
            }
        }
        mainHandler.postDelayed(
            baselineFitnessTestTickRunnable!!,
            BASELINE_FITNESS_TEST_TICK_INTERVAL_MS,
        )
    }

    /**
     * Stable fingerprint used to prevent duplicate auto-export attempts for one summary.
     */
    private fun autoExportFingerprint(summary: com.example.ergometerapp.session.SessionSummary): String {
        return listOf(
            summary.startTimestampMillis.toString(),
            summary.stopTimestampMillis.toString(),
            summary.durationSeconds.toString(),
            summary.actualTss?.toString().orEmpty(),
            summary.distanceMeters?.toString().orEmpty(),
            summary.totalEnergyKcal?.toString().orEmpty(),
            summary.avgPower?.toString().orEmpty(),
            summary.maxPower?.toString().orEmpty(),
            summary.avgCadence?.toString().orEmpty(),
            summary.maxCadence?.toString().orEmpty(),
            summary.avgHeartRate?.toString().orEmpty(),
            summary.maxHeartRate?.toString().orEmpty(),
        ).joinToString(separator = "|")
    }

    private fun setSessionFitExportStatus(message: String?, isError: Boolean) {
        documentsFolderUiState.fitExportStatusMessageState.value = message
        documentsFolderUiState.fitExportStatusIsErrorState.value = isError
    }

    private fun clearSessionFitExportStatus() {
        documentsFolderUiState.clearFitExportStatus()
    }

    /**
     * Verifies bound folder readiness and updates state when permission is lost.
     */
    private fun ensureDocumentsFolderReadyForFileOperations(): Uri? {
        return documentsFolderCoordinator.ensureReadyForFileOperations()
    }

    /**
     * Recomputes persisted SAF folder health from URI permissions and tree access.
     */
    private fun refreshDocumentsFolderState(clearStatusMessage: Boolean) {
        documentsFolderCoordinator.refreshState(clearStatusMessage = clearStatusMessage)
    }

    private fun setDocumentsFolderStatus(message: String?, isError: Boolean) {
        documentsFolderCoordinator.setStatus(
            message = message,
            isError = isError,
        )
    }

    private fun clearDocumentsFolderStatus() {
        documentsFolderCoordinator.clearStatus()
    }

    private fun consumeDebugDocumentsFolderWriteFailureOnce(): Boolean {
        if (!BuildConfig.DEBUG) {
            return false
        }
        return debugAutomationUiState.consumeDocumentsFolderWriteFailure()
    }

    /**
     * Avoids immediate passive probe scans right after closing picker, so rapid
     * picker reopen does not hit platform scan-frequency throttling.
     */
    private fun suppressStatusProbesTemporarily() {
        menuStatusProbeFacade.suppressStatusProbesTemporarily()
    }

    private fun cancelPendingStatusProbeResume() {
        menuStatusProbeFacade.cancelPendingStatusProbeResume()
    }

    /**
     * Closes active HR GATT before opening HR picker because many straps stop
     * advertising while connected, which would hide them from the scan list.
     */
    private fun closeHrClientForPickerScan() {
        hrClient.close()
    }

    private fun isMockTrainerModeActive(): Boolean {
        return sessionStartEligibilityCoordinator.isMockTrainerModeActive()
    }

    private fun currentFtmsDeviceMac(): String? {
        return sessionStartEligibilityCoordinator.currentFtmsDeviceMac()
    }

    private fun currentHrDeviceMac(): String? {
        return sessionStartEligibilityCoordinator.currentHrDeviceMac()
    }

    /**
     * Starts periodic trainer availability probing while this ViewModel is bound to UI.
     */
    private fun startTrainerStatusPolling() {
        menuStatusProbeFacade.startTrainerStatusPolling()
    }

    /**
     * Starts periodic HR availability probing while this ViewModel is bound to UI.
     */
    private fun startHrStatusPolling() {
        menuStatusProbeFacade.startHrStatusPolling()
    }

    /**
     * Stops periodic trainer availability probing.
     */
    private fun stopTrainerStatusPolling() {
        menuStatusProbeFacade.stopTrainerStatusPolling()
    }

    /**
     * Stops periodic HR availability probing.
     */
    private fun stopHrStatusPolling() {
        menuStatusProbeFacade.stopHrStatusPolling()
    }

    /**
     * Stops any in-flight trainer status probe scan.
     */
    private fun cancelTrainerStatusProbeScan() {
        menuStatusProbeFacade.cancelTrainerStatusProbeScan()
    }

    /**
     * Stops any in-flight HR status probe scan.
     */
    private fun cancelHrStatusProbeScan() {
        menuStatusProbeFacade.cancelHrStatusProbeScan()
    }

    /**
     * Probes whether the selected trainer is currently discoverable via FTMS advertisements.
     *
     * Probe is intentionally passive and does not open a GATT session, so it
     * cannot interfere with control-point ownership or active workouts.
     */
    private fun probeTrainerAvailabilityNow() {
        menuStatusProbeFacade.probeTrainerAvailabilityNow()
    }

    /**
     * Probes whether the selected HR sensor is currently discoverable.
     *
     * Probe remains passive (scan-only) so HR status can update in MENU without
     * creating/holding an actual GATT connection between sessions.
     */
    private fun probeHrAvailabilityNow() {
        menuStatusProbeFacade.probeHrAvailabilityNow()
    }

    private fun refreshAiAssistantRecommendations(
        forcePhase: AiPhase? = null,
        force: Boolean = false,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        aiAssistantFacade.refresh(
            forcePhase = forcePhase,
            force = force,
            nowMillis = nowMillis,
        )
    }

    private fun hasBluetoothScanPermission(): Boolean {
        return appContext.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) ==
            PackageManager.PERMISSION_GRANTED
    }

    override fun onCleared() {
        stopAndClose()
        super.onCleared()
    }

    private fun playExecutionFailureTone() {
        try {
            val generator = errorToneGenerator ?: ToneGenerator(
                AudioManager.STREAM_NOTIFICATION,
                80
            ).also { created ->
                errorToneGenerator = created
            }
            generator.startTone(ToneGenerator.TONE_PROP_BEEP, errorToneDurationMs)
        } catch (_: Throwable) {
            // Tone playback is non-critical; failures should not affect session flow.
        }
    }

    private fun releaseErrorTone() {
        try {
            errorToneGenerator?.release()
        } catch (_: Throwable) {
            // Best-effort release only.
        } finally {
            errorToneGenerator = null
        }
    }

}

internal enum class SessionCadenceRetentionDecision {
    KEEP,
    REFRESH,
    CLEAR,
}

internal fun resolveSessionCadenceRetention(
    targetRpm: Int,
    recoveryToleranceRpm: Int,
    liveCadenceRpm: Int,
    displayedCadenceRpm: Int?,
    displayedTargetRpm: Int?,
): SessionCadenceRetentionDecision {
    if (recoveryToleranceRpm >= 0 &&
        kotlin.math.abs(liveCadenceRpm - targetRpm) <= recoveryToleranceRpm
    ) {
        return SessionCadenceRetentionDecision.CLEAR
    }
    if (displayedCadenceRpm == liveCadenceRpm && displayedTargetRpm == targetRpm) {
        return SessionCadenceRetentionDecision.KEEP
    }
    return SessionCadenceRetentionDecision.REFRESH
}

internal data class SessionSuppressionHistory(
    val recentEmissions: List<AiRecentEmission>,
    val recentPresented: List<AiPresentedMessageRecord>,
)

internal fun filterSessionSuppressionHistoryByType(
    recentEmissions: List<AiRecentEmission>,
    recentPresented: List<AiPresentedMessageRecord>,
    type: AiRecommendationType,
): SessionSuppressionHistory {
    return SessionSuppressionHistory(
        recentEmissions = recentEmissions.filter { emission ->
            emission.type != type
        },
        recentPresented = recentPresented.filter { presented ->
            !(presented.phase == AiPhase.SESSION && presented.type == type)
        },
    )
}

internal enum class AiMenuAssistantActionResult {
    SUCCESS,
    IGNORED_WRONG_PHASE,
    IGNORED_UNSUPPORTED_ACTION,
}

internal data class AiMenuAssistantActionResolution(
    val openDevicesSetup: Boolean,
    val result: AiMenuAssistantActionResult,
)

internal enum class DocumentsFolderAccessState {
    NOT_CONFIGURED,
    READY,
    ACCESS_LOST,
}

internal fun resolveDocumentsFolderAccessState(
    hasSelectedTreeUri: Boolean,
    hasReadWriteAccess: Boolean,
): DocumentsFolderAccessState {
    if (!hasSelectedTreeUri) {
        return DocumentsFolderAccessState.NOT_CONFIGURED
    }
    return if (hasReadWriteAccess) {
        DocumentsFolderAccessState.READY
    } else {
        DocumentsFolderAccessState.ACCESS_LOST
    }
}

/**
 * Resolves when the post-workout continuation overlay can safely disappear.
 *
 * Telemetry-only continuation keeps the old runner snapshot marked `done`, so
 * `runnerDone == false` is not a reliable readiness signal after Continue
 * ride. The handoff is ready once the fresh telemetry-only session is back on
 * `SESSION` with a live FTMS link and control restored; structured-session
 * paths still use the runner state as before.
 */
internal fun isPostWorkoutContinuationHandoffReady(
    screen: AppScreen,
    selectedSessionSetupMode: SessionSetupMode,
    ftmsReady: Boolean,
    ftmsControlGranted: Boolean,
    runnerDone: Boolean,
): Boolean {
    if (screen != AppScreen.SESSION) {
        return false
    }
    if (selectedSessionSetupMode == SessionSetupMode.TELEMETRY_ONLY) {
        return ftmsReady && ftmsControlGranted
    }
    return !runnerDone
}

internal fun shouldUseCreateDocumentFallbackForWorkoutTreeWrite(
    targetUriCreated: Boolean,
    writeSucceeded: Boolean?,
): Boolean {
    if (!targetUriCreated) {
        return true
    }
    return writeSucceeded != true
}

internal fun resolveDocumentsFolderBindPermissionGranted(
    readWritePersisted: Boolean,
    readOnlyPersisted: Boolean,
): Boolean {
    return readWritePersisted || readOnlyPersisted
}

internal fun shouldUseCreateDocumentFallbackForFitTreeExport(
    targetUriCreated: Boolean,
    exportResult: FitExportResult?,
): Boolean {
    if (!targetUriCreated) {
        return true
    }
    return when (exportResult) {
        FitExportResult.Success -> false
        is FitExportResult.Failure -> when (exportResult.reason) {
            FitExportFailureReason.OUTPUT_STREAM_UNAVAILABLE,
            FitExportFailureReason.WRITE_FAILED,
            -> true

            FitExportFailureReason.NO_SUMMARY,
            FitExportFailureReason.INVALID_TIMESTAMPS,
            -> false
        }
        null -> true
    }
}

internal fun resolveCompatibilityFailureReasonLookupKey(
    failureReasonKey: String?,
    failureCode: CompatibilityFailureCode?,
): String {
    val normalizedReasonKey = failureReasonKey
        ?.trim()
        ?.lowercase()
        ?.takeIf { it.isNotEmpty() }
    if (normalizedReasonKey != null) {
        return normalizedReasonKey
    }
    return failureCode?.name?.lowercase() ?: "unknown_failure"
}

internal fun resolveCompatibilityFailureReasonMessageResId(
    failureReasonKey: String?,
    failureCode: CompatibilityFailureCode?,
): Int {
    return when (resolveCompatibilityFailureReasonLookupKey(failureReasonKey, failureCode)) {
        "connect_timeout" -> R.string.compatibility_failure_reason_connect_timeout
        "connect_disconnected" -> R.string.compatibility_failure_reason_connect_disconnected
        "connect_failed" -> R.string.compatibility_failure_reason_connect_failed
        "request_control_timeout" -> R.string.compatibility_failure_reason_request_control_timeout
        "request_control_write_not_started" -> {
            R.string.compatibility_failure_reason_request_control_write_not_started
        }

        "request_control_rejected" -> R.string.compatibility_failure_reason_request_control_rejected
        "request_control_failed" -> R.string.compatibility_failure_reason_request_control_failed
        "power_step_timeout" -> R.string.compatibility_failure_reason_power_step_timeout
        "power_step_write_not_started" -> {
            R.string.compatibility_failure_reason_power_step_write_not_started
        }

        "power_step_rejected" -> R.string.compatibility_failure_reason_power_step_rejected
        "power_step_failed" -> R.string.compatibility_failure_reason_power_step_failed
        "stop_timeout" -> R.string.compatibility_failure_reason_stop_timeout
        "stop_write_not_started" -> R.string.compatibility_failure_reason_stop_write_not_started
        "stop_rejected" -> R.string.compatibility_failure_reason_stop_rejected
        "stop_failed" -> R.string.compatibility_failure_reason_stop_failed
        "cleanup_fallback_timeout" -> R.string.compatibility_failure_reason_cleanup_fallback_timeout
        "cleanup_fallback_write_not_started" -> {
            R.string.compatibility_failure_reason_cleanup_fallback_write_not_started
        }

        "cleanup_fallback_failed" -> R.string.compatibility_failure_reason_cleanup_fallback_failed
        "cleanup_disconnect_failed" -> R.string.compatibility_failure_reason_cleanup_disconnect_failed
        "global_deadline_exceeded" -> R.string.compatibility_failure_reason_global_deadline_exceeded
        "unknown_failure" -> R.string.compatibility_failure_reason_unknown_failure
        else -> R.string.compatibility_failure_reason_unknown_failure
    }
}

internal fun resolveCompatibilityFailureReasonMessage(
    failureReasonKey: String?,
    failureCode: CompatibilityFailureCode?,
    resolveString: (Int) -> String,
): String {
    return resolveString(
        resolveCompatibilityFailureReasonMessageResId(failureReasonKey, failureCode),
    )
}

internal fun resolveAiMenuAssistantAction(
    actionKey: String,
    currentScreen: AppScreen,
): AiMenuAssistantActionResolution {
    if (currentScreen != AppScreen.MENU) {
        return AiMenuAssistantActionResolution(
            openDevicesSetup = false,
            result = AiMenuAssistantActionResult.IGNORED_WRONG_PHASE,
        )
    }
    return when (actionKey) {
        AI_MENU_CONNECTIVITY_CHECK_TRAINER_ACTION -> {
            AiMenuAssistantActionResolution(
                openDevicesSetup = true,
                result = AiMenuAssistantActionResult.SUCCESS,
            )
        }
        else -> {
            AiMenuAssistantActionResolution(
                openDevicesSetup = false,
                result = AiMenuAssistantActionResult.IGNORED_UNSUPPORTED_ACTION,
            )
        }
    }
}

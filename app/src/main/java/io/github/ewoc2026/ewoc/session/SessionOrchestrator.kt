package io.github.ewoc2026.ewoc.session

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.OpenableColumns
import android.util.Log
import com.ewo.core.EwoCompileContext
import io.github.ewoc2026.ewoc.AppFailure
import io.github.ewoc2026.ewoc.AppFailureFactory
import io.github.ewoc2026.ewoc.AppFailureStringResolver
import io.github.ewoc2026.ewoc.AppFailureUserMessageMapper
import io.github.ewoc2026.ewoc.AppScreen
import io.github.ewoc2026.ewoc.AppUiState
import io.github.ewoc2026.ewoc.BuildConfig
import io.github.ewoc2026.ewoc.R
import io.github.ewoc2026.ewoc.SessionSetupMode
import io.github.ewoc2026.ewoc.SessionStartStopPort
import io.github.ewoc2026.ewoc.ble.FtmsBleClient
import io.github.ewoc2026.ewoc.ble.FtmsCommandLifecycleEvent
import io.github.ewoc2026.ewoc.ble.FtmsCommandLifecycleStage
import io.github.ewoc2026.ewoc.ble.FtmsController
import io.github.ewoc2026.ewoc.ble.FtmsUnexpectedControlPointResponseReason
import io.github.ewoc2026.ewoc.ftms.IndoorBikeParseFailure
import io.github.ewoc2026.ewoc.logging.AppLog
import io.github.ewoc2026.ewoc.session.diagnostics.SessionDiagnosticsBuffer
import io.github.ewoc2026.ewoc.session.diagnostics.SessionDiagnosticsEvent
import io.github.ewoc2026.ewoc.session.release.ReleaseContext
import io.github.ewoc2026.ewoc.session.release.ReleaseIntent
import io.github.ewoc2026.ewoc.session.release.ReleaseRampDecider
import io.github.ewoc2026.ewoc.session.release.ReleaseRampDecision
import io.github.ewoc2026.ewoc.session.release.ReleaseRampPlan
import io.github.ewoc2026.ewoc.session.release.ReleaseRampTraceEmitter
import io.github.ewoc2026.ewoc.session.release.ReleaseRampTraceEvent
import io.github.ewoc2026.ewoc.session.release.TrainerReleasePolicy
import io.github.ewoc2026.ewoc.session.release.TrainerControlAuthority
import io.github.ewoc2026.ewoc.session.release.TrainerReleaseProfiles
import io.github.ewoc2026.ewoc.workout.ExecutionWorkoutMapper
import io.github.ewoc2026.ewoc.workout.ImportedErgoWorkout
import io.github.ewoc2026.ewoc.workout.ImportedErgoWorkoutExecutionMapper
import io.github.ewoc2026.ewoc.workout.ImportedErgoWorkoutStep
import io.github.ewoc2026.ewoc.workout.ImportedHrExecutionCapabilitySnapshot
import io.github.ewoc2026.ewoc.workout.MappingResult
import io.github.ewoc2026.ewoc.workout.WorkoutFile
import io.github.ewoc2026.ewoc.workout.WorkoutImportResult
import io.github.ewoc2026.ewoc.workout.WorkoutImportService
import io.github.ewoc2026.ewoc.workout.WorkoutExecutionStepCounter
import io.github.ewoc2026.ewoc.workout.WorkoutPlannedTssCalculator
import io.github.ewoc2026.ewoc.workout.WorkoutTotalDurationCalculator
import io.github.ewoc2026.ewoc.workout.resolvedSignalLossPowerWatts
import io.github.ewoc2026.ewoc.workout.runner.ImportedHrRuntimeCommand
import io.github.ewoc2026.ewoc.workout.runner.ImportedHrRuntimeControllerV1
import io.github.ewoc2026.ewoc.workout.runner.ImportedHrRuntimeEvent
import io.github.ewoc2026.ewoc.workout.runner.ImportedHrRuntimeState
import io.github.ewoc2026.ewoc.workout.runner.ImportedHrRuntimeUnreachableTargetStatus
import io.github.ewoc2026.ewoc.workout.runner.ImportedHrRuntimeTransition
import java.io.File
import java.io.FileInputStream
import io.github.ewoc2026.ewoc.workout.runner.RunnerSegmentType
import io.github.ewoc2026.ewoc.workout.runner.WorkoutRunner
import io.github.ewoc2026.ewoc.workout.runner.WorkoutStepper
import io.github.ewoc2026.ewoc.session.export.SessionExportSnapshot
import java.util.UUID
import kotlin.math.roundToInt

/**
 * Owns FTMS/session/workout orchestration while Activity keeps lifecycle and permissions.
 *
 * The key invariant is that stale BLE callbacks from an old GATT client instance are ignored
 * by generation checks, so reconnect races cannot mutate current UI/session state.
 */
class SessionOrchestrator internal constructor(
    private val context: Context,
    private val uiState: AppUiState,
    private val sessionManager: SessionManager,
    private val ensureBluetoothPermission: () -> Boolean,
    private val connectHeartRate: () -> Unit,
    private val closeHeartRate: () -> Unit,
    private val connectFtmsTransport: (FtmsBleClient, String) -> Unit = { client, deviceMac ->
        client.connect(deviceMac)
    },
    private val keepScreenOn: () -> Unit,
    private val allowScreenOff: () -> Unit,
    private val onExecutionMappingFailure: () -> Unit = {},
    private val isMockTrainerModeEnabled: () -> Boolean = { false },
    private val currentFtmsDeviceMac: () -> String?,
    private val currentFtpWatts: () -> Int,
    private val currentEwoCompileContext: () -> EwoCompileContext = {
        EwoCompileContext(ftpWatts = currentFtpWatts())
    },
    private val allowLegacyWorkoutFallback: Boolean = BuildConfig.ALLOW_LEGACY_WORKOUT_FALLBACK,
    private val workoutImportService: WorkoutImportService = WorkoutImportService(),
    private val mainThreadHandler: Handler = Handler(Looper.getMainLooper()),
    private val indoorBikeParsingDispatcher: SessionIndoorBikeParsingDispatcher = DefaultSessionIndoorBikeParsingDispatcher(),
    private val mockTrainerEngineFactory: (Handler) -> MockTrainerEngine = { handler ->
        MockTrainerEngine(handler = handler)
    },
    private val consumeMockTrainerDebugScenario: () -> MockTrainerDebugScenario? = { null },
    private val elapsedRealtimeMsProvider: () -> Long = { SystemClock.elapsedRealtime() },
    private val diagnosticsNowMillis: () -> Long = { System.currentTimeMillis() },
    private val sessionIdFactory: () -> String = { UUID.randomUUID().toString() },
    private val recordDiagnosticsEvent: (SessionDiagnosticsEvent) -> Unit = { event ->
        SessionDiagnosticsBuffer.record(event)
    },
) : SessionStartStopPort {
    private enum class TrainerPreparationOwner {
        NONE,
        MENU_WARM,
        EXTERNAL_USE,
    }

    private enum class PostWorkoutReleaseOwner(val wireName: String) {
        TELEMETRY_TRANSITION("telemetry_transition"),
        COMPLETION_EXIT_PREP("completion_exit_prep"),
    }

    private enum class StopFlowTransportClosePolicy(val wireName: String) {
        CLOSE_AFTER_COMPLETION("close_after_completion"),
        KEEP_CONNECTED("keep_connected"),
    }

    private val requestControlOpcode = 0x00
    private val resetOpcode = 0x01
    private val setTargetPowerOpcode = 0x05
    private val stopOpcode = 0x08
    private val controlResponseSuccessCode = 0x01
    private var bleClient: FtmsBleClient? = null
    private lateinit var ftmsController: FtmsController
    private var workoutRunner: WorkoutRunner? = null
    private var importedHrRuntimeController: ImportedHrRuntimeControllerV1? = null
    private var importedHrRuntimeTargetOverrideWatts: Int? = null
    private var importedHrRuntimeActiveSourceStepIndex: Int? = null
    private var importedHrStartupGraceActive = false
    private var importedHrStartupGraceExpiryRunnable: Runnable? = null
    private var ftmsClientGeneration: Int = 0
    private var activeFtmsClientGeneration: Int = 0
    private var activeFtmsControlLinkGeneration: Int = 0
    private val connectFlowTimeoutMs = 15000L
    private val stopFlowTimeoutMs = 4000L
    private val explicitTrainerReconnectSettleDelayMs = 1_500L
    private val importedHrStartupGraceMs = 5_000L
    private val indoorBikeParseLock = Any()
    private var latestIndoorBikeParseRequestId = 0L
    private var mockTrainerEngine: MockTrainerEngine? = null
    private var activeSessionId: String? = null
    private var nextPermissionRequestId = 0L
    private var externalTrainerControlRequestPending = false
    private var externalTrainerControlRequestOutcome: ExternalTrainerControlRequestOutcome? = null
    private var externalTrainerPreparationState = ExternalTrainerPreparationState.IDLE
    private var trainerPreparationOwner = TrainerPreparationOwner.NONE
    private var preparedTrainerDeviceMac: String? = null
    private var explicitTrainerCloseInProgress = false
    private var explicitTrainerReconnectAllowedAtElapsedMs = 0L
    private var pendingTrainerPreparationOwnerAfterClose: TrainerPreparationOwner? = null
    private var pendingTrainerPreparationRunnable: Runnable? = null
    private var postWorkoutFreerideReconnectInProgress = false
    private var postWorkoutFreerideDisconnectOnlyInProgress = false
    private var postWorkoutTelemetryTransitionAwaitingControl = false
    private var postWorkoutTelemetryTransitionSoftStopInProgress = false
    private var postWorkoutTelemetryReconnectInProgress = false
    private var postWorkoutTelemetryAwaitingReconnectControl = false
    private var postWorkoutFreshTelemetryStartPending = false
    private var postWorkoutTelemetryReconnectRunnable: Runnable? = null
    private var telemetryOnlyStartReconnectInProgress = false
    private var telemetryOnlyStartShouldBypassControlRequest = false
    private var telemetryOnlyStartReconnectRunnable: Runnable? = null
    private var postWorkoutCompletionExitArmed = false
    private var postWorkoutCompletionExitAwaitingControl = false
    private var postWorkoutCompletionExitSoftStopInProgress = false
    private var postWorkoutReleaseRampOwner: PostWorkoutReleaseOwner? = null
    private var postWorkoutReleaseRampPlan: ReleaseRampPlan? = null
    private var postWorkoutReleaseRampStartedAtElapsedMs = 0L
    private var postWorkoutReleaseRampFloorHoldLogged = false
    private var postWorkoutReleaseRampRunnable: Runnable? = null
    private var debugTransportDisconnectInProgress = false
    private var pendingExternalTrainerPreparationAfterPermission = false
    private var externalTrainerPreparationTimeoutRunnable: Runnable? = null
    private var pendingAppControlledTargetPowerW: Int? = null
    private var stopFlowTransportClosePolicy = StopFlowTransportClosePolicy.CLOSE_AFTER_COMPLETION
    override val pendingBluetoothPermissionRequestId: Long
        get() = _pendingPermissionRequestId
    private var _pendingPermissionRequestId = 0L
    private val failureStrings = AppFailureStringResolver.fromContext(context)
    private val activeTrainerReleaseProfile = TrainerReleaseProfiles.tunturiBaseline
    private val freeRideControlCoordinator = SessionFreeRideControlCoordinator(
        requestControl = { ftmsController.requestControl() },
        resetTrainer = { ftmsController.reset() },
        setTargetPower = { targetWatts -> ftmsController.setTargetPower(targetWatts) },
    )
    private val connectionFlow = SessionConnectionFlow(
        uiState = uiState,
        mainThreadHandler = mainThreadHandler,
        connectFlowTimeoutMs = connectFlowTimeoutMs,
        onConnectFlowTimeoutElapsed = {
            Log.w("FTMS", "Connect-flow timeout reached; awaiting user action from CONNECTING")
            uiState.connectingTimeoutMessage.value = failureStrings.resolve(
                resId = R.string.connecting_timeout_message,
                fallback = "Could not connect to the trainer yet. Check that the trainer is powered on and nearby.",
            )
            recordDiagnostics(
                category = "session",
                event = "connect_flow_timeout_prompted",
            )
        },
        onMockSessionConnected = {
            uiState.connectingTimeoutMessage.value = null
            uiState.ftmsReady.value = true
            uiState.ftmsControlGranted.value = true
            val postWorkoutFreshTelemetry = postWorkoutFreshTelemetryStartPending
            if (!postWorkoutFreshTelemetry) {
                sessionManager.startSession(
                    ftpWatts = currentFtpWatts(),
                    startActive = false,
                )
            } else {
                postWorkoutFreshTelemetryStartPending = false
            }
            uiState.pendingCadenceStartAfterControlGranted = true
            uiState.autoPausedByZeroCadence = false
            keepScreenOn()
            uiState.screen.value = AppScreen.SESSION
            startMockTrainerEngine()
            applyCadenceDrivenRunnerControl(uiState.bikeData.value?.instantaneousCadenceRpm)
            dumpUiState(
                if (postWorkoutFreshTelemetry) {
                    "transitionFromConnectingToSessionInMockMode(postWorkoutFreshTelemetry)"
                } else {
                    "transitionFromConnectingToSessionInMockMode"
                },
            )
        },
        onSessionControlGranted = {
            completeConnectingToSession(
                dumpReason = "transitionFromConnectingToSessionAfterControlGranted",
                postWorkoutFreshTelemetryDumpReason =
                    "transitionFromConnectingToSessionAfterPostWorkoutFreshTelemetryStart",
            )
        },
    )
    private val stopFlowPolicy = SessionStopFlowPolicy(
        uiState = uiState,
        mainThreadHandler = mainThreadHandler,
        stopFlowTimeoutMs = stopFlowTimeoutMs,
        onStopFlowTimeout = {
            Log.w("FTMS", "Stop-flow timeout reached; finalizing summary without STOP acknowledgement")
            recordDiagnostics(
                category = "session",
                event = "stop_flow_timeout",
            )
            stopFlowTerminalCallbacks.onStopFlowTimeout()
        },
    )
    private val stopFlowCompletionSideEffects = SessionStopFlowCompletionSideEffectAdapter(
        uiState = uiState,
        finalizeSessionSummary = sessionManager::stopSession,
        summaryProvider = { sessionManager.lastSummary },
        allowScreenOff = allowScreenOff,
        onAfterSummaryTransition = { reason ->
            dumpUiState("completeStopFlowToSummary(reason=$reason)")
            recordDiagnostics(
                category = "session",
                event = "stop_flow_completed",
                context = mapOf("reason" to reason),
            )
            endSessionScope(reason = "stop_flow_completed:$reason")
        },
    )
    private val stopFlowCompletionPolicy = SessionStopFlowCompletionPolicy(
        stopFlowPolicy = stopFlowPolicy,
        onStopFlowCompleted = stopFlowCompletionSideEffects::completeToSummary,
    )
    private val stopFlowTerminalCallbacks = SessionStopFlowTerminalCallbackAdapter(
        completeStopFlowToSummary = ::completeStopFlowToSummary,
        closeBleTransport = ::closeBleTransportAfterStopFlow,
        shouldCloseBleTransport = {
            stopFlowTransportClosePolicy == StopFlowTransportClosePolicy.CLOSE_AFTER_COMPLETION
        },
        onBleCloseSkipped = ::handleStopFlowBleCloseSkipped,
    )
    private val rollbackToMenuRecoverySideEffects = SessionRollbackToMenuRecoverySideEffectAdapter(
        uiState = uiState,
        cancelConnectFlowTimeout = ::cancelConnectFlowTimeout,
        cancelMockConnectTransition = ::cancelMockConnectTransition,
        stopMockTrainerEngine = ::stopMockTrainerEngine,
        stopWorkout = ::stopWorkout,
        clearWorkoutRunner = { workoutRunner = null },
        stopSession = sessionManager::stopSession,
        resetStopFlowPolicy = stopFlowPolicy::resetToIdle,
        resetFtmsUiState = { resetFtmsUiState(clearReady = true) },
        allowScreenOff = allowScreenOff,
        closeBleTransport = { bleClient?.close() },
        onAfterRollbackApplied = { reason ->
            recordDiagnostics(
                category = "session",
                event = "rollback_to_menu",
                context = mapOf("reason" to reason),
            )
            endSessionScope(reason = "rollback_to_menu:$reason")
            Log.w("FTMS", "Request-control failure handled: $reason")
            dumpUiState("handleRequestControlFailure(reason=$reason)")
        },
    )
    private val requestControlFailureAdapter = SessionRequestControlFailureAdapter(
        currentScreen = { uiState.screen.value },
        onFailureIgnoredOutsideActiveFlow = { reason ->
            Log.d("FTMS", "Ignoring request-control failure outside active flow: $reason")
            recordDiagnostics(
                category = "session",
                event = "request_control_failure_ignored",
                context = mapOf("reason" to reason),
            )
        },
        rollbackToMenuWithConnectionIssue = ::rollbackToMenuWithConnectionIssue,
        finalizeSessionToSummaryWithConnectionIssue = ::finalizeSessionToSummaryWithConnectionIssue,
    )
    internal val workoutReadinessEvaluator = SessionWorkoutReadinessEvaluator(
        uiState = uiState,
        currentFtpWatts = currentFtpWatts,
        allowLegacyWorkoutFallback = allowLegacyWorkoutFallback,
        toUserMessage = ::toUserMessage,
        onExecutionMappingFailure = onExecutionMappingFailure,
        recordDiagnostics = { category, event, context ->
            recordDiagnostics(category = category, event = event, context = context)
        },
    )

    /**
     * Creates fresh BLE/controller instances for the current activity lifetime.
     */
    fun initialize() {
        bleClient = createFtmsBleClient()
        ftmsController = createFtmsController()
    }

    /**
     * Starts FTMS connection flow for a new session.
     *
     * Session start is blocked until the selected setup mode is runnable. Structured
     * modes still require a validated workout, but Telemetry only is allowed to
     * acquire control and record telemetry without a runner plan.
     */
    override fun startSessionConnection() {
        val useMockTrainer = isMockTrainerModeEnabled()
        val telemetryOnlyModeActive = isTelemetryOnlyModeSelected()
        resetPostWorkoutCompletionExitState()
        if (!telemetryOnlyModeActive && !uiState.workoutReady.value) {
            Log.w("WORKOUT", "Start ignored: no valid workout selected")
            dumpUiState("startSessionConnectionIgnored(noWorkout)")
            return
        }
        if (telemetryOnlyModeActive &&
            (uiState.selectedWorkout.value != null || uiState.selectedImportedWorkout.value != null)
        ) {
            Log.w("WORKOUT", "Telemetry-only start clearing stale structured workout selection")
            clearStructuredWorkoutSelectionForTelemetryOnlyMode()
            workoutReadinessEvaluator.clearLastFailureSignal()
        }
        val selectedWorkout = uiState.selectedWorkout.value
        val selectedImportedWorkout = uiState.selectedImportedWorkout.value
        if (!telemetryOnlyModeActive && selectedWorkout == null && selectedImportedWorkout == null) {
            Log.w("WORKOUT", "Start ignored: selected workout missing")
            dumpUiState("startSessionConnectionIgnored(workoutMissing)")
            return
        }
        val executionEligible = when {
            telemetryOnlyModeActive -> true
            selectedWorkout != null -> workoutReadinessEvaluator.evaluateWorkoutExecutionEligibility(selectedWorkout)
            selectedImportedWorkout != null -> {
                workoutReadinessEvaluator.evaluateImportedWorkoutExecutionEligibility(
                    workout = selectedImportedWorkout,
                    source = "start_session_ergo",
                )
            }
            else -> false
        }
        if (!executionEligible) {
            uiState.workoutReady.value = false
            dumpUiState("startSessionConnectionIgnored(executionBlocked)")
            return
        }
        if (!useMockTrainer && currentFtmsDeviceMac() == null) {
            Log.w("BLE", "Start ignored: FTMS device MAC is missing or invalid")
            dumpUiState("startSessionConnectionIgnored(noFtmsMac)")
            return
        }

        logTestMarker(
            event = "session_start_pressed",
            context = mapOf(
                "setupMode" to uiState.selectedSessionSetupMode.value.name.lowercase(),
                "trainerMac" to (currentFtmsDeviceMac() ?: "none"),
                "trainerWarmReady" to canReusePreparedTrainerConnection().toString(),
                "mockTrainer" to useMockTrainer.toString(),
            ),
        )

        beginSessionScope(entryPoint = if (useMockTrainer) "start_session_mock" else "start_session_ble")
        recordDiagnostics(
            category = "session",
            event = "start_requested",
            context = mapOf("mockTrainer" to useMockTrainer.toString()),
        )

        cancelConnectFlowTimeout()
        stopFlowPolicy.resetToIdle()
        cancelMockConnectTransition()
        stopMockTrainerEngine()
        uiState.connectionIssueMessage.value = null
        uiState.connectingTimeoutMessage.value = null
        uiState.suggestTrainerSearchAfterConnectionIssue.value = false
        uiState.suggestOpenSettingsAfterConnectionIssue.value = false
        val canReusePreparedTrainer = canReusePreparedTrainerConnection()
        resetFtmsUiState(clearReady = !canReusePreparedTrainer)
        uiState.pendingSessionStartAfterPermission = !useMockTrainer && !canReusePreparedTrainer
        if (!canReusePreparedTrainer) {
            uiState.bikeData.value = null
            uiState.bikeDataLastUpdatedAtEpochMs = null
        }
        externalTrainerControlRequestPending = false
        externalTrainerControlRequestOutcome = null
        pendingExternalTrainerPreparationAfterPermission = false
        cancelExternalTrainerPreparationTimeout()
        externalTrainerPreparationState = ExternalTrainerPreparationState.IDLE
        trainerPreparationOwner = TrainerPreparationOwner.NONE
        telemetryOnlyStartReconnectInProgress = false
        telemetryOnlyStartShouldBypassControlRequest = false
        cancelPendingTelemetryOnlyReconnect()

        if (useMockTrainer) {
            invalidateActiveFtmsCallbackGeneration()
        }
        if (beginTelemetryOnlyReconnectForStartIfNeeded(useMockTrainer = useMockTrainer)) {
            dumpUiState("startSessionConnection(telemetryOnlyReconnectRequired)")
            return
        }
        if (!canReusePreparedTrainer) {
            bleClient?.close()
        }
        if (useMockTrainer) {
            connectHeartRate()
            uiState.pendingSessionStartAfterPermission = false
            enterConnectingState()
            scheduleMockConnectTransition()
            dumpUiState("startSessionConnectionMockMode")
            return
        }

        if (canReusePreparedTrainer) {
            recordDiagnostics(
                category = "session",
                event = "reuse_prepared_trainer_connection",
                context = mapOf("preparedMac" to preparedTrainerDeviceMac.orEmpty()),
            )
            enterConnectingState()
            ftmsController.requestControl()
            dumpUiState("startSessionConnection(reusedPreparedTrainer=true)")
            return
        }

        preparedTrainerDeviceMac = null
        bleClient = createFtmsBleClient()
        ftmsController = createFtmsController()

        val connectInitiated = ensureBluetoothPermission()
        if (connectInitiated) {
            uiState.pendingSessionStartAfterPermission = false
            _pendingPermissionRequestId = 0L
            recordDiagnostics(
                category = "session",
                event = "permission_granted_connect_attempt",
            )
            if (connectBleClients()) {
                enterConnectingState()
            } else {
                cancelConnectFlowTimeout()
                uiState.screen.value = AppScreen.MENU
                endSessionScope(reason = "connect_clients_failed")
            }
        } else {
            _pendingPermissionRequestId = ++nextPermissionRequestId
            recordDiagnostics(
                category = "permission",
                event = "bluetooth_connect_request_issued",
                context = mapOf("requestId" to _pendingPermissionRequestId.toString()),
            )
        }
        dumpUiState("startSessionConnection(connectInitiated=$connectInitiated)")
    }

    /**
     * Handles Android permission callback and resumes pending session start when possible.
     *
     * The [requestId] must match [pendingBluetoothPermissionRequestId] for the callback
     * to be processed. A mismatch means the callback is from a superseded permission
     * request and is silently ignored, preventing stale callbacks from tearing down
     * a newer pending start.
     */
    override fun onBluetoothPermissionResult(granted: Boolean, requestId: Long) {
        if (requestId != _pendingPermissionRequestId) {
            Log.d("BLE", "Stale permission callback ignored (requestId=$requestId, expected=$_pendingPermissionRequestId)")
            recordDiagnostics(
                category = "permission",
                event = "bluetooth_connect_stale_callback",
                context = mapOf(
                    "granted" to granted.toString(),
                    "requestId" to requestId.toString(),
                    "expectedRequestId" to _pendingPermissionRequestId.toString(),
                ),
            )
            dumpUiState("permissionResult(stale, requestId=$requestId)")
            return
        }
        _pendingPermissionRequestId = 0L

        if (granted) {
            Log.d("BLE", "BLUETOOTH_CONNECT granted")
            recordDiagnostics(
                category = "permission",
                event = "bluetooth_connect_granted",
                context = mapOf(
                    "pendingStart" to uiState.pendingSessionStartAfterPermission.toString(),
                    "pendingExternalPreparation" to pendingExternalTrainerPreparationAfterPermission.toString(),
                    "requestId" to requestId.toString(),
                ),
            )
            if (uiState.pendingSessionStartAfterPermission) {
                uiState.pendingSessionStartAfterPermission = false
                if (connectBleClients()) {
                    enterConnectingState()
                } else {
                    cancelConnectFlowTimeout()
                    uiState.screen.value = AppScreen.MENU
                    endSessionScope(reason = "connect_clients_failed_after_permission")
                }
            } else if (pendingExternalTrainerPreparationAfterPermission) {
                pendingExternalTrainerPreparationAfterPermission = false
                if (connectBleClients()) {
                    startExternalTrainerPreparationTimeout()
                } else {
                    failExternalTrainerPreparation(reason = "connect_clients_failed_after_permission")
                }
            } else {
                Log.d("BLE", "Permission granted without pending session start; connect skipped")
            }
            dumpUiState("permissionResult(granted=true)")
            return
        }

        val pendingStartWasActive = uiState.pendingSessionStartAfterPermission
        uiState.pendingSessionStartAfterPermission = false
        val pendingExternalPreparationWasActive = pendingExternalTrainerPreparationAfterPermission
        pendingExternalTrainerPreparationAfterPermission = false
        if (pendingStartWasActive) {
            cancelConnectFlowTimeout()
            uiState.connectingTimeoutMessage.value = null
            uiState.connectionIssueMessage.value = toUserMessage(
                AppFailureFactory.sessionBluetoothPermissionDenied(),
            )
            uiState.suggestTrainerSearchAfterConnectionIssue.value = false
            uiState.suggestOpenSettingsAfterConnectionIssue.value = true
            uiState.screen.value = AppScreen.MENU
            endSessionScope(reason = "permission_denied")
        }
        if (pendingExternalPreparationWasActive) {
            failExternalTrainerPreparation(reason = "permission_denied")
        }
        Log.d("BLE", "BLUETOOTH_CONNECT denied")
        recordDiagnostics(
            category = "permission",
            event = "bluetooth_connect_denied",
            context = mapOf(
                "pendingStartWasActive" to pendingStartWasActive.toString(),
                "pendingExternalPreparationWasActive" to pendingExternalPreparationWasActive.toString(),
                "requestId" to requestId.toString(),
            ),
        )
        dumpUiState("permissionResult(granted=false)")
    }

    /**
     * Opens FTMS and HR links for a pending session-start flow.
     *
     * Returns true only when FTMS connect was started with a valid configured trainer MAC.
     */
    fun connectBleClients(): Boolean {
        val ftmsDeviceMac = currentFtmsDeviceMac()
        if (ftmsDeviceMac == null) {
            Log.w("BLE", "FTMS connect skipped: missing or invalid MAC address")
            dumpUiState("connectBleClientsSkipped(noFtmsMac)")
            return false
        }

        recordDiagnostics(
            category = "session",
            event = "connect_ble_clients",
            context = mapOf("hasFtmsMac" to "true"),
        )
        bleClient?.let { client ->
            connectFtmsTransport(client, ftmsDeviceMac)
        }
        connectHeartRate()
        return true
    }

    /**
     * Imports a workout file selected by the user and updates UI-ready metadata.
     */
    fun onWorkoutFileSelected(uri: Uri?) {
        if (uri == null) {
            dumpUiState("workoutSelection(cancelled)")
            return
        }
        importWorkoutFromUri(uri)
    }

    /**
     * Applies the bundled guided workout asset as the active selection.
     *
     * This keeps the built-in guided path on the canonical `.ewo` import seam
     * without allowing arbitrary user-selected file import.
     */
    fun onStarterWorkoutSelected() {
        onBundledWorkoutSelected(
            assetPath = io.github.ewoc2026.ewoc.workout.StarterWorkoutProvider.assetPath,
            sourceName = io.github.ewoc2026.ewoc.workout.StarterWorkoutProvider.sourceName,
        )
    }

    /**
     * Applies one packaged workout asset as the active file-based workout selection.
     *
     * Bundled workouts stay read-only because the app re-imports them directly from
     * APK assets instead of from a user-writable filesystem location.
     */
    fun onBundledWorkoutSelected(
        assetPath: String,
        sourceName: String = io.github.ewoc2026.ewoc.workout.BundledWorkoutAssetCatalog.fileNameFromAssetPath(assetPath),
    ) {
        val content = try {
            io.github.ewoc2026.ewoc.workout.BundledWorkoutAssetCatalog.loadText(
                context = context,
                assetPath = assetPath,
            )
        } catch (e: Exception) {
            Log.w("WORKOUT", "Failed reading bundled workout asset $assetPath: ${e.message}")
            applyBundledWorkoutFailure(
                failure = AppFailureFactory.workoutImportReadFailed(
                    e.message?.takeIf { it.isNotBlank() },
                ),
                sourceName = sourceName,
            )
            return
        }

        when (
            val result = workoutImportService.importFromText(
                sourceName = sourceName,
                content = content,
                context = currentEwoCompileContext(),
            )
        ) {
            is WorkoutImportResult.Success -> {
                val workoutFile = result.workoutFile
                if (workoutFile != null) {
                    uiState.selectedSessionSetupMode.value = SessionSetupMode.FILE
                    uiState.selectedWorkout.value = workoutFile
                    uiState.selectedImportedWorkout.value = null
                    workoutRunner = null
                    uiState.selectedWorkoutFileName.value = sourceName
                    uiState.selectedWorkoutImportError.value = null
                    recalculateSelectedWorkoutDerivedMetrics(workoutFile)
                    workoutReadinessEvaluator.clearLastFailureSignal()
                    Log.d(
                        "WORKOUT",
                        "Bundled workout applied name=$sourceName executionSteps=${uiState.selectedWorkoutStepCount.value} rawSteps=${workoutFile.steps.size} plannedTss=${uiState.selectedWorkoutPlannedTss.value}",
                    )
                    dumpUiState("bundledWorkoutApplied(name=$sourceName)")
                } else {
                    val bundledWorkout = result.ergoWorkout
                    if (bundledWorkout == null) {
                        applyBundledWorkoutFailure(
                            failure = AppFailureFactory.workoutImportFailure(
                                io.github.ewoc2026.ewoc.workout.WorkoutImportError(
                                    code = io.github.ewoc2026.ewoc.workout.WorkoutImportErrorCode.PARSE_FAILED,
                                    message = "Bundled workout did not import as a supported workout payload.",
                                    detectedFormat = result.format,
                                ),
                            ),
                            sourceName = sourceName,
                        )
                        return
                    }
                    uiState.selectedSessionSetupMode.value = SessionSetupMode.FILE
                    uiState.selectedWorkout.value = null
                    uiState.selectedImportedWorkout.value = bundledWorkout
                    workoutRunner = null
                    uiState.selectedWorkoutFileName.value = sourceName
                    uiState.selectedWorkoutImportError.value = null
                    recalculateSelectedImportedWorkoutDerivedMetrics(bundledWorkout)
                    workoutReadinessEvaluator.clearLastFailureSignal()
                    Log.d(
                        "WORKOUT",
                        "Bundled ergo workout applied name=$sourceName steps=${bundledWorkout.steps.size} durationSec=${bundledWorkout.totalDurationSec} plannedTss=${uiState.selectedWorkoutPlannedTss.value}",
                    )
                    dumpUiState("bundledWorkoutAppliedErgo(name=$sourceName)")
                }
            }

            is WorkoutImportResult.Failure -> {
                applyBundledWorkoutFailure(
                    failure = AppFailureFactory.workoutImportFailure(result.error),
                    sourceName = sourceName,
                )
            }
        }
    }

    fun onWorkoutEdited(workout: WorkoutFile, sourceName: String) {
        uiState.selectedSessionSetupMode.value = SessionSetupMode.EDITOR
        uiState.selectedWorkout.value = workout
        uiState.selectedImportedWorkout.value = null
        workoutRunner = null
        uiState.selectedWorkoutFileName.value = sourceName
        uiState.selectedWorkoutImportError.value = null
        recalculateSelectedWorkoutDerivedMetrics(workout)
        workoutReadinessEvaluator.clearLastFailureSignal()
        Log.d(
            "WORKOUT",
            "Workout editor applied name=$sourceName executionSteps=${uiState.selectedWorkoutStepCount.value} rawSteps=${workout.steps.size} plannedTss=${uiState.selectedWorkoutPlannedTss.value}",
        )
        dumpUiState("workoutEditorApplied(name=$sourceName)")
    }

    /**
     * Re-evaluates execution readiness when FTP changes.
     *
     * Execution mapper output depends on FTP; this keeps menu readiness and
     * degraded-mode messaging in sync with the current FTP value.
     */
    fun onFtpWattsChanged() {
        val selectedWorkout = uiState.selectedWorkout.value
        if (selectedWorkout != null) {
            recalculateSelectedWorkoutDerivedMetrics(selectedWorkout)
            dumpUiState("onFtpWattsChanged")
            return
        }

        val selectedImportedWorkout = uiState.selectedImportedWorkout.value
        if (selectedImportedWorkout != null) {
            recalculateSelectedImportedWorkoutDerivedMetrics(selectedImportedWorkout)
            dumpUiState("onFtpWattsChanged")
            return
        }

        uiState.selectedWorkoutPlannedTss.value = null
        uiState.selectedWorkoutTotalDurationSec.value = null
        uiState.workoutExecutionModeMessage.value = null
        uiState.workoutExecutionModeIsError.value = false
        dumpUiState("onFtpWattsChanged")
    }

    fun onTelemetryOnlyModeSelected() {
        uiState.selectedSessionSetupMode.value = SessionSetupMode.TELEMETRY_ONLY
        clearStructuredWorkoutSelectionForTelemetryOnlyMode()
        uiState.workoutReady.value = true
        workoutReadinessEvaluator.clearLastFailureSignal()
        Log.d("WORKOUT", "Telemetry-only mode selected")
        dumpUiState("telemetryOnlyModeSelected")
    }

    fun onSessionSetupModeSelected(mode: SessionSetupMode) {
        uiState.selectedSessionSetupMode.value = mode
        if (mode == SessionSetupMode.TELEMETRY_ONLY) {
            onTelemetryOnlyModeSelected()
        }
    }

    /**
     * Prepares FTMS telemetry/control transport for a non-workout feature such as the baseline test.
     *
     * Unlike [startSessionConnection], this keeps the caller on its current screen and reports
     * progress through [externalTrainerPreparationStateForExternalUse] instead of CONNECTING UI.
     */
    internal fun prepareTrainerForExternalUse(): Boolean {
        return prepareTrainerForOwner(owner = TrainerPreparationOwner.EXTERNAL_USE)
    }

    /**
     * Prepares and holds the FTMS transport warm while the rider remains in MENU flows.
     */
    internal fun prepareTrainerWarmConnectionInMenu(): Boolean {
        logTestMarker(
            event = "menu_warm_prepare_requested",
            context = mapOf("trainerMac" to (currentFtmsDeviceMac() ?: "none")),
        )
        return prepareTrainerForOwner(owner = TrainerPreparationOwner.MENU_WARM)
    }

    private fun prepareTrainerForOwner(owner: TrainerPreparationOwner): Boolean {
        val reconnectDelayMs = remainingExplicitTrainerReconnectDelayMs()
        if (explicitTrainerCloseInProgress || reconnectDelayMs > 0L) {
            scheduleTrainerPreparationAfterExplicitClose(
                owner = owner,
                delayMs = reconnectDelayMs,
            )
            return true
        }

        val useMockTrainer = isMockTrainerModeEnabled()
        val selectedFtmsMac = currentFtmsDeviceMac()
        externalTrainerControlRequestPending = false
        externalTrainerControlRequestOutcome = null
        pendingExternalTrainerPreparationAfterPermission = false
        cancelExternalTrainerPreparationTimeout()

        if (useMockTrainer) {
            trainerPreparationOwner = owner
            preparedTrainerDeviceMac = selectedFtmsMac
            if (uiState.ftmsReady.value && mockTrainerEngine != null) {
                externalTrainerPreparationState = ExternalTrainerPreparationState.READY
                return true
            }

            beginSessionScope(
                entryPoint = if (owner == TrainerPreparationOwner.MENU_WARM) {
                    "menu_warm_prepare_mock"
                } else {
                    "external_prepare_mock"
                },
            )
            stopFlowPolicy.resetToIdle()
            cancelConnectFlowTimeout()
            cancelMockConnectTransition()
            stopMockTrainerEngine()
            resetFtmsUiState(clearReady = true)
            uiState.bikeData.value = null
            uiState.bikeDataLastUpdatedAtEpochMs = null
            externalTrainerPreparationState = ExternalTrainerPreparationState.PENDING
            invalidateActiveFtmsCallbackGeneration()
            bleClient?.close()
            connectHeartRate()
            mainThreadHandler.post {
                if (externalTrainerPreparationState != ExternalTrainerPreparationState.PENDING) return@post
                uiState.ftmsReady.value = true
                uiState.ftmsControlGranted.value = true
                externalTrainerPreparationState = ExternalTrainerPreparationState.READY
                startMockTrainerEngine()
                dumpUiState("prepareTrainerForExternalUseMockReady")
            }
            dumpUiState("prepareTrainerForExternalUseMockPending")
            return true
        }

        if (selectedFtmsMac == null) {
            externalTrainerPreparationState = ExternalTrainerPreparationState.FAILED
            return false
        }

        if (trainerPreparationOwner != TrainerPreparationOwner.NONE &&
            trainerPreparationOwner != owner
        ) {
            if (trainerPreparationOwner == TrainerPreparationOwner.MENU_WARM &&
                owner == TrainerPreparationOwner.EXTERNAL_USE &&
                preparedTrainerDeviceMac == selectedFtmsMac
            ) {
                trainerPreparationOwner = owner
                if (externalTrainerPreparationState == ExternalTrainerPreparationState.IDLE) {
                    externalTrainerPreparationState = ExternalTrainerPreparationState.PENDING
                }
                return true
            }
            return false
        }

        trainerPreparationOwner = owner
        preparedTrainerDeviceMac = selectedFtmsMac
        if (uiState.ftmsReady.value &&
            ftmsController.isReady() &&
            preparedTrainerDeviceMac == selectedFtmsMac
        ) {
            externalTrainerPreparationState = ExternalTrainerPreparationState.READY
            return true
        }
        if (externalTrainerPreparationState == ExternalTrainerPreparationState.PENDING) {
            return true
        }

        beginSessionScope(
            entryPoint = if (owner == TrainerPreparationOwner.MENU_WARM) {
                "menu_warm_prepare_ble"
            } else {
                "external_prepare_ble"
            },
        )
        stopFlowPolicy.resetToIdle()
        cancelConnectFlowTimeout()
        cancelMockConnectTransition()
        stopMockTrainerEngine()
        resetFtmsUiState(clearReady = true)
        uiState.bikeData.value = null
        uiState.bikeDataLastUpdatedAtEpochMs = null
        bleClient?.close()
        bleClient = createFtmsBleClient()
        ftmsController = createFtmsController()
        externalTrainerPreparationState = ExternalTrainerPreparationState.PENDING

        val connectInitiated = ensureBluetoothPermission()
        if (connectInitiated) {
            _pendingPermissionRequestId = 0L
            if (!connectBleClients()) {
                failExternalTrainerPreparation(reason = "connect_clients_failed")
                return false
            }
            startExternalTrainerPreparationTimeout()
            dumpUiState("prepareTrainerForExternalUseBleConnectStarted")
            return true
        }

        pendingExternalTrainerPreparationAfterPermission = true
        _pendingPermissionRequestId = ++nextPermissionRequestId
        recordDiagnostics(
            category = "permission",
            event = "bluetooth_connect_request_issued",
            context = mapOf("requestId" to _pendingPermissionRequestId.toString()),
        )
        dumpUiState("prepareTrainerForExternalUseAwaitingPermission")
        return true
    }

    /**
     * Returns the latest external trainer-preparation state without consuming it.
     */
    internal fun externalTrainerPreparationStateForExternalUse(): ExternalTrainerPreparationState {
        return externalTrainerPreparationState
    }

    /**
     * Exposes trainer-preparation ownership for debug automation status dumps.
     */
    internal fun trainerPreparationOwnerForDebug(): String {
        return when (trainerPreparationOwner) {
            TrainerPreparationOwner.NONE -> "none"
            TrainerPreparationOwner.MENU_WARM -> "menu_warm"
            TrainerPreparationOwner.EXTERNAL_USE -> "external_use"
        }
    }

    /**
     * Returns the FTMS MAC currently parked for warm-link reuse, if any.
     */
    internal fun preparedTrainerDeviceMacForDebug(): String? = preparedTrainerDeviceMac

    /**
     * Reports whether session start would currently reuse the parked FTMS link.
     */
    internal fun preparedTrainerConnectionReusableForDebug(): Boolean = canReusePreparedTrainerConnection()

    /**
     * Returns a stable trainer-release status line for debug automation and handoff work.
     *
     * The current post-workout policy still evaluates everything through the continuation-oriented
     * release intent, so surfacing that assumption explicitly helps future teardown-policy refactors
     * distinguish "what the code currently believes" from "what the product should eventually do."
     */
    internal fun trainerReleaseSummaryForDebug(): String {
        return currentTrainerReleaseDebugSummary()
    }

    /**
     * Returns a compact trainer-release line suitable for the probe overlay.
     *
     * The overlay is rider/operator facing, so this stays intentionally shorter than the full
     * automation dump while still exposing the current release phase and reconnect gate state.
     */
    internal fun trainerReleaseOverlaySummaryForDebug(): String? {
        if (!shouldShowTrainerReleaseOverlaySummary()) {
            return null
        }
        val context = currentTrainerReleaseDebugContext()
        return buildString {
            append("Release: ")
            append(context.getValue("flowState"))
            append(" | intent=").append(context.getValue("policyIntent"))
            append(" | authority=").append(context.getValue("authority"))
            append(" | disconnect=").append(context.getValue("disconnectRequired"))
            append(" | ramp=").append(context.getValue("releaseRampOwner"))
            append(" | summary=").append(context.getValue("summaryWindowOpen"))
            append(" | settleMs=").append(context.getValue("reconnectDelayRemainingMs"))
            append(" | restart=").append(context.getValue("restartWindowOpen"))
        }
    }

    /**
     * Returns one semantic explanation of the current session/runtime intent for both overlay and
     * automation dumps.
     */
    internal fun sessionIntentDiagnosticsForDebug(): SessionIntentDiagnostics {
        val releaseContext = currentTrainerReleaseDebugContext()
        val selectedWorkoutLabel = uiState.selectedWorkout.value?.name
            ?: uiState.selectedImportedWorkout.value?.title
            ?: uiState.selectedWorkoutFileName.value
        return resolveSessionIntentDiagnostics(
            SessionIntentDebugFacts(
                screen = uiState.screen.value,
                setupMode = uiState.selectedSessionSetupMode.value,
                selectedWorkoutLabel = selectedWorkoutLabel,
                runnerRunning = uiState.runner.value.running,
                runnerPaused = uiState.runner.value.paused,
                runnerDone = uiState.runner.value.done,
                ftmsReady = uiState.ftmsReady.value,
                ftmsControlGranted = uiState.ftmsControlGranted.value,
                trainerAuthority = uiState.trainerControlAuthority.value,
                preparedTrainerReusable = canReusePreparedTrainerConnection(),
                telemetryOnlyStartReconnectInProgress = telemetryOnlyStartReconnectInProgress,
                telemetryOnlyStartShouldBypassControlRequest = telemetryOnlyStartShouldBypassControlRequest,
                postWorkoutFreerideModeActive = uiState.postWorkoutFreerideModeActive,
                releaseFlowState = releaseContext.getValue("flowState"),
            ),
        )
    }

    internal fun sessionIntentOverlaySummaryForDebug(): String {
        return sessionIntentDiagnosticsForDebug().overlaySummary(
            releaseSummary = trainerReleaseOverlaySummaryForDebug(),
        )
    }

    /**
     * Releases trainer resources held for non-workout external use.
     *
     * This is intentionally conservative: it only tears down transport that the external-use path
     * previously claimed, so normal session start/stop flow retains ownership of its own teardown.
     */
    internal fun releaseTrainerForExternalUse() {
        releaseTrainerPreparationForOwner(owner = TrainerPreparationOwner.EXTERNAL_USE)
    }

    /**
     * Releases the warm trainer connection that MENU-time selection previously claimed.
     */
    internal fun releaseTrainerWarmConnectionInMenu() {
        logTestMarker(
            event = "menu_warm_release_requested",
            context = mapOf(
                "trainerMac" to (preparedTrainerDeviceMac ?: currentFtmsDeviceMac() ?: "none"),
            ),
        )
        releaseTrainerPreparationForOwner(owner = TrainerPreparationOwner.MENU_WARM)
    }

    private fun releaseTrainerPreparationForOwner(owner: TrainerPreparationOwner) {
        pendingExternalTrainerPreparationAfterPermission = false
        cancelExternalTrainerPreparationTimeout()
        cancelPendingTrainerPreparationAfterExplicitClose()
        externalTrainerControlRequestPending = false
        externalTrainerControlRequestOutcome = null
        if (trainerPreparationOwner != owner) {
            if (trainerPreparationOwner == TrainerPreparationOwner.NONE) {
                preparedTrainerDeviceMac = null
            }
            externalTrainerPreparationState = ExternalTrainerPreparationState.IDLE
            return
        }

        trainerPreparationOwner = TrainerPreparationOwner.NONE
        preparedTrainerDeviceMac = null
        externalTrainerPreparationState = ExternalTrainerPreparationState.IDLE
        stopMockTrainerEngine()
        explicitTrainerCloseInProgress = true
        if (bleClient?.close() != true) {
            completeExplicitTrainerCloseWithoutDisconnectCallback()
        }
        resetFtmsUiState(clearReady = true)
        uiState.bikeData.value = null
        uiState.bikeDataLastUpdatedAtEpochMs = null
        endSessionScope(reason = "external_release")
        dumpUiState("releaseTrainerForExternalUse")
    }

    /**
     * Requests FTMS control for a non-workout feature that reuses the active FTMS link.
     *
     * External consumers (e.g. baseline fitness test) call this instead of starting a
     * full workout session. The request is asynchronous: callers poll
     * [consumeExternalTrainerControlRequestOutcome] to learn whether control was
     * [ExternalTrainerControlRequestOutcome.GRANTED] or
     * [ExternalTrainerControlRequestOutcome.FAILED].
     *
     * @return `true` if the request was accepted for processing (control already held
     *   or FTMS link is ready and a Control Point request was sent), `false` if the
     *   FTMS link is not ready and the request cannot proceed.
     */
    internal fun requestTrainerControlForExternalUse(): Boolean {
        externalTrainerControlRequestOutcome = null
        val selectedFtmsMac = currentFtmsDeviceMac()
        if (trainerPreparationOwner == TrainerPreparationOwner.MENU_WARM &&
            selectedFtmsMac != null &&
            preparedTrainerDeviceMac == selectedFtmsMac
        ) {
            trainerPreparationOwner = TrainerPreparationOwner.EXTERNAL_USE
            if (externalTrainerPreparationState == ExternalTrainerPreparationState.IDLE &&
                uiState.ftmsReady.value &&
                ftmsController.isReady()
            ) {
                externalTrainerPreparationState = ExternalTrainerPreparationState.READY
            }
        }
        if (uiState.ftmsControlGranted.value) {
            externalTrainerControlRequestPending = false
            externalTrainerControlRequestOutcome = ExternalTrainerControlRequestOutcome.GRANTED
            return true
        }
        if (!uiState.ftmsReady.value || !ftmsController.isReady()) {
            externalTrainerControlRequestPending = false
            return false
        }
        externalTrainerControlRequestPending = true
        ftmsController.requestControl()
        return true
    }

    /**
     * Returns and clears the terminal outcome for the last externally initiated control
     * request. Returns `null` if the request is still pending or no request has been made.
     *
     * Callers should poll this after [requestTrainerControlForExternalUse] returns `true`.
     * The outcome is consumed on first read; subsequent calls return `null` until a new
     * request is initiated.
     */
    internal fun consumeExternalTrainerControlRequestOutcome(): ExternalTrainerControlRequestOutcome? {
        val outcome = externalTrainerControlRequestOutcome
        externalTrainerControlRequestOutcome = null
        return outcome
    }

    /**
     * Applies a one-off ERG target outside the structured workout runner.
     *
     * The caller must hold FTMS control via [requestTrainerControlForExternalUse]
     * before calling this. The target is sent directly through [FtmsController] (or
     * the mock trainer in debug mode) and is not tracked by the workout runner.
     * Use [resetExternalTrainerToIdle] when the caller intends to end that FTMS-controlled
     * ERG phase and return the trainer toward its default state.
     */
    internal fun setExternalTargetPower(watts: Int) {
        if (isMockTrainerModeEnabled()) {
            mockTrainerEngine?.setTargetPowerWatts(watts)
            markAppControlledTargetConfirmed(watts)
            return
        }
        markAppControlledTargetRequested(watts)
        ftmsController.setTargetPower(watts)
    }

    /**
     * Resets the external-use trainer session toward FTMS Idle without issuing STOP.
     *
     * This is the closest normative FTMS match for "end this app-owned ERG phase" while
     * keeping the BLE link open for further observation or later control reacquisition.
     */
    internal fun resetExternalTrainerToIdle() {
        if (isMockTrainerModeEnabled()) {
            mockTrainerEngine?.setTargetPowerWatts(null)
            return
        }
        ftmsController.reset()
    }

    /**
     * Sends a raw FTMS STOP for debug-only trainer probing outside the workout runner.
     *
     * This intentionally bypasses structured-session ownership so adb automation can compare
     * trainer behavior across firmware variants using either an active session transport or
     * a warm menu-owned connection. The caller is responsible for ensuring the trainer is in
     * a state where a direct STOP is safe to issue.
     *
     * @return `true` when the FTMS link was ready and the STOP command was sent.
     */
    internal fun stopTrainerWorkoutForDebug(): Boolean {
        if (isMockTrainerModeEnabled()) {
            return false
        }
        if (!uiState.ftmsReady.value || !ftmsController.isReady()) {
            return false
        }
        ftmsController.stopWorkout()
        return true
    }

    /**
     * Sends a raw FTMS RESET for debug-only trainer probing outside the workout runner.
     *
     * Trainers differ in how they react to RESET while an app-owned transport is still open,
     * so this escape hatch exists specifically for compatibility mapping and protocol research.
     *
     * @return `true` when the FTMS link was ready and the RESET command was sent.
     */
    internal fun resetTrainerForDebug(): Boolean {
        if (isMockTrainerModeEnabled()) {
            return false
        }
        if (!uiState.ftmsReady.value || !ftmsController.isReady()) {
            return false
        }
        ftmsController.reset()
        return true
    }

    /**
     * Closes the active FTMS transport without layering on STOP or RESET first.
     *
     * This debug-only probe isolates whether trainer-local controls stay locked simply because
     * the BLE session remains connected after control procedures have already been exercised.
     *
     * @return `true` when an active FTMS transport existed and close was started.
     */
    internal fun disconnectTrainerTransportForDebug(): Boolean {
        if (isMockTrainerModeEnabled()) {
            return false
        }
        if (!uiState.ftmsReady.value || !ftmsController.isReady()) {
            return false
        }
        val client = bleClient ?: return false
        debugTransportDisconnectInProgress = true
        if (client.close()) {
            return true
        }
        debugTransportDisconnectInProgress = false
        return false
    }

    /**
     * Switches a finished structured session into rider-controlled post-workout free ride.
     *
     * The workout session remains open for telemetry collection, but the FTMS control-holding
     * transport is intentionally torn down and reconnected in observer mode so trainer-local
     * resistance adjustment can become available again on trainers that keep remote control
     * latched until disconnect.
     */
    internal fun continueRideAfterWorkoutComplete(): Boolean {
        if (uiState.screen.value != AppScreen.SESSION || !uiState.runner.value.done) {
            dumpUiState("continueRideAfterWorkoutCompleteIgnored")
            return false
        }
        recordTrainerReleaseRuntime(event = "continue_ride_requested")

        val preservedTrainerMetrics = sessionManager.currentCumulativeTrainerMetrics()
        stopWorkout()
        workoutRunner = null
        uiState.selectedSessionSetupMode.value = SessionSetupMode.TELEMETRY_ONLY
        clearStructuredWorkoutSelectionForTelemetryOnlyMode()
        uiState.postWorkoutFreerideModeActive = false
        uiState.pendingCadenceStartAfterControlGranted = true
        uiState.autoPausedByZeroCadence = false
        sessionManager.bridgeCumulativeTrainerMetrics(
            distanceMeters = preservedTrainerMetrics.distanceMeters,
            totalEnergyKcal = preservedTrainerMetrics.totalEnergyKcal,
        )

        if (isMockTrainerModeEnabled()) {
            uiState.ftmsControlGranted.value = true
            syncSessionActivityState()
            recordTrainerReleaseRuntime(
                event = "continue_ride_mock_reuse",
                extraContext = mapOf("path" to "mock_reuse"),
            )
            dumpUiState("continueRideAfterWorkoutCompleteMockTelemetryOnlyReuse")
            return true
        }

        if (!uiState.ftmsReady.value) {
            evaluatePostWorkoutReleaseRamp(stage = "continue_ride_requested")
            recordTrainerReleaseRuntime(
                event = "continue_ride_no_trainer_release_needed",
                extraContext = mapOf("path" to "ftms_not_ready"),
            )
            completePostWorkoutTelemetryTransition(
                preserveControlGranted = false,
                dumpReason = "continueRideAfterWorkoutCompleteNoReadyFtmsTelemetryOnlyReuse",
            )
            return true
        }

        val releaseDecision = evaluatePostWorkoutReleaseRamp(stage = "continue_ride_requested")
        if (uiState.ftmsControlGranted.value) {
            continuePostWorkoutReleaseTeardown(
                owner = PostWorkoutReleaseOwner.TELEMETRY_TRANSITION,
                decision = releaseDecision,
            )
            dumpUiState("continueRideAfterWorkoutCompleteReleaseSequenceStartedWithExistingControl")
            return true
        }

        postWorkoutTelemetryTransitionAwaitingControl = true
        recordTrainerReleaseRuntime(
            event = "telemetry_release_control_requested",
            extraContext = mapOf("owner" to PostWorkoutReleaseOwner.TELEMETRY_TRANSITION.wireName),
        )
        ftmsController.requestControl()
        dumpUiState("continueRideAfterWorkoutCompleteRequestControlForReleaseSequence")
        return true
    }

    /**
     * Starts trainer-side workout exit as soon as the completion prompt is shown.
     *
     * The rider still chooses between Continue ride and Summary on the visible dialog, but the
     * trainer can spend that decision time performing its required stop/disconnect dwell.
     */
    internal fun preparePostWorkoutExitWindowAfterCompletion(): Boolean {
        if (uiState.screen.value != AppScreen.SESSION || !uiState.runner.value.done) {
            dumpUiState("preparePostWorkoutExitWindowAfterCompletionIgnored")
            return false
        }
        recordTrainerReleaseRuntime(event = "completion_exit_prep_requested")
        logTestMarker(event = "post_workout_exit_prep_requested")
        val releaseDecision = evaluatePostWorkoutReleaseRamp(stage = "completion_prep_requested")
        if (continueRideRestartWindowOpen()) {
            postWorkoutCompletionExitArmed = true
            recordTrainerReleaseRuntime(
                event = "completion_exit_prep_already_ready",
                extraContext = mapOf("path" to "restart_window_open"),
            )
            logTestMarker(event = "post_workout_exit_prep_already_ready")
            dumpUiState("preparePostWorkoutExitWindowAfterCompletionAlreadyReady")
            return true
        }
        if (postWorkoutCompletionExitArmed ||
            postWorkoutCompletionExitAwaitingControl ||
            postWorkoutCompletionExitSoftStopInProgress ||
            explicitTrainerCloseInProgress
        ) {
            recordTrainerReleaseRuntime(
                event = "completion_exit_prep_already_active",
                extraContext = mapOf("path" to "existing_release_state"),
            )
            logTestMarker(event = "post_workout_exit_prep_already_armed")
            dumpUiState("preparePostWorkoutExitWindowAfterCompletionAlreadyArmed")
            return true
        }

        postWorkoutCompletionExitArmed = true
        if (isMockTrainerModeEnabled() || !uiState.ftmsReady.value) {
            recordTrainerReleaseRuntime(
                event = "completion_exit_prep_no_trainer_release_needed",
                extraContext = mapOf(
                    "mockTrainer" to isMockTrainerModeEnabled().toString(),
                    "ftmsReady" to uiState.ftmsReady.value.toString(),
                ),
            )
            logTestMarker(
                event = "post_workout_exit_prep_no_trainer_exit_needed",
                context = mapOf(
                    "mockTrainer" to isMockTrainerModeEnabled().toString(),
                    "ftmsReady" to uiState.ftmsReady.value.toString(),
                ),
            )
            dumpUiState("preparePostWorkoutExitWindowAfterCompletionNoTrainerExitNeeded")
            return true
        }

        if (uiState.ftmsControlGranted.value) {
            continuePostWorkoutReleaseTeardown(
                owner = PostWorkoutReleaseOwner.COMPLETION_EXIT_PREP,
                decision = releaseDecision,
            )
            dumpUiState("preparePostWorkoutExitWindowAfterCompletionReleaseSequenceStartedWithExistingControl")
            return true
        }

        postWorkoutCompletionExitAwaitingControl = true
        recordTrainerReleaseRuntime(
            event = "completion_exit_control_requested",
            extraContext = mapOf("owner" to PostWorkoutReleaseOwner.COMPLETION_EXIT_PREP.wireName),
        )
        logTestMarker(event = "post_workout_exit_prep_request_control")
        ftmsController.requestControl()
        dumpUiState("preparePostWorkoutExitWindowAfterCompletionRequestControl")
        return true
    }

    /**
     * Reports whether completion-time trainer exit prep has been started or already finished.
     */
    internal fun hasPreparedPostWorkoutExitWindow(): Boolean {
        return postWorkoutCompletionExitArmed ||
            postWorkoutCompletionExitAwaitingControl ||
            postWorkoutCompletionExitSoftStopInProgress ||
            explicitTrainerCloseInProgress ||
            continueRideRestartWindowOpen()
    }

    /**
     * Ends the current diagnostics/session scope after a completion-time fast choice is consumed.
     */
    internal fun finishPreparedPostWorkoutExit(reason: String) {
        resetPostWorkoutCompletionExitState()
        logTestMarker(
            event = "post_workout_exit_prep_finished",
            context = mapOf("reason" to reason),
        )
        endSessionScope(reason = reason)
        dumpUiState("finishPreparedPostWorkoutExit(reason=$reason)")
    }

    /**
     * Closes the FTMS transport during post-workout free ride without reconnecting.
     *
     * This debug-only probe isolates whether trainer-local controls stay locked as
     * long as any FTMS link remains connected, even after control ownership has
     * already been released.
     */
    internal fun disconnectPostWorkoutFreerideTransportForDebug(): Boolean {
        if (uiState.screen.value != AppScreen.SESSION ||
            !uiState.postWorkoutFreerideModeActive ||
            !uiState.ftmsReady.value
        ) {
            dumpUiState("disconnectPostWorkoutFreerideTransportIgnored")
            return false
        }
        postWorkoutFreerideReconnectInProgress = false
        postWorkoutFreerideDisconnectOnlyInProgress = true
        bleClient?.close()
        dumpUiState("disconnectPostWorkoutFreerideTransportStarted")
        return true
    }

    /**
     * Resets active transport/session state so debug automation can always return to a clean menu.
     *
     * This intentionally favors deterministic recovery over graceful summary preservation.
     * It is reserved for debug-only dead-end cleanup when manual validation needs a known
     * baseline faster than normal stop-flow can provide.
     */
    internal fun forceResetToCleanMenuForDebug() {
        cancelConnectFlowTimeout()
        stopFlowPolicy.resetToIdle()
        cancelMockConnectTransition()
        cancelExternalTrainerPreparationTimeout()
        cancelPendingTrainerPreparationAfterExplicitClose()
        cancelPendingTelemetryOnlyReconnect()
        telemetryOnlyStartReconnectInProgress = false
        telemetryOnlyStartShouldBypassControlRequest = false
        explicitTrainerCloseInProgress = false
        explicitTrainerReconnectAllowedAtElapsedMs = 0L
        pendingExternalTrainerPreparationAfterPermission = false
        externalTrainerControlRequestPending = false
        externalTrainerControlRequestOutcome = null
        externalTrainerPreparationState = ExternalTrainerPreparationState.IDLE
        trainerPreparationOwner = TrainerPreparationOwner.NONE
        preparedTrainerDeviceMac = null
        resetPostWorkoutCompletionExitState()
        _pendingPermissionRequestId = 0L

        stopWorkout()
        stopMockTrainerEngine()
        workoutRunner = null
        uiState.runner.value = io.github.ewoc2026.ewoc.workout.runner.RunnerState.stopped()
        uiState.workoutReady.value = false
        uiState.pendingSessionStartAfterPermission = false
        uiState.pendingCadenceStartAfterControlGranted = false
        uiState.autoPausedByZeroCadence = false
        uiState.connectingTimeoutMessage.value = null
        uiState.connectionIssueMessage.value = null
        uiState.suggestTrainerSearchAfterConnectionIssue.value = false
        uiState.suggestOpenSettingsAfterConnectionIssue.value = false
        uiState.bikeData.value = null
        uiState.bikeDataLastUpdatedAtEpochMs = null
        uiState.heartRate.value = null
        uiState.heartRateLastUpdatedAtEpochMs = null

        sessionManager.resetToIdle()
        clearAppControlledTargetTracking()
        resetFtmsUiState(clearReady = true)
        invalidateActiveFtmsCallbackGeneration()
        bleClient?.close()
        bleClient = null
        closeHeartRate()
        allowScreenOff()
        endSessionScope(reason = "debug_force_clean_menu_reset")
        dumpUiState("forceResetToCleanMenuForDebug")
    }

    /**
     * Stops the active session and enters explicit stop-flow before summary.
     *
     * Summary is shown only after STOP acknowledgement, disconnect, or timeout,
     * so UI transitions cannot race ahead of trainer teardown.
     */
    override fun endSessionAndGoToSummary() {
        if (stopFlowPolicy.isStopFlowInProgress()) {
            dumpUiState("endSessionAndGoToSummaryIgnoredAlreadyStopping")
            return
        }
        recordDiagnostics(
            category = "session",
            event = "stop_requested",
        )
        beginStopFlow(
            dumpReason = "endSessionAndGoToSummary",
            mockDumpReason = "endSessionAndGoToSummaryMock",
        )
    }

    /**
     * Finishes workout-complete SUMMARY through STOP acknowledgement/timeout without forcing a
     * transport disconnect up front.
     *
     * This keeps the ordinary finish path separate from the stricter Continue-ride handoff
     * teardown, so the proactive trainer release remains an explicit opt-in optimization.
     */
    internal fun endWorkoutCompleteSessionToSummaryKeepingConnection() {
        if (stopFlowPolicy.isStopFlowInProgress()) {
            dumpUiState("endWorkoutCompleteSessionToSummaryKeepingConnectionIgnoredAlreadyStopping")
            return
        }
        recordDiagnostics(
            category = "session",
            event = "stop_requested",
            context = mapOf("transportClosePolicy" to StopFlowTransportClosePolicy.KEEP_CONNECTED.wireName),
        )
        beginStopFlow(
            dumpReason = "endWorkoutCompleteSessionToSummaryKeepingConnection",
            mockDumpReason = "endWorkoutCompleteSessionToSummaryKeepingConnectionMock",
            transportClosePolicy = StopFlowTransportClosePolicy.KEEP_CONNECTED,
        )
    }

    /**
     * Dismisses the CONNECTING-timeout prompt and re-arms the timeout watchdog.
     */
    fun onConnectingTimeoutKeepWaiting() {
        if (uiState.screen.value != AppScreen.CONNECTING) return
        uiState.connectingTimeoutMessage.value = null
        restartConnectFlowTimeout()
        recordDiagnostics(
            category = "session",
            event = "connect_flow_timeout_keep_waiting",
        )
        dumpUiState("onConnectingTimeoutKeepWaiting")
    }

    /**
     * Restarts trainer connect attempt after a CONNECTING-timeout prompt.
     */
    fun onConnectingTimeoutRetry() {
        if (uiState.screen.value != AppScreen.CONNECTING) return
        uiState.connectingTimeoutMessage.value = null
        recordDiagnostics(
            category = "session",
            event = "connect_flow_timeout_retry",
        )

        if (isMockTrainerModeEnabled()) {
            cancelMockConnectTransition()
            scheduleMockConnectTransition()
            restartConnectFlowTimeout()
            dumpUiState("onConnectingTimeoutRetry(mock)")
            return
        }

        bleClient?.close()
        bleClient = createFtmsBleClient()
        ftmsController = createFtmsController()
        if (connectBleClients()) {
            restartConnectFlowTimeout()
            dumpUiState("onConnectingTimeoutRetry(ble)")
            return
        }

        val failure = AppFailureFactory.sessionConnectFlowTimeout()
        rollbackToMenuWithConnectionIssue(
            message = toUserMessage(failure),
            reason = "connect_timeout_retry_connect_failed",
            suggestTrainerSearch = true,
            suggestOpenSettings = false,
        )
    }

    /**
     * Aborts CONNECTING flow and returns to MENU from timeout prompt.
     */
    fun onConnectingTimeoutBackToMenu() {
        if (uiState.screen.value != AppScreen.CONNECTING) return
        uiState.connectingTimeoutMessage.value = null
        val failure = AppFailureFactory.sessionConnectFlowTimeout()
        rollbackToMenuWithConnectionIssue(
            message = toUserMessage(failure),
            reason = "connect_timeout_back_to_menu",
            suggestTrainerSearch = true,
            suggestOpenSettings = false,
        )
    }

    /**
     * Returns the latest session export snapshot after session stop.
     */
    fun getSessionExportSnapshot(): SessionExportSnapshot? = sessionManager.buildExportSnapshot()

    /**
     * Re-evaluates imported-HR runtime safety whenever the external HR channel changes.
     *
     * FTMS indoor-bike telemetry already drives the same runtime path, but external
     * HR peripherals can disconnect without producing a fresh bike packet. This hook
     * closes that gap so signal-loss fallback still executes while cadence remains
     * positive on the trainer link.
     */
    internal fun onExternalHeartRateTelemetryUpdated() {
        processImportedHrRuntimeTelemetry(source = "external_heart_rate")
    }

    /**
     * Releases owned resources during activity teardown.
     */
    fun stopAndClose() {
        cancelConnectFlowTimeout()
        stopFlowPolicy.resetToIdle()
        cancelMockConnectTransition()
        cancelExternalTrainerPreparationTimeout()
        cancelPostWorkoutReleaseRamp()
        stopMockTrainerEngine()
        externalTrainerControlRequestPending = false
        externalTrainerControlRequestOutcome = null
        externalTrainerPreparationState = ExternalTrainerPreparationState.IDLE
        trainerPreparationOwner = TrainerPreparationOwner.NONE
        preparedTrainerDeviceMac = null
        cancelPendingTelemetryOnlyReconnect()
        telemetryOnlyStartReconnectInProgress = false
        telemetryOnlyStartShouldBypassControlRequest = false
        clearAppControlledTargetTracking()
        pendingExternalTrainerPreparationAfterPermission = false
        uiState.connectingTimeoutMessage.value = null
        _pendingPermissionRequestId = 0L
        endSessionScope(reason = "orchestrator_stop_and_close")
        dumpUiState("onDestroy")
        workoutRunner?.stop()
        bleClient?.close()
        indoorBikeParsingDispatcher.close()
        closeHeartRate()
        allowScreenOff()
    }

    /**
     * Parses FTMS telemetry on a background executor and applies only the latest
     * parse result to UI/session state.
     *
     * This keeps heavy byte parsing away from the main thread while preserving
     * reconnect safety via generation and request-id stale guards.
     */
    private fun scheduleIndoorBikeParsing(payload: ByteArray, generation: Int) {
        val snapshot = payload.copyOf()
        val requestId = synchronized(indoorBikeParseLock) {
            latestIndoorBikeParseRequestId += 1
            latestIndoorBikeParseRequestId
        }

        val scheduled = indoorBikeParsingDispatcher.dispatch {
            var parseFailure: IndoorBikeParseFailure? = null
            val parsedData = io.github.ewoc2026.ewoc.ftms.parseIndoorBikeData(snapshot) { failure ->
                parseFailure = failure
            }
            mainThreadHandler.post {
                if (generation != activeFtmsClientGeneration) return@post
                val isLatest = synchronized(indoorBikeParseLock) {
                    requestId == latestIndoorBikeParseRequestId
                }
                if (!isLatest) return@post
                parseFailure?.let(::recordIndoorBikeParseFailureDiagnostics)
                uiState.bikeData.value = parsedData
                uiState.bikeDataLastUpdatedAtEpochMs = System.currentTimeMillis()
                sessionManager.updateBikeData(parsedData)
                processImportedHrRuntimeTelemetry(source = "indoor_bike")
                applyCadenceDrivenRunnerControl(parsedData.instantaneousCadenceRpm)
            }
        }
        if (!scheduled) {
            Log.w("FTMS", "Indoor bike parsing skipped after orchestrator shutdown")
        }
    }

    private fun importWorkoutFromUri(uri: Uri) {
        val sourceName = resolveDisplayName(uri) ?: "selected_workout"
        var readFailureDetails: String? = null
        val content = try {
            openWorkoutInputStream(uri)?.bufferedReader(Charsets.UTF_8).use { reader ->
                reader?.readText()
            }
        } catch (e: Exception) {
            Log.w("WORKOUT", "Failed reading selected workout file: ${e.message}")
            readFailureDetails = e.message?.takeIf { it.isNotBlank() }
            null
        }

        if (content.isNullOrBlank()) {
            val failure = AppFailureFactory.workoutImportReadFailed(readFailureDetails)
            uiState.selectedWorkout.value = null
            uiState.selectedImportedWorkout.value = null
            workoutRunner = null
            uiState.selectedWorkoutFileName.value = sourceName
            uiState.selectedWorkoutStepCount.value = null
            uiState.selectedWorkoutPlannedTss.value = null
            uiState.selectedWorkoutImportError.value = toUserMessage(failure)
            uiState.workoutExecutionModeMessage.value = null
            uiState.workoutExecutionModeIsError.value = false
            workoutReadinessEvaluator.clearLastFailureSignal()
            uiState.workoutReady.value = false
            dumpUiState("workoutImportReadFailed(name=$sourceName)")
            return
        }

        when (val result = workoutImportService.importFromText(
            sourceName = sourceName,
            content = content,
            context = currentEwoCompileContext(),
        )) {
            is WorkoutImportResult.Success -> {
                val workoutFile = result.workoutFile
                if (workoutFile != null) {
                    uiState.selectedSessionSetupMode.value = SessionSetupMode.FILE
                    uiState.selectedWorkout.value = workoutFile
                    uiState.selectedImportedWorkout.value = null
                    workoutRunner = null
                    uiState.selectedWorkoutFileName.value = sourceName
                    uiState.selectedWorkoutImportError.value = null
                    recalculateSelectedWorkoutDerivedMetrics(workoutFile)
                    Log.d(
                        "WORKOUT",
                        "Selected workout imported name=$sourceName executionSteps=${uiState.selectedWorkoutStepCount.value} rawSteps=${workoutFile.steps.size} plannedTss=${uiState.selectedWorkoutPlannedTss.value}"
                    )
                    dumpUiState("workoutImportSuccess(name=$sourceName)")
                } else {
                    uiState.selectedSessionSetupMode.value = SessionSetupMode.FILE
                    uiState.selectedWorkout.value = null
                    uiState.selectedImportedWorkout.value = requireNotNull(result.ergoWorkout)
                    workoutRunner = null
                    uiState.selectedWorkoutFileName.value = sourceName
                    uiState.selectedWorkoutImportError.value = null
                    recalculateSelectedImportedWorkoutDerivedMetrics(
                        requireNotNull(uiState.selectedImportedWorkout.value),
                    )
                    Log.d(
                        "WORKOUT",
                        "Selected ergo workout imported name=$sourceName executionSteps=${uiState.selectedWorkoutStepCount.value} compiledSteps=${uiState.selectedImportedWorkout.value?.steps?.size} plannedTss=${uiState.selectedWorkoutPlannedTss.value} ready=${uiState.workoutReady.value}",
                    )
                    dumpUiState("workoutImportSuccessErgo(name=$sourceName)")
                }
            }

            is WorkoutImportResult.Failure -> {
                val failure = AppFailureFactory.workoutImportFailure(result.error)
                uiState.selectedSessionSetupMode.value = SessionSetupMode.FILE
                uiState.selectedWorkout.value = null
                uiState.selectedImportedWorkout.value = null
                workoutRunner = null
                uiState.selectedWorkoutFileName.value = sourceName
                uiState.selectedWorkoutStepCount.value = null
                uiState.selectedWorkoutPlannedTss.value = null
                uiState.selectedWorkoutImportError.value = toUserMessage(failure)
                uiState.workoutExecutionModeMessage.value = null
                uiState.workoutExecutionModeIsError.value = false
                workoutReadinessEvaluator.clearLastFailureSignal()
                uiState.workoutReady.value = false
                Log.w(
                    "WORKOUT",
                    "Selected workout import failed code=${result.error.code} format=${result.error.detectedFormat}"
                )
                dumpUiState("workoutImportFailure(name=$sourceName,code=${result.error.code})")
            }
        }
    }

    private fun applyBundledWorkoutFailure(
        failure: AppFailure.WorkoutImport,
        sourceName: String,
    ) {
        uiState.selectedSessionSetupMode.value = SessionSetupMode.FILE
        uiState.selectedWorkout.value = null
        uiState.selectedImportedWorkout.value = null
        workoutRunner = null
        uiState.selectedWorkoutFileName.value = sourceName
        uiState.selectedWorkoutStepCount.value = null
        uiState.selectedWorkoutPlannedTss.value = null
        uiState.selectedWorkoutImportError.value = toUserMessage(failure)
        uiState.workoutExecutionModeMessage.value = null
        uiState.workoutExecutionModeIsError.value = false
        workoutReadinessEvaluator.clearLastFailureSignal()
        uiState.workoutReady.value = false
        dumpUiState("bundledWorkoutImportFailed(name=$sourceName)")
    }

    private fun toUserMessage(failure: AppFailure): String {
        return AppFailureUserMessageMapper.toUserMessage(
            failure = failure,
            strings = failureStrings,
        )
    }

    private fun recalculateSelectedWorkoutDerivedMetrics(workout: WorkoutFile) {
        val ftpWatts = currentFtpWatts()
        uiState.selectedWorkoutStepCount.value = WorkoutExecutionStepCounter.count(
            workout = workout,
            ftpWatts = ftpWatts,
        )
        uiState.selectedWorkoutPlannedTss.value = WorkoutPlannedTssCalculator.calculate(
            workout = workout,
            ftpWatts = ftpWatts,
        )
        uiState.selectedWorkoutTotalDurationSec.value = WorkoutTotalDurationCalculator.calculate(
            workout = workout,
            ftpWatts = ftpWatts,
        )
        uiState.workoutReady.value = workoutReadinessEvaluator.evaluateWorkoutExecutionEligibility(workout)
    }

    private fun recalculateSelectedImportedWorkoutDerivedMetrics(workout: ImportedErgoWorkout) {
        val ftpWatts = currentFtpWatts()
        when (val mapped = ImportedErgoWorkoutExecutionMapper.map(workout)) {
            is MappingResult.Success -> {
                uiState.selectedWorkoutStepCount.value = mapped.workout.segments.size
                uiState.selectedWorkoutPlannedTss.value = WorkoutPlannedTssCalculator.calculate(
                    workout = mapped.workout,
                    ftpWatts = ftpWatts,
                )
                uiState.selectedWorkoutTotalDurationSec.value = mapped.workout.totalDurationSec
                uiState.workoutReady.value = workoutReadinessEvaluator.evaluateImportedWorkoutExecutionEligibility(
                    workout = workout,
                    source = "eligibility_check_ergo",
                )
            }

            is MappingResult.Failure -> {
                val failureContext = workoutReadinessEvaluator.importedWorkoutExecutionFailureContext(workout, mapped)
                uiState.selectedWorkoutStepCount.value = workout.steps.size
                uiState.selectedWorkoutPlannedTss.value = null
                uiState.selectedWorkoutTotalDurationSec.value = null
                uiState.workoutReady.value = workoutReadinessEvaluator.evaluateMappedWorkoutExecutionEligibility(
                    mapped = mapped,
                    source = "eligibility_check_ergo",
                    allowLegacyFallback = false,
                    failureDetail = failureContext.detail,
                    diagnosticsContext = failureContext.diagnosticsContext,
                )
            }
        }
    }

    private fun resolveDisplayName(uri: Uri): String? {
        if (uri.scheme == ContentResolver.SCHEME_FILE) {
            return uri.path?.let { path -> File(path).name.takeIf { it.isNotBlank() } }
        }
        return context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index < 0) null else cursor.getString(index)
            }
    }

    private fun openWorkoutInputStream(uri: Uri): java.io.InputStream? {
        return if (uri.scheme == ContentResolver.SCHEME_FILE) {
            val path = uri.path ?: return null
            FileInputStream(File(path))
        } else {
            context.contentResolver.openInputStream(uri)
        }
    }

    private fun createFtmsBleClient(): FtmsBleClient {
        val generation = ++ftmsClientGeneration
        activeFtmsClientGeneration = generation

        return FtmsBleClient(
            context = context,
            onIndoorBikeData = { bytes ->
                if (generation != activeFtmsClientGeneration) {
                    Log.d("FTMS", "Ignoring stale onIndoorBikeData callback (generation=$generation)")
                    return@FtmsBleClient
                }
                scheduleIndoorBikeParsing(
                    payload = bytes,
                    generation = generation,
                )
            },
            onReady = { controlPointReady, linkGeneration ->
                if (generation != activeFtmsClientGeneration) {
                    Log.d("FTMS", "Ignoring stale onReady callback (generation=$generation)")
                    return@FtmsBleClient
                }
                activeFtmsControlLinkGeneration = linkGeneration
                ftmsController.onControlLinkGenerationChanged(linkGeneration)
                uiState.ftmsReady.value = controlPointReady
                ftmsController.setTransportReady(controlPointReady)
                if (!controlPointReady) {
                    sessionManager.pauseSessionActivity()
                    ftmsController.onDisconnected()
                }
                if (controlPointReady && externalTrainerPreparationState == ExternalTrainerPreparationState.PENDING) {
                    externalTrainerPreparationState = ExternalTrainerPreparationState.READY
                    cancelExternalTrainerPreparationTimeout()
                }
                val shouldRequestReconnectControl =
                    uiState.screen.value == AppScreen.SESSION &&
                        (postWorkoutTelemetryReconnectInProgress || postWorkoutTelemetryAwaitingReconnectControl)
                val shouldBypassControlRequestForTelemetryOnlyStart =
                    controlPointReady &&
                        uiState.screen.value == AppScreen.CONNECTING &&
                        telemetryOnlyStartShouldBypassControlRequest
                if (shouldBypassControlRequestForTelemetryOnlyStart) {
                    telemetryOnlyStartShouldBypassControlRequest = false
                    recordDiagnostics(
                        category = "session",
                        event = "telemetry_only_clean_reconnect_session_started_without_control_request",
                    )
                    logTestMarker(event = "telemetry_only_clean_reconnect_session_started_without_control_request")
                    completeConnectingToSession(
                        dumpReason = "transitionFromConnectingToTelemetryOnlySessionWithoutControlRequest",
                    )
                } else
                if (
                    controlPointReady &&
                    (
                        uiState.screen.value == AppScreen.CONNECTING ||
                            shouldRequestReconnectControl ||
                            (
                                uiState.screen.value == AppScreen.SESSION &&
                                    !uiState.ftmsControlGranted.value &&
                                    shouldRequestSessionControlOnReady()
                                )
                    )
                ) {
                    // Post-workout telemetry reconnect intentionally reclaims control even though
                    // the UI has already switched to telemetry-only; that matches the clean
                    // session-start path that keeps this trainer's local controls functional.
                    if (shouldRequestReconnectControl) {
                        postWorkoutTelemetryReconnectInProgress = false
                        postWorkoutTelemetryAwaitingReconnectControl = true
                    }
                    ftmsController.requestControl()
                }
                syncSessionActivityState()
                dumpUiState("bleOnReady")
            },
            onControlPointResponse = { requestOpcode, resultCode, linkGeneration ->
                if (generation != activeFtmsClientGeneration) {
                    Log.d(
                        "FTMS",
                        "Ignoring stale onControlPointResponse callback (generation=$generation)"
                    )
                    return@FtmsBleClient
                }
                handleControlPointResponseFromBle(
                    requestOpcode = requestOpcode,
                    resultCode = resultCode,
                    linkGeneration = linkGeneration,
                )
            },
            onDisconnected = { handleBleDisconnected(generation) },
            onControlOwnershipChanged = { controlGranted ->
                if (generation != activeFtmsClientGeneration) {
                    Log.d(
                        "FTMS",
                        "Ignoring stale onControlOwnershipChanged callback (generation=$generation)"
                    )
                    return@FtmsBleClient
                }
                uiState.ftmsControlGranted.value = controlGranted
                freeRideControlCoordinator.onControlOwnershipChanged(
                    controlGranted = controlGranted,
                    ftmsReady = uiState.ftmsReady.value,
                )
                processImportedHrRuntimeTelemetry(source = "control_ownership_changed")
                syncSessionActivityState()
                dumpUiState("bleOnControlOwnershipChanged(granted=$controlGranted)")
            }
        )
    }

    /**
     * Invalidates active FTMS callback generation before closing a client without immediate replacement.
     */
    private fun invalidateActiveFtmsCallbackGeneration() {
        activeFtmsClientGeneration = ++ftmsClientGeneration
    }

    private fun handleBleDisconnected(generation: Int) {
        if (generation != activeFtmsClientGeneration) {
            Log.d("FTMS", "Ignoring stale onDisconnected callback (generation=$generation)")
            return
        }
        cancelPostWorkoutReleaseRamp()
        if (explicitTrainerCloseInProgress) {
            explicitTrainerCloseInProgress = false
            explicitTrainerReconnectAllowedAtElapsedMs =
                elapsedRealtimeMsProvider() + explicitTrainerReconnectSettleDelayMs
            logTestMarker(
                event = "post_workout_exit_prep_disconnect_observed",
                context = mapOf(
                    "reconnectDelayMs" to explicitTrainerReconnectSettleDelayMs.toString(),
                ),
            )
            recordTrainerReleaseRuntime(
                event = "explicit_close_disconnect_observed",
                extraContext = mapOf(
                    "reconnectDelayMs" to explicitTrainerReconnectSettleDelayMs.toString(),
                ),
            )
            ftmsController.setTransportReady(false)
            ftmsController.onDisconnected()
            clearAppControlledTargetTracking()
            uiState.ftmsReady.value = false
            uiState.ftmsControlGranted.value = false
            bleClient = null
            invalidateActiveFtmsCallbackGeneration()
            syncSessionActivityState()
            schedulePendingTrainerPreparationAfterExplicitClose()
            dumpUiState("bleOnDisconnected(explicitTrainerClose)")
            return
        }
        if (postWorkoutFreerideReconnectInProgress) {
            postWorkoutFreerideReconnectInProgress = false
            ftmsController.setTransportReady(false)
            ftmsController.onDisconnected()
            uiState.ftmsReady.value = false
            uiState.ftmsControlGranted.value = false
            bleClient = null
            invalidateActiveFtmsCallbackGeneration()
            if (currentFtmsDeviceMac() == null) {
                syncSessionActivityState()
                dumpUiState("bleOnDisconnected(postWorkoutFreerideNoTrainerMac)")
                return
            }
            bleClient = createFtmsBleClient()
            ftmsController = createFtmsController()
            if (!connectBleClients()) {
                syncSessionActivityState()
                dumpUiState("bleOnDisconnected(postWorkoutFreerideReconnectFailed)")
                return
            }
            dumpUiState("bleOnDisconnected(postWorkoutFreerideReconnectStarted)")
            return
        }
        if (postWorkoutFreerideDisconnectOnlyInProgress) {
            postWorkoutFreerideDisconnectOnlyInProgress = false
            ftmsController.setTransportReady(false)
            ftmsController.onDisconnected()
            clearAppControlledTargetTracking()
            uiState.ftmsReady.value = false
            uiState.ftmsControlGranted.value = false
            bleClient = null
            invalidateActiveFtmsCallbackGeneration()
            uiState.bikeData.value = null
            uiState.bikeDataLastUpdatedAtEpochMs = null
            syncSessionActivityState()
            dumpUiState("bleOnDisconnected(postWorkoutFreerideDisconnectOnly)")
            return
        }
        if (debugTransportDisconnectInProgress) {
            debugTransportDisconnectInProgress = false
            ftmsController.setTransportReady(false)
            ftmsController.onDisconnected()
            clearAppControlledTargetTracking()
            resetFtmsUiState(clearReady = true)
            stopWorkout()
            workoutRunner = null
            sessionManager.stopSession()
            trainerPreparationOwner = TrainerPreparationOwner.NONE
            preparedTrainerDeviceMac = null
            externalTrainerPreparationState = ExternalTrainerPreparationState.IDLE
            uiState.connectingTimeoutMessage.value = null
            uiState.connectionIssueMessage.value = null
            uiState.suggestTrainerSearchAfterConnectionIssue.value = false
            uiState.suggestOpenSettingsAfterConnectionIssue.value = false
            uiState.bikeData.value = null
            uiState.bikeDataLastUpdatedAtEpochMs = null
            allowScreenOff()
            uiState.screen.value = AppScreen.MENU
            bleClient = null
            invalidateActiveFtmsCallbackGeneration()
            endSessionScope(reason = "debug_transport_disconnect")
            dumpUiState("bleOnDisconnected(debugTransportDisconnect)")
            return
        }
        if (postWorkoutTelemetryReconnectInProgress) {
            ftmsController.setTransportReady(false)
            ftmsController.onDisconnected()
            clearAppControlledTargetTracking()
            uiState.ftmsReady.value = false
            uiState.ftmsControlGranted.value = false
            bleClient = null
            invalidateActiveFtmsCallbackGeneration()
            if (currentFtmsDeviceMac() == null) {
                postWorkoutTelemetryReconnectInProgress = false
                postWorkoutFreshTelemetryStartPending = false
                syncSessionActivityState()
                dumpUiState("bleOnDisconnected(postWorkoutTelemetryNoTrainerMac)")
                return
            }
            schedulePostWorkoutTelemetryReconnectAfterDisconnect()
            dumpUiState("bleOnDisconnected(postWorkoutTelemetryReconnectScheduled)")
            return
        }
        if (telemetryOnlyStartReconnectInProgress) {
            ftmsController.setTransportReady(false)
            ftmsController.onDisconnected()
            clearAppControlledTargetTracking()
            uiState.ftmsReady.value = false
            uiState.ftmsControlGranted.value = false
            bleClient = null
            invalidateActiveFtmsCallbackGeneration()
            if (currentFtmsDeviceMac() == null) {
                telemetryOnlyStartReconnectInProgress = false
                telemetryOnlyStartShouldBypassControlRequest = false
                uiState.screen.value = AppScreen.MENU
                endSessionScope(reason = "telemetry_only_reconnect_missing_trainer_mac")
                dumpUiState("bleOnDisconnected(telemetryOnlyReconnectMissingTrainerMac)")
                return
            }
            scheduleTelemetryOnlyReconnectAfterDisconnect()
            dumpUiState("bleOnDisconnected(telemetryOnlyReconnectScheduled)")
            return
        }
        if (externalTrainerControlRequestPending) {
            externalTrainerControlRequestPending = false
            externalTrainerControlRequestOutcome = ExternalTrainerControlRequestOutcome.FAILED
        }
        cancelConnectFlowTimeout()
        cancelExternalTrainerPreparationTimeout()
        cancelMockConnectTransition()
        stopMockTrainerEngine()
        val stopFlowInProgress = stopFlowPolicy.isStopFlowInProgress()
        val wasConnecting = uiState.screen.value == AppScreen.CONNECTING
        val wasSessionFlowActive =
            uiState.screen.value == AppScreen.CONNECTING || uiState.screen.value == AppScreen.SESSION
        ftmsController.setTransportReady(false)
        ftmsController.onDisconnected()
        clearAppControlledTargetTracking()
        resetFtmsUiState(clearReady = true)
        stopWorkout()
        workoutRunner = null
        if (stopFlowInProgress) {
            stopFlowTerminalCallbacks.onBleDisconnectedDuringStopFlow()
        } else if (wasSessionFlowActive) {
            stopFlowPolicy.resetToIdle()
            sessionManager.stopSession()
            uiState.connectingTimeoutMessage.value = null
            if (wasConnecting) {
                uiState.connectionIssueMessage.value = toUserMessage(
                    AppFailureFactory.sessionConnectFlowTimeout(),
                )
                uiState.suggestTrainerSearchAfterConnectionIssue.value = true
                uiState.suggestOpenSettingsAfterConnectionIssue.value = false
            }
            allowScreenOff()
            uiState.screen.value = AppScreen.MENU
            endSessionScope(reason = "ble_disconnected")
        } else if (trainerPreparationOwner != TrainerPreparationOwner.NONE) {
            externalTrainerPreparationState = ExternalTrainerPreparationState.FAILED
            trainerPreparationOwner = TrainerPreparationOwner.NONE
            preparedTrainerDeviceMac = null
            endSessionScope(reason = "external_ble_disconnected")
        }
        Log.w("FTMS", "UI state: disconnected -> READY=false CONTROL=false sessionStopped=true")
        recordDiagnostics(
            category = "session",
            event = "ble_disconnected",
            context = mapOf(
                "wasConnecting" to wasConnecting.toString(),
                "stopFlowInProgress" to stopFlowInProgress.toString(),
            ),
        )
        dumpUiState("bleOnDisconnected")
    }

    private fun remainingExplicitTrainerReconnectDelayMs(
        nowElapsedMs: Long = elapsedRealtimeMsProvider(),
    ): Long {
        if (explicitTrainerCloseInProgress) {
            return explicitTrainerReconnectSettleDelayMs
        }
        return (explicitTrainerReconnectAllowedAtElapsedMs - nowElapsedMs).coerceAtLeast(0L)
    }

    private fun scheduleTrainerPreparationAfterExplicitClose(
        owner: TrainerPreparationOwner,
        delayMs: Long,
    ) {
        pendingTrainerPreparationOwnerAfterClose = owner
        pendingTrainerPreparationRunnable?.let(mainThreadHandler::removeCallbacks)
        val runnable = Runnable {
            pendingTrainerPreparationRunnable = null
            if (explicitTrainerCloseInProgress) {
                schedulePendingTrainerPreparationAfterExplicitClose()
                return@Runnable
            }
            val pendingOwner = pendingTrainerPreparationOwnerAfterClose ?: return@Runnable
            pendingTrainerPreparationOwnerAfterClose = null
            prepareTrainerForOwner(owner = pendingOwner)
        }
        pendingTrainerPreparationRunnable = runnable
        mainThreadHandler.postDelayed(runnable, delayMs)
    }

    private fun schedulePendingTrainerPreparationAfterExplicitClose() {
        val pendingOwner = pendingTrainerPreparationOwnerAfterClose ?: return
        scheduleTrainerPreparationAfterExplicitClose(
            owner = pendingOwner,
            delayMs = remainingExplicitTrainerReconnectDelayMs(),
        )
    }

    private fun cancelPendingTrainerPreparationAfterExplicitClose() {
        pendingTrainerPreparationRunnable?.let(mainThreadHandler::removeCallbacks)
        pendingTrainerPreparationRunnable = null
        pendingTrainerPreparationOwnerAfterClose = null
    }

    /**
     * Starts a trainer-settle window after summary-bound stop-flow closes the FTMS transport.
     *
     * The continuation path must not reconnect before the trainer has observed the disconnect
     * and completed its own workout-exit transition.
     */
    private fun closeBleTransportAfterStopFlow() {
        stopFlowTransportClosePolicy = StopFlowTransportClosePolicy.CLOSE_AFTER_COMPLETION
        val client = bleClient
        if (client != null) {
            explicitTrainerCloseInProgress = true
            if (client.close()) {
                return
            }
        }
        completeExplicitTrainerCloseWithoutDisconnectCallback()
    }

    /**
     * Finishes explicit-close bookkeeping when no BLE disconnect callback can arrive.
     *
     * This happens in JVM tests, mock mode, and real edge cases where the transport is already
     * gone by the time the stop-flow close step runs. Real trainers still keep the bounded settle
     * delay because a missing callback does not prove the trainer has already finished its own
     * workout-exit dwell.
     */
    private fun completeExplicitTrainerCloseWithoutDisconnectCallback() {
        val reconnectDelayMs = if (isMockTrainerModeEnabled()) {
            0L
        } else {
            explicitTrainerReconnectSettleDelayMs
        }
        explicitTrainerCloseInProgress = false
        explicitTrainerReconnectAllowedAtElapsedMs =
            elapsedRealtimeMsProvider() + reconnectDelayMs
        logTestMarker(
            event = "post_workout_exit_prep_disconnect_unavailable",
            context = mapOf(
                "reconnectDelayMs" to reconnectDelayMs.toString(),
                "mockTrainer" to isMockTrainerModeEnabled().toString(),
            ),
        )
        recordTrainerReleaseRuntime(
            event = "explicit_close_disconnect_unavailable",
            extraContext = mapOf(
                "reconnectDelayMs" to reconnectDelayMs.toString(),
                "mockTrainer" to isMockTrainerModeEnabled().toString(),
            ),
        )
        ftmsController.setTransportReady(false)
        ftmsController.onDisconnected()
        clearAppControlledTargetTracking()
        uiState.ftmsReady.value = false
        uiState.ftmsControlGranted.value = false
        bleClient = null
        invalidateActiveFtmsCallbackGeneration()
        syncSessionActivityState()
        schedulePendingTrainerPreparationAfterExplicitClose()
        dumpUiState("closeBleTransportAfterStopFlowNoClient")
    }

    /**
     * Applies session-side effects only for control-point responses that matched
     * the active in-flight FTMS command.
     */
    private fun handleControlPointResponseFromBle(
        requestOpcode: Int,
        resultCode: Int,
        linkGeneration: Int = activeFtmsControlLinkGeneration,
    ) {
        val wasExpectedResponse = ftmsController.onControlPointResponse(
            requestOpcode = requestOpcode,
            resultCode = resultCode,
            responseLinkGeneration = linkGeneration,
        )
        Log.d("FTMS", "UI state: cp response opcode=$requestOpcode result=$resultCode")
        if (!wasExpectedResponse) {
            dumpUiState("bleOnControlPointResponseIgnoredUnexpected(op=$requestOpcode,result=$resultCode)")
            return
        }

        // Session enters active mode only after Request Control is acknowledged.
        if (requestOpcode == requestControlOpcode) {
            if (postWorkoutCompletionExitAwaitingControl) {
                postWorkoutCompletionExitAwaitingControl = false
                recordTrainerReleaseRuntime(
                    event = "completion_exit_control_response",
                    extraContext = mapOf("resultCode" to formatOpcode(resultCode)),
                )
                logTestMarker(
                    event = "post_workout_exit_prep_control_response",
                    context = mapOf("resultCode" to formatOpcode(resultCode)),
                )
                val releaseDecision = evaluatePostWorkoutReleaseRamp(
                    stage = "completion_prep_control_response",
                    controlGrantedOverride = resultCode == controlResponseSuccessCode,
                )
                if (resultCode == controlResponseSuccessCode) {
                    continuePostWorkoutReleaseTeardown(
                        owner = PostWorkoutReleaseOwner.COMPLETION_EXIT_PREP,
                        decision = releaseDecision,
                    )
                } else {
                    beginPostWorkoutCompletionExplicitClose()
                }
                dumpUiState("bleOnControlPointResponsePostWorkoutCompletionExitControl(result=$resultCode)")
                return
            }
            if (postWorkoutTelemetryTransitionAwaitingControl) {
                postWorkoutTelemetryTransitionAwaitingControl = false
                recordTrainerReleaseRuntime(
                    event = "telemetry_release_control_response",
                    extraContext = mapOf("resultCode" to formatOpcode(resultCode)),
                )
                val releaseDecision = evaluatePostWorkoutReleaseRamp(
                    stage = "continue_ride_control_response",
                    controlGrantedOverride = resultCode == controlResponseSuccessCode,
                )
                if (resultCode == controlResponseSuccessCode) {
                    continuePostWorkoutReleaseTeardown(
                        owner = PostWorkoutReleaseOwner.TELEMETRY_TRANSITION,
                        decision = releaseDecision,
                    )
                } else {
                    completePostWorkoutTelemetryTransition(
                        preserveControlGranted = false,
                        dumpReason = "postWorkoutTelemetryRequestControlRejected(result=$resultCode)",
                    )
                }
                dumpUiState("bleOnControlPointResponsePostWorkoutTelemetryRequestControl(result=$resultCode)")
                return
            }
            if (postWorkoutTelemetryAwaitingReconnectControl) {
                postWorkoutTelemetryAwaitingReconnectControl = false
                recordTrainerReleaseRuntime(
                    event = "telemetry_reconnect_control_response",
                    extraContext = mapOf("resultCode" to formatOpcode(resultCode)),
                )
                completePostWorkoutTelemetryTransition(
                    preserveControlGranted = resultCode == controlResponseSuccessCode,
                    dumpReason = "postWorkoutTelemetryReconnectControl(result=$resultCode)",
                )
                dumpUiState("bleOnControlPointResponsePostWorkoutTelemetryReconnectControl(result=$resultCode)")
                return
            }
            recordExternalTrainerControlRequestOutcome(
                outcome = if (resultCode == controlResponseSuccessCode) {
                    ExternalTrainerControlRequestOutcome.GRANTED
                } else {
                    ExternalTrainerControlRequestOutcome.FAILED
                },
            )
            if (resultCode == controlResponseSuccessCode) {
                transitionFromConnectingToSessionAfterControlGranted()
                freeRideControlCoordinator.onRequestControlSucceeded(
                    ftmsReady = uiState.ftmsReady.value,
                    controlGranted = uiState.ftmsControlGranted.value,
                )
            } else {
                val failure = AppFailureFactory.sessionRequestControlRejected(resultCode)
                requestControlFailureAdapter.onRequestControlFailure(
                    message = toUserMessage(failure),
                    reason = failure.reason.stableCode,
                )
            }
        }
        if (requestOpcode == setTargetPowerOpcode &&
            resultCode == controlResponseSuccessCode
        ) {
            confirmPendingAppControlledTargetPower()
        }

        // Free ride release uses RESET and continues telemetry/session flow.
        if (requestOpcode == resetOpcode && resultCode == controlResponseSuccessCode) {
            freeRideControlCoordinator.onFreeRideResetSucceeded(
                ftmsReady = uiState.ftmsReady.value,
                controlGranted = uiState.ftmsControlGranted.value,
            )
        }
        if (requestOpcode == stopOpcode &&
            resultCode != controlResponseSuccessCode &&
            postWorkoutCompletionExitSoftStopInProgress
        ) {
            logTestMarker(
                event = "post_workout_exit_prep_soft_stop_rejected",
                context = mapOf("resultCode" to formatOpcode(resultCode)),
            )
            beginPostWorkoutCompletionExplicitClose()
        }
        if (requestOpcode == stopOpcode &&
            resultCode != controlResponseSuccessCode &&
            postWorkoutTelemetryTransitionSoftStopInProgress
        ) {
            postWorkoutTelemetryTransitionSoftStopInProgress = false
            startPostWorkoutTelemetryReconnect()
        }
        dumpUiState("bleOnControlPointResponse(op=$requestOpcode,result=$resultCode)")
    }

    /**
     * Starts summary-bound stop flow with a real FTMS STOP before transport teardown.
     */
    private fun sendStopForStopFlow() {
        if (isMockTrainerModeEnabled()) {
            resetFtmsUiState(clearReady = true)
            dumpUiState("sendStopForStopFlowMock")
            return
        }
        ftmsController.stopWorkout()
        resetFtmsUiState(clearReady = false)
        dumpUiState("sendStopForStopFlow")
    }

    /**
     * Finalizes stop-flow navigation exactly once for all ack/disconnect/timeout paths.
     */
    private fun completeStopFlowToSummary(reason: String): Boolean {
        return stopFlowCompletionPolicy.completeToSummaryIfInProgress(reason = reason)
    }

    /**
     * Reports whether a summary-originated Continue ride restart can safely reconnect yet.
     *
     * This stays false until the prior FTMS link has fully disconnected and the trainer-side
     * settle window has elapsed, so one-button continuation does not race the trainer's
     * workout-exit mode change.
     */
    internal fun continueRideRestartWindowOpen(): Boolean {
        return continueRideRestartWindowOpen(
            reconnectDelayRemainingMs = remainingExplicitTrainerReconnectDelayMs(),
        )
    }

    /**
     * Reports whether the hidden completion-time exit prep has progressed far enough for SUMMARY.
     *
     * `Continue ride` still needs the stricter disconnect-and-settle gate before reconnecting.
     * `SUMMARY` only needs the completion-exit prep to have passed its ramp/request-control/STOP
     * stages so the visible finish action no longer waits on the continuation-specific dwell.
     */
    internal fun preparedPostWorkoutSummaryWindowOpen(): Boolean {
        return preparedPostWorkoutSummaryWindowOpen(
            reconnectDelayRemainingMs = remainingExplicitTrainerReconnectDelayMs(),
        )
    }

    private fun enterConnectingState() {
        uiState.connectingTimeoutMessage.value = null
        connectionFlow.enterConnectingState()
    }

    private fun scheduleMockConnectTransition() {
        connectionFlow.scheduleMockConnectTransition()
    }

    private fun cancelMockConnectTransition() {
        connectionFlow.cancelMockConnectTransition()
    }

    private fun startMockTrainerEngine() {
        stopMockTrainerEngine()
        val engine = mockTrainerEngineFactory(mainThreadHandler)
        val debugScenario = consumeMockTrainerDebugScenario()
        engine.armDebugScenario(debugScenario)
        mockTrainerEngine = engine
        debugScenario?.let { scenario ->
            recordDiagnostics(
                category = "session",
                event = "mock_trainer_debug_scenario_applied",
                context = mapOf("scenario" to scenario.wireName),
            )
        }
        engine.start { mockBikeData ->
            val activeScreen = uiState.screen.value
            if (activeScreen != AppScreen.SESSION && activeScreen != AppScreen.BASELINE_FITNESS_TEST) {
                return@start
            }
            uiState.bikeData.value = mockBikeData
            uiState.bikeDataLastUpdatedAtEpochMs = System.currentTimeMillis()
            sessionManager.updateBikeData(mockBikeData)
            processImportedHrRuntimeTelemetry(source = "mock_trainer")
            applyCadenceDrivenRunnerControl(mockBikeData.instantaneousCadenceRpm)
        }
    }

    private fun stopMockTrainerEngine() {
        mockTrainerEngine?.stop()
        mockTrainerEngine = null
    }

    private fun transitionFromConnectingToSessionAfterControlGranted() {
        connectionFlow.transitionFromConnectingToSessionAfterControlGranted()
    }

    private fun completeConnectingToSession(
        dumpReason: String,
        postWorkoutFreshTelemetryDumpReason: String = dumpReason,
    ) {
        uiState.connectingTimeoutMessage.value = null
        val postWorkoutFreshTelemetry = postWorkoutFreshTelemetryStartPending
        if (!postWorkoutFreshTelemetry) {
            sessionManager.startSession(
                ftpWatts = currentFtpWatts(),
                startActive = false,
            )
        } else {
            postWorkoutFreshTelemetryStartPending = false
        }
        uiState.pendingCadenceStartAfterControlGranted = true
        uiState.autoPausedByZeroCadence = false
        keepScreenOn()
        uiState.screen.value = AppScreen.SESSION
        applyCadenceDrivenRunnerControl(uiState.bikeData.value?.instantaneousCadenceRpm)
        dumpUiState(
            if (postWorkoutFreshTelemetry) {
                postWorkoutFreshTelemetryDumpReason
            } else {
                dumpReason
            },
        )
    }

    private fun cancelConnectFlowTimeout() {
        connectionFlow.cancelConnectFlowTimeout()
    }

    private fun startExternalTrainerPreparationTimeout() {
        cancelExternalTrainerPreparationTimeout()
        externalTrainerPreparationTimeoutRunnable = Runnable {
            externalTrainerPreparationTimeoutRunnable = null
            if (externalTrainerPreparationState != ExternalTrainerPreparationState.PENDING) return@Runnable
            failExternalTrainerPreparation(reason = "external_prepare_timeout")
        }
        mainThreadHandler.postDelayed(externalTrainerPreparationTimeoutRunnable!!, connectFlowTimeoutMs)
    }

    private fun cancelExternalTrainerPreparationTimeout() {
        externalTrainerPreparationTimeoutRunnable?.let { mainThreadHandler.removeCallbacks(it) }
        externalTrainerPreparationTimeoutRunnable = null
    }

    private fun failExternalTrainerPreparation(reason: String) {
        pendingExternalTrainerPreparationAfterPermission = false
        cancelExternalTrainerPreparationTimeout()
        externalTrainerControlRequestPending = false
        externalTrainerControlRequestOutcome = ExternalTrainerControlRequestOutcome.FAILED
        trainerPreparationOwner = TrainerPreparationOwner.NONE
        preparedTrainerDeviceMac = null
        externalTrainerPreparationState = ExternalTrainerPreparationState.FAILED
        stopMockTrainerEngine()
        invalidateActiveFtmsCallbackGeneration()
        bleClient?.close()
        clearAppControlledTargetTracking()
        resetFtmsUiState(clearReady = true)
        endSessionScope(reason = reason)
        dumpUiState("failExternalTrainerPreparation(reason=$reason)")
    }

    private fun restartConnectFlowTimeout() {
        connectionFlow.restartConnectFlowTimeout()
    }

    private fun canReusePreparedTrainerConnection(): Boolean {
        val selectedFtmsMac = currentFtmsDeviceMac()
        return !isMockTrainerModeEnabled() &&
            trainerPreparationOwner != TrainerPreparationOwner.NONE &&
            selectedFtmsMac != null &&
            preparedTrainerDeviceMac == selectedFtmsMac &&
            uiState.ftmsReady.value &&
            ftmsController.isReady() &&
            !uiState.ftmsControlGranted.value
    }

    /**
     * Ends only the structured workout and keeps telemetry channels active.
     */
    private fun stopWorkout(clearLastTargetPower: Boolean = true) {
        val preservedLastTargetPower = uiState.lastTargetPower.value
        freeRideControlCoordinator.clear()
        uiState.pendingCadenceStartAfterControlGranted = false
        uiState.autoPausedByZeroCadence = false
        sessionManager.pauseSessionActivity()
        if (workoutRunner != null) {
            freeRideControlCoordinator.ignoreNextNullTargetWriteOnce()
        }
        workoutRunner?.stop()
        if (clearLastTargetPower) {
            uiState.lastTargetPower.value = null
        } else {
            uiState.lastTargetPower.value = preservedLastTargetPower
        }
        clearImportedHrRuntimeState()
    }

    /**
     * Clears FTMS UI state; callers decide whether readiness should be reset.
     */
    private fun resetFtmsUiState(clearReady: Boolean) {
        cancelPostWorkoutReleaseRamp()
        freeRideControlCoordinator.clear()
        uiState.postWorkoutFreerideModeActive = false
        postWorkoutTelemetryTransitionAwaitingControl = false
        postWorkoutTelemetryTransitionSoftStopInProgress = false
        if (clearReady) {
            uiState.ftmsReady.value = false
        }
        uiState.ftmsControlGranted.value = false
        uiState.lastTargetPower.value = null
        uiState.pendingCadenceStartAfterControlGranted = false
        uiState.autoPausedByZeroCadence = false
        clearImportedHrRuntimeState()
        sessionManager.pauseSessionActivity()
        dumpUiState("resetFtmsUiState(clearReady=$clearReady)")
    }

    /**
     * During pure FreeRide we intentionally avoid auto-requesting control so trainer-side
     * manual adjustment remains available.
     */
    private fun shouldRequestSessionControlOnReady(): Boolean {
        return freeRideControlCoordinator.shouldRequestSessionControlOnReady()
    }

    /**
     * Trainer-local controls seem to recover only after the trainer sees a workout stop.
     *
     * This path keeps the app-side session alive while issuing FTMS STOP as a trainer-mode
     * release signal before switching the UI/runtime into telemetry-only continuation.
     */
    private fun startPostWorkoutTelemetrySoftStop() {
        postWorkoutTelemetryTransitionSoftStopInProgress = true
        ftmsController.stopWorkout()
    }

    /**
     * Uses the rider's decision time on the completion prompt as the trainer's exit dwell window.
     */
    private fun startPostWorkoutCompletionExitSoftStop() {
        postWorkoutCompletionExitSoftStopInProgress = true
        logTestMarker(event = "post_workout_exit_prep_soft_stop_sent")
        ftmsController.stopWorkout()
    }

    /**
     * Applies the release-policy decision while keeping the existing STOP/disconnect flow intact.
     *
     * `Execute` inserts an app-owned target ramp before STOP. Other outcomes keep the earlier
     * teardown behavior so continuation stays functional while the policy evolves.
     */
    private fun continuePostWorkoutReleaseTeardown(
        owner: PostWorkoutReleaseOwner,
        decision: ReleaseRampDecision,
    ) {
        recordTrainerReleaseRuntime(
            event = "teardown_decision_applied",
            extraContext = mapOf(
                "owner" to owner.wireName,
                "decision" to decision.debugLabel(),
            ) + decision.debugContext(),
        )
        when (decision) {
            is ReleaseRampDecision.Execute -> startPostWorkoutReleaseRamp(
                owner = owner,
                plan = decision.plan,
            )

            ReleaseRampDecision.NoNeed,
            is ReleaseRampDecision.NotPossible -> startPostWorkoutSoftStop(owner)
        }
    }

    /**
     * Starts the pre-disconnect release ramp using app-owned target history instead of live power.
     *
     * The underlying FTMS controller already provides "last wins" target serialization, so this
     * lightweight scheduler can stay on elapsed-time ticks instead of building a second command
     * queue just for the ramp.
     */
    private fun startPostWorkoutReleaseRamp(
        owner: PostWorkoutReleaseOwner,
        plan: ReleaseRampPlan,
    ) {
        cancelPostWorkoutReleaseRamp()
        postWorkoutReleaseRampOwner = owner
        postWorkoutReleaseRampPlan = plan
        postWorkoutReleaseRampStartedAtElapsedMs = elapsedRealtimeMsProvider()
        postWorkoutReleaseRampFloorHoldLogged = false
        if (owner == PostWorkoutReleaseOwner.COMPLETION_EXIT_PREP) {
            logTestMarker(
                event = "post_workout_exit_prep_release_ramp_started",
                context = mapOf(
                    "startTargetPowerW" to plan.startTargetPowerW.toString(),
                    "endTargetPowerW" to plan.endTargetPowerW.toString(),
                    "durationMs" to plan.durationMs.toString(),
                    "floorHoldMs" to plan.floorHoldMs.toString(),
                ),
            )
        }
        recordTrainerReleaseRuntime(
            event = "release_ramp_started",
            extraContext = mapOf(
                "owner" to owner.wireName,
                "startTargetPowerW" to plan.startTargetPowerW.toString(),
                "endTargetPowerW" to plan.endTargetPowerW.toString(),
                "durationMs" to plan.durationMs.toString(),
                "floorHoldMs" to plan.floorHoldMs.toString(),
                "tickMs" to plan.tickMs.toString(),
            ),
        )
        scheduleNextPostWorkoutReleaseRampTick(delayMs = plan.tickMs)
    }

    private fun scheduleNextPostWorkoutReleaseRampTick(delayMs: Long) {
        val runnable = Runnable {
            postWorkoutReleaseRampRunnable = null
            runPostWorkoutReleaseRampTick()
        }
        postWorkoutReleaseRampRunnable = runnable
        mainThreadHandler.postDelayed(runnable, delayMs.coerceAtLeast(0L))
    }

    private fun runPostWorkoutReleaseRampTick() {
        val owner = postWorkoutReleaseRampOwner ?: return
        val plan = postWorkoutReleaseRampPlan ?: return
        val elapsedMs =
            (elapsedRealtimeMsProvider() - postWorkoutReleaseRampStartedAtElapsedMs).coerceAtLeast(0L)
        val targetWatts = releaseRampTargetPowerForElapsed(plan = plan, elapsedMs = elapsedMs)
        sendPostWorkoutReleaseRampTarget(targetWatts)

        val holdElapsedMs = (elapsedMs - plan.durationMs).coerceAtLeast(0L)
        if (elapsedMs >= plan.durationMs + plan.floorHoldMs) {
            cancelPostWorkoutReleaseRamp()
            recordTrainerReleaseRuntime(
                event = "release_ramp_completed",
                extraContext = mapOf(
                    "owner" to owner.wireName,
                    "elapsedMs" to elapsedMs.toString(),
                    "targetPowerW" to targetWatts.toString(),
                    "floorHoldMs" to plan.floorHoldMs.toString(),
                ),
            )
            startPostWorkoutSoftStop(owner)
            return
        }

        if (elapsedMs >= plan.durationMs && plan.floorHoldMs > 0L) {
            if (!postWorkoutReleaseRampFloorHoldLogged) {
                postWorkoutReleaseRampFloorHoldLogged = true
                recordTrainerReleaseRuntime(
                    event = "release_ramp_floor_hold_active",
                    extraContext = mapOf(
                        "owner" to owner.wireName,
                        "targetPowerW" to targetWatts.toString(),
                        "holdElapsedMs" to holdElapsedMs.toString(),
                        "floorHoldMs" to plan.floorHoldMs.toString(),
                    ),
                )
            }
            val remainingHoldMs = (plan.durationMs + plan.floorHoldMs - elapsedMs).coerceAtLeast(0L)
            scheduleNextPostWorkoutReleaseRampTick(delayMs = minOf(plan.tickMs, remainingHoldMs))
            return
        }

        scheduleNextPostWorkoutReleaseRampTick(delayMs = plan.tickMs)
    }

    private fun sendPostWorkoutReleaseRampTarget(targetWatts: Int) {
        uiState.lastTargetPower.value = targetWatts
        markAppControlledTargetRequested(targetWatts)
        ftmsController.setTargetPower(targetWatts)
    }

    private fun releaseRampTargetPowerForElapsed(
        plan: ReleaseRampPlan,
        elapsedMs: Long,
    ): Int {
        if (plan.durationMs <= 0L) {
            return plan.endTargetPowerW
        }
        val clampedElapsedMs = elapsedMs.coerceIn(0L, plan.durationMs)
        val progress = clampedElapsedMs.toDouble() / plan.durationMs.toDouble()
        val interpolatedTarget =
            plan.startTargetPowerW + ((plan.endTargetPowerW - plan.startTargetPowerW) * progress)
        val roundedTarget =
            (interpolatedTarget / plan.stepRoundToWatts.toDouble()).roundToInt() *
                plan.stepRoundToWatts
        return roundedTarget.coerceIn(
            minOf(plan.startTargetPowerW, plan.endTargetPowerW),
            maxOf(plan.startTargetPowerW, plan.endTargetPowerW),
        )
    }

    private fun cancelPostWorkoutReleaseRamp() {
        postWorkoutReleaseRampRunnable?.let(mainThreadHandler::removeCallbacks)
        postWorkoutReleaseRampRunnable = null
        postWorkoutReleaseRampOwner = null
        postWorkoutReleaseRampPlan = null
        postWorkoutReleaseRampStartedAtElapsedMs = 0L
        postWorkoutReleaseRampFloorHoldLogged = false
    }

    private fun startPostWorkoutSoftStop(owner: PostWorkoutReleaseOwner) {
        recordTrainerReleaseRuntime(
            event = "soft_stop_started",
            extraContext = mapOf("owner" to owner.wireName),
        )
        when (owner) {
            PostWorkoutReleaseOwner.TELEMETRY_TRANSITION -> startPostWorkoutTelemetrySoftStop()
            PostWorkoutReleaseOwner.COMPLETION_EXIT_PREP -> {
                logTestMarker(event = "post_workout_exit_prep_soft_stop_started")
                startPostWorkoutCompletionExitSoftStop()
            }
        }
    }

    /**
     * Closes the current FTMS link after the hidden completion-time STOP has been sent.
     */
    private fun beginPostWorkoutCompletionExplicitClose() {
        postWorkoutCompletionExitAwaitingControl = false
        postWorkoutCompletionExitSoftStopInProgress = false
        recordTrainerReleaseRuntime(event = "completion_exit_explicit_close_started")
        logTestMarker(event = "post_workout_exit_prep_explicit_close_started")
        closeBleTransportAfterStopFlow()
    }

    /**
     * After trainer-side STOP, reconnect from a clean GATT session so the continuation
     * matches a fresh telemetry-only start as closely as possible.
     */
    private fun startPostWorkoutTelemetryReconnect() {
        postWorkoutTelemetryReconnectInProgress = true
        postWorkoutTelemetryAwaitingReconnectControl = false
        postWorkoutFreshTelemetryStartPending = true
        recordTrainerReleaseRuntime(event = "telemetry_reconnect_started")
        cancelConnectFlowTimeout()
        stopFlowPolicy.resetToIdle()
        cancelMockConnectTransition()
        stopMockTrainerEngine()
        uiState.connectionIssueMessage.value = null
        uiState.connectingTimeoutMessage.value = null
        uiState.suggestTrainerSearchAfterConnectionIssue.value = false
        uiState.suggestOpenSettingsAfterConnectionIssue.value = false
        cancelPendingPostWorkoutTelemetryReconnect()
        resetFtmsUiState(clearReady = true)
        postWorkoutFreshTelemetryStartPending = true
        uiState.pendingSessionStartAfterPermission = false
        uiState.bikeData.value = null
        uiState.bikeDataLastUpdatedAtEpochMs = null
        externalTrainerControlRequestPending = false
        externalTrainerControlRequestOutcome = null
        pendingExternalTrainerPreparationAfterPermission = false
        cancelExternalTrainerPreparationTimeout()
        externalTrainerPreparationState = ExternalTrainerPreparationState.IDLE
        trainerPreparationOwner = TrainerPreparationOwner.NONE
        preparedTrainerDeviceMac = null

        if (bleClient == null) {
            schedulePostWorkoutTelemetryReconnectAfterDisconnect()
            dumpUiState("startPostWorkoutTelemetryReconnect(noActiveTransport)")
            return
        }

        bleClient?.close()
        dumpUiState("startPostWorkoutTelemetryReconnect(awaitingDisconnect)")
    }

    /**
     * Gives the trainer a short transport-settle window after workout STOP/disconnect
     * before reopening a telemetry-only FTMS link.
     *
     * Some trainers keep their workout/remote-control mode latched until the prior
     * session is fully torn down on-device. Waiting for the disconnect callback and
     * then a bounded settle delay mirrors the operator-observed "mode exit" window
     * without forcing the app to abandon the active logical session.
     */
    private fun schedulePostWorkoutTelemetryReconnectAfterDisconnect() {
        cancelPendingPostWorkoutTelemetryReconnect()
        val runnable = Runnable {
            postWorkoutTelemetryReconnectRunnable = null
            if (!postWorkoutFreshTelemetryStartPending) return@Runnable
            bleClient = createFtmsBleClient()
            ftmsController = createFtmsController()

            val connectInitiated = ensureBluetoothPermission()
            if (connectInitiated) {
                uiState.pendingSessionStartAfterPermission = false
                _pendingPermissionRequestId = 0L
                if (connectBleClients()) {
                    enterConnectingState()
                } else {
                    postWorkoutTelemetryReconnectInProgress = false
                    postWorkoutFreshTelemetryStartPending = false
                    uiState.screen.value = AppScreen.SESSION
                    dumpUiState("postWorkoutFreshTelemetryStartConnectFailed")
                }
            } else {
                uiState.pendingSessionStartAfterPermission = true
                _pendingPermissionRequestId = ++nextPermissionRequestId
                recordDiagnostics(
                    category = "permission",
                    event = "bluetooth_connect_request_issued",
                    context = mapOf("requestId" to _pendingPermissionRequestId.toString()),
                )
                enterConnectingState()
            }
            dumpUiState("schedulePostWorkoutTelemetryReconnectAfterDisconnect(connectInitiated=$connectInitiated)")
        }
        postWorkoutTelemetryReconnectRunnable = runnable
        mainThreadHandler.postDelayed(runnable, explicitTrainerReconnectSettleDelayMs)
    }

    private fun cancelPendingPostWorkoutTelemetryReconnect() {
        postWorkoutTelemetryReconnectRunnable?.let(mainThreadHandler::removeCallbacks)
        postWorkoutTelemetryReconnectRunnable = null
    }

    /**
     * Some trainers keep rider-local resistance/power controls latched until the app-owned
     * FTMS epoch has truly ended. When telemetry-only is selected after any prior target write,
     * reconnect through a fresh BLE epoch instead of carrying app-controlled history straight
     * into the next "manual" ride.
     */
    private fun beginTelemetryOnlyReconnectForStartIfNeeded(useMockTrainer: Boolean): Boolean {
        if (useMockTrainer || !isTelemetryOnlyModeSelected()) {
            return false
        }
        val appControlledHistoryPresent =
            uiState.trainerControlAuthority.value == TrainerControlAuthority.APP_CONTROLLED ||
                uiState.lastAppControlledTargetPower.value != null
        if (!appControlledHistoryPresent) {
            return false
        }
        if (!uiState.ftmsReady.value && bleClient == null) {
            clearAppControlledTargetTracking()
            return false
        }

        telemetryOnlyStartReconnectInProgress = true
        telemetryOnlyStartShouldBypassControlRequest = true
        uiState.pendingSessionStartAfterPermission = false
        uiState.bikeData.value = null
        uiState.bikeDataLastUpdatedAtEpochMs = null
        recordDiagnostics(
            category = "session",
            event = "telemetry_only_clean_reconnect_requested",
            context = mapOf(
                "authority" to uiState.trainerControlAuthority.value.name,
                "lastAppControlledTargetPower" to (uiState.lastAppControlledTargetPower.value?.toString() ?: "none"),
            ),
        )
        logTestMarker(
            event = "telemetry_only_clean_reconnect_requested",
            context = mapOf(
                "authority" to uiState.trainerControlAuthority.value.name,
                "lastAppControlledTargetPower" to (uiState.lastAppControlledTargetPower.value?.toString() ?: "none"),
            ),
        )
        resetFtmsUiState(clearReady = true)
        enterConnectingState()

        val currentClient = bleClient
        if (currentClient != null && currentClient.close()) {
            dumpUiState("beginTelemetryOnlyReconnectForStart(awaitingDisconnect)")
            return true
        }

        completeTelemetryOnlyReconnectWithoutDisconnectCallback()
        return true
    }

    private fun completeTelemetryOnlyReconnectWithoutDisconnectCallback() {
        clearAppControlledTargetTracking()
        uiState.ftmsReady.value = false
        uiState.ftmsControlGranted.value = false
        bleClient = null
        invalidateActiveFtmsCallbackGeneration()
        scheduleTelemetryOnlyReconnectAfterDisconnect()
        dumpUiState("completeTelemetryOnlyReconnectWithoutDisconnectCallback")
    }

    private fun scheduleTelemetryOnlyReconnectAfterDisconnect() {
        cancelPendingTelemetryOnlyReconnect()
        val runnable = Runnable {
            telemetryOnlyStartReconnectRunnable = null
            if (!telemetryOnlyStartReconnectInProgress) return@Runnable
            telemetryOnlyStartReconnectInProgress = false
            bleClient = createFtmsBleClient()
            ftmsController = createFtmsController()

            val connectInitiated = ensureBluetoothPermission()
            if (connectInitiated) {
                uiState.pendingSessionStartAfterPermission = false
                _pendingPermissionRequestId = 0L
                recordDiagnostics(
                    category = "session",
                    event = "telemetry_only_clean_reconnect_connect_attempt",
                )
                if (connectBleClients()) {
                    enterConnectingState()
                } else {
                    telemetryOnlyStartShouldBypassControlRequest = false
                    uiState.screen.value = AppScreen.MENU
                    endSessionScope(reason = "telemetry_only_clean_reconnect_connect_failed")
                }
            } else {
                uiState.pendingSessionStartAfterPermission = true
                _pendingPermissionRequestId = ++nextPermissionRequestId
                recordDiagnostics(
                    category = "permission",
                    event = "bluetooth_connect_request_issued",
                    context = mapOf("requestId" to _pendingPermissionRequestId.toString()),
                )
                enterConnectingState()
            }
            dumpUiState("scheduleTelemetryOnlyReconnectAfterDisconnect(connectInitiated=$connectInitiated)")
        }
        telemetryOnlyStartReconnectRunnable = runnable
        mainThreadHandler.postDelayed(runnable, explicitTrainerReconnectSettleDelayMs)
    }

    private fun cancelPendingTelemetryOnlyReconnect() {
        telemetryOnlyStartReconnectRunnable?.let(mainThreadHandler::removeCallbacks)
        telemetryOnlyStartReconnectRunnable = null
    }

    private fun resetPostWorkoutCompletionExitState() {
        postWorkoutCompletionExitArmed = false
        postWorkoutCompletionExitAwaitingControl = false
        postWorkoutCompletionExitSoftStopInProgress = false
    }

    /**
     * Finishes the trainer-side handoff into telemetry-only continuation without reopening
     * session storage or summary state.
     */
    private fun completePostWorkoutTelemetryTransition(
        preserveControlGranted: Boolean,
        dumpReason: String,
    ) {
        postWorkoutTelemetryTransitionAwaitingControl = false
        postWorkoutTelemetryTransitionSoftStopInProgress = false
        postWorkoutTelemetryReconnectInProgress = false
        postWorkoutTelemetryAwaitingReconnectControl = false
        uiState.selectedSessionSetupMode.value = SessionSetupMode.TELEMETRY_ONLY
        clearStructuredWorkoutSelectionForTelemetryOnlyMode()
        uiState.postWorkoutFreerideModeActive = false
        uiState.pendingCadenceStartAfterControlGranted = true
        uiState.autoPausedByZeroCadence = false
        uiState.ftmsControlGranted.value = preserveControlGranted
        syncSessionActivityState()
        dumpUiState(dumpReason)
    }

    /**
     * Routes runner target updates through the FreeRide release/reacquire protocol.
     */
    private fun handleRunnerTargetWrite(targetWatts: Int?) {
        val effectiveTargetWatts = when {
            targetWatts == null -> null
            importedHrRuntimeTargetOverrideWatts != null -> importedHrRuntimeTargetOverrideWatts
            else -> targetWatts
        }
        val terminalRunnerTargetClear = effectiveTargetWatts == null && uiState.runner.value.done
        uiState.lastTargetPower.value = effectiveTargetWatts
        if (isMockTrainerModeEnabled()) {
            mockTrainerEngine?.setTargetPowerWatts(effectiveTargetWatts)
            effectiveTargetWatts?.let(::markAppControlledTargetConfirmed)
            return
        }
        if (terminalRunnerTargetClear) {
            return
        }
        effectiveTargetWatts?.let(::markAppControlledTargetRequested)
        freeRideControlCoordinator.onRunnerTargetWrite(
            targetWatts = effectiveTargetWatts,
            ftmsReady = uiState.ftmsReady.value,
            controlGranted = uiState.ftmsControlGranted.value,
        )
    }

    /**
     * Applies cadence gating for runner progression.
     *
     * Runner starts only after control is granted and cadence is above zero.
     * During an active workout, cadence zero auto-pauses and cadence above zero
     * auto-resumes only when the pause was cadence-triggered.
     */
    private fun applyCadenceDrivenRunnerControl(cadenceRpm: Double?) {
        val telemetryOnlyModeActive = isTelemetryOnlyModeSelected()
        val postWorkoutFreerideModeActive = uiState.postWorkoutFreerideModeActive
        val sessionCanRunWithoutControl =
            telemetryOnlyModeActive || postWorkoutFreerideModeActive
        if (
            uiState.screen.value != AppScreen.SESSION ||
                (!uiState.ftmsControlGranted.value && !sessionCanRunWithoutControl)
        ) {
            syncSessionActivityState()
            return
        }
        val cadencePositive = (cadenceRpm ?: 0.0) > 0.0

        if (uiState.pendingCadenceStartAfterControlGranted) {
            if (!cadencePositive) {
                syncSessionActivityState()
                return
            }
            uiState.pendingCadenceStartAfterControlGranted = false
            uiState.autoPausedByZeroCadence = false
            if (telemetryOnlyModeActive || postWorkoutFreerideModeActive) {
                sessionManager.resumeSessionActivity()
                dumpUiState(
                    if (telemetryOnlyModeActive) {
                        "telemetryOnlySessionStartByCadence"
                    } else {
                        "postWorkoutFreerideStartByCadence"
                    }
                )
                return
            }
            val runner = ensureWorkoutRunner()
            if (runner == null) {
                syncSessionActivityState()
                dumpUiState("runnerStartSkippedNoWorkout")
                return
            }
            val selectedImportedWorkout = uiState.selectedImportedWorkout.value
            if (selectedImportedWorkout != null &&
                !prepareImportedHrRuntimeStartIfNeeded(selectedImportedWorkout)
            ) {
                syncSessionActivityState()
                dumpUiState("runnerStartBlockedImportedHrPreflight")
                return
            }
            runner.start()
            sessionManager.resumeSessionActivity()
            dumpUiState("runnerStartByCadence")
            return
        }

        val runner = workoutRunner ?: run {
            if (telemetryOnlyModeActive || postWorkoutFreerideModeActive) {
                if (cadencePositive) {
                    uiState.autoPausedByZeroCadence = false
                    sessionManager.resumeSessionActivity()
                    dumpUiState(
                        if (telemetryOnlyModeActive) {
                            "telemetryOnlySessionActiveByCadence"
                        } else {
                            "postWorkoutFreerideActiveByCadence"
                        }
                    )
                } else {
                    uiState.autoPausedByZeroCadence = true
                    sessionManager.pauseSessionActivity()
                    dumpUiState(
                        if (telemetryOnlyModeActive) {
                            "telemetryOnlySessionPausedByCadenceZero"
                        } else {
                            "postWorkoutFreeridePausedByCadenceZero"
                        }
                    )
                }
            } else {
                syncSessionActivityState()
            }
            return
        }
        val currentState = uiState.runner.value
        if (!currentState.running || currentState.done) {
            syncSessionActivityState()
            return
        }

        if (!currentState.paused && !cadencePositive) {
            uiState.autoPausedByZeroCadence = true
            runner.pause()
            sessionManager.pauseSessionActivity()
            dumpUiState("runnerAutoPauseByCadenceZero")
            return
        }

        if (currentState.paused && cadencePositive && uiState.autoPausedByZeroCadence) {
            uiState.autoPausedByZeroCadence = false
            runner.resume()
            sessionManager.resumeSessionActivity()
            dumpUiState("runnerAutoResumeByCadencePositive")
        }
        syncSessionActivityState()
    }

    private fun syncSessionActivityState() {
        val runnerState = uiState.runner.value
        val telemetryOnlyShouldCountAsActive =
            isTelemetryOnlyModeSelected() &&
                uiState.screen.value == AppScreen.SESSION &&
                uiState.ftmsReady.value &&
                !uiState.pendingCadenceStartAfterControlGranted &&
                (uiState.bikeData.value?.instantaneousCadenceRpm ?: 0.0) > 0.0
        val postWorkoutFreerideShouldCountAsActive =
            uiState.postWorkoutFreerideModeActive &&
                uiState.screen.value == AppScreen.SESSION &&
                uiState.ftmsReady.value &&
                !uiState.pendingCadenceStartAfterControlGranted &&
                (uiState.bikeData.value?.instantaneousCadenceRpm ?: 0.0) > 0.0
        val shouldCountAsActive =
            telemetryOnlyShouldCountAsActive ||
                postWorkoutFreerideShouldCountAsActive ||
                (
                    uiState.screen.value == AppScreen.SESSION &&
                        uiState.ftmsReady.value &&
                        uiState.ftmsControlGranted.value &&
                        !uiState.pendingCadenceStartAfterControlGranted &&
                        runnerState.running &&
                        !runnerState.paused &&
                        !runnerState.done
                    )

        if (shouldCountAsActive) {
            sessionManager.resumeSessionActivity()
        } else {
            sessionManager.pauseSessionActivity()
        }
    }

    private fun isTelemetryOnlyModeSelected(): Boolean {
        return uiState.selectedSessionSetupMode.value == SessionSetupMode.TELEMETRY_ONLY
    }

    /**
     * Telemetry-only must never inherit a stale structured workout selection.
     *
     * Clearing the full selection cluster here keeps menu summary, session runtime,
     * and exported telemetry behavior aligned even if another path previously seeded
     * editor/file workout metadata.
     */
    private fun clearStructuredWorkoutSelectionForTelemetryOnlyMode() {
        uiState.selectedWorkout.value = null
        uiState.selectedImportedWorkout.value = null
        uiState.selectedWorkoutFileName.value = null
        uiState.selectedWorkoutStepCount.value = null
        uiState.selectedWorkoutPlannedTss.value = null
        uiState.selectedWorkoutTotalDurationSec.value = null
        uiState.selectedWorkoutImportError.value = null
        uiState.workoutExecutionModeMessage.value = null
        uiState.workoutExecutionModeIsError.value = false
        workoutRunner = null
        clearImportedHrRuntimeState()
    }

    /**
     * Lazily builds a runner bound to the currently selected workout.
     */
    private fun ensureWorkoutRunner(): WorkoutRunner? {
        val existing = workoutRunner
        if (existing != null) return existing

        val selectedWorkout = uiState.selectedWorkout.value
        val stepper = when {
            selectedWorkout != null -> createRunnerStepper(selectedWorkout)
            uiState.selectedImportedWorkout.value != null -> {
                createRunnerStepper(requireNotNull(uiState.selectedImportedWorkout.value))
            }
            else -> {
                Log.e("WORKOUT", "Runner creation skipped: no selected workout in session flow")
                null
            }
        }
        if (stepper == null) {
            handleRunnerCreationBlocked()
            return null
        }
        val runner = WorkoutRunner(
            stepper = stepper,
            targetWriter = { targetWatts -> handleRunnerTargetWrite(targetWatts) },
            onStateChanged = { state ->
                uiState.runner.value = state
                syncImportedHrRuntimeForRunnerState(state)
            },
            handler = mainThreadHandler,
        )

        workoutRunner = runner
        return runner
    }

    /**
     * Prefers strict execution mapping while preserving legacy fallback for unsupported steps.
     */
    private fun createRunnerStepper(workout: WorkoutFile): WorkoutStepper? {
        val ftpWatts = currentFtpWatts().coerceAtLeast(1)
        return when (val mapped = ExecutionWorkoutMapper.map(workout, ftp = ftpWatts)) {
            is MappingResult.Success -> {
                WorkoutStepper.fromExecutionWorkout(mapped.workout)
            }

            is MappingResult.Failure -> {
                val summary = mapped.errors.joinToString(separator = ", ") { it.code.name }
                if (!workoutReadinessEvaluator.handleExecutionMappingFailure(summary = summary, source = "runner_creation")) {
                    return null
                }
                WorkoutStepper(workout, ftpWatts = ftpWatts)
            }
        }
    }

    /**
     * Uses strict execution mapping for imported `ergo_workout` timelines.
     */
    private fun createRunnerStepper(workout: ImportedErgoWorkout): WorkoutStepper? {
        return when (val mapped = ImportedErgoWorkoutExecutionMapper.map(workout)) {
            is MappingResult.Success -> WorkoutStepper.fromExecutionWorkout(mapped.workout)
            is MappingResult.Failure -> {
                val failureContext = workoutReadinessEvaluator.importedWorkoutExecutionFailureContext(workout, mapped)
                val summary = mapped.errors.joinToString(separator = ", ") { it.code.name }
                if (!workoutReadinessEvaluator.handleExecutionMappingFailure(
                        summary = summary,
                        source = "runner_creation_ergo",
                        allowLegacyFallback = false,
                        detail = failureContext.detail,
                        diagnosticsContext = failureContext.diagnosticsContext,
                    )
                ) {
                    return null
                }
                null
            }
        }
    }

    private fun prepareImportedHrRuntimeStartIfNeeded(
        workout: ImportedErgoWorkout,
    ): Boolean {
        val firstStep = workout.steps.firstOrNull()
        val step = firstStep as? ImportedErgoWorkoutStep.HeartRateSteady
            ?: run {
                clearImportedHrRuntimeState()
                return true
            }
        return prepareImportedHrRuntimeStartIfNeeded(workout = workout, step = step)
    }

    private fun prepareImportedHrRuntimeStartIfNeeded(
        workout: ImportedErgoWorkout,
        step: ImportedErgoWorkoutStep.HeartRateSteady,
    ): Boolean {
        val snapshot = currentImportedHrCapabilitySnapshot()
        return when (
            val preflight = ImportedHrRuntimePreflightAdapter.evaluate(
                workout = workout,
                step = step,
                snapshot = snapshot,
            )
        ) {
            is ImportedHrRuntimePreflightResult.MissingCanonicalControl -> {
                clearImportedHrRuntimeState()
                uiState.workoutExecutionModeIsError.value = true
                uiState.workoutExecutionModeMessage.value =
                    "Heart-rate-controlled workout start is blocked because canonical workout-level HR control metadata is missing."
                false
            }

            is ImportedHrRuntimePreflightResult.Blocked -> {
                clearImportedHrRuntimeState()
                uiState.workoutExecutionModeIsError.value = true
                uiState.workoutExecutionModeMessage.value =
                    "Heart-rate-controlled workout start requires ${preflight.failureState.missingCapabilities.joinToString()}."
                false
            }

            is ImportedHrRuntimePreflightResult.Ready -> {
                val controller = ImportedHrRuntimeControllerV1(preflight.policy)
                val transition = controller.start(snapshot)
                importedHrRuntimeController = controller
                importedHrRuntimeTargetOverrideWatts = null
                importedHrRuntimeActiveSourceStepIndex = step.stepIndex
                armImportedHrStartupGraceIfNeeded()
                uiState.workoutExecutionModeMessage.value = null
                uiState.workoutExecutionModeIsError.value = false
                recordImportedHrRuntimeTransition(
                    transition = transition,
                    source = "start",
                )
                applyImportedHrRuntimeCommands(
                    commands = transition.commands,
                    stopReason = null,
                    source = "start",
                )
                true
            }
        }
    }

    private fun processImportedHrRuntimeTelemetry(source: String) {
        val controller = importedHrRuntimeController ?: return
        val heartRateSnapshot = currentImportedHeartRateSnapshot()
        if (shouldSkipImportedHrRuntimeTelemetry(source, heartRateSnapshot)) {
            return
        }
        if (source == "external_heart_rate") {
            recordImportedHrRuntimeTelemetryProbe(
                source = source,
                heartRateSnapshot = heartRateSnapshot,
            )
        }
        if (shouldSuppressImportedHrSignalLossDuringStartupGrace(heartRateSnapshot)) {
            return
        }
        completeImportedHrStartupGraceOnPositiveHeartRate(heartRateSnapshot)
        val transition = controller.onTelemetry(
            heartRateBpm = heartRateSnapshot.selectedHeartRateBpm,
            hasTrainerControl = uiState.ftmsControlGranted.value,
            elapsedRealtimeMs = diagnosticsNowMillis(),
        ) ?: return
        recordImportedHrRuntimeTransition(
            transition = transition,
            source = source,
            heartRateSnapshot = heartRateSnapshot,
        )
        applyImportedHrRuntimeCommands(
            commands = transition.commands,
            stopReason = transition.event.name,
            source = source,
        )
    }

    /**
     * Avoids double-driving imported-HR corrections from trainer telemetry
     * when an external HR peripheral already owns the resolved HR source.
     *
     * FTMS/mock bike packets should still refresh cadence and power telemetry,
     * but HR control decisions must follow the selected HR source exactly once
     * per telemetry change instead of decrementing twice from duplicated
     * callbacks.
     */
    private fun shouldSkipImportedHrRuntimeTelemetry(
        source: String,
        heartRateSnapshot: ImportedHeartRateSnapshot,
    ): Boolean {
        if (heartRateSnapshot.selectedSource != "external_heart_rate") {
            return false
        }
        return source == "indoor_bike" || source == "mock_trainer"
    }

    /**
     * Records imported-HR runtime transitions with enough live context to tell
     * whether cadence gating or upstream telemetry ordering masked a fallback.
     */
    private fun recordImportedHrRuntimeTransition(
        transition: ImportedHrRuntimeTransition,
        source: String,
        heartRateSnapshot: ImportedHeartRateSnapshot = currentImportedHeartRateSnapshot(),
    ) {
        val context = linkedMapOf(
            "source" to source,
            "event" to transition.event.name,
            "fromState" to transition.fromState.javaClass.simpleName,
            "toState" to transition.toState.javaClass.simpleName,
            "heartRateBpm" to (heartRateSnapshot.selectedHeartRateBpm?.toString() ?: "null"),
            "resolvedHeartRateSource" to heartRateSnapshot.selectedSource,
            "ftmsHeartRateBpm" to (heartRateSnapshot.ftmsHeartRateBpm?.toString() ?: "null"),
            "externalHeartRateBpm" to (heartRateSnapshot.externalHeartRateBpm?.toString() ?: "null"),
            "trainerControl" to uiState.ftmsControlGranted.value.toString(),
            "cadenceRpm" to ((uiState.bikeData.value?.instantaneousCadenceRpm)?.toString() ?: "null"),
            "autoPausedByZeroCadence" to uiState.autoPausedByZeroCadence.toString(),
            "commands" to transition.commands.joinToString(separator = ",") { command ->
                when (command) {
                    is ImportedHrRuntimeCommand.SetPower -> "SetPower(${command.watts})"
                    ImportedHrRuntimeCommand.BlockIncrease -> "BlockIncrease"
                    ImportedHrRuntimeCommand.FailStart -> "FailStart"
                    ImportedHrRuntimeCommand.StopWorkout -> "StopWorkout"
                    ImportedHrRuntimeCommand.RequireUserAcknowledgement -> "RequireUserAcknowledgement"
                    is ImportedHrRuntimeCommand.ReportUnreachableTarget ->
                        "ReportUnreachableTarget(${command.status.name})"

                    ImportedHrRuntimeCommand.ClearUnreachableTargetStatus ->
                        "ClearUnreachableTargetStatus"
                }
            },
        )
        recordDiagnostics(
            category = "imported_hr_runtime",
            event = "transition",
            context = context,
        )
    }

    /**
     * Records the HR-source resolution that the imported-HR runtime saw when
     * an external-HR-triggered re-evaluation happened, even if no transition fired.
     *
     * This keeps live validation actionable when the external channel changes but
     * FTMS HR still wins source selection, which would otherwise look like a
     * "missing fallback" with no nearby evidence in logcat.
     */
    private fun recordImportedHrRuntimeTelemetryProbe(
        source: String,
        heartRateSnapshot: ImportedHeartRateSnapshot,
    ) {
        val signalLossIntent = importedHrSignalLossIntent()
        recordDiagnostics(
            category = "imported_hr_runtime",
            event = "telemetry_probe",
            context = linkedMapOf(
                "source" to source,
                "resolvedHeartRateSource" to heartRateSnapshot.selectedSource,
                "heartRateBpm" to (heartRateSnapshot.selectedHeartRateBpm?.toString() ?: "null"),
                "heartRateZeroObserved" to (heartRateSnapshot.selectedHeartRateBpm == 0).toString(),
                "ftmsHeartRateBpm" to (heartRateSnapshot.ftmsHeartRateBpm?.toString() ?: "null"),
                "externalHeartRateBpm" to (heartRateSnapshot.externalHeartRateBpm?.toString() ?: "null"),
                "signalLossIntendedEvent" to (signalLossIntent?.event ?: "none"),
                "signalLossIntendedTargetState" to (signalLossIntent?.targetState ?: "none"),
                "signalLossIntendedCommands" to (signalLossIntent?.commands ?: "none"),
                "trainerControl" to uiState.ftmsControlGranted.value.toString(),
                "cadenceRpm" to ((uiState.bikeData.value?.instantaneousCadenceRpm)?.toString() ?: "null"),
                "autoPausedByZeroCadence" to uiState.autoPausedByZeroCadence.toString(),
            ),
        )
    }

    private fun applyImportedHrRuntimeCommands(
        commands: Collection<ImportedHrRuntimeCommand>,
        stopReason: String?,
        source: String,
    ) {
        commands.forEach { command ->
            when (command) {
                is ImportedHrRuntimeCommand.SetPower -> {
                    importedHrRuntimeTargetOverrideWatts = command.watts
                    handleRunnerTargetWrite(command.watts)
                }

                ImportedHrRuntimeCommand.BlockIncrease -> Unit
                ImportedHrRuntimeCommand.FailStart -> Unit
                ImportedHrRuntimeCommand.ClearUnreachableTargetStatus -> {
                    uiState.workoutExecutionModeIsError.value = false
                    uiState.workoutExecutionModeMessage.value = null
                }

                is ImportedHrRuntimeCommand.ReportUnreachableTarget -> {
                    uiState.workoutExecutionModeIsError.value = false
                    uiState.workoutExecutionModeMessage.value =
                        importedHrRuntimeUnreachableStatusMessage(command.status)
                }

                ImportedHrRuntimeCommand.StopWorkout -> {
                    uiState.workoutExecutionModeIsError.value = true
                    uiState.workoutExecutionModeMessage.value =
                        importedHrRuntimeStopMessage(
                            stopReason = stopReason,
                            source = source,
                        )
                    stopWorkout(clearLastTargetPower = false)
                }

                ImportedHrRuntimeCommand.RequireUserAcknowledgement -> {
                    uiState.workoutExecutionModeIsError.value = true
                }
            }
        }
    }

    /**
     * Keeps degraded imported-HR reachability outcomes explicit while the
     * session is still running instead of implying the target is being met.
     */
    private fun importedHrRuntimeUnreachableStatusMessage(
        status: ImportedHrRuntimeUnreachableTargetStatus,
    ): String {
        return when (status) {
            ImportedHrRuntimeUnreachableTargetStatus.AT_MAX_POWER_BELOW_TARGET ->
                "Heart-rate-controlled workout is below target at maximum power."

            ImportedHrRuntimeUnreachableTargetStatus.AT_MIN_POWER_ABOVE_TARGET ->
                "Heart-rate-controlled workout is above target at minimum power."
        }
    }

    /**
     * Keeps terminal imported-HR outcomes explicit for operators instead of
     * leaking state-machine event names straight into the session UI.
     */
    private fun importedHrRuntimeStopMessage(
        stopReason: String?,
        source: String,
    ): String {
        return when (stopReason) {
            ImportedHrRuntimeEvent.HR_SIGNAL_LOSS_FALLBACK_APPLIED.name ->
                "Heart-rate-controlled workout stopped after heart-rate signal loss fallback."

            ImportedHrRuntimeEvent.HR_CAP_BREACH_PERSISTED.name ->
                "Heart-rate-controlled workout stopped because the heart-rate safety cap stayed exceeded."

            ImportedHrRuntimeEvent.TRAINER_CONTROL_LOST.name ->
                "Heart-rate-controlled workout stopped because trainer control was lost."

            else -> "Heart-rate-controlled workout stopped: ${stopReason ?: source}."
        }
    }

    private fun currentImportedHrCapabilitySnapshot(): ImportedHrExecutionCapabilitySnapshot {
        return ImportedHrExecutionCapabilitySnapshot(
            hasHeartRateSignal = currentImportedHeartRateBpm() != null,
            hasTrainerControl = uiState.ftmsControlGranted.value,
        )
    }

    private fun currentImportedHeartRateBpm(): Int? {
        return currentImportedHeartRateSnapshot().selectedHeartRateBpm
    }

    /**
     * Prevents trainer-integrated HR startup warmup zeros from looking like a
     * real mid-session signal-loss event before the sensor has had a short
     * chance to acquire the strap/session.
     */
    private fun armImportedHrStartupGraceIfNeeded() {
        cancelImportedHrStartupGrace()
        val heartRateSnapshot = currentImportedHeartRateSnapshot()
        if (heartRateSnapshot.selectedSource != "ftms_indoor_bike") return
        if ((heartRateSnapshot.selectedHeartRateBpm ?: 0) > 0) return

        importedHrStartupGraceActive = true
        recordDiagnostics(
            category = "imported_hr_runtime",
            event = "startup_grace_started",
            context = linkedMapOf(
                "resolvedHeartRateSource" to heartRateSnapshot.selectedSource,
                "heartRateBpm" to (heartRateSnapshot.selectedHeartRateBpm?.toString() ?: "null"),
                "graceMs" to importedHrStartupGraceMs.toString(),
            ),
        )
        val runnable = Runnable {
            importedHrStartupGraceActive = false
            importedHrStartupGraceExpiryRunnable = null
            recordDiagnostics(
                category = "imported_hr_runtime",
                event = "startup_grace_expired",
                context = linkedMapOf(
                    "resolvedHeartRateSource" to currentImportedHeartRateSnapshot().selectedSource,
                    "heartRateBpm" to (currentImportedHeartRateSnapshot().selectedHeartRateBpm?.toString() ?: "null"),
                ),
            )
            processImportedHrRuntimeTelemetry(source = "startup_grace_expired")
        }
        importedHrStartupGraceExpiryRunnable = runnable
        mainThreadHandler.postDelayed(runnable, importedHrStartupGraceMs)
    }

    private fun cancelImportedHrStartupGrace() {
        importedHrStartupGraceActive = false
        importedHrStartupGraceExpiryRunnable?.let(mainThreadHandler::removeCallbacks)
        importedHrStartupGraceExpiryRunnable = null
    }

    private fun shouldSuppressImportedHrSignalLossDuringStartupGrace(
        heartRateSnapshot: ImportedHeartRateSnapshot,
    ): Boolean {
        if (!importedHrStartupGraceActive) return false
        if (!uiState.ftmsControlGranted.value) return false
        return heartRateSnapshot.selectedSource == "ftms_indoor_bike" &&
            (heartRateSnapshot.selectedHeartRateBpm == null || heartRateSnapshot.selectedHeartRateBpm <= 0)
    }

    private fun completeImportedHrStartupGraceOnPositiveHeartRate(
        heartRateSnapshot: ImportedHeartRateSnapshot,
    ) {
        if (!importedHrStartupGraceActive) return
        if ((heartRateSnapshot.selectedHeartRateBpm ?: 0) <= 0) return
        recordDiagnostics(
            category = "imported_hr_runtime",
            event = "startup_hr_acquired",
            context = linkedMapOf(
                "resolvedHeartRateSource" to heartRateSnapshot.selectedSource,
                "heartRateBpm" to heartRateSnapshot.selectedHeartRateBpm.toString(),
            ),
        )
        cancelImportedHrStartupGrace()
    }

    /**
     * Keeps imported-HR HR-source resolution aligned with the product rule that
     * an external HR peripheral is preferred over trainer-integrated HR when
     * both are available.
     */
    private fun currentImportedHeartRateSnapshot(): ImportedHeartRateSnapshot {
        val ftmsHeartRateBpm = uiState.bikeData.value?.heartRateBpm
        val externalHeartRateBpm = uiState.heartRate.value
        return when {
            externalHeartRateBpm != null -> ImportedHeartRateSnapshot(
                selectedHeartRateBpm = externalHeartRateBpm,
                selectedSource = "external_heart_rate",
                ftmsHeartRateBpm = ftmsHeartRateBpm,
                externalHeartRateBpm = externalHeartRateBpm,
            )

            ftmsHeartRateBpm != null -> ImportedHeartRateSnapshot(
                selectedHeartRateBpm = ftmsHeartRateBpm,
                selectedSource = "ftms_indoor_bike",
                ftmsHeartRateBpm = ftmsHeartRateBpm,
                externalHeartRateBpm = externalHeartRateBpm,
            )

            else -> ImportedHeartRateSnapshot(
                selectedHeartRateBpm = null,
                selectedSource = "none",
                ftmsHeartRateBpm = null,
                externalHeartRateBpm = null,
            )
        }
    }

    private fun clearImportedHrRuntimeState() {
        cancelImportedHrStartupGrace()
        importedHrRuntimeController = null
        importedHrRuntimeTargetOverrideWatts = null
        importedHrRuntimeActiveSourceStepIndex = null
    }

    /**
     * Keeps imported-HR control ownership attached only to active HR steady
     * execution segments so power ramps stay on the deterministic power-led path.
     */
    private fun syncImportedHrRuntimeForRunnerState(state: io.github.ewoc2026.ewoc.workout.runner.RunnerState) {
        val selectedImportedWorkout = uiState.selectedImportedWorkout.value
        if (selectedImportedWorkout == null) {
            clearImportedHrRuntimeState()
            return
        }

        if (state.done || !state.running) {
            clearImportedHrRuntimeState()
            return
        }

        if (state.segmentType == null) {
            return
        }

        if (state.segmentType != RunnerSegmentType.HEART_RATE_STEADY) {
            if (importedHrRuntimeController != null) {
                clearImportedHrRuntimeState()
                uiState.workoutExecutionModeMessage.value = null
                uiState.workoutExecutionModeIsError.value = false
            }
            return
        }

        val activeStep = selectedImportedWorkout.steps
            .getOrNull(state.sourceStepIndex ?: -1) as? ImportedErgoWorkoutStep.HeartRateSteady
            ?: run {
                clearImportedHrRuntimeState()
                return
            }

        if (importedHrRuntimeActiveSourceStepIndex == activeStep.stepIndex && importedHrRuntimeController != null) {
            return
        }

        if (!prepareImportedHrRuntimeStartIfNeeded(selectedImportedWorkout, activeStep)) {
            stopWorkout(clearLastTargetPower = false)
        }
    }

    /**
     * Captures the bounded signal-loss path that would be taken from the current
     * active HR runtime state if the resolved HR source were treated as missing.
     */
    private fun importedHrSignalLossIntent(): ImportedHrSignalLossIntent? {
        val controller = importedHrRuntimeController ?: return null
        return when (val state = controller.state()) {
            is ImportedHrRuntimeState.Running -> ImportedHrSignalLossIntent(
                event = ImportedHrRuntimeEvent.HR_SIGNAL_LOST.name,
                targetState = ImportedHrRuntimeState.Fallback::class.java.simpleName,
                commands = "SetPower(${state.policy.resolvedSignalLossPowerWatts()}),BlockIncrease",
            )

            is ImportedHrRuntimeState.Fallback -> ImportedHrSignalLossIntent(
                event = ImportedHrRuntimeEvent.HR_SIGNAL_LOSS_FALLBACK_APPLIED.name,
                targetState = ImportedHrRuntimeState.Stopped::class.java.simpleName,
                commands = "StopWorkout",
            )

            else -> null
        }
    }

    private data class ImportedHeartRateSnapshot(
        val selectedHeartRateBpm: Int?,
        val selectedSource: String,
        val ftmsHeartRateBpm: Int?,
        val externalHeartRateBpm: Int?,
    )

    private data class ImportedHrSignalLossIntent(
        val event: String,
        val targetState: String,
        val commands: String,
    )

    private fun handleRunnerCreationBlocked() {
        stopWorkout()
        stopMockTrainerEngine()
        workoutRunner = null
        sessionManager.stopSession()
        uiState.workoutReady.value = false
        uiState.pendingCadenceStartAfterControlGranted = false
        uiState.autoPausedByZeroCadence = false

        val wasSessionFlowActive =
            uiState.screen.value == AppScreen.CONNECTING || uiState.screen.value == AppScreen.SESSION
        if (wasSessionFlowActive) {
            allowScreenOff()
            uiState.screen.value = AppScreen.MENU
        }

        bleClient?.close()
        dumpUiState("handleRunnerCreationBlocked")
    }

    /**
     * Creates a fresh FTMS command controller bound to the active BLE client.
     */
    private fun createFtmsController(): FtmsController {
        return FtmsController(
            writeControlPoint = { payload ->
                bleClient?.writeControlPoint(payload) == true
            },
            onStopAcknowledged = {
                mainThreadHandler.post {
                    if (postWorkoutTelemetryTransitionSoftStopInProgress) {
                        postWorkoutTelemetryTransitionSoftStopInProgress = false
                        startPostWorkoutTelemetryReconnect()
                        dumpUiState("onPostWorkoutTelemetrySoftStopAcknowledged")
                    } else if (postWorkoutCompletionExitSoftStopInProgress) {
                        logTestMarker(event = "post_workout_exit_prep_soft_stop_acknowledged")
                        beginPostWorkoutCompletionExplicitClose()
                        dumpUiState("onPostWorkoutCompletionExitSoftStopAcknowledged")
                    } else {
                        stopFlowTerminalCallbacks.onStopAcknowledged()
                        dumpUiState("onStopAcknowledged")
                    }
                }
            },
            onCommandTimeout = { requestOpcode ->
                mainThreadHandler.post {
                    handleFtmsCommandTimeout(requestOpcode)
                }
            },
            onCommandLifecycleEvent = { commandEvent ->
                mainThreadHandler.post {
                    recordFtmsCommandDiagnostics(commandEvent)
                }
            },
            onUnexpectedControlPointResponse = { expectedOpcode, receivedOpcode, resultCode, reason ->
                mainThreadHandler.post {
                    Log.w(
                        "FTMS",
                        "Unexpected control-point response reason=$reason expected=$expectedOpcode received=$receivedOpcode result=$resultCode",
                    )
                    if (reason == FtmsUnexpectedControlPointResponseReason.OPCODE_MISMATCH) {
                        dumpUiState(
                            "onUnexpectedControlPointResponseMismatch(expected=$expectedOpcode,received=$receivedOpcode,result=$resultCode)",
                        )
                    } else {
                        dumpUiState(
                            "onUnexpectedControlPointResponseNoInFlight(received=$receivedOpcode,result=$resultCode)",
                        )
                    }
                }
            }
        )
    }

    private fun handleFtmsCommandTimeout(requestOpcode: Int?) {
        if (requestOpcode == requestControlOpcode) {
            if (postWorkoutCompletionExitAwaitingControl) {
                logTestMarker(event = "post_workout_exit_prep_request_control_timeout")
                evaluatePostWorkoutReleaseRamp(stage = "completion_prep_request_control_timeout")
                beginPostWorkoutCompletionExplicitClose()
                dumpUiState("postWorkoutCompletionExitRequestControlTimeout")
                return
            }
            if (postWorkoutTelemetryTransitionAwaitingControl) {
                evaluatePostWorkoutReleaseRamp(stage = "continue_ride_request_control_timeout")
                completePostWorkoutTelemetryTransition(
                    preserveControlGranted = false,
                    dumpReason = "postWorkoutTelemetryRequestControlTimeout",
                )
                return
            }
            if (postWorkoutTelemetryAwaitingReconnectControl) {
                completePostWorkoutTelemetryTransition(
                    preserveControlGranted = false,
                    dumpReason = "postWorkoutTelemetryReconnectControlTimeout",
                )
                return
            }
            recordExternalTrainerControlRequestOutcome(
                outcome = ExternalTrainerControlRequestOutcome.FAILED,
            )
            val failure = AppFailureFactory.sessionRequestControlTimeout()
            requestControlFailureAdapter.onRequestControlFailure(
                message = toUserMessage(failure),
                reason = failure.reason.stableCode,
            )
        } else if (requestOpcode == resetOpcode) {
            freeRideControlCoordinator.onFreeRideResetTimeout(
                ftmsReady = uiState.ftmsReady.value,
                controlGranted = uiState.ftmsControlGranted.value,
            )
        } else if (requestOpcode == setTargetPowerOpcode) {
            pendingAppControlledTargetPowerW = null
        } else if (requestOpcode == stopOpcode && postWorkoutCompletionExitSoftStopInProgress) {
            logTestMarker(event = "post_workout_exit_prep_soft_stop_timeout")
            beginPostWorkoutCompletionExplicitClose()
            dumpUiState("postWorkoutCompletionExitSoftStopTimeout")
        } else if (requestOpcode == stopOpcode && postWorkoutTelemetryTransitionSoftStopInProgress) {
            postWorkoutTelemetryTransitionSoftStopInProgress = false
            startPostWorkoutTelemetryReconnect()
            dumpUiState("postWorkoutTelemetrySoftStopTimeout")
        }
        dumpUiState("onCommandTimeout(op=$requestOpcode)")
    }

    private fun recordExternalTrainerControlRequestOutcome(
        outcome: ExternalTrainerControlRequestOutcome,
    ) {
        if (!externalTrainerControlRequestPending) return
        externalTrainerControlRequestPending = false
        externalTrainerControlRequestOutcome = outcome
    }

    private fun rollbackToMenuWithConnectionIssue(
        message: String,
        reason: String,
        suggestTrainerSearch: Boolean,
        suggestOpenSettings: Boolean,
    ) {
        rollbackToMenuRecoverySideEffects.rollbackToMenu(
            message = message,
            reason = reason,
            suggestTrainerSearch = suggestTrainerSearch,
            suggestOpenSettings = suggestOpenSettings,
        )
    }

    /**
     * Finalizes an already-started session after trainer control recovery failed.
     *
     * Why this is separate from rollback:
     * - Once `SESSION` was reached, the user has an active ride that should be summarized.
     * - Mid-session recovery failure must preserve explicit stop/finalization semantics
     *   rather than collapsing back into the start-flow MENU rollback path.
     */
    private fun finalizeSessionToSummaryWithConnectionIssue(
        message: String,
        reason: String,
        suggestTrainerSearch: Boolean,
        suggestOpenSettings: Boolean,
    ) {
        if (stopFlowPolicy.isStopFlowInProgress()) {
            Log.d(
                "FTMS",
                "Ignoring mid-session request-control failure while stop flow is already active: $reason",
            )
            return
        }
        uiState.connectionIssueMessage.value = message
        uiState.suggestTrainerSearchAfterConnectionIssue.value = suggestTrainerSearch
        uiState.suggestOpenSettingsAfterConnectionIssue.value = suggestOpenSettings

        recordDiagnostics(
            category = "session",
            event = "request_control_failure_to_stop_flow",
            context = mapOf("reason" to reason),
        )
        beginStopFlow(
            dumpReason = "finalizeSessionToSummaryWithConnectionIssue(reason=$reason)",
            mockDumpReason = "finalizeSessionToSummaryWithConnectionIssue(mock,reason=$reason)",
        )
    }

    private fun beginStopFlow(
        dumpReason: String,
        mockDumpReason: String,
        transportClosePolicy: StopFlowTransportClosePolicy = StopFlowTransportClosePolicy.CLOSE_AFTER_COMPLETION,
    ) {
        ensureSessionScope(reason = "stop_requested_without_active_scope")
        stopFlowTransportClosePolicy = transportClosePolicy
        cancelConnectFlowTimeout()
        cancelMockConnectTransition()
        stopMockTrainerEngine()
        stopWorkout()
        workoutRunner = null
        uiState.connectingTimeoutMessage.value = null
        stopFlowPolicy.enterStoppingAwaitAck()
        uiState.screen.value = AppScreen.STOPPING

        if (isMockTrainerModeEnabled()) {
            resetFtmsUiState(clearReady = true)
            stopFlowTerminalCallbacks.onMockStopFlowImmediate()
            dumpUiState(mockDumpReason)
            return
        }

        stopFlowPolicy.startStopFlowTimeout()
        sendStopForStopFlow()
        dumpUiState(dumpReason)
    }

    /**
     * Records when stop flow intentionally keeps the FTMS transport open after SUMMARY.
     *
     * This gives live validation a stable breadcrumb for the reversible policy split without
     * expanding the trainer-release teardown state machine itself.
     */
    private fun handleStopFlowBleCloseSkipped(reason: String) {
        recordDiagnostics(
            category = "session",
            event = "stop_flow_transport_close_skipped",
            context = mapOf(
                "reason" to reason,
                "transportClosePolicy" to stopFlowTransportClosePolicy.wireName,
            ),
        )
        stopFlowTransportClosePolicy = StopFlowTransportClosePolicy.CLOSE_AFTER_COMPLETION
        dumpUiState("handleStopFlowBleCloseSkipped(reason=$reason)")
    }

    /**
     * Test hook for deterministic start-flow verification without BLE runtime dependencies.
     */
    internal fun simulateRequestControlGrantedForTest() {
        seedInFlightCommandOpcodeForTest(requestOpcode = requestControlOpcode)
        handleControlPointResponseFromBle(
            requestOpcode = requestControlOpcode,
            resultCode = controlResponseSuccessCode,
        )
    }

    /**
     * Test hook for request-control rejection rollback behavior.
     */
    internal fun simulateRequestControlRejectedForTest(resultCode: Int) {
        seedInFlightCommandOpcodeForTest(requestOpcode = requestControlOpcode)
        handleControlPointResponseFromBle(
            requestOpcode = requestControlOpcode,
            resultCode = resultCode,
        )
    }

    /**
     * Test hook for request-control timeout rollback behavior.
     */
    internal fun simulateRequestControlTimeoutForTest() {
        handleFtmsCommandTimeout(requestOpcode = requestControlOpcode)
    }

    /**
     * Test hook for exercising BLE control-point callback handling without transport.
     */
    internal fun simulateBleControlPointResponseForTest(
        requestOpcode: Int,
        resultCode: Int,
        linkGeneration: Int = activeFtmsControlLinkGeneration,
    ) {
        handleControlPointResponseFromBle(
            requestOpcode = requestOpcode,
            resultCode = resultCode,
            linkGeneration = linkGeneration,
        )
    }

    /**
     * Test hook for deterministic opcode-mismatch paths with a seeded in-flight command.
     */
    internal fun seedInFlightCommandOpcodeForTest(requestOpcode: Int) {
        ftmsController.seedInFlightCommandForTest(requestOpcode)
    }

    /**
     * Test hook for runner target routing without depending on a live WorkoutRunner.
     */
    internal fun simulateRunnerTargetWriteForTest(targetWatts: Int?) {
        handleRunnerTargetWrite(targetWatts)
    }

    /**
     * Test hook for FTMS command-pipeline assertions during orchestration regressions.
     */
    internal fun inFlightFtmsRequestOpcodeForTest(): Int? =
        ftmsController.inFlightRequestOpcodeForTest()

    /**
     * Test hook for forcing FTMS control-link generation ownership checks.
     */
    internal fun setControlLinkGenerationForTest(linkGeneration: Int) {
        activeFtmsControlLinkGeneration = linkGeneration.coerceAtLeast(0)
        ftmsController.onControlLinkGenerationChanged(activeFtmsControlLinkGeneration)
    }

    /**
     * Test hook that enters CONNECTING and arms the connect-phase watchdog.
     */
    internal fun beginConnectFlowForTest() {
        enterConnectingState()
    }

    /**
     * Test hook for simulating a disconnect callback with an explicit FTMS callback generation.
     */
    internal fun simulateFtmsDisconnectedForTest(generation: Int) {
        handleBleDisconnected(generation)
    }

    /**
     * Test hook for asserting callback-generation invalidation behavior across session starts.
     */
    internal fun activeFtmsGenerationForTest(): Int = activeFtmsClientGeneration

    /**
     * Test hook for explicit-close settle-window assertions without reaching into private state.
     */
    internal fun explicitTrainerReconnectDelayRemainingForTest(): Long =
        remainingExplicitTrainerReconnectDelayMs()

    /**
     * Test hook for explicit-close state assertions without reflection.
     */
    internal fun explicitTrainerCloseInProgressForTest(): Boolean = explicitTrainerCloseInProgress

    /**
     * Test hook for the completion-summary readiness gate without reflection.
     */
    internal fun preparedPostWorkoutSummaryWindowOpenForTest(): Boolean =
        preparedPostWorkoutSummaryWindowOpen()

    /**
     * Test hook for asserting whether start flow can reuse the current prepared FTMS transport.
     */
    internal fun preparedTrainerConnectionReusableForTest(): Boolean = canReusePreparedTrainerConnection()

    /**
     * Test hook for deterministic indoor-bike parsing scheduling assertions.
     */
    internal fun scheduleIndoorBikeParsingForTest(
        payload: ByteArray,
        generation: Int = activeFtmsClientGeneration,
    ) {
        scheduleIndoorBikeParsing(payload = payload, generation = generation)
    }

    /**
     * Test hook for routing indoor-bike payloads through the normal parsing pipeline.
     */
    internal fun simulateIndoorBikeDataForTest(
        payload: ByteArray,
        generation: Int = activeFtmsClientGeneration,
    ) {
        scheduleIndoorBikeParsing(payload = payload, generation = generation)
    }

    /**
     * Test hook for the cadence-driven session-activity gate without FTMS parsing indirection.
     */
    internal fun applyCadenceDrivenRunnerControlForTest(cadenceRpm: Double?) {
        applyCadenceDrivenRunnerControl(cadenceRpm = cadenceRpm)
    }

    /**
     * Test hook for asserting session diagnostics scope lifecycle.
     */
    internal fun activeSessionIdForTest(): String? = activeSessionId

    /**
     * Test hook for BLE ready callback without BLE transport.
     *
     * Returns `true` when the onReady path would have triggered a `requestControl()` call.
     */
    internal fun simulateBleReadyForTest(controlPointReady: Boolean, linkGeneration: Int = activeFtmsControlLinkGeneration): Boolean {
        activeFtmsControlLinkGeneration = linkGeneration
        ftmsController.onControlLinkGenerationChanged(linkGeneration)
        uiState.ftmsReady.value = controlPointReady
        ftmsController.setTransportReady(controlPointReady)
        if (!controlPointReady) {
            sessionManager.pauseSessionActivity()
            ftmsController.onDisconnected()
        }
        if (controlPointReady && externalTrainerPreparationState == ExternalTrainerPreparationState.PENDING) {
            externalTrainerPreparationState = ExternalTrainerPreparationState.READY
            cancelExternalTrainerPreparationTimeout()
        }
        val shouldRequestReconnectControl =
            uiState.screen.value == AppScreen.SESSION &&
                (postWorkoutTelemetryReconnectInProgress || postWorkoutTelemetryAwaitingReconnectControl)
        val shouldBypassControlRequestForTelemetryOnlyStart =
            controlPointReady &&
                uiState.screen.value == AppScreen.CONNECTING &&
                telemetryOnlyStartShouldBypassControlRequest
        if (shouldBypassControlRequestForTelemetryOnlyStart) {
            telemetryOnlyStartShouldBypassControlRequest = false
            completeConnectingToSession(
                dumpReason = "transitionFromConnectingToTelemetryOnlySessionWithoutControlRequest(test)",
            )
            syncSessionActivityState()
            dumpUiState("bleOnReady(test)")
            return false
        }
        val wouldRequestControl = controlPointReady &&
            (
                uiState.screen.value == AppScreen.CONNECTING ||
                    shouldRequestReconnectControl ||
                    (
                        uiState.screen.value == AppScreen.SESSION &&
                            !uiState.ftmsControlGranted.value &&
                            shouldRequestSessionControlOnReady()
                        )
                )
        if (wouldRequestControl && (postWorkoutTelemetryReconnectInProgress || postWorkoutTelemetryAwaitingReconnectControl)) {
            postWorkoutTelemetryReconnectInProgress = false
            postWorkoutTelemetryAwaitingReconnectControl = true
        }
        syncSessionActivityState()
        dumpUiState("bleOnReady(test)")
        return wouldRequestControl
    }

    /**
     * Test hook for the post-workout reconnect leg without invoking platform BLE APIs.
     *
     * JVM unit tests cannot exercise real `BluetoothAdapter` validation, so this mirrors the
     * state observed after the disconnect callback has spawned a fresh FTMS client and before
     * the new link reports Control Point readiness.
     */
    internal fun simulatePostWorkoutTelemetryReconnectReadyForTest(
        linkGeneration: Int = activeFtmsControlLinkGeneration,
    ): Boolean {
        postWorkoutTelemetryReconnectInProgress = false
        postWorkoutTelemetryAwaitingReconnectControl = false
        postWorkoutFreshTelemetryStartPending = true
        uiState.screen.value = AppScreen.CONNECTING
        uiState.ftmsReady.value = false
        uiState.ftmsControlGranted.value = false
        return simulateBleReadyForTest(
            controlPointReady = true,
            linkGeneration = linkGeneration,
        )
    }

    /**
     * Test hook for BLE control ownership change without BLE transport.
     */
    internal fun simulateControlOwnershipChangedForTest(controlGranted: Boolean) {
        uiState.ftmsControlGranted.value = controlGranted
        freeRideControlCoordinator.onControlOwnershipChanged(
            controlGranted = controlGranted,
            ftmsReady = uiState.ftmsReady.value,
        )
        processImportedHrRuntimeTelemetry(source = "control_ownership_changed_test")
        syncSessionActivityState()
        dumpUiState("bleOnControlOwnershipChanged(test, granted=$controlGranted)")
    }

    /**
     * Test hook for deterministic stop-flow completion without waiting for BLE callbacks.
     */
    internal fun simulateStopAcknowledgedForTest() {
        stopFlowTerminalCallbacks.onStopAcknowledgedForTest()
    }

    /**
     * Test hook for explicit stop-flow requests without UI interaction.
     */
    internal fun requestStopForTest() {
        endSessionAndGoToSummary()
    }

    private fun ensureSessionScope(reason: String): String {
        val existing = activeSessionId
        if (existing != null) return existing
        val recovered = sessionIdFactory()
        activeSessionId = recovered
        recordDiagnostics(
            category = "session",
            event = "scope_recovered",
            sessionId = recovered,
            context = mapOf("reason" to reason),
        )
        return recovered
    }

    private fun beginSessionScope(entryPoint: String) {
        val replacedSessionId = activeSessionId
        val newSessionId = sessionIdFactory()
        activeSessionId = newSessionId
        val context = mutableMapOf("entryPoint" to entryPoint)
        if (replacedSessionId != null) {
            context["replacedSessionId"] = replacedSessionId
        }
        recordDiagnostics(
            category = "session",
            event = "scope_started",
            sessionId = newSessionId,
            context = context,
        )
    }

    private fun endSessionScope(reason: String) {
        val endedSessionId = activeSessionId ?: return
        recordDiagnostics(
            category = "session",
            event = "scope_ended",
            sessionId = endedSessionId,
            context = mapOf("reason" to reason),
        )
        activeSessionId = null
    }

    private fun recordFtmsCommandDiagnostics(commandEvent: FtmsCommandLifecycleEvent) {
        val stage = when (commandEvent.stage) {
            FtmsCommandLifecycleStage.SENT -> "sent"
            FtmsCommandLifecycleStage.RESPONSE -> "response"
            FtmsCommandLifecycleStage.TIMEOUT -> "timeout"
            FtmsCommandLifecycleStage.UNEXPECTED_RESPONSE -> "unexpected_response"
        }
        val context = linkedMapOf<String, String>()
        commandEvent.correlationId?.let { context["correlationId"] = it }
        commandEvent.requestOpcode?.let { context["requestOpcode"] = formatOpcode(it) }
        commandEvent.expectedOpcode?.let { context["expectedOpcode"] = formatOpcode(it) }
        commandEvent.receivedOpcode?.let { context["receivedOpcode"] = formatOpcode(it) }
        commandEvent.resultCode?.let { context["resultCode"] = formatOpcode(it) }
        commandEvent.unexpectedReason?.let { context["unexpectedReason"] = it.name }
        recordDiagnostics(
            category = "ftms_command",
            event = stage,
            context = context,
        )
    }

    private fun recordIndoorBikeParseFailureDiagnostics(failure: IndoorBikeParseFailure) {
        val context = linkedMapOf(
            "reason" to failure.reason.name,
            "exceptionType" to failure.exceptionType,
            "payloadLength" to failure.payloadLength.toString(),
            "payloadPreviewHex" to failure.payloadPreviewHex,
        )
        failure.flags?.let { context["flags"] = formatOpcode(it) }
        recordDiagnostics(
            category = "ftms_parser",
            event = "parse_failed",
            context = context,
        )
    }

    private fun formatOpcode(value: Int): String {
        return "0x" + value.toString(16).padStart(2, '0')
    }

    private fun recordDiagnostics(
        category: String,
        event: String,
        sessionId: String? = activeSessionId,
        context: Map<String, String> = emptyMap(),
    ) {
        val contextPayload = context.entries.joinToString(separator = " ") { (key, value) ->
            "$key=$value"
        }
        val sessionLabel = sessionId ?: "none"
        val suffix = if (contextPayload.isEmpty()) "" else " $contextPayload"
        AppLog.telemetryDebug("FTMS") {
            "SESSION_DIAG category=$category event=$event sessionId=$sessionLabel$suffix"
        }
        recordDiagnosticsEvent(
            SessionDiagnosticsEvent(
                timestampMillis = diagnosticsNowMillis(),
                sessionId = sessionId,
                category = category,
                event = event,
                context = context,
            ),
        )
    }

    private fun logTestMarker(
        event: String,
        context: Map<String, String> = emptyMap(),
    ) {
        AppLog.testMarker(
            event = event,
            context = context + mapOf("screen" to uiState.screen.value.name),
        )
    }

    private fun dumpUiState(event: String) {
        val lifecycleState = currentSessionLifecycleState()
        val releaseSummary = currentTrainerReleaseDebugSummary()
        AppLog.telemetryDebug("FTMS") {
            "UI_DUMP event=$event lifecycleState=$lifecycleState screen=${uiState.screen.value} " +
                "ready=${uiState.ftmsReady.value} controlGranted=${uiState.ftmsControlGranted.value} " +
                "postWorkoutFreeride=${uiState.postWorkoutFreerideModeActive} " +
                "lastTarget=${uiState.lastTargetPower.value} " +
                "authority=${uiState.trainerControlAuthority.value} " +
                "lastAppControlledTarget=${uiState.lastAppControlledTargetPower.value} " +
                "runnerState=${uiState.runner.value} " +
                "stopFlowState=${uiState.stopFlowState.value} " +
                "executionMessage=${uiState.workoutExecutionModeMessage.value} " +
                "executionMessageIsError=${uiState.workoutExecutionModeIsError.value} " +
                "pendingSessionStart=${uiState.pendingSessionStartAfterPermission} " +
                "pendingCadenceStart=${uiState.pendingCadenceStartAfterControlGranted} " +
                "autoPausedByZeroCadence=${uiState.autoPausedByZeroCadence} " +
                "release={$releaseSummary}"
        }
    }

    /**
     * Evaluates the current release-ramp decision while emitting stable diagnostics.
     *
     * The decider now steers production teardown, but the `evaluate` / `decision` trace payloads
     * still stay on this dedicated seam so live trainer analysis does not depend on branch-specific
     * logging scattered through the session flow.
     */
    private fun evaluatePostWorkoutReleaseRamp(
        stage: String,
        controlGrantedOverride: Boolean? = null,
    ): ReleaseRampDecision {
        val context = createPostWorkoutReleaseContext(
            controlGrantedOverride = controlGrantedOverride,
        )
        return ReleaseRampDecider(
            policy = activeTrainerReleaseProfile.releasePolicy,
            traceEmitter = ReleaseRampTraceEmitter { event ->
                recordDiagnostics(
                    category = "release_ramp",
                    event = when (event) {
                        is ReleaseRampTraceEvent.Evaluate -> "evaluate"
                        is ReleaseRampTraceEvent.Decision -> "decision"
                    },
                    context = event.context() + mapOf(
                        "stage" to stage,
                        "screen" to uiState.screen.value.name,
                    ),
                )
            },
        ).decide(context)
    }

    /**
     * Captures the current `Continue ride` release facts from app-owned state only.
     *
     * Cadence remains the risk signal, but the known app target stays separate from live power so
     * rider-controlled telemetry does not get reinterpreted as app-owned load authority.
     */
    private fun createPostWorkoutReleaseContext(
        controlGrantedOverride: Boolean? = null,
    ): ReleaseContext {
        val bikeData = uiState.bikeData.value
        return ReleaseContext(
            intent = ReleaseIntent.CONTINUE_RIDE_HANDOFF,
            authority = uiState.trainerControlAuthority.value,
            disconnectRequired = !isMockTrainerModeEnabled() && uiState.ftmsReady.value,
            ftmsReady = uiState.ftmsReady.value,
            ftmsControlGranted = controlGrantedOverride ?: uiState.ftmsControlGranted.value,
            cadenceRpm = bikeData?.instantaneousCadenceRpm?.roundToInt(),
            instantaneousPowerW = bikeData?.instantaneousPowerW,
            knownAppTargetPowerW = uiState.lastAppControlledTargetPower.value,
        )
    }

    private fun shouldShowTrainerReleaseOverlaySummary(): Boolean {
        return uiState.ftmsReady.value ||
            uiState.ftmsControlGranted.value ||
            uiState.postWorkoutFreerideModeActive ||
            postWorkoutCompletionExitArmed ||
            postWorkoutCompletionExitAwaitingControl ||
            postWorkoutCompletionExitSoftStopInProgress ||
            explicitTrainerCloseInProgress ||
            postWorkoutTelemetryTransitionAwaitingControl ||
            postWorkoutTelemetryTransitionSoftStopInProgress ||
            postWorkoutTelemetryReconnectInProgress ||
            postWorkoutTelemetryAwaitingReconnectControl ||
            postWorkoutFreshTelemetryStartPending ||
            postWorkoutReleaseRampPlan != null
    }

    private fun currentTrainerReleaseDebugSummary(): String {
        return currentTrainerReleaseDebugContext().entries.joinToString(separator = " ") { (key, value) ->
            "$key=$value"
        }
    }

    private fun currentTrainerReleaseDebugContext(
        policyIntent: ReleaseIntent = ReleaseIntent.CONTINUE_RIDE_HANDOFF,
        nowElapsedMs: Long = elapsedRealtimeMsProvider(),
    ): Map<String, String> {
        val bikeData = uiState.bikeData.value
        val reconnectDelayRemainingMs = remainingExplicitTrainerReconnectDelayMs(nowElapsedMs)
        val summaryWindowOpen = preparedPostWorkoutSummaryWindowOpen(
            reconnectDelayRemainingMs = reconnectDelayRemainingMs,
        )
        val restartWindowOpen = continueRideRestartWindowOpen(
            reconnectDelayRemainingMs = reconnectDelayRemainingMs,
        )
        return linkedMapOf(
            "policyIntent" to policyIntent.name,
            "flowState" to currentTrainerReleaseFlowStateLabel(
                reconnectDelayRemainingMs = reconnectDelayRemainingMs,
                restartWindowOpen = restartWindowOpen,
            ),
            "authority" to uiState.trainerControlAuthority.value.name,
            "disconnectRequired" to (!isMockTrainerModeEnabled() && uiState.ftmsReady.value).toString(),
            "ftmsReady" to uiState.ftmsReady.value.toString(),
            "ftmsControlGranted" to uiState.ftmsControlGranted.value.toString(),
            "postWorkoutFreerideModeActive" to uiState.postWorkoutFreerideModeActive.toString(),
            "releaseRampActive" to (postWorkoutReleaseRampPlan != null).toString(),
            "releaseRampOwner" to currentPostWorkoutReleaseRampOwnerLabel(),
            "completionExitArmed" to postWorkoutCompletionExitArmed.toString(),
            "completionExitAwaitingControl" to postWorkoutCompletionExitAwaitingControl.toString(),
            "completionExitSoftStopInProgress" to postWorkoutCompletionExitSoftStopInProgress.toString(),
            "telemetryTransitionAwaitingControl" to postWorkoutTelemetryTransitionAwaitingControl.toString(),
            "telemetryTransitionSoftStopInProgress" to postWorkoutTelemetryTransitionSoftStopInProgress.toString(),
            "telemetryReconnectInProgress" to postWorkoutTelemetryReconnectInProgress.toString(),
            "telemetryAwaitingReconnectControl" to postWorkoutTelemetryAwaitingReconnectControl.toString(),
            "freshTelemetryStartPending" to postWorkoutFreshTelemetryStartPending.toString(),
            "explicitCloseInProgress" to explicitTrainerCloseInProgress.toString(),
            "summaryWindowOpen" to summaryWindowOpen.toString(),
            "reconnectDelayRemainingMs" to reconnectDelayRemainingMs.toString(),
            "restartWindowOpen" to restartWindowOpen.toString(),
            "cadenceRpm" to (bikeData?.instantaneousCadenceRpm?.roundToInt()?.toString() ?: "none"),
            "instantaneousPowerW" to (bikeData?.instantaneousPowerW?.toString() ?: "none"),
            "knownAppTargetPowerW" to (uiState.lastAppControlledTargetPower.value?.toString() ?: "none"),
            "lastVisibleTargetPowerW" to (uiState.lastTargetPower.value?.toString() ?: "none"),
            "mockTrainer" to isMockTrainerModeEnabled().toString(),
        )
    }

    private fun currentTrainerReleaseFlowStateLabel(
        reconnectDelayRemainingMs: Long,
        restartWindowOpen: Boolean,
    ): String {
        return when {
            postWorkoutReleaseRampPlan != null -> "release_ramp"
            postWorkoutCompletionExitAwaitingControl -> "completion_exit_awaiting_control"
            postWorkoutCompletionExitSoftStopInProgress -> "completion_exit_soft_stop"
            explicitTrainerCloseInProgress -> "explicit_close"
            postWorkoutTelemetryTransitionAwaitingControl -> "telemetry_awaiting_control"
            postWorkoutTelemetryTransitionSoftStopInProgress -> "telemetry_soft_stop"
            postWorkoutTelemetryReconnectInProgress -> "telemetry_reconnect_connecting"
            postWorkoutTelemetryAwaitingReconnectControl -> "telemetry_reconnect_awaiting_control"
            postWorkoutFreshTelemetryStartPending && reconnectDelayRemainingMs > 0L ->
                "telemetry_reconnect_settle"
            postWorkoutFreshTelemetryStartPending -> "telemetry_reconnect_pending"
            postWorkoutCompletionExitArmed && restartWindowOpen -> "restart_window_open"
            postWorkoutCompletionExitArmed -> "completion_exit_armed"
            uiState.postWorkoutFreerideModeActive -> "post_workout_freeride"
            else -> "idle"
        }
    }

    private fun currentPostWorkoutReleaseRampOwnerLabel(): String {
        return when (postWorkoutReleaseRampOwner) {
            PostWorkoutReleaseOwner.TELEMETRY_TRANSITION ->
                PostWorkoutReleaseOwner.TELEMETRY_TRANSITION.wireName

            PostWorkoutReleaseOwner.COMPLETION_EXIT_PREP ->
                PostWorkoutReleaseOwner.COMPLETION_EXIT_PREP.wireName

            null -> "none"
        }
    }

    private fun continueRideRestartWindowOpen(
        reconnectDelayRemainingMs: Long,
    ): Boolean {
        return !explicitTrainerCloseInProgress &&
            reconnectDelayRemainingMs == 0L &&
            !uiState.ftmsReady.value &&
            !uiState.ftmsControlGranted.value
    }

    private fun preparedPostWorkoutSummaryWindowOpen(
        reconnectDelayRemainingMs: Long,
    ): Boolean {
        return continueRideRestartWindowOpen(reconnectDelayRemainingMs) ||
            (
                postWorkoutCompletionExitArmed &&
                    postWorkoutReleaseRampPlan == null &&
                    !postWorkoutCompletionExitAwaitingControl &&
                    !postWorkoutCompletionExitSoftStopInProgress
                )
    }

    private fun recordTrainerReleaseRuntime(
        event: String,
        policyIntent: ReleaseIntent = ReleaseIntent.CONTINUE_RIDE_HANDOFF,
        extraContext: Map<String, String> = emptyMap(),
    ) {
        recordDiagnostics(
            category = "trainer_release_runtime",
            event = event,
            context = currentTrainerReleaseDebugContext(policyIntent = policyIntent) +
                mapOf("screen" to uiState.screen.value.name) +
                extraContext,
        )
    }

    /**
     * Remembers the latest app-owned target request until FTMS confirms that write.
     */
    private fun markAppControlledTargetRequested(targetWatts: Int) {
        pendingAppControlledTargetPowerW = targetWatts
    }

    /**
     * Promotes the latest confirmed app-owned target into release-policy state.
     */
    private fun markAppControlledTargetConfirmed(targetWatts: Int) {
        pendingAppControlledTargetPowerW = null
        uiState.trainerControlAuthority.value = TrainerControlAuthority.APP_CONTROLLED
        uiState.lastAppControlledTargetPower.value = targetWatts
    }

    /**
     * Uses the latest pending/visible target as the confirmed app-owned target after FTMS ACK.
     */
    private fun confirmPendingAppControlledTargetPower() {
        val confirmedTargetWatts =
            pendingAppControlledTargetPowerW ?: uiState.lastTargetPower.value ?: return
        markAppControlledTargetConfirmed(targetWatts = confirmedTargetWatts)
    }

    /**
     * Disconnect is the current authority boundary: once transport is gone, app-owned load
     * assumptions must be cleared before any later reconnect establishes a new control epoch.
     */
    private fun clearAppControlledTargetTracking() {
        pendingAppControlledTargetPowerW = null
        uiState.trainerControlAuthority.value = TrainerControlAuthority.RIDER_CONTROLLED
        uiState.lastAppControlledTargetPower.value = null
    }

    private fun currentSessionLifecycleState(): io.github.ewoc2026.ewoc.SessionLifecycleState {
        val runtimeState = uiState.sessionRuntimeUiState
        val recoveryState = uiState.connectionRecoveryUiState
        return when {
            runtimeState.stopFlowState.value == io.github.ewoc2026.ewoc.StopFlowState.STOPPING_AWAIT_ACK ||
                uiState.screen.value == AppScreen.STOPPING -> io.github.ewoc2026.ewoc.SessionLifecycleState.STOPPING

            uiState.screen.value == AppScreen.SUMMARY &&
                uiState.summary.value != null -> io.github.ewoc2026.ewoc.SessionLifecycleState.COMPLETED

            !recoveryState.connectionIssueMessageState.value.isNullOrBlank() ->
                io.github.ewoc2026.ewoc.SessionLifecycleState.FAILED

            runtimeState.pendingSessionStartAfterPermission ->
                io.github.ewoc2026.ewoc.SessionLifecycleState.PREPARING

            uiState.screen.value == AppScreen.CONNECTING &&
                runtimeState.ftmsReady.value &&
                !runtimeState.ftmsControlGranted.value ->
                io.github.ewoc2026.ewoc.SessionLifecycleState.AWAITING_CONTROL

            uiState.screen.value == AppScreen.CONNECTING ->
                io.github.ewoc2026.ewoc.SessionLifecycleState.CONNECTING

            uiState.screen.value == AppScreen.SESSION &&
                runtimeState.postWorkoutFreerideModeActive ->
                io.github.ewoc2026.ewoc.SessionLifecycleState.RUNNING

            uiState.screen.value == AppScreen.SESSION &&
                !runtimeState.ftmsControlGranted.value ->
                io.github.ewoc2026.ewoc.SessionLifecycleState.AWAITING_CONTROL

            uiState.screen.value == AppScreen.SESSION &&
                uiState.runner.value.running &&
                uiState.runner.value.paused ->
                io.github.ewoc2026.ewoc.SessionLifecycleState.PAUSED

            uiState.screen.value == AppScreen.SESSION ->
                io.github.ewoc2026.ewoc.SessionLifecycleState.RUNNING

            else -> io.github.ewoc2026.ewoc.SessionLifecycleState.IDLE
        }
    }
}

private fun ReleaseRampDecision.debugLabel(): String {
    return when (this) {
        ReleaseRampDecision.NoNeed -> "no_need"
        is ReleaseRampDecision.NotPossible -> "not_possible"
        is ReleaseRampDecision.Execute -> "execute"
    }
}

private fun ReleaseRampDecision.debugContext(): Map<String, String> {
    return when (this) {
        ReleaseRampDecision.NoNeed -> emptyMap()
        is ReleaseRampDecision.NotPossible -> mapOf("reason" to reason.name)
        is ReleaseRampDecision.Execute -> mapOf(
            "startTargetPowerW" to plan.startTargetPowerW.toString(),
            "endTargetPowerW" to plan.endTargetPowerW.toString(),
            "durationMs" to plan.durationMs.toString(),
            "tickMs" to plan.tickMs.toString(),
        )
    }
}

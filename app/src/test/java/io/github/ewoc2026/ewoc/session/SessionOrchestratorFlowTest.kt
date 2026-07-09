package io.github.ewoc2026.ewoc.session

import android.content.ContextWrapper
import io.github.ewoc2026.ewoc.AppScreen
import io.github.ewoc2026.ewoc.AppUiState
import io.github.ewoc2026.ewoc.SessionSetupMode
import io.github.ewoc2026.ewoc.StopFlowState
import io.github.ewoc2026.ewoc.ftms.IndoorBikeData
import io.github.ewoc2026.ewoc.session.release.TrainerControlAuthority
import io.github.ewoc2026.ewoc.session.diagnostics.SessionDiagnosticsEvent
import io.github.ewoc2026.ewoc.workout.ImportedErgoWorkout
import io.github.ewoc2026.ewoc.workout.ImportedErgoWorkoutCanonicalMetadata
import io.github.ewoc2026.ewoc.workout.ImportedErgoWorkoutControl
import io.github.ewoc2026.ewoc.workout.ImportedErgoWorkoutStep
import io.github.ewoc2026.ewoc.workout.Step
import io.github.ewoc2026.ewoc.workout.WorkoutFile
import io.github.ewoc2026.ewoc.workout.runner.RunnerSegmentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionOrchestratorFlowTest {
    @Test
    fun successfulExternalTargetWriteMarksAppControlledAuthority() {
        val harness = createHarness(
            ensureBluetoothPermission = { false },
        )
        harness.orchestrator.initialize()
        harness.orchestrator.simulateBleReadyForTest(controlPointReady = true)
        harness.uiState.ftmsControlGranted.value = true
        harness.uiState.lastTargetPower.value = 180

        harness.orchestrator.seedInFlightCommandOpcodeForTest(0x05)
        harness.orchestrator.simulateBleControlPointResponseForTest(
            requestOpcode = 0x05,
            resultCode = 0x01,
        )

        assertEquals(TrainerControlAuthority.APP_CONTROLLED, harness.uiState.trainerControlAuthority.value)
        assertEquals(180, harness.uiState.lastAppControlledTargetPower.value)
    }

    @Test
    fun disconnectClearsAppControlledAuthorityTracking() {
        val harness = createHarness(
            ensureBluetoothPermission = { false },
        )
        harness.orchestrator.initialize()
        val generation = harness.orchestrator.activeFtmsGenerationForTest()
        harness.orchestrator.simulateBleReadyForTest(controlPointReady = true)
        harness.uiState.ftmsControlGranted.value = true
        harness.uiState.lastTargetPower.value = 180

        harness.orchestrator.seedInFlightCommandOpcodeForTest(0x05)
        harness.orchestrator.simulateBleControlPointResponseForTest(
            requestOpcode = 0x05,
            resultCode = 0x01,
        )
        harness.orchestrator.simulateFtmsDisconnectedForTest(generation)

        assertEquals(TrainerControlAuthority.RIDER_CONTROLLED, harness.uiState.trainerControlAuthority.value)
        assertNull(harness.uiState.lastAppControlledTargetPower.value)
    }

    @Test
    fun prepareTrainerWarmConnectionInMenuMarksReadyWithoutClaimingControl() {
        val harness = createHarness(
            ensureBluetoothPermission = { false },
        )
        harness.orchestrator.initialize()
        harness.uiState.screen.value = AppScreen.MENU

        val accepted = harness.orchestrator.prepareTrainerWarmConnectionInMenu()
        val requestedControlOnReady = harness.orchestrator.simulateBleReadyForTest(controlPointReady = true)

        assertTrue(accepted)
        assertFalse(requestedControlOnReady)
        assertEquals(
            ExternalTrainerPreparationState.READY,
            harness.orchestrator.externalTrainerPreparationStateForExternalUse(),
        )
        assertEquals(AppScreen.MENU, harness.uiState.screen.value)
        assertTrue(harness.uiState.ftmsReady.value)
        assertFalse(harness.uiState.ftmsControlGranted.value)
        assertEquals(0, countSessionEvent(harness.diagnosticsEvents, "connect_ble_clients"))
    }

    @Test
    fun externalPreparationCanTakeOwnershipOfMatchingMenuWarmConnection() {
        val harness = createHarness(
            ensureBluetoothPermission = { false },
        )
        harness.orchestrator.initialize()

        assertTrue(harness.orchestrator.prepareTrainerWarmConnectionInMenu())
        harness.orchestrator.simulateBleReadyForTest(controlPointReady = true)

        val accepted = harness.orchestrator.prepareTrainerForExternalUse()

        assertTrue(accepted)
        assertEquals(
            ExternalTrainerPreparationState.READY,
            harness.orchestrator.externalTrainerPreparationStateForExternalUse(),
        )
        assertTrue(harness.uiState.ftmsReady.value)
        assertFalse(harness.uiState.ftmsControlGranted.value)
        assertEquals(0, countSessionEvent(harness.diagnosticsEvents, "connect_ble_clients"))
    }

    @Test
    fun externalControlRequestClaimsOwnershipOfMatchingMenuWarmConnection() {
        val harness = createHarness(
            ensureBluetoothPermission = { false },
        )
        harness.orchestrator.initialize()

        assertTrue(harness.orchestrator.prepareTrainerWarmConnectionInMenu())
        harness.orchestrator.simulateBleReadyForTest(controlPointReady = true)
        harness.uiState.ftmsControlGranted.value = true
        assertEquals("menu_warm", harness.orchestrator.trainerPreparationOwnerForDebug())

        val accepted = harness.orchestrator.requestTrainerControlForExternalUse()

        assertTrue(accepted)
        assertEquals("external_use", harness.orchestrator.trainerPreparationOwnerForDebug())
        assertEquals(
            ExternalTrainerPreparationState.READY,
            harness.orchestrator.externalTrainerPreparationStateForExternalUse(),
        )
        assertEquals(0, countSessionEvent(harness.diagnosticsEvents, "connect_ble_clients"))
    }

    @Test
    fun warmReprepareWaitsForExplicitDisconnectAndSettleWindow() {
        val manualHandler = ManualHandler()
        val harness = createHarness(
            ensureBluetoothPermission = { false },
            mainHandler = manualHandler,
        )
        harness.orchestrator.initialize()

        assertTrue(harness.orchestrator.prepareTrainerWarmConnectionInMenu())
        harness.orchestrator.simulateBleReadyForTest(controlPointReady = true)
        val initialConnectEvents = countSessionEvent(harness.diagnosticsEvents, "connect_ble_clients")
        val initialPermissionRequestId = harness.permissionRequestId
        val generation = harness.orchestrator.activeFtmsGenerationForTest()

        harness.orchestrator.releaseTrainerWarmConnectionInMenu()
        assertTrue(harness.orchestrator.prepareTrainerWarmConnectionInMenu())
        assertEquals(initialConnectEvents, countSessionEvent(harness.diagnosticsEvents, "connect_ble_clients"))
        assertEquals("none", harness.orchestrator.trainerPreparationOwnerForDebug())
        assertEquals(initialPermissionRequestId, harness.permissionRequestId)

        harness.orchestrator.simulateFtmsDisconnectedForTest(generation)
        manualHandler.advanceBy(1_000L)
        assertEquals(initialConnectEvents, countSessionEvent(harness.diagnosticsEvents, "connect_ble_clients"))
        assertEquals("none", harness.orchestrator.trainerPreparationOwnerForDebug())
        assertEquals(initialPermissionRequestId, harness.permissionRequestId)

        manualHandler.advanceBy(600L)
        assertEquals(initialConnectEvents, countSessionEvent(harness.diagnosticsEvents, "connect_ble_clients"))
        assertEquals("menu_warm", harness.orchestrator.trainerPreparationOwnerForDebug())
        assertEquals(initialPermissionRequestId + 1, harness.permissionRequestId)
    }

    @Test
    fun preparedTrainerConnectionBecomesReusableForSessionStartWithoutReconnect() {
        val harness = createHarness(
            ensureBluetoothPermission = { false },
        )
        harness.orchestrator.initialize()
        harness.uiState.selectedWorkout.value = readyWorkout()
        harness.uiState.workoutReady.value = true

        assertTrue(harness.orchestrator.prepareTrainerWarmConnectionInMenu())
        harness.orchestrator.simulateBleReadyForTest(controlPointReady = true)

        assertTrue(harness.orchestrator.preparedTrainerConnectionReusableForTest())
        assertEquals(0, countSessionEvent(harness.diagnosticsEvents, "connect_ble_clients"))
    }

    @Test
    fun continueRideAfterWorkoutCompleteSoftStopsIntoTelemetryOnlyWhenControlAlreadyHeld() {
        val manualHandler = ManualHandler()
        val ftmsMac = "AA:BB:CC:DD:EE:FF"
        val harness = createHarness(
            mainHandler = manualHandler,
            ensureBluetoothPermission = { true },
            currentFtmsDeviceMac = { ftmsMac },
        )
        harness.orchestrator.initialize()
        harness.uiState.selectedSessionSetupMode.value = SessionSetupMode.FILE
        harness.uiState.selectedWorkout.value = readyWorkout()
        harness.uiState.selectedWorkoutFileName.value = "session.ewo"
        harness.uiState.screen.value = AppScreen.SESSION
        harness.uiState.ftmsReady.value = true
        harness.uiState.ftmsControlGranted.value = true
        harness.uiState.runner.value = io.github.ewoc2026.ewoc.workout.runner.RunnerState.stopped(
            workoutElapsedSec = 600,
        )

        assertTrue(harness.orchestrator.continueRideAfterWorkoutComplete())
        val generation = harness.orchestrator.activeFtmsGenerationForTest()

        assertFalse(harness.uiState.postWorkoutFreerideModeActive)
        assertEquals(SessionSetupMode.TELEMETRY_ONLY, harness.uiState.selectedSessionSetupMode.value)
        assertNull(harness.uiState.selectedWorkout.value)
        assertNull(harness.uiState.selectedWorkoutFileName.value)
        assertTrue(harness.uiState.pendingCadenceStartAfterControlGranted)
        assertEquals(AppScreen.SESSION, harness.uiState.screen.value)

        harness.orchestrator.seedInFlightCommandOpcodeForTest(requestOpcode = 0x08)
        harness.orchestrator.simulateBleControlPointResponseForTest(
            requestOpcode = 0x08,
            resultCode = 0x01,
        )
        manualHandler.advanceBy(0L)
        assertTrue(harness.orchestrator.simulatePostWorkoutTelemetryReconnectReadyForTest())
        harness.orchestrator.simulateControlOwnershipChangedForTest(controlGranted = true)
        harness.orchestrator.simulateRequestControlGrantedForTest()

        assertFalse(harness.uiState.postWorkoutFreerideModeActive)
        assertEquals(SessionSetupMode.TELEMETRY_ONLY, harness.uiState.selectedSessionSetupMode.value)
        assertNull(harness.uiState.selectedWorkout.value)
        assertNull(harness.uiState.selectedWorkoutFileName.value)
        assertTrue(harness.uiState.pendingCadenceStartAfterControlGranted)
        assertEquals(AppScreen.SESSION, harness.uiState.screen.value)
        assertTrue(harness.uiState.ftmsReady.value)
        assertTrue(harness.uiState.ftmsControlGranted.value)
    }

    @Test
    fun telemetryOnlyReuseDisconnectRollsBackToMenuWhenLinkDrops() {
        var ftmsMac: String? = "AA:BB:CC:DD:EE:FF"
        val harness = createHarness(
            ensureBluetoothPermission = { false },
            currentFtmsDeviceMac = { ftmsMac },
        )
        harness.orchestrator.initialize()

        assertTrue(harness.orchestrator.prepareTrainerWarmConnectionInMenu())
        val generation = harness.orchestrator.activeFtmsGenerationForTest()
        harness.uiState.screen.value = AppScreen.SESSION
        harness.uiState.ftmsReady.value = true
        harness.uiState.ftmsControlGranted.value = true
        harness.uiState.runner.value = io.github.ewoc2026.ewoc.workout.runner.RunnerState.stopped(
            workoutElapsedSec = 900,
        )

        assertTrue(harness.orchestrator.continueRideAfterWorkoutComplete())
        ftmsMac = null
        harness.orchestrator.simulateFtmsDisconnectedForTest(generation)

        assertEquals(AppScreen.MENU, harness.uiState.screen.value)
        assertFalse(harness.uiState.postWorkoutFreerideModeActive)
        assertFalse(harness.uiState.ftmsReady.value)
        assertFalse(harness.uiState.ftmsControlGranted.value)
    }

    @Test
    fun continueRideAfterWorkoutCompleteRequestsControlThenSoftStopsIntoTelemetryOnly() {
        val manualHandler = ManualHandler()
        val ftmsMac = "AA:BB:CC:DD:EE:FF"
        val harness = createHarness(
            mainHandler = manualHandler,
            ensureBluetoothPermission = { true },
            currentFtmsDeviceMac = { ftmsMac },
        )
        harness.orchestrator.initialize()

        harness.uiState.selectedSessionSetupMode.value = SessionSetupMode.FILE
        harness.uiState.selectedWorkout.value = readyWorkout()
        harness.uiState.selectedWorkoutFileName.value = "session.ewo"
        harness.uiState.screen.value = AppScreen.SESSION
        harness.uiState.ftmsReady.value = true
        harness.uiState.ftmsControlGranted.value = false
        harness.uiState.runner.value = io.github.ewoc2026.ewoc.workout.runner.RunnerState.stopped(
            workoutElapsedSec = 900,
        )

        assertTrue(harness.orchestrator.continueRideAfterWorkoutComplete())
        assertEquals(SessionSetupMode.TELEMETRY_ONLY, harness.uiState.selectedSessionSetupMode.value)
        assertNull(harness.uiState.selectedWorkout.value)
        assertTrue(harness.uiState.pendingCadenceStartAfterControlGranted)

        harness.orchestrator.simulateControlOwnershipChangedForTest(controlGranted = true)
        harness.orchestrator.simulateRequestControlGrantedForTest()
        harness.orchestrator.seedInFlightCommandOpcodeForTest(requestOpcode = 0x08)
        harness.orchestrator.simulateBleControlPointResponseForTest(
            requestOpcode = 0x08,
            resultCode = 0x01,
        )
        manualHandler.advanceBy(0L)
        assertTrue(harness.orchestrator.simulatePostWorkoutTelemetryReconnectReadyForTest())
        harness.orchestrator.simulateControlOwnershipChangedForTest(controlGranted = true)
        harness.orchestrator.simulateRequestControlGrantedForTest()

        assertEquals(AppScreen.SESSION, harness.uiState.screen.value)
        assertFalse(harness.uiState.postWorkoutFreerideModeActive)
        assertEquals(SessionSetupMode.TELEMETRY_ONLY, harness.uiState.selectedSessionSetupMode.value)
        assertNull(harness.uiState.selectedWorkout.value)
        assertNull(harness.uiState.selectedWorkoutFileName.value)
        assertTrue(harness.uiState.pendingCadenceStartAfterControlGranted)
        assertTrue(harness.uiState.ftmsReady.value)
        assertTrue(harness.uiState.ftmsControlGranted.value)
    }

    @Test
    fun continueRideAfterWorkoutCompleteTelemetryOnlyContinuationStartsSessionActivity() {
        val manualHandler = ManualHandler()
        val ftmsMac = "AA:BB:CC:DD:EE:FF"
        val harness = createHarness(
            ensureBluetoothPermission = { true },
            mainHandler = manualHandler,
            currentFtmsDeviceMac = { ftmsMac },
        )
        harness.orchestrator.initialize()
        harness.sessionManager.startSession(ftpWatts = 250, startActive = false)

        harness.uiState.selectedSessionSetupMode.value = SessionSetupMode.FILE
        harness.uiState.selectedWorkout.value = readyWorkout()
        harness.uiState.selectedWorkoutFileName.value = "session.ewo"
        harness.uiState.screen.value = AppScreen.SESSION
        harness.uiState.ftmsReady.value = true
        harness.uiState.ftmsControlGranted.value = false
        harness.uiState.runner.value = io.github.ewoc2026.ewoc.workout.runner.RunnerState.stopped(
            workoutElapsedSec = 900,
        )

        assertTrue(harness.orchestrator.continueRideAfterWorkoutComplete())
        harness.orchestrator.simulateControlOwnershipChangedForTest(controlGranted = true)
        harness.orchestrator.simulateRequestControlGrantedForTest()
        harness.orchestrator.seedInFlightCommandOpcodeForTest(requestOpcode = 0x08)
        harness.orchestrator.simulateBleControlPointResponseForTest(
            requestOpcode = 0x08,
            resultCode = 0x01,
        )
        manualHandler.advanceBy(0L)
        assertTrue(harness.orchestrator.simulatePostWorkoutTelemetryReconnectReadyForTest())
        harness.orchestrator.simulateControlOwnershipChangedForTest(controlGranted = true)
        harness.orchestrator.simulateRequestControlGrantedForTest()
        harness.orchestrator.applyCadenceDrivenRunnerControlForTest(cadenceRpm = 85.0)

        assertFalse(harness.uiState.pendingCadenceStartAfterControlGranted)
        assertTrue(harness.uiState.ftmsControlGranted.value)
        assertEquals(AppScreen.SESSION, harness.uiState.screen.value)
        assertEquals(SessionPhase.RUNNING, harness.sessionManager.getPhase())
    }

    @Test
    fun continueRideAfterWorkoutCompletePreservesCumulativeTrainerMetricsAcrossResettingReconnect() {
        val manualHandler = ManualHandler()
        val ftmsMac = "AA:BB:CC:DD:EE:FF"
        val harness = createHarness(
            ensureBluetoothPermission = { true },
            mainHandler = manualHandler,
            currentFtmsDeviceMac = { ftmsMac },
        )
        harness.orchestrator.initialize()
        harness.sessionManager.startSession(ftpWatts = 250, startActive = true)
        harness.sessionManager.updateBikeData(
            bikeData(
                powerW = 190,
                distanceMeters = 149,
                totalEnergyKcal = 4,
            ),
        )

        harness.uiState.selectedSessionSetupMode.value = SessionSetupMode.FILE
        harness.uiState.selectedWorkout.value = readyWorkout()
        harness.uiState.selectedWorkoutFileName.value = "session.ewo"
        harness.uiState.screen.value = AppScreen.SESSION
        harness.uiState.ftmsReady.value = true
        harness.uiState.ftmsControlGranted.value = true
        harness.uiState.runner.value = io.github.ewoc2026.ewoc.workout.runner.RunnerState.stopped(
            workoutElapsedSec = 900,
        )

        assertTrue(harness.orchestrator.continueRideAfterWorkoutComplete())
        harness.orchestrator.seedInFlightCommandOpcodeForTest(requestOpcode = 0x08)
        harness.orchestrator.simulateBleControlPointResponseForTest(
            requestOpcode = 0x08,
            resultCode = 0x01,
        )
        manualHandler.advanceBy(0L)
        assertTrue(harness.orchestrator.simulatePostWorkoutTelemetryReconnectReadyForTest())
        harness.orchestrator.simulateControlOwnershipChangedForTest(controlGranted = true)
        harness.orchestrator.simulateRequestControlGrantedForTest()
        harness.orchestrator.applyCadenceDrivenRunnerControlForTest(cadenceRpm = 85.0)

        harness.sessionManager.updateBikeData(
            bikeData(
                powerW = 160,
                distanceMeters = 0,
                totalEnergyKcal = 0,
            ),
        )
        harness.sessionManager.updateBikeData(
            bikeData(
                powerW = 170,
                distanceMeters = 31,
                totalEnergyKcal = 2,
            ),
        )
        harness.sessionManager.stopSession()

        val summary = harness.sessionManager.lastSummary
        assertNotNull(summary)
        assertEquals(180, summary!!.distanceMeters)
        assertEquals(6, summary.totalEnergyKcal)
    }

    @Test
    fun prepareTrainerForExternalUseInMockModeKeepsBaselineScreenAndStreamsTelemetry() {
        val manualHandler = ManualHandler()
        var connectHeartRateCalls = 0
        val harness = createHarness(
            mainHandler = manualHandler,
            isMockTrainerModeEnabled = { true },
            connectHeartRate = { connectHeartRateCalls += 1 },
        )
        harness.orchestrator.initialize()
        harness.uiState.screen.value = AppScreen.BASELINE_FITNESS_TEST

        val accepted = harness.orchestrator.prepareTrainerForExternalUse()

        assertTrue(accepted)
        assertEquals(
            ExternalTrainerPreparationState.PENDING,
            harness.orchestrator.externalTrainerPreparationStateForExternalUse(),
        )
        assertEquals(AppScreen.BASELINE_FITNESS_TEST, harness.uiState.screen.value)

        manualHandler.advanceBy(0L)
        manualHandler.advanceBy(1_000L)

        assertEquals(
            ExternalTrainerPreparationState.READY,
            harness.orchestrator.externalTrainerPreparationStateForExternalUse(),
        )
        assertEquals(AppScreen.BASELINE_FITNESS_TEST, harness.uiState.screen.value)
        assertTrue(harness.uiState.ftmsReady.value)
        assertTrue(harness.uiState.ftmsControlGranted.value)
        assertNotNull(harness.uiState.bikeData.value)
        assertEquals(1, connectHeartRateCalls)
    }

    @Test
    fun startSessionConnectionInMockModeConnectsHeartRateBeforeImportedHrPreflight() {
        val manualHandler = ManualHandler()
        var connectHeartRateCalls = 0
        val harness = createHarness(
            mainHandler = manualHandler,
            isMockTrainerModeEnabled = { true },
            connectHeartRate = { connectHeartRateCalls += 1 },
        )
        harness.orchestrator.initialize()
        harness.uiState.selectedImportedWorkout.value = heartRateImportedWorkout()
        harness.uiState.workoutReady.value = true

        harness.orchestrator.startSessionConnection()

        assertEquals(1, connectHeartRateCalls)
        assertEquals(AppScreen.CONNECTING, harness.uiState.screen.value)
    }

    @Test
    fun permissionDeniedDuringExternalTrainerPreparationMarksFailureWithoutLeavingBaselineScreen() {
        val harness = createHarness(
            ensureBluetoothPermission = { false },
        )
        harness.orchestrator.initialize()
        harness.uiState.screen.value = AppScreen.BASELINE_FITNESS_TEST

        val accepted = harness.orchestrator.prepareTrainerForExternalUse()

        assertTrue(accepted)
        assertEquals(
            ExternalTrainerPreparationState.PENDING,
            harness.orchestrator.externalTrainerPreparationStateForExternalUse(),
        )

        harness.deliverPermissionResult(granted = false)

        assertEquals(
            ExternalTrainerPreparationState.FAILED,
            harness.orchestrator.externalTrainerPreparationStateForExternalUse(),
        )
        assertEquals(AppScreen.BASELINE_FITNESS_TEST, harness.uiState.screen.value)
        assertFalse(harness.uiState.ftmsReady.value)
        assertFalse(harness.uiState.ftmsControlGranted.value)
    }

    @Test
    fun requestControlRejectedRollsBackToMenuWithRecoveryPrompt() {
        val harness = createHarness()
        harness.orchestrator.initialize()
        harness.uiState.screen.value = AppScreen.CONNECTING
        harness.uiState.ftmsReady.value = true
        harness.uiState.ftmsControlGranted.value = true

        harness.orchestrator.simulateRequestControlRejectedForTest(resultCode = 0x05)

        assertEquals(AppScreen.MENU, harness.uiState.screen.value)
        assertEquals(StopFlowState.IDLE, harness.uiState.stopFlowState.value)
        assertFalse(harness.uiState.ftmsReady.value)
        assertFalse(harness.uiState.ftmsControlGranted.value)
        assertTrue(harness.uiState.suggestTrainerSearchAfterConnectionIssue.value)
        assertFalse(harness.uiState.suggestOpenSettingsAfterConnectionIssue.value)
        assertNotNull(harness.uiState.connectionIssueMessage.value)
        assertEquals(1, harness.currentAllowScreenOffCalls)
    }

    @Test
    fun requestControlTimeoutRollsBackToMenuWithRecoveryPrompt() {
        val harness = createHarness()
        harness.orchestrator.initialize()
        harness.uiState.screen.value = AppScreen.CONNECTING
        harness.uiState.ftmsReady.value = true
        harness.uiState.ftmsControlGranted.value = true

        harness.orchestrator.simulateRequestControlTimeoutForTest()

        assertEquals(AppScreen.MENU, harness.uiState.screen.value)
        assertEquals(StopFlowState.IDLE, harness.uiState.stopFlowState.value)
        assertFalse(harness.uiState.ftmsReady.value)
        assertFalse(harness.uiState.ftmsControlGranted.value)
        assertTrue(harness.uiState.suggestTrainerSearchAfterConnectionIssue.value)
        assertFalse(harness.uiState.suggestOpenSettingsAfterConnectionIssue.value)
        assertNotNull(harness.uiState.connectionIssueMessage.value)
        assertEquals(1, harness.currentAllowScreenOffCalls)
    }

    @Test
    fun requestControlRejectedDuringSessionTransitionsToStoppingWithRecoveryPrompt() {
        val harness = createHarness()
        harness.orchestrator.initialize()
        harness.uiState.screen.value = AppScreen.CONNECTING
        harness.orchestrator.simulateRequestControlGrantedForTest()

        harness.orchestrator.simulateRequestControlRejectedForTest(resultCode = 0x05)

        assertEquals(AppScreen.STOPPING, harness.uiState.screen.value)
        assertEquals(StopFlowState.STOPPING_AWAIT_ACK, harness.uiState.stopFlowState.value)
        assertFalse(harness.uiState.ftmsReady.value)
        assertFalse(harness.uiState.ftmsControlGranted.value)
        assertTrue(harness.uiState.suggestTrainerSearchAfterConnectionIssue.value)
        assertFalse(harness.uiState.suggestOpenSettingsAfterConnectionIssue.value)
        assertNotNull(harness.uiState.connectionIssueMessage.value)
        assertNull(harness.uiState.summary.value)
        assertEquals(0, harness.currentAllowScreenOffCalls)
        assertEquals(0, countSessionEvent(harness.diagnosticsEvents, "rollback_to_menu"))
        assertEquals(1, countSessionEvent(harness.diagnosticsEvents, "request_control_failure_to_stop_flow"))
    }

    @Test
    fun requestControlTimeoutDuringSessionCompletesThroughStopFlowTimeout() {
        val manualHandler = ManualHandler()
        val harness = createHarness(mainHandler = manualHandler)
        harness.orchestrator.initialize()
        harness.uiState.screen.value = AppScreen.CONNECTING
        harness.orchestrator.simulateRequestControlGrantedForTest()

        harness.orchestrator.simulateRequestControlTimeoutForTest()

        assertEquals(AppScreen.STOPPING, harness.uiState.screen.value)
        assertEquals(StopFlowState.STOPPING_AWAIT_ACK, harness.uiState.stopFlowState.value)
        assertFalse(harness.uiState.ftmsReady.value)
        assertFalse(harness.uiState.ftmsControlGranted.value)
        assertTrue(harness.uiState.suggestTrainerSearchAfterConnectionIssue.value)
        assertFalse(harness.uiState.suggestOpenSettingsAfterConnectionIssue.value)
        assertNotNull(harness.uiState.connectionIssueMessage.value)
        assertNull(harness.uiState.summary.value)
        assertEquals(0, harness.currentAllowScreenOffCalls)
        assertEquals(0, countSessionEvent(harness.diagnosticsEvents, "rollback_to_menu"))
        assertEquals(1, countSessionEvent(harness.diagnosticsEvents, "request_control_failure_to_stop_flow"))

        manualHandler.advanceBy(4_000L)

        assertEquals(AppScreen.SUMMARY, harness.uiState.screen.value)
        assertEquals(StopFlowState.IDLE, harness.uiState.stopFlowState.value)
        assertNotNull(harness.uiState.summary.value)
        assertEquals(1, harness.currentAllowScreenOffCalls)
        assertEquals(1, countSessionEvent(harness.diagnosticsEvents, "stop_flow_completed"))
    }

    @Test
    fun unexpectedRequestControlResponseOutsideInFlightDoesNotRollbackSession() {
        val harness = createHarness()
        harness.orchestrator.initialize()
        harness.uiState.screen.value = AppScreen.SESSION
        harness.uiState.ftmsReady.value = true
        harness.uiState.ftmsControlGranted.value = true

        harness.orchestrator.simulateBleControlPointResponseForTest(
            requestOpcode = 0x00,
            resultCode = 0x05,
        )

        assertEquals(AppScreen.SESSION, harness.uiState.screen.value)
        assertTrue(harness.uiState.ftmsReady.value)
        assertTrue(harness.uiState.ftmsControlGranted.value)
        assertNull(harness.uiState.connectionIssueMessage.value)
        assertEquals(0, harness.currentAllowScreenOffCalls)
        assertEquals(0, countSessionEvent(harness.diagnosticsEvents, "rollback_to_menu"))
    }

    @Test
    fun unexpectedRequestControlResponseWhileConnectingKeepsFlowInConnecting() {
        val manualHandler = ManualHandler()
        val harness = createHarness(mainHandler = manualHandler)
        harness.orchestrator.initialize()

        harness.orchestrator.beginConnectFlowForTest()
        assertEquals(AppScreen.CONNECTING, harness.uiState.screen.value)

        harness.orchestrator.simulateBleControlPointResponseForTest(
            requestOpcode = 0x00,
            resultCode = 0x05,
        )

        assertEquals(AppScreen.CONNECTING, harness.uiState.screen.value)
        assertNull(harness.uiState.connectionIssueMessage.value)
        assertNull(harness.uiState.connectingTimeoutMessage.value)
        assertEquals(0, harness.currentAllowScreenOffCalls)
        assertEquals(0, countSessionEvent(harness.diagnosticsEvents, "rollback_to_menu"))

        manualHandler.advanceBy(15_000L)
        assertEquals(AppScreen.CONNECTING, harness.uiState.screen.value)
        assertNotNull(harness.uiState.connectingTimeoutMessage.value)
    }

    @Test
    fun mismatchedRequestControlResponseWhileTargetPowerInFlightDoesNotRollbackSession() {
        val manualHandler = ManualHandler()
        val harness = createHarness(mainHandler = manualHandler)
        harness.orchestrator.initialize()
        harness.uiState.screen.value = AppScreen.SESSION
        harness.uiState.ftmsReady.value = true
        harness.uiState.ftmsControlGranted.value = true
        harness.orchestrator.seedInFlightCommandOpcodeForTest(requestOpcode = 0x05)

        harness.orchestrator.simulateBleControlPointResponseForTest(
            requestOpcode = 0x00,
            resultCode = 0x05,
        )

        assertEquals(AppScreen.SESSION, harness.uiState.screen.value)
        assertTrue(harness.uiState.ftmsReady.value)
        assertTrue(harness.uiState.ftmsControlGranted.value)
        assertNull(harness.uiState.connectionIssueMessage.value)
        assertEquals(0, harness.currentAllowScreenOffCalls)
        assertEquals(0, countSessionEvent(harness.diagnosticsEvents, "rollback_to_menu"))

        manualHandler.advanceBy(0L)
        val mismatchUnexpectedResponseCount = harness.diagnosticsEvents.count { diagnostic ->
            diagnostic.category == "ftms_command" &&
                diagnostic.event == "unexpected_response" &&
                diagnostic.context["unexpectedReason"] == "OPCODE_MISMATCH"
        }
        assertEquals(1, mismatchUnexpectedResponseCount)
    }

    @Test
    fun staleLinkGenerationRequestControlResponseDoesNotRollbackSession() {
        val manualHandler = ManualHandler()
        val harness = createHarness(mainHandler = manualHandler)
        harness.orchestrator.initialize()
        harness.uiState.screen.value = AppScreen.SESSION
        harness.uiState.ftmsReady.value = true
        harness.uiState.ftmsControlGranted.value = true
        harness.orchestrator.setControlLinkGenerationForTest(linkGeneration = 4)
        harness.orchestrator.seedInFlightCommandOpcodeForTest(requestOpcode = 0x00)

        harness.orchestrator.simulateBleControlPointResponseForTest(
            requestOpcode = 0x00,
            resultCode = 0x05,
            linkGeneration = 3,
        )

        assertEquals(AppScreen.SESSION, harness.uiState.screen.value)
        assertTrue(harness.uiState.ftmsReady.value)
        assertTrue(harness.uiState.ftmsControlGranted.value)
        assertNull(harness.uiState.connectionIssueMessage.value)
        assertEquals(0, harness.currentAllowScreenOffCalls)
        assertEquals(0, countSessionEvent(harness.diagnosticsEvents, "rollback_to_menu"))

        manualHandler.advanceBy(0L)
        val staleLinkUnexpectedResponseCount = harness.diagnosticsEvents.count { diagnostic ->
            diagnostic.category == "ftms_command" &&
                diagnostic.event == "unexpected_response" &&
                diagnostic.context["unexpectedReason"] == "STALE_LINK_GENERATION"
        }
        assertEquals(1, staleLinkUnexpectedResponseCount)
    }

    @Test
    fun requestControlFailureOrderingRecordsRollbackBeforeScopeEnd() {
        val harness = createHarness(
            isMockTrainerModeEnabled = { true },
        )
        harness.orchestrator.initialize()
        harness.uiState.selectedWorkout.value = readyWorkout()
        harness.uiState.workoutReady.value = true

        harness.orchestrator.startSessionConnection()
        assertEquals(AppScreen.CONNECTING, harness.uiState.screen.value)

        harness.orchestrator.simulateRequestControlTimeoutForTest()

        assertEquals(AppScreen.MENU, harness.uiState.screen.value)
        assertEquals(
            "session.request_control_timeout",
            firstSessionEventContext(
                events = harness.diagnosticsEvents,
                event = "rollback_to_menu",
                key = "reason",
            ),
        )
        assertEquals(
            "rollback_to_menu:session.request_control_timeout",
            firstSessionEventContext(
                events = harness.diagnosticsEvents,
                event = "scope_ended",
                key = "reason",
            ),
        )

        val rollbackIndex = indexOfSessionEvent(
            events = harness.diagnosticsEvents,
            event = "rollback_to_menu",
        )
        val scopeEndedIndex = indexOfSessionEvent(
            events = harness.diagnosticsEvents,
            event = "scope_ended",
        )
        assertTrue(rollbackIndex < scopeEndedIndex)
    }

    @Test
    fun requestControlSuccessFromConnectingTransitionsToSession() {
        val harness = createHarness()
        harness.orchestrator.initialize()
        harness.uiState.screen.value = AppScreen.CONNECTING

        harness.orchestrator.simulateRequestControlGrantedForTest()

        assertEquals(AppScreen.SESSION, harness.uiState.screen.value)
        assertEquals(SessionPhase.RUNNING, harness.sessionManager.getPhase())
        assertTrue(harness.uiState.pendingCadenceStartAfterControlGranted)
        assertEquals(1, harness.currentKeepScreenOnCalls)
    }

    @Test
    fun stopFlowCompletesToSummaryOnAcknowledgement() {
        val manualHandler = ManualHandler()
        val harness = createHarness(mainHandler = manualHandler)
        harness.orchestrator.initialize()
        harness.uiState.screen.value = AppScreen.SESSION

        harness.orchestrator.endSessionAndGoToSummary()

        assertEquals(AppScreen.STOPPING, harness.uiState.screen.value)
        assertEquals(StopFlowState.STOPPING_AWAIT_ACK, harness.uiState.stopFlowState.value)
        assertNull(harness.uiState.summary.value)

        harness.orchestrator.simulateStopAcknowledgedForTest()

        assertEquals(AppScreen.SUMMARY, harness.uiState.screen.value)
        assertEquals(StopFlowState.IDLE, harness.uiState.stopFlowState.value)
        assertEquals(1, harness.currentAllowScreenOffCalls)
    }

    @Test
    fun startAndStopFlowTransitionCompletesToSummaryOnAcknowledgement() {
        val manualHandler = ManualHandler()
        val harness = createHarness(mainHandler = manualHandler)
        harness.orchestrator.initialize()
        harness.uiState.screen.value = AppScreen.CONNECTING

        harness.orchestrator.simulateRequestControlGrantedForTest()

        assertEquals(AppScreen.SESSION, harness.uiState.screen.value)
        assertEquals(SessionPhase.RUNNING, harness.sessionManager.getPhase())
        assertEquals(1, harness.currentKeepScreenOnCalls)

        harness.orchestrator.endSessionAndGoToSummary()
        assertEquals(AppScreen.STOPPING, harness.uiState.screen.value)
        assertEquals(StopFlowState.STOPPING_AWAIT_ACK, harness.uiState.stopFlowState.value)

        harness.orchestrator.simulateStopAcknowledgedForTest()

        assertEquals(AppScreen.SUMMARY, harness.uiState.screen.value)
        assertEquals(StopFlowState.IDLE, harness.uiState.stopFlowState.value)
        assertEquals(1, harness.currentAllowScreenOffCalls)
        assertNotNull(harness.uiState.summary.value)
    }

    @Test
    fun transitionTableNominalLifecycleMovesMenuToSummary() {
        val manualHandler = ManualHandler()
        val harness = createHarness(mainHandler = manualHandler)
        harness.orchestrator.initialize()
        harness.uiState.selectedWorkout.value = readyWorkout()
        harness.uiState.workoutReady.value = true

        runTransitionTable(
            scenario = "nominal lifecycle",
            uiState = harness.uiState,
            steps = listOf(
                TransitionStep(
                    event = "initial state",
                    expectedScreen = AppScreen.MENU,
                    expectedStopFlowState = StopFlowState.IDLE,
                ),
                TransitionStep(
                    event = "begin connect flow",
                    trigger = { harness.orchestrator.beginConnectFlowForTest() },
                    expectedScreen = AppScreen.CONNECTING,
                    expectedStopFlowState = StopFlowState.IDLE,
                ),
                TransitionStep(
                    event = "request control granted",
                    trigger = { harness.orchestrator.simulateRequestControlGrantedForTest() },
                    expectedScreen = AppScreen.SESSION,
                    expectedStopFlowState = StopFlowState.IDLE,
                    assertions = {
                        assertEquals(SessionPhase.RUNNING, harness.sessionManager.getPhase())
                    },
                ),
                TransitionStep(
                    event = "end session",
                    trigger = { harness.orchestrator.endSessionAndGoToSummary() },
                    expectedScreen = AppScreen.STOPPING,
                    expectedStopFlowState = StopFlowState.STOPPING_AWAIT_ACK,
                ),
                TransitionStep(
                    event = "stop acknowledged",
                    trigger = { harness.orchestrator.simulateStopAcknowledgedForTest() },
                    expectedScreen = AppScreen.SUMMARY,
                    expectedStopFlowState = StopFlowState.IDLE,
                    assertions = {
                        assertNotNull(harness.uiState.summary.value)
                    },
                ),
            ),
        )
    }

    @Test
    fun transitionTableConnectTimeoutPromptsInConnecting() {
        val manualHandler = ManualHandler()
        val harness = createHarness(mainHandler = manualHandler)
        harness.orchestrator.initialize()

        runTransitionTable(
            scenario = "connect timeout prompt",
            uiState = harness.uiState,
            steps = listOf(
                TransitionStep(
                    event = "initial state",
                    expectedScreen = AppScreen.MENU,
                    expectedStopFlowState = StopFlowState.IDLE,
                ),
                TransitionStep(
                    event = "begin connect flow",
                    trigger = { harness.orchestrator.beginConnectFlowForTest() },
                    expectedScreen = AppScreen.CONNECTING,
                    expectedStopFlowState = StopFlowState.IDLE,
                ),
                TransitionStep(
                    event = "timeout not yet reached",
                    trigger = { manualHandler.advanceBy(14_999L) },
                    expectedScreen = AppScreen.CONNECTING,
                    expectedStopFlowState = StopFlowState.IDLE,
                ),
                TransitionStep(
                    event = "connect timeout fires",
                    trigger = { manualHandler.advanceBy(1L) },
                    expectedScreen = AppScreen.CONNECTING,
                    expectedStopFlowState = StopFlowState.IDLE,
                    assertions = {
                        assertNotNull(harness.uiState.connectingTimeoutMessage.value)
                        assertNull(harness.uiState.connectionIssueMessage.value)
                    },
                ),
            ),
        )
    }

    @Test
    fun transitionTablePermissionDeniedPathRequiresExplicitRetry() {
        var connectPermissionGranted = false
        val harness = createHarness(
            ensureBluetoothPermission = { connectPermissionGranted },
        )
        harness.orchestrator.initialize()
        harness.uiState.selectedWorkout.value = readyWorkout()
        harness.uiState.workoutReady.value = true

        runTransitionTable(
            scenario = "permission denied recovery",
            uiState = harness.uiState,
            steps = listOf(
                TransitionStep(
                    event = "initial state",
                    expectedScreen = AppScreen.MENU,
                    expectedStopFlowState = StopFlowState.IDLE,
                ),
                TransitionStep(
                    event = "start session connection",
                    trigger = { harness.orchestrator.startSessionConnection() },
                    expectedScreen = AppScreen.MENU,
                    expectedStopFlowState = StopFlowState.IDLE,
                    assertions = {
                        assertTrue(harness.uiState.pendingSessionStartAfterPermission)
                    },
                ),
                TransitionStep(
                    event = "permission denied callback",
                    trigger = { harness.deliverPermissionResult(granted = false) },
                    expectedScreen = AppScreen.MENU,
                    expectedStopFlowState = StopFlowState.IDLE,
                    assertions = {
                        assertFalse(harness.uiState.pendingSessionStartAfterPermission)
                        assertTrue(harness.uiState.suggestOpenSettingsAfterConnectionIssue.value)
                        assertFalse(harness.uiState.suggestTrainerSearchAfterConnectionIssue.value)
                    },
                ),
                TransitionStep(
                    event = "permission later granted without pending start",
                    trigger = {
                        connectPermissionGranted = true
                        harness.deliverPermissionResult(granted = true)
                    },
                    expectedScreen = AppScreen.MENU,
                    expectedStopFlowState = StopFlowState.IDLE,
                    assertions = {
                        assertFalse(harness.uiState.pendingSessionStartAfterPermission)
                        assertTrue(harness.uiState.suggestOpenSettingsAfterConnectionIssue.value)
                    },
                ),
                TransitionStep(
                    event = "explicit retry",
                    trigger = {
                        connectPermissionGranted = false
                        harness.orchestrator.startSessionConnection()
                    },
                    expectedScreen = AppScreen.MENU,
                    expectedStopFlowState = StopFlowState.IDLE,
                    assertions = {
                        assertTrue(harness.uiState.pendingSessionStartAfterPermission)
                        assertFalse(harness.uiState.suggestOpenSettingsAfterConnectionIssue.value)
                    },
                ),
            ),
        )
    }

    @Test
    fun transitionTableStopTimeoutCompletesToSummary() {
        val manualHandler = ManualHandler()
        val harness = createHarness(mainHandler = manualHandler)
        harness.orchestrator.initialize()
        harness.uiState.screen.value = AppScreen.SESSION

        runTransitionTable(
            scenario = "stop timeout completion",
            uiState = harness.uiState,
            steps = listOf(
                TransitionStep(
                    event = "initial session state",
                    expectedScreen = AppScreen.SESSION,
                    expectedStopFlowState = StopFlowState.IDLE,
                ),
                TransitionStep(
                    event = "end session",
                    trigger = { harness.orchestrator.endSessionAndGoToSummary() },
                    expectedScreen = AppScreen.STOPPING,
                    expectedStopFlowState = StopFlowState.STOPPING_AWAIT_ACK,
                ),
                TransitionStep(
                    event = "timeout not yet reached",
                    trigger = { manualHandler.advanceBy(3_999L) },
                    expectedScreen = AppScreen.STOPPING,
                    expectedStopFlowState = StopFlowState.STOPPING_AWAIT_ACK,
                ),
                TransitionStep(
                    event = "stop timeout fires",
                    trigger = { manualHandler.advanceBy(1L) },
                    expectedScreen = AppScreen.SUMMARY,
                    expectedStopFlowState = StopFlowState.IDLE,
                ),
            ),
        )
    }

    @Test
    fun transitionTableStopDisconnectCompletesToSummary() {
        val harness = createHarness()
        harness.orchestrator.initialize()
        harness.uiState.screen.value = AppScreen.SESSION

        runTransitionTable(
            scenario = "stop disconnect completion",
            uiState = harness.uiState,
            steps = listOf(
                TransitionStep(
                    event = "initial session state",
                    expectedScreen = AppScreen.SESSION,
                    expectedStopFlowState = StopFlowState.IDLE,
                ),
                TransitionStep(
                    event = "end session",
                    trigger = { harness.orchestrator.endSessionAndGoToSummary() },
                    expectedScreen = AppScreen.STOPPING,
                    expectedStopFlowState = StopFlowState.STOPPING_AWAIT_ACK,
                ),
                TransitionStep(
                    event = "disconnect callback completes stop flow",
                    trigger = {
                        val activeGeneration = harness.orchestrator.activeFtmsGenerationForTest()
                        harness.orchestrator.simulateFtmsDisconnectedForTest(activeGeneration)
                    },
                    expectedScreen = AppScreen.SUMMARY,
                    expectedStopFlowState = StopFlowState.IDLE,
                ),
            ),
        )
    }

    @Test
    fun stopFlowTimeoutCompletesToSummaryWithoutAcknowledgement() {
        val manualHandler = ManualHandler()
        val harness = createHarness(mainHandler = manualHandler)
        harness.orchestrator.initialize()
        harness.uiState.screen.value = AppScreen.SESSION

        harness.orchestrator.endSessionAndGoToSummary()

        assertEquals(AppScreen.STOPPING, harness.uiState.screen.value)
        assertEquals(StopFlowState.STOPPING_AWAIT_ACK, harness.uiState.stopFlowState.value)

        manualHandler.advanceBy(3999L)
        assertEquals(AppScreen.STOPPING, harness.uiState.screen.value)
        assertEquals(StopFlowState.STOPPING_AWAIT_ACK, harness.uiState.stopFlowState.value)

        manualHandler.advanceBy(1L)
        assertEquals(AppScreen.SUMMARY, harness.uiState.screen.value)
        assertEquals(StopFlowState.IDLE, harness.uiState.stopFlowState.value)
        assertEquals(1, harness.currentAllowScreenOffCalls)
    }

    @Test
    fun stopFlowTimeoutOrderingRecordsTimeoutBeforeCompletionAndScopeEnd() {
        val manualHandler = ManualHandler()
        val harness = createHarness(mainHandler = manualHandler)
        harness.orchestrator.initialize()
        harness.uiState.screen.value = AppScreen.SESSION

        harness.orchestrator.endSessionAndGoToSummary()
        manualHandler.advanceBy(4_000L)

        assertEquals(AppScreen.SUMMARY, harness.uiState.screen.value)
        assertEquals(StopFlowState.IDLE, harness.uiState.stopFlowState.value)
        assertEquals(1, harness.currentAllowScreenOffCalls)
        assertEquals(
            "stopFlowTimeout",
            firstSessionEventContext(
                events = harness.diagnosticsEvents,
                event = "stop_flow_completed",
                key = "reason",
            ),
        )
        assertEquals(
            "stop_flow_completed:stopFlowTimeout",
            firstSessionEventContext(
                events = harness.diagnosticsEvents,
                event = "scope_ended",
                key = "reason",
            ),
        )

        val stopRequestedIndex = indexOfSessionEvent(
            events = harness.diagnosticsEvents,
            event = "stop_requested",
        )
        val timeoutIndex = indexOfSessionEvent(
            events = harness.diagnosticsEvents,
            event = "stop_flow_timeout",
        )
        val completionIndex = indexOfSessionEvent(
            events = harness.diagnosticsEvents,
            event = "stop_flow_completed",
        )
        val scopeEndedIndex = indexOfSessionEvent(
            events = harness.diagnosticsEvents,
            event = "scope_ended",
        )
        assertTrue(stopRequestedIndex < timeoutIndex)
        assertTrue(timeoutIndex < completionIndex)
        assertTrue(completionIndex < scopeEndedIndex)
    }

    @Test
    fun stopFlowDisconnectOrderingCompletesWithoutTimeoutAndEndsScope() {
        val harness = createHarness()
        harness.orchestrator.initialize()
        harness.uiState.screen.value = AppScreen.SESSION

        harness.orchestrator.endSessionAndGoToSummary()
        val activeGeneration = harness.orchestrator.activeFtmsGenerationForTest()
        harness.orchestrator.simulateFtmsDisconnectedForTest(activeGeneration)

        assertEquals(AppScreen.SUMMARY, harness.uiState.screen.value)
        assertEquals(StopFlowState.IDLE, harness.uiState.stopFlowState.value)
        assertEquals(1, harness.currentAllowScreenOffCalls)
        assertEquals(0, countSessionEvent(harness.diagnosticsEvents, "stop_flow_timeout"))
        assertEquals(
            "bleOnDisconnectedDuringStopFlow",
            firstSessionEventContext(
                events = harness.diagnosticsEvents,
                event = "stop_flow_completed",
                key = "reason",
            ),
        )
        assertEquals(
            "stop_flow_completed:bleOnDisconnectedDuringStopFlow",
            firstSessionEventContext(
                events = harness.diagnosticsEvents,
                event = "scope_ended",
                key = "reason",
            ),
        )

        val stopRequestedIndex = indexOfSessionEvent(
            events = harness.diagnosticsEvents,
            event = "stop_requested",
        )
        val completionIndex = indexOfSessionEvent(
            events = harness.diagnosticsEvents,
            event = "stop_flow_completed",
        )
        val scopeEndedIndex = indexOfSessionEvent(
            events = harness.diagnosticsEvents,
            event = "scope_ended",
        )
        assertTrue(stopRequestedIndex < completionIndex)
        assertTrue(completionIndex < scopeEndedIndex)
    }

    @Test
    fun ftmsDisconnectDuringSessionDoesNotForceHeartRateClose() {
        var closeHeartRateCalls = 0
        val harness = createHarness(
            closeHeartRate = { closeHeartRateCalls += 1 },
        )
        harness.orchestrator.initialize()
        harness.uiState.screen.value = AppScreen.SESSION

        val activeGeneration = harness.orchestrator.activeFtmsGenerationForTest()
        harness.orchestrator.simulateFtmsDisconnectedForTest(activeGeneration)

        assertEquals(AppScreen.MENU, harness.uiState.screen.value)
        assertEquals(0, closeHeartRateCalls)
    }

    @Test
    fun stopAndCloseAlwaysClosesHeartRateTransport() {
        var closeHeartRateCalls = 0
        val harness = createHarness(
            closeHeartRate = { closeHeartRateCalls += 1 },
        )
        harness.orchestrator.initialize()

        harness.orchestrator.stopAndClose()

        assertEquals(1, closeHeartRateCalls)
    }

    @Test
    fun stopFlowAcknowledgeOrderingCompletesOnceAndCancelsTimeoutPath() {
        val manualHandler = ManualHandler()
        val harness = createHarness(mainHandler = manualHandler)
        harness.orchestrator.initialize()
        harness.uiState.screen.value = AppScreen.SESSION

        harness.orchestrator.endSessionAndGoToSummary()
        harness.orchestrator.simulateStopAcknowledgedForTest()
        manualHandler.advanceBy(4_000L)

        assertEquals(AppScreen.SUMMARY, harness.uiState.screen.value)
        assertEquals(StopFlowState.IDLE, harness.uiState.stopFlowState.value)
        assertEquals(1, harness.currentAllowScreenOffCalls)
        assertEquals(0, countSessionEvent(harness.diagnosticsEvents, "stop_flow_timeout"))
        assertEquals(1, countSessionEvent(harness.diagnosticsEvents, "stop_flow_completed"))
        assertEquals(1, countSessionEvent(harness.diagnosticsEvents, "scope_ended"))
        assertEquals(
            "onStopAcknowledgedForTest",
            firstSessionEventContext(
                events = harness.diagnosticsEvents,
                event = "stop_flow_completed",
                key = "reason",
            ),
        )

        val stopRequestedIndex = indexOfSessionEvent(
            events = harness.diagnosticsEvents,
            event = "stop_requested",
        )
        val completionIndex = indexOfSessionEvent(
            events = harness.diagnosticsEvents,
            event = "stop_flow_completed",
        )
        val scopeEndedIndex = indexOfSessionEvent(
            events = harness.diagnosticsEvents,
            event = "scope_ended",
        )
        assertTrue(stopRequestedIndex < completionIndex)
        assertTrue(completionIndex < scopeEndedIndex)
    }

    @Test
    fun stopFlowAckKeepsContinueRideRestartWindowClosedUntilDisconnectAndSettleDelayFinish() {
        val manualHandler = ManualHandler()
        val harness = createHarness(mainHandler = manualHandler)
        harness.orchestrator.initialize()
        harness.uiState.screen.value = AppScreen.SESSION
        harness.uiState.ftmsReady.value = true
        harness.uiState.ftmsControlGranted.value = true

        val generation = harness.orchestrator.activeFtmsGenerationForTest()

        harness.orchestrator.endSessionAndGoToSummary()
        harness.orchestrator.simulateStopAcknowledgedForTest()

        assertEquals(AppScreen.SUMMARY, harness.uiState.screen.value)
        assertFalse(harness.orchestrator.continueRideRestartWindowOpen())

        harness.orchestrator.simulateFtmsDisconnectedForTest(generation)
        assertFalse(harness.orchestrator.continueRideRestartWindowOpen())
        assertFalse(harness.uiState.ftmsReady.value)
        assertFalse(harness.uiState.ftmsControlGranted.value)

        manualHandler.advanceBy(1_499L)
        assertFalse(harness.orchestrator.continueRideRestartWindowOpen())

        manualHandler.advanceBy(1L)
        assertTrue(harness.orchestrator.continueRideRestartWindowOpen())
    }

    @Test
    fun workoutCompleteSummaryStopFlowCanFinishWithoutClosingTransport() {
        val manualHandler = ManualHandler()
        val harness = createHarness(mainHandler = manualHandler)
        harness.orchestrator.initialize()
        harness.uiState.screen.value = AppScreen.SESSION
        harness.uiState.ftmsReady.value = true
        harness.uiState.ftmsControlGranted.value = true

        harness.orchestrator.endWorkoutCompleteSessionToSummaryKeepingConnection()
        harness.orchestrator.simulateStopAcknowledgedForTest()

        assertEquals(AppScreen.SUMMARY, harness.uiState.screen.value)
        assertEquals(StopFlowState.IDLE, harness.uiState.stopFlowState.value)
        assertTrue(harness.uiState.ftmsReady.value)
        assertFalse(harness.uiState.ftmsControlGranted.value)
        assertFalse(harness.orchestrator.explicitTrainerCloseInProgressForTest())
        assertEquals(0L, harness.orchestrator.explicitTrainerReconnectDelayRemainingForTest())
        assertFalse(harness.orchestrator.continueRideRestartWindowOpen())
        assertEquals(1, countSessionEvent(harness.diagnosticsEvents, "stop_flow_transport_close_skipped"))
        assertEquals(
            "keep_connected",
            firstSessionEventContext(
                events = harness.diagnosticsEvents,
                event = "stop_flow_transport_close_skipped",
                key = "transportClosePolicy",
            ),
        )
    }

    @Test
    fun preparePostWorkoutExitWindowUsesCompletionDwellTimeBeforeContinueChoice() {
        val manualHandler = ManualHandler()
        val harness = createHarness(mainHandler = manualHandler)
        harness.orchestrator.initialize()
        harness.uiState.screen.value = AppScreen.SESSION
        harness.uiState.ftmsReady.value = true
        harness.uiState.ftmsControlGranted.value = true
        harness.uiState.runner.value = io.github.ewoc2026.ewoc.workout.runner.RunnerState.stopped(
            workoutElapsedSec = 1_200,
        )

        val generation = harness.orchestrator.activeFtmsGenerationForTest()

        assertTrue(harness.orchestrator.preparePostWorkoutExitWindowAfterCompletion())
        assertTrue(harness.orchestrator.hasPreparedPostWorkoutExitWindow())
        assertFalse(harness.orchestrator.continueRideRestartWindowOpen())
        assertFalse(harness.orchestrator.preparedPostWorkoutSummaryWindowOpenForTest())

        harness.orchestrator.seedInFlightCommandOpcodeForTest(requestOpcode = 0x08)
        harness.orchestrator.simulateBleControlPointResponseForTest(
            requestOpcode = 0x08,
            resultCode = 0x01,
        )
        manualHandler.advanceBy(0L)

        assertTrue(harness.orchestrator.preparedPostWorkoutSummaryWindowOpenForTest())
        assertFalse(harness.orchestrator.continueRideRestartWindowOpen())

        harness.orchestrator.simulateFtmsDisconnectedForTest(generation)

        assertTrue(harness.orchestrator.preparedPostWorkoutSummaryWindowOpenForTest())
        assertFalse(harness.orchestrator.continueRideRestartWindowOpen())
        manualHandler.advanceBy(1_499L)
        assertTrue(harness.orchestrator.preparedPostWorkoutSummaryWindowOpenForTest())
        assertFalse(harness.orchestrator.continueRideRestartWindowOpen())
        manualHandler.advanceBy(1L)
        assertTrue(harness.orchestrator.preparedPostWorkoutSummaryWindowOpenForTest())
        assertTrue(harness.orchestrator.continueRideRestartWindowOpen())
    }

    @Test
    fun preparePostWorkoutExitWindowRecordsReleaseRampDryRunDecision() {
        val harness = createHarness()
        harness.orchestrator.initialize()
        harness.uiState.screen.value = AppScreen.SESSION
        harness.uiState.ftmsReady.value = true
        harness.uiState.ftmsControlGranted.value = true
        harness.uiState.trainerControlAuthority.value = TrainerControlAuthority.APP_CONTROLLED
        harness.uiState.lastAppControlledTargetPower.value = 180
        harness.uiState.bikeData.value = IndoorBikeData(
            valid = true,
            instantaneousSpeedKmh = null,
            averageSpeedKmh = null,
            instantaneousCadenceRpm = 85.0,
            averageCadenceRpm = null,
            totalDistanceMeters = null,
            resistanceLevel = null,
            instantaneousPowerW = 182,
            averagePowerW = null,
            totalEnergyKcal = null,
            energyPerHourKcal = null,
            energyPerMinuteKcal = null,
            heartRateBpm = null,
            metabolicEquivalent = null,
            elapsedTimeSeconds = null,
            remainingTimeSeconds = null,
        )
        harness.uiState.runner.value = io.github.ewoc2026.ewoc.workout.runner.RunnerState.stopped(
            workoutElapsedSec = 1_200,
        )

        assertTrue(harness.orchestrator.preparePostWorkoutExitWindowAfterCompletion())

        assertEquals(1, countDiagnosticsEvent(harness.diagnosticsEvents, "release_ramp", "evaluate"))
        assertEquals(1, countDiagnosticsEvent(harness.diagnosticsEvents, "release_ramp", "decision"))
        assertEquals(1, countDiagnosticsEvent(harness.diagnosticsEvents, "trainer_release_runtime", "completion_exit_prep_requested"))
        assertEquals(1, countDiagnosticsEvent(harness.diagnosticsEvents, "trainer_release_runtime", "teardown_decision_applied"))
        val decision = harness.diagnosticsEvents.last { diagnostic ->
            diagnostic.category == "release_ramp" && diagnostic.event == "decision"
        }
        assertEquals("completion_prep_requested", decision.context["stage"])
        assertEquals("EXECUTE", decision.context["outcome"])
        assertEquals("APP_CONTROLLED", decision.context["authority"])
        assertEquals("180", decision.context["knownAppTargetPowerW"])
        val teardownDecision = harness.diagnosticsEvents.last { diagnostic ->
            diagnostic.category == "trainer_release_runtime" &&
                diagnostic.event == "teardown_decision_applied"
        }
        assertEquals("completion_exit_prep", teardownDecision.context["owner"])
        assertEquals("execute", teardownDecision.context["decision"])
    }

    @Test
    fun completionExitControlResponseRecordsUpdatedReleaseRampDryRunDecision() {
        val harness = createHarness()
        harness.orchestrator.initialize()
        harness.uiState.screen.value = AppScreen.SESSION
        harness.uiState.ftmsReady.value = true
        harness.uiState.ftmsControlGranted.value = false
        harness.uiState.trainerControlAuthority.value = TrainerControlAuthority.APP_CONTROLLED
        harness.uiState.lastAppControlledTargetPower.value = 180
        harness.uiState.bikeData.value = IndoorBikeData(
            valid = true,
            instantaneousSpeedKmh = null,
            averageSpeedKmh = null,
            instantaneousCadenceRpm = 85.0,
            averageCadenceRpm = null,
            totalDistanceMeters = null,
            resistanceLevel = null,
            instantaneousPowerW = 181,
            averagePowerW = null,
            totalEnergyKcal = null,
            energyPerHourKcal = null,
            energyPerMinuteKcal = null,
            heartRateBpm = null,
            metabolicEquivalent = null,
            elapsedTimeSeconds = null,
            remainingTimeSeconds = null,
        )
        harness.uiState.runner.value = io.github.ewoc2026.ewoc.workout.runner.RunnerState.stopped(
            workoutElapsedSec = 1_200,
        )

        assertTrue(harness.orchestrator.preparePostWorkoutExitWindowAfterCompletion())
        harness.orchestrator.seedInFlightCommandOpcodeForTest(requestOpcode = 0x00)
        harness.orchestrator.simulateBleControlPointResponseForTest(
            requestOpcode = 0x00,
            resultCode = 0x01,
        )

        assertEquals(2, countDiagnosticsEvent(harness.diagnosticsEvents, "release_ramp", "evaluate"))
        assertEquals(2, countDiagnosticsEvent(harness.diagnosticsEvents, "release_ramp", "decision"))
        val decision = harness.diagnosticsEvents.last { diagnostic ->
            diagnostic.category == "release_ramp" && diagnostic.event == "decision"
        }
        assertEquals("completion_prep_control_response", decision.context["stage"])
        assertEquals("true", decision.context["ftmsControlGranted"])
        assertEquals("EXECUTE", decision.context["outcome"])
    }

    @Test
    fun continueRideAfterWorkoutCompleteExecutesReleaseRampBeforeSoftStop() {
        val manualHandler = ManualHandler()
        val ftmsMac = "AA:BB:CC:DD:EE:FF"
        val harness = createHarness(
            mainHandler = manualHandler,
            ensureBluetoothPermission = { true },
            currentFtmsDeviceMac = { ftmsMac },
        )
        harness.orchestrator.initialize()
        harness.uiState.selectedSessionSetupMode.value = SessionSetupMode.FILE
        harness.uiState.selectedWorkout.value = readyWorkout()
        harness.uiState.selectedWorkoutFileName.value = "session.ewo"
        harness.uiState.screen.value = AppScreen.SESSION
        harness.uiState.ftmsReady.value = true
        harness.uiState.ftmsControlGranted.value = true
        harness.uiState.trainerControlAuthority.value = TrainerControlAuthority.APP_CONTROLLED
        harness.uiState.lastAppControlledTargetPower.value = 180
        harness.uiState.bikeData.value = IndoorBikeData(
            valid = true,
            instantaneousSpeedKmh = null,
            averageSpeedKmh = null,
            instantaneousCadenceRpm = 85.0,
            averageCadenceRpm = null,
            totalDistanceMeters = null,
            resistanceLevel = null,
            instantaneousPowerW = 182,
            averagePowerW = null,
            totalEnergyKcal = null,
            energyPerHourKcal = null,
            energyPerMinuteKcal = null,
            heartRateBpm = null,
            metabolicEquivalent = null,
            elapsedTimeSeconds = null,
            remainingTimeSeconds = null,
        )
        harness.uiState.runner.value = io.github.ewoc2026.ewoc.workout.runner.RunnerState.stopped(
            workoutElapsedSec = 900,
        )

        assertTrue(harness.orchestrator.continueRideAfterWorkoutComplete())
        assertEquals(1, countDiagnosticsEvent(harness.diagnosticsEvents, "release_ramp", "evaluate"))
        assertEquals(1, countDiagnosticsEvent(harness.diagnosticsEvents, "release_ramp", "decision"))
        assertEquals(1, countDiagnosticsEvent(harness.diagnosticsEvents, "trainer_release_runtime", "continue_ride_requested"))
        assertEquals(1, countDiagnosticsEvent(harness.diagnosticsEvents, "trainer_release_runtime", "release_ramp_started"))
        assertNull(harness.uiState.lastTargetPower.value)

        manualHandler.advanceBy(249L)
        assertNull(harness.uiState.lastTargetPower.value)
        manualHandler.advanceBy(1L)
        assertEquals(165, harness.uiState.lastTargetPower.value)

        manualHandler.advanceBy(2_750L)
        assertEquals(25, harness.uiState.lastTargetPower.value)
        assertFalse(harness.orchestrator.continueRideRestartWindowOpen())
        assertEquals(
            1,
            countDiagnosticsEvent(
                harness.diagnosticsEvents,
                "trainer_release_runtime",
                "release_ramp_floor_hold_active",
            ),
        )

        manualHandler.advanceBy(500L)
        assertEquals(25, harness.uiState.lastTargetPower.value)

        harness.orchestrator.seedInFlightCommandOpcodeForTest(requestOpcode = 0x08)
        harness.orchestrator.simulateBleControlPointResponseForTest(
            requestOpcode = 0x08,
            resultCode = 0x01,
        )
        manualHandler.advanceBy(0L)
        assertEquals(1, countDiagnosticsEvent(harness.diagnosticsEvents, "trainer_release_runtime", "telemetry_reconnect_started"))
        assertTrue(harness.orchestrator.simulatePostWorkoutTelemetryReconnectReadyForTest())
        harness.orchestrator.simulateControlOwnershipChangedForTest(controlGranted = true)
        harness.orchestrator.simulateRequestControlGrantedForTest()

        assertEquals(AppScreen.SESSION, harness.uiState.screen.value)
        assertTrue(harness.uiState.ftmsReady.value)
        assertTrue(harness.uiState.ftmsControlGranted.value)
    }

    @Test
    fun terminalRunnerTargetClearDoesNotQueueResetBeforeCompletionExitRamp() {
        val manualHandler = ManualHandler()
        val harness = createHarness(mainHandler = manualHandler)
        harness.orchestrator.initialize()
        harness.uiState.screen.value = AppScreen.SESSION
        harness.uiState.ftmsReady.value = true
        harness.uiState.ftmsControlGranted.value = true
        harness.uiState.trainerControlAuthority.value = TrainerControlAuthority.APP_CONTROLLED
        harness.uiState.lastAppControlledTargetPower.value = 180
        harness.uiState.bikeData.value = IndoorBikeData(
            valid = true,
            instantaneousSpeedKmh = null,
            averageSpeedKmh = null,
            instantaneousCadenceRpm = 85.0,
            averageCadenceRpm = null,
            totalDistanceMeters = null,
            resistanceLevel = null,
            instantaneousPowerW = 182,
            averagePowerW = null,
            totalEnergyKcal = null,
            energyPerHourKcal = null,
            energyPerMinuteKcal = null,
            heartRateBpm = null,
            metabolicEquivalent = null,
            elapsedTimeSeconds = null,
            remainingTimeSeconds = null,
        )
        harness.uiState.runner.value = io.github.ewoc2026.ewoc.workout.runner.RunnerState.stopped(
            workoutElapsedSec = 1_200,
        )

        harness.orchestrator.simulateRunnerTargetWriteForTest(targetWatts = null)

        assertNull(harness.orchestrator.inFlightFtmsRequestOpcodeForTest())
        assertTrue(harness.orchestrator.preparePostWorkoutExitWindowAfterCompletion())

        manualHandler.advanceBy(250L)
        assertEquals(165, harness.uiState.lastTargetPower.value)
        assertNull(harness.orchestrator.inFlightFtmsRequestOpcodeForTest())
    }

    @Test
    fun preparePostWorkoutExitWindowExecutesReleaseRampBeforeSoftStop() {
        val manualHandler = ManualHandler()
        val harness = createHarness(mainHandler = manualHandler)
        harness.orchestrator.initialize()
        harness.uiState.screen.value = AppScreen.SESSION
        harness.uiState.ftmsReady.value = true
        harness.uiState.ftmsControlGranted.value = true
        harness.uiState.trainerControlAuthority.value = TrainerControlAuthority.APP_CONTROLLED
        harness.uiState.lastAppControlledTargetPower.value = 180
        harness.uiState.bikeData.value = IndoorBikeData(
            valid = true,
            instantaneousSpeedKmh = null,
            averageSpeedKmh = null,
            instantaneousCadenceRpm = 85.0,
            averageCadenceRpm = null,
            totalDistanceMeters = null,
            resistanceLevel = null,
            instantaneousPowerW = 182,
            averagePowerW = null,
            totalEnergyKcal = null,
            energyPerHourKcal = null,
            energyPerMinuteKcal = null,
            heartRateBpm = null,
            metabolicEquivalent = null,
            elapsedTimeSeconds = null,
            remainingTimeSeconds = null,
        )
        harness.uiState.runner.value = io.github.ewoc2026.ewoc.workout.runner.RunnerState.stopped(
            workoutElapsedSec = 1_200,
        )

        assertTrue(harness.orchestrator.preparePostWorkoutExitWindowAfterCompletion())
        assertTrue(harness.orchestrator.hasPreparedPostWorkoutExitWindow())

        manualHandler.advanceBy(250L)
        assertEquals(165, harness.uiState.lastTargetPower.value)
        assertFalse(harness.orchestrator.continueRideRestartWindowOpen())

        manualHandler.advanceBy(2_750L)
        assertEquals(25, harness.uiState.lastTargetPower.value)
        assertFalse(harness.orchestrator.continueRideRestartWindowOpen())
        assertEquals(
            1,
            countDiagnosticsEvent(
                harness.diagnosticsEvents,
                "trainer_release_runtime",
                "release_ramp_floor_hold_active",
            ),
        )

        manualHandler.advanceBy(500L)
        assertEquals(25, harness.uiState.lastTargetPower.value)

        harness.orchestrator.seedInFlightCommandOpcodeForTest(requestOpcode = 0x08)
        harness.orchestrator.simulateBleControlPointResponseForTest(
            requestOpcode = 0x08,
            resultCode = 0x01,
        )
        manualHandler.advanceBy(0L)
        assertEquals(1, countDiagnosticsEvent(harness.diagnosticsEvents, "trainer_release_runtime", "completion_exit_explicit_close_started"))
        assertEquals(1, countDiagnosticsEvent(harness.diagnosticsEvents, "trainer_release_runtime", "explicit_close_disconnect_unavailable"))

        assertFalse(harness.orchestrator.continueRideRestartWindowOpen())
        manualHandler.advanceBy(1_499L)
        assertFalse(harness.orchestrator.continueRideRestartWindowOpen())
        manualHandler.advanceBy(1L)
        assertTrue(harness.orchestrator.continueRideRestartWindowOpen())
    }

    @Test
    fun completionExitControlResponseCanUpgradeFromNotPossibleToExecute() {
        val manualHandler = ManualHandler()
        val harness = createHarness(mainHandler = manualHandler)
        harness.orchestrator.initialize()
        harness.uiState.screen.value = AppScreen.SESSION
        harness.uiState.ftmsReady.value = true
        harness.uiState.ftmsControlGranted.value = false
        harness.uiState.trainerControlAuthority.value = TrainerControlAuthority.APP_CONTROLLED
        harness.uiState.lastAppControlledTargetPower.value = 180
        harness.uiState.bikeData.value = IndoorBikeData(
            valid = true,
            instantaneousSpeedKmh = null,
            averageSpeedKmh = null,
            instantaneousCadenceRpm = 85.0,
            averageCadenceRpm = null,
            totalDistanceMeters = null,
            resistanceLevel = null,
            instantaneousPowerW = 181,
            averagePowerW = null,
            totalEnergyKcal = null,
            energyPerHourKcal = null,
            energyPerMinuteKcal = null,
            heartRateBpm = null,
            metabolicEquivalent = null,
            elapsedTimeSeconds = null,
            remainingTimeSeconds = null,
        )
        harness.uiState.runner.value = io.github.ewoc2026.ewoc.workout.runner.RunnerState.stopped(
            workoutElapsedSec = 1_200,
        )

        assertTrue(harness.orchestrator.preparePostWorkoutExitWindowAfterCompletion())
        harness.orchestrator.seedInFlightCommandOpcodeForTest(requestOpcode = 0x00)
        harness.orchestrator.simulateBleControlPointResponseForTest(
            requestOpcode = 0x00,
            resultCode = 0x01,
        )

        val requestedDecision = harness.diagnosticsEvents.first { diagnostic ->
            diagnostic.category == "release_ramp" &&
                diagnostic.event == "decision" &&
                diagnostic.context["stage"] == "completion_prep_requested"
        }
        assertEquals("NOT_POSSIBLE", requestedDecision.context["outcome"])
        assertEquals("CONTROL_NOT_GRANTED", requestedDecision.context["reason"])

        val responseDecision = harness.diagnosticsEvents.last { diagnostic ->
            diagnostic.category == "release_ramp" &&
                diagnostic.event == "decision" &&
                diagnostic.context["stage"] == "completion_prep_control_response"
        }
        assertEquals("EXECUTE", responseDecision.context["outcome"])

        manualHandler.advanceBy(250L)
        assertEquals(165, harness.uiState.lastTargetPower.value)
    }

    @Test
    fun completionExitPrepRequestControlTimeoutStartsSyntheticSettleWindowWhenTransportMissing() {
        val manualHandler = ManualHandler()
        val harness = createHarness(mainHandler = manualHandler)
        harness.orchestrator.initialize()
        harness.uiState.screen.value = AppScreen.SESSION
        harness.uiState.ftmsReady.value = true
        harness.uiState.ftmsControlGranted.value = false
        harness.uiState.runner.value = io.github.ewoc2026.ewoc.workout.runner.RunnerState.stopped(
            workoutElapsedSec = 1_200,
        )

        assertTrue(harness.orchestrator.preparePostWorkoutExitWindowAfterCompletion())
        assertTrue(harness.orchestrator.hasPreparedPostWorkoutExitWindow())
        assertFalse(harness.orchestrator.preparedPostWorkoutSummaryWindowOpenForTest())

        harness.orchestrator.simulateRequestControlTimeoutForTest()
        manualHandler.advanceBy(0L)

        assertFalse(harness.orchestrator.explicitTrainerCloseInProgressForTest())
        assertEquals(1_500L, harness.orchestrator.explicitTrainerReconnectDelayRemainingForTest())
        assertFalse(harness.uiState.ftmsReady.value)
        assertFalse(harness.uiState.ftmsControlGranted.value)
        assertTrue(harness.orchestrator.preparedPostWorkoutSummaryWindowOpenForTest())
        assertFalse(harness.orchestrator.continueRideRestartWindowOpen())
        manualHandler.advanceBy(1_499L)
        assertEquals(1L, harness.orchestrator.explicitTrainerReconnectDelayRemainingForTest())
        assertTrue(harness.orchestrator.preparedPostWorkoutSummaryWindowOpenForTest())
        assertFalse(harness.orchestrator.continueRideRestartWindowOpen())
        manualHandler.advanceBy(1L)
        assertEquals(0L, harness.orchestrator.explicitTrainerReconnectDelayRemainingForTest())
        assertTrue(harness.orchestrator.preparedPostWorkoutSummaryWindowOpenForTest())
        assertTrue(harness.orchestrator.continueRideRestartWindowOpen())
    }

    @Test
    fun completionExitPrepSoftStopNonSuccessResponseFallsBackToSyntheticSettleWindow() {
        val manualHandler = ManualHandler()
        val harness = createHarness(mainHandler = manualHandler)
        harness.orchestrator.initialize()
        harness.uiState.screen.value = AppScreen.SESSION
        harness.uiState.ftmsReady.value = true
        harness.uiState.ftmsControlGranted.value = true
        harness.uiState.runner.value = io.github.ewoc2026.ewoc.workout.runner.RunnerState.stopped(
            workoutElapsedSec = 1_200,
        )

        assertTrue(harness.orchestrator.preparePostWorkoutExitWindowAfterCompletion())
        assertFalse(harness.orchestrator.preparedPostWorkoutSummaryWindowOpenForTest())

        harness.orchestrator.seedInFlightCommandOpcodeForTest(requestOpcode = 0x08)
        harness.orchestrator.simulateBleControlPointResponseForTest(
            requestOpcode = 0x08,
            resultCode = 0x03,
        )
        manualHandler.advanceBy(0L)

        assertFalse(harness.orchestrator.explicitTrainerCloseInProgressForTest())
        assertEquals(1_500L, harness.orchestrator.explicitTrainerReconnectDelayRemainingForTest())
        assertFalse(harness.uiState.ftmsReady.value)
        assertFalse(harness.uiState.ftmsControlGranted.value)
        assertTrue(harness.orchestrator.preparedPostWorkoutSummaryWindowOpenForTest())
        assertFalse(harness.orchestrator.continueRideRestartWindowOpen())
        manualHandler.advanceBy(1_499L)
        assertEquals(1L, harness.orchestrator.explicitTrainerReconnectDelayRemainingForTest())
        assertTrue(harness.orchestrator.preparedPostWorkoutSummaryWindowOpenForTest())
        assertFalse(harness.orchestrator.continueRideRestartWindowOpen())
        manualHandler.advanceBy(1L)
        assertEquals(0L, harness.orchestrator.explicitTrainerReconnectDelayRemainingForTest())
        assertTrue(harness.orchestrator.preparedPostWorkoutSummaryWindowOpenForTest())
        assertTrue(harness.orchestrator.continueRideRestartWindowOpen())
    }

    @Test
    fun stopFlowWithMissingTransportUsesSyntheticSettleWindowInsteadOfStickingClosed() {
        val manualHandler = ManualHandler()
        val harness = createHarness(mainHandler = manualHandler)
        harness.orchestrator.initialize()
        harness.uiState.screen.value = AppScreen.SESSION
        harness.uiState.ftmsReady.value = true
        harness.uiState.ftmsControlGranted.value = true
        harness.uiState.runner.value = io.github.ewoc2026.ewoc.workout.runner.RunnerState.stopped(
            workoutElapsedSec = 1_200,
        )

        harness.orchestrator.endSessionAndGoToSummary()
        harness.orchestrator.simulateStopAcknowledgedForTest()
        manualHandler.advanceBy(0L)
        assertEquals(AppScreen.SUMMARY, harness.uiState.screen.value)

        assertFalse(harness.orchestrator.explicitTrainerCloseInProgressForTest())
        assertEquals(1_500L, harness.orchestrator.explicitTrainerReconnectDelayRemainingForTest())
        assertFalse(harness.uiState.ftmsReady.value)
        assertFalse(harness.uiState.ftmsControlGranted.value)
        assertFalse(harness.orchestrator.continueRideRestartWindowOpen())
        manualHandler.advanceBy(1_499L)
        assertEquals(1L, harness.orchestrator.explicitTrainerReconnectDelayRemainingForTest())
        assertFalse(harness.orchestrator.continueRideRestartWindowOpen())
        manualHandler.advanceBy(1L)
        assertEquals(0L, harness.orchestrator.explicitTrainerReconnectDelayRemainingForTest())
        assertTrue(harness.orchestrator.continueRideRestartWindowOpen())
    }

    @Test
    fun completionExitPrepMockTrainerLeavesRestartWindowOpenImmediately() {
        val manualHandler = ManualHandler()
        val harness = createHarness(
            mainHandler = manualHandler,
            isMockTrainerModeEnabled = { true },
        )
        harness.orchestrator.initialize()
        harness.uiState.screen.value = AppScreen.SESSION
        harness.uiState.ftmsReady.value = false
        harness.uiState.ftmsControlGranted.value = false
        harness.uiState.runner.value = io.github.ewoc2026.ewoc.workout.runner.RunnerState.stopped(
            workoutElapsedSec = 1_200,
        )

        assertTrue(harness.orchestrator.preparePostWorkoutExitWindowAfterCompletion())
        assertTrue(harness.orchestrator.hasPreparedPostWorkoutExitWindow())
        assertTrue(harness.orchestrator.preparedPostWorkoutSummaryWindowOpenForTest())
        assertTrue(harness.orchestrator.continueRideRestartWindowOpen())
    }

    @Test
    fun connectFlowTimeoutShowsPromptAndStaysInConnecting() {
        val manualHandler = ManualHandler()
        val harness = createHarness(mainHandler = manualHandler)
        harness.orchestrator.initialize()

        harness.orchestrator.beginConnectFlowForTest()
        assertEquals(AppScreen.CONNECTING, harness.uiState.screen.value)

        manualHandler.advanceBy(14_999L)
        assertEquals(AppScreen.CONNECTING, harness.uiState.screen.value)

        manualHandler.advanceBy(1L)
        assertEquals(AppScreen.CONNECTING, harness.uiState.screen.value)
        assertEquals(StopFlowState.IDLE, harness.uiState.stopFlowState.value)
        assertNotNull(harness.uiState.connectingTimeoutMessage.value)
        assertNull(harness.uiState.connectionIssueMessage.value)
        assertEquals(0, harness.currentAllowScreenOffCalls)
    }

    @Test
    fun connectingTimeoutKeepWaitingReArmsTimeoutWithoutRollback() {
        val manualHandler = ManualHandler()
        val harness = createHarness(mainHandler = manualHandler)
        harness.orchestrator.initialize()

        harness.orchestrator.beginConnectFlowForTest()
        manualHandler.advanceBy(15_000L)
        assertNotNull(harness.uiState.connectingTimeoutMessage.value)
        assertEquals(AppScreen.CONNECTING, harness.uiState.screen.value)

        harness.orchestrator.onConnectingTimeoutKeepWaiting()
        assertNull(harness.uiState.connectingTimeoutMessage.value)
        assertEquals(AppScreen.CONNECTING, harness.uiState.screen.value)

        manualHandler.advanceBy(14_999L)
        assertNull(harness.uiState.connectingTimeoutMessage.value)
        manualHandler.advanceBy(1L)
        assertNotNull(harness.uiState.connectingTimeoutMessage.value)
        assertEquals(AppScreen.CONNECTING, harness.uiState.screen.value)
    }

    @Test
    fun connectingTimeoutBackToMenuRollsBackWithRecoveryPrompt() {
        val manualHandler = ManualHandler()
        val harness = createHarness(mainHandler = manualHandler)
        harness.orchestrator.initialize()

        harness.orchestrator.beginConnectFlowForTest()
        manualHandler.advanceBy(15_000L)
        assertNotNull(harness.uiState.connectingTimeoutMessage.value)

        harness.orchestrator.onConnectingTimeoutBackToMenu()

        assertEquals(AppScreen.MENU, harness.uiState.screen.value)
        assertNull(harness.uiState.connectingTimeoutMessage.value)
        assertNotNull(harness.uiState.connectionIssueMessage.value)
        assertTrue(harness.uiState.suggestTrainerSearchAfterConnectionIssue.value)
        assertFalse(harness.uiState.suggestOpenSettingsAfterConnectionIssue.value)
        assertEquals(1, harness.currentAllowScreenOffCalls)
    }

    @Test
    fun connectTimeoutBackToMenuRecordsRollbackReasonFromTestHookFlow() {
        val manualHandler = ManualHandler()
        val harness = createHarness(mainHandler = manualHandler)
        harness.orchestrator.initialize()

        harness.orchestrator.beginConnectFlowForTest()
        assertEquals(AppScreen.CONNECTING, harness.uiState.screen.value)

        manualHandler.advanceBy(15_000L)
        assertNotNull(harness.uiState.connectingTimeoutMessage.value)

        harness.orchestrator.onConnectingTimeoutBackToMenu()

        assertEquals(AppScreen.MENU, harness.uiState.screen.value)
        assertEquals(
            "connect_timeout_back_to_menu",
            firstSessionEventContext(
                events = harness.diagnosticsEvents,
                event = "rollback_to_menu",
                key = "reason",
            ),
        )
        assertEquals(0, countSessionEvent(harness.diagnosticsEvents, "scope_ended"))
    }

    @Test
    fun repeatedStartRequestReplacesActiveSessionScopeBeforeMockConnectionCompletes() {
        val manualHandler = ManualHandler()
        var sessionIdCounter = 0
        val harness = createHarness(
            mainHandler = manualHandler,
            currentFtmsDeviceMac = { null },
            isMockTrainerModeEnabled = { true },
            sessionIdFactory = {
                sessionIdCounter += 1
                "session-$sessionIdCounter"
            },
        )
        harness.orchestrator.initialize()
        harness.uiState.selectedWorkout.value = readyWorkout()
        harness.uiState.workoutReady.value = true

        harness.orchestrator.startSessionConnection()
        assertEquals("session-1", harness.orchestrator.activeSessionIdForTest())
        assertEquals(AppScreen.CONNECTING, harness.uiState.screen.value)

        harness.orchestrator.startSessionConnection()

        assertEquals("session-2", harness.orchestrator.activeSessionIdForTest())
        assertEquals(AppScreen.CONNECTING, harness.uiState.screen.value)
        val scopeStartedEvents = harness.diagnosticsEvents.filter { diagnostic ->
            diagnostic.category == "session" && diagnostic.event == "scope_started"
        }
        assertEquals(2, scopeStartedEvents.size)
        assertEquals(0, countSessionEvent(harness.diagnosticsEvents, "scope_ended"))
        assertEquals(
            "start_session_mock",
            scopeStartedEvents.first().context["entryPoint"],
        )
        assertEquals(
            "session-1",
            scopeStartedEvents[1].context["replacedSessionId"],
        )

        manualHandler.advanceBy(0L)
        assertEquals(AppScreen.SESSION, harness.uiState.screen.value)
    }

    @Test
    fun repeatedStartRequestReplacesActiveSessionScopeWhileWaitingForBluetoothPermission() {
        var sessionIdCounter = 0
        val harness = createHarness(
            ensureBluetoothPermission = { false },
            sessionIdFactory = {
                sessionIdCounter += 1
                "session-$sessionIdCounter"
            },
        )
        harness.orchestrator.initialize()
        harness.uiState.selectedWorkout.value = readyWorkout()
        harness.uiState.workoutReady.value = true

        harness.orchestrator.startSessionConnection()
        assertEquals("session-1", harness.orchestrator.activeSessionIdForTest())
        assertTrue(harness.uiState.pendingSessionStartAfterPermission)
        assertEquals(AppScreen.MENU, harness.uiState.screen.value)

        harness.orchestrator.startSessionConnection()

        assertEquals("session-2", harness.orchestrator.activeSessionIdForTest())
        assertTrue(harness.uiState.pendingSessionStartAfterPermission)
        assertEquals(AppScreen.MENU, harness.uiState.screen.value)
        val scopeStartedEvents = harness.diagnosticsEvents.filter { diagnostic ->
            diagnostic.category == "session" && diagnostic.event == "scope_started"
        }
        assertEquals(2, scopeStartedEvents.size)
        assertEquals(0, countSessionEvent(harness.diagnosticsEvents, "scope_ended"))
        assertEquals(
            "start_session_ble",
            scopeStartedEvents.first().context["entryPoint"],
        )
        assertEquals(
            "session-1",
            scopeStartedEvents[1].context["replacedSessionId"],
        )
    }

    @Test
    fun permissionDeniedAfterRepeatedPendingStartEndsLatestSessionScopeAndRequiresRetry() {
        val harness = createHarness(
            ensureBluetoothPermission = { false },
            sessionIdFactory = object : () -> String {
                private var sessionIdCounter = 0

                override fun invoke(): String {
                    sessionIdCounter += 1
                    return "session-$sessionIdCounter"
                }
            },
        )
        harness.orchestrator.initialize()
        harness.uiState.selectedWorkout.value = readyWorkout()
        harness.uiState.workoutReady.value = true

        harness.orchestrator.startSessionConnection()
        harness.orchestrator.startSessionConnection()

        assertEquals("session-2", harness.orchestrator.activeSessionIdForTest())
        assertTrue(harness.uiState.pendingSessionStartAfterPermission)

        harness.deliverPermissionResult(granted = false)

        assertFalse(harness.uiState.pendingSessionStartAfterPermission)
        assertEquals(AppScreen.MENU, harness.uiState.screen.value)
        assertNull(harness.orchestrator.activeSessionIdForTest())
        assertTrue(harness.uiState.suggestOpenSettingsAfterConnectionIssue.value)
        assertFalse(harness.uiState.suggestTrainerSearchAfterConnectionIssue.value)
        assertEquals(
            "permission_denied",
            firstSessionEventContext(
                events = harness.diagnosticsEvents,
                event = "scope_ended",
                key = "reason",
            ),
        )
        val scopeEnded = harness.diagnosticsEvents.single { diagnostic ->
            diagnostic.category == "session" && diagnostic.event == "scope_ended"
        }
        assertEquals("session-2", scopeEnded.sessionId)

        harness.deliverPermissionResult(granted = true)

        assertFalse(harness.uiState.pendingSessionStartAfterPermission)
        assertEquals(AppScreen.MENU, harness.uiState.screen.value)
        assertEquals(1, countSessionEvent(harness.diagnosticsEvents, "scope_ended"))
        assertEquals(0, countSessionEvent(harness.diagnosticsEvents, "connect_ble_clients"))
    }

    @Test
    fun repeatedPermissionDeniedCallbackAfterRepeatedPendingStartDoesNotEndScopeTwice() {
        val harness = createHarness(
            ensureBluetoothPermission = { false },
            sessionIdFactory = object : () -> String {
                private var sessionIdCounter = 0

                override fun invoke(): String {
                    sessionIdCounter += 1
                    return "session-$sessionIdCounter"
                }
            },
        )
        harness.orchestrator.initialize()
        harness.uiState.selectedWorkout.value = readyWorkout()
        harness.uiState.workoutReady.value = true

        harness.orchestrator.startSessionConnection()
        harness.orchestrator.startSessionConnection()

        assertEquals("session-2", harness.orchestrator.activeSessionIdForTest())
        assertTrue(harness.uiState.pendingSessionStartAfterPermission)
        assertEquals(AppScreen.MENU, harness.uiState.screen.value)

        val legitimateRequestId = harness.permissionRequestId
        harness.deliverPermissionResult(granted = false)

        assertFalse(harness.uiState.pendingSessionStartAfterPermission)
        assertNull(harness.orchestrator.activeSessionIdForTest())
        assertEquals(AppScreen.MENU, harness.uiState.screen.value)
        assertEquals(1, countSessionEvent(harness.diagnosticsEvents, "scope_ended"))
        val firstDeniedEvent = harness.diagnosticsEvents.last { diagnostic ->
            diagnostic.category == "permission" && diagnostic.event == "bluetooth_connect_denied"
        }
        assertEquals("true", firstDeniedEvent.context["pendingStartWasActive"])

        harness.deliverPermissionResult(granted = false, requestId = legitimateRequestId)

        assertFalse(harness.uiState.pendingSessionStartAfterPermission)
        assertNull(harness.orchestrator.activeSessionIdForTest())
        assertEquals(AppScreen.MENU, harness.uiState.screen.value)
        assertEquals(1, countSessionEvent(harness.diagnosticsEvents, "scope_ended"))
        val staleCallbackEvents = harness.diagnosticsEvents.filter { diagnostic ->
            diagnostic.category == "permission" && diagnostic.event == "bluetooth_connect_stale_callback"
        }
        assertEquals(1, staleCallbackEvents.size)
        assertEquals("false", staleCallbackEvents.first().context["granted"])
    }

    @Test
    fun stalePermissionDeniedCallbackAfterExplicitRetryIsRejectedByRequestCorrelation() {
        val harness = createHarness(
            ensureBluetoothPermission = { false },
            sessionIdFactory = object : () -> String {
                private var sessionIdCounter = 0

                override fun invoke(): String {
                    sessionIdCounter += 1
                    return "session-$sessionIdCounter"
                }
            },
        )
        harness.orchestrator.initialize()
        harness.uiState.selectedWorkout.value = readyWorkout()
        harness.uiState.workoutReady.value = true

        harness.orchestrator.startSessionConnection()
        assertEquals("session-1", harness.orchestrator.activeSessionIdForTest())
        assertTrue(harness.uiState.pendingSessionStartAfterPermission)
        val session1RequestId = harness.permissionRequestId

        harness.deliverPermissionResult(granted = false)
        assertNull(harness.orchestrator.activeSessionIdForTest())
        assertFalse(harness.uiState.pendingSessionStartAfterPermission)
        assertEquals(AppScreen.MENU, harness.uiState.screen.value)

        harness.orchestrator.startSessionConnection()
        assertEquals("session-2", harness.orchestrator.activeSessionIdForTest())
        assertTrue(harness.uiState.pendingSessionStartAfterPermission)
        assertFalse(harness.uiState.suggestOpenSettingsAfterConnectionIssue.value)
        val session2RequestId = harness.permissionRequestId
        assertTrue(session2RequestId != session1RequestId)

        harness.deliverPermissionResult(granted = false, requestId = session1RequestId)

        assertEquals("session-2", harness.orchestrator.activeSessionIdForTest())
        assertTrue(harness.uiState.pendingSessionStartAfterPermission)
        assertEquals(AppScreen.MENU, harness.uiState.screen.value)
        assertFalse(harness.uiState.suggestOpenSettingsAfterConnectionIssue.value)
        assertEquals(1, countSessionEvent(harness.diagnosticsEvents, "scope_ended"))
        val scopeEndedEvents = harness.diagnosticsEvents.filter { diagnostic ->
            diagnostic.category == "session" && diagnostic.event == "scope_ended"
        }
        assertEquals("session-1", scopeEndedEvents.single().sessionId)
        val staleCallbackEvents = harness.diagnosticsEvents.filter { diagnostic ->
            diagnostic.category == "permission" && diagnostic.event == "bluetooth_connect_stale_callback"
        }
        assertEquals(1, staleCallbackEvents.size)
        assertEquals("false", staleCallbackEvents.first().context["granted"])
        assertEquals(session1RequestId.toString(), staleCallbackEvents.first().context["requestId"])
    }

    @Test
    fun legitimateCallbackAfterStaleRejectionProcessesNormally() {
        val harness = createHarness(
            ensureBluetoothPermission = { false },
            sessionIdFactory = object : () -> String {
                private var sessionIdCounter = 0

                override fun invoke(): String {
                    sessionIdCounter += 1
                    return "session-$sessionIdCounter"
                }
            },
        )
        harness.orchestrator.initialize()
        harness.uiState.selectedWorkout.value = readyWorkout()
        harness.uiState.workoutReady.value = true

        harness.orchestrator.startSessionConnection()
        val session1RequestId = harness.permissionRequestId

        harness.deliverPermissionResult(granted = false)
        assertNull(harness.orchestrator.activeSessionIdForTest())

        harness.orchestrator.startSessionConnection()
        assertEquals("session-2", harness.orchestrator.activeSessionIdForTest())
        assertTrue(harness.uiState.pendingSessionStartAfterPermission)
        val session2RequestId = harness.permissionRequestId

        harness.deliverPermissionResult(granted = false, requestId = session1RequestId)

        assertEquals("session-2", harness.orchestrator.activeSessionIdForTest())
        assertTrue(harness.uiState.pendingSessionStartAfterPermission)
        assertEquals(1, countDiagnosticsEvent(harness.diagnosticsEvents, "permission", "bluetooth_connect_stale_callback"))

        harness.deliverPermissionResult(granted = false, requestId = session2RequestId)

        assertNull(harness.orchestrator.activeSessionIdForTest())
        assertFalse(harness.uiState.pendingSessionStartAfterPermission)
        assertEquals(AppScreen.MENU, harness.uiState.screen.value)
        assertTrue(harness.uiState.suggestOpenSettingsAfterConnectionIssue.value)
        assertEquals(2, countSessionEvent(harness.diagnosticsEvents, "scope_ended"))
        val scopeEndedEvents = harness.diagnosticsEvents.filter { diagnostic ->
            diagnostic.category == "session" && diagnostic.event == "scope_ended"
        }
        assertEquals("session-1", scopeEndedEvents.first().sessionId)
        assertEquals("session-2", scopeEndedEvents.last().sessionId)
        assertEquals("permission_denied", scopeEndedEvents.last().context["reason"])
    }

    @Test
    fun repeatedPermissionGrantedCallbackAfterRepeatedPendingStartConnectFailureDoesNotEndScopeTwice() {
        var connectPermissionGranted = false
        var currentTrainerMac: String? = "AA:BB:CC:DD:EE:FF"
        val harness = createHarness(
            ensureBluetoothPermission = { connectPermissionGranted },
            currentFtmsDeviceMac = { currentTrainerMac },
            sessionIdFactory = object : () -> String {
                private var sessionIdCounter = 0

                override fun invoke(): String {
                    sessionIdCounter += 1
                    return "session-$sessionIdCounter"
                }
            },
        )
        harness.orchestrator.initialize()
        harness.uiState.selectedWorkout.value = readyWorkout()
        harness.uiState.workoutReady.value = true

        harness.orchestrator.startSessionConnection()
        harness.orchestrator.startSessionConnection()

        assertEquals("session-2", harness.orchestrator.activeSessionIdForTest())
        assertTrue(harness.uiState.pendingSessionStartAfterPermission)

        val legitimateRequestId = harness.permissionRequestId
        currentTrainerMac = null
        connectPermissionGranted = true
        harness.deliverPermissionResult(granted = true)

        assertFalse(harness.uiState.pendingSessionStartAfterPermission)
        assertNull(harness.orchestrator.activeSessionIdForTest())
        assertEquals(AppScreen.MENU, harness.uiState.screen.value)
        assertEquals(1, countSessionEvent(harness.diagnosticsEvents, "scope_ended"))
        assertEquals(0, countSessionEvent(harness.diagnosticsEvents, "connect_ble_clients"))
        val firstGrantedEvent = harness.diagnosticsEvents.last { diagnostic ->
            diagnostic.category == "permission" && diagnostic.event == "bluetooth_connect_granted"
        }
        assertEquals("true", firstGrantedEvent.context["pendingStart"])

        harness.deliverPermissionResult(granted = true, requestId = legitimateRequestId)

        assertFalse(harness.uiState.pendingSessionStartAfterPermission)
        assertNull(harness.orchestrator.activeSessionIdForTest())
        assertEquals(AppScreen.MENU, harness.uiState.screen.value)
        assertEquals(1, countSessionEvent(harness.diagnosticsEvents, "scope_ended"))
        assertEquals(0, countSessionEvent(harness.diagnosticsEvents, "connect_ble_clients"))
        val staleCallbackEvents = harness.diagnosticsEvents.filter { diagnostic ->
            diagnostic.category == "permission" && diagnostic.event == "bluetooth_connect_stale_callback"
        }
        assertEquals(1, staleCallbackEvents.size)
        assertEquals("true", staleCallbackEvents.first().context["granted"])
    }

    @Test
    fun connectingTimeoutRetryInMockModeTransitionsToSession() {
        val manualHandler = ManualHandler()
        val harness = createHarness(
            mainHandler = manualHandler,
            isMockTrainerModeEnabled = { true },
        )
        harness.orchestrator.initialize()
        harness.uiState.selectedWorkout.value = readyWorkout()
        harness.uiState.workoutReady.value = true

        harness.orchestrator.beginConnectFlowForTest()
        manualHandler.advanceBy(15_000L)
        assertNotNull(harness.uiState.connectingTimeoutMessage.value)

        harness.orchestrator.onConnectingTimeoutRetry()
        assertNull(harness.uiState.connectingTimeoutMessage.value)
        assertEquals(AppScreen.CONNECTING, harness.uiState.screen.value)

        manualHandler.advanceBy(0L)
        assertEquals(AppScreen.SESSION, harness.uiState.screen.value)
        assertNull(harness.uiState.connectingTimeoutMessage.value)

        manualHandler.advanceBy(15_000L)
        assertEquals(AppScreen.SESSION, harness.uiState.screen.value)
        assertNull(harness.uiState.connectingTimeoutMessage.value)
    }

    @Test
    fun connectFlowTimeoutIsCancelledAfterRequestControlGranted() {
        val manualHandler = ManualHandler()
        val harness = createHarness(mainHandler = manualHandler)
        harness.orchestrator.initialize()

        harness.orchestrator.beginConnectFlowForTest()
        assertEquals(AppScreen.CONNECTING, harness.uiState.screen.value)

        harness.orchestrator.simulateRequestControlGrantedForTest()
        assertEquals(AppScreen.SESSION, harness.uiState.screen.value)
        assertEquals(SessionPhase.RUNNING, harness.sessionManager.getPhase())
        assertNull(harness.uiState.connectingTimeoutMessage.value)

        manualHandler.advanceBy(15_000L)
        assertEquals(AppScreen.SESSION, harness.uiState.screen.value)
        assertEquals(0, harness.currentAllowScreenOffCalls)
        assertNull(harness.uiState.connectionIssueMessage.value)
        assertNull(harness.uiState.connectingTimeoutMessage.value)
    }

    @Test
    fun connectPermissionDeniedThenGrantedKeepsFlowStableUntilExplicitRetry() {
        var connectPermissionGranted = false
        val harness = createHarness(
            ensureBluetoothPermission = { connectPermissionGranted },
        )
        harness.orchestrator.initialize()
        harness.uiState.selectedWorkout.value = readyWorkout()
        harness.uiState.workoutReady.value = true

        harness.orchestrator.startSessionConnection()

        assertTrue(harness.uiState.pendingSessionStartAfterPermission)
        assertEquals(AppScreen.MENU, harness.uiState.screen.value)

        harness.deliverPermissionResult(granted = false)
        assertFalse(harness.uiState.pendingSessionStartAfterPermission)
        assertEquals(AppScreen.MENU, harness.uiState.screen.value)
        assertTrue(harness.uiState.suggestOpenSettingsAfterConnectionIssue.value)
        assertFalse(harness.uiState.suggestTrainerSearchAfterConnectionIssue.value)
        assertNotNull(harness.uiState.connectionIssueMessage.value)

        connectPermissionGranted = true
        harness.deliverPermissionResult(granted = true)
        assertFalse(harness.uiState.pendingSessionStartAfterPermission)
        assertEquals(AppScreen.MENU, harness.uiState.screen.value)
        assertTrue(harness.uiState.suggestOpenSettingsAfterConnectionIssue.value)
    }

    @Test
    fun connectPermissionDeniedAllowsPendingStartToBeReArmedOnExplicitRetry() {
        val harness = createHarness(
            ensureBluetoothPermission = { false },
        )
        harness.orchestrator.initialize()
        harness.uiState.selectedWorkout.value = readyWorkout()
        harness.uiState.workoutReady.value = true

        harness.orchestrator.startSessionConnection()
        assertTrue(harness.uiState.pendingSessionStartAfterPermission)
        assertEquals(AppScreen.MENU, harness.uiState.screen.value)

        harness.deliverPermissionResult(granted = false)
        assertFalse(harness.uiState.pendingSessionStartAfterPermission)
        assertEquals(AppScreen.MENU, harness.uiState.screen.value)
        assertTrue(harness.uiState.suggestOpenSettingsAfterConnectionIssue.value)
        assertFalse(harness.uiState.suggestTrainerSearchAfterConnectionIssue.value)

        harness.orchestrator.startSessionConnection()
        assertTrue(harness.uiState.pendingSessionStartAfterPermission)
        assertEquals(AppScreen.MENU, harness.uiState.screen.value)
        assertFalse(harness.uiState.suggestOpenSettingsAfterConnectionIssue.value)
        assertFalse(harness.uiState.suggestTrainerSearchAfterConnectionIssue.value)
        assertNull(harness.uiState.connectionIssueMessage.value)
    }

    @Test
    fun permissionGrantResumeEndsSessionScopeWhenTrainerMacDisappearsBeforeConnectStarts() {
        var connectPermissionGranted = false
        var currentTrainerMac: String? = "AA:BB:CC:DD:EE:FF"
        val harness = createHarness(
            ensureBluetoothPermission = { connectPermissionGranted },
            currentFtmsDeviceMac = { currentTrainerMac },
            sessionIdFactory = { "session-permission-mac-loss" },
        )
        harness.orchestrator.initialize()
        harness.uiState.selectedWorkout.value = readyWorkout()
        harness.uiState.workoutReady.value = true

        harness.orchestrator.startSessionConnection()

        assertTrue(harness.uiState.pendingSessionStartAfterPermission)
        assertEquals("session-permission-mac-loss", harness.orchestrator.activeSessionIdForTest())

        currentTrainerMac = null
        connectPermissionGranted = true
        harness.deliverPermissionResult(granted = true)

        assertFalse(harness.uiState.pendingSessionStartAfterPermission)
        assertEquals(AppScreen.MENU, harness.uiState.screen.value)
        assertNull(harness.orchestrator.activeSessionIdForTest())
        assertEquals(0, countSessionEvent(harness.diagnosticsEvents, "connect_ble_clients"))
        assertEquals(
            "connect_clients_failed_after_permission",
            firstSessionEventContext(
                events = harness.diagnosticsEvents,
                event = "scope_ended",
                key = "reason",
            ),
        )
    }

    @Test
    fun startIsBlockedWithoutTrainerMacWhenMockModeIsOff() {
        val harness = createHarness(
            currentFtmsDeviceMac = { null },
            isMockTrainerModeEnabled = { false },
        )
        harness.orchestrator.initialize()
        harness.uiState.selectedWorkout.value = readyWorkout()
        harness.uiState.workoutReady.value = true

        harness.orchestrator.startSessionConnection()

        assertEquals(AppScreen.MENU, harness.uiState.screen.value)
        assertFalse(harness.uiState.pendingSessionStartAfterPermission)
        assertFalse(harness.uiState.ftmsReady.value)
        assertFalse(harness.uiState.ftmsControlGranted.value)
    }

    @Test
    fun strictFallbackPolicyBlocksStartWhenWorkoutNeedsLegacyMapping() {
        val harness = createHarness(
            allowLegacyWorkoutFallback = false,
        )
        harness.uiState.selectedWorkout.value = unsupportedWorkout()

        harness.orchestrator.onFtpWattsChanged()

        assertFalse(harness.uiState.workoutReady.value)
        assertTrue(harness.uiState.workoutExecutionModeIsError.value)
        assertTrue(
            requireNotNull(harness.uiState.workoutExecutionModeMessage.value).contains(
                "Workout execution blocked",
            ),
        )
        assertEquals(
            1,
            countDiagnosticsEvent(
                events = harness.diagnosticsEvents,
                category = "workout_execution",
                event = "mapping_policy_applied",
            ),
        )
        assertEquals(
            "workout.execution_mapping_blocked",
            firstDiagnosticsEventContext(
                events = harness.diagnosticsEvents,
                category = "workout_execution",
                event = "mapping_policy_applied",
                key = "reason",
            ),
        )
    }

    @Test
    fun permissiveFallbackPolicyAllowsStartWhenWorkoutNeedsLegacyMapping() {
        val harness = createHarness(
            allowLegacyWorkoutFallback = true,
        )
        harness.uiState.selectedWorkout.value = unsupportedWorkout()

        harness.orchestrator.onFtpWattsChanged()

        assertTrue(harness.uiState.workoutReady.value)
        assertTrue(harness.uiState.workoutExecutionModeIsError.value)
        assertTrue(
            requireNotNull(harness.uiState.workoutExecutionModeMessage.value).contains(
                "degraded",
                ignoreCase = true,
            ),
        )
        assertEquals(
            1,
            countDiagnosticsEvent(
                events = harness.diagnosticsEvents,
                category = "workout_execution",
                event = "mapping_policy_applied",
            ),
        )
        assertEquals(
            "workout.execution_mapping_degraded",
            firstDiagnosticsEventContext(
                events = harness.diagnosticsEvents,
                category = "workout_execution",
                event = "mapping_policy_applied",
                key = "reason",
            ),
        )
    }

    @Test
    fun importedErgoWorkoutBecomesReadyWhenPowerOnlyExecutionMapsSuccessfully() {
        val harness = createHarness()
        harness.uiState.selectedImportedWorkout.value = powerOnlyImportedWorkout()

        harness.orchestrator.onFtpWattsChanged()

        assertTrue(harness.uiState.workoutReady.value)
        assertEquals(2, harness.uiState.selectedWorkoutStepCount.value)
        assertEquals(12.1, harness.uiState.selectedWorkoutPlannedTss.value!!, 0.0)
        assertNull(harness.uiState.workoutExecutionModeMessage.value)
        assertFalse(harness.uiState.workoutExecutionModeIsError.value)
    }

    @Test
    fun importedErgoWorkoutWithHeartRateControlRemainsStartEligibleAtSelectionTime() {
        val harness = createHarness(
            allowLegacyWorkoutFallback = false,
        )
        harness.uiState.selectedImportedWorkout.value = heartRateImportedWorkout()

        harness.orchestrator.onFtpWattsChanged()

        assertTrue(harness.uiState.workoutReady.value)
        assertEquals(1, harness.uiState.selectedWorkoutStepCount.value)
        assertEquals(8.6, harness.uiState.selectedWorkoutPlannedTss.value ?: 0.0, 0.0)
        assertFalse(harness.uiState.workoutExecutionModeIsError.value)
        assertNull(harness.uiState.workoutExecutionModeMessage.value)
        assertEquals(
            "imported_hr_runtime_deferred_to_session_start",
            firstDiagnosticsEventContext(
                events = harness.diagnosticsEvents,
                category = "workout_execution",
                event = "mapping_policy_applied",
                key = "reason",
            ),
        )
    }

    @Test
    fun importedErgoWorkoutHeartRateStartFailsAtCadenceWhenHrSignalIsMissing() {
        val manualHandler = ManualHandler()
        val parseDispatcher = ManualIndoorBikeParsingDispatcher()
        val harness = createHarness(
            mainHandler = manualHandler,
            allowLegacyWorkoutFallback = false,
            indoorBikeParsingDispatcher = parseDispatcher,
        )
        harness.uiState.selectedImportedWorkout.value = heartRateImportedWorkout()

        harness.orchestrator.initialize()
        harness.orchestrator.onFtpWattsChanged()
        harness.orchestrator.beginConnectFlowForTest()
        manualHandler.advanceBy(0L)
        harness.orchestrator.simulateRequestControlGrantedForTest()
        harness.orchestrator.simulateControlOwnershipChangedForTest(controlGranted = true)
        manualHandler.advanceBy(0L)
        harness.orchestrator.simulateIndoorBikeDataForTest(
            indoorBikePayloadWithCadence(85.0),
        )
        parseDispatcher.runAll()
        manualHandler.advanceBy(1_000L)

        assertFalse(harness.uiState.runner.value.running)
        assertTrue(
            requireNotNull(harness.uiState.workoutExecutionModeMessage.value).contains(
                "HEART_RATE_SIGNAL",
            ),
        )
    }

    @Test
    fun importedErgoWorkoutHeartRateRuntimeFallsBackAfterSignalLoss() {
        val manualHandler = ManualHandler()
        val parseDispatcher = ManualIndoorBikeParsingDispatcher()
        val harness = createHarness(
            mainHandler = manualHandler,
            allowLegacyWorkoutFallback = false,
            indoorBikeParsingDispatcher = parseDispatcher,
        )
        harness.uiState.selectedImportedWorkout.value = heartRateImportedWorkout()

        harness.orchestrator.initialize()
        harness.orchestrator.onFtpWattsChanged()
        harness.orchestrator.beginConnectFlowForTest()
        manualHandler.advanceBy(0L)
        harness.orchestrator.simulateRequestControlGrantedForTest()
        harness.orchestrator.simulateControlOwnershipChangedForTest(controlGranted = true)
        manualHandler.advanceBy(0L)
        harness.orchestrator.simulateIndoorBikeDataForTest(
            indoorBikePayloadWithCadenceAndHeartRate(
                cadenceRpm = 85.0,
                heartRateBpm = 145,
            ),
        )
        parseDispatcher.runAll()
        manualHandler.advanceBy(1_000L)

        assertTrue(harness.uiState.runner.value.running)
        assertEquals(180, harness.uiState.lastTargetPower.value)

        harness.orchestrator.simulateIndoorBikeDataForTest(
            indoorBikePayloadWithCadence(85.0),
        )
        parseDispatcher.runAll()
        manualHandler.advanceBy(1_000L)

        assertTrue(harness.uiState.runner.value.running)
        assertEquals(90, harness.uiState.lastTargetPower.value)

        harness.orchestrator.simulateIndoorBikeDataForTest(
            indoorBikePayloadWithCadence(85.0),
        )
        parseDispatcher.runAll()
        manualHandler.advanceBy(1_000L)

        assertTrue(harness.uiState.runner.value.done)
        assertEquals(90, harness.uiState.lastTargetPower.value)
        assertTrue(
            requireNotNull(harness.uiState.workoutExecutionModeMessage.value).contains(
                "signal loss fallback",
                ignoreCase = true,
            ),
        )
    }

    @Test
    fun importedErgoWorkoutStopsWhenTrainerControlIsLostWithExplicitMessage() {
        val manualHandler = ManualHandler()
        val parseDispatcher = ManualIndoorBikeParsingDispatcher()
        val harness = createHarness(
            mainHandler = manualHandler,
            allowLegacyWorkoutFallback = false,
            indoorBikeParsingDispatcher = parseDispatcher,
        )
        harness.uiState.selectedImportedWorkout.value = heartRateImportedWorkout()

        harness.orchestrator.initialize()
        harness.orchestrator.onFtpWattsChanged()
        harness.orchestrator.beginConnectFlowForTest()
        manualHandler.advanceBy(0L)
        harness.orchestrator.simulateRequestControlGrantedForTest()
        harness.orchestrator.simulateControlOwnershipChangedForTest(controlGranted = true)
        manualHandler.advanceBy(0L)
        harness.orchestrator.simulateIndoorBikeDataForTest(
            indoorBikePayloadWithCadenceAndHeartRate(
                cadenceRpm = 85.0,
                heartRateBpm = 145,
            ),
        )
        parseDispatcher.runAll()
        manualHandler.advanceBy(1_000L)

        assertTrue(harness.uiState.runner.value.running)
        assertEquals(180, harness.uiState.lastTargetPower.value)

        harness.orchestrator.simulateControlOwnershipChangedForTest(controlGranted = false)
        manualHandler.advanceBy(1_000L)

        assertTrue(harness.uiState.runner.value.done)
        assertEquals(180, harness.uiState.lastTargetPower.value)
        assertTrue(
            requireNotNull(harness.uiState.workoutExecutionModeMessage.value).contains(
                "trainer control was lost",
                ignoreCase = true,
            ),
        )
        assertTrue(harness.uiState.workoutExecutionModeIsError.value)
        assertEquals(
            "TRAINER_CONTROL_LOST",
            harness.diagnosticsEvents.last { diagnostic ->
                diagnostic.category == "imported_hr_runtime" && diagnostic.event == "transition"
            }.context["event"],
        )
        assertEquals(
            "false",
            harness.diagnosticsEvents.last { diagnostic ->
                diagnostic.category == "imported_hr_runtime" && diagnostic.event == "transition"
            }.context["trainerControl"],
        )
    }

    @Test
    fun importedErgoWorkoutSurfacesBelowTargetAtMaximumPowerStatus() {
        val manualHandler = ManualHandler()
        val parseDispatcher = ManualIndoorBikeParsingDispatcher()
        val harness = createHarness(
            mainHandler = manualHandler,
            allowLegacyWorkoutFallback = false,
            indoorBikeParsingDispatcher = parseDispatcher,
        )
        harness.uiState.selectedImportedWorkout.value = heartRateImportedWorkout(
            initialPowerWatts = 255,
            maxPowerWatts = 260,
        )
        harness.uiState.heartRate.value = 145

        harness.orchestrator.initialize()
        harness.orchestrator.onFtpWattsChanged()
        harness.orchestrator.beginConnectFlowForTest()
        manualHandler.advanceBy(0L)
        harness.orchestrator.simulateRequestControlGrantedForTest()
        harness.orchestrator.simulateControlOwnershipChangedForTest(controlGranted = true)
        manualHandler.advanceBy(0L)
        harness.orchestrator.simulateIndoorBikeDataForTest(
            indoorBikePayloadWithCadence(85.0),
        )
        parseDispatcher.runAll()
        manualHandler.advanceBy(1_000L)

        assertEquals(255, harness.uiState.lastTargetPower.value)

        harness.uiState.heartRate.value = 130
        harness.orchestrator.onExternalHeartRateTelemetryUpdated()
        assertEquals(260, harness.uiState.lastTargetPower.value)
        assertNull(harness.uiState.workoutExecutionModeMessage.value)

        manualHandler.advanceBy(16_000L)
        harness.orchestrator.onExternalHeartRateTelemetryUpdated()

        assertEquals(260, harness.uiState.lastTargetPower.value)
        assertEquals(
            "Heart-rate-controlled workout is below target at maximum power.",
            harness.uiState.workoutExecutionModeMessage.value,
        )
        assertFalse(harness.uiState.workoutExecutionModeIsError.value)
        assertEquals(
            "TARGET_UNREACHABLE_HIGH",
            harness.diagnosticsEvents.last { diagnostic ->
                diagnostic.category == "imported_hr_runtime" && diagnostic.event == "transition"
            }.context["event"],
        )
    }

    @Test
    fun importedErgoWorkoutSurfacesAboveTargetAtMinimumPowerStatus() {
        val manualHandler = ManualHandler()
        val parseDispatcher = ManualIndoorBikeParsingDispatcher()
        val harness = createHarness(
            mainHandler = manualHandler,
            allowLegacyWorkoutFallback = false,
            indoorBikeParsingDispatcher = parseDispatcher,
        )
        harness.uiState.selectedImportedWorkout.value = heartRateImportedWorkout(
            initialPowerWatts = 125,
            minPowerWatts = 120,
        )
        harness.uiState.heartRate.value = 145

        harness.orchestrator.initialize()
        harness.orchestrator.onFtpWattsChanged()
        harness.orchestrator.beginConnectFlowForTest()
        manualHandler.advanceBy(0L)
        harness.orchestrator.simulateRequestControlGrantedForTest()
        harness.orchestrator.simulateControlOwnershipChangedForTest(controlGranted = true)
        manualHandler.advanceBy(0L)
        harness.orchestrator.simulateIndoorBikeDataForTest(
            indoorBikePayloadWithCadence(85.0),
        )
        parseDispatcher.runAll()
        manualHandler.advanceBy(1_000L)

        assertEquals(125, harness.uiState.lastTargetPower.value)

        harness.uiState.heartRate.value = 155
        harness.orchestrator.onExternalHeartRateTelemetryUpdated()
        assertEquals(120, harness.uiState.lastTargetPower.value)
        assertNull(harness.uiState.workoutExecutionModeMessage.value)

        manualHandler.advanceBy(2_000L)
        harness.orchestrator.onExternalHeartRateTelemetryUpdated()

        assertEquals(120, harness.uiState.lastTargetPower.value)
        assertEquals(
            "Heart-rate-controlled workout is above target at minimum power.",
            harness.uiState.workoutExecutionModeMessage.value,
        )
        assertFalse(harness.uiState.workoutExecutionModeIsError.value)
        assertEquals(
            "TARGET_UNREACHABLE_LOW",
            harness.diagnosticsEvents.last { diagnostic ->
                diagnostic.category == "imported_hr_runtime" && diagnostic.event == "transition"
            }.context["event"],
        )
    }

    @Test
    fun importedErgoWorkoutDoesNotDoubleDecreaseFromIndoorBikeWhenExternalHrIsActive() {
        val manualHandler = ManualHandler()
        val parseDispatcher = ManualIndoorBikeParsingDispatcher()
        val harness = createHarness(
            mainHandler = manualHandler,
            allowLegacyWorkoutFallback = false,
            indoorBikeParsingDispatcher = parseDispatcher,
        )
        harness.uiState.selectedImportedWorkout.value = heartRateImportedWorkout()
        harness.uiState.heartRate.value = 145

        harness.orchestrator.initialize()
        harness.orchestrator.onFtpWattsChanged()
        harness.orchestrator.beginConnectFlowForTest()
        manualHandler.advanceBy(0L)
        harness.orchestrator.simulateRequestControlGrantedForTest()
        harness.orchestrator.simulateControlOwnershipChangedForTest(controlGranted = true)
        manualHandler.advanceBy(0L)
        harness.orchestrator.simulateIndoorBikeDataForTest(
            indoorBikePayloadWithCadence(85.0),
        )
        parseDispatcher.runAll()
        manualHandler.advanceBy(1_000L)

        assertEquals(180, harness.uiState.lastTargetPower.value)

        harness.uiState.heartRate.value = 155
        harness.orchestrator.onExternalHeartRateTelemetryUpdated()

        assertEquals(170, harness.uiState.lastTargetPower.value)

        harness.orchestrator.simulateIndoorBikeDataForTest(
            indoorBikePayloadWithCadence(86.0),
        )
        parseDispatcher.runAll()
        manualHandler.advanceBy(0L)

        assertEquals(170, harness.uiState.lastTargetPower.value)
        assertEquals(
            1,
            harness.diagnosticsEvents.count { diagnostic ->
                diagnostic.category == "imported_hr_runtime" &&
                    diagnostic.event == "transition" &&
                    diagnostic.context["event"] == "POWER_DECREASED"
            },
        )
    }

    @Test
    fun importedErgoWorkoutFallsBackWhenExternalHeartRateDisconnectsWithoutFreshBikePacket() {
        val manualHandler = ManualHandler()
        val parseDispatcher = ManualIndoorBikeParsingDispatcher()
        val harness = createHarness(
            mainHandler = manualHandler,
            allowLegacyWorkoutFallback = false,
            indoorBikeParsingDispatcher = parseDispatcher,
        )
        harness.uiState.selectedImportedWorkout.value = heartRateImportedWorkout()
        harness.uiState.heartRate.value = 145

        harness.orchestrator.initialize()
        harness.orchestrator.onFtpWattsChanged()
        harness.orchestrator.beginConnectFlowForTest()
        manualHandler.advanceBy(0L)
        harness.orchestrator.simulateRequestControlGrantedForTest()
        harness.orchestrator.simulateControlOwnershipChangedForTest(controlGranted = true)
        manualHandler.advanceBy(0L)
        harness.orchestrator.simulateIndoorBikeDataForTest(
            indoorBikePayloadWithCadence(85.0),
        )
        parseDispatcher.runAll()
        manualHandler.advanceBy(1_000L)

        assertTrue(harness.uiState.runner.value.running)
        assertEquals(180, harness.uiState.lastTargetPower.value)

        harness.uiState.heartRate.value = null
        harness.orchestrator.onExternalHeartRateTelemetryUpdated()
        manualHandler.advanceBy(1_000L)

        assertTrue(harness.uiState.runner.value.running)
        assertEquals(90, harness.uiState.lastTargetPower.value)
        assertEquals(
            "none",
            harness.diagnosticsEvents.last { diagnostic ->
                diagnostic.category == "imported_hr_runtime" && diagnostic.event == "telemetry_probe"
            }.context["resolvedHeartRateSource"],
        )
        assertEquals(
            "external_heart_rate",
            harness.diagnosticsEvents.last { diagnostic ->
                diagnostic.category == "imported_hr_runtime" && diagnostic.event == "transition"
            }.context["source"],
        )

        harness.orchestrator.onExternalHeartRateTelemetryUpdated()
        manualHandler.advanceBy(1_000L)

        assertTrue(harness.uiState.runner.value.done)
        assertEquals(90, harness.uiState.lastTargetPower.value)
        assertTrue(
            requireNotNull(harness.uiState.workoutExecutionModeMessage.value).contains(
                "signal loss fallback",
                ignoreCase = true,
            ),
        )
    }

    @Test
    fun importedErgoWorkoutDoesNotStartHrRuntimeDuringLeadingPowerRamp() {
        val manualHandler = ManualHandler()
        val parseDispatcher = ManualIndoorBikeParsingDispatcher()
        val harness = createHarness(
            mainHandler = manualHandler,
            allowLegacyWorkoutFallback = false,
            indoorBikeParsingDispatcher = parseDispatcher,
        )
        harness.uiState.selectedImportedWorkout.value = powerRampThenHeartRateImportedWorkout()
        harness.uiState.heartRate.value = 145

        harness.orchestrator.initialize()
        harness.orchestrator.onFtpWattsChanged()
        harness.orchestrator.beginConnectFlowForTest()
        manualHandler.advanceBy(0L)
        harness.orchestrator.simulateRequestControlGrantedForTest()
        harness.orchestrator.simulateControlOwnershipChangedForTest(controlGranted = true)
        manualHandler.advanceBy(0L)
        harness.orchestrator.simulateIndoorBikeDataForTest(
            indoorBikePayloadWithCadence(85.0),
        )
        parseDispatcher.runAll()
        manualHandler.advanceBy(1_000L)

        assertTrue(harness.uiState.runner.value.running)
        assertNotNull(harness.uiState.lastTargetPower.value)
        assertTrue(harness.uiState.lastTargetPower.value!! in 200..210)
        assertEquals(RunnerSegmentType.RAMP, harness.uiState.runner.value.segmentType)

        harness.uiState.heartRate.value = 130
        harness.orchestrator.onExternalHeartRateTelemetryUpdated()

        assertTrue(harness.uiState.lastTargetPower.value!! in 200..210)
        assertEquals(
            0,
            countDiagnosticsEvent(harness.diagnosticsEvents, "imported_hr_runtime", "transition"),
        )
    }

    @Test
    fun importedErgoWorkoutStopsAfterPersistentSafetyCapBreachWithExplicitMessage() {
        val manualHandler = ManualHandler()
        val parseDispatcher = ManualIndoorBikeParsingDispatcher()
        val harness = createHarness(
            mainHandler = manualHandler,
            allowLegacyWorkoutFallback = false,
            indoorBikeParsingDispatcher = parseDispatcher,
        )
        harness.uiState.selectedImportedWorkout.value = heartRateImportedWorkout()

        harness.orchestrator.initialize()
        harness.orchestrator.onFtpWattsChanged()
        harness.orchestrator.beginConnectFlowForTest()
        manualHandler.advanceBy(0L)
        harness.orchestrator.simulateRequestControlGrantedForTest()
        harness.orchestrator.simulateControlOwnershipChangedForTest(controlGranted = true)
        manualHandler.advanceBy(0L)
        harness.orchestrator.simulateIndoorBikeDataForTest(
            indoorBikePayloadWithCadenceAndHeartRate(
                cadenceRpm = 85.0,
                heartRateBpm = 145,
            ),
        )
        parseDispatcher.runAll()
        manualHandler.advanceBy(1_000L)

        assertTrue(harness.uiState.runner.value.running)
        assertEquals(180, harness.uiState.lastTargetPower.value)

        harness.orchestrator.simulateIndoorBikeDataForTest(
            indoorBikePayloadWithCadenceAndHeartRate(
                cadenceRpm = 85.0,
                heartRateBpm = 186,
            ),
        )
        parseDispatcher.runAll()
        manualHandler.advanceBy(1_000L)

        assertTrue(harness.uiState.runner.value.running)
        assertEquals(72, harness.uiState.lastTargetPower.value)
        assertEquals(
            "HR_CAP_BREACHED",
            harness.diagnosticsEvents.last { diagnostic ->
                diagnostic.category == "imported_hr_runtime" && diagnostic.event == "transition"
            }.context["event"],
        )

        harness.orchestrator.simulateIndoorBikeDataForTest(
            indoorBikePayloadWithCadenceAndHeartRate(
                cadenceRpm = 85.0,
                heartRateBpm = 186,
            ),
        )
        parseDispatcher.runAll()
        manualHandler.advanceBy(1_000L)

        assertTrue(harness.uiState.runner.value.done)
        assertEquals(72, harness.uiState.lastTargetPower.value)
        assertTrue(
            requireNotNull(harness.uiState.workoutExecutionModeMessage.value).contains(
                "safety cap stayed exceeded",
                ignoreCase = true,
            ),
        )
        assertTrue(harness.uiState.workoutExecutionModeIsError.value)
        assertEquals(
            "HR_CAP_BREACH_PERSISTED",
            harness.diagnosticsEvents.last { diagnostic ->
                diagnostic.category == "imported_hr_runtime" && diagnostic.event == "transition"
            }.context["event"],
        )
    }

    @Test
    fun mockModeAllowsStartWithoutTrainerMacAndTransitionsConnectingToSession() {
        val manualHandler = ManualHandler()
        val harness = createHarness(
            mainHandler = manualHandler,
            currentFtmsDeviceMac = { null },
            isMockTrainerModeEnabled = { true },
        )
        harness.orchestrator.initialize()
        harness.uiState.selectedWorkout.value = readyWorkout()
        harness.uiState.workoutReady.value = true

        harness.orchestrator.startSessionConnection()
        assertEquals(AppScreen.CONNECTING, harness.uiState.screen.value)

        manualHandler.advanceBy(0L)
        assertEquals(AppScreen.SESSION, harness.uiState.screen.value)
        assertTrue(harness.uiState.ftmsReady.value)
        assertTrue(harness.uiState.ftmsControlGranted.value)
        assertEquals(SessionPhase.RUNNING, harness.sessionManager.getPhase())
        assertEquals(1, harness.currentKeepScreenOnCalls)
    }

    @Test
    fun mockModeAllowsStartForImportedPowerOnlyErgoWorkout() {
        val manualHandler = ManualHandler()
        val harness = createHarness(
            mainHandler = manualHandler,
            currentFtmsDeviceMac = { null },
            isMockTrainerModeEnabled = { true },
        )
        harness.orchestrator.initialize()
        harness.uiState.selectedImportedWorkout.value = powerOnlyImportedWorkout()

        harness.orchestrator.onFtpWattsChanged()
        assertTrue(harness.uiState.workoutReady.value)

        harness.orchestrator.startSessionConnection()
        assertEquals(AppScreen.CONNECTING, harness.uiState.screen.value)

        manualHandler.advanceBy(0L)
        assertEquals(AppScreen.SESSION, harness.uiState.screen.value)
        assertEquals(SessionPhase.RUNNING, harness.sessionManager.getPhase())
    }

    @Test
    fun mockModeImportedHrWorkoutStartsFromExternalHeartRateAndStopsAfterSignalLoss() {
        val manualHandler = ManualHandler()
        val harness = createHarness(
            mainHandler = manualHandler,
            currentFtmsDeviceMac = { null },
            isMockTrainerModeEnabled = { true },
        )
        harness.orchestrator.initialize()
        harness.uiState.selectedImportedWorkout.value = heartRateImportedWorkout()
        harness.uiState.heartRate.value = 145

        harness.orchestrator.onFtpWattsChanged()
        assertTrue(harness.uiState.workoutReady.value)

        harness.orchestrator.startSessionConnection()
        manualHandler.advanceBy(0L)

        assertEquals(AppScreen.SESSION, harness.uiState.screen.value)
        assertTrue(harness.uiState.runner.value.running)
        assertEquals(180, harness.uiState.lastTargetPower.value)

        harness.uiState.heartRate.value = null
        harness.orchestrator.onExternalHeartRateTelemetryUpdated()
        assertEquals(90, harness.uiState.lastTargetPower.value)

        manualHandler.advanceBy(1_000L)

        assertTrue(harness.uiState.runner.value.done)
        assertTrue(
            requireNotNull(harness.uiState.workoutExecutionModeMessage.value).contains(
                "signal loss fallback",
                ignoreCase = true,
            ),
        )
    }

    @Test
    fun externalHeartRateTelemetryProbePrefersExternalHeartRateWhenTrainerHrAlsoExists() {
        val manualHandler = ManualHandler()
        val parseDispatcher = ManualIndoorBikeParsingDispatcher()
        val harness = createHarness(
            mainHandler = manualHandler,
            allowLegacyWorkoutFallback = false,
            indoorBikeParsingDispatcher = parseDispatcher,
        )
        harness.uiState.selectedImportedWorkout.value = heartRateImportedWorkout()
        harness.uiState.heartRate.value = 145

        harness.orchestrator.initialize()
        harness.orchestrator.onFtpWattsChanged()
        harness.orchestrator.beginConnectFlowForTest()
        manualHandler.advanceBy(0L)
        harness.orchestrator.simulateRequestControlGrantedForTest()
        harness.orchestrator.simulateControlOwnershipChangedForTest(controlGranted = true)
        manualHandler.advanceBy(0L)
        harness.orchestrator.simulateIndoorBikeDataForTest(
            indoorBikePayloadWithCadenceAndHeartRate(
                cadenceRpm = 85.0,
                heartRateBpm = 160,
            ),
        )
        parseDispatcher.runAll()
        manualHandler.advanceBy(1_000L)

        harness.orchestrator.onExternalHeartRateTelemetryUpdated()

        val telemetryProbe = harness.diagnosticsEvents.last { diagnostic ->
            diagnostic.category == "imported_hr_runtime" && diagnostic.event == "telemetry_probe"
        }

        assertEquals("external_heart_rate", telemetryProbe.context["source"])
        assertEquals("external_heart_rate", telemetryProbe.context["resolvedHeartRateSource"])
        assertEquals("145", telemetryProbe.context["heartRateBpm"])
        assertEquals("160", telemetryProbe.context["ftmsHeartRateBpm"])
        assertEquals("145", telemetryProbe.context["externalHeartRateBpm"])
        assertEquals(180, harness.uiState.lastTargetPower.value)
        assertTrue(harness.uiState.runner.value.running)
    }

    @Test
    fun zeroHeartRateTelemetryProbeStillExposesIntendedSignalLossReaction() {
        val manualHandler = ManualHandler()
        val parseDispatcher = ManualIndoorBikeParsingDispatcher()
        val harness = createHarness(
            mainHandler = manualHandler,
            allowLegacyWorkoutFallback = false,
            indoorBikeParsingDispatcher = parseDispatcher,
        )
        harness.uiState.selectedImportedWorkout.value = heartRateImportedWorkout()

        harness.orchestrator.initialize()
        harness.orchestrator.onFtpWattsChanged()
        harness.orchestrator.beginConnectFlowForTest()
        manualHandler.advanceBy(0L)
        harness.orchestrator.simulateRequestControlGrantedForTest()
        harness.orchestrator.simulateControlOwnershipChangedForTest(controlGranted = true)
        manualHandler.advanceBy(0L)
        harness.orchestrator.simulateIndoorBikeDataForTest(
            indoorBikePayloadWithCadenceAndHeartRate(
                cadenceRpm = 85.0,
                heartRateBpm = 0,
            ),
        )
        parseDispatcher.runAll()
        manualHandler.advanceBy(1_000L)

        harness.orchestrator.onExternalHeartRateTelemetryUpdated()

        val telemetryProbe = harness.diagnosticsEvents.last { diagnostic ->
            diagnostic.category == "imported_hr_runtime" && diagnostic.event == "telemetry_probe"
        }

        assertEquals("ftms_indoor_bike", telemetryProbe.context["resolvedHeartRateSource"])
        assertEquals("0", telemetryProbe.context["heartRateBpm"])
        assertEquals("true", telemetryProbe.context["heartRateZeroObserved"])
        assertEquals("HR_SIGNAL_LOST", telemetryProbe.context["signalLossIntendedEvent"])
        assertEquals("Fallback", telemetryProbe.context["signalLossIntendedTargetState"])
        assertEquals(
            "SetPower(90),BlockIncrease",
            telemetryProbe.context["signalLossIntendedCommands"],
        )
        assertEquals(180, harness.uiState.lastTargetPower.value)
        assertEquals(1, countDiagnosticsEvent(harness.diagnosticsEvents, "imported_hr_runtime", "startup_grace_started"))
        assertTrue(harness.uiState.runner.value.running)
    }

    @Test
    fun externalHeartRateZeroTriggersFallbackWhenExternalSourceIsPreferred() {
        val manualHandler = ManualHandler()
        val parseDispatcher = ManualIndoorBikeParsingDispatcher()
        val harness = createHarness(
            mainHandler = manualHandler,
            allowLegacyWorkoutFallback = false,
            indoorBikeParsingDispatcher = parseDispatcher,
        )
        harness.uiState.selectedImportedWorkout.value = heartRateImportedWorkout()
        harness.uiState.heartRate.value = 145

        harness.orchestrator.initialize()
        harness.orchestrator.onFtpWattsChanged()
        harness.orchestrator.beginConnectFlowForTest()
        manualHandler.advanceBy(0L)
        harness.orchestrator.simulateRequestControlGrantedForTest()
        harness.orchestrator.simulateControlOwnershipChangedForTest(controlGranted = true)
        manualHandler.advanceBy(0L)
        harness.orchestrator.simulateIndoorBikeDataForTest(
            indoorBikePayloadWithCadenceAndHeartRate(
                cadenceRpm = 85.0,
                heartRateBpm = 90,
            ),
        )
        parseDispatcher.runAll()
        manualHandler.advanceBy(1_000L)

        harness.uiState.heartRate.value = 0
        harness.orchestrator.onExternalHeartRateTelemetryUpdated()
        manualHandler.advanceBy(1_000L)

        assertEquals(90, harness.uiState.lastTargetPower.value)
        assertEquals(
            "external_heart_rate",
            harness.diagnosticsEvents.last { diagnostic ->
                diagnostic.category == "imported_hr_runtime" && diagnostic.event == "transition"
            }.context["resolvedHeartRateSource"],
        )
        assertEquals(
            "0",
            harness.diagnosticsEvents.last { diagnostic ->
                diagnostic.category == "imported_hr_runtime" && diagnostic.event == "transition"
            }.context["heartRateBpm"],
        )
    }

    @Test
    fun trainerIntegratedZeroHeartRateAtStartWaitsThroughStartupGraceBeforeFallback() {
        val manualHandler = ManualHandler()
        val parseDispatcher = ManualIndoorBikeParsingDispatcher()
        val harness = createHarness(
            mainHandler = manualHandler,
            allowLegacyWorkoutFallback = false,
            indoorBikeParsingDispatcher = parseDispatcher,
        )
        harness.uiState.selectedImportedWorkout.value = heartRateImportedWorkout()

        harness.orchestrator.initialize()
        harness.orchestrator.onFtpWattsChanged()
        harness.orchestrator.beginConnectFlowForTest()
        manualHandler.advanceBy(0L)
        harness.orchestrator.simulateRequestControlGrantedForTest()
        harness.orchestrator.simulateControlOwnershipChangedForTest(controlGranted = true)
        manualHandler.advanceBy(0L)
        harness.orchestrator.simulateIndoorBikeDataForTest(
            indoorBikePayloadWithCadenceAndHeartRate(
                cadenceRpm = 85.0,
                heartRateBpm = 0,
            ),
        )
        parseDispatcher.runAll()
        manualHandler.advanceBy(1_000L)

        assertEquals(180, harness.uiState.lastTargetPower.value)
        assertEquals(1, countDiagnosticsEvent(harness.diagnosticsEvents, "imported_hr_runtime", "startup_grace_started"))

        harness.orchestrator.onExternalHeartRateTelemetryUpdated()
        manualHandler.advanceBy(1_000L)

        assertEquals(180, harness.uiState.lastTargetPower.value)
        assertEquals(
            1,
            harness.diagnosticsEvents.count { diagnostic ->
                diagnostic.category == "imported_hr_runtime" &&
                    diagnostic.event == "transition" &&
                    diagnostic.context["event"] == "STARTED"
            },
        )

        manualHandler.advanceBy(4_000L)

        assertEquals(90, harness.uiState.lastTargetPower.value)
        assertEquals(1, countDiagnosticsEvent(harness.diagnosticsEvents, "imported_hr_runtime", "startup_grace_expired"))
        assertEquals(
            "HR_SIGNAL_LOST",
            harness.diagnosticsEvents.last { diagnostic ->
                diagnostic.category == "imported_hr_runtime" && diagnostic.event == "transition"
            }.context["event"],
        )
    }

    @Test
    fun trainerIntegratedHeartRateAcquisitionCancelsStartupGrace() {
        val manualHandler = ManualHandler()
        val parseDispatcher = ManualIndoorBikeParsingDispatcher()
        val harness = createHarness(
            mainHandler = manualHandler,
            allowLegacyWorkoutFallback = false,
            indoorBikeParsingDispatcher = parseDispatcher,
        )
        harness.uiState.selectedImportedWorkout.value = heartRateImportedWorkout()

        harness.orchestrator.initialize()
        harness.orchestrator.onFtpWattsChanged()
        harness.orchestrator.beginConnectFlowForTest()
        manualHandler.advanceBy(0L)
        harness.orchestrator.simulateRequestControlGrantedForTest()
        harness.orchestrator.simulateControlOwnershipChangedForTest(controlGranted = true)
        manualHandler.advanceBy(0L)
        harness.orchestrator.simulateIndoorBikeDataForTest(
            indoorBikePayloadWithCadenceAndHeartRate(
                cadenceRpm = 85.0,
                heartRateBpm = 0,
            ),
        )
        parseDispatcher.runAll()
        manualHandler.advanceBy(1_000L)

        harness.orchestrator.simulateIndoorBikeDataForTest(
            indoorBikePayloadWithCadenceAndHeartRate(
                cadenceRpm = 85.0,
                heartRateBpm = 92,
            ),
        )
        parseDispatcher.runAll()
        manualHandler.advanceBy(1_000L)

        assertEquals(1, countDiagnosticsEvent(harness.diagnosticsEvents, "imported_hr_runtime", "startup_hr_acquired"))
        assertEquals(185, harness.uiState.lastTargetPower.value)

        manualHandler.advanceBy(5_000L)

        assertEquals(0, countDiagnosticsEvent(harness.diagnosticsEvents, "imported_hr_runtime", "startup_grace_expired"))
        assertEquals(185, harness.uiState.lastTargetPower.value)
    }

    @Test
    fun mockModeDebugScenarioKeepsWaitingStartThenAutoPausesAndResumes() {
        val manualHandler = ManualHandler()
        val harness = createHarness(
            mainHandler = manualHandler,
            currentFtmsDeviceMac = { null },
            isMockTrainerModeEnabled = { true },
            consumeMockTrainerDebugScenario = {
                MockTrainerDebugScenario.WAITING_START_AND_PAUSE_CAPTURE
            },
        )
        harness.orchestrator.initialize()
        harness.uiState.selectedImportedWorkout.value = powerOnlyImportedWorkout()

        harness.orchestrator.onFtpWattsChanged()
        assertTrue(harness.uiState.workoutReady.value)

        harness.orchestrator.startSessionConnection()
        manualHandler.advanceBy(0L)

        assertEquals(AppScreen.SESSION, harness.uiState.screen.value)
        assertTrue(harness.uiState.pendingCadenceStartAfterControlGranted)
        assertEquals(
            0.0,
            requireNotNull(harness.uiState.bikeData.value?.instantaneousCadenceRpm),
            0.0,
        )
        assertFalse(harness.uiState.runner.value.running)

        manualHandler.advanceBy(8_000L)
        assertFalse(harness.uiState.pendingCadenceStartAfterControlGranted)
        assertTrue(harness.uiState.runner.value.running)
        assertFalse(harness.uiState.runner.value.paused)
        assertFalse(harness.uiState.autoPausedByZeroCadence)

        manualHandler.advanceBy(8_000L)
        assertTrue(harness.uiState.runner.value.running)
        assertTrue(harness.uiState.runner.value.paused)
        assertTrue(harness.uiState.autoPausedByZeroCadence)
        assertEquals(
            0.0,
            requireNotNull(harness.uiState.bikeData.value?.instantaneousCadenceRpm),
            0.0,
        )

        manualHandler.advanceBy(5_000L)
        assertTrue(harness.uiState.runner.value.running)
        assertFalse(harness.uiState.runner.value.paused)
        assertFalse(harness.uiState.autoPausedByZeroCadence)
    }

    @Test
    fun mockModeStartIgnoresStaleDisconnectFromPreviousFtmsGeneration() {
        val manualHandler = ManualHandler()
        val harness = createHarness(
            mainHandler = manualHandler,
            currentFtmsDeviceMac = { null },
            isMockTrainerModeEnabled = { true },
        )
        harness.orchestrator.initialize()
        val staleGeneration = harness.orchestrator.activeFtmsGenerationForTest()
        harness.uiState.selectedWorkout.value = readyWorkout()
        harness.uiState.workoutReady.value = true

        harness.orchestrator.startSessionConnection()
        val activeGeneration = harness.orchestrator.activeFtmsGenerationForTest()
        assertTrue(activeGeneration > staleGeneration)
        assertEquals(AppScreen.CONNECTING, harness.uiState.screen.value)

        harness.orchestrator.simulateFtmsDisconnectedForTest(staleGeneration)
        assertEquals(AppScreen.CONNECTING, harness.uiState.screen.value)

        manualHandler.advanceBy(0L)
        assertEquals(AppScreen.SESSION, harness.uiState.screen.value)
        assertEquals(SessionPhase.RUNNING, harness.sessionManager.getPhase())
    }

    @Test
    fun mockModeStopFlowStopsTelemetryAndResetsFtmsFlags() {
        val manualHandler = ManualHandler()
        val harness = createHarness(
            mainHandler = manualHandler,
            currentFtmsDeviceMac = { null },
            isMockTrainerModeEnabled = { true },
        )
        harness.orchestrator.initialize()
        harness.uiState.selectedWorkout.value = readyWorkout()
        harness.uiState.workoutReady.value = true

        harness.orchestrator.startSessionConnection()
        manualHandler.advanceBy(0L)
        manualHandler.advanceBy(2_000L)
        val beforeStopElapsed = harness.uiState.bikeData.value?.elapsedTimeSeconds
        assertNotNull(beforeStopElapsed)

        harness.orchestrator.endSessionAndGoToSummary()
        assertEquals(AppScreen.SUMMARY, harness.uiState.screen.value)
        assertEquals(StopFlowState.IDLE, harness.uiState.stopFlowState.value)
        assertFalse(harness.uiState.ftmsReady.value)
        assertFalse(harness.uiState.ftmsControlGranted.value)

        val elapsedAfterStop = harness.uiState.bikeData.value?.elapsedTimeSeconds
        manualHandler.advanceBy(5_000L)
        assertEquals(elapsedAfterStop, harness.uiState.bikeData.value?.elapsedTimeSeconds)
    }

    @Test
    fun mockModeStopAndCloseStopsTelemetryCallbacks() {
        val manualHandler = ManualHandler()
        val harness = createHarness(
            mainHandler = manualHandler,
            currentFtmsDeviceMac = { null },
            isMockTrainerModeEnabled = { true },
        )
        harness.orchestrator.initialize()
        harness.uiState.selectedWorkout.value = readyWorkout()
        harness.uiState.workoutReady.value = true
        harness.orchestrator.startSessionConnection()
        manualHandler.advanceBy(0L)
        manualHandler.advanceBy(1_000L)
        val elapsedBeforeClose = harness.uiState.bikeData.value?.elapsedTimeSeconds

        harness.orchestrator.stopAndClose()
        manualHandler.advanceBy(3_000L)

        assertEquals(elapsedBeforeClose, harness.uiState.bikeData.value?.elapsedTimeSeconds)
        assertEquals(1, harness.currentAllowScreenOffCalls)
    }

    @Test
    fun sessionDiagnosticsScopeStartsOnStartRequestAndEndsAfterStopCompletion() {
        val manualHandler = ManualHandler()
        val harness = createHarness(
            mainHandler = manualHandler,
            currentFtmsDeviceMac = { null },
            isMockTrainerModeEnabled = { true },
            sessionIdFactory = { "session-test-1" },
        )
        harness.orchestrator.initialize()
        harness.uiState.selectedWorkout.value = readyWorkout()
        harness.uiState.workoutReady.value = true

        harness.orchestrator.startSessionConnection()
        assertEquals("session-test-1", harness.orchestrator.activeSessionIdForTest())

        manualHandler.advanceBy(0L)
        harness.orchestrator.endSessionAndGoToSummary()

        assertNull(harness.orchestrator.activeSessionIdForTest())
        val scopeStart = harness.diagnosticsEvents.single { it.event == "scope_started" }
        val scopeEnd = harness.diagnosticsEvents.single { it.event == "scope_ended" }
        assertEquals("session-test-1", scopeStart.sessionId)
        assertEquals("session-test-1", scopeEnd.sessionId)
    }

    @Test
    fun indoorBikeParsingLatestRequestWinsWhenBackgroundCompletionOrderFlips() {
        val manualHandler = ManualHandler()
        val parseDispatcher = ManualIndoorBikeParsingDispatcher()
        val harness = createHarness(
            mainHandler = manualHandler,
            indoorBikeParsingDispatcher = parseDispatcher,
        )
        harness.orchestrator.initialize()
        val generation = harness.orchestrator.activeFtmsGenerationForTest()

        harness.orchestrator.scheduleIndoorBikeParsingForTest(
            payload = indoorBikePayloadWithCadence(cadenceRpm = 60.0),
            generation = generation,
        )
        harness.orchestrator.scheduleIndoorBikeParsingForTest(
            payload = indoorBikePayloadWithCadence(cadenceRpm = 90.0),
            generation = generation,
        )

        parseDispatcher.runLast()
        manualHandler.advanceBy(0L)
        assertEquals(
            90.0,
            requireNotNull(harness.uiState.bikeData.value?.instantaneousCadenceRpm),
            0.0,
        )

        parseDispatcher.runFirst()
        manualHandler.advanceBy(0L)
        assertEquals(
            90.0,
            requireNotNull(harness.uiState.bikeData.value?.instantaneousCadenceRpm),
            0.0,
        )
    }

    @Test
    fun indoorBikeParsingFromStaleGenerationIsIgnored() {
        val manualHandler = ManualHandler()
        val parseDispatcher = ManualIndoorBikeParsingDispatcher()
        val harness = createHarness(
            mainHandler = manualHandler,
            indoorBikeParsingDispatcher = parseDispatcher,
        )
        harness.orchestrator.initialize()
        val staleGeneration = harness.orchestrator.activeFtmsGenerationForTest() - 1

        harness.orchestrator.scheduleIndoorBikeParsingForTest(
            payload = indoorBikePayloadWithCadence(cadenceRpm = 75.0),
            generation = staleGeneration,
        )

        parseDispatcher.runAll()
        manualHandler.advanceBy(0L)

        assertNull(harness.uiState.bikeData.value)
    }

    @Test
    fun indoorBikeParseFailureRecordsStructuredDiagnosticsForLatestRequest() {
        val manualHandler = ManualHandler()
        val parseDispatcher = ManualIndoorBikeParsingDispatcher()
        val harness = createHarness(
            mainHandler = manualHandler,
            indoorBikeParsingDispatcher = parseDispatcher,
            sessionIdFactory = { "session-parse-failure" },
        )
        harness.orchestrator.initialize()
        harness.orchestrator.beginConnectFlowForTest()
        val generation = harness.orchestrator.activeFtmsGenerationForTest()

        harness.orchestrator.scheduleIndoorBikeParsingForTest(
            payload = malformedIndoorBikePayload(),
            generation = generation,
        )

        parseDispatcher.runAll()
        manualHandler.advanceBy(0L)

        assertFalse(requireNotNull(harness.uiState.bikeData.value).valid)
        assertEquals(
            1,
            countDiagnosticsEvent(
                events = harness.diagnosticsEvents,
                category = "ftms_parser",
                event = "parse_failed",
            ),
        )
        assertEquals(
            "TRUNCATED_PAYLOAD",
            firstDiagnosticsEventContext(
                events = harness.diagnosticsEvents,
                category = "ftms_parser",
                event = "parse_failed",
                key = "reason",
            ),
        )
        assertEquals(
            "5",
            firstDiagnosticsEventContext(
                events = harness.diagnosticsEvents,
                category = "ftms_parser",
                event = "parse_failed",
                key = "payloadLength",
            ),
        )
        assertEquals(
            "0x04",
            firstDiagnosticsEventContext(
                events = harness.diagnosticsEvents,
                category = "ftms_parser",
                event = "parse_failed",
                key = "flags",
            ),
        )
    }

    // region BLE ready and control ownership tests

    @Test
    fun bleReadyDuringConnectingTriggersRequestControl() {
        val harness = createHarness()
        harness.orchestrator.initialize()
        harness.uiState.screen.value = AppScreen.CONNECTING

        val wouldRequestControl = harness.orchestrator.simulateBleReadyForTest(
            controlPointReady = true,
            linkGeneration = 1,
        )

        assertTrue(harness.uiState.ftmsReady.value)
        assertTrue("onReady during CONNECTING should trigger requestControl", wouldRequestControl)
    }

    @Test
    fun bleReadyDuringSessionWithoutControlTriggersRequestControl() {
        val harness = createHarness()
        harness.orchestrator.initialize()
        harness.uiState.screen.value = AppScreen.CONNECTING
        harness.orchestrator.simulateRequestControlGrantedForTest()
        assertEquals(AppScreen.SESSION, harness.uiState.screen.value)

        // Simulate losing control, then BLE ready arrives
        harness.uiState.ftmsControlGranted.value = false
        val wouldRequestControl = harness.orchestrator.simulateBleReadyForTest(
            controlPointReady = true,
            linkGeneration = 2,
        )

        assertTrue(harness.uiState.ftmsReady.value)
        assertTrue("onReady during SESSION without control should trigger requestControl", wouldRequestControl)
    }

    @Test
    fun bleReadyDuringMenuDoesNotTriggerRequestControl() {
        val harness = createHarness()
        harness.orchestrator.initialize()
        harness.uiState.screen.value = AppScreen.MENU

        val wouldRequestControl = harness.orchestrator.simulateBleReadyForTest(
            controlPointReady = true,
            linkGeneration = 1,
        )

        assertTrue(harness.uiState.ftmsReady.value)
        assertFalse("onReady during MENU should not trigger requestControl", wouldRequestControl)
    }

    @Test
    fun bleNotReadyDuringSessionClearsFtmsReadyState() {
        val harness = createHarness()
        harness.orchestrator.initialize()
        harness.uiState.screen.value = AppScreen.CONNECTING
        harness.orchestrator.simulateRequestControlGrantedForTest()
        assertEquals(AppScreen.SESSION, harness.uiState.screen.value)

        harness.orchestrator.simulateBleReadyForTest(controlPointReady = false, linkGeneration = 1)

        assertFalse(harness.uiState.ftmsReady.value)
    }

    @Test
    fun controlOwnershipLostDuringSessionClearsControlGranted() {
        val harness = createHarness()
        harness.orchestrator.initialize()
        harness.uiState.screen.value = AppScreen.CONNECTING
        harness.orchestrator.simulateRequestControlGrantedForTest()
        assertEquals(AppScreen.SESSION, harness.uiState.screen.value)
        // In production, BLE onControlOwnershipChanged sets this; in tests, simulate it.
        harness.orchestrator.simulateControlOwnershipChangedForTest(controlGranted = true)
        assertTrue(harness.uiState.ftmsControlGranted.value)

        harness.orchestrator.simulateControlOwnershipChangedForTest(controlGranted = false)

        assertFalse(harness.uiState.ftmsControlGranted.value)
    }

    @Test
    fun controlOwnershipRegainedDuringSessionRestoresControlGranted() {
        val harness = createHarness()
        harness.orchestrator.initialize()
        harness.uiState.screen.value = AppScreen.CONNECTING
        harness.orchestrator.simulateRequestControlGrantedForTest()
        harness.orchestrator.simulateControlOwnershipChangedForTest(controlGranted = true)

        harness.orchestrator.simulateControlOwnershipChangedForTest(controlGranted = false)
        assertFalse(harness.uiState.ftmsControlGranted.value)

        harness.orchestrator.simulateControlOwnershipChangedForTest(controlGranted = true)
        assertTrue(harness.uiState.ftmsControlGranted.value)
    }

    // endregion

    // region BLE disconnect during CONNECTING

    @Test
    fun bleDisconnectDuringConnectingRollsBackToMenu() {
        val harness = createHarness()
        harness.orchestrator.initialize()
        harness.uiState.screen.value = AppScreen.CONNECTING
        harness.uiState.ftmsReady.value = true
        val generation = harness.orchestrator.activeFtmsGenerationForTest()

        harness.orchestrator.simulateFtmsDisconnectedForTest(generation)

        assertEquals(AppScreen.MENU, harness.uiState.screen.value)
        assertFalse(harness.uiState.ftmsReady.value)
        assertFalse(harness.uiState.ftmsControlGranted.value)
        assertEquals(1, harness.currentAllowScreenOffCalls)
    }

    @Test
    fun staleDisconnectDuringConnectingIsIgnored() {
        val harness = createHarness()
        harness.orchestrator.initialize()
        harness.uiState.screen.value = AppScreen.CONNECTING
        harness.uiState.ftmsReady.value = true
        val staleGeneration = harness.orchestrator.activeFtmsGenerationForTest() - 1

        harness.orchestrator.simulateFtmsDisconnectedForTest(staleGeneration)

        assertEquals(AppScreen.CONNECTING, harness.uiState.screen.value)
        assertTrue(harness.uiState.ftmsReady.value)
    }

    // endregion

    // region Telemetry-only (3C) session lifecycle

    @Test
    fun telemetryOnlySession_selectsModeAndSetsWorkoutReadyWithoutWorkout() {
        val harness = createHarness(
            isMockTrainerModeEnabled = { true },
        )
        harness.orchestrator.initialize()

        harness.orchestrator.onTelemetryOnlyModeSelected()

        assertEquals(
            SessionSetupMode.TELEMETRY_ONLY,
            harness.uiState.selectedSessionSetupMode.value,
        )
        assertTrue(harness.uiState.workoutReady.value)
        assertNull(harness.uiState.selectedWorkout.value)
        assertNull(harness.uiState.selectedImportedWorkout.value)
    }

    @Test
    fun telemetryOnlySession_startsAndReachesSessionScreen() {
        val manualHandler = ManualHandler()
        val harness = createHarness(
            mainHandler = manualHandler,
            isMockTrainerModeEnabled = { true },
        )
        harness.orchestrator.initialize()
        harness.orchestrator.onTelemetryOnlyModeSelected()

        harness.orchestrator.beginConnectFlowForTest()
        manualHandler.advanceBy(0)
        assertEquals(AppScreen.CONNECTING, harness.uiState.screen.value)

        harness.orchestrator.simulateRequestControlGrantedForTest()
        manualHandler.advanceBy(0)
        assertEquals(AppScreen.SESSION, harness.uiState.screen.value)
    }

    @Test
    fun telemetryOnlySession_noFtmsTargetWritesDuringSession() {
        val manualHandler = ManualHandler()
        val harness = createHarness(
            mainHandler = manualHandler,
            isMockTrainerModeEnabled = { true },
        )
        harness.orchestrator.initialize()
        harness.orchestrator.onTelemetryOnlyModeSelected()

        harness.orchestrator.beginConnectFlowForTest()
        manualHandler.advanceBy(0)
        harness.orchestrator.simulateRequestControlGrantedForTest()
        manualHandler.advanceBy(0)

        harness.orchestrator.simulateIndoorBikeDataForTest(
            indoorBikePayloadWithCadence(85.0),
        )
        manualHandler.advanceBy(1_000)

        assertEquals(
            0,
            countDiagnosticsEvent(harness.diagnosticsEvents, "ftms", "target_power_write"),
        )
    }

    @Test
    fun telemetryOnlySession_ignoresStaleStructuredWorkoutSelectionDuringCadenceStart() {
        val manualHandler = ManualHandler()
        val harness = createHarness(
            mainHandler = manualHandler,
            isMockTrainerModeEnabled = { true },
        )
        harness.orchestrator.initialize()
        harness.orchestrator.onTelemetryOnlyModeSelected()
        harness.uiState.selectedWorkout.value = readyWorkout()
        harness.uiState.selectedWorkoutFileName.value = "stale_editor_workout.zwo"
        harness.uiState.selectedWorkoutStepCount.value = 1
        harness.uiState.selectedWorkoutPlannedTss.value = 12.3
        harness.uiState.selectedWorkoutTotalDurationSec.value = 180

        harness.orchestrator.startSessionConnection()
        manualHandler.advanceBy(0)
        assertNull(harness.uiState.selectedWorkout.value)
        assertNull(harness.uiState.selectedImportedWorkout.value)

        harness.orchestrator.simulateRequestControlGrantedForTest()
        manualHandler.advanceBy(0)
        harness.orchestrator.simulateIndoorBikeDataForTest(
            indoorBikePayloadWithCadence(85.0),
        )
        manualHandler.advanceBy(1_000)

        assertEquals(AppScreen.SESSION, harness.uiState.screen.value)
        assertFalse(harness.uiState.runner.value.running)
        assertEquals(
            0,
            countDiagnosticsEvent(harness.diagnosticsEvents, "ftms", "target_power_write"),
        )
    }

    @Test
    fun selectingWorkoutClearsCompletedRunnerProgressBeforeNextSessionStarts() {
        val harness = createHarness()
        harness.orchestrator.initialize()
        harness.uiState.runner.value =
            io.github.ewoc2026.ewoc.workout.runner.RunnerState.stopped(
                workoutElapsedSec = 180,
            )

        harness.orchestrator.onWorkoutEdited(
            workout = readyWorkout(),
            sourceName = "same-workout.ewo",
        )

        assertFalse(harness.uiState.runner.value.running)
        assertTrue(harness.uiState.runner.value.done)
        assertNull(harness.uiState.runner.value.workoutElapsedSec)
    }

    @Test
    fun telemetryOnlySession_waitsForCleanDisconnectEpochAfterAppControlledTargetHistory() {
        val manualHandler = ManualHandler()
        var connectFtmsTransportCalls = 0
        val harness = createHarness(
            mainHandler = manualHandler,
            connectFtmsTransport = { _, _ -> connectFtmsTransportCalls += 1 },
        )
        harness.orchestrator.initialize()

        assertTrue(harness.orchestrator.prepareTrainerWarmConnectionInMenu())
        harness.orchestrator.simulateBleReadyForTest(controlPointReady = true)
        val connectCallsAfterWarmPrepare = connectFtmsTransportCalls
        val activeGeneration = harness.orchestrator.activeFtmsGenerationForTest()

        harness.uiState.trainerControlAuthority.value = TrainerControlAuthority.APP_CONTROLLED
        harness.uiState.lastAppControlledTargetPower.value = 68
        harness.orchestrator.onTelemetryOnlyModeSelected()

        harness.orchestrator.startSessionConnection()
        manualHandler.advanceBy(0L)

        assertEquals(AppScreen.CONNECTING, harness.uiState.screen.value)
        assertEquals(connectCallsAfterWarmPrepare, connectFtmsTransportCalls)

        harness.orchestrator.simulateFtmsDisconnectedForTest(activeGeneration)
        manualHandler.advanceBy(1_499L)
        assertEquals(connectCallsAfterWarmPrepare, connectFtmsTransportCalls)

        manualHandler.advanceBy(1L)
        assertEquals(connectCallsAfterWarmPrepare + 1, connectFtmsTransportCalls)
        assertEquals(
            TrainerControlAuthority.RIDER_CONTROLLED,
            harness.uiState.trainerControlAuthority.value,
        )
        assertNull(harness.uiState.lastAppControlledTargetPower.value)

        assertFalse(harness.orchestrator.simulateBleReadyForTest(controlPointReady = true))
        manualHandler.advanceBy(0L)

        assertEquals(AppScreen.SESSION, harness.uiState.screen.value)
        assertTrue(harness.uiState.ftmsReady.value)
        assertFalse(harness.uiState.ftmsControlGranted.value)
        assertEquals(
            TrainerControlAuthority.RIDER_CONTROLLED,
            harness.uiState.trainerControlAuthority.value,
        )
    }

    @Test
    fun telemetryOnlySession_completesNominalStopFlowAndProducesSummary() {
        val manualHandler = ManualHandler()
        val harness = createHarness(
            mainHandler = manualHandler,
            isMockTrainerModeEnabled = { true },
        )
        harness.orchestrator.initialize()
        harness.orchestrator.onTelemetryOnlyModeSelected()

        harness.orchestrator.beginConnectFlowForTest()
        manualHandler.advanceBy(0)
        harness.orchestrator.simulateRequestControlGrantedForTest()
        manualHandler.advanceBy(0)
        assertEquals(AppScreen.SESSION, harness.uiState.screen.value)

        harness.orchestrator.requestStopForTest()
        manualHandler.advanceBy(0)
        assertEquals(AppScreen.SUMMARY, harness.uiState.screen.value)
        assertEquals(StopFlowState.IDLE, harness.uiState.stopFlowState.value)
        assertNotNull(harness.uiState.summary.value)

        assertNotNull(harness.uiState.session.value)
    }

    // endregion

    private fun createHarness(
        mainHandler: android.os.Handler = ManualHandler(),
        ensureBluetoothPermission: () -> Boolean = { true },
        currentFtmsDeviceMac: () -> String? = { "AA:BB:CC:DD:EE:FF" },
        connectFtmsTransport: (io.github.ewoc2026.ewoc.ble.FtmsBleClient, String) -> Unit = { _, _ -> },
        isMockTrainerModeEnabled: () -> Boolean = { false },
        consumeMockTrainerDebugScenario: () -> MockTrainerDebugScenario? = { null },
        connectHeartRate: () -> Unit = {},
        closeHeartRate: () -> Unit = {},
        allowLegacyWorkoutFallback: Boolean = false,
        indoorBikeParsingDispatcher: SessionIndoorBikeParsingDispatcher = DefaultSessionIndoorBikeParsingDispatcher(),
        sessionIdFactory: () -> String = { "session-default" },
    ): Harness {
        val uiState = AppUiState()
        val context = ContextWrapper(null)
        val diagnosticsEvents = mutableListOf<SessionDiagnosticsEvent>()

        var keepScreenOnCalls = 0
        var allowScreenOffCalls = 0

        val sessionManager = SessionManager(
            context = context,
            onStateUpdated = { state -> uiState.session.value = state },
        )
        val nowProvider = if (mainHandler is ManualHandler) {
            { mainHandler.currentTimeMs() }
        } else {
            { System.currentTimeMillis() }
        }

        val orchestrator = SessionOrchestrator(
            context = context,
            uiState = uiState,
            sessionManager = sessionManager,
            ensureBluetoothPermission = ensureBluetoothPermission,
            connectHeartRate = connectHeartRate,
            closeHeartRate = closeHeartRate,
            connectFtmsTransport = connectFtmsTransport,
            keepScreenOn = { keepScreenOnCalls += 1 },
            allowScreenOff = { allowScreenOffCalls += 1 },
            isMockTrainerModeEnabled = isMockTrainerModeEnabled,
            currentFtmsDeviceMac = currentFtmsDeviceMac,
            currentFtpWatts = { 250 },
            allowLegacyWorkoutFallback = allowLegacyWorkoutFallback,
            mainThreadHandler = mainHandler,
            indoorBikeParsingDispatcher = indoorBikeParsingDispatcher,
            mockTrainerEngineFactory = { handler ->
                val nowProvider = if (mainHandler is ManualHandler) {
                    { mainHandler.currentTimeMs() }
                } else {
                    { System.currentTimeMillis() }
                }
                MockTrainerEngine(
                    handler = handler,
                    nowElapsedMs = nowProvider,
                )
            },
            consumeMockTrainerDebugScenario = consumeMockTrainerDebugScenario,
            sessionIdFactory = sessionIdFactory,
            elapsedRealtimeMsProvider = nowProvider,
            diagnosticsNowMillis = nowProvider,
            recordDiagnosticsEvent = { event -> diagnosticsEvents += event },
        )

        return Harness(
            orchestrator = orchestrator,
            uiState = uiState,
            sessionManager = sessionManager,
            diagnosticsEvents = diagnosticsEvents,
            keepScreenOnCounter = { keepScreenOnCalls },
            allowScreenOffCounter = { allowScreenOffCalls },
        )
    }

    private data class Harness(
        val orchestrator: SessionOrchestrator,
        val uiState: AppUiState,
        val sessionManager: SessionManager,
        val diagnosticsEvents: List<SessionDiagnosticsEvent>,
        val keepScreenOnCounter: () -> Int,
        val allowScreenOffCounter: () -> Int,
    ) {
        val currentKeepScreenOnCalls: Int
            get() = keepScreenOnCounter()

        val currentAllowScreenOffCalls: Int
            get() = allowScreenOffCounter()

        /** Shorthand for the current pending permission request ID from the orchestrator. */
        val permissionRequestId: Long
            get() = orchestrator.pendingBluetoothPermissionRequestId

        /** Delivers a permission result with the current (matching) request ID. */
        fun deliverPermissionResult(granted: Boolean) {
            orchestrator.onBluetoothPermissionResult(granted, requestId = permissionRequestId)
        }

        /** Delivers a permission result with a specific (potentially stale) request ID. */
        fun deliverPermissionResult(granted: Boolean, requestId: Long) {
            orchestrator.onBluetoothPermissionResult(granted, requestId = requestId)
        }
    }

    private data class TransitionStep(
        val event: String,
        val expectedScreen: AppScreen,
        val expectedStopFlowState: StopFlowState? = null,
        val trigger: () -> Unit = {},
        val assertions: () -> Unit = {},
    )

    private fun runTransitionTable(
        scenario: String,
        uiState: AppUiState,
        steps: List<TransitionStep>,
    ) {
        steps.forEachIndexed { index, step ->
            step.trigger()
            assertEquals(
                "$scenario step ${index + 1} (${step.event}) screen",
                step.expectedScreen,
                uiState.screen.value,
            )
            step.expectedStopFlowState?.let { expectedStopFlowState ->
                assertEquals(
                    "$scenario step ${index + 1} (${step.event}) stopFlowState",
                    expectedStopFlowState,
                    uiState.stopFlowState.value,
                )
            }
            step.assertions()
        }
    }

    private fun countSessionEvent(events: List<SessionDiagnosticsEvent>, event: String): Int {
        return events.count { diagnostic -> diagnostic.category == "session" && diagnostic.event == event }
    }

    private fun countDiagnosticsEvent(
        events: List<SessionDiagnosticsEvent>,
        category: String,
        event: String,
    ): Int {
        return events.count { diagnostic ->
            diagnostic.category == category && diagnostic.event == event
        }
    }

    private fun indexOfSessionEvent(events: List<SessionDiagnosticsEvent>, event: String): Int {
        val index = events.indexOfFirst { diagnostic ->
            diagnostic.category == "session" && diagnostic.event == event
        }
        assertTrue("Expected session event '$event' in diagnostics.", index >= 0)
        return index
    }

    private fun firstSessionEventContext(
        events: List<SessionDiagnosticsEvent>,
        event: String,
        key: String,
    ): String? {
        return events.firstOrNull { diagnostic ->
            diagnostic.category == "session" && diagnostic.event == event
        }?.context?.get(key)
    }

    private fun firstDiagnosticsEventContext(
        events: List<SessionDiagnosticsEvent>,
        category: String,
        event: String,
        key: String,
    ): String? {
        return events.firstOrNull { diagnostic ->
            diagnostic.category == category && diagnostic.event == event
        }?.context?.get(key)
    }

    private fun readyWorkout(): WorkoutFile {
        return WorkoutFile(
            name = "Permission Flow Workout",
            description = null,
            author = null,
            tags = emptyList(),
            steps = listOf(
                Step.SteadyState(
                    durationSec = 180,
                    power = 0.75,
                    cadence = 90,
                )
            ),
        )
    }

    private fun unsupportedWorkout(): WorkoutFile {
        return WorkoutFile(
            name = "Unsupported Workout",
            description = null,
            author = null,
            tags = emptyList(),
            steps = listOf(
                Step.SteadyState(
                    durationSec = null,
                    power = null,
                    cadence = null,
                ),
            ),
        )
    }

    private fun powerOnlyImportedWorkout(): ImportedErgoWorkout {
        return ImportedErgoWorkout(
            title = "Imported power builder",
            description = "Absolute watts",
            steps = listOf(
                ImportedErgoWorkoutStep.PowerSteady(
                    stepIndex = 0,
                    startOffsetSec = 0,
                    durationSec = 300,
                    watts = 200,
                ),
                ImportedErgoWorkoutStep.PowerRamp(
                    stepIndex = 1,
                    startOffsetSec = 300,
                    durationSec = 300,
                    fromWatts = 200,
                    toWatts = 250,
                ),
            ),
            totalDurationSec = 600,
        )
    }

    private fun heartRateImportedWorkout(
        initialPowerWatts: Int = 180,
        minPowerWatts: Int = 120,
        maxPowerWatts: Int = 260,
        hrUpperCapBpm: Int = 185,
    ): ImportedErgoWorkout {
        return ImportedErgoWorkout(
            title = "Imported HR builder",
            description = null,
            steps = listOf(
                ImportedErgoWorkoutStep.HeartRateSteady(
                    stepIndex = 0,
                    startOffsetSec = 0,
                    durationSec = 600,
                    lowBpm = 140,
                    highBpm = 150,
                    initialPowerWatts = initialPowerWatts,
                    minPowerWatts = minPowerWatts,
                    maxPowerWatts = maxPowerWatts,
                    signalLossPowerWatts = 150,
                ),
            ),
            totalDurationSec = 600,
            canonicalMetadata = ImportedErgoWorkoutCanonicalMetadata(
                uid = null,
                revision = null,
                control = ImportedErgoWorkoutControl(
                    initialPowerWatts = initialPowerWatts,
                    minPowerWatts = minPowerWatts,
                    maxPowerWatts = maxPowerWatts,
                    signalLossPowerWatts = 150,
                    hrUpperCapBpm = hrUpperCapBpm,
                ),
                messages = emptyList(),
            ),
        )
    }

    private fun powerRampThenHeartRateImportedWorkout(): ImportedErgoWorkout {
        return ImportedErgoWorkout(
            title = "Ramp then HR",
            description = null,
            steps = listOf(
                ImportedErgoWorkoutStep.PowerRamp(
                    stepIndex = 0,
                    startOffsetSec = 0,
                    durationSec = 4,
                    fromWatts = 200,
                    toWatts = 230,
                ),
                ImportedErgoWorkoutStep.HeartRateSteady(
                    stepIndex = 1,
                    startOffsetSec = 4,
                    durationSec = 6,
                    lowBpm = 140,
                    highBpm = 150,
                    initialPowerWatts = 180,
                    minPowerWatts = 120,
                    maxPowerWatts = 260,
                    signalLossPowerWatts = 150,
                ),
            ),
            totalDurationSec = 10,
            canonicalMetadata = ImportedErgoWorkoutCanonicalMetadata(
                uid = null,
                revision = null,
                control = ImportedErgoWorkoutControl(
                    initialPowerWatts = 180,
                    minPowerWatts = 120,
                    maxPowerWatts = 260,
                    signalLossPowerWatts = 150,
                    hrUpperCapBpm = 185,
                ),
                messages = emptyList(),
            ),
        )
    }

    private fun bikeData(
        powerW: Int,
        distanceMeters: Int? = null,
        totalEnergyKcal: Int? = null,
    ): IndoorBikeData {
        return IndoorBikeData(
            valid = true,
            instantaneousSpeedKmh = null,
            averageSpeedKmh = null,
            instantaneousCadenceRpm = null,
            averageCadenceRpm = null,
            totalDistanceMeters = distanceMeters,
            resistanceLevel = null,
            instantaneousPowerW = powerW,
            averagePowerW = null,
            totalEnergyKcal = totalEnergyKcal,
            energyPerHourKcal = null,
            energyPerMinuteKcal = null,
            heartRateBpm = null,
            metabolicEquivalent = null,
            elapsedTimeSeconds = null,
            remainingTimeSeconds = null,
        )
    }


    private fun indoorBikePayloadWithCadence(cadenceRpm: Double): ByteArray {
        val rawCadence = (cadenceRpm * 2.0).toInt().coerceIn(0, 0xFFFF)
        return byteArrayOf(
            0x04, 0x00,
            0x00, 0x00,
            (rawCadence and 0xFF).toByte(),
            ((rawCadence shr 8) and 0xFF).toByte(),
        )
    }

    private fun indoorBikePayloadWithCadenceAndHeartRate(
        cadenceRpm: Double,
        heartRateBpm: Int,
    ): ByteArray {
        val rawCadence = (cadenceRpm * 2.0).toInt().coerceIn(0, 0xFFFF)
        return byteArrayOf(
            0x04, 0x08,
            0x00, 0x00,
            (rawCadence and 0xFF).toByte(),
            ((rawCadence shr 8) and 0xFF).toByte(),
            heartRateBpm.coerceIn(0, 0xFF).toByte(),
        )
    }

    private fun malformedIndoorBikePayload(): ByteArray {
        return byteArrayOf(
            0x04, 0x00,
            0x00, 0x00,
            0x01,
        )
    }

    private class ManualIndoorBikeParsingDispatcher : SessionIndoorBikeParsingDispatcher {
        private val queuedTasks = mutableListOf<() -> Unit>()
        private var closed = false

        override fun dispatch(task: () -> Unit): Boolean {
            if (closed) return false
            queuedTasks += task
            return true
        }

        override fun close() {
            closed = true
            queuedTasks.clear()
        }

        fun runFirst() {
            require(queuedTasks.isNotEmpty()) { "Expected at least one queued parse task." }
            val task = queuedTasks.removeAt(0)
            task()
        }

        fun runLast() {
            require(queuedTasks.isNotEmpty()) { "Expected at least one queued parse task." }
            val task = queuedTasks.removeAt(queuedTasks.lastIndex)
            task()
        }

        fun runAll() {
            while (queuedTasks.isNotEmpty()) {
                runFirst()
            }
        }
    }

    private class ManualHandler : android.os.Handler(android.os.Looper.getMainLooper()) {
        private data class ScheduledRunnable(
            val runnable: Runnable,
            val runAtMs: Long,
            val order: Long,
        )

        private val queue = mutableListOf<ScheduledRunnable>()
        private var nextOrder = 0L
        private var nowMs = 0L

        override fun post(runnable: Runnable): Boolean {
            queue += ScheduledRunnable(
                runnable = runnable,
                runAtMs = nowMs,
                order = nextOrder++,
            )
            return true
        }

        override fun postDelayed(runnable: Runnable, delayMillis: Long): Boolean {
            queue += ScheduledRunnable(
                runnable = runnable,
                runAtMs = nowMs + delayMillis.coerceAtLeast(0L),
                order = nextOrder++,
            )
            return true
        }

        override fun removeCallbacks(runnable: Runnable) {
            queue.removeAll { it.runnable === runnable }
        }

        fun currentTimeMs(): Long = nowMs

        fun advanceBy(deltaMs: Long) {
            require(deltaMs >= 0L) { "Delta must be non-negative." }
            val targetMs = nowMs + deltaMs
            while (true) {
                val next = queue
                    .filter { it.runAtMs <= targetMs }
                    .minWithOrNull(compareBy<ScheduledRunnable> { it.runAtMs }.thenBy { it.order })
                    ?: break
                queue.remove(next)
                nowMs = next.runAtMs
                next.runnable.run()
            }
            nowMs = targetMs
        }
    }

}

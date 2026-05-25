package com.example.ergometerapp

import android.content.pm.ActivityInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.android.ComposeNotIdleException
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.runtime.MutableState
import androidx.lifecycle.ViewModelProvider
import com.example.ergometerapp.session.SessionPhase
import com.example.ergometerapp.workout.Step
import com.example.ergometerapp.workout.WorkoutFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Guards lifecycle-sensitive destination continuity so launch and active-session anchors stay
 * stable across activity recreation.
 */
class MainActivityLifecycleContinuityTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun launchStartsInMenuWithStartGateClosedByDefault() {
        val menuTitle = composeRule.activity.getString(R.string.menu_title)

        waitUntilMenuVisible(menuTitle = menuTitle)
        composeRule.onNodeWithText(menuTitle).assertIsDisplayed()
        composeRule.runOnIdle {
            val viewModel = currentViewModel()
            assertEquals(AppScreen.MENU, viewModel.uiState.screen.value)
            assertFalse(viewModel.uiState.workoutReady.value)
            assertFalse(viewModel.canStartSession())
        }
    }

    @Test
    fun activeSessionRemainsVisibleAndRunningAcrossRotation() {
        val initialMockTrainerMode = currentViewModel().mockTrainerModeEnabledState.value

        try {
            composeRule.runOnUiThread {
                val viewModel = currentViewModel()
                viewModel.onMockTrainerModeChanged(enabled = true)
                seedWorkoutReadyFixture(viewModel)
                viewModel.onStartSession()
            }

            assertSessionVisibleAndRunning()
            rotateTo(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
            assertSessionVisibleAndRunning()
            rotateTo(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
            assertSessionVisibleAndRunning()
        } finally {
            composeRule.runOnUiThread {
                val viewModel = currentViewModel()
                viewModel.onMockTrainerModeChanged(enabled = initialMockTrainerMode)
                composeRule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    @Test
    fun connectingTimeoutRecoveryMaintainsExpectedDestinationFlow() {
        val timeoutTitle = composeRule.activity.getString(R.string.connecting_timeout_title)
        val timeoutMessage = composeRule.activity.getString(R.string.connecting_timeout_message)
        val keepWaitingLabel = composeRule.activity.getString(R.string.connecting_timeout_keep_waiting)
        val retryLabel = composeRule.activity.getString(R.string.connecting_timeout_retry)
        val backToMenuLabel = composeRule.activity.getString(R.string.connecting_timeout_back_to_menu)
        val menuTitle = composeRule.activity.getString(R.string.menu_title)
        val initialMockTrainerMode = currentViewModel().mockTrainerModeEnabledState.value

        try {
            composeRule.runOnUiThread {
                val viewModel = currentViewModel()
                viewModel.onMockTrainerModeChanged(enabled = true)
                seedWorkoutReadyFixture(viewModel)
                viewModel.uiState.screen.value = AppScreen.CONNECTING
                viewModel.uiState.connectingTimeoutMessage.value = timeoutMessage
            }

            assertConnectingTimeoutPromptVisible(
                timeoutTitle = timeoutTitle,
                timeoutMessage = timeoutMessage,
                keepWaitingLabel = keepWaitingLabel,
                retryLabel = retryLabel,
                backToMenuLabel = backToMenuLabel,
            )

            composeRule.onNodeWithText(keepWaitingLabel).performClick()
            composeRule.runOnIdle {
                val viewModel = currentViewModel()
                assertEquals(AppScreen.CONNECTING, viewModel.uiState.screen.value)
                assertNull(viewModel.uiState.connectingTimeoutMessage.value)
            }

            composeRule.runOnUiThread {
                val viewModel = currentViewModel()
                viewModel.uiState.connectingTimeoutMessage.value = timeoutMessage
            }
            assertConnectingTimeoutPromptVisible(
                timeoutTitle = timeoutTitle,
                timeoutMessage = timeoutMessage,
                keepWaitingLabel = keepWaitingLabel,
                retryLabel = retryLabel,
                backToMenuLabel = backToMenuLabel,
            )
            composeRule.onNodeWithText(backToMenuLabel).performClick()
            waitUntilMenuVisible(menuTitle = menuTitle)
            composeRule.runOnIdle {
                val viewModel = currentViewModel()
                assertEquals(AppScreen.MENU, viewModel.uiState.screen.value)
                assertNotNull(viewModel.uiState.connectionIssueMessage.value)
                assertTrue(viewModel.uiState.suggestTrainerSearchAfterConnectionIssue.value)
                assertFalse(viewModel.uiState.suggestOpenSettingsAfterConnectionIssue.value)
            }

            composeRule.runOnUiThread {
                val viewModel = currentViewModel()
                viewModel.uiState.screen.value = AppScreen.CONNECTING
                viewModel.uiState.connectingTimeoutMessage.value = timeoutMessage
            }
            composeRule.onNodeWithText(retryLabel).performClick()
            assertSessionVisibleAndRunning()
        } finally {
            composeRule.runOnUiThread {
                val viewModel = currentViewModel()
                viewModel.onMockTrainerModeChanged(enabled = initialMockTrainerMode)
                viewModel.onBackToMenu()
                composeRule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    @Test
    fun stopFlowTransitionsToSummaryAndSurvivesRotation() {
        val initialMockTrainerMode = currentViewModel().mockTrainerModeEnabledState.value

        try {
            composeRule.runOnUiThread {
                val viewModel = currentViewModel()
                viewModel.onMockTrainerModeChanged(enabled = true)
                seedWorkoutReadyFixture(viewModel)
                viewModel.onStartSession()
            }
            assertSessionVisibleAndRunning()

            composeRule.runOnUiThread {
                val viewModel = currentViewModel()
                viewModel.onMockTrainerModeChanged(enabled = false)
                viewModel.onEndSessionAndGoToSummary()
            }
            waitUntilScreen(screen = AppScreen.STOPPING)
            waitUntilScreen(screen = AppScreen.SUMMARY)

            rotateTo(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
            waitUntilScreen(screen = AppScreen.SUMMARY)

            rotateTo(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
            waitUntilScreen(screen = AppScreen.SUMMARY)
        } finally {
            composeRule.runOnUiThread {
                val viewModel = currentViewModel()
                viewModel.onMockTrainerModeChanged(enabled = initialMockTrainerMode)
                viewModel.onBackToMenu()
                composeRule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    @Test
    fun reconnectMidSessionReturnsToSessionWithoutLosingRunningState() {
        val initialMockTrainerMode = currentViewModel().mockTrainerModeEnabledState.value

        try {
            composeRule.runOnUiThread {
                val viewModel = currentViewModel()
                viewModel.onMockTrainerModeChanged(enabled = true)
                seedWorkoutReadyFixture(viewModel)
                viewModel.onStartSession()
            }
            assertSessionVisibleAndRunning()

            composeRule.runOnUiThread {
                val viewModel = currentViewModel()
                invokeSessionOrchestratorNoArg(viewModel, methodName = "beginConnectFlowForTest")
            }
            waitUntilScreen(screen = AppScreen.CONNECTING)

            composeRule.runOnUiThread {
                val viewModel = currentViewModel()
                invokeSessionOrchestratorNoArg(viewModel, methodName = "simulateRequestControlGrantedForTest")
            }
            assertSessionVisibleAndRunning()
        } finally {
            composeRule.runOnUiThread {
                val viewModel = currentViewModel()
                viewModel.onMockTrainerModeChanged(enabled = initialMockTrainerMode)
                viewModel.onBackToMenu()
                composeRule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    @Test
    fun bluetoothConnectPermissionDenyThenGrantMaintainsStateContinuity() {
        val menuTitle = composeRule.activity.getString(R.string.menu_title)
        var connectPermissionGranted = false
        var scanPermissionGranted = true
        val initialMockTrainerMode = currentViewModel().mockTrainerModeEnabledState.value

        try {
            composeRule.runOnUiThread {
                val viewModel = currentViewModel()
                viewModel.bindActivityCallbacks(
                    ensureBluetoothConnectPermission = { connectPermissionGranted },
                    ensureBluetoothScanPermission = { scanPermissionGranted },
                    requestHealthConnectPermissions = {},
                    currentBillingActivity = { composeRule.activity },
                    keepScreenOn = {},
                    allowScreenOff = {},
                )
                viewModel.onMockTrainerModeChanged(enabled = false)
                seedWorkoutReadyFixture(viewModel)
                seedTrainerSelectionForSessionStart(viewModel)
                viewModel.onStartSession()
            }
            waitUntilMenuVisible(menuTitle = menuTitle)
            composeRule.runOnIdle {
                val viewModel = currentViewModel()
                assertEquals(AppScreen.MENU, viewModel.uiState.screen.value)
                assertTrue(viewModel.uiState.pendingSessionStartAfterPermission)
            }

            composeRule.runOnUiThread {
                val vm = currentViewModel()
                vm.onBluetoothPermissionResult(granted = false, requestId = vm.pendingBluetoothPermissionRequestId)
            }
            waitUntilMenuVisible(menuTitle = menuTitle)
            composeRule.runOnIdle {
                val viewModel = currentViewModel()
                assertEquals(AppScreen.MENU, viewModel.uiState.screen.value)
                assertFalse(viewModel.uiState.pendingSessionStartAfterPermission)
                assertNotNull(viewModel.uiState.connectionIssueMessage.value)
                assertFalse(viewModel.uiState.suggestTrainerSearchAfterConnectionIssue.value)
                assertTrue(viewModel.uiState.suggestOpenSettingsAfterConnectionIssue.value)
            }

            composeRule.runOnUiThread {
                val viewModel = currentViewModel()
                viewModel.clearConnectionIssuePrompt()
                viewModel.onStartSession()
            }
            composeRule.runOnIdle {
                assertTrue(currentViewModel().uiState.pendingSessionStartAfterPermission)
            }

            composeRule.runOnUiThread {
                val vmGrant = currentViewModel()
                vmGrant.onBluetoothPermissionResult(granted = true, requestId = vmGrant.pendingBluetoothPermissionRequestId)
            }
            waitUntilPendingSessionStart(expected = false)
            composeRule.runOnIdle {
                val viewModel = currentViewModel()
                assertFalse(viewModel.uiState.pendingSessionStartAfterPermission)
                val destinationAfterGrant = viewModel.uiState.screen.value
                assertTrue(
                    destinationAfterGrant == AppScreen.CONNECTING ||
                        destinationAfterGrant == AppScreen.MENU
                )
            }
        } finally {
            composeRule.runOnUiThread {
                val viewModel = currentViewModel()
                viewModel.onMockTrainerModeChanged(enabled = initialMockTrainerMode)
                viewModel.onBackToMenu()
                composeRule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    @Test
    fun bluetoothScanPermissionDenyThenGrantKeepsMenuContinuity() {
        val menuTitle = composeRule.activity.getString(R.string.menu_title)
        val permissionRequired = composeRule.activity.getString(R.string.menu_device_scan_permission_required)
        var scanPermissionGranted = false
        val initialMockTrainerMode = currentViewModel().mockTrainerModeEnabledState.value

        try {
            composeRule.runOnUiThread {
                val viewModel = currentViewModel()
                viewModel.bindActivityCallbacks(
                    ensureBluetoothConnectPermission = { true },
                    ensureBluetoothScanPermission = { scanPermissionGranted },
                    requestHealthConnectPermissions = {},
                    currentBillingActivity = { composeRule.activity },
                    keepScreenOn = {},
                    allowScreenOff = {},
                )
                viewModel.onMockTrainerModeChanged(enabled = false)
            }
            waitUntilMenuVisible(menuTitle = menuTitle)

            composeRule.runOnUiThread {
                currentViewModel().onSearchFtmsDevicesRequested()
                currentViewModel().onBluetoothScanPermissionResult(granted = false)
            }
            composeRule.runOnIdle {
                val viewModel = currentViewModel()
                assertEquals(AppScreen.MENU, viewModel.uiState.screen.value)
                assertFalse(viewModel.deviceScanInProgressState.value)
                assertNull(viewModel.activeDeviceSelectionKindState.value)
                assertEquals(permissionRequired, viewModel.deviceScanStatusState.value)
            }

            composeRule.runOnUiThread {
                currentViewModel().onSearchFtmsDevicesRequested()
                scanPermissionGranted = true
                currentViewModel().onBluetoothScanPermissionResult(granted = true)
            }
            composeRule.waitUntil(20_000L) {
                currentViewModel().activeDeviceSelectionKindState.value == DeviceSelectionKind.FTMS
            }
            composeRule.runOnIdle {
                val viewModel = currentViewModel()
                assertEquals(AppScreen.MENU, viewModel.uiState.screen.value)
                assertEquals(DeviceSelectionKind.FTMS, viewModel.activeDeviceSelectionKindState.value)
                assertFalse(viewModel.deviceScanStatusState.value == permissionRequired)
            }
        } finally {
            composeRule.runOnUiThread {
                val viewModel = currentViewModel()
                viewModel.onDismissDeviceSelection()
                viewModel.onMockTrainerModeChanged(enabled = initialMockTrainerMode)
                viewModel.onBackToMenu()
                composeRule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    private fun assertConnectingTimeoutPromptVisible(
        timeoutTitle: String,
        timeoutMessage: String,
        keepWaitingLabel: String,
        retryLabel: String,
        backToMenuLabel: String,
    ) {
        waitUntilTextVisible(text = timeoutTitle)
        composeRule.onNodeWithText(timeoutTitle).assertIsDisplayed()
        composeRule.onNodeWithText(timeoutMessage).assertIsDisplayed()
        composeRule.onNodeWithText(keepWaitingLabel).assertIsDisplayed()
        composeRule.onNodeWithText(retryLabel).assertIsDisplayed()
        composeRule.onNodeWithText(backToMenuLabel).assertIsDisplayed()
    }

    private fun seedWorkoutReadyFixture(viewModel: MainViewModel) {
        viewModel.uiState.selectedWorkout.value = minimalWorkoutFixture()
        viewModel.uiState.selectedWorkoutFileName.value = "instrumentation-fixture.zwo"
        viewModel.uiState.selectedWorkoutStepCount.value = 1
        viewModel.uiState.selectedWorkoutPlannedTss.value = 0.0
        viewModel.uiState.selectedWorkoutImportError.value = null
        viewModel.uiState.workoutReady.value = true
    }

    private fun seedTrainerSelectionForSessionStart(viewModel: MainViewModel) {
        viewModel.ftmsDeviceNameState.value = "Instrumentation Trainer"
        viewModel.selectedFtmsDeviceMacState.value = "AA:BB:CC:DD:EE:FF"
    }

    private fun invokeSessionOrchestratorNoArg(
        viewModel: MainViewModel,
        methodName: String,
    ) {
        val orchestratorField = MainViewModel::class.java.getDeclaredField("sessionOrchestrator")
        orchestratorField.isAccessible = true
        val orchestrator = orchestratorField.get(viewModel)
        val method = orchestrator.javaClass.declaredMethods.firstOrNull { candidate ->
            candidate.parameterCount == 0 &&
                (candidate.name == methodName || candidate.name.startsWith("${methodName}$"))
        } ?: throw NoSuchMethodException(
            "${orchestrator.javaClass.name}.$methodName (or Kotlin-mangled equivalent) []"
        )
        method.isAccessible = true
        method.invoke(orchestrator)
    }

    private fun waitUntilMenuVisible(
        menuTitle: String,
        timeoutMillis: Long = 20_000L,
    ) {
        composeRule.waitUntil(timeoutMillis) {
            try {
                composeRule.onNodeWithText(menuTitle).assertIsDisplayed()
                true
            } catch (_: AssertionError) {
                false
            } catch (_: IllegalStateException) {
                false
            } catch (_: ComposeNotIdleException) {
                false
            }
        }
    }

    private fun waitUntilScreen(
        screen: AppScreen,
        timeoutMillis: Long = 20_000L,
    ) {
        composeRule.waitUntil(timeoutMillis) {
            currentViewModel().uiState.screen.value == screen
        }
    }

    private fun waitUntilPendingSessionStart(
        expected: Boolean,
        timeoutMillis: Long = 20_000L,
    ) {
        composeRule.waitUntil(timeoutMillis) {
            currentViewModel().uiState.pendingSessionStartAfterPermission == expected
        }
    }

    private fun waitUntilTextVisible(
        text: String,
        timeoutMillis: Long = 20_000L,
    ) {
        composeRule.waitUntil(timeoutMillis) {
            try {
                composeRule.onNodeWithText(text).assertIsDisplayed()
                true
            } catch (_: AssertionError) {
                false
            } catch (_: IllegalStateException) {
                false
            } catch (_: ComposeNotIdleException) {
                false
            }
        }
    }

    private fun assertSessionVisibleAndRunning(
        timeoutMillis: Long = 20_000L,
    ) {
        composeRule.waitUntil(timeoutMillis) {
            try {
                composeRule.onNodeWithTag("sessionScreenRoot").assertIsDisplayed()
                true
            } catch (_: AssertionError) {
                false
            } catch (_: IllegalStateException) {
                false
            } catch (_: ComposeNotIdleException) {
                false
            }
        }

        composeRule.runOnIdle {
            val viewModel = currentViewModel()
            assertEquals(AppScreen.SESSION, viewModel.uiState.screen.value)
            assertEquals(SessionPhase.RUNNING, viewModel.phase())
            assertTrue(viewModel.uiState.ftmsReady.value)
            assertTrue(viewModel.uiState.ftmsControlGranted.value)
        }
    }

    private fun rotateTo(orientation: Int) {
        composeRule.runOnUiThread {
            composeRule.activity.requestedOrientation = orientation
        }
    }

    private fun minimalWorkoutFixture(): WorkoutFile {
        return WorkoutFile(
            name = "Instrumentation Fixture",
            description = "Deterministic workout used by lifecycle instrumentation coverage.",
            author = "androidTest",
            tags = emptyList(),
            steps = listOf(
                Step.SteadyState(
                    durationSec = 60,
                    power = 0.55,
                    cadence = 85,
                )
            ),
            textEvents = emptyList(),
        )
    }

    private fun currentViewModel(): MainViewModel {
        return ViewModelProvider(composeRule.activity)[MainViewModel::class.java]
    }
}

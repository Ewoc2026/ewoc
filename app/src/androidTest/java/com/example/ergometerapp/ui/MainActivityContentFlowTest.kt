package com.example.ergometerapp.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.test.filters.FlakyTest
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import com.example.ergometerapp.AppScreen
import com.example.ergometerapp.DeviceSelectionKind
import com.example.ergometerapp.FitExportPreference
import com.example.ergometerapp.R
import com.example.ergometerapp.ScannedBleDevice
import com.example.ergometerapp.session.SessionPhase
import com.example.ergometerapp.session.SessionSummary
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MainActivityContentFlowTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    // CI emulators occasionally fail this viewport scroll assertion despite local determinism.
    @FlakyTest
    @Test
    fun criticalFlowScreensRenderExpectedAnchors() {
        val modelState = mutableStateOf(MainActivityUiModelTestFactory.base(screen = AppScreen.MENU))

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

        composeRule.onNodeWithText(composeRule.activity.getString(R.string.menu_title)).assertIsDisplayed()

        composeRule.runOnIdle {
            modelState.value = MainActivityUiModelTestFactory.base(screen = AppScreen.CONNECTING)
        }
        composeRule.onNodeWithText(
            normalizedWaitingStatus(R.string.status_connecting),
            substring = true,
        ).assertIsDisplayed()

        composeRule.runOnIdle {
            modelState.value = MainActivityUiModelTestFactory.base(screen = AppScreen.SESSION)
        }
        composeRule.waitForIdle()
        composeRule.onNode(hasScrollToIndexAction()).performScrollToIndex(1)
        composeRule.onNodeWithTag("sessionQuitButton").assertIsDisplayed()

        composeRule.runOnIdle {
            modelState.value = MainActivityUiModelTestFactory.base(screen = AppScreen.STOPPING)
        }
        composeRule.onNodeWithText(
            normalizedWaitingStatus(R.string.status_stopping),
            substring = true,
        ).assertIsDisplayed()

        composeRule.runOnIdle {
            modelState.value = MainActivityUiModelTestFactory.base(
                screen = AppScreen.SUMMARY,
                summary = SessionSummary(
                    startTimestampMillis = 1_700_000_000_000L,
                    stopTimestampMillis = 1_700_000_120_000L,
                    durationSeconds = 120,
                    actualTss = 3.3,
                    avgPower = 200,
                    maxPower = 260,
                    avgCadence = 85,
                    maxCadence = 95,
                    avgHeartRate = 140,
                    maxHeartRate = 158,
                    distanceMeters = 1000,
                    totalEnergyKcal = 35,
                )
            )
        }
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.summary_duration)).assertIsDisplayed()
    }

    @Test
    fun menuPickerScanAndCloseStatesRenderExpectedActions() {
        val modelState = mutableStateOf(MainActivityUiModelTestFactory.base(screen = AppScreen.MENU))
        val scanningStatus = composeRule.activity.getString(R.string.menu_device_scan_status_scanning)
        val scanningStatusBase = normalizedStatusLabel(scanningStatus)

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

        composeRule.runOnIdle {
            modelState.value = MainActivityUiModelTestFactory.base(screen = AppScreen.MENU).withMenuState {
                copy(
                    activeDeviceSelectionKind = DeviceSelectionKind.FTMS,
                    deviceScanInProgress = true,
                    deviceScanStopEnabled = false,
                    deviceScanStatus = scanningStatus,
                )
            }
        }

        openSetupStep(R.string.menu_setup_step_devices_title)
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.menu_trainer_picker_title)
        ).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(
            scanningStatusBase,
            substring = true,
        ).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.menu_cancel_device_scan)
        ).performScrollTo().assertIsDisplayed().assertIsNotEnabled()

        composeRule.runOnIdle {
            modelState.value = MainActivityUiModelTestFactory.base(screen = AppScreen.MENU).withMenuState {
                copy(
                    activeDeviceSelectionKind = DeviceSelectionKind.FTMS,
                    deviceScanInProgress = false,
                    deviceScanStopEnabled = true,
                    deviceScanStatus = composeRule.activity.getString(R.string.menu_device_scan_status_failed),
                )
            }
        }

        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.menu_device_scan_status_failed)
        ).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.menu_close_device_picker)
        ).performScrollTo().assertIsDisplayed().assertIsEnabled()
    }

    @Test
    fun menuConnectionIssueDialogShowsRecoveryActionsAndRoutesCallbacks() {
        val modelState = mutableStateOf(MainActivityUiModelTestFactory.base(screen = AppScreen.MENU))
        var searchAgainClicks = 0
        var dismissClicks = 0
        val issueMessage = "Request control timed out"

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
                onDismissConnectionIssue = { dismissClicks += 1 },
                onSearchFtmsDevicesFromConnectionIssue = { searchAgainClicks += 1 },
                onOpenAppSettingsFromConnectionIssue = {},
                onAiMenuAssistantAction = { false },
                onStartSession = {},
                onEndSession = {},
                onBackToMenu = {},
                onRequestSummaryFitExport = {},
            )
        }

        composeRule.runOnIdle {
            modelState.value = MainActivityUiModelTestFactory.base(screen = AppScreen.MENU).withMenuState {
                copy(
                    connectionIssueMessage = issueMessage,
                    suggestTrainerSearchAfterConnectionIssue = true,
                )
            }
        }

        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.menu_connection_issue_title)
        ).assertIsDisplayed()
        composeRule.onNodeWithText(issueMessage).assertIsDisplayed()

        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.menu_connection_issue_search_again)
        ).assertIsDisplayed().performClick()
        composeRule.runOnIdle {
            assertEquals(1, searchAgainClicks)
            assertEquals(0, dismissClicks)
        }

        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.menu_connection_issue_dismiss)
        ).assertIsDisplayed().performClick()
        composeRule.runOnIdle {
            assertEquals(1, dismissClicks)
        }
    }

    @Test
    fun menuConnectionIssueDialogSupportsOpenSettingsRecoveryAction() {
        val modelState = mutableStateOf(MainActivityUiModelTestFactory.base(screen = AppScreen.MENU))
        var openSettingsClicks = 0
        var dismissClicks = 0
        val issueMessage = "Bluetooth permission is required."

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
                onDismissConnectionIssue = { dismissClicks += 1 },
                onSearchFtmsDevicesFromConnectionIssue = {},
                onOpenAppSettingsFromConnectionIssue = { openSettingsClicks += 1 },
                onAiMenuAssistantAction = { false },
                onStartSession = {},
                onEndSession = {},
                onBackToMenu = {},
                onRequestSummaryFitExport = {},
            )
        }

        composeRule.runOnIdle {
            modelState.value = MainActivityUiModelTestFactory.base(screen = AppScreen.MENU).withMenuState {
                copy(
                    connectionIssueMessage = issueMessage,
                    suggestTrainerSearchAfterConnectionIssue = false,
                    suggestOpenSettingsAfterConnectionIssue = true,
                )
            }
        }

        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.menu_connection_issue_open_settings)
        ).assertIsDisplayed().performClick()
        composeRule.runOnIdle {
            assertEquals(1, openSettingsClicks)
            assertEquals(0, dismissClicks)
        }

        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.menu_connection_issue_dismiss)
        ).assertIsDisplayed().performClick()
        composeRule.runOnIdle {
            assertEquals(1, dismissClicks)
        }
    }

    @Test
    fun menuPickerFlowStaysConsistentAcrossScanPermissionDenyThenGrant() {
        val modelState = mutableStateOf(MainActivityUiModelTestFactory.base(screen = AppScreen.MENU))
        val permissionRequired = composeRule.activity.getString(R.string.menu_device_scan_permission_required)
        val scanningStatus = composeRule.activity.getString(R.string.menu_device_scan_status_scanning)
        val scanningStatusBase = normalizedStatusLabel(scanningStatus)
        val doneStatus = composeRule.activity.getString(R.string.menu_device_scan_status_done, 1)

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

        openSetupStep(R.string.menu_setup_step_devices_title)
        composeRule.runOnIdle {
            modelState.value = MainActivityUiModelTestFactory.base(screen = AppScreen.MENU).withMenuState {
                copy(
                    activeDeviceSelectionKind = DeviceSelectionKind.FTMS,
                    deviceScanInProgress = false,
                    deviceScanStopEnabled = true,
                    deviceScanStatus = permissionRequired,
                )
            }
        }

        composeRule.onNodeWithText(composeRule.activity.getString(R.string.menu_title)).assertIsDisplayed()
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.menu_trainer_picker_title)
        ).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(permissionRequired).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.menu_close_device_picker)
        ).performScrollTo().assertIsDisplayed().assertIsEnabled()

        composeRule.runOnIdle {
            modelState.value = MainActivityUiModelTestFactory.base(screen = AppScreen.MENU).withMenuState {
                copy(
                    activeDeviceSelectionKind = DeviceSelectionKind.FTMS,
                    deviceScanInProgress = true,
                    deviceScanStopEnabled = false,
                    deviceScanStatus = scanningStatus,
                )
            }
        }

        composeRule.onNodeWithText(
            scanningStatusBase,
            substring = true,
        ).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.menu_cancel_device_scan)
        ).performScrollTo().assertIsDisplayed().assertIsNotEnabled()

        composeRule.runOnIdle {
            modelState.value = MainActivityUiModelTestFactory.base(screen = AppScreen.MENU).withMenuState {
                copy(
                    activeDeviceSelectionKind = DeviceSelectionKind.FTMS,
                    scannedDevices = listOf(
                        ScannedBleDevice(
                            macAddress = "AA:BB:CC:DD:EE:FF",
                            displayName = "Tunturi Trainer",
                            rssi = -48,
                        )
                    ),
                    deviceScanInProgress = false,
                    deviceScanStopEnabled = true,
                    deviceScanStatus = doneStatus,
                )
            }
        }

        composeRule.onNodeWithText(doneStatus).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Tunturi Trainer", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.menu_close_device_picker)
        ).performScrollTo().assertIsDisplayed().assertIsEnabled()
    }

    @Test
    fun startSessionButtonRemainsDisabledWhenStartEnabledIsFalse() {
        val modelState = mutableStateOf(
            MainActivityUiModelTestFactory.base(screen = AppScreen.MENU).withMenuState { copy(startEnabled = false) }
        )
        var startClicks = 0
        val blockedReason = composeRule.activity.getString(R.string.menu_start_blocked_select_workout)

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
                onStartSession = { startClicks += 1 },
                onEndSession = {},
                onBackToMenu = {},
                onRequestSummaryFitExport = {},
            )
        }

        openSetupStep(R.string.menu_setup_step_summary_title)
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.menu_start_session)
        ).assertIsDisplayed().assertIsNotEnabled()
        composeRule.onNodeWithText(blockedReason).assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(0, startClicks)
        }
    }

    @Test
    fun startSessionAnchorsStayConsistentAcrossConnectPermissionDenyThenGrant() {
        val modelState = mutableStateOf(
            MainActivityUiModelTestFactory.base(screen = AppScreen.MENU).withMenuState { copy(startEnabled = true) }
        )
        var startClicks = 0

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
                onStartSession = { startClicks += 1 },
                onEndSession = {},
                onBackToMenu = {},
                onRequestSummaryFitExport = {},
            )
        }

        val menuTitle = composeRule.activity.getString(R.string.menu_title)
        val startSession = composeRule.activity.getString(R.string.menu_start_session)
        val connecting = normalizedWaitingStatus(R.string.status_connecting)
        val connectingHint = composeRule.activity.getString(R.string.menu_connection_hint)

        openSetupStep(R.string.menu_setup_step_summary_title)
        composeRule.onNodeWithText(menuTitle).assertIsDisplayed()
        composeRule.onNodeWithText(startSession).assertIsDisplayed().performClick()
        composeRule.runOnIdle {
            assertEquals(1, startClicks)
        }

        composeRule.runOnIdle {
            modelState.value = MainActivityUiModelTestFactory.base(screen = AppScreen.MENU).withMenuState {
                copy(startEnabled = true)
            }
        }
        composeRule.onNodeWithText(menuTitle).assertIsDisplayed()
        composeRule.onNodeWithText(startSession).assertIsDisplayed().performClick()
        composeRule.runOnIdle {
            assertEquals(2, startClicks)
        }

        composeRule.runOnIdle {
            modelState.value = MainActivityUiModelTestFactory.base(screen = AppScreen.CONNECTING)
        }
        composeRule.onNodeWithText(connecting, substring = true).assertIsDisplayed()
        composeRule.onNodeWithText(connectingHint).assertIsDisplayed()
    }

    @Test
    fun activePickerPermissionDeniedStateSupportsExplicitSearchRetry() {
        val modelState = mutableStateOf(MainActivityUiModelTestFactory.base(screen = AppScreen.MENU))
        var searchTrainerClicks = 0
        val permissionRequired = composeRule.activity.getString(R.string.menu_device_scan_permission_required)
        val scanningStatus = composeRule.activity.getString(R.string.menu_device_scan_status_scanning)
        val scanningStatusBase = normalizedStatusLabel(scanningStatus)
        val searchTrainerLabel = composeRule.activity.getString(R.string.menu_search_trainer_devices_short)

        composeRule.setContent {
            MainActivityContent(
                model = modelState.value,
                onSelectWorkoutFile = {},
                onFtpInputChanged = {},
                onHrProfileAgeInputChanged = {},
                onHrProfileSexSelected = {},
                onSearchFtmsDevices = { searchTrainerClicks += 1 },
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

        openSetupStep(R.string.menu_setup_step_devices_title)
        composeRule.runOnIdle {
            modelState.value = MainActivityUiModelTestFactory.base(screen = AppScreen.MENU).withMenuState {
                copy(
                    activeDeviceSelectionKind = DeviceSelectionKind.FTMS,
                    deviceScanInProgress = false,
                    deviceScanStopEnabled = true,
                    deviceScanStatus = permissionRequired,
                )
            }
        }

        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.menu_trainer_picker_title)
        ).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(permissionRequired).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(searchTrainerLabel).performScrollTo().assertIsDisplayed().performClick()
        composeRule.runOnIdle {
            assertEquals(1, searchTrainerClicks)
        }

        composeRule.runOnIdle {
            modelState.value = MainActivityUiModelTestFactory.base(screen = AppScreen.MENU).withMenuState {
                copy(
                    activeDeviceSelectionKind = DeviceSelectionKind.FTMS,
                    deviceScanInProgress = true,
                    deviceScanStopEnabled = false,
                    deviceScanStatus = scanningStatus,
                )
            }
        }

        composeRule.onNodeWithText(
            scanningStatusBase,
            substring = true,
        ).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.menu_cancel_device_scan)
        ).performScrollTo().assertIsDisplayed().assertIsNotEnabled()
    }

    @Test
    fun connectingTimeoutPromptShowsActionsAndRoutesCallbacks() {
        val modelState = mutableStateOf(MainActivityUiModelTestFactory.base(screen = AppScreen.CONNECTING))
        var keepWaitingClicks = 0
        var retryClicks = 0
        var backToMenuClicks = 0
        val timeoutMessage = composeRule.activity.getString(R.string.connecting_timeout_message)

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
                onConnectingTimeoutKeepWaiting = { keepWaitingClicks += 1 },
                onConnectingTimeoutRetry = { retryClicks += 1 },
                onConnectingTimeoutBackToMenu = { backToMenuClicks += 1 },
                onStartSession = {},
                onEndSession = {},
                onBackToMenu = {},
                onRequestSummaryFitExport = {},
            )
        }

        composeRule.runOnIdle {
            modelState.value = MainActivityUiModelTestFactory.base(screen = AppScreen.CONNECTING).copy(
                connectingTimeoutMessage = timeoutMessage,
            )
        }

        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.connecting_timeout_title),
        ).assertIsDisplayed()
        composeRule.onNodeWithText(timeoutMessage).assertIsDisplayed()
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.connecting_timeout_retry),
        ).assertIsDisplayed().performClick()
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.connecting_timeout_keep_waiting),
        ).assertIsDisplayed().performClick()
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.connecting_timeout_back_to_menu),
        ).assertIsDisplayed().performClick()

        composeRule.runOnIdle {
            assertEquals(1, retryClicks)
            assertEquals(1, keepWaitingClicks)
            assertEquals(1, backToMenuClicks)
        }
    }

    @Test
    fun summaryFitExportFailureKeepsErrorVisibleAndAllowsRetryAction() {
        val exportFailedMessage = composeRule.activity.getString(R.string.summary_fit_export_failed)
        val summaryExportCta = composeRule.activity.getString(R.string.summary_export_fit)
        val modelState = mutableStateOf(
            MainActivityUiModelTestFactory.base(
                screen = AppScreen.SUMMARY,
                summary = SessionSummary(
                    startTimestampMillis = 1_700_000_000_000L,
                    stopTimestampMillis = 1_700_000_120_000L,
                    durationSeconds = 120,
                    actualTss = 3.3,
                    avgPower = 200,
                    maxPower = 260,
                    avgCadence = 85,
                    maxCadence = 95,
                    avgHeartRate = 140,
                    maxHeartRate = 158,
                    distanceMeters = 1000,
                    totalEnergyKcal = 35,
                ),
            )
                .withMenuState { copy(fitExportPreference = FitExportPreference.ASK_EVERY_TIME) }
                .copy(
                    summaryFitExportStatusMessage = exportFailedMessage,
                    summaryFitExportStatusIsError = true,
                )
        )
        var exportClicks = 0

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
                onRequestSummaryFitExport = { exportClicks += 1 },
            )
        }

        dismissSummaryPaywallNudgeIfVisible()
        assertSummaryExportErrorVisible(exportFailedMessage)
        composeRule.onNodeWithText(summaryExportCta).assertIsDisplayed().assertIsEnabled().performClick()
        composeRule.runOnIdle {
            assertEquals(1, exportClicks)
        }
        assertSummaryExportErrorVisible(exportFailedMessage)
    }

    private fun normalizedWaitingStatus(resId: Int): String {
        return composeRule.activity.getString(resId).trimEnd().trimEnd('.', '…')
    }

    private fun normalizedStatusLabel(text: String): String {
        return text.trimEnd().trimEnd('.', '…')
    }

    private fun openSetupStep(stepTitleResId: Int) {
        composeRule.onNodeWithText(
            composeRule.activity.getString(stepTitleResId)
        ).performScrollTo().assertIsDisplayed().performClick()
    }

    private fun dismissSummaryPaywallNudgeIfVisible() {
        val laterLabel = composeRule.activity.getString(R.string.paywall_nudge_cta_later)
        composeRule.waitForIdle()
        runCatching {
            composeRule.onNodeWithText(laterLabel).assertIsDisplayed().performClick()
        }
    }

    private fun assertSummaryExportErrorVisible(message: String) {
        // Summary status is rendered after the metrics section in a LazyColumn, so we must
        // first bring that index into composition before matching the exact status text.
        composeRule.onNode(hasScrollToIndexAction()).performScrollToIndex(3)
        composeRule.onNodeWithText(message).performScrollTo().assertIsDisplayed()
    }

}

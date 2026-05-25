package com.example.ergometerapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionStartEligibilityUiStateTest {

    @Test
    fun statePortAndDeviceSelectionShareSelectionBackingState() {
        val uiState = SessionStartEligibilityUiState()
        val deviceSelectionUiState = DeviceSelectionUiState(
            ftmsSelectedMacState = uiState.ftmsSelection.selectedMacState,
            hrSelectedMacState = uiState.hrSelection.selectedMacState,
        )
        val port = uiState.statePort(
            workoutReadyProvider = { true },
            selectedSessionSetupModeProvider = { SessionSetupMode.FILE },
        )

        deviceSelectionUiState.loadStoredSelections(
            ftmsMac = "AA:BB:CC:DD:EE:FF",
            ftmsName = "Trainer",
            hrMac = "11:22:33:44:55:66",
            hrName = "HR",
        )
        port.mockTrainerModeEnabled = true
        port.selectedFtmsDeviceMac = "FF:EE:DD:CC:BB:AA"
        port.selectedHrDeviceMac = "66:55:44:33:22:11"

        assertTrue(port.workoutReady)
        assertTrue(uiState.mockTrainerModeEnabledState.value)
        assertEquals("FF:EE:DD:CC:BB:AA", deviceSelectionUiState.ftmsDevice.selectedMacState.value)
        assertEquals("66:55:44:33:22:11", deviceSelectionUiState.hrDevice.selectedMacState.value)
    }

    @Test
    fun restoreMockTrainerModeDisablesStoredValueOutsideDebugBuild() {
        val uiState = SessionStartEligibilityUiState()

        uiState.restoreMockTrainerMode(enabled = true, isDebugBuild = false)

        assertFalse(uiState.mockTrainerModeEnabledState.value)
    }

    // region Telemetry-only (3C) state interactions

    @Test
    fun statePortReflectsSelectedSessionSetupMode() {
        val uiState = SessionStartEligibilityUiState()
        val port = uiState.statePort(
            workoutReadyProvider = { false },
            selectedSessionSetupModeProvider = { SessionSetupMode.TELEMETRY_ONLY },
        )

        assertEquals(SessionSetupMode.TELEMETRY_ONLY, port.selectedSessionSetupMode)
        assertFalse(port.workoutReady)
    }

    @Test
    fun statePortDefaultsToFileSetupMode() {
        val uiState = SessionStartEligibilityUiState()
        val port = uiState.statePort(
            workoutReadyProvider = { false },
            selectedSessionSetupModeProvider = { SessionSetupMode.FILE },
        )

        assertEquals(SessionSetupMode.FILE, port.selectedSessionSetupMode)
    }

    // endregion
}

package com.example.ergometerapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class MenuStatusProbeStateAdapterTest {

    @Test
    fun realMenuStatusProbeStatePort_mutatesSharedProbeOwners() {
        val uiState = AppUiState().apply {
            screen.value = AppScreen.MENU
        }
        val deviceSelectionUiState = DeviceSelectionUiState().apply {
            activeSelectionKindState.value = DeviceSelectionKind.FTMS
            scanInProgressState.value = true
        }
        var selectedFtmsMac: String? = "AA:BB:CC:DD:EE:FF"
        var selectedHrMac: String? = "11:22:33:44:55:66"
        val statePort = RealMenuStatusProbeStatePort(
            uiState = uiState,
            deviceSelectionUiState = deviceSelectionUiState,
            currentFtmsDeviceMacProvider = { selectedFtmsMac },
            currentHrDeviceMacProvider = { selectedHrMac },
        )

        assertEquals(AppScreen.MENU, statePort.currentScreen)
        assertEquals(true, statePort.isPickerActiveOrScanInProgress)
        assertEquals("AA:BB:CC:DD:EE:FF", statePort.currentFtmsDeviceMac)
        assertEquals("11:22:33:44:55:66", statePort.currentHrDeviceMac)

        statePort.ftmsReachable = true
        statePort.hrReachable = false
        statePort.hrConsecutiveMisses = 2
        statePort.hrLastSeenElapsedMs = 456L

        assertEquals(true, deviceSelectionUiState.ftmsDevice.reachableState.value)
        assertEquals(false, deviceSelectionUiState.hrDevice.reachableState.value)
        assertEquals(2, deviceSelectionUiState.hrDevice.consecutiveMisses)
        assertEquals(456L, deviceSelectionUiState.hrDevice.lastSeenElapsedMs)

        selectedFtmsMac = null
        selectedHrMac = null
        deviceSelectionUiState.activeSelectionKindState.value = null
        deviceSelectionUiState.scanInProgressState.value = false
        statePort.ftmsReachable = null
        statePort.hrReachable = null
        statePort.hrConsecutiveMisses = 0
        statePort.hrLastSeenElapsedMs = null

        assertNull(statePort.currentFtmsDeviceMac)
        assertNull(statePort.currentHrDeviceMac)
        assertFalse(statePort.isPickerActiveOrScanInProgress)
        assertNull(deviceSelectionUiState.ftmsDevice.reachableState.value)
        assertNull(deviceSelectionUiState.hrDevice.reachableState.value)
        assertEquals(0, deviceSelectionUiState.hrDevice.consecutiveMisses)
        assertNull(deviceSelectionUiState.hrDevice.lastSeenElapsedMs)
    }
}

package com.example.ergometerapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceScanUiStateAdapterTest {

    @Test
    fun realDeviceScanUiPortMutatesSharedPickerStateOwner() {
        val deviceSelectionUiState = DeviceSelectionUiState()
        val uiPort = RealDeviceScanUiPort(
            deviceSelectionUiState = deviceSelectionUiState,
        )
        val scannedDevice = ScannedBleDevice(
            macAddress = "AA:BB:CC:DD:EE:FF",
            displayName = "Trainer",
            rssi = -42,
        )

        uiPort.activeSelectionKind = DeviceSelectionKind.FTMS
        uiPort.scanInProgress = true
        uiPort.scanStatus = "scanning"
        uiPort.stopEnabled = false
        uiPort.scannedDevices += scannedDevice

        assertEquals(DeviceSelectionKind.FTMS, deviceSelectionUiState.activeSelectionKindState.value)
        assertEquals(true, deviceSelectionUiState.scanInProgressState.value)
        assertEquals("scanning", deviceSelectionUiState.scanStatusState.value)
        assertFalse(deviceSelectionUiState.scanStopEnabledState.value)
        assertSame(deviceSelectionUiState.scannedDevicesState, uiPort.scannedDevices)
        assertEquals(listOf(scannedDevice), deviceSelectionUiState.scannedDevicesState)

        deviceSelectionUiState.activeSelectionKindState.value = null
        deviceSelectionUiState.scanInProgressState.value = false
        deviceSelectionUiState.scanStatusState.value = null
        deviceSelectionUiState.scanStopEnabledState.value = true
        deviceSelectionUiState.scannedDevicesState.clear()

        assertNull(uiPort.activeSelectionKind)
        assertEquals(false, uiPort.scanInProgress)
        assertNull(uiPort.scanStatus)
        assertEquals(true, uiPort.stopEnabled)
        assertTrue(uiPort.scannedDevices.isEmpty())
    }
}

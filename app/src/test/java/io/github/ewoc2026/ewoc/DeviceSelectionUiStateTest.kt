package io.github.ewoc2026.ewoc

import androidx.compose.runtime.mutableStateOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceSelectionUiStateTest {

    @Test
    fun applyStatePortAndPreScanStatePortShareBackingState() {
        val heartRateState = mutableStateOf<Int?>(128)
        val uiState = DeviceSelectionUiState()
        val applyStatePort = uiState.applyStatePort()
        val preScanStatePort = uiState.preScanStatePort(heartRateState = heartRateState)

        applyStatePort.selectedFtmsDeviceMac = "AA:BB:CC:DD:EE:FF"
        applyStatePort.ftmsDeviceName = "Trainer"
        applyStatePort.ftmsReachable = true
        applyStatePort.selectedHrDeviceMac = "11:22:33:44:55:66"
        applyStatePort.hrDeviceName = "Strap"
        applyStatePort.hrReachable = false
        applyStatePort.hrConsecutiveMisses = 3
        applyStatePort.hrLastSeenElapsedMs = 456L
        preScanStatePort.hrConnected = false
        preScanStatePort.heartRate = null

        assertEquals("AA:BB:CC:DD:EE:FF", uiState.ftmsDevice.selectedMacState.value)
        assertEquals("Trainer", uiState.ftmsDevice.displayNameState.value)
        assertEquals(true, uiState.ftmsDevice.reachableState.value)
        assertEquals("11:22:33:44:55:66", uiState.hrDevice.selectedMacState.value)
        assertEquals("Strap", uiState.hrDevice.displayNameState.value)
        assertEquals(false, uiState.hrDevice.reachableState.value)
        assertEquals(3, uiState.hrDevice.consecutiveMisses)
        assertEquals(456L, uiState.hrDevice.lastSeenElapsedMs)
        assertFalse(uiState.hrDevice.connectedState.value)
        assertNull(heartRateState.value)
    }
}

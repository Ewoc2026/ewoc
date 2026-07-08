package io.github.ewoc2026.ewoc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceSelectionApplyCoordinatorTest {
    @Test
    fun applyFtmsDeviceSelection_clearSelectionResetsStateAndSkipsProbe() {
        val state = FakeStatePort().apply {
            selectedFtmsDeviceMac = "AA:BB:CC:DD:EE:FF"
            ftmsDeviceName = "Trainer"
            ftmsReachable = true
        }
        val record = SelectionEffectRecord()
        val coordinator = buildCoordinator(state, record)

        coordinator.applyFtmsDeviceSelection(normalizedMac = null, deviceName = null)

        assertEquals(1, record.cancelTrainerStatusProbeCalls)
        assertEquals(0, record.probeTrainerAvailabilityCalls)
        assertEquals(1, record.releaseWarmTrainerConnectionCalls)
        assertEquals(0, record.prepareWarmTrainerConnectionCalls)
        assertEquals(listOf(null), record.savedFtmsMacValues)
        assertEquals(listOf(null), record.savedFtmsNameValues)
        assertEquals(1, record.refreshCalls)
        assertNull(state.selectedFtmsDeviceMac)
        assertEquals("", state.ftmsDeviceName)
        assertNull(state.ftmsReachable)
    }

    @Test
    fun applyFtmsDeviceSelection_applySelectionPersistsAndProbes() {
        val state = FakeStatePort().apply {
            ftmsReachable = false
        }
        val record = SelectionEffectRecord()
        val coordinator = buildCoordinator(state, record)

        coordinator.applyFtmsDeviceSelection(
            normalizedMac = "AA:BB:CC:DD:EE:FF",
            deviceName = "  Kickr Bike  ",
        )

        assertEquals(1, record.cancelTrainerStatusProbeCalls)
        assertEquals(1, record.probeTrainerAvailabilityCalls)
        assertEquals(1, record.releaseWarmTrainerConnectionCalls)
        assertEquals(1, record.prepareWarmTrainerConnectionCalls)
        assertEquals(listOf("AA:BB:CC:DD:EE:FF"), record.savedFtmsMacValues)
        assertEquals(listOf("Kickr Bike"), record.savedFtmsNameValues)
        assertEquals(1, record.refreshCalls)
        assertEquals("AA:BB:CC:DD:EE:FF", state.selectedFtmsDeviceMac)
        assertEquals("Kickr Bike", state.ftmsDeviceName)
        assertNull(state.ftmsReachable)
    }

    @Test
    fun applyHrDeviceSelection_clearSelectionResetsRuntimeStateAndSkipsProbe() {
        val state = FakeStatePort().apply {
            selectedHrDeviceMac = "11:22:33:44:55:66"
            hrDeviceName = "Strap"
            hrReachable = true
            hrConsecutiveMisses = 4
            hrLastSeenElapsedMs = 456L
            hrConnected = true
        }
        val record = SelectionEffectRecord()
        val coordinator = buildCoordinator(state, record)

        coordinator.applyHrDeviceSelection(normalizedMac = null, deviceName = null)

        assertEquals(1, record.cancelHrStatusProbeCalls)
        assertEquals(0, record.probeHrAvailabilityCalls)
        assertEquals(listOf(null), record.savedHrMacValues)
        assertEquals(listOf(null), record.savedHrNameValues)
        assertEquals(1, record.refreshCalls)
        assertNull(state.selectedHrDeviceMac)
        assertEquals("", state.hrDeviceName)
        assertNull(state.hrReachable)
        assertEquals(0, state.hrConsecutiveMisses)
        assertNull(state.hrLastSeenElapsedMs)
        assertEquals(false, state.hrConnected)
    }

    @Test
    fun applyHrDeviceSelection_applySelectionKeepsConnectionFlagAndResetsProbeState() {
        val state = FakeStatePort().apply {
            hrReachable = false
            hrConsecutiveMisses = 2
            hrLastSeenElapsedMs = 200L
            hrConnected = true
        }
        val record = SelectionEffectRecord()
        val coordinator = buildCoordinator(state, record)

        coordinator.applyHrDeviceSelection(
            normalizedMac = "11:22:33:44:55:66",
            deviceName = "   ",
        )

        assertEquals(1, record.cancelHrStatusProbeCalls)
        assertEquals(1, record.probeHrAvailabilityCalls)
        assertEquals(listOf("11:22:33:44:55:66"), record.savedHrMacValues)
        assertEquals(listOf(null), record.savedHrNameValues)
        assertEquals(1, record.refreshCalls)
        assertEquals("11:22:33:44:55:66", state.selectedHrDeviceMac)
        assertEquals("", state.hrDeviceName)
        assertNull(state.hrReachable)
        assertEquals(0, state.hrConsecutiveMisses)
        assertNull(state.hrLastSeenElapsedMs)
        assertEquals(true, state.hrConnected)
    }

    private fun buildCoordinator(
        state: FakeStatePort,
        record: SelectionEffectRecord,
    ): DeviceSelectionApplyCoordinator {
        return DeviceSelectionApplyCoordinator(
            statePort = state,
            saveFtmsDeviceMac = { value -> record.savedFtmsMacValues += value },
            saveFtmsDeviceName = { value -> record.savedFtmsNameValues += value },
            saveHrDeviceMac = { value -> record.savedHrMacValues += value },
            saveHrDeviceName = { value -> record.savedHrNameValues += value },
            releaseWarmTrainerConnection = { record.releaseWarmTrainerConnectionCalls += 1 },
            prepareWarmTrainerConnection = { record.prepareWarmTrainerConnectionCalls += 1 },
            cancelTrainerStatusProbeScan = { record.cancelTrainerStatusProbeCalls += 1 },
            cancelHrStatusProbeScan = { record.cancelHrStatusProbeCalls += 1 },
            probeTrainerAvailabilityNow = { record.probeTrainerAvailabilityCalls += 1 },
            probeHrAvailabilityNow = { record.probeHrAvailabilityCalls += 1 },
            refreshAiAssistantRecommendations = { record.refreshCalls += 1 },
        )
    }

    private class FakeStatePort : DeviceSelectionApplyStatePort {
        override var selectedFtmsDeviceMac: String? = null
        override var ftmsDeviceName: String = ""
        override var ftmsReachable: Boolean? = null
        override var selectedHrDeviceMac: String? = null
        override var hrDeviceName: String = ""
        override var hrReachable: Boolean? = null
        override var hrConsecutiveMisses: Int = 0
        override var hrLastSeenElapsedMs: Long? = null
        override var hrConnected: Boolean = false
    }

    private class SelectionEffectRecord {
        val savedFtmsMacValues = mutableListOf<String?>()
        val savedFtmsNameValues = mutableListOf<String?>()
        val savedHrMacValues = mutableListOf<String?>()
        val savedHrNameValues = mutableListOf<String?>()
        var releaseWarmTrainerConnectionCalls = 0
        var prepareWarmTrainerConnectionCalls = 0
        var cancelTrainerStatusProbeCalls = 0
        var cancelHrStatusProbeCalls = 0
        var probeTrainerAvailabilityCalls = 0
        var probeHrAvailabilityCalls = 0
        var refreshCalls = 0
    }
}

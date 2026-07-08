package io.github.ewoc2026.ewoc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceSelectionPostSelectionCoordinatorTest {
    @Test
    fun onScannedDeviceSelected_ftmsAppliesSelectionThenRunsPostSelectionEffects() {
        val state = FakeStatePort().apply {
            activeSelectionKind = DeviceSelectionKind.FTMS
        }
        val record = PostSelectionEffectRecord()
        val coordinator = buildCoordinator(state, record)

        coordinator.onScannedDeviceSelected(
            ScannedBleDevice(
                macAddress = "aa:bb:cc:dd:ee:ff",
                displayName = "Trainer",
                rssi = -45,
            ),
        )

        assertEquals(
            SelectionPayload(
                normalizedMac = "AA:BB:CC:DD:EE:FF",
                deviceName = "Trainer",
            ),
            record.ftmsSelection,
        )
        assertNull(record.hrSelection)
        assertEquals(
            listOf(
                "apply_ftms",
                "clear_prompt",
                "dismiss_picker",
                "refresh_recommendations",
            ),
            record.events,
        )
    }

    @Test
    fun onScannedDeviceSelected_hrAppliesSelectionThenRunsPostSelectionEffects() {
        val state = FakeStatePort().apply {
            activeSelectionKind = DeviceSelectionKind.HEART_RATE
        }
        val record = PostSelectionEffectRecord()
        val coordinator = buildCoordinator(state, record)

        coordinator.onScannedDeviceSelected(
            ScannedBleDevice(
                macAddress = "11:22:33:44:55:66",
                displayName = "HR Strap",
                rssi = -58,
            ),
        )

        assertNull(record.ftmsSelection)
        assertEquals(
            SelectionPayload(
                normalizedMac = "11:22:33:44:55:66",
                deviceName = "HR Strap",
            ),
            record.hrSelection,
        )
        assertEquals(
            listOf(
                "apply_hr",
                "clear_prompt",
                "dismiss_picker",
                "refresh_recommendations",
            ),
            record.events,
        )
    }

    @Test
    fun onScannedDeviceSelected_noSelectionKindNoops() {
        val state = FakeStatePort().apply {
            activeSelectionKind = null
        }
        val record = PostSelectionEffectRecord()
        val coordinator = buildCoordinator(state, record)

        coordinator.onScannedDeviceSelected(
            ScannedBleDevice(
                macAddress = "AA:BB:CC:DD:EE:FF",
                displayName = "Ignored",
                rssi = -70,
            ),
        )

        assertNull(record.ftmsSelection)
        assertNull(record.hrSelection)
        assertEquals(emptyList<String>(), record.events)
    }

    private fun buildCoordinator(
        state: FakeStatePort,
        record: PostSelectionEffectRecord,
    ): DeviceSelectionPostSelectionCoordinator {
        return DeviceSelectionPostSelectionCoordinator(
            statePort = state,
            applyFtmsSelection = { normalizedMac, deviceName ->
                record.ftmsSelection = SelectionPayload(normalizedMac, deviceName)
                record.events += "apply_ftms"
            },
            applyHrSelection = { normalizedMac, deviceName ->
                record.hrSelection = SelectionPayload(normalizedMac, deviceName)
                record.events += "apply_hr"
            },
            clearConnectionIssuePrompt = { record.events += "clear_prompt" },
            dismissPicker = { record.events += "dismiss_picker" },
            refreshAiAssistantRecommendations = { record.events += "refresh_recommendations" },
        )
    }

    private class FakeStatePort : DeviceSelectionPostSelectionStatePort {
        override var activeSelectionKind: DeviceSelectionKind? = null
    }

    private data class SelectionPayload(
        val normalizedMac: String?,
        val deviceName: String?,
    )

    private class PostSelectionEffectRecord {
        var ftmsSelection: SelectionPayload? = null
        var hrSelection: SelectionPayload? = null
        val events = mutableListOf<String>()
    }
}

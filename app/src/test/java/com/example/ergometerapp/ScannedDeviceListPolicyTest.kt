package com.example.ergometerapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScannedDeviceListPolicyTest {

    @Test
    fun upsertAddsNewDeviceWhenMacDoesNotExist() {
        val devices = mutableListOf<ScannedBleDevice>()

        val changed = ScannedDeviceListPolicy.upsert(
            devices = devices,
            incoming = ScannedBleDevice(
                macAddress = "AA:BB:CC:DD:EE:01",
                displayName = "Trainer",
                rssi = -48,
            ),
        )

        assertTrue(changed)
        assertEquals(1, devices.size)
        assertEquals("AA:BB:CC:DD:EE:01", devices.first().macAddress)
    }

    @Test
    fun upsertUpdatesExistingRssiWhenIncomingSignalIsWeaker() {
        val devices = mutableListOf(
            ScannedBleDevice(
                macAddress = "AA:BB:CC:DD:EE:02",
                displayName = "HR Strap",
                rssi = -55,
            ),
        )

        val changed = ScannedDeviceListPolicy.upsert(
            devices = devices,
            incoming = ScannedBleDevice(
                macAddress = "AA:BB:CC:DD:EE:02",
                displayName = "HR Strap",
                rssi = -70,
            ),
        )

        assertTrue(changed)
        assertEquals(-70, devices.first().rssi)
    }

    @Test
    fun upsertReplacesExistingWhenNameWasMissing() {
        val devices = mutableListOf(
            ScannedBleDevice(
                macAddress = "AA:BB:CC:DD:EE:03",
                displayName = null,
                rssi = -50,
            ),
        )

        val changed = ScannedDeviceListPolicy.upsert(
            devices = devices,
            incoming = ScannedBleDevice(
                macAddress = "AA:BB:CC:DD:EE:03",
                displayName = "Polar H10",
                rssi = -65,
            ),
        )

        assertTrue(changed)
        assertEquals("Polar H10", devices.first().displayName)
        assertEquals(-65, devices.first().rssi)
    }

    @Test
    fun upsertKeepsExistingNameWhenIncomingNameIsBlank() {
        val devices = mutableListOf(
            ScannedBleDevice(
                macAddress = "AA:BB:CC:DD:EE:04",
                displayName = "Known Name",
                rssi = -62,
            ),
        )

        val changed = ScannedDeviceListPolicy.upsert(
            devices = devices,
            incoming = ScannedBleDevice(
                macAddress = "AA:BB:CC:DD:EE:04",
                displayName = "",
                rssi = -61,
            ),
        )

        assertTrue(changed)
        assertEquals("Known Name", devices.first().displayName)
        assertEquals(-61, devices.first().rssi)
    }

    @Test
    fun sortedBySignalOrdersDescendingByRssi() {
        val sorted = ScannedDeviceListPolicy.sortedBySignal(
            listOf(
                ScannedBleDevice(macAddress = "A", displayName = "A", rssi = -80),
                ScannedBleDevice(macAddress = "B", displayName = "B", rssi = -50),
                ScannedBleDevice(macAddress = "C", displayName = "C", rssi = -65),
            ),
        )

        assertEquals(listOf("B", "C", "A"), sorted.map { it.macAddress })
    }
}

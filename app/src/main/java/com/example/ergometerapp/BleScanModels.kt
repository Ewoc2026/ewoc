package com.example.ergometerapp

/**
 * Device category used by the in-app BLE picker.
 */
enum class DeviceSelectionKind {
    FTMS,
    HEART_RATE,
}

/**
 * UI-level BLE scan result item.
 */
data class ScannedBleDevice(
    val macAddress: String,
    val displayName: String?,
    val rssi: Int,
)

/**
 * Pure update/sort policy for picker scan results.
 *
 * Keeping list mutation rules isolated makes scanner ordering behavior easy to
 * regression-test without Android BLE callbacks.
 */
internal object ScannedDeviceListPolicy {

    /**
     * Upserts [incoming] into [devices] and returns true when list content changed.
     *
     * Invariant: for the same MAC we keep the strongest RSSI seen, and we only
     * replace device name when the existing row has no usable name.
     */
    fun upsert(devices: MutableList<ScannedBleDevice>, incoming: ScannedBleDevice): Boolean {
        val existingIndex = devices.indexOfFirst { it.macAddress == incoming.macAddress }
        if (existingIndex < 0) {
            devices.add(incoming)
            return true
        }

        val existing = devices[existingIndex]
        val mergedName = if (incoming.displayName.isNullOrBlank()) {
            existing.displayName
        } else {
            incoming.displayName
        }
        val merged = existing.copy(
            displayName = mergedName,
            rssi = incoming.rssi,
        )
        if (merged == existing) {
            return false
        }
        devices[existingIndex] = merged
        return true
    }

    /**
     * Returns a new list sorted by descending RSSI.
     */
    fun sortedBySignal(devices: List<ScannedBleDevice>): List<ScannedBleDevice> {
        return devices.sortedByDescending { it.rssi }
    }
}

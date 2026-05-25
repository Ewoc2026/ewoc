package com.example.ergometerapp

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf

/**
 * Owns picker-visible device-selection state and the closely related runtime fields.
 *
 * Keeping this cluster behind one owner makes the selection flow easier to extract
 * from `MainViewModel` without changing callback ordering or BLE/session ownership.
 * The selected MAC values and HR probe counters stay here because the existing
 * coordinators mutate them together with the visible picker state to preserve the
 * current invariants.
 */
internal class DeviceSelectionUiState(
    ftmsSelectedMacState: MutableState<String?> = mutableStateOf(null),
    hrSelectedMacState: MutableState<String?> = mutableStateOf(null),
) {
    val ftmsDevice = SelectedDeviceUiState(selectedMacState = ftmsSelectedMacState)
    val hrDevice = HeartRateSelectedDeviceUiState(selectedMacState = hrSelectedMacState)
    val activeSelectionKindState = mutableStateOf<DeviceSelectionKind?>(null)
    val scannedDevicesState = mutableStateListOf<ScannedBleDevice>()
    val scanInProgressState = mutableStateOf(false)
    val scanStatusState = mutableStateOf<String?>(null)
    val scanStopEnabledState = mutableStateOf(true)

    val isPickerActiveOrScanInProgress: Boolean
        get() = activeSelectionKindState.value != null || scanInProgressState.value

    fun loadStoredSelections(
        ftmsMac: String?,
        ftmsName: String,
        hrMac: String?,
        hrName: String,
    ) {
        ftmsDevice.selectedMacState.value = ftmsMac
        ftmsDevice.displayNameState.value = ftmsName
        hrDevice.selectedMacState.value = hrMac
        hrDevice.displayNameState.value = hrName
    }

    fun resetTransientConnectionState() {
        ftmsDevice.reachableState.value = null
        hrDevice.reachableState.value = null
        hrDevice.connectedState.value = false
        hrDevice.permissionDeniedState.value = false
        hrDevice.consecutiveMisses = 0
        hrDevice.lastSeenElapsedMs = null
    }

    fun applyStatePort(): DeviceSelectionApplyStatePort {
        return object : DeviceSelectionApplyStatePort {
            override var selectedFtmsDeviceMac: String?
                get() = ftmsDevice.selectedMacState.value
                set(value) {
                    ftmsDevice.selectedMacState.value = value
                }

            override var ftmsDeviceName: String
                get() = ftmsDevice.displayNameState.value
                set(value) {
                    ftmsDevice.displayNameState.value = value
                }

            override var ftmsReachable: Boolean?
                get() = ftmsDevice.reachableState.value
                set(value) {
                    ftmsDevice.reachableState.value = value
                }

            override var selectedHrDeviceMac: String?
                get() = hrDevice.selectedMacState.value
                set(value) {
                    hrDevice.selectedMacState.value = value
                }

            override var hrDeviceName: String
                get() = hrDevice.displayNameState.value
                set(value) {
                    hrDevice.displayNameState.value = value
                }

            override var hrReachable: Boolean?
                get() = hrDevice.reachableState.value
                set(value) {
                    hrDevice.reachableState.value = value
                }

            override var hrConsecutiveMisses: Int
                get() = hrDevice.consecutiveMisses
                set(value) {
                    hrDevice.consecutiveMisses = value
                }

            override var hrLastSeenElapsedMs: Long?
                get() = hrDevice.lastSeenElapsedMs
                set(value) {
                    hrDevice.lastSeenElapsedMs = value
                }

            override var hrConnected: Boolean
                get() = hrDevice.connectedState.value
                set(value) {
                    hrDevice.connectedState.value = value
                }
        }
    }

    fun preScanStatePort(heartRateState: MutableState<Int?>): DeviceSelectionPreScanStatePort {
        return object : DeviceSelectionPreScanStatePort {
            override var hrConnected: Boolean
                get() = hrDevice.connectedState.value
                set(value) {
                    hrDevice.connectedState.value = value
                }

            override var heartRate: Int?
                get() = heartRateState.value
                set(value) {
                    heartRateState.value = value
                }
        }
    }
}

/**
 * Holds the selected device identity and the picker-facing availability summary
 * for one BLE device role.
 */
internal open class SelectedDeviceUiState(
    val selectedMacState: MutableState<String?> = mutableStateOf(null),
) {
    val displayNameState = mutableStateOf("")
    val reachableState = mutableStateOf<Boolean?>(null)
}

/**
 * Extends the generic device state with live HR transport/probe details.
 *
 * These fields stay outside Compose state because they are operational counters
 * used by probe/reconnect logic rather than independently rendered UI values.
 */
internal class HeartRateSelectedDeviceUiState(
    selectedMacState: MutableState<String?> = mutableStateOf(null),
) : SelectedDeviceUiState(selectedMacState = selectedMacState) {
    val connectedState = mutableStateOf(false)
    val permissionDeniedState = mutableStateOf(false)
    var consecutiveMisses: Int = 0
    var lastSeenElapsedMs: Long? = null
}

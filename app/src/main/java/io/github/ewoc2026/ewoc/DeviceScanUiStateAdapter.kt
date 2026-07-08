package io.github.ewoc2026.ewoc

/**
 * Concrete [DeviceScanUiPort] backed by the existing device-selection owner.
 *
 * Why:
 * - Picker scan lifecycle needs a narrow mutable view of the same
 *   `DeviceSelectionUiState` fields that Compose and post-selection flows already
 *   observe.
 * - Keeping that bridge explicit prevents `MainViewModel` from rebuilding another
 *   anonymous scan-state adapter inline as Phase 2 extraction continues.
 */
internal class RealDeviceScanUiPort(
    private val deviceSelectionUiState: DeviceSelectionUiState,
) : DeviceScanUiPort {
    override var activeSelectionKind: DeviceSelectionKind?
        get() = deviceSelectionUiState.activeSelectionKindState.value
        set(value) {
            deviceSelectionUiState.activeSelectionKindState.value = value
        }

    override var scanInProgress: Boolean
        get() = deviceSelectionUiState.scanInProgressState.value
        set(value) {
            deviceSelectionUiState.scanInProgressState.value = value
        }

    override var scanStatus: String?
        get() = deviceSelectionUiState.scanStatusState.value
        set(value) {
            deviceSelectionUiState.scanStatusState.value = value
        }

    override var stopEnabled: Boolean
        get() = deviceSelectionUiState.scanStopEnabledState.value
        set(value) {
            deviceSelectionUiState.scanStopEnabledState.value = value
        }

    override val scannedDevices: MutableList<ScannedBleDevice>
        get() = deviceSelectionUiState.scannedDevicesState
}

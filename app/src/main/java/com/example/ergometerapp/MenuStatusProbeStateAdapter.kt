package com.example.ergometerapp

/**
 * Concrete [MenuStatusProbeStatePort] backed by the existing app/device-selection owners.
 *
 * Why:
 * - MENU status probing spans `AppUiState`, `DeviceSelectionUiState`, and the persisted
 *   session-start selections, so this adapter keeps that cross-owner contract explicit.
 * - The adapter preserves the coordinator's narrow view of mutable probe state without
 *   rebuilding another anonymous bridge inside `MainViewModel`.
 */
internal class RealMenuStatusProbeStatePort(
    private val uiState: AppUiState,
    private val deviceSelectionUiState: DeviceSelectionUiState,
    private val currentFtmsDeviceMacProvider: () -> String?,
    private val currentHrDeviceMacProvider: () -> String?,
) : MenuStatusProbeStatePort {
    override val currentScreen: AppScreen
        get() = uiState.screen.value

    override val isPickerActiveOrScanInProgress: Boolean
        get() = deviceSelectionUiState.isPickerActiveOrScanInProgress

    override val currentFtmsDeviceMac: String?
        get() = currentFtmsDeviceMacProvider()

    override val currentHrDeviceMac: String?
        get() = currentHrDeviceMacProvider()

    override var ftmsReachable: Boolean?
        get() = deviceSelectionUiState.ftmsDevice.reachableState.value
        set(value) {
            deviceSelectionUiState.ftmsDevice.reachableState.value = value
        }

    override var hrReachable: Boolean?
        get() = deviceSelectionUiState.hrDevice.reachableState.value
        set(value) {
            deviceSelectionUiState.hrDevice.reachableState.value = value
        }

    override var hrConsecutiveMisses: Int
        get() = deviceSelectionUiState.hrDevice.consecutiveMisses
        set(value) {
            deviceSelectionUiState.hrDevice.consecutiveMisses = value
        }

    override var hrLastSeenElapsedMs: Long?
        get() = deviceSelectionUiState.hrDevice.lastSeenElapsedMs
        set(value) {
            deviceSelectionUiState.hrDevice.lastSeenElapsedMs = value
        }
}

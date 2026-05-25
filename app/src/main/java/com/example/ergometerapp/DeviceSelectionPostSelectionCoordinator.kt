package com.example.ergometerapp

/**
 * State bridge consumed by [DeviceSelectionPostSelectionCoordinator].
 *
 * The coordinator only reads selection intent and delegates effects through
 * callbacks so post-selection behavior stays unit-testable without scanner/UI
 * runtime dependencies.
 */
internal interface DeviceSelectionPostSelectionStatePort {
    val activeSelectionKind: DeviceSelectionKind?
}

/**
 * Coordinates side effects after the user selects a scanned picker device.
 *
 * Invariants:
 * - Missing active picker selection kind is a no-op guard.
 * - Selection apply always runs before prompt clear/dismiss/refresh effects.
 * - Prompt clear, picker dismiss, and recommendation refresh keep ordering.
 */
internal class DeviceSelectionPostSelectionCoordinator(
    private val statePort: DeviceSelectionPostSelectionStatePort,
    private val applyFtmsSelection: (normalizedMac: String?, deviceName: String?) -> Unit,
    private val applyHrSelection: (normalizedMac: String?, deviceName: String?) -> Unit,
    private val clearConnectionIssuePrompt: () -> Unit,
    private val dismissPicker: () -> Unit,
    private val refreshAiAssistantRecommendations: () -> Unit,
) {
    fun onScannedDeviceSelected(device: ScannedBleDevice) {
        when (statePort.activeSelectionKind) {
            DeviceSelectionKind.FTMS -> applyFtmsSelection(
                BluetoothMacAddress.normalizeOrNull(device.macAddress),
                device.displayName,
            )

            DeviceSelectionKind.HEART_RATE -> applyHrSelection(
                BluetoothMacAddress.normalizeOrNull(device.macAddress),
                device.displayName,
            )

            null -> return
        }
        clearConnectionIssuePrompt()
        dismissPicker()
        refreshAiAssistantRecommendations()
    }
}

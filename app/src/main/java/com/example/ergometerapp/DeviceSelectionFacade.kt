package com.example.ergometerapp

import android.os.Handler
import java.util.UUID

/**
 * Owns menu device-picker actions so ViewModel entrypoints can delegate
 * selection flow behavior through one seam.
 */
internal interface DeviceSelectionFacade {
    fun requestFtmsScan()

    fun requestHrScan()

    fun onBluetoothScanPermissionResult(granted: Boolean): ScanPermissionResult

    fun dismissSelection()

    fun onScannedDeviceSelected(device: ScannedBleDevice)

    fun close()
}

internal class RealDeviceSelectionFacade(
    uiPort: DeviceScanUiPort,
    scanEngine: DeviceScanEngine,
    handler: Handler,
    messages: DeviceScanMessages,
    ftmsServiceUuid: UUID,
    hrServiceUuid: UUID,
    pickerScanMode: Int,
    pickerScanRetryDelayMs: Long,
    pickerStopButtonLockDurationMs: Long,
    scannedDeviceSortThrottleMs: Long,
    ensureBluetoothScanPermission: () -> Boolean,
    onBeforeScanRequest: (DeviceSelectionKind) -> Unit,
    onBeforeScanStart: () -> Unit,
    onAfterPickerDismissed: () -> Unit,
    private val applyFtmsSelection: (normalizedMac: String?, deviceName: String?) -> Unit,
    private val applyHrSelection: (normalizedMac: String?, deviceName: String?) -> Unit,
    private val clearConnectionIssuePrompt: () -> Unit,
    private val refreshAiAssistantRecommendations: () -> Unit,
) : DeviceSelectionFacade {
    private val coordinatorUiPort: DeviceScanUiPort = uiPort

    private val coordinator = DeviceScanCoordinator(
        uiPort = coordinatorUiPort,
        scanEngine = scanEngine,
        handler = handler,
        messages = messages,
        ftmsServiceUuid = ftmsServiceUuid,
        hrServiceUuid = hrServiceUuid,
        pickerScanMode = pickerScanMode,
        pickerScanRetryDelayMs = pickerScanRetryDelayMs,
        pickerStopButtonLockDurationMs = pickerStopButtonLockDurationMs,
        scannedDeviceSortThrottleMs = scannedDeviceSortThrottleMs,
        ensureBluetoothScanPermission = ensureBluetoothScanPermission,
        onBeforeScanRequest = onBeforeScanRequest,
        onBeforeScanStart = onBeforeScanStart,
        onAfterPickerDismissed = onAfterPickerDismissed,
    )
    private val postSelectionStatePort = object : DeviceSelectionPostSelectionStatePort {
        override val activeSelectionKind: DeviceSelectionKind?
            get() = coordinatorUiPort.activeSelectionKind
    }
    private val postSelectionCoordinator = DeviceSelectionPostSelectionCoordinator(
        statePort = postSelectionStatePort,
        applyFtmsSelection = applyFtmsSelection,
        applyHrSelection = applyHrSelection,
        clearConnectionIssuePrompt = clearConnectionIssuePrompt,
        dismissPicker = coordinator::dismissSelection,
        refreshAiAssistantRecommendations = refreshAiAssistantRecommendations,
    )

    override fun requestFtmsScan() {
        coordinator.requestScan(DeviceSelectionKind.FTMS)
    }

    override fun requestHrScan() {
        coordinator.requestScan(DeviceSelectionKind.HEART_RATE)
    }

    override fun onBluetoothScanPermissionResult(granted: Boolean): ScanPermissionResult {
        return coordinator.onBluetoothScanPermissionResult(granted)
    }

    override fun dismissSelection() {
        coordinator.dismissSelection()
    }

    override fun onScannedDeviceSelected(device: ScannedBleDevice) {
        postSelectionCoordinator.onScannedDeviceSelected(device)
    }

    override fun close() {
        coordinator.close()
    }
}

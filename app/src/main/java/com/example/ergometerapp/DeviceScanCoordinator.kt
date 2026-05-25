package com.example.ergometerapp

import android.os.Handler
import java.util.UUID

/**
 * UI-facing state port used by [DeviceScanCoordinator].
 *
 * Keeping scan state writes behind this port lets the coordinator remain
 * unit-testable without Compose runtime dependencies.
 */
internal interface DeviceScanUiPort {
    var activeSelectionKind: DeviceSelectionKind?
    var scanInProgress: Boolean
    var scanStatus: String?
    var stopEnabled: Boolean
    val scannedDevices: MutableList<ScannedBleDevice>
}

/**
 * Minimal scanner abstraction used by [DeviceScanCoordinator].
 */
internal interface DeviceScanEngine {
    fun start(
        serviceUuid: UUID,
        scanMode: Int,
        onDeviceFound: (ScannedBleDevice) -> Unit,
        onFinished: (String?) -> Unit,
    ): Boolean

    fun stop()
}

/**
 * Adapter that exposes [com.example.ergometerapp.ble.BleDeviceScanner] via
 * [DeviceScanEngine].
 */
internal class BleDeviceScanEngine(
    private val scanner: com.example.ergometerapp.ble.BleDeviceScanner,
) : DeviceScanEngine {
    override fun start(
        serviceUuid: UUID,
        scanMode: Int,
        onDeviceFound: (ScannedBleDevice) -> Unit,
        onFinished: (String?) -> Unit,
    ): Boolean {
        return scanner.start(
            serviceUuid = serviceUuid,
            scanMode = scanMode,
            onDeviceFound = onDeviceFound,
            onFinished = onFinished,
        )
    }

    override fun stop() {
        scanner.stop()
    }
}

/**
 * String provider for user-visible scan statuses.
 */
internal data class DeviceScanMessages(
    val scanning: () -> String,
    val retrying: () -> String,
    val noResults: () -> String,
    val done: (Int) -> String,
    val failed: () -> String,
    val permissionRequired: () -> String,
)

/**
 * Result of resuming a pending picker flow after BLUETOOTH_SCAN permission.
 */
internal enum class ScanPermissionResult {
    STARTED_PENDING_SCAN,
    DENIED,
    GRANTED_NO_PENDING,
}

/**
 * Owns BLE picker scan lifecycle (request/start/retry/stop/sort) for ViewModel.
 *
 * Invariants:
 * - At most one picker scan is active.
 * - Automatic retry runs at most once and only for scanner too-frequent errors.
 * - Stop button is temporarily locked right after scan start to avoid immediate
 *   restart loops that trigger Android scanner throttling.
 */
internal class DeviceScanCoordinator(
    private val uiPort: DeviceScanUiPort,
    private val scanEngine: DeviceScanEngine,
    private val handler: Handler,
    private val messages: DeviceScanMessages,
    private val ftmsServiceUuid: UUID,
    private val hrServiceUuid: UUID,
    private val pickerScanMode: Int,
    private val pickerScanRetryDelayMs: Long,
    private val pickerStopButtonLockDurationMs: Long,
    private val scannedDeviceSortThrottleMs: Long,
    private val ensureBluetoothScanPermission: () -> Boolean,
    private val onBeforeScanRequest: (DeviceSelectionKind) -> Unit,
    private val onBeforeScanStart: () -> Unit,
    private val onAfterPickerDismissed: () -> Unit,
) {
    private var pendingScanKind: DeviceSelectionKind? = null
    private var pendingScanRetry: Runnable? = null
    private var pendingStopUnlock: Runnable? = null
    private var pendingBatchFlush: Runnable? = null
    private val pendingDevicesByMac = linkedMapOf<String, ScannedBleDevice>()

    fun requestScan(kind: DeviceSelectionKind) {
        cancelPendingRetry()
        cancelPendingStopUnlock()
        cancelPendingBatchFlush()
        clearPendingDevices()
        onBeforeScanRequest(kind)
        pendingScanKind = kind
        val granted = ensureBluetoothScanPermission()
        if (!granted) {
            return
        }
        pendingScanKind = null
        startScan(kind)
    }

    fun onBluetoothScanPermissionResult(granted: Boolean): ScanPermissionResult {
        val pendingKind = pendingScanKind
        pendingScanKind = null
        if (!granted) {
            uiPort.scanStatus = messages.permissionRequired()
            uiPort.scanInProgress = false
            return ScanPermissionResult.DENIED
        }
        if (pendingKind == null) {
            return ScanPermissionResult.GRANTED_NO_PENDING
        }
        startScan(pendingKind)
        return ScanPermissionResult.STARTED_PENDING_SCAN
    }

    fun dismissSelection() {
        cancelPendingRetry()
        cancelPendingStopUnlock()
        cancelPendingBatchFlush()
        clearPendingDevices()
        scanEngine.stop()
        uiPort.activeSelectionKind = null
        uiPort.scanInProgress = false
        uiPort.scanStatus = null
        uiPort.stopEnabled = true
        uiPort.scannedDevices.clear()
        pendingScanKind = null
        onAfterPickerDismissed()
    }

    fun close() {
        cancelPendingRetry()
        cancelPendingStopUnlock()
        cancelPendingBatchFlush()
        clearPendingDevices()
        scanEngine.stop()
        pendingScanKind = null
    }

    private fun startScan(kind: DeviceSelectionKind, allowRetryOnTooFrequent: Boolean = true) {
        cancelPendingRetry()
        cancelPendingStopUnlock()
        cancelPendingBatchFlush()
        clearPendingDevices()
        onBeforeScanStart()
        scanEngine.stop()
        uiPort.activeSelectionKind = kind
        uiPort.scannedDevices.clear()
        uiPort.scanInProgress = true
        uiPort.scanStatus = messages.scanning()
        lockStopButton()

        val targetServiceUuid = when (kind) {
            DeviceSelectionKind.FTMS -> ftmsServiceUuid
            DeviceSelectionKind.HEART_RATE -> hrServiceUuid
        }

        val started = scanEngine.start(
            serviceUuid = targetServiceUuid,
            scanMode = pickerScanMode,
            onDeviceFound = { device ->
                addOrUpdateScannedDevice(device)
            },
            onFinished = onFinished@{ errorMessage ->
                val shouldRetry = DeviceScanPolicy.shouldRetryTooFrequent(
                    allowRetryOnTooFrequent = allowRetryOnTooFrequent,
                    errorMessage = errorMessage,
                    isSelectionStillActive = uiPort.activeSelectionKind == kind,
                )
                if (shouldRetry) {
                    uiPort.scanInProgress = true
                    uiPort.scanStatus = messages.retrying()
                    lockStopButton()
                    val retry = Runnable {
                        pendingScanRetry = null
                        if (uiPort.activeSelectionKind != kind) return@Runnable
                        startScan(kind, allowRetryOnTooFrequent = false)
                    }
                    pendingScanRetry = retry
                    handler.postDelayed(retry, pickerScanRetryDelayMs)
                    return@onFinished
                }

                uiPort.scanInProgress = false
                uiPort.stopEnabled = true
                cancelPendingStopUnlock()
                flushPendingBatch()
                val completion = DeviceScanPolicy.classifyCompletion(
                    errorMessage = errorMessage,
                    resultCount = uiPort.scannedDevices.size,
                )
                uiPort.scanStatus =
                    when (completion) {
                        DeviceScanPolicy.Completion.ERROR -> errorMessage
                        DeviceScanPolicy.Completion.NO_RESULTS -> messages.noResults()
                        DeviceScanPolicy.Completion.DONE -> messages.done(uiPort.scannedDevices.size)
                    }
            },
        )

        if (!started) {
            uiPort.scanInProgress = false
            uiPort.stopEnabled = true
            cancelPendingStopUnlock()
            clearPendingDevices()
            if (uiPort.scanStatus == null) {
                uiPort.scanStatus = messages.failed()
            }
        }
    }

    private fun lockStopButton() {
        uiPort.stopEnabled = false
        cancelPendingStopUnlock()
        val unlock = Runnable {
            pendingStopUnlock = null
            if (!uiPort.scanInProgress) return@Runnable
            uiPort.stopEnabled = true
        }
        pendingStopUnlock = unlock
        handler.postDelayed(unlock, pickerStopButtonLockDurationMs)
    }

    private fun cancelPendingStopUnlock() {
        pendingStopUnlock?.let { handler.removeCallbacks(it) }
        pendingStopUnlock = null
    }

    private fun scheduleBatchFlush() {
        if (pendingBatchFlush != null) return
        val flush = Runnable {
            pendingBatchFlush = null
            applyPendingBatchToUi()
        }
        pendingBatchFlush = flush
        handler.postDelayed(flush, scannedDeviceSortThrottleMs)
    }

    private fun flushPendingBatch() {
        val pending = pendingBatchFlush ?: run {
            applyPendingBatchToUi()
            return
        }
        handler.removeCallbacks(pending)
        pendingBatchFlush = null
        applyPendingBatchToUi()
    }

    private fun cancelPendingBatchFlush() {
        pendingBatchFlush?.let { handler.removeCallbacks(it) }
        pendingBatchFlush = null
    }

    private fun applyPendingBatchToUi() {
        if (pendingDevicesByMac.isEmpty()) return
        val nextDevices = uiPort.scannedDevices.toMutableList()
        var changed = false
        pendingDevicesByMac.values.forEach { device ->
            val updated = ScannedDeviceListPolicy.upsert(
                devices = nextDevices,
                incoming = device,
            )
            changed = changed || updated
        }
        clearPendingDevices()
        if (!changed) return

        val sorted = if (nextDevices.size <= 1) {
            nextDevices
        } else {
            ScannedDeviceListPolicy.sortedBySignal(nextDevices)
        }
        if (uiPort.scannedDevices == sorted) return
        uiPort.scannedDevices.clear()
        uiPort.scannedDevices.addAll(sorted)
    }

    private fun addOrUpdateScannedDevice(device: ScannedBleDevice) {
        if (uiPort.scanInProgress) {
            uiPort.stopEnabled = true
            cancelPendingStopUnlock()
        }
        val existing = pendingDevicesByMac[device.macAddress]
        val merged = if (existing == null) {
            device
        } else {
            val mergedName = if (device.displayName.isNullOrBlank()) {
                existing.displayName
            } else {
                device.displayName
            }
            existing.copy(
                displayName = mergedName,
                rssi = device.rssi,
            )
        }
        if (existing != merged) {
            pendingDevicesByMac[device.macAddress] = merged
            scheduleBatchFlush()
        }
    }

    private fun clearPendingDevices() {
        pendingDevicesByMac.clear()
    }

    private fun cancelPendingRetry() {
        pendingScanRetry?.let { handler.removeCallbacks(it) }
        pendingScanRetry = null
    }
}

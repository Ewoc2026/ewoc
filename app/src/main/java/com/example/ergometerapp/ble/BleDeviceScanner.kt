package com.example.ergometerapp.ble

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.os.SystemClock
import android.util.Log
import com.example.ergometerapp.ScannedBleDevice
import java.util.UUID

/**
 * Performs short BLE scans filtered by service UUID for trainer/HR selection.
 *
 * The scanner owns a single in-flight scan to avoid overlapping callbacks and
 * duplicated device lists in UI.
 */
class BleDeviceScanner(
    private val context: Context,
    private val scannerLabel: String = "scanner",
) {
    companion object {
        private const val JOURNAL_CAPACITY = 160
        private val scanRateLock = Any()
        private val journalLock = Any()
        private val lowLatencyStartGate = LowLatencyScanStartGate()
        private val scanJournal = ArrayDeque<String>(JOURNAL_CAPACITY)

        private fun appendJournal(line: String) {
            synchronized(journalLock) {
                if (scanJournal.size >= JOURNAL_CAPACITY) {
                    scanJournal.removeFirst()
                }
                scanJournal.addLast(line)
            }
        }

        private fun dumpJournal(tag: String, reason: String) {
            val snapshot = synchronized(journalLock) { scanJournal.toList() }
            Log.w(tag, "Scan journal dump ($reason), entries=${snapshot.size}")
            snapshot.forEach { entry ->
                Log.w(tag, "JOURNAL $entry")
            }
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var scanner: android.bluetooth.le.BluetoothLeScanner? = null
    private var callback: ScanCallback? = null
    private var stopRunnable: Runnable? = null
    private var pendingStartRunnable: Runnable? = null

    /**
     * Captures the effective delay for a low-latency scan start.
     *
     * [reason] is logged for diagnostics and helps separate cooldown, explicit
     * backoff, and global scan-start window throttling.
     */
    private data class StartDelay(
        val delayMs: Long,
        val reason: String? = null,
    )

    /**
     * Starts a fresh scan for [serviceUuid] and emits discovered devices.
     *
     * [scanMode] defaults to low-latency for interactive picker flows. Callers
     * can pass a lower-power mode for passive availability probes.
     *
     * Returns false when scanning cannot start.
     */
    fun start(
        serviceUuid: UUID,
        durationMs: Long = 10000L,
        scanMode: Int = ScanSettings.SCAN_MODE_LOW_LATENCY,
        onDeviceFound: (ScannedBleDevice) -> Unit,
        onFinished: (String?) -> Unit,
    ): Boolean {
        journal(
            event = "start_requested",
            serviceUuid = serviceUuid,
            scanMode = scanMode,
            detail = "durationMs=$durationMs",
        )
        val hadActiveOrPending = clearScanState(stopNativeScan = true)
        if (hadActiveOrPending) {
            markGlobalScanStopNow()
            journal(
                event = "reset_previous_scan",
                serviceUuid = serviceUuid,
                scanMode = scanMode,
                detail = "activeOrPending=true",
            )
        }

        if (!hasBluetoothScanPermission()) {
            journal(
                event = "start_rejected",
                serviceUuid = serviceUuid,
                scanMode = scanMode,
                detail = "missing_scan_permission",
            )
            onFinished("Missing BLUETOOTH_SCAN permission.")
            return false
        }

        val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
        val adapter = bluetoothManager?.adapter
        if (adapter == null || !adapter.isEnabled) {
            journal(
                event = "start_rejected",
                serviceUuid = serviceUuid,
                scanMode = scanMode,
                detail = "bluetooth_unavailable_or_disabled",
            )
            onFinished("Bluetooth is unavailable or disabled.")
            return false
        }

        val leScanner = adapter.bluetoothLeScanner
        if (leScanner == null) {
            journal(
                event = "start_rejected",
                serviceUuid = serviceUuid,
                scanMode = scanMode,
                detail = "ble_scanner_unavailable",
            )
            onFinished("BLE scanner is unavailable on this device.")
            return false
        }

        val filters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(serviceUuid))
                .build(),
        )
        val settings = ScanSettings.Builder()
            .setScanMode(scanMode)
            .build()

        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                emitDevice(result, onDeviceFound)
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { emitDevice(it, onDeviceFound) }
            }

            override fun onScanFailed(errorCode: Int) {
                clearScanState(stopNativeScan = false)
                markGlobalScanStopNow()
                if (errorCode == ScanCallback.SCAN_FAILED_SCANNING_TOO_FREQUENTLY) {
                    markGlobalThrottleBackoff()
                    journal(
                        event = "scan_failed",
                        serviceUuid = serviceUuid,
                        scanMode = scanMode,
                        detail = "errorCode=$errorCode throttle_backoff_applied=true",
                    )
                    dumpJournal(
                        tag = "BLE_SCAN",
                        reason = "status=6 source=$scannerLabel mode=${scanModeToLabel(scanMode)}",
                    )
                } else {
                    journal(
                        event = "scan_failed",
                        serviceUuid = serviceUuid,
                        scanMode = scanMode,
                        detail = "errorCode=$errorCode",
                    )
                }
                mainHandler.post { onFinished("Scan failed (code=$errorCode).") }
            }
        }

        val startDelay = restartDelayForScanMode(scanMode)
        if (startDelay.delayMs > 0L) {
            journal(
                event = "start_delayed",
                serviceUuid = serviceUuid,
                scanMode = scanMode,
                detail = "delayMs=${startDelay.delayMs} reason=${startDelay.reason ?: "unknown"}",
            )
        }
        val startRunnable = Runnable {
            pendingStartRunnable = null
            if (!hasBluetoothScanPermission()) {
                journal(
                    event = "start_aborted",
                    serviceUuid = serviceUuid,
                    scanMode = scanMode,
                    detail = "permission_revoked_before_start",
                )
                onFinished("Missing BLUETOOTH_SCAN permission.")
                return@Runnable
            }
            journal(
                event = "start_attempt",
                serviceUuid = serviceUuid,
                scanMode = scanMode,
                detail = "durationMs=$durationMs",
            )
            startNativeScan(
                leScanner = leScanner,
                filters = filters,
                settings = settings,
                scanCallback = scanCallback,
                durationMs = durationMs,
                serviceUuid = serviceUuid,
                scanMode = scanMode,
                onFinished = onFinished,
            )
        }

        if (startDelay.delayMs <= 0L) {
            startRunnable.run()
            return scanner != null && callback != null
        }

        pendingStartRunnable = startRunnable
        mainHandler.postDelayed(startRunnable, startDelay.delayMs)
        return true
    }

    /**
     * Stops current scan, if any.
     */
    fun stop() {
        val hadActiveOrPending = clearScanState(stopNativeScan = true)
        journal(
            event = "stop_called",
            detail = "activeOrPending=$hadActiveOrPending",
        )
        if (hadActiveOrPending) {
            markGlobalScanStopNow()
        }
    }

    private fun restartDelayForScanMode(scanMode: Int): StartDelay {
        val now = SystemClock.elapsedRealtime()
        val gateDelay = synchronized(scanRateLock) {
            lowLatencyStartGate.computeDelay(
                nowElapsedMs = now,
                scanMode = scanMode,
            )
        }
        return StartDelay(
            delayMs = gateDelay.delayMs,
            reason = gateDelay.reason?.key,
        )
    }

    private fun startNativeScan(
        leScanner: android.bluetooth.le.BluetoothLeScanner,
        filters: List<ScanFilter>,
        settings: ScanSettings,
        scanCallback: ScanCallback,
        durationMs: Long,
        serviceUuid: UUID,
        scanMode: Int,
        onFinished: (String?) -> Unit,
    ) {
        try {
            leScanner.startScan(filters, settings, scanCallback)
            recordLowLatencyStartNow(scanMode)
            journal(
                event = "start_success",
                serviceUuid = serviceUuid,
                scanMode = scanMode,
                detail = "durationMs=$durationMs",
            )
        } catch (e: SecurityException) {
            journal(
                event = "start_failed_exception",
                serviceUuid = serviceUuid,
                scanMode = scanMode,
                detail = "security_exception=${e.message ?: "permission denied"}",
            )
            onFinished("Scan failed: ${e.message ?: "permission denied"}")
            return
        } catch (e: Exception) {
            journal(
                event = "start_failed_exception",
                serviceUuid = serviceUuid,
                scanMode = scanMode,
                detail = "exception=${e.message ?: "unknown error"}",
            )
            onFinished("Scan failed: ${e.message ?: "unknown error"}")
            return
        }

        scanner = leScanner
        callback = scanCallback
        stopRunnable = Runnable {
            journal(
                event = "stop_timeout_elapsed",
                serviceUuid = serviceUuid,
                scanMode = scanMode,
            )
            stop()
            onFinished(null)
        }
        mainHandler.postDelayed(stopRunnable!!, durationMs)
    }

    /**
     * Clears active and pending scanner state.
     *
     * Returns true when there was active/pending work that was cancelled.
     */
    private fun clearScanState(stopNativeScan: Boolean): Boolean {
        stopRunnable?.let { mainHandler.removeCallbacks(it) }
        stopRunnable = null

        pendingStartRunnable?.let { mainHandler.removeCallbacks(it) }
        val hadPendingStart = pendingStartRunnable != null
        pendingStartRunnable = null

        val currentScanner = scanner
        val currentCallback = callback
        scanner = null
        callback = null

        val hadActiveScan = currentScanner != null && currentCallback != null
        if (stopNativeScan && hadActiveScan) {
            try {
                currentScanner.stopScan(currentCallback)
                journal(
                    event = "stop_native_scan",
                    detail = "hadActiveScan=true",
                )
            } catch (e: SecurityException) {
                Log.w("BLE_SCAN", "stopScan failed: ${e.message}")
            } catch (_: IllegalStateException) {
                // Scanner may already be torn down.
            }
        }
        return hadPendingStart || hadActiveScan
    }

    private fun markGlobalScanStopNow() {
        val now = SystemClock.elapsedRealtime()
        synchronized(scanRateLock) { lowLatencyStartGate.markStop(now) }
        journal(
            event = "global_stop_marked",
            detail = "globalLastScanStopElapsedMs=$now",
        )
    }

    private fun markGlobalThrottleBackoff() {
        val now = SystemClock.elapsedRealtime()
        synchronized(scanRateLock) { lowLatencyStartGate.markThrottleFailure(now) }
        journal(
            event = "global_throttle_backoff",
            detail = "markedAtElapsedMs=$now",
        )
    }

    /**
     * Records successful low-latency scan starts in a rolling window.
     *
     * This proactively guards against Android scanner registration throttling
     * (status=6) when interactive scans are toggled repeatedly.
     */
    private fun recordLowLatencyStartNow(scanMode: Int) {
        if (scanMode != ScanSettings.SCAN_MODE_LOW_LATENCY) return
        val now = SystemClock.elapsedRealtime()
        val windowCount = synchronized(scanRateLock) {
            lowLatencyStartGate.recordSuccessfulStart(
                nowElapsedMs = now,
                scanMode = scanMode,
            )
        }
        journal(
            event = "low_latency_start_recorded",
            scanMode = scanMode,
            detail = "countWithinWindow=$windowCount",
        )
    }

    private fun emitDevice(result: ScanResult, onDeviceFound: (ScannedBleDevice) -> Unit) {
        val device = result.device ?: return
        val address = device.address ?: return
        if (!BluetoothAdapter.checkBluetoothAddress(address)) return
        val nameFromRecord = result.scanRecord?.deviceName
        val name = nameFromRecord ?: safeDeviceName(device)
        mainHandler.post {
            onDeviceFound(
                ScannedBleDevice(
                    macAddress = address,
                    displayName = name,
                    rssi = result.rssi,
                ),
            )
        }
    }

    private fun safeDeviceName(device: BluetoothDevice): String? {
        return try {
            device.name
        } catch (_: SecurityException) {
            null
        }
    }

    private fun hasBluetoothScanPermission(): Boolean {
        return context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun journal(
        event: String,
        serviceUuid: UUID? = null,
        scanMode: Int? = null,
        detail: String? = null,
    ) {
        val elapsedMs = SystemClock.elapsedRealtime()
        val line = buildString {
            append("t=")
            append(elapsedMs)
            append(" src=")
            append(scannerLabel)
            append(" event=")
            append(event)
            if (serviceUuid != null) {
                append(" service=")
                append(shortServiceId(serviceUuid))
            }
            if (scanMode != null) {
                append(" mode=")
                append(scanModeToLabel(scanMode))
            }
            if (!detail.isNullOrBlank()) {
                append(" ")
                append(detail)
            }
        }
        appendJournal(line)
    }

    private fun shortServiceId(uuid: UUID): String {
        val normalized = uuid.toString().lowercase()
        return when {
            normalized.startsWith("00001826") -> "FTMS"
            normalized.startsWith("0000180d") -> "HR"
            else -> normalized
        }
    }

    private fun scanModeToLabel(scanMode: Int): String {
        return when (scanMode) {
            ScanSettings.SCAN_MODE_LOW_POWER -> "LOW_POWER"
            ScanSettings.SCAN_MODE_BALANCED -> "BALANCED"
            ScanSettings.SCAN_MODE_LOW_LATENCY -> "LOW_LATENCY"
            ScanSettings.SCAN_MODE_OPPORTUNISTIC -> "OPPORTUNISTIC"
            else -> scanMode.toString()
        }
    }
}

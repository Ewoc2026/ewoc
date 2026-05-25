package com.example.ergometerapp

import android.os.Handler
import com.example.ergometerapp.ble.BleDeviceScanner
import java.util.UUID

/**
 * State bridge consumed by [MenuStatusProbeCoordinator].
 *
 * Keeping mutable probe state behind this interface lets the coordinator stay
 * unit-testable while MainViewModel remains the owner of Compose state holders.
 */
internal interface MenuStatusProbeStatePort {
    val currentScreen: AppScreen
    val isPickerActiveOrScanInProgress: Boolean
    val currentFtmsDeviceMac: String?
    val currentHrDeviceMac: String?
    var ftmsReachable: Boolean?
    var hrReachable: Boolean?
    var hrConsecutiveMisses: Int
    var hrLastSeenElapsedMs: Long?
}

/**
 * Scanner abstraction for passive status probes.
 */
internal interface StatusProbeScanEngine {
    fun start(
        serviceUuid: UUID,
        durationMs: Long,
        scanMode: Int,
        onDeviceFound: (ScannedBleDevice) -> Unit,
        onFinished: (String?) -> Unit,
    ): Boolean

    fun stop()
}

/**
 * Adapter from [BleDeviceScanner] to [StatusProbeScanEngine].
 */
internal class BleStatusProbeScanEngine(
    private val scanner: BleDeviceScanner,
) : StatusProbeScanEngine {
    override fun start(
        serviceUuid: UUID,
        durationMs: Long,
        scanMode: Int,
        onDeviceFound: (ScannedBleDevice) -> Unit,
        onFinished: (String?) -> Unit,
    ): Boolean {
        return scanner.start(
            serviceUuid = serviceUuid,
            durationMs = durationMs,
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
 * Coordinates passive FTMS/HR availability probes used by MENU readiness logic.
 *
 * Invariants:
 * - Trainer and HR probes never overlap; only one probe lane runs at a time.
 * - HR becomes stale only after repeated misses or an elapsed-time timeout.
 * - Picker dismiss triggers a temporary probe suppression window to avoid
 *   immediate scan restarts that can hit Android scan-frequency throttling.
 */
internal class MenuStatusProbeCoordinator(
    private val statePort: MenuStatusProbeStatePort,
    private val trainerScanEngine: StatusProbeScanEngine,
    private val hrScanEngine: StatusProbeScanEngine,
    private val handler: Handler,
    private val nowElapsedMs: () -> Long,
    private val isClosed: () -> Boolean,
    private val hasBluetoothScanPermission: () -> Boolean,
    private val refreshMenuRecommendations: () -> Unit,
    private val ftmsServiceUuid: UUID,
    private val hrServiceUuid: UUID,
    private val statusProbeScanMode: Int,
    private val trainerStatusProbeIntervalMs: Long,
    private val trainerStatusProbeDurationMs: Long,
    private val hrStatusProbeIntervalMs: Long,
    private val hrStatusProbeDurationMs: Long,
    private val statusProbeResumeDelayAfterPickerMs: Long,
    private val hrStatusMissThreshold: Int,
    private val hrStatusStaleTimeoutMs: Long,
) {
    private var pendingStatusProbeResume: Runnable? = null
    private var statusProbeSuppressedUntilElapsedMs: Long = 0L
    private var trainerStatusProbeInProgress = false
    private var hrStatusProbeInProgress = false
    private var trainerStatusProbeLoopRunning = false
    private var hrStatusProbeLoopRunning = false
    private val trainerStatusProbeRunnable = object : Runnable {
        override fun run() {
            if (isClosed() || !trainerStatusProbeLoopRunning) return
            probeTrainerAvailabilityNow()
            handler.postDelayed(this, trainerStatusProbeIntervalMs)
        }
    }
    private val hrStatusProbeRunnable = object : Runnable {
        override fun run() {
            if (isClosed() || !hrStatusProbeLoopRunning) return
            probeHrAvailabilityNow()
            handler.postDelayed(this, hrStatusProbeIntervalMs)
        }
    }

    fun suppressStatusProbesTemporarily() {
        cancelPendingStatusProbeResume()
        statusProbeSuppressedUntilElapsedMs = nowElapsedMs() + statusProbeResumeDelayAfterPickerMs
        val resumeRunnable = Runnable {
            pendingStatusProbeResume = null
            if (isClosed()) return@Runnable
            if (statePort.currentScreen != AppScreen.MENU) return@Runnable
            if (statePort.isPickerActiveOrScanInProgress) return@Runnable
            probeTrainerAvailabilityNow()
            probeHrAvailabilityNow()
        }
        pendingStatusProbeResume = resumeRunnable
        handler.postDelayed(resumeRunnable, statusProbeResumeDelayAfterPickerMs)
    }

    fun cancelPendingStatusProbeResume() {
        pendingStatusProbeResume?.let { handler.removeCallbacks(it) }
        pendingStatusProbeResume = null
    }

    fun startTrainerStatusPolling() {
        if (trainerStatusProbeLoopRunning) return
        trainerStatusProbeLoopRunning = true
        handler.post(trainerStatusProbeRunnable)
    }

    fun startHrStatusPolling() {
        if (hrStatusProbeLoopRunning) return
        hrStatusProbeLoopRunning = true
        handler.post(hrStatusProbeRunnable)
    }

    fun stopTrainerStatusPolling() {
        trainerStatusProbeLoopRunning = false
        handler.removeCallbacks(trainerStatusProbeRunnable)
        cancelTrainerStatusProbeScan()
    }

    fun stopHrStatusPolling() {
        hrStatusProbeLoopRunning = false
        handler.removeCallbacks(hrStatusProbeRunnable)
        cancelHrStatusProbeScan()
    }

    fun cancelTrainerStatusProbeScan() {
        trainerScanEngine.stop()
        trainerStatusProbeInProgress = false
    }

    fun cancelHrStatusProbeScan() {
        hrScanEngine.stop()
        hrStatusProbeInProgress = false
    }

    fun probeTrainerAvailabilityNow() {
        if (shouldSkipProbe()) return

        val targetMac = statePort.currentFtmsDeviceMac
        if (targetMac == null) {
            statePort.ftmsReachable = null
            refreshMenuRecommendations()
            return
        }

        if (!hasBluetoothScanPermission()) {
            return
        }

        var targetFound = false
        trainerStatusProbeInProgress = true
        val started = trainerScanEngine.start(
            serviceUuid = ftmsServiceUuid,
            durationMs = trainerStatusProbeDurationMs,
            scanMode = statusProbeScanMode,
            onDeviceFound = { device ->
                if (BluetoothMacAddress.normalizeOrNull(device.macAddress) == targetMac) {
                    targetFound = true
                }
            },
            onFinished = onFinished@{ errorMessage ->
                trainerStatusProbeInProgress = false
                if (errorMessage != null) {
                    // Keep last known FTMS reachability for transient scan errors.
                    probeHrAvailabilityNow()
                    return@onFinished
                }
                if (statePort.currentFtmsDeviceMac == targetMac) {
                    val changed = statePort.ftmsReachable != targetFound
                    statePort.ftmsReachable = targetFound
                    if (changed) {
                        refreshMenuRecommendations()
                    }
                }
                probeHrAvailabilityNow()
            },
        )
        if (!started) {
            trainerStatusProbeInProgress = false
            probeHrAvailabilityNow()
        }
    }

    fun probeHrAvailabilityNow() {
        if (shouldSkipProbe()) return

        val targetMac = statePort.currentHrDeviceMac
        if (targetMac == null) {
            statePort.hrReachable = null
            statePort.hrConsecutiveMisses = 0
            statePort.hrLastSeenElapsedMs = null
            refreshMenuRecommendations()
            return
        }

        if (!hasBluetoothScanPermission()) {
            return
        }

        var targetFound = false
        hrStatusProbeInProgress = true
        val started = hrScanEngine.start(
            serviceUuid = hrServiceUuid,
            durationMs = hrStatusProbeDurationMs,
            scanMode = statusProbeScanMode,
            onDeviceFound = { device ->
                if (BluetoothMacAddress.normalizeOrNull(device.macAddress) == targetMac) {
                    targetFound = true
                }
            },
            onFinished = onFinished@{ errorMessage ->
                hrStatusProbeInProgress = false
                if (errorMessage != null) {
                    return@onFinished
                }
                if (statePort.currentHrDeviceMac == targetMac) {
                    if (targetFound) {
                        val changed = statePort.hrReachable != true
                        statePort.hrReachable = true
                        statePort.hrConsecutiveMisses = 0
                        statePort.hrLastSeenElapsedMs = nowElapsedMs()
                        if (changed) {
                            refreshMenuRecommendations()
                        }
                    } else {
                        statePort.hrConsecutiveMisses += 1
                        val now = nowElapsedMs()
                        val staleByMisses = statePort.hrConsecutiveMisses >= hrStatusMissThreshold
                        val staleByTime = statePort.hrLastSeenElapsedMs?.let { lastSeenElapsedMs ->
                            (now - lastSeenElapsedMs) >= hrStatusStaleTimeoutMs
                        } == true
                        if (staleByMisses || staleByTime) {
                            val changed = statePort.hrReachable != false
                            statePort.hrReachable = false
                            if (changed) {
                                refreshMenuRecommendations()
                            }
                        }
                    }
                }
            },
        )
        if (!started) {
            hrStatusProbeInProgress = false
        }
    }

    fun close() {
        cancelPendingStatusProbeResume()
        stopTrainerStatusPolling()
        stopHrStatusPolling()
    }

    private fun shouldSkipProbe(): Boolean {
        if (isClosed()) return true
        if (nowElapsedMs() < statusProbeSuppressedUntilElapsedMs) return true
        if (statePort.currentScreen != AppScreen.MENU) return true
        if (statePort.isPickerActiveOrScanInProgress) return true
        if (trainerStatusProbeInProgress || hrStatusProbeInProgress) return true
        return false
    }
}

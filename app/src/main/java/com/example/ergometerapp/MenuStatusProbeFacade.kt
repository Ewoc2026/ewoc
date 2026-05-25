package com.example.ergometerapp

import android.os.Handler
import android.os.SystemClock
import com.example.ergometerapp.ble.BleDeviceScanner
import java.util.UUID

/**
 * Owns passive MENU probe lifecycle so [MainViewModel] can delegate availability
 * polling through one seam while still retaining Compose state ownership.
 */
internal interface MenuStatusProbeFacade {
    fun suppressStatusProbesTemporarily()

    fun cancelPendingStatusProbeResume()

    fun startTrainerStatusPolling()

    fun startHrStatusPolling()

    fun stopTrainerStatusPolling()

    fun stopHrStatusPolling()

    fun cancelTrainerStatusProbeScan()

    fun cancelHrStatusProbeScan()

    fun probeTrainerAvailabilityNow()

    fun probeHrAvailabilityNow()

    fun close()
}

internal class RealMenuStatusProbeFacade(
    statePort: MenuStatusProbeStatePort,
    appHandler: Handler,
    hasBluetoothScanPermission: () -> Boolean,
    refreshMenuRecommendations: () -> Unit,
    isClosed: () -> Boolean,
    ftmsServiceUuid: UUID,
    hrServiceUuid: UUID,
    statusProbeScanMode: Int,
    trainerStatusProbeIntervalMs: Long,
    trainerStatusProbeDurationMs: Long,
    hrStatusProbeIntervalMs: Long,
    hrStatusProbeDurationMs: Long,
    statusProbeResumeDelayAfterPickerMs: Long,
    hrStatusMissThreshold: Int,
    hrStatusStaleTimeoutMs: Long,
    trainerScanEngine: StatusProbeScanEngine,
    hrScanEngine: StatusProbeScanEngine,
    nowElapsedMs: () -> Long = SystemClock::elapsedRealtime,
) : MenuStatusProbeFacade {
    private val coordinator = MenuStatusProbeCoordinator(
        statePort = statePort,
        trainerScanEngine = trainerScanEngine,
        hrScanEngine = hrScanEngine,
        handler = appHandler,
        nowElapsedMs = nowElapsedMs,
        isClosed = isClosed,
        hasBluetoothScanPermission = hasBluetoothScanPermission,
        refreshMenuRecommendations = refreshMenuRecommendations,
        ftmsServiceUuid = ftmsServiceUuid,
        hrServiceUuid = hrServiceUuid,
        statusProbeScanMode = statusProbeScanMode,
        trainerStatusProbeIntervalMs = trainerStatusProbeIntervalMs,
        trainerStatusProbeDurationMs = trainerStatusProbeDurationMs,
        hrStatusProbeIntervalMs = hrStatusProbeIntervalMs,
        hrStatusProbeDurationMs = hrStatusProbeDurationMs,
        statusProbeResumeDelayAfterPickerMs = statusProbeResumeDelayAfterPickerMs,
        hrStatusMissThreshold = hrStatusMissThreshold,
        hrStatusStaleTimeoutMs = hrStatusStaleTimeoutMs,
    )

    override fun suppressStatusProbesTemporarily() {
        coordinator.suppressStatusProbesTemporarily()
    }

    override fun cancelPendingStatusProbeResume() {
        coordinator.cancelPendingStatusProbeResume()
    }

    override fun startTrainerStatusPolling() {
        coordinator.startTrainerStatusPolling()
    }

    override fun startHrStatusPolling() {
        coordinator.startHrStatusPolling()
    }

    override fun stopTrainerStatusPolling() {
        coordinator.stopTrainerStatusPolling()
    }

    override fun stopHrStatusPolling() {
        coordinator.stopHrStatusPolling()
    }

    override fun cancelTrainerStatusProbeScan() {
        coordinator.cancelTrainerStatusProbeScan()
    }

    override fun cancelHrStatusProbeScan() {
        coordinator.cancelHrStatusProbeScan()
    }

    override fun probeTrainerAvailabilityNow() {
        coordinator.probeTrainerAvailabilityNow()
    }

    override fun probeHrAvailabilityNow() {
        coordinator.probeHrAvailabilityNow()
    }

    override fun close() {
        coordinator.close()
    }
}

internal fun buildMenuStatusProbeFacade(
    appContext: android.content.Context,
    statePort: MenuStatusProbeStatePort,
    appHandler: Handler,
    hasBluetoothScanPermission: () -> Boolean,
    refreshMenuRecommendations: () -> Unit,
    isClosed: () -> Boolean,
    ftmsServiceUuid: UUID,
    hrServiceUuid: UUID,
    statusProbeScanMode: Int,
    trainerStatusProbeIntervalMs: Long,
    trainerStatusProbeDurationMs: Long,
    hrStatusProbeIntervalMs: Long,
    hrStatusProbeDurationMs: Long,
    statusProbeResumeDelayAfterPickerMs: Long,
    hrStatusMissThreshold: Int,
    hrStatusStaleTimeoutMs: Long,
): MenuStatusProbeFacade {
    return RealMenuStatusProbeFacade(
        statePort = statePort,
        appHandler = appHandler,
        hasBluetoothScanPermission = hasBluetoothScanPermission,
        refreshMenuRecommendations = refreshMenuRecommendations,
        isClosed = isClosed,
        ftmsServiceUuid = ftmsServiceUuid,
        hrServiceUuid = hrServiceUuid,
        statusProbeScanMode = statusProbeScanMode,
        trainerStatusProbeIntervalMs = trainerStatusProbeIntervalMs,
        trainerStatusProbeDurationMs = trainerStatusProbeDurationMs,
        hrStatusProbeIntervalMs = hrStatusProbeIntervalMs,
        hrStatusProbeDurationMs = hrStatusProbeDurationMs,
        statusProbeResumeDelayAfterPickerMs = statusProbeResumeDelayAfterPickerMs,
        hrStatusMissThreshold = hrStatusMissThreshold,
        hrStatusStaleTimeoutMs = hrStatusStaleTimeoutMs,
        trainerScanEngine = BleStatusProbeScanEngine(
            BleDeviceScanner(appContext, scannerLabel = "probe_ftms"),
        ),
        hrScanEngine = BleStatusProbeScanEngine(
            BleDeviceScanner(appContext, scannerLabel = "probe_hr"),
        ),
    )
}

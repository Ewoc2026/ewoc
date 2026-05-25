package com.example.ergometerapp

/**
 * State bridge consumed by [DeviceSelectionPreScanCoordinator].
 *
 * Keeping mutable runtime fields behind a port keeps callback behavior
 * testable without Compose runtime or ViewModel dependencies.
 */
internal interface DeviceSelectionPreScanStatePort {
    var hrConnected: Boolean
    var heartRate: Int?
}

/**
 * Coordinates side effects that must happen before picker scan request/start.
 *
 * Invariants:
 * - Any picker request cancels pending probe resume and active probe scans.
 * - HR picker requests close HR GATT and clear live HR values before scan.
 * - Picker scan start re-applies probe cancellation to guard retry paths.
 */
internal class DeviceSelectionPreScanCoordinator(
    private val statePort: DeviceSelectionPreScanStatePort,
    private val cancelPendingStatusProbeResume: () -> Unit,
    private val cancelTrainerStatusProbeScan: () -> Unit,
    private val cancelHrStatusProbeScan: () -> Unit,
    private val closeHeartRateClient: () -> Unit,
    private val updateSessionHeartRate: (Int?) -> Unit,
) {
    fun onBeforeScanRequest(kind: DeviceSelectionKind) {
        cancelPendingStatusProbeResume()
        cancelTrainerStatusProbeScan()
        cancelHrStatusProbeScan()
        if (kind != DeviceSelectionKind.HEART_RATE) {
            return
        }
        closeHeartRateClient()
        statePort.hrConnected = false
        statePort.heartRate = null
        updateSessionHeartRate(null)
    }

    fun onBeforeScanStart() {
        cancelTrainerStatusProbeScan()
        cancelHrStatusProbeScan()
    }
}

package io.github.ewoc2026.ewoc

import io.github.ewoc2026.ewoc.logging.AppLog

/**
 * State bridge consumed by [DeviceSelectionApplyCoordinator].
 *
 * This keeps selection side effects unit-testable while MainViewModel remains
 * the owner of Compose state holders and runtime probe counters.
 */
internal interface DeviceSelectionApplyStatePort {
    var selectedFtmsDeviceMac: String?
    var ftmsDeviceName: String
    var ftmsReachable: Boolean?
    var selectedHrDeviceMac: String?
    var hrDeviceName: String
    var hrReachable: Boolean?
    var hrConsecutiveMisses: Int
    var hrLastSeenElapsedMs: Long?
    var hrConnected: Boolean
}

/**
 * Applies FTMS/HR picker selections and preserves selection-side invariants.
 *
 * Invariants:
 * - Every selection change first cancels the matching in-flight status probe.
 * - Clearing selection resets volatile state and persists null device identity.
 * - Applying a selection resets stale probe state and triggers one fresh probe.
 */
internal class DeviceSelectionApplyCoordinator(
    private val statePort: DeviceSelectionApplyStatePort,
    private val saveFtmsDeviceMac: (String?) -> Unit,
    private val saveFtmsDeviceName: (String?) -> Unit,
    private val saveHrDeviceMac: (String?) -> Unit,
    private val saveHrDeviceName: (String?) -> Unit,
    private val releaseWarmTrainerConnection: () -> Unit,
    private val prepareWarmTrainerConnection: () -> Unit,
    private val cancelTrainerStatusProbeScan: () -> Unit,
    private val cancelHrStatusProbeScan: () -> Unit,
    private val probeTrainerAvailabilityNow: () -> Unit,
    private val probeHrAvailabilityNow: () -> Unit,
    private val refreshAiAssistantRecommendations: () -> Unit,
) {
    fun applyFtmsDeviceSelection(normalizedMac: String?, deviceName: String?) {
        cancelTrainerStatusProbeScan()
        releaseWarmTrainerConnection()
        if (normalizedMac == null) {
            AppLog.testMarker(event = "trainer_selection_cleared")
            statePort.selectedFtmsDeviceMac = null
            statePort.ftmsDeviceName = ""
            statePort.ftmsReachable = null
            saveFtmsDeviceMac(null)
            saveFtmsDeviceName(null)
            refreshAiAssistantRecommendations()
            return
        }

        statePort.selectedFtmsDeviceMac = normalizedMac
        val normalizedName = normalizeDeviceName(deviceName)
        AppLog.testMarker(
            event = "trainer_selection_applied",
            context = mapOf(
                "trainerMac" to normalizedMac,
                "trainerName" to (normalizedName ?: "unknown"),
            ),
        )
        statePort.ftmsDeviceName = normalizedName.orEmpty()
        statePort.ftmsReachable = null
        saveFtmsDeviceMac(normalizedMac)
        saveFtmsDeviceName(normalizedName)
        prepareWarmTrainerConnection()
        probeTrainerAvailabilityNow()
        refreshAiAssistantRecommendations()
    }

    fun applyHrDeviceSelection(normalizedMac: String?, deviceName: String?) {
        cancelHrStatusProbeScan()
        if (normalizedMac == null) {
            statePort.selectedHrDeviceMac = null
            statePort.hrDeviceName = ""
            statePort.hrReachable = null
            statePort.hrConsecutiveMisses = 0
            statePort.hrLastSeenElapsedMs = null
            statePort.hrConnected = false
            saveHrDeviceMac(null)
            saveHrDeviceName(null)
            refreshAiAssistantRecommendations()
            return
        }

        statePort.selectedHrDeviceMac = normalizedMac
        val normalizedName = normalizeDeviceName(deviceName)
        statePort.hrDeviceName = normalizedName.orEmpty()
        statePort.hrReachable = null
        statePort.hrConsecutiveMisses = 0
        statePort.hrLastSeenElapsedMs = null
        saveHrDeviceMac(normalizedMac)
        saveHrDeviceName(normalizedName)
        probeHrAvailabilityNow()
        refreshAiAssistantRecommendations()
    }

    private fun normalizeDeviceName(rawDeviceName: String?): String? {
        return rawDeviceName?.trim()?.takeIf { it.isNotEmpty() }
    }
}

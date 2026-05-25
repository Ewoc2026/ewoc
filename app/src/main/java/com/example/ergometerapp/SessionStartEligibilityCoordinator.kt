package com.example.ergometerapp

/**
 * State bridge consumed by [SessionStartEligibilityCoordinator].
 *
 * Session start and selected-device guards are read through this port so
 * invariants can be unit-tested without MainViewModel or Compose runtime.
 */
internal interface SessionStartEligibilityStatePort {
    val workoutReady: Boolean
    val selectedSessionSetupMode: SessionSetupMode
    var mockTrainerModeEnabled: Boolean
    var selectedFtmsDeviceMac: String?
    var selectedHrDeviceMac: String?
}

/**
 * Coordinates session-start eligibility and debug mock-trainer toggles.
 *
 * Invariants:
 * - Mock-trainer toggles are no-op in non-debug builds.
 * - Debug toggles persist and clear connection issue prompt after state update.
 * - Session start requires either workout readiness or telemetry-only mode, plus either FTMS
 *   selection or active mock trainer.
 */
internal class SessionStartEligibilityCoordinator(
    private val statePort: SessionStartEligibilityStatePort,
    private val isDebugBuild: () -> Boolean,
    private val normalizeBluetoothMac: (String) -> String?,
    private val saveMockTrainerModeEnabled: (Boolean) -> Unit,
    private val clearConnectionIssuePrompt: () -> Unit,
) {
    fun onMockTrainerModeChanged(enabled: Boolean) {
        if (!isDebugBuild()) return
        statePort.mockTrainerModeEnabled = enabled
        saveMockTrainerModeEnabled(enabled)
        clearConnectionIssuePrompt()
    }

    fun canStartSession(): Boolean {
        return currentSelectionIsRunnable() &&
            (isMockTrainerModeActive() || hasSelectedFtmsDevice())
    }

    private fun currentSelectionIsRunnable(): Boolean {
        return statePort.workoutReady || statePort.selectedSessionSetupMode == SessionSetupMode.TELEMETRY_ONLY
    }

    fun hasSelectedFtmsDevice(): Boolean = currentFtmsDeviceMac() != null

    fun hasSelectedHrDevice(): Boolean = currentHrDeviceMac() != null

    fun isMockTrainerModeActive(): Boolean {
        return isDebugBuild() && statePort.mockTrainerModeEnabled
    }

    fun currentFtmsDeviceMac(): String? {
        return statePort.selectedFtmsDeviceMac?.let(normalizeBluetoothMac)
    }

    fun currentHrDeviceMac(): String? {
        return statePort.selectedHrDeviceMac?.let(normalizeBluetoothMac)
    }
}

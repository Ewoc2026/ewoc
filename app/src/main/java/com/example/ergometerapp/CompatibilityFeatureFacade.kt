package com.example.ergometerapp

import com.example.ergometerapp.compat.CompatibilityRunArtifacts

internal interface CompatibilityFeatureStatePort : CompatibilityCheckStatePort {
    override var latestRunArtifacts: CompatibilityRunArtifacts?
    override var checkInProgress: Boolean
    override var statusMessage: String?
}

/**
 * Encapsulates the ViewModel-local Compatibility Mode workflow glue that still
 * belongs near Compose state, while lower-level execution/preparation remains in
 * the dedicated coordinators.
 *
 */
internal class CompatibilityFeatureFacade(
    private val statePort: CompatibilityFeatureStatePort,
    private val currentTrainerMac: () -> String?,
    private val currentTrainerAlias: () -> String,
    private val hasBluetoothConnectPermission: () -> Boolean,
    private val runCompatibilityCheck: (
        launchRequest: CompatibilityCheckLaunchRequest,
        statePort: CompatibilityCheckStatePort,
        resolveRequiresTrainerMessage: () -> String,
        resolveRequiresPermissionMessage: () -> String,
        resolveRunningStatusMessage: () -> String,
        resolvePassStatusMessage: () -> String,
        resolveFailStatusMessage: (String) -> String,
        resolvePersistFailureStatusMessage: (String) -> String,
    ) -> Unit,
    private val resolveRequiresTrainerMessage: () -> String,
    private val resolveRequiresPermissionMessage: () -> String,
    private val resolveRunningStatusMessage: () -> String,
    private val resolvePassStatusMessage: () -> String,
    private val resolveFailStatusMessage: (String) -> String,
    private val resolvePersistFailureStatusMessage: (String) -> String,
) {

    fun onRunRequested() {
        if (statePort.checkInProgress) return
        runCompatibilityCheck(
            CompatibilityCheckLaunchRequest(
                trainerMacAddress = currentTrainerMac(),
                trainerAlias = currentTrainerAlias(),
                hasBluetoothConnectPermission = hasBluetoothConnectPermission(),
            ),
            statePort,
            resolveRequiresTrainerMessage,
            resolveRequiresPermissionMessage,
            resolveRunningStatusMessage,
            resolvePassStatusMessage,
            resolveFailStatusMessage,
            resolvePersistFailureStatusMessage,
        )
    }
}

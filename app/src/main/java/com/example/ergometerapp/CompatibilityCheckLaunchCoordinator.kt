package com.example.ergometerapp

/**
 * Input payload for Compatibility Mode launch preflight.
 *
 * Invariants:
 * - Trainer address is expected to be already normalized when present.
 * - Trainer alias is trimmed before it reaches execution so blank UI labels do
 *   not become persisted run metadata.
 */
internal data class CompatibilityCheckLaunchRequest(
    val trainerMacAddress: String?,
    val trainerAlias: String?,
    val hasBluetoothConnectPermission: Boolean,
)

/**
 * Result of Compatibility Mode launch preflight.
 */
internal sealed class CompatibilityCheckLaunchPreparationResult {
    data class Blocked(
        val statusMessage: String,
    ) : CompatibilityCheckLaunchPreparationResult()

    data class Ready(
        val executionRequest: CompatibilityCheckExecutionRequest,
    ) : CompatibilityCheckLaunchPreparationResult()
}

/**
 * Keeps Compatibility Mode launch guards and request assembly out of MainViewModel.
 *
 * Invariants:
 * - Missing-trainer validation wins before permission messaging so the UI does
 *   not imply permissions alone can fix an unselected device.
 * - Only validated inputs produce [CompatibilityCheckExecutionRequest].
 */
internal class CompatibilityCheckLaunchCoordinator {

    fun prepareRun(
        request: CompatibilityCheckLaunchRequest,
        resolveRequiresTrainerMessage: () -> String,
        resolveRequiresPermissionMessage: () -> String,
    ): CompatibilityCheckLaunchPreparationResult {
        val trainerMacAddress = request.trainerMacAddress?.takeIf { it.isNotBlank() }
            ?: return CompatibilityCheckLaunchPreparationResult.Blocked(
                statusMessage = resolveRequiresTrainerMessage(),
            )

        if (!request.hasBluetoothConnectPermission) {
            return CompatibilityCheckLaunchPreparationResult.Blocked(
                statusMessage = resolveRequiresPermissionMessage(),
            )
        }

        return CompatibilityCheckLaunchPreparationResult.Ready(
            executionRequest = CompatibilityCheckExecutionRequest(
                trainerMacAddress = trainerMacAddress,
                trainerAlias = request.trainerAlias?.trim()?.takeIf { it.isNotEmpty() },
            ),
        )
    }
}

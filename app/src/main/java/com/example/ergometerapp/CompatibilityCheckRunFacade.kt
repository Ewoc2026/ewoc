package com.example.ergometerapp

import java.util.concurrent.RejectedExecutionException

/**
 * Owns Compatibility Mode launch gating plus background execution handoff.
 *
 * Invariants:
 * - The running-state transition happens only after background scheduling has
 *   been accepted so rejected tasks cannot strand the UI in a loading state.
 * - Completion is routed back through the main thread before state mutation so
 *   ViewModel-owned Compose state is updated from a single thread.
 */
internal class CompatibilityCheckRunFacade(
    private val launchCoordinator: CompatibilityCheckLaunchCoordinator,
    private val checkCoordinator: CompatibilityCheckCoordinator,
    private val executor: CompatibilityCheckExecutor,
    private val runOnBackgroundThread: (() -> Unit) -> Unit,
    private val runOnMainThread: (() -> Unit) -> Unit,
    private val isClosed: () -> Boolean = { false },
) {

    fun startRun(
        launchRequest: CompatibilityCheckLaunchRequest,
        statePort: CompatibilityCheckStatePort,
        resolveRequiresTrainerMessage: () -> String,
        resolveRequiresPermissionMessage: () -> String,
        resolveRunningStatusMessage: () -> String,
        resolvePassStatusMessage: () -> String,
        resolveFailStatusMessage: (String) -> String,
        resolvePersistFailureStatusMessage: (String) -> String,
    ) {
        val launchPreparation = launchCoordinator.prepareRun(
            request = launchRequest,
            resolveRequiresTrainerMessage = resolveRequiresTrainerMessage,
            resolveRequiresPermissionMessage = resolveRequiresPermissionMessage,
        )
        val executionRequest = when (launchPreparation) {
            is CompatibilityCheckLaunchPreparationResult.Blocked -> {
                statePort.statusMessage = launchPreparation.statusMessage
                return
            }

            is CompatibilityCheckLaunchPreparationResult.Ready -> launchPreparation.executionRequest
        }

        try {
            runOnBackgroundThread {
                val execution = executor.runAndPersist(
                    request = executionRequest,
                )
                runOnMainThread {
                    if (isClosed()) return@runOnMainThread
                    checkCoordinator.onRunCompleted(
                        executionResult = execution,
                        statePort = statePort,
                        resolvePassStatusMessage = resolvePassStatusMessage,
                        resolveFailStatusMessage = resolveFailStatusMessage,
                        resolvePersistFailureStatusMessage = resolvePersistFailureStatusMessage,
                    )
                }
            }
        } catch (_: RejectedExecutionException) {
            return
        }

        checkCoordinator.onRunStarted(
            statePort = statePort,
            resolveRunningStatusMessage = resolveRunningStatusMessage,
        )
    }
}

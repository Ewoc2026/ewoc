package com.example.ergometerapp

import com.example.ergometerapp.compat.CompatibilityFailureCode
import com.example.ergometerapp.compat.CompatibilityRunArtifacts
import com.example.ergometerapp.compat.CompatibilitySummaryStatus

/**
 */
internal interface CompatibilityCheckStatePort {
    var latestRunArtifacts: CompatibilityRunArtifacts?
    var checkInProgress: Boolean
    var statusMessage: String?
}

/**
 * Coordinates compatibility-check status and prompt routing around the run executor.
 *
 * Invariants:
 * - Start/reset policy and completion routing stay together so `MainViewModel`
 *   does not branch separately for pass/fail/persist outcomes.
 */
internal class CompatibilityCheckCoordinator(
    private val resolveFailureReasonMessage: (
        failureReasonKey: String?,
        failureCode: CompatibilityFailureCode?,
    ) -> String,
) {

    fun onRunStarted(
        statePort: CompatibilityCheckStatePort,
        resolveRunningStatusMessage: () -> String,
    ) {
        statePort.checkInProgress = true
        statePort.statusMessage = resolveRunningStatusMessage()
    }

    fun onRunCompleted(
        executionResult: CompatibilityCheckExecutionResult,
        statePort: CompatibilityCheckStatePort,
        resolvePassStatusMessage: () -> String,
        resolveFailStatusMessage: (String) -> String,
        resolvePersistFailureStatusMessage: (String) -> String,
    ) {
        val artifacts = executionResult.artifacts
        val summary = artifacts.result.summary
        val failureReason = if (summary.status == CompatibilitySummaryStatus.FAIL) {
            resolveFailureReasonMessage(
                summary.failureReasonKey,
                summary.failureCode,
            )
        } else {
            null
        }
        val baseStatusMessage = if (summary.status == CompatibilitySummaryStatus.PASS) {
            resolvePassStatusMessage()
        } else {
            resolveFailStatusMessage(requireNotNull(failureReason))
        }

        statePort.latestRunArtifacts = artifacts
        statePort.checkInProgress = false
        statePort.statusMessage = if (executionResult.persisted) {
            baseStatusMessage
        } else {
            resolvePersistFailureStatusMessage(baseStatusMessage)
        }
    }
}

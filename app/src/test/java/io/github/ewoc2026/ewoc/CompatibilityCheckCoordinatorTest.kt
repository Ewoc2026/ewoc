package io.github.ewoc2026.ewoc

import io.github.ewoc2026.ewoc.compat.CompatibilityCheckResult
import io.github.ewoc2026.ewoc.compat.CompatibilityFailureCode
import io.github.ewoc2026.ewoc.compat.CompatibilityRunArtifacts
import io.github.ewoc2026.ewoc.compat.CompatibilitySummaryOutput
import io.github.ewoc2026.ewoc.compat.CompatibilitySummaryStatus
import io.github.ewoc2026.ewoc.compat.quirks.MatchConfidence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CompatibilityCheckCoordinatorTest {

    @Test
    fun onRunStarted_marksRunInProgressAndUpdatesStatus() {
        val statePort = FakeCompatibilityCheckStatePort(
            checkInProgress = false,
            statusMessage = "old-status",
        )
        val coordinator = coordinator()

        coordinator.onRunStarted(
            statePort = statePort,
            resolveRunningStatusMessage = { "running" },
        )

        assertEquals("running", statePort.statusMessage)
        assertEquals(true, statePort.checkInProgress)
    }

    @Test
    fun onRunCompleted_routesPassStatus() {
        val artifacts = compatibilityArtifacts(
            runId = "pass-run",
            status = CompatibilitySummaryStatus.PASS,
        )
        val statePort = FakeCompatibilityCheckStatePort(checkInProgress = true)
        val coordinator = coordinator()

        coordinator.onRunCompleted(
            executionResult = CompatibilityCheckExecutionResult(
                artifacts = artifacts,
                persisted = true,
            ),
            statePort = statePort,
            resolvePassStatusMessage = { "pass" },
            resolveFailStatusMessage = { reason -> "fail:$reason" },
            resolvePersistFailureStatusMessage = { baseMessage -> "persist:$baseMessage" },
        )

        assertEquals(artifacts, statePort.latestRunArtifacts)
        assertFalse(statePort.checkInProgress)
        assertEquals("pass", statePort.statusMessage)
    }

    @Test
    fun onRunCompleted_routesFailureStatusAndPersistSuffixFromResolvedReason() {
        val artifacts = compatibilityArtifacts(
            runId = "fail-run",
            status = CompatibilitySummaryStatus.FAIL,
            failureReasonKey = "ftms.control_denied",
            failureCode = CompatibilityFailureCode.REQUEST_CONTROL_REJECTED,
        )
        val resolvedReasons = mutableListOf<Pair<String?, CompatibilityFailureCode?>>()
        val coordinator = coordinator(
            resolveFailureReasonMessage = { failureReasonKey, failureCode ->
                resolvedReasons += failureReasonKey to failureCode
                "Control denied"
            },
        )
        val statePort = FakeCompatibilityCheckStatePort(checkInProgress = true)

        coordinator.onRunCompleted(
            executionResult = CompatibilityCheckExecutionResult(
                artifacts = artifacts,
                persisted = false,
            ),
            statePort = statePort,
            resolvePassStatusMessage = { "pass" },
            resolveFailStatusMessage = { reason -> "fail:$reason" },
            resolvePersistFailureStatusMessage = { baseMessage -> "persist:$baseMessage" },
        )

        assertEquals(artifacts, statePort.latestRunArtifacts)
        assertFalse(statePort.checkInProgress)
        assertEquals("persist:fail:Control denied", statePort.statusMessage)
        assertEquals(
            listOf<Pair<String?, CompatibilityFailureCode?>>(
                "ftms.control_denied" to CompatibilityFailureCode.REQUEST_CONTROL_REJECTED,
            ),
            resolvedReasons,
        )
    }

    private fun coordinator(
        resolveFailureReasonMessage: (
            failureReasonKey: String?,
            failureCode: CompatibilityFailureCode?,
        ) -> String = { _, _ -> "Unknown failure" },
    ): CompatibilityCheckCoordinator {
        return CompatibilityCheckCoordinator(
            resolveFailureReasonMessage = resolveFailureReasonMessage,
        )
    }

    private fun compatibilityArtifacts(
        runId: String,
        status: CompatibilitySummaryStatus,
        failureReasonKey: String? = null,
        failureCode: CompatibilityFailureCode? = null,
    ): CompatibilityRunArtifacts {
        return CompatibilityRunArtifacts(
            runId = runId,
            capturedAtEpochMs = 2_000L,
            trainerIdentity = "AA:BB:CC:DD:EE:FF",
            trainerAlias = "Trainer",
            result = CompatibilityCheckResult(
                summary = CompatibilitySummaryOutput(
                    status = status,
                    startedAtEpochMs = 1_000L,
                    endedAtEpochMs = 2_000L,
                    elapsedMs = 1_000L,
                    totalBudgetMs = 3_000L,
                    quirksId = "default",
                    quirksMatchConfidence = MatchConfidence.LOW,
                    degradationSignals = emptyList(),
                    failureCode = failureCode,
                    failureCategory = null,
                    failureReasonKey = failureReasonKey,
                    failureDetail = null,
                ),
                timeline = emptyList(),
            ),
        )
    }

    private class FakeCompatibilityCheckStatePort(
        override var latestRunArtifacts: CompatibilityRunArtifacts? = null,
        override var checkInProgress: Boolean = false,
        override var statusMessage: String? = null,
    ) : CompatibilityCheckStatePort
}

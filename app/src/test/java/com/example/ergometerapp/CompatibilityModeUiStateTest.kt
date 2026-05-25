package com.example.ergometerapp

import com.example.ergometerapp.compat.CompatibilityCheckResult
import com.example.ergometerapp.compat.CompatibilityRunArtifacts
import com.example.ergometerapp.compat.CompatibilitySummaryOutput
import com.example.ergometerapp.compat.CompatibilitySummaryStatus
import com.example.ergometerapp.compat.quirks.MatchConfidence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class CompatibilityModeUiStateTest {

    @Test
    fun checkStatePortSharesCompatibilityBackingState() {
        val uiState = CompatibilityModeUiState()
        val artifacts = compatibilityArtifacts("run-1")

        uiState.checkStatePort.latestRunArtifacts = artifacts
        uiState.checkStatePort.checkInProgress = true
        uiState.checkStatePort.statusMessage = "running"

        assertSame(artifacts, uiState.latestRunArtifacts)
        assertEquals("running", uiState.statusMessageState.value)
        assertEquals(true, uiState.checkInProgressState.value)
    }

    private fun compatibilityArtifacts(runId: String): CompatibilityRunArtifacts {
        return CompatibilityRunArtifacts(
            runId = runId,
            capturedAtEpochMs = 2_000L,
            trainerIdentity = "AA:BB:CC:DD:EE:FF",
            trainerAlias = "Trainer",
            result = CompatibilityCheckResult(
                summary = CompatibilitySummaryOutput(
                    status = CompatibilitySummaryStatus.PASS,
                    startedAtEpochMs = 1_000L,
                    endedAtEpochMs = 2_000L,
                    elapsedMs = 1_000L,
                    totalBudgetMs = 3_000L,
                    quirksId = "default",
                    quirksMatchConfidence = MatchConfidence.LOW,
                    degradationSignals = emptyList(),
                    failureCode = null,
                    failureCategory = null,
                    failureReasonKey = null,
                    failureDetail = null,
                ),
                timeline = emptyList(),
            ),
        )
    }
}

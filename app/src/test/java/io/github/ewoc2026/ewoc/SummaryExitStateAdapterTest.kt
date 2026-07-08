package io.github.ewoc2026.ewoc

import io.github.ewoc2026.ewoc.session.SessionSummary
import io.github.ewoc2026.ewoc.session.export.SessionExportSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class SummaryExitStateAdapterTest {

    @Test
    fun realSummaryExitStatePort_mutatesSharedSummaryOwners() {
        val uiState = AppUiState()
        val documentsFolderUiState = DocumentsFolderUiState()
        val compatibilityModeUiState = CompatibilityModeUiState()
        val summaryFitUiState = SummaryFitUiState()
        val statePort = RealSummaryExitStatePort(
            uiState = uiState,
            documentsFolderUiState = documentsFolderUiState,
            compatibilityModeUiState = compatibilityModeUiState,
            summaryFitUiState = summaryFitUiState,
        )

        val summary = sampleSummary()
        val snapshot = sampleSnapshot(summary)

        statePort.summary = summary
        statePort.connectingTimeoutMessage = "timeout"
        statePort.fitPendingSnapshot = snapshot
        statePort.fitStatusMessage = "export failed"
        statePort.fitStatusIsError = true
        statePort.lastAutoExportSummaryFingerprint = "fingerprint"

        assertEquals(summary, uiState.summary.value)
        assertEquals("timeout", uiState.connectingTimeoutMessage.value)
        assertEquals(snapshot, documentsFolderUiState.pendingFitExportSnapshot)
        assertEquals("export failed", documentsFolderUiState.fitExportStatusMessageState.value)
        assertEquals(true, documentsFolderUiState.fitExportStatusIsErrorState.value)
        assertEquals("fingerprint", summaryFitUiState.lastAutoExportSummaryFingerprint)

        statePort.summary = null
        statePort.connectingTimeoutMessage = null
        statePort.fitPendingSnapshot = null
        statePort.fitStatusMessage = null
        statePort.fitStatusIsError = false
        statePort.lastAutoExportSummaryFingerprint = null

        assertNull(uiState.summary.value)
        assertNull(uiState.connectingTimeoutMessage.value)
        assertNull(documentsFolderUiState.pendingFitExportSnapshot)
        assertNull(documentsFolderUiState.fitExportStatusMessageState.value)
        assertFalse(documentsFolderUiState.fitExportStatusIsErrorState.value)
        assertNull(summaryFitUiState.lastAutoExportSummaryFingerprint)
    }

    private fun sampleSummary(startMillis: Long = 1_000L): SessionSummary {
        return SessionSummary(
            startTimestampMillis = startMillis,
            stopTimestampMillis = startMillis + 2_000L,
            durationSeconds = 2,
            actualTss = 12.5,
            avgPower = 200,
            maxPower = 300,
            avgCadence = 85,
            maxCadence = 96,
            avgHeartRate = 140,
            maxHeartRate = 155,
            distanceMeters = 1_500,
            totalEnergyKcal = 40,
        )
    }

    private fun sampleSnapshot(summary: SessionSummary): SessionExportSnapshot {
        return SessionExportSnapshot(
            summary = summary,
            timeline = emptyList(),
        )
    }
}

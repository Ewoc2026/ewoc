package com.example.ergometerapp

import android.net.Uri
import com.example.ergometerapp.session.SessionSummary
import com.example.ergometerapp.session.export.SessionExportSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito

class DocumentsFolderUiStateTest {

    @Test
    fun documentsFolderAndFitExportPortsShareBackingState() {
        val uiState = DocumentsFolderUiState()
        val treeUri = Mockito.mock(Uri::class.java)
        val snapshot = sampleSnapshot()

        uiState.documentsFolderStatePort.treeUri = treeUri
        uiState.documentsFolderStatePort.ready = true
        uiState.documentsFolderStatePort.accessLost = false
        uiState.documentsFolderStatePort.summary = "Ready"
        uiState.documentsFolderStatePort.statusMessage = "Folder updated"
        uiState.documentsFolderStatePort.statusIsError = false
        uiState.documentsFolderStatePort.workoutFiles += DocumentsFolderWorkoutOption(
            uriString = "content://documents/tree/workouts/tempo",
            displayName = "tempo.zwo",
        )
        uiState.summaryFitExportStatePort.pendingSnapshot = snapshot
        uiState.summaryFitExportStatePort.statusMessage = "FIT exported"
        uiState.summaryFitExportStatePort.statusIsError = true

        assertSame(treeUri, uiState.treeUri)
        assertTrue(uiState.readyState.value)
        assertFalse(uiState.accessLostState.value)
        assertEquals("Ready", uiState.summaryState.value)
        assertEquals("Folder updated", uiState.statusMessageState.value)
        assertFalse(uiState.statusIsErrorState.value)
        assertEquals(1, uiState.workoutFilesState.size)
        assertSame(snapshot, uiState.pendingFitExportSnapshot)
        assertEquals("FIT exported", uiState.summaryFitShareStatePort.statusMessage)
        assertTrue(uiState.summaryFitShareStatePort.statusIsError)
    }

    @Test
    fun clearFitExportStatus_resetsPendingSnapshotAndStatusOnly() {
        val uiState = DocumentsFolderUiState()
        uiState.summaryFitExportStatePort.pendingSnapshot = sampleSnapshot()
        uiState.summaryFitExportStatePort.statusMessage = "failed"
        uiState.summaryFitExportStatePort.statusIsError = true
        uiState.documentsFolderStatePort.summary = "Folder ready"

        uiState.clearFitExportStatus()

        assertNull(uiState.pendingFitExportSnapshot)
        assertNull(uiState.fitExportStatusMessageState.value)
        assertFalse(uiState.fitExportStatusIsErrorState.value)
        assertEquals("Folder ready", uiState.summaryState.value)
    }

    private fun sampleSnapshot(): SessionExportSnapshot {
        return SessionExportSnapshot(
            summary = SessionSummary(
                startTimestampMillis = 1_000L,
                stopTimestampMillis = 3_000L,
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
            ),
            timeline = emptyList(),
        )
    }
}

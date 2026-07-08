package io.github.ewoc2026.ewoc

import android.net.Uri
import io.github.ewoc2026.ewoc.session.SessionSummary
import io.github.ewoc2026.ewoc.session.export.FitExportFailureReason
import io.github.ewoc2026.ewoc.session.export.FitExportResult
import io.github.ewoc2026.ewoc.session.export.SessionExportSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito

class SummaryFitExportCoordinatorTest {

    @Test
    fun prepareExport_clearsPendingSnapshotAndSetsErrorWhenSummaryMissing() {
        val statePort = FakeSummaryFitExportStatePort(
            pendingSnapshot = sampleSnapshot(),
            statusMessage = "old",
            statusIsError = false,
        )
        val coordinator = coordinator()

        val result = coordinator.prepareExport(
            snapshot = null,
            statePort = statePort,
        )

        assertNull(result)
        assertNull(statePort.pendingSnapshot)
        assertEquals("No summary available.", statePort.statusMessage)
        assertTrue(statePort.statusIsError)
    }

    @Test
    fun prepareExport_stagesPendingSnapshotAndClearsPreviousStatus() {
        val snapshot = sampleSnapshot()
        val statePort = FakeSummaryFitExportStatePort(
            statusMessage = "stale",
            statusIsError = true,
        )
        val coordinator = coordinator(
            suggestFileName = { "session.fit" },
        )

        val result = coordinator.prepareExport(
            snapshot = snapshot,
            statePort = statePort,
        )

        assertEquals("session.fit", result)
        assertSame(snapshot, statePort.pendingSnapshot)
        assertNull(statePort.statusMessage)
        assertFalse(statePort.statusIsError)
    }

    @Test
    fun tryExportPendingToDocumentsFolder_reportsSuccessAndClearsPendingSnapshot() {
        val folderUri = Mockito.mock(Uri::class.java)
        val targetUri = Mockito.mock(Uri::class.java)
        val snapshot = sampleSnapshot()
        val statePort = FakeSummaryFitExportStatePort(
            pendingSnapshot = snapshot,
        )
        var clearedDocumentsStatus = false
        var exportedSnapshot: SessionExportSnapshot? = null
        val coordinator = coordinator(
            prepareTreeWrite = {
                DocumentsFolderWritePreparation(
                    decision = DocumentsFolderWriteDecision.CONTINUE_TREE_WRITE,
                    folderUri = folderUri,
                )
            },
            prepareWriteTarget = { _, _, _, _, _, _ ->
                DocumentsFolderWriteTargetPreparation(
                    decision = DocumentsFolderWriteDecision.CONTINUE_TREE_WRITE,
                    targetUri = targetUri,
                )
            },
            resolvePostWriteDecision = { _, _, _, _ ->
                DocumentsFolderWriteDecision.CONTINUE_TREE_WRITE
            },
            exportToUri = { uri, snapshot ->
                assertSame(targetUri, uri)
                exportedSnapshot = snapshot
                FitExportResult.Success
            },
            clearDocumentsFolderStatus = {
                clearedDocumentsStatus = true
            },
        )

        val handled = coordinator.tryExportPendingToDocumentsFolder(
            suggestedFileName = "session.fit",
            allowPickerFallback = true,
            mimeType = "application/octet-stream",
            statePort = statePort,
        )

        assertTrue(handled)
        assertSame(snapshot, exportedSnapshot)
        assertNull(statePort.pendingSnapshot)
        assertEquals("FIT export complete.", statePort.statusMessage)
        assertFalse(statePort.statusIsError)
        assertTrue(clearedDocumentsStatus)
    }

    @Test
    fun tryExportPendingToDocumentsFolder_mapsTerminalExportFailureAndClearsPendingSnapshot() {
        val folderUri = Mockito.mock(Uri::class.java)
        val targetUri = Mockito.mock(Uri::class.java)
        val snapshot = sampleSnapshot()
        val statePort = FakeSummaryFitExportStatePort(
            pendingSnapshot = snapshot,
        )
        val failure = FitExportResult.Failure(FitExportFailureReason.INVALID_TIMESTAMPS)
        val coordinator = coordinator(
            prepareTreeWrite = {
                DocumentsFolderWritePreparation(
                    decision = DocumentsFolderWriteDecision.CONTINUE_TREE_WRITE,
                    folderUri = folderUri,
                )
            },
            prepareWriteTarget = { _, _, _, _, _, _ ->
                DocumentsFolderWriteTargetPreparation(
                    decision = DocumentsFolderWriteDecision.CONTINUE_TREE_WRITE,
                    targetUri = targetUri,
                )
            },
            resolvePostWriteDecision = { _, _, _, _ ->
                DocumentsFolderWriteDecision.CONTINUE_TREE_WRITE
            },
            exportToUri = { _, _ -> failure },
        )

        val handled = coordinator.tryExportPendingToDocumentsFolder(
            suggestedFileName = "session.fit",
            allowPickerFallback = true,
            mimeType = "application/octet-stream",
            statePort = statePort,
        )

        assertTrue(handled)
        assertNull(statePort.pendingSnapshot)
        assertEquals("export:INVALID_TIMESTAMPS", statePort.statusMessage)
        assertTrue(statePort.statusIsError)
    }

    @Test
    fun tryExportPendingToDocumentsFolder_returnsFalseWhenPickerFallbackIsRequired() {
        val folderUri = Mockito.mock(Uri::class.java)
        val snapshot = sampleSnapshot()
        val statePort = FakeSummaryFitExportStatePort(
            pendingSnapshot = snapshot,
        )
        val coordinator = coordinator(
            prepareTreeWrite = {
                DocumentsFolderWritePreparation(
                    decision = DocumentsFolderWriteDecision.CONTINUE_TREE_WRITE,
                    folderUri = folderUri,
                )
            },
            prepareWriteTarget = { _, _, _, _, _, _ ->
                DocumentsFolderWriteTargetPreparation(
                    decision = DocumentsFolderWriteDecision.REQUIRE_PICKER_FALLBACK,
                    targetUri = null,
                )
            },
        )

        val handled = coordinator.tryExportPendingToDocumentsFolder(
            suggestedFileName = "session.fit",
            allowPickerFallback = true,
            mimeType = "application/octet-stream",
            statePort = statePort,
        )

        assertFalse(handled)
        assertSame(snapshot, statePort.pendingSnapshot)
        assertNull(statePort.statusMessage)
    }

    @Test
    fun completeDocumentExport_clearsPendingSnapshotWhenPickerIsCancelled() {
        val statePort = FakeSummaryFitExportStatePort(
            pendingSnapshot = sampleSnapshot(),
            statusMessage = "old",
            statusIsError = true,
        )
        val coordinator = coordinator()

        coordinator.completeDocumentExport(
            targetUri = null,
            fallbackSnapshot = sampleSnapshot(),
            statePort = statePort,
        )

        assertNull(statePort.pendingSnapshot)
        assertEquals("old", statePort.statusMessage)
        assertTrue(statePort.statusIsError)
    }

    @Test
    fun completeDocumentExport_usesPendingSnapshotWhenAvailableAndReportsSuccess() {
        val targetUri = Mockito.mock(Uri::class.java)
        val pendingSnapshot = sampleSnapshot()
        val fallbackSnapshot = sampleSnapshot(startMillis = 5_000L)
        val statePort = FakeSummaryFitExportStatePort(
            pendingSnapshot = pendingSnapshot,
        )
        var exportedSnapshot: SessionExportSnapshot? = null
        var clearedDocumentsStatus = false
        val coordinator = coordinator(
            exportToUri = { uri, snapshot ->
                assertSame(targetUri, uri)
                exportedSnapshot = snapshot
                FitExportResult.Success
            },
            clearDocumentsFolderStatus = {
                clearedDocumentsStatus = true
            },
        )

        coordinator.completeDocumentExport(
            targetUri = targetUri,
            fallbackSnapshot = fallbackSnapshot,
            statePort = statePort,
        )

        assertSame(pendingSnapshot, exportedSnapshot)
        assertNull(statePort.pendingSnapshot)
        assertEquals("FIT export complete.", statePort.statusMessage)
        assertFalse(statePort.statusIsError)
        assertTrue(clearedDocumentsStatus)
    }

    @Test
    fun completeDocumentExport_usesFallbackSnapshotWhenPendingSnapshotMissing() {
        val targetUri = Mockito.mock(Uri::class.java)
        val fallbackSnapshot = sampleSnapshot(startMillis = 7_000L)
        val statePort = FakeSummaryFitExportStatePort()
        var exportedSnapshot: SessionExportSnapshot? = null
        val coordinator = coordinator(
            exportToUri = { _, snapshot ->
                exportedSnapshot = snapshot
                FitExportResult.Success
            },
        )

        coordinator.completeDocumentExport(
            targetUri = targetUri,
            fallbackSnapshot = fallbackSnapshot,
            statePort = statePort,
        )

        assertSame(fallbackSnapshot, exportedSnapshot)
        assertEquals("FIT export complete.", statePort.statusMessage)
        assertFalse(statePort.statusIsError)
    }

    @Test
    fun completeDocumentExport_mapsFailureToErrorStatus() {
        val targetUri = Mockito.mock(Uri::class.java)
        val statePort = FakeSummaryFitExportStatePort(
            pendingSnapshot = sampleSnapshot(),
        )
        val coordinator = coordinator(
            exportToUri = { _, _ ->
                FitExportResult.Failure(
                    reason = FitExportFailureReason.WRITE_FAILED,
                    detail = "disk full",
                )
            },
        )

        coordinator.completeDocumentExport(
            targetUri = targetUri,
            fallbackSnapshot = null,
            statePort = statePort,
        )

        assertNull(statePort.pendingSnapshot)
        assertEquals("export:WRITE_FAILED", statePort.statusMessage)
        assertTrue(statePort.statusIsError)
    }

    private fun coordinator(
        suggestFileName: (SessionExportSnapshot) -> String = { "session.fit" },
        prepareTreeWrite: (onFolderUnavailable: (String) -> Unit) -> DocumentsFolderWritePreparation = {
            DocumentsFolderWritePreparation(
                decision = DocumentsFolderWriteDecision.COMPLETE,
                folderUri = null,
            )
        },
        prepareWriteTarget: (
            folderUri: Uri,
            suggestedFileName: String,
            requiredExtension: String,
            mimeType: String,
            allowPickerFallback: Boolean,
            onPickerFallbackBlocked: (() -> Unit)?,
        ) -> DocumentsFolderWriteTargetPreparation = { _, _, _, _, _, _ ->
            DocumentsFolderWriteTargetPreparation(
                decision = DocumentsFolderWriteDecision.COMPLETE,
                targetUri = null,
            )
        },
        resolvePostWriteDecision: (
            result: FitExportResult,
            allowPickerFallback: Boolean,
            shouldFallback: (FitExportResult) -> Boolean,
            onPickerFallbackBlocked: (() -> Unit)?,
        ) -> DocumentsFolderWriteDecision = { _, _, _, _ ->
            DocumentsFolderWriteDecision.CONTINUE_TREE_WRITE
        },
        exportToUri: (Uri, SessionExportSnapshot?) -> FitExportResult = { _, _ ->
            FitExportResult.Success
        },
        clearDocumentsFolderStatus: () -> Unit = {},
    ): SummaryFitExportCoordinator {
        return SummaryFitExportCoordinator(
            suggestFileName = suggestFileName,
            noSummaryMessage = { "No summary available." },
            exportFailureMessage = { failure -> "export:${failure.reason}" },
            exportSuccessMessage = { "FIT export complete." },
            autoSaveFailedMessage = { "Auto-save failed." },
            prepareTreeWrite = prepareTreeWrite,
            prepareWriteTarget = prepareWriteTarget,
            resolvePostWriteDecision = resolvePostWriteDecision,
            exportToUri = exportToUri,
            clearDocumentsFolderStatus = clearDocumentsFolderStatus,
        )
    }

    private fun sampleSnapshot(startMillis: Long = 1_000L): SessionExportSnapshot {
        return SessionExportSnapshot(
            summary = SessionSummary(
                startTimestampMillis = startMillis,
                stopTimestampMillis = startMillis + 1_000L,
                durationSeconds = 1,
                actualTss = 42.0,
                avgPower = 210,
                maxPower = 420,
                avgCadence = 88,
                maxCadence = 101,
                avgHeartRate = 145,
                maxHeartRate = 168,
                distanceMeters = 12_345,
                totalEnergyKcal = 678,
            ),
            timeline = emptyList(),
        )
    }

    private class FakeSummaryFitExportStatePort(
        override var pendingSnapshot: SessionExportSnapshot? = null,
        override var statusMessage: String? = null,
        override var statusIsError: Boolean = false,
    ) : SummaryFitExportStatePort
}

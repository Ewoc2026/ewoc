package com.example.ergometerapp

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.Mockito

class DocumentsFolderWriteCoordinatorTest {
    @Test
    fun prepareTreeWrite_returnsCompleteAndReportsNotConfiguredWhenFolderIsMissing() {
        val record = WritePolicyRecord()
        val coordinator = buildCoordinator(
            record = record,
            ensureFolderReadyForFileOperations = { null },
            isFolderAccessLost = { false },
        )

        val preparation = coordinator.prepareTreeWrite { message ->
            record.folderUnavailableMessages += message
        }

        assertEquals(DocumentsFolderWriteDecision.COMPLETE, preparation.decision)
        assertNull(preparation.folderUri)
        assertEquals(listOf("documents-not-configured"), record.folderUnavailableMessages)
        assertEquals(emptyList<StatusUpdate>(), record.statusUpdates)
    }

    @Test
    fun prepareTreeWrite_returnsCompleteAndReportsAccessLostWhenFolderPermissionIsRevoked() {
        val record = WritePolicyRecord()
        val coordinator = buildCoordinator(
            record = record,
            ensureFolderReadyForFileOperations = { null },
            isFolderAccessLost = { true },
        )

        val preparation = coordinator.prepareTreeWrite { message ->
            record.folderUnavailableMessages += message
        }

        assertEquals(DocumentsFolderWriteDecision.COMPLETE, preparation.decision)
        assertNull(preparation.folderUri)
        assertEquals(listOf("documents-access-lost"), record.folderUnavailableMessages)
        assertEquals(emptyList<StatusUpdate>(), record.statusUpdates)
    }

    @Test
    fun prepareTreeWrite_returnsPickerFallbackWhenDebugWriteFailureIsForced() {
        val treeUri = Mockito.mock(Uri::class.java)
        val record = WritePolicyRecord()
        val coordinator = buildCoordinator(
            record = record,
            ensureFolderReadyForFileOperations = { treeUri },
            consumeDebugWriteFailureOnce = { true },
        )

        val preparation = coordinator.prepareTreeWrite { message ->
            record.folderUnavailableMessages += message
        }

        assertEquals(DocumentsFolderWriteDecision.REQUIRE_PICKER_FALLBACK, preparation.decision)
        assertNull(preparation.folderUri)
        assertEquals(emptyList<String>(), record.folderUnavailableMessages)
        assertEquals(
            listOf(StatusUpdate(message = "documents-write-failed", isError = true)),
            record.statusUpdates,
        )
    }

    @Test
    fun resolveFallbackDecision_returnsPickerFallbackWhenAllowed() {
        val record = WritePolicyRecord()
        val coordinator = buildCoordinator(record = record)

        val decision = coordinator.resolveFallbackDecision(
            shouldFallback = true,
            allowPickerFallback = true,
        )

        assertEquals(DocumentsFolderWriteDecision.REQUIRE_PICKER_FALLBACK, decision)
        assertEquals(
            listOf(StatusUpdate(message = "documents-write-failed", isError = true)),
            record.statusUpdates,
        )
    }

    @Test
    fun resolveFallbackDecision_returnsCompleteWhenFallbackBlockedAndKeepsSideEffectOrder() {
        val record = WritePolicyRecord()
        val coordinator = buildCoordinator(record = record)

        val decision = coordinator.resolveFallbackDecision(
            shouldFallback = true,
            allowPickerFallback = false,
            onPickerFallbackBlocked = {
                record.events += "picker-blocked"
            },
        )

        assertEquals(DocumentsFolderWriteDecision.COMPLETE, decision)
        assertEquals(
            listOf(StatusUpdate(message = "documents-write-failed", isError = true)),
            record.statusUpdates,
        )
        assertEquals(
            listOf("documents-status-updated", "picker-blocked"),
            record.events,
        )
    }

    @Test
    fun resolveFallbackDecision_returnsContinueWhenFallbackIsNotNeeded() {
        val record = WritePolicyRecord()
        val coordinator = buildCoordinator(record = record)

        val decision = coordinator.resolveFallbackDecision(
            shouldFallback = false,
            allowPickerFallback = false,
            onPickerFallbackBlocked = {
                record.events += "picker-blocked"
            },
        )

        assertEquals(DocumentsFolderWriteDecision.CONTINUE_TREE_WRITE, decision)
        assertEquals(emptyList<StatusUpdate>(), record.statusUpdates)
        assertEquals(emptyList<String>(), record.events)
    }

    private fun buildCoordinator(
        record: WritePolicyRecord,
        ensureFolderReadyForFileOperations: () -> Uri? = { Mockito.mock(Uri::class.java) },
        isFolderAccessLost: () -> Boolean = { false },
        consumeDebugWriteFailureOnce: () -> Boolean = { false },
    ): DocumentsFolderWriteCoordinator {
        return DocumentsFolderWriteCoordinator(
            ensureFolderReadyForFileOperations = ensureFolderReadyForFileOperations,
            isFolderAccessLost = isFolderAccessLost,
            consumeDebugWriteFailureOnce = consumeDebugWriteFailureOnce,
            setDocumentsFolderStatus = { message, isError ->
                record.events += "documents-status-updated"
                record.statusUpdates += StatusUpdate(message = message, isError = isError)
            },
            documentsFolderAccessLostMessage = { "documents-access-lost" },
            documentsFolderNotConfiguredMessage = { "documents-not-configured" },
            documentsFolderWriteFailedMessage = { "documents-write-failed" },
        )
    }

    private data class StatusUpdate(
        val message: String?,
        val isError: Boolean,
    )

    private class WritePolicyRecord {
        val folderUnavailableMessages = mutableListOf<String>()
        val statusUpdates = mutableListOf<StatusUpdate>()
        val events = mutableListOf<String>()
    }
}

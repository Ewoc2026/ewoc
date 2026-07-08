package io.github.ewoc2026.ewoc

import android.net.Uri
import io.github.ewoc2026.ewoc.session.export.FitExportFailureReason
import io.github.ewoc2026.ewoc.session.export.FitExportResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.Mockito

class DocumentsFolderWriteExecutionCoordinatorTest {
    @Test
    fun prepareWriteTarget_enforcesExtensionAndPassesMimeTypeToSafTargetCreation() {
        val folderUri = Mockito.mock(Uri::class.java)
        val targetUri = Mockito.mock(Uri::class.java)
        var capturedTreeUri: Uri? = null
        var capturedPreferredFileName: String? = null
        var capturedMimeType: String? = null
        var capturedExistingFilePolicy: DocumentsFolderExistingFilePolicy? = null
        val fallbackCalls = mutableListOf<FallbackCall>()
        val coordinator = DocumentsFolderWriteExecutionCoordinator(
            createWritableDocumentUri = { treeUri, preferredFileName, mimeType, existingFilePolicy ->
                capturedTreeUri = treeUri
                capturedPreferredFileName = preferredFileName
                capturedMimeType = mimeType
                capturedExistingFilePolicy = existingFilePolicy
                targetUri
            },
            resolveFallbackDecision = { shouldFallback, allowPickerFallback, _ ->
                fallbackCalls += FallbackCall(
                    shouldFallback = shouldFallback,
                    allowPickerFallback = allowPickerFallback,
                )
                DocumentsFolderWriteDecision.CONTINUE_TREE_WRITE
            },
        )

        val preparation = coordinator.prepareWriteTarget(
            folderUri = folderUri,
            suggestedFileName = "summary",
            requiredExtension = ".fit",
            mimeType = "application/octet-stream",
            allowPickerFallback = true,
        )

        assertEquals(folderUri, capturedTreeUri)
        assertEquals("summary.fit", capturedPreferredFileName)
        assertEquals("application/octet-stream", capturedMimeType)
        assertEquals(
            DocumentsFolderExistingFilePolicy.CREATE_COPY_ON_CONFLICT,
            capturedExistingFilePolicy,
        )
        assertEquals(
            listOf(
                FallbackCall(
                    shouldFallback = false,
                    allowPickerFallback = true,
                ),
            ),
            fallbackCalls,
        )
        assertEquals(DocumentsFolderWriteDecision.CONTINUE_TREE_WRITE, preparation.decision)
        assertEquals(targetUri, preparation.targetUri)
    }

    @Test
    fun prepareWriteTarget_returnsPickerFallbackWhenTargetCreationFailsAndFallbackIsAllowed() {
        val folderUri = Mockito.mock(Uri::class.java)
        val fallbackCalls = mutableListOf<FallbackCall>()
        val coordinator = DocumentsFolderWriteExecutionCoordinator(
            createWritableDocumentUri = { _, _, _, _ ->
                null
            },
            resolveFallbackDecision = { shouldFallback, allowPickerFallback, _ ->
                fallbackCalls += FallbackCall(
                    shouldFallback = shouldFallback,
                    allowPickerFallback = allowPickerFallback,
                )
                DocumentsFolderWriteDecision.REQUIRE_PICKER_FALLBACK
            },
        )

        val preparation = coordinator.prepareWriteTarget(
            folderUri = folderUri,
            suggestedFileName = "session.fit",
            requiredExtension = ".fit",
            mimeType = "application/octet-stream",
            allowPickerFallback = true,
        )

        assertEquals(
            listOf(
                FallbackCall(
                    shouldFallback = true,
                    allowPickerFallback = true,
                ),
            ),
            fallbackCalls,
        )
        assertEquals(DocumentsFolderWriteDecision.REQUIRE_PICKER_FALLBACK, preparation.decision)
        assertNull(preparation.targetUri)
    }

    @Test
    fun prepareWriteTarget_invokesBlockedCallbackWhenFallbackIsNotAllowed() {
        val folderUri = Mockito.mock(Uri::class.java)
        val events = mutableListOf<String>()
        val coordinator = DocumentsFolderWriteExecutionCoordinator(
            createWritableDocumentUri = { _, _, _, _ ->
                null
            },
            resolveFallbackDecision = { shouldFallback, allowPickerFallback, onPickerFallbackBlocked ->
                if (shouldFallback && !allowPickerFallback) {
                    onPickerFallbackBlocked?.invoke()
                    DocumentsFolderWriteDecision.COMPLETE
                } else {
                    DocumentsFolderWriteDecision.CONTINUE_TREE_WRITE
                }
            },
        )

        val preparation = coordinator.prepareWriteTarget(
            folderUri = folderUri,
            suggestedFileName = "session.fit",
            requiredExtension = ".fit",
            mimeType = "application/octet-stream",
            allowPickerFallback = false,
            onPickerFallbackBlocked = {
                events += "picker-blocked"
            },
        )

        assertEquals(listOf("picker-blocked"), events)
        assertEquals(DocumentsFolderWriteDecision.COMPLETE, preparation.decision)
        assertNull(preparation.targetUri)
    }

    @Test
    fun resolvePostWriteDecision_routesResultToPolicyAndFallbackResolver() {
        val events = mutableListOf<String>()
        val coordinator = DocumentsFolderWriteExecutionCoordinator(
            createWritableDocumentUri = { _, _, _, _ ->
                Mockito.mock(Uri::class.java)
            },
            resolveFallbackDecision = { shouldFallback, allowPickerFallback, onPickerFallbackBlocked ->
                events += "resolve:$shouldFallback:$allowPickerFallback"
                if (shouldFallback && !allowPickerFallback) {
                    onPickerFallbackBlocked?.invoke()
                    DocumentsFolderWriteDecision.COMPLETE
                } else {
                    DocumentsFolderWriteDecision.CONTINUE_TREE_WRITE
                }
            },
        )

        val failureResult: FitExportResult =
            FitExportResult.Failure(FitExportFailureReason.WRITE_FAILED)
        val decision = coordinator.resolvePostWriteDecision(
            result = failureResult,
            allowPickerFallback = false,
            shouldFallback = { exportResult ->
                exportResult is FitExportResult.Failure &&
                    exportResult.reason == FitExportFailureReason.WRITE_FAILED
            },
            onPickerFallbackBlocked = {
                events += "blocked"
            },
        )

        assertEquals(DocumentsFolderWriteDecision.COMPLETE, decision)
        assertEquals(
            listOf(
                "resolve:true:false",
                "blocked",
            ),
            events,
        )
    }

    @Test
    fun prepareWriteTarget_passesOverwritePolicyWhenRequested() {
        val folderUri = Mockito.mock(Uri::class.java)
        val targetUri = Mockito.mock(Uri::class.java)
        var capturedExistingFilePolicy: DocumentsFolderExistingFilePolicy? = null
        val coordinator = DocumentsFolderWriteExecutionCoordinator(
            createWritableDocumentUri = { _, _, _, existingFilePolicy ->
                capturedExistingFilePolicy = existingFilePolicy
                targetUri
            },
            resolveFallbackDecision = { _, _, _ ->
                DocumentsFolderWriteDecision.CONTINUE_TREE_WRITE
            },
        )

        val preparation = coordinator.prepareWriteTarget(
            folderUri = folderUri,
            suggestedFileName = "workout.zwo",
            requiredExtension = ".zwo",
            mimeType = "application/octet-stream",
            allowPickerFallback = true,
            existingFilePolicy = DocumentsFolderExistingFilePolicy.OVERWRITE_EXISTING,
        )

        assertEquals(
            DocumentsFolderExistingFilePolicy.OVERWRITE_EXISTING,
            capturedExistingFilePolicy,
        )
        assertEquals(DocumentsFolderWriteDecision.CONTINUE_TREE_WRITE, preparation.decision)
        assertEquals(targetUri, preparation.targetUri)
    }

    private data class FallbackCall(
        val shouldFallback: Boolean,
        val allowPickerFallback: Boolean,
    )
}

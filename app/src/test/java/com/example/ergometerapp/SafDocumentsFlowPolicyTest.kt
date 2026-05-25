package com.example.ergometerapp

import com.example.ergometerapp.session.export.FitExportFailureReason
import com.example.ergometerapp.session.export.FitExportResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafDocumentsFlowPolicyTest {
    @Test
    fun resolveDocumentsFolderAccessState_returnsNotConfiguredWhenNoTreeIsSelected() {
        val state = resolveDocumentsFolderAccessState(
            hasSelectedTreeUri = false,
            hasReadWriteAccess = false,
        )

        assertEquals(DocumentsFolderAccessState.NOT_CONFIGURED, state)
    }

    @Test
    fun resolveDocumentsFolderAccessState_returnsAccessLostWhenTreePermissionWasRevoked() {
        val state = resolveDocumentsFolderAccessState(
            hasSelectedTreeUri = true,
            hasReadWriteAccess = false,
        )

        assertEquals(DocumentsFolderAccessState.ACCESS_LOST, state)
    }

    @Test
    fun resolveDocumentsFolderAccessState_returnsReadyWhenTreeHasReadWriteAccess() {
        val state = resolveDocumentsFolderAccessState(
            hasSelectedTreeUri = true,
            hasReadWriteAccess = true,
        )

        assertEquals(DocumentsFolderAccessState.READY, state)
    }

    @Test
    fun shouldUseCreateDocumentFallbackForWorkoutTreeWrite_returnsTrueWhenTargetCreateFails() {
        val shouldFallback = shouldUseCreateDocumentFallbackForWorkoutTreeWrite(
            targetUriCreated = false,
            writeSucceeded = null,
        )

        assertTrue(shouldFallback)
    }

    @Test
    fun shouldUseCreateDocumentFallbackForWorkoutTreeWrite_returnsTrueWhenTreeWriteFails() {
        val shouldFallback = shouldUseCreateDocumentFallbackForWorkoutTreeWrite(
            targetUriCreated = true,
            writeSucceeded = false,
        )

        assertTrue(shouldFallback)
    }

    @Test
    fun shouldUseCreateDocumentFallbackForWorkoutTreeWrite_returnsFalseWhenTreeWriteSucceeds() {
        val shouldFallback = shouldUseCreateDocumentFallbackForWorkoutTreeWrite(
            targetUriCreated = true,
            writeSucceeded = true,
        )

        assertFalse(shouldFallback)
    }

    @Test
    fun shouldUseCreateDocumentFallbackForFitTreeExport_returnsTrueWhenTargetCreateFails() {
        val shouldFallback = shouldUseCreateDocumentFallbackForFitTreeExport(
            targetUriCreated = false,
            exportResult = null,
        )

        assertTrue(shouldFallback)
    }

    @Test
    fun shouldUseCreateDocumentFallbackForFitTreeExport_returnsTrueForOutputStreamFailure() {
        val shouldFallback = shouldUseCreateDocumentFallbackForFitTreeExport(
            targetUriCreated = true,
            exportResult = FitExportResult.Failure(FitExportFailureReason.OUTPUT_STREAM_UNAVAILABLE),
        )

        assertTrue(shouldFallback)
    }

    @Test
    fun shouldUseCreateDocumentFallbackForFitTreeExport_returnsTrueForWriteFailure() {
        val shouldFallback = shouldUseCreateDocumentFallbackForFitTreeExport(
            targetUriCreated = true,
            exportResult = FitExportResult.Failure(FitExportFailureReason.WRITE_FAILED),
        )

        assertTrue(shouldFallback)
    }

    @Test
    fun shouldUseCreateDocumentFallbackForFitTreeExport_returnsFalseForNoSummaryFailure() {
        val shouldFallback = shouldUseCreateDocumentFallbackForFitTreeExport(
            targetUriCreated = true,
            exportResult = FitExportResult.Failure(FitExportFailureReason.NO_SUMMARY),
        )

        assertFalse(shouldFallback)
    }

    @Test
    fun shouldUseCreateDocumentFallbackForFitTreeExport_returnsFalseForInvalidTimestampFailure() {
        val shouldFallback = shouldUseCreateDocumentFallbackForFitTreeExport(
            targetUriCreated = true,
            exportResult = FitExportResult.Failure(FitExportFailureReason.INVALID_TIMESTAMPS),
        )

        assertFalse(shouldFallback)
    }

    @Test
    fun shouldUseCreateDocumentFallbackForFitTreeExport_returnsFalseForSuccess() {
        val shouldFallback = shouldUseCreateDocumentFallbackForFitTreeExport(
            targetUriCreated = true,
            exportResult = FitExportResult.Success,
        )

        assertFalse(shouldFallback)
    }

    @Test
    fun resolveDocumentsFolderBindPermissionGranted_returnsTrueWhenReadFallbackSucceeds() {
        val granted = resolveDocumentsFolderBindPermissionGranted(
            readWritePersisted = false,
            readOnlyPersisted = true,
        )

        assertTrue(granted)
    }

    @Test
    fun resolveDocumentsFolderBindPermissionGranted_returnsFalseWhenBothPersistsFail() {
        val granted = resolveDocumentsFolderBindPermissionGranted(
            readWritePersisted = false,
            readOnlyPersisted = false,
        )

        assertFalse(granted)
    }
}

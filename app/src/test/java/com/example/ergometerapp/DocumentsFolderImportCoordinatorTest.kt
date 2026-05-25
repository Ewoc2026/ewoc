package com.example.ergometerapp

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito

class DocumentsFolderImportCoordinatorTest {
    private val refreshRequests = mutableListOf<Boolean>()
    private val releasedUris = mutableListOf<Uri>()
    private val importedUris = mutableListOf<Uri>()
    private var savedTreeUri: Uri? = null
    private var lastStatusMessage: String? = null
    private var lastStatusIsError: Boolean? = null
    private var nextReadWritePersistResult = false
    private var nextReadOnlyPersistResult = false
    private var nextReadyFolderUri: Uri? = null
    private var nextWorkoutFiles = emptyList<DocumentsFolderWorkoutOption>()
    private val parsedUrisByString = mutableMapOf<String, Uri?>()
    private val statePort = FakeDocumentsFolderStatePort()
    private val coordinator = DocumentsFolderImportCoordinator(
        statePort = statePort,
        persistReadWritePermission = { nextReadWritePersistResult },
        persistReadOnlyPermission = { nextReadOnlyPersistResult },
        releasePersistedPermission = { uri -> releasedUris += uri },
        saveTreeUri = { uri -> savedTreeUri = uri },
        refreshState = { clearStatusMessage -> refreshRequests += clearStatusMessage },
        ensureFolderReadyForFileOperations = { nextReadyFolderUri },
        listWorkoutFiles = { nextWorkoutFiles },
        parseUri = { uriString -> parsedUrisByString[uriString] },
        setDocumentsFolderStatus = { message, isError ->
            lastStatusMessage = message
            lastStatusIsError = isError
        },
        clearDocumentsFolderStatus = {
            lastStatusMessage = null
            lastStatusIsError = false
        },
        importWorkoutFromUri = { uri -> importedUris += uri },
        bindFailedMessage = { "bind failed" },
        boundMessage = { "bound" },
        noWorkoutFilesMessage = { "no workouts" },
    )

    @Test
    fun onFolderSelected_ignoresNullSelection() {
        coordinator.onFolderSelected(uri = null)

        assertTrue(refreshRequests.isEmpty())
        assertTrue(releasedUris.isEmpty())
        assertNull(savedTreeUri)
        assertNull(lastStatusMessage)
        assertNull(lastStatusIsError)
    }

    @Test
    fun onFolderSelected_reportsFailureWhenPersistPermissionCannotBeSaved() {
        val selectedUri = Mockito.mock(Uri::class.java)

        coordinator.onFolderSelected(selectedUri)

        assertEquals(listOf(false), refreshRequests)
        assertEquals("bind failed", lastStatusMessage)
        assertEquals(true, lastStatusIsError)
        assertNull(savedTreeUri)
        assertNull(statePort.treeUri)
    }

    @Test
    fun onFolderSelected_acceptsReadOnlyFallbackForImportFolderBinding() {
        val selectedUri = Mockito.mock(Uri::class.java)
        nextReadOnlyPersistResult = true

        coordinator.onFolderSelected(selectedUri)

        assertEquals(listOf(true), refreshRequests)
        assertEquals(selectedUri, statePort.treeUri)
        assertEquals(selectedUri, savedTreeUri)
        assertEquals("bound", lastStatusMessage)
        assertEquals(false, lastStatusIsError)
        assertTrue(releasedUris.isEmpty())
    }

    @Test
    fun onFolderSelected_releasesPreviousTreeAfterSuccessfulRebind() {
        val previousUri = Mockito.mock(Uri::class.java)
        val selectedUri = Mockito.mock(Uri::class.java)
        statePort.treeUri = previousUri
        nextReadWritePersistResult = true

        coordinator.onFolderSelected(selectedUri)

        assertEquals(listOf(previousUri), releasedUris)
        assertEquals(selectedUri, statePort.treeUri)
        assertEquals(selectedUri, savedTreeUri)
    }

    @Test
    fun onRefreshWorkoutFilesRequested_keepsStateUntouchedWhenFolderIsUnavailable() {
        statePort.workoutFiles += DocumentsFolderWorkoutOption(
            uriString = "content://documents/tree/existing",
            displayName = "existing.zwo",
        )

        coordinator.onRefreshWorkoutFilesRequested()

        assertEquals(1, statePort.workoutFiles.size)
        assertNull(lastStatusMessage)
        assertNull(lastStatusIsError)
    }

    @Test
    fun onRefreshWorkoutFilesRequested_populatesFilesAndClearsStatusWhenMatchesExist() {
        nextReadyFolderUri = Mockito.mock(Uri::class.java)
        nextWorkoutFiles = listOf(
            DocumentsFolderWorkoutOption(
                uriString = "content://documents/tree/workouts/tempo",
                displayName = "tempo.zwo",
            ),
            DocumentsFolderWorkoutOption(
                uriString = "content://documents/tree/workouts/endurance",
                displayName = "endurance.ewo",
            ),
        )
        lastStatusMessage = "stale"
        lastStatusIsError = true

        coordinator.onRefreshWorkoutFilesRequested()

        assertEquals(nextWorkoutFiles, statePort.workoutFiles)
        assertNull(lastStatusMessage)
        assertEquals(false, lastStatusIsError)
    }

    @Test
    fun onRefreshWorkoutFilesRequested_publishesNoFilesStatusWhenFolderIsEmpty() {
        nextReadyFolderUri = Mockito.mock(Uri::class.java)

        coordinator.onRefreshWorkoutFilesRequested()

        assertTrue(statePort.workoutFiles.isEmpty())
        assertEquals("no workouts", lastStatusMessage)
        assertEquals(false, lastStatusIsError)
    }

    @Test
    fun onWorkoutFileSelected_ignoresBlankUriString() {
        statePort.workoutFiles += DocumentsFolderWorkoutOption(
            uriString = "content://documents/tree/workouts/tempo",
            displayName = "tempo.zwo",
        )

        coordinator.onWorkoutFileSelected("  ")

        assertEquals(1, statePort.workoutFiles.size)
        assertTrue(importedUris.isEmpty())
    }

    @Test
    fun onWorkoutFileSelected_clearsOptionsAndStartsImport() {
        val selectedUri = Mockito.mock(Uri::class.java)
        val uriString = "content://documents/tree/workouts/tempo"
        parsedUrisByString[uriString] = selectedUri
        statePort.workoutFiles += DocumentsFolderWorkoutOption(
            uriString = uriString,
            displayName = "tempo.zwo",
        )

        coordinator.onWorkoutFileSelected(uriString)

        assertTrue(statePort.workoutFiles.isEmpty())
        assertEquals(listOf(selectedUri), importedUris)
    }

    private class FakeDocumentsFolderStatePort : DocumentsFolderStatePort {
        override var treeUri: Uri? = null
        override var ready: Boolean = false
        override var accessLost: Boolean = false
        override var summary: String? = null
        override var statusMessage: String? = null
        override var statusIsError: Boolean = false
        override val workoutFiles: MutableList<DocumentsFolderWorkoutOption> = mutableListOf()
    }
}

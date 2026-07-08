package io.github.ewoc2026.ewoc

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito

class DocumentsFolderCoordinatorTest {
    @Test
    fun refreshState_notConfiguredClearsReadyFlagsAndWorkoutFiles() {
        val state = FakeStatePort(
            treeUri = null,
            ready = true,
            accessLost = true,
            summary = "stale",
            statusMessage = "keep-me",
            statusIsError = true,
            workoutFiles = mutableListOf(
                DocumentsFolderWorkoutOption(
                    uriString = "content://documents/tree/fit",
                    displayName = "existing.zwo",
                ),
            ),
        )
        val coordinator = buildCoordinator(state = state)

        coordinator.refreshState(clearStatusMessage = false)

        assertFalse(state.ready)
        assertFalse(state.accessLost)
        assertEquals("folder-unconfigured", state.summary)
        assertTrue(state.workoutFiles.isEmpty())
        assertEquals("keep-me", state.statusMessage)
        assertTrue(state.statusIsError)
    }

    @Test
    fun refreshState_readyPopulatesSummaryAndPreservesWorkoutFiles() {
        val treeUri = Mockito.mock(Uri::class.java)
        val workoutOption = DocumentsFolderWorkoutOption(
            uriString = "content://documents/tree/fit/workout",
            displayName = "main.zwo",
        )
        val state = FakeStatePort(
            treeUri = treeUri,
            ready = false,
            accessLost = true,
            summary = null,
            statusMessage = null,
            statusIsError = false,
            workoutFiles = mutableListOf(workoutOption),
        )
        val coordinator = buildCoordinator(
            state = state,
            hasReadWriteAccess = { true },
            resolveFolderLabel = { "RideFolder" },
        )

        coordinator.refreshState(clearStatusMessage = false)

        assertTrue(state.ready)
        assertFalse(state.accessLost)
        assertEquals("folder-ready:RideFolder", state.summary)
        assertEquals(listOf(workoutOption), state.workoutFiles)
    }

    @Test
    fun refreshState_accessLostClearsWorkoutFilesAndStatusWhenRequested() {
        val treeUri = Mockito.mock(Uri::class.java)
        val state = FakeStatePort(
            treeUri = treeUri,
            ready = true,
            accessLost = false,
            summary = "stale",
            statusMessage = "stale-status",
            statusIsError = true,
            workoutFiles = mutableListOf(
                DocumentsFolderWorkoutOption(
                    uriString = "content://documents/tree/fit/workout",
                    displayName = "old.zwo",
                ),
            ),
        )
        val coordinator = buildCoordinator(
            state = state,
            hasReadWriteAccess = { false },
            resolveFolderLabel = { "RideFolder" },
        )

        coordinator.refreshState(clearStatusMessage = true)

        assertFalse(state.ready)
        assertTrue(state.accessLost)
        assertEquals("folder-access-lost", state.summary)
        assertTrue(state.workoutFiles.isEmpty())
        assertNull(state.statusMessage)
        assertFalse(state.statusIsError)
    }

    @Test
    fun ensureReadyForFileOperations_returnsNullAndSetsNotConfiguredStatus() {
        val state = FakeStatePort(
            treeUri = null,
            ready = true,
            accessLost = false,
            summary = "stale",
            statusMessage = null,
            statusIsError = false,
            workoutFiles = mutableListOf(),
        )
        val coordinator = buildCoordinator(state = state)

        val result = coordinator.ensureReadyForFileOperations()

        assertNull(result)
        assertEquals("folder-not-configured", state.statusMessage)
        assertTrue(state.statusIsError)
    }

    @Test
    fun ensureReadyForFileOperations_returnsNullAndSetsAccessLostStatus() {
        val treeUri = Mockito.mock(Uri::class.java)
        val state = FakeStatePort(
            treeUri = treeUri,
            ready = true,
            accessLost = false,
            summary = null,
            statusMessage = "previous",
            statusIsError = false,
            workoutFiles = mutableListOf(),
        )
        val coordinator = buildCoordinator(
            state = state,
            hasReadWriteAccess = { false },
        )

        val result = coordinator.ensureReadyForFileOperations()

        assertNull(result)
        assertEquals("folder-access-lost", state.statusMessage)
        assertTrue(state.statusIsError)
    }

    @Test
    fun ensureReadyForFileOperations_returnsTreeUriWhenFolderIsReady() {
        val treeUri = Mockito.mock(Uri::class.java)
        val state = FakeStatePort(
            treeUri = treeUri,
            ready = false,
            accessLost = true,
            summary = null,
            statusMessage = "keep",
            statusIsError = false,
            workoutFiles = mutableListOf(),
        )
        val coordinator = buildCoordinator(
            state = state,
            hasReadWriteAccess = { true },
            resolveFolderLabel = { "RideFolder" },
        )

        val result = coordinator.ensureReadyForFileOperations()

        assertEquals(treeUri, result)
        assertEquals("keep", state.statusMessage)
        assertFalse(state.statusIsError)
    }

    private fun buildCoordinator(
        state: FakeStatePort,
        hasReadWriteAccess: (Uri) -> Boolean = { false },
        resolveFolderLabel: (Uri) -> String? = { null },
    ): DocumentsFolderCoordinator {
        return DocumentsFolderCoordinator(
            statePort = state,
            hasReadWriteAccess = hasReadWriteAccess,
            resolveFolderLabel = resolveFolderLabel,
            resolveAccessState = ::resolveDocumentsFolderAccessState,
            notConfiguredStatusMessage = { "folder-not-configured" },
            accessLostStatusMessage = { "folder-access-lost" },
            unconfiguredSummary = { "folder-unconfigured" },
            accessLostSummary = { "folder-access-lost" },
            unknownFolderLabel = { "unknown-folder" },
            readySummary = { folderLabel -> "folder-ready:$folderLabel" },
        )
    }

    private data class FakeStatePort(
        override var treeUri: Uri?,
        override var ready: Boolean,
        override var accessLost: Boolean,
        override var summary: String?,
        override var statusMessage: String?,
        override var statusIsError: Boolean,
        override val workoutFiles: MutableList<DocumentsFolderWorkoutOption>,
    ) : DocumentsFolderStatePort
}

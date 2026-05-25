package com.example.ergometerapp

import android.net.Uri

/**
 * Coordinates documents-folder binding and folder-based workout import flows.
 *
 * Invariants:
 * - Binding accepts read-only persisted permission because folder import needs only read access.
 * - Successful rebind releases the previous persisted permission only after the new tree is secured.
 * - Refresh clears stale workout-file options before publishing the latest folder contents.
 */
internal class DocumentsFolderImportCoordinator(
    private val statePort: DocumentsFolderStatePort,
    private val persistReadWritePermission: (Uri) -> Boolean,
    private val persistReadOnlyPermission: (Uri) -> Boolean,
    private val releasePersistedPermission: (Uri) -> Unit,
    private val saveTreeUri: (Uri) -> Unit,
    private val refreshState: (clearStatusMessage: Boolean) -> Unit,
    private val ensureFolderReadyForFileOperations: () -> Uri?,
    private val listWorkoutFiles: (Uri) -> List<DocumentsFolderWorkoutOption>,
    private val parseUri: (String) -> Uri?,
    private val setDocumentsFolderStatus: (String?, Boolean) -> Unit,
    private val clearDocumentsFolderStatus: () -> Unit,
    private val importWorkoutFromUri: (Uri) -> Unit,
    private val bindFailedMessage: () -> String,
    private val boundMessage: () -> String,
    private val noWorkoutFilesMessage: () -> String,
) {
    fun onFolderSelected(uri: Uri?) {
        if (uri == null) return

        val readWritePersisted = persistReadWritePermission(uri)
        val readOnlyPersisted = if (!readWritePersisted) {
            persistReadOnlyPermission(uri)
        } else {
            false
        }
        val granted = resolveDocumentsFolderBindPermissionGranted(
            readWritePersisted = readWritePersisted,
            readOnlyPersisted = readOnlyPersisted,
        )
        if (!granted) {
            setDocumentsFolderStatus(bindFailedMessage(), true)
            refreshState(false)
            return
        }

        val previousUri = statePort.treeUri
        if (previousUri != null && previousUri != uri) {
            releasePersistedPermission(previousUri)
        }
        statePort.treeUri = uri
        saveTreeUri(uri)
        refreshState(true)
        setDocumentsFolderStatus(boundMessage(), false)
    }

    fun onRefreshWorkoutFilesRequested() {
        val folderUri = ensureFolderReadyForFileOperations() ?: return
        val mappedFiles = listWorkoutFiles(folderUri)
        statePort.workoutFiles.clear()
        statePort.workoutFiles.addAll(mappedFiles)
        if (mappedFiles.isEmpty()) {
            setDocumentsFolderStatus(noWorkoutFilesMessage(), false)
        } else {
            clearDocumentsFolderStatus()
        }
    }

    fun onWorkoutFileSelected(uriString: String) {
        if (uriString.isBlank()) return
        val uri = parseUri(uriString) ?: return
        statePort.workoutFiles.clear()
        importWorkoutFromUri(uri)
    }
}

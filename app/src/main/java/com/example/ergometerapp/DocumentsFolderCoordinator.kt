package com.example.ergometerapp

import android.net.Uri

/**
 * State bridge consumed by [DocumentsFolderCoordinator].
 *
 * Documents-folder access is mutable UI-facing state; routing reads/writes
 * through this port keeps permission-transition behavior unit-testable
 * without Compose runtime or MainViewModel coupling.
 */
internal interface DocumentsFolderStatePort {
    var treeUri: Uri?
    var ready: Boolean
    var accessLost: Boolean
    var summary: String?
    var statusMessage: String?
    var statusIsError: Boolean
    val workoutFiles: MutableList<DocumentsFolderWorkoutOption>
}

/**
 * Coordinates SAF documents-folder readiness refresh and status messaging.
 *
 * Invariants:
 * - `ensureReadyForFileOperations` always refreshes access state first.
 * - `NOT_CONFIGURED` and `ACCESS_LOST` states clear stale workout file lists.
 * - Status messages are cleared only when explicitly requested by caller.
 */
internal class DocumentsFolderCoordinator(
    private val statePort: DocumentsFolderStatePort,
    private val hasReadWriteAccess: (Uri) -> Boolean,
    private val resolveFolderLabel: (Uri) -> String?,
    private val resolveAccessState: (Boolean, Boolean) -> DocumentsFolderAccessState,
    private val notConfiguredStatusMessage: () -> String,
    private val accessLostStatusMessage: () -> String,
    private val unconfiguredSummary: () -> String,
    private val accessLostSummary: () -> String,
    private val unknownFolderLabel: () -> String,
    private val readySummary: (String) -> String,
) {
    fun ensureReadyForFileOperations(): Uri? {
        refreshState(clearStatusMessage = false)
        val folderUri = statePort.treeUri
        if (folderUri == null) {
            setStatus(message = notConfiguredStatusMessage(), isError = true)
            return null
        }
        if (!statePort.ready) {
            setStatus(message = accessLostStatusMessage(), isError = true)
            return null
        }
        return folderUri
    }

    fun refreshState(clearStatusMessage: Boolean) {
        val treeUri = statePort.treeUri
        val hasAccess = treeUri?.let(hasReadWriteAccess) == true
        when (
            resolveAccessState(
                treeUri != null,
                hasAccess,
            )
        ) {
            DocumentsFolderAccessState.NOT_CONFIGURED -> {
                statePort.ready = false
                statePort.accessLost = false
                statePort.summary = unconfiguredSummary()
                statePort.workoutFiles.clear()
            }
            DocumentsFolderAccessState.READY,
            DocumentsFolderAccessState.ACCESS_LOST,
            -> {
                val folderLabel = treeUri?.let(resolveFolderLabel) ?: unknownFolderLabel()
                statePort.summary = if (hasAccess) {
                    readySummary(folderLabel)
                } else {
                    accessLostSummary()
                }
                statePort.ready = hasAccess
                statePort.accessLost = !hasAccess
                if (!hasAccess) {
                    statePort.workoutFiles.clear()
                }
            }
        }
        if (clearStatusMessage) {
            clearStatus()
        }
    }

    fun setStatus(message: String?, isError: Boolean) {
        statePort.statusMessage = message
        statePort.statusIsError = isError
    }

    fun clearStatus() {
        statePort.statusMessage = null
        statePort.statusIsError = false
    }
}

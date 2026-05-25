package com.example.ergometerapp

import android.net.Uri
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.example.ergometerapp.session.export.SessionExportSnapshot

/**
 * Owns Documents-folder UI state plus the staged summary FIT export that shares the same routing.
 *
 * Keeping folder readiness, picker-visible workout options, and FIT export staging together avoids
 * coordinator ports drifting onto separate backing vars inside `MainViewModel` while preserving the
 * existing Documents-folder and create-document fallback behavior.
 */
internal class DocumentsFolderUiState {
    val readyState = mutableStateOf(false)
    val accessLostState = mutableStateOf(false)
    val summaryState = mutableStateOf<String?>(null)
    val statusMessageState = mutableStateOf<String?>(null)
    val statusIsErrorState = mutableStateOf(false)
    val workoutFilesState = mutableStateListOf<DocumentsFolderWorkoutOption>()

    val fitExportStatusMessageState = mutableStateOf<String?>(null)
    val fitExportStatusIsErrorState = mutableStateOf(false)

    var treeUri: Uri? = null
    var pendingFitExportSnapshot: SessionExportSnapshot? = null

    val documentsFolderStatePort = object : DocumentsFolderStatePort {
        override var treeUri: Uri?
            get() = this@DocumentsFolderUiState.treeUri
            set(value) {
                this@DocumentsFolderUiState.treeUri = value
            }

        override var ready: Boolean
            get() = readyState.value
            set(value) {
                readyState.value = value
            }

        override var accessLost: Boolean
            get() = accessLostState.value
            set(value) {
                accessLostState.value = value
            }

        override var summary: String?
            get() = summaryState.value
            set(value) {
                summaryState.value = value
            }

        override var statusMessage: String?
            get() = statusMessageState.value
            set(value) {
                statusMessageState.value = value
            }

        override var statusIsError: Boolean
            get() = statusIsErrorState.value
            set(value) {
                statusIsErrorState.value = value
            }

        override val workoutFiles: MutableList<DocumentsFolderWorkoutOption>
            get() = workoutFilesState
    }

    val summaryFitExportStatePort = object : SummaryFitExportStatePort {
        override var pendingSnapshot: SessionExportSnapshot?
            get() = pendingFitExportSnapshot
            set(value) {
                pendingFitExportSnapshot = value
            }

        override var statusMessage: String?
            get() = fitExportStatusMessageState.value
            set(value) {
                fitExportStatusMessageState.value = value
            }

        override var statusIsError: Boolean
            get() = fitExportStatusIsErrorState.value
            set(value) {
                fitExportStatusIsErrorState.value = value
            }
    }

    val summaryFitShareStatePort = object : SummaryFitShareStatePort {
        override var statusMessage: String?
            get() = fitExportStatusMessageState.value
            set(value) {
                fitExportStatusMessageState.value = value
            }

        override var statusIsError: Boolean
            get() = fitExportStatusIsErrorState.value
            set(value) {
                fitExportStatusIsErrorState.value = value
            }
    }

    fun restoreTreeUri(treeUri: Uri?) {
        this.treeUri = treeUri
    }

    fun clearFitExportStatus() {
        pendingFitExportSnapshot = null
        fitExportStatusMessageState.value = null
        fitExportStatusIsErrorState.value = false
    }
}

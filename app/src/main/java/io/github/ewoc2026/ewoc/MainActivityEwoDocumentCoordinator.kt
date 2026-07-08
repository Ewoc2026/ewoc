package io.github.ewoc2026.ewoc

import android.net.Uri

/**
 * Owns MainActivity-side EWO open/save result handling so launcher callbacks stay at framework
 * boundaries.
 *
 * Invariants:
 * - Cancelled SAF results are terminal no-ops because the Activity receives no later callback.
 * - Open dispatch reaches the ViewModel only after UTF-8 content is readable and a stable file
 *   name has been resolved for editor status messaging.
 * - Save completion callbacks run only after canonical export JSON exists and the SAF write
 *   succeeded, which prevents the editor from advancing on failed writes.
 */
internal class MainActivityEwoDocumentCoordinator(
    private val readDocumentUtf8: (Uri) -> String?,
    private val resolveOpenedFileName: (Uri) -> String = { uri ->
        uri.lastPathSegment?.substringAfterLast('/') ?: "unknown.ewo"
    },
    private val onOpenDocumentLoaded: (json: String, fileName: String) -> Unit,
    private val onOpenError: (message: String) -> Unit = {},
    private val prepareExportJson: () -> String?,
    private val writeDocumentUtf8: (Uri, String) -> Boolean,
    private val onSaveCompleted: (fileName: String) -> Unit,
) {

    fun handleOpenResult(uri: Uri?) {
        if (uri == null) return
        val json = try {
            readDocumentUtf8(uri)
        } catch (e: Exception) {
            onOpenError("Failed to read file: ${e.message}")
            return
        }
        if (json == null) {
            onOpenError("Could not read file content")
            return
        }
        if (json.isBlank()) {
            onOpenError("File is empty")
            return
        }
        onOpenDocumentLoaded(json, resolveOpenedFileName(uri))
    }

    fun handleSaveResult(uri: Uri?) {
        if (uri == null) return
        val json = prepareExportJson() ?: return
        if (writeDocumentUtf8(uri, json)) {
            onSaveCompleted(resolveOpenedFileName(uri))
        }
    }
}

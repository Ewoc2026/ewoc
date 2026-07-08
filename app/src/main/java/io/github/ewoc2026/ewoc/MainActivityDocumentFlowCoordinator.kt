package io.github.ewoc2026.ewoc

/**
 * Owns MainActivity-side document launch and fallback policy so Compose callback wiring stays thin.
 *
 * Invariants:
 * - Documents-folder writes are always attempted before explicit create-document fallbacks for
 *   flows that support the shared folder path.
 * - Suggested file names are normalized before launch so the SAF picker and folder writes target
 *   the same extension policy.
 */
internal class MainActivityDocumentFlowCoordinator(
    private val launchEwoSaveDocument: (String) -> Unit,
    private val prepareSessionFitExport: () -> String?,
    private val tryExportPendingSessionFitToDocumentsFolder: (String) -> Boolean,
    private val launchSessionFitExportDocument: (String) -> Unit,
) {

    fun requestEwoSave(documentTitle: String) {
        val resolvedTitle = documentTitle.ifBlank { "workout" }
        launchEwoSaveDocument("$resolvedTitle.ewo")
    }

    fun requestSummaryFitExport() {
        val suggestedFileName = prepareSessionFitExport() ?: return
        if (!tryExportPendingSessionFitToDocumentsFolder(suggestedFileName)) {
            launchSessionFitExportDocument(suggestedFileName)
        }
    }

}

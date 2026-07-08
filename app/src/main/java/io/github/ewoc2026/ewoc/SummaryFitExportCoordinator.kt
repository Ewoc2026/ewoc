package io.github.ewoc2026.ewoc

import android.net.Uri
import io.github.ewoc2026.ewoc.session.export.FitExportFailureReason
import io.github.ewoc2026.ewoc.session.export.FitExportResult
import io.github.ewoc2026.ewoc.session.export.SessionExportSnapshot

/**
 * ViewModel-owned state bridge for summary FIT export flows.
 *
 * Invariants:
 * - Pending snapshot stays in the ViewModel layer so SAF callbacks can complete later.
 * - Status text always reflects the latest FIT export attempt outcome.
 */
internal interface SummaryFitExportStatePort {
    var pendingSnapshot: SessionExportSnapshot?
    var statusMessage: String?
    var statusIsError: Boolean
}

/**
 * Coordinates summary FIT export preparation and Documents-folder routing.
 *
 * Invariants:
 * - Missing-summary and timestamp failures clear pending export state to avoid stale SAF targets.
 * - Successful tree writes clear pending snapshot before reporting success to the caller.
 * - Picker-fallback policy still flows through the existing Documents-folder coordinators.
 */
internal class SummaryFitExportCoordinator(
    private val suggestFileName: (SessionExportSnapshot) -> String,
    private val noSummaryMessage: () -> String,
    private val exportFailureMessage: (FitExportResult.Failure) -> String,
    private val exportSuccessMessage: () -> String,
    private val autoSaveFailedMessage: () -> String,
    private val prepareTreeWrite: (onFolderUnavailable: (String) -> Unit) -> DocumentsFolderWritePreparation,
    private val prepareWriteTarget: (
        folderUri: Uri,
        suggestedFileName: String,
        requiredExtension: String,
        mimeType: String,
        allowPickerFallback: Boolean,
        onPickerFallbackBlocked: (() -> Unit)?,
    ) -> DocumentsFolderWriteTargetPreparation,
    private val resolvePostWriteDecision: (
        result: FitExportResult,
        allowPickerFallback: Boolean,
        shouldFallback: (FitExportResult) -> Boolean,
        onPickerFallbackBlocked: (() -> Unit)?,
    ) -> DocumentsFolderWriteDecision,
    private val exportToUri: (Uri, SessionExportSnapshot?) -> FitExportResult,
    private val clearDocumentsFolderStatus: () -> Unit,
) {

    fun prepareExport(
        snapshot: SessionExportSnapshot?,
        statePort: SummaryFitExportStatePort,
    ): String? {
        if (snapshot == null) {
            applyStatus(
                statePort = statePort,
                message = noSummaryMessage(),
                isError = true,
            )
            statePort.pendingSnapshot = null
            return null
        }

        clearStatus(statePort)
        statePort.pendingSnapshot = snapshot
        return suggestFileName(snapshot)
    }

    fun tryExportPendingToDocumentsFolder(
        suggestedFileName: String,
        allowPickerFallback: Boolean,
        mimeType: String,
        statePort: SummaryFitExportStatePort,
    ): Boolean {
        val snapshot = statePort.pendingSnapshot ?: return true
        val writePreparation = prepareTreeWrite { message ->
            applyStatus(
                statePort = statePort,
                message = message,
                isError = true,
            )
        }
        when (writePreparation.decision) {
            DocumentsFolderWriteDecision.COMPLETE -> return true
            DocumentsFolderWriteDecision.REQUIRE_PICKER_FALLBACK -> return false
            DocumentsFolderWriteDecision.CONTINUE_TREE_WRITE -> Unit
        }

        val folderUri = writePreparation.folderUri ?: return false
        val targetPreparation = prepareWriteTarget(
            folderUri,
            suggestedFileName,
            ".fit",
            mimeType,
            allowPickerFallback,
            {
                applyStatus(
                    statePort = statePort,
                    message = autoSaveFailedMessage(),
                    isError = true,
                )
            },
        )
        when (targetPreparation.decision) {
            DocumentsFolderWriteDecision.COMPLETE -> return true
            DocumentsFolderWriteDecision.REQUIRE_PICKER_FALLBACK -> return false
            DocumentsFolderWriteDecision.CONTINUE_TREE_WRITE -> Unit
        }

        val targetUri = targetPreparation.targetUri ?: return false
        val result = exportToUri(targetUri, snapshot)
        when (
            resolvePostWriteDecision(
                result,
                allowPickerFallback,
                { exportResult ->
                    shouldUseCreateDocumentFallbackForFitTreeExport(
                        targetUriCreated = true,
                        exportResult = exportResult,
                    )
                },
                {
                    applyStatus(
                        statePort = statePort,
                        message = autoSaveFailedMessage(),
                        isError = true,
                    )
                },
            )
        ) {
            DocumentsFolderWriteDecision.COMPLETE -> return true
            DocumentsFolderWriteDecision.REQUIRE_PICKER_FALLBACK -> return false
            DocumentsFolderWriteDecision.CONTINUE_TREE_WRITE -> Unit
        }

        return when (result) {
            FitExportResult.Success -> {
                statePort.pendingSnapshot = null
                applyStatus(
                    statePort = statePort,
                    message = exportSuccessMessage(),
                    isError = false,
                )
                clearDocumentsFolderStatus()
                true
            }

            is FitExportResult.Failure -> when (result.reason) {
                FitExportFailureReason.NO_SUMMARY,
                FitExportFailureReason.INVALID_TIMESTAMPS,
                -> {
                    statePort.pendingSnapshot = null
                    applyStatus(
                        statePort = statePort,
                        message = exportFailureMessage(result),
                        isError = true,
                    )
                    true
                }

                FitExportFailureReason.OUTPUT_STREAM_UNAVAILABLE,
                FitExportFailureReason.WRITE_FAILED,
                -> {
                    when (
                        resolvePostWriteDecision(
                            result,
                            allowPickerFallback,
                            { true },
                            {
                                applyStatus(
                                    statePort = statePort,
                                    message = autoSaveFailedMessage(),
                                    isError = true,
                                )
                            },
                        )
                    ) {
                        DocumentsFolderWriteDecision.COMPLETE -> true
                        DocumentsFolderWriteDecision.REQUIRE_PICKER_FALLBACK -> false
                        DocumentsFolderWriteDecision.CONTINUE_TREE_WRITE -> false
                    }
                }
            }
        }
    }

    /**
     * Completes explicit create-document export after the SAF picker returns.
     *
     * Invariants:
     * - Cancelled picker results always clear pending snapshot so stale exports cannot complete later.
     * - If no staged snapshot exists, caller-provided fallback snapshot supplies the same export payload
     *   the ViewModel would have used inline before this seam existed.
     * - Explicit document targets report terminal success/failure immediately because picker fallback is
     *   already resolved once the user chooses a concrete destination.
     */
    fun completeDocumentExport(
        targetUri: Uri?,
        fallbackSnapshot: SessionExportSnapshot?,
        statePort: SummaryFitExportStatePort,
    ) {
        if (targetUri == null) {
            statePort.pendingSnapshot = null
            return
        }

        val snapshot = statePort.pendingSnapshot ?: fallbackSnapshot
        statePort.pendingSnapshot = null
        when (val result = exportToUri(targetUri, snapshot)) {
            FitExportResult.Success -> {
                applyStatus(
                    statePort = statePort,
                    message = exportSuccessMessage(),
                    isError = false,
                )
                clearDocumentsFolderStatus()
            }

            is FitExportResult.Failure -> {
                applyStatus(
                    statePort = statePort,
                    message = exportFailureMessage(result),
                    isError = true,
                )
            }
        }
    }

    private fun clearStatus(statePort: SummaryFitExportStatePort) {
        statePort.pendingSnapshot = null
        statePort.statusMessage = null
        statePort.statusIsError = false
    }

    private fun applyStatus(
        statePort: SummaryFitExportStatePort,
        message: String?,
        isError: Boolean,
    ) {
        statePort.statusMessage = message
        statePort.statusIsError = isError
    }
}

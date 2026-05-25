package com.example.ergometerapp

import android.net.Uri

/**
 * Shared decision outcome for documents-folder write attempts.
 *
 * `COMPLETE` means caller-side request handling is finished.
 * `REQUIRE_PICKER_FALLBACK` means caller should continue with create-document fallback flow.
 * `CONTINUE_TREE_WRITE` means tree-write flow can keep going.
 */
internal enum class DocumentsFolderWriteDecision {
    COMPLETE,
    REQUIRE_PICKER_FALLBACK,
    CONTINUE_TREE_WRITE,
}

/**
 * Result returned by [DocumentsFolderWriteCoordinator.prepareTreeWrite].
 *
 * `folderUri` is non-null only when [decision] is [DocumentsFolderWriteDecision.CONTINUE_TREE_WRITE].
 */
internal data class DocumentsFolderWritePreparation(
    val decision: DocumentsFolderWriteDecision,
    val folderUri: Uri?,
)

/**
 * Coordinates documents-folder tree-write guard checks and fallback policy.
 *
 * Invariants:
 * - Readiness checks run before one-shot debug write-failure overrides.
 * - Every fallback-required path sets documents-folder write status first.
 * - When picker fallback is blocked, caller callback runs after documents status update.
 */
internal class DocumentsFolderWriteCoordinator(
    private val ensureFolderReadyForFileOperations: () -> Uri?,
    private val isFolderAccessLost: () -> Boolean,
    private val consumeDebugWriteFailureOnce: () -> Boolean,
    private val setDocumentsFolderStatus: (String?, Boolean) -> Unit,
    private val documentsFolderAccessLostMessage: () -> String,
    private val documentsFolderNotConfiguredMessage: () -> String,
    private val documentsFolderWriteFailedMessage: () -> String,
) {
    fun prepareTreeWrite(onFolderUnavailable: (String) -> Unit): DocumentsFolderWritePreparation {
        val folderUri = ensureFolderReadyForFileOperations()
        if (folderUri == null) {
            onFolderUnavailable(
                if (isFolderAccessLost()) {
                    documentsFolderAccessLostMessage()
                } else {
                    documentsFolderNotConfiguredMessage()
                },
            )
            return DocumentsFolderWritePreparation(
                decision = DocumentsFolderWriteDecision.COMPLETE,
                folderUri = null,
            )
        }

        if (consumeDebugWriteFailureOnce()) {
            setDocumentsFolderStatus(
                documentsFolderWriteFailedMessage(),
                true,
            )
            return DocumentsFolderWritePreparation(
                decision = DocumentsFolderWriteDecision.REQUIRE_PICKER_FALLBACK,
                folderUri = null,
            )
        }

        return DocumentsFolderWritePreparation(
            decision = DocumentsFolderWriteDecision.CONTINUE_TREE_WRITE,
            folderUri = folderUri,
        )
    }

    fun resolveFallbackDecision(
        shouldFallback: Boolean,
        allowPickerFallback: Boolean,
        onPickerFallbackBlocked: (() -> Unit)? = null,
    ): DocumentsFolderWriteDecision {
        if (!shouldFallback) {
            return DocumentsFolderWriteDecision.CONTINUE_TREE_WRITE
        }

        setDocumentsFolderStatus(
            documentsFolderWriteFailedMessage(),
            true,
        )
        if (!allowPickerFallback) {
            onPickerFallbackBlocked?.invoke()
            return DocumentsFolderWriteDecision.COMPLETE
        }
        return DocumentsFolderWriteDecision.REQUIRE_PICKER_FALLBACK
    }
}

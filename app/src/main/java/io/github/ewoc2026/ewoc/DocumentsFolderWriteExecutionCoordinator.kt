package io.github.ewoc2026.ewoc

import android.net.Uri

/**
 * Result returned by [DocumentsFolderWriteExecutionCoordinator.prepareWriteTarget].
 *
 * `targetUri` is non-null only when [decision] is [DocumentsFolderWriteDecision.CONTINUE_TREE_WRITE].
 */
internal data class DocumentsFolderWriteTargetPreparation(
    val decision: DocumentsFolderWriteDecision,
    val targetUri: Uri?,
)

/**
 * Controls how tree writes behave when the preferred file name already exists.
 */
internal enum class DocumentsFolderExistingFilePolicy {
    CREATE_COPY_ON_CONFLICT,
    OVERWRITE_EXISTING,
}

/**
 * Coordinates tree-write target creation and post-write fallback routing.
 *
 * Invariants:
 * - Filename extension is normalized before target creation.
 * - MIME type is passed through unchanged for SAF providers.
 * - Non-continue decisions always return a null target URI.
 */
internal class DocumentsFolderWriteExecutionCoordinator(
    private val createWritableDocumentUri: (
        treeUri: Uri,
        preferredFileName: String,
        mimeType: String,
        existingFilePolicy: DocumentsFolderExistingFilePolicy,
    ) -> Uri?,
    private val resolveFallbackDecision: (
        shouldFallback: Boolean,
        allowPickerFallback: Boolean,
        onPickerFallbackBlocked: (() -> Unit)?,
    ) -> DocumentsFolderWriteDecision,
) {
    fun prepareWriteTarget(
        folderUri: Uri,
        suggestedFileName: String,
        requiredExtension: String,
        mimeType: String,
        allowPickerFallback: Boolean,
        existingFilePolicy: DocumentsFolderExistingFilePolicy =
            DocumentsFolderExistingFilePolicy.CREATE_COPY_ON_CONFLICT,
        onPickerFallbackBlocked: (() -> Unit)? = null,
    ): DocumentsFolderWriteTargetPreparation {
        val targetUri = createWritableDocumentUri(
            folderUri,
            SafFileNamePolicy.ensureExtension(suggestedFileName, requiredExtension),
            mimeType,
            existingFilePolicy,
        )
        val decision = resolveFallbackDecision(
            targetUri == null,
            allowPickerFallback,
            onPickerFallbackBlocked,
        )
        if (decision != DocumentsFolderWriteDecision.CONTINUE_TREE_WRITE) {
            return DocumentsFolderWriteTargetPreparation(
                decision = decision,
                targetUri = null,
            )
        }
        return DocumentsFolderWriteTargetPreparation(
            decision = decision,
            targetUri = targetUri,
        )
    }

    fun <T> resolvePostWriteDecision(
        result: T,
        allowPickerFallback: Boolean,
        shouldFallback: (T) -> Boolean,
        onPickerFallbackBlocked: (() -> Unit)? = null,
    ): DocumentsFolderWriteDecision {
        return resolveFallbackDecision(
            shouldFallback(result),
            allowPickerFallback,
            onPickerFallbackBlocked,
        )
    }
}

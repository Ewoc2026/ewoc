package io.github.ewoc2026.ewoc.ui

import androidx.compose.runtime.Composable
import com.ewo.editor.model.*
import io.github.ewoc2026.ewoc.ewoeditor.EwoEditorSnapshot

@DestinationScreenPreviews
@Composable
private fun EwoEditorScreenEmptyPreview() {
    val emptyDoc = EditorDocumentFactory.empty()
    EwoEditorScreen(
        snapshot = EwoEditorSnapshot(
            document = emptyDoc,
            preview = EditorPreview(
                steps = emptyList(),
                totalDurationSec = 0,
                intensityFactor = null,
                tss = null,
                sanityWarnings = emptyList(),
                compileErrors = emptyList(),
            ),
            canUndo = false,
            canRedo = false,
            statusMessage = "Ready",
            currentFileName = null,
            openedFromBundledLibrary = false,
            ftpWatts = null,
            hrMaxBpm = null,
            restingHrBpm = null,
            lthrBpm = null,
            hasClipboard = false,
        ),
        onAction = {},
    )
}

package com.example.ergometerapp.ewoeditor

import android.content.Context
import com.ewo.core.EwoCompileContext
import com.ewo.core.EwoEngine
import com.ewo.core.EwoWorkoutParseResult
import com.ewo.editor.commands.EditorCommand
import com.ewo.editor.commands.EditorCommandExecutor
import com.ewo.editor.commands.EditorViewAction
import com.ewo.editor.commands.PasteSegment
import com.ewo.editor.model.*
import com.example.ergometerapp.R
import com.example.ergometerapp.workout.WorkoutFile

/**
 * Coordinator for the canonical `.ewo` editor on Android.
 *
 * Wraps the shared [EditorHistory] and [EditorCommandExecutor] from the KMP
 * `ewo-editor-model` and `ewo-editor-commands` modules. Provides an immutable
 * [EwoEditorSnapshot] for the UI layer.
 *
 * This is the Android counterpart of the desktop `EditorState`.
 */
internal class EwoEditorCoordinator(private val appContext: Context) {

    private var history: EditorHistory = EditorHistory.of(EditorDocumentFactory.empty())
    private var preview: EditorPreview = emptyPreview()
    private var ftpWatts: Int? = null
    private var hrMaxBpm: Int? = null
    private var restingHrBpm: Int? = null
    private var lthrBpm: Int? = null
    private var statusMessage: String = appContext.getString(R.string.ewo_editor_status_ready)
    private var currentFileName: String? = null
    private var openedFromBundledLibrary: Boolean = false
    private var clipboard: EditorSegment? = null

    val document: EditorWorkoutDocument get() = history.present

    fun snapshot(): EwoEditorSnapshot = EwoEditorSnapshot(
        document = history.present,
        preview = preview,
        canUndo = history.canUndo,
        canRedo = history.canRedo,
        statusMessage = statusMessage,
        currentFileName = currentFileName,
        openedFromBundledLibrary = openedFromBundledLibrary,
        ftpWatts = ftpWatts,
        hrMaxBpm = hrMaxBpm,
        restingHrBpm = restingHrBpm,
        lthrBpm = lthrBpm,
        hasClipboard = clipboard != null,
    )

    fun dispatch(command: EditorCommand) {
        history = EditorCommandExecutor.execute(history, command)
        recomputePreview()
    }

    fun dispatchView(action: EditorViewAction) {
        history = EditorCommandExecutor.applyViewAction(history, action)
    }

    fun undo() {
        history = history.undo()
        recomputePreview()
    }

    fun redo() {
        history = history.redo()
        recomputePreview()
    }

    fun copySegment(nodeId: EditorNodeId) {
        clipboard = document.findSegment(nodeId)
    }

    fun pasteSegment() {
        val seg = clipboard ?: return
        dispatch(PasteSegment(segment = seg, afterNodeId = document.selectedNodeId))
    }

    fun newDocument() {
        history = EditorHistory.of(EditorDocumentFactory.empty())
        currentFileName = null
        openedFromBundledLibrary = false
        statusMessage = appContext.getString(R.string.ewo_editor_status_new_document)
        recomputePreview()
    }

    fun openJson(
        json: String,
        fileName: String,
        openedFromBundledLibrary: Boolean = false,
    ): Boolean {
        val result = EwoEngine.parseDroppingInvalidTags(json, buildCompileContext()).parseResult
        return when (result) {
            is EwoWorkoutParseResult.Success -> {
                val doc = EditorDocumentFactory.fromParseResult(result)
                history = EditorHistory.of(doc)
                currentFileName = fileName
                this.openedFromBundledLibrary = openedFromBundledLibrary
                statusMessage = when (result) {
                    is EwoWorkoutParseResult.Success.NeedsCompileContext ->
                        appContext.getString(R.string.ewo_editor_status_opened_needs_context, fileName)
                    is EwoWorkoutParseResult.Success.Compiled ->
                        appContext.getString(R.string.ewo_editor_status_opened, fileName)
                }
                updatePreviewFromResult(result)
                true
            }
            is EwoWorkoutParseResult.Failure -> {
                this.openedFromBundledLibrary = false
                statusMessage = appContext.getString(R.string.ewo_editor_status_error, result.error.message)
                false
            }
        }
    }

    fun reportOpenError(message: String) {
        openedFromBundledLibrary = false
        statusMessage = appContext.getString(R.string.ewo_editor_status_error, message)
    }

    fun openZwo(workoutFile: WorkoutFile, fileName: String) {
        val doc = ZwoToEditorConverter.convert(workoutFile)
        history = EditorHistory.of(doc)
        currentFileName = fileName
        openedFromBundledLibrary = false
        statusMessage = appContext.getString(R.string.ewo_editor_status_opened, fileName)
        recomputePreview()
    }

    fun exportCanonicalJson(): String? {
        if (!document.canExport) {
            statusMessage = appContext.getString(R.string.ewo_editor_status_export_blocked)
            return null
        }
        val json = buildCanonicalJson(document)
        // Safety net: verify the produced JSON parses back successfully before saving.
        val parseResult = EwoEngine.parse(json, buildCompileContext())
        if (parseResult is EwoWorkoutParseResult.Failure) {
            statusMessage = appContext.getString(
                R.string.ewo_editor_status_error,
                parseResult.error.message,
            )
            return null
        }
        return json
    }

    fun completeSave(fileName: String) {
        currentFileName = fileName
        openedFromBundledLibrary = false
        statusMessage = appContext.getString(R.string.ewo_editor_status_opened, fileName)
    }

    fun setFtpWatts(value: Int?) {
        ftpWatts = value
        recomputePreview()
    }

    fun setHrMaxBpm(value: Int?) {
        hrMaxBpm = value
        recomputePreview()
    }

    fun setRestingHrBpm(value: Int?) {
        restingHrBpm = value
        recomputePreview()
    }

    fun setLthrBpm(value: Int?) {
        lthrBpm = value
        recomputePreview()
    }

    private fun buildCompileContext(): EwoCompileContext = EwoCompileContext(
        ftpWatts = ftpWatts,
        hrMaxBpm = hrMaxBpm,
        restingHrBpm = restingHrBpm,
        lthrBpm = lthrBpm,
    )

    private fun recomputePreview() {
        val json = buildCanonicalJson(document)
        val result = EwoEngine.analyze(json, buildCompileContext())
        updatePreviewFromResult(result)
    }

    private fun updatePreviewFromResult(result: EwoWorkoutParseResult) {
        preview = when (result) {
            is EwoWorkoutParseResult.Success.Compiled -> buildEditorPreview(
                compiled = result.compiled,
                sanityResult = result.sanityResult,
                ftpWatts = ftpWatts,
            )
            is EwoWorkoutParseResult.Success.NeedsCompileContext -> buildEditorPreview(
                compiled = null,
                sanityResult = result.sanityResult,
                compileErrors = result.compileErrors,
                ftpWatts = ftpWatts,
            )
            is EwoWorkoutParseResult.Failure -> emptyPreview()
        }
    }

    companion object {
        private fun emptyPreview() = EditorPreview(
            steps = emptyList(),
            totalDurationSec = 0,
            intensityFactor = null,
            tss = null,
            sanityWarnings = emptyList(),
            compileErrors = emptyList(),
        )
    }
}

/**
 * Immutable snapshot of the EWO editor state, consumed by the Compose UI.
 */
internal data class EwoEditorSnapshot(
    val document: EditorWorkoutDocument,
    val preview: EditorPreview,
    val canUndo: Boolean,
    val canRedo: Boolean,
    val statusMessage: String,
    val currentFileName: String?,
    val openedFromBundledLibrary: Boolean,
    val ftpWatts: Int?,
    val hrMaxBpm: Int?,
    val restingHrBpm: Int?,
    val lthrBpm: Int?,
    val hasClipboard: Boolean,
)

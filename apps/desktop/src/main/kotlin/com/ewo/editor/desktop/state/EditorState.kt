package com.ewo.editor.desktop.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ewo.core.EwoCompileContext
import com.ewo.core.EwoEngine
import com.ewo.core.EwoWorkoutParseResult
import com.ewo.editor.commands.*
import com.ewo.editor.model.*
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File

/**
 * Mutable state holder for the editor. Compose observes [history] for recomposition.
 *
 * All mutations go through [dispatch] (undoable commands) or [dispatchView]
 * (non-undoable view actions like selection).
 */
class EditorState {

    companion object {
        private const val COMPACT_WINDOW_WIDTH_DP = 1400f
        private const val LARGE_WINDOW_WIDTH_DP = 1800f
        private const val DEFAULT_WORKOUT_TREE_PANEL_WIDTH_DP = 240f
        private const val COMPACT_WORKOUT_TREE_PANEL_WIDTH_DP = 260f
        private const val LARGE_WORKOUT_TREE_PANEL_WIDTH_DP = 300f
        private const val MIN_WORKOUT_TREE_PANEL_WIDTH_DP = 220f
        private const val MAX_WORKOUT_TREE_PANEL_WIDTH_DP = 340f
        private const val DEFAULT_DIAGNOSTICS_PANEL_WIDTH_DP = 320f
        private const val COMPACT_DIAGNOSTICS_PANEL_WIDTH_DP = 300f
        private const val LARGE_DIAGNOSTICS_PANEL_WIDTH_DP = 320f
        private const val MIN_DIAGNOSTICS_PANEL_WIDTH_DP = 280f
        private const val MAX_DIAGNOSTICS_PANEL_WIDTH_DP = 420f
    }

    enum class OnboardingPath {
        FIRST_WORKOUT,
        KNOWS_FTP,
        EXPLORE_HR,
    }

    data class OnboardingGuideSection(
        val title: String,
        val items: List<String>,
    )

    data class OnboardingGuide(
        val title: String,
        val summary: String,
        val primaryActionLabel: String,
        val sections: List<OnboardingGuideSection>,
        val closingNote: String,
    )

    data class LastOperationDiagnostics(
        val operation: String,
        val targetName: String?,
        val outcome: String,
        val detail: String,
    )

    var history by mutableStateOf(EditorHistory.of(EditorDocumentFactory.empty()))
        private set

    /** The file currently open, or null for a new unsaved document. */
    var currentFile: File? by mutableStateOf(null)
        private set

    /** Status message shown in the bottom bar. */
    var statusMessage: String by mutableStateOf("Ready")
        private set

    /** Lightweight summary of the latest file/preview operation for diagnostics UI. */
    var lastOperationDiagnostics: LastOperationDiagnostics? by mutableStateOf(
        LastOperationDiagnostics(
            operation = "Editor startup",
            targetName = null,
            outcome = "idle",
            detail = "No file opened yet.",
        ),
    )
        private set

    /** In-memory clipboard — holds a segment template for paste. Never serialized. */
    var clipboard: EditorSegment? by mutableStateOf(null)

    private val recentFilesFile = File(System.getProperty("user.home"), ".config/ewo-editor/recent.txt")
    private val lastFileChooserDirectoryFile = File(
        System.getProperty("user.home"),
        ".config/ewo-editor/last-directory.txt",
    )
    private val onboardingDismissedFile = File(
        System.getProperty("user.home"),
        ".config/ewo-editor/onboarding-dismissed.txt",
    )
    private val initialOnboardingDismissed = loadOnboardingDismissed()
    private val workoutTreePanelWidthFile = File(
        System.getProperty("user.home"),
        ".config/ewo-editor/workout-tree-panel-width.txt",
    )
    private val diagnosticsPanelWidthFile = File(
        System.getProperty("user.home"),
        ".config/ewo-editor/diagnostics-panel-width.txt",
    )
    private var hasSavedWorkoutTreePanelWidth = workoutTreePanelWidthFile.exists()
    private var hasSavedDiagnosticsPanelWidth = diagnosticsPanelWidthFile.exists()
    private var lastKnownWindowWidthDp = DEFAULT_WORKOUT_TREE_PANEL_WIDTH_DP + DEFAULT_DIAGNOSTICS_PANEL_WIDTH_DP

    var layoutWidthPreferenceVersion by mutableStateOf(0)
        private set

    /** Recently opened/saved files, most recent first. Persisted across sessions. */
    var recentFiles: List<File> by mutableStateOf(loadRecentFiles())
        private set

    /** Last directory used in the file chooser, persisted across sessions. */
    var lastFileChooserDirectory: File? by mutableStateOf(loadLastFileChooserDirectory())
        private set

    /**
     * Whether the lightweight getting-started card has been dismissed.
     *
     * This is persisted because Path B users should not have to close the same
     * orientation card on every launch, while still being able to reopen it
     * later from the UI.
     */
    var onboardingDismissed: Boolean by mutableStateOf(initialOnboardingDismissed)
        private set

    /**
     * The help path currently shown in the center workspace.
     *
     * Kept out of persisted settings because reopening the same long-form help
     * panel on every launch would feel heavier than the lightweight,
     * dismissible onboarding entry point.
     */
    var activeOnboardingPath: OnboardingPath? by mutableStateOf(
        if (initialOnboardingDismissed) null else OnboardingPath.FIRST_WORKOUT,
    )
        private set

    /**
     * Width of the left workout-flow rail in dp.
     *
     * This is persisted because some workout structures need more room for
     * labels and repeat nesting than others.
     */
    var workoutTreePanelWidthDp: Float by mutableStateOf(loadWorkoutTreePanelWidthDp())
        private set

    /**
     * Width of the right diagnostics rail in dp.
     *
     * This is persisted because different desktop window sizes need different
     * compromises between diagnostics readability and center-workspace room.
     */
    var diagnosticsPanelWidthDp: Float by mutableStateOf(loadDiagnosticsPanelWidthDp())
        private set

    /** Athlete profile — optional, used to resolve heart_rate_relative targets. */
    var hrMaxBpm: Int? by mutableStateOf(null)
    var restingHrBpm: Int? by mutableStateOf(null)
    var lthrBpm: Int? by mutableStateOf(null)

    /** User-provided FTP for IF/TSS computation. Editor-only, never stored in .ewo. */
    var ftpWatts: Int? by mutableStateOf(null)

    val document: EditorWorkoutDocument get() = history.present

    /** Compiled preview of the current document. Recomputed on demand. */
    var preview: EditorPreview by mutableStateOf(
        EditorPreview(
            steps = emptyList(),
            totalDurationSec = 0,
            intensityFactor = null,
            tss = null,
            sanityWarnings = emptyList(),
            compileErrors = emptyList(),
        ),
    )
        private set

    private fun buildCompileContext(): EwoCompileContext = EwoCompileContext(
        ftpWatts = ftpWatts,
        hrMaxBpm = hrMaxBpm,
        restingHrBpm = restingHrBpm,
        lthrBpm = lthrBpm,
    )

    // --- Command dispatch ---

    fun dispatch(command: EditorCommand) {
        // Wrap title/description changes with localized sync into a single undo entry
        val effective = wrapWithLocalizedSync(command)
        history = EditorCommandExecutor.execute(history, effective)
        recomputePreview()
    }

    /**
     * When the root title or description changes, wraps the command with a
     * localized default-text sync so the v1.6 consistency rule
     * (`title_localized.default == title`) holds automatically.
     * Returns a [CompositeCommand] when sync is needed, otherwise the original command.
     */
    private fun wrapWithLocalizedSync(command: EditorCommand): EditorCommand {
        val doc = document
        return when (command) {
            is SetWorkoutTitle -> {
                val loc = doc.titleLocalized ?: return command
                if (loc.defaultText != command.title) {
                    CompositeCommand(listOf(command, SetTitleLocalized(loc.copy(defaultText = command.title))))
                } else command
            }
            is SetWorkoutDescription -> {
                val loc = doc.descriptionLocalized ?: return command
                if (loc.defaultText != command.description) {
                    CompositeCommand(listOf(command, SetDescriptionLocalized(loc.copy(defaultText = command.description))))
                } else command
            }
            else -> command
        }
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

    // --- File operations ---

    fun newDocument() {
        history = EditorHistory.of(EditorDocumentFactory.empty())
        currentFile = null
        statusMessage = "New document"
        recomputePreview()
        lastOperationDiagnostics = LastOperationDiagnostics(
            operation = "New document",
            targetName = null,
            outcome = "success",
            detail = "Started a new empty workout document.",
        )
    }

    fun openFile(file: File): Boolean {
        val json = file.readText()
        val result = EwoEngine.parseDroppingInvalidTags(json, buildCompileContext()).parseResult
        return when (result) {
            is EwoWorkoutParseResult.Success -> {
                val doc = EditorDocumentFactory.fromParseResult(result)
                history = EditorHistory.of(doc)
                currentFile = file
                rememberFileChooserDirectory(file.parentFile)
                statusMessage = when (result) {
                    is EwoWorkoutParseResult.Success.NeedsCompileContext ->
                        "Opened: ${file.name} (some targets need preview rider profile values to render fully)"
                    is EwoWorkoutParseResult.Success.Compiled ->
                        "Opened: ${file.name}"
                }
                lastOperationDiagnostics = when (result) {
                    is EwoWorkoutParseResult.Success.Compiled -> LastOperationDiagnostics(
                        operation = "Open file",
                        targetName = file.name,
                        outcome = "compiled",
                        detail = "Opened successfully with a fully compiled preview.",
                    )
                    is EwoWorkoutParseResult.Success.NeedsCompileContext -> LastOperationDiagnostics(
                        operation = "Open file",
                        targetName = file.name,
                        outcome = "needs_compile_context",
                        detail = "Opened successfully, but preview needs rider profile values: " +
                            result.compileErrors.joinToString("; ") { it.message },
                    )
                }
                updatePreviewFromResult(result)
                addToRecentFiles(file)
                true
            }
            is EwoWorkoutParseResult.Failure -> {
                statusMessage = "Error: ${result.error.message}"
                lastOperationDiagnostics = LastOperationDiagnostics(
                    operation = "Open file",
                    targetName = file.name,
                    outcome = "failure",
                    detail = "${result.error.code.stableCode}: ${result.error.message}",
                )
                false
            }
        }
    }

    fun saveFile(file: File? = currentFile) {
        if (file == null) return
        val json = exportCanonicalJson()
        if (json != null) {
            file.writeText(json)
            currentFile = file
            rememberFileChooserDirectory(file.parentFile)
            history = history.updatePresent { it.copy(isDirty = false) }
            statusMessage = "Saved: ${file.name}"
            lastOperationDiagnostics = LastOperationDiagnostics(
                operation = "Save file",
                targetName = file.name,
                outcome = "success",
                detail = "Saved the current workout to disk.",
            )
            addToRecentFiles(file)
        }
    }

    fun exportCanonicalJson(): String? {
        if (!document.canExport) {
            statusMessage = "Cannot export: document has validation errors"
            lastOperationDiagnostics = LastOperationDiagnostics(
                operation = "Export canonical JSON",
                targetName = currentFile?.name,
                outcome = "blocked",
                detail = "Export is blocked until validation errors are fixed.",
            )
            return null
        }
        lastOperationDiagnostics = LastOperationDiagnostics(
            operation = "Export canonical JSON",
            targetName = currentFile?.name,
            outcome = "success",
            detail = "Built canonical JSON from the current document.",
        )
        return buildCanonicalJson(document)
    }

    fun diagnosticsReport(): String = buildString {
        val summary = lastOperationDiagnostics
        val blockingMarkers = document.validationMarkers.filter { it.blocksExport }
        appendLine("Latest operation")
        appendLine("Operation: ${summary?.operation ?: "Unknown"}")
        appendLine("Target: ${summary?.targetName ?: "None"}")
        appendLine("Outcome: ${summary?.outcome ?: "unknown"}")
        appendLine("Detail: ${summary?.detail ?: "No details recorded."}")
        appendLine()
        appendLine("Current document")
        appendLine("Current file: ${currentFile?.absolutePath ?: "Unsaved document"}")
        appendLine("Dirty: ${document.isDirty}")
        appendLine("Can export: ${document.canExport}")
        appendLine("Status message: $statusMessage")
        appendLine()
        appendLine("Preview rider profile")
        appendLine("FTP watts: ${ftpWatts ?: "unset"}")
        appendLine("HR max bpm: ${hrMaxBpm ?: "unset"}")
        appendLine("Resting HR bpm: ${restingHrBpm ?: "unset"}")
        appendLine("Threshold HR bpm: ${lthrBpm ?: "unset"}")
        appendLine()
        appendLine("Preview summary")
        appendLine("Chart unit: ${preview.chartPowerUnit.name.lowercase()}")
        appendLine("Compiled steps: ${preview.steps.size}")
        appendLine("Total duration sec: ${preview.totalDurationSec}")
        appendLine("Sanity warnings: ${preview.sanityWarnings.size}")
        preview.sanityWarnings.forEach { warning ->
            appendLine("- ${warning.code}: ${warning.message}")
        }
        appendLine("Compile errors: ${preview.compileErrors.size}")
        preview.compileErrors.forEach { error ->
            appendLine("- $error")
        }
        appendLine("Export blockers: ${blockingMarkers.size}")
        blockingMarkers.forEach { marker ->
            appendLine("- ${marker.severity.name.lowercase()}: ${marker.code ?: "no_code"}: ${marker.message}")
        }
    }

    fun copyDiagnosticsToClipboard() {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(
            StringSelection(diagnosticsReport()),
            null,
        )
        statusMessage = "Diagnostics copied to clipboard"
        lastOperationDiagnostics = LastOperationDiagnostics(
            operation = "Copy diagnostics",
            targetName = currentFile?.name,
            outcome = "success",
            detail = "Copied the latest diagnostics summary to the clipboard.",
        )
    }

    fun dismissOnboardingCard() {
        onboardingDismissed = true
        activeOnboardingPath = null
        persistOnboardingDismissed(true)
        statusMessage = "Getting started guide hidden. You can reopen it from Help."
        lastOperationDiagnostics = LastOperationDiagnostics(
            operation = "Hide getting started guide",
            targetName = currentFile?.name,
            outcome = "success",
            detail = "Dismissed the lightweight onboarding entry point and closed the active guide view.",
        )
    }

    fun reopenOnboardingCard(path: OnboardingPath = OnboardingPath.FIRST_WORKOUT) {
        onboardingDismissed = false
        activeOnboardingPath = path
        persistOnboardingDismissed(false)
        statusMessage = "Getting started guide reopened."
        lastOperationDiagnostics = LastOperationDiagnostics(
            operation = "Show getting started guide",
            targetName = currentFile?.name,
            outcome = "success",
            detail = "Reopened the lightweight onboarding card and guide view.",
        )
    }

    fun showOnboardingPath(path: OnboardingPath) {
        onboardingDismissed = false
        activeOnboardingPath = path
        persistOnboardingDismissed(false)
        val pathLabel = when (path) {
            OnboardingPath.FIRST_WORKOUT -> "first-workout guidance"
            OnboardingPath.KNOWS_FTP -> "FTP guidance"
            OnboardingPath.EXPLORE_HR -> "HR-based guidance"
        }
        statusMessage = "Showing $pathLabel"
        lastOperationDiagnostics = LastOperationDiagnostics(
            operation = "Show onboarding guide",
            targetName = currentFile?.name,
            outcome = "success",
            detail = "Opened the $pathLabel in the center workspace.",
        )
    }

    fun closeOnboardingGuide() {
        activeOnboardingPath = null
        statusMessage = "Returned to the editor workspace."
        lastOperationDiagnostics = LastOperationDiagnostics(
            operation = "Close onboarding guide",
            targetName = currentFile?.name,
            outcome = "success",
            detail = "Closed the center-workspace help view and returned focus to the editor.",
        )
    }

    fun onboardingGuide(path: OnboardingPath): OnboardingGuide = when (path) {
        OnboardingPath.FIRST_WORKOUT -> OnboardingGuide(
            title = "First workout",
            summary = "Build one simple workout first: add a few steps, confirm the shape in the chart, then export only when diagnostics are clean.",
            primaryActionLabel = "Start with simple steady or build-up steps from the workout tree.",
            sections = listOf(
                OnboardingGuideSection(
                    title = "Basic loop",
                    items = listOf(
                        "Add one or two steps from the workout tree on the left.",
                        "Set duration and target in the inspector below the chart.",
                        "Use the chart to confirm the overall shape before worrying about details.",
                    ),
                ),
                OnboardingGuideSection(
                    title = "Preview vs export",
                    items = listOf(
                        "Preview rider profile values help the chart and metrics when a target depends on FTP or heart-rate context.",
                        "Those preview values are editor-only and are not saved into the workout file.",
                        "Export blockers in the diagnostics rail must be fixed before the workout can be exported.",
                    ),
                ),
            ),
            closingNote = "Keep the first workout small on purpose. Once the basic loop feels natural, interval structure and HR-based authoring become much easier to reason about.",
        )
        OnboardingPath.KNOWS_FTP -> OnboardingGuide(
            title = "I already know FTP workouts",
            summary = "Use the tree to build interval structure quickly, then rely on preview FTP, the chart, and diagnostics to verify the file before export.",
            primaryActionLabel = "Build the structure first, then tune targets and labels.",
            sections = listOf(
                OnboardingGuideSection(
                    title = "Fast path",
                    items = listOf(
                        "Use steady and build-up steps to sketch the workout before polishing names or notes.",
                        "Set targets in watts or %FTP depending on how you want to think about the session.",
                        "Use repeat blocks for reusable ON/OFF structure, then close the block before adding the next outer step.",
                    ),
                ),
                OnboardingGuideSection(
                    title = "Checks before export",
                    items = listOf(
                        "Set preview FTP when you want the chart, IF, and TSS to reflect the intended rider.",
                        "Use `Internal label` for your own structure and `Shown to rider` for the rider-facing message.",
                        "Wait for diagnostics to say the workout is ready before exporting the final EWO file.",
                    ),
                ),
            ),
            closingNote = "If the workflow already feels familiar, keep help lightweight: use this view as a quick refresher, not a mandatory wizard.",
        )
        OnboardingPath.EXPLORE_HR -> OnboardingGuide(
            title = "Explore HR-based workouts",
            summary = "HR-based authoring adds two layers beyond plain FTP steps: choosing the right heart-rate band model and deciding whether the workout really needs workout-level control settings.",
            primaryActionLabel = "Start with one simple HR work block. Use HR or HR% only when the coaching intent is truly heart-rate-led.",
            sections = listOf(
                OnboardingGuideSection(
                    title = "Choose the target type first",
                    items = listOf(
                        "Choose `HR` for an absolute bpm band such as `140-150` when you already know the real working range you want.",
                        "Choose `HR%` for a band relative to a reference such as HR max when you want the target to scale from rider profile context instead of fixed bpm.",
                        "Do not use HR targets just because they exist. Stay with watts or %FTP if the workout idea is really power-led.",
                    ),
                ),
                OnboardingGuideSection(
                    title = "When control settings matter",
                    items = listOf(
                        "Add workout-level control settings only when the workout should actively adjust trainer power to keep HR inside the target band.",
                        "Treat starting power and min/max power as the allowed correction window, not as the workout's real target itself.",
                        "Use the safety cap as a hard stop boundary and signal-loss fallback as the fail-safe state, not as part of the normal working range.",
                    ),
                ),
                OnboardingGuideSection(
                    title = "Preview and export",
                    items = listOf(
                        "Relative HR targets need preview rider profile values before the editor can resolve them cleanly in preview.",
                        "Preview profile values help you inspect the workout, but they are editor-only context and are not saved into the exported workout file.",
                        "Recheck the chart and diagnostics after every HR-target or control-settings change so you can separate preview gaps from real export blockers.",
                    ),
                ),
                OnboardingGuideSection(
                    title = "Best first validation",
                    items = listOf(
                        "Start with a simple structure: easy warmup, one steady HR block, easy cooldown.",
                        "Keep the first target band realistic and the first ride diagnostic in spirit, not ambitious.",
                        "If the first test feels off, simplify the workout before trying to tune every control value at once.",
                    ),
                ),
            ),
            closingNote = "HR-based workouts are best treated as a deliberate capability, not the default for every session. Use them when the desired coaching intent is truly heart-rate-led.",
        )
    }

    fun applyAdaptivePanelDefaults(windowWidthDp: Float) {
        lastKnownWindowWidthDp = windowWidthDp
        if (!hasSavedWorkoutTreePanelWidth) {
            workoutTreePanelWidthDp = recommendedWorkoutTreePanelWidthDp(windowWidthDp)
        }
        if (!hasSavedDiagnosticsPanelWidth) {
            diagnosticsPanelWidthDp = recommendedDiagnosticsPanelWidthDp(windowWidthDp)
        }
    }

    fun updateWorkoutTreePanelWidthDp(widthDp: Float, persist: Boolean = true) {
        val clampedWidth = widthDp.coerceIn(
            MIN_WORKOUT_TREE_PANEL_WIDTH_DP,
            MAX_WORKOUT_TREE_PANEL_WIDTH_DP,
        )
        workoutTreePanelWidthDp = clampedWidth
        if (persist) {
            hasSavedWorkoutTreePanelWidth = true
            persistWorkoutTreePanelWidthDp(clampedWidth)
        }
    }

    fun updateDiagnosticsPanelWidthDp(widthDp: Float, persist: Boolean = true) {
        val clampedWidth = widthDp.coerceIn(
            MIN_DIAGNOSTICS_PANEL_WIDTH_DP,
            MAX_DIAGNOSTICS_PANEL_WIDTH_DP,
        )
        diagnosticsPanelWidthDp = clampedWidth
        if (persist) {
            hasSavedDiagnosticsPanelWidth = true
            persistDiagnosticsPanelWidthDp(clampedWidth)
        }
    }

    fun resetWorkoutTreePanelWidth() {
        hasSavedWorkoutTreePanelWidth = false
        try {
            workoutTreePanelWidthFile.delete()
        } catch (_: Exception) {}
        applyAdaptivePanelDefaults(lastKnownWindowWidthDp)
        layoutWidthPreferenceVersion++
        statusMessage = "Workout flow panel width reset."
        lastOperationDiagnostics = LastOperationDiagnostics(
            operation = "Reset workout flow panel width",
            targetName = currentFile?.name,
            outcome = "success",
            detail = "Restored the workout flow rail width to the adaptive desktop default.",
        )
    }

    fun resetDiagnosticsPanelWidth() {
        hasSavedDiagnosticsPanelWidth = false
        try {
            diagnosticsPanelWidthFile.delete()
        } catch (_: Exception) {}
        applyAdaptivePanelDefaults(lastKnownWindowWidthDp)
        layoutWidthPreferenceVersion++
        statusMessage = "Diagnostics panel width reset."
        lastOperationDiagnostics = LastOperationDiagnostics(
            operation = "Reset diagnostics panel width",
            targetName = currentFile?.name,
            outcome = "success",
            detail = "Restored the diagnostics rail width to the adaptive desktop default.",
        )
    }

    fun initialFileChooserDirectory(): File? {
        return currentFile?.parentFile
            ?: lastFileChooserDirectory
            ?: recentFiles.firstOrNull()?.parentFile
    }

    fun rememberFileChooserDirectory(directory: File?) {
        if (directory == null || !directory.exists() || !directory.isDirectory) return
        lastFileChooserDirectory = directory
        try {
            lastFileChooserDirectoryFile.parentFile?.mkdirs()
            lastFileChooserDirectoryFile.writeText(directory.absolutePath)
        } catch (_: Exception) {}
    }

    fun updatePreviewFtpWatts(value: Int?) {
        ftpWatts = value
        recomputePreviewFromProfileInput("FTP updated")
    }

    fun updatePreviewHrMaxBpm(value: Int?) {
        hrMaxBpm = value
        recomputePreviewFromProfileInput("Max heart rate updated")
    }

    fun updatePreviewRestingHrBpm(value: Int?) {
        restingHrBpm = value
        recomputePreviewFromProfileInput("Resting heart rate updated")
    }

    fun updatePreviewLthrBpm(value: Int?) {
        lthrBpm = value
        recomputePreviewFromProfileInput("Threshold heart rate updated")
    }

    // --- Clipboard ---

    fun copySelected() {
        clipboard = document.selectedNodeId?.let { document.findSegment(it) }
    }

    fun cutSelected() {
        val nodeId = document.selectedNodeId ?: return
        clipboard = document.findSegment(nodeId) ?: return
        dispatch(DeleteSegment(nodeId))
    }

    fun pasteAtSelected() {
        val seg = clipboard ?: return
        dispatch(PasteSegment(seg, document.selectedNodeId))
    }

    // --- Recent files ---

    fun clearRecentFiles() {
        recentFiles = emptyList()
        try { recentFilesFile.delete() } catch (_: Exception) {}
    }

    private fun addToRecentFiles(file: File) {
        try {
            val paths = listOf(file.absolutePath) +
                recentFiles.map { it.absolutePath }.filter { it != file.absolutePath }
            val updated = paths.take(8)
            recentFiles = updated.map { File(it) }
            recentFilesFile.parentFile?.mkdirs()
            recentFilesFile.writeText(updated.joinToString("\n"))
        } catch (_: Exception) {}
    }

    private fun loadRecentFiles(): List<File> = try {
        if (!recentFilesFile.exists()) emptyList()
        else recentFilesFile.readLines()
            .filter { it.isNotBlank() }
            .map { File(it.trim()) }
            .filter { it.exists() }
            .take(8)
    } catch (_: Exception) { emptyList() }

    private fun loadLastFileChooserDirectory(): File? = try {
        if (!lastFileChooserDirectoryFile.exists()) null
        else File(lastFileChooserDirectoryFile.readText().trim())
            .takeIf { it.exists() && it.isDirectory }
    } catch (_: Exception) { null }

    private fun loadOnboardingDismissed(): Boolean = try {
        onboardingDismissedFile.exists() && onboardingDismissedFile.readText().trim() == "true"
    } catch (_: Exception) { false }

    private fun loadWorkoutTreePanelWidthDp(): Float = try {
        if (!workoutTreePanelWidthFile.exists()) {
            DEFAULT_WORKOUT_TREE_PANEL_WIDTH_DP
        } else {
            workoutTreePanelWidthFile.readText().trim().toFloatOrNull()
                ?.coerceIn(MIN_WORKOUT_TREE_PANEL_WIDTH_DP, MAX_WORKOUT_TREE_PANEL_WIDTH_DP)
                ?: DEFAULT_WORKOUT_TREE_PANEL_WIDTH_DP
        }
    } catch (_: Exception) { DEFAULT_WORKOUT_TREE_PANEL_WIDTH_DP }

    private fun persistOnboardingDismissed(dismissed: Boolean) {
        try {
            onboardingDismissedFile.parentFile?.mkdirs()
            onboardingDismissedFile.writeText(dismissed.toString())
        } catch (_: Exception) {}
    }

    private fun persistWorkoutTreePanelWidthDp(widthDp: Float) {
        try {
            workoutTreePanelWidthFile.parentFile?.mkdirs()
            workoutTreePanelWidthFile.writeText(widthDp.toString())
        } catch (_: Exception) {}
    }

    private fun loadDiagnosticsPanelWidthDp(): Float = try {
        if (!diagnosticsPanelWidthFile.exists()) {
            DEFAULT_DIAGNOSTICS_PANEL_WIDTH_DP
        } else {
            diagnosticsPanelWidthFile.readText().trim().toFloatOrNull()
                ?.coerceIn(MIN_DIAGNOSTICS_PANEL_WIDTH_DP, MAX_DIAGNOSTICS_PANEL_WIDTH_DP)
                ?: DEFAULT_DIAGNOSTICS_PANEL_WIDTH_DP
        }
    } catch (_: Exception) { DEFAULT_DIAGNOSTICS_PANEL_WIDTH_DP }

    private fun persistDiagnosticsPanelWidthDp(widthDp: Float) {
        try {
            diagnosticsPanelWidthFile.parentFile?.mkdirs()
            diagnosticsPanelWidthFile.writeText(widthDp.toString())
        } catch (_: Exception) {}
    }

    private fun recommendedWorkoutTreePanelWidthDp(windowWidthDp: Float): Float = when {
        windowWidthDp >= LARGE_WINDOW_WIDTH_DP -> LARGE_WORKOUT_TREE_PANEL_WIDTH_DP
        windowWidthDp >= COMPACT_WINDOW_WIDTH_DP -> COMPACT_WORKOUT_TREE_PANEL_WIDTH_DP
        else -> DEFAULT_WORKOUT_TREE_PANEL_WIDTH_DP
    }

    private fun recommendedDiagnosticsPanelWidthDp(windowWidthDp: Float): Float = when {
        windowWidthDp >= LARGE_WINDOW_WIDTH_DP -> LARGE_DIAGNOSTICS_PANEL_WIDTH_DP
        windowWidthDp >= COMPACT_WINDOW_WIDTH_DP -> COMPACT_DIAGNOSTICS_PANEL_WIDTH_DP
        else -> MIN_DIAGNOSTICS_PANEL_WIDTH_DP
    }

    /** Recompute preview by serializing the current document and re-parsing through EwoEngine. */
    fun recomputePreview() {
        val json = buildCanonicalJson(document)
        val result = EwoEngine.analyze(json, buildCompileContext())
        updatePreviewFromResult(result)
        lastOperationDiagnostics = when (result) {
            is EwoWorkoutParseResult.Success.Compiled -> LastOperationDiagnostics(
                operation = "Recompute preview",
                targetName = currentFile?.name,
                outcome = "compiled",
                detail = "Preview compiled successfully.",
            )
            is EwoWorkoutParseResult.Success.NeedsCompileContext -> LastOperationDiagnostics(
                operation = "Recompute preview",
                targetName = currentFile?.name,
                outcome = "needs_compile_context",
                detail = "Preview needs rider profile values: " +
                    result.compileErrors.joinToString("; ") { it.message },
            )
            is EwoWorkoutParseResult.Failure -> LastOperationDiagnostics(
                operation = "Recompute preview",
                targetName = currentFile?.name,
                outcome = "failure",
                detail = "${result.error.code.stableCode}: ${result.error.message}",
            )
        }
    }

    private fun recomputePreviewFromProfileInput(prefix: String) {
        recomputePreview()
        statusMessage = when (lastOperationDiagnostics?.outcome) {
            "compiled" -> "$prefix. Preview updated."
            "needs_compile_context" -> "$prefix. More preview rider profile values are still needed."
            "failure" -> "$prefix. Preview update failed."
            else -> "$prefix."
        }
    }

    private fun updatePreviewFromResult(result: EwoWorkoutParseResult) {
        preview = when (result) {
            is EwoWorkoutParseResult.Success.Compiled -> buildEditorPreview(
                compiled = result.compiled,
                sanityResult = result.sanityResult,
                ftpWatts = ftpWatts,
            )
            is EwoWorkoutParseResult.Success.NeedsCompileContext -> {
                // Compilation failed (typically FTP not set for ftp_percent targets).
                // Build chart bars directly from editor segments so the profile is
                // still visible — Y-axis will show % FTP instead of absolute watts.
                val fallbackBars = buildChartBarsFromSegments(document.segments)
                val totalDuration = fallbackBars.maxOfOrNull { it.endSec } ?: 0
                EditorPreview(
                    steps = emptyList(),
                    totalDurationSec = totalDuration,
                    intensityFactor = null,
                    tss = null,
                    sanityWarnings = emptyList(),
                    compileErrors = result.compileErrors.map { it.message },
                    chartBars = fallbackBars,
                    chartPowerUnit = ChartPowerUnit.FTP_PERCENT,
                )
            }
            is EwoWorkoutParseResult.Failure -> {
                // Parse/validation failed (e.g. empty title), but editor segments
                // may still be renderable — show the profile chart from raw segments.
                val fallbackBars = buildChartBarsFromSegments(document.segments)
                val totalDuration = fallbackBars.maxOfOrNull { it.endSec } ?: 0
                EditorPreview(
                    steps = emptyList(),
                    totalDurationSec = totalDuration,
                    intensityFactor = null,
                    tss = null,
                    sanityWarnings = emptyList(),
                    compileErrors = listOf(result.error.message),
                    chartBars = fallbackBars,
                    chartPowerUnit = ChartPowerUnit.FTP_PERCENT,
                )
            }
        }
    }
}

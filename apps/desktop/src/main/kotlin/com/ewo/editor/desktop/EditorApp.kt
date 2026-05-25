package com.ewo.editor.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.ewo.editor.desktop.panel.DiagnosticsPanel
import com.ewo.editor.desktop.panel.PropertyInspectorPanel
import com.ewo.editor.desktop.panel.WorkoutMetadataPanel
import com.ewo.editor.desktop.panel.WorkoutProfileChart
import com.ewo.editor.desktop.panel.WorkoutTreePanel
import com.ewo.editor.desktop.state.EditorState
import com.ewo.editor.commands.EditorViewAction
import com.ewo.editor.desktop.EditorStrings
import com.ewo.editor.model.EditorNodeId
import com.ewo.editor.model.EditorSegment
import com.ewo.editor.model.findSegment
import java.awt.Cursor
import java.io.File
import java.nio.file.Path
import javax.swing.JOptionPane
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

@Composable
fun EditorApp(state: EditorState) {
    val density = LocalDensity.current

    BoxWithConstraints {
        LaunchedEffect(maxWidth, state.layoutWidthPreferenceVersion) {
            state.applyAdaptivePanelDefaults(maxWidth.value)
        }

        MaterialTheme(
            colorScheme = darkColorScheme(),
        ) {
            Scaffold(
                topBar = { EditorTopBar(state) },
                bottomBar = { EditorStatusBar(state) },
            ) { padding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) {
                    // Left column — Segment tree
                    Surface(
                        modifier = Modifier.width(state.workoutTreePanelWidthDp.dp).fillMaxHeight(),
                        tonalElevation = 1.dp,
                    ) {
                        WorkoutTreePanel(state)
                    }

                    LeadingResizeHandle(
                        onDrag = { dragDeltaPx ->
                            state.updateWorkoutTreePanelWidthDp(
                                widthDp = state.workoutTreePanelWidthDp + (dragDeltaPx / density.density),
                                persist = false,
                            )
                        },
                        onDragEnd = {
                            state.updateWorkoutTreePanelWidthDp(state.workoutTreePanelWidthDp)
                        },
                    )

                    // Center column — editor workspace or lightweight help view
                    if (state.activeOnboardingPath != null) {
                        OnboardingWorkspace(
                            state = state,
                            path = state.activeOnboardingPath!!,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    } else {
                        CenterWorkspace(state, Modifier.weight(1f).fillMaxHeight())
                    }

                    TrailingResizeHandle(
                        onDrag = { dragDeltaPx ->
                            state.updateDiagnosticsPanelWidthDp(
                                widthDp = state.diagnosticsPanelWidthDp - (dragDeltaPx / density.density),
                                persist = false,
                            )
                        },
                        onDragEnd = {
                            state.updateDiagnosticsPanelWidthDp(state.diagnosticsPanelWidthDp)
                        },
                    )

                    // Right column — Diagnostics rail
                    Surface(
                        modifier = Modifier.width(state.diagnosticsPanelWidthDp.dp).fillMaxHeight(),
                        tonalElevation = 1.dp,
                    ) {
                        DiagnosticsPanel(state)
                    }
                }
            }
        }
    }
}

@Composable
private fun LeadingResizeHandle(
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    ResizeHandle(
        pointerIcon = Cursor(Cursor.E_RESIZE_CURSOR),
        onDrag = onDrag,
        onDragEnd = onDragEnd,
    )
}

@Composable
private fun TrailingResizeHandle(
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    ResizeHandle(
        pointerIcon = Cursor(Cursor.E_RESIZE_CURSOR),
        onDrag = onDrag,
        onDragEnd = onDragEnd,
    )
}

@Composable
private fun ResizeHandle(
    pointerIcon: Cursor,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(6.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            .pointerHoverIcon(PointerIcon(pointerIcon))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = onDragEnd,
                    onDragCancel = onDragEnd,
                ) { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.x)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(32.dp)
                .background(
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                    shape = MaterialTheme.shapes.extraSmall,
                ),
        )
    }
}

/** Center workspace with a draggable horizontal split between chart and inspector. */
@Composable
private fun CenterWorkspace(state: EditorState, modifier: Modifier) {
    val density = LocalDensity.current

    // Track total column height in px, and split fraction (chart share)
    var totalHeightPx by remember { mutableIntStateOf(0) }
    var splitFraction by remember { mutableFloatStateOf(0.40f) }

    Column(
        modifier = modifier.onSizeChanged { totalHeightPx = it.height },
    ) {
        WorkoutMetadataPanel(state)
        Spacer(Modifier.height(8.dp))

        // Chart area
        Surface(
            modifier = Modifier.fillMaxWidth().weight(splitFraction),
            tonalElevation = 0.dp,
        ) {
            val segIdToNodeId = remember(state.document.segments) {
                buildSegmentIdToNodeId(state.document.segments)
            }
            WorkoutProfileChart(
                bars = state.preview.chartBars,
                totalDurationSec = state.preview.totalDurationSec,
                ftpWatts = state.ftpWatts,
                powerUnit = state.preview.chartPowerUnit,
                highlightedSegmentIds = selectedSegmentIds(state),
                compileErrors = if (state.document.segments.isNotEmpty()) {
                    state.preview.compileErrors
                } else {
                    emptyList()
                },
                onBarClick = { segmentId ->
                    segIdToNodeId[segmentId]?.let { nodeId ->
                        state.dispatchView(EditorViewAction.Select(nodeId))
                    }
                },
                modifier = Modifier.fillMaxSize().padding(12.dp),
            )
        }

        // Draggable divider handle
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                .pointerHoverIcon(PointerIcon(Cursor(Cursor.N_RESIZE_CURSOR)))
                .pointerInput(Unit) {
                    detectDragGestures { _, dragAmount ->
                        if (totalHeightPx > 0) {
                            val delta = dragAmount.y / totalHeightPx
                            splitFraction = (splitFraction + delta).coerceIn(0.20f, 0.70f)
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            // Subtle grip indicator
            Box(
                modifier = Modifier
                    .width(32.dp)
                    .height(2.dp)
                    .background(
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        shape = MaterialTheme.shapes.extraSmall,
                    ),
            )
        }

        // Property inspector
        Surface(
            modifier = Modifier.fillMaxWidth().weight(1f - splitFraction),
        ) {
            PropertyInspectorPanel(state)
        }
    }
}

@Composable
private fun OnboardingWorkspace(
    state: EditorState,
    path: EditorState.OnboardingPath,
    modifier: Modifier,
) {
    val guide = state.onboardingGuide(path)

    Surface(
        modifier = modifier,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        "Editor help",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "Use this as a lightweight reference while the workout tree stays visible on the left.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { state.closeOnboardingGuide() }) {
                    Icon(Icons.Default.Close, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Return to editor")
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OnboardingPathChip(
                    title = "First workout",
                    selected = path == EditorState.OnboardingPath.FIRST_WORKOUT,
                    onClick = { state.showOnboardingPath(EditorState.OnboardingPath.FIRST_WORKOUT) },
                )
                OnboardingPathChip(
                    title = "FTP path",
                    selected = path == EditorState.OnboardingPath.KNOWS_FTP,
                    onClick = { state.showOnboardingPath(EditorState.OnboardingPath.KNOWS_FTP) },
                )
                OnboardingPathChip(
                    title = "HR-based",
                    selected = path == EditorState.OnboardingPath.EXPLORE_HR,
                    onClick = { state.showOnboardingPath(EditorState.OnboardingPath.EXPLORE_HR) },
                )
            }

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        guide.title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Text(
                        guide.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Text(
                        guide.primaryActionLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }

            guide.sections.forEach { section ->
                OutlinedCard {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            section.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        section.items.forEachIndexed { index, item ->
                            Text(
                                "${index + 1}. $item",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = MaterialTheme.shapes.medium,
            ) {
                Text(
                    guide.closingNote,
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun OnboardingPathChip(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    if (selected) {
        Button(onClick = onClick) { Text(title) }
    } else {
        OutlinedButton(onClick = onClick) { Text(title) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorTopBar(state: EditorState) {
    TopAppBar(
        title = {
            val fileName = state.currentFile?.name ?: "Untitled"
            val dirty = if (state.document.isDirty) " *" else ""
            Text("Workout Editor — $fileName$dirty")
        },
        actions = {
            // New
            IconButton(onClick = { state.newDocument() }) {
                Icon(Icons.Default.Add, "New")
            }
            // Open
            IconButton(onClick = { showOpenDialog(state) }) {
                Icon(Icons.Default.FolderOpen, "Open")
            }
            // Save
            IconButton(
                onClick = {
                    if (state.currentFile != null) {
                        state.saveFile()
                    } else {
                        showSaveDialog(state)
                    }
                },
            ) {
                Icon(Icons.Default.Save, "Save")
            }

            Spacer(Modifier.width(16.dp))

            // Undo
            IconButton(
                onClick = { state.undo() },
                enabled = state.history.canUndo,
            ) {
                Icon(Icons.AutoMirrored.Filled.Undo, "Undo")
            }
            // Redo
            IconButton(
                onClick = { state.redo() },
                enabled = state.history.canRedo,
            ) {
                Icon(Icons.AutoMirrored.Filled.Redo, "Redo")
            }
        },
    )
}

@Composable
private fun EditorStatusBar(state: EditorState) {
    val preview = state.preview
    val doc = state.document
    val segmentCount = countAllSegments(doc.segments)
    val totalDuration = if (preview.totalDurationSec > 0) preview.totalDurationSec else computeRawDuration(doc.segments)

    Surface(tonalElevation = 2.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Status message
            Text(
                state.statusMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )

            // Segment count
            Text(
                "$segmentCount segments",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Total duration
            Text(
                formatDurationCompact(totalDuration),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // IF/TSS if available
            val ifVal = preview.intensityFactor
            val tssVal = preview.tss
            if (ifVal != null && tssVal != null) {
                Text(
                    "IF %.2f".format(ifVal),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "TSS %.0f".format(tssVal),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            // Version badge
            Text(
                "v${doc.version}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}


fun showOpenDialog(state: EditorState) {
    val chooser = JFileChooser().apply {
        fileFilter = FileNameExtensionFilter("EWO Workout Files (*.ewo)", "ewo")
        dialogTitle = "Open EWO File"
        currentDirectory = state.initialFileChooserDirectory()
    }
    if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        state.rememberFileChooserDirectory(chooser.currentDirectory)
        state.openFile(chooser.selectedFile)
    }
}

fun showSaveDialog(state: EditorState) {
    val chooser = JFileChooser().apply {
        fileFilter = FileNameExtensionFilter("EWO Workout Files (*.ewo)", "ewo")
        dialogTitle = "Save EWO File"
        currentDirectory = state.initialFileChooserDirectory()
        selectedFile = File("workout.ewo")
    }
    if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
        state.rememberFileChooserDirectory(chooser.currentDirectory)
        val saveTarget = resolveSaveTarget(
            selectedFile = chooser.selectedFile,
            currentFile = state.currentFile,
        )
        if (!saveTarget.requiresOverwriteConfirmation || confirmOverwriteSave(saveTarget.file)) {
            state.saveFile(saveTarget.file)
        }
    }
}

internal data class SaveTarget(
    val file: File,
    val requiresOverwriteConfirmation: Boolean,
)

/**
 * Normalizes Save As targets before writing so the UI can warn only when the
 * user is about to replace a different existing file.
 */
internal fun resolveSaveTarget(
    selectedFile: File,
    currentFile: File?,
): SaveTarget {
    val normalizedFile = ensureEwoExtension(selectedFile).toNormalizedAbsoluteFile()
    val requiresOverwriteConfirmation = normalizedFile.exists() &&
        !isSameFilePath(normalizedFile, currentFile)
    return SaveTarget(
        file = normalizedFile,
        requiresOverwriteConfirmation = requiresOverwriteConfirmation,
    )
}

private fun ensureEwoExtension(file: File): File =
    if (file.name.endsWith(".ewo")) file else File(file.parentFile, "${file.name}.ewo")

private fun isSameFilePath(left: File, right: File?): Boolean {
    if (right == null) return false
    return left.toNormalizedAbsolutePath() == right.toNormalizedAbsolutePath()
}

private fun File.toNormalizedAbsoluteFile(): File = toNormalizedAbsolutePath().toFile()

private fun File.toNormalizedAbsolutePath(): Path = toPath().toAbsolutePath().normalize()

private fun confirmOverwriteSave(file: File): Boolean =
    JOptionPane.showConfirmDialog(
        null,
        "Replace the existing file?\n${file.absolutePath}",
        "Overwrite existing file",
        JOptionPane.YES_NO_OPTION,
        JOptionPane.WARNING_MESSAGE,
    ) == JOptionPane.YES_OPTION

/** Handles keyboard shortcuts at the window level. Returns true if consumed. */
fun handleKeyEvent(event: KeyEvent, state: EditorState): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    val ctrl = event.isCtrlPressed || event.isMetaPressed

    return when {
        ctrl && event.key == Key.Z && event.isShiftPressed -> {
            state.redo(); true
        }
        ctrl && event.key == Key.Z -> {
            state.undo(); true
        }
        ctrl && event.key == Key.S -> {
            if (state.currentFile != null) state.saveFile() else showSaveDialog(state)
            true
        }
        ctrl && event.key == Key.O -> {
            showOpenDialog(state); true
        }
        ctrl && event.key == Key.N -> {
            state.newDocument(); true
        }
        ctrl && event.key == Key.C -> {
            state.copySelected(); true
        }
        ctrl && event.key == Key.X -> {
            state.cutSelected(); true
        }
        ctrl && event.key == Key.V -> {
            state.pasteAtSelected(); true
        }
        else -> false
    }
}

/** Collects all segmentIds that should be highlighted for the current selection. */
private fun selectedSegmentIds(state: EditorState): Set<String> {
    val nodeId = state.document.selectedNodeId ?: return emptySet()
    val segment = state.document.findSegment(nodeId) ?: return emptySet()
    return collectSegmentIds(segment)
}

private fun collectSegmentIds(segment: EditorSegment): Set<String> = when (segment) {
    is EditorSegment.Repeat -> segment.segments.flatMapTo(mutableSetOf()) { collectSegmentIds(it) }
    else -> setOf(segment.segmentId)
}

/** Builds a segmentId → EditorNodeId index for click-to-select from the chart. */
private fun buildSegmentIdToNodeId(segments: List<EditorSegment>): Map<String, EditorNodeId> = buildMap {
    fun visit(list: List<EditorSegment>) {
        for (seg in list) {
            put(seg.segmentId, seg.nodeId)
            if (seg is EditorSegment.Repeat) visit(seg.segments)
        }
    }
    visit(segments)
}

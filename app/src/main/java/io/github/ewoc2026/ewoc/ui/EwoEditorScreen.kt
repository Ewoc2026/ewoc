package io.github.ewoc2026.ewoc.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ewo.core.HrReference
import com.ewo.editor.commands.*
import com.ewo.editor.model.*
import io.github.ewoc2026.ewoc.R
import io.github.ewoc2026.ewoc.WebsiteGuideDestination
import io.github.ewoc2026.ewoc.ewoeditor.EwoEditorSnapshot
import io.github.ewoc2026.ewoc.ui.components.EditorWorkoutProfileChart

/**
 * Sealed interface for actions emitted by [EwoEditorScreen].
 */
internal sealed interface EwoEditorScreenAction {
    data object Back : EwoEditorScreenAction
    data object NewDocument : EwoEditorScreenAction
    data object OpenFile : EwoEditorScreenAction
    data object SaveFile : EwoEditorScreenAction
    data object Undo : EwoEditorScreenAction
    data object Redo : EwoEditorScreenAction
    data class Dispatch(val command: EditorCommand) : EwoEditorScreenAction
    data class DispatchView(val action: EditorViewAction) : EwoEditorScreenAction
    data class SetFtp(val value: Int?) : EwoEditorScreenAction
    data class SetHrMax(val value: Int?) : EwoEditorScreenAction
    data class SetRestingHr(val value: Int?) : EwoEditorScreenAction
    data class SetLthr(val value: Int?) : EwoEditorScreenAction
    data class CopySegment(val nodeId: EditorNodeId) : EwoEditorScreenAction
    data object PasteSegment : EwoEditorScreenAction
    data class MoveSegment(val nodeId: EditorNodeId, val direction: MoveDirection) : EwoEditorScreenAction
}

internal sealed interface CadenceInputResolution {
    data object KeepExisting : CadenceInputResolution
    data object Clear : CadenceInputResolution
    data class Update(val cadence: EditorCadenceRange) : CadenceInputResolution
}

internal sealed interface IntegerInputResolution {
    data object KeepExisting : IntegerInputResolution
    data class Update(val value: Int) : IntegerInputResolution
}

internal sealed interface PositiveIntegerInputResolution {
    data object KeepExisting : PositiveIntegerInputResolution
    data class Update(val value: Int) : PositiveIntegerInputResolution
}

internal sealed interface OptionalIntegerInputResolution {
    data object KeepExisting : OptionalIntegerInputResolution
    data object Clear : OptionalIntegerInputResolution
    data class Update(val value: Int) : OptionalIntegerInputResolution
}

internal sealed interface IntBandInputResolution {
    data object KeepExisting : IntBandInputResolution
    data class Update(val low: Int, val high: Int) : IntBandInputResolution
}

private enum class SteadyTargetMode {
    WATTS,
    FTP_PERCENT,
    HEART_RATE,
    HEART_RATE_RELATIVE,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EwoEditorScreen(
    snapshot: EwoEditorSnapshot,
    onAction: (EwoEditorScreenAction) -> Unit,
) {
    val doc = snapshot.document
    val preview = snapshot.preview
    val visibleValidationMarkers = remember(doc.validationMarkers, preview.compileErrors) {
        doc.validationMarkers.filterNot(::isResolvedCompileContextMarker)
    }
    val athleteProfileNeedsAttention = remember(preview.compileErrors) {
        preview.compileErrors.any(::isAthleteProfileSupportError)
    }
    val controlBlockNeedsAttention = remember(doc.control, doc.segments) {
        doc.hasHrTargets() && doc.control == null
    }
    val previewDetailsVisible = preview.intensityFactor != null ||
        preview.tss != null ||
        preview.chartBars.isNotEmpty() ||
        preview.sanityWarnings.isNotEmpty() ||
        preview.compileErrors.isNotEmpty() ||
        visibleValidationMarkers.isNotEmpty() ||
        preview.steps.isNotEmpty()
    val previewDetailsNeedsAttention = preview.sanityWarnings.isNotEmpty() ||
        preview.compileErrors.isNotEmpty() ||
        visibleValidationMarkers.isNotEmpty()

    var athleteProfileExpanded by rememberSaveable { mutableStateOf(athleteProfileNeedsAttention) }
    var controlBlockExpanded by rememberSaveable { mutableStateOf(controlBlockNeedsAttention) }
    var previewDetailsExpanded by rememberSaveable { mutableStateOf(previewDetailsNeedsAttention) }

    LaunchedEffect(athleteProfileNeedsAttention) {
        if (athleteProfileNeedsAttention) athleteProfileExpanded = true
    }
    LaunchedEffect(controlBlockNeedsAttention) {
        if (controlBlockNeedsAttention) controlBlockExpanded = true
    }
    LaunchedEffect(previewDetailsNeedsAttention) {
        if (previewDetailsNeedsAttention) previewDetailsExpanded = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        snapshot.currentFileName ?: stringResource(R.string.ewo_editor_new_document_title),
                        maxLines = 1,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { onAction(EwoEditorScreenAction.Back) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.ewo_editor_back))
                    }
                },
                actions = {
                    TextButton(
                        onClick = { onAction(EwoEditorScreenAction.Undo) },
                        enabled = snapshot.canUndo,
                    ) {
                        Text(stringResource(R.string.ewo_editor_undo))
                    }
                    TextButton(
                        onClick = { onAction(EwoEditorScreenAction.Redo) },
                        enabled = snapshot.canRedo,
                    ) {
                        Text(stringResource(R.string.ewo_editor_redo))
                    }
                },
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 2.dp,
                modifier = Modifier.navigationBarsPadding(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        snapshot.statusMessage,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { onAction(EwoEditorScreenAction.NewDocument) }) {
                            Text(stringResource(R.string.ewo_editor_new))
                        }
                        TextButton(onClick = { onAction(EwoEditorScreenAction.OpenFile) }) {
                            Text(stringResource(R.string.ewo_editor_open))
                        }
                        TextButton(
                            onClick = { onAction(EwoEditorScreenAction.SaveFile) },
                            enabled = doc.canExport,
                        ) {
                            Text(stringResource(R.string.ewo_editor_save))
                        }
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            // Workout title
            item {
                WorkoutTitleSection(doc, onAction)
            }

            // F9: Workout description
            item {
                OutlinedTextField(
                    value = doc.description,
                    onValueChange = { onAction(EwoEditorScreenAction.Dispatch(SetWorkoutDescription(it))) },
                    label = { Text(stringResource(R.string.ewo_editor_description_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                )
            }

            // Export status
            item {
                ExportBanner(doc)
            }

            if (snapshot.openedFromBundledLibrary) {
                item {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = stringResource(R.string.ewo_editor_bundled_source_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        )
                    }
                }
            }

            // Segment list
            item {
                SegmentListHeader(snapshot, onAction)
            }

            items(doc.segments) { segment ->
                SegmentCard(segment, doc.selectedNodeId, doc, onAction)
            }

            // Selected segment properties
            val selectedId = doc.selectedNodeId
            if (selectedId != null) {
                val segment = doc.findSegment(selectedId)
                if (segment != null) {
                    item {
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        SelectedSegmentProperties(segment, onAction)
                    }
                }
            }

            item {
                CollapsibleSupportPanel(
                    title = stringResource(R.string.ewo_editor_athlete_profile_title),
                    supportingText = stringResource(R.string.ewo_editor_athlete_profile_hint),
                    expanded = athleteProfileExpanded,
                    onExpandedChange = { athleteProfileExpanded = it },
                    contentTag = "ewo_editor_support_panel_athlete_content",
                ) {
                    AthleteProfileSection(snapshot, onAction)
                }
            }

            item {
                CollapsibleSupportPanel(
                    title = stringResource(R.string.ewo_editor_control_block_title),
                    supportingText = stringResource(R.string.ewo_editor_control_block_hint),
                    expanded = controlBlockExpanded,
                    onExpandedChange = { controlBlockExpanded = it },
                    contentTag = "ewo_editor_support_panel_control_content",
                ) {
                    ControlBlockSection(doc, onAction)
                }
            }

            if (previewDetailsVisible) {
                item {
                    CollapsibleSupportPanel(
                        title = stringResource(R.string.ewo_editor_preview_details_title),
                        supportingText = stringResource(R.string.ewo_editor_preview_details_hint),
                        expanded = previewDetailsExpanded,
                        onExpandedChange = { previewDetailsExpanded = it },
                        contentTag = "ewo_editor_support_panel_preview_content",
                    ) {
                        PreviewDetailsSection(snapshot)
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkoutTitleSection(doc: EditorWorkoutDocument, onAction: (EwoEditorScreenAction) -> Unit) {
    OutlinedTextField(
        value = doc.title,
        onValueChange = { onAction(EwoEditorScreenAction.Dispatch(SetWorkoutTitle(it))) },
        label = { Text(stringResource(R.string.ewo_editor_title_label)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun AthleteProfileSection(snapshot: EwoEditorSnapshot, onAction: (EwoEditorScreenAction) -> Unit) {
    var ftpText by remember(snapshot.ftpWatts) {
        mutableStateOf(snapshot.ftpWatts?.toString().orEmpty())
    }
    var hrMaxText by remember(snapshot.hrMaxBpm) {
        mutableStateOf(snapshot.hrMaxBpm?.toString().orEmpty())
    }
    var restingHrText by remember(snapshot.restingHrBpm) {
        mutableStateOf(snapshot.restingHrBpm?.toString().orEmpty())
    }
    var lthrText by remember(snapshot.lthrBpm) {
        mutableStateOf(snapshot.lthrBpm?.toString().orEmpty())
    }

    Text(
        text = stringResource(R.string.ewo_editor_help_profile_scope),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = ftpText,
            onValueChange = { text ->
                ftpText = text
                resolveOptionalIntegerAction(text, EwoEditorScreenAction::SetFtp)?.let(onAction)
            },
            label = { Text(stringResource(R.string.ewo_editor_ftp_label)) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = hrMaxText,
            onValueChange = { text ->
                hrMaxText = text
                resolveOptionalIntegerAction(text, EwoEditorScreenAction::SetHrMax)?.let(onAction)
            },
            label = { Text(stringResource(R.string.ewo_editor_hr_max_label)) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = restingHrText,
            onValueChange = { text ->
                restingHrText = text
                resolveOptionalIntegerAction(text, EwoEditorScreenAction::SetRestingHr)?.let(onAction)
            },
            label = { Text(stringResource(R.string.ewo_editor_resting_hr_label)) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = lthrText,
            onValueChange = { text ->
                lthrText = text
                resolveOptionalIntegerAction(text, EwoEditorScreenAction::SetLthr)?.let(onAction)
            },
            label = { Text(stringResource(R.string.ewo_editor_lthr_label)) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ExportBanner(doc: EditorWorkoutDocument) {
    val canExport = doc.canExport
    Surface(
        color = if (canExport) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = if (canExport) {
                    stringResource(R.string.ewo_editor_export_ready)
                } else {
                    stringResource(R.string.ewo_editor_export_blocked_label)
                },
                color = if (canExport) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodySmall,
            )
            if (!canExport) {
                Text(
                    text = stringResource(R.string.ewo_editor_help_export_blocked_next_step),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun IfTssSummary(preview: EditorPreview) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        preview.intensityFactor?.let { ifVal ->
            Text(
                stringResource(R.string.ewo_editor_if_value, "%.2f".format(ifVal)),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        preview.tss?.let { tssVal ->
            Text(
                stringResource(R.string.ewo_editor_tss_value, "%.1f".format(tssVal)),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun CollapsibleSupportPanel(
    title: String,
    supportingText: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    contentTag: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    SectionCard(title = null) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { onExpandedChange(!expanded) }) {
                val toggleDescription = if (expanded) {
                    stringResource(R.string.ewo_editor_hide_section, title)
                } else {
                    stringResource(R.string.ewo_editor_show_section, title)
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = toggleDescription,
                )
            }
        }
        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(contentTag),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                content()
            }
        }
    }
}

@Composable
private fun PreviewDetailsSection(snapshot: EwoEditorSnapshot) {
    val doc = snapshot.document
    val preview = snapshot.preview
    val visibleValidationMarkers = remember(doc.validationMarkers, preview.compileErrors) {
        doc.validationMarkers.filterNot(::isResolvedCompileContextMarker)
    }

    Text(
        text = stringResource(R.string.ewo_editor_help_preview_use_case),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    EnglishOnlyGuideLink(destination = WebsiteGuideDestination.ANDROID_EDITOR)

    if (preview.intensityFactor != null || preview.tss != null) {
        IfTssSummary(preview)
    }

    if (preview.chartBars.isNotEmpty()) {
        Text(
            stringResource(R.string.ewo_editor_workout_profile_title),
            style = MaterialTheme.typography.titleSmall,
        )
        EditorWorkoutProfileChart(
            chartBars = preview.chartBars,
            ftpWatts = snapshot.ftpWatts,
            powerUnit = preview.chartPowerUnit,
            modifier = Modifier.padding(vertical = 4.dp),
        )
    }

    if (preview.sanityWarnings.isNotEmpty()) {
        Text(
            stringResource(R.string.ewo_editor_sanity_warnings_title),
            style = MaterialTheme.typography.titleSmall,
        )
        preview.sanityWarnings.forEach { warning ->
            Text(
                stringResource(R.string.ewo_editor_warning_item, warning.message),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
    }

    if (preview.compileErrors.isNotEmpty()) {
        Text(
            stringResource(R.string.ewo_editor_compile_notes_title),
            style = MaterialTheme.typography.titleSmall,
        )
        preview.compileErrors.forEach { error ->
            Text(
                stringResource(R.string.ewo_editor_warning_item, error),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }

    if (visibleValidationMarkers.isNotEmpty()) {
        Text(
            stringResource(R.string.ewo_editor_validation_issues_title),
            style = MaterialTheme.typography.titleSmall,
        )
        visibleValidationMarkers.forEach { marker ->
            Text(
                stringResource(R.string.ewo_editor_validation_item, marker.severity.name, marker.message),
                style = MaterialTheme.typography.bodySmall,
                color = when (marker.severity) {
                    EditorValidationSeverity.ERROR -> MaterialTheme.colorScheme.error
                    EditorValidationSeverity.WARNING -> MaterialTheme.colorScheme.tertiary
                    EditorValidationSeverity.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }

    if (preview.steps.isNotEmpty()) {
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        Text(
            stringResource(R.string.ewo_editor_compiled_steps_title, preview.steps.size),
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            stringResource(R.string.ewo_editor_compiled_steps_total, formatDurationCompact(preview.totalDurationSec)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        preview.steps.forEach { step ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "${step.index + 1}. ${step.label}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    formatDurationCompact(step.durationSec),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun isAthleteProfileSupportError(error: String): Boolean {
    val normalized = error.lowercase()
    return normalized.contains("ftp") ||
        normalized.contains("athlete profile") ||
        normalized.contains("preview rider profile") ||
        normalized.contains("hr_max") ||
        normalized.contains("resting") ||
        normalized.contains("lthr")
}

@Composable
private fun SegmentListHeader(snapshot: EwoEditorSnapshot, onAction: (EwoEditorScreenAction) -> Unit) {
    var showAddMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.ewo_editor_segments_title), style = MaterialTheme.typography.titleSmall)
            Row {
                if (snapshot.hasClipboard) {
                    TextButton(onClick = { onAction(EwoEditorScreenAction.PasteSegment) }) {
                        Text(stringResource(R.string.ewo_editor_paste_segment))
                    }
                }
                Box {
                    IconButton(onClick = { showAddMenu = true }) {
                        Icon(Icons.Default.Add, stringResource(R.string.ewo_editor_add_segment))
                    }
                    DropdownMenu(expanded = showAddMenu, onDismissRequest = { showAddMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.ewo_editor_add_steady)) },
                            onClick = {
                                showAddMenu = false
                                onAction(EwoEditorScreenAction.Dispatch(AddSegment(segmentType = NewSegmentType.STEADY)))
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.ewo_editor_add_ramp)) },
                            onClick = {
                                showAddMenu = false
                                onAction(EwoEditorScreenAction.Dispatch(AddSegment(segmentType = NewSegmentType.RAMP)))
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.ewo_editor_add_repeat)) },
                            onClick = {
                                showAddMenu = false
                                onAction(EwoEditorScreenAction.Dispatch(AddSegment(segmentType = NewSegmentType.REPEAT)))
                            },
                        )
                    }
                }
            }
        }
        Text(
            text = stringResource(R.string.ewo_editor_help_segment_labels),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SegmentCard(
    segment: EditorSegment,
    selectedNodeId: EditorNodeId?,
    doc: EditorWorkoutDocument,
    onAction: (EwoEditorScreenAction) -> Unit,
) {
    val isSelected = segment.nodeId == selectedNodeId
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onAction(EwoEditorScreenAction.DispatchView(EditorViewAction.Select(segment.nodeId))) },
        colors = if (isSelected) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    segment.segmentId,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    segmentSummary(segment),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (isSelected) {
                // F2: Move up/down
                val canMoveUp = computeMoveTarget(doc.segments, segment.nodeId, MoveDirection.UP) != null
                val canMoveDown = computeMoveTarget(doc.segments, segment.nodeId, MoveDirection.DOWN) != null
                IconButton(
                    onClick = { onAction(EwoEditorScreenAction.MoveSegment(segment.nodeId, MoveDirection.UP)) },
                    enabled = canMoveUp,
                ) {
                    Icon(Icons.Default.KeyboardArrowUp, stringResource(R.string.ewo_editor_move_up))
                }
                IconButton(
                    onClick = { onAction(EwoEditorScreenAction.MoveSegment(segment.nodeId, MoveDirection.DOWN)) },
                    enabled = canMoveDown,
                ) {
                    Icon(Icons.Default.KeyboardArrowDown, stringResource(R.string.ewo_editor_move_down))
                }
                // F6: Copy
                TextButton(
                    onClick = { onAction(EwoEditorScreenAction.CopySegment(segment.nodeId)) },
                ) {
                    Text(stringResource(R.string.ewo_editor_copy_segment))
                }
                IconButton(
                    onClick = { onAction(EwoEditorScreenAction.Dispatch(DeleteSegment(segment.nodeId))) },
                ) {
                    Icon(
                        Icons.Default.Delete,
                        stringResource(R.string.ewo_editor_delete_segment),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        // Show repeat children
        if (segment is EditorSegment.Repeat) {
            segment.segments.forEach { child ->
                Row(
                    modifier = Modifier
                        .padding(start = 24.dp, end = 12.dp, bottom = 4.dp)
                        .fillMaxWidth()
                        .clickable { onAction(EwoEditorScreenAction.DispatchView(EditorViewAction.Select(child.nodeId))) },
                ) {
                    Text(
                        "${child.segmentId}: ${segmentSummary(child)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (child.nodeId == selectedNodeId) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectedSegmentProperties(segment: EditorSegment, onAction: (EwoEditorScreenAction) -> Unit) {
    Text(stringResource(R.string.ewo_editor_properties_title), style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(4.dp))
    Text(
        text = stringResource(R.string.ewo_editor_help_segment_labels),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(4.dp))

    OutlinedTextField(
        value = segment.segmentId,
        onValueChange = { onAction(EwoEditorScreenAction.Dispatch(SetSegmentId(segment.nodeId, it))) },
        label = { Text(stringResource(R.string.ewo_editor_segment_id_label)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )

    Spacer(Modifier.height(4.dp))
    OutlinedTextField(
        value = segment.label ?: "",
        onValueChange = { onAction(setSegmentLabelAction(segment.nodeId, it)) },
        label = { Text(stringResource(R.string.ewo_editor_segment_label_label)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )

    Spacer(Modifier.height(4.dp))
    OutlinedTextField(
        value = segment.note ?: "",
        onValueChange = { onAction(setSegmentNoteAction(segment.nodeId, it)) },
        label = { Text(stringResource(R.string.ewo_editor_segment_note_label)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )

    when (segment) {
        is EditorSegment.Steady -> {
            var durationText by remember(segment.nodeId, segment.durationSec) {
                mutableStateOf(segment.durationSec.toString())
            }

            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = durationText,
                onValueChange = { text ->
                    durationText = text
                    resolveSegmentDurationAction(segment.nodeId, text)?.let(onAction)
                },
                label = { Text(stringResource(R.string.ewo_editor_duration_sec_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            SteadyTargetFields(segment, onAction)
        }
        is EditorSegment.Ramp -> {
            var durationText by remember(segment.nodeId, segment.durationSec) {
                mutableStateOf(segment.durationSec.toString())
            }

            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = durationText,
                onValueChange = { text ->
                    durationText = text
                    resolveSegmentDurationAction(segment.nodeId, text)?.let(onAction)
                },
                label = { Text(stringResource(R.string.ewo_editor_duration_sec_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // F1: Ramp from/to target editing (Power watts and FTP%)
            RampTargetFields(segment, onAction)
        }
        is EditorSegment.FreeRide -> {
            Spacer(Modifier.height(4.dp))
            Text(
                "Free Ride authoring is disabled in the in-app editor. Replace this segment with structured steps before saving for import use.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        is EditorSegment.Repeat -> {
            var repeatCountText by remember(segment.nodeId, segment.count) {
                mutableStateOf(segment.count.toString())
            }

            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = repeatCountText,
                onValueChange = { text ->
                    repeatCountText = text
                    resolveRepeatCountAction(segment.nodeId, text)?.let(onAction)
                },
                label = { Text(stringResource(R.string.ewo_editor_repeat_count_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // Add child segment dropdown
            Spacer(Modifier.height(8.dp))
            RepeatChildAddSection(segment, onAction)

            // Editable children list
            Spacer(Modifier.height(4.dp))
            segment.segments.forEachIndexed { index, child ->
                val isChildSelected = child.nodeId == segment.nodeId // never true, but children can be selected via card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, top = 2.dp, bottom = 2.dp)
                        .clickable {
                            onAction(EwoEditorScreenAction.DispatchView(EditorViewAction.Select(child.nodeId)))
                        },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${index + 1}. ${child.segmentId}: ${segmentSummary(child)}",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = {
                                onAction(EwoEditorScreenAction.Dispatch(DeleteSegment(child.nodeId)))
                            },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                stringResource(R.string.ewo_editor_delete_segment),
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    // F5: Segment messages
    SegmentMessagesSection(segment, onAction)
}

@Composable
private fun SegmentMessagesSection(segment: EditorSegment, onAction: (EwoEditorScreenAction) -> Unit) {
    Spacer(Modifier.height(8.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.ewo_editor_messages_title),
            style = MaterialTheme.typography.titleSmall,
        )
        TextButton(onClick = {
            onAction(
                EwoEditorScreenAction.Dispatch(
                    AddMessage(
                        parentNodeId = segment.nodeId,
                        kind = "instruction",
                        defaultText = "",
                    ),
                ),
            )
        }) {
            Text(stringResource(R.string.ewo_editor_add_message))
        }
    }

    segment.messages.forEach { msg ->
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                var textValue by remember(msg.nodeId, msg.defaultText) {
                    mutableStateOf(msg.defaultText)
                }
                OutlinedTextField(
                    value = textValue,
                    onValueChange = { text ->
                        textValue = text
                        onAction(
                            EwoEditorScreenAction.Dispatch(
                                UpdateMessage(messageNodeId = msg.nodeId, defaultText = text),
                            ),
                        )
                    },
                    label = { Text(stringResource(R.string.ewo_editor_message_text_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val validKinds = listOf("intro", "instruction", "transition", "warning", "motivation")
                    var kindMenuExpanded by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedButton(
                            onClick = { kindMenuExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(msg.kind)
                        }
                        DropdownMenu(
                            expanded = kindMenuExpanded,
                            onDismissRequest = { kindMenuExpanded = false },
                        ) {
                            validKinds.forEach { kind ->
                                DropdownMenuItem(
                                    text = { Text(kind) },
                                    onClick = {
                                        kindMenuExpanded = false
                                        onAction(
                                            EwoEditorScreenAction.Dispatch(
                                                UpdateMessage(messageNodeId = msg.nodeId, kind = kind),
                                            ),
                                        )
                                    },
                                )
                            }
                        }
                    }

                    // Anchor toggle
                    val anchorLabel = when (msg.timing.anchor) {
                        EditorMessageAnchor.START -> stringResource(R.string.ewo_editor_message_anchor_start)
                        EditorMessageAnchor.END -> stringResource(R.string.ewo_editor_message_anchor_end)
                    }
                    OutlinedButton(
                        onClick = {
                            val newAnchor = when (msg.timing.anchor) {
                                EditorMessageAnchor.START -> EditorMessageAnchor.END
                                EditorMessageAnchor.END -> EditorMessageAnchor.START
                            }
                            onAction(
                                EwoEditorScreenAction.Dispatch(
                                    UpdateMessage(
                                        messageNodeId = msg.nodeId,
                                        timing = msg.timing.copy(anchor = newAnchor),
                                    ),
                                ),
                            )
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(anchorLabel)
                    }
                }

                Spacer(Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    var offsetText by remember(msg.nodeId, msg.timing.offsetSec) {
                        mutableStateOf(msg.timing.offsetSec.toString())
                    }
                    OutlinedTextField(
                        value = offsetText,
                        onValueChange = { text ->
                            offsetText = text
                            val value = text.toIntOrNull() ?: return@OutlinedTextField
                            onAction(
                                EwoEditorScreenAction.Dispatch(
                                    UpdateMessage(
                                        messageNodeId = msg.nodeId,
                                        timing = msg.timing.copy(offsetSec = value),
                                    ),
                                ),
                            )
                        },
                        label = { Text(stringResource(R.string.ewo_editor_message_offset_label)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = {
                            onAction(EwoEditorScreenAction.Dispatch(DeleteMessage(msg.nodeId)))
                        },
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            stringResource(R.string.ewo_editor_delete_message),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun segmentSummary(segment: EditorSegment): String = when (segment) {
    is EditorSegment.Steady -> {
        val targetStr = when (val t = segment.target) {
            is EditorTarget.Power -> stringResource(R.string.ewo_editor_target_watts, t.watts)
            is EditorTarget.FtpPercent -> stringResource(R.string.ewo_editor_target_ftp_percent, (t.fraction * 100).toInt())
            is EditorTarget.HeartRate -> stringResource(R.string.ewo_editor_target_bpm_range, t.lowBpm, t.highBpm)
            is EditorTarget.HeartRateRelative -> stringResource(
                R.string.ewo_editor_target_hr_relative_range,
                (t.lowFraction * 100).toInt(),
                (t.highFraction * 100).toInt(),
                t.reference.stableCode,
            )
            null -> stringResource(R.string.ewo_editor_target_no_target)
        }
        stringResource(R.string.ewo_editor_segment_steady_summary, formatDurationCompact(segment.durationSec), targetStr)
    }
    is EditorSegment.Ramp -> {
        val fromStr = when (val t = segment.fromTarget) {
            is EditorTarget.Power -> stringResource(R.string.ewo_editor_target_watts, t.watts)
            is EditorTarget.FtpPercent -> stringResource(R.string.ewo_editor_target_ftp_percent, (t.fraction * 100).toInt())
            else -> stringResource(R.string.ewo_editor_target_unknown)
        }
        val toStr = when (val t = segment.toTarget) {
            is EditorTarget.Power -> stringResource(R.string.ewo_editor_target_watts, t.watts)
            is EditorTarget.FtpPercent -> stringResource(R.string.ewo_editor_target_ftp_percent, (t.fraction * 100).toInt())
            else -> stringResource(R.string.ewo_editor_target_unknown)
        }
        stringResource(R.string.ewo_editor_segment_ramp_summary, formatDurationCompact(segment.durationSec), fromStr, toStr)
    }
    is EditorSegment.FreeRide -> stringResource(
        R.string.ewo_editor_segment_free_ride_summary,
        formatDurationCompact(segment.durationSec),
    )
    is EditorSegment.Repeat -> stringResource(R.string.ewo_editor_segment_repeat_summary, segment.count, segment.segments.size)
}

private fun formatDurationCompact(totalSec: Int): String {
    val min = totalSec / 60
    val sec = totalSec % 60
    return if (sec == 0) "${min}m" else "${min}m${sec}s"
}

internal fun setSegmentLabelAction(nodeId: EditorNodeId, text: String): EwoEditorScreenAction.Dispatch =
    EwoEditorScreenAction.Dispatch(SetSegmentLabel(nodeId, normalizeOptionalEditorText(text)))

internal fun setSegmentNoteAction(nodeId: EditorNodeId, text: String): EwoEditorScreenAction.Dispatch =
    EwoEditorScreenAction.Dispatch(SetSegmentNote(nodeId, normalizeOptionalEditorText(text)))

internal fun resolveRepeatCountAction(nodeId: EditorNodeId, text: String): EwoEditorScreenAction.Dispatch? =
    when (val resolution = resolvePositiveIntegerInput(text)) {
        PositiveIntegerInputResolution.KeepExisting -> null
        is PositiveIntegerInputResolution.Update -> EwoEditorScreenAction.Dispatch(
            SetRepeatCount(nodeId, resolution.value),
        )
    }

internal fun normalizeOptionalEditorText(text: String): String? = text.ifBlank { null }

internal fun resolveOptionalIntegerAction(
    text: String,
    actionBuilder: (Int?) -> EwoEditorScreenAction,
): EwoEditorScreenAction? = when (val resolution = resolveOptionalIntegerInput(text)) {
    OptionalIntegerInputResolution.Clear -> actionBuilder(null)
    OptionalIntegerInputResolution.KeepExisting -> null
    is OptionalIntegerInputResolution.Update -> actionBuilder(resolution.value)
}

internal fun resolveSegmentDurationAction(nodeId: EditorNodeId, text: String): EwoEditorScreenAction.Dispatch? =
    when (val resolution = resolveIntegerInput(text)) {
        IntegerInputResolution.KeepExisting -> null
        is IntegerInputResolution.Update -> EwoEditorScreenAction.Dispatch(SetSegmentDuration(nodeId, resolution.value))
    }

internal fun resolveSteadyTargetPowerAction(nodeId: EditorNodeId, text: String): EwoEditorScreenAction.Dispatch? =
    when (val resolution = resolveIntegerInput(text)) {
        IntegerInputResolution.KeepExisting -> null
        is IntegerInputResolution.Update -> EwoEditorScreenAction.Dispatch(
            SetSteadyTarget(nodeId, EditorTarget.Power(resolution.value)),
        )
    }

internal fun resolveSteadyTargetFtpPercentAction(nodeId: EditorNodeId, text: String): EwoEditorScreenAction.Dispatch? {
    if (text.isBlank()) return null
    val percent = text.toIntOrNull() ?: return null
    return EwoEditorScreenAction.Dispatch(
        SetSteadyTarget(nodeId, EditorTarget.FtpPercent(percent / 100.0)),
    )
}

internal fun resolveSteadyTargetHeartRateAction(nodeId: EditorNodeId, text: String): EwoEditorScreenAction.Dispatch? =
    when (val resolution = resolveIntBandInput(text)) {
        IntBandInputResolution.KeepExisting -> null
        is IntBandInputResolution.Update -> EwoEditorScreenAction.Dispatch(
            SetSteadyTarget(nodeId, EditorTarget.HeartRate(resolution.low, resolution.high)),
        )
    }

internal fun resolveSteadyTargetHeartRateRelativeAction(
    nodeId: EditorNodeId,
    text: String,
    reference: HrReference,
): EwoEditorScreenAction.Dispatch? = when (val resolution = resolveIntBandInput(text)) {
    IntBandInputResolution.KeepExisting -> null
    is IntBandInputResolution.Update -> EwoEditorScreenAction.Dispatch(
        SetSteadyTarget(
            nodeId,
            EditorTarget.HeartRateRelative(
                reference = reference,
                lowFraction = resolution.low / 100.0,
                highFraction = resolution.high / 100.0,
            ),
        ),
    )
}

internal fun resolveIntegerInput(text: String): IntegerInputResolution {
    if (text.isBlank()) {
        return IntegerInputResolution.KeepExisting
    }

    val value = text.toIntOrNull() ?: return IntegerInputResolution.KeepExisting
    return IntegerInputResolution.Update(value)
}

internal fun resolvePositiveIntegerInput(text: String): PositiveIntegerInputResolution {
    if (text.isBlank()) {
        return PositiveIntegerInputResolution.KeepExisting
    }

    val value = text.toIntOrNull()
        ?.takeIf { it >= 1 }
        ?: return PositiveIntegerInputResolution.KeepExisting
    return PositiveIntegerInputResolution.Update(value)
}

internal fun resolveOptionalIntegerInput(text: String): OptionalIntegerInputResolution {
    if (text.isBlank()) {
        return OptionalIntegerInputResolution.Clear
    }

    val value = text.toIntOrNull() ?: return OptionalIntegerInputResolution.KeepExisting
    return OptionalIntegerInputResolution.Update(value)
}

internal fun resolveIntBandInput(text: String): IntBandInputResolution {
    if (text.isBlank()) {
        return IntBandInputResolution.KeepExisting
    }

    val parts = text.split("-", limit = 2)
        .map { it.trim() }
    if (parts.size != 2) {
        return IntBandInputResolution.KeepExisting
    }

    val low = parts[0].toIntOrNull() ?: return IntBandInputResolution.KeepExisting
    val high = parts[1].toIntOrNull() ?: return IntBandInputResolution.KeepExisting
    if (high < low) {
        return IntBandInputResolution.KeepExisting
    }

    return IntBandInputResolution.Update(low = low, high = high)
}

internal fun resolveCadenceInput(lowText: String, highText: String): CadenceInputResolution {
    if (lowText.isBlank() && highText.isBlank()) {
        return CadenceInputResolution.Clear
    }

    val low = lowText.toIntOrNull()
    val high = highText.toIntOrNull()
    if (low == null || high == null) {
        return CadenceInputResolution.KeepExisting
    }

    return CadenceInputResolution.Update(EditorCadenceRange(low = low, high = high))
}

private fun EditorTarget?.toSteadyTargetMode(): SteadyTargetMode = when (this) {
    is EditorTarget.Power, null -> SteadyTargetMode.WATTS
    is EditorTarget.FtpPercent -> SteadyTargetMode.FTP_PERCENT
    is EditorTarget.HeartRate -> SteadyTargetMode.HEART_RATE
    is EditorTarget.HeartRateRelative -> SteadyTargetMode.HEART_RATE_RELATIVE
}

private fun defaultSteadyTargetForMode(
    mode: SteadyTargetMode,
    existingTarget: EditorTarget?,
): EditorTarget = when (mode) {
    SteadyTargetMode.WATTS -> existingTarget as? EditorTarget.Power ?: EditorTarget.Power(200)
    SteadyTargetMode.FTP_PERCENT -> existingTarget as? EditorTarget.FtpPercent ?: EditorTarget.FtpPercent(0.75)
    SteadyTargetMode.HEART_RATE -> existingTarget as? EditorTarget.HeartRate ?: EditorTarget.HeartRate(130, 150)
    SteadyTargetMode.HEART_RATE_RELATIVE -> {
        existingTarget as? EditorTarget.HeartRateRelative
            ?: EditorTarget.HeartRateRelative(
                reference = HrReference.HR_MAX,
                lowFraction = 0.70,
                highFraction = 0.80,
            )
    }
}

private fun formatIntBand(low: Int, high: Int): String = "$low-$high"

private fun isResolvedCompileContextMarker(marker: EditorValidationMarker): Boolean {
    return marker.code in setOf("missing_ftp", "missing_hr_max", "missing_resting_hr", "missing_lthr")
}

// F1: Ramp target resolution helpers

private fun resolveRampFromTargetPowerAction(
    nodeId: EditorNodeId,
    text: String,
    existingTo: EditorTarget?,
    onAction: (EwoEditorScreenAction) -> Unit,
) {
    val fromWatts = text.toIntOrNull() ?: return
    val to = existingTo ?: EditorTarget.Power(0)
    onAction(EwoEditorScreenAction.Dispatch(SetRampTargets(nodeId, EditorTarget.Power(fromWatts), to)))
}

private fun resolveRampToTargetPowerAction(
    nodeId: EditorNodeId,
    text: String,
    existingFrom: EditorTarget?,
    onAction: (EwoEditorScreenAction) -> Unit,
) {
    val toWatts = text.toIntOrNull() ?: return
    val from = existingFrom ?: EditorTarget.Power(0)
    onAction(EwoEditorScreenAction.Dispatch(SetRampTargets(nodeId, from, EditorTarget.Power(toWatts))))
}

// F1: Ramp target fields — supports both Power(watts) and FtpPercent modes

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SteadyTargetFields(segment: EditorSegment.Steady, onAction: (EwoEditorScreenAction) -> Unit) {
    val target = segment.target
    val selectedMode = target.toSteadyTargetMode()

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = selectedMode == SteadyTargetMode.WATTS,
            onClick = {
                val newTarget = defaultSteadyTargetForMode(SteadyTargetMode.WATTS, target)
                if (newTarget != target) {
                    onAction(EwoEditorScreenAction.Dispatch(SetSteadyTarget(segment.nodeId, newTarget)))
                }
            },
            label = { Text(stringResource(R.string.ewo_editor_steady_mode_watts)) },
        )
        FilterChip(
            selected = selectedMode == SteadyTargetMode.FTP_PERCENT,
            onClick = {
                val newTarget = defaultSteadyTargetForMode(SteadyTargetMode.FTP_PERCENT, target)
                if (newTarget != target) {
                    onAction(EwoEditorScreenAction.Dispatch(SetSteadyTarget(segment.nodeId, newTarget)))
                }
            },
            label = { Text(stringResource(R.string.ewo_editor_steady_mode_ftp)) },
        )
        FilterChip(
            selected = selectedMode == SteadyTargetMode.HEART_RATE,
            onClick = {
                val newTarget = defaultSteadyTargetForMode(SteadyTargetMode.HEART_RATE, target)
                if (newTarget != target) {
                    onAction(EwoEditorScreenAction.Dispatch(SetSteadyTarget(segment.nodeId, newTarget)))
                }
            },
            label = { Text(stringResource(R.string.ewo_editor_steady_mode_hr)) },
        )
        FilterChip(
            selected = selectedMode == SteadyTargetMode.HEART_RATE_RELATIVE,
            onClick = {
                val newTarget = defaultSteadyTargetForMode(SteadyTargetMode.HEART_RATE_RELATIVE, target)
                if (newTarget != target) {
                    onAction(EwoEditorScreenAction.Dispatch(SetSteadyTarget(segment.nodeId, newTarget)))
                }
            },
            label = { Text(stringResource(R.string.ewo_editor_steady_mode_hr_percent)) },
        )
    }

    Spacer(Modifier.height(4.dp))

    when (selectedMode) {
        SteadyTargetMode.WATTS -> {
            val watts = (target as? EditorTarget.Power)?.watts
            var wattsText by remember(segment.nodeId, watts) {
                mutableStateOf(watts?.toString().orEmpty())
            }

            OutlinedTextField(
                value = wattsText,
                onValueChange = { text ->
                    wattsText = text
                    resolveSteadyTargetPowerAction(segment.nodeId, text)?.let(onAction)
                },
                label = { Text(stringResource(R.string.ewo_editor_power_watts_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        SteadyTargetMode.FTP_PERCENT -> {
            val ftpPercent = (target as? EditorTarget.FtpPercent)?.fraction
            var ftpText by remember(segment.nodeId, ftpPercent) {
                mutableStateOf(ftpPercent?.let { (it * 100).toInt().toString() }.orEmpty())
            }

            OutlinedTextField(
                value = ftpText,
                onValueChange = { text ->
                    ftpText = text
                    resolveSteadyTargetFtpPercentAction(segment.nodeId, text)?.let(onAction)
                },
                label = { Text(stringResource(R.string.ewo_editor_steady_ftp_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        SteadyTargetMode.HEART_RATE -> {
            val heartRateTarget = target as? EditorTarget.HeartRate
            var bpmBandText by remember(segment.nodeId, heartRateTarget?.lowBpm, heartRateTarget?.highBpm) {
                mutableStateOf(heartRateTarget?.let { formatIntBand(it.lowBpm, it.highBpm) }.orEmpty())
            }

            OutlinedTextField(
                value = bpmBandText,
                onValueChange = { text ->
                    bpmBandText = text
                    resolveSteadyTargetHeartRateAction(segment.nodeId, text)?.let(onAction)
                },
                label = { Text(stringResource(R.string.ewo_editor_hr_band_label)) },
                placeholder = { Text(stringResource(R.string.ewo_editor_hr_band_placeholder)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.ewo_editor_help_hr_target),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SteadyTargetMode.HEART_RATE_RELATIVE -> {
            val relativeTarget = (target as? EditorTarget.HeartRateRelative)
                ?: defaultSteadyTargetForMode(
                    mode = SteadyTargetMode.HEART_RATE_RELATIVE,
                    existingTarget = null,
                ) as EditorTarget.HeartRateRelative
            var relativeBandText by remember(
                segment.nodeId,
                relativeTarget.lowFraction,
                relativeTarget.highFraction,
            ) {
                mutableStateOf(
                    formatIntBand(
                        (relativeTarget.lowFraction * 100).toInt(),
                        (relativeTarget.highFraction * 100).toInt(),
                    ),
                )
            }
            var referenceMenuExpanded by remember { mutableStateOf(false) }

            OutlinedTextField(
                value = relativeBandText,
                onValueChange = { text ->
                    relativeBandText = text
                    resolveSteadyTargetHeartRateRelativeAction(
                        nodeId = segment.nodeId,
                        text = text,
                        reference = relativeTarget.reference,
                    )?.let(onAction)
                },
                label = { Text(stringResource(R.string.ewo_editor_hr_relative_band_label)) },
                placeholder = { Text(stringResource(R.string.ewo_editor_hr_relative_band_placeholder)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.ewo_editor_help_hr_relative_target),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.ewo_editor_hr_reference_label),
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.width(8.dp))
                Box {
                    OutlinedButton(onClick = { referenceMenuExpanded = true }) {
                        Text(relativeTarget.reference.stableCode)
                    }
                    DropdownMenu(
                        expanded = referenceMenuExpanded,
                        onDismissRequest = { referenceMenuExpanded = false },
                    ) {
                        HrReference.entries.forEach { reference ->
                            DropdownMenuItem(
                                text = { Text(reference.stableCode) },
                                onClick = {
                                    referenceMenuExpanded = false
                                    resolveSteadyTargetHeartRateRelativeAction(
                                        nodeId = segment.nodeId,
                                        text = relativeBandText,
                                        reference = reference,
                                    )?.let(onAction)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RampTargetFields(segment: EditorSegment.Ramp, onAction: (EwoEditorScreenAction) -> Unit) {
    val fromTarget = segment.fromTarget
    val toTarget = segment.toTarget

    // Determine current mode from existing targets
    val isFtpMode = fromTarget is EditorTarget.FtpPercent || toTarget is EditorTarget.FtpPercent
    var useFtpMode by remember(segment.nodeId) { mutableStateOf(isFtpMode) }

    Spacer(Modifier.height(4.dp))

    // Mode toggle
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = !useFtpMode,
            onClick = { useFtpMode = false },
            label = { Text(stringResource(R.string.ewo_editor_ramp_mode_watts)) },
        )
        FilterChip(
            selected = useFtpMode,
            onClick = { useFtpMode = true },
            label = { Text(stringResource(R.string.ewo_editor_ramp_mode_ftp)) },
        )
    }

    Spacer(Modifier.height(4.dp))

    if (useFtpMode) {
        val fromFtp = (fromTarget as? EditorTarget.FtpPercent)?.fraction
        val toFtp = (toTarget as? EditorTarget.FtpPercent)?.fraction
        var fromText by remember(segment.nodeId, fromFtp) {
            mutableStateOf(fromFtp?.let { (it * 100).toInt().toString() }.orEmpty())
        }
        var toText by remember(segment.nodeId, toFtp) {
            mutableStateOf(toFtp?.let { (it * 100).toInt().toString() }.orEmpty())
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = fromText,
                onValueChange = { text ->
                    fromText = text
                    val pct = text.toIntOrNull() ?: return@OutlinedTextField
                    val newFrom = EditorTarget.FtpPercent(pct / 100.0)
                    val existingTo = toTarget ?: EditorTarget.FtpPercent(1.0)
                    onAction(EwoEditorScreenAction.Dispatch(SetRampTargets(segment.nodeId, newFrom, existingTo)))
                },
                label = { Text(stringResource(R.string.ewo_editor_ramp_from_ftp_label)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = toText,
                onValueChange = { text ->
                    toText = text
                    val pct = text.toIntOrNull() ?: return@OutlinedTextField
                    val newTo = EditorTarget.FtpPercent(pct / 100.0)
                    val existingFrom = fromTarget ?: EditorTarget.FtpPercent(0.5)
                    onAction(EwoEditorScreenAction.Dispatch(SetRampTargets(segment.nodeId, existingFrom, newTo)))
                },
                label = { Text(stringResource(R.string.ewo_editor_ramp_to_ftp_label)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
    } else {
        val fromWatts = (fromTarget as? EditorTarget.Power)?.watts
        val toWatts = (toTarget as? EditorTarget.Power)?.watts
        var fromText by remember(segment.nodeId, fromWatts) {
            mutableStateOf(fromWatts?.toString().orEmpty())
        }
        var toText by remember(segment.nodeId, toWatts) {
            mutableStateOf(toWatts?.toString().toString().orEmpty())
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = fromText,
                onValueChange = { text ->
                    fromText = text
                    resolveRampFromTargetPowerAction(segment.nodeId, text, toTarget, onAction)
                },
                label = { Text(stringResource(R.string.ewo_editor_ramp_from_watts_label)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = toText,
                onValueChange = { text ->
                    toText = text
                    resolveRampToTargetPowerAction(segment.nodeId, text, fromTarget, onAction)
                },
                label = { Text(stringResource(R.string.ewo_editor_ramp_to_watts_label)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

// Repeat child add dropdown

@Composable
private fun RepeatChildAddSection(repeat: EditorSegment.Repeat, onAction: (EwoEditorScreenAction) -> Unit) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.ewo_editor_add_child_segment),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box {
            IconButton(onClick = { showMenu = true }) {
                Icon(Icons.Default.Add, stringResource(R.string.ewo_editor_add_child_segment))
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.ewo_editor_add_steady)) },
                    onClick = {
                        showMenu = false
                        onAction(
                            EwoEditorScreenAction.Dispatch(
                                AddSegment(segmentType = NewSegmentType.STEADY, parentNodeId = repeat.nodeId),
                            ),
                        )
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.ewo_editor_add_ramp)) },
                    onClick = {
                        showMenu = false
                        onAction(
                            EwoEditorScreenAction.Dispatch(
                                AddSegment(segmentType = NewSegmentType.RAMP, parentNodeId = repeat.nodeId),
                            ),
                        )
                    },
                )
            }
        }
    }
}

// F7: Control block section

@Composable
private fun ControlBlockSection(doc: EditorWorkoutDocument, onAction: (EwoEditorScreenAction) -> Unit) {
    val control = doc.control

    // Warning: HR targets but no control block
    if (doc.hasHrTargets() && control == null) {
        Text(
            stringResource(R.string.ewo_editor_control_warning_hr_no_control),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(vertical = 4.dp),
        )
    }

    var initialPowerText by remember(control?.initialPowerWatts) {
        mutableStateOf(control?.initialPowerWatts?.toString().orEmpty())
    }
    var minPowerText by remember(control?.minPowerWatts) {
        mutableStateOf(control?.minPowerWatts?.toString().orEmpty())
    }
    var maxPowerText by remember(control?.maxPowerWatts) {
        mutableStateOf(control?.maxPowerWatts?.toString().orEmpty())
    }
    var signalLossText by remember(control?.signalLossPowerWatts) {
        mutableStateOf(control?.signalLossPowerWatts?.toString().orEmpty())
    }
    var hrCapText by remember(control?.hrUpperCapBpm) {
        mutableStateOf(control?.hrUpperCapBpm?.toString().orEmpty())
    }

    fun dispatchControlIfComplete() {
        val initial = initialPowerText.toIntOrNull()
        val min = minPowerText.toIntOrNull()
        val max = maxPowerText.toIntOrNull()
        val signalLoss = signalLossText.toIntOrNull()
        val hrCap = hrCapText.toIntOrNull()
        if (initial != null && min != null && max != null && signalLoss != null && hrCap != null) {
            onAction(
                EwoEditorScreenAction.Dispatch(
                    SetControl(EditorControl(initial, min, max, signalLoss, hrCap)),
                ),
            )
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = initialPowerText,
            onValueChange = { initialPowerText = it; dispatchControlIfComplete() },
            label = { Text(stringResource(R.string.ewo_editor_initial_power_label)) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = minPowerText,
            onValueChange = { minPowerText = it; dispatchControlIfComplete() },
            label = { Text(stringResource(R.string.ewo_editor_min_power_label)) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = maxPowerText,
            onValueChange = { maxPowerText = it; dispatchControlIfComplete() },
            label = { Text(stringResource(R.string.ewo_editor_max_power_label)) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = signalLossText,
            onValueChange = { signalLossText = it; dispatchControlIfComplete() },
            label = { Text(stringResource(R.string.ewo_editor_signal_loss_power_label)) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
    }
    Spacer(Modifier.height(4.dp))
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = hrCapText,
            onValueChange = { hrCapText = it; dispatchControlIfComplete() },
            label = { Text(stringResource(R.string.ewo_editor_hr_upper_cap_label)) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        if (control != null) {
            TextButton(onClick = {
                onAction(EwoEditorScreenAction.Dispatch(SetControl(null)))
            }) {
                Text(stringResource(R.string.ewo_editor_clear_control))
            }
        }
    }
}

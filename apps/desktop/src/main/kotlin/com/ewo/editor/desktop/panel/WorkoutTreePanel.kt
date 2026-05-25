package com.ewo.editor.desktop.panel

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ewo.editor.commands.*
import com.ewo.editor.model.*
import com.ewo.editor.desktop.EditorStrings
import com.ewo.editor.desktop.formatDurationShort
import com.ewo.editor.desktop.state.EditorState
import com.ewo.editor.desktop.theme.SegmentColors

@Composable
fun WorkoutTreePanel(state: EditorState) {
    val doc = state.document
    var multiSelectMode by remember { mutableStateOf(false) }
    var multiSelectedIds by remember { mutableStateOf<Set<EditorNodeId>>(emptySet()) }
    var searchText by remember { mutableStateOf("") }
    val selectedRepeatId = doc.selectedNodeId?.let { id ->
        doc.findSegment(id)?.takeIf { it is EditorSegment.Repeat }?.nodeId
    }

    val focusRequester = remember { FocusRequester() }
    val selectNode: (EditorNodeId) -> Unit = { nodeId ->
        state.dispatchView(EditorViewAction.Select(nodeId))
        focusRequester.requestFocus()
    }

    LaunchedEffect(multiSelectMode) {
        if (!multiSelectMode) multiSelectedIds = emptySet()
    }

    // Build flat list of visible node IDs for keyboard navigation
    val visibleNodeIds = remember(doc.segments, doc.expandedNodeIds) {
        buildVisibleNodeList(doc.segments, doc.expandedNodeIds)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || multiSelectMode || searchText.isNotBlank()) return@onKeyEvent false
                handleTreeKeyEvent(event, state, doc, visibleNodeIds)
            },
    ) {
        // Header row with multi-select toggle
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Workout flow",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                EditorStrings.selectMultiple,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Tip(EditorStrings.tooltipMultiSelect) {
                IconToggleButton(
                    checked = multiSelectMode,
                    onCheckedChange = { multiSelectMode = it },
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        if (multiSelectMode) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                        contentDescription = "Multi-select mode",
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))

        // Search field
        OutlinedTextField(
            value = searchText,
            onValueChange = { searchText = it },
            label = { Text("Find steps") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                if (searchText.isNotEmpty()) {
                    IconButton(onClick = { searchText = "" }) {
                        Icon(Icons.Default.Clear, "Clear search", modifier = Modifier.size(16.dp))
                    }
                }
            },
        )
        Spacer(Modifier.height(8.dp))

        // Add segment toolbar (hidden during search)
        if (searchText.isBlank()) {
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // When a repeat is selected, add inside it; otherwise add after selected
                SegmentAddButton("Steady effort", Icons.Default.LinearScale, SegmentColors.steady, EditorStrings.tooltipAddSteady) {
                    state.dispatch(AddSegment(segmentType = NewSegmentType.STEADY,
                        afterNodeId = if (selectedRepeatId == null) doc.selectedNodeId else null,
                        parentNodeId = selectedRepeatId))
                }
                SegmentAddButton("Build up", Icons.AutoMirrored.Filled.TrendingUp, SegmentColors.ramp, EditorStrings.tooltipAddRamp) {
                    state.dispatch(AddSegment(segmentType = NewSegmentType.RAMP,
                        afterNodeId = if (selectedRepeatId == null) doc.selectedNodeId else null,
                        parentNodeId = selectedRepeatId))
                }
                SegmentAddButton("Cool down", Icons.AutoMirrored.Filled.TrendingDown, SegmentColors.ramp, EditorStrings.tooltipAddRampDown) {
                    state.dispatch(AddSegment(segmentType = NewSegmentType.RAMP_DOWN,
                        afterNodeId = if (selectedRepeatId == null) doc.selectedNodeId else null,
                        parentNodeId = selectedRepeatId))
                }
                SegmentAddButton("Repeat block", Icons.Default.Repeat, SegmentColors.repeat, EditorStrings.tooltipAddRepeat) {
                    state.dispatch(AddSegment(segmentType = NewSegmentType.REPEAT, afterNodeId = doc.selectedNodeId))
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        HorizontalDivider()
        Spacer(Modifier.height(4.dp))

        // Segment tree or search results
        if (searchText.isBlank()) {
            LazyColumn(modifier = Modifier.weight(1f)) {
                segmentTreeItems(
                    segments = doc.segments,
                    selectedNodeId = doc.selectedNodeId,
                    expandedNodeIds = doc.expandedNodeIds,
                    depth = 0,
                    state = state,
                    multiSelectMode = multiSelectMode,
                    multiSelectedIds = multiSelectedIds,
                    onToggleMultiSelect = { id ->
                        multiSelectedIds = if (id in multiSelectedIds) multiSelectedIds - id else multiSelectedIds + id
                    },
                    onSelectNode = selectNode,
                )
            }
        } else {
            val matching = collectMatchingSegments(doc.segments, searchText)
            if (matching.isEmpty()) {
                Text(
                    "No steps match \"$searchText\"",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(matching, key = { it.segment.nodeId.value }) { result ->
                        Column {
                            if (result.path.isNotEmpty()) {
                                Text(
                                    result.path,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.padding(start = 8.dp, top = 2.dp),
                                )
                            }
                            ContextMenuArea(items = { buildContextMenuItems(result.segment, emptyList(), state) }) {
                                SegmentRow(
                                    segment = result.segment,
                                    isSelected = result.segment.nodeId == doc.selectedNodeId,
                                    isExpanded = false,
                                    depth = 0,
                                    showCheckbox = false,
                                    isMultiSelected = false,
                                    onSelect = { selectNode(result.segment.nodeId) },
                                    onToggleExpand = {},
                                    onToggleMultiSelect = {},
                                )
                            }
                        }
                    }
                }
            }
        }

        // Multi-select action bar
        if (multiSelectMode && multiSelectedIds.size >= 2) {
            HorizontalDivider()
            Spacer(Modifier.height(4.dp))
            val canWrap = canWrapSelected(doc.segments, multiSelectedIds)
            Row {
                Button(
                    onClick = {
                        val sorted = sortedSelectedIds(doc.segments, multiSelectedIds)
                        multiSelectedIds = emptySet()
                        multiSelectMode = false
                        state.dispatch(WrapInRepeat(sorted))
                    },
                    enabled = canWrap,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text("Repeat ${multiSelectedIds.size} selected steps", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        // Single-select action buttons
        val selected = doc.selectedNodeId
        if (selected != null && !multiSelectMode) {
            HorizontalDivider()
            Spacer(Modifier.height(4.dp))
            SegmentActions(state, selected)
        }

        // Keyboard shortcut hints
        Spacer(Modifier.height(4.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    if (selectedRepeatId != null) {
                        "New steps are added inside the selected repeat block. Close the repeat block before adding the next step outside it."
                    } else {
                        "Select a repeat block to add steps inside it. Close the repeat block before adding the next step outside it."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "Arrow keys move selection · Ctrl+↑↓ reorders · ←→ opens and closes repeats · Del removes",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SegmentAddButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: androidx.compose.ui.graphics.Color,
    tooltip: String = "",
    onClick: () -> Unit,
) {
    val button = @Composable {
        FilledTonalButton(
            onClick = onClick,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(16.dp), tint = tint)
            Spacer(Modifier.width(4.dp))
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
    if (tooltip.isNotBlank()) {
        TooltipBox(
            positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
            tooltip = { PlainTooltip { Text(tooltip) } },
            state = rememberTooltipState(),
            content = { button() },
        )
    } else {
        button()
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.segmentTreeItems(
    segments: List<EditorSegment>,
    selectedNodeId: EditorNodeId?,
    expandedNodeIds: Set<EditorNodeId>,
    depth: Int,
    state: EditorState,
    multiSelectMode: Boolean,
    multiSelectedIds: Set<EditorNodeId>,
    onToggleMultiSelect: (EditorNodeId) -> Unit,
    onSelectNode: (EditorNodeId) -> Unit,
) {
    for (segment in segments) {
        item(key = segment.nodeId.value) {
            ContextMenuArea(items = {
                buildContextMenuItems(segment, segments, state)
            }) {
                SegmentRow(
                    segment = segment,
                    isSelected = segment.nodeId == selectedNodeId,
                    isExpanded = segment.nodeId in expandedNodeIds,
                    depth = depth,
                    showCheckbox = multiSelectMode,
                    isMultiSelected = segment.nodeId in multiSelectedIds,
                    onSelect = { onSelectNode(segment.nodeId) },
                    onToggleExpand = { state.dispatchView(EditorViewAction.ToggleExpanded(segment.nodeId)) },
                    onToggleMultiSelect = { onToggleMultiSelect(segment.nodeId) },
                )
            }
        }
        if (segment is EditorSegment.Repeat && segment.nodeId in expandedNodeIds) {
            segmentTreeItems(
                segments = segment.segments,
                selectedNodeId = selectedNodeId,
                expandedNodeIds = expandedNodeIds,
                depth = depth + 1,
                state = state,
                multiSelectMode = multiSelectMode,
                multiSelectedIds = multiSelectedIds,
                onToggleMultiSelect = onToggleMultiSelect,
                onSelectNode = onSelectNode,
            )
        }
    }
}

@Composable
private fun SegmentRow(
    segment: EditorSegment,
    isSelected: Boolean,
    isExpanded: Boolean,
    depth: Int,
    showCheckbox: Boolean = false,
    isMultiSelected: Boolean = false,
    onSelect: () -> Unit,
    onToggleExpand: () -> Unit,
    onToggleMultiSelect: () -> Unit = {},
) {
    val bgColor = if (isMultiSelected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }

    val segmentColor = segmentTypeColor(segment)

    Surface(
        color = bgColor,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (depth * 20).dp, bottom = 2.dp)
            .clickable { if (showCheckbox) onToggleMultiSelect() else onSelect() },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            // Color indicator dot
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(segmentColor),
            )
            Spacer(Modifier.width(6.dp))

            // Multi-select checkbox
            if (showCheckbox) {
                Checkbox(
                    checked = isMultiSelected,
                    onCheckedChange = { onToggleMultiSelect() },
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(4.dp))
            }

            // Expand/collapse for repeats
            if (segment is EditorSegment.Repeat) {
                IconButton(
                    onClick = onToggleExpand,
                    modifier = Modifier.size(20.dp),
                ) {
                    Icon(
                        if (isExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                        contentDescription = "Toggle",
                        modifier = Modifier.size(16.dp),
                    )
                }
                Spacer(Modifier.width(4.dp))
            } else {
                Spacer(Modifier.width(24.dp))
            }

            // Type icon
            Icon(
                imageVector = when (segment) {
                    is EditorSegment.Steady -> Icons.Default.LinearScale
                    is EditorSegment.Ramp -> if (isDescendingRamp(segment)) {
                        Icons.AutoMirrored.Filled.TrendingDown
                    } else {
                        Icons.AutoMirrored.Filled.TrendingUp
                    }
                    is EditorSegment.FreeRide -> Icons.AutoMirrored.Filled.DirectionsBike
                    is EditorSegment.Repeat -> Icons.Default.Repeat
                },
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = segmentColor,
            )
            Spacer(Modifier.width(8.dp))

            // Segment info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    segment.label ?: segmentTypeName(segment),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                )
                Text(
                    segmentSummary(segment),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SegmentActions(state: EditorState, nodeId: EditorNodeId) {
    val doc = state.document
    val segment = doc.findSegment(nodeId)
    val posInfo = findPositionInfo(doc.segments, nodeId, null)

    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (posInfo != null) {
            val (parentId, index, siblingCount) = posInfo
            Tip(EditorStrings.tooltipMoveUp) {
                IconButton(
                    onClick = { state.dispatch(MoveSegment(nodeId, parentId, index - 1)) },
                    enabled = index > 0,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(Icons.Default.KeyboardArrowUp, "Move Up", modifier = Modifier.size(18.dp))
                }
            }
            Tip(EditorStrings.tooltipMoveDown) {
                IconButton(
                    onClick = { state.dispatch(MoveSegment(nodeId, parentId, index + 1)) },
                    enabled = index < siblingCount - 1,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(Icons.Default.KeyboardArrowDown, "Move Down", modifier = Modifier.size(18.dp))
                }
            }
        }

        Tip(EditorStrings.tooltipDuplicate) {
            IconButton(onClick = { state.dispatch(DuplicateSegment(nodeId)) }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.ContentCopy, "Duplicate", modifier = Modifier.size(18.dp))
            }
        }

        Tip(EditorStrings.tooltipCopy) {
            IconButton(onClick = { state.copySelected() }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.CopyAll, "Copy", modifier = Modifier.size(18.dp))
            }
        }

        if (segment is EditorSegment.Repeat) {
            Tip(EditorStrings.tooltipUnwrap) {
                IconButton(onClick = { state.dispatch(UnwrapRepeat(nodeId)) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.UnfoldLess, "Unwrap", modifier = Modifier.size(18.dp))
                }
            }
        }

        Tip(EditorStrings.tooltipDelete) {
            IconButton(onClick = { state.dispatch(DeleteSegment(nodeId)) }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Delete, "Delete", modifier = Modifier.size(18.dp))
            }
        }
    }
}

private fun segmentTypeColor(segment: EditorSegment): androidx.compose.ui.graphics.Color = when (segment) {
    is EditorSegment.Steady -> SegmentColors.steady
    is EditorSegment.Ramp -> SegmentColors.ramp
    is EditorSegment.FreeRide -> SegmentColors.freeRide
    is EditorSegment.Repeat -> SegmentColors.repeat
}

private fun segmentTypeName(segment: EditorSegment): String = when (segment) {
    is EditorSegment.Steady -> "Steady effort"
    is EditorSegment.Ramp -> if (isDescendingRamp(segment)) "Cool down" else "Build up"
    is EditorSegment.FreeRide -> "Ride free"
    is EditorSegment.Repeat -> "Repeat block"
}

private fun segmentSummary(segment: EditorSegment): String = when (segment) {
    is EditorSegment.Steady -> "${targetSummary(segment.target)} · ${formatDurationShort(segment.durationSec)}"
    is EditorSegment.Ramp -> "${targetSummary(segment.fromTarget)}→${targetSummary(segment.toTarget)} · ${formatDurationShort(segment.durationSec)}"
    is EditorSegment.FreeRide -> formatDurationShort(segment.durationSec)
    is EditorSegment.Repeat -> "×${segment.count}"
}

private fun targetSummary(target: EditorTarget?): String = when (target) {
    is EditorTarget.Power -> "${target.watts}W"
    is EditorTarget.FtpPercent -> "${(target.fraction * 100).toInt()}% FTP"
    is EditorTarget.HeartRate -> "${target.lowBpm}–${target.highBpm} bpm"
    is EditorTarget.HeartRateRelative -> "${(target.lowFraction * 100).toInt()}–${(target.highFraction * 100).toInt()}% ${target.reference.stableCode}"
    null -> "—"
}

private fun isDescendingRamp(segment: EditorSegment.Ramp): Boolean {
    val fromValue = rampTargetSortValue(segment.fromTarget)
    val toValue = rampTargetSortValue(segment.toTarget)
    return fromValue != null && toValue != null && fromValue > toValue
}

private fun rampTargetSortValue(target: EditorTarget?): Double? = when (target) {
    is EditorTarget.Power -> target.watts.toDouble()
    is EditorTarget.FtpPercent -> target.fraction
    is EditorTarget.HeartRate -> target.highBpm.toDouble()
    is EditorTarget.HeartRateRelative -> target.highFraction
    null -> null
}


private fun findPositionInfo(
    segments: List<EditorSegment>,
    nodeId: EditorNodeId,
    parentNodeId: EditorNodeId?,
): Triple<EditorNodeId?, Int, Int>? {
    for ((index, segment) in segments.withIndex()) {
        if (segment.nodeId == nodeId) return Triple(parentNodeId, index, segments.size)
        if (segment is EditorSegment.Repeat) {
            findPositionInfo(segment.segments, nodeId, segment.nodeId)?.let { return it }
        }
    }
    return null
}

/** A search result with its parent path for breadcrumb display. */
private data class SearchResult(val segment: EditorSegment, val path: String)

private fun collectMatchingSegments(
    segments: List<EditorSegment>,
    query: String,
    parentPath: String = "",
): List<SearchResult> {
    val q = query.lowercase()
    return buildList {
        for (seg in segments) {
            val currentPath = if (parentPath.isEmpty()) seg.segmentId else "$parentPath > ${seg.segmentId}"
            if (seg.segmentId.lowercase().contains(q) ||
                seg.label?.lowercase()?.contains(q) == true) {
                add(SearchResult(seg, parentPath))
            }
            if (seg is EditorSegment.Repeat) {
                addAll(collectMatchingSegments(seg.segments, query, currentPath))
            }
        }
    }
}

private fun findSiblingsOf(
    segments: List<EditorSegment>,
    nodeId: EditorNodeId,
): List<EditorSegment>? {
    if (segments.any { it.nodeId == nodeId }) return segments
    for (seg in segments) {
        if (seg is EditorSegment.Repeat) {
            findSiblingsOf(seg.segments, nodeId)?.let { return it }
        }
    }
    return null
}

private fun canWrapSelected(segments: List<EditorSegment>, selectedIds: Set<EditorNodeId>): Boolean {
    if (selectedIds.size < 2) return false
    val siblings = findSiblingsOf(segments, selectedIds.first()) ?: return false
    val siblingIdSet = siblings.map { it.nodeId }.toSet()
    if (!selectedIds.all { it in siblingIdSet }) return false
    val indices = siblings.mapIndexedNotNull { i, seg -> if (seg.nodeId in selectedIds) i else null }
    if (indices.size != selectedIds.size) return false
    return indices.max() - indices.min() == indices.size - 1
}

private fun sortedSelectedIds(segments: List<EditorSegment>, selectedIds: Set<EditorNodeId>): List<EditorNodeId> {
    val siblings = findSiblingsOf(segments, selectedIds.first()) ?: return selectedIds.toList()
    return siblings.filter { it.nodeId in selectedIds }.map { it.nodeId }
}

private fun buildContextMenuItems(
    segment: EditorSegment,
    siblings: List<EditorSegment>,
    state: EditorState,
): List<ContextMenuItem> {
    val nodeId = segment.nodeId
    val doc = state.document
    val posInfo = findPositionInfo(doc.segments, nodeId, null)

    return buildList {
        if (posInfo != null && posInfo.second > 0) {
            add(ContextMenuItem("Move step up") { state.dispatch(MoveSegment(nodeId, posInfo.first, posInfo.second - 1)) })
        }
        if (posInfo != null && posInfo.second < posInfo.third - 1) {
            add(ContextMenuItem("Move step down") { state.dispatch(MoveSegment(nodeId, posInfo.first, posInfo.second + 1)) })
        }
        add(ContextMenuItem("Copy step") { state.clipboard = segment })
        add(ContextMenuItem("Duplicate step") { state.dispatch(DuplicateSegment(nodeId)) })
        if (segment is EditorSegment.Repeat) {
            add(ContextMenuItem("Add steady child step") {
                state.dispatch(AddSegment(segmentType = NewSegmentType.STEADY, parentNodeId = nodeId))
            })
            add(ContextMenuItem("Add build-up child step") {
                state.dispatch(AddSegment(segmentType = NewSegmentType.RAMP, parentNodeId = nodeId))
            })
            add(ContextMenuItem("Add cool-down child step") {
                state.dispatch(AddSegment(segmentType = NewSegmentType.RAMP_DOWN, parentNodeId = nodeId))
            })
            add(ContextMenuItem("Flatten repeat block") { state.dispatch(UnwrapRepeat(nodeId)) })
        }
        add(ContextMenuItem("Delete step") { state.dispatch(DeleteSegment(nodeId)) })
    }
}

/** Builds a flat list of node IDs in tree-display order, respecting expand state. */
private fun buildVisibleNodeList(
    segments: List<EditorSegment>,
    expandedNodeIds: Set<EditorNodeId>,
): List<EditorNodeId> = buildList {
    for (seg in segments) {
        add(seg.nodeId)
        if (seg is EditorSegment.Repeat && seg.nodeId in expandedNodeIds) {
            addAll(buildVisibleNodeList(seg.segments, expandedNodeIds))
        }
    }
}

/** Handles keyboard events for tree navigation. Returns true if consumed. */
private fun handleTreeKeyEvent(
    event: KeyEvent,
    state: EditorState,
    doc: EditorWorkoutDocument,
    visibleNodeIds: List<EditorNodeId>,
): Boolean {
    val selected = doc.selectedNodeId
    val currentIndex = if (selected != null) visibleNodeIds.indexOf(selected) else -1

    return when {
        event.key == Key.DirectionDown && event.isCtrlPressed -> {
            // Ctrl+↓: move segment down
            if (selected != null) {
                val posInfo = findPositionInfo(doc.segments, selected, null)
                if (posInfo != null && posInfo.second < posInfo.third - 1) {
                    state.dispatch(MoveSegment(selected, posInfo.first, posInfo.second + 1))
                }
            }
            true
        }
        event.key == Key.DirectionUp && event.isCtrlPressed -> {
            // Ctrl+↑: move segment up
            if (selected != null) {
                val posInfo = findPositionInfo(doc.segments, selected, null)
                if (posInfo != null && posInfo.second > 0) {
                    state.dispatch(MoveSegment(selected, posInfo.first, posInfo.second - 1))
                }
            }
            true
        }
        event.key == Key.DirectionDown -> {
            val next = if (currentIndex < 0) 0 else (currentIndex + 1).coerceAtMost(visibleNodeIds.lastIndex)
            state.dispatchView(EditorViewAction.Select(visibleNodeIds.getOrNull(next)))
            true
        }
        event.key == Key.DirectionUp -> {
            val prev = if (currentIndex < 0) visibleNodeIds.lastIndex else (currentIndex - 1).coerceAtLeast(0)
            state.dispatchView(EditorViewAction.Select(visibleNodeIds.getOrNull(prev)))
            true
        }
        event.key == Key.DirectionRight -> {
            // Expand repeat
            if (selected != null) {
                val seg = doc.findSegment(selected)
                if (seg is EditorSegment.Repeat && selected !in doc.expandedNodeIds) {
                    state.dispatchView(EditorViewAction.ToggleExpanded(selected))
                    return true
                }
            }
            false
        }
        event.key == Key.DirectionLeft -> {
            // Collapse repeat
            if (selected != null && selected in doc.expandedNodeIds) {
                state.dispatchView(EditorViewAction.ToggleExpanded(selected))
                return true
            }
            false
        }
        event.key == Key.Delete -> {
            if (selected != null) {
                state.dispatch(DeleteSegment(selected))
                true
            } else false
        }
        else -> false
    }
}

/** Compact tooltip wrapper using Material 3 TooltipBox. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Tip(text: String, content: @Composable () -> Unit) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(text) } },
        state = rememberTooltipState(),
        content = { content() },
    )
}

package com.ewo.editor.model

/**
 * The editor's in-memory workout document.
 *
 * This is the **authoritative representation** while the user is editing. It is
 * richer than the canonical `.ewo` model: it carries selection state, expanded/collapsed
 * tree state, validation markers, and a dirty flag.
 *
 * Fields marked as "editor-only" are never serialized to canonical `.ewo`.
 *
 * The document may hold temporarily invalid state (empty title, negative duration, etc.)
 * while the user is actively editing. Canonical export is blocked when structural or
 * semantic validation errors exist (see [canExport]).
 */
data class EditorWorkoutDocument(
    // --- Canonical content (maps to .ewo fields) ---

    val version: String,
    val uid: String?,
    val revision: Int?,
    val title: String,
    val description: String,
    /** Optional v1.6+ localized title companion. [defaultText] must match [title]. */
    val titleLocalized: EditorLocalizedText? = null,
    /** Optional v1.6+ localized description companion. [defaultText] must match [description]. */
    val descriptionLocalized: EditorLocalizedText? = null,
    val difficulty: String?,
    val tags: List<String>,
    val control: EditorControl?,
    val messages: List<EditorMessage>,
    val segments: List<EditorSegment>,

    // --- Editor-only state (never in .ewo) ---

    /** Stable ID for the root workout node (used for selection/commands on the root). */
    val rootNodeId: EditorNodeId,

    /** Currently selected node, or null if nothing is selected. */
    val selectedNodeId: EditorNodeId?,

    /** Nodes whose children are visible in the tree view. */
    val expandedNodeIds: Set<EditorNodeId>,

    /** True when the document has unsaved changes. */
    val isDirty: Boolean,

    /** Validation issues recomputed after every command. */
    val validationMarkers: List<EditorValidationMarker>,

    /** Counter for generating unique [EditorNodeId] values. */
    val nextNodeCounter: Int,
) {
    /** True when no export-blocking errors exist. */
    val canExport: Boolean get() = validationMarkers.none { it.blocksExport }

    /** All error-level markers. */
    val errors: List<EditorValidationMarker>
        get() = validationMarkers.filter { it.severity == EditorValidationSeverity.ERROR }

    /** All warning-level markers (sanity/advisory). */
    val warnings: List<EditorValidationMarker>
        get() = validationMarkers.filter { it.severity == EditorValidationSeverity.WARNING }
}

/** Returns true if any segment in the tree uses HR-based targets. */
fun EditorWorkoutDocument.hasHrTargets(): Boolean = segmentsHaveHrTargets(segments)

private fun segmentsHaveHrTargets(segments: List<EditorSegment>): Boolean {
    for (seg in segments) {
        when (seg) {
            is EditorSegment.Steady -> {
                if (seg.target is EditorTarget.HeartRate || seg.target is EditorTarget.HeartRateRelative) return true
            }
            is EditorSegment.Repeat -> {
                if (segmentsHaveHrTargets(seg.segments)) return true
            }
            is EditorSegment.Ramp, is EditorSegment.FreeRide -> Unit
        }
    }
    return false
}

// --- Tree traversal utilities ---

/** Finds a segment by its [EditorNodeId] anywhere in the document tree. */
fun EditorWorkoutDocument.findSegment(nodeId: EditorNodeId): EditorSegment? {
    return findInList(segments, nodeId)
}

/** Finds a message by its [EditorNodeId] in the document (root or segment level). */
fun EditorWorkoutDocument.findMessage(nodeId: EditorNodeId): EditorMessage? {
    messages.firstOrNull { it.nodeId == nodeId }?.let { return it }
    return findMessageInSegments(segments, nodeId)
}

/** Returns all [EditorNodeId] values in the document tree. */
fun EditorWorkoutDocument.allNodeIds(): Set<EditorNodeId> {
    val ids = mutableSetOf(rootNodeId)
    messages.forEach { ids += it.nodeId }
    collectNodeIds(segments, ids)
    return ids
}

/** Returns all canonical segment IDs in the document tree. */
fun EditorWorkoutDocument.allSegmentIds(): List<String> {
    val ids = mutableListOf<String>()
    collectSegmentIds(segments, ids)
    return ids
}

/** Generates a new unique [EditorNodeId] and returns it with the updated counter. */
fun EditorWorkoutDocument.generateNodeId(): Pair<EditorNodeId, Int> {
    val id = EditorNodeId("node_${nextNodeCounter}")
    return id to (nextNodeCounter + 1)
}

// --- Internal helpers ---

private fun findInList(segments: List<EditorSegment>, nodeId: EditorNodeId): EditorSegment? {
    for (segment in segments) {
        if (segment.nodeId == nodeId) return segment
        if (segment is EditorSegment.Repeat) {
            findInList(segment.segments, nodeId)?.let { return it }
        }
    }
    return null
}

private fun findMessageInSegments(segments: List<EditorSegment>, nodeId: EditorNodeId): EditorMessage? {
    for (segment in segments) {
        segment.messages.firstOrNull { it.nodeId == nodeId }?.let { return it }
        if (segment is EditorSegment.Repeat) {
            findMessageInSegments(segment.segments, nodeId)?.let { return it }
        }
    }
    return null
}

private fun collectNodeIds(segments: List<EditorSegment>, ids: MutableSet<EditorNodeId>) {
    for (segment in segments) {
        ids += segment.nodeId
        segment.messages.forEach { ids += it.nodeId }
        if (segment is EditorSegment.Repeat) {
            collectNodeIds(segment.segments, ids)
        }
    }
}

private fun collectSegmentIds(segments: List<EditorSegment>, ids: MutableList<String>) {
    for (segment in segments) {
        ids += segment.segmentId
        if (segment is EditorSegment.Repeat) {
            collectSegmentIds(segment.segments, ids)
        }
    }
}

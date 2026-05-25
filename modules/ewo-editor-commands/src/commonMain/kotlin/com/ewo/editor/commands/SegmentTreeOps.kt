package com.ewo.editor.commands

import com.ewo.editor.model.EditorMessage
import com.ewo.editor.model.EditorNodeId
import com.ewo.editor.model.EditorSegment

/**
 * Pure tree-transformation functions for [EditorSegment] lists.
 *
 * All operations return new lists; nothing is mutated in place.
 */
internal object SegmentTreeOps {

    /** Replaces a single segment found by [nodeId] with [replacement]. */
    fun replace(
        segments: List<EditorSegment>,
        nodeId: EditorNodeId,
        replacement: EditorSegment,
    ): List<EditorSegment>? {
        val result = segments.map { seg ->
            if (seg.nodeId == nodeId) return@replace segments.map {
                if (it.nodeId == nodeId) replacement else it
            }
            if (seg is EditorSegment.Repeat) {
                val updated = replace(seg.segments, nodeId, replacement)
                if (updated != null) return@replace segments.map {
                    if (it.nodeId == seg.nodeId) seg.copy(segments = updated) else it
                }
            }
            seg
        }
        return null // not found
    }

    /** Removes a segment by [nodeId]. Returns null if not found. */
    fun remove(segments: List<EditorSegment>, nodeId: EditorNodeId): List<EditorSegment>? {
        if (segments.any { it.nodeId == nodeId }) {
            return segments.filter { it.nodeId != nodeId }
        }
        for (seg in segments) {
            if (seg is EditorSegment.Repeat) {
                val updated = remove(seg.segments, nodeId)
                if (updated != null) {
                    return segments.map {
                        if (it.nodeId == seg.nodeId) seg.copy(segments = updated) else it
                    }
                }
            }
        }
        return null
    }

    /** Inserts [newSegment] after [afterNodeId] at the same level. Returns null if [afterNodeId] not found. */
    fun insertAfter(
        segments: List<EditorSegment>,
        afterNodeId: EditorNodeId,
        newSegment: EditorSegment,
    ): List<EditorSegment>? {
        val idx = segments.indexOfFirst { it.nodeId == afterNodeId }
        if (idx >= 0) {
            return segments.toMutableList().apply { add(idx + 1, newSegment) }
        }
        for (seg in segments) {
            if (seg is EditorSegment.Repeat) {
                val updated = insertAfter(seg.segments, afterNodeId, newSegment)
                if (updated != null) {
                    return segments.map {
                        if (it.nodeId == seg.nodeId) seg.copy(segments = updated) else it
                    }
                }
            }
        }
        return null
    }

    /**
     * Inserts [newSegment] as a child of the repeat with [parentNodeId] at [position].
     * Returns null if the parent is not found or is not a repeat.
     */
    fun insertChild(
        segments: List<EditorSegment>,
        parentNodeId: EditorNodeId,
        newSegment: EditorSegment,
        position: Int,
    ): List<EditorSegment>? {
        return segments.map { seg ->
            if (seg.nodeId == parentNodeId && seg is EditorSegment.Repeat) {
                val pos = position.coerceIn(0, seg.segments.size)
                val updated = seg.segments.toMutableList().apply { add(pos, newSegment) }
                return segments.map {
                    if (it.nodeId == parentNodeId) seg.copy(segments = updated) else it
                }
            }
            if (seg is EditorSegment.Repeat) {
                val updated = insertChild(seg.segments, parentNodeId, newSegment, position)
                if (updated != null) {
                    return segments.map {
                        if (it.nodeId == seg.nodeId) seg.copy(segments = updated) else it
                    }
                }
            }
            seg
        }.let { null }
    }

    /** Finds the index of [nodeId] among its siblings and returns the sibling list. */
    fun findSiblings(
        segments: List<EditorSegment>,
        nodeId: EditorNodeId,
    ): List<EditorSegment>? {
        if (segments.any { it.nodeId == nodeId }) return segments
        for (seg in segments) {
            if (seg is EditorSegment.Repeat) {
                val found = findSiblings(seg.segments, nodeId)
                if (found != null) return found
            }
        }
        return null
    }

    /**
     * Wraps consecutive segments identified by [nodeIds] into a new repeat.
     * All identified nodes must be consecutive siblings.
     */
    fun wrapInRepeat(
        segments: List<EditorSegment>,
        nodeIds: List<EditorNodeId>,
        repeatSegment: EditorSegment.Repeat,
    ): List<EditorSegment>? {
        val nodeIdSet = nodeIds.toSet()
        val indices = segments.mapIndexedNotNull { i, seg ->
            if (seg.nodeId in nodeIdSet) i else null
        }
        if (indices.size == nodeIds.size && indices == (indices.first()..indices.last()).toList()) {
            // Consecutive at this level
            val before = segments.subList(0, indices.first())
            val after = segments.subList(indices.last() + 1, segments.size)
            return before + repeatSegment + after
        }
        // Try recursing into repeats
        for (seg in segments) {
            if (seg is EditorSegment.Repeat) {
                val updated = wrapInRepeat(seg.segments, nodeIds, repeatSegment)
                if (updated != null) {
                    return segments.map {
                        if (it.nodeId == seg.nodeId) seg.copy(segments = updated) else it
                    }
                }
            }
        }
        return null
    }

    /** Replaces a repeat node with its children. Returns null if not found or not a repeat. */
    fun unwrapRepeat(
        segments: List<EditorSegment>,
        nodeId: EditorNodeId,
    ): List<EditorSegment>? {
        val idx = segments.indexOfFirst { it.nodeId == nodeId }
        if (idx >= 0) {
            val seg = segments[idx]
            if (seg !is EditorSegment.Repeat) return null
            val before = segments.subList(0, idx)
            val after = segments.subList(idx + 1, segments.size)
            return before + seg.segments + after
        }
        for (seg in segments) {
            if (seg is EditorSegment.Repeat) {
                val updated = unwrapRepeat(seg.segments, nodeId)
                if (updated != null) {
                    return segments.map {
                        if (it.nodeId == seg.nodeId) seg.copy(segments = updated) else it
                    }
                }
            }
        }
        return null
    }

    /**
     * Deep-copies a segment tree, assigning new node IDs **and** new segment IDs via [nextId].
     * Each segment consumes two IDs from [nextId]: one for `nodeId`, one to derive `segmentId`.
     */
    fun deepCopy(
        segment: EditorSegment,
        nextId: () -> EditorNodeId,
    ): EditorSegment {
        val newMessages = segment.messages.map { it.copy(nodeId = nextId()) }
        val newNodeId = nextId()
        val newSegmentId = "segment_${nextId().value.removePrefix("node_")}"
        return when (segment) {
            is EditorSegment.Steady -> segment.copy(nodeId = newNodeId, segmentId = newSegmentId, messages = newMessages)
            is EditorSegment.Ramp -> segment.copy(nodeId = newNodeId, segmentId = newSegmentId, messages = newMessages)
            is EditorSegment.FreeRide -> segment.copy(nodeId = newNodeId, segmentId = newSegmentId, messages = newMessages)
            is EditorSegment.Repeat -> segment.copy(
                nodeId = newNodeId,
                segmentId = newSegmentId,
                messages = newMessages,
                segments = segment.segments.map { deepCopy(it, nextId) },
            )
        }
    }

    /** Removes a message by [messageNodeId] from segment-level message lists. */
    fun removeMessage(
        segments: List<EditorSegment>,
        messageNodeId: EditorNodeId,
    ): List<EditorSegment>? {
        for (seg in segments) {
            if (seg.messages.any { it.nodeId == messageNodeId }) {
                val updatedMessages = seg.messages.filter { it.nodeId != messageNodeId }
                val updated = replaceMessages(seg, updatedMessages)
                return segments.map { if (it.nodeId == seg.nodeId) updated else it }
            }
            if (seg is EditorSegment.Repeat) {
                val updated = removeMessage(seg.segments, messageNodeId)
                if (updated != null) {
                    return segments.map {
                        if (it.nodeId == seg.nodeId) seg.copy(segments = updated) else it
                    }
                }
            }
        }
        return null
    }

    /** Updates a message by [messageNodeId] in segment-level message lists. */
    fun updateMessage(
        segments: List<EditorSegment>,
        messageNodeId: EditorNodeId,
        transform: (EditorMessage) -> EditorMessage,
    ): List<EditorSegment>? {
        for (seg in segments) {
            val msgIdx = seg.messages.indexOfFirst { it.nodeId == messageNodeId }
            if (msgIdx >= 0) {
                val updatedMessages = seg.messages.toMutableList().apply {
                    set(msgIdx, transform(get(msgIdx)))
                }
                val updated = replaceMessages(seg, updatedMessages)
                return segments.map { if (it.nodeId == seg.nodeId) updated else it }
            }
            if (seg is EditorSegment.Repeat) {
                val updated = updateMessage(seg.segments, messageNodeId, transform)
                if (updated != null) {
                    return segments.map {
                        if (it.nodeId == seg.nodeId) seg.copy(segments = updated) else it
                    }
                }
            }
        }
        return null
    }

    /** Adds a message to the segment with [parentNodeId]. */
    fun addMessage(
        segments: List<EditorSegment>,
        parentNodeId: EditorNodeId,
        message: EditorMessage,
    ): List<EditorSegment>? {
        for (seg in segments) {
            if (seg.nodeId == parentNodeId) {
                val updated = replaceMessages(seg, seg.messages + message)
                return segments.map { if (it.nodeId == seg.nodeId) updated else it }
            }
            if (seg is EditorSegment.Repeat) {
                val result = addMessage(seg.segments, parentNodeId, message)
                if (result != null) {
                    return segments.map {
                        if (it.nodeId == seg.nodeId) seg.copy(segments = result) else it
                    }
                }
            }
        }
        return null
    }

    /**
     * Finds the parent repeat node ID of a segment, or null if the segment is at root level.
     */
    fun findParentNodeId(
        segments: List<EditorSegment>,
        nodeId: EditorNodeId,
        parentId: EditorNodeId? = null,
    ): EditorNodeId? {
        for (seg in segments) {
            if (seg.nodeId == nodeId) return parentId
            if (seg is EditorSegment.Repeat) {
                val found = findParentNodeId(seg.segments, nodeId, seg.nodeId)
                if (found != null || seg.segments.any { it.nodeId == nodeId }) {
                    return found ?: seg.nodeId
                }
            }
        }
        return null
    }

    private fun replaceMessages(seg: EditorSegment, messages: List<EditorMessage>): EditorSegment =
        when (seg) {
            is EditorSegment.Steady -> seg.copy(messages = messages)
            is EditorSegment.Ramp -> seg.copy(messages = messages)
            is EditorSegment.FreeRide -> seg.copy(messages = messages)
            is EditorSegment.Repeat -> seg.copy(messages = messages)
        }
}

/**
 * Direction for segment move operations.
 */
enum class MoveDirection { UP, DOWN }

/**
 * Computes a [MoveSegment] command for a given node in a given direction,
 * or returns null if the segment is already at the boundary.
 */
fun computeMoveTarget(
    segments: List<EditorSegment>,
    nodeId: EditorNodeId,
    direction: MoveDirection,
): MoveSegment? {
    val siblings = SegmentTreeOps.findSiblings(segments, nodeId) ?: return null
    val idx = siblings.indexOfFirst { it.nodeId == nodeId }
    if (idx < 0) return null

    val newPosition = when (direction) {
        MoveDirection.UP -> if (idx == 0) return null else idx - 1
        MoveDirection.DOWN -> if (idx == siblings.lastIndex) return null else idx + 1
    }

    val parentNodeId = SegmentTreeOps.findParentNodeId(segments, nodeId)
    return MoveSegment(nodeId = nodeId, targetParentNodeId = parentNodeId, position = newPosition)
}

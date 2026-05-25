package com.ewo.editor.commands

import com.ewo.editor.model.*
import kotlin.test.*

class SegmentTreeOpsTest {

    private val seg1 = EditorSegment.Steady(
        nodeId = EditorNodeId("n1"),
        segmentId = "s1",
        label = null,
        note = null,
        messages = emptyList(),
        durationSec = 60,
        target = EditorTarget.Power(watts = 100),
        cadence = null,
    )
    private val seg2 = EditorSegment.Steady(
        nodeId = EditorNodeId("n2"),
        segmentId = "s2",
        label = null,
        note = null,
        messages = emptyList(),
        durationSec = 120,
        target = EditorTarget.Power(watts = 200),
        cadence = null,
    )
    private val child1 = EditorSegment.Steady(
        nodeId = EditorNodeId("c1"),
        segmentId = "c1",
        label = null,
        note = null,
        messages = emptyList(),
        durationSec = 30,
        target = EditorTarget.Power(watts = 150),
        cadence = null,
    )
    private val child2 = EditorSegment.Steady(
        nodeId = EditorNodeId("c2"),
        segmentId = "c2",
        label = null,
        note = null,
        messages = emptyList(),
        durationSec = 30,
        target = EditorTarget.Power(watts = 50),
        cadence = null,
    )
    private val repeat = EditorSegment.Repeat(
        nodeId = EditorNodeId("r1"),
        segmentId = "repeat1",
        label = null,
        note = null,
        messages = emptyList(),
        count = 3,
        segments = listOf(child1, child2),
    )

    // --- replace ---

    @Test
    fun replaceAtRootLevel() {
        val segments = listOf(seg1, seg2)
        val replacement = seg1.copy(durationSec = 999)
        val result = SegmentTreeOps.replace(segments, EditorNodeId("n1"), replacement)
        assertNotNull(result)
        assertEquals(999, (result[0] as EditorSegment.Steady).durationSec)
        assertEquals(seg2, result[1])
    }

    @Test
    fun replaceInsideRepeat() {
        val segments = listOf(seg1, repeat)
        val replacement = child1.copy(durationSec = 999)
        val result = SegmentTreeOps.replace(segments, EditorNodeId("c1"), replacement)
        assertNotNull(result)
        val updatedRepeat = result[1] as EditorSegment.Repeat
        assertEquals(999, (updatedRepeat.segments[0] as EditorSegment.Steady).durationSec)
    }

    @Test
    fun replaceNotFoundReturnsNull() {
        assertNull(SegmentTreeOps.replace(listOf(seg1), EditorNodeId("missing"), seg2))
    }

    // --- remove ---

    @Test
    fun removeAtRootLevel() {
        val result = SegmentTreeOps.remove(listOf(seg1, seg2), EditorNodeId("n1"))
        assertNotNull(result)
        assertEquals(1, result.size)
        assertEquals(EditorNodeId("n2"), result[0].nodeId)
    }

    @Test
    fun removeInsideRepeat() {
        val result = SegmentTreeOps.remove(listOf(repeat), EditorNodeId("c1"))
        assertNotNull(result)
        val updatedRepeat = result[0] as EditorSegment.Repeat
        assertEquals(1, updatedRepeat.segments.size)
        assertEquals(EditorNodeId("c2"), updatedRepeat.segments[0].nodeId)
    }

    @Test
    fun removeNotFoundReturnsNull() {
        assertNull(SegmentTreeOps.remove(listOf(seg1), EditorNodeId("missing")))
    }

    // --- insertAfter ---

    @Test
    fun insertAfterAtRootLevel() {
        val newSeg = seg2.copy(nodeId = EditorNodeId("n3"), segmentId = "s3")
        val result = SegmentTreeOps.insertAfter(listOf(seg1, seg2), EditorNodeId("n1"), newSeg)
        assertNotNull(result)
        assertEquals(3, result.size)
        assertEquals(EditorNodeId("n3"), result[1].nodeId)
    }

    @Test
    fun insertAfterInsideRepeat() {
        val newSeg = child1.copy(nodeId = EditorNodeId("c3"), segmentId = "c3")
        val result = SegmentTreeOps.insertAfter(listOf(repeat), EditorNodeId("c1"), newSeg)
        assertNotNull(result)
        val updatedRepeat = result[0] as EditorSegment.Repeat
        assertEquals(3, updatedRepeat.segments.size)
        assertEquals(EditorNodeId("c3"), updatedRepeat.segments[1].nodeId)
    }

    // --- insertChild ---

    @Test
    fun insertChildAtBeginning() {
        val newSeg = child1.copy(nodeId = EditorNodeId("c3"), segmentId = "c3")
        val result = SegmentTreeOps.insertChild(listOf(repeat), EditorNodeId("r1"), newSeg, 0)
        assertNotNull(result)
        val updatedRepeat = result[0] as EditorSegment.Repeat
        assertEquals(3, updatedRepeat.segments.size)
        assertEquals(EditorNodeId("c3"), updatedRepeat.segments[0].nodeId)
    }

    @Test
    fun insertChildAtEnd() {
        val newSeg = child1.copy(nodeId = EditorNodeId("c3"), segmentId = "c3")
        val result = SegmentTreeOps.insertChild(listOf(repeat), EditorNodeId("r1"), newSeg, 2)
        assertNotNull(result)
        val updatedRepeat = result[0] as EditorSegment.Repeat
        assertEquals(3, updatedRepeat.segments.size)
        assertEquals(EditorNodeId("c3"), updatedRepeat.segments[2].nodeId)
    }

    @Test
    fun insertChildOnNonRepeatReturnsNull() {
        val newSeg = child1.copy(nodeId = EditorNodeId("c3"))
        assertNull(SegmentTreeOps.insertChild(listOf(seg1), EditorNodeId("n1"), newSeg, 0))
    }

    // --- findSiblings ---

    @Test
    fun findSiblingsAtRootLevel() {
        val segments = listOf(seg1, seg2)
        val result = SegmentTreeOps.findSiblings(segments, EditorNodeId("n1"))
        assertNotNull(result)
        assertEquals(2, result.size)
    }

    @Test
    fun findSiblingsInsideRepeat() {
        val result = SegmentTreeOps.findSiblings(listOf(repeat), EditorNodeId("c1"))
        assertNotNull(result)
        assertEquals(2, result.size) // child1, child2
    }

    // --- wrapInRepeat ---

    @Test
    fun wrapConsecutiveSiblings() {
        val segments = listOf(seg1, seg2)
        val wrapper = EditorSegment.Repeat(
            nodeId = EditorNodeId("r_new"),
            segmentId = "wrapped",
            label = null,
            note = null,
            messages = emptyList(),
            count = 2,
            segments = listOf(seg1, seg2),
        )
        val result = SegmentTreeOps.wrapInRepeat(segments, listOf(EditorNodeId("n1"), EditorNodeId("n2")), wrapper)
        assertNotNull(result)
        assertEquals(1, result.size)
        assertIs<EditorSegment.Repeat>(result[0])
    }

    // --- unwrapRepeat ---

    @Test
    fun unwrapRepeatFlattens() {
        val result = SegmentTreeOps.unwrapRepeat(listOf(repeat), EditorNodeId("r1"))
        assertNotNull(result)
        assertEquals(2, result.size)
        assertEquals(EditorNodeId("c1"), result[0].nodeId)
        assertEquals(EditorNodeId("c2"), result[1].nodeId)
    }

    @Test
    fun unwrapNonRepeatReturnsNull() {
        assertNull(SegmentTreeOps.unwrapRepeat(listOf(seg1), EditorNodeId("n1")))
    }

    // --- deepCopy ---

    @Test
    fun deepCopyAssignsNewNodeIdsAndSegmentIds() {
        var counter = 100
        val copy = SegmentTreeOps.deepCopy(repeat) { EditorNodeId("node_${counter++}") }
        val copyRepeat = copy as EditorSegment.Repeat

        // Node IDs must be new
        assertNotEquals(repeat.nodeId, copyRepeat.nodeId)
        assertNotEquals(repeat.segments[0].nodeId, copyRepeat.segments[0].nodeId)

        // Segment IDs must also be new and unique
        assertNotEquals(repeat.segmentId, copyRepeat.segmentId)
        assertNotEquals(repeat.segments[0].segmentId, copyRepeat.segments[0].segmentId)
        assertNotEquals(repeat.segments[1].segmentId, copyRepeat.segments[1].segmentId)

        // All segment IDs in the copy must be distinct from each other
        val allSegmentIds = listOf(copyRepeat.segmentId) +
            copyRepeat.segments.map { it.segmentId }
        assertEquals(allSegmentIds.size, allSegmentIds.toSet().size, "All segment IDs must be unique")
    }

    // --- message operations ---

    @Test
    fun addMessageToSegment() {
        val msg = EditorMessage(
            nodeId = EditorNodeId("m1"),
            kind = "instruction",
            timing = EditorMessageTiming(),
            defaultText = "Go!",
            translations = emptyMap(),
        )
        val result = SegmentTreeOps.addMessage(listOf(seg1), EditorNodeId("n1"), msg)
        assertNotNull(result)
        assertEquals(1, result[0].messages.size)
    }

    @Test
    fun removeMessageFromSegment() {
        val msg = EditorMessage(
            nodeId = EditorNodeId("m1"),
            kind = "instruction",
            timing = EditorMessageTiming(),
            defaultText = "Go!",
            translations = emptyMap(),
        )
        val segWithMsg = seg1.copy(messages = listOf(msg))
        val result = SegmentTreeOps.removeMessage(listOf(segWithMsg), EditorNodeId("m1"))
        assertNotNull(result)
        assertTrue(result[0].messages.isEmpty())
    }

    @Test
    fun updateMessageInSegment() {
        val msg = EditorMessage(
            nodeId = EditorNodeId("m1"),
            kind = "instruction",
            timing = EditorMessageTiming(),
            defaultText = "Go!",
            translations = emptyMap(),
        )
        val segWithMsg = seg1.copy(messages = listOf(msg))
        val result = SegmentTreeOps.updateMessage(listOf(segWithMsg), EditorNodeId("m1")) {
            it.copy(defaultText = "Updated!")
        }
        assertNotNull(result)
        assertEquals("Updated!", result[0].messages[0].defaultText)
    }

    // --- findParentNodeId ---

    @Test
    fun findParentNodeIdAtRoot() {
        val segments = listOf(seg1, seg2)
        val parent = SegmentTreeOps.findParentNodeId(segments, EditorNodeId("n1"))
        assertNull(parent, "Root-level segment should have null parent")
    }

    @Test
    fun findParentNodeIdInsideRepeat() {
        val segments = listOf(seg1, repeat)
        val parent = SegmentTreeOps.findParentNodeId(segments, EditorNodeId("c1"))
        assertEquals(EditorNodeId("r1"), parent)
    }

    @Test
    fun findParentNodeIdNotFound() {
        val segments = listOf(seg1, seg2)
        val parent = SegmentTreeOps.findParentNodeId(segments, EditorNodeId("nonexistent"))
        assertNull(parent)
    }

    // --- computeMoveTarget ---

    @Test
    fun computeMoveTargetUp() {
        val segments = listOf(seg1, seg2)
        val cmd = computeMoveTarget(segments, EditorNodeId("n2"), MoveDirection.UP)
        assertNotNull(cmd)
        assertEquals(EditorNodeId("n2"), cmd.nodeId)
        assertNull(cmd.targetParentNodeId)
        assertEquals(0, cmd.position)
    }

    @Test
    fun computeMoveTargetDown() {
        val segments = listOf(seg1, seg2)
        val cmd = computeMoveTarget(segments, EditorNodeId("n1"), MoveDirection.DOWN)
        assertNotNull(cmd)
        assertEquals(EditorNodeId("n1"), cmd.nodeId)
        assertNull(cmd.targetParentNodeId)
        assertEquals(1, cmd.position)
    }

    @Test
    fun computeMoveTargetUpAtBoundary() {
        val segments = listOf(seg1, seg2)
        val cmd = computeMoveTarget(segments, EditorNodeId("n1"), MoveDirection.UP)
        assertNull(cmd, "Cannot move the first segment up")
    }

    @Test
    fun computeMoveTargetDownAtBoundary() {
        val segments = listOf(seg1, seg2)
        val cmd = computeMoveTarget(segments, EditorNodeId("n2"), MoveDirection.DOWN)
        assertNull(cmd, "Cannot move the last segment down")
    }

    @Test
    fun computeMoveTargetInsideRepeat() {
        val segments = listOf(seg1, repeat)
        val cmd = computeMoveTarget(segments, EditorNodeId("c2"), MoveDirection.UP)
        assertNotNull(cmd)
        assertEquals(EditorNodeId("c2"), cmd.nodeId)
        assertEquals(EditorNodeId("r1"), cmd.targetParentNodeId)
        assertEquals(0, cmd.position)
    }
}

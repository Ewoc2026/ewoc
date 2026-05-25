package com.ewo.editor.commands

import com.ewo.core.HrReference
import com.ewo.editor.model.*
import kotlin.test.*

class EditorCommandExecutorTest {

    // --- Test fixtures ---

    private fun emptyDoc(): EditorWorkoutDocument = EditorDocumentFactory.empty()

    /** A document with a single Steady segment for tests that need one to operate on. */
    private fun docWithOneSteady(): EditorWorkoutDocument = emptyDoc().copy(
        segments = listOf(
            EditorSegment.Steady(
                nodeId = EditorNodeId("node_2"),
                segmentId = "steady",
                label = null,
                note = null,
                messages = emptyList(),
                durationSec = 300,
                target = EditorTarget.Power(watts = 100),
                cadence = null,
            ),
        ),
        nextNodeCounter = 3,
    )

    private fun docWithTwoSegments(): EditorWorkoutDocument {
        val seg1 = EditorSegment.Steady(
            nodeId = EditorNodeId("node_2"),
            segmentId = "warmup",
            label = null,
            note = null,
            messages = emptyList(),
            durationSec = 300,
            target = EditorTarget.Power(watts = 100),
            cadence = null,
        )
        val seg2 = EditorSegment.Steady(
            nodeId = EditorNodeId("node_3"),
            segmentId = "main",
            label = null,
            note = null,
            messages = emptyList(),
            durationSec = 600,
            target = EditorTarget.FtpPercent(fraction = 0.90),
            cadence = null,
        )
        return EditorDocumentFactory.empty().copy(
            segments = listOf(seg1, seg2),
            nextNodeCounter = 4,
        )
    }

    private fun docWithRepeat(): EditorWorkoutDocument {
        val child1 = EditorSegment.Steady(
            nodeId = EditorNodeId("node_3"),
            segmentId = "on",
            label = null,
            note = null,
            messages = emptyList(),
            durationSec = 60,
            target = EditorTarget.FtpPercent(fraction = 1.20),
            cadence = null,
        )
        val child2 = EditorSegment.Steady(
            nodeId = EditorNodeId("node_4"),
            segmentId = "off",
            label = null,
            note = null,
            messages = emptyList(),
            durationSec = 60,
            target = EditorTarget.FtpPercent(fraction = 0.50),
            cadence = null,
        )
        val repeat = EditorSegment.Repeat(
            nodeId = EditorNodeId("node_2"),
            segmentId = "intervals",
            label = null,
            note = null,
            messages = emptyList(),
            count = 5,
            segments = listOf(child1, child2),
        )
        return EditorDocumentFactory.empty().copy(
            segments = listOf(repeat),
            expandedNodeIds = setOf(EditorNodeId("node_2")),
            nextNodeCounter = 5,
        )
    }

    private fun historyOf(doc: EditorWorkoutDocument) = EditorHistory.of(doc)

    // === Workout-level properties ===

    @Test
    fun setWorkoutTitle() {
        val h = historyOf(emptyDoc())
        val result = EditorCommandExecutor.execute(h, SetWorkoutTitle("My Workout"))
        assertEquals("My Workout", result.present.title)
        assertTrue(result.present.validationMarkers.none { it.code == "empty_title" })
        assertTrue(result.present.validationMarkers.any { it.code == "empty_segments" && it.blocksExport })
        assertTrue(result.present.isDirty)
        assertTrue(result.canUndo)
    }

    @Test
    fun emptyDocumentStartsWithBlockingTitleValidation() {
        val document = emptyDoc()

        assertFalse(document.canExport)
        assertTrue(document.validationMarkers.any { it.code == "empty_title" && it.blocksExport })
    }

    @Test
    fun setWorkoutDescription() {
        val h = historyOf(emptyDoc())
        val result = EditorCommandExecutor.execute(h, SetWorkoutDescription("desc"))
        assertEquals("desc", result.present.description)
    }

    @Test
    fun setDifficulty() {
        val h = historyOf(emptyDoc())
        val result = EditorCommandExecutor.execute(h, SetDifficulty("hard"))
        assertEquals("hard", result.present.difficulty)
    }

    @Test
    fun setTags() {
        val h = historyOf(emptyDoc())
        val result = EditorCommandExecutor.execute(h, SetTags(listOf("endurance", "base")))
        assertEquals(listOf("endurance", "base"), result.present.tags)
    }

    @Test
    fun setVersion() {
        val h = historyOf(emptyDoc())
        val result = EditorCommandExecutor.execute(h, SetVersion("1.2"))
        assertEquals("1.2", result.present.version)
    }

    @Test
    fun setWorkoutUidAndRevision() {
        val h = historyOf(emptyDoc())
        val withUid = EditorCommandExecutor.execute(h, SetWorkoutUid("workout-uid"))
        val withRevision = EditorCommandExecutor.execute(withUid, SetWorkoutRevision(2))
        assertEquals("workout-uid", withRevision.present.uid)
        assertEquals(2, withRevision.present.revision)
    }

    @Test
    fun compositeCommandAppliesAllChangesInSingleUndoEntry() {
        val h = historyOf(emptyDoc())
        val result = EditorCommandExecutor.execute(
            h,
            CompositeCommand(
                listOf(
                    SetWorkoutTitle("My Workout"),
                    SetWorkoutDescription("Built atomically"),
                ),
            ),
        )

        assertEquals("My Workout", result.present.title)
        assertEquals("Built atomically", result.present.description)
        assertTrue(result.canUndo)

        val undone = result.undo()
        assertEquals("", undone.present.title)
        assertEquals("", undone.present.description)
    }

    @Test
    fun compositeCommandRollsBackWhenAnySubcommandFails() {
        val h = historyOf(emptyDoc())
        val result = EditorCommandExecutor.execute(
            h,
            CompositeCommand(
                listOf(
                    SetWorkoutTitle("Should not stick"),
                    DeleteSegment(EditorNodeId("missing")),
                ),
            ),
        )

        assertSame(h, result)
        assertEquals("", result.present.title)
        assertFalse(result.canUndo)
    }

    // === Segment CRUD ===

    @Test
    fun addSegmentAppendsToRoot() {
        val h = historyOf(emptyDoc())
        val result = EditorCommandExecutor.execute(h, AddSegment(NewSegmentType.STEADY))
        assertEquals(1, result.present.segments.size)
        // New segment is selected
        assertEquals(result.present.segments[0].nodeId, result.present.selectedNodeId)
    }

    @Test
    fun addSegmentAfterExisting() {
        val doc = docWithTwoSegments()
        val h = historyOf(doc)
        val result = EditorCommandExecutor.execute(h, AddSegment(
            segmentType = NewSegmentType.RAMP,
            afterNodeId = EditorNodeId("node_2"),
        ))
        assertEquals(3, result.present.segments.size)
        assertIs<EditorSegment.Ramp>(result.present.segments[1])
    }

    @Test
    fun addRampSegmentUsesNewBuildupDefaults() {
        val h = historyOf(emptyDoc())
        val result = EditorCommandExecutor.execute(h, AddSegment(NewSegmentType.RAMP))

        val ramp = assertIs<EditorSegment.Ramp>(result.present.segments.single())
        val from = assertIs<EditorTarget.FtpPercent>(ramp.fromTarget)
        val to = assertIs<EditorTarget.FtpPercent>(ramp.toTarget)

        assertEquals(0.45, from.fraction, 0.0001)
        assertEquals(0.65, to.fraction, 0.0001)
    }

    @Test
    fun addRampDownSegmentUsesDescendingDefaultTargets() {
        val h = historyOf(emptyDoc())
        val result = EditorCommandExecutor.execute(h, AddSegment(NewSegmentType.RAMP_DOWN))

        val ramp = assertIs<EditorSegment.Ramp>(result.present.segments.single())
        val from = assertIs<EditorTarget.FtpPercent>(ramp.fromTarget)
        val to = assertIs<EditorTarget.FtpPercent>(ramp.toTarget)

        assertTrue(from.fraction > to.fraction)
    }

    @Test
    fun addSegmentToRepeatParent() {
        val doc = docWithRepeat()
        val h = historyOf(doc)
        val result = EditorCommandExecutor.execute(h, AddSegment(
            segmentType = NewSegmentType.STEADY,
            parentNodeId = EditorNodeId("node_2"),
        ))
        val repeat = result.present.segments[0] as EditorSegment.Repeat
        assertEquals(3, repeat.segments.size)
    }

    @Test
    fun addRepeatSegment() {
        val h = historyOf(emptyDoc())
        val result = EditorCommandExecutor.execute(h, AddSegment(NewSegmentType.REPEAT))
        assertEquals(1, result.present.segments.size)
        assertIs<EditorSegment.Repeat>(result.present.segments[0])
    }

    @Test
    fun addFreeRideSegment() {
        val h = historyOf(emptyDoc())
        val result = EditorCommandExecutor.execute(h, AddSegment(NewSegmentType.FREE_RIDE))
        assertIs<EditorSegment.FreeRide>(result.present.segments[0])
    }

    @Test
    fun deleteSegment() {
        val doc = docWithTwoSegments()
        val h = historyOf(doc)
        val result = EditorCommandExecutor.execute(h, DeleteSegment(EditorNodeId("node_2")))
        assertEquals(1, result.present.segments.size)
        assertEquals("main", result.present.segments[0].segmentId)
    }

    @Test
    fun deleteSegmentClearsSelectionIfSelected() {
        val doc = docWithTwoSegments().copy(selectedNodeId = EditorNodeId("node_2"))
        val h = historyOf(doc)
        val result = EditorCommandExecutor.execute(h, DeleteSegment(EditorNodeId("node_2")))
        assertNull(result.present.selectedNodeId)
    }

    @Test
    fun deleteSegmentPreservesSelectionIfOther() {
        val doc = docWithTwoSegments().copy(selectedNodeId = EditorNodeId("node_3"))
        val h = historyOf(doc)
        val result = EditorCommandExecutor.execute(h, DeleteSegment(EditorNodeId("node_2")))
        assertEquals(EditorNodeId("node_3"), result.present.selectedNodeId)
    }

    @Test
    fun deleteNonexistentSegmentIsNoOp() {
        val h = historyOf(emptyDoc())
        val result = EditorCommandExecutor.execute(h, DeleteSegment(EditorNodeId("nonexistent")))
        assertFalse(result.canUndo, "No undo entry for failed command")
    }

    @Test
    fun duplicateSegment() {
        val h0 = historyOf(emptyDoc())
        val h = EditorCommandExecutor.execute(h0, AddSegment(NewSegmentType.STEADY))
        val segNodeId = h.present.segments.single().nodeId
        val result = EditorCommandExecutor.execute(h, DuplicateSegment(segNodeId))
        assertEquals(2, result.present.segments.size)
        // Copy has different nodeId
        assertNotEquals(result.present.segments[0].nodeId, result.present.segments[1].nodeId)
        // Copy is selected
        assertEquals(result.present.segments[1].nodeId, result.present.selectedNodeId)
    }

    @Test
    fun duplicateRepeatDeepCopiesChildren() {
        val doc = docWithRepeat()
        val h = historyOf(doc)
        val result = EditorCommandExecutor.execute(h, DuplicateSegment(EditorNodeId("node_2")))
        assertEquals(2, result.present.segments.size)
        val original = result.present.segments[0] as EditorSegment.Repeat
        val copy = result.present.segments[1] as EditorSegment.Repeat
        assertNotEquals(original.nodeId, copy.nodeId)
        assertEquals(original.segments.size, copy.segments.size)
        // Children have unique node IDs
        assertNotEquals(original.segments[0].nodeId, copy.segments[0].nodeId)
    }

    @Test
    fun moveSegmentToRootPosition() {
        val doc = docWithTwoSegments()
        val h = historyOf(doc)
        val result = EditorCommandExecutor.execute(h, MoveSegment(
            nodeId = EditorNodeId("node_3"),
            targetParentNodeId = null,
            position = 0,
        ))
        assertEquals("main", result.present.segments[0].segmentId)
        assertEquals("warmup", result.present.segments[1].segmentId)
    }

    @Test
    fun moveSegmentIntoRepeat() {
        val doc = docWithRepeat().copy(
            segments = docWithRepeat().segments + EditorSegment.Steady(
                nodeId = EditorNodeId("node_5"),
                segmentId = "cooldown",
                label = null,
                note = null,
                messages = emptyList(),
                durationSec = 300,
                target = EditorTarget.Power(watts = 80),
                cadence = null,
            ),
            nextNodeCounter = 6,
        )
        val h = historyOf(doc)
        val result = EditorCommandExecutor.execute(h, MoveSegment(
            nodeId = EditorNodeId("node_5"),
            targetParentNodeId = EditorNodeId("node_2"),
            position = 1,
        ))
        assertEquals(1, result.present.segments.size) // only repeat at root
        val repeat = result.present.segments[0] as EditorSegment.Repeat
        assertEquals(3, repeat.segments.size)
        assertEquals("cooldown", repeat.segments[1].segmentId)
    }

    // === Repeat operations ===

    @Test
    fun wrapInRepeat() {
        val doc = docWithTwoSegments()
        val h = historyOf(doc)
        val result = EditorCommandExecutor.execute(h, WrapInRepeat(
            listOf(EditorNodeId("node_2"), EditorNodeId("node_3"))
        ))
        assertEquals(1, result.present.segments.size)
        val repeat = result.present.segments[0] as EditorSegment.Repeat
        assertEquals(2, repeat.segments.size)
        assertEquals(2, repeat.count)
        // Repeat is selected and expanded
        assertEquals(repeat.nodeId, result.present.selectedNodeId)
        assertTrue(repeat.nodeId in result.present.expandedNodeIds)
    }

    @Test
    fun wrapEmptyListIsNoOp() {
        val h = historyOf(emptyDoc())
        val result = EditorCommandExecutor.execute(h, WrapInRepeat(emptyList()))
        assertFalse(result.canUndo)
    }

    @Test
    fun unwrapRepeat() {
        val doc = docWithRepeat()
        val h = historyOf(doc)
        val result = EditorCommandExecutor.execute(h, UnwrapRepeat(EditorNodeId("node_2")))
        assertEquals(2, result.present.segments.size)
        assertIs<EditorSegment.Steady>(result.present.segments[0])
        assertIs<EditorSegment.Steady>(result.present.segments[1])
    }

    @Test
    fun unwrapRepeatClearsSelection() {
        val doc = docWithRepeat().copy(selectedNodeId = EditorNodeId("node_2"))
        val h = historyOf(doc)
        val result = EditorCommandExecutor.execute(h, UnwrapRepeat(EditorNodeId("node_2")))
        assertNull(result.present.selectedNodeId)
    }

    @Test
    fun unwrapNonRepeatIsNoOp() {
        val h = historyOf(docWithOneSteady())
        val result = EditorCommandExecutor.execute(h, UnwrapRepeat(EditorNodeId("node_2")))
        // node_2 is a Steady, so unwrap fails
        assertFalse(result.canUndo)
    }

    // === Segment properties ===

    @Test
    fun setSegmentId() {
        val h = historyOf(docWithOneSteady())
        val result = EditorCommandExecutor.execute(h, SetSegmentId(EditorNodeId("node_2"), "new_id"))
        assertEquals("new_id", result.present.segments[0].segmentId)
    }

    @Test
    fun setSegmentLabelAndNote() {
        val h = historyOf(docWithOneSteady())
        val withLabel = EditorCommandExecutor.execute(h, SetSegmentLabel(EditorNodeId("node_2"), "Warmup"))
        val withNote = EditorCommandExecutor.execute(withLabel, SetSegmentNote(EditorNodeId("node_2"), "Keep cadence loose"))
        val segment = withNote.present.segments[0] as EditorSegment.Steady
        assertEquals("Warmup", segment.label)
        assertEquals("Keep cadence loose", segment.note)
    }

    @Test
    fun setSegmentDuration() {
        val h = historyOf(docWithOneSteady())
        val result = EditorCommandExecutor.execute(h, SetSegmentDuration(EditorNodeId("node_2"), 600))
        assertEquals(600, (result.present.segments[0] as EditorSegment.Steady).durationSec)
    }

    @Test
    fun setSegmentDurationOnRepeatIsNoOp() {
        val h = historyOf(docWithRepeat())
        val result = EditorCommandExecutor.execute(h, SetSegmentDuration(EditorNodeId("node_2"), 600))
        assertFalse(result.canUndo)
    }

    @Test
    fun setSteadyTarget() {
        val h = historyOf(docWithOneSteady())
        val newTarget = EditorTarget.Power(watts = 200)
        val result = EditorCommandExecutor.execute(h, SetSteadyTarget(EditorNodeId("node_2"), newTarget))
        assertEquals(newTarget, (result.present.segments[0] as EditorSegment.Steady).target)
    }

    @Test
    fun setSteadyTargetOnRampIsNoOp() {
        val doc = emptyDoc().copy(
            segments = listOf(
                EditorSegment.Ramp(
                    nodeId = EditorNodeId("node_2"),
                    segmentId = "ramp",
                    label = null,
                    note = null,
                    messages = emptyList(),
                    durationSec = 300,
                    fromTarget = EditorTarget.Power(watts = 100),
                    toTarget = EditorTarget.Power(watts = 200),
                    cadence = null,
                )
            )
        )
        val h = historyOf(doc)
        val result = EditorCommandExecutor.execute(h, SetSteadyTarget(EditorNodeId("node_2"), EditorTarget.Power(watts = 150)))
        assertFalse(result.canUndo)
    }

    @Test
    fun setRampTargets() {
        val doc = emptyDoc().copy(
            segments = listOf(
                EditorSegment.Ramp(
                    nodeId = EditorNodeId("node_2"),
                    segmentId = "ramp",
                    label = null,
                    note = null,
                    messages = emptyList(),
                    durationSec = 300,
                    fromTarget = EditorTarget.Power(watts = 100),
                    toTarget = EditorTarget.Power(watts = 200),
                    cadence = null,
                )
            )
        )
        val h = historyOf(doc)
        val from = EditorTarget.FtpPercent(fraction = 0.50)
        val to = EditorTarget.FtpPercent(fraction = 1.00)
        val result = EditorCommandExecutor.execute(h, SetRampTargets(EditorNodeId("node_2"), from, to))
        val ramp = result.present.segments[0] as EditorSegment.Ramp
        assertEquals(from, ramp.fromTarget)
        assertEquals(to, ramp.toTarget)
    }

    @Test
    fun setRampTargetsRejectsHeartRateRelative() {
        val doc = emptyDoc().copy(
            segments = listOf(
                EditorSegment.Ramp(
                    nodeId = EditorNodeId("node_2"),
                    segmentId = "ramp",
                    label = null,
                    note = null,
                    messages = emptyList(),
                    durationSec = 300,
                    fromTarget = EditorTarget.Power(watts = 100),
                    toTarget = EditorTarget.Power(watts = 200),
                    cadence = null,
                )
            )
        )
        val h = historyOf(doc)
        val from = EditorTarget.HeartRateRelative(HrReference.HR_MAX, 0.60, 0.70)
        val to = EditorTarget.HeartRateRelative(HrReference.HR_MAX, 0.70, 0.80)
        val result = EditorCommandExecutor.execute(h, SetRampTargets(EditorNodeId("node_2"), from, to))
        assertFalse(result.canUndo, "HR relative targets in ramps should be rejected")
    }

    @Test
    fun setSteadyTargetAcceptsHeartRateRelative() {
        val h = historyOf(docWithOneSteady())
        val newTarget = EditorTarget.HeartRateRelative(HrReference.HR_MAX, 0.72, 0.80)
        val result = EditorCommandExecutor.execute(h, SetSteadyTarget(EditorNodeId("node_2"), newTarget))
        assertEquals(newTarget, (result.present.segments[0] as EditorSegment.Steady).target)
    }

    @Test
    fun setCadence() {
        val h = historyOf(docWithOneSteady())
        val cadence = EditorCadenceRange(low = 80, high = 100)
        val result = EditorCommandExecutor.execute(h, SetCadence(EditorNodeId("node_2"), cadence))
        assertEquals(cadence, (result.present.segments[0] as EditorSegment.Steady).cadence)
    }

    @Test
    fun setCadenceNull() {
        val doc = docWithOneSteady().let { d ->
            val seg = d.segments[0] as EditorSegment.Steady
            d.copy(segments = listOf(seg.copy(cadence = EditorCadenceRange(80, 100))))
        }
        val h = historyOf(doc)
        val result = EditorCommandExecutor.execute(h, SetCadence(EditorNodeId("node_2"), null))
        assertNull((result.present.segments[0] as EditorSegment.Steady).cadence)
    }

    // === Nested segment property changes ===

    @Test
    fun setDurationOnNestedSegment() {
        val h = historyOf(docWithRepeat())
        val result = EditorCommandExecutor.execute(h, SetSegmentDuration(EditorNodeId("node_3"), 120))
        val repeat = result.present.segments[0] as EditorSegment.Repeat
        assertEquals(120, (repeat.segments[0] as EditorSegment.Steady).durationSec)
    }

    // === Messages ===

    @Test
    fun addMessage() {
        val h = historyOf(docWithOneSteady())
        val result = EditorCommandExecutor.execute(h, AddMessage(
            parentNodeId = EditorNodeId("node_2"),
            kind = "instruction",
            defaultText = "Let's go!",
        ))
        val seg = result.present.segments[0]
        assertEquals(1, seg.messages.size)
        assertEquals("instruction", seg.messages[0].kind)
        assertEquals("Let's go!", seg.messages[0].defaultText)
    }

    @Test
    fun addMessageWithStructuredTiming() {
        val h = historyOf(docWithOneSteady())
        val result = EditorCommandExecutor.execute(h, AddMessage(
            parentNodeId = EditorNodeId("node_2"),
            kind = "instruction",
            timing = EditorMessageTiming(anchor = EditorMessageAnchor.END, offsetSec = -5),
            defaultText = "Recover soon",
        ))
        val message = result.present.segments[0].messages.single()
        assertEquals(EditorMessageAnchor.END, message.timing.anchor)
        assertEquals(-5, message.timing.offsetSec)
    }

    @Test
    fun updateMessage() {
        // First add a message, then update it
        var h = historyOf(docWithOneSteady())
        h = EditorCommandExecutor.execute(h, AddMessage(
            parentNodeId = EditorNodeId("node_2"),
            kind = "instruction",
            defaultText = "Let's go!",
        ))
        val msgNodeId = h.present.segments[0].messages[0].nodeId

        h = EditorCommandExecutor.execute(h, UpdateMessage(
            messageNodeId = msgNodeId,
            timing = EditorMessageTiming(anchor = EditorMessageAnchor.END, offsetSec = -3),
            defaultText = "Push harder!",
        ))
        val msg = h.present.segments[0].messages[0]
        assertEquals("Push harder!", msg.defaultText)
        assertEquals("instruction", msg.kind) // kind unchanged
        assertEquals(EditorMessageAnchor.END, msg.timing.anchor)
    }

    @Test
    fun deleteMessage() {
        var h = historyOf(docWithOneSteady())
        h = EditorCommandExecutor.execute(h, AddMessage(
            parentNodeId = EditorNodeId("node_2"),
            kind = "instruction",
            defaultText = "msg",
        ))
        val msgNodeId = h.present.segments[0].messages[0].nodeId
        h = EditorCommandExecutor.execute(h, DeleteMessage(msgNodeId))
        assertTrue(h.present.segments[0].messages.isEmpty())
    }

    // === Control ===

    @Test
    fun setControl() {
        val h = historyOf(emptyDoc())
        val control = EditorControl(
            initialPowerWatts = 100,
            minPowerWatts = 50,
            maxPowerWatts = 300,
            signalLossPowerWatts = 80,
            hrUpperCapBpm = 180,
        )
        val result = EditorCommandExecutor.execute(h, SetControl(control))
        assertEquals(control, result.present.control)
    }

    @Test
    fun clearControl() {
        val doc = emptyDoc().copy(
            control = EditorControl(100, 50, 300, 80, 180)
        )
        val h = historyOf(doc)
        val result = EditorCommandExecutor.execute(h, SetControl(null))
        assertNull(result.present.control)
    }

    // === View actions ===

    @Test
    fun selectDoesNotCreateUndoEntry() {
        val h = historyOf(emptyDoc())
        val result = EditorCommandExecutor.applyViewAction(h, EditorViewAction.Select(EditorNodeId("node_2")))
        assertEquals(EditorNodeId("node_2"), result.present.selectedNodeId)
        assertFalse(result.canUndo)
    }

    @Test
    fun deselectNode() {
        val doc = emptyDoc().copy(selectedNodeId = EditorNodeId("node_2"))
        val h = historyOf(doc)
        val result = EditorCommandExecutor.applyViewAction(h, EditorViewAction.Select(null))
        assertNull(result.present.selectedNodeId)
    }

    @Test
    fun toggleExpandedAdds() {
        val h = historyOf(emptyDoc())
        val result = EditorCommandExecutor.applyViewAction(h, EditorViewAction.ToggleExpanded(EditorNodeId("node_2")))
        assertTrue(EditorNodeId("node_2") in result.present.expandedNodeIds)
        assertFalse(result.canUndo)
    }

    @Test
    fun toggleExpandedRemoves() {
        val doc = emptyDoc().copy(expandedNodeIds = setOf(EditorNodeId("node_2")))
        val h = historyOf(doc)
        val result = EditorCommandExecutor.applyViewAction(h, EditorViewAction.ToggleExpanded(EditorNodeId("node_2")))
        assertFalse(EditorNodeId("node_2") in result.present.expandedNodeIds)
    }

    // === Undo / redo round-trips ===

    @Test
    fun undoRestoresDocumentState() {
        val original = emptyDoc()
        var h = historyOf(original)
        h = EditorCommandExecutor.execute(h, SetWorkoutTitle("Changed"))
        assertEquals("Changed", h.present.title)

        h = h.undo()
        assertEquals(original.title, h.present.title)
        assertFalse(h.present.isDirty)
    }

    @Test
    fun redoReappliesCommand() {
        var h = historyOf(emptyDoc())
        h = EditorCommandExecutor.execute(h, SetWorkoutTitle("Changed"))
        h = h.undo()
        h = h.redo()
        assertEquals("Changed", h.present.title)
    }

    @Test
    fun undoRedoRoundTripForSegmentDelete() {
        val doc = docWithTwoSegments()
        var h = historyOf(doc)
        h = EditorCommandExecutor.execute(h, DeleteSegment(EditorNodeId("node_2")))
        assertEquals(1, h.present.segments.size)

        h = h.undo()
        assertEquals(2, h.present.segments.size)
        assertEquals("warmup", h.present.segments[0].segmentId)

        h = h.redo()
        assertEquals(1, h.present.segments.size)
    }

    @Test
    fun multipleCommandsUndoInOrder() {
        var h = historyOf(emptyDoc())
        h = EditorCommandExecutor.execute(h, SetWorkoutTitle("Title 1"))
        h = EditorCommandExecutor.execute(h, SetWorkoutTitle("Title 2"))
        h = EditorCommandExecutor.execute(h, SetWorkoutTitle("Title 3"))

        h = h.undo()
        assertEquals("Title 2", h.present.title)
        h = h.undo()
        assertEquals("Title 1", h.present.title)
        h = h.undo()
        assertEquals("", h.present.title) // original empty title
    }

    @Test
    fun newCommandClearsFuture() {
        var h = historyOf(emptyDoc())
        h = EditorCommandExecutor.execute(h, SetWorkoutTitle("A"))
        h = EditorCommandExecutor.execute(h, SetWorkoutTitle("B"))
        h = h.undo() // back to "A"
        h = EditorCommandExecutor.execute(h, SetWorkoutTitle("C")) // diverge
        assertFalse(h.canRedo)
        assertEquals("C", h.present.title)
    }

    // === isDirty flag ===

    @Test
    fun commandSetsDirtyFlag() {
        val h = historyOf(emptyDoc())
        assertFalse(h.present.isDirty)
        val result = EditorCommandExecutor.execute(h, SetWorkoutTitle("changed"))
        assertTrue(result.present.isDirty)
    }

    @Test
    fun failedCommandDoesNotSetDirty() {
        val h = historyOf(emptyDoc())
        val result = EditorCommandExecutor.execute(h, DeleteSegment(EditorNodeId("nonexistent")))
        assertFalse(result.present.isDirty)
    }
}

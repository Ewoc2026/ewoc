package com.ewo.editor.commands

import com.ewo.editor.model.*

/**
 * Applies [EditorCommand] instances to an [EditorHistory], producing a new history
 * with the command's effect reflected in the present document.
 *
 * Every command creates an undo entry (via [EditorHistory.push]). View-state actions
 * ([EditorViewAction]) update the present without creating undo entries.
 *
 * Commands that reference a non-existent [EditorNodeId] return the history unchanged.
 */
object EditorCommandExecutor {

    /** Applies a command, creating an undo entry. Returns updated history. */
    fun execute(history: EditorHistory, command: EditorCommand): EditorHistory {
        val doc = history.present
        val newDoc = applyCommand(doc, command) ?: return history
        val validatedDoc = newDoc.copy(
            isDirty = true,
            validationMarkers = EditorDocumentFactory.recomputeValidationMarkers(newDoc),
        )
        return history.push(validatedDoc)
    }

    /** Applies a view action without creating an undo entry. */
    fun applyViewAction(history: EditorHistory, action: EditorViewAction): EditorHistory {
        return history.updatePresent { doc ->
            when (action) {
                is EditorViewAction.Select -> doc.copy(selectedNodeId = action.nodeId)
                is EditorViewAction.ToggleExpanded -> {
                    val expanded = if (action.nodeId in doc.expandedNodeIds) {
                        doc.expandedNodeIds - action.nodeId
                    } else {
                        doc.expandedNodeIds + action.nodeId
                    }
                    doc.copy(expandedNodeIds = expanded)
                }
            }
        }
    }

    // --- Command dispatch ---

    private fun applyCommand(doc: EditorWorkoutDocument, command: EditorCommand): EditorWorkoutDocument? {
        return when (command) {
            // Workout-level properties
            is SetWorkoutTitle -> doc.copy(title = command.title)
            is SetWorkoutDescription -> doc.copy(description = command.description)
            is SetTitleLocalized -> doc.copy(titleLocalized = command.localized)
            is SetDescriptionLocalized -> doc.copy(descriptionLocalized = command.localized)
            is SetDifficulty -> doc.copy(difficulty = command.difficulty)
            is SetTags -> doc.copy(tags = command.tags)
            is SetVersion -> doc.copy(version = command.version)
            is SetWorkoutUid -> doc.copy(uid = command.uid)
            is SetWorkoutRevision -> doc.copy(revision = command.revision)

            // Segment CRUD
            is AddSegment -> applyAddSegment(doc, command)
            is DeleteSegment -> applyDeleteSegment(doc, command)
            is DuplicateSegment -> applyDuplicateSegment(doc, command)
            is PasteSegment -> applyPasteSegment(doc, command)
            is MoveSegment -> applyMoveSegment(doc, command)

            // Repeat operations
            is WrapInRepeat -> applyWrapInRepeat(doc, command)
            is UnwrapRepeat -> applyUnwrapRepeat(doc, command)

            // Segment properties
            is SetSegmentId -> applySetSegmentId(doc, command)
            is SetSegmentLabel -> applySetSegmentLabel(doc, command)
            is SetSegmentNote -> applySetSegmentNote(doc, command)
            is SetRepeatCount -> applySetRepeatCount(doc, command)
            is SetSegmentDuration -> applySetSegmentDuration(doc, command)
            is SetSteadyTarget -> applySetSteadyTarget(doc, command)
            is SetRampTargets -> applySetRampTargets(doc, command)
            is SetCadence -> applySetCadence(doc, command)

            // Messages
            is AddMessage -> applyAddMessage(doc, command)
            is UpdateMessage -> applyUpdateMessage(doc, command)
            is DeleteMessage -> applyDeleteMessage(doc, command)

            // Composite
            is CompositeCommand -> {
                if (command.commands.isEmpty()) return null
                var current: EditorWorkoutDocument = doc
                for (sub in command.commands) {
                    current = applyCommand(current, sub) ?: return null
                }
                current
            }

            // Control
            is SetControl -> doc.copy(control = command.control)
        }
    }

    // --- Segment CRUD ---

    private fun applyAddSegment(doc: EditorWorkoutDocument, cmd: AddSegment): EditorWorkoutDocument {
        var counter = doc.nextNodeCounter
        fun nextNodeId(): EditorNodeId {
            val id = EditorNodeId("node_$counter")
            counter++
            return id
        }

        val nodeId = nextNodeId()
        val segmentId = "segment_${nodeId.value.removePrefix("node_")}"

        var newSegment = createDefaultSegment(cmd.segmentType, nodeId, segmentId)

        // For repeat segments, generate default ON/OFF child segments
        if (cmd.segmentType == NewSegmentType.REPEAT && newSegment is EditorSegment.Repeat) {
            val onId = nextNodeId()
            val offId = nextNodeId()
            val onChild = EditorSegment.Steady(
                nodeId = onId,
                segmentId = "segment_${onId.value.removePrefix("node_")}",
                label = "ON",
                note = null,
                messages = emptyList(),
                durationSec = 60,
                target = EditorTarget.FtpPercent(fraction = 1.00),
                cadence = null,
            )
            val offChild = EditorSegment.Steady(
                nodeId = offId,
                segmentId = "segment_${offId.value.removePrefix("node_")}",
                label = "OFF",
                note = null,
                messages = emptyList(),
                durationSec = 60,
                target = EditorTarget.FtpPercent(fraction = 0.50),
                cadence = null,
            )
            newSegment = newSegment.copy(segments = listOf(onChild, offChild))
        }

        val newSegments = when {
            cmd.afterNodeId != null -> {
                SegmentTreeOps.insertAfter(doc.segments, cmd.afterNodeId, newSegment)
                    ?: return doc.copy(nextNodeCounter = counter)
            }
            cmd.parentNodeId != null -> {
                val parent = doc.findSegment(cmd.parentNodeId)
                val position = if (parent is EditorSegment.Repeat) parent.segments.size else 0
                SegmentTreeOps.insertChild(doc.segments, cmd.parentNodeId, newSegment, position)
                    ?: return doc.copy(nextNodeCounter = counter)
            }
            else -> doc.segments + newSegment
        }

        // Auto-expand repeat nodes so children are visible
        val expandedIds = if (cmd.segmentType == NewSegmentType.REPEAT) {
            doc.expandedNodeIds + nodeId
        } else {
            doc.expandedNodeIds
        }

        return doc.copy(
            segments = newSegments,
            selectedNodeId = nodeId,
            nextNodeCounter = counter,
            expandedNodeIds = expandedIds,
        )
    }

    private fun applyDeleteSegment(doc: EditorWorkoutDocument, cmd: DeleteSegment): EditorWorkoutDocument? {
        val newSegments = SegmentTreeOps.remove(doc.segments, cmd.nodeId) ?: return null
        val newSelection = if (doc.selectedNodeId == cmd.nodeId) null else doc.selectedNodeId
        return doc.copy(
            segments = newSegments,
            selectedNodeId = newSelection,
            expandedNodeIds = doc.expandedNodeIds - cmd.nodeId,
        )
    }

    private fun applyDuplicateSegment(doc: EditorWorkoutDocument, cmd: DuplicateSegment): EditorWorkoutDocument? {
        val original = doc.findSegment(cmd.nodeId) ?: return null
        var counter = doc.nextNodeCounter
        val copy = SegmentTreeOps.deepCopy(original) {
            val id = EditorNodeId("node_$counter")
            counter++
            id
        }
        val newSegments = SegmentTreeOps.insertAfter(doc.segments, cmd.nodeId, copy) ?: return null
        return doc.copy(
            segments = newSegments,
            selectedNodeId = copy.nodeId,
            nextNodeCounter = counter,
        )
    }

    private fun applyPasteSegment(doc: EditorWorkoutDocument, cmd: PasteSegment): EditorWorkoutDocument {
        var counter = doc.nextNodeCounter
        val copy = SegmentTreeOps.deepCopy(cmd.segment) {
            val id = EditorNodeId("node_$counter")
            counter++
            id
        }
        val newSegments = if (cmd.afterNodeId != null) {
            SegmentTreeOps.insertAfter(doc.segments, cmd.afterNodeId, copy) ?: (doc.segments + copy)
        } else {
            doc.segments + copy
        }
        return doc.copy(segments = newSegments, selectedNodeId = copy.nodeId, nextNodeCounter = counter)
    }

    private fun applyMoveSegment(doc: EditorWorkoutDocument, cmd: MoveSegment): EditorWorkoutDocument? {
        val segment = doc.findSegment(cmd.nodeId) ?: return null
        // Remove from current position
        val withoutSegment = SegmentTreeOps.remove(doc.segments, cmd.nodeId) ?: return null
        // Insert at new position
        val newSegments = if (cmd.targetParentNodeId != null) {
            SegmentTreeOps.insertChild(withoutSegment, cmd.targetParentNodeId, segment, cmd.position)
        } else {
            // Insert at root level
            val pos = cmd.position.coerceIn(0, withoutSegment.size)
            withoutSegment.toMutableList().apply { add(pos, segment) }
        } ?: return null
        return doc.copy(segments = newSegments)
    }

    // --- Repeat operations ---

    private fun applyWrapInRepeat(doc: EditorWorkoutDocument, cmd: WrapInRepeat): EditorWorkoutDocument? {
        if (cmd.nodeIds.isEmpty()) return null
        val (repeatNodeId, counter) = doc.generateNodeId()
        val segmentId = "repeat_${repeatNodeId.value.removePrefix("node_")}"

        // Gather the segments to wrap
        val siblings = SegmentTreeOps.findSiblings(doc.segments, cmd.nodeIds.first()) ?: return null
        val nodeIdSet = cmd.nodeIds.toSet()
        val wrappedSegments = siblings.filter { it.nodeId in nodeIdSet }

        val repeat = EditorSegment.Repeat(
            nodeId = repeatNodeId,
            segmentId = segmentId,
            label = null,
            note = null,
            messages = emptyList(),
            count = 2,
            segments = wrappedSegments,
        )

        val newSegments = SegmentTreeOps.wrapInRepeat(doc.segments, cmd.nodeIds, repeat) ?: return null
        return doc.copy(
            segments = newSegments,
            selectedNodeId = repeatNodeId,
            expandedNodeIds = doc.expandedNodeIds + repeatNodeId,
            nextNodeCounter = counter,
        )
    }

    private fun applyUnwrapRepeat(doc: EditorWorkoutDocument, cmd: UnwrapRepeat): EditorWorkoutDocument? {
        val newSegments = SegmentTreeOps.unwrapRepeat(doc.segments, cmd.nodeId) ?: return null
        val newSelection = if (doc.selectedNodeId == cmd.nodeId) null else doc.selectedNodeId
        return doc.copy(
            segments = newSegments,
            selectedNodeId = newSelection,
            expandedNodeIds = doc.expandedNodeIds - cmd.nodeId,
        )
    }

    // --- Segment properties ---

    private fun applySetSegmentId(doc: EditorWorkoutDocument, cmd: SetSegmentId): EditorWorkoutDocument? {
        val segment = doc.findSegment(cmd.nodeId) ?: return null
        val updated = when (segment) {
            is EditorSegment.Steady -> segment.copy(segmentId = cmd.segmentId)
            is EditorSegment.Ramp -> segment.copy(segmentId = cmd.segmentId)
            is EditorSegment.FreeRide -> segment.copy(segmentId = cmd.segmentId)
            is EditorSegment.Repeat -> segment.copy(segmentId = cmd.segmentId)
        }
        val newSegments = SegmentTreeOps.replace(doc.segments, cmd.nodeId, updated) ?: return null
        return doc.copy(segments = newSegments)
    }

    private fun applySetSegmentLabel(doc: EditorWorkoutDocument, cmd: SetSegmentLabel): EditorWorkoutDocument? {
        val segment = doc.findSegment(cmd.nodeId) ?: return null
        val updated = when (segment) {
            is EditorSegment.Steady -> segment.copy(label = cmd.label)
            is EditorSegment.Ramp -> segment.copy(label = cmd.label)
            is EditorSegment.FreeRide -> segment.copy(label = cmd.label)
            is EditorSegment.Repeat -> segment.copy(label = cmd.label)
        }
        val newSegments = SegmentTreeOps.replace(doc.segments, cmd.nodeId, updated) ?: return null
        return doc.copy(segments = newSegments)
    }

    private fun applySetSegmentNote(doc: EditorWorkoutDocument, cmd: SetSegmentNote): EditorWorkoutDocument? {
        val segment = doc.findSegment(cmd.nodeId) ?: return null
        val updated = when (segment) {
            is EditorSegment.Steady -> segment.copy(note = cmd.note)
            is EditorSegment.Ramp -> segment.copy(note = cmd.note)
            is EditorSegment.FreeRide -> segment.copy(note = cmd.note)
            is EditorSegment.Repeat -> segment.copy(note = cmd.note)
        }
        val newSegments = SegmentTreeOps.replace(doc.segments, cmd.nodeId, updated) ?: return null
        return doc.copy(segments = newSegments)
    }

    private fun applySetRepeatCount(doc: EditorWorkoutDocument, cmd: SetRepeatCount): EditorWorkoutDocument? {
        val segment = doc.findSegment(cmd.nodeId) ?: return null
        if (segment !is EditorSegment.Repeat) return null
        if (cmd.count < 1) return null
        val updated = segment.copy(count = cmd.count)
        val newSegments = SegmentTreeOps.replace(doc.segments, cmd.nodeId, updated) ?: return null
        return doc.copy(segments = newSegments)
    }

    private fun applySetSegmentDuration(doc: EditorWorkoutDocument, cmd: SetSegmentDuration): EditorWorkoutDocument? {
        val segment = doc.findSegment(cmd.nodeId) ?: return null
        val updated = when (segment) {
            is EditorSegment.Steady -> segment.copy(durationSec = cmd.durationSec)
            is EditorSegment.Ramp -> segment.copy(durationSec = cmd.durationSec)
            is EditorSegment.FreeRide -> segment.copy(durationSec = cmd.durationSec)
            is EditorSegment.Repeat -> return null // repeats don't have duration
        }
        val newSegments = SegmentTreeOps.replace(doc.segments, cmd.nodeId, updated) ?: return null
        return doc.copy(segments = newSegments)
    }

    private fun applySetSteadyTarget(doc: EditorWorkoutDocument, cmd: SetSteadyTarget): EditorWorkoutDocument? {
        val segment = doc.findSegment(cmd.nodeId) ?: return null
        if (segment !is EditorSegment.Steady) return null
        val updated = segment.copy(target = cmd.target)
        val newSegments = SegmentTreeOps.replace(doc.segments, cmd.nodeId, updated) ?: return null
        return doc.copy(segments = newSegments)
    }

    private fun applySetRampTargets(doc: EditorWorkoutDocument, cmd: SetRampTargets): EditorWorkoutDocument? {
        val segment = doc.findSegment(cmd.nodeId) ?: return null
        if (segment !is EditorSegment.Ramp) return null
        // HR and HR-relative targets are not allowed in ramps
        if (cmd.fromTarget is EditorTarget.HeartRate || cmd.fromTarget is EditorTarget.HeartRateRelative) return null
        if (cmd.toTarget is EditorTarget.HeartRate || cmd.toTarget is EditorTarget.HeartRateRelative) return null
        val updated = segment.copy(fromTarget = cmd.fromTarget, toTarget = cmd.toTarget)
        val newSegments = SegmentTreeOps.replace(doc.segments, cmd.nodeId, updated) ?: return null
        return doc.copy(segments = newSegments)
    }

    private fun applySetCadence(doc: EditorWorkoutDocument, cmd: SetCadence): EditorWorkoutDocument? {
        val segment = doc.findSegment(cmd.nodeId) ?: return null
        val updated = when (segment) {
            is EditorSegment.Steady -> segment.copy(cadence = cmd.cadence)
            is EditorSegment.Ramp -> segment.copy(cadence = cmd.cadence)
            is EditorSegment.FreeRide -> segment.copy(cadence = cmd.cadence)
            is EditorSegment.Repeat -> return null // repeats don't have cadence
        }
        val newSegments = SegmentTreeOps.replace(doc.segments, cmd.nodeId, updated) ?: return null
        return doc.copy(segments = newSegments)
    }

    // --- Messages ---

    private fun applyAddMessage(doc: EditorWorkoutDocument, cmd: AddMessage): EditorWorkoutDocument? {
        val (msgNodeId, counter) = doc.generateNodeId()
        val message = EditorMessage(
            nodeId = msgNodeId,
            kind = cmd.kind,
            timing = cmd.timing,
            defaultText = cmd.defaultText,
            translations = cmd.translations,
        )
        val newSegments = SegmentTreeOps.addMessage(doc.segments, cmd.parentNodeId, message)
            ?: return null
        return doc.copy(segments = newSegments, nextNodeCounter = counter)
    }

    private fun applyUpdateMessage(doc: EditorWorkoutDocument, cmd: UpdateMessage): EditorWorkoutDocument? {
        val newSegments = SegmentTreeOps.updateMessage(doc.segments, cmd.messageNodeId) { msg ->
            msg.copy(
                kind = cmd.kind ?: msg.kind,
                timing = cmd.timing ?: msg.timing,
                defaultText = cmd.defaultText ?: msg.defaultText,
                translations = cmd.translations ?: msg.translations,
            )
        } ?: return null
        return doc.copy(segments = newSegments)
    }

    private fun applyDeleteMessage(doc: EditorWorkoutDocument, cmd: DeleteMessage): EditorWorkoutDocument? {
        val newSegments = SegmentTreeOps.removeMessage(doc.segments, cmd.messageNodeId) ?: return null
        return doc.copy(segments = newSegments)
    }

    // --- Helpers ---

    private fun createDefaultSegment(
        type: NewSegmentType,
        nodeId: EditorNodeId,
        segmentId: String,
    ): EditorSegment = when (type) {
        NewSegmentType.STEADY -> EditorSegment.Steady(
            nodeId = nodeId,
            segmentId = segmentId,
            label = null,
            note = null,
            messages = emptyList(),
            durationSec = 300,
            target = EditorTarget.FtpPercent(fraction = 0.75),
            cadence = null,
        )
        NewSegmentType.RAMP -> EditorSegment.Ramp(
            nodeId = nodeId,
            segmentId = segmentId,
            label = null,
            note = null,
            messages = emptyList(),
            durationSec = 300,
            fromTarget = EditorTarget.FtpPercent(fraction = 0.45),
            toTarget = EditorTarget.FtpPercent(fraction = 0.65),
            cadence = null,
        )
        NewSegmentType.RAMP_DOWN -> EditorSegment.Ramp(
            nodeId = nodeId,
            segmentId = segmentId,
            label = null,
            note = null,
            messages = emptyList(),
            durationSec = 300,
            fromTarget = EditorTarget.FtpPercent(fraction = 0.75),
            toTarget = EditorTarget.FtpPercent(fraction = 0.45),
            cadence = null,
        )
        NewSegmentType.FREE_RIDE -> EditorSegment.FreeRide(
            nodeId = nodeId,
            segmentId = segmentId,
            label = null,
            note = null,
            messages = emptyList(),
            durationSec = 300,
            cadence = null,
        )
        NewSegmentType.REPEAT -> EditorSegment.Repeat(
            nodeId = nodeId,
            segmentId = segmentId,
            label = null,
            note = null,
            messages = emptyList(),
            count = 2,
            segments = emptyList(),
        )
    }
}

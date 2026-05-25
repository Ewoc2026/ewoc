package com.ewo.editor.commands

import com.ewo.editor.model.EditorCadenceRange
import com.ewo.editor.model.EditorMessageTiming
import com.ewo.editor.model.EditorNodeId
import com.ewo.editor.model.EditorSegment
import com.ewo.editor.model.EditorTarget

/**
 * An editing command that mutates the workout document.
 *
 * Every command is a pure function: applying a command to a document produces
 * a new document. Commands always reference nodes by stable [EditorNodeId],
 * never by list index.
 *
 * Commands create undo entries. View-state changes (selection, expand/collapse)
 * are handled separately through [EditorViewAction].
 */
sealed interface EditorCommand

// --- Workout-level properties ---

data class SetWorkoutTitle(val title: String) : EditorCommand
data class SetWorkoutDescription(val description: String) : EditorCommand
data class SetTitleLocalized(val localized: com.ewo.editor.model.EditorLocalizedText?) : EditorCommand
data class SetDescriptionLocalized(val localized: com.ewo.editor.model.EditorLocalizedText?) : EditorCommand
data class SetDifficulty(val difficulty: String?) : EditorCommand
data class SetTags(val tags: List<String>) : EditorCommand
data class SetVersion(val version: String) : EditorCommand
data class SetWorkoutUid(val uid: String?) : EditorCommand
data class SetWorkoutRevision(val revision: Int?) : EditorCommand

// --- Segment CRUD ---

/**
 * Adds a new segment. If [afterNodeId] is provided, inserts after that sibling.
 * If [parentNodeId] is a repeat node, inserts as a child of that repeat.
 * Otherwise appends to the root segment list.
 */
data class AddSegment(
    val segmentType: NewSegmentType,
    val afterNodeId: EditorNodeId? = null,
    val parentNodeId: EditorNodeId? = null,
) : EditorCommand

enum class NewSegmentType { STEADY, RAMP, RAMP_DOWN, FREE_RIDE, REPEAT }

/** Removes a segment from the tree. Clears selection if the deleted node was selected. */
data class DeleteSegment(val nodeId: EditorNodeId) : EditorCommand

/** Creates a copy of a segment (with new node IDs) and inserts it after the original. */
data class DuplicateSegment(val nodeId: EditorNodeId) : EditorCommand

/** Moves a segment to a new position. [position] is the index within the target parent's child list. */
data class MoveSegment(
    val nodeId: EditorNodeId,
    val targetParentNodeId: EditorNodeId?,
    val position: Int,
) : EditorCommand

// --- Repeat operations ---

/** Wraps one or more consecutive sibling segments in a new repeat block. */
data class WrapInRepeat(val nodeIds: List<EditorNodeId>) : EditorCommand

/** Replaces a repeat node with its children (flattens one level). */
data class UnwrapRepeat(val nodeId: EditorNodeId) : EditorCommand

// --- Segment properties ---

data class SetSegmentId(val nodeId: EditorNodeId, val segmentId: String) : EditorCommand
data class SetSegmentLabel(val nodeId: EditorNodeId, val label: String?) : EditorCommand
data class SetSegmentNote(val nodeId: EditorNodeId, val note: String?) : EditorCommand
data class SetRepeatCount(val nodeId: EditorNodeId, val count: Int) : EditorCommand
data class SetSegmentDuration(val nodeId: EditorNodeId, val durationSec: Int) : EditorCommand
data class SetSteadyTarget(val nodeId: EditorNodeId, val target: EditorTarget) : EditorCommand
data class SetRampTargets(
    val nodeId: EditorNodeId,
    val fromTarget: EditorTarget,
    val toTarget: EditorTarget,
) : EditorCommand
data class SetCadence(val nodeId: EditorNodeId, val cadence: EditorCadenceRange?) : EditorCommand

// --- Messages ---

/**
 * Pastes a deep copy of [segment] into the document after [afterNodeId].
 * New [EditorNodeId] values are assigned by the executor; [segment] node IDs are not reused.
 */
data class PasteSegment(
    val segment: EditorSegment,
    val afterNodeId: EditorNodeId?,
) : EditorCommand

data class AddMessage(
    val parentNodeId: EditorNodeId,
    val kind: String,
    val timing: EditorMessageTiming = EditorMessageTiming(),
    val defaultText: String,
    val translations: Map<String, String> = emptyMap(),
) : EditorCommand

data class UpdateMessage(
    val messageNodeId: EditorNodeId,
    val kind: String? = null,
    val timing: EditorMessageTiming? = null,
    val defaultText: String? = null,
    val translations: Map<String, String>? = null,
) : EditorCommand

data class DeleteMessage(val messageNodeId: EditorNodeId) : EditorCommand

// --- Composite ---

/** Applies multiple commands as a single atomic undo entry. */
data class CompositeCommand(val commands: List<EditorCommand>) : EditorCommand

// --- Control ---

data class SetControl(val control: com.ewo.editor.model.EditorControl?) : EditorCommand

/**
 * View-state actions that modify the document without creating undo entries.
 */
sealed interface EditorViewAction {
    data class Select(val nodeId: EditorNodeId?) : EditorViewAction
    data class ToggleExpanded(val nodeId: EditorNodeId) : EditorViewAction
}

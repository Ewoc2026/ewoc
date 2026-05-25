package com.ewo.editor.model

/**
 * A segment node in the editor document tree.
 *
 * Each segment has a stable [nodeId] for editor operations (selection, commands,
 * validation markers) and a user-editable [segmentId] that corresponds to the
 * canonical `.ewo` `id` field.
 *
 * Values may be temporarily invalid while the user is editing. Validation markers
 * surface issues; canonical export is blocked when structural/semantic errors exist.
 */
sealed class EditorSegment {
    /** Stable editor-internal identity. Never serialized to `.ewo`. */
    abstract val nodeId: EditorNodeId

    /** User-editable canonical segment ID. Must be unique across the workout tree. */
    abstract val segmentId: String

    /** Optional rider-facing segment title preserved by canonical `.ewo` v1.5+. */
    abstract val label: String?

    /** Optional authoring note preserved by canonical `.ewo` v1.5+. */
    abstract val note: String?

    /** Segment-level messages. */
    abstract val messages: List<EditorMessage>

    data class Steady(
        override val nodeId: EditorNodeId,
        override val segmentId: String,
        override val label: String?,
        override val note: String?,
        override val messages: List<EditorMessage>,
        val durationSec: Int,
        val target: EditorTarget?,
        val cadence: EditorCadenceRange?,
    ) : EditorSegment()

    data class Ramp(
        override val nodeId: EditorNodeId,
        override val segmentId: String,
        override val label: String?,
        override val note: String?,
        override val messages: List<EditorMessage>,
        val durationSec: Int,
        val fromTarget: EditorTarget?,
        val toTarget: EditorTarget?,
        val cadence: EditorCadenceRange?,
    ) : EditorSegment()

    data class FreeRide(
        override val nodeId: EditorNodeId,
        override val segmentId: String,
        override val label: String?,
        override val note: String?,
        override val messages: List<EditorMessage>,
        val durationSec: Int,
        val cadence: EditorCadenceRange?,
    ) : EditorSegment()

    data class Repeat(
        override val nodeId: EditorNodeId,
        override val segmentId: String,
        override val label: String?,
        override val note: String?,
        override val messages: List<EditorMessage>,
        val count: Int,
        val segments: List<EditorSegment>,
    ) : EditorSegment()
}

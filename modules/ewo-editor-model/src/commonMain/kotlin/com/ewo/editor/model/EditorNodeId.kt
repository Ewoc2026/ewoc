package com.ewo.editor.model

/**
 * Stable identity for every editable node in the editor document tree.
 *
 * Node IDs are generated internally by the editor and are **never** serialized
 * to canonical `.ewo`. They are separate from the user-editable segment `id` field.
 *
 * Commands, selection, validation markers, and undo/redo all reference nodes
 * through this ID — never through list indices.
 */
@JvmInline
value class EditorNodeId(val value: String)

package com.ewo.editor.model

/**
 * Severity levels for editor validation markers.
 *
 * - [ERROR]: blocks canonical `.ewo` export (structural/semantic issues)
 * - [WARNING]: advisory (sanity issues); never blocks export
 * - [INFO]: informational hints
 */
enum class EditorValidationSeverity { ERROR, WARNING, INFO }

/**
 * A validation issue attached to a specific node and optional field.
 *
 * Markers are recomputed after every command. They are transient editor state —
 * never serialized to canonical `.ewo`.
 */
data class EditorValidationMarker(
    val nodeId: EditorNodeId?,
    val field: String?,
    val severity: EditorValidationSeverity,
    val message: String,
    val code: String?,
) {
    /** True if this marker blocks canonical export. */
    val blocksExport: Boolean get() = severity == EditorValidationSeverity.ERROR
}

package com.ewo.editor.model

/**
 * Immutable undo/redo history using full document snapshots.
 *
 * Workout documents are small (tens of segments at most), so full snapshots are
 * cheap and avoid the complexity of inverse-command patterns.
 *
 * Selection and expanded state are preserved in each snapshot, so undo/redo
 * restores the user's view context along with the content.
 */
data class EditorHistory(
    val past: List<EditorWorkoutDocument>,
    val present: EditorWorkoutDocument,
    val future: List<EditorWorkoutDocument>,
) {
    val canUndo: Boolean get() = past.isNotEmpty()
    val canRedo: Boolean get() = future.isNotEmpty()

    companion object {
        /** Maximum number of undo snapshots retained. */
        const val MAX_UNDO_DEPTH = 100

        /** Creates a new history with an initial document and no undo/redo. */
        fun of(document: EditorWorkoutDocument): EditorHistory =
            EditorHistory(past = emptyList(), present = document, future = emptyList())
    }
}

/**
 * Pushes the current document to history and sets a new present.
 * Clears the redo stack (future edits diverge from undone state).
 * Trims undo stack to [EditorHistory.MAX_UNDO_DEPTH].
 */
fun EditorHistory.push(newPresent: EditorWorkoutDocument): EditorHistory {
    val trimmedPast = (past + present).takeLast(EditorHistory.MAX_UNDO_DEPTH)
    return copy(
        past = trimmedPast,
        present = newPresent,
        future = emptyList(),
    )
}

/**
 * Undoes the last edit. Returns the updated history, or the same history if
 * there is nothing to undo.
 */
fun EditorHistory.undo(): EditorHistory {
    if (past.isEmpty()) return this
    return copy(
        past = past.dropLast(1),
        present = past.last(),
        future = listOf(present) + future,
    )
}

/**
 * Redoes the last undone edit. Returns the updated history, or the same history
 * if there is nothing to redo.
 */
fun EditorHistory.redo(): EditorHistory {
    if (future.isEmpty()) return this
    return copy(
        past = past + present,
        present = future.first(),
        future = future.drop(1),
    )
}

/**
 * Replaces the current present without adding an undo entry.
 * Useful for non-undoable state changes like selection or expand/collapse.
 */
fun EditorHistory.updatePresent(transform: (EditorWorkoutDocument) -> EditorWorkoutDocument): EditorHistory {
    return copy(present = transform(present))
}

package com.ewo.editor.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EditorHistoryTest {

    @Test
    fun newHistoryHasNoUndoRedo() {
        val history = EditorHistory.of(emptyDoc("v1"))
        assertFalse(history.canUndo)
        assertFalse(history.canRedo)
    }

    @Test
    fun pushAddsToHistoryAndClearsFuture() {
        val doc1 = emptyDoc("v1")
        val doc2 = emptyDoc("v2")
        val doc3 = emptyDoc("v3")

        var history = EditorHistory.of(doc1)
        history = history.push(doc2)
        assertTrue(history.canUndo)
        assertFalse(history.canRedo)
        assertEquals("v2", history.present.title)

        // Undo creates future
        history = history.undo()
        assertTrue(history.canRedo)
        assertEquals("v1", history.present.title)

        // Push clears future
        history = history.push(doc3)
        assertFalse(history.canRedo)
        assertEquals("v3", history.present.title)
    }

    @Test
    fun undoRestoresPreviousState() {
        val doc1 = emptyDoc("v1")
        val doc2 = emptyDoc("v2")

        var history = EditorHistory.of(doc1)
        history = history.push(doc2)
        history = history.undo()

        assertEquals("v1", history.present.title)
        assertTrue(history.canRedo)
        assertFalse(history.canUndo)
    }

    @Test
    fun undoOnEmptyHistoryIsNoOp() {
        val history = EditorHistory.of(emptyDoc("v1"))
        val unchanged = history.undo()
        assertEquals(history, unchanged)
    }

    @Test
    fun redoRestoresUndoneState() {
        val doc1 = emptyDoc("v1")
        val doc2 = emptyDoc("v2")

        var history = EditorHistory.of(doc1)
        history = history.push(doc2)
        history = history.undo()
        history = history.redo()

        assertEquals("v2", history.present.title)
        assertFalse(history.canRedo)
        assertTrue(history.canUndo)
    }

    @Test
    fun redoOnEmptyFutureIsNoOp() {
        val history = EditorHistory.of(emptyDoc("v1"))
        val unchanged = history.redo()
        assertEquals(history, unchanged)
    }

    @Test
    fun multipleUndoRedoCycles() {
        var history = EditorHistory.of(emptyDoc("v1"))
        history = history.push(emptyDoc("v2"))
        history = history.push(emptyDoc("v3"))
        history = history.push(emptyDoc("v4"))

        // Undo twice
        history = history.undo()
        assertEquals("v3", history.present.title)
        history = history.undo()
        assertEquals("v2", history.present.title)

        // Redo once
        history = history.redo()
        assertEquals("v3", history.present.title)

        // Push diverges — clears remaining redo
        history = history.push(emptyDoc("v5"))
        assertEquals("v5", history.present.title)
        assertFalse(history.canRedo)
    }

    @Test
    fun historyTrimsToMaxDepth() {
        var history = EditorHistory.of(emptyDoc("v0"))
        for (i in 1..150) {
            history = history.push(emptyDoc("v$i"))
        }

        assertTrue(history.past.size <= EditorHistory.MAX_UNDO_DEPTH)
        assertEquals("v150", history.present.title)
    }

    @Test
    fun updatePresentDoesNotCreateUndoEntry() {
        val doc1 = emptyDoc("v1")
        var history = EditorHistory.of(doc1)

        history = history.updatePresent { it.copy(title = "updated") }

        assertEquals("updated", history.present.title)
        assertFalse(history.canUndo, "updatePresent should not add undo entry")
    }

    private fun emptyDoc(title: String): EditorWorkoutDocument =
        EditorDocumentFactory.empty().copy(title = title)
}

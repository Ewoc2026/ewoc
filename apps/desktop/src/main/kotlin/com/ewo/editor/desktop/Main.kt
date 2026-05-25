package com.ewo.editor.desktop

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.ewo.editor.desktop.state.EditorState

fun main() = application {
    val state = EditorState()

    Window(
        onCloseRequest = ::exitApplication,
        title = "EWO Editor",
        state = rememberWindowState(width = 1200.dp, height = 800.dp),
        onKeyEvent = { handleKeyEvent(it, state) },
    ) {
        MenuBar {
            Menu("File", mnemonic = 'F') {
                Item("New", shortcut = KeyShortcut(Key.N, ctrl = true)) { state.newDocument() }
                Item("Open...", shortcut = KeyShortcut(Key.O, ctrl = true)) { showOpenDialog(state) }
                Item("Save", shortcut = KeyShortcut(Key.S, ctrl = true)) {
                    if (state.currentFile != null) state.saveFile() else showSaveDialog(state)
                }
                Item("Save As...") { showSaveDialog(state) }

                if (state.recentFiles.isNotEmpty()) {
                    Separator()
                    Menu("Recent Files") {
                        state.recentFiles.forEach { file ->
                            Item(file.name) { state.openFile(file) }
                        }
                        Separator()
                        Item("Clear Recent Files") { state.clearRecentFiles() }
                    }
                }

                Separator()
                Item("Exit") { exitApplication() }
            }
            Menu("Edit", mnemonic = 'E') {
                Item("Undo", shortcut = KeyShortcut(Key.Z, ctrl = true), enabled = state.history.canUndo) { state.undo() }
                Item("Redo", shortcut = KeyShortcut(Key.Z, ctrl = true, shift = true), enabled = state.history.canRedo) { state.redo() }
                Separator()
                Item("Copy", shortcut = KeyShortcut(Key.C, ctrl = true), enabled = state.document.selectedNodeId != null) { state.copySelected() }
                Item("Cut", shortcut = KeyShortcut(Key.X, ctrl = true), enabled = state.document.selectedNodeId != null) { state.cutSelected() }
                Item("Paste", shortcut = KeyShortcut(Key.V, ctrl = true), enabled = state.clipboard != null) { state.pasteAtSelected() }
            }
            Menu("View", mnemonic = 'V') {
                Item("Reset Workout Flow Panel Width") { state.resetWorkoutTreePanelWidth() }
                Item("Reset Diagnostics Panel Width") { state.resetDiagnosticsPanelWidth() }
            }
            Menu("Help", mnemonic = 'H') {
                Item("Show Getting Started") { state.reopenOnboardingCard(EditorState.OnboardingPath.FIRST_WORKOUT) }
            }
        }
        EditorApp(state)
    }
}

package com.ewo.editor.desktop.state

import com.ewo.editor.desktop.resolveSaveTarget
import com.ewo.editor.commands.AddSegment
import com.ewo.editor.commands.NewSegmentType
import com.ewo.editor.commands.SetSegmentDuration
import com.ewo.editor.commands.SetTitleLocalized
import com.ewo.editor.commands.SetWorkoutTitle
import com.ewo.editor.commands.SetWorkoutUid
import com.ewo.editor.commands.EditorViewAction
import com.ewo.editor.model.EditorLocalizedText
import com.ewo.editor.model.EditorNodeId
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EditorStateTest {

    private val originalUserHome = System.getProperty("user.home")

    @AfterTest
    fun restoreUserHome() {
        System.setProperty("user.home", originalUserHome)
    }

    @Test
    fun dispatchKeepsLocalizedTitleDefaultInSyncAsSingleUndoEntry() {
        val state = EditorState()
        state.dispatch(
            SetTitleLocalized(
                EditorLocalizedText(
                    defaultText = "",
                    translations = mapOf("fi" to "Kynnysajo"),
                ),
            ),
        )

        state.dispatch(SetWorkoutTitle("Threshold Builder"))

        assertEquals("Threshold Builder", state.document.title)
        assertEquals("Threshold Builder", state.document.titleLocalized?.defaultText)
        assertTrue(state.history.canUndo)

        state.undo()

        assertEquals("", state.document.title)
        assertEquals("", state.document.titleLocalized?.defaultText)
    }

    @Test
    fun copyAndPasteSelectedSegmentCreatesDistinctClone() {
        val state = EditorState()
        state.dispatch(AddSegment(NewSegmentType.STEADY))
        val original = assertIs<com.ewo.editor.model.EditorSegment.Steady>(
            state.document.segments.single(),
        )
        state.dispatchView(EditorViewAction.Select(original.nodeId))

        state.copySelected()
        state.pasteAtSelected()

        assertNotNull(state.clipboard)
        assertEquals(2, state.document.segments.size)
        val pasted = assertIs<com.ewo.editor.model.EditorSegment.Steady>(
            state.document.segments.last(),
        )
        assertEquals(original.durationSec, pasted.durationSec)
        assertNotEquals(original.nodeId, pasted.nodeId)
        assertNotEquals(original.segmentId, pasted.segmentId)
        assertEquals(pasted.nodeId, state.document.selectedNodeId)
    }

    @Test
    fun undoAndRedoRecomputePreviewAfterSegmentEdit() {
        val state = EditorState()
        state.ftpWatts = 200
        state.dispatch(AddSegment(NewSegmentType.STEADY))
        val segmentNodeId = state.document.segments.single().nodeId

        state.dispatch(SetWorkoutTitle("Previewable workout"))
        state.dispatch(SetSegmentDuration(segmentNodeId, 600))
        assertEquals(600, state.preview.totalDurationSec)

        state.undo()
        assertEquals(300, state.preview.totalDurationSec)

        state.redo()
        assertEquals(600, state.preview.totalDurationSec)
    }

    @Test
    fun saveFileMarksDocumentCleanAndOpenFileRestoresIt() {
        val tempHome = Files.createTempDirectory("editor-state-home").toFile()
        System.setProperty("user.home", tempHome.absolutePath)

        val state = EditorState()
        state.ftpWatts = 200
        state.dispatch(AddSegment(NewSegmentType.STEADY))
        state.dispatch(SetWorkoutTitle("Saved workout"))
        state.dispatch(SetWorkoutUid("saved-uid"))
        val file = File(tempHome, "saved-workout.ewo.json")

        state.saveFile(file)

        assertEquals(file, state.currentFile)
        assertEquals("Saved: ${file.name}", state.statusMessage)
        assertFalse(state.document.isDirty)
        assertTrue(file.readText().contains("\"title\": \"Saved workout\""))

        state.newDocument()
        assertNull(state.currentFile)

        val opened = state.openFile(file)

        assertTrue(opened)
        assertEquals(file, state.currentFile)
        assertEquals("Saved workout", state.document.title)
        assertEquals("saved-uid", state.document.uid)
        assertEquals("Opened: ${file.name}", state.statusMessage)
        assertTrue(state.recentFiles.any { it.absolutePath == file.absolutePath })
        assertEquals(300, state.preview.totalDurationSec)
    }

    @Test
    fun resolveSaveTargetAddsExtensionWithoutOverwritePromptForNewPath() {
        val tempHome = Files.createTempDirectory("editor-save-target-home").toFile()
        val selectedFile = File(tempHome, "new-workout")

        val target = resolveSaveTarget(
            selectedFile = selectedFile,
            currentFile = null,
        )

        assertEquals(File(tempHome, "new-workout.ewo"), target.file)
        assertFalse(target.requiresOverwriteConfirmation)
    }

    @Test
    fun resolveSaveTargetRequestsOverwritePromptForDifferentExistingFile() {
        val tempHome = Files.createTempDirectory("editor-save-target-home").toFile()
        val existingFile = File(tempHome, "existing-workout.ewo").apply {
            writeText("existing")
        }
        val currentFile = File(tempHome, "current-workout.ewo").apply {
            writeText("current")
        }

        val target = resolveSaveTarget(
            selectedFile = existingFile,
            currentFile = currentFile,
        )

        assertEquals(existingFile, target.file)
        assertTrue(target.requiresOverwriteConfirmation)
    }

    @Test
    fun resolveSaveTargetSkipsOverwritePromptWhenSaveAsTargetsCurrentFile() {
        val tempHome = Files.createTempDirectory("editor-save-target-home").toFile()
        val currentFile = File(tempHome, "same-workout.ewo").apply {
            writeText("existing")
        }

        val target = resolveSaveTarget(
            selectedFile = File(tempHome, "./same-workout.ewo"),
            currentFile = currentFile,
        )

        assertEquals(currentFile.absoluteFile, target.file.absoluteFile)
        assertFalse(target.requiresOverwriteConfirmation)
    }

    @Test
    fun openFileWithMissingCompileContextKeepsDocumentAndReportsPreviewErrors() {
        val tempHome = Files.createTempDirectory("editor-state-home").toFile()
        System.setProperty("user.home", tempHome.absolutePath)
        val file = File(tempHome, "hr-relative.ewo.json")
        file.writeText(HR_RELATIVE_WORKOUT)

        val state = EditorState()
        val opened = state.openFile(file)

        assertTrue(opened)
        assertEquals(file, state.currentFile)
        assertTrue(state.statusMessage.contains("some targets need preview rider profile values"))
        assertTrue(state.preview.compileErrors.any { it.contains("hr_max") || it.contains("preview", ignoreCase = true) || it.contains("athlete profile", ignoreCase = true) })
        assertTrue(state.preview.totalDurationSec > 0)
        assertTrue(state.preview.chartBars.isNotEmpty())
        assertEquals(EditorNodeId("node_2"), state.document.segments.single().nodeId)
    }

    @Test
    fun openFileDropsInvalidRootTagsInsteadOfFailing() {
        val tempHome = Files.createTempDirectory("editor-state-home").toFile()
        System.setProperty("user.home", tempHome.absolutePath)
        val file = File(tempHome, "invalid-tags.ewo.json")
        file.writeText(
            """
            {
              "format": "ewo",
              "version": "1.3",
              "title": "Opened with cleaned tags",
              "tags": ["endurance", "maki", "tempo", "bad tag"],
              "segments": [
                {
                  "id": "steady",
                  "type": "steady",
                  "duration_sec": 300,
                  "target": {
                    "metric": "power",
                    "value": 180
                  }
                }
              ]
            }
            """.trimIndent(),
        )

        val state = EditorState()
        val opened = state.openFile(file)

        assertTrue(opened)
        assertEquals(listOf("endurance", "maki", "tempo"), state.document.tags)
        assertEquals("Opened: ${file.name}", state.statusMessage)
    }

    @Test
    fun openFileWithMissingFtpKeepsDocumentOpenAndReportsPreviewErrors() {
        val tempHome = Files.createTempDirectory("editor-state-home").toFile()
        System.setProperty("user.home", tempHome.absolutePath)
        val file = File(tempHome, "ftp-preview.ewo")
        file.writeText(FTP_PERCENT_WORKOUT)

        val state = EditorState()
        val opened = state.openFile(file)

        assertTrue(opened)
        assertEquals(file, state.currentFile)
        assertTrue(state.statusMessage.contains("some targets need preview rider profile values"))
        assertTrue(state.preview.compileErrors.any { it.contains("FTP is not configured") })
    }

    @Test
    fun diagnosticsReportSummarizesLatestOpenOutcome() {
        val tempHome = Files.createTempDirectory("editor-state-home").toFile()
        System.setProperty("user.home", tempHome.absolutePath)
        val file = File(tempHome, "ftp-preview.ewo")
        file.writeText(FTP_PERCENT_WORKOUT)

        val state = EditorState()
        state.openFile(file)

        val report = state.diagnosticsReport()
        assertTrue(report.contains("Operation: Open file"))
        assertTrue(report.contains("Outcome: needs_compile_context"))
        assertTrue(report.contains("FTP watts: unset"))
        assertTrue(report.contains("Compile errors: 1"))
    }

    @Test
    fun missingFtpPreviewErrorsClearAfterFtpIsProvided() {
        val tempHome = Files.createTempDirectory("editor-state-home").toFile()
        System.setProperty("user.home", tempHome.absolutePath)
        val file = File(tempHome, "ftp-preview.ewo")
        file.writeText(FTP_PERCENT_WORKOUT)

        val state = EditorState()
        assertTrue(state.openFile(file))
        assertTrue(state.preview.compileErrors.any { it.contains("FTP is not configured") })

        state.updatePreviewFtpWatts(220)

        assertTrue(state.preview.compileErrors.isEmpty())
        assertTrue(state.preview.chartPowerUnit.name == "WATTS")
        assertTrue(state.statusMessage.contains("Preview updated"))
    }

    @Test
    fun remembersLastFileChooserDirectoryAcrossStateInstances() {
        val tempHome = Files.createTempDirectory("editor-state-home").toFile()
        System.setProperty("user.home", tempHome.absolutePath)
        val selectedDir = File(tempHome, "fixtures").apply { mkdirs() }

        val firstState = EditorState()
        firstState.rememberFileChooserDirectory(selectedDir)

        val secondState = EditorState()
        assertEquals(selectedDir.absolutePath, secondState.initialFileChooserDirectory()?.absolutePath)
    }

    @Test
    fun onboardingDismissalPersistsAcrossStateInstancesAndCanBeReopened() {
        val tempHome = Files.createTempDirectory("editor-state-home").toFile()
        System.setProperty("user.home", tempHome.absolutePath)

        val firstState = EditorState()
        assertFalse(firstState.onboardingDismissed)
        assertEquals(EditorState.OnboardingPath.FIRST_WORKOUT, firstState.activeOnboardingPath)

        firstState.dismissOnboardingCard()
        assertTrue(firstState.onboardingDismissed)
        assertNull(firstState.activeOnboardingPath)

        val secondState = EditorState()
        assertTrue(secondState.onboardingDismissed)
        assertNull(secondState.activeOnboardingPath)

        secondState.reopenOnboardingCard()
        assertFalse(secondState.onboardingDismissed)
        assertEquals(EditorState.OnboardingPath.FIRST_WORKOUT, secondState.activeOnboardingPath)

        val thirdState = EditorState()
        assertFalse(thirdState.onboardingDismissed)
        assertEquals(EditorState.OnboardingPath.FIRST_WORKOUT, thirdState.activeOnboardingPath)
    }

    @Test
    fun onboardingGuideMatchesChosenPath() {
        val state = EditorState()

        val firstWorkoutGuide = state.onboardingGuide(EditorState.OnboardingPath.FIRST_WORKOUT)
        val hrGuide = state.onboardingGuide(EditorState.OnboardingPath.EXPLORE_HR)

        assertEquals("First workout", firstWorkoutGuide.title)
        assertTrue(firstWorkoutGuide.summary.contains("simple"))
        assertEquals("Explore HR-based workouts", hrGuide.title)
        assertTrue(hrGuide.summary.contains("preview", ignoreCase = true) || hrGuide.sections.flatMap { it.items }.any { it.contains("preview rider profile", ignoreCase = true) })
    }

    @Test
    fun showAndCloseOnboardingGuideUpdateActivePath() {
        val state = EditorState()

        state.showOnboardingPath(EditorState.OnboardingPath.EXPLORE_HR)
        assertEquals(EditorState.OnboardingPath.EXPLORE_HR, state.activeOnboardingPath)
        assertFalse(state.onboardingDismissed)

        state.closeOnboardingGuide()
        assertNull(state.activeOnboardingPath)
    }

    @Test
    fun diagnosticsPanelWidthPersistsAcrossStateInstancesAndClampsToLimits() {
        val tempHome = Files.createTempDirectory("editor-state-home").toFile()
        System.setProperty("user.home", tempHome.absolutePath)

        val firstState = EditorState()
        assertEquals(320f, firstState.diagnosticsPanelWidthDp)

        firstState.updateDiagnosticsPanelWidthDp(999f)
        assertEquals(420f, firstState.diagnosticsPanelWidthDp)

        val secondState = EditorState()
        assertEquals(420f, secondState.diagnosticsPanelWidthDp)

        secondState.updateDiagnosticsPanelWidthDp(100f)
        assertEquals(280f, secondState.diagnosticsPanelWidthDp)

        val thirdState = EditorState()
        assertEquals(280f, thirdState.diagnosticsPanelWidthDp)
    }

    @Test
    fun workoutTreePanelWidthPersistsAcrossStateInstancesAndClampsToLimits() {
        val tempHome = Files.createTempDirectory("editor-state-home").toFile()
        System.setProperty("user.home", tempHome.absolutePath)

        val firstState = EditorState()
        assertEquals(240f, firstState.workoutTreePanelWidthDp)

        firstState.updateWorkoutTreePanelWidthDp(999f)
        assertEquals(340f, firstState.workoutTreePanelWidthDp)

        val secondState = EditorState()
        assertEquals(340f, secondState.workoutTreePanelWidthDp)

        secondState.updateWorkoutTreePanelWidthDp(100f)
        assertEquals(220f, secondState.workoutTreePanelWidthDp)

        val thirdState = EditorState()
        assertEquals(220f, thirdState.workoutTreePanelWidthDp)
    }

    companion object {
        private val FTP_PERCENT_WORKOUT = """
            {
              "format": "ewo",
              "version": "1.5",
              "title": "FTP Preview Required",
              "description": "Needs FTP to resolve preview targets.",
              "difficulty": "easy",
              "tags": ["preview", "ftp_percent"],
              "segments": [
                {
                  "id": "tempo",
                  "label": "Steady FTP Step",
                  "type": "steady",
                  "duration_sec": 300,
                  "target": {
                    "metric": "ftp_percent",
                    "value": 0.75
                  }
                }
              ]
            }
        """.trimIndent()

        private val HR_RELATIVE_WORKOUT = """
            {
              "format": "ewo",
              "version": "1.4",
              "title": "HR Relative",
              "control": {
                "initial_power_watts": 110,
                "min_power_watts": 90,
                "max_power_watts": 220,
                "signal_loss_power_watts": 100,
                "hr_upper_cap_bpm": 160
              },
              "segments": [
                {
                  "id": "tempo",
                  "type": "steady",
                  "duration_sec": 480,
                  "target": {
                    "metric": "heart_rate_relative",
                    "reference": "hr_max",
                    "range": {
                      "low": 0.72,
                      "high": 0.8
                    }
                  }
                }
              ]
            }
        """.trimIndent()
    }
}

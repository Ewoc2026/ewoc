package io.github.ewoc2026.ewoc

import io.github.ewoc2026.ewoc.workout.ImportedErgoWorkout
import io.github.ewoc2026.ewoc.workout.WorkoutFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutSelectionUiStateTest {

    @Test
    fun appUiStateForwardersReuseSharedWorkoutSelectionOwner() {
        val uiState = AppUiState()
        val selectionState = uiState.workoutSelectionUiState
        val workout = WorkoutFile(
            name = "Threshold",
            description = null,
            author = null,
            tags = emptyList(),
            steps = emptyList(),
        )
        val importedWorkout = ImportedErgoWorkout(
            title = "Threshold",
            description = null,
            steps = emptyList(),
            totalDurationSec = 0,
        )

        assertSame(selectionState.selectedWorkoutState, uiState.selectedWorkout)
        assertSame(selectionState.selectedImportedWorkoutState, uiState.selectedImportedWorkout)
        assertSame(selectionState.selectedWorkoutFileNameState, uiState.selectedWorkoutFileName)
        assertSame(selectionState.selectedWorkoutStepCountState, uiState.selectedWorkoutStepCount)
        assertSame(selectionState.selectedWorkoutPlannedTssState, uiState.selectedWorkoutPlannedTss)
        assertSame(selectionState.selectedWorkoutImportErrorState, uiState.selectedWorkoutImportError)
        assertSame(selectionState.executionModeMessageState, uiState.workoutExecutionModeMessage)
        assertSame(selectionState.executionModeIsErrorState, uiState.workoutExecutionModeIsError)

        selectionState.selectedWorkoutState.value = workout
        selectionState.selectedImportedWorkoutState.value = importedWorkout
        selectionState.selectedWorkoutFileNameState.value = "threshold.ewo"
        selectionState.selectedWorkoutStepCountState.value = 8
        selectionState.selectedWorkoutPlannedTssState.value = 42.5
        selectionState.selectedWorkoutImportErrorState.value = "parse failed"
        selectionState.executionModeMessageState.value = "Legacy fallback active"
        selectionState.executionModeIsErrorState.value = true

        assertSame(workout, uiState.selectedWorkout.value)
        assertSame(importedWorkout, uiState.selectedImportedWorkout.value)
        assertEquals("threshold.ewo", uiState.selectedWorkoutFileName.value)
        assertEquals(8, uiState.selectedWorkoutStepCount.value)
        assertEquals(42.5, uiState.selectedWorkoutPlannedTss.value)
        assertEquals("parse failed", uiState.selectedWorkoutImportError.value)
        assertEquals("Legacy fallback active", uiState.workoutExecutionModeMessage.value)
        assertTrue(uiState.workoutExecutionModeIsError.value)

        uiState.selectedWorkout.value = null
        uiState.selectedImportedWorkout.value = null
        uiState.selectedWorkoutFileName.value = null
        uiState.selectedWorkoutStepCount.value = null
        uiState.selectedWorkoutPlannedTss.value = null
        uiState.selectedWorkoutImportError.value = null
        uiState.workoutExecutionModeMessage.value = null
        uiState.workoutExecutionModeIsError.value = false

        assertNull(selectionState.selectedWorkoutState.value)
        assertNull(selectionState.selectedImportedWorkoutState.value)
        assertNull(selectionState.selectedWorkoutFileNameState.value)
        assertNull(selectionState.selectedWorkoutStepCountState.value)
        assertNull(selectionState.selectedWorkoutPlannedTssState.value)
        assertNull(selectionState.selectedWorkoutImportErrorState.value)
        assertNull(selectionState.executionModeMessageState.value)
        assertFalse(selectionState.executionModeIsErrorState.value)
    }
}

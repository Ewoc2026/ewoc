package com.example.ergometerapp

import androidx.compose.runtime.mutableStateOf
import com.example.ergometerapp.workout.ImportedErgoWorkout
import com.example.ergometerapp.workout.WorkoutFile

/**
 * Owns the selected workout payload plus the import/execution banner state that changes with it.
 *
 * Keeping the raw selection, derived metadata, import failure, and execution-mode banner together
 * prevents `AppUiState` consumers from reintroducing another loose workout-selection cluster while
 * preserving the existing observable state shape used by session orchestration, AI context, and UI.
 */
internal class WorkoutSelectionUiState {
    val selectedSessionSetupModeState = mutableStateOf(SessionSetupMode.FILE)
    val selectedWorkoutState = mutableStateOf<WorkoutFile?>(null)
    val selectedImportedWorkoutState = mutableStateOf<ImportedErgoWorkout?>(null)
    val selectedWorkoutFileNameState = mutableStateOf<String?>(null)
    val selectedWorkoutStepCountState = mutableStateOf<Int?>(null)
    val selectedWorkoutPlannedTssState = mutableStateOf<Double?>(null)
    val selectedWorkoutTotalDurationSecState = mutableStateOf<Int?>(null)
    val selectedWorkoutImportErrorState = mutableStateOf<String?>(null)
    val executionModeMessageState = mutableStateOf<String?>(null)
    val executionModeIsErrorState = mutableStateOf(false)
}

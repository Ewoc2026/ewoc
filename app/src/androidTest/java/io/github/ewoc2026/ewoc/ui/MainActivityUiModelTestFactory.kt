package io.github.ewoc2026.ewoc.ui

import com.ewo.editor.model.EditorDocumentFactory
import com.ewo.editor.model.EditorPreview
import io.github.ewoc2026.ewoc.AppScreen
import io.github.ewoc2026.ewoc.HrProfileSex
import io.github.ewoc2026.ewoc.SessionSetupMode
import io.github.ewoc2026.ewoc.SessionLifecycleState
import io.github.ewoc2026.ewoc.ewoeditor.EwoEditorSnapshot
import io.github.ewoc2026.ewoc.session.SessionPhase
import io.github.ewoc2026.ewoc.session.SessionSummary
import io.github.ewoc2026.ewoc.workout.runner.RunnerState

/**
 * Shared instrumentation-test fixtures for [MainActivityUiModel].
 *
 * Centralizing these defaults keeps UI-model drift repairs localized when
 * the production model gains or moves fields.
 */
internal object MainActivityUiModelTestFactory {
    fun base(
        screen: AppScreen,
        summary: SessionSummary? = null,
    ): MainActivityUiModel {
        return MainActivityUiModel(
            screen = screen,
            bikeData = null,
            heartRate = null,
            logicalDistanceMeters = null,
            logicalTotalEnergyKcal = null,
            phase = SessionPhase.RUNNING,
            sessionLifecycleState = SessionLifecycleState.IDLE,
            ftmsReady = true,
            ftmsControlGranted = true,
            postWorkoutFreerideModeActive = false,
            lastTargetPower = null,
            runnerState = RunnerState.stopped(workoutElapsedSec = 0),
            sessionDurationSeconds = 0,
            summary = summary,
            connectingTimeoutMessage = null,
            menuState = menuState(),
            ewoEditorSnapshot = ewoEditorSnapshot(),
            summaryFitExportStatusMessage = null,
            summaryFitExportStatusIsError = false,
        )
    }

    fun menuState(): MenuUiState {
        return MenuUiState(
            selectedSessionSetupMode = SessionSetupMode.FILE,
            selectedWorkoutFileName = null,
            selectedWorkoutStepCount = null,
            selectedWorkoutPlannedTss = null,
            selectedWorkoutTotalDurationSec = null,
            selectedWorkoutImportError = null,
            selectedWorkout = null,
            selectedImportedWorkout = null,
            ftpWatts = 250,
            ftpInputText = "250",
            ftpInputError = null,
            hrProfileAge = 35,
            hrProfileAgeInput = "35",
            hrProfileAgeError = null,
            hrProfileSex = HrProfileSex.MALE,
            ftmsDeviceName = "Trainer",
            ftmsSelected = true,
            ftmsConnected = true,
            ftmsConnectionKnown = true,
            hrDeviceName = "HR",
            hrSelected = true,
            hrConnected = true,
            hrConnectionKnown = true,
            workoutExecutionModeMessage = null,
            workoutExecutionModeIsError = false,
            aiAssistantMessage = null,
            aiAssistantIsError = false,
            aiAssistantTemplateKey = null,
            connectionIssueMessage = null,
            suggestTrainerSearchAfterConnectionIssue = false,
            suggestOpenSettingsAfterConnectionIssue = false,
            activeDeviceSelectionKind = null,
            scannedDevices = emptyList(),
            deviceScanInProgress = false,
            deviceScanStatus = null,
            deviceScanStopEnabled = true,
            startEnabled = false,
            showMockTrainerControls = false,
            mockTrainerModeEnabled = false,
            fitExportPreference = null,
            compatibilityCheckInProgress = false,
            compatibilityCheckStatusMessage = null,
            documentsFolderReady = false,
            documentsFolderAccessLost = false,
            documentsFolderSummary = null,
            documentsFolderStatusMessage = null,
            documentsFolderStatusIsError = false,
            documentsFolderWorkoutFiles = emptyList(),
            builtInWorkoutFiles = emptyList(),
        )
    }

    private fun ewoEditorSnapshot(): EwoEditorSnapshot {
        return EwoEditorSnapshot(
            document = EditorDocumentFactory.empty(),
            preview = EditorPreview(
                steps = emptyList(),
                totalDurationSec = 0,
                intensityFactor = null,
                tss = null,
                sanityWarnings = emptyList(),
                compileErrors = emptyList(),
            ),
            canUndo = false,
            canRedo = false,
            statusMessage = "Ready",
            currentFileName = null,
            openedFromBundledLibrary = false,
            ftpWatts = null,
            hrMaxBpm = null,
            restingHrBpm = null,
            lthrBpm = null,
            hasClipboard = false,
        )
    }
}

internal fun MainActivityUiModel.withMenuState(
    transform: MenuUiState.() -> MenuUiState,
): MainActivityUiModel = copy(menuState = menuState.transform())

package com.example.ergometerapp.ui

import androidx.compose.runtime.Composable
import com.example.ergometerapp.HrProfileSex
import com.example.ergometerapp.SessionSetupMode
import com.example.ergometerapp.ftms.IndoorBikeData
import com.example.ergometerapp.session.SessionPhase
import com.example.ergometerapp.workout.WorkoutFile
import com.example.ergometerapp.workout.runner.RunnerState
import com.example.ergometerapp.workout.runner.RunnerSegmentType
import com.example.ergometerapp.workout.ImportedErgoWorkoutStep

private val PreviewLongSessionWorkout = previewWorkout().copy(
    name = "Threshold Builder With A Very Long Workout Name For Layout Audit",
    description = "Long workout descriptions should stay readable enough to preserve context in the top rail.",
)

@Composable
private fun SessionScreenPreviewContent(
    phase: SessionPhase = SessionPhase.RUNNING,
    bikeData: IndoorBikeData? = previewIndoorBikeData(),
    heartRate: Int? = 154,
    ftmsReady: Boolean = true,
    ftmsControlGranted: Boolean = true,
    postWorkoutFreerideModeActive: Boolean = false,
    selectedWorkout: WorkoutFile? = previewWorkout(),
    selectedImportedWorkout: com.example.ergometerapp.workout.ImportedErgoWorkout? = null,
    selectedWorkoutFileName: String? = "sweet-spot-builder.zwo",
    selectedSessionSetupMode: SessionSetupMode = SessionSetupMode.FILE,
    runnerState: RunnerState = previewRunnerState(),
    lastTargetPower: Int? = 225,
    workoutExecutionModeMessage: String? = null,
    workoutExecutionModeIsError: Boolean = false,
    aiAssistantMessage: String? = "Hold steady effort and keep breathing relaxed.",
    aiAssistantIsError: Boolean = false,
) {
    ScreenPreviewTheme {
        SessionScreen(
            phase = phase,
            bikeData = bikeData,
            heartRate = heartRate,
            logicalDistanceMeters = bikeData?.totalDistanceMeters,
            logicalTotalEnergyKcal = bikeData?.totalEnergyKcal,
            ftmsReady = ftmsReady,
            ftmsControlGranted = ftmsControlGranted,
            postWorkoutFreerideModeActive = postWorkoutFreerideModeActive,
            selectedWorkout = selectedWorkout,
            selectedImportedWorkout = selectedImportedWorkout,
            selectedWorkoutFileName = selectedWorkoutFileName,
            selectedSessionSetupMode = selectedSessionSetupMode,
            ftpWatts = 250,
            runnerState = runnerState,
            hrProfileAge = 34,
            hrProfileSex = HrProfileSex.MALE,
            lastTargetPower = lastTargetPower,
            workoutExecutionModeMessage = workoutExecutionModeMessage,
            workoutExecutionModeIsError = workoutExecutionModeIsError,
            aiAssistantMessage = aiAssistantMessage,
            aiAssistantIsError = aiAssistantIsError,
            mockTrainerModeEnabled = false,
            onEndSession = {},
            onWorkoutCompletePresented = {},
            onContinueRideAfterWorkoutComplete = {},
            onEndSessionAfterWorkoutComplete = {},
        )
    }
}

@DestinationScreenPreviews
@Composable
private fun SessionScreenPreview() {
    SessionScreenPreviewContent()
}

@DestinationScreenPreviews
@Composable
private fun SessionScreenControlRecoveryPreview() {
    SessionScreenPreviewContent(
        ftmsControlGranted = false,
        workoutExecutionModeMessage = "Waiting for trainer control before sending workout targets.",
        workoutExecutionModeIsError = true,
        aiAssistantMessage = "Stay seated while the trainer reconnects.",
        aiAssistantIsError = false,
    )
}

@DestinationScreenPreviews
@Composable
private fun SessionScreenPausedPreview() {
    SessionScreenPreviewContent(
        runnerState = previewRunnerState().copy(
            paused = true,
            label = "Recovery",
            stepRemainingSec = 75,
        ),
        aiAssistantMessage = "Breathing is back under control. Resume when cadence feels smooth.",
    )
}

@DestinationScreenPreviews
@Composable
private fun SessionScreenCompletePreview() {
    SessionScreenPreviewContent(
        phase = SessionPhase.STOPPED,
        bikeData = previewIndoorBikeData().copy(
            elapsedTimeSeconds = 1_740,
            remainingTimeSeconds = 0,
        ),
        heartRate = 146,
        runnerState = RunnerState.stopped(workoutElapsedSec = 1_740),
        lastTargetPower = null,
        aiAssistantMessage = "Workout complete. End the session to review your summary.",
    )
}

@DestinationScreenPreviews
@Composable
private fun SessionScreenLongTitlePreview() {
    SessionScreenPreviewContent(
        selectedWorkout = PreviewLongSessionWorkout,
        selectedWorkoutFileName = "threshold-builder-layout-audit-version-2026.zwo",
    )
}

@TabletLandscapeAuditPreviews
@Composable
private fun SessionScreenTabletLandscapeAuditPreview() {
    SessionScreenPreviewContent(
        selectedWorkout = PreviewLongSessionWorkout,
        selectedWorkoutFileName = "threshold-builder-layout-audit-version-2026.zwo",
        aiAssistantMessage = "Keep pressure smooth and use the chart as the primary pacing reference.",
    )
}

@DestinationScreenPreviews
@Composable
private fun SessionScreenWaitingStartImportedPreview() {
    SessionScreenPreviewContent(
        bikeData = previewIndoorBikeData().copy(
            instantaneousSpeedKmh = 0.0,
            instantaneousCadenceRpm = 0.0,
            instantaneousPowerW = 0,
        ),
        selectedWorkout = null,
        selectedImportedWorkout = previewImportedErgoWorkout(),
        selectedWorkoutFileName = "imported-tempo-builder.ewo",
        runnerState = previewRunnerState().copy(
            label = "Warmup",
            targetPowerWatts = 170,
            workoutElapsedSec = 0,
            stepRemainingSec = 300,
        ),
        aiAssistantMessage = "Start pedaling to begin the imported workout.",
    )
}

@DestinationScreenPreviews
@Composable
private fun SessionScreenImportedHrControlPreview() {
    val importedWorkout = previewImportedErgoWorkout().copy(
        title = "Imported HR Tempo Builder",
        steps = listOf(
            ImportedErgoWorkoutStep.PowerRamp(
                stepIndex = 0,
                startOffsetSec = 0,
                durationSec = 120,
                fromWatts = 150,
                toWatts = 190,
            ),
            ImportedErgoWorkoutStep.HeartRateSteady(
                stepIndex = 1,
                startOffsetSec = 120,
                durationSec = 240,
                lowBpm = 145,
                highBpm = 155,
                initialPowerWatts = 210,
                minPowerWatts = 170,
                maxPowerWatts = 260,
                signalLossPowerWatts = 180,
            ),
        ),
        totalDurationSec = 360,
    )
    SessionScreenPreviewContent(
        heartRate = 152,
        selectedWorkout = null,
        selectedImportedWorkout = importedWorkout,
        selectedWorkoutFileName = "imported-hr-tempo-builder.ewo",
        runnerState = previewRunnerState().copy(
            label = "HR Steady",
            targetPowerWatts = 214,
            workoutElapsedSec = 180,
            stepRemainingSec = 180,
            segmentType = RunnerSegmentType.HEART_RATE_STEADY,
            sourceStepIndex = 1,
        ),
        aiAssistantMessage = "Breathe steady and let the trainer keep you inside the HR band.",
    )
}

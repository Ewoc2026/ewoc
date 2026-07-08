package io.github.ewoc2026.ewoc.ui

import io.github.ewoc2026.ewoc.BuiltInWorkoutOption
import io.github.ewoc2026.ewoc.DocumentsFolderWorkoutOption
import io.github.ewoc2026.ewoc.DeviceSelectionKind
import io.github.ewoc2026.ewoc.FitExportPreference
import io.github.ewoc2026.ewoc.HrProfileSex
import io.github.ewoc2026.ewoc.ScannedBleDevice
import io.github.ewoc2026.ewoc.SessionSetupMode
import io.github.ewoc2026.ewoc.workout.ImportedErgoWorkout
import io.github.ewoc2026.ewoc.workout.WorkoutFile

/**
 * Top-level immutable state for the entire [MenuScreen].
 *
 * Replaces 48+ individual state parameters with a single typed object.
 * Callbacks remain as separate lambda parameters on the composable.
 */
internal data class MenuUiState(
    val activeSetupStep: MenuSetupStep? = null,
    val selectedSessionSetupMode: SessionSetupMode,
    // Workout selection
    val selectedWorkoutFileName: String?,
    val selectedWorkoutStepCount: Int?,
    val selectedWorkoutPlannedTss: Double?,
    val selectedWorkoutTotalDurationSec: Int?,
    val selectedWorkoutImportError: String?,
    val selectedWorkout: WorkoutFile?,
    val selectedImportedWorkout: ImportedErgoWorkout?,
    // Profile
    val ftpWatts: Int,
    val ftpInputText: String,
    val ftpInputError: String?,
    val hrProfileAge: Int?,
    val hrProfileAgeInput: String,
    val hrProfileAgeError: String?,
    val hrProfileSex: HrProfileSex?,
    // Devices
    val ftmsDeviceName: String,
    val ftmsSelected: Boolean,
    val ftmsConnected: Boolean,
    val ftmsConnectionKnown: Boolean,
    val hrDeviceName: String,
    val hrSelected: Boolean,
    val hrConnected: Boolean,
    val hrConnectionKnown: Boolean,
    // Workout execution
    val workoutExecutionModeMessage: String?,
    val workoutExecutionModeIsError: Boolean,
    // AI assistant
    val aiAssistantMessage: String?,
    val aiAssistantIsError: Boolean,
    val aiAssistantTemplateKey: String?,
    // Connection issue
    val connectionIssueMessage: String?,
    val suggestTrainerSearchAfterConnectionIssue: Boolean,
    val suggestOpenSettingsAfterConnectionIssue: Boolean,
    // Device scan
    val activeDeviceSelectionKind: DeviceSelectionKind?,
    val scannedDevices: List<ScannedBleDevice>,
    val deviceScanInProgress: Boolean,
    val deviceScanStatus: String?,
    val deviceScanStopEnabled: Boolean,
    // Session
    val startEnabled: Boolean,
    val showMockTrainerControls: Boolean,
    val mockTrainerModeEnabled: Boolean,
    // FIT export
    val fitExportPreference: FitExportPreference?,
    // Compatibility
    val compatibilityCheckInProgress: Boolean,
    val compatibilityCheckStatusMessage: String?,
    // Documents folder
    val documentsFolderReady: Boolean,
    val documentsFolderAccessLost: Boolean,
    val documentsFolderSummary: String?,
    val documentsFolderStatusMessage: String?,
    val documentsFolderStatusIsError: Boolean,
    val documentsFolderWorkoutFiles: List<DocumentsFolderWorkoutOption>,
    val builtInWorkoutFiles: List<BuiltInWorkoutOption>,
    // Baseline fitness test metadata for profile affordance
    val baselineLastFtpWatts: Int? = null,
    val baselineLastTestedAt: String? = null,
)

/**
 * Read-only state for the DEVICES setup step.
 *
 * Groups device-related UI state that was previously passed as 16+
 * individual parameters. Callbacks remain as separate lambda parameters
 * on the composable to follow standard Compose conventions.
 */
internal data class DevicesSectionState(
    val trainerDisplayName: String,
    val trainerIndicatorState: DeviceConnectionIndicatorState,
    val hrDisplayName: String,
    val hrIndicatorState: DeviceConnectionIndicatorState,
    val showTwoPane: Boolean,
    val ftmsSelected: Boolean,
    val compatibilityCheckInProgress: Boolean,
    val compatibilityCheckStatusMessage: String?,
    val showMockTrainerControls: Boolean,
    val mockTrainerModeEnabled: Boolean,
    val activeDeviceSelectionKind: DeviceSelectionKind?,
    val scannedDevices: List<ScannedBleDevice>,
    val deviceScanInProgress: Boolean,
    val deviceScanStatus: String?,
    val deviceScanStopEnabled: Boolean,
)

/**
 * Read-only state for the PROFILE setup step.
 *
 * Groups FTP, HR profile, and FIT export preference state.
 */
internal data class ProfileSectionState(
    val ftpWatts: Int,
    val ftpInputText: String,
    val ftpInputError: String?,
    val hrProfileAge: Int?,
    val hrProfileAgeInput: String,
    val hrProfileAgeError: String?,
    val hrProfileSex: HrProfileSex?,
    val hrProfileSummary: String,
    val fitExportPreference: FitExportPreference?,
    val fitExportPreferenceLabel: String,
    /** FTP from the last completed baseline test, or null if none recorded. */
    val baselineLastFtpWatts: Int? = null,
    /** Formatted date/time of the last baseline test, or null if none recorded. */
    val baselineLastTestedAt: String? = null,
)

/**
 * Read-only state for the FILE_BASED setup step.
 *
 * Groups documents folder state, workout file metadata, and layout flags.
 * Dialog triggers and workout mode changes are exposed as callbacks.
 */
internal data class FileBasedSectionState(
    val documentsFolderReady: Boolean,
    val documentsFolderAccessLost: Boolean,
    val documentsFolderSummary: String?,
    val documentsFolderStatusMessage: String?,
    val documentsFolderStatusIsError: Boolean,
    val selectedWorkoutImportError: String?,
    val compactModeActionLayout: Boolean,
    val hasSelectedWorkout: Boolean,
    val selectedWorkoutFileName: String?,
    val workoutFileSummaryValue: String,
    val workoutNameTagValue: String,
    val workoutDescriptionTagValue: String,
    val selectedWorkout: WorkoutFile? = null,
    val selectedImportedWorkout: ImportedErgoWorkout? = null,
    val ftpWatts: Int = 200,
    val workoutMetricsLine: String? = null,
)

/**
 * Read-only state for the SUMMARY setup step.
 *
 * Groups all derived display values shown in the final review before
 * starting a session.
 */
internal data class SummarySectionState(
    val telemetryOnlySelected: Boolean,
    val selectedWorkoutModeLabel: String,
    val workoutDisplayName: String,
    val ftpWatts: Int,
    val hrProfileSummary: String,
    val trainerDisplayName: String,
    val trainerStatusSummary: String,
    val mockTrainerModeEnabled: Boolean,
    val hrDisplayName: String,
    val hrStatusSummary: String,
    val stepCountText: String?,
    val plannedTssText: String?,
    val plannedTssValueText: String?,
    val totalDurationText: String?,
    val statusText: String?,
    val statusTextColor: androidx.compose.ui.graphics.Color,
    val workoutExecutionModeMessage: String?,
    val workoutExecutionModeIsError: Boolean,
    val aiAssistantMessage: String?,
    val aiAssistantIsError: Boolean,
    val aiAssistantTemplateKey: String?,
    val selectedWorkout: WorkoutFile? = null,
    val selectedImportedWorkout: ImportedErgoWorkout? = null,
)

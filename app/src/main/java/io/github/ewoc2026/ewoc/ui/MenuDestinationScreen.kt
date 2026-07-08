package io.github.ewoc2026.ewoc.ui

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.ewoc2026.ewoc.BuildConfig
import io.github.ewoc2026.ewoc.DocumentsFolderWorkoutOption
import io.github.ewoc2026.ewoc.LocalizedWebLinkLauncher
import io.github.ewoc2026.ewoc.R
import io.github.ewoc2026.ewoc.DeviceSelectionKind
import io.github.ewoc2026.ewoc.FitExportPreference
import io.github.ewoc2026.ewoc.HrProfileSex
import io.github.ewoc2026.ewoc.ScannedBleDevice
import io.github.ewoc2026.ewoc.SessionSetupMode
import io.github.ewoc2026.ewoc.ftms.IndoorBikeData
import io.github.ewoc2026.ewoc.session.SessionPhase
import io.github.ewoc2026.ewoc.session.SessionSummary
import io.github.ewoc2026.ewoc.ui.components.SegmentKind
import io.github.ewoc2026.ewoc.ui.components.WorkoutProfileChart
import io.github.ewoc2026.ewoc.ui.components.WorkoutProfileSegment
import io.github.ewoc2026.ewoc.ui.components.buildWorkoutProfileSegments
import io.github.ewoc2026.ewoc.workout.DefaultWorkoutTextEventDurationSec
import io.github.ewoc2026.ewoc.workout.ImportedErgoWorkout
import io.github.ewoc2026.ewoc.workout.WorkoutFile
import io.github.ewoc2026.ewoc.workout.resolveActiveWorkoutTextEvent
import io.github.ewoc2026.ewoc.workout.runner.IntervalPartPhase
import io.github.ewoc2026.ewoc.workout.runner.RunnerState
import java.util.Locale
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Entry screen for starting a session.
 *
 * The start action is gated on a runnable setup mode and trainer readiness.
 * File/editor modes still require a validated workout, while Telemetry only
 * can start without one. Debug-only mock mode remains the single exception
 * where trainer MAC is not required.
 */
@Composable
internal fun MenuScreen(
    state: MenuUiState,
    onSelectWorkoutFile: () -> Unit,
    onChooseDocumentsFolder: () -> Unit,
    onRefreshDocumentsFolderWorkouts: () -> Unit,
    onSelectDocumentsFolderWorkout: (String) -> Unit,
    onSelectBuiltInWorkout: (String) -> Unit,
    onOpenBuiltInWorkoutInEditor: (String) -> Unit,
    onOpenEwoEditor: () -> Unit = {},
    onFtpInputChanged: (String) -> Unit,
    onHrProfileAgeInputChanged: (String) -> Unit,
    onHrProfileSexSelected: (HrProfileSex) -> Unit,
    onSearchFtmsDevices: () -> Unit,
    onSearchHrDevices: () -> Unit,
    onScannedDeviceSelected: (ScannedBleDevice) -> Unit,
    onDismissDeviceSelection: () -> Unit,
    onDismissConnectionIssue: () -> Unit,
    onSearchFtmsDevicesFromConnectionIssue: () -> Unit,
    onOpenAppSettingsFromConnectionIssue: () -> Unit,
    onAiMenuAssistantAction: (String) -> Boolean,
    onMockTrainerModeChanged: (Boolean) -> Unit,
    onQuitApp: () -> Unit = {},
    onFitExportPreferenceSelected: (FitExportPreference) -> Unit,
    onRunCompatibilityCheck: () -> Unit,
    onStartSession: () -> Unit,
    onSelectStarterWorkout: () -> Unit = {},
    onOpenBaselineFitnessTest: () -> Unit = {},
    onSelectTelemetryOnlyMode: () -> Unit = {},
    onSessionSetupModeSelected: (SessionSetupMode) -> Unit = {},
    onActiveSetupStepChanged: (MenuSetupStep?) -> Unit = {},
    previewActiveSetupStep: MenuSetupStep? = null,
) {
    val normalTextColor = menuNormalTextColor()
    val errorTextColor = menuErrorTextColor()
    val pickerStatusColor = menuPickerStatusColor()
    val pickerWarningColor = menuPickerWarningColor()
    val pickerNeutralColor = menuPickerNeutralColor()
    val showWorkoutFileDialog = remember { mutableStateOf(false) }
    val showWorkoutNameDialog = remember { mutableStateOf(false) }
    val showWorkoutDescriptionDialog = remember { mutableStateOf(false) }
    val showOpenWorkoutDialog = remember { mutableStateOf(false) }
    val showDocumentsFolderWorkoutDialog = remember { mutableStateOf(false) }
    val showBuiltInWorkoutDialog = remember { mutableStateOf(false) }
    var showAboutDialog by rememberSaveable { mutableStateOf(false) }
    val unknown = stringResource(R.string.value_unknown)
    val telemetryOnlySelected = state.selectedSessionSetupMode == SessionSetupMode.TELEMETRY_ONLY
    val effectiveSelectedWorkout = if (telemetryOnlySelected) null else state.selectedWorkout
    val effectiveSelectedImportedWorkout = if (telemetryOnlySelected) null else state.selectedImportedWorkout
    val hasSelectedWorkout = effectiveSelectedWorkout != null || effectiveSelectedImportedWorkout != null
    val stepCountText =
        if (hasSelectedWorkout && state.selectedWorkoutStepCount != null && state.selectedWorkoutImportError == null) {
            stringResource(R.string.menu_workout_step_count, state.selectedWorkoutStepCount)
        } else {
            null
        }
    val plannedTssText =
        if (hasSelectedWorkout && state.selectedWorkoutPlannedTss != null && state.selectedWorkoutImportError == null) {
            stringResource(R.string.menu_workout_planned_tss, state.selectedWorkoutPlannedTss)
        } else {
            null
        }
    val plannedTssValueText =
        if (hasSelectedWorkout && state.selectedWorkoutPlannedTss != null && state.selectedWorkoutImportError == null) {
            String.format(Locale.getDefault(), "%.1f", state.selectedWorkoutPlannedTss)
        } else {
            null
        }
    val totalDurationText =
        if (hasSelectedWorkout && state.selectedWorkoutTotalDurationSec != null && state.selectedWorkoutImportError == null) {
            formatDurationMinSec(state.selectedWorkoutTotalDurationSec)
        } else {
            null
        }
    val workoutMetricsLine = if (hasSelectedWorkout && state.selectedWorkoutImportError == null) {
        listOfNotNull(
            stepCountText,
            totalDurationText?.let {
                stringResource(R.string.menu_setup_summary_duration) + ": " + it
            },
            plannedTssText,
        ).takeIf { it.isNotEmpty() }?.joinToString(" · ")
    } else {
        null
    }
    val statusText =
        when {
            state.selectedWorkoutImportError != null -> {
                state.selectedWorkoutImportError
            }
            telemetryOnlySelected -> null
            !hasSelectedWorkout -> stringResource(R.string.menu_workout_not_selected)
            else -> null
        }
    val statusTextColor = if (state.selectedWorkoutImportError != null) errorTextColor else normalTextColor
    val startBlockedReasonText = if (!state.startEnabled) {
        val reasons = mutableListOf<String>()
        if (state.selectedWorkoutImportError != null) {
            reasons += stringResource(R.string.menu_start_blocked_fix_workout)
        } else if (!telemetryOnlySelected && !hasSelectedWorkout) {
            reasons += stringResource(R.string.menu_start_blocked_select_workout)
        }
        if (!state.mockTrainerModeEnabled && !state.ftmsSelected) {
            reasons += stringResource(R.string.menu_start_blocked_select_trainer)
        }
        if (state.workoutExecutionModeIsError) {
            reasons += stringResource(R.string.menu_start_blocked_execution)
        }
        if (reasons.isEmpty()) {
            stringResource(R.string.menu_start_blocked_generic)
        } else {
            reasons.joinToString(separator = " ")
        }
    } else {
        null
    }
    val workoutFileDisplayName = state.selectedWorkoutFileName
        ?.substringAfterLast('/')
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: stringResource(R.string.menu_workout_file_none)
    val workoutFileSummaryValue = if (state.selectedWorkoutFileName != null) {
        stringResource(R.string.menu_workout_selected_file_value, workoutFileDisplayName)
    } else {
        workoutFileDisplayName
    }
    val documentsFolderSummaryText = buildString {
        append(
            stringResource(
                R.string.menu_workout_default_folder_value,
                state.documentsFolderSummary ?: stringResource(R.string.menu_documents_folder_unconfigured),
            ),
        )
        if (state.documentsFolderStatusIsError && !state.documentsFolderStatusMessage.isNullOrBlank()) {
            append(" · ")
            append(state.documentsFolderStatusMessage)
        }
    }
    val workoutDisplayName = resolveWorkoutDisplayName(
        selectedWorkout = effectiveSelectedWorkout,
        selectedImportedWorkout = effectiveSelectedImportedWorkout,
        selectedWorkoutFileName = state.selectedWorkoutFileName,
        selectedSessionSetupMode = state.selectedSessionSetupMode,
        fallback = stringResource(R.string.menu_workout_file_none),
        telemetryOnlyLabel = stringResource(R.string.menu_setup_mode_telemetry_only_title),
    )
    val workoutNameTagValue = effectiveSelectedWorkout?.name
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: effectiveSelectedImportedWorkout?.title
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        ?: unknown
    val workoutDescriptionTagValue = resolveWorkoutDescription(
        selectedWorkout = effectiveSelectedWorkout,
        selectedImportedWorkout = effectiveSelectedImportedWorkout,
        fallback = unknown,
    )
    val hrProfileSexLabel = when (state.hrProfileSex) {
        HrProfileSex.MALE -> stringResource(R.string.menu_hr_profile_sex_male)
        HrProfileSex.FEMALE -> stringResource(R.string.menu_hr_profile_sex_female)
        null -> unknown
    }
    val hrProfileSummary = when {
        state.hrProfileAge != null && state.hrProfileSex != null -> {
            stringResource(R.string.menu_hr_profile_summary_value, state.hrProfileAge, hrProfileSexLabel)
        }
        state.hrProfileAge != null -> {
            stringResource(R.string.menu_hr_profile_summary_age_only_estimate, state.hrProfileAge)
        }
        else -> {
            stringResource(R.string.menu_hr_profile_summary_missing)
        }
    }
    val fitExportPreferenceLabel = when (state.fitExportPreference) {
        FitExportPreference.AUTO_SAVE -> stringResource(R.string.summary_fit_preference_auto_save)
        FitExportPreference.ASK_EVERY_TIME -> stringResource(R.string.summary_fit_preference_ask_every_time)
        FitExportPreference.DO_NOT_SAVE -> stringResource(R.string.summary_fit_preference_do_not_save)
        null -> stringResource(R.string.menu_fit_export_preference_not_set)
    }
    val trainerDisplayName = state.ftmsDeviceName.ifBlank { stringResource(R.string.menu_device_not_selected) }
    val hrDisplayName = state.hrDeviceName.ifBlank { stringResource(R.string.menu_device_not_selected) }
    val trainerIndicatorState = when {
        state.ftmsConnected -> DeviceConnectionIndicatorState.CONNECTED
        state.ftmsSelected && state.ftmsConnectionKnown -> DeviceConnectionIndicatorState.ISSUE
        (state.suggestTrainerSearchAfterConnectionIssue || state.suggestOpenSettingsAfterConnectionIssue) &&
            !state.connectionIssueMessage.isNullOrBlank() -> {
            DeviceConnectionIndicatorState.ISSUE
        }
        else -> DeviceConnectionIndicatorState.IDLE
    }
    val hrIndicatorState = if (state.hrConnected) {
        DeviceConnectionIndicatorState.CONNECTED
    } else if (state.hrSelected && state.hrConnectionKnown) {
        DeviceConnectionIndicatorState.ISSUE
    } else {
        DeviceConnectionIndicatorState.IDLE
    }

    BoxWithConstraints(
        modifier = Modifier
            .testTag(DebugAutomationTags.MENU_ROOT)
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        val layoutMode = rememberImeStableAdaptiveLayoutMode(width = maxWidth, height = maxHeight)
        val showTwoPane = layoutMode.isTwoPane()
        val isDense = layoutMode == AdaptiveLayoutMode.SINGLE_PANE_DENSE
        val showTwoColumnDetail = showTwoPane || isDense
        val contentMaxWidth = if (showTwoColumnDetail) MenuTwoPaneMaxContentWidth else MenuMaxContentWidth
        val compactHubLayout = maxHeight < 860.dp || (maxWidth > maxHeight && maxHeight < 1100.dp)
        val isLandscape = maxWidth > maxHeight
        val compactModeActionLayout = maxWidth < 420.dp
        val narrowLandscapeTwoColumnHub = isLandscape && maxHeight < 500.dp
        val activeSetupStep = previewActiveSetupStep ?: state.activeSetupStep
        val updateActiveSetupStep = onActiveSetupStepChanged
        val profileValidForSession = state.ftpInputError == null
        val profileReady =
            profileValidForSession &&
                state.hrProfileAge != null &&
                state.hrProfileAgeError == null &&
                state.hrProfileSex != null
        val devicesConfigured = state.ftmsSelected || state.mockTrainerModeEnabled
        val modeValidationMessage = when (state.selectedSessionSetupMode) {
            SessionSetupMode.FILE,
            SessionSetupMode.EDITOR -> when {
                state.selectedWorkoutImportError != null -> stringResource(R.string.menu_setup_mode_fix_workout)
                !hasSelectedWorkout -> stringResource(R.string.menu_setup_mode_select_workout)
                state.workoutExecutionModeIsError -> stringResource(R.string.menu_setup_mode_fix_execution)
                else -> null
            }

            SessionSetupMode.TELEMETRY_ONLY -> if (state.workoutExecutionModeIsError) {
                stringResource(R.string.menu_setup_mode_fix_execution)
            } else {
                null
            }

        }
        val modeConfigured = modeValidationMessage == null
        val summaryConfigured = state.startEnabled
        val selectedWorkoutModeLabel = when (state.selectedSessionSetupMode) {
            SessionSetupMode.FILE -> stringResource(R.string.menu_setup_mode_file_title)
            SessionSetupMode.EDITOR -> stringResource(R.string.menu_setup_mode_editor_title)
            SessionSetupMode.TELEMETRY_ONLY -> stringResource(R.string.menu_setup_mode_telemetry_only_title)
        }
        val modeStepSupportingText = stringResource(
            R.string.menu_setup_mode_selected_value,
            selectedWorkoutModeLabel,
        )
        val trainerStatusSummary = when {
            state.mockTrainerModeEnabled -> stringResource(R.string.menu_mock_mode_active)
            state.ftmsConnected -> stringResource(R.string.menu_setup_status_connected)
            state.ftmsSelected -> stringResource(R.string.menu_setup_status_selected)
            else -> stringResource(R.string.menu_device_not_selected)
        }
        val hrStatusSummary = when {
            state.hrConnected -> stringResource(R.string.menu_setup_status_connected)
            state.hrSelected -> stringResource(R.string.menu_setup_status_selected)
            else -> stringResource(R.string.menu_device_not_selected)
        }
        val currentStepValidationMessage = when (activeSetupStep) {
            MenuSetupStep.PROFILE -> if (!profileValidForSession) {
                stringResource(R.string.menu_setup_profile_fix_ftp)
            } else {
                null
            }
            MenuSetupStep.DEVICES -> if (!devicesConfigured) {
                stringResource(R.string.menu_setup_devices_select_trainer)
            } else {
                null
            }
            MenuSetupStep.FILE_BASED -> modeValidationMessage
            MenuSetupStep.SUMMARY -> startBlockedReasonText
            null -> null
        }
        val activeSetupHeading = when (activeSetupStep) {
            MenuSetupStep.PROFILE -> stringResource(R.string.menu_setup_step_profile_heading)
            MenuSetupStep.DEVICES -> stringResource(R.string.menu_setup_step_devices_heading)
            MenuSetupStep.FILE_BASED -> stringResource(R.string.menu_setup_step_mode_title)
            MenuSetupStep.SUMMARY -> if (telemetryOnlySelected) {
                stringResource(R.string.menu_setup_step_ready_heading)
            } else {
                stringResource(R.string.menu_setup_step_summary_heading)
            }
            null -> null
        }
        val summaryStepTitle = if (telemetryOnlySelected) {
            stringResource(R.string.menu_setup_step_ready_title)
        } else {
            stringResource(R.string.menu_setup_step_summary_title)
        }
        // Hub removed from detail steps; each step handles its own two-column layout via isTwoColumn.
        val scrollContentSpacing = if (compactHubLayout) 12.dp else 16.dp
        val scrollBottomPadding = if (activeSetupStep == null) 24.dp else 8.dp

        @Composable
        fun MenuHeaderContent(
            showActiveHeading: Boolean,
            modifier: Modifier = Modifier,
        ) {
            Column(
                modifier = modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (showActiveHeading && activeSetupHeading != null) {
                        Text(
                            text = stringResource(R.string.menu_title) + " " + BuildConfig.VERSION_NAME,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = normalTextColor,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = activeSetupHeading,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = normalTextColor,
                            textAlign = TextAlign.Start,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.menu_title) + " " + BuildConfig.VERSION_NAME,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = normalTextColor,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(
                        onClick = { showAboutDialog = true },
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = stringResource(R.string.menu_about_title),
                            tint = normalTextColor,
                        )
                    }
                }
                if (state.mockTrainerModeEnabled) {
                    Text(
                        text = stringResource(R.string.menu_mock_mode_active),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
        }

        @Composable
        fun MenuSetupHubContent(
            modifier: Modifier = Modifier,
            highlightSelectedStep: Boolean = false,
        ) {
            SectionCard(
                title = stringResource(R.string.menu_setup_flow_title),
                compact = compactHubLayout,
                modifier = modifier,
            ) {
                if (narrowLandscapeTwoColumnHub) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        MenuSetupEntryCard(
                            title = stringResource(R.string.menu_setup_step_profile_title),
                            statusLabel = if (profileReady) {
                                stringResource(R.string.menu_setup_status_ready)
                            } else {
                                stringResource(R.string.menu_setup_status_needs_setup)
                            },
                            ready = profileReady,
                            selected = highlightSelectedStep && activeSetupStep == MenuSetupStep.PROFILE,
                            compact = compactHubLayout,
                            modifier = Modifier.weight(1f),
                            automationTag = DebugAutomationTags.MENU_STEP_PROFILE,
                            onClick = { updateActiveSetupStep(MenuSetupStep.PROFILE) },
                        )
                        MenuSetupEntryCard(
                            title = stringResource(R.string.menu_setup_step_devices_title),
                            statusLabel = if (devicesConfigured) {
                                stringResource(R.string.menu_setup_status_ready)
                            } else {
                                stringResource(R.string.menu_setup_status_needs_setup)
                            },
                            ready = devicesConfigured,
                            selected = highlightSelectedStep && activeSetupStep == MenuSetupStep.DEVICES,
                            compact = compactHubLayout,
                            modifier = Modifier.weight(1f),
                            automationTag = DebugAutomationTags.MENU_STEP_DEVICES,
                            onClick = { updateActiveSetupStep(MenuSetupStep.DEVICES) },
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        MenuSetupEntryCard(
                            title = stringResource(R.string.menu_setup_step_mode_title),
                            statusLabel = if (modeConfigured) {
                                stringResource(R.string.menu_setup_status_ready)
                            } else {
                                stringResource(R.string.menu_setup_status_needs_setup)
                            },
                            supportingText = modeStepSupportingText,
                            ready = modeConfigured,
                            selected = highlightSelectedStep && activeSetupStep == MenuSetupStep.FILE_BASED,
                            compact = compactHubLayout,
                            modifier = Modifier.weight(1f),
                            automationTag = DebugAutomationTags.MENU_STEP_FILE_BASED,
                            onClick = { updateActiveSetupStep(MenuSetupStep.FILE_BASED) },
                        )
                        MenuSetupEntryCard(
                            title = summaryStepTitle,
                            statusLabel = if (summaryConfigured) {
                                stringResource(R.string.menu_setup_status_ready)
                            } else {
                                stringResource(R.string.menu_setup_status_needs_setup)
                            },
                            ready = summaryConfigured,
                            selected = highlightSelectedStep && activeSetupStep == MenuSetupStep.SUMMARY,
                            compact = compactHubLayout,
                            modifier = Modifier.weight(1f),
                            automationTag = DebugAutomationTags.MENU_STEP_SUMMARY,
                            onClick = { updateActiveSetupStep(MenuSetupStep.SUMMARY) },
                        )
                    }
                } else {
                    MenuSetupEntryCard(
                        title = stringResource(R.string.menu_setup_step_profile_title),
                        statusLabel = if (profileReady) {
                            stringResource(R.string.menu_setup_status_ready)
                        } else {
                            stringResource(R.string.menu_setup_status_needs_setup)
                        },
                        ready = profileReady,
                        selected = highlightSelectedStep && activeSetupStep == MenuSetupStep.PROFILE,
                        compact = compactHubLayout,
                        automationTag = DebugAutomationTags.MENU_STEP_PROFILE,
                        onClick = { updateActiveSetupStep(MenuSetupStep.PROFILE) },
                    )
                    MenuSetupEntryCard(
                        title = stringResource(R.string.menu_setup_step_devices_title),
                        statusLabel = if (devicesConfigured) {
                            stringResource(R.string.menu_setup_status_ready)
                        } else {
                            stringResource(R.string.menu_setup_status_needs_setup)
                        },
                        ready = devicesConfigured,
                        selected = highlightSelectedStep && activeSetupStep == MenuSetupStep.DEVICES,
                        compact = compactHubLayout,
                        automationTag = DebugAutomationTags.MENU_STEP_DEVICES,
                        onClick = { updateActiveSetupStep(MenuSetupStep.DEVICES) },
                    )
                    MenuSetupEntryCard(
                        title = stringResource(R.string.menu_setup_step_mode_title),
                        statusLabel = if (modeConfigured) {
                            stringResource(R.string.menu_setup_status_ready)
                        } else {
                            stringResource(R.string.menu_setup_status_needs_setup)
                        },
                        supportingText = modeStepSupportingText,
                        ready = modeConfigured,
                        selected = highlightSelectedStep && activeSetupStep == MenuSetupStep.FILE_BASED,
                        compact = compactHubLayout,
                        automationTag = DebugAutomationTags.MENU_STEP_FILE_BASED,
                        onClick = { updateActiveSetupStep(MenuSetupStep.FILE_BASED) },
                    )
                    MenuSetupEntryCard(
                        title = summaryStepTitle,
                        statusLabel = if (summaryConfigured) {
                            stringResource(R.string.menu_setup_status_ready)
                        } else {
                            stringResource(R.string.menu_setup_status_needs_setup)
                        },
                        ready = summaryConfigured,
                        selected = highlightSelectedStep && activeSetupStep == MenuSetupStep.SUMMARY,
                        compact = compactHubLayout,
                        automationTag = DebugAutomationTags.MENU_STEP_SUMMARY,
                        onClick = { updateActiveSetupStep(MenuSetupStep.SUMMARY) },
                    )
                }
            }
        }

        @Composable
        fun MenuSetupDetailContent(
            modifier: Modifier = Modifier,
            showStepHeading: Boolean = false,
            isTwoColumn: Boolean = false,
            isCompact: Boolean = false,
        ) {
            val selectedSetupStep = activeSetupStep ?: return
            val summaryStepActive = selectedSetupStep == MenuSetupStep.SUMMARY
            val canApplySelectedSetupStep = currentStepValidationMessage == null
            val applyTargetSetupStep = when (selectedSetupStep) {
                MenuSetupStep.PROFILE -> MenuSetupStep.DEVICES
                MenuSetupStep.DEVICES -> MenuSetupStep.FILE_BASED
                MenuSetupStep.FILE_BASED -> MenuSetupStep.SUMMARY
                MenuSetupStep.SUMMARY -> MenuSetupStep.SUMMARY
            }

            Column(
                modifier = modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(scrollContentSpacing),
            ) {
                if (showStepHeading && activeSetupHeading != null) {
                    Text(
                        text = activeSetupHeading,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = normalTextColor,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = { updateActiveSetupStep(null) },
                        modifier = if (summaryStepActive) {
                            Modifier
                                .fillMaxWidth(0.3f)
                                .widthIn(min = 88.dp, max = 144.dp)
                        } else {
                            Modifier.weight(1f)
                        },
                    ) {
                        Text(
                            text = if (summaryStepActive) {
                                stringResource(R.string.menu_setup_back_button)
                            } else {
                                stringResource(R.string.menu_setup_back_to_hub_button)
                            },
                        )
                    }
                    if (summaryStepActive) {
                        Spacer(modifier = Modifier.weight(1f))
                        Button(
                            onClick = onStartSession,
                            enabled = state.startEnabled,
                            modifier = Modifier
                                .widthIn(min = 148.dp, max = 220.dp)
                                .testTag(DebugAutomationTags.MENU_SUMMARY_START_SESSION),
                            colors = menuStartButtonColors(),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = null,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.menu_start_session),
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    } else {
                        Button(
                            onClick = {
                                if (canApplySelectedSetupStep) {
                                    updateActiveSetupStep(applyTargetSetupStep)
                                }
                            },
                            enabled = canApplySelectedSetupStep,
                            modifier = Modifier.weight(1f),
                            colors = menuSecondaryButtonColors(),
                        ) {
                            Text(stringResource(R.string.menu_setup_ok_button))
                        }
                    }
                }
                if (currentStepValidationMessage != null) {
                    Text(
                        text = currentStepValidationMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = errorTextColor,
                    )
                }
                when (selectedSetupStep) {
                    MenuSetupStep.PROFILE -> {
                        MenuProfileStepContent(
                            isTwoColumn = isTwoColumn,
                            isCompact = isCompact,
                            state = ProfileSectionState(
                                ftpWatts = state.ftpWatts,
                                ftpInputText = state.ftpInputText,
                                ftpInputError = state.ftpInputError,
                                hrProfileAge = state.hrProfileAge,
                                hrProfileAgeInput = state.hrProfileAgeInput,
                                hrProfileAgeError = state.hrProfileAgeError,
                                hrProfileSex = state.hrProfileSex,
                                hrProfileSummary = hrProfileSummary,
                                fitExportPreference = state.fitExportPreference,
                                fitExportPreferenceLabel = fitExportPreferenceLabel,
                                baselineLastFtpWatts = state.baselineLastFtpWatts,
                                baselineLastTestedAt = state.baselineLastTestedAt,
                            ),
                            onFtpInputChanged = onFtpInputChanged,
                            onHrProfileAgeInputChanged = onHrProfileAgeInputChanged,
                            onHrProfileSexSelected = onHrProfileSexSelected,
                            onFitExportPreferenceSelected = onFitExportPreferenceSelected,
                            onOpenBaselineFitnessTest = onOpenBaselineFitnessTest,
                        )
                    }

                    MenuSetupStep.DEVICES -> {
                        MenuDevicesStepContent(
                            isTwoColumn = isTwoColumn,
                            isDense = isDense,
                            state = DevicesSectionState(
                                trainerDisplayName = trainerDisplayName,
                                trainerIndicatorState = trainerIndicatorState,
                                hrDisplayName = hrDisplayName,
                                hrIndicatorState = hrIndicatorState,
                                showTwoPane = showTwoPane,
                                ftmsSelected = state.ftmsSelected,
                                compatibilityCheckInProgress = state.compatibilityCheckInProgress,
                                compatibilityCheckStatusMessage = state.compatibilityCheckStatusMessage,
                                showMockTrainerControls = state.showMockTrainerControls,
                                mockTrainerModeEnabled = state.mockTrainerModeEnabled,
                                activeDeviceSelectionKind = state.activeDeviceSelectionKind,
                                scannedDevices = state.scannedDevices,
                                deviceScanInProgress = state.deviceScanInProgress,
                                deviceScanStatus = state.deviceScanStatus,
                                deviceScanStopEnabled = state.deviceScanStopEnabled,
                            ),
                            onSearchFtmsDevices = onSearchFtmsDevices,
                            onSearchHrDevices = onSearchHrDevices,
                            onRunCompatibilityCheck = onRunCompatibilityCheck,
                            onMockTrainerModeChanged = onMockTrainerModeChanged,
                            onScannedDeviceSelected = onScannedDeviceSelected,
                            onDismissDeviceSelection = onDismissDeviceSelection,
                        )
                    }

                    MenuSetupStep.FILE_BASED -> {
                        MenuWorkoutModeStepContent(
                            selectedSessionSetupMode = state.selectedSessionSetupMode,
                            isLandscape = isLandscape || isTwoColumn,
                            isCompact = isCompact,
                            fileBasedState = FileBasedSectionState(
                                documentsFolderReady = state.documentsFolderReady,
                                documentsFolderAccessLost = state.documentsFolderAccessLost,
                                documentsFolderSummary = state.documentsFolderSummary,
                                documentsFolderStatusMessage = state.documentsFolderStatusMessage,
                                documentsFolderStatusIsError = state.documentsFolderStatusIsError,
                                selectedWorkoutImportError = state.selectedWorkoutImportError,
                                compactModeActionLayout = compactModeActionLayout,
                                hasSelectedWorkout = hasSelectedWorkout,
                                selectedWorkoutFileName = state.selectedWorkoutFileName,
                                workoutFileSummaryValue = workoutFileSummaryValue,
                                workoutNameTagValue = workoutNameTagValue,
                                workoutDescriptionTagValue = workoutDescriptionTagValue,
                                selectedWorkout = effectiveSelectedWorkout,
                                selectedImportedWorkout = effectiveSelectedImportedWorkout,
                                ftpWatts = state.ftpWatts,
                                workoutMetricsLine = workoutMetricsLine,
                            ),
                            onSelectStructuredMode = {
                                if (state.selectedSessionSetupMode != SessionSetupMode.FILE &&
                                    state.selectedSessionSetupMode != SessionSetupMode.EDITOR
                                ) {
                                    onSessionSetupModeSelected(SessionSetupMode.FILE)
                                }
                            },
                            onSelectTelemetryOnly = onSelectTelemetryOnlyMode,
                            onShowOpenWorkoutDialog = { showOpenWorkoutDialog.value = true },
                            onChooseDocumentsFolder = onChooseDocumentsFolder,
                            onOpenEwoEditor = onOpenEwoEditor,
                            onShowWorkoutFileDialog = { showWorkoutFileDialog.value = true },
                            onShowWorkoutNameDialog = { showWorkoutNameDialog.value = true },
                            onShowWorkoutDescriptionDialog = { showWorkoutDescriptionDialog.value = true },
                        )
                    }

                    MenuSetupStep.SUMMARY -> {
                        MenuSummaryStepContent(
                            isTwoColumn = isTwoColumn,
                            isCompact = isCompact,
                            state = SummarySectionState(
                                telemetryOnlySelected = telemetryOnlySelected,
                                selectedWorkoutModeLabel = selectedWorkoutModeLabel,
                                workoutDisplayName = workoutDisplayName,
                                ftpWatts = state.ftpWatts,
                                hrProfileSummary = hrProfileSummary,
                                trainerDisplayName = trainerDisplayName,
                                trainerStatusSummary = trainerStatusSummary,
                                mockTrainerModeEnabled = state.mockTrainerModeEnabled,
                                hrDisplayName = hrDisplayName,
                                hrStatusSummary = hrStatusSummary,
                                stepCountText = stepCountText,
                                plannedTssText = plannedTssText,
                                plannedTssValueText = plannedTssValueText,
                                totalDurationText = totalDurationText,
                                statusText = statusText,
                                statusTextColor = statusTextColor,
                                workoutExecutionModeMessage = state.workoutExecutionModeMessage,
                                workoutExecutionModeIsError = state.workoutExecutionModeIsError,
                                aiAssistantMessage = state.aiAssistantMessage,
                                aiAssistantIsError = state.aiAssistantIsError,
                                aiAssistantTemplateKey = state.aiAssistantTemplateKey,
                                selectedWorkout = effectiveSelectedWorkout,
                                selectedImportedWorkout = effectiveSelectedImportedWorkout,
                            ),
                            onAiMenuAssistantAction = onAiMenuAssistantAction,
                            onNavigateToDevices = { updateActiveSetupStep(MenuSetupStep.DEVICES) },
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = if (isDense) 12.dp else 20.dp,
                    vertical = if (isDense) 12.dp else 24.dp,
                ),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = contentMaxWidth)
                    .fillMaxHeight()
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(bottom = scrollBottomPadding),
                    verticalArrangement = Arrangement.spacedBy(scrollContentSpacing),
                ) {
                    item(key = "menuScrollableContent") {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(scrollContentSpacing),
                        ) {
                            if (activeSetupStep == null) {
                                MenuHeaderContent(showActiveHeading = false)
                                MenuSetupHubContent()
                                OutlinedButton(
                                    onClick = onQuitApp,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(stringResource(R.string.menu_quit_app_button))
                                }
                            } else {
                                MenuSetupDetailContent(
                                    isTwoColumn = showTwoColumnDetail,
                                    isCompact = isDense,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // --- Dialogs (extracted to MenuDialogs.kt) ---

    if (showWorkoutFileDialog.value && state.selectedWorkoutFileName != null) {
        MenuWorkoutFileDialog(
            workoutFileDisplayName = workoutFileDisplayName,
            onDismiss = { showWorkoutFileDialog.value = false },
        )
    }

    if (showWorkoutNameDialog.value && hasSelectedWorkout) {
        MenuWorkoutNameDialog(
            workoutNameTagValue = workoutNameTagValue,
            onDismiss = { showWorkoutNameDialog.value = false },
        )
    }

    if (showWorkoutDescriptionDialog.value && hasSelectedWorkout) {
        MenuWorkoutDescriptionDialog(
            workoutDescriptionTagValue = workoutDescriptionTagValue,
            onDismiss = { showWorkoutDescriptionDialog.value = false },
        )
    }

    if (showOpenWorkoutDialog.value) {
        MenuOpenWorkoutDialog(
            linkedFolderReady = state.documentsFolderReady,
            linkedFolderSummary = documentsFolderSummaryText,
            onOpenWorkoutFile = {
                showOpenWorkoutDialog.value = false
                onSessionSetupModeSelected(SessionSetupMode.FILE)
                onSelectWorkoutFile()
            },
            onOpenFromLinkedFolder = {
                showOpenWorkoutDialog.value = false
                onSessionSetupModeSelected(SessionSetupMode.FILE)
                onRefreshDocumentsFolderWorkouts()
                showDocumentsFolderWorkoutDialog.value = true
            },
            onOpenBuiltInWorkouts = {
                showOpenWorkoutDialog.value = false
                showBuiltInWorkoutDialog.value = true
            },
            onDismiss = { showOpenWorkoutDialog.value = false },
        )
    }

    if (showDocumentsFolderWorkoutDialog.value) {
        MenuDocumentsFolderWorkoutDialog(
            documentsFolderWorkoutFiles = state.documentsFolderWorkoutFiles,
            onSelectWorkout = onSelectDocumentsFolderWorkout,
            onDismiss = { showDocumentsFolderWorkoutDialog.value = false },
        )
    }

    if (showBuiltInWorkoutDialog.value) {
        MenuBuiltInWorkoutDialog(
            builtInWorkoutFiles = state.builtInWorkoutFiles,
            onSelectWorkout = {
                onSessionSetupModeSelected(SessionSetupMode.FILE)
                onSelectBuiltInWorkout(it)
            },
            onOpenInEditor = {
                onOpenBuiltInWorkoutInEditor(it)
            },
            onDismiss = { showBuiltInWorkoutDialog.value = false },
        )
    }

    val showConnectionIssueDialog =
        state.connectionIssueMessage != null &&
            (state.suggestTrainerSearchAfterConnectionIssue || state.suggestOpenSettingsAfterConnectionIssue)
    if (showConnectionIssueDialog) {
        MenuConnectionIssueDialog(
            message = state.connectionIssueMessage,
            suggestTrainerSearch = state.suggestTrainerSearchAfterConnectionIssue,
            suggestOpenSettings = state.suggestOpenSettingsAfterConnectionIssue,
            onSearchAgain = onSearchFtmsDevicesFromConnectionIssue,
            onOpenSettings = onOpenAppSettingsFromConnectionIssue,
            onDismiss = onDismissConnectionIssue,
        )
    }

    if (showAboutDialog) {
        MenuAboutDialog(
            onDismiss = { showAboutDialog = false },
        )
    }
}

@Composable
private fun MenuAboutDialog(
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val openLinkFailed = stringResource(R.string.menu_about_open_link_failed)

    fun openUrl(url: String) {
        if (!LocalizedWebLinkLauncher.open(context = context, url = url)) {
            Toast.makeText(context, openLinkFailed, Toast.LENGTH_SHORT).show()
        }
    }

    val changelogUrl = stringResource(R.string.about_url_changelog)
    val userGuideUrl = stringResource(R.string.about_url_user_guide)
    val supportUrl = stringResource(R.string.about_url_support)
    val privacyPolicyUrl = stringResource(R.string.privacy_policy_url)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.menu_about_title) + " " + BuildConfig.VERSION_NAME,
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TextButton(onClick = { openUrl(changelogUrl) }) {
                    Text(
                        text = stringResource(R.string.menu_about_changelog),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                TextButton(onClick = { openUrl(userGuideUrl) }) {
                    Text(
                        text = stringResource(R.string.menu_about_user_guide),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                TextButton(onClick = { openUrl(supportUrl) }) {
                    Text(
                        text = stringResource(R.string.menu_about_support),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                TextButton(onClick = { openUrl(privacyPolicyUrl) }) {
                    Text(
                        text = stringResource(R.string.menu_about_privacy_policy),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.menu_dialog_ok))
            }
        },
    )
}

private fun formatDurationMinSec(totalSec: Int): String {
    val hours = totalSec / 3600
    val minutes = (totalSec % 3600) / 60
    val seconds = totalSec % 60
    return when {
        hours > 0 && seconds > 0 -> "${hours}h ${minutes}m ${seconds}s"
        hours > 0 -> "${hours}h ${minutes}m"
        seconds > 0 -> "${minutes}m ${seconds}s"
        else -> "${minutes}m"
    }
}

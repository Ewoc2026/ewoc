package com.example.ergometerapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ergometerapp.AI_MENU_CONNECTIVITY_CHECK_TRAINER_ACTION
import com.example.ergometerapp.R
import com.example.ergometerapp.ui.components.WorkoutProfileChart
import com.example.ergometerapp.ui.components.buildWorkoutProfileSegments

/**
 * SUMMARY step content within the menu setup detail flow.
 *
 * Renders a final review of all configuration (mode, workout, FTP, HR,
 * trainer, steps, TSS) plus workout execution mode and AI assistant
 * messages before starting a session.
 */
@Composable
internal fun MenuSummaryStepContent(
    state: SummarySectionState,
    onAiMenuAssistantAction: (String) -> Boolean,
    onNavigateToDevices: () -> Unit,
    isTwoColumn: Boolean = false,
    isCompact: Boolean = false,
) {
    val sectionTitle = if (state.telemetryOnlySelected) {
        stringResource(R.string.menu_setup_step_ready_title)
    } else {
        stringResource(R.string.menu_setup_step_summary_title)
    }

    if (isTwoColumn && !state.telemetryOnlySelected) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SummaryConfigCard(state, sectionTitle, isCompact)
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SummaryStatusCard(state, onAiMenuAssistantAction, onNavigateToDevices, isCompact)
                val previewSegments = remember(
                    state.selectedWorkout,
                    state.selectedImportedWorkout,
                    state.ftpWatts,
                ) {
                    when {
                        state.selectedWorkout != null -> buildWorkoutProfileSegments(state.selectedWorkout)
                        state.selectedImportedWorkout != null ->
                            buildWorkoutProfileSegments(state.selectedImportedWorkout, state.ftpWatts)
                        else -> emptyList()
                    }
                }
                if (previewSegments.isNotEmpty()) {
                    WorkoutProfileChart(
                        segments = previewSegments,
                        ftpWatts = state.ftpWatts,
                        modifier = Modifier.fillMaxWidth(),
                        chartHeight = 175.dp,
                    )
                }
            }
        }
    } else {
        SectionCard(title = sectionTitle, compact = isCompact) {
            SummaryAllItems(state, onAiMenuAssistantAction, onNavigateToDevices, isCompact)
        }
    }
}

@Composable
private fun SummaryConfigCard(
    state: SummarySectionState,
    title: String,
    isCompact: Boolean = false,
) {
    SectionCard(title = title, compact = isCompact) {
        MenuSetupSummaryItem(
            label = stringResource(R.string.menu_setup_summary_mode),
            value = state.selectedWorkoutModeLabel,
        )
        if (!state.telemetryOnlySelected) {
            MenuSetupSummaryItem(
                label = stringResource(R.string.menu_setup_summary_workout),
                value = state.workoutDisplayName,
            )
        }
        MenuSetupSummaryItem(
            label = stringResource(R.string.menu_setup_summary_ftp),
            value = stringResource(R.string.menu_ftp_hint, state.ftpWatts),
        )
        MenuSetupSummaryItem(
            label = stringResource(R.string.menu_hr_profile_title),
            value = state.hrProfileSummary,
        )
        MenuSetupSummaryItem(
            label = stringResource(R.string.menu_trainer_device_label),
            value = formatDeviceSummaryValue(
                displayName = state.trainerDisplayName,
                statusSummary = state.trainerStatusSummary,
            ),
        )
        if (state.mockTrainerModeEnabled) {
            MenuSetupSummaryItem(
                label = stringResource(R.string.menu_mock_trainer_toggle_label),
                value = stringResource(R.string.menu_mock_mode_active),
            )
        }
        MenuSetupSummaryItem(
            label = stringResource(R.string.menu_hr_device_label),
            value = formatDeviceSummaryValue(
                displayName = state.hrDisplayName,
                statusSummary = state.hrStatusSummary,
            ),
        )
    }
}

@Composable
private fun SummaryStatusCard(
    state: SummarySectionState,
    onAiMenuAssistantAction: (String) -> Boolean,
    onNavigateToDevices: () -> Unit,
    isCompact: Boolean = false,
) {
    val normalTextColor = menuNormalTextColor()
    val errorTextColor = menuErrorTextColor()

    SectionCard(title = stringResource(R.string.menu_setup_summary_workout), compact = isCompact) {
        val metricsLine = listOfNotNull(
            state.stepCountText,
            state.totalDurationText,
            state.plannedTssText,
        ).takeIf { it.isNotEmpty() }?.joinToString(" · ")
        if (metricsLine != null) {
            Text(
                text = metricsLine,
                style = MaterialTheme.typography.bodyMedium,
                color = menuNormalTextColor(),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }
        if (state.statusText != null) {
            Text(
                text = state.statusText,
                style = MaterialTheme.typography.bodyMedium,
                color = state.statusTextColor,
            )
        }
        SummaryMessages(state, onAiMenuAssistantAction, onNavigateToDevices)
    }
}

@Composable
private fun SummaryMessages(
    state: SummarySectionState,
    onAiMenuAssistantAction: (String) -> Boolean,
    onNavigateToDevices: () -> Unit,
) {
    val normalTextColor = menuNormalTextColor()
    val errorTextColor = menuErrorTextColor()

    if (state.workoutExecutionModeMessage != null) {
        Text(
            text = state.workoutExecutionModeMessage,
            style = MaterialTheme.typography.bodyMedium,
            color = if (state.workoutExecutionModeIsError) {
                errorTextColor
            } else {
                normalTextColor
            }
        )
    }
    if (state.aiAssistantMessage != null) {
        Text(
            text = state.aiAssistantMessage,
            style = MaterialTheme.typography.bodyMedium,
            color = if (state.aiAssistantIsError) {
                errorTextColor
            } else {
                normalTextColor
            }
        )
        if (state.aiAssistantTemplateKey == AI_MENU_CONNECTIVITY_CHECK_TRAINER_ACTION) {
            OutlinedButton(
                onClick = {
                    val shouldOpenDevices = onAiMenuAssistantAction(
                        AI_MENU_CONNECTIVITY_CHECK_TRAINER_ACTION,
                    )
                    if (shouldOpenDevices) {
                        onNavigateToDevices()
                    }
                },
            ) {
                Text(stringResource(R.string.ai_menu_action_open_device_setup))
            }
        }
    }
}

/** Single-column layout used on phones. */
@Composable
private fun SummaryAllItems(
    state: SummarySectionState,
    onAiMenuAssistantAction: (String) -> Boolean,
    onNavigateToDevices: () -> Unit,
    isCompact: Boolean = false,
) {
    val normalTextColor = menuNormalTextColor()

    if (!state.telemetryOnlySelected) {
        Text(
            text = stringResource(R.string.menu_setup_summary_hint),
            style = MaterialTheme.typography.bodySmall,
            color = normalTextColor,
        )
    }
    MenuSetupSummaryItem(
        label = stringResource(R.string.menu_setup_summary_mode),
        value = state.selectedWorkoutModeLabel,
    )
    if (!state.telemetryOnlySelected) {
        MenuSetupSummaryItem(
            label = stringResource(R.string.menu_setup_summary_workout),
            value = state.workoutDisplayName,
        )
    }
    MenuSetupSummaryItem(
        label = stringResource(R.string.menu_setup_summary_ftp),
        value = stringResource(R.string.menu_ftp_hint, state.ftpWatts),
    )
    MenuSetupSummaryItem(
        label = stringResource(R.string.menu_hr_profile_title),
        value = state.hrProfileSummary,
    )
    MenuSetupSummaryItem(
        label = stringResource(R.string.menu_trainer_device_label),
        value = formatDeviceSummaryValue(
            displayName = state.trainerDisplayName,
            statusSummary = state.trainerStatusSummary,
        ),
    )
    if (state.mockTrainerModeEnabled) {
        MenuSetupSummaryItem(
            label = stringResource(R.string.menu_mock_trainer_toggle_label),
            value = stringResource(R.string.menu_mock_mode_active),
        )
    }
    MenuSetupSummaryItem(
        label = stringResource(R.string.menu_hr_device_label),
        value = formatDeviceSummaryValue(
            displayName = state.hrDisplayName,
            statusSummary = state.hrStatusSummary,
        ),
    )
    if (!state.telemetryOnlySelected && state.stepCountText != null) {
        MenuSetupSummaryItem(
            label = stringResource(R.string.menu_setup_summary_steps),
            value = state.stepCountText,
        )
    }
    if (!state.telemetryOnlySelected && state.plannedTssText != null) {
        MenuSetupSummaryItem(
            label = stringResource(R.string.menu_setup_summary_tss),
            value = state.plannedTssValueText ?: state.plannedTssText,
        )
    }
    if (!state.telemetryOnlySelected && state.totalDurationText != null) {
        MenuSetupSummaryItem(
            label = stringResource(R.string.menu_setup_summary_duration),
            value = state.totalDurationText,
        )
    }
    if (state.statusText != null) {
        Text(
            text = state.statusText,
            style = MaterialTheme.typography.bodyMedium,
            color = state.statusTextColor,
        )
    }
    SummaryMessages(state, onAiMenuAssistantAction, onNavigateToDevices)
    val previewSegments = remember(
        state.selectedWorkout,
        state.selectedImportedWorkout,
        state.ftpWatts,
    ) {
        when {
            state.selectedWorkout != null -> buildWorkoutProfileSegments(state.selectedWorkout)
            state.selectedImportedWorkout != null ->
                buildWorkoutProfileSegments(state.selectedImportedWorkout, state.ftpWatts)
            else -> emptyList()
        }
    }
    if (!state.telemetryOnlySelected && previewSegments.isNotEmpty()) {
        WorkoutProfileChart(
            segments = previewSegments,
            ftpWatts = state.ftpWatts,
            modifier = Modifier.fillMaxWidth(),
            chartHeight = if (isCompact) 80.dp else 165.dp,
        )
    }
}

private fun formatDeviceSummaryValue(
    displayName: String,
    statusSummary: String,
): String {
    return if (displayName == statusSummary) {
        displayName
    } else {
        "$displayName • $statusSummary"
    }
}

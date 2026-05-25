package com.example.ergometerapp.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ergometerapp.R
import com.example.ergometerapp.SessionSetupMode

/**
 * Workout-mode step content for the menu setup flow.
 *
 * The mode choice stays adjacent to any mode-specific content so users can
 * still switch away quickly, but compact phone layouts may surface the active
 * structured-workout file controls first to keep the import CTA visible.
 */
@Composable
internal fun MenuWorkoutModeStepContent(
    selectedSessionSetupMode: SessionSetupMode,
    fileBasedState: FileBasedSectionState,
    onSelectStructuredMode: () -> Unit,
    onSelectTelemetryOnly: () -> Unit,
    onShowOpenWorkoutDialog: () -> Unit,
    onChooseDocumentsFolder: () -> Unit,
    onOpenEwoEditor: () -> Unit,
    onShowWorkoutFileDialog: () -> Unit,
    onShowWorkoutNameDialog: () -> Unit,
    onShowWorkoutDescriptionDialog: () -> Unit,
    isLandscape: Boolean = false,
    isCompact: Boolean = false,
) {
    val telemetryOnlySelected = selectedSessionSetupMode == SessionSetupMode.TELEMETRY_ONLY
    val structuredModeSelected = !telemetryOnlySelected
    val prioritizeFileCardOnPhone = fileBasedState.compactModeActionLayout && !isLandscape
    val showFileContentBeforeModeChoice =
        structuredModeSelected && (isLandscape || prioritizeFileCardOnPhone)

    when {
        structuredModeSelected -> {
            if (showFileContentBeforeModeChoice) {
                MenuFileBasedStepContent(
                    state = fileBasedState,
                    onShowOpenWorkoutDialog = onShowOpenWorkoutDialog,
                    onChooseDocumentsFolder = onChooseDocumentsFolder,
                    onOpenEwoEditor = onOpenEwoEditor,
                    onShowWorkoutFileDialog = onShowWorkoutFileDialog,
                    onShowWorkoutNameDialog = onShowWorkoutNameDialog,
                    onShowWorkoutDescriptionDialog = onShowWorkoutDescriptionDialog,
                    isLandscape = isLandscape,
                    isCompact = isCompact,
                )
            }
            WorkoutModeChoiceSection(
                structuredModeSelected = structuredModeSelected,
                telemetryOnlySelected = telemetryOnlySelected,
                onSelectStructuredMode = onSelectStructuredMode,
                onSelectTelemetryOnly = onSelectTelemetryOnly,
                isCompact = isCompact,
            )
            if (!showFileContentBeforeModeChoice) {
                MenuFileBasedStepContent(
                    state = fileBasedState,
                    onShowOpenWorkoutDialog = onShowOpenWorkoutDialog,
                    onChooseDocumentsFolder = onChooseDocumentsFolder,
                    onOpenEwoEditor = onOpenEwoEditor,
                    onShowWorkoutFileDialog = onShowWorkoutFileDialog,
                    onShowWorkoutNameDialog = onShowWorkoutNameDialog,
                    onShowWorkoutDescriptionDialog = onShowWorkoutDescriptionDialog,
                    isLandscape = false,
                    isCompact = isCompact,
                )
            }
        }

        telemetryOnlySelected -> {
            WorkoutModeChoiceSection(
                structuredModeSelected = structuredModeSelected,
                telemetryOnlySelected = telemetryOnlySelected,
                onSelectStructuredMode = onSelectStructuredMode,
                onSelectTelemetryOnly = onSelectTelemetryOnly,
                isCompact = isCompact,
            )
        }
    }
}

@Composable
private fun WorkoutModeChoiceSection(
    structuredModeSelected: Boolean,
    telemetryOnlySelected: Boolean,
    onSelectStructuredMode: () -> Unit,
    onSelectTelemetryOnly: () -> Unit,
    isCompact: Boolean,
) {
    SectionCard(title = stringResource(R.string.menu_setup_step_mode_title), compact = isCompact) {
        Text(
            text = stringResource(R.string.menu_setup_mode_hint),
            style = MaterialTheme.typography.bodySmall,
            color = menuNormalTextColor(),
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            WorkoutModeChoiceCard(
                title = stringResource(R.string.menu_setup_mode_structured_title),
                supportingText = stringResource(R.string.menu_setup_file_mode_hint),
                selected = structuredModeSelected,
                onClick = onSelectStructuredMode,
                compact = isCompact,
            )
            WorkoutModeChoiceCard(
                title = stringResource(R.string.menu_setup_mode_telemetry_only_title),
                supportingText = stringResource(R.string.menu_setup_mode_telemetry_only_summary),
                selected = telemetryOnlySelected,
                onClick = onSelectTelemetryOnly,
                compact = isCompact,
            )
        }
    }
}

@Composable
private fun WorkoutModeChoiceCard(
    title: String,
    supportingText: String,
    selected: Boolean,
    onClick: () -> Unit,
    compact: Boolean,
) {
    val accentColor = MaterialTheme.colorScheme.primary
    val outlineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)

    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) accentColor else outlineColor,
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurface,
            containerColor = if (selected) {
                accentColor.copy(alpha = 0.08f)
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = if (compact) {
                    MaterialTheme.typography.titleSmall
                } else {
                    MaterialTheme.typography.titleMedium
                },
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = supportingText,
                style = if (compact) {
                    MaterialTheme.typography.labelMedium
                } else {
                    MaterialTheme.typography.bodySmall
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (compact) 2 else 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

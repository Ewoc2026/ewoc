package com.example.ergometerapp.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ergometerapp.R
import com.example.ergometerapp.WebsiteGuideDestination
import com.example.ergometerapp.ui.components.WorkoutProfileChart
import com.example.ergometerapp.ui.components.buildWorkoutProfileSegments

/**
 * Structured workout source content within the workout-mode detail flow.
 *
 * Renders documents folder selection, workout file/editor actions, and workout
 * metadata info cards. In landscape mode, splits into two columns: source
 * actions on the left and workout preview on the right.
 */
@Composable
internal fun MenuFileBasedStepContent(
    state: FileBasedSectionState,
    onShowOpenWorkoutDialog: () -> Unit,
    onChooseDocumentsFolder: () -> Unit,
    onOpenEwoEditor: () -> Unit,
    onShowWorkoutFileDialog: () -> Unit,
    onShowWorkoutNameDialog: () -> Unit,
    onShowWorkoutDescriptionDialog: () -> Unit,
    isLandscape: Boolean = false,
    isCompact: Boolean = false,
) {
    if (isLandscape && state.hasSelectedWorkout) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(0.42f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SectionCard(title = stringResource(R.string.menu_setup_mode_file_title), compact = isCompact) {
                    FileBasedFolderAndActions(
                        state = state,
                        onShowOpenWorkoutDialog = onShowOpenWorkoutDialog,
                        onChooseDocumentsFolder = onChooseDocumentsFolder,
                        onOpenEwoEditor = onOpenEwoEditor,
                        onShowWorkoutFileDialog = onShowWorkoutFileDialog,
                    )
                }
            }
            Column(
                modifier = Modifier.weight(0.58f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FileBasedWorkoutPreview(
                    state = state,
                    onShowWorkoutNameDialog = onShowWorkoutNameDialog,
                    onShowWorkoutDescriptionDialog = onShowWorkoutDescriptionDialog,
                    metaBoxHeight = 110.dp,
                )
            }
        }
    } else {
        SectionCard(title = stringResource(R.string.menu_setup_mode_file_title), compact = isCompact) {
            FileBasedFolderAndActions(
                state = state,
                onShowOpenWorkoutDialog = onShowOpenWorkoutDialog,
                onChooseDocumentsFolder = onChooseDocumentsFolder,
                onOpenEwoEditor = onOpenEwoEditor,
                onShowWorkoutFileDialog = onShowWorkoutFileDialog,
            )
        }
        if (state.hasSelectedWorkout) {
            FileBasedWorkoutPreview(
                state = state,
                onShowWorkoutNameDialog = onShowWorkoutNameDialog,
                onShowWorkoutDescriptionDialog = onShowWorkoutDescriptionDialog,
            )
        }
    }
}

@Composable
private fun FileBasedFolderAndActions(
    state: FileBasedSectionState,
    onShowOpenWorkoutDialog: () -> Unit,
    onChooseDocumentsFolder: () -> Unit,
    onOpenEwoEditor: () -> Unit,
    onShowWorkoutFileDialog: () -> Unit,
) {
    val normalTextColor = menuNormalTextColor()
    val supportingTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val compactLayout = state.compactModeActionLayout
    val actionHeight = if (compactLayout) 36.dp else 40.dp
    val folderSummary = buildString {
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

    Text(
        text = stringResource(R.string.menu_setup_file_mode_hint),
        style = if (compactLayout) {
            MaterialTheme.typography.labelMedium
        } else {
            MaterialTheme.typography.bodySmall
        },
        color = normalTextColor,
    )
    Text(
        text = stringResource(R.string.menu_file_help_import_vs_editor),
        style = MaterialTheme.typography.bodySmall,
        color = supportingTextColor,
    )
    EnglishOnlyGuideLink(destination = WebsiteGuideDestination.DESKTOP_EDITOR)
    if (!state.selectedWorkoutImportError.isNullOrBlank()) {
        Text(
            text = stringResource(R.string.menu_file_error_unsupported_next_step),
            style = MaterialTheme.typography.bodySmall,
            color = supportingTextColor,
        )
    }
    Button(
        onClick = onShowOpenWorkoutDialog,
        modifier = Modifier
            .fillMaxWidth()
            .height(actionHeight)
            .testTag(DebugAutomationTags.MENU_FILE_SELECT_WORKOUT_FILE),
        colors = menuSecondaryButtonColors(),
    ) {
        Text(text = stringResource(R.string.menu_select_workout_file_short))
    }
    MenuInlineValueCard(
        value = state.workoutFileSummaryValue,
        onClick = if (state.selectedWorkoutFileName != null) {
            onShowWorkoutFileDialog
        } else {
            null
        },
        height = actionHeight,
    )
    MenuInlineValueCard(
        value = folderSummary,
        isError = state.documentsFolderStatusIsError,
        height = actionHeight,
    )
    if (!state.documentsFolderReady || state.documentsFolderAccessLost) {
        Text(
            text = stringResource(R.string.menu_file_help_folder_optional),
            style = MaterialTheme.typography.bodySmall,
            color = supportingTextColor,
        )
    }
    OutlinedButton(
        onClick = onChooseDocumentsFolder,
        modifier = Modifier
            .fillMaxWidth()
            .height(actionHeight)
            .testTag(DebugAutomationTags.MENU_FILE_LINK_FOLDER),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.primary,
        ),
    ) {
        Text(
            text = if (state.documentsFolderReady && !state.documentsFolderAccessLost) {
                stringResource(R.string.menu_documents_folder_reselect)
            } else {
                stringResource(R.string.menu_documents_folder_choose)
            },
        )
    }
    if (state.compactModeActionLayout) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = onOpenEwoEditor,
                modifier = Modifier.fillMaxWidth().height(actionHeight),
                colors = menuSecondaryButtonColors(),
            ) {
                Text(text = stringResource(R.string.menu_open_workout_editor))
            }
        }
    } else {
        Row(modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = onOpenEwoEditor,
                modifier = Modifier.fillMaxWidth().height(actionHeight),
                colors = menuSecondaryButtonColors(),
            ) {
                Text(
                    text = stringResource(R.string.menu_open_workout_editor),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
    if (!state.selectedWorkoutImportError.isNullOrBlank()) {
        Text(
            text = state.selectedWorkoutImportError,
            style = MaterialTheme.typography.bodySmall,
            color = menuErrorTextColor(),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(R.string.menu_file_error_unsupported_next_step),
            style = MaterialTheme.typography.bodySmall,
            color = supportingTextColor,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun FileBasedWorkoutPreview(
    state: FileBasedSectionState,
    onShowWorkoutNameDialog: () -> Unit,
    onShowWorkoutDescriptionDialog: () -> Unit,
    metaBoxHeight: Dp = 48.dp,
) {
    if (state.selectedWorkoutImportError == null) {
        Text(
            text = stringResource(R.string.menu_file_help_review_after_import),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        WorkoutMetaListBox(
            label = stringResource(R.string.menu_workout_name_tag),
            value = state.workoutNameTagValue,
            onClick = onShowWorkoutNameDialog,
            modifier = Modifier.weight(1f),
            boxHeight = metaBoxHeight,
        )
        WorkoutMetaListBox(
            label = stringResource(R.string.menu_workout_description_tag),
            value = state.workoutDescriptionTagValue,
            onClick = onShowWorkoutDescriptionDialog,
            modifier = Modifier.weight(1f),
            boxHeight = metaBoxHeight,
        )
    }
    if (state.workoutMetricsLine != null) {
        Text(
            text = state.workoutMetricsLine,
            style = MaterialTheme.typography.bodySmall,
            color = menuNormalTextColor(),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
    }
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
            chartHeight = 165.dp,
        )
    }
}

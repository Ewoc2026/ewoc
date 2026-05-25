package com.example.ergometerapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ergometerapp.BuiltInWorkoutOption
import com.example.ergometerapp.DocumentsFolderWorkoutOption
import com.example.ergometerapp.R

/**
 * Simple info dialog showing the selected workout file name.
 */
@Composable
internal fun MenuWorkoutFileDialog(
    workoutFileDisplayName: String,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.menu_workout_file_dialog_title)) },
        text = { Text(workoutFileDisplayName) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.menu_dialog_ok))
            }
        },
    )
}

/**
 * Simple info dialog showing the workout name/title tag.
 */
@Composable
internal fun MenuWorkoutNameDialog(
    workoutNameTagValue: String,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.menu_workout_name_tag)) },
        text = { Text(workoutNameTagValue) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.menu_dialog_ok))
            }
        },
    )
}

/**
 * Simple info dialog showing the workout description tag.
 */
@Composable
internal fun MenuWorkoutDescriptionDialog(
    workoutDescriptionTagValue: String,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.menu_workout_description_tag)) },
        text = { Text(workoutDescriptionTagValue) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.menu_dialog_ok))
            }
        },
    )
}

/**
 * Dialog for choosing how to import a structured workout file.
 */
@Composable
internal fun MenuOpenWorkoutDialog(
    linkedFolderReady: Boolean,
    linkedFolderSummary: String,
    onOpenWorkoutFile: () -> Unit,
    onOpenFromLinkedFolder: () -> Unit,
    onOpenBuiltInWorkouts: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.menu_workout_import_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onOpenFromLinkedFolder,
                    enabled = linkedFolderReady,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(DebugAutomationTags.MENU_FILE_IMPORT_FROM_FOLDER),
                ) {
                    Text(stringResource(R.string.menu_documents_folder_import_workout))
                }
                OutlinedButton(
                    onClick = onOpenWorkoutFile,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.menu_workout_import_browse_files))
                }
                OutlinedButton(
                    onClick = onOpenBuiltInWorkouts,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.menu_workout_import_built_in_workouts))
                }
                Text(
                    text = linkedFolderSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.menu_dialog_ok))
            }
        },
    )
}

/**
 * Dialog listing bundled workouts packaged inside the APK.
 *
 * Built-in workouts stay read-only; opening one in the editor is only a
 * convenience for creating a saved copy in the user's own folder.
 */
@Composable
internal fun MenuBuiltInWorkoutDialog(
    builtInWorkoutFiles: List<BuiltInWorkoutOption>,
    onSelectWorkout: (String) -> Unit,
    onOpenInEditor: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.menu_built_in_workout_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.menu_built_in_workout_dialog_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (builtInWorkoutFiles.isEmpty()) {
                    Text(
                        text = stringResource(R.string.menu_built_in_workout_empty),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(
                            items = builtInWorkoutFiles,
                            key = { entry -> entry.assetPath },
                        ) { entry ->
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = entry.displayName,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            onSelectWorkout(entry.assetPath)
                                            onDismiss()
                                        },
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text(stringResource(R.string.menu_built_in_workout_use))
                                    }
                                    TextButton(
                                        onClick = {
                                            onOpenInEditor(entry.assetPath)
                                            onDismiss()
                                        },
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text(stringResource(R.string.menu_built_in_workout_edit))
                                    }
                                }
                            }
                        }
                    }
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

/**
 * Dialog listing workout files from the Documents folder for selection.
 */
@Composable
internal fun MenuDocumentsFolderWorkoutDialog(
    documentsFolderWorkoutFiles: List<DocumentsFolderWorkoutOption>,
    onSelectWorkout: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.menu_documents_folder_import_dialog_title)) },
        text = {
            if (documentsFolderWorkoutFiles.isEmpty()) {
                Text(
                    text = stringResource(R.string.menu_documents_folder_no_zwo_files),
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(
                        items = documentsFolderWorkoutFiles,
                        key = { entry -> entry.uriString },
                    ) { entry ->
                        OutlinedButton(
                            onClick = {
                                onSelectWorkout(entry.uriString)
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = entry.displayName,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
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

/**
 * Dialog showing a BLE connection issue with contextual action buttons.
 */
@Composable
internal fun MenuConnectionIssueDialog(
    message: String,
    suggestTrainerSearch: Boolean,
    suggestOpenSettings: Boolean,
    onSearchAgain: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.menu_connection_issue_title)) },
        text = { Text(message) },
        confirmButton = {
            val confirmLabel = if (suggestOpenSettings) {
                stringResource(R.string.menu_connection_issue_open_settings)
            } else {
                stringResource(R.string.menu_connection_issue_search_again)
            }
            val confirmAction = if (suggestOpenSettings) {
                onOpenSettings
            } else {
                onSearchAgain
            }
            TextButton(onClick = confirmAction) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.menu_connection_issue_dismiss))
            }
        },
    )
}

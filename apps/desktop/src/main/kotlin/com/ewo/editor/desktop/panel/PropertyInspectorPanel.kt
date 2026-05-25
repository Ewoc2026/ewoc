package com.ewo.editor.desktop.panel

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ewo.core.HrReference
import com.ewo.editor.commands.*
import com.ewo.editor.model.*
import com.ewo.editor.desktop.EditorStrings
import com.ewo.editor.desktop.state.CanonicalTagInputPolicy
import com.ewo.editor.desktop.state.EditorState
import com.ewo.editor.desktop.theme.SegmentColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.window.PopupProperties
import kotlin.math.roundToInt

@Composable
fun PropertyInspectorPanel(state: EditorState) {
    val doc = state.document
    val selectedId = doc.selectedNodeId

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        // Selected segment properties
        if (selectedId != null) {
            val segment = doc.findSegment(selectedId)
            if (segment != null) {
                val segColor = when (segment) {
                    is EditorSegment.Steady -> SegmentColors.steady
                    is EditorSegment.Ramp -> SegmentColors.ramp
                    is EditorSegment.FreeRide -> SegmentColors.freeRide
                    is EditorSegment.Repeat -> SegmentColors.repeat
                }
                Text(
                    "Selected step",
                    style = MaterialTheme.typography.titleMedium,
                    color = segColor,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                SegmentProperties(state, segment)
            } else {
                Text(
                    "Selected node not found",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        } else {
            Text(
                "Select a step to edit its settings",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        WorkoutSupportPanels(state, doc)
    }
}

@Composable
fun WorkoutMetadataPanel(state: EditorState, modifier: Modifier = Modifier) {
    val doc = state.document
    var expanded by remember { mutableStateOf(true) }
    val titleValidationMessage = remember(doc.validationMarkers) {
        doc.validationMarkers
            .firstOrNull { it.field == "title" && it.blocksExport }
            ?.message
    }

    if (!expanded) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            tonalElevation = 1.dp,
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    doc.title.ifBlank { "Untitled" },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { expanded = true }) {
                    Text("Show details")
                }
            }
        }
        return
    }

    EditorSectionCard(
        title = "Workout details",
        supportingText = "Edit workout-wide metadata here, then hide this panel to keep the chart and step editor in focus.",
        modifier = modifier,
        headerActions = {
            TextButton(onClick = { expanded = false }) {
                Text("Hide details")
            }
        },
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            if (maxWidth >= 900.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    WorkoutTitleField(
                        title = doc.title,
                        validationMessage = titleValidationMessage,
                        modifier = Modifier.weight(1f),
                        onValueChange = { state.dispatch(SetWorkoutTitle(it)) },
                    )
                    OutlinedTextField(
                        value = doc.description,
                        onValueChange = { state.dispatch(SetWorkoutDescription(it)) },
                        label = { Text("Description") },
                        minLines = 1,
                        maxLines = 3,
                        modifier = Modifier.weight(2f),
                    )
                    CanonicalTagField(
                        state = state,
                        tags = doc.tags,
                        modifier = Modifier.weight(1.2f),
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    WorkoutTitleField(
                        title = doc.title,
                        validationMessage = titleValidationMessage,
                        modifier = Modifier.fillMaxWidth(),
                        onValueChange = { state.dispatch(SetWorkoutTitle(it)) },
                    )
                    OutlinedTextField(
                        value = doc.description,
                        onValueChange = { state.dispatch(SetWorkoutDescription(it)) },
                        label = { Text("Description") },
                        minLines = 1,
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    CanonicalTagField(
                        state = state,
                        tags = doc.tags,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        LocalizedMetadataSection(state, doc)
        Spacer(Modifier.height(12.dp))
        MetadataFieldsSection(state, doc)
    }
}

@Composable
private fun WorkoutSupportPanels(state: EditorState, doc: EditorWorkoutDocument) {
    val ftpFocusRequester = remember { FocusRequester() }
    val previewNeedsFtp = state.ftpWatts == null &&
        state.preview.compileErrors.isNotEmpty() &&
        state.preview.chartPowerUnit == ChartPowerUnit.FTP_PERCENT
    val previewProfileNeeded = state.preview.compileErrors.isNotEmpty()
    var previewProfileExpanded by remember(previewProfileNeeded) { mutableStateOf(previewProfileNeeded) }
    var controlSettingsExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(previewProfileNeeded) {
        if (previewProfileNeeded) {
            previewProfileExpanded = true
        }
    }

    if (previewProfileExpanded) {
        EditorSectionCard(
            title = "Preview rider profile",
            supportingText = "These values are only for preview calculations. Use them when the workout depends on FTP or relative heart-rate references such as HR max, heart-rate reserve, or threshold heart rate. They are not saved into the workout file.",
            headerActions = {
                TextButton(onClick = { previewProfileExpanded = false }) {
                    Text("Hide profile")
                }
            },
        ) {
            if (previewNeedsFtp) {
                PreviewRequirementBanner(
                    title = "This workout needs FTP for a full preview",
                    message = "Add FTP in watts to resolve FTP-based targets and show the workout preview correctly.",
                    actionLabel = "Set FTP now",
                    onAction = { ftpFocusRequester.requestFocus() },
                )
                Spacer(Modifier.height(10.dp))
            }
            if (previewProfileNeeded && state.preview.compileErrors.isNotEmpty()) {
                Text(
                    "Relative HR targets often need preview profile context before the chart and metrics can resolve cleanly. That does not automatically mean the workout structure itself is invalid.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
            }
            AthleteProfileSection(
                state = state,
                showHeader = false,
                ftpFocusRequester = ftpFocusRequester,
            )
        }
    } else {
        CollapsedSupportPanel(
            title = "Preview rider profile",
            summary = previewProfileStatusText(state),
            actionLabel = "Show profile",
            onExpand = { previewProfileExpanded = true },
        )
    }

    Spacer(Modifier.height(12.dp))

    if (controlSettingsExpanded) {
        EditorSectionCard(
            title = "Heart rate control settings",
            supportingText = "These workout-level safety limits guide trainer power during heart-rate-controlled steps.",
            headerActions = {
                TextButton(onClick = { controlSettingsExpanded = false }) {
                    Text("Hide settings")
                }
            },
        ) {
            ControlBlockEditor(state, doc, showHeader = false)
        }
    } else {
        CollapsedSupportPanel(
            title = "Heart rate control settings",
            summary = controlSettingsStatusText(doc),
            actionLabel = "Show settings",
            onExpand = { controlSettingsExpanded = true },
        )
    }
}

@Composable
private fun CollapsedSupportPanel(
    title: String,
    summary: String,
    actionLabel: String,
    onExpand: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 220.dp),
            )
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = onExpand,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
            ) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
private fun WorkoutTitleField(
    title: String,
    validationMessage: String?,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit,
) {
    Column(modifier = modifier) {
        OutlinedTextField(
            value = title,
            onValueChange = onValueChange,
            label = { Text("Title") },
            minLines = 1,
            maxLines = 3,
            isError = validationMessage != null,
            modifier = Modifier.fillMaxWidth(),
        )
        if (validationMessage != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                "Add a workout title before export.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun EditorSectionCard(
    title: String,
    supportingText: String? = null,
    modifier: Modifier = Modifier,
    headerActions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                headerActions()
            }
            if (supportingText != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    supportingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun PreviewRequirementBanner(
    title: String,
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onErrorContainer,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(Modifier.height(8.dp))
            FilledTonalButton(onClick = onAction) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
private fun MetadataFieldsSection(state: EditorState, doc: EditorWorkoutDocument) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        when {
            maxWidth >= 1250.dp -> {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        VersionMetadataField(doc.version, Modifier.weight(0.7f))
                        OutlinedTextField(
                            value = doc.difficulty ?: "",
                            onValueChange = { state.dispatch(SetDifficulty(it.ifBlank { null })) },
                            label = { Text("Difficulty note") },
                            singleLine = true,
                            modifier = Modifier.weight(1.1f),
                        )
                        OutlinedTextField(
                            value = doc.uid ?: "",
                            onValueChange = { state.dispatch(SetWorkoutUid(it.ifBlank { null })) },
                            label = { Text("Internal ID") },
                            singleLine = true,
                            modifier = Modifier.weight(1.5f),
                        )
                        OutlinedTextField(
                            value = doc.revision?.toString() ?: "",
                            onValueChange = { state.dispatch(SetWorkoutRevision(it.toIntOrNull())) },
                            label = { Text("Revision number") },
                            singleLine = true,
                            modifier = Modifier.weight(0.9f),
                        )
                    }
                }
            }

            maxWidth >= 900.dp -> {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        VersionMetadataField(doc.version, Modifier.weight(0.7f))
                        OutlinedTextField(
                            value = doc.difficulty ?: "",
                            onValueChange = { state.dispatch(SetDifficulty(it.ifBlank { null })) },
                            label = { Text("Difficulty note") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = doc.uid ?: "",
                            onValueChange = { state.dispatch(SetWorkoutUid(it.ifBlank { null })) },
                            label = { Text("Internal ID") },
                            singleLine = true,
                            modifier = Modifier.weight(1.4f),
                        )
                    }
                    Spacer(Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = doc.revision?.toString() ?: "",
                            onValueChange = { state.dispatch(SetWorkoutRevision(it.toIntOrNull())) },
                            label = { Text("Revision number") },
                            singleLine = true,
                            modifier = Modifier.weight(0.9f),
                        )
                    }
                }
            }

            maxWidth >= 700.dp -> {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        VersionMetadataField(doc.version, Modifier.weight(0.8f))
                        OutlinedTextField(
                            value = doc.difficulty ?: "",
                            onValueChange = { state.dispatch(SetDifficulty(it.ifBlank { null })) },
                            label = { Text("Difficulty note") },
                            singleLine = true,
                            modifier = Modifier.weight(1.2f),
                        )
                    }
                    Spacer(Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = doc.uid ?: "",
                            onValueChange = { state.dispatch(SetWorkoutUid(it.ifBlank { null })) },
                            label = { Text("Internal ID") },
                            singleLine = true,
                            modifier = Modifier.weight(1.7f),
                        )
                        OutlinedTextField(
                            value = doc.revision?.toString() ?: "",
                            onValueChange = { state.dispatch(SetWorkoutRevision(it.toIntOrNull())) },
                            label = { Text("Revision number") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            else -> {
                Column(modifier = Modifier.fillMaxWidth()) {
                    VersionMetadataField(doc.version, Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = doc.difficulty ?: "",
                        onValueChange = { state.dispatch(SetDifficulty(it.ifBlank { null })) },
                        label = { Text("Difficulty note") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = doc.uid ?: "",
                        onValueChange = { state.dispatch(SetWorkoutUid(it.ifBlank { null })) },
                        label = { Text("Internal ID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = doc.revision?.toString() ?: "",
                        onValueChange = { state.dispatch(SetWorkoutRevision(it.toIntOrNull())) },
                        label = { Text("Revision number") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun VersionMetadataField(value: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = {},
        readOnly = true,
        label = { Text("Version") },
        singleLine = true,
        modifier = modifier,
        colors = OutlinedTextFieldDefaults.colors(
            disabledBorderColor = MaterialTheme.colorScheme.outlineVariant,
            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            disabledTextColor = MaterialTheme.colorScheme.onSurface,
        ),
    )
}

@Composable
private fun CanonicalTagField(
    state: EditorState,
    tags: List<String>,
    modifier: Modifier = Modifier,
) {
    var rawText by remember(tags) { mutableStateOf(CanonicalTagInputPolicy.format(tags)) }
    var errorText by remember(tags) { mutableStateOf<String?>(null) }

    OutlinedTextField(
        value = rawText,
        onValueChange = { proposedText ->
            when (val evaluation = CanonicalTagInputPolicy.evaluate(proposedText)) {
                is CanonicalTagInputPolicy.Evaluation.Accepted -> {
                    rawText = evaluation.rawText
                    errorText = null
                    if (evaluation.tags != tags) {
                        state.dispatch(SetTags(evaluation.tags))
                    }
                }

                is CanonicalTagInputPolicy.Evaluation.Rejected -> {
                    errorText = evaluation.message
                }
            }
        },
        label = { Text(EditorStrings.tagFieldLabel) },
        supportingText = { Text(errorText ?: CanonicalTagInputPolicy.guidanceText()) },
        isError = errorText != null,
        singleLine = true,
        modifier = modifier,
    )
}

/** EWO v1.6 localized root metadata editing. */
@Composable
private fun LocalizedMetadataSection(state: EditorState, doc: EditorWorkoutDocument) {
    var expanded by remember { mutableStateOf(doc.titleLocalized != null || doc.descriptionLocalized != null) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Translate, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(6.dp))
        Text(
            "Translations",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.weight(1f))
        if (!expanded) {
            TextButton(onClick = {
                expanded = true
                // Initialize both localized fields from current title/description
                if (doc.titleLocalized == null && doc.title.isNotBlank()) {
                    state.dispatch(SetTitleLocalized(EditorLocalizedText(doc.title, emptyMap())))
                }
                if (doc.descriptionLocalized == null && doc.description.isNotBlank()) {
                    state.dispatch(SetDescriptionLocalized(EditorLocalizedText(doc.description, emptyMap())))
                }
            }) { Text("Add") }
        }
    }

    if (expanded) {
        Spacer(Modifier.height(4.dp))
        Text(
            "Add translated title and description text for each language. The default text stays in sync with the main workout fields.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))

        val usedLanguages = remember(doc) { collectUsedLanguages(doc) }

        // Title localized
        LocalizedTextEditor(
            label = "Title translations",
            localized = doc.titleLocalized,
            fallbackDefault = doc.title,
            usedLanguages = usedLanguages,
            onChanged = { state.dispatch(SetTitleLocalized(it)) },
            onRemoved = {
                state.dispatch(SetTitleLocalized(null))
                if (doc.descriptionLocalized == null) expanded = false
            },
        )

        Spacer(Modifier.height(8.dp))

        // Description localized
        if (doc.description.isNotBlank() || doc.descriptionLocalized != null) {
            LocalizedTextEditor(
                label = "Description translations",
                localized = doc.descriptionLocalized,
                fallbackDefault = doc.description,
                usedLanguages = usedLanguages,
                onChanged = { state.dispatch(SetDescriptionLocalized(it)) },
                onRemoved = {
                    state.dispatch(SetDescriptionLocalized(null))
                    if (doc.titleLocalized == null) expanded = false
                },
            )
        }
    }
}

@Composable
private fun LocalizedTextEditor(
    label: String,
    localized: EditorLocalizedText?,
    fallbackDefault: String,
    usedLanguages: Set<String>,
    onChanged: (EditorLocalizedText) -> Unit,
    onRemoved: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                if (localized != null) {
                    TextButton(onClick = onRemoved) { Text("Remove") }
                } else {
                    TextButton(onClick = { onChanged(EditorLocalizedText(fallbackDefault, emptyMap())) }) {
                        Text("Enable")
                    }
                }
            }

            if (localized != null) {
                // Default text (read-only, synced from root field)
                OutlinedTextField(
                    value = localized.defaultText,
                    onValueChange = {},
                    label = { Text("Default text (from main field)") },
                    readOnly = true,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))

                // Existing translations
                localized.translations.forEach { (lang, text) ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = lang,
                            onValueChange = {},
                            label = { Text("Language") },
                            readOnly = true,
                            singleLine = true,
                            modifier = Modifier.width(72.dp),
                        )
                        OutlinedTextField(
                            value = text,
                            onValueChange = { newText ->
                                onChanged(localized.copy(translations = localized.translations + (lang to newText)))
                            },
                            label = { Text("Translated text") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = {
                            onChanged(localized.copy(translations = localized.translations - lang))
                        }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Delete, "Remove", modifier = Modifier.size(16.dp))
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                }

                // Add translation row
                var newLang by remember(localized) { mutableStateOf("") }
                var newText by remember(localized) { mutableStateOf("") }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LanguageCodePicker(
                        value = newLang,
                        onValueChange = { newLang = it },
                        usedLanguages = usedLanguages,
                        excludeLanguages = localized.translations.keys,
                    )
                    OutlinedTextField(
                        value = newText,
                        onValueChange = { newText = it },
                        label = { Text("Translated text") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = {
                            if (newLang.isNotBlank() && newText.isNotBlank()) {
                                onChanged(localized.copy(translations = localized.translations + (newLang.trim() to newText)))
                                newLang = ""
                                newText = ""
                            }
                        },
                        enabled = newLang.isNotBlank() && newText.isNotBlank(),
                    ) { Text("Add") }
                }
            }
        }
    }
}

@Composable
private fun AthleteProfileSection(
    state: EditorState,
    showHeader: Boolean = true,
    ftpFocusRequester: FocusRequester? = null,
) {
    if (showHeader) {
        Text(
            "Preview rider profile",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Used only for preview calculations. This is not saved into the workout file.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = state.ftpWatts?.toString() ?: "",
            onValueChange = {
                state.updatePreviewFtpWatts(it.toIntOrNull())
            },
            label = { Text("FTP in watts") },
            singleLine = true,
            modifier = Modifier
                .weight(1f)
                .then(if (ftpFocusRequester != null) Modifier.focusRequester(ftpFocusRequester) else Modifier),
        )
        OutlinedTextField(
            value = state.hrMaxBpm?.toString() ?: "",
            onValueChange = {
                state.updatePreviewHrMaxBpm(it.toIntOrNull())
            },
            label = { Text("Max heart rate") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = state.restingHrBpm?.toString() ?: "",
            onValueChange = {
                state.updatePreviewRestingHrBpm(it.toIntOrNull())
            },
            label = { Text("Resting heart rate") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = state.lthrBpm?.toString() ?: "",
            onValueChange = {
                state.updatePreviewLthrBpm(it.toIntOrNull())
            },
            label = { Text("Threshold heart rate") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SegmentProperties(state: EditorState, segment: EditorSegment) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = segment.label ?: "",
            onValueChange = { state.dispatch(SetSegmentLabel(segment.nodeId, it.ifBlank { null })) },
            label = { Text("Internal label") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = segment.note ?: "",
            onValueChange = { state.dispatch(SetSegmentNote(segment.nodeId, it.ifBlank { null })) },
            label = { Text("Shown to rider") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
    }
    Spacer(Modifier.height(8.dp))

    when (segment) {
        is EditorSegment.Steady -> SteadyProperties(state, segment)
        is EditorSegment.Ramp -> RampProperties(state, segment)
        is EditorSegment.FreeRide -> FreeRideProperties(state, segment)
        is EditorSegment.Repeat -> RepeatProperties(state, segment)
    }

    // Messages section
    Spacer(Modifier.height(16.dp))
    MessagesSection(state, segment)
}

@Composable
private fun SteadyProperties(state: EditorState, segment: EditorSegment.Steady) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DurationField(segment.durationSec, modifier = Modifier.weight(1f)) {
            state.dispatch(SetSegmentDuration(segment.nodeId, it))
        }
        TargetValueField(segment.target, modifier = Modifier.weight(1f)) { newTarget ->
            state.dispatch(SetSteadyTarget(segment.nodeId, newTarget))
        }
    }
    Spacer(Modifier.height(8.dp))

    TargetTypeAndCadenceRow(
        target = segment.target,
        cadence = segment.cadence,
        onTargetChanged = { state.dispatch(SetSteadyTarget(segment.nodeId, it)) },
        onCadenceChanged = { state.dispatch(SetCadence(segment.nodeId, it)) },
    )
}

@Composable
private fun RampProperties(state: EditorState, segment: EditorSegment.Ramp) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        DurationField(
            durationSec = segment.durationSec,
            modifier = Modifier.weight(1f),
            headerMinHeight = 24.dp,
        ) {
            state.dispatch(SetSegmentDuration(segment.nodeId, it))
        }
        RampTargetFieldRow(
            state = state,
            segment = segment,
            modifier = Modifier.weight(1f),
            headerMinHeight = 24.dp,
        )
    }
    Spacer(Modifier.height(8.dp))

    TargetTypeAndCadenceRow(
        target = segment.fromTarget,
        cadence = segment.cadence,
        allowHr = false,
        onTargetChanged = {
            val (from, to) = defaultRampTargetsForType(
                when (it) {
                    is EditorTarget.Power -> "W"
                    is EditorTarget.FtpPercent -> "%FTP"
                    else -> "%FTP"
                },
            )
            state.dispatch(SetRampTargets(segment.nodeId, from, to))
        },
        onCadenceChanged = { state.dispatch(SetCadence(segment.nodeId, it)) },
    )
}

@Composable
private fun FreeRideProperties(state: EditorState, segment: EditorSegment.FreeRide) {
    Text(
        "Free Ride authoring is disabled in the desktop editor. Replace this segment with structured steps before using the workout for app import.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        "Current segment remains visible for inspection only.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun RepeatProperties(state: EditorState, segment: EditorSegment.Repeat) {
    OutlinedTextField(
        value = segment.count.toString(),
        onValueChange = { text ->
            val count = text.toIntOrNull() ?: return@OutlinedTextField
            if (count >= 1) state.dispatch(SetRepeatCount(segment.nodeId, count))
        },
        label = { Text("How many times to repeat") },
        singleLine = true,
        modifier = Modifier.width(100.dp),
    )
}

@Composable
private fun DurationField(durationSec: Int, modifier: Modifier = Modifier, onDurationChanged: (Int) -> Unit) {
    val minutes = durationSec / 60
    val seconds = durationSec % 60

    DurationField(
        durationSec = durationSec,
        modifier = modifier,
        headerMinHeight = Dp.Unspecified,
        onDurationChanged = onDurationChanged,
    )
}

@Composable
private fun DurationField(
    durationSec: Int,
    modifier: Modifier = Modifier,
    headerMinHeight: Dp,
    onDurationChanged: (Int) -> Unit,
) {
    val minutes = durationSec / 60
    val seconds = durationSec % 60

    Column(modifier = modifier) {
        Box(
            modifier = if (headerMinHeight == Dp.Unspecified) {
                Modifier.fillMaxWidth()
            } else {
                Modifier.fillMaxWidth().heightIn(min = headerMinHeight)
            },
            contentAlignment = Alignment.BottomStart,
        ) {
            Text(
                "Step duration",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedTextField(
                value = minutes.toString(),
                onValueChange = { text ->
                    val min = text.toIntOrNull() ?: return@OutlinedTextField
                    onDurationChanged(min * 60 + seconds)
                },
                label = { Text("Minutes") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = seconds.toString(),
                onValueChange = { text ->
                    val sec = text.toIntOrNull() ?: return@OutlinedTextField
                    onDurationChanged(minutes * 60 + sec)
                },
                label = { Text("Seconds") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun RampTargetFieldRow(state: EditorState, segment: EditorSegment.Ramp, modifier: Modifier = Modifier) {
    RampTargetFieldRow(
        state = state,
        segment = segment,
        modifier = modifier,
        headerMinHeight = Dp.Unspecified,
    )
}

@Composable
private fun RampTargetFieldRow(
    state: EditorState,
    segment: EditorSegment.Ramp,
    modifier: Modifier = Modifier,
    headerMinHeight: Dp,
) {
    val unit = when (segment.fromTarget) {
        is EditorTarget.Power -> "W"
        is EditorTarget.FtpPercent -> "% FTP"
        else -> ""
    }

    Column(modifier = modifier) {
        Box(
            modifier = if (headerMinHeight == Dp.Unspecified) {
                Modifier.fillMaxWidth()
            } else {
                Modifier.fillMaxWidth().heightIn(min = headerMinHeight)
            },
            contentAlignment = Alignment.BottomStart,
        ) {
            Text(
                "Start -> finish target ($unit)",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RampValueField(segment.fromTarget, "Start", Modifier.weight(1f)) { fromTarget ->
                state.dispatch(SetRampTargets(segment.nodeId, fromTarget, segment.toTarget ?: fromTarget))
            }
            Text("→", style = MaterialTheme.typography.bodyMedium)
            RampValueField(segment.toTarget, "Finish", Modifier.weight(1f)) { toTarget ->
                state.dispatch(SetRampTargets(segment.nodeId, segment.fromTarget ?: toTarget, toTarget))
            }
        }
    }
}

private fun previewProfileStatusText(state: EditorState): String = when {
    state.preview.compileErrors.isNotEmpty() ->
        "Preview profile is needed: ${state.preview.compileErrors.first()}"
    listOf(state.ftpWatts, state.hrMaxBpm, state.restingHrBpm, state.lthrBpm).any { it != null } ->
        "Preview profile has values set."
    else ->
        "No preview profile values set."
}

private fun controlSettingsStatusText(doc: EditorWorkoutDocument): String {
    val control = doc.control ?: return "Not configured. Only needed for HR-based workouts."
    return "Power ${control.initialPowerWatts} W, range ${control.minPowerWatts}-${control.maxPowerWatts} W, cap ${control.hrUpperCapBpm} bpm."
}

/** Compact inline editor for a single target value (Watts or % FTP). */
@Composable
private fun TargetValueField(target: EditorTarget?, modifier: Modifier = Modifier, onTargetChanged: (EditorTarget) -> Unit) {
    Column(modifier = modifier) {
        val label = when (target) {
            is EditorTarget.Power -> "Power target"
            is EditorTarget.FtpPercent -> "FTP-based target"
            is EditorTarget.HeartRate -> "Heart rate target"
            is EditorTarget.HeartRateRelative -> "Relative heart rate target"
            null -> "Target"
        }
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(4.dp))
        when (target) {
            is EditorTarget.Power -> {
                OutlinedTextField(
                    value = target.watts.toString(),
                    onValueChange = { text ->
                        val watts = text.toIntOrNull() ?: return@OutlinedTextField
                        onTargetChanged(EditorTarget.Power(watts))
                    },
                    label = { Text("W") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            is EditorTarget.FtpPercent -> {
                OutlinedTextField(
                    value = ((target.fraction * 100).toInt()).toString(),
                    onValueChange = { text ->
                        val pct = text.toIntOrNull() ?: return@OutlinedTextField
                        onTargetChanged(EditorTarget.FtpPercent(pct / 100.0))
                    },
                    label = { Text("%") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            is EditorTarget.HeartRate -> {
                HeartRateBandField(
                    value = formatIntBand(target.lowBpm, target.highBpm),
                    label = "BPM band",
                    placeholder = "130-150",
                    modifier = Modifier.fillMaxWidth(),
                ) { low, high ->
                    onTargetChanged(EditorTarget.HeartRate(low, high))
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Use HR for an absolute working band when you already know the target in bpm.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            is EditorTarget.HeartRateRelative -> {
                HeartRateBandField(
                    value = formatIntBand(
                        (target.lowFraction * 100).roundToInt(),
                        (target.highFraction * 100).roundToInt(),
                    ),
                    label = "Target band %",
                    placeholder = "72-80",
                    modifier = Modifier.fillMaxWidth(),
                ) { low, high ->
                    onTargetChanged(
                        target.copy(
                            lowFraction = low / 100.0,
                            highFraction = high / 100.0,
                        ),
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Use HR% when the band should be relative to the selected reference point instead of fixed bpm.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            null -> {
                Text("—", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}


private fun defaultRampTargetsForType(type: String): Pair<EditorTarget, EditorTarget> = when (type) {
    "W" -> EditorTarget.Power(150) to EditorTarget.Power(250)
    "%FTP" -> EditorTarget.FtpPercent(0.45) to EditorTarget.FtpPercent(0.65)
    else -> EditorTarget.FtpPercent(0.45) to EditorTarget.FtpPercent(0.65)
}

private fun defaultTargetForType(type: String): EditorTarget = when (type) {
    "W" -> EditorTarget.Power(200)
    "%FTP" -> EditorTarget.FtpPercent(0.75)
    "HR" -> EditorTarget.HeartRate(130, 150)
    "HR%" -> EditorTarget.HeartRateRelative(HrReference.entries.first(), 0.70, 0.80)
    else -> EditorTarget.FtpPercent(0.75)
}

/** Compact single-value field for ramp from/to. */
@Composable
private fun RampValueField(
    target: EditorTarget?,
    label: String,
    modifier: Modifier = Modifier,
    onTargetChanged: (EditorTarget) -> Unit,
) {
    when (target) {
        is EditorTarget.Power -> OutlinedTextField(
            value = target.watts.toString(),
            onValueChange = { text -> text.toIntOrNull()?.let { onTargetChanged(EditorTarget.Power(it)) } },
            label = { Text(label) },
            singleLine = true,
            modifier = modifier,
        )
        is EditorTarget.FtpPercent -> OutlinedTextField(
            value = ((target.fraction * 100).toInt()).toString(),
            onValueChange = { text -> text.toIntOrNull()?.let { onTargetChanged(EditorTarget.FtpPercent(it / 100.0)) } },
            label = { Text(label) },
            singleLine = true,
            modifier = modifier,
        )
        else -> Text("—", modifier = modifier)
    }
}

/** Target type buttons + inline cadence on the same row. */
@Composable
private fun TargetTypeAndCadenceRow(
    target: EditorTarget?,
    cadence: EditorCadenceRange?,
    allowHr: Boolean = true,
    onTargetChanged: (EditorTarget) -> Unit,
    onCadenceChanged: (EditorCadenceRange?) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Target type buttons
        val currentType = when (target) {
            is EditorTarget.Power -> "W"
            is EditorTarget.FtpPercent -> "%FTP"
            is EditorTarget.HeartRate -> "HR"
            is EditorTarget.HeartRateRelative -> "HR%"
            null -> null
        }
        val types = if (allowHr) listOf("W", "%FTP", "HR", "HR%") else listOf("W", "%FTP")
        types.forEach { type ->
            val isSelected = type == currentType
            Tip(EditorStrings.tooltipForTargetType(type)) {
                if (isSelected) {
                    Button(
                        onClick = {},
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    ) { Text(type, style = MaterialTheme.typography.labelSmall) }
                } else {
                    OutlinedButton(
                        onClick = { onTargetChanged(defaultTargetForType(type)) },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    ) { Text(type, style = MaterialTheme.typography.labelSmall) }
                }
            }
        }

        Spacer(Modifier.weight(1f))

        // Inline cadence
        InlineCadenceEditor(cadence, onCadenceChanged)

        // Balance spacing when cadence button is shown (centers it better)
        if (cadence == null) Spacer(Modifier.weight(1f))
    }

    // HR-relative reference selector below the row
    when (target) {
        is EditorTarget.Power, is EditorTarget.FtpPercent, is EditorTarget.HeartRate, null -> {}
        is EditorTarget.HeartRateRelative -> {
            Spacer(Modifier.height(4.dp))
            var refMenuExpanded by remember { mutableStateOf(false) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Reference point:", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.width(4.dp))
                Box {
                    OutlinedButton(
                        onClick = { refMenuExpanded = true },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    ) { Text(target.reference.stableCode, style = MaterialTheme.typography.labelSmall) }
                    DropdownMenu(
                        expanded = refMenuExpanded,
                        onDismissRequest = { refMenuExpanded = false },
                    ) {
                        HrReference.entries.forEach { ref ->
                            DropdownMenuItem(
                                text = { Text(ref.stableCode) },
                                onClick = {
                                    onTargetChanged(target.copy(reference = ref))
                                    refMenuExpanded = false
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeartRateBandField(
    value: String,
    label: String,
    placeholder: String,
    modifier: Modifier = Modifier,
    onBandChanged: (Int, Int) -> Unit,
) {
    var text by remember(value) { mutableStateOf(value) }

    OutlinedTextField(
        value = text,
        onValueChange = { updated ->
            text = updated
            parseIntBand(updated)?.let { (low, high) ->
                onBandChanged(low, high)
            }
        },
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        singleLine = true,
        modifier = modifier,
    )
}

private fun formatIntBand(low: Int, high: Int): String = "$low-$high"

private fun parseIntBand(text: String): Pair<Int, Int>? {
    val match = Regex("""^\s*(\d+)\s*[-–]\s*(\d+)\s*$""").matchEntire(text) ?: return null
    val low = match.groupValues[1].toIntOrNull() ?: return null
    val high = match.groupValues[2].toIntOrNull() ?: return null
    if (low > high) return null
    return low to high
}

/** Compact inline cadence: two small fields or an "Add" button. */
@Composable
private fun InlineCadenceEditor(cadence: EditorCadenceRange?, onChanged: (EditorCadenceRange?) -> Unit) {
    if (cadence != null) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = cadence.low.toString(),
                onValueChange = { text ->
                    val low = text.toIntOrNull() ?: return@OutlinedTextField
                    onChanged(EditorCadenceRange(low, cadence.high))
                },
                label = { Text("Low") },
                singleLine = true,
                modifier = Modifier.width(64.dp),
            )
            OutlinedTextField(
                value = cadence.high.toString(),
                onValueChange = { text ->
                    val high = text.toIntOrNull() ?: return@OutlinedTextField
                    onChanged(EditorCadenceRange(cadence.low, high))
                },
                label = { Text("High") },
                singleLine = true,
                modifier = Modifier.width(64.dp),
            )
            Text("rpm", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Tip(EditorStrings.tooltipCadenceClear) {
                IconButton(onClick = { onChanged(null) }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, "Clear cadence", modifier = Modifier.size(14.dp))
                }
            }
        }
    } else {
        Tip(EditorStrings.tooltipCadenceAdd) {
            FilledTonalButton(
                onClick = { onChanged(EditorCadenceRange(80, 100)) },
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            ) { Text("Add cadence") }
        }
    }
}

@Composable
private fun MessagesSection(state: EditorState, segment: EditorSegment) {
    Text("Coach messages", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(4.dp))

    val usedLanguages = remember(state.document) { collectUsedLanguages(state.document) }
    for (msg in segment.messages) {
        MessageCard(state, msg, usedLanguages)
        Spacer(Modifier.height(4.dp))
    }

    TextButton(onClick = {
        state.dispatch(AddMessage(
            parentNodeId = segment.nodeId,
            kind = "instruction",
            defaultText = "New coach message",
        ))
    }) {
        Text("Add message")
    }
}

@Composable
private fun MessageCard(state: EditorState, msg: EditorMessage, usedLanguages: Set<String>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(8.dp)) {
            OutlinedTextField(
                value = msg.defaultText,
                onValueChange = { state.dispatch(UpdateMessage(msg.nodeId, defaultText = it)) },
                label = { Text("Message text") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MessageKindPicker(
                    value = msg.kind,
                    onValueChange = { state.dispatch(UpdateMessage(msg.nodeId, kind = it)) },
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = msg.timing.offsetSec.toString(),
                    onValueChange = { text ->
                        val offset = text.toIntOrNull() ?: return@OutlinedTextField
                        state.dispatch(UpdateMessage(msg.nodeId, timing = msg.timing.copy(offsetSec = offset)))
                    },
                    label = { Text("Message time offset +/- (s)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(4.dp))
            // Anchor selector
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Start from:", style = MaterialTheme.typography.labelSmall)
                EditorMessageAnchor.entries.forEach { anchor ->
                    val isSelected = msg.timing.anchor == anchor
                    val anchorTooltip = if (anchor == EditorMessageAnchor.START) EditorStrings.tooltipAnchorStart else EditorStrings.tooltipAnchorEnd
                    Tip(anchorTooltip) {
                        if (isSelected) {
                            Button(
                                onClick = {},
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            ) { Text(anchor.name.lowercase(), style = MaterialTheme.typography.labelSmall) }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    state.dispatch(UpdateMessage(msg.nodeId, timing = msg.timing.copy(anchor = anchor)))
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            ) { Text(anchor.name.lowercase(), style = MaterialTheme.typography.labelSmall) }
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { state.dispatch(DeleteMessage(msg.nodeId)) }) {
                    Text("Remove")
                }
            }

            // Offset sanity warning
            val offsetWarning = when {
                msg.timing.anchor == EditorMessageAnchor.END && msg.timing.offsetSec > 0 ->
                    "Positive offset on END anchor — message would fire after the segment has ended."
                msg.timing.anchor == EditorMessageAnchor.START && msg.timing.offsetSec < 0 ->
                    "Negative offset on START anchor — message would fire before the segment has started."
                else -> null
            }
            if (offsetWarning != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    offsetWarning,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            // Translations
            if (msg.translations.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("Translations", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                msg.translations.forEach { (lang, text) ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = lang,
                            onValueChange = {},
                            label = { Text("Language") },
                            readOnly = true,
                            singleLine = true,
                            modifier = Modifier.width(64.dp),
                        )
                        OutlinedTextField(
                            value = text,
                            onValueChange = { newText ->
                                state.dispatch(UpdateMessage(msg.nodeId,
                                    translations = msg.translations + (lang to newText)))
                            },
                            label = { Text("Translated text") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = {
                            state.dispatch(UpdateMessage(msg.nodeId,
                                translations = msg.translations - lang))
                        }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Delete, "Remove translation",
                                modifier = Modifier.size(16.dp))
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                }
            }
            // Add translation row
            var newLang by remember(msg.nodeId) { mutableStateOf("") }
            var newTranslationText by remember(msg.nodeId) { mutableStateOf("") }
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LanguageCodePicker(
                    value = newLang,
                    onValueChange = { newLang = it },
                    usedLanguages = usedLanguages,
                    excludeLanguages = msg.translations.keys,
                )
                OutlinedTextField(
                    value = newTranslationText,
                    onValueChange = { newTranslationText = it },
                    label = { Text("Translated text") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = {
                        if (newLang.isNotBlank() && newTranslationText.isNotBlank()) {
                            state.dispatch(UpdateMessage(msg.nodeId,
                                translations = msg.translations + (newLang.trim() to newTranslationText)))
                            newLang = ""
                            newTranslationText = ""
                        }
                    },
                    enabled = newLang.isNotBlank() && newTranslationText.isNotBlank(),
                ) { Text("Add") }
            }
        }
    }
}

@Composable
private fun ControlBlockEditor(state: EditorState, doc: EditorWorkoutDocument, showHeader: Boolean = true) {
    if (showHeader) {
        Text("Heart rate control settings", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(4.dp))
    }
    val ctrl = doc.control
    if (ctrl != null) {
        if (showHeader) {
            Text(
                "These workout-level safety limits guide trainer power during heart-rate-controlled steps.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
        }
        Text(
            "Think of these as the allowed correction window around the HR target. They matter only when the workout should actively adjust trainer power during HR-controlled steps.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = ctrl.initialPowerWatts.toString(),
                onValueChange = { text ->
                    val v = text.toIntOrNull() ?: return@OutlinedTextField
                    state.dispatch(SetControl(ctrl.copy(initialPowerWatts = v)))
                },
                label = { Text("Starting power (W)") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = ctrl.minPowerWatts.toString(),
                onValueChange = { text ->
                    val v = text.toIntOrNull() ?: return@OutlinedTextField
                    state.dispatch(SetControl(ctrl.copy(minPowerWatts = v)))
                },
                label = { Text("Minimum power (W)") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = ctrl.maxPowerWatts.toString(),
                onValueChange = { text ->
                    val v = text.toIntOrNull() ?: return@OutlinedTextField
                    state.dispatch(SetControl(ctrl.copy(maxPowerWatts = v)))
                },
                label = { Text("Maximum power (W)") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Starting power is the initial command. Minimum and maximum power define the range Ewoc may use while trying to keep HR inside the band.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = ctrl.signalLossPowerWatts.toString(),
                onValueChange = { text ->
                    val v = text.toIntOrNull() ?: return@OutlinedTextField
                    state.dispatch(SetControl(ctrl.copy(signalLossPowerWatts = v)))
                },
                label = { Text("Fallback power on signal loss") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = ctrl.hrUpperCapBpm.toString(),
                onValueChange = { text ->
                    val v = text.toIntOrNull() ?: return@OutlinedTextField
                    state.dispatch(SetControl(ctrl.copy(hrUpperCapBpm = v)))
                },
                label = { Text("Heart rate safety cap") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Fallback power is the fail-safe command if heart-rate signal is lost. The safety cap is a hard protection boundary, not the normal working target.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        TextButton(onClick = { state.dispatch(SetControl(null)) }) {
            Text("Remove control settings")
        }
    } else {
        TextButton(onClick = {
            state.dispatch(SetControl(EditorControl(
                initialPowerWatts = 150,
                minPowerWatts = 50,
                maxPowerWatts = 400,
                signalLossPowerWatts = 100,
                hrUpperCapBpm = 185,
            )))
        }) {
            Text("Add control settings")
        }
    }
}

/** Common BCP 47 language codes offered in the picker dropdown. */
private val COMMON_LANGUAGE_CODES = listOf(
    "en", "fi", "sv", "de", "fr", "es", "it", "nl", "pt", "da", "no", "pl", "cs", "ja", "zh", "ko",
)

/** Collects all language codes already used anywhere in the document (translations + messages). */
private fun collectUsedLanguages(doc: EditorWorkoutDocument): Set<String> = buildSet {
    doc.titleLocalized?.translations?.keys?.let { addAll(it) }
    doc.descriptionLocalized?.translations?.keys?.let { addAll(it) }
    fun collectFromSegments(segments: List<EditorSegment>) {
        for (seg in segments) {
            for (msg in seg.messages) addAll(msg.translations.keys)
            if (seg is EditorSegment.Repeat) collectFromSegments(seg.segments)
        }
    }
    collectFromSegments(doc.segments)
    for (msg in doc.messages) addAll(msg.translations.keys)
}

/**
 * Editable language code picker: shows a dropdown with common codes and codes already
 * used in the document, but also accepts free-text input for arbitrary BCP 47 tags.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageCodePicker(
    value: String,
    onValueChange: (String) -> Unit,
    usedLanguages: Set<String>,
    excludeLanguages: Set<String>,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val query = value.lowercase()

    // Build suggestion list: used languages first, then common ones, filtered
    val suggestions = remember(usedLanguages, excludeLanguages, query) {
        val used = usedLanguages
            .filter { it !in excludeLanguages && (query.isEmpty() || it.startsWith(query)) }
            .sorted()
        val common = COMMON_LANGUAGE_CODES
            .filter { it !in excludeLanguages && it !in usedLanguages && (query.isEmpty() || it.startsWith(query)) }
        used + common
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                expanded = true
            },
            label = { Text("Language") },
            placeholder = { Text("fi") },
            singleLine = true,
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable).width(100.dp),
        )
        if (suggestions.isNotEmpty()) {
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                suggestions.forEach { code ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                code,
                                fontWeight = if (code in usedLanguages) FontWeight.Bold else FontWeight.Normal,
                            )
                        },
                        onClick = {
                            onValueChange(code)
                            expanded = false
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    )
                }
            }
        }
    }
}

/** Standard message kind values from the EWO spec. */
private val COMMON_MESSAGE_KINDS = listOf("instruction", "motivation", "warning", "transition", "intro")

/** Editable combo box for message kind — offers common values but allows free input. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessageKindPicker(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val query = value.lowercase()
    val suggestions = remember(query) {
        COMMON_MESSAGE_KINDS.filter { query.isEmpty() || it.startsWith(query) }
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                expanded = true
            },
            label = { Text("Message type") },
            singleLine = true,
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable).fillMaxWidth(),
        )
        if (suggestions.isNotEmpty()) {
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                suggestions.forEach { kind ->
                    DropdownMenuItem(
                        text = { Text(kind) },
                        onClick = {
                            onValueChange(kind)
                            expanded = false
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    )
                }
            }
        }
    }
}

/** Compact tooltip wrapper using Material 3 TooltipBox. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Tip(text: String, content: @Composable () -> Unit) {
    if (text.isBlank()) {
        content()
        return
    }
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(text) } },
        state = rememberTooltipState(),
        content = { content() },
    )
}

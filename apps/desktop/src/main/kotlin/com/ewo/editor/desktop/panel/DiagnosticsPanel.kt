package com.ewo.editor.desktop.panel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ewo.editor.model.*
import com.ewo.editor.desktop.countAllSegments
import com.ewo.editor.desktop.computeRawDuration
import com.ewo.editor.desktop.formatDurationHuman
import com.ewo.editor.desktop.state.EditorState

/** Secondary diagnostics rail — export status, validation, summary, compiled steps. */
@Composable
fun DiagnosticsPanel(state: EditorState) {
    val doc = state.document
    val preview = state.preview
    val blockingMarkers = doc.validationMarkers.filter { it.blocksExport }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        item {
            if (state.onboardingDismissed) {
                GettingStartedReopenCard(state)
            } else {
                GettingStartedCard(state)
            }
            Spacer(Modifier.height(8.dp))
        }

        item {
            LatestDiagnosticsCard(state)
            Spacer(Modifier.height(8.dp))
        }

        // Export status
        item {
            ExportStatusBanner(
                doc = doc,
                blockingMarkers = blockingMarkers,
            )
            Spacer(Modifier.height(8.dp))
        }

        item {
            DiagnosticsSummaryCard(
                exportBlockerCount = blockingMarkers.size,
                sanityWarningCount = preview.sanityWarnings.size,
                compileNoteCount = preview.compileErrors.size,
            )
            Spacer(Modifier.height(8.dp))
        }

        // Validation markers
        if (blockingMarkers.isNotEmpty()) {
            item {
                SectionHeader("Export blockers")
                Spacer(Modifier.height(4.dp))
            }
            items(blockingMarkers) { marker ->
                ValidationMarkerRow(marker)
            }
        }

        // Sanity warnings from compilation
        if (preview.sanityWarnings.isNotEmpty()) {
            item {
                Spacer(Modifier.height(8.dp))
                SectionHeader("Sanity Warnings")
                Spacer(Modifier.height(4.dp))
            }
            items(preview.sanityWarnings) { warning ->
                SanityWarningRow(warning)
            }
        }

        // Compile errors (NeedsCompileContext)
        if (preview.compileErrors.isNotEmpty()) {
            item {
                Spacer(Modifier.height(8.dp))
                SectionHeader("Compile Notes")
                Spacer(Modifier.height(4.dp))
            }
            items(preview.compileErrors) { error ->
                CompileErrorRow(error)
            }
        }

        item {
            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
        }

        // Summary stats
        item {
            DocumentSummary(doc, preview)
        }

        // Compiled step preview
        if (preview.steps.isNotEmpty()) {
            item {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                SectionHeader("Compiled Steps")
                Spacer(Modifier.height(4.dp))
            }
            items(preview.steps) { step ->
                PreviewStepRow(step)
            }
        }
    }
}

@Composable
private fun GettingStartedCard(state: EditorState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    Icons.Default.Lightbulb,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(top = 2.dp).size(18.dp),
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Getting started",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Pick the path that best matches what you want to do. Each path opens a wider help view in the center workspace and can be reopened later.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
                IconButton(onClick = { state.dismissOnboardingCard() }) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Hide getting started guide",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }

            OnboardingPathButton(
                title = "First workout",
                description = "Learn the basic loop: add steps, set targets, preview, then export.",
                onClick = { state.showOnboardingPath(EditorState.OnboardingPath.FIRST_WORKOUT) },
            )
            OnboardingPathButton(
                title = "I already know FTP workouts",
                description = "Jump straight to interval structure, %FTP targets, and fast preview checks.",
                onClick = { state.showOnboardingPath(EditorState.OnboardingPath.KNOWS_FTP) },
            )
            OnboardingPathButton(
                title = "Explore HR-based workouts",
                description = "See when to choose HR or HR%, what control settings do, and how preview differs from export.",
                onClick = { state.showOnboardingPath(EditorState.OnboardingPath.EXPLORE_HR) },
            )
        }
    }
}

@Composable
private fun GettingStartedReopenCard(state: EditorState) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "Getting started is hidden",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Reopen the lightweight orientation card and center help view here or from the Help menu whenever you want a refresher.",
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(
                onClick = { state.reopenOnboardingCard() },
                modifier = Modifier.align(Alignment.Start),
            ) {
                Text("Show getting started")
            }
        }
    }
}

@Composable
private fun OnboardingPathButton(
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(title)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DiagnosticsSummaryCard(
    exportBlockerCount: Int,
    sanityWarningCount: Int,
    compileNoteCount: Int,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "Diagnostics summary",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            LabeledValue("Export blockers", exportBlockerCount.toString())
            LabeledValue("Compile notes", compileNoteCount.toString())
            LabeledValue("Sanity warnings", sanityWarningCount.toString())
        }
    }
}

@Composable
private fun LatestDiagnosticsCard(state: EditorState) {
    val summary = state.lastOperationDiagnostics

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "Latest diagnostics",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            FilledTonalButton(
                onClick = { state.copyDiagnosticsToClipboard() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Copy diagnostics")
            }

            LabeledValue("Operation", summary?.operation ?: "Unknown")
            LabeledValue("Target", summary?.targetName ?: "None")
            LabeledValue("Outcome", summary?.outcome ?: "unknown")
            LabeledValue("Detail", summary?.detail ?: "No details recorded.")
            LabeledValue("Status bar", state.statusMessage)
        }
    }
}

@Composable
private fun LabeledValue(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun ExportStatusBanner(
    doc: EditorWorkoutDocument,
    blockingMarkers: List<EditorValidationMarker>,
) {
    val canExport = doc.canExport
    val color = if (canExport) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.errorContainer
    }
    val textColor = if (canExport) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onErrorContainer
    }
    val icon = if (canExport) Icons.Default.CheckCircle else Icons.Default.Error
    val titleBlocked = blockingMarkers.any { it.field == "title" }
    val statusText = when {
        canExport -> "Ready to export"
        titleBlocked -> "Export blocked — add a workout title first"
        else -> "Export blocked — fix errors first"
    }

    Surface(
        color = color,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, null, tint = textColor, modifier = Modifier.size(16.dp))
            Text(
                text = statusText,
                color = textColor,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ValidationMarkerRow(marker: EditorValidationMarker) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = when (marker.severity) {
                    EditorValidationSeverity.ERROR -> Icons.Default.Error
                    EditorValidationSeverity.WARNING -> Icons.Default.Warning
                    EditorValidationSeverity.INFO -> Icons.Default.Info
                },
                contentDescription = marker.severity.name,
                tint = when (marker.severity) {
                    EditorValidationSeverity.ERROR -> MaterialTheme.colorScheme.error
                    EditorValidationSeverity.WARNING -> MaterialTheme.colorScheme.tertiary
                    EditorValidationSeverity.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(18.dp),
            )
            Column {
                Text(marker.message, style = MaterialTheme.typography.bodySmall)
                val code = marker.code
                if (code != null) {
                    Text(
                        code,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SanityWarningRow(warning: EditorPreviewSanityWarning) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Default.Info, "Sanity warning", tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp))
            Column {
                Text(warning.message, style = MaterialTheme.typography.bodySmall)
                Text(warning.code, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
            }
        }
    }
}

@Composable
private fun CompileErrorRow(error: String) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Default.Info, "Compile note", tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(18.dp))
            Text(error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun PreviewStepRow(step: EditorPreviewStep) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "${step.index + 1}.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(24.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "${step.label}  ${formatDurationHuman(step.durationSec)}",
                style = MaterialTheme.typography.bodySmall,
            )
            val info = buildString {
                append(step.segmentId)
                step.repeatInfo?.let { append(" ($it)") }
            }
            Text(info, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DocumentSummary(doc: EditorWorkoutDocument, preview: EditorPreview) {
    SectionHeader("Summary")
    Spacer(Modifier.height(4.dp))

    val totalSegments = countAllSegments(doc.segments)
    val totalDuration = if (preview.totalDurationSec > 0) preview.totalDurationSec else computeRawDuration(doc.segments)

    SummaryRow("Segments", "$totalSegments")
    SummaryRow("Total Duration", formatDurationHuman(totalDuration))
    if (preview.steps.isNotEmpty()) {
        SummaryRow("Compiled Steps", "${preview.steps.size}")
    }
    SummaryRow("Version", doc.version)
    SummaryRow("Dirty", if (doc.isDirty) "Yes" else "No")
    SummaryRow("Errors", "${doc.errors.size}")
    SummaryRow("Warnings", "${doc.warnings.size}")

    val ifValue = preview.intensityFactor
    val tssValue = preview.tss
    if (ifValue != null && tssValue != null) {
        Spacer(Modifier.height(8.dp))
        SummaryRow("IF", "%.2f".format(ifValue))
        SummaryRow("TSS", "%.1f".format(tssValue))
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

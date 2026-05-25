package com.example.ergometerapp.ui

import android.util.Log
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.ergometerapp.AI_MENU_CONNECTIVITY_CHECK_TRAINER_ACTION
import com.example.ergometerapp.DocumentsFolderWorkoutOption
import com.example.ergometerapp.R
import com.example.ergometerapp.DeviceSelectionKind
import com.example.ergometerapp.FitExportPreference
import com.example.ergometerapp.HrProfileSex
import com.example.ergometerapp.ScannedBleDevice
import com.example.ergometerapp.ftms.IndoorBikeData
import com.example.ergometerapp.session.SessionPhase
import com.example.ergometerapp.session.SessionSummary
import com.example.ergometerapp.ui.components.SegmentKind
import com.example.ergometerapp.ui.components.WorkoutProfileChart
import com.example.ergometerapp.ui.components.WorkoutProfileSegment
import com.example.ergometerapp.ui.components.buildWorkoutProfileSegments
import com.example.ergometerapp.ui.components.disabledVisibleButtonColors
import com.example.ergometerapp.workout.DefaultWorkoutTextEventDurationSec
import com.example.ergometerapp.workout.WorkoutFile
import com.example.ergometerapp.workout.resolveActiveWorkoutTextEvent
import com.example.ergometerapp.workout.runner.IntervalPartPhase
import com.example.ergometerapp.workout.runner.RunnerState
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import java.util.Locale

/**
 * End-of-session summary UI.
 *
 * Summary values may be null when signals were not available during the session.
 */
@Composable
internal fun SummaryScreen(
    summary: SessionSummary?,
    fitExportStatusMessage: String?,
    fitExportStatusIsError: Boolean,
    fitExportPreference: FitExportPreference?,
    aiAssistantMessage: String?,
    aiAssistantIsError: Boolean,
    onRequestFitExport: () -> Unit,
    onRequestFitShare: () -> Unit,
    onRequestFitAutoExport: () -> Unit,
    onFitExportPreferenceSelected: (FitExportPreference) -> Unit,
    onBackToMenu: () -> Unit,
) {
    val unknown = stringResource(R.string.value_unknown)
    val summaryCardBorder = sessionCardBorder()
    var showFitPreferenceDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(summary, fitExportPreference) {
        if (summary != null && fitExportPreference == FitExportPreference.AUTO_SAVE) {
            onRequestFitAutoExport()
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        val layoutMode = resolveAdaptiveLayoutMode(width = maxWidth, height = maxHeight)
        // Keep landscape summaries dense on phones and tablets, even when only tablets enter two-pane mode.
        val useFourColumnMetrics = maxWidth > maxHeight
        val contentMaxWidth = if (layoutMode.isTwoPane()) {
            SummaryTwoPaneMaxContentWidth
        } else {
            SummaryMaxContentWidth
        }
        val summaryColumns = if (useFourColumnMetrics) 4 else 2
        val fontScale = LocalDensity.current.fontScale
        val summaryMetricsScale = resolveSummaryMetricsScale(
            isTwoPane = layoutMode.isTwoPane(),
            columns = summaryColumns,
            fontScale = fontScale,
        )

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = contentMaxWidth)
                    .fillMaxHeight(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item(key = "summaryHeader") {
                    Text(
                        text = stringResource(R.string.summary_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                val buttonModifier = if (layoutMode.isTwoPane()) {
                    Modifier.fillMaxWidth(0.7f)
                } else {
                    Modifier.fillMaxWidth()
                }

                item(key = "summaryActions") {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(
                            modifier = buttonModifier,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(
                                onClick = {
                                    when (fitExportPreference) {
                                        null -> showFitPreferenceDialog = true
                                        FitExportPreference.ASK_EVERY_TIME -> onRequestFitExport()
                                        FitExportPreference.AUTO_SAVE,
                                        FitExportPreference.DO_NOT_SAVE,
                                        -> Unit
                                    }
                                },
                                enabled = summary != null &&
                                    fitExportPreference != FitExportPreference.AUTO_SAVE &&
                                    fitExportPreference != FitExportPreference.DO_NOT_SAVE,
                                modifier = Modifier.weight(1f),
                                colors = disabledVisibleButtonColors(),
                            ) {
                                Text(stringResource(R.string.summary_export_fit))
                            }
                            Button(
                                onClick = onRequestFitShare,
                                enabled = summary != null,
                                modifier = Modifier.weight(1f),
                                colors = disabledVisibleButtonColors(),
                            ) {
                                Text(stringResource(R.string.summary_share_fit))
                            }
                        }
                    }
                }

                item(key = "summaryContent") {
                    if (summary == null) {
                        SectionCard(
                            title = stringResource(R.string.summary_title),
                            border = summaryCardBorder,
                        ) {
                            Text(
                                text = stringResource(R.string.no_summary),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        val summaryItems = listOf(
                            MetricItem(
                                stringResource(R.string.summary_duration),
                                formatTime(summary.durationSeconds, unknown)
                            ),
                            MetricItem(
                                stringResource(R.string.summary_actual_tss),
                                format1(summary.actualTss, unknown)
                            ),
                            MetricItem(
                                stringResource(R.string.summary_distance),
                                stringResource(
                                    R.string.summary_distance_value,
                                    format0(summary.distanceMeters, unknown)
                                )
                            ),
                            MetricItem(
                                stringResource(R.string.summary_total_calories),
                                stringResource(
                                    R.string.summary_kcal_value,
                                    format0(summary.totalEnergyKcal, unknown)
                                )
                            ),
                            MetricItem(
                                stringResource(R.string.summary_avg_power),
                                stringResource(
                                    R.string.summary_power_value,
                                    format0(summary.avgPower, unknown)
                                )
                            ),
                            MetricItem(
                                stringResource(R.string.summary_max_power),
                                stringResource(
                                    R.string.summary_power_value,
                                    format0(summary.maxPower, unknown)
                                )
                            ),
                            MetricItem(
                                stringResource(R.string.summary_avg_cadence),
                                stringResource(
                                    R.string.summary_cadence_value,
                                    format0(summary.avgCadence, unknown)
                                )
                            ),
                            MetricItem(
                                stringResource(R.string.summary_max_cadence),
                                stringResource(
                                    R.string.summary_cadence_value,
                                    format0(summary.maxCadence, unknown)
                                )
                            ),
                            MetricItem(
                                stringResource(R.string.summary_avg_hr),
                                stringResource(
                                    R.string.summary_hr_value,
                                    format0(summary.avgHeartRate, unknown)
                                )
                            ),
                            MetricItem(
                                stringResource(R.string.summary_max_hr),
                                stringResource(
                                    R.string.summary_hr_value,
                                    format0(summary.maxHeartRate, unknown)
                                )
                            )
                        )

                        SectionCard(
                            title = stringResource(R.string.summary_title),
                            border = summaryCardBorder,
                        ) {
                            MetricsGrid(
                                items = summaryItems,
                                columns = summaryColumns,
                                cardBorder = summaryCardBorder,
                                scale = summaryMetricsScale,
                            )
                        }
                    }
                }

                if (!fitExportStatusMessage.isNullOrBlank()) {
                    item(key = "summaryFitStatus") {
                        Text(
                            text = fitExportStatusMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (fitExportStatusIsError) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }

                if (fitExportPreference == FitExportPreference.AUTO_SAVE) {
                    item(key = "summaryAutoSaveHint") {
                        Text(
                            text = stringResource(R.string.summary_fit_auto_save_mode_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (fitExportPreference == FitExportPreference.DO_NOT_SAVE) {
                    item(key = "summaryDoNotSaveHint") {
                        Text(
                            text = stringResource(R.string.summary_fit_do_not_save_mode_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (!aiAssistantMessage.isNullOrBlank()) {
                    item(key = "summaryAiMessage") {
                        Text(
                            text = aiAssistantMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (aiAssistantIsError) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }

                item(key = "summaryBackToMenu") {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Button(
                            onClick = onBackToMenu,
                            modifier = buttonModifier,
                            colors = disabledVisibleButtonColors(),
                        ) {
                            Text(stringResource(R.string.back_to_menu))
                        }
                    }
                }
            }
        }
    }

    if (showFitPreferenceDialog) {
        AlertDialog(
            onDismissRequest = { showFitPreferenceDialog = false },
            title = {
                Text(text = stringResource(R.string.summary_fit_preference_dialog_title))
            },
            text = {
                Text(text = stringResource(R.string.summary_fit_preference_dialog_message))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showFitPreferenceDialog = false
                        onFitExportPreferenceSelected(FitExportPreference.AUTO_SAVE)
                        onRequestFitAutoExport()
                    },
                ) {
                    Text(text = stringResource(R.string.summary_fit_preference_auto_save))
                }
            },
            dismissButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(
                        onClick = {
                            showFitPreferenceDialog = false
                            onFitExportPreferenceSelected(FitExportPreference.ASK_EVERY_TIME)
                            onRequestFitExport()
                        },
                    ) {
                        Text(text = stringResource(R.string.summary_fit_preference_ask_every_time))
                    }
                    TextButton(
                        onClick = {
                            showFitPreferenceDialog = false
                            onFitExportPreferenceSelected(FitExportPreference.DO_NOT_SAVE)
                        },
                    ) {
                        Text(text = stringResource(R.string.summary_fit_preference_do_not_save))
                    }
                }
            },
        )
    }

}

private fun resolveSummaryMetricsScale(
    isTwoPane: Boolean,
    columns: Int,
    fontScale: Float,
): Float {
    val (baseScale, maxScale) = when {
        columns >= 4 && isTwoPane -> 0.72f to 0.82f
        columns >= 4 -> 0.64f to 0.74f
        isTwoPane -> 0.75f to 0.9f
        else -> 0.85f to 1f
    }
    val accessibilityProgress = ((fontScale - 1f) / 0.35f).coerceIn(0f, 1f)
    return baseScale + (maxScale - baseScale) * accessibilityProgress
}

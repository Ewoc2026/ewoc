package io.github.ewoc2026.ewoc.ui

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.ewoc2026.ewoc.AI_MENU_CONNECTIVITY_CHECK_TRAINER_ACTION
import io.github.ewoc2026.ewoc.DocumentsFolderWorkoutOption
import io.github.ewoc2026.ewoc.R
import io.github.ewoc2026.ewoc.DeviceSelectionKind
import io.github.ewoc2026.ewoc.estimatedMaxHeartRate
import io.github.ewoc2026.ewoc.HrProfileSex
import io.github.ewoc2026.ewoc.ScannedBleDevice
import io.github.ewoc2026.ewoc.SessionSetupMode
import io.github.ewoc2026.ewoc.ftms.IndoorBikeData
import io.github.ewoc2026.ewoc.session.SessionPhase
import io.github.ewoc2026.ewoc.session.SessionSample
import io.github.ewoc2026.ewoc.session.SessionSummary
import io.github.ewoc2026.ewoc.ui.components.LiveTelemetryChart
import io.github.ewoc2026.ewoc.ui.components.SegmentKind
import io.github.ewoc2026.ewoc.ui.components.WorkoutProfileChart
import io.github.ewoc2026.ewoc.ui.components.WorkoutProfileSegment
import io.github.ewoc2026.ewoc.ui.components.buildWorkoutProfileSegments
import io.github.ewoc2026.ewoc.ui.components.disabledVisibleButtonColors
import io.github.ewoc2026.ewoc.ui.theme.UiSemanticColor
import io.github.ewoc2026.ewoc.ui.theme.UiSpacing
import io.github.ewoc2026.ewoc.workout.DefaultWorkoutTextEventDurationSec
import io.github.ewoc2026.ewoc.workout.WorkoutFile
import io.github.ewoc2026.ewoc.workout.ImportedErgoWorkout
import io.github.ewoc2026.ewoc.workout.resolveActiveWorkoutTextEvent
import io.github.ewoc2026.ewoc.workout.runner.IntervalPartPhase
import io.github.ewoc2026.ewoc.workout.runner.RunnerState
import io.github.ewoc2026.ewoc.workout.runner.RunnerSegmentType
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import java.util.Locale
internal val SessionTopMetricsCompactWidth = 700.dp
internal val MenuMaxContentWidth = 560.dp
internal val MenuTwoPaneMaxContentWidth = 1200.dp
internal val SessionMaxContentWidth = 1200.dp
internal val SummaryMaxContentWidth = 920.dp
internal val SummaryTwoPaneMaxContentWidth = 1200.dp
internal val SessionStickyActionBottomPadding = 96.dp
internal val SessionWorkoutChartHeight = 176.dp
internal val SessionWorkoutChartHeightDense = 144.dp
internal val SessionWorkoutChartHeightPhonePortrait = 208.dp
internal val SessionWorkoutChartHeightTabletPortrait = 211.dp // SessionWorkoutChartHeight * 1.2
internal val SessionAiMessageRowHeight = 44.dp
internal val SessionCompactMessageRailHeight = 52.dp

internal data class MetricItem(
    val label: String,
    val value: String
)

internal data class SessionStatusPresentation(
    val message: String,
    val animateDots: Boolean,
)

internal enum class DeviceConnectionIndicatorState {
    CONNECTED,
    IDLE,
    ISSUE,
}

internal enum class SessionPortraitPreset {
    BALANCED,
    POWER_FIRST,
    WORKOUT_FIRST,
}

internal enum class MenuSetupStep {
    PROFILE,
    DEVICES,
    FILE_BASED,
    SUMMARY,
}

internal enum class MenuWorkoutMode {
    FILE,
    EDITOR,
}

@Composable
internal fun sessionCardBorder(): BorderStroke {
    val alpha = if (isSystemInDarkTheme()) 0.22f else 0.45f
    return BorderStroke(
        width = 1.dp,
        color = MaterialTheme.colorScheme.outline.copy(alpha = alpha),
    )
}

@Composable
internal fun menuNormalTextColor() = MaterialTheme.colorScheme.onSurface

@Composable
internal fun menuErrorTextColor() = MaterialTheme.colorScheme.error

@Composable
internal fun menuPickerStatusColor() = MaterialTheme.colorScheme.tertiary

@Composable
internal fun menuPickerWarningColor() = MaterialTheme.colorScheme.error

@Composable
internal fun menuPickerNeutralColor() = MaterialTheme.colorScheme.onSurface

@Composable
internal fun menuStartCtaColor() = MaterialTheme.colorScheme.primary

@Composable
internal fun menuStartCtaContentColor() = MaterialTheme.colorScheme.onPrimary

@Composable
internal fun menuSetupPendingColor() = UiSemanticColor.setupPending

@Composable
internal fun menuSetupReadyColor() = UiSemanticColor.setupReady

@Composable
internal fun menuStartButtonColors() = ButtonDefaults.buttonColors(
    containerColor = menuStartCtaColor(),
    contentColor = menuStartCtaContentColor(),
    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
)

@Composable
internal fun sessionQuitButtonColors(emphasized: Boolean) = if (emphasized) {
    ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
    )
} else {
    ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
    )
}

/**
 * Shared end-session CTA so all layouts keep the same disable/emphasis behavior.
 */
@Composable
internal fun SessionEndButton(
    onClick: () -> Unit,
    enabled: Boolean,
    emphasized: Boolean,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = sessionQuitButtonColors(emphasized = emphasized),
        modifier = modifier,
    ) {
        Text(
            text = stringResource(R.string.btn_quit_session_now),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
internal fun menuDeviceConnectedColor() = MaterialTheme.colorScheme.primary

@Composable
internal fun menuDeviceIdleColor() = MaterialTheme.colorScheme.outline

@Composable
internal fun menuDeviceIssueColor() = MaterialTheme.colorScheme.error

@Composable
internal fun menuTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = menuNormalTextColor(),
    unfocusedTextColor = menuNormalTextColor(),
    cursorColor = menuNormalTextColor(),
    focusedBorderColor = menuNormalTextColor(),
    unfocusedBorderColor = menuNormalTextColor(),
    focusedLabelColor = menuNormalTextColor(),
    unfocusedLabelColor = menuNormalTextColor(),
    focusedPlaceholderColor = menuNormalTextColor().copy(alpha = 0.7f),
    unfocusedPlaceholderColor = menuNormalTextColor().copy(alpha = 0.7f),
    errorBorderColor = menuErrorTextColor(),
    errorLabelColor = menuErrorTextColor(),
    errorCursorColor = menuErrorTextColor(),
)

@Composable
internal fun menuSecondaryButtonColors() = ButtonDefaults.buttonColors(
    containerColor = MaterialTheme.colorScheme.primaryContainer,
    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    disabledContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
    disabledContentColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
)

@Composable
internal fun menuInfoCardColors() = CardDefaults.elevatedCardColors(
    containerColor = MaterialTheme.colorScheme.surface,
    contentColor = MaterialTheme.colorScheme.onSurface,
)

/**
 * Shared scaffold for short-lived transition states where users need one clear status line.
 *
 * Keeping this centralized ensures CONNECTING/STOPPING flows keep the same spacing and
 * hierarchy when additional transitional states are added.
 */
@Composable
internal fun TransitionStatusScreen(
    statusText: String,
    hintText: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(UiSpacing.sm),
        ) {
            WaitingStatusText(
                baseText = statusText,
                animateDots = true,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = hintText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            content()
        }
    }
}

/**
 * Reusable card for timeout/recovery prompts in transition flows.
 */
@Composable
internal fun TransitionRecoveryCard(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actions: @Composable ColumnScope.() -> Unit,
) {
    ElevatedCard(
        modifier = modifier,
        colors = menuInfoCardColors(),
    ) {
        Column(
            modifier = Modifier.padding(UiSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(UiSpacing.md),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            actions()
        }
    }
}

@Composable
internal fun WaitingStatusText(
    baseText: String,
    animateDots: Boolean,
    style: TextStyle,
    color: Color,
    fontWeight: FontWeight = FontWeight.Normal,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Start,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
) {
    val normalizedBase = baseText.trimEnd().trimEnd('.', '…')
    if (!animateDots) {
        Text(
            text = normalizedBase,
            style = style,
            color = color,
            fontWeight = fontWeight,
            modifier = modifier,
            textAlign = textAlign,
            maxLines = maxLines,
            overflow = overflow,
            softWrap = softWrap,
        )
        return
    }

    var dotsCount by remember(animateDots) { mutableIntStateOf(1) }
    LaunchedEffect(animateDots) {
        while (true) {
            delay(350)
            dotsCount = if (dotsCount >= 3) 1 else dotsCount + 1
        }
    }
    val displayText = buildAnnotatedString {
        append(normalizedBase)
        val dotsStart = length
        append("...")
        val hiddenDots = 3 - dotsCount.coerceIn(1, 3)
        if (hiddenDots > 0) {
            addStyle(
                style = SpanStyle(color = Color.Transparent),
                start = dotsStart + dotsCount,
                end = dotsStart + 3,
            )
        }
    }

    Text(
        text = displayText,
        style = style,
        color = color,
        fontWeight = fontWeight,
        modifier = modifier,
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = overflow,
        softWrap = softWrap,
    )
}



@Composable
internal fun DeviceSelectionInfoCard(
    label: String,
    value: String,
    indicatorState: DeviceConnectionIndicatorState,
    compactLabel: Boolean,
    modifier: Modifier = Modifier,
) {
    val normalTextColor = menuNormalTextColor()
    val connectionPulseTransition = rememberInfiniteTransition(label = "deviceConnectionPulse")
    val pulseAlpha = connectionPulseTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "deviceConnectionPulseAlpha",
    ).value
    val indicatorColor = when (indicatorState) {
        DeviceConnectionIndicatorState.CONNECTED -> menuDeviceConnectedColor()
        DeviceConnectionIndicatorState.IDLE -> menuDeviceIdleColor()
        DeviceConnectionIndicatorState.ISSUE -> menuDeviceIssueColor()
    }
    val indicatorAlpha = if (indicatorState == DeviceConnectionIndicatorState.CONNECTED) {
        pulseAlpha
    } else {
        1.0f
    }

    ElevatedCard(
        modifier = modifier.height(if (compactLabel) 68.dp else 88.dp),
        colors = menuInfoCardColors(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    style = if (compactLabel) {
                        MaterialTheme.typography.labelMedium
                    } else {
                        MaterialTheme.typography.labelLarge
                    },
                    color = normalTextColor,
                    maxLines = if (compactLabel) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(
                            color = indicatorColor.copy(alpha = indicatorAlpha),
                            shape = CircleShape,
                        )
                )
            }
            Text(
                text = value,
                style = if (compactLabel) {
                    MaterialTheme.typography.bodySmall
                } else {
                    MaterialTheme.typography.bodyMedium
                },
                color = normalTextColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun MenuInlineValueCard(
    value: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    isError: Boolean = false,
    height: Dp = 40.dp,
) {
    val textColor = if (isError) menuErrorTextColor() else menuNormalTextColor()
    val cardModifier = if (onClick != null) {
        modifier
            .height(height)
            .clickable(onClick = onClick)
    } else {
        modifier.height(height)
    }
    ElevatedCard(
        modifier = cardModifier,
        colors = menuInfoCardColors(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun WorkoutMetaListBox(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    boxHeight: Dp = 64.dp,
) {
    val normalTextColor = menuNormalTextColor()
    val cardModifier = if (onClick != null) {
        modifier
            .height(boxHeight)
            .clickable(onClick = onClick)
    } else {
        modifier.height(boxHeight)
    }
    ElevatedCard(
        modifier = cardModifier,
        colors = menuInfoCardColors(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = normalTextColor,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodySmall,
                    color = normalTextColor,
                )
            }
        }
    }
}

@Composable
internal fun MenuSetupSummaryItem(
    label: String,
    value: String,
) {
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                append("$label: ")
            }
            append(value)
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
internal fun MenuSetupEntryCard(
    title: String,
    statusLabel: String,
    supportingText: String? = null,
    ready: Boolean,
    selected: Boolean = false,
    compact: Boolean = false,
    automationTag: String? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val accentColor = if (ready) {
        menuSetupReadyColor()
    } else {
        menuSetupPendingColor()
    }
    val minHeight = when {
        supportingText == null && compact -> 48.dp
        supportingText == null -> 68.dp
        compact -> 56.dp
        else -> 84.dp
    }
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .then(
                if (automationTag != null) {
                    Modifier.testTag(automationTag)
                } else {
                    Modifier
                },
            )
            .fillMaxWidth()
            .heightIn(min = minHeight),
        border = BorderStroke(if (selected) 3.dp else 2.dp, accentColor),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurface,
            containerColor = accentColor.copy(alpha = if (selected) 0.16f else 0.08f),
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (compact) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    MenuSetupStatusChip(
                        statusLabel = statusLabel,
                        ready = ready,
                        accentColor = accentColor,
                        compact = true,
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        modifier = Modifier.weight(1f),
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    MenuSetupStatusChip(
                        statusLabel = statusLabel,
                        ready = ready,
                        accentColor = accentColor,
                        compact = false,
                    )
                }
            }
            if (supportingText != null) {
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun MenuSetupStatusChip(
    statusLabel: String,
    ready: Boolean,
    accentColor: Color,
    compact: Boolean,
) {
    Box(
        modifier = Modifier
            .background(
                color = accentColor.copy(alpha = if (ready) 0.18f else 0.22f),
                shape = CircleShape,
            )
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            text = statusLabel,
            style = if (compact) {
                MaterialTheme.typography.labelSmall
            } else {
                MaterialTheme.typography.labelLarge
            },
            fontWeight = FontWeight.SemiBold,
            color = accentColor,
            maxLines = 1,
        )
    }
}


@Composable
internal fun PhonePortraitSessionWorkoutCard(
    onOpenWorkoutInfo: () -> Unit,
    onEndSession: () -> Unit,
    endSessionEnabled: Boolean,
    endSessionCtaEmphasized: Boolean,
    statusOverrideMessage: String?,
    sessionIssues: List<String>,
    phase: SessionPhase,
    runnerState: RunnerState,
    cadenceRpm: Double?,
    heartRateValue: String,
    speedValue: String,
    kcalValue: String,
    distanceValue: String,
    hrZoneValue: String,
    elapsedOfTotalText: String,
    selectedWorkout: WorkoutFile?,
    ftpWatts: Int,
    workoutElapsedSec: Int?,
    currentTargetWatts: Int?,
    workoutExecutionModeMessage: String?,
    workoutExecutionModeIsError: Boolean,
    aiAssistantMessage: String?,
    aiAssistantIsError: Boolean,
    targetPowerWatts: Int?,
    instantPowerValue: String,
    instantCadenceValue: String,
) {
    val cardBorder = sessionCardBorder()

    SectionCard(title = null, border = cardBorder) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SessionInfoButton(
                onClick = onOpenWorkoutInfo,
                modifier = Modifier,
            )
            SessionEndButton(
                onClick = onEndSession,
                enabled = endSessionEnabled,
                emphasized = endSessionCtaEmphasized,
                modifier = Modifier.height(36.dp),
            )
        }

        SessionInlineMetricsRow(
            leftLabel = stringResource(R.string.session_hr_short_label),
            leftValue = heartRateValue,
            rightLabel = stringResource(R.string.session_hr_zone_label),
            rightValue = hrZoneValue,
            leftValueScale = 1.3f,
            rightValueScale = 1.3f,
        )

        sessionIssues.forEach { issue ->
            Text(
                text = issue,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        SessionInlineMetricsRow(
            leftLabel = stringResource(R.string.session_elapsed_of_total),
            leftValue = elapsedOfTotalText,
            rightLabel = stringResource(R.string.summary_distance),
            rightValue = distanceValue,
        )
        SessionInlineMetricsRow(
            leftLabel = stringResource(R.string.session_speed_label),
            leftValue = speedValue,
            rightLabel = stringResource(R.string.session_kcal_label),
            rightValue = kcalValue,
        )

        val sessionStatus = resolveSessionStatusPresentation(
            phase = phase,
            runnerState = runnerState,
            cadenceRpm = cadenceRpm,
            overrideMessage = statusOverrideMessage,
        )
        Spacer(modifier = Modifier.height(10.dp))
        WaitingStatusText(
            baseText = sessionStatus.message,
            animateDots = sessionStatus.animateDots,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp, max = 96.dp)
                .animateContentSize(),
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        if (!workoutExecutionModeMessage.isNullOrBlank()) {
            Text(
                text = workoutExecutionModeMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = if (workoutExecutionModeIsError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        SessionAiMessageRow(
            aiAssistantMessage = aiAssistantMessage,
            aiAssistantIsError = aiAssistantIsError,
        )
        Spacer(modifier = Modifier.height(6.dp))

        if (selectedWorkout != null) {
            WorkoutProfileChart(
                workout = selectedWorkout,
                ftpWatts = ftpWatts,
                elapsedSec = workoutElapsedSec,
                currentTargetWatts = currentTargetWatts,
                targetPowerWatts = targetPowerWatts,
                instantPowerValue = instantPowerValue,
                instantCadenceValue = instantCadenceValue,
                kcalValue = kcalValue,
                heartRateValue = heartRateValue,
                heartRateZoneValue = hrZoneValue,
                chartHeight = SessionWorkoutChartHeightPhonePortrait
            )
        }
    }
}

@Composable
internal fun PhoneLandscapeSessionWorkoutCard(
    onOpenWorkoutInfo: () -> Unit,
    onEndSession: () -> Unit,
    statusOverrideMessage: String?,
    endSessionEnabled: Boolean,
    endSessionCtaEmphasized: Boolean,
    heartRateValue: String,
    remainingText: String,
    elapsedOfTotalText: String,
    speedValue: String,
    distanceValue: String,
    kcalValue: String,
    hrZoneValue: String,
    sessionIssues: List<String>,
    phase: SessionPhase,
    runnerState: RunnerState,
    cadenceRpm: Double?,
    workoutExecutionModeMessage: String?,
    workoutExecutionModeIsError: Boolean,
    aiAssistantMessage: String?,
    aiAssistantIsError: Boolean,
    selectedWorkout: WorkoutFile?,
    ftpWatts: Int,
    workoutElapsedSec: Int?,
    currentTargetWatts: Int?,
    targetPowerWatts: Int?,
    instantPowerValue: String,
    instantCadenceValue: String,
) {
    val cardBorder = sessionCardBorder()
    val sideColumnTopInset = SessionAiMessageRowHeight + 6.dp
    val sessionStatus = resolveSessionStatusPresentation(
        phase = phase,
        runnerState = runnerState,
        cadenceRpm = cadenceRpm,
        overrideMessage = statusOverrideMessage,
    )
    SectionCard(title = null, border = cardBorder) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SessionInfoButton(
                onClick = onOpenWorkoutInfo,
                modifier = Modifier,
            )
            WaitingStatusText(
                baseText = sessionStatus.message,
                animateDots = sessionStatus.animateDots,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 24.dp, max = 64.dp)
                    .animateContentSize(),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            SessionEndButton(
                onClick = onEndSession,
                enabled = endSessionEnabled,
                emphasized = endSessionCtaEmphasized,
                modifier = Modifier.height(36.dp),
            )
        }
        if (!workoutExecutionModeMessage.isNullOrBlank()) {
            Text(
                text = workoutExecutionModeMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = if (workoutExecutionModeIsError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(top = sideColumnTopInset),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SessionInlineMetric(
                    label = stringResource(R.string.session_hr_zone_label),
                    value = hrZoneValue,
                )
                SessionInlineMetric(
                    label = stringResource(R.string.session_workout_remaining),
                    value = remainingText,
                )
                SessionInlineMetric(
                    label = stringResource(R.string.session_elapsed_of_total),
                    value = elapsedOfTotalText,
                )
            }

            Column(
                modifier = Modifier.weight(2.7f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SessionAiMessageRow(
                    aiAssistantMessage = aiAssistantMessage,
                    aiAssistantIsError = aiAssistantIsError,
                )
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    if (selectedWorkout != null) {
                        WorkoutProfileChart(
                            workout = selectedWorkout,
                            ftpWatts = ftpWatts,
                            elapsedSec = workoutElapsedSec,
                            currentTargetWatts = currentTargetWatts,
                            targetPowerWatts = targetPowerWatts,
                            instantPowerValue = instantPowerValue,
                            instantCadenceValue = instantCadenceValue,
                            kcalValue = kcalValue,
                            heartRateValue = heartRateValue,
                            heartRateZoneValue = hrZoneValue,
                            chartHeight = SessionWorkoutChartHeight,
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(top = sideColumnTopInset),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SessionInlineMetric(
                    label = stringResource(R.string.session_speed_label),
                    value = speedValue,
                )
                SessionInlineMetric(
                    label = stringResource(R.string.summary_distance),
                    value = distanceValue,
                )
                SessionInlineMetric(
                    label = stringResource(R.string.session_kcal_label),
                    value = kcalValue,
                )
            }
        }

        sessionIssues.forEach { issue ->
            Text(
                text = issue,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
internal fun SessionInlineMetricsRow(
    leftLabel: String,
    leftValue: String,
    rightLabel: String,
    rightValue: String,
    leftValueScale: Float = 1f,
    rightValueScale: Float = 1f,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SessionInlineMetric(
            label = leftLabel,
            value = leftValue,
            modifier = Modifier.weight(1f),
            valueScale = leftValueScale,
        )
        SessionInlineMetric(
            label = rightLabel,
            value = rightValue,
            modifier = Modifier.weight(1f),
            valueScale = rightValueScale,
        )
    }
}

@Composable
internal fun SessionInlineMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueScale: Float = 1f,
) {
    val safeScale = valueScale.coerceAtLeast(0.8f)
    val labelStyle = MaterialTheme.typography.labelMedium.copy(
        fontSize = MaterialTheme.typography.labelMedium.fontSize * safeScale,
        lineHeight = MaterialTheme.typography.labelMedium.lineHeight * safeScale,
    )
    val valueStyle = MaterialTheme.typography.titleMedium.copy(
        fontSize = MaterialTheme.typography.titleMedium.fontSize * safeScale,
        lineHeight = MaterialTheme.typography.titleMedium.lineHeight * safeScale,
    )
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label,
            style = labelStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = valueStyle,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
internal fun SessionAiMessageRow(
    aiAssistantMessage: String?,
    aiAssistantIsError: Boolean,
    modifier: Modifier = Modifier,
) {
    val message = aiAssistantMessage?.trim().orEmpty()
    val hasMessage = message.isNotEmpty()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(SessionAiMessageRowHeight),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (hasMessage) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = if (aiAssistantIsError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun SessionPresetSelector(
    selectedPreset: SessionPortraitPreset,
    onPresetSelected: (SessionPortraitPreset) -> Unit,
    phonePortraitLayout: Boolean = false,
) {
    val hasUserSelectedPreset = rememberSaveable { mutableStateOf(false) }
    val expanded = rememberSaveable { mutableStateOf(true) }
    val cardBorder = sessionCardBorder()
    val balancedLabel = stringResource(R.string.session_layout_preset_balanced)
    val powerFirstLabel = stringResource(R.string.session_layout_preset_power_first)
    val workoutFirstLabel = stringResource(R.string.session_layout_preset_workout_first)
    val selectedPresetLabel = when (selectedPreset) {
        SessionPortraitPreset.BALANCED -> balancedLabel
        SessionPortraitPreset.POWER_FIRST -> powerFirstLabel
        SessionPortraitPreset.WORKOUT_FIRST -> workoutFirstLabel
    }
    val onPresetClick: (SessionPortraitPreset) -> Unit = { preset ->
        onPresetSelected(preset)
        hasUserSelectedPreset.value = true
        expanded.value = false
    }
    val showCompactSelector = hasUserSelectedPreset.value && !expanded.value

    if (showCompactSelector) {
        SectionCard(title = null, border = cardBorder) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(
                        R.string.session_layout_preset_selected_value,
                        selectedPresetLabel,
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(onClick = { expanded.value = true }) {
                    Text(text = stringResource(R.string.session_layout_preset_change))
                }
            }
        }
        return
    }

    SectionCard(
        title = stringResource(R.string.session_layout_preset_title),
        border = cardBorder,
    ) {
        if (phonePortraitLayout) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SessionPresetButton(
                    label = balancedLabel,
                    selected = selectedPreset == SessionPortraitPreset.BALANCED,
                    onClick = { onPresetClick(SessionPortraitPreset.BALANCED) },
                    modifier = Modifier.fillMaxWidth(),
                )
                SessionPresetButton(
                    label = powerFirstLabel,
                    selected = selectedPreset == SessionPortraitPreset.POWER_FIRST,
                    onClick = { onPresetClick(SessionPortraitPreset.POWER_FIRST) },
                    modifier = Modifier.fillMaxWidth(),
                )
                SessionPresetButton(
                    label = workoutFirstLabel,
                    selected = selectedPreset == SessionPortraitPreset.WORKOUT_FIRST,
                    onClick = { onPresetClick(SessionPortraitPreset.WORKOUT_FIRST) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SessionPresetButton(
                    label = balancedLabel,
                    selected = selectedPreset == SessionPortraitPreset.BALANCED,
                    onClick = { onPresetClick(SessionPortraitPreset.BALANCED) },
                    modifier = Modifier.weight(1f),
                )
                SessionPresetButton(
                    label = powerFirstLabel,
                    selected = selectedPreset == SessionPortraitPreset.POWER_FIRST,
                    onClick = { onPresetClick(SessionPortraitPreset.POWER_FIRST) },
                    modifier = Modifier.weight(1f),
                )
                SessionPresetButton(
                    label = workoutFirstLabel,
                    selected = selectedPreset == SessionPortraitPreset.WORKOUT_FIRST,
                    onClick = { onPresetClick(SessionPortraitPreset.WORKOUT_FIRST) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
internal fun SessionPresetButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (selected) {
        Button(
            onClick = onClick,
            modifier = modifier,
        ) {
            Text(text = label, maxLines = 1)
        }
        return
    }

    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
    ) {
        Text(text = label, maxLines = 1)
    }
}

@Composable
internal fun HrProfileSexButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (selected) {
        Button(
            onClick = onClick,
            modifier = modifier.height(40.dp),
            colors = menuSecondaryButtonColors(),
        ) {
            Text(text = label, maxLines = 1)
        }
        return
    }
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(40.dp),
    ) {
        Text(text = label, maxLines = 1)
    }
}

@Composable
internal fun SessionInfoButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(32.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Info,
            contentDescription = stringResource(R.string.session_workout_info_content_description),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun TopTelemetrySection(
    compactLayout: Boolean,
    phonePortraitLayout: Boolean = false,
    speedValue: String,
    kcalValue: String,
    distanceValue: String,
    workoutRemainingValue: String,
    elapsedOfTotalValue: String,
) {
    val cardBorder = sessionCardBorder()
    val metricSpacing = if (compactLayout) 8.dp else 10.dp
    SectionCard(title = null, border = cardBorder) {
        if (phonePortraitLayout) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(metricSpacing),
            ) {
                TopMetricCard(
                    label = stringResource(R.string.session_speed_label),
                    value = speedValue,
                    modifier = Modifier.weight(1f),
                    border = cardBorder,
                )
                TopMetricCard(
                    label = stringResource(R.string.session_kcal_label),
                    value = kcalValue,
                    modifier = Modifier.weight(1f),
                    border = cardBorder,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(metricSpacing),
            ) {
                TopMetricCard(
                    label = stringResource(R.string.summary_distance),
                    value = distanceValue,
                    modifier = Modifier.weight(1f),
                    border = cardBorder,
                )
                TopMetricCard(
                    label = stringResource(R.string.session_workout_remaining),
                    value = workoutRemainingValue,
                    modifier = Modifier.weight(1f),
                    border = cardBorder,
                )
            }
            TopMetricCard(
                label = stringResource(R.string.session_elapsed_of_total),
                value = elapsedOfTotalValue,
                modifier = Modifier.fillMaxWidth(),
                border = cardBorder,
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(metricSpacing),
            ) {
                TopMetricCard(
                    label = stringResource(R.string.session_speed_label),
                    value = speedValue,
                    modifier = Modifier.weight(1f),
                    border = cardBorder,
                )
                TopMetricCard(
                    label = stringResource(R.string.session_kcal_label),
                    value = kcalValue,
                    modifier = Modifier.weight(1f),
                    border = cardBorder,
                )
                TopMetricCard(
                    label = stringResource(R.string.summary_distance),
                    value = distanceValue,
                    modifier = Modifier.weight(1f),
                    border = cardBorder,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(metricSpacing),
            ) {
                TopMetricCard(
                    label = stringResource(R.string.session_workout_remaining),
                    value = workoutRemainingValue,
                    modifier = Modifier.weight(1f),
                    border = cardBorder,
                )
                TopMetricCard(
                    label = stringResource(R.string.session_elapsed_of_total),
                    value = elapsedOfTotalValue,
                    modifier = Modifier.weight(1f),
                    border = cardBorder,
                )
            }
        }
    }
}

@Composable
internal fun PrimaryTelemetrySection(
    heartRateValue: String,
    hrZoneValue: String,
    phonePortraitLayout: Boolean = false,
) {
    val cardBorder = sessionCardBorder()
    SectionCard(title = null, border = cardBorder) {
        if (phonePortraitLayout) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TopMetricCard(
                    label = stringResource(R.string.session_hr_short_label),
                    value = heartRateValue,
                    modifier = Modifier.weight(1f),
                    border = cardBorder,
                    emphasized = true,
                )
                TopMetricCard(
                    label = stringResource(R.string.session_hr_zone_label),
                    value = hrZoneValue,
                    modifier = Modifier.weight(1f),
                    border = cardBorder,
                    emphasized = true,
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TopMetricCard(
                    label = stringResource(R.string.session_hr_short_label),
                    value = heartRateValue,
                    modifier = Modifier.weight(1f),
                    border = cardBorder,
                    emphasized = true,
                )
                TopMetricCard(
                    label = stringResource(R.string.session_hr_zone_label),
                    value = hrZoneValue,
                    modifier = Modifier.weight(1f),
                    border = cardBorder,
                    emphasized = true,
                )
            }
        }
    }
}

@Composable
internal fun TopMetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    border: BorderStroke? = null,
    emphasized: Boolean = false,
    compact: Boolean = false,
    supportingText: String? = null,
) {
    val compactScale = if (compact) 0.74f else 0.80f
    val emphasizedScale = 1.2f * compactScale
    val labelStyle = if (emphasized) {
        MaterialTheme.typography.titleSmall.copy(
            fontSize = MaterialTheme.typography.titleSmall.fontSize * emphasizedScale,
            lineHeight = MaterialTheme.typography.titleSmall.lineHeight * emphasizedScale,
        )
    } else {
        MaterialTheme.typography.labelMedium.copy(
            fontSize = MaterialTheme.typography.labelMedium.fontSize * compactScale,
            lineHeight = MaterialTheme.typography.labelMedium.lineHeight * compactScale,
        )
    }
    val valueStyle = if (emphasized) {
        MaterialTheme.typography.headlineSmall.copy(
            fontSize = MaterialTheme.typography.headlineSmall.fontSize * emphasizedScale,
            lineHeight = MaterialTheme.typography.headlineSmall.lineHeight * emphasizedScale,
        )
    } else {
        MaterialTheme.typography.titleMedium.copy(
            fontSize = MaterialTheme.typography.titleMedium.fontSize * compactScale,
            lineHeight = MaterialTheme.typography.titleMedium.lineHeight * compactScale,
        )
    }
    val labelColor = if (emphasized) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val valueWeight = if (emphasized) FontWeight.Bold else FontWeight.SemiBold
    val verticalPadding = if (emphasized) {
        if (compact) 10.dp else 11.dp
    } else {
        if (compact) 6.dp else 7.dp
    }

    Card(
        modifier = modifier,
        border = border,
        colors = CardDefaults.elevatedCardColors(),
        elevation = CardDefaults.elevatedCardElevation(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (compact) 8.dp else 10.dp, vertical = verticalPadding),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = labelStyle,
                color = labelColor
            )
            if (emphasized) {
                Text(
                    text = value,
                    style = valueStyle,
                    fontWeight = valueWeight,
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(maxFontSize = valueStyle.fontSize),
                )
            } else {
                Text(
                    text = value,
                    style = valueStyle,
                    fontWeight = valueWeight,
                )
            }
            if (!supportingText.isNullOrBlank()) {
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun WorkoutProgressSection(
    statusOverrideMessage: String?,
    workoutSegments: List<WorkoutProfileSegment>,
    ftpWatts: Int,
    runnerState: RunnerState,
    phase: SessionPhase,
    cadenceRpm: Double?,
    remainingText: String,
    elapsedOfTotalText: String,
    unknown: String,
    lastTargetPower: Int?,
    workoutComplete: Boolean,
    workoutExecutionModeMessage: String?,
    workoutExecutionModeIsError: Boolean,
    aiAssistantMessage: String?,
    aiAssistantIsError: Boolean,
    showTimingMetrics: Boolean = true,
    showStepMetrics: Boolean = true,
    showStatusContent: Boolean = false,
    phonePortraitLayout: Boolean = false,
    heartRateValue: String,
    hrZoneValue: String,
    targetPowerWatts: Int?,
    instantPowerValue: String,
    instantCadenceValue: String,
    kcalValue: String,
    chartHeight: Dp = SessionWorkoutChartHeight,
    timelineSamples: List<SessionSample> = emptyList(),
    isTelemetryOnly: Boolean = false,
    compactChart: Boolean = false,
    isProEntitled: Boolean = false,
    endButtonSlot: (@Composable ColumnScope.() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val cardBorder = sessionCardBorder()
    val activeSegment = remember(workoutSegments, runnerState.workoutElapsedSec, workoutComplete) {
        currentWorkoutProfileSegment(
            workoutSegments = workoutSegments,
            elapsedSec = runnerState.workoutElapsedSec,
            completed = workoutComplete,
        )
    }
    val chartHeaderLabel = sessionChartHeaderLabel(
        phase = phase,
        runnerState = runnerState,
        cadenceRpm = cadenceRpm,
        activeSegment = activeSegment,
        workoutSegments = workoutSegments,
        unknown = unknown,
    )
    SectionCard(
        title = null,
        border = cardBorder,
        modifier = modifier,
    ) {
        if (showTimingMetrics) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricCard(
                    label = stringResource(R.string.session_workout_remaining),
                    value = remainingText,
                    modifier = Modifier.weight(1f),
                    border = cardBorder,
                )
                MetricCard(
                    label = stringResource(R.string.session_elapsed_of_total),
                    value = elapsedOfTotalText,
                    modifier = Modifier.weight(1f),
                    valueStyle = MaterialTheme.typography.titleMedium,
                    valueMaxLines = 1,
                    border = cardBorder,
                )
            }
        }

        if (showStepMetrics) {
            if (phonePortraitLayout) {
                MetricCard(
                    label = stringResource(R.string.session_workout_step_type),
                    value = sessionStepTypeLabel(
                        phase = phase,
                        runnerState = runnerState,
                        cadenceRpm = cadenceRpm,
                        activeSegment = activeSegment,
                        workoutSegments = workoutSegments,
                        unknown = unknown,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    border = cardBorder,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    MetricCard(
                        label = stringResource(R.string.session_workout_step_remaining),
                        value = formatTime(runnerState.stepRemainingSec, unknown),
                        modifier = Modifier.weight(1f),
                        border = cardBorder,
                    )
                    MetricCard(
                        label = stringResource(R.string.session_target_label),
                        value = sessionTargetPowerLabel(
                            runnerState = runnerState,
                            activeSegment = activeSegment,
                            ftpWatts = ftpWatts,
                            fallbackTargetPower = lastTargetPower,
                            unknown = unknown,
                        ),
                        modifier = Modifier.weight(1f),
                        border = cardBorder,
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    MetricCard(
                        label = stringResource(R.string.session_workout_step_type),
                        value = sessionStepTypeLabel(
                            phase = phase,
                            runnerState = runnerState,
                            cadenceRpm = cadenceRpm,
                            activeSegment = activeSegment,
                            workoutSegments = workoutSegments,
                            unknown = unknown,
                        ),
                        modifier = Modifier.weight(1f),
                        border = cardBorder,
                    )
                    MetricCard(
                        label = stringResource(R.string.session_workout_step_remaining),
                        value = formatTime(runnerState.stepRemainingSec, unknown),
                        modifier = Modifier.weight(1f),
                        border = cardBorder,
                    )
                    MetricCard(
                        label = stringResource(R.string.session_target_label),
                        value = sessionTargetPowerLabel(
                            runnerState = runnerState,
                            activeSegment = activeSegment,
                            ftpWatts = ftpWatts,
                            fallbackTargetPower = lastTargetPower,
                            unknown = unknown,
                        ),
                        modifier = Modifier.weight(1f),
                        border = cardBorder,
                    )
                }
            }
        }

        if (showStatusContent) {
            SessionWorkoutStatusContent(
                phase = phase,
                runnerState = runnerState,
                cadenceRpm = cadenceRpm,
                statusOverrideMessage = statusOverrideMessage,
                workoutExecutionModeMessage = workoutExecutionModeMessage,
                workoutExecutionModeIsError = workoutExecutionModeIsError,
                aiAssistantMessage = aiAssistantMessage,
                aiAssistantIsError = aiAssistantIsError,
                workoutComplete = workoutComplete,
            )
        }

        if (workoutSegments.isNotEmpty()) {
            WorkoutProfileChart(
                segments = workoutSegments,
                ftpWatts = ftpWatts,
                elapsedSec = runnerState.workoutElapsedSec,
                currentTargetWatts = runnerState.targetPowerWatts ?: lastTargetPower,
                targetPowerWatts = targetPowerWatts,
                instantPowerValue = instantPowerValue,
                instantCadenceValue = instantCadenceValue,
                kcalValue = kcalValue,
                heartRateValue = heartRateValue,
                heartRateZoneValue = hrZoneValue,
                topCenterLabel = chartHeaderLabel,
                chartHeight = chartHeight,
            )
        } else if (isTelemetryOnly && timelineSamples.isNotEmpty()) {
            val defaultWindowSeconds = 120
            var selectedWindowSeconds by remember { mutableIntStateOf(defaultWindowSeconds) }
            val effectiveWindowSeconds = if (isProEntitled) selectedWindowSeconds else defaultWindowSeconds
            val telemetrySelectorReserve = if (isProEntitled) 48.dp else 0.dp
            val telemetryChartMaxHeight = (chartHeight - telemetrySelectorReserve)
                .coerceAtLeast(72.dp)

            if (isProEntitled) {
                TelemetryTimeWindowSelector(
                    selectedSeconds = selectedWindowSeconds,
                    onSelected = { selectedWindowSeconds = it },
                )
            }

            LiveTelemetryChart(
                samples = timelineSamples,
                ftpWatts = ftpWatts,
                windowSeconds = effectiveWindowSeconds,
                compact = compactChart,
                maxHeight = telemetryChartMaxHeight,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        endButtonSlot?.invoke(this)
    }
}

private val TelemetryTimeWindowOptions = listOf(
    120 to "2 min",
    300 to "5 min",
    600 to "10 min",
)

@Composable
private fun TelemetryTimeWindowSelector(
    selectedSeconds: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TelemetryTimeWindowOptions.forEach { (seconds, label) ->
            val isSelected = selectedSeconds == seconds
            OutlinedButton(
                onClick = { onSelected(seconds) },
                colors = if (isSelected) {
                    ButtonDefaults.outlinedButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                } else {
                    ButtonDefaults.outlinedButtonColors()
                },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.heightIn(min = 32.dp),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
internal fun SessionWorkoutStatusCard(
    phase: SessionPhase,
    runnerState: RunnerState,
    cadenceRpm: Double?,
    statusOverrideMessage: String?,
    workoutExecutionModeMessage: String?,
    workoutExecutionModeIsError: Boolean,
    aiAssistantMessage: String?,
    aiAssistantIsError: Boolean,
    workoutComplete: Boolean,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val cardBorder = sessionCardBorder()
    SectionCard(
        title = null,
        compact = compact,
        border = cardBorder,
        modifier = modifier,
    ) {
        SessionWorkoutStatusContent(
            phase = phase,
            runnerState = runnerState,
            cadenceRpm = cadenceRpm,
            statusOverrideMessage = statusOverrideMessage,
            workoutExecutionModeMessage = workoutExecutionModeMessage,
            workoutExecutionModeIsError = workoutExecutionModeIsError,
            aiAssistantMessage = aiAssistantMessage,
            aiAssistantIsError = aiAssistantIsError,
            workoutComplete = workoutComplete,
        )
    }
}

@Composable
internal fun SessionCompactMessageRail(
    phase: SessionPhase,
    runnerState: RunnerState,
    cadenceRpm: Double?,
    statusOverrideMessage: String?,
    workoutExecutionModeMessage: String?,
    workoutExecutionModeIsError: Boolean,
    aiAssistantMessage: String?,
    aiAssistantIsError: Boolean,
    workoutComplete: Boolean,
    modifier: Modifier = Modifier,
) {
    val sessionStatus = resolveSessionStatusPresentation(
        phase = phase,
        runnerState = runnerState,
        cadenceRpm = cadenceRpm,
        overrideMessage = statusOverrideMessage,
    )
    val primaryStatusMessage = resolvePrimarySessionStatusMessage(
        statusOverrideMessage = statusOverrideMessage,
    )
    val compactMessage = when {
        workoutExecutionModeIsError && !workoutExecutionModeMessage.isNullOrBlank() ->
            workoutExecutionModeMessage.trim() to true

        !aiAssistantMessage.isNullOrBlank() ->
            aiAssistantMessage.trim() to aiAssistantIsError

        !primaryStatusMessage.isNullOrBlank() ->
            primaryStatusMessage to false

        workoutComplete ->
            stringResource(R.string.session_workout_complete) to false

        !workoutExecutionModeMessage.isNullOrBlank() ->
            workoutExecutionModeMessage.trim() to false

        sessionStatus.animateDots ->
            sessionStatus.message to false

        else -> null
    }
    SectionCard(
        title = null,
        compact = true,
        border = sessionCardBorder(),
        modifier = modifier.height(SessionCompactMessageRailHeight),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize(),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (compactMessage != null) {
                Text(
                    text = compactMessage.first,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (compactMessage.second) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SessionWorkoutStatusContent(
    phase: SessionPhase,
    runnerState: RunnerState,
    cadenceRpm: Double?,
    statusOverrideMessage: String?,
    workoutExecutionModeMessage: String?,
    workoutExecutionModeIsError: Boolean,
    aiAssistantMessage: String?,
    aiAssistantIsError: Boolean,
    workoutComplete: Boolean,
) {
    val primaryStatusMessage = resolvePrimarySessionStatusMessage(
        statusOverrideMessage = statusOverrideMessage,
    )
    val extraMessages = buildList<Pair<String, Boolean>> {
        if (!workoutExecutionModeMessage.isNullOrBlank()) {
            add(workoutExecutionModeMessage to workoutExecutionModeIsError)
        }
        if (workoutComplete) {
            add(stringResource(R.string.session_workout_complete) to false)
        }
    }
    val hasAiMessage = !aiAssistantMessage.isNullOrBlank()
    val hasStatusContent = !primaryStatusMessage.isNullOrBlank() || extraMessages.isNotEmpty() || hasAiMessage
    if (!hasStatusContent) return

    if (!primaryStatusMessage.isNullOrBlank()) {
        Text(
            text = stringResource(R.string.session_workout_messages),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        WaitingStatusText(
            baseText = primaryStatusMessage,
            animateDots = false,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 24.dp, max = 64.dp)
                .animateContentSize(),
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
    extraMessages.forEach { (message, isError) ->
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    SessionAiMessageRow(
        aiAssistantMessage = aiAssistantMessage,
        aiAssistantIsError = aiAssistantIsError,
    )
}

internal fun resolveWorkoutDisplayName(
    selectedWorkout: WorkoutFile?,
    selectedImportedWorkout: ImportedErgoWorkout? = null,
    selectedSessionSetupMode: SessionSetupMode = SessionSetupMode.FILE,
    selectedWorkoutFileName: String?,
    fallback: String,
    telemetryOnlyLabel: String = "Telemetry only",
): String {
    if (selectedSessionSetupMode == SessionSetupMode.TELEMETRY_ONLY) {
        return telemetryOnlyLabel
    }
    val parsedName = selectedWorkout?.name?.trim().orEmpty()
    if (parsedName.isNotEmpty()) {
        return parsedName
    }
    val importedTitle = selectedImportedWorkout?.title?.trim().orEmpty()
    if (importedTitle.isNotEmpty()) {
        return importedTitle
    }
    val fileName = selectedWorkoutFileName
        ?.substringAfterLast('/')
        ?.trim()
        .orEmpty()
    if (fileName.isNotEmpty()) {
        return fileName.substringBeforeLast('.', missingDelimiterValue = fileName)
    }
    return fallback
}

/**
 * Keeps the session message rail focused on explicit status text only.
 *
 * Interval ON/OFF countdown is already rendered in the chart/header path, so
 * repeating it here adds redundant noise without adding new guidance.
 */
internal fun resolvePrimarySessionStatusMessage(statusOverrideMessage: String?): String? {
    return statusOverrideMessage
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
}

internal fun resolveWorkoutDescription(
    selectedWorkout: WorkoutFile?,
    selectedImportedWorkout: ImportedErgoWorkout? = null,
    fallback: String,
): String {
    val workoutDescription = selectedWorkout?.description?.trim().orEmpty()
    if (workoutDescription.isNotEmpty()) {
        return workoutDescription
    }
    val importedDescription = selectedImportedWorkout?.description?.trim().orEmpty()
    if (importedDescription.isNotEmpty()) {
        return importedDescription
    }
    return fallback
}

internal fun currentWorkoutProfileSegment(
    workoutSegments: List<WorkoutProfileSegment>,
    elapsedSec: Int?,
    completed: Boolean,
): WorkoutProfileSegment? {
    if (workoutSegments.isEmpty()) return null
    if (completed) return workoutSegments.last()
    val elapsed = (elapsedSec ?: 0).coerceAtLeast(0)
    return workoutSegments.firstOrNull { segment ->
        elapsed < (segment.startSec + segment.durationSec)
    } ?: workoutSegments.last()
}

@Composable
internal fun sessionStepTypeLabel(
    phase: SessionPhase,
    runnerState: RunnerState,
    cadenceRpm: Double?,
    activeSegment: WorkoutProfileSegment?,
    workoutSegments: List<WorkoutProfileSegment>,
    isTelemetryOnly: Boolean = false,
    unknown: String,
): String {
    if (isTelemetryOnly) return unknown
    if (isWaitingStartState(phase = phase, runnerState = runnerState, cadenceRpm = cadenceRpm)) {
        return stringResource(R.string.session_workout_step_start)
    }
    if (runnerState.done) {
        return stringResource(R.string.session_workout_state_done)
    }
    if (runnerState.segmentType == RunnerSegmentType.HEART_RATE_STEADY) {
        return stringResource(R.string.session_workout_step_type_hr_control)
    }
    if (runnerState.intervalPart != null) {
        return stringResource(R.string.session_workout_step_type_interval)
    }
    return when (activeSegment?.kind) {
        SegmentKind.RAMP -> stringResource(
            resolveRampSessionStepTypeLabelRes(
                activeSegment = activeSegment,
                workoutSegments = workoutSegments,
                runnerLabel = runnerState.label,
            )
        )
        SegmentKind.STEADY -> stringResource(R.string.session_workout_step_type_steady)
        SegmentKind.FREERIDE -> stringResource(R.string.session_workout_step_type_free_ride)
        null -> runnerState.label ?: unknown
    }
}

@Composable
internal fun sessionChartHeaderLabel(
    phase: SessionPhase,
    runnerState: RunnerState,
    cadenceRpm: Double?,
    activeSegment: WorkoutProfileSegment?,
    workoutSegments: List<WorkoutProfileSegment>,
    unknown: String,
): String {
    runnerState.intervalPart?.let { intervalPart ->
        val intervalPhase = intervalPhaseLabel(intervalPart.phase)
        val intervalRemaining = formatTime(intervalPart.remainingSec, unknown)
        return resolveSessionChartHeaderIntervalCue(
            intervalPhase = intervalPhase,
            intervalRemaining = intervalRemaining,
        )
    }

    val stepType = sessionStepTypeLabel(
        phase = phase,
        runnerState = runnerState,
        cadenceRpm = cadenceRpm,
        activeSegment = activeSegment,
        workoutSegments = workoutSegments,
        unknown = unknown,
    )
    val stepRemainingSec = runnerState.stepRemainingSec
    if (stepRemainingSec == null || stepRemainingSec <= 0) {
        return stepType
    }
    val stepRemaining = formatTime(stepRemainingSec, unknown)
    return "$stepType • $stepRemaining"
}

/**
 * Formats the interval cue kept in the chart/header path.
 *
 * Session Messages intentionally no longer mirror this cue, so interval phase
 * and remaining time stay visible in exactly one place during interval blocks.
 */
internal fun resolveSessionChartHeaderIntervalCue(
    intervalPhase: String,
    intervalRemaining: String,
): String {
    return "$intervalPhase $intervalRemaining"
}

private fun resolveRampSessionStepTypeLabelRes(
    activeSegment: WorkoutProfileSegment?,
    workoutSegments: List<WorkoutProfileSegment>,
    runnerLabel: String?,
): Int {
    val normalizedRunnerLabel = runnerLabel?.trim()
    if (normalizedRunnerLabel.equals("Warmup", ignoreCase = true)) {
        return R.string.session_workout_step_type_warmup
    }
    if (normalizedRunnerLabel.equals("Cooldown", ignoreCase = true)) {
        return R.string.session_workout_step_type_cooldown
    }

    val segment = activeSegment ?: return R.string.session_workout_step_type_ramp
    val totalDurationSec = workoutSegments.sumOf { it.durationSec }
    val startsWorkout = segment.startSec == 0
    val endsWorkout = totalDurationSec > 0 &&
        (segment.startSec + segment.durationSec) >= totalDurationSec

    if (startsWorkout && !endsWorkout) {
        return R.string.session_workout_step_type_warmup
    }
    if (endsWorkout && !startsWorkout) {
        return R.string.session_workout_step_type_cooldown
    }
    if (startsWorkout && endsWorkout) {
        val startPower = segment.startPowerRelFtp
        val endPower = segment.endPowerRelFtp
        if (startPower != null && endPower != null) {
            if (endPower > startPower) {
                return R.string.session_workout_step_type_warmup
            }
            if (endPower < startPower) {
                return R.string.session_workout_step_type_cooldown
            }
        }
    }
    return R.string.session_workout_step_type_ramp
}

internal fun sessionTargetPowerLabel(
    runnerState: RunnerState,
    activeSegment: WorkoutProfileSegment?,
    ftpWatts: Int,
    fallbackTargetPower: Int?,
    unknown: String,
): String {
    if (runnerState.done && fallbackTargetPower != null) {
        return formatWatts(fallbackTargetPower, unknown)
    }
    if (runnerState.intervalPart != null) {
        return formatWatts(
            sessionDisplayedTargetPowerWatts(
                runnerState = runnerState,
                fallbackTargetPower = fallbackTargetPower,
            ),
            unknown,
        )
    }
    if (activeSegment?.kind == SegmentKind.RAMP) {
        val startWatts = activeSegment.startPowerRelFtp?.let { relativePowerToWatts(it, ftpWatts) }
        val endWatts = activeSegment.endPowerRelFtp?.let { relativePowerToWatts(it, ftpWatts) }
        if (startWatts != null && endWatts != null) {
            return if (startWatts == endWatts) {
                formatWatts(startWatts, unknown)
            } else {
                "$startWatts -> $endWatts W"
            }
        }
    }
    val resolvedTarget = sessionDisplayedTargetPowerWatts(
        runnerState = runnerState,
        fallbackTargetPower = fallbackTargetPower,
    ) ?: activeSegment?.startPowerRelFtp?.let { relativePowerToWatts(it, ftpWatts) }
    return formatWatts(resolvedTarget, unknown)
}

/**
 * Keeps session display target telemetry aligned with imported-HR live control.
 *
 * During ordinary power-driven segments the runner state's target is the
 * source of truth. During imported-HR segments that same runner value can stay
 * anchored to the step's planned initial power while [fallbackTargetPower]
 * tracks the currently applied HR-controlled override, so the UI must prefer
 * the fallback there.
 */
internal fun sessionDisplayedTargetPowerWatts(
    runnerState: RunnerState,
    fallbackTargetPower: Int?,
): Int? {
    val preferFallbackTarget = runnerState.segmentType == RunnerSegmentType.HEART_RATE_STEADY
    return if (preferFallbackTarget) {
        fallbackTargetPower ?: runnerState.targetPowerWatts
    } else {
        runnerState.targetPowerWatts ?: fallbackTargetPower
    }
}

internal data class HrZoneRange(
    val zone: Int,
    val minBpm: Int,
    val maxBpm: Int,
)

@Composable
internal fun sessionHeartRateZoneLabel(
    currentHeartRate: Int?,
    profileAge: Int?,
    profileSex: HrProfileSex?,
    unknown: String,
    missingProfile: String,
): String {
    if (profileAge == null) {
        return missingProfile
    }
    val hr = currentHeartRate?.takeIf { it > 0 } ?: return unknown
    val estimatedMaxHr = estimatedMaxHeartRate(age = profileAge, sex = profileSex)
    val matchedZone = resolveHeartRateZone(
        heartRateBpm = hr,
        maxHeartRate = estimatedMaxHr,
    )
    val zoneLabelRes = if (profileSex == null) {
        R.string.session_hr_zone_value_estimated
    } else {
        R.string.session_hr_zone_value
    }
    return stringResource(
        zoneLabelRes,
        matchedZone,
    )
}

internal fun heartRateZoneRanges(maxHeartRate: Int): List<HrZoneRange> {
    val maxHr = maxHeartRate.coerceIn(120, 220)
    val percentages = listOf(
        0.50 to 0.60,
        0.60 to 0.70,
        0.70 to 0.80,
        0.80 to 0.90,
        0.90 to 1.00,
    )
    return percentages.mapIndexed { index, (minPercent, maxPercent) ->
        val minBpm = (maxHr * minPercent).roundToInt()
        val maxBpm = (maxHr * maxPercent).roundToInt()
        HrZoneRange(
            zone = index + 1,
            minBpm = minBpm,
            maxBpm = maxBpm,
        )
    }
}

/**
 * Resolves a heart-rate zone for values that may drift slightly outside calculated bounds.
 *
 * Values below the first range are clamped to zone 1 to avoid flashing high-intensity zones
 * during warm-up. Values above the last range are clamped to zone 5.
 */
internal fun resolveHeartRateZone(
    heartRateBpm: Int,
    maxHeartRate: Int,
): Int {
    val ranges = heartRateZoneRanges(maxHeartRate = maxHeartRate)
    val matched = ranges.firstOrNull { heartRateBpm in it.minBpm..it.maxBpm }
    if (matched != null) {
        return matched.zone
    }
    return if (heartRateBpm < ranges.first().minBpm) {
        ranges.first().zone
    } else {
        ranges.last().zone
    }
}

internal fun relativePowerToWatts(relativePower: Double, ftpWatts: Int): Int {
    return (relativePower * ftpWatts.coerceAtLeast(1)).roundToInt()
}

internal fun formatWatts(value: Int?, fallback: String): String {
    return value?.let { "$it W" } ?: fallback
}


@Composable
internal fun SessionIssuesSection(
    messages: List<String>
) {
    SectionCard(
        title = stringResource(R.string.session_issue_title),
        border = sessionCardBorder(),
    ) {
        messages.forEach { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
internal fun sessionStateLabel(
    phase: SessionPhase,
    runnerState: RunnerState,
    cadenceRpm: Double?,
    isTelemetryOnly: Boolean = false,
    postWorkoutFreerideModeActive: Boolean = false,
): String {
    if (phase != SessionPhase.RUNNING) {
        return phaseLabel(phase)
    }
    if (postWorkoutFreerideModeActive) {
        return phaseLabel(phase)
    }
    if (!isTelemetryOnly && isWaitingStartState(phase = phase, runnerState = runnerState, cadenceRpm = cadenceRpm)) {
        return stringResource(R.string.session_state_waiting)
    }
    if (isTelemetryOnly) {
        return phaseLabel(phase)
    }
    return workoutStateLabel(runnerState)
}

internal fun isWaitingStartState(
    phase: SessionPhase,
    runnerState: RunnerState,
    cadenceRpm: Double?,
): Boolean {
    if (phase != SessionPhase.RUNNING) return false
    val elapsedSec = runnerState.workoutElapsedSec ?: 0
    return elapsedSec == 0 &&
        (cadenceRpm ?: 0.0) <= 0.0
}

internal fun isSessionWaitingForUserActionState(
    phase: SessionPhase,
    runnerState: RunnerState,
    cadenceRpm: Double?,
): Boolean {
    if (isWaitingStartState(phase = phase, runnerState = runnerState, cadenceRpm = cadenceRpm)) {
        return true
    }
    return phase == SessionPhase.RUNNING &&
        runnerState.running &&
        runnerState.paused
}

internal fun resolveSessionStatusMessage(
    overrideMessage: String?,
    fallbackMessage: String,
): String {
    return overrideMessage
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: fallbackMessage
}

internal fun shouldAnimateSessionStatusDots(
    overrideMessage: String?,
    waitingForUserAction: Boolean,
): Boolean {
    return overrideMessage.isNullOrBlank() && waitingForUserAction
}

@Composable
internal fun resolveSessionStatusPresentation(
    phase: SessionPhase,
    runnerState: RunnerState,
    cadenceRpm: Double?,
    overrideMessage: String?,
): SessionStatusPresentation {
    val fallbackMessage = sessionStateLabel(
        phase = phase,
        runnerState = runnerState,
        cadenceRpm = cadenceRpm,
    )
    val waitingForUserAction = isSessionWaitingForUserActionState(
        phase = phase,
        runnerState = runnerState,
        cadenceRpm = cadenceRpm,
    )
    return SessionStatusPresentation(
        message = resolveSessionStatusMessage(
            overrideMessage = overrideMessage,
            fallbackMessage = fallbackMessage,
        ),
        animateDots = shouldAnimateSessionStatusDots(
            overrideMessage = overrideMessage,
            waitingForUserAction = waitingForUserAction,
        ),
    )
}

@Composable
internal fun intervalCountdownLabel(
    phase: IntervalPartPhase,
    remainingSec: Int,
): String {
    val phaseLabel = intervalPhaseLabel(phase)
    return stringResource(
        R.string.session_workout_interval_countdown,
        phaseLabel,
        remainingSec.coerceAtLeast(0),
    )
}

@Composable
internal fun intervalPhaseLabel(phase: IntervalPartPhase): String {
    return when (phase) {
        IntervalPartPhase.ON -> stringResource(R.string.session_workout_interval_phase_on)
        IntervalPartPhase.OFF -> stringResource(R.string.session_workout_interval_phase_off)
    }
}

@Composable
internal fun phaseLabel(phase: SessionPhase): String {
    return when (phase) {
        SessionPhase.IDLE -> stringResource(R.string.session_phase_idle)
        SessionPhase.RUNNING -> stringResource(R.string.session_phase_running)
        SessionPhase.STOPPED -> stringResource(R.string.session_phase_stopped)
    }
}

@Composable
internal fun workoutStateLabel(runnerState: RunnerState): String {
    return when {
        runnerState.running && runnerState.paused ->
            stringResource(R.string.session_workout_state_paused)

        runnerState.running ->
            stringResource(R.string.session_workout_state_running)

        runnerState.done ->
            stringResource(R.string.session_workout_state_done)

        else ->
            stringResource(R.string.session_workout_state_stopped)
    }
}

@Composable
internal fun MetricsGrid(
    items: List<MetricItem>,
    columns: Int,
    cardBorder: BorderStroke? = null,
    scale: Float = 1f,
) {
    val safeScale = scale.coerceIn(0.6f, 1f)
    val spacing = 10.dp * safeScale
    val widthFraction = safeScale

    if (columns <= 1) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(widthFraction),
                verticalArrangement = Arrangement.spacedBy(spacing),
            ) {
                items.forEach { item ->
                    MetricCard(
                        label = item.label,
                        value = item.value,
                        modifier = Modifier.fillMaxWidth(),
                        border = cardBorder,
                        scale = safeScale,
                    )
                }
            }
        }
        return
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(widthFraction),
            verticalArrangement = Arrangement.spacedBy(spacing),
        ) {
            items.chunked(columns).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing)
                ) {
                    rowItems.forEach { item ->
                        MetricCard(
                            label = item.label,
                            value = item.value,
                            modifier = Modifier.weight(1f),
                            border = cardBorder,
                            scale = safeScale,
                        )
                    }
                    repeat(columns - rowItems.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
internal fun MetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueStyle: TextStyle = MaterialTheme.typography.headlineSmall,
    valueMaxLines: Int = Int.MAX_VALUE,
    border: BorderStroke? = null,
    scale: Float = 1f,
) {
    val safeScale = scale.coerceIn(0.6f, 1f)

    Card(
        modifier = modifier,
        border = border,
        colors = CardDefaults.elevatedCardColors(),
        elevation = CardDefaults.elevatedCardElevation(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp * safeScale, vertical = 12.dp * safeScale),
            verticalArrangement = Arrangement.spacedBy(4.dp * safeScale)
        ) {
            Text(
                text = label,
                style = scaledTextStyle(MaterialTheme.typography.labelLarge, safeScale),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = scaledTextStyle(valueStyle, safeScale),
                fontWeight = FontWeight.SemiBold,
                maxLines = valueMaxLines,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun scaledTextStyle(base: TextStyle, scale: Float): TextStyle {
    return base.copy(
        fontSize = base.fontSize * scale,
        lineHeight = base.lineHeight * scale,
        letterSpacing = base.letterSpacing * scale,
    )
}

@Composable
internal fun SectionCard(
    title: String?,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    border: BorderStroke? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        border = border,
        colors = CardDefaults.elevatedCardColors(),
        elevation = CardDefaults.elevatedCardElevation(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (compact) 8.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 12.dp)
        ) {
            if (!title.isNullOrBlank()) {
                Text(
                    text = title,
                    style = if (compact) {
                        MaterialTheme.typography.titleSmall
                    } else {
                        MaterialTheme.typography.titleMedium
                    },
                    fontWeight = FontWeight.SemiBold
                )
            }
            content()
        }
    }
}

internal fun format1(value: Double?, fallback: String): String {
    return value?.let { String.format(Locale.getDefault(), "%.1f", it) } ?: fallback
}

internal fun format0(value: Int?, fallback: String): String {
    return value?.toString() ?: fallback
}

/**
 * Formats seconds as `mm:ss` for human readability.
 */
internal fun formatTime(seconds: Int?, fallback: String): String {
    val safeSeconds = seconds ?: return fallback
    val minutes = safeSeconds / 60
    val remainingSeconds = safeSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, remainingSeconds)
}

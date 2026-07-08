package io.github.ewoc2026.ewoc.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.ewoc2026.ewoc.R
import io.github.ewoc2026.ewoc.baseline.BaselineFitnessTestConfidence
import io.github.ewoc2026.ewoc.baseline.BaselineFitnessTestResult
import io.github.ewoc2026.ewoc.baseline.BaselineFitnessTestRuntimeSnapshot
import io.github.ewoc2026.ewoc.baseline.BaselineFitnessTestStopReason
import io.github.ewoc2026.ewoc.baseline.BaselineFitnessTestUiPhase
import io.github.ewoc2026.ewoc.baseline.BaselineFitnessZoneCalculator

/**
 * Dedicated screen for the baseline FTP ramp test.
 *
 * Consumes [snapshot] from [BaselineFitnessTestRuntimeSnapshot] and routes all user actions
 * back to the coordinator through the ViewModel action callbacks. This screen owns no protocol
 * logic — it is a pure stateless consumer of the existing coordinator seam.
 */
@Composable
internal fun BaselineFitnessTestScreen(
    snapshot: BaselineFitnessTestRuntimeSnapshot,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onCancel: () -> Unit,
    onAcceptAdvisoryFallback: () -> Unit,
    onDeclineAdvisoryFallback: () -> Unit,
    onSkipCooldown: () -> Unit,
    onBack: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BaselineScreenTitle(snapshot)

            when (snapshot.phase) {
                BaselineFitnessTestUiPhase.IDLE -> {
                    BaselineIdleContent(onStart = onStart, onBack = onBack)
                }

                BaselineFitnessTestUiPhase.PRECHECK,
                BaselineFitnessTestUiPhase.REQUESTING_CONTROL,
                BaselineFitnessTestUiPhase.ADVISORY_FALLBACK_PROMPT -> {
                    // ADVISORY_FALLBACK_PROMPT is handled as an AlertDialog overlay above;
                    // show the preparing state underneath while the dialog is visible.
                    BaselinePreparingContent(onCancel = onCancel)
                }

                BaselineFitnessTestUiPhase.WARMUP -> {
                    BaselineActiveContent(
                        snapshot = snapshot,
                        actionRow = {
                            OutlinedButton(
                                onClick = onCancel,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(R.string.baseline_fitness_test_cancel_button))
                            }
                        },
                    )
                }

                BaselineFitnessTestUiPhase.RAMP_ACTIVE -> {
                    BaselineActiveContent(
                        snapshot = snapshot,
                        actionRow = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedButton(
                                    onClick = onCancel,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(stringResource(R.string.baseline_fitness_test_cancel_button))
                                }
                                Button(
                                    onClick = onStop,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(stringResource(R.string.baseline_fitness_test_stop_button))
                                }
                            }
                        },
                    )
                }

                BaselineFitnessTestUiPhase.COOLDOWN -> {
                    BaselineActiveContent(
                        snapshot = snapshot,
                        actionRow = {
                            OutlinedButton(
                                onClick = onSkipCooldown,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(R.string.baseline_fitness_test_skip_cooldown_button))
                            }
                        },
                    )
                }

                BaselineFitnessTestUiPhase.RESULT_READY -> {
                    val result = snapshot.result
                    if (result != null) {
                        BaselineResultContent(result = result)
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = onBack,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.baseline_fitness_test_back_button))
                    }
                }

                BaselineFitnessTestUiPhase.INVALID -> {
                    Text(
                        text = stringResource(R.string.baseline_fitness_test_invalid_reason),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = onBack,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.baseline_fitness_test_back_button))
                    }
                }

                BaselineFitnessTestUiPhase.CANCELLED -> {
                    val reason = snapshot.result?.stopReason?.toDisplayString()
                    Text(
                        text = reason ?: stringResource(R.string.baseline_fitness_test_cancelled_reason_user),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = onBack,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.baseline_fitness_test_back_button))
                    }
                }

                BaselineFitnessTestUiPhase.UNAVAILABLE -> {
                    Text(
                        text = stringResource(R.string.baseline_fitness_test_unavailable_reason),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = onBack,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.baseline_fitness_test_back_button))
                    }
                }
            }
        }

        if (snapshot.advisoryFallbackPromptVisible) {
            AlertDialog(
                onDismissRequest = onDeclineAdvisoryFallback,
                title = { Text(stringResource(R.string.baseline_fitness_test_advisory_dialog_title)) },
                text = { Text(stringResource(R.string.baseline_fitness_test_advisory_dialog_body)) },
                confirmButton = {
                    Button(onClick = onAcceptAdvisoryFallback) {
                        Text(stringResource(R.string.baseline_fitness_test_accept_advisory_button))
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDeclineAdvisoryFallback) {
                        Text(stringResource(R.string.baseline_fitness_test_decline_advisory_button))
                    }
                },
            )
        }
    }
}

@Composable
private fun BaselineScreenTitle(snapshot: BaselineFitnessTestRuntimeSnapshot) {
    val title = when (snapshot.phase) {
        BaselineFitnessTestUiPhase.IDLE -> stringResource(R.string.baseline_fitness_test_phase_idle_title)
        BaselineFitnessTestUiPhase.PRECHECK,
        BaselineFitnessTestUiPhase.REQUESTING_CONTROL,
        BaselineFitnessTestUiPhase.ADVISORY_FALLBACK_PROMPT -> stringResource(R.string.baseline_fitness_test_screen_title)
        BaselineFitnessTestUiPhase.WARMUP -> stringResource(R.string.baseline_fitness_test_phase_warmup)
        BaselineFitnessTestUiPhase.RAMP_ACTIVE -> stringResource(
            R.string.baseline_fitness_test_phase_ramp,
            (snapshot.currentRampMinuteNumber ?: 1),
        )
        BaselineFitnessTestUiPhase.COOLDOWN -> stringResource(R.string.baseline_fitness_test_phase_cooldown)
        BaselineFitnessTestUiPhase.RESULT_READY -> stringResource(R.string.baseline_fitness_test_phase_result_ready)
        BaselineFitnessTestUiPhase.INVALID -> stringResource(R.string.baseline_fitness_test_phase_invalid)
        BaselineFitnessTestUiPhase.CANCELLED -> stringResource(R.string.baseline_fitness_test_phase_cancelled)
        BaselineFitnessTestUiPhase.UNAVAILABLE -> stringResource(R.string.baseline_fitness_test_phase_unavailable)
    }
    Text(
        text = title,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun BaselineIdleContent(
    onStart: () -> Unit,
    onBack: () -> Unit,
) {
    Text(
        text = stringResource(R.string.baseline_fitness_test_phase_idle_description_intro),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        text = stringResource(R.string.baseline_fitness_test_phase_idle_description_protocol),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        text = stringResource(R.string.baseline_fitness_test_phase_idle_description_effort),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        text = stringResource(R.string.baseline_fitness_test_phase_idle_description_start_detail),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Medium,
    )
    Spacer(Modifier.height(8.dp))
    Button(
        onClick = onStart,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.baseline_fitness_test_start_button))
    }
    OutlinedButton(
        onClick = onBack,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.baseline_fitness_test_back_button))
    }
}

@Composable
private fun BaselinePreparingContent(onCancel: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator()
        Text(
            text = stringResource(R.string.baseline_fitness_test_phase_preparing),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
    Spacer(Modifier.height(8.dp))
    OutlinedButton(
        onClick = onCancel,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.baseline_fitness_test_cancel_button))
    }
}

@Composable
private fun BaselineActiveContent(
    snapshot: BaselineFitnessTestRuntimeSnapshot,
    actionRow: @Composable () -> Unit,
) {
    // Control mode label
    val controlModeLabel = when (snapshot.controlMode) {
        io.github.ewoc2026.ewoc.baseline.BaselineFitnessTestControlMode.ERG ->
            stringResource(R.string.baseline_fitness_test_label_control_mode_erg)
        io.github.ewoc2026.ewoc.baseline.BaselineFitnessTestControlMode.ADVISORY ->
            stringResource(R.string.baseline_fitness_test_label_control_mode_advisory)
        null -> null
    }
    if (controlModeLabel != null) {
        Text(
            text = controlModeLabel,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }

    // Timer rows
    val timerText = when (snapshot.phase) {
        BaselineFitnessTestUiPhase.WARMUP -> {
            val remaining = snapshot.warmupRemainingSeconds ?: 0
            "${stringResource(R.string.baseline_fitness_test_label_remaining)}: ${remaining.toMmSs()}"
        }
        BaselineFitnessTestUiPhase.RAMP_ACTIVE -> {
            val elapsed = snapshot.rampElapsedSeconds ?: 0
            "${stringResource(R.string.baseline_fitness_test_label_elapsed)}: ${elapsed.toMmSs()}"
        }
        BaselineFitnessTestUiPhase.COOLDOWN -> {
            val remaining = snapshot.cooldownRemainingSeconds ?: 0
            "${stringResource(R.string.baseline_fitness_test_label_remaining)}: ${remaining.toMmSs()}"
        }
        else -> null
    }
    if (timerText != null) {
        Text(
            text = timerText,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }

    if (snapshot.awaitingWarmupStartSignal) {
        Text(
            text = stringResource(R.string.baseline_fitness_test_waiting_for_pedaling_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(R.string.baseline_fitness_test_waiting_for_pedaling_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    // Metrics grid
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BaselineMetricColumn(
            label = stringResource(R.string.baseline_fitness_test_label_target),
            value = snapshot.targetWatts?.let { "$it W" } ?: "—",
            modifier = Modifier.weight(1f),
        )
        BaselineMetricColumn(
            label = stringResource(R.string.baseline_fitness_test_label_power),
            value = snapshot.measuredPowerWatts?.let { "$it W" } ?: "—",
            modifier = Modifier.weight(1f),
        )
        BaselineMetricColumn(
            label = stringResource(R.string.baseline_fitness_test_label_cadence),
            value = snapshot.measuredCadenceRpm?.let { "$it rpm" } ?: "—",
            modifier = Modifier.weight(1f),
        )
        if (snapshot.sensorProfile.heartRate) {
            BaselineMetricColumn(
                label = stringResource(R.string.baseline_fitness_test_label_hr),
                value = snapshot.measuredHeartRateBpm?.let { "$it bpm" } ?: "—",
                modifier = Modifier.weight(1f),
            )
        }
    }

    if (snapshot.phase == BaselineFitnessTestUiPhase.RAMP_ACTIVE) {
        Text(
            text = "${stringResource(R.string.baseline_fitness_test_label_valid_steps)}: ${snapshot.validRampMinutes}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    Spacer(Modifier.height(4.dp))
    actionRow()
}

@Composable
private fun BaselineMetricColumn(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BaselineResultContent(result: BaselineFitnessTestResult) {
    val ftpEstimate = result.ftpEstimateWatts
    if (ftpEstimate != null) {
        Text(
            text = stringResource(R.string.baseline_fitness_test_result_ftp_updated, ftpEstimate),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
    }

    val confidenceLabel = when (result.confidence) {
        BaselineFitnessTestConfidence.LOW -> stringResource(R.string.baseline_fitness_test_result_confidence_low)
        BaselineFitnessTestConfidence.MEDIUM -> stringResource(R.string.baseline_fitness_test_result_confidence_medium)
        BaselineFitnessTestConfidence.HIGH -> stringResource(R.string.baseline_fitness_test_result_confidence_high)
        null -> null
    }
    if (confidenceLabel != null) {
        Text(
            text = "${stringResource(R.string.baseline_fitness_test_result_confidence_label)}: $confidenceLabel",
            style = MaterialTheme.typography.bodyMedium,
        )
    }

    val hrEstimate = result.thresholdHrEstimateBpm
    if (hrEstimate != null) {
        Text(
            text = "${stringResource(R.string.baseline_fitness_test_result_hr_label)}: $hrEstimate bpm",
            style = MaterialTheme.typography.bodyMedium,
        )
    }

    if (ftpEstimate != null) {
        Spacer(Modifier.height(8.dp))
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.baseline_fitness_test_result_zones_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        val zones = BaselineFitnessZoneCalculator.calculate(ftpEstimate)
        zones.forEach { zone ->
            val rangeText = if (zone.maxWattsInclusive != null) {
                "${zone.minWatts}–${zone.maxWattsInclusive} W"
            } else {
                "${zone.minWatts}+ W"
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "${zone.code} ${zone.label}",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = rangeText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun BaselineFitnessTestStopReason.toDisplayString(): String {
    return when (this) {
        BaselineFitnessTestStopReason.MANUAL_STOP,
        BaselineFitnessTestStopReason.USER_CANCEL ->
            stringResource(R.string.baseline_fitness_test_cancelled_reason_user)
        BaselineFitnessTestStopReason.CONTROL_LOST_MID_TEST ->
            stringResource(R.string.baseline_fitness_test_cancelled_reason_control_lost)
        BaselineFitnessTestStopReason.CADENCE_DROP ->
            stringResource(R.string.baseline_fitness_test_cancelled_reason_cadence)
        BaselineFitnessTestStopReason.POWER_SIGNAL_LOST ->
            stringResource(R.string.baseline_fitness_test_cancelled_reason_power_lost)
        BaselineFitnessTestStopReason.DEVICE_DISCONNECT ->
            stringResource(R.string.baseline_fitness_test_cancelled_reason_disconnect)
        BaselineFitnessTestStopReason.CONTROL_GRANT_DECLINED ->
            stringResource(R.string.baseline_fitness_test_cancelled_reason_advisory_declined)
    }
}

private fun Int.toMmSs(): String {
    val totalSec = coerceAtLeast(0)
    val m = totalSec / 60
    val s = totalSec % 60
    return "%d:%02d".format(m, s)
}

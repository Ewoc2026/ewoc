package io.github.ewoc2026.ewoc.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.ewoc2026.ewoc.DeviceSelectionKind
import io.github.ewoc2026.ewoc.R
import io.github.ewoc2026.ewoc.ScannedBleDevice
import io.github.ewoc2026.ewoc.ui.components.disabledVisibleButtonColors

/**
 * DEVICES step content within the menu setup detail flow.
 *
 * Renders trainer/HR device cards, search buttons, compatibility check,
 * optional mock trainer controls, and the device picker overlay when a scan is active.
 */
@Composable
internal fun MenuDevicesStepContent(
    state: DevicesSectionState,
    onSearchFtmsDevices: () -> Unit,
    onSearchHrDevices: () -> Unit,
    onRunCompatibilityCheck: () -> Unit,
    onMockTrainerModeChanged: (Boolean) -> Unit,
    onScannedDeviceSelected: (ScannedBleDevice) -> Unit,
    onDismissDeviceSelection: () -> Unit,
    isTwoColumn: Boolean = false,
    isDense: Boolean = false,
) {
    val pickerHeight = if (isDense) 160.dp else 280.dp
    if (isTwoColumn) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DevicesSelectionCard(
                    state = state,
                    onSearchFtmsDevices = onSearchFtmsDevices,
                    onSearchHrDevices = onSearchHrDevices,
                    onScannedDeviceSelected = onScannedDeviceSelected,
                    onDismissDeviceSelection = onDismissDeviceSelection,
                    pickerMaxHeight = pickerHeight,
                    isCompact = isDense,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DevicesToolsCard(
                    state = state,
                    onRunCompatibilityCheck = onRunCompatibilityCheck,
                    onMockTrainerModeChanged = onMockTrainerModeChanged,
                    isCompact = isDense,
                )
            }
        }
    } else {
        DevicesSingleColumn(
            state = state,
            onSearchFtmsDevices = onSearchFtmsDevices,
            onSearchHrDevices = onSearchHrDevices,
            onRunCompatibilityCheck = onRunCompatibilityCheck,
            onMockTrainerModeChanged = onMockTrainerModeChanged,
            onScannedDeviceSelected = onScannedDeviceSelected,
            onDismissDeviceSelection = onDismissDeviceSelection,
            pickerMaxHeight = pickerHeight,
            isCompact = isDense,
        )
    }
}

@Composable
private fun DevicesSelectionCard(
    state: DevicesSectionState,
    onSearchFtmsDevices: () -> Unit,
    onSearchHrDevices: () -> Unit,
    onScannedDeviceSelected: (ScannedBleDevice) -> Unit,
    onDismissDeviceSelection: () -> Unit,
    pickerMaxHeight: Dp = 280.dp,
    isCompact: Boolean = false,
) {
    val normalTextColor = menuNormalTextColor()
    val supportingTextColor = MaterialTheme.colorScheme.onSurfaceVariant

    SectionCard(title = stringResource(R.string.menu_setup_step_devices_title), compact = isCompact) {
        Text(
            text = stringResource(R.string.menu_setup_devices_hint),
            style = MaterialTheme.typography.bodySmall,
            color = normalTextColor,
        )
        Text(
            text = stringResource(R.string.menu_devices_help_scan_precondition),
            style = MaterialTheme.typography.bodySmall,
            color = supportingTextColor,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            DeviceSelectionInfoCard(
                label = stringResource(R.string.menu_trainer_device_label),
                value = state.trainerDisplayName,
                indicatorState = state.trainerIndicatorState,
                compactLabel = true,
                modifier = Modifier.weight(1f),
            )
            DeviceSelectionInfoCard(
                label = stringResource(R.string.menu_hr_device_label),
                value = state.hrDisplayName,
                indicatorState = state.hrIndicatorState,
                compactLabel = true,
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = onSearchFtmsDevices,
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp),
                colors = menuSecondaryButtonColors()
            ) {
                Text(stringResource(R.string.menu_search_trainer_devices_short))
            }
            Button(
                onClick = onSearchHrDevices,
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp),
                colors = menuSecondaryButtonColors()
            ) {
                Text(stringResource(R.string.menu_search_hr_devices_short))
            }
        }
        DevicePickerContent(
            state = state,
            onScannedDeviceSelected = onScannedDeviceSelected,
            onDismissDeviceSelection = onDismissDeviceSelection,
            pickerMaxHeight = pickerMaxHeight,
        )
    }
}

@Composable
private fun DevicesToolsCard(
    state: DevicesSectionState,
    onRunCompatibilityCheck: () -> Unit,
    onMockTrainerModeChanged: (Boolean) -> Unit,
    isCompact: Boolean = false,
) {
    val normalTextColor = menuNormalTextColor()
    val supportingTextColor = MaterialTheme.colorScheme.onSurfaceVariant

    SectionCard(title = stringResource(R.string.compatibility_check_run_button), compact = isCompact) {
        if (state.ftmsSelected) {
            Text(
                text = stringResource(R.string.menu_devices_help_compatibility_intro),
                style = MaterialTheme.typography.bodySmall,
                color = supportingTextColor,
            )
        } else {
            Text(
                text = stringResource(R.string.menu_devices_error_select_trainer_next_step),
                style = MaterialTheme.typography.bodySmall,
                color = menuErrorTextColor(),
            )
        }
        OutlinedButton(
            onClick = onRunCompatibilityCheck,
            enabled = state.ftmsSelected && !state.compatibilityCheckInProgress,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (state.compatibilityCheckInProgress) {
                    stringResource(R.string.compatibility_check_running_button)
                } else {
                    stringResource(R.string.compatibility_check_run_button)
                },
            )
        }
        if (!state.compatibilityCheckStatusMessage.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = state.compatibilityCheckStatusMessage,
                style = MaterialTheme.typography.bodySmall,
                color = normalTextColor,
            )
        }
        DevicesDebugControls(
            state = state,
            onMockTrainerModeChanged = onMockTrainerModeChanged,
        )
    }
}

/** Original single-column layout used on phones. */
@Composable
private fun DevicesSingleColumn(
    state: DevicesSectionState,
    onSearchFtmsDevices: () -> Unit,
    onSearchHrDevices: () -> Unit,
    onRunCompatibilityCheck: () -> Unit,
    onMockTrainerModeChanged: (Boolean) -> Unit,
    onScannedDeviceSelected: (ScannedBleDevice) -> Unit,
    onDismissDeviceSelection: () -> Unit,
    pickerMaxHeight: Dp = 280.dp,
    isCompact: Boolean = false,
) {
    val normalTextColor = menuNormalTextColor()
    val supportingTextColor = MaterialTheme.colorScheme.onSurfaceVariant

    SectionCard(title = stringResource(R.string.menu_setup_step_devices_title), compact = isCompact) {
        Text(
            text = stringResource(R.string.menu_setup_devices_hint),
            style = MaterialTheme.typography.bodySmall,
            color = normalTextColor,
        )
        Text(
            text = stringResource(R.string.menu_devices_help_scan_precondition),
            style = MaterialTheme.typography.bodySmall,
            color = supportingTextColor,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            DeviceSelectionInfoCard(
                label = stringResource(R.string.menu_trainer_device_label),
                value = state.trainerDisplayName,
                indicatorState = state.trainerIndicatorState,
                compactLabel = state.showTwoPane,
                modifier = Modifier.weight(1f),
            )
            DeviceSelectionInfoCard(
                label = stringResource(R.string.menu_hr_device_label),
                value = state.hrDisplayName,
                indicatorState = state.hrIndicatorState,
                compactLabel = state.showTwoPane,
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = onSearchFtmsDevices,
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp),
                colors = menuSecondaryButtonColors()
            ) {
                Text(stringResource(R.string.menu_search_trainer_devices_short))
            }
            Button(
                onClick = onSearchHrDevices,
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp),
                colors = menuSecondaryButtonColors()
            ) {
                Text(stringResource(R.string.menu_search_hr_devices_short))
            }
        }
        DevicePickerContent(
            state = state,
            onScannedDeviceSelected = onScannedDeviceSelected,
            onDismissDeviceSelection = onDismissDeviceSelection,
            pickerMaxHeight = pickerMaxHeight,
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (state.ftmsSelected) {
            Text(
                text = stringResource(R.string.menu_devices_help_compatibility_intro),
                style = MaterialTheme.typography.bodySmall,
                color = supportingTextColor,
            )
        } else {
            Text(
                text = stringResource(R.string.menu_devices_error_select_trainer_next_step),
                style = MaterialTheme.typography.bodySmall,
                color = menuErrorTextColor(),
            )
        }
        OutlinedButton(
            onClick = onRunCompatibilityCheck,
            enabled = state.ftmsSelected && !state.compatibilityCheckInProgress,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (state.compatibilityCheckInProgress) {
                    stringResource(R.string.compatibility_check_running_button)
                } else {
                    stringResource(R.string.compatibility_check_run_button)
                },
            )
        }
        if (!state.compatibilityCheckStatusMessage.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = state.compatibilityCheckStatusMessage,
                style = MaterialTheme.typography.bodySmall,
                color = normalTextColor,
            )
        }
        DevicesDebugControls(
            state = state,
            onMockTrainerModeChanged = onMockTrainerModeChanged,
        )
    }
}

@Composable
private fun DevicePickerContent(
    state: DevicesSectionState,
    onScannedDeviceSelected: (ScannedBleDevice) -> Unit,
    onDismissDeviceSelection: () -> Unit,
    pickerMaxHeight: Dp = 280.dp,
) {
    val pickerStatusColor = menuPickerStatusColor()
    val pickerWarningColor = menuPickerWarningColor()
    val pickerNeutralColor = menuPickerNeutralColor()

    if (state.activeDeviceSelectionKind != null) {
        Spacer(modifier = Modifier.height(8.dp))
        val unknownDeviceName = stringResource(R.string.menu_device_unknown_name)
        val pickerTitle = when (state.activeDeviceSelectionKind) {
            DeviceSelectionKind.FTMS -> stringResource(R.string.menu_trainer_picker_title)
            DeviceSelectionKind.HEART_RATE -> stringResource(R.string.menu_hr_picker_title)
        }
        SectionCard(title = pickerTitle) {
            if (state.deviceScanStatus != null) {
                val scanStatusText = if (state.deviceScanInProgress) {
                    val dotsTransition =
                        rememberInfiniteTransition(label = "deviceScanDots")
                    val dotsProgress = dotsTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 3f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(
                                durationMillis = 1400,
                                easing = LinearEasing,
                            ),
                            repeatMode = RepeatMode.Restart,
                        ),
                        label = "deviceScanDotsProgress",
                    ).value
                    val dotsCount = dotsProgress.toInt().coerceIn(0, 2) + 1
                    val baseText = state.deviceScanStatus.trimEnd().trimEnd('.', '…')
                    "$baseText${".".repeat(dotsCount)}"
                } else {
                    state.deviceScanStatus
                }
                Text(
                    text = scanStatusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = pickerStatusColor
                )
            }
            if (state.deviceScanInProgress) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.menu_devices_help_scanning),
                    style = MaterialTheme.typography.bodySmall,
                    color = pickerStatusColor,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = pickerMaxHeight),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(
                    items = state.scannedDevices,
                    key = { device -> device.macAddress },
                ) { device ->
                    val label = buildString {
                        val baseName = device.displayName?.takeIf { it.isNotBlank() }
                            ?: unknownDeviceName
                        append(baseName)
                        append(" • RSSI ")
                        append(device.rssi)
                    }
                    Button(
                        onClick = { onScannedDeviceSelected(device) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = disabledVisibleButtonColors()
                    ) {
                        Text(
                            text = label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            val dismissContentColor =
                if (state.deviceScanInProgress) pickerWarningColor else pickerNeutralColor
            val dismissBorderColor =
                if (state.deviceScanInProgress) {
                    pickerWarningColor
                } else {
                    pickerNeutralColor.copy(alpha = 0.75f)
                }

            OutlinedButton(
                onClick = onDismissDeviceSelection,
                enabled = if (state.deviceScanInProgress) state.deviceScanStopEnabled else true,
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, dismissBorderColor),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = dismissContentColor,
                )
            ) {
                Text(
                    stringResource(
                        if (state.deviceScanInProgress) {
                            R.string.menu_cancel_device_scan
                        } else {
                            R.string.menu_close_device_picker
                        }
                    )
                )
            }
        }
    }
}

@Composable
private fun DevicesDebugControls(
    state: DevicesSectionState,
    onMockTrainerModeChanged: (Boolean) -> Unit,
) {
    val normalTextColor = menuNormalTextColor()

    if (state.showMockTrainerControls) {
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(R.string.menu_mock_trainer_toggle_label),
                    style = MaterialTheme.typography.bodyMedium,
                    color = normalTextColor,
                )
                Text(
                    text = stringResource(R.string.menu_mock_trainer_toggle_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.mockTrainerModeEnabled) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Switch(
                checked = state.mockTrainerModeEnabled,
                onCheckedChange = onMockTrainerModeChanged,
            )
        }
    }
}

package com.example.ergometerapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ergometerapp.R
import com.example.ergometerapp.SessionDebugProbeSignal

@Composable
internal fun SessionDebugProbeOverlayHost(
    title: String?,
    message: String?,
    diagnostics: String?,
    lastSignalReceipt: String?,
    onSignal: (SessionDebugProbeSignal) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.BottomCenter,
    ) {
        SessionDebugProbeOverlay(
            title = title ?: stringResource(R.string.session_debug_probe_default_title),
            message = message ?: stringResource(R.string.session_debug_probe_default_message),
            diagnostics = diagnostics,
            lastSignalReceipt = lastSignalReceipt,
            onSignal = onSignal,
        )
    }
}

@Composable
private fun SessionDebugProbeOverlay(
    title: String,
    message: String,
    diagnostics: String?,
    lastSignalReceipt: String?,
    onSignal: (SessionDebugProbeSignal) -> Unit,
) {
    val overlayContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f)
    val overlayContentColor = MaterialTheme.colorScheme.onSurface
    val outlineButtonContentColor = MaterialTheme.colorScheme.onSurfaceVariant
    val signalLocked = isSessionDebugProbeSignalLocked(lastSignalReceipt)

    ElevatedCard(
        modifier = Modifier
            .padding(16.dp)
            .widthIn(max = 920.dp)
            .fillMaxWidth()
            .testTag(DebugAutomationTags.SESSION_DEBUG_PROBE_OVERLAY),
        colors = CardDefaults.elevatedCardColors(
            containerColor = overlayContainerColor,
            contentColor = overlayContentColor,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = overlayContentColor,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = overlayContentColor,
            )
            if (diagnostics != null) {
                Text(
                    text = diagnostics,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (lastSignalReceipt != null) {
                Text(
                    text = lastSignalReceipt,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = stringResource(R.string.session_debug_probe_locked_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = { onSignal(SessionDebugProbeSignal.READY) },
                    enabled = !signalLocked,
                    modifier = Modifier
                        .weight(1f)
                        .testTag(DebugAutomationTags.SESSION_DEBUG_PROBE_READY),
                ) {
                    Text(stringResource(R.string.session_debug_probe_signal_ready))
                }
                Button(
                    onClick = { onSignal(SessionDebugProbeSignal.SMOOTH) },
                    enabled = !signalLocked,
                    modifier = Modifier
                        .weight(1f)
                        .testTag(DebugAutomationTags.SESSION_DEBUG_PROBE_SMOOTH),
                ) {
                    Text(stringResource(R.string.session_debug_probe_signal_smooth))
                }
                Button(
                    onClick = { onSignal(SessionDebugProbeSignal.NOTICEABLE) },
                    enabled = !signalLocked,
                    modifier = Modifier
                        .weight(1f)
                        .testTag(DebugAutomationTags.SESSION_DEBUG_PROBE_NOTICEABLE),
                ) {
                    Text(stringResource(R.string.session_debug_probe_signal_noticeable))
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = { onSignal(SessionDebugProbeSignal.UNSAFE) },
                    enabled = !signalLocked,
                    modifier = Modifier
                        .weight(1f)
                        .testTag(DebugAutomationTags.SESSION_DEBUG_PROBE_UNSAFE),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = outlineButtonContentColor,
                    ),
                ) {
                    Text(
                        text = stringResource(R.string.session_debug_probe_signal_unsafe),
                        color = outlineButtonContentColor,
                    )
                }
                OutlinedButton(
                    onClick = { onSignal(SessionDebugProbeSignal.ABORT) },
                    enabled = !signalLocked,
                    modifier = Modifier
                        .weight(1f)
                        .testTag(DebugAutomationTags.SESSION_DEBUG_PROBE_ABORT),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = outlineButtonContentColor,
                    ),
                ) {
                    Text(
                        text = stringResource(R.string.session_debug_probe_signal_abort),
                        color = outlineButtonContentColor,
                    )
                }
            }
        }
    }
}

internal fun isSessionDebugProbeSignalLocked(lastSignalReceipt: String?): Boolean =
    lastSignalReceipt != null

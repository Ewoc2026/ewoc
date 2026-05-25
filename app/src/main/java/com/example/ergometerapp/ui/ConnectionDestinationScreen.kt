package com.example.ergometerapp.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.ergometerapp.R
import com.example.ergometerapp.ui.theme.UiSpacing

/**
 * Transitional screen shown while FTMS setup is completing.
 */
@Composable
internal fun ConnectingScreen(
    timeoutMessage: String? = null,
    onKeepWaiting: () -> Unit = {},
    onRetry: () -> Unit = {},
    onBackToMenu: () -> Unit = {},
) {
    TransitionStatusScreen(
        statusText = stringResource(R.string.status_connecting),
        hintText = stringResource(R.string.menu_connection_hint),
    ) {
        if (timeoutMessage != null) {
            TransitionRecoveryCard(
                title = stringResource(R.string.connecting_timeout_title),
                message = timeoutMessage,
                modifier = Modifier
                    .padding(top = UiSpacing.lg, start = UiSpacing.xl, end = UiSpacing.xl)
                    .fillMaxWidth(),
            ) {
                Button(
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.connecting_timeout_retry))
                }
                OutlinedButton(
                    onClick = onKeepWaiting,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.connecting_timeout_keep_waiting))
                }
                TextButton(
                    onClick = onBackToMenu,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(stringResource(R.string.connecting_timeout_back_to_menu))
                }
            }
        }
    }
}

/**
 * Transitional screen shown while waiting for STOP acknowledgment before summary.
 */
@Composable
internal fun StoppingScreen() {
    TransitionStatusScreen(
        statusText = stringResource(R.string.status_stopping),
        hintText = stringResource(R.string.status_stopping_hint),
    )
}

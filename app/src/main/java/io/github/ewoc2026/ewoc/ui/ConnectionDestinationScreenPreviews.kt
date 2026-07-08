package io.github.ewoc2026.ewoc.ui

import androidx.compose.runtime.Composable

@DestinationScreenPreviews
@Composable
private fun ConnectingScreenPreview() {
    ScreenPreviewTheme {
        ConnectingScreen()
    }
}

@DestinationScreenPreviews
@Composable
private fun ConnectingScreenTimeoutPreview() {
    ScreenPreviewTheme {
        ConnectingScreen(
            timeoutMessage = "The trainer did not grant control in time. Retry or keep waiting.",
        )
    }
}

@DestinationScreenPreviews
@Composable
private fun StoppingScreenPreview() {
    ScreenPreviewTheme {
        StoppingScreen()
    }
}

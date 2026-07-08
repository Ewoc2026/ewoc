package io.github.ewoc2026.ewoc.ui

import androidx.compose.runtime.Composable
import io.github.ewoc2026.ewoc.baseline.BaselineFitnessTestControlMode
import io.github.ewoc2026.ewoc.baseline.BaselineFitnessTestRuntimeSnapshot
import io.github.ewoc2026.ewoc.baseline.BaselineFitnessTestUiPhase

@DestinationScreenPreviews
@Composable
private fun BaselineFitnessTestScreenIdlePreview() {
    ScreenPreviewTheme {
        BaselineFitnessTestScreen(
            snapshot = BaselineFitnessTestRuntimeSnapshot(),
            onStart = {},
            onStop = {},
            onCancel = {},
            onAcceptAdvisoryFallback = {},
            onDeclineAdvisoryFallback = {},
            onSkipCooldown = {},
            onBack = {},
        )
    }
}

@DestinationScreenPreviews
@Composable
private fun BaselineFitnessTestScreenRampPreview() {
    ScreenPreviewTheme {
        BaselineFitnessTestScreen(
            snapshot = BaselineFitnessTestRuntimeSnapshot(
                phase = BaselineFitnessTestUiPhase.RAMP_ACTIVE,
                controlMode = BaselineFitnessTestControlMode.ERG,
                startWatts = 100,
                targetWatts = 180,
                measuredPowerWatts = 176,
                measuredCadenceRpm = 88,
                measuredHeartRateBpm = 152,
                rampElapsedSeconds = 240,
                currentRampMinuteNumber = 5,
                validRampMinutes = 4,
            ),
            onStart = {},
            onStop = {},
            onCancel = {},
            onAcceptAdvisoryFallback = {},
            onDeclineAdvisoryFallback = {},
            onSkipCooldown = {},
            onBack = {},
        )
    }
}

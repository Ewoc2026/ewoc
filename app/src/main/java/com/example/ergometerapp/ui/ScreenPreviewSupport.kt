package com.example.ergometerapp.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.ergometerapp.DocumentsFolderWorkoutOption
import com.example.ergometerapp.ScannedBleDevice
import com.example.ergometerapp.ftms.IndoorBikeData
import com.example.ergometerapp.session.SessionSummary
import com.example.ergometerapp.ui.theme.EwocTheme
import com.example.ergometerapp.workout.ImportedErgoWorkout
import com.example.ergometerapp.workout.ImportedErgoWorkoutStep
import com.example.ergometerapp.workout.Step
import com.example.ergometerapp.workout.WorkoutFile
import com.example.ergometerapp.workout.WorkoutTextEvent
import com.example.ergometerapp.workout.runner.RunnerState

/**
 * Shared multi-device preview matrix for destination screens.
 *
 * Keeping one annotation centralizes the preview baseline so future screens
 * inherit the same phone/tablet and large-font coverage without copy-pasting.
 */
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
@Preview(
    name = "Phone Portrait",
    widthDp = 411,
    heightDp = 891,
    showBackground = true,
)
@Preview(
    name = "Phone Landscape",
    widthDp = 891,
    heightDp = 411,
    showBackground = true,
)
@Preview(
    name = "Tablet Portrait",
    widthDp = 800,
    heightDp = 1_280,
    showBackground = true,
)
@Preview(
    name = "Tablet Landscape",
    widthDp = 1_280,
    heightDp = 800,
    showBackground = true,
)
@Preview(
    name = "Phone Portrait Large Font",
    widthDp = 411,
    heightDp = 891,
    fontScale = 1.3f,
    showBackground = true,
)
internal annotation class DestinationScreenPreviews

/**
 * Focused tablet-landscape audit matrix for layouts that depend heavily on aspect ratio.
 *
 * Keeping this separate avoids inflating every destination-screen preview while still giving
 * high-risk landscape surfaces a reusable 16:10, 4:3, and wide-tablet checkpoint set.
 */
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
@Preview(
    name = "Tablet Landscape 16:10",
    widthDp = 1_280,
    heightDp = 800,
    showBackground = true,
)
@Preview(
    name = "Tablet Landscape 4:3",
    widthDp = 1_024,
    heightDp = 768,
    showBackground = true,
)
@Preview(
    name = "Tablet Landscape Wide",
    widthDp = 1_366,
    heightDp = 768,
    showBackground = true,
)
internal annotation class TabletLandscapeAuditPreviews

@Composable
internal fun ScreenPreviewTheme(content: @Composable () -> Unit) {
    EwocTheme {
        content()
    }
}

internal fun previewWorkout(): WorkoutFile {
    return WorkoutFile(
        name = "Sweet Spot Builder",
        description = "Build sustainable threshold power with one steady main block.",
        author = "Preview",
        tags = listOf("sweetspot", "endurance"),
        steps = listOf(
            Step.Warmup(
                durationSec = 300,
                powerLow = 0.5,
                powerHigh = 0.7,
                cadence = 85,
            ),
            Step.SteadyState(
                durationSec = 1_200,
                power = 0.9,
                cadence = 92,
            ),
            Step.Cooldown(
                durationSec = 240,
                powerLow = 0.6,
                powerHigh = 0.4,
                cadence = 80,
            ),
        ),
        textEvents = listOf(
            WorkoutTextEvent(
                timeOffsetSec = 540,
                message = "Stay relaxed and hold a smooth cadence.",
                durationSec = 18,
            ),
        ),
    )
}

internal fun previewImportedErgoWorkout(): ImportedErgoWorkout {
    return ImportedErgoWorkout(
        title = "Imported Tempo Builder",
        description = "Absolute-watt imported workout used to verify menu metadata surfacing.",
        steps = listOf(
            ImportedErgoWorkoutStep.PowerSteady(
                stepIndex = 0,
                startOffsetSec = 0,
                durationSec = 300,
                watts = 170,
            ),
            ImportedErgoWorkoutStep.PowerRamp(
                stepIndex = 1,
                startOffsetSec = 300,
                durationSec = 480,
                fromWatts = 180,
                toWatts = 235,
            ),
            ImportedErgoWorkoutStep.PowerSteady(
                stepIndex = 2,
                startOffsetSec = 780,
                durationSec = 240,
                watts = 190,
            ),
        ),
        totalDurationSec = 1_020,
    )
}

internal fun previewIndoorBikeData(): IndoorBikeData {
    return IndoorBikeData(
        valid = true,
        instantaneousSpeedKmh = 33.4,
        averageSpeedKmh = 31.8,
        instantaneousCadenceRpm = 91.0,
        averageCadenceRpm = 88.4,
        totalDistanceMeters = 8_450,
        resistanceLevel = 28,
        instantaneousPowerW = 228,
        averagePowerW = 214,
        totalEnergyKcal = 312,
        energyPerHourKcal = 782,
        energyPerMinuteKcal = 13,
        heartRateBpm = 154,
        metabolicEquivalent = 8.2,
        elapsedTimeSeconds = 630,
        remainingTimeSeconds = 1_110,
    )
}

internal fun previewRunnerState(): RunnerState {
    return RunnerState(
        running = true,
        paused = false,
        done = false,
        label = "Sweet Spot",
        targetPowerWatts = 225,
        targetCadence = 90,
        workoutElapsedSec = 630,
        stepRemainingSec = 150,
        intervalPart = null,
    )
}

internal fun previewSessionSummary(): SessionSummary {
    return SessionSummary(
        startTimestampMillis = 1_741_420_800_000,
        stopTimestampMillis = 1_741_424_580_000,
        durationSeconds = 3_780,
        actualTss = 71.6,
        avgPower = 214,
        maxPower = 312,
        avgCadence = 88,
        maxCadence = 104,
        avgHeartRate = 149,
        maxHeartRate = 166,
        distanceMeters = 29_600,
        totalEnergyKcal = 812,
    )
}

internal fun previewScannedDevices(): List<ScannedBleDevice> {
    return listOf(
        ScannedBleDevice(
            macAddress = "AA:BB:CC:11:22:33",
            displayName = "Wahoo KICKR Core",
            rssi = -48,
        ),
        ScannedBleDevice(
            macAddress = "DD:EE:FF:44:55:66",
            displayName = "Elite Suito",
            rssi = -57,
        ),
        ScannedBleDevice(
            macAddress = "11:22:33:44:55:66",
            displayName = null,
            rssi = -69,
        ),
    )
}

internal fun previewDocumentsFolderWorkouts(): List<DocumentsFolderWorkoutOption> {
    return listOf(
        DocumentsFolderWorkoutOption(
            uriString = "content://preview/workouts/sweet-spot-builder.zwo",
            displayName = "sweet-spot-builder.zwo",
        ),
        DocumentsFolderWorkoutOption(
            uriString = "content://preview/workouts/vo2-max-blocks.zwo",
            displayName = "vo2-max-blocks.zwo",
        ),
    )
}

internal fun previewBuiltInWorkouts() = listOf(
    com.example.ergometerapp.BuiltInWorkoutOption(
        assetPath = "workouts/25min_endurance.ewo",
        fileName = "25min_endurance.ewo",
        displayName = "25min Endurance",
    ),
    com.example.ergometerapp.BuiltInWorkoutOption(
        assetPath = "workouts/40min_threshold_intro.ewo",
        fileName = "40min_threshold_intro.ewo",
        displayName = "40min Threshold Intro",
    ),
)

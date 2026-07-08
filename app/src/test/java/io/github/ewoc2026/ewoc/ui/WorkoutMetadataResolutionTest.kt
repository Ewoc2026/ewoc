package io.github.ewoc2026.ewoc.ui

import io.github.ewoc2026.ewoc.SessionSetupMode
import io.github.ewoc2026.ewoc.workout.ImportedErgoWorkout
import io.github.ewoc2026.ewoc.workout.WorkoutFile
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutMetadataResolutionTest {

    @Test
    fun resolveWorkoutDisplayNamePrefersImportedTitleBeforeFileName() {
        val importedWorkout = ImportedErgoWorkout(
            title = "Imported Threshold Builder",
            description = "Progressive absolute-watt steps.",
            steps = emptyList(),
            totalDurationSec = 0,
        )

        val displayName = resolveWorkoutDisplayName(
            selectedWorkout = null,
            selectedImportedWorkout = importedWorkout,
            selectedWorkoutFileName = "imported_threshold_builder.ewo",
            fallback = "Unknown workout",
        )

        assertEquals("Imported Threshold Builder", displayName)
    }

    @Test
    fun resolveWorkoutDisplayNameFallsBackToFileBasenameWhenMetadataIsMissing() {
        val displayName = resolveWorkoutDisplayName(
            selectedWorkout = null,
            selectedImportedWorkout = null,
            selectedWorkoutFileName = "content://docs/workouts/endurance_builder.ewo",
            fallback = "Unknown workout",
        )

        assertEquals("endurance_builder", displayName)
    }

    @Test
    fun resolveWorkoutDisplayNamePrefersTelemetryOnlyLabelOverStaleWorkoutMetadata() {
        val workout = WorkoutFile(
            name = "Stale Editor Workout",
            description = "Should be ignored in telemetry-only mode.",
            author = "Preview",
            tags = emptyList(),
            steps = emptyList(),
            textEvents = emptyList(),
        )

        val displayName = resolveWorkoutDisplayName(
            selectedWorkout = workout,
            selectedImportedWorkout = null,
            selectedSessionSetupMode = SessionSetupMode.TELEMETRY_ONLY,
            selectedWorkoutFileName = "stale_editor_workout.zwo",
            fallback = "Unknown workout",
            telemetryOnlyLabel = "Telemetry only localized",
        )

        assertEquals("Telemetry only localized", displayName)
    }

    @Test
    fun resolveWorkoutDescriptionPrefersImportedDescription() {
        val importedWorkout = ImportedErgoWorkout(
            title = "Imported Threshold Builder",
            description = "Progressive absolute-watt steps.",
            steps = emptyList(),
            totalDurationSec = 0,
        )

        val description = resolveWorkoutDescription(
            selectedWorkout = null,
            selectedImportedWorkout = importedWorkout,
            fallback = "Unknown",
        )

        assertEquals("Progressive absolute-watt steps.", description)
    }

    @Test
    fun resolveWorkoutDescriptionFallsBackWhenBothSourcesAreBlank() {
        val workout = WorkoutFile(
            name = "Recovery Ride",
            description = "   ",
            author = "Preview",
            tags = emptyList(),
            steps = emptyList(),
            textEvents = emptyList(),
        )

        val description = resolveWorkoutDescription(
            selectedWorkout = workout,
            selectedImportedWorkout = null,
            fallback = "Unknown",
        )

        assertEquals("Unknown", description)
    }
}

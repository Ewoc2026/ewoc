package io.github.ewoc2026.ewoc.workout

import android.content.Context
import com.ewo.core.EwoCompileContext

/**
 * Loads the bundled guided workout shipped with the app.
 *
 * The built-in guided workout stays on the same canonical `.ewo` import path
 * as other bundled workouts, but the asset is resolved only from packaged app
 * assets rather than from a user-selected file location. That keeps the guided
 * path fixed even when the rider has editable workout files elsewhere.
 */
internal object StarterWorkoutProvider {
    const val sourceName: String = "22_min_fixed_basic_free_workout.ewo"
    const val assetPath: String = "workouts/Fixed-free/22_min_fixed_basic_free_workout.ewo"

    /**
     * Imports the bundled guided workout through [WorkoutImportService].
     *
     * This keeps the guided path file-backed without widening it to arbitrary
     * user-selected workout files.
     */
    fun load(
        context: Context,
        compileContext: EwoCompileContext,
        importService: WorkoutImportService,
    ): WorkoutImportResult {
        val content = BundledWorkoutAssetCatalog.loadText(context, assetPath)
        return importService.importFromText(
            sourceName = sourceName,
            content = content,
            context = compileContext,
        )
    }
}

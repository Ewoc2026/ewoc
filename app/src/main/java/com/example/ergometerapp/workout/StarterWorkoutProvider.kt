package com.example.ergometerapp.workout

import android.content.Context
import com.ewo.core.EwoCompileContext

/**
 * Loads the bundled guided workout available to Free tier users.
 *
 * The Free-tier workout stays on the same canonical `.ewo` import path as
 * other bundled workouts, but the asset is resolved only from the packaged app
 * assets rather than from a user-selected file location. That keeps the free
 * guided path fixed even when the rider has editable workout files elsewhere.
 */
internal object StarterWorkoutProvider {
    const val sourceName: String = "22_min_fixed_basic_free_workout.ewo"
    const val assetPath: String = "workouts/Fixed-free/22_min_fixed_basic_free_workout.ewo"

    /**
     * Imports the bundled Free-tier workout through [WorkoutImportService].
     *
     * This keeps the guided Free-tier path file-backed without widening
     * Free-tier access to arbitrary user-selected workout files.
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

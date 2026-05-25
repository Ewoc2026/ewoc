package com.example.ergometerapp

import android.content.Context
import android.net.Uri
import androidx.core.content.edit

/**
 * Persists the last workout selection so it can be restored after process death.
 *
 * Stores the [SessionSetupMode], the source URI (when file-based), the packaged
 * bundled asset path (when the workout comes from APK assets), and the display
 * name. On restore the caller re-imports from the original source; if a SAF URI
 * is no longer accessible (e.g. expired permission) the restore is skipped.
 */
object WorkoutSelectionSettingsStorage {
    private const val PREFERENCES_NAME = "ergometer_app_settings"
    private const val KEY_SESSION_SETUP_MODE = "workout_selection_session_setup_mode"
    private const val KEY_WORKOUT_URI = "workout_selection_uri"
    private const val KEY_BUNDLED_WORKOUT_ASSET_PATH = "workout_selection_bundled_asset_path"
    private const val KEY_WORKOUT_FILE_NAME = "workout_selection_file_name"

    fun loadSessionSetupMode(context: Context): SessionSetupMode? {
        val prefs = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_SESSION_SETUP_MODE, null) ?: return null
        return runCatching { SessionSetupMode.valueOf(raw) }.getOrNull()
    }

    fun loadWorkoutUri(context: Context): Uri? {
        val prefs = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_WORKOUT_URI, null) ?: return null
        return runCatching { Uri.parse(raw) }.getOrNull()
    }

    fun loadWorkoutFileName(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_WORKOUT_FILE_NAME, null)?.takeIf { it.isNotBlank() }
    }

    fun loadBundledWorkoutAssetPath(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_BUNDLED_WORKOUT_ASSET_PATH, null)?.takeIf { it.isNotBlank() }
    }

    fun save(
        context: Context,
        mode: SessionSetupMode,
        uri: Uri?,
        fileName: String?,
        bundledWorkoutAssetPath: String? = null,
    ) {
        val prefs = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        prefs.edit {
            putString(KEY_SESSION_SETUP_MODE, mode.name)
            if (uri != null) {
                putString(KEY_WORKOUT_URI, uri.toString())
            } else {
                remove(KEY_WORKOUT_URI)
            }
            if (!bundledWorkoutAssetPath.isNullOrBlank()) {
                putString(KEY_BUNDLED_WORKOUT_ASSET_PATH, bundledWorkoutAssetPath)
            } else {
                remove(KEY_BUNDLED_WORKOUT_ASSET_PATH)
            }
            if (!fileName.isNullOrBlank()) {
                putString(KEY_WORKOUT_FILE_NAME, fileName)
            } else {
                remove(KEY_WORKOUT_FILE_NAME)
            }
        }
    }

    fun clear(context: Context) {
        val prefs = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        prefs.edit {
            remove(KEY_SESSION_SETUP_MODE)
            remove(KEY_WORKOUT_URI)
            remove(KEY_BUNDLED_WORKOUT_ASSET_PATH)
            remove(KEY_WORKOUT_FILE_NAME)
        }
    }
}

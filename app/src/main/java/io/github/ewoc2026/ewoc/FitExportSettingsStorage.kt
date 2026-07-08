package io.github.ewoc2026.ewoc

import android.content.Context
import androidx.core.content.edit

/**
 * User preference for summary FIT export behavior.
 */
enum class FitExportPreference {
    AUTO_SAVE,
    ASK_EVERY_TIME,
    DO_NOT_SAVE,
}

/**
 * Persists summary FIT export preference in app settings storage.
 */
object FitExportSettingsStorage {
    private const val PREFERENCES_NAME = "ergometer_app_settings"
    private const val KEY_FIT_EXPORT_PREFERENCE = "fit_export_preference"

    /**
     * Loads the saved preference. Null means user has not decided yet.
     */
    fun loadPreference(context: Context): FitExportPreference? {
        val prefs = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val stored = prefs.getString(KEY_FIT_EXPORT_PREFERENCE, null) ?: return null
        return runCatching { FitExportPreference.valueOf(stored) }.getOrNull()
    }

    /**
     * Saves or clears the preference.
     */
    fun savePreference(context: Context, preference: FitExportPreference?) {
        val prefs = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        if (preference == null) {
            prefs.edit { remove(KEY_FIT_EXPORT_PREFERENCE) }
            return
        }
        prefs.edit { putString(KEY_FIT_EXPORT_PREFERENCE, preference.name) }
    }
}

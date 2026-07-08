package io.github.ewoc2026.ewoc

import android.content.Context
import androidx.core.content.edit

/**
 * Persists debug-only mock trainer mode for development session flows.
 */
object MockTrainerSettingsStorage {
    private const val PREFERENCES_NAME = "ergometer_app_settings"
    private const val KEY_MOCK_TRAINER_ENABLED = "mock_trainer_enabled"

    fun loadEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_MOCK_TRAINER_ENABLED, false)
    }

    fun saveEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        prefs.edit { putBoolean(KEY_MOCK_TRAINER_ENABLED, enabled) }
    }
}

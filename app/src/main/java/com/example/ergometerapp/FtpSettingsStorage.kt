package com.example.ergometerapp

import android.content.Context
import androidx.core.content.edit

/**
 * Persists user-selected FTP watts value for reuse across app launches.
 */
object FtpSettingsStorage {
    private const val PREFERENCES_NAME = "ergometer_app_settings"
    private const val KEY_FTP_WATTS = "ftp_watts"

    /**
     * Loads FTP watts from app settings and falls back to [defaultFtpWatts] when missing/invalid.
     */
    fun loadFtpWatts(context: Context, defaultFtpWatts: Int): Int {
        val prefs = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val stored = prefs.getInt(KEY_FTP_WATTS, defaultFtpWatts)
        return stored.coerceAtLeast(1)
    }

    /**
     * Saves FTP watts if the value is valid.
     */
    fun saveFtpWatts(context: Context, ftpWatts: Int) {
        if (ftpWatts <= 0) return
        val prefs = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        prefs.edit { putInt(KEY_FTP_WATTS, ftpWatts) }
    }
}

package com.example.ergometerapp

import android.content.Context
import androidx.core.content.edit

/**
 * Biological sex selection used for estimated HR zone calculations.
 */
enum class HrProfileSex {
    MALE,
    FEMALE,
}

/**
 * Persists basic HR profile fields used by session HR-zone estimates.
 */
object HrProfileSettingsStorage {
    private const val PREFERENCES_NAME = "ergometer_app_settings"
    private const val KEY_HR_PROFILE_AGE = "hr_profile_age"
    private const val KEY_HR_PROFILE_SEX = "hr_profile_sex"

    fun loadAge(context: Context): Int? {
        val prefs = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val stored = prefs.getInt(KEY_HR_PROFILE_AGE, -1)
        return stored.takeIf { it in 13..100 }
    }

    fun saveAge(context: Context, age: Int?) {
        val prefs = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        if (age == null || age !in 13..100) {
            prefs.edit { remove(KEY_HR_PROFILE_AGE) }
            return
        }
        prefs.edit { putInt(KEY_HR_PROFILE_AGE, age) }
    }

    fun loadSex(context: Context): HrProfileSex? {
        val prefs = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val stored = prefs.getString(KEY_HR_PROFILE_SEX, null) ?: return null
        return runCatching { HrProfileSex.valueOf(stored) }.getOrNull()
    }

    fun saveSex(context: Context, sex: HrProfileSex?) {
        val prefs = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        if (sex == null) {
            prefs.edit { remove(KEY_HR_PROFILE_SEX) }
            return
        }
        prefs.edit { putString(KEY_HR_PROFILE_SEX, sex.name) }
    }
}

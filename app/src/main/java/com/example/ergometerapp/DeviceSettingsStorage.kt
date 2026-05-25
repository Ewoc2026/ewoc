package com.example.ergometerapp

import android.content.Context
import androidx.core.content.edit

/**
 * Persists user-selected BLE device addresses for FTMS and optional HR sensors.
 */
object DeviceSettingsStorage {
    private const val PREFERENCES_NAME = "ergometer_app_settings"
    private const val KEY_FTMS_DEVICE_MAC = "ftms_device_mac"
    private const val KEY_HR_DEVICE_MAC = "hr_device_mac"
    private const val KEY_FTMS_DEVICE_NAME = "ftms_device_name"
    private const val KEY_HR_DEVICE_NAME = "hr_device_name"

    /**
     * Loads a normalized FTMS device address or null when no valid value exists.
     */
    fun loadFtmsDeviceMac(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val stored = prefs.getString(KEY_FTMS_DEVICE_MAC, null) ?: return null
        return BluetoothMacAddress.normalizeOrNull(stored)
    }

    /**
     * Saves FTMS device address when valid, otherwise clears the stored value.
     */
    fun saveFtmsDeviceMac(context: Context, macAddress: String?) {
        val prefs = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val normalized = macAddress?.let { BluetoothMacAddress.normalizeOrNull(it) }
        if (normalized == null) {
            prefs.edit { remove(KEY_FTMS_DEVICE_MAC) }
            return
        }
        prefs.edit { putString(KEY_FTMS_DEVICE_MAC, normalized) }
    }

    /**
     * Loads the saved FTMS device name used for UI display, when available.
     */
    fun loadFtmsDeviceName(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_FTMS_DEVICE_NAME, null)?.trim()?.takeIf { it.isNotEmpty() }
    }

    /**
     * Saves FTMS device name for UI display, or clears it when null/blank.
     */
    fun saveFtmsDeviceName(context: Context, deviceName: String?) {
        val prefs = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val normalized = deviceName?.trim()?.takeIf { it.isNotEmpty() }
        if (normalized == null) {
            prefs.edit { remove(KEY_FTMS_DEVICE_NAME) }
            return
        }
        prefs.edit { putString(KEY_FTMS_DEVICE_NAME, normalized) }
    }

    /**
     * Loads a normalized HR device address or null when no valid value exists.
     */
    fun loadHrDeviceMac(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val stored = prefs.getString(KEY_HR_DEVICE_MAC, null) ?: return null
        return BluetoothMacAddress.normalizeOrNull(stored)
    }

    /**
     * Saves optional HR device address when valid, otherwise clears the stored value.
     */
    fun saveHrDeviceMac(context: Context, macAddress: String?) {
        val prefs = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val normalized = macAddress?.let { BluetoothMacAddress.normalizeOrNull(it) }
        if (normalized == null) {
            prefs.edit { remove(KEY_HR_DEVICE_MAC) }
            return
        }
        prefs.edit { putString(KEY_HR_DEVICE_MAC, normalized) }
    }

    /**
     * Loads the saved heart-rate device name used for UI display, when available.
     */
    fun loadHrDeviceName(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_HR_DEVICE_NAME, null)?.trim()?.takeIf { it.isNotEmpty() }
    }

    /**
     * Saves heart-rate device name for UI display, or clears it when null/blank.
     */
    fun saveHrDeviceName(context: Context, deviceName: String?) {
        val prefs = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val normalized = deviceName?.trim()?.takeIf { it.isNotEmpty() }
        if (normalized == null) {
            prefs.edit { remove(KEY_HR_DEVICE_NAME) }
            return
        }
        prefs.edit { putString(KEY_HR_DEVICE_NAME, normalized) }
    }
}

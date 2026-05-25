package com.example.ergometerapp

import android.content.Context
import android.net.Uri
import androidx.core.content.edit

/**
 * Persists the user-selected SAF documents tree URI used for shared file workflows.
 */
object SafSharedFolderSettingsStorage {
    private const val PREFERENCES_NAME = "ergometer_app_settings"
    private const val KEY_SHARED_DOCUMENTS_TREE_URI = "shared_documents_tree_uri"

    /**
     * Loads the persisted documents tree URI when available.
     */
    fun loadTreeUri(context: Context): Uri? {
        val prefs = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_SHARED_DOCUMENTS_TREE_URI, null) ?: return null
        return runCatching { Uri.parse(raw) }.getOrNull()
    }

    /**
     * Persists the documents tree URI or clears it when null.
     */
    fun saveTreeUri(context: Context, uri: Uri?) {
        val prefs = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        if (uri == null) {
            prefs.edit { remove(KEY_SHARED_DOCUMENTS_TREE_URI) }
            return
        }
        prefs.edit { putString(KEY_SHARED_DOCUMENTS_TREE_URI, uri.toString()) }
    }
}

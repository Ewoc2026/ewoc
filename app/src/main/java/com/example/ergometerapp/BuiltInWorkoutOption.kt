package com.example.ergometerapp

/**
 * Immutable menu entry for a packaged workout shipped inside the APK assets.
 *
 * The asset path stays internal to the app so the packaged original can be
 * selected or opened in the editor without exposing a writable filesystem path.
 */
data class BuiltInWorkoutOption(
    val assetPath: String,
    val fileName: String,
    val displayName: String,
)

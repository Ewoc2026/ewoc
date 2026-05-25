package com.example.ergometerapp

import java.io.File

/**
 * Resolves debug-only workout import requests onto app-owned storage.
 *
 * Relative paths always target the dedicated staged-workout directory so adb
 * flows can avoid scoped-storage permission drift. Absolute paths stay
 * allowed only when they already point inside app-owned storage roots.
 */
internal object DebugWorkoutAutomationFiles {
    internal const val STAGING_DIRECTORY_NAME = "debug-workouts"

    fun resolveSelectionTarget(
        requestedPath: String,
        stagingDirectory: File?,
        appOwnedRoots: List<File>,
    ): DebugWorkoutSelectionTarget {
        val trimmedPath = requestedPath.trim()
        if (trimmedPath.isEmpty()) {
            return DebugWorkoutSelectionTarget.InvalidRequest
        }
        val requestedFile = File(trimmedPath)
        if (!requestedFile.isAbsolute) {
            val resolvedStagingDirectory = stagingDirectory?.canonicalOrAbsolute()
                ?: return DebugWorkoutSelectionTarget.MissingStagingDirectory
            return DebugWorkoutSelectionTarget.Resolved(
                File(resolvedStagingDirectory, trimmedPath).canonicalOrAbsolute(),
            )
        }
        val canonicalRequestedFile = requestedFile.canonicalOrAbsolute()
        val canonicalRoots = appOwnedRoots.map(File::canonicalOrAbsolute)
        return if (canonicalRoots.any { root -> canonicalRequestedFile.isSameAsOrDescendantOf(root) }) {
            DebugWorkoutSelectionTarget.Resolved(canonicalRequestedFile)
        } else {
            DebugWorkoutSelectionTarget.UnsupportedAbsolutePath(canonicalRequestedFile)
        }
    }
}

internal sealed interface DebugWorkoutSelectionTarget {
    data object InvalidRequest : DebugWorkoutSelectionTarget
    data object MissingStagingDirectory : DebugWorkoutSelectionTarget
    data class Resolved(val file: File) : DebugWorkoutSelectionTarget
    data class UnsupportedAbsolutePath(val file: File) : DebugWorkoutSelectionTarget
}

private fun File.canonicalOrAbsolute(): File = runCatching { canonicalFile }.getOrElse { absoluteFile }

private fun File.isSameAsOrDescendantOf(root: File): Boolean {
    val rootPath = root.path.removeSuffix(File.separator)
    val filePath = path
    return filePath == rootPath || filePath.startsWith("$rootPath${File.separator}")
}

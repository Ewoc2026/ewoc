package io.github.ewoc2026.ewoc.workout

import android.content.Context
import android.content.res.AssetManager
import io.github.ewoc2026.ewoc.BuiltInWorkoutOption
import java.util.Locale

/**
 * Enumerates packaged workout assets that should be visible as bundled examples.
 *
 * The catalog intentionally excludes the legacy fixed-free workout directory
 * so it does not appear beside the editable built-in workout library.
 */
internal object BundledWorkoutAssetCatalog {
    const val workoutsRootAssetPath: String = "workouts"
    private const val fixedFreeAssetPrefix: String = "workouts/Fixed-free/"

    fun listBuiltInWorkouts(context: Context): List<BuiltInWorkoutOption> {
        return collectAssetPaths(
            assetManager = context.assets,
            directory = workoutsRootAssetPath,
        ).asSequence()
            .filter { it.endsWith(".ewo", ignoreCase = true) }
            .filterNot { it.startsWith(fixedFreeAssetPrefix) }
            .sorted()
            .map { assetPath ->
                val fileName = fileNameFromAssetPath(assetPath)
                BuiltInWorkoutOption(
                    assetPath = assetPath,
                    fileName = fileName,
                    displayName = displayNameFromFileName(fileName),
                )
            }
            .toList()
    }

    fun loadText(context: Context, assetPath: String): String {
        return context.assets.open(assetPath).bufferedReader(Charsets.UTF_8).use { reader ->
            reader.readText()
        }
    }

    fun fileNameFromAssetPath(assetPath: String): String = assetPath.substringAfterLast('/')

    private fun collectAssetPaths(
        assetManager: AssetManager,
        directory: String,
    ): List<String> {
        val entries = assetManager.list(directory).orEmpty()
        if (entries.isEmpty()) {
            return listOf(directory)
        }
        return entries.flatMap { entry ->
            collectAssetPaths(assetManager, "$directory/$entry")
        }
    }

    private fun displayNameFromFileName(fileName: String): String {
        return fileName.removeSuffix(".ewo")
            .split('_')
            .filter { it.isNotBlank() }
            .joinToString(" ") { token ->
                when (token.lowercase(Locale.ROOT)) {
                    "ftp" -> "FTP"
                    "hr" -> "HR"
                    "vo2" -> "VO2"
                    else -> token.replaceFirstChar { first ->
                        if (first.isLowerCase()) first.titlecase(Locale.getDefault()) else first.toString()
                    }
                }
            }
    }
}

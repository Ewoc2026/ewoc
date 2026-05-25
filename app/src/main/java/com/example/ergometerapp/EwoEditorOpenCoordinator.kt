package com.example.ergometerapp

import com.example.ergometerapp.workout.WorkoutFile
import com.example.ergometerapp.workout.WorkoutImportResult
import com.example.ergometerapp.workout.WorkoutImportService

/**
 * Keeps the canonical editor on one visible open path while still accepting
 * legacy XML workout files as import-only sources.
 *
 * Non-XML payloads go straight to the canonical editor first because `.ewo`
 * files should stay editable even when execution currently needs more athlete
 * profile context.
 */
internal interface EwoEditorOpenStatePort {
    fun openCanonicalJson(json: String, fileName: String): Boolean
    fun openLegacyWorkout(workoutFile: WorkoutFile, fileName: String)
    fun reportOpenError(message: String)
}

/**
 * Resolves editor open requests between canonical `.ewo` documents and
 * import-only legacy `.zwo` / `.xml` files.
 */
internal class EwoEditorOpenCoordinator(
    private val importWorkout: (String?, String, Int?) -> WorkoutImportResult =
        WorkoutImportService()::importFromText,
) {

    /**
     * Opens [content] directly as canonical JSON unless the source looks like
     * a legacy XML workout, in which case the workout is imported first.
     */
    fun openDocument(
        content: String,
        fileName: String,
        ftpWatts: Int?,
        statePort: EwoEditorOpenStatePort,
    ): Boolean {
        val trimmedContent = content.trim()
        if (!looksLikeLegacyXml(fileName, trimmedContent)) {
            return statePort.openCanonicalJson(content, fileName)
        }

        return when (val result = importWorkout(fileName, content, ftpWatts)) {
            is WorkoutImportResult.Success -> {
                val workoutFile = result.workoutFile
                if (workoutFile == null) {
                    statePort.reportOpenError("This workout format cannot be opened in the editor.")
                    false
                } else {
                    statePort.openLegacyWorkout(workoutFile, fileName)
                    true
                }
            }

            is WorkoutImportResult.Failure -> {
                statePort.reportOpenError(result.error.message)
                false
            }
        }
    }

    private fun looksLikeLegacyXml(fileName: String, trimmedContent: String): Boolean {
        val normalizedFileName = fileName.trim().lowercase()
        return normalizedFileName.endsWith(".zwo") ||
            normalizedFileName.endsWith(".xml") ||
            trimmedContent.startsWith("<")
    }
}

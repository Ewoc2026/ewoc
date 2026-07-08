package io.github.ewoc2026.ewoc

import io.github.ewoc2026.ewoc.workout.ImportedErgoWorkout
import io.github.ewoc2026.ewoc.workout.WorkoutFile
import io.github.ewoc2026.ewoc.workout.WorkoutImportError
import io.github.ewoc2026.ewoc.workout.WorkoutImportErrorCode
import io.github.ewoc2026.ewoc.workout.WorkoutImportFormat
import io.github.ewoc2026.ewoc.workout.WorkoutImportPayload
import io.github.ewoc2026.ewoc.workout.WorkoutImportResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EwoEditorOpenCoordinatorTest {
    @Test
    fun openDocument_routesCanonicalJsonStraightToEditor() {
        val events = mutableListOf<String>()
        val coordinator = EwoEditorOpenCoordinator(
            importWorkout = { _, _, _ ->
                error("Legacy import should not run for canonical editor files")
            },
        )
        val statePort = FakeEwoEditorOpenStatePort(
            openCanonicalJson = { json, fileName ->
                events += "canonical:$fileName:$json"
                true
            },
        )

        val opened = coordinator.openDocument(
            content = """{"format":"ewo","title":"Tempo"}""",
            fileName = "tempo.ewo",
            ftpWatts = 250,
            statePort = statePort,
        )

        assertTrue(opened)
        assertEquals(listOf("""canonical:tempo.ewo:{"format":"ewo","title":"Tempo"}"""), events)
    }

    @Test
    fun openDocument_importsLegacyXmlBeforeOpeningEditor() {
        val workout = WorkoutFile(
            name = "Tempo",
            description = null,
            author = null,
            tags = emptyList(),
            steps = emptyList(),
        )
        val events = mutableListOf<String>()
        val coordinator = EwoEditorOpenCoordinator(
            importWorkout = { sourceName, content, ftpWatts ->
                events += "import:$sourceName:$ftpWatts:${content.trim()}"
                WorkoutImportResult.Success(
                    format = WorkoutImportFormat.ZWO_XML,
                    payload = WorkoutImportPayload.Zwo(workoutFile = workout),
                )
            },
        )
        val statePort = FakeEwoEditorOpenStatePort(
            openLegacyWorkout = { openedWorkout, fileName ->
                events += "legacy:$fileName:${openedWorkout.name}"
            },
        )

        val opened = coordinator.openDocument(
            content = "<workout_file />",
            fileName = "tempo.zwo",
            ftpWatts = 260,
            statePort = statePort,
        )

        assertTrue(opened)
        assertEquals(
            listOf(
                "import:tempo.zwo:260:<workout_file />",
                "legacy:tempo.zwo:Tempo",
            ),
            events,
        )
    }

    @Test
    fun openDocument_reportsLegacyImportFailures() {
        val events = mutableListOf<String>()
        val coordinator = EwoEditorOpenCoordinator(
            importWorkout = { _, _, _ ->
                WorkoutImportResult.Failure(
                    WorkoutImportError(
                        code = WorkoutImportErrorCode.PARSE_FAILED,
                        message = "Workout XML parsing failed.",
                        detectedFormat = WorkoutImportFormat.ZWO_XML,
                    ),
                )
            },
        )
        val statePort = FakeEwoEditorOpenStatePort(
            reportOpenError = { message ->
                events += "error:$message"
            },
        )

        val opened = coordinator.openDocument(
            content = "<broken",
            fileName = "broken.xml",
            ftpWatts = 240,
            statePort = statePort,
        )

        assertFalse(opened)
        assertEquals(listOf("error:Workout XML parsing failed."), events)
    }

    @Test
    fun openDocument_reportsUnsupportedLegacySuccessPayloads() {
        val events = mutableListOf<String>()
        val coordinator = EwoEditorOpenCoordinator(
            importWorkout = { _, _, _ ->
                WorkoutImportResult.Success(
                    format = WorkoutImportFormat.ERGO_WORKOUT_JSON,
                    payload = WorkoutImportPayload.ErgoWorkout(
                        workout = ImportedErgoWorkout(
                            title = "Tempo",
                            description = null,
                            steps = emptyList(),
                            totalDurationSec = 0,
                        ),
                    ),
                )
            },
        )
        val statePort = FakeEwoEditorOpenStatePort(
            reportOpenError = { message ->
                events += "error:$message"
            },
        )

        val opened = coordinator.openDocument(
            content = "<xml />",
            fileName = "legacy.xml",
            ftpWatts = null,
            statePort = statePort,
        )

        assertFalse(opened)
        assertEquals(listOf("error:This workout format cannot be opened in the editor."), events)
    }

    private class FakeEwoEditorOpenStatePort(
        private val openCanonicalJson: (String, String) -> Boolean = { _, _ -> false },
        private val openLegacyWorkout: (WorkoutFile, String) -> Unit = { _, _ -> },
        private val reportOpenError: (String) -> Unit = {},
    ) : EwoEditorOpenStatePort {
        override fun openCanonicalJson(json: String, fileName: String): Boolean {
            return openCanonicalJson.invoke(json, fileName)
        }

        override fun openLegacyWorkout(workoutFile: WorkoutFile, fileName: String) {
            openLegacyWorkout.invoke(workoutFile, fileName)
        }

        override fun reportOpenError(message: String) {
            reportOpenError.invoke(message)
        }
    }
}

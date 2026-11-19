package io.github.ewoc2026.ewoc.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exercises format detection through the production importer, including valid JSON whose
 * format discriminator has the wrong type. Rejected input must remain a failure result.
 */
class WorkoutImportFailureBoundaryTest {
    private val service = WorkoutImportService()

    @Test
    fun arrayFormatReturnsFailureForEwoDocument() {
        val result = service.importFromText("array-format.ewo", """{"format":[]}""")

        assertTrue("Wrong-type format should return an import failure", result is WorkoutImportResult.Failure)
    }

    @Test
    fun objectFormatReturnsFailureForJsonDocument() {
        val result = service.importFromText("object-format.json", """{"format":{}}""")

        assertTrue("Wrong-type format should return an import failure", result is WorkoutImportResult.Failure)
    }

    @Test
    fun validCanonicalFormatImportsPowerStep() {
        val content = """
            {
              "format": "ewo",
              "version": "1.0",
              "title": "Import boundary control",
              "segments": [
                {
                  "id": "steady",
                  "type": "steady",
                  "duration_sec": 60,
                  "target": {"metric": "power", "value": 100}
                }
              ]
            }
        """.trimIndent()

        val result = service.importFromText("valid-format.json", content)
        val success = result as? WorkoutImportResult.Success
            ?: throw AssertionError("Expected a valid import, got $result")

        assertEquals(WorkoutImportFormat.CANONICAL_EWO_JSON, success.format)
        val step = requireNotNull(success.ergoWorkout).steps.single() as ImportedErgoWorkoutStep.PowerSteady
        assertEquals(60, step.durationSec)
        assertEquals(100, step.watts)
    }
}

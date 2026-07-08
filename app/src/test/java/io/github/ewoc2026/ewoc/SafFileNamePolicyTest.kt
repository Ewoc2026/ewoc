package io.github.ewoc2026.ewoc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafFileNamePolicyTest {
    @Test
    fun workoutImportPickerMimeTypes_returnsExpectedFilters() {
        assertEquals(
            listOf("application/xml", "text/xml", "application/json", "application/octet-stream"),
            SafFileNamePolicy.workoutImportPickerMimeTypes().toList(),
        )
    }

    @Test
    fun isSupportedWorkoutImportFileName_acceptsZwoXmlAndEwoCaseInsensitive() {
        assertTrue(SafFileNamePolicy.isSupportedWorkoutImportFileName("workout.zwo"))
        assertTrue(SafFileNamePolicy.isSupportedWorkoutImportFileName("WORKOUT.ZWO"))
        assertTrue(SafFileNamePolicy.isSupportedWorkoutImportFileName("workout.xml"))
        assertTrue(SafFileNamePolicy.isSupportedWorkoutImportFileName("WORKOUT.XML"))
        assertTrue(SafFileNamePolicy.isSupportedWorkoutImportFileName("power-builder.ewo"))
        assertTrue(SafFileNamePolicy.isSupportedWorkoutImportFileName("POWER-BUILDER.EWO"))
        assertFalse(SafFileNamePolicy.isSupportedWorkoutImportFileName("workout.fit"))
        assertFalse(SafFileNamePolicy.isSupportedWorkoutImportFileName(null))
    }

    @Test
    fun ensureExtension_appendsWhenMissing() {
        assertEquals("session.fit", SafFileNamePolicy.ensureExtension("session", ".fit"))
        assertEquals("session.fit", SafFileNamePolicy.ensureExtension("session", "fit"))
    }

    @Test
    fun ensureExtension_keepsExistingSuffix() {
        assertEquals("session.fit", SafFileNamePolicy.ensureExtension("session.fit", ".fit"))
        assertEquals("session.FIT", SafFileNamePolicy.ensureExtension("session.FIT", ".fit"))
    }

    @Test
    fun resolveConflictSafeFileName_returnsPreferredWhenNotTaken() {
        val resolved = SafFileNamePolicy.resolveConflictSafeFileName(
            preferredFileName = "session.fit",
            timestampSuffix = "2026-02-23_10-00-00",
            existingFileNames = setOf("other.fit"),
        )
        assertEquals("session.fit", resolved)
    }

    @Test
    fun resolveConflictSafeFileName_appendsTimestampBeforeExtension() {
        val resolved = SafFileNamePolicy.resolveConflictSafeFileName(
            preferredFileName = "session.fit",
            timestampSuffix = "2026-02-23_10-00-00",
            existingFileNames = setOf("session.fit"),
        )
        assertEquals("session_2026-02-23_10-00-00.fit", resolved)
    }

    @Test
    fun resolveConflictSafeFileName_usesCounterWhenTimestampVariantExists() {
        val resolved = SafFileNamePolicy.resolveConflictSafeFileName(
            preferredFileName = "session.fit",
            timestampSuffix = "2026-02-23_10-00-00",
            existingFileNames = setOf(
                "session.fit",
                "session_2026-02-23_10-00-00.fit",
            ),
        )
        assertEquals("session_2026-02-23_10-00-00_2.fit", resolved)
    }
}

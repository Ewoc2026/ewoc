package io.github.ewoc2026.ewoc.compat.quirks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrainerFingerprintTest {

    @Test
    fun normalizeAdvertisementName_collapsesWhitespace_and_preserves_digits() {
        val normalized = TrainerFingerprint.normalizeAdvertisementName("  Trainer   2.0   X  ")
        assertEquals("trainer 2.0 x", normalized)
    }

    @Test
    fun normalizeAdvertisementName_returnsNull_forBlankOrNullValues() {
        assertNull(TrainerFingerprint.normalizeAdvertisementName(null))
        assertNull(TrainerFingerprint.normalizeAdvertisementName("   "))
    }
}


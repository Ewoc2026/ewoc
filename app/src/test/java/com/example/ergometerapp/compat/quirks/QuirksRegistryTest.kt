package com.example.ergometerapp.compat.quirks

import com.example.ergometerapp.compat.CompatibilityV1Constants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuirksRegistryTest {

    @Test
    fun resolve_returnsDefaultQuirks_whenNoRuleMatches() {
        val fingerprint = TrainerFingerprint(
            advNameNormalized = "trainer x",
            manufacturer = null,
            model = null,
            ftmsServicePresent = true,
            has2ad2 = true,
            has2ad9 = true,
            androidManufacturer = "samsung",
            androidModel = "sm-a226b",
        )

        val quirks = QuirksRegistry.resolve(fingerprint)

        assertEquals("default", quirks.id)
        assertEquals(MatchConfidence.HIGH, quirks.matchConfidence)
        assertEquals(CompatibilityV1Constants.CONNECT_RETRY_COUNT, quirks.maxReconnectRetries)
        assertEquals(CompatibilityV1Constants.STOP_FALLBACK_TARGET_WATTS, quirks.fallbackPowerWatts)
        assertTrue(quirks.enableResetOptional)
    }
}


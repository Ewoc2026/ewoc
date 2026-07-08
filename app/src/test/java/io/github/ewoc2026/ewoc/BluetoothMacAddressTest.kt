package io.github.ewoc2026.ewoc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BluetoothMacAddressTest {

    @Test
    fun normalizeOrNullReturnsCanonicalAddressWhenInputIsValid() {
        val normalized = BluetoothMacAddress.normalizeOrNull("aa:bb:cc:dd:ee:ff")

        assertEquals("AA:BB:CC:DD:EE:FF", normalized)
    }

    @Test
    fun normalizeOrNullReturnsNullWhenFormatIsInvalid() {
        val normalized = BluetoothMacAddress.normalizeOrNull("AABBCCDDEEFF")

        assertNull(normalized)
    }

    @Test
    fun sanitizeUserInputDropsUnsupportedCharacters() {
        val sanitized = BluetoothMacAddress.sanitizeUserInput("aa:bb-cc?dd.ee ff")

        assertEquals("AA:BBCCDDEEFF", sanitized)
    }

    @Test
    fun redactForLogsMasksLeadingBytes() {
        val redacted = BluetoothMacAddress.redactForLogs("aa:bb:cc:dd:ee:ff")

        assertEquals("XX:XX:XX:XX:EE:FF", redacted)
    }

    @Test
    fun redactForLogsReturnsPlaceholderWhenInputIsInvalid() {
        val redacted = BluetoothMacAddress.redactForLogs("invalid")

        assertEquals("<invalid-or-missing>", redacted)
    }

    @Test
    fun redactForLogsReturnsPlaceholderWhenInputIsMissing() {
        val redacted = BluetoothMacAddress.redactForLogs(null)

        assertEquals("<invalid-or-missing>", redacted)
    }
}

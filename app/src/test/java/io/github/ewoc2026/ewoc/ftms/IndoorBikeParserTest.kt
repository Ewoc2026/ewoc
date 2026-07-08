package io.github.ewoc2026.ewoc.ftms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IndoorBikeParserTest {
    @Test
    fun parsesTotalDistanceAsMetersFromDecimeterPayload() {
        val payload = byteArrayOf(
            0x10, 0x00, // flags: bit 4 (Total Distance present)
            0x00, 0x00, // instantaneous speed (required field)
            0x10, 0x27, 0x00, // total distance raw = 10000 decimeters => 1000 meters
        )

        val parsed = parseIndoorBikeData(payload)

        assertTrue(parsed.valid)
        assertEquals(1000, parsed.totalDistanceMeters)
    }

    @Test
    fun leavesTotalDistanceNullWhenFieldIsNotPresent() {
        val payload = byteArrayOf(
            0x00, 0x00, // flags: no optional fields
            0x00, 0x00, // instantaneous speed (required field)
        )

        val parsed = parseIndoorBikeData(payload)

        assertTrue(parsed.valid)
        assertNull(parsed.totalDistanceMeters)
    }

    @Test
    fun reportsTruncatedPayloadFailureWithStructuredContext() {
        val payload = byteArrayOf(
            0x04, 0x00, // flags: bit 2 (instantaneous cadence present)
            0x00, 0x00, // instantaneous speed (required field)
            0x01, // truncated cadence field: one byte instead of two
        )
        var failure: IndoorBikeParseFailure? = null

        val parsed = parseIndoorBikeData(payload) { parseFailure ->
            failure = parseFailure
        }

        assertFalse(parsed.valid)
        val recordedFailure = requireNotNull(failure)
        assertEquals(IndoorBikeParseFailureReason.TRUNCATED_PAYLOAD, recordedFailure.reason)
        assertEquals("IndexOutOfBoundsException", recordedFailure.exceptionType)
        assertEquals(payload.size, recordedFailure.payloadLength)
        assertEquals(0x0004, recordedFailure.flags)
        assertEquals("04 00 00 00 01", recordedFailure.payloadPreviewHex)
    }
}

package com.example.ergometerapp.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HrMeasurementParserTest {
    @Test
    fun parseBpmReturnsNullForEmptyPayload() {
        assertNull(HrMeasurementParser.parseBpm(byteArrayOf()))
    }

    @Test
    fun parseBpmParses8BitHeartRate() {
        val bpm = HrMeasurementParser.parseBpm(
            byteArrayOf(
                0x00,
                72,
            ),
        )

        assertEquals(72, bpm)
    }

    @Test
    fun parseBpmParses16BitHeartRate() {
        val bpm = HrMeasurementParser.parseBpm(
            byteArrayOf(
                0x01,
                0x2C,
                0x01,
            ),
        )

        assertEquals(300, bpm)
    }

    @Test
    fun parseBpmReturnsNullForTruncated16BitHeartRate() {
        val bpm = HrMeasurementParser.parseBpm(
            byteArrayOf(
                0x01,
                0x2C,
            ),
        )

        assertNull(bpm)
    }

    @Test
    fun parseBpmReturnsNullWhenEnergyExpendedIsFlaggedButMissing() {
        val bpm = HrMeasurementParser.parseBpm(
            byteArrayOf(
                0x08,
                70,
            ),
        )

        assertNull(bpm)
    }

    @Test
    fun parseBpmReturnsNullWhenRrIntervalFlagHasOddTrailingBytes() {
        val bpm = HrMeasurementParser.parseBpm(
            byteArrayOf(
                0x10,
                68,
                0x34,
            ),
        )

        assertNull(bpm)
    }

    @Test
    fun parseBpmParsesPayloadWithEnergyAndRrIntervals() {
        val bpm = HrMeasurementParser.parseBpm(
            byteArrayOf(
                0x19,
                0x32,
                0x00,
                0x05,
                0x00,
                0x40,
                0x03,
                0x44,
                0x03,
            ),
        )

        assertEquals(50, bpm)
    }
}

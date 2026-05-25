package com.example.ergometerapp.ble

/**
 * Parses Heart Rate Measurement (0x2A37) payloads into BPM.
 *
 * The format is defined by the Bluetooth SIG and starts with a flag byte
 * followed by mandatory/optional fields. This parser validates that all
 * flag-indicated fields are present so truncated packets are dropped.
 */
internal object HrMeasurementParser {
    private const val FLAG_HEART_RATE_VALUE_16_BIT = 0x01
    private const val FLAG_ENERGY_EXPENDED_PRESENT = 0x08
    private const val FLAG_RR_INTERVAL_PRESENT = 0x10

    fun parseBpm(bytes: ByteArray): Int? {
        if (bytes.isEmpty()) return null

        val flags = bytes[0].toInt() and 0xFF
        var index = 1

        val bpm = if ((flags and FLAG_HEART_RATE_VALUE_16_BIT) != 0) {
            if (bytes.size < index + 2) return null
            val value = (bytes[index].toInt() and 0xFF) or ((bytes[index + 1].toInt() and 0xFF) shl 8)
            index += 2
            value
        } else {
            if (bytes.size < index + 1) return null
            val value = bytes[index].toInt() and 0xFF
            index += 1
            value
        }

        if ((flags and FLAG_ENERGY_EXPENDED_PRESENT) != 0) {
            if (bytes.size < index + 2) return null
            index += 2
        }

        if ((flags and FLAG_RR_INTERVAL_PRESENT) != 0) {
            val remaining = bytes.size - index
            if (remaining <= 0 || remaining % 2 != 0) return null
        }

        return bpm
    }
}

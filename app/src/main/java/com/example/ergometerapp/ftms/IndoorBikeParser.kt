package com.example.ergometerapp.ftms

/**
 * Spec-compliant parser for FTMS Indoor Bike Data (UUID 0x2AD2).
 *
 * Parsing is driven strictly by the flags bitmask, exactly as defined
 * in the Bluetooth FTMS specification.
 *
 * This implementation is verified against nRF Connect output.
 */
fun parseIndoorBikeData(
    bytes: ByteArray,
    onParseFailure: (IndoorBikeParseFailure) -> Unit = {},
): IndoorBikeData {
    var offset = 0

    fun require(n: Int) {
        if (offset + n > bytes.size) throw IndexOutOfBoundsException()
    }

    fun u8(): Int {
        require(1)
        return bytes[offset++].toInt() and 0xFF
    }

    fun u16(): Int {
        require(2)
        val v =
            (bytes[offset].toInt() and 0xFF) or
                    ((bytes[offset + 1].toInt() and 0xFF) shl 8)
        offset += 2
        return v
    }

    fun s16(): Int {
        val v = u16()
        return v.toShort().toInt()
    }

    fun u24(): Int {
        require(3)
        val v =
            (bytes[offset].toInt() and 0xFF) or
                    ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
                    ((bytes[offset + 2].toInt() and 0xFF) shl 16)
        offset += 3
        return v
    }

    return try {
        /* -------------------------------------------------------------
         * FLAGS
         *
         * Tunturi-specific mapping:
         * bit 0 = More Data (NO FIELD)
         * bit 1 = Average Speed
         * bit 2 = Instantaneous Cadence
         * bit 3 = Average Cadence
         * bit 4 = Total Distance (u24)
         * bit 5 = Resistance Level
         * bit 6 = Instantaneous Power
         * bit 7 = Average Power
         * bit 8 = Total Energy
         * bit 9 = Energy per Hour
         * bit 10 = Energy per Minute
         * bit 11 = Heart Rate
         * bit 12 = MET
         * bit 13 = Elapsed Time
         * bit 14 = Remaining Time
         * ------------------------------------------------------------- */
        val flags = u16()
        fun flag(bit: Int) = (flags and (1 shl bit)) != 0

        /* -------------------------------------------------------------
         * FIXED FIELD
         * ------------------------------------------------------------- */
        // Instantaneous Speed (always present)
        val instantaneousSpeedKmh = u16() / 100.0

        /* -------------------------------------------------------------
         * OPTIONAL FIELDS (TUNTURI ORDER)
         * ------------------------------------------------------------- */
        val averageSpeedKmh =
            if (flag(1)) u16() / 100.0 else null

        val instantaneousCadenceRpm =
            if (flag(2)) u16() / 2.0 else null

        val averageCadenceRpm =
            if (flag(3)) u16() / 2.0 else null

        val totalDistanceMeters =
            if (flag(4)) {
                // Tunturi reports Total Distance in decimeters for this characteristic.
                // Convert to meters so downstream session summaries stay unit-consistent.
                u24() / 10
            } else {
                null
            }

        val resistanceLevel =
            if (flag(5)) u16() else null

        val instantaneousPowerW =
            if (flag(6)) s16() else null

        val averagePowerW =
            if (flag(7)) s16() else null

        val totalEnergyKcal =
            if (flag(8)) u16() else null

        val energyPerHourKcal =
            if (flag(9)) u16() else null

        val energyPerMinuteKcal =
            if (flag(10)) u8() else null

        val heartRateBpm =
            if (flag(11)) u8() else null

        val metabolicEquivalent =
            if (flag(12)) u8() / 10.0 else null

        val elapsedTimeSeconds =
            if (flag(13)) u16() else null

        val remainingTimeSeconds =
            if (flag(14)) u16() else null

        IndoorBikeData(
            valid = true,
            instantaneousSpeedKmh = instantaneousSpeedKmh,
            averageSpeedKmh = averageSpeedKmh,
            instantaneousCadenceRpm = instantaneousCadenceRpm,
            averageCadenceRpm = averageCadenceRpm,
            totalDistanceMeters = totalDistanceMeters,
            resistanceLevel = resistanceLevel,
            instantaneousPowerW = instantaneousPowerW,
            averagePowerW = averagePowerW,
            totalEnergyKcal = totalEnergyKcal,
            energyPerHourKcal = energyPerHourKcal,
            energyPerMinuteKcal = energyPerMinuteKcal,
            heartRateBpm = heartRateBpm,
            metabolicEquivalent = metabolicEquivalent,
            elapsedTimeSeconds = elapsedTimeSeconds,
            remainingTimeSeconds = remainingTimeSeconds
        )
    } catch (exception: Exception) {
        onParseFailure(
            IndoorBikeParseFailure(
                reason = classifyParseFailureReason(exception),
                exceptionType = exception.javaClass.simpleName.ifBlank { "Exception" },
                payloadLength = bytes.size,
                flags = extractFlagsOrNull(bytes),
                payloadPreviewHex = buildPayloadPreviewHex(bytes),
            ),
        )
        IndoorBikeData(
            valid = false,
            instantaneousSpeedKmh = null,
            averageSpeedKmh = null,
            instantaneousCadenceRpm = null,
            averageCadenceRpm = null,
            totalDistanceMeters = null,
            resistanceLevel = null,
            instantaneousPowerW = null,
            averagePowerW = null,
            totalEnergyKcal = null,
            energyPerHourKcal = null,
            energyPerMinuteKcal = null,
            heartRateBpm = null,
            metabolicEquivalent = null,
            elapsedTimeSeconds = null,
            remainingTimeSeconds = null
        )
    }
}

private fun classifyParseFailureReason(exception: Exception): IndoorBikeParseFailureReason {
    return when (exception) {
        is IndexOutOfBoundsException -> IndoorBikeParseFailureReason.TRUNCATED_PAYLOAD
        else -> IndoorBikeParseFailureReason.UNEXPECTED_EXCEPTION
    }
}

private fun extractFlagsOrNull(bytes: ByteArray): Int? {
    if (bytes.size < 2) return null
    return (bytes[0].toInt() and 0xFF) or ((bytes[1].toInt() and 0xFF) shl 8)
}

private fun buildPayloadPreviewHex(bytes: ByteArray, maxBytes: Int = 8): String {
    if (bytes.isEmpty()) return ""
    val preview = bytes.take(maxBytes).joinToString(separator = " ") { byte ->
        (byte.toInt() and 0xFF).toString(16).padStart(2, '0')
    }
    return if (bytes.size > maxBytes) "$preview ..." else preview
}

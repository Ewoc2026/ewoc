package com.example.ergometerapp.session.export

import com.example.ergometerapp.session.SessionSample
import com.example.ergometerapp.session.SessionSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FitExportServiceTest {
    @Test
    fun buildFitBytes_containsRequiredMessagesAndValidCrc() {
        val summary = SessionSummary(
            startTimestampMillis = 1_705_000_000_000L,
            stopTimestampMillis = 1_705_000_180_000L,
            durationSeconds = 180,
            actualTss = 12.3,
            avgPower = 190,
            maxPower = 330,
            avgCadence = 86,
            maxCadence = 104,
            avgHeartRate = 142,
            maxHeartRate = 168,
            distanceMeters = 2_150,
            totalEnergyKcal = 58,
        )
        val snapshot = SessionExportSnapshot(
            summary = summary,
            timeline = listOf(
                SessionSample(
                    timestampMillis = 1_705_000_030_000L,
                    powerWatts = 180,
                    cadenceRpm = 82,
                    heartRateBpm = 136,
                    distanceMeters = 500,
                    totalEnergyKcal = 15,
                ),
                SessionSample(
                    timestampMillis = 1_705_000_031_000L,
                    powerWatts = 185,
                    cadenceRpm = 83,
                    heartRateBpm = 137,
                    distanceMeters = 510,
                    totalEnergyKcal = 15,
                ),
                SessionSample(
                    timestampMillis = 1_705_000_032_000L,
                    powerWatts = 190,
                    cadenceRpm = 84,
                    heartRateBpm = 138,
                    distanceMeters = 520,
                    totalEnergyKcal = 16,
                ),
            ),
        )

        val bytes = FitExportService.buildFitBytes(snapshot)

        assertEquals(14, bytes[0].toInt() and 0xFF)
        assertEquals('.'.code, bytes[8].toInt())
        assertEquals('F'.code, bytes[9].toInt())
        assertEquals('I'.code, bytes[10].toInt())
        assertEquals('T'.code, bytes[11].toInt())

        val dataSize = readUInt32LittleEndian(bytes, 4)
        assertEquals(14 + dataSize + 2, bytes.size)

        val headerCrc = readUInt16LittleEndian(bytes, 12)
        val computedHeaderCrc = crc16(bytes, until = 12)
        assertEquals(computedHeaderCrc, headerCrc)

        val fileCrc = readUInt16LittleEndian(bytes, bytes.size - 2)
        val computedFileCrc = crc16(bytes, until = bytes.size - 2)
        assertEquals(computedFileCrc, fileCrc)

        val parsedDefinitionMesgNums = parseDefinitionMesgNums(bytes)
        val expected = setOf(0, 20, 19, 18, 34)
        assertTrue(parsedDefinitionMesgNums.containsAll(expected))
        val dataCounts = parseDataMesgCounts(bytes)
        assertTrue((dataCounts[20] ?: 0) >= 3)
    }

    @Test
    fun buildFitBytes_acceptsSparseSummary() {
        val summary = SessionSummary(
            startTimestampMillis = 1_705_000_000_000L,
            stopTimestampMillis = 1_705_000_030_000L,
            durationSeconds = 30,
            actualTss = null,
            avgPower = null,
            maxPower = null,
            avgCadence = null,
            maxCadence = null,
            avgHeartRate = null,
            maxHeartRate = null,
            distanceMeters = null,
            totalEnergyKcal = null,
        )
        val snapshot = SessionExportSnapshot(
            summary = summary,
            timeline = emptyList(),
        )

        val bytes = FitExportService.buildFitBytes(snapshot)
        assertTrue(bytes.isNotEmpty())
        assertEquals('.'.code, bytes[8].toInt())
        val dataCounts = parseDataMesgCounts(bytes)
        assertTrue((dataCounts[20] ?: 0) >= 1)
    }

    @Test
    fun buildFitBytes_telemetryOnlySessionWithoutWorkoutProducesValidFit() {
        val summary = SessionSummary(
            startTimestampMillis = 1_705_000_000_000L,
            stopTimestampMillis = 1_705_000_600_000L,
            durationSeconds = 600,
            actualTss = null,
            avgPower = 175,
            maxPower = 210,
            avgCadence = 80,
            maxCadence = 95,
            avgHeartRate = 135,
            maxHeartRate = 155,
            distanceMeters = 5_000,
            totalEnergyKcal = 120,
        )
        val snapshot = SessionExportSnapshot(
            summary = summary,
            timeline = listOf(
                SessionSample(
                    timestampMillis = 1_705_000_010_000L,
                    powerWatts = 170,
                    cadenceRpm = 78,
                    heartRateBpm = 130,
                    distanceMeters = 200,
                    totalEnergyKcal = 5,
                ),
                SessionSample(
                    timestampMillis = 1_705_000_020_000L,
                    powerWatts = 180,
                    cadenceRpm = 82,
                    heartRateBpm = 138,
                    distanceMeters = 420,
                    totalEnergyKcal = 11,
                ),
            ),
        )

        val bytes = FitExportService.buildFitBytes(snapshot)

        // Valid FIT header
        assertEquals(14, bytes[0].toInt() and 0xFF)
        assertEquals('.'.code, bytes[8].toInt())
        assertEquals('F'.code, bytes[9].toInt())
        assertEquals('I'.code, bytes[10].toInt())
        assertEquals('T'.code, bytes[11].toInt())

        // Valid file size and CRC
        val dataSize = readUInt32LittleEndian(bytes, 4)
        assertEquals(14 + dataSize + 2, bytes.size)
        val fileCrc = readUInt16LittleEndian(bytes, bytes.size - 2)
        val computedFileCrc = crc16(bytes, until = bytes.size - 2)
        assertEquals(computedFileCrc, fileCrc)

        // Required definition messages present (no workout-specific messages needed)
        val definitionMesgNums = parseDefinitionMesgNums(bytes)
        assertTrue(definitionMesgNums.contains(0))  // file_id
        assertTrue(definitionMesgNums.contains(18)) // session
        assertTrue(definitionMesgNums.contains(19)) // lap
        assertTrue(definitionMesgNums.contains(20)) // record
        assertTrue(definitionMesgNums.contains(34)) // activity

        // Record messages present for the telemetry samples
        val dataCounts = parseDataMesgCounts(bytes)
        assertTrue((dataCounts[20] ?: 0) >= 2)
    }

    @Test
    fun buildFitBytes_usesElapsedAndActiveTimersSeparately() {
        val summary = SessionSummary(
            startTimestampMillis = 1_705_000_000_000L,
            stopTimestampMillis = 1_705_000_240_000L,
            durationSeconds = 180,
            elapsedDurationSeconds = 240,
            actualTss = 12.3,
            avgPower = 190,
            maxPower = 330,
            avgCadence = 86,
            maxCadence = 104,
            avgHeartRate = 142,
            maxHeartRate = 168,
            distanceMeters = 2_150,
            totalEnergyKcal = 58,
        )
        val snapshot = SessionExportSnapshot(
            summary = summary,
            timeline = emptyList(),
        )

        val bytes = FitExportService.buildFitBytes(snapshot)

        val lapFields = parseFirstDataFields(bytes, globalMessageNumber = 19)
        val sessionFields = parseFirstDataFields(bytes, globalMessageNumber = 18)
        val activityFields = parseFirstDataFields(bytes, globalMessageNumber = 34)

        assertEquals(240_000L, lapFields[7])
        assertEquals(180_000L, lapFields[8])
        assertEquals(240_000L, sessionFields[7])
        assertEquals(180_000L, sessionFields[8])
        assertEquals(180_000L, activityFields[0])
    }

    private fun parseDefinitionMesgNums(buffer: ByteArray): Set<Int> {
        val dataSize = readUInt32LittleEndian(buffer, 4)
        val dataStart = 14
        val dataEnd = dataStart + dataSize
        val mesgByLocal = HashMap<Int, Int>()
        val fieldBytesByLocal = HashMap<Int, Int>()
        val definitionMesgNums = LinkedHashSet<Int>()

        var offset = dataStart
        while (offset < dataEnd) {
            val header = buffer[offset++].toInt() and 0xFF
            require((header and 0x80) == 0) { "Compressed timestamp headers are not expected" }
            val local = header and 0x0F
            val isDefinition = (header and 0x40) != 0
            if (isDefinition) {
                offset += 1 // reserved
                val architecture = buffer[offset++].toInt() and 0xFF
                val mesgNum = if (architecture == 1) {
                    ((buffer[offset].toInt() and 0xFF) shl 8) or
                        (buffer[offset + 1].toInt() and 0xFF)
                } else {
                    ((buffer[offset + 1].toInt() and 0xFF) shl 8) or
                        (buffer[offset].toInt() and 0xFF)
                }
                offset += 2
                val fieldCount = buffer[offset++].toInt() and 0xFF
                var fieldByteSize = 0
                repeat(fieldCount) {
                    offset += 1 // field num
                    fieldByteSize += buffer[offset++].toInt() and 0xFF
                    offset += 1 // base type
                }
                if ((header and 0x20) != 0) {
                    val developerFieldCount = buffer[offset++].toInt() and 0xFF
                    offset += developerFieldCount * 3
                }
                mesgByLocal[local] = mesgNum
                fieldBytesByLocal[local] = fieldByteSize
                definitionMesgNums.add(mesgNum)
            } else {
                val payloadSize = fieldBytesByLocal[local]
                    ?: error("Missing local message definition for $local")
                offset += payloadSize
            }
        }
        require(offset == dataEnd) { "Data section parse ended at unexpected offset" }
        return definitionMesgNums
    }

    private fun parseDataMesgCounts(buffer: ByteArray): Map<Int, Int> {
        val dataSize = readUInt32LittleEndian(buffer, 4)
        val dataStart = 14
        val dataEnd = dataStart + dataSize
        val mesgByLocal = HashMap<Int, Int>()
        val fieldBytesByLocal = HashMap<Int, Int>()
        val countsByMesg = HashMap<Int, Int>()

        var offset = dataStart
        while (offset < dataEnd) {
            val header = buffer[offset++].toInt() and 0xFF
            require((header and 0x80) == 0) { "Compressed timestamp headers are not expected" }
            val local = header and 0x0F
            val isDefinition = (header and 0x40) != 0
            if (isDefinition) {
                offset += 1
                val architecture = buffer[offset++].toInt() and 0xFF
                val mesgNum = if (architecture == 1) {
                    ((buffer[offset].toInt() and 0xFF) shl 8) or
                        (buffer[offset + 1].toInt() and 0xFF)
                } else {
                    ((buffer[offset + 1].toInt() and 0xFF) shl 8) or
                        (buffer[offset].toInt() and 0xFF)
                }
                offset += 2
                val fieldCount = buffer[offset++].toInt() and 0xFF
                var fieldByteSize = 0
                repeat(fieldCount) {
                    offset += 1
                    fieldByteSize += buffer[offset++].toInt() and 0xFF
                    offset += 1
                }
                if ((header and 0x20) != 0) {
                    val developerFieldCount = buffer[offset++].toInt() and 0xFF
                    offset += developerFieldCount * 3
                }
                mesgByLocal[local] = mesgNum
                fieldBytesByLocal[local] = fieldByteSize
            } else {
                val payloadSize = fieldBytesByLocal[local]
                    ?: error("Missing local message definition for $local")
                val mesgNum = mesgByLocal[local]
                    ?: error("Missing global message mapping for local $local")
                countsByMesg[mesgNum] = (countsByMesg[mesgNum] ?: 0) + 1
                offset += payloadSize
            }
        }

        require(offset == dataEnd) { "Data section parse ended at unexpected offset" }
        return countsByMesg
    }

    private fun parseFirstDataFields(
        buffer: ByteArray,
        globalMessageNumber: Int,
    ): Map<Int, Long> {
        val dataSize = readUInt32LittleEndian(buffer, 4)
        val dataStart = 14
        val dataEnd = dataStart + dataSize
        val definitionsByLocal = HashMap<Int, Definition>()

        var offset = dataStart
        while (offset < dataEnd) {
            val header = buffer[offset++].toInt() and 0xFF
            require((header and 0x80) == 0) { "Compressed timestamp headers are not expected" }
            val local = header and 0x0F
            val isDefinition = (header and 0x40) != 0
            if (isDefinition) {
                offset += 1
                val architecture = buffer[offset++].toInt() and 0xFF
                val mesgNum = if (architecture == 1) {
                    ((buffer[offset].toInt() and 0xFF) shl 8) or
                        (buffer[offset + 1].toInt() and 0xFF)
                } else {
                    ((buffer[offset + 1].toInt() and 0xFF) shl 8) or
                        (buffer[offset].toInt() and 0xFF)
                }
                offset += 2
                val fieldCount = buffer[offset++].toInt() and 0xFF
                val fields = buildList {
                    repeat(fieldCount) {
                        val fieldNumber = buffer[offset++].toInt() and 0xFF
                        val fieldSize = buffer[offset++].toInt() and 0xFF
                        offset += 1
                        add(DefinitionField(number = fieldNumber, size = fieldSize))
                    }
                }
                if ((header and 0x20) != 0) {
                    val developerFieldCount = buffer[offset++].toInt() and 0xFF
                    offset += developerFieldCount * 3
                }
                definitionsByLocal[local] = Definition(
                    architecture = architecture,
                    globalMessageNumber = mesgNum,
                    fields = fields,
                )
                continue
            }

            val definition = definitionsByLocal[local]
                ?: error("Missing local message definition for $local")
            val payloadStart = offset
            val payloadSize = definition.fields.sumOf { it.size }
            if (definition.globalMessageNumber == globalMessageNumber) {
                val values = LinkedHashMap<Int, Long>()
                var fieldOffset = payloadStart
                definition.fields.forEach { field ->
                    values[field.number] = readUnsigned(
                        buffer = buffer,
                        offset = fieldOffset,
                        size = field.size,
                        architecture = definition.architecture,
                    )
                    fieldOffset += field.size
                }
                return values
            }
            offset += payloadSize
        }

        error("No data message found for global message $globalMessageNumber")
    }

    private fun readUnsigned(
        buffer: ByteArray,
        offset: Int,
        size: Int,
        architecture: Int,
    ): Long {
        var result = 0L
        if (architecture == 1) {
            repeat(size) { index ->
                result = (result shl 8) or (buffer[offset + index].toLong() and 0xFFL)
            }
            return result
        }
        repeat(size) { index ->
            result = result or ((buffer[offset + index].toLong() and 0xFFL) shl (index * 8))
        }
        return result
    }

    private fun readUInt16LittleEndian(buffer: ByteArray, offset: Int): Int {
        val lo = buffer[offset].toInt() and 0xFF
        val hi = buffer[offset + 1].toInt() and 0xFF
        return (hi shl 8) or lo
    }

    private fun readUInt32LittleEndian(buffer: ByteArray, offset: Int): Int {
        val b0 = buffer[offset].toInt() and 0xFF
        val b1 = buffer[offset + 1].toInt() and 0xFF
        val b2 = buffer[offset + 2].toInt() and 0xFF
        val b3 = buffer[offset + 3].toInt() and 0xFF
        return (b3 shl 24) or (b2 shl 16) or (b1 shl 8) or b0
    }

    private fun crc16(buffer: ByteArray, until: Int): Int {
        val table = intArrayOf(
            0x0000, 0xCC01, 0xD801, 0x1400,
            0xF001, 0x3C00, 0x2800, 0xE401,
            0xA001, 0x6C00, 0x7800, 0xB401,
            0x5000, 0x9C01, 0x8801, 0x4400,
        )
        var crc = 0
        for (index in 0 until until) {
            val byteValue = buffer[index].toInt() and 0xFF
            var tmp = table[crc and 0x0F]
            crc = (crc shr 4) and 0x0FFF
            crc = crc xor tmp xor table[byteValue and 0x0F]

            tmp = table[crc and 0x0F]
            crc = (crc shr 4) and 0x0FFF
            crc = crc xor tmp xor table[(byteValue shr 4) and 0x0F]
        }
        return crc and 0xFFFF
    }

    private data class Definition(
        val architecture: Int,
        val globalMessageNumber: Int,
        val fields: List<DefinitionField>,
    )

    private data class DefinitionField(
        val number: Int,
        val size: Int,
    )
}

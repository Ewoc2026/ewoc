package com.example.ergometerapp.session.export

import android.content.Context
import android.net.Uri
import com.example.ergometerapp.session.SessionSummary
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToLong

/**
 * Exports completed sessions to `.fit` activity files using an in-repo encoder.
 *
 * The encoder intentionally targets a small interoperable subset:
 * `file_id`, `record`, `lap`, `session`, and `activity`.
 */
object FitExportService {
    private const val fitEpochOffsetMillis = 631_065_600_000L
    private const val fitProtocolVersionV1 = 0x10
    private const val fitProfileVersion = 21_194
    private const val fileTypeActivity = 4
    private const val manufacturerDevelopment = 255
    private const val activityTypeManual = 0
    private const val eventActivity = 26
    private const val eventTypeStop = 1
    private const val sportCycling = 2
    private const val subSportIndoorCycling = 6
    private const val maxUInt16 = 0xFFFFL
    private const val maxUInt32 = 0xFFFF_FFFFL

    /**
     * Generates a deterministic default filename for create-document flows.
     */
    fun suggestedFileName(summary: SessionSummary): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)
        val timestamp = summary.stopTimestampMillis
        val label = formatter.format(Date(timestamp))
        return "session_$label.fit"
    }

    /**
     * Writes one FIT activity file to the provided destination URI.
     */
    fun exportToUri(
        context: Context,
        uri: Uri,
        snapshot: SessionExportSnapshot?,
    ): FitExportResult {
        val resolvedSnapshot = snapshot
            ?: return FitExportResult.Failure(FitExportFailureReason.NO_SUMMARY)
        val resolvedSummary = resolvedSnapshot.summary
        if (resolvedSummary.stopTimestampMillis < resolvedSummary.startTimestampMillis) {
            return FitExportResult.Failure(FitExportFailureReason.INVALID_TIMESTAMPS)
        }

        val payload = runCatching { buildFitBytes(resolvedSnapshot) }
            .getOrElse { throwable ->
                return FitExportResult.Failure(
                    reason = FitExportFailureReason.WRITE_FAILED,
                    detail = throwable.message,
                )
            }

        val output = context.contentResolver.openOutputStream(uri, "w")
            ?: return FitExportResult.Failure(FitExportFailureReason.OUTPUT_STREAM_UNAVAILABLE)

        return runCatching {
            output.use { stream -> stream.write(payload) }
            FitExportResult.Success
        }.getOrElse { throwable ->
            FitExportResult.Failure(
                reason = FitExportFailureReason.WRITE_FAILED,
                detail = throwable.message,
            )
        }
    }

    internal fun buildFitBytes(snapshot: SessionExportSnapshot): ByteArray {
        val summary = snapshot.summary
        val startFit = unixMillisToFitSeconds(summary.startTimestampMillis)
        val stopFit = unixMillisToFitSeconds(summary.stopTimestampMillis)
        val elapsedMillis =
            (summary.elapsedDurationSeconds.coerceAtLeast(0).toLong() * 1000L).coerceAtMost(maxUInt32)
        val activeMillis =
            (summary.durationSeconds.coerceAtLeast(0).toLong() * 1000L).coerceAtMost(maxUInt32)
        val totalDistanceCm = ((summary.distanceMeters ?: 0).coerceAtLeast(0).toLong() * 100L)
            .coerceAtMost(maxUInt32)
        val totalCalories = ((summary.totalEnergyKcal ?: 0).coerceAtLeast(0).toLong())
            .coerceAtMost(maxUInt16)
        val avgPower = ((summary.avgPower ?: 0).coerceAtLeast(0).toLong()).coerceAtMost(maxUInt16)
        val maxPower = ((summary.maxPower ?: 0).coerceAtLeast(0).toLong()).coerceAtMost(maxUInt16)
        val avgHr = ((summary.avgHeartRate ?: 0).coerceAtLeast(0).toLong()).coerceAtMost(0xFF)
        val maxHr = ((summary.maxHeartRate ?: 0).coerceAtLeast(0).toLong()).coerceAtMost(0xFF)
        val avgCadence = ((summary.avgCadence ?: 0).coerceAtLeast(0).toLong()).coerceAtMost(0xFF)
        val maxCadence = ((summary.maxCadence ?: 0).coerceAtLeast(0).toLong()).coerceAtMost(0xFF)
        val tssScaled = summary.actualTss
            ?.coerceAtLeast(0.0)
            ?.times(10.0)
            ?.roundToLong()
            ?.coerceAtMost(maxUInt16)

        val fitWriter = FitBinaryWriter(
            protocolVersion = fitProtocolVersionV1,
            profileVersion = fitProfileVersion,
        )

        val fileIdFields = listOf(
            FitField(
                number = 0,
                size = 1,
                baseType = BaseType.ENUM,
                value = fileTypeActivity.toLong(),
            ),
            FitField(
                number = 1,
                size = 2,
                baseType = BaseType.UINT16,
                value = manufacturerDevelopment.toLong(),
            ),
            FitField(
                number = 2,
                size = 2,
                baseType = BaseType.UINT16,
                value = 1L,
            ),
            FitField(
                number = 3,
                size = 4,
                baseType = BaseType.UINT32Z,
                value = (startFit.takeIf { it > 0 } ?: 1L).coerceAtMost(maxUInt32),
            ),
            FitField(
                number = 4,
                size = 4,
                baseType = BaseType.UINT32,
                value = stopFit,
            ),
        )
        fitWriter.writeDefinition(localMessage = 0, globalMessage = 0, fields = fileIdFields)
        fitWriter.writeData(localMessage = 0, fields = fileIdFields)

        val recordDefinition = listOf(
            FitField(number = 253, size = 4, baseType = BaseType.UINT32, value = null),
            FitField(number = 3, size = 1, baseType = BaseType.UINT8, value = null),
            FitField(number = 4, size = 1, baseType = BaseType.UINT8, value = null),
            FitField(number = 5, size = 4, baseType = BaseType.UINT32, value = null),
            FitField(number = 7, size = 2, baseType = BaseType.UINT16, value = null),
        )
        fitWriter.writeDefinition(localMessage = 1, globalMessage = 20, fields = recordDefinition)
        val recordSamples = timelineOrFallback(snapshot)
        recordSamples.forEach { sample ->
            fitWriter.writeData(
                localMessage = 1,
                fields = listOf(
                    FitField(number = 253, size = 4, baseType = BaseType.UINT32, value = sample.fitTimestamp),
                    FitField(number = 3, size = 1, baseType = BaseType.UINT8, value = sample.heartRateBpm),
                    FitField(number = 4, size = 1, baseType = BaseType.UINT8, value = sample.cadenceRpm),
                    FitField(number = 5, size = 4, baseType = BaseType.UINT32, value = sample.distanceCm),
                    FitField(number = 7, size = 2, baseType = BaseType.UINT16, value = sample.powerWatts),
                ),
            )
        }

        val lapFields = listOf(
            FitField(number = 253, size = 4, baseType = BaseType.UINT32, value = stopFit),
            FitField(number = 2, size = 4, baseType = BaseType.UINT32, value = startFit),
            FitField(number = 7, size = 4, baseType = BaseType.UINT32, value = elapsedMillis),
            FitField(number = 8, size = 4, baseType = BaseType.UINT32, value = activeMillis),
            FitField(number = 9, size = 4, baseType = BaseType.UINT32, value = totalDistanceCm),
            FitField(number = 11, size = 2, baseType = BaseType.UINT16, value = totalCalories),
            FitField(number = 19, size = 2, baseType = BaseType.UINT16, value = avgPower),
            FitField(number = 20, size = 2, baseType = BaseType.UINT16, value = maxPower),
            FitField(number = 15, size = 1, baseType = BaseType.UINT8, value = avgHr),
            FitField(number = 16, size = 1, baseType = BaseType.UINT8, value = maxHr),
            FitField(number = 17, size = 1, baseType = BaseType.UINT8, value = avgCadence),
            FitField(number = 18, size = 1, baseType = BaseType.UINT8, value = maxCadence),
        )
        fitWriter.writeDefinition(localMessage = 2, globalMessage = 19, fields = lapFields)
        fitWriter.writeData(localMessage = 2, fields = lapFields)

        val sessionFields = buildList {
            add(FitField(number = 253, size = 4, baseType = BaseType.UINT32, value = stopFit))
            add(FitField(number = 2, size = 4, baseType = BaseType.UINT32, value = startFit))
            add(FitField(number = 5, size = 1, baseType = BaseType.ENUM, value = sportCycling.toLong()))
            add(FitField(number = 6, size = 1, baseType = BaseType.ENUM, value = subSportIndoorCycling.toLong()))
            add(FitField(number = 7, size = 4, baseType = BaseType.UINT32, value = elapsedMillis))
            add(FitField(number = 8, size = 4, baseType = BaseType.UINT32, value = activeMillis))
            add(FitField(number = 9, size = 4, baseType = BaseType.UINT32, value = totalDistanceCm))
            add(FitField(number = 11, size = 2, baseType = BaseType.UINT16, value = totalCalories))
            add(FitField(number = 20, size = 2, baseType = BaseType.UINT16, value = avgPower))
            add(FitField(number = 21, size = 2, baseType = BaseType.UINT16, value = maxPower))
            add(FitField(number = 16, size = 1, baseType = BaseType.UINT8, value = avgHr))
            add(FitField(number = 17, size = 1, baseType = BaseType.UINT8, value = maxHr))
            add(FitField(number = 18, size = 1, baseType = BaseType.UINT8, value = avgCadence))
            add(FitField(number = 19, size = 1, baseType = BaseType.UINT8, value = maxCadence))
            add(FitField(number = 26, size = 2, baseType = BaseType.UINT16, value = 1L))
            if (tssScaled != null) {
                add(FitField(number = 35, size = 2, baseType = BaseType.UINT16, value = tssScaled))
            }
        }
        fitWriter.writeDefinition(localMessage = 3, globalMessage = 18, fields = sessionFields)
        fitWriter.writeData(localMessage = 3, fields = sessionFields)

        val activityFields = listOf(
            FitField(number = 253, size = 4, baseType = BaseType.UINT32, value = stopFit),
            FitField(number = 0, size = 4, baseType = BaseType.UINT32, value = activeMillis),
            FitField(number = 1, size = 2, baseType = BaseType.UINT16, value = 1L),
            FitField(number = 2, size = 1, baseType = BaseType.ENUM, value = activityTypeManual.toLong()),
            FitField(number = 3, size = 1, baseType = BaseType.ENUM, value = eventActivity.toLong()),
            FitField(number = 4, size = 1, baseType = BaseType.ENUM, value = eventTypeStop.toLong()),
        )
        fitWriter.writeDefinition(localMessage = 4, globalMessage = 34, fields = activityFields)
        fitWriter.writeData(localMessage = 4, fields = activityFields)

        return fitWriter.toFileBytes()
    }

    private fun unixMillisToFitSeconds(unixMillis: Long): Long {
        val seconds = (unixMillis - fitEpochOffsetMillis) / 1000L
        return seconds.coerceIn(0L, maxUInt32)
    }

    private fun timelineOrFallback(snapshot: SessionExportSnapshot): List<FitRecordSample> {
        val summary = snapshot.summary
        val startMillis = summary.startTimestampMillis
        val stopMillis = summary.stopTimestampMillis
        val samples = snapshot.timeline
            .asSequence()
            .map { sample ->
                val timestampMillis = sample.timestampMillis.coerceIn(startMillis, stopMillis)
                val fitTimestamp = unixMillisToFitSeconds(timestampMillis)
                FitRecordSample(
                    fitTimestamp = fitTimestamp,
                    powerWatts = sample.powerWatts?.toLong()?.coerceIn(0L, maxUInt16),
                    cadenceRpm = sample.cadenceRpm?.toLong()?.coerceIn(0L, 0xFFL),
                    heartRateBpm = sample.heartRateBpm?.toLong()?.coerceIn(0L, 0xFFL),
                    distanceCm = sample.distanceMeters?.toLong()?.coerceAtLeast(0L)?.times(100L)?.coerceAtMost(maxUInt32),
                )
            }
            .sortedBy { it.fitTimestamp }
            .distinctBy { it.fitTimestamp }
            .toList()
        if (samples.isNotEmpty()) return samples

        return listOf(
            FitRecordSample(
                fitTimestamp = unixMillisToFitSeconds(summary.stopTimestampMillis),
                powerWatts = summary.avgPower?.toLong()?.coerceIn(0L, maxUInt16),
                cadenceRpm = summary.avgCadence?.toLong()?.coerceIn(0L, 0xFFL),
                heartRateBpm = summary.avgHeartRate?.toLong()?.coerceIn(0L, 0xFFL),
                distanceCm = summary.distanceMeters?.toLong()?.coerceAtLeast(0L)?.times(100L)?.coerceAtMost(maxUInt32),
            ),
        )
    }
}

private data class FitRecordSample(
    val fitTimestamp: Long,
    val powerWatts: Long?,
    val cadenceRpm: Long?,
    val heartRateBpm: Long?,
    val distanceCm: Long?,
)

private data class FitField(
    val number: Int,
    val size: Int,
    val baseType: BaseType,
    val value: Long?,
)

private enum class BaseType(val id: Int) {
    ENUM(0x00),
    UINT8(0x02),
    UINT16(0x84),
    UINT32(0x86),
    UINT32Z(0x8C),
}

/**
 * Minimal FIT binary writer that covers definition/data records needed by v1 export.
 */
private class FitBinaryWriter(
    private val protocolVersion: Int,
    private val profileVersion: Int,
) {
    private val headerSize = 14
    private val messageBytes = ByteArrayOutputStream()
    private val localDefinitions = HashMap<Int, List<FitField>>()

    fun writeDefinition(localMessage: Int, globalMessage: Int, fields: List<FitField>) {
        require(localMessage in 0..0x0F) { "Local message must be 0..15" }
        require(globalMessage in 0..0xFFFF) { "Global message must be 0..65535" }

        messageBytes.write((0x40 or (localMessage and 0x0F)))
        messageBytes.write(0x00)
        messageBytes.write(0x01) // big-endian architecture
        writeUInt16BigEndian(messageBytes, globalMessage.toLong())
        messageBytes.write(fields.size and 0xFF)
        fields.forEach { field ->
            messageBytes.write(field.number and 0xFF)
            messageBytes.write(field.size and 0xFF)
            messageBytes.write(field.baseType.id and 0xFF)
        }
        localDefinitions[localMessage] = fields
    }

    fun writeData(localMessage: Int, fields: List<FitField>) {
        val definition = localDefinitions[localMessage]
            ?: error("Missing definition for local message $localMessage")
        check(hasSameFieldLayout(definition, fields)) {
            "Data fields must match the active local message definition"
        }
        messageBytes.write(localMessage and 0x0F)
        fields.forEach { field ->
            writeFieldValue(field)
        }
    }

    private fun hasSameFieldLayout(definition: List<FitField>, data: List<FitField>): Boolean {
        if (definition.size != data.size) return false
        return definition.zip(data).all { (expected, actual) ->
            expected.number == actual.number &&
                expected.size == actual.size &&
                expected.baseType == actual.baseType
        }
    }

    fun toFileBytes(): ByteArray {
        val data = messageBytes.toByteArray()
        val header = ByteArray(headerSize)
        header[0] = headerSize.toByte()
        header[1] = (protocolVersion and 0xFF).toByte()
        writeUInt16LittleEndian(header, offset = 2, value = profileVersion.toLong())
        writeUInt32LittleEndian(header, offset = 4, value = data.size.toLong())
        header[8] = '.'.code.toByte()
        header[9] = 'F'.code.toByte()
        header[10] = 'I'.code.toByte()
        header[11] = 'T'.code.toByte()

        val headerCrc = crc16(initial = 0, buffer = header, until = headerSize - 2)
        writeUInt16LittleEndian(header, offset = headerSize - 2, value = headerCrc.toLong())

        val payload = ByteArrayOutputStream()
        payload.writeBytes(header)
        payload.writeBytes(data)
        val withoutFileCrc = payload.toByteArray()
        val fileCrc = crc16(initial = 0, buffer = withoutFileCrc, until = withoutFileCrc.size)

        val fileBytes = ByteArrayOutputStream()
        fileBytes.writeBytes(withoutFileCrc)
        writeUInt16LittleEndian(fileBytes, fileCrc.toLong())
        return fileBytes.toByteArray()
    }

    private fun writeFieldValue(field: FitField) {
        when (field.baseType) {
            BaseType.ENUM, BaseType.UINT8 -> {
                val resolved = field.value
                    ?.coerceIn(0L, 0xFFL)
                    ?: 0xFFL
                messageBytes.write((resolved and 0xFF).toInt())
            }

            BaseType.UINT16 -> {
                val resolved = field.value
                    ?.coerceIn(0L, 0xFFFFL)
                    ?: 0xFFFFL
                writeUInt16BigEndian(messageBytes, resolved)
            }

            BaseType.UINT32 -> {
                val resolved = field.value
                    ?.coerceIn(0L, 0xFFFF_FFFFL)
                    ?: 0xFFFF_FFFFL
                writeUInt32BigEndian(messageBytes, resolved)
            }

            BaseType.UINT32Z -> {
                val resolved = field.value
                    ?.coerceIn(1L, 0xFFFF_FFFFL)
                    ?: 0L
                writeUInt32BigEndian(messageBytes, resolved)
            }
        }
    }
}

private fun crc16(
    initial: Int,
    buffer: ByteArray,
    until: Int,
): Int {
    val table = intArrayOf(
        0x0000, 0xCC01, 0xD801, 0x1400,
        0xF001, 0x3C00, 0x2800, 0xE401,
        0xA001, 0x6C00, 0x7800, 0xB401,
        0x5000, 0x9C01, 0x8801, 0x4400,
    )
    var crc = initial
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

private fun writeUInt16BigEndian(out: ByteArrayOutputStream, value: Long) {
    out.write(((value shr 8) and 0xFF).toInt())
    out.write((value and 0xFF).toInt())
}

private fun writeUInt32BigEndian(out: ByteArrayOutputStream, value: Long) {
    out.write(((value shr 24) and 0xFF).toInt())
    out.write(((value shr 16) and 0xFF).toInt())
    out.write(((value shr 8) and 0xFF).toInt())
    out.write((value and 0xFF).toInt())
}

private fun writeUInt16LittleEndian(buffer: ByteArray, offset: Int, value: Long) {
    buffer[offset] = (value and 0xFF).toByte()
    buffer[offset + 1] = ((value shr 8) and 0xFF).toByte()
}

private fun writeUInt32LittleEndian(buffer: ByteArray, offset: Int, value: Long) {
    buffer[offset] = (value and 0xFF).toByte()
    buffer[offset + 1] = ((value shr 8) and 0xFF).toByte()
    buffer[offset + 2] = ((value shr 16) and 0xFF).toByte()
    buffer[offset + 3] = ((value shr 24) and 0xFF).toByte()
}

private fun writeUInt16LittleEndian(out: ByteArrayOutputStream, value: Long) {
    out.write((value and 0xFF).toInt())
    out.write(((value shr 8) and 0xFF).toInt())
}

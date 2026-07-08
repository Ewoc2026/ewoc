package io.github.ewoc2026.ewoc.compat

import android.content.Context
import android.util.Log
import io.github.ewoc2026.ewoc.compat.quirks.MatchConfidence
import java.nio.charset.StandardCharsets
import java.util.Locale
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * Persistable output of one completed compatibility run.
 */
data class CompatibilityRunArtifacts(
    val runId: String,
    val capturedAtEpochMs: Long,
    val trainerIdentity: String?,
    val trainerAlias: String?,
    val androidManufacturer: String? = null,
    val androidModel: String? = null,
    val quirksNotes: String? = null,
    val result: CompatibilityCheckResult,
)

/**
 * Writes latest compatibility run artifacts into app-private files for support triage.
 */
object CompatibilityRunArtifactsStorage {
    private const val logTag = "COMPAT_SUPPORT"
    private const val summaryFileName = "compatibility-last-summary.json"
    private const val timelineFileName = "compatibility-last-timeline.jsonl"
    private val structuredJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun persist(context: Context, artifacts: CompatibilityRunArtifacts): Boolean {
        return runCatching {
            context.openFileOutput(summaryFileName, Context.MODE_PRIVATE).use { stream ->
                stream.write(buildSummaryJson(artifacts).toByteArray(StandardCharsets.UTF_8))
            }
            context.openFileOutput(timelineFileName, Context.MODE_PRIVATE).use { stream ->
                stream.write(buildTimelineJsonl(artifacts).toByteArray(StandardCharsets.UTF_8))
            }
        }.isSuccess
    }

    /**
     * Loads the latest persisted compatibility run artifacts for post-restart export flows.
     *
     * Returns null when artifacts are absent or malformed.
     */
    fun loadLatest(context: Context): CompatibilityRunArtifacts? {
        return runCatching {
            val summaryJson = context.openFileInput(summaryFileName).bufferedReader().use { reader ->
                reader.readText()
            }
            val timelineJsonl = runCatching {
                context.openFileInput(timelineFileName).bufferedReader().use { reader ->
                    reader.readText()
                }
            }.getOrDefault("")
            val rebuilt = rebuildArtifactsFromPersistedJson(summaryJson, timelineJsonl)
            if (rebuilt == null) {
                Log.w(logTag, "loadLatest: persisted compatibility artifacts could not be parsed.")
            }
            rebuilt
        }.getOrElse { throwable ->
            Log.w(logTag, "loadLatest: failed to read persisted compatibility artifacts.", throwable)
            null
        }
    }

    internal fun buildSummaryJson(artifacts: CompatibilityRunArtifacts): String {
        val summary = artifacts.result.summary
        val fields = linkedMapOf<String, Any?>(
            "run_id" to artifacts.runId,
            "captured_at_epoch_ms" to artifacts.capturedAtEpochMs,
            "trainer_identity" to artifacts.trainerIdentity,
            "trainer_alias" to artifacts.trainerAlias,
            "android_manufacturer" to artifacts.androidManufacturer,
            "android_model" to artifacts.androidModel,
            "quirks_notes" to artifacts.quirksNotes,
            "status" to summary.status.name.lowercase(),
            "started_at_epoch_ms" to summary.startedAtEpochMs,
            "ended_at_epoch_ms" to summary.endedAtEpochMs,
            "elapsed_ms" to summary.elapsedMs,
            "total_budget_ms" to summary.totalBudgetMs,
            "quirks_id" to summary.quirksId,
            "quirks_match_confidence" to summary.quirksMatchConfidence.name.lowercase(),
            "degradation_signals" to summary.degradationSignals.map { signal ->
                signal.name.lowercase()
            },
            "failure_code" to summary.failureCode?.name?.lowercase(),
            "failure_category" to summary.failureCategory?.name?.lowercase(),
            "failure_reason_key" to summary.failureReasonKey,
            "failure_detail" to summary.failureDetail,
            "timeline_event_count" to artifacts.result.timeline.size,
        )
        return encodeJson(fields)
    }

    internal fun buildTimelineJsonl(artifacts: CompatibilityRunArtifacts): String {
        return artifacts.result.timeline.joinToString(separator = "\n") { event ->
            val fields = linkedMapOf<String, Any?>(
                "run_id" to artifacts.runId,
                "ts_epoch_ms" to event.tsEpochMs,
                "category" to event.category,
                "event" to event.event,
                "status" to event.status,
                "details" to event.details,
            )
            encodeJson(fields)
        }
    }

    /**
     * Rebuilds one [CompatibilityRunArtifacts] instance from persisted summary/timeline JSON.
     */
    internal fun rebuildArtifactsFromPersistedJson(
        summaryJson: String,
        timelineJsonl: String,
    ): CompatibilityRunArtifacts? {
        val runId = findJsonStringValue(summaryJson, "run_id") ?: return null
        val capturedAtEpochMs = findJsonLongValue(summaryJson, "captured_at_epoch_ms") ?: return null
        val status = parseSummaryStatus(findJsonStringValue(summaryJson, "status")) ?: return null
        val startedAtEpochMs = findJsonLongValue(summaryJson, "started_at_epoch_ms") ?: return null
        val endedAtEpochMs = findJsonLongValue(summaryJson, "ended_at_epoch_ms") ?: return null
        val elapsedMs = findJsonLongValue(summaryJson, "elapsed_ms") ?: return null
        val totalBudgetMs = findJsonLongValue(summaryJson, "total_budget_ms") ?: return null
        val quirksId = findJsonStringValue(summaryJson, "quirks_id") ?: return null
        val quirksMatchConfidence = parseMatchConfidence(
            findJsonStringValue(summaryJson, "quirks_match_confidence"),
        ) ?: return null
        val degradationSignals = parseDegradationSignals(
            findJsonStringArray(summaryJson, "degradation_signals"),
        ) ?: return null

        val summary = CompatibilitySummaryOutput(
            status = status,
            startedAtEpochMs = startedAtEpochMs,
            endedAtEpochMs = endedAtEpochMs,
            elapsedMs = elapsedMs,
            totalBudgetMs = totalBudgetMs,
            quirksId = quirksId,
            quirksMatchConfidence = quirksMatchConfidence,
            degradationSignals = degradationSignals,
            failureCode = parseFailureCode(findJsonStringValue(summaryJson, "failure_code")),
            failureCategory = parseFailureCategory(findJsonStringValue(summaryJson, "failure_category")),
            failureReasonKey = findJsonStringValue(summaryJson, "failure_reason_key"),
            failureDetail = findJsonStringValue(summaryJson, "failure_detail"),
        )

        val timeline = mutableListOf<CompatibilityTimelineEvent>()
        timelineJsonl
            .lineSequence()
            .map { line -> line.trim() }
            .filter { line -> line.isNotEmpty() }
            .forEach { line ->
                val parsedEvent = parseTimelineEvent(line, expectedRunId = runId) ?: return@forEach
                timeline += parsedEvent
            }

        return CompatibilityRunArtifacts(
            runId = runId,
            capturedAtEpochMs = capturedAtEpochMs,
            trainerIdentity = findJsonStringValue(summaryJson, "trainer_identity"),
            trainerAlias = findJsonStringValue(summaryJson, "trainer_alias"),
            androidManufacturer = findJsonStringValue(summaryJson, "android_manufacturer"),
            androidModel = findJsonStringValue(summaryJson, "android_model"),
            quirksNotes = findJsonStringValue(summaryJson, "quirks_notes"),
            result = CompatibilityCheckResult(
                summary = summary,
                timeline = timeline,
            ),
        )
    }

    private fun encodeJson(value: Any?): String {
        return when (value) {
            null -> "null"
            is String -> "\"${escapeJson(value)}\""
            is Number,
            is Boolean,
            -> value.toString()

            is Map<*, *> -> {
                value.entries.joinToString(
                    prefix = "{",
                    postfix = "}",
                    separator = ",",
                ) { (key, mapValue) ->
                    "\"${escapeJson(key.toString())}\":${encodeJson(mapValue)}"
                }
            }

            is Iterable<*> -> {
                value.joinToString(
                    prefix = "[",
                    postfix = "]",
                    separator = ",",
                ) { entry ->
                    encodeJson(entry)
                }
            }

            else -> "\"${escapeJson(value.toString())}\""
        }
    }

    private fun escapeJson(value: String): String {
        val builder = StringBuilder(value.length + 16)
        for (char in value) {
            when (char) {
                '\\' -> builder.append("\\\\")
                '"' -> builder.append("\\\"")
                '\b' -> builder.append("\\b")
                '\u000C' -> builder.append("\\f")
                '\n' -> builder.append("\\n")
                '\r' -> builder.append("\\r")
                '\t' -> builder.append("\\t")
                else -> {
                    if (char.code < 0x20) {
                        builder.append("\\u")
                        builder.append(char.code.toString(16).padStart(4, '0'))
                    } else {
                        builder.append(char)
                    }
                }
            }
        }
        return builder.toString()
    }

    private fun parseTimelineEvent(
        timelineJsonLine: String,
        expectedRunId: String,
    ): CompatibilityTimelineEvent? {
        val timelineJson = parseJsonObject(timelineJsonLine) ?: return null
        val runId = readRequiredStringValue(timelineJson, "run_id") ?: return null
        if (runId != expectedRunId) return null
        val tsEpochMs = readRequiredLongValue(timelineJson, "ts_epoch_ms") ?: return null
        val category = readRequiredStringValue(timelineJson, "category") ?: return null
        val event = readRequiredStringValue(timelineJson, "event") ?: return null
        val status = readRequiredStringValue(timelineJson, "status") ?: return null
        val details = parseDetailsMap(timelineJson["details"]) ?: return null
        return CompatibilityTimelineEvent(
            tsEpochMs = tsEpochMs,
            category = category,
            event = event,
            status = status,
            details = details,
        )
    }

    private fun parseJsonObject(json: String): JsonObject? {
        return runCatching { structuredJson.parseToJsonElement(json).jsonObject }.getOrNull()
    }

    private fun readRequiredStringValue(json: JsonObject, key: String): String? {
        val value = json[key] ?: return null
        if (value is JsonNull) return null
        val primitive = value as? JsonPrimitive ?: return null
        return primitive.content
    }

    private fun readRequiredLongValue(json: JsonObject, key: String): Long? {
        val value = json[key] ?: return null
        val primitive = value as? JsonPrimitive ?: return null
        return primitive.content.toLongOrNull()
    }

    private fun parseDetailsMap(rawDetails: JsonElement?): Map<String, String>? {
        return when (rawDetails) {
            null,
            JsonNull,
            -> emptyMap()

            is JsonObject -> parseDetailsObject(rawDetails)
            // Some older artifacts can persist details as a quoted JSON object payload.
            is JsonPrimitive -> {
                if (!rawDetails.isString) {
                    null
                } else {
                    parseLegacyDetailsString(rawDetails.content)
                }
            }

            else -> null
        }
    }

    private fun parseLegacyDetailsString(rawDetails: String): Map<String, String>? {
        val trimmed = rawDetails.trim()
        if (trimmed.isEmpty()) return emptyMap()
        val parsedObject = parseJsonObject(trimmed) ?: return null
        return parseDetailsObject(parsedObject)
    }

    private fun parseDetailsObject(detailsObject: JsonObject): Map<String, String> {
        val parsedDetails = linkedMapOf<String, String>()
        detailsObject.forEach { (key, rawValue) ->
            parsedDetails[key] = coerceDetailValueToString(rawValue)
        }
        return parsedDetails
    }

    private fun coerceDetailValueToString(rawValue: JsonElement): String {
        return when (rawValue) {
            JsonNull,
            -> "null"

            is JsonPrimitive -> rawValue.content
            is JsonObject,
            is JsonArray,
            -> rawValue.toString()
        }
    }

    private fun parseSummaryStatus(rawValue: String?): CompatibilitySummaryStatus? {
        return when (rawValue?.lowercase(Locale.US)) {
            "pass" -> CompatibilitySummaryStatus.PASS
            "fail" -> CompatibilitySummaryStatus.FAIL
            else -> null
        }
    }

    private fun parseMatchConfidence(rawValue: String?): MatchConfidence? {
        val normalized = rawValue?.trim()?.uppercase(Locale.US) ?: return null
        return MatchConfidence.entries.firstOrNull { entry -> entry.name == normalized }
    }

    private fun parseDegradationSignals(rawValues: List<String>?): List<CompatibilityDegradationSignal>? {
        if (rawValues == null) return null
        val parsed = mutableListOf<CompatibilityDegradationSignal>()
        for (rawValue in rawValues) {
            val normalized = rawValue.trim().uppercase(Locale.US)
            val parsedSignal = CompatibilityDegradationSignal.entries.firstOrNull { signal ->
                signal.name == normalized
            } ?: return null
            parsed += parsedSignal
        }
        return parsed
    }

    private fun parseFailureCode(rawValue: String?): CompatibilityFailureCode? {
        val normalized = rawValue?.trim()?.uppercase(Locale.US) ?: return null
        return CompatibilityFailureCode.entries.firstOrNull { code -> code.name == normalized }
    }

    private fun parseFailureCategory(rawValue: String?): CompatibilityFailureCategory? {
        val normalized = rawValue?.trim()?.uppercase(Locale.US) ?: return null
        return CompatibilityFailureCategory.entries.firstOrNull { category ->
            category.name == normalized
        }
    }

    private fun findJsonStringArray(json: String, key: String): List<String>? {
        val arrayRegex = Regex("\"${Regex.escape(key)}\":\\[(.*?)]")
        val match = arrayRegex.find(json) ?: return null
        val rawArray = match.groupValues[1].trim()
        if (rawArray.isEmpty()) return emptyList()
        return Regex("\"((?:\\\\.|[^\"\\\\])*)\"")
            .findAll(rawArray)
            .map { entry -> unescapeJson(entry.groupValues[1]) }
            .toList()
    }

    private fun findJsonLongValue(json: String, key: String): Long? {
        val regex = Regex("\"${Regex.escape(key)}\":(-?\\d+)")
        val match = regex.find(json) ?: return null
        return match.groupValues[1].toLongOrNull()
    }

    private fun findJsonStringValue(json: String, key: String): String? {
        val regex = Regex("\"${Regex.escape(key)}\":(\"((?:\\\\.|[^\"\\\\])*)\"|null)")
        val match = regex.find(json) ?: return null
        val rawValue = match.groupValues[1]
        if (rawValue == "null") return null
        return unescapeJson(match.groupValues[2])
    }

    private fun unescapeJson(value: String): String {
        val builder = StringBuilder(value.length)
        var index = 0
        while (index < value.length) {
            val char = value[index]
            if (char != '\\' || index + 1 >= value.length) {
                builder.append(char)
                index += 1
                continue
            }
            val escaped = value[index + 1]
            when (escaped) {
                '\\' -> builder.append('\\')
                '"' -> builder.append('"')
                'b' -> builder.append('\b')
                'f' -> builder.append('\u000C')
                'n' -> builder.append('\n')
                'r' -> builder.append('\r')
                't' -> builder.append('\t')
                'u' -> {
                    if (index + 5 < value.length) {
                        val hex = value.substring(index + 2, index + 6)
                        val decoded = hex.toIntOrNull(16)
                        if (decoded != null) {
                            builder.append(decoded.toChar())
                            index += 4
                        }
                    }
                }
                else -> builder.append(escaped)
            }
            index += 2
        }
        return builder.toString()
    }
}

package com.ewo.core

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Converts raw canonical `.ewo` JSON into the parsed authoring model while rejecting unknown
 * fields and unsupported shape variants before semantic validation begins.
 */
internal object EwoWorkoutSchemaValidator {
    private const val FTP_PERCENT_MIN = 0.10
    private const val FTP_PERCENT_MAX = 2.50
    private val allowedDifficulties = setOf("easy", "moderate", "hard", "very_hard")

    private val allowedMessageKinds = setOf(
        "intro",
        "instruction",
        "transition",
        "warning",
        "motivation",
    )
    private val allowedMessageAnchors = setOf("start", "end")
    private const val segmentIdPattern = "^[a-z][a-z0-9_-]{0,63}$"

    fun validate(root: JsonObject, rootPath: String): ParsedEwoWorkoutFile {
        requireOnlyKeys(
            json = root,
            allowedKeys = setOf(
                "format", "version", "title", "description",
                "title_localized", "description_localized",
                "uid", "revision",
                "difficulty", "tags",
                "control", "messages", "segments",
            ),
            path = rootPath,
        )
        return ParsedEwoWorkoutFile(
            format = requireStringField(root, "format", rootPath),
            version = requireStringField(root, "version", rootPath),
            uid = optionalStringField(root, "uid", rootPath),
            revision = root["revision"]?.let {
                requireIntValue(it, ewoChildPath(rootPath, "revision"))
            },
            title = requireStringField(root, "title", rootPath),
            description = optionalStringField(root, "description", rootPath),
            titleLocalized = root["title_localized"]?.let {
                parseLocalizedText(it, ewoChildPath(rootPath, "title_localized"))
            },
            descriptionLocalized = root["description_localized"]?.let {
                parseLocalizedText(it, ewoChildPath(rootPath, "description_localized"))
            },
            difficulty = root["difficulty"]?.let {
                parseDifficulty(it, ewoChildPath(rootPath, "difficulty"))
            },
            tags = root["tags"]?.let {
                parseTags(it, ewoChildPath(rootPath, "tags"))
            } ?: emptyList(),
            control = root["control"]?.let { parseControl(it, ewoChildPath(rootPath, "control")) },
            messages = root["messages"]?.let {
                parseMessages(it, ewoChildPath(rootPath, "messages"))
            } ?: emptyList(),
            segments = requireArrayField(root, "segments", rootPath).mapIndexed { index, element ->
                parseSegment(element, ewoIndexPath(ewoChildPath(rootPath, "segments"), index))
            },
        )
    }

    private fun parseControl(element: JsonElement, path: String): ParsedEwoWorkoutControl {
        val json = requireObject(element, path)
        requireOnlyKeys(
            json = json,
            allowedKeys = setOf(
                "initial_power_watts",
                "min_power_watts",
                "max_power_watts",
                "signal_loss_power_watts",
                "hr_upper_cap_bpm",
            ),
            path = path,
        )
        return ParsedEwoWorkoutControl(
            initialPowerWatts = requireIntField(json, "initial_power_watts", path),
            minPowerWatts = requireIntField(json, "min_power_watts", path),
            maxPowerWatts = requireIntField(json, "max_power_watts", path),
            signalLossPowerWatts = requireIntField(json, "signal_loss_power_watts", path),
            hrUpperCapBpm = requireIntField(json, "hr_upper_cap_bpm", path),
        )
    }

    private fun parseMessages(element: JsonElement, path: String): List<ParsedEwoMessage> {
        return requireArray(element, path).mapIndexed { index, messageElement ->
            parseMessage(messageElement, ewoIndexPath(path, index))
        }
    }

    private fun parseMessage(element: JsonElement, path: String): ParsedEwoMessage {
        val json = requireObject(element, path)
        requireOnlyKeys(json, setOf("kind", "when", "text"), path)
        val kindPath = ewoChildPath(path, "kind")
        val kind = requireStringField(json, "kind", path)
        if (kind !in allowedMessageKinds) {
            failEwoValidation(
                code = EwoWorkoutValidationErrorCode.INVALID_MESSAGE_KIND,
                message = "Unsupported message kind '$kind'.",
                fieldPath = kindPath,
            )
        }
        return ParsedEwoMessage(
            kind = kind,
            timing = parseMessageTiming(
                element = requireField(json, "when", path),
                path = ewoChildPath(path, "when"),
            ),
            text = parseLocalizedText(
                element = requireField(json, "text", path),
                path = ewoChildPath(path, "text"),
            ),
        )
    }

    private fun parseMessageTiming(element: JsonElement, path: String): ParsedEwoMessageTiming {
        return when (element) {
            is JsonPrimitive -> {
                val anchor = requireStringValue(element, path)
                if (anchor !in allowedMessageAnchors) {
                    failEwoValidation(
                        code = EwoWorkoutValidationErrorCode.INVALID_MESSAGE_WHEN,
                        message = "Message anchor must be one of: ${allowedMessageAnchors.sorted().joinToString()}.",
                        fieldPath = path,
                    )
                }
                ParsedEwoMessageTiming(anchor = anchor, offsetSec = 0)
            }

            is JsonObject -> {
                requireOnlyKeys(element, setOf("anchor", "offset_sec"), path)
                val anchor = requireStringField(element, "anchor", path)
                if (anchor !in allowedMessageAnchors) {
                    failEwoValidation(
                        code = EwoWorkoutValidationErrorCode.INVALID_MESSAGE_WHEN,
                        message = "Message anchor must be one of: ${allowedMessageAnchors.sorted().joinToString()}.",
                        fieldPath = ewoChildPath(path, "anchor"),
                    )
                }
                ParsedEwoMessageTiming(
                    anchor = anchor,
                    offsetSec = requireIntField(element, "offset_sec", path),
                )
            }

            else -> failEwoValidation(
                code = EwoWorkoutValidationErrorCode.INVALID_TYPE,
                message = "Message when must be a string or object.",
                fieldPath = path,
            )
        }
    }

    private fun parseLocalizedText(element: JsonElement, path: String): ParsedEwoLocalizedText {
        val json = requireObject(element, path)
        requireOnlyKeys(json, setOf("default", "translations"), path)
        val translations = json["translations"]?.let { translationElement ->
            val translationsPath = ewoChildPath(path, "translations")
            val translationObject = requireObject(translationElement, translationsPath)
            translationObject.entries.associate { (key, value) ->
                if (key.isEmpty()) {
                    failEwoValidation(
                        code = EwoWorkoutValidationErrorCode.INVALID_LOCALIZED_TEXT,
                        message = "translation locale keys must not be empty.",
                        fieldPath = translationsPath,
                    )
                }
                key to requireStringValue(value, ewoChildPath(translationsPath, key))
            }
        } ?: emptyMap()
        return ParsedEwoLocalizedText(
            defaultText = requireStringField(json, "default", path),
            translations = translations,
        )
    }

    private fun parseSegment(element: JsonElement, path: String): ParsedEwoSegment {
        val json = requireObject(element, path)
        val type = requireStringField(json, "type", path)
        return when (type) {
            "steady" -> {
                requireOnlyKeys(
                    json,
                    setOf("id", "type", "label", "note", "duration_sec", "target", "messages", "cadence"),
                    path,
                )
                ParsedEwoSegment.Steady(
                    id = requireSegmentId(json, path),
                    label = optionalStringField(json, "label", path),
                    note = optionalStringField(json, "note", path),
                    messages = json["messages"]?.let {
                        parseMessages(it, ewoChildPath(path, "messages"))
                    } ?: emptyList(),
                    durationSec = requireIntField(json, "duration_sec", path),
                    target = parseTarget(
                        element = requireField(json, "target", path),
                        path = ewoChildPath(path, "target"),
                    ),
                    cadence = json["cadence"]?.let {
                        parseCadence(it, ewoChildPath(path, "cadence"))
                    },
                )
            }

            "ramp" -> {
                requireOnlyKeys(
                    json,
                    setOf(
                        "id",
                        "type",
                        "label",
                        "note",
                        "duration_sec",
                        "from_target",
                        "to_target",
                        "messages",
                        "cadence",
                    ),
                    path,
                )
                val fromTarget = parseRampEndpointTarget(
                    element = requireField(json, "from_target", path),
                    path = ewoChildPath(path, "from_target"),
                )
                val toTarget = parseRampEndpointTarget(
                    element = requireField(json, "to_target", path),
                    path = ewoChildPath(path, "to_target"),
                )
                if (fromTarget::class != toTarget::class) {
                    failEwoValidation(
                        code = EwoWorkoutValidationErrorCode.RAMP_MIXED_TARGET_METRICS,
                        message = "Ramp from_target and to_target must use the same metric.",
                        fieldPath = ewoChildPath(path, "to_target"),
                    )
                }
                ParsedEwoSegment.Ramp(
                    id = requireSegmentId(json, path),
                    label = optionalStringField(json, "label", path),
                    note = optionalStringField(json, "note", path),
                    messages = json["messages"]?.let {
                        parseMessages(it, ewoChildPath(path, "messages"))
                    } ?: emptyList(),
                    durationSec = requireIntField(json, "duration_sec", path),
                    fromTarget = fromTarget,
                    toTarget = toTarget,
                    cadence = json["cadence"]?.let {
                        parseCadence(it, ewoChildPath(path, "cadence"))
                    },
                )
            }

            "free_ride" -> {
                requireOnlyKeys(
                    json,
                    setOf("id", "type", "label", "note", "duration_sec", "messages", "cadence"),
                    path,
                )
                ParsedEwoSegment.FreeRide(
                    id = requireSegmentId(json, path),
                    label = optionalStringField(json, "label", path),
                    note = optionalStringField(json, "note", path),
                    messages = json["messages"]?.let {
                        parseMessages(it, ewoChildPath(path, "messages"))
                    } ?: emptyList(),
                    durationSec = requireIntField(json, "duration_sec", path),
                    cadence = json["cadence"]?.let {
                        parseCadence(it, ewoChildPath(path, "cadence"))
                    },
                )
            }

            "repeat" -> {
                requireOnlyKeys(
                    json,
                    setOf("id", "type", "label", "note", "count", "segments", "messages"),
                    path,
                )
                ParsedEwoSegment.Repeat(
                    id = requireSegmentId(json, path),
                    label = optionalStringField(json, "label", path),
                    note = optionalStringField(json, "note", path),
                    messages = json["messages"]?.let {
                        parseMessages(it, ewoChildPath(path, "messages"))
                    } ?: emptyList(),
                    count = requireIntField(json, "count", path),
                    segments = requireArrayField(json, "segments", path).mapIndexed { index, child ->
                        parseSegment(
                            child,
                            ewoIndexPath(ewoChildPath(path, "segments"), index),
                        )
                    },
                )
            }

            else -> failEwoValidation(
                code = EwoWorkoutValidationErrorCode.UNKNOWN_SEGMENT_TYPE,
                message = "Unsupported segment type '$type'.",
                fieldPath = ewoChildPath(path, "type"),
            )
        }
    }

    private fun parseTarget(element: JsonElement, path: String): ParsedEwoTarget {
        val json = requireObject(element, path)
        val metric = requireStringField(json, "metric", path)
        return when (metric) {
            "power" -> {
                requireOnlyKeys(json, setOf("metric", "value"), path)
                ParsedEwoTarget.Power(
                    value = requireIntField(json, "value", path),
                )
            }
            "ftp_percent" -> {
                requireOnlyKeys(json, setOf("metric", "value"), path)
                val fraction = requireDoubleField(json, "value", path)
                if (fraction < FTP_PERCENT_MIN || fraction > FTP_PERCENT_MAX) {
                    failEwoValidation(
                        code = EwoWorkoutValidationErrorCode.INVALID_FTP_PERCENT_VALUE,
                        message = "ftp_percent value must be a fraction in the range " +
                            "$FTP_PERCENT_MIN..$FTP_PERCENT_MAX " +
                            "(e.g. 0.90 for 90% FTP).",
                        fieldPath = ewoChildPath(path, "value"),
                    )
                }
                ParsedEwoTarget.FtpPercent(fraction = fraction)
            }
            "heart_rate" -> {
                requireOnlyKeys(json, setOf("metric", "range"), path)
                val rangePath = ewoChildPath(path, "range")
                val range = requireObject(requireField(json, "range", path), rangePath)
                requireOnlyKeys(range, setOf("low", "high"), rangePath)
                ParsedEwoTarget.HeartRateRange(
                    low = requireIntField(range, "low", rangePath),
                    high = requireIntField(range, "high", rangePath),
                )
            }
            "heart_rate_relative" -> {
                requireOnlyKeys(json, setOf("metric", "reference", "range"), path)
                val reference = requireStringField(json, "reference", path)
                val rangePath = ewoChildPath(path, "range")
                val range = requireObject(requireField(json, "range", path), rangePath)
                requireOnlyKeys(range, setOf("low", "high"), rangePath)
                ParsedEwoTarget.HeartRateRelativeRange(
                    reference = reference,
                    low = requireDoubleField(range, "low", rangePath),
                    high = requireDoubleField(range, "high", rangePath),
                )
            }

            else -> failEwoValidation(
                code = EwoWorkoutValidationErrorCode.INVALID_TARGET_METRIC,
                message = "Target metric must be 'power', 'ftp_percent', 'heart_rate', or 'heart_rate_relative'.",
                fieldPath = ewoChildPath(path, "metric"),
            )
        }
    }

    /**
     * Parses a ramp endpoint target: must be `power` or `ftp_percent`, and both endpoints
     * of the same ramp must use the same metric (checked by the caller after parsing both).
     */
    private fun parseRampEndpointTarget(element: JsonElement, path: String): ParsedEwoTarget {
        val json = requireObject(element, path)
        val metric = requireStringField(json, "metric", path)
        return when (metric) {
            "power" -> {
                requireOnlyKeys(json, setOf("metric", "value"), path)
                ParsedEwoTarget.Power(
                    value = requireIntField(json, "value", path),
                )
            }
            "ftp_percent" -> {
                requireOnlyKeys(json, setOf("metric", "value"), path)
                val fraction = requireDoubleField(json, "value", path)
                if (fraction < FTP_PERCENT_MIN || fraction > FTP_PERCENT_MAX) {
                    failEwoValidation(
                        code = EwoWorkoutValidationErrorCode.INVALID_FTP_PERCENT_VALUE,
                        message = "ftp_percent value must be a fraction in the range " +
                            "$FTP_PERCENT_MIN..$FTP_PERCENT_MAX " +
                            "(e.g. 0.90 for 90% FTP).",
                        fieldPath = ewoChildPath(path, "value"),
                    )
                }
                ParsedEwoTarget.FtpPercent(fraction = fraction)
            }
            else -> failEwoValidation(
                code = EwoWorkoutValidationErrorCode.RAMP_INVALID_TARGET_METRIC,
                message = "Ramp targets must use 'power' or 'ftp_percent'.",
                fieldPath = ewoChildPath(path, "metric"),
            )
        }
    }

    private fun parseDifficulty(element: JsonElement, path: String): String {
        val value = requireStringValue(element, path)
        if (value !in allowedDifficulties) {
            failEwoValidation(
                code = EwoWorkoutValidationErrorCode.INVALID_DIFFICULTY,
                message = "difficulty must be one of: ${allowedDifficulties.sorted().joinToString()}.",
                fieldPath = path,
            )
        }
        return value
    }

    private fun parseTags(element: JsonElement, path: String): List<String> {
        val array = requireArray(element, path)
        if (array.size > EwoWorkoutTagPolicy.maxCount) {
            failEwoValidation(
                code = EwoWorkoutValidationErrorCode.TOO_MANY_TAGS,
                message = "tags must contain at most ${EwoWorkoutTagPolicy.maxCount} items.",
                fieldPath = path,
            )
        }
        return array.mapIndexed { index, tagElement ->
            val tag = requireStringValue(tagElement, ewoIndexPath(path, index))
            if (!EwoWorkoutTagPolicy.isValid(tag)) {
                failEwoValidation(
                    code = EwoWorkoutValidationErrorCode.INVALID_TAG,
                    message = "Tag '$tag' must match ${EwoWorkoutTagPolicy.pattern}.",
                    fieldPath = ewoIndexPath(path, index),
                )
            }
            tag
        }
    }

    private fun parseCadence(element: JsonElement, path: String): ParsedEwoCadenceRange {
        val json = requireObject(element, path)
        requireOnlyKeys(json, setOf("low", "high"), path)
        return ParsedEwoCadenceRange(
            low = requireIntField(json, "low", path),
            high = requireIntField(json, "high", path),
        )
    }

    private fun requireSegmentId(json: JsonObject, parentPath: String): String {
        val path = ewoChildPath(parentPath, "id")
        val id = requireStringField(json, "id", parentPath)
        if (!Regex(segmentIdPattern).matches(id)) {
            failEwoValidation(
                code = EwoWorkoutValidationErrorCode.INVALID_SEGMENT_ID,
                message = "Segment id must match ^[a-z][a-z0-9_-]{0,63}$.",
                fieldPath = path,
            )
        }
        return id
    }

    private fun requireOnlyKeys(json: JsonObject, allowedKeys: Set<String>, path: String) {
        json.keys.sorted().firstOrNull { it !in allowedKeys }?.let { unknownKey ->
            failEwoValidation(
                code = EwoWorkoutValidationErrorCode.UNKNOWN_FIELD,
                message = "Unknown field '$unknownKey'.",
                fieldPath = ewoChildPath(path, unknownKey),
            )
        }
    }

    private fun requireField(json: JsonObject, fieldName: String, parentPath: String): JsonElement {
        return json[fieldName] ?: failEwoValidation(
            code = EwoWorkoutValidationErrorCode.MISSING_REQUIRED_FIELD,
            message = "Missing required field '$fieldName'.",
            fieldPath = ewoChildPath(parentPath, fieldName),
        )
    }

    private fun requireStringField(json: JsonObject, fieldName: String, parentPath: String): String {
        return requireStringValue(
            element = requireField(json, fieldName, parentPath),
            path = ewoChildPath(parentPath, fieldName),
        )
    }

    private fun optionalStringField(json: JsonObject, fieldName: String, parentPath: String): String? {
        val element = json[fieldName] ?: return null
        return requireStringValue(element, ewoChildPath(parentPath, fieldName))
    }

    private fun requireIntField(json: JsonObject, fieldName: String, parentPath: String): Int {
        return requireIntValue(
            element = requireField(json, fieldName, parentPath),
            path = ewoChildPath(parentPath, fieldName),
        )
    }

    private fun requireArrayField(json: JsonObject, fieldName: String, parentPath: String): JsonArray {
        return requireArray(
            element = requireField(json, fieldName, parentPath),
            path = ewoChildPath(parentPath, fieldName),
        )
    }

    private fun requireStringValue(element: JsonElement, path: String): String {
        val primitive = element as? JsonPrimitive ?: failEwoValidation(
            code = EwoWorkoutValidationErrorCode.INVALID_TYPE,
            message = "Expected a string.",
            fieldPath = path,
        )
        if (!primitive.isString) {
            failEwoValidation(
                code = EwoWorkoutValidationErrorCode.INVALID_TYPE,
                message = "Expected a string.",
                fieldPath = path,
            )
        }
        return primitive.content
    }

    private fun requireIntValue(element: JsonElement, path: String): Int {
        val primitive = element as? JsonPrimitive ?: failEwoValidation(
            code = EwoWorkoutValidationErrorCode.INVALID_TYPE,
            message = "Expected an integer.",
            fieldPath = path,
        )
        if (primitive.isString) {
            failEwoValidation(
                code = EwoWorkoutValidationErrorCode.INVALID_TYPE,
                message = "Expected an integer.",
                fieldPath = path,
            )
        }
        return primitive.content.toIntOrNull() ?: failEwoValidation(
            code = EwoWorkoutValidationErrorCode.INVALID_TYPE,
            message = "Expected an integer.",
            fieldPath = path,
        )
    }

    private fun requireDoubleField(json: JsonObject, fieldName: String, parentPath: String): Double {
        return requireDoubleValue(
            element = requireField(json, fieldName, parentPath),
            path = ewoChildPath(parentPath, fieldName),
        )
    }

    private fun requireDoubleValue(element: JsonElement, path: String): Double {
        val primitive = element as? JsonPrimitive ?: failEwoValidation(
            code = EwoWorkoutValidationErrorCode.INVALID_TYPE,
            message = "Expected a number.",
            fieldPath = path,
        )
        if (primitive.isString) {
            failEwoValidation(
                code = EwoWorkoutValidationErrorCode.INVALID_TYPE,
                message = "Expected a number.",
                fieldPath = path,
            )
        }
        return primitive.content.toDoubleOrNull() ?: failEwoValidation(
            code = EwoWorkoutValidationErrorCode.INVALID_TYPE,
            message = "Expected a number.",
            fieldPath = path,
        )
    }

    private fun requireObject(element: JsonElement, path: String): JsonObject {
        return element as? JsonObject ?: failEwoValidation(
            code = EwoWorkoutValidationErrorCode.INVALID_TYPE,
            message = "Expected an object.",
            fieldPath = path,
        )
    }

    private fun requireArray(element: JsonElement, path: String): JsonArray {
        return element as? JsonArray ?: failEwoValidation(
            code = EwoWorkoutValidationErrorCode.INVALID_TYPE,
            message = "Expected an array.",
            fieldPath = path,
        )
    }
}

package com.example.ergometerapp.workout

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParserFactory
import java.io.IOException
import java.io.StringReader
import kotlin.math.roundToInt

/**
 * Minimal, tolerant parser for Zwift `.zwo` files.
 *
 * Parsing errors throw [ZwoParseException] so callers can distinguish malformed XML from valid but
 * partially-known content.
 *
 * Tag handling rules:
 * - Tag values are de-duplicated.
 * - If a `<tag>` element has a `name` attribute, that value is used and any text content is ignored.
 * - If there is no `name` attribute, trimmed text content is used.
 * - Tags are only collected when inside a `<tags>` section.
 *
 * Unknown step handling:
 * - Inside `<workout>`, unknown tags are only recorded when they look like steps by having a
 *   `Duration` or `OnDuration` attribute, and the tag name starts with an uppercase letter.
 *
 * Example XML for manual testing:
 * ```
 * <workout_file>
 *   <name>Short Test</name>
 *   <description>Quick warmup and steady work.</description>
 *   <author>Trainer</author>
 *   <tags>
 *     <tag name="test"/>
 *     <tag>short</tag>
 *   </tags>
 *   <workout>
 *     <Warmup Duration="300" PowerLow="0.5" PowerHigh="0.75" Cadence="85"/>
 *     <SteadyState Duration="600" Power="0.8"/>
 *     <Cooldown Duration="300" PowerLow="0.6" PowerHigh="0.4"/>
 *   </workout>
 * </workout_file>
 * ```
 */
fun parseZwo(xml: String): WorkoutFile {
    val parser = XmlPullParserFactory.newInstance().newPullParser()
    parser.setInput(StringReader(xml))

    var name: String? = null
    var description: String? = null
    var author: String? = null
    var targetHeartRateBpm: Int? = null
    var maxHeartRateBpm: Int? = null
    val tags = LinkedHashSet<String>()
    val steps = mutableListOf<Step>()
    val textEvents = mutableListOf<WorkoutTextEvent>()

    var currentTextTag: String? = null
    var currentText = StringBuilder()
    var inWorkout = false
    var inTags = false
    var pendingTextEventOffsetSec: Int? = null
    var pendingTextEventDurationSec: Int? = null

    fun String.toRoundedIntOrNull(): Int? = trim().toDoubleOrNull()?.roundToInt()

    /**
     * Normalizes known workout-level metadata tags so parser tolerance stays focused on spelling
     * variants seen in real-world files without broadening support to unrelated fields.
     */
    fun canonicalWorkoutMetadataTagName(tagName: String): String? =
        when (tagName.filter { it.isLetterOrDigit() }.lowercase()) {
            "targetheartrate" -> "TargetHeartrate"
            "maxheartrate" -> "MaxHeartrate"
            else -> null
        }

    fun flushTextIfNeeded(endTag: String) {
        val text = currentText.toString().trim()
        if (text.isEmpty()) return
        when (canonicalWorkoutMetadataTagName(endTag) ?: endTag) {
            "name" -> name = text
            "description" -> description = text
            "author" -> author = text
            "TargetHeartrate" -> targetHeartRateBpm = text.toRoundedIntOrNull()
            "MaxHeartrate" -> maxHeartRateBpm = text.toRoundedIntOrNull()
            "tag" -> tags.add(text)
            "textevent", "TextEvent" -> {
                val offsetSec = pendingTextEventOffsetSec
                if (offsetSec != null) {
                    textEvents.add(
                        WorkoutTextEvent(
                            timeOffsetSec = offsetSec.coerceAtLeast(0),
                            message = text,
                            durationSec = pendingTextEventDurationSec?.takeIf { it > 0 },
                        ),
                    )
                }
            }
        }
    }

    fun attrInt(tag: XmlPullParser, name: String): Int? =
        tag.getAttributeValue(null, name)?.toIntOrNull()

    fun attrDouble(tag: XmlPullParser, name: String): Double? =
        tag.getAttributeValue(null, name)?.toDoubleOrNull()

    fun attrStringAny(tag: XmlPullParser, vararg names: String): String? {
        names.forEach { name ->
            val value = tag.getAttributeValue(null, name)
            if (!value.isNullOrBlank()) return value
        }
        return null
    }

    fun attrSecondsAny(tag: XmlPullParser, vararg names: String): Int? {
        names.forEach { name ->
            val raw = tag.getAttributeValue(null, name)?.trim().orEmpty()
            if (raw.isEmpty()) return@forEach
            val parsed = raw.toDoubleOrNull()?.roundToInt()
            if (parsed != null) return parsed
        }
        return null
    }

    fun attrDoubleAny(tag: XmlPullParser, vararg names: String): Double? {
        names.forEach { name ->
            val value = attrDouble(tag, name)
            if (value != null) return value
        }
        return null
    }

    fun midpointOrSingle(low: Double?, high: Double?): Double? {
        if (low != null && high != null) return (low + high) / 2.0
        return low ?: high
    }

    fun attrStringMap(tag: XmlPullParser): Map<String, String> {
        if (tag.attributeCount == 0) return emptyMap()
        val map = LinkedHashMap<String, String>(tag.attributeCount)
        for (i in 0 until tag.attributeCount) {
            val key = tag.getAttributeName(i)
            val value = tag.getAttributeValue(i)
            if (key != null && value != null) {
                map[key] = value
            }
        }
        return map
    }

    try {
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    val tagName = parser.name ?: ""
                    if (tagName == "workout") {
                        inWorkout = true
                    }
                    if (tagName == "tags") {
                        inTags = true
                    }

                    when (tagName) {
                        "name", "description", "author" -> {
                            currentTextTag = tagName
                            currentText = StringBuilder()
                        }
                        else -> if (canonicalWorkoutMetadataTagName(tagName) != null) {
                            currentTextTag = tagName
                            currentText = StringBuilder()
                        }
                    }

                    when (tagName) {
                        "tag" -> if (inTags) {
                            val attrName = parser.getAttributeValue(null, "name")
                            if (!attrName.isNullOrBlank()) {
                                tags.add(attrName)
                            } else {
                                currentTextTag = tagName
                                currentText = StringBuilder()
                            }
                        }
                        "Warmup" -> if (inWorkout) {
                            val power = attrDouble(parser, "Power")
                            steps.add(
                                Step.Warmup(
                                    durationSec = attrInt(parser, "Duration"),
                                    powerLow = attrDouble(parser, "PowerLow") ?: power,
                                    powerHigh = attrDouble(parser, "PowerHigh") ?: power,
                                    cadence = attrInt(parser, "Cadence"),
                                ),
                            )
                        }
                        "Cooldown" -> if (inWorkout) {
                            val power = attrDouble(parser, "Power")
                            steps.add(
                                Step.Cooldown(
                                    durationSec = attrInt(parser, "Duration"),
                                    powerLow = attrDouble(parser, "PowerLow") ?: power,
                                    powerHigh = attrDouble(parser, "PowerHigh") ?: power,
                                    cadence = attrInt(parser, "Cadence"),
                                ),
                            )
                        }
                        "SteadyState", "SolidState" -> if (inWorkout) {
                            val power = attrDouble(parser, "Power")
                            val powerFromRange = midpointOrSingle(
                                low = attrDouble(parser, "PowerLow"),
                                high = attrDouble(parser, "PowerHigh"),
                            )
                            steps.add(
                                Step.SteadyState(
                                    durationSec = attrInt(parser, "Duration"),
                                    power = power ?: powerFromRange,
                                    cadence = attrInt(parser, "Cadence"),
                                ),
                            )
                        }
                        "Ramp" -> if (inWorkout) {
                            val power = attrDouble(parser, "Power")
                            steps.add(
                                Step.Ramp(
                                    durationSec = attrInt(parser, "Duration"),
                                    powerLow = attrDouble(parser, "PowerLow") ?: power,
                                    powerHigh = attrDouble(parser, "PowerHigh") ?: power,
                                    cadence = attrInt(parser, "Cadence"),
                                ),
                            )
                        }
                        "IntervalsT" -> if (inWorkout) {
                            val onPower = attrDoubleAny(parser, "OnPower")
                                ?: midpointOrSingle(
                                    low = attrDouble(parser, "PowerOnLow"),
                                    high = attrDouble(parser, "PowerOnHigh"),
                                )
                            val offPower = attrDoubleAny(parser, "OffPower")
                                ?: midpointOrSingle(
                                    low = attrDouble(parser, "PowerOffLow"),
                                    high = attrDouble(parser, "PowerOffHigh"),
                                )
                            steps.add(
                                Step.IntervalsT(
                                    onDurationSec = attrInt(parser, "OnDuration"),
                                    offDurationSec = attrInt(parser, "OffDuration"),
                                    onPower = onPower,
                                    offPower = offPower,
                                    repeat = attrInt(parser, "Repeat"),
                                    cadence = attrInt(parser, "Cadence"),
                                ),
                            )
                        }
                        "FreeRide", "Freeride", "MaxEffort" -> if (inWorkout) {
                            steps.add(
                                Step.FreeRide(
                                    durationSec = attrInt(parser, "Duration"),
                                    cadence = attrInt(parser, "Cadence"),
                                ),
                            )
                        }
                        "textevent", "TextEvent" -> if (inWorkout) {
                            val message = attrStringAny(
                                parser,
                                "message",
                                "Message",
                                "text",
                                "Text",
                            )?.trim().orEmpty()
                            val timeOffsetSec = attrSecondsAny(
                                parser,
                                "timeoffset",
                                "TimeOffset",
                                "timeOffset",
                            )
                            val durationSec = attrSecondsAny(
                                parser,
                                "duration",
                                "Duration",
                            )
                            if (timeOffsetSec != null) {
                                if (message.isNotEmpty()) {
                                    textEvents.add(
                                        WorkoutTextEvent(
                                            timeOffsetSec = timeOffsetSec.coerceAtLeast(0),
                                            message = message,
                                            durationSec = durationSec?.takeIf { it > 0 },
                                        ),
                                    )
                                } else {
                                    // Some ZWO files carry the message in text content.
                                    pendingTextEventOffsetSec = timeOffsetSec.coerceAtLeast(0)
                                    pendingTextEventDurationSec = durationSec
                                    currentTextTag = tagName
                                    currentText = StringBuilder()
                                }
                            }
                        }
                        else -> if (inWorkout) {
                            val looksLikeStep = parser.getAttributeValue(null, "Duration") != null ||
                                parser.getAttributeValue(null, "OnDuration") != null
                            val startsUppercase = tagName.firstOrNull()?.isUpperCase() == true
                            if (looksLikeStep && startsUppercase) {
                                // Preserve unknown step tags to keep forward compatibility with new ZWO variants.
                                steps.add(
                                    Step.Unknown(
                                        tagName = tagName,
                                        attributes = attrStringMap(parser),
                                    ),
                                )
                            }
                        }
                    }
                }
                XmlPullParser.TEXT -> {
                    if (currentTextTag != null) {
                        currentText.append(parser.text)
                    }
                }
                XmlPullParser.END_TAG -> {
                    val tagName = parser.name ?: ""
                    if (tagName == "workout") {
                        inWorkout = false
                    }
                    if (tagName == "tags") {
                        inTags = false
                    }
                    if (currentTextTag == tagName) {
                        flushTextIfNeeded(tagName)
                        currentTextTag = null
                        currentText = StringBuilder()
                    }
                    if (tagName.equals("textevent", ignoreCase = true)) {
                        pendingTextEventOffsetSec = null
                        pendingTextEventDurationSec = null
                    }
                }
            }
            parser.next()
        }
    } catch (e: Exception) {
        if (e is XmlPullParserException || e is IOException || e is IllegalArgumentException) {
            val location = "line ${parser.lineNumber}, column ${parser.columnNumber}"
            val parserReason = e.message
                ?.lineSequence()
                ?.firstOrNull()
                ?.trim()
                ?.takeIf { it.isNotBlank() }
            val message = buildString {
                append("Malformed ZWO XML at ")
                append(location)
                parserReason?.let {
                    append(". ")
                    append(it)
                }
            }
            throw ZwoParseException(message, e)
        }
        throw e
    }

    return WorkoutFile(
        name = name,
        description = description,
        author = author,
        tags = tags.toList(),
        steps = steps.toList(),
        textEvents = textEvents
            .asSequence()
            .sortedBy { it.timeOffsetSec }
            .toList(),
        heartRateTargets = WorkoutHeartRateTargets(
            targetHeartRateBpm = targetHeartRateBpm,
            maxHeartRateBpm = maxHeartRateBpm,
        ).takeIf {
            it.targetHeartRateBpm != null || it.maxHeartRateBpm != null
        },
    )
}

class ZwoParseException(message: String, cause: Throwable) : Exception(message, cause)

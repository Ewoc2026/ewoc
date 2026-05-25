package com.ewo.core.zwo

import com.ewo.core.ParsedEwoCadenceRange
import com.ewo.core.ParsedEwoSegment
import com.ewo.core.ParsedEwoTarget
import com.ewo.core.ParsedEwoWorkoutFile

/**
 * Imports a Zwift `.zwo` XML payload and converts it to a canonical EWO workout.
 *
 * Mapping rules:
 * - Warmup → EWO Ramp (from PowerLow to PowerHigh)
 * - Cooldown → EWO Ramp (from PowerHigh to PowerLow)
 * - SteadyState / SolidState → EWO Steady
 * - Ramp → EWO Ramp
 * - IntervalsT → EWO Repeat with Steady on/off children
 * - FreeRide / Freeride → EWO FreeRide
 * - MaxEffort → EWO FreeRide (with warning)
 *
 * ZWO power values (FTP fractions like 0.75 = 75% FTP) map to EWO `ftp_percent` targets.
 * Unsupported or partially supported constructs produce warnings rather than guessed behavior.
 */
fun importZwo(xml: String): ZwoImportResult {
    val events: List<XmlEvent>
    try {
        events = MiniXmlTokenizer(xml).tokenize()
    } catch (e: XmlParseException) {
        return ZwoImportResult.Failure("Malformed XML: ${e.message}")
    }

    return ZwoConverter(events).convert()
}

// ---------------------------------------------------------------------------
// Internal conversion logic
// ---------------------------------------------------------------------------

private class ZwoConverter(private val events: List<XmlEvent>) {
    private val warnings = mutableListOf<ZwoImportWarning>()
    private var segmentCounter = 0

    fun convert(): ZwoImportResult {
        var name: String? = null
        var description: String? = null
        var author: String? = null
        val tags = mutableListOf<String>()
        val segments = mutableListOf<ParsedEwoSegment>()
        var hasTextEvents = false
        var hasHrTargets = false

        var inWorkout = false
        var inTags = false
        var currentTextTag: String? = null
        val textBuffer = StringBuilder()

        var i = 0
        while (i < events.size) {
            when (val event = events[i]) {
                is XmlEvent.StartElement -> {
                    val tag = event.name
                    if (tag == "workout") inWorkout = true
                    if (tag == "tags") inTags = true

                    when {
                        tag == "name" || tag == "description" || tag == "author" -> {
                            currentTextTag = tag
                            textBuffer.clear()
                        }
                        tag == "tag" && inTags -> {
                            val attrName = event.attributes["name"]
                            if (!attrName.isNullOrBlank()) {
                                tags.add(attrName.trim())
                            } else {
                                currentTextTag = "tag"
                                textBuffer.clear()
                            }
                        }
                        inWorkout && tag.firstOrNull()?.isUpperCase() == true -> {
                            convertStep(tag, event.attributes)?.let { segments.add(it) }
                        }
                        inWorkout && (tag.equals("textevent", ignoreCase = true) ||
                            tag.equals("TextNotification", ignoreCase = true)) -> {
                            hasTextEvents = true
                        }
                        isHrMetadataTag(tag) -> {
                            hasHrTargets = true
                        }
                    }
                }
                is XmlEvent.Text -> {
                    if (currentTextTag != null) {
                        textBuffer.append(event.content)
                    }
                }
                is XmlEvent.EndElement -> {
                    val tag = event.name
                    if (tag == "workout") inWorkout = false
                    if (tag == "tags") inTags = false

                    if (currentTextTag == tag) {
                        val text = textBuffer.toString().trim()
                        if (text.isNotEmpty()) {
                            when (tag) {
                                "name" -> name = text
                                "description" -> description = text
                                "author" -> if (description == null) {
                                    // Author preserved in description since EWO has no author field
                                } else {
                                    // Append author info
                                }
                                "tag" -> tags.add(text)
                            }
                            // Handle author: append to description if present
                            if (tag == "author") {
                                val authorText = text
                                description = when {
                                    description != null -> "$description (Author: $authorText)"
                                    else -> "Author: $authorText"
                                }
                            }
                        }
                        currentTextTag = null
                        textBuffer.clear()
                    }
                }
            }
            i++
        }

        if (hasTextEvents) {
            warnings.add(
                ZwoImportWarning(
                    ZwoImportWarningCode.TEXT_EVENTS_NOT_IMPORTED,
                    "ZWO text events were present but are not imported in this version.",
                ),
            )
        }
        if (hasHrTargets) {
            warnings.add(
                ZwoImportWarning(
                    ZwoImportWarningCode.HEART_RATE_TARGETS_NOT_IMPORTED,
                    "ZWO heart-rate target metadata was present but is not imported.",
                ),
            )
        }

        if (segments.isEmpty()) {
            return ZwoImportResult.Failure(
                "No valid segments could be converted from the ZWO file.",
            )
        }

        val title = name?.takeIf { it.isNotBlank() } ?: "Imported ZWO Workout"

        val workout = ParsedEwoWorkoutFile(
            format = "ewo",
            version = "1.5",
            uid = null,
            revision = null,
            title = title,
            description = description,
            titleLocalized = null,
            descriptionLocalized = null,
            difficulty = null,
            tags = tags.distinct(),
            control = null,
            messages = emptyList(),
            segments = segments,
        )

        return ZwoImportResult.Success(
            workout = workout,
            warnings = warnings.toList(),
        )
    }

    private fun isHrMetadataTag(tag: String): Boolean {
        val normalized = tag.filter { it.isLetterOrDigit() }.lowercase()
        return normalized == "targetheartrate" || normalized == "maxheartrate"
    }

    private fun convertStep(tag: String, attrs: Map<String, String>): ParsedEwoSegment? {
        return when (tag) {
            "Warmup" -> convertWarmup(attrs)
            "Cooldown" -> convertCooldown(attrs)
            "SteadyState", "SolidState" -> convertSteadyState(attrs)
            "Ramp" -> convertRamp(attrs)
            "IntervalsT" -> convertIntervalsT(attrs)
            "FreeRide", "Freeride" -> convertFreeRide(attrs)
            "MaxEffort" -> convertMaxEffort(attrs)
            else -> {
                warnings.add(
                    ZwoImportWarning(
                        ZwoImportWarningCode.UNSUPPORTED_STEP_TYPE,
                        "Unsupported ZWO step type '$tag' was skipped.",
                    ),
                )
                null
            }
        }
    }

    /** Warmup: ramp from PowerLow to PowerHigh (ascending). */
    private fun convertWarmup(attrs: Map<String, String>): ParsedEwoSegment? {
        val durationSec = attrInt(attrs, "Duration")
        if (durationSec == null || durationSec <= 0) {
            warnMissingDuration("Warmup")
            return null
        }
        val power = attrDouble(attrs, "Power")
        val powerLow = attrDouble(attrs, "PowerLow") ?: power
        val powerHigh = attrDouble(attrs, "PowerHigh") ?: power
        if (powerLow == null || powerHigh == null) {
            warnMissingPower("Warmup")
            return null
        }
        return ParsedEwoSegment.Ramp(
            id = nextId(),
            label = "Warmup",
            note = null,
            messages = emptyList(),
            durationSec = durationSec,
            fromTarget = ParsedEwoTarget.FtpPercent(powerLow),
            toTarget = ParsedEwoTarget.FtpPercent(powerHigh),
            cadence = attrCadence(attrs),
        )
    }

    /** Cooldown: ramp from PowerHigh down to PowerLow (descending). */
    private fun convertCooldown(attrs: Map<String, String>): ParsedEwoSegment? {
        val durationSec = attrInt(attrs, "Duration")
        if (durationSec == null || durationSec <= 0) {
            warnMissingDuration("Cooldown")
            return null
        }
        val power = attrDouble(attrs, "Power")
        val powerLow = attrDouble(attrs, "PowerLow") ?: power
        val powerHigh = attrDouble(attrs, "PowerHigh") ?: power
        if (powerLow == null || powerHigh == null) {
            warnMissingPower("Cooldown")
            return null
        }
        // Cooldown goes from high to low
        return ParsedEwoSegment.Ramp(
            id = nextId(),
            label = "Cooldown",
            note = null,
            messages = emptyList(),
            durationSec = durationSec,
            fromTarget = ParsedEwoTarget.FtpPercent(powerHigh),
            toTarget = ParsedEwoTarget.FtpPercent(powerLow),
            cadence = attrCadence(attrs),
        )
    }

    /** SteadyState / SolidState: constant power. */
    private fun convertSteadyState(attrs: Map<String, String>): ParsedEwoSegment? {
        val durationSec = attrInt(attrs, "Duration")
        if (durationSec == null || durationSec <= 0) {
            warnMissingDuration("SteadyState")
            return null
        }
        val power = attrDouble(attrs, "Power")
        val powerFromRange = midpoint(
            attrDouble(attrs, "PowerLow"),
            attrDouble(attrs, "PowerHigh"),
        )
        val effectivePower = power ?: powerFromRange
        if (effectivePower == null) {
            warnMissingPower("SteadyState")
            return null
        }
        return ParsedEwoSegment.Steady(
            id = nextId(),
            label = null,
            note = null,
            messages = emptyList(),
            durationSec = durationSec,
            target = ParsedEwoTarget.FtpPercent(effectivePower),
            cadence = attrCadence(attrs),
        )
    }

    /** Ramp: linear power change. */
    private fun convertRamp(attrs: Map<String, String>): ParsedEwoSegment? {
        val durationSec = attrInt(attrs, "Duration")
        if (durationSec == null || durationSec <= 0) {
            warnMissingDuration("Ramp")
            return null
        }
        val power = attrDouble(attrs, "Power")
        val powerLow = attrDouble(attrs, "PowerLow") ?: power
        val powerHigh = attrDouble(attrs, "PowerHigh") ?: power
        if (powerLow == null || powerHigh == null) {
            warnMissingPower("Ramp")
            return null
        }
        return ParsedEwoSegment.Ramp(
            id = nextId(),
            label = null,
            note = null,
            messages = emptyList(),
            durationSec = durationSec,
            fromTarget = ParsedEwoTarget.FtpPercent(powerLow),
            toTarget = ParsedEwoTarget.FtpPercent(powerHigh),
            cadence = attrCadence(attrs),
        )
    }

    /** IntervalsT: structured on/off repeats. */
    private fun convertIntervalsT(attrs: Map<String, String>): ParsedEwoSegment? {
        val repeat = attrInt(attrs, "Repeat")
        val onDuration = attrInt(attrs, "OnDuration")
        val offDuration = attrInt(attrs, "OffDuration")
        val onPower = attrDouble(attrs, "OnPower")
            ?: midpoint(attrDouble(attrs, "PowerOnLow"), attrDouble(attrs, "PowerOnHigh"))
        val offPower = attrDouble(attrs, "OffPower")
            ?: midpoint(attrDouble(attrs, "PowerOffLow"), attrDouble(attrs, "PowerOffHigh"))

        if (repeat == null || repeat <= 0 ||
            onDuration == null || onDuration <= 0 ||
            offDuration == null || offDuration <= 0 ||
            onPower == null || offPower == null
        ) {
            warnings.add(
                ZwoImportWarning(
                    ZwoImportWarningCode.INCOMPLETE_INTERVALS,
                    "IntervalsT block is missing required attributes (Repeat, OnDuration, OffDuration, power) and was skipped.",
                ),
            )
            return null
        }

        val repeatId = nextId()
        val cadence = attrCadence(attrs)

        val onSegment = ParsedEwoSegment.Steady(
            id = "$repeatId-on",
            label = "Work",
            note = null,
            messages = emptyList(),
            durationSec = onDuration,
            target = ParsedEwoTarget.FtpPercent(onPower),
            cadence = cadence,
        )
        val offSegment = ParsedEwoSegment.Steady(
            id = "$repeatId-off",
            label = "Rest",
            note = null,
            messages = emptyList(),
            durationSec = offDuration,
            target = ParsedEwoTarget.FtpPercent(offPower),
            cadence = null, // Rest cadence is typically uncontrolled
        )

        return ParsedEwoSegment.Repeat(
            id = repeatId,
            label = "Intervals",
            note = null,
            messages = emptyList(),
            count = repeat,
            segments = listOf(onSegment, offSegment),
        )
    }

    /** FreeRide: uncontrolled effort. */
    private fun convertFreeRide(attrs: Map<String, String>): ParsedEwoSegment? {
        val durationSec = attrInt(attrs, "Duration")
        if (durationSec == null || durationSec <= 0) {
            warnMissingDuration("FreeRide")
            return null
        }
        return ParsedEwoSegment.FreeRide(
            id = nextId(),
            label = null,
            note = null,
            messages = emptyList(),
            durationSec = durationSec,
            cadence = attrCadence(attrs),
        )
    }

    /** MaxEffort: mapped to FreeRide with a warning. */
    private fun convertMaxEffort(attrs: Map<String, String>): ParsedEwoSegment? {
        val durationSec = attrInt(attrs, "Duration")
        if (durationSec == null || durationSec <= 0) {
            warnMissingDuration("MaxEffort")
            return null
        }
        warnings.add(
            ZwoImportWarning(
                ZwoImportWarningCode.MAX_EFFORT_AS_FREE_RIDE,
                "MaxEffort step was converted to a free-ride segment.",
            ),
        )
        return ParsedEwoSegment.FreeRide(
            id = nextId(),
            label = "Max Effort",
            note = null,
            messages = emptyList(),
            durationSec = durationSec,
            cadence = attrCadence(attrs),
        )
    }

    // --- Helpers ---

    private fun nextId(): String = "seg-${++segmentCounter}"

    private fun attrInt(attrs: Map<String, String>, name: String): Int? =
        attrs[name]?.trim()?.toDoubleOrNull()?.toInt()

    private fun attrDouble(attrs: Map<String, String>, name: String): Double? =
        attrs[name]?.trim()?.toDoubleOrNull()

    private fun attrCadence(attrs: Map<String, String>): ParsedEwoCadenceRange? {
        val cadence = attrInt(attrs, "Cadence") ?: return null
        if (cadence <= 0) return null
        val cadenceLow = attrInt(attrs, "CadenceLow") ?: cadence
        val cadenceHigh = attrInt(attrs, "CadenceHigh") ?: cadence
        return ParsedEwoCadenceRange(low = cadenceLow, high = cadenceHigh)
    }

    private fun midpoint(low: Double?, high: Double?): Double? {
        if (low != null && high != null) return (low + high) / 2.0
        return low ?: high
    }

    private fun warnMissingDuration(stepType: String) {
        warnings.add(
            ZwoImportWarning(
                ZwoImportWarningCode.MISSING_DURATION,
                "$stepType step is missing a valid Duration and was skipped.",
            ),
        )
    }

    private fun warnMissingPower(stepType: String) {
        warnings.add(
            ZwoImportWarning(
                ZwoImportWarningCode.MISSING_POWER_TARGET,
                "$stepType step is missing power target attributes and was skipped.",
            ),
        )
    }

}

// ---------------------------------------------------------------------------
// Minimal KMP-compatible XML tokenizer
// ---------------------------------------------------------------------------

internal sealed class XmlEvent {
    data class StartElement(val name: String, val attributes: Map<String, String>) : XmlEvent()
    data class EndElement(val name: String) : XmlEvent()
    data class Text(val content: String) : XmlEvent()
}

internal class XmlParseException(message: String) : Exception(message)

/**
 * Minimal XML tokenizer for the narrow ZWO subset. Not a general-purpose XML parser.
 *
 * Handles: elements, attributes, text content, self-closing tags, comments,
 * processing instructions, DOCTYPE declarations, and basic character entities.
 */
internal class MiniXmlTokenizer(private val input: String) {
    private var pos = 0
    private val len = input.length

    fun tokenize(): List<XmlEvent> {
        val events = mutableListOf<XmlEvent>()
        while (pos < len) {
            if (input[pos] == '<') {
                handleMarkup(events)
            } else {
                handleText(events)
            }
        }
        return events
    }

    private fun handleMarkup(events: MutableList<XmlEvent>) {
        pos++ // skip '<'
        if (pos >= len) throw XmlParseException("Unexpected end of input after '<'")

        when {
            input.startsWith("!--", pos) -> skipComment()
            input.startsWith("![CDATA[", pos) -> handleCData(events)
            input.startsWith("!DOCTYPE", pos, ignoreCase = true) ||
                input.startsWith("!", pos) -> skipDeclaration()
            input.startsWith("?", pos) -> skipProcessingInstruction()
            input[pos] == '/' -> handleEndElement(events)
            else -> handleStartElement(events)
        }
    }

    private fun handleText(events: MutableList<XmlEvent>) {
        val start = pos
        while (pos < len && input[pos] != '<') pos++
        val raw = input.substring(start, pos)
        val decoded = decodeEntities(raw)
        if (decoded.isNotEmpty()) {
            events.add(XmlEvent.Text(decoded))
        }
    }

    private fun handleStartElement(events: MutableList<XmlEvent>) {
        val name = readName()
        val attrs = readAttributes()
        skipWhitespace()

        if (pos < len && input[pos] == '/') {
            // Self-closing: <Name ... />
            pos++ // skip '/'
            expect('>')
            events.add(XmlEvent.StartElement(name, attrs))
            events.add(XmlEvent.EndElement(name))
        } else {
            expect('>')
            events.add(XmlEvent.StartElement(name, attrs))
        }
    }

    private fun handleEndElement(events: MutableList<XmlEvent>) {
        pos++ // skip '/'
        val name = readName()
        skipWhitespace()
        expect('>')
        events.add(XmlEvent.EndElement(name))
    }

    private fun readName(): String {
        skipWhitespace()
        val start = pos
        while (pos < len && isNameChar(input[pos])) pos++
        if (pos == start) throw XmlParseException("Expected element name at position $pos")
        return input.substring(start, pos)
    }

    private fun readAttributes(): Map<String, String> {
        val attrs = LinkedHashMap<String, String>()
        while (true) {
            skipWhitespace()
            if (pos >= len || input[pos] == '>' || input[pos] == '/') break
            if (!isNameStartChar(input[pos])) break

            val attrName = readName()
            skipWhitespace()
            expect('=')
            skipWhitespace()
            val attrValue = readAttributeValue()
            attrs[attrName] = attrValue
        }
        return attrs
    }

    private fun readAttributeValue(): String {
        if (pos >= len) throw XmlParseException("Expected attribute value at position $pos")
        val quote = input[pos]
        if (quote != '"' && quote != '\'') {
            throw XmlParseException("Expected quote character at position $pos, got '${input[pos]}'")
        }
        pos++ // skip opening quote
        val start = pos
        while (pos < len && input[pos] != quote) pos++
        if (pos >= len) throw XmlParseException("Unterminated attribute value starting at position $start")
        val raw = input.substring(start, pos)
        pos++ // skip closing quote
        return decodeEntities(raw)
    }

    private fun skipComment() {
        pos += 3 // skip "!--"
        val end = input.indexOf("-->", pos)
        if (end < 0) throw XmlParseException("Unterminated comment")
        pos = end + 3
    }

    private fun handleCData(events: MutableList<XmlEvent>) {
        pos += 8 // skip "![CDATA["
        val end = input.indexOf("]]>", pos)
        if (end < 0) throw XmlParseException("Unterminated CDATA section")
        val content = input.substring(pos, end)
        if (content.isNotEmpty()) {
            events.add(XmlEvent.Text(content))
        }
        pos = end + 3
    }

    private fun skipDeclaration() {
        // Skip <!DOCTYPE ...> or other declarations
        var depth = 1
        while (pos < len && depth > 0) {
            when (input[pos]) {
                '<' -> depth++
                '>' -> depth--
            }
            pos++
        }
    }

    private fun skipProcessingInstruction() {
        val end = input.indexOf("?>", pos)
        if (end < 0) throw XmlParseException("Unterminated processing instruction")
        pos = end + 2
    }

    private fun skipWhitespace() {
        while (pos < len && input[pos].isWhitespace()) pos++
    }

    private fun expect(ch: Char) {
        if (pos >= len || input[pos] != ch) {
            val actual = if (pos < len) "'${input[pos]}'" else "end of input"
            throw XmlParseException("Expected '$ch' at position $pos, got $actual")
        }
        pos++
    }

    private fun isNameStartChar(ch: Char): Boolean =
        ch.isLetter() || ch == '_' || ch == ':'

    private fun isNameChar(ch: Char): Boolean =
        ch.isLetterOrDigit() || ch == '_' || ch == ':' || ch == '-' || ch == '.'

    companion object {
        private val ENTITY_MAP = mapOf(
            "amp" to "&",
            "lt" to "<",
            "gt" to ">",
            "quot" to "\"",
            "apos" to "'",
        )

        internal fun decodeEntities(raw: String): String {
            if ('&' !in raw) return raw
            val sb = StringBuilder(raw.length)
            var i = 0
            while (i < raw.length) {
                if (raw[i] == '&') {
                    val semi = raw.indexOf(';', i + 1)
                    if (semi < 0) {
                        sb.append('&')
                        i++
                        continue
                    }
                    val ref = raw.substring(i + 1, semi)
                    val decoded = when {
                        ref.startsWith('#') -> decodeCharRef(ref.substring(1))
                        else -> ENTITY_MAP[ref]
                    }
                    if (decoded != null) {
                        sb.append(decoded)
                        i = semi + 1
                    } else {
                        sb.append('&')
                        i++
                    }
                } else {
                    sb.append(raw[i])
                    i++
                }
            }
            return sb.toString()
        }

        private fun decodeCharRef(ref: String): String? {
            val codePoint = if (ref.startsWith('x') || ref.startsWith('X')) {
                ref.substring(1).toIntOrNull(16)
            } else {
                ref.toIntOrNull()
            }
            return codePoint?.toChar()?.toString()
        }
    }
}

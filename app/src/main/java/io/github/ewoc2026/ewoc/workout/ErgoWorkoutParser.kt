package io.github.ewoc2026.ewoc.workout

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Parses frozen ergo_workout v0.1 payloads intended for future `.ewo` files into parsed,
 * validated, and repeat-expanded forms.
 *
 * The parser is intentionally strict so malformed or AI-generated files fail fast with a stable
 * field path before runner integration is added.
 */
internal object ErgoWorkoutParser {
    private const val rootPath = "$"
    private const val frozenFormat = "ergo_workout"
    private const val frozenVersion = "0.1"
    private const val minHeartRateBpm = 40
    private const val maxHeartRateBpm = 210

    private val structuredJson = Json {
        ignoreUnknownKeys = false
        isLenient = false
    }

    fun parse(content: String): ErgoWorkoutParseResult {
        val rootElement = try {
            structuredJson.parseToJsonElement(content)
        } catch (exception: Exception) {
            return ErgoWorkoutParseResult.Failure(
                error = ErgoWorkoutValidationError(
                    code = ErgoWorkoutValidationErrorCode.INVALID_JSON,
                    message = exception.message?.takeIf { it.isNotBlank() }
                        ?: "Workout JSON is malformed.",
                    fieldPath = rootPath,
                ),
            )
        }

        val rootObject = rootElement as? JsonObject
            ?: return ErgoWorkoutParseResult.Failure(
                error = ErgoWorkoutValidationError(
                    code = ErgoWorkoutValidationErrorCode.INVALID_JSON,
                    message = "Workout JSON root must be an object.",
                    fieldPath = rootPath,
                ),
            )

        return try {
            val parsed = parseWorkout(rootObject)
            val normalized = normalize(parsed)
            val compiled = compile(normalized)
            ErgoWorkoutParseResult.Success(
                parsed = parsed,
                normalized = normalized,
                compiled = compiled,
            )
        } catch (exception: ValidationException) {
            ErgoWorkoutParseResult.Failure(error = exception.error)
        }
    }

    private fun parseWorkout(root: JsonObject): ParsedErgoWorkoutFile {
        requireOnlyKeys(
            json = root,
            allowedKeys = setOf("format", "version", "title", "description", "control", "segments"),
            path = rootPath,
        )
        return ParsedErgoWorkoutFile(
            format = requireStringField(root, "format", rootPath),
            version = requireStringField(root, "version", rootPath),
            title = requireStringField(root, "title", rootPath),
            description = optionalStringField(root, "description", rootPath),
            control = root["control"]?.let { parseControl(it, childPath(rootPath, "control")) },
            segments = requireArrayField(root, "segments", rootPath).mapIndexed { index, element ->
                parseSegment(
                    element = element,
                    path = indexPath(childPath(rootPath, "segments"), index),
                )
            },
        )
    }

    private fun parseControl(element: JsonElement, path: String): ParsedErgoWorkoutControl {
        val json = requireObject(element, path)
        requireOnlyKeys(
            json = json,
            allowedKeys = setOf(
                "initial_power_watts",
                "min_power_watts",
                "max_power_watts",
                "signal_loss_power_watts",
            ),
            path = path,
        )
        return ParsedErgoWorkoutControl(
            initialPowerWatts = requireIntField(json, "initial_power_watts", path),
            minPowerWatts = requireIntField(json, "min_power_watts", path),
            maxPowerWatts = requireIntField(json, "max_power_watts", path),
            signalLossPowerWatts = requireIntField(json, "signal_loss_power_watts", path),
        )
    }

    private fun parseSegment(element: JsonElement, path: String): ParsedErgoWorkoutSegment {
        val json = requireObject(element, path)
        val type = requireStringField(json, "type", path)
        return when (type) {
            "steady" -> {
                requireOnlyKeys(json, setOf("type", "duration_sec", "target"), path)
                ParsedErgoWorkoutSegment.Steady(
                    durationSec = requireIntField(json, "duration_sec", path),
                    target = parseTarget(
                        element = requireField(json, "target", path),
                        path = childPath(path, "target"),
                    ),
                )
            }

            "ramp" -> {
                requireOnlyKeys(json, setOf("type", "duration_sec", "from_target", "to_target"), path)
                ParsedErgoWorkoutSegment.Ramp(
                    durationSec = requireIntField(json, "duration_sec", path),
                    fromTarget = parseTarget(
                        element = requireField(json, "from_target", path),
                        path = childPath(path, "from_target"),
                    ),
                    toTarget = parseTarget(
                        element = requireField(json, "to_target", path),
                        path = childPath(path, "to_target"),
                    ),
                )
            }

            "repeat" -> {
                requireOnlyKeys(json, setOf("type", "count", "steps"), path)
                ParsedErgoWorkoutSegment.Repeat(
                    count = requireIntField(json, "count", path),
                    steps = requireArrayField(json, "steps", path).mapIndexed { index, step ->
                        parseSegment(
                            element = step,
                            path = indexPath(childPath(path, "steps"), index),
                        )
                    },
                )
            }

            else -> fail(
                code = ErgoWorkoutValidationErrorCode.UNKNOWN_SEGMENT_TYPE,
                message = "Unsupported segment type '$type'.",
                fieldPath = childPath(path, "type"),
            )
        }
    }

    private fun parseTarget(element: JsonElement, path: String): ParsedErgoWorkoutTarget {
        val json = requireObject(element, path)
        requireOnlyKeys(json, setOf("metric", "value", "range"), path)
        val metric = requireStringField(json, "metric", path)
        return when (metric) {
            "power" -> {
                requireOnlyKeys(json, setOf("metric", "value"), path)
                ParsedErgoWorkoutTarget.Power(
                    value = requireIntField(json, "value", path),
                )
            }

            "heart_rate" -> {
                requireOnlyKeys(json, setOf("metric", "range"), path)
                val rangePath = childPath(path, "range")
                val range = requireObject(requireField(json, "range", path), rangePath)
                requireOnlyKeys(range, setOf("low", "high"), rangePath)
                ParsedErgoWorkoutTarget.HeartRateRange(
                    low = requireIntField(range, "low", rangePath),
                    high = requireIntField(range, "high", rangePath),
                )
            }

            else -> fail(
                code = ErgoWorkoutValidationErrorCode.INVALID_TARGET_METRIC,
                message = "Target metric must be 'power' or 'heart_rate'.",
                fieldPath = childPath(path, "metric"),
            )
        }
    }

    private fun normalize(parsed: ParsedErgoWorkoutFile): NormalizedErgoWorkout {
        if (parsed.format != frozenFormat) {
            fail(
                code = ErgoWorkoutValidationErrorCode.INVALID_FORMAT,
                message = "format must be '$frozenFormat'.",
                fieldPath = childPath(rootPath, "format"),
            )
        }
        if (parsed.version != frozenVersion) {
            fail(
                code = ErgoWorkoutValidationErrorCode.UNSUPPORTED_VERSION,
                message = "version must be '$frozenVersion'.",
                fieldPath = childPath(rootPath, "version"),
            )
        }

        val normalizedTitle = parsed.title.trim().takeIf { it.isNotEmpty() }
            ?: fail(
                code = ErgoWorkoutValidationErrorCode.EMPTY_TITLE,
                message = "title must not be blank.",
                fieldPath = childPath(rootPath, "title"),
            )

        if (parsed.segments.isEmpty()) {
            fail(
                code = ErgoWorkoutValidationErrorCode.EMPTY_SEGMENTS,
                message = "segments must contain at least one segment.",
                fieldPath = childPath(rootPath, "segments"),
            )
        }

        var requiresHeartRateControl = false
        val normalizedSegments = parsed.segments.mapIndexed { index, segment ->
            val normalized = normalizeSegment(
                segment = segment,
                path = indexPath(childPath(rootPath, "segments"), index),
            )
            requiresHeartRateControl = requiresHeartRateControl || normalized.requiresHeartRateControl
            normalized.value
        }

        val control = when {
            requiresHeartRateControl -> {
                normalizeControl(
                    control = parsed.control ?: fail(
                        code = ErgoWorkoutValidationErrorCode.CONTROL_REQUIRED_FOR_HEART_RATE,
                        message = "control is required when any segment targets heart rate.",
                        fieldPath = childPath(rootPath, "control"),
                    ),
                )
            }
            parsed.control != null -> normalizeControl(parsed.control)
            else -> null
        }

        return NormalizedErgoWorkout(
            title = normalizedTitle,
            description = parsed.description?.trim()?.takeIf { it.isNotEmpty() },
            control = control,
            segments = normalizedSegments,
        )
    }

    private fun normalizeControl(control: ParsedErgoWorkoutControl): ErgoWorkoutControl {
        if (
            control.minPowerWatts <= 0 ||
            control.maxPowerWatts <= 0 ||
            control.minPowerWatts >= control.maxPowerWatts
        ) {
            fail(
                code = ErgoWorkoutValidationErrorCode.INVALID_CONTROL_BOUNDS,
                message = "Control bounds must be positive and min_power_watts must be less than max_power_watts.",
                fieldPath = childPath(rootPath, "control"),
            )
        }

        if (control.initialPowerWatts !in control.minPowerWatts..control.maxPowerWatts) {
            fail(
                code = ErgoWorkoutValidationErrorCode.CONTROL_VALUE_OUT_OF_BOUNDS,
                message = "initial_power_watts must stay within min_power_watts and max_power_watts.",
                fieldPath = childPath(childPath(rootPath, "control"), "initial_power_watts"),
            )
        }
        if (control.signalLossPowerWatts !in control.minPowerWatts..control.maxPowerWatts) {
            fail(
                code = ErgoWorkoutValidationErrorCode.CONTROL_VALUE_OUT_OF_BOUNDS,
                message = "signal_loss_power_watts must stay within min_power_watts and max_power_watts.",
                fieldPath = childPath(childPath(rootPath, "control"), "signal_loss_power_watts"),
            )
        }

        return ErgoWorkoutControl(
            initialPowerWatts = control.initialPowerWatts,
            minPowerWatts = control.minPowerWatts,
            maxPowerWatts = control.maxPowerWatts,
            signalLossPowerWatts = control.signalLossPowerWatts,
        )
    }

    private fun normalizeSegment(
        segment: ParsedErgoWorkoutSegment,
        path: String,
    ): Normalization<NormalizedErgoWorkoutSegment> {
        return when (segment) {
            is ParsedErgoWorkoutSegment.Steady -> {
                val steady = normalizeSteadySpec(
                    durationSec = segment.durationSec,
                    target = segment.target,
                    path = path,
                )
                when (val spec = steady.value) {
                    is NormalizedSteadySpec.Power -> Normalization(
                        value = NormalizedErgoWorkoutSegment.PowerSteady(
                            durationSec = spec.durationSec,
                            watts = spec.watts,
                        ),
                        requiresHeartRateControl = false,
                    )

                    is NormalizedSteadySpec.HeartRate -> Normalization(
                        value = NormalizedErgoWorkoutSegment.HeartRateSteady(
                            durationSec = spec.durationSec,
                            lowBpm = spec.lowBpm,
                            highBpm = spec.highBpm,
                        ),
                        requiresHeartRateControl = true,
                    )
                }
            }

            is ParsedErgoWorkoutSegment.Ramp -> {
                val durationSec = normalizeDuration(segment.durationSec, childPath(path, "duration_sec"))
                val fromTarget = segment.fromTarget as? ParsedErgoWorkoutTarget.Power
                    ?: fail(
                        code = ErgoWorkoutValidationErrorCode.RAMP_TARGET_MUST_BE_POWER,
                        message = "Ramp targets must use the power metric in frozen v0.1.",
                        fieldPath = childPath(childPath(path, "from_target"), "metric"),
                    )
                val toTarget = segment.toTarget as? ParsedErgoWorkoutTarget.Power
                    ?: fail(
                        code = ErgoWorkoutValidationErrorCode.RAMP_TARGET_MUST_BE_POWER,
                        message = "Ramp targets must use the power metric in frozen v0.1.",
                        fieldPath = childPath(childPath(path, "to_target"), "metric"),
                    )
                Normalization(
                    value = NormalizedErgoWorkoutSegment.PowerRamp(
                        durationSec = durationSec,
                        fromWatts = normalizePowerValue(
                            fromTarget.value,
                            childPath(childPath(path, "from_target"), "value"),
                        ),
                        toWatts = normalizePowerValue(
                            toTarget.value,
                            childPath(childPath(path, "to_target"), "value"),
                        ),
                    ),
                    requiresHeartRateControl = false,
                )
            }

            is ParsedErgoWorkoutSegment.Repeat -> {
                val count = if (segment.count > 0) {
                    segment.count
                } else {
                    fail(
                        code = ErgoWorkoutValidationErrorCode.INVALID_REPEAT_COUNT,
                        message = "count must be a positive integer.",
                        fieldPath = childPath(path, "count"),
                    )
                }
                if (segment.steps.size < 2) {
                    fail(
                        code = ErgoWorkoutValidationErrorCode.REPEAT_STEPS_TOO_SHORT,
                        message = "Repeat steps must contain at least two steady segments.",
                        fieldPath = childPath(path, "steps"),
                    )
                }

                var requiresHeartRateControl = false
                val normalizedSteps = segment.steps.mapIndexed { index, child ->
                    if (child !is ParsedErgoWorkoutSegment.Steady) {
                        fail(
                            code = ErgoWorkoutValidationErrorCode.REPEAT_CHILD_TYPE_NOT_ALLOWED,
                            message = "Repeat steps may contain only steady segments in frozen v0.1.",
                            fieldPath = childPath(
                                indexPath(childPath(path, "steps"), index),
                                "type",
                            ),
                        )
                    }
                    val normalized = normalizeRepeatStep(
                        segment = child,
                        path = indexPath(childPath(path, "steps"), index),
                    )
                    requiresHeartRateControl = requiresHeartRateControl || normalized.requiresHeartRateControl
                    normalized.value
                }

                Normalization(
                    value = NormalizedErgoWorkoutSegment.Repeat(
                        count = count,
                        steps = normalizedSteps,
                    ),
                    requiresHeartRateControl = requiresHeartRateControl,
                )
            }
        }
    }

    private fun normalizeRepeatStep(
        segment: ParsedErgoWorkoutSegment.Steady,
        path: String,
    ): Normalization<NormalizedErgoWorkoutRepeatStep> {
        val steady = normalizeSteadySpec(
            durationSec = segment.durationSec,
            target = segment.target,
            path = path,
        )
        return when (val spec = steady.value) {
            is NormalizedSteadySpec.Power -> Normalization(
                value = NormalizedErgoWorkoutRepeatStep.PowerSteady(
                    durationSec = spec.durationSec,
                    watts = spec.watts,
                ),
                requiresHeartRateControl = false,
            )

            is NormalizedSteadySpec.HeartRate -> Normalization(
                value = NormalizedErgoWorkoutRepeatStep.HeartRateSteady(
                    durationSec = spec.durationSec,
                    lowBpm = spec.lowBpm,
                    highBpm = spec.highBpm,
                ),
                requiresHeartRateControl = true,
            )
        }
    }

    private fun normalizeSteadySpec(
        durationSec: Int,
        target: ParsedErgoWorkoutTarget,
        path: String,
    ): Normalization<NormalizedSteadySpec> {
        val normalizedDuration = normalizeDuration(durationSec, childPath(path, "duration_sec"))
        return when (target) {
            is ParsedErgoWorkoutTarget.Power -> Normalization(
                value = NormalizedSteadySpec.Power(
                    durationSec = normalizedDuration,
                    watts = normalizePowerValue(
                        target.value,
                        childPath(childPath(path, "target"), "value"),
                    ),
                ),
                requiresHeartRateControl = false,
            )

            is ParsedErgoWorkoutTarget.HeartRateRange -> {
                val range = normalizeHeartRateRange(
                    low = target.low,
                    high = target.high,
                    path = childPath(childPath(path, "target"), "range"),
                )
                Normalization(
                    value = NormalizedSteadySpec.HeartRate(
                        durationSec = normalizedDuration,
                        lowBpm = range.first,
                        highBpm = range.second,
                    ),
                    requiresHeartRateControl = true,
                )
            }
        }
    }

    private fun normalizeDuration(durationSec: Int, path: String): Int {
        return durationSec.takeIf { it > 0 } ?: fail(
            code = ErgoWorkoutValidationErrorCode.INVALID_DURATION_SEC,
            message = "duration_sec must be a positive integer.",
            fieldPath = path,
        )
    }

    private fun normalizePowerValue(value: Int, path: String): Int {
        return value.takeIf { it > 0 } ?: fail(
            code = ErgoWorkoutValidationErrorCode.INVALID_POWER_TARGET_VALUE,
            message = "Power target values must be positive integers.",
            fieldPath = path,
        )
    }

    private fun normalizeHeartRateRange(low: Int, high: Int, path: String): Pair<Int, Int> {
        if (low < minHeartRateBpm || high > maxHeartRateBpm || low >= high) {
            fail(
                code = ErgoWorkoutValidationErrorCode.INVALID_HEART_RATE_RANGE,
                message = "Heart-rate ranges must satisfy 40 <= low < high <= 210.",
                fieldPath = path,
            )
        }
        return low to high
    }

    private fun compile(workout: NormalizedErgoWorkout): CompiledErgoWorkout {
        val compiledSteps = mutableListOf<CompiledErgoWorkoutStep>()
        var nextOffsetSec = 0

        fun emitPowerSteady(durationSec: Int, watts: Int) {
            compiledSteps += CompiledErgoWorkoutStep.PowerSteady(
                stepIndex = compiledSteps.size,
                startOffsetSec = nextOffsetSec,
                durationSec = durationSec,
                watts = watts,
            )
            nextOffsetSec = safeAdd(nextOffsetSec, durationSec)
        }

        fun emitHeartRateSteady(durationSec: Int, lowBpm: Int, highBpm: Int) {
            val control = workout.control
                ?: throw IllegalStateException("Heart-rate steps require validated control state.")
            compiledSteps += CompiledErgoWorkoutStep.HeartRateSteady(
                stepIndex = compiledSteps.size,
                startOffsetSec = nextOffsetSec,
                durationSec = durationSec,
                lowBpm = lowBpm,
                highBpm = highBpm,
                initialPowerWatts = control.initialPowerWatts,
                minPowerWatts = control.minPowerWatts,
                maxPowerWatts = control.maxPowerWatts,
                signalLossPowerWatts = control.signalLossPowerWatts,
            )
            nextOffsetSec = safeAdd(nextOffsetSec, durationSec)
        }

        fun emitPowerRamp(durationSec: Int, fromWatts: Int, toWatts: Int) {
            compiledSteps += CompiledErgoWorkoutStep.PowerRamp(
                stepIndex = compiledSteps.size,
                startOffsetSec = nextOffsetSec,
                durationSec = durationSec,
                fromWatts = fromWatts,
                toWatts = toWatts,
            )
            nextOffsetSec = safeAdd(nextOffsetSec, durationSec)
        }

        workout.segments.forEach { segment ->
            when (segment) {
                is NormalizedErgoWorkoutSegment.PowerSteady -> emitPowerSteady(
                    durationSec = segment.durationSec,
                    watts = segment.watts,
                )

                is NormalizedErgoWorkoutSegment.HeartRateSteady -> emitHeartRateSteady(
                    durationSec = segment.durationSec,
                    lowBpm = segment.lowBpm,
                    highBpm = segment.highBpm,
                )

                is NormalizedErgoWorkoutSegment.PowerRamp -> emitPowerRamp(
                    durationSec = segment.durationSec,
                    fromWatts = segment.fromWatts,
                    toWatts = segment.toWatts,
                )

                is NormalizedErgoWorkoutSegment.Repeat -> {
                    repeat(segment.count) {
                        segment.steps.forEach { step ->
                            when (step) {
                                is NormalizedErgoWorkoutRepeatStep.PowerSteady -> emitPowerSteady(
                                    durationSec = step.durationSec,
                                    watts = step.watts,
                                )

                                is NormalizedErgoWorkoutRepeatStep.HeartRateSteady -> emitHeartRateSteady(
                                    durationSec = step.durationSec,
                                    lowBpm = step.lowBpm,
                                    highBpm = step.highBpm,
                                )
                            }
                        }
                    }
                }
            }
        }

        return CompiledErgoWorkout(
            title = workout.title,
            description = workout.description,
            steps = compiledSteps,
            totalDurationSec = nextOffsetSec,
        )
    }

    private fun requireOnlyKeys(json: JsonObject, allowedKeys: Set<String>, path: String) {
        json.keys.sorted().firstOrNull { it !in allowedKeys }?.let { unknownKey ->
            fail(
                code = ErgoWorkoutValidationErrorCode.UNKNOWN_FIELD,
                message = "Unknown field '$unknownKey'.",
                fieldPath = childPath(path, unknownKey),
            )
        }
    }

    private fun requireField(json: JsonObject, fieldName: String, parentPath: String): JsonElement {
        return json[fieldName] ?: fail(
            code = ErgoWorkoutValidationErrorCode.MISSING_REQUIRED_FIELD,
            message = "Missing required field '$fieldName'.",
            fieldPath = childPath(parentPath, fieldName),
        )
    }

    private fun requireStringField(json: JsonObject, fieldName: String, parentPath: String): String {
        return requireStringValue(
            element = requireField(json, fieldName, parentPath),
            path = childPath(parentPath, fieldName),
        )
    }

    private fun optionalStringField(json: JsonObject, fieldName: String, parentPath: String): String? {
        val element = json[fieldName] ?: return null
        return requireStringValue(element, childPath(parentPath, fieldName))
    }

    private fun requireIntField(json: JsonObject, fieldName: String, parentPath: String): Int {
        return requireIntValue(
            element = requireField(json, fieldName, parentPath),
            path = childPath(parentPath, fieldName),
        )
    }

    private fun requireArrayField(json: JsonObject, fieldName: String, parentPath: String): JsonArray {
        return requireArray(
            element = requireField(json, fieldName, parentPath),
            path = childPath(parentPath, fieldName),
        )
    }

    private fun requireStringValue(element: JsonElement, path: String): String {
        val primitive = element as? JsonPrimitive ?: fail(
            code = ErgoWorkoutValidationErrorCode.INVALID_TYPE,
            message = "Expected a string.",
            fieldPath = path,
        )
        if (!primitive.isString) {
            fail(
                code = ErgoWorkoutValidationErrorCode.INVALID_TYPE,
                message = "Expected a string.",
                fieldPath = path,
            )
        }
        return primitive.content
    }

    private fun requireIntValue(element: JsonElement, path: String): Int {
        val primitive = element as? JsonPrimitive ?: fail(
            code = ErgoWorkoutValidationErrorCode.INVALID_TYPE,
            message = "Expected an integer.",
            fieldPath = path,
        )
        if (primitive.isString) {
            fail(
                code = ErgoWorkoutValidationErrorCode.INVALID_TYPE,
                message = "Expected an integer.",
                fieldPath = path,
            )
        }
        return primitive.content.toIntOrNull() ?: fail(
            code = ErgoWorkoutValidationErrorCode.INVALID_TYPE,
            message = "Expected an integer.",
            fieldPath = path,
        )
    }

    private fun requireObject(element: JsonElement, path: String): JsonObject {
        return element as? JsonObject ?: fail(
            code = ErgoWorkoutValidationErrorCode.INVALID_TYPE,
            message = "Expected an object.",
            fieldPath = path,
        )
    }

    private fun requireArray(element: JsonElement, path: String): JsonArray {
        return element as? JsonArray ?: fail(
            code = ErgoWorkoutValidationErrorCode.INVALID_TYPE,
            message = "Expected an array.",
            fieldPath = path,
        )
    }

    private fun safeAdd(current: Int, increment: Int): Int {
        return try {
            Math.addExact(current, increment)
        } catch (_: ArithmeticException) {
            fail(
                code = ErgoWorkoutValidationErrorCode.TOTAL_DURATION_OVERFLOW,
                message = "Compiled workout duration exceeds the supported range.",
                fieldPath = childPath(rootPath, "segments"),
            )
        }
    }

    private fun childPath(parentPath: String, fieldName: String): String = "$parentPath.$fieldName"

    private fun indexPath(parentPath: String, index: Int): String = "$parentPath[$index]"

    private fun fail(
        code: ErgoWorkoutValidationErrorCode,
        message: String,
        fieldPath: String,
    ): Nothing {
        throw ValidationException(
            error = ErgoWorkoutValidationError(
                code = code,
                message = message,
                fieldPath = fieldPath,
            ),
        )
    }

    private data class Normalization<T>(
        val value: T,
        val requiresHeartRateControl: Boolean,
    )

    private sealed class NormalizedSteadySpec {
        abstract val durationSec: Int

        data class Power(
            override val durationSec: Int,
            val watts: Int,
        ) : NormalizedSteadySpec()

        data class HeartRate(
            override val durationSec: Int,
            val lowBpm: Int,
            val highBpm: Int,
        ) : NormalizedSteadySpec()
    }

    private class ValidationException(
        val error: ErgoWorkoutValidationError,
    ) : IllegalArgumentException(error.message)
}

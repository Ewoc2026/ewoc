package io.github.ewoc2026.ewoc.workout

import com.ewo.core.CompiledEwoWorkoutStep
import com.ewo.core.EwoCadenceRange
import com.ewo.core.EwoCompileContext
import com.ewo.core.EwoDifficulty
import com.ewo.core.EwoEngine
import com.ewo.core.EwoMessageAnchor
import com.ewo.core.EwoMessage
import com.ewo.core.EwoMessageKind
import com.ewo.core.EwoMessageTiming
import com.ewo.core.EwoWorkoutControl
import com.ewo.core.EwoWorkoutParseResult
import com.ewo.core.EwoWorkoutValidationError
import com.ewo.core.SanityIssue
import com.ewo.core.SanitySeverity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Canonical workout import formats recognized by the app.
 */
enum class WorkoutImportFormat {
    ZWO_XML,
    CANONICAL_EWO_JSON,
    ERGO_WORKOUT_JSON,
    MYWHOOSH_JSON,
    UNKNOWN,
}

/**
 * Stable error codes for workout import failures.
 */
enum class WorkoutImportErrorCode {
    EMPTY_CONTENT,
    UNSUPPORTED_FORMAT,
    UNSUPPORTED_SEGMENT,
    PARSE_FAILED,
    EMPTY_WORKOUT,
}

/**
 * Structured import error payload for UI and logging.
 */
data class WorkoutImportError(
    val code: WorkoutImportErrorCode,
    val message: String,
    val detectedFormat: WorkoutImportFormat,
    val technicalDetails: String? = null,
)

/**
 * Import payload variants carried beyond lightweight format detection.
 *
 * This keeps `.ewo` parsing reachable without forcing the existing execution
 * model to represent wider canonical semantics prematurely.
 */
sealed interface WorkoutImportPayload {
    data class Zwo(
        val workoutFile: WorkoutFile,
    ) : WorkoutImportPayload

    data class ErgoWorkout(
        val workout: ImportedErgoWorkout,
    ) : WorkoutImportPayload
}

/**
 * Minimal app-facing handoff for imported `.ewo` timelines.
 *
 * The model intentionally mirrors only the repeat-expanded timeline needed for
 * future runtime or editor integration, while parser internals stay private.
 */
data class ImportedErgoWorkout(
    val title: String,
    val description: String?,
    val steps: List<ImportedErgoWorkoutStep>,
    val totalDurationSec: Int,
    val canonicalMetadata: ImportedErgoWorkoutCanonicalMetadata? = null,
    val difficulty: ImportedErgoWorkoutDifficulty? = null,
    val tags: List<String> = emptyList(),
    val sanityIssues: List<ImportedSanityIssue> = emptyList(),
)

/**
 * Canonical `.ewo` metadata preserved at the import boundary for future slices.
 *
 * The runtime/execution mapper ignores this payload for now, but keeping it on
 * the handoff model prevents authored metadata from being lost during import.
 */
data class ImportedErgoWorkoutCanonicalMetadata(
    val uid: String?,
    val revision: Int?,
    val titleLocalized: ImportedErgoWorkoutLocalizedText? = null,
    val descriptionLocalized: ImportedErgoWorkoutLocalizedText? = null,
    val control: ImportedErgoWorkoutControl?,
    val messages: List<ImportedErgoWorkoutMessage>,
)

/**
 * Public import-time copy of the canonical control block.
 *
 * Keeping the full control block avoids coupling future runtime slices to the
 * parser's internal model classes.
 */
data class ImportedErgoWorkoutControl(
    val initialPowerWatts: Int,
    val minPowerWatts: Int,
    val maxPowerWatts: Int,
    val signalLossPowerWatts: Int,
    val hrUpperCapBpm: Int,
)

/**
 * Public import-time copy of authored canonical workout messages.
 */
data class ImportedErgoWorkoutMessage(
    val kind: ImportedErgoWorkoutMessageKind,
    val timing: ImportedErgoWorkoutMessageTiming,
    val text: ImportedErgoWorkoutLocalizedText,
)

/**
 * Stable public message kinds preserved from canonical `.ewo` imports.
 */
enum class ImportedErgoWorkoutMessageKind {
    INTRO,
    INSTRUCTION,
    TRANSITION,
    WARNING,
    MOTIVATION,
}

/**
 * Stable public message timing values preserved from canonical `.ewo` imports.
 */
enum class ImportedErgoWorkoutMessageAnchor {
    START,
    END,
}

/**
 * Public import-safe message timing copied from canonical `.ewo`.
 *
 * [durationSec] stays nullable so authored imports can keep relying on the
 * shared session fallback while synthetic producers may opt into a longer or
 * shorter display window without changing layout behavior.
 */
data class ImportedErgoWorkoutMessageTiming(
    val anchor: ImportedErgoWorkoutMessageAnchor,
    val offsetSec: Int,
    val durationSec: Int? = null,
)

/**
 * Cadence coaching hint carried from canonical `.ewo` imports.
 * Never affects trainer resistance or execution semantics — UI layer only.
 */
data class ImportedErgoWorkoutCadenceRange(val low: Int, val high: Int)

/**
 * Workout difficulty level preserved from canonical `.ewo` v1.3+ imports.
 */
enum class ImportedErgoWorkoutDifficulty {
    EASY, MODERATE, HARD, VERY_HARD,
}

/**
 * Severity of a sanity issue reported at import time.
 */
enum class ImportedSanityIssueSeverity {
    WARNING, ERROR,
}

/**
 * Non-fatal workout quality issue reported after successful import.
 *
 * Sanity issues never block import; they are surfaced to the UI as advisory information.
 */
data class ImportedSanityIssue(
    val code: String,
    val severity: ImportedSanityIssueSeverity,
    val message: String,
    val segmentId: String?,
)

/**
 * Import-safe localized text payload for preserved canonical messages.
 */
data class ImportedErgoWorkoutLocalizedText(
    val defaultText: String,
    val translations: Map<String, String>,
)

/**
 * Per-step canonical metadata preserved across import handoff.
 */
data class ImportedErgoWorkoutStepCanonicalMetadata(
    val messages: List<ImportedErgoWorkoutMessage>,
    val origin: ImportedErgoWorkoutStepOrigin,
)

/**
 * Canonical source identity for repeat-expanded imported steps.
 */
data class ImportedErgoWorkoutStepOrigin(
    val sourceSegmentId: String,
    val sourceSegmentLabel: String?,
    val sourceSegmentNote: String?,
    val enclosingRepeatSegmentId: String?,
    val repeatIterationIndex: Int?,
)

/**
 * Repeat-expanded `.ewo` execution steps preserved at import time.
 */
sealed class ImportedErgoWorkoutStep {
    abstract val stepIndex: Int
    abstract val startOffsetSec: Int
    abstract val durationSec: Int
    abstract val canonicalMetadata: ImportedErgoWorkoutStepCanonicalMetadata?

    data class PowerSteady(
        override val stepIndex: Int,
        override val startOffsetSec: Int,
        override val durationSec: Int,
        val watts: Int,
        val cadence: ImportedErgoWorkoutCadenceRange? = null,
        override val canonicalMetadata: ImportedErgoWorkoutStepCanonicalMetadata? = null,
    ) : ImportedErgoWorkoutStep()

    data class PowerRamp(
        override val stepIndex: Int,
        override val startOffsetSec: Int,
        override val durationSec: Int,
        val fromWatts: Int,
        val toWatts: Int,
        val cadence: ImportedErgoWorkoutCadenceRange? = null,
        override val canonicalMetadata: ImportedErgoWorkoutStepCanonicalMetadata? = null,
    ) : ImportedErgoWorkoutStep()

    data class HeartRateSteady(
        override val stepIndex: Int,
        override val startOffsetSec: Int,
        override val durationSec: Int,
        val lowBpm: Int,
        val highBpm: Int,
        val initialPowerWatts: Int,
        val minPowerWatts: Int,
        val maxPowerWatts: Int,
        val signalLossPowerWatts: Int,
        val cadence: ImportedErgoWorkoutCadenceRange? = null,
        override val canonicalMetadata: ImportedErgoWorkoutStepCanonicalMetadata? = null,
    ) : ImportedErgoWorkoutStep()

    data class FreeRide(
        override val stepIndex: Int,
        override val startOffsetSec: Int,
        override val durationSec: Int,
        val cadence: ImportedErgoWorkoutCadenceRange? = null,
        override val canonicalMetadata: ImportedErgoWorkoutStepCanonicalMetadata? = null,
    ) : ImportedErgoWorkoutStep()
}

/**
 * Import operation result with either parsed workout data or a typed error.
 */
sealed class WorkoutImportResult {
    data class Success(
        val format: WorkoutImportFormat,
        val payload: WorkoutImportPayload,
    ) : WorkoutImportResult() {
        val workoutFile: WorkoutFile?
            get() = (payload as? WorkoutImportPayload.Zwo)?.workoutFile

        val ergoWorkout: ImportedErgoWorkout?
            get() = (payload as? WorkoutImportPayload.ErgoWorkout)?.workout
    }

    data class Failure(
        val error: WorkoutImportError,
    ) : WorkoutImportResult()
}

/**
 * Imports workout files from text content with lightweight format detection.
 *
 * The service intentionally keeps detection simple and deterministic:
 * - `.zwo`/`.xml` are parsed through the existing ZWO XML parser.
 * - JSON payloads with `format: "ewo"` are validated through the canonical parser.
 * - JSON payloads with `format: "ergo_workout"` are validated through the frozen parser.
 * - Both `.ewo` families return the same narrow imported-workout handoff model.
 * - `.json` is recognized as MyWhoosh JSON and reported as not yet supported.
 * - Unknown content types return a typed unsupported-format failure.
 */
class WorkoutImportService {
    private val sniffingJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Parses a workout definition from raw text.
     *
     * [sourceName] is optional but recommended, because filename extension helps
     * select the intended parser when content sniffing is ambiguous.
     *
     * [ftpWatts] is the rider's configured FTP. Required when importing canonical `.ewo` v1.1+
     * workouts that contain `ftp_percent` targets; ignored otherwise.
     */
    fun importFromText(sourceName: String?, content: String, ftpWatts: Int? = null): WorkoutImportResult {
        return importFromText(
            sourceName = sourceName,
            content = content,
            context = EwoCompileContext(ftpWatts = ftpWatts),
        )
    }

    /**
     * Parses a workout definition from raw text using the full rider compile context.
     */
    fun importFromText(
        sourceName: String?,
        content: String,
        context: EwoCompileContext,
    ): WorkoutImportResult {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) {
            return WorkoutImportResult.Failure(
                WorkoutImportError(
                    code = WorkoutImportErrorCode.EMPTY_CONTENT,
                    message = "Workout file content is empty.",
                    detectedFormat = WorkoutImportFormat.UNKNOWN,
                ),
            )
        }

        val format = detectFormat(sourceName = sourceName, trimmedContent = trimmed)
        return when (format) {
            WorkoutImportFormat.ZWO_XML -> parseZwoXml(trimmed)
            WorkoutImportFormat.CANONICAL_EWO_JSON -> parseCanonicalEwo(trimmed, context)
            WorkoutImportFormat.ERGO_WORKOUT_JSON -> parseErgoWorkout(trimmed)
            WorkoutImportFormat.MYWHOOSH_JSON -> WorkoutImportResult.Failure(
                WorkoutImportError(
                    code = WorkoutImportErrorCode.UNSUPPORTED_FORMAT,
                    message = "MyWhoosh JSON import is not implemented yet.",
                    detectedFormat = WorkoutImportFormat.MYWHOOSH_JSON,
                ),
            )
            WorkoutImportFormat.UNKNOWN -> WorkoutImportResult.Failure(
                WorkoutImportError(
                    code = WorkoutImportErrorCode.UNSUPPORTED_FORMAT,
                    message = "Unsupported workout file format.",
                    detectedFormat = WorkoutImportFormat.UNKNOWN,
                ),
            )
        }
    }

    private fun parseZwoXml(content: String): WorkoutImportResult {
        return try {
            val workout = parseZwo(content)
            if (workout.steps.isEmpty()) {
                WorkoutImportResult.Failure(
                    WorkoutImportError(
                        code = WorkoutImportErrorCode.EMPTY_WORKOUT,
                        message = "Workout does not contain executable steps.",
                        detectedFormat = WorkoutImportFormat.ZWO_XML,
                    ),
                )
            } else if (workout.steps.any { it is Step.FreeRide }) {
                unsupportedFreeRideFailure(WorkoutImportFormat.ZWO_XML)
            } else {
                WorkoutImportResult.Success(
                    format = WorkoutImportFormat.ZWO_XML,
                    payload = WorkoutImportPayload.Zwo(workoutFile = workout),
                )
            }
        } catch (e: Exception) {
            WorkoutImportResult.Failure(
                WorkoutImportError(
                    code = WorkoutImportErrorCode.PARSE_FAILED,
                    message = "Workout XML parsing failed.",
                    detectedFormat = WorkoutImportFormat.ZWO_XML,
                    technicalDetails = e.message?.takeIf { it.isNotBlank() },
                ),
            )
        }
    }

    private fun parseCanonicalEwo(content: String, context: EwoCompileContext): WorkoutImportResult {
        return when (val result = EwoEngine.parseDroppingInvalidTags(content, context).parseResult) {
            is EwoWorkoutParseResult.Success.Compiled -> {
                val workout = ImportedErgoWorkout(
                    title = result.compiled.title,
                    description = result.compiled.description,
                    steps = result.compiled.steps.map(::mapCanonicalStep),
                    totalDurationSec = result.compiled.totalDurationSec,
                    canonicalMetadata = ImportedErgoWorkoutCanonicalMetadata(
                        uid = result.compiled.uid,
                        revision = result.compiled.revision,
                        titleLocalized = result.compiled.titleLocalized?.let(::mapLocalizedText),
                        descriptionLocalized = result.compiled.descriptionLocalized?.let(::mapLocalizedText),
                        control = result.normalized.control?.let(::mapCanonicalControl),
                        messages = result.compiled.messages.map(::mapCanonicalMessage),
                    ),
                    difficulty = result.compiled.difficulty?.let(::mapDifficulty),
                    tags = result.compiled.tags,
                    sanityIssues = result.sanityResult.issues.map(::mapSanityIssue),
                )
                if (workout.steps.any { it is ImportedErgoWorkoutStep.FreeRide }) {
                    unsupportedFreeRideFailure(WorkoutImportFormat.CANONICAL_EWO_JSON)
                } else {
                    WorkoutImportResult.Success(
                        format = WorkoutImportFormat.CANONICAL_EWO_JSON,
                        payload = WorkoutImportPayload.ErgoWorkout(workout = workout),
                    )
                }
            }

            is EwoWorkoutParseResult.Success.NeedsCompileContext -> WorkoutImportResult.Failure(
                WorkoutImportError(
                    code = WorkoutImportErrorCode.PARSE_FAILED,
                    message = "Workout requires athlete profile data to execute.",
                    detectedFormat = WorkoutImportFormat.CANONICAL_EWO_JSON,
                    technicalDetails = result.compileErrors.joinToString("; ") {
                        "${it.code.stableCode}: ${it.message}"
                    },
                ),
            )

            is EwoWorkoutParseResult.Failure -> WorkoutImportResult.Failure(
                WorkoutImportError(
                    code = WorkoutImportErrorCode.PARSE_FAILED,
                    message = "Canonical .ewo JSON parsing failed.",
                    detectedFormat = WorkoutImportFormat.CANONICAL_EWO_JSON,
                    technicalDetails = formatEwoValidationDetails(result.error),
                ),
            )
        }
    }

    private fun parseErgoWorkout(content: String): WorkoutImportResult {
        return when (val result = ErgoWorkoutParser.parse(content)) {
            is ErgoWorkoutParseResult.Success -> {
                val workout = ImportedErgoWorkout(
                    title = result.compiled.title,
                    description = result.compiled.description,
                    steps = result.compiled.steps.map { step ->
                        when (step) {
                            is CompiledErgoWorkoutStep.PowerSteady -> {
                                ImportedErgoWorkoutStep.PowerSteady(
                                    stepIndex = step.stepIndex,
                                    startOffsetSec = step.startOffsetSec,
                                    durationSec = step.durationSec,
                                    watts = step.watts,
                                )
                            }

                            is CompiledErgoWorkoutStep.PowerRamp -> {
                                ImportedErgoWorkoutStep.PowerRamp(
                                    stepIndex = step.stepIndex,
                                    startOffsetSec = step.startOffsetSec,
                                    durationSec = step.durationSec,
                                    fromWatts = step.fromWatts,
                                    toWatts = step.toWatts,
                                )
                            }

                            is CompiledErgoWorkoutStep.HeartRateSteady -> {
                                ImportedErgoWorkoutStep.HeartRateSteady(
                                    stepIndex = step.stepIndex,
                                    startOffsetSec = step.startOffsetSec,
                                    durationSec = step.durationSec,
                                    lowBpm = step.lowBpm,
                                    highBpm = step.highBpm,
                                    initialPowerWatts = step.initialPowerWatts,
                                    minPowerWatts = step.minPowerWatts,
                                    maxPowerWatts = step.maxPowerWatts,
                                    signalLossPowerWatts = step.signalLossPowerWatts,
                                )
                            }
                        }
                    },
                    totalDurationSec = result.compiled.totalDurationSec,
                )
                WorkoutImportResult.Success(
                    format = WorkoutImportFormat.ERGO_WORKOUT_JSON,
                    payload = WorkoutImportPayload.ErgoWorkout(workout = workout),
                )
            }

            is ErgoWorkoutParseResult.Failure -> WorkoutImportResult.Failure(
                WorkoutImportError(
                    code = WorkoutImportErrorCode.PARSE_FAILED,
                    message = "ergo_workout JSON parsing failed.",
                    detectedFormat = WorkoutImportFormat.ERGO_WORKOUT_JSON,
                    technicalDetails = formatErgoValidationDetails(result.error),
                ),
            )
        }
    }

    private fun detectFormat(sourceName: String?, trimmedContent: String): WorkoutImportFormat {
        val extension = sourceName
            ?.substringAfterLast('.', missingDelimiterValue = "")
            ?.lowercase()

        if (extension == "zwo" || extension == "xml") {
            return WorkoutImportFormat.ZWO_XML
        }
        if (extension == "ewo") {
            return sniffJsonFormat(trimmedContent) ?: WorkoutImportFormat.UNKNOWN
        }
        if (extension == "json") {
            return sniffJsonFormat(trimmedContent) ?: WorkoutImportFormat.MYWHOOSH_JSON
        }

        if (trimmedContent.startsWith("<")) {
            return WorkoutImportFormat.ZWO_XML
        }
        if (trimmedContent.startsWith("{") || trimmedContent.startsWith("[")) {
            return sniffJsonFormat(trimmedContent) ?: WorkoutImportFormat.MYWHOOSH_JSON
        }
        return WorkoutImportFormat.UNKNOWN
    }

    private fun sniffJsonFormat(trimmedContent: String): WorkoutImportFormat? {
        val rootObject = runCatching {
            sniffingJson.parseToJsonElement(trimmedContent) as? JsonObject
        }.getOrNull() ?: return null

        return when (rootObject["format"]?.jsonPrimitive?.contentOrNull) {
            "ewo" -> WorkoutImportFormat.CANONICAL_EWO_JSON
            "ergo_workout" -> WorkoutImportFormat.ERGO_WORKOUT_JSON
            else -> null
        }
    }

    private fun formatEwoValidationDetails(error: EwoWorkoutValidationError): String {
        return "${error.code.stableCode} at ${error.fieldPath}: ${error.message}"
    }

    private fun unsupportedFreeRideFailure(format: WorkoutImportFormat): WorkoutImportResult.Failure {
        return WorkoutImportResult.Failure(
            WorkoutImportError(
                code = WorkoutImportErrorCode.UNSUPPORTED_SEGMENT,
                message = "Workout contains free-ride segments, which Ewoc does not currently import.",
                detectedFormat = format,
                technicalDetails = "unsupported_segment: free_ride",
            ),
        )
    }

    private fun formatErgoValidationDetails(error: ErgoWorkoutValidationError): String {
        return "${error.code.stableCode} at ${error.fieldPath}: ${error.message}"
    }

    private fun mapCanonicalStep(step: CompiledEwoWorkoutStep): ImportedErgoWorkoutStep {
        val canonicalMetadata = ImportedErgoWorkoutStepCanonicalMetadata(
            messages = step.messages.map(::mapCanonicalMessage),
            origin = ImportedErgoWorkoutStepOrigin(
                sourceSegmentId = step.origin.sourceSegmentId,
                sourceSegmentLabel = step.origin.sourceSegmentLabel,
                sourceSegmentNote = step.origin.sourceSegmentNote,
                enclosingRepeatSegmentId = step.origin.enclosingRepeatSegmentId,
                repeatIterationIndex = step.origin.repeatIterationIndex,
            ),
        )
        return when (step) {
            is CompiledEwoWorkoutStep.PowerSteady -> ImportedErgoWorkoutStep.PowerSteady(
                stepIndex = step.stepIndex,
                startOffsetSec = step.startOffsetSec,
                durationSec = step.durationSec,
                watts = step.watts,
                cadence = step.cadence?.let { ImportedErgoWorkoutCadenceRange(it.low, it.high) },
                canonicalMetadata = canonicalMetadata,
            )

            is CompiledEwoWorkoutStep.PowerRamp -> ImportedErgoWorkoutStep.PowerRamp(
                stepIndex = step.stepIndex,
                startOffsetSec = step.startOffsetSec,
                durationSec = step.durationSec,
                fromWatts = step.fromWatts,
                toWatts = step.toWatts,
                cadence = step.cadence?.let { ImportedErgoWorkoutCadenceRange(it.low, it.high) },
                canonicalMetadata = canonicalMetadata,
            )

            is CompiledEwoWorkoutStep.HeartRateSteady -> ImportedErgoWorkoutStep.HeartRateSteady(
                stepIndex = step.stepIndex,
                startOffsetSec = step.startOffsetSec,
                durationSec = step.durationSec,
                lowBpm = step.lowBpm,
                highBpm = step.highBpm,
                initialPowerWatts = step.initialPowerWatts,
                minPowerWatts = step.minPowerWatts,
                maxPowerWatts = step.maxPowerWatts,
                signalLossPowerWatts = step.signalLossPowerWatts,
                cadence = step.cadence?.let { ImportedErgoWorkoutCadenceRange(it.low, it.high) },
                canonicalMetadata = canonicalMetadata,
            )

            is CompiledEwoWorkoutStep.FreeRide -> ImportedErgoWorkoutStep.FreeRide(
                stepIndex = step.stepIndex,
                startOffsetSec = step.startOffsetSec,
                durationSec = step.durationSec,
                cadence = step.cadence?.let { ImportedErgoWorkoutCadenceRange(it.low, it.high) },
                canonicalMetadata = canonicalMetadata,
            )
        }
    }

    private fun mapCanonicalControl(control: EwoWorkoutControl): ImportedErgoWorkoutControl {
        return ImportedErgoWorkoutControl(
            initialPowerWatts = control.initialPowerWatts,
            minPowerWatts = control.minPowerWatts,
            maxPowerWatts = control.maxPowerWatts,
            signalLossPowerWatts = control.signalLossPowerWatts,
            hrUpperCapBpm = control.hrUpperCapBpm,
        )
    }

    private fun mapDifficulty(difficulty: EwoDifficulty): ImportedErgoWorkoutDifficulty = when (difficulty) {
        EwoDifficulty.EASY -> ImportedErgoWorkoutDifficulty.EASY
        EwoDifficulty.MODERATE -> ImportedErgoWorkoutDifficulty.MODERATE
        EwoDifficulty.HARD -> ImportedErgoWorkoutDifficulty.HARD
        EwoDifficulty.VERY_HARD -> ImportedErgoWorkoutDifficulty.VERY_HARD
    }

    private fun mapSanityIssue(issue: SanityIssue): ImportedSanityIssue = ImportedSanityIssue(
        code = issue.code.stableCode,
        severity = when (issue.severity) {
            SanitySeverity.WARNING -> ImportedSanityIssueSeverity.WARNING
            SanitySeverity.ERROR -> ImportedSanityIssueSeverity.ERROR
        },
        message = issue.message,
        segmentId = issue.segmentId,
    )

    private fun mapCanonicalMessage(message: EwoMessage): ImportedErgoWorkoutMessage {
        return ImportedErgoWorkoutMessage(
            kind = when (message.kind) {
                EwoMessageKind.INTRO -> ImportedErgoWorkoutMessageKind.INTRO
                EwoMessageKind.INSTRUCTION -> ImportedErgoWorkoutMessageKind.INSTRUCTION
                EwoMessageKind.TRANSITION -> ImportedErgoWorkoutMessageKind.TRANSITION
                EwoMessageKind.WARNING -> ImportedErgoWorkoutMessageKind.WARNING
                EwoMessageKind.MOTIVATION -> ImportedErgoWorkoutMessageKind.MOTIVATION
            },
            timing = ImportedErgoWorkoutMessageTiming(
                anchor = when (message.timing.anchor) {
                    EwoMessageAnchor.START -> ImportedErgoWorkoutMessageAnchor.START
                    EwoMessageAnchor.END -> ImportedErgoWorkoutMessageAnchor.END
                },
                offsetSec = message.timing.offsetSec,
            ),
            text = ImportedErgoWorkoutLocalizedText(
                defaultText = message.text.defaultText,
                translations = message.text.translations,
            ),
        )
    }

    private fun mapLocalizedText(text: com.ewo.core.EwoLocalizedText): ImportedErgoWorkoutLocalizedText {
        return ImportedErgoWorkoutLocalizedText(
            defaultText = text.defaultText,
            translations = text.translations,
        )
    }
}

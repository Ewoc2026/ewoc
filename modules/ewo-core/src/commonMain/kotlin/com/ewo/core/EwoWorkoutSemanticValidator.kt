package com.ewo.core

/**
 * Enforces canonical `.ewo` invariants that depend on relationships between authored fields rather
 * than raw JSON shape.
 */
internal object EwoWorkoutSemanticValidator {
    private const val rootPath = "$"
    private const val canonicalFormat = "ewo"
    private val supportedVersions = setOf("1.0", "1.1", "1.2", "1.3", "1.4", "1.5", "1.6")
    private val ftpPercentMinVersion = EwoVersion(1, 1)
    private val cadenceMinVersion = EwoVersion(1, 2)
    private val difficultyTagsMinVersion = EwoVersion(1, 3)
    private val hrRelativeMinVersion = EwoVersion(1, 4)
    private val schemaV15MinVersion = EwoVersion(1, 5)
    private val rootLocalizedMetadataMinVersion = EwoVersion(1, 6)
    private const val minHeartRateBpm = 40
    private const val maxHeartRateBpm = 220
    private const val CADENCE_MIN = 30
    private const val CADENCE_MAX = 200

    fun validate(parsed: ParsedEwoWorkoutFile): NormalizedEwoWorkout {
        if (parsed.format != canonicalFormat) {
            failEwoValidation(
                code = EwoWorkoutValidationErrorCode.INVALID_FORMAT,
                message = "format must be '$canonicalFormat'.",
                fieldPath = ewoChildPath(rootPath, "format"),
            )
        }
        if (parsed.version !in supportedVersions) {
            failEwoValidation(
                code = EwoWorkoutValidationErrorCode.UNSUPPORTED_VERSION,
                message = "Unsupported version '${parsed.version}'. Supported: ${supportedVersions.sorted().joinToString()}.",
                fieldPath = ewoChildPath(rootPath, "version"),
            )
        }
        val version = EwoVersion.parse(parsed.version)
        val allowFtpPercent = version >= ftpPercentMinVersion
        val allowCadence = version >= cadenceMinVersion
        val allowDifficultyTags = version >= difficultyTagsMinVersion
        val allowV15Metadata = version >= schemaV15MinVersion

        val normalizedUid = normalizeDocumentUid(parsed.uid, allowV15Metadata)
        val normalizedRevision = normalizeDocumentRevision(parsed.revision, allowV15Metadata)

        if (parsed.difficulty != null && !allowDifficultyTags) {
            failEwoValidation(
                code = EwoWorkoutValidationErrorCode.UNKNOWN_FIELD,
                message = "difficulty requires version $difficultyTagsMinVersion or later.",
                fieldPath = ewoChildPath(rootPath, "difficulty"),
            )
        }
        if (parsed.tags.isNotEmpty() && !allowDifficultyTags) {
            failEwoValidation(
                code = EwoWorkoutValidationErrorCode.UNKNOWN_FIELD,
                message = "tags requires version $difficultyTagsMinVersion or later.",
                fieldPath = ewoChildPath(rootPath, "tags"),
            )
        }

        val normalizedTitle = parsed.title.trim().takeIf { it.isNotEmpty() }
            ?: failEwoValidation(
                code = EwoWorkoutValidationErrorCode.EMPTY_TITLE,
                message = "title must not be blank.",
                fieldPath = ewoChildPath(rootPath, "title"),
            )
        val normalizedDescription = parsed.description?.trim()?.takeIf { it.isNotEmpty() }
        val normalizedTitleLocalized = normalizeRootLocalizedText(
            localizedText = parsed.titleLocalized,
            path = ewoChildPath(rootPath, "title_localized"),
            version = version,
        )?.also { localized ->
            if (localized.defaultText != normalizedTitle) {
                failEwoValidation(
                    code = EwoWorkoutValidationErrorCode.INVALID_LOCALIZED_TEXT,
                    message = "title_localized.default must match title after trimming normalization.",
                    fieldPath = ewoChildPath(ewoChildPath(rootPath, "title_localized"), "default"),
                )
            }
        }
        val normalizedDescriptionLocalized = normalizeRootLocalizedText(
            localizedText = parsed.descriptionLocalized,
            path = ewoChildPath(rootPath, "description_localized"),
            version = version,
        )?.also { localized ->
            val description = normalizedDescription ?: failEwoValidation(
                code = EwoWorkoutValidationErrorCode.INVALID_LOCALIZED_TEXT,
                message = "description_localized requires root description to be present.",
                fieldPath = ewoChildPath(rootPath, "description_localized"),
            )
            if (localized.defaultText != description) {
                failEwoValidation(
                    code = EwoWorkoutValidationErrorCode.INVALID_LOCALIZED_TEXT,
                    message = "description_localized.default must match description after trimming normalization.",
                    fieldPath = ewoChildPath(ewoChildPath(rootPath, "description_localized"), "default"),
                )
            }
        }

        if (parsed.segments.isEmpty()) {
            failEwoValidation(
                code = EwoWorkoutValidationErrorCode.EMPTY_SEGMENTS,
                message = "segments must contain at least one segment.",
                fieldPath = ewoChildPath(rootPath, "segments"),
            )
        }

        val seenIds = linkedSetOf<String>()
        var requiresHeartRateControl = false
        val normalizedSegments = parsed.segments.mapIndexed { index, segment ->
            val normalized = normalizeSegment(
                segment = segment,
                path = ewoIndexPath(ewoChildPath(rootPath, "segments"), index),
                seenIds = seenIds,
                allowFtpPercent = allowFtpPercent,
                allowCadence = allowCadence,
                version = version,
            )
            requiresHeartRateControl = requiresHeartRateControl || normalized.requiresHeartRateControl
            normalized.value
        }

        val normalizedMessages = parsed.messages.mapIndexed { index, message ->
            normalizeMessage(
                message = message,
                path = ewoIndexPath(ewoChildPath(rootPath, "messages"), index),
                version = version,
            )
        }

        val control = when {
            requiresHeartRateControl -> normalizeControl(
                control = parsed.control ?: failEwoValidation(
                    code = EwoWorkoutValidationErrorCode.CONTROL_REQUIRED_FOR_HEART_RATE,
                    message = "control is required when any segment targets heart rate.",
                    fieldPath = ewoChildPath(rootPath, "control"),
                ),
            )
            parsed.control != null -> normalizeControl(parsed.control)
            else -> null
        }

        return NormalizedEwoWorkout(
            uid = normalizedUid,
            revision = normalizedRevision,
            title = normalizedTitle,
            description = normalizedDescription,
            titleLocalized = normalizedTitleLocalized,
            descriptionLocalized = normalizedDescriptionLocalized,
            difficulty = parsed.difficulty?.let { normalizeDifficulty(it) },
            tags = parsed.tags,
            control = control,
            messages = normalizedMessages,
            segments = normalizedSegments,
        )
    }

    private fun normalizeControl(control: ParsedEwoWorkoutControl): EwoWorkoutControl {
        if (
            control.minPowerWatts <= 0 ||
            control.maxPowerWatts <= 0 ||
            control.minPowerWatts >= control.maxPowerWatts
        ) {
            failEwoValidation(
                code = EwoWorkoutValidationErrorCode.INVALID_CONTROL_BOUNDS,
                message = "Control bounds must be positive and min_power_watts must be less than max_power_watts.",
                fieldPath = ewoChildPath(rootPath, "control"),
            )
        }
        if (control.initialPowerWatts !in control.minPowerWatts..control.maxPowerWatts) {
            failEwoValidation(
                code = EwoWorkoutValidationErrorCode.CONTROL_VALUE_OUT_OF_BOUNDS,
                message = "initial_power_watts must stay within min_power_watts and max_power_watts.",
                fieldPath = ewoChildPath(ewoChildPath(rootPath, "control"), "initial_power_watts"),
            )
        }
        if (control.signalLossPowerWatts !in control.minPowerWatts..control.maxPowerWatts) {
            failEwoValidation(
                code = EwoWorkoutValidationErrorCode.CONTROL_VALUE_OUT_OF_BOUNDS,
                message = "signal_loss_power_watts must stay within min_power_watts and max_power_watts.",
                fieldPath = ewoChildPath(ewoChildPath(rootPath, "control"), "signal_loss_power_watts"),
            )
        }
        if (control.hrUpperCapBpm !in minHeartRateBpm..maxHeartRateBpm) {
            failEwoValidation(
                code = EwoWorkoutValidationErrorCode.INVALID_HR_UPPER_CAP_BPM,
                message = "hr_upper_cap_bpm must stay within 40..220.",
                fieldPath = ewoChildPath(ewoChildPath(rootPath, "control"), "hr_upper_cap_bpm"),
            )
        }

        return EwoWorkoutControl(
            initialPowerWatts = control.initialPowerWatts,
            minPowerWatts = control.minPowerWatts,
            maxPowerWatts = control.maxPowerWatts,
            signalLossPowerWatts = control.signalLossPowerWatts,
            hrUpperCapBpm = control.hrUpperCapBpm,
        )
    }

    private fun normalizeSegment(
        segment: ParsedEwoSegment,
        path: String,
        seenIds: MutableSet<String>,
        allowFtpPercent: Boolean,
        allowCadence: Boolean,
        version: EwoVersion,
    ): Normalization<NormalizedEwoWorkoutSegment> {
        registerSegmentId(segment.id, ewoChildPath(path, "id"), seenIds)
        val metadata = normalizeSegmentMetadata(segment, path, version)
        return when (segment) {
            is ParsedEwoSegment.Steady -> {
                val normalizedMessages = normalizeMessages(
                    messages = segment.messages,
                    path = ewoChildPath(path, "messages"),
                    version = version,
                )
                val cadence = normalizeCadence(segment.cadence, ewoChildPath(path, "cadence"), allowCadence)
                val steady = normalizeSteadySpec(
                    durationSec = segment.durationSec,
                    target = segment.target,
                    path = path,
                    allowFtpPercent = allowFtpPercent,
                    version = version,
                )
                when (val spec = steady.value) {
                    is NormalizedSteadySpec.Power -> Normalization(
                        value = NormalizedEwoWorkoutSegment.PowerSteady(
                            id = segment.id,
                            label = metadata.label,
                            note = metadata.note,
                            messages = normalizedMessages,
                            durationSec = spec.durationSec,
                            watts = spec.watts,
                            cadence = cadence,
                        ),
                        requiresHeartRateControl = false,
                    )

                    is NormalizedSteadySpec.FtpPercent -> Normalization(
                        value = NormalizedEwoWorkoutSegment.FtpPercentSteady(
                            id = segment.id,
                            label = metadata.label,
                            note = metadata.note,
                            messages = normalizedMessages,
                            durationSec = spec.durationSec,
                            fraction = spec.fraction,
                            cadence = cadence,
                        ),
                        requiresHeartRateControl = false,
                    )

                    is NormalizedSteadySpec.HeartRate -> Normalization(
                        value = NormalizedEwoWorkoutSegment.HeartRateSteady(
                            id = segment.id,
                            label = metadata.label,
                            note = metadata.note,
                            messages = normalizedMessages,
                            durationSec = spec.durationSec,
                            lowBpm = spec.lowBpm,
                            highBpm = spec.highBpm,
                            cadence = cadence,
                        ),
                        requiresHeartRateControl = true,
                    )

                    is NormalizedSteadySpec.HeartRateRelative -> Normalization(
                        value = NormalizedEwoWorkoutSegment.HeartRateRelativeSteady(
                            id = segment.id,
                            label = metadata.label,
                            note = metadata.note,
                            messages = normalizedMessages,
                            durationSec = spec.durationSec,
                            reference = spec.reference,
                            lowFraction = spec.lowFraction,
                            highFraction = spec.highFraction,
                            cadence = cadence,
                        ),
                        requiresHeartRateControl = true,
                    )
                }
            }

            is ParsedEwoSegment.Ramp -> {
                val normalizedMessages = normalizeMessages(
                    messages = segment.messages,
                    path = ewoChildPath(path, "messages"),
                    version = version,
                )
                val durationSec = normalizeDuration(segment.durationSec, ewoChildPath(path, "duration_sec"))
                val cadence = normalizeCadence(segment.cadence, ewoChildPath(path, "cadence"), allowCadence)
                when (val from = segment.fromTarget) {
                    is ParsedEwoTarget.Power -> {
                        val to = segment.toTarget as ParsedEwoTarget.Power
                        Normalization(
                            value = NormalizedEwoWorkoutSegment.PowerRamp(
                                id = segment.id,
                                label = metadata.label,
                                note = metadata.note,
                                messages = normalizedMessages,
                                durationSec = durationSec,
                                fromWatts = normalizePowerValue(
                                    from.value,
                                    ewoChildPath(ewoChildPath(path, "from_target"), "value"),
                                ),
                                toWatts = normalizePowerValue(
                                    to.value,
                                    ewoChildPath(ewoChildPath(path, "to_target"), "value"),
                                ),
                                cadence = cadence,
                            ),
                            requiresHeartRateControl = false,
                        )
                    }

                    is ParsedEwoTarget.FtpPercent -> {
                        if (!allowFtpPercent) {
                            failEwoValidation(
                                code = EwoWorkoutValidationErrorCode.INVALID_TARGET_METRIC,
                                message = "ftp_percent targets require version $ftpPercentMinVersion or later.",
                                fieldPath = ewoChildPath(ewoChildPath(path, "from_target"), "metric"),
                            )
                        }
                        val to = segment.toTarget as ParsedEwoTarget.FtpPercent
                        Normalization(
                            value = NormalizedEwoWorkoutSegment.FtpPercentRamp(
                                id = segment.id,
                                label = metadata.label,
                                note = metadata.note,
                                messages = normalizedMessages,
                                durationSec = durationSec,
                                fromFraction = from.fraction,
                                toFraction = to.fraction,
                                cadence = cadence,
                            ),
                            requiresHeartRateControl = false,
                        )
                    }

                    is ParsedEwoTarget.HeartRateRange -> failEwoValidation(
                        code = EwoWorkoutValidationErrorCode.RAMP_INVALID_TARGET_METRIC,
                        message = "Ramp targets must use 'power' or 'ftp_percent'.",
                        fieldPath = ewoChildPath(ewoChildPath(path, "from_target"), "metric"),
                    )

                    is ParsedEwoTarget.HeartRateRelativeRange -> failEwoValidation(
                        code = EwoWorkoutValidationErrorCode.RAMP_INVALID_TARGET_METRIC,
                        message = "Ramp targets must use 'power' or 'ftp_percent'.",
                        fieldPath = ewoChildPath(ewoChildPath(path, "from_target"), "metric"),
                    )
                }
            }

            is ParsedEwoSegment.FreeRide -> {
                if (version < schemaV15MinVersion) {
                    failEwoValidation(
                        code = EwoWorkoutValidationErrorCode.UNKNOWN_FIELD,
                        message = "free_ride segments require version $schemaV15MinVersion or later.",
                        fieldPath = ewoChildPath(path, "type"),
                    )
                }
                Normalization(
                    value = NormalizedEwoWorkoutSegment.FreeRide(
                        id = segment.id,
                        label = metadata.label,
                        note = metadata.note,
                        messages = normalizeMessages(
                            messages = segment.messages,
                            path = ewoChildPath(path, "messages"),
                            version = version,
                        ),
                        durationSec = normalizeDuration(segment.durationSec, ewoChildPath(path, "duration_sec")),
                        cadence = normalizeCadence(segment.cadence, ewoChildPath(path, "cadence"), allowCadence),
                    ),
                    requiresHeartRateControl = false,
                )
            }

            is ParsedEwoSegment.Repeat -> {
                val count = if (segment.count >= 2) {
                    segment.count
                } else {
                    failEwoValidation(
                        code = EwoWorkoutValidationErrorCode.INVALID_REPEAT_COUNT,
                        message = "count must be an integer >= 2.",
                        fieldPath = ewoChildPath(path, "count"),
                    )
                }
                if (segment.segments.size < 2) {
                    failEwoValidation(
                        code = EwoWorkoutValidationErrorCode.REPEAT_SEGMENTS_TOO_SHORT,
                        message = "Repeat segments must contain at least two steady segments.",
                        fieldPath = ewoChildPath(path, "segments"),
                    )
                }
                var requiresHeartRateControl = false
                val normalizedChildren = segment.segments.mapIndexed { index, child ->
                    if (child !is ParsedEwoSegment.Steady && child !is ParsedEwoSegment.FreeRide) {
                        failEwoValidation(
                            code = EwoWorkoutValidationErrorCode.REPEAT_CHILD_TYPE_NOT_ALLOWED,
                            message = "Repeat segments may contain only steady or free_ride segments in canonical v1.",
                            fieldPath = ewoChildPath(
                                ewoIndexPath(ewoChildPath(path, "segments"), index),
                                "type",
                            ),
                        )
                    }
                    registerSegmentId(
                        id = child.id,
                        path = ewoChildPath(ewoIndexPath(ewoChildPath(path, "segments"), index), "id"),
                        seenIds = seenIds,
                    )
                    val normalized = normalizeRepeatChild(
                        segment = child,
                        path = ewoIndexPath(ewoChildPath(path, "segments"), index),
                        allowFtpPercent = allowFtpPercent,
                        allowCadence = allowCadence,
                        version = version,
                    )
                    requiresHeartRateControl = requiresHeartRateControl || normalized.requiresHeartRateControl
                    normalized.value
                }

                Normalization(
                    value = NormalizedEwoWorkoutSegment.Repeat(
                        id = segment.id,
                        label = metadata.label,
                        note = metadata.note,
                        messages = normalizeMessages(
                            messages = segment.messages,
                            path = ewoChildPath(path, "messages"),
                            version = version,
                        ),
                        count = count,
                        segments = normalizedChildren,
                    ),
                    requiresHeartRateControl = requiresHeartRateControl,
                )
            }
        }
    }

    private fun normalizeRepeatChild(
        segment: ParsedEwoSegment,
        path: String,
        allowFtpPercent: Boolean,
        allowCadence: Boolean,
        version: EwoVersion,
    ): Normalization<NormalizedEwoWorkoutRepeatSegment> {
        val metadata = normalizeSegmentMetadata(segment, path, version)
        return when (segment) {
            is ParsedEwoSegment.Steady -> {
                val normalizedMessages = normalizeMessages(
                    messages = segment.messages,
                    path = ewoChildPath(path, "messages"),
                    version = version,
                )
                val cadence = normalizeCadence(segment.cadence, ewoChildPath(path, "cadence"), allowCadence)
                val steady = normalizeSteadySpec(
                    durationSec = segment.durationSec,
                    target = segment.target,
                    path = path,
                    allowFtpPercent = allowFtpPercent,
                    version = version,
                )
                when (val spec = steady.value) {
                    is NormalizedSteadySpec.Power -> Normalization(
                        value = NormalizedEwoWorkoutRepeatSegment.PowerSteady(
                            id = segment.id,
                            label = metadata.label,
                            note = metadata.note,
                            messages = normalizedMessages,
                            durationSec = spec.durationSec,
                            watts = spec.watts,
                            cadence = cadence,
                        ),
                        requiresHeartRateControl = false,
                    )

                    is NormalizedSteadySpec.FtpPercent -> Normalization(
                        value = NormalizedEwoWorkoutRepeatSegment.FtpPercentSteady(
                            id = segment.id,
                            label = metadata.label,
                            note = metadata.note,
                            messages = normalizedMessages,
                            durationSec = spec.durationSec,
                            fraction = spec.fraction,
                            cadence = cadence,
                        ),
                        requiresHeartRateControl = false,
                    )

                    is NormalizedSteadySpec.HeartRate -> Normalization(
                        value = NormalizedEwoWorkoutRepeatSegment.HeartRateSteady(
                            id = segment.id,
                            label = metadata.label,
                            note = metadata.note,
                            messages = normalizedMessages,
                            durationSec = spec.durationSec,
                            lowBpm = spec.lowBpm,
                            highBpm = spec.highBpm,
                            cadence = cadence,
                        ),
                        requiresHeartRateControl = true,
                    )

                    is NormalizedSteadySpec.HeartRateRelative -> Normalization(
                        value = NormalizedEwoWorkoutRepeatSegment.HeartRateRelativeSteady(
                            id = segment.id,
                            label = metadata.label,
                            note = metadata.note,
                            messages = normalizedMessages,
                            durationSec = spec.durationSec,
                            reference = spec.reference,
                            lowFraction = spec.lowFraction,
                            highFraction = spec.highFraction,
                            cadence = cadence,
                        ),
                        requiresHeartRateControl = true,
                    )
                }
            }

            is ParsedEwoSegment.FreeRide -> {
                if (version < schemaV15MinVersion) {
                    failEwoValidation(
                        code = EwoWorkoutValidationErrorCode.UNKNOWN_FIELD,
                        message = "free_ride segments require version $schemaV15MinVersion or later.",
                        fieldPath = ewoChildPath(path, "type"),
                    )
                }
                Normalization(
                    value = NormalizedEwoWorkoutRepeatSegment.FreeRide(
                        id = segment.id,
                        label = metadata.label,
                        note = metadata.note,
                        messages = normalizeMessages(
                            messages = segment.messages,
                            path = ewoChildPath(path, "messages"),
                            version = version,
                        ),
                        durationSec = normalizeDuration(segment.durationSec, ewoChildPath(path, "duration_sec")),
                        cadence = normalizeCadence(segment.cadence, ewoChildPath(path, "cadence"), allowCadence),
                    ),
                    requiresHeartRateControl = false,
                )
            }

            is ParsedEwoSegment.Ramp,
            is ParsedEwoSegment.Repeat -> throw IllegalStateException("Repeat children are validated earlier.")
        }
    }

    private fun normalizeSteadySpec(
        durationSec: Int,
        target: ParsedEwoTarget,
        path: String,
        allowFtpPercent: Boolean,
        version: EwoVersion,
    ): Normalization<NormalizedSteadySpec> {
        val normalizedDuration = normalizeDuration(durationSec, ewoChildPath(path, "duration_sec"))
        return when (target) {
            is ParsedEwoTarget.Power -> Normalization(
                value = NormalizedSteadySpec.Power(
                    durationSec = normalizedDuration,
                    watts = normalizePowerValue(target.value, ewoChildPath(ewoChildPath(path, "target"), "value")),
                ),
                requiresHeartRateControl = false,
            )

            is ParsedEwoTarget.FtpPercent -> {
                if (!allowFtpPercent) {
                    failEwoValidation(
                        code = EwoWorkoutValidationErrorCode.INVALID_TARGET_METRIC,
                        message = "ftp_percent targets require version $ftpPercentMinVersion or later.",
                        fieldPath = ewoChildPath(ewoChildPath(path, "target"), "metric"),
                    )
                }
                Normalization(
                    value = NormalizedSteadySpec.FtpPercent(
                        durationSec = normalizedDuration,
                        fraction = target.fraction,
                    ),
                    requiresHeartRateControl = false,
                )
            }

            is ParsedEwoTarget.HeartRateRange -> {
                val range = normalizeHeartRateRange(
                    low = target.low,
                    high = target.high,
                    path = ewoChildPath(ewoChildPath(path, "target"), "range"),
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

            is ParsedEwoTarget.HeartRateRelativeRange -> {
                if (version < hrRelativeMinVersion) {
                    failEwoValidation(
                        code = EwoWorkoutValidationErrorCode.INVALID_TARGET_METRIC,
                        message = "heart_rate_relative targets require version $hrRelativeMinVersion or later.",
                        fieldPath = ewoChildPath(ewoChildPath(path, "target"), "metric"),
                    )
                }
                val reference = HrReference.fromCode(target.reference) ?: failEwoValidation(
                    code = EwoWorkoutValidationErrorCode.INVALID_HR_RELATIVE_REFERENCE,
                    message = "Unknown heart_rate_relative reference '${target.reference}'. " +
                        "Must be one of: ${HrReference.entries.joinToString { it.stableCode }}.",
                    fieldPath = ewoChildPath(ewoChildPath(path, "target"), "reference"),
                )
                if (target.low <= 0 || target.low >= target.high) {
                    failEwoValidation(
                        code = EwoWorkoutValidationErrorCode.INVALID_HR_RELATIVE_RANGE,
                        message = "heart_rate_relative range must satisfy 0 < low < high.",
                        fieldPath = ewoChildPath(ewoChildPath(path, "target"), "range"),
                    )
                }
                Normalization(
                    value = NormalizedSteadySpec.HeartRateRelative(
                        durationSec = normalizedDuration,
                        reference = reference,
                        lowFraction = target.low,
                        highFraction = target.high,
                    ),
                    requiresHeartRateControl = true,
                )
            }
        }
    }

    private fun normalizeMessages(
        messages: List<ParsedEwoMessage>,
        path: String,
        version: EwoVersion,
    ): List<EwoMessage> {
        return messages.mapIndexed { index, message ->
            normalizeMessage(message, ewoIndexPath(path, index), version)
        }
    }

    private fun normalizeMessage(message: ParsedEwoMessage, path: String, version: EwoVersion): EwoMessage {
        return EwoMessage(
            kind = when (message.kind) {
                "intro" -> EwoMessageKind.INTRO
                "instruction" -> EwoMessageKind.INSTRUCTION
                "transition" -> EwoMessageKind.TRANSITION
                "warning" -> EwoMessageKind.WARNING
                "motivation" -> EwoMessageKind.MOTIVATION
                else -> throw IllegalStateException("Schema validator must reject unsupported kinds.")
            },
            timing = normalizeMessageTiming(message.timing, path, version),
            text = normalizeLocalizedText(message.text, ewoChildPath(path, "text")),
        )
    }

    private fun normalizeRootLocalizedText(
        localizedText: ParsedEwoLocalizedText?,
        path: String,
        version: EwoVersion,
    ): EwoLocalizedText? {
        localizedText ?: return null
        if (version < rootLocalizedMetadataMinVersion) {
            failEwoValidation(
                code = EwoWorkoutValidationErrorCode.UNKNOWN_FIELD,
                message = "Root localized metadata requires version $rootLocalizedMetadataMinVersion or later.",
                fieldPath = path,
            )
        }
        return normalizeLocalizedText(localizedText, path)
    }

    private fun normalizeLocalizedText(text: ParsedEwoLocalizedText, path: String): EwoLocalizedText {
        val defaultText = text.defaultText.trim().takeIf { it.isNotEmpty() }
            ?: failEwoValidation(
                code = EwoWorkoutValidationErrorCode.INVALID_LOCALIZED_TEXT,
                message = "text.default must not be blank.",
                fieldPath = ewoChildPath(path, "default"),
            )
        val normalizedTranslations = text.translations.mapValues { (_, value) ->
            value.trim().takeIf { it.isNotEmpty() } ?: failEwoValidation(
                code = EwoWorkoutValidationErrorCode.INVALID_LOCALIZED_TEXT,
                message = "translations values must not be blank.",
                fieldPath = ewoChildPath(path, "translations"),
            )
        }
        return EwoLocalizedText(
            defaultText = defaultText,
            translations = normalizedTranslations,
        )
    }

    private fun normalizeDifficulty(value: String): EwoDifficulty = when (value) {
        "easy" -> EwoDifficulty.EASY
        "moderate" -> EwoDifficulty.MODERATE
        "hard" -> EwoDifficulty.HARD
        "very_hard" -> EwoDifficulty.VERY_HARD
        else -> throw IllegalStateException("Schema validator must reject unknown difficulty values.")
    }

    private fun normalizeCadence(
        cadence: ParsedEwoCadenceRange?,
        path: String,
        allowCadence: Boolean,
    ): EwoCadenceRange? {
        cadence ?: return null
        if (!allowCadence) {
            failEwoValidation(
                code = EwoWorkoutValidationErrorCode.UNKNOWN_FIELD,
                message = "cadence requires version $cadenceMinVersion or later.",
                fieldPath = path,
            )
        }
        if (cadence.low < CADENCE_MIN || cadence.high > CADENCE_MAX || cadence.low > cadence.high) {
            failEwoValidation(
                code = EwoWorkoutValidationErrorCode.INVALID_CADENCE_RANGE,
                message = "Cadence range must satisfy $CADENCE_MIN <= low <= high <= $CADENCE_MAX.",
                fieldPath = path,
            )
        }
        return EwoCadenceRange(low = cadence.low, high = cadence.high)
    }

    private fun normalizeDocumentUid(uid: String?, allowV15Metadata: Boolean): String? {
        uid ?: return null
        if (!allowV15Metadata) {
            failEwoValidation(
                code = EwoWorkoutValidationErrorCode.UNKNOWN_FIELD,
                message = "uid requires version $schemaV15MinVersion or later.",
                fieldPath = ewoChildPath(rootPath, "uid"),
            )
        }
        return uid.trim().takeIf { it.isNotEmpty() } ?: failEwoValidation(
            code = EwoWorkoutValidationErrorCode.INVALID_FORMAT,
            message = "uid must not be blank.",
            fieldPath = ewoChildPath(rootPath, "uid"),
        )
    }

    private fun normalizeDocumentRevision(revision: Int?, allowV15Metadata: Boolean): Int? {
        revision ?: return null
        if (!allowV15Metadata) {
            failEwoValidation(
                code = EwoWorkoutValidationErrorCode.UNKNOWN_FIELD,
                message = "revision requires version $schemaV15MinVersion or later.",
                fieldPath = ewoChildPath(rootPath, "revision"),
            )
        }
        return revision.takeIf { it > 0 } ?: failEwoValidation(
            code = EwoWorkoutValidationErrorCode.INVALID_TYPE,
            message = "revision must be a positive integer.",
            fieldPath = ewoChildPath(rootPath, "revision"),
        )
    }

    private fun normalizeSegmentMetadata(
        segment: ParsedEwoSegment,
        path: String,
        version: EwoVersion,
    ): SegmentMetadata {
        if (version < schemaV15MinVersion) {
            if (segment.label != null) {
                failEwoValidation(
                    code = EwoWorkoutValidationErrorCode.UNKNOWN_FIELD,
                    message = "label requires version $schemaV15MinVersion or later.",
                    fieldPath = ewoChildPath(path, "label"),
                )
            }
            if (segment.note != null) {
                failEwoValidation(
                    code = EwoWorkoutValidationErrorCode.UNKNOWN_FIELD,
                    message = "note requires version $schemaV15MinVersion or later.",
                    fieldPath = ewoChildPath(path, "note"),
                )
            }
        }
        return SegmentMetadata(
            label = segment.label?.trim()?.takeIf { it.isNotEmpty() },
            note = segment.note?.trim()?.takeIf { it.isNotEmpty() },
        )
    }

    private fun normalizeMessageTiming(
        timing: ParsedEwoMessageTiming,
        path: String,
        version: EwoVersion,
    ): EwoMessageTiming {
        if (version < schemaV15MinVersion && (timing.anchor != "start" || timing.offsetSec != 0)) {
            failEwoValidation(
                code = EwoWorkoutValidationErrorCode.UNKNOWN_FIELD,
                message = "Object message timing requires version $schemaV15MinVersion or later.",
                fieldPath = ewoChildPath(path, "when"),
            )
        }
        return EwoMessageTiming(
            anchor = when (timing.anchor) {
                "start" -> EwoMessageAnchor.START
                "end" -> EwoMessageAnchor.END
                else -> throw IllegalStateException("Schema validator must reject unsupported timing anchors.")
            },
            offsetSec = timing.offsetSec,
        )
    }

    private fun normalizeDuration(durationSec: Int, path: String): Int {
        return durationSec.takeIf { it > 0 } ?: failEwoValidation(
            code = EwoWorkoutValidationErrorCode.INVALID_DURATION_SEC,
            message = "duration_sec must be a positive integer.",
            fieldPath = path,
        )
    }

    private fun normalizePowerValue(value: Int, path: String): Int {
        return value.takeIf { it > 0 } ?: failEwoValidation(
            code = EwoWorkoutValidationErrorCode.INVALID_POWER_TARGET_VALUE,
            message = "Power target values must be positive integers.",
            fieldPath = path,
        )
    }

    private fun normalizeHeartRateRange(low: Int, high: Int, path: String): Pair<Int, Int> {
        if (low < minHeartRateBpm || high > maxHeartRateBpm || low >= high) {
            failEwoValidation(
                code = EwoWorkoutValidationErrorCode.INVALID_HEART_RATE_RANGE,
                message = "Heart-rate ranges must satisfy 40 <= low < high <= 220.",
                fieldPath = path,
            )
        }
        return low to high
    }

    private fun registerSegmentId(id: String, path: String, seenIds: MutableSet<String>) {
        if (!seenIds.add(id)) {
            failEwoValidation(
                code = EwoWorkoutValidationErrorCode.DUPLICATE_SEGMENT_ID,
                message = "Segment id '$id' must be unique across the whole workout.",
                fieldPath = path,
            )
        }
    }

    private data class Normalization<T>(
        val value: T,
        val requiresHeartRateControl: Boolean,
    )

    private data class SegmentMetadata(
        val label: String?,
        val note: String?,
    )

    private sealed class NormalizedSteadySpec {
        abstract val durationSec: Int

        data class Power(
            override val durationSec: Int,
            val watts: Int,
        ) : NormalizedSteadySpec()

        data class FtpPercent(
            override val durationSec: Int,
            val fraction: Double,
        ) : NormalizedSteadySpec()

        data class HeartRate(
            override val durationSec: Int,
            val lowBpm: Int,
            val highBpm: Int,
        ) : NormalizedSteadySpec()

        data class HeartRateRelative(
            override val durationSec: Int,
            val reference: HrReference,
            val lowFraction: Double,
            val highFraction: Double,
        ) : NormalizedSteadySpec()
    }
}

/**
 * Parses and compares `.ewo` version strings numerically to avoid lexicographic ordering
 * bugs (e.g. "1.10" < "1.2" in string comparison).
 */
data class EwoVersion(val major: Int, val minor: Int) : Comparable<EwoVersion> {
    override fun compareTo(other: EwoVersion): Int =
        compareValuesBy(this, other, { it.major }, { it.minor })

    override fun toString(): String = "$major.$minor"

    companion object {
        fun parse(version: String): EwoVersion {
            val parts = version.split(".")
            return EwoVersion(major = parts[0].toInt(), minor = parts[1].toInt())
        }
    }
}

package io.github.ewoc2026.ewoc

import android.content.Context
import io.github.ewoc2026.ewoc.session.export.FitExportFailureReason
import io.github.ewoc2026.ewoc.workout.WorkoutImportError
import io.github.ewoc2026.ewoc.workout.WorkoutImportErrorCode
import io.github.ewoc2026.ewoc.workout.WorkoutImportFormat
import java.util.Locale

/**
 * Stable reason-code registry for session/workout/export failures.
 *
 * Codes are treated as a contract for logging/tests and should remain stable
 * once shipped.
 */
enum class AppFailureReasonCode(val stableCode: String) {
    SESSION_CONNECT_FLOW_TIMEOUT("session.connect_flow_timeout"),
    SESSION_REQUEST_CONTROL_REJECTED("session.request_control_rejected"),
    SESSION_REQUEST_CONTROL_TIMEOUT("session.request_control_timeout"),
    SESSION_BLUETOOTH_PERMISSION_DENIED("session.bluetooth_permission_denied"),
    WORKOUT_IMPORT_READ_FAILED("workout.import_read_failed"),
    WORKOUT_IMPORT_EMPTY_CONTENT("workout.import_empty_content"),
    WORKOUT_IMPORT_UNSUPPORTED_FORMAT("workout.import_unsupported_format"),
    WORKOUT_IMPORT_UNSUPPORTED_SEGMENT("workout.import_unsupported_segment"),
    WORKOUT_IMPORT_PARSE_FAILED("workout.import_parse_failed"),
    WORKOUT_IMPORT_EMPTY_WORKOUT("workout.import_empty_workout"),
    WORKOUT_EXECUTION_MAPPING_BLOCKED("workout.execution_mapping_blocked"),
    WORKOUT_EXECUTION_MAPPING_DEGRADED("workout.execution_mapping_degraded"),
    EXPORT_NO_SUMMARY("export.no_summary"),
    EXPORT_INVALID_TIMESTAMPS("export.invalid_timestamps"),
    EXPORT_OUTPUT_STREAM_UNAVAILABLE("export.output_stream_unavailable"),
    EXPORT_WRITE_FAILED("export.write_failed"),
}

/**
 * Unified failure model for session/workout/export paths.
 */
sealed interface AppFailure {
    val reason: AppFailureReasonCode
    val detail: String?

    /**
     * Session-start/control failures where a recovery prompt is shown in MENU.
     */
    data class Session(
        override val reason: AppFailureReasonCode,
        val requestControlResultCode: Int? = null,
        override val detail: String? = null,
    ) : AppFailure

    /**
     * Workout import failures with optional parser and format diagnostics.
     */
    data class WorkoutImport(
        override val reason: AppFailureReasonCode,
        val importErrorCode: WorkoutImportErrorCode? = null,
        val detectedFormat: WorkoutImportFormat? = null,
        val baseMessage: String? = null,
        override val detail: String? = null,
    ) : AppFailure

    /**
     * Workout execution-mapping policy failures.
     */
    data class WorkoutExecution(
        override val reason: AppFailureReasonCode,
        val mappingSummary: String,
        override val detail: String? = null,
    ) : AppFailure

    /**
     * Session export failures.
     */
    data class Export(
        override val reason: AppFailureReasonCode,
        override val detail: String? = null,
    ) : AppFailure
}

/**
 * Factory helpers that map existing domain failures to unified app failures.
 */
object AppFailureFactory {
    fun sessionConnectFlowTimeout(): AppFailure.Session {
        return AppFailure.Session(reason = AppFailureReasonCode.SESSION_CONNECT_FLOW_TIMEOUT)
    }

    fun sessionRequestControlRejected(resultCode: Int): AppFailure.Session {
        return AppFailure.Session(
            reason = AppFailureReasonCode.SESSION_REQUEST_CONTROL_REJECTED,
            requestControlResultCode = resultCode,
        )
    }

    fun sessionRequestControlTimeout(): AppFailure.Session {
        return AppFailure.Session(reason = AppFailureReasonCode.SESSION_REQUEST_CONTROL_TIMEOUT)
    }

    fun sessionBluetoothPermissionDenied(): AppFailure.Session {
        return AppFailure.Session(reason = AppFailureReasonCode.SESSION_BLUETOOTH_PERMISSION_DENIED)
    }

    fun workoutImportReadFailed(detail: String?): AppFailure.WorkoutImport {
        return AppFailure.WorkoutImport(
            reason = AppFailureReasonCode.WORKOUT_IMPORT_READ_FAILED,
            detail = detail?.takeIf { it.isNotBlank() },
        )
    }

    fun workoutImportFailure(error: WorkoutImportError): AppFailure.WorkoutImport {
        val reason = when (error.code) {
            WorkoutImportErrorCode.EMPTY_CONTENT -> AppFailureReasonCode.WORKOUT_IMPORT_EMPTY_CONTENT
            WorkoutImportErrorCode.UNSUPPORTED_FORMAT -> AppFailureReasonCode.WORKOUT_IMPORT_UNSUPPORTED_FORMAT
            WorkoutImportErrorCode.UNSUPPORTED_SEGMENT -> AppFailureReasonCode.WORKOUT_IMPORT_UNSUPPORTED_SEGMENT
            WorkoutImportErrorCode.PARSE_FAILED -> AppFailureReasonCode.WORKOUT_IMPORT_PARSE_FAILED
            WorkoutImportErrorCode.EMPTY_WORKOUT -> AppFailureReasonCode.WORKOUT_IMPORT_EMPTY_WORKOUT
        }
        return AppFailure.WorkoutImport(
            reason = reason,
            importErrorCode = error.code,
            detectedFormat = error.detectedFormat,
            baseMessage = error.message,
            detail = error.technicalDetails,
        )
    }

    fun workoutExecutionMappingBlocked(
        mappingSummary: String,
        detail: String? = null,
    ): AppFailure.WorkoutExecution {
        return AppFailure.WorkoutExecution(
            reason = AppFailureReasonCode.WORKOUT_EXECUTION_MAPPING_BLOCKED,
            mappingSummary = mappingSummary,
            detail = detail?.takeIf { it.isNotBlank() },
        )
    }

    fun workoutExecutionMappingDegraded(
        mappingSummary: String,
        detail: String? = null,
    ): AppFailure.WorkoutExecution {
        return AppFailure.WorkoutExecution(
            reason = AppFailureReasonCode.WORKOUT_EXECUTION_MAPPING_DEGRADED,
            mappingSummary = mappingSummary,
            detail = detail?.takeIf { it.isNotBlank() },
        )
    }

    fun exportFailure(reason: FitExportFailureReason, detail: String? = null): AppFailure.Export {
        val mappedReason = when (reason) {
            FitExportFailureReason.NO_SUMMARY -> AppFailureReasonCode.EXPORT_NO_SUMMARY
            FitExportFailureReason.INVALID_TIMESTAMPS -> AppFailureReasonCode.EXPORT_INVALID_TIMESTAMPS
            FitExportFailureReason.OUTPUT_STREAM_UNAVAILABLE -> {
                AppFailureReasonCode.EXPORT_OUTPUT_STREAM_UNAVAILABLE
            }
            FitExportFailureReason.WRITE_FAILED -> AppFailureReasonCode.EXPORT_WRITE_FAILED
        }
        return AppFailure.Export(reason = mappedReason, detail = detail)
    }
}

/**
 * String provider used by failure-message mapper to keep Android APIs out of tests.
 */
fun interface AppFailureStringResolver {
    fun resolve(resId: Int, fallback: String, vararg args: Any): String

    companion object {
        fun fromContext(context: Context): AppFailureStringResolver {
            return AppFailureStringResolver { resId, fallback, args ->
                val resolved = runCatching {
                    if (args.isEmpty()) {
                        context.getString(resId)
                    } else {
                        context.getString(resId, *args)
                    }
                }.getOrNull()
                resolved ?: formatFallback(fallback = fallback, args = args)
            }
        }

        private fun formatFallback(fallback: String, args: Array<out Any>): String {
            if (args.isEmpty()) return fallback
            return runCatching {
                String.format(Locale.US, fallback, *args)
            }.getOrDefault(fallback)
        }
    }
}

/**
 * Centralized mapper from typed failures to user-safe UI messages.
 */
object AppFailureUserMessageMapper {
    fun toUserMessage(
        failure: AppFailure,
        strings: AppFailureStringResolver,
    ): String {
        return when (failure) {
            is AppFailure.Session -> mapSessionFailure(failure, strings)
            is AppFailure.WorkoutImport -> mapWorkoutImportFailure(failure)
            is AppFailure.WorkoutExecution -> mapWorkoutExecutionFailure(failure, strings)
            is AppFailure.Export -> mapExportFailure(failure, strings)
        }
    }

    private fun mapSessionFailure(
        failure: AppFailure.Session,
        strings: AppFailureStringResolver,
    ): String {
        return when (failure.reason) {
            AppFailureReasonCode.SESSION_CONNECT_FLOW_TIMEOUT -> {
                strings.resolve(
                    resId = R.string.menu_saved_trainer_connect_failed,
                    fallback = "Could not connect to the saved trainer. Confirm the trainer is powered on. Do you want to search for devices again?",
                )
            }
            AppFailureReasonCode.SESSION_REQUEST_CONTROL_REJECTED -> {
                strings.resolve(
                    resId = R.string.menu_request_control_rejected,
                    fallback = "Trainer rejected control request (code %d). Confirm trainer is powered on and not controlled by another app. Search for devices again?",
                    failure.requestControlResultCode ?: -1,
                )
            }
            AppFailureReasonCode.SESSION_REQUEST_CONTROL_TIMEOUT -> {
                strings.resolve(
                    resId = R.string.menu_request_control_timeout,
                    fallback = "Trainer did not confirm control request in time. Confirm trainer is powered on and try searching devices again.",
                )
            }
            AppFailureReasonCode.SESSION_BLUETOOTH_PERMISSION_DENIED -> {
                strings.resolve(
                    resId = R.string.menu_connect_permission_denied_open_settings,
                    fallback = "Bluetooth permission is required to start a session. Open app settings and allow Nearby devices permission.",
                )
            }
            else -> "Session operation failed."
        }
    }

    private fun mapWorkoutImportFailure(failure: AppFailure.WorkoutImport): String {
        if (failure.reason == AppFailureReasonCode.WORKOUT_IMPORT_READ_FAILED) {
            return buildUserFacingImportMessage(
                primary = "Could not read the selected file.",
                recovery = "Choose the file again. If it comes from cloud storage, download it to the device first.",
                technicalDetail = failure.detail,
            )
        }

        return when (failure.reason) {
            AppFailureReasonCode.WORKOUT_IMPORT_EMPTY_CONTENT -> {
                buildUserFacingImportMessage(
                    primary = "The selected file is empty.",
                    recovery = "Choose a workout file that contains a valid .zwo, .xml, or .ewo workout.",
                )
            }

            AppFailureReasonCode.WORKOUT_IMPORT_EMPTY_WORKOUT -> {
                buildUserFacingImportMessage(
                    primary = "The file was read, but it does not contain any executable workout steps.",
                    recovery = "Check that the workout includes intervals or other supported steps before importing again.",
                )
            }

            AppFailureReasonCode.WORKOUT_IMPORT_UNSUPPORTED_FORMAT -> {
                when (failure.detectedFormat) {
                    WorkoutImportFormat.MYWHOOSH_JSON -> {
                        buildUserFacingImportMessage(
                            primary = "This file looks like MyWhoosh JSON, which Ewoc cannot import yet.",
                            recovery = "Use a .zwo, .xml, or .ewo workout file instead.",
                        )
                    }

                    else -> {
                        buildUserFacingImportMessage(
                            primary = "Unsupported workout file format.",
                            recovery = "Ewoc currently imports .zwo, .xml, and .ewo workout files.",
                        )
                    }
                }
            }

            AppFailureReasonCode.WORKOUT_IMPORT_UNSUPPORTED_SEGMENT -> {
                buildUserFacingImportMessage(
                    primary = "This workout contains Free Ride segments, which Ewoc does not currently support during workout import.",
                    recovery = "Replace Free Ride with structured steps before importing the workout again.",
                    technicalDetail = failure.detail,
                )
            }

            AppFailureReasonCode.WORKOUT_IMPORT_PARSE_FAILED -> {
                when (failure.detectedFormat) {
                    WorkoutImportFormat.ZWO_XML -> {
                        buildUserFacingImportMessage(
                            primary = "This ZWO/XML workout file could not be parsed.",
                            recovery = "Check that the XML is complete and that workout steps use valid attributes.",
                            technicalDetail = failure.detail,
                        )
                    }

                    WorkoutImportFormat.CANONICAL_EWO_JSON -> {
                        val needsProfileData = failure.baseMessage
                            ?.contains("athlete profile data", ignoreCase = true) == true
                        if (needsProfileData) {
                            val recovery = when {
                                failure.detail?.contains("missing_hr_max", ignoreCase = true) == true ->
                                    "Set age in Rider profile so Ewoc can estimate HR max, then try importing the workout again."
                                failure.detail?.contains("missing_ftp", ignoreCase = true) == true ||
                                    failure.detail?.contains("ftp_required", ignoreCase = true) == true ->
                                    "Set FTP in Rider profile and try importing the workout again."
                                else ->
                                    "Set the rider profile values this workout needs, then try importing again."
                            }
                            buildUserFacingImportMessage(
                                primary = "This .ewo workout needs rider profile data before it can run.",
                                recovery = recovery,
                                technicalDetail = failure.detail,
                            )
                        } else {
                            buildUserFacingImportMessage(
                                primary = "This .ewo workout file could not be parsed.",
                                recovery = "Check the JSON structure and required workout fields before importing again.",
                                technicalDetail = failure.detail,
                            )
                        }
                    }

                    WorkoutImportFormat.ERGO_WORKOUT_JSON -> {
                        buildUserFacingImportMessage(
                            primary = "This workout JSON file could not be parsed.",
                            recovery = "Check the JSON structure and required workout fields before importing again.",
                            technicalDetail = failure.detail,
                        )
                    }

                    WorkoutImportFormat.MYWHOOSH_JSON -> {
                        buildUserFacingImportMessage(
                            primary = "This file looks like MyWhoosh JSON, which Ewoc cannot import yet.",
                            recovery = "Use a .zwo, .xml, or .ewo workout file instead.",
                            technicalDetail = failure.detail,
                        )
                    }

                    WorkoutImportFormat.UNKNOWN,
                    null,
                    -> {
                        buildUserFacingImportMessage(
                            primary = "The selected workout file could not be parsed.",
                            recovery = "Use a supported .zwo, .xml, or .ewo workout file and try again.",
                            technicalDetail = failure.detail,
                        )
                    }
                }
            }

            else -> {
                buildUserFacingImportMessage(
                    primary = "Workout import failed.",
                    technicalDetail = failure.detail,
                )
            }
        }
    }

    private fun buildUserFacingImportMessage(
        primary: String,
        recovery: String? = null,
        technicalDetail: String? = null,
    ): String {
        val sanitizedDetail = technicalDetail
            ?.lineSequence()
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.joinToString(separator = " ")
            ?.takeIf { it.isNotEmpty() }

        return buildString {
            append(primary)
            recovery?.let {
                append('\n')
                append(it)
            }
            sanitizedDetail?.let {
                append("\nTechnical detail: ")
                append(it)
            }
        }
    }

    private fun mapWorkoutExecutionFailure(
        failure: AppFailure.WorkoutExecution,
        strings: AppFailureStringResolver,
    ): String {
        val baseMessage = when (failure.reason) {
            AppFailureReasonCode.WORKOUT_EXECUTION_MAPPING_BLOCKED -> {
                strings.resolve(
                    resId = R.string.menu_workout_execution_blocked,
                    fallback = "Workout execution blocked: unsupported steps (%s).",
                    failure.mappingSummary,
                )
            }
            AppFailureReasonCode.WORKOUT_EXECUTION_MAPPING_DEGRADED -> {
                strings.resolve(
                    resId = R.string.menu_workout_execution_degraded,
                    fallback = "Workout execution degraded: legacy fallback enabled (%s).",
                    failure.mappingSummary,
                )
            }
            else -> "Workout execution is unavailable."
        }
        val detail = failure.detail?.trim()?.takeIf { it.isNotEmpty() } ?: return baseMessage
        return "$baseMessage\n$detail"
    }

    private fun mapExportFailure(
        failure: AppFailure.Export,
        strings: AppFailureStringResolver,
    ): String {
        return when (failure.reason) {
            AppFailureReasonCode.EXPORT_NO_SUMMARY -> {
                strings.resolve(
                    resId = R.string.summary_fit_export_no_summary,
                    fallback = "No summary available for export.",
                )
            }
            AppFailureReasonCode.EXPORT_INVALID_TIMESTAMPS -> {
                strings.resolve(
                    resId = R.string.summary_fit_export_invalid_timestamps,
                    fallback = "Session timestamps are invalid for FIT export.",
                )
            }
            AppFailureReasonCode.EXPORT_OUTPUT_STREAM_UNAVAILABLE,
            AppFailureReasonCode.EXPORT_WRITE_FAILED,
            -> {
                strings.resolve(
                    resId = R.string.summary_fit_export_failed,
                    fallback = "FIT export failed.",
                )
            }
            else -> "Session export failed."
        }
    }
}

package io.github.ewoc2026.ewoc

import io.github.ewoc2026.ewoc.session.export.FitExportFailureReason
import io.github.ewoc2026.ewoc.workout.WorkoutImportError
import io.github.ewoc2026.ewoc.workout.WorkoutImportErrorCode
import io.github.ewoc2026.ewoc.workout.WorkoutImportFormat
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppFailureModelTest {
    private val fallbackStrings = AppFailureStringResolver { _, fallback, args ->
        if (args.isEmpty()) {
            fallback
        } else {
            String.format(Locale.US, fallback, *args)
        }
    }

    @Test
    fun reasonCodesRemainStable() {
        val codes = AppFailureReasonCode.values().map { it.stableCode }

        assertEquals(
            listOf(
                "session.connect_flow_timeout",
                "session.request_control_rejected",
                "session.request_control_timeout",
                "session.bluetooth_permission_denied",
                "workout.import_read_failed",
                "workout.import_empty_content",
                "workout.import_unsupported_format",
                "workout.import_unsupported_segment",
                "workout.import_parse_failed",
                "workout.import_empty_workout",
                "workout.execution_mapping_blocked",
                "workout.execution_mapping_degraded",
                "export.no_summary",
                "export.invalid_timestamps",
                "export.output_stream_unavailable",
                "export.write_failed",
            ),
            codes,
        )
    }

    @Test
    fun mapsWorkoutImportParseFailureToDetailedMessage() {
        val failure = AppFailureFactory.workoutImportFailure(
            error = WorkoutImportError(
                code = WorkoutImportErrorCode.PARSE_FAILED,
                message = "Workout XML parsing failed.",
                detectedFormat = WorkoutImportFormat.ZWO_XML,
                technicalDetails = "Malformed ZWO XML at line 2.",
            ),
        )

        val message = AppFailureUserMessageMapper.toUserMessage(failure, fallbackStrings)

        assertEquals(AppFailureReasonCode.WORKOUT_IMPORT_PARSE_FAILED, failure.reason)
        assertTrue(message.contains("This ZWO/XML workout file could not be parsed."))
        assertTrue(message.contains("Check that the XML is complete"))
        assertTrue(message.contains("Technical detail: Malformed ZWO XML at line 2."))
    }

    @Test
    fun mapsErgoWorkoutImportFailureToErgoFormatLabel() {
        val failure = AppFailureFactory.workoutImportFailure(
            error = WorkoutImportError(
                code = WorkoutImportErrorCode.UNSUPPORTED_FORMAT,
                message = "ergo_workout import is recognized, but session execution is not wired yet.",
                detectedFormat = WorkoutImportFormat.ERGO_WORKOUT_JSON,
                technicalDetails = "Imported 'Workout' with 3 compiled steps and total duration 600 sec.",
            ),
        )

        val message = AppFailureUserMessageMapper.toUserMessage(failure, fallbackStrings)

        assertEquals(AppFailureReasonCode.WORKOUT_IMPORT_UNSUPPORTED_FORMAT, failure.reason)
        assertTrue(message.contains("Unsupported workout file format."))
        assertTrue(message.contains("Ewoc currently imports .zwo, .xml, and .ewo workout files."))
    }

    @Test
    fun mapsCanonicalEwoImportFailureToCanonicalFormatLabel() {
        val failure = AppFailureFactory.workoutImportFailure(
            error = WorkoutImportError(
                code = WorkoutImportErrorCode.PARSE_FAILED,
                message = "Canonical .ewo JSON parsing failed.",
                detectedFormat = WorkoutImportFormat.CANONICAL_EWO_JSON,
                technicalDetails = "unknown_field at $.segments[0].unexpected: Unknown field 'unexpected'.",
            ),
        )

        val message = AppFailureUserMessageMapper.toUserMessage(failure, fallbackStrings)

        assertEquals(AppFailureReasonCode.WORKOUT_IMPORT_PARSE_FAILED, failure.reason)
        assertTrue(message.contains("This .ewo workout file could not be parsed."))
        assertTrue(message.contains("Check the JSON structure and required workout fields"))
        assertTrue(message.contains("Technical detail: unknown_field"))
    }

    @Test
    fun mapsCanonicalEwoProfileContextFailureToActionableProfileMessage() {
        val failure = AppFailureFactory.workoutImportFailure(
            error = WorkoutImportError(
                code = WorkoutImportErrorCode.PARSE_FAILED,
                message = "Workout requires athlete profile data to execute.",
                detectedFormat = WorkoutImportFormat.CANONICAL_EWO_JSON,
                technicalDetails = "ftp_required: ftp_percent target needs ftpWatts",
            ),
        )

        val message = AppFailureUserMessageMapper.toUserMessage(failure, fallbackStrings)

        assertTrue(message.contains("needs rider profile data"))
        assertTrue(message.contains("Set FTP in Rider profile"))
        assertTrue(message.contains("Technical detail: ftp_required"))
    }

    @Test
    fun mapsCanonicalEwoHrRelativeProfileFailureToHrMaxRecoveryMessage() {
        val failure = AppFailureFactory.workoutImportFailure(
            error = WorkoutImportError(
                code = WorkoutImportErrorCode.PARSE_FAILED,
                message = "Workout requires athlete profile data to execute.",
                detectedFormat = WorkoutImportFormat.CANONICAL_EWO_JSON,
                technicalDetails = "missing_hr_max: HR max is required to resolve heart_rate_relative targets with hr_max reference.",
            ),
        )

        val message = AppFailureUserMessageMapper.toUserMessage(failure, fallbackStrings)

        assertTrue(message.contains("needs rider profile data"))
        assertTrue(message.contains("Set age in Rider profile so Ewoc can estimate HR max"))
        assertTrue(message.contains("Technical detail: missing_hr_max"))
    }

    @Test
    fun mapsUnsupportedFreeRideSegmentToActionableMessage() {
        val failure = AppFailureFactory.workoutImportFailure(
            error = WorkoutImportError(
                code = WorkoutImportErrorCode.UNSUPPORTED_SEGMENT,
                message = "Workout contains free-ride segments, which Ewoc does not currently import.",
                detectedFormat = WorkoutImportFormat.CANONICAL_EWO_JSON,
                technicalDetails = "unsupported_segment: free_ride",
            ),
        )

        val message = AppFailureUserMessageMapper.toUserMessage(failure, fallbackStrings)

        assertEquals(AppFailureReasonCode.WORKOUT_IMPORT_UNSUPPORTED_SEGMENT, failure.reason)
        assertTrue(message.contains("Free Ride segments"))
        assertTrue(message.contains("Replace Free Ride with structured steps"))
        assertTrue(message.contains("Technical detail: unsupported_segment: free_ride"))
    }

    @Test
    fun mapsReadFailureToActionableMessage() {
        val failure = AppFailureFactory.workoutImportReadFailed("Permission denied")

        val message = AppFailureUserMessageMapper.toUserMessage(failure, fallbackStrings)

        assertTrue(message.contains("Could not read the selected file."))
        assertTrue(message.contains("download it to the device first"))
        assertTrue(message.contains("Technical detail: Permission denied"))
    }

    @Test
    fun mapsRequestControlRejectedWithStableUserPrompt() {
        val failure = AppFailureFactory.sessionRequestControlRejected(resultCode = 5)

        val message = AppFailureUserMessageMapper.toUserMessage(failure, fallbackStrings)

        assertEquals("session.request_control_rejected", failure.reason.stableCode)
        assertTrue(message.contains("code 5"))
        assertTrue(message.contains("Search for devices again"))
    }

    @Test
    fun mapsWorkoutExecutionBlockedMessageWithSummary() {
        val failure = AppFailureFactory.workoutExecutionMappingBlocked(
            mappingSummary = "UNSUPPORTED_INTERVALS_T",
        )

        val message = AppFailureUserMessageMapper.toUserMessage(failure, fallbackStrings)

        assertEquals(AppFailureReasonCode.WORKOUT_EXECUTION_MAPPING_BLOCKED, failure.reason)
        assertTrue(message.contains("UNSUPPORTED_INTERVALS_T"))
    }

    @Test
    fun appendsWorkoutExecutionDetailWhenAvailable() {
        val failure = AppFailureFactory.workoutExecutionMappingBlocked(
            mappingSummary = "UNSUPPORTED_HEART_RATE_TARGET",
            detail = "Imported HR execution requires HEART_RATE_SIGNAL and TRAINER_CONTROL.",
        )

        val message = AppFailureUserMessageMapper.toUserMessage(failure, fallbackStrings)

        assertTrue(message.contains("UNSUPPORTED_HEART_RATE_TARGET"))
        assertTrue(message.contains("Imported HR execution requires HEART_RATE_SIGNAL"))
    }

    @Test
    fun mapsExportFailuresToUserSafeMessages() {
        val noSummaryFailure = AppFailureFactory.exportFailure(FitExportFailureReason.NO_SUMMARY)
        val noSummaryMessage = AppFailureUserMessageMapper.toUserMessage(noSummaryFailure, fallbackStrings)
        assertEquals(AppFailureReasonCode.EXPORT_NO_SUMMARY, noSummaryFailure.reason)
        assertEquals("No summary available for export.", noSummaryMessage)

        val writeFailure = AppFailureFactory.exportFailure(FitExportFailureReason.WRITE_FAILED)
        val writeFailureMessage = AppFailureUserMessageMapper.toUserMessage(writeFailure, fallbackStrings)
        assertEquals(AppFailureReasonCode.EXPORT_WRITE_FAILED, writeFailure.reason)
        assertEquals("FIT export failed.", writeFailureMessage)
    }
}

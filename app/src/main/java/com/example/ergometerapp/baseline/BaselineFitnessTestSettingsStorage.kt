package com.example.ergometerapp.baseline

import android.content.Context
import androidx.core.content.edit
import java.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Persists the latest baseline-test summary and the metadata that marks FTP as baseline-derived.
 *
 * Invalid or cancelled attempts replace only the latest test payload. Active-FTP provenance is updated
 * only by completed results so a failed retest cannot silently rewrite source metadata for the current FTP.
 */
internal object BaselineFitnessTestSettingsStorage {
    private const val preferencesName = "ergometer_app_settings"
    private const val keyFtpSource = "ftp_source"
    private const val keyFtpLastTestedAt = "ftp_last_tested_at"
    private const val keyLastBaselineTest = "last_baseline_test"

    fun loadLatestResult(context: Context): BaselineFitnessTestResult? {
        val prefs = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
        val payload = prefs.getString(keyLastBaselineTest, null) ?: return null
        return decodeLatestResult(payload)
    }

    fun loadFtpSource(context: Context): String? {
        val prefs = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
        return prefs.getString(keyFtpSource, null)?.trim()?.takeIf { it.isNotEmpty() }
    }

    fun loadFtpLastTestedAt(context: Context): Instant? {
        val prefs = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
        val stored = prefs.getString(keyFtpLastTestedAt, null)?.trim()?.takeIf { it.isNotEmpty() }
            ?: return null
        return parseInstantOrNull(stored)
    }

    fun saveLatestResult(
        context: Context,
        result: BaselineFitnessTestResult,
    ) {
        val prefs = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
        prefs.edit {
            putString(keyLastBaselineTest, encodeLatestResult(result))
            if (result.status == BaselineFitnessTestStatus.COMPLETED) {
                putString(keyFtpSource, result.testVersion)
                putString(keyFtpLastTestedAt, result.completedAt.toString())
            }
        }
    }

    fun clearActiveFtpSourceMetadata(context: Context) {
        val prefs = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
        prefs.edit {
            remove(keyFtpSource)
            remove(keyFtpLastTestedAt)
        }
    }

    internal fun encodeLatestResult(result: BaselineFitnessTestResult): String {
        return buildJsonObject {
            put("testVersion", result.testVersion)
            put("status", result.status.persistedValue)
            put("stopReason", result.stopReason.persistedValue)
            put("controlMode", result.controlMode.name)
            put("startedAt", result.startedAt.toString())
            put("completedAt", result.completedAt.toString())
            put("startWatts", result.startWatts)
            put("validRampMinutes", result.validRampMinutes)
            putNullableInt("lastFullStepWatts", result.lastFullStepWatts)
            putNullableInt("ftpEstimateWatts", result.ftpEstimateWatts)
            putNullableInt("peak1mPowerWatts", result.peak1mPowerWatts)
            putNullableInt("thresholdHrEstimateBpm", result.thresholdHrEstimateBpm)
            putNullableString("confidence", result.confidence?.name)
            put("maxPowerGapSec", result.maxPowerGapSec)
            put("hrCoverageRatio", result.hrCoverageRatio)
            put(
                "sensorProfile",
                buildJsonObject {
                    put("power", result.sensorProfile.power)
                    put("heartRate", result.sensorProfile.heartRate)
                    put("cadence", result.sensorProfile.cadence)
                },
            )
        }.toString()
    }

    internal fun decodeLatestResult(payload: String): BaselineFitnessTestResult? {
        val root = runCatching {
            Json.parseToJsonElement(payload).jsonObject
        }.getOrNull() ?: return null
        val sensorProfile = root.sensorProfileOrNull() ?: return null
        val testVersion = root.stringOrNull("testVersion") ?: return null
        val status = root.stringOrNull("status")?.let(::statusFromPersistedValue) ?: return null
        val stopReason = root.stringOrNull("stopReason")?.let(::stopReasonFromPersistedValue) ?: return null
        val controlMode = root.stringOrNull("controlMode")?.let(::controlModeFromPersistedValue) ?: return null
        val startedAt = root.stringOrNull("startedAt")?.let(::parseInstantOrNull) ?: return null
        val completedAt = root.stringOrNull("completedAt")?.let(::parseInstantOrNull) ?: return null
        val startWatts = root.intOrNull("startWatts") ?: return null
        val validRampMinutes = root.intOrNull("validRampMinutes") ?: return null
        val maxPowerGapSec = root.doubleOrNull("maxPowerGapSec") ?: return null
        val hrCoverageRatio = root.doubleOrNull("hrCoverageRatio") ?: return null

        return BaselineFitnessTestResult(
            testVersion = testVersion,
            status = status,
            stopReason = stopReason,
            controlMode = controlMode,
            startedAt = startedAt,
            completedAt = completedAt,
            startWatts = startWatts,
            validRampMinutes = validRampMinutes,
            lastFullStepWatts = root.intOrNull("lastFullStepWatts"),
            ftpEstimateWatts = root.intOrNull("ftpEstimateWatts"),
            peak1mPowerWatts = root.intOrNull("peak1mPowerWatts"),
            thresholdHrEstimateBpm = root.intOrNull("thresholdHrEstimateBpm"),
            confidence = root.stringOrNull("confidence")?.let(::confidenceFromPersistedValue),
            maxPowerGapSec = maxPowerGapSec,
            hrCoverageRatio = hrCoverageRatio,
            sensorProfile = sensorProfile,
        )
    }

    private fun JsonObject.sensorProfileOrNull(): BaselineFitnessTestSensorProfile? {
        val profile = this["sensorProfile"]?.jsonObject ?: return null
        val power = profile.booleanOrNull("power") ?: return null
        val heartRate = profile.booleanOrNull("heartRate") ?: return null
        val cadence = profile.booleanOrNull("cadence") ?: return null
        return BaselineFitnessTestSensorProfile(
            power = power,
            heartRate = heartRate,
            cadence = cadence,
        )
    }

    private fun JsonObject.stringOrNull(key: String): String? {
        return this[key]?.jsonPrimitive?.contentOrNull
    }

    private fun JsonObject.intOrNull(key: String): Int? {
        return this[key]?.jsonPrimitive?.intOrNull
    }

    private fun JsonObject.doubleOrNull(key: String): Double? {
        return this[key]?.jsonPrimitive?.doubleOrNull
    }

    private fun JsonObject.booleanOrNull(key: String): Boolean? {
        return this[key]?.jsonPrimitive?.booleanOrNull
    }

    private fun statusFromPersistedValue(value: String): BaselineFitnessTestStatus? {
        return when (value) {
            "completed" -> BaselineFitnessTestStatus.COMPLETED
            "invalid" -> BaselineFitnessTestStatus.INVALID
            "cancelled" -> BaselineFitnessTestStatus.CANCELLED
            else -> null
        }
    }

    private fun stopReasonFromPersistedValue(value: String): BaselineFitnessTestStopReason? {
        return when (value) {
            "manual_stop" -> BaselineFitnessTestStopReason.MANUAL_STOP
            "cadence_drop" -> BaselineFitnessTestStopReason.CADENCE_DROP
            "power_signal_lost" -> BaselineFitnessTestStopReason.POWER_SIGNAL_LOST
            "device_disconnect" -> BaselineFitnessTestStopReason.DEVICE_DISCONNECT
            "control_grant_declined" -> BaselineFitnessTestStopReason.CONTROL_GRANT_DECLINED
            "control_lost_mid_test" -> BaselineFitnessTestStopReason.CONTROL_LOST_MID_TEST
            "user_cancel" -> BaselineFitnessTestStopReason.USER_CANCEL
            else -> null
        }
    }

    private fun controlModeFromPersistedValue(value: String): BaselineFitnessTestControlMode? {
        return runCatching {
            BaselineFitnessTestControlMode.valueOf(value)
        }.getOrNull()
    }

    private fun confidenceFromPersistedValue(value: String): BaselineFitnessTestConfidence? {
        return runCatching {
            BaselineFitnessTestConfidence.valueOf(value)
        }.getOrNull()
    }

    private fun parseInstantOrNull(value: String): Instant? {
        return runCatching {
            Instant.parse(value)
        }.getOrNull()
    }

    private val BaselineFitnessTestStatus.persistedValue: String
        get() = when (this) {
            BaselineFitnessTestStatus.COMPLETED -> "completed"
            BaselineFitnessTestStatus.INVALID -> "invalid"
            BaselineFitnessTestStatus.CANCELLED -> "cancelled"
        }

    private val BaselineFitnessTestStopReason.persistedValue: String
        get() = when (this) {
            BaselineFitnessTestStopReason.MANUAL_STOP -> "manual_stop"
            BaselineFitnessTestStopReason.CADENCE_DROP -> "cadence_drop"
            BaselineFitnessTestStopReason.POWER_SIGNAL_LOST -> "power_signal_lost"
            BaselineFitnessTestStopReason.DEVICE_DISCONNECT -> "device_disconnect"
            BaselineFitnessTestStopReason.CONTROL_GRANT_DECLINED -> "control_grant_declined"
            BaselineFitnessTestStopReason.CONTROL_LOST_MID_TEST -> "control_lost_mid_test"
            BaselineFitnessTestStopReason.USER_CANCEL -> "user_cancel"
        }

    private fun kotlinx.serialization.json.JsonObjectBuilder.putNullableInt(
        key: String,
        value: Int?,
    ) {
        if (value == null) {
            put(key, JsonNull)
        } else {
            put(key, value)
        }
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.putNullableString(
        key: String,
        value: String?,
    ) {
        if (value == null) {
            put(key, JsonNull)
        } else {
            put(key, value)
        }
    }
}

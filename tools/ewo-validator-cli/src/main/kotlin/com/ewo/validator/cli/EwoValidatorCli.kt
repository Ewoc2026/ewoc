package com.ewo.validator.cli

import com.ewo.core.EwoCompileContext
import com.ewo.core.EwoEngine
import com.ewo.core.EwoWorkoutParseResult
import java.io.InputStreamReader
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

private val structuredJson = Json {
    ignoreUnknownKeys = false
    isLenient = false
}

/**
 * Thin JVM entrypoint that exposes the shared ewo-core validator as a machine-readable CLI.
 *
 * Reads a JSON request from stdin and writes a JSON result to stdout so non-JVM callers can
 * reuse the canonical validator without copying schema or semantic rules into another language.
 */
fun main() {
    val requestText = InputStreamReader(System.`in`, Charsets.UTF_8).readText()
    val response = runCatching { validateRequest(requestText) }
        .getOrElse { exception ->
            buildJsonObject {
                put("status", "cli_error")
                put("message", exception.message ?: "CLI validation failed")
            }
        }
    print(response.toString())
}

internal fun validateRequest(requestText: String): JsonObject {
    val root = structuredJson.parseToJsonElement(requestText).jsonObject
    val workoutJson = root["workout_json"]?.jsonPrimitive?.contentOrNull
        ?: error("Missing workout_json")
    val ftpWatts = root["ftp_watts"]?.jsonPrimitive?.intOrNull
    val hrProfile = root["hr_profile"]?.jsonObject
    val context = EwoCompileContext(
        ftpWatts = ftpWatts,
        hrMaxBpm = hrProfile?.get("hr_max_bpm")?.jsonPrimitive?.intOrNull,
        restingHrBpm = hrProfile?.get("resting_hr_bpm")?.jsonPrimitive?.intOrNull,
        lthrBpm = hrProfile?.get("lthr_bpm")?.jsonPrimitive?.intOrNull,
    )

    return when (val result = EwoEngine.analyze(workoutJson, context)) {
        is EwoWorkoutParseResult.Failure -> buildJsonObject {
            put("status", "failure")
            put("error_code", result.error.code.stableCode)
            put("message", result.error.message)
            put("field_path", result.error.fieldPath)
        }

        is EwoWorkoutParseResult.Success.NeedsCompileContext -> buildJsonObject {
            put("status", "needs_compile_context")
            put(
                "compile_errors",
                buildJsonArray {
                    result.compileErrors.forEach { compileError ->
                        add(
                            buildJsonObject {
                                put("code", compileError.code.stableCode)
                                put("message", compileError.message)
                                put("segment_id", compileError.segmentId)
                            },
                        )
                    }
                },
            )
            put("sanity_issues", sanityIssuesJson(result.sanityResult.issues))
        }

        is EwoWorkoutParseResult.Success.Compiled -> buildJsonObject {
            put("status", "compiled")
            put("sanity_issues", sanityIssuesJson(result.sanityResult.issues))
        }
    }
}

private fun sanityIssuesJson(issues: List<com.ewo.core.SanityIssue>) = buildJsonArray {
    issues.forEach { issue ->
        add(
            buildJsonObject {
                put("code", issue.code.stableCode)
                put("severity", issue.severity.name.lowercase())
                put("message", issue.message)
                put("segment_id", issue.segmentId)
            },
        )
    }
}

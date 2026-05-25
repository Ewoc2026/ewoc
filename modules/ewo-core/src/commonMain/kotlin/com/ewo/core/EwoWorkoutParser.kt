package com.ewo.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * Parses canonical `.ewo` v1 payloads into parsed, validated, normalized, and repeat-expanded
 * forms without yet changing import routing or runtime trainer behavior.
 */
internal object EwoWorkoutParser {
    private const val rootPath = "$"

    private val structuredJson = Json {
        ignoreUnknownKeys = false
        isLenient = false
    }

    fun parse(content: String, context: EwoCompileContext): EwoWorkoutParseResult {
        val rootElement = try {
            structuredJson.parseToJsonElement(content)
        } catch (exception: Exception) {
            return EwoWorkoutParseResult.Failure(
                error = EwoWorkoutValidationError(
                    code = EwoWorkoutValidationErrorCode.INVALID_JSON,
                    message = exception.message?.takeIf { it.isNotBlank() }
                        ?: "Workout JSON is malformed.",
                    fieldPath = rootPath,
                ),
            )
        }

        val rootObject = rootElement as? JsonObject
            ?: return EwoWorkoutParseResult.Failure(
                error = EwoWorkoutValidationError(
                    code = EwoWorkoutValidationErrorCode.INVALID_JSON,
                    message = "Workout JSON root must be an object.",
                    fieldPath = rootPath,
                ),
            )

        return try {
            val parsed = EwoWorkoutSchemaValidator.validate(rootObject, rootPath)
            val normalized = EwoWorkoutSemanticValidator.validate(parsed)
            val compiled = try {
                EwoWorkoutRepeatExpansionCompiler.compile(normalized, context)
            } catch (compileException: EwoCompileContextException) {
                return EwoWorkoutParseResult.Success.NeedsCompileContext(
                    parsed = parsed,
                    normalized = normalized,
                    compileErrors = listOf(compileException.compileError),
                )
            }
            val sanityResult = EwoWorkoutSanityValidator.check(compiled, context.ftpWatts)
            EwoWorkoutParseResult.Success.Compiled(
                parsed = parsed,
                normalized = normalized,
                compiled = compiled,
                sanityResult = sanityResult,
            )
        } catch (exception: EwoWorkoutValidationException) {
            EwoWorkoutParseResult.Failure(error = exception.error)
        }
    }
}
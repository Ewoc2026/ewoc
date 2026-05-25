package com.ewo.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class EwoSpecFixtureConformanceTest {
    @Test
    fun manifestCasesMatchCurrentParserBehavior() {
        val manifestFile = resolveRepoFile("spec/ewo/v1/conformance/manifest.json")
        val manifest = Json.parseToJsonElement(manifestFile.readText()).jsonObject
        val cases = manifest.getValue("cases").jsonArray
        val failures = mutableListOf<String>()

        cases.forEach { caseElement ->
            val case = caseElement.jsonObject
            val id = case.getValue("id").jsonPrimitive.content
            val fixtureFile = File(manifestFile.parentFile, case.getValue("file").jsonPrimitive.content).canonicalFile
            val expected = case.getValue("expected").jsonObject
            val parserResult = expected.getValue("parser_result").jsonPrimitive.content
            val result = EwoEngine.parse(fixtureFile.readText(), ftpWatts = 250)

            when (parserResult) {
                "success" -> if (result !is EwoWorkoutParseResult.Success) {
                    failures += "$id expected success but got $result"
                }

                "failure" -> {
                    val failure = result as? EwoWorkoutParseResult.Failure
                    if (failure == null) {
                        failures += "$id expected failure but got $result"
                    } else {
                        val expectedCode = expected.getValue("error_code").jsonPrimitive.content
                        val expectedFieldPath = expected.getValue("field_path").jsonPrimitive.content
                        if (failure.error.code.stableCode != expectedCode) {
                            failures += "$id expected error_code=$expectedCode but was ${failure.error.code.stableCode}"
                        }
                        if (failure.error.fieldPath != expectedFieldPath) {
                            failures += "$id expected field_path=$expectedFieldPath but was ${failure.error.fieldPath}"
                        }
                    }
                }

                else -> failures += "$id declared unsupported parser_result '$parserResult'"
            }

            if (parserResult == "success" && expected.containsKey("validation_levels")) {
                val levels = expected.getValue("validation_levels").jsonArray
                if (levels.isEmpty()) {
                    failures += "$id expected non-empty validation_levels"
                }
            }
        }

        if (failures.isNotEmpty()) {
            fail(failures.joinToString(separator = "\n"))
        }
    }

    @Test
    fun manifestMetadataMatchesCanonicalRootIdentity() {
        val manifest = Json.parseToJsonElement(resolveRepoFile("spec/ewo/v1/conformance/manifest.json").readText()).jsonObject
        assertEquals("ewo", manifest.getValue("format").jsonPrimitive.content)
        val supportedVersions = manifest.getValue("supported_versions").jsonArray.map { it.jsonPrimitive.content }
        assertEquals(listOf("1.0", "1.1", "1.2", "1.3", "1.4", "1.5", "1.6"), supportedVersions)
    }

    private fun resolveRepoFile(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File("../$relativePath"),
            File("../../$relativePath"),
        )
        return candidates.firstOrNull { it.isFile }
            ?: throw AssertionError(
                "Expected file at one of: ${candidates.joinToString { it.path }}",
            )
    }
}

package com.ewo.validator.cli

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EwoValidatorCliTest {

    @Test
    fun `validateRequest returns compiled for valid workout`() {
        val response = validateRequest(
            """
            {
              "workout_json": "{\"format\":\"ewo\",\"version\":\"1.6\",\"title\":\"Test\",\"segments\":[{\"id\":\"s1\",\"type\":\"steady\",\"duration_sec\":600,\"target\":{\"metric\":\"power\",\"value\":180}}]}",
              "ftp_watts": 250
            }
            """.trimIndent(),
        )

        assertEquals("compiled", response["status"]?.jsonPrimitive?.content)
        assertEquals(0, response["sanity_issues"]?.jsonArray?.size)
    }

    @Test
    fun `validateRequest returns failure details for invalid workout`() {
        val response = validateRequest(
            """
            {
              "workout_json": "{\"format\":\"ewo\",\"version\":\"1.6\",\"title\":\"Bad\",\"segments\":[{\"id\":\"s1\",\"type\":\"steady\",\"duration_sec\":0,\"target\":{\"metric\":\"power\",\"value\":180}}]}"
            }
            """.trimIndent(),
        )

        assertEquals("failure", response["status"]?.jsonPrimitive?.content)
        assertEquals("invalid_duration_sec", response["error_code"]?.jsonPrimitive?.content)
        assertTrue(response["field_path"]?.jsonPrimitive?.content?.isNotBlank() == true)
    }

    @Test
    fun `validateRequest returns compile context requirement when hr profile is missing`() {
        val response = validateRequest(
            """
            {
              "workout_json": "{\"format\":\"ewo\",\"version\":\"1.6\",\"title\":\"HR Workout\",\"control\":{\"initial_power_watts\":120,\"min_power_watts\":80,\"max_power_watts\":260,\"signal_loss_power_watts\":100,\"hr_upper_cap_bpm\":190},\"segments\":[{\"id\":\"s1\",\"type\":\"steady\",\"duration_sec\":600,\"target\":{\"metric\":\"heart_rate_relative\",\"reference\":\"hr_max\",\"range\":{\"low\":0.70,\"high\":0.80}}}]}"
            }
            """.trimIndent(),
        )

        assertEquals("needs_compile_context", response["status"]?.jsonPrimitive?.content)
        val compileErrors = response["compile_errors"]?.jsonArray ?: error("compile_errors missing")
        assertTrue("missing compile errors", compileErrors.isNotEmpty())
        assertEquals(
            "missing_hr_max",
            compileErrors[0].jsonObject["code"]?.jsonPrimitive?.content,
        )
    }
}

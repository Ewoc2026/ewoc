package com.ewo.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Lenient intake helper for metadata-only root tags.
 *
 * Import/open flows may choose to drop invalid or surplus tags so one malformed
 * classification hint does not block an otherwise executable workout. Canonical
 * parsing and export remain strict unless a caller opts into this helper.
 */
internal object EwoWorkoutTagSanitizer {
    private val structuredJson = Json {
        ignoreUnknownKeys = false
        isLenient = false
    }

    data class SanitizationResult(
        val sanitizedJson: String,
        val droppedTags: List<String>,
    )

    fun sanitize(json: String): SanitizationResult? {
        val rootObject = runCatching {
            structuredJson.parseToJsonElement(json) as? JsonObject
        }.getOrNull() ?: return null

        val tags = rootObject["tags"] as? JsonArray ?: return null
        val keptTags = mutableListOf<JsonPrimitive>()
        val droppedTags = mutableListOf<String>()

        for (tagElement in tags) {
            val primitive = tagElement as? JsonPrimitive ?: return null
            if (!primitive.isString) return null
            val tag = primitive.contentOrNull ?: return null
            val keepTag = EwoWorkoutTagPolicy.isValid(tag) &&
                keptTags.size < EwoWorkoutTagPolicy.maxCount
            if (keepTag) {
                keptTags += primitive
            } else {
                droppedTags += tag
            }
        }

        if (droppedTags.isEmpty()) return null

        val sanitizedRoot = JsonObject(
            rootObject.toMutableMap().apply {
                this["tags"] = JsonArray(keptTags)
            },
        )

        return SanitizationResult(
            sanitizedJson = sanitizedRoot.toString(),
            droppedTags = droppedTags,
        )
    }
}

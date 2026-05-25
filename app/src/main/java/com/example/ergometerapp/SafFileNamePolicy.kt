package com.example.ergometerapp

/**
 * Pure filename helpers for SAF tree import/export rules.
 *
 * All helpers avoid Android framework dependencies so conflict behavior can be
 * unit-tested in plain JVM tests.
 */
object SafFileNamePolicy {
    private val supportedWorkoutImportExtensions = setOf("zwo", "xml", "ewo")
    private val workoutDocumentPickerMimeTypes = arrayOf(
        "application/xml",
        "text/xml",
        "application/json",
        "application/octet-stream",
    )

    /**
     * Returns MIME type filters used by system document pickers for workout import.
     *
     * SAF providers expose inconsistent MIME metadata for `.zwo` and `.ewo` files,
     * and the picker cannot reliably constrain by filename extension alone. Keeping
     * the generic binary fallback preserves provider compatibility while `.xml`
     * remains discoverable through XML MIME types.
     */
    fun workoutImportPickerMimeTypes(): Array<String> = workoutDocumentPickerMimeTypes.copyOf()

    /**
     * Returns true when [name] points to a supported workout import file.
     *
     * Current supported filename extensions are `.zwo`, `.xml`, and `.ewo`.
     */
    fun isSupportedWorkoutImportFileName(name: String?): Boolean {
        val normalized = name?.trim()?.lowercase() ?: return false
        val extension = normalized.substringAfterLast('.', missingDelimiterValue = "")
        return extension in supportedWorkoutImportExtensions
    }

    /**
     * Ensures [fileName] ends with [requiredExtension] and keeps original casing otherwise.
     */
    fun ensureExtension(fileName: String, requiredExtension: String): String {
        val normalizedExt = normalizeExtension(requiredExtension)
        val trimmed = fileName.trim().ifBlank { "export$normalizedExt" }
        return if (trimmed.lowercase().endsWith(normalizedExt.lowercase())) {
            trimmed
        } else {
            "$trimmed$normalizedExt"
        }
    }

    /**
     * Resolves a conflict-safe file name by appending [timestampSuffix] before extension.
     *
     * Matching against [existingFileNames] is case-insensitive.
     */
    fun resolveConflictSafeFileName(
        preferredFileName: String,
        timestampSuffix: String,
        existingFileNames: Set<String>,
    ): String {
        val sanitizedTimestamp = timestampSuffix.trim().ifBlank { "copy" }
        val normalizedPreferred = preferredFileName.trim().ifBlank { "export" }
        val existingLower = existingFileNames.map { it.lowercase() }.toSet()
        if (normalizedPreferred.lowercase() !in existingLower) return normalizedPreferred

        val (base, extension) = splitBaseAndExtension(normalizedPreferred)
        val candidate = if (extension.isEmpty()) {
            "${base}_$sanitizedTimestamp"
        } else {
            "${base}_$sanitizedTimestamp.$extension"
        }
        if (candidate.lowercase() !in existingLower) return candidate

        var counter = 2
        while (true) {
            val indexedCandidate = if (extension.isEmpty()) {
                "${base}_${sanitizedTimestamp}_$counter"
            } else {
                "${base}_${sanitizedTimestamp}_$counter.$extension"
            }
            if (indexedCandidate.lowercase() !in existingLower) {
                return indexedCandidate
            }
            counter += 1
        }
    }

    private fun normalizeExtension(requiredExtension: String): String {
        val trimmed = requiredExtension.trim()
        if (trimmed.isEmpty()) return ""
        return if (trimmed.startsWith('.')) trimmed else ".$trimmed"
    }

    private fun splitBaseAndExtension(name: String): Pair<String, String> {
        val dotIndex = name.lastIndexOf('.')
        if (dotIndex <= 0 || dotIndex == name.length - 1) {
            return name to ""
        }
        return name.substring(0, dotIndex) to name.substring(dotIndex + 1)
    }
}

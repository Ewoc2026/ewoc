package io.github.ewoc2026.ewoc.compat.quirks

/**
 * Privacy-safe trainer identity signals used for quirks resolution.
 *
 * MAC addresses are intentionally excluded. These values are best-effort and
 * may be partially unavailable depending on peripheral behavior.
 */
data class TrainerFingerprint(
    val advNameNormalized: String?,
    val manufacturer: String?,
    val model: String?,
    val ftmsServicePresent: Boolean,
    val has2ad2: Boolean,
    val has2ad9: Boolean,
    val androidManufacturer: String,
    val androidModel: String,
) {
    companion object {
        /**
         * Normalizes trainer advertising names for stable matching without
         * erasing model/version digits that can be diagnostically important.
         */
        fun normalizeAdvertisementName(raw: String?): String? {
            val normalized = raw
                ?.trim()
                ?.lowercase()
                ?.replace(Regex("\\s+"), " ")
                ?: return null
            return normalized.takeIf { it.isNotEmpty() }
        }
    }
}

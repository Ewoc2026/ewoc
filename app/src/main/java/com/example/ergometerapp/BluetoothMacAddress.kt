package com.example.ergometerapp

import java.util.Locale

/**
 * Utility for validating and normalizing Bluetooth MAC address strings.
 *
 * The app accepts only the canonical colon-separated format to keep storage
 * deterministic and avoid runtime parsing surprises across BLE clients.
 */
object BluetoothMacAddress {
    private const val MAC_MAX_LENGTH = 17
    private val macRegex = Regex("^([0-9A-F]{2}:){5}[0-9A-F]{2}$")
    private const val REDACTED_INVALID = "<invalid-or-missing>"

    /**
     * Keeps only characters relevant for MAC entry and enforces canonical length.
     */
    fun sanitizeUserInput(rawInput: String): String {
        return rawInput
            .uppercase(Locale.US)
            .filter { it in '0'..'9' || it in 'A'..'F' || it == ':' }
            .take(MAC_MAX_LENGTH)
    }

    /**
     * Returns a canonical uppercase address when valid, otherwise null.
     */
    fun normalizeOrNull(rawInput: String): String? {
        val normalized = sanitizeUserInput(rawInput)
        if (!macRegex.matches(normalized)) return null
        return normalized
    }

    /**
     * Returns a log-safe representation that never exposes full device identifiers.
     *
     * Support diagnostics still need a stable hint to correlate user reports, so
     * we keep only the last two bytes and mask the rest.
     */
    fun redactForLogs(rawInput: String?): String {
        val normalized = rawInput?.let(::normalizeOrNull) ?: return REDACTED_INVALID
        val suffix = normalized.takeLast(5)
        return "XX:XX:XX:XX:$suffix"
    }
}

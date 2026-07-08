package io.github.ewoc2026.ewoc.ftms

/**
 * Coarse parser failure bucket for FTMS Indoor Bike payload diagnostics.
 *
 * Consumers rely on stable values here to aggregate production/support exports
 * without parsing exception class names.
 */
enum class IndoorBikeParseFailureReason {
    TRUNCATED_PAYLOAD,
    UNEXPECTED_EXCEPTION,
}

/**
 * Privacy-safe parse failure snapshot emitted when an FTMS Indoor Bike payload
 * cannot be decoded.
 *
 * [payloadPreviewHex] is always length-limited and never contains the full
 * packet so diagnostics remain useful without creating raw-data dumps.
 */
data class IndoorBikeParseFailure(
    val reason: IndoorBikeParseFailureReason,
    val exceptionType: String,
    val payloadLength: Int,
    val flags: Int?,
    val payloadPreviewHex: String,
)

package com.example.ergometerapp.ble

/**
 * Stages in the lifecycle of one FTMS Control Point command.
 *
 * The same [correlationId] is expected to be reused from [SENT] to the matching
 * [RESPONSE] or [TIMEOUT] event so request/response/timeout paths can be tied
 * together deterministically in diagnostics.
 */
enum class FtmsCommandLifecycleStage {
    SENT,
    RESPONSE,
    TIMEOUT,
    UNEXPECTED_RESPONSE,
}

/**
 * Structured FTMS command lifecycle signal for diagnostics consumers.
 */
data class FtmsCommandLifecycleEvent(
    val stage: FtmsCommandLifecycleStage,
    val correlationId: String?,
    val requestOpcode: Int?,
    val expectedOpcode: Int?,
    val receivedOpcode: Int?,
    val resultCode: Int?,
    val unexpectedReason: FtmsUnexpectedControlPointResponseReason?,
)

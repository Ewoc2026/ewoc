package io.github.ewoc2026.ewoc.ble

/**
 * Configures bounded reconnect behavior for the HR BLE link.
 *
 * `maxReconnectAttempts` counts scheduled reconnect tries after an unexpected
 * disconnect or setup failure. Value `0` disables reconnect scheduling and
 * emits exhaustion immediately.
 */
data class HrReconnectPolicy(
    val maxReconnectAttempts: Int = 4,
    val baseDelayMs: Long = 1000L,
    val maxDelayMs: Long = 8000L,
) {
    init {
        require(maxReconnectAttempts >= 0) {
            "maxReconnectAttempts must be >= 0."
        }
        require(baseDelayMs >= 0L) {
            "baseDelayMs must be >= 0."
        }
        require(maxDelayMs >= baseDelayMs) {
            "maxDelayMs must be >= baseDelayMs."
        }
    }
}

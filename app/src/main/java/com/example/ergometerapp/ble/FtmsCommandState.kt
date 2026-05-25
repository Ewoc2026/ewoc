package com.example.ergometerapp.ble

/**
 * Tracks the lifecycle of a single FTMS Control Point command.
 *
 * FTMS expects commands to be serialized and acknowledged via the Control Point
 * response opcode (0x80). This state is used to enforce that invariant and to
 * surface the last result for logging/diagnostics.
 */
enum class FtmsCommandState {
    /** No outstanding Control Point command; safe to send a new one. */
    IDLE,
    /** A Control Point command is in flight and must be acknowledged or timed out. */
    BUSY,
    /** The last Control Point command was accepted by the device. */
    SUCCESS,
    /** The last Control Point command was rejected by the device. */
    ERROR
}

package com.example.ergometerapp.session.diagnostics

/**
 * Structured diagnostics event emitted by session orchestration flows.
 *
 * [sessionId] is nullable for events that happen before a session scope is
 * created or after it has already been finalized.
 */
data class SessionDiagnosticsEvent(
    val timestampMillis: Long,
    val sessionId: String?,
    val category: String,
    val event: String,
    val context: Map<String, String> = emptyMap(),
)

/**
 * In-memory ring buffer for recent session diagnostics.
 */
object SessionDiagnosticsBuffer {
    private const val capacity = 600
    private val events = ArrayDeque<SessionDiagnosticsEvent>(capacity)

    @Synchronized
    fun record(event: SessionDiagnosticsEvent) {
        if (events.size >= capacity) {
            events.removeFirst()
        }
        events.addLast(event)
    }

    @Synchronized
    fun snapshot(): List<SessionDiagnosticsEvent> {
        return events.toList()
    }

    @Synchronized
    fun clear() {
        events.clear()
    }
}

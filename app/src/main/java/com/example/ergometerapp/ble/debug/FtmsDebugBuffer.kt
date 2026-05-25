package com.example.ergometerapp.ble.debug

object FtmsDebugBuffer {
    private const val capacity = 200

    private val events = ArrayDeque<FtmsDebugEvent>(capacity)

    @Synchronized
    fun record(event: FtmsDebugEvent) {
        if (events.size >= capacity) {
            events.removeFirst()
        }
        events.addLast(event)
    }

    @Synchronized
    fun snapshot(): List<FtmsDebugEvent> {
        return events.toList()
    }
}

package com.example.ergometerapp

import android.content.Context
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito

class SessionDebugProbeEventQueueTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun append_writesSequentialJsonlEvents() {
        val filesDir = temporaryFolder.newFolder("files")
        val queue = createQueue(filesDir)

        queue.append(
            SessionDebugProbeEventQueue.AppendRequest(
                event = "shown",
                screen = "SESSION",
                title = "Safety probe",
                message = "Hold steady.",
            ),
        )
        queue.append(
            SessionDebugProbeEventQueue.AppendRequest(
                event = "signal",
                screen = "SESSION",
                signal = "smooth",
                signalLabel = "Smooth",
                signalCount = 2,
                powerW = 101,
                cadenceRpm = 71,
            ),
        )

        val lines = File(filesDir, "debug/session-debug-probe-events.jsonl").readLines()
        assertEquals(2, lines.size)
        assertTrue(lines[0].contains("\"seq\":1"))
        assertTrue(lines[0].contains("\"event\":\"shown\""))
        assertTrue(lines[0].contains("\"title\":\"Safety probe\""))
        assertTrue(lines[1].contains("\"seq\":2"))
        assertTrue(lines[1].contains("\"event\":\"signal\""))
        assertTrue(lines[1].contains("\"signal\":\"smooth\""))
        assertTrue(lines[1].contains("\"powerW\":101"))
        assertTrue(lines[1].contains("\"cadenceRpm\":71"))
    }

    @Test
    fun reset_clearsPriorQueueAndRestartsSequence() {
        val filesDir = temporaryFolder.newFolder("files")
        val queue = createQueue(filesDir)
        val queueFile = File(filesDir, "debug/session-debug-probe-events.jsonl")

        queue.append(
            SessionDebugProbeEventQueue.AppendRequest(
                event = "armed",
                screen = "MENU",
            ),
        )

        assertTrue(queueFile.exists())

        queue.reset()

        assertFalse(queueFile.exists())

        queue.append(
            SessionDebugProbeEventQueue.AppendRequest(
                event = "shown",
                screen = "SESSION",
            ),
        )

        val firstLine = queueFile.readLines().single()
        assertTrue(firstLine.contains("\"seq\":1"))
        assertTrue(firstLine.contains("\"event\":\"shown\""))
    }

    private fun createQueue(filesDir: File): SessionDebugProbeEventQueue {
        val appContext = Mockito.mock(Context::class.java)
        Mockito.`when`(appContext.filesDir).thenReturn(filesDir)
        return SessionDebugProbeEventQueue(appContext)
    }
}

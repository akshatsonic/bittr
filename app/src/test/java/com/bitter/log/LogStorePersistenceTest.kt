package com.bitter.log

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class LogStorePersistenceTest {

    @Test
    fun `serialize and deserialize round trips entries`() {
        val entries = listOf(
            LogEntry(1000, LogStore.INFO, "BleMesh", "advert started"),
            LogEntry(2000, LogStore.ERROR, "Sync", "boom\nwith\nnewlines"),
            LogEntry(3000, LogStore.DEBUG, "Scan", "rssi=-50 peer=aa:bb"),
        )
        val restored = LogStore.deserializeAll(LogStore.serializeAll(entries))
        assertEquals(entries, restored)
    }

    @Test
    fun `deserialize skips malformed lines`() {
        val valid = LogStore.serializeAll(listOf(LogEntry(1000, LogStore.INFO, "Tag", "msg")))
        val raw = "garbage\n$valid\n1000\t3\tTag\nnot-enough"
        val restored = LogStore.deserializeAll(raw)
        assertEquals(1, restored.size)
        assertEquals("msg", restored[0].message)
    }

    @Test
    fun `restore seeds the store on construction`() = runTest {
        val persisted = LogStore.serializeAll(
            listOf(LogEntry(1, LogStore.WARN, "t", "old log line")),
        )
        val store = LogStore(restore = { persisted })
        assertEquals(1, store.entries.value.size)
        assertEquals("old log line", store.entries.value[0].message)
    }

    @Test
    fun `persist is called after append`() = runTest {
        var saved: List<LogEntry>? = null
        val store = LogStore(
            persist = { saved = it },
            restore = { "" },
        )
        store.append(LogStore.INFO, "t", "hello")
        assertEquals(listOf("hello"), saved?.map { it.message })
    }

    @Test
    fun `persist is called with empty list after clear`() = runTest {
        var saved: List<LogEntry>? = null
        val store = LogStore(
            persist = { saved = it },
            restore = { "" },
        )
        store.append(LogStore.INFO, "t", "hello")
        store.clear()
        assertTrue(saved?.isEmpty() == true)
    }
}

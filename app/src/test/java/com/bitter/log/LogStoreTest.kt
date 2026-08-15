package com.bitter.log

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class LogStoreTest {

    private fun store(capacity: Int = 500) = LogStore(capacity = capacity, clock = { tick })

    private var tick = 0L

    @Test
    fun `append stores entries with metadata`() = runTest {
        val s = store()
        s.append(LogStore.INFO, "BleMesh", "advert started")
        val entry = s.entries.value.single()
        assertEquals(LogStore.INFO, entry.priority)
        assertEquals("BleMesh", entry.tag)
        assertEquals("advert started", entry.message)
        assertEquals(0L, entry.timestamp)
    }

    @Test
    fun `entries appear in append order`() = runTest {
        val s = store()
        s.append(LogStore.DEBUG, "t", "first")
        tick++
        s.append(LogStore.DEBUG, "t", "second")
        assertEquals(listOf("first", "second"), s.entries.value.map { it.message })
    }

    @Test
    fun `older entries are evicted beyond capacity`() = runTest {
        val s = store(capacity = 3)
        s.append(LogStore.DEBUG, "t", "one")
        tick++
        s.append(LogStore.DEBUG, "t", "two")
        tick++
        s.append(LogStore.DEBUG, "t", "three")
        tick++
        s.append(LogStore.DEBUG, "t", "four")
        assertEquals(listOf("two", "three", "four"), s.entries.value.map { it.message })
    }

    @Test
    fun `clear empties the log`() = runTest {
        val s = store()
        s.append(LogStore.DEBUG, "t", "one")
        s.clear()
        assertEquals(0, s.entries.value.size)
    }
}

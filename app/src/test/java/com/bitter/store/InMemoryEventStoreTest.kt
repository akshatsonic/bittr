package com.bitter.store

import com.bitter.crypto.Sha256
import com.bitter.merkle.Bytes
import com.bitter.merkle.MerkleTree
import com.bitter.model.Event
import com.bitter.model.EventKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class InMemoryEventStoreTest {

    @Test
    fun `insert all then read back in chronological order`() = runTest {
        val store = InMemoryEventStore()
        val e1 = Event.create(EventKind.POST, "a", "first", null, 1000)
        val e2 = Event.create(EventKind.POST, "a", "second", null, 2000)
        store.insertAll(listOf(e1, e2), "2026-01-15")
        val events = store.eventsForDay("2026-01-15")
        assertEquals(listOf(e1.id, e2.id), events.map { it.id })
    }

    @Test
    fun `duplicate ids are ignored`() = runTest {
        val store = InMemoryEventStore()
        val e = Event.create(EventKind.POST, "a", "dup", null, 1000)
        store.insertAll(listOf(e), "2026-01-15")
        store.insertAll(listOf(e), "2026-01-15")
        assertEquals(1, store.countForDay("2026-01-15"))
    }

    @Test
    fun `leaves are sorted merkle leaf hashes`() = runTest {
        val store = InMemoryEventStore()
        val events = listOf(
            Event.create(EventKind.POST, "u", "content-c", null, 3),
            Event.create(EventKind.POST, "u", "content-a", null, 1),
            Event.create(EventKind.POST, "u", "content-b", null, 2),
        )
        store.insertAll(events, "2026-01-15")
        val leaves = store.leavesForDay("2026-01-15")
        val expected = events.map { MerkleTree.leafOf(it.id) }.sortedWith(Bytes)
        assertEquals(expected.map { Sha256.hex(it) }, leaves.map { Sha256.hex(it) })
    }
}

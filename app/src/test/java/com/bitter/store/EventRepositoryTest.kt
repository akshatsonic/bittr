package com.bitter.store

import com.bitter.crypto.Sha256
import com.bitter.model.Event
import com.bitter.model.EventKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class EventRepositoryTest {

    private val fixedClock = { 1_700_000_000_000L }

    private fun repo(username: String = "alice", store: EventStore = InMemoryEventStore()) =
        EventRepository(store, username, clock = fixedClock)

    @Test
    fun `post creates a valid authored post event`() = runTest {
        val store = InMemoryEventStore()
        val event = repo("alice", store).post("hello world")
        assertEquals(EventKind.POST, event.kind)
        assertEquals("alice", event.author)
        assertEquals("hello world", event.content)
        assertEquals(1_700_000_000_000L, event.createdAt)
        assertTrue(Event.verify(event))
        assertEquals(listOf(event.id), store.eventsForDay("2026-01-14").map { it.id })
    }

    @Test
    fun `post rejects content over 280 characters`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            repo().post("x".repeat(281))
        }
    }

    @Test
    fun `post accepts exactly 280 characters`() = runTest {
        val event = repo().post("x".repeat(280))
        assertEquals(280, event.content.length)
    }

    @Test
    fun `post rejects blank content`() = runTest {
        assertFailsWith<IllegalArgumentException> { repo().post("   ") }
    }

    @Test
    fun `like creates a like event targeting another event`() = runTest {
        val event = repo("bob").like("some-target-id")
        assertEquals(EventKind.LIKE, event.kind)
        assertEquals("some-target-id", event.targetEventId)
    }

    @Test
    fun `apply remote inserts valid events and returns the count`() = runTest {
        val store = InMemoryEventStore()
        val repo = repo("alice", store)
        val e1 = Event.create(EventKind.POST, "bob", "hi", null, fixedClock())
        val e2 = Event.create(EventKind.POST, "carol", "yo", null, fixedClock() + 1)
        val inserted = repo.applyRemote(listOf(e1, e2))
        assertEquals(2, inserted)
        assertEquals(2, store.countForDay("2026-01-14"))
    }

    @Test
    fun `apply remote rejects tampered events`() = runTest {
        val store = InMemoryEventStore()
        val repo = repo("alice", store)
        val good = Event.create(EventKind.POST, "bob", "hi", null, fixedClock())
        val bad = good.copy(content = "tampered")
        val inserted = repo.applyRemote(listOf(good, bad))
        assertEquals(1, inserted)
    }

    @Test
    fun `apply remote dedupes events already present`() = runTest {
        val store = InMemoryEventStore()
        val repo = repo("alice", store)
        val e = Event.create(EventKind.POST, "bob", "hi", null, fixedClock())
        assertEquals(1, repo.applyRemote(listOf(e)))
        assertEquals(0, repo.applyRemote(listOf(e)))
        assertEquals(1, store.countForDay("2026-01-14"))
    }

    @Test
    fun `observe timeline emits chronological events`() = runTest {
        val store = InMemoryEventStore()
        val repo = repo("alice", store)
        repo.post("first")
        repo.post("second")
        val events = repo.observeTimeline().first()
        assertEquals(listOf("first", "second"), events.map { it.content })
    }

    @Test
    fun `today leaves are sorted and match the stored events`() = runTest {
        val store = InMemoryEventStore()
        val repo = repo("alice", store)
        val e1 = repo.post("a")
        val e2 = repo.post("b")
        val leaves = repo.todayLeaves()
        val expected = listOf(e1, e2).map { com.bitter.merkle.MerkleTree.leafOf(it.id) }
            .sortedWith(com.bitter.merkle.Bytes)
        assertEquals(expected.map { Sha256.hex(it) }, leaves.map { Sha256.hex(it) })
    }
}

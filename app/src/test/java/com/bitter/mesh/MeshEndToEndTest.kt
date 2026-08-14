package com.bitter.mesh

import com.bitter.merkle.MerkleTree
import com.bitter.sync.LocalSyncServer
import com.bitter.store.EventRepository
import com.bitter.store.InMemoryEventStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

private val clock = { 1_700_000_000_000L }

class MeshEndToEndTest {

    private class MeshNode(val deviceId: Int, val username: String) {
        val store = InMemoryEventStore()
        val repository = EventRepository(store, username, clock)
        val coordinator = MeshCoordinator(repository, deviceId)

        suspend fun advertRoot(): ByteArray =
            MerkleTree.truncateRoot(MerkleTree.rootOf(repository.todayLeaves()))
    }

    private suspend fun mutualSync(a: MeshNode, b: MeshNode) {
        val serverB = LocalSyncServer(b.repository.todayEvents())
        a.coordinator.sync(serverB) { events -> b.repository.applyRemote(events) }
    }

    @Test
    fun `two nodes converge on a single post`() = runTest {
        val a = MeshNode(1, "alice")
        val b = MeshNode(2, "bob")

        a.repository.post("hello from alice")

        mutualSync(a, b)

        assertEquals(a.repository.todayEvents().size, b.repository.todayEvents().size)
        assertEquals("hello from alice", b.repository.todayEvents().single().content)
    }

    @Test
    fun `three nodes relay a post through an intermediary`() = runTest {
        val a = MeshNode(1, "alice")
        val b = MeshNode(2, "bob")
        val c = MeshNode(3, "carol")

        a.repository.post("relayed message")

        mutualSync(a, b)
        mutualSync(b, c)

        assertEquals(1, c.repository.todayEvents().size)
        assertEquals("relayed message", c.repository.todayEvents().single().content)
    }

    @Test
    fun `sync is idempotent and converges to the union`() = runTest {
        val a = MeshNode(1, "alice")
        val b = MeshNode(2, "bob")

        a.repository.post("from a")
        b.repository.post("from b")

        mutualSync(a, b)
        mutualSync(a, b)

        assertEquals(2, a.repository.todayEvents().size)
        assertEquals(2, b.repository.todayEvents().size)
        val contentsA = a.repository.todayEvents().map { it.content }.toSet()
        assertEquals(setOf("from a", "from b"), contentsA)
    }

    @Test
    fun `matching roots produce no changes`() = runTest {
        val a = MeshNode(1, "alice")
        val b = MeshNode(2, "bob")

        mutualSync(a, b)

        assertEquals(0, a.repository.todayEvents().size)
        assertEquals(0, b.repository.todayEvents().size)
        assertTrue(a.advertRoot().contentEquals(b.advertRoot()))
    }

    @Test
    fun `likes propagate like posts do`() = runTest {
        val a = MeshNode(1, "alice")
        val b = MeshNode(2, "bob")

        val post = a.repository.post("a post")
        a.repository.like(post.id)

        mutualSync(a, b)

        val kinds = b.repository.todayEvents().map { it.kind }
        assertTrue(com.bitter.model.EventKind.POST in kinds)
        assertTrue(com.bitter.model.EventKind.LIKE in kinds)
    }

    @Test
    fun `roots differ when one node has extra events`() = runTest {
        val a = MeshNode(1, "alice")
        val b = MeshNode(2, "bob")

        a.repository.post("extra")

        assertTrue(!a.advertRoot().contentEquals(b.advertRoot()))
    }
}

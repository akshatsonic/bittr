package com.bitter.mesh

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class MeshStatusStoreTest {

    private fun store() = MeshStatusStore(clock = { tick })

    private var tick = 0L

    @Test
    fun `advertising flag toggles`() {
        val s = store()
        assertEquals(false, s.status().advertising)
        s.setAdvertising(true)
        assertEquals(true, s.status().advertising)
        s.setAdvertising(false)
        assertEquals(false, s.status().advertising)
    }

    @Test
    fun `active sync is cleared by passing null`() {
        val s = store()
        s.setActiveSync(ActiveSync(0x01020304, "initiating"))
        assertEquals(0x01020304, s.status().activeSync?.deviceId)
        s.setActiveSync(null)
        assertNull(s.status().activeSync)
    }

    @Test
    fun `peer is upserted by deviceId`() = runTest {
        val s = store()
        s.upsertPeer(PeerInfo(0x01020304, "alice", "aa:bb", -50, rootMatches = false))
        s.upsertPeer(PeerInfo(0x01020304, "alice", "aa:bb", -60, rootMatches = true))
        assertEquals(1, s.status().peers.size)
        assertEquals(-60, s.status().peers.single().rssi)
        assertEquals(true, s.status().peers.single().rootMatches)
    }

    @Test
    fun `same deviceId with different addresses is deduplicated`() = runTest {
        val s = store()
        s.upsertPeer(PeerInfo(0x01020304, "alice", "aa:bb", -50, rootMatches = false))
        s.upsertPeer(PeerInfo(0x01020304, "alice", "cc:dd", -60, rootMatches = false))
        s.upsertPeer(PeerInfo(0x01020304, "alice", "ee:ff", -70, rootMatches = false))
        assertEquals(1, s.status().peers.size)
        assertEquals("ee:ff", s.status().peers.single().address)
        assertEquals(-70, s.status().peers.single().rssi)
    }

    @Test
    fun `different deviceIds with same address are both kept`() = runTest {
        val s = store()
        s.upsertPeer(PeerInfo(0x01020304, "alice", "aa:bb", -50, rootMatches = false))
        s.upsertPeer(PeerInfo(0x05060708, "bob", "aa:bb", -60, rootMatches = false))
        assertEquals(2, s.status().peers.size)
    }

    @Test
    fun `stale peers are pruned`() = runTest {
        val s = store()
        s.upsertPeer(PeerInfo(0x01020304, "alice", "aa:bb", -50, rootMatches = false))
        tick = MeshStatusStore.PEER_TTL_MS + 1
        s.upsertPeer(PeerInfo(0x05060708, "bob", "cc:dd", -70, rootMatches = false))
        assertEquals(1, s.status().peers.size)
        assertEquals("cc:dd", s.status().peers.single().address)
    }

    @Test
    fun `recent peer survives pruning`() = runTest {
        val s = store()
        s.upsertPeer(PeerInfo(0x01020304, "alice", "aa:bb", -50, rootMatches = false))
        tick = MeshStatusStore.PEER_TTL_MS - 1
        s.upsertPeer(PeerInfo(0x01020304, "alice", "aa:bb", -55, rootMatches = false))
        assertEquals(1, s.status().peers.size)
        assertEquals(-55, s.status().peers.single().rssi)
    }

    @Test
    fun `pruned peer frees capacity for new peers`() = runTest {
        val s = store()
        s.upsertPeer(PeerInfo(0x01020304, "alice", "aa:bb", -50, rootMatches = false))
        tick = MeshStatusStore.PEER_TTL_MS + 1
        s.upsertPeer(PeerInfo(0x05060708, "bob", "cc:dd", -70, rootMatches = false))
        assertTrue(s.status().peers.all { it.address != "aa:bb" })
    }

    @Test
    fun `peers is empty initially`() {
        assertTrue(store().status().peers.isEmpty())
    }
}

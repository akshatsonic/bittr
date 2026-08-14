package com.bitter.sync

import com.bitter.crypto.Sha256
import com.bitter.merkle.Bytes
import com.bitter.merkle.MerkleTree
import com.bitter.model.Event
import kotlin.test.Test
import kotlin.test.assertEquals

class SyncEngineTest {

    private fun leaf(i: Int): ByteArray {
        val bytes = ByteArray(4)
        bytes[0] = (i ushr 24).toByte()
        bytes[1] = (i ushr 16).toByte()
        bytes[2] = (i ushr 8).toByte()
        bytes[3] = i.toByte()
        return Sha256.hash(bytes)
    }

    private fun leaves(range: IntRange) = range.map { leaf(it) }.sortedWith(Bytes)

    private class FakePeer(serverLeaves: List<ByteArray>) : SyncPeer {
        private val sorted = serverLeaves.sortedWith(Bytes)
        override val leafCount: Int get() = sorted.size
        override fun nodeHash(lo: Int, hi: Int): ByteArray = MerkleTree.rootOf(sorted.subList(lo, hi))
        override fun leafAt(index: Int): ByteArray = sorted[index]
        override fun eventsForLeaves(leafHashes: List<ByteArray>): List<Event> = emptyList()
    }

    private fun bruteForce(server: List<ByteArray>, client: List<ByteArray>): List<String> {
        val clientSet = client.map { Sha256.hex(it) }.toHashSet()
        return server.filter { Sha256.hex(it) !in clientSet }.map { Sha256.hex(it) }
    }

    private fun assertMatches(server: List<ByteArray>, client: List<ByteArray>) {
        val peer = FakePeer(server)
        val actual = SyncEngine(client).computeMissing(peer).map { Sha256.hex(it) }
        assertEquals(bruteForce(server, client), actual)
    }

    @Test
    fun `no missing leaves when identical`() {
        assertMatches(leaves(0..9), leaves(0..9))
    }

    @Test
    fun `all missing when client empty`() {
        assertMatches(leaves(0..9), emptyList())
    }

    @Test
    fun `nothing missing when server empty`() {
        assertEquals(emptyList(), SyncEngine(leaves(0..9)).computeMissing(FakePeer(emptyList())))
    }

    @Test
    fun `scattered missing leaves`() {
        val server = leaves(0..99)
        val missingIdx = setOf(0, 5, 33, 66, 99)
        val client = server.filterIndexed { i, _ -> i !in missingIdx }
        assertMatches(server, client)
    }

    @Test
    fun `disjoint sets`() {
        assertMatches(leaves(0..4), leaves(100..104))
    }
}

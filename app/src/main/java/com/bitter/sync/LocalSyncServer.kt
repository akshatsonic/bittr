package com.bitter.sync

import com.bitter.crypto.Sha256
import com.bitter.merkle.Bytes
import com.bitter.merkle.MerkleTree
import com.bitter.model.Event

class LocalSyncServer(private val events: List<Event>) : SyncPeer {

    private val sortedLeaves = events.map { MerkleTree.leafOf(it.id) }.sortedWith(Bytes)
    private val eventByLeafHex = events.associateBy { Sha256.hex(MerkleTree.leafOf(it.id)) }

    override val leafCount: Int get() = sortedLeaves.size

    override fun nodeHash(lo: Int, hi: Int): ByteArray = MerkleTree.rootOf(sortedLeaves.subList(lo, hi))

    override fun leafAt(index: Int): ByteArray = sortedLeaves[index]

    override fun eventsForLeaves(leafHashes: List<ByteArray>): List<Event> =
        leafHashes.mapNotNull { eventByLeafHex[Sha256.hex(it)] }

    override fun allEvents(): List<Event> = events

    fun root(): ByteArray = MerkleTree.rootOf(sortedLeaves)

    fun truncatedRoot(): ByteArray = MerkleTree.truncateRoot(root())
}

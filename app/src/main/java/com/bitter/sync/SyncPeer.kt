package com.bitter.sync

import com.bitter.merkle.MerkleServer
import com.bitter.model.Event

interface SyncPeer : MerkleServer {
    fun eventsForLeaves(leafHashes: List<ByteArray>): List<Event>
}

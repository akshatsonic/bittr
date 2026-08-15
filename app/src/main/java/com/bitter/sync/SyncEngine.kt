package com.bitter.sync

import com.bitter.merkle.MerkleDiffer

class SyncEngine(private val localLeaves: List<ByteArray>) {
    fun computeMissing(peer: SyncPeer): List<ByteArray> = MerkleDiffer.findMissing(peer, localLeaves)
}

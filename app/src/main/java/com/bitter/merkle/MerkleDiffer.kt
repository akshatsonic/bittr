package com.bitter.merkle

import com.bitter.crypto.Sha256

object MerkleDiffer {

    fun findMissing(serverLeaves: List<ByteArray>, clientLeaves: List<ByteArray>): List<ByteArray> =
        findMissing(ListServer(serverLeaves), clientLeaves)

    fun findMissing(server: MerkleServer, clientLeaves: List<ByteArray>): List<ByteArray> {
        val client = Bytes.sorted(clientLeaves)
        val clientSet = client.map { Sha256.hex(it) }.toHashSet()
        val missing = mutableListOf<ByteArray>()

        fun walk(lo: Int, hi: Int) {
            val loVal = server.leafAt(lo)
            val hiVal = if (hi < server.leafCount) server.leafAt(hi) else null
            val inRange = client.filter { leaf ->
                Bytes.compare(leaf, loVal) >= 0 && (hiVal == null || Bytes.compare(leaf, hiVal) < 0)
            }
            if (inRange.isEmpty()) {
                for (i in lo until hi) missing.add(server.leafAt(i))
                return
            }
            if (hi - lo == 1) {
                val leaf = server.leafAt(lo)
                if (Sha256.hex(leaf) !in clientSet) missing.add(leaf)
                return
            }
            val serverHash = server.nodeHash(lo, hi)
            val clientHash = MerkleTree.rootOf(inRange)
            if (Bytes.equals(serverHash, clientHash)) return
            val mid = (lo + hi) / 2
            walk(lo, mid)
            walk(mid, hi)
        }

        if (server.leafCount > 0) walk(0, server.leafCount)
        return missing
    }
}

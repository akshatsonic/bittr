package com.bitter.merkle

import com.bitter.crypto.Sha256

object MerkleTree {

    val EMPTY_ROOT: ByteArray = ByteArray(32)

    fun leafOf(eventId: String): ByteArray = Sha256.hashUtf8(eventId)

    fun rootOf(leaves: List<ByteArray>): ByteArray {
        if (leaves.isEmpty()) return EMPTY_ROOT.copyOf()
        var level = leaves
        while (level.size > 1) {
            level = level.chunked(2).map { pair ->
                val right = if (pair.size == 1) pair[0] else pair[1]
                Sha256.hash(Sha256.concat(pair[0], right))
            }
        }
        return level[0]
    }

    fun truncateRoot(root: ByteArray, length: Int = 20): ByteArray = root.copyOf(length)
}

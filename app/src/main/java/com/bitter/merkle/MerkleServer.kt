package com.bitter.merkle

interface MerkleServer {
    val leafCount: Int
    fun nodeHash(lo: Int, hi: Int): ByteArray
    fun leafAt(index: Int): ByteArray
}

class ListServer(leaves: List<ByteArray>) : MerkleServer {
    private val sorted = Bytes.sorted(leaves)
    override val leafCount: Int get() = sorted.size
    override fun nodeHash(lo: Int, hi: Int): ByteArray = MerkleTree.rootOf(sorted.subList(lo, hi))
    override fun leafAt(index: Int): ByteArray = sorted[index]
}

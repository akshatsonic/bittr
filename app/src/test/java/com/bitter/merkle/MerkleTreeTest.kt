package com.bitter.merkle

import com.bitter.crypto.Sha256
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class MerkleTreeTest {

    private fun leafOf(s: String) = Sha256.hashUtf8(s)

    @Test
    fun `empty tree has a zero root`() {
        assertContentEquals(ByteArray(32), MerkleTree.rootOf(emptyList()))
    }

    @Test
    fun `single leaf is its own root`() {
        val leaf = leafOf("a")
        assertContentEquals(leaf, MerkleTree.rootOf(listOf(leaf)))
    }

    @Test
    fun `two leaves match known vector`() {
        val a = leafOf("a")
        val b = leafOf("b")
        val root = MerkleTree.rootOf(listOf(a, b))
        assertEquals(
            "e5a01fee14e0ed5c48714f22180f25ad8365b53f9779f79dc4a3d7e93963f94a",
            Sha256.hex(root),
        )
    }

    @Test
    fun `three leaves duplicate the last leaf`() {
        val a = leafOf("a")
        val b = leafOf("b")
        val c = leafOf("c")
        val root = MerkleTree.rootOf(listOf(a, b, c))
        assertEquals(
            "d31a37ef6ac14a2db1470c4316beb5592e6afd4465022339adafda76a18ffabe",
            Sha256.hex(root),
        )
    }

    @Test
    fun `root is independent of input list ordering when sorted`() {
        val leaves = listOf("a", "b", "c", "d").map { leafOf(it) }
        val sorted = leaves.sortedWith(Bytes)
        assertContentEquals(MerkleTree.rootOf(sorted), MerkleTree.rootOf(sorted.reversed().sortedWith(Bytes)))
    }

    @Test
    fun `leaf of event id is 32 byte sha256`() {
        assertEquals(32, MerkleTree.leafOf("some-event-id").size)
    }

    @Test
    fun `truncate root cuts to 20 bytes`() {
        val root = MerkleTree.rootOf(listOf(leafOf("a")))
        assertEquals(20, MerkleTree.truncateRoot(root).size)
        assertContentEquals(root.copyOf(20), MerkleTree.truncateRoot(root))
    }

    @Test
    fun `truncate root is stable for a given tree`() {
        val leaves = listOf("a", "b", "c").map { leafOf(it) }
        val r1 = MerkleTree.truncateRoot(MerkleTree.rootOf(leaves))
        val r2 = MerkleTree.truncateRoot(MerkleTree.rootOf(leaves))
        assertContentEquals(r1, r2)
    }
}

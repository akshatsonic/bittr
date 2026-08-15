package com.bitter.merkle

import com.bitter.crypto.Sha256
import kotlin.test.Test
import kotlin.test.assertEquals

class MerkleDifferTest {

    private fun leaf(i: Int): ByteArray {
        val bytes = ByteArray(4)
        bytes[0] = (i ushr 24).toByte()
        bytes[1] = (i ushr 16).toByte()
        bytes[2] = (i ushr 8).toByte()
        bytes[3] = i.toByte()
        return Sha256.hash(bytes)
    }

    private fun leaves(range: IntRange) = range.map { leaf(it) }.sortedWith(Bytes)

    private fun bruteForce(server: List<ByteArray>, client: List<ByteArray>): List<ByteArray> {
        val clientSet = client.map { Sha256.hex(it) }.toHashSet()
        return server.filter { Sha256.hex(it) !in clientSet }
    }

    private fun assertMatchesBruteForce(server: List<ByteArray>, client: List<ByteArray>) {
        val expected = bruteForce(server, client)
        val actual = MerkleDiffer.findMissing(server, client)
        assertEquals(expected.map { Sha256.hex(it) }, actual.map { Sha256.hex(it) })
    }

    @Test
    fun `identical sets yield no missing leaves`() {
        assertMatchesBruteForce(leaves(0..9), leaves(0..9))
    }

    @Test
    fun `empty server yields nothing`() {
        assertEquals(emptyList(), MerkleDiffer.findMissing(emptyList(), leaves(0..5)))
    }

    @Test
    fun `empty client yields everything missing`() {
        assertMatchesBruteForce(leaves(0..9), emptyList())
    }

    @Test
    fun `disjoint sets yield all server leaves missing`() {
        assertMatchesBruteForce(leaves(0..4), leaves(100..104))
    }

    @Test
    fun `single missing leaf at the start`() {
        assertMatchesBruteForce(leaves(0..9), leaves(1..9))
    }

    @Test
    fun `single missing leaf at the end`() {
        assertMatchesBruteForce(leaves(0..9), leaves(0..8))
    }

    @Test
    fun `single missing leaf in the middle`() {
        val server = leaves(0..9)
        val client = (leaves(0..4) + leaves(6..9)).sortedWith(Bytes)
        assertMatchesBruteForce(server, client)
    }

    @Test
    fun `client has extra leaves not on server`() {
        assertMatchesBruteForce(leaves(0..9), leaves(-5..14))
    }

    @Test
    fun `odd and even sized server with scattered gaps`() {
        assertMatchesBruteForce(leaves(0..12), leaves(0..12).filterIndexed { i, _ -> i % 3 != 0 })
        assertMatchesBruteForce(leaves(0..11), leaves(0..11).filterIndexed { i, _ -> i % 2 != 0 })
    }

    @Test
    fun `one hundred server leaves with ten missing`() {
        val server = leaves(0..99)
        val missingIdx = setOf(3, 17, 42, 55, 63, 71, 80, 88, 92, 99)
        val client = server.filterIndexed { i, _ -> i !in missingIdx }
        assertMatchesBruteForce(server, client)
    }

    @Test
    fun `server is a superset of client`() {
        assertMatchesBruteForce(leaves(0..49), leaves(0..19))
    }

    @Test
    fun `client is a superset of server`() {
        assertMatchesBruteForce(leaves(0..19), leaves(0..49))
    }
}

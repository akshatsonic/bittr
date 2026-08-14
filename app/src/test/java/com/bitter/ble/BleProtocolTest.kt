package com.bitter.ble

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class BleProtocolTest {

    @Test
    fun `node hash query round trips`() {
        val query = BleProtocol.decodeQuery(BleProtocol.encodeNodeHashQuery(3, 7)) as BleProtocol.Query.NodeHash
        assertEquals(3, query.lo)
        assertEquals(7, query.hi)
    }

    @Test
    fun `leaf count query round trips`() {
        assertEquals(BleProtocol.Query.LeafCount, BleProtocol.decodeQuery(BleProtocol.encodeLeafCountQuery()))
    }

    @Test
    fun `node hash answer round trips`() {
        val hash = ByteArray(32) { it.toByte() }
        val answer = BleProtocol.decodeAnswer(BleProtocol.encodeNodeHashAnswer(1, 2, hash)) as BleProtocol.Answer.NodeHash
        assertEquals(1, answer.lo)
        assertEquals(2, answer.hi)
        assertContentEquals(hash, answer.hash)
    }

    @Test
    fun `leaf count answer round trips`() {
        val answer = BleProtocol.decodeAnswer(BleProtocol.encodeLeafCountAnswer(42)) as BleProtocol.Answer.LeafCount
        assertEquals(42, answer.count)
    }

    @Test
    fun `fetch request round trips multiple leaves`() {
        val leaves = listOf(ByteArray(32) { 1 }, ByteArray(32) { 2 }, ByteArray(32) { 3 })
        val decoded = BleProtocol.decodeFetchRequest(BleProtocol.encodeFetchRequest(leaves))!!
        assertEquals(3, decoded.size)
        assertContentEquals(leaves[0], decoded[0])
        assertContentEquals(leaves[2], decoded[2])
    }

    @Test
    fun `event payload round trips`() {
        val bytes = byteArrayOf(1, 2, 3, 4, 5)
        assertContentEquals(bytes, BleProtocol.decodeEventPayload(BleProtocol.encodeEventPayload(bytes)))
    }

    @Test
    fun `decode query rejects garbage`() {
        assertEquals(null, BleProtocol.decodeQuery(byteArrayOf(0x7F, 0x00)))
    }
}

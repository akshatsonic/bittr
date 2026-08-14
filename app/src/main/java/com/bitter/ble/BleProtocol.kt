package com.bitter.ble

import java.util.UUID

object BleProtocol {

    val SERVICE_UUID: UUID = UUID.fromString("7b3e4c10-0000-1000-8000-00805f9b34fb")
    val CHAR_MERKLE_QUERY: UUID = UUID.fromString("7b3e4c10-0001-1000-8000-00805f9b34fb")
    val CHAR_EVENT_FETCH: UUID = UUID.fromString("7b3e4c10-0002-1000-8000-00805f9b34fb")
    val CHAR_IDENTITY: UUID = UUID.fromString("7b3e4c10-0003-1000-8000-00805f9b34fb")

    const val ROOM_ID = 0x0A
    const val ADVERT_COMPANY_ID = 0xFFFF

    const val OP_NODE_HASH = 0
    const val OP_LEAF_COUNT = 1

    sealed interface Query {
        data object LeafCount : Query
        data class NodeHash(val lo: Int, val hi: Int) : Query
    }

    sealed interface Answer {
        data class NodeHash(val lo: Int, val hi: Int, val hash: ByteArray) : Answer
        data class LeafCount(val count: Int) : Answer
    }

    fun encodeNodeHashQuery(lo: Int, hi: Int): ByteArray {
        val buf = java.nio.ByteBuffer.allocate(9)
        buf.put(OP_NODE_HASH.toByte())
        buf.putInt(lo)
        buf.putInt(hi)
        return buf.array()
    }

    fun encodeLeafCountQuery(): ByteArray = byteArrayOf(OP_LEAF_COUNT.toByte())

    fun decodeQuery(payload: ByteArray): Query? = try {
        val buf = java.nio.ByteBuffer.wrap(payload)
        when (buf.get().toInt()) {
            OP_NODE_HASH -> Query.NodeHash(buf.int, buf.int)
            OP_LEAF_COUNT -> Query.LeafCount
            else -> null
        }
    } catch (e: Exception) {
        null
    }

    fun encodeNodeHashAnswer(lo: Int, hi: Int, hash: ByteArray): ByteArray {
        val buf = java.nio.ByteBuffer.allocate(9 + 32)
        buf.put(OP_NODE_HASH.toByte())
        buf.putInt(lo)
        buf.putInt(hi)
        buf.put(hash.copyOf(32))
        return buf.array()
    }

    fun encodeLeafCountAnswer(count: Int): ByteArray {
        val buf = java.nio.ByteBuffer.allocate(5)
        buf.put(OP_LEAF_COUNT.toByte())
        buf.putInt(count)
        return buf.array()
    }

    fun decodeAnswer(payload: ByteArray): Answer? {
        return try {
            val buf = java.nio.ByteBuffer.wrap(payload)
            when (buf.get().toInt()) {
                OP_NODE_HASH -> {
                    val lo = buf.int
                    val hi = buf.int
                    val hash = ByteArray(32).also { buf.get(it) }
                    Answer.NodeHash(lo, hi, hash)
                }
                OP_LEAF_COUNT -> Answer.LeafCount(buf.int)
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    fun encodeFetchRequest(leaves: List<ByteArray>): ByteArray {
        val buf = java.nio.ByteBuffer.allocate(1 + 2 + leaves.size * 32)
        buf.put(0)
        buf.putShort(leaves.size.toShort())
        leaves.forEach { buf.put(it.copyOf(32)) }
        return buf.array()
    }

    fun decodeFetchRequest(payload: ByteArray): List<ByteArray>? {
        return try {
            val buf = java.nio.ByteBuffer.wrap(payload)
            if (buf.get().toInt() != 0) return null
            val count = buf.short.toInt() and 0xFFFF
            List(count) { ByteArray(32).also { buf.get(it) } }
        } catch (e: Exception) {
            null
        }
    }

    fun encodeEventPayload(eventBytes: ByteArray): ByteArray {
        val buf = java.nio.ByteBuffer.allocate(1 + 4 + eventBytes.size)
        buf.put(1)
        buf.putInt(eventBytes.size)
        buf.put(eventBytes)
        return buf.array()
    }

    fun decodeEventPayload(payload: ByteArray): ByteArray? {
        return try {
            val buf = java.nio.ByteBuffer.wrap(payload)
            if (buf.get().toInt() != 1) return null
            val len = buf.int
            if (len < 0) return null
            ByteArray(len).also { buf.get(it) }
        } catch (e: Exception) {
            null
        }
    }
}

package com.bitter.ble

import java.util.UUID

object BleProtocol {

    val SERVICE_UUID: UUID = UUID.fromString("7b3e4c10-0000-1000-8000-00805f9b34fb")
    val CHAR_MERKLE_QUERY: UUID = UUID.fromString("7b3e4c10-0001-1000-8000-00805f9b34fb")
    val CHAR_EVENT_FETCH: UUID = UUID.fromString("7b3e4c10-0002-1000-8000-00805f9b34fb")
    val CHAR_IDENTITY: UUID = UUID.fromString("7b3e4c10-0003-1000-8000-00805f9b34fb")

    const val ROOM_ID = 0x0A
    const val ADVERT_COMPANY_ID = 0xFFFF
    const val NICKNAME_COMPANY_ID = 0xFFFE

    // MERKLE_QUERY opcodes
    const val OP_NODE_HASH = 0
    const val OP_LEAF_COUNT = 1
    const val OP_ALL_EVENTS = 3

    // EVENT_FETCH opcodes
    const val OP_FETCH = 0 // client -> server: request events for a list of leaves
    const val OP_EVENT = 1 // server -> client: stream one event
    const val OP_PUSH = 2 // client -> server: push one event

    sealed interface Query {
        data object LeafCount : Query
        data class NodeHash(val lo: Int, val hi: Int) : Query
        data object AllEvents : Query
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

    fun encodeAllEventsQuery(): ByteArray = byteArrayOf(OP_ALL_EVENTS.toByte())

    fun decodeQuery(payload: ByteArray): Query? = try {
        val buf = java.nio.ByteBuffer.wrap(payload)
        when (buf.get().toInt()) {
            OP_NODE_HASH -> Query.NodeHash(buf.int, buf.int)
            OP_LEAF_COUNT -> Query.LeafCount
            OP_ALL_EVENTS -> Query.AllEvents
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
        buf.put(OP_FETCH.toByte())
        buf.putShort(leaves.size.toShort())
        leaves.forEach { buf.put(it.copyOf(32)) }
        return buf.array()
    }

    fun decodeFetchRequest(payload: ByteArray): List<ByteArray>? {
        return try {
            val buf = java.nio.ByteBuffer.wrap(payload)
            if (buf.get().toInt() != OP_FETCH) return null
            val count = buf.short.toInt() and 0xFFFF
            List(count) { ByteArray(32).also { buf.get(it) } }
        } catch (e: Exception) {
            null
        }
    }

    fun encodeEventStream(eventBytes: ByteArray): ByteArray {
        val buf = java.nio.ByteBuffer.allocate(1 + eventBytes.size)
        buf.put(OP_EVENT.toByte())
        buf.put(eventBytes)
        return buf.array()
    }

    fun decodeEventStream(payload: ByteArray): ByteArray? {
        if (payload.isEmpty() || (payload[0].toInt() and 0xFF) != OP_EVENT) return null
        return payload.copyOfRange(1, payload.size)
    }

    fun encodePush(eventBytes: ByteArray): ByteArray {
        val buf = java.nio.ByteBuffer.allocate(1 + eventBytes.size)
        buf.put(OP_PUSH.toByte())
        buf.put(eventBytes)
        return buf.array()
    }

    fun decodePush(payload: ByteArray): ByteArray? {
        if (payload.isEmpty() || (payload[0].toInt() and 0xFF) != OP_PUSH) return null
        return payload.copyOfRange(1, payload.size)
    }

    fun encodeIdentityAnnounce(deviceId: Int, username: String): ByteArray {
        val user = username.toByteArray(Charsets.UTF_8)
        val buf = java.nio.ByteBuffer.allocate(4 + 1 + user.size)
        buf.putInt(deviceId)
        buf.put(user.size.toByte())
        buf.put(user)
        return buf.array()
    }

    fun decodeIdentityAnnounce(payload: ByteArray): Pair<Int, String>? {
        if (payload.size < 5) return null
        return try {
            val buf = java.nio.ByteBuffer.wrap(payload)
            val deviceId = buf.int
            val userLen = buf.get().toInt() and 0xFF
            if (buf.remaining() != userLen) return null
            val username = ByteArray(userLen).also { buf.get(it) }.toString(Charsets.UTF_8)
            deviceId to username
        } catch (e: Exception) {
            null
        }
    }
}

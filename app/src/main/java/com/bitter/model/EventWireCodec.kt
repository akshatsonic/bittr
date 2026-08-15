package com.bitter.model

import com.bitter.crypto.Sha256

object EventWireCodec {

    fun encode(event: Event): ByteArray {
        val author = event.author.toByteArray(Charsets.UTF_8)
        val content = event.content.toByteArray(Charsets.UTF_8)
        val target = event.targetEventId?.toByteArray(Charsets.UTF_8)
        val size = 1 + 8 + 2 + author.size + 2 + content.size + 1 + (target?.let { 2 + it.size } ?: 0)
        val buf = java.nio.ByteBuffer.allocate(size)
        buf.put(kindToByte(event.kind))
        buf.putLong(event.createdAt)
        buf.putShort(author.size.toShort())
        buf.put(author)
        buf.putShort(content.size.toShort())
        buf.put(content)
        if (target != null) {
            buf.put(1)
            buf.putShort(target.size.toShort())
            buf.put(target)
        } else {
            buf.put(0)
        }
        return buf.array()
    }

    fun decode(bytes: ByteArray): Event? {
        return try {
            val buf = java.nio.ByteBuffer.wrap(bytes)
            val kind = byteToKind(buf.get()) ?: return null
            val createdAt = buf.long
            val authorLen = buf.short.toInt() and 0xFFFF
            val author = readUtf8(buf, authorLen) ?: return null
            val contentLen = buf.short.toInt() and 0xFFFF
            val content = readUtf8(buf, contentLen) ?: return null
            val hasTarget = buf.get().toInt()
            val target = when (hasTarget) {
                1 -> {
                    val targetLen = buf.short.toInt() and 0xFFFF
                    readUtf8(buf, targetLen) ?: return null
                }
                0 -> null
                else -> return null
            }
            if (buf.hasRemaining()) return null
            Event.create(kind, author, content, target, createdAt)
        } catch (e: Exception) {
            null
        }
    }

    private fun readUtf8(buf: java.nio.ByteBuffer, length: Int): String? = try {
        ByteArray(length).also { buf.get(it) }.toString(Charsets.UTF_8)
    } catch (e: Exception) {
        null
    }

    private fun kindToByte(kind: EventKind): Byte = when (kind) {
        EventKind.POST -> 0
        EventKind.LIKE -> 1
        EventKind.UNLIKE -> 2
    }

    private fun byteToKind(b: Byte): EventKind? = when (b.toInt()) {
        0 -> EventKind.POST
        1 -> EventKind.LIKE
        2 -> EventKind.UNLIKE
        else -> null
    }
}

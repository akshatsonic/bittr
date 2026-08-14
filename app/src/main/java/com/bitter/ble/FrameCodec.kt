package com.bitter.ble

object FrameCodec {

    fun encode(payload: ByteArray): ByteArray {
        val out = ByteArray(4 + payload.size)
        writeInt(out, 0, payload.size)
        payload.copyInto(out, 4)
        return out
    }

    fun decode(stream: ByteArray): List<ByteArray> {
        val frames = mutableListOf<ByteArray>()
        var offset = 0
        while (offset + 4 <= stream.size) {
            val length = readInt(stream, offset)
            offset += 4
            if (length < 0 || offset + length > stream.size) break
            frames.add(stream.copyOfRange(offset, offset + length))
            offset += length
        }
        return frames
    }

    private fun writeInt(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value ushr 24).toByte()
        bytes[offset + 1] = (value ushr 16).toByte()
        bytes[offset + 2] = (value ushr 8).toByte()
        bytes[offset + 3] = value.toByte()
    }

    private fun readInt(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)
}

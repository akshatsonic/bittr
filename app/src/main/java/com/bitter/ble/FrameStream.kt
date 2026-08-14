package com.bitter.ble

class FrameStream {
    private var buffer = ByteArray(0)

    fun feed(bytes: ByteArray): List<ByteArray> {
        buffer = buffer + bytes
        val frames = FrameCodec.decode(buffer)
        var consumed = 0
        for (frame in frames) consumed += 4 + frame.size
        buffer = buffer.copyOfRange(consumed, buffer.size)
        return frames
    }
}

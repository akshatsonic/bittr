package com.bitter.ble

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FrameStreamTest {

    @Test
    fun `single frame fed all at once yields one frame`() {
        val stream = FrameStream()
        val frames = stream.feed(FrameCodec.encode(byteArrayOf(1, 2, 3)))
        assertEquals(1, frames.size)
        assertContentEquals(byteArrayOf(1, 2, 3), frames[0])
    }

    @Test
    fun `frame fed byte by byte still assembles correctly`() {
        val stream = FrameStream()
        val full = FrameCodec.encode(byteArrayOf(1, 2, 3, 4, 5))
        var frames = emptyList<ByteArray>()
        for (b in full) {
            frames = frames + stream.feed(byteArrayOf(b))
        }
        assertEquals(1, frames.size)
        assertContentEquals(byteArrayOf(1, 2, 3, 4, 5), frames[0])
    }

    @Test
    fun `multiple frames split across arbitrary chunks reassemble in order`() {
        val stream = FrameStream()
        val f1 = FrameCodec.encode(byteArrayOf(1, 2))
        val f2 = FrameCodec.encode(byteArrayOf(3, 4, 5))
        val f3 = FrameCodec.encode(byteArrayOf(6))
        val all = f1 + f2 + f3
        val chunk1 = all.copyOfRange(0, 3)
        val chunk2 = all.copyOfRange(3, all.size)
        val frames = stream.feed(chunk1) + stream.feed(chunk2)
        assertEquals(3, frames.size)
        assertContentEquals(byteArrayOf(1, 2), frames[0])
        assertContentEquals(byteArrayOf(3, 4, 5), frames[1])
        assertContentEquals(byteArrayOf(6), frames[2])
    }

    @Test
    fun `partial trailing data is buffered for the next feed`() {
        val stream = FrameStream()
        val full = FrameCodec.encode(byteArrayOf(1, 2, 3))
        assertTrue(stream.feed(full.copyOfRange(0, 3)).isEmpty())
        val frames = stream.feed(full.copyOfRange(3, full.size))
        assertEquals(1, frames.size)
        assertContentEquals(byteArrayOf(1, 2, 3), frames[0])
    }
}

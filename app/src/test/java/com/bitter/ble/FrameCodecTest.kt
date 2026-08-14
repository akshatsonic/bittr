package com.bitter.ble

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FrameCodecTest {

    @Test
    fun `encodes a length prefixed frame`() {
        val frame = FrameCodec.encode(byteArrayOf(1, 2, 3))
        assertEquals(7, frame.size)
        assertEquals(3, frame[3].toInt() and 0xFF)
        assertContentEquals(byteArrayOf(0, 0, 0, 3, 1, 2, 3), frame)
    }

    @Test
    fun `decodes a single frame`() {
        val frames = FrameCodec.decode(byteArrayOf(0, 0, 0, 3, 9, 8, 7))
        assertEquals(1, frames.size)
        assertContentEquals(byteArrayOf(9, 8, 7), frames[0])
    }

    @Test
    fun `decodes multiple concatenated frames`() {
        val f1 = FrameCodec.encode(byteArrayOf(1, 2))
        val f2 = FrameCodec.encode(byteArrayOf(3, 4, 5))
        val frames = FrameCodec.decode(f1 + f2)
        assertEquals(2, frames.size)
        assertContentEquals(byteArrayOf(1, 2), frames[0])
        assertContentEquals(byteArrayOf(3, 4, 5), frames[1])
    }

    @Test
    fun `empty payload frames correctly`() {
        val frames = FrameCodec.decode(FrameCodec.encode(byteArrayOf()))
        assertEquals(1, frames.size)
        assertEquals(0, frames[0].size)
    }

    @Test
    fun `ignores trailing partial data`() {
        val frame = FrameCodec.encode(byteArrayOf(1, 2, 3))
        val frames = FrameCodec.decode(frame + byteArrayOf(0, 0))
        assertEquals(1, frames.size)
        assertContentEquals(byteArrayOf(1, 2, 3), frames[0])
    }

    @Test
    fun `decodes empty stream to no frames`() {
        assertTrue(FrameCodec.decode(byteArrayOf()).isEmpty())
    }
}

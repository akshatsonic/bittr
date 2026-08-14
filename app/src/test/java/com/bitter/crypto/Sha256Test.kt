package com.bitter.crypto

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class Sha256Test {

    @Test
    fun `hashes empty string to known vector`() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            Sha256.hexOfUtf8(""),
        )
    }

    @Test
    fun `hashes abc to known vector`() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            Sha256.hexOfUtf8("abc"),
        )
    }

    @Test
    fun `hash is always 32 bytes`() {
        assertEquals(32, Sha256.hashUtf8("").size)
        assertEquals(32, Sha256.hashUtf8("some longer input").size)
    }

    @Test
    fun `hex encodes bytes correctly`() {
        assertEquals("00ff10", Sha256.hex(byteArrayOf(0x00, 0xff.toByte(), 0x10)))
    }

    @Test
    fun `concat joins two byte arrays`() {
        assertContentEquals(
            byteArrayOf(1, 2, 3, 4),
            Sha256.concat(byteArrayOf(1, 2), byteArrayOf(3, 4)),
        )
    }
}

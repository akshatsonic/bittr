package com.bitter.ble

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NicknamePacketTest {

    @Test
    fun `packet fits legacy BLE scan response`() {
        val packet = NicknamePacket.encode(1, 0x01020304, "alice")!!
        assertTrue(packet.size <= 27)
        assertEquals(11, packet.size)
    }

    @Test
    fun `encode and decode round trip`() {
        val bytes = NicknamePacket.encode(1, 0x12345678, "cool-cat")
        val decoded = NicknamePacket.decode(bytes)
        assertEquals(1, decoded?.version)
        assertEquals(0x12345678, decoded?.deviceId)
        assertEquals("cool-cat", decoded?.nickname)
    }

    @Test
    fun `empty nickname round trips`() {
        val decoded = NicknamePacket.decode(NicknamePacket.encode(1, 0x01020304, ""))
        assertEquals("", decoded?.nickname)
    }

    @Test
    fun `unicode nickname round trips`() {
        val decoded = NicknamePacket.decode(NicknamePacket.encode(1, 0x01020304, "renard malin"))
        assertEquals("renard malin", decoded?.nickname)
    }

    @Test
    fun `decode rejects wrong length`() {
        assertNull(NicknamePacket.decode(ByteArray(5)))
        assertNull(NicknamePacket.decode(ByteArray(28)))
    }

    @Test
    fun `decode rejects truncated nickname`() {
        val bytes = NicknamePacket.encode(1, 0x01020304, "abcdef")!!
        val truncated = bytes.copyOf(8)
        assertNull(NicknamePacket.decode(truncated))
    }

    @Test
    fun `device id preserves full 4 byte range`() {
        val bytes = NicknamePacket.encode(1, 0xFFFFFFFF.toInt(), "x")
        assertEquals(0xFFFFFFFF.toInt(), NicknamePacket.decode(bytes)?.deviceId)
    }

    @Test
    fun `full 20 byte nickname fits`() {
        val nick = "a".repeat(20)
        val bytes = NicknamePacket.encode(1, 0x01020304, nick)
        assertTrue(bytes!!.size <= 27)
        assertEquals(nick, NicknamePacket.decode(bytes)?.nickname)
    }

    @Test
    fun `nickname longer than 20 bytes is rejected by decode`() {
        val bytes = NicknamePacket.encode(1, 0x01020304, "a".repeat(21))
        assertNull(bytes)
    }

    @Test
    fun `encode returns null for default username length nickname`() {
        assertNull(NicknamePacket.encode(1, 0x01020304, "bittr-lucky-quail-7650"))
    }

    @Test
    fun `encode accepts up to 20 byte nickname`() {
        val packet = NicknamePacket.encode(1, 0x01020304, "a".repeat(20))
        assertEquals(20, NicknamePacket.decode(packet)?.nickname?.length)
    }

    @Test
    fun `byteLength counts utf8 bytes not chars`() {
        assertEquals(5, NicknamePacket.byteLength("hello"))
        assertEquals(6, NicknamePacket.byteLength("héllo")) // é is 2 bytes
    }

    @Test
    fun `truncateToMaxBytes keeps short strings intact`() {
        assertEquals("hello", NicknamePacket.truncateToMaxBytes("hello"))
    }

    @Test
    fun `truncateToMaxBytes cuts to 20 bytes`() {
        assertEquals("a".repeat(20), NicknamePacket.truncateToMaxBytes("a".repeat(30)))
    }

    @Test
    fun `truncateToMaxBytes does not split a multibyte char`() {
        val nick = "a".repeat(19) + "é"
        val truncated = NicknamePacket.truncateToMaxBytes(nick)
        assertTrue(NicknamePacket.byteLength(truncated) <= 20)
        assertFalse(truncated.endsWith("\uFFFD"))
    }

    @Test
    fun `truncated nickname encodes successfully`() {
        val truncated = NicknamePacket.truncateToMaxBytes("bittr-lucky-quail-7650")
        assertTrue(NicknamePacket.encode(1, 0x01020304, truncated) != null)
    }
}

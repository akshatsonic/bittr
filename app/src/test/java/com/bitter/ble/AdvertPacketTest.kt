package com.bitter.ble

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AdvertPacketTest {

    private val root = ByteArray(20) { it.toByte() }

    @Test
    fun `packet is exactly 28 bytes`() {
        val packet = AdvertPacket(1, 0x0A, 0x01020304, root)
        assertEquals(AdvertPacket.SIZE, AdvertPacket.encode(packet).size)
    }

    @Test
    fun `encode begins with the company id 0xFFFF`() {
        val bytes = AdvertPacket.encode(AdvertPacket(1, 0x0A, 0x01020304, root))
        assertEquals(0xFF, bytes[0].toInt() and 0xFF)
        assertEquals(0xFF, bytes[1].toInt() and 0xFF)
    }

    @Test
    fun `encode and decode round trip`() {
        val packet = AdvertPacket(1, 0x0A, 0x12345678, root)
        val decoded = AdvertPacket.decode(AdvertPacket.encode(packet))
        assertEquals(packet.version, decoded?.version)
        assertEquals(packet.roomId, decoded?.roomId)
        assertEquals(packet.deviceId, decoded?.deviceId)
        assertContentEquals(packet.merkleRoot, decoded?.merkleRoot)
    }

    @Test
    fun `decode rejects wrong length`() {
        assertNull(AdvertPacket.decode(ByteArray(27)))
        assertNull(AdvertPacket.decode(ByteArray(29)))
    }

    @Test
    fun `decode rejects wrong company id`() {
        val bytes = AdvertPacket.encode(AdvertPacket(1, 0x0A, 0x01020304, root))
        bytes[0] = 0x00
        assertNull(AdvertPacket.decode(bytes))
    }

    @Test
    fun `device id preserves full 4 byte range`() {
        val packet = AdvertPacket(1, 0x0A, 0xFFFFFFFF.toInt(), root)
        assertEquals(0xFFFFFFFF.toInt(), AdvertPacket.decode(AdvertPacket.encode(packet))?.deviceId)
    }
}

package com.bitter.ble

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AdvertPacketTest {

    private val root = ByteArray(16) { it.toByte() }

    @Test
    fun `packet is exactly 22 bytes to fit legacy BLE advertising`() {
        val packet = AdvertPacket(1, 0x0A, 0x01020304, root)
        assertEquals(AdvertPacket.SIZE, AdvertPacket.encode(packet).size)
        assertEquals(22, AdvertPacket.encode(packet).size)
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
        assertNull(AdvertPacket.decode(ByteArray(21)))
        assertNull(AdvertPacket.decode(ByteArray(23)))
    }

    @Test
    fun `device id preserves full 4 byte range`() {
        val packet = AdvertPacket(1, 0x0A, 0xFFFFFFFF.toInt(), root)
        assertEquals(0xFFFFFFFF.toInt(), AdvertPacket.decode(AdvertPacket.encode(packet))?.deviceId)
    }

    @Test
    fun `root is 16 bytes`() {
        val packet = AdvertPacket(1, 0x0A, 0x01020304, root)
        assertEquals(16, AdvertPacket.decode(AdvertPacket.encode(packet))?.merkleRoot?.size)
    }
}

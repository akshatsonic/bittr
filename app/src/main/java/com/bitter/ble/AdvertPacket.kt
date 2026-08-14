package com.bitter.ble

data class AdvertPacket(
    val version: Int,
    val roomId: Int,
    val deviceId: Int,
    val merkleRoot: ByteArray,
) {
    companion object {
        const val SIZE = 28
        const val COMPANY_ID = 0xFFFF
        const val VERSION = 1

        fun encode(packet: AdvertPacket): ByteArray {
            val bytes = ByteArray(SIZE)
            bytes[0] = 0xFF.toByte()
            bytes[1] = 0xFF.toByte()
            bytes[2] = packet.version.toByte()
            bytes[3] = packet.roomId.toByte()
            val id = packet.deviceId
            bytes[4] = (id ushr 24).toByte()
            bytes[5] = (id ushr 16).toByte()
            bytes[6] = (id ushr 8).toByte()
            bytes[7] = id.toByte()
            packet.merkleRoot.copyOf(20).copyInto(bytes, 8)
            return bytes
        }

        fun decode(bytes: ByteArray): AdvertPacket? {
            if (bytes.size != SIZE) return null
            if ((bytes[0].toInt() and 0xFF) != 0xFF || (bytes[1].toInt() and 0xFF) != 0xFF) return null
            val version = bytes[2].toInt() and 0xFF
            val roomId = bytes[3].toInt() and 0xFF
            val deviceId = ((bytes[4].toInt() and 0xFF) shl 24) or
                ((bytes[5].toInt() and 0xFF) shl 16) or
                ((bytes[6].toInt() and 0xFF) shl 8) or
                (bytes[7].toInt() and 0xFF)
            val root = bytes.copyOfRange(8, 28)
            return AdvertPacket(version, roomId, deviceId, root)
        }
    }
}

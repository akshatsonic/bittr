package com.bitter.ble

data class AdvertPacket(
    val version: Int,
    val roomId: Int,
    val deviceId: Int,
    val merkleRoot: ByteArray,
) {
    companion object {
        const val SIZE = 22
        const val VERSION = 1
        const val ROOT_BYTES = 16

        fun encode(packet: AdvertPacket): ByteArray {
            val bytes = ByteArray(SIZE)
            bytes[0] = packet.version.toByte()
            bytes[1] = packet.roomId.toByte()
            val id = packet.deviceId
            bytes[2] = (id ushr 24).toByte()
            bytes[3] = (id ushr 16).toByte()
            bytes[4] = (id ushr 8).toByte()
            bytes[5] = id.toByte()
            packet.merkleRoot.copyOf(ROOT_BYTES).copyInto(bytes, 6)
            return bytes
        }

        fun decode(bytes: ByteArray): AdvertPacket? {
            if (bytes.size != SIZE) return null
            val version = bytes[0].toInt() and 0xFF
            val roomId = bytes[1].toInt() and 0xFF
            val deviceId = ((bytes[2].toInt() and 0xFF) shl 24) or
                ((bytes[3].toInt() and 0xFF) shl 16) or
                ((bytes[4].toInt() and 0xFF) shl 8) or
                (bytes[5].toInt() and 0xFF)
            val root = bytes.copyOfRange(6, 6 + ROOT_BYTES)
            return AdvertPacket(version, roomId, deviceId, root)
        }
    }
}

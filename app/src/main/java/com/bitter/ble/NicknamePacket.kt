package com.bitter.ble

data class NicknamePacket(
    val version: Int,
    val deviceId: Int,
    val nickname: String,
) {
    companion object {
        const val VERSION = 1
        const val MAX_NICKNAME_BYTES = 20
        const val MAX_SIZE = 27

        fun encode(version: Int, deviceId: Int, nickname: String): ByteArray? {
            val nick = nickname.toByteArray(Charsets.UTF_8)
            if (nick.size > MAX_NICKNAME_BYTES) return null
            val bytes = ByteArray(6 + nick.size)
            bytes[0] = version.toByte()
            bytes[1] = (deviceId ushr 24).toByte()
            bytes[2] = (deviceId ushr 16).toByte()
            bytes[3] = (deviceId ushr 8).toByte()
            bytes[4] = deviceId.toByte()
            bytes[5] = nick.size.toByte()
            nick.copyInto(bytes, 6)
            return bytes
        }

        fun decode(bytes: ByteArray?): NicknamePacket? {
            if (bytes == null) return null
            if (bytes.size < 6 || bytes.size > MAX_SIZE) return null
            val version = bytes[0].toInt() and 0xFF
            val deviceId = ((bytes[1].toInt() and 0xFF) shl 24) or
                ((bytes[2].toInt() and 0xFF) shl 16) or
                ((bytes[3].toInt() and 0xFF) shl 8) or
                (bytes[4].toInt() and 0xFF)
            val nickLen = bytes[5].toInt() and 0xFF
            if (nickLen > MAX_NICKNAME_BYTES) return null
            if (6 + nickLen != bytes.size) return null
            val nickname = String(bytes, 6, nickLen, Charsets.UTF_8)
            return NicknamePacket(version, deviceId, nickname)
        }
    }
}

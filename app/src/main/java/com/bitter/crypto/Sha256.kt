package com.bitter.crypto

import java.security.MessageDigest

object Sha256 {
    private fun digest(): MessageDigest = MessageDigest.getInstance("SHA-256")

    fun hash(bytes: ByteArray): ByteArray = digest().digest(bytes)

    fun hashUtf8(text: String): ByteArray = hash(text.toByteArray(Charsets.UTF_8))

    fun hex(bytes: ByteArray): String =
        bytes.joinToString("") { byte -> "%02x".format(byte.toInt() and 0xFF) }

    fun hexOfUtf8(text: String): String = hex(hashUtf8(text))

    fun concat(left: ByteArray, right: ByteArray): ByteArray = left + right
}

package com.bitter.merkle

import java.util.Comparator

object Bytes : Comparator<ByteArray> {
    override fun compare(a: ByteArray, b: ByteArray): Int {
        val n = minOf(a.size, b.size)
        for (i in 0 until n) {
            val x = a[i].toInt() and 0xFF
            val y = b[i].toInt() and 0xFF
            if (x != y) return x - y
        }
        return a.size - b.size
    }

    fun sorted(items: List<ByteArray>): List<ByteArray> = items.sortedWith(this)

    fun equals(a: ByteArray, b: ByteArray): Boolean = compare(a, b) == 0
}

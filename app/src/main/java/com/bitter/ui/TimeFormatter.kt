package com.bitter.ui

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object TimeFormatter {
    private const val MINUTE = 60_000L
    private const val HOUR = 3_600_000L
    private const val DAY = 86_400_000L
    private const val WEEK = 7 * DAY

    private val absoluteFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("MMM d", Locale.US)

    fun format(
        epochMillis: Long,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        val diff = now - epochMillis
        return when {
            diff < MINUTE -> "now"
            diff < HOUR -> "${diff / MINUTE}m"
            diff < DAY -> "${diff / HOUR}h"
            diff < WEEK -> "${diff / DAY}d"
            else -> Instant.ofEpochMilli(epochMillis).atZone(zone).format(absoluteFormatter)
        }
    }
}

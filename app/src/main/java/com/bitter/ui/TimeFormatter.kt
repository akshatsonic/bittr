package com.bitter.ui

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object TimeFormatter {
    private val formatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("MMM d, HH:mm", Locale.US)

    fun format(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(epochMillis).atZone(zone).format(formatter)
}

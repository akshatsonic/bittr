package com.bitter.store

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object DayKey {

    private val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    fun of(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate().format(formatter)

    fun today(zone: ZoneId = ZoneId.systemDefault()): String = of(System.currentTimeMillis(), zone)
}

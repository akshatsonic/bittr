package com.bitter.store

import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class DayKeyTest {

    private val utc = ZoneId.of("UTC")

    @Test
    fun `formats epoch millis as yyyy-MM-dd in a fixed zone`() {
        assertEquals("2026-01-15", DayKey.of(1_768_435_200_000L, utc))
    }

    @Test
    fun `same instant produces different day keys in different zones`() {
        val instant = 1_700_000_000_000L
        assert(DayKey.of(instant, ZoneId.of("UTC")) != DayKey.of(instant, ZoneId.of("Asia/Jakarta")))
    }

    @Test
    fun `today is consistent with of for the same zone`() {
        val today = DayKey.today(utc)
        assertEquals(today, DayKey.of(System.currentTimeMillis(), utc))
    }

    @Test
    fun `day key boundary splits at midnight`() {
        val beforeMidnight = 1_768_435_199_999L
        val afterMidnight = 1_768_435_200_000L
        assertEquals("2026-01-14", DayKey.of(beforeMidnight, utc))
        assertEquals("2026-01-15", DayKey.of(afterMidnight, utc))
    }
}

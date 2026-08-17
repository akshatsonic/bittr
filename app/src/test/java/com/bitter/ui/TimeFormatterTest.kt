package com.bitter.ui

import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class TimeFormatterTest {

    private val utc = ZoneId.of("UTC")
    private val now = 1_700_000_000_000L // 2023-11-14T22:13:20Z

    @Test
    fun `formats very recent timestamps as now`() {
        assertEquals("now", TimeFormatter.format(now, now, utc))
        assertEquals("now", TimeFormatter.format(now - 59_000L, now, utc))
    }

    @Test
    fun `formats minutes within the hour`() {
        assertEquals("1m", TimeFormatter.format(now - 60_000L, now, utc))
        assertEquals("42m", TimeFormatter.format(now - 42 * 60_000L, now, utc))
    }

    @Test
    fun `formats hours within the day`() {
        assertEquals("1h", TimeFormatter.format(now - 3_600_000L, now, utc))
        assertEquals("23h", TimeFormatter.format(now - 23 * 3_600_000L, now, utc))
    }

    @Test
    fun `formats days within the week`() {
        assertEquals("1d", TimeFormatter.format(now - 86_400_000L, now, utc))
        assertEquals("6d", TimeFormatter.format(now - 6 * 86_400_000L, now, utc))
    }

    @Test
    fun `falls back to absolute date beyond a week`() {
        assertEquals("Jan 1", TimeFormatter.format(0L, now, utc))
        assertEquals("Nov 6", TimeFormatter.format(now - 8 * 86_400_000L, now, utc))
    }
}

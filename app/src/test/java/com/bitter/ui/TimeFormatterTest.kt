package com.bitter.ui

import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class TimeFormatterTest {

    private val utc = ZoneId.of("UTC")

    @Test
    fun `formats epoch millis with date and time`() {
        assertEquals("Jan 1, 00:00", TimeFormatter.format(0L, utc))
    }

    @Test
    fun `formats a specific timestamp`() {
        assertEquals("Nov 14, 22:13", TimeFormatter.format(1_700_000_000_000L, utc))
    }

    @Test
    fun `is deterministic for the same input and zone`() {
        assertEquals(
            TimeFormatter.format(1_768_435_200_000L, utc),
            TimeFormatter.format(1_768_435_200_000L, utc),
        )
    }
}

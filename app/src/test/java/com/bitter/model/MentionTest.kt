package com.bitter.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MentionTest {

    @Test
    fun `token wraps a username in mention braces`() {
        assertEquals("@{bittr-amber-badger-1234}", Mention.token("bittr-amber-badger-1234"))
    }

    @Test
    fun `parse returns a single text segment for plain text`() {
        assertEquals(listOf<Mention.Segment>(Mention.Segment.Text("hi")), Mention.parse("hi"))
    }

    @Test
    fun `parse returns empty list for empty content`() {
        assertEquals(emptyList(), Mention.parse(""))
    }

    @Test
    fun `parse splits text and mentions`() {
        assertEquals(
            listOf(
                Mention.Segment.Text("hi "),
                Mention.Segment.Mention("bob", "@{bob}"),
                Mention.Segment.Text(" bye"),
            ),
            Mention.parse("hi @{bob} bye"),
        )
    }

    @Test
    fun `parse handles adjacent mentions`() {
        assertEquals(
            listOf(
                Mention.Segment.Mention("a", "@{a}"),
                Mention.Segment.Mention("b", "@{b}"),
            ),
            Mention.parse("@{a}@{b}"),
        )
    }

    @Test
    fun `findAll reports token ranges`() {
        val tokens = Mention.findAll("hi @{bob} bye")
        assertEquals(listOf("bob"), tokens.map { it.username })
        assertEquals(3..8, tokens[0].range)
        assertEquals("@{bob}", tokens[0].raw)
    }

    @Test
    fun `active mention detects a partially typed mention`() {
        val active = Mention.activeMention("hi @al", 6)
        assertEquals("al", active?.query)
        assertEquals(3..5, active?.range)
    }

    @Test
    fun `active mention detects a bare at sign`() {
        val active = Mention.activeMention("hi @", 4)
        assertEquals("", active?.query)
        assertEquals(3..3, active?.range)
    }

    @Test
    fun `active mention is null after a completed token`() {
        assertNull(Mention.activeMention("hi @{alice}", 11))
    }

    @Test
    fun `active mention is null when no at sign present`() {
        assertNull(Mention.activeMention("hello world", 5))
    }

    @Test
    fun `insert replaces the active range with a token`() {
        assertEquals("hi @{bob}", Mention.insert("hi @", Mention.Active("", 3..3), "bob"))
        assertEquals("hi @{bob}", Mention.insert("hi @al", Mention.Active("al", 3..5), "bob"))
    }

    @Test
    fun `deleteBefore removes a whole token when cursor follows it`() {
        assertEquals("hi ", Mention.deleteBefore("hi @{bob}", 9))
    }

    @Test
    fun `deleteBefore removes a whole token leaving surrounding text`() {
        assertEquals("hi  x", Mention.deleteBefore("hi @{bob} x", 9))
    }

    @Test
    fun `deleteBefore removes a single character otherwise`() {
        assertEquals("ab", Mention.deleteBefore("abc", 3))
    }

    @Test
    fun `deleteBefore is a no-op at cursor zero`() {
        assertEquals("abc", Mention.deleteBefore("abc", 0))
    }

    @Test
    fun `visualize resolves alias and maps offsets for a single mention`() {
        val v = Mention.visualize("hi @{bob}!") { if (it == "bob") "Robert" else it }
        assertEquals("hi @Robert!", v.text)
        assertEquals(listOf(3..9), v.mentionRanges)
        assertEquals(
            intArrayOf(0, 1, 2, 3, 10, 10, 10, 10, 10, 10, 11).toList(),
            v.originalToTransformed.toList(),
        )
        assertEquals(
            intArrayOf(0, 1, 2, 3, 9, 9, 9, 9, 9, 9, 9, 10).toList(),
            v.transformedToOriginal.toList(),
        )
    }

    @Test
    fun `visualize maps offsets across multiple mentions`() {
        val v = Mention.visualize("@{a} @{bb}") { when (it) {
            "a" -> "X"
            "bb" -> "YY"
            else -> it
        } }
        assertEquals("@X @YY", v.text)
        assertEquals(listOf(0..1, 3..5), v.mentionRanges)
        assertEquals(
            intArrayOf(0, 2, 2, 2, 2, 3, 6, 6, 6, 6, 6).toList(),
            v.originalToTransformed.toList(),
        )
        assertEquals(
            intArrayOf(0, 4, 4, 5, 10, 10, 10).toList(),
            v.transformedToOriginal.toList(),
        )
    }

    @Test
    fun `visualize falls back to username when no alias`() {
        val v = Mention.visualize("@{bob}") { it }
        assertEquals("@bob", v.text)
        assertEquals(listOf(0..3), v.mentionRanges)
    }

    @Test
    fun `filter matches on display name prefix case insensitively`() {
        val c = listOf(
            Mention.Candidate("bob", "Robert"),
            Mention.Candidate("alice", "Alice"),
            Mention.Candidate("carol", "Carol"),
        )
        assertEquals(listOf("Robert"), Mention.filter(c, "rob").map { it.displayName })
    }

    @Test
    fun `filter matches on username prefix`() {
        val c = listOf(
            Mention.Candidate("bittr-amber-badger-1234", "Rob"),
            Mention.Candidate("alice", "Alice"),
        )
        assertEquals(listOf("Rob"), Mention.filter(c, "bittr-amber").map { it.displayName })
    }

    @Test
    fun `filter returns all candidates for empty query`() {
        val c = listOf(
            Mention.Candidate("bob", "Robert"),
            Mention.Candidate("alice", "Alice"),
        )
        assertEquals(c, Mention.filter(c, ""))
        assertEquals(c, Mention.filter(c, "   "))
    }

    @Test
    fun `filter returns empty when nothing matches`() {
        val c = listOf(Mention.Candidate("bob", "Robert"))
        assertTrue(Mention.filter(c, "zzz").isEmpty())
    }
}

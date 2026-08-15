package com.bitter.ui

import com.bitter.model.Event
import com.bitter.model.EventKind
import kotlin.test.Test
import kotlin.test.assertEquals

class TimelineModelTest {

    private val t0 = 1_700_000_000_000L

    private fun post(author: String, content: String, at: Long) =
        Event.create(EventKind.POST, author, content, null, at)

    private fun like(author: String, target: String, at: Long) =
        Event.create(EventKind.LIKE, author, "", target, at)

    private fun unlike(author: String, target: String, at: Long) =
        Event.create(EventKind.UNLIKE, author, "", target, at)

    @Test
    fun `posts render newest first`() {
        val items = TimelineModel.build(listOf(post("a", "second", t0 + 1), post("a", "first", t0)))
        assertEquals(listOf("second", "first"), items.map { it.post.content })
    }

    @Test
    fun `likes attach to their parent post`() {
        val p = post("a", "hello", t0)
        val l = like("b", p.id, t0 + 1)
        val items = TimelineModel.build(listOf(p, l))
        assertEquals(1, items.size)
        assertEquals(listOf(l.id), items[0].likes.map { it.id })
    }

    @Test
    fun `like on a missing parent is hidden until parent arrives`() {
        val orphanLike = like("b", "missing-parent-id", t0)
        val items = TimelineModel.build(listOf(orphanLike))
        assertEquals(0, items.size)
    }

    @Test
    fun `like becomes visible once parent syncs in`() {
        val p = post("a", "hello", t0)
        val l = like("b", p.id, t0 + 1)
        val hidden = TimelineModel.build(listOf(l))
        assertEquals(0, hidden.size)
        val revealed = TimelineModel.build(listOf(l, p))
        assertEquals(listOf(l.id), revealed[0].likes.map { it.id })
    }

    @Test
    fun `multiple likes on the same post are all attached`() {
        val p = post("a", "hello", t0)
        val l1 = like("b", p.id, t0 + 1)
        val l2 = like("c", p.id, t0 + 2)
        val items = TimelineModel.build(listOf(p, l1, l2))
        assertEquals(setOf(l1.id, l2.id), items[0].likes.map { it.id }.toSet())
    }

    @Test
    fun `display name overrides author when mapped`() {
        val p = post("bob", "hello", t0)
        val items = TimelineModel.build(listOf(p), displayNames = mapOf("bob" to "cool-cat"))
        assertEquals("cool-cat", items[0].displayAuthor)
    }

    @Test
    fun `author is used when no display name is mapped`() {
        val p = post("bob", "hello", t0)
        val items = TimelineModel.build(listOf(p), displayNames = mapOf("alice" to "x"))
        assertEquals("bob", items[0].displayAuthor)
    }

    @Test
    fun `display name applies to likes too`() {
        val p = post("bob", "hello", t0)
        val l = like("alice", p.id, t0 + 1)
        val items = TimelineModel.build(listOf(p, l), displayNames = mapOf("alice" to "a-nick"))
        assertEquals("a-nick", items[0].displayLikes.single().author)
    }

    @Test
    fun `unlike removes a like`() {
        val p = post("bob", "hello", t0)
        val l = like("alice", p.id, t0 + 1)
        val u = unlike("alice", p.id, t0 + 2)
        val items = TimelineModel.build(listOf(p, l, u))
        assertEquals(0, items[0].likes.size)
    }

    @Test
    fun `unlike only removes the same authors like`() {
        val p = post("bob", "hello", t0)
        val l1 = like("alice", p.id, t0 + 1)
        val l2 = like("carol", p.id, t0 + 2)
        val u = unlike("alice", p.id, t0 + 3)
        val items = TimelineModel.build(listOf(p, l1, l2, u))
        assertEquals(listOf("carol"), items[0].likes.map { it.author })
    }

    @Test
    fun `liked by me is true for my like`() {
        val p = post("bob", "hello", t0)
        val l = like("me", p.id, t0 + 1)
        val items = TimelineModel.build(listOf(p, l), myUsername = "me")
        assertEquals(true, items[0].likedByMe)
    }

    @Test
    fun `liked by me is false after my unlike`() {
        val p = post("bob", "hello", t0)
        val l = like("me", p.id, t0 + 1)
        val u = unlike("me", p.id, t0 + 2)
        val items = TimelineModel.build(listOf(p, l, u), myUsername = "me")
        assertEquals(false, items[0].likedByMe)
    }

    @Test
    fun `liked by me is false for someone elses like`() {
        val p = post("bob", "hello", t0)
        val l = like("alice", p.id, t0 + 1)
        val items = TimelineModel.build(listOf(p, l), myUsername = "me")
        assertEquals(false, items[0].likedByMe)
    }

    @Test
    fun `re-like after unlike restores the like`() {
        val p = post("bob", "hello", t0)
        val l1 = like("me", p.id, t0 + 1)
        val u = unlike("me", p.id, t0 + 2)
        val l2 = like("me", p.id, t0 + 3)
        val items = TimelineModel.build(listOf(p, l1, u, l2), myUsername = "me")
        assertEquals(1, items[0].likes.size)
        assertEquals(true, items[0].likedByMe)
    }

    @Test
    fun `unlike after re-like removes the like again`() {
        val p = post("bob", "hello", t0)
        val l1 = like("me", p.id, t0 + 1)
        val u1 = unlike("me", p.id, t0 + 2)
        val l2 = like("me", p.id, t0 + 3)
        val u2 = unlike("me", p.id, t0 + 4)
        val items = TimelineModel.build(listOf(p, l1, u1, l2, u2), myUsername = "me")
        assertEquals(0, items[0].likes.size)
        assertEquals(false, items[0].likedByMe)
    }

    @Test
    fun `unlike before a like does not block the like`() {
        val p = post("bob", "hello", t0)
        val u = unlike("me", p.id, t0 + 1)
        val l = like("me", p.id, t0 + 2)
        val items = TimelineModel.build(listOf(p, u, l), myUsername = "me")
        assertEquals(1, items[0].likes.size)
        assertEquals(true, items[0].likedByMe)
    }
}

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

    @Test
    fun `posts render in chronological order`() {
        val items = TimelineModel.build(listOf(post("a", "second", t0 + 1), post("a", "first", t0)))
        assertEquals(listOf("first", "second"), items.map { it.post.content })
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
}

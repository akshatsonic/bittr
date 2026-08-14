package com.bitter.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EventCodecTest {

    private val createdAt = 1_700_000_000_000L

    @Test
    fun `event id is content addressed and matches known vector`() {
        val event = Event.create(EventKind.POST, "alice", "hello world", null, createdAt)
        assertEquals(
            "f8442753195e92a2de341bdf41e948e10e496696c6f0fee03f4c93aa3031b13f",
            event.id,
        )
    }

    @Test
    fun `event signature matches known vector`() {
        val event = Event.create(EventKind.POST, "alice", "hello world", null, createdAt)
        assertEquals(
            "eee433882286363f40d91d3256616ebab5f2038a53b7120a00ca9fd63c61cb70",
            event.signature,
        )
    }

    @Test
    fun `id depends on all fields`() {
        val base = Event.create(EventKind.POST, "alice", "hi", null, createdAt)
        assertFalse(base.id == Event.create(EventKind.POST, "bob", "hi", null, createdAt).id)
        assertFalse(base.id == Event.create(EventKind.POST, "alice", "hi2", null, createdAt).id)
        assertFalse(base.id == Event.create(EventKind.POST, "alice", "hi", null, createdAt + 1).id)
        assertFalse(base.id == Event.create(EventKind.LIKE, "alice", "hi", null, createdAt).id)
    }

    @Test
    fun `verify accepts an event with intact id and signature`() {
        val event = Event.create(EventKind.POST, "alice", "hello", null, createdAt)
        assertTrue(Event.verify(event))
    }

    @Test
    fun `verify rejects a tampered event`() {
        val event = Event.create(EventKind.POST, "alice", "hello", null, createdAt)
        val tampered = event.copy(content = "tampered")
        assertFalse(Event.verify(tampered))
    }

    @Test
    fun `wire codec round trips a post event`() {
        val event = Event.create(EventKind.POST, "alice", "hello \uD83D\uDC4B world", null, createdAt)
        val decoded = EventWireCodec.decode(EventWireCodec.encode(event))
        assertEquals(event, decoded)
    }

    @Test
    fun `wire codec round trips a like event with target`() {
        val event = Event.create(EventKind.LIKE, "bob", "", "f8442753195e92a2de341bdf41e948e10e496696c6f0fee03f4c93aa3031b13f", createdAt)
        val decoded = EventWireCodec.decode(EventWireCodec.encode(event))
        assertEquals(event, decoded)
    }

    @Test
    fun `wire decode recomputes id and signature rather than trusting them`() {
        val event = Event.create(EventKind.POST, "carol", "x", null, createdAt)
        val bytes = EventWireCodec.encode(event)
        val decoded = EventWireCodec.decode(bytes)!!
        assertEquals(event.id, decoded.id)
        assertEquals(event.signature, decoded.signature)
        assertTrue(Event.verify(decoded))
    }

    @Test
    fun `wire decode returns null for garbage input`() {
        assertNull(EventWireCodec.decode(byteArrayOf(0x00, 0x01, 0x02)))
    }

    @Test
    fun `wire decode round trips empty content`() {
        val event = Event.create(EventKind.POST, "alice", "", null, createdAt)
        assertEquals(event, EventWireCodec.decode(EventWireCodec.encode(event)))
    }

    @Test
    fun `content can hold the full 280 characters`() {
        val content = "x".repeat(280)
        val event = Event.create(EventKind.POST, "alice", content, null, createdAt)
        assertEquals(content, EventWireCodec.decode(EventWireCodec.encode(event))?.content)
    }
}

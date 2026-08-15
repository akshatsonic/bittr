package com.bitter.store

import com.bitter.model.Event
import com.bitter.model.EventKind
import kotlinx.coroutines.flow.Flow

class EventRepository(
    private val store: EventStore,
    private val username: String,
    private val clock: () -> Long = System::currentTimeMillis,
    private val dayKeyOf: (Long) -> String = { DayKey.of(it) },
) {
    suspend fun post(content: String): Event {
        require(content.length <= Event.MAX_CONTENT_LENGTH) {
            "content exceeds ${Event.MAX_CONTENT_LENGTH} characters"
        }
        require(content.isNotBlank()) { "content must not be blank" }
        val event = Event.create(EventKind.POST, username, content, null, clock())
        store.insertAll(listOf(event), dayKeyOf(event.createdAt))
        return event
    }

    suspend fun like(targetEventId: String): Event {
        val event = Event.create(EventKind.LIKE, username, "", targetEventId, clock())
        store.insertAll(listOf(event), dayKeyOf(event.createdAt))
        return event
    }

    suspend fun unlike(targetEventId: String): Event {
        val event = Event.create(EventKind.UNLIKE, username, "", targetEventId, clock())
        store.insertAll(listOf(event), dayKeyOf(event.createdAt))
        return event
    }

    suspend fun applyRemote(events: List<Event>): Int {
        val valid = events.filter { Event.verify(it) }
        var inserted = 0
        valid.groupBy { dayKeyOf(it.createdAt) }.forEach { (dayKey, group) ->
            val before = store.countForDay(dayKey)
            store.insertAll(group, dayKey)
            inserted += store.countForDay(dayKey) - before
        }
        return inserted
    }

    fun observeTimeline(): Flow<List<Event>> = store.observeDay(todayKey())

    fun todayKey(): String = dayKeyOf(clock())

    suspend fun todayEvents(): List<Event> = store.eventsForDay(todayKey())

    suspend fun todayLeaves(): List<ByteArray> = store.leavesForDay(todayKey())
}

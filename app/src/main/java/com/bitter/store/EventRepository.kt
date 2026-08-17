package com.bitter.store

import com.bitter.model.Event
import com.bitter.model.EventKind
import com.bitter.model.Mention
import com.bitter.notify.NoopNotifier
import com.bitter.notify.Notifier
import kotlinx.coroutines.flow.Flow

class EventRepository(
    private val store: EventStore,
    private val username: String,
    private val clock: () -> Long = System::currentTimeMillis,
    private val dayKeyOf: (Long) -> String = { DayKey.of(it) },
    private val notifier: Notifier = NoopNotifier,
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

    suspend fun changeUsername(newName: String): Event {
        require(newName.isNotBlank()) { "name must not be blank" }
        val event = Event.create(EventKind.CHANGE_USERNAME, username, newName, null, clock())
        store.insertAll(listOf(event), dayKeyOf(event.createdAt))
        return event
    }

    suspend fun applyRemote(events: List<Event>): Int {
        val valid = events.filter { Event.verify(it) }
        val inserted = mutableListOf<Event>()
        valid.groupBy { dayKeyOf(it.createdAt) }.forEach { (dayKey, group) ->
            inserted += store.insertAll(group, dayKey)
        }
        inserted.forEach { event -> notifyIfRelevant(event) }
        return inserted.size
    }

    private suspend fun notifyIfRelevant(event: Event) {
        if (event.author == username) return
        try {
            when (event.kind) {
                EventKind.POST -> {
                    if (event.content.contains(Mention.token(username))) {
                        notifier.onMention(event.author, event.content, event.id)
                    }
                }
                EventKind.LIKE -> {
                    val targetId = event.targetEventId
                    if (targetId != null && store.findById(targetId)?.author == username) {
                        notifier.onLike(event.author, targetId)
                    }
                }
                else -> Unit
            }
        } catch (e: Exception) {
            com.bitter.log.Log.w("notify skipped for event %s: %s", event.id.take(8), e.message ?: e.toString())
        }
    }

    fun observeTimeline(): Flow<List<Event>> = store.observeAll()

    fun observeToday(): Flow<List<Event>> = store.observeDay(todayKey())

    fun observePosts(limit: Int): Flow<List<Event>> = store.observePosts(limit)

    fun observeInteractions(): Flow<List<Event>> = store.observeInteractions()

    fun observeRenames(): Flow<List<Event>> = store.observeRenames()

    fun observePostCount(): Flow<Int> = store.observePostCount()

    fun todayKey(): String = dayKeyOf(clock())

    suspend fun todayEvents(): List<Event> = store.eventsForDay(todayKey())

    suspend fun todayLeaves(): List<ByteArray> = store.leavesForDay(todayKey())
}

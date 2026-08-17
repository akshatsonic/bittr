package com.bitter.store.db

import com.bitter.merkle.Bytes
import com.bitter.merkle.MerkleTree
import com.bitter.model.Event
import com.bitter.store.EventStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomEventStore(private val dao: EventDao) : EventStore {

    override suspend fun insertAll(events: List<Event>, dayKey: String): List<Event> {
        val ids = dao.insertAll(events.map { EventEntity.from(it, dayKey) })
        return events.filterIndexed { index, _ -> ids[index] != -1L }
    }

    override suspend fun findById(id: String): Event? = dao.findById(id)?.toEvent()

    override suspend fun eventsForDay(dayKey: String): List<Event> =
        dao.eventsForDay(dayKey).mapNotNull { it.toEvent() }

    override fun observeDay(dayKey: String): Flow<List<Event>> =
        dao.observeDay(dayKey).map { rows -> rows.mapNotNull { it.toEvent() } }

    override fun observeAll(): Flow<List<Event>> =
        dao.observeAll().map { rows -> rows.mapNotNull { it.toEvent() } }

    override fun observePosts(limit: Int): Flow<List<Event>> =
        dao.observePosts(limit).map { rows -> rows.mapNotNull { it.toEvent() } }

    override fun observeInteractions(): Flow<List<Event>> =
        dao.observeInteractions().map { rows -> rows.mapNotNull { it.toEvent() } }

    override fun observeRenames(): Flow<List<Event>> =
        dao.observeRenames().map { rows -> rows.mapNotNull { it.toEvent() } }

    override fun observePostCount(): Flow<Int> = dao.observePostCount()

    override suspend fun leavesForDay(dayKey: String): List<ByteArray> =
        eventsForDay(dayKey).map { MerkleTree.leafOf(it.id) }.sortedWith(Bytes)

    override suspend fun countForDay(dayKey: String): Int = dao.countForDay(dayKey)
}

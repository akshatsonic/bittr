package com.bitter.store.db

import com.bitter.merkle.Bytes
import com.bitter.merkle.MerkleTree
import com.bitter.model.Event
import com.bitter.store.EventStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomEventStore(private val dao: EventDao) : EventStore {

    override suspend fun insertAll(events: List<Event>, dayKey: String) {
        dao.insertAll(events.map { EventEntity.from(it, dayKey) })
    }

    override suspend fun eventsForDay(dayKey: String): List<Event> =
        dao.eventsForDay(dayKey).mapNotNull { it.toEvent() }

    override fun observeDay(dayKey: String): Flow<List<Event>> =
        dao.observeDay(dayKey).map { rows -> rows.mapNotNull { it.toEvent() } }

    override suspend fun leavesForDay(dayKey: String): List<ByteArray> =
        eventsForDay(dayKey).map { MerkleTree.leafOf(it.id) }.sortedWith(Bytes)

    override suspend fun countForDay(dayKey: String): Int = dao.countForDay(dayKey)
}

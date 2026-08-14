package com.bitter.store

import com.bitter.merkle.Bytes
import com.bitter.merkle.MerkleTree
import com.bitter.model.Event
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class InMemoryEventStore : EventStore {

    private data class Row(val event: Event, val dayKey: String)

    private val mutex = Mutex()
    private val state = MutableStateFlow<List<Row>>(emptyList())

    override suspend fun insertAll(events: List<Event>, dayKey: String) {
        mutex.withLock {
            val existing = state.value.map { it.event.id }.toHashSet()
            val toAdd = events.filter { it.id !in existing }.map { Row(it, dayKey) }
            if (toAdd.isNotEmpty()) {
                state.value = state.value + toAdd
            }
        }
    }

    override suspend fun eventsForDay(dayKey: String): List<Event> =
        state.value.filter { it.dayKey == dayKey }.map { it.event }.sortedBy { it.createdAt }

    override fun observeDay(dayKey: String): Flow<List<Event>> =
        state.map { rows ->
            rows.filter { it.dayKey == dayKey }.map { it.event }.sortedBy { it.createdAt }
        }

    override suspend fun leavesForDay(dayKey: String): List<ByteArray> =
        eventsForDay(dayKey).map { MerkleTree.leafOf(it.id) }.sortedWith(Bytes)

    override suspend fun countForDay(dayKey: String): Int = eventsForDay(dayKey).size
}

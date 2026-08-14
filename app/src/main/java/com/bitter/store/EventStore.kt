package com.bitter.store

import com.bitter.model.Event
import kotlinx.coroutines.flow.Flow

interface EventStore {
    suspend fun insertAll(events: List<Event>, dayKey: String)
    suspend fun eventsForDay(dayKey: String): List<Event>
    fun observeDay(dayKey: String): Flow<List<Event>>
    suspend fun leavesForDay(dayKey: String): List<ByteArray>
    suspend fun countForDay(dayKey: String): Int
}

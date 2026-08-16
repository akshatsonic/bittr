package com.bitter.store

import com.bitter.model.Event
import kotlinx.coroutines.flow.Flow

interface EventStore {
    suspend fun insertAll(events: List<Event>, dayKey: String)
    suspend fun eventsForDay(dayKey: String): List<Event>
    fun observeDay(dayKey: String): Flow<List<Event>>
    fun observeAll(): Flow<List<Event>>
    fun observePosts(limit: Int): Flow<List<Event>>
    fun observeInteractions(): Flow<List<Event>>
    fun observeRenames(): Flow<List<Event>>
    fun observePostCount(): Flow<Int>
    suspend fun leavesForDay(dayKey: String): List<ByteArray>
    suspend fun countForDay(dayKey: String): Int
}

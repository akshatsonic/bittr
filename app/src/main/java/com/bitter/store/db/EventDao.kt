package com.bitter.store.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(events: List<EventEntity>)

    @Query("SELECT * FROM events WHERE dayKey = :dayKey ORDER BY createdAt ASC")
    suspend fun eventsForDay(dayKey: String): List<EventEntity>

    @Query("SELECT * FROM events WHERE dayKey = :dayKey ORDER BY createdAt ASC")
    fun observeDay(dayKey: String): Flow<List<EventEntity>>

    @Query("SELECT * FROM events ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<EventEntity>>

    @Query("SELECT COUNT(*) FROM events WHERE dayKey = :dayKey")
    suspend fun countForDay(dayKey: String): Int
}

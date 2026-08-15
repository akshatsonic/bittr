package com.bitter.store.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [EventEntity::class], version = 1, exportSchema = false)
abstract class BitterDatabase : RoomDatabase() {
    abstract fun eventDao(): EventDao
}

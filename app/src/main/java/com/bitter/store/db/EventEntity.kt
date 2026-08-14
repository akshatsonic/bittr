package com.bitter.store.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.bitter.model.Event
import com.bitter.model.EventKind

@Entity(
    tableName = "events",
    indices = [Index("dayKey"), Index("createdAt")],
)
data class EventEntity(
    @PrimaryKey val id: String,
    val dayKey: String,
    val kind: String,
    val author: String,
    val content: String,
    val targetEventId: String?,
    val createdAt: Long,
    val signature: String,
) {
    fun toEvent(): Event? {
        val kind = EventKind.fromCode(kind) ?: return null
        return Event(id, kind, author, content, targetEventId, createdAt, signature)
    }

    companion object {
        fun from(event: Event, dayKey: String): EventEntity = EventEntity(
            id = event.id,
            dayKey = dayKey,
            kind = event.kind.code,
            author = event.author,
            content = event.content,
            targetEventId = event.targetEventId,
            createdAt = event.createdAt,
            signature = event.signature,
        )
    }
}

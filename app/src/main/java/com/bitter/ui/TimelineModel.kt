package com.bitter.ui

import com.bitter.model.Event
import com.bitter.model.EventKind

data class TimelineItem(val post: Event, val likes: List<Event>)

object TimelineModel {
    fun build(events: List<Event>): List<TimelineItem> {
        val posts = events.filter { it.kind == EventKind.POST }.sortedBy { it.createdAt }
        val likesByTarget = events
            .filter { it.kind == EventKind.LIKE && it.targetEventId != null }
            .groupBy { it.targetEventId }
        return posts.map { post ->
            val likes = (likesByTarget[post.id] ?: emptyList()).sortedBy { it.createdAt }
            TimelineItem(post, likes)
        }
    }
}

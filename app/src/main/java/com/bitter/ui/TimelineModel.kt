package com.bitter.ui

import com.bitter.model.Event
import com.bitter.model.EventKind

data class TimelineItem(
    val post: Event,
    val likes: List<Event>,
    val displayLikes: List<Event>,
    val displayAuthor: String = post.author,
    val likedByMe: Boolean = false,
)

object TimelineModel {
    fun build(
        events: List<Event>,
        displayNames: Map<String, String> = emptyMap(),
        myUsername: String? = null,
    ): List<TimelineItem> {
        val posts = events.filter { it.kind == EventKind.POST }.sortedBy { it.createdAt }
        val likesByTarget = events
            .filter { it.kind == EventKind.LIKE && it.targetEventId != null }
            .groupBy { it.targetEventId }
        val unlikesByTarget = events
            .filter { it.kind == EventKind.UNLIKE && it.targetEventId != null }
            .groupBy { it.targetEventId }

        fun activeLikersFor(postId: String): List<String> {
            val likes = likesByTarget[postId] ?: emptyList()
            val unliked = (unlikesByTarget[postId] ?: emptyList()).map { it.author }.toSet()
            return likes.map { it.author }.distinct().filter { it !in unliked }
        }

        fun display(author: String): String = displayNames[author] ?: author

        return posts.map { post ->
            val activeAuthors = activeLikersFor(post.id)
            val likes = activeAuthors.map { author ->
                (likesByTarget[post.id] ?: emptyList()).first { it.author == author }
            }.sortedBy { it.createdAt }
            val displayLikes = likes.map { it.copy(author = display(it.author)) }
            TimelineItem(
                post = post,
                likes = likes,
                displayLikes = displayLikes,
                displayAuthor = display(post.author),
                likedByMe = myUsername?.let { it in activeAuthors } ?: false,
            )
        }
    }
}

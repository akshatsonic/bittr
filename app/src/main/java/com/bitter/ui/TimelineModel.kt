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
        posts: List<Event>,
        interactions: List<Event>,
        displayNames: Map<String, String> = emptyMap(),
        myUsername: String? = null,
    ): List<TimelineItem> {
        val sortedPosts = posts.filter { it.kind == EventKind.POST }.sortedByDescending { it.createdAt }
        val likesByTarget = interactions
            .filter { it.kind == EventKind.LIKE && it.targetEventId != null }
            .groupBy { it.targetEventId }
        val unlikesByTarget = interactions
            .filter { it.kind == EventKind.UNLIKE && it.targetEventId != null }
            .groupBy { it.targetEventId }

        fun activeLikersFor(postId: String): List<String> {
            val likes = likesByTarget[postId] ?: emptyList()
            val unlikes = unlikesByTarget[postId] ?: emptyList()
            val likers = likes.map { it.author }.distinct()
            return likers.filter { author ->
                val lastLike = likes.filter { it.author == author }.maxOfOrNull { it.createdAt }
                val lastUnlike = unlikes.filter { it.author == author }.maxOfOrNull { it.createdAt }
                when {
                    lastLike == null -> false
                    lastUnlike == null -> true
                    else -> lastLike > lastUnlike
                }
            }
        }

        fun display(author: String): String = displayNames[author] ?: author

        return sortedPosts.map { post ->
            val activeAuthors = activeLikersFor(post.id)
            val likes = activeAuthors.map { author ->
                (likesByTarget[post.id] ?: emptyList())
                    .filter { it.author == author }
                    .maxBy { it.createdAt }
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

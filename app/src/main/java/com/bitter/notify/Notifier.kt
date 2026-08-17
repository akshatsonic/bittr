package com.bitter.notify

interface Notifier {
    fun onMention(author: String, content: String, eventId: String) {}
    fun onLike(author: String, targetEventId: String) {}
}

object NoopNotifier : Notifier

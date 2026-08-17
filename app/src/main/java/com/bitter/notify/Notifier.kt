package com.bitter.notify

interface Notifier {
    fun onMention(author: String, content: String) {}
    fun onLike(author: String) {}
}

object NoopNotifier : Notifier

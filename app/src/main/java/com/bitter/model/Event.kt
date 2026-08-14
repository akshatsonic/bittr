package com.bitter.model

import com.bitter.crypto.Sha256

data class Event(
    val id: String,
    val kind: EventKind,
    val author: String,
    val content: String,
    val targetEventId: String?,
    val createdAt: Long,
    val signature: String,
) {
    companion object {
        const val MAX_CONTENT_LENGTH = 280
        private const val SIG_PREFIX = "bitter/sig/v1\n"

        fun canonical(
            kind: EventKind,
            author: String,
            content: String,
            targetEventId: String?,
            createdAt: Long,
        ): String = listOf(kind.code, author, content, targetEventId ?: "", createdAt.toString())
            .joinToString("\n")

        fun computeId(canonical: String): String = Sha256.hexOfUtf8(canonical)

        fun computeSignature(canonical: String): String = Sha256.hexOfUtf8(SIG_PREFIX + canonical)

        fun create(
            kind: EventKind,
            author: String,
            content: String,
            targetEventId: String?,
            createdAt: Long,
        ): Event {
            val canonical = canonical(kind, author, content, targetEventId, createdAt)
            return Event(
                id = computeId(canonical),
                kind = kind,
                author = author,
                content = content,
                targetEventId = targetEventId,
                createdAt = createdAt,
                signature = computeSignature(canonical),
            )
        }

        fun verify(event: Event): Boolean {
            val canonical = canonical(
                event.kind, event.author, event.content, event.targetEventId, event.createdAt,
            )
            return event.id == computeId(canonical) && event.signature == computeSignature(canonical)
        }
    }
}

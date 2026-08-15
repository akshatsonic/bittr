package com.bitter.model

enum class EventKind(val code: String) {
    POST("post"),
    LIKE("like"),
    UNLIKE("unlike");

    companion object {
        fun fromCode(code: String): EventKind? = entries.firstOrNull { it.code == code }
    }
}

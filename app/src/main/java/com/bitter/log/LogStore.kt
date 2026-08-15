package com.bitter.log

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class LogEntry(
    val timestamp: Long,
    val priority: Int,
    val tag: String,
    val message: String,
)

class LogStore(
    private val capacity: Int = DEFAULT_CAPACITY,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries

    fun append(priority: Int, tag: String, message: String) {
        val entry = LogEntry(clock(), priority, tag, message)
        _entries.value = (_entries.value + entry).takeLast(capacity)
    }

    fun clear() {
        _entries.value = emptyList()
    }

    companion object {
        const val DEFAULT_CAPACITY = 500

        const val VERBOSE = android.util.Log.VERBOSE
        const val DEBUG = android.util.Log.DEBUG
        const val INFO = android.util.Log.INFO
        const val WARN = android.util.Log.WARN
        const val ERROR = android.util.Log.ERROR
    }
}

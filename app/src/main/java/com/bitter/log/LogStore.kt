package com.bitter.log

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Base64

data class LogEntry(
    val timestamp: Long,
    val priority: Int,
    val tag: String,
    val message: String,
)

class LogStore(
    private val capacity: Int = DEFAULT_CAPACITY,
    private val clock: () -> Long = System::currentTimeMillis,
    private val persist: ((List<LogEntry>) -> Unit)? = null,
    restore: (() -> String)? = null,
) {
    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries

    init {
        restore?.invoke()?.let { raw ->
            val restored = deserializeAll(raw).takeLast(capacity)
            _entries.value = restored
        }
    }

    fun append(priority: Int, tag: String, message: String) {
        val entry = LogEntry(clock(), priority, tag, message)
        _entries.value = (_entries.value + entry).takeLast(capacity)
        persist?.invoke(_entries.value)
    }

    fun clear() {
        _entries.value = emptyList()
        persist?.invoke(_entries.value)
    }

    companion object {
        const val DEFAULT_CAPACITY = 500

        const val VERBOSE = android.util.Log.VERBOSE
        const val DEBUG = android.util.Log.DEBUG
        const val INFO = android.util.Log.INFO
        const val WARN = android.util.Log.WARN
        const val ERROR = android.util.Log.ERROR

        private val encoder = Base64.getEncoder()
        private val decoder = Base64.getDecoder()

        fun serializeAll(entries: List<LogEntry>): String =
            entries.joinToString("\n") { entry ->
                listOf(
                    entry.timestamp.toString(),
                    entry.priority.toString(),
                    encoder.encodeToString(entry.tag.toByteArray(Charsets.UTF_8)),
                    encoder.encodeToString(entry.message.toByteArray(Charsets.UTF_8)),
                ).joinToString("\t")
            }

        fun deserializeAll(raw: String): List<LogEntry> =
            raw.lineSequence().mapNotNull(::deserializeLine).toList()

        fun deserializeLine(line: String): LogEntry? {
            if (line.isBlank()) return null
            val parts = line.split('\t')
            if (parts.size != 4) return null
            return try {
                val timestamp = parts[0].toLong()
                val priority = parts[1].toInt()
                val tag = String(decoder.decode(parts[2]), Charsets.UTF_8)
                val message = String(decoder.decode(parts[3]), Charsets.UTF_8)
                LogEntry(timestamp, priority, tag, message)
            } catch (e: Exception) {
                null
            }
        }
    }
}

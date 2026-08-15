package com.bitter.mesh

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class NicknameRecord(
    val deviceId: Int,
    val nickname: String?,
    val username: String?,
    val lastSeenAt: Long,
)

class NicknameRegistry(
    val maxSize: Int = DEFAULT_MAX_SIZE,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()

    private val _records = MutableStateFlow<List<NicknameRecord>>(emptyList())
    val records: StateFlow<List<NicknameRecord>> = _records

    private val _displayNames = MutableStateFlow<Map<String, String>>(emptyMap())
    val displayNames: StateFlow<Map<String, String>> = _displayNames

    suspend fun recordNickname(deviceId: Int, nickname: String) {
        mutex.withLock {
            val now = clock()
            val existing = _records.value.find { it.deviceId == deviceId }
            val updated = if (existing == null) {
                _records.value + NicknameRecord(deviceId, nickname, null, now)
            } else {
                _records.value.map { if (it.deviceId == deviceId) it.copy(nickname = nickname, lastSeenAt = now) else it }
            }
            publish(updated)
        }
    }

    suspend fun recordUsername(deviceId: Int, username: String) {
        mutex.withLock {
            val now = clock()
            val existing = _records.value.find { it.deviceId == deviceId }
            val updated = if (existing == null) {
                _records.value + NicknameRecord(deviceId, null, username, now)
            } else {
                _records.value.map { if (it.deviceId == deviceId) it.copy(username = username, lastSeenAt = now) else it }
            }
            publish(updated)
        }
    }

    suspend fun touch(deviceId: Int) {
        mutex.withLock {
            val now = clock()
            val updated = _records.value.map { if (it.deviceId == deviceId) it.copy(lastSeenAt = now) else it }
            publish(updated)
        }
    }

    fun nicknameFor(deviceId: Int): String? =
        _records.value.find { it.deviceId == deviceId }?.nickname

    fun usernameFor(deviceId: Int): String? =
        _records.value.find { it.deviceId == deviceId }?.username

    fun deviceIdFor(username: String): Int? =
        _records.value.firstOrNull { it.username == username }?.deviceId

    fun size(): Int = _records.value.size

    fun maxSize(): Int = maxSize

    private fun publish(updated: List<NicknameRecord>) {
        val sorted = updated.sortedByDescending { it.lastSeenAt }
        val kept = if (sorted.size > maxSize) sorted.take(maxSize) else sorted
        _records.value = kept
        _displayNames.value = kept
            .filter { it.username != null }
            .associate { it.username!! to (it.nickname ?: it.username!!) }
    }

    companion object {
        const val DEFAULT_MAX_SIZE = 300
    }
}

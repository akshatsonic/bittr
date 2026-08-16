package com.bitter.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.bitter.ble.NicknamePacket
import com.bitter.log.LogStore
import com.bitter.mesh.MeshStatus
import com.bitter.mesh.MeshStatusStore
import com.bitter.mesh.NicknameRegistry
import com.bitter.model.EventKind
import com.bitter.store.EventRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TimelineViewModel(
    private val repository: EventRepository,
    val username: String,
    val deviceId: Int,
    private val nicknames: NicknameRegistry,
    private val meshStatus: MeshStatusStore,
    val ownNickname: StateFlow<String>,
    private val setOwnNickname: (String) -> Unit,
    private val logStore: LogStore,
) : ViewModel() {

    val meshState: StateFlow<MeshStatus> = meshStatus.status

    val logEntries: StateFlow<List<com.bitter.log.LogEntry>> = logStore.entries

    val displayNames: StateFlow<Map<String, String>> = combine(
        repository.observeTimeline(),
        nicknames.displayNames,
        ownNickname,
    ) { events, peerNames, own ->
        val renames = events
            .filter { it.kind == EventKind.CHANGE_USERNAME }
            .groupBy { it.author }
            .mapValues { (_, authorEvents) -> authorEvents.maxBy { it.createdAt }.content }
        peerNames + renames + (username to own)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val items: StateFlow<List<TimelineItem>> = combine(
        repository.observeTimeline(),
        displayNames,
    ) { events, names ->
        TimelineModel.build(events, names, username)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun post(content: String) {
        viewModelScope.launch { repository.post(content) }
    }

    fun like(targetEventId: String) {
        viewModelScope.launch { repository.like(targetEventId) }
    }

    fun unlike(targetEventId: String) {
        viewModelScope.launch { repository.unlike(targetEventId) }
    }

    fun setNickname(nickname: String) {
        val normalized = NicknamePacket.truncateToMaxBytes(nickname.trim())
        setOwnNickname(normalized)
        viewModelScope.launch { repository.changeUsername(normalized) }
    }

    fun clearLogs() {
        logStore.clear()
    }

    fun fingerprintFor(username: String): Int? =
        if (username == this.username) deviceId else nicknames.deviceIdFor(username)
}

class TimelineViewModelFactory(
    private val repository: EventRepository,
    private val username: String,
    private val deviceId: Int,
    private val nicknames: NicknameRegistry,
    private val meshStatus: MeshStatusStore,
    private val ownNickname: StateFlow<String>,
    private val setOwnNickname: (String) -> Unit,
    private val logStore: LogStore,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        TimelineViewModel(
            repository,
            username,
            deviceId,
            nicknames,
            meshStatus,
            ownNickname,
            setOwnNickname,
            logStore,
        ) as T
}

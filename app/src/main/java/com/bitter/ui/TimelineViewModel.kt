package com.bitter.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.bitter.ble.NicknamePacket
import com.bitter.log.LogStore
import com.bitter.mesh.MeshStatus
import com.bitter.mesh.MeshStatusStore
import com.bitter.mesh.NicknameRegistry
import com.bitter.model.Mention
import com.bitter.store.EventRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
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

    private val pageSize = PAGE_SIZE

    private val loadedCount = MutableStateFlow(pageSize)

    val meshState: StateFlow<MeshStatus> = meshStatus.status

    val logEntries: StateFlow<List<com.bitter.log.LogEntry>> = logStore.entries

    val displayNames: StateFlow<Map<String, String>> = combine(
        repository.observeRenames(),
        nicknames.displayNames,
        ownNickname,
    ) { renames, peerNames, own ->
        val renameMap = renames
            .groupBy { it.author }
            .mapValues { (_, authorEvents) -> authorEvents.maxBy { it.createdAt }.content }
        peerNames + renameMap + (username to own)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val candidates: StateFlow<List<Mention.Candidate>> = displayNames
        .map { names ->
            names.map { (username, display) -> Mention.Candidate(username, display) }
                .sortedBy { it.displayName.lowercase() }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val hasMore: StateFlow<Boolean> = combine(
        loadedCount,
        repository.observePostCount(),
    ) { loaded, total -> loaded < total }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val items: StateFlow<List<TimelineItem>> = combine(
        loadedCount.flatMapLatest { limit -> repository.observePosts(limit) },
        repository.observeInteractions(),
        displayNames,
    ) { posts, interactions, names ->
        TimelineModel.build(posts, interactions, names, username)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun loadMore() {
        loadedCount.update { it + pageSize }
    }

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

    private companion object {
        const val PAGE_SIZE = 20
    }
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

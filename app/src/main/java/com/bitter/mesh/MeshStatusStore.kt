package com.bitter.mesh

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class PeerInfo(
    val deviceId: Int,
    val nickname: String?,
    val address: String,
    val rssi: Int,
    val rootMatches: Boolean,
    val lastSeenAt: Long = 0L,
)

data class ActiveSync(
    val deviceId: Int,
    val direction: String,
)

data class MeshStatus(
    val advertising: Boolean = false,
    val activeSync: ActiveSync? = null,
    val peers: List<PeerInfo> = emptyList(),
)

class MeshStatusStore(
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()

    private val _status = MutableStateFlow(MeshStatus())
    val status: StateFlow<MeshStatus> = _status

    fun setAdvertising(advertising: Boolean) {
        _status.value = _status.value.copy(advertising = advertising)
    }

    fun setActiveSync(activeSync: ActiveSync?) {
        _status.value = _status.value.copy(activeSync = activeSync)
    }

    suspend fun upsertPeer(peer: PeerInfo) {
        mutex.withLock {
            val now = clock()
            val seen = if (peer.lastSeenAt == 0L) peer.copy(lastSeenAt = now) else peer
            val alive = _status.value.peers.filter { now - it.lastSeenAt < PEER_TTL_MS }
            val withoutAddress = alive.filter { it.address != seen.address }
            val updated = withoutAddress + seen
            _status.value = _status.value.copy(peers = updated.sortedBy { it.address })
        }
    }

    fun status(): MeshStatus = _status.value

    companion object {
        const val PEER_TTL_MS = 15_000L
    }
}

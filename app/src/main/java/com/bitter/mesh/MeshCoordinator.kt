package com.bitter.mesh

import com.bitter.store.EventRepository
import com.bitter.sync.SyncEngine
import com.bitter.sync.SyncPeer

class MeshCoordinator(
    private val repository: EventRepository,
    val deviceId: Int,
) {
    suspend fun pullFrom(peer: SyncPeer): Int {
        val localLeaves = repository.todayLeaves()
        val missing = SyncEngine(localLeaves).computeMissing(peer)
        if (missing.isEmpty()) return 0
        val events = peer.eventsForLeaves(missing)
        return repository.applyRemote(events)
    }
}

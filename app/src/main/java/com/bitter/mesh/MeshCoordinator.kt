package com.bitter.mesh

import com.bitter.store.EventRepository
import com.bitter.sync.SyncEngine
import com.bitter.sync.SyncPeer
import timber.log.Timber

class MeshCoordinator(
    private val repository: EventRepository,
    val deviceId: Int,
) {
    suspend fun sync(peer: SyncPeer, push: suspend (List<com.bitter.model.Event>) -> Unit): Int {
        val localLeaves = repository.todayLeaves()
        val missing = SyncEngine(localLeaves).computeMissing(peer)
        var applied = 0
        if (missing.isNotEmpty()) {
            val events = peer.eventsForLeaves(missing)
            applied = repository.applyRemote(events)
            Timber.d("sync pulled %d events (applied %d)", events.size, applied)
        }
        val mine = repository.todayEvents()
        if (mine.isNotEmpty()) {
            push(mine)
            Timber.d("sync pushed %d events", mine.size)
        }
        return applied
    }
}

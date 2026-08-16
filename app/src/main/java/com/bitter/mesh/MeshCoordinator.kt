package com.bitter.mesh

import com.bitter.store.EventRepository
import com.bitter.sync.SyncPeer
import com.bitter.log.Log

class MeshCoordinator(
    private val repository: EventRepository,
    val deviceId: Int,
) {
    suspend fun sync(peer: SyncPeer, push: suspend (List<com.bitter.model.Event>) -> Unit): Int {
        val remote = peer.allEvents()
        val applied = repository.applyRemote(remote)
        Log.d("sync pulled %d remote events (applied %d)", remote.size, applied)
        val mine = repository.todayEvents()
        if (mine.isNotEmpty()) {
            push(mine)
            Log.d("sync pushed %d events", mine.size)
        }
        return applied
    }
}

package com.bitter.mesh

import com.bitter.store.EventRepository
import com.bitter.sync.SyncPeer
import com.bitter.log.Log

class MeshCoordinator(
    private val repository: EventRepository,
    val deviceId: Int,
) {
    suspend fun sync(peer: SyncPeer, push: suspend (List<com.bitter.model.Event>) -> Unit): Int {
        val remote = try {
            peer.allEvents()
        } catch (e: Exception) {
            Log.w("sync pull failed: %s", e.message ?: e.toString())
            emptyList()
        }
        val applied = try {
            repository.applyRemote(remote)
        } catch (e: Exception) {
            Log.w("sync applyRemote failed: %s", e.message ?: e.toString())
            0
        }
        Log.d("sync pulled %d remote events (applied %d)", remote.size, applied)
        val mine = try {
            repository.todayEvents()
        } catch (e: Exception) {
            Log.w("sync todayEvents failed: %s", e.message ?: e.toString())
            emptyList()
        }
        if (mine.isNotEmpty()) {
            try {
                push(mine)
                Log.d("sync pushed %d events", mine.size)
            } catch (e: Exception) {
                Log.w("sync push failed: %s", e.message ?: e.toString())
            }
        } else {
            Log.w("sync nothing to push (no today events)")
        }
        return applied
    }
}

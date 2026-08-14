package com.bitter

import android.content.Context
import android.provider.Settings
import androidx.room.Room
import com.bitter.crypto.IdentityDerivation
import com.bitter.mesh.MeshCoordinator
import com.bitter.store.EventRepository
import com.bitter.store.EventStore
import com.bitter.store.db.BitterDatabase
import com.bitter.store.db.RoomEventStore
import kotlin.random.Random

class AppGraph(context: Context) {

    private val prefs = context.getSharedPreferences("bitter", Context.MODE_PRIVATE)

    val deviceId: Int = loadOrCreateDeviceId()

    val username: String = prefs.getString("username", null)
        ?: IdentityDerivation.deriveUsername(fingerprint(context)).also {
            prefs.edit().putString("username", it).apply()
        }

    val database: BitterDatabase =
        Room.databaseBuilder(context, BitterDatabase::class.java, "bitter.db").build()

    val store: EventStore = RoomEventStore(database.eventDao())

    val repository: EventRepository = EventRepository(store, username)

    val coordinator: MeshCoordinator = MeshCoordinator(repository, deviceId)

    private fun loadOrCreateDeviceId(): Int {
        val existing = prefs.getInt("deviceId", -1)
        if (existing != -1) return existing
        var id = Random.nextInt()
        while (id == -1) id = Random.nextInt()
        prefs.edit().putInt("deviceId", id).apply()
        return id
    }

    private fun fingerprint(context: Context): String =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?: "unknown-device"
}

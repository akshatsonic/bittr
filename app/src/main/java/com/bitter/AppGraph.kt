package com.bitter

import android.content.Context
import android.provider.Settings
import androidx.room.Room
import com.bitter.crypto.IdentityDerivation
import com.bitter.log.LogStore
import com.bitter.mesh.MeshCoordinator
import com.bitter.mesh.MeshStatusStore
import com.bitter.mesh.NicknameRegistry
import com.bitter.store.EventRepository
import com.bitter.store.EventStore
import com.bitter.store.db.BitterDatabase
import com.bitter.store.db.RoomEventStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.random.Random

class AppGraph(context: Context) {

    private val prefs = context.getSharedPreferences("bitter", Context.MODE_PRIVATE)

    val deviceId: Int = loadOrCreateDeviceId()

    val username: String = prefs.getString("username", null)
        ?: IdentityDerivation.deriveUsername(fingerprint(context)).also {
            prefs.edit().putString("username", it).apply()
        }

    private val nickPref: String = prefs.getString("nickname", null)
        ?: defaultNickname(deviceId).also {
            prefs.edit().putString("nickname", it).apply()
        }

    private val _ownNickname = MutableStateFlow(nickPref)
    val ownNickname: StateFlow<String> = _ownNickname

    val nicknames: NicknameRegistry = NicknameRegistry()

    val meshStatus: MeshStatusStore = MeshStatusStore()

    val logStore: LogStore = LogStore(
        persist = { entries ->
            prefs.edit().putString(LOG_PREFS_KEY, LogStore.serializeAll(entries)).apply()
        },
        restore = { prefs.getString(LOG_PREFS_KEY, null) ?: "" },
    )

    val database: BitterDatabase =
        Room.databaseBuilder(context, BitterDatabase::class.java, "bitter.db").build()

    val store: EventStore = RoomEventStore(database.eventDao())

    val repository: EventRepository = EventRepository(store, username)

    val coordinator: MeshCoordinator = MeshCoordinator(repository, deviceId)

    fun setOwnNickname(nickname: String) {
        val normalized = nickname.trim().take(NicknameRegistry.MAX_NICKNAME_CHARS)
        prefs.edit().putString("nickname", normalized).apply()
        _ownNickname.value = normalized
    }

    private fun loadOrCreateDeviceId(): Int {
        val existing = prefs.getInt("deviceId", -1)
        if (existing != -1) return existing
        var id = Random.nextInt()
        while (id == -1) id = Random.nextInt()
        prefs.edit().putInt("deviceId", id).apply()
        return id
    }

    private fun defaultNickname(deviceId: Int): String =
        "bittr-%04x".format(deviceId and 0xFFFF)

    private fun fingerprint(context: Context): String =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?: "unknown-device"

    private companion object {
        const val LOG_PREFS_KEY = "logs_v1"
    }
}

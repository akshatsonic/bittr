package com.bitter.ble

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import com.bitter.AppGraph
import com.bitter.BitterApplication
import com.bitter.R
import com.bitter.mesh.ActiveSync
import com.bitter.mesh.PeerInfo
import com.bitter.sync.LocalSyncServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber

class BleMeshService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val graph: AppGraph by lazy { (application as BitterApplication).graph }

    private var adapter: BluetoothAdapter? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private var scanner: BluetoothLeScanner? = null
    private var gattServerHandler: GattServerHandler? = null

    @Volatile
    private var currentServer: LocalSyncServer = LocalSyncServer(emptyList())

    private val seenDevices = mutableSetOf<String>()
    private val syncingDevices = mutableSetOf<String>()

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            Timber.d("ADVERTISE started ok (mode=%d)", settingsInEffect.mode)
            graph.meshStatus.setAdvertising(true)
        }

        override fun onStartFailure(errorCode: Int) {
            Timber.w("ADVERTISE failed: errorCode=%d", errorCode)
            graph.meshStatus.setAdvertising(false)
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            handleScanResult(result)
        }

        override fun onScanFailed(errorCode: Int) {
            Timber.w("SCAN failed: errorCode=%d", errorCode)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val graph = (application as BitterApplication).graph

        startForegroundNotification()

        val manager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        adapter = manager.adapter
        advertiser = adapter?.bluetoothLeAdvertiser
        scanner = adapter?.bluetoothLeScanner

        Timber.d("BleMeshService onCreate: adapter=%s advertiser=%s scanner=%s deviceId=%08x username=%s",
            adapter != null, advertiser != null, scanner != null, graph.deviceId, graph.username)

        gattServerHandler = GattServerHandler(
            this,
            { currentServer },
            graph.username,
            onPushEvents = { events -> scope.launch { graph.repository.applyRemote(events) } },
            onPeerIdentity = { deviceId, username ->
                scope.launch { graph.nicknames.recordUsername(deviceId, username) }
            },
        )
        gattServerHandler?.start()

        scope.launch {
            graph.repository.observeTimeline().collect { events ->
                currentServer = LocalSyncServer(events)
                Timber.d("timeline changed: %d events, root=%s", events.size, currentServer.truncatedRoot().toHex())
                restartAdvertising(graph)
            }
        }

        scope.launch {
            graph.ownNickname.collect { nickname ->
                Timber.d("own nickname changed: %s", nickname)
                restartAdvertising(graph)
            }
        }

        restartAdvertising(graph)
        startScanning()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        advertiser?.stopAdvertising(advertiseCallback)
        scanner?.stopScan(scanCallback)
        gattServerHandler?.close()
        super.onDestroy()
    }

    private fun hasPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun restartAdvertising(graph: com.bitter.AppGraph) {
        val adv = advertiser ?: return
        if (!hasPermissions()) {
            Timber.w("ADVERTISE skipped: no permission")
            return
        }
        adv.stopAdvertising(advertiseCallback)

        val packet = AdvertPacket(
            version = AdvertPacket.VERSION,
            roomId = BleProtocol.ROOM_ID,
            deviceId = graph.deviceId,
            merkleRoot = currentServer.truncatedRoot(),
        )
        val data = AdvertiseData.Builder()
            .addManufacturerData(BleProtocol.ADVERT_COMPANY_ID, AdvertPacket.encode(packet))
            .build()
        val nickname = graph.ownNickname.value
        val scanResponse = if (nickname.isNotEmpty()) {
            AdvertiseData.Builder()
                .addManufacturerData(
                    BleProtocol.NICKNAME_COMPANY_ID,
                    NicknamePacket.encode(NicknamePacket.VERSION, graph.deviceId, nickname),
                )
                .build()
        } else {
            null
        }
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            .setTimeout(0)
            .build()
        Timber.d("ADVERTISE starting: deviceId=%08x root=%s nickname=%s", packet.deviceId, packet.merkleRoot.toHex(), nickname)
        if (scanResponse != null) {
            adv.startAdvertising(settings, data, scanResponse, advertiseCallback)
        } else {
            adv.startAdvertising(settings, data, advertiseCallback)
        }
    }

    private fun startScanning() {
        val sc = scanner ?: return
        if (!hasPermissions()) {
            Timber.w("SCAN skipped: no permission")
            return
        }
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        Timber.d("SCAN starting")
        sc.startScan(null, settings, scanCallback)
    }

    private fun handleScanResult(result: ScanResult) {
        val record = result.scanRecord ?: return
        val payload = record.getManufacturerSpecificData(BleProtocol.ADVERT_COMPANY_ID) ?: return
        val packet = AdvertPacket.decode(payload)
        if (packet == null) {
            Timber.v("SCAN: non-bittr advert (len=%d) from %s", payload.size, result.device.address)
            return
        }
        if (packet.roomId != BleProtocol.ROOM_ID) return

        val device = result.device
        val graph = (application as BitterApplication).graph
        val myId = graph.deviceId
        val rootMatches = currentServer.truncatedRoot().contentEquals(packet.merkleRoot)
        val iAmClient = CollisionResolver.isClient(myId, packet.deviceId)

        val nicknamePayload = record.getManufacturerSpecificData(BleProtocol.NICKNAME_COMPANY_ID)
        val nickname = NicknamePacket.decode(nicknamePayload)?.nickname
            ?: graph.nicknames.nicknameFor(packet.deviceId)

        Timber.v("SCAN peer=%s rssi=%d peerId=%08x myId=%08x rootMatch=%s iAmClient=%s nickname=%s",
            device.address, result.rssi, packet.deviceId, myId, rootMatches, iAmClient, nickname)

        seenDevices.add(device.address)
        scope.launch {
            graph.meshStatus.upsertPeer(
                PeerInfo(
                    deviceId = packet.deviceId,
                    nickname = nickname,
                    address = device.address,
                    rssi = result.rssi,
                    rootMatches = rootMatches,
                ),
            )
        }
        nickname?.let { scope.launch { graph.nicknames.recordNickname(packet.deviceId, it) } }

        if (rootMatches) return
        if (!iAmClient) return
        if (device.address in syncingDevices) return

        Timber.d("SCAN: root mismatch, connecting as client to %s", device.address)
        graph.meshStatus.setActiveSync(ActiveSync(packet.deviceId, "initiating"))
        syncingDevices.add(device.address)
        scope.launch(Dispatchers.IO) {
            try {
                val client = GattClientSync(this@BleMeshService, device)
                if (client.connect() && client.awaitReady(10_000)) {
                    client.writeIdentity(myId, graph.username)
                    val peerUsername = client.readIdentity()
                    peerUsername?.let { graph.nicknames.recordUsername(packet.deviceId, it) }
                    graph.coordinator.sync(client.peer) { events ->
                        client.pushEvents(events)
                    }
                } else {
                    Timber.w("GATT connect/ready timed out for %s", device.address)
                }
                client.close()
            } finally {
                graph.meshStatus.setActiveSync(null)
                syncingDevices.remove(device.address)
            }
        }
    }

    private fun startForegroundNotification() {
        val channelId = "bittr-mesh"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                channelId,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
        val notification = Notification.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_stat_bitter)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setOngoing(true)
            .build()
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { byte -> "%02x".format(byte.toInt() and 0xFF) }

    private companion object {
        const val NOTIFICATION_ID = 1
    }
}

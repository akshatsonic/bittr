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
import android.util.Log
import com.bitter.BitterApplication
import com.bitter.R
import com.bitter.sync.LocalSyncServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class BleMeshService : Service() {

    private val tag = "BitterMesh"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

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
            Log.d(tag, "advertising started")
        }

        override fun onStartFailure(errorCode: Int) {
            Log.w(tag, "advertising failed: $errorCode")
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            handleScanResult(result)
        }

        override fun onScanFailed(errorCode: Int) {
            Log.w(tag, "scan failed: $errorCode")
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

        gattServerHandler = GattServerHandler(this, { currentServer }, graph.username)
        gattServerHandler?.start()

        scope.launch {
            graph.repository.observeTimeline().collect { events ->
                currentServer = LocalSyncServer(events)
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
        if (!hasPermissions()) return
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
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            .setTimeout(0)
            .build()
        adv.startAdvertising(settings, data, advertiseCallback)
    }

    private fun startScanning() {
        val sc = scanner ?: return
        if (!hasPermissions()) return
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        sc.startScan(null, settings, scanCallback)
    }

    private fun handleScanResult(result: ScanResult) {
        val record = result.scanRecord ?: return
        val payload = record.getManufacturerSpecificData(BleProtocol.ADVERT_COMPANY_ID) ?: return
        val packet = AdvertPacket.decode(payload) ?: return
        if (packet.roomId != BleProtocol.ROOM_ID) return

        val device = result.device
        seenDevices.add(device.address)

        val rootMatches = currentServer.truncatedRoot().contentEquals(packet.merkleRoot)
        if (rootMatches) return
        if (!CollisionResolver.isClient((application as BitterApplication).graph.deviceId, packet.deviceId)) return
        if (device.address in syncingDevices) return

        syncingDevices.add(device.address)
        scope.launch(Dispatchers.IO) {
            try {
                val client = GattClientSync(this@BleMeshService, device)
                if (client.connect() && client.awaitReady(10_000)) {
                    (application as BitterApplication).graph.coordinator.pullFrom(client.peer)
                }
                client.close()
            } finally {
                syncingDevices.remove(device.address)
            }
        }
    }

    private fun startForegroundNotification() {
        val channelId = "bitter-mesh"
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

    private companion object {
        const val NOTIFICATION_ID = 1
    }
}

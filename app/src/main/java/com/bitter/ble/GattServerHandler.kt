package com.bitter.ble

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.content.Context
import com.bitter.model.Event
import com.bitter.model.EventWireCodec
import com.bitter.sync.LocalSyncServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.bitter.log.Log
import java.util.UUID

class GattServerHandler(
    context: Context,
    private val serverProvider: () -> LocalSyncServer,
    private val username: String,
    private val onPushEvents: (List<Event>) -> Unit,
    private val onPeerIdentity: (Int, String) -> Unit = { _, _ -> },
) {
    private val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val gattServer: BluetoothGattServer by lazy { manager.openGattServer(context, callback) }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val writeStreams = mutableMapOf<String, FrameStream>()

    fun start() {
        val ok = gattServer.addService(buildService())
        Log.d("GATT server service added: $ok")
    }

    fun close() {
        gattServer.clearServices()
        gattServer.close()
    }

    private fun buildService(): BluetoothGattService {
        val service = BluetoothGattService(
            BleProtocol.SERVICE_UUID,
            BluetoothGattService.SERVICE_TYPE_PRIMARY,
        )

        val query = BluetoothGattCharacteristic(
            BleProtocol.CHAR_MERKLE_QUERY,
            BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_WRITE,
        )
        val fetch = BluetoothGattCharacteristic(
            BleProtocol.CHAR_EVENT_FETCH,
            BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_WRITE,
        )
        val identity = BluetoothGattCharacteristic(
            BleProtocol.CHAR_IDENTITY,
            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_WRITE,
            BluetoothGattCharacteristic.PERMISSION_READ or BluetoothGattCharacteristic.PERMISSION_WRITE,
        )

        val cccdUuid = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        query.addDescriptor(
            BluetoothGattDescriptor(
                cccdUuid,
                BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE,
            ),
        )
        fetch.addDescriptor(
            BluetoothGattDescriptor(
                cccdUuid,
                BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE,
            ),
        )

        service.addCharacteristic(query)
        service.addCharacteristic(fetch)
        service.addCharacteristic(identity)
        return service
    }

    private val callback = object : BluetoothGattServerCallback() {

        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            Log.d("GATT server connection state: device=%s status=%d newState=%d", device.address, status, newState)
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray,
        ) {
            Log.v("GATT write request: device=%s char=%s len=%d", device.address, characteristic.uuid, value.size)
            when (characteristic.uuid) {
                BleProtocol.CHAR_MERKLE_QUERY -> handleQuery(device, value)
                BleProtocol.CHAR_EVENT_FETCH -> handleEventFetchWrite(device, value)
                BleProtocol.CHAR_IDENTITY -> handleIdentityWrite(device, value)
            }
            if (responseNeeded) {
                gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
            }
        }

        override fun onCharacteristicReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic,
        ) {
            if (characteristic.uuid == BleProtocol.CHAR_IDENTITY) {
                gattServer.sendResponse(
                    device,
                    requestId,
                    BluetoothGatt.GATT_SUCCESS,
                    0,
                    username.toByteArray(Charsets.UTF_8),
                )
            } else {
                gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
            }
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray,
        ) {
            Log.d("GATT CCCD descriptor write: device=%s uuid=%s", device.address, descriptor.uuid)
            if (responseNeeded) {
                gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
            }
        }
    }

    private fun handleIdentityWrite(device: BluetoothDevice, value: ByteArray) {
        val announce = BleProtocol.decodeIdentityAnnounce(value)
        if (announce != null) {
            val (deviceId, peerUsername) = announce
            Log.d("GATT identity write received: deviceId=%08x username=%s", deviceId, peerUsername)
            onPeerIdentity(deviceId, peerUsername)
        } else {
            Log.w("GATT identity write decoded to null announce")
        }
    }

    private fun handleQuery(device: BluetoothDevice, payload: ByteArray) {
        val peer = serverProvider()
        val characteristic = gattServer.getService(BleProtocol.SERVICE_UUID)
            ?.getCharacteristic(BleProtocol.CHAR_MERKLE_QUERY) ?: return
        when (val query = BleProtocol.decodeQuery(payload)) {
            is BleProtocol.Query.NodeHash -> {
                val hash = peer.nodeHash(query.lo, query.hi)
                scope.launch { notify(device, characteristic, BleProtocol.encodeNodeHashAnswer(query.lo, query.hi, hash)) }
            }
            BleProtocol.Query.LeafCount -> {
                Log.d("GATT leafCount query answered: %d", peer.leafCount)
                scope.launch { notify(device, characteristic, BleProtocol.encodeLeafCountAnswer(peer.leafCount)) }
            }
            BleProtocol.Query.AllEvents -> {
                Log.d("GATT all-events query: streaming %d events", peer.allEvents().size)
                streamEvents(device, peer.allEvents())
            }
            null -> Log.w("GATT unknown merkle query: %s", payload.size)
        }
    }

    private fun handleEventFetchWrite(device: BluetoothDevice, value: ByteArray) {
        val stream = writeStreams.getOrPut(device.address) { FrameStream() }
        stream.feed(value).forEach { frame ->
            val op = if (frame.isNotEmpty()) frame[0].toInt() and 0xFF else -1
            when (op) {
                BleProtocol.OP_FETCH -> handleFetch(device, frame)
                BleProtocol.OP_PUSH -> {
                    val eventBytes = BleProtocol.decodePush(frame)
                    val event = eventBytes?.let { EventWireCodec.decode(it) }
                    if (event != null) {
                        Log.d("GATT push received event id=%s author=%s", event.id.take(8), event.author)
                        onPushEvents(listOf(event))
                    } else {
                        Log.w("GATT push decoded to null event")
                    }
                }
                else -> Log.w("GATT unknown EVENT_FETCH opcode: %d", op)
            }
        }
    }

    private fun handleFetch(device: BluetoothDevice, payload: ByteArray) {
        val peer = serverProvider()
        val leaves = BleProtocol.decodeFetchRequest(payload) ?: return
        Log.d("GATT fetch request: %d leaves", leaves.size)
        streamEvents(device, peer.eventsForLeaves(leaves))
    }

    private fun streamEvents(device: BluetoothDevice, events: List<Event>) {
        val characteristic = gattServer.getService(BleProtocol.SERVICE_UUID)
            ?.getCharacteristic(BleProtocol.CHAR_EVENT_FETCH) ?: return
        scope.launch {
            for (event in events) {
                val frame = FrameCodec.encode(BleProtocol.encodeEventStream(EventWireCodec.encode(event)))
                notify(device, characteristic, frame)
            }
            notify(device, characteristic, FrameCodec.encode(byteArrayOf()))
        }
    }

    private suspend fun notify(device: BluetoothDevice, characteristic: BluetoothGattCharacteristic, payload: ByteArray) {
        val chunkSize = 180
        var offset = 0
        while (offset < payload.size) {
            val end = minOf(offset + chunkSize, payload.size)
            characteristic.value = payload.copyOfRange(offset, end)
            var sent = gattServer.notifyCharacteristicChanged(device, characteristic, false)
            var attempts = 0
            while (!sent && attempts < 5) {
                delay(NOTIFY_RETRY_MS)
                sent = gattServer.notifyCharacteristicChanged(device, characteristic, false)
                attempts++
            }
            if (!sent) {
                Log.w("GATT notify failed after retries for device=%s", device.address)
                return
            }
            offset = end
            delay(NOTIFY_INTERVAL_MS)
        }
    }

    private companion object {
        const val NOTIFY_INTERVAL_MS = 20L
        const val NOTIFY_RETRY_MS = 50L
    }
}

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
import android.util.Log
import com.bitter.model.EventWireCodec
import com.bitter.sync.LocalSyncServer
import java.util.UUID

class GattServerHandler(
    context: Context,
    private val serverProvider: () -> LocalSyncServer,
    private val username: String,
) {
    private val tag = "BitterGattServer"
    private val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val gattServer: BluetoothGattServer by lazy { manager.openGattServer(context, callback) }

    fun start() {
        gattServer.addService(buildService())
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
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ,
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

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray,
        ) {
            when (characteristic.uuid) {
                BleProtocol.CHAR_MERKLE_QUERY -> handleQuery(device, value)
                BleProtocol.CHAR_EVENT_FETCH -> handleFetch(device, value)
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
    }

    private fun handleQuery(device: BluetoothDevice, payload: ByteArray) {
        val peer = serverProvider()
        val characteristic = gattServer.getService(BleProtocol.SERVICE_UUID)
            ?.getCharacteristic(BleProtocol.CHAR_MERKLE_QUERY) ?: return
        when (val query = BleProtocol.decodeQuery(payload)) {
            is BleProtocol.Query.NodeHash -> {
                val hash = peer.nodeHash(query.lo, query.hi)
                notify(device, characteristic, BleProtocol.encodeNodeHashAnswer(query.lo, query.hi, hash))
            }
            BleProtocol.Query.LeafCount -> {
                notify(device, characteristic, BleProtocol.encodeLeafCountAnswer(peer.leafCount))
            }
            null -> Unit
        }
    }

    private fun handleFetch(device: BluetoothDevice, payload: ByteArray) {
        val peer = serverProvider()
        val characteristic = gattServer.getService(BleProtocol.SERVICE_UUID)
            ?.getCharacteristic(BleProtocol.CHAR_EVENT_FETCH) ?: return
        val leaves = BleProtocol.decodeFetchRequest(payload) ?: return
        val events = peer.eventsForLeaves(leaves)
        for (event in events) {
            val frame = FrameCodec.encode(EventWireCodec.encode(event))
            notify(device, characteristic, frame)
        }
        notify(device, characteristic, FrameCodec.encode(byteArrayOf()))
    }

    private fun notify(device: BluetoothDevice, characteristic: BluetoothGattCharacteristic, payload: ByteArray) {
        val chunkSize = 200
        var offset = 0
        while (offset < payload.size) {
            val end = minOf(offset + chunkSize, payload.size)
            characteristic.value = payload.copyOfRange(offset, end)
            gattServer.notifyCharacteristicChanged(device, characteristic, false)
            offset = end
        }
    }
}

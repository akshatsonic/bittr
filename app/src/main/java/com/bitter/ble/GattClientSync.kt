package com.bitter.ble

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.util.Log
import com.bitter.model.Event
import com.bitter.model.EventWireCodec
import com.bitter.sync.SyncPeer
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class GattClientSync(
    private val context: Context,
    private val device: BluetoothDevice,
) {
    private val tag = "BitterGattClient"

    private val answerQueue = LinkedBlockingQueue<BleProtocol.Answer>()
    private val eventQueue = LinkedBlockingQueue<ByteArray>()
    private val eventStream = FrameStream()
    private val ready = CountDownLatch(1)

    @Volatile
    private var gatt: BluetoothGatt? = null

    private val cccdUuid = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                g.discoverServices()
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            g.requestMtu(512)
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            enableNotifications(g)
            ready.countDown()
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            when (characteristic.uuid) {
                BleProtocol.CHAR_MERKLE_QUERY -> BleProtocol.decodeAnswer(value)?.let {
                    answerQueue.offer(it)
                }
                BleProtocol.CHAR_EVENT_FETCH -> eventStream.feed(value).forEach { eventQueue.offer(it) }
            }
        }
    }

    fun connect(): Boolean {
        val g = device.connectGatt(context, false, callback) ?: return false
        gatt = g
        return true
    }

    fun awaitReady(timeoutMs: Long): Boolean = ready.await(timeoutMs, TimeUnit.MILLISECONDS)

    fun close() {
        gatt?.disconnect()
        gatt?.close()
        gatt = null
    }

    private fun enableNotifications(g: BluetoothGatt) {
        val service = g.getService(BleProtocol.SERVICE_UUID) ?: return
        writeCccd(g, service.getCharacteristic(BleProtocol.CHAR_MERKLE_QUERY))
        writeCccd(g, service.getCharacteristic(BleProtocol.CHAR_EVENT_FETCH))
    }

    private fun writeCccd(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic?) {
        characteristic ?: return
        val descriptor = characteristic.getDescriptor(cccdUuid) ?: return
        g.setCharacteristicNotification(characteristic, true)
        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        g.writeDescriptor(descriptor)
    }

    private fun write(characteristicUuid: UUID, payload: ByteArray) {
        val g = gatt ?: return
        val service = g.getService(BleProtocol.SERVICE_UUID) ?: return
        val characteristic = service.getCharacteristic(characteristicUuid) ?: return
        characteristic.value = payload
        g.writeCharacteristic(characteristic)
    }

    val peer: SyncPeer = object : SyncPeer {
        private var leafCountCache = -1
        private var leafCountLoaded = false

        override val leafCount: Int
            get() {
                if (!leafCountLoaded) {
                    write(BleProtocol.CHAR_MERKLE_QUERY, BleProtocol.encodeLeafCountQuery())
                    val answer = answerQueue.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                        as? BleProtocol.Answer.LeafCount ?: return 0
                    leafCountCache = answer.count
                    leafCountLoaded = true
                }
                return leafCountCache
            }

        override fun nodeHash(lo: Int, hi: Int): ByteArray {
            write(BleProtocol.CHAR_MERKLE_QUERY, BleProtocol.encodeNodeHashQuery(lo, hi))
            val answer = answerQueue.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                as? BleProtocol.Answer.NodeHash ?: return ByteArray(32)
            return answer.hash
        }

        override fun leafAt(index: Int): ByteArray = nodeHash(index, index + 1)

        override fun eventsForLeaves(leafHashes: List<ByteArray>): List<Event> {
            write(BleProtocol.CHAR_EVENT_FETCH, BleProtocol.encodeFetchRequest(leafHashes))
            val events = mutableListOf<Event>()
            while (true) {
                val frame = eventQueue.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS) ?: break
                if (frame.isEmpty()) break
                EventWireCodec.decode(frame)?.let { events.add(it) }
            }
            return events
        }
    }

    private companion object {
        const val TIMEOUT_SECONDS = 8L
    }
}

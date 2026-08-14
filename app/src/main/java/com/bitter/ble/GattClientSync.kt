package com.bitter.ble

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import com.bitter.model.Event
import com.bitter.model.EventWireCodec
import com.bitter.sync.SyncPeer
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class GattClientSync(
    private val context: Context,
    private val device: BluetoothDevice,
) {
    private val answerQueue = LinkedBlockingQueue<BleProtocol.Answer>()
    private val eventQueue = LinkedBlockingQueue<ByteArray>()
    private val eventStream = FrameStream()
    private val ready = CountDownLatch(1)

    @Volatile
    private var gatt: BluetoothGatt? = null

    @Volatile
    private var mtu = DEFAULT_MTU

    @Volatile
    private var mtuReady = false

    @Volatile
    private var cccdConfirmed = 0

    private val cccdUuid = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            Timber.d("GATT client state: device=%s status=%d newState=%d", g.device.address, status, newState)
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                g.discoverServices()
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            Timber.d("GATT services discovered: status=%d", status)
            if (!g.requestMtu(512)) {
                Timber.w("GATT requestMtu returned false, using default MTU")
                mtu = DEFAULT_MTU
                mtuReady = true
                enableNotifications(g)
                maybeReady()
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            Timber.d("GATT MTU changed: mtu=%d status=%d", mtu, status)
            this@GattClientSync.mtu = if (status == BluetoothGatt.GATT_SUCCESS) mtu else DEFAULT_MTU
            mtuReady = true
            enableNotifications(g)
            maybeReady()
        }

        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            Timber.d("GATT descriptor write confirmed: uuid=%s status=%d", descriptor.uuid, status)
            cccdConfirmed++
            maybeReady()
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

    private fun maybeReady() {
        if (mtuReady && cccdConfirmed >= 2) {
            ready.countDown()
        }
    }

    fun connect(): Boolean {
        val g = device.connectGatt(context, false, callback) ?: return false
        gatt = g
        Timber.d("GATT connect initiated to %s", device.address)
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
        val descriptor = characteristic.getDescriptor(cccdUuid)
        if (descriptor == null) {
            Timber.w("GATT no CCCD descriptor for %s, counting as confirmed", characteristic.uuid)
            cccdConfirmed++
            return
        }
        g.setCharacteristicNotification(characteristic, true)
        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        g.writeDescriptor(descriptor)
    }

    private fun chunkSize(): Int = maxOf(20, mtu - 3)

    private fun write(characteristicUuid: UUID, payload: ByteArray) {
        val g = gatt ?: return
        val service = g.getService(BleProtocol.SERVICE_UUID) ?: return
        val characteristic = service.getCharacteristic(characteristicUuid) ?: return
        writeChunked(g, characteristic, payload)
    }

    private fun writeChunked(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, payload: ByteArray) {
        val chunk = chunkSize()
        var offset = 0
        while (offset < payload.size) {
            val end = minOf(offset + chunk, payload.size)
            characteristic.value = payload.copyOfRange(offset, end)
            val ok = g.writeCharacteristic(characteristic)
            Timber.v("GATT write: char=%s ok=%s chunk=%d..%d", characteristic.uuid, ok, offset, end)
            if (!ok) break
            offset = end
        }
    }

    private fun writeFramed(characteristicUuid: UUID, payload: ByteArray) {
        write(characteristicUuid, FrameCodec.encode(payload))
    }

    fun pushEvents(events: List<Event>) {
        Timber.d("GATT pushing %d events", events.size)
        for (event in events) {
            writeFramed(BleProtocol.CHAR_EVENT_FETCH, BleProtocol.encodePush(EventWireCodec.encode(event)))
        }
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
            writeFramed(BleProtocol.CHAR_EVENT_FETCH, BleProtocol.encodeFetchRequest(leafHashes))
            val events = mutableListOf<Event>()
            while (true) {
                val frame = eventQueue.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS) ?: break
                if (frame.isEmpty()) break
                val eventBytes = BleProtocol.decodeEventStream(frame) ?: continue
                EventWireCodec.decode(eventBytes)?.let { events.add(it) }
            }
            Timber.d("GATT pulled %d events", events.size)
            return events
        }

        override fun allEvents(): List<Event> {
            write(BleProtocol.CHAR_MERKLE_QUERY, BleProtocol.encodeAllEventsQuery())
            val events = mutableListOf<Event>()
            while (true) {
                val frame = eventQueue.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS) ?: break
                if (frame.isEmpty()) break
                val eventBytes = BleProtocol.decodeEventStream(frame) ?: continue
                EventWireCodec.decode(eventBytes)?.let { events.add(it) }
            }
            Timber.d("GATT pulled %d events (all)", events.size)
            return events
        }
    }

    private companion object {
        const val TIMEOUT_SECONDS = 8L
        const val DEFAULT_MTU = 23
    }
}

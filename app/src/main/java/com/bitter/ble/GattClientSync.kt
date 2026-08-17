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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.bitter.log.Log
import java.util.ArrayDeque
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class GattClientSync(
    private val context: Context,
    private val device: BluetoothDevice,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val answerQueue = LinkedBlockingQueue<BleProtocol.Answer>()
    private val eventQueue = LinkedBlockingQueue<ByteArray>()
    private val eventStream = FrameStream()
    private val ready = CountDownLatch(1)
    private val identityLatch = CountDownLatch(1)

    @Volatile
    private var peerUsername: String? = null

    @Volatile
    private var gatt: BluetoothGatt? = null

    @Volatile
    private var mtu = DEFAULT_MTU

    @Volatile
    private var mtuReady = false

    @Volatile
    private var mtuHandled = false

    @Volatile
    private var notificationsStarted = false

    @Volatile
    private var notificationsDone = false

    private val cccdQueue = ArrayDeque<BluetoothGattCharacteristic>()

    @Volatile
    private var cccdCompleted = 0

    private val notificationLock = Any()

    @Volatile
    private var writeLatch = CountDownLatch(0)

    private val cccdUuid = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            Log.d("GATT client state: device=%s status=%d newState=%d", g.device.address, status, newState)
            when {
                newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS -> {
                    g.discoverServices()
                }
                newState == BluetoothProfile.STATE_CONNECTED -> {
                    Log.w("GATT connected with error status=%d, failing fast", status)
                    ready.countDown()
                }
                newState == BluetoothProfile.STATE_DISCONNECTED -> {
                    ready.countDown()
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            Log.d("GATT services discovered: status=%d", status)
            if (status != BluetoothGatt.GATT_SUCCESS) {
                ready.countDown()
                return
            }
            synchronized(notificationLock) {
                if (mtuHandled) return
                mtuHandled = true
                if (!g.requestMtu(512)) {
                    Log.w("GATT requestMtu returned false, using default MTU")
                    mtu = DEFAULT_MTU
                    mtuReady = true
                    enableNotifications(g)
                    maybeReady()
                } else {
                    scope.launch {
                        delay(MTU_TIMEOUT_MS)
                        if (!mtuReady) {
                            Log.w("GATT MTU exchange timed out, falling back to default MTU")
                            mtu = DEFAULT_MTU
                            mtuReady = true
                            gatt?.let { enableNotifications(it) }
                            maybeReady()
                        }
                    }
                }
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            Log.d("GATT MTU changed: mtu=%d status=%d", mtu, status)
            synchronized(notificationLock) {
                mtuHandled = true
                this@GattClientSync.mtu = if (status == BluetoothGatt.GATT_SUCCESS) mtu else DEFAULT_MTU
                mtuReady = true
                enableNotifications(g)
                maybeReady()
            }
        }

        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            Log.d("GATT descriptor write confirmed: uuid=%s status=%d", descriptor.uuid, status)
            synchronized(notificationLock) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    cccdCompleted++
                } else {
                    Log.w("GATT descriptor write failed status=%d, continuing", status)
                }
                writeNextCccdLocked(g)
                maybeReady()
            }
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

        override fun onCharacteristicRead(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            if (characteristic.uuid == BleProtocol.CHAR_IDENTITY && status == BluetoothGatt.GATT_SUCCESS) {
                peerUsername = characteristic.value?.toString(Charsets.UTF_8)
            }
            identityLatch.countDown()
        }

        override fun onCharacteristicWrite(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            Log.v("GATT write confirmed: char=%s status=%d", characteristic.uuid, status)
            writeLatch.countDown()
        }
    }

    private fun maybeReady() {
        if (mtuReady && notificationsDone) {
            ready.countDown()
        }
    }

    fun connect(): Boolean {
        val g = device.connectGatt(context, false, callback) ?: return false
        gatt = g
        Log.d("GATT connect initiated to %s", device.address)
        scope.launch {
            delay(CONNECT_WATCHDOG_MS)
            if (ready.count > 0) {
                Log.w("GATT connect/discover watchdog fired for %s", device.address)
                ready.countDown()
            }
        }
        return true
    }

    fun awaitReady(timeoutMs: Long): Boolean = ready.await(timeoutMs, TimeUnit.MILLISECONDS) && mtuReady

    fun readIdentity(timeoutMs: Long = TIMEOUT_SECONDS * 1000): String? {
        val g = gatt ?: return null
        val service = g.getService(BleProtocol.SERVICE_UUID) ?: return null
        val characteristic = service.getCharacteristic(BleProtocol.CHAR_IDENTITY) ?: return null
        val ok = g.readCharacteristic(characteristic)
        if (!ok) return null
        identityLatch.await(timeoutMs, TimeUnit.MILLISECONDS)
        return peerUsername
    }

    fun writeIdentity(deviceId: Int, username: String) {
        write(
            BleProtocol.CHAR_IDENTITY,
            BleProtocol.encodeIdentityAnnounce(deviceId, username),
        )
    }

    fun close() {
        gatt?.disconnect()
        gatt?.close()
        gatt = null
    }

    private fun enableNotifications(g: BluetoothGatt) {
        synchronized(notificationLock) {
            if (notificationsStarted) return
            notificationsStarted = true
            val service = g.getService(BleProtocol.SERVICE_UUID) ?: run {
                notificationsDone = true
                maybeReady()
                return
            }
            listOf(BleProtocol.CHAR_MERKLE_QUERY, BleProtocol.CHAR_EVENT_FETCH).forEach { uuid ->
                service.getCharacteristic(uuid)?.let { cccdQueue.add(it) }
            }
            if (cccdQueue.isEmpty()) {
                notificationsDone = true
                maybeReady()
                return
            }
            writeNextCccdLocked(g)
            // Watchdog: never let CCCD writes stall the handshake.
            scope.launch {
                delay(NOTIFICATIONS_TIMEOUT_MS)
                synchronized(notificationLock) {
                    if (!notificationsDone) {
                        Log.w("GATT notification enable timed out, proceeding anyway")
                        notificationsDone = true
                        maybeReady()
                    }
                }
            }
        }
    }

    private fun writeNextCccdLocked(g: BluetoothGatt) {
        val characteristic = cccdQueue.pollFirst() ?: run {
            if (notificationsStarted) {
                notificationsDone = true
                maybeReady()
            }
            return
        }
        val descriptor = characteristic.getDescriptor(cccdUuid)
        if (descriptor == null) {
            Log.w("GATT no CCCD descriptor for %s, counting as completed", characteristic.uuid)
            cccdCompleted++
            writeNextCccdLocked(g)
            return
        }
        if (!g.setCharacteristicNotification(characteristic, true)) {
            Log.w("GATT setCharacteristicNotification returned false for %s", characteristic.uuid)
            cccdCompleted++
            writeNextCccdLocked(g)
            return
        }
        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        if (!g.writeDescriptor(descriptor)) {
            Log.w("GATT writeDescriptor returned false for %s, counting as completed", characteristic.uuid)
            cccdCompleted++
            writeNextCccdLocked(g)
        }
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
            writeLatch = CountDownLatch(1)
            val ok = g.writeCharacteristic(characteristic)
            Log.v("GATT write: char=%s ok=%s chunk=%d..%d", characteristic.uuid, ok, offset, end)
            if (!ok) break
            writeLatch.await(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            offset = end
        }
    }

    private fun writeFramed(characteristicUuid: UUID, payload: ByteArray) {
        write(characteristicUuid, FrameCodec.encode(payload))
    }

    fun pushEvents(events: List<Event>) {
        Log.d("GATT pushing %d events", events.size)
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
            Log.d("GATT pulled %d events", events.size)
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
            Log.d("GATT pulled %d events (all)", events.size)
            return events
        }
    }

    private companion object {
        const val TIMEOUT_SECONDS = 8L
        const val WRITE_TIMEOUT_SECONDS = 8L
        const val DEFAULT_MTU = 23
        const val MTU_TIMEOUT_MS = 2_000L
        const val NOTIFICATIONS_TIMEOUT_MS = 3_000L
        const val CONNECT_WATCHDOG_MS = 6_000L
    }
}

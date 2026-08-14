package com.bitter.ble

object CollisionResolver {
    fun isClient(myDeviceId: Int, peerDeviceId: Int): Boolean {
        val mine = myDeviceId.toLong() and 0xFFFFFFFFL
        val peer = peerDeviceId.toLong() and 0xFFFFFFFFL
        return mine < peer
    }
}

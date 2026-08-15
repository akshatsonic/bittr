package com.bitter.ble

object CollisionResolver {
    fun isClient(
        myDeviceId: Int,
        peerDeviceId: Int,
        myNickname: String = "",
        peerNickname: String = "",
    ): Boolean {
        val mine = myDeviceId.toLong() and 0xFFFFFFFFL
        val peer = peerDeviceId.toLong() and 0xFFFFFFFFL
        if (mine != peer) return mine < peer
        return myNickname < peerNickname
    }
}

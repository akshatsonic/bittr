package com.bitter.ble

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CollisionResolverTest {

    @Test
    fun `lower device id acts as client`() {
        assertTrue(CollisionResolver.isClient(myDeviceId = 0x00000001, peerDeviceId = 0x00000002))
        assertFalse(CollisionResolver.isClient(myDeviceId = 0x00000002, peerDeviceId = 0x00000001))
    }

    @Test
    fun `equal ids are not a client (both back off)`() {
        assertFalse(CollisionResolver.isClient(myDeviceId = 7, peerDeviceId = 7))
    }

    @Test
    fun `comparison is unsigned across the 4 byte range`() {
        assertTrue(CollisionResolver.isClient(myDeviceId = 0x7FFFFFFF, peerDeviceId = 0xFFFFFFFF.toInt()))
        assertFalse(CollisionResolver.isClient(myDeviceId = 0xFFFFFFFF.toInt(), peerDeviceId = 0x00000000))
        assertTrue(CollisionResolver.isClient(myDeviceId = 0x00000000, peerDeviceId = 0xFFFFFFFF.toInt()))
    }
}

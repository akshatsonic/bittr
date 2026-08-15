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
    fun `equal ids fall back to nickname tie break`() {
        assertTrue(CollisionResolver.isClient(7, 7, myNickname = "alice", peerNickname = "bob"))
        assertFalse(CollisionResolver.isClient(7, 7, myNickname = "bob", peerNickname = "alice"))
    }

    @Test
    fun `equal ids and equal nicknames are not a client`() {
        assertFalse(CollisionResolver.isClient(7, 7, myNickname = "alice", peerNickname = "alice"))
        assertFalse(CollisionResolver.isClient(7, 7))
    }

    @Test
    fun `device id dominates the nickname tie break`() {
        assertTrue(CollisionResolver.isClient(7, 8, myNickname = "z", peerNickname = "a"))
        assertFalse(CollisionResolver.isClient(8, 7, myNickname = "a", peerNickname = "z"))
    }

    @Test
    fun `comparison is unsigned across the 4 byte range`() {
        assertTrue(CollisionResolver.isClient(myDeviceId = 0x7FFFFFFF, peerDeviceId = 0xFFFFFFFF.toInt()))
        assertFalse(CollisionResolver.isClient(myDeviceId = 0xFFFFFFFF.toInt(), peerDeviceId = 0x00000000))
        assertTrue(CollisionResolver.isClient(myDeviceId = 0x00000000, peerDeviceId = 0xFFFFFFFF.toInt()))
    }
}

package com.bitter.mesh

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

class NicknameRegistryTest {

    private fun registry(maxSize: Int = 300) =
        NicknameRegistry(maxSize = maxSize, clock = { tick })

    private var tick = 0L

    @Test
    fun `nickname is surfaced as display name`() = runTest {
        val r = registry()
        r.recordNickname(0x01020304, "cool-cat")
        r.recordUsername(0x01020304, "bob")
        assertEquals("cool-cat", r.displayNames.value.getOrDefault("bob", ""))
    }

    @Test
    fun `username links device id to author for rename`() = runTest {
        val r = registry()
        r.recordNickname(0x01020304, "old-name")
        r.recordUsername(0x01020304, "bob")
        assertEquals("old-name", r.displayNames.value["bob"])
        assertEquals("bob", r.usernameFor(0x01020304))
        assertEquals("old-name", r.nicknameFor(0x01020304))

        tick++
        r.recordNickname(0x01020304, "new-name")
        assertEquals("new-name", r.displayNames.value["bob"])
        assertEquals("new-name", r.nicknameFor(0x01020304))
    }

    @Test
    fun `display falls back to username when no nickname`() = runTest {
        val r = registry()
        r.recordUsername(0x01020304, "bob")
        assertEquals("bob", r.displayNames.value["bob"])
    }

    @Test
    fun `unknown device has no entry`() = runTest {
        val r = registry()
        assertNull(r.nicknameFor(0x01020304))
    }

    @Test
    fun `records are keyed by device id`() = runTest {
        val r = registry()
        r.recordNickname(0x01020304, "alice")
        r.recordNickname(0x05060708, "bob")
        assertEquals("alice", r.nicknameFor(0x01020304))
        assertEquals("bob", r.nicknameFor(0x05060708))
    }

    @Test
    fun `evicts oldest seen when at capacity`() = runTest {
        val r = registry(maxSize = 3)
        r.recordNickname(0x01000001, "one")
        tick++
        r.recordNickname(0x01000002, "two")
        tick++
        r.recordNickname(0x01000003, "three")
        assertEquals(3, r.size())

        tick++
        r.recordNickname(0x01000004, "four")
        assertEquals(3, r.size())
        assertNull(r.nicknameFor(0x01000001))
        assertEquals("two", r.nicknameFor(0x01000002))
        assertEquals("four", r.nicknameFor(0x01000004))
    }

    @Test
    fun `re-seeing a device refreshes its recency`() = runTest {
        val r = registry(maxSize = 3)
        r.recordNickname(0x01000001, "one")
        tick++
        r.recordNickname(0x01000002, "two")
        tick++
        r.recordNickname(0x01000003, "three")

        tick += 10
        r.recordNickname(0x01000001, "one-again")

        tick++
        r.recordNickname(0x01000004, "four")

        assertEquals(3, r.size())
        assertNull(r.nicknameFor(0x01000002))
        assertEquals("one-again", r.nicknameFor(0x01000001))
    }

    @Test
    fun `capacity defaults to 300`() {
        assertEquals(300, NicknameRegistry().maxSize())
    }
}

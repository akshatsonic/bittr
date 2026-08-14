package com.bitter.crypto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class IdentityTest {

    @Test
    fun `derives a deterministic username from a fingerprint`() {
        val fp = "android-id-1234abcd"
        assertEquals(IdentityDerivation.deriveUsername(fp), IdentityDerivation.deriveUsername(fp))
    }

    @Test
    fun `different fingerprints produce different usernames`() {
        val a = IdentityDerivation.deriveUsername("fp-a")
        val b = IdentityDerivation.deriveUsername("fp-b")
        assertNotEquals(a, b)
    }

    @Test
    fun `username follows the bittr prefix format`() {
        val name = IdentityDerivation.deriveUsername("some-fingerprint")
        assertTrue(name.startsWith("bittr-"), "expected 'bittr-' prefix, got: $name")
        assertTrue(name.length > "bittr-".length, "expected a non-empty handle suffix")
    }

    @Test
    fun `empty fingerprint still yields a valid username`() {
        val name = IdentityDerivation.deriveUsername("")
        assertTrue(name.startsWith("bittr-"))
    }
}

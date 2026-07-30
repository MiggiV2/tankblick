package de.mymiggi.tankblick.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiKeyTest {

    @Test
    fun `accepts a well formed uuid key`() {
        val parsed = ApiKey.parse("d4f1a2b3-1111-4222-8333-abcdefabcdef")

        assertEquals("d4f1a2b3-1111-4222-8333-abcdefabcdef", parsed?.value)
    }

    @Test
    fun `trims surrounding whitespace because keys arrive via copy paste`() {
        val parsed = ApiKey.parse("  d4f1a2b3-1111-4222-8333-abcdefabcdef\n")

        assertEquals("d4f1a2b3-1111-4222-8333-abcdefabcdef", parsed?.value)
    }

    @Test
    fun `normalises to lower case so the same key is never stored twice`() {
        val parsed = ApiKey.parse("D4F1A2B3-1111-4222-8333-ABCDEFABCDEF")

        assertEquals("d4f1a2b3-1111-4222-8333-abcdefabcdef", parsed?.value)
    }

    @Test
    fun `rejects blank input`() {
        assertNull(ApiKey.parse(""))
        assertNull(ApiKey.parse("   "))
    }

    @Test
    fun `rejects input that is not uuid shaped`() {
        assertNull(ApiKey.parse("not-a-key"))
        assertNull(ApiKey.parse("d4f1a2b3111142228333abcdefabcdef"))
        assertNull(ApiKey.parse("d4f1a2b3-1111-4222-8333-abcdefabcde"))
        assertNull(ApiKey.parse("d4f1a2b3-1111-4222-8333-abcdefabcdeff"))
        assertNull(ApiKey.parse("z4f1a2b3-1111-4222-8333-abcdefabcdef"))
    }

    @Test
    fun `recognises the tankerkoenig demo key`() {
        val demo = ApiKey.parse(ApiKey.DEMO_KEY)

        assertTrue(demo!!.isDemo)
    }

    @Test
    fun `a personal key is not the demo key`() {
        val personal = ApiKey.parse("d4f1a2b3-1111-4222-8333-abcdefabcdef")

        assertFalse(personal!!.isDemo)
    }

    @Test
    fun `masks itself so it never leaks into logs or screenshots`() {
        val key = ApiKey.parse("d4f1a2b3-1111-4222-8333-abcdefabcdef")!!

        assertEquals("d4f1a2b3-…-abcdefabcdef", key.masked())
        assertFalse(key.toString().contains("1111-4222-8333"))
    }
}

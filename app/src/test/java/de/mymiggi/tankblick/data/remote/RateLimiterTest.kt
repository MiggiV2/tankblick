package de.mymiggi.tankblick.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RateLimiterTest {

    private var now = 1_000_000L
    private val limiter = RateLimiter(minIntervalMillis = 60_000L, clock = { now })

    @Test
    fun `allows the first request`() {
        assertNull(limiter.tryAcquire())
    }

    @Test
    fun `blocks a second request within the interval`() {
        limiter.tryAcquire()

        assertEquals(60L, limiter.tryAcquire())
    }

    @Test
    fun `reports the remaining seconds, rounded up`() {
        limiter.tryAcquire()

        now += 100L
        assertEquals(60L, limiter.tryAcquire())

        now += 29_900L
        assertEquals(30L, limiter.tryAcquire())

        now += 29_500L
        assertEquals(1L, limiter.tryAcquire())
    }

    @Test
    fun `allows the next request once the interval has passed`() {
        limiter.tryAcquire()
        now += 60_000L

        assertNull(limiter.tryAcquire())
    }

    /** A blocked attempt must not restart the clock, or the caller could starve itself. */
    @Test
    fun `a rejected attempt does not extend the wait`() {
        limiter.tryAcquire()

        now += 30_000L
        limiter.tryAcquire()

        now += 30_000L
        assertNull(limiter.tryAcquire())
    }

    @Test
    fun `each acquired request starts a new interval`() {
        limiter.tryAcquire()
        now += 60_000L
        limiter.tryAcquire()

        assertEquals(60L, limiter.tryAcquire())
    }

    /** Detail lookups use a much shorter interval than the refresh path. */
    @Test
    fun `honours a custom interval`() {
        val debounce = RateLimiter(minIntervalMillis = 2_000L, clock = { now })

        debounce.tryAcquire()
        assertEquals(2L, debounce.tryAcquire())

        now += 2_000L
        assertNull(debounce.tryAcquire())
    }

    /**
     * Wall clock time can jump backwards (NTP, manual change). That must not
     * lock the user out for hours.
     */
    @Test
    fun `recovers when the clock jumps backwards`() {
        limiter.tryAcquire()

        now -= 3_600_000L

        assertNull(limiter.tryAcquire())
    }
}

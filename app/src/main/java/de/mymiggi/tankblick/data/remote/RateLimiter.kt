package de.mymiggi.tankblick.data.remote

import kotlin.math.ceil

/**
 * Enforces a minimum gap between requests.
 *
 * Tankerkönig's free API asks for at most one request per minute. Rather than
 * letting the server reject us, the app throttles itself and tells the user how
 * long the wait is - a countdown is far more understandable than an error.
 *
 * Two instances exist: a one-minute limiter for the refresh path (nearby search
 * and favourite prices), and a short debounce for detail lookups, which are
 * one-off reactions to a tap rather than polling.
 *
 * [clock] is injectable so the behaviour can be tested without waiting.
 *
 * [store] holds the timestamp of the last granted request. Every attempt reads
 * it back rather than caching it in a field, so the limit survives process
 * death when the store does.
 */
class RateLimiter(
    private val minIntervalMillis: Long,
    private val clock: () -> Long = System::currentTimeMillis,
    private val store: RateLimiterStore = RateLimiterStore.inMemory(),
) {

    /**
     * Claims a request slot.
     *
     * @return `null` when the caller may proceed, otherwise the whole seconds
     *   still to wait. A rejected attempt does not extend the wait.
     */
    @Synchronized
    fun tryAcquire(): Long? {
        val now = clock()
        val last = store.lastRequestAtMillis()
        val elapsed = if (last == null) Long.MAX_VALUE else now - last

        // A negative elapsed time means the wall clock jumped backwards, for
        // example after an NTP correction. Treating that as "wait" could lock
        // the user out for hours, so the interval is considered over.
        if (elapsed in 0 until minIntervalMillis) {
            return ceil((minIntervalMillis - elapsed) / 1000.0).toLong()
        }

        store.recordRequestAt(now)
        return null
    }

    companion object {
        /** What Tankerkönig asks for on the refresh path. */
        const val REFRESH_INTERVAL_MILLIS = 60_000L

        /** Detail lookups follow a tap, so they only need debouncing. */
        const val DETAIL_INTERVAL_MILLIS = 2_000L
    }
}

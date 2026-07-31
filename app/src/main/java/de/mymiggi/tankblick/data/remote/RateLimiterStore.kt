package de.mymiggi.tankblick.data.remote

/**
 * Where a [RateLimiter] keeps the moment of its last granted request.
 *
 * Pulled out of the limiter so the timestamp can outlive the process: an
 * in-memory limiter starts every launch with a free request, which turns
 * force-stopping the app into a way around Tankerkönig's one-per-minute
 * request.
 *
 * Reads are synchronous because [RateLimiter.tryAcquire] is, which rules out
 * DataStore for the Android implementation.
 */
interface RateLimiterStore {

    /** The last granted request, or `null` if there has not been one. */
    fun lastRequestAtMillis(): Long?

    fun recordRequestAt(millis: Long)

    companion object {
        /** For tests and for limiters whose state is not worth persisting. */
        fun inMemory(): RateLimiterStore = InMemoryRateLimiterStore()
    }
}

private class InMemoryRateLimiterStore : RateLimiterStore {

    @Volatile
    private var lastRequestAtMillis: Long? = null

    override fun lastRequestAtMillis(): Long? = lastRequestAtMillis

    override fun recordRequestAt(millis: Long) {
        lastRequestAtMillis = millis
    }
}

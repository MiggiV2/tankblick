package de.mymiggi.tankblick.location

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation

/** Lets tests drive every branch of the permission and provider handling. */
class FakeLocationSource(
    var result: LocationResult = LocationResult.Available(52.5, 13.4),
) : LocationSource {

    /** When true, the call never returns - what a silent provider looks like. */
    var hangForever: Boolean = false

    private var gate: CompletableDeferred<Unit>? = null

    /** Holds the next call open, so a test can act while a refresh is in flight. */
    fun block() {
        gate = CompletableDeferred()
    }

    fun release() {
        gate?.complete(Unit)
        gate = null
    }

    override suspend fun currentLocation(): LocationResult {
        if (hangForever) awaitCancellation()
        gate?.await()
        return result
    }
}

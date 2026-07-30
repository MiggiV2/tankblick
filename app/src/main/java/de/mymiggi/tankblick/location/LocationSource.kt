package de.mymiggi.tankblick.location

/** Where the user is, or why the app cannot tell. */
sealed interface LocationResult {

    data class Available(val latitude: Double, val longitude: Double) : LocationResult

    /** The user has not granted a location permission. */
    data object PermissionMissing : LocationResult

    /** Location services are switched off system-wide. */
    data object LocationDisabled : LocationResult

    /** Permission and services are fine, but no fix could be obtained. */
    data object Unavailable : LocationResult
}

/**
 * A single, on-demand position fix.
 *
 * An interface so the nearby screen can be tested without a device, and so the
 * implementation choice stays swappable - which matters here, because
 * FusedLocationProvider is off limits: it lives in Google Play Services, and
 * F-Droid does not build against proprietary libraries.
 *
 * Single fix, never a subscription. Continuous updates would drain the battery
 * for a screen the user looks at for ten seconds.
 */
interface LocationSource {

    suspend fun currentLocation(): LocationResult
}

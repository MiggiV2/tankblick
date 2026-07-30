package de.mymiggi.tankblick.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Position fixes from the platform's own [LocationManager].
 *
 * Not FusedLocationProvider: that ships in Google Play Services, which F-Droid
 * will not build against. The platform API costs a little accuracy and needs
 * more care around providers, and it keeps the app free of proprietary code.
 *
 * Providers are tried cheapest first. The network provider answers in about a
 * second and is accurate to a few hundred metres, which is plenty for a search
 * with a kilometre-scale radius; GPS is only asked when nothing else answers.
 */
class LocationManagerSource(
    context: Context,
) : LocationSource {

    private val appContext = context.applicationContext
    private val locationManager =
        appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    override suspend fun currentLocation(): LocationResult {
        if (!hasPermission()) return LocationResult.PermissionMissing
        if (!LocationManagerCompat.isLocationEnabled(locationManager)) {
            return LocationResult.LocationDisabled
        }

        for (provider in providers()) {
            // Per provider, so a silent GPS does not starve the network
            // provider queued behind it.
            val location = withTimeoutOrNull(PROVIDER_TIMEOUT_MILLIS) {
                requestSingleFix(provider)
            } ?: continue
            return LocationResult.Available(location.latitude, location.longitude)
        }

        // Nothing fresh; a recent last-known fix still beats asking the user to
        // stand still under an open sky.
        val lastKnown = providers().firstNotNullOfOrNull { lastKnownLocation(it) }
        return lastKnown
            ?.let { LocationResult.Available(it.latitude, it.longitude) }
            ?: LocationResult.Unavailable
    }

    private fun providers(): List<String> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            locationManager.isProviderEnabled(LocationManager.FUSED_PROVIDER)
        ) {
            add(LocationManager.FUSED_PROVIDER)
        }
        if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            add(LocationManager.NETWORK_PROVIDER)
        }
        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            add(LocationManager.GPS_PROVIDER)
        }
    }

    private suspend fun requestSingleFix(provider: String): Location? =
        suspendCancellableCoroutine { continuation ->
            val cancellationSignal = android.os.CancellationSignal()
            continuation.invokeOnCancellation { cancellationSignal.cancel() }

            try {
                LocationManagerCompat.getCurrentLocation(
                    locationManager,
                    provider,
                    cancellationSignal,
                    // Not Context#getMainExecutor: that needs API 28 and minSdk is 24.
                    ContextCompat.getMainExecutor(appContext),
                ) { location ->
                    if (continuation.isActive) continuation.resume(location)
                }
            } catch (e: SecurityException) {
                // Permission revoked between the check and the call.
                if (continuation.isActive) continuation.resume(null)
            }
        }

    private fun lastKnownLocation(provider: String): Location? = try {
        locationManager.getLastKnownLocation(provider)
    } catch (e: SecurityException) {
        null
    }

    private fun hasPermission(): Boolean =
        LOCATION_PERMISSIONS.any {
            ContextCompat.checkSelfPermission(appContext, it) == PackageManager.PERMISSION_GRANTED
        }

    companion object {
        /** A provider that has not answered by now is not going to. */
        private const val PROVIDER_TIMEOUT_MILLIS = 8_000L

        /**
         * Coarse is enough for a search measured in kilometres, so the app asks
         * for both and is happy with either.
         */
        val LOCATION_PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
        )
    }
}

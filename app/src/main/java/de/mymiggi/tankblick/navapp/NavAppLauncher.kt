package de.mymiggi.tankblick.navapp

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.net.toUri

/** A navigation app the user could pick, as found on this device. */
data class NavApp(
    val packageName: String,
    val label: String,
)

/**
 * Finds and starts navigation apps.
 *
 * Requires the `<queries>` block in the manifest: since API 30 an app cannot
 * see which other apps can handle `geo:` without declaring the intent it wants
 * to resolve.
 */
class NavAppLauncher(
    context: Context,
) {

    private val appContext = context.applicationContext

    /**
     * Every installed app that handles a `geo:` intent, minus this one.
     *
     * Used to fill the "navigation app" setting. Apps come and go, so this is
     * queried fresh rather than cached.
     */
    fun installedNavApps(): List<NavApp> {
        val probe = Intent(Intent.ACTION_VIEW, PROBE_URI.toUri())
        val packageManager = appContext.packageManager

        return packageManager.queryIntentActivities(probe, 0)
            .asSequence()
            .map { it.activityInfo.packageName }
            .filter { it != appContext.packageName }
            .distinct()
            .mapNotNull { packageName ->
                val label = packageManager.appLabel(packageName) ?: return@mapNotNull null
                NavApp(packageName, label)
            }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    /**
     * Opens the station in a navigation app.
     *
     * With no preferred app set, the system chooser asks. A preferred app that
     * has since been uninstalled falls back to the chooser too, rather than
     * failing at a tap the user cannot connect to a setting they made months
     * ago.
     *
     * @return false if nothing on the device can handle a `geo:` intent.
     */
    fun launch(
        latitude: Double,
        longitude: Double,
        name: String,
        preferredPackage: String?,
    ): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, GeoUri.forStation(latitude, longitude, name).toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        if (preferredPackage != null && appContext.canHandle(intent, preferredPackage)) {
            return appContext.tryStart(intent.setPackage(preferredPackage))
        }

        return appContext.tryStart(
            Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private fun Context.canHandle(intent: Intent, packageName: String): Boolean =
        packageManager.queryIntentActivities(Intent(intent).setPackage(packageName), 0).isNotEmpty()

    private fun Context.tryStart(intent: Intent): Boolean = try {
        startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    }

    private fun PackageManager.appLabel(packageName: String): String? = try {
        getApplicationLabel(getApplicationInfo(packageName, 0)).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    private companion object {
        /** Somewhere in Germany; only the scheme matters for resolution. */
        const val PROBE_URI = "geo:52.5,13.4?q=52.5,13.4"
    }
}

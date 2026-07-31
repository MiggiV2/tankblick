package de.mymiggi.tankblick.data.prefs

import android.content.Context
import android.content.SharedPreferences
import de.mymiggi.tankblick.data.remote.RateLimiterStore

/**
 * Keeps a rate limiter's last-request timestamp in SharedPreferences.
 *
 * SharedPreferences rather than DataStore, which the rest of the app uses,
 * because the limiter has to answer synchronously - see [RateLimiterStore].
 * Reads come from the in-memory map after the first load, and writes go through
 * `apply()`: losing the very last write to a kill is a second of accuracy, not
 * a correctness problem, and blocking the caller on disk would be worse.
 *
 * Not a secret and not user data, so plain preferences are fine here.
 */
class PrefsRateLimiterStore(
    private val preferences: SharedPreferences,
    private val name: String,
) : RateLimiterStore {

    override fun lastRequestAtMillis(): Long? =
        preferences.getLong(name, ABSENT).takeIf { it != ABSENT }

    override fun recordRequestAt(millis: Long) {
        preferences.edit().putLong(name, millis).apply()
    }

    companion object {
        private const val ABSENT = -1L

        /** One file for all limiters; [name] separates them. */
        fun preferencesOf(context: Context): SharedPreferences =
            context.getSharedPreferences("rate_limits", Context.MODE_PRIVATE)
    }
}

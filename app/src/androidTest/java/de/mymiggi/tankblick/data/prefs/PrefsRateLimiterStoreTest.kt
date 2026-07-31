package de.mymiggi.tankblick.data.prefs

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import de.mymiggi.tankblick.data.remote.RateLimiter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs on a device because SharedPreferences needs a real Context. The limiter
 * logic itself is covered by RateLimiterTest on the JVM.
 */
@RunWith(AndroidJUnit4::class)
class PrefsRateLimiterStoreTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val preferences = PrefsRateLimiterStore.preferencesOf(context)

    @Before
    @After
    fun clearPreferences() {
        preferences.edit().clear().commit()
    }

    @Test
    fun reportsNoRequestOnAFreshInstall() {
        assertNull(PrefsRateLimiterStore(preferences, "refresh").lastRequestAtMillis())
    }

    @Test
    fun readsBackARecordedRequest() {
        PrefsRateLimiterStore(preferences, "refresh").recordRequestAt(1_700_000_000_000L)

        assertEquals(
            1_700_000_000_000L,
            PrefsRateLimiterStore(preferences, "refresh").lastRequestAtMillis(),
        )
    }

    @Test
    fun keepsLimitersWithDifferentNamesApart() {
        PrefsRateLimiterStore(preferences, "refresh").recordRequestAt(1_700_000_000_000L)

        assertNull(PrefsRateLimiterStore(preferences, "detail").lastRequestAtMillis())
    }

    /** The point of the whole exercise: a new process inherits the wait. */
    @Test
    fun makesTheIntervalOutliveTheLimiterInstance() {
        var now = 1_700_000_000_000L
        val store = PrefsRateLimiterStore(preferences, "refresh")
        RateLimiter(60_000L, clock = { now }, store = store).tryAcquire()

        now += 15_000L
        val restarted = RateLimiter(
            60_000L,
            clock = { now },
            store = PrefsRateLimiterStore(preferences, "refresh"),
        )

        assertEquals(45L, restarted.tryAcquire())
    }
}

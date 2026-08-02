package de.mymiggi.tankblick

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Which language an unsupported device locale falls back to.
 *
 * Android answers this from the resource qualifiers alone, so it can only be
 * checked against real resources on a device. F-Droid asks that apps fall back
 * to English rather than to the author's own language.
 */
@RunWith(AndroidJUnit4::class)
class LocaleFallbackTest {

    private fun stringsIn(locale: Locale): Context {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val configuration = Configuration(context.resources.configuration).apply {
            setLocales(LocaleList(locale))
        }
        return context.createConfigurationContext(configuration)
    }

    @Test
    fun fallsBackToEnglishForAnUnsupportedLanguage() {
        assertEquals("Save key", stringsIn(Locale.FRENCH).getString(R.string.onboarding_save))
        assertEquals("Save key", stringsIn(Locale.JAPANESE).getString(R.string.onboarding_save))
    }

    @Test
    fun speaksGermanOnAGermanDevice() {
        assertEquals("Key speichern", stringsIn(Locale.GERMAN).getString(R.string.onboarding_save))
        assertEquals("Key speichern", stringsIn(Locale.GERMANY).getString(R.string.onboarding_save))
    }

    @Test
    fun speaksEnglishOnAnEnglishDevice() {
        assertEquals("Save key", stringsIn(Locale.ENGLISH).getString(R.string.onboarding_save))
        assertEquals("Save key", stringsIn(Locale.US).getString(R.string.onboarding_save))
    }
}

package de.mymiggi.tankblick.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemePreferenceTest {

    @Test
    fun `parses every known scheme name`() {
        ColorSchemePreference.entries.forEach { scheme ->
            assertEquals(scheme, ColorSchemePreference.fromName(scheme.name))
        }
    }

    /**
     * A scheme dropped in a later build would otherwise crash on first read of
     * a settings file written by an older one.
     */
    @Test
    fun `falls back to dynamic for an unknown or missing name`() {
        assertEquals(ColorSchemePreference.DYNAMIC, ColorSchemePreference.fromName("NEON"))
        assertEquals(ColorSchemePreference.DYNAMIC, ColorSchemePreference.fromName(null))
        assertEquals(ColorSchemePreference.DYNAMIC, ColorSchemePreference.fromName(""))
    }

    /** Dynamic colour is the default, so it must be the first entry users meet. */
    @Test
    fun `dynamic is the default scheme`() {
        assertEquals(ColorSchemePreference.DYNAMIC, ColorSchemePreference.entries.first())
    }
}

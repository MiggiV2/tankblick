package de.mymiggi.tankblick.navapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoUriTest {

    @Test
    fun `points at the coordinates and labels them`() {
        val uri = GeoUri.forStation(52.530831, 13.440946, "ARAL Berlin")

        assertEquals("geo:52.530831,13.440946?q=52.530831,13.440946(ARAL%20Berlin)", uri)
    }

    /**
     * The label sits inside parentheses in the query, so a name containing one
     * would truncate the label or break the URI outright. "Esso (Autohof)" is
     * not a hypothetical station name.
     */
    @Test
    fun `escapes parentheses in the name`() {
        val uri = GeoUri.forStation(52.5, 13.4, "Esso (Autohof)")

        assertTrue(uri.endsWith("(Esso%20%28Autohof%29)"))
    }

    @Test
    fun `escapes umlauts and sharp s`() {
        val uri = GeoUri.forStation(52.5, 13.4, "Tankstelle Grünstraße")

        assertEquals("geo:52.5,13.4?q=52.5,13.4(Tankstelle%20Gr%C3%BCnstra%C3%9Fe)", uri)
    }

    @Test
    fun `escapes characters that would start a new query parameter`() {
        val uri = GeoUri.forStation(52.5, 13.4, "A&B ?nope #1")

        assertTrue(uri.endsWith("(A%26B%20%3Fnope%20%231)"))
    }

    /** Not every record has a usable name; the coordinates alone still work. */
    @Test
    fun `omits an empty label`() {
        assertEquals("geo:52.5,13.4?q=52.5,13.4", GeoUri.forStation(52.5, 13.4, ""))
        assertEquals("geo:52.5,13.4?q=52.5,13.4", GeoUri.forStation(52.5, 13.4, "   "))
    }

    /** A decimal comma would be read as the latitude/longitude separator. */
    @Test
    fun `always writes coordinates with a decimal point`() {
        val uri = GeoUri.forStation(-3.5, 0.25, "x")

        assertTrue(uri.startsWith("geo:-3.5,0.25?"))
    }

    /** "1.23E-5" would be meaningless to a navigation app. */
    @Test
    fun `does not use scientific notation for small values`() {
        val uri = GeoUri.forStation(0.0000123, 13.4, "x")

        assertEquals("geo:0.0000123,13.4?q=0.0000123,13.4(x)", uri)
    }
}

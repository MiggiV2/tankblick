package de.mymiggi.tankblick.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class StationTest {

    private fun station(
        name: String = "TotalEnergies Berlin",
        brand: String = "TotalEnergies",
        street: String = "Margarete-Sommer-Str.",
        houseNumber: String = "2",
        postCode: String = "10407",
        place: String = "Berlin",
    ) = Station(
        id = "a",
        name = name,
        brand = brand,
        street = street,
        houseNumber = houseNumber,
        postCode = postCode,
        place = place,
        latitude = 52.5,
        longitude = 13.4,
        isOpen = true,
    )

    @Test
    fun `joins the address the german way`() {
        assertEquals("Margarete-Sommer-Str. 2, 10407 Berlin", station().address)
    }

    /** The API sends trailing spaces in street names often enough to matter. */
    @Test
    fun `does not leave a double space from a padded street name`() {
        assertEquals(
            "Margarete-Sommer-Str. 2, 10407 Berlin",
            station(street = "Margarete-Sommer-Str. ").address,
        )
    }

    @Test
    fun `omits a missing house number`() {
        assertEquals("Bundesstraße, 10407 Berlin", station(street = "Bundesstraße", houseNumber = "").address)
    }

    @Test
    fun `omits a missing post code`() {
        assertEquals("Bundesstraße 7, Berlin", station(street = "Bundesstraße", houseNumber = "7", postCode = "").address)
    }

    @Test
    fun `survives having nothing but a street`() {
        assertEquals("Bundesstraße", station(street = "Bundesstraße", houseNumber = "", postCode = "", place = "").address)
    }

    @Test
    fun `survives having no address at all`() {
        assertEquals("", station(street = "", houseNumber = "", postCode = "", place = "").address)
    }

    /** Not every record has a brand, and a nameless row would be useless. */
    @Test
    fun `falls back from brand to name to place`() {
        assertEquals("TotalEnergies", station().displayName)
        assertEquals("TotalEnergies Berlin", station(brand = "").displayName)
        assertEquals("Berlin", station(brand = "", name = "").displayName)
    }
}

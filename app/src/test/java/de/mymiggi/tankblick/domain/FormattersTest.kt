package de.mymiggi.tankblick.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class PriceFormatterTest {

    /**
     * German pumps price to a tenth of a cent and print it small and raised:
     * 1,79 with a superscript 9. Anything else looks wrong to a German driver.
     */
    @Test
    fun `splits a price into euro cents and the tenth of a cent`() {
        assertEquals("1,79" to "9", PriceFormatter.split(1.799))
        assertEquals("1,85" to "0", PriceFormatter.split(1.850))
        assertEquals("2,00" to "0", PriceFormatter.split(2.0))
    }

    @Test
    fun `rounds rather than truncates`() {
        assertEquals("1,79" to "9", PriceFormatter.split(1.7994))
        assertEquals("1,80" to "0", PriceFormatter.split(1.7996))
    }

    @Test
    fun `handles a price below one euro`() {
        assertEquals("0,99" to "9", PriceFormatter.split(0.999))
    }

    @Test
    fun `formats the whole price as one string`() {
        assertEquals("1,799", PriceFormatter.format(1.799))
    }
}

class DistanceFormatterTest {

    @Test
    fun `uses metres below one kilometre`() {
        assertEquals("850 m", DistanceFormatter.format(0.85))
        assertEquals("100 m", DistanceFormatter.format(0.1))
    }

    @Test
    fun `uses kilometres from one upwards`() {
        assertEquals("1,0 km", DistanceFormatter.format(1.0))
        assertEquals("12,3 km", DistanceFormatter.format(12.34))
    }

    @Test
    fun `rounds metres to something a person would say`() {
        assertEquals("450 m", DistanceFormatter.format(0.4523))
    }

    @Test
    fun `treats zero as here`() {
        assertEquals("0 m", DistanceFormatter.format(0.0))
    }
}

class AgeTest {

    private val minute = 60_000L
    private val hour = 60 * minute

    @Test
    fun `describes a fresh value as just now`() {
        assertEquals(Age.JustNow, Age.of(fetchedAt = 1_000L, now = 1_000L + 30_000L))
    }

    @Test
    fun `counts whole minutes`() {
        assertEquals(Age.Minutes(1), Age.of(0L, minute))
        assertEquals(Age.Minutes(59), Age.of(0L, 59 * minute))
    }

    @Test
    fun `counts whole hours`() {
        assertEquals(Age.Hours(1), Age.of(0L, hour))
        assertEquals(Age.Hours(23), Age.of(0L, 23 * hour))
    }

    @Test
    fun `falls back to days`() {
        assertEquals(Age.Days(1), Age.of(0L, 24 * hour))
        assertEquals(Age.Days(3), Age.of(0L, 80 * hour))
    }

    /** A clock correction must not produce "in -2 minutes". */
    @Test
    fun `treats a future timestamp as just now`() {
        assertEquals(Age.JustNow, Age.of(fetchedAt = 10 * minute, now = 0L))
    }
}

package de.mymiggi.tankblick.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Note what this deliberately does not do: it never decides whether a station
 * is open. The API already answers that with `isOpen`, and re-deriving it here
 * would mean parsing free-text German day ranges ("Samstag, Sonntag, Feiertag")
 * and guessing at public holidays - a guess that could contradict the very
 * source it came from. This only makes the published hours readable.
 */
class OpeningHoursTest {

    @Test
    fun `trims seconds off the times`() {
        val hours = OpeningHours.of(
            wholeDay = false,
            entries = listOf(OpeningEntry("Mo-Fr", "05:00:00", "23:30:00")),
            overrides = emptyList(),
        )

        assertEquals(listOf(OpeningRow("Mo-Fr", "05:00 – 23:30")), hours.rows)
    }

    @Test
    fun `keeps a time that already has no seconds`() {
        val hours = OpeningHours.of(
            wholeDay = false,
            entries = listOf(OpeningEntry("Sa", "06:00", "22:00")),
            overrides = emptyList(),
        )

        assertEquals("06:00 – 22:00", hours.rows.single().hours)
    }

    @Test
    fun `keeps the day labels the api sent, in order`() {
        val hours = OpeningHours.of(
            wholeDay = false,
            entries = listOf(
                OpeningEntry("Mo-Fr", "05:00:00", "23:30:00"),
                OpeningEntry("Samstag, Sonntag, Feiertag", "06:00:00", "23:30:00"),
            ),
            overrides = emptyList(),
        )

        assertEquals(
            listOf("Mo-Fr", "Samstag, Sonntag, Feiertag"),
            hours.rows.map { it.days },
        )
    }

    /** Stations open around the clock send an empty list plus wholeDay. */
    @Test
    fun `reports a station that never closes`() {
        val hours = OpeningHours.of(wholeDay = true, entries = emptyList(), overrides = emptyList())

        assertTrue(hours.isWholeDay)
        assertTrue(hours.rows.isEmpty())
    }

    /** wholeDay wins: published hours alongside it would only confuse. */
    @Test
    fun `ignores published hours when the station never closes`() {
        val hours = OpeningHours.of(
            wholeDay = true,
            entries = listOf(OpeningEntry("Mo-Fr", "05:00:00", "23:30:00")),
            overrides = emptyList(),
        )

        assertTrue(hours.isWholeDay)
        assertTrue(hours.rows.isEmpty())
    }

    @Test
    fun `passes exceptions through as the free text they are`() {
        val hours = OpeningHours.of(
            wholeDay = false,
            entries = emptyList(),
            overrides = listOf("24.12.2026, 06:00-14:00", "  ", "31.12.2026 geschlossen"),
        )

        assertEquals(
            listOf("24.12.2026, 06:00-14:00", "31.12.2026 geschlossen"),
            hours.exceptions,
        )
    }

    @Test
    fun `reports having nothing to show`() {
        val hours = OpeningHours.of(wholeDay = false, entries = emptyList(), overrides = emptyList())

        assertTrue(hours.isUnknown)
    }

    @Test
    fun `is not unknown when there is anything to show`() {
        val withRows = OpeningHours.of(
            wholeDay = false,
            entries = listOf(OpeningEntry("Mo-Fr", "05:00:00", "23:30:00")),
            overrides = emptyList(),
        )
        val wholeDay = OpeningHours.of(true, emptyList(), emptyList())

        assertTrue(!withRows.isUnknown)
        assertTrue(!wholeDay.isUnknown)
    }

    @Test
    fun `drops an entry with no usable times`() {
        val hours = OpeningHours.of(
            wholeDay = false,
            entries = listOf(
                OpeningEntry("Mo-Fr", "", ""),
                OpeningEntry("Sa", "06:00:00", "22:00:00"),
            ),
            overrides = emptyList(),
        )

        assertEquals(listOf("Sa"), hours.rows.map { it.days })
    }
}

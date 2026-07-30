package de.mymiggi.tankblick.data.remote.dto

import de.mymiggi.tankblick.fixture
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The fixtures are real responses from the Tankerkönig demo key plus
 * hand-written edge cases. The API expresses "no price available" as the JSON
 * literal `false`, which is the single most common way a naive client breaks.
 */
class StationDtoParsingTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `parses a station list`() {
        val response = json.decodeFromString<StationListResponseDto>(fixture("list_ok.json"))

        assertTrue(response.ok)
        assertEquals(3, response.stations.size)

        val first = response.stations[0]
        assertEquals("474e5046-deaf-4f9b-9a32-9797b778f047", first.id)
        assertEquals("TotalEnergies Berlin", first.name)
        assertEquals("TotalEnergies", first.brand)
        assertEquals("Margarete-Sommer-Str.", first.street)
        assertEquals("2", first.houseNumber)
        assertEquals("10407", first.postCode)
        assertEquals("Berlin", first.place)
        assertEquals(52.530831, first.lat, 1e-9)
        assertEquals(13.440946, first.lng, 1e-9)
        assertEquals(1.1, first.distanceKm!!, 1e-9)
        assertTrue(first.isOpen)
        assertEquals(1.859, first.e5!!, 1e-9)
        assertEquals(1.799, first.e10!!, 1e-9)
        assertEquals(1.749, first.diesel!!, 1e-9)
    }

    @Test
    fun `reads a missing price expressed as false`() {
        val response = json.decodeFromString<StationListResponseDto>(fixture("list_ok.json"))

        val closed = response.stations[1]
        assertFalse(closed.isOpen)
        assertEquals(1.879, closed.e5!!, 1e-9)
        assertNull(closed.e10)
        assertNull(closed.diesel)
    }

    /** A price of zero is not a real price either, and the API does emit it. */
    @Test
    fun `treats zero and null prices as missing`() {
        val response = json.decodeFromString<StationListResponseDto>(fixture("list_ok.json"))

        val third = response.stations[2]
        assertNull(third.diesel)
        assertNull(third.e5)
        assertEquals(1.789, third.e10!!, 1e-9)
    }

    /** The API is allowed to add fields; that must never break an installed app. */
    @Test
    fun `ignores unknown fields`() {
        val response = json.decodeFromString<StationListResponseDto>(fixture("list_ok.json"))

        assertEquals("", response.stations[2].brand)
    }

    /**
     * The API sends post codes as JSON numbers, which silently drops the
     * leading zero every eastern German post code has.
     */
    @Test
    fun `restores the leading zero in a post code`() {
        val body = """
            {"ok":true,"stations":[
              {"id":"a","postCode":1067},
              {"id":"b","postCode":10407},
              {"id":"c","postCode":"01067"}
            ]}
        """.trimIndent()

        val stations = json.decodeFromString<StationListResponseDto>(body).stations

        assertEquals("01067", stations[0].postCode)
        assertEquals("10407", stations[1].postCode)
        assertEquals("01067", stations[2].postCode)
    }

    @Test
    fun `parses an empty result`() {
        val response = json.decodeFromString<StationListResponseDto>(fixture("list_empty.json"))

        assertTrue(response.ok)
        assertTrue(response.stations.isEmpty())
    }

    /**
     * Tankerkönig answers errors with HTTP 200 and ok=false, so the body is the
     * only place the failure shows up.
     */
    @Test
    fun `parses an error body`() {
        val response = json.decodeFromString<StationListResponseDto>(fixture("error_bad_key.json"))

        assertFalse(response.ok)
        assertEquals("Key existiert nicht oder ist deaktiviert", response.message)
        assertTrue(response.stations.isEmpty())
    }

    @Test
    fun `parses station details with opening times`() {
        val response = json.decodeFromString<StationDetailResponseDto>(fixture("detail_ok.json"))

        assertTrue(response.ok)
        val station = response.station!!
        assertEquals("474e5046-deaf-4f9b-9a32-9797b778f047", station.id)
        assertFalse(station.wholeDay)
        assertEquals(2, station.openingTimes.size)
        assertEquals("Mo-Fr", station.openingTimes[0].text)
        assertEquals("05:00:00", station.openingTimes[0].start)
        assertEquals("23:30:00", station.openingTimes[0].end)
        assertTrue(station.overrides.isEmpty())
    }

    @Test
    fun `parses a station that is open around the clock`() {
        val response =
            json.decodeFromString<StationDetailResponseDto>(fixture("detail_whole_day.json"))

        val station = response.station!!
        assertTrue(station.wholeDay)
        assertTrue(station.openingTimes.isEmpty())
        assertEquals(listOf("24.12.2026, 06:00-14:00"), station.overrides)
        assertNull(station.e5)
        assertEquals(1.789, station.e10!!, 1e-9)
        assertNull(station.diesel)
    }

    @Test
    fun `parses a price response`() {
        val response = json.decodeFromString<PricesResponseDto>(fixture("prices_ok.json"))

        assertTrue(response.ok)
        assertEquals(2, response.prices.size)
        val price = response.prices.getValue("474e5046-deaf-4f9b-9a32-9797b778f047")
        assertEquals("open", price.status)
        assertEquals(1.234, price.e5!!, 1e-9)
    }

    /** Closed and unknown stations come back without any price fields at all. */
    @Test
    fun `parses every price status the api reports`() {
        val response = json.decodeFromString<PricesResponseDto>(fixture("prices_mixed.json"))

        assertEquals(5, response.prices.size)

        val closed = response.prices.getValue("278130b1-e062-4a0f-80cc-19e486b4c024")
        assertEquals("closed", closed.status)
        assertNull(closed.e5)

        assertEquals("no prices", response.prices.getValue("1c4f126b-1f3c-4b38-9692-05c400ea8e61").status)
        assertEquals("not found", response.prices.getValue("00000000-0000-0000-0000-000000000000").status)

        val partial = response.prices.getValue("94e70fc4-b22f-4e5a-877f-bc1082cdae81")
        assertNull(partial.e5)
        assertEquals(1.789, partial.e10!!, 1e-9)
        assertNull(partial.diesel)
    }
}

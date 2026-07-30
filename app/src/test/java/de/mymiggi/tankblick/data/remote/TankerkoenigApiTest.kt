package de.mymiggi.tankblick.data.remote

import de.mymiggi.tankblick.domain.ApiKey
import de.mymiggi.tankblick.fixture
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import java.io.IOException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TankerkoenigApiTest {

    private val key = ApiKey.parse("d4f1a2b3-1111-4222-8333-abcdefabcdef")!!
    private val requests = mutableListOf<HttpRequestData>()
    private var now = 0L

    private fun api(
        refreshIntervalMillis: Long = 0L,
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): TankerkoenigApi {
        val engine = MockEngine { request ->
            requests += request
            handler(request)
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        return TankerkoenigApi(
            httpClient = client,
            refreshLimiter = RateLimiter(refreshIntervalMillis) { now },
            detailLimiter = RateLimiter(0L) { now },
        )
    }

    private fun MockRequestHandleScope.jsonResponse(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ) = respond(body, status, headersOf("Content-Type", "application/json"))

    @Test
    fun `returns nearby stations`() = runTest {
        val api = api { jsonResponse(fixture("list_ok.json")) }

        val result = api.findNearby(key, lat = 52.52, lng = 13.44, radiusKm = 5)

        val stations = (result as ApiResult.Success).value
        assertEquals(3, stations.size)
        assertEquals("474e5046-deaf-4f9b-9a32-9797b778f047", stations[0].id)
    }

    @Test
    fun `sends the parameters the api expects`() = runTest {
        val api = api { jsonResponse(fixture("list_ok.json")) }

        api.findNearby(key, lat = 52.52, lng = 13.44, radiusKm = 5)

        val url = requests.single().url
        assertEquals("creativecommons.tankerkoenig.de", url.host)
        assertEquals("/json/list.php", url.encodedPath)
        assertEquals("52.52", url.parameters["lat"])
        assertEquals("13.44", url.parameters["lng"])
        assertEquals("5", url.parameters["rad"])
        assertEquals(key.value, url.parameters["apikey"])
    }

    /**
     * Two rules of this API push us to always ask for everything:
     * with type=<fuel> the response carries a single "price" field instead of
     * e5/e10/diesel, and type=all only accepts sort=dist. Asking for all three
     * prices at once is also what makes switching fuel free of a request.
     */
    @Test
    fun `always asks for all fuels sorted by distance`() = runTest {
        val api = api { jsonResponse(fixture("list_ok.json")) }

        api.findNearby(key, lat = 52.52, lng = 13.44, radiusKm = 5)

        val url = requests.single().url
        assertEquals("all", url.parameters["type"])
        assertEquals("dist", url.parameters["sort"])
    }

    /** The API rejects anything above 25 km, so the app never asks for more. */
    @Test
    fun `clamps the radius to what the api allows`() = runTest {
        val api = api { jsonResponse(fixture("list_ok.json")) }

        api.findNearby(key, lat = 52.52, lng = 13.44, radiusKm = 400)

        assertEquals("25", requests.single().url.parameters["rad"])
    }

    /** Errors arrive with HTTP 200, so the status code alone proves nothing. */
    @Test
    fun `recognises a rejected key inside a 200 response`() = runTest {
        val api = api { jsonResponse(fixture("error_bad_key.json")) }

        val result = api.findNearby(key, 52.52, 13.44, 5)

        assertEquals(ApiResult.InvalidKey, result)
    }

    @Test
    fun `surfaces other api errors with their message`() = runTest {
        val api = api { jsonResponse(fixture("error_parameter.json")) }

        val result = api.stationDetail(key, "not-a-uuid")

        assertEquals(ApiResult.ApiError("parameter error"), result)
    }

    @Test
    fun `maps a server error to its status code`() = runTest {
        val api = api { respondError(HttpStatusCode.ServiceUnavailable) }

        val result = api.findNearby(key, 52.52, 13.44, 5)

        assertEquals(ApiResult.ServerError(503), result)
    }

    @Test
    fun `maps a connection failure to offline`() = runTest {
        val api = api { throw IOException("no route to host") }

        val result = api.findNearby(key, 52.52, 13.44, 5)

        assertEquals(ApiResult.Offline, result)
    }

    @Test
    fun `maps an unparseable body to a malformed response`() = runTest {
        val api = api { jsonResponse("<html>maintenance</html>") }

        val result = api.findNearby(key, 52.52, 13.44, 5)

        assertEquals(ApiResult.MalformedResponse, result)
    }

    @Test
    fun `blocks a refresh that comes too soon and sends no request`() = runTest {
        val api = api(refreshIntervalMillis = 60_000L) { jsonResponse(fixture("list_ok.json")) }

        api.findNearby(key, 52.52, 13.44, 5)
        val second = api.findNearby(key, 52.52, 13.44, 5)

        assertEquals(ApiResult.RateLimited(60L), second)
        assertEquals(1, requests.size)
    }

    @Test
    fun `returns station details`() = runTest {
        val api = api { jsonResponse(fixture("detail_ok.json")) }

        val result = api.stationDetail(key, "474e5046-deaf-4f9b-9a32-9797b778f047")

        val station = (result as ApiResult.Success).value
        assertEquals("TotalEnergies Berlin", station.name)
        assertEquals(2, station.openingTimes.size)
        assertEquals("/json/detail.php", requests.single().url.encodedPath)
        assertEquals("474e5046-deaf-4f9b-9a32-9797b778f047", requests.single().url.parameters["id"])
    }

    /** ok=true but no station is a shape we cannot use. */
    @Test
    fun `treats a detail response without a station as malformed`() = runTest {
        val api = api { jsonResponse("""{"ok":true,"status":"ok"}""") }

        assertEquals(ApiResult.MalformedResponse, api.stationDetail(key, "abc"))
    }

    @Test
    fun `returns prices for the requested stations`() = runTest {
        val api = api { jsonResponse(fixture("prices_mixed.json")) }

        val result = api.prices(key, listOf("a", "b"))

        val prices = (result as ApiResult.Success).value
        assertEquals(5, prices.size)
        assertEquals("a,b", requests.single().url.parameters["ids"])
    }

    /** The API takes at most ten ids per call, so longer lists are chunked. */
    @Test
    fun `splits a long id list into requests of ten`() = runTest {
        val api = api { jsonResponse(fixture("prices_ok.json")) }
        val ids = (1..23).map { "id$it" }

        val result = api.prices(key, ids)

        assertTrue(result is ApiResult.Success)
        assertEquals(3, requests.size)
        assertEquals(10, requests[0].url.parameters["ids"]!!.split(",").size)
        assertEquals(10, requests[1].url.parameters["ids"]!!.split(",").size)
        assertEquals(3, requests[2].url.parameters["ids"]!!.split(",").size)
    }

    @Test
    fun `merges the chunked price responses`() = runTest {
        var call = 0
        val api = api {
            call++
            jsonResponse(
                if (call == 1) {
                    """{"ok":true,"prices":{"a":{"status":"open","e5":1.5}}}"""
                } else {
                    """{"ok":true,"prices":{"b":{"status":"open","e5":1.6}}}"""
                },
            )
        }

        val result = api.prices(key, (1..11).map { "id$it" })

        val prices = (result as ApiResult.Success).value
        assertEquals(setOf("a", "b"), prices.keys)
    }

    @Test
    fun `asking for no prices makes no request`() = runTest {
        val api = api { jsonResponse(fixture("prices_ok.json")) }

        val result = api.prices(key, emptyList())

        assertEquals(emptyMap<String, Any>(), (result as ApiResult.Success).value)
        assertTrue(requests.isEmpty())
    }

    /** One bad chunk must not silently hide the stations that did come back. */
    @Test
    fun `reports a failing chunk instead of returning partial prices`() = runTest {
        var call = 0
        val api = api {
            call++
            if (call == 1) {
                jsonResponse("""{"ok":true,"prices":{"a":{"status":"open"}}}""")
            } else {
                jsonResponse(fixture("error_bad_key.json"))
            }
        }

        val result = api.prices(key, (1..11).map { "id$it" })

        assertEquals(ApiResult.InvalidKey, result)
    }

    @Test
    fun `never puts the api key in the url path`() = runTest {
        val api = api { jsonResponse(fixture("list_ok.json")) }

        api.findNearby(key, 52.52, 13.44, 5)

        assertNull(requests.single().url.encodedPath.takeIf { it.contains(key.value) })
    }
}

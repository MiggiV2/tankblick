package de.mymiggi.tankblick.data.remote

import de.mymiggi.tankblick.data.prefs.Settings
import de.mymiggi.tankblick.data.remote.dto.PricesResponseDto
import de.mymiggi.tankblick.data.remote.dto.StationDetailDto
import de.mymiggi.tankblick.data.remote.dto.StationDetailResponseDto
import de.mymiggi.tankblick.data.remote.dto.StationListResponseDto
import de.mymiggi.tankblick.data.remote.dto.StationPriceDto
import de.mymiggi.tankblick.data.remote.dto.StationSummaryDto
import de.mymiggi.tankblick.domain.ApiKey
import io.ktor.client.HttpClient
import io.ktor.client.call.NoTransformationFoundException
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess
import io.ktor.serialization.ContentConvertException
import java.io.IOException
import kotlinx.serialization.SerializationException

/**
 * Thin client for the Tankerkönig JSON API.
 *
 * Two things about this API shape the whole class:
 *
 * 1. Errors come back as HTTP 200 with `ok: false`, so the status code is never
 *    enough - every response body has to be inspected.
 * 2. The free tier asks for at most one request per minute, a 25 km radius and
 *    ten stations per price call. Those limits are enforced here rather than
 *    discovered from a rejection, so the UI can show a countdown instead of an
 *    error.
 *
 * Nothing here polls. Every method is a direct reaction to something the user
 * did, which is what Tankerkönig's terms of use require.
 */
class TankerkoenigApi(
    private val httpClient: HttpClient,
    private val refreshLimiter: RateLimiter,
    private val detailLimiter: RateLimiter,
    private val baseUrl: String = BASE_URL,
) {

    /**
     * Stations around a point, with all three prices, ordered by distance.
     *
     * The fuel type and the ordering are fixed rather than passed in, and both
     * for the same reason. Asking for a single fuel makes the API answer with
     * one "price" field instead of e5/e10/diesel, and `type=all` in turn only
     * accepts `sort=dist`. Taking everything in one response is also what lets
     * the user switch fuel without spending a request.
     */
    suspend fun findNearby(
        apiKey: ApiKey,
        lat: Double,
        lng: Double,
        radiusKm: Int,
    ): ApiResult<List<StationSummaryDto>> {
        refreshLimiter.tryAcquire()?.let { return ApiResult.RateLimited(it) }

        return request<StationListResponseDto>("$baseUrl/list.php") {
            parameter("lat", lat)
            parameter("lng", lng)
            parameter("rad", radiusKm.coerceIn(Settings.MIN_RADIUS_KM, Settings.MAX_RADIUS_KM))
            parameter("type", TYPE_ALL)
            parameter("sort", SORT_BY_DISTANCE)
            parameter("apikey", apiKey.value)
        }.mapBody { it.stations }
    }

    /** Full record for one station, including opening hours. */
    suspend fun stationDetail(apiKey: ApiKey, stationId: String): ApiResult<StationDetailDto> {
        detailLimiter.tryAcquire()?.let { return ApiResult.RateLimited(it) }

        return request<StationDetailResponseDto>("$baseUrl/detail.php") {
            parameter("id", stationId)
            parameter("apikey", apiKey.value)
        }.mapBody { it.station }
    }

    /**
     * Current prices for the given stations, keyed by station id.
     *
     * Longer lists are split into chunks of [MAX_IDS_PER_PRICE_REQUEST]. If any
     * chunk fails the whole call fails: half a favourites list, silently
     * missing the rest, is worse than an honest error.
     */
    suspend fun prices(
        apiKey: ApiKey,
        stationIds: List<String>,
    ): ApiResult<Map<String, StationPriceDto>> {
        if (stationIds.isEmpty()) return ApiResult.Success(emptyMap())

        refreshLimiter.tryAcquire()?.let { return ApiResult.RateLimited(it) }

        val merged = mutableMapOf<String, StationPriceDto>()
        for (chunk in stationIds.chunked(MAX_IDS_PER_PRICE_REQUEST)) {
            val result = request<PricesResponseDto>("$baseUrl/prices.php") {
                parameter("ids", chunk.joinToString(","))
                parameter("apikey", apiKey.value)
            }.mapBody { it.prices }

            when (result) {
                is ApiResult.Success -> merged += result.value
                else -> return result
            }
        }
        return ApiResult.Success(merged)
    }

    private suspend inline fun <reified T : Any> request(
        url: String,
        block: io.ktor.client.request.HttpRequestBuilder.() -> Unit,
    ): ApiResult<T> = try {
        val response: HttpResponse = httpClient.get(url) { block() }
        if (response.status.isSuccess()) {
            ApiResult.Success(response.body<T>())
        } else {
            ApiResult.ServerError(response.status.value)
        }
    } catch (e: IOException) {
        ApiResult.Offline
    } catch (e: ContentConvertException) {
        // Ktor wraps a parse failure in its own type rather than letting the
        // kotlinx SerializationException through.
        ApiResult.MalformedResponse
    } catch (e: SerializationException) {
        ApiResult.MalformedResponse
    } catch (e: NoTransformationFoundException) {
        ApiResult.MalformedResponse
    }

    /**
     * Applies the `ok` flag that the HTTP status does not carry, then narrows
     * the envelope down to the payload the caller wants.
     */
    private inline fun <T : ResponseEnvelope, R : Any> ApiResult<T>.mapBody(
        payload: (T) -> R?,
    ): ApiResult<R> = when (this) {
        is ApiResult.Success -> when {
            !value.ok -> value.message.toFailure()
            else -> payload(value)?.let { ApiResult.Success(it) } ?: ApiResult.MalformedResponse
        }

        is ApiResult.RateLimited -> this
        is ApiResult.InvalidKey -> this
        is ApiResult.Offline -> this
        is ApiResult.ServerError -> this
        is ApiResult.ApiError -> this
        is ApiResult.MalformedResponse -> this
    }

    /**
     * Tankerkönig reports a bad key as free text rather than a code, so the
     * message is all there is to go on.
     */
    private fun String?.toFailure(): ApiResult<Nothing> = when {
        this == null -> ApiResult.MalformedResponse
        contains("key", ignoreCase = true) -> ApiResult.InvalidKey
        else -> ApiResult.ApiError(this)
    }

    companion object {
        const val BASE_URL = "https://creativecommons.tankerkoenig.de/json"

        /** Hard limit of prices.php. */
        const val MAX_IDS_PER_PRICE_REQUEST = 10

        private const val TYPE_ALL = "all"
        private const val SORT_BY_DISTANCE = "dist"
    }
}

/** Shared shape of every Tankerkönig response. */
internal interface ResponseEnvelope {
    val ok: Boolean
    val message: String?
}

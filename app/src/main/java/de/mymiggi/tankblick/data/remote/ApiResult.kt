package de.mymiggi.tankblick.data.remote

/**
 * Outcome of a Tankerkönig call.
 *
 * Every failure the app can meaningfully react to gets its own case, because
 * each needs a different answer in the UI: a wrong key needs onboarding, a rate
 * limit needs a countdown, and being offline needs the cached data plus a note.
 * Collapsing them into a single "error" would throw exactly that away.
 */
sealed interface ApiResult<out T> {

    data class Success<T>(val value: T) : ApiResult<T>

    /** Blocked by our own [RateLimiter]; no request was sent. */
    data class RateLimited(val retryInSeconds: Long) : ApiResult<Nothing>

    /** The key is unknown or deactivated. The user has to supply a new one. */
    data object InvalidKey : ApiResult<Nothing>

    /** No usable connection. Cached data stays on screen. */
    data object Offline : ApiResult<Nothing>

    /** Reachable but unhappy: 5xx, or Tankerkönig's best-effort service is down. */
    data class ServerError(val statusCode: Int) : ApiResult<Nothing>

    /** `ok: false` with a message that is not about the key. */
    data class ApiError(val message: String) : ApiResult<Nothing>

    /** The response did not look like the API we know. */
    data object MalformedResponse : ApiResult<Nothing>
}

inline fun <T, R> ApiResult<T>.map(transform: (T) -> R): ApiResult<R> = when (this) {
    is ApiResult.Success -> ApiResult.Success(transform(value))
    is ApiResult.RateLimited -> this
    is ApiResult.InvalidKey -> this
    is ApiResult.Offline -> this
    is ApiResult.ServerError -> this
    is ApiResult.ApiError -> this
    is ApiResult.MalformedResponse -> this
}

fun <T> ApiResult<T>.valueOrNull(): T? = (this as? ApiResult.Success)?.value

package de.mymiggi.tankblick.domain

/** Ordering of the nearby list, mirrored by the API's `sort` parameter. */
enum class SortMode(
    val apiValue: String,
) {
    PRICE("price"),
    DISTANCE("dist"),
}

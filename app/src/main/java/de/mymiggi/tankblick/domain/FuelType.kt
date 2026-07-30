package de.mymiggi.tankblick.domain

/** The three fuel types the Tankerkönig API reports prices for. */
enum class FuelType(
    /** Value expected by the API's `type` parameter. */
    val apiValue: String,
) {
    E5("e5"),
    E10("e10"),
    DIESEL("diesel"),
}

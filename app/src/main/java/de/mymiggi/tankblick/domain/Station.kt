package de.mymiggi.tankblick.domain

/**
 * A filling station as the UI needs it: identity, where it is, what it charges,
 * and how old that answer is.
 *
 * [fetchedAt] is part of the model rather than a detail of the cache, because
 * the app is offline-first and the screen has to be able to say "as of eleven
 * minutes ago" instead of implying the number is live.
 */
data class Station(
    val id: String,
    val name: String,
    val brand: String,
    val street: String,
    val houseNumber: String,
    val postCode: String,
    val place: String,
    val latitude: Double,
    val longitude: Double,
    val isOpen: Boolean,
    /** Distance from the last search centre, if this station came from a search. */
    val distanceKm: Double? = null,
    val prices: Prices = Prices(),
    val isFavorite: Boolean = false,
    val favoriteLabel: String? = null,
    /** Epoch millis of the response these prices came from. */
    val fetchedAt: Long = 0L,
) {
    /** Best label for a station, since not every record has a brand. */
    val displayName: String get() = brand.ifBlank { name }.ifBlank { place }

    val address: String
        get() = buildString {
            append(street)
            if (houseNumber.isNotBlank()) append(" ").append(houseNumber)
            if (postCode.isNotBlank() || place.isNotBlank()) {
                append(", ")
                if (postCode.isNotBlank()) append(postCode).append(" ")
                append(place)
            }
        }.trim().trim(',')
}

/** Prices in euro per litre. `null` means the station reported none. */
data class Prices(
    val e5: Double? = null,
    val e10: Double? = null,
    val diesel: Double? = null,
) {
    operator fun get(fuelType: FuelType): Double? = when (fuelType) {
        FuelType.E5 -> e5
        FuelType.E10 -> e10
        FuelType.DIESEL -> diesel
    }

    val hasAny: Boolean get() = e5 != null || e10 != null || diesel != null
}

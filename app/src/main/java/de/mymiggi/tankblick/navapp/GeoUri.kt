package de.mymiggi.tankblick.navapp

import java.math.BigDecimal
import java.math.RoundingMode
import java.net.URLEncoder

/**
 * Builds the `geo:` URI every Android navigation app understands.
 *
 * A plain intent rather than a vendor SDK, so Organic Maps, OsmAnd, Google Maps
 * or anything else the user has installed all work the same way, and the app
 * stays free of a proprietary dependency.
 */
object GeoUri {

    /**
     * `geo:<lat>,<lng>?q=<lat>,<lng>(<name>)`.
     *
     * The coordinates appear twice on purpose: the path positions the map, and
     * the query is what makes apps drop a labelled pin rather than merely pan.
     */
    fun forStation(latitude: Double, longitude: Double, name: String): String {
        val lat = latitude.toPlainString()
        val lng = longitude.toPlainString()
        val label = name.trim()

        return if (label.isEmpty()) {
            "geo:$lat,$lng?q=$lat,$lng"
        } else {
            "geo:$lat,$lng?q=$lat,$lng(${label.encode()})"
        }
    }

    /**
     * Percent-encodes everything that could break out of the label.
     *
     * URLEncoder handles the characters that matter, including the parentheses
     * the label is wrapped in - station names like "Esso (Autohof)" would
     * otherwise truncate the label or break the URI. Two fixups are still
     * needed: it writes a space as "+", which a geo URI reads literally rather
     * than as a space, and the explicit parenthesis replacements guard against
     * ever swapping in an encoder with a more permissive safe set.
     */
    private fun String.encode(): String =
        URLEncoder.encode(this, Charsets.UTF_8.name())
            .replace("+", "%20")
            .replace("(", "%28")
            .replace(")", "%29")

    /**
     * A decimal point, never scientific notation and never a comma - a comma
     * would be read as the separator between latitude and longitude.
     */
    private fun Double.toPlainString(): String =
        BigDecimal(this).setScale(COORDINATE_DECIMALS, RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString()

    /** About a centimetre of resolution; more digits would be noise. */
    private const val COORDINATE_DECIMALS = 7
}

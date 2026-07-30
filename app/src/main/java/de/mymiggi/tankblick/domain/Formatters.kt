package de.mymiggi.tankblick.domain

import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Fuel prices, the way German pumps write them.
 *
 * Prices are quoted to a tenth of a cent, and that last digit is printed small
 * and raised: 1,79 with a superscript 9. Showing a plain "1.799" reads as wrong
 * to anyone who has stood at a German pump.
 */
object PriceFormatter {

    /** @return the main part ("1,79") and the tenth-of-a-cent digit ("9"). */
    fun split(price: Double): Pair<String, String> {
        val tenthsOfCents = (price * 1000).roundToLong()
        val main = tenthsOfCents / 10
        val last = tenthsOfCents % 10
        return String.format(Locale.GERMANY, "%d,%02d", main / 100, main % 100) to last.toString()
    }

    /** The whole price on one line, for places without room for a superscript. */
    fun format(price: Double): String {
        val (main, last) = split(price)
        return main + last
    }
}

object DistanceFormatter {

    /**
     * Metres below a kilometre, kilometres above.
     *
     * Metres are rounded to the nearest fifty: the underlying figure comes from
     * a coarse position fix, and "847 m" would claim a precision that is not
     * there.
     */
    fun format(distanceKm: Double): String = if (distanceKm < 1.0) {
        val metres = (distanceKm * 1000 / 50).roundToInt() * 50
        "$metres m"
    } else {
        String.format(Locale.GERMANY, "%.1f km", distanceKm)
    }
}

/**
 * How old a cached price is.
 *
 * A type rather than a formatted string so the wording stays in the string
 * resources and the arithmetic stays testable.
 */
sealed interface Age {

    data object JustNow : Age
    data class Minutes(val value: Int) : Age
    data class Hours(val value: Int) : Age
    data class Days(val value: Int) : Age

    companion object {
        private const val MINUTE = 60_000L
        private const val HOUR = 60 * MINUTE
        private const val DAY = 24 * HOUR

        fun of(fetchedAt: Long, now: Long): Age {
            // A backwards clock correction must not produce "in -2 minutes".
            val elapsed = (now - fetchedAt).coerceAtLeast(0)
            return when {
                elapsed < MINUTE -> JustNow
                elapsed < HOUR -> Minutes((elapsed / MINUTE).toInt())
                elapsed < DAY -> Hours((elapsed / HOUR).toInt())
                else -> Days((elapsed / DAY).toInt())
            }
        }
    }
}

package de.mymiggi.tankblick.domain

/** One published opening window, as the API sends it. */
data class OpeningEntry(
    /** Free-text German day range, e.g. "Mo-Fr" or "Samstag, Sonntag, Feiertag". */
    val days: String,
    val start: String,
    val end: String,
)

/** A row ready to be shown. */
data class OpeningRow(
    val days: String,
    /** e.g. "05:00 – 23:30". */
    val hours: String,
)

/**
 * The published opening hours of a station, made readable.
 *
 * This deliberately does not work out whether a station is open right now. The
 * API already answers that with `isOpen`, and re-deriving it would mean parsing
 * free-text German day ranges and guessing at regional public holidays - a
 * guess that could end up contradicting the very source it came from. Showing
 * the hours and trusting the API for the status is both simpler and more
 * honest.
 */
data class OpeningHours(
    val isWholeDay: Boolean,
    val rows: List<OpeningRow>,
    /** Free-text exceptions such as holiday hours. Not machine readable. */
    val exceptions: List<String>,
) {

    /** True when there is nothing at all to show. */
    val isUnknown: Boolean get() = !isWholeDay && rows.isEmpty()

    companion object {

        fun of(
            wholeDay: Boolean,
            entries: List<OpeningEntry>,
            overrides: List<String>,
        ): OpeningHours = OpeningHours(
            isWholeDay = wholeDay,
            // Published hours next to "never closes" would only confuse.
            rows = if (wholeDay) emptyList() else entries.mapNotNull { it.toRow() },
            exceptions = overrides.map { it.trim() }.filter { it.isNotEmpty() },
        )

        private fun OpeningEntry.toRow(): OpeningRow? {
            val from = start.toShortTime() ?: return null
            val until = end.toShortTime() ?: return null
            return OpeningRow(days = days, hours = "$from – $until")
        }

        /** "05:00:00" and "05:00" both become "05:00". */
        private fun String.toShortTime(): String? {
            val parts = trim().split(":")
            return if (parts.size >= 2 && parts[0].isNotEmpty() && parts[1].isNotEmpty()) {
                "${parts[0]}:${parts[1]}"
            } else {
                null
            }
        }
    }
}

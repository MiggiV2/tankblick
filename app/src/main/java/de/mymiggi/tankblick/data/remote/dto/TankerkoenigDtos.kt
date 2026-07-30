package de.mymiggi.tankblick.data.remote.dto

import de.mymiggi.tankblick.data.remote.ResponseEnvelope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire format of the Tankerkönig JSON API.
 *
 * Every response carries `ok`, and errors arrive with HTTP 200 and `ok: false`,
 * so the status code alone never tells you whether a call succeeded. Defaults
 * are generous on purpose: an error body contains none of the payload fields.
 */
@Serializable
data class StationListResponseDto(
    override val ok: Boolean = false,
    val status: String? = null,
    override val message: String? = null,
    val license: String? = null,
    val stations: List<StationSummaryDto> = emptyList(),
) : ResponseEnvelope

@Serializable
data class StationSummaryDto(
    val id: String,
    val name: String = "",
    val brand: String = "",
    val street: String = "",
    val houseNumber: String = "",
    @Serializable(with = PostCodeSerializer::class)
    val postCode: String = "",
    val place: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    /** Distance from the search centre in km; absent outside of list.php. */
    @SerialName("dist")
    val distanceKm: Double? = null,
    val isOpen: Boolean = false,
    @Serializable(with = PriceSerializer::class)
    val e5: Double? = null,
    @Serializable(with = PriceSerializer::class)
    val e10: Double? = null,
    @Serializable(with = PriceSerializer::class)
    val diesel: Double? = null,
)

@Serializable
data class StationDetailResponseDto(
    override val ok: Boolean = false,
    val status: String? = null,
    override val message: String? = null,
    val license: String? = null,
    val station: StationDetailDto? = null,
) : ResponseEnvelope

@Serializable
data class StationDetailDto(
    val id: String,
    val name: String = "",
    val brand: String = "",
    val street: String = "",
    val houseNumber: String = "",
    @Serializable(with = PostCodeSerializer::class)
    val postCode: String = "",
    val place: String = "",
    val state: String? = null,
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val isOpen: Boolean = false,
    /** True for stations without opening hours, which then send an empty list. */
    val wholeDay: Boolean = false,
    val openingTimes: List<OpeningTimeDto> = emptyList(),
    /** Free-text exceptions such as holiday hours; not machine readable. */
    val overrides: List<String> = emptyList(),
    @Serializable(with = PriceSerializer::class)
    val e5: Double? = null,
    @Serializable(with = PriceSerializer::class)
    val e10: Double? = null,
    @Serializable(with = PriceSerializer::class)
    val diesel: Double? = null,
)

@Serializable
data class OpeningTimeDto(
    /** Day range in German, e.g. "Mo-Fr" or "Samstag, Sonntag, Feiertag". */
    val text: String = "",
    /** "HH:mm:ss". */
    val start: String = "",
    val end: String = "",
)

@Serializable
data class PricesResponseDto(
    override val ok: Boolean = false,
    val status: String? = null,
    override val message: String? = null,
    val license: String? = null,
    val prices: Map<String, StationPriceDto> = emptyMap(),
) : ResponseEnvelope

@Serializable
data class StationPriceDto(
    /** One of "open", "closed", "no prices", "not found". */
    val status: String = "",
    @Serializable(with = PriceSerializer::class)
    val e5: Double? = null,
    @Serializable(with = PriceSerializer::class)
    val e10: Double? = null,
    @Serializable(with = PriceSerializer::class)
    val diesel: Double? = null,
)

package de.mymiggi.tankblick.data.remote.dto

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.nullable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull

/**
 * Reads a Tankerkönig price field, which is not consistently a number.
 *
 * The API writes the JSON literal `false` when it has no price for a fuel type,
 * and occasionally `0` or `null`. A plain `Double?` blows up on the first
 * closed station, which makes this the most common way a client breaks against
 * this API.
 *
 * Everything that is not a positive number becomes `null`, so callers only ever
 * deal with "there is a price" or "there is not".
 */
object PriceSerializer : KSerializer<Double?> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("de.mymiggi.tankblick.Price", PrimitiveKind.DOUBLE).nullable

    override fun deserialize(decoder: Decoder): Double? {
        val jsonDecoder = decoder as? JsonDecoder ?: return decoder.decodeDouble().orNullIfUnusable()
        val element = jsonDecoder.decodeJsonElement() as? JsonPrimitive ?: return null

        // false means "no price"; a real price never arrives as a boolean.
        if (element.booleanOrNull != null) return null

        return element.doubleOrNull.orNullIfUnusable()
    }

    @OptIn(ExperimentalSerializationApi::class)
    override fun serialize(encoder: Encoder, value: Double?) {
        if (value == null) encoder.encodeNull() else encoder.encodeDouble(value)
    }

    private fun Double?.orNullIfUnusable(): Double? = if (this != null && this > 0.0) this else null
}

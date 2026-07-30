package de.mymiggi.tankblick.data.remote.dto

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/**
 * Reads a German post code, which the API sends as a JSON number.
 *
 * That drops the leading zero every eastern post code has: Dresden's 01067
 * arrives as `1067`. Padding it back to five digits keeps addresses correct for
 * a sixth of the country.
 */
object PostCodeSerializer : KSerializer<String> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("de.mymiggi.tankblick.PostCode", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String {
        val jsonDecoder = decoder as? JsonDecoder ?: return decoder.decodeString()
        val element = jsonDecoder.decodeJsonElement() as? JsonPrimitive ?: return ""

        val asNumber = element.intOrNull
        return if (asNumber != null) asNumber.toString().padStart(5, '0') else element.content
    }

    override fun serialize(encoder: Encoder, value: String) = encoder.encodeString(value)
}

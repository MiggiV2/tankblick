import java.util.Base64

/**
 * Reads the optional Tankerkoenig key that a build compiles into the app.
 *
 * Two spellings are accepted. A plain UUID is what you type locally. The
 * F-Droid recipe carries the key base64-encoded and then reversed instead, the
 * same shape `org.woheller69.spritpreise` has used since 2022: fdroiddata is a
 * public and heavily crawled repository, and a bare UUID sitting in it is a key
 * waiting to be harvested by something that greps for UUIDs.
 *
 * This is obfuscation, not protection. Anyone holding the APK can read the key
 * out of it with `strings`. It only keeps the metadata itself uninteresting.
 */
object TankblickApiKey {

    private val UUID_FORMAT =
        Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

    /**
     * Returns the key to compile in, or an empty string when there is none.
     *
     * Throws when a value was given but is neither spelling, so a typo fails
     * the build instead of every request at runtime. The value is kept out of
     * the message: build logs get pasted into issues.
     */
    fun resolve(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        if (UUID_FORMAT.matches(trimmed)) return trimmed.lowercase()

        val decoded = decodeReversedBase64(trimmed)
        require(decoded != null && UUID_FORMAT.matches(decoded)) {
            "tankblick.apiKey is neither a Tankerkoenig UUID nor a reversed base64 " +
                "one. Expected 00000000-0000-0000-0000-000000000002 or " +
                "MgAAAAAAAAA…-style, got ${trimmed.length} characters."
        }
        return decoded.lowercase()
    }

    private fun decodeReversedBase64(value: String): String? = try {
        String(Base64.getDecoder().decode(value.reversed()), Charsets.UTF_8)
    } catch (_: IllegalArgumentException) {
        null
    }
}

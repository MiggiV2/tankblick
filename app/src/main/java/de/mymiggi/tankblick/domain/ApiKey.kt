package de.mymiggi.tankblick.domain

/**
 * A validated Tankerkönig API key.
 *
 * Wrapping the key in its own type keeps the raw string from being passed
 * around by accident: [toString] is masked, so a key cannot slip into a log
 * line, a crash trace or a screenshot just because someone interpolated the
 * wrong variable.
 *
 * Use [parse] to obtain an instance; the constructor is private so an invalid
 * key cannot exist.
 */
@JvmInline
value class ApiKey private constructor(val value: String) {

    /**
     * True for Tankerkönig's public demo key, which answers with dummy data.
     * Useful for trying the app out while waiting for a personal key, which is
     * reviewed by hand and can take days.
     */
    val isDemo: Boolean
        get() = value == DEMO_KEY

    /** Key with its middle replaced, safe to show in the UI. */
    fun masked(): String = "${value.take(8)}-…-${value.takeLast(12)}"

    override fun toString(): String = "ApiKey(${masked()})"

    companion object {
        const val DEMO_KEY: String = "00000000-0000-0000-0000-000000000002"

        private val UUID_FORMAT =
            Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")

        /**
         * Returns the parsed key, or `null` if [raw] is not a Tankerkönig key.
         *
         * Keys are UUIDs. Validating the shape up front turns a typo into an
         * immediate, understandable error instead of an HTTP failure minutes
         * later, and it costs no network request.
         */
        fun parse(raw: String): ApiKey? {
            val normalised = raw.trim().lowercase()
            return if (UUID_FORMAT.matches(normalised)) ApiKey(normalised) else null
        }
    }
}

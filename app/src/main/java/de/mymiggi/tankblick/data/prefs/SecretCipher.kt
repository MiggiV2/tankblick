package de.mymiggi.tankblick.data.prefs

/**
 * Symmetric encryption for the one secret this app holds: the user's API key.
 *
 * Kept as an interface so storage logic can be unit-tested on the JVM without
 * an Android Keystore, and so the primitive can be swapped without touching
 * callers.
 */
interface SecretCipher {

    /** Returns [plaintext] encrypted and encoded as an ASCII-safe string. */
    fun encrypt(plaintext: String): String

    /**
     * Returns the plaintext, or `null` if [ciphertext] cannot be decrypted -
     * for example after the Keystore key was invalidated by a lock screen
     * change, or if the stored blob was truncated.
     *
     * Implementations must not throw: an unreadable secret is an expected
     * state that the app recovers from by asking for the key again.
     */
    fun decrypt(ciphertext: String): String?
}

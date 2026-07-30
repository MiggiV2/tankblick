package de.mymiggi.tankblick.data.prefs

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM backed by the Android Keystore.
 *
 * The key material never leaves the Keystore - it is generated there, marked
 * non-exportable, and only ever referenced by alias. Even with root access the
 * stored ciphertext cannot be decrypted off the device.
 *
 * This replaces the deprecated `EncryptedSharedPreferences`. Google's successor
 * is Tink, but that would add roughly a megabyte to the APK for a single
 * short string, so the primitive is used directly instead.
 *
 * Wire format: `[12-byte IV][ciphertext + 16-byte GCM tag]`, Base64 encoded.
 * A fresh random IV is generated per encryption by the Keystore provider, which
 * is what makes GCM safe to reuse the same key for.
 */
class AndroidKeystoreCipher(
    private val alias: String = DEFAULT_ALIAS,
) : SecretCipher {

    override fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, loadOrCreateKey())

        val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val payload = cipher.iv + encrypted
        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    override fun decrypt(ciphertext: String): String? = try {
        val payload = Base64.decode(ciphertext, Base64.NO_WRAP)
        if (payload.size <= IV_LENGTH) {
            null
        } else {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                loadOrCreateKey(),
                GCMParameterSpec(TAG_LENGTH_BITS, payload, 0, IV_LENGTH),
            )
            val plaintext = cipher.doFinal(payload, IV_LENGTH, payload.size - IV_LENGTH)
            String(plaintext, Charsets.UTF_8)
        }
    } catch (e: GeneralSecurityException) {
        // Key invalidated, tag mismatch or corrupt blob. All mean the same to
        // callers: the secret is gone and has to be entered again.
        null
    } catch (e: IllegalArgumentException) {
        // Stored value was not valid Base64.
        null
    }

    private fun loadOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                // No user authentication requirement: the app must be able to
                // refresh prices without a biometric prompt, and the threat this
                // guards against is a stolen data blob, not an unlocked device.
                .setUserAuthenticationRequired(false)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    companion object {
        const val DEFAULT_ALIAS: String = "tankblick_api_key"

        private const val PROVIDER = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_SIZE_BITS = 256
        private const val TAG_LENGTH_BITS = 128
        private const val IV_LENGTH = 12
    }
}

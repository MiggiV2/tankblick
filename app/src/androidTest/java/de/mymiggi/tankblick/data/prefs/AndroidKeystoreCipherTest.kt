package de.mymiggi.tankblick.data.prefs

import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.security.KeyStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs on a device because there is no Android Keystore on the JVM. Storage
 * behaviour around this class is covered by the ApiKeyStore unit tests.
 */
@RunWith(AndroidJUnit4::class)
class AndroidKeystoreCipherTest {

    private val alias = "tankblick_test_key"
    private val cipher = AndroidKeystoreCipher(alias)
    private val secret = "d4f1a2b3-1111-4222-8333-abcdefabcdef"

    @Before
    @After
    fun removeTestKey() {
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias)
    }

    @Test
    fun roundTripsASecret() {
        assertEquals(secret, cipher.decrypt(cipher.encrypt(secret)))
    }

    @Test
    fun doesNotStoreThePlaintext() {
        val encrypted = cipher.encrypt(secret)

        assertNotEquals(secret, encrypted)
        assertTrue(
            "ciphertext must not contain the plaintext",
            !String(Base64.decode(encrypted, Base64.NO_WRAP), Charsets.ISO_8859_1).contains(secret),
        )
    }

    /** GCM is only safe with a fresh IV per message, so identical input must differ. */
    @Test
    fun producesADifferentCiphertextEveryTime() {
        assertNotEquals(cipher.encrypt(secret), cipher.encrypt(secret))
    }

    @Test
    fun returnsNullForATamperedCiphertext() {
        val payload = Base64.decode(cipher.encrypt(secret), Base64.NO_WRAP)
        payload[payload.size - 1] = (payload[payload.size - 1] + 1).toByte()

        assertNull(cipher.decrypt(Base64.encodeToString(payload, Base64.NO_WRAP)))
    }

    @Test
    fun returnsNullForGarbageInsteadOfThrowing() {
        assertNull(cipher.decrypt("this is not base64 ciphertext!!"))
        assertNull(cipher.decrypt(""))
        assertNull(cipher.decrypt(Base64.encodeToString(ByteArray(4), Base64.NO_WRAP)))
    }

    /**
     * A wiped Keystore key must read as "no secret", not as a crash - this is
     * what happens after the user changes their lock screen.
     */
    @Test
    fun returnsNullAfterTheKeyIsGone() {
        val encrypted = cipher.encrypt(secret)

        removeTestKey()

        assertNull(cipher.decrypt(encrypted))
    }

    @Test
    fun handlesAnEmptySecret() {
        assertEquals("", cipher.decrypt(cipher.encrypt("")))
    }
}

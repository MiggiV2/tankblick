package de.mymiggi.tankblick.data.prefs

/**
 * Reversible stand-in for the Android Keystore cipher. Deliberately trivial:
 * these tests are about [ApiKeyStore]'s behaviour, not about cryptography.
 * The real primitive is covered by AndroidKeystoreCipherTest in androidTest.
 */
class FakeSecretCipher(
    /** When true, [decrypt] fails the way a wiped Keystore key would. */
    var failDecryption: Boolean = false,
) : SecretCipher {

    override fun encrypt(plaintext: String): String = plaintext.reversed()

    override fun decrypt(ciphertext: String): String? =
        if (failDecryption) null else ciphertext.reversed()
}

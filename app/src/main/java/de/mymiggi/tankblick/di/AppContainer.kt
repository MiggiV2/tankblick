package de.mymiggi.tankblick.di

import android.content.Context
import de.mymiggi.tankblick.data.prefs.AndroidKeystoreCipher
import de.mymiggi.tankblick.data.prefs.ApiKeyStore
import de.mymiggi.tankblick.data.prefs.SettingsStore
import de.mymiggi.tankblick.data.prefs.secretsDataStore
import de.mymiggi.tankblick.data.prefs.settingsDataStore

/**
 * The app's object graph, wired by hand.
 *
 * Deliberately not Hilt: this app has a handful of singletons, and avoiding an
 * annotation processor keeps the build simple and easier to reproduce
 * byte-for-byte for F-Droid.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val apiKeyStore: ApiKeyStore by lazy {
        ApiKeyStore(appContext.secretsDataStore, AndroidKeystoreCipher())
    }

    val settingsStore: SettingsStore by lazy {
        SettingsStore(appContext.settingsDataStore)
    }
}

package de.mymiggi.tankblick.data.prefs

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore

/**
 * Secrets live in their own store, separate from settings, so backup rules can
 * exclude exactly this file and nothing else.
 *
 * The name is referenced verbatim in res/xml/backup_rules.xml and
 * res/xml/data_extraction_rules.xml - renaming it here without updating those
 * would silently start backing up the encrypted key.
 */
internal val Context.secretsDataStore by preferencesDataStore(name = "tankblick_secrets")

internal val Context.settingsDataStore by preferencesDataStore(name = "tankblick_settings")

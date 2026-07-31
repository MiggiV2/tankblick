package de.mymiggi.tankblick.domain

/** Whether to follow the system, or force light or dark. */
enum class DarkModePreference {
    SYSTEM,
    LIGHT,
    DARK,
}

/**
 * Which colours the app paints with.
 *
 * [DYNAMIC] takes them from the wallpaper on Android 12+ and is the default,
 * because it makes the app feel native. The named schemes exist for everyone
 * who does not want their fuel prices tinted by whatever photo is on their
 * home screen, and for devices too old to have dynamic colour at all.
 */
enum class ColorSchemePreference {
    DYNAMIC,
    PETROL,
    FOREST,
    AMBER,
    PLUM,
    ;

    companion object {
        /** Falls back to the default rather than throwing on an unknown name. */
        fun fromName(name: String?): ColorSchemePreference =
            entries.firstOrNull { it.name == name } ?: DYNAMIC
    }
}

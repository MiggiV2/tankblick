package de.mymiggi.tankblick.navapp

import androidx.annotation.StringRes
import de.mymiggi.tankblick.R

/**
 * What to tell the user after trying to open a navigation app.
 *
 * A separate function rather than an `if` inside the composable, so the rule
 * can be tested without a device: a tap that silently does nothing reads as a
 * broken app, and "no navigation app installed" is a real state on a stripped
 * down phone.
 */
@StringRes
fun navigationFeedbackRes(launched: Boolean): Int? =
    // Success says nothing on purpose. The map opening is the feedback, and a
    // toast on top of it would be noise on every single navigation.
    if (launched) null else R.string.navigate_no_app

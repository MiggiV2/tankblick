package de.mymiggi.tankblick

import android.app.Application

/**
 * Owns the app-wide object graph. Dependency wiring is done by hand in
 * [de.mymiggi.tankblick.di.AppContainer] rather than with Hilt or Koin: the app
 * has a handful of singletons, and skipping the annotation processor keeps the
 * F-Droid build simple and reproducible.
 */
class TankblickApplication : Application()

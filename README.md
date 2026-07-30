# Tankblick

Schnelle, datensparsame Spritpreis-App für Deutschland. Zeigt aktuelle Preise für
E5, E10 und Diesel aus der [Tankerkönig-API](https://creativecommons.tankerkoenig.de/)
(Daten der Markttransparenzstelle für Kraftstoffe des Bundeskartellamts).

> **Status:** in Entwicklung. Onboarding, Umkreissuche, Favoriten,
> Detailansicht, Navigation und Einstellungen sind implementiert und getestet
> (168 Unit-Tests, 26 instrumentierte Tests).

## Warum noch eine Tankpreis-App?

- **Kein Konto, kein Backend, kein Tracking.** Die App spricht mit genau einem
  Server: `creativecommons.tankerkoenig.de`. Sonst mit niemandem.
- **Keine Hintergrundabfragen.** Netzwerkzugriff passiert nur, wenn du ihn
  auslöst — das schont Akku, Datenvolumen und die freie API.
- **Freie Software**, gebaut für F-Droid: keine Google Play Services, keine
  proprietären SDKs, reproduzierbarer Build.

## Funktionen

- **Onboarding:** eigener Tankerkönig-API-Key oder der öffentliche Demo-Key.
  Der Key wird als UUID validiert, AES-256-GCM-verschlüsselt im Android
  Keystore abgelegt, in DataStore gespeichert und vom Backup ausgeschlossen.
- **Umkreissuche:** Standort über `LocationManager` (keine Play Services),
  Liste mit Preis, Entfernung und Öffnungsstatus, Filter-Chips für E5/E10/
  Diesel, Sortierung nach Preis oder Entfernung. Sortierung und Filterwechsel
  passieren lokal — dafür ist kein neuer Request nötig.
- **Favoriten:** eigener Tab, in einer einzigen Anfrage aktualisiert (der
  Client bündelt Stations-IDs in Zehnerpaketen, wie von der API verlangt).
- **Detailansicht:** Adresse, alle drei Preise, Öffnungszeiten laut API,
  Favoriten-Umschalter mit eigenem Label, Navigations-Button.
- **Navigation** über einen einfachen `geo:`-Intent — entweder die in den
  Einstellungen gewählte App oder der System-Dialog.
- **Einstellungen:** Suchradius (1–25 km), Wahl der Navigations-App,
  API-Key (maskiert, austauschbar, löschbar), Info-/Datenschutz-Abschnitt.
- **Offline-first:** Room-Cache, jeder Bildschirm zeigt das Alter seiner
  Daten, ein fehlgeschlagener Refresh leert den Cache nie, Preisverlauf wird
  beim Start nach 30 Tagen bereinigt.
- **Selbst auferlegtes Rate-Limiting:** 60 s zwischen Refreshes, 2 s Debounce
  für Detailabfragen, Radius auf 25 km gedeckelt.
- Deutsch ist die Standard-Locale (`values/`), Englisch liegt in
  `values-en/`.

## Datenschutz

| Was | Wohin |
|---|---|
| Dein Standort | Verlässt das Gerät nur als Koordinatenpaar im Request an Tankerkönig, und nur wenn du eine Umkreissuche startest. |
| Dein API-Key | Bleibt auf dem Gerät. AES-256-GCM-verschlüsselt mit einem nicht exportierbaren Schlüssel aus dem Android Keystore. Vom Cloud-Backup und der Geräteübertragung ausgenommen. |
| Favoriten, Einstellungen, Preis-Cache | Ausschließlich lokal (Room/DataStore). |
| Analytics, Crash-Reporting, Werbung | Gibt es nicht. |

Die App fordert genau drei Berechtigungen an: `INTERNET`,
`ACCESS_COARSE_LOCATION` und `ACCESS_FINE_LOCATION`. Ohne Standortfreigabe
bleibt sie nutzbar — dann eben mit Favoriten statt Umkreissuche.

## API-Key

Tankerkönig gibt keine App-weiten Schlüssel für Client-Apps aus, und ein in der
APK versteckter Key wäre ohnehin extrahierbar. Deshalb bringt **jede Nutzerin
und jeder Nutzer den eigenen Key mit**:

1. Auf <https://onboarding.tankerkoenig.de/> registrieren.
   Pflichtangaben sind Vorname, Nachname und E-Mail-Adresse.
2. Der Key wird manuell geprüft — das kann einige Tage dauern.
3. Key in Tankblick unter *Einstellungen → API-Key* eintragen.

Zum reinen Ausprobieren gibt es einen Demo-Key, der Dummy-Daten liefert.

**Die Tankerkönig-Nutzungsbedingungen sind bindend:** maximal ein Request pro
Minute, maximal 25 km Radius, maximal 10 Stationen pro Preisabfrage. Tankblick
erzwingt diese Grenzen selbst und zeigt dir einen Countdown, statt in ein
Rate-Limit zu laufen.

## Bauen

Voraussetzungen: JDK 17+, Android SDK. Alles Weitere zieht Gradle selbst.

```sh
./gradlew assembleDebug          # Debug-APK
./gradlew testDebugUnitTest      # Unit-Tests
./gradlew connectedDebugAndroidTest  # Instrumentierte Tests (Gerät/Emulator nötig)
./gradlew lint                   # Lint
./gradlew assembleRelease        # Release-APK (unsigniert)
```

Ergebnis unter `app/build/outputs/apk/`.

## Techstack

| Bereich | Wahl |
|---|---|
| Sprache | Kotlin (über AGPs eingebauten Kotlin-Support, KGP 2.2.10) |
| UI | Jetpack Compose, Material 3 |
| Architektur | Single-Activity, ViewModel + StateFlow, Repository, manuelles DI |
| HTTP | Ktor Client (OkHttp-Engine) + kotlinx.serialization |
| Lokale Daten | Room (Favoriten, Cache, Preisverlauf), DataStore (Einstellungen) |
| Standort | `android.location.LocationManager` — **kein** Google Play Services |
| Build | AGP 9.3.1, Gradle 9.5, compileSdk 37, minSdk 24, targetSdk 36 |

Versionsstände im Detail: Compose BOM 2026.06.01, Material 3 1.4.0, Room 2.8.4
(über KSP 2.3.10), Ktor 3.5.1, DataStore 1.2.1.

Die Kotlin-Version ist bewusst an die von AGP gebündelte KGP-Version gepinnt,
damit sich Compose- und Serialization-Compiler-Plugin nicht mit einem zweiten
Kotlin-Compiler auf dem Klassenpfad streiten.

## F-Droid-Metadaten

Store-Texte und Changelogs für F-Droid liegen unter
`fastlane/metadata/android/{de-DE,en-US}/` (Titel, Kurz- und Langbeschreibung,
Changelog pro Versionscode). Screenshots fehlen noch — die erwarteten Pfade
stehen in [RELEASING.md](RELEASING.md).

Der Ablauf für einen Release und die Aufnahme ins F-Droid-Repo ist in
[RELEASING.md](RELEASING.md) beschrieben, inklusive der Punkte, die für einen
reproduzierbaren Build zählen.

## Lizenz & Attribution

Tankblick steht unter der [GPL-3.0-or-later](LICENSE).

Preisdaten: **Tankerkönig / MTS-K**, lizenziert unter
[CC BY 4.0](https://creativecommons.org/licenses/by/4.0/deed.de) —
<https://creativecommons.tankerkoenig.de/>

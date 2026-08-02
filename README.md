# Tankblick

Schnelle, datensparsame Spritpreis-App für Deutschland. Zeigt aktuelle Preise für
E5, E10 und Diesel aus der [Tankerkönig-API](https://creativecommons.tankerkoenig.de/)
(Daten der Markttransparenzstelle für Kraftstoffe des Bundeskartellamts).

> **Status:** 0.2.0. Onboarding, Umkreissuche, Favoriten, Detailansicht,
> Navigation und Einstellungen sind implementiert und getestet
> (203 Unit-Tests, 26 instrumentierte Tests).

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
- **Einstellungen:** Darstellung (hell, dunkel oder wie das System) und vier
  Farbschemata neben dem dynamischen Material-Theme, Suchradius (1–25 km),
  Wahl der Navigations-App, API-Key (maskiert, austauschbar, löschbar — ein
  einkompilierter Key nur austauschbar),
  Info-/Datenschutz-Abschnitt.
- **Offline-first:** Room-Cache, jeder Bildschirm zeigt das Alter seiner
  Daten, ein fehlgeschlagener Refresh leert den Cache nie, Preisverlauf wird
  beim Start nach 30 Tagen bereinigt.
- **Selbst auferlegtes Rate-Limiting:** 60 s zwischen Refreshes, 2 s Debounce
  für Detailabfragen, Radius auf 25 km gedeckelt. Die Wartezeit wird persistiert
  und übersteht einen App-Neustart.
- Deutsch ist die Standard-Locale (`values/`), Englisch liegt in
  `values-en/`.

## Datenschutz

| Was | Wohin |
|---|---|
| Dein Standort | Verlässt das Gerät nur als Koordinatenpaar im Request an Tankerkönig, und nur wenn du eine Umkreissuche startest. |
| Dein API-Key | Bleibt auf dem Gerät. AES-256-GCM-verschlüsselt mit einem nicht exportierbaren Schlüssel aus dem Android Keystore. Vom Cloud-Backup und der Geräteübertragung ausgenommen. |
| Favoriten, Einstellungen, Preis-Cache | Ausschließlich lokal (Room/DataStore). |
| Zeitstempel der letzten Anfrage | Ausschließlich lokal (SharedPreferences `rate_limits`), damit das Rate-Limit einen Neustart übersteht. |
| Analytics, Crash-Reporting, Werbung | Gibt es nicht. |

Die App fordert drei Berechtigungen an: `INTERNET`, `ACCESS_COARSE_LOCATION`
und `ACCESS_FINE_LOCATION`. Ohne Standortfreigabe bleibt sie nutzbar — dann
eben mit Favoriten statt Umkreissuche.

Im fertigen APK steht eine vierte: `de.mymiggi.tankblick.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`.
Die deklariert `androidx.core` selbst und die App vergibt sie an sich selbst
(Schutzlevel `signature`), damit ab API 33 registrierte Broadcast-Receiver nicht
exportiert werden. Sie gewährt keinen Zugriff auf irgendetwas außerhalb der App
und lässt sich nicht abwählen — sie taucht nur auf, weil AGP sie beim Mergen der
Manifeste einfügt.

## API-Key

Builds mit einkompiliertem Key — dazu gehört der aus F-Droid — laufen ohne
Onboarding los. Wird dieser Key von Tankerkönig abgelehnt (`ok: false` oder
HTTP 401/403), merkt sich die App genau diesen Key als tot, schickt dich ins
Onboarding und schreibt dort hin, warum. Ein eigener Key hat immer Vorrang und
wird nie verworfen.

Ohne einkompilierten Key bringt **jede Nutzerin und jeder Nutzer den eigenen
Key mit** — Tankerkönig gibt keine App-weiten Schlüssel für Client-Apps aus:

1. Auf <https://onboarding.tankerkoenig.de/> registrieren.
   Pflichtangaben sind Vorname, Nachname und E-Mail-Adresse.
2. Der Key wird manuell geprüft — das kann einige Tage dauern.
3. Key in Tankblick unter *Einstellungen → API-Key* eintragen.

Zum reinen Ausprobieren gibt es einen Demo-Key, der Dummy-Daten liefert.

### Key beim Bauen einbacken

Wer selbst baut, kann den eigenen Key mitkompilieren und das Onboarding
überspringen:

```sh
./gradlew assembleRelease -Ptankblick.apiKey=<uuid>
# oder dauerhaft: tankblick.apiKey=<uuid> in ~/.gradle/gradle.properties
# oder per Umgebungsvariable: TANKBLICK_API_KEY=<uuid>
```

Ohne die Option bleibt `BuildConfig.API_KEY` leer und die App fragt wie gehabt
nach einem Key. Ein in den Einstellungen eingetragener Key hat immer Vorrang.

Neben der nackten UUID wird ein zweite Schreibweise akzeptiert: base64-kodiert
und dann rückwärts. Genau die steht in der F-Droid-Rezeptur, weil fdroiddata
öffentlich ist und fleißig durchsucht wird — eine UUID im Klartext wäre dort
über kurz oder lang abgegriffen. Schutz ist das keiner, aus der APK liest sie
jeder mit `strings` heraus; es hält nur die Metadaten uninteressant.

```sh
printf %s "<uuid>" | base64 | rev
```

**Nicht in die committete `gradle.properties` schreiben** und ein so gebautes APK
nicht weitergeben: der Key steht im Klartext in der APK und ist mit `strings` in
Sekunden ausgelesen. Die Verschlüsselung im Android Keystore gilt nur für Keys,
die in der App eingetragen wurden.

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
Changelog pro Versionscode, Icon und Screenshots in beiden Sprachen).

Der Ablauf für einen Release und die Aufnahme ins F-Droid-Repo ist in
[RELEASING.md](RELEASING.md) beschrieben, inklusive der Punkte, die für einen
reproduzierbaren Build zählen.

## Lizenz & Attribution

Tankblick steht unter der [GPL-3.0-or-later](LICENSE).

Preisdaten: **Tankerkönig / MTS-K**, lizenziert unter
[CC BY 4.0](https://creativecommons.org/licenses/by/4.0/deed.de) —
<https://creativecommons.tankerkoenig.de/>

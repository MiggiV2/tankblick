# Tankblick

Schnelle, datensparsame Spritpreis-App für Deutschland. Zeigt aktuelle Preise für
E5, E10 und Diesel aus der [Tankerkönig-API](https://creativecommons.tankerkoenig.de/)
(Daten der Markttransparenzstelle für Kraftstoffe des Bundeskartellamts).

> **Status:** in Entwicklung. Aktuell steht das Projektgerüst (M0).

## Warum noch eine Tankpreis-App?

- **Kein Konto, kein Backend, kein Tracking.** Die App spricht mit genau einem
  Server: `creativecommons.tankerkoenig.de`. Sonst mit niemandem.
- **Keine Hintergrundabfragen.** Netzwerkzugriff passiert nur, wenn du ihn
  auslöst — das schont Akku, Datenvolumen und die freie API.
- **Freie Software**, gebaut für F-Droid: keine Google Play Services, keine
  proprietären SDKs, reproduzierbarer Build.

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
| Build | AGP 9.3.1, Gradle 9.5, compileSdk 37, minSdk 24 |

Die Kotlin-Version ist bewusst an die von AGP gebündelte KGP-Version gepinnt,
damit sich Compose- und Serialization-Compiler-Plugin nicht mit einem zweiten
Kotlin-Compiler auf dem Klassenpfad streiten.

## Lizenz & Attribution

Tankblick steht unter der [GPL-3.0-or-later](LICENSE).

Preisdaten: **Tankerkönig / MTS-K**, lizenziert unter
[CC BY 4.0](https://creativecommons.org/licenses/by/4.0/deed.de) —
<https://creativecommons.tankerkoenig.de/>

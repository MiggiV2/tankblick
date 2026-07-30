# Release

Kurzanleitung für einen Tankblick-Release und die Aufnahme bei F-Droid.

## Vor jedem Release

1. `versionCode` und `versionName` in `app/build.gradle.kts` erhöhen.
   `versionCode` muss monoton steigen — F-Droid erkennt Updates ausschließlich
   daran.
2. Changelog anlegen: `fastlane/metadata/android/{de-DE,en-US}/changelogs/<versionCode>.txt`.
   Der Dateiname ist der `versionCode`, nicht der `versionName`.
3. Tests und Lint laufen lassen:

   ```sh
   ./gradlew testDebugUnitTest lint
   ./gradlew connectedDebugAndroidTest   # Gerät oder Emulator nötig
   ```

4. Release-Build prüfen:

   ```sh
   ./gradlew assembleRelease
   ```

5. Taggen und pushen:

   ```sh
   git tag -s v<versionName> -m "Tankblick <versionName>"
   git push origin main --tags
   ```

Ein signierter Tag ist kein Muss, aber F-Droid kann darauf prüfen
(`AllowedAPKSigningKeys` bzw. Tag-Signaturen in der Build-Rezeptur).

## Signieren

F-Droid **signiert selbst**, nachdem es die App aus dem Quelltext neu gebaut
hat. Für den F-Droid-Weg wird hier also nichts signiert.

Für eine eigene, direkt verteilte APK legst du `keystore.properties` im
Projektwurzelverzeichnis an — die Datei ist in `.gitignore` und darf nie
eingecheckt werden:

```properties
storeFile=/absoluter/pfad/zum/tankblick.jks
storePassword=…
keyAlias=tankblick
keyPassword=…
```

Existiert die Datei nicht, bleibt `assembleRelease` unsigniert statt
fehlzuschlagen. Das ist Absicht: ein frischer Clone und CI sollen bauen können.

## Reproduzierbarkeit

F-Droid baut die App neu und vergleicht das Ergebnis mit der veröffentlichten
APK. Damit das gelingt:

- `dependenciesInfo` ist abgeschaltet (`app/build.gradle.kts`). Der sonst
  eingebettete Metadaten-Block ist mit einem Google-Schlüssel verschlüsselt und
  macht den Build unvergleichbar.
- Keine Abhängigkeit außerhalb von Maven Central und Google Maven, keine
  vorgebauten Binaries im Repo außer dem Gradle-Wrapper.
- Der Gradle-Wrapper ist mit `distributionSha256Sum` gepinnt.

Zwei Clean-Builds sollten dieselbe APK ergeben:

```sh
./gradlew clean assembleRelease && sha256sum app/build/outputs/apk/release/*.apk
./gradlew clean assembleRelease && sha256sum app/build/outputs/apk/release/*.apk
```

## Screenshots für F-Droid

Noch offen. Erwartet werden sie unter:

```
fastlane/metadata/android/de-DE/images/phoneScreenshots/1.png
fastlane/metadata/android/en-US/images/phoneScreenshots/1.png
```

Sinnvolle Motive: Umkreisliste, Detailansicht, Favoriten, Einstellungen.
Aufnehmen lassen sie sich mit einem laufenden Emulator über

```sh
adb exec-out screencap -p > 1.png
```

Für die Store-Grafik zusätzlich `images/icon.png` (512×512).

## Aufnahme bei F-Droid

F-Droid-Metadaten liegen **nicht** in diesem Repo, sondern als Rezeptur in
[fdroiddata](https://gitlab.com/fdroid/fdroiddata). Nötig sind dort:

- `metadata/de.mymiggi.tankblick.yml` mit `RepoType: git`, `Repo: <URL>`,
  `Builds:` mit `versionName`/`versionCode`/`commit`/`gradle: yes`
- `AutoUpdateMode: Version` und `UpdateCheckMode: Tags`, damit neue Tags
  automatisch erkannt werden
- Lizenz `GPL-3.0-or-later`, Kategorie z. B. `Navigation` oder `Money`

Die Beschreibungstexte zieht F-Droid aus `fastlane/metadata/` in diesem Repo.

Anti-Features sollten keine nötig sein: keine Werbung, kein Tracking, keine
proprietären Abhängigkeiten. Die App braucht allerdings einen Netzwerkdienst
(Tankerkönig) und einen personenbezogenen API-Key — das gehört in die
Beschreibung, ist aber kein Anti-Feature.

## Nach dem Release

Prüfen, ob der F-Droid-Buildserver die Version reproduzieren konnte
(`https://f-droid.org/wiki/page/de.mymiggi.tankblick/lastbuild`). Schlägt der
Vergleich fehl, liegt es meist an einer neuen Abhängigkeit, die Zeitstempel oder
absolute Pfade einbettet.

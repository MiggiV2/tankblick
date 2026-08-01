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

Je vier Stück pro Sprache liegen bereits unter:

```
fastlane/metadata/android/de-DE/images/phoneScreenshots/1.png
fastlane/metadata/android/en-US/images/phoneScreenshots/1.png
```

Motive: Umkreisliste, Detailansicht, Favoriten, Einstellungen.
Neue aufnehmen lassen sie sich mit einem laufenden Emulator über

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
- Lizenz `GPL-3.0-or-later`, Kategorien `Market & Price` und `Navigation`

Die Beschreibungstexte zieht F-Droid aus `fastlane/metadata/` in diesem Repo.

`Repo` zeigt auf den GitHub-Mirror, nicht auf Forgejo: der Buildserver klont
bei jedem Build, und dafür zählt Verfügbarkeit. `SourceCode`, `IssueTracker`
und `Changelog` bleiben auf `code.mymiggi.de` — dort wird entwickelt.

### Anti-Feature

`NonFreeNet` ist gesetzt und bleibt es. Die App hängt vollständig an der
Tankerkönig-API, deren Serverseite nicht offen ist, und es gibt keine freie
Alternative, auf die sie ausweichen könnte. Dieselbe Einstufung hat
`org.woheller69.spritpreise`, das die gleiche API nutzt.

### Mitgelieferter API-Key

Damit F-Droid-Nutzerinnen nicht durchs Onboarding müssen, wird ein Key beim
Bauen einkompiliert — über `gradleprops` in der Rezeptur:

```yaml
    gradleprops:
      - tankblick.apiKey=<uuid>
```

Wird der Key später gesperrt, antwortet die API mit `ok: false` (oder 401/403).
Die App merkt sich genau diesen Key als abgelehnt, fällt auf das Onboarding
zurück und erklärt dort, warum. Ein Key, den die Nutzerin selbst eingetragen
hat, gewinnt immer und wird nie verworfen.

### Offen vor der Einreichung

- [ ] Tag `v<versionName>` anlegen und pushen — ohne ihn ist `commit:` ins Leere
      gezeigt und die fdroiddata-CI schlägt fehl.
- [ ] `commit:` auf den vollen Hash setzen, nicht auf den Tag-Namen. Die
      F-Droid-Maintainer bestehen bei neuen Apps darauf.
- [ ] Dummy-Key `00000000-0000-0000-0000-000000000000` in `gradleprops` durch
      den echten Key ersetzen.
- [ ] Build lokal gegenprüfen: `fdroid build de.mymiggi.tankblick`. AGP 9.x,
      Gradle 9.5 und compileSdk 37 sind neu genug, dass der Buildserver
      stolpern kann.

## Nach dem Release

Prüfen, ob der F-Droid-Buildserver die Version reproduzieren konnte
(`https://f-droid.org/wiki/page/de.mymiggi.tankblick/lastbuild`). Schlägt der
Vergleich fehl, liegt es meist an einer neuen Abhängigkeit, die Zeitstempel oder
absolute Pfade einbettet.

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

Damit baut [`scripts/release-apk.sh`](scripts/release-apk.sh) die verteilbare
APK:

```sh
./scripts/release-apk.sh
# → build/release/tankblick-2-universal-release.apk
```

Das Skript macht einen Clean-Build, liest `versionCode` und die enthaltenen
ABIs per `aapt2` aus der **fertigen** APK, benennt sie danach und prüft mit
`apksigner`, dass sie wirklich signiert ist. Fehlt `keystore.properties`,
bricht es ab statt eine unsignierte Datei „release" zu nennen. Zusätzliche
Argumente gehen unverändert an Gradle durch.

Die Parsing-Logik hat Tests: `scripts/release-apk.test.sh` (kein Gradle, kein
SDK, kein Schlüssel nötig).

Soll ein API-Key mit einkompiliert werden, gehört er in die **Umgebung**, nicht
auf die Kommandozeile — das Skript gibt die Gradle-Argumente aus, die Umgebung
nicht:

```sh
export TANKBLICK_API_KEY=<uuid>
./scripts/release-apk.sh
```

Ohne die Variable bleibt `BuildConfig.API_KEY` leer und die App fragt im
Onboarding nach einem Key.

`universal` steht im Namen, weil die APK alle vier ABIs enthält — sie bringt
zwei fremde native Libraries mit (`libandroidx.graphics.path.so` aus Compose,
`libdatastore_shared_counter.so` aus DataStore) und es sind keine ABI-Splits
konfiguriert. Käme es je zu Splits, setzt das Skript den echten ABI ein.

### Ins eigene F-Droid-Repo veröffentlichen

Nach dem Build fragt das Skript, ob die APK ins eigene Repo soll, und ruft dann
`publish-fdroid.sh` aus `~/git/private/fdroid-repo` mit dem absoluten Pfad der
APK auf. Das kopiert sie nach `repo/`, signiert den Index neu und schiebt alles
in den k3s-Cluster.

Die Frage kommt nur an einem Terminal. In einer Pipeline oder in CI baut das
Skript und hört danach auf — Veröffentlichen ist keine Nebenwirkung eines
Builds.

```sh
./scripts/release-apk.sh                 # baut, fragt danach
./scripts/release-apk.sh --publish       # baut und veröffentlicht ohne Rückfrage
./scripts/release-apk.sh --no-publish    # baut und fragt nichts
```

Liegt das Repo woanders, sagt `TANKBLICK_FDROID_REPO=/pfad/zum/checkout` wo.

Das eigene Repo ist unabhängig von f-droid.org: dort signierst **du**, hier
signiert F-Droid nach eigenem Rebuild. Dieselbe App aus beiden Quellen hat
verschiedene Signaturen und lässt sich nicht übereinander installieren.

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

### Kein mitgelieferter API-Key

Die Rezeptur setzt **kein** `gradleprops` und backt keinen Key ein. Ein Key in
einer öffentlich heruntergeladenen APK ist nach dem ersten `strings` bekannt,
und sein Rate-Limit von einem Request pro Minute teilen sich dann alle Nutzer.
F-Droid-Builds starten deshalb im Onboarding.

`BuildConfig.API_KEY` bleibt dabei leer — der Gradle-Build kommt ohne die
Property aus. Sollte doch einmal ein Key mitgeliefert werden und die API ihn
später sperren, antwortet sie mit `ok: false` (oder 401/403); die App merkt sich
genau diesen Key als abgelehnt und fällt aufs Onboarding zurück. Ein Key, den
die Nutzerin selbst eingetragen hat, gewinnt immer und wird nie verworfen.

### Was der F-Droid-Scanner nicht durchlässt

`fdroid build` scannt den Quelltext, bevor er baut, und bricht bei Werkzeugen
ab, die zur Buildzeit Binaries nachladen. Konkret aufgefallen:

```
ERROR: Found usual suspect 'org.gradle.toolchains.foojay-resolver' at settings.gradle.kts
```

Der foojay-Resolver lädt JDKs von `api.foojay.io`. Er ist samt der von ihm
erzeugten `gradle/gradle-daemon-jvm.properties` aus dem Projekt entfernt; das
Projekt braucht keine Toolchain-Provisionierung, `compileOptions` steht auf
Java 17 und der Buildserver bringt sein eigenes JDK mit. Neue Plugins vor einem
Release gegen `fdroid build` prüfen, nicht erst in der fdroiddata-CI.

### Vor der Einreichung

- [ ] Tag `v<versionName>` anlegen und pushen — ohne ihn ist `commit:` ins Leere
      gezeigt und die fdroiddata-CI schlägt fehl.
- [ ] `commit:` auf den vollen Hash setzen, nicht auf den Tag-Namen. Die
      F-Droid-Maintainer bestehen bei neuen Apps darauf.
- [ ] Build lokal gegenprüfen. AGP 9.x, Gradle 9.5 und compileSdk 37 sind neu
      genug, dass der Buildserver stolpern kann:

      ```sh
      docker run --rm -v "$PWD":/repo -w /repo \
        registry.gitlab.com/fdroid/docker-executable-fdroidserver:master \
        build --no-tarball -v de.mymiggi.tankblick:<versionCode>
      ```

      Im fdroiddata-Klon ausführen, nicht hier. `lint` und `rewritemeta` aus
      demselben Image laufen lassen — die CI prüft, dass `rewritemeta` keine
      Änderung mehr erzeugt.

## Nach dem Release

Prüfen, ob der F-Droid-Buildserver die Version reproduzieren konnte
(`https://f-droid.org/wiki/page/de.mymiggi.tankblick/lastbuild`). Schlägt der
Vergleich fehl, liegt es meist an einer neuen Abhängigkeit, die Zeitstempel oder
absolute Pfade einbettet.

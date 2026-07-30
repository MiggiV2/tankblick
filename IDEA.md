Ja — **für dich ist das eher ein kleines bis mittleres Projekt**, nicht „eine App von null lernen“. Mit Kotlin, Jetpack Compose und Claude Code kannst du einen brauchbaren F-Droid-MVP vermutlich in einigen Wochen nebenbei bauen; die schwierigen Teile sind weniger Android selbst als API-Key-/Rate-Limit-Design, Standort/Datenschutz, Karten und ein sauber reproduzierbarer F-Droid-Build.  [f-droid](https://f-droid.org/docs/Inclusion_Policy/)

## Beste Datenquelle

Nimm **Tankerkönig**. Die API liefert aktuelle Preise von mehr als 14.000 deutschen Tankstellen, die an die Markttransparenzstelle für Kraftstoffe des Bundeskartellamts melden. Die Daten sind CC BY 4.0 lizenziert und für Apps gedacht. 

Für dein MVP reichen drei Endpunkte:

| App-Funktion | API-Endpunkt | Hinweis |
|---|---|---|
| Tankstellen in der Nähe | `json/list.php` mit `lat`, `lng`, `rad`, `type`, `sort` | Radius maximal 25 km; nach Preis oder Entfernung sortierbar.  |
| Favoriten aktualisieren | `json/prices.php` mit bis zu 10 IDs | Ein Request aktualisiert mehrere gespeicherte Stationen.  |
| Detailseite | `json/detail.php?id=...` | Öffnungszeiten, Adresse und aktuelle Preise.  |

Die aktuelle Swagger-Doku weist zusätzlich eine modernere API-Struktur mit Such- und Statistikendpunkten aus; für ein unkompliziertes MVP sind die dokumentierten JSON-Endpunkte aber völlig ausreichend.  [creativecommons.tankerkoenig](https://creativecommons.tankerkoenig.de/swagger/)

**Wichtig:** Ein API-Key ist nötig und darf nicht in dein öffentliches Git-Repo. Tankerkönig verlangt außerdem On-Demand-Abfragen durch Nutzereingabe; Hintergrund-Polling ohne explizite Aktion soll vermieden werden. Die freie API ist auf einen Request pro Minute und 25 km Radius begrenzt, läuft best effort und ist nicht zum Massenspiegeln aller Tankstellen gedacht. 

## API-Key sinnvoll lösen

Bei einer F-Droid-App kannst du den Key nicht sicher in der APK verstecken — alles in einer Client-App ist extrahierbar.

Die zwei vernünftigen Varianten:

1. **MVP und F-Droid-freundlich: persönlicher Key pro Nutzer.** In den Einstellungen gibt der Nutzer seinen eigenen Tankerkönig-Key ein; Speicherung lokal verschlüsselt mit Android Keystore/EncryptedSharedPreferences. Das vermeidet, dass ein geleakter zentraler Key deine gesamte App lahmlegt.
2. **Später: schlanker eigener Proxy.** Dein Backend hält den Key und erzwingt Caching, Rate Limits und Abuse-Schutz. Das passt zwar zu deinem K3s-/Traefik-Stack, macht aus einer Offline-First-App aber ein dauerhaft zu betreibendes Backend mit Datenschutz- und Betriebspflicht.

Für Version 1 würde ich **Variante 1** nehmen. Ergänze in der App sichtbar die erforderliche Attribution, etwa: „Preisdaten: Tankerkönig / MTS-K, CC BY 4.0“, plus Link; Tankerkönig verlangt die Namensnennung auch im Store-Infotext. 

## Moderner MVP statt alter App

Die APK, die du verlinkt hast, ist wahrscheinlich funktional, aber UX- und Architekturstand wirken klar überholt. Dein Differenzierungsmerkmal muss nicht „noch eine Liste von Tankstellen“ sein, sondern **schnell zur realen Ersparnis**:

- Standortbasierte Liste für E5, E10 oder Diesel, mit Preis, Distanz, Öffnungsstatus und letztem Abrufzeitpunkt
- Favoriten für Zuhause, Arbeit und typische Routen
- „Lohnt sich der Umweg?“: Berechne Ersparnis aus Tankmenge minus geschätzten Umweg-Kosten
- Preisalarme nur lokal bzw. nutzerinitiiert gedacht — keine aggressiven Hintergrundabfragen gegen die freie API
- Direkte Navigation über Geo-Intent zu Organic Maps, OsmAnd oder einer installierten Navi-App
- Kartendarstellung optional erst nach dem Listen-MVP, damit du Kartenprovider und API-Key-Fragen nicht vorziehst

Ein hilfreicher Kernwert wäre beispielsweise: Bei 40 Litern und 6 Cent/Liter Preisdifferenz beträgt die maximale Bruttoersparnis 2,40 Euro. Die App sollte zeigen, ob dieser Betrag den Umweg tatsächlich übersteigt.

## Empfohlener Stack

| Bereich | Wahl |
|---|---|
| Sprache/UI | Kotlin, Jetpack Compose, Material 3 |
| Architektur | Single-Activity, ViewModel, Repository, StateFlow |
| HTTP/JSON | Ktor Client oder Retrofit + Kotlinx Serialization |
| Lokale Daten | Room für Favoriten, letzte Ergebnisse und Preisverlauf |
| Einstellungen/Key | DataStore; Key zusätzlich über Android Keystore schützen |
| Standort | Fused Location Provider oder Android LocationManager; nur „während der Nutzung“ |
| Navigation | Standard-`geo:`-Intent, keine proprietäre SDK-Abhängigkeit nötig |
| Build/Release | Gradle, GitHub/Forgejo Actions, signierte Releases, F-Droid-Metadaten |

F-Droid verlangt FLOSS und baut Apps selbst aus dem Quellcode; eine saubere Lizenz wie GPL-3.0-or-later oder Apache-2.0, ein öffentliches Repository und vollständig freie Build-Abhängigkeiten sind deshalb wichtig.  [f-droid](https://f-droid.org/docs/Inclusion_Policy/)

Für dich würde ich **Kotlin + Compose + Room + Ktor + DataStore** wählen und das Backend zunächst bewusst weglassen. Das bleibt leichtgewichtig, gut testbar und ohne Kubernetes-Pflege.

## Claude-Code-Aufteilung

Claude Code ist sehr gut für das mechanische und beschleunigende Arbeiten, aber du solltest API-Regeln, Berechtigungen und Builds reviewen.

Gute Aufgaben für Claude Code:

- Compose-Screens und UI-States erzeugen
- DTOs für die Tankerkönig-Antworten erstellen
- Repository, Room-Entitäten, Migrations und Unit-Tests scaffolden
- Preisvergleichslogik und Formatierung schreiben
- Fastlane-Metadaten, Screenshots-Checkliste, Lizenzdateien und CI vorbereiten

Was du selbst prüfen solltest:

- Kein echter API-Key in `BuildConfig`, Ressourcen, Commits oder Beispielcode
- Standort nur nach Interaktion und mit nachvollziehbarer Erklärung abfragen
- API nur bei Refresh, Standortwechsel durch Nutzer oder Öffnen der Ansicht aufrufen
- Jede Antwort auf `ok: false`, fehlende Preise und geschlossene Tankstellen robust behandeln
- Der Gradle-Build muss ohne proprietäre Libraries und ohne Zugriff auf private Artefakt-Repositories funktionieren

## Realistische Reihenfolge

1. **Projektgerüst:** Compose, Material 3, Navigation, Ktor, Room, DataStore; Lizenz und öffentliches Forgejo/GitHub-Repo sofort anlegen.
2. **API-Integration:** API-Key-Einstellung, Umkreissuche, Sortierung nach Diesel/E5/E10 und Fehlerzustände.
3. **Nützliche Nutzung:** Favoriten, Detailseite, lokale Zwischenspeicherung und Navigation.
4. **Qualität:** Tests für Preis-/Umweglogik, Offline-Zustand, leere Daten und Rate-Limit-Fehler.
5. **F-Droid-Release:** reproduzierbarer Release-Build, keine Tracker/Closed-Source-SDKs, Metadaten und Attribution vorbereiten.

Das ist ein sehr gutes Claude-Code-Projekt: klarer Scope, eine einfache REST-API und ein echter persönlicher Nutzen in Deutschland. Der Knackpunkt ist nicht, ob du es bauen kannst, sondern das Produkt bewusst klein zu halten: **erst schnelle, datensparsame Preisliste plus Favoriten — Karte, Historie und eigene Infrastruktur später.**  [f-droid](https://f-droid.org/docs/Inclusion_Policy/)

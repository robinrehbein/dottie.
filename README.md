# DOTTIE.

Bis v2.13 hieß das Spiel „PUNKT." (Paket-ID-Wechsel auf
`de.robinrehbein.pointless`, weil der alte Signing-Key verloren ging),
bis v2.14 dann kurz „POINTLESS.". Seit v2.15 heißt es „Dottie." — die
Paket-ID bleibt dabei unverändert, sie ist im Play-Eintrag registriert
und für Nutzer unsichtbar.

Ein Hyper-Casual-Android-Spiel im Retro-Pixel-Look (Jetpack Compose,
alles im Code gezeichnet — keine Assets). Ein Punkt, ein Daumen, sofort
wieder ein Versuch: kurze Runs, hoher Rage-Faktor, Highscore-Jagd.

## Das Spiel: STOPP

Der Punkt kreist von allein auf einer Bahn. Ein Tap, während er in der
grünen Zone ist, zählt — daneben getippt oder die Zone überfahren ist
sofort das Ende. Die helle Zonen-Mitte zählt als PERFEKT: +2 Punkte,
und in Serie steigt der Bonus auf +3, +4 bis maximal +5 pro Treffer
(ein normaler Treffer setzt die Serie zurück, ohne Strafe).

Die physische Schwierigkeit (Tempo, Zonenbreite) wächst mit der Anzahl
der Treffer, nicht mit dem Score — perfekte Serien sind reiner Bonus
und beschleunigen das Spiel nicht doppelt. Twist-Freischaltungen und
Himmelsstufen bleiben Score-basiert, sie sind Belohnung.

Mit steigendem Score schalten sich Twists frei, die pro Zone zufällig
gemischt werden (maximal zwei gleichzeitig):

| Twist | Ab Score | Effekt |
|---|---|---|
| PULS | 5 | Die Zone atmet — wird größer und kleiner |
| DRIFT | 10 | Die Zone wandert langsam über die Bahn |
| GEIST | 15 | Der Punkt blinkt weg — Bahn im Kopf behalten |
| FALLE | 20 | Violette Köder-Zone: nie hineintippen |
| KETTE | 25 | Zwei Zonen nacheinander in gleicher Richtung |

Kuratierte Ausnahme: GEIST + FALLE erscheinen nie gleichzeitig —
unsichtbarer Punkt plus tödliche Köder-Zone wäre Zufalls-Tod statt
Skill. Alle anderen Kombinationen bleiben erlaubt.

Dazu: Himmel färbt sich pro 5er-Stufe Richtung Nacht — und wieder
zurück zum Tag, ein voller Umlauf sind 60 Punkte —, Medaillen ab
10/20/30/40 Punkten, Spott-Texte beim Tod, Haptik-Feedback, „?"-Button
mit Spielerklärung. Wer den eigenen Rekord im Lauf überholt, bekommt
das sofort gefeiert („REKORD GEKNACKT!") — nicht erst beim Tod.

## Retention & Teilen (ab v2.8)

Drei Features zahlen auf tägliches Wiederkommen und organisches
Wachstum ein:

- **Daily Challenge**: Ein Button auf dem Startscreen startet die
  Tages-Challenge — der Kalendertag bestimmt den Zufalls-Seed, alle
  Versuche des Tages (und alle Spieler:innen) bekommen dieselbe Zonen-
  und Twist-Abfolge. Gespeichert werden Tagesbest und die Tages-Serie
  („SERIE: 5 TAGE"); nur der erste Lauf des Tages schreibt die Serie
  fort, eine Lücke reißt sie.
- **Skins**: 42 Punkt-Skins in sechs Familien — einfarbige (Klassik,
  Minze, Lava, Gold, Frost, Schatten, Prisma), gemusterte (Biene, Melone,
  Fliegenpilz, Koi, Galaxie, Karo, Ei, Tiger, Pinguin, Fussball, Donut),
  bewegte (Regenbogen, Aurora, Magma, Neon, Chrom, Welle, Gewitter,
  Konfetti, Disco, Holo) und reagierende (Chamäleon folgt der
  Himmelsstufe, Kombo lädt sich mit der Perfekt-Serie auf, Tinte zieht
  einen Schweif, Thermo glüht mit dem Score, Medaille wechselt mit der
  Medaillenstufe die Legierung, Tageszeit und Jahreszeit folgen der Uhr
  des Geräts). Dazu vier Saison- und drei Gönner-Skins, siehe unten.

  Freigeschaltet wird über Rekord, beste Perfekt-Serie, Daily-Serie —
  und seit v2.20 zusätzlich über **Ausdauer**: Anzahl Läufe, Punkte
  insgesamt, gespielte Tage, verschiedene Monate. Der Grund: Vorher
  hingen 14 von 21 Skins am Rekord, der letzte bei 60 Punkten. Wer bei
  Rekord 25 stehenbleibt, sammelte nie wieder etwas; jetzt fällt der
  erste zusätzliche Skin nach 25 Läufen. Der Regenbogen kommt weiterhin
  zuletzt, wenn alle anderen gesammelt sind. Auswahl über den
  SKINS-Button, nach Familien gegliedert; gesperrte Skins zeigen ihre
  Bedingung — und lassen sich dort per freiwilligem Spot einen Tag lang
  ausprobieren (siehe Monetarisierung).

  **Saison-Skins** (Kürbis im Oktober, Zuckerstange im Dezember, Herz im
  Februar, Osterei im April) sind nur in ihrem Monat verdienbar, dafür
  danach für immer. Geprüft wird deshalb eine gespeicherte Maske und nie
  der Kalender — sonst wäre der Kürbis im November wieder weg. Verpasst
  ist nicht verloren, das Fenster kommt jedes Jahr wieder.

  **Gönner-Skins** (Diamant, Phönix, Onyx) sind gekauft, nicht verdient.
  Sie lösen keine Freischalt-Feier aus, zählen nicht im Sammlungsstand
  und sind keine Bedingung für den Regenbogen — sonst hinge der Abschluss
  der Sammlung am Konto statt am Spielen. Aus demselben Grund zählen
  Saison-Skins ebenfalls nicht mit: Der Regenbogen soll nicht ein Jahr
  auf einen Kalendermonat warten müssen.

  Farben und Schwellen liegen in `SkinPaint` (`:core`): Ein Skin ist dort
  eine Funktion über das 13x13-Raster des Vogels, kein Tripel aus drei
  Farben mehr — Android, Wear, PWA und iOS zeichnen alle dasselbe Raster.
  Zwei Regeln sichert `:core` per Test ab: Kein Skin färbt sich flächig
  wie die grüne Zielzone — mit zwei benannten Ausnahmen im Bestand, denn
  die Schale der Melone trägt exakt GrassDark und Auroras Welle läuft
  durch den Grünbereich, den der Regenbogen ausdrücklich überspringt —
  und reagierende Skins schlüsseln ihren Frame über ihren Auslöser, damit
  der Textur-Cache auf iOS überhaupt greift.
- **Teilen**: Der TEILEN-Button im Game-Over rendert eine Score-Card
  als PNG (komplett im Code, wie alles hier) und öffnet den
  System-Share-Dialog — Score, Medaille, Skin und Daily-Serie inklusive.

Ab v2.9 sind außerdem **Play-Games-Bestenlisten** (Rekord + Daily)
vorbereitet — hart deaktiviert, bis in `res/values/games.xml` echte IDs
aus der Play Console stehen (Anleitung in PUBLISHING.md). Die
Store-Feature-Grafik liegt generiert unter `store/feature-graphic.png`.

Ab v2.11 gibt es zusätzlich eine optionale **tägliche Erinnerung** an
die Daily Challenge (Opt-in über den Startscreen, komplett lokal per
WorkManager, ab Android 13 hinter der Notification-Permission).

Ab v2.17 ist die **Monetarisierung aktiv**: ein freiwilliger
Rewarded-Spot für den **Skin-Tagespass**, seltene Interstitials
(frühestens ab dem 6. Tod einer Sitzung, mit 180-Sekunden-Sperre) und der
einmalige Kauf „Werbung entfernen" über Play Billing. Die AdMob-IDs
stehen in `res/values/ads.xml`; leert man eine der beiden
Anzeigenblock-IDs, fällt alles wieder in den werbefreien Zustand zurück —
kein SDK-Init, keine Requests, kein Consent-Dialog, kein BillingClient,
und die UI sieht aus wie ohne die Abhängigkeiten. Vor der ersten Anzeige
fragt Googles UMP nach der Einwilligung; wo das Pflicht ist, führt eine
Zeile auf dem Startscreen dauerhaft zurück in dieses Formular.
Datenschutzerklärung: `docs/index.html`, restliche Schritte in
PUBLISHING.md.

**Die Design-Entscheidung dahinter:** Werbung rührt weder den Lauf noch
den Rekord an. Der Tod ist endgültig — „Perfekt oder vorbei" ist das
Versprechen des Spiels, und ein gekauftes Weiterspielen würde genau das
entwerten. Der freiwillige Spot schaltet stattdessen einen gesperrten
Punkt-Skin zum Ausprobieren frei: genau einen, nur für den laufenden
Kalendertag, danach fällt die Auswahl automatisch auf KLASSIK zurück.
Dauerhaft verdient werden Skins weiterhin ausschließlich über Medaillen
und Serien — ein Tagespass zählt nicht als Freischaltung und löst die
„NEUER SKIN FREIGESCHALTET!"-Feier nicht aus.

Der Tagespass ist eine reine Android-Sache: Er hängt am Rewarded-Spot,
und PWA (`web/`) sowie iOS (`ios/`) haben keine Werbung — dort ändert
sich nichts.

## Abgleich zwischen Telefon und Uhr (ab v2.19)

Rekord, Lauf-Zahl, beste Perfekt-Serie, Daily-Stand und die Skin-Wahl
gleichen sich über den **Wearable Data Layer** ab (Modul `:sync`). Es
gibt dabei bewusst **keine Haupt- und keine Nebenrolle**: Jedes Gerät
legt seinen Stand ab, liest den der Gegenseite und führt beide mit
`SyncState.mergedWith` (`:core`) zusammen. Weil das Zusammenführen
kommutativ und idempotent ist, landen beide Seiten zwangsläufig beim
selben Ergebnis — unabhängig davon, wer zuerst online war oder wie oft
dieselbe Nachricht ankommt. Ohne diese beiden Eigenschaften könnten sich
zwei Geräte endlos gegenseitig neue Stände schicken.

Die Regeln:

- **Bestleistungen**: der höhere Wert gewinnt. Ein Rekord, der einmal
  existiert hat, darf durch den Abgleich nie verschwinden.
- **Skin-Wahl**: die *neuere* gewinnt, nicht die „größere" — eine
  Auswahl ist eine Entscheidung, kein Rekord. Ein nur geliehener
  Tagespass-Skin wird gar nicht erst mitgeteilt: geliehen ist nicht
  verdient, und die Uhr leitet ihre Freischaltungen ohnehin selbst aus
  den Bestleistungen ab.
- **Daily-Serie**: der Sonderfall. Wer gestern auf der Uhr und heute am
  Telefon gespielt hat, hat die Serie fortgesetzt — auch wenn das
  Telefon für sich genommen bei 1 stand, weil es von gestern nichts
  wusste. Bei aufeinanderfolgenden Tagen zählt deshalb `gestern + 1`,
  bei einer echten Lücke reißt die Serie wie gewohnt.

Der Abgleich läuft nur, solange eine der beiden Apps offen ist — bewusst
ohne Hintergrunddienst. Beim Öffnen wird geholt, was die Gegenseite
zuletzt abgelegt hat, auch wenn deren App längst geschlossen ist. Ohne
gekoppelte Uhr oder ohne Play-Dienste passiert schlicht nichts; das
Spiel läuft davon unberührt.

## Sprachen (ab v2.11)

Die App ist zweisprachig: **Englisch** ist die Standardsprache,
**Deutsch** liegt in `values-de/` und wird auf deutschen Geräten
automatisch gewählt. Alle UI-Texte kommen aus `strings.xml` — neue
Strings immer in beiden Dateien ergänzen. Store-Texte in beiden
Sprachen sowie generierte Screenshots (je 4 Motive DE/EN unter
`store/screenshots/`) stehen bereit, siehe PUBLISHING.md.

## Sound (ab v2.7)

Chiptune-Soundeffekte im NES-Stil, komplett zur Laufzeit im Code
synthetisiert (Rechteckwellen + Rauschen, `ChipSynth`) — es gibt keine
Audio-Assets im Repo. Die WAVs werden einmalig in den App-Cache
geschrieben und laufen latenzarm über einen `SoundPool`:

- Treffer-Blip klettert innerhalb jeder 5er-Stufe eine Pentatonik hoch —
  jeder Lauf spielt seine eigene kleine Melodie
- PERFEKT ist ein Münz-Sound, der pro Serien-Stufe höher klingt
- Tod = fallender Sweep + Rausch-Burst, Twists/Stufen = Fanfare,
  neuer Rekord = eigener Jingle
- Ton-Schalter auf dem Startscreen („TON: AN/AUS"), Einstellung bleibt
  gespeichert

## Ehemaliger zweiter Modus: FLIP (entfernt in v2.6)

Bis v2.5 hatte die App einen zweiten Modus **FLIP** — einen
Gravity-Flip-Runner (Tap kippt die Schwerkraft, orangene Blocksäulen
ausweichen). Wir haben uns entschieden, uns erstmal voll auf STOPP zu
konzentrieren, und FLIP in v2.6 komplett entfernt.

**Falls FLIP zurückkommen soll:** Der letzte funktionierende Stand mit
beiden Modi ist als Tag [`v2.5-mit-flip`](../../tree/v2.5-mit-flip)
archiviert (Commit `defaab2`) — inklusive Modus-Umschalter,
Fairness-Tuning der Blocksäulen (v2.3/v2.4) und Hilfe-Overlay-Texten.
Die letzte APK mit FLIP ist das Release `apk-build-13` (v2.5).
Der gespeicherte FLIP-Highscore wird beim Update nicht gelöscht, nur
nicht mehr angezeigt.

Festgehaltene Design-Ideen für ein FLIP-Comeback („FLIP-Münzen"):

- Münzen als Sammel-Köder auf dem Scheitelpunkt echter Flip-Flugbahnen
  (Spawner kennt die Physik → Erreichbarkeit garantiert)
- Nur Sammeln, kein Schutz — Gier als Kern: riskante Münzen nahe an
  Säulen, „aus Gier gestorben" füttert den Rage-Faktor
- Münzwert +1 auf den normalen Score, keine zweite Währung, kein Shop
- Münzen ab Score 5, etwa jede 3.–4. Säule, Pixel-Explosion + Haptik
  beim Einsammeln, eigene Gier-Spott-Texte beim Tod

## Web-Version (PWA)

Unter `web/` liegt ein kompletter Browser-Port als Progressive Web App —
statische Dateien ohne Build-Tooling, Spiellogik 1:1 aus `TimingGame`
portiert, offline spielbar (Service Worker). Auf iPhones ist das der
kostenlose Verteilweg: Safari → Teilen → „Zum Home-Bildschirm".
Der Workflow `deploy-pages.yml` veröffentlicht sie bei jedem Push auf
`main` nach GitHub Pages:
**<https://dottie.robinrehbein.de/>** (Datenschutzerklärung unter
`/datenschutz/`). Tests: `node web/tests/run-tests.js`.

## iOS-Port

Unter `ios/` liegt ein nativer Swift-Port (SpriteKit), gebaut über
XcodeGen aus `ios/project.yml` — Details und Verteilweg in
[ios/README.md](ios/README.md).

## Vier Plattformen, eine Wahrheit

Die Spiellogik gibt es dreifach: Kotlin in `:core`, Swift in `ios/`,
JavaScript in `web/`. Damit die Ports nicht auseinanderlaufen, erzeugt
`:core` eine Datei mit Soll-Werten — Konstanten, Medaillen-Schwellen,
Skin-Farben, die Zahlenfolge von Kotlins Zufallsgenerator und zwei
komplette Läufe Treffer für Treffer:

```
parity/golden-vectors.txt
```

Alle drei prüfen sich dagegen (`./gradlew :core:test`,
`node web/tests/run-tests.js`, `xcodebuild test` in der iOS-CI). Ändert
sich `:core` absichtlich, schreibt
`./gradlew :core:test -Dparity.update=true` die Datei neu — der Diff
zeigt dann, was in `ios/` und `web/` nachzuziehen ist. Alles Weitere in
[parity/README.md](parity/README.md).

`:wear` teilt sich den Kotlin-Code mit `:app` direkt: Spiellogik,
Skin-Farbwerk (`SkinPaint`) und Medaillen (`MedalPaint`) liegen in
`:core`, beide Apps halten nur noch ihre eigenen Texte dazu.

Ob und wann sich die drei Ports mit Kotlin Multiplatform wirklich
zusammenlegen lassen — mit Aufwand, Kosten und Gegenargumenten —, steht
in [ARCHITEKTUR.md](ARCHITEKTUR.md).

## Wear-OS-Prototyp (experimentell)

Im Modul `:wear` liegt ein eigenständiger Prototyp für runde Wear-OS-Uhren:
eine schlanke, alleinstehende Watch-App, die die Spiellogik aus `:core`
wiederverwendet — bewusst abgespeckt bleiben nur Teilen und Notifications.
Feedback kommt über Haptik plus dieselben Chiptune-Sounds wie am Phone
(`ChipSynth` liegt dafür in `:core`, abschaltbar über „TON: AN/AUS" auf
dem Startscreen).
Das Game-Feel der Phone-App ist mit an Bord: die Mario-Tod-Animation
(Hüpfer und Sturz aus dem Bild), Medaillen ab 10/20/30/40 Punkten, das
live eingeblendete „REKORD GEKNACKT!"-Banner und Spott-Texte beim Tod
(eine gekürzte, Wear-taugliche Auswahl).
Auch die Daily Challenge läuft auf der Uhr: gleicher Tages-Seed wie am
Phone (`DailyChallenge` in `:core`), umgeschaltet über die Zeile
„KLASSIK / DAILY" auf dem Start- und Game-Over-Overlay; Tagesbest und
Tages-Serie werden lokal geführt (nur der erste Lauf des Tages schreibt
die Serie fort, eine Lücke reißt sie). Dazu alle Punkt-Skins mit den
Freischalt-Bedingungen des Phones (gemeinsames Farbwerk in `:core`) —
ein Tap auf die kleine Skin-Münze im Startscreen öffnet eine scrollbare
Liste aller freigeschalteten Skins (Drehkrone schiebt den Cursor Skin für
Skin weiter, Tap auf eine Zeile wählt direkt). Die Gönner-Skins bleiben
auf der Uhr gesperrt — dort gibt es kein Billing.
Rekord, Daily-Stand, Skin-Wahl und die Ausdauer-Zähler (Läufe,
Punktesumme, Tage, Monate, Saison-Fortschritt) werden lokal auf der Uhr
gespeichert, getrennt vom Telefon-Store — die Uhr schaltet ihre Skins
also auch ohne Telefon frei.
Auf Uhren mit Zusatztasten (z. B. dem Quick-Button der Galaxy Watch Ultra)
lässt sich statt per Touch auch per Tastendruck tappen — praktisch, weil
der Finger beim Timing sonst genau die Zielzone verdeckt.

Installation zum Testen per WLAN-ADB: Entwicklermodus auf der Uhr
aktivieren, dort WLAN-Debugging einschalten, dann vom Rechner aus
`adb connect UHR-IP:5555` und `adb install wear-debug.apk`.

Alternativ ohne PC: Die CI baut auch ein store-taugliches
`wear-release.aab` (eigener versionCode-Bereich ab 100001), das sich in
der Play Console als zweites Bundle im selben Release verteilen lässt —
die Uhr installiert dann über ihren eigenen Play Store. Anleitung in
[PUBLISHING.md](PUBLISHING.md).

## Build

```
./gradlew assembleDebug assembleRelease bundleRelease
```

CI (GitHub Actions) baut bei jedem Push auf `main` beide APKs sowie das
App Bundle (`.aab`) für den Play Store, führt die Unit-Tests aus und
veröffentlicht alles als Pre-Release (`apk-build-N`-Tags) unter
Releases. Jeder Pull Request läuft als Check (Tests + Debug-Build); die
Debug-APK hängt dort als Workflow-Artefakt am Run, ein Release entsteht
erst nach dem Merge auf `main`.

Welcher Workflow wann läuft:

| Workflow | Läuft bei | Prüft |
|---|---|---|
| `build-apk.yml` | Push auf `main`, jedem PR | Kotlin-Tests (`:core` und `:app`), Debug-Build; auf `main` zusätzlich Release-Artefakte |
| `web-tests.yml` | PR/Push mit Änderungen an `web/`, `core/`, `app/src/main/` | Logik-, Farb-, Text- und Paritäts-Tests der PWA |
| `build-ios.yml` | manuell, PR mit Änderungen an `ios/`, `core/`, `parity/` | Paritäts-Tests im Simulator, Device- und Simulator-Build |
| `deploy-pages.yml` | Push auf `main` mit Änderungen an `web/`, `docs/` | veröffentlicht die PWA auf GitHub Pages |

## Veröffentlichung

Der komplette Fahrplan Richtung Play Store — Keystore-Rotation,
Play-Console-Schritte, Datenschutz-URL (GitHub Pages aus `docs/`) und
die Store-Texte — steht in [PUBLISHING.md](PUBLISHING.md).

Der eingecheckte `punkt-release-key.keystore` ist ein reiner
Test-Keystore (Passwort öffentlich in der Git-Historie). Sobald das
Secret `PUNKT_KEYSTORE_BASE64` gesetzt ist, signiert die CI stattdessen
mit dem rotierten Keystore; Details in PUBLISHING.md.

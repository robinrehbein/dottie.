# Plan: Erstkontakt, Flackern, Lila und Bedienelemente

Stand: 25.09.2026, geprüft gegen `main` c5742c9 (Version 2.27).
Spielbare Vorschau aller Entscheidungen: `docs/feedback-check.html`
(dieselbe Seite als Artefakt: https://claude.ai/artifact/7gP6DMMrMaWCp6wU8YskB4, Version 10).

Dieses Dokument hält alles fest, was bis hier entschieden ist, damit bei der
Umsetzung nichts verloren geht. Abschnitt 7 enthält die Prüfung der
Bedienelemente.

---

## 1. Ziel und Rahmen

- Dottie. soll aufregen wie Flappy Bird, sofort verständlich sein, süchtig
  machen und sich monetarisieren lassen.
- Maßstab für jede Änderung: Wut, die zurückbringt, richtet sich gegen einen
  selbst („ich war zu früh“). Wut, die vertreibt, richtet sich gegen das Spiel
  („das war Glück“).
- Der Kern bleibt unangetastet: Punkt kreist, Tap im Grün, Richtung dreht,
  Tempo steigt, Zone schrumpft, Perfekt-Serie +2 bis +5, Sofort-Neustart nach
  0,55 s. Ebenso PULS, DRIFT und KETTE.
- Werbung bleibt wie sie ist (`InterstitialGate.kt`: erst ab dem 6. Tod einer
  Sitzung, dann höchstens alle 3 Minuten). Kein bezahltes Weiterspielen, bevor
  die ersten 60 Sekunden sitzen.

## 2. Tester-Feedback und Ursachen im Code

| Rückmeldung | Ursache | Beleg |
|---|---|---|
| „Wo muss ich hintippen?“ | Der erste Tap im Grün startet nur: Zone springt 63°–160° weiter, Tempo verdoppelt sich (1,2 → 2,4 rad/s). Der Hinweis sagt nicht „tippen“ und nicht „überall“, blinkt (100 % ↔ 25 %) und liegt auf 1080×2400 direkt auf Ring und Zone. Nach dem Aus steht kein Grund. Kein Erststart-Tutorial. | `TimingGame.kt:219`, `GameOverlays.kt:290`, `:373` |
| „Das Flackern ist komisch“ | Twist GEIST (ab 15): Punkt hart an/aus, 1,6 Hz, 38 % weg (0,24 s). Takt hängt an der Laufuhr, nicht an der Zone. In 38 % der Fälle ist der Punkt in der Zonenmitte unsichtbar, bei Score 15 in 2–8 % und bei Höchsttempo in 26–29 % der GEIST-Zonen während der ganzen Zonenpassage. | `TimingGame.kt:205`, `:519–520` |
| „Was ist das Lila?“ | Zwei Lilas: Himmel ab Score 10 (`#7B6FD0`, zeitgleich mit DRIFT) und Falle ab 20 (`#B44FD8`). Die Falle ist so groß und erhaben wie die echte Zone und liest sich als „zweite Zone“. Erklärt wird sie nur nach dem Tod, einmal im Leben, und nur, wenn kein älterer Twist noch unerklärt ist. Vor lila Himmel (Score 50–54) verschwindet sie fast. | `ScenePaint.kt:289`, `:326`, `WorldRenderer.kt:695–702`, `TwistLessons.kt:34`, `GameScreen.kt:459` |

Unklar: Welches Lila die Tester meinen. Beim nächsten Test beide Bilder zeigen
(Himmel bei Score 10, Falle bei Score 20).

## 3. Entschiedene Änderungen (Spezifikation)

### 3.1 Startbildschirm

- **Erster Tap im Grün ist Treffer 1** und startet den Lauf genau dort (normal
  oder PERFEKT, wie im Lauf).
- **Tap daneben kostet nichts:** Punkt wackelt kurz (0,7 s), über dem Ring
  steht „NOCH NICHT“, der Punkt kreist weiter.
- **Neuer Hinweis:** „TIPPE, WENN DER PUNKT IM GRÜNEN IST“, ohne Blinken,
  unterhalb des Rings und ohne Überlappung mit Ring oder Zone.
- **Pixel-Hand** in der Ringmitte (16×17-Raster, Zeigefinger nach oben,
  Kontur `#543847`, Haut weiß, Schatten `#D9CFD6`, gelber Ärmel `#FFD847`,
  halbtransparenter Schlagschatten). Sie macht den Takt vor: Sie drückt genau
  dann, wenn der Punkt im Grün ist (2 Pixel nach unten, Fingerspitze gelblich),
  mit Tipp-Echo an der Fingerspitze, und lässt los, wenn der Punkt draußen ist.
  Sprite steht in `docs/feedback-check.html` (`HAND`).
- **Tipp-Echo überall:** kurzes weißes Quadrat an jeder Tippstelle, damit klar
  wird, dass man überall tippen kann.
- **Zone leuchtet**, solange der Punkt drin ist (Kern `#E4FFC4`, Zone
  `#B8F27A`, weiße Kontur). Nur als Stützräder in den ersten 5 Läufen.
- **Zielzeile** („NÄCHSTE KULISSE: WELTRAUM · 1/5 KULISSEN“) in den ersten
  Läufen ausblenden.

### 3.2 Todesursache nach jedem Aus

- Am Ring, im Freeze-Moment: **ZU FRÜH**, **ZU SPÄT**, **VERPASST**,
  **BOOM!** (Falle), dazu ein weißer Rahmen um den Punkt.
- Wird beim `GameEventDied` aus dem Spielzustand gelesen (Winkel, Zone,
  Fallen-Zone). Die Engine bleibt unverändert.
  - Punkt in der Fallen-Zone → BOOM!
  - Tap mit `relativeToZone() < 0` → ZU FRÜH
  - Tap danach → ZU SPÄT
  - Tod in `update()` durch Überfahren → VERPASST

### 3.3 GEIST wird NEBEL

- Sichtbarkeit hängt an der Bahn statt an der Uhr: Eine Nebelbank liegt direkt
  vor der Zone.
- **Länge in Zeit:** beginnt `0,12 s × Tempo` vor der Zone, reicht bis ins
  erste Viertel der Zone (`to = -h + 0,5·h`). Bei Score 15 ist der Punkt rund
  0,17 s am Stück verdeckt, weniger als die heutige Lücke von 0,24 s und ohne
  Zufall. Der Punkt taucht kurz vor der hellen Mitte auf, für PERFEKT muss man
  vorausahnen.
- **Pixel-Wolke:** bauschige, stufige Kontur aus überlagerten Kugeln,
  zusätzliche kleine Bauschen obendrauf. Schattierung nach Abstand zur
  Unterkante: Unterkante `#A0BEDA`, darüber `#BED4EA`, dann `#D6E5F4`
  (auch rechte Flanke), Oberkante `#FFFFFF`, Inneres `#F4F8FD`. Leichtes
  Wabern, aber feste Form pro Zone (kein Flackern).
- **In der App: ein Wolkenpixel = ein Vogelpixel**, also `u = 2r / GRID` mit
  `GRID = 13`, `r = 0,026 × Bildhöhe` (`WorldRenderer.kt:775`, `:807`). Rund
  9,6 px auf einem 2400-px-Display, die Wolke ist dann etwa 25 Vogelpixel
  lang. Im Mockup ist der Punkt nur ein Klötzchen, dort stimmt das Verhältnis
  nicht.
- Die Wolke endet dort, wo der Nebelbereich endet, und quillt nicht über die
  Zone. Der Punkt muss über die ganze Länge vollständig verdeckt sein
  (am Anfang, in der Mitte und am Ende geprüft).
- **Rollt beim Erscheinen ein** (0,18 s), damit man sofort sieht, dass diese
  Zone Nebel hat.
- **Wölkchen beim Ein- und Austritt** (6 Pixel, 0,35 s): zeigt, wann der
  Punkt verschwindet, nicht wo er dazwischen ist.
- **Optional BLIND! +1:** Treffer, solange der Punkt noch im Nebel ist, gibt
  einen Punkt extra. Das ist eine Änderung an der Punktewertung (siehe
  offene Entscheidungen).
- **Später denkbar:** Summen des Punkts im Nebel gedämpft.
- **Sofortmaßnahme** vor dem Umbau, ohne Regeländerung: GEIST weich ein- und
  ausblenden statt hart schalten.

### 3.4 FALLE wird Bomben

- **Jeder Block der Falle ist eine Minesweeper-Mine** in Blockgröße (7×7:
  schwarze Kugel `#1E1A22`, vier Zacken, weißer Glanzpunkt), mit dünnem hellem
  Rand `#F4E9EC` für dunkle Himmel. Keine grauen Vierecke mehr, keine lila
  Blöcke. Sprite steht in `docs/feedback-check.html` (`MINE`).
- **Rotes Lauflicht:** alle 0,09 s ein Schritt. Erst eine Bombe rot
  (`#E53935`), dann zwei, bis die halbe Kette rot ist, danach wandert der rote
  Block weiter (schwarz-rot-schwarz) und läuft am anderen Ende hinaus, dann von
  vorn. Richtung pro Falle zufällig. Beispiel bei 6 Bomben:
  `R·····  RR····  RRR···  ·RRR··  ··RRR·  ···RRR  ····RR  ·····R  ······`
- **Explosion beim Hineintippen:** weißer Blitz (0,08 s), 12 Pixel-Funken,
  erst gelb/orange, dann rot/grau, über 0,45 s. Am Ring „BOOM!“ und beim
  ersten Mal „BOMBE = NIE TIPPEN“.
- **Erklärung beim ersten Fallen-Tod:** Die Falle darf in `TwistLessons`
  vordrängeln, die übrige Reihenfolge bleibt.
- **Lila raus:** Himmelsstufe bei Score 10 (`ScenePaint.kt:326`, `#7B6FD0`)
  in einen Ton ohne Verwechslung verschieben, zum Beispiel ein tieferes Blau.
  Alle Kulissen prüfen (z. B. STADT Stufe 2 `#7B6B9E`) und die Zusicherung
  `MIN_SKY_SIGNAL_DISTANCE` neu rechnen lassen.
- Verworfen: Stacheln (bleiben nur als Vergleich im Mockup).

## 4. Umsetzung in Phasen

> Ersetzt durch Abschnitt 8 (Arbeitspakete und Wellen). Die Tabelle bleibt als Überblick.

Sortiert nach Wirkung pro Aufwand. Phase 1–3 ändern keine Spielregel.

| Phase | Inhalt | Module | Regel? |
|---|---|---|---|
| 1 | Todesursache am Ring (3.2) | `:ui` | nein |
| 2 | Falle als Bomben mit Lauflicht, Explosion, Erklärung beim ersten Fallen-Tod, Lila raus (3.4) | `:ui`, Farbwert in `:core` | nein |
| 3 | GEIST weich ausblenden (Sofortmaßnahme) | `:ui` | nein |
| 4 | Startbildschirm: erster Tap zählt, Hinweis, Hand, Tipp-Echo, Stützräder, Zielzeile (3.1) | `:core` (`tap()` in READY) + `:ui` | ja, Startregel |
| 5 | GEIST wird NEBEL (3.3) | `:core` (`isDotVisible` an der Bahn) + `:ui` + `:wear` | ja, Twist-Regel |
| 6 | Test mit 5 neuen Leuten (Abschnitt 6) | kein Code | – |

Hinweise zur Umsetzung:

- **Phase 4 und 5** betreffen die Parität: `parity/golden-vectors.txt` neu
  erzeugen und prüfen, Daily-Seeds bleiben gleich, aber Verläufe ändern sich.
  `WearGameController.kt` nutzt dieselbe Engine und muss mitziehen.
- Neue Typen in `:core` auf oberster Ebene anlegen (Objective-C-Export,
  siehe Kommentar in `TimingGame.kt`).
- Für Screenshots ohne Emulator gibt es `ui/src/jvmTest/.../ScreenshotRenderer.kt`
  (`SHOTS_DIR=… ./gradlew :ui:jvmTest`). Twists lassen sich über
  `TimingGame.twistOverride` erzwingen.

## 5. Entscheidungen

Alle Vorschläge aus diesem Plan sind angenommen (25.09.2026). Im Einzelnen:

1. **BLIND! +1** kommt (Treffer, solange der Punkt im Nebel ist).
2. **Stützräder:** Zone leuchtet in den ersten 5 Läufen.
3. **Himmel bei Score 10** bekommt in allen Kulissen einen Ton ohne Lila
   (tieferes Blau), mit neu gerechneter `MIN_SKY_SIGNAL_DISTANCE`.
4. **Wear OS** zieht bei Regeländerungen mit (Startregel, Nebel) und
   übernimmt Bomben und Nebel vereinfacht, soweit das kleine Display es
   zulässt.
5. **Summen im Nebel gedämpft:** später, nicht Teil dieser Runde.
6. **Welten-Schwellen:** WÜSTE 100 Läufe, MEER 2.500 Punkte, BERG 1 Daily,
   STADT Rekord 100, mit Bestandsschutz (Abschnitt 7).
7. **Gesperrte Töne** sind probehörbar.
8. **Game-Over:** TEILEN und MENÜ in der festen Leiste, 0,8 s gesperrt.
9. **Keine Pause** im laufenden Spiel; Zurück tut dort nichts.

Noch offen bleibt nur, welches Lila die Tester meinten. Das klärt der Test
in Abschnitt 6, die Umsetzung hängt nicht davon ab.

## 6. Test mit neuen Spielern

Fünf Leute, Handy in die Hand, nichts erklären, 60 Sekunden zuschauen.

- Wie lange bis zum ersten Treffer?
- Wie oft tippen sie auf den Punkt oder die Zone statt irgendwohin?
- Lesen sie die Todesursache, und spielen sie danach anders?
- Beide Lila-Bilder zeigen: „Welches davon meinst du?“
- Erkennen sie Nebel und Bomben ohne Erklärung?

## 7. Bedienelemente

Geprüft am 25.09.2026. Spielbare Vorschau: `docs/bedienelemente.html`
(Artefakt: https://claude.ai/artifact/XGqtpxgMGUg969nbUQGsvf).

Tippflächen sind überall mindestens 48 dp groß, die Taster-Leiste ist gut zu
treffen. Die Probleme liegen bei Position, Zeitpunkt und Zurück.

### 7.1 Muss

- **Game-Over: Wut-Taps landen im Menü.** Das Spiel reagiert auf das
  Aufsetzen des Fingers überall (`GameScreen.kt:535`), der Neustart ist
  0,55 s gesperrt. MENÜ und TEILEN sitzen mitten in der Spalte direkt unter
  „TIPPEN = NOCHMAL“ und sind sofort aktiv (`GameOverlays.kt:776–789`). Die
  Spalte ist senkrecht zentriert, jede Zusatzzeile verschiebt die Knöpfe.
  → MENÜ und TEILEN in eine feste Leiste am unteren Rand (Stil der
  Taster-Leiste), in den ersten 0,8 s blass und gesperrt. Alles darüber
  heißt „nochmal“. Inhalt oben verankert statt zentriert.
- **Zurück-Geste beendet die App.** Weder `:ui` noch `:app` fangen Zurück ab.
  → Zurück schließt das oberste Overlay (Hilfe, Einstellungen, Sammlung,
  Statistik), im Game-Over geht es zum Startbildschirm, im laufenden Spiel
  passiert nichts, im Startbildschirm ohne Overlay schließt die App.
  Umsetzung: `expect`/`actual` in `:ui`, Android mit
  `androidx.activity.compose.BackHandler`, iOS ohne Wirkung (CMP 1.7.3 hat
  noch keinen gemeinsamen BackHandler).

- **Sammlung (heute „SKINS“) umbauen.** Eine Liste mit 62 Zeilen: 6
  Kulissen, 3 Töne, 7 Rahmen, 46 Skins in 6 Familien
  (`GameOverlays.kt:1294–1600`). Keine Vorschau im Spielbild, gesperrte
  Zeilen sind nicht antippbar und zeigen keinen Fortschritt (obwohl
  `Progress.goals` ihn schon rechnet, `Progress.kt:245–254`), frisch
  Freigeschaltetes wird nicht markiert.
  → **SAMMLUNG** mit vier Reitern **VOGEL · WELT · TON · RAHMEN**, jeweils mit
  Zähler („12/46“).
  → **Schaufenster** oben: Stück Bahn mit Vogel in der angesehenen Welt, bei
  Tönen die Hörprobe. **Anschauen darf man alles, auswählen nur
  Freigeschaltetes.** Gesperrtes zeigt Bedingung, Fortschritt
  („37/500 LÄUFE“) und bei Skins „HEUTE PER SPOT TESTEN“ (Gönner-Skins
  weiterhin ausgenommen).
  → **Raster** mit vier Kacheln pro Zeile, gesperrte blass mit kleinem
  Fortschrittsbalken. Im Reiter VOGEL bleiben die Familien als
  Zwischenüberschriften, das Gönner-Angebot steht unter seiner Familie.
  → **NEU-Markierung** an frischen Kacheln, roter Punkt am Reiter und am
  Taster im Startbildschirm, bis man es angesehen hat.
  → „Kulissen“ heißen im Spiel künftig **Welten**.
  → **Entschieden:** Auch gesperrte Töne sind probehörbar (Anreiz), auswählen
  lassen sie sich erst nach dem Freischalten.

### 7.2 Sollte

- **DAILY sieht aus wie „Spielen“:** einziger gelber Taster, links, startet
  sofort einen Tageslauf (`GameScreen.kt:576–580`). → Sandfarben wie die
  anderen, hervorgehoben nur durch das Serien-Abzeichen. Beim ersten Tap eine
  einmalige Karte: „TAGESLAUF: HEUTE FÜR ALLE GLEICH. DEIN BESTER VERSUCH
  ZÄHLT.“ mit START.
- **Ein Druck-Stil:** Nur der Regler-Knopf hat Schatten und sinkt ein
  (`PixelButton.kt:101–140`). Text-Knöpfe und Taster zeigen die runde
  Material-Welle. → Alle Knöpfe mit 4-dp-Pixelschatten, beim Drücken
  einsinken, keine Welle, kurzer Haptik-Tick.
- **Sichtbarer Ausgang:** Overlays schließen nur über „TIPPEN ZUM
  SCHLIESSEN“ (60 % Weiß, unten). → X-Knopf oben rechts, Tippen daneben
  schließt weiterhin.
- **Schalter statt Zustands-Knöpfe:** „TON: AN“ / „ERINNERUNG: AUS“ sind
  mehrdeutig. → Zeilen mit Beschriftung links und Pixel-Schalter rechts. Die
  Lautsprecher- und Glocken-Symbole in `PixelButton.kt:171–202` sind
  gezeichnet, aber ungenutzt.

- **Welten früher erreichbar (entschieden).** Heute braucht die erste neue
  Welt 500 Läufe, 10.000 Punkte, eine 30-Tage-Daily-Serie oder Rekord 85
  (`ScenePaint.kt:610–615`). Skins kommen dagegen früh (MATCHA nach 5
  Läufen). Neue Schwellen:

  | Welt | Heute | Neu |
  |---|---|---|
  | WIESE | offen | offen |
  | WÜSTE | 500 Läufe | **100 Läufe** |
  | MEER | 10.000 Punkte insgesamt | **2.500 Punkte insgesamt** |
  | BERG | Daily-Serie 30 Tage | **1 Daily gespielt** (`bestDailyStreak >= 1`) |
  | STADT | Rekord 85 | **Rekord 100** |
  | WELTRAUM | alle anderen | alle anderen |

  Nur Belohnungsschwellen, keine Änderung am Spiel. Folgen, die mitziehen
  müssen: die `scene_hint_*`-Texte in allen Sprachen, die Zielzeile
  (`Progress`), Tests, die Schwellen festnageln. STADT wird schwerer als
  heute, damit auch WELTRAUM und der Rahmen KASKADE („Pracht und alle
  Kulissen“), die alle Welten voraussetzen. Die Schwellen sind in
  `Progress.kt` (ab Zeile 100) gespiegelt und müssen dort mitgeändert werden.

  **Bestandsschutz (entschieden):** Freischaltungen werden heute nicht
  gespeichert, sondern bei jedem Aufruf aus den Statistiken berechnet
  (`ScenePaint.isUnlocked`, auch beim Sync-Merge in `GameStore.kt:562`). Wer
  Rekord 85–99 hat, würde STADT mit dem Update verlieren. Lösung:

  1. **Besitz-Menge statt nur Regel.** Neue gespeicherte Menge „Welten im
     Besitz“ in `GameStore`, gespeichert über die **Namen** (wie
     `TwistLessons`, nicht über Ordinale). Offen ist eine Welt, wenn sie in
     der Menge steht **oder** die neue Regel erfüllt ist. Sobald die Regel
     greift, kommt die Welt dauerhaft in die Menge.
  2. **Einmalige Übernahme beim ersten Start nach dem Update:** Mit den
     **alten** Schwellen (eine eingefrorene Funktion `legacyUnlocked`, nur
     für diesen Schritt) alle Welten in die Menge schreiben, die der Spieler
     heute schon hat. Ein Versionsmerker verhindert eine Wiederholung.
  3. **Sync:** Die Menge wandert in `SyncState` mit und wird als
     Vereinigung zusammengeführt, passend zu den übrigen Regeln dort
     (`maxOf` für Rekord und Läufe, `SyncState.kt:96–130`). So verliert
     auch die Uhr oder ein zweites Gerät nichts.
  4. **Abhängige Regeln** (WELTRAUM „alle anderen“, Rahmen KASKADE „alle
     Kulissen“) fragen die Besitz-Menge statt der Regel.
  5. **Tests:** Rekord 90 vor dem Update behält STADT; frischer Spieler mit
     Rekord 90 bekommt sie nicht; Merge zweier Geräte vereinigt; die gewählte
     Welt fällt nicht auf WIESE zurück.

  Dasselbe Muster lässt sich später für Skins, Töne und Rahmen nutzen, falls
  deren Schwellen je geändert werden. Beim Update können mehrere Welten auf
  einmal aufgehen, dann gesammelt feiern.

### 7.3 Schnell

- **Umlaute:** `bytesized_regular.ttf` enthält Ä, Ö, Ü, ß. 38 Zeilen in
  `values-de/strings.xml` schreiben trotzdem UE/AE/OE (MENUE, GRUENEN,
  WAEHLEN, SCHLIESSEN).
- **„?“ im Game-Over** entfernen, sobald Todesursache und Erklärung beim
  ersten Fallen-Tod da sind. Hilfe bleibt in den Einstellungen.
- **Vorlesen:** `role = Role.Button` für `PixelButton` und Taster, „?“ als
  „Hilfe“ beschriften.
- **MENÜ 6 dp neben der Mitte**, wenn TEILEN fehlt: Der Abstandhalter in der
  Knopfzeile bleibt stehen.

### 7.4 Reihenfolge

1. Zurück-Geste abfangen.
2. Game-Over: feste Leiste unten mit 0,8 s Sperre.
3. Umlaute und SAMMLUNG als Name.
4. Sammlung umbauen (Reiter, Raster, Schaufenster, Fortschritt, NEU).
   Die Welten-Leiter lässt sich unabhängig davon umsetzen.
5. Ein Druck-Stil, X-Knopf, Schalter.
6. DAILY zurücknehmen und beim ersten Mal erklären, zusammen mit dem neuen
   Startbildschirm (Phase 4).

### 7.5 Entschieden

- TEILEN steht immer in der Leiste, nicht nur nach einem Rekord.
- 0,8 s Sperre für die Leiste; im Test mit echten Wut-Taps nachmessen.
- Keine Pause im laufenden Spiel; Zurück tut dort nichts.
## 8. Umsetzung mit parallelen Agenten

Stand: `main` 410004e (nach #67) plus #68. Eingearbeitet sind die Code-Prüfung und die Kritik am ersten Entwurf. Wo Abschnitt 3–7 abweichen, gilt dieser Abschnitt. Abschnitt 4 (Phasen) wird durch die Wellen unten ersetzt.

### 8.1 Grundregeln

1. **Branches.** Integrationsbranch `claude/feedback-ux`, abgezweigt vom aktuellen `main` (enthält diesen Plan). Jedes Paket arbeitet auf `claude/ap-XX-kurzname`, abgezweigt vom aktuellen `claude/feedback-ux`, in einem eigenen Worktree, und wird dorthin zurückgemergt. `main` bekommt nur den fertigen Stand nach Welle 3, weil jeder Push auf `main` ein Prerelease erzeugt. `claude/**` löst `build-apk.yml` und `build-ios.yml` aus.
2. **Worktree einrichten:** `git worktree add ../dottie-ap-XX -b claude/ap-XX-name claude/feedback-ux && cp local.properties ../dottie-ap-XX/`. Gradle immer mit `--offline --max-workers=2 -Dorg.gradle.jvmargs=-Xmx1536m`. Die Maschine hat 4 Kerne und 15 GB, deshalb höchstens 2 Gradle-Läufe gleichzeitig.
3. **Feste Zuständigkeit.** Ein Paket ändert nur die Dateien und Symbole, die ihm in 8.2 und 8.4 gehören. In geteilten Dateien schreibt es nur zwischen seinen Ankern. Braucht es eine fremde Stelle, hört es auf und schreibt den Wunsch in die Übergabe.
4. **Spielregeln** ändern nur AP-21 (Startregel, Nebel) und AP-13 (Welten-Schwellen). Alles andere ist Darstellung oder API ohne Wirkung.
5. **Golden Vectors** (`parity/golden-vectors.txt`) erzeugen nur AP-02, AP-13, AP-21 und AP-31 neu, immer mit `./gradlew --offline :core:jvmTest -Dparity.update=true`. Nie von Hand zusammenführen. Nach jedem Merge dieser Pakete auf `claude/feedback-ux` einmal neu erzeugen. Der Diff muss dann leer sein.
6. **Objective-C-Export:** Neue Typen in `:core` stehen auf oberster Ebene. Öffentliche Funktionen haben keine Standardparameter, es gibt kein nullbares `Long`, `List<Long>` statt `LongArray` (ARCHITEKTUR.md:83-100).
7. **Kein Zufall in der Darstellung:** Nebel, Wolkenform und Lauflicht ziehen keine Zufallszahlen, weder aus dem Engine-`random` noch aus `Random.Default`.
8. **`Twist.GHOST` behält den Namen.** Nur die Texte sagen NEBEL.
9. **Umlaute:** Neue deutsche Texte werden mit Ä/Ö/Ü geschrieben, SS bleibt (SCHLIESSEN). Schlüsselnamen (`scene_wueste`, `skin_chamaeleon` …) bleiben unverändert.
10. **Vor dem Merge grün:** die Prüfbefehle des Pakets, dazu `./gradlew --offline :core:jvmTest testDebugUnitTest :ui:jvmTest` und `build-apk.yml` auf dem Paket-Branch. `build-ios.yml` auf dem Paket-Branch ist Pflicht für AP-01, AP-02, AP-03, AP-12, AP-21 und AP-31, denn nur dort werden die iosMain-actuals und der ObjC-Export kompiliert.
11. **Übergabe** je Paket: geänderte Dateien, jedes Akzeptanzkriterium mit Beleg (Test oder Screenshot-Pfad), offene Punkte und Wünsche an fremde Dateien.

### 8.2 Arbeitspakete

| ID | Ziel (Plan) | Besitz: Dateien / Symbole | Hängt ab von | Welle |
|---|---|---|---|---|
| AP-01 | Umbau ohne Verhaltensänderung, Texte 7.3, :ui-Verträge als Stubs | Aufteilung von `GameOverlays.kt` (8.4), alle Anker, bestehende Zeilen in `values{,-de}/strings.xml` (Umlaute, KULISSE→WELT, SCENE→WORLD, neu `collection`), `wear/.../values-de/strings.xml` (`back`), `GameScreen` (Parameter `game`, Zustand `collectionHasNew`), `WorldRenderer.ringGeometry`, neu `ui/.../BackAction.kt` (Stub), Stub-Signaturen `TwistLessons.next(…, diedInTrap)`, `GameStore.twistToExplain(…, diedInTrap)`, `ReadyOverlay(…, collectionHasNew, banner)`, `ScreenshotRenderer` (Seed, feste Uhr, Größe/Dichte als Parameter), neu `TwistShots.kt` | – | 0 |
| AP-02 | Engine-API ohne Regeländerung | `TimingGame.kt` (`start()`, `GameEventNotYet`, `READY_ZONE_CENTER`, `DeathCause`, `lastDeathCause`, Puls in DYING einfrieren, `zoneAge`, `FOG_*`, `fogStart/fogEnd/isInFog`, `blindBonus`, `lastHitBlind`), neu `core/.../TrapPaint.kt`, `Progress.kt` (Tabellen `internal`), `ParityBot.kt`, `app/src/test/.../TimingGameTest.kt`, `DailyChallengeTest.kt`, `core/.../ZoneWindowTest.kt`, neu `core/src/jvmTest/.../DeathCauseTest.kt`, `TrapPaintTest.kt` | – | 0 |
| AP-03 | Bausteine Bedienung 7.1/7.2 | neu `components/PressFeedback.kt`, `OverlayCloseButton.kt`, `PixelSwitch.kt`, `platform/PlatformBackHandler{,.android,.ios,.jvm}.kt`, `PixelButton.kt`, `GameSounds.kt` (`GameFeedback.tap()`), `app/.../GameHaptics.kt`, `ui/src/iosMain/.../IosFeedback.kt`, `ui/build.gradle.kts` | Merge nach AP-01 | 0 |
| AP-11 | Todesursache 3.2 inkl. BOOM! | neu `screens/DeathCauseLabel.kt`, `WorldRenderer.drawTimingDot` (Rahmen), Anker `todesursache` in GameScreen/GameStore/FxState/strings/TwistShots, bestehende GameScreen-Zeile `twistToExplain = …` im Died-Zweig | AP-01, AP-02 | 1a |
| AP-12 | Bomben 3.4 (ohne Himmel) | neu `world/MineField.kt`, `WorldRenderer.drawTrack`, Anker `bomben` in `drawTimingWorld`/FxState/strings/TwistShots, `Palette.kt` (FakeZone*), `TwistLessons.next` (Rumpf), `TwistLessonShield.kt`, `HelpOverlay.kt` (`StopHelpContent`, `TwistHelpRow`), neu `components/MineIcon.kt`, `TwistLessonTest`, strings `twist_fake_title`, `twist_fake_text`, `twist_learned_fake` | AP-01, AP-02 | 1a |
| AP-13 | Himmel ohne Lila, Welten-Leiter 7.2 mit Bestandsschutz | `ScenePaint.kt` (inkl. `legacyUnlocked`), `SkinStats` (Feld `ownedScenes`), `SyncState.kt` (Feld + Vereinigung in `mergedWith`), Anker `welten` in `GameStore` (Besitz-Menge, einmalige Übernahme), `CardStyle.isUnlocked` (KASKADE über Besitz), `sync/.../StatsSync.kt` (Feld übertragen), `wear/.../WearSyncMerge.kt` und `WearDotSkin.kt` (nur Besitz-Menge; mit AP-24 abstimmen, AP-13 wird vorher gemergt), `SkinPaint.SKY_STAGES`, `store/skin_paint.py`, `Progress.SCENE_THRESHOLDS`, `ScenePaintTest`, `ProgressTest`, `CardStyleTest`, `DotSkinTest`, `GameStoreSyncTest`, `WearSceneSyncTest` (Kommentare), `ParityVectors.UNLOCK_PROBES`, Vektoren, strings `scene_hint_*` | AP-02 (`TrapPaint`-Farben) | 1a |
| AP-14 | Bedienung 7.1/7.2/7.3 (ohne Sammlung, DAILY, „?“) | `GameOverOverlay.kt` (Leiste), `ReadyOverlay.kt` (`TasterBar`, `Taster`), `SettingsOverlay.kt`, X-Knopf in `HelpOverlay` (nur das Composable `HelpOverlay`) und `StatsOverlay` (nur `StatsOverlay`), `BackAction.kt` (Rumpf, Test), bestehende GameScreen-Zeilen `onMenu`, Aufrufe Settings/Help/Stats/GameOver, Provider `LocalPressFeedback`, `ScreenshotRenderer` (MENÜ-Tap), strings-Anker `bedienung` | AP-01, AP-03 | 1a |
| AP-15 | Sammlung 7.1 | `CollectionOverlay.kt`, neu `core/.../CollectionProgress.kt` und Test, neu `ui/data/CollectionSeen.kt` und `CollectionSeenTest`, Anker `sammlung` in GameStore/GameScreen/ReadyOverlay (Banner-Slot)/strings, bestehender `SkinOverlay(…)`-Block in GameScreen inkl. `showSkins = false` und `onSkins`, `StatsOverlay.goalAxisText` (`internal`) | AP-13, AP-14 gemergt | 1b |
| AP-21 | Kernregeln: Startregel, Nebel, BLIND | `TimingGame.tap`/`isDotVisible`/Konstanten, `ParityBot.playReadyHit`, `ParityVectors.constants`/`traces`, Vektoren, `TimingGameTest`, neu `core/.../ReadyTapTest.kt`, `RendererSourceTest` (Nebelfall), `ScreenshotRenderer` (Start-Tap) | Welle 1 gemergt | 2a |
| AP-22 | Startbildschirm 3.1 + DAILY 7.2 | neu `world/StartCoach.kt`, `WorldRenderer.drawTrack` (Leuchten), `ReadyOverlay.kt` (Hinweis, Zielzeile, DAILY), Zielzeile in `GameOverOverlay.kt`, neu `screens/DailyIntroCard.kt`, `BackAction.kt` (DailyIntro), Anker `start` in GameScreen/GameStore/FxState/strings, bestehende GameScreen-Zeilen `onPress`, `onDaily`, `ScreenshotRenderer` (neue Shots), neu `DailyIntroTest`, `FxStateTest`, string `ready_hint` | AP-21 gemergt | 2b |
| AP-23 | Nebel-Darstellung 3.3 | neu `world/FogRenderer.kt`, Anker `nebel` in `drawTimingWorld`/FxState/GameScreen/strings/TwistShots, strings `twist_ghost_title`, `twist_ghost_text`, `twist_learned_ghost` | AP-21 gemergt | 2b |
| AP-24 | Wear | `WearGameController.kt`, `WearGameScreen.kt`, `WearRenderer.kt`, `wear/.../MainActivity.kt`, `wear/src/main/res/values{,-de}/strings.xml` | AP-21 gemergt | 2b |
| AP-31 | Integration, Store, Release | „?“ entfernen (`HelpCornerButton`, `onHelp`, GameScreen:704), `ScreenshotRenderer` Schritt 04, `README.md`, `parity/README.md`, `PUBLISHING.md`, `store/generate_screenshots.py`, `store/generate_wear_screenshots.py`, versionCode/versionName in `app/` und `wear/`, `docs/plan-feedback-ux.md`, `.github/workflows/build-apk.yml` (`:ui:jvmTest`) | alle | 3 |

Phase 3 (GEIST weich ausblenden) entfällt, weil der Nebel im selben Release kommt. Wird zwischen Welle 1 und 2 doch veröffentlicht, dann als `dotAlpha()` in `:core`.

### 8.3 API-Verträge (in Welle 0 angelegt)

**`:core`, AP-02**, alles auf oberster Ebene bzw. als Member von `TimingGame`:
- `fun start(): GameEvent?` (Member, nutzt das private `spawnZone()`): das heutige READY-Verhalten, legt `GameEventStarted` wie `tap()` in `pendingEvents`.
- `data object GameEventNotYet : GameEvent`. `READY_ZONE_CENTER = 1.8f` ersetzt die `1.8f` an TimingGame.kt:93 und :292.
- `enum class DeathCause { NONE, EARLY, LATE, MISSED, TRAP }` und `val lastDeathCause`. Gesetzt wird der Wert in `tap()` und im Überfahren-Zweig, bevor `die()` `elapsed` zurücksetzt. Die Fallenprüfung zuerst.
- In `die()` wird die Pulsbreite eingefroren, `effectiveZoneHalf()` ist vor und nach dem tödlichen Tap gleich.
- `val zoneAge: Float`: in RUNNING `min(elapsed, timeSinceHit)`, in DYING/OVER der Wert beim Tod.
- `FOG_SECONDS = 0.12f`, `FOG_END_SHARE = 0.5f`, `fun fogStart(): Float`, `fun fogEnd(): Float` (relativ zur Zone, gerechnet mit `zoneHalfWidth`), `val isInFog`. `var blindBonus = false`, `val lastHitBlind` (bis AP-21 immer `false`). Noch ohne Wirkung auf `isDotVisible`.
- `object TrapPaint`: `MINE` (7×7), Farben Kugel #1E1A22, Rand #F4E9EC, Glanz #FFFFFF, Rot #E53935. `LIGHT_STEP_SECONDS = 0.09f`, `fun count(zoneHalfWidth: Float, cell: Float): Int`, `fun redMask(count: Int, step: Int): List<Boolean>` (wB = ceil(n/2), Periode n + wB), `fun direction(fakeZoneCenter: Float): Int = if (((bits ushr 7) and 1) == 0) 1 else -1`.

**`:ui`, AP-01 (Stubs) und AP-03 (Bausteine):**
- `ringGeometry(size): RingGeometry(cx, cy, radius)` (cy = 0,44h, R = min(0,36w; 0,28h)).
- `TwistLessons.next(unlocked, explained, diedInTrap: Boolean = false)`, `GameStore.twistToExplain(unlocked, diedInTrap: Boolean = false)`. `diedInTrap` bleibt bis AP-12 ohne Wirkung.
- `ReadyOverlay(…, collectionHasNew: Boolean = false, banner: @Composable () -> Unit = {})`.
- `backAction(showHelp, showSettings, showStats, showSkins, showDailyIntro, phase): BackAction`, bis AP-14 nur ein Stub.
- AP-03: `LocalPressFeedback`, `Modifier.pixelPressable(enabled, role, onClick)`, `OverlayCloseButton(onClose)`, `PixelSwitchRow(label, checked, icon, onCheckedChange)`, `@Composable expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)`, `GameFeedback.tap()` (Default leer).

### 8.4 Konflikt-Hotspots

| Datei | Regel |
|---|---|
| `GameOverlays.kt` | AP-01 teilt die Datei im selben Paket auf, danach gibt es sie nicht mehr: `ReadyOverlay.kt` (ReadyOverlay, TasterBar, Taster, StreakBadge), `GameOverOverlay.kt` (GameOverOverlay, HelpCornerButton, rememberTaunter), `TwistLessonShield.kt`, `HelpOverlay.kt` (HelpOverlay, StopHelpContent, HelpHeading, HelpLine, TwistHelpRow), `SettingsOverlay.kt` (inkl. streakLabel), `CollectionOverlay.kt` (SkinOverlay unverändert, drawScenePreview, drawSoundPreview, drawCardFramePreview), `Panels.kt` (ScoreShadowStyle, ScoreHud, PixelPanel, PANEL_FRAME_CELL, medalColors, MedalBadge, SkinFamilyHeading). Was mehrere Dateien brauchen, wird `internal`. `GoalBar`/`goalAxisText` bleiben in `StatsOverlay.kt`. |
| `GameScreen.kt` | Anker-Paare `// == AP-xx name ==` / `// == /AP-xx ==` mit mindestens einer fremden Zeile dazwischen, je in Event-Loop (≈350-470), Eingabe (≈536-560) und Overlay-Aufbau (≈571-806). Bestehende Zeilen gehören genau einem Paket: Welle 1: AP-11 den Died-Zweig (`twistToExplain`), AP-14 `onMenu`→`backToMenu()` und die Aufrufe Settings/Help/Stats/GameOver, AP-15 den `SkinOverlay`-Block mit `onSkins`. Welle 2: AP-22 `onPress` und `onDaily`. Der Zustand `collectionHasNew` liegt schon seit AP-01 vor und wird an `ReadyOverlay` übergeben. |
| `strings.xml` (en/de) | Bestehende Zeilen ändert in Welle 0 nur AP-01. Danach gehören sie namentlich: `scene_hint_*` → AP-13, `twist_fake_title/_text`, `twist_learned_fake` → AP-12, `twist_ghost_title/_text`, `twist_learned_ghost` → AP-23, `ready_hint` → AP-22. Neue Schlüssel nur unter dem eigenen Anker-Paar mit Präfix: `death_*`, `bomb_*`, `ctl_*`, `collection_*`/`tab_*`, `start_*`/`daily_intro_*`, `fog_*`. |
| `GameStore.kt`, `FxState.kt` (Felder und `reset()`), `FxStateTest`, `drawTimingWorld`, `TwistShots.kt`, `ReadyOverlay.kt` (Banner) | Anker-Paare je Paket von AP-01. Keys im Companion stehen ebenfalls unter dem Anker. |
| `WorldRenderer.kt` | `drawTimingDot`: nur AP-11. `drawTrack`: Welle 1 nur AP-12, Welle 2 nur AP-22. Neue Zeichnungen in eigene Dateien. `fakeZoneHalf(` und `perfectHalf(` bleiben in `WorldRenderer.kt` (RendererSourceTest). |
| `HelpOverlay.kt`, `StatsOverlay.kt` | Nach Symbol getrennt: AP-12 `StopHelpContent`/`TwistHelpRow`, AP-14 nur das Composable `HelpOverlay` (X). AP-14 `StatsOverlay` (X), AP-15 `goalAxisText`. Merge AP-12 und AP-14 vor AP-15. |
| `TimingGame.kt`, `ParityVectors.kt`, `golden-vectors.txt`, `TimingGameTest.kt` | Welle 0 AP-02, Welle 1 AP-13 (nur `UNLOCK_PROBES` und Vektoren), Welle 2a AP-21. Nie zwei Pakete gleichzeitig. |
| `ScreenshotRenderer.kt` | Nacheinander: AP-01, AP-14, AP-21, AP-22, AP-31. Twist-Bilder nur in `TwistShots.kt`. |
| `BackAction.kt` | AP-01 (Stub), AP-14 (Logik), AP-22 (DailyIntro). |
| `WearGameController.kt`, `WearRenderer.kt` | Nur AP-24. |
| `GameStore`-Migrationen | Zwei, nacheinander: AP-13 (Besitz-Menge der Welten, einmalige Übernahme nach alten Schwellen, Versionsmerker) vor AP-15 (CollectionSeen). AP-15 liest die Besitz-Menge von AP-13. |

**Merge-Reihenfolge auf `claude/feedback-ux`:**
- Welle 0: AP-01 → AP-02 → AP-03.
- Welle 1a: AP-13 (danach Vektoren) → AP-12 → AP-11 → AP-14. Welle 1b: AP-15.
- Welle 2a: AP-21 (danach Vektoren). Welle 2b: AP-22 → AP-23 → AP-24. Die Pakete aus 2b dürfen gegen die API aus AP-02 früher beginnen, rebasen aber vor der Abnahme auf AP-21.
- Welle 3: AP-31, danach `claude/feedback-ux` → `main`.

### 8.5 Pakete

Screenshots immer mit: `SHOTS_DIR=$PWD/build/shots ./gradlew --offline :ui:jvmTest --tests '*ScreenshotRenderer*' --tests '*TwistShots*' --rerun`

#### Welle 0

**AP-01 Umbau und Texte**
- Aufgabe:
  - `GameOverlays.kt` aufteilen und alle Anker setzen (8.4).
  - 34 Umlaut-Zeilen in values-de: 16, 57, 64, 84, 90, 92, 96, 99, 101, 102, 108-110, 128, 140, 174, 179, 181, 182, 184, 187, 195, 202, 205, 210, 215, 221, 223, 229, 237, 252, 256-258. NEUE/NEUER bleiben. In wear values-de nur `back`.
  - KULISSE(N)→WELT(EN) und SCENE(S)→WORLD(S) in `scenes`, `goal_axis_scenes`, `goal_next_scene`, `card_scene`, `frame_hint_kaskade`. Neuer Schlüssel `collection` (SAMMLUNG/COLLECTION) für Taster und Titel. `skins` bleibt für die Statistik.
  - Stubs aus 8.3. GameScreen-Parameter `game: TimingGame = remember { TimingGame() }`.
  - ScreenshotRenderer: geseedetes Spiel, feste Uhr, Breite, Höhe und Dichte als Parameter.
  - `TwistShots.kt` zeichnet `drawTimingWorld` mit geseedetem `TimingGame`, `twistOverride` und eigener Bot-Schleife. ParityBot liegt in core/jvmTest und ist von hier aus nicht sichtbar. Ohne `SHOTS_DIR` tut der Test nichts.
- Akzeptanz:
  - `git diff -M --stat` zeigt Verschiebungen, Texte und Stubs.
  - Alle Tests grün.
  - `grep -nE '<string[^>]*>[^<]*(AE|OE|UE)[^<]*<' ui/src/commonMain/composeResources/values-de/strings.xml | grep -vE 'NEUER? '` ist leer.
  - Zwei ScreenshotRenderer-Läufe mit demselben Seed geben dieselben Bilder.
  - `:ui:jvmTest` ohne SHOTS_DIR dauert nicht merklich länger.
- Prüfen: `./gradlew --offline :ui:jvmTest :ui:testDebugUnitTest :ui:compileDebugKotlinAndroid :app:assembleDebug :wear:assembleDebug`, dazu `build-ios.yml`.

**AP-02 Engine-API**
- Aufgabe:
  - Den `:core`-Teil aus 8.3 umsetzen.
  - Alle READY-Starts auf `start()` umstellen: ParityBot:77/:95, TimingGameTest (33), DailyChallengeTest:25-39, ZoneWindowTest:42/:117.
  - `DeathCauseTest` mit `twistOverride`:
    - sofortiger zweiter Tap → EARLY
    - Tap bei rel > effHalf + 0,07·speed → LATE
    - nie tappen → MISSED
    - Tap in die Falle mit FAKE, auch mit PULSE → TRAP
    - `effectiveZoneHalf()` ist vor und nach dem Tod gleich
  - `TrapPaintTest`:
    - Folge für n = 6 wie in 3.4 (9 Schritte)
    - Periode n + ceil(n/2) für n = 1..10
    - `direction` ∈ {−1, 1}
    - Raster 7×7
  - Nebel-Getter prüfen:
    - Dauer 0,12 + 0,5h/speed
    - PERFEKT-Kern nie im Nebel
    - Spawn nie im Nebel
- Akzeptanz: `-Dparity.update=true` → `git diff --exit-code parity/`. Alle bestehenden Tests grün.
- Prüfen: `./gradlew --offline :core:jvmTest :app:testDebugUnitTest`, `./gradlew --offline :core:jvmTest -Dparity.update=true && git diff --exit-code parity/`, dazu `build-ios.yml`.

**AP-03 Bausteine**
- Aufgabe:
  - Bausteine aus 8.3.
  - `PixelButton`: 4 dp Schatten, sinkt beim Drücken ein, `indication = null`, `Role.Button`, optionale `contentDescription`, Haptik über `LocalPressFeedback`. `PixelIconButton` bekommt ebenfalls Haptik. `drawPixelIcon` wird `internal`.
  - `GameHaptics.tap()` über die Systemeinstellung für Berührungs-Feedback (`View.performHapticFeedback`/`LocalHapticFeedback`), damit man es abschalten kann. `IosFeedback.tap()`: `UISelectionFeedbackGenerator`.
  - `androidMain.dependencies { implementation(libs.androidx.activity.compose) }`, drei actuals.
- Akzeptanz: kompiliert für android, jvm und iOS. `grep -rn 'ripple\|indication' ui/src/commonMain` findet nur `indication = null`. Die Layout-Verschiebung durch den Schatten ist im Screenshot bewusst.
- Prüfen: `./gradlew --offline :ui:compileKotlinJvm :ui:compileDebugKotlinAndroid :app:compileDebugKotlin :ui:jvmTest`, dazu `build-ios.yml`.

#### Welle 1a (parallel)

**AP-11 Todesursache**
- Aufgabe:
  - `DeathCauseLabel` am Ring zeigt ZU FRÜH, ZU SPÄT, VERPASST oder BOOM! während DYING, also im Freeze und im Sturz (etwa 1,5 s), danach klein im Game-Over unter dem Titel. Quelle ist nur `game.lastDeathCause`.
  - Weißer Rahmen um den Punkt, solange `0 ≤ fx.deathTime < DEATH_FREEZE_SECONDS`.
  - Beim ersten Bomben-Tod zusätzlich „BOMBE = NIE TIPPEN“. Merker `bomb_lesson_seen`, lokal, nicht im SyncState.
  - Im Died-Zweig `twistToExplain(unlocked, diedInTrap = cause == TRAP)`.
- Akzeptanz:
  - Test in commonTest: Die Label-Rect liegt außerhalb des Rings (`ringGeometry`) bei 1080×2400 und 720×1280.
  - Test: Der Merker verhindert die Lektion beim zweiten Mal.
  - Screenshots aller vier Tode, TRAP auch unter PULSE.
- Prüfen: `./gradlew --offline :ui:jvmTest :ui:testDebugUnitTest`.

**AP-12 Bomben**
- Aufgabe:
  - `MineField.kt`: `drawMine` mit ganzzahlig gerundetem Pixelmaß. `drawTrapBoom` mit 12 Funken über 0,45 s, ohne eigenen Blitz, und nur bei `lastDeathCause == TRAP`.
  - Anzahl und Maske der Minen aus `TrapPaint.count(zoneHalfWidth, …)`, gezeichnet in der Breite von `fakeZoneHalf()`. Lauflicht-Uhr ist `game.zoneAge`, die Richtung kommt aus `TrapPaint.direction`.
  - `drawTrack` zeichnet für `inFake && !inZone` eine Mine.
  - Lila raus aus `Palette`, `TwistLessonShield`, `StopHelpContent` (0xFFB44FD8) und `TwistHelpRow`. Neu ist `MineIcon`.
  - `TwistLessons.next` lässt FAKE bei `diedInTrap` vordrängeln.
- Akzeptanz:
  - `grep -rn 'B44FD8\|8A2FB0\|FakeZoneColor' ui/src core/src/commonMain` ist leer.
  - `TwistLessonTest` deckt ab: Fallen-Tod mit unerklärtem PULSE → FAKE, ohne Fallen-Tod bleibt die Reihenfolge, FAKE schon erklärt → nächster Twist, Neustart-Persistenz.
  - RendererSourceTest ist grün.
- Prüfen: `./gradlew --offline :core:jvmTest --tests '*RendererSourceTest' :ui:jvmTest --tests '*TwistLessonTest' --tests '*TextsTest'`. TwistShots: Falle in WIESE, STADT und einer Nacht-Welt, drei Lauflicht-Stände, Explosion.

**AP-13 Himmel und Welten-Leiter**
- Aufgabe:
  - Himmelsstufe 2: WIESE, `SKY_STAGES[2]` und `skin_paint.py` → #3F6FC4, STADT-2 → #4A6AA8, WELTRAUM-2 → #243A8C.
  - Falle-Signale aus `ScenePaintTest` entfernen (Z. 20, 77-80, 95-126). `MIN_SKY_SIGNAL_DISTANCE = 93f` gilt nur für Zonensignale, KDoc anpassen.
  - Neuer Test: Mine vor jedem Himmel mit `max(abstand(Kugel), abstand(Rand)) ≥ 150`, Farben aus `TrapPaint`.
  - Welten-Leiter nach 8.6 #11: WÜSTE `runCount ≥ 100`, MEER `totalScore ≥ 2_500`, BERG `bestDailyStreak ≥ 1`, STADT `bestScore ≥ 100`, WELTRAUM alle anderen. In `ScenePaint.isUnlocked`, `Progress.SCENE_THRESHOLDS`, den Tests, `UNLOCK_PROBES` (Kanten 99/100, 2_499/2_500, 0/1, 99/100) und `scene_hint_*` (inkl. `scene_hint_weltraum` „ALLE ANDEREN WELTEN“).
  - Bestandsschutz (Plan 7.1): `SkinStats.ownedScenes` (Namen, nicht Ordinale). Offen = in der Menge **oder** Regel erfüllt; greift die Regel, kommt die Welt dauerhaft in die Menge. Einmalige Übernahme beim ersten Start mit `ScenePaint.legacyUnlocked` (alte Schwellen 500/10.000/30/85), geschützt durch einen Versionsmerker. `SyncState` trägt die Menge und vereinigt sie in `mergedWith`. WELTRAUM und Rahmen KASKADE fragen die Besitz-Menge. Wear übernimmt die Menge über den Sync.
- Akzeptanz:
  - Test Bestandsschutz: Rekord 90 vor dem Update behält STADT und die gewählte STADT bleibt gewählt; frischer Spieler mit Rekord 90 bekommt sie nicht; Merge zweier Geräte vereinigt die Mengen; die Übernahme läuft genau einmal.
  - WÜSTE fällt mit TIGER (100 Läufe), MEER mit BASKETBALL (2.500 Punkte) zusammen. Das ist gewollt, beides wird gemeinsam gefeiert.
  - Der Vektor-Diff betrifft nur `sky.stages`, `scene.sky.*`, `scene.skyForScore.*`, `scene.unlocked.*`, `progress.*`, die Proben und die neuen Sync-Felder.
- Prüfen: `./gradlew --offline :core:jvmTest :app:testDebugUnitTest :ui:jvmTest --tests '*GameStoreSyncTest' :wear:testDebugUnitTest`, `python3 store/check_skin_paint.py`, Vektoren neu erzeugen, `git diff parity/ | grep '^[-+]' | cut -d' ' -f1 | sort | uniq -c`. TwistShots: Himmel bei Score 10 in allen 6 Welten.

**AP-14 Bedienung**
- Aufgabe:
  - Game-Over: Inhalt oben (`TopCenter`), unten eine feste Leiste mit MENÜ und TEILEN. Solange `game.elapsed < GAME_OVER_BAR_LOCK_SECONDS = 0.8f` in OVER, ist sie blass und schluckt Taps selbst (`pointerInput` mit consume).
  - `backAction` in dieser Reihenfolge: Hilfe > Einstellungen > Statistik > Sammlung > OVER→`backToMenu()` > RUNNING/DYING verbrauchen > READY nicht behandeln. Zurück beachtet die Sperre nicht.
  - Taster über `pixelPressable`.
  - Einstellungen: `PixelSwitchRow` für TON und ERINNERUNG, Links mindestens 48 dp mit `Role.Button`.
  - X-Knopf in Hilfe, Einstellungen und Statistik.
  - `LocalPressFeedback provides { feedback.tap() }` in GameScreen.
  - ScreenshotRenderer: MENÜ-Tap auf die Leiste.
- Akzeptanz:
  - `BackActionTest` deckt jede Stufe ab. Test: `GAME_OVER_BAR_LOCK_SECONDS > RESTART_LOCK_SECONDS`.
  - jvmTest mit `ImageComposeScene` wie im ScreenshotRenderer: Tap auf die Leiste bei t = 0,3 s lässt `phase == OVER` und kein Menü. Bei t = 1,0 s öffnet MENÜ.
  - Semantics-Test: Alle Knöpfe der Leiste und der Einstellungen haben `Role.Button` oder `Role.Switch`.
- Prüfen: `./gradlew --offline :ui:jvmTest :ui:testDebugUnitTest :app:assembleDebug`. Screenshots: Game-Over bei 0,3 s und 1,0 s, Einstellungen, Hilfe mit X.

#### Welle 1b

**AP-15 Sammlung**
- Aufgabe:
  - `CollectionOverlay` mit Reitern VOGEL (x/46), WELT, TON und RAHMEN, jeweils mit Zähler.
  - Schaufenster: Welt und Vogel aus `drawScenery`/`drawGroundStrip`/`drawTimingDot`. Hörprobe über den neuen Callback `onPreviewSound`, auch für gesperrte Töne. „HEUTE PER SPOT TESTEN“ als Knopf, nicht für Gönner.
  - Kacheln im 4er-Raster. Gesperrte Kacheln blass mit `GoalBar`, dazu NEU-Markierung und roter Punkt.
  - Eine Auswahl schließt das Overlay nicht.
  - `CollectionProgress` pro ID. `FrameProgress`: FRAME_STEPS 10/20/33, KASKADE x/6, PERLENKRANZ x/3, KRONE.
  - `CollectionSeen` mit Schlüsseln `SKIN:`/`SCENE:`/`SOUND:`/`FRAME:`, lokal. Migration beim ersten Lesen: Alles Offene gilt als gesehen, außer Welten, die nach `ScenePaint.legacyUnlocked` zu waren (von AP-13), und außer KASKADE und höher, falls WELTRAUM zu war.
  - Aktualisierung per `LaunchedEffect(phase, syncRevision)`.
  - Banner „NEUE WELTEN: …“ über den Slot in `ReadyOverlay`, Tippen öffnet die Sammlung.
- Akzeptanz:
  - Neuinstallation: nichts ist NEU.
  - Bestand mit 130 Läufen und Rekord 45: WÜSTE ist NEU, alte Skins nicht. Saison- und Gönner-Skins bekommen keine NEU-Markierung.
  - Jede Kachel ist genau an ihrer Schwelle voll.
  - Downgrade-Toleranz beim Dekodieren.
- Prüfen: `./gradlew --offline :core:jvmTest --tests '*CollectionProgress*' :ui:jvmTest --tests '*CollectionSeenTest' --tests '*TextsTest'`. Screenshots: vier Reiter, gesperrte Kachel ausgewählt, NEU-Markierung, Banner.

#### Welle 2a

**AP-21 Kernregeln**
- Aufgabe:
  - `tap()` in READY bei `isInZone`: RUNNING, `elapsed = 0`, zuerst `GameEventStarted` in `pendingEvents`, dann `registerHit(perfect)`, Rückgabe `Hit` oder `PerfectHit`. Sonst `GameEventNotYet`, ohne Zufallszahl, Phase bleibt READY. Keine Spät-Gnade in READY. Der Sofort-Neustart aus OVER bleibt.
  - `isDotVisible = phase != RUNNING || GHOST !in activeTwists || !isInFog`. `GHOST_BLINK_SPEED` und `GHOST_VISIBLE_SHARE` entfallen.
  - BLIND: `inFog` vor `registerHit` festhalten, `lastHitBlind` setzen, +1 nur bei einem gültigen Treffer. `blindBonus` ist standardmäßig **an**. Ein Blindtreffer setzt die Perfekt-Serie nicht zurück.
  - `playReadyHit`, `trace.ready.*`, `const.FOG_*`, `const.READY_ZONE_CENTER`.
  - `TimingGameTest` `ghost twist blinks…` neu schreiben.
  - RendererSourceTest bekommt den Fall `nebel kommt aus der Engine`: Jede Datei unter `ui/src` und `wear/src`, die `drawFog` enthält, muss `fogStart(` enthalten und darf `FOG_SECONDS` nicht enthalten.
  - ScreenshotRenderer: Start-Tap bei `step(1.5)`.
- Akzeptanz:
  - Der Vektor-Diff hat nur hinzugefügte Zeilen, außer den entfernten `const.GHOST_*`.
  - `ReadyTapTest`: Events in der Reihenfolge [Started, Hit|PerfectHit], NotYet lässt RNG und Zone unberührt, Daily-Seed deterministisch, `blindBonus` aus → kein +1, an → +1 nur bei Treffer, Serie bleibt erhalten.
  - DYING immer sichtbar. GHOST+FAKE bleibt verboten.
- Prüfen: `./gradlew --offline :core:jvmTest :app:testDebugUnitTest`, `./gradlew --offline :core:jvmTest -Dparity.update=true && git diff parity/ | grep '^-' | grep -v '^---'` (nur `const.GHOST_*`), dazu `build-ios.yml`.

#### Welle 2b (parallel, nach dem Rebase auf AP-21)

**AP-22 Startbildschirm und DAILY**
- Aufgabe:
  - `StartCoach.kt`: Hand 16×17 in der Ringmitte, gedrückt solange `isInZone`, Echo an der Fingerspitze. Nur in READY während der Stützräder.
  - Tipp-Echos als wachsender Umriss in allen Phasen, Offset aus `onPress`.
  - Zone leuchtet in READY und RUNNING, solange `runCount < 5`.
  - Hinweis „TIPPE, WENN DER PUNKT IM GRÜNEN IST“ ohne Blinken, unter dem Ring per `ringGeometry` minus oberem Inset.
  - „NOCH NICHT“ im Ring über der Hand, 0,7 s Wackeln, `feedback.tap()`.
  - Zielzeile im Startbildschirm und im Game-Over aus, solange `runCount < 5`.
  - Beim READY-Treffer kein `sounds.start()`.
  - `prepareRun()` in READY nur, wenn `game.isInZone`.
  - DAILY sandfarben (PanelSand). Der erste Tap zeigt `DailyIntroCard` (`daily_intro_seen`, lokal). START auf der Karte schaltet scharf. Danach ist DAILY ein Umschalter (aus ↔ scharf), MENÜ schaltet ab. Scharf ist im READY markiert, gestartet wird per Tap im Grün. Zurück schließt die Karte.
- Akzeptanz:
  - Test: Tap daneben lässt Score, Phase und Zone unverändert. Erster Tap im Grün ergibt Score 1 oder 2.
  - `DailyIntroTest`: Karte genau einmal, Umschalter, nicht in `syncState()`.
  - Geometrie-Test: Die Hinweis-Rect überlappt Ring und Zone bei 360×640 dp und 411×914 dp nicht.
  - `FxStateTest` ist erweitert.
- Prüfen: `./gradlew --offline :ui:jvmTest :app:testDebugUnitTest --tests '*FxStateTest' :app:assembleDebug`. Screenshots bei 1080×2400 und 720×1280: READY mit Hand oben und gedrückt, NOCH NICHT, DAILY-Karte, DAILY scharf.

**AP-23 Nebel-Darstellung**
- Aufgabe:
  - `drawFogBank` nur in RUNNING (beim Tod verschwindet die Wolke). Grenzen aus `fogStart()`/`fogEnd()`. Am Telefon wird der Vogel an der Wolke abgeschnitten (Clip), er gleitet hinein und heraus, statt auf einen Schlag zu verschwinden. Die Engine-Regel `isDotVisible` bleibt für Uhr und Tests.
  - Wolkenpixel = Vogelpixel, Bauschradien 8-12 u, Farben #A0BEDA, #BED4EA, #D6E5F4, #FFFFFF, #F4F8FD in `FogRenderer.kt`. Seed aus `hits` und `direction`.
  - Einrollen über `zoneAge/0.18`. Wölkchen beim Wechsel von `isInFog` (FxState-Anker, eine Zeile im Loop).
  - BLIND-Pop nur bei `lastHitBlind`. Die Texte sagen NEBEL.
- Akzeptanz:
  - Test: Der Punkt ist bei fogStart, in der Mitte und bei fogEnd unsichtbar.
  - Test: Der Seed ist unter DRIFT über 60 Frames konstant.
  - RendererSourceTest ist grün.
  - TwistShots mit `blindBonus = true` zeigt den Pop.
- Prüfen: `./gradlew --offline :ui:jvmTest :core:jvmTest --tests '*RendererSourceTest'`. TwistShots: 15 Treffer, Höchsttempo, Einrollen bei 0,05 s und 0,18 s, GHOST+DRIFT.

**AP-24 Wear**
- Aufgabe:
  - `GameEventNotYet` gibt einen Haptik-Tick und „NOCH NICHT“, auch über Taste und Drehring. TIPP blinkt nicht mehr.
  - Minen aus `TrapPaint` ohne Lauflicht, `WearFakeZone*` entfällt.
  - Einfaches Nebelband über `fogStart()`/`fogEnd()`.
  - Keine Hand, keine Todesursache.
- Akzeptanz: `grep -rn 'WearFakeZone\|B44FD8' wear/src` ist leer. RendererSourceTest ist grün. Hardware-Feedback prüft der Mensch in AP-31.
- Prüfen: `./gradlew --offline :wear:testDebugUnitTest :wear:assembleDebug :core:jvmTest --tests '*RendererSourceTest'`.

#### Welle 3

**AP-31 Integration und Release**
- Aufgabe:
  - „?“ entfernen, ScreenshotRenderer Schritt 04 auf Einstellungen → HILFE umstellen.
  - `:ui:jvmTest` in `build-apk.yml` aufnehmen.
  - README (Twist-, Welten- und Fallenregel), `parity/README.md:66`, `PUBLISHING.md:150-151/:197/:226`.
  - `store/generate_screenshots.py` und `generate_wear_screenshots.py` auf Minen, Nebel, Himmel und die Startregel umstellen.
  - versionCode und versionName in `app/` und `wear/` erhöhen. Abschnitte 3–7 dieses Plans an 8 angleichen.
  - Vektoren ein letztes Mal erzeugen, vollständiger Screenshot-Satz, Review aller Diffs gegen 3.1–7.3.
- Akzeptanz:
  - Vektor-Diff leer.
  - `grep -rnE 'HelpCornerButton|B44FD8|8A2FB0|GHOST_BLINK|0xB4, 0x4F, 0xD8|violett|GEIST' --include=*.kt --include=*.xml --include=*.py --include=*.md . | grep -v '/build/\|docs/plan-'` ist leer.
  - `build-apk.yml` und `build-ios.yml` auf `claude/feedback-ux` sind grün.
- Manuell (Mensch, am Gerät): Zurück-Geste auf Android, Haptik am Telefon, NOCH NICHT über Taste und Drehring an der Uhr, 5 neue Spieler (Abschnitt 6).
- Prüfen: `./gradlew --offline :core:jvmTest testDebugUnitTest :ui:jvmTest assembleDebug`, der Screenshot-Befehl von oben.

### 8.6 Entscheidungen (Stand 25.09.2026, gelten für die Umsetzung)

| # | Frage | Standard |
|---|---|---|
| 1 | BLIND! +1 | **An** (entschieden). +1 nur bei einem gültigen Treffer im Nebel, die Perfekt-Serie bleibt erhalten. |
| 2 | Stützräder | 5 Läufe nach `GameStore.runCount`, Uhr- und Daily-Läufe zählen mit. Leuchten in READY und RUNNING, Hand nur in READY. |
| 3 | Neustart nach dem Aus | Sofort-Neustart bleibt (OVER → RUNNING). READY gibt es beim App-Start und nach MENÜ. |
| 4 | DAILY | Einmalige Karte, danach Umschalter (aus ↔ scharf). Start per Tap im Grün, MENÜ schaltet ab. |
| 5 | Spät-Gnade in READY | Nein. |
| 6 | NOCH NICHT / Tipp-Echo | Im Ring über der Hand. Echo als wachsender Umriss, immer. |
| 7 | Zielzeile | Aus während der Stützräder, im Startbildschirm und im Game-Over. |
| 8 | Todesursache | `:core` bekommt `DeathCause` und eingefrorenen Puls, die Vektoren bleiben gleich. Anzeige während DYING am Ring und klein im Game-Over, nicht auf der Uhr. |
| 9 | Bomben-Lektion | Eigener lokaler Merker für „BOMBE = NIE TIPPEN“, einmal pro Gerät. FAKE drängelt vor. Kein neuer Sound. |
| 10 | Himmel ohne Lila | WIESE-2 #3F6FC4, STADT-2 #4A6AA8, WELTRAUM-2 #243A8C, dazu WELTRAUM-3 (#6A1E6E, mit Bomben verwechselbar) in einen dunklen Blauton. Übrige Nacht-Töne (WELTRAUM-5, WÜSTE-5, BERG-3) bleiben. |
| 11 | Welten-Leiter | **Entschieden:** WÜSTE 100 Läufe, MEER 2.500 Punkte, BERG 1 Daily, STADT Rekord 100. Zusammenfall mit TIGER und BASKETBALL ist gewollt (gemeinsam feiern). STADT steigt, deshalb Bestandsschutz über `ownedScenes` mit einmaliger Übernahme und Sync-Vereinigung (AP-13). |
| 12 | Sammlung | VOGEL x/46. Auswahl lässt die Sammlung offen. Neue Welten per Banner im Startbildschirm. Gesperrte Töne probehörbar. Saison- und Gönner-Skins ohne NEU. |
| 13 | Game-Over-Leiste | 0,8 s Sperre, gesperrt schluckt sie Taps. Zurück ignoriert die Sperre. TEILEN bleibt. |
| 14 | Nebel verbergen | Telefon: Vogel wird an der Wolke abgeschnitten (gleitet hinein). Uhr: über `isDotVisible`. Die Wolke verschwindet beim Tod. |
| 15 | Wear | Neue Startregel mit Tick, Minen ohne Lauflicht, einfaches Nebelband. |
| 16 | Schreibweise | SS bleibt. Beinamen in `CardStyle` bleiben außerhalb des Umfangs. |
| 17 | Haptik-Tick | Über die Systemeinstellung für Berührungs-Feedback, damit abschaltbar. |
| 18 | Phase 3 | Entfällt. |
| 19 | Release | Telefon, Uhr und iOS am selben Tag. Die Release-Notes sagen: „Erster Tap im Grün zählt, Daily-Ergebnisse ab heute nicht mit älteren vergleichbar.“ Kein Merge nach `main` vor dem Ende von Welle 3. |


### 8.7 Korrekturen an Abschnitt 3–7 aus der Code-Prüfung

Wo diese Liste Abschnitt 3–7 widerspricht, gilt die Liste (und Abschnitt 8).

- 3.1/4: 'Verläufe ändern sich' gilt nur, wenn ParityBot weiter mit tap() startet. Mit einer neuen Member-Funktion TimingGame.start() (altes READY-Verhalten) für ParityBot.kt:77/:95, TimingGameTest (33 Starts), DailyChallengeTest:25-39 und ZoneWindowTest:42/:117 bleiben alle trace-Zeilen bytegleich. Die neue Regel bekommt eigene trace.ready.*-Zeilen. Ohne start() bleibt der Bot in READY (Punkt bei angle=0, Zone 1,8±0,4), und botPlaysWithoutDying (ParityVectorsTest.kt:49-56) schlägt fehl.
- 3.1: onDaily (GameScreen.kt:576-580) ruft prepareRun(); game.tap() bei beliebigem Winkel auf. Mit der neuen Regel kommt dann meist GameEventNotYet, und die Daily startet nicht. 7.2 DAILY muss deshalb zusammen mit 3.1 umgesetzt werden (Taster schaltet scharf, Start per Tap im Grün).
- 3.1: READY sieht man nur beim App-Start und nach MENÜ. tap() in OVER springt direkt nach RUNNING (TimingGame.kt:248-259). Das Mockup geht dagegen zurück nach READY (feedback-check.html:196, :499-503, restartToReady:true). Die Stützräder (Leuchten) müssen deshalb auch in RUNNING wirken.
- 3.1: 'NOCH NICHT' steht im Mockup nicht über dem Ring, sondern im Ring bei cy−0,45R über der Hand (feedback-check.html:769). Wackeln: dx += sin(nope·40)·4·nope px über 0,7 s (:605).
- 3.1: Das Tipp-Echo ist kein gefülltes Quadrat, sondern ein wachsender Umriss (8→38 px, Strich 3 px, 0,45 s), und es erscheint in allen Phasen (feedback-check.html:616-619).
- 3.1: Die heutige Zielzeile lautet 'NAECHSTE KULISSE: WELTRAUM — 1/5 KULISSEN' (values-de/strings.xml:256, golden-vectors.txt:914), nicht mit '·' und Umlaut. Eine zweite Zielzeile steht im GameOverOverlay (GameOverlays.kt:749-758).
- 3.1: Beim READY-Treffer muss GameEventStarted vor Hit/PerfectHit in pendingEvents stehen (tap() hängt sein Rückgabe-Event erst nach registerHit an, TimingGame.kt:261). Sonst setzt der Started-Handler (GameScreen.kt:377) maxPerfect nach dem PerfectHit auf 0 zurück.
- 3.1: Die Stützräder hängen an GameStore.runCount ('run_count_timing', GameStore.kt:42-43). Der Wert wird per Sync mit maxOf von der Uhr übernommen (GameStore.kt:500). Bestandsspieler mit runCount ≥ 5 sehen die Stützräder nie.
- 3.1: Der Start-Tap im ScreenshotRenderer bei step(1.2) liegt mit ≈1,44 rad am Zonenrand 1,4 (ScreenshotRenderer.kt:96-101). Unter der neuen Regel step(1.5) nehmen.
- 3.2: Aus dem Zustand beim GameEventDied allein lässt sich die Ursache nicht sicher ablesen. Das Event ist ein data object ohne Inhalt (TimingGame.kt:36) und kommt erst im nächsten update(). die() setzt elapsed=0 (:458-462), damit verschieben sich unter PULS effectiveZoneHalf()/fakeZoneHalf() (:161-166, :202). Ursache und Falle müssen deshalb zum Zeitpunkt des Taps festgehalten werden, am besten als lastDeathCause in :core.
- 3.2: Unter PULS pulsiert die Zone während DYING ab Phase 0 weiter. Im Freeze kann der Vogel sichtbar im Grün stehen, obwohl ZU FRÜH angezeigt wird. Abhilfe: den Puls in die() einfrieren (kleine :core-Änderung, Vektoren bleiben gleich).
- 3.3: Dass die Wolke den Punkt vollständig verdeckt und zugleich nicht über die Zone quillt, geht geometrisch nicht. Der Vogelradius beträgt 0,16 rad am Telefon und 0,197 rad an der Uhr, die Zone ist bis zu 0,30 rad schmal. Verdecken muss isDotVisible, die Wolke ist nur Bild. Die Bauschradien im Mockup (1,6-3,4 px) sind dünner als der Vogel (13 u) und müssen auf etwa 8-12 u.
- 3.3: Ein Seed aus zoneCenter lässt die Wolke unter DRIFT flackern. GHOST+DRIFT ist erlaubt. Seed stattdessen aus hits und direction.
- 3.3: Die BLIND-Logik im Mockup (feedback-check.html:494-508) ist fehlerhaft: Ein normaler Treffer tötet, und ein Fehltap im Nebel stürzt ab. Richtig: inFog vor registerHit festhalten, +1 nur bei einem Treffer. Das ist immer ein normaler Treffer (+2 insgesamt), die Perfekt-Serie wird zurückgesetzt.
- 3.3/4: Phase 5 ändert keine trace-Zeilen. Der Bot tappt bei |rel| ≤ 0,11h und damit nie im Nebel. Es ändern sich nur const.GHOST_* (golden-vectors.txt:35-36, ParityVectors.kt:230-231).
- 3.3: Twist.GHOST darf nicht umbenannt werden. Der Name wird persistiert (TwistLessons.encode), aus ihm werden die Textschlüssel gebildet (Texts.kt:98), und er steht in den Vektoren. Nur die Texte sagen NEBEL.
- 3.3: Die Wolkenlänge von ≈25 Vogelpixeln gilt nur für 1080×2400 bei etwa 15 Treffern (bei Höchsttempo 28-29 u, auf 1080×1920 ≈29 u). Die Verdeckung von 0,17 s gilt für 15 TREFFER, nicht für Score 15. Bei Perfekt-Serien sind es 0,18-0,19 s.
- 3.3: GRID=13 steht in ui/.../world/PixelShapes.kt:16, nicht in WorldRenderer. Die Uhr hat WEAR_GRID=13 mit r=0,075·minDimension (WearRenderer.kt:50, :208).
- 3.4: Die Fallenfarben sind in ui/.../world/Palette.kt:37-38 definiert. ScenePaint.kt:289 ist nur KDoc. Außerdem hart codiert in GameOverlays.kt:1060-1064 (StopHelpContent, Color(0xFFB44FD8)) und als Kopie WearFakeZoneColor in WearRenderer.kt:46-47.
- 3.4: Der Himmelston #7B6FD0 steht auch in SkinPaint.SKY_STAGES (SkinPaint.kt:168) und in store/skin_paint.py:109. DotSkinTest.kt:225-235 verlangt, dass er gleich bleibt. Weitere Lila-Himmel: WELTRAUM-2 #3E1A78 (ScenePaint.kt:530) und WELTRAUM-3 #6A1E6E.
- 3.4/4: Phase 2 ändert die Golden Vectors doch: sky.stages (Z. 82), scene.sky.* (557, 589, 597) und scene.skyForScore.WIESE (605). Neu erzeugen mit -Dparity.update=true.
- 3.4: Die Lauflicht-Richtung darf nicht vom Engine-RNG und nicht von Random.Default kommen. Sie wird aus fakeZoneCenter abgeleitet (ohne RNG).
- 3.4: Die Bombenzahl schwankt unter PULS, weil fakeZoneHalf() mitatmet. Zahl und Lauflicht-Maske aus zoneHalfWidth rechnen, gezeichnet wird in der Breite fakeZoneHalf().
- 3.4: Jeder Tod setzt schon flashAlpha=1 (GameScreen.kt:418). Keinen zweiten Blitz zeichnen.
- 3.4: ScenePaintTest.kt:20/:77-80/:95-126 prüft gegen die Fallenfarben #B44FD8/#8A2FB0. Mit Minen neu denken: Die Kugel #1E1A22 liegt vor Nachthimmeln bei nur 12,6-34,5 Abstand, als Signal taugt nur der Rand oder max(Kugel, Rand) ≥ 191. MIN_SKY_SIGNAL_DISTANCE nur mit Zonensignalen neu berechnen (Bestand 93,9).
- 4: Screenshots mit erzwungenem Twist gehen über ScreenshotRenderer nicht. GameScreen legt TimingGame intern an (GameScreen.kt:166). Nötig ist ein Parameter game an GameScreen oder ein eigener jvmTest, der drawTimingWorld aufruft (WorldRenderer.kt:46).
- 4: SHOTS_DIR ist für Gradle keine Eingabe. Ein zweiter Lauf ist UP-TO-DATE, deshalb immer mit --rerun. -Dshots.dir erreicht die Test-JVM nicht.
- 7.1 Zurück: :ui hat ein drittes Ziel jvm(). Nötig sind drei actuals (android, ios, jvm) nach dem Muster Clock.*.kt. :ui hat noch keine androidMain-Abhängigkeit auf activity-compose (nur app/build.gradle.kts:112).
- 7.1 Druck-Stil: PixelIconButton reicht von PixelButton.kt:100 bis 158 (Schatten 130-145). Die Zeilen 190-200 sind SLIDERS und in Gebrauch, ungenutzt sind nur SPEAKER_*/BELL_* (171-189, 202-207).
- 7.1 Haptik: Der Plan sagt nicht, wie sie aus :ui ausgelöst wird. Vorschlag: GameFeedback.tap() mit Default (GameSounds.kt:51-58), weitergereicht über eine CompositionLocal aus GameScreen.
- 7.1: Die Text-Links in den Einstellungen sind nur etwa 26-30 dp hoch und haben keine role (GameOverlays.kt:1173-1195). 'Überall ≥ 48 dp' stimmt nicht.
- 7.1: Eine gesperrte Leiste mit clickable(enabled=false) verbraucht den Tap nicht. Er geht an detectTapGestures (GameScreen.kt:535) und startet neu. Die Leiste muss Taps während der Sperre selbst schlucken. Die Sperre an game.elapsed messen, nicht an der Echtzeit.
- 7.3 Umlaute: Nicht 38, sondern 34 Wertzeilen in values-de sind falsch (16, 57, 64, 84, 90, 92, 96, 99, 101, 102, 108-110, 128, 140, 174, 179, 181, 182, 184, 187, 195, 202, 205, 210, 215, 221, 223, 229, 237, 252, 256-258). NEUE/NEUER (32, 40, 43, 44) sind richtig. SS in Großschrift ist korrekt. In wear values-de ist nur 1 Zeile falsch (back=ZURUECK).
- 7.3 SAMMLUNG: Res.string.skins wird auch in StatsOverlay.kt:150 benutzt. Für Taster (GameOverlays.kt:459) und Titel (1351) braucht es einen neuen Schlüssel collection.
- 7.3: Der MENÜ-Abstandhalter (GameOverlays.kt:786) ist auf Android und iOS nicht sichtbar, weil beide onShare setzen. Er fällt nur im ScreenshotRenderer auf.
- 7.1 Sammlung: Progress.goals() steht in Progress.kt:196-240 (245-254 ist nextGoals) und liefert nur offene Ziele, ohne Rahmen, Gönner und Saison außerhalb des Monats. Für die Kacheln braucht es eine eigene Abfrage pro ID und FrameProgress.
- 7.1 Sammlung: SkinOverlay reicht von GameOverlays.kt:1294 bis 1605, dazu Vorschau-Helfer bis 1773. collectableCount()=39, nicht 46. Gesperrte Skins mit adOfferReady sind heute antippbar (1531-1535).
- 7.2 Welten-Leiter: Die Szenen-Schwellen stehen in Progress.kt:153-158 (nicht ab Z. 100) und ScenePaint.kt:610-624. Die Planwerte 100 Läufe und 2.500 Punkte fallen auf TIGER (Progress.kt:125, RUN_COUNT 100) und BASKETBALL (:131, TOTAL_SCORE 2_500). Die Alternative 25/1.000/7/40 fällt auf EI, DONUT, KOI und FROST (:124, :128, :121, :113). Kollisionsfrei ist 120 Läufe / 3.000 Punkte / Daily-Serie 1 / Rekord 85. **Entschieden:** Der Zusammenfall ist gewollt, Welt und Skin werden gemeinsam gefeiert.
- 7.2 STADT auf Rekord 100 erzwingt Merker, SyncState-Feld, StatsSync, WearSyncMerge/WearDotSkin und eine Migration. **Entschieden: STADT 100 bleibt**, der Aufwand ist in AP-13 eingeplant.
- 7.2: Einen Zähler für 'Tage mit Daily' gibt es nicht. Ausdrückbar ist nur bestDailyStreak.
- Allgemein: main steht auf c5742c9, der Plan (809f8fc) liegt auf claude/interactive-ui-suggestions-eez0aj. CI (build-apk.yml, build-ios.yml) läuft nur auf main und claude/**. Ein Push auf main erzeugt ein Prerelease.

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

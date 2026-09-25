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

## 5. Offene Entscheidungen

1. **BLIND! +1** einführen (Punktewertung) oder nur Nebel ohne Bonus?
2. **Stützräder:** Zone leuchtet in den ersten 5 Läufen. Ist 5 richtig?
3. **Ersatzfarbe** für den Himmel bei Score 10 in allen Kulissen.
4. **Welches Lila** meinen die Tester? Klärt der Test in Phase 6.
5. **Wear OS:** Bomben, Lauflicht und Nebel auch auf der Uhr, oder dort
   vereinfacht?
6. **Summen im Nebel gedämpft:** später oder gar nicht?

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

### 7.3 Schnell

- **Umlaute:** `bytesized_regular.ttf` enthält Ä, Ö, Ü, ß. 38 Zeilen in
  `values-de/strings.xml` schreiben trotzdem UE/AE/OE (MENUE, GRUENEN,
  WAEHLEN, SCHLIESSEN).
- **SKINS → SAMMLUNG:** Der Taster öffnet Skins, Kulissen, Töne und Rahmen.
- **„?“ im Game-Over** entfernen, sobald Todesursache und Erklärung beim
  ersten Fallen-Tod da sind. Hilfe bleibt in den Einstellungen.
- **Vorlesen:** `role = Role.Button` für `PixelButton` und Taster, „?“ als
  „Hilfe“ beschriften.
- **MENÜ 6 dp neben der Mitte**, wenn TEILEN fehlt: Der Abstandhalter in der
  Knopfzeile bleibt stehen.

### 7.4 Reihenfolge

1. Zurück-Geste abfangen.
2. Game-Over: feste Leiste unten mit 0,8 s Sperre.
3. Umlaute und SAMMLUNG.
4. Ein Druck-Stil, X-Knopf, Schalter.
5. DAILY zurücknehmen und beim ersten Mal erklären, zusammen mit dem neuen
   Startbildschirm (Phase 4).

### 7.5 Offen

- Braucht das Game-Over TEILEN überhaupt in der Leiste, oder nur nach einem
  neuen Rekord?
- 0,8 s Sperre für die Leiste: im Test mit echten Wut-Taps prüfen.
- Soll es im laufenden Spiel eine Pause geben (dann würde Zurück pausieren)?

# Plan: Erstkontakt, Flackern, Lila — und danach die Bedienelemente

Stand: 25.09.2026, geprüft gegen `main` c5742c9 (Version 2.27).
Spielbare Vorschau aller Entscheidungen: `docs/feedback-check.html`
(dieselbe Seite als Artefakt: https://claude.ai/artifact/7gP6DMMrMaWCp6wU8YskB4, Version 10).

Dieses Dokument hält alles fest, was bis hier entschieden ist, damit bei der
Umsetzung nichts verloren geht. Abschnitt 7 bereitet den nächsten Schritt vor:
die UX der Bedienelemente.

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

## 7. Nächster Schritt: UX der Bedienelemente

Noch nicht bewertet. Bestandsaufnahme aus dem Code als Ausgangspunkt:

| Ort | Element | Code |
|---|---|---|
| Startbildschirm | Einstellungen (Regler-Symbol, 48 dp, oben rechts) | `GameOverlays.kt:313` |
| Startbildschirm | Taster-Leiste DAILY / SKINS / STATISTIK, randlos unten, 64 dp | `GameOverlays.kt:429–483` |
| Startbildschirm | Serien-Abzeichen am DAILY-Taster | `GameOverlays.kt:526` |
| Startbildschirm | Titel „DOTTIE.“ (langer Druck = versteckte Diagnose) | `GameOverlays.kt:335` |
| Game-Over | „TIPPEN = NOCHMAL“, TEILEN, MENÜ, Hilfe „?“ oben rechts | `GameOverlays.kt:244`, `:767–789` |
| Einstellungen | Ton, Erinnerung, Hilfe, Kauf, „Tippen zum Schließen“ | `GameOverlays.kt:1089–1200` |
| Skins | Auswahl, Freischalt-Hinweise, Werbung für Freischaltung | `GameOverlays.kt:1294` |
| Statistik | Bestenliste, „Tippen zum Schließen“ | `StatsOverlay.kt:169` |

Fragen, die dabei zu prüfen sind:

- **„Überall tippen“ gegen Knöpfe:** Im Startbildschirm und im Game-Over
  startet ein Tap irgendwo das Spiel, Knöpfe fangen Taps ab. Wo landen
  Wut-Taps nach dem Tod (MENÜ, TEILEN)? Reicht die Sperre von 0,55 s?
- **Mit der neuen Startregel** (erster Tap zählt) werden Taps im Startbildschirm
  wichtiger. Liegt die Taster-Leiste zu nah am Tippbereich?
- **Tippflächen:** Größe und Abstand (Material empfiehlt mindestens 48 dp),
  besonders Regler-Symbol und „?“ oben rechts.
- **Einheitlichkeit:** Symbol (Regler) gegen Text-Knöpfe, „Tippen zum
  Schließen“ statt eines sichtbaren Schließen-Knopfs, Hilfe an zwei Orten.
- **Auffindbarkeit:** Hilfe liegt hinter dem Zahnrad und dem „?“. Braucht es
  sie nach 3.1 überhaupt noch prominent?
- **Rückmeldung beim Drücken:** Haben alle Knöpfe denselben Druck-Effekt
  (Einsinken in den Schatten wie beim Regler)?

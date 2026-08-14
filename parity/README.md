# Paritäts-Vektoren

`golden-vectors.txt` ist der gemeinsame Vertrag zwischen den drei
Fassungen der Spiellogik:

| Fassung | Ort | Rolle |
|---|---|---|
| Kotlin | `core/src/main/kotlin/…` | **Quelle der Wahrheit**, erzeugt die Datei |
| Swift | `ios/Dottie/Sources/Engine/` | Handport, prüft sich dagegen |
| JavaScript | `web/js/` | Handport, prüft sich dagegen |

Die Engine existiert dreifach — rund 500 Zeilen je Sprache, von Hand
portiert. Kotlin-Tests sagen über die anderen beiden nichts aus. Statt
dieselben Fälle dreimal zu schreiben, schreibt Kotlin einmal auf, was
herauskommen muss, und jeder Port prüft sich gegen dieselbe Datei.

## Wer prüft was

| Abschnitt | Inhalt | Kotlin | Swift | JS |
|---|---|---|---|---|
| `const.*`, `twist.*`, `daily.*` | Konstanten, Freischalt-Scores, Tages-Seeds | ✅ | ✅ | ✅ |
| `medal.*`, `sky.*` | Medaillen-Schwellen und -Farben, Himmelsstufen | ✅ | ✅ | ✅ |
| `skin.*`, `season.*` | Reihenfolge, Farben, Raster, Freischaltungen, Saison-Regeln | ✅ | ✅ | ✅ |
| `scene.*` | Kulissen: Himmel, Wolken, Boden, Requisiten, Freischaltungen | ✅ | ✅ | ✅ |
| `sound.*` | Ton-Sets: Töne, Rauschen, Kacheln, Freischaltungen | ✅ | ✅ | ✅ |
| `progress.*` | Ziele, ihre Reihenfolge und der Fortschrittsbalken | ✅ | ✅ | ✅ |
| `rng.*` | Kotlins XorWow-Generator Zahl für Zahl | ✅ | ✅ | — |
| `trace.*` | ganze Läufe, Treffer für Treffer | ✅ | ✅ | — |

Die Skin-Abschnitte im Einzelnen — sie sind der größte Teil der Datei,
weil dort auch der größte Teil der Handarbeit steckt:

- `skin.order`, `skin.grid`, `skin.collectableCount` — Reihenfolge (sie
  ist zugleich der gespeicherte Wert) und der Sammlungsstand, an dem der
  REGENBOGEN hängt.
- `skin.chips.<ID>` — Stellvertreterfarben und die Eigenschaften
  Schweif, Augen-Kontur, animiert, Saison, Gönner, zählt-für-die-Sammlung.
- `skin.state.N` und `skin.cells.N.<ID>` — abgetastete Rasterfarben in
  mehreren Zuständen. Der Zustand trägt `elapsed score perfectStreak
  hour month`, deckt also auch die Skins ab, deren Farbe an Uhrzeit
  (TAGESZEIT) oder Monat (JAHRESZEIT) hängt.
- `skin.probe.N` und `skin.unlocked.N` — je Probe die neun
  Bestleistungen (Rekord, Perfekt-Serie, Daily-Serie, Läufe, Punkte
  insgesamt, Tage, Monate, Saison-Maske, Kauf) und dahinter, was damit
  offen ist.
- `season.<ID>` — Monat, Bit in `seasonEarned` und geforderte Tage. Ein
  Saison-Skin wird nur in seinem Monat verdient, gilt danach aber für
  immer; entschieden wird deshalb über die Maske, nie über den Kalender.

Die Kulissen (zweite Sammlung), die Ton-Sets (dritte) und die Ziele der
Statistik-Seite hängen mit denselben Proben daran:

- `scene.order`, `scene.sky.<ID>`, `scene.cloud.<ID>`, `scene.ground.<ID>`,
  `scene.chips.<ID>`, `scene.prop.<ID>.<k>` — die komplette Datentabelle
  einer Kulisse. Sie fällt beim Ansehen *nicht* auf: Die WÜSTE öffnet
  erst nach 500 Läufen, der WELTRAUM ganz zuletzt. Ein falscher Wert im
  Port könnte dort monatelang unbemerkt liegen.
- `scene.unlocked.N` — dieselben Proben wie bei den Skins, plus eigene
  für die höheren Kulissen-Schwellen (je einmal knapp darunter und genau
  auf der Kante).
- `sound.order`, `sound.events`, `sound.voice.<ID>.<EREIGNIS>`,
  `sound.chips.<ID>`, `sound.unlocked.N` — die komplette Klangtabelle.
  Ein Ton steht als ein Wort `fromHz:toHz:Sekunden:Lautstärke:
  Abklingrate:Pulsbreite`, das letzte Wort einer Zeile ist das Rauschen
  (`-` heißt keins). Ein Port, der das Rauschen überliest, fällt an der
  Feldzahl auf — und ein falscher Wert fällt sonst nirgends auf: Klang
  hat kein Bild, das man vergleichen könnte.
- `progress.probe.N`, `progress.goals.N`, `progress.next.N` — die offenen
  Ziele **in ihrer Reihenfolge**. Das erste Ziel ist das, was im
  Game-Over steht; eine andere Sortierung wäre auf jeder Plattform ein
  anderer Satz. Die Proben tragen zusätzlich Monat und Saison-Tage, weil
  ein Saison-Ziel nur in seinem eigenen Monat auftauchen darf.
- `progress.fractions` / `progress.filledBlocks` — die Rastung des
  Balkens an ihren Kanten, inklusive der Werte unter 0 und über 1.

Der Web-Port lässt `rng` und `trace` aus, und zwar bewusst: Er baut
Kotlins `XorWowRandom` nicht nach (siehe Kommentar in `web/js/game.js`),
seine Daily Challenge hat deshalb eine eigene Zonen-Abfolge. Regeln,
Farben und Konstanten müssen trotzdem überall identisch sein — genau die
stehen in den anderen Abschnitten.

Für iOS ist der `rng`-Abschnitt der wichtigste: `KotlinRandom.swift`
baut Kotlins Generator bitgenau nach, damit iPhone und Android an
demselben Tag dieselbe Daily Challenge spielen. Ohne diese Vektoren
würde ein Fehler darin niemandem auffallen, bis jemand zwei Geräte
nebeneinanderlegt.

## Was der Vertrag nicht sieht

Die Vektoren tasten **reine Funktionen** ab: gleiche Eingabe, gleiche
Ausgabe. Was sie nicht prüfen können, ist, **womit** eine Plattform diese
Funktionen füttert — und genau dort saß bei einer Durchsicht der Ports
die einzige echte Abweichung:

- Android rechnet einen Lauf dem Tag zu, an dem er **gestartet** ist
  (`ScoreStore.submitRun(score, epochDay, month, year)`), iOS und die PWA
  lesen die Uhr beim **Tod**. Ein Lauf über Mitternacht landet damit in
  unterschiedlichen Monaten — dieselbe `SkinPaint.isUnlocked`, andere
  Eingabe, anderes Ergebnis.
- Dasselbe bei Uhrzeit und Monat der Skins: Android liest sie einmal je
  Lauf, die Ports pro Frame. TAGESZEIT wechselt dort mitten im Lauf die
  Farbe.

Solche Fälle brauchen Tests **in** den Ports (oder eine gemeinsame
Schicht darüber, siehe ARCHITEKTUR.md) — die Vektoren allein finden sie
nicht und behaupten das auch nicht.

## Ausführen

```sh
./gradlew :core:test          # Kotlin: Datei gegen die Engine prüfen
node web/tests/run-tests.js   # JS-Port gegen die Datei prüfen
```

Swift läuft nur auf einem Mac beziehungsweise in der CI:

```sh
cd ios && xcodegen
xcodebuild test -project Dottie.xcodeproj -scheme Dottie \
  -destination 'platform=iOS Simulator,name=iPhone 16'
```

In GitHub Actions macht das der Workflow **Build iOS**.

## Ändern

Die Datei ist erzeugt und wird nicht von Hand bearbeitet. Wenn sich das
Verhalten von `:core` absichtlich ändert:

```sh
./gradlew :core:test -Dparity.update=true
```

Der Diff der Datei zeigt dann genau, was sich für die Ports ändert. Wer
`:core` anfasst, sieht damit schwarz auf weiß, was in `ios/` und `web/`
nachzuziehen ist — und die Tests dort schlagen so lange fehl, bis es
passiert ist.

## Format

Eine Zeile pro Wert, Schlüssel und Werte durch Leerzeichen getrennt,
`#` leitet einen Kommentar ein:

```
const.BASE_SPEED 2.400000
medal.BRONZE 10 0xFFCD7F32 0xFF9C5A1E
trace.perfect.0 2 1 1 2 -1 - 0.369220 0.395000 1.629999 0 -
```

Bewusst kein JSON: Drei Sprachen sollen es ohne Bibliothek lesen können.

## Toleranzen — und warum es sie braucht

Fließkommazahlen stehen mit sechs Nachkommastellen und werden nicht auf
Gleichheit geprüft:

| Was | Toleranz | Grund |
|---|---|---|
| Konstanten, Zonenbreite | `1e-5` | rechnen sich nicht auf, nur Darstellungsrundung |
| Winkel, Zonenmitte | `5e-3` | summieren sich über einen ganzen Lauf auf |
| Farben | ±2 pro Kanal, ohne Alpha | Kotlin rechnet Skins in `Float`, der Swift-Port in `CGFloat` |

Farben stehen als ARGB (`0xFFRRGGBB`) in der Datei, verglichen werden
aber nur Rot, Grün und Blau: Die Ports stellen sie unterschiedlich dar —
Kotlin als ARGB-Long (damit `:core` ohne Compose-Typen auskommt), Swift
als 24-Bit-RGB mit Deckkraft erst an der `UIColor`, JavaScript als
`#RRGGBB`. Alle Werte in `:core` sind vollflächig deckend, es geht also
nichts verloren.

Der interessante Fall ist die zweite Zeile. Kotlin läuft auf der JVM und
rechnet jede Fließkomma-Operation einzeln; Swift wird von LLVM übersetzt,
das `a + b * c` zu einem fused multiply-add zusammenziehen darf. Beides
ist korrekt, keins ist „falsch" — bitgleich sind sie aber nicht. Über die
rund 6000 Frames eines Laufs summiert sich das auf: gemessen wurden
**4·10⁻⁴ Radiant** Abstand am Ende eines 40-Treffer-Laufs, bei sonst
exakt identischen Zahlen (Score, Treffer, Serie, Twists, Richtung,
Ketten). Deshalb 5·10⁻³ als Schranke: reichlich Luft für die Drift und
immer noch fünfzigmal enger als die 0,02 Radiant, die der Punkt pro Frame
zurücklegt. Ein echter Logikunterschied fällt also weiter auf.

Damit die Drift nur Zahlen und keine *Entscheidungen* verschiebt, tappt
der Bot tief im Perfekt-Kern und rechnet ohne `sin` — siehe `ParityBot`
in `core/src/test/`.

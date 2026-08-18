# Paritäts-Vektoren

`golden-vectors.txt` hält das Verhalten von `:core` fest: eine Zeile pro
Wert, erzeugt aus der Engine, eingecheckt und bei jedem Testlauf
nachgeprüft.

## Wozu die Datei heute da ist

Bis v2.23 war sie ein Vertrag zwischen **zwei** Fassungen der Engine:
Kotlin in `:core` und ein Swift-Handport unter
`ios/Dottie/Sources/Engine`. Beide Seiten prüften sich gegen dieselbe
Datei, weil Kotlin-Tests über den Swift-Port nichts aussagen.

Den Handport gibt es nicht mehr — `:core` ist ein
Kotlin-Multiplatform-Modul, und die iOS-App linkt es als
`DottieCore.xcframework`. Damit ist die Datei kein Vertrag zwischen
Sprachen mehr, sondern ein **Golden-Master-Test für `:core` selbst**: Sie
zeigt jede Verhaltensänderung als Diff, bevor sie in drei Apps landet.

Das ist keine Verlegenheitsrolle. Genau in dieser Rolle hat sie zuletzt
gearbeitet: Als eine Farbrundung von Abschneiden auf kaufmännisches
Runden umgestellt wurde, verschob das 78 Zeilen um je eine Kanalstufe —
sichtbar im Diff, statt unbemerkt in vier Apps.

Bis v2.22 hat sich hier zusätzlich ein JavaScript-Port (`web/`) geprüft.
Er ist mit der Konzentration auf die nativen Apps entfallen; sein letzter
Stand liegt im Commit `b4ed73f`.

## Was drinsteht

| Abschnitt | Inhalt |
|---|---|
| `const.*`, `twist.*`, `daily.*` | Konstanten, Freischalt-Scores, Tages-Seeds |
| `medal.*`, `sky.*` | Medaillen-Schwellen und -Farben, Himmelsstufen |
| `skin.*`, `season.*` | Reihenfolge, Farben, Raster, Freischaltungen, Saison-Regeln |
| `scene.*` | Kulissen: Himmel, Wolken, Boden, Requisiten, Freischaltungen |
| `progress.*` | Ziele, ihre Reihenfolge und der Fortschrittsbalken |
| `rng.*` | Kotlins XorWow-Generator Zahl für Zahl |
| `trace.*` | ganze Läufe, Treffer für Treffer |

Die Skin-Abschnitte im Einzelnen — sie sind der größte Teil der Datei:

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

Die Kulissen und die Ziele der Statistik-Seite hängen mit denselben
Proben daran:

- `scene.order`, `scene.sky.<ID>`, `scene.cloud.<ID>`, `scene.ground.<ID>`,
  `scene.chips.<ID>`, `scene.prop.<ID>.<k>` — die komplette Datentabelle
  einer Kulisse. Sie fällt beim Ansehen *nicht* auf: Die WÜSTE öffnet
  erst nach 500 Läufen, der WELTRAUM ganz zuletzt. Ein falscher Wert
  könnte dort monatelang unbemerkt liegen.
- `scene.unlocked.N` — dieselben Proben wie bei den Skins, plus eigene
  für die höheren Kulissen-Schwellen (je einmal knapp darunter und genau
  auf der Kante).
- `progress.probe.N`, `progress.goals.N`, `progress.next.N` — die offenen
  Ziele **in ihrer Reihenfolge**. Das erste Ziel ist das, was im
  Game-Over steht; eine andere Sortierung wäre ein anderer Satz. Die
  Proben tragen zusätzlich Monat und Saison-Tage, weil ein Saison-Ziel
  nur in seinem eigenen Monat auftauchen darf.
- `progress.fractions` / `progress.filledBlocks` — die Rastung des
  Balkens an ihren Kanten, inklusive der Werte unter 0 und über 1.

`rng` bleibt der empfindlichste Abschnitt: An dieser Zahlenfolge hängt,
dass iPhone und Android an demselben Tag dieselbe Daily Challenge
spielen. Seit beide `kotlin.random.Random` benutzen, ist das keine
Nachbau-Frage mehr — die Vektoren halten aber weiter fest, dass sich die
Folge nicht durch einen Umbau in `:core` verschiebt.

## Was der Vertrag nicht sieht

Die Vektoren tasten **reine Funktionen** ab: gleiche Eingabe, gleiche
Ausgabe. Was sie nicht prüfen können, ist, **womit** eine Plattform diese
Funktionen füttert — und genau dort sitzt die verbliebene Abweichung:

- Android rechnet einen Lauf dem Tag zu, an dem er **gestartet** ist
  (`ScoreStore.submitRun(score, epochDay, month, year)`), iOS liest die
  Uhr beim **Tod**. Ein Lauf über Mitternacht landet damit in
  unterschiedlichen Monaten — dieselbe `SkinPaint.isUnlocked`, andere
  Eingabe, anderes Ergebnis.
- Dasselbe bei Uhrzeit und Monat der Skins: Android liest sie einmal je
  Lauf, iOS pro Frame. TAGESZEIT wechselt dort mitten im Lauf die Farbe.

Solche Fälle bräuchten eine gemeinsame Schicht über der Engine (siehe
ARCHITEKTUR.md) — die Vektoren allein finden sie nicht und behaupten das
auch nicht.

## Ausführen

```sh
./gradlew :core:jvmTest          # Datei gegen die Engine prüfen
```

## Ändern

Die Datei ist erzeugt und wird nicht von Hand bearbeitet. Wenn sich das
Verhalten von `:core` absichtlich ändert:

```sh
./gradlew :core:jvmTest -Dparity.update=true
```

Der Diff zeigt dann genau, was sich für alle drei Apps ändert. Ein
unerwarteter Diff ist die Ansage, dass ein Umbau mehr angefasst hat als
gedacht.

## Format

Eine Zeile pro Wert, Schlüssel und Werte durch Leerzeichen getrennt,
`#` leitet einen Kommentar ein:

```
const.BASE_SPEED 2.400000
medal.BRONZE 10 0xFFCD7F32 0xFF9C5A1E
trace.perfect.0 2 1 1 2 -1 - 0.369220 0.395000 1.629999 0 -
```

Bewusst kein JSON: Jede Sprache soll es ohne Bibliothek lesen können.

Farben stehen als ARGB (`0xFFRRGGBB`). Fließkommazahlen stehen mit sechs
Nachkommastellen; verglichen wird mit `1e-5` Toleranz für Konstanten und
`5e-3` für Werte, die sich über einen Lauf aufsummieren (Winkel,
Zonenmitte). Die weite Schranke stammt aus der Zeit des Swift-Ports: Die
JVM rechnet jede Fließkomma-Operation einzeln, LLVM darf `a + b * c` zu
einem fused multiply-add zusammenziehen — über die rund 6 000 Frames
eines Laufs waren das gemessen 4·10⁻⁴ Radiant Abstand. Sie bleibt
stehen, weil sie auch für Kotlin/Native gilt: Dieselbe Engine, auf zwei
Backends übersetzt, rechnet nicht bitgleich.

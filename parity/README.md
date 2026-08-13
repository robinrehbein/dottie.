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
| `medal.*`, `sky.*`, `skin.*` | Schwellen, Farben, Skin-Raster, Freischaltungen | ✅ | ✅ | ✅ |
| `rng.*` | Kotlins XorWow-Generator Zahl für Zahl | ✅ | ✅ | — |
| `trace.*` | ganze Läufe, Treffer für Treffer | ✅ | ✅ | — |

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

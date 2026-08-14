# Architektur: vier Plattformen, dreimal dieselbe Logik

Dieses Dokument beschreibt, wo der Code heute steht, warum er dreifach
existiert, und was eine Vereinheitlichung mit **Kotlin Multiplatform**
(KMP) konkret kosten und bringen würde. Es ist eine Entscheidungsgrundlage,
kein Plan — umgesetzt ist bisher nichts davon.

## Wo der Code steht

| Bereich | Ort | Zeilen | Sprache |
|---|---|---|---|
| Spiellogik (geteilt) | `core/src/main/kotlin` | 1257 | Kotlin |
| Phone-App | `app/src/main` | 4030 | Kotlin (Compose) |
| Wear-App | `wear/src/main` | 1536 | Kotlin (Wear Compose) |
| iOS: Engine-Port | `ios/Dottie/Sources/Engine` | 1338 | Swift |
| iOS: UI und Umfeld | `ios/Dottie/Sources/{UI,Support}` | 2246 | Swift (SpriteKit) |
| Web: Logik-Port | `web/js/{game,daily,synth,skins}.js` | 1114 | JavaScript |
| Web: UI und Umfeld | `web/js/{render,main,pixelbutton,audio,store,strings}.js` | 1859 | JavaScript |

`:app` und `:wear` teilen sich `:core` direkt — dort liegen `TimingGame`,
`DailyChallenge`, `SkinPaint` und `MedalPaint`. Zwischen Kotlin, Swift und
JavaScript gibt es dagegen keine geteilte Zeile: **2452 Zeilen Logik sind
von Hand nachgebaut.**

Abgesichert ist das seit dem Paritäts-Vertrag in
[`parity/`](parity/README.md): `:core` erzeugt Soll-Werte, alle Ports
prüfen sich dagegen. Das *findet* Abweichungen — es *verhindert* sie
nicht. Der nächste Schritt wäre, sie unmöglich zu machen.

## Was KMP lösen würde — und was nicht

**Lösen würde es:** die Engine. `TimingGame`, `DailyChallenge`,
`SkinPaint`, `MedalPaint` und `ChipSynth` sind reines Kotlin ohne
Plattform-Anteil — genau der Fall, für den `commonMain` gedacht ist.

Der interessanteste Einzelfall ist `ios/…/Engine/KotlinRandom.swift`: 119
Zeilen, die Kotlins `XorWowRandom` bitgenau nachbauen, damit iPhone und
Android an demselben Tag dieselbe Daily Challenge spielen. Unter KMP wäre
diese Datei nicht mehr nötig, sondern **unmöglich falsch**: Es liefe
überall dieselbe `kotlin.random.Random`.

**Nicht lösen würde es:**

- **Die Renderer.** Compose Canvas, SpriteKit und Canvas2D zeichnen
  dieselben Rechtecke mit drei verschiedenen APIs. Das sind 2246 Zeilen
  Swift und 1859 Zeilen JavaScript, die bleiben (außer bei Option C).
- **Die Texte.** `strings.xml`, `Localizable.strings` und `strings.js` —
  dreimal dieselben Sätze. Geteilte Ressourcen gehen mit KMP (Compose
  Resources, moko-resources), das ist aber ein eigenes Projekt mit
  eigener Abhängigkeit.
- **Audio, Haptik, Persistenz, Teilen.** SoundPool vs. AVAudioEngine vs.
  WebAudio; DataStore vs. UserDefaults vs. localStorage. Das ist
  `expect`/`actual`-Gebiet: die Schnittstelle wird geteilt, die
  Umsetzung nicht.

## Option A — `:core` als KMP-Modul für Android und iOS

Der naheliegende Schnitt: `:core` bekommt neben dem JVM-Ziel iOS-Ziele
und wird als Framework in die Xcode-App gelinkt.

**Was zu tun wäre**

1. `core/build.gradle.kts` auf `kotlin("multiplatform")` umstellen, Ziele
   `jvm()` (für `:app`/`:wear`), `iosArm64`, `iosSimulatorArm64`; Quellen
   nach `src/commonMain/kotlin` verschieben.
2. XCFramework-Task ergänzen; `ios/project.yml` um eine Build-Phase, die
   das Framework baut und linkt.
3. Die 1338 Zeilen unter `ios/…/Engine` löschen und die Aufrufstellen in
   `GameScene.swift`, `OverlayNodes.swift`, `PixelArt.swift` und
   `GameViewController.swift` auf die Framework-Typen umstellen.
4. `build-ios.yml` um den Gradle-Schritt erweitern.

**Was es kostet**

- **Interop-Reibung.** Kotlin-Typen kommen über Objective-C in Swift an:
  Enums werden Klassen, `Set<Twist>` wird `NSSet`, das sealed interface
  `GameEvent` wird eine Klassenhierarchie mit `is`-Prüfungen,
  Default-Argumente verschwinden. Der Swift-Code wird dadurch nicht
  hübscher, sondern umständlicher — betroffen sind geschätzt 300–500 der
  2246 UI-Zeilen.
- **Build-Zeit auf teuren Runnern.** Der erste Kotlin/Native-Lauf lädt
  die Konan-Toolchain (~1 GB) und braucht mehrere Minuten; danach greift
  der Cache. Genau bei diesem Repo tut das weh: Die iOS-CI läuft bewusst
  selten, und macOS-Minuten kosten das Zehnfache.
- **Debugging.** Ein Fehler in der Engine wird aus Xcode heraus deutlich
  unangenehmer zu verfolgen als heute, wo dort lesbarer Swift liegt.
- **Lokale Entwicklung** bleibt gleich gut — auf einem Mac ist es ein
  Gradle-Schritt mehr.

**Was es bringt**

- 1338 Zeilen Swift weg, ein ganzer Handport weniger zu pflegen.
- Die Daily Challenge ist per Konstruktion identisch statt per Test.
- Neue Spiel-Features landen einmal in Kotlin und sind auf iOS sofort da
  — heute ist jedes Feature zweimal zu bauen, und beide Seiten müssen
  sich auch wieder gemeinsam ändern: Als `revive` aus der Engine flog,
  war das ein Eingriff in `:core` **und** in den Swift-Port.

**Aufwand:** grob ein bis zwei Tage für Setup und Umstellung der
Aufrufstellen, plus eine CI-Runde zum Geradeziehen.

## Option B — zusätzlich Kotlin/JS für die PWA

Technisch dieselbe Bewegung, ein Ziel `js(IR)` mehr, und die 1114 Zeilen
JavaScript-Logik fielen weg. Die Web-Daily-Challenge liefe dann sogar
erstmals synchron zu Android und iOS (heute eine bewusste, dokumentierte
Abweichung).

**Dagegen spricht ziemlich viel:**

- `web/` hat heute **kein Build-Tooling**. Man legt die Dateien auf einen
  Webserver, fertig; jede Datei ist lesbar und einzeln debuggbar. Mit
  Kotlin/JS braucht die PWA einen Gradle-Build, bevor sie überhaupt
  startet.
- **Größe.** Die gesamte PWA-Logik wiegt heute 107 KB unkomprimiert,
  32 KB gzip — davon 38 KB der Engine-Anteil. Ein Kotlin/JS-Bundle
  derselben Logik landet mit Runtime typischerweise bei einem
  Mehrfachen davon. Für ein Spiel, dessen Verkaufsargument „lädt sofort,
  läuft offline, ist der kostenlose Weg auf iPhones" ist, ist das die
  falsche Richtung.
- Die JS-UI (1859 Zeilen) bliebe ohnehin und müsste dann über
  `@JsExport`-Grenzen mit der Engine reden.

**Empfehlung: nicht machen.** Die Paritäts-Vektoren decken beim Web-Port
genau das ab, was zählt (Regeln, Farben, Schwellen, Texte), und die
Abweichung beim Zufallsgenerator ist bewusst gewählt.

## Option C — Compose Multiplatform statt SpriteKit

Eine Besonderheit dieses Projekts macht die radikale Variante
überhaupt erst denkbar: **Die Android-App zeichnet alles im Code.** Keine
Layouts, keine Bild-Assets — `TimingGameScreen.kt`, `GameOverlays.kt` und
`PixelButton.kt` malen Rechtecke auf ein Compose-Canvas. Genau das läuft
mit Compose Multiplatform auch auf iOS (über Skia).

Damit wäre nicht nur die Engine geteilt, sondern der komplette Renderer
samt Overlays — die 3584 Zeilen unter `ios/` fielen weg, eine UI für
Phone und iPhone.

**Kosten:** Die iOS-App wird ein Kotlin/Native-Compose-Build (App-Größe
plus etwa 8–12 MB für Compose und Skia, spürbar längere CI-Läufe);
AdMob, Billing und Play Games bleiben Android-only und brauchen
`expect`/`actual`; Audio, Haptik und Persistenz ebenso. Und: Ein
fertiger, funktionierender Swift-Port würde weggeworfen.

**Empfehlung:** nur, wenn das Pflegen von zwei UI-Codebasen tatsächlich
zum Problem wird. Aktuell ist die iOS-UI fertig und stabil — sie kostet
gerade nichts.

## Empfehlung in einem Satz

Solange iOS ein gelegentlich nachgezogener Port ist, reicht der
Paritäts-Vertrag in `parity/`; sobald iOS regelmäßig neue Spiel-Features
bekommen soll, ist **Option A** der richtige Zeitpunkt-Auslöser — Web
bleibt außen vor, und Compose Multiplatform ist eine Frage für einen
größeren Umbau, nicht für den nächsten Schritt.

Der Auslöser ist also nicht „wir sparen Zeilen", sondern „iOS bekommt
laufend neue Logik". Bis dahin gilt: Die Vektoren sagen Bescheid, wenn
die Ports auseinanderlaufen.

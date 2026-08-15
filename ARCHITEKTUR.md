# Architektur: drei Apps, eine Spiellogik

Dieses Dokument beschreibt, wo der Code steht, was seit v2.24 geteilt ist
und was als Nächstes zu teilen wäre.

Ausgeliefert wird nativ auf Android (Telefon und Wear OS) und iOS. Eine
Web-Version gibt es seit v2.23 nicht mehr; ihr letzter Stand liegt im
Commit `b4ed73f`.

## Wo der Code steht

| Bereich | Ort | Zeilen | Sprache |
|---|---|---|---|
| Spiellogik (geteilt) | `core/src/commonMain/kotlin` | 3506 | Kotlin |
| Oberflaeche (geteilt) | `ui/src/commonMain` | 1027 | Kotlin (Compose Multiplatform) |
| Phone-App | `app/src/main` | 5230 | Kotlin (Compose) |
| Wear-App | `wear/src/main` | 2511 | Kotlin (Wear Compose) |
| iOS: Brücke zu `:core` | `ios/Dottie/Sources/Core` | 536 | Swift |
| iOS: UI und Umfeld | `ios/Dottie/Sources/{UI,Support}` | 3658 | Swift (SpriteKit) |

`:core` ist ein Kotlin-Multiplatform-Modul: `jvm()` für `:app` und
`:wear`, dazu `iosArm64` (Gerät), `iosSimulatorArm64` und `iosX64`
(Simulator auf Apple Silicon und Intel).
Dort liegen `TimingGame`, `DailyChallenge`, `SkinPaint`, `ScenePaint`,
`SoundBank`, `ChipSynth`, `MedalPaint` und `Progress` — **alle drei Apps
rechnen mit demselben Code.**

## Was der Weg dahin war

Bis v2.23 gab es die Engine zweimal: einmal Kotlin in `:core`, einmal
Swift unter `ios/Dottie/Sources/Engine` — 2 893 Zeilen von Hand portiert.
Abgesichert war das über den Paritäts-Vertrag in
[`parity/`](parity/README.md): `:core` erzeugte Soll-Werte, der Port
prüfte sich dagegen. Das *fand* Abweichungen; verhindert hat es sie nicht.

Zwei Schritte haben das aufgelöst:

**Stufe 1 (v2.23)** — `:core` wurde ein Multiplattform-Modul. Die Quellen
liegen seither in `src/commonMain/kotlin`, die Tests bleiben JVM-only
(sie lesen und schreiben Dateien). `:app`, `:wear` und `:sync` merken
davon nichts: Kotlins Plattform-Regel lässt `androidJvm`-Konsumenten
`jvm`-Produzenten nutzen.

**Stufe 2 (v2.24)** — iOS linkt `DottieCore.xcframework` aus `:core`, und
der Swift-Handport ist gelöscht. Was blieb, ist
`ios/Dottie/Sources/Core/CoreBridge.swift`: die Grenze zwischen den
Sprachen, und zwar nur die.

## Die Grenze in einer Datei

`CoreBridge.swift` enthält keine Spielregel. Was dort steht, ist
Übersetzung:

- **Zahlen.** Kotlins `Int` ist Swifts `Int32`, Kotlins `Long` ist
  `Int64`; die Szene rechnet in `Int` und `CGFloat`.
- **Farben.** `:core` führt Farben als ARGB in einem `Long`, die Renderer
  als 0xRRGGBB in `UInt32`.
- **Namen.** Kotlin/Native exportiert `object X` als `X.shared` und
  `companion object` als `X.companion`. Erweiterungen holen die
  Schreibweise zurück, die der Renderer schon vorher benutzt hat; wo ein
  Name schon vergeben ist — Kotlins `Progress` gegen Foundations
  `Progress` —, steht ein `typealias` daneben.
- **Aufzählungen.** Kotlin-`enum`s werden Klassen; `switch` geht darauf
  nicht. Wo der Renderer eine Fallunterscheidung braucht (die acht
  Requisiten-Formen), steht ein Swift-`enum` daneben.

Dazu zwei Werte, die `:core` bewusst nicht kennt, weil sie keine
Spielregel sind, sondern deren Eingabe: die Geräte-Uhr und der lokale
Kalendertag.

### Was `:core` dafür bekommen hat

Die Interop-Reibung ist nicht nur in Swift aufgefangen worden — an ein
paar Stellen war die Kotlin-Seite schlicht die bessere:

- `GamePhase`, `Twist` und `GameEvent` standen in `TimingGame`
  verschachtelt. Kotlin/Native flacht verschachtelte Klassen beim Export
  ab; der Name, den Swift sähe, stünde nirgends im Kotlin-Code. Jetzt
  stehen sie auf oberster Ebene und heißen in beiden Sprachen gleich.
- `reseed(seed: Long?)` wurde zu `reseed(seed: Long)` plus
  `reseedSystem()`: Ein nullbares `Long` käme in Swift als eingepacktes
  `KotlinLong?` an.
- `TimingGame` hat einen parameterlosen Konstruktor bekommen, weil
  Standardwerte nicht exportiert werden.
- `SkinFamily` — die Menü-Gliederung der 42 Skins — lag vorher nur im
  iOS-Menü und damit als zweite, stille Quelle für dieselbe Reihenfolge.
  Jetzt steht sie neben den Skins, die sie einteilt.
- `ChipSynth.render(voice, rate)` rechnet die Tonhöhen-Varianten, die
  iOS vorrendert. Android pitcht beim Abspielen (SoundPool) und kommt mit
  `rate = 1` aus — die Rechnung gehört trotzdem zum Klang, nicht zur
  Abspielschicht.
- `Scene.sky` und `SkinPaint.SKY_STAGES` sind `List<Long>` statt
  `LongArray`: `LongArray` wird zu `KotlinLongArray` und lässt sich in
  Swift nur umständlich durchlaufen.

Keine dieser Änderungen hat das Verhalten verschoben — die
Paritäts-Vektoren in `parity/` haben das Zeile für Zeile bestätigt.

## Was noch doppelt ist

- **Die Renderer.** Compose Canvas und SpriteKit zeichnen dieselben
  Rechtecke mit zwei APIs. Das sind die 3 658 Zeilen unter
  `ios/Dottie/Sources/{UI,Support}` — der größte verbliebene Block.
- **Die Texte.** `strings.xml` und `Localizable.strings`, zweimal
  dieselben Sätze.
- **Audio, Haptik, Persistenz, Teilen.** SoundPool vs. AVAudioEngine,
  SharedPreferences vs. UserDefaults. Das ist `expect`/`actual`-Gebiet:
  Die Schnittstelle ließe sich teilen, die Umsetzung nicht.
- **Die Wear-Kopien.** `WearDotSkin`, `WearRenderer` und die
  Android-Wrapper `DotSkin`, `DotScene`, `DotSound` in `:app` sind dünne
  Compose-Adapter um `:core` — aber sie sind zu zweit.

## Stufe 3 — Compose Multiplatform statt SpriteKit

Eine Besonderheit dieses Projekts macht die radikale Variante überhaupt
erst denkbar: **Die Android-App zeichnet alles im Code.** Keine Layouts,
keine Bild-Assets — `TimingGameScreen.kt`, `GameOverlays.kt` und
`PixelButton.kt` malen Rechtecke auf ein Compose-Canvas. Genau das läuft
mit Compose Multiplatform auch auf iOS (über Skia).

### Angefangen: das Modul `:ui` und die Spielwelt

`:ui` ist ein Compose-Multiplatform-Modul mit denselben vier Zielen wie
`:core` (`androidTarget`, `iosArm64`, `iosSimulatorArm64`, `iosX64`).
Darin liegt bisher die **Spielwelt**: Himmel, Wolken, Kulisse, Boden,
Perlenketten-Bahn und Pixel-Vogel — 813 Zeilen `DrawScope`, die vorher
in `:app` steckten, plus die Retro-Palette, die Pixel-Bausteine und den
Effekt-Zustand.

Der Schnitt ist mit Absicht dort gemacht: Die Zeichenschicht kennt weder
Texte noch Knöpfe noch Werbung, nur `:core` und Compose. Sie war deshalb
die einzige größere Einheit, die sich ohne Vorarbeit teilen ließ. `:app`
zeichnet seither über `:ui`; die Android-Wrapper `DotSkin`/`DotScene`
tauchen im Renderer nicht mehr auf, er rechnet direkt mit `SkinId` und
`SceneId`.

Nutzen hat das aktuell nur die Wartung: Solange iOS SpriteKit benutzt,
läuft der geteilte Renderer nur auf Android.

### Offen: der Rest

1. **Overlays und Texte.** `GameOverlays.kt` (1 300 Zeilen),
   `StatsOverlay.kt` und `PixelButton.kt` sind reines Compose, hängen
   aber an 87 `R.string`/`R.font`-Verweisen. Sie brauchen Compose
   Resources — und damit fallen auch die Wrapper `DotSkin`, `DotScene`
   und `DotSound` weg, die es nur gibt, um `@StringRes`-IDs zu tragen.
2. **Der Controller.** `TimingGameScreen` ist keine Zeichenroutine,
   sondern die Verdrahtung: Werbung, Kauf, Bestenlisten,
   Benachrichtigungs-Berechtigung, Lebenszyklus. Er muss in einen
   geteilten Spielablauf und eine Android-Schale zerfallen.
3. **`expect`/`actual`** für Audio, Haptik, Persistenz und
   Benachrichtigungen (SoundPool vs. AVAudioEngine,
   SharedPreferences vs. UserDefaults).
4. **iOS-Einstieg** über `ComposeUIViewController`; `ios/project.yml`
   linkt ein zweites Framework. Erst dann fallen die 3 658 Zeilen Swift
   unter `ios/Dottie/Sources/{UI,Support}` weg — und mit ihnen
   `CoreBridge.swift`.

**Kosten:** Die iOS-App wird ein Kotlin/Native-Compose-Build (App-Größe
plus etwa 8–12 MB für Compose und Skia, spürbar längere CI-Läufe). AdMob,
Billing und Play Games bleiben Android-only. Der SpriteKit-Renderer wird
weggeworfen — er ist fertig und stabil, kostet also gerade nichts.

**Das eigentliche Risiko** liegt nicht auf iOS, sondern auf der Seite,
die im Play Store steht: Schritt 1 und 2 fassen die Android-Oberfläche
an, und es gibt keinen UI-Test, der eine Regression fangen würde. Deshalb
endet dieser Schritt hier — die Spielwelt ist geteilt und der Umbau
bleibt an einer Stelle stehen, an der beide Apps unverändert laufen.

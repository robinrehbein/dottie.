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
| Oberflaeche (geteilt) | `ui/src/commonMain` | 3234 | Kotlin (Compose Multiplatform) |
| Phone-App | `app/src/main` | 2871 | Kotlin (Compose) |
| Wear-App | `wear/src/main` | 2511 | Kotlin (Wear Compose) |
| iOS: Brücke zu `:core` | `ios/Dottie/Sources/Core` | 551 | Swift |
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

### Gebaut: `:ui` traegt die Oberflaeche

`:ui` ist ein Compose-Multiplatform-Modul mit denselben vier Zielen wie
`:core` (`androidTarget`, `iosArm64`, `iosSimulatorArm64`, `iosX64`).
Darin liegt inzwischen alles, was die Android-App zeichnet, ausser dem
Controller:

- **Die Spielwelt** — Himmel, Wolken, Kulisse, Boden, Perlenketten-Bahn
  und Pixel-Vogel (813 Zeilen `DrawScope`), dazu die Retro-Palette, die
  Pixel-Bausteine und der Effekt-Zustand.
- **Die Overlays** — `GameOverlays`, `StatsOverlay`, `PixelButton`,
  Theme und Typografie.
- **Die Texte** — 184 Saetze je Sprache in
  `ui/src/commonMain/composeResources`. In `:app` bleiben 16: die, die
  nur Android hat (Benachrichtigungen, Teilen-Text, Score-Karte).

Damit sind vier Aufzaehlungen ersatzlos entfallen — `DotSkin`,
`DotScene`, `DotSound` und `MedalTier` in `:app`. Es waren 55 Zeilen
Zuordnung von Kennung zu Ressourcen-ID, und sie existierten nur, weil
Android `@StringRes`-Ints verlangt. Der Schluessel wird jetzt aus dem
Namen gerechnet (`"skin_" + name.lowercase()`), genau wie es der iOS-Port
schon immer tat; dass zu jedem Namen ein Text existiert, prueft
`TextsTest`.

Zwei inhaltliche Ausnahmen von der Namensregel stehen ausdruecklich da:
KLASSIK hat keinen Freischalt-Hinweis, und die drei Goenner-Skins teilen
sich einen.

Nach `:core` gewandert ist dabei `SkinPaint.earnedCount` — "verdient,
Saison zaehlt mit, Kauf nicht". Daran haengt die Freischalt-Feier; es lag
im geloeschten Wrapper und ist nicht dasselbe wie `unlockedCount`, dem
Sammlungsstand ohne Saison.

Nutzen hat das bisher nur die Wartung: Solange iOS SpriteKit benutzt,
laeuft die geteilte Oberflaeche nur auf Android.

### Gebaut: die Plattform-Grenze

Zwischen der geteilten Oberflaeche und dem Geraet stehen jetzt drei
Schnittstellen in `:ui`:

- **`KeyValueStore`** — vier Leser und ein Schreib-Block. Darueber liegt
  `GameStore` mit den Spielstands-Regeln: Tages-Serie, Saison-Fenster,
  Tagespass, Abgleich mit der Uhr. Sie lagen bis dahin doppelt (470
  Zeilen Kotlin, 269 Zeilen Swift) und waren an einer Stelle schon
  auseinandergelaufen. Android legt die Schnittstelle auf
  SharedPreferences, iOS bekommt NSUserDefaults.
- **`GameSounds`** und **`GameFeedback`** — was klingt und ruettelt,
  steht in `:core`; hinter der Schnittstelle steht nur, wie es zum
  Lautsprecher kommt (SoundPool gegen AVAudioEngine).
- **`epochMillis()`** und **`deviceHourAndMonth()`** als
  `expect`/`actual`: Kotlins Standardbibliothek hat keine gemeinsame Uhr.

Der iOS-Workflow uebersetzt `:ui` seither auch fuer iOS. Der Schritt hat
sich sofort bezahlt gemacht: Fuenf Stellen in `commonMain` liefen nur,
weil das Android-Ziel die JVM-Bibliothek mitbringt (`Integer.bitCount`,
`Math.PI`, `java.time.LocalDateTime`) — sie waeren sonst erst beim
iOS-Einstieg aufgefallen.

### Offen: der Controller und der iOS-Einstieg

`TimingGameScreen` (1 600 Zeilen) ist die letzte Datei der Oberflaeche in
`:app` — und sie ist kein Bildschirm, sondern die Verdrahtung. Nach den
drei Schnittstellen bleiben 25 Beruehrungspunkte, die wirklich nur
Android hat: Werbung, Kauf, Bestenlisten, Abgleich mit der Uhr,
Benachrichtigungs-Berechtigung, Teilen.

Was noch zu tun ist:

1. **`TimingGameScreen` nach `:ui`**, mit `GameStore`, `GameSounds` und
   `GameFeedback` als Parametern. Die 25 Android-Punkte haengen an
   optionalen Rueckrufen, die auf iOS nichts tun; `:app` behaelt eine
   duenne Schale, die sie fuellt.
2. **iOS-Umsetzungen** in `iosMain`: `NSUserDefaults` fuer den Speicher,
   `UIImpactFeedbackGenerator` fuer die Haptik, AVAudioEngine fuer den
   Klang.
3. **iOS-Einstieg** ueber `ComposeUIViewController`; `ios/project.yml`
   linkt ein zweites Framework. Erst dann fallen
   `ios/Dottie/Sources/UI` (2 900 Zeilen SpriteKit) und
   `CoreBridge.swift` weg.

**Kosten:** Die iOS-App wird ein Kotlin/Native-Compose-Build (App-Größe
plus etwa 8–12 MB für Compose und Skia, spürbar längere CI-Läufe). AdMob,
Billing und Play Games bleiben Android-only. Der SpriteKit-Renderer wird
weggeworfen — er ist fertig und stabil, kostet also gerade nichts.

**Das eigentliche Risiko** liegt nicht auf iOS, sondern auf der Seite,
die im Play Store steht: Der Umbau fasst die Android-Oberfläche an, und
es gibt keinen UI-Test, der eine Regression fangen würde. Abgesichert ist
er über die Logik-Tests (`:core`, `:ui`, `:app`) und darüber, dass jeder
Schritt an einer Stelle endet, an der beide Apps unverändert laufen.

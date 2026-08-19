# Architektur: drei Apps, eine Spiellogik, eine Oberfläche

Dieses Dokument beschreibt, wo der Code steht und wie es dazu kam: Bis
v2.23 gab es die Spiellogik zweimal, bis v2.24 die Oberfläche zweimal.
Beides ist jetzt aufgelöst — iOS ist nur noch eine dünne Swift-Hülle
(App-Lifecycle plus Einstiegspunkt) um zwei Kotlin-Multiplatform-Module.

Ausgeliefert wird nativ auf Android (Telefon und Wear OS) und iOS. Eine
Web-Version gibt es seit v2.23 nicht mehr; ihr letzter Stand liegt im
Commit `b4ed73f`.

## Wo der Code steht

| Bereich | Ort | Zeilen | Sprache |
|---|---|---|---|
| Spiellogik (geteilt) | `core/src/commonMain/kotlin` | 3748 | Kotlin |
| Oberfläche (geteilt) | `ui/src/commonMain` | 5087 | Kotlin (Compose Multiplatform) |
| Oberfläche, Android-Anschluss | `ui/src/androidMain` | 57 | Kotlin |
| Oberfläche, iOS-Anschluss | `ui/src/iosMain` | 283 | Kotlin |
| Phone-App (nur noch Verdrahtung: Ads, Billing, Sync, Teilen) | `app/src/main` | 1792 | Kotlin (Compose) |
| Wear-App | `wear/src/main` | 2612 | Kotlin (Wear Compose) |
| iOS-Hülle | `ios/Dottie/Sources` | 46 | Swift (UIKit) |

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

**Stufe 2 (v2.24, Zwischenstand)** — iOS linkt `DottieCore.xcframework`
aus `:core`, und der Swift-Handport der Engine ist gelöscht. Was zu diesem
Zeitpunkt blieb, war `ios/Dottie/Sources/Core/CoreBridge.swift`: die
Grenze zwischen den Sprachen, und zwar nur die — die Darstellung selbst
zeichnete iOS zu diesem Zeitpunkt noch mit SpriteKit. Stufe 3 (unten) hat
auch das aufgelöst; `CoreBridge.swift` gibt es seither nicht mehr.

## Die Grenze in einer Datei (historisch, aufgelöst in Stufe 3)

Solange iOS noch SpriteKit für die Darstellung benutzte, enthielt
`CoreBridge.swift` keine Spielregel. Was dort stand, war Übersetzung —
festgehalten hier, weil dieselben Übersetzungsprobleme bei jeder
Kotlin/Native-Anbindung wiederkehren würden:

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

## Was nach Stufe 2 noch doppelt war — und was daraus wurde

Zum Zeitpunkt, als nur `:core` geteilt war (Stufe 2), lagen vier Dinge
noch zweimal im Repo. Stufe 3 (unten) hat drei davon aufgelöst:

- **Die Renderer.** Compose Canvas und SpriteKit zeichneten dieselben
  Rechtecke mit zwei APIs, 3 658 Zeilen unter
  `ios/Dottie/Sources/{UI,Support}`. **Aufgelöst (v2.24):** Der
  SpriteKit-Renderer ist gelöscht, `:ui` zeichnet auf beiden Plattformen.
- **Die Texte.** `strings.xml` und `Localizable.strings`, zweimal
  dieselben Sätze. **Aufgelöst (v2.24):** Die `.lproj`-Dateien unter
  `ios/Dottie/Resources` sind weg, beide Plattformen lesen
  `ui/src/commonMain/composeResources`.
- **Audio, Haptik, Persistenz.** SoundPool vs. AVAudioEngine,
  SharedPreferences vs. UserDefaults. **Aufgelöst (v2.24)** über die drei
  Plattform-Schnittstellen `KeyValueStore`, `GameSounds`, `GameFeedback`
  (`ui/src/iosMain`, siehe „Gebaut: die Plattform-Grenze" unten) — die
  Schnittstelle ist jetzt geteilt, nur die Umsetzung bleibt platform-
  eigen, wie es `expect`/`actual` vorsieht.
- **Die Wear-Kopien.** `WearDotSkin`, `WearRenderer` und die
  Android-Wrapper `DotSkin`, `DotScene`, `DotSound` in `:app` sind dünne
  Compose-Adapter um `:core` — aber sie sind zu zweit. **Bleibt offen:**
  Das war nie Teil von Stufe 3, die iOS betraf; die Wear-App teilt sich
  weiterhin nur `:core`, keine Oberfläche.

Was `:ui` bewusst nicht teilt, weil es keine Oberfläche ist, sondern ein
Vertrieb: Anzeigen, Kauf, Bestenlisten und das Teilen einer Score-Card als
Bild bleiben Android-only (siehe die Tabelle unter „Gebaut: der
Controller und der iOS-Einstieg").

## Stufe 3 — Compose Multiplatform statt SpriteKit (v2.24, umgesetzt)

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
- **Die geteilte Score-Karte** — seit v2.26 auch sie: eine
  `DrawScope`-Routine (`share/ScoreCardRenderer.kt`), die den Bauplan
  aus `:core` (`CardPlan`) in ein `ImageBitmap` von 1080 mal 1350 Pixeln
  ausmalt. Vorher zeichnete sie `android.graphics` in `:app`, und auf
  dem iPhone gab es sie deshalb nicht.
- **Die Texte** — die Saetze in `ui/src/commonMain/composeResources`. In
  `:app` bleibt, was nur Android hat: die Benachrichtigungen und die
  Store-Schluessel.

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

Nutzen hatte das zunächst nur die Wartung: Solange iOS noch SpriteKit
benutzte, lief die geteilte Oberfläche nur auf Android. Seit Stufe 3
(unten) zeichnet iOS dieselbe `:ui` — der Nutzen ist jetzt auch der
gemeinsame Bildschirm selbst.

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

### Gebaut: der Controller und der iOS-Einstieg (v2.24)

`TimingGameScreen` in `:app` war die letzte Datei der Oberfläche, die kein
Bildschirm war, sondern Verdrahtung — sie blieb bewusst in `:app`, weil
genau diese Berührungspunkte wirklich nur Android haben. Die Tabelle hält
fest, was das war und wie iOS ohne sie auskommt — nicht mehr als Plan,
sondern als Bestand:

| Was | Wo im Bildschirm | Auf iOS |
|---|---|---|
| `ads.enabled`, `ads.status`, `ads.rewardedReady` | Diagnose-Zeile, Skin-Menue | immer `false` |
| `ads.start()`, `ads.onGameOver(activity)` | Aufbau, Tod | entfaellt |
| `ads.showRewarded(activity) { … }` | Tagespass im Skin-Menue | entfaellt |
| `ads.privacyOptionsRequired`, `showPrivacyOptions` | READY-Overlay | entfaellt |
| `billing.priceLabel`, `patronPriceLabel`, `status` | READY, Skin-Menue | `null` |
| `billing.purchase/purchasePatron(activity)` | READY, Skin-Menue | entfaellt |
| `billing.connect()`, `release()` | Lebenszyklus | entfaellt |
| `leaderboards.available/connect/show` | READY-Overlay | entfaellt |
| `leaderboards.submitBest/submitDaily` | Tod | entfaellt |
| `statsSync.start/stop/publish` | Lebenszyklus, nach jeder Wahl | entfaellt |
| `DailyReminder.schedule/cancel/needsPermission` | READY-Schalter | spaeter UNUserNotificationCenter |
| `notifPermission.launch(POST_NOTIFICATIONS)` | READY-Schalter | entfaellt |
| `ScoreCard.share(…)` | Game-Over | seit v2.26 `IosShare` (UIActivityViewController) |
| `LocalLifecycleOwner` | Start/Stopp des Abgleichs | Compose Multiplatform hat kein Pendant |

**So wurde es umgesetzt, in drei Schritten:**

1. `PlatformHooks` (`ui/src/commonMain/.../platform/PlatformHooks.kt`)
   fasst genau diese Punkte als Werte und Rückrufe, alle mit einem
   Standard, der nichts tut. Der Bildschirm zieht `GameStore`,
   `GameSounds`, `GameFeedback` und `PlatformHooks` als Parameter und
   heißt `GameScreen` (`ui/src/commonMain/.../screens/GameScreen.kt`).
2. `:app` behält eine dünne `TimingGameScreen`-Schale, die die Dienste
   baut und die Rückrufe füllt — die einzige Datei, die noch `Activity`
   und `LocalLifecycleOwner` kennt.
3. `iosMain` bekam `IosSounds` (AVAudioEngine; `ChipSynth` aus `:core`
   liefert die Samples, wie es der SpriteKit-Port vorher schon tat) und
   `fun MainViewController() = ComposeUIViewController { GameScreen(…) }`.
   `ios/project.yml` linkt seither `DottieUi.xcframework`,
   `GameViewController.swift` hängt den Controller ein — mehr steht dort
   nicht mehr.

Damit sind `ios/Dottie/Sources/UI` (2 900 Zeilen SpriteKit) und
`CoreBridge.swift` weggefallen; die iOS-Hülle besteht seither nur noch aus
`AppDelegate.swift` und `GameViewController.swift` (zusammen 46 Zeilen).

**Kosten — eingetreten wie erwartet:** Die iOS-App ist jetzt ein
Kotlin/Native-Compose-Build (größere App, spürbar längere CI-Läufe wegen
Compose und Skia). AdMob, Billing und Play Games bleiben Android-only,
siehe Tabelle oben. Der SpriteKit-Renderer wurde verworfen — er war fertig
und stabil, das Wegwerfen kostete also nichts an Funktion, nur die
3 658 Zeilen, die ihn gebaut hatten.

**Das eigentliche Risiko** liegt nicht auf iOS, sondern auf der Seite,
die im Play Store steht: Der Umbau fasst die Android-Oberfläche an, und
es gibt keinen UI-Test, der eine Regression fangen würde. Abgesichert ist
er über die Logik-Tests (`:core`, `:ui`, `:app`) und darüber, dass jeder
Schritt an einer Stelle endet, an der beide Apps unverändert laufen.

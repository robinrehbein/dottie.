# Dottie. für iOS

Die iPhone-App: SpriteKit + UIKit ohne Storyboard für die Darstellung,
die Spiellogik aus dem geteilten Kotlin-Modul `:core` — dasselbe, mit dem
die Android-App in `app/` und die Wear-App in `wear/` rechnen.

## Stand der Dinge

Die **Spiellogik kommt aus `:core`** — demselben Kotlin-Modul, mit dem
auch die Android- und die Wear-App rechnen. Es wird als
`DottieCore.xcframework` gelinkt; übersetzt wird an genau einer Stelle,
in `Dottie/Sources/Core/CoreBridge.swift`.

Bis v2.23 lagen unter `Dottie/Sources/Engine` 2 893 Zeilen Swift, die
dieselbe Logik von Hand nachbauten (`TimingGame`, `SkinPaint`,
`ScenePaint`, `SoundSet`, `ChipSynth`, `Progress`, `MedalTier`,
`DailyChallenge` und `KotlinRandom`, das Kotlins XorWow-Generator
bitgenau nachstellte). Das ist ersatzlos entfallen: Die Daily Challenge
ist jetzt per Konstruktion auf beiden Plattformen dieselbe, nicht per
Test.

**Was iOS selbst mitbringt:**

- Darstellung in SpriteKit/UIKit ohne Storyboard: Himmelsstufen,
  Perlenketten-Bahn mit 60 Segmenten, Pixel-Vogel mit Blickrichtung,
  Boden mit Grasnarbe, Szenerie mit Parallaxe, Wolken, „DOTTIE."-Titel,
  Mario-Tod (Freeze → Hüpfer → Kopfüber-Fall), Medaillen, Spott-Texte,
  REKORD-Banner, Freischalt-Zelebration, Flash + Shake.
- Klang über AVAudioEngine: `ChipSynth` aus `:core` liefert die Samples,
  die Tonhöhen-Varianten (Treffer-Pentatonik, Perfekt-Serie) werden
  vorgerendert statt wie auf Android per SoundPool-Rate gepitcht.
- Umfeld: Persistenz über UserDefaults, Haptik über
  UIImpactFeedbackGenerator, Tages-Erinnerung, Texte EN/DE
  (Localizable.strings), Bytesized-Pixel-Font.

**Inhaltlich gleichauf mit Android:** Classic + Daily, 42 Skins in sechs
Familien, sechs Kulissen, drei Ton-Sets, Ziele und Statistik-Seite —
alles aus denselben Tabellen.

**Bewusst nicht dabei (wie beauftragt):** Teilen/Score-Card,
Leaderboards/Game Center.

**Kein Billing:** Die drei Gönner-Skins (DIAMANT, PHOENIX, ONYX) stehen im
Menü, `patronOwned` ist auf iOS aber fest `false` — sie bleiben sichtbar,
aber gesperrt, bis es einen Kauf gibt.

## Build (CI, macOS-Runner)

Lokal gibt es in dieser Repo-Umgebung keine Apple-Toolchain — kompiliert
wird über GitHub Actions:

1. GitHub → **Actions** → Workflow **„Build iOS"** → **„Run workflow"**
   (Branch wählen). Der Workflow startet auch automatisch bei jedem Push,
   der `ios/`, `core/` oder `parity/` anfasst.
2. Der Lauf macht: `./gradlew :core:assembleDottieCoreDebugXCFramework`
   (die Spiellogik als Framework — Kotlin/Native baut Apple-Ziele nur auf
   einem Mac) → `brew install xcodegen` → `cd ios && xcodegen` (erzeugt
   `Dottie.xcodeproj` aus `project.yml`) → `xcodebuild test`
   (Brücken-Tests im Simulator, siehe unten) → `xcodebuild` einmal für
   `generic/platform=iOS` (Device, unsigniert) und einmal für den
   Simulator.
3. Artefakt: die unsignierte Simulator-`Dottie.app` — auf einem Mac per
   Drag & Drop in den Simulator ziehen oder
   `xcrun simctl install booted Dottie.app`.

Lokal auf einem Mac:

```sh
./gradlew :core:assembleDottieCoreDebugXCFramework
brew install xcodegen && cd ios && xcodegen && open Dottie.xcodeproj
```

Der erste Schritt ist Pflicht: Ohne das Framework in
`core/build/XCFrameworks/debug/` findet Xcode `import DottieCore` nicht.
Nach jeder Änderung an `:core` muss er wiederholt werden.

## Tests: die Brücke, nicht die Engine

Die Engine braucht auf dieser Seite keine Tests mehr — sie ist dieselbe,
die `./gradlew :core:jvmTest` prüft. Was bleibt, ist das, was
`CoreBridge.swift` von Hand macht und deshalb still auseinanderlaufen
kann: die aus Kotlin-Namen abgeleiteten Textschlüssel (`SkinId.LAVA` →
`skin_lava`), die Familien-Gliederung des Skin-Menüs und die Zuordnung
der Requisiten-Formen. Dazu ein Lauf über die Brücke als Beweis, dass das
Framework wirklich verlinkt ist.

```sh
./gradlew :core:assembleDottieCoreDebugXCFramework
cd ios && xcodegen
xcodebuild test -project Dottie.xcodeproj -scheme Dottie \
  -destination 'platform=iOS Simulator,name=iPhone 16'
```

Es ist ein Logik-Test-Bundle ohne Host-App: Es übersetzt
`Dottie/Sources/Core` direkt mit und linkt dasselbe Framework wie die
App — deshalb braucht es keine Signierung.

## Was zum Verteilen noch fehlt

- **Apple-Developer-Account (99 $/Jahr)** — daran hängt alles:
  - Mit Account: App in App Store Connect anlegen, Build signieren und
    hochladen → **TestFlight**. Danach lässt sich das Spiel komplett vom
    iPhone aus installieren und an bis zu 10 000 Tester:innen verteilen;
    App-Store-Release ist derselbe Weg plus Review.
  - Ohne Account: **keine Installation auf echten iPhones** — auch nicht
    in der EU. Alternative Wege (Web Distribution, AltStore & Co. über
    den DMA) setzen ebenfalls einen Developer-Account samt
    Signierung/Notarisierung voraus. Ein Gratis-Konto kann nur über Xcode
    direkt am Mac auf ein per Kabel verbundenes Gerät deployen, 7 Tage
    gültig.
- Für signierte CI-Builds bräuchte der Workflow zusätzlich Zertifikat +
  Provisioning-Profil als Secrets (z. B. via Fastlane match) — bewusst
  noch nicht eingerichtet.

## Struktur

```
ios/
├── project.yml                  # XcodeGen-Definition (Target "Dottie")
├── Dottie/
│   ├── Info.plist               # portrait-only, Font-Registrierung
│   ├── Sources/
│   │   ├── AppDelegate.swift    # UIKit-Lifecycle ohne Storyboard
│   │   ├── GameViewController.swift
│   │   ├── Core/
│   │   │   └── CoreBridge.swift # Zahlen, Farben und Namen an der Sprachgrenze
│   │   ├── Support/             # Store, Audio, Haptik, Farben, L10n
│   │   └── UI/                  # GameScene, Overlays, Pixel-Texturen
│   └── Resources/
│       ├── Fonts/bytesized_regular.ttf
│       ├── Assets.xcassets/AppIcon.appiconset/
│       ├── en.lproj/Localizable.strings
│       └── de.lproj/Localizable.strings
├── DottieTests/
│   └── CoreBridgeTests.swift    # prüft die Brücke, nicht die Engine
└── README.md
```

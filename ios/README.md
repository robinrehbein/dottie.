# Dottie. für iOS

Nativer Swift-Port des Spiels (SpriteKit + UIKit, kein Storyboard) —
parallel zur Android-App in `app/` und der Wear-App in `wear/`.

## Stand der Dinge

**Portiert (1:1 zur Android-Logik, gleiche Konstanten, keine eigene Balance):**

- `TimingGame` — komplette Engine: Phasen (READY/RUNNING/DYING/OVER),
  Zielzone mit PERFEKT-Kern und Serien-Bonus (+2 … +5), Twists
  PULS/DRIFT/GEIST/FALLE/KETTE ab Score 5/10/15/20/25, Schwierigkeit über
  Treffer, Touch-Latenz-Gnade, Restart-Sperre.
- `DailyChallenge` — Tages-Seed und Serien-Regeln. Der Seed ist
  **bit-identisch zu Android**: `KotlinRandom` baut Kotlins
  XorWow-Generator (inkl. 64 Warmup-Runden, `nextFloat`, `nextBoolean`,
  `nextInt(bound)`, Fisher-Yates-Shuffle) in Swift nach — dieselbe
  Tages-Abfolge auf beiden Plattformen.
- `ChipSynth` — Chiptune-Sounds aus denselben Wellenform-Berechnungen,
  abgespielt über AVAudioEngine/AVAudioPCMBuffer. Tonhöhen-Varianten
  (Treffer-Pentatonik, Perfekt-Serie) sind vorgerendert statt per
  SoundPool-Rate gepitcht — klingt gleich.
- Look: Himmelsstufen (7 Farben pro 5er-Stufe), Perlenketten-Bahn mit
  60 Segmenten, Pixel-Vogel mit Blickrichtung, Boden mit Grasnarbe,
  Szenerie (Bäume/Blumen/Sträucher mit Parallaxe), Wolken, „DOTTIE."-Titel,
  Mario-Tod (Freeze → Hüpfer → Kopfüber-Fall), Medaillen (10/20/30/40),
  Spott-Texte, REKORD-Banner, Freischalt-Zelebration, Flash + Shake.
- Kulissen: sechs Sets (Wiese, Wüste, Meer, Berg, Stadt, Weltraum) aus
  `ScenePaint` — Himmel, Wolken, Requisiten und Boden als Daten, die
  Texturen entstehen daraus in `PixelArt.propTexture`. Auswahl im
  SKINS-Overlay über den Skin-Familien, gespeichert wie die Skin-Wahl.
- Features: Classic + Daily, 42 Skins (gleiche Farben, Muster und
  Freischalt-Bedingungen wie `:core`; das Menü ist nach Familien
  gegliedert und scrollt), Sound an/aus (persistiert), Haptik
  (UIImpactFeedbackGenerator), Persistenz via UserDefaults,
  Texte EN/DE (Localizable.strings), Bytesized-Pixel-Font.

**Bewusst nicht dabei (wie beauftragt):** Teilen/Score-Card,
Daily-Reminder-Notification, Leaderboards/Game Center.

**Kein Billing:** Die drei Gönner-Skins (DIAMANT, PHOENIX, ONYX) stehen im
Menü, `patronOwned` ist auf iOS aber fest `false` — sie bleiben sichtbar,
aber gesperrt, bis es einen Kauf gibt.

## Build (CI, macOS-Runner)

Lokal gibt es in dieser Repo-Umgebung keine Apple-Toolchain — kompiliert
wird über GitHub Actions:

1. GitHub → **Actions** → Workflow **„Build iOS"** → **„Run workflow"**
   (Branch wählen). Der Workflow läuft **nur manuell** — macOS-Minuten
   sind auf privaten Repos 10x teurer als Linux-Minuten.
2. Der Lauf macht: `brew install xcodegen` → `cd ios && xcodegen`
   (erzeugt `Dottie.xcodeproj` aus `project.yml`) → `xcodebuild` einmal
   für `generic/platform=iOS` (Device, unsigniert) und einmal für den
   Simulator.
3. Artefakt: die unsignierte Simulator-`Dottie.app` — auf einem Mac per
   Drag & Drop in den Simulator ziehen oder
   `xcrun simctl install booted Dottie.app`.

Lokal auf einem Mac genügt: `brew install xcodegen && cd ios && xcodegen
&& open Dottie.xcodeproj`.

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
│   │   ├── Engine/              # 1:1-Ports der Kotlin-Logik
│   │   │   ├── TimingGame.swift
│   │   │   ├── DailyChallenge.swift
│   │   │   ├── KotlinRandom.swift   # Kotlins XorWowRandom, bit-identisch
│   │   │   ├── ChipSynth.swift
│   │   │   ├── MedalTier.swift
│   │   │   └── DotSkin.swift
│   │   ├── Support/             # Store, Audio, Haptik, Farben, L10n
│   │   └── UI/                  # GameScene, Overlays, Pixel-Texturen
│   └── Resources/
│       ├── Fonts/bytesized_regular.ttf
│       ├── Assets.xcassets/AppIcon.appiconset/
│       ├── en.lproj/Localizable.strings
│       └── de.lproj/Localizable.strings
└── README.md
```

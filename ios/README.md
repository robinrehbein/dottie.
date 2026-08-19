# Dottie. für iOS

Die iPhone-App: eine Swift-Hülle um zwei geteilte Kotlin-Multiplatform-
Module. `:core` liefert die Spiellogik, `:ui` die komplette Oberfläche —
dasselbe Compose-Multiplatform-Modul, das auch die Android-App zeichnet.
Was in Swift bleibt, ist der App-Einstieg: zwei Dateien, 46 Zeilen.

## Stand der Dinge

**Die Spiellogik kommt aus `:core`**, **die Oberfläche aus `:ui`** —
denselben Kotlin-Modulen, mit denen auch die Android- und die Wear-App
rechnen bzw. zeichnen. Beide werden als XCFrameworks gelinkt
(`DottieCore.xcframework`, `DottieUi.xcframework`).

Bis v2.23 lagen unter `Dottie/Sources/Engine` 2 893 Zeilen Swift, die
dieselbe Spiellogik von Hand nachbauten. Bis v2.24 zeichnete
`Dottie/Sources/UI` (SpriteKit) dieselbe Oberfläche noch einmal, über eine
551 Zeilen lange Brücke (`CoreBridge.swift`). Beides ist ersatzlos
entfallen: Die Oberfläche ist jetzt per Konstruktion auf beiden
Plattformen dieselbe, nicht per Test oder Handarbeit abgeglichen.

**Was von der iOS-App selbst übrig ist:**

- `AppDelegate.swift` — klassischer UIKit-Lifecycle ohne Storyboard und
  ohne Scene-Manifest, für ein Ein-Screen-Spiel der einfachste stabile
  Weg.
- `GameViewController.swift` — hängt die Compose-Oberfläche aus `:ui`
  (`MainViewControllerKt.MainViewController()`) als Kind-View-Controller
  ein. Mehr steht dort nicht.

Alles andere — Himmel, Bahn, Vogel, Overlays, Skins, Kulissen, Ton-Sets,
Statistik-Seite, Texte in Englisch und Deutsch — kommt aus `:ui` und ist
mit Android identisch, weil es derselbe Code ist, der auf Skia statt auf
dem Android-Canvas zeichnet.

**Inhaltlich gleichauf mit Android:** Classic + Daily, 42 Skins in sechs
Familien, sechs Kulissen, drei Ton-Sets, Ziele und Statistik-Seite —
alles aus denselben Tabellen und derselben Oberfläche. Dazu die tägliche
Erinnerung an die Daily Challenge: Opt-in über dieselbe Zeile im
Einstellungs-Blatt, abends um 18 Uhr wie am Telefon, gebaut auf
`UNUserNotificationCenter`
(`ui/src/iosMain/.../platform/IosReminder.kt`). Weil iOS keinen
Hintergrund-Job kennt, der abends nachschaut, erinnert sie auch an einem
Tag, an dem die Daily schon gespielt wurde — der Unterschied ist in der
Datei und in [../ARCHITEKTUR.md](../ARCHITEKTUR.md) begründet. Ein
Eintrag in `Info.plist` oder `project.yml` ist dafür nicht nötig: Lokale
Benachrichtigungen kennen keinen Usage-Description-Schlüssel, die
Berechtigung wird zur Laufzeit erfragt.

**Bewusst nicht dabei** — das sind Vertriebs-Anschlüsse, keine
Oberfläche, und `:ui` teilt sie deshalb nicht: Anzeigen (AdMob), Käufe
(Play Billing), Bestenlisten (Play Games) und das Teilen einer Score-Card
als Bild (`android.graphics`, Android-only). Details und der Stand der
Migration stehen in [../ARCHITEKTUR.md](../ARCHITEKTUR.md) unter „Gebaut:
der Controller und der iOS-Einstieg".

**Kein Billing:** Die drei Gönner-Skins (DIAMANT, PHOENIX, ONYX) stehen im
Menü, sind auf iOS aber fest gesperrt — sie bleiben sichtbar, weil sie
Teil der geteilten Oberfläche sind, aber es gibt dort kein Billing, das
sie freischalten könnte.

## Build (CI, macOS-Runner)

Lokal gibt es in dieser Repo-Umgebung keine Apple-Toolchain — kompiliert
wird über GitHub Actions, Workflow [`build-ios.yml`](../.github/workflows/build-ios.yml):

1. Läuft automatisch bei jedem Push auf `main` und auf `claude/**`, der
   `ios/`, `core/`, `ui/` oder `parity/` anfasst; sonst per **Actions →
   Build iOS → Run workflow**.
2. Der Lauf macht: `./gradlew :core:assembleDottieCoreDebugXCFramework`
   (Spiellogik als Framework) → `./gradlew :ui:assembleDottieUiDebugXCFramework`
   (Oberfläche als Framework, mit Compose und Skia der langsamste Schritt
   im Lauf) → `brew install xcodegen` → `cd ios && xcodegen` (erzeugt
   `Dottie.xcodeproj` aus `project.yml`) → je ein `xcodebuild` für
   `generic/platform=iOS` (Device, unsigniert) und für den Simulator.
3. Artefakt: die unsignierte Simulator-`Dottie.app` — auf einem Mac per
   Drag & Drop in den Simulator ziehen oder
   `xcrun simctl install booted Dottie.app`.

Lokal auf einem Mac:

```sh
./gradlew :core:assembleDottieCoreDebugXCFramework
./gradlew :ui:assembleDottieUiDebugXCFramework
brew install xcodegen && cd ios && xcodegen && open Dottie.xcodeproj
```

Die ersten beiden Schritte sind Pflicht: Ohne die Frameworks in
`core/build/XCFrameworks/debug/` und `ui/build/XCFrameworks/debug/`
findet Xcode `import DottieCore` und `import DottieUi` nicht. Nach jeder
Änderung an `:core` oder `:ui` müssen sie wiederholt werden.

## Tests

Es gibt kein eigenes iOS-Testbundle: Die Spiellogik prüft
`./gradlew :core:jvmTest`, die Oberfläche `./gradlew :ui:testDebugUnitTest`
(`:ui` hat kein `jvm()`-Ziel, die Tests in `ui/src/commonTest` laufen über
die Android-Zielplattform) — beide gelten für alle Plattformen mit, weil
es nur noch eine Logik und eine Oberfläche gibt. Was auf der iOS-Seite
bleibt, ist der Beweis, dass die Frameworks wirklich linken; das
übernimmt der Build-Schritt selbst (`xcodebuild build` schlägt fehl, wenn
`import DottieCore` oder `import DottieUi` nicht auflöst).

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
├── project.yml                  # XcodeGen-Definition (Target "Dottie"),
│                                 # linkt DottieUi.xcframework und
│                                 # DottieCore.xcframework aus core/ und ui/
├── Dottie/
│   ├── Info.plist               # portrait-only, Font-Registrierung
│   ├── Sources/
│   │   ├── AppDelegate.swift    # UIKit-Lifecycle ohne Storyboard
│   │   └── GameViewController.swift  # haengt die Compose-Oberflaeche aus :ui ein
│   └── Resources/
│       ├── Fonts/bytesized_regular.ttf
│       └── Assets.xcassets/AppIcon.appiconset/
└── README.md
```

Texte, Pixel-Zeichnung und alle Overlays liegen nicht mehr unter `ios/`,
sondern in `ui/src/commonMain` — siehe [../ARCHITEKTUR.md](../ARCHITEKTUR.md).

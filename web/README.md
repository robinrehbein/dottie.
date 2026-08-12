# Dottie. — Web-Version (PWA)

Die Browser-Version des Spiels: reines HTML/CSS/JS ohne Build-Tooling,
die Spiellogik ist ein 1:1-Port von `core/.../TimingGame.kt` (gleiche
Konstanten, gleiches Verhalten), die Sounds werden wie am Phone zur
Laufzeit synthetisiert (`ChipSynth` → WebAudio).

Warum: Auf iPhones gibt es ohne Apple-Developer-Account keinen Weg zu
einer nativen App — eine PWA ist der einzige kostenlose Vertriebsweg.
Über Safari → Teilen → **„Zum Home-Bildschirm"** installiert sie sich
wie eine App (Vollbild, Hochformat, eigenes Icon, offline spielbar).

## Lokal testen

```sh
cd web
python3 -m http.server 8000
```

Dann <http://localhost:8000> öffnen (am besten im Mobile-Emulations-Modus
der DevTools). Der Service Worker braucht `localhost` oder HTTPS.

Logik-Tests (Port-Parität mit der Kotlin-Quelle):

```sh
cd web
node tests/run-tests.js
```

## Optik: die Android-App ist die Referenz

Die App gibt vor, wie das Spiel aussieht — die PWA folgt ihr. Konkret:

- **1 dp = 1 CSS-Pixel.** Alle Größen, Schriftgrößen und Abstände in
  `style.css` sind die dp/sp-Werte aus `GameOverlays.kt`; die Overlays
  bilden die Compose-Ausrichtungen nach (`.safe` = `windowInsetsPadding`,
  `.go-center` = `Alignment.Center`, `#hint` = `Center` + 140 dp Polster).
- **Zeilenhöhe 1.25** — genau `(ascent + |descent|) / unitsPerEm` der
  Bytesized, also dieselbe Zeilenhöhe, die Compose aus den Font-Metriken
  zieht.
- **Textschatten in Geräte-Pixeln.** Compose gibt ihn als
  `Shadow(offset = Offset(4f, 4f))` an, das sind 4 echte Pixel und nicht
  4 dp. `main.js` setzt dafür `--dev-px` aus `devicePixelRatio`.
- **Treppen-Rahmen der Knöpfe** (`js/pixelbutton.js`): Port von
  `drawPixelBorder`. Ein `border: 3px solid` kann die im oberen und
  unteren Viertel doppelt breite Kante nicht; die Geometrie steckt
  deshalb in Hintergrund-Layern und bleibt bei jeder Pixeldichte scharf.
- **Farben** kommen aus `js/render.js` (`Renderer.Palette`) und werden im
  Test Wert für Wert gegen `GameOverlays.kt`, `TimingGameScreen.kt` und
  `DotSkin.kt` geprüft — dasselbe für alle Texte gegen `strings.xml`.

Bewusste Abweichungen (Browser-Grenzen, jeweils im Code kommentiert):

- **Keine Tages-Erinnerung.** Die App hat neben dem Ton-Knopf einen
  Glocken-Knopf; verlässliche geplante Benachrichtigungen gibt es in
  einer PWA (vor allem auf iOS) nicht, deshalb fehlt der Knopf hier.
- **Keine Rangliste.** Der LEADERBOARD-Knopf hängt in der App an Play
  Games.
- **TEILEN ohne Score-Card.** Die App rendert ein PNG (`ScoreCard.kt`);
  im Browser wird derselbe Text per Web Share verschickt (sonst in die
  Zwischenablage).
- **Druck-Feedback.** Statt des Material-Ripples hellt sich der Knopf
  kurz auf — verschieben tut er sich wie am Phone nicht.

## Screenshots

`tests/screenshots/` enthält die aktuellen PWA-Aufnahmen (393x852 CSS-Px
bei dpr 3) zum Vergleich mit den echten App-Bildern in
`store/screenshots/`: Startscreen (leer und mit Rekord), Lauf, Hilfe,
Skin-Picker, Game-Over. Neu erzeugen:

```sh
cd web
python3 -m http.server 8765 &
node tests/screenshots.js      # braucht Playwright + Chromium
```

## Hosting

Der Ordner ist als statische Site für **GitHub Pages** gedacht: In den
Repo-Einstellungen GitHub Pages auf den Branch zeigen lassen (z. B. mit
`/docs` oder einem Deploy-Workflow, der `web/` veröffentlicht). Alle
Pfade sind relativ, ein Unterverzeichnis wie
`https://<user>.github.io/<repo>/` funktioniert daher direkt.

Nach Änderungen die `CACHE_VERSION` in `sw.js` hochzählen, sonst liefert
der Cache-First-Service-Worker installierten Spieler:innen alte Dateien.

## Dateien

- `index.html`, `style.css` — Markup und Pixel-Look (Bytesized-Font,
  Safe-Areas, kein Zoom)
- `js/game.js` — Spiellogik (Port von `TimingGame.kt`)
- `js/daily.js` — Daily Challenge: Tages-Seed und Serien-Regeln
- `js/synth.js` — Chiptune-Synthese (Port von `ChipSynth.kt`)
- `js/audio.js` — WebAudio-Abspiel-Schicht + Haptik (`navigator.vibrate`)
- `js/skins.js` — Skins und Medaillen-Stufen
- `js/strings.js` — Texte EN/DE (Browser-Sprache entscheidet)
- `js/store.js` — Persistenz in `localStorage`
- `js/render.js` — Canvas-Rendering (Bahn, Vogel, Szenerie, Medaille)
- `js/pixelbutton.js` — Treppen-Rahmen und Lautsprecher-Motiv der Knöpfe
  (Port von `PixelButton.kt`)
- `js/main.js` — Frame-Loop, Events, Overlays
- `manifest.webmanifest`, `sw.js`, `icon-*.png` — PWA-Installation und
  Offline-Cache
- `tests/run-tests.js` — Logik-, Farb- und Text-Tests, laufen mit Node
- `tests/screenshots.js`, `tests/screenshots/` — Vergleichsbilder gegen
  die Android-App

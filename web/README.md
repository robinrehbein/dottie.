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
- `js/main.js` — Frame-Loop, Events, Overlays
- `manifest.webmanifest`, `sw.js`, `icon-*.png` — PWA-Installation und
  Offline-Cache
- `tests/run-tests.js` — Logik-Tests, laufen mit Node

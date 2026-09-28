# Bevel-Look — Briefing für die Umsetzung

In eine neue Claude-Code-Session mit dem Repo `robinrehbein/dottie.` geben.
Maßgeblich ist Abschnitt 0 mit den Zielbildern in
`docs/bevel-mockups/ziel/`.

```text
Baue den Bevel-Look in Dottie ein.

Briefing, Zielbilder und Prototyp liegen auf dem Branch
claude/dotty-2-5d-graphics-3y59iu. Lies docs/bevel-look.md komplett.
Abschnitt 0 ist verbindlich und geht allem anderen vor. Das Ergebnis soll
genau wie die Zielbilder in docs/bevel-mockups/ziel/ aussehen. Sie sind
mit dem echten Renderer und docs/bevel-prototyp.patch erzeugt.

Arbeite auf einem eigenen Branch und öffne am Ende einen Draft-PR nach
main. Kein Merge. Commit-Nachrichten und Kommentare auf Deutsch, im Stil
des bestehenden Codes. Frag nicht nach Dingen, die im Briefing schon
entschieden sind.
```

## 0. Finaler Stand (verbindlich, geht allem anderen vor)

Nach den Abstimmungen gilt dieser Abschnitt. Wo die Abschnitte 1–9 oder
ältere Mockups etwas anderes sagen, gilt **dieser Abschnitt**.

**Die Zielbilder** liegen in `docs/bevel-mockups/ziel/`. Sie sind mit dem
echten Renderer auf dem Stand von `main` nach PR #77 und dem Prototyp
`docs/bevel-prototyp.patch` gerendert: jede Welt mit jedem Twist und
wechselnden Skins (`<welt>-<twist>-<skin>.png`), dazu eine Übersicht pro
Welt (`uebersicht-<welt>.png`). Das Ergebnis soll **genau so** aussehen.
Die älteren gezeichneten Mockups (`welt-*.png`, `alle-welten.png`,
`welten-*.png`) sind überholt, maßgeblich sind die Zielbilder.

Die Entscheidungen:

1. **Bevel-Regel:** Licht oben links, eine Stufe breit, dunkle Kante unten
   und rechts, dann die helle Kante oben und links darüber. Flächen unter
   3 Stufen bleiben flach. Die Farben werden abgeleitet mit
   `light = mix(basis, Weiß, 0.35)` und `dark = mix(basis, Outline #543847, 0.30)`.
2. **Dottie „Kugel“:** Stufen, Glanzkern und Augenkante wie in Abschnitt 4.
   Keine Flügel.
3. **Minen:** `TrapPaint.MINE` bleibt unverändert, dazu kommt die Kante auf
   den Kugel-Pixeln. Schwarz: hell `#4E4656`, dunkel `#0A080C`. Rot: hell
   `#FF8A7E`, dunkel `#9E1F1C`.
4. **Bahn-Blöcke pro Welt** im Raster der Bahn (`TrackBlock`, Kante =
   `unit`), mit den Farben und Mustern aus der Tabelle in 8.2. Die WIESE
   behält `GroundSandShade` als Basis.
5. **Zone:** Der Körper bleibt überall grün wie heute. Die Blätter und
   Tupfer der Wiese bleiben nur in der WIESE. Die anderen Welten bekommen
   ihr Motiv aus 8.2. **Im Perfekt-Kern gibt es kein Rot und kein Rosa**
   (`main` hat die Blüte entfernt, weil Tester sie für eine Warnung
   hielten). Die Kern-Akzente:
   - WIESE: keiner, wie `main`
   - WUESTE: gelbes Kreuz `#FFE08A` mit weißer Mitte
   - MEER: weißes Kreuz mit gelber Mitte (`DotBody`)
   - BERG: weißer Stern mit gelber Mitte
   - STADT: weißes 3×3-Quadrat
   - WELTRAUM: weiße Diagonale
6. **Requisiten:** Baum, Blume, Strauch, Kaktus, Palmeninsel, Nadelbaum,
   Hochhaus und Laterne bleiben **exakt in ihrer Form** und bekommen nur
   Bevel. Ausnahmen:
   - **FELS** wird Findling plus Kiesel.
   - **WELLE** wird ein Brecher.

   Beide sind Pixel-Masken (`BOULDER`, `PEBBLE`, `BREAKER` im Prototyp),
   eine Zelle pro Maskenpixel, und liegen wie `TrapPaint.MINE` als
   Zeilen in `:core`.
7. **Boden pro Welt** wie im Prototyp (`drawProtoGround`):
   - WIESE: Grasnarbe als Bevel-Kacheln, Sand mit Kieseln
   - WUESTE: Dünenkante, drei Sandsteinschichten mit Fugen, halb
     eingesunkene Kiesel in Sandtönen (`#E3BE82`, `#C79A55`, `#A57C42`)
   - MEER: Wellenkämme mit Schaum, die bis 3 Zellen über die Bodenkante
     reichen, dazu Wasserstufen und Luftblasen-Ringe
   - BERG: Schneedecke mit Eiszapfen über einer bevelten Felsmauer
   - STADT: Bordsteine, Asphalt mit gelber Mittellinie und Gully
   - WELTRAUM: kein Boden
8. **Wolken:** weiße Oberkante, dunklere Unterkante, keine Outline.
9. **Galaxien:** Arme in drei deckenden Stufen (`t < 0.35` volle Farbe,
   `t < 0.7` `mix(arm, Himmel, 0.2)`, sonst `mix(arm, Himmel, 0.45)`). Staub
   nur innen, deckend. Schimmer deckend in `mix(arm, Himmel, 0.2)`.
   „Himmel“ ist die aktuelle Himmelsstufe des Weltraums, nicht fest.
10. **Nicht anfassen:** Score und Texte, Overlays, Nebel (der kommt pro Welt
    aus `main`), Himmel, Gebirge und Sterne. Mechanik, Timing, Größen und
    Golden Vectors bleiben unverändert.
11. **Architektur:** Farbableitung und Welten-Stile in `:core` (`BevelPaint`,
    Stil-Daten in `ScenePaint`, Masken), die Renderer in `:ui` und `:wear`
    rechnen nicht selbst. Die Kulisse wird als Parameter übergeben, **keine
    globale Variable** wie `protoScene` im Prototyp. Neue Tests in `:core`
    nach 8.4. Ein Screenshot-Werkzeug nach dem Muster von `TwistShots`
    (z. B. `BevelShots`) rendert die Welt-×-Twist-Matrix.
12. **Uhr (`:wear`):** Dottie als Kugel, Minen mit Kante, Blockfarben pro
    Welt, auf den Sandblöcken ohne Muster. Die **Zone** zeichnet die Uhr
    seit dem Nachzug genau wie das Telefon (`WearZone.kt`): im Raster der
    Bahn, zur Mitte hin größer, goldener Saum um den Kern, Kante in den
    Grastönen, Blätter in der WIESE und das Motiv der Welt samt
    Kern-Akzent in den anderen. Die Uhr hat eigene Szenerie
    (`WearScenery.kt`): Requisiten dort bekommen Bevel, wo sie groß genug
    sind. Seit dem Nachzug der Menüs (`WearTaster.kt`) tragen auch die
    Bedienflächen der Uhr den Look der Telefon-Menüs: Symbol-Taster der
    Startzeile (sinken beim Drücken ein), der Modus-Schalter als
    Kippschalter, das Game-Over-Panel, die gewählte Zeile im Wähler und
    TON: AN/AUS — Sand im Pixelrahmen, Schatten, Kante aus `BevelPaint`,
    verkleinert auf 1,5 dp Rand, 2 dp Schatten, 1 dp Kante.

## 1. Ziel

Dottie bekommt einen einheitlichen **Bevel-Look**: Jede Form bekommt eine
helle Kante oben links und eine dunkle Kante unten rechts, so als käme das
Licht von oben links. Alles bleibt flach, frontal und im bestehenden
Pixel-/Retro-Look (Flappy-Bird-Stil). Das Bild soll hochwertiger und
„drückbarer“ wirken, ohne am Spiel etwas zu ändern.

Das Prinzip gibt es schon im Code, nämlich in `drawZoneBlock` in
`ui/.../world/WorldRenderer.kt` und in `drawSandBevel` in
`ui/.../components/OverlayCloseButton.kt`. Es soll auf die übrigen
Spielelemente übertragen werden.

## 2. Nicht-Ziele (verbindlich)

- **Keine 2,5D-Optik:** keine Perspektive, keine gekippte Bahn, keine
  Extrusion und keine Isometrie.
- **Keine Schlagschatten:** Kein Element wirft einen Schatten auf den
  Himmel oder den Boden. Der Pixelschatten des Scores bleibt, wie er heute
  ist.
- **Keine weichen Verläufe, kein Anti-Aliasing, keine Transparenz-Effekte.**
  Alles bleibt in harten Pixelstufen.
- **Keine Änderung an Mechanik oder Layout:** Ring, Radius, Tempo,
  Zonenbreite, Trefferlogik und Größen bleiben gleich.
- **Keine neuen Formen:** Dottie bekommt keine Flügel, er bleibt ein Punkt.
  Die Minen behalten `TrapPaint.MINE`, Bäume, Büsche und Blumen behalten
  ihre heutige Form.
- **Boden:** In Schritt 1 bleibt die Form wie heute, nur mit Bevel und
  ohne Ziegelmuster. Welteigene Böden kommen erst in Schritt 2
  (Abschnitt 8).
- **Die Golden Vectors (`parity/golden-vectors.txt`) dürfen sich nicht
  ändern.** Das hier ist reine Darstellung.

## 3. Die Bevel-Regel

- **Licht** kommt immer von oben links.
- **Helle Kante:** oben und links, `light = mix(basis, Weiß, 0.35)`.
  Bei Dottie wird statt Weiß `SkinPaint.shine(skin)` verwendet.
- **Dunkle Kante:** unten und rechts, `dark = mix(basis, Outline #543847, 0.30)`.
  Wo es schon eine Schattenfarbe gibt, etwa die Schattenfarbe eines Skins
  oder `SandBevelDark`, gilt diese.
- **Reihenfolge wie in `drawZoneBlock`:** erst die dunkle Kante unten und
  rechts zeichnen, dann die helle Kante oben und links darüber. An den
  Ecken oben rechts und unten links gewinnt damit das Licht.
- **Kantenbreite:** eine Pixelzelle, bei großen Flächen wie Baumkronen und
  Büschen zwei. „Pixelzelle“ heißt das jeweilige `cell` bzw. `u` des
  Elements, auf ganze Pixel gerundet wie in `drawZoneBlock`.
- **Die Outline `#543847`** bleibt überall bestehen, die Kante liegt
  innerhalb davon.
- **Dunkle Musterzellen** bleiben unberührt, etwa Bienenstreifen, Kerne
  oder Pupillen. Als dunkel gilt eine Zelle, wenn der Mittelwert ihrer
  RGB-Kanäle unter 80 liegt. Aufgehellt würden sie grau.
- **Farben werden abgeleitet, nicht neu erfunden:** Die Kanten entstehen
  aus der jeweiligen Grundfarbe und funktionieren damit automatisch für
  alle Kulissen (`ScenePaint`) und alle Skins (`SkinPaint`).

**Ort der Logik:** Wie bei `SkinPaint` und `TrapPaint` rechnen die Renderer
nicht selbst. Die Farbableitung (mix, light, dark, Dunkel-Test und die
Kugel-Stufen aus Abschnitt 4) kommt in ein neues Objekt in `:core`, etwa
`BevelPaint` mit ARGB-Longs. Telefon/iOS (`:ui`) und die Uhr (`:wear`)
nutzen es gemeinsam. Diese Funktionen bekommen Unit-Tests in `:core`.

## 4. Dottie: Option „Kugel“

Grundlage ist das echte Sprite: `drawPixelCircle` in `ui/.../world/PixelShapes.kt`
und `drawTimingDot` → `drawBird` in `WorldRenderer.kt`. Das Raster ist
`GRID = 13`, `MID = 6`, die Kreismaske hat `RR = 6.25`, der Kontur-Ring
liegt bei `dist > RR - 1.1`, und die Grundfarbe jeder Zelle kommt aus
`SkinPaint.cell(...)`.

Für jede Zelle innerhalb der Kontur gilt, mit `dx = col - MID`,
`dy = row - MID` und `s = (dx + dy) / (RR * √2)` (−1 heißt ganz im Licht,
+1 ganz im Schatten):

| Bereich | Farbe |
|---|---|
| `s < -0.42` | `mix(zelle, shine, 0.55)` |
| `-0.42 ≤ s < -0.12` | `mix(zelle, shine, 0.22)` |
| `-0.12 ≤ s ≤ 0.55` | `zelle` (unverändert) |
| `s > 0.55` | `mix(zelle, Outline, 0.28)` |

Dunkle Musterzellen bleiben ausgenommen (siehe Abschnitt 3).

Glanz und Auge, bei Blick nach rechts, in Rasterzellen:
- Der Glanzpunkt bleibt `rect(2.5, 2.5, 2, 2, shine)`. Neu kommt ein
  weißer Kern darüber: `rect(2.5, 2.5, 1, 1, Weiß)`.
- Das Auge bleibt, wie es ist: weiß bei `(7.5, 3, 3.5, 4)`, Pupille bei
  `(9.5, 4, 1.5, 2)`, Kontur nur bei `needsEyeOutline`. Neu ist eine halbe
  Zeile `#D5DEE2` am unteren Rand des Augenweiß, also `(7.5, 6.5, 3.5, 0.5)`.
- Bei `facingLeft` wird alles gespiegelt, genau wie heute.

Die Kugel-Stufen gehören in den Zellfarben-Lambda des Vogels, nicht fest
in `drawPixelCircle`. Münzen und andere Nutzer von `drawPixelCircle`
bleiben unverändert. Die Skin-Vorschau in der Sammlung
(`CollectionOverlay`) soll aber dieselbe Kugel zeigen wie das Spiel.

**Alle Skins prüfen.** Besonders wichtig sind die hellen Skins (Koi, Chrom,
Ei, Pinguin), die gemusterten (Biene, Melone, Pilz, Karo, Galaxie,
Fußball), die animierten (Regenbogen, Neon, Holo, Disco) und die dunklen
(Onyx, Gewitter). Wenn die Stufen bei einem Skin das Muster zerstören,
lieber die Faktoren für alle Skins gemeinsam senken, statt Ausnahmen
einzelner Skins einzubauen.

> Alternative: die sanfte Kante (`docs/bevel-mockups/dot-sanft.png`),
> falls die Kugel verworfen wird. Dabei wird nur der erste Ring innerhalb
> der Kontur verändert, nicht die Fläche. Der Ring wird oben links hell
> mit `mix(zelle, Weiß, 0.35)` und unten rechts dunkel mit
> `mix(zelle, Outline, 0.30)`. Die Richtung kommt aus `±(dx+dy)/dist > 0.35`.

## 5. Weitere Elemente

| Element | Ort | Was |
|---|---|---|
| Bahn-Blöcke | `drawTrack` (WorldRenderer) | Bevel nach der Regel, Basis ist die Bahnfarbe der Kulisse. |
| Zone und Perfekt-Kern | `drawZoneBlock` | Ist schon bevelt. Nur prüfen, ob die Kantenbreite zu den neuen Blöcken passt. Keine Formänderung. |
| Minen / Bomben | `drawMineBody` (MineField.kt), `drawWearMine` | Sprite `TrapPaint.MINE`, `RIM`, `GLOSS` und das Lauflicht bleiben. Neu ist nur die Kante auf den Kugel-Pixeln: schwarz mit hell `#4E4656` und dunkel `#0A080C`, rot mit hell `#FF8A7E` und dunkel `#9E1F1C`. Die Farben gehören als Konstanten in `TrapPaint`. Siehe `bevel-detail-bomben.png`. |
| Wolken | `drawCloud` (PixelShapes.kt) | Oberkante in Weiß, Unterkante leicht dunkler als die Wolkenfarbe der Kulisse, ohne Outline wie heute. Wird auch in `CollectionOverlay` genutzt. |
| Bäume, Büsche, Blumen, Kakteen, Tannen, Türme, Wellen | `drawOutlinedBlocks`, `drawBlockParts`, `drawPixel*` | Bevel pro Block. Am besten zentral in `drawOutlinedBlocks` bzw. `drawBlockParts`, dann erben es alle Requisiten. Kronen und Büsche zwei Zellen breit. |
| Graskante am Boden | `drawGroundStrip` | Die Grasstreifen-Kacheln bekommen eine Kante. Der Sand bleibt flach, höchstens mit einer hellen Oberkante. |
| Uhr | `wear/.../WearRenderer.kt` (`drawWearTrack`, `drawWearMine`, `drawWearDot`, `drawWearPixelCircle`) | Dieselben Regeln, soweit sie auf der kleinen Fläche lesbar bleiben. Im Zweifel nur Dottie und die Minen. |

Nicht anfassen: den Score und die Texte (sie laufen über die Schrift
`Bytesized`), Overlays und Knöpfe (die haben schon Bevel), Nebel, Himmel
und die Backdrop-Ebenen (Gebirge, Sterne). Die einzige Ausnahme sind die
Galaxien in Schritt 2 (Abschnitt 8.3b).

## 6. Vorgehen und Prüfung

1. **Vorher-Screenshots** über `ui/src/jvmTest/.../ScreenshotRenderer.kt`
   erzeugen: `SHOTS_DIR=… ./gradlew :ui:jvmTest --rerun`, dazu
   `TwistShots.kt` und `CollectionShots.kt`.
2. `BevelPaint` in `:core` mit Tests anlegen.
3. Die Elemente in dieser Reihenfolge umstellen: Dottie, Minen, Bahn,
   Requisiten und Wolken, Boden, Uhr.
4. **Nachher-Screenshots** derselben Szenen erzeugen, dazu alle Kulissen
   und eine Skin-Galerie mit allen Skins in Spielgröße.
5. Tests laufen lassen:
   `./gradlew :core:jvmTest :ui:jvmTest testDebugUnitTest assembleDebug :wear:assembleDebug`.
   Die Golden Vectors müssen unverändert grün sein.
6. Performance: Dottie wird in jedem Frame gezeichnet. Die Kugel-Stufen
   einmal pro Skin und `frameKey` berechnen oder günstig halten, keine
   Allokationen pro Frame.

## 7. Abnahme

- Im Draft-PR stehen Vorher/Nachher-Bilder: Startbildschirm, Lauf mit
  Bomben und Lauflicht, Perfekt-Kern, Nebel, Game-Over, alle Kulissen,
  Skin-Galerie und Uhr.
- Dottie ist auf jedem Skin als Kugel erkennbar, Muster bleiben erhalten,
  und das Auge bleibt deutlich.
- Es gibt keine Schlagschatten, keine Perspektive und keine Verläufe, und
  kein Element hat sich in Größe oder Position verändert.
- Alle Tests sind grün, die Golden Vectors unverändert.
- Im PR steht eine Liste der Punkte, die ein Mensch am Gerät prüfen muss:
  Wirkung bei 60 fps, kleine Displays und die Uhr.

## 8. Schritt 2: Bahn-Blöcke und Boden pro Welt

Vorlage: `docs/bevel-mockups/welten-bloecke.png` und
`docs/bevel-mockups/welten-boden.png`. Auch diese Mockups zeigen die
Richtung, sie sind keine pixelgenaue Vorlage.

Heute sieht die Bahn in allen sechs Welten gleich aus, mit Sandblöcken
auch im Weltraum. Der Boden hat überall dieselbe Form, nur in anderen
Farben. Nach diesem Schritt passt jede Welt von der Bahn bis zum Boden
zusammen.

### 8.1 Grundregel: Material wechselt, Signal bleibt

- **Die normalen Bahn-Blöcke** bekommen pro Welt ein eigenes Material.
- **Die Zone bleibt in jeder Welt grün.** Der Blockkörper nutzt immer die
  Zonentöne aus `Palette.kt` (`GrassLight`, `GrassDark`, `GrassShine`,
  `GrassEdge`, dazu `CORE` für den Kern). Pro Welt wechseln nur ein
  kleines Motiv auf dem Block und der Akzent im Perfekt-Kern. Der goldene
  Saum (`ZoneCoreHalo`) bleibt überall gleich.
- **Die Minen bleiben in jeder Welt unverändert** (`TrapPaint`).
- **Der Bevel aus Schritt 1 gilt für alles.**
- **Kein Grün außerhalb der Zone.** Die einzige Ausnahme ist die Grasnarbe
  der Wiese (`LEGACY_ZONE_GREENS`), die heute schon als Ausnahme gilt.
- **Die Motive sind nur 1–3 Pixel groß.** Die Lesbarkeit von Zone und Kern
  geht immer vor.

### 8.2 Bahn-Blöcke

| Welt | Normaler Block | Zonen-Motiv | Kern-Akzent |
|---|---|---|---|
| WIESE | Sand wie heute (`#DED895`) | Blätter (wie heute) | Blüte rosa/gold (wie heute) |
| WUESTE | Sandstein `#E3B26A` mit waagrechter Fuge | 2–3 weiße Kaktusstacheln | Kaktusblüte `#FF5A8A` mit gelber Mitte |
| MEER | Treibholz-Planke `#B9844F` mit Maserungspunkten | Seerosenblatt mit Kerbe, ein Wassertropfen `#7FD8F0` | Seerose weiß mit rosa Mitte |
| BERG | Fels `#9AA0AA`, obere Kante als Schneekappe in Weiß | Moos mit Steinchen | Edelweiß weiß mit gelber Mitte |
| STADT | Betonplatte `#B9BCC4` mit zwei Nieten | LED-Kachel: 2×2 hellgrüne Punkte | weißes „Go“-Licht, 3×3 |
| WELTRAUM | Metallpanel `#C9D2E2` mit blauem Lämpchen `#7FD4FF` | hellgrüne Kristall-Facette (Diagonale) | leuchtende weiße Facette |

Die Farben sind Startwerte. Die Tests aus 8.4 entscheiden: Wenn eine
Farbe durchfällt, wird die Farbe angepasst und nicht der Test.

### 8.3 Boden

Die Bodenkante `ScenePaint.GROUND_TOP` bleibt in jeder Welt auf derselben
Höhe. Requisiten stehen darauf, und die Tod-Animation landet dort. Der
Boden bleibt statisch, ohne Animation.

| Welt | Oberkante | Füllung |
|---|---|---|
| WIESE | Grasnarbe als Bevel-Kacheln (Form wie heute) | Sand mit ein paar Kieseln, Schattenstreifen wie heute |
| WUESTE | Dünenkante als Pixel-Welle mit heller Oberkante | Sandstein in 3 Schichten mit versetzten Fugen. 3–4 Kiesel, halb im Sand eingesunken: nur die gerundete obere Hälfte (7×3, Licht oben links) schaut heraus. Keine schwebenden Quadrate. |
| MEER | Wellenkämme mit weißem Schaum | Wasser, nach unten in 3 Stufen dunkler. 4–5 Luftblasen als runde Pixel-Ringe (5×5, innen Wasser, ein weißes Glanzpixel oben links), darüber je eine kleine 2×2-Begleitblase. Keine einzelnen Pixel, die wirken wie Kratzer. |
| BERG | Schneedecke mit Eiszapfen | Felsblöcke, versetzt wie eine Mauer, jeder Block bevelt |
| STADT | Bordsteine mit Fugen | Asphalt mit gestrichelter gelber Mittellinie und einem Gully |
| WELTRAUM | **kein Boden, wie heute** | — |

Der Weltraum bleibt bewusst die einzige Welt ohne Boden. Ein Mondboden
mit Kratern ist im Mockup nur als Option gezeigt und wird **nicht**
umgesetzt.

### 8.3a Requisiten, die dazu passen müssen

Zwei Requisiten wirken heute als Kästen und sollen zum neuen Boden passen:

- **FELS (Wüste):** Statt gestapelter Rechtecke (`ROCK_PARTS`) ein
  gerundeter Findling mit Outline, Licht oben links, dunkler Unterseite
  und 1–2 Riss-Pixeln. Daneben liegt ein kleiner Kiesel (8×5). Farben wie
  bisher: `light #C4A87C`, `body #A88860`, `dark #8A6A4A`, Riss `#6E5238`.
- **WELLE (Meer):** Statt drei gestapelter Kästen ein Brecher mit
  eingerollter Krone: blauer Körper (`#2E86D8`, Licht `#7FC8F0`, Schatten
  `#1F5FA8`), weiße Schaumkante oben, 2 Gischt-Pixel vor der Krone. Der
  Fuß sitzt sichtbar auf der Wasserlinie und wird nicht vom Boden
  verdeckt.

Beide werden am besten als Pixel-Maske gezeichnet, wie `TrapPaint.MINE`
(Zeilen aus Zeichen in `:core`, gezeichnet vom Renderer), statt als
Rechteck-Liste. Vorlage: `alle-welten.png`, Wüste und Meer.

### 8.3b Galaxien im Weltraum

Die zwei Galaxien in `drawGalaxy` (`ui/.../world/Backdrop.kt`) bleiben an
ihrem Platz und behalten ihre Form: oben links in Blau (`colors[4]`
`#7FA8E8`), unten rechts in Rosé (`colors[5]` `#E89AB8`), zwei Arme, 40
Schritte, gleiche Drehung. Geändert wird nur, wie sie verblassen.

Heute werden die Arme nach außen stufenlos durchsichtig (`alpha`).
Rosé halbtransparent über Dunkelblau wird ein schmutziges Grau-Lila. Die
Staub-Pixel in Kernfarbe mit Transparenz wirken wie graubeige Flecken
oder Bildrauschen.

Neu:
- **Arme in drei festen, deckenden Stufen statt `alpha`:** innen
  (`t < 0.35`) die volle Armfarbe, in der Mitte (`t < 0.7`)
  `mix(arm, Himmel, 0.35)`, außen `mix(arm, Himmel, 0.62)`. „Himmel“ ist
  die aktuelle Himmelsstufe des Weltraums, damit es auf jeder Stufe passt.
- **Staub nur auf der inneren Hälfte** (`t < 0.5`), deckend in
  `mix(arm, Weiß, 0.6)` statt in Kernfarbe mit Transparenz.
- **Der Kern-Schimmer** (heute das Kreuz mit `alpha = 0.5`) wird deckend in
  der mittleren Armstufe gezeichnet, der helle Kern darüber bleibt, wie er
  ist.
- **Farbwerte in `:core`:** Die Mischwerte gehören zu `BevelPaint`,
  damit sie testbar sind. Ein Test prüft, dass keine der Stufen dem
  Zonengrün nahekommt (`MIN_ZONE_DISTANCE`).

Vorlage: `alle-welten.png`, Weltraum.

### 8.4 Umsetzung

- **Daten statt Sonderlogik:** In `:core` → `ScenePaint` bekommt jede
  `Scene` eine Beschreibung ihrer Bahn, etwa `TrackStyle` mit Blockfarbe,
  Blockmuster (`GLATT`, `FUGE`, `PLANKE`, `SCHNEEKAPPE`, `NIETEN`,
  `LAEMPCHEN`), Zonen-Motiv und Kern-Akzent. `Ground` bekommt einen Stil
  für die Oberkante (`NARBE`, `DUENE`, `WELLE`, `SCHNEE`, `BORDSTEIN`) und
  einen für die Füllung (`KIESEL`, `SCHICHTEN`, `BLASEN`, `FELSMAUER`,
  `ASPHALT`). Die Namen sind Vorschläge, der Stil des Codes geht vor.
  Keine Kulisse darf Sonderfälle im Renderer brauchen.
- **Renderer:** `drawTrack` und `drawZoneBlock` bekommen den Stil der
  aktiven Kulisse und zeichnen danach, ebenso `drawGroundStrip`.
  Telefon und iOS sind derselbe Code.
- **Uhr (`WearRenderer`):** auf den Sandblöcken nur die Blockfarbe pro
  Welt, keine Muster, weil die Fläche zu klein ist. Die Zone dagegen wie
  am Telefon, mit Motiv (`WearZone.kt`, siehe Abschnitt 0 Punkt 12).
- **Sammlung:** Die Kulissen-Vorschau in `CollectionOverlay` zeigt die
  neuen Blöcke und Böden mit. Kulissen sind das, was dort verkauft wird.
- **Tests in `:core` (`ScenePaintTest`) erweitern:**
  - Jede Blockfarbe jeder Welt hebt sich von allen 7 Himmelsstufen dieser
    Welt ab, nach demselben Maß wie `MIN_SKY_SIGNAL_DISTANCE`.
  - Keine Block-, Motiv- oder Bodenfarbe außerhalb der Zone liegt näher
    als `MIN_ZONE_DISTANCE` am Zonengrün. Die Ausnahme ist nur
    `LEGACY_ZONE_GREENS` in der WIESE.
  - Der Zonenkörper nutzt in jeder Welt exakt die Zonentöne.
  - `GROUND_TOP` ist unverändert.
- Die Golden Vectors bleiben unverändert.

### 8.5 Abnahme für Schritt 2

- Im Draft-PR stehen Screenshots aller 6 Welten mit Bahn, Zone, Kern,
  Minen und Boden, jeweils bei Himmelsstufe 0 und 6.
- Zone und Kern sind in jeder Welt auf den ersten Blick als „hier
  tippen“ erkennbar.
- Die Bodenkante liegt in jeder Welt auf derselben Höhe wie vorher.
- Die neuen Tests sind grün, die Golden Vectors unverändert.

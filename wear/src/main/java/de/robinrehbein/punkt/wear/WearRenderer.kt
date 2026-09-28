package de.robinrehbein.punkt.wear

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import de.robinrehbein.punkt.game.BevelPaint
import de.robinrehbein.punkt.game.DeathCause
import de.robinrehbein.punkt.game.FogPaint
import de.robinrehbein.punkt.game.FogSpeck
import de.robinrehbein.punkt.game.GamePhase
import de.robinrehbein.punkt.game.SceneId
import de.robinrehbein.punkt.game.ScenePaint
import de.robinrehbein.punkt.game.SkinPaint
import de.robinrehbein.punkt.game.SkinState
import de.robinrehbein.punkt.game.SoundBank
import de.robinrehbein.punkt.game.SoundSetId
import de.robinrehbein.punkt.game.TimingGame
import de.robinrehbein.punkt.game.TrackMarks
import de.robinrehbein.punkt.game.TrapPaint
import de.robinrehbein.punkt.game.Twist
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

// ===== Palette 1:1 aus ui/.../world/Palette.kt (bis v2.27
// GameOverlays.kt und TimingGameScreen.kt) übernommen, damit der Wear-Prototyp optisch zur
// Telefon-Version passt, ohne eine Abhängigkeit auf :app zu brauchen. =====

// Himmel, Kulisse und Nebel kommen aus ScenePaint (:core), je nach
// gewählter Welt. Die Bahn-Blöcke tragen das Material der Welt
// (ScenePaint.track), die Zone bleibt überall grün wie am Telefon:
// Worauf getippt wird, sieht überall gleich aus.

internal val WearOutlineColor = Color(0xFF543847)
internal val WearGrassLight = Color(0xFF9DE85A)
internal val WearGrassDark = Color(0xFF74BF2E)

/**
 * Gold-Akzent (DotBody am Phone) für Overlay-Texte und der Glanzton der
 * Medaillen-Münze. Der Vogel selbst zeichnet nicht mehr mit diesen
 * Konstanten, sondern mit dem gewählten Skin (WearDotSkin) — KLASSIK
 * trägt dieselben Werte.
 */
internal val WearDotBody = Color(0xFFFFD847)
internal val WearDotShine = Color(0xFFFFF3B8)

/** Raster-Auflösung für den Pixel-Vogel, wie GRID in ui/.../world/PixelShapes.kt. */
private const val WEAR_GRID = 13f

/**
 * Mario-Tod, Werte 1:1 aus TimingGameScreen.kt: Nach dem Todes-Freeze
 * hüpft der Vogel mit dieser Anfangsgeschwindigkeit nach oben und fällt
 * dann mit der Gravitation unten aus dem Bild — beides in Bildhöhen pro
 * Sekunde(²), skaliert also automatisch aufs kleine Display.
 */
private const val WEAR_DEATH_HOP_SPEED = 1.6f
private const val WEAR_DEATH_GRAVITY = 6f

/**
 * Während des Hüpfers dreht sich der Vogel um 180° auf den Rücken und
 * fällt kopfüber — die Drehung ist am Scheitelpunkt (~0,27s) fertig.
 */
private const val WEAR_DEATH_FLIP_SECONDS = 0.3f

/** Vogelradius im Verhältnis zur kleineren Displaykante. */
private const val WEAR_DOT_RADIUS_SHARE = 0.075f

/** Segmentzahl der Bahn — dieselbe wie am Phone (drawTrack). */
internal const val WEAR_TRACK_SEGMENTS = 60

/**
 * Blockbreiten als Vielfaches des Segment-Abstands. Die Werte stammen aus
 * der Phone-Bahn: Dort liegen bei 60 Segmenten rund 41 Pixel zwischen den
 * Mittelpunkten, die neutralen Blöcke sind 30 Pixel breit (0,74) und die
 * Zonen-Blöcke 50 (1,23). Genau daraus entsteht der gewollte Unterschied —
 * die Kette behält ihre Lücken, die Zone überlappt sich zu einem
 * durchgehenden Band.
 *
 * Warum am Abstand statt an einer festen Zellgröße (so war es vorher):
 * `floor(d / 220)` ergibt auf einem 480er Uhren-Display die Zellgröße 2,
 * damit waren die Blöcke nur 6 bzw. 14 Pixel breit — bei 29 Pixeln
 * Abstand. Die Kette zerfiel in Staubkörner und die Zone blieb eine Reihe
 * einzelner Quadrate statt eines Bandes. Am Abstand gerechnet stimmen die
 * Proportionen auf jeder Displaygröße von selbst.
 */
private const val WEAR_SEG_NEUTRAL = 0.74f
private const val WEAR_SEG_ZONE = 1.23f

/** Farbkern eines Sandblocks im Verhältnis zum Block — am Phone 1.8/3. */
private const val WEAR_CORE_NEUTRAL = 0.6f

/**
 * Zeichnet die komplette Spielwelt für das runde Wear-Display: Himmel je
 * nach Score-Stufe, die Kulisse der Welt am Rand (siehe WearScenery.kt),
 * die Bahn als Perlenkette, den Nebel der Welt und den Vogel — dazu die
 * Effekte aus [fx]: Wackeln und Blitz beim Tod, Goldring bei jeder
 * Freischaltung, Funken bei BOOM!.
 *
 * [hour] und [month] kommen von der Geräte-Uhr (Controller): TAGESZEIT
 * und JAHRESZEIT ziehen daraus ihr Kleid. [scene] ist die gewählte Welt —
 * auf der Uhr gewählt oder über den Abgleich vom Telefon.
 * [dotWobble] schiebt den Vogel seitlich, in Vogel-Pixeln: das Wackeln
 * nach einem Tap daneben in READY (siehe WearNotYet.wobble).
 */
internal fun DrawScope.drawWearWorld(
    game: TimingGame,
    skin: WearDotSkin,
    hour: Int,
    month: Int,
    scene: SceneId = SceneId.WIESE,
    dotWobble: Float = 0f,
    fx: WearFx? = null
) {
    val d = size.minDimension
    val cx = size.width / 2f
    val cy = size.height / 2f
    val kulisse = ScenePaint.of(scene)

    // Wackeln beim Tod, wie am Telefon — auf die Bildgröße umgerechnet.
    val shake = fx?.shakeTime ?: 0f
    val strength = shake * WearFx.SHAKE_SHARE * d / 0.45f
    val sx = if (shake > 0f) sin(shake * 91f) * strength else 0f
    val sy = if (shake > 0f) sin(shake * 77f) * strength else 0f

    translate(sx, sy) {
        val sky = Color(ScenePaint.skyFor(scene, game.score))
        drawRect(color = sky, topLeft = Offset(-20f, -20f), size = Size(size.width + 40f, size.height + 40f))

        // Radius proportional zu minDimension statt zur Bildhöhe wie am Phone —
        // auf der Uhr sind Breite und Höhe (fast) identisch, aber minDimension
        // ist robust gegenüber eckigen/ovalen Displays.
        val radius = d * WEAR_TRACK_RADIUS_SHARE
        val dotRadius = d * WEAR_DOT_RADIUS_SHARE
        val cell = wearCell(d)
        val trackOuter = radius + round(2f * PI.toFloat() * radius / WEAR_TRACK_SEGMENTS * WEAR_SEG_ZONE) / 2f
        drawWearScenery(kulisse, game.elapsed, cx, cy, trackOuter, cell)
        drawWearTrack(game, cx, cy, radius, scene)
        // Die Nebelbank liegt über der Bahn und unter dem Vogel. Verdeckt wird
        // der Vogel nicht vom Band, sondern von der Engine (isDotVisible) —
        // das Band zeigt nur, wo das passiert. Farben und Partikel sind die
        // des Nebels der Welt (ScenePaint.of(scene).fog).
        drawFogBand(game, cx, cy, radius, dotRadius, kulisse.fog)
        // In OVER ist der Vogel bereits unten aus dem Bild gefallen
        // (Mario-Hüpfer in der DYING-Phase) — die Bahn bleibt leer, bis der
        // nächste Lauf startet. Am Phone regelt das fx.deathTime genauso.
        if (game.phase != GamePhase.OVER && game.isDotVisible) {
            drawWearDot(game, cx, cy, radius, d, skin, hour, month, dotWobble)
        }
        if (fx != null) {
            if (fx.celebrateTime > 0f) drawWearBurst(fx.celebrateTime, cx, cy, radius, cell)
            drawWearTrapBoom(game, fx.deathTime, cx, cy, radius, cell)
        }
    }

    // Weißer Blitz beim Aufprall, über allem und ohne Wackeln.
    val flash = fx?.flashAlpha ?: 0f
    if (flash > 0f) drawRect(color = Color.White.copy(alpha = flash.coerceAtMost(1f)))
}

/**
 * Freischalt-Feier wie drawUnlockBurst am Telefon: zwei Ringe goldener
 * Pixel steigen von der Bahn nach außen und verblassen, am Anfang liegt
 * ein kurzer Goldschimmer über dem Bild.
 */
private fun DrawScope.drawWearBurst(timeLeft: Float, cx: Float, cy: Float, radius: Float, cell: Float) {
    val progress = 1f - (timeLeft / WearFx.CELEBRATE_SECONDS).coerceIn(0f, 1f)
    val fade = 1f - progress
    val glow = (fade - 0.66f).coerceAtLeast(0f) * 0.9f
    if (glow > 0f) drawRect(color = WearDotBody.copy(alpha = glow))
    val sparks = 20
    for (ring in 0 until 2) {
        val ringProgress = (progress - ring * 0.15f).coerceIn(0f, 1f)
        if (ringProgress <= 0f) continue
        // Auf der Uhr steigen die Ringe nach INNEN: Außen schneidet das
        // runde Display sie nach wenigen Pixeln ab.
        val burstRadius = radius * (1.05f - ringProgress * 0.6f)
        val block = cell * (2.5f - ring) * fade
        if (block <= 0f) continue
        val color = (if (ring == 0) WearDotBody else WearDotShine).copy(alpha = fade)
        for (k in 0 until sparks) {
            val a = (k.toFloat() / sparks + ring * 0.025f) * (2f * PI.toFloat())
            drawRect(
                color = color,
                topLeft = Offset(cx + cos(a) * burstRadius - block / 2f, cy + sin(a) * burstRadius - block / 2f),
                size = Size(block, block)
            )
        }
    }
}

/**
 * Die Explosion beim Tippen in die Bomben, wie drawTrapBoom am Telefon:
 * zwanzig Funken, erst gelb/orange, dann rot/grau, dazu ein schrumpfender
 * heller Kern. Nur bei Todesursache TRAP, [time] = Sekunden seit dem Tod;
 * die Funken beginnen [WearFx.BOOM_DELAY] danach, zugleich mit dem Platzen.
 */
private fun DrawScope.drawWearTrapBoom(
    game: TimingGame,
    time: Float,
    cx: Float,
    cy: Float,
    radius: Float,
    cell: Float
) {
    if (game.lastDeathCause != DeathCause.TRAP) return
    val t = time - WearFx.BOOM_DELAY
    if (time < 0f || t < 0f || t >= WearFx.BOOM_SECONDS) return
    val x = cx + cos(game.angle) * radius
    val y = cy + sin(game.angle) * radius
    val q = (t / WearFx.BOOM_SECONDS).coerceIn(0f, 1f)
    val sparks = 20
    for (i in 0 until sparks) {
        val odd = i % 2 == 1
        val a = i.toFloat() / sparks * (2f * PI.toFloat()) + (if (odd) 0.2f else 0f)
        val dist = cell * (3f + q * (if (odd) 11f else 15f))
        val s = max(1f, (cell * (4.5f - q * 2.5f)).roundToInt().toFloat())
        val color = if (q < 0.5f) {
            if (odd) WearDotBody else WearBoomOrange
        } else {
            if (odd) Color(TrapPaint.RED) else WearBoomSmoke
        }
        drawRect(
            color = color,
            topLeft = Offset(
                (x + cos(a) * dist - s / 2f).roundToInt().toFloat(),
                (y + sin(a) * dist - s / 2f).roundToInt().toFloat()
            ),
            size = Size(s, s)
        )
    }
    val core = (cell * 6f * (1f - q)).roundToInt().toFloat()
    if (core >= 1f) {
        drawRect(WearDotShine, Offset((x - core / 2f).roundToInt().toFloat(), (y - core / 2f).roundToInt().toFloat()), Size(core, core))
    }
}

/** Funkenfarben der Bomben wie am Telefon (DotShade, Rauchgrau). */
private val WearBoomOrange = Color(0xFFF5A623)
private val WearBoomSmoke = Color(0xFF8A8A8A)

/**
 * Die Kreisbahn als Kette blockiger Zellen — dieselbe Zeichnung wie
 * drawTrack am Phone, nur dass die Blockgrößen hier aus dem
 * Segment-Abstand folgen (siehe WEAR_SEG_*), damit die Proportionen auf
 * dem kleinen Display erhalten bleiben.
 *
 * Die Falle (Twist FAKE) ist wie am Telefon eine Kette aus Minen aus
 * [TrapPaint], mit demselben roten Lauflicht (siehe [wearTrapMines]).
 * Wo eine Mine liegt, bleibt die Bahn frei.
 *
 * Die Sandblöcke tragen die Farben der Welt [scene] aus
 * [ScenePaint.track] (Fläche, helle und dunkle Kante), aber anders als
 * am Telefon ohne Muster und ohne Zonen-Motive: Auf rund acht
 * Bildpunkten Blockfläche bliebe von Fuge oder Niete nur ein
 * verirrter Pixel, der wie Schmutz aussähe (docs/bevel-look.md,
 * Abschnitt 0 Punkt 12). Die Zone bleibt, wie sie ist.
 */
private fun DrawScope.drawWearTrack(
    game: TimingGame,
    cx: Float,
    cy: Float,
    radius: Float,
    scene: SceneId
) {
    val segments = WEAR_TRACK_SEGMENTS
    // Abstand zwischen zwei Segment-Mittelpunkten. Auf ganze Pixel
    // gerundet, damit die Blöcke ihre harten Kanten behalten.
    val spacing = wearTrackSpacing(radius)
    val zoneOuter = round(spacing * WEAR_SEG_ZONE).coerceAtLeast(4f)
    val neutralOuter = wearNeutralOuter(radius)
    val neutralInner = wearNeutralInner(radius)
    val track = ScenePaint.track(scene)
    val trackBlock = Color(track.block)
    val trackLight = Color(track.light)
    val trackDark = Color(track.dark)
    val trackEdge = wearTrackEdge(neutralInner)

    val zoneHalf = game.effectiveZoneHalf()
    // Die Fallenbreite kommt aus der Engine (fakeZoneHalf()), die Minen
    // verteilen sich darüber. Den Kern misst wearZoneSlots.
    val fakeHalf = game.fakeZoneHalf()
    val mines = wearTrapMines(game, segments, zoneHalf)
    val minePx = wearMinePixel(wearMineDistance(game, segments, radius), zoneOuter)
    val mineHalf = wearMineHalfExtent(minePx)
    val mineCenters = mines.map { Offset(cx + cos(it.angle) * radius, cy + sin(it.angle) * radius) }

    for (k in 0 until segments) {
        val a = k.toFloat() / segments * (2f * Math.PI.toFloat())
        val px = cx + cos(a) * radius
        val py = cy + sin(a) * radius

        // Die Zonenblöcke kommen nach der Schleife (wearZoneSlots) und
        // liegen damit über dem Sand daneben — wie am Telefon.
        val inZone = abs(TimingGame.wrapToPi(a - game.zoneCenter)) <= zoneHalf
        if (inZone) continue

        val inFake = game.hasFakeZone &&
            abs(TimingGame.wrapToPi(a - game.fakeZoneCenter)) <= fakeHalf
        // Auf der Falle liegen die Minen statt der Blöcke. Liegt sie auf
        // der Zone, gewinnt die Zone: Grün bleibt Grün.
        if (inFake) continue

        val outer = neutralOuter
        // Die Minen liegen nicht auf dem Segment-Raster: Ein Sandblock,
        // den eine Mine berühren würde, bleibt ebenfalls frei.
        if (wearBlockHitsMine(px, py, outer / 2f, mineCenters, mineHalf)) continue
        val inner = neutralInner

        drawRect(
            color = WearOutlineColor,
            topLeft = Offset(px - outer / 2f, py - outer / 2f),
            size = Size(outer, outer)
        )
        val innerTopLeft = Offset(px - inner / 2f, py - inner / 2f)
        // SPIEGEL und TEMPO (ab v2.36): Auf der Uhr trägt der Block nur
        // die Farbe der Markierung — für Pfeil oder Pause-Striche ist
        // die Fläche mit rund acht Pixeln zu klein. Welche Farbe, sagt
        // TrackMarks in :core, wie am Telefon.
        val mark = TrackMarks.at(game, a, segments)
        if (mark != null) {
            val face = Color(TrackMarks.face(mark))
            wearBevelRect(
                face, innerTopLeft, Size(inner, inner), trackEdge,
                Color(BevelPaint.light(TrackMarks.face(mark))),
                Color(BevelPaint.dark(TrackMarks.face(mark)))
            )
        } else {
            wearBevelRect(trackBlock, innerTopLeft, Size(inner, inner), trackEdge, trackLight, trackDark)
        }
    }

    // Die Zone wie am Telefon: im Raster der Bahn, zur Mitte hin größer,
    // goldener Saum um den Kern, Kante und das Motiv der Welt (WearZone.kt).
    drawWearZone(wearZoneSlots(game, cx, cy, radius, spacing, zoneOuter), track.motif)

    // Erst alle Ränder, dann alle Kugeln: Benachbarte Minen teilen sich
    // ihren Rand, die Kugeln berühren sich nie (wearMinePixel).
    for (c in mineCenters) drawWearMine(c.x, c.y, minePx, rimOnly = true)
    // Das Lauflicht läuft nur, solange der Lauf läuft — im Todes-Freeze
    // bleibt es stehen, weil die Zonenuhr steht.
    mineCenters.forEachIndexed { i, c ->
        drawWearMine(c.x, c.y, minePx, rimOnly = false, red = mines[i].red)
    }
}

/**
 * Kantenbreite der Bahn-Blöcke bei einer Blockfläche von [inner]
 * Bildpunkten: ein Viertel davon, auf ganze Pixel gerundet. Am Telefon
 * ist die Fläche vier Rasterstufen breit und die Kante eine Stufe
 * (TrackBlock.unit) — dasselbe Verhältnis hält die Uhr. Die Kontur
 * drumherum taugt hier nicht als Maß: Bei 13 zu 8 Pixeln wäre sie
 * 2,5 Pixel und die Kante gerundet so breit, dass die Fläche flach
 * bliebe.
 */
internal fun wearTrackEdge(inner: Float): Float = wearBevelEdge(inner / 4f)

/** Radius der Bahn im Verhältnis zur kürzeren Displayseite. */
internal const val WEAR_TRACK_RADIUS_SHARE = 0.38f

/** Abstand zweier Segment-Mittelpunkte auf der Bahn mit [radius]. */
internal fun wearTrackSpacing(radius: Float): Float = 2f * Math.PI.toFloat() * radius / WEAR_TRACK_SEGMENTS

/**
 * Farbfläche eines Sandblocks außerhalb der Zone, auf ganze Pixel
 * gerundet. Eine Funktion statt einer Rechnung in [drawWearTrack], damit
 * der Test genau das prüft, was gezeichnet wird: Mit nachgerechneten
 * Literalen bliebe er grün, wenn sich eine Konstante ändert.
 */
internal fun wearNeutralInner(radius: Float): Float =
    round(wearNeutralOuter(radius) * WEAR_CORE_NEUTRAL).coerceAtLeast(1f)

/** Ganzer Sandblock außerhalb der Zone, samt Kontur (siehe [wearNeutralInner]). */
internal fun wearNeutralOuter(radius: Float): Float =
    round(wearTrackSpacing(radius) * WEAR_SEG_NEUTRAL).coerceAtLeast(2f)

// ===== Minen der Falle (Plan 3.4) =====

/**
 * Winkel der Minen der aktuellen Falle auf der Bahn.
 *
 * Die Zahl kommt aus [TrapPaint.count] mit der Grundbreite
 * [TimingGame.zoneHalfWidth] und dem Winkel eines Bahn-Blocks als Zelle;
 * sie rundet ab und bleibt unter PULS stehen. Die Minen liegen gleichmäßig
 * über die Breite von [TimingGame.fakeZoneHalf] verteilt (je eine in der
 * Mitte von n gleich breiten Feldern). Unter PULS atmet nur die Zone, die
 * Kette steht. Minen, die in der echten Zone ([zoneHalf]) lägen, entfallen.
 * Keine Zufallszahl.
 */
internal fun wearTrapMineAngles(game: TimingGame, segments: Int, zoneHalf: Float): List<Float> =
    wearTrapMines(game, segments, zoneHalf).map { it.angle }

/** Eine Mine der Falle: Winkel auf der Bahn und ob das Lauflicht gerade auf ihr steht. */
internal data class WearMine(val angle: Float, val red: Boolean)

/**
 * Die Minen samt rotem Lauflicht, wie am Telefon: Welche Minen rot sind,
 * sagt [TrapPaint.redMask] im Takt von [TrapPaint.LIGHT_STEP_SECONDS] an
 * der Zonenuhr ([TimingGame.zoneAge]), die Laufrichtung
 * [TrapPaint.direction] — beides ohne Zufallszahl. Lauflicht-Index 0 ist
 * bei Richtung +1 die erste Mine in Bahnrichtung, sonst die letzte.
 */
internal fun wearTrapMines(game: TimingGame, segments: Int, zoneHalf: Float): List<WearMine> {
    if (!game.hasFakeZone) return emptyList()
    val cell = 2f * PI.toFloat() / segments
    val count = TrapPaint.count(game.zoneHalfWidth, cell)
    val fakeHalf = game.fakeZoneHalf()
    val pitch = 2f * fakeHalf / count
    val mask = TrapPaint.redMask(count, floor(game.zoneAge / TrapPaint.LIGHT_STEP_SECONDS).toInt())
    val forward = TrapPaint.direction(game.fakeZoneCenter) > 0
    val mines = ArrayList<WearMine>(count)
    for (i in 0 until count) {
        val angle = TimingGame.wrapTwoPi(game.fakeZoneCenter - fakeHalf + (i + 0.5f) * pitch)
        if (abs(TimingGame.wrapToPi(angle - game.zoneCenter)) <= zoneHalf) continue
        val light = if (forward) i else count - 1 - i
        mines.add(WearMine(angle, mask[light]))
    }
    return mines
}

/**
 * Abstand zweier benachbarter Minen auf dem Bild (Sehne, in Bildpunkten).
 * Die Falle atmet unter PULS nicht ([TimingGame.fakeZoneHalf]), der
 * Abstand steht also die ganze Runde.
 */
internal fun wearMineDistance(game: TimingGame, segments: Int, radius: Float): Float {
    val count = TrapPaint.count(game.zoneHalfWidth, 2f * PI.toFloat() / segments)
    val pitch = 2f * game.fakeZoneHalf() / count
    return 2f * radius * sin(pitch / 2f)
}

/** Breite des hellen Rands in Bildpunkten bei Sprite-Pixeln der Kante [px]. */
internal fun wearMineRim(px: Int): Int = (px * 0.6f).roundToInt().coerceAtLeast(1)

/** Halbe Kantenlänge einer Mine samt Rand, von der Mitte gemessen. */
internal fun wearMineHalfExtent(px: Int): Float = TrapPaint.MINE_SIZE * px / 2f + wearMineRim(px)

/**
 * Berühren sich zwei Kugeln mit Sprite-Pixeln [px] im Abstand [distance]
 * nicht? Zwischen ihnen bleibt mindestens ein Bildpunkt; die hellen Ränder
 * dürfen sich teilen, sie liegen unter den Kugeln. Das Sprite ist ein
 * 5×5-Kern mit vier Zacken: schräg begrenzt der Kern (`√2 · 5 px`), längs
 * der Achsen die Zacken (`7 px`).
 */
internal fun wearMinesFit(px: Int, distance: Float): Boolean {
    val diagonal = sqrt(2f) * 5f * px
    val axis = (TrapPaint.MINE_SIZE * px).toFloat()
    return distance >= max(diagonal, axis) + 1f
}

/**
 * Sprite-Pixelmaß der Minen: höchstens so groß, wie ein Zonenblock
 * ([zoneOuter]) es hergibt, und nie so groß, dass sich benachbarte Kugeln
 * im Abstand [distance] berühren. Mindestens 1.
 */
internal fun wearMinePixel(distance: Float, zoneOuter: Float): Int {
    var px = (zoneOuter / TrapPaint.MINE_SIZE).roundToInt().coerceAtLeast(1)
    while (px > 1 && !wearMinesFit(px, distance)) px--
    return px
}

/**
 * Überdeckt ein Bahn-Block mit Mitte ([bx], [by]) und halber Kante
 * [blockHalf] eine der Minen? Ein Bildpunkt Luft zählt mit.
 */
internal fun wearBlockHitsMine(
    bx: Float,
    by: Float,
    blockHalf: Float,
    mineCenters: List<Offset>,
    mineHalf: Float
): Boolean {
    val reach = blockHalf + mineHalf + 1f
    return mineCenters.any { abs(it.x - bx) < reach && abs(it.y - by) < reach }
}

/**
 * Eine Mine aus [TrapPaint.MINE], mittig auf ([cx], [cy]). [rimOnly]: nur
 * der helle Rand um jeden gesetzten Pixel, sonst Kugel und Glanz. [red]:
 * Das Lauflicht steht gerade auf dieser Mine.
 *
 * Die Kugel-Pixel bekommen ihre Kante aus [BevelPaint.mineEdge] (vorberechnet) — hell
 * oben links, dunkel unten rechts, wie am Telefon. Das Sprite selbst,
 * Rand, Glanz und Lauflicht bleiben unverändert.
 */
private fun DrawScope.drawWearMine(cx: Float, cy: Float, px: Int, rimOnly: Boolean, red: Boolean = false) {
    val u = px.toFloat()
    val n = TrapPaint.MINE_SIZE
    val ox = (cx - u * n / 2f).roundToInt().toFloat()
    val oy = (cy - u * n / 2f).roundToInt().toFloat()
    val rim = wearMineRim(px).toFloat()
    val rimColor = Color(TrapPaint.RIM)
    val gloss = Color(TrapPaint.GLOSS)
    for (r in 0 until n) {
        val row = TrapPaint.MINE[r]
        for (k in 0 until n) {
            val ch = row[k]
            if (ch == '.') continue
            if (rimOnly) {
                drawRect(
                    color = rimColor,
                    topLeft = Offset(ox + k * u - rim, oy + r * u - rim),
                    size = Size(u + 2f * rim, u + 2f * rim)
                )
            } else {
                drawRect(
                    color = if (ch == 'W') gloss else Color(BevelPaint.mineEdge(r, k, red)),
                    topLeft = Offset(ox + k * u, oy + r * u),
                    size = Size(u, u)
                )
            }
        }
    }
}

// ===== Nebelband (Twist NEBEL, Plan 3.3 und 8.6 Punkt 15) =====

/**
 * Winkel, an denen das Nebelband Blöcke setzt, vom Anfang der Nebelbank
 * bis zu ihrem Ende, im Abstand von höchstens [step] (Radiant). Leer, wenn
 * gerade kein Nebel liegt: nur in RUNNING (beim Tod verschwindet das
 * Band) und nur unter NEBEL.
 *
 * Die Grenzen kommen allein aus der Engine ([TimingGame.fogStart],
 * [TimingGame.fogEnd], relativ zur Zone in Laufrichtung) — dieselben, an
 * denen [TimingGame.isDotVisible] den Vogel verbirgt.
 */
internal fun wearFogAngles(game: TimingGame, step: Float): List<Float> {
    if (game.phase != GamePhase.RUNNING || Twist.GHOST !in game.activeTwists) return emptyList()
    if (!(step > 0f)) return emptyList()
    val from = game.fogStart()
    val to = game.fogEnd()
    if (to < from) return emptyList()
    val n = ceil((to - from) / step).toInt().coerceAtLeast(1)
    return List(n + 1) { i ->
        val rel = from + (to - from) * i / n
        TimingGame.wrapTwoPi(game.zoneCenter + game.direction * rel)
    }
}

/**
 * Das Nebelband: eine Reihe heller Pixel-Blöcke über der Bahn, etwas
 * dicker als der Vogel. Keine Wolkenform, kein Einrollen — auf dem
 * kleinen Display zählt nur, dass man sieht, wo der Vogel verschwindet.
 * [dotRadius] ist der Vogelradius in Bildpunkten.
 *
 * Farben und Partikel sind die des Nebels der gewählten Welt ([fog], aus
 * ScenePaint): Rand mit Bevel wie die Telefon-Wolke (oben und links
 * deren Lichtkante, unten und rechts ihre Unterkante), darin die Tupfer-
 * und die Flächenfarbe. Die Partikel (Pollen, Staub, Gischt, Schneekreuze, Ruß, Sterne)
 * liegen verstreut im Kern, ihre Lage hängt an der Zone ([wearFogSpecks]).
 */
private fun DrawScope.drawFogBand(
    game: TimingGame,
    cx: Float,
    cy: Float,
    radius: Float,
    dotRadius: Float,
    fog: FogPaint
) {
    // Ein Band-Pixel = ein Vogel-Pixel, wie die Wolke am Telefon.
    val u = (dotRadius * 2f) / WEAR_GRID
    val edge = round(u).coerceAtLeast(1f)
    val outer = round(u * (WEAR_GRID + 2f))
    val mid = outer - 2f * edge
    val core = mid - 4f * edge
    // Blöcke im Abstand eines halben Blocks: Das Band hat keine Lücken.
    val angles = wearFogAngles(game, step = (outer / 2f) / radius)
    if (angles.isEmpty()) return
    // Der Rand trägt den Bevel wie die Nebelbank am Telefon: erst alle
    // Blöcke in der Schattenfarbe, dann oben und links die Lichtkante —
    // links endet sie über der Unterkante, wie in drawCloud. Die Töne
    // wählt BevelPaint.fogTone. Was davon im Innern des Bandes liegt,
    // decken die mittleren Blöcke danach wieder zu; übrig bleibt der
    // Rand, oben und links hell, unten und rechts dunkel.
    val dark = Color(BevelPaint.fogTone(fog, BevelPaint.Edge.DARK, nearTop = false, speckle = false))
    val light = Color(BevelPaint.fogTone(fog, BevelPaint.Edge.LIGHT, nearTop = false, speckle = false))
    val lit = wearBevelFits(outer, outer, edge)
    val layers = listOf(
        outer to dark,
        mid to Color(fog.mid),
        core to Color(fog.inner)
    )
    for ((extent, color) in layers) {
        if (extent <= 0f) continue
        for (a in angles) {
            val px = round(cx + cos(a) * radius)
            val py = round(cy + sin(a) * radius)
            drawRect(
                color = color,
                topLeft = Offset(px - extent / 2f, py - extent / 2f),
                size = Size(extent, extent)
            )
        }
        if (extent == outer && lit) {
            for (a in angles) {
                val px = round(cx + cos(a) * radius)
                val py = round(cy + sin(a) * radius)
                wearCloudLight(light, Offset(px - outer / 2f, py - outer / 2f), Size(outer, outer), edge)
            }
        }
    }
    // Partikel: im Kern, damit sie nie über den Rand ragen.
    val speck = Color(fog.speck)
    for (p in wearFogSpecks(game, angles, core / 2f - edge)) {
        val px = round(cx + cos(p.angle) * (radius + p.offset))
        val py = round(cy + sin(p.angle) * (radius + p.offset))
        if (fog.speckShape == FogSpeck.CROSS) {
            drawRect(speck, Offset(px - edge * 1.5f, py - edge / 2f), Size(edge * 3f, edge))
            drawRect(speck, Offset(px - edge / 2f, py - edge * 1.5f), Size(edge, edge * 3f))
        } else {
            drawRect(speck, Offset(px - edge / 2f, py - edge / 2f), Size(edge, edge))
        }
    }
}

/** Ein Partikel im Nebelband: Winkel auf der Bahn und Versatz quer dazu. */
internal data class WearFogSpeck(val angle: Float, val offset: Float)

/**
 * Die Partikel im Nebelband: jeder dritte Block trägt eines, quer zur
 * Bahn um höchstens [reach] Bildpunkte versetzt. Lage aus einem festen
 * Hash über Zone und Block (game.hits) statt aus einer Zufallszahl —
 * der Engine-Zufall bleibt unberührt, und das Bild flackert nicht.
 */
internal fun wearFogSpecks(game: TimingGame, angles: List<Float>, reach: Float): List<WearFogSpeck> {
    if (angles.isEmpty() || !(reach > 0f)) return emptyList()
    val seed = game.hits * 131
    val out = ArrayList<WearFogSpeck>()
    for (i in angles.indices step 3) {
        val h = wearHash(seed + i)
        out.add(WearFogSpeck(angles[i], (h * 2f - 1f) * reach))
    }
    return out
}

/**
 * Vogel als Pixel-Kreis mit Auge/Glanzpunkt in Flugrichtung — Körper-,
 * Schatten- und Glanzfarbe kommen aus dem gewählten Skin, wie
 * drawTimingDot am Phone.
 */
private fun DrawScope.drawWearDot(
    game: TimingGame,
    cx: Float,
    cy: Float,
    radius: Float,
    minDimension: Float,
    skin: WearDotSkin,
    hour: Int,
    month: Int,
    wobble: Float
) {
    val r = minDimension * WEAR_DOT_RADIUS_SHARE
    val px = cx + cos(game.angle) * radius + wobble * (r * 2f) / WEAR_GRID
    var py = cy + sin(game.angle) * radius

    // Mario-Tod wie am Phone (drawTimingDot in TimingGameScreen.kt):
    // Während des Todes-Freeze bleibt der Vogel stehen, dann hüpft er nach
    // oben, dreht sich dabei auf den Rücken und fällt kopfüber mit
    // Gravitation unten aus dem Bild. Ein eigener Zeitgeber ist unnötig —
    // game.elapsed zählt in DYING ab dem Todesmoment.
    // In die Bomben getippt: Der Vogel steht den Freeze über im Rahmen und
    // platzt dann wie am Phone (drawBirdBurst) statt des Mario-Hüpfers.
    val trapDeath = game.phase == GamePhase.DYING && game.lastDeathCause == DeathCause.TRAP
    val burstTime = game.elapsed - TimingGame.DEATH_FREEZE_SECONDS
    var flip = 0f
    if (game.phase == GamePhase.DYING && !trapDeath) {
        val t = game.elapsed - TimingGame.DEATH_FREEZE_SECONDS
        if (t > 0f) {
            val h = size.height
            py += (-WEAR_DEATH_HOP_SPEED * t + 0.5f * WEAR_DEATH_GRAVITY * t * t) * h
            if (py - r * 2f > h) return
            flip = 180f * (t / WEAR_DEATH_FLIP_SECONDS).coerceAtMost(1f)
        }
    }

    val state = SkinState(
        elapsed = game.elapsed,
        score = game.score,
        perfectStreak = game.perfectStreak,
        hour = hour,
        month = month
    )

    // Glanzfarbe einmal je Bild: Die Kugel-Stufen brauchen sie für jedes
    // der 169 Felder, und bewegte Skins rechnen sie aus der Zeit.
    val shineArgb = skin.shineArgb(state)

    fun drawBird(centerX: Float, centerY: Float, alpha: Float = 1f) {
        // Dottie als Kugel (docs/bevel-look.md, Abschnitt 4): Die Stufen
        // hängen am festen Licht oben links, sie werden beim Blick nach
        // links also NICHT gespiegelt — nur Glanzpunkt und Auge.
        drawWearPixelCircle(
            outline = WearOutlineColor,
            centerX = centerX,
            centerY = centerY,
            radius = r,
            alpha = alpha
        ) { col, row -> skin.kugelCell(col, row, state, shineArgb) }

        val u = (r * 2f) / WEAR_GRID
        fun rect(col: Float, row: Float, cols: Float, rows: Float, color: Color) {
            drawRect(
                color = color,
                topLeft = Offset(centerX - r + col * u, centerY - r + row * u),
                size = Size(cols * u, rows * u),
                alpha = alpha
            )
        }

        // Auge/Glanzpunkt folgen der sichtbaren Flugrichtung wie am Phone
        // (drawTimingDot in TimingGameScreen.kt): horizontale Geschwindigkeit
        // ist ~ -sin(angle) * direction — zeigt sie nach links, wird gespiegelt.
        // Das Auge bekommt zum Körper hin eine Kontur, sonst geht es auf
        // hellen Skins (Koi, Chrom) im Körper unter.
        val facingLeft = sin(game.angle) * game.direction > 0f
        val shine = Color(shineArgb)
        // Weißer Kern im Glanzpunkt, an seiner äußeren oberen Ecke, und eine
        // halbe Zeile Schatten unter dem Augenweiß: Das Auge sitzt in der
        // Kugel statt auf ihr.
        val eyeEdge = Color(BevelPaint.EYE_EDGE)
        val eyeOutline = skin.needsEyeOutline
        if (facingLeft) {
            rect(WEAR_GRID - 4.5f, 2.5f, 2f, 2f, shine)
            rect(WEAR_GRID - 3.5f, 2.5f, 1f, 1f, Color.White)
            if (eyeOutline) {
                rect(5.5f, 3f, 0.5f, 4f, WearOutlineColor)
                rect(2f, 2.5f, 3.5f, 0.5f, WearOutlineColor)
                rect(2f, 7f, 3.5f, 0.5f, WearOutlineColor)
            }
            rect(2f, 3f, 3.5f, 4f, Color.White)
            rect(2f, 6.5f, 3.5f, 0.5f, eyeEdge)
            rect(2f, 4f, 1.5f, 2f, WearOutlineColor)
        } else {
            rect(2.5f, 2.5f, 2f, 2f, shine)
            rect(2.5f, 2.5f, 1f, 1f, Color.White)
            if (eyeOutline) {
                rect(7f, 3f, 0.5f, 4f, WearOutlineColor)
                rect(7.5f, 2.5f, 3.5f, 0.5f, WearOutlineColor)
                rect(7.5f, 7f, 3.5f, 0.5f, WearOutlineColor)
            }
            rect(7.5f, 3f, 3.5f, 4f, Color.White)
            rect(7.5f, 6.5f, 3.5f, 0.5f, eyeEdge)
            rect(9.5f, 4f, 1.5f, 2f, WearOutlineColor)
        }
    }

    // Schweif-Skins (Tinte) lassen Nachbilder auf der Bahn zurück; die
    // Positionen werden wie am Phone aus dem Winkel zurückgerechnet.
    if (skin.hasTrail && game.phase == GamePhase.RUNNING) {
        for (step in SkinPaint.TRAIL_STEPS downTo 1) {
            val a = game.angle - game.direction * step * SkinPaint.TRAIL_SPACING
            drawBird(
                centerX = cx + cos(a) * radius,
                centerY = cy + sin(a) * radius,
                alpha = 0.34f / step
            )
        }
    }

    if (trapDeath && burstTime > 0f) {
        drawWearBirdBurst(px, py, r, burstTime, size.height) { col, row -> skin.kugelCell(col, row, state, shineArgb) }
    } else if (flip > 0f) {
        rotate(degrees = flip, pivot = Offset(px, py)) { drawBird(px, py) }
    } else {
        drawBird(px, py)
    }

    // Todesmarker wie am Telefon: Im Freeze steht ein weißer Pixelrahmen
    // um den Vogel — genau dort ist es passiert. Mit dem Hüpfer ist er weg.
    if (game.phase == GamePhase.DYING && game.elapsed < TimingGame.DEATH_FREEZE_SECONDS) {
        drawWearDeathFrame(px, py, r, wearCell(minDimension))
    }
}

/**
 * Der Vogel platzt (Tod durch die Bomben), wie drawBirdBurst am Telefon:
 * zwölf äußere, sechs innere Tortenstücke und ein Kern fliegen aus der Mitte, hüpfen hoch,
 * fallen mit Gravitation und blassen aus. [time] = Sekunden seit dem Ende
 * des Todes-Freeze.
 */
private fun DrawScope.drawWearBirdBurst(
    centerX: Float,
    centerY: Float,
    radius: Float,
    time: Float,
    height: Float,
    cell: (col: Int, row: Int) -> Color
) {
    val outer = 12
    val inner = 6
    val core = outer + inner
    val alpha = (1f - (time - 0.5f) / 0.5f).coerceIn(0f, 1f)
    if (alpha <= 0f) return
    val n = WEAR_GRID.toInt()
    val u = (radius * 2f) / WEAR_GRID
    val mid = (WEAR_GRID - 1f) / 2f
    val rr = WEAR_GRID / 2f - 0.25f
    val twoPi = 2f * Math.PI.toFloat()
    val offsets = Array(core + 1) { piece ->
        val (a, speed) = when {
            piece < outer -> (piece + 0.5f) / outer * twoPi to 0.35f * (0.8f + 0.2f * (piece % 3))
            piece < core -> (piece - outer + 1f) / inner * twoPi to 0.35f * (0.45f + 0.15f * (piece % 2))
            else -> 0f to 0f
        }
        Offset(
            cos(a) * speed * time * height,
            (sin(a) * speed * time - 0.8f * time + 0.5f * WEAR_DEATH_GRAVITY * time * time) * height
        )
    }
    for (row in 0 until n) {
        for (col in 0 until n) {
            val dx = col - mid
            val dy = row - mid
            val dist = kotlin.math.sqrt(dx * dx + dy * dy)
            if (dist > rr) continue
            val turn = (kotlin.math.atan2(dy, dx) + twoPi) / twoPi
            val piece = when {
                dist < 1.5f -> core
                dist < 3.8f -> outer + ((turn * inner + 0.5f).toInt() % inner)
                else -> (turn * outer).toInt() % outer
            }
            val offset = offsets[piece]
            val color = if (dist > rr - 1.1f) WearOutlineColor else cell(col, row)
            drawRect(
                color = color,
                topLeft = Offset(
                    (centerX - radius + col * u + offset.x).roundToInt().toFloat(),
                    (centerY - radius + row * u + offset.y).roundToInt().toFloat()
                ),
                size = Size(u + 0.5f, u + 0.5f),
                alpha = alpha
            )
        }
    }
}

/** Weißer Rahmen mit dunkler Kontur um den Vogel (drawDeathFrame am Telefon). */
private fun DrawScope.drawWearDeathFrame(px: Float, py: Float, r: Float, cell: Float) {
    val half = r + cell * 2f
    fun frame(inset: Float, width: Float, color: Color) {
        val left = px - half - inset
        val top = py - half - inset
        val side = (half + inset) * 2f
        drawRect(color, Offset(left, top), Size(side, width))
        drawRect(color, Offset(left, top + side - width), Size(side, width))
        drawRect(color, Offset(left, top), Size(width, side))
        drawRect(color, Offset(left + side - width, top), Size(width, side))
    }
    frame(inset = cell * 0.5f, width = cell * 2f, color = WearOutlineColor)
    frame(inset = 0f, width = cell, color = Color.White)
}

/**
 * Kleine Pixel-Münze in den Farben der Medaillen-Stufe. Die 72dp-Medaille
 * des Phones (MedalBadge in ui/.../screens/Panels.kt) samt Band wäre auf der Uhr
 * zu groß — hier reicht die Münze mit Glanzpunkt als kompaktes Symbol.
 */
internal fun DrawScope.drawWearMedalCoin(tier: WearMedalTier) {
    val r = size.minDimension / 2f
    val cx = size.width / 2f
    val cy = size.height / 2f
    drawWearPixelCircle(
        outline = WearOutlineColor,
        centerX = cx,
        centerY = cy,
        radius = r
    ) { col, row -> if (col + row > WEAR_GRID * 1.15f) tier.shade else tier.body }
    // Glanzpunkt oben links, wie auf der Phone-Münze.
    val u = (r * 2f) / WEAR_GRID
    drawRect(
        color = WearDotShine,
        topLeft = Offset(cx - r + 2.5f * u, cy - r + 2.5f * u),
        size = Size(2f * u, 2f * u)
    )
}

/**
 * Kleine Skin-Vorschau-Münze: im READY-Overlay der Knopf zum Skin-Wähler,
 * in dessen Liste das Symbol jeder Zeile. Pixel-Kreis in den Farben des
 * Skins mit Glanzpunkt — dieselbe Zeichnung wie die Medaillen-Münze.
 *
 * Bewusst als Standbild (elapsed = 0): Eine Liste mit lauter animierten
 * Münzen würde die Uhr pro Frame durch dutzende Raster jagen. Stunde und
 * Monat kommen trotzdem mit, sonst zeigten TAGESZEIT und JAHRESZEIT in
 * der Vorschau ein anderes Kleid als im Lauf.
 *
 * Mit denselben Kugel-Stufen und demselben Glanzkern wie im Spiel: Die
 * Vorschau soll den Vogel zeigen, den man bekommt.
 */
internal fun DrawScope.drawWearSkinCoin(skin: WearDotSkin, hour: Int, month: Int) {
    val r = size.minDimension / 2f
    val cx = size.width / 2f
    val cy = size.height / 2f
    val state = SkinState(hour = hour, month = month)
    val shine = skin.shineArgb(state)
    drawWearPixelCircle(
        outline = WearOutlineColor,
        centerX = cx,
        centerY = cy,
        radius = r
    ) { col, row -> skin.kugelCell(col, row, state, shine) }
    val u = (r * 2f) / WEAR_GRID
    drawRect(
        color = Color(shine),
        topLeft = Offset(cx - r + 2.5f * u, cy - r + 2.5f * u),
        size = Size(2f * u, 2f * u)
    )
    drawRect(
        color = Color.White,
        topLeft = Offset(cx - r + 2.5f * u, cy - r + 2.5f * u),
        size = Size(u, u)
    )
}

/**
 * Lokale Kopie von drawPixelCircle (ui/.../world/PixelShapes.kt) — kein :app-Zugriff.
 * Die Füllfarbe kommt pro Feld aus [cell], damit gemusterte und bewegte
 * Skins auf der Uhr genauso aussehen wie am Phone.
 */
private fun DrawScope.drawWearPixelCircle(
    outline: Color,
    centerX: Float,
    centerY: Float,
    radius: Float,
    alpha: Float = 1f,
    cell: (col: Int, row: Int) -> Color
) {
    val n = WEAR_GRID.toInt()
    val u = (radius * 2f) / WEAR_GRID
    val mid = (WEAR_GRID - 1f) / 2f
    val rr = WEAR_GRID / 2f - 0.25f

    for (row in 0 until n) {
        for (col in 0 until n) {
            val dx = col - mid
            val dy = row - mid
            val dist = sqrt(dx * dx + dy * dy)
            if (dist <= rr) {
                val cellColor = if (dist > rr - 1.1f) outline else cell(col, row)
                drawRect(
                    color = cellColor,
                    topLeft = Offset(centerX - radius + col * u, centerY - radius + row * u),
                    size = Size(u + 0.5f, u + 0.5f),
                    alpha = alpha
                )
            }
        }
    }
}

/**
 * Vorschau-Münze einer Welt: der Tageshimmel oben, der Boden unten und
 * ein Tupfer in der Farbe der größten Requisite — die drei Farben aus
 * [ScenePaint.chips], dieselben wie auf den Welt-Kacheln am Telefon.
 */
internal fun DrawScope.drawWearSceneCoin(scene: SceneId) {
    val r = size.minDimension / 2f
    val cx = size.width / 2f
    val cy = size.height / 2f
    val (sky, ground, accent) = ScenePaint.chips(scene).map { Color(it) }
    drawWearPixelCircle(
        outline = WearOutlineColor,
        centerX = cx,
        centerY = cy,
        radius = r
    ) { col, row ->
        when {
            row >= 9 -> ground
            row >= 6 && col in 7..9 -> accent
            else -> sky
        }
    }
}

/**
 * Vorschau eines Ton-Sets: drei Balken (Treffer, Perfekt, Rekord) in der
 * Höhe, in der das Set klingt ([SoundBank.chips]) — ein Ton-Set hat kein
 * Bild, die Balken zeigen, wo es liegt.
 */
internal fun DrawScope.drawWearSoundBars(set: SoundSetId, color: Color) {
    val w = size.width
    val h = size.height
    val bar = floor(w / 5f).coerceAtLeast(1f)
    SoundBank.chips(set).forEachIndexed { i, level ->
        val barH = (h * (0.25f + 0.75f * level)).roundToInt().toFloat()
        drawRect(color, Offset(bar * (i * 2f), h - barH), Size(bar, barH))
    }
}

/**
 * Lautsprecher-Symbol für den Ton-Knopf im Startbildschirm, als
 * Pixelblöcke. [muted]: rotes Kreuz statt Schallwellen.
 */
internal fun DrawScope.drawWearSpeaker(muted: Boolean, color: Color) {
    val u = floor(size.minDimension / 8f).coerceAtLeast(1f)
    val oy = (size.height - 8f * u) / 2f
    val ox = (size.width - 8f * u) / 2f
    fun px(col: Int, row: Int, cols: Int, rows: Int, c: Color) =
        drawRect(c, Offset(ox + col * u, oy + row * u), Size(cols * u, rows * u))
    px(0, 3, 2, 2, color)
    px(2, 2, 1, 4, color)
    px(3, 1, 1, 6, color)
    if (muted) {
        val red = Color(TrapPaint.RED)
        for (k in 0 until 3) {
            px(5 + k, 2 + k, 1, 1, red)
            px(7 - k, 2 + k, 1, 1, red)
        }
    } else {
        px(5, 3, 1, 2, color)
        px(6, 2, 1, 1, color)
        px(6, 5, 1, 1, color)
        px(7, 3, 1, 2, color)
    }
}

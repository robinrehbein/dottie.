package de.robinrehbein.punkt.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.robinrehbein.punkt.game.BackdropKind
import de.robinrehbein.punkt.game.CardFrame
import de.robinrehbein.punkt.game.CardStyle
import de.robinrehbein.punkt.game.CollectionAxis
import de.robinrehbein.punkt.game.CollectionItemProgress
import de.robinrehbein.punkt.game.CollectionProgress
import de.robinrehbein.punkt.game.FrameTone
import de.robinrehbein.punkt.game.GoalAxis
import de.robinrehbein.punkt.game.PropShape
import de.robinrehbein.punkt.game.SceneId
import de.robinrehbein.punkt.game.ScenePaint
import de.robinrehbein.punkt.game.SkinFamily
import de.robinrehbein.punkt.game.SkinId
import de.robinrehbein.punkt.game.SkinPaint
import de.robinrehbein.punkt.game.SkinState
import de.robinrehbein.punkt.game.SkinStats
import de.robinrehbein.punkt.game.SoundBank
import de.robinrehbein.punkt.game.SoundSetId
import de.robinrehbein.punkt.game.TimingGame
import de.robinrehbein.punkt.ui.components.CORNER_BUTTON_PADDING
import de.robinrehbein.punkt.ui.components.OverlayCloseButton
import de.robinrehbein.punkt.ui.components.PIXEL_SHADOW
import de.robinrehbein.punkt.ui.components.PixelButton
import de.robinrehbein.punkt.ui.components.pixelPressable
import de.robinrehbein.punkt.ui.data.CollectionSeen
import de.robinrehbein.punkt.ui.data.deviceHourAndMonth
import de.robinrehbein.punkt.ui.resources.Res
import de.robinrehbein.punkt.ui.resources.collection
import de.robinrehbein.punkt.ui.resources.collection_hear
import de.robinrehbein.punkt.ui.resources.collection_locked
import de.robinrehbein.punkt.ui.resources.collection_locked_preview
import de.robinrehbein.punkt.ui.resources.collection_new
import de.robinrehbein.punkt.ui.resources.collection_new_world
import de.robinrehbein.punkt.ui.resources.collection_new_worlds
import de.robinrehbein.punkt.ui.resources.collection_progress
import de.robinrehbein.punkt.ui.resources.collection_unlocked
import de.robinrehbein.punkt.ui.resources.ctl_close
import de.robinrehbein.punkt.ui.resources.frame_on_card
import de.robinrehbein.punkt.ui.resources.patron_owned
import de.robinrehbein.punkt.ui.resources.patron_pack
import de.robinrehbein.punkt.ui.resources.patron_pack_skins_only
import de.robinrehbein.punkt.ui.resources.skin_pass_offer
import de.robinrehbein.punkt.ui.resources.skin_pass_today
import de.robinrehbein.punkt.ui.resources.skin_selected
import de.robinrehbein.punkt.ui.resources.sounds
import de.robinrehbein.punkt.ui.resources.tab_bird
import de.robinrehbein.punkt.ui.resources.tab_frame
import de.robinrehbein.punkt.ui.resources.tab_sound
import de.robinrehbein.punkt.ui.resources.tab_world
import de.robinrehbein.punkt.ui.text.familyTitle
import de.robinrehbein.punkt.ui.text.frameHint
import de.robinrehbein.punkt.ui.text.frameTitle
import de.robinrehbein.punkt.ui.text.sceneHint
import de.robinrehbein.punkt.ui.text.sceneTitle
import de.robinrehbein.punkt.ui.text.skinHint
import de.robinrehbein.punkt.ui.text.skinTitle
import de.robinrehbein.punkt.ui.text.soundHint
import de.robinrehbein.punkt.ui.text.soundTitle
import de.robinrehbein.punkt.ui.theme.Bytesized
import de.robinrehbein.punkt.ui.world.DotBody
import de.robinrehbein.punkt.ui.world.FxState
import de.robinrehbein.punkt.ui.world.GrassLight
import de.robinrehbein.punkt.ui.world.OutlineColor
import de.robinrehbein.punkt.ui.world.PanelSand
import de.robinrehbein.punkt.ui.world.RecordRed
import de.robinrehbein.punkt.ui.world.TextDark
import de.robinrehbein.punkt.ui.world.drawCloud
import de.robinrehbein.punkt.ui.world.drawBackdrop
import de.robinrehbein.punkt.ui.world.drawGroundStrip
import de.robinrehbein.punkt.ui.world.drawPixelCircle
import de.robinrehbein.punkt.ui.world.drawScenery
import de.robinrehbein.punkt.ui.world.drawTimingDot
import de.robinrehbein.punkt.ui.world.drawTrack
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random
import org.jetbrains.compose.resources.stringResource

// Die Sammlung (Plan 7.1, AP-15): vier Reiter VOGEL · WELT · TON ·
// RAHMEN, oben ein Schaufenster, darunter ein Raster mit vier Kacheln pro
// Zeile. Bis v2.27 eine Liste mit 62 Zeilen in GameOverlays.kt.

/** Die vier Reiter der Sammlung, in der Reihenfolge von links nach rechts. */
enum class CollectionTab { VOGEL, WELT, TON, RAHMEN }

/**
 * Die Sammlung: Skins, Welten, Ton-Sets und Rahmen in vier Reitern.
 *
 * **Anschauen darf man alles, auswählen nur Freigeschaltetes.** Ein Tap
 * auf eine Kachel zeigt sie im Schaufenster oben — die Welt als Stück
 * Bahn mit Vogel, bei Tönen spielt die Hörprobe ([onPreviewSound], auch
 * für gesperrte). Ist die Kachel offen, ist sie damit auch gewählt; die
 * Sammlung bleibt dabei offen. Gesperrte Kacheln sind blass, tragen einen
 * kleinen Fortschrittsbalken und zeigen im Schaufenster Bedingung und
 * Stand („37/500 LÄUFE“), bei Skins dazu „HEUTE PER SPOT TESTEN“ als
 * Knopf (nie für Gönner-Skins: Ein Spot ersetzt keinen Kauf).
 *
 * [newKeys] sind die NEU-Kacheln (siehe CollectionSeen). Was im
 * Schaufenster steht, gilt als angesehen und wird über [onSeen] gemeldet.
 *
 * Ausgang über das X oben rechts, die Zurück-Geste oder einen Tap auf den
 * Rand neben dem Kopf; Taps in die Sammlung selbst schließen nicht.
 *
 * [skinPass] ist der heute per Spot freigeschaltete Probier-Skin,
 * [adOfferReady] sagt, ob dafür ein Spot bereitliegt. [patronPrice] ist
 * der Preis des Gönner-Pakets (null = nicht kaufbar), [adsAlreadyRemoved]
 * ändert nur dessen Beschriftung: Wer schon werbefrei ist, liest, was für
 * ihn wirklich neu ist — die drei Skins.
 */
@Composable
fun CollectionOverlay(
    stats: SkinStats,
    selected: SkinId,
    onSelect: (SkinId) -> Unit,
    selectedScene: SceneId,
    onSelectScene: (SceneId) -> Unit,
    selectedSound: SoundSetId,
    onSelectSound: (SoundSetId) -> Unit,
    onPreviewSound: (SoundSetId) -> Unit,
    selectedCardFrame: CardFrame?,
    onSelectCardFrame: (CardFrame) -> Unit,
    onClose: () -> Unit,
    // Kalendermonat und Tage im laufenden Saison-Fenster, für den Balken
    // der Saison-Skins (siehe CollectionProgress.skin).
    month: Int = 0,
    seasonDays: Int = 0,
    newKeys: Set<String> = emptySet(),
    onSeen: (String) -> Unit = {},
    initialTab: CollectionTab = CollectionTab.VOGEL,
    skinPass: SkinId? = null,
    adOfferReady: Boolean = false,
    onWatchAdFor: (SkinId) -> Unit = {},
    patronPrice: String? = null,
    adsAlreadyRemoved: Boolean = false,
    onPatron: () -> Unit = {}
) {
    // Uhr und Kalender einmal pro Öffnen ablesen, nicht pro Vorschau:
    // TAGESZEIT und JAHRESZEIT tragen ihr heutiges Kleid, aber 46 Kacheln
    // dürfen nicht 46-mal die Systemzeit fragen.
    val clock = remember { deviceHourAndMonth() }

    // Eine Uhr für Schaufenster und Kacheln. Das Schaufenster schreibt ein
    // eigenes, stilles Spiel im READY fort (kein Zufall, kein Lauf), die
    // Kacheln rasten auf die Kadenz von frameKey (12 Hz) ein — feiner
    // ändert sich kein Vogel, und stehende Skins zeichnen nicht umsonst.
    val showcaseGame = remember { TimingGame(Random(0)) }
    val showcaseFx = remember { FxState() }
    var showcaseTick by remember { mutableLongStateOf(0L) }
    var previewElapsed by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var last = -1L
        var start = -1L
        while (true) {
            withFrameNanos { now ->
                if (start < 0) start = now
                val dt = if (last < 0) 0f else (now - last) / 1_000_000_000f
                last = now
                showcaseGame.update(dt)
                showcaseTick++
                val quantized = ((now - start) / 1_000_000_000f * 12f).toInt() / 12f
                if (quantized != previewElapsed) previewElapsed = quantized
            }
        }
    }
    val tileState = SkinState(elapsed = previewElapsed, hour = clock.first, month = clock.second)

    var tab by remember { mutableStateOf(initialTab) }
    // Was gerade im Schaufenster steht, je Reiter. Zu Beginn die eigene
    // Wahl — außer, die Sammlung wurde über das Banner „NEUE WELTEN“
    // geöffnet: Dann steht die erste neue Welt vorn.
    var viewSkin by remember { mutableStateOf(selected) }
    var viewScene by remember {
        mutableStateOf(
            if (initialTab == CollectionTab.WELT) {
                CollectionSeen.newScenes(newKeys).firstOrNull() ?: selectedScene
            } else {
                selectedScene
            }
        )
    }
    var viewSound by remember { mutableStateOf(selectedSound) }
    var viewFrame by remember { mutableStateOf(CardStyle.frame(selectedCardFrame, stats)) }
    val shownFrame = CardStyle.frame(selectedCardFrame, stats)

    // Jede Kachel wird nur einmal als angesehen gemeldet, auch wenn Tap
    // und Schaufenster sie im selben Moment melden.
    val reported = remember { mutableSetOf<String>() }
    fun see(key: String) {
        if (key in newKeys && reported.add(key)) onSeen(key)
    }

    val viewedKey = when (tab) {
        CollectionTab.VOGEL -> CollectionSeen.key(viewSkin)
        CollectionTab.WELT -> CollectionSeen.key(viewScene)
        CollectionTab.TON -> CollectionSeen.key(viewSound)
        CollectionTab.RAHMEN -> CollectionSeen.key(viewFrame)
    }
    // Was im Schaufenster steht, ist angesehen — auch das, was beim
    // Öffnen oder beim Reiterwechsel schon vorne steht.
    LaunchedEffect(viewedKey) { see(viewedKey) }

    fun skinAvailable(id: SkinId) = SkinPaint.isUnlocked(id, stats) || id == skinPass

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OutlineColor)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onClose() })
            }
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Der Kopf gehört zum Rand: Ein Tap auf ihn schließt wie
            // früher „TIPPEN ZUM SCHLIESSEN“, das X steht rechts.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(HEADER_HEIGHT)
            ) {
                // Auf Höhe der Knopfmitte, nicht der Kopfmitte.
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = CORNER_BUTTON_PADDING)
                        .height(48.dp)
                ) {
                    Text(
                        text = stringResource(Res.string.collection),
                        style = ScoreShadowStyle,
                        fontSize = 28.sp,
                        color = Color.White
                    )
                }
            }

            // Alles darunter schluckt Taps: Wer in der Sammlung daneben
            // tippt, will weiterschauen, nicht gehen.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .pointerInput(Unit) { detectTapGestures(onTap = {}) }
            ) {
                val showSkin = if (tab == CollectionTab.VOGEL) viewSkin else selected
                val showScene = if (tab == CollectionTab.WELT) viewScene else selectedScene
                val viewedOpen = when (tab) {
                    CollectionTab.VOGEL -> skinAvailable(viewSkin)
                    CollectionTab.WELT -> ScenePaint.isUnlocked(viewScene, stats)
                    CollectionTab.TON -> SoundBank.isUnlocked(viewSound, stats)
                    CollectionTab.RAHMEN -> CardStyle.isUnlocked(viewFrame, stats)
                }
                Showcase(
                    tick = showcaseTick,
                    game = showcaseGame,
                    fx = showcaseFx,
                    skin = showSkin,
                    scene = showScene,
                    frame = if (tab == CollectionTab.RAHMEN) viewFrame else null,
                    hour = clock.first,
                    month = clock.second,
                    locked = !viewedOpen
                )

                ShowcaseInfo(
                    tab = tab,
                    stats = stats,
                    month = month,
                    seasonDays = seasonDays,
                    viewSkin = viewSkin,
                    viewScene = viewScene,
                    viewSound = viewSound,
                    viewFrame = viewFrame,
                    selectedSkin = selected,
                    selectedScene = selectedScene,
                    selectedSound = selectedSound,
                    shownFrame = shownFrame,
                    skinPass = skinPass,
                    adOfferReady = adOfferReady,
                    onWatchAdFor = onWatchAdFor,
                    onPreviewSound = onPreviewSound
                )

                CollectionTabs(
                    current = tab,
                    stats = stats,
                    newKeys = newKeys,
                    onTab = { tab = it }
                )

                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                ) {
                    when (tab) {
                        CollectionTab.VOGEL -> {
                            // Die Familien bleiben als Zwischenüberschriften:
                            // Sie sagen, wonach die nächsten Kacheln
                            // funktionieren — Muster, Zeit, Spielstand, Kauf.
                            SkinFamily.entries.forEach { family ->
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    Box(contentAlignment = Alignment.Center) {
                                        SkinFamilyHeading(familyTitle(family))
                                    }
                                }
                                // Das Gönner-Angebot steht unter seiner
                                // Familie und nirgends sonst.
                                if (family == SkinFamily.GOENNER &&
                                    (stats.patronOwned || patronPrice != null)
                                ) {
                                    item(span = { GridItemSpan(maxLineSpan) }) {
                                        PatronOffer(
                                            owned = stats.patronOwned,
                                            price = patronPrice.orEmpty(),
                                            adsAlreadyRemoved = adsAlreadyRemoved,
                                            onPatron = onPatron
                                        )
                                    }
                                }
                                items(SkinPaint.ORDER.filter { SkinPaint.family(it) == family }) { skin ->
                                    val progress = CollectionProgress.skin(skin, stats, month, seasonDays)
                                    val available = skinAvailable(skin)
                                    CollectionTile(
                                        label = skinTitle(skin),
                                        open = available,
                                        progress = progress,
                                        isSelected = skin == selected,
                                        isViewed = skin == viewSkin,
                                        isNew = CollectionSeen.key(skin) in newKeys,
                                        onClick = {
                                            viewSkin = skin
                                            see(CollectionSeen.key(skin))
                                            if (available && skin != selected) onSelect(skin)
                                        }
                                    ) {
                                        val d = size.minDimension
                                        drawPixelCircle(
                                            outline = OutlineColor,
                                            centerX = size.width / 2f,
                                            centerY = size.height / 2f,
                                            radius = d / 2f,
                                            alpha = if (available) 1f else LOCKED_ALPHA
                                        ) { col, row -> Color(SkinPaint.cell(skin, col, row, tileState)) }
                                    }
                                }
                            }
                        }
                        CollectionTab.WELT -> items(ScenePaint.ORDER) { scene ->
                            val progress = CollectionProgress.scene(scene, stats)
                            CollectionTile(
                                label = sceneTitle(scene),
                                open = progress.unlocked,
                                progress = progress,
                                isSelected = scene == selectedScene,
                                isViewed = scene == viewScene,
                                isNew = CollectionSeen.key(scene) in newKeys,
                                onClick = {
                                    viewScene = scene
                                    see(CollectionSeen.key(scene))
                                    if (progress.unlocked && scene != selectedScene) onSelectScene(scene)
                                }
                            ) {
                                drawScenePreview(scene, alpha = if (progress.unlocked) 1f else LOCKED_ALPHA)
                            }
                        }
                        CollectionTab.TON -> items(SoundBank.ORDER) { sound ->
                            val progress = CollectionProgress.sound(sound, stats)
                            CollectionTile(
                                label = soundTitle(sound),
                                open = progress.unlocked,
                                progress = progress,
                                isSelected = sound == selectedSound,
                                isViewed = sound == viewSound,
                                isNew = CollectionSeen.key(sound) in newKeys,
                                onClick = {
                                    viewSound = sound
                                    see(CollectionSeen.key(sound))
                                    // Die Hörprobe gibt es für jedes Set, auch
                                    // gesperrt: Wer es hört, will es haben.
                                    onPreviewSound(sound)
                                    if (progress.unlocked && sound != selectedSound) onSelectSound(sound)
                                }
                            ) {
                                drawSoundPreview(sound, alpha = if (progress.unlocked) 1f else LOCKED_ALPHA)
                            }
                        }
                        CollectionTab.RAHMEN -> items(CardFrame.entries) { frame ->
                            val progress = CollectionProgress.frame(frame, stats)
                            CollectionTile(
                                label = frameTitle(frame),
                                open = progress.unlocked,
                                progress = progress,
                                isSelected = frame == shownFrame,
                                isViewed = frame == viewFrame,
                                isNew = CollectionSeen.key(frame) in newKeys,
                                onClick = {
                                    viewFrame = frame
                                    see(CollectionSeen.key(frame))
                                    if (progress.unlocked && frame != shownFrame) onSelectCardFrame(frame)
                                }
                            ) {
                                drawCardFramePreview(frame, alpha = if (progress.unlocked) 1f else LOCKED_ALPHA)
                            }
                        }
                    }
                }
            }
        }

        // Das X liegt wie in jedem Overlay genau dort, wo im
        // Startbildschirm die Einstellungen sitzen.
        OverlayCloseButton(
            onClose = onClose,
            contentDescription = stringResource(Res.string.ctl_close),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(CORNER_BUTTON_PADDING)
        )
    }
}

/**
 * Höhe des Kopfs mit Titel und X — er zählt zum Rand und schließt beim
 * Tippen. Der Eckknopf (48 dp plus 4 dp Schatten) sitzt 16 dp unter der
 * Oberkante; darunter bleiben 8 dp Luft bis zum Schaufenster.
 */
private val HEADER_HEIGHT = 76.dp

/** Wie blass gesperrte Vorschauen sind. */
private const val LOCKED_ALPHA = 0.3f

/** Wie groß der Vogel im Schaufenster gegenüber dem Spielbild steht. */
private const val SHOWCASE_BIRD_SCALE = 2.4f

private val TileBackground = Color(0xFF4A3642)
private val TileEdge = Color(0xFF1A1016)

/**
 * Das Schaufenster: ein Stück Spielbild in der angesehenen Welt mit dem
 * angesehenen Vogel — gezeichnet mit denselben Funktionen wie das Spiel
 * ([drawScenery], [drawGroundStrip], [drawTrack], [drawTimingDot]), damit
 * die Vorschau nicht lügt. Im Reiter RAHMEN liegt der Rahmen darum.
 * Gesperrtes bekommt einen Schleier und „VORSCHAU · GESPERRT“.
 */
@Composable
private fun Showcase(
    tick: Long,
    game: TimingGame,
    fx: FxState,
    skin: SkinId,
    scene: SceneId,
    frame: CardFrame?,
    hour: Int,
    month: Int,
    locked: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(SHOWCASE_HEIGHT)
            .clipToBounds()
    ) {
        // Wie weit der Rahmen nach innen reicht: Schriftzug und Ring
        // bleiben innerhalb der Rahmenkante.
        val depth = showcaseFrameDepth(frame)
        Canvas(modifier = Modifier.fillMaxSize()) {
            tick // pro Frame neu zeichnen
            drawShowcase(game, fx, skin, scene, hour, month, depth.toPx())
            if (locked) drawRect(color = TileEdge.copy(alpha = 0.35f))
            drawShowcaseFrame(frame)
        }
        if (locked) {
            Text(
                text = stringResource(Res.string.collection_locked_preview),
                style = ScoreShadowStyle,
                fontSize = 14.sp,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = depth + SHOWCASE_LABEL_TOP)
            )
        }
    }
}

private val SHOWCASE_HEIGHT = 168.dp

/** Abstand des Schriftzugs „VORSCHAU · GESPERRT“ unter der Rahmenkante. */
private val SHOWCASE_LABEL_TOP = 6.dp

/**
 * Das Band oben im Schaufenster (unter der Rahmenkante), das dem
 * Schriftzug gehört. Der Ring beginnt erst darunter — auch wenn nichts
 * gesperrt ist, damit das Bild beim Blättern nicht springt.
 */
private val SHOWCASE_LABEL_BAND = 36.dp

/** Luft zwischen Ring und unterer Rahmenkante. */
private val SHOWCASE_RING_BOTTOM = 6.dp

/** Größter Ring im Schaufenster, als Anteil der Höhe. */
private const val SHOWCASE_RING_MAX = 0.27f

/**
 * Wie tief der Rahmen von oben ins Schaufenster reicht — aus derselben
 * Tabelle, mit der er gezeichnet wird ([drawShowcaseFrame]).
 */
private fun showcaseFrameDepth(frame: CardFrame?): androidx.compose.ui.unit.Dp = when (frame) {
    null -> 0.dp
    CardFrame.SCHLICHT -> 6.dp
    else -> {
        val zeilen = 84
        val zellen = CardStyle.frameRects(frame, 200, zeilen)
            // Nur Stücke der oberen Kante; die Seitenbänder reichen
            // von oben bis unten und zählen hier nicht.
            .filter { it.row + it.rows < zeilen / 4 }
            .maxOfOrNull { it.row + it.rows } ?: 0
        FRAME_CELL * zellen
    }
}

/** Kantenlänge einer Rahmenzelle im Schaufenster. */
private val FRAME_CELL = 2.dp

private fun DrawScope.drawShowcase(
    game: TimingGame,
    fx: FxState,
    skin: SkinId,
    scene: SceneId,
    hour: Int,
    month: Int,
    frameDepth: Float
) {
    val w = size.width
    val h = size.height
    // Gröber gerastert als das Spielbild: Das Schaufenster ist klein, und
    // die Pixel sollen trotzdem als Pixel lesbar bleiben.
    val cell = (h / 110f).roundToInt().toFloat().coerceAtLeast(2f)
    val kulisse = ScenePaint.of(scene)

    drawRect(color = Color(kulisse.sky[0]))
    drawBackdrop(kulisse.backdrop, game.elapsed, cell)
    kulisse.cloud?.let { cloud ->
        val drift = game.elapsed * h * 0.02f
        drawCloud(w * 0.12f - drift % (w * 1.4f) + w * 0.2f, h * 0.12f, cell, Color(cloud))
        drawCloud(w * 0.78f - drift % (w * 1.4f) + w * 0.2f, h * 0.2f, cell, Color(cloud))
    }
    drawScenery(game, cell, kulisse.props)
    kulisse.ground?.let { drawGroundStrip(cell, it) }

    // Die Bahn als Ring, der Vogel darauf größer als im Spiel: Im
    // Schaufenster soll man sein Muster erkennen. Der Ring bleibt
    // innerhalb der Rahmenkante und unter dem Band, das oben dem
    // Schriftzug „VORSCHAU · GESPERRT“ gehört.
    val top = frameDepth + SHOWCASE_LABEL_BAND.toPx()
    val bottom = h - frameDepth - SHOWCASE_RING_BOTTOM.toPx()
    val radius = minOf((bottom - top) / 2f, h * SHOWCASE_RING_MAX)
    val cx = w / 2f
    val cy = (top + bottom) / 2f
    drawTrack(game, cx, cy, radius, cell)
    val px = cx + cos(game.angle) * radius
    val py = cy + sin(game.angle) * radius
    scale(SHOWCASE_BIRD_SCALE, pivot = Offset(px, py)) {
        drawTimingDot(game, fx, cx, cy, radius, skin, hour, month)
    }
}

/**
 * Der Rahmen um das Schaufenster im Reiter RAHMEN — aus derselben
 * Tabelle wie Karte und Game-Over ([CardStyle.frameRects]).
 */
private fun DrawScope.drawShowcaseFrame(frame: CardFrame?) {
    if (frame == null) return
    if (frame == CardFrame.SCHLICHT) {
        drawRect(
            color = OutlineColor,
            topLeft = Offset(2.dp.toPx(), 2.dp.toPx()),
            size = Size(size.width - 4.dp.toPx(), size.height - 4.dp.toPx()),
            style = Stroke(width = 4.dp.toPx())
        )
        return
    }
    val zelle = FRAME_CELL.toPx()
    val spalten = (size.width / zelle).roundToInt().coerceAtLeast(1)
    val zeilen = (size.height / zelle).roundToInt().coerceAtLeast(1)
    val breite = size.width / spalten
    val hoehe = size.height / zeilen
    CardStyle.frameRects(frame, spalten, zeilen).forEach { r ->
        drawRect(
            color = Color(r.tone.argb),
            topLeft = Offset(r.col * breite, r.row * hoehe),
            size = Size(r.cols * breite, r.rows * hoehe)
        )
    }
}

/**
 * Name, Bedingung und Stand der angesehenen Kachel, darunter der eine
 * Knopf, den es gerade braucht: der Spot für einen gesperrten Skin oder
 * die Hörprobe bei Tönen. Die Höhe steht fest, damit das Raster darunter
 * nicht springt.
 */
@Composable
private fun ShowcaseInfo(
    tab: CollectionTab,
    stats: SkinStats,
    month: Int,
    seasonDays: Int,
    viewSkin: SkinId,
    viewScene: SceneId,
    viewSound: SoundSetId,
    viewFrame: CardFrame,
    selectedSkin: SkinId,
    selectedScene: SceneId,
    selectedSound: SoundSetId,
    shownFrame: CardFrame,
    skinPass: SkinId?,
    adOfferReady: Boolean,
    onWatchAdFor: (SkinId) -> Unit,
    onPreviewSound: (SoundSetId) -> Unit
) {
    val name: String
    val hint: String?
    val progress: CollectionItemProgress
    val isSelected: Boolean
    var onPass = false
    when (tab) {
        CollectionTab.VOGEL -> {
            name = skinTitle(viewSkin)
            hint = skinHint(viewSkin)
            progress = CollectionProgress.skin(viewSkin, stats, month, seasonDays)
            isSelected = viewSkin == selectedSkin
            onPass = viewSkin == skinPass && !progress.unlocked
        }
        CollectionTab.WELT -> {
            name = sceneTitle(viewScene)
            hint = sceneHint(viewScene)
            progress = CollectionProgress.scene(viewScene, stats)
            isSelected = viewScene == selectedScene
        }
        CollectionTab.TON -> {
            name = soundTitle(viewSound)
            hint = soundHint(viewSound)
            progress = CollectionProgress.sound(viewSound, stats)
            isSelected = viewSound == selectedSound
        }
        CollectionTab.RAHMEN -> {
            name = frameTitle(viewFrame)
            hint = frameHint(viewFrame)
            progress = CollectionProgress.frame(viewFrame, stats)
            isSelected = viewFrame == shownFrame
        }
    }
    val open = progress.unlocked || onPass

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .height(INFO_HEIGHT)
            .padding(horizontal = 24.dp, vertical = 6.dp)
    ) {
        // Die Texte bekommen, was der Knopf übrig lässt, und bleiben je
        // eine Zeile: Mit großer Systemschrift drückten sie sonst den
        // Knopf unter seine Schattenhöhe, und die App stürzte ab.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clipToBounds()
        ) {
            Text(
                text = name,
                style = ScoreShadowStyle,
                fontSize = 20.sp,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val status = when {
                isSelected -> stringResource(Res.string.skin_selected)
                onPass -> stringResource(Res.string.skin_pass_today)
                open && tab == CollectionTab.RAHMEN -> stringResource(Res.string.frame_on_card)
                open -> stringResource(Res.string.collection_unlocked)
                else -> stringResource(Res.string.collection_locked) + (hint?.let { " · $it" } ?: "")
            }
            Text(
                text = status,
                fontFamily = Bytesized,
                fontSize = 14.sp,
                color = when {
                    isSelected -> DotBody
                    onPass -> GrassLight
                    open -> Color.White.copy(alpha = 0.75f)
                    else -> Color.White.copy(alpha = 0.6f)
                },
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val axis = axisText(progress.axis)
            if (!progress.unlocked && axis != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GoalBar(fraction = progress.fraction, modifier = Modifier.width(120.dp), height = 10.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(
                            Res.string.collection_progress,
                            progress.current,
                            progress.target,
                            axis
                        ),
                        fontFamily = Bytesized,
                        fontSize = 13.sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        // Der Spot macht einen gesperrten Skin für heute spielbar. Gönner
        // bleiben ausgenommen: Ein Spot darf keinen Kauf ersetzen.
        val spotOffer = tab == CollectionTab.VOGEL && !open && adOfferReady &&
            !SkinPaint.isPatron(viewSkin)
        when {
            spotOffer -> PixelButton(
                text = stringResource(Res.string.skin_pass_offer),
                onClick = { onWatchAdFor(viewSkin) },
                backgroundColor = GrassLight,
                borderColor = TextDark,
                textColor = TextDark,
                width = 260.dp,
                height = 40.dp,
                borderWidth = 3.dp
            )
            tab == CollectionTab.TON -> PixelButton(
                text = stringResource(Res.string.collection_hear),
                onClick = { onPreviewSound(viewSound) },
                backgroundColor = PanelSand,
                borderColor = TextDark,
                textColor = TextDark,
                width = 180.dp,
                height = 40.dp,
                borderWidth = 3.dp
            )
        }
    }
}

/**
 * Name, Bedingung, Fortschritt und ein 40-dp-Knopf mit Schatten, jeweils
 * einzeilig bei Schriftgröße 1. Mit 112 dp war das zu knapp: Der Knopf
 * wurde gestaucht, bei großer Systemschrift unter seine Schattenhöhe.
 */
private val INFO_HEIGHT = 128.dp

/** Worauf ein Balken zählt, als Wort — null, wo es nichts zu zählen gibt. */
@Composable
private fun axisText(axis: CollectionAxis): String? = when (axis) {
    CollectionAxis.NONE, CollectionAxis.PURCHASE -> null
    CollectionAxis.SOUND_COLLECTION -> stringResource(Res.string.sounds)
    else -> stringResource(goalAxisText(GoalAxis.valueOf(axis.name)))
}

/**
 * Die vier Reiter mit Zähler („12/46“) und rotem Punkt, solange darin
 * etwas NEU ist.
 */
@Composable
private fun CollectionTabs(
    current: CollectionTab,
    stats: SkinStats,
    newKeys: Set<String>,
    onTab: (CollectionTab) -> Unit
) {
    // Eine Leiste im Pixel-Stil wie die Kacheln darunter: gemeinsamer
    // dunkler Rahmen, dunkle Trennstriche, der aktive Reiter sandfarben
    // mit Glanzkante wie die Knöpfe. Seitlich bündig mit dem Raster.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(top = 4.dp)
            .height(TAB_HEIGHT)
            .background(TileEdge)
            .padding(TAB_BORDER),
        horizontalArrangement = Arrangement.spacedBy(TAB_BORDER)
    ) {
        CollectionTab.entries.forEach { t ->
            val (label, count, total, prefix) = when (t) {
                CollectionTab.VOGEL -> TabData(
                    stringResource(Res.string.tab_bird),
                    CollectionProgress.skinCount(stats),
                    CollectionProgress.SKIN_TOTAL,
                    CollectionSeen.SKIN
                )
                CollectionTab.WELT -> TabData(
                    stringResource(Res.string.tab_world),
                    CollectionProgress.sceneCount(stats),
                    CollectionProgress.SCENE_TOTAL,
                    CollectionSeen.SCENE
                )
                CollectionTab.TON -> TabData(
                    stringResource(Res.string.tab_sound),
                    CollectionProgress.soundCount(stats),
                    CollectionProgress.SOUND_TOTAL,
                    CollectionSeen.SOUND
                )
                CollectionTab.RAHMEN -> TabData(
                    stringResource(Res.string.tab_frame),
                    CollectionProgress.frameCount(stats),
                    CollectionProgress.FRAME_TOTAL,
                    CollectionSeen.FRAME
                )
            }
            val active = t == current
            val hasNew = newKeys.any { it.startsWith(prefix) }
            val ink = if (active) TextDark else PanelSand
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(if (active) PanelSand else TileBackground)
                    .drawBehind {
                        if (active) {
                            drawRect(TabHighlight, size = Size(size.width, TAB_BORDER.toPx()))
                        }
                    }
                    .pixelPressable(role = Role.Tab) { onTab(t) }
                    .semantics { selected = active },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Der rote Punkt steht direkt hinter dem Wort, nicht
                    // lose in der Ecke.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = label,
                            fontFamily = Bytesized,
                            fontSize = 13.sp,
                            color = ink
                        )
                        if (hasNew) {
                            Spacer(modifier = Modifier.width(5.dp))
                            NewDot(dotSize = 9.dp)
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "$count/$total",
                        fontFamily = Bytesized,
                        fontSize = 11.sp,
                        color = ink.copy(alpha = if (active) 0.7f else 0.6f)
                    )
                }
            }
        }
    }
}

private val TAB_HEIGHT = 52.dp
private val TAB_BORDER = 3.dp
private val TabHighlight = Color(0xFFEFE9C2)

private data class TabData(val label: String, val count: Int, val total: Int, val prefix: String)

/**
 * Eine Kachel im Raster: Vorschau in der Mitte, gesperrt blass mit
 * kleinem Fortschrittsbalken, gewählt mit goldenem Rand, angesehen mit
 * weißem, NEU mit rotem Schild in der Ecke.
 */
@Composable
private fun CollectionTile(
    label: String,
    open: Boolean,
    progress: CollectionItemProgress,
    isSelected: Boolean,
    isViewed: Boolean,
    isNew: Boolean,
    onClick: () -> Unit,
    preview: DrawScope.() -> Unit
) {
    val lockedText = stringResource(Res.string.collection_locked)
    val newText = stringResource(Res.string.collection_new)
    val cd = buildString {
        append(label)
        if (!open) append(", ").append(lockedText)
        if (isNew) append(", ").append(newText)
    }
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .background(TileBackground)
            .border(
                width = 3.dp,
                color = when {
                    isSelected -> DotBody
                    isViewed -> Color.White
                    else -> TileEdge
                }
            )
            .pixelPressable(onClick = onClick)
            .semantics { contentDescription = cd }
    ) {
        Canvas(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxSize(0.58f)
        ) {
            preview()
        }
        val axisCounts = progress.axis != CollectionAxis.NONE && progress.axis != CollectionAxis.PURCHASE
        if (!open && axisCounts) {
            GoalBar(
                fraction = progress.fraction,
                height = 6.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(start = 6.dp, end = 6.dp, bottom = 5.dp)
            )
        }
        if (isNew && open) {
            NewBadge(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = 2.dp, y = 2.dp)
            )
        }
    }
}

/** Das rote „NEU“-Schild an einer Kachel. */
@Composable
internal fun NewBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(TileEdge)
            .padding(2.dp)
            .background(RecordRed)
            .padding(horizontal = 3.dp)
    ) {
        Text(
            text = stringResource(Res.string.collection_new),
            fontFamily = Bytesized,
            fontSize = 10.sp,
            color = Color.White
        )
    }
}

/**
 * Der rote Punkt: am Reiter und am Taster SAMMLUNG im Startbildschirm,
 * solange es in der Sammlung etwas NEU gibt.
 */
@Composable
internal fun NewDot(modifier: Modifier = Modifier, dotSize: Dp = 12.dp) {
    Canvas(modifier = modifier.size(dotSize)) {
        val border = 2.dp.toPx()
        drawRect(color = TileEdge)
        drawRect(
            color = RecordRed,
            topLeft = Offset(border, border),
            size = Size(size.width - 2 * border, size.height - 2 * border)
        )
    }
}

/** Das Gönner-Angebot unter seiner Familie — oder ein Danke, wenn es gekauft ist. */
@Composable
private fun PatronOffer(
    owned: Boolean,
    price: String,
    adsAlreadyRemoved: Boolean,
    onPatron: () -> Unit
) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            text = when {
                owned -> stringResource(Res.string.patron_owned)
                adsAlreadyRemoved -> stringResource(Res.string.patron_pack_skins_only, price)
                else -> stringResource(Res.string.patron_pack, price)
            },
            fontFamily = Bytesized,
            fontSize = 15.sp,
            color = DotBody,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .pixelPressable(enabled = !owned, onClick = onPatron)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        )
    }
}

/**
 * Das Banner im Startbildschirm, wenn neue Welten auf sind:
 * „NEUE WELTEN: WÜSTE, MEER“. Mehrere Welten auf einmal (nach dem Update
 * oder wenn Skin und Welt zusammenfallen) stehen gesammelt in einer
 * Zeile. Tippen öffnet die Sammlung im Reiter WELT. Es ist bewusst flach
 * und sitzt dicht unter dem Rekord: Darunter beginnt der Ring, und in
 * seinem Inneren gehört der Platz dem Spiel (Hinweise von AP-22).
 */
@Composable
fun NewWorldsBanner(scenes: List<SceneId>, onClick: () -> Unit) {
    if (scenes.isEmpty()) return
    val names = scenes.map { sceneTitle(it) }.joinToString(", ")
    val text = if (scenes.size == 1) {
        stringResource(Res.string.collection_new_world, names)
    } else {
        stringResource(Res.string.collection_new_worlds, names)
    }
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Box(
        modifier = Modifier
            .padding(top = 6.dp, start = 24.dp, end = 24.dp)
            .pixelPressable(interactionSource = interactionSource, onClick = onClick)
            .offset(y = if (pressed) PIXEL_SHADOW else 0.dp)
            .drawBehind {
                val s = PIXEL_SHADOW.toPx()
                val b = 3.dp.toPx()
                if (!pressed) {
                    drawRect(TileEdge, topLeft = Offset(s, s), size = size)
                }
                drawRect(OutlineColor)
                drawRect(RecordRed, topLeft = Offset(b, b), size = Size(size.width - 2 * b, size.height - 2 * b))
            }
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Text(
            text = text,
            fontFamily = Bytesized,
            fontSize = 14.sp,
            color = Color.White,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Vorschau einer Welt in einer Kachel: Tageshimmel, Bodenkante mit Narbe
 * und eine Requisite als Silhouette. Mehr passt nicht hinein — und
 * weniger wäre nicht auseinanderzuhalten.
 */
private fun DrawScope.drawScenePreview(scene: SceneId, alpha: Float) {
    val d = size.minDimension
    val paint = ScenePaint.of(scene)
    val border = d / 12f
    val horizon = d * 0.62f

    drawRect(color = OutlineColor, size = Size(d, d), alpha = alpha)
    drawRect(
        color = Color(paint.sky[0]),
        topLeft = Offset(border, border),
        size = Size(d - border * 2f, horizon - border),
        alpha = alpha
    )
    // Ohne Boden (WELTRAUM) läuft der Himmel bis unten durch und zeigt
    // dort seine Nachtstufe — die Kachel bleibt so trotzdem lesbar.
    val ground = paint.ground
    drawRect(
        color = Color(ground?.sand ?: paint.sky[6]),
        topLeft = Offset(border, horizon),
        size = Size(d - border * 2f, d - horizon - border),
        alpha = alpha
    )
    if (ground != null) {
        drawRect(
            color = Color(ground.turfLight),
            topLeft = Offset(border, horizon),
            size = Size(d - border * 2f, d * 0.07f),
            alpha = alpha
        )
    }
    fun block(color: Long, x: Float, y: Float, bw: Float, bh: Float, a: Float = alpha) =
        drawRect(color = Color(color), topLeft = Offset(d * x, d * y), size = Size(d * bw, d * bh), alpha = a)
    val backdrop = paint.backdrop
    when {
        // WELTRAUM: Sterne und eine kleine Galaxie statt Requisiten.
        backdrop?.kind == BackdropKind.STERNENHIMMEL -> {
            val c = backdrop.colors
            listOf(0.2f to 0.2f, 0.7f to 0.16f, 0.45f to 0.32f, 0.8f to 0.42f, 0.25f to 0.55f, 0.62f to 0.7f, 0.18f to 0.78f)
                .forEachIndexed { i, (x, y) -> block(c[i % 3], x, y, 0.05f, 0.05f) }
            block(c[4], 0.44f, 0.5f, 0.3f, 0.08f, alpha * 0.8f)
            block(c[5], 0.36f, 0.56f, 0.18f, 0.06f, alpha * 0.7f)
            block(c[3], 0.52f, 0.49f, 0.1f, 0.1f)
        }
        // BERG: ein verschneiter Gipfel hinter einer Tanne.
        backdrop?.kind == BackdropKind.GEBIRGE -> {
            val c = backdrop.colors
            block(c[0], 0.14f, horizon / d - 0.14f, 0.6f, 0.14f)
            block(c[0], 0.24f, horizon / d - 0.26f, 0.4f, 0.12f)
            block(c[4], 0.32f, horizon / d - 0.36f, 0.24f, 0.1f)
            val tanne = paint.props.first()
            block(tanne.dark, 0.6f, horizon / d - 0.18f, 0.22f, 0.18f)
            block(tanne.body, 0.64f, horizon / d - 0.3f, 0.14f, 0.12f)
            block(tanne.light, 0.67f, horizon / d - 0.36f, 0.08f, 0.06f)
        }
        // MEER: Insel mit Palme.
        paint.props.first().shape == PropShape.INSEL -> {
            val insel = paint.props.first()
            block(insel.light, 0.2f, horizon / d - 0.08f, 0.44f, 0.08f)
            block(insel.stem, 0.36f, horizon / d - 0.3f, 0.06f, 0.22f)
            block(insel.body, 0.24f, horizon / d - 0.36f, 0.3f, 0.07f)
            block(insel.dark, 0.2f, horizon / d - 0.3f, 0.08f, 0.06f)
            block(insel.dark, 0.48f, horizon / d - 0.3f, 0.08f, 0.06f)
        }
        // Sonst: die größte Form der Welt als drei Blöcke.
        else -> {
            val prop = paint.props.first()
            block(prop.dark, 0.22f, horizon / d - 0.22f, 0.26f, 0.22f)
            block(prop.body, 0.28f, horizon / d - 0.34f, 0.16f, 0.14f)
            block(prop.light, 0.58f, horizon / d - 0.16f, 0.18f, 0.16f)
        }
    }
}

/**
 * Vorschau eines Ton-Sets in einer Kachel: drei Balken für Treffer, Perfekt
 * und Rekord, deren Höhe aus [SoundBank.chips] kommt. Ein Ton-Set hat
 * kein Bild — die Kachel zeigt deshalb die Lage des Sets: Die Glocke
 * steht hoch, der Amboss bleibt am Boden. Die Zahlen stammen aus :core,
 * damit die PWA dieselbe Kachel zeichnet.
 */
private fun DrawScope.drawSoundPreview(sound: SoundSetId, alpha: Float) {
    val d = size.minDimension
    val border = d / 12f
    val innen = d - border * 2f

    drawRect(color = OutlineColor, size = Size(d, d), alpha = alpha)
    drawRect(
        color = PanelSand,
        topLeft = Offset(border, border),
        size = Size(innen, innen),
        alpha = alpha
    )
    // Dieselbe Sprache wie der Fortschrittsbalken: Sandbett, goldene
    // Blöcke — nur senkrecht, weil hier keine Strecke gemeint ist.
    val chips = SoundBank.chips(sound)
    val breite = innen / (chips.size * 2f - 1f)
    chips.forEachIndexed { index, anteil ->
        // Auch das tiefste Set bleibt sichtbar: ein Fünftel Mindesthöhe.
        val hoehe = innen * (0.2f + 0.75f * anteil)
        drawRect(
            color = DotBody,
            topLeft = Offset(border + index * breite * 2f, border + innen - hoehe),
            size = Size(breite, hoehe),
            alpha = alpha
        )
    }
}

/**
 * Vorschau einer Rahmenstufe: eine leere Karte im Seitenverhältnis der
 * echten, mit genau dem Rahmen, den sie bekäme.
 *
 * Bewusst ohne Inhalt — kein Punkt, kein Titel, keine Zahl. Die Kachel
 * beantwortet eine einzige Frage („wie dick und wie verziert ist der
 * Rand"), und alles andere darin wäre bei 36 dp ohnehin nur Grieß.
 */
private fun DrawScope.drawCardFramePreview(frame: CardFrame, alpha: Float) {
    val d = size.minDimension
    // Die Karte ist breiter als hoch; die Kachel ist quadratisch. Also
    // ein liegendes Rechteck mittig einsetzen, statt zu verzerren.
    val h = d * 0.72f
    val top = (d - h) / 2f
    // Bewusst kräftiger als auf der echten Karte: Auf 36 dp wäre der
    // wahre Anteil (15 von 180 Feldern) ein Haar. Die Reihenfolge
    // stimmt trotzdem — jede Stufe ist breiter als die darunter.
    val staerke = when (frame) {
        CardFrame.SCHLICHT -> d / 18f
        CardFrame.DOPPELLINIE -> d / 12f
        CardFrame.ZINNEN -> d / 8f
        CardFrame.PRACHT -> d / 6f
        CardFrame.KASKADE -> d / 5.5f
        CardFrame.PERLENKRANZ -> d / 5f
        CardFrame.KRONE -> d / 4.5f
    }

    drawRect(color = OutlineColor, topLeft = Offset(0f, top), size = Size(d, h), alpha = alpha)
    drawRect(
        color = PanelSand,
        topLeft = Offset(staerke, top + staerke),
        size = Size(d - staerke * 2f, h - staerke * 2f),
        alpha = alpha
    )
    // Ab der zweiten Stufe liegt ein farbiges Band im Rahmen — dasselbe
    // Erkennungszeichen wie auf der Karte selbst.
    if (frame != CardFrame.SCHLICHT) {
        val band = staerke / 3f
        drawRect(
            color = DotBody,
            topLeft = Offset(band, top + band),
            size = Size(d - band * 2f, h - band * 2f),
            alpha = alpha,
            style = Stroke(width = band)
        )
    }
    // Ab der Prachtstufe kommen Eckornamente dazu, sonst sähen die
    // oberen vier Stufen alle aus wie eine bloß dickere Zinnenstufe.
    // Die Farbe unterscheidet sie: Gold für die Pracht, danach das
    // Kennzeichen der jeweiligen Sammlung.
    val eckFarbe = when (frame) {
        CardFrame.PRACHT -> DotBody
        CardFrame.KASKADE -> Color(FrameTone.INLAY.argb)
        CardFrame.PERLENKRANZ -> Color(FrameTone.PEARL.argb)
        CardFrame.KRONE -> Color(FrameTone.GOLD.argb)
        else -> null
    }
    if (eckFarbe != null) {
        val eck = staerke * 0.8f
        listOf(
            Offset(0f, top),
            Offset(d - eck, top),
            Offset(0f, top + h - eck),
            Offset(d - eck, top + h - eck)
        ).forEach {
            drawRect(color = eckFarbe, topLeft = it, size = Size(eck, eck), alpha = alpha)
        }
    }
}

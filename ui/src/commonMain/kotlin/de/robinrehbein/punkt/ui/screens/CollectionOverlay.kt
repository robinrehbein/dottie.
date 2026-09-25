package de.robinrehbein.punkt.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.robinrehbein.punkt.game.CardFrame
import de.robinrehbein.punkt.game.CardStyle
import de.robinrehbein.punkt.game.FrameTone
import de.robinrehbein.punkt.game.SceneId
import de.robinrehbein.punkt.game.ScenePaint
import de.robinrehbein.punkt.game.SkinFamily
import de.robinrehbein.punkt.game.SkinId
import de.robinrehbein.punkt.game.SkinPaint
import de.robinrehbein.punkt.game.SkinState
import de.robinrehbein.punkt.game.SkinStats
import de.robinrehbein.punkt.game.SoundBank
import de.robinrehbein.punkt.game.SoundSetId
import de.robinrehbein.punkt.ui.data.deviceHourAndMonth
import de.robinrehbein.punkt.ui.resources.Res
import de.robinrehbein.punkt.ui.resources.collection
import de.robinrehbein.punkt.ui.resources.frame_on_card
import de.robinrehbein.punkt.ui.resources.frames
import de.robinrehbein.punkt.ui.resources.patron_owned
import de.robinrehbein.punkt.ui.resources.patron_pack
import de.robinrehbein.punkt.ui.resources.patron_pack_skins_only
import de.robinrehbein.punkt.ui.resources.scenes
import de.robinrehbein.punkt.ui.resources.skin_pass_offer
import de.robinrehbein.punkt.ui.resources.skin_pass_today
import de.robinrehbein.punkt.ui.resources.skin_selected
import de.robinrehbein.punkt.ui.resources.skin_tap_select
import de.robinrehbein.punkt.ui.resources.sound_tap_hear
import de.robinrehbein.punkt.ui.resources.sounds
import de.robinrehbein.punkt.ui.resources.stats
import de.robinrehbein.punkt.ui.resources.tap_to_close
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
import de.robinrehbein.punkt.ui.world.GrassLight
import de.robinrehbein.punkt.ui.world.OutlineColor
import de.robinrehbein.punkt.ui.world.PanelSand
import de.robinrehbein.punkt.ui.world.drawPixelCircle
import org.jetbrains.compose.resources.stringResource

// Die Sammlung: Welten, Töne, Rahmen und Skins mit ihren Vorschauen.
// Bis v2.27 Teil von GameOverlays.kt (aufgeteilt nach Plan 8.4).

// ===== Skin-Auswahl =====

/**
 * Vollflächiger Skin-Picker über dunklem Scrim, im Stil der Hilfe.
 * Freigeschaltete Skins lassen sich antippen, gesperrte zeigen ihre
 * Freischalt-Bedingung. Ein Tap außerhalb schließt (und wird konsumiert,
 * damit er nicht als Spiel-Tap durchschlägt).
 *
 * [skinPass] ist der heute per Spot freigeschaltete Probier-Skin (siehe
 * ScoreStore.skinPassFor); [adOfferReady] sagt, ob dafür gerade ein Spot
 * bereitliegt. Beides ist ohne AdMob-IDs immer null bzw. false — dann
 * gibt es weder Zusatzzeilen noch anklickbare gesperrte Skins, das
 * Overlay ist Pixel für Pixel das alte.
 *
 * [patronPrice] ist der von Google gelieferte Preis des Gönner-Pakets
 * (null = nicht kaufbar, dann bleibt das Angebot unsichtbar).
 *
 * [adsAlreadyRemoved] ändert nur die Beschriftung dieses Angebots. Das
 * Paket enthält "Werbung entfernen"; wer das schon gekauft hat, würde es
 * ein zweites Mal bezahlen. Play kennt für Einmalprodukte keinen
 * Upgrade-Pfad, im Store ist das also nicht zu lösen — in der App schon:
 * Diese Gruppe liest, was für sie wirklich neu ist, nämlich die drei
 * Skins. Am Preis und am Produkt ändert sich nichts.
 */
@Composable
fun SkinOverlay(
    stats: SkinStats,
    selected: SkinId,
    onSelect: (SkinId) -> Unit,
    selectedScene: SceneId,
    onSelectScene: (SceneId) -> Unit,
    selectedSound: SoundSetId,
    onSelectSound: (SoundSetId) -> Unit,
    selectedCardFrame: CardFrame?,
    onSelectCardFrame: (CardFrame) -> Unit,
    onClose: () -> Unit,
    skinPass: SkinId? = null,
    adOfferReady: Boolean = false,
    onWatchAdFor: (SkinId) -> Unit = {},
    patronPrice: String? = null,
    adsAlreadyRemoved: Boolean = false,
    onPatron: () -> Unit = {}
) {
    // Uhr und Kalender einmal pro Öffnen ablesen, nicht pro Vorschau:
    // TAGESZEIT und JAHRESZEIT sollen in der Liste ihr heutiges Kleid
    // tragen, aber 46 Zeilen dürfen nicht 46-mal die Systemzeit fragen.
    val clock = remember { deviceHourAndMonth() }

    // Bewegte Skins laufen auch in der Liste: eine gemeinsame Uhr für
    // alle Vorschauen, gerastert auf die Kadenz von frameKey (12 Hz) —
    // feiner ändert sich kein Vogel, gröber ruckelte er sichtbar. Die
    // Rasterung sorgt zugleich dafür, dass stehende Skins nicht 60-mal
    // pro Sekunde umsonst neu gezeichnet werden.
    var previewElapsed by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var startNanos = -1L
        while (true) {
            withFrameNanos { now ->
                if (startNanos < 0) startNanos = now
                val quantized = ((now - startNanos) / 1_000_000_000f * 12f).toInt() / 12f
                if (quantized != previewElapsed) previewElapsed = quantized
            }
        }
    }
    val preview = SkinState(elapsed = previewElapsed, hour = clock.first, month = clock.second)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OutlineColor.copy(alpha = 0.92f))
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onClose() })
            }
            .windowInsetsPadding(WindowInsets.systemBars),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(vertical = 32.dp)
        ) {
            Text(
                text = stringResource(Res.string.collection),
                style = ScoreShadowStyle,
                fontSize = 32.sp,
                color = Color.White,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Die Kulissen stehen ganz oben und vor allen Skin-Familien:
            // Es sind nur sechs, sie wirken auf das ganze Bild, und wer
            // das Menü öffnet, soll sie nicht erst suchen müssen.
            SkinFamilyHeading(stringResource(Res.string.scenes))
            ScenePaint.ORDER.forEach { scene ->
                val open = ScenePaint.isUnlocked(scene, stats)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = open) { onSelectScene(scene) }
                        .padding(horizontal = 48.dp, vertical = 10.dp)
                ) {
                    Canvas(modifier = Modifier.size(36.dp)) {
                        drawScenePreview(scene, alpha = if (open) 1f else 0.3f)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = sceneTitle(scene),
                            fontFamily = Bytesized,
                            fontSize = 20.sp,
                            color = if (open) Color.White else Color.White.copy(alpha = 0.45f)
                        )
                        Text(
                            text = when {
                                scene == selectedScene -> stringResource(Res.string.skin_selected)
                                open -> stringResource(Res.string.skin_tap_select)
                                else -> sceneHint(scene) ?: ""
                            },
                            fontFamily = Bytesized,
                            fontSize = 14.sp,
                            color = when {
                                scene == selectedScene -> DotBody
                                open -> Color.White.copy(alpha = 0.7f)
                                else -> Color.White.copy(alpha = 0.45f)
                            }
                        )
                    }
                }
            }

            // Die Ton-Sets stehen direkt hinter den Kulissen und vor den
            // Skins: Es sind drei, sie wirken wie die Kulisse auf den
            // ganzen Lauf, und die Hörprobe beim Antippen soll nicht
            // hinter 42 Vogel-Zeilen liegen — wer sie hört, will sofort
            // die nächste hören.
            SkinFamilyHeading(stringResource(Res.string.sounds))
            SoundBank.ORDER.forEach { sound ->
                val open = SoundBank.isUnlocked(sound, stats)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = open) { onSelectSound(sound) }
                        .padding(horizontal = 48.dp, vertical = 10.dp)
                ) {
                    Canvas(modifier = Modifier.size(36.dp)) {
                        drawSoundPreview(sound, alpha = if (open) 1f else 0.3f)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = soundTitle(sound),
                            fontFamily = Bytesized,
                            fontSize = 20.sp,
                            color = if (open) Color.White else Color.White.copy(alpha = 0.45f)
                        )
                        Text(
                            text = when {
                                sound == selectedSound -> stringResource(Res.string.skin_selected)
                                open -> stringResource(Res.string.sound_tap_hear)
                                else -> soundHint(sound) ?: ""
                            },
                            fontFamily = Bytesized,
                            fontSize = 14.sp,
                            color = when {
                                sound == selectedSound -> DotBody
                                open -> Color.White.copy(alpha = 0.7f)
                                else -> Color.White.copy(alpha = 0.45f)
                            }
                        )
                    }
                }
            }

            // Die Rahmen zuletzt unter den drei kleinen Sammlungen: Sie
            // sind die einzige, die im Spiel selbst nicht vorkommt —
            // sichtbar wird sie erst auf der geteilten Karte.
            //
            // Die angezeigte Wahl ist nie null: Wer nie gewählt hat,
            // steht auf seiner höchsten verdienten Stufe, und genau die
            // trägt seine Karte auch.
            SkinFamilyHeading(stringResource(Res.string.frames))
            val wirksamerRahmen = CardStyle.frame(selectedCardFrame, stats)
            CardFrame.entries.forEach { frame ->
                val open = CardStyle.isUnlocked(frame, stats)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = open) { onSelectCardFrame(frame) }
                        .padding(horizontal = 48.dp, vertical = 10.dp)
                ) {
                    Canvas(modifier = Modifier.size(36.dp)) {
                        drawCardFramePreview(frame, alpha = if (open) 1f else 0.3f)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = frameTitle(frame),
                            fontFamily = Bytesized,
                            fontSize = 20.sp,
                            color = if (open) Color.White else Color.White.copy(alpha = 0.45f)
                        )
                        Text(
                            text = when {
                                frame == wirksamerRahmen -> stringResource(Res.string.skin_selected)
                                open -> stringResource(Res.string.frame_on_card)
                                else -> frameHint(frame) ?: ""
                            },
                            fontFamily = Bytesized,
                            fontSize = 14.sp,
                            color = when {
                                frame == wirksamerRahmen -> DotBody
                                open -> Color.White.copy(alpha = 0.7f)
                                else -> Color.White.copy(alpha = 0.45f)
                            }
                        )
                    }
                }
            }

            // Bei 42 Skins ist die reine Liste nicht mehr lesbar: Die
            // Familien-Überschrift sagt, wonach die nächsten Zeilen
            // funktionieren — Muster, Zeit, Spielstand, Kauf.
            var lastFamily: SkinFamily? = null

            SkinPaint.ORDER.forEach { skin ->
                if (SkinPaint.family(skin) != lastFamily) {
                    lastFamily = SkinPaint.family(skin)
                    SkinFamilyHeading(familyTitle(SkinPaint.family(skin)))
                    // Das Gönner-Angebot steht unter seiner Überschrift und
                    // nirgends sonst: Wer die Skins ansieht, ist der einzige,
                    // den es interessiert. Ohne Preis von Google (oder wenn
                    // das Paket schon gehört) bleibt die Zeile weg.
                    if (SkinPaint.family(skin) == SkinFamily.GOENNER &&
                        (stats.patronOwned || patronPrice != null)
                    ) {
                        Text(
                            text = when {
                                stats.patronOwned -> stringResource(Res.string.patron_owned)
                                adsAlreadyRemoved -> stringResource(
                                    Res.string.patron_pack_skins_only, patronPrice.orEmpty()
                                )
                                else -> stringResource(Res.string.patron_pack, patronPrice.orEmpty())
                            },
                            fontFamily = Bytesized,
                            fontSize = 15.sp,
                            color = DotBody,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .clickable(enabled = !stats.patronOwned) { onPatron() }
                                .padding(horizontal = 48.dp, vertical = 4.dp)
                        )
                    }
                }

                // "Verdient" und "heute spielbar" bleiben getrennt: Der
                // Tagespass macht den Skin nutzbar, nicht freigeschaltet.
                val available = SkinPaint.isUnlocked(skin, stats) || skin == skinPass
                val onPass = skin == skinPass && !SkinPaint.isUnlocked(skin, stats)
                // Gönner-Skins bleiben vom Tagespass ausgenommen: Ein Spot
                // darf keinen Kauf ersetzen, auch nicht für einen Tag.
                val adOffer = !available && adOfferReady && !SkinPaint.isPatron(skin)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = available || adOffer) {
                            if (available) onSelect(skin) else onWatchAdFor(skin)
                        }
                        .padding(horizontal = 48.dp, vertical = 10.dp)
                ) {
                    // Vorschau als echter Vogel statt als Farbfläche: Bei
                    // gemusterten Skins sagt ein einzelner Farbwert nichts
                    // mehr aus. Bewegte Skins laufen live mit, Uhr- und
                    // Kalender-Skins zeigen das Kleid von jetzt.
                    Canvas(modifier = Modifier.size(36.dp)) {
                        val d = size.minDimension
                        drawPixelCircle(
                            outline = OutlineColor,
                            centerX = d / 2f,
                            centerY = d / 2f,
                            radius = d / 2f,
                            alpha = if (available) 1f else 0.3f
                        ) { col, row -> Color(SkinPaint.cell(skin, col, row, preview)) }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = skinTitle(skin),
                            fontFamily = Bytesized,
                            fontSize = 20.sp,
                            color = if (available) Color.White else Color.White.copy(alpha = 0.45f)
                        )
                        Text(
                            text = when {
                                skin == selected -> stringResource(Res.string.skin_selected)
                                available -> stringResource(Res.string.skin_tap_select)
                                else -> skinHint(skin) ?: ""
                            },
                            fontFamily = Bytesized,
                            fontSize = 14.sp,
                            color = when {
                                skin == selected -> DotBody
                                available -> Color.White.copy(alpha = 0.7f)
                                else -> Color.White.copy(alpha = 0.45f)
                            }
                        )
                        // Der Spot-Hinweis bekommt eine eigene, kleinere
                        // Zeile: Die Freischalt-Bedingung ist die wichtigere
                        // Information und bleibt deshalb ungekürzt oben.
                        if (onPass || adOffer) {
                            Text(
                                text = stringResource(
                                    if (onPass) Res.string.skin_pass_today
                                    else Res.string.skin_pass_offer
                                ),
                                fontFamily = Bytesized,
                                fontSize = 12.sp,
                                color = GrassLight
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = stringResource(Res.string.tap_to_close),
                fontFamily = Bytesized,
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.6f)
            )
        }
    }
}

/**
 * Vorschau einer Kulisse auf 36 dp: Tageshimmel, Bodenkante mit Narbe
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
    // Requisite: die größte Form der Kulisse als zwei Blöcke.
    val prop = paint.props.first()
    drawRect(
        color = Color(prop.dark),
        topLeft = Offset(d * 0.22f, horizon - d * 0.22f),
        size = Size(d * 0.26f, d * 0.22f),
        alpha = alpha
    )
    drawRect(
        color = Color(prop.body),
        topLeft = Offset(d * 0.28f, horizon - d * 0.34f),
        size = Size(d * 0.16f, d * 0.14f),
        alpha = alpha
    )
    drawRect(
        color = Color(prop.light),
        topLeft = Offset(d * 0.58f, horizon - d * 0.16f),
        size = Size(d * 0.18f, d * 0.16f),
        alpha = alpha
    )
}

/**
 * Vorschau eines Ton-Sets auf 36 dp: drei Balken für Treffer, Perfekt
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

package de.robinrehbein.punkt.ui

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import de.robinrehbein.punkt.game.GamePhase
import de.robinrehbein.punkt.game.SoundSetId
import de.robinrehbein.punkt.game.TimingGame
import de.robinrehbein.punkt.ui.data.DeviceCalendar
import de.robinrehbein.punkt.ui.data.FakeKeyValueStore
import de.robinrehbein.punkt.ui.data.GameStore
import de.robinrehbein.punkt.ui.data.fixedCalendarForTools
import de.robinrehbein.punkt.ui.platform.GameFeedback
import de.robinrehbein.punkt.ui.platform.GameSounds
import de.robinrehbein.punkt.ui.screens.GameScreen
import de.robinrehbein.punkt.ui.screens.ProbeScene
import java.io.File
import kotlin.random.Random
import kotlin.test.Test
import org.jetbrains.skia.EncodedImageFormat

/**
 * Headless-Screenshots der geteilten Oberflaeche: rendert GameScreen mit
 * Skia in Geraetegroesse (Standard 1080x2400 bei 2.625x) und schreibt PNGs
 * in das Verzeichnis aus der Umgebungsvariable SHOTS_DIR. Ohne sie tut der
 * Test nichts — er ist ein Werkzeug, kein Pruefstein. Gradle kennt
 * SHOTS_DIR nicht als Eingabe, deshalb immer mit --rerun starten.
 *
 * Reproduzierbar: Das Spiel ist geseedet (auch jeder freie Lauf, siehe
 * GameScreen.runSeed), die Uhr steht fest ([FIXED_CALENDAR]) und die
 * Bildzeit laeuft in festen Schritten. Zwei Laeufe mit demselben Seed
 * liefern dieselben Bilder.
 */
class ScreenshotRenderer {

    private class NoSounds : GameSounds {
        override var muted = false
        override var soundSet = SoundSetId.KLASSIK
        override fun start() {}
        override fun hit(score: Int) {}
        override fun perfect(streak: Int) {}
        override fun chain() {}
        override fun unlock() {}
        override fun death() {}
        override fun thud() {}
        override fun newRecord() {}
        override fun preview(set: SoundSetId) {}
        override fun release() {}
    }

    private class NoFeedback : GameFeedback {
        override fun score() {}
        override fun perfect() {}
        override fun unlock() {}
        override fun death() {}
        override fun thud() {}
        override fun newRecord() {}
    }

    @Test
    fun render() {
        val dir = System.getProperty("shots.dir") ?: System.getenv("SHOTS_DIR") ?: return
        renderSet(File(dir), width = 1080, height = 2400, density = 2.625f, seed = SEED)
        // == AP-31 release ==
        // Derselbe Ablauf auf einem kleinen Telefon (360x640 dp).
        renderSet(File(dir, "720x1280"), width = 720, height = 1280, density = 2f, seed = SEED)
        // == /AP-31 ==
    }

    // == AP-22 start ==
    /**
     * Die Bilder des Startbildschirms (Plan 8.5 AP-22) bei 1080x2400 und
     * 720x1280: Hand oben und gedrückt, NOCH NICHT, die DAILY-Karte,
     * DAILY scharf, das Leuchten im Lauf und ein Bestandsspieler ohne
     * Stützräder. Die kleinen Bilder liegen im Unterordner 720x1280.
     */
    @Test
    fun renderStart() {
        val dir = System.getProperty("shots.dir") ?: System.getenv("SHOTS_DIR") ?: return
        java.util.Locale.setDefault(java.util.Locale.GERMANY)
        val vorher = fixedCalendarForTools
        fixedCalendarForTools = FIXED_CALENDAR
        try {
            renderStartSet(File(dir), 1080, 2400, 2.625f)
            renderStartSet(File(dir, "720x1280"), 720, 1280, 2f)
        } finally {
            fixedCalendarForTools = vorher
            // Die geschlossenen Szenen geben ihren nativen Speicher erst
            // frei, wenn die JVM aufräumt. Die übrigen Screenshot-Tests
            // laufen im selben Prozess und sollen ihn nicht erben.
            System.gc()
        }
    }

    private fun renderStartSet(dir: File, w: Int, h: Int, d: Float) {
        dir.mkdirs()
        var now = 0L
        // Jedes Zwischenbild sofort schließen: Die Skia-Bilder liegen im
        // nativen Speicher, den die JVM nicht sieht. Ohne close() wuchs der
        // Test-Executor bei den Warteschleifen auf über 7 GB und wurde
        // vom System beendet (Exit 137).
        fun ImageComposeScene.step(seconds: Double) {
            val until = now + (seconds * 1_000_000_000L).toLong()
            while (now < until) {
                now += 16L * 1_000_000L
                render(now).close()
            }
        }
        fun ImageComposeScene.tap(x: Float, y: Float) {
            sendPointerEvent(PointerEventType.Press, Offset(x, y))
            step(0.05)
            sendPointerEvent(PointerEventType.Release, Offset(x, y))
            step(0.05)
        }
        fun ImageComposeScene.save(name: String) {
            val img = render(now)
            try {
                val data = img.encodeToData(EncodedImageFormat.PNG)!!
                File(dir, name).writeBytes(data.bytes)
                data.close()
            } finally {
                img.close()
            }
            println("-> ${dir.name}/$name")
        }

        // Neuer Spieler: Stützräder an (runCount 0).
        val game = TimingGame(Random(SEED))
        ImageComposeScene(width = w, height = h, density = Density(d)) {
            GameScreen(
                store = GameStore(FakeKeyValueStore()),
                sounds = NoSounds(),
                feedback = NoFeedback(),
                game = game,
                runSeed = SEED
            )
        }.use { scene ->
            // READY_SPEED 1,2 rad/s ab Winkel 0, Zone 1,8 ± 0,4: Bei 0,4 s
            // ist der Punkt weit vor dem Grün, die Hand oben.
            scene.step(0.4)
            scene.save("20-start-hand-oben.png")
            // Tap daneben (links neben dem Ring): NOCH NICHT und Echo.
            scene.tap(w * 0.3f, h * 0.44f)
            scene.step(0.1)
            check(game.phase == GamePhase.READY) { "der Tap daneben hat einen Lauf gestartet" }
            scene.save("21-noch-nicht.png")
            // Bei 1,35 s steht der Punkt im Grün: Hand gedrückt, Zone
            // leuchtet, Echo an der Fingerspitze.
            scene.step(1.35 - 0.4 - 0.2)
            scene.save("22-start-hand-gedrueckt.png")
            // DAILY: linkes Drittel der Taster-Leiste -> Karte
            scene.tap(w / 6f, h - 32 * d)
            scene.step(0.3)
            scene.save("23-daily-karte.png")
            // START auf der Karte (der Knopf sitzt unten im Panel).
            scene.tap(w / 2f, h / 2f + DAILY_START_OFFSET_DP * d)
            scene.step(0.3)
            scene.save("24-daily-scharf.png")
            // Start per Tap im Grün, dann im Lauf auf das nächste Grün warten.
            var frames = 0
            while (!game.isInZone && frames++ < 1_000) scene.step(0.016)
            scene.tap(w / 2f, h * 0.5f)
            frames = 0
            while (game.phase == GamePhase.RUNNING && !game.isInZone && frames++ < 1_000) {
                scene.step(0.016)
            }
            scene.step(0.03)
            scene.save("25-lauf-leuchten.png")
        }

        // Bestandsspieler: fünf Läufe gezählt, Rekord 12. Keine Hand, kein
        // Leuchten, die Zielzeile ist da.
        val prefs = FakeKeyValueStore()
        val bestand = GameStore(prefs)
        repeat(5) { bestand.submitRun(score = 12, epochDay = FIXED_CALENDAR.epochDay, month = 6, year = 2026) }
        now = 0L
        val game2 = TimingGame(Random(SEED))
        ImageComposeScene(width = w, height = h, density = Density(d)) {
            GameScreen(
                store = bestand,
                sounds = NoSounds(),
                feedback = NoFeedback(),
                game = game2,
                runSeed = SEED
            )
        }.use { scene ->
            scene.step(1.5)
            scene.save("26-start-bestand.png")
            // NOCH NICHT ohne Stützräder: Bei 1,5 s steht der Punkt im
            // Grün, ein Tap dort würde starten. Erst warten, bis er das
            // Grün verlassen hat, dann daneben tippen.
            var frames = 0
            while (game2.isInZone && frames++ < 1_000) scene.step(0.016)
            scene.step(0.1)
            check(!game2.isInZone) { "der Punkt steht noch im Grün" }
            scene.tap(w * 0.3f, h * 0.44f)
            scene.step(0.1)
            check(game2.phase == GamePhase.READY) { "der Tap daneben hat einen Lauf gestartet" }
            check(game2.score == 0) { "der Tap daneben hat gezählt" }
            scene.save("27-bestand-noch-nicht.png")
        }
    }
    // == /AP-22 ==

    /**
     * Der Screenshot-Satz fuer eine Geraetegroesse. Breite und Hoehe in
     * Pixeln, [density] in Pixeln je dp — alle Tippstellen rechnen damit,
     * damit derselbe Ablauf auch auf 720x1280 trifft.
     */
    fun renderSet(dir: File, width: Int, height: Int, density: Float, seed: Long) {
        dir.mkdirs()
        java.util.Locale.setDefault(java.util.Locale.GERMANY)
        val vorher = fixedCalendarForTools
        fixedCalendarForTools = FIXED_CALENDAR
        try {
            renderSetWithFixedClock(dir, width, height, density, seed)
        } finally {
            fixedCalendarForTools = vorher
        }
    }

    private fun renderSetWithFixedClock(
        dir: File,
        width: Int,
        height: Int,
        density: Float,
        seed: Long
    ) {
        val w = width
        val h = height
        val d = density
        // == AP-31 release ==
        // Die Szene aus den Sammlungs-Bildern (AP-15): Sie zeichnet in eine
        // einzige Fläche, statt je Bild ein Skia-Bild anzulegen (Speicher,
        // Wunsch AP-22), und findet Knöpfe über ihre Beschriftung. So trifft
        // HILFE in den Einstellungen auch auf 720x1280, ohne abgezählte Pixel.
        fun ProbeScene.tap(x: Float, y: Float) = tapAt(Offset(x, y))
        fun ProbeScene.save(name: String) = save(File(dir, name))
        // Ein Tap per Beschriftung, der wirkt: Gradle-Läufe nebeneinander
        // bremsen die Test-JVM so, dass zwischen Drücken und Loslassen
        // echte Zeit über der Langdruck-Grenze vergehen kann. Dann wird
        // aus dem Tap kein Klick. Wiederholen, bis sich das Bild ändert.
        fun ProbeScene.tapSure(label: String) {
            val vorher = labels()
            repeat(3) {
                tap(label)
                step(0.1)
                if (labels() != vorher) return
            }
            error("„$label“ wirkt nicht: ${labels()}")
        }
        // == /AP-31 ==

        val game = TimingGame(Random(seed))
        ProbeScene(w, h, d) {
            GameScreen(
                store = GameStore(FakeKeyValueStore()),
                sounds = NoSounds(),
                feedback = NoFeedback(),
                game = game,
                runSeed = seed
            )
        }.use { scene ->
            // READY: kurz laufen lassen, damit Vogel und Blinken stehen.
            // Nach 1,5 s steht der Vogel mitten im Grün (READY_SPEED 1,2
            // rad/s, Zone bei 1,8 ± 0,4): Unter der Startregel zählt nur
            // ein Tap im Grün. Bei 1,2 s läge er am Zonenrand.
            scene.step(1.5)
            scene.save("01-ready.png")

            // Lauf starten (Treffer 1), 0,5 s kreisen lassen. Der Start
            // ist jetzt ein Treffer, die neue Zone liegt 1,1-2,8 rad voraus:
            // Nach 1 s wäre der Vogel je nach Seed schon vorbei und das Bild
            // zeigte den Todesblitz. Frühestens nach ≈ 0,69 s ist er vorbei.
            scene.tap(w / 2f, h * 0.5f)
            scene.step(0.5)
            scene.save("02-running.png")

            // Sterben: Zone einfach nicht antippen und ueberfahren lassen,
            // dann Freeze + Sturz abwarten -> Game-Over steht.
            // == AP-14 bedienung ==
            // Zwei Zeitpunkte nach dem Aufschlag: bei 0,3 s ist die Leiste
            // unten noch gesperrt (blass), bei 1,0 s ist sie frei.
            var frames = 0
            while (game.phase != GamePhase.OVER && frames++ < 1_000) scene.step(0.016)
            scene.step(0.3)
            scene.save("03a-gameover-gesperrt.png")
            scene.step(0.7)
            scene.save("03-gameover.png")
            // == /AP-14 ==

            // == AP-14 bedienung ==
            // Zurueck ins Menue: MENUE in der Leiste am unteren Rand. Über
            // die Beschriftung getippt (AP-31): Auf 720x1280 lag der feste
            // Punkt w/4 neben dem Knopf.
            scene.tapSure("MENÜ")
            scene.step(0.5)
            // == /AP-14 ==
            scene.save("05-menu-back.png")

            // == AP-31 release ==
            // Einstellungen: Regler-Knopf oben rechts im READY. Die Hilfe
            // gibt es nur noch hier (das „?“ im Game-Over ist weg, Plan 7.3).
            scene.tapSure("EINSTELLUNGEN")
            scene.step(0.6)
            scene.save("06-settings.png")
            scene.tapSure("HILFE")
            scene.step(0.6)
            scene.save("04-help.png")
            // Hilfe über das X schließen: zurück im Startbildschirm.
            scene.tapSure("SCHLIESSEN")
            scene.step(0.3)

            // Sammlung: Taster-Leiste unten, mittleres Drittel
            scene.tapSure("SAMMLUNG")
            scene.step(0.8)
            scene.save("07-sammlung.png")
            scene.tapSure("SCHLIESSEN")
            scene.step(0.3)
            // == /AP-31 ==

            // Statistik: Taster-Leiste unten, rechtes Drittel
            scene.tapSure("STATISTIK")
            scene.step(0.8)
            scene.save("08-stats.png")
        }
    }

    private companion object {
        // == AP-22 start ==
        /** Abstand des START-Knopfs der DAILY-Karte unter der Bildmitte, in dp. */
        const val DAILY_START_OFFSET_DP = 95f
        // == /AP-22 ==

        /** Der Seed der Standard-Bilder. */
        const val SEED = 20260925L

        /** Mittwoch, 17. Juni 2026, 12 Uhr: Tageshimmel, kein Saison-Monat. */
        val FIXED_CALENDAR = DeviceCalendar(
            epochDay = java.time.LocalDate.of(2026, 6, 17).toEpochDay(),
            month = 6,
            year = 2026,
            hour = 12
        )
    }
}

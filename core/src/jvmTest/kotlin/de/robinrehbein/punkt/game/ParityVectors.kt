package de.robinrehbein.punkt.game

import java.util.Locale
import kotlin.random.Random

/**
 * Erzeugt die Paritäts-Vektoren in `parity/golden-vectors.txt` — den
 * Golden-Master von `:core`.
 *
 * Ursprünglich waren sie ein Vertrag zwischen zwei Fassungen der Engine:
 * Kotlin hier, Swift unter `ios/`. Was in Kotlin getestet war, sagte
 * über den Handport nichts aus — ausgerechnet Kotlins Zufallsgenerator
 * musste dort bit-genau nachgebaut sein, damit iPhone und Android an
 * demselben Tag dieselbe Daily Challenge spielen.
 *
 * Seit v2.24 linkt iOS `:core` selbst, und es gibt keinen zweiten Nachbau
 * mehr. Die Datei bleibt trotzdem: Sie zeigt jede Verhaltensänderung als
 * Diff, bevor sie in drei Apps landet. Genau so hat sie zuletzt
 * gearbeitet — eine geänderte Farbrundung verschob 78 Zeilen um je eine
 * Kanalstufe, sichtbar statt still.
 *
 * Format: eine Zeile pro Wert, `schlüssel wert…`, durch Leerzeichen
 * getrennt, `#` leitet einen Kommentar ein. Bewusst so simpel, dass es
 * jede Sprache ohne JSON-Bibliothek lesen kann.
 */
object ParityVectors {

    /** Bei jeder Formatänderung hochzählen. */
    const val VERSION = 6

    /** Seed der Vektoren — irgendein fester Tag, nichts Magisches. */
    private const val SEED = 20240813L

    /** Rasterfelder, an denen Skin-Farben abgetastet werden. */
    private val SAMPLE_CELLS = listOf(
        2 to 6, 4 to 3, 6 to 2, 6 to 6, 8 to 5, 9 to 8, 6 to 10, 10 to 6
    )

    /**
     * Zustände, in denen die Skins abgetastet werden. Deckt alles ab,
     * woran eine Skin-Farbe hängen kann: Zeit (bewegte Skins), Score
     * (CHAMÄLEON, THERMO, MEDAILLE), Perfekt-Serie (KOMBO), Uhrzeit
     * (TAGESZEIT) und Monat (JAHRESZEIT).
     */
    private val SAMPLE_STATES = listOf(
        SkinState(),
        SkinState(elapsed = 1.75f, score = 23, perfectStreak = 3, hour = 20, month = 10),
        SkinState(elapsed = 4.5f, score = 61, perfectStreak = 5, hour = 3, month = 2)
    )

    /**
     * Bestleistungen, mit denen die Freischalt-Regeln abgeklopft werden —
     * je Achse an ihrer Kante und einmal alles zusammen. Seit die Skins
     * auch an Ausdauer hängen (Läufe, Punkte insgesamt, gespielte Tage und
     * Monate) sowie an Saison-Maske und Kauf, sind das neun Felder statt
     * drei; die Datei führt sie deshalb durchnummeriert.
     */
    private val UNLOCK_PROBES = listOf(
        SkinStats(0, 0, 0),
        SkinStats(9, 0, 0),
        SkinStats(10, 0, 0),
        SkinStats(25, 0, 0),
        SkinStats(30, 0, 0),
        SkinStats(40, 3, 0),
        SkinStats(60, 0, 0),
        SkinStats(80, 0, 0),
        SkinStats(0, 4, 0),
        SkinStats(0, 8, 0),
        SkinStats(0, 12, 0),
        SkinStats(0, 15, 0),
        SkinStats(0, 0, 3),
        SkinStats(0, 0, 7),
        SkinStats(0, 0, 14),
        SkinStats(0, 0, 21),
        // Ausdauer-Achsen, jeweils knapp darunter und genau auf der Kante.
        SkinStats(0, 0, 0, runCount = 24),
        SkinStats(0, 0, 0, runCount = 25),
        SkinStats(0, 0, 0, runCount = 100),
        SkinStats(0, 0, 0, runCount = 300),
        SkinStats(0, 0, 0, totalScore = 999),
        SkinStats(0, 0, 0, totalScore = 1_000),
        SkinStats(0, 0, 0, totalScore = 5_000),
        SkinStats(0, 0, 0, daysPlayed = 7),
        SkinStats(0, 0, 0, monthsPlayed = 3),
        // Saison: nur die Maske entscheidet, nie der Kalender.
        SkinStats(0, 0, 0, seasonEarned = 1),
        SkinStats(0, 0, 0, seasonEarned = 0b1111),
        // Kauf schaltet die Gönner-Skins frei und sonst nichts.
        SkinStats(0, 0, 0, patronOwned = true),
        // Alles verdient: hier muss auch der Regenbogen fallen.
        SkinStats(
            bestScore = 80, bestPerfectStreak = 15, bestDailyStreak = 21,
            runCount = 300, totalScore = 5_000, daysPlayed = 7, monthsPlayed = 3
        ),
        SkinStats(
            bestScore = 80, bestPerfectStreak = 15, bestDailyStreak = 21,
            runCount = 300, totalScore = 5_000, daysPlayed = 7, monthsPlayed = 3,
            seasonEarned = 0b1111, patronOwned = true
        ),
        // Die Kulissen (ScenePaint) hängen an denselben Achsen, aber an
        // deutlich höheren Schwellen als die Skins — ohne eigene Proben
        // wäre von ihnen nur "alles zu" geprüft. Je Kulisse einmal knapp
        // darunter, einmal genau auf der Kante.
        SkinStats(0, 0, 0, runCount = 499),
        SkinStats(0, 0, 0, runCount = 500),
        SkinStats(0, 0, 0, totalScore = 9_999),
        SkinStats(0, 0, 0, totalScore = 10_000),
        SkinStats(0, 0, 29),
        SkinStats(0, 0, 30),
        SkinStats(84, 0, 0),
        SkinStats(85, 0, 0),
        // Alle vier zusammen: erst hier fällt auch der WELTRAUM.
        SkinStats(
            bestScore = 85, bestPerfectStreak = 0, bestDailyStreak = 30,
            runCount = 500, totalScore = 10_000
        ),
        // Die Ton-Sets (SoundBank) liegen noch einmal höher und auf
        // Zahlen, auf denen sonst nichts liegt — ohne eigene Proben wäre
        // von ihnen nur "alles zu" geprüft.
        SkinStats(0, 19, 0),
        SkinStats(0, 20, 0),
        SkinStats(0, 0, 0, totalScore = 24_999),
        SkinStats(0, 0, 0, totalScore = 25_000),
        // Die Kartenrahmen (CardStyle) sind die vierte Sammlung und die
        // einzige, die auf den anderen aufbaut: Ihre oberen drei Stufen
        // verlangen alle Kulissen, alle Töne und alle Skins. So weit
        // reicht keine Probe darüber — diese vier tun es, je eine knapp
        // unter einer der drei Vollständigkeiten und eine darüber.
        SkinStats(
            bestScore = 84, bestPerfectStreak = 19, bestDailyStreak = 30,
            runCount = 500, totalScore = 25_000, daysPlayed = 7, monthsPlayed = 3
        ),
        SkinStats(
            bestScore = 85, bestPerfectStreak = 19, bestDailyStreak = 30,
            runCount = 500, totalScore = 25_000, daysPlayed = 7, monthsPlayed = 3
        ),
        SkinStats(
            bestScore = 85, bestPerfectStreak = 20, bestDailyStreak = 30,
            runCount = 500, totalScore = 25_000, daysPlayed = 7, monthsPlayed = 0
        ),
        SkinStats(
            bestScore = 85, bestPerfectStreak = 20, bestDailyStreak = 30,
            runCount = 500, totalScore = 25_000, daysPlayed = 7, monthsPlayed = 3
        )
    )

    /**
     * Felder, an denen das Rahmenmuster abgetastet wird — auf einem Blatt
     * von 180 mal 225 Feldern, also der Score-Karte in ihrem Raster.
     *
     * Die Auswahl ist kein Zufall: eine Diagonale in die Ecke hinein
     * (dort liegen die Ornamente), ein Querschnitt durch die obere Kante
     * (dort liegen die Bänder), sechs Felder nebeneinander in der
     * Zahnreihe (dort liegt ihr Takt) und ein Querschnitt durch die
     * linke Kante samt gegenüberliegender Ecke (dort fällt auf, wenn ein
     * Renderer nur eine Seite spiegelt).
     */
    private val FRAME_CELLS = listOf(
        0 to 0, 3 to 3, 9 to 9, 17 to 17,
        90 to 0, 90 to 2, 90 to 4, 90 to 6, 90 to 8,
        90 to 10, 90 to 12, 90 to 14, 90 to 16, 90 to 18,
        91 to 3, 92 to 3, 93 to 3, 94 to 3, 95 to 3, 96 to 3,
        0 to 112, 3 to 112, 9 to 112, 17 to 112,
        179 to 224, 176 to 221
    )

    /** Kartenbreite und -höhe in Feldern (1080 mal 1350 Pixel zu je 6). */
    private const val CARD_COLS = 180
    private const val CARD_ROWS = 225

    fun build(): String {
        val out = StringBuilder()
        out.header()
        out.constants()
        out.medals()
        out.sky()
        out.skins()
        out.scenes()
        out.sounds()
        out.frames()
        out.progress()
        out.rng()
        out.traces()
        return out.toString()
    }

    // ===== Abschnitte =====

    private fun StringBuilder.header() {
        appendLine("# Dottie. — Paritäts-Vektoren: der Golden-Master der Engine in :core.")
        appendLine("#")
        appendLine("# ERZEUGT — nicht von Hand bearbeiten. Neu schreiben mit:")
        appendLine("#   ./gradlew :core:jvmTest -Dparity.update=true")
        appendLine("#")
        appendLine("# Quelle der Wahrheit ist :core. Ändert sich hier eine Zahl, ist das")
        appendLine("# die Ansage an ios/, nachzuziehen — siehe parity/README.md.")
        appendLine()
        line("version", VERSION.toString())
        appendLine()
    }

    private fun StringBuilder.constants() {
        section("Konstanten der Engine (TimingGame). Namen wie in Kotlin.")
        line("const.MAX_DELTA", f(TimingGame.MAX_DELTA))
        line("const.BASE_SPEED", f(TimingGame.BASE_SPEED))
        line("const.SPEED_PER_HIT", f(TimingGame.SPEED_PER_HIT))
        line("const.MAX_SPEED", f(TimingGame.MAX_SPEED))
        line("const.READY_SPEED", f(TimingGame.READY_SPEED))
        line("const.BASE_ZONE_HALF", f(TimingGame.BASE_ZONE_HALF))
        line("const.ZONE_SHRINK_PER_HIT", f(TimingGame.ZONE_SHRINK_PER_HIT))
        line("const.MIN_ZONE_HALF", f(TimingGame.MIN_ZONE_HALF))
        line("const.PERFECT_SHARE", f(TimingGame.PERFECT_SHARE))
        // Die Bahn-Blöcke sind Spielregel, seit der PERFEKT-Kern nie
        // schmaler wird als ein halber Block — dreifach nachgebaut und
        // bis hierher im Vertrag nicht vorgekommen.
        line("const.TRACK_SEGMENTS", TimingGame.TRACK_SEGMENTS.toString())
        line("const.SEGMENT_HALF", f(TimingGame.SEGMENT_HALF))
        line("const.MIN_ZONE_DISTANCE", f(TimingGame.MIN_ZONE_DISTANCE))
        line("const.MAX_ZONE_DISTANCE", f(TimingGame.MAX_ZONE_DISTANCE))
        line("const.MIN_REACTION_SECONDS", f(TimingGame.MIN_REACTION_SECONDS))
        line("const.LATE_TAP_FORGIVENESS_SECONDS", f(TimingGame.LATE_TAP_FORGIVENESS_SECONDS))
        line("const.PASS_BUFFER_SECONDS", f(TimingGame.PASS_BUFFER_SECONDS))
        line("const.PERFECT_BASE_SCORE", TimingGame.PERFECT_BASE_SCORE.toString())
        line("const.PERFECT_MAX_SCORE", TimingGame.PERFECT_MAX_SCORE.toString())
        line("const.MAX_ACTIVE_TWISTS", TimingGame.MAX_ACTIVE_TWISTS.toString())
        line("const.TWIST_PROBABILITY", f(TimingGame.TWIST_PROBABILITY))
        line("const.PULSE_SPEED", f(TimingGame.PULSE_SPEED))
        line("const.PULSE_MIN_SHARE", f(TimingGame.PULSE_MIN_SHARE))
        line("const.DRIFT_SPEED", f(TimingGame.DRIFT_SPEED))
        line("const.GHOST_BLINK_SPEED", f(TimingGame.GHOST_BLINK_SPEED))
        line("const.GHOST_VISIBLE_SHARE", f(TimingGame.GHOST_VISIBLE_SHARE))
        line("const.FAKE_MIN_DISTANCE", f(TimingGame.FAKE_MIN_DISTANCE))
        line("const.CHAIN_LENGTH", TimingGame.CHAIN_LENGTH.toString())
        line("const.CHAIN_MIN_DISTANCE", f(TimingGame.CHAIN_MIN_DISTANCE))
        line("const.CHAIN_MAX_DISTANCE", f(TimingGame.CHAIN_MAX_DISTANCE))
        line("const.DEATH_FREEZE_SECONDS", f(TimingGame.DEATH_FREEZE_SECONDS))
        line("const.DEATH_FALL_SECONDS", f(TimingGame.DEATH_FALL_SECONDS))
        line("const.RESTART_LOCK_SECONDS", f(TimingGame.RESTART_LOCK_SECONDS))
        appendLine()

        section("Ab welchem Score ein Twist ins Spiel kommt.")
        Twist.entries.forEach {
            line("twist.unlock.${it.name}", TimingGame.unlockScore(it).toString())
        }
        line(
            "twist.forbidden",
            TimingGame.FORBIDDEN_COMBOS.joinToString(" ") { combo ->
                combo.map { it.name }.sorted().joinToString("+")
            }
        )
        appendLine()

        section("Daily Challenge: Tages-Seed und Serien-Regeln.")
        listOf(0L, 1L, 19000L, 20000L, -5L).forEach {
            line("daily.seed.$it", DailyChallenge.seedFor(it).toString())
        }
        listOf(
            Triple(0L, 0, 19000L),
            Triple(19000L, 4, 19000L),
            Triple(18999L, 4, 19000L),
            Triple(18990L, 9, 19000L)
        ).forEach { (last, streak, today) ->
            line(
                "daily.streak.$last.$streak.$today",
                DailyChallenge.nextStreak(last, streak, today).toString()
            )
        }
        appendLine()
    }

    private fun StringBuilder.medals() {
        section("Medaillen: Schwellen und Münzfarben (MedalPaint).")
        MedalId.entries.forEach {
            line(
                "medal.${it.name}",
                MedalPaint.threshold(it).toString(),
                argb(MedalPaint.body(it)),
                argb(MedalPaint.shade(it))
            )
        }
        listOf(0, 9, 10, 19, 20, 29, 30, 39, 40, 999).forEach { score ->
            line(
                "medal.forScore.$score",
                MedalPaint.forScore(score)?.name ?: "-",
                MedalPaint.next(score)?.name ?: "-"
            )
        }
        appendLine()
    }

    private fun StringBuilder.sky() {
        section("Himmelsstufen und ihr Umlauf (SkinPaint).")
        line("sky.cycle", SkinPaint.SKY_CYCLE.toString())
        line("sky.stages", *SkinPaint.SKY_STAGES.map { argb(it) }.toTypedArray())
        line(
            "sky.stageForScore",
            *(0..70 step 5).map { SkinPaint.skyStage(it).toString() }.toTypedArray()
        )
        appendLine()
    }

    private fun StringBuilder.skins() {
        section("Skins: Reihenfolge, Stellvertreterfarben, Eigenschaften.")
        line("skin.order", *SkinId.entries.map { it.name }.toTypedArray())
        line("skin.grid", SkinPaint.GRID.toString())
        line("skin.collectableCount", SkinPaint.collectableCount().toString())
        SkinId.entries.forEach { id ->
            line(
                "skin.chips.${id.name}",
                argb(SkinPaint.body(id)),
                argb(SkinPaint.shade(id)),
                argb(SkinPaint.shine(id)),
                if (SkinPaint.hasTrail(id)) "trail" else "-",
                if (SkinPaint.needsEyeOutline(id)) "eyeoutline" else "-",
                if (SkinPaint.isAnimated(id)) "animated" else "-",
                if (SkinPaint.isSeasonal(id)) "seasonal" else "-",
                if (SkinPaint.isPatron(id)) "patron" else "-",
                if (SkinPaint.countsForCollection(id)) "collectable" else "-"
            )
        }
        appendLine()

        section(
            "Saison-Skins: Monat, Bit in SkinStats.seasonEarned und die\n" +
                "# Anzahl Tage, die der Monat verlangt."
        )
        Season.entries.forEach { season ->
            line(
                "season.${season.skin.name}",
                season.month.toString(),
                season.bit.toString(),
                season.requiredDays.toString()
            )
        }
        line(
            "season.forMonth",
            *(1..12).map { Season.forMonth(it)?.skin?.name ?: "-" }.toTypedArray()
        )
        appendLine()

        section(
            "Abgetastete Rasterfarben je Skin und Zustand — die Felder sind\n" +
                "# ${SAMPLE_CELLS.joinToString(" ") { "(${it.first},${it.second})" }}.\n" +
                "# Zustand: elapsed score perfectStreak hour month."
        )
        SAMPLE_STATES.forEachIndexed { index, state ->
            line(
                "skin.state.$index",
                f(state.elapsed),
                state.score.toString(),
                state.perfectStreak.toString(),
                state.hour.toString(),
                state.month.toString()
            )
            SkinId.entries.forEach { id ->
                line(
                    "skin.cells.$index.${id.name}",
                    *SAMPLE_CELLS.map { (col, row) ->
                        argb(SkinPaint.cell(id, col, row, state))
                    }.toTypedArray()
                )
            }
            SkinId.entries.forEach { id ->
                line("skin.shine.$index.${id.name}", argb(SkinPaint.shine(id, state)))
            }
        }
        appendLine()

        section(
            "Freischaltungen. Je Probe zwei Zeilen: skin.probe.N traegt die\n" +
                "# Bestleistungen (bestScore bestPerfectStreak bestDailyStreak\n" +
                "# runCount totalScore daysPlayed monthsPlayed seasonEarned\n" +
                "# patronOwned), skin.unlocked.N die Zahl der sammelbaren Skins,\n" +
                "# dahinter alle offenen."
        )
        UNLOCK_PROBES.forEachIndexed { index, stats ->
            line(
                "skin.probe.$index",
                stats.bestScore.toString(),
                stats.bestPerfectStreak.toString(),
                stats.bestDailyStreak.toString(),
                stats.runCount.toString(),
                stats.totalScore.toString(),
                stats.daysPlayed.toString(),
                stats.monthsPlayed.toString(),
                stats.seasonEarned.toString(),
                if (stats.patronOwned) "1" else "0"
            )
            val open = SkinId.entries.filter { SkinPaint.isUnlocked(it, stats) }
            line(
                "skin.unlocked.$index",
                SkinPaint.unlockedCount(stats).toString(),
                *open.map { it.name }.toTypedArray()
            )
        }
        appendLine()
    }

    private fun StringBuilder.scenes() {
        section(
            "Kulissen (ScenePaint) — die zweite Sammlung. Farben, Boden und\n" +
                "# Requisiten stehen als Datentabelle, die Freischaltung als Regel."
        )
        line("scene.order", *SceneId.entries.map { it.name }.toTypedArray())
        line("scene.groundTop", f(ScenePaint.GROUND_TOP))
        line("scene.propSlots", ScenePaint.PROP_SLOTS.toString())
        line("scene.minZoneDistance", f(ScenePaint.MIN_ZONE_DISTANCE))
        line("scene.minSkyStep", f(ScenePaint.MIN_SKY_STEP))
        // Fels und Laterne stehen hier, weil sie als einzige
        // Requisitenformen Datentabellen sind statt Zeichencode: Die
        // Renderer lesen dieselben Rechtecke. Läuft einer davon weg,
        // sieht man es an keinem Farbwert — nur daran, dass die Form
        // anders aussieht.
        line("scene.rockSize", f(ScenePaint.ROCK_WIDTH), f(ScenePaint.ROCK_HEIGHT))
        ScenePaint.ROCK_PARTS.forEachIndexed { index, part ->
            line(
                "scene.rock.$index",
                f(part.x), f(part.y), f(part.w), f(part.h), part.tone.toString()
            )
        }
        line("scene.lanternSize", f(ScenePaint.LANTERN_WIDTH), f(ScenePaint.LANTERN_HEIGHT))
        ScenePaint.LANTERN_PARTS.forEachIndexed { index, part ->
            line(
                "scene.lantern.$index",
                f(part.x), f(part.y), f(part.w), f(part.h), part.tone.toString()
            )
        }
        // MIN_SKY_SIGNAL_DISTANCE steht bewusst NICHT hier: Die Schwelle
        // ist eine Zusicherung über die Farbtabellen (ScenePaintTest prüft
        // sie in :core), kein Wert, den ein Port nachbaut. Eine Zeile, die
        // niemand gegenprüft, sähe nach Abdeckung aus, wo keine ist.

        SceneId.entries.forEach { id ->
            val scene = ScenePaint.of(id)
            line("scene.sky.${id.name}", *scene.sky.map { argb(it) }.toTypedArray())
            line("scene.cloud.${id.name}", scene.cloud?.let { argb(it) } ?: "-")
            line(
                "scene.ground.${id.name}",
                *scene.ground?.let {
                    arrayOf(argb(it.sand), argb(it.sandShade), argb(it.turfDark), argb(it.turfLight))
                } ?: arrayOf("-")
            )
            line("scene.chips.${id.name}", *ScenePaint.chips(id).map { argb(it) }.toTypedArray())
            // Requisiten: Form, Größe, Schwingen und die drei Farbstufen.
            // Der Stiel und die Akzente hängen daran, dass ein Renderer sie
            // überhaupt kennt — deshalb stehen sie mit in der Zeile.
            scene.props.forEachIndexed { index, prop ->
                line(
                    "scene.prop.${id.name}.$index",
                    prop.shape.name,
                    f(prop.size),
                    f(prop.sway),
                    argb(prop.dark),
                    argb(prop.body),
                    argb(prop.light),
                    argb(prop.stem),
                    argb(prop.stemShade),
                    if (prop.accents.isEmpty()) "-" else prop.accents.joinToString(",") { argb(it) }
                )
            }
        }
        line(
            "scene.skyForScore.WIESE",
            *(0..70 step 5).map { argb(ScenePaint.skyFor(SceneId.WIESE, it)) }.toTypedArray()
        )
        appendLine()

        section("Freischaltung der Kulissen — dieselben Proben wie bei den Skins.")
        UNLOCK_PROBES.forEachIndexed { index, stats ->
            val open = SceneId.entries.filter { ScenePaint.isUnlocked(it, stats) }
            line(
                "scene.unlocked.$index",
                ScenePaint.unlockedCount(stats).toString(),
                *open.map { it.name }.toTypedArray()
            )
        }
        appendLine()
    }

    private fun StringBuilder.sounds() {
        section(
            "Ton-Sets (SoundBank) — die dritte Sammlung. Jeder Ton steht\n" +
                "# als Zahlenreihe da, damit kein Port seine eigene Synthese\n" +
                "# erfindet: fromHz toHz Sekunden Lautstärke Abklingrate\n" +
                "# Pulsbreite Wellenform, je Ton ein Wort mit Doppelpunkten\n" +
                "# getrennt. Die Wellenform steht als Name (PULS, DREIECK)\n" +
                "# und nicht als Zahl: Ein Port, der sie nicht kennt, soll\n" +
                "# beim Lesen scheitern und nicht stillschweigend Rechteck\n" +
                "# spielen, wo ein Dreieck stehen müsste."
        )
        line("sound.order", *SoundSetId.entries.map { it.name }.toTypedArray())
        line("sound.events", *SoundEvent.entries.map { it.name }.toTypedArray())
        line("sound.minHz", f(SoundBank.MIN_HZ))
        line("sound.maxHz", f(SoundBank.MAX_HZ))
        line("sound.minPitchRatio", f(SoundBank.MIN_PITCH_RATIO))

        SoundSetId.entries.forEach { id ->
            SoundEvent.entries.forEach { event ->
                val voice = SoundBank.voice(id, event)
                line(
                    "sound.voice.${id.name}.${event.name}",
                    voice.tones.joinToString(" ") { t ->
                        listOf(t.fromHz, t.toHz, t.seconds, t.volume, t.decay, t.duty)
                            .joinToString(":") { f(it) } + ":" + t.wave.name
                    },
                    // Das Rauschen steht am Ende derselben Zeile, damit
                    // ein Port, der es überliest, an der Feldzahl auffällt.
                    voice.noise?.let { "noise:" + f(it.seconds) + ":" + f(it.volume) + ":" + f(it.decay) }
                        ?: "-"
                )
            }
            line("sound.chips.${id.name}", *SoundBank.chips(id).map { f(it) }.toTypedArray())
        }
        appendLine()

        section("Freischaltung der Ton-Sets — dieselben Proben wie bei den Skins.")
        UNLOCK_PROBES.forEachIndexed { index, stats ->
            val open = SoundSetId.entries.filter { SoundBank.isUnlocked(it, stats) }
            line(
                "sound.unlocked.$index",
                SoundBank.unlockedCount(stats).toString(),
                *open.map { it.name }.toTypedArray()
            )
        }
        appendLine()
    }

    private fun StringBuilder.frames() {
        section(
            "Kartenrahmen (CardStyle) — die vierte Sammlung.\n" +
                "#\n" +
                "# Sie standen bis v2.25 bewusst NICHT hier: Der Rahmen war nur\n" +
                "# auf der geteilten Score-Karte zu sehen, und die rendert allein\n" +
                "# Android. Seit das Game-Over-Panel in :ui denselben Rahmen\n" +
                "# trägt, gilt das nicht mehr — er ist auf jeder Plattform\n" +
                "# sichtbar, also gehört er in den Vertrag. Der Beiname bleibt\n" +
                "# draußen: Er steht weiterhin nur auf der Karte.\n" +
                "#\n" +
                "# Das Muster steht als Tabelle und nicht als Zeichencode, damit\n" +
                "# die Renderer stumpf Rechtecke füllen: shape inset size tone\n" +
                "# step phase, alles in Feldern. Wer die Tabelle liest, muss die\n" +
                "# Formen selbst ausrollen — deshalb steht darunter zusätzlich\n" +
                "# das fertige Bild an abgetasteten Feldern."
        )
        line("frame.order", *CardFrame.entries.map { it.name }.toTypedArray())
        line("frame.steps", *CardStyle.FRAME_STEPS.map { it.toString() }.toTypedArray())
        FrameTone.entries.forEach { line("frame.tone.${it.name}", argb(it.argb)) }
        line("frame.cardSize", CARD_COLS.toString(), CARD_ROWS.toString())
        line(
            "frame.cells",
            *FRAME_CELLS.map { "${it.first},${it.second}" }.toTypedArray()
        )

        CardFrame.entries.forEach { frame ->
            line("frame.thickness.${frame.name}", CardStyle.thickness(frame).toString())
            val layout = CardStyle.layout(frame)
            line(
                "frame.layout.${frame.name}",
                f(layout.title), f(layout.titleSize),
                f(layout.subline), f(layout.sublineSize),
                f(layout.dot), f(layout.dotRadius),
                f(layout.challenge)
            )
            CardStyle.parts(frame).forEachIndexed { index, part ->
                line(
                    "frame.part.${frame.name}.$index",
                    part.shape.name,
                    part.inset.toString(),
                    part.size.toString(),
                    part.tone.name,
                    part.step.toString(),
                    part.phase.toString()
                )
            }
            line("frame.raster.${frame.name}", *raster(frame))
        }
        appendLine()

        section("Freischaltung der Rahmen — dieselben Proben wie bei den Skins.")
        UNLOCK_PROBES.forEachIndexed { index, stats ->
            val open = CardFrame.entries.filter { CardStyle.isUnlocked(it, stats) }
            line(
                "frame.unlocked.$index",
                CardStyle.frame(stats).name,
                CardStyle.unlockedCount(stats).toString(),
                *open.map { it.name }.toTypedArray()
            )
        }
        appendLine()
    }

    /**
     * Das ausgerollte Muster an den Feldern aus [FRAME_CELLS] — die
     * Gegenprobe zur Tabelle. Eine Farbrolle je Feld, "-" heißt "hier
     * liegt kein Rahmen".
     */
    private fun raster(frame: CardFrame): Array<String> {
        val blatt = arrayOfNulls<FrameTone>(CARD_COLS * CARD_ROWS)
        CardStyle.frameRects(frame, CARD_COLS, CARD_ROWS).forEach { r ->
            for (row in r.row until r.row + r.rows) {
                if (row !in 0 until CARD_ROWS) continue
                for (col in r.col until r.col + r.cols) {
                    if (col !in 0 until CARD_COLS) continue
                    blatt[row * CARD_COLS + col] = r.tone
                }
            }
        }
        return FRAME_CELLS
            .map { (col, row) -> blatt[row * CARD_COLS + col]?.name ?: "-" }
            .toTypedArray()
    }

    private fun StringBuilder.progress() {
        section(
            "Ziele und Fortschrittsbalken (Progress). Die Schwellen stehen\n" +
                "# damit dreifach im Repo (SkinPaint, ScenePaint, Progress) —\n" +
                "# hier wird geprüft, dass alle dasselbe sagen, inklusive\n" +
                "# Reihenfolge: Das erste Ziel ist das, was Spieler:innen im\n" +
                "# Game-Over lesen."
        )
        line("progress.pageGoals", Progress.PAGE_GOALS.toString())
        line("progress.barBlocks", Progress.BAR_BLOCKS.toString())
        // Die Kanten des Balkens: knapp unter und genau auf einem Block.
        val fractions = listOf(
            0f, -0.5f, 0.0416f, 1f / 24f, 0.2f, 0.5f, 0.999f, 1f, 1.5f
        )
        line("progress.fractions", *fractions.map { f(it) }.toTypedArray())
        line(
            "progress.filledBlocks",
            *fractions.map { Progress.filledBlocks(it).toString() }.toTypedArray()
        )

        // Zusätzlich zu den Skin-Proben zwei mit offenem Saison-Fenster:
        // Ein Saison-Ziel darf nur im eigenen Monat auftauchen.
        val probes = UNLOCK_PROBES.map { Triple(it, 0, 0) } + listOf(
            Triple(SkinStats(0, 0, 0), 10, 2),
            Triple(SkinStats(0, 0, 0), 10, 5),
            Triple(SkinStats(0, 0, 0), 3, 4)
        )
        probes.forEachIndexed { index, (stats, month, seasonDays) ->
            line(
                "progress.probe.$index",
                stats.bestScore.toString(),
                stats.bestPerfectStreak.toString(),
                stats.bestDailyStreak.toString(),
                stats.runCount.toString(),
                stats.totalScore.toString(),
                stats.daysPlayed.toString(),
                stats.monthsPlayed.toString(),
                stats.seasonEarned.toString(),
                if (stats.patronOwned) "1" else "0",
                month.toString(),
                seasonDays.toString()
            )
            val goals = Progress.goals(stats, month, seasonDays)
            line(
                "progress.goals.$index",
                goals.size.toString(),
                *goals.map { goalToken(it) }.toTypedArray()
            )
            val next = Progress.nextGoal(stats, month, seasonDays)
            line(
                "progress.next.$index",
                next?.let { goalToken(it) } ?: "-",
                next?.remaining?.toString() ?: "-",
                next?.let { f(it.fraction) } ?: "-"
            )
        }
        appendLine()
    }

    /** Ein Ziel als ein Wort: WAS:NAME|ACHSE|stand|ziel. */
    private fun goalToken(goal: Goal): String {
        val subject = goal.skin?.let { "SKIN:${it.name}" }
            ?: goal.scene?.let { "SCENE:${it.name}" }
            ?: "SOUND:${goal.sound!!.name}"
        return "$subject|${goal.axis.name}|${goal.current}|${goal.target}"
    }

    private fun StringBuilder.rng() {
        section(
            "kotlin.random.Random(seed) — XorWow inklusive 64 Warmup-Runden.\n" +
                "# An dieser Folge hängt, dass iPhone und Android an demselben\n" +
                "# Tag dieselbe Daily Challenge spielen."
        )
        line("rng.seed", SEED.toString())

        line(
            "rng.nextInt",
            *Random(SEED).let { r -> (1..16).map { r.nextInt().toString() } }.toTypedArray()
        )
        line(
            "rng.nextFloat",
            *Random(SEED).let { r -> (1..16).map { f(r.nextFloat()) } }.toTypedArray()
        )
        line(
            "rng.nextBoolean",
            *Random(SEED).let { r -> (1..16).map { if (r.nextBoolean()) "1" else "0" } }
                .toTypedArray()
        )
        line(
            "rng.nextIntBound5",
            *Random(SEED).let { r -> (1..16).map { r.nextInt(5).toString() } }.toTypedArray()
        )
        line(
            "rng.nextIntBound8",
            *Random(SEED).let { r -> (1..16).map { r.nextInt(8).toString() } }.toTypedArray()
        )
        // Genau die Operation aus chooseTwists(): shuffled() über die
        // Twist-Liste. Sie hängt an nextInt(bound) und ist der Grund, warum
        // zwei Plattformen dieselben Twists sehen.
        val shuffleRandom = Random(SEED)
        repeat(4) { round ->
            line(
                "rng.shuffleTwists.$round",
                *Twist.entries.shuffled(shuffleRandom)
                    .map { it.name }.toTypedArray()
            )
        }
        // Der Tages-Seed selbst, so wie ihn die Daily Challenge benutzt.
        line(
            "rng.dailyFloats",
            *Random(DailyChallenge.seedFor(19947L)).let { r -> (1..8).map { f(r.nextFloat()) } }
                .toTypedArray()
        )
        appendLine()
    }

    private fun StringBuilder.traces() {
        section(
            "Ganze Läufe, Frame für Frame nachgerechnet. Der Bot tappt immer\n" +
                "# tief im Perfekt-Kern (siehe ParityBot), damit eine Abweichung im\n" +
                "# letzten Bit keine Entscheidung kippt — was zählt, ist die\n" +
                "# Abfolge aus Zonen, Twists und Punkten."
        )
        line("trace.dt", f(ParityBot.DT))

        val perfect = ParityBot.playPerfect(SEED, maxHits = 40)
        line("trace.perfect.hits", perfect.size.toString())
        perfect.forEachIndexed { index, snap -> line("trace.perfect.$index", *fields(snap)) }

        // Zweiter Lauf mit anderem Seed: andere Zonen, andere Twists.
        val other = ParityBot.playPerfect(SEED + 7, maxHits = 25)
        line("trace.second.hits", other.size.toString())
        other.forEachIndexed { index, snap -> line("trace.second.$index", *fields(snap)) }

        // Nie tappen: Tod durch Überfahren, dann der Weg DYING → OVER.
        val death = ParityBot.playPassive(SEED)
        line(
            "trace.death",
            death.framesToDeath.toString(),
            death.framesToSettle.toString(),
            f(death.angleAtDeath),
            f(death.zoneCenterAtDeath)
        )
        appendLine()
    }

    // ===== Formatierung =====

    /** Feste Nachkommastellen, Locale-unabhängig (sonst kommt ein Komma). */
    private fun f(value: Float): String = String.format(Locale.ROOT, "%.6f", value)

    private fun argb(value: Long): String =
        "0x" + java.lang.Long.toHexString(value).uppercase().padStart(8, '0')

    private fun StringBuilder.section(text: String) {
        appendLine("# --- $text")
    }

    private fun StringBuilder.line(key: String, vararg values: String) {
        append(key)
        values.forEach { append(' ').append(it) }
        appendLine()
    }

    /**
     * Ein Treffer als Zeile: score hits streak punkte richtung twists
     * zoneCenter zoneHalf angle kette fake.
     */
    private fun fields(snap: ParityBot.Snapshot): Array<String> = arrayOf(
        snap.score.toString(),
        snap.hits.toString(),
        snap.perfectStreak.toString(),
        snap.lastHitPoints.toString(),
        snap.direction.toString(),
        if (snap.twists.isEmpty()) "-" else snap.twists.joinToString("+"),
        f(snap.zoneCenter),
        f(snap.zoneHalfWidth),
        f(snap.angle),
        snap.chainRemaining.toString(),
        snap.fakeZoneCenter?.let { f(it) } ?: "-"
    )
}

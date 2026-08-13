package de.robinrehbein.punkt.game

import java.util.Locale
import kotlin.random.Random

/**
 * Erzeugt die Paritäts-Vektoren in `parity/golden-vectors.txt` — den
 * gemeinsamen Vertrag zwischen der Kotlin-Engine (:core, die Quelle der
 * Wahrheit) und ihren Handports nach Swift (ios/) und JavaScript (web/).
 *
 * Warum das nötig ist: Die Spiellogik existiert dreifach. Was in Kotlin
 * getestet ist, sagt nichts über den Swift-Port aus — der hat bis hierher
 * gar keine Tests gehabt, obwohl ausgerechnet [KotlinRandom in Swift] die
 * Daily Challenge bit-genau nachbauen muss. Statt jeden Port einzeln
 * nachzutesten, schreibt Kotlin einmal auf, was herauskommen muss, und
 * jeder Port prüft sich gegen dieselbe Datei.
 *
 * Format: eine Zeile pro Wert, `schlüssel wert…`, durch Leerzeichen
 * getrennt, `#` leitet einen Kommentar ein. Bewusst so simpel, dass drei
 * Sprachen es ohne JSON-Bibliothek lesen können.
 *
 * Wer welchen Abschnitt prüft:
 *
 * | Abschnitt | Kotlin | Swift | JS |
 * |---|---|---|---|
 * | `const.*`, `medal.*`, `sky.*`, `skin.*` | ja | ja | ja |
 * | `rng.*`, `trace.*` | ja | ja | nein |
 *
 * Der Web-Port lässt `rng`/`trace` bewusst aus: Er baut Kotlins XorWow
 * nicht nach (siehe Kommentar in web/js/game.js), seine Daily Challenge
 * hat also eine eigene Abfolge. Regeln, Farben und Konstanten müssen
 * trotzdem überall gleich sein — genau die stehen in den anderen
 * Abschnitten.
 */
object ParityVectors {

    /** Bei jeder Formatänderung hochzählen; die Ports prüfen sie. */
    const val VERSION = 2

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
        )
    )

    fun build(): String {
        val out = StringBuilder()
        out.header()
        out.constants()
        out.medals()
        out.sky()
        out.skins()
        out.rng()
        out.traces()
        return out.toString()
    }

    // ===== Abschnitte =====

    private fun StringBuilder.header() {
        appendLine("# Dottie. — Paritäts-Vektoren zwischen Kotlin, Swift und JavaScript.")
        appendLine("#")
        appendLine("# ERZEUGT — nicht von Hand bearbeiten. Neu schreiben mit:")
        appendLine("#   ./gradlew :core:test -Dparity.update=true")
        appendLine("#")
        appendLine("# Quelle der Wahrheit ist :core. Ändert sich hier eine Zahl, ist das")
        appendLine("# die Ansage an ios/ und web/, nachzuziehen — siehe parity/README.md.")
        appendLine()
        line("version", VERSION.toString())
        appendLine()
    }

    private fun StringBuilder.constants() {
        section("Konstanten der Engine (TimingGame). Namen wie in Kotlin und JS.")
        line("const.MAX_DELTA", f(TimingGame.MAX_DELTA))
        line("const.BASE_SPEED", f(TimingGame.BASE_SPEED))
        line("const.SPEED_PER_HIT", f(TimingGame.SPEED_PER_HIT))
        line("const.MAX_SPEED", f(TimingGame.MAX_SPEED))
        line("const.READY_SPEED", f(TimingGame.READY_SPEED))
        line("const.BASE_ZONE_HALF", f(TimingGame.BASE_ZONE_HALF))
        line("const.ZONE_SHRINK_PER_HIT", f(TimingGame.ZONE_SHRINK_PER_HIT))
        line("const.MIN_ZONE_HALF", f(TimingGame.MIN_ZONE_HALF))
        line("const.PERFECT_SHARE", f(TimingGame.PERFECT_SHARE))
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
        TimingGame.Twist.entries.forEach {
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

    private fun StringBuilder.rng() {
        section(
            "kotlin.random.Random(seed) — XorWow inklusive 64 Warmup-Runden.\n" +
                "# Der Swift-Port (KotlinRandom.swift) muss diese Zahlen exakt\n" +
                "# treffen, sonst spielt iOS eine andere Daily Challenge."
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
                *TimingGame.Twist.entries.shuffled(shuffleRandom)
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

package de.robinrehbein.punkt.game

import kotlin.math.floor
import kotlin.math.min

/**
 * Der Weg zur nächsten Freischaltung — die Rechnung hinter der
 * Statistik-Seite und der Zeile im Game-Over.
 *
 * Seit v2.20 laufen vier Ausdauer-Achsen mit (Läufe, Punkte insgesamt,
 * gespielte Tage, verschiedene Monate), und keine davon war je sichtbar:
 * [SkinPaint.isUnlocked] beantwortet nur "offen oder nicht", nie "wie
 * weit noch". Genau diese Lücke schließt diese Datei — und zwar in
 * `:core`, damit Android, PWA und iOS dieselbe Reihenfolge zeigen.
 *
 * Zwei Regeln stecken in der Auswahl, nicht in der Anzeige:
 *
 * - **Gönner-Skins tauchen nie auf.** Sie werden gekauft, nicht erreicht;
 *   ein Fortschrittsbalken zu einem Kauf wäre eine Werbefläche.
 * - **Saison-Skins nur in ihrem Monat.** "Noch 5 Tage im Oktober" wäre im
 *   März schlicht falsch — das Fenster ist zu, der Balken bewegt sich
 *   nicht. Deshalb braucht [Progress.goals] den Kalendermonat und den
 *   Tageszähler des laufenden Fensters von außen (beides steht in den
 *   Stores, nicht in [SkinStats]).
 */
enum class GoalAxis {
    BEST_SCORE,
    PERFECT_STREAK,
    DAILY_STREAK,
    RUN_COUNT,
    TOTAL_SCORE,
    DAYS_PLAYED,
    MONTHS_PLAYED,

    /** Tage mit Lauf im laufenden Saison-Fenster (siehe [Season]). */
    SEASON_DAYS,

    /** Gesammelte Skins — die Bedingung des REGENBOGEN. */
    SKIN_COLLECTION,

    /** Gesammelte Kulissen — die Bedingung des WELTRAUM. */
    SCENE_COLLECTION
}

/**
 * Ein noch offenes Ziel: was es freischaltet, woran es hängt, wo man
 * steht und wo es fällt.
 *
 * [skin], [scene] und [sound] sind bewusst drei Felder statt einer
 * versiegelten Klasse: Dieselbe Form wird nach JavaScript und Swift
 * portiert, und dort sind drei Optionale billiger als eine nachgebaute
 * Vererbungshierarchie. Genau eines von dreien ist gesetzt.
 */
data class Goal(
    val skin: SkinId?,
    val scene: SceneId?,
    val sound: SoundSetId? = null,
    val axis: GoalAxis,
    /** Aktueller Stand auf der Achse, nie größer als [target]. */
    val current: Int,
    val target: Int
) {
    init {
        require(listOfNotNull(skin, scene, sound).size == 1) {
            "Ein Ziel schaltet genau einen Skin ODER eine Kulisse ODER ein Ton-Set frei"
        }
    }

    /** Was noch fehlt — die Zahl, die im Game-Over die Motivation trägt. */
    val remaining: Int get() = (target - current).coerceAtLeast(0)

    /** Anteil 0..1 für den Balken. */
    val fraction: Float get() =
        if (target <= 0) 1f else (current.toFloat() / target).coerceIn(0f, 1f)
}

object Progress {

    /**
     * Wie viele Ziele die Statistik-Seite zeigt. Drei sind genug: Die
     * Liste soll den nächsten Schritt zeigen, nicht die ganze Sammlung
     * ein zweites Mal — dafür gibt es das Skin-Menü.
     */
    const val PAGE_GOALS = 3

    /**
     * Aus wie vielen Blöcken der Fortschrittsbalken besteht. Er rastet
     * auf ganze Blöcke ein — ein weicher Balken wäre der einzige
     * stufenlose Verlauf im ganzen Spiel. Die Zahl steht hier und nicht
     * in den drei Renderern, damit derselbe Stand überall gleich weit
     * gefüllt aussieht.
     */
    const val BAR_BLOCKS = 24

    /** Wie viele Blöcke bei diesem Anteil leuchten. */
    fun filledBlocks(fraction: Float): Int =
        floor(fraction.coerceIn(0f, 1f) * BAR_BLOCKS).toInt()

    /**
     * Die Schwellen, gespiegelt aus [SkinPaint.isUnlocked] — in derselben
     * Reihenfolge wie [SkinId], damit die Reihenfolge bei Gleichstand
     * vorhersagbar bleibt.
     *
     * Dass die Zahlen hier ein zweites Mal stehen, ist der Preis dafür,
     * dass `isUnlocked` eine einzige billige Abfrage bleibt. Der Preis
     * wird per Test bezahlt: `ProgressTest` prüft für jedes Ziel, dass es
     * bei `target` fällt und bei `target - 1` noch steht.
     */
    private val SKIN_THRESHOLDS: List<Triple<SkinId, GoalAxis, Int>> = listOf(
        Triple(SkinId.MINZE, GoalAxis.BEST_SCORE, 10),
        Triple(SkinId.LAVA, GoalAxis.BEST_SCORE, 20),
        Triple(SkinId.GOLD, GoalAxis.BEST_SCORE, 30),
        Triple(SkinId.FROST, GoalAxis.BEST_SCORE, 40),
        Triple(SkinId.SCHATTEN, GoalAxis.PERFECT_STREAK, 4),
        Triple(SkinId.PRISMA, GoalAxis.DAILY_STREAK, 3),

        Triple(SkinId.BIENE, GoalAxis.PERFECT_STREAK, 6),
        Triple(SkinId.MELONE, GoalAxis.BEST_SCORE, 25),
        Triple(SkinId.PILZ, GoalAxis.BEST_SCORE, 35),
        Triple(SkinId.KOI, GoalAxis.DAILY_STREAK, 7),
        Triple(SkinId.GALAXIE, GoalAxis.BEST_SCORE, 50),
        Triple(SkinId.KARO, GoalAxis.PERFECT_STREAK, 10),
        Triple(SkinId.EI, GoalAxis.RUN_COUNT, 25),
        Triple(SkinId.TIGER, GoalAxis.RUN_COUNT, 100),
        Triple(SkinId.PINGUIN, GoalAxis.BEST_SCORE, 65),
        Triple(SkinId.FUSSBALL, GoalAxis.RUN_COUNT, 300),
        Triple(SkinId.DONUT, GoalAxis.TOTAL_SCORE, 1_000),

        Triple(SkinId.AURORA, GoalAxis.DAILY_STREAK, 14),
        Triple(SkinId.MAGMA, GoalAxis.BEST_SCORE, 60),
        Triple(SkinId.NEON, GoalAxis.PERFECT_STREAK, 12),
        Triple(SkinId.CHROM, GoalAxis.BEST_SCORE, 45),
        Triple(SkinId.WELLE, GoalAxis.BEST_SCORE, 70),
        Triple(SkinId.GEWITTER, GoalAxis.PERFECT_STREAK, 15),
        Triple(SkinId.KONFETTI, GoalAxis.TOTAL_SCORE, 5_000),
        Triple(SkinId.DISCO, GoalAxis.DAILY_STREAK, 21),
        Triple(SkinId.HOLO, GoalAxis.BEST_SCORE, 80),

        Triple(SkinId.CHAMAELEON, GoalAxis.BEST_SCORE, 30),
        Triple(SkinId.KOMBO, GoalAxis.PERFECT_STREAK, 8),
        Triple(SkinId.TINTE, GoalAxis.BEST_SCORE, 55),
        Triple(SkinId.THERMO, GoalAxis.BEST_SCORE, 75),
        Triple(SkinId.MEDAILLE, GoalAxis.RUN_COUNT, 200),
        Triple(SkinId.TAGESZEIT, GoalAxis.DAYS_PLAYED, 7),
        Triple(SkinId.JAHRESZEIT, GoalAxis.MONTHS_PLAYED, 3)
    )

    /** Dieselbe Tabelle für die Kulissen (siehe [ScenePaint.isUnlocked]). */
    private val SCENE_THRESHOLDS: List<Triple<SceneId, GoalAxis, Int>> = listOf(
        Triple(SceneId.WUESTE, GoalAxis.RUN_COUNT, 500),
        Triple(SceneId.MEER, GoalAxis.TOTAL_SCORE, 10_000),
        Triple(SceneId.BERG, GoalAxis.DAILY_STREAK, 30),
        Triple(SceneId.STADT, GoalAxis.BEST_SCORE, 85)
    )

    /**
     * Und dieselbe für die Ton-Sets (siehe [SoundBank.isUnlocked]).
     *
     * Beide Schwellen liegen bewusst auf Zahlen, die sonst nirgends
     * vorkommen: Fiele ein Ton-Set zusammen mit einem Skin oder einer
     * Kulisse, hörte niemand das neue Set — er sähe den neuen Vogel und
     * hielte den Klang für dessen Beiwerk.
     */
    private val SOUND_THRESHOLDS: List<Triple<SoundSetId, GoalAxis, Int>> = listOf(
        Triple(SoundSetId.GLOCKE, GoalAxis.PERFECT_STREAK, 20),
        Triple(SoundSetId.AMBOSS, GoalAxis.TOTAL_SCORE, 25_000)
    )

    /**
     * Alle noch offenen Ziele, das nächstliegende zuerst.
     *
     * [month] ist der Kalendermonat 1-12 (0 = kein Kalender bekannt, dann
     * fallen die Saison-Ziele weg), [seasonDays] der Tageszähler des
     * laufenden Saison-Fensters.
     */
    fun goals(stats: SkinStats, month: Int = 0, seasonDays: Int = 0): List<Goal> {
        val open = ArrayList<Goal>()

        SKIN_THRESHOLDS.forEach { (id, axis, target) ->
            if (!SkinPaint.isUnlocked(id, stats)) {
                open += goal(skin = id, axis = axis, stats = stats, target = target)
            }
        }

        // Saison: nur im eigenen Monat, und nur solange das Bit fehlt.
        Season.forMonth(month)?.let { season ->
            if (!SkinPaint.isUnlocked(season.skin, stats)) {
                open += Goal(
                    skin = season.skin,
                    scene = null,
                    axis = GoalAxis.SEASON_DAYS,
                    current = min(seasonDays.coerceAtLeast(0), season.requiredDays),
                    target = season.requiredDays
                )
            }
        }

        // Der REGENBOGEN ist der Abschluss der Sammlung: Er zählt selbst
        // mit, also fehlt zum Ziel genau er — daher collectableCount - 1.
        if (!SkinPaint.isUnlocked(SkinId.REGENBOGEN, stats)) {
            open += Goal(
                skin = SkinId.REGENBOGEN,
                scene = null,
                axis = GoalAxis.SKIN_COLLECTION,
                current = SkinPaint.unlockedCount(stats),
                target = SkinPaint.collectableCount() - 1
            )
        }

        SCENE_THRESHOLDS.forEach { (id, axis, target) ->
            if (!ScenePaint.isUnlocked(id, stats)) {
                open += goal(scene = id, axis = axis, stats = stats, target = target)
            }
        }

        // Der WELTRAUM steht zu den Kulissen wie der REGENBOGEN zu den
        // Skins — dieselbe Rechnung, dieselbe Begründung.
        if (!ScenePaint.isUnlocked(SceneId.WELTRAUM, stats)) {
            open += Goal(
                skin = null,
                scene = SceneId.WELTRAUM,
                axis = GoalAxis.SCENE_COLLECTION,
                current = ScenePaint.unlockedCount(stats),
                target = SceneId.entries.size - 1
            )
        }

        // Die Ton-Sets haben keinen Abschluss wie REGENBOGEN und
        // WELTRAUM: Drei Sets sind zu wenig für ein Sammel-Ziel, und ein
        // Set, das nur auf zwei andere wartet, wäre kein eigener Weg.
        SOUND_THRESHOLDS.forEach { (id, axis, target) ->
            if (!SoundBank.isUnlocked(id, stats)) {
                open += goal(sound = id, axis = axis, stats = stats, target = target)
            }
        }

        return open.sortedWith(NEAREST_FIRST)
    }

    /** Die vordersten [limit] Ziele — das Kurzformat für Game-Over und Seite. */
    fun nextGoals(
        stats: SkinStats,
        month: Int = 0,
        seasonDays: Int = 0,
        limit: Int = PAGE_GOALS
    ): List<Goal> = goals(stats, month, seasonDays).take(limit.coerceAtLeast(0))

    /** Das eine Ziel für die Zeile im Game-Over — null, wenn alles offen ist. */
    fun nextGoal(stats: SkinStats, month: Int = 0, seasonDays: Int = 0): Goal? =
        goals(stats, month, seasonDays).firstOrNull()

    /**
     * Nähe zum Ziel zuerst: Oben steht, was als Nächstes fällt. Der Anteil
     * entscheidet und nicht der Restweg, weil "5 von 7 Tagen" näher dran
     * ist als "4.800 von 5.000 Punkten" — auch wenn dort nur 200 fehlen
     * und hier 2.
     *
     * Bei Gleichstand entscheidet erst der kleinere Rest, dann die
     * Reihenfolge der Sammlung: Ohne diesen letzten Schritt könnte die
     * Liste bei zwei gleich weiten Zielen zwischen zwei Aufrufen springen.
     */
    private val NEAREST_FIRST: Comparator<Goal> =
        compareByDescending<Goal> { it.fraction }
            .thenBy { it.remaining }
            .thenBy { order(it) }

    /**
     * Die Reihenfolge der Sammlungen als eine Zahl: erst die Skins, dann
     * die Kulissen, dann die Ton-Sets. Sie entscheidet nur bei
     * Gleichstand — aber dort auf beiden Geräten gleich.
     */
    private fun order(goal: Goal): Int = when {
        goal.skin != null -> goal.skin.ordinal
        goal.scene != null -> SkinId.entries.size + goal.scene.ordinal
        else -> SkinId.entries.size + SceneId.entries.size + goal.sound!!.ordinal
    }

    private fun goal(
        skin: SkinId? = null,
        scene: SceneId? = null,
        sound: SoundSetId? = null,
        axis: GoalAxis,
        stats: SkinStats,
        target: Int
    ) = Goal(
        skin = skin,
        scene = scene,
        sound = sound,
        axis = axis,
        // Ein Balken zeigt nie mehr als voll: Der Rohwert kann die
        // Schwelle nur überholen, wenn das Ziel längst offen ist.
        current = min(value(axis, stats, seasonDays = 0), target),
        target = target
    )

    /** Der aktuelle Stand auf einer Achse. */
    private fun value(axis: GoalAxis, stats: SkinStats, seasonDays: Int): Int = when (axis) {
        GoalAxis.BEST_SCORE -> stats.bestScore
        GoalAxis.PERFECT_STREAK -> stats.bestPerfectStreak
        GoalAxis.DAILY_STREAK -> stats.bestDailyStreak
        GoalAxis.RUN_COUNT -> stats.runCount
        GoalAxis.TOTAL_SCORE -> stats.totalScore
        GoalAxis.DAYS_PLAYED -> stats.daysPlayed
        GoalAxis.MONTHS_PLAYED -> stats.monthsPlayed
        GoalAxis.SEASON_DAYS -> seasonDays
        GoalAxis.SKIN_COLLECTION -> SkinPaint.unlockedCount(stats)
        GoalAxis.SCENE_COLLECTION -> ScenePaint.unlockedCount(stats)
    }
}

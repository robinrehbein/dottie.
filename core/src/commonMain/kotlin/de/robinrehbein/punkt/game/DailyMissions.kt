package de.robinrehbein.punkt.game

import kotlin.random.Random

/**
 * Tagesaufgaben (ab v2.36): drei kleine Ziele, die jeden Tag wechseln.
 *
 * Alle anderen Ziele im Spiel sind Summen, die über Wochen wachsen —
 * Läufe, Punkte, Rekord. Wer bei Rekord 60 steht, sieht als nächstes
 * Ziel „HOLO 60/80" und weiß, dass heute nichts mehr davon fällt. Die
 * Aufgaben geben jeder Sitzung ein Ziel, das heute erreichbar ist, und
 * schicken nebenbei in Ecken des Spiels, die man sonst meidet: PERFEKT in
 * Serie, den Tageslauf, Treffer im Nebel.
 *
 * Wie die Daily hängen die Aufgaben am Kalendertag: Alle bekommen am
 * selben Tag dieselben — getrennt nur nach [tier], der Stufe, auf der
 * jemand spielt. Eine Aufgabe „30 Punkte in einem Lauf" wäre für eine
 * Anfängerin eine Wand und für einen Stammspieler ein Gähnen.
 *
 * Die Stufe wird beim ersten Blick des Tages festgehalten (siehe
 * GameStore): Wer mittags den Rekord von 24 auf 26 hebt, rutscht zwar in
 * die nächste Stufe, behält aber seine drei Aufgaben bis Mitternacht —
 * sonst wechselten sie mitten im Tag samt Fortschritt.
 *
 * Alles hier ist reine Rechnung ohne Speicher, damit es als Unit-Test
 * prüfbar bleibt; den Stand des Tages hält der GameStore in `:ui`.
 */
enum class MissionType {
    /** n Läufe heute. */
    RUNS,

    /** n Punkte heute, über alle Läufe zusammen. */
    POINTS_TODAY,

    /** Den Tageslauf spielen (Ziel immer 1). */
    DAILY_RUN,

    /** n PERFEKT-Treffer heute, über alle Läufe zusammen. */
    PERFECTS_TODAY,

    /** n PERFEKT in Folge, in einem Lauf. */
    PERFECT_STREAK,

    /** n BLIND!-Treffer heute (Treffer im Nebel, siehe TimingGame.lastHitBlind). */
    BLIND_HITS,

    /** n Punkte in einem einzigen Lauf. */
    SCORE_IN_RUN,

    /** n Punkte im Tageslauf. */
    DAILY_SCORE;

    /**
     * Zählt die Aufgabe über alle Läufe des Tages zusammen (true) oder
     * gilt der beste einzelne Lauf (false)? „20 Punkte heute" und „20
     * Punkte in einem Lauf" sind zwei sehr verschiedene Aufgaben.
     */
    val cumulative: Boolean
        get() = when (this) {
            RUNS, POINTS_TODAY, DAILY_RUN, PERFECTS_TODAY, BLIND_HITS -> true
            PERFECT_STREAK, SCORE_IN_RUN, DAILY_SCORE -> false
        }
}

/** Eine Aufgabe: was zu tun ist und bis wohin. */
data class Mission(val type: MissionType, val target: Int)

/**
 * Was ein beendeter Lauf zu den Aufgaben beiträgt. Die Oberfläche zählt
 * mit, die Engine liefert die Zahlen (Treffer, Serie, Blindtreffer).
 */
data class RunFacts(
    val score: Int,
    val perfectHits: Int = 0,
    val maxPerfectStreak: Int = 0,
    val blindHits: Int = 0,
    val daily: Boolean = false
)

/**
 * Der Stand eines Tages: welche Stufe, wie weit jede der drei Aufgaben
 * ist. [progress] hat immer genau [DailyMissions.COUNT] Einträge und ist
 * je Aufgabe auf ihr Ziel gedeckelt.
 */
data class MissionDay(
    val epochDay: Long,
    val tier: Int,
    val progress: List<Int>
) {
    val missions: List<Mission> get() = DailyMissions.forDay(epochDay, tier)

    fun isDone(index: Int): Boolean = progress[index] >= missions[index].target

    val doneCount: Int get() = missions.indices.count { isDone(it) }

    val allDone: Boolean get() = doneCount == DailyMissions.COUNT
}

object DailyMissions {

    /** Drei Aufgaben am Tag: eine fürs Dabeisein, eine für Technik, eine für Können. */
    const val COUNT = 3

    /** Anzahl der Stufen, siehe [tierFor]. */
    const val TIERS = 4

    /**
     * Die Stufe zu einem Rekord. Die Grenzen folgen den Twists: unter 10
     * kennt man höchstens den PULS, ab 25 alle fünf, ab 45 spielt man
     * sie seit Wochen.
     */
    fun tierFor(bestScore: Int): Int = when {
        bestScore < 10 -> 0
        bestScore < 25 -> 1
        bestScore < 45 -> 2
        else -> 3
    }

    /**
     * Die drei Aufgaben des Tages für eine Stufe. Deterministisch: derselbe
     * Tag und dieselbe Stufe geben auf jedem Gerät dieselben Aufgaben.
     *
     * Der Seed ist der Tages-Seed der Daily plus eine feste Konstante —
     * sonst zöge der Zufall hier dieselbe Folge wie die Zonen der Daily,
     * und wer die Aufgaben kennt, kennte ein Stück des Tageslaufs.
     */
    fun forDay(epochDay: Long, tier: Int): List<Mission> {
        val t = tier.coerceIn(0, TIERS - 1)
        val random = Random(DailyChallenge.seedFor(epochDay) + SALT + t)
        return listOf(
            pick(random, presence(t)),
            pick(random, technique(t)),
            pick(random, skill(t))
        )
    }

    /**
     * Schreibt einen Lauf in den Stand eines Tages. Kumulative Aufgaben
     * addieren, die übrigen nehmen den besseren Wert; gedeckelt wird aufs
     * Ziel, damit ein Balken nie über voll hinausläuft.
     */
    fun apply(day: MissionDay, facts: RunFacts): MissionDay {
        val missions = day.missions
        val next = missions.mapIndexed { i, mission ->
            val current = day.progress.getOrElse(i) { 0 }
            val value = contribution(mission.type, facts)
            val raw = if (mission.type.cumulative) current + value else maxOf(current, value)
            raw.coerceIn(0, mission.target)
        }
        return day.copy(progress = next)
    }

    /** Was ein Lauf zu einer Aufgabe beiträgt. */
    fun contribution(type: MissionType, facts: RunFacts): Int = when (type) {
        MissionType.RUNS -> 1
        MissionType.POINTS_TODAY -> facts.score
        MissionType.DAILY_RUN -> if (facts.daily) 1 else 0
        MissionType.PERFECTS_TODAY -> facts.perfectHits
        MissionType.PERFECT_STREAK -> facts.maxPerfectStreak
        MissionType.BLIND_HITS -> facts.blindHits
        MissionType.SCORE_IN_RUN -> facts.score
        MissionType.DAILY_SCORE -> if (facts.daily) facts.score else 0
    }

    /** Ein leerer Tag: noch nichts geschafft. */
    fun emptyDay(epochDay: Long, tier: Int): MissionDay =
        MissionDay(epochDay, tier.coerceIn(0, TIERS - 1), List(COUNT) { 0 })

    // ===== Die Töpfe =====
    //
    // Jede Stufe hat pro Platz eine kleine Auswahl. Die Zahlen sollen in
    // einer normalen Sitzung (zehn Minuten, ein Dutzend Läufe) fallen und
    // orientieren sich an den Stufengrenzen: Wer in Stufe 1 steht, hat
    // Rekord 10 bis 24, also sind 8 Punkte in einem Lauf ein guter, aber
    // kein Ausnahme-Lauf. Es sind Startwerte — sobald die Nutzungsdaten
    // (Ereignis `mission_done`) da sind, gehören sie daran nachgezogen.

    /** Dabeisein: kostet Zeit, kein Können. */
    private fun presence(tier: Int): List<Mission> = listOf(
        Mission(MissionType.RUNS, intArrayOf(3, 4, 5, 6)[tier]),
        Mission(MissionType.POINTS_TODAY, intArrayOf(15, 40, 100, 200)[tier]),
        Mission(MissionType.DAILY_RUN, 1)
    )

    /** Technik: PERFEKT und, ab Stufe 3, der Nebel. */
    private fun technique(tier: Int): List<Mission> = buildList {
        add(Mission(MissionType.PERFECTS_TODAY, intArrayOf(3, 6, 10, 15)[tier]))
        add(Mission(MissionType.PERFECT_STREAK, intArrayOf(2, 3, 4, 5)[tier]))
        // Blindtreffer gibt es erst ab Score 15 (NEBEL), und auch dann nur,
        // wenn der Nebel in dieser Zone gezogen wurde. Unter Stufe 3 wäre
        // das eine Aufgabe, die an einem schlechten Tag nie kommt.
        if (tier >= 3) add(Mission(MissionType.BLIND_HITS, 2))
    }

    /**
     * Können: ein guter Lauf, frei oder im Tageslauf. Die Ziele liegen
     * unter der unteren Rekordgrenze ihrer Stufe (10, 25, 45): Eine
     * Aufgabe soll ein guter Lauf sein, kein neuer Rekord.
     */
    private fun skill(tier: Int): List<Mission> = listOf(
        Mission(MissionType.SCORE_IN_RUN, intArrayOf(5, 8, 18, 30)[tier]),
        Mission(MissionType.DAILY_SCORE, intArrayOf(4, 6, 14, 22)[tier])
    )

    private fun pick(random: Random, pool: List<Mission>): Mission = pool[random.nextInt(pool.size)]

    /** Trennt den Aufgaben-Zufall vom Zonen-Zufall der Daily. */
    private const val SALT = 0x5EED_1A5CL
}

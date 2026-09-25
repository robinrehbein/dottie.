package de.robinrehbein.punkt.game

import kotlin.math.min

/**
 * Worauf eine Kachel der Sammlung zählt.
 *
 * Die ersten zehn Werte heißen wie die Achsen von [GoalAxis] und meinen
 * dasselbe. Dazu kommen die Fälle, die es als Ziel nicht gibt, weil
 * [Progress.goals] sie nie vorschlägt: der Anfangsbestand ([NONE]), der
 * Kauf ([PURCHASE]) und die Ton-Sammlung als Bedingung eines Rahmens
 * ([SOUND_COLLECTION]).
 *
 * Eine eigene Aufzählung statt neuer Werte in [GoalAxis]: Die Ziele sind
 * eine Auswahl dessen, was als Nächstes fällt, die Sammlung zeigt alles.
 * Ein Kauf-Ziel darf es auf der Statistik-Seite nie geben (siehe
 * [Progress]), eine Kachel dafür schon.
 */
enum class CollectionAxis {
    /** Von Anfang an offen: KLASSIK, WIESE, das KLASSIK-Set, SCHLICHT. */
    NONE,
    BEST_SCORE,
    PERFECT_STREAK,
    DAILY_STREAK,
    RUN_COUNT,
    TOTAL_SCORE,
    DAYS_PLAYED,
    MONTHS_PLAYED,
    SEASON_DAYS,
    SKIN_COLLECTION,
    SCENE_COLLECTION,

    /** Offene Ton-Sets — die Bedingung des PERLENKRANZ. */
    SOUND_COLLECTION,

    /** Gekauft, nicht verdient: die Gönner-Skins. */
    PURCHASE
}

/**
 * Der Stand einer Kachel: worauf sie zählt, wo man steht, wo sie fällt,
 * und ob sie offen ist.
 *
 * [current] ist nie größer als [target]. Eine offene Kachel steht immer
 * auf voll, auch wenn der Rohwert darunter liegt — eine Welt aus der
 * Besitz-Menge (Rekord 90, STADT aus dem Bestand) oder ein Saison-Skin
 * aus dem letzten Jahr sind offen, und ein halber Balken daran wäre
 * gelogen.
 */
data class CollectionItemProgress(
    val axis: CollectionAxis,
    val current: Int,
    val target: Int,
    val unlocked: Boolean
) {
    /** Anteil 0..1 für den kleinen Balken unter der Kachel. */
    val fraction: Float get() =
        if (target <= 0) 1f else (current.toFloat() / target).coerceIn(0f, 1f)
}

/**
 * Der Fortschritt jeder einzelnen Kachel der Sammlung (Plan 7.1): VOGEL,
 * WELT, TON und RAHMEN, dazu die Zähler der Reiter.
 *
 * [Progress.goals] beantwortet „was fällt als Nächstes?“ und lässt dafür
 * weg, was kein Ziel sein darf (Gönner, Saison außerhalb des Monats,
 * Rahmen). Die Sammlung fragt pro Kachel, deshalb steht hier eine
 * Abfrage je Id. Die Schwellen kommen aus denselben Tabellen wie die
 * Ziele ([Progress.SKIN_THRESHOLDS] und Geschwister): Es gibt sie nur
 * einmal, und `ProgressTest` nagelt sie an `isUnlocked` fest.
 */
object CollectionProgress {

    /** Wie viele Kacheln der Reiter VOGEL hat — alle Skins, auch Saison und Gönner. */
    val SKIN_TOTAL: Int = SkinId.entries.size

    val SCENE_TOTAL: Int = SceneId.entries.size

    val SOUND_TOTAL: Int = SoundSetId.entries.size

    val FRAME_TOTAL: Int = CardFrame.entries.size

    /**
     * Ein Skin. [month] und [seasonDays] tragen das laufende Saison-Fenster
     * (siehe GameStore.seasonDaysFor): Außerhalb des eigenen Monats steht
     * ein Saison-Skin auf 0, das Fenster ist zu.
     */
    fun skin(id: SkinId, stats: SkinStats, month: Int, seasonDays: Int): CollectionItemProgress {
        val unlocked = SkinPaint.isUnlocked(id, stats)
        Progress.SKIN_THRESHOLDS.firstOrNull { it.first == id }?.let { (_, axis, target) ->
            return item(axis.toCollectionAxis(), value(axis, stats), target, unlocked)
        }
        Season.forSkin(id)?.let { season ->
            val days = if (season.month == month) seasonDays.coerceAtLeast(0) else 0
            return item(CollectionAxis.SEASON_DAYS, days, season.requiredDays, unlocked)
        }
        return when {
            SkinPaint.isPatron(id) ->
                item(CollectionAxis.PURCHASE, if (stats.patronOwned) 1 else 0, 1, unlocked)
            // Der Abschluss: Er zählt selbst mit, also fehlt zum Ziel genau
            // er (wie in Progress.goals).
            id == SkinId.REGENBOGEN -> item(
                CollectionAxis.SKIN_COLLECTION,
                SkinPaint.unlockedCount(stats),
                SkinPaint.collectableCount() - 1,
                unlocked
            )
            else -> item(CollectionAxis.NONE, 1, 1, unlocked)
        }
    }

    /** Eine Welt, samt Besitz-Menge (über [ScenePaint.isUnlocked]). */
    fun scene(id: SceneId, stats: SkinStats): CollectionItemProgress {
        val unlocked = ScenePaint.isUnlocked(id, stats)
        Progress.SCENE_THRESHOLDS.firstOrNull { it.first == id }?.let { (_, axis, target) ->
            return item(axis.toCollectionAxis(), value(axis, stats), target, unlocked)
        }
        return if (id == SceneId.WELTRAUM) {
            item(
                CollectionAxis.SCENE_COLLECTION,
                ScenePaint.unlockedCount(stats),
                SceneId.entries.size - 1,
                unlocked
            )
        } else {
            item(CollectionAxis.NONE, 1, 1, unlocked)
        }
    }

    /** Ein Ton-Set. */
    fun sound(id: SoundSetId, stats: SkinStats): CollectionItemProgress {
        val unlocked = SoundBank.isUnlocked(id, stats)
        Progress.SOUND_THRESHOLDS.firstOrNull { it.first == id }?.let { (_, axis, target) ->
            return item(axis.toCollectionAxis(), value(axis, stats), target, unlocked)
        }
        return item(CollectionAxis.NONE, 1, 1, unlocked)
    }

    /** Eine Rahmenstufe, siehe [FrameProgress]. */
    fun frame(frame: CardFrame, stats: SkinStats): CollectionItemProgress =
        FrameProgress.of(frame, stats)

    /** Offene Skins für den Reiter VOGEL („12/46“), Saison und Gönner zählen mit. */
    fun skinCount(stats: SkinStats): Int = SkinId.entries.count { SkinPaint.isUnlocked(it, stats) }

    fun sceneCount(stats: SkinStats): Int = ScenePaint.unlockedCount(stats)

    fun soundCount(stats: SkinStats): Int = SoundBank.unlockedCount(stats)

    fun frameCount(stats: SkinStats): Int = CardStyle.unlockedCount(stats)

    internal fun item(
        axis: CollectionAxis,
        value: Int,
        target: Int,
        unlocked: Boolean
    ) = CollectionItemProgress(
        axis = axis,
        current = if (unlocked) target else min(value.coerceAtLeast(0), target),
        target = target,
        unlocked = unlocked
    )

    private fun GoalAxis.toCollectionAxis(): CollectionAxis = when (this) {
        GoalAxis.BEST_SCORE -> CollectionAxis.BEST_SCORE
        GoalAxis.PERFECT_STREAK -> CollectionAxis.PERFECT_STREAK
        GoalAxis.DAILY_STREAK -> CollectionAxis.DAILY_STREAK
        GoalAxis.RUN_COUNT -> CollectionAxis.RUN_COUNT
        GoalAxis.TOTAL_SCORE -> CollectionAxis.TOTAL_SCORE
        GoalAxis.DAYS_PLAYED -> CollectionAxis.DAYS_PLAYED
        GoalAxis.MONTHS_PLAYED -> CollectionAxis.MONTHS_PLAYED
        GoalAxis.SEASON_DAYS -> CollectionAxis.SEASON_DAYS
        GoalAxis.SKIN_COLLECTION -> CollectionAxis.SKIN_COLLECTION
        GoalAxis.SCENE_COLLECTION -> CollectionAxis.SCENE_COLLECTION
    }

    /** Der Rohwert auf einer Achse der Tabellen. */
    private fun value(axis: GoalAxis, stats: SkinStats): Int = when (axis) {
        GoalAxis.BEST_SCORE -> stats.bestScore
        GoalAxis.PERFECT_STREAK -> stats.bestPerfectStreak
        GoalAxis.DAILY_STREAK -> stats.bestDailyStreak
        GoalAxis.RUN_COUNT -> stats.runCount
        GoalAxis.TOTAL_SCORE -> stats.totalScore
        GoalAxis.DAYS_PLAYED -> stats.daysPlayed
        GoalAxis.MONTHS_PLAYED -> stats.monthsPlayed
        // Kommt in den Tabellen nicht vor, die Saison läuft oben eigens.
        GoalAxis.SEASON_DAYS -> 0
        GoalAxis.SKIN_COLLECTION -> SkinPaint.unlockedCount(stats)
        GoalAxis.SCENE_COLLECTION -> ScenePaint.unlockedCount(stats)
    }
}

/**
 * Der Fortschritt der Rahmenstufen, jede auf ihrer eigenen Bedingung
 * ([CardStyle.earns]): DOPPELLINIE, ZINNEN und PRACHT auf den gesammelten
 * Skins ([CardStyle.FRAME_STEPS] 10/20/33), KASKADE auf den Welten (x/6),
 * PERLENKRANZ auf den Tönen (x/3), KRONE auf der ganzen Skin-Sammlung.
 *
 * Der Balken zeigt die eigene Bedingung, offen ist eine Stufe aber erst
 * mit allen darunter ([CardStyle.isUnlocked]). Wer alle Welten hat und
 * zwölf Skins, sieht die KASKADE also voll und trotzdem gesperrt — sie
 * ist vorgemerkt, nicht verdient. Der Hinweistext („PRACHT UND ALLE
 * WELTEN“) sagt, was fehlt.
 */
object FrameProgress {

    fun of(frame: CardFrame, stats: SkinStats): CollectionItemProgress {
        val unlocked = CardStyle.isUnlocked(frame, stats)
        val skins = SkinPaint.unlockedCount(stats)
        return when (frame) {
            CardFrame.SCHLICHT -> CollectionProgress.item(CollectionAxis.NONE, 1, 1, unlocked)
            CardFrame.DOPPELLINIE -> collection(skins, CardStyle.FRAME_STEPS[0], unlocked)
            CardFrame.ZINNEN -> collection(skins, CardStyle.FRAME_STEPS[1], unlocked)
            CardFrame.PRACHT -> collection(skins, CardStyle.FRAME_STEPS[2], unlocked)
            CardFrame.KASKADE -> CollectionProgress.item(
                CollectionAxis.SCENE_COLLECTION,
                ScenePaint.unlockedCount(stats),
                SceneId.entries.size,
                unlocked
            )
            CardFrame.PERLENKRANZ -> CollectionProgress.item(
                CollectionAxis.SOUND_COLLECTION,
                SoundBank.unlockedCount(stats),
                SoundSetId.entries.size,
                unlocked
            )
            CardFrame.KRONE -> collection(skins, SkinPaint.collectableCount(), unlocked)
        }
    }

    private fun collection(skins: Int, target: Int, unlocked: Boolean) =
        CollectionProgress.item(CollectionAxis.SKIN_COLLECTION, skins, target, unlocked)
}

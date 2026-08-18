package de.robinrehbein.punkt.ui.data

import de.robinrehbein.punkt.game.DailyChallenge
import de.robinrehbein.punkt.game.CardFrame
import de.robinrehbein.punkt.game.CardStyle
import de.robinrehbein.punkt.game.SceneId
import de.robinrehbein.punkt.game.ScenePaint
import de.robinrehbein.punkt.game.Season
import de.robinrehbein.punkt.game.SkinId
import de.robinrehbein.punkt.game.SkinPaint
import de.robinrehbein.punkt.game.SkinStats
import de.robinrehbein.punkt.game.SoundBank
import de.robinrehbein.punkt.game.SoundSetId
import de.robinrehbein.punkt.game.SyncState

/**
 * Persistiert Highscore, Daily-Challenge-Stand, Bestleistungen und den
 * gewählten Skin — samt der Regeln, die daran hängen: Tages-Serie,
 * Saison-Fenster, Tagespass, Abgleich mit der Uhr.
 *
 * Das ist keine Speicher-Schicht, das sind Spielregeln mit einem
 * Speicher darunter. Sie lagen bis v2.24 zweimal im Repo — 470 Zeilen
 * Kotlin hier und 269 Zeilen Swift in `ios/.../Support/ScoreStore.swift`
 * — und liefen an genau einer Stelle schon auseinander: Android rechnete
 * einen Lauf dem Tag zu, an dem er startete, iOS dem, an dem er endete
 * (siehe parity/README.md). Deshalb stehen sie jetzt hier, über einem
 * [KeyValueStore], den jede Plattform selbst mitbringt.
 *
 * Die Keys tragen noch das "_timing"-Suffix aus der Zeit, als es neben
 * STOPP auch den FLIP-Modus gab (bis v2.5, Tag "v2.5-mit-flip") — so
 * überleben bestehende Highscores das Update.
 */
class GameStore(private val prefs: KeyValueStore) {

    val bestScore: Int
        get() = prefs.int(KEY_BEST, 0)

    val runCount: Int
        get() = prefs.int(KEY_RUNS, 0)

    /** Beste jemals erreichte Perfekt-Serie (für Skin-Freischaltungen). */
    val bestPerfectStreak: Int
        get() = prefs.int(KEY_BEST_PERFECT, 0)

    // ===== Ausdauer-Achsen (Menge statt Können) =====

    /** Summe aller je erspielten Punkte. */
    val totalScore: Int
        get() = prefs.int(KEY_TOTAL_SCORE, 0)

    /** Kalendertage mit mindestens einem Lauf. */
    val daysPlayed: Int
        get() = prefs.int(KEY_DAYS_PLAYED, 0)

    /**
     * Bitmaske der Kalendermonate mit mindestens einem Lauf (Bit 0 =
     * Januar). Eine Maske statt eines Zählers, weil "in drei Monaten
     * gespielt" verschiedene Monate meint — dreimal im Mai ist einer.
     */
    val monthsPlayedMask: Int
        get() = prefs.int(KEY_MONTHS_PLAYED, 0)

    /**
     * Bitmaske der verdienten Saison-Skins (siehe Season.bit in :core).
     * Sie ist die einzige Wahrheit über Saison-Skins: Der Kalender allein
     * würde den Kürbis im November wieder wegnehmen.
     */
    val seasonEarned: Int
        get() = prefs.int(KEY_SEASON_EARNED, 0)

    /**
     * Gönner-Paket gekauft ("patron_pack"). Wie [adsRemoved] nur der
     * lokale Spiegel des Play-Kaufs, damit die drei Gönner-Skins beim
     * Start ohne Netz sofort dastehen.
     */
    var patronOwned: Boolean
        get() = prefs.boolean(KEY_PATRON, false)
        set(value) {
            prefs.edit { putBoolean(KEY_PATRON, value) }
        }

    /** Ton an/aus — überlebt App-Neustarts. */
    var soundMuted: Boolean
        get() = prefs.boolean(KEY_MUTED, false)
        set(value) {
            prefs.edit { putBoolean(KEY_MUTED, value) }
        }

    /** Tägliche Daily-Challenge-Erinnerung (Opt-in, lokal). */
    var reminderEnabled: Boolean
        get() = prefs.boolean(KEY_REMINDER, false)
        set(value) {
            prefs.edit { putBoolean(KEY_REMINDER, value) }
        }

    /**
     * Werbefrei gekauft ("remove_ads"). Play Billing ist die Wahrheit —
     * dieser Wert ist nur der lokale Spiegel, damit die UI beim Start
     * ohne Netz sofort weiß, dass keine Werbung erscheinen darf.
     */
    var adsRemoved: Boolean
        get() = prefs.boolean(KEY_ADS_REMOVED, false)
        set(value) {
            prefs.edit { putBoolean(KEY_ADS_REMOVED, value) }
        }

    /**
     * Gewählter Punkt-Skin, KLASSIK als Fallback. Beim Setzen wird der
     * Zeitpunkt mitgeschrieben: Für den Abgleich mit der Uhr ist die
     * Skin-Wahl der einzige Wert, bei dem nicht "größer", sondern
     * "neuer" gewinnt (siehe SyncState).
     */
    var selectedSkin: SkinId
        get() = SkinPaint.fromName(prefs.string(KEY_SKIN))
        set(value) {
            prefs.edit {
                putString(KEY_SKIN, value.name)
                putLong(KEY_SKIN_CHANGED, epochMillis())
            }
        }

    /**
     * Gewählte Kulisse, WIESE als Fallback. Wie beim Skin wird der
     * Zeitpunkt mitgeschrieben: Auch die Kulisse ist eine Entscheidung,
     * beim Abgleich mit der Uhr gewinnt deshalb die neuere (siehe
     * SyncState). Einen Tagespass gibt es hier bewusst nicht — die
     * Kulisse ist der seltene große Wechsel, nicht das Probierstück.
     */
    var selectedScene: SceneId
        get() = ScenePaint.fromName(prefs.string(KEY_SCENE))
        set(value) {
            prefs.edit {
                putString(KEY_SCENE, value.name)
                putLong(KEY_SCENE_CHANGED, epochMillis())
            }
        }

    /**
     * Gewähltes Ton-Set, KLASSIK als Fallback. Dieselbe Bauart wie Skin
     * und Kulisse — die dritte Entscheidung, bei der beim Abgleich die
     * neuere gewinnt und nicht die "größere" (siehe SyncState).
     */
    var selectedSound: SoundSetId
        get() = SoundBank.fromName(prefs.string(KEY_SOUND))
        set(value) {
            prefs.edit {
                putString(KEY_SOUND, value.name)
                putLong(KEY_SOUND_CHANGED, epochMillis())
            }
        }

    /**
     * Gewählter Rahmen der Score-Karte — null heißt "nie gewählt", und
     * das ist etwas anderes als SCHLICHT. Wer nie gewählt hat, bekommt
     * automatisch seine höchste verdiente Stufe; erst eine Wahl macht
     * SCHLICHT zu einer Entscheidung statt zu einem Anfangszustand.
     *
     * Anders als Skin, Kulisse und Ton-Set wird der Rahmen **nicht** mit
     * der Uhr abgeglichen und trägt deshalb auch keinen Zeitstempel: Die
     * Uhr hat keine Score-Karte. Ein Wert, den die Gegenseite nicht
     * benutzen kann, gehört nicht in den [SyncState] — er wäre nur ein
     * weiteres Feld, das auseinanderlaufen kann, ohne dass es jemandem
     * auffällt.
     */
    var selectedCardFrame: CardFrame?
        get() = CardStyle.fromName(prefs.string(KEY_CARD_FRAME))
        set(value) {
            prefs.edit {
                if (value == null) remove(KEY_CARD_FRAME) else putString(KEY_CARD_FRAME, value.name)
            }
        }

    // ===== Skin-Tagespass (Rewarded) =====

    /**
     * Der per Spot geliehene Skin — aber nur, wenn der Pass zu [epochDay]
     * gehört. Wie beim Tagesbest entscheidet allein der Vergleich mit dem
     * gespeicherten Tag; abgelaufene Pässe werden nicht aufgeräumt,
     * sondern beantworten die Frage einfach mit null.
     */
    fun skinPassFor(epochDay: Long): SkinId? {
        if (prefs.long(KEY_SKIN_PASS_DAY, Long.MIN_VALUE) != epochDay) return null
        val name = prefs.string(KEY_SKIN_PASS_SKIN) ?: return null
        return SkinPaint.ORDER.firstOrNull { it.name == name }
    }

    /**
     * Gewährt den Tagespass. Es gibt immer nur EINEN: Ein neuer Spot für
     * einen anderen Skin ersetzt den alten — der Pass ist zum Probieren
     * da, gesammelt wird weiter über Medaillen.
     */
    fun grantSkinPass(epochDay: Long, skin: SkinId) {
        prefs.edit {
                putLong(KEY_SKIN_PASS_DAY, epochDay)
                putString(KEY_SKIN_PASS_SKIN, skin.name)
        }
    }

    // ===== Daily Challenge =====

    /** Tagesbest-Score — gilt nur für den in [dailyDay] gespeicherten Tag. */
    val dailyBest: Int
        get() = prefs.int(KEY_DAILY_BEST, 0)

    /** Epoch-Day, zu dem [dailyBest] gehört. */
    val dailyDay: Long
        get() = prefs.long(KEY_DAILY_DAY, 0L)

    /** Aktuelle Serie an Tagen mit mindestens einem Daily-Lauf. */
    val dailyStreak: Int
        get() = prefs.int(KEY_DAILY_STREAK, 0)

    /**
     * Beste je erreichte Daily-Serie — der Wert, an dem PRISMA, KOI,
     * AURORA, DISCO und die Kulisse BERG hängen. Die laufende Serie taugt
     * dafür nicht: Sie fällt nach einer Lücke auf 1 zurück und würde
     * bereits gefeierte Freischaltungen wieder zusperren.
     *
     * Der Vergleich mit [dailyStreak] ist zugleich die Migration: Wer vor
     * v2.23 eine Serie aufgebaut hat, hat den Schlüssel noch nicht — dann
     * ist die laufende Serie der beste bekannte Wert.
     */
    val bestDailyStreak: Int
        get() = maxOf(prefs.int(KEY_BEST_DAILY_STREAK, 0), dailyStreak)

    /** Tagesbest für einen konkreten Tag — 0, wenn der Tag nicht passt. */
    fun dailyBestFor(epochDay: Long): Int =
        if (dailyDay == epochDay) dailyBest else 0

    /**
     * Die Serie, wie sie ein Daily-Lauf HEUTE fortschreiben würde. Für die
     * Anzeige auf dem Startscreen: War gestern der letzte Lauf, läuft die
     * Serie noch; liegt er länger zurück, ist sie faktisch gerissen.
     */
    fun dailyStreakPreviewFor(epochDay: Long): Int = when {
        dailyDay == epochDay -> dailyStreak
        dailyDay == epochDay - 1 -> dailyStreak
        else -> 0
    }

    /**
     * Meldet einen beendeten Lauf; liefert true, wenn es ein neuer Rekord
     * war.
     *
     * Der Lauf bringt seinen Kalender selbst mit ([epochDay], [month],
     * [year]) statt hier die Uhr zu fragen: Ein Lauf gehört dem Tag, an
     * dem er gestartet ist — auch wenn er über Mitternacht läuft —, und
     * genau dieser Tag entscheidet schon über den Daily-Seed.
     */
    fun submitRun(score: Int, epochDay: Long, month: Int, year: Int): Boolean {
        val record = score > bestScore
        prefs.edit {
                putInt(KEY_RUNS, runCount + 1)
                putInt(KEY_TOTAL_SCORE, totalScore + score)

                // Ein neuer Kalendertag zählt genau einmal — gespeichert wird
                // deshalb der Zähler UND der Tag, an dem er zuletzt stieg.
                if (prefs.long(KEY_LAST_PLAYED_DAY, Long.MIN_VALUE) != epochDay) {
                    putInt(KEY_DAYS_PLAYED, daysPlayed + 1)
                    putLong(KEY_LAST_PLAYED_DAY, epochDay)
                }
                putInt(KEY_MONTHS_PLAYED, monthsPlayedMask or (1 shl (month - 1)))

                writeSeasonProgress(epochDay, month, year)

                if (record) putInt(KEY_BEST, score)
        }
        return record
    }

    /**
     * Saison-Fortschritt: Tage mit Lauf im laufenden Saison-Monat. Der
     * Fensterschlüssel (Jahr*100+Monat) setzt den Zähler beim Wechsel des
     * Fensters zurück — der Oktober 2026 fängt wieder bei null an, sonst
     * bekäme man den Kürbis über Jahre zusammengestückelt.
     *
     * Das erreichte Bit wird dagegen NIE zurückgenommen: Verdient ist
     * verdient, auch im November.
     */
    private fun KeyValueEditor.writeSeasonProgress(
        epochDay: Long,
        month: Int,
        year: Int
    ) {
        val season = Season.forMonth(month) ?: return
        val window = year * 100 + month
        val sameWindow = prefs.int(KEY_SEASON_WINDOW, 0) == window
        val daysSoFar = if (sameWindow) prefs.int(KEY_SEASON_DAYS, 0) else 0
        val lastDay = if (sameWindow) {
            prefs.long(KEY_SEASON_LAST_DAY, Long.MIN_VALUE)
        } else {
            Long.MIN_VALUE
        }
        val days = if (lastDay == epochDay) daysSoFar else daysSoFar + 1

        putInt(KEY_SEASON_WINDOW, window)
        putInt(KEY_SEASON_DAYS, days)
        putLong(KEY_SEASON_LAST_DAY, epochDay)
        if (days >= season.requiredDays) {
            putInt(KEY_SEASON_EARNED, seasonEarned or season.bit)
        }
    }

    /** Meldet die höchste Perfekt-Serie eines Laufs. */
    fun submitPerfectStreak(streak: Int) {
        if (streak > bestPerfectStreak) {
            prefs.edit { putInt(KEY_BEST_PERFECT, streak) }
        }
    }

    /**
     * Meldet einen beendeten Daily-Lauf: schreibt die Tages-Serie fort
     * (nur der erste Lauf des Tages zählt dafür) und aktualisiert den
     * Tagesbest-Score. Liefert true bei neuem Tagesbest.
     */
    fun submitDailyRun(epochDay: Long, score: Int): Boolean {
        val firstRunToday = dailyDay != epochDay
        if (firstRunToday) {
            val streak = DailyChallenge.nextStreak(
                lastPlayedEpochDay = dailyDay,
                currentStreak = dailyStreak,
                todayEpochDay = epochDay
            )
            prefs.edit {
                putInt(KEY_DAILY_STREAK, streak)
                // Der Bestwert wandert bei jedem Schreiben der Serie mit:
                // Fällt sie gleich hier auf 1 zurück, bleibt oben stehen,
                // was einmal erreicht war.
                putInt(KEY_BEST_DAILY_STREAK, maxOf(bestDailyStreak, streak))
                putLong(KEY_DAILY_DAY, epochDay)
                putInt(KEY_DAILY_BEST, score)
            }
            return score > 0
        }
        if (score > dailyBest) {
            prefs.edit { putInt(KEY_DAILY_BEST, score) }
            return true
        }
        return false
    }

    /**
     * Tage mit Lauf im laufenden Saison-Fenster — 0, sobald der Kalender
     * weitergezogen ist. Der Wert steht bewusst nicht in [stats]: Er
     * verfällt mit dem Monat und taugt deshalb für keine Freischaltung,
     * nur für die Anzeige des Saison-Ziels (siehe Progress in :core).
     */
    fun seasonDaysFor(month: Int, year: Int): Int =
        if (prefs.int(KEY_SEASON_WINDOW, 0) == year * 100 + month) {
            prefs.int(KEY_SEASON_DAYS, 0)
        } else {
            0
        }

    /** Aktueller Stand gebündelt, für Skin-Freischaltungen. */
    fun stats(): SkinStats = SkinStats(
        bestScore = bestScore,
        bestPerfectStreak = bestPerfectStreak,
        bestDailyStreak = bestDailyStreak,
        runCount = runCount,
        totalScore = totalScore,
        daysPlayed = daysPlayed,
        // Gespeichert wird die Maske, gefragt ist die Anzahl VERSCHIEDENER
        // Monate — deshalb hier die gesetzten Bits zählen.
        monthsPlayed = monthsPlayedMask.countOneBits(),
        seasonEarned = seasonEarned,
        patronOwned = patronOwned
    )

    // ===== Abgleich mit der Uhr =====

    /**
     * Der lokale Stand als Austauschformat für den Data Layer.
     *
     * Ein nur geliehener Skin (Tagespass) wird bewusst nicht mitgeteilt:
     * Er ist morgen wieder weg, und die Uhr könnte ihn gar nicht
     * annehmen, weil sie ihre Freischaltungen selbst aus den
     * Bestleistungen ableitet. Damit bleibt die Regel auch hier gültig —
     * geliehen ist nicht verdient.
     */
    fun syncState(): SyncState {
        val chosen = selectedSkin
        val shareSkin = SkinPaint.isUnlocked(chosen, stats())
        val sceneShared = ScenePaint.isUnlocked(selectedScene, stats())
        val soundShared = SoundBank.isUnlocked(selectedSound, stats())
        return SyncState(
            bestScore = bestScore,
            runCount = runCount,
            bestPerfectStreak = bestPerfectStreak,
            dailyDay = dailyDay,
            dailyBest = dailyBest,
            dailyStreak = dailyStreak,
            bestDailyStreak = bestDailyStreak,
            totalScore = totalScore,
            daysPlayed = daysPlayed,
            lastPlayedDay = prefs.long(KEY_LAST_PLAYED_DAY, 0L),
            monthsPlayed = monthsPlayedMask,
            seasonEarned = seasonEarned,
            // Der Gönner-Kauf wird bewusst NICHT geteilt: Er hängt am
            // Google-Konto, und die Uhr stellt ihn über Play selbst wieder
            // her. Ein mitgeteiltes Flag wäre nur eine zweite, schlechtere
            // Wahrheit — und über den Data Layer fälschbar.
            skin = if (shareSkin) chosen.name else "",
            skinChangedAt = if (shareSkin) prefs.long(KEY_SKIN_CHANGED, 0L) else 0L,
            // Die Kulisse hat keinen Tagespass, sie ist also entweder
            // verdient oder gar nicht ausgewählt — die Prüfung bleibt
            // trotzdem stehen, damit ein wiederhergestelltes Backup keine
            // ungedeckte Wahl auf die Uhr trägt.
            scene = if (sceneShared) selectedScene.name else "",
            sceneChangedAt = if (sceneShared) prefs.long(KEY_SCENE_CHANGED, 0L) else 0L,
            // Dieselbe Prüfung für das Ton-Set: Die Uhr leitet ihre
            // Freischaltungen selbst aus den Ständen ab und würde ein
            // ungedecktes Set ohnehin abweisen.
            sound = if (soundShared) selectedSound.name else "",
            soundChangedAt = if (soundShared) prefs.long(KEY_SOUND_CHANGED, 0L) else 0L
        )
    }

    /**
     * Übernimmt einen zusammengeführten Stand und meldet, ob sich dabei
     * lokal etwas geändert hat. Genau diese Rückmeldung bremst den
     * Abgleich: Nur wer wirklich dazugelernt hat, antwortet der
     * Gegenseite — sonst würden sich beide Geräte endlos gegenseitig
     * bestätigen.
     *
     * Der Skin wird nur übernommen, wenn er hier auch spielbar ist: Die
     * Uhr kennt ihre eigenen Freischaltungen, und ein dort erspielter
     * Skin wäre am Telefon sonst plötzlich aktiv, ohne verdient zu sein.
     * Der Zeitstempel wandert trotzdem mit, damit die Entscheidung nicht
     * bei jedem Abgleich erneut aufschlägt.
     */
    fun applySync(state: SyncState): Boolean {
        val before = syncState()
        if (before == state) return false
        prefs.edit {
            if (state.bestScore > before.bestScore) putInt(KEY_BEST, state.bestScore)
            if (state.runCount > before.runCount) putInt(KEY_RUNS, state.runCount)
            if (state.bestPerfectStreak > before.bestPerfectStreak) {
                putInt(KEY_BEST_PERFECT, state.bestPerfectStreak)
        }
        // Zahlen wachsen nur, Masken werden verodert — dieselbe Regel wie
        // in SyncState.mergedWith. Ein Monat oder ein Saison-Skin, den nur
        // die Uhr gesehen hat, darf hier nicht verlorengehen.
        if (state.totalScore > before.totalScore) putInt(KEY_TOTAL_SCORE, state.totalScore)
        if (state.daysPlayed > before.daysPlayed) putInt(KEY_DAYS_PLAYED, state.daysPlayed)
        if (state.lastPlayedDay > before.lastPlayedDay) {
            putLong(KEY_LAST_PLAYED_DAY, state.lastPlayedDay)
        }
        if (state.monthsPlayed != before.monthsPlayed) {
            putInt(KEY_MONTHS_PLAYED, before.monthsPlayed or state.monthsPlayed)
        }
        if (state.seasonEarned != before.seasonEarned) {
            putInt(KEY_SEASON_EARNED, before.seasonEarned or state.seasonEarned)
        }
        if (state.dailyDay != before.dailyDay ||
            state.dailyBest != before.dailyBest ||
            state.dailyStreak != before.dailyStreak
        ) {
            putLong(KEY_DAILY_DAY, state.dailyDay)
            putInt(KEY_DAILY_BEST, state.dailyBest)
            putInt(KEY_DAILY_STREAK, state.dailyStreak)
        }
        // Der Bestwert steht bewusst außerhalb dieses Blocks: Die aktuelle
        // Serie darf beim Abgleich auch kleiner werden (eine Lücke reißt
        // sie), der Bestwert nie. Verglichen wird mit dem ROHEN Wert, nicht
        // mit dem aus syncState(): Ein Bestand ohne den Schlüssel leiht
        // sich seinen Bestwert von der laufenden Serie — genau die wird
        // hier gerade womöglich kleiner geschrieben.
        val bestDaily = maxOf(state.bestDailyStreak, before.bestDailyStreak)
        if (bestDaily > prefs.int(KEY_BEST_DAILY_STREAK, 0)) {
            putInt(KEY_BEST_DAILY_STREAK, bestDaily)
        }
        // Bewusst gegen den ROHEN Zeitstempel geprüft, nicht gegen den aus
        // syncState(): Wer sich gerade einen Tagespass-Skin ausgesucht
        // hat, teilt diese Wahl zwar nicht mit, soll sie aber auch nicht
        // von einer älteren Wahl der Uhr weggerissen bekommen.
        if (state.skinChangedAt > prefs.long(KEY_SKIN_CHANGED, 0L)) {
            putLong(KEY_SKIN_CHANGED, state.skinChangedAt)
            val incoming = SkinPaint.fromName(state.skin)
            // stats() liest die Werte, die gerade erst geschrieben werden —
            // deshalb hier mit den zusammengeführten Zahlen prüfen.
            if (SkinPaint.isUnlocked(incoming, mergedStats(state, before))) {
                putString(KEY_SKIN, incoming.name)
            }
        }
        // Dieselbe Regel für die Kulisse: Die neuere Wahl gewinnt, aber
        // nur, wenn sie hier auch verdient ist — verdient bleibt verdient.
        if (state.sceneChangedAt > prefs.long(KEY_SCENE_CHANGED, 0L)) {
            putLong(KEY_SCENE_CHANGED, state.sceneChangedAt)
            val incoming = ScenePaint.fromName(state.scene)
            if (ScenePaint.isUnlocked(incoming, mergedStats(state, before))) {
                putString(KEY_SCENE, incoming.name)
            }
        }
        // Und dieselbe für das Ton-Set.
        if (state.soundChangedAt > prefs.long(KEY_SOUND_CHANGED, 0L)) {
            putLong(KEY_SOUND_CHANGED, state.soundChangedAt)
            val incoming = SoundBank.fromName(state.sound)
            if (SoundBank.isUnlocked(incoming, mergedStats(state, before))) {
                putString(KEY_SOUND, incoming.name)
            }
        }
        }
        return true
    }

    /**
     * Der Stand, wie er nach dem Zusammenführen aussieht. [stats] taugt
     * hier nicht: Es liest die Werte aus den Preferences, die gerade erst
     * geschrieben werden — Skin- und Kulissen-Prüfung liefen sonst gegen
     * den alten Stand und lehnten eine soeben verdiente Wahl ab.
     */
    private fun mergedStats(state: SyncState, before: SyncState) = SkinStats(
        bestScore = maxOf(state.bestScore, before.bestScore),
        bestPerfectStreak = maxOf(state.bestPerfectStreak, before.bestPerfectStreak),
        bestDailyStreak = maxOf(state.bestDailyStreak, before.bestDailyStreak),
        runCount = maxOf(state.runCount, before.runCount),
        totalScore = maxOf(state.totalScore, before.totalScore),
        daysPlayed = maxOf(state.daysPlayed, before.daysPlayed),
        monthsPlayed = (state.monthsPlayed or before.monthsPlayed).countOneBits(),
        seasonEarned = state.seasonEarned or before.seasonEarned,
        // Der Kauf steht nicht im Austauschformat, also gilt hier der
        // lokale Spiegel — sonst fiele ein Gönner-Skin beim Abgleich
        // stumm auf KLASSIK zurück.
        patronOwned = patronOwned
    )

    private companion object {
        const val PREFS_NAME = "punkt_scores"
        const val KEY_BEST = "best_score_timing"
        const val KEY_RUNS = "run_count_timing"
        const val KEY_MUTED = "sound_muted"
        const val KEY_REMINDER = "daily_reminder"
        const val KEY_BEST_PERFECT = "best_perfect_streak"
        const val KEY_SKIN = "selected_skin"
        const val KEY_SCENE = "selected_scene"
        const val KEY_SCENE_CHANGED = "scene_changed_at"
        const val KEY_SOUND = "selected_sound"
        const val KEY_SOUND_CHANGED = "sound_changed_at"
        const val KEY_CARD_FRAME = "selected_card_frame"
        const val KEY_ADS_REMOVED = "ads_removed"
        const val KEY_DAILY_BEST = "daily_best"
        const val KEY_DAILY_DAY = "daily_day"
        const val KEY_DAILY_STREAK = "daily_streak"
        // Der Bestwert der Daily-Serie (ab v2.23). Bestände ohne diesen
        // Schlüssel starten mit der laufenden Serie, siehe bestDailyStreak.
        const val KEY_BEST_DAILY_STREAK = "best_daily_streak"
        const val KEY_SKIN_CHANGED = "skin_changed_at"
        const val KEY_SKIN_PASS_SKIN = "skin_pass_skin"
        const val KEY_SKIN_PASS_DAY = "skin_pass_day"
        const val KEY_TOTAL_SCORE = "total_score"
        const val KEY_DAYS_PLAYED = "days_played"
        const val KEY_LAST_PLAYED_DAY = "last_played_day"
        const val KEY_MONTHS_PLAYED = "months_played"
        // Fenster, Tageszähler und letzter gezählter Tag der laufenden
        // Saison; nur KEY_SEASON_EARNED überlebt den Monat.
        const val KEY_SEASON_WINDOW = "season_window"
        const val KEY_SEASON_DAYS = "season_days"
        const val KEY_SEASON_LAST_DAY = "season_last_day"
        const val KEY_SEASON_EARNED = "season_earned"
        const val KEY_PATRON = "patron_owned"
    }
}

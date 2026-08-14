import Foundation

/// Port von app/.../ScoreStore.kt: Persistiert Highscore, Daily-Stand,
/// Bestleistungen und den gewählten Skin — auf iOS über UserDefaults,
/// synchron und simpel, genau richtig für eine Handvoll Zahlen.
final class ScoreStore {

    private let defaults: UserDefaults

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    var bestScore: Int {
        return defaults.integer(forKey: ScoreStore.keyBest)
    }

    var runCount: Int {
        return defaults.integer(forKey: ScoreStore.keyRuns)
    }

    /// Beste jemals erreichte Perfekt-Serie (für Skin-Freischaltungen).
    var bestPerfectStreak: Int {
        return defaults.integer(forKey: ScoreStore.keyBestPerfect)
    }

    // MARK: - Ausdauer (Menge statt Können)

    /// Summe aller je erspielten Punkte.
    var totalScore: Int {
        return defaults.integer(forKey: ScoreStore.keyTotalScore)
    }

    /// Anzahl Kalendertage mit mindestens einem Lauf.
    var daysPlayed: Int {
        return defaults.integer(forKey: ScoreStore.keyDaysPlayed)
    }

    /// Letzter gespielter Kalendertag als Epoch-Day, 0 = noch nie.
    var lastPlayedDay: Int64 {
        return Int64(defaults.integer(forKey: ScoreStore.keyLastPlayedDay))
    }

    /// Bitmaske der Kalendermonate mit mindestens einem Lauf (Bit 0 = Januar).
    var monthsPlayedMask: Int {
        return defaults.integer(forKey: ScoreStore.keyMonthsPlayed)
    }

    /// Bitmaske der verdienten Saison-Skins (siehe Season.bit). Einmal
    /// gesetzte Bits werden nie wieder gelöscht — verdient bleibt verdient,
    /// auch wenn der Monat vorbei ist.
    var seasonEarned: Int {
        return defaults.integer(forKey: ScoreStore.keySeasonEarned)
    }

    /// Ton an/aus — überlebt App-Neustarts.
    var soundMuted: Bool {
        get { return defaults.bool(forKey: ScoreStore.keyMuted) }
        set { defaults.set(newValue, forKey: ScoreStore.keyMuted) }
    }

    /// Tägliche Daily-Challenge-Erinnerung (Opt-in, lokal).
    var reminderEnabled: Bool {
        get { return defaults.bool(forKey: ScoreStore.keyReminder) }
        set { defaults.set(newValue, forKey: ScoreStore.keyReminder) }
    }

    /// Gewählter Punkt-Skin, KLASSIK als Fallback.
    var selectedSkin: DotSkin {
        get { return DotSkin.fromName(defaults.string(forKey: ScoreStore.keySkin)) }
        set { defaults.set(newValue.rawValue, forKey: ScoreStore.keySkin) }
    }

    /// Gewählte Kulisse, WIESE als Fallback. Wie die Skin-Wahl eine
    /// Entscheidung, keine Bestleistung — und ohne Tagespass: Die Kulisse
    /// ist der seltene große Wechsel, nicht das Probierstück.
    var selectedScene: SceneId {
        get { return SceneId.fromName(defaults.string(forKey: ScoreStore.keyScene)) }
        set { defaults.set(newValue.rawValue, forKey: ScoreStore.keyScene) }
    }

    /// Gewähltes Ton-Set, KLASSIK als Fallback — die dritte Sammlung
    /// nach demselben Muster.
    var selectedSound: SoundSetId {
        get { return SoundSetId.fromName(defaults.string(forKey: ScoreStore.keySound)) }
        set { defaults.set(newValue.rawValue, forKey: ScoreStore.keySound) }
    }

    // MARK: - Daily Challenge

    /// Tagesbest-Score — gilt nur für den in `dailyDay` gespeicherten Tag.
    var dailyBest: Int {
        return defaults.integer(forKey: ScoreStore.keyDailyBest)
    }

    /// Epoch-Day, zu dem `dailyBest` gehört.
    var dailyDay: Int64 {
        return Int64(defaults.integer(forKey: ScoreStore.keyDailyDay))
    }

    /// Aktuelle Serie an Tagen mit mindestens einem Daily-Lauf.
    var dailyStreak: Int {
        return defaults.integer(forKey: ScoreStore.keyDailyStreak)
    }

    /// Tagesbest für einen konkreten Tag — 0, wenn der Tag nicht passt.
    func dailyBestFor(epochDay: Int64) -> Int {
        return dailyDay == epochDay ? dailyBest : 0
    }

    /// Die Serie, wie sie ein Daily-Lauf HEUTE fortschreiben würde. Für die
    /// Anzeige auf dem Startscreen.
    func dailyStreakPreviewFor(epochDay: Int64) -> Int {
        if dailyDay == epochDay {
            return dailyStreak
        }
        if dailyDay == epochDay - 1 {
            return dailyStreak
        }
        return 0
    }

    /// Meldet einen beendeten Lauf; liefert true bei neuem Rekord.
    @discardableResult
    func submitRun(score: Int) -> Bool {
        defaults.set(runCount + 1, forKey: ScoreStore.keyRuns)
        noteEndurance(score: score)
        if score > bestScore {
            defaults.set(score, forKey: ScoreStore.keyBest)
            return true
        }
        return false
    }

    /// Die Ausdauer-Achsen eines Laufs: Punkte insgesamt, Kalendertage,
    /// Monate und der Fortschritt im laufenden Saison-Fenster. Der Kalender
    /// kommt aus der Geräte-Uhr — dieselbe Quelle, aus der auch TAGESZEIT
    /// und JAHRESZEIT ihr Kleid ziehen.
    private func noteEndurance(score: Int) {
        defaults.set(totalScore + max(score, 0), forKey: ScoreStore.keyTotalScore)

        let now = Date()
        let epochDay = DailyChallenge.todayEpochDay(date: now)
        if lastPlayedDay != epochDay {
            defaults.set(daysPlayed + 1, forKey: ScoreStore.keyDaysPlayed)
            defaults.set(Int(epochDay), forKey: ScoreStore.keyLastPlayedDay)
        }

        let parts = Calendar.current.dateComponents([.year, .month], from: now)
        guard let month = parts.month, let year = parts.year else {
            return
        }
        // Maske statt Zähler: Zwölf Läufe im Januar sind ein Monat, nicht
        // zwölf. Gezählt wird in stats() über die gesetzten Bits.
        defaults.set(monthsPlayedMask | (1 << (month - 1)), forKey: ScoreStore.keyMonthsPlayed)
        noteSeason(year: year, month: month, epochDay: epochDay)
    }

    /// Saison-Fortschritt: Tage mit Lauf im aktiven Saison-Monat. Das
    /// Fenster ist Jahr*100+Monat; wechselt es, beginnt der Tageszähler von
    /// vorn — die verdiente Maske bleibt davon unberührt.
    private func noteSeason(year: Int, month: Int, epochDay: Int64) {
        guard let season = Season.forMonth(month) else {
            return
        }
        let window = year * 100 + month
        if defaults.integer(forKey: ScoreStore.keySeasonWindow) != window {
            defaults.set(window, forKey: ScoreStore.keySeasonWindow)
            defaults.set(0, forKey: ScoreStore.keySeasonDays)
            defaults.set(0, forKey: ScoreStore.keySeasonLastDay)
        }
        // Nur der erste Lauf des Tages zählt: Anwesenheit, nicht Sitzfleisch.
        if Int64(defaults.integer(forKey: ScoreStore.keySeasonLastDay)) == epochDay {
            return
        }
        defaults.set(Int(epochDay), forKey: ScoreStore.keySeasonLastDay)
        let days = defaults.integer(forKey: ScoreStore.keySeasonDays) + 1
        defaults.set(days, forKey: ScoreStore.keySeasonDays)
        if days >= season.requiredDays {
            defaults.set(seasonEarned | season.bit, forKey: ScoreStore.keySeasonEarned)
        }
    }

    /// Meldet die höchste Perfekt-Serie eines Laufs.
    func submitPerfectStreak(_ streak: Int) {
        if streak > bestPerfectStreak {
            defaults.set(streak, forKey: ScoreStore.keyBestPerfect)
        }
    }

    /// Meldet einen beendeten Daily-Lauf: schreibt die Tages-Serie fort
    /// (nur der erste Lauf des Tages zählt dafür) und aktualisiert den
    /// Tagesbest-Score. Liefert true bei neuem Tagesbest.
    @discardableResult
    func submitDailyRun(epochDay: Int64, score: Int) -> Bool {
        let firstRunToday = dailyDay != epochDay
        if firstRunToday {
            let streak = DailyChallenge.nextStreak(
                lastPlayedEpochDay: dailyDay,
                currentStreak: dailyStreak,
                todayEpochDay: epochDay
            )
            defaults.set(streak, forKey: ScoreStore.keyDailyStreak)
            defaults.set(Int(epochDay), forKey: ScoreStore.keyDailyDay)
            defaults.set(score, forKey: ScoreStore.keyDailyBest)
            return score > 0
        }
        if score > dailyBest {
            defaults.set(score, forKey: ScoreStore.keyDailyBest)
            return true
        }
        return false
    }

    /// Tage mit Lauf im laufenden Saison-Fenster — 0, sobald der Kalender
    /// weitergezogen ist. Der Wert gehört nicht in `stats()`: Er verfällt
    /// mit dem Monat und taugt für keine Freischaltung, nur für die
    /// Anzeige des Saison-Ziels (siehe `Progress`).
    func seasonDaysFor(year: Int, month: Int) -> Int {
        guard defaults.integer(forKey: ScoreStore.keySeasonWindow) == year * 100 + month else {
            return 0
        }
        return defaults.integer(forKey: ScoreStore.keySeasonDays)
    }

    /// Alles, woraus sich Freischaltungen speisen — Bestleistungen und
    /// Ausdauer gebündelt.
    func stats() -> DotSkin.Stats {
        return DotSkin.Stats(
            bestScore: bestScore,
            bestPerfectStreak: bestPerfectStreak,
            bestDailyStreak: dailyStreak,
            runCount: runCount,
            totalScore: totalScore,
            daysPlayed: daysPlayed,
            // Die Anzahl gesetzter Bits ist die Anzahl gesehener Monate.
            monthsPlayed: monthsPlayedMask.nonzeroBitCount,
            seasonEarned: seasonEarned,
            // Kein Billing auf iOS: Die Gönner-Skins stehen im Menü, sind
            // aber gesperrt. Ein "gekauft" zu behaupten, das es nicht gibt,
            // wäre die schlechtere Lüge als ein gesperrter Skin.
            patronOwned: false
        )
    }

    // Gleiche Key-Namen wie die Android-SharedPreferences — reine Kosmetik,
    // aber so bleiben die Plattformen leicht vergleichbar.
    private static let keyBest = "best_score_timing"
    private static let keyRuns = "run_count_timing"
    private static let keyMuted = "sound_muted"
    private static let keyReminder = "daily_reminder"
    private static let keyBestPerfect = "best_perfect_streak"
    private static let keySkin = "selected_skin"
    private static let keyScene = "selected_scene"
    private static let keySound = "selected_sound"
    private static let keyDailyBest = "daily_best"
    private static let keyDailyDay = "daily_day"
    private static let keyDailyStreak = "daily_streak"
    private static let keyTotalScore = "total_score"
    private static let keyDaysPlayed = "days_played"
    private static let keyLastPlayedDay = "last_played_day"
    private static let keyMonthsPlayed = "months_played"
    private static let keySeasonEarned = "season_earned"
    // Nur lokaler Fortschritt im laufenden Saison-Fenster — verdient wird
    // daraus die Maske oben, und nur die überlebt den Monat.
    private static let keySeasonWindow = "season_window"
    private static let keySeasonDays = "season_days"
    private static let keySeasonLastDay = "season_last_day"
}

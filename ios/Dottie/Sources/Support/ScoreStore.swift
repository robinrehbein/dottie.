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
        if score > bestScore {
            defaults.set(score, forKey: ScoreStore.keyBest)
            return true
        }
        return false
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

    /// Aktuelle Bestleistungen gebündelt, für Skin-Freischaltungen.
    func stats() -> DotSkin.Stats {
        return DotSkin.Stats(
            bestScore: bestScore,
            bestPerfectStreak: bestPerfectStreak,
            bestDailyStreak: dailyStreak
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
    private static let keyDailyBest = "daily_best"
    private static let keyDailyDay = "daily_day"
    private static let keyDailyStreak = "daily_streak"
}

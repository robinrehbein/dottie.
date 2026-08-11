import Foundation

/// Kleine Helfer für die Lokalisierung (Localizable.strings, en + de).
enum L10n {

    static func text(_ key: String) -> String {
        return NSLocalizedString(key, comment: "")
    }

    static func format(_ key: String, _ arguments: CVarArg...) -> String {
        return String(format: text(key), locale: Locale.current, arguments: arguments)
    }

    /// "SERIE: n TAG/TAGE" bzw. "STREAK: n DAY/DAYS", sprachrichtig.
    static func streakLabel(days: Int) -> String {
        if days == 1 {
            return text("streak_one")
        }
        return format("streak_many", days)
    }

    // MARK: - Spott-Texte für den Rage-Faktor

    private static let tauntsZero = ["taunts_zero_0", "taunts_zero_1", "taunts_zero_2", "taunts_zero_3"]
    private static let tauntsClose = ["taunts_close_0", "taunts_close_1", "taunts_close_2"]
    private static let tauntsLow = ["taunts_low_0", "taunts_low_1", "taunts_low_2"]
    private static let tauntsDefault = ["taunts_default_0", "taunts_default_1", "taunts_default_2", "taunts_default_3"]

    /// Port von pickTaunt() aus GameOverlays.kt — die Auswahl ist
    /// deterministisch über (score + previousBest) % pool.count.
    static func pickTaunt(score: Int, previousBest: Int, isNewRecord: Bool) -> String {
        if isNewRecord {
            return text("new_record")
        }
        let gap = previousBest - score
        let pool: [String]
        if score == 0 {
            pool = tauntsZero
        } else if gap >= 1 && gap <= 3 {
            pool = tauntsClose
        } else if score < previousBest / 2 {
            pool = tauntsLow
        } else {
            pool = tauntsDefault
        }
        let line = text(pool[(score + previousBest) % pool.count])
        // Nur die "knapp daneben"-Zeilen tragen einen %1$d-Platzhalter.
        if line.contains("%1$d") {
            return String(format: line, locale: Locale.current, gap)
        }
        return line
    }
}

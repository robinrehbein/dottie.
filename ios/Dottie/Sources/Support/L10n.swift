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

    // MARK: - Ziel-Zeile

    /// Die eine Zeile des Startbildes:
    /// "NAECHSTER SKIN: MEDAILLE — 199/200 LAEUFE".
    ///
    /// Anders als im Game-Over und in der Statistik (dort steht die
    /// Kurzform "MEDAILLE 199/200", umgeben von ihresgleichen) steht diese
    /// Zeile allein. Sie muss deshalb aus sich heraus verständlich sein:
    /// Was es gibt, wofür man es bekommt, und vor allem — worin gezählt
    /// wird. "199/200" ohne Einheit ist eine Zahl ohne Aufgabe.
    static func goalLine(_ goal: Goal) -> String {
        // Genau eines von skin und scene ist gesetzt (siehe Goal).
        let kind = text(goal.scene != nil ? "goal_next_scene" : "goal_next_skin")
        return format(
            "goal_line", kind, text(goal.titleKey), goal.current, goal.target, axisLabel(goal.axis)
        )
    }

    /// Die Einheit einer Achse, in der Wortwahl der Freischalt-Hinweise
    /// im Skin-Menü ("500 LAEUFE", "DAILY-SERIE: 7 TAGE").
    private static func axisLabel(_ axis: GoalAxis) -> String {
        switch axis {
        case .bestScore: return text("goal_axis_best")
        case .perfectStreak: return text("goal_axis_perfect")
        case .dailyStreak: return text("goal_axis_daily_streak")
        case .runCount: return text("goal_axis_runs")
        case .totalScore: return text("goal_axis_total")
        case .daysPlayed: return text("goal_axis_days")
        case .monthsPlayed: return text("goal_axis_months")
        case .seasonDays: return text("goal_axis_season")
        case .skinCollection: return text("goal_axis_skins")
        case .sceneCollection: return text("goal_axis_scenes")
        }
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

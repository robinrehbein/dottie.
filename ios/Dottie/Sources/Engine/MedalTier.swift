import Foundation

/// Port von app/.../MedalTier.kt: Medaillen-Stufen ab 10/20/30/40 Punkten.
/// Die Reihenfolge der Fälle ist gleichzeitig die Rangfolge (rawValue) —
/// daran hängt die "NEUE MEDAILLE!"-Feier im Game-Over.
enum MedalTier: Int, CaseIterable {
    case bronze
    case silver
    case gold
    case platinum

    var threshold: Int {
        switch self {
        case .bronze: return 10
        case .silver: return 20
        case .gold: return 30
        case .platinum: return 40
        }
    }

    /// Localizable.strings-Key des Namens.
    var nameKey: String {
        switch self {
        case .bronze: return "medal_bronze"
        case .silver: return "medal_silver"
        case .gold: return "medal_gold"
        case .platinum: return "medal_platinum"
        }
    }

    /// Höchste erreichte Stufe, nil unterhalb von Bronze.
    static func forScore(_ score: Int) -> MedalTier? {
        return allCases.last(where: { score >= $0.threshold })
    }

    /// Nächste noch nicht erreichte Stufe, nil ab Platin.
    static func next(_ score: Int) -> MedalTier? {
        return allCases.first(where: { score < $0.threshold })
    }

    /// Bringt dieser Score eine höhere Stufe als der bisherige Bestwert?
    static func isUpgrade(score: Int, previousBest: Int) -> Bool {
        let newRank = forScore(score)?.rawValue ?? -1
        let oldRank = forScore(previousBest)?.rawValue ?? -1
        return newRank > oldRank
    }
}

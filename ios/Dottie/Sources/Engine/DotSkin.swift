import Foundation

/// Port von app/.../DotSkin.kt: Freischaltbare Punkt-Skins. Die rawValues
/// entsprechen den Kotlin-Enum-Namen, damit die Persistenz-Werte auf
/// beiden Plattformen gleich heißen. Farben sind ARGB-Werte.
///
/// Freischaltungen hängen an dauerhaften Leistungen (Rekord, beste
/// Perfekt-Serie, Daily-Serie), nie an Käufen.
enum DotSkin: String, CaseIterable {
    case klassik = "KLASSIK"
    case minze = "MINZE"
    case lava = "LAVA"
    case gold = "GOLD"
    case frost = "FROST"
    case schatten = "SCHATTEN"
    case prisma = "PRISMA"

    /// Localizable.strings-Key des Namens.
    var titleKey: String {
        switch self {
        case .klassik: return "skin_klassik"
        case .minze: return "skin_minze"
        case .lava: return "skin_lava"
        case .gold: return "skin_gold"
        case .frost: return "skin_frost"
        case .schatten: return "skin_schatten"
        case .prisma: return "skin_prisma"
        }
    }

    /// Localizable.strings-Key des Freischalt-Hinweises, nil für KLASSIK.
    var unlockHintKey: String? {
        switch self {
        case .klassik: return nil
        case .minze: return "skin_hint_minze"
        case .lava: return "skin_hint_lava"
        case .gold: return "skin_hint_gold"
        case .frost: return "skin_hint_frost"
        case .schatten: return "skin_hint_schatten"
        case .prisma: return "skin_hint_prisma"
        }
    }

    var body: UInt32 {
        switch self {
        case .klassik: return 0xFFD847
        case .minze: return 0x4BE38C
        case .lava: return 0xFF5A36
        case .gold: return 0xFFC400
        case .frost: return 0x8FD8FF
        case .schatten: return 0x6B4F8A
        case .prisma: return 0xFF6FD8
        }
    }

    var shade: UInt32 {
        switch self {
        case .klassik: return 0xF5A623
        case .minze: return 0x2BA55E
        case .lava: return 0xC22F12
        case .gold: return 0xCC8F00
        case .frost: return 0x4FA3D8
        case .schatten: return 0x43315C
        case .prisma: return 0xC93BAA
        }
    }

    var shine: UInt32 {
        switch self {
        case .klassik: return 0xFFF3B8
        case .minze: return 0xC8FFE0
        case .lava: return 0xFFC9A3
        case .gold: return 0xFFF7CC
        case .frost: return 0xE8F9FF
        case .schatten: return 0xCBB8E8
        case .prisma: return 0xB8F3FF
        }
    }

    /// Dauerhafte Bestleistungen, gegen die Freischaltungen geprüft werden.
    struct Stats {
        let bestScore: Int
        let bestPerfectStreak: Int
        let bestDailyStreak: Int
    }

    func isUnlocked(_ stats: Stats) -> Bool {
        switch self {
        case .klassik: return true
        case .minze: return stats.bestScore >= 10
        case .lava: return stats.bestScore >= 20
        case .gold: return stats.bestScore >= 30
        case .frost: return stats.bestScore >= 40
        case .schatten: return stats.bestPerfectStreak >= 4
        case .prisma: return stats.bestDailyStreak >= 3
        }
    }

    /// Skin zu einem gespeicherten Namen, KLASSIK als Fallback.
    static func fromName(_ name: String?) -> DotSkin {
        guard let name = name, let skin = DotSkin(rawValue: name) else {
            return .klassik
        }
        return skin
    }

    static func unlockedCount(_ stats: Stats) -> Int {
        return allCases.filter { $0.isUnlocked(stats) }.count
    }
}

import Foundation

/// Port von app/.../DotSkin.kt: Freischaltbare Punkt-Skins. Die rawValues
/// entsprechen den Kotlin-Enum-Namen, damit die Persistenz-Werte auf
/// beiden Plattformen gleich heißen.
///
/// Farben liegen nicht hier, sondern in `SkinPaint` (Port von SkinPaint.kt
/// aus :core): Ein Skin ist dort eine Funktion über das 13x13-Raster des
/// Vogels, damit gemusterte, animierte und auf den Lauf reagierende Skins
/// möglich sind.
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

    // Gemustert
    case biene = "BIENE"
    case melone = "MELONE"
    case pilz = "PILZ"
    case koi = "KOI"
    case galaxie = "GALAXIE"
    case karo = "KARO"

    // Bewegt
    case regenbogen = "REGENBOGEN"
    case aurora = "AURORA"
    case magma = "MAGMA"
    case neon = "NEON"
    case chrom = "CHROM"

    // Reagierend
    case chamaeleon = "CHAMAELEON"
    case kombo = "KOMBO"
    case tinte = "TINTE"

    /// Localizable.strings-Key des Namens.
    var titleKey: String { "skin_" + rawValue.lowercased() }

    /// Localizable.strings-Key des Freischalt-Hinweises, nil für KLASSIK.
    var unlockHintKey: String? {
        self == .klassik ? nil : "skin_hint_" + rawValue.lowercased()
    }

    /// Stellvertreter-Farben für Münzen und Score-Karte.
    var body: UInt32 { SkinPaint.body(self) }
    var shade: UInt32 { SkinPaint.shade(self) }
    var shine: UInt32 { SkinPaint.shine(self) }

    /// Farbe eines Rasterfelds des Vogels — siehe SkinPaint.cell.
    func cell(_ col: Int, _ row: Int, _ state: SkinPaint.State = .still) -> UInt32 {
        SkinPaint.cell(self, col, row, state)
    }

    func shineColor(_ state: SkinPaint.State = .still) -> UInt32 {
        SkinPaint.shine(self, state)
    }

    var hasTrail: Bool { SkinPaint.hasTrail(self) }

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
        case .biene: return stats.bestPerfectStreak >= 6
        case .melone: return stats.bestScore >= 25
        case .chamaeleon: return stats.bestScore >= 30
        case .pilz: return stats.bestScore >= 35
        case .chrom: return stats.bestScore >= 45
        case .galaxie: return stats.bestScore >= 50
        case .tinte: return stats.bestScore >= 55
        case .magma: return stats.bestScore >= 60
        case .koi: return stats.bestDailyStreak >= 7
        case .aurora: return stats.bestDailyStreak >= 14
        case .kombo: return stats.bestPerfectStreak >= 8
        case .karo: return stats.bestPerfectStreak >= 10
        case .neon: return stats.bestPerfectStreak >= 12
        // Der Regenbogen ist der Abschluss der Sammlung: Er kommt erst,
        // wenn alle anderen Skins offen sind (er selbst zählt nicht mit,
        // sonst wäre die Bedingung zirkulär).
        case .regenbogen:
            return DotSkin.allCases.allSatisfy { $0 == .regenbogen || $0.isUnlocked(stats) }
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

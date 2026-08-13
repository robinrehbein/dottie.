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
/// Freischaltungen hängen an dauerhaften Leistungen — Können (Rekord,
/// Perfekt-Serie, Daily-Serie) und Ausdauer (Läufe, Punkte, Tage, Monate).
/// Die drei Gönner-Skins sind die einzige Ausnahme: Sie sind gekauft, nicht
/// verdient, zählen deshalb nirgends mit und bleiben auf iOS mangels
/// Billing vorerst gesperrt.
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
    case ei = "EI"
    case tiger = "TIGER"
    case pinguin = "PINGUIN"
    case fussball = "FUSSBALL"
    case donut = "DONUT"

    // Bewegt
    case regenbogen = "REGENBOGEN"
    case aurora = "AURORA"
    case magma = "MAGMA"
    case neon = "NEON"
    case chrom = "CHROM"
    case welle = "WELLE"
    case gewitter = "GEWITTER"
    case konfetti = "KONFETTI"
    case disco = "DISCO"
    case holo = "HOLO"

    // Reagierend (Spielstand, Uhr, Kalender)
    case chamaeleon = "CHAMAELEON"
    case kombo = "KOMBO"
    case tinte = "TINTE"
    case thermo = "THERMO"
    case medaille = "MEDAILLE"
    case tageszeit = "TAGESZEIT"
    case jahreszeit = "JAHRESZEIT"

    // Saison — nur im eigenen Monat verdienbar, dann für immer
    case kuerbis = "KUERBIS"
    case zuckerstange = "ZUCKERSTANGE"
    case herz = "HERZ"
    case osterei = "OSTEREI"

    // Gönner — gekauft, nicht verdient
    case diamant = "DIAMANT"
    case phoenix = "PHOENIX"
    case onyx = "ONYX"

    /// Familien für die Gliederung des Skin-Menüs: 42 Zeilen am Stück
    /// wären eine Wand, und die Familien erklären nebenbei, warum ein Skin
    /// aussieht, wie er aussieht.
    enum Family: CaseIterable {
        case einfarbig
        case gemustert
        case bewegt
        case reagierend
        case saison
        case goenner

        /// Localizable.strings-Key der Überschrift.
        var titleKey: String {
            switch self {
            case .einfarbig: return "skin_family_solid"
            case .gemustert: return "skin_family_pattern"
            case .bewegt: return "skin_family_animated"
            case .reagierend: return "skin_family_reactive"
            case .saison: return "skin_family_season"
            case .goenner: return "skin_family_patron"
            }
        }
    }

    var family: Family {
        switch self {
        case .klassik, .minze, .lava, .gold, .frost, .schatten, .prisma:
            return .einfarbig
        case .biene, .melone, .pilz, .koi, .galaxie, .karo,
             .ei, .tiger, .pinguin, .fussball, .donut:
            return .gemustert
        case .regenbogen, .aurora, .magma, .neon, .chrom,
             .welle, .gewitter, .konfetti, .disco, .holo:
            return .bewegt
        case .chamaeleon, .kombo, .tinte, .thermo, .medaille, .tageszeit, .jahreszeit:
            return .reagierend
        case .kuerbis, .zuckerstange, .herz, .osterei:
            return .saison
        case .diamant, .phoenix, .onyx:
            return .goenner
        }
    }

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

    /// Braucht das Auge eine Kontur zum Körper hin? Siehe SkinPaint.
    var needsEyeOutline: Bool { SkinPaint.needsEyeOutline(self) }

    var isSeasonal: Bool { SkinPaint.isSeasonal(self) }
    var isPatron: Bool { SkinPaint.isPatron(self) }

    /// Zählt für den Sammlungsstand (und damit für den REGENBOGEN)?
    var countsForCollection: Bool { SkinPaint.countsForCollection(self) }

    /// Alles, woraus sich Freischaltungen speisen. Die ersten drei Werte
    /// sind Bestleistungen (Können), die nächsten vier Ausdauer (Menge) —
    /// die Trennung ist Absicht: Wer nie Rekord 60 sieht, sammelt trotzdem
    /// weiter.
    ///
    /// `seasonEarned` ist eine Bitmaske über `Season.bit`: Saison-Skins
    /// werden nur in ihrem Monat verdient, bleiben danach aber für immer.
    /// Die Maske ist deshalb der einzige Weg, sie zu prüfen — der Kalender
    /// allein würde sie im November wieder wegnehmen.
    ///
    /// `patronOwned` ist kein Verdienst, sondern ein Kauf.
    struct Stats {
        let bestScore: Int
        let bestPerfectStreak: Int
        let bestDailyStreak: Int
        let runCount: Int
        let totalScore: Int
        let daysPlayed: Int
        /// Anzahl verschiedener Kalendermonate mit mindestens einem Lauf.
        let monthsPlayed: Int
        let seasonEarned: Int
        let patronOwned: Bool

        // Eigener Initialisierer statt Standardwerten an den Feldern: Der
        // erzeugte Memberwise-Init würde `let`-Felder mit Vorgabe gar nicht
        // erst anbieten, und Vorschauen sollen die drei Bestleistungen
        // allein setzen können.
        init(
            bestScore: Int,
            bestPerfectStreak: Int,
            bestDailyStreak: Int,
            runCount: Int = 0,
            totalScore: Int = 0,
            daysPlayed: Int = 0,
            monthsPlayed: Int = 0,
            seasonEarned: Int = 0,
            patronOwned: Bool = false
        ) {
            self.bestScore = bestScore
            self.bestPerfectStreak = bestPerfectStreak
            self.bestDailyStreak = bestDailyStreak
            self.runCount = runCount
            self.totalScore = totalScore
            self.daysPlayed = daysPlayed
            self.monthsPlayed = monthsPlayed
            self.seasonEarned = seasonEarned
            self.patronOwned = patronOwned
        }
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

        // Ausdauer statt Können: Diese Achsen wachsen mit jedem Lauf, auch
        // mit den schlechten. Ohne sie hängen fast alle Skins am Rekord,
        // und wer bei 25 stehenbleibt, sammelt nie wieder etwas.
        case .ei: return stats.runCount >= 25
        case .tiger: return stats.runCount >= 100
        case .medaille: return stats.runCount >= 200
        case .fussball: return stats.runCount >= 300
        case .donut: return stats.totalScore >= 1_000
        case .konfetti: return stats.totalScore >= 5_000
        case .tageszeit: return stats.daysPlayed >= 7
        case .jahreszeit: return stats.monthsPlayed >= 3

        case .pinguin: return stats.bestScore >= 65
        case .welle: return stats.bestScore >= 70
        case .thermo: return stats.bestScore >= 75
        case .holo: return stats.bestScore >= 80
        case .gewitter: return stats.bestPerfectStreak >= 15
        case .disco: return stats.bestDailyStreak >= 21

        // Saison: im eigenen Monat verdient, danach für immer gehalten.
        // Geprüft wird deshalb die Maske, nie der Kalender — sonst wäre
        // der Kürbis im November wieder weg.
        case .kuerbis, .zuckerstange, .herz, .osterei:
            guard let season = Season.forSkin(self) else { return false }
            return stats.seasonEarned & season.bit != 0

        // Gönner: gekauft. Kein Verdienst, keine Feier, kein Zählwert.
        // Auf iOS gibt es (noch) kein Billing, `patronOwned` ist deshalb
        // immer false — die drei bleiben sichtbar, aber gesperrt.
        case .diamant, .phoenix, .onyx: return stats.patronOwned

        // Der Regenbogen ist der Abschluss der Sammlung: Er kommt erst,
        // wenn alle Skins offen sind, die für die Sammlung zählen (er
        // selbst zählt nicht mit, sonst wäre die Bedingung zirkulär —
        // Saison und Gönner zählen nicht mit, siehe countsForCollection).
        case .regenbogen:
            return DotSkin.allCases.allSatisfy {
                $0 == .regenbogen || !$0.countsForCollection || $0.isUnlocked(stats)
            }
        }
    }

    /// Skin zu einem gespeicherten Namen, KLASSIK als Fallback.
    static func fromName(_ name: String?) -> DotSkin {
        guard let name = name, let skin = DotSkin(rawValue: name) else {
            return .klassik
        }
        return skin
    }

    /// Wie viele Skins dauerhaft verdient sind. Gekaufte und Saison-Skins
    /// bleiben außen vor: Der Zähler ist eine Leistungsanzeige — und weil
    /// die Freischalt-Feier an ihm hängt, feiert ein Kauf auch nichts.
    static func unlockedCount(_ stats: Stats) -> Int {
        return allCases.filter { $0.countsForCollection && $0.isUnlocked(stats) }.count
    }

    /// Wie viele Skins dieser Zähler insgesamt erreichen kann.
    static func collectableCount() -> Int {
        return allCases.filter { $0.countsForCollection }.count
    }
}

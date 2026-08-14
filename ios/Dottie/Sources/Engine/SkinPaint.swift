import CoreGraphics
import Foundation

/// Port von core/.../SkinPaint.kt: das Farbwerk aller Punkt-Skins.
///
/// Ein Skin ist nicht "drei Farben", sondern eine Funktion über das
/// 13x13-Raster des Vogels: `cell` liefert die Farbe eines Feldes. Damit
/// sind gemusterte (Biene, Melone), animierte (Regenbogen, Magma) und auf
/// den Lauf reagierende Skins (Chamäleon, Kombo) möglich, ohne dass der
/// Renderer Sonderfälle kennen muss.
///
/// `body`, `shade` und `shine` bleiben als Stellvertreter-Farben erhalten:
/// Münzen und Score-Karte brauchen einen einzelnen Farbwert, wo kein
/// ganzer Vogel gezeichnet wird. Farben sind RGB-Werte ohne Alpha, wie im
/// Rest des iOS-Ports (UIColor(rgb:)).
enum SkinPaint {

    /// Kantenlänge des Vogel-Rasters (wie PixelArt.grid).
    static let grid = 13

    private static let mid = CGFloat(grid - 1) / 2
    private static let rr = CGFloat(grid) / 2 - 0.25

    /// Himmelsstufen des Bestands — Spiegel von ScenePaint.of(.wiese).sky.
    /// Der CHAMAELEON spiegelt die Stufe der WIESE, nicht die der
    /// gewählten Kulisse: Ein Skin ist die andere Sammlung und darf sich
    /// nicht davon abhängig machen, was gerade im Hintergrund liegt.
    static let skyStages: [UInt32] = [
        0x4EC0CA, 0x5B9BD5, 0x7B6FD0, 0xC0616F, 0xD98A3D, 0x3D4A8C, 0x2A2640
    ]

    /// Länge eines Himmels-Umlaufs in Stufen: sechs hoch von Tag bis Nacht,
    /// sechs zurück. Bei einer Stufe je fünf Punkte ist ein Umlauf also 60
    /// Punkte lang.
    static let skyCycle = 12

    /// Himmelsstufe zu einem Score. Der Zähler bleibt nicht in der Nacht
    /// stehen, sondern läuft weiter — hoch bis zur Nacht und wieder zurück
    /// zum Tag.
    static func skyStage(_ score: Int) -> Int {
        let step = ((score / 5) % skyCycle + skyCycle) % skyCycle
        return step <= skyCycle / 2 ? step : skyCycle - step
    }

    /// Nachbilder eines Schweif-Skins und ihr Winkelabstand (Radiant).
    static let trailSteps = 3
    static let trailSpacing: CGFloat = 0.10

    /// Der Lauf-Zustand, aus dem bewegte und reagierende Skins schöpfen.
    ///
    /// `hour` (0-23) und `month` (1-12) kommen von der Uhr des Geräts, nicht
    /// aus dem Lauf: TAGESZEIT und JAHRESZEIT ziehen daraus ihr Kleid. Die
    /// Standardwerte zeigen den Mittag im Juni — so sieht jede Vorschau, die
    /// keinen Kalender kennt, dasselbe Bild.
    struct State {
        var elapsed: CGFloat = 0
        var score: Int = 0
        var perfectStreak: Int = 0
        var hour: Int = 12
        var month: Int = 6

        static let still = State()

        /// Zustand mit der echten Geräte-Uhr — überall dort zu nehmen, wo
        /// ein Vogel gezeichnet wird (Lauf, Vorschau), damit TAGESZEIT und
        /// JAHRESZEIT nicht ewig Mittag im Juni zeigen.
        static func now(elapsed: CGFloat = 0, score: Int = 0, perfectStreak: Int = 0) -> State {
            let clock = SkinPaint.clock()
            return State(
                elapsed: elapsed,
                score: score,
                perfectStreak: perfectStreak,
                hour: clock.hour,
                month: clock.month
            )
        }
    }

    /// Stunde und Monat der Geräte-Uhr, für eine halbe Minute gemerkt: Die
    /// beiden Werte werden in jedem Frame gebraucht, ändern sich aber
    /// höchstens stündlich — `Calendar` 60-mal je Sekunde zu fragen wäre
    /// reine Verschwendung.
    private static var clockStamp: TimeInterval = -1
    private static var clockHour = 12
    private static var clockMonth = 6

    static func clock() -> (hour: Int, month: Int) {
        let date = Date()
        let stamp = date.timeIntervalSince1970
        if abs(stamp - clockStamp) > 30 {
            let parts = Calendar.current.dateComponents([.hour, .month], from: date)
            clockHour = parts.hour ?? 12
            clockMonth = parts.month ?? 6
            clockStamp = stamp
        }
        return (clockHour, clockMonth)
    }

    // MARK: - Farb-Werkzeug

    /// Kanalwert einer gerechneten Farbe auf ein Byte bringen.
    ///
    /// Kaufmännisch gerundet, nicht abgeschnitten: Abschneiden zieht jeden
    /// Kanal systematisch nach unten (im Mittel eine halbe Stufe), Runden
    /// halbiert den Fehler und verteilt ihn symmetrisch. `SkinPaint.kt` in
    /// :core und der Web-Port rechnen genauso — sonst zeigten App und PWA
    /// dieselbe Farbe eine Stufe versetzt.
    private static func byteOf(_ v: CGFloat) -> UInt32 {
        return UInt32(min(max(v.rounded(), 0), 255))
    }

    static func mix(_ a: UInt32, _ b: UInt32, _ k: CGFloat) -> UInt32 {
        let f = min(max(k, 0), 1)
        var out: UInt32 = 0
        for shift in [16, 8, 0] {
            let ca = CGFloat((a >> UInt32(shift)) & 0xFF)
            let cb = CGFloat((b >> UInt32(shift)) & 0xFF)
            out |= byteOf(ca + (cb - ca) * f) << UInt32(shift)
        }
        return out
    }

    /// HSL nach RGB. `h` in Grad, `s` und `l` von 0 bis 1.
    static func hsl(_ h: CGFloat, _ s: CGFloat, _ l: CGFloat) -> UInt32 {
        let wrapped = h.truncatingRemainder(dividingBy: 360)
        let hue = wrapped < 0 ? wrapped + 360 : wrapped
        let c = (1 - abs(2 * l - 1)) * s
        let x = c * (1 - abs((hue / 60).truncatingRemainder(dividingBy: 2) - 1))
        let m = l - c / 2
        let rgb: (CGFloat, CGFloat, CGFloat)
        switch hue {
        case ..<60: rgb = (c, x, 0)
        case ..<120: rgb = (x, c, 0)
        case ..<180: rgb = (0, c, x)
        case ..<240: rgb = (0, x, c)
        case ..<300: rgb = (x, 0, c)
        default: rgb = (c, 0, x)
        }
        func byte(_ v: CGFloat) -> UInt32 { byteOf((v + m) * 255) }
        return (byte(rgb.0) << 16) | (byte(rgb.1) << 8) | byte(rgb.2)
    }

    /// Die Standard-Schattierung des Spiels: untere rechte Hälfte dunkler.
    private static func shaded(_ col: Int, _ row: Int, _ body: UInt32, _ shade: UInt32) -> UInt32 {
        return CGFloat(col + row) > CGFloat(grid) * 1.15 ? shade : body
    }

    private static func at(_ list: [(Int, Int)], _ col: Int, _ row: Int) -> Bool {
        return list.contains { $0.0 == col && $0.1 == row }
    }

    // MARK: - Muster-Details

    private static let melonSeeds = [(4, 3), (7, 5), (3, 6), (8, 2), (6, 7)]
    private static let mushroomDots = [(3, 2), (8, 1), (5, 4), (9, 5), (2, 6), (6, 6)]
    private static let koiRed = [(2, 4), (3, 4), (3, 5), (2, 5), (4, 5), (3, 3)]
    private static let koiOrange = [(8, 7), (9, 7), (8, 8), (7, 8), (9, 6), (7, 7)]
    private static let galaxyStars = [(3, 3), (9, 4), (5, 8), (10, 8), (2, 7)]
    private static let galaxyNebula = [(7, 2), (4, 6), (8, 9)]

    /// Fünfeck in der Mitte plus angeschnittene Flecken am Rand.
    private static let ballPatches = [
        (6, 5), (5, 6), (6, 6), (7, 6), (5, 7), (6, 7), (7, 7), (6, 8),
        (1, 4), (2, 4), (2, 3), (10, 9), (9, 10), (3, 11)
    ]
    private static let sprinkles = [
        (3, 2), (5, 1), (8, 2), (4, 4), (9, 4), (6, 3), (10, 5), (2, 4)
    ]
    /// Zickzack des Blitzes — läuft von oben rechts nach unten links.
    private static let boltCells = [
        (7, 2), (6, 3), (6, 4), (7, 4), (5, 5), (5, 6), (6, 6),
        (4, 7), (4, 8), (5, 8), (3, 9)
    ]

    /// Heller Bauch des PINGUIN — als Ellipse, damit er zur Kugel passt.
    private static func isBelly(_ col: Int, _ row: Int) -> Bool {
        let dx = (CGFloat(col) - 6) * 0.9
        let dy = CGFloat(row) - 8.2
        return sqrt(dx * dx + dy * dy) < 3.4
    }

    /// Geschnitztes Grinsen des KUERBIS, bewusst unterhalb des Auges.
    private static func isGrin(_ col: Int, _ row: Int) -> Bool {
        if row == 10 { return col >= 3 && col <= 9 }
        if row == 9 { return col == 3 || col == 6 || col == 9 }
        return false
    }

    /// Pixelherz, tief gesetzt — oben hat das Auge Vorrang.
    private static func isHeart(_ col: Int, _ row: Int) -> Bool {
        switch row {
        case 6: return col == 4 || col == 5 || col == 7 || col == 8
        case 7, 8: return col >= 3 && col <= 9
        case 9: return col >= 4 && col <= 8
        case 10: return col >= 5 && col <= 7
        case 11: return col == 6
        default: return false
        }
    }

    private static let sprinkleColors: [UInt32] = [0x4EC0CA, 0xFFF3B8, 0xFFFFFF, 0xFF5A36]
    private static let confettiColors: [UInt32] = [0xFF5A36, 0x4EC0CA, 0xFFD847, 0xFF6FD8, 0x7B6FD0]
    private static let discoColors: [UInt32] = [0xFF6FD8, 0x4EC0CA, 0xFFD847]
    private static let diamondColors: [UInt32] = [0xDCEBFF, 0xA8C8EE, 0x7FA8D8]

    /// Bänder des OSTEREI: Körper- und Schattenfarbe je Band.
    private static let easterColors: [[UInt32]] = [
        [0xFFB8D9, 0xE086B4],
        [0xBFE9FF, 0x8FC8E8],
        [0xFFF0A8, 0xE0CE6A],
        [0xD9C2FF, 0xB096E8]
    ]

    /// Bei welchem Score THERMO fertig durchgeglüht ist — bewusst die
    /// Platin-Schwelle: weißglühend genau dann, wenn der Lauf die höchste
    /// Medaille erreicht hat.
    static let heatScore = 40

    /// Legierungen von MEDAILLE: Zinn, Bronze, Silber, Gold, Platin.
    private static let medalColors: [[UInt32]] = [
        [0xB8BEC9, 0x8A909C],
        [0xCD7F32, 0x9C5A1E],
        [0xC0C0C0, 0x8F8F9C],
        [0xFFD700, 0xC9A400],
        [0xE5E4E2, 0xADB5C4]
    ]

    /// Medaillenstufe eines Scores (0 = noch keine) — Spiegel von MedalTier.
    static func medalTier(_ score: Int) -> Int {
        if score >= 40 { return 4 }
        if score >= 30 { return 3 }
        if score >= 20 { return 2 }
        if score >= 10 { return 1 }
        return 0
    }

    /// Kleid von TAGESZEIT nach Stunde: Morgenrot, Mittagsblau, Abendglut,
    /// Nachtblau mit Sternen. Nur die Nacht hat einen dritten Wert.
    private static func dayPalette(_ hour: Int) -> [UInt32] {
        switch hour {
        case 5...8: return [0xFFC58F, 0xE8935A]
        case 9...16: return [0x8FD8FF, 0x4FA3D8]
        case 17...20: return [0xFF8A3C, 0xC0616F]
        default: return [0x3D4A8C, 0x232B55, 0xFFF3B8]
        }
    }

    /// Kleid von JAHRESZEIT nach Kalendermonat (1-12): Körper, Schatten,
    /// Streufarbe und der Rest, bei dem die Streufarbe erscheint.
    private static func seasonPalette(_ month: Int) -> [UInt32] {
        switch month {
        case 3, 4, 5: return [0xFFB8D9, 0xE086B4, 0xFFFFFF, 5]
        case 6, 7, 8: return [0xFFC93C, 0xE09218, 0xFFF6C0, 7]
        case 9, 10, 11: return [0xC2551E, 0x8E3A14, 0xFFB84E, 4]
        default: return [0xDCF3FF, 0xA8C8DE, 0xFFFFFF, 6]
        }
    }

    /// Deterministisches Rauschen über Feld und Zeitschritt. Bewusst kein
    /// Zufall: Zwei Geräte, zwei Renderer und der Textur-Cache müssen beim
    /// selben Zeitschritt dasselbe Bild ergeben.
    ///
    /// Kotlins `Int` ist 32 Bit und läuft still über, Swift bricht bei
    /// Überlauf ab — deshalb `Int32` und `&*` statt `*` sowie ein
    /// logisches Rechtsschieben über `UInt32` (Kotlins `ushr`). Das
    /// Ergebnis wird auf 64 Bit geweitet, bevor `abs` greift: Nur so kann
    /// der Betrag von `Int32.min` nicht negativ bleiben (Kotlin liefert
    /// dort wieder `Int32.min` und stürzt beim Indizieren ab).
    private static func noise(_ col: Int, _ row: Int, _ seed: Int) -> Int {
        var n = (Int32(truncatingIfNeeded: col) &* 73856093)
            ^ (Int32(truncatingIfNeeded: row) &* 19349663)
            ^ (Int32(truncatingIfNeeded: seed) &* 83492791)
        n = (n ^ Int32(bitPattern: UInt32(bitPattern: n) >> 13)) &* 1274126177
        n = n ^ Int32(bitPattern: UInt32(bitPattern: n) >> 16)
        return abs(Int(n))
    }

    /// Index in eine Farbtabelle, immer im gültigen Bereich. Kotlin rechnet
    /// hier roh mit `%`; negativ werden die Werte nur bei negativer Zeit,
    /// die es im Lauf nicht gibt — der Umweg kostet nichts und spart einen
    /// Absturz, falls doch.
    private static func wrap(_ value: Int, _ count: Int) -> Int {
        return ((value % count) + count) % count
    }

    /// Leuchtfarbe von NEON: springt im Vierteltakt weiter.
    private static func neonGlow(_ state: State) -> UInt32 {
        let cols: [UInt32] = [0xFF3DCB, 0x3DF5E0, 0xC3FF3D]
        let step = Int(floor(state.elapsed * 2.5))
        return cols[((step % cols.count) + cols.count) % cols.count]
    }

    // MARK: - Stellvertreter-Farben

    static func body(_ id: DotSkin) -> UInt32 {
        switch id {
        case .klassik: return 0xFFD847
        case .minze: return 0x4BE38C
        case .lava: return 0xFF5A36
        case .gold: return 0xFFC400
        case .frost: return 0x8FD8FF
        case .schatten: return 0x6B4F8A
        case .prisma: return 0xFF6FD8
        case .biene: return 0xFFD847
        case .melone: return 0xF0555C
        case .pilz: return 0xE8452F
        case .koi: return 0xF7F3EE
        case .galaxie: return 0x4E3C86
        case .karo: return 0x4EC0CA
        case .regenbogen: return 0xFF6FD8
        case .aurora: return 0x3FE0A8
        case .magma: return 0x3A2431
        case .neon: return 0x241E33
        case .chrom: return 0xE6EAF2
        case .chamaeleon: return 0x8FD8DE
        case .kombo: return 0xFFD847
        case .tinte: return 0x2A46A8
        case .ei: return 0xFFE58F
        case .tiger: return 0xFF8A2B
        case .pinguin: return 0x2E3440
        case .fussball: return 0xF7F3EE
        case .donut: return 0xFF7FBF
        case .welle: return 0x2E86D8
        case .gewitter: return 0x4A5568
        case .konfetti: return 0xF7F3EE
        case .disco: return 0xC3CBD9
        case .holo: return 0x7FD8E8
        case .thermo: return 0xFFD847
        case .medaille: return 0xC0C0C0
        case .tageszeit: return 0x8FD8FF
        case .jahreszeit: return 0xFFC93C
        case .kuerbis: return 0xF5821F
        case .zuckerstange: return 0xE8452F
        case .herz: return 0xFF6FA8
        case .osterei: return 0xFFB8D9
        case .diamant: return 0xA8C8EE
        case .phoenix: return 0xFF8A2B
        case .onyx: return 0x221C29
        }
    }

    static func shade(_ id: DotSkin) -> UInt32 {
        switch id {
        case .klassik: return 0xF5A623
        case .minze: return 0x2BA55E
        case .lava: return 0xC22F12
        case .gold: return 0xCC8F00
        case .frost: return 0x4FA3D8
        case .schatten: return 0x43315C
        case .prisma: return 0xC93BAA
        case .biene: return 0x3A2C33
        case .melone: return 0x74BF2E
        case .pilz: return 0xC2301F
        case .koi: return 0xE8452F
        case .galaxie: return 0x231A3F
        case .karo: return 0x2E8E98
        case .regenbogen: return 0x7A3BC9
        case .aurora: return 0x2A7F8E
        case .magma: return 0xC22F12
        case .neon: return 0x181328
        case .chrom: return 0x5B6478
        case .chamaeleon: return 0x3F9BA5
        case .kombo: return 0xE0A400
        case .tinte: return 0x1F3A8A
        case .ei: return 0xE8B92E
        case .tiger: return 0x2A1F1C
        case .pinguin: return 0x1B1F28
        case .fussball: return 0x2A2C33
        case .donut: return 0xC08A47
        case .welle: return 0x1F5FA8
        case .gewitter: return 0x2F3644
        case .konfetti: return 0xFF5A36
        case .disco: return 0x8892A6
        case .holo: return 0xC93BAA
        case .thermo: return 0xE0A400
        case .medaille: return 0x8F8F9C
        case .tageszeit: return 0x3D4A8C
        case .jahreszeit: return 0xE09218
        case .kuerbis: return 0xC25E10
        case .zuckerstange: return 0xC2301F
        case .herz: return 0xD6407E
        case .osterei: return 0xB096E8
        case .diamant: return 0x4E6A96
        case .phoenix: return 0x8E2410
        case .onyx: return 0x141018
        }
    }

    /// Glanzpunkt — bei NEON wandert er mit der Leuchtfarbe mit.
    static func shine(_ id: DotSkin, _ state: State = .still) -> UInt32 {
        switch id {
        case .klassik: return 0xFFF3B8
        case .minze: return 0xC8FFE0
        case .lava: return 0xFFC9A3
        case .gold: return 0xFFF7CC
        case .frost: return 0xE8F9FF
        case .schatten: return 0xCBB8E8
        case .prisma: return 0xB8F3FF
        case .biene: return 0xFFF3B8
        case .melone: return 0xFFD3D6
        case .pilz: return 0xFFD9C9
        case .koi: return 0xFFFFFF
        case .galaxie: return 0xFFF3B8
        case .karo: return 0xFFFFFF
        case .regenbogen: return 0xFFFFFF
        case .aurora: return 0xE8F9FF
        case .magma: return 0xFFD847
        case .neon: return neonGlow(state)
        case .chrom: return 0xFFFFFF
        case .chamaeleon: return 0xFFFFFF
        case .kombo: return 0xFFF3B8
        case .tinte: return 0xA8C0FF
        case .ei: return 0xFFFFFF
        case .tiger: return 0xFFE0B8
        case .pinguin: return 0xFFFFFF
        case .fussball: return 0xFFFFFF
        case .donut: return 0xFFFFFF
        case .welle: return 0xFFFFFF
        case .gewitter: return 0xFFF3B8
        case .konfetti: return 0xFFFFFF
        case .disco: return 0xFFFFFF
        case .holo: return 0xFFFFFF
        case .thermo: return 0xFFFFFF
        case .medaille: return 0xFFFFFF
        case .tageszeit: return 0xFFFFFF
        case .jahreszeit: return 0xFFFFFF
        case .kuerbis: return 0xFFE0B8
        case .zuckerstange: return 0xFFFFFF
        case .herz: return 0xFFFFFF
        case .osterei: return 0xFFFFFF
        case .diamant: return 0xFFFFFF
        case .phoenix: return 0xFFF3B8
        case .onyx: return 0xFFE07A
        }
    }

    /// Hinterlässt der Skin Nachbilder auf der Bahn?
    static func hasTrail(_ id: DotSkin) -> Bool { id == .tinte || id == .phoenix }

    /// Saison-Skin? Verdienbar nur im eigenen Monat (siehe Season).
    static func isSeasonal(_ id: DotSkin) -> Bool { Season.forSkin(id) != nil }

    /// Gekaufter Gönner-Skin?
    static func isPatron(_ id: DotSkin) -> Bool {
        switch id {
        case .diamant, .phoenix, .onyx: return true
        default: return false
        }
    }

    /// Zählt dieser Skin für den Sammlungsstand — und damit für die
    /// Bedingung des REGENBOGEN?
    ///
    /// Saison-Skins nicht, sonst wäre der Regenbogen frühestens nach einem
    /// Jahr erreichbar. Gönner-Skins nicht, sonst wäre er käuflich.
    static func countsForCollection(_ id: DotSkin) -> Bool {
        return !isSeasonal(id) && !isPatron(id)
    }

    /// Felder, an die das Auge grenzt — in beiden Blickrichtungen, damit
    /// die Entscheidung nicht beim Richtungswechsel kippt.
    private static let eyeNeighbours = [
        (7, 3), (7, 4), (7, 5), (7, 6), (8, 2), (9, 2), (10, 2), (8, 7), (9, 7), (10, 7),
        (5, 3), (5, 4), (5, 5), (5, 6), (4, 2), (3, 2), (2, 2), (4, 7), (3, 7), (2, 7)
    ]

    /// Ab welchem Abstand zu Weiß (0 bis 441 im RGB-Raum) ein Körper als
    /// "zu hell fürs Auge" gilt.
    private static let eyeOutlineDistance: CGFloat = 60

    /// Braucht das Auge dieses Skins eine Kontur zum Körper hin? Auf sehr
    /// hellen Körpern (Koi, Chrom) verschwände das weiße Auge sonst und
    /// nur die Pupille bliebe stehen; auf allen anderen wirkt die Kontur
    /// wie ein Kasten ums Auge. Gemessen im Ruhezustand, damit sie bei
    /// bewegten Skins nicht mitten im Lauf an- und ausgeht.
    static func needsEyeOutline(_ id: DotSkin) -> Bool {
        return eyeNeighbours.contains { col, row in
            distanceToWhite(cell(id, col, row)) < eyeOutlineDistance
        }
    }

    /// Abstand einer RGB-Farbe zu Weiß.
    private static func distanceToWhite(_ color: UInt32) -> CGFloat {
        let r = 255 - CGFloat((color >> 16) & 0xFF)
        let g = 255 - CGFloat((color >> 8) & 0xFF)
        let b = 255 - CGFloat(color & 0xFF)
        return sqrt(r * r + g * g + b * b)
    }

    /// Hängt die Farbe an der Uhr (im Gegensatz zu Muster und Spielstand)?
    static func isAnimated(_ id: DotSkin) -> Bool {
        switch id {
        case .regenbogen, .aurora, .magma, .neon, .chrom,
             .welle, .gewitter, .konfetti, .disco, .holo,
             .zuckerstange, .diamant, .phoenix, .onyx: return true
        default: return false
        }
    }

    /// Bewegte Skins müssen nicht in jedem Frame neu gerastert werden — ein
    /// Zwölftel einer Sekunde ist fein genug für den Pixel-Look. Der
    /// Textur-Cache in GameScene schlüsselt darüber; reagierende Skins
    /// liefern hier den Wert, an dem ihr Bild hängt (Score, Stufe, Uhr),
    /// und werden dadurch nur bei echtem Wechsel neu gerastert.
    static func frameKey(_ id: DotSkin, _ state: State) -> Int {
        if isAnimated(id) { return Int(state.elapsed * 12) }
        switch id {
        case .chamaeleon: return skyStage(state.score)
        case .kombo: return min(state.perfectStreak, 5)
        case .thermo: return min(state.score, heatScore)
        case .medaille: return medalTier(state.score)
        case .tageszeit: return state.hour
        case .jahreszeit: return state.month
        default: return 0
        }
    }

    // MARK: - Das Farbwerk

    /// Farbe eines Rasterfelds. Kreismaske und Kontur bleiben Sache des
    /// Renderers — hier kommt immer die Füllfarbe zurück.
    static func cell(_ id: DotSkin, _ col: Int, _ row: Int, _ state: State = .still) -> UInt32 {
        let t = state.elapsed
        let sum = CGFloat(col + row)
        let dark = sum > CGFloat(grid) * 1.15

        switch id {
        case .klassik, .minze, .lava, .gold, .frost, .schatten, .prisma, .tinte:
            return shaded(col, row, body(id), shade(id))

        case .biene:
            if ((col - row) % 6 + 6) % 6 < 2 { return 0x3A2C33 }
            return shaded(col, row, 0xFFD847, 0xE0A400)

        case .melone:
            if row >= 10 { return dark ? 0x5AA020 : 0x74BF2E }
            if row == 9 { return 0xDFF2C6 }
            if at(melonSeeds, col, row) { return 0x3A2C33 }
            return shaded(col, row, 0xF0555C, 0xC93B48)

        case .pilz:
            if row >= 9 { return shaded(col, row, 0xF7F3EE, 0xD9CEC2) }
            if at(mushroomDots, col, row) { return 0xF7F3EE }
            return shaded(col, row, 0xE8452F, 0xC2301F)

        case .koi:
            if at(koiRed, col, row) { return 0xE8452F }
            if at(koiOrange, col, row) { return 0xF59A2E }
            return shaded(col, row, 0xF7F3EE, 0xD9CEC2)

        case .galaxie:
            if at(galaxyStars, col, row) { return 0xFFF3B8 }
            if at(galaxyNebula, col, row) { return 0x7FDCE4 }
            return mix(0x4E3C86, 0x231A3F, sum / CGFloat(grid * 2))

        case .karo:
            if (col / 2 + row / 2) % 2 == 0 { return dark ? 0x2E8E98 : 0x4EC0CA }
            return shaded(col, row, 0xF7F3EE, 0xD9CEC2)

        case .regenbogen:
            // Der Grünbereich wird übersprungen: Ein grüner Vogel sähe für
            // einen Moment aus wie die Zielzone.
            var h = (t * 45).truncatingRemainder(dividingBy: 300)
            if h > 80 { h += 60 }
            return dark ? hsl(h, 0.70, 0.44) : hsl(h, 0.85, 0.62)

        case .aurora:
            let wave = sin(sum * 0.42 - t * 1.6)
            let h = 168 + wave * 90
            return dark ? hsl(h, 0.55, 0.40) : hsl(h, 0.72, 0.60)

        case .magma:
            let vein = sin(CGFloat(col) * 1.3 + CGFloat(row) * 0.7) > 0.35
            if !vein { return dark ? 0x241722 : 0x3A2431 }
            let heat = 0.5 + 0.5 * sin(t * 3.4 + CGFloat(col) * 0.8 + CGFloat(row) * 0.5)
            return mix(0x8E2410, 0xFFD847, heat)

        case .neon:
            let dx = CGFloat(col) - mid
            let dy = CGFloat(row) - mid
            if sqrt(dx * dx + dy * dy) > rr - 2.2 { return neonGlow(state) }
            return dark ? 0x181328 : 0x241E33

        case .chrom:
            let band = 0.5 + 0.5 * sin(CGFloat(col) * 1.1)
            var base = mix(0x5B6478, 0xE6EAF2, band)
            let sweep = (t * 6).truncatingRemainder(dividingBy: 18) - 3
            let d = abs(CGFloat(col) + CGFloat(row) * 0.4 - sweep)
            if d < 1.6 { base = mix(base, 0xFFFFFF, 1 - d / 1.6) }
            return dark ? mix(base, 0x3B4152, 0.35) : base

        case .chamaeleon:
            let sky = skyStages[skyStage(state.score)]
            return dark ? mix(sky, 0x000000, 0.18) : mix(sky, 0xFFFFFF, 0.34)

        case .kombo:
            let k = CGFloat(min(state.perfectStreak, 5)) / 5
            return shaded(col, row, mix(0x8C8790, 0xFFD847, k), mix(0x5F5B63, 0xE0A400, k))

        // MARK: Gemustert

        case .ei:
            // Gezackte Schalenkante: Die Kappe endet je Spalte etwas
            // anders, sonst läge ein gerader Deckel auf dem Küken.
            let jag: CGFloat = 3.5 + (col % 3 == 0 ? 1.0 : 0.0) + (col % 2 == 0 ? 0.5 : 0.0)
            if CGFloat(row) <= jag { return shaded(col, row, 0xF7F3EE, 0xDCD2C4) }
            return shaded(col, row, 0xFFE58F, 0xE8B92E)

        case .tiger:
            let wave = CGFloat(col) + sin(CGFloat(row) * 0.55) * 2.2
            let stripe = (wave.truncatingRemainder(dividingBy: 6) + 6)
                .truncatingRemainder(dividingBy: 6)
            if stripe < 1.7 { return 0x2A1F1C }
            return shaded(col, row, 0xFF8A2B, 0xD2601A)

        case .pinguin:
            if row >= 11 { return 0xF5A623 }
            if isBelly(col, row) { return shaded(col, row, 0xF7F3EE, 0xDCD2C4) }
            return shaded(col, row, 0x2E3440, 0x1B1F28)

        case .fussball:
            if at(ballPatches, col, row) { return 0x2A2C33 }
            return shaded(col, row, 0xF7F3EE, 0xD9CEC2)

        case .donut:
            let edge = 5.5 + sin(CGFloat(col) * 1.05) * 1.3
            if CGFloat(row) > edge { return shaded(col, row, 0xE8B36A, 0xC08A47) }
            if at(sprinkles, col, row) {
                return sprinkleColors[wrap(col + row, sprinkleColors.count)]
            }
            return shaded(col, row, 0xFF7FBF, 0xE04E9C)

        // MARK: Bewegt

        case .welle:
            // Eine Wasserlinie, die im Körper schwappt — darüber Luft, an
            // der Kante Schaum.
            let line = 5.6 + sin(t * 1.7 + CGFloat(col) * 0.52) * 1.5
            if CGFloat(row) > line + 0.9 { return shaded(col, row, 0x2E86D8, 0x1F5FA8) }
            if CGFloat(row) > line { return 0xBFE9FF }
            return shaded(col, row, 0xDCF3FF, 0xBBD9E8)

        case .gewitter:
            // Der Blitz ist kurz und selten: Er trägt den Skin, aber ein
            // Dauerflackern würde den Punkt unlesbar machen.
            let phase = t.truncatingRemainder(dividingBy: 2.6)
            let flash: CGFloat = phase < 0.14 ? 1 : (phase < 0.30 ? 0.35 : 0)
            let base = shaded(col, row, 0x4A5568, 0x2F3644)
            if flash > 0 && at(boltCells, col, row) { return 0xFFF3B8 }
            if flash > 0 { return mix(base, 0xFFE95E, 0.5 * flash) }
            return base

        case .konfetti:
            let n = noise(col, row, Int(floor(t * 0.9)))
            if n % 100 < 38 { return confettiColors[wrap(n, confettiColors.count)] }
            return shaded(col, row, 0xF7F3EE, 0xD9CEC2)

        case .disco:
            let facet = (col / 2 + row / 2) % 2
            let base: UInt32 = facet == 0 ? 0xC3CBD9 : 0x8892A6
            let k = Int(floor(t * 7))
            if (col * 2 + row * 3 + k) % 11 == 0 { return 0xFFFFFF }
            if (col + row * 2 + k) % 13 == 0 {
                return discoColors[wrap(col + row + k, discoColors.count)]
            }
            return dark ? mix(base, 0x3B4152, 0.3) : base

        case .holo:
            // Sammelkarten-Folie. Der Grünbereich wird übersprungen wie
            // beim REGENBOGEN — ein grüner Vogel sähe für einen Moment aus
            // wie die Zielzone.
            var h = (CGFloat(col - row) * 13 + t * 60).truncatingRemainder(dividingBy: 360)
            if h < 0 { h += 360 }
            if h > 80 && h < 150 { h += 70 }
            var color = hsl(h, 0.75, dark ? 0.46 : 0.66)
            let sweep = (t * 5).truncatingRemainder(dividingBy: 20) - 4
            let d = abs(CGFloat(col) + CGFloat(row) * 0.6 - sweep)
            if d < 1.4 { color = mix(color, 0xFFFFFF, 1 - d / 1.4) }
            return color

        // MARK: Reagierend

        case .thermo:
            // Der Vogel heizt sich im Lauf auf: kalt bei 0, weißglühend bei
            // heatScore. Fortschrittsanzeige an der Stelle, auf die der
            // Daumen ohnehin schaut.
            let k = CGFloat(min(state.score, heatScore)) / CGFloat(heatScore)
            let hot = k < 0.5
                ? mix(0x8FD8FF, 0xFFD847, k * 2)
                : mix(0xFFD847, 0xFFF6E0, (k - 0.5) * 2)
            let hotShade = k < 0.5
                ? mix(0x4FA3D8, 0xE0A400, k * 2)
                : mix(0xE0A400, 0xFF7A3C, (k - 0.5) * 2)
            return shaded(col, row, hot, hotShade)

        case .medaille:
            let tier = medalColors[medalTier(state.score)]
            let dx = CGFloat(col) - mid
            let dy = CGFloat(row) - mid
            // Prägerand: außen dunkler, damit die Münze eine Kante hat.
            if sqrt(dx * dx + dy * dy) > rr - 1.85 { return mix(tier[1], 0x000000, 0.18) }
            return shaded(col, row, tier[0], tier[1])

        case .tageszeit:
            let p = dayPalette(state.hour)
            if p.count > 2 && at(galaxyStars, col, row) { return p[2] }
            return shaded(col, row, p[0], p[1])

        case .jahreszeit:
            let p = seasonPalette(state.month)
            if (col * 3 + row * 5) % 11 == Int(p[3]) { return p[2] }
            return shaded(col, row, p[0], p[1])

        // MARK: Saison

        case .kuerbis:
            if row <= 1 && col >= 5 && col <= 7 { return 0x5AA020 }
            if isGrin(col, row) { return 0x2A1F1C }
            let rib = abs(((col + 1) % 4 + 4) % 4 - 2) < 1
            let pumpkin: UInt32 = rib ? 0xD86A12 : 0xF5821F
            return dark ? mix(pumpkin, 0x000000, 0.22) : pumpkin

        case .zuckerstange:
            let band = Int(floor((CGFloat(col + row) - t * 4) / 2.2))
            if ((band % 2) + 2) % 2 == 0 { return shaded(col, row, 0xE8452F, 0xC2301F) }
            return shaded(col, row, 0xF7F3EE, 0xDCD2C4)

        case .herz:
            // Das Herz sitzt tief: Weiter oben verdeckte es das Auge, und
            // zwei Zeichen im selben Gesicht kämpfen gegeneinander.
            if isHeart(col, row) { return shaded(col, row, 0xFFF0F5, 0xFFC8DC) }
            return shaded(col, row, 0xFF6FA8, 0xD6407E)

        case .osterei:
            let band = (row + (col % 2 == 0 ? 1 : 0)) / 2 % 4
            if band == 1 && col % 3 == 0 { return 0xFFFFFF }
            return shaded(col, row, easterColors[band][0], easterColors[band][1])

        // MARK: Gönner

        case .diamant:
            let facet = ((Int(floor(CGFloat(col) * 0.9 + CGFloat(row) * 0.4)) % 3) + 3) % 3
            var base = diamondColors[facet]
            let sweep = (t * 7).truncatingRemainder(dividingBy: 20) - 4
            let d = abs(CGFloat(col) + CGFloat(row) * 0.5 - sweep)
            if d < 1.2 { base = mix(base, 0xFFFFFF, 1 - d / 1.2) }
            if noise(col, row, Int(floor(t * 3))) % 37 == 0 { return 0xFFFFFF }
            return dark ? mix(base, 0x4E6A96, 0.35) : base

        case .phoenix:
            let flicker = 0.5 + 0.5 * sin(t * 4 + CGFloat(col) * 0.7 - CGFloat(row) * 1.1)
            let heat = max(0, 1 - CGFloat(row) / 11) * 0.6 + flicker * 0.5
            let color: UInt32 = heat > 0.9 ? 0xFFF3B8 : mix(0xE5341A, 0xFFB020, min(1, heat))
            return dark ? mix(color, 0x8E2410, 0.35) : color

        case .onyx:
            let vein = sin(CGFloat(col) * 1.15 + CGFloat(row) * 0.85) > 0.55
            if !vein { return dark ? 0x141018 : 0x221C29 }
            let glow = 0.5 + 0.5 * sin(t * 1.6 + CGFloat(col) * 0.5 + CGFloat(row) * 0.4)
            return mix(0x8A6A1E, 0xFFE07A, glow)
        }
    }
}

/// Port von Season aus :core — die vier Saison-Skins und ihr Fenster.
/// `requiredDays` zählt Tage mit mindestens einem Lauf im Monat: bewusst
/// kein Rekord und keine Serie, ein Saison-Skin soll an Anwesenheit hängen.
///
/// Verpasst ist nicht verloren: Das Fenster kommt jedes Jahr wieder.
enum Season: CaseIterable {
    case kuerbis
    case zuckerstange
    case herz
    case osterei

    var skin: DotSkin {
        switch self {
        case .kuerbis: return .kuerbis
        case .zuckerstange: return .zuckerstange
        case .herz: return .herz
        case .osterei: return .osterei
        }
    }

    /// Kalendermonat 1-12, in dem dieser Skin verdient werden kann.
    var month: Int {
        switch self {
        case .kuerbis: return 10
        case .zuckerstange: return 12
        case .herz: return 2
        case .osterei: return 4
        }
    }

    var requiredDays: Int {
        switch self {
        case .kuerbis: return 5
        case .zuckerstange: return 5
        case .herz: return 3
        case .osterei: return 5
        }
    }

    /// Bit dieses Skins in DotSkin.Stats.seasonEarned — die Reihenfolge
    /// entspricht der Ordinalzahl in :core und darf sich nie verschieben,
    /// sonst zeigt eine gespeicherte Maske auf den falschen Skin.
    var bit: Int {
        switch self {
        case .kuerbis: return 1 << 0
        case .zuckerstange: return 1 << 1
        case .herz: return 1 << 2
        case .osterei: return 1 << 3
        }
    }

    /// Der Skin, der in diesem Monat verdient werden kann — sonst nil.
    static func forMonth(_ month: Int) -> Season? {
        return allCases.first { $0.month == month }
    }

    static func forSkin(_ id: DotSkin) -> Season? {
        return allCases.first { $0.skin == id }
    }
}

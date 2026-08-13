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

    /// Himmelsstufen für CHAMAELEON — Spiegel von Palette.skyStages.
    static let skyStages: [UInt32] = [
        0x4EC0CA, 0x5B9BD5, 0x7B6FD0, 0xC0616F, 0xD98A3D, 0x3D4A8C, 0x2A2640
    ]

    /// Nachbilder eines Schweif-Skins und ihr Winkelabstand (Radiant).
    static let trailSteps = 3
    static let trailSpacing: CGFloat = 0.10

    /// Der Lauf-Zustand, aus dem bewegte und reagierende Skins schöpfen.
    struct State {
        var elapsed: CGFloat = 0
        var score: Int = 0
        var perfectStreak: Int = 0

        static let still = State()
    }

    // MARK: - Farb-Werkzeug

    static func mix(_ a: UInt32, _ b: UInt32, _ k: CGFloat) -> UInt32 {
        let f = min(max(k, 0), 1)
        var out: UInt32 = 0
        for shift in [16, 8, 0] {
            let ca = CGFloat((a >> UInt32(shift)) & 0xFF)
            let cb = CGFloat((b >> UInt32(shift)) & 0xFF)
            let v = UInt32(min(max(ca + (cb - ca) * f, 0), 255))
            out |= v << UInt32(shift)
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
        func byte(_ v: CGFloat) -> UInt32 { UInt32(min(max((v + m) * 255, 0), 255)) }
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
        }
    }

    /// Hinterlässt der Skin Nachbilder auf der Bahn?
    static func hasTrail(_ id: DotSkin) -> Bool { id == .tinte }

    /// Hängt die Farbe an der Uhr (im Gegensatz zu Muster und Spielstand)?
    static func isAnimated(_ id: DotSkin) -> Bool {
        switch id {
        case .regenbogen, .aurora, .magma, .neon, .chrom: return true
        default: return false
        }
    }

    /// Bewegte Skins müssen nicht in jedem Frame neu gerastert werden — ein
    /// Zwölftel einer Sekunde ist fein genug für den Pixel-Look. Der
    /// Textur-Cache in GameScene schlüsselt darüber.
    static func frameKey(_ id: DotSkin, _ state: State) -> Int {
        if isAnimated(id) { return Int(state.elapsed * 12) }
        if id == .chamaeleon { return min(state.score / 5, skyStages.count - 1) }
        if id == .kombo { return min(state.perfectStreak, 5) }
        return 0
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
            let sky = skyStages[min(state.score / 5, skyStages.count - 1)]
            return dark ? mix(sky, 0x000000, 0.18) : mix(sky, 0xFFFFFF, 0.34)

        case .kombo:
            let k = CGFloat(min(state.perfectStreak, 5)) / 5
            return shaded(col, row, mix(0x8C8790, 0xFFD847, k), mix(0x5F5B63, 0xE0A400, k))
        }
    }
}

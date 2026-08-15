import UIKit

extension UIColor {
    /// Farbe aus einem 0xRRGGBB-Wert (volle Deckkraft).
    convenience init(rgb: UInt32) {
        self.init(
            red: CGFloat((rgb >> 16) & 0xFF) / 255.0,
            green: CGFloat((rgb >> 8) & 0xFF) / 255.0,
            blue: CGFloat(rgb & 0xFF) / 255.0,
            alpha: 1.0
        )
    }
}

/// Gemeinsame Retro-Farbpalette — identische Werte wie in
/// app/.../GameOverlays.kt und TimingGameScreen.kt.
enum Palette {
    static let sky = UIColor(rgb: 0x4EC0CA)
    static let cloud = UIColor(rgb: 0xE9FCFD)
    static let bush = UIColor(rgb: 0x71C837)
    static let bushShade = UIColor(rgb: 0x5AA82C)
    static let trunk = UIColor(rgb: 0x9C6B3C)
    static let trunkShade = UIColor(rgb: 0x7A4E2A)
    static let groundSand = UIColor(rgb: 0xDED895)
    static let groundSandShade = UIColor(rgb: 0xD3C87E)
    static let grassLight = UIColor(rgb: 0x9DE85A)
    static let grassDark = UIColor(rgb: 0x74BF2E)
    static let outline = UIColor(rgb: 0x543847)
    static let dotBody = UIColor(rgb: 0xFFD847)
    static let dotShade = UIColor(rgb: 0xF5A623)
    static let dotShine = UIColor(rgb: 0xFFF3B8)
    static let panelSand = UIColor(rgb: 0xDED895)
    static let textDark = UIColor(rgb: 0x543847)
    static let recordRed = UIColor(rgb: 0xE53935)

    /// Fallen-Zone: klar als Gefahr lesbar, aber unter Zeitdruck verwechselbar.
    static let fakeZone = UIColor(rgb: 0xB44FD8)
    static let fakeZoneCore = UIColor(rgb: 0x8A2FB0)

    /// Banner-/Akzent-Orange und Perfekt-Gelb.
    static let bannerOrange = UIColor(rgb: 0xFF8A3C)
    static let perfectYellow = UIColor(rgb: 0xFFE95E)

    /// Himmelsfarbe pro 5er-Stufe: von Tag über Abendrot bis Nacht. Seit
    /// den Kulissen sagt ScenePaint, welche sieben Töne das sind — hier
    /// bleibt nur der Bestand (WIESE), an dem sich der CHAMAELEON und
    /// jede Vorschau ohne Kulisse orientieren.
    static var skyStages: [UIColor] {
        return ScenePaint.of(.wiese).sky.map { UIColor(rgb: $0) }
    }

    /// Körper- und Schattenfarbe pro Medaillen-Stufe — aus :core, wie
    /// jede andere Farbe des Spiels auch.
    static func medalColors(_ tier: MedalTier) -> (body: UIColor, shade: UIColor) {
        return (UIColor(rgb: tier.bodyColor), UIColor(rgb: tier.shadeColor))
    }

    /// PostScript-Name des Pixel-Fonts (Bytesized, via Info.plist geladen).
    static let fontName = "Bytesized-Regular"
}

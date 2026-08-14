import CoreGraphics
import Foundation

/// Port von core/.../ScenePaint.kt: das Farbwerk aller Kulissen — die
/// zweite Sammlung neben den Skins.
///
/// Eine Kulisse ist alles, was NICHT über Treffer entscheidet: Himmel (in
/// sieben Stufen), Wolken, Requisiten am Boden und der Bodenstreifen. Die
/// Bahn gehört ausdrücklich nicht dazu — Zielzone, Perfekt-Kern und Falle
/// behalten in jeder Kulisse dieselben Farben. Genau deshalb ist die
/// Kulisse die verkäufliche Fläche und die Bahn nicht.
///
/// Die Requisiten sind bewusst Daten und kein Zeichencode: `Prop`
/// beschreibt Form, Größe, Windanteil und Farben, und alle Renderer werten
/// dieselbe Liste gleich aus. Farben sind RGB-Werte ohne Alpha, wie im
/// Rest des iOS-Ports (UIColor(rgb:)).
enum SceneId: String, CaseIterable {
    case wiese = "WIESE"
    case wueste = "WUESTE"
    case meer = "MEER"
    case berg = "BERG"
    case stadt = "STADT"
    case weltraum = "WELTRAUM"

    /// Kulisse zu einem gespeicherten Namen, WIESE als Fallback.
    static func fromName(_ name: String?) -> SceneId {
        guard let name = name, let found = SceneId(rawValue: name) else {
            return .wiese
        }
        return found
    }

    var titleKey: String {
        switch self {
        case .wiese: return "scene_wiese"
        case .wueste: return "scene_wueste"
        case .meer: return "scene_meer"
        case .berg: return "scene_berg"
        case .stadt: return "scene_stadt"
        case .weltraum: return "scene_weltraum"
        }
    }

    /// Freischalt-Hinweis; die WIESE ist von Anfang an offen.
    var unlockHintKey: String? {
        switch self {
        case .wiese: return nil
        case .wueste: return "scene_hint_wueste"
        case .meer: return "scene_hint_meer"
        case .berg: return "scene_hint_berg"
        case .stadt: return "scene_hint_stadt"
        case .weltraum: return "scene_hint_weltraum"
        }
    }

    var scene: ScenePaint.Scene {
        return ScenePaint.of(self)
    }

    func isUnlocked(_ stats: DotSkin.Stats) -> Bool {
        return ScenePaint.isUnlocked(self, stats)
    }
}

/// Die Formen, aus denen Kulissen ihre Requisiten bauen. Jede ist als
/// Stapel von Rechtecken umgesetzt — der Pixel-Look entsteht aus Blöcken.
enum PropShape {
    case baum
    case blume
    case strauch
    case kaktus
    case welle
    case nadelbaum
    case hochhaus
    case fels
}

enum ScenePaint {

    /// Eine Requisite. Die Renderer laufen die Liste einer Kulisse
    /// zyklisch ab (`props[k % props.count]`), genau wie der Bestand
    /// bisher `k % 4` benutzt hat.
    ///
    /// `size` ist ein Anteil der Bildhöhe, `sway` der Anteil am
    /// Windausschlag; negativ heißt gegenläufig, 0 heißt unbeweglich.
    /// `dark`/`body`/`light` gehen von unten (dunkel) nach oben (hell) —
    /// außer bei `blume`, wo `dark` der Stiel, `body` die Blätter und
    /// `light` die Blütenmitte ist. `accents` wechselt je Wiederholung
    /// durch (Blütenblätter, Schaum, Fensterfarbe).
    struct Prop {
        let shape: PropShape
        let size: CGFloat
        let sway: CGFloat
        let dark: UInt32
        let body: UInt32
        let light: UInt32
        var stem: UInt32 = 0x543847
        var stemShade: UInt32 = 0x543847
        var accents: [UInt32] = []
    }

    /// Der Bodenstreifen: Grundfläche mit einem dunkleren Band darin,
    /// darüber eine Narbe aus zwei Farben. WELTRAUM hat keinen.
    struct Ground {
        let sand: UInt32
        let sandShade: UInt32
        let turfDark: UInt32
        let turfLight: UInt32
    }

    /// Eine komplette Kulisse. `cloud` und `ground` sind optional: Im
    /// Vakuum gibt es weder Wolken noch Boden, und beides fehlt dort mit
    /// Absicht, statt in Grau ausgeblendet zu werden.
    struct Scene {
        let sky: [UInt32]
        let cloud: UInt32?
        let ground: Ground?
        let props: [Prop]
    }

    /// Die Bodenkante als Anteil der Bildhöhe. Layout-Anker, nicht Dekor:
    /// Requisiten stehen darauf, der Bodenstreifen beginnt dort, und die
    /// Tod-Animation misst ihren Sturz daran. Gilt für JEDE Kulisse —
    /// auch für WELTRAUM, der gar keinen Boden zeichnet.
    static let groundTop: CGFloat = 0.88

    /// Die Bodenkante in Punkten — der einzige Ort, an dem 0.88 steht.
    static func groundY(_ height: CGFloat) -> CGFloat {
        return height * groundTop
    }

    /// Requisiten-Plätze je Kulisse (Bestand: Baum, Blume, Baum, Strauch).
    static let propSlots = 4

    /// Mindestabstände im RGB-Raum (siehe ScenePaintTest in :core).
    static let minZoneDistance: CGFloat = 60
    static let minSkyStep: CGFloat = 40

    /// Die Greens, die die WIESE seit jeher trägt. Sie sind praktisch die
    /// Zielzonenfarbe — die Grasnarbe ist sogar exakt sie. Das bleibt so:
    /// Diese Flächen liegen am unteren Bildrand, nie im Ringband.
    static let legacyZoneGreens: [UInt32] = [0x71C837, 0x5AA82C, 0x9DE85A, 0x74BF2E]

    // MARK: - Die Kulissen

    /// Der Bestand. Jeder Wert stammt aus GameOverlays.kt und ist
    /// absichtlich unverändert: Wer die Umstellung sieht, hat sie falsch
    /// gemacht.
    private static let wiese = Scene(
        sky: [0x4EC0CA, 0x5B9BD5, 0x7B6FD0, 0xC0616F, 0xD98A3D, 0x3D4A8C, 0x2A2640],
        cloud: 0xE9FCFD,
        ground: Ground(sand: 0xDED895, sandShade: 0xD3C87E, turfDark: 0x74BF2E, turfLight: 0x9DE85A),
        props: [
            Prop(shape: .baum, size: 0.075, sway: 1.0,
                 dark: 0x5AA82C, body: 0x71C837, light: 0x9DE85A,
                 stem: 0x9C6B3C, stemShade: 0x7A4E2A),
            // Die Mitte der Blüte ist Gold (dotBody), nicht Grün — sie war
            // es immer, und sie ist der einzige warme Punkt im Grün.
            Prop(shape: .blume, size: 0.032, sway: 0.8,
                 dark: 0x5AA82C, body: 0x71C837, light: 0xFFD847,
                 accents: [0xE53935, 0xE9FCFD]),
            Prop(shape: .baum, size: 0.058, sway: -1.0,
                 dark: 0x5AA82C, body: 0x71C837, light: 0x9DE85A,
                 stem: 0x9C6B3C, stemShade: 0x7A4E2A),
            Prop(shape: .strauch, size: 0.026, sway: 0.4,
                 dark: 0x5AA82C, body: 0x71C837, light: 0x9DE85A)
        ]
    )

    /// Wüste: heller Dunsthimmel, der über Sandschleier und Glut in eine
    /// kalte Nacht fällt. Die Kakteen sind bewusst blaustichig grün — ein
    /// Wiesengrün hätte den Mindestabstand zur Zielzone gerissen.
    private static let wueste = Scene(
        sky: [0xA8DCE8, 0xF2C46B, 0xE8934A, 0xC85F3C, 0x8E3B47, 0x4A2C4E, 0x241C33],
        cloud: 0xF7E9C8,
        ground: Ground(sand: 0xE8C88A, sandShade: 0xD4AE6E, turfDark: 0xC79A55, turfLight: 0xEFD7A0),
        props: [
            Prop(shape: .kaktus, size: 0.075, sway: 1.0,
                 dark: 0x1F6B41, body: 0x2E8B57, light: 0x43A96B,
                 accents: [0xE8607A, 0xF2A83C]),
            Prop(shape: .fels, size: 0.032, sway: 0,
                 dark: 0x8A6A4A, body: 0xA88860, light: 0xC4A87C),
            Prop(shape: .kaktus, size: 0.058, sway: -1.0,
                 dark: 0x1F6B41, body: 0x2E8B57, light: 0x43A96B,
                 accents: [0xF2A83C, 0xE8607A]),
            Prop(shape: .fels, size: 0.026, sway: 0.4,
                 dark: 0x8A6A4A, body: 0xA88860, light: 0xC4A87C)
        ]
    )

    /// Meer: der Boden ist Wasser, die Narbe darauf ist Schaum.
    private static let meer = Scene(
        sky: [0x5AD2E8, 0x2F9AD4, 0x2E5FB8, 0xC4707C, 0xE09A4A, 0x35447F, 0x1B2138],
        cloud: 0xDFF4FF,
        ground: Ground(sand: 0x2F86C8, sandShade: 0x24699E, turfDark: 0x4FC3DE, turfLight: 0xBFE9FF),
        props: [
            Prop(shape: .welle, size: 0.075, sway: 1.0,
                 dark: 0x1F5FA8, body: 0x2E86D8, light: 0x7FC8F0,
                 accents: [0xFFFFFF, 0xDFF4FF]),
            Prop(shape: .welle, size: 0.032, sway: 0.8,
                 dark: 0x1F5FA8, body: 0x2E86D8, light: 0x7FC8F0,
                 accents: [0xDFF4FF, 0xFFFFFF]),
            Prop(shape: .welle, size: 0.058, sway: -1.0,
                 dark: 0x1F5FA8, body: 0x2E86D8, light: 0x7FC8F0,
                 accents: [0xFFFFFF, 0xDFF4FF]),
            Prop(shape: .fels, size: 0.026, sway: 0.4,
                 dark: 0x4A5A6A, body: 0x6B7C8C, light: 0x9AAAB8)
        ]
    )

    /// Berg: Schnee statt Sand, Nadelbäume mit weißer Spitze.
    private static let berg = Scene(
        sky: [0xA8D8E8, 0x6FAFD8, 0x4A7FC0, 0x8A5A6E, 0xD08A5A, 0x3E4A78, 0x1E2438],
        cloud: 0xF2FAFF,
        ground: Ground(sand: 0xE4EDF4, sandShade: 0xCBD8E4, turfDark: 0xA8B8C8, turfLight: 0xFFFFFF),
        props: [
            Prop(shape: .nadelbaum, size: 0.075, sway: 1.0,
                 dark: 0x1E5140, body: 0x2A6B52, light: 0xD8E8F0,
                 stem: 0x5C4130, stemShade: 0x46311F),
            Prop(shape: .fels, size: 0.032, sway: 0,
                 dark: 0x6A6E78, body: 0x8A8F9C, light: 0xB8BEC9),
            Prop(shape: .nadelbaum, size: 0.058, sway: -1.0,
                 dark: 0x1E5140, body: 0x2A6B52, light: 0xD8E8F0,
                 stem: 0x5C4130, stemShade: 0x46311F),
            Prop(shape: .fels, size: 0.026, sway: 0.4,
                 dark: 0x6A6E78, body: 0x8A8F9C, light: 0xB8BEC9)
        ]
    )

    /// Stadt: Asphalt statt Wiese, Bordstein statt Grasnarbe. Die
    /// Hochhäuser haben Windanteil 0 — ein wankendes Haus wäre ein Witz,
    /// den das Spiel an dieser Stelle nicht macht.
    private static let stadt = Scene(
        sky: [0x9ED4E4, 0x5F9BC8, 0x7B6B9E, 0xC4707E, 0xE8963C, 0x3A3F6E, 0x1A1A2E],
        cloud: 0xE4E8F0,
        ground: Ground(sand: 0x4A4550, sandShade: 0x383340, turfDark: 0x6E6878, turfLight: 0x9A93A4),
        props: [
            Prop(shape: .hochhaus, size: 0.075, sway: 0,
                 dark: 0x3E4A5E, body: 0x56647C, light: 0x8494AC,
                 accents: [0xFFD847, 0x7FD8E8]),
            Prop(shape: .hochhaus, size: 0.052, sway: 0,
                 dark: 0x4E3E52, body: 0x6C5870, light: 0x9A86A0,
                 accents: [0x7FD8E8, 0xFFD847]),
            Prop(shape: .hochhaus, size: 0.062, sway: 0,
                 dark: 0x3A4C50, body: 0x54686C, light: 0x869A9E,
                 accents: [0xFFD847, 0x7FD8E8]),
            Prop(shape: .fels, size: 0.026, sway: 0.4,
                 dark: 0x4E4A56, body: 0x6A6672, light: 0x8C8894)
        ]
    )

    /// Weltraum: kein Boden, keine Wolken. Statt Pflanzen treiben
    /// Felsbrocken in zwei Legierungen auf der Höhe, auf der sonst der
    /// Boden läge — die Linie bleibt, nur der Boden fehlt.
    private static let weltraum = Scene(
        sky: [0x0E1430, 0x1A2A62, 0x3E1A78, 0x6A1E6E, 0x8A2C4A, 0x3A1A3E, 0x0A0716],
        cloud: nil,
        ground: nil,
        props: [
            Prop(shape: .fels, size: 0.075, sway: 1.0,
                 dark: 0x342E42, body: 0x4E4860, light: 0x726C88),
            Prop(shape: .fels, size: 0.032, sway: 0.8,
                 dark: 0x2E3A4A, body: 0x46566C, light: 0x6C8098),
            Prop(shape: .fels, size: 0.058, sway: -1.0,
                 dark: 0x342E42, body: 0x4E4860, light: 0x726C88),
            Prop(shape: .fels, size: 0.026, sway: 0.4,
                 dark: 0x2E3A4A, body: 0x46566C, light: 0x6C8098)
        ]
    )

    static func of(_ id: SceneId) -> Scene {
        switch id {
        case .wiese: return wiese
        case .wueste: return wueste
        case .meer: return meer
        case .berg: return berg
        case .stadt: return stadt
        case .weltraum: return weltraum
        }
    }

    /// Himmelsfarbe zu einem Score — der Weg, den alle Renderer gehen.
    static func skyFor(_ id: SceneId, score: Int) -> UInt32 {
        return of(id).sky[SkinPaint.skyStage(score)]
    }

    /// Drei Farben für Vorschau-Kacheln: Tageshimmel, Boden (im Weltraum
    /// ersatzweise die Nachtstufe) und die Körperfarbe der größten
    /// Requisite.
    static func chips(_ id: SceneId) -> [UInt32] {
        let scene = of(id)
        return [scene.sky[0], scene.ground?.sand ?? scene.sky[6], scene.props[0].body]
    }

    // MARK: - Freischaltung

    /// Kulissen hängen an denselben Zahlen wie die Skins, aber an anderen
    /// Achsen: Wo Skins in dichten Stufen fallen, ist eine Kulisse ein
    /// seltener, großer Wechsel — hohe Schwellen, je eine pro Achse.
    static func isUnlocked(_ id: SceneId, _ stats: DotSkin.Stats) -> Bool {
        switch id {
        case .wiese: return true
        case .wueste: return stats.runCount >= 500
        case .meer: return stats.totalScore >= 10_000
        case .berg: return stats.bestDailyStreak >= 30
        case .stadt: return stats.bestScore >= 85
        // Der Weltraum ist der Abschluss der Sammlung, wie der REGENBOGEN
        // bei den Skins: Er kommt erst, wenn alle anderen offen sind (er
        // selbst zählt nicht mit, sonst wäre die Bedingung zirkulär).
        case .weltraum:
            return SceneId.allCases.allSatisfy { $0 == .weltraum || isUnlocked($0, stats) }
        }
    }

    static func unlockedCount(_ stats: DotSkin.Stats) -> Int {
        return SceneId.allCases.filter { isUnlocked($0, stats) }.count
    }
}

import Foundation

/// Port von core/.../SoundSet.kt: das Klangwerk aller Ton-Sets — die
/// dritte Sammlung neben den Skins und den Kulissen.
///
/// Ein Ton-Set beschreibt dieselben acht Ereignisse noch einmal in einem
/// anderen Charakter. Es entscheidet dabei nie über einen Treffer: Die
/// Zone bleibt gleich breit, und jedes Set gibt zu jedem Ereignis eine
/// Rückmeldung. Genau deshalb ist der Klang — wie die Kulisse — eine
/// verdienbare Fläche und keine Spielregel.
///
/// Die Klänge sind bewusst Daten und kein Synthese-Code pro Port: `Tone`
/// beschreibt Frequenz, Dauer, Lautstärke, Abklingrate und Pulsbreite,
/// `Noise` den Rauschanteil darüber, und alle Ports werfen dieselbe
/// Tabelle in denselben Baukasten aus `ChipSynth`.
enum SoundSetId: String, CaseIterable {
    case klassik = "KLASSIK"
    case glocke = "GLOCKE"
    case amboss = "AMBOSS"

    /// Ton-Set zu einem gespeicherten Namen, KLASSIK als Fallback.
    static func fromName(_ name: String?) -> SoundSetId {
        guard let name = name, let found = SoundSetId(rawValue: name) else {
            return .klassik
        }
        return found
    }

    var titleKey: String {
        switch self {
        case .klassik: return "sound_klassik"
        case .glocke: return "sound_glocke"
        case .amboss: return "sound_amboss"
        }
    }

    /// Freischalt-Hinweis; KLASSIK ist von Anfang an offen.
    var unlockHintKey: String? {
        switch self {
        case .klassik: return nil
        case .glocke: return "sound_hint_glocke"
        case .amboss: return "sound_hint_amboss"
        }
    }

    func isUnlocked(_ stats: DotSkin.Stats) -> Bool {
        return SoundBank.isUnlocked(self, stats)
    }
}

/// Die Ereignisse, für die das Spiel einen Klang hat — genau die, die
/// `GameAudio` anbietet. Der Rohwert ist der Name, unter dem die
/// Abspiel-Schicht den Puffer führt.
enum SoundEvent: String, CaseIterable {
    case start
    case hit
    case perfect
    case chain
    case unlock
    case record
    case death
    case thud
}

enum SoundBank {

    /// Die Wellenformen, die der Baukasten kennt. Mehr als zwei sind es
    /// bewusst nicht: Der Chip, dem dieses Spiel seinen Klang schuldet,
    /// hatte Pulskanäle, einen Dreieckkanal und Rauschen — aber keine Säge.
    enum Wave: String {
        /// Rechteck mit einstellbarer Pulsbreite — hell und schneidend.
        case puls = "PULS"

        /// Dreieck: nur ungerade Oberwellen, quadratisch gedämpft. Weich
        /// und flötenartig; die Pulsbreite bleibt ungelesen.
        case dreieck = "DREIECK"
    }

    /// Ein Ton. Bleibt `fromHz` gleich `toHz`, ist es ein stehender Ton;
    /// sonst ein Gleitton. `duty` ist die Pulsbreite und der eigentliche
    /// Charakterregler: 0,5 klingt rund, 0,125 dünn und nasal. Beim
    /// Gleitton und beim Dreieck steht sie immer auf 0,5 — beide kennen
    /// keine Pulsbreite. `wave` ist die Form; sie steht am Ton und nicht
    /// am Set, weil die Glocke ihre weiche Form bis in den Gleitton des
    /// Todes hinein braucht.
    struct Tone {
        let fromHz: Float
        let toHz: Float
        let seconds: Float
        let volume: Float
        let decay: Float
        let duty: Float
        let wave: Wave
    }

    /// Rauschanteil, der über die Töne gelegt wird. Er beginnt zugleich
    /// mit dem ersten Ton: Rauschen ist das Geräusch des Aufpralls.
    struct Noise {
        let seconds: Float
        let volume: Float
        let decay: Float
    }

    /// Der Klang eines Ereignisses: Töne nacheinander, Rauschen darüber.
    struct Voice {
        let tones: [Tone]
        let noise: Noise?
    }

    // MARK: - Grenzen, die jedes Set einhalten muss

    static let minHz: Float = 50
    static let maxHz: Float = 2500
    static let minSeconds: Float = 0.02
    static let maxSeconds: Float = 0.8
    static let minVolume: Float = 0.05
    static let maxVolume: Float = 0.6
    static let maxDecay: Float = 40

    /// Wie weit zwei Sets bei demselben Ereignis mindestens auseinander
    /// liegen müssen — als Frequenzverhältnis des ersten Tons.
    static let minPitchRatio: Float = 1.25

    // MARK: - Tabellen-Werkzeug

    private static func tone(
        _ hz: Float, _ seconds: Float, _ volume: Float, _ decay: Float, duty: Float = 0.5
    ) -> Tone {
        return Tone(
            fromHz: hz, toHz: hz, seconds: seconds,
            volume: volume, decay: decay, duty: duty, wave: .puls
        )
    }

    /// Ein Dreieck-Ton fester Höhe — dieselben vier Zahlen, nur die weiche
    /// Form. Ohne Pulsbreite, weil ein Dreieck keine hat.
    private static func dreieck(
        _ hz: Float, _ seconds: Float, _ volume: Float, _ decay: Float
    ) -> Tone {
        return Tone(
            fromHz: hz, toHz: hz, seconds: seconds,
            volume: volume, decay: decay, duty: 0.5, wave: .dreieck
        )
    }

    private static func glide(
        _ fromHz: Float, _ toHz: Float, _ seconds: Float, _ volume: Float, _ decay: Float,
        wave: Wave = .puls
    ) -> Tone {
        return Tone(
            fromHz: fromHz, toHz: toHz, seconds: seconds,
            volume: volume, decay: decay, duty: 0.5, wave: wave
        )
    }

    private static func voice(_ tones: [Tone], noise: Noise? = nil) -> Voice {
        return Voice(tones: tones, noise: noise)
    }

    // MARK: - Die Ton-Sets

    /// Der Bestand: NES-Blips, mittlere Lage, volle Pulsbreite, kurzes
    /// perkussives Abklingen — hell und freundlich.
    private static let klassik: [SoundEvent: Voice] = [
        .start: voice([tone(440, 0.06, 0.22, 20)]),
        .hit: voice([tone(660, 0.07, 0.38, 18)]),
        .perfect: voice([tone(988, 0.06, 0.32, 12), tone(1319, 0.16, 0.38, 9)]),
        .chain: voice([tone(880, 0.05, 0.3, 20), tone(1175, 0.07, 0.3, 18)]),
        .unlock: voice([
            tone(523, 0.07, 0.3, 14), tone(659, 0.07, 0.3, 14),
            tone(784, 0.07, 0.3, 14), tone(1046, 0.2, 0.34, 8)
        ]),
        .record: voice([
            tone(784, 0.09, 0.32, 10), tone(1046, 0.09, 0.32, 10),
            tone(1319, 0.09, 0.32, 10), tone(1568, 0.3, 0.36, 6)
        ]),
        .death: voice([glide(700, 90, 0.35, 0.42, 4)], noise: Noise(seconds: 0.12, volume: 0.32, decay: 22)),
        .thud: voice([tone(100, 0.09, 0.5, 14)])
    ]

    /// Glocke: weich und rund. Eine Oktave über dem Bestand, jeder Ton ein
    /// Dreieck, langes Nachklingen. Kein Ereignis trägt Rauschen, auch der
    /// Tod nicht: Hier zerbricht nichts, hier geht das Licht aus.
    ///
    /// Die Form trägt hier mehr als die Tonhöhe: Ein Rechteck in dieser
    /// Lage sticht, das Dreieck lässt die geraden Oberwellen weg.
    private static let glocke: [SoundEvent: Voice] = [
        .start: voice([dreieck(659, 0.16, 0.18, 5)]),
        .hit: voice([dreieck(988, 0.2, 0.26, 5)]),
        .perfect: voice([dreieck(1319, 0.14, 0.24, 4), dreieck(1976, 0.3, 0.26, 3)]),
        .chain: voice([dreieck(1568, 0.12, 0.22, 5), dreieck(2093, 0.18, 0.22, 4)]),
        .unlock: voice([
            dreieck(784, 0.14, 0.2, 4), dreieck(1047, 0.14, 0.2, 4), dreieck(1568, 0.36, 0.24, 2.5)
        ]),
        .record: voice([
            dreieck(1047, 0.16, 0.22, 3), dreieck(1319, 0.16, 0.22, 3), dreieck(2093, 0.5, 0.26, 2)
        ]),
        // Auch der Tod ist ein Dreieck — ein Rechteck-Gleitton wäre der
        // eine harte Moment in einem Set, das sonst keinen hat.
        .death: voice([glide(932, 294, 0.55, 0.28, 2.5, wave: .dreieck)]),
        .thud: voice([dreieck(220, 0.26, 0.3, 5)])
    ]

    /// Amboss: hart, tief und sparsam. Jeder Ton unter 450 Hz, jede
    /// Pulsbreite höchstens ein Viertel, nichts hallt nach. Treffer und
    /// Tod bekommen Rauschen — das macht aus dem Ton einen Schlag.
    private static let amboss: [SoundEvent: Voice] = [
        .start: voice([tone(110, 0.05, 0.3, 30, duty: 0.125)]),
        .hit: voice(
            [tone(220, 0.05, 0.4, 34, duty: 0.125)],
            noise: Noise(seconds: 0.03, volume: 0.18, decay: 40)
        ),
        .perfect: voice([
            tone(330, 0.05, 0.38, 30, duty: 0.25), tone(440, 0.1, 0.42, 22, duty: 0.25)
        ]),
        .chain: voice([
            tone(262, 0.04, 0.34, 36, duty: 0.125), tone(392, 0.05, 0.34, 32, duty: 0.125)
        ]),
        .unlock: voice([
            tone(147, 0.06, 0.36, 22, duty: 0.25), tone(220, 0.06, 0.36, 22, duty: 0.25),
            tone(294, 0.16, 0.4, 12, duty: 0.25)
        ]),
        .record: voice([
            tone(196, 0.07, 0.38, 18, duty: 0.25), tone(294, 0.07, 0.38, 18, duty: 0.25),
            tone(392, 0.26, 0.42, 9, duty: 0.25)
        ]),
        .death: voice(
            [glide(300, 60, 0.3, 0.44, 6)],
            noise: Noise(seconds: 0.18, volume: 0.4, decay: 12)
        ),
        .thud: voice([tone(70, 0.12, 0.55, 10, duty: 0.25)])
    ]

    static func of(_ id: SoundSetId) -> [SoundEvent: Voice] {
        switch id {
        case .klassik: return klassik
        case .glocke: return glocke
        case .amboss: return amboss
        }
    }

    /// Der Klang eines Ereignisses — der Weg, den alle Ports gehen.
    static func voice(_ id: SoundSetId, _ event: SoundEvent) -> Voice {
        // Jedes Set beschreibt jedes Ereignis (SoundSetTest in :core hält
        // das fest); der Rückfall ist nur da, damit ein Tippfehler kein
        // Absturz wird, sondern der Bestand.
        return of(id)[event] ?? klassik[event]!
    }

    /// Drei Balkenhöhen (0..1) für die Vorschau-Kachel: Treffer, Perfekt
    /// und Rekord. Gemessen in Oktaven und nicht in Hertz — zwischen 200
    /// und 400 liegt fürs Ohr derselbe Schritt wie zwischen 1000 und 2000.
    static func chips(_ id: SoundSetId) -> [Float] {
        let spanne = log2(maxHz / minHz)
        return [SoundEvent.hit, .perfect, .record].map { event -> Float in
            let hz = voice(id, event).tones[0].fromHz
            return min(1, max(0, log2(hz / minHz) / spanne))
        }
    }

    // MARK: - Freischaltung

    /// Ton-Sets werden verdient wie Kulissen, aber an eigenen Achsen und
    /// an Schwellen, auf denen sonst nichts liegt: Die Glocke hängt am
    /// Können, der Amboss an der Ausdauer.
    static func isUnlocked(_ id: SoundSetId, _ stats: DotSkin.Stats) -> Bool {
        switch id {
        case .klassik: return true
        case .glocke: return stats.bestPerfectStreak >= 20
        case .amboss: return stats.totalScore >= 25_000
        }
    }

    static func unlockedCount(_ stats: DotSkin.Stats) -> Int {
        return SoundSetId.allCases.filter { isUnlocked($0, stats) }.count
    }
}

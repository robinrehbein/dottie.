import CoreGraphics
import Foundation
import DottieCore

/// Die Stelle, an der iOS auf `:core` trifft.
///
/// Bis v2.23 lag die Spiellogik hier ein zweites Mal — rund 2 900 Zeilen
/// Swift, von Hand aus Kotlin übersetzt und über
/// `parity/golden-vectors.txt` gegen das Original geprüft. Seit `:core`
/// ein Multiplattform-Modul ist, kommt dieselbe Rechnung als
/// `DottieCore.xcframework` ins Projekt: Es gibt nichts mehr zu prüfen,
/// weil es nichts mehr gibt, was auseinanderlaufen könnte.
///
/// Was bleibt, ist Übersetzungsarbeit an der Grenze, und die steckt
/// vollständig in dieser Datei:
///
/// - **Zahlen.** Kotlins `Int` ist Swifts `Int32`, Kotlins `Long` ist
///   `Int64`. Die Szene rechnet in `Int` und `CGFloat`.
/// - **Farben.** `:core` führt Farben als ARGB in einem `Long`, die
///   Renderer hier als 0xRRGGBB in `UInt32` (die Deckkraft sitzt an der
///   `UIColor`). Alle Werte in `:core` sind vollflächig deckend, es geht
///   also nichts verloren.
/// - **Namen.** Kotlin/Native exportiert `object X` als `X.shared` und
///   `companion object` als `X.companion`. Die Erweiterungen unten holen
///   die Schreibweise zurück, die der Renderer schon immer benutzt hat.
///   Wo ein Name schon vergeben ist — Kotlins `Progress` gegen Foundations
///   `Progress` —, steht hier ein `typealias` daneben.
/// - **Aufzählungen.** Kotlin-`enum`s werden zu Klassen; `switch` geht
///   darauf nicht. Wo der Renderer eine Fallunterscheidung braucht
///   (Requisiten-Formen), steht hier ein echtes Swift-`enum` daneben.
///
/// Platform-Code, den `:core` bewusst nicht kennt, steht ebenfalls hier:
/// die Geräte-Uhr (`SkinPaint.clock`) und der lokale Kalendertag
/// (`DailyChallenge.todayEpochDay`). Beides ist keine Spielregel, sondern
/// die Eingabe für eine.

// MARK: - Farben

/// ARGB-`Long` aus `:core` als 0xRRGGBB.
@inline(__always)
func coreRGB(_ argb: Int64) -> UInt32 {
    return UInt32(truncatingIfNeeded: argb) & 0x00FF_FFFF
}

@inline(__always)
private func coreRGB(_ argb: KotlinLong) -> UInt32 {
    return coreRGB(argb.int64Value)
}

// MARK: - Skins

/// Der Name, unter dem der Renderer die Skins kennt. `:core` nennt sie
/// `SkinId`; beides ist derselbe Typ, es gibt keine Umrechnung.
typealias DotSkin = SkinId

extension SkinId {

    typealias Stats = SkinStats
    typealias Family = SkinFamily

    /// Alle Skins in Sammlungs-Reihenfolge.
    static var allCases: [SkinId] { return SkinPaint.shared.ORDER }

    /// Der gespeicherte Wert — derselbe String wie Kotlins `name`.
    var rawValue: String { return name }

    static func fromName(_ name: String?) -> SkinId {
        return SkinPaint.shared.fromName(name: name)
    }

    static func unlockedCount(_ stats: SkinStats) -> Int {
        return Int(SkinPaint.shared.unlockedCount(stats: stats))
    }

    static func collectableCount() -> Int {
        return Int(SkinPaint.shared.collectableCount())
    }

    /// Localizable.strings-Key des Namens.
    var titleKey: String { return "skin_" + name.lowercased() }

    /// Localizable.strings-Key des Freischalt-Hinweises, nil für KLASSIK.
    var unlockHintKey: String? {
        return self == SkinId.klassik ? nil : "skin_hint_" + name.lowercased()
    }

    /// Stellvertreter-Farben für Münzen und Score-Karte.
    var body: UInt32 { return coreRGB(SkinPaint.shared.body(id: self)) }
    var shade: UInt32 { return coreRGB(SkinPaint.shared.shade(id: self)) }
    var shine: UInt32 { return coreRGB(SkinPaint.shared.shine(id: self, state: .still)) }

    /// Farbe eines Rasterfelds des Vogels.
    func cell(_ col: Int, _ row: Int, _ state: SkinState = .still) -> UInt32 {
        return coreRGB(
            SkinPaint.shared.cell(id: self, col: Int32(col), row: Int32(row), state: state)
        )
    }

    func shineColor(_ state: SkinState = .still) -> UInt32 {
        return coreRGB(SkinPaint.shared.shine(id: self, state: state))
    }

    var hasTrail: Bool { return SkinPaint.shared.hasTrail(id: self) }
    var needsEyeOutline: Bool { return SkinPaint.shared.needsEyeOutline(id: self) }
    var isSeasonal: Bool { return SkinPaint.shared.isSeasonal(id: self) }
    var isPatron: Bool { return SkinPaint.shared.isPatron(id: self) }
    var countsForCollection: Bool { return SkinPaint.shared.countsForCollection(id: self) }
    var family: SkinFamily { return SkinPaint.shared.family(id: self) }

    func isUnlocked(_ stats: SkinStats) -> Bool {
        return SkinPaint.shared.isUnlocked(id: self, stats: stats)
    }
}

extension SkinFamily {

    /// Die Familien in Menü-Reihenfolge — dieselbe, in der die Skins
    /// stehen.
    static var allCases: [SkinFamily] {
        return [.einfarbig, .gemustert, .bewegt, .reagierend, .saison, .goenner]
    }

    var titleKey: String { return "skin_family_" + name.lowercased() }
}

extension SkinState {

    /// Standbild: Mittag im Juni, Zeitpunkt 0.
    static let still = SkinState(elapsed: 0, score: 0, perfectStreak: 0, hour: 12, month: 6)

    /// Zustand mit der echten Geräte-Uhr — überall dort zu nehmen, wo ein
    /// Vogel gezeichnet wird, damit TAGESZEIT und JAHRESZEIT nicht ewig
    /// Mittag im Juni zeigen.
    static func now(
        elapsed: CGFloat = 0,
        score: Int = 0,
        perfectStreak: Int = 0
    ) -> SkinState {
        let clock = SkinPaint.clock()
        return SkinState(
            elapsed: Float(elapsed),
            score: Int32(score),
            perfectStreak: Int32(perfectStreak),
            hour: Int32(clock.hour),
            month: Int32(clock.month)
        )
    }
}

extension SkinPaint {

    /// Der Zustandstyp unter dem Namen, unter dem der Renderer ihn kennt.
    typealias State = SkinState

    static var trailSteps: Int { return Int(SkinPaint.shared.TRAIL_STEPS) }
    static var trailSpacing: CGFloat { return CGFloat(SkinPaint.shared.TRAIL_SPACING) }
    static var grid: Int { return Int(SkinPaint.shared.GRID) }

    static func frameKey(_ id: SkinId, _ state: SkinState) -> Int {
        return Int(SkinPaint.shared.frameKey(id: id, state: state))
    }

    static func skyStage(_ score: Int) -> Int {
        return Int(SkinPaint.shared.skyStage(score: Int32(score)))
    }

    /// Stunde und Monat der Geräte-Uhr, für eine halbe Minute gemerkt: Die
    /// beiden Werte werden in jedem Frame gebraucht, ändern sich aber
    /// höchstens stündlich — `Calendar` 60-mal je Sekunde zu fragen wäre
    /// reine Verschwendung.
    static func clock() -> (hour: Int, month: Int) {
        let date = Date()
        let stamp = date.timeIntervalSince1970
        if abs(stamp - Clock.stamp) > 30 {
            let parts = Calendar.current.dateComponents([.hour, .month], from: date)
            Clock.hour = parts.hour ?? 12
            Clock.month = parts.month ?? 6
            Clock.stamp = stamp
        }
        return (Clock.hour, Clock.month)
    }
}

/// Gespeicherter Stand der Geräte-Uhr. Eigener Typ, weil Erweiterungen
/// keine veränderlichen Eigenschaften tragen dürfen.
private enum Clock {
    static var stamp: TimeInterval = -1
    static var hour = 12
    static var month = 6
}

extension Season {

    /// Der Saison-Skin dieses Monats — nil außerhalb jedes Fensters.
    static func forMonth(_ month: Int) -> Season? {
        return Season.companion.forMonth(month: Int32(month))
    }
}

// MARK: - Medaillen

/// `:core` nennt sie `MedalId`, der Renderer `MedalTier` — derselbe Typ.
typealias MedalTier = MedalId

extension MedalId {

    static func forScore(_ score: Int) -> MedalId? {
        return MedalPaint.shared.forScore(score: Int32(score))
    }

    static func next(_ score: Int) -> MedalId? {
        return MedalPaint.shared.next(score: Int32(score))
    }

    static func isUpgrade(score: Int, previousBest: Int) -> Bool {
        return MedalPaint.shared.isUpgrade(
            score: Int32(score), previousBest: Int32(previousBest)
        )
    }

    var threshold: Int { return Int(MedalPaint.shared.threshold(id: self)) }
    var nameKey: String { return "medal_" + name.lowercased() }
    var bodyColor: UInt32 { return coreRGB(MedalPaint.shared.body(id: self)) }
    var shadeColor: UInt32 { return coreRGB(MedalPaint.shared.shade(id: self)) }
}

// MARK: - Kulissen

/// Die Formen, aus denen Kulissen ihre Requisiten bauen — als echtes
/// Swift-`enum`, damit der Renderer darüber `switch`en kann. `:core`
/// führt dieselbe Liste als `PropShape`; [PropLook] übersetzt.
enum PropKind {
    case baum
    case blume
    case strauch
    case kaktus
    case welle
    case nadelbaum
    case hochhaus
    case fels

    fileprivate init(_ shape: PropShape) {
        switch shape {
        case PropShape.blume: self = .blume
        case PropShape.strauch: self = .strauch
        case PropShape.kaktus: self = .kaktus
        case PropShape.welle: self = .welle
        case PropShape.nadelbaum: self = .nadelbaum
        case PropShape.hochhaus: self = .hochhaus
        case PropShape.fels: self = .fels
        default: self = .baum
        }
    }
}

/// Eine Requisite in Renderer-Maßen: Farben als 0xRRGGBB, Form als
/// `switch`-bares `enum`, Größen als `CGFloat`. Reine Umrechnung — jeder
/// Wert stammt aus `:core`.
struct PropLook {
    let shape: PropKind
    let size: CGFloat
    let sway: CGFloat
    let dark: UInt32
    let body: UInt32
    let light: UInt32
    let stem: UInt32
    let stemShade: UInt32
    let accents: [UInt32]

    fileprivate init(_ prop: Prop) {
        shape = PropKind(prop.shape)
        size = CGFloat(prop.size)
        sway = CGFloat(prop.sway)
        dark = coreRGB(prop.dark)
        body = coreRGB(prop.body)
        light = coreRGB(prop.light)
        stem = coreRGB(prop.stem)
        stemShade = coreRGB(prop.stemShade)
        accents = prop.accents.map { coreRGB($0) }
    }
}

/// Der Bodenstreifen einer Kulisse, umgerechnet wie [PropLook].
struct GroundLook {
    let sand: UInt32
    let sandShade: UInt32
    let turfDark: UInt32
    let turfLight: UInt32

    fileprivate init(_ ground: Ground) {
        sand = coreRGB(ground.sand)
        sandShade = coreRGB(ground.sandShade)
        turfDark = coreRGB(ground.turfDark)
        turfLight = coreRGB(ground.turfLight)
    }
}

/// Eine komplette Kulisse, umgerechnet wie [PropLook].
struct SceneLook {
    let sky: [UInt32]
    let cloud: UInt32?
    let ground: GroundLook?
    let props: [PropLook]

    fileprivate init(_ scene: Scene) {
        sky = scene.sky.map { coreRGB($0) }
        cloud = scene.cloud.map { coreRGB($0) }
        ground = scene.ground.map { GroundLook($0) }
        props = scene.props.map { PropLook($0) }
    }
}

/// Ein Stück des Fels-Umrisses in Renderer-Maßen.
struct RockLook {
    let x: CGFloat
    let y: CGFloat
    let w: CGFloat
    let h: CGFloat
    let tone: Int
}

extension ScenePaint {

    typealias Scene = SceneLook
    typealias Prop = PropLook
    typealias Ground = GroundLook

    /// Alle Kulissen, einmal umgerechnet. `of(_:)` läuft in jedem Frame —
    /// sechs Kulissen je Start sind billiger als sechs Umrechnungen je
    /// Sekunde.
    private static let looks: [SceneLook] =
        ScenePaint.shared.ORDER.map { SceneLook(ScenePaint.shared.of(id: $0)) }

    static func of(_ id: SceneId) -> SceneLook {
        return looks[Int(id.ordinal)]
    }

    static func skyFor(_ id: SceneId, score: Int) -> UInt32 {
        return coreRGB(ScenePaint.shared.skyFor(id: id, score: Int32(score)))
    }

    static func groundY(_ height: CGFloat) -> CGFloat {
        return CGFloat(ScenePaint.shared.groundY(height: Float(height)))
    }

    static var groundTop: CGFloat { return CGFloat(ScenePaint.shared.GROUND_TOP) }
    static var propSlots: Int { return Int(ScenePaint.shared.PROP_SLOTS) }
    static var rockWidth: CGFloat { return CGFloat(ScenePaint.shared.ROCK_WIDTH) }
    static var rockHeight: CGFloat { return CGFloat(ScenePaint.shared.ROCK_HEIGHT) }

    static let rockParts: [RockLook] = ScenePaint.shared.ROCK_PARTS.map {
        RockLook(
            x: CGFloat($0.x), y: CGFloat($0.y),
            w: CGFloat($0.w), h: CGFloat($0.h),
            tone: Int($0.tone)
        )
    }

    static func unlockedCount(_ stats: SkinStats) -> Int {
        return Int(ScenePaint.shared.unlockedCount(stats: stats))
    }
}

extension SceneId {

    static var allCases: [SceneId] { return ScenePaint.shared.ORDER }

    static func fromName(_ name: String?) -> SceneId {
        return ScenePaint.shared.fromName(name: name)
    }

    var rawValue: String { return name }
    var titleKey: String { return "scene_" + name.lowercased() }

    var unlockHintKey: String? {
        return self == SceneId.wiese ? nil : "scene_hint_" + name.lowercased()
    }

    func isUnlocked(_ stats: SkinStats) -> Bool {
        return ScenePaint.shared.isUnlocked(id: self, stats: stats)
    }
}

// MARK: - Ton-Sets

extension SoundSetId {

    static var allCases: [SoundSetId] { return SoundBank.shared.ORDER }

    static func fromName(_ name: String?) -> SoundSetId {
        return SoundBank.shared.fromName(name: name)
    }

    var rawValue: String { return name }
    var titleKey: String { return "sound_" + name.lowercased() }

    var unlockHintKey: String? {
        return self == SoundSetId.klassik ? nil : "sound_hint_" + name.lowercased()
    }
}

extension SoundEvent {

    static var allCases: [SoundEvent] { return SoundBank.shared.EVENTS }

    /// Der Schlüssel, unter dem `GameAudio` den Puffer ablegt — dieselbe
    /// Kleinschreibung, die auch `ChipSynth.effects` benutzt.
    var rawValue: String { return name.lowercased() }
}

extension SoundBank {

    typealias Voice = DottieCore.Voice

    static func voice(_ id: SoundSetId, _ event: SoundEvent) -> DottieCore.Voice {
        return SoundBank.shared.voice(id: id, event: event)
    }

    static func chips(_ id: SoundSetId) -> [CGFloat] {
        return SoundBank.shared.chips(id: id).map { CGFloat($0.floatValue) }
    }

    static func isUnlocked(_ id: SoundSetId, _ stats: SkinStats) -> Bool {
        return SoundBank.shared.isUnlocked(id: id, stats: stats)
    }

    static func unlockedCount(_ stats: SkinStats) -> Int {
        return Int(SoundBank.shared.unlockedCount(stats: stats))
    }
}

extension ChipSynth {

    static var sampleRate: Int { return Int(ChipSynth.shared.SAMPLE_RATE) }

    /// Samples eines Klangs. `rate` verstimmt ihn: Android pitcht beim
    /// Abspielen (SoundPool), iOS rendert jede Stufe einmal vor.
    static func render(_ voice: DottieCore.Voice, rate: Float = 1) -> [Float] {
        let samples = ChipSynth.shared.render(voice: voice, rate: rate)
        return (0..<Int(samples.size)).map { samples.get(index: Int32($0)) }
    }

    static func hitRate(score: Int) -> Float {
        return ChipSynth.shared.hitRate(score: Int32(score))
    }

    static func perfectRate(streak: Int) -> Float {
        return ChipSynth.shared.perfectRate(streak: Int32(streak))
    }
}

// MARK: - Ziele

/// Kotlins `Progress` unter einem Namen, der nicht schon vergeben ist:
/// Foundation bringt `Progress` (NSProgress) mit, und `import Foundation`
/// steht in jeder Datei dieser App.
typealias Goals = DottieCore.Progress

extension DottieCore.Progress {

    static var pageGoals: Int { return Int(Goals.shared.PAGE_GOALS) }
    static var barBlocks: Int { return Int(Goals.shared.BAR_BLOCKS) }

    static func filledBlocks(_ fraction: CGFloat) -> Int {
        return Int(Goals.shared.filledBlocks(fraction: Float(fraction)))
    }

    static func nextGoals(
        _ stats: SkinStats,
        month: Int,
        seasonDays: Int,
        limit: Int
    ) -> [Goal] {
        return Goals.shared.nextGoals(
            stats: stats,
            month: Int32(month),
            seasonDays: Int32(seasonDays),
            limit: Int32(limit)
        )
    }
}

extension Goal {

    /// Der Beschriftungs-Schlüssel der Belohnung. Genau eines von Skin,
    /// Kulisse und Ton-Set ist gesetzt.
    var titleKey: String {
        if let skin = skin { return skin.titleKey }
        if let scene = scene { return scene.titleKey }
        if let sound = sound { return sound.titleKey }
        return ""
    }
}

// MARK: - Spiel

extension TimingGame {

    static func wrapToPi(_ value: Float) -> Float {
        return TimingGame.companion.wrapToPi(value: value)
    }

    static var deathFreezeSeconds: Float {
        return TimingGame.companion.DEATH_FREEZE_SECONDS
    }
}

extension Twist {

    /// Localizable.strings-Key des Ankündigungs-Banners.
    var bannerKey: String { return "banner_twist_" + name.lowercased() }
}

// MARK: - Daily Challenge

extension DailyChallenge {

    static func seedFor(epochDay: Int64) -> Int64 {
        return DailyChallenge.shared.seedFor(epochDay: epochDay)
    }

    static func nextStreak(
        lastPlayedEpochDay: Int64,
        currentStreak: Int,
        todayEpochDay: Int64
    ) -> Int {
        return Int(DailyChallenge.shared.nextStreak(
            lastPlayedEpochDay: lastPlayedEpochDay,
            currentStreak: Int32(currentStreak),
            todayEpochDay: todayEpochDay
        ))
    }

    /// Heutiger Kalendertag als Epoch-Day, äquivalent zu Javas
    /// `LocalDate.now().toEpochDay()`: Tage seit 1970-01-01 in der lokalen
    /// Zeitzone. Steht hier und nicht in `:core` — welcher Tag "heute" ist,
    /// beantwortet das Gerät, nicht die Spielregel.
    static func todayEpochDay(date: Date = Date()) -> Int64 {
        let offset = TimeZone.current.secondsFromGMT(for: date)
        let localSeconds = date.timeIntervalSince1970 + Double(offset)
        return Int64(floor(localSeconds / 86400.0))
    }
}

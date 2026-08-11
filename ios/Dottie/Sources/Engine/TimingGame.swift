import Foundation

/// 1:1-Port der Kotlin-Engine `TimingGame` (core/.../TimingGame.kt).
///
/// Der Punkt läuft automatisch auf einer Kreisbahn. Irgendwo auf der Bahn
/// liegt eine Zielzone — ein Tap, während der Punkt in der Zone ist,
/// zählt einen Treffer: Die Laufrichtung dreht um, die Zone springt an
/// eine neue Position, das Tempo steigt und die Zone schrumpft. Ein Tap
/// außerhalb der Zone oder ein Überfahren der Zone ohne Tap ist sofort
/// das Ende.
///
/// Alle Konstanten und Formeln stammen unverändert aus dem Original —
/// keine eigene Balance. Winkel in Radiant, Tempo in Radiant pro Sekunde.
final class TimingGame {

    enum Phase {
        case ready
        case running
        case dying
        case over
    }

    /// Reihenfolge entspricht `Twist.entries` in Kotlin — daran hängt die
    /// deterministische Twist-Auswahl im Daily-Modus.
    enum Twist: Int, CaseIterable {
        case pulse
        case drift
        case ghost
        case fake
        case chain
    }

    enum GameEvent: Equatable {
        case started
        case hit
        case perfectHit
        case chainNext
        case twistUnlocked(Twist)
        case died
        case settled
    }

    private var random: KotlinRandom

    private(set) var phase: Phase = .ready

    /// Position des Punkts auf der Bahn.
    private(set) var angle: Float = 0

    /// Laufrichtung: +1 = im Uhrzeigersinn, -1 = dagegen.
    private(set) var direction: Int = 1

    private(set) var zoneCenter: Float = 1.8

    /// Basisbreite der Zone (halb); die effektive Breite kann pulsieren.
    private(set) var zoneHalfWidth: Float = TimingGame.baseZoneHalf

    private(set) var score: Int = 0

    /// Anzahl der Treffer — die Basis für Tempo und Zonenbreite.
    private(set) var hits: Int = 0

    /// Aktuelle Serie perfekter Treffer in Folge.
    private(set) var perfectStreak: Int = 0

    /// Punkte des letzten Treffers, für die Anzeige ("PERFEKT! +3").
    private(set) var lastHitPoints: Int = 0

    private(set) var elapsed: Float = 0

    /// Zeit seit dem letzten Treffer, für Animationen.
    private(set) var timeSinceHit: Float = 99

    /// War der letzte Treffer ein perfekter? Für Anzeige-Effekte.
    private(set) var lastHitPerfect: Bool = false

    /// Die für die aktuelle Zone aktiven Twists.
    private(set) var activeTwists: Set<Twist> = []

    /// Köder-Zone (nur relevant, wenn FAKE aktiv ist).
    private(set) var hasFakeZone: Bool = false
    private(set) var fakeZoneCenter: Float = 0

    /// Wie viele Ketten-Zonen nach der aktuellen noch folgen.
    private(set) var chainRemaining: Int = 0

    /// Drift-Richtung relativ zur Laufrichtung: +1 = flieht, -1 = kommt entgegen.
    private var driftSign: Int = 1

    /// Bereits angekündigte Twists (Banner nur einmal pro Lauf).
    private var announcedTwists: Set<Twist> = []

    /// Events aus tap() werden gepuffert und mit dem nächsten update()
    /// ausgeliefert, damit der UI-Loop eine einzige Event-Quelle hat.
    private var pendingEvents: [GameEvent] = []

    init(random: KotlinRandom = KotlinRandom.systemSeeded()) {
        self.random = random
    }

    /// Relative Position des Punkts zur Zone: negativ = davor, 0 = Mitte.
    func relativeToZone() -> Float {
        return TimingGame.wrapToPi(Float(direction) * (angle - zoneCenter))
    }

    /// Effektive halbe Zonenbreite — pulsiert, wenn PULSE aktiv ist.
    /// sin wie Kotlin/JVM in Double gerechnet und auf Float gerundet,
    /// damit Android und iOS bit-identisch bleiben.
    func effectiveZoneHalf() -> Float {
        if !activeTwists.contains(.pulse) {
            return zoneHalfWidth
        }
        let wave = Float(sin(Double(elapsed * TimingGame.pulseSpeed)))
        let pulse = TimingGame.pulseMinShare + (1 - TimingGame.pulseMinShare) *
            (0.5 + 0.5 * wave)
        return zoneHalfWidth * pulse
    }

    /// Steht der Punkt gerade in der (effektiven) Zielzone?
    var isInZone: Bool {
        return abs(relativeToZone()) <= effectiveZoneHalf()
    }

    /// Ist der Punkt gerade sichtbar? Blinkt nur im GHOST-Twist.
    var isDotVisible: Bool {
        if phase != .running || !activeTwists.contains(.ghost) {
            return true
        }
        let cycle = (elapsed * TimingGame.ghostBlinkSpeed)
            .truncatingRemainder(dividingBy: 1)
        return cycle < TimingGame.ghostVisibleShare
    }

    func currentSpeed() -> Float {
        return min(
            TimingGame.baseSpeed + Float(hits) * TimingGame.speedPerHit,
            TimingGame.maxSpeed
        )
    }

    /// Verarbeitet einen Tap. In READY startet er den Lauf, in RUNNING ist
    /// er der Stopp-Versuch, in OVER (nach kurzer Sperre gegen Wut-Taps)
    /// geht es zurück in den READY-Zustand.
    @discardableResult
    func tap() -> GameEvent? {
        var event: GameEvent?
        switch phase {
        case .ready:
            phase = .running
            elapsed = 0
            spawnZone()
            event = .started
        case .running:
            let rel = relativeToZone()
            let half = effectiveZoneHalf()
            if abs(rel) <= half {
                let perfect = abs(rel) <= half * TimingGame.perfectShare
                registerHit(perfect: perfect)
                event = perfect ? .perfectHit : .hit
            } else if rel > half &&
                rel <= half + currentSpeed() * TimingGame.lateTapForgivenessSeconds {
                // Touch-Latenz-Gnade: Auf der Auslauf-Seite zählt ein
                // minimal verspäteter Tap noch als normaler Treffer.
                registerHit(perfect: false)
                event = .hit
            } else {
                // Auch ein Tap in der Fallen-Zone landet hier: Sie ist
                // mechanisch einfach "daneben" — ihre Gefahr ist optisch.
                die()
                event = .died
            }
        case .dying:
            event = nil
        case .over:
            if elapsed >= TimingGame.restartLockSeconds {
                // Sofort-Neustart: aus der Wut direkt in den nächsten Lauf.
                reset()
                phase = .running
                elapsed = 0
                spawnZone()
                event = .started
            } else {
                event = nil
            }
        }
        if let event = event {
            pendingEvents.append(event)
        }
        return event
    }

    /// Ersetzt die Zufallsquelle — vor jedem Daily-Lauf mit dem Tages-Seed
    /// aufgerufen, damit jeder Versuch des Tages dieselbe Zonen- und
    /// Twist-Abfolge bekommt. `nil` stellt echten Zufall wieder her.
    func reseed(_ seed: Int64?) {
        if let seed = seed {
            random = KotlinRandom(seed: seed)
        } else {
            random = KotlinRandom.systemSeeded()
        }
    }

    /// Setzt alles auf den READY-Zustand zurück (Rekord bleibt beim Store).
    func reset() {
        phase = .ready
        angle = 0
        direction = 1
        zoneCenter = 1.8
        zoneHalfWidth = TimingGame.baseZoneHalf
        score = 0
        hits = 0
        perfectStreak = 0
        lastHitPoints = 0
        elapsed = 0
        timeSinceHit = 99
        lastHitPerfect = false
        activeTwists.removeAll()
        hasFakeZone = false
        chainRemaining = 0
        announcedTwists.removeAll()
        pendingEvents.removeAll()
    }

    /// Schreibt einen Frame fort und liefert die dabei aufgetretenen Events.
    func update(deltaSeconds: Float) -> [GameEvent] {
        let dt = min(max(deltaSeconds, 0), TimingGame.maxDelta)
        elapsed += dt
        timeSinceHit += dt
        var events: [GameEvent] = []
        events.append(contentsOf: pendingEvents)
        pendingEvents.removeAll()

        switch phase {
        case .ready:
            angle = TimingGame.wrapTwoPi(
                angle + Float(direction) * TimingGame.readySpeed * dt
            )
        case .running:
            angle = TimingGame.wrapTwoPi(
                angle + Float(direction) * currentSpeed() * dt
            )
            if activeTwists.contains(.drift) {
                zoneCenter = TimingGame.wrapTwoPi(
                    zoneCenter + Float(direction * driftSign) * TimingGame.driftSpeed * dt
                )
            }
            // Zone ohne Tap überfahren -> vorbei. Geprüft wird gegen die
            // volle Basisbreite, damit eine pulsierende Zone fair bleibt.
            if relativeToZone() > zoneHalfWidth + currentSpeed() * TimingGame.passBufferSeconds {
                die()
                events.append(.died)
            }
        case .dying:
            // Kurzer Freeze für Flash und Shake, dann der Mario-Hüpfer:
            // Das Game-Over-Overlay erscheint erst, wenn der Vogel aus
            // dem Bild gefallen ist (settled = "aufgeschlagen").
            if elapsed >= TimingGame.deathFreezeSeconds + TimingGame.deathFallSeconds {
                phase = .over
                elapsed = 0
                events.append(.settled)
            }
        case .over:
            break
        }
        return events
    }

    private func registerHit(perfect: Bool) {
        hits += 1
        if perfect {
            perfectStreak += 1
            lastHitPoints = min(
                TimingGame.perfectBaseScore - 1 + perfectStreak,
                TimingGame.perfectMaxScore
            )
        } else {
            perfectStreak = 0
            lastHitPoints = 1
        }
        score += lastHitPoints
        timeSinceHit = 0
        lastHitPerfect = perfect
        zoneHalfWidth = max(
            TimingGame.baseZoneHalf - Float(hits) * TimingGame.zoneShrinkPerHit,
            TimingGame.minZoneHalf
        )

        if chainRemaining > 0 {
            // Ketten-Zone: gleiche Richtung, die nächste kommt sofort.
            chainRemaining -= 1
            hasFakeZone = false
            activeTwists.remove(.fake)
            spawnChainZone()
            pendingEvents.append(.chainNext)
        } else {
            direction = -direction
            spawnZone()
        }
    }

    private func spawnZone() {
        // Der Mindestabstand ist zeitbasiert: Egal wie schnell der Punkt
        // schon kreist, bleiben immer mindestens minReactionSeconds bis
        // zur neuen Zone — sonst stirbt man an Physik statt an Skill.
        let minDistance = max(
            TimingGame.minZoneDistance,
            currentSpeed() * TimingGame.minReactionSeconds
        )
        let maxDistance = max(TimingGame.maxZoneDistance, minDistance + 0.4)
        let distance = minDistance + random.nextFloat() * (maxDistance - minDistance)
        zoneCenter = TimingGame.wrapTwoPi(angle + Float(direction) * distance)
        chooseTwists()

        driftSign = random.nextBoolean() ? 1 : -1
        chainRemaining = activeTwists.contains(.chain) ? TimingGame.chainLength : 0

        hasFakeZone = false
        if activeTwists.contains(.fake) {
            let maxFakeDistance = distance - zoneHalfWidth * 3
            if maxFakeDistance > TimingGame.fakeMinDistance {
                let fakeDistance = TimingGame.fakeMinDistance +
                    random.nextFloat() * (maxFakeDistance - TimingGame.fakeMinDistance)
                fakeZoneCenter = TimingGame.wrapTwoPi(
                    angle + Float(direction) * fakeDistance
                )
                hasFakeZone = true
            }
        }
    }

    /// Folge-Zone einer Kette: näher dran, keine neue Twist-Auswahl.
    private func spawnChainZone() {
        let minDistance = max(
            TimingGame.chainMinDistance,
            currentSpeed() * TimingGame.minReactionSeconds
        )
        let maxDistance = max(TimingGame.chainMaxDistance, minDistance + 0.3)
        let distance = minDistance + random.nextFloat() * (maxDistance - minDistance)
        zoneCenter = TimingGame.wrapTwoPi(angle + Float(direction) * distance)
    }

    private func chooseTwists() {
        activeTwists.removeAll()

        // Reihenfolge wie Kotlin `Twist.entries.filter { ... }`.
        let unlocked = Twist.allCases.filter { score >= TimingGame.unlockScore($0) }

        // Ein frisch freigeschalteter Twist wird garantiert gezeigt
        // und einmalig angekündigt.
        if let fresh = unlocked.first(where: { !announcedTwists.contains($0) }) {
            activeTwists.insert(fresh)
            announcedTwists.insert(fresh)
            pendingEvents.append(.twistUnlocked(fresh))
        }

        var shuffled = unlocked
        random.shuffle(&shuffled)
        for twist in shuffled {
            if activeTwists.count >= TimingGame.maxActiveTwists {
                break
            }
            if activeTwists.contains(twist) {
                continue
            }
            if conflictsWithActive(twist) {
                continue
            }
            if random.nextFloat() < TimingGame.twistProbability {
                activeTwists.insert(twist)
            }
        }
    }

    /// Kuratierte Kombis: GEIST + FALLE stapelt fehlende Information mit
    /// tödlicher Fehlinformation — Tode daraus fühlen sich nach Zufall an.
    private func conflictsWithActive(_ candidate: Twist) -> Bool {
        for pair in TimingGame.forbiddenCombos {
            if pair.contains(candidate) &&
                activeTwists.contains(where: { $0 != candidate && pair.contains($0) }) {
                return true
            }
        }
        return false
    }

    private func die() {
        if phase != .running {
            return
        }
        phase = .dying
        elapsed = 0
    }

    // MARK: - Konstanten (identisch zum Kotlin-Original)

    static let maxDelta: Float = 1.0 / 30.0

    // Tempo (Radiant pro Sekunde)
    static let baseSpeed: Float = 2.4
    static let speedPerHit: Float = 0.07
    static let maxSpeed: Float = 5.2
    static let readySpeed: Float = 1.2

    // Zielzone (Radiant)
    static let baseZoneHalf: Float = 0.40
    static let zoneShrinkPerHit: Float = 0.005
    static let minZoneHalf: Float = 0.15
    static let perfectShare: Float = 0.35
    static let minZoneDistance: Float = 1.1
    static let maxZoneDistance: Float = 2.8

    // Fairness (Sekunden)
    static let minReactionSeconds: Float = 0.45
    static let lateTapForgivenessSeconds: Float = 0.07
    static let passBufferSeconds: Float = 0.09

    // Scoring: erster Perfekt +2, jeder weitere in Serie +1 mehr, Deckel +5.
    static let perfectBaseScore: Int = 2
    static let perfectMaxScore: Int = 5

    // Twists
    static let maxActiveTwists: Int = 2

    /// Nie zusammen aktive Twist-Paare.
    static let forbiddenCombos: [Set<Twist>] = [[.ghost, .fake]]
    static let twistProbability: Float = 0.45
    static let pulseSpeed: Float = 5
    static let pulseMinShare: Float = 0.62
    static let driftSpeed: Float = 0.35
    static let ghostBlinkSpeed: Float = 1.6
    static let ghostVisibleShare: Float = 0.62
    static let fakeMinDistance: Float = 0.55
    static let chainLength: Int = 1
    static let chainMinDistance: Float = 1.0
    static let chainMaxDistance: Float = 1.8

    static let deathFreezeSeconds: Float = 0.5

    /// Dauer der Fall-Animation nach dem Freeze (Mario-Hüpfer).
    static let deathFallSeconds: Float = 1.0
    static let restartLockSeconds: Float = 0.55

    /// Ab welchem Score ein Twist ins Spiel kommt.
    static func unlockScore(_ twist: Twist) -> Int {
        switch twist {
        case .pulse: return 5
        case .drift: return 10
        case .ghost: return 15
        case .fake: return 20
        case .chain: return 25
        }
    }

    /// Wichtig: exakt wie Kotlin `(2 * PI).toFloat()` — zum NÄCHSTEN Float
    /// gerundet. Swifts `Float.pi` ist dagegen zur Null hin gerundet und
    /// läge 1 ulp daneben; das würde die Daily-Determinismus-Garantie
    /// gegenüber Android brechen.
    private static let twoPi: Float = Float(2.0 * Double.pi)

    /// Normalisiert auf (-PI, PI]. Die Vergleiche laufen wie in Kotlin
    /// gegen das Double-PI (dort promotet `v <= -PI` den Float).
    static func wrapToPi(_ value: Float) -> Float {
        var v = value.truncatingRemainder(dividingBy: twoPi)
        if Double(v) <= -Double.pi {
            v += twoPi
        }
        if Double(v) > Double.pi {
            v -= twoPi
        }
        return v
    }

    /// Normalisiert auf [0, 2*PI).
    static func wrapTwoPi(_ value: Float) -> Float {
        var v = value.truncatingRemainder(dividingBy: twoPi)
        if v < 0 {
            v += twoPi
        }
        return v
    }
}

import CoreGraphics
import Foundation
import XCTest

/// Prüft den Swift-Port gegen `parity/golden-vectors.txt` — die Werte,
/// die die Kotlin-Engine in `:core` liefert (siehe parity/README.md).
///
/// Ohne diese Tests war der iOS-Port völlig ungetestet: Ob `KotlinRandom`
/// wirklich Kotlins XorWow-Generator trifft (und damit dieselbe Daily
/// Challenge erzeugt), hätte man nur gemerkt, indem man ein iPhone und
/// ein Android-Gerät nebeneinanderlegt.
final class ParityTests: XCTestCase {

    /// Toleranz für Werte, die nicht über einen Lauf aufsummiert werden
    /// (Konstanten, Zonenbreite): Die reichen bis auf die sechs
    /// Nachkommastellen der Vektor-Datei heran.
    private let eps: Float = 1e-5

    /// Toleranz für aufsummierte Werte (Winkel, Zonenmitte).
    ///
    /// Kotlin läuft auf der JVM und rechnet jede Fließkomma-Operation
    /// einzeln; Swift wird von LLVM übersetzt, das `a + b * c` zu einem
    /// fused multiply-add zusammenziehen darf. Beides ist korrekt, aber
    /// nicht bitgleich — über die rund 6000 Frames eines Laufs summiert
    /// sich das auf gemessene 4·10⁻⁴ Radiant. Ein echter Logikfehler
    /// fällt trotzdem auf: Der Punkt wandert pro Frame etwa 0,02 Radiant,
    /// also das Fünfzigfache dieser Schranke.
    private let epsDrift: Float = 5e-3

    private var vectors: ParityVectors!

    override func setUpWithError() throws {
        vectors = try ParityVectors()
    }

    // MARK: - Format

    func testVectorVersion() throws {
        XCTAssertEqual(try vectors.int("version"), 3,
                       "Format der Vektor-Datei hat sich geändert")
    }

    // MARK: - Konstanten

    func testEngineConstants() throws {
        let expected: [String: Float] = [
            "MAX_DELTA": TimingGame.maxDelta,
            "BASE_SPEED": TimingGame.baseSpeed,
            "SPEED_PER_HIT": TimingGame.speedPerHit,
            "MAX_SPEED": TimingGame.maxSpeed,
            "READY_SPEED": TimingGame.readySpeed,
            "BASE_ZONE_HALF": TimingGame.baseZoneHalf,
            "ZONE_SHRINK_PER_HIT": TimingGame.zoneShrinkPerHit,
            "MIN_ZONE_HALF": TimingGame.minZoneHalf,
            "PERFECT_SHARE": TimingGame.perfectShare,
            "MIN_ZONE_DISTANCE": TimingGame.minZoneDistance,
            "MAX_ZONE_DISTANCE": TimingGame.maxZoneDistance,
            "MIN_REACTION_SECONDS": TimingGame.minReactionSeconds,
            "LATE_TAP_FORGIVENESS_SECONDS": TimingGame.lateTapForgivenessSeconds,
            "PASS_BUFFER_SECONDS": TimingGame.passBufferSeconds,
            "TWIST_PROBABILITY": TimingGame.twistProbability,
            "PULSE_SPEED": TimingGame.pulseSpeed,
            "PULSE_MIN_SHARE": TimingGame.pulseMinShare,
            "DRIFT_SPEED": TimingGame.driftSpeed,
            "GHOST_BLINK_SPEED": TimingGame.ghostBlinkSpeed,
            "GHOST_VISIBLE_SHARE": TimingGame.ghostVisibleShare,
            "FAKE_MIN_DISTANCE": TimingGame.fakeMinDistance,
            "CHAIN_MIN_DISTANCE": TimingGame.chainMinDistance,
            "CHAIN_MAX_DISTANCE": TimingGame.chainMaxDistance,
            "DEATH_FREEZE_SECONDS": TimingGame.deathFreezeSeconds,
            "DEATH_FALL_SECONDS": TimingGame.deathFallSeconds,
            "RESTART_LOCK_SECONDS": TimingGame.restartLockSeconds,
            // Untergrenze des PERFEKT-Kerns: ein halber Bahn-Block.
            "SEGMENT_HALF": TimingGame.segmentHalf
        ]
        for (name, value) in expected {
            XCTAssertEqual(try vectors.float("const.\(name)"), value, accuracy: eps,
                           "Konstante \(name)")
        }

        let ints: [String: Int] = [
            "PERFECT_BASE_SCORE": TimingGame.perfectBaseScore,
            "PERFECT_MAX_SCORE": TimingGame.perfectMaxScore,
            "MAX_ACTIVE_TWISTS": TimingGame.maxActiveTwists,
            "CHAIN_LENGTH": TimingGame.chainLength,
            "TRACK_SEGMENTS": TimingGame.trackSegments
        ]
        for (name, value) in ints {
            XCTAssertEqual(try vectors.int("const.\(name)"), value, "Konstante \(name)")
        }
    }

    func testTwistUnlocks() throws {
        for twist in TimingGame.Twist.allCases {
            let name = ParityTests.name(twist)
            let expected = try vectors.int("twist.unlock.\(name)")
            XCTAssertEqual(expected, TimingGame.unlockScore(twist),
                           "Freischalt-Score von \(name)")
        }

        let forbidden = try vectors.strings("twist.forbidden")
        let actual = TimingGame.forbiddenCombos.map { combo in
            combo.map { ParityTests.name($0) }.sorted().joined(separator: "+")
        }
        XCTAssertEqual(forbidden.sorted(), actual.sorted(), "verbotene Twist-Paare")
    }

    // MARK: - Daily Challenge

    func testDailySeedsAndStreaks() throws {
        for key in vectors.keys where key.hasPrefix("daily.seed.") {
            let day = Int64(key.dropFirst("daily.seed.".count)) ?? 0
            let expected = try vectors.int64(key)
            XCTAssertEqual(expected, DailyChallenge.seedFor(epochDay: day),
                           "Tages-Seed für Epoch-Day \(day)")
        }

        for key in vectors.keys where key.hasPrefix("daily.streak.") {
            let parts = key.dropFirst("daily.streak.".count).split(separator: ".")
            guard parts.count == 3,
                  let last = Int64(parts[0]),
                  let streak = Int(parts[1]),
                  let today = Int64(parts[2]) else {
                XCTFail("Unlesbarer Schlüssel: \(key)")
                continue
            }
            let expected = try vectors.int(key)
            XCTAssertEqual(
                expected,
                DailyChallenge.nextStreak(lastPlayedEpochDay: last,
                                          currentStreak: streak,
                                          todayEpochDay: today),
                "Serien-Regel \(key)"
            )
        }
    }

    // MARK: - Medaillen

    func testMedals() throws {
        for tier in MedalTier.allCases {
            let name = ParityTests.name(tier)
            let row = try vectors.strings("medal.\(name)")
            XCTAssertEqual(row.count, 3, "medal.\(name): Schwelle, Körper, Schatten")
            XCTAssertEqual(Int(row[0]) ?? -1, tier.threshold, "Schwelle von \(name)")
            // Die Münzfarben leben auf iOS in der UI (Palette.swift), nicht
            // im Engine-Port — geprüft wird hier die Schwelle. Die Farben
            // stehen trotzdem in der Datei, damit :app, :wear und web/ sie
            // gegen dieselbe Quelle halten können.
        }

        for key in vectors.keys where key.hasPrefix("medal.forScore.") {
            let score = Int(key.dropFirst("medal.forScore.".count)) ?? 0
            let row = try vectors.strings(key)
            let current = MedalTier.forScore(score).map { ParityTests.name($0) } ?? "-"
            let next = MedalTier.next(score).map { ParityTests.name($0) } ?? "-"
            XCTAssertEqual(row[0], current, "Medaille bei Score \(score)")
            XCTAssertEqual(row[1], next, "nächste Medaille bei Score \(score)")
        }
    }

    // MARK: - Himmel und Skins

    func testSky() throws {
        XCTAssertEqual(try vectors.int("sky.cycle"), SkinPaint.skyCycle)

        // Ohne Alpha vergleichen, siehe assertColor.
        let stages = try vectors.strings("sky.stages")
            .map { ParityVectors.color($0) & 0x00FFFFFF }
        XCTAssertEqual(stages, SkinPaint.skyStages.map { $0 & 0x00FFFFFF },
                       "Himmelsstufen")

        let expected = try vectors.strings("sky.stageForScore").map { Int($0) ?? -1 }
        let actual = stride(from: 0, through: 70, by: 5).map { SkinPaint.skyStage($0) }
        XCTAssertEqual(expected, actual, "Himmelsstufe je Score")
    }

    func testSkinOrderAndChips() throws {
        let order = try vectors.strings("skin.order")
        XCTAssertEqual(order, DotSkin.allCases.map { $0.rawValue },
                       "Reihenfolge der Skins (daran hängt der gespeicherte Wert)")
        XCTAssertEqual(try vectors.int("skin.grid"), SkinPaint.grid)

        XCTAssertEqual(try vectors.int("skin.collectableCount"),
                       DotSkin.collectableCount(),
                       "Zahl der sammelbaren Skins (daran hängt der REGENBOGEN)")

        for skin in DotSkin.allCases {
            let row = try vectors.strings("skin.chips.\(skin.rawValue)")
            XCTAssertEqual(row.count, 9, "skin.chips.\(skin.rawValue)")
            assertColor(ParityVectors.color(row[0]), SkinPaint.body(skin),
                        "\(skin.rawValue).body")
            assertColor(ParityVectors.color(row[1]), SkinPaint.shade(skin),
                        "\(skin.rawValue).shade")
            assertColor(ParityVectors.color(row[2]), SkinPaint.shine(skin),
                        "\(skin.rawValue).shine")
            XCTAssertEqual(row[3] == "trail", SkinPaint.hasTrail(skin),
                           "\(skin.rawValue).hasTrail")
            XCTAssertEqual(row[4] == "eyeoutline", SkinPaint.needsEyeOutline(skin),
                           "\(skin.rawValue).needsEyeOutline")
            XCTAssertEqual(row[5] == "animated", SkinPaint.isAnimated(skin),
                           "\(skin.rawValue).isAnimated")
            XCTAssertEqual(row[6] == "seasonal", SkinPaint.isSeasonal(skin),
                           "\(skin.rawValue).isSeasonal")
            XCTAssertEqual(row[7] == "patron", SkinPaint.isPatron(skin),
                           "\(skin.rawValue).isPatron")
            XCTAssertEqual(row[8] == "collectable", SkinPaint.countsForCollection(skin),
                           "\(skin.rawValue).countsForCollection")
        }
    }

    /// Saison-Skins: nur die Maske entscheidet, nie der Kalender — genau
    /// deshalb müssen Monat und Bit auf allen Plattformen gleich sein.
    func testSeasons() throws {
        for season in Season.allCases {
            let row = try vectors.strings("season.\(season.skin.rawValue)")
            XCTAssertEqual(row.count, 3, "season.\(season.skin.rawValue)")
            XCTAssertEqual(Int(row[0]) ?? -1, season.month,
                           "Monat von \(season.skin.rawValue)")
            XCTAssertEqual(Int(row[1]) ?? -1, season.bit,
                           "Bit von \(season.skin.rawValue)")
            XCTAssertEqual(Int(row[2]) ?? -1, season.requiredDays,
                           "Tage für \(season.skin.rawValue)")
        }

        let expected = try vectors.strings("season.forMonth")
        let actual = (1...12).map { Season.forMonth($0)?.skin.rawValue ?? "-" }
        XCTAssertEqual(expected, actual, "Saison je Kalendermonat")
    }

    func testSkinCells() throws {
        // Dieselben Felder wie in ParityVectors.kt (:core).
        let cells: [(Int, Int)] = [
            (2, 6), (4, 3), (6, 2), (6, 6), (8, 5), (9, 8), (6, 10), (10, 6)
        ]

        var index = 0
        while vectors.has("skin.state.\(index)") {
            let stateRow = try vectors.strings("skin.state.\(index)")
            let state = SkinPaint.State(
                elapsed: CGFloat(Float(stateRow[0]) ?? 0),
                score: Int(stateRow[1]) ?? 0,
                perfectStreak: Int(stateRow[2]) ?? 0,
                hour: Int(stateRow[3]) ?? 12,
                month: Int(stateRow[4]) ?? 6
            )

            for skin in DotSkin.allCases {
                let row = try vectors.strings("skin.cells.\(index).\(skin.rawValue)")
                XCTAssertEqual(row.count, cells.count,
                               "skin.cells.\(index).\(skin.rawValue)")
                for (position, cell) in cells.enumerated() where position < row.count {
                    assertColor(
                        ParityVectors.color(row[position]),
                        SkinPaint.cell(skin, cell.0, cell.1, state),
                        "\(skin.rawValue) Feld (\(cell.0),\(cell.1)) Zustand \(index)"
                    )
                }
                assertColor(
                    ParityVectors.color(try vectors.string("skin.shine.\(index).\(skin.rawValue)")),
                    SkinPaint.shine(skin, state),
                    "\(skin.rawValue).shine Zustand \(index)"
                )
            }
            index += 1
        }
        XCTAssertGreaterThan(index, 0, "keine Skin-Zustände in der Datei gefunden")
    }

    func testSkinUnlocks() throws {
        var probe = 0
        while vectors.has("skin.probe.\(probe)") {
            let key = "skin.unlocked.\(probe)"
            let p = try vectors.strings("skin.probe.\(probe)")
            XCTAssertEqual(p.count, 9, "skin.probe.\(probe): neun Felder erwartet")
            let stats = DotSkin.Stats(
                bestScore: Int(p[0]) ?? 0,
                bestPerfectStreak: Int(p[1]) ?? 0,
                bestDailyStreak: Int(p[2]) ?? 0,
                runCount: Int(p[3]) ?? 0,
                totalScore: Int(p[4]) ?? 0,
                daysPlayed: Int(p[5]) ?? 0,
                monthsPlayed: Int(p[6]) ?? 0,
                seasonEarned: Int(p[7]) ?? 0,
                patronOwned: p[8] == "1"
            )
            probe += 1
            let row = try vectors.strings(key)
            let open = DotSkin.allCases.filter { $0.isUnlocked(stats) }.map { $0.rawValue }
            // Erste Zahl ist der Sammlungsstand (ohne Saison und Gönner),
            // danach steht jeder offene Skin.
            XCTAssertEqual(Int(row[0]) ?? -1, DotSkin.unlockedCount(stats),
                           "Sammlungsstand bei \(key)")
            XCTAssertEqual(Array(row.dropFirst()), open, "offene Skins bei \(key)")
        }
        XCTAssertGreaterThan(probe, 0, "keine Freischalt-Proben in der Datei")
    }

    // MARK: - Kulissen

    /// Die zweite Sammlung: Farben, Requisiten und Freischaltung.
    func testScenes() throws {
        XCTAssertEqual(try vectors.strings("scene.order"),
                       SceneId.allCases.map { $0.rawValue },
                       "Reihenfolge der Kulissen (auch sie ist der gespeicherte Wert)")
        XCTAssertEqual(Float(ScenePaint.groundTop), try vectors.float("scene.groundTop"),
                       accuracy: eps, "Bodenkante")
        XCTAssertEqual(try vectors.int("scene.propSlots"), ScenePaint.propSlots,
                       "Requisiten-Plätze")
        XCTAssertEqual(Float(ScenePaint.minZoneDistance),
                       try vectors.float("scene.minZoneDistance"),
                       accuracy: eps, "Mindestabstand zur Zone")
        XCTAssertEqual(Float(ScenePaint.minSkyStep), try vectors.float("scene.minSkyStep"),
                       accuracy: eps, "Himmels-Schrittweite")

        for id in SceneId.allCases {
            let scene = ScenePaint.of(id)
            let name = id.rawValue

            let sky = try vectors.strings("scene.sky.\(name)")
            XCTAssertEqual(sky.count, scene.sky.count, "\(name): Zahl der Himmelsstufen")
            for (index, token) in sky.enumerated() where index < scene.sky.count {
                assertColor(ParityVectors.color(token), scene.sky[index],
                            "\(name) Himmelsstufe \(index)")
            }

            let cloud = try vectors.string("scene.cloud.\(name)")
            if cloud == "-" {
                XCTAssertNil(scene.cloud, "\(name): keine Wolken")
            } else if let actual = scene.cloud {
                assertColor(ParityVectors.color(cloud), actual, "\(name) Wolkenfarbe")
            } else {
                XCTFail("\(name): Wolkenfarbe erwartet")
            }

            let ground = try vectors.strings("scene.ground.\(name)")
            if ground[0] == "-" {
                XCTAssertNil(scene.ground, "\(name): kein Boden")
            } else if let actual = scene.ground {
                assertColor(ParityVectors.color(ground[0]), actual.sand, "\(name) Sand")
                assertColor(ParityVectors.color(ground[1]), actual.sandShade,
                            "\(name) Sandschatten")
                assertColor(ParityVectors.color(ground[2]), actual.turfDark,
                            "\(name) Grasnarbe dunkel")
                assertColor(ParityVectors.color(ground[3]), actual.turfLight,
                            "\(name) Grasnarbe hell")
            } else {
                XCTFail("\(name): Boden erwartet")
            }

            let chips = try vectors.strings("scene.chips.\(name)")
            let actualChips = ScenePaint.chips(id)
            XCTAssertEqual(chips.count, actualChips.count, "\(name): Zahl der Kachelfarben")
            for (index, token) in chips.enumerated() where index < actualChips.count {
                assertColor(ParityVectors.color(token), actualChips[index],
                            "\(name) Kachelfarbe \(index)")
            }

            // Zahl der Requisiten: Ohne diese Zeile wuerde die Schleife
            // ueber die Swift-Seite laufen und ein fehlendes Stueck
            // stillschweigend uebergehen.
            XCTAssertFalse(vectors.has("scene.prop.\(name).\(scene.props.count)"),
                           "\(name): der Port hat weniger Requisiten als :core")
            for (index, prop) in scene.props.enumerated() {
                let row = try vectors.strings("scene.prop.\(name).\(index)")
                XCTAssertEqual(row.count, 9, "scene.prop.\(name).\(index)")
                XCTAssertEqual(row[0], ParityTests.name(prop.shape),
                               "\(name).\(index) Form")
                XCTAssertEqual(Float(prop.size), Float(row[1]) ?? -1, accuracy: eps,
                               "\(name).\(index) Größe")
                XCTAssertEqual(Float(prop.sway), Float(row[2]) ?? -1, accuracy: eps,
                               "\(name).\(index) Schwingen")
                assertColor(ParityVectors.color(row[3]), prop.dark, "\(name).\(index) dunkel")
                assertColor(ParityVectors.color(row[4]), prop.body, "\(name).\(index) Körper")
                assertColor(ParityVectors.color(row[5]), prop.light, "\(name).\(index) hell")
                assertColor(ParityVectors.color(row[6]), prop.stem, "\(name).\(index) Stiel")
                assertColor(ParityVectors.color(row[7]), prop.stemShade,
                            "\(name).\(index) Stielschatten")
                let accents = row[8] == "-" ? [] : row[8].split(separator: ",").map(String.init)
                XCTAssertEqual(accents.count, prop.accents.count,
                               "\(name).\(index) Zahl der Akzente")
                for (k, token) in accents.enumerated() where k < prop.accents.count {
                    assertColor(ParityVectors.color(token), prop.accents[k],
                                "\(name).\(index) Akzent \(k)")
                }
            }
        }

        let skyForScore = try vectors.strings("scene.skyForScore.WIESE")
        for (index, token) in skyForScore.enumerated() {
            assertColor(ParityVectors.color(token),
                        ScenePaint.skyFor(.wiese, score: index * 5),
                        "WIESE Himmel bei Score \(index * 5)")
        }
    }

    // MARK: - Ziele

    /// Die Ziele der Statistik-Seite. Reihenfolge inklusive: Das erste
    /// Ziel ist das, was im Game-Over steht.
    func testProgress() throws {
        XCTAssertEqual(try vectors.int("progress.pageGoals"), Progress.pageGoals)
        XCTAssertEqual(try vectors.int("progress.barBlocks"), Progress.barBlocks)

        let fractions = try vectors.strings("progress.fractions")
        let blocks = try vectors.strings("progress.filledBlocks")
        for (index, token) in fractions.enumerated() where index < blocks.count {
            XCTAssertEqual(Progress.filledBlocks(CGFloat(Float(token) ?? 0)),
                           Int(blocks[index]) ?? -1,
                           "gefüllte Blöcke bei Anteil \(token)")
        }

        var probe = 0
        while vectors.has("progress.probe.\(probe)") {
            let p = try vectors.strings("progress.probe.\(probe)")
            XCTAssertEqual(p.count, 11, "progress.probe.\(probe): elf Felder erwartet")
            let stats = DotSkin.Stats(
                bestScore: Int(p[0]) ?? 0,
                bestPerfectStreak: Int(p[1]) ?? 0,
                bestDailyStreak: Int(p[2]) ?? 0,
                runCount: Int(p[3]) ?? 0,
                totalScore: Int(p[4]) ?? 0,
                daysPlayed: Int(p[5]) ?? 0,
                monthsPlayed: Int(p[6]) ?? 0,
                seasonEarned: Int(p[7]) ?? 0,
                patronOwned: p[8] == "1"
            )
            let month = Int(p[9]) ?? 0
            let seasonDays = Int(p[10]) ?? 0

            let goals = Progress.goals(stats, month: month, seasonDays: seasonDays)
            let expected = try vectors.strings("progress.goals.\(probe)")
            XCTAssertEqual(goals.count, Int(expected[0]) ?? -1,
                           "Zahl der Ziele bei Probe \(probe)")
            XCTAssertEqual(goals.map { ParityTests.token($0) },
                           Array(expected.dropFirst()),
                           "Ziele bei Probe \(probe)")

            let next = Progress.nextGoal(stats, month: month, seasonDays: seasonDays)
            let expectedNext = try vectors.strings("progress.next.\(probe)")
            if expectedNext[0] == "-" {
                XCTAssertNil(next, "kein nächstes Ziel bei Probe \(probe)")
            } else if let next = next {
                XCTAssertEqual(ParityTests.token(next), expectedNext[0],
                               "nächstes Ziel bei Probe \(probe)")
                XCTAssertEqual(next.remaining, Int(expectedNext[1]) ?? -1,
                               "Rest bis zum Ziel bei Probe \(probe)")
                XCTAssertEqual(Float(next.fraction), Float(expectedNext[2]) ?? -1,
                               accuracy: eps, "Anteil bei Probe \(probe)")
            } else {
                XCTFail("nächstes Ziel erwartet bei Probe \(probe)")
            }
            probe += 1
        }
        XCTAssertGreaterThan(probe, 0, "keine Ziel-Proben in der Datei")
    }

    // MARK: - Zufallsgenerator

    /// Der wichtigste Test des Ports: Weicht KotlinRandom auch nur in
    /// einer Zahl ab, spielt iOS eine andere Daily Challenge als Android.
    func testKotlinRandomStream() throws {
        let seed = try vectors.int64("rng.seed")

        let ints = try vectors.strings("rng.nextInt").map { Int32($0) ?? 0 }
        var random = KotlinRandom(seed: seed)
        XCTAssertEqual(ints.map { _ in random.nextInt() }, ints, "nextInt-Folge")

        random = KotlinRandom(seed: seed)
        for (index, expected) in try vectors.strings("rng.nextFloat").enumerated() {
            XCTAssertEqual(random.nextFloat(), Float(expected) ?? 0, accuracy: 1e-6,
                           "nextFloat #\(index)")
        }

        random = KotlinRandom(seed: seed)
        for (index, expected) in try vectors.strings("rng.nextBoolean").enumerated() {
            XCTAssertEqual(random.nextBoolean(), expected == "1", "nextBoolean #\(index)")
        }

        for bound in [5, 8] {
            random = KotlinRandom(seed: seed)
            for (index, expected) in try vectors.strings("rng.nextIntBound\(bound)").enumerated() {
                XCTAssertEqual(Int(random.nextInt(bound: Int32(bound))), Int(expected) ?? -1,
                               "nextInt(\(bound)) #\(index)")
            }
        }

        // shuffled() über die Twist-Liste — genau die Operation, mit der
        // chooseTwists() entscheidet, was auf der Bahn passiert.
        random = KotlinRandom(seed: seed)
        var round = 0
        while vectors.has("rng.shuffleTwists.\(round)") {
            var twists = TimingGame.Twist.allCases
            random.shuffle(&twists)
            XCTAssertEqual(twists.map { ParityTests.name($0) },
                           try vectors.strings("rng.shuffleTwists.\(round)"),
                           "shuffle-Runde \(round)")
            round += 1
        }
        XCTAssertGreaterThan(round, 0, "keine shuffle-Runden in der Datei")

        // Und derselbe Generator aus einem echten Tages-Seed.
        let daily = KotlinRandom(seed: DailyChallenge.seedFor(epochDay: 19947))
        for (index, expected) in try vectors.strings("rng.dailyFloats").enumerated() {
            XCTAssertEqual(daily.nextFloat(), Float(expected) ?? 0, accuracy: 1e-6,
                           "Tages-Seed nextFloat #\(index)")
        }
    }

    // MARK: - Ganze Läufe

    func testPerfectRunTrace() throws {
        try assertTrace(prefix: "trace.perfect", seed: try vectors.int64("rng.seed"))
    }

    func testSecondRunTrace() throws {
        try assertTrace(prefix: "trace.second", seed: try vectors.int64("rng.seed") + 7)
    }

    func testPassiveRunDiesTheSameWay() throws {
        let row = try vectors.strings("trace.death")
        let dt = try vectors.float("trace.dt")
        let game = TimingGame(random: KotlinRandom(seed: try vectors.int64("rng.seed")))
        _ = game.tap()

        var frames = 0
        var angle = game.angle
        var zoneCenter = game.zoneCenter
        while game.phase == .running && frames < 100_000 {
            frames += 1
            angle = game.angle
            zoneCenter = game.zoneCenter
            _ = game.update(deltaSeconds: dt)
        }
        var settle = 0
        while game.phase == .dying && settle < 100_000 {
            settle += 1
            _ = game.update(deltaSeconds: dt)
        }

        XCTAssertEqual(frames, Int(row[0]) ?? -1, "Frames bis zum Überfahren-Tod")
        XCTAssertEqual(settle, Int(row[1]) ?? -1, "Frames von DYING nach OVER")
        XCTAssertEqual(angle, Float(row[2]) ?? 0, accuracy: epsDrift, "Winkel beim Tod")
        XCTAssertEqual(zoneCenter, Float(row[3]) ?? 0, accuracy: epsDrift, "Zone beim Tod")
    }

    /// Spielt den Lauf des Bots aus :core nach (ParityBot.kt) und
    /// vergleicht jeden Treffer.
    private func assertTrace(prefix: String, seed: Int64) throws {
        let dt = try vectors.float("trace.dt")
        let expectedHits = try vectors.int("\(prefix).hits")

        // Identisch zu ParityBot: tief im Perfekt-Kern tappen, damit keine
        // Entscheidung am letzten Bit hängt.
        let tapWindow = TimingGame.perfectShare * TimingGame.pulseMinShare * 0.5

        let game = TimingGame(random: KotlinRandom(seed: seed))
        _ = game.tap()

        var hit = 0
        var frames = 0
        while hit < expectedHits && frames < 100_000 {
            frames += 1
            _ = game.update(deltaSeconds: dt)
            guard game.phase == .running else { break }
            if abs(game.relativeToZone()) <= game.zoneHalfWidth * tapWindow {
                _ = game.tap()
                let row = try vectors.strings("\(prefix).\(hit)")
                XCTAssertEqual(row.count, 11, "\(prefix).\(hit): unerwartete Spaltenzahl")
                XCTAssertEqual(game.score, Int(row[0]) ?? -1, "\(prefix).\(hit) Score")
                XCTAssertEqual(game.hits, Int(row[1]) ?? -1, "\(prefix).\(hit) Treffer")
                XCTAssertEqual(game.perfectStreak, Int(row[2]) ?? -1, "\(prefix).\(hit) Serie")
                XCTAssertEqual(game.lastHitPoints, Int(row[3]) ?? -1, "\(prefix).\(hit) Punkte")
                XCTAssertEqual(game.direction, Int(row[4]) ?? -1, "\(prefix).\(hit) Richtung")
                XCTAssertEqual(twistLabel(game), row[5], "\(prefix).\(hit) Twists")
                XCTAssertEqual(game.zoneCenter, Float(row[6]) ?? 0, accuracy: epsDrift,
                               "\(prefix).\(hit) Zonenmitte")
                XCTAssertEqual(game.zoneHalfWidth, Float(row[7]) ?? 0, accuracy: eps,
                               "\(prefix).\(hit) Zonenbreite")
                XCTAssertEqual(game.angle, Float(row[8]) ?? 0, accuracy: epsDrift,
                               "\(prefix).\(hit) Winkel")
                XCTAssertEqual(game.chainRemaining, Int(row[9]) ?? -1, "\(prefix).\(hit) Kette")
                if row[10] == "-" {
                    XCTAssertFalse(game.hasFakeZone, "\(prefix).\(hit) keine Köder-Zone")
                } else {
                    XCTAssertTrue(game.hasFakeZone, "\(prefix).\(hit) Köder-Zone erwartet")
                    XCTAssertEqual(game.fakeZoneCenter, Float(row[10]) ?? 0, accuracy: epsDrift,
                                   "\(prefix).\(hit) Köder-Zone")
                }
                hit += 1
            }
        }
        XCTAssertEqual(hit, expectedHits, "\(prefix): Lauf endete zu früh")
    }

    // MARK: - Hilfen

    private func twistLabel(_ game: TimingGame) -> String {
        let names = game.activeTwists.map { ParityTests.name($0) }.sorted()
        return names.isEmpty ? "-" : names.joined(separator: "+")
    }

    /// Verglichen werden nur Rot, Grün und Blau — **ohne Alpha**.
    ///
    /// Die beiden Ports stellen Farben verschieden dar: Kotlin führt sie
    /// als ARGB-Long (`0xFFRRGGBB`), damit `:core` ohne Compose-Typen
    /// auskommt; der Swift-Port führt sie als 24-Bit-RGB (`0xRRGGBB`) und
    /// setzt die Deckkraft erst beim Erzeugen der `UIColor`. Alle Werte
    /// in `:core` sind vollflächig deckend, es geht also nichts verloren.
    ///
    /// Toleranz ±2 pro Kanal: Der Swift-Port rechnet die Skin-Muster in
    /// CGFloat (Double), Kotlin in Float — das darf eine Rundung kosten.
    private func assertColor(_ expected: UInt32, _ actual: UInt32, _ label: String) {
        for shift in [16, 8, 0] {
            let a = Int((expected >> UInt32(shift)) & 0xFF)
            let b = Int((actual >> UInt32(shift)) & 0xFF)
            XCTAssertLessThanOrEqual(
                abs(a - b), 2,
                "\(label): erwartet \(String(expected, radix: 16)), "
                    + "bekommen \(String(actual, radix: 16))"
            )
        }
    }

    private static func name(_ twist: TimingGame.Twist) -> String {
        switch twist {
        case .pulse: return "PULSE"
        case .drift: return "DRIFT"
        case .ghost: return "GHOST"
        case .fake: return "FAKE"
        case .chain: return "CHAIN"
        }
    }

    /// Ein Ziel als dasselbe Wort wie in ParityVectors.kt.
    private static func token(_ goal: Goal) -> String {
        let subject = goal.skin.map { "SKIN:\($0.rawValue)" }
            ?? "SCENE:\(goal.scene?.rawValue ?? "?")"
        return "\(subject)|\(name(goal.axis))|\(goal.current)|\(goal.target)"
    }

    private static func name(_ axis: GoalAxis) -> String {
        switch axis {
        case .bestScore: return "BEST_SCORE"
        case .perfectStreak: return "PERFECT_STREAK"
        case .dailyStreak: return "DAILY_STREAK"
        case .runCount: return "RUN_COUNT"
        case .totalScore: return "TOTAL_SCORE"
        case .daysPlayed: return "DAYS_PLAYED"
        case .monthsPlayed: return "MONTHS_PLAYED"
        case .seasonDays: return "SEASON_DAYS"
        case .skinCollection: return "SKIN_COLLECTION"
        case .sceneCollection: return "SCENE_COLLECTION"
        }
    }

    private static func name(_ shape: PropShape) -> String {
        switch shape {
        case .baum: return "BAUM"
        case .blume: return "BLUME"
        case .strauch: return "STRAUCH"
        case .kaktus: return "KAKTUS"
        case .welle: return "WELLE"
        case .nadelbaum: return "NADELBAUM"
        case .hochhaus: return "HOCHHAUS"
        case .fels: return "FELS"
        }
    }

    private static func name(_ tier: MedalTier) -> String {
        switch tier {
        case .bronze: return "BRONZE"
        case .silver: return "SILVER"
        case .gold: return "GOLD"
        case .platinum: return "PLATINUM"
        }
    }
}

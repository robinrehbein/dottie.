import SpriteKit
import UIKit

/// Die komplette Spiel-Szene: Welt-Rendering (Himmel, Wolken, Szenerie,
/// Boden, Perlenketten-Bahn, Pixel-Vogel), Game-Loop und Overlays —
/// das SpriteKit-Pendant zu TimingGameScreen.kt.
///
/// Koordinaten: Die Android-Formeln rechnen mit y nach unten; SpriteKit
/// hat y nach oben. Alle Welt-Positionen werden deshalb über
/// `flipY(_:)` gespiegelt, die Formeln selbst bleiben unverändert.
final class GameScene: SKScene {

    // MARK: - Engine & Dienste

    private let game = TimingGame()
    private let store = ScoreStore()
    private let audio = GameAudio()
    private let haptics = GameHaptics()

    // MARK: - Lauf-/Effekt-Zustand (FxState/BannerState/RunState-Port)

    private var flashAlpha: CGFloat = 0
    private var shakeTime: CGFloat = 0
    private var celebrateTime: CGFloat = 0
    /// Sekunden seit dem Tod (Mario-Hüpfer), negativ = kein Tod aktiv.
    private var deathTime: CGFloat = -1

    private var bannerTimeLeft: CGFloat = 0
    private var bannerPriority = 0
    private var lastStage = 0
    private var recordCelebrated = false

    private var runEpochDay: Int64 = 0
    private var runMaxPerfect = 0

    private var dailyMode = false
    private var bestScore = 0
    private var isNewRecord = false
    private var taunt = ""
    private var skinUnlockedThisRun = false
    private var newMedalThisRun = false
    private var skin: DotSkin = .klassik

    private var lastUpdateTime: TimeInterval = 0
    private var lastPhase: TimingGame.Phase = .over // erzwingt READY-Setup im 1. Frame

    /// Alle Effekte auf den Ruhezustand (FxState.reset am Phone) — nötig
    /// überall dort, wo ein Lauf endet, ohne dass gleich der nächste
    /// startet. Vor allem `deathTime`: Bliebe der Sturz aktiv, wäre der
    /// Vogel im READY-Bild längst unten aus dem Kader gefallen.
    private func resetFx() {
        flashAlpha = 0
        shakeTime = 0
        celebrateTime = 0
        deathTime = -1
    }

    // MARK: - Layout-Konstanten (aus TimingGameScreen.kt)

    private static let celebrateSeconds: CGFloat = 1.1
    private static let deathHopSpeed: CGFloat = 1.6
    private static let deathGravity: CGFloat = 6
    private static let deathFlipSeconds: CGFloat = 0.3
    private static let segmentCount = 60
    private static let sparkCount = 20

    private var cell: CGFloat = 2
    private var trackCenter = CGPoint.zero // SpriteKit-Koordinaten
    private var trackRadius: CGFloat = 100
    private var birdRadius: CGFloat = 12

    // MARK: - Nodes

    private let worldNode = SKNode()
    private var skyNode = SKSpriteNode()
    private var cloudNodes: [SKSpriteNode] = []
    private var sceneryNodes: [SKSpriteNode] = []
    private var segmentOutlines: [SKSpriteNode] = []
    private var segmentInners: [SKSpriteNode] = []
    private var burstGlowNode = SKSpriteNode()
    private var burstSparks: [SKSpriteNode] = []
    private var birdNode = SKSpriteNode()
    private var birdTextureLeft: SKTexture?
    private var birdTextureRight: SKTexture?

    /// Zustand, für den die Vogel-Texturen zuletzt gerastert wurden.
    /// Bewegte Skins werden nur bei einem Wechsel neu gezeichnet (siehe
    /// SkinPaint.frameKey) statt 60-mal pro Sekunde.
    private var birdTextureKey = -1

    /// Nachbilder auf der Bahn für Schweif-Skins (Tinte).
    private var trailNodes: [SKSpriteNode] = []
    private var flashNode = SKSpriteNode()

    private var hud: HudOverlay?
    private var readyOverlay: ReadyOverlay?
    private var overOverlay: GameOverOverlay?
    private var helpOverlay: HelpOverlay?
    private var skinOverlay: SkinOverlay?

    // MARK: - Aufbau

    override func didMove(to view: SKView) {
        backgroundColor = Palette.skyStages[0]
        skin = store.selectedSkin
        audio.muted = store.soundMuted
        bestScore = store.bestScore

        let w = size.width
        let h = size.height
        cell = max(2, floor(h / 220))
        trackCenter = CGPoint(x: w / 2, y: flipY(h * 0.44))
        trackRadius = min(w * 0.36, h * 0.28)
        birdRadius = h * 0.026

        addChild(worldNode)
        buildWorld()
        buildOverlays(safeInsets: view.safeAreaInsets)
        rebuildBirdTextures()
        enterReady()
        // Bei jedem Start neu planen: Die Erinnerungen sind im Voraus
        // gesetzt, und ob heute schon gespielt wurde, weiss erst dieser
        // Moment (siehe DailyReminder).
        DailyReminder.refresh(store: store)
    }

    private func flipY(_ yTopDown: CGFloat) -> CGFloat {
        return size.height - yTopDown
    }

    private func buildWorld() {
        let w = size.width
        let h = size.height

        skyNode = SKSpriteNode(color: Palette.skyStages[0], size: CGSize(width: w + 80, height: h + 80))
        skyNode.position = CGPoint(x: w / 2, y: h / 2)
        skyNode.zPosition = 0
        worldNode.addChild(skyNode)

        // Langsam driftende Wolken.
        let cloudTexture = PixelArt.cloudTexture(cell: cell)
        let cloudTopsA: [CGFloat] = [h * 0.16 - cell * 3, h * 0.24 - cell * 3]
        for topA in cloudTopsA {
            let cloud = SKSpriteNode(texture: cloudTexture)
            cloud.anchorPoint = CGPoint(x: 0, y: 1)
            cloud.position = CGPoint(x: 0, y: flipY(topA))
            cloud.zPosition = 1
            worldNode.addChild(cloud)
            cloudNodes.append(cloud)
        }

        // Szenerie: Bäume, Blumen, Sträucher vor dem Boden (Parallaxe).
        let spacing = w * 0.26
        let count = Int(w / spacing) + 3
        let groundBaselineA = h * 0.88 + cell * 2
        let treeBig = PixelArt.treeTexture(s: h * 0.075, cell: cell)
        let treeSmall = PixelArt.treeTexture(s: h * 0.058, cell: cell)
        let flowerRed = PixelArt.flowerTexture(s: h * 0.032, cell: cell, petal: Palette.recordRed)
        let flowerWhite = PixelArt.flowerTexture(s: h * 0.032, cell: cell, petal: Palette.cloud)
        let bush = PixelArt.bushTexture(s: h * 0.026, cell: cell)
        for k in 0..<count {
            let texture: SKTexture
            switch k % 4 {
            case 0:
                texture = treeBig
            case 1:
                texture = (k / 4) % 2 == 0 ? flowerRed : flowerWhite
            case 2:
                texture = treeSmall
            default:
                texture = bush
            }
            let node = SKSpriteNode(texture: texture)
            node.anchorPoint = CGPoint(x: 0.5, y: 0)
            node.position = CGPoint(x: 0, y: flipY(groundBaselineA))
            node.zPosition = 2
            worldNode.addChild(node)
            sceneryNodes.append(node)
        }

        // Boden-Streifen (verdeckt die Wurzeln der Szenerie).
        let ground = SKSpriteNode(texture: PixelArt.groundTexture(
            width: w, sandHeight: h * 0.12, cell: cell
        ))
        ground.anchorPoint = CGPoint(x: 0, y: 0)
        ground.position = CGPoint(x: 0, y: 0)
        ground.zPosition = 3
        worldNode.addChild(ground)

        // Perlenketten-Bahn: 60 Segmente, jedes als Outline + Innenblock.
        for k in 0..<GameScene.segmentCount {
            let a = CGFloat(k) / CGFloat(GameScene.segmentCount) * 2 * CGFloat.pi
            let position = CGPoint(
                x: trackCenter.x + cos(a) * trackRadius,
                y: trackCenter.y - sin(a) * trackRadius
            )
            let outline = SKSpriteNode(color: Palette.outline, size: CGSize(width: cell * 3, height: cell * 3))
            outline.position = position
            outline.zPosition = 4
            worldNode.addChild(outline)
            segmentOutlines.append(outline)

            let inner = SKSpriteNode(color: Palette.groundSandShade, size: CGSize(width: cell * 1.8, height: cell * 1.8))
            inner.position = position
            inner.zPosition = 4.1
            worldNode.addChild(inner)
            segmentInners.append(inner)
        }

        // Freischalt-Zelebration: Goldschimmer + zwei Pixel-Ringe.
        // Android zeichnet den Burst NACH dem Vogel — deshalb über z=6.
        burstGlowNode = SKSpriteNode(color: Palette.dotBody, size: CGSize(width: w + 80, height: h + 80))
        burstGlowNode.position = CGPoint(x: w / 2, y: h / 2)
        burstGlowNode.zPosition = 7
        burstGlowNode.alpha = 0
        worldNode.addChild(burstGlowNode)
        for ring in 0..<2 {
            for _ in 0..<GameScene.sparkCount {
                let spark = SKSpriteNode(
                    color: ring == 0 ? Palette.dotBody : Palette.dotShine,
                    size: CGSize(width: cell * 3, height: cell * 3)
                )
                spark.zPosition = 7.1
                spark.alpha = 0
                worldNode.addChild(spark)
                burstSparks.append(spark)
            }
        }

        // Nachbilder liegen unter dem Vogel und sind normalerweise unsichtbar.
        trailNodes = (0..<SkinPaint.trailSteps).map { _ in
            let node = SKSpriteNode()
            node.size = CGSize(width: birdRadius * 2, height: birdRadius * 2)
            node.zPosition = 5
            node.alpha = 0
            node.isHidden = true
            worldNode.addChild(node)
            return node
        }

        birdNode = SKSpriteNode()
        birdNode.size = CGSize(width: birdRadius * 2, height: birdRadius * 2)
        birdNode.zPosition = 6
        worldNode.addChild(birdNode)

        flashNode = SKSpriteNode(color: .white, size: CGSize(width: w + 80, height: h + 80))
        flashNode.position = CGPoint(x: w / 2, y: h / 2)
        flashNode.zPosition = 50
        flashNode.alpha = 0
        addChild(flashNode)
    }

    private func buildOverlays(safeInsets: UIEdgeInsets) {
        let safeTop = safeInsets.top
        let safeBottom = safeInsets.bottom

        let hud = HudOverlay(sceneSize: size, safeTop: safeTop)
        hud.zPosition = 100
        hud.isHidden = true
        addChild(hud)
        self.hud = hud

        let ready = ReadyOverlay(sceneSize: size, safeTop: safeTop, safeBottom: safeBottom)
        ready.zPosition = 100
        addChild(ready)
        self.readyOverlay = ready

        let over = GameOverOverlay(sceneSize: size, safeTop: safeTop)
        over.zPosition = 100
        over.isHidden = true
        addChild(over)
        self.overOverlay = over

        let help = HelpOverlay(sceneSize: size)
        help.zPosition = 300
        help.isHidden = true
        addChild(help)
        self.helpOverlay = help

        let skins = SkinOverlay(sceneSize: size)
        skins.zPosition = 300
        skins.isHidden = true
        addChild(skins)
        self.skinOverlay = skins
    }

    private func rebuildBirdTextures(state: SkinPaint.State = .still) {
        birdTextureLeft = PixelArt.birdTexture(
            skin: skin, facingLeft: true, diameter: birdRadius * 2, state: state
        )
        birdTextureRight = PixelArt.birdTexture(
            skin: skin, facingLeft: false, diameter: birdRadius * 2, state: state
        )
        birdTextureKey = SkinPaint.frameKey(skin, state)
        birdNode.texture = birdTextureRight
    }

    // MARK: - Lauf-Vorbereitung & Banner

    /// Vor jedem Lauf-Start: Tag fixieren und die Zufallsquelle passend zum
    /// Modus setzen — die Daily bekommt den Tages-Seed.
    private func prepareRun() {
        let today = DailyChallenge.todayEpochDay()
        runEpochDay = today
        game.reseed(dailyMode ? DailyChallenge.seedFor(epochDay: today) : nil)
    }

    /// Banner mit Priorität: Ein wichtigeres Banner (Twist-Ankündigung)
    /// wird nicht von einem beiläufigen ("NOCH EINE!") überschrieben.
    private func showBanner(_ text: String, seconds: CGFloat, priority: Int) {
        if bannerTimeLeft > 0 && bannerPriority > priority {
            return
        }
        hud?.bannerLabel.text = text
        bannerTimeLeft = seconds
        bannerPriority = priority
    }

    private func twistBannerText(_ twist: TimingGame.Twist) -> String {
        switch twist {
        case .pulse: return L10n.text("banner_twist_pulse")
        case .drift: return L10n.text("banner_twist_drift")
        case .ghost: return L10n.text("banner_twist_ghost")
        case .fake: return L10n.text("banner_twist_fake")
        case .chain: return L10n.text("banner_twist_chain")
        }
    }

    // MARK: - Game-Loop

    override func update(_ currentTime: TimeInterval) {
        let dt: CGFloat
        if lastUpdateTime == 0 {
            dt = 0
        } else {
            dt = CGFloat(currentTime - lastUpdateTime)
        }
        lastUpdateTime = currentTime

        let events = game.update(deltaSeconds: Float(dt))
        flashAlpha = max(flashAlpha - dt * 3.5, 0)
        shakeTime = max(shakeTime - dt, 0)
        celebrateTime = max(celebrateTime - dt, 0)
        if deathTime >= 0 {
            deathTime += dt
        }
        bannerTimeLeft = max(bannerTimeLeft - dt, 0)

        var twistUnlockedThisFrame = false
        for event in events {
            handle(event: event, twistUnlockedThisFrame: &twistUnlockedThisFrame)
        }

        // Rekord live feiern: In dem Moment, in dem der Lauf den alten
        // Bestwert überholt — nicht erst beim Tod.
        if game.phase == .running && !recordCelebrated && bestScore > 0 && game.score > bestScore {
            recordCelebrated = true
            showBanner(L10n.text("banner_record"), seconds: 2.2, priority: 2)
            celebrateTime = GameScene.celebrateSeconds
            haptics.newRecord()
            audio.newRecord()
        }

        // Stufen-Feedback: jede 5er-Stufe färbt den Himmel um.
        let stage = game.score / 5
        if game.phase == .running && stage > lastStage {
            lastStage = stage
            if !twistUnlockedThisFrame {
                showBanner(L10n.text("banner_stage"), seconds: 1.6, priority: 1)
                celebrateTime = GameScene.celebrateSeconds
                haptics.unlock()
                audio.unlock()
            }
        }
        if game.phase == .ready {
            lastStage = 0
        }

        handlePhaseTransition()
        renderWorld()
        renderHud()
    }

    private func handle(event: TimingGame.GameEvent, twistUnlockedThisFrame: inout Bool) {
        switch event {
        case .started:
            // Auch beim Sofort-Neustart aus dem Game-Over: Banner,
            // Stufen-Zähler und Rekord-Feier auf Anfang.
            lastStage = 0
            recordCelebrated = false
            bannerTimeLeft = 0
            hud?.bannerLabel.text = ""
            runMaxPerfect = 0
            skinUnlockedThisRun = false
            newMedalThisRun = false
            deathTime = -1
            audio.start()
        case .hit:
            haptics.score()
            audio.hit(score: game.score)
        case .perfectHit:
            haptics.perfect()
            audio.perfect(streak: game.perfectStreak)
            hud?.perfectLabel.text = L10n.format("perfect_plus", game.lastHitPoints)
            runMaxPerfect = max(runMaxPerfect, game.perfectStreak)
        case .chainNext:
            showBanner(L10n.text("banner_chain"), seconds: 1.2, priority: 1)
            audio.chain()
        case .twistUnlocked(let twist):
            twistUnlockedThisFrame = true
            showBanner(twistBannerText(twist), seconds: 2.2, priority: 2)
            celebrateTime = GameScene.celebrateSeconds
            haptics.unlock()
            audio.unlock()
        case .died:
            haptics.death()
            audio.death()
            flashAlpha = 1
            shakeTime = 0.4
            celebrateTime = 0
            deathTime = 0
            let previousBest = store.bestScore
            newMedalThisRun = MedalTier.isUpgrade(score: game.score, previousBest: previousBest)
            let unlockedBefore = DotSkin.unlockedCount(store.stats())
            isNewRecord = store.submitRun(score: game.score)
            store.submitPerfectStreak(runMaxPerfect)
            if dailyMode {
                store.submitDailyRun(epochDay: runEpochDay, score: game.score)
                // Heute gespielt -> die heutige Erinnerung faellt weg.
                DailyReminder.refresh(store: store)
            }
            skinUnlockedThisRun = DotSkin.unlockedCount(store.stats()) > unlockedBefore
            taunt = L10n.pickTaunt(
                score: game.score, previousBest: previousBest, isNewRecord: isNewRecord
            )
            bestScore = store.bestScore
            if isNewRecord && !recordCelebrated {
                haptics.newRecord()
            }
        case .settled:
            haptics.thud()
            // Der Rekord-Jingle lief meist schon live im Lauf; sonst
            // (z. B. allererster Lauf) kommt er jetzt.
            if isNewRecord && !recordCelebrated {
                audio.newRecord()
            } else {
                audio.thud()
            }
        }
    }

    private func handlePhaseTransition() {
        let phase = game.phase
        if phase == lastPhase {
            return
        }
        lastPhase = phase
        switch phase {
        case .ready:
            enterReady()
        case .running:
            readyOverlay?.isHidden = true
            overOverlay?.isHidden = true
            hud?.isHidden = false
            hud?.dailyLabel.isHidden = !dailyMode
        case .dying:
            break
        case .over:
            hud?.isHidden = true
            overOverlay?.configure(
                score: game.score,
                bestScore: bestScore,
                isNewRecord: isNewRecord,
                taunt: taunt,
                daily: dailyMode,
                dailyBest: store.dailyBestFor(epochDay: runEpochDay),
                dailyStreak: store.dailyStreak,
                skinUnlocked: skinUnlockedThisRun,
                newMedal: newMedalThisRun
            )
            overOverlay?.isHidden = false
        }
    }

    private func enterReady() {
        hud?.isHidden = true
        overOverlay?.isHidden = true
        let today = DailyChallenge.todayEpochDay()
        readyOverlay?.refresh(
            bestScore: store.bestScore,
            runNumber: store.runCount,
            soundOn: !store.soundMuted,
            reminderOn: store.reminderEnabled,
            dailyBest: store.dailyBestFor(epochDay: today),
            dailyStreak: store.dailyStreakPreviewFor(epochDay: today)
        )
        readyOverlay?.isHidden = false
    }

    // MARK: - Welt-Rendering (Port von drawTimingWorld)

    private func renderWorld() {
        let w = size.width
        let h = size.height
        let elapsed = CGFloat(game.elapsed)

        // Screen-Shake beim Tod.
        if shakeTime > 0 {
            let strength = shakeTime * 28
            worldNode.position = CGPoint(
                x: sin(shakeTime * 91) * strength,
                y: -sin(shakeTime * 77) * strength
            )
        } else {
            worldNode.position = .zero
        }

        // Himmel färbt sich mit jeder 5er-Stufe weiter Richtung Nacht.
        let stageIndex = SkinPaint.skyStage(game.score)
        skyNode.color = Palette.skyStages[stageIndex]

        // Wolken driften nach links.
        let cloudDrift = elapsed * h * 0.01
        let cloudXs: [CGFloat] = [w * 0.1, w * 0.75]
        for (i, cloud) in cloudNodes.enumerated() {
            cloud.position.x = cloudXs[i] - cloudDrift.truncatingRemainder(dividingBy: w * 1.4)
        }

        // Szenerie mit Parallaxe und Wind-Sway.
        let spacing = w * 0.26
        let total = spacing * CGFloat(sceneryNodes.count)
        let sceneryDrift = elapsed * h * 0.016
        for (k, node) in sceneryNodes.enumerated() {
            let raw = (CGFloat(k) * spacing - sceneryDrift).truncatingRemainder(dividingBy: total)
            node.position.x = (raw + total).truncatingRemainder(dividingBy: total) - spacing
            node.zRotation = sin(elapsed * 1.4 + CGFloat(k) * 1.7) * 0.04
        }

        renderTrack()
        renderBird()
        renderBurst()

        flashNode.alpha = min(flashAlpha, 1)
    }

    /// Port von drawTrack: Zielzone grün mit hellem Perfekt-Kern,
    /// Fallen-Zone violett — alles im Pixel-Raster.
    private func renderTrack() {
        let segments = GameScene.segmentCount
        let zoneHalf = CGFloat(game.effectiveZoneHalf())
        let zoneCenter = CGFloat(game.zoneCenter)
        let baseHalf = CGFloat(game.zoneHalfWidth)
        let fakeCenter = CGFloat(game.fakeZoneCenter)
        let hasFake = game.hasFakeZone

        // Kern fürs Zeichnen auf mindestens einen Rasterschritt aufrunden.
        let coreHalf = max(
            zoneHalf * CGFloat(TimingGame.perfectShare),
            CGFloat.pi / CGFloat(segments)
        )

        for k in 0..<segments {
            let a = CGFloat(k) / CGFloat(segments) * 2 * CGFloat.pi
            let relativeZone = CGFloat(TimingGame.wrapToPi(Float(a) - Float(zoneCenter)))
            let inZone = abs(relativeZone) <= zoneHalf
            let inPerfectCore = abs(relativeZone) <= coreHalf

            let relativeFake = CGFloat(TimingGame.wrapToPi(Float(a) - Float(fakeCenter)))
            let inFake = hasFake && abs(relativeFake) <= baseHalf
            let inFakeCore = hasFake && abs(relativeFake) <= baseHalf * CGFloat(TimingGame.perfectShare)

            let highlighted = inZone || inFake
            let outer = highlighted ? cell * 5 : cell * 3
            let inner = highlighted ? cell * 3.4 : cell * 1.8

            let innerColor: UIColor
            if inPerfectCore {
                innerColor = Palette.grassLight
            } else if inZone {
                innerColor = Palette.grassDark
            } else if inFakeCore {
                innerColor = Palette.fakeZoneCore
            } else if inFake {
                innerColor = Palette.fakeZone
            } else {
                innerColor = Palette.groundSandShade
            }

            segmentOutlines[k].size = CGSize(width: outer, height: outer)
            segmentInners[k].size = CGSize(width: inner, height: inner)
            segmentInners[k].color = innerColor
        }
    }

    /// Port von drawTimingDot inkl. Mario-Tod (Hüpfer + Kopfüber-Fall).
    private func renderBird() {
        let h = size.height
        let angle = CGFloat(game.angle)
        let px = trackCenter.x + cos(angle) * trackRadius
        var pyA = (h - trackCenter.y) + sin(angle) * trackRadius // Android-y
        let r = birdRadius

        var flip: CGFloat = 0
        if deathTime >= 0 {
            let t = deathTime - CGFloat(TimingGame.deathFreezeSeconds)
            if t > 0 {
                pyA += (-GameScene.deathHopSpeed * t + 0.5 * GameScene.deathGravity * t * t) * h
                if pyA - r * 2 > h {
                    birdNode.isHidden = true
                    return
                }
                flip = CGFloat.pi * min(t / GameScene.deathFlipSeconds, 1)
            }
        }

        // Bewegte und reagierende Skins: neu rastern, sobald sich das Bild
        // wirklich ändert — nicht in jedem Frame.
        let state = SkinPaint.State(
            elapsed: CGFloat(game.elapsed),
            score: game.score,
            perfectStreak: game.perfectStreak
        )
        if SkinPaint.frameKey(skin, state) != birdTextureKey {
            rebuildBirdTextures(state: state)
        }

        birdNode.isHidden = !game.isDotVisible
        birdNode.position = CGPoint(x: px, y: flipY(pyA))
        birdNode.zRotation = flip

        // Glanzpunkt und Auge folgen der sichtbaren Flugrichtung.
        let facingLeft = sin(Float(angle)) * Float(game.direction) > 0
        let texture = facingLeft ? birdTextureLeft : birdTextureRight
        birdNode.texture = texture

        // Schweif-Skins (Tinte) lassen Nachbilder auf der Bahn zurück. Die
        // Positionen werden wie in den anderen Ports aus dem Winkel
        // zurückgerechnet, es braucht also keinen eigenen Zustand.
        let showTrail = skin.hasTrail && game.phase == .running && !birdNode.isHidden
        for (index, node) in trailNodes.enumerated() {
            guard showTrail else {
                node.isHidden = true
                continue
            }
            let step = CGFloat(index + 1)
            let a = angle - CGFloat(game.direction) * step * SkinPaint.trailSpacing
            node.isHidden = false
            node.texture = texture
            node.alpha = 0.34 / step
            node.position = CGPoint(
                x: trackCenter.x + cos(a) * trackRadius,
                y: flipY((h - trackCenter.y) + sin(a) * trackRadius)
            )
            node.zRotation = flip
        }
    }

    /// Port von drawUnlockBurst: goldener Ring aus Pixel-Blöcken, der von
    /// der Bahn nach außen aufsteigt und dabei verblasst.
    private func renderBurst() {
        guard celebrateTime > 0 else {
            burstGlowNode.alpha = 0
            for spark in burstSparks {
                spark.alpha = 0
            }
            return
        }
        let progress = 1 - min(max(celebrateTime / GameScene.celebrateSeconds, 0), 1)
        let fade = 1 - progress

        // Goldschimmer, nur im ersten Drittel spürbar.
        burstGlowNode.alpha = max(fade - 0.66, 0) * 0.9

        for ring in 0..<2 {
            let ringProgress = min(max(progress - CGFloat(ring) * 0.15, 0), 1)
            for k in 0..<GameScene.sparkCount {
                let spark = burstSparks[ring * GameScene.sparkCount + k]
                let blockSize = cell * (3.5 - CGFloat(ring)) * fade
                if ringProgress <= 0 || blockSize <= 0 {
                    spark.alpha = 0
                    continue
                }
                let burstRadius = trackRadius * (0.55 + ringProgress * 0.9)
                let a = (CGFloat(k) / CGFloat(GameScene.sparkCount) + CGFloat(ring) * 0.025) * 2 * CGFloat.pi
                spark.position = CGPoint(
                    x: trackCenter.x + cos(a) * burstRadius,
                    y: trackCenter.y - sin(a) * burstRadius
                )
                spark.size = CGSize(width: blockSize, height: blockSize)
                spark.alpha = fade
            }
        }
    }

    private func renderHud() {
        guard let hud = hud else {
            return
        }
        hud.scoreLabel.text = String(game.score)
        let bannerVisible = game.phase == .running && bannerTimeLeft > 0
        hud.bannerLabel.isHidden = !bannerVisible
        let showPerfect = game.lastHitPerfect && game.timeSinceHit < 0.6 && game.phase == .running
        hud.perfectLabel.isHidden = !showPerfect
    }

    // MARK: - Eingabe

    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let touch = touches.first else {
            return
        }
        let location = touch.location(in: self)

        // Hilfe/Skins konsumieren den Tap komplett — er darf nicht
        // gleichzeitig als Spiel-Tap (Sofort-Neustart!) durchschlagen.
        if let help = helpOverlay, !help.isHidden {
            help.isHidden = true
            return
        }
        if let skins = skinOverlay, !skins.isHidden {
            if let selected = skins.skinAt(point: location, stats: store.stats()) {
                skin = selected
                store.selectedSkin = selected
                rebuildBirdTextures()
            }
            skins.isHidden = true
            return
        }

        if let ready = readyOverlay, !ready.isHidden,
           let buttonName = ready.buttonHit(at: location) {
            handleButton(buttonName)
            return
        }
        if let over = overOverlay, !over.isHidden,
           let buttonName = over.buttonHit(at: location) {
            handleButton(buttonName)
            return
        }

        // Ein Tap in READY/OVER startet gleich einen Lauf — vorher Seed
        // und Tag für den aktuellen Modus setzen.
        if game.phase == .ready || game.phase == .over {
            prepareRun()
        }
        game.tap()
    }

    private func handleButton(_ name: String) {
        switch name {
        case "btn.sound":
            store.soundMuted = !store.soundMuted
            audio.muted = store.soundMuted
            enterReadyRefreshOnly()
        case "btn.reminder":
            toggleReminder()
        case "btn.help":
            helpOverlay?.isHidden = false
        case "btn.daily":
            dailyMode = true
            prepareRun()
            game.tap()
        case "btn.skins":
            skinOverlay?.refresh(stats: store.stats(), selected: skin)
            skinOverlay?.isHidden = false
        case "btn.menu":
            dailyMode = false
            game.reset()
            // Auch die Effekte zurücksetzen — sonst läuft die
            // Sturz-Animation weiter und der Vogel fehlt im Startbild,
            // obwohl er dort wieder kreisen soll.
            resetFx()
            bannerTimeLeft = 0
            lastStage = 0
            recordCelebrated = false
            hud?.bannerLabel.text = ""
        default:
            break
        }
    }

    /// Erinnerung an/aus. Beim Einschalten fragt iOS beim ersten Mal nach
    /// der Berechtigung; lehnt die Nutzerin ab, bleibt der Schalter aus —
    /// ein aktivierter Schalter ohne Zustellung wäre eine Lüge.
    private func toggleReminder() {
        if store.reminderEnabled {
            store.reminderEnabled = false
            DailyReminder.cancel()
            enterReadyRefreshOnly()
            return
        }
        DailyReminder.requestPermission { [weak self] granted in
            guard let self = self else { return }
            self.store.reminderEnabled = granted
            if granted {
                DailyReminder.refresh(store: self.store)
            }
            self.enterReadyRefreshOnly()
        }
    }

    private func enterReadyRefreshOnly() {
        let today = DailyChallenge.todayEpochDay()
        readyOverlay?.refresh(
            bestScore: store.bestScore,
            runNumber: store.runCount,
            soundOn: !store.soundMuted,
            reminderOn: store.reminderEnabled,
            dailyBest: store.dailyBestFor(epochDay: today),
            dailyStreak: store.dailyStreakPreviewFor(epochDay: today)
        )
    }
}

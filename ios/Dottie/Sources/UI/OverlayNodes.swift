import SpriteKit
import UIKit

/// Label mit hartem Pixel-Schatten — das SpriteKit-Pendant zum
/// ScoreShadowStyle (Bytesized-Font, Schatten 4px ohne Blur).
final class PixelLabel: SKNode {

    private let shadowLabel: SKLabelNode
    private let mainLabel: SKLabelNode

    var text: String {
        didSet {
            shadowLabel.text = text
            mainLabel.text = text
        }
    }

    var color: UIColor {
        didSet {
            mainLabel.fontColor = color
        }
    }

    init(
        text: String,
        fontSize: CGFloat,
        color: UIColor,
        shadow: Bool = true,
        maxWidth: CGFloat = 0
    ) {
        self.text = text
        self.color = color
        shadowLabel = SKLabelNode(fontNamed: Palette.fontName)
        mainLabel = SKLabelNode(fontNamed: Palette.fontName)
        super.init()

        for label in [shadowLabel, mainLabel] {
            label.text = text
            label.fontSize = fontSize
            label.horizontalAlignmentMode = .center
            label.verticalAlignmentMode = .center
            if maxWidth > 0 {
                label.numberOfLines = 0
                label.lineBreakMode = .byWordWrapping
                label.preferredMaxLayoutWidth = maxWidth
            }
        }
        shadowLabel.fontColor = Palette.outline
        let offset = max(2, fontSize * 0.06)
        shadowLabel.position = CGPoint(x: offset, y: -offset)
        mainLabel.fontColor = color
        if shadow {
            addChild(shadowLabel)
        }
        addChild(mainLabel)
    }

    required init?(coder aDecoder: NSCoder) {
        return nil
    }
}

/// Motive für die Icon-Knöpfe, gezeichnet auf einem 16er-Raster —
/// dieselbe Aufzählung wie PixelIcon in app/.../components/PixelButton.kt.
enum PixelIconKind {
    case speakerOn
    case speakerOff
    case bellOn
    case bellOff
}

/// Blockiger Button mit Treppenkanten, 1:1 aus drawPixelBorder in
/// app/.../ui/components/PixelButton.kt: Oben und unten ein
/// durchgehender Streifen, links und rechts eine Kante, die im obersten
/// und untersten Viertel doppelt so breit ist.
///
/// Ein glatter Rahmen wäre einfacher — aber genau diese Treppe macht den
/// Retro-Look aus, und Android ist die Vorlage, an der sich iOS und die
/// PWA ausrichten. Gezeichnet wird in Rechtecken statt als Textur, damit
/// es bei jeder Bildschirmdichte scharf bleibt.
final class PixelButton: SKNode {

    let buttonName: String
    private let size: CGSize
    private let label: PixelLabel
    private let iconLayer = SKNode()
    private let iconColor: UIColor
    private let strikeColor: UIColor

    var text: String {
        get { return label.text }
        set { label.text = newValue }
    }

    /// Motiv umschalten (Ton an/aus). Neu gezeichnet statt zwei Knöpfe
    /// übereinanderzulegen — der Wechsel passiert selten genug.
    var icon: PixelIconKind? {
        didSet {
            iconLayer.removeAllChildren()
            if let icon = icon {
                PixelButton.addIcon(
                    icon, to: iconLayer, size: size, color: iconColor, strike: strikeColor
                )
            }
        }
    }

    init(
        name: String,
        text: String,
        size: CGSize,
        background: UIColor,
        border: UIColor = Palette.textDark,
        textColor: UIColor = Palette.textDark,
        fontSize: CGFloat = 20,
        borderWidth: CGFloat = 4,
        icon: PixelIconKind? = nil,
        iconColor: UIColor = Palette.textDark,
        strikeColor: UIColor = Palette.recordRed
    ) {
        self.buttonName = name
        self.size = size
        self.label = PixelLabel(text: text, fontSize: fontSize, color: textColor, shadow: false)
        self.iconColor = iconColor
        self.strikeColor = strikeColor
        super.init()
        self.name = name

        addChild(SKSpriteNode(color: background, size: size))
        PixelButton.addSteppedBorder(to: self, size: size, color: border, pixelSize: borderWidth)
        addChild(iconLayer)
        // Bewusst direkt gezeichnet statt ueber self.icon: Swift ruft
        // didSet bei Zuweisungen im eigenen Initialisierer nicht auf, das
        // Motiv bliebe sonst unsichtbar.
        self.icon = icon
        if let icon = icon {
            PixelButton.addIcon(
                icon, to: iconLayer, size: size, color: iconColor, strike: strikeColor
            )
        } else {
            addChild(label)
        }
    }

    /// Ein Rechteck in Pixel-Koordinaten (Ursprung oben links) auf einen
    /// Knoten legen, dessen Kinder um die Mitte zentriert sind.
    private static func addRect(
        to node: SKNode,
        buttonSize: CGSize,
        x: CGFloat,
        y: CGFloat,
        w: CGFloat,
        h: CGFloat,
        color: UIColor
    ) {
        guard w > 0, h > 0 else { return }
        let sprite = SKSpriteNode(color: color, size: CGSize(width: w, height: h))
        sprite.position = CGPoint(
            x: x + w / 2 - buttonSize.width / 2,
            y: buttonSize.height / 2 - y - h / 2
        )
        node.addChild(sprite)
    }

    private static func addSteppedBorder(
        to node: SKNode,
        size: CGSize,
        color: UIColor,
        pixelSize: CGFloat
    ) {
        addRect(to: node, buttonSize: size, x: 0, y: 0, w: size.width, h: pixelSize, color: color)
        addRect(
            to: node, buttonSize: size,
            x: 0, y: size.height - pixelSize, w: size.width, h: pixelSize, color: color
        )

        // Kotlin: steps = ((height - 2 * pixelSize) / pixelSize).toInt()
        let steps = Int((size.height - 2 * pixelSize) / pixelSize)
        guard steps > 0 else { return }
        let stepHeight = (size.height - 2 * pixelSize) / CGFloat(steps)
        for i in 0..<steps {
            let y = pixelSize + CGFloat(i) * stepHeight
            // Int-Division wie in Kotlin: das obere und das untere
            // Viertel bekommen die doppelte Breite.
            let stepWidth = (i < steps / 4 || i >= steps * 3 / 4) ? pixelSize * 2 : pixelSize
            // +1 wie am Phone: verhindert Haarrisse zwischen den Stufen.
            let h = stepHeight + 1
            addRect(to: node, buttonSize: size, x: 0, y: y, w: stepWidth, h: h, color: color)
            addRect(
                to: node, buttonSize: size,
                x: size.width - stepWidth, y: y, w: stepWidth, h: h, color: color
            )
        }
    }

    /// Lautsprecher aus drawPixelIcon: Bloecke auf dem 16er-Raster, "aus"
    /// zusätzlich mit der roten Treppen-Durchstreichung.
    private static func addIcon(
        _ icon: PixelIconKind,
        to node: SKNode,
        size: CGSize,
        color: UIColor,
        strike: UIColor
    ) {
        let u = min(size.width, size.height) / 16
        func block(_ x: CGFloat, _ y: CGFloat, _ w: CGFloat, _ h: CGFloat, _ c: UIColor) {
            addRect(to: node, buttonSize: size, x: x * u, y: y * u, w: w * u, h: h * u, color: c)
        }
        switch icon {
        case .speakerOn, .speakerOff:
            // Korpus plus Trichter nach rechts
            block(3, 6, 2.5, 4, color)
            block(5.5, 5, 1.5, 6, color)
            block(7, 4, 1.5, 8, color)
            if icon == .speakerOn {
                // Zwei blockige Schallwellen
                block(10, 6, 1.2, 4, color)
                block(12, 4.5, 1.2, 7, color)
            }
        case .bellOn, .bellOff:
            // Knauf, Kuppel, Koerper, Rand, Kloeppel
            block(7.2, 2.5, 1.6, 1.5, color)
            block(5.5, 3.8, 5, 2.2, color)
            block(4.5, 6, 7, 3.5, color)
            block(3.5, 9.3, 9, 1.6, color)
            block(7.2, 11.2, 1.6, 1.6, color)
        }
        if icon == .speakerOff || icon == .bellOff {
            // Treppen-Durchstreichung von links oben nach rechts unten
            for i in 0..<6 {
                let d = 2.5 + CGFloat(i) * 1.9
                block(d, d, 2.2, 2.2, strike)
            }
        }
    }

    required init?(coder aDecoder: NSCoder) {
        return nil
    }

    /// Liegt der Punkt (im Koordinatensystem des Parents) auf dem Button?
    func contains(parentPoint: CGPoint) -> Bool {
        let frame = CGRect(
            x: position.x - size.width / 2,
            y: position.y - size.height / 2,
            width: size.width,
            height: size.height
        )
        return frame.insetBy(dx: -8, dy: -8).contains(parentPoint)
    }
}

/// HUD während des Laufs: Score, DAILY-Tag, Twist-Banner, PERFEKT-Text.
final class HudOverlay: SKNode {

    let scoreLabel: PixelLabel
    let dailyLabel: PixelLabel
    let bannerLabel: PixelLabel
    let perfectLabel: PixelLabel

    init(sceneSize: CGSize, safeTop: CGFloat) {
        let w = sceneSize.width
        let h = sceneSize.height
        scoreLabel = PixelLabel(text: "0", fontSize: 72, color: .white)
        dailyLabel = PixelLabel(text: L10n.text("daily"), fontSize: 18, color: Palette.dotBody)
        bannerLabel = PixelLabel(
            text: "", fontSize: 30, color: Palette.bannerOrange, maxWidth: w - 48
        )
        perfectLabel = PixelLabel(text: "", fontSize: 28, color: Palette.perfectYellow)
        super.init()

        let topY = h - safeTop - 80
        scoreLabel.position = CGPoint(x: w / 2, y: topY)
        dailyLabel.position = CGPoint(x: w / 2, y: topY - 52)
        bannerLabel.position = CGPoint(x: w / 2, y: topY - 82)
        perfectLabel.position = CGPoint(x: w / 2, y: h * 0.24)
        addChild(scoreLabel)
        addChild(dailyLabel)
        addChild(bannerLabel)
        addChild(perfectLabel)
        dailyLabel.isHidden = true
        bannerLabel.isHidden = true
        perfectLabel.isHidden = true
    }

    required init?(coder aDecoder: NSCoder) {
        return nil
    }
}

/// Startscreen: Titel, Rekord, blinkender Hinweis, DAILY/SKINS, Sound, Hilfe.
final class ReadyOverlay: SKNode {

    private let bestLabel: PixelLabel
    private let statsLabel: PixelLabel
    private let runLabel: PixelLabel
    private let soundButton: PixelButton
    private let reminderButton: PixelButton
    private var buttons: [PixelButton] = []

    init(sceneSize: CGSize, safeTop: CGFloat, safeBottom: CGFloat) {
        let w = sceneSize.width
        let h = sceneSize.height

        bestLabel = PixelLabel(text: "", fontSize: 22, color: .white)
        statsLabel = PixelLabel(text: "", fontSize: 15, color: Palette.dotBody)
        runLabel = PixelLabel(text: "", fontSize: 16, color: UIColor(white: 1, alpha: 0.8))
        // Icon statt Text, wie am Phone (PixelIconButton in
        // GameOverlays.kt): 48x48, Sandfläche, 3px Rahmen.
        soundButton = PixelButton(
            name: "btn.sound",
            text: "",
            size: CGSize(width: 48, height: 48),
            background: Palette.panelSand,
            borderWidth: 3,
            icon: .speakerOn
        )
        reminderButton = PixelButton(
            name: "btn.reminder",
            text: "",
            size: CGSize(width: 48, height: 48),
            background: Palette.panelSand,
            borderWidth: 3,
            icon: .bellOff
        )
        super.init()

        // "DOTTIE." ist mit 7 Zeichen schmal genug für die vollen 64pt.
        let title = PixelLabel(text: "DOTTIE.", fontSize: 64, color: .white)
        title.position = CGPoint(x: w / 2, y: h - safeTop - 110)
        addChild(title)

        bestLabel.position = CGPoint(x: w / 2, y: h - safeTop - 165)
        addChild(bestLabel)

        let hint = PixelLabel(
            text: L10n.text("ready_hint"),
            fontSize: 22,
            color: .white,
            maxWidth: w - 48
        )
        hint.position = CGPoint(x: w / 2, y: h * 0.36)
        hint.run(SKAction.repeatForever(SKAction.sequence([
            SKAction.fadeAlpha(to: 0.25, duration: 0.6),
            SKAction.fadeAlpha(to: 1.0, duration: 0.6)
        ])))
        addChild(hint)

        // Ton und Erinnerung nebeneinander oben links, wie am Phone
        // (die beiden PixelIconButton in GameOverlays.kt, 10dp Abstand).
        soundButton.position = CGPoint(x: 16 + 24, y: h - safeTop - 40)
        addChild(soundButton)
        reminderButton.position = CGPoint(x: 16 + 24 + 48 + 10, y: h - safeTop - 40)
        addChild(reminderButton)

        let helpButton = PixelButton(
            name: "btn.help",
            text: "?",
            size: CGSize(width: 48, height: 48),
            background: Palette.panelSand,
            fontSize: 24,
            borderWidth: 3
        )
        helpButton.position = CGPoint(x: w - 16 - 24, y: h - safeTop - 40)
        addChild(helpButton)

        let buttonY = safeBottom + 110
        let dailyButton = PixelButton(
            name: "btn.daily",
            text: L10n.text("daily"),
            size: CGSize(width: 116, height: 52),
            background: Palette.dotBody
        )
        dailyButton.position = CGPoint(x: w / 2 - 64, y: buttonY)
        addChild(dailyButton)

        let skinsButton = PixelButton(
            name: "btn.skins",
            text: L10n.text("skins"),
            size: CGSize(width: 116, height: 52),
            background: Palette.panelSand
        )
        skinsButton.position = CGPoint(x: w / 2 + 64, y: buttonY)
        addChild(skinsButton)

        statsLabel.position = CGPoint(x: w / 2, y: safeBottom + 66)
        addChild(statsLabel)
        runLabel.position = CGPoint(x: w / 2, y: safeBottom + 42)
        addChild(runLabel)

        buttons = [soundButton, reminderButton, helpButton, dailyButton, skinsButton]
    }

    required init?(coder aDecoder: NSCoder) {
        return nil
    }

    func refresh(
        bestScore: Int,
        runNumber: Int,
        soundOn: Bool,
        reminderOn: Bool,
        dailyBest: Int,
        dailyStreak: Int
    ) {
        bestLabel.text = L10n.format("best_score", bestScore)
        bestLabel.isHidden = bestScore <= 0

        soundButton.icon = soundOn ? .speakerOn : .speakerOff
        reminderButton.icon = reminderOn ? .bellOn : .bellOff

        var parts: [String] = []
        if dailyBest > 0 {
            parts.append(L10n.format("today_score", dailyBest))
        }
        if dailyStreak > 0 {
            parts.append(L10n.streakLabel(days: dailyStreak))
        }
        statsLabel.text = parts.joined(separator: "  ·  ")
        statsLabel.isHidden = parts.isEmpty

        runLabel.text = L10n.format("run_number", runNumber + 1)
        runLabel.isHidden = runNumber <= 0
    }

    func buttonHit(at point: CGPoint) -> String? {
        for button in buttons where button.contains(parentPoint: point) {
            return button.buttonName
        }
        return nil
    }
}

/// Game-Over: Panel mit Medaille + Score/Rekord, Spott, Daily-Zeile,
/// "TIPPEN = NOCHMAL" und MENUE-Button. Kein Teilen auf iOS.
final class GameOverOverlay: SKNode {

    private let sceneSize: CGSize
    private let medalSprite: SKSpriteNode
    private let medalNameLabel: PixelLabel
    private let medalNextLabel: PixelLabel
    private let scoreValueLabel: PixelLabel
    private let bestTitleLabel: PixelLabel
    private let bestValueLabel: PixelLabel
    private let tauntLabel: PixelLabel
    private let dailyLabel: PixelLabel
    private let newMedalLabel: PixelLabel
    private let newSkinLabel: PixelLabel
    private var buttons: [PixelButton] = []

    init(sceneSize: CGSize, safeTop: CGFloat) {
        self.sceneSize = sceneSize
        let w = sceneSize.width
        let h = sceneSize.height
        let centerX = w / 2
        let centerY = h / 2

        medalSprite = SKSpriteNode(
            texture: PixelArt.medalTexture(tier: nil, size: 72)
        )
        medalNameLabel = PixelLabel(text: "", fontSize: 12, color: Palette.textDark, shadow: false)
        medalNextLabel = PixelLabel(text: "", fontSize: 10, color: UIColor(rgb: 0x8A7F5A), shadow: false)
        scoreValueLabel = PixelLabel(text: "0", fontSize: 40, color: Palette.textDark, shadow: false)
        bestTitleLabel = PixelLabel(text: L10n.text("record_label"), fontSize: 16, color: Palette.textDark, shadow: false)
        bestValueLabel = PixelLabel(text: "0", fontSize: 40, color: Palette.textDark, shadow: false)
        tauntLabel = PixelLabel(text: "", fontSize: 24, color: .white, maxWidth: w - 48)
        dailyLabel = PixelLabel(text: "", fontSize: 16, color: Palette.dotBody, maxWidth: w - 48)
        newMedalLabel = PixelLabel(text: L10n.text("new_medal"), fontSize: 18, color: Palette.perfectYellow)
        newSkinLabel = PixelLabel(text: L10n.text("new_skin_unlocked"), fontSize: 18, color: Palette.perfectYellow)
        super.init()

        let gameOver = PixelLabel(text: L10n.text("game_over"), fontSize: 48, color: Palette.bannerOrange)
        gameOver.position = CGPoint(x: centerX, y: centerY + 210)
        addChild(gameOver)

        // "?" auch hier, wie am Phone: Nach dem ersten Tod will man die
        // Regeln nachlesen, und der Startbildschirm ist von hier aus nur
        // ueber den Umweg MENUE erreichbar.
        let helpButton = PixelButton(
            name: "btn.help",
            text: "?",
            size: CGSize(width: 48, height: 48),
            background: Palette.panelSand,
            fontSize: 24,
            borderWidth: 3
        )
        helpButton.position = CGPoint(x: w - 16 - 24, y: h - safeTop - 40)
        addChild(helpButton)

        // Panel: dunkler Rahmen + Sandfläche.
        let panelSize = CGSize(width: min(w - 48, 330), height: 170)
        let panelBorder = SKSpriteNode(color: Palette.outline, size: panelSize)
        panelBorder.position = CGPoint(x: centerX, y: centerY + 95)
        addChild(panelBorder)
        let panelInner = SKSpriteNode(
            color: Palette.panelSand,
            size: CGSize(width: panelSize.width - 8, height: panelSize.height - 8)
        )
        panelInner.position = panelBorder.position
        addChild(panelInner)

        let panelCenter = panelBorder.position
        medalSprite.size = CGSize(width: 72, height: 72)
        medalSprite.position = CGPoint(x: panelCenter.x - 80, y: panelCenter.y + 20)
        addChild(medalSprite)
        medalNameLabel.position = CGPoint(x: panelCenter.x - 80, y: panelCenter.y - 30)
        addChild(medalNameLabel)
        medalNextLabel.position = CGPoint(x: panelCenter.x - 80, y: panelCenter.y - 48)
        addChild(medalNextLabel)

        let scoreTitle = PixelLabel(text: L10n.text("points_label"), fontSize: 16, color: Palette.textDark, shadow: false)
        scoreTitle.position = CGPoint(x: panelCenter.x + 60, y: panelCenter.y + 55)
        addChild(scoreTitle)
        scoreValueLabel.position = CGPoint(x: panelCenter.x + 60, y: panelCenter.y + 22)
        addChild(scoreValueLabel)
        bestTitleLabel.position = CGPoint(x: panelCenter.x + 60, y: panelCenter.y - 16)
        addChild(bestTitleLabel)
        bestValueLabel.position = CGPoint(x: panelCenter.x + 60, y: panelCenter.y - 50)
        addChild(bestValueLabel)

        tauntLabel.position = CGPoint(x: centerX, y: centerY - 20)
        addChild(tauntLabel)
        dailyLabel.position = CGPoint(x: centerX, y: centerY - 55)
        addChild(dailyLabel)
        newMedalLabel.position = CGPoint(x: centerX, y: centerY - 85)
        addChild(newMedalLabel)
        newSkinLabel.position = CGPoint(x: centerX, y: centerY - 110)
        addChild(newSkinLabel)

        // Kein NOCHMAL-Button: Tap irgendwo startet sofort neu — der
        // blinkende Hinweis ist die einzige Restart-Affordanz.
        let retry = PixelLabel(text: L10n.text("tap_retry"), fontSize: 26, color: .white)
        retry.position = CGPoint(x: centerX, y: centerY - 155)
        retry.run(SKAction.repeatForever(SKAction.sequence([
            SKAction.fadeAlpha(to: 0.3, duration: 0.5),
            SKAction.fadeAlpha(to: 1.0, duration: 0.5)
        ])))
        addChild(retry)

        let menuButton = PixelButton(
            name: "btn.menu",
            text: L10n.text("menu"),
            size: CGSize(width: 116, height: 48),
            background: Palette.panelSand
        )
        menuButton.position = CGPoint(x: centerX, y: centerY - 210)
        addChild(menuButton)
        buttons = [menuButton, helpButton]
    }

    required init?(coder aDecoder: NSCoder) {
        return nil
    }

    func configure(
        score: Int,
        bestScore: Int,
        isNewRecord: Bool,
        taunt: String,
        daily: Bool,
        dailyBest: Int,
        dailyStreak: Int,
        skinUnlocked: Bool,
        newMedal: Bool
    ) {
        let tier = MedalTier.forScore(score)
        medalSprite.texture = PixelArt.medalTexture(tier: tier, size: 72)
        // Medaille ploppt mit kleinem Überschwinger ein.
        medalSprite.setScale(0.01)
        medalSprite.run(SKAction.sequence([
            SKAction.scale(to: 1.15, duration: 0.22),
            SKAction.scale(to: 1.0, duration: 0.12)
        ]))
        if let tier = tier {
            medalNameLabel.text = L10n.text(tier.nameKey)
        } else {
            medalNameLabel.text = L10n.text("medal")
        }
        if let next = MedalTier.next(score) {
            medalNextLabel.text = L10n.format(
                "medal_next", next.threshold - score, L10n.text(next.nameKey)
            )
            medalNextLabel.isHidden = false
        } else {
            medalNextLabel.isHidden = true
        }

        scoreValueLabel.text = String(score)
        bestValueLabel.text = String(bestScore)
        let recordColor: UIColor = isNewRecord ? Palette.recordRed : Palette.textDark
        bestTitleLabel.color = recordColor
        bestValueLabel.color = recordColor

        tauntLabel.text = isNewRecord ? L10n.text("new_record") : taunt
        tauntLabel.color = isNewRecord ? Palette.perfectYellow : .white

        if daily {
            var parts: [String] = [L10n.text("daily"), L10n.format("today_score", dailyBest)]
            if dailyStreak > 0 {
                parts.append(L10n.streakLabel(days: dailyStreak))
            }
            dailyLabel.text = parts.joined(separator: "  ·  ")
            dailyLabel.isHidden = false
        } else {
            dailyLabel.isHidden = true
        }
        newMedalLabel.isHidden = !newMedal
        newSkinLabel.isHidden = !skinUnlocked
    }

    func buttonHit(at point: CGPoint) -> String? {
        for button in buttons where button.contains(parentPoint: point) {
            return button.buttonName
        }
        return nil
    }
}

/// Vollflächige Spiel-Erklärung über dunklem Scrim; Tap schließt.
final class HelpOverlay: SKNode {

    init(sceneSize: CGSize) {
        super.init()
        let w = sceneSize.width
        let h = sceneSize.height

        let scrim = SKSpriteNode(
            color: Palette.outline.withAlphaComponent(0.92),
            size: CGSize(width: w + 80, height: h + 80)
        )
        scrim.position = CGPoint(x: w / 2, y: h / 2)
        addChild(scrim)

        var y = h - 120
        func addCentered(_ text: String, _ fontSize: CGFloat, _ color: UIColor, shadow: Bool = false, gapAfter: CGFloat = 0) {
            let label = PixelLabel(text: text, fontSize: fontSize, color: color, shadow: shadow, maxWidth: w - 56)
            label.position = CGPoint(x: w / 2, y: y)
            addChild(label)
            let height = label.calculateAccumulatedFrame().height
            y -= max(height, fontSize) + 8 + gapAfter
        }

        addCentered(L10n.text("help_title"), 32, .white, shadow: true, gapAfter: 6)
        addCentered(L10n.text("help_line1"), 15, .white)
        addCentered(L10n.text("help_line2"), 15, .white)
        addCentered(L10n.text("help_line3"), 15, Palette.dotBody)
        addCentered(L10n.text("help_line4"), 15, Palette.dotBody)
        addCentered(L10n.text("help_line5"), 15, .white, gapAfter: 10)
        addCentered(L10n.text("help_twists"), 24, Palette.bannerOrange, shadow: true, gapAfter: 4)

        let twists: [(UIColor, String, String)] = [
            (Palette.grassLight, "twist_pulse_title", "twist_pulse_text"),
            (UIColor(rgb: 0x5B9BD5), "twist_drift_title", "twist_drift_text"),
            (Palette.cloud, "twist_ghost_title", "twist_ghost_text"),
            (UIColor(rgb: 0xB44FD8), "twist_fake_title", "twist_fake_text"),
            (Palette.bannerOrange, "twist_chain_title", "twist_chain_text")
        ]
        for (color, titleKey, textKey) in twists {
            let swatch = SKSpriteNode(color: color, size: CGSize(width: 14, height: 14))
            swatch.position = CGPoint(x: 44, y: y)
            addChild(swatch)
            let title = PixelLabel(text: L10n.text(titleKey), fontSize: 15, color: color, shadow: false)
            title.position = CGPoint(x: 60, y: y)
            titleAlignLeft(title)
            addChild(title)
            y -= 20
            let text = PixelLabel(
                text: L10n.text(textKey), fontSize: 13,
                color: UIColor(white: 1, alpha: 0.85), shadow: false, maxWidth: w - 100
            )
            text.position = CGPoint(x: 60, y: y)
            titleAlignLeft(text)
            addChild(text)
            y -= max(text.calculateAccumulatedFrame().height, 16) + 10
        }

        addCentered(L10n.text("help_max_twists"), 14, .white, gapAfter: 10)
        addCentered(L10n.text("tap_to_close"), 14, UIColor(white: 1, alpha: 0.6))
    }

    /// Stellt alle Kind-Labels eines PixelLabels auf linksbündig um.
    private func titleAlignLeft(_ node: SKNode) {
        for child in node.children {
            if let label = child as? SKLabelNode {
                label.horizontalAlignmentMode = .left
            }
        }
    }

    required init?(coder aDecoder: NSCoder) {
        return nil
    }
}

/// Vollflächiger Skin-Picker über dunklem Scrim, im Stil der Hilfe.
///
/// 42 Skins passen auf kein Telefon. Die Liste ist deshalb nach Familien
/// gegliedert (EINFARBIG … GOENNER) und läuft in einem Fenster, das per
/// Wischen gescrollt wird; Titel und Schließen-Hinweis bleiben stehen. Das
/// Fenster ist ein SKCropNode — ohne Maske würden die gescrollten Zeilen
/// über Titel und Hinweis hinauslaufen.
final class SkinOverlay: SKNode {

    /// Was eine Berührung im Picker bedeutet: Ein Zug scrollt nur, ein Tipp
    /// wählt oder schließt. Der Fall heißt `scrolled` und nicht `none` —
    /// letzteres ließe sich beim Auswerten mit `Optional.none` verwechseln.
    enum TouchResult {
        case scrolled
        case select(DotSkin)
        case close
    }

    private struct Row {
        let skin: DotSkin
        let centerY: CGFloat
        let swatch: SKSpriteNode
        let titleLabel: PixelLabel
        let statusLabel: PixelLabel
    }

    private var rows: [Row] = []
    private let rowHeight: CGFloat = 58
    private let headerHeight: CGFloat = 36

    private let contentNode = SKNode()
    private let scrollThumb: SKSpriteNode
    private let listTop: CGFloat
    private let listBottom: CGFloat
    private var maxScroll: CGFloat = 0
    private var scrollOffset: CGFloat = 0

    private var dragStartY: CGFloat = 0
    private var dragStartOffset: CGFloat = 0
    private var dragged = false

    init(sceneSize: CGSize) {
        let w = sceneSize.width
        let h = sceneSize.height
        listTop = h - 158
        listBottom = 84
        scrollThumb = SKSpriteNode(
            color: UIColor(white: 1, alpha: 0.35), size: CGSize(width: 4, height: 40)
        )
        super.init()

        let scrim = SKSpriteNode(
            color: Palette.outline.withAlphaComponent(0.92),
            size: CGSize(width: w + 80, height: h + 80)
        )
        scrim.position = CGPoint(x: w / 2, y: h / 2)
        addChild(scrim)

        let title = PixelLabel(text: L10n.text("skins"), fontSize: 32, color: .white)
        title.position = CGPoint(x: w / 2, y: h - 120)
        addChild(title)

        // Sichtfenster der Liste: Alles darüber und darunter wird
        // weggeschnitten, damit gescrollte Zeilen nicht im Titel landen.
        let crop = SKCropNode()
        let mask = SKSpriteNode(
            color: .white, size: CGSize(width: w, height: listTop - listBottom)
        )
        mask.position = CGPoint(x: w / 2, y: (listTop + listBottom) / 2)
        crop.maskNode = mask
        crop.addChild(contentNode)
        addChild(crop)

        var y = listTop - 24
        for family in DotSkin.Family.allCases {
            let header = PixelLabel(
                text: L10n.text(family.titleKey), fontSize: 15,
                color: Palette.dotBody, shadow: false
            )
            header.position = CGPoint(x: 40, y: y)
            alignLeft(header)
            contentNode.addChild(header)
            y -= headerHeight

            for skin in DotSkin.allCases where skin.family == family {
                // Vorschau als echter Vogel statt als Farbfläche: Bei
                // gemusterten Skins sagt ein einzelner Farbwert nichts mehr
                // aus. Bewegte Skins stehen dabei still (Zeitpunkt 0).
                let swatch = SKSpriteNode(
                    texture: PixelArt.skinPreviewTexture(skin: skin, size: 36)
                )
                swatch.size = CGSize(width: 36, height: 36)
                swatch.position = CGPoint(x: 64, y: y)
                contentNode.addChild(swatch)

                let titleLabel = PixelLabel(
                    text: L10n.text(skin.titleKey), fontSize: 20, color: .white, shadow: false
                )
                titleLabel.position = CGPoint(x: 96, y: y + 10)
                alignLeft(titleLabel)
                contentNode.addChild(titleLabel)

                let statusLabel = PixelLabel(
                    text: "", fontSize: 14, color: UIColor(white: 1, alpha: 0.7), shadow: false
                )
                statusLabel.position = CGPoint(x: 96, y: y - 12)
                alignLeft(statusLabel)
                contentNode.addChild(statusLabel)

                rows.append(Row(
                    skin: skin,
                    centerY: y,
                    swatch: swatch,
                    titleLabel: titleLabel,
                    statusLabel: statusLabel
                ))
                y -= rowHeight
            }
            y -= 8
        }
        maxScroll = max(0, (listTop - y) - (listTop - listBottom))

        scrollThumb.position = CGPoint(x: w - 12, y: listTop - 20)
        scrollThumb.isHidden = maxScroll <= 0
        addChild(scrollThumb)
        applyScroll()

        let closeHint = PixelLabel(
            text: L10n.text("tap_to_close"), fontSize: 14,
            color: UIColor(white: 1, alpha: 0.6), shadow: false
        )
        closeHint.position = CGPoint(x: w / 2, y: 48)
        addChild(closeHint)
    }

    private func alignLeft(_ node: SKNode) {
        for child in node.children {
            if let label = child as? SKLabelNode {
                label.horizontalAlignmentMode = .left
            }
        }
    }

    required init?(coder aDecoder: NSCoder) {
        return nil
    }

    // MARK: - Scrollen

    /// Inhalt an den Offset schieben und den Balken nachziehen. Der Balken
    /// ist die einzige Anzeige, dass es unter der Liste weitergeht.
    private func applyScroll() {
        scrollOffset = min(max(scrollOffset, 0), maxScroll)
        contentNode.position = CGPoint(x: 0, y: scrollOffset)
        guard maxScroll > 0 else {
            return
        }
        let window = listTop - listBottom
        let thumbHeight = max(30, window * window / (window + maxScroll))
        let travel = window - thumbHeight
        let progress = scrollOffset / maxScroll
        scrollThumb.size = CGSize(width: 4, height: thumbHeight)
        scrollThumb.position = CGPoint(
            x: scrollThumb.position.x,
            y: listTop - thumbHeight / 2 - travel * progress
        )
    }

    func touchBegan(at point: CGPoint) {
        dragStartY = point.y
        dragStartOffset = scrollOffset
        dragged = false
    }

    func touchMoved(to point: CGPoint) {
        let dy = point.y - dragStartY
        // Ein paar Punkt Toleranz: Ein Tipp mit zitterndem Daumen soll
        // weiter als Tipp gelten, nicht als Zug.
        if abs(dy) > 6 {
            dragged = true
        }
        scrollOffset = dragStartOffset - dy
        applyScroll()
    }

    /// Liefert, was die Berührung bedeutet hat. Nach einem Zug bleibt der
    /// Picker offen — sonst würde jedes Scrollen ihn schließen.
    func touchEnded(at point: CGPoint, stats: DotSkin.Stats) -> TouchResult {
        if dragged {
            return .scrolled
        }
        guard point.y <= listTop && point.y >= listBottom else {
            return .close
        }
        let contentY = point.y - scrollOffset
        for row in rows where abs(contentY - row.centerY) <= rowHeight / 2 {
            // Auf eine gesperrte Zeile getippt: Der Picker schließt wie
            // bei jedem Tipp daneben — der Hinweis unten verspricht genau das.
            return row.skin.isUnlocked(stats) ? .select(row.skin) : .close
        }
        return .close
    }

    func refresh(stats: DotSkin.Stats, selected: DotSkin) {
        for row in rows {
            let unlocked = row.skin.isUnlocked(stats)
            row.swatch.alpha = unlocked ? 1.0 : 0.3
            row.titleLabel.color = unlocked ? .white : UIColor(white: 1, alpha: 0.45)
            if row.skin == selected {
                row.statusLabel.text = L10n.text("skin_selected")
                row.statusLabel.color = Palette.dotBody
            } else if unlocked {
                row.statusLabel.text = L10n.text("skin_tap_select")
                row.statusLabel.color = UIColor(white: 1, alpha: 0.7)
            } else {
                if let hintKey = row.skin.unlockHintKey {
                    row.statusLabel.text = L10n.text(hintKey)
                } else {
                    row.statusLabel.text = ""
                }
                row.statusLabel.color = UIColor(white: 1, alpha: 0.45)
            }
        }
        scrollTo(selected)
    }

    /// Beim Öffnen zum gewählten Skin springen: Bei 42 Zeilen wäre er
    /// sonst irgendwo weit unten und man sucht seinen eigenen Vogel.
    private func scrollTo(_ skin: DotSkin) {
        guard maxScroll > 0, let row = rows.first(where: { $0.skin == skin }) else {
            return
        }
        let window = listTop - listBottom
        scrollOffset = listTop - row.centerY - window / 2
        applyScroll()
    }
}

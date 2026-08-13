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

/// Blockiger Button: dunkler Rahmen, farbige Fläche, Bytesized-Label.
/// Getroffene Buttons werden über `PixelButton.hit(in:at:)` gefunden.
final class PixelButton: SKNode {

    let buttonName: String
    private let size: CGSize
    private let label: PixelLabel

    var text: String {
        get { return label.text }
        set { label.text = newValue }
    }

    init(
        name: String,
        text: String,
        size: CGSize,
        background: UIColor,
        border: UIColor = Palette.textDark,
        textColor: UIColor = Palette.textDark,
        fontSize: CGFloat = 20
    ) {
        self.buttonName = name
        self.size = size
        self.label = PixelLabel(text: text, fontSize: fontSize, color: textColor, shadow: false)
        super.init()
        self.name = name

        let borderNode = SKSpriteNode(color: border, size: size)
        let innerNode = SKSpriteNode(
            color: background,
            size: CGSize(width: size.width - 6, height: size.height - 6)
        )
        addChild(borderNode)
        addChild(innerNode)
        addChild(label)
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
    private var buttons: [PixelButton] = []

    init(sceneSize: CGSize, safeTop: CGFloat, safeBottom: CGFloat) {
        let w = sceneSize.width
        let h = sceneSize.height

        bestLabel = PixelLabel(text: "", fontSize: 22, color: .white)
        statsLabel = PixelLabel(text: "", fontSize: 15, color: Palette.dotBody)
        runLabel = PixelLabel(text: "", fontSize: 16, color: UIColor(white: 1, alpha: 0.8))
        soundButton = PixelButton(
            name: "btn.sound",
            text: L10n.text("sound_on"),
            size: CGSize(width: 150, height: 44),
            background: Palette.panelSand,
            fontSize: 16
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

        soundButton.position = CGPoint(x: 16 + 75, y: h - safeTop - 40)
        addChild(soundButton)

        let helpButton = PixelButton(
            name: "btn.help",
            text: "?",
            size: CGSize(width: 48, height: 48),
            background: Palette.panelSand,
            fontSize: 24
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

        buttons = [soundButton, helpButton, dailyButton, skinsButton]
    }

    required init?(coder aDecoder: NSCoder) {
        return nil
    }

    func refresh(bestScore: Int, runNumber: Int, soundOn: Bool, dailyBest: Int, dailyStreak: Int) {
        bestLabel.text = L10n.format("best_score", bestScore)
        bestLabel.isHidden = bestScore <= 0

        soundButton.text = L10n.text(soundOn ? "sound_on" : "sound_off")

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

    init(sceneSize: CGSize) {
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
        buttons = [menuButton]
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
final class SkinOverlay: SKNode {

    private struct Row {
        let skin: DotSkin
        let centerY: CGFloat
        let swatch: SKSpriteNode
        let titleLabel: PixelLabel
        let statusLabel: PixelLabel
    }

    private var rows: [Row] = []
    private let rowHeight: CGFloat = 58

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

        let title = PixelLabel(text: L10n.text("skins"), fontSize: 32, color: .white)
        title.position = CGPoint(x: w / 2, y: h - 120)
        addChild(title)

        var y = h - 180
        for skin in DotSkin.allCases {
            // Vorschau als echter Vogel statt als Farbfläche: Bei
            // gemusterten Skins sagt ein einzelner Farbwert nichts mehr
            // aus. Bewegte Skins stehen dabei still (Zeitpunkt 0).
            let swatch = SKSpriteNode(texture: PixelArt.skinPreviewTexture(skin: skin, size: 36))
            swatch.size = CGSize(width: 36, height: 36)
            swatch.position = CGPoint(x: 64, y: y)
            addChild(swatch)

            let titleLabel = PixelLabel(
                text: L10n.text(skin.titleKey), fontSize: 20, color: .white, shadow: false
            )
            titleLabel.position = CGPoint(x: 96, y: y + 10)
            alignLeft(titleLabel)
            addChild(titleLabel)

            let statusLabel = PixelLabel(
                text: "", fontSize: 14, color: UIColor(white: 1, alpha: 0.7), shadow: false
            )
            statusLabel.position = CGPoint(x: 96, y: y - 12)
            alignLeft(statusLabel)
            addChild(statusLabel)

            rows.append(Row(
                skin: skin,
                centerY: y,
                swatch: swatch,
                titleLabel: titleLabel,
                statusLabel: statusLabel
            ))
            y -= rowHeight
        }

        let closeHint = PixelLabel(
            text: L10n.text("tap_to_close"), fontSize: 14,
            color: UIColor(white: 1, alpha: 0.6), shadow: false
        )
        closeHint.position = CGPoint(x: w / 2, y: max(y, 40))
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
    }

    /// Skin an dieser Position — nil, wenn keine Zeile getroffen wurde.
    func skinAt(point: CGPoint, stats: DotSkin.Stats) -> DotSkin? {
        for row in rows {
            if abs(point.y - row.centerY) <= rowHeight / 2 && row.skin.isUnlocked(stats) {
                return row.skin
            }
        }
        return nil
    }
}

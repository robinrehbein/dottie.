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
    /// Zahnrad — der einzige Knopf, der noch oben rechts im Startbild
    /// steht: Ton, Erinnerung und Hilfe liegen dahinter.
    case gear
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
    /// Die Fläche unter dem Motiv: Das Zahnrad braucht sie als Farbe für
    /// sein Loch — ein echtes Loch kann eine Textur aus Rechtecken nicht.
    private let holeColor: UIColor

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
                    icon, to: iconLayer, size: size,
                    color: iconColor, strike: strikeColor, hole: holeColor
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
        self.holeColor = background
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
                icon, to: iconLayer, size: size,
                color: iconColor, strike: strikeColor, hole: background
            )
        } else {
            addChild(label)
        }
    }

    /// Ein Rechteck in Pixel-Koordinaten (Ursprung oben links) auf einen
    /// Knoten legen, dessen Kinder um die Mitte zentriert sind.
    static func addRect(
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

    /// Auch das Einstellungs-Blatt trägt diesen Rahmen — deshalb nicht
    /// privat: Ein zweiter Treppen-Zeichner wäre eine Kopie, die
    /// irgendwann anders aussieht als der Knopf daneben.
    static func addSteppedBorder(
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
        strike: UIColor,
        hole: UIColor
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
        case .gear:
            // Koerper, vier gerade und vier schraege Zaehne, dann die
            // Nabe in der Farbe der Knopffläche. Acht Zaehne statt sechs:
            // Auf dem 16er-Raster liegen nur gerade Teilungen sauber.
            block(4.5, 4.5, 7, 7, color)
            block(6.5, 2.4, 3, 2.3, color)
            block(6.5, 11.3, 3, 2.3, color)
            block(2.4, 6.5, 2.3, 3, color)
            block(11.3, 6.5, 2.3, 3, color)
            block(3.2, 3.2, 2.1, 2.1, color)
            block(10.7, 3.2, 2.1, 2.1, color)
            block(3.2, 10.7, 2.1, 2.1, color)
            block(10.7, 10.7, 2.1, 2.1, color)
            block(6.6, 6.6, 2.8, 2.8, hole)
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

/// Rotes Zähler-Abzeichen an der Ecke eines Knopfes — im Startbild hängt
/// es am DAILY und trägt die Tage der laufenden Serie.
///
/// Es ersetzt die Zeile "HEUTE: 12 · SERIE: 7": Eine Serie ist kein
/// Messwert, den man liest, sondern ein Besitz, den man nicht verlieren
/// will. Als Zahl am Knopf steht sie genau dort, wo der nächste Tag
/// geholt wird — und sie kostet keine eigene Zeile.
///
/// Der Kasten wächst mit der Stelligkeit nach links, die rechte Kante
/// bleibt stehen: Eine dreistellige Serie (ja, die gibt es) schiebt sich
/// sonst in den Knopf nebenan. Die Position des Knotens ist deshalb die
/// rechte Kante des Abzeichens, nicht seine Mitte.
final class PixelBadge: SKNode {

    private let border = SKSpriteNode(color: Palette.outline, size: .zero)
    private let body = SKSpriteNode(color: Palette.recordRed, size: .zero)
    private let label = PixelLabel(text: "", fontSize: 15, color: .white, shadow: false)

    private static let height: CGFloat = 20
    private static let edge: CGFloat = 2

    override init() {
        super.init()
        addChild(border)
        addChild(body)
        addChild(label)
        isHidden = true
    }

    required init?(coder aDecoder: NSCoder) {
        return nil
    }

    /// Die Serienlänge. Unter 1 verschwindet das Abzeichen ganz — ein
    /// Abzeichen mit einer Null darauf wäre eine Auszeichnung fürs Nichts.
    var count: Int = 0 {
        didSet {
            isHidden = count < 1
            guard count >= 1 else {
                return
            }
            let text = String(count)
            label.text = text
            let width = max(PixelBadge.height, 10 + 9 * CGFloat(text.count))
            body.size = CGSize(width: width, height: PixelBadge.height)
            border.size = CGSize(
                width: width + PixelBadge.edge * 2,
                height: PixelBadge.height + PixelBadge.edge * 2
            )
            // Alles nach links vom Ursprung: der bleibt die rechte Kante.
            let center = CGPoint(x: -width / 2, y: 0)
            body.position = center
            border.position = center
            label.position = center
        }
    }
}

/// Der Fortschrittsbalken im Pixel-Look: dunkler Rahmen, Sandbett, gold
/// gefüllte Blöcke. Der Füllstand rastet auf ganze Blöcke ein
/// (`Progress.barBlocks`) — ein weicher Balken wäre der einzige
/// stufenlose Verlauf im ganzen Spiel.
final class GoalBar: SKNode {

    private let bed: SKSpriteNode
    private let fill: SKSpriteNode
    private let innerWidth: CGFloat

    init(width: CGFloat, height: CGFloat = 12) {
        let border: CGFloat = 2
        // Über eine lokale Größe statt über die Eigenschaft: Vor
        // super.init() darf ein Initialisierer eigene Werte setzen, aber
        // keine lesen.
        let inner = width - border * 2
        innerWidth = inner
        bed = SKSpriteNode(
            color: Palette.groundSandShade,
            size: CGSize(width: inner, height: height - border * 2)
        )
        fill = SKSpriteNode(
            color: Palette.dotBody,
            size: CGSize(width: inner, height: height - border * 2)
        )
        super.init()

        addChild(SKSpriteNode(color: Palette.outline, size: CGSize(width: width, height: height)))
        addChild(bed)
        // Links verankert: Der Balken wächst nach rechts, nicht aus der Mitte.
        fill.anchorPoint = CGPoint(x: 0, y: 0.5)
        fill.position = CGPoint(x: -innerWidth / 2, y: 0)
        // Leer starten: `didSet` läuft bei der Zuweisung im Initialisierer
        // nicht, ein voller Balken wäre sonst der Ausgangszustand.
        fill.size = CGSize(width: 0, height: height - border * 2)
        fill.isHidden = true
        addChild(fill)
    }

    required init?(coder aDecoder: NSCoder) {
        return nil
    }

    var fraction: CGFloat = 0 {
        didSet {
            let blocks = Progress.filledBlocks(fraction)
            fill.size = CGSize(
                width: innerWidth * CGFloat(blocks) / CGFloat(Progress.barBlocks),
                height: fill.size.height
            )
            fill.isHidden = blocks <= 0
        }
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

/// Startscreen in der Fassung "ein Ziel": Titel, REKORD, der blinkende
/// Hinweis, die Reihe DAILY/SKINS/STATS — und eine einzige Zeile, die
/// sagt, was als Nächstes fällt.
///
/// Vorher standen hier drei Icon-Knöpfe (Ton, Erinnerung, Hilfe), die
/// Zeile "HEUTE: 12 · SERIE: 7" und "VERSUCH #218". Elf Elemente, von
/// denen keines beantwortete, warum man jetzt tippen sollte. Die
/// Schalter liegen seither hinter dem Zahnrad, die Zähler in der
/// Statistik, die Serie hängt als Abzeichen am DAILY-Knopf — und der
/// gewonnene Platz trägt das nächste Ziel.
final class ReadyOverlay: SKNode {

    private let bestLabel: PixelLabel
    private let goalLabel: PixelLabel
    private let goalBar: GoalBar
    private let streakBadge = PixelBadge()
    private var buttons: [PixelButton] = []

    init(sceneSize: CGSize, safeTop: CGFloat, safeBottom: CGFloat) {
        let w = sceneSize.width
        let h = sceneSize.height

        bestLabel = PixelLabel(text: "", fontSize: 22, color: .white)
        // Eine Zeile, ein Balken — dieselbe Mechanik wie im Game-Over.
        // 15 Punkt und eine Umbruchbreite, weil die längste Fassung
        // ("NAECHSTER SKIN: JAHRESZEIT — 2/3 MONATE GESPIELT") auch auf
        // 375 Punkt Breite noch ganz dastehen muss.
        goalLabel = PixelLabel(text: "", fontSize: 15, color: .white, maxWidth: w - 40)
        goalBar = GoalBar(width: min(w - 64, 240))
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

        // Ein Zahnrad statt drei Icon-Knöpfen. Es steht da, wo vorher das
        // "?" stand: Die obere rechte Ecke war schon immer die Ecke der
        // Nebensachen, und die linke ist damit frei geworden.
        let settingsButton = PixelButton(
            name: "btn.settings",
            text: "",
            size: CGSize(width: 48, height: 48),
            background: Palette.panelSand,
            borderWidth: 3,
            icon: .gear
        )
        settingsButton.position = CGPoint(x: w - 16 - 24, y: h - safeTop - 40)
        addChild(settingsButton)

        // Drei Knöpfe statt zwei: Die Statistik gehört auf den
        // Startscreen, nicht in ein Untermenü — sie ist der Grund, den
        // nächsten Lauf zu starten. Dafür sind alle drei etwas schmaler
        // (108 statt 116 bei 10 Abstand), damit die Reihe auch auf
        // schmalen Geräten mit Rand steht.
        let buttonY = safeBottom + 110
        let buttonSize = CGSize(width: 108, height: 52)
        let buttonStep: CGFloat = 118
        let dailyButton = PixelButton(
            name: "btn.daily",
            text: L10n.text("daily"),
            size: buttonSize,
            background: Palette.dotBody
        )
        dailyButton.position = CGPoint(x: w / 2 - buttonStep, y: buttonY)
        addChild(dailyButton)

        let skinsButton = PixelButton(
            name: "btn.skins",
            text: L10n.text("skins"),
            size: buttonSize,
            background: Palette.panelSand
        )
        skinsButton.position = CGPoint(x: w / 2, y: buttonY)
        addChild(skinsButton)

        let statsButton = PixelButton(
            name: "btn.stats",
            text: L10n.text("stats"),
            size: buttonSize,
            background: Palette.panelSand
        )
        statsButton.position = CGPoint(x: w / 2 + buttonStep, y: buttonY)
        addChild(statsButton)

        // Das Abzeichen hängt in der oberen rechten Ecke des DAILY-Knopfes.
        // Seine rechte Kante steht 2 Punkt neben dem Knopf und damit im
        // 10-Punkt-Zwischenraum zum SKINS-Knopf; wachsen kann es nur nach
        // links, also über den eigenen Knopf. Es kommt nach allen Knöpfen
        // in den Baum: Bei gleicher zPosition entscheidet die Reihenfolge,
        // wer oben liegt.
        streakBadge.position = CGPoint(
            x: dailyButton.position.x + buttonSize.width / 2 + 2,
            y: dailyButton.position.y + buttonSize.height / 2 - 2
        )
        addChild(streakBadge)

        // Das nächste Ziel unter der Knopfreihe, wo vorher die Zahlen
        // standen. 30 Punkt zwischen Zeile und Balken: Bricht die Zeile
        // auf schmalen Geräten um, wächst sie nach unten in genau diesen
        // Abstand hinein und stößt trotzdem nicht an den Balken.
        goalLabel.position = CGPoint(x: w / 2, y: safeBottom + 76)
        addChild(goalLabel)
        goalBar.position = CGPoint(x: w / 2, y: safeBottom + 46)
        addChild(goalBar)

        buttons = [settingsButton, dailyButton, skinsButton, statsButton]
    }

    required init?(coder aDecoder: NSCoder) {
        return nil
    }

    /// `goal` ist das nächstliegende offene Ziel; nil heißt "alles
    /// gesammelt" — dann fällt die Zeile ersatzlos weg, statt einen
    /// vollen Balken ohne Zweck zu zeigen.
    func refresh(bestScore: Int, dailyStreak: Int, goal: Goal?) {
        bestLabel.text = L10n.format("best_score", bestScore)
        bestLabel.isHidden = bestScore <= 0

        streakBadge.count = dailyStreak

        if let goal = goal {
            goalLabel.text = L10n.goalLine(goal)
            goalBar.fraction = goal.fraction
        }
        goalLabel.isHidden = goal == nil
        goalBar.isHidden = goal == nil
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
    private let goalLabel: PixelLabel
    private let goalBar: GoalBar
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
        goalLabel = PixelLabel(text: "", fontSize: 16, color: .white)
        goalBar = GoalBar(width: 220)
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

        // Das nächste Ziel: eine Zeile, ein Balken, mehr nicht. Hier
        // stirbt gerade jemand und will neu starten — der Fortschritt
        // soll ihn anschieben, nicht aufhalten.
        goalLabel.position = CGPoint(x: centerX, y: centerY - 132)
        addChild(goalLabel)
        goalBar.position = CGPoint(x: centerX, y: centerY - 148)
        addChild(goalBar)

        // Kein NOCHMAL-Button: Tap irgendwo startet sofort neu — der
        // blinkende Hinweis ist die einzige Restart-Affordanz.
        let retry = PixelLabel(text: L10n.text("tap_retry"), fontSize: 26, color: .white)
        retry.position = CGPoint(x: centerX, y: centerY - 175)
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
        menuButton.position = CGPoint(x: centerX, y: centerY - 225)
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
        newMedal: Bool,
        // Das nächstliegende offene Ziel — nil, wenn alles gesammelt ist.
        goal: Goal?
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

        if let goal = goal {
            goalLabel.text = L10n.format(
                "goal_progress", L10n.text(goal.titleKey), goal.current, goal.target
            )
            goalBar.fraction = goal.fraction
        }
        goalLabel.isHidden = goal == nil
        goalBar.isHidden = goal == nil
    }

    func buttonHit(at point: CGPoint) -> String? {
        for button in buttons where button.contains(parentPoint: point) {
            return button.buttonName
        }
        return nil
    }
}

/// Vollflächige Spiel-Erklärung über dunklem Scrim; Tap schließt.
///
/// Der Inhalt steht fest — Titel, fünf Regelzeilen, fünf Twists und der
/// Schließen-Hinweis — nur der Platz nicht: Auf einem 4,7-Zoll-Display
/// (667 Punkt) lief der Stapel unten aus dem Bild, ausgerechnet mit dem
/// Hinweis, wie man die Seite wieder loswird.
///
/// Anders als bei der Statistik-Seite lässt sich der Bedarf nicht
/// vorrechnen: Die Regelzeilen brechen um, und wie oft, hängt an Sprache
/// und Gerätebreite. Deshalb wird gestapelt und danach gemessen — steht
/// die letzte Zeile zu tief, wird der Stapel verworfen und mit dem
/// kompakten Maßsatz neu aufgebaut. Ab 4,7 Zoll Plus (736 Punkt) passt
/// der normale Satz, dort sieht die Seite aus wie eh und je.
final class HelpOverlay: SKNode {

    /// Ein Satz Maße für den Stapel. Zwei feste Sätze statt einer
    /// Skalierung des ganzen Knotens: Der Pixel-Font bleibt nur bei
    /// ganzen Schriftgrößen scharf, und genau darauf steht das Spiel.
    private struct Metrics {
        let top: CGFloat
        let title: CGFloat
        let body: CGFloat
        let twistsHeader: CGFloat
        let twistTitle: CGFloat
        let twistText: CGFloat
        let foot: CGFloat
        /// Abstand unter jeder Zeile.
        let lineGap: CGFloat
        /// Abstand von der Twist-Überschrift zu ihrer Erklärung.
        let twistTitleStep: CGFloat
        /// Abstand unter einem fertigen Twist-Block.
        let twistGap: CGFloat

        static let standard = Metrics(
            top: 120, title: 32, body: 15, twistsHeader: 24, twistTitle: 15,
            twistText: 13, foot: 14, lineGap: 8, twistTitleStep: 20, twistGap: 10
        )
        static let compact = Metrics(
            top: 96, title: 26, body: 13, twistsHeader: 20, twistTitle: 13,
            twistText: 12, foot: 13, lineGap: 6, twistTitleStep: 18, twistGap: 8
        )
    }

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

        // Der Inhalt hängt an einem eigenen Knoten, damit ein misslungener
        // Versuch in einem Zug wieder verschwindet — der Scrim bleibt.
        let content = SKNode()
        addChild(content)
        // 32 Punkt über dem unteren Rand: Darunter beginnt bei Geräten mit
        // Home-Indikator dessen Streifen, und eine Zeile, die halb darunter
        // liegt, liest niemand mehr.
        if stack(into: content, sceneSize: sceneSize, metrics: .standard) < 32 {
            content.removeAllChildren()
            _ = stack(into: content, sceneSize: sceneSize, metrics: .compact)
        }
    }

    /// Stapelt die Seite von oben nach unten in `content` und liefert die
    /// Höhe zurück, auf der die letzte Zeile (der Schließen-Hinweis)
    /// gelandet ist — daran entscheidet sich, ob der Maßsatz getaugt hat.
    private func stack(into content: SKNode, sceneSize: CGSize, metrics: Metrics) -> CGFloat {
        let w = sceneSize.width
        let h = sceneSize.height

        var y = h - metrics.top
        var lastY = y
        func addCentered(_ text: String, _ fontSize: CGFloat, _ color: UIColor, shadow: Bool = false, gapAfter: CGFloat = 0) {
            let label = PixelLabel(text: text, fontSize: fontSize, color: color, shadow: shadow, maxWidth: w - 56)
            label.position = CGPoint(x: w / 2, y: y)
            content.addChild(label)
            let height = label.calculateAccumulatedFrame().height
            lastY = y
            y -= max(height, fontSize) + metrics.lineGap + gapAfter
        }

        addCentered(L10n.text("help_title"), metrics.title, .white, shadow: true, gapAfter: 6)
        addCentered(L10n.text("help_line1"), metrics.body, .white)
        addCentered(L10n.text("help_line2"), metrics.body, .white)
        addCentered(L10n.text("help_line3"), metrics.body, Palette.dotBody)
        addCentered(L10n.text("help_line4"), metrics.body, Palette.dotBody)
        addCentered(L10n.text("help_line5"), metrics.body, .white, gapAfter: 10)
        addCentered(L10n.text("help_twists"), metrics.twistsHeader, Palette.bannerOrange, shadow: true, gapAfter: 4)

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
            content.addChild(swatch)
            let title = PixelLabel(
                text: L10n.text(titleKey), fontSize: metrics.twistTitle, color: color, shadow: false
            )
            title.position = CGPoint(x: 60, y: y)
            titleAlignLeft(title)
            content.addChild(title)
            y -= metrics.twistTitleStep
            let text = PixelLabel(
                text: L10n.text(textKey), fontSize: metrics.twistText,
                color: UIColor(white: 1, alpha: 0.85), shadow: false, maxWidth: w - 100
            )
            text.position = CGPoint(x: 60, y: y)
            titleAlignLeft(text)
            content.addChild(text)
            y -= max(text.calculateAccumulatedFrame().height, 16) + metrics.twistGap
        }

        addCentered(L10n.text("help_max_twists"), metrics.foot, .white, gapAfter: 10)
        addCentered(L10n.text("tap_to_close"), metrics.foot, UIColor(white: 1, alpha: 0.6))
        return lastY
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

/// Das Einstellungs-Blatt hinter dem Zahnrad: TON, ERINNERUNG, HILFE.
///
/// Drei Schalter, die früher als Icons im Startbild standen und dort
/// jedes Mal mitgelesen werden mussten, obwohl man sie im Schnitt einmal
/// im Leben anfasst. Hier sind sie mit Worten beschriftet statt mit
/// durchgestrichenen Symbolen — auf einem Blatt, das man absichtlich
/// öffnet, ist Platz für Klartext.
///
/// Kein Zurück-Knopf: Der nächste Tap neben einer Zeile schließt, genau
/// wie bei Hilfe und Statistik, und der Hinweis unten sagt es an.
///
/// Zum Tippen: Das Blatt wertet ausschließlich `touchesBegan` aus. Der
/// Tap, der es öffnet, ist zu diesem Zeitpunkt längst verarbeitet (die
/// Szene hat ihn am Zahnrad verbraucht und kehrt zurück) — er kann also
/// keine Zeile mitnehmen, und es braucht dafür auch keine Sperre wie im
/// Skin-Picker, der bis zum Loslassen zuhört.
final class SettingsOverlay: SKNode {

    /// Was eine Zeile auslöst. `nil` als Ergebnis von `rowHit` heißt
    /// "daneben" und damit: schließen.
    enum Row {
        case sound
        case reminder
        case help
    }

    private let soundLabel: PixelLabel
    private let reminderLabel: PixelLabel
    private var hitAreas: [(row: Row, frame: CGRect)] = []

    private static let rowHeight: CGFloat = 46
    /// Titelzeile samt Abstand darunter.
    private static let titleBlock: CGFloat = 52
    private static let padding: CGFloat = 18

    init(sceneSize: CGSize) {
        let w = sceneSize.width
        let h = sceneSize.height

        soundLabel = PixelLabel(text: "", fontSize: 18, color: Palette.textDark, shadow: false)
        reminderLabel = PixelLabel(text: "", fontSize: 18, color: Palette.textDark, shadow: false)
        super.init()

        let scrim = SKSpriteNode(
            color: Palette.outline.withAlphaComponent(0.92),
            size: CGSize(width: w + 80, height: h + 80)
        )
        scrim.position = CGPoint(x: w / 2, y: h / 2)
        addChild(scrim)

        // Das Blatt: Sandfläche mit demselben Treppenrahmen wie die
        // Knöpfe. Drei Zeilen brauchen 226 Punkt — auf dem kleinsten
        // unterstützten Gerät (667 Punkt) bleiben oben und unten je gut
        // 200 Punkt Luft, ein kompakter Maßsatz wie in der Hilfe erübrigt
        // sich deshalb.
        let sheetWidth = min(w - 48, 300)
        let rowsHeight = SettingsOverlay.rowHeight * 3
        let sheetHeight = SettingsOverlay.padding * 2 + SettingsOverlay.titleBlock + rowsHeight
        let centerX = w / 2
        let centerY = h / 2
        let sheetSize = CGSize(width: sheetWidth, height: sheetHeight)

        let sheet = SKNode()
        sheet.position = CGPoint(x: centerX, y: centerY)
        sheet.addChild(SKSpriteNode(color: Palette.panelSand, size: sheetSize))
        PixelButton.addSteppedBorder(to: sheet, size: sheetSize, color: Palette.outline, pixelSize: 4)
        addChild(sheet)

        let sheetTop = centerY + sheetHeight / 2
        let title = PixelLabel(
            text: L10n.text("settings"), fontSize: 22,
            color: Palette.textDark, shadow: false
        )
        title.position = CGPoint(x: centerX, y: sheetTop - SettingsOverlay.padding - 14)
        addChild(title)

        // Die Zeilen von oben nach unten; die Trefferfläche ist jeweils
        // die volle Blattbreite, nicht nur der Text — ein Schalter, den
        // man auf den Buchstaben genau treffen muss, ist keiner.
        let rowsTop = sheetTop - SettingsOverlay.padding - SettingsOverlay.titleBlock
        var y = rowsTop - SettingsOverlay.rowHeight / 2
        for (row, label) in [
            (Row.sound, soundLabel),
            (Row.reminder, reminderLabel),
            (Row.help, PixelLabel(
                text: L10n.text("settings_help"), fontSize: 18,
                color: Palette.textDark, shadow: false
            ))
        ] {
            label.position = CGPoint(x: centerX, y: y)
            addChild(label)
            hitAreas.append((
                row: row,
                frame: CGRect(
                    x: centerX - sheetWidth / 2,
                    y: y - SettingsOverlay.rowHeight / 2,
                    width: sheetWidth,
                    height: SettingsOverlay.rowHeight
                )
            ))
            y -= SettingsOverlay.rowHeight
        }

        let closeHint = PixelLabel(
            text: L10n.text("tap_to_close"), fontSize: 14,
            color: UIColor(white: 1, alpha: 0.6), shadow: false
        )
        closeHint.position = CGPoint(x: centerX, y: centerY - sheetHeight / 2 - 30)
        addChild(closeHint)
    }

    required init?(coder aDecoder: NSCoder) {
        return nil
    }

    /// Beschriftungen an den Stand anpassen. Ausgeschrieben statt als
    /// Zustand eines Symbols: "TON: AUS" lässt keine zwei Lesarten zu,
    /// ein durchgestrichener Lautsprecher schon.
    func refresh(soundOn: Bool, reminderOn: Bool) {
        soundLabel.text = L10n.text(soundOn ? "sound_on" : "sound_off")
        reminderLabel.text = L10n.text(reminderOn ? "reminder_on" : "reminder_off")
    }

    /// Welche Zeile getroffen wurde — nil heißt: daneben, also schließen.
    func rowHit(at point: CGPoint) -> Row? {
        for area in hitAreas where area.frame.contains(point) {
            return area.row
        }
        return nil
    }
}

/// Die Statistik-Seite: alle Zähler auf einen Blick und darunter die
/// nächsten Freischaltungen mit Balken. Vollflächig über dunklem Scrim,
/// im Stil der Hilfe; ein Tap irgendwo schließt.
///
/// Der Anlass: Seit v2.20 laufen vier Ausdauer-Achsen mit, und sichtbar
/// war davon nichts. Wer bei Rekord 25 hängenbleibt, sah nur eine Zahl —
/// dass der nächste Skin in 30 Läufen fällt, stand nirgends.
///
/// Anders als der Skin-Picker scrollt hier nichts: Neun Zeilen und drei
/// Ziele passen auf jedes iPhone, und ein Scroll-Fenster für eine Seite,
/// die ohnehin ganz sichtbar ist, wäre nur Mechanik ohne Zweck.
final class StatsOverlay: SKNode {

    private let sceneSize: CGSize
    private var valueLabels: [PixelLabel] = []
    private var goalNodes: [SKNode] = []
    private let goalsHeader: PixelLabel
    private let firstGoalY: CGFloat

    /// Wie viele Ziele auf DIESES Gerät passen. Auf einem 4,7-Zoll-Display
    /// bleibt unter neun Zeilen weniger Platz als auf einem 6,7-Zoll —
    /// lieber ein Ziel weniger als eines hinter dem Schließen-Hinweis.
    private let maxGoals: Int

    private static let rowStep: CGFloat = 26
    private static let goalStep: CGFloat = 46

    /// Die Zähler in der Reihenfolge, in der sie stehen — die Werte
    /// füllt `refresh` nach.
    private static let rowKeys = [
        "record_label", "stats_runs", "stats_total_score", "stats_days",
        "stats_months", "stats_perfect", "stats_daily_streak", "skins", "scenes",
        "sounds"
    ]

    init(sceneSize: CGSize) {
        self.sceneSize = sceneSize
        let w = sceneSize.width
        let h = sceneSize.height
        goalsHeader = PixelLabel(
            text: L10n.text("stats_goals"), fontSize: 18,
            color: Palette.bannerOrange, shadow: true
        )
        // Über lokale Größen statt über die Eigenschaften: Vor super.init()
        // darf ein Initialisierer eigene Werte setzen, aber keine lesen.
        let rowsHeight = CGFloat(StatsOverlay.rowKeys.count) * StatsOverlay.rowStep
        let goalTop = h - 175 - rowsHeight - 34
        firstGoalY = goalTop
        // Bis 78 Punkt über dem unteren Rand: Darunter steht der
        // Schließen-Hinweis.
        maxGoals = max(0, min(Progress.pageGoals, Int((goalTop - 78) / StatsOverlay.goalStep)))
        super.init()

        let scrim = SKSpriteNode(
            color: Palette.outline.withAlphaComponent(0.92),
            size: CGSize(width: w + 80, height: h + 80)
        )
        scrim.position = CGPoint(x: w / 2, y: h / 2)
        addChild(scrim)

        let title = PixelLabel(text: L10n.text("stats"), fontSize: 32, color: .white)
        title.position = CGPoint(x: w / 2, y: h - 120)
        addChild(title)

        // Eine Zeile "LAEUFE ........ 218": Beschriftung links am Rand,
        // Zahl rechts am Rand — dieselben 40 Punkt wie im Skin-Picker.
        var y = h - 175
        for key in StatsOverlay.rowKeys {
            let label = PixelLabel(
                text: L10n.text(key), fontSize: 16,
                color: UIColor(white: 1, alpha: 0.8), shadow: false
            )
            label.position = CGPoint(x: 40, y: y)
            align(label, mode: .left)
            addChild(label)

            let value = PixelLabel(text: "", fontSize: 18, color: Palette.dotBody, shadow: false)
            value.position = CGPoint(x: w - 40, y: y)
            align(value, mode: .right)
            addChild(value)
            valueLabels.append(value)

            y -= StatsOverlay.rowStep
        }

        goalsHeader.position = CGPoint(x: 40, y: y - 14)
        align(goalsHeader, mode: .left)
        addChild(goalsHeader)

        let closeHint = PixelLabel(
            text: L10n.text("tap_to_close"), fontSize: 14,
            color: UIColor(white: 1, alpha: 0.6), shadow: false
        )
        closeHint.position = CGPoint(x: w / 2, y: 48)
        addChild(closeHint)
    }

    required init?(coder aDecoder: NSCoder) {
        return nil
    }

    private func align(_ node: SKNode, mode: SKLabelHorizontalAlignmentMode) {
        for child in node.children {
            if let label = child as? SKLabelNode {
                label.horizontalAlignmentMode = mode
            }
        }
    }

    func refresh(stats: DotSkin.Stats, goals: [Goal]) {
        let values = [
            String(stats.bestScore),
            String(stats.runCount),
            String(stats.totalScore),
            String(stats.daysPlayed),
            String(stats.monthsPlayed),
            String(stats.bestPerfectStreak),
            String(stats.bestDailyStreak),
            // Alle drei Sammlungen als Stand "12/35": Die Zahl allein
            // sagt nichts, erst das Verhältnis zeigt, wie weit es noch ist.
            "\(DotSkin.unlockedCount(stats))/\(DotSkin.collectableCount())",
            "\(ScenePaint.unlockedCount(stats))/\(SceneId.allCases.count)",
            "\(SoundBank.unlockedCount(stats))/\(SoundSetId.allCases.count)"
        ]
        for (label, value) in zip(valueLabels, values) {
            label.text = value
        }

        // Die Ziele wechseln mit jedem Lauf — deshalb neu aufgebaut statt
        // vorgehalten; drei Zeilen kosten nichts.
        goalNodes.forEach { $0.removeFromParent() }
        goalNodes = []
        let visible = Array(goals.prefix(maxGoals))
        goalsHeader.isHidden = visible.isEmpty

        var y = firstGoalY
        for goal in visible {
            let label = PixelLabel(
                text: L10n.format(
                    "goal_progress", L10n.text(goal.titleKey), goal.current, goal.target
                ),
                fontSize: 16,
                color: .white,
                shadow: false
            )
            label.position = CGPoint(x: 40, y: y)
            align(label, mode: .left)
            addChild(label)
            goalNodes.append(label)

            let bar = GoalBar(width: sceneSize.width - 80)
            bar.fraction = goal.fraction
            bar.position = CGPoint(x: sceneSize.width / 2, y: y - 18)
            addChild(bar)
            goalNodes.append(bar)

            y -= StatsOverlay.goalStep
        }
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
        case selectScene(SceneId)
        case selectSound(SoundSetId)
        case close
    }

    private struct Row {
        let skin: DotSkin
        let centerY: CGFloat
        let swatch: SKSpriteNode
        let titleLabel: PixelLabel
        let statusLabel: PixelLabel
    }

    /// Dieselbe Zeile, nur fuer eine Kulisse. Zwei Typen statt eines
    /// generischen: Die beiden Sammlungen haben nichts gemeinsam ausser
    /// dem Aussehen ihrer Zeile.
    private struct SceneRow {
        let scene: SceneId
        let centerY: CGFloat
        let swatch: SKSpriteNode
        let titleLabel: PixelLabel
        let statusLabel: PixelLabel
    }

    /// Und dieselbe Zeile fuer ein Ton-Set. Auch hier ein eigener Typ:
    /// Die drei Sammlungen haben nichts gemeinsam ausser dem Aussehen
    /// ihrer Zeile.
    private struct SoundRow {
        let sound: SoundSetId
        let centerY: CGFloat
        let swatch: SKSpriteNode
        let titleLabel: PixelLabel
        let statusLabel: PixelLabel
    }

    private var rows: [Row] = []
    private var sceneRows: [SceneRow] = []
    private var soundRows: [SoundRow] = []
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

        // Die Kulissen stehen ganz oben und vor allen Skin-Familien: Es
        // sind nur sechs, sie wirken auf das ganze Bild, und wer das Menue
        // oeffnet, soll sie nicht erst suchen muessen.
        let scenesHeader = PixelLabel(
            text: L10n.text("scenes"), fontSize: 15,
            color: Palette.dotBody, shadow: false
        )
        scenesHeader.position = CGPoint(x: 40, y: y)
        alignLeft(scenesHeader)
        contentNode.addChild(scenesHeader)
        y -= headerHeight

        for scene in SceneId.allCases {
            let swatch = SKSpriteNode(texture: PixelArt.scenePreviewTexture(scene: scene, size: 36))
            swatch.size = CGSize(width: 36, height: 36)
            swatch.position = CGPoint(x: 64, y: y)
            contentNode.addChild(swatch)

            let titleLabel = PixelLabel(
                text: L10n.text(scene.titleKey), fontSize: 20, color: .white, shadow: false
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

            sceneRows.append(SceneRow(
                scene: scene,
                centerY: y,
                swatch: swatch,
                titleLabel: titleLabel,
                statusLabel: statusLabel
            ))
            y -= rowHeight
        }
        y -= 8

        // Die Ton-Sets stehen direkt hinter den Kulissen und vor den
        // Skins: Es sind drei, sie wirken wie die Kulisse auf den ganzen
        // Lauf, und die Hoerprobe beim Antippen soll nicht hinter 42
        // Vogel-Zeilen liegen.
        let soundsHeader = PixelLabel(
            text: L10n.text("sounds"), fontSize: 15,
            color: Palette.dotBody, shadow: false
        )
        soundsHeader.position = CGPoint(x: 40, y: y)
        alignLeft(soundsHeader)
        contentNode.addChild(soundsHeader)
        y -= headerHeight

        for sound in SoundSetId.allCases {
            let swatch = SKSpriteNode(texture: PixelArt.soundPreviewTexture(sound: sound, size: 36))
            swatch.size = CGSize(width: 36, height: 36)
            swatch.position = CGPoint(x: 64, y: y)
            contentNode.addChild(swatch)

            let titleLabel = PixelLabel(
                text: L10n.text(sound.titleKey), fontSize: 20, color: .white, shadow: false
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

            soundRows.append(SoundRow(
                sound: sound,
                centerY: y,
                swatch: swatch,
                titleLabel: titleLabel,
                statusLabel: statusLabel
            ))
            y -= rowHeight
        }
        y -= 8

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
        for row in sceneRows where abs(contentY - row.centerY) <= rowHeight / 2 {
            // Auf eine gesperrte Zeile getippt: Der Picker schliesst wie
            // bei jedem Tipp daneben.
            return row.scene.isUnlocked(stats) ? .selectScene(row.scene) : .close
        }
        for row in soundRows where abs(contentY - row.centerY) <= rowHeight / 2 {
            // Ein Tipp auf ein offenes Set waehlt es UND spielt die
            // Hoerprobe; der Picker bleibt dabei offen (siehe GameScene).
            return SoundBank.isUnlocked(row.sound, stats) ? .selectSound(row.sound) : .close
        }
        for row in rows where abs(contentY - row.centerY) <= rowHeight / 2 {
            // Auf eine gesperrte Zeile getippt: Der Picker schließt wie
            // bei jedem Tipp daneben — der Hinweis unten verspricht genau das.
            return row.skin.isUnlocked(stats) ? .select(row.skin) : .close
        }
        return .close
    }

    /// [scrollToSelected] springt beim Oeffnen zum gewaehlten Skin. Beim
    /// Nachziehen nach einer Ton-Set-Wahl darf das nicht passieren: Die
    /// Liste wuerde unter dem Finger wegspringen, obwohl der Tipp ganz
    /// oben war.
    func refresh(
        stats: DotSkin.Stats,
        selected: DotSkin,
        selectedScene: SceneId,
        selectedSound: SoundSetId,
        scrollToSelected: Bool = true
    ) {
        for row in soundRows {
            let unlocked = SoundBank.isUnlocked(row.sound, stats)
            row.swatch.alpha = unlocked ? 1.0 : 0.3
            row.titleLabel.color = unlocked ? .white : UIColor(white: 1, alpha: 0.45)
            if row.sound == selectedSound {
                row.statusLabel.text = L10n.text("skin_selected")
                row.statusLabel.color = Palette.dotBody
            } else if unlocked {
                row.statusLabel.text = L10n.text("sound_tap_hear")
                row.statusLabel.color = UIColor(white: 1, alpha: 0.7)
            } else {
                row.statusLabel.text = row.sound.unlockHintKey.map { L10n.text($0) } ?? ""
                row.statusLabel.color = UIColor(white: 1, alpha: 0.45)
            }
        }
        for row in sceneRows {
            let unlocked = row.scene.isUnlocked(stats)
            row.swatch.alpha = unlocked ? 1.0 : 0.3
            row.titleLabel.color = unlocked ? .white : UIColor(white: 1, alpha: 0.45)
            if row.scene == selectedScene {
                row.statusLabel.text = L10n.text("skin_selected")
                row.statusLabel.color = Palette.dotBody
            } else if unlocked {
                row.statusLabel.text = L10n.text("skin_tap_select")
                row.statusLabel.color = UIColor(white: 1, alpha: 0.7)
            } else {
                row.statusLabel.text = row.scene.unlockHintKey.map { L10n.text($0) } ?? ""
                row.statusLabel.color = UIColor(white: 1, alpha: 0.45)
            }
        }
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
        if scrollToSelected {
            scrollTo(selected)
        }
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

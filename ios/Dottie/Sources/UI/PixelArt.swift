import SpriteKit
import UIKit

/// Erzeugt die Pixel-Look-Texturen (Vogel, Wolken, Bäume, Boden, Medaille)
/// per CGContext — dieselben Block-Formeln wie die Android-Canvas-Zeichnung
/// in TimingGameScreen.kt / GameOverlays.kt. UIGraphicsImageRenderer nutzt
/// UIKit-Koordinaten (y nach unten), deshalb lässt sich der Android-Code
/// 1:1 übernehmen; erst die fertige Textur landet im SpriteKit-Raum.
enum PixelArt {

    /// Raster des Pixel-Kreises (GRID in GameOverlays.kt).
    static let grid: CGFloat = 13

    static func texture(size: CGSize, draw: @escaping (CGContext) -> Void) -> SKTexture {
        let format = UIGraphicsImageRendererFormat()
        format.scale = UIScreen.main.scale
        format.opaque = false
        let renderer = UIGraphicsImageRenderer(size: size, format: format)
        let image = renderer.image { context in
            draw(context.cgContext)
        }
        let texture = SKTexture(image: image)
        texture.filteringMode = .nearest
        return texture
    }

    static func fill(_ ctx: CGContext, _ color: UIColor, _ x: CGFloat, _ y: CGFloat, _ w: CGFloat, _ h: CGFloat) {
        ctx.setFillColor(color.cgColor)
        ctx.fill(CGRect(x: x, y: y, width: w, height: h))
    }

    /// Blockiger "Pixel"-Kreis aus Rasterzellen (drawPixelCircle). Die
    /// Füllfarbe kommt pro Feld aus `cell` — so zeichnet dieselbe Routine
    /// einfarbige, gemusterte und animierte Skins (siehe SkinPaint).
    static func pixelCircle(
        _ ctx: CGContext,
        rect: CGRect,
        outline: UIColor,
        cell: (Int, Int) -> UIColor
    ) {
        let n = Int(grid)
        let radius = rect.width / 2
        let u = (radius * 2) / grid
        let mid = (grid - 1) / 2
        let rr = grid / 2 - 0.25

        for row in 0..<n {
            for col in 0..<n {
                let dx = CGFloat(col) - mid
                let dy = CGFloat(row) - mid
                let dist = sqrt(dx * dx + dy * dy)
                if dist <= rr {
                    let cellColor = dist > rr - 1.1 ? outline : cell(col, row)
                    fill(
                        ctx, cellColor,
                        rect.minX + CGFloat(col) * u,
                        rect.minY + CGFloat(row) * u,
                        u + 0.5, u + 0.5
                    )
                }
            }
        }
    }

    /// Einfarbige Variante mit Schattenseite — für Münzen und Deko.
    static func pixelCircle(
        _ ctx: CGContext,
        rect: CGRect,
        color: UIColor,
        outline: UIColor,
        shade: UIColor
    ) {
        pixelCircle(ctx, rect: rect, outline: outline) { col, row in
            CGFloat(row + col) > grid * 1.15 ? shade : color
        }
    }

    /// Pixel-Vogel: Kreis-Körper mit Glanzpunkt, Auge und Pupille —
    /// gespiegelt je nach Flugrichtung (drawTimingDot / drawBird). Der
    /// Zustand entscheidet bei bewegten und reagierenden Skins über die
    /// Farbe; GameScene rastert nur neu, wenn sich SkinPaint.frameKey
    /// ändert.
    static func birdTexture(
        skin: DotSkin,
        facingLeft: Bool,
        diameter: CGFloat,
        state: SkinPaint.State = .still
    ) -> SKTexture {
        let d = max(diameter, 8)
        return texture(size: CGSize(width: d, height: d)) { ctx in
            let shine = UIColor(rgb: skin.shineColor(state))
            pixelCircle(
                ctx,
                rect: CGRect(x: 0, y: 0, width: d, height: d),
                outline: Palette.outline
            ) { col, row in
                UIColor(rgb: skin.cell(col, row, state))
            }
            let u = d / grid
            func block(_ col: CGFloat, _ row: CGFloat, _ cols: CGFloat, _ rows: CGFloat, _ color: UIColor) {
                fill(ctx, color, col * u, row * u, cols * u, rows * u)
            }
            // Auf sehr hellen Skins (Koi, Chrom) bekommt das Auge zum
            // Körper hin eine Kontur, sonst ginge das Weiß im Körper
            // unter; wo der Körper von selbst Kontrast hat, bleibt sie weg.
            let eyeOutline = skin.needsEyeOutline
            if facingLeft {
                block(grid - 4.5, 2.5, 2, 2, shine)
                if eyeOutline {
                    block(5.5, 3, 0.5, 4, Palette.outline)
                    block(2, 2.5, 3.5, 0.5, Palette.outline)
                    block(2, 7, 3.5, 0.5, Palette.outline)
                }
                block(2, 3, 3.5, 4, UIColor.white)
                block(2, 4, 1.5, 2, Palette.outline)
            } else {
                block(2.5, 2.5, 2, 2, shine)
                if eyeOutline {
                    block(7, 3, 0.5, 4, Palette.outline)
                    block(7.5, 2.5, 3.5, 0.5, Palette.outline)
                    block(7.5, 7, 3.5, 0.5, Palette.outline)
                }
                block(7.5, 3, 3.5, 4, UIColor.white)
                block(9.5, 4, 1.5, 2, Palette.outline)
            }
        }
    }

    /// Kleine Skin-Vorschau für die Auswahl: nur der Körper im Muster des
    /// Skins, ohne Gesicht — bei 36 Punkten wäre es sonst Matsch. Bewegte
    /// Skins stehen still (Zeitpunkt 0), Uhr und Kalender kommen aber vom
    /// Gerät: TAGESZEIT und JAHRESZEIT sollen in der Auswahl das Kleid
    /// zeigen, das sie im Lauf gerade tragen.
    static func skinPreviewTexture(skin: DotSkin, size: CGFloat) -> SKTexture {
        let d = max(size, 8)
        let state = SkinPaint.State.now()
        return texture(size: CGSize(width: d, height: d)) { ctx in
            pixelCircle(
                ctx,
                rect: CGRect(x: 0, y: 0, width: d, height: d),
                outline: Palette.outline
            ) { col, row in
                UIColor(rgb: skin.cell(col, row, state))
            }
        }
    }

    /// Blockige Retro-Wolke aus drei gestapelten Rechtecken (drawCloud).
    /// Die Farbe kommt seit den Kulissen von außen.
    static func cloudTexture(cell: CGFloat, color: UIColor) -> SKTexture {
        let u = cell * 2
        let size = CGSize(width: u * 14, height: u * 6.5)
        return texture(size: size) { ctx in
            fill(ctx, color, 0, u * 3.5, u * 14, u * 3)
            fill(ctx, color, u * 2, u * 1.5, u * 7, u * 2)
            fill(ctx, color, u * 4, 0, u * 4, u * 1.5)
        }
    }

    /// Textur einer Requisite. Der Wind steckt hier nicht drin — er kommt
    /// wie bisher als Sprite-Rotation, damit dieselbe Textur für alle
    /// Kopien einer Requisite reicht.
    static func propTexture(
        _ prop: ScenePaint.Prop,
        s: CGFloat,
        cell: CGFloat,
        accent: UInt32
    ) -> SKTexture {
        let size = propSize(prop.shape, s: s, cell: cell)
        let cx = size.width / 2
        let groundY = size.height
        let dark = UIColor(rgb: prop.dark)
        let body = UIColor(rgb: prop.body)
        let light = UIColor(rgb: prop.light)
        let stem = UIColor(rgb: prop.stem)
        let stemShade = UIColor(rgb: prop.stemShade)
        let accentColor = UIColor(rgb: accent)
        return texture(size: size) { ctx in
            switch prop.shape {
            case .baum:
                drawTree(ctx, cx: cx, groundY: groundY, s: s, cell: cell,
                         dark: dark, body: body, light: light,
                         stem: stem, stemShade: stemShade)
            case .strauch:
                drawBush(ctx, cx: cx, groundY: groundY, s: s, cell: cell,
                         dark: dark, body: body, light: light)
            case .blume:
                drawFlower(ctx, cx: cx, groundY: groundY, s: s, cell: cell,
                           dark: dark, body: body, light: light, petal: accentColor)
            case .kaktus:
                drawCactus(ctx, cx: cx, groundY: groundY, s: s, cell: cell,
                           dark: dark, body: body, light: light, bloom: accentColor)
            case .welle:
                drawWave(ctx, cx: cx, groundY: groundY, s: s, cell: cell,
                         dark: dark, body: body, light: light, foam: accentColor)
            case .nadelbaum:
                drawFir(ctx, cx: cx, groundY: groundY, s: s, cell: cell,
                        dark: dark, body: body, light: light,
                        stem: stem, stemShade: stemShade)
            case .hochhaus:
                drawTower(ctx, cx: cx, groundY: groundY, s: s, cell: cell,
                          dark: dark, body: body, light: light, window: accentColor)
            case .fels:
                drawRock(ctx, cx: cx, groundY: groundY, s: s, cell: cell,
                         dark: dark, body: body, light: light)
            }
        }
    }

    /// Wie groß die Textur einer Form sein muss, damit nichts abgeschnitten
    /// wird — die Breite folgt der breitesten Lage, die Höhe der höchsten.
    private static func propSize(_ shape: PropShape, s: CGFloat, cell: CGFloat) -> CGSize {
        switch shape {
        case .baum: return CGSize(width: s * 1.6 + cell * 4, height: s * 1.8 + cell * 3)
        case .strauch: return CGSize(width: s * 2.7 + cell * 4, height: s * 1.8 + cell * 3)
        case .blume: return CGSize(width: s * 1.6 + cell * 6, height: s * 2.0 + cell * 3)
        case .kaktus: return CGSize(width: s * 1.5 + cell * 4, height: s * 1.8 + cell * 3)
        case .welle: return CGSize(width: s * 3.0 + cell * 4, height: s * 0.9 + cell * 3)
        case .nadelbaum: return CGSize(width: s * 1.5 + cell * 4, height: s * 1.8 + cell * 3)
        case .hochhaus: return CGSize(width: s * 0.9 + cell * 4, height: s * 2.4 + cell * 3)
        case .fels: return CGSize(width: s * 2.2 + cell * 4, height: s * 1.4 + cell * 3)
        }
    }

    /// Formen mit sich überlappenden Teilen (Kaktus) brauchen zwei
    /// Durchgänge: erst alle Konturen, dann alle Füllungen. Sonst legt die
    /// Kontur des einen Blocks einen Balken über die Füllung des anderen.
    private static func outlinedBlocks(
        _ ctx: CGContext,
        cell: CGFloat,
        blocks: [(CGFloat, CGFloat, CGFloat, CGFloat)],
        color: UIColor
    ) {
        for b in blocks {
            fill(ctx, Palette.outline, b.0 - cell, b.1 - cell, b.2 + cell * 2, b.3 + cell * 2)
        }
        for b in blocks {
            fill(ctx, color, b.0, b.1, b.2, b.3)
        }
    }

    /// Pixel-Baum: Stamm mit Schattenseite, dreistufige Krone.
    private static func drawTree(
        _ ctx: CGContext, cx: CGFloat, groundY: CGFloat, s: CGFloat, cell: CGFloat,
        dark: UIColor, body: UIColor, light: UIColor, stem: UIColor, stemShade: UIColor
    ) {
        let trunkW = s * 0.30
        let trunkH = s * 0.60
        fill(ctx, Palette.outline, cx - trunkW / 2 - cell, groundY - trunkH - cell,
             trunkW + cell * 2, trunkH + cell)
        fill(ctx, stem, cx - trunkW / 2, groundY - trunkH, trunkW, trunkH)
        fill(ctx, stemShade, cx, groundY - trunkH, trunkW / 2, trunkH)

        let layers: [(CGFloat, CGFloat, UIColor)] = [
            (s * 1.6, s * 0.45, dark),
            (s * 1.2, s * 0.40, body),
            (s * 0.7, s * 0.35, light)
        ]
        var layerTop = groundY - trunkH
        for (lw, lh, color) in layers {
            layerTop -= lh
            fill(ctx, Palette.outline, cx - lw / 2 - cell, layerTop - cell,
                 lw + cell * 2, lh + cell * 2)
            fill(ctx, color, cx - lw / 2, layerTop, lw, lh)
        }
    }

    /// Pixel-Strauch: runde Beeren-Silhouette mit Licht-Tupfern.
    private static func drawBush(
        _ ctx: CGContext, cx: CGFloat, groundY: CGFloat, s: CGFloat, cell: CGFloat,
        dark: UIColor, body: UIColor, light: UIColor
    ) {
        let layers: [(CGFloat, CGFloat, UIColor)] = [
            (s * 2.1, s * 0.55, dark),
            (s * 2.7, s * 0.70, body),
            (s * 1.5, s * 0.55, light)
        ]
        var layerTop = groundY
        for (lw, lh, color) in layers {
            layerTop -= lh
            fill(ctx, Palette.outline, cx - lw / 2 - cell, layerTop - cell,
                 lw + cell * 2, lh + cell * 2)
            fill(ctx, color, cx - lw / 2, layerTop, lw, lh)
        }
        let u = cell * 1.5
        fill(ctx, light, cx - s * 1.0, groundY - s * 1.05, u * 2, u)
        fill(ctx, light, cx + s * 0.35, groundY - s * 0.8, u, u)
    }

    /// Pixel-Blume: Stiel mit Blättern und großer Blüte.
    private static func drawFlower(
        _ ctx: CGContext, cx: CGFloat, groundY: CGFloat, s: CGFloat, cell: CGFloat,
        dark: UIColor, body: UIColor, light: UIColor, petal: UIColor
    ) {
        let stemH = s * 1.15
        let bx = cx
        let by = groundY - stemH

        fill(ctx, Palette.outline, cx - cell * 1.5, by, cell * 3, stemH)
        fill(ctx, dark, cx - cell * 0.75, by, cell * 1.5, stemH)

        let leafY = groundY - stemH * 0.45
        fill(ctx, Palette.outline, cx - s * 0.6 - cell, leafY - cell, s * 0.6 + cell * 2, cell * 3)
        fill(ctx, body, cx - s * 0.6, leafY, s * 0.6, cell * 1.5)
        fill(ctx, Palette.outline, cx - cell, leafY + cell * 3, s * 0.55 + cell * 2, cell * 3)
        fill(ctx, body, cx, leafY + cell * 4, s * 0.55, cell * 1.5)

        let u = s * 0.38
        func blossomBlock(_ x: CGFloat, _ y: CGFloat, _ color: UIColor) {
            fill(ctx, Palette.outline, x - cell, y - cell, u + cell * 2, u + cell * 2)
            fill(ctx, color, x, y, u, u)
        }
        blossomBlock(bx - u / 2, by - u * 1.5, petal)
        blossomBlock(bx - u * 1.5, by - u / 2, petal)
        blossomBlock(bx + u / 2, by - u / 2, petal)
        blossomBlock(bx - u / 2, by + u / 2, petal)
        blossomBlock(bx - u / 2, by - u / 2, light)
    }

    /// Kaktus: Säule mit zwei versetzten Armen und einer Blüte obendrauf.
    /// Die Arme sitzen auf verschiedenen Höhen — zwei gleich hohe Arme
    /// sähen aus wie ein Zeichen, nicht wie eine Pflanze.
    private static func drawCactus(
        _ ctx: CGContext, cx: CGFloat, groundY: CGFloat, s: CGFloat, cell: CGFloat,
        dark: UIColor, body: UIColor, light: UIColor, bloom: UIColor
    ) {
        let stemW = s * 0.34
        let stemH = s * 1.5
        let armW = s * 0.20
        let leftY = groundY - stemH * 0.55
        let rightY = groundY - stemH * 0.78

        outlinedBlocks(ctx, cell: cell, blocks: [
            (cx - stemW / 2, groundY - stemH, stemW, stemH),
            (cx - s * 0.75, leftY, s * 0.75, armW),
            (cx - s * 0.75, leftY - s * 0.45, armW, s * 0.45 + armW),
            (cx, rightY, s * 0.75, armW),
            (cx + s * 0.75 - armW, rightY - s * 0.38, armW, s * 0.38 + armW)
        ], color: body)

        fill(ctx, dark, cx + stemW * 0.12, groundY - stemH, stemW * 0.38, stemH)
        fill(ctx, light, cx - stemW / 2, groundY - stemH, stemW * 0.26, stemH * 0.92)

        let fw = s * 0.26
        fill(ctx, Palette.outline, cx - fw / 2 - cell, groundY - stemH - fw - cell,
             fw + cell * 2, fw + cell * 2)
        fill(ctx, bloom, cx - fw / 2, groundY - stemH - fw, fw, fw)
    }

    /// Welle: flacher, breiter Stapel mit Schaumtupfern. Bewusst breiter
    /// als hoch — eine Welle, die wie ein Busch stünde, läse sich als
    /// Pflanze.
    private static func drawWave(
        _ ctx: CGContext, cx: CGFloat, groundY: CGFloat, s: CGFloat, cell: CGFloat,
        dark: UIColor, body: UIColor, light: UIColor, foam: UIColor
    ) {
        let layers: [(CGFloat, CGFloat, UIColor)] = [
            (s * 3.0, s * 0.30, dark),
            (s * 2.2, s * 0.26, body),
            (s * 1.2, s * 0.22, light)
        ]
        var layerTop = groundY
        for (lw, lh, color) in layers {
            layerTop -= lh
            fill(ctx, Palette.outline, cx - lw / 2 - cell, layerTop - cell,
                 lw + cell * 2, lh + cell * 2)
            fill(ctx, color, cx - lw / 2, layerTop, lw, lh)
        }
        let u = cell * 1.5
        fill(ctx, foam, cx - s * 0.5, layerTop, u * 2, u)
        fill(ctx, foam, cx + s * 0.2, layerTop + u, u, u)
    }

    /// Nadelbaum: schmaler Stamm, drei spitze Lagen, helle Spitze obendrauf.
    private static func drawFir(
        _ ctx: CGContext, cx: CGFloat, groundY: CGFloat, s: CGFloat, cell: CGFloat,
        dark: UIColor, body: UIColor, light: UIColor, stem: UIColor, stemShade: UIColor
    ) {
        let trunkW = s * 0.22
        let trunkH = s * 0.30
        fill(ctx, Palette.outline, cx - trunkW / 2 - cell, groundY - trunkH - cell,
             trunkW + cell * 2, trunkH + cell)
        fill(ctx, stem, cx - trunkW / 2, groundY - trunkH, trunkW, trunkH)
        fill(ctx, stemShade, cx, groundY - trunkH, trunkW / 2, trunkH)

        let layers: [(CGFloat, CGFloat, UIColor)] = [
            (s * 1.50, s * 0.42, dark),
            (s * 1.05, s * 0.38, body),
            (s * 0.60, s * 0.34, body)
        ]
        var layerTop = groundY - trunkH
        for (lw, lh, color) in layers {
            layerTop -= lh
            fill(ctx, Palette.outline, cx - lw / 2 - cell, layerTop - cell,
                 lw + cell * 2, lh + cell * 2)
            fill(ctx, color, cx - lw / 2, layerTop, lw, lh)
        }

        let tw = s * 0.24
        let th = s * 0.26
        fill(ctx, Palette.outline, cx - tw / 2 - cell, layerTop - th - cell,
             tw + cell * 2, th + cell * 2)
        fill(ctx, light, cx - tw / 2, layerTop - th, tw, th)
    }

    /// Hochhaus: ein Block mit Schattenseite, heller Dachkante und einem
    /// Fensterraster.
    private static func drawTower(
        _ ctx: CGContext, cx: CGFloat, groundY: CGFloat, s: CGFloat, cell: CGFloat,
        dark: UIColor, body: UIColor, light: UIColor, window: UIColor
    ) {
        let w = s * 0.9
        let hgt = s * 2.4
        fill(ctx, Palette.outline, cx - w / 2 - cell, groundY - hgt - cell,
             w + cell * 2, hgt + cell)
        fill(ctx, body, cx - w / 2, groundY - hgt, w, hgt)
        fill(ctx, dark, cx, groundY - hgt, w / 2, hgt)
        fill(ctx, light, cx - w / 2, groundY - hgt, w, s * 0.16)

        // Jedes dritte Fenster bleibt dunkel, sonst sähe die Fassade aus
        // wie ein Schachbrett aus Licht.
        let uw = w * 0.22
        let uh = s * 0.16
        for r in 0..<5 {
            let fy = groundY - hgt + s * 0.34 + CGFloat(r) * s * 0.36
            if fy + uh > groundY - s * 0.1 {
                break
            }
            for c in 0..<2 {
                let fx = cx - w * 0.30 + CGFloat(c) * w * 0.34
                fill(ctx, (r + c) % 3 == 0 ? dark : window, fx, fy, uw, uh)
            }
        }
    }

    /// Fels: pyramidenförmiger Stapel, unten am breitesten.
    private static func drawRock(
        _ ctx: CGContext, cx: CGFloat, groundY: CGFloat, s: CGFloat, cell: CGFloat,
        dark: UIColor, body: UIColor, light: UIColor
    ) {
        let layers: [(CGFloat, CGFloat, UIColor)] = [
            (s * 2.2, s * 0.50, dark),
            (s * 1.6, s * 0.45, body),
            (s * 0.8, s * 0.35, light)
        ]
        var layerTop = groundY
        for (lw, lh, color) in layers {
            layerTop -= lh
            fill(ctx, Palette.outline, cx - lw / 2 - cell, layerTop - cell,
                 lw + cell * 2, lh + cell * 2)
            fill(ctx, color, cx - lw / 2, layerTop, lw, lh)
        }
    }

    /// Sand-Streifen mit Narbe — der statische Boden (drawGroundStrip).
    /// Die Textur reicht von der Outline-Kante (oben) bis zum Bildrand;
    /// welche Farben, sagt die Kulisse.
    static func groundTexture(
        width: CGFloat,
        sandHeight: CGFloat,
        cell: CGFloat,
        ground: ScenePaint.Ground
    ) -> SKTexture {
        let height = sandHeight + cell
        return texture(size: CGSize(width: width, height: height)) { ctx in
            let top = cell // Sand-Oberkante; darüber liegt die Outline.
            fill(ctx, UIColor(rgb: ground.sand), 0, top, width, sandHeight)
            fill(ctx, UIColor(rgb: ground.sandShade), 0, top + cell * 8, width, cell * 2)
            let toothW = cell * 5
            fill(ctx, UIColor(rgb: ground.turfDark), 0, top, width, cell * 5)
            var x: CGFloat = 0
            while x < width {
                fill(ctx, UIColor(rgb: ground.turfLight), x, top, toothW, cell * 4)
                x += toothW * 2
            }
            fill(ctx, Palette.outline, 0, 0, width, cell)
        }
    }

    /// Vorschau einer Kulisse für die Auswahl: Tageshimmel, Bodenkante mit
    /// Narbe und eine Requisite als Silhouette. Mehr passt auf 36 Punkte
    /// nicht hinein — und weniger wäre nicht auseinanderzuhalten.
    static func scenePreviewTexture(scene: SceneId, size: CGFloat) -> SKTexture {
        let d = max(size, 8)
        let paint = ScenePaint.of(scene)
        return texture(size: CGSize(width: d, height: d)) { ctx in
            let border = d / 12
            let horizon = d * 0.62
            fill(ctx, Palette.outline, 0, 0, d, d)
            fill(ctx, UIColor(rgb: paint.sky[0]), border, border, d - border * 2, horizon - border)
            // Ohne Boden (WELTRAUM) läuft der Himmel bis unten durch und
            // zeigt dort seine Nachtstufe — die Kachel bleibt lesbar.
            fill(ctx, UIColor(rgb: paint.ground?.sand ?? paint.sky[6]),
                 border, horizon, d - border * 2, d - horizon - border)
            if let ground = paint.ground {
                fill(ctx, UIColor(rgb: ground.turfLight), border, horizon, d - border * 2, d * 0.07)
            }
            let prop = paint.props[0]
            fill(ctx, UIColor(rgb: prop.dark), d * 0.22, horizon - d * 0.22, d * 0.26, d * 0.22)
            fill(ctx, UIColor(rgb: prop.body), d * 0.28, horizon - d * 0.34, d * 0.16, d * 0.14)
            fill(ctx, UIColor(rgb: prop.light), d * 0.58, horizon - d * 0.16, d * 0.18, d * 0.16)
        }
    }

    /// Medaille (MedalBadge): rotes Band im V, Münze mit geprägtem Stern
    /// und Glanzpunkt; Platin funkelt. tier == nil ergibt die Sand-Silhouette.
    static func medalTexture(tier: MedalTier?, size: CGFloat) -> SKTexture {
        return texture(size: CGSize(width: size, height: size)) { ctx in
            let body: UIColor
            let shade: UIColor
            if let tier = tier {
                let colors = Palette.medalColors(tier)
                body = colors.body
                shade = colors.shade
            } else {
                body = UIColor(rgb: 0xBDB48A)
                shade = UIColor(rgb: 0xA89E74)
            }
            let ribbon = tier != nil ? Palette.recordRed : UIColor(rgb: 0xBDB48A)
            let ribbonDark = tier != nil ? UIColor(rgb: 0xB02A28) : UIColor(rgb: 0xA89E74)

            let u = size / 16
            func block(_ c: CGFloat, _ r: CGFloat, _ w: CGFloat, _ h: CGFloat, _ color: UIColor) {
                fill(ctx, color, c * u, r * u, w * u, h * u)
            }

            let leftBand: [(CGFloat, CGFloat)] = [(3.5, 0), (4.5, 1.5), (5.5, 3)]
            let rightBand: [(CGFloat, CGFloat)] = [(9.5, 0), (8.5, 1.5), (7.5, 3)]
            for (c, r) in leftBand + rightBand {
                block(c - 0.5, r - 0.5, 3, 2.5, Palette.outline)
            }
            for (c, r) in leftBand {
                block(c, r, 2, 1.5, ribbon)
            }
            for (c, r) in rightBand {
                block(c, r, 2, 1.5, ribbonDark)
            }

            let coinR = size * 0.33
            let coinCx = size * 0.5
            let coinCy = size * 0.6
            pixelCircle(
                ctx,
                rect: CGRect(x: coinCx - coinR, y: coinCy - coinR, width: coinR * 2, height: coinR * 2),
                color: body,
                outline: Palette.outline,
                shade: shade
            )

            let cu = coinR * 2 / grid
            func emboss(_ c: CGFloat, _ r: CGFloat, _ w: CGFloat, _ h: CGFloat) {
                fill(ctx, shade, coinCx - coinR + c * cu, coinCy - coinR + r * cu, w * cu, h * cu)
            }
            emboss(5, 5, 3, 3)
            emboss(5.5, 3.5, 2, 2)
            emboss(5.5, 7.5, 2, 2)
            emboss(3.5, 5.5, 2, 2)
            emboss(7.5, 5.5, 2, 2)
            let shineColor = tier != nil ? Palette.dotShine : UIColor(rgb: 0xEFE7C0)
            fill(ctx, shineColor, coinCx - coinR + 2.5 * cu, coinCy - coinR + 2.5 * cu, 2 * cu, 2 * cu)

            if tier == .platinum {
                let sparks: [(CGFloat, CGFloat)] = [(0.2, 4), (12.6, 7), (10.5, 0.2)]
                for (sc, sr) in sparks {
                    fill(ctx, Palette.dotShine, coinCx - coinR + sc * cu, coinCy - coinR + sr * cu, cu, cu)
                }
            }
        }
    }
}

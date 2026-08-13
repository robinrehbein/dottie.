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
    static func cloudTexture(cell: CGFloat) -> SKTexture {
        let u = cell * 2
        let size = CGSize(width: u * 14, height: u * 6.5)
        return texture(size: size) { ctx in
            fill(ctx, Palette.cloud, 0, u * 3.5, u * 14, u * 3)
            fill(ctx, Palette.cloud, u * 2, u * 1.5, u * 7, u * 2)
            fill(ctx, Palette.cloud, u * 4, 0, u * 4, u * 1.5)
        }
    }

    /// Pixel-Baum: Stamm mit Schattenseite, dreistufige Krone
    /// (drawPixelTree, ohne Wind — der kommt als Sprite-Rotation).
    static func treeTexture(s: CGFloat, cell: CGFloat) -> SKTexture {
        let width = s * 1.6 + cell * 4
        let height = s * 1.8 + cell * 3
        let cx = width / 2
        let groundY = height
        return texture(size: CGSize(width: width, height: height)) { ctx in
            let trunkW = s * 0.30
            let trunkH = s * 0.60
            fill(ctx, Palette.outline, cx - trunkW / 2 - cell, groundY - trunkH - cell, trunkW + cell * 2, trunkH + cell)
            fill(ctx, Palette.trunk, cx - trunkW / 2, groundY - trunkH, trunkW, trunkH)
            fill(ctx, Palette.trunkShade, cx, groundY - trunkH, trunkW / 2, trunkH)

            let layers: [(CGFloat, CGFloat, UIColor)] = [
                (s * 1.6, s * 0.45, Palette.bushShade),
                (s * 1.2, s * 0.40, Palette.bush),
                (s * 0.7, s * 0.35, Palette.grassLight)
            ]
            var layerTop = groundY - trunkH
            for (lw, lh, color) in layers {
                layerTop -= lh
                fill(ctx, Palette.outline, cx - lw / 2 - cell, layerTop - cell, lw + cell * 2, lh + cell * 2)
                fill(ctx, color, cx - lw / 2, layerTop, lw, lh)
            }
        }
    }

    /// Pixel-Strauch: runde Beeren-Silhouette mit Licht-Tupfern (drawPixelBush).
    static func bushTexture(s: CGFloat, cell: CGFloat) -> SKTexture {
        let width = s * 2.7 + cell * 4
        let height = s * 1.8 + cell * 3
        let cx = width / 2
        let groundY = height
        return texture(size: CGSize(width: width, height: height)) { ctx in
            let layers: [(CGFloat, CGFloat, UIColor)] = [
                (s * 2.1, s * 0.55, Palette.bushShade),
                (s * 2.7, s * 0.70, Palette.bush),
                (s * 1.5, s * 0.55, Palette.grassLight)
            ]
            var layerTop = groundY
            for (lw, lh, color) in layers {
                layerTop -= lh
                fill(ctx, Palette.outline, cx - lw / 2 - cell, layerTop - cell, lw + cell * 2, lh + cell * 2)
                fill(ctx, color, cx - lw / 2, layerTop, lw, lh)
            }
            let u = cell * 1.5
            fill(ctx, Palette.grassLight, cx - s * 1.0, groundY - s * 1.05, u * 2, u)
            fill(ctx, Palette.grassLight, cx + s * 0.35, groundY - s * 0.8, u, u)
        }
    }

    /// Pixel-Blume: Stiel mit Blättern und großer Blüte (drawPixelFlower).
    static func flowerTexture(s: CGFloat, cell: CGFloat, petal: UIColor) -> SKTexture {
        let width = s * 1.6 + cell * 6
        let height = s * 2.0 + cell * 3
        let cx = width / 2
        let groundY = height
        return texture(size: CGSize(width: width, height: height)) { ctx in
            let stemH = s * 1.15
            let bx = cx
            let by = groundY - stemH

            fill(ctx, Palette.outline, cx - cell * 1.5, by, cell * 3, stemH)
            fill(ctx, Palette.bushShade, cx - cell * 0.75, by, cell * 1.5, stemH)

            let leafY = groundY - stemH * 0.45
            fill(ctx, Palette.outline, cx - s * 0.6 - cell, leafY - cell, s * 0.6 + cell * 2, cell * 3)
            fill(ctx, Palette.bush, cx - s * 0.6, leafY, s * 0.6, cell * 1.5)
            fill(ctx, Palette.outline, cx - cell, leafY + cell * 3, s * 0.55 + cell * 2, cell * 3)
            fill(ctx, Palette.bush, cx, leafY + cell * 4, s * 0.55, cell * 1.5)

            let u = s * 0.38
            func blossomBlock(_ x: CGFloat, _ y: CGFloat, _ color: UIColor) {
                fill(ctx, Palette.outline, x - cell, y - cell, u + cell * 2, u + cell * 2)
                fill(ctx, color, x, y, u, u)
            }
            blossomBlock(bx - u / 2, by - u * 1.5, petal)
            blossomBlock(bx - u * 1.5, by - u / 2, petal)
            blossomBlock(bx + u / 2, by - u / 2, petal)
            blossomBlock(bx - u / 2, by + u / 2, petal)
            blossomBlock(bx - u / 2, by - u / 2, Palette.dotBody)
        }
    }

    /// Sand-Streifen mit Grasnarbe — der statische Boden (drawGroundStrip).
    /// Die Textur reicht von der Outline-Kante (oben) bis zum Bildrand.
    static func groundTexture(width: CGFloat, sandHeight: CGFloat, cell: CGFloat) -> SKTexture {
        let height = sandHeight + cell
        return texture(size: CGSize(width: width, height: height)) { ctx in
            let top = cell // Sand-Oberkante; darüber liegt die Outline.
            fill(ctx, Palette.groundSand, 0, top, width, sandHeight)
            fill(ctx, Palette.groundSandShade, 0, top + cell * 8, width, cell * 2)
            let toothW = cell * 5
            fill(ctx, Palette.grassDark, 0, top, width, cell * 5)
            var x: CGFloat = 0
            while x < width {
                fill(ctx, Palette.grassLight, x, top, toothW, cell * 4)
                x += toothW * 2
            }
            fill(ctx, Palette.outline, 0, 0, width, cell)
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

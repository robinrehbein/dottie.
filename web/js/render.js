/*
 * Canvas-Rendering — Port des Zeichencodes aus TimingGameScreen.kt und
 * GameOverlays.kt (drawTimingWorld, Szenerie, Bahn, Pixel-Vogel,
 * Mario-Tod, Unlock-Burst, Medaille). Alle Maße und Farben wie am Phone.
 */
(function (global) {
  "use strict";

  // ===== Retro-Farbpalette (GameOverlays.kt) =====
  var CloudColor = "#E9FCFD";
  var BushColor = "#71C837";
  var BushShadeColor = "#5AA82C";
  var TrunkColor = "#9C6B3C";
  var TrunkShade = "#7A4E2A";
  var GroundSand = "#DED895";
  var GroundSandShade = "#D3C87E";
  var GrassLight = "#9DE85A";
  var GrassDark = "#74BF2E";
  var OutlineColor = "#543847";
  var DotBody = "#FFD847";
  var DotShine = "#FFF3B8";
  var RecordRed = "#E53935";

  /** Fallen-Zone: klar als Gefahr lesbar, aber unter Zeitdruck verwechselbar. */
  var FakeZoneColor = "#B44FD8";
  var FakeZoneCoreColor = "#8A2FB0";

  /** Himmelsfarbe pro 5er-Stufe: von Tag über Abendrot bis Nacht. */
  var SkyStages = [
    "#4EC0CA", // 0+  Tag (türkis)
    "#5B9BD5", // 5+  Blau
    "#7B6FD0", // 10+ Lila
    "#C0616F", // 15+ Altrosa
    "#D98A3D", // 20+ Sonnenuntergang
    "#3D4A8C", // 25+ Dämmerung
    "#2A2640"  // 30+ Nacht
  ];

  /** Dauer der Freischalt-Zelebration (goldener Ring + Schimmer). */
  var CELEBRATE_SECONDS = 1.1;

  /** Mario-Tod: Hüpfer nach oben, dann Gravitation (Bildhöhen pro s bzw. s²). */
  var DEATH_HOP_SPEED = 1.6;
  var DEATH_GRAVITY = 6;

  /** Drehung auf den Rücken, fertig am Scheitelpunkt (~0,27s). */
  var DEATH_FLIP_SECONDS = 0.3;

  var GRID = 13;

  function rect(ctx, color, x, y, w, h) {
    ctx.fillStyle = color;
    ctx.fillRect(x, y, w, h);
  }

  /** Zeichnet einen blockigen "Pixel"-Kreis aus Rasterzellen. */
  /**
   * Blockiger Pixel-Kreis. Die Fuellfarbe kommt pro Feld aus cell(col,row) —
   * so zeichnet dieselbe Routine einfarbige, gemusterte und animierte Skins
   * (siehe skins.js).
   */
  function drawPixelCircle(ctx, outline, centerX, centerY, radius, cell) {
    var n = GRID;
    var u = (radius * 2) / GRID;
    var mid = (GRID - 1) / 2;
    var rr = GRID / 2 - 0.25;

    for (var row = 0; row < n; row++) {
      for (var col = 0; col < n; col++) {
        var dx = col - mid;
        var dy = row - mid;
        var dist = Math.sqrt(dx * dx + dy * dy);
        if (dist <= rr) {
          var cellColor = dist > rr - 1.1 ? outline : cell(col, row);
          rect(ctx, cellColor,
            centerX - radius + col * u, centerY - radius + row * u,
            u + 0.5, u + 0.5);
        }
      }
    }
  }

  /** Einfarbige Variante mit Schattenseite — fuer Muenzen und Deko. */
  function drawSolidPixelCircle(ctx, color, outline, centerX, centerY, radius, shade) {
    if (shade === undefined) shade = color;
    drawPixelCircle(ctx, outline, centerX, centerY, radius, function (col, row) {
      return row + col > GRID * 1.15 ? shade : color;
    });
  }

  /** Blockige Retro-Wolke aus drei gestapelten Rechtecken. */
  function drawCloud(ctx, x, y, cell) {
    var u = cell * 2;
    rect(ctx, CloudColor, x, y + u * 2, u * 14, u * 3);
    rect(ctx, CloudColor, x + u * 2, y, u * 7, u * 2);
    rect(ctx, CloudColor, x + u * 4, y - u * 1.5, u * 4, u * 1.5);
  }

  /** Pixel-Baum: Stamm mit Schattenseite, dreistufige Krone im Wind. */
  function drawPixelTree(ctx, cx, groundY, s, sway, cell) {
    var trunkW = s * 0.30;
    var trunkH = s * 0.60;
    rect(ctx, OutlineColor, cx - trunkW / 2 - cell, groundY - trunkH - cell,
      trunkW + cell * 2, trunkH + cell);
    rect(ctx, TrunkColor, cx - trunkW / 2, groundY - trunkH, trunkW, trunkH);
    rect(ctx, TrunkShade, cx, groundY - trunkH, trunkW / 2, trunkH);

    var layers = [
      [s * 1.6, s * 0.45, BushShadeColor],
      [s * 1.2, s * 0.40, BushColor],
      [s * 0.7, s * 0.35, GrassLight]
    ];
    var layerTop = groundY - trunkH;
    for (var i = 0; i < layers.length; i++) {
      var lw = layers[i][0], lh = layers[i][1], color = layers[i][2];
      layerTop -= lh;
      var lx = cx + sway * (0.35 + 0.35 * i);
      rect(ctx, OutlineColor, lx - lw / 2 - cell, layerTop - cell,
        lw + cell * 2, lh + cell * 2);
      rect(ctx, color, lx - lw / 2, layerTop, lw, lh);
    }
  }

  /** Pixel-Strauch: runde Beeren-Silhouette mit Licht-Tupfern. */
  function drawPixelBush(ctx, cx, groundY, s, sway, cell) {
    var layers = [
      [s * 2.1, s * 0.55, BushShadeColor], // Sockel
      [s * 2.7, s * 0.70, BushColor],      // Bauch — am breitesten
      [s * 1.5, s * 0.55, GrassLight]      // Kuppe
    ];
    var layerTop = groundY;
    for (var i = 0; i < layers.length; i++) {
      var lw = layers[i][0], lh = layers[i][1], color = layers[i][2];
      layerTop -= lh;
      var lx = cx + sway * (0.2 + 0.3 * i);
      rect(ctx, OutlineColor, lx - lw / 2 - cell, layerTop - cell,
        lw + cell * 2, lh + cell * 2);
      rect(ctx, color, lx - lw / 2, layerTop, lw, lh);
    }

    var u = cell * 1.5;
    rect(ctx, GrassLight, cx - s * 1.0 + sway * 0.4, groundY - s * 1.05, u * 2, u);
    rect(ctx, GrassLight, cx + s * 0.35 + sway * 0.4, groundY - s * 0.8, u, u);
  }

  /** Pixel-Blume: Stiel mit Blättern und Blüte, die im Wind wiegt. */
  function drawPixelFlower(ctx, cx, groundY, s, sway, cell, petal) {
    var stemH = s * 1.15;
    var bx = cx + sway;
    var by = groundY - stemH;

    rect(ctx, OutlineColor, cx - cell * 1.5, by, cell * 3, stemH);
    rect(ctx, BushShadeColor, cx - cell * 0.75, by, cell * 1.5, stemH);

    var leafY = groundY - stemH * 0.45;
    rect(ctx, OutlineColor, cx - s * 0.6 - cell, leafY - cell, s * 0.6 + cell * 2, cell * 3);
    rect(ctx, BushColor, cx - s * 0.6, leafY, s * 0.6, cell * 1.5);
    rect(ctx, OutlineColor, cx - cell, leafY + cell * 3, s * 0.55 + cell * 2, cell * 3);
    rect(ctx, BushColor, cx, leafY + cell * 4, s * 0.55, cell * 1.5);

    var u = s * 0.38;
    function block(x, y, color) {
      rect(ctx, OutlineColor, x - cell, y - cell, u + cell * 2, u + cell * 2);
      rect(ctx, color, x, y, u, u);
    }
    block(bx - u / 2, by - u * 1.5, petal);   // oben
    block(bx - u * 1.5, by - u / 2, petal);   // links
    block(bx + u / 2, by - u / 2, petal);     // rechts
    block(bx - u / 2, by + u / 2, petal);     // unten
    block(bx - u / 2, by - u / 2, DotBody);   // Mitte
  }

  /** Bäume/Sträucher mit Parallaxe-Drift und Wind (drawScenery). */
  function drawScenery(ctx, w, h, game, cell) {
    var groundY = h * 0.88 + cell * 2;
    var drift = game.elapsed * h * 0.016;
    var spacing = w * 0.26;
    var count = Math.floor(w / spacing) + 3;
    var total = spacing * count;
    for (var k = 0; k < count; k++) {
      var x = ((k * spacing - drift) % total + total) % total - spacing;
      var sway = Math.sin(game.elapsed * 1.4 + k * 1.7) * cell * 0.6;
      switch (k % 4) {
        case 0:
          drawPixelTree(ctx, x, groundY, h * 0.075, sway, cell);
          break;
        case 1:
          drawPixelFlower(ctx, x, groundY, h * 0.032, sway * 0.8, cell,
            (Math.floor(k / 4) % 2 === 0) ? RecordRed : CloudColor);
          break;
        case 2:
          drawPixelTree(ctx, x, groundY, h * 0.058, -sway, cell);
          break;
        default:
          drawPixelBush(ctx, x, groundY, h * 0.026, sway * 0.4, cell);
      }
    }
  }

  /** Sand-Streifen mit Grasnarbe — der statische Boden unter allem. */
  function drawGroundStrip(ctx, w, h, cell) {
    var groundTop = h * 0.88;

    rect(ctx, GroundSand, 0, groundTop, w, h - groundTop);
    rect(ctx, GroundSandShade, 0, groundTop + cell * 8, w, cell * 2);
    var toothW = cell * 5;
    rect(ctx, GrassDark, 0, groundTop, w, cell * 5);
    var x = 0;
    while (x < w) {
      rect(ctx, GrassLight, x, groundTop, toothW, cell * 4);
      x += toothW * 2;
    }
    rect(ctx, OutlineColor, 0, groundTop - cell, w, cell);
  }

  /**
   * Die Kreisbahn als Kette blockiger Zellen — 60 Segmente für den
   * Perlenketten-Look. Zielzone grün mit hellem Perfekt-Kern, Falle violett.
   */
  function drawTrack(ctx, game, cx, cy, radius, cell) {
    var TG = global.TimingGame;
    var segments = 60;
    var zoneHalf = game.effectiveZoneHalf();
    for (var k = 0; k < segments; k++) {
      var a = (k / segments) * (2 * Math.PI);
      var px = cx + Math.cos(a) * radius;
      var py = cy + Math.sin(a) * radius;

      var relativeZone = TG.wrapToPi(a - game.zoneCenter);
      var inZone = Math.abs(relativeZone) <= zoneHalf;
      // Kern fürs Zeichnen auf mindestens einen Rasterschritt aufrunden.
      var coreHalf = Math.max(zoneHalf * TG.C.PERFECT_SHARE, Math.PI / segments);
      var inPerfectCore = Math.abs(relativeZone) <= coreHalf;

      var inFake = game.hasFakeZone &&
        Math.abs(TG.wrapToPi(a - game.fakeZoneCenter)) <= game.zoneHalfWidth;
      var inFakeCore = game.hasFakeZone &&
        Math.abs(TG.wrapToPi(a - game.fakeZoneCenter)) <=
          game.zoneHalfWidth * TG.C.PERFECT_SHARE;

      var highlighted = inZone || inFake;
      var outer = highlighted ? cell * 5 : cell * 3;
      var inner = highlighted ? cell * 3.4 : cell * 1.8;
      var innerColor = inPerfectCore ? GrassLight
        : inZone ? GrassDark
        : inFakeCore ? FakeZoneCoreColor
        : inFake ? FakeZoneColor
        : GroundSandShade;

      rect(ctx, OutlineColor, px - outer / 2, py - outer / 2, outer, outer);
      rect(ctx, innerColor, px - inner / 2, py - inner / 2, inner, inner);
    }
  }

  /** Freischalt-Zelebration: goldene Pixel-Ringe wandern nach außen. */
  function drawUnlockBurst(ctx, w, h, timeLeft, cx, cy, radius, cell) {
    var progress = 1 - Math.min(1, Math.max(0, timeLeft / CELEBRATE_SECONDS));
    var fade = 1 - progress;

    // Goldschimmer, nur im ersten Drittel spürbar
    var glow = Math.max(0, fade - 0.66) * 0.9;
    if (glow > 0) {
      ctx.globalAlpha = glow;
      rect(ctx, DotBody, 0, 0, w, h);
      ctx.globalAlpha = 1;
    }

    var sparks = 20;
    for (var ring = 0; ring < 2; ring++) {
      var ringProgress = Math.min(1, Math.max(0, progress - ring * 0.15));
      if (ringProgress <= 0) continue;
      var burstRadius = radius * (0.55 + ringProgress * 0.9);
      var blockSize = cell * (3.5 - ring) * fade;
      if (blockSize <= 0) continue;
      ctx.globalAlpha = fade;
      var color = ring === 0 ? DotBody : DotShine;
      for (var k = 0; k < sparks; k++) {
        var a = (k / sparks + ring * 0.025) * (2 * Math.PI);
        var px = cx + Math.cos(a) * burstRadius;
        var py = cy + Math.sin(a) * burstRadius;
        rect(ctx, color, px - blockSize / 2, py - blockSize / 2, blockSize, blockSize);
      }
      ctx.globalAlpha = 1;
    }
  }

  /** Pixel-Vogel mit Auge/Glanz; Mario-Tod mit Hüpfer und 180°-Flip. */
  function drawTimingDot(ctx, w, h, game, fx, cx, cy, radius, skin) {
    var TG = global.TimingGame;
    var px = cx + Math.cos(game.angle) * radius;
    var py = cy + Math.sin(game.angle) * radius;
    var r = h * 0.026;

    var flip = 0;
    if (fx.deathTime >= 0) {
      var t = fx.deathTime - TG.C.DEATH_FREEZE_SECONDS;
      if (t > 0) {
        py += (-DEATH_HOP_SPEED * t + 0.5 * DEATH_GRAVITY * t * t) * h;
        if (py - r * 2 > h) return;
        flip = 180 * Math.min(1, t / DEATH_FLIP_SECONDS);
      }
    }

    var state = {
      elapsed: game.elapsed,
      score: game.score,
      perfectStreak: game.perfectStreak
    };
    var shine = global.DotSkin.shine(skin, state);

    function drawBird(centerX, centerY, alpha) {
      if (alpha !== undefined && alpha < 1) ctx.globalAlpha = alpha;

      drawPixelCircle(ctx, OutlineColor, centerX, centerY, r, function (col, row) {
        return global.DotSkin.cell(skin, col, row, state);
      });

      var u = (r * 2) / GRID;
      function birdRect(col, row, cols, rows, color) {
        rect(ctx, color, centerX - r + col * u, centerY - r + row * u, cols * u, rows * u);
      }

      // Glanzpunkt und Auge folgen der sichtbaren Flugrichtung. Das Auge
      // bekommt zum Koerper hin eine Kontur, sonst geht es auf hellen
      // Skins (Koi, Chrom) im Koerper unter.
      var facingLeft = Math.sin(game.angle) * game.direction > 0;
      if (facingLeft) {
        birdRect(GRID - 4.5, 2.5, 2, 2, shine);
        birdRect(5.5, 3, 0.5, 4, OutlineColor);
        birdRect(2, 2.5, 3.5, 0.5, OutlineColor);
        birdRect(2, 7, 3.5, 0.5, OutlineColor);
        birdRect(2, 3, 3.5, 4, "#FFFFFF");
        birdRect(2, 4, 1.5, 2, OutlineColor);
      } else {
        birdRect(2.5, 2.5, 2, 2, shine);
        birdRect(7, 3, 0.5, 4, OutlineColor);
        birdRect(7.5, 2.5, 3.5, 0.5, OutlineColor);
        birdRect(7.5, 7, 3.5, 0.5, OutlineColor);
        birdRect(7.5, 3, 3.5, 4, "#FFFFFF");
        birdRect(9.5, 4, 1.5, 2, OutlineColor);
      }

      ctx.globalAlpha = 1;
    }

    // Schweif-Skins (Tinte) lassen Nachbilder auf der Bahn zurueck. Die
    // Positionen werden aus dem Winkel zurueckgerechnet statt gespeichert —
    // damit sehen alle Ports identisch aus, ohne eigenen Zustand.
    if (skin.trail && game.phase === TG.Phase.RUNNING) {
      for (var step = global.DotSkin.TRAIL_STEPS; step >= 1; step--) {
        var a = game.angle - game.direction * step * global.DotSkin.TRAIL_SPACING;
        drawBird(cx + Math.cos(a) * radius, cy + Math.sin(a) * radius, 0.34 / step);
      }
    }

    if (flip > 0) {
      ctx.save();
      ctx.translate(px, py);
      ctx.rotate((flip * Math.PI) / 180);
      ctx.translate(-px, -py);
      drawBird(px, py);
      ctx.restore();
    } else {
      drawBird(px, py);
    }
  }

  /** Port von drawTimingWorld: ein kompletter Frame. */
  function drawWorld(ctx, w, h, game, fx, skin) {
    var cell = Math.max(2, Math.floor(h / 220));

    // Screen-Shake beim Tod
    var shakeX = 0, shakeY = 0;
    if (fx.shakeTime > 0) {
      var strength = fx.shakeTime * 28;
      shakeX = Math.sin(fx.shakeTime * 91) * strength;
      shakeY = Math.sin(fx.shakeTime * 77) * strength;
    }

    ctx.save();
    ctx.translate(shakeX, shakeY);

    // Himmel färbt sich mit jeder 5er-Stufe weiter Richtung Nacht.
    var sky = SkyStages[global.DotSkin.skyStage(game.score)];
    rect(ctx, sky, -40, -40, w + 80, h + 80);

    // Langsam driftende Wolken
    var drift = game.elapsed * h * 0.01;
    drawCloud(ctx, w * 0.1 - drift % (w * 1.4), h * 0.16, cell);
    drawCloud(ctx, w * 0.75 - drift % (w * 1.4), h * 0.24, cell);

    drawScenery(ctx, w, h, game, cell);
    drawGroundStrip(ctx, w, h, cell);

    // Kreisbahn mit Zielzone, ggf. Fallen-Zone und Punkt
    var cx = w / 2;
    var cy = h * 0.44;
    var radius = Math.min(w * 0.36, h * 0.28);
    drawTrack(ctx, game, cx, cy, radius, cell);
    if (game.isDotVisible()) {
      drawTimingDot(ctx, w, h, game, fx, cx, cy, radius, skin);
    }
    if (fx.celebrateTime > 0) {
      drawUnlockBurst(ctx, w, h, fx.celebrateTime, cx, cy, radius, cell);
    }

    ctx.restore();

    // Weißer Blitz beim Aufprall
    if (fx.flashAlpha > 0) {
      ctx.globalAlpha = Math.min(1, fx.flashAlpha);
      rect(ctx, "#FFFFFF", 0, 0, w, h);
      ctx.globalAlpha = 1;
    }
  }

  /**
   * Medaille (MedalBadge aus GameOverlays.kt): rotes Band im V, Münze mit
   * geprägtem Stern und Glanzpunkt; Platin funkelt. Unterhalb von Bronze
   * dieselbe Form als Sand-Silhouette.
   */
  function drawMedal(canvas, score) {
    var MT = global.MedalTier;
    var ctx = canvas.getContext("2d");
    var size = Math.min(canvas.width, canvas.height);
    ctx.clearRect(0, 0, canvas.width, canvas.height);

    var tier = MT.forScore(score);
    var body = tier ? tier.body : "#BDB48A";
    var shade = tier ? tier.shade : "#A89E74";
    var ribbon = tier ? RecordRed : "#BDB48A";
    var ribbonDark = tier ? "#B02A28" : "#A89E74";

    var u = size / 16;
    function block(c, r, w2, h2, color) {
      rect(ctx, color, c * u, r * u, w2 * u, h2 * u);
    }

    // Band im V: erst Outline-Pass, dann Farbe (links hell, rechts dunkel)
    var leftBand = [[3.5, 0], [4.5, 1.5], [5.5, 3]];
    var rightBand = [[9.5, 0], [8.5, 1.5], [7.5, 3]];
    var all = leftBand.concat(rightBand);
    for (var i = 0; i < all.length; i++) {
      block(all[i][0] - 0.5, all[i][1] - 0.5, 3, 2.5, OutlineColor);
    }
    for (var l = 0; l < leftBand.length; l++) {
      block(leftBand[l][0], leftBand[l][1], 2, 1.5, ribbon);
    }
    for (var rr = 0; rr < rightBand.length; rr++) {
      block(rightBand[rr][0], rightBand[rr][1], 2, 1.5, ribbonDark);
    }

    // Münze
    var coinR = size * 0.33;
    var coinCx = size * 0.5;
    var coinCy = size * 0.6;
    drawSolidPixelCircle(ctx, body, OutlineColor, coinCx, coinCy, coinR, shade);

    // Geprägter Stern (Plus-Form in Schattenfarbe) und Glanzpunkt
    var cu = (coinR * 2) / GRID;
    function emboss(c, r2, w2, h2) {
      rect(ctx, shade, coinCx - coinR + c * cu, coinCy - coinR + r2 * cu, w2 * cu, h2 * cu);
    }
    emboss(5, 5, 3, 3);
    emboss(5.5, 3.5, 2, 2);
    emboss(5.5, 7.5, 2, 2);
    emboss(3.5, 5.5, 2, 2);
    emboss(7.5, 5.5, 2, 2);
    rect(ctx, tier ? DotShine : "#EFE7C0",
      coinCx - coinR + 2.5 * cu, coinCy - coinR + 2.5 * cu, 2 * cu, 2 * cu);

    if (tier && tier.name === "PLATINUM") {
      var sparkles = [[0.2, 4], [12.6, 7], [10.5, 0.2]];
      for (var s = 0; s < sparkles.length; s++) {
        rect(ctx, DotShine,
          coinCx - coinR + sparkles[s][0] * cu,
          coinCy - coinR + sparkles[s][1] * cu, cu, cu);
      }
    }
  }

  /**
   * Kleine Skin-Vorschau fuer die Auswahl: der Koerper im Muster des
   * Skins, ohne Gesicht — bei 36px waere es nur Matsch.
   */
  function drawSkinPreview(ctx, size, skin) {
    ctx.clearRect(0, 0, size, size);
    drawPixelCircle(ctx, OutlineColor, size / 2, size / 2, size / 2, function (col, row) {
      return global.DotSkin.cell(skin, col, row);
    });
  }

  var Renderer = {
    drawSkinPreview: drawSkinPreview,
    drawWorld: drawWorld,
    drawMedal: drawMedal,
    CELEBRATE_SECONDS: CELEBRATE_SECONDS,
    Colors: {
      OutlineColor: OutlineColor,
      PanelSand: GroundSand,
      DotBody: DotBody,
      RecordRed: RecordRed
    },
    /**
     * Die komplette Palette unter den Kotlin-Namen — die Tests vergleichen
     * sie Wert für Wert gegen GameOverlays.kt/TimingGameScreen.kt, damit
     * eine Farbänderung am Phone hier nicht unbemerkt auseinanderläuft.
     */
    Palette: {
      CloudColor: CloudColor,
      BushColor: BushColor,
      BushShadeColor: BushShadeColor,
      TrunkColor: TrunkColor,
      TrunkShade: TrunkShade,
      GroundSand: GroundSand,
      GroundSandShade: GroundSandShade,
      GrassLight: GrassLight,
      GrassDark: GrassDark,
      OutlineColor: OutlineColor,
      DotBody: DotBody,
      DotShine: DotShine,
      RecordRed: RecordRed,
      FakeZoneColor: FakeZoneColor,
      FakeZoneCoreColor: FakeZoneCoreColor,
      SkyStages: SkyStages
    }
  };

  global.Renderer = Renderer;
  if (typeof module !== "undefined" && module.exports) {
    module.exports = Renderer;
  }
})(typeof globalThis !== "undefined" ? globalThis : this);

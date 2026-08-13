/*
 * Port von SkinPaint.kt (:core) und MedalTier.kt: das Farbwerk aller
 * Skins, ihre Freischalt-Bedingungen und die Medaillen-Stufen ab
 * 10/20/30/40 Punkten.
 *
 * Ein Skin ist keine Sammlung von drei Farben mehr, sondern eine Funktion
 * ueber das 13x13-Raster des Vogels: cell(col, row, state) liefert die
 * Farbe eines Feldes. Damit sind gemusterte, animierte und auf den Lauf
 * reagierende Skins moeglich, ohne dass der Renderer Sonderfaelle kennt.
 * body/shade/shine bleiben als Stellvertreter fuer Muenzen und Chips.
 */
(function (global) {
  "use strict";

  var GRID = 13;
  var MID = (GRID - 1) / 2;
  var RR = GRID / 2 - 0.25;

  /** Himmelsstufen fuer CHAMAELEON — Spiegel von SkyStages im Renderer. */
  var SKY_STAGES = [
    "#4EC0CA", "#5B9BD5", "#7B6FD0", "#C0616F", "#D98A3D", "#3D4A8C", "#2A2640"
  ];

  /** Nachbilder eines Schweif-Skins und ihr Winkelabstand (Radiant). */
  var TRAIL_STEPS = 3;
  var TRAIL_SPACING = 0.10;

  // ===== Farb-Werkzeug (Port von SkinPaint.mix / .hsl) =====

  function channels(hex) {
    var n = parseInt(hex.slice(1), 16);
    return [(n >> 16) & 255, (n >> 8) & 255, n & 255];
  }

  function toHex(r, g, b) {
    function part(v) {
      var c = Math.max(0, Math.min(255, Math.round(v))).toString(16);
      return c.length < 2 ? "0" + c : c;
    }
    return "#" + part(r) + part(g) + part(b);
  }

  function mix(a, b, k) {
    var f = Math.max(0, Math.min(1, k));
    var x = channels(a), y = channels(b);
    return toHex(x[0] + (y[0] - x[0]) * f, x[1] + (y[1] - x[1]) * f, x[2] + (y[2] - x[2]) * f);
  }

  function hsl(h, s, l) {
    var hue = ((h % 360) + 360) % 360;
    var c = (1 - Math.abs(2 * l - 1)) * s;
    var x = c * (1 - Math.abs(((hue / 60) % 2) - 1));
    var m = l - c / 2;
    var rgb;
    if (hue < 60) rgb = [c, x, 0];
    else if (hue < 120) rgb = [x, c, 0];
    else if (hue < 180) rgb = [0, c, x];
    else if (hue < 240) rgb = [0, x, c];
    else if (hue < 300) rgb = [x, 0, c];
    else rgb = [c, 0, x];
    return toHex((rgb[0] + m) * 255, (rgb[1] + m) * 255, (rgb[2] + m) * 255);
  }

  /** Die Standard-Schattierung des Spiels: untere rechte Haelfte dunkler. */
  function shaded(col, row, body, shade) {
    return col + row > GRID * 1.15 ? shade : body;
  }

  function at(list, col, row) {
    for (var i = 0; i < list.length; i++) {
      if (list[i][0] === col && list[i][1] === row) return true;
    }
    return false;
  }

  /** Leuchtfarbe von NEON: springt im Vierteltakt weiter. */
  function neonGlow(state) {
    var cols = ["#FF3DCB", "#3DF5E0", "#C3FF3D"];
    var step = Math.floor(state.elapsed * 2.5);
    return cols[((step % cols.length) + cols.length) % cols.length];
  }

  function normalize(state) {
    state = state || {};
    return {
      elapsed: state.elapsed || 0,
      score: state.score || 0,
      perfectStreak: state.perfectStreak || 0
    };
  }

  // ===== Muster-Details =====

  var MELON_SEEDS = [[4, 3], [7, 5], [3, 6], [8, 2], [6, 7]];
  var MUSHROOM_DOTS = [[3, 2], [8, 1], [5, 4], [9, 5], [2, 6], [6, 6]];
  var KOI_RED = [[2, 4], [3, 4], [3, 5], [2, 5], [4, 5], [3, 3]];
  var KOI_ORANGE = [[8, 7], [9, 7], [8, 8], [7, 8], [9, 6], [7, 7]];
  var GALAXY_STARS = [[3, 3], [9, 4], [5, 8], [10, 8], [2, 7]];
  var GALAXY_NEBULA = [[7, 2], [4, 6], [8, 9]];

  /**
   * Reihenfolge = SkinId in SkinPaint.kt. Die Namen sind zugleich die
   * gespeicherten Werte, sie muessen auf allen Plattformen gleich heissen.
   */
  var SKINS = [
    {
      name: "KLASSIK", titleKey: "skin_klassik", hintKey: null,
      body: "#FFD847", shade: "#F5A623", shine: "#FFF3B8",
      cell: function (col, row) { return shaded(col, row, "#FFD847", "#F5A623"); }
    },
    {
      name: "MINZE", titleKey: "skin_minze", hintKey: "skin_hint_minze",
      body: "#4BE38C", shade: "#2BA55E", shine: "#C8FFE0",
      cell: function (col, row) { return shaded(col, row, "#4BE38C", "#2BA55E"); }
    },
    {
      name: "LAVA", titleKey: "skin_lava", hintKey: "skin_hint_lava",
      body: "#FF5A36", shade: "#C22F12", shine: "#FFC9A3",
      cell: function (col, row) { return shaded(col, row, "#FF5A36", "#C22F12"); }
    },
    {
      name: "GOLD", titleKey: "skin_gold", hintKey: "skin_hint_gold",
      body: "#FFC400", shade: "#CC8F00", shine: "#FFF7CC",
      cell: function (col, row) { return shaded(col, row, "#FFC400", "#CC8F00"); }
    },
    {
      name: "FROST", titleKey: "skin_frost", hintKey: "skin_hint_frost",
      body: "#8FD8FF", shade: "#4FA3D8", shine: "#E8F9FF",
      cell: function (col, row) { return shaded(col, row, "#8FD8FF", "#4FA3D8"); }
    },
    {
      name: "SCHATTEN", titleKey: "skin_schatten", hintKey: "skin_hint_schatten",
      body: "#6B4F8A", shade: "#43315C", shine: "#CBB8E8",
      cell: function (col, row) { return shaded(col, row, "#6B4F8A", "#43315C"); }
    },
    {
      name: "PRISMA", titleKey: "skin_prisma", hintKey: "skin_hint_prisma",
      body: "#FF6FD8", shade: "#C93BAA", shine: "#B8F3FF",
      cell: function (col, row) { return shaded(col, row, "#FF6FD8", "#C93BAA"); }
    },

    // ===== Gemustert =====
    {
      name: "BIENE", titleKey: "skin_biene", hintKey: "skin_hint_biene",
      body: "#FFD847", shade: "#3A2C33", shine: "#FFF3B8",
      cell: function (col, row) {
        if ((((col - row) % 6) + 6) % 6 < 2) return "#3A2C33";
        return shaded(col, row, "#FFD847", "#E0A400");
      }
    },
    {
      name: "MELONE", titleKey: "skin_melone", hintKey: "skin_hint_melone",
      body: "#F0555C", shade: "#74BF2E", shine: "#FFD3D6",
      cell: function (col, row) {
        if (row >= 10) return col + row > GRID * 1.15 ? "#5AA020" : "#74BF2E";
        if (row === 9) return "#DFF2C6";
        if (at(MELON_SEEDS, col, row)) return "#3A2C33";
        return shaded(col, row, "#F0555C", "#C93B48");
      }
    },
    {
      name: "PILZ", titleKey: "skin_pilz", hintKey: "skin_hint_pilz",
      body: "#E8452F", shade: "#C2301F", shine: "#FFD9C9",
      cell: function (col, row) {
        if (row >= 9) return shaded(col, row, "#F7F3EE", "#D9CEC2");
        if (at(MUSHROOM_DOTS, col, row)) return "#F7F3EE";
        return shaded(col, row, "#E8452F", "#C2301F");
      }
    },
    {
      name: "KOI", titleKey: "skin_koi", hintKey: "skin_hint_koi",
      body: "#F7F3EE", shade: "#E8452F", shine: "#FFFFFF",
      cell: function (col, row) {
        if (at(KOI_RED, col, row)) return "#E8452F";
        if (at(KOI_ORANGE, col, row)) return "#F59A2E";
        return shaded(col, row, "#F7F3EE", "#D9CEC2");
      }
    },
    {
      name: "GALAXIE", titleKey: "skin_galaxie", hintKey: "skin_hint_galaxie",
      body: "#4E3C86", shade: "#231A3F", shine: "#FFF3B8",
      cell: function (col, row) {
        if (at(GALAXY_STARS, col, row)) return "#FFF3B8";
        if (at(GALAXY_NEBULA, col, row)) return "#7FDCE4";
        return mix("#4E3C86", "#231A3F", (col + row) / (GRID * 2));
      }
    },
    {
      name: "KARO", titleKey: "skin_karo", hintKey: "skin_hint_karo",
      body: "#4EC0CA", shade: "#2E8E98", shine: "#FFFFFF",
      cell: function (col, row) {
        var dark = (Math.floor(col / 2) + Math.floor(row / 2)) % 2 === 0;
        if (dark) return col + row > GRID * 1.15 ? "#2E8E98" : "#4EC0CA";
        return shaded(col, row, "#F7F3EE", "#D9CEC2");
      }
    },

    // ===== Bewegt =====
    {
      name: "REGENBOGEN", titleKey: "skin_regenbogen", hintKey: "skin_hint_regenbogen",
      body: "#FF6FD8", shade: "#7A3BC9", shine: "#FFFFFF", animated: true,
      cell: function (col, row, state) {
        // Der Gruenbereich wird uebersprungen: Ein gruener Vogel saehe
        // fuer einen Moment aus wie die Zielzone.
        var h = (state.elapsed * 45) % 300;
        if (h > 80) h += 60;
        return col + row > GRID * 1.15 ? hsl(h, 0.70, 0.44) : hsl(h, 0.85, 0.62);
      }
    },
    {
      name: "AURORA", titleKey: "skin_aurora", hintKey: "skin_hint_aurora",
      body: "#3FE0A8", shade: "#2A7F8E", shine: "#E8F9FF", animated: true,
      cell: function (col, row, state) {
        var wave = Math.sin((col + row) * 0.42 - state.elapsed * 1.6);
        var h = 168 + wave * 90;
        return col + row > GRID * 1.15 ? hsl(h, 0.55, 0.40) : hsl(h, 0.72, 0.60);
      }
    },
    {
      name: "MAGMA", titleKey: "skin_magma", hintKey: "skin_hint_magma",
      body: "#3A2431", shade: "#C22F12", shine: "#FFD847", animated: true,
      cell: function (col, row, state) {
        var vein = Math.sin(col * 1.3 + row * 0.7) > 0.35;
        if (!vein) return col + row > GRID * 1.15 ? "#241722" : "#3A2431";
        var heat = 0.5 + 0.5 * Math.sin(state.elapsed * 3.4 + col * 0.8 + row * 0.5);
        return mix("#8E2410", "#FFD847", heat);
      }
    },
    {
      name: "NEON", titleKey: "skin_neon", hintKey: "skin_hint_neon",
      body: "#241E33", shade: "#181328", shine: "#FF3DCB", animated: true,
      shineColor: neonGlow,
      cell: function (col, row, state) {
        var dx = col - MID, dy = row - MID;
        if (Math.sqrt(dx * dx + dy * dy) > RR - 2.2) return neonGlow(state);
        return col + row > GRID * 1.15 ? "#181328" : "#241E33";
      }
    },
    {
      name: "CHROM", titleKey: "skin_chrom", hintKey: "skin_hint_chrom",
      body: "#E6EAF2", shade: "#5B6478", shine: "#FFFFFF", animated: true,
      cell: function (col, row, state) {
        var band = 0.5 + 0.5 * Math.sin(col * 1.1);
        var base = mix("#5B6478", "#E6EAF2", band);
        var sweep = ((state.elapsed * 6) % 18) - 3;
        var d = Math.abs(col + row * 0.4 - sweep);
        if (d < 1.6) base = mix(base, "#FFFFFF", 1 - d / 1.6);
        return col + row > GRID * 1.15 ? mix(base, "#3B4152", 0.35) : base;
      }
    },

    // ===== Reagierend =====
    {
      name: "CHAMAELEON", titleKey: "skin_chamaeleon", hintKey: "skin_hint_chamaeleon",
      body: "#8FD8DE", shade: "#3F9BA5", shine: "#FFFFFF",
      cell: function (col, row, state) {
        var sky = SKY_STAGES[Math.min(Math.floor(state.score / 5), SKY_STAGES.length - 1)];
        return col + row > GRID * 1.15 ? mix(sky, "#000000", 0.18) : mix(sky, "#FFFFFF", 0.34);
      }
    },
    {
      name: "KOMBO", titleKey: "skin_kombo", hintKey: "skin_hint_kombo",
      body: "#FFD847", shade: "#E0A400", shine: "#FFF3B8",
      cell: function (col, row, state) {
        var k = Math.min(state.perfectStreak, 5) / 5;
        return shaded(col, row, mix("#8C8790", "#FFD847", k), mix("#5F5B63", "#E0A400", k));
      }
    },
    {
      name: "TINTE", titleKey: "skin_tinte", hintKey: "skin_hint_tinte",
      body: "#2A46A8", shade: "#1F3A8A", shine: "#A8C0FF", trail: true,
      cell: function (col, row) { return shaded(col, row, "#2A46A8", "#1F3A8A"); }
    }
  ];

  /** Farbe eines Rasterfelds; state ist optional (Standbild). */
  function cell(skin, col, row, state) {
    return skin.cell(col, row, normalize(state));
  }

  /** Glanzpunkt — bei NEON wandert er mit der Leuchtfarbe mit. */
  function shine(skin, state) {
    return skin.shineColor ? skin.shineColor(normalize(state)) : skin.shine;
  }

  /** stats = { bestScore, bestPerfectStreak, bestDailyStreak } */
  function isUnlocked(skin, stats) {
    switch (skin.name) {
      case "KLASSIK": return true;
      case "MINZE": return stats.bestScore >= 10;
      case "LAVA": return stats.bestScore >= 20;
      case "GOLD": return stats.bestScore >= 30;
      case "FROST": return stats.bestScore >= 40;
      case "SCHATTEN": return stats.bestPerfectStreak >= 4;
      case "PRISMA": return stats.bestDailyStreak >= 3;
      case "BIENE": return stats.bestPerfectStreak >= 6;
      case "MELONE": return stats.bestScore >= 25;
      case "CHAMAELEON": return stats.bestScore >= 30;
      case "PILZ": return stats.bestScore >= 35;
      case "CHROM": return stats.bestScore >= 45;
      case "GALAXIE": return stats.bestScore >= 50;
      case "TINTE": return stats.bestScore >= 55;
      case "MAGMA": return stats.bestScore >= 60;
      case "KOI": return stats.bestDailyStreak >= 7;
      case "AURORA": return stats.bestDailyStreak >= 14;
      case "KOMBO": return stats.bestPerfectStreak >= 8;
      case "KARO": return stats.bestPerfectStreak >= 10;
      case "NEON": return stats.bestPerfectStreak >= 12;
      // Der Regenbogen ist der Abschluss der Sammlung: Er kommt erst,
      // wenn alle anderen Skins offen sind (er selbst zaehlt nicht mit,
      // sonst waere die Bedingung zirkulaer).
      case "REGENBOGEN":
        return SKINS.every(function (s) {
          return s.name === "REGENBOGEN" || isUnlocked(s, stats);
        });
    }
    return false;
  }

  /** Skin zu einem gespeicherten Namen, KLASSIK als Fallback. */
  function fromName(name) {
    for (var i = 0; i < SKINS.length; i++) {
      if (SKINS[i].name === name) return SKINS[i];
    }
    return SKINS[0];
  }

  function unlockedCount(stats) {
    return SKINS.filter(function (s) { return isUnlocked(s, stats); }).length;
  }

  var DotSkin = {
    SKINS: SKINS,
    SKY_STAGES: SKY_STAGES,
    GRID: GRID,
    TRAIL_STEPS: TRAIL_STEPS,
    TRAIL_SPACING: TRAIL_SPACING,
    cell: cell,
    shine: shine,
    mix: mix,
    hsl: hsl,
    isUnlocked: isUnlocked,
    fromName: fromName,
    unlockedCount: unlockedCount
  };

  // ===== Medaillen (MedalTier.kt) =====

  var MEDALS = [
    { name: "BRONZE",   threshold: 10, nameKey: "medal_bronze",   body: "#CD7F32", shade: "#9C5A1E" },
    { name: "SILVER",   threshold: 20, nameKey: "medal_silver",   body: "#C0C0C0", shade: "#8F8F9C" },
    { name: "GOLD",     threshold: 30, nameKey: "medal_gold",     body: "#FFD700", shade: "#C9A400" },
    { name: "PLATINUM", threshold: 40, nameKey: "medal_platinum", body: "#E5E4E2", shade: "#ADB5C4" }
  ];

  var MedalTier = {
    MEDALS: MEDALS,

    /** Höchste erreichte Stufe, null unterhalb von Bronze. */
    forScore: function (score) {
      var found = null;
      for (var i = 0; i < MEDALS.length; i++) {
        if (score >= MEDALS[i].threshold) found = MEDALS[i];
      }
      return found;
    },

    /** Nächste noch nicht erreichte Stufe, null ab Platin. */
    next: function (score) {
      for (var i = 0; i < MEDALS.length; i++) {
        if (score < MEDALS[i].threshold) return MEDALS[i];
      }
      return null;
    },

    /** Bringt dieser Score eine höhere Stufe als der bisherige Bestwert? */
    isUpgrade: function (score, previousBest) {
      var a = MedalTier.forScore(score);
      var b = MedalTier.forScore(previousBest);
      return (a ? MEDALS.indexOf(a) : -1) > (b ? MEDALS.indexOf(b) : -1);
    }
  };

  global.DotSkin = DotSkin;
  global.MedalTier = MedalTier;
  if (typeof module !== "undefined" && module.exports) {
    module.exports = { DotSkin: DotSkin, MedalTier: MedalTier };
  }
})(typeof globalThis !== "undefined" ? globalThis : this);

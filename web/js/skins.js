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

  /** Himmelsstufen — Spiegel von SkyStages im Renderer. */
  var SKY_STAGES = [
    "#4EC0CA", "#5B9BD5", "#7B6FD0", "#C0616F", "#D98A3D", "#3D4A8C", "#2A2640"
  ];

  /**
   * Laenge eines Himmels-Umlaufs in Stufen: sechs hoch von Tag bis Nacht,
   * sechs zurueck. Bei einer Stufe je fuenf Punkte ist ein Umlauf also 60
   * Punkte lang.
   */
  var SKY_CYCLE = 12;

  /**
   * Himmelsstufe zu einem Score (Port von SkinPaint.skyStage). Der Zaehler
   * bleibt nicht in der Nacht stehen, sondern laeuft weiter — hoch bis zur
   * Nacht und wieder zurueck zum Tag.
   */
  function skyStage(score) {
    var step = ((Math.floor(score / 5) % SKY_CYCLE) + SKY_CYCLE) % SKY_CYCLE;
    return step <= SKY_CYCLE / 2 ? step : SKY_CYCLE - step;
  }

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

  /**
   * Deterministisches Rauschen ueber Feld und Zeitschritt (Port von
   * SkinPaint.noise). Bewusst kein Zufall: Alle Ports muessen beim selben
   * Zeitschritt dasselbe Bild ergeben.
   *
   * Kotlin rechnet hier in 32-Bit-Int mit Ueberlauf — JS-Zahlen sind
   * Gleitkomma und wuerden ab 2^53 abrunden. Math.imul multipliziert
   * genau wie ein Int, ">>>" ist das Gegenstueck zu "ushr", und "^"
   * liefert wieder einen vorzeichenbehafteten 32-Bit-Wert. Auch der
   * Betrag wird auf 32 Bit gestutzt, damit er sich bei Int.MIN_VALUE so
   * verhaelt wie Kotlins abs (das dort negativ bleibt).
   */
  function noise(col, row, seed) {
    var n = (Math.imul(col, 73856093) ^ Math.imul(row, 19349663) ^ Math.imul(seed, 83492791)) | 0;
    n = Math.imul(n ^ (n >>> 13), 1274126177);
    var v = (n ^ (n >>> 16)) | 0;
    return (v < 0 ? -v : v) | 0;
  }

  function normalize(state) {
    state = state || {};
    // hour und month kommen von der Geraete-Uhr, nicht aus dem Lauf. Die
    // Standardwerte (Mittag im Juni) sorgen dafuer, dass jede Vorschau
    // ohne Kalender dasselbe Bild zeigt — deshalb hier keine
    // ||-Kurzschluesse: Stunde 0 ist ein gueltiger Wert.
    return {
      elapsed: state.elapsed || 0,
      score: state.score || 0,
      perfectStreak: state.perfectStreak || 0,
      hour: state.hour === undefined ? 12 : state.hour,
      month: state.month === undefined ? 6 : state.month
    };
  }

  /** Bestleistungen und Ausdauer-Zaehler, aufgefuellt (Port von SkinStats). */
  function normalizeStats(stats) {
    stats = stats || {};
    return {
      bestScore: stats.bestScore || 0,
      bestPerfectStreak: stats.bestPerfectStreak || 0,
      bestDailyStreak: stats.bestDailyStreak || 0,
      runCount: stats.runCount || 0,
      totalScore: stats.totalScore || 0,
      daysPlayed: stats.daysPlayed || 0,
      monthsPlayed: stats.monthsPlayed || 0,
      seasonEarned: stats.seasonEarned || 0,
      patronOwned: !!stats.patronOwned
    };
  }

  // ===== Muster-Details =====

  var MELON_SEEDS = [[4, 3], [7, 5], [3, 6], [8, 2], [6, 7]];
  var MUSHROOM_DOTS = [[3, 2], [8, 1], [5, 4], [9, 5], [2, 6], [6, 6]];
  var KOI_RED = [[2, 4], [3, 4], [3, 5], [2, 5], [4, 5], [3, 3]];
  var KOI_ORANGE = [[8, 7], [9, 7], [8, 8], [7, 8], [9, 6], [7, 7]];
  var GALAXY_STARS = [[3, 3], [9, 4], [5, 8], [10, 8], [2, 7]];
  var GALAXY_NEBULA = [[7, 2], [4, 6], [8, 9]];
  /** Fuenfeck in der Mitte plus angeschnittene Flecken am Rand. */
  var BALL_PATCHES = [
    [6, 5], [5, 6], [6, 6], [7, 6], [5, 7], [6, 7], [7, 7], [6, 8],
    [1, 4], [2, 4], [2, 3], [10, 9], [9, 10], [3, 11]
  ];
  var DONUT_SPRINKLES = [
    [3, 2], [5, 1], [8, 2], [4, 4], [9, 4], [6, 3], [10, 5], [2, 4]
  ];
  /** Zickzack des Blitzes — laeuft von oben rechts nach unten links. */
  var BOLT = [
    [7, 2], [6, 3], [6, 4], [7, 4], [5, 5], [5, 6], [6, 6],
    [4, 7], [4, 8], [5, 8], [3, 9]
  ];

  /** Heller Bauch des PINGUIN — als Ellipse, damit er zur Kugel passt. */
  function isBelly(col, row) {
    var dx = (col - 6) * 0.9;
    var dy = row - 8.2;
    return Math.sqrt(dx * dx + dy * dy) < 3.4;
  }

  /** Geschnitztes Grinsen des KUERBIS, bewusst unterhalb des Auges. */
  function isGrin(col, row) {
    if (row === 10) return col >= 3 && col <= 9;
    return row === 9 && (col === 3 || col === 6 || col === 9);
  }

  /** Pixelherz, tief gesetzt — oben hat das Auge Vorrang. */
  function isHeart(col, row) {
    if (row === 6) return col === 4 || col === 5 || col === 7 || col === 8;
    if (row === 7 || row === 8) return col >= 3 && col <= 9;
    if (row === 9) return col >= 4 && col <= 8;
    if (row === 10) return col >= 5 && col <= 7;
    return row === 11 && col === 6;
  }

  var SPRINKLE_COLORS = ["#4EC0CA", "#FFF3B8", "#FFFFFF", "#FF5A36"];
  var CONFETTI_COLORS = ["#FF5A36", "#4EC0CA", "#FFD847", "#FF6FD8", "#7B6FD0"];
  var DISCO_COLORS = ["#FF6FD8", "#4EC0CA", "#FFD847"];
  var DIAMOND_COLORS = ["#DCEBFF", "#A8C8EE", "#7FA8D8"];

  /** Baender des OSTEREI: Koerper- und Schattenfarbe je Band. */
  var EASTER_COLORS = [
    ["#FFB8D9", "#E086B4"],
    ["#BFE9FF", "#8FC8E8"],
    ["#FFF0A8", "#E0CE6A"],
    ["#D9C2FF", "#B096E8"]
  ];

  /**
   * Bei welchem Score THERMO fertig durchgegluet ist. Bewusst die
   * Platin-Schwelle: Der Vogel ist genau dann weissgluehend, wenn der
   * Lauf die hoechste Medaille erreicht hat.
   */
  var HEAT_SCORE = 40;

  /** Legierungen von MEDAILLE: Zinn, Bronze, Silber, Gold, Platin. */
  var MEDAL_COLORS = [
    ["#B8BEC9", "#8A909C"],
    ["#CD7F32", "#9C5A1E"],
    ["#C0C0C0", "#8F8F9C"],
    ["#FFD700", "#C9A400"],
    ["#E5E4E2", "#ADB5C4"]
  ];

  /** Medaillenstufe eines Scores (0 = noch keine) — Port von SkinPaint. */
  function medalTier(score) {
    if (score >= 40) return 4;
    if (score >= 30) return 3;
    if (score >= 20) return 2;
    if (score >= 10) return 1;
    return 0;
  }

  /**
   * Kleid von TAGESZEIT nach Stunde: Morgenrot, Mittagsblau, Abendglut,
   * Nachtblau mit Sternen. Nur die Nacht hat einen dritten Wert.
   */
  function dayPalette(hour) {
    if (hour >= 5 && hour <= 8) return ["#FFC58F", "#E8935A"];
    if (hour >= 9 && hour <= 16) return ["#8FD8FF", "#4FA3D8"];
    if (hour >= 17 && hour <= 20) return ["#FF8A3C", "#C0616F"];
    return ["#3D4A8C", "#232B55", "#FFF3B8"];
  }

  /**
   * Kleid von JAHRESZEIT nach Kalendermonat (1-12): Koerper, Schatten,
   * Streufarbe und der Rest, bei dem die Streufarbe erscheint.
   */
  function seasonPalette(month) {
    if (month >= 3 && month <= 5) return ["#FFB8D9", "#E086B4", "#FFFFFF", 5];
    if (month >= 6 && month <= 8) return ["#4EC0CA", "#2E8E98", "#FFF3B8", 7];
    if (month >= 9 && month <= 11) return ["#E08A3C", "#B2571F", "#7A3B1F", 4];
    return ["#DCF3FF", "#A8C8DE", "#FFFFFF", 6];
  }

  /**
   * Die vier Saison-Skins und ihr Fenster (Port von Season). requiredDays
   * zaehlt Tage mit mindestens einem Lauf im Monat — bewusst kein Rekord:
   * Ein Saison-Skin haengt an Anwesenheit, nicht an Koennen. Das Bit
   * merkt sich den Erfolg dauerhaft, sonst waere der Kuerbis im November
   * wieder weg.
   */
  var SEASONS = [
    { skin: "KUERBIS", month: 10, requiredDays: 5, bit: 1 },
    { skin: "ZUCKERSTANGE", month: 12, requiredDays: 5, bit: 2 },
    { skin: "HERZ", month: 2, requiredDays: 3, bit: 4 },
    { skin: "OSTEREI", month: 4, requiredDays: 5, bit: 8 }
  ];

  /** Der Skin, der in diesem Monat verdient werden kann — sonst null. */
  function seasonForMonth(month) {
    for (var i = 0; i < SEASONS.length; i++) {
      if (SEASONS[i].month === month) return SEASONS[i];
    }
    return null;
  }

  function seasonForSkin(name) {
    for (var i = 0; i < SEASONS.length; i++) {
      if (SEASONS[i].skin === name) return SEASONS[i];
    }
    return null;
  }

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
    {
      name: "EI", titleKey: "skin_ei", hintKey: "skin_hint_ei",
      body: "#FFE58F", shade: "#E8B92E", shine: "#FFFFFF",
      cell: function (col, row) {
        // Gezackte Schalenkante: Die Kappe endet je Spalte etwas anders,
        // sonst laege ein gerader Deckel auf dem Kueken.
        var jag = 3.5 + (col % 3 === 0 ? 1 : 0) + (col % 2 === 0 ? 0.5 : 0);
        if (row <= jag) return shaded(col, row, "#F7F3EE", "#DCD2C4");
        return shaded(col, row, "#FFE58F", "#E8B92E");
      }
    },
    {
      name: "TIGER", titleKey: "skin_tiger", hintKey: "skin_hint_tiger",
      body: "#FF8A2B", shade: "#2A1F1C", shine: "#FFE0B8",
      cell: function (col, row) {
        var wave = col + Math.sin(row * 0.55) * 2.2;
        if (((wave % 6) + 6) % 6 < 1.7) return "#2A1F1C";
        return shaded(col, row, "#FF8A2B", "#D2601A");
      }
    },
    {
      name: "PINGUIN", titleKey: "skin_pinguin", hintKey: "skin_hint_pinguin",
      body: "#2E3440", shade: "#1B1F28", shine: "#FFFFFF",
      cell: function (col, row) {
        if (row >= 11) return "#F5A623";
        if (isBelly(col, row)) return shaded(col, row, "#F7F3EE", "#DCD2C4");
        return shaded(col, row, "#2E3440", "#1B1F28");
      }
    },
    {
      name: "FUSSBALL", titleKey: "skin_fussball", hintKey: "skin_hint_fussball",
      body: "#F7F3EE", shade: "#2A2C33", shine: "#FFFFFF",
      cell: function (col, row) {
        if (at(BALL_PATCHES, col, row)) return "#2A2C33";
        return shaded(col, row, "#F7F3EE", "#D9CEC2");
      }
    },
    {
      name: "DONUT", titleKey: "skin_donut", hintKey: "skin_hint_donut",
      body: "#FF7FBF", shade: "#C08A47", shine: "#FFFFFF",
      cell: function (col, row) {
        var edge = 5.5 + Math.sin(col * 1.05) * 1.3;
        if (row > edge) return shaded(col, row, "#E8B36A", "#C08A47");
        if (at(DONUT_SPRINKLES, col, row)) {
          return SPRINKLE_COLORS[(col + row) % SPRINKLE_COLORS.length];
        }
        return shaded(col, row, "#FF7FBF", "#E04E9C");
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
    {
      name: "WELLE", titleKey: "skin_welle", hintKey: "skin_hint_welle",
      body: "#2E86D8", shade: "#1F5FA8", shine: "#FFFFFF", animated: true,
      cell: function (col, row, state) {
        // Eine Wasserlinie, die im Koerper schwappt — darueber Luft, an
        // der Kante Schaum.
        var line = 5.6 + Math.sin(state.elapsed * 1.7 + col * 0.52) * 1.5;
        if (row > line + 0.9) return shaded(col, row, "#2E86D8", "#1F5FA8");
        if (row > line) return "#BFE9FF";
        return shaded(col, row, "#DCF3FF", "#BBD9E8");
      }
    },
    {
      name: "GEWITTER", titleKey: "skin_gewitter", hintKey: "skin_hint_gewitter",
      body: "#4A5568", shade: "#2F3644", shine: "#FFF3B8", animated: true,
      cell: function (col, row, state) {
        // Der Blitz ist kurz und selten: Er traegt den Skin, aber ein
        // Dauerflackern wuerde den Punkt unlesbar machen.
        var phase = state.elapsed % 2.6;
        var flash = phase < 0.14 ? 1 : (phase < 0.30 ? 0.35 : 0);
        var base = shaded(col, row, "#4A5568", "#2F3644");
        if (flash > 0 && at(BOLT, col, row)) return "#FFF3B8";
        if (flash > 0) return mix(base, "#FFE95E", 0.5 * flash);
        return base;
      }
    },
    {
      name: "KONFETTI", titleKey: "skin_konfetti", hintKey: "skin_hint_konfetti",
      body: "#F7F3EE", shade: "#FF5A36", shine: "#FFFFFF", animated: true,
      cell: function (col, row, state) {
        var step = Math.floor(state.elapsed * 0.9);
        var n = noise(col, row, step);
        if (n % 100 < 38) return CONFETTI_COLORS[n % CONFETTI_COLORS.length];
        return shaded(col, row, "#F7F3EE", "#D9CEC2");
      }
    },
    {
      name: "DISCO", titleKey: "skin_disco", hintKey: "skin_hint_disco",
      body: "#C3CBD9", shade: "#8892A6", shine: "#FFFFFF", animated: true,
      cell: function (col, row, state) {
        var facet = (Math.floor(col / 2) + Math.floor(row / 2)) % 2;
        var base = facet === 0 ? "#C3CBD9" : "#8892A6";
        var k = Math.floor(state.elapsed * 7);
        if ((col * 2 + row * 3 + k) % 11 === 0) return "#FFFFFF";
        if ((col + row * 2 + k) % 13 === 0) {
          return DISCO_COLORS[(col + row + k) % DISCO_COLORS.length];
        }
        if (col + row > GRID * 1.15) return mix(base, "#3B4152", 0.3);
        return base;
      }
    },
    {
      name: "HOLO", titleKey: "skin_holo", hintKey: "skin_hint_holo",
      body: "#7FD8E8", shade: "#C93BAA", shine: "#FFFFFF", animated: true,
      cell: function (col, row, state) {
        // Sammelkarten-Folie. Der Gruenbereich wird uebersprungen wie beim
        // REGENBOGEN — ein gruener Vogel saehe fuer einen Moment aus wie
        // die Zielzone.
        var h = ((col - row) * 13 + state.elapsed * 60) % 360;
        if (h < 0) h += 360;
        if (h > 80 && h < 150) h += 70;
        var color = hsl(h, 0.75, col + row > GRID * 1.15 ? 0.46 : 0.66);
        var sweep = ((state.elapsed * 5) % 20) - 4;
        var d = Math.abs(col + row * 0.6 - sweep);
        if (d < 1.4) color = mix(color, "#FFFFFF", 1 - d / 1.4);
        return color;
      }
    },

    // ===== Reagierend =====
    {
      name: "CHAMAELEON", titleKey: "skin_chamaeleon", hintKey: "skin_hint_chamaeleon",
      body: "#8FD8DE", shade: "#3F9BA5", shine: "#FFFFFF",
      cell: function (col, row, state) {
        var sky = SKY_STAGES[skyStage(state.score)];
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
    },
    {
      name: "THERMO", titleKey: "skin_thermo", hintKey: "skin_hint_thermo",
      body: "#FFD847", shade: "#E0A400", shine: "#FFFFFF",
      cell: function (col, row, state) {
        // Der Vogel heizt sich im Lauf auf: kalt bei 0, weissgluehend bei
        // HEAT_SCORE. Fortschrittsanzeige an der Stelle, auf die der
        // Daumen ohnehin schaut.
        var k = Math.min(state.score, HEAT_SCORE) / HEAT_SCORE;
        var body = k < 0.5
          ? mix("#8FD8FF", "#FFD847", k * 2)
          : mix("#FFD847", "#FFF6E0", (k - 0.5) * 2);
        var shade = k < 0.5
          ? mix("#4FA3D8", "#E0A400", k * 2)
          : mix("#E0A400", "#FF7A3C", (k - 0.5) * 2);
        return shaded(col, row, body, shade);
      }
    },
    {
      name: "MEDAILLE", titleKey: "skin_medaille", hintKey: "skin_hint_medaille",
      body: "#C0C0C0", shade: "#8F8F9C", shine: "#FFFFFF",
      cell: function (col, row, state) {
        var tier = MEDAL_COLORS[medalTier(state.score)];
        var dx = col - MID, dy = row - MID;
        // Praegerand: aussen dunkler, damit die Muenze eine Kante hat.
        if (Math.sqrt(dx * dx + dy * dy) > RR - 1.85) return mix(tier[1], "#000000", 0.18);
        return shaded(col, row, tier[0], tier[1]);
      }
    },
    {
      name: "TAGESZEIT", titleKey: "skin_tageszeit", hintKey: "skin_hint_tageszeit",
      body: "#8FD8FF", shade: "#3D4A8C", shine: "#FFFFFF",
      cell: function (col, row, state) {
        var p = dayPalette(state.hour);
        if (p.length > 2 && at(GALAXY_STARS, col, row)) return p[2];
        return shaded(col, row, p[0], p[1]);
      }
    },
    {
      name: "JAHRESZEIT", titleKey: "skin_jahreszeit", hintKey: "skin_hint_jahreszeit",
      body: "#4EC0CA", shade: "#2E8E98", shine: "#FFFFFF",
      cell: function (col, row, state) {
        var p = seasonPalette(state.month);
        if ((col * 3 + row * 5) % 11 === p[3]) return p[2];
        return shaded(col, row, p[0], p[1]);
      }
    },

    // ===== Saison =====
    {
      name: "KUERBIS", titleKey: "skin_kuerbis", hintKey: "skin_hint_kuerbis",
      body: "#F5821F", shade: "#C25E10", shine: "#FFE0B8",
      cell: function (col, row) {
        if (row <= 1 && col >= 5 && col <= 7) return "#5AA020";
        if (isGrin(col, row)) return "#2A1F1C";
        var rib = Math.abs((((col + 1) % 4) + 4) % 4 - 2) < 1;
        var body = rib ? "#D86A12" : "#F5821F";
        return col + row > GRID * 1.15 ? mix(body, "#000000", 0.22) : body;
      }
    },
    {
      name: "ZUCKERSTANGE", titleKey: "skin_zuckerstange", hintKey: "skin_hint_zuckerstange",
      body: "#E8452F", shade: "#C2301F", shine: "#FFFFFF", animated: true,
      cell: function (col, row, state) {
        var band = Math.floor((col + row - state.elapsed * 4) / 2.2);
        if (((band % 2) + 2) % 2 === 0) return shaded(col, row, "#E8452F", "#C2301F");
        return shaded(col, row, "#F7F3EE", "#DCD2C4");
      }
    },
    {
      name: "HERZ", titleKey: "skin_herz", hintKey: "skin_hint_herz",
      body: "#FF6FA8", shade: "#D6407E", shine: "#FFFFFF",
      cell: function (col, row) {
        // Das Herz sitzt tief: Weiter oben verdeckte es das Auge, und
        // zwei Zeichen im selben Gesicht kaempfen gegeneinander.
        if (isHeart(col, row)) return shaded(col, row, "#FFF0F5", "#FFC8DC");
        return shaded(col, row, "#FF6FA8", "#D6407E");
      }
    },
    {
      name: "OSTEREI", titleKey: "skin_osterei", hintKey: "skin_hint_osterei",
      body: "#FFB8D9", shade: "#B096E8", shine: "#FFFFFF",
      cell: function (col, row) {
        var band = Math.floor((row + (col % 2 === 0 ? 1 : 0)) / 2) % 4;
        if (band === 1 && col % 3 === 0) return "#FFFFFF";
        return shaded(col, row, EASTER_COLORS[band][0], EASTER_COLORS[band][1]);
      }
    },

    // ===== Goenner =====
    {
      name: "DIAMANT", titleKey: "skin_diamant", hintKey: "skin_hint_diamant",
      body: "#A8C8EE", shade: "#4E6A96", shine: "#FFFFFF", animated: true,
      cell: function (col, row, state) {
        var facet = ((Math.floor(col * 0.9 + row * 0.4) % 3) + 3) % 3;
        var base = DIAMOND_COLORS[facet];
        var sweep = ((state.elapsed * 7) % 20) - 4;
        var d = Math.abs(col + row * 0.5 - sweep);
        if (d < 1.2) base = mix(base, "#FFFFFF", 1 - d / 1.2);
        if (noise(col, row, Math.floor(state.elapsed * 3)) % 37 === 0) return "#FFFFFF";
        if (col + row > GRID * 1.15) return mix(base, "#4E6A96", 0.35);
        return base;
      }
    },
    {
      name: "PHOENIX", titleKey: "skin_phoenix", hintKey: "skin_hint_phoenix",
      body: "#FF8A2B", shade: "#8E2410", shine: "#FFF3B8",
      animated: true, trail: true,
      cell: function (col, row, state) {
        var flicker = 0.5 + 0.5 * Math.sin(state.elapsed * 4 + col * 0.7 - row * 1.1);
        var heat = Math.max(0, 1 - row / 11) * 0.6 + flicker * 0.5;
        var color = heat > 0.9 ? "#FFF3B8" : mix("#E5341A", "#FFB020", Math.min(1, heat));
        return col + row > GRID * 1.15 ? mix(color, "#8E2410", 0.35) : color;
      }
    },
    {
      name: "ONYX", titleKey: "skin_onyx", hintKey: "skin_hint_onyx",
      body: "#221C29", shade: "#141018", shine: "#FFE07A", animated: true,
      cell: function (col, row, state) {
        var vein = Math.sin(col * 1.15 + row * 0.85) > 0.55;
        if (!vein) return col + row > GRID * 1.15 ? "#141018" : "#221C29";
        var glow = 0.5 + 0.5 * Math.sin(state.elapsed * 1.6 + col * 0.5 + row * 0.4);
        return mix("#8A6A1E", "#FFE07A", glow);
      }
    }
  ];

  /**
   * Familien fuers Skin-Menue: 42 Skins am Stueck sind eine Wand, sechs
   * Ueberschriften machen daraus lesbare Abschnitte. Die Grenzen folgen
   * der Reihenfolge in SkinId — dort stehen die Familien schon beisammen,
   * deshalb reicht der erste Skin je Familie als Marke.
   */
  var FAMILIES = [
    { first: "KLASSIK", titleKey: "skin_family_einfarbig" },
    { first: "BIENE", titleKey: "skin_family_gemustert" },
    { first: "REGENBOGEN", titleKey: "skin_family_bewegt" },
    { first: "CHAMAELEON", titleKey: "skin_family_reagierend" },
    { first: "KUERBIS", titleKey: "skin_family_saison" },
    { first: "DIAMANT", titleKey: "skin_family_goenner" }
  ];

  /** Ueberschrift, die VOR diesem Skin steht — sonst null. */
  function familyTitleKey(skin) {
    for (var i = 0; i < FAMILIES.length; i++) {
      if (FAMILIES[i].first === skin.name) return FAMILIES[i].titleKey;
    }
    return null;
  }

  /** Farbe eines Rasterfelds; state ist optional (Standbild). */
  function cell(skin, col, row, state) {
    return skin.cell(col, row, normalize(state));
  }

  /** Glanzpunkt — bei NEON wandert er mit der Leuchtfarbe mit. */
  function shine(skin, state) {
    return skin.shineColor ? skin.shineColor(normalize(state)) : skin.shine;
  }

  /**
   * Felder, an die das Auge grenzt — in beiden Blickrichtungen, damit die
   * Entscheidung nicht beim Richtungswechsel kippt (Port von SkinPaint).
   */
  var EYE_NEIGHBOURS = [
    [7, 3], [7, 4], [7, 5], [7, 6], [8, 2], [9, 2], [10, 2], [8, 7], [9, 7], [10, 7],
    [5, 3], [5, 4], [5, 5], [5, 6], [4, 2], [3, 2], [2, 2], [4, 7], [3, 7], [2, 7]
  ];

  /** Ab welchem Abstand zu Weiss (0 bis 441) ein Koerper als zu hell gilt. */
  var EYE_OUTLINE_DISTANCE = 60;

  function distanceToWhite(hex) {
    var c = channels(hex);
    var r = 255 - c[0], g = 255 - c[1], b = 255 - c[2];
    return Math.sqrt(r * r + g * g + b * b);
  }

  /**
   * Braucht das Auge dieses Skins eine Kontur zum Koerper hin? Auf sehr
   * hellen Koerpern (Koi, Chrom) verschwaende das weisse Auge sonst; auf
   * allen anderen wirkt die Kontur wie ein Kasten ums Auge. Gemessen im
   * Ruhezustand, damit sie bei bewegten Skins nicht flackert.
   */
  function needsEyeOutline(skin) {
    return EYE_NEIGHBOURS.some(function (feld) {
      return distanceToWhite(cell(skin, feld[0], feld[1])) < EYE_OUTLINE_DISTANCE;
    });
  }

  /** Hinterlaesst der Skin Nachbilder auf der Bahn? */
  function hasTrail(skin) { return !!skin.trail; }

  /** Haengt die Farbe an der Uhr (im Gegensatz zu Muster und Spielstand)? */
  function isAnimated(skin) { return !!skin.animated; }

  /** Saison-Skin? Verdienbar nur im eigenen Monat (siehe SEASONS). */
  function isSeasonal(skin) { return seasonForSkin(skin.name) !== null; }

  /** Gekaufter Goenner-Skin? */
  function isPatron(skin) {
    return skin.name === "DIAMANT" || skin.name === "PHOENIX" || skin.name === "ONYX";
  }

  /**
   * Zaehlt dieser Skin fuer den Sammlungsstand — und damit fuer die
   * Bedingung des REGENBOGEN? Saison-Skins nicht, sonst waere der
   * Regenbogen fruehestens nach einem Jahr erreichbar; Goenner-Skins
   * nicht, sonst waere er kaeuflich.
   */
  function countsForCollection(skin) {
    return !isSeasonal(skin) && !isPatron(skin);
  }

  /**
   * stats = { bestScore, bestPerfectStreak, bestDailyStreak, runCount,
   * totalScore, daysPlayed, monthsPlayed, seasonEarned, patronOwned }
   */
  function isUnlocked(skin, rawStats) {
    var stats = normalizeStats(rawStats);
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

      // Ausdauer statt Koennen: Diese Achsen wachsen mit jedem Lauf, auch
      // mit den schlechten. Ohne sie haengen fast alle Skins am Rekord,
      // und wer bei 25 stehenbleibt, sammelt nie wieder etwas.
      case "EI": return stats.runCount >= 25;
      case "TIGER": return stats.runCount >= 100;
      case "MEDAILLE": return stats.runCount >= 200;
      case "FUSSBALL": return stats.runCount >= 300;
      case "DONUT": return stats.totalScore >= 1000;
      case "KONFETTI": return stats.totalScore >= 5000;
      case "TAGESZEIT": return stats.daysPlayed >= 7;
      case "JAHRESZEIT": return stats.monthsPlayed >= 3;

      case "PINGUIN": return stats.bestScore >= 65;
      case "WELLE": return stats.bestScore >= 70;
      case "THERMO": return stats.bestScore >= 75;
      case "HOLO": return stats.bestScore >= 80;
      case "GEWITTER": return stats.bestPerfectStreak >= 15;
      case "DISCO": return stats.bestDailyStreak >= 21;

      // Saison: im eigenen Monat verdient, danach fuer immer gehalten.
      // Geprueft wird deshalb die Maske, nie der Kalender.
      case "KUERBIS":
      case "ZUCKERSTANGE":
      case "HERZ":
      case "OSTEREI":
        return (stats.seasonEarned & seasonForSkin(skin.name).bit) !== 0;

      // Goenner: gekauft. Kein Verdienst, keine Feier, kein Zaehlwert.
      case "DIAMANT":
      case "PHOENIX":
      case "ONYX":
        return stats.patronOwned;

      // Der Regenbogen ist der Abschluss der Sammlung: Er kommt erst,
      // wenn alle Skins offen sind, die fuer die Sammlung zaehlen (er
      // selbst zaehlt nicht mit, sonst waere die Bedingung zirkulaer —
      // Saison und Goenner zaehlen nicht mit, siehe countsForCollection).
      case "REGENBOGEN":
        return SKINS.every(function (s) {
          return s.name === "REGENBOGEN" || !countsForCollection(s) || isUnlocked(s, stats);
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

  /**
   * Wie viele Skins dauerhaft verdient sind. Gekaufte und Saison-Skins
   * bleiben aussen vor: Der Zaehler ist eine Leistungsanzeige.
   */
  function unlockedCount(stats) {
    return SKINS.filter(function (s) {
      return countsForCollection(s) && isUnlocked(s, stats);
    }).length;
  }

  /** Wie viele Skins dieser Zaehler insgesamt erreichen kann. */
  function collectableCount() {
    return SKINS.filter(countsForCollection).length;
  }

  var DotSkin = {
    SKINS: SKINS,
    SKY_STAGES: SKY_STAGES,
    SKY_CYCLE: SKY_CYCLE,
    SEASONS: SEASONS,
    FAMILIES: FAMILIES,
    HEAT_SCORE: HEAT_SCORE,
    MEDAL_COLORS: MEDAL_COLORS,
    SPRINKLE_COLORS: SPRINKLE_COLORS,
    CONFETTI_COLORS: CONFETTI_COLORS,
    DISCO_COLORS: DISCO_COLORS,
    DIAMOND_COLORS: DIAMOND_COLORS,
    EASTER_COLORS: EASTER_COLORS,
    skyStage: skyStage,
    GRID: GRID,
    TRAIL_STEPS: TRAIL_STEPS,
    TRAIL_SPACING: TRAIL_SPACING,
    cell: cell,
    shine: shine,
    noise: noise,
    medalTier: medalTier,
    dayPalette: dayPalette,
    seasonPalette: seasonPalette,
    seasonForMonth: seasonForMonth,
    seasonForSkin: seasonForSkin,
    familyTitleKey: familyTitleKey,
    needsEyeOutline: needsEyeOutline,
    hasTrail: hasTrail,
    isAnimated: isAnimated,
    isSeasonal: isSeasonal,
    isPatron: isPatron,
    countsForCollection: countsForCollection,
    mix: mix,
    hsl: hsl,
    isUnlocked: isUnlocked,
    fromName: fromName,
    unlockedCount: unlockedCount,
    collectableCount: collectableCount
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

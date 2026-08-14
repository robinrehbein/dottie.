/*
 * Port von ScenePaint.kt (:core): das Farbwerk aller Kulissen — Himmel in
 * sieben Stufen, Wolken, Requisiten und Bodenstreifen — samt ihren
 * Freischalt-Bedingungen.
 *
 * Eine Kulisse ist alles, was NICHT ueber Treffer entscheidet. Die Bahn
 * gehoert ausdruecklich nicht dazu: Zielzone, Perfekt-Kern und Falle
 * behalten in jeder Kulisse dieselben Farben. Genau deshalb ist die
 * Kulisse die verkaeufliche Flaeche und die Bahn nicht.
 *
 * Die Requisiten sind Daten, kein Zeichencode: shape/size/sway/Farben,
 * und der Renderer wertet dieselbe Liste aus wie Compose, SpriteKit und
 * die Uhr.
 */
(function (global) {
  "use strict";

  /**
   * Die Bodenkante als Anteil der Bildhoehe. Layout-Anker, nicht Dekor:
   * Requisiten stehen darauf, der Bodenstreifen beginnt dort, und die
   * Tod-Animation misst ihren Sturz daran. Gilt fuer JEDE Kulisse — auch
   * fuer WELTRAUM, der gar keinen Boden zeichnet.
   */
  var GROUND_TOP = 0.88;

  /** Requisiten-Plaetze je Kulisse (Bestand: Baum, Blume, Baum, Strauch). */
  var PROP_SLOTS = 4;

  /** Mindestabstand zu Zielzone und Falle bzw. zwischen zwei Himmelsstufen. */
  var MIN_ZONE_DISTANCE = 60;
  var MIN_SKY_STEP = 40;

  /**
   * Die Greens, die die WIESE seit jeher traegt. Sie sind praktisch die
   * Zielzonenfarbe — die Grasnarbe ist sogar exakt sie. Das bleibt so:
   * Diese Flaechen liegen am unteren Bildrand, nie im Ringband. Benannte
   * Ausnahme statt stillem Umfaerben (siehe ScenePaintTest).
   */
  var LEGACY_ZONE_GREENS = ["#71C837", "#5AA82C", "#9DE85A", "#74BF2E"];

  function prop(shape, size, sway, dark, body, light, stem, stemShade, accents) {
    return {
      shape: shape,
      size: size,
      sway: sway,
      dark: dark,
      body: body,
      light: light,
      stem: stem || "#543847",
      stemShade: stemShade || "#543847",
      accents: accents || []
    };
  }

  var SCENES = [
    {
      // Der Bestand. Jeder Wert stammt aus GameOverlays.kt und ist
      // absichtlich unveraendert: Wer die Umstellung sieht, hat sie
      // falsch gemacht.
      name: "WIESE", titleKey: "scene_wiese", hintKey: null,
      sky: ["#4EC0CA", "#5B9BD5", "#7B6FD0", "#C0616F", "#D98A3D", "#3D4A8C", "#2A2640"],
      cloud: "#E9FCFD",
      ground: {
        sand: "#DED895", sandShade: "#D3C87E",
        turfDark: "#74BF2E", turfLight: "#9DE85A"
      },
      props: [
        prop("BAUM", 0.075, 1.0, "#5AA82C", "#71C837", "#9DE85A", "#9C6B3C", "#7A4E2A"),
        // Die Mitte der Bluete ist Gold (DotBody), nicht Gruen — sie war
        // es immer, und sie ist der einzige warme Punkt im Gruen.
        prop("BLUME", 0.032, 0.8, "#5AA82C", "#71C837", "#FFD847", null, null,
          ["#E53935", "#E9FCFD"]),
        prop("BAUM", 0.058, -1.0, "#5AA82C", "#71C837", "#9DE85A", "#9C6B3C", "#7A4E2A"),
        prop("STRAUCH", 0.026, 0.4, "#5AA82C", "#71C837", "#9DE85A")
      ]
    },
    {
      // Wueste: heller Dunsthimmel, der ueber Sandschleier und Glut in
      // eine kalte Nacht faellt. Die Kakteen sind bewusst blaustichig —
      // ein Wiesengruen haette den Mindestabstand zur Zielzone gerissen.
      name: "WUESTE", titleKey: "scene_wueste", hintKey: "scene_hint_wueste",
      sky: ["#A8DCE8", "#F2C46B", "#E8934A", "#C85F3C", "#8E3B47", "#4A2C4E", "#241C33"],
      cloud: "#F7E9C8",
      ground: {
        sand: "#E8C88A", sandShade: "#D4AE6E",
        turfDark: "#C79A55", turfLight: "#EFD7A0"
      },
      props: [
        prop("KAKTUS", 0.075, 1.0, "#1F6B41", "#2E8B57", "#43A96B", null, null,
          ["#E8607A", "#F2A83C"]),
        prop("FELS", 0.032, 0, "#8A6A4A", "#A88860", "#C4A87C"),
        prop("KAKTUS", 0.058, -1.0, "#1F6B41", "#2E8B57", "#43A96B", null, null,
          ["#F2A83C", "#E8607A"]),
        prop("FELS", 0.026, 0, "#8A6A4A", "#A88860", "#C4A87C")
      ]
    },
    {
      // Meer: der Boden ist Wasser, die Narbe darauf ist Schaum.
      name: "MEER", titleKey: "scene_meer", hintKey: "scene_hint_meer",
      sky: ["#5AD2E8", "#2F9AD4", "#2E5FB8", "#C4707C", "#E09A4A", "#35447F", "#1B2138"],
      cloud: "#DFF4FF",
      ground: {
        sand: "#2F86C8", sandShade: "#24699E",
        turfDark: "#4FC3DE", turfLight: "#BFE9FF"
      },
      props: [
        prop("WELLE", 0.075, 1.0, "#1F5FA8", "#2E86D8", "#7FC8F0", null, null,
          ["#FFFFFF", "#DFF4FF"]),
        prop("WELLE", 0.032, 0.8, "#1F5FA8", "#2E86D8", "#7FC8F0", null, null,
          ["#DFF4FF", "#FFFFFF"]),
        prop("WELLE", 0.058, -1.0, "#1F5FA8", "#2E86D8", "#7FC8F0", null, null,
          ["#FFFFFF", "#DFF4FF"]),
        prop("FELS", 0.026, 0, "#4A5A6A", "#6B7C8C", "#9AAAB8")
      ]
    },
    {
      // Berg: Schnee statt Sand, Nadelbaeume mit weisser Spitze.
      name: "BERG", titleKey: "scene_berg", hintKey: "scene_hint_berg",
      sky: ["#A8D8E8", "#6FAFD8", "#4A7FC0", "#8A5A6E", "#D08A5A", "#3E4A78", "#1E2438"],
      cloud: "#F2FAFF",
      ground: {
        sand: "#E4EDF4", sandShade: "#CBD8E4",
        turfDark: "#A8B8C8", turfLight: "#FFFFFF"
      },
      props: [
        prop("NADELBAUM", 0.075, 1.0, "#1E5140", "#2A6B52", "#D8E8F0", "#5C4130", "#46311F"),
        prop("FELS", 0.032, 0, "#6A6E78", "#8A8F9C", "#B8BEC9"),
        prop("NADELBAUM", 0.058, -1.0, "#1E5140", "#2A6B52", "#D8E8F0", "#5C4130", "#46311F"),
        prop("FELS", 0.026, 0, "#6A6E78", "#8A8F9C", "#B8BEC9")
      ]
    },
    {
      // Stadt: Asphalt statt Wiese, Bordstein statt Grasnarbe. Die
      // Hochhaeuser haben Windanteil 0 — ein wankendes Haus waere ein
      // Witz, den das Spiel an dieser Stelle nicht macht.
      name: "STADT", titleKey: "scene_stadt", hintKey: "scene_hint_stadt",
      sky: ["#9ED4E4", "#5F9BC8", "#7B6B9E", "#C4707E", "#E8963C", "#3A3F6E", "#1A1A2E"],
      cloud: "#E4E8F0",
      ground: {
        sand: "#4A4550", sandShade: "#383340",
        turfDark: "#6E6878", turfLight: "#9A93A4"
      },
      props: [
        prop("HOCHHAUS", 0.075, 0, "#3E4A5E", "#56647C", "#8494AC", null, null,
          ["#FFD847", "#7FD8E8"]),
        prop("HOCHHAUS", 0.052, 0, "#4E3E52", "#6C5870", "#9A86A0", null, null,
          ["#7FD8E8", "#FFD847"]),
        prop("HOCHHAUS", 0.062, 0, "#3A4C50", "#54686C", "#869A9E", null, null,
          ["#FFD847", "#7FD8E8"]),
        prop("FELS", 0.026, 0, "#4E4A56", "#6A6672", "#8C8894")
      ]
    },
    {
      // Weltraum: kein Boden, keine Wolken. Statt Pflanzen treiben
      // Felsbrocken in zwei Legierungen auf der Hoehe, auf der sonst der
      // Boden laege — die Linie bleibt, nur der Boden fehlt.
      name: "WELTRAUM", titleKey: "scene_weltraum", hintKey: "scene_hint_weltraum",
      sky: ["#0E1430", "#1A2A62", "#3E1A78", "#6A1E6E", "#8A2C4A", "#3A1A3E", "#0A0716"],
      cloud: null,
      ground: null,
      props: [
        prop("FELS", 0.075, 1.0, "#342E42", "#4E4860", "#726C88"),
        prop("FELS", 0.032, 0.8, "#2E3A4A", "#46566C", "#6C8098"),
        prop("FELS", 0.058, -1.0, "#342E42", "#4E4860", "#726C88"),
        prop("FELS", 0.026, 0.4, "#2E3A4A", "#46566C", "#6C8098")
      ]
    }
  ];

  /** Kulisse zu einem gespeicherten Namen, WIESE als Fallback. */
  function fromName(name) {
    for (var i = 0; i < SCENES.length; i++) {
      if (SCENES[i].name === name) return SCENES[i];
    }
    return SCENES[0];
  }

  /** Himmelsfarbe zu einem Score — der Weg, den alle Renderer gehen. */
  function skyFor(scene, score) {
    return scene.sky[global.DotSkin.skyStage(score)];
  }

  /** Die Bodenkante in Pixeln — der einzige Ort, an dem 0.88 steht. */
  function groundY(height) {
    return height * GROUND_TOP;
  }

  /**
   * Drei Farben fuer Vorschau-Kacheln: Tageshimmel, Boden (im Weltraum
   * ersatzweise die Nachtstufe) und die Koerperfarbe der groessten
   * Requisite.
   */
  function chips(scene) {
    return [
      scene.sky[0],
      scene.ground ? scene.ground.sand : scene.sky[6],
      scene.props[0].body
    ];
  }

  /**
   * Kulissen haengen an denselben Zahlen wie die Skins, aber an anderen
   * Achsen: Wo Skins in dichten Stufen fallen, ist eine Kulisse ein
   * seltener, grosser Wechsel — hohe Schwellen, je eine pro Achse.
   */
  function isUnlocked(scene, rawStats) {
    var stats = rawStats || {};
    switch (scene.name) {
      case "WIESE": return true;
      case "WUESTE": return (stats.runCount || 0) >= 500;
      case "MEER": return (stats.totalScore || 0) >= 10000;
      case "BERG": return (stats.bestDailyStreak || 0) >= 30;
      case "STADT": return (stats.bestScore || 0) >= 85;
      // Der Weltraum ist der Abschluss der Sammlung, wie der REGENBOGEN
      // bei den Skins: Er kommt erst, wenn alle anderen offen sind (er
      // selbst zaehlt nicht mit, sonst waere die Bedingung zirkulaer).
      case "WELTRAUM":
        return SCENES.every(function (s) {
          return s.name === "WELTRAUM" || isUnlocked(s, stats);
        });
    }
    return false;
  }

  function unlockedCount(stats) {
    return SCENES.filter(function (s) { return isUnlocked(s, stats); }).length;
  }

  /**
   * Der Fels-Umriss (ScenePaint.ROCK_PARTS). x ist auf die Mitte
   * bezogen, y zaehlt vom Boden nach oben, tone waehlt aus der
   * Requisiten-Palette: 0 dunkel, 1 Koerper, 2 hell.
   */
  function rockPart(x, y, w, h, tone) {
    return { x: x, y: y, w: w, h: h, tone: tone };
  }

  var ROCK_PARTS = [
    rockPart(-1.20, 0.00, 2.40, 0.42, 0),
    rockPart(-1.10, 0.42, 1.45, 0.40, 1),
    rockPart(0.35, 0.42, 0.75, 0.40, 0),
    rockPart(-0.85, 0.82, 0.70, 0.36, 2),
    rockPart(-0.15, 0.82, 0.55, 0.36, 1),
    rockPart(-0.60, 1.18, 0.50, 0.32, 2)
  ];

  var ROCK_WIDTH = 2.40;
  var ROCK_HEIGHT = 1.50;

  var DotScene = {
    SCENES: SCENES,
    GROUND_TOP: GROUND_TOP,
    PROP_SLOTS: PROP_SLOTS,
    ROCK_PARTS: ROCK_PARTS,
    ROCK_WIDTH: ROCK_WIDTH,
    ROCK_HEIGHT: ROCK_HEIGHT,
    MIN_ZONE_DISTANCE: MIN_ZONE_DISTANCE,
    MIN_SKY_STEP: MIN_SKY_STEP,
    LEGACY_ZONE_GREENS: LEGACY_ZONE_GREENS,
    fromName: fromName,
    skyFor: skyFor,
    groundY: groundY,
    chips: chips,
    isUnlocked: isUnlocked,
    unlockedCount: unlockedCount
  };

  global.DotScene = DotScene;
  if (typeof module !== "undefined" && module.exports) {
    module.exports = DotScene;
  }
})(typeof globalThis !== "undefined" ? globalThis : this);

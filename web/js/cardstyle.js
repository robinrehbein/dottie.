/*
 * Port von core/.../CardStyle.kt: Rahmen und Beiname der Score-Karte.
 *
 * Beides sind reine Ableitungen aus dem Spielstand — der Rahmen aus der
 * Groesse der Sammlung, der Beiname aus dem, was jemand geleistet hat.
 * Die Regel steht in :core, damit App, PWA und iOS bei demselben Stand
 * denselben Rahmen und denselben Titel zeigen; der Testlauf vergleicht
 * diese Datei Eintrag fuer Eintrag mit dem Kotlin-Quelltext.
 *
 * Die Texte stehen hier und nicht in strings.js: Ein Beiname ist ohne
 * seine Bedingung sinnlos, und drei Plattformen, die ihre Titel getrennt
 * pflegen, laufen garantiert auseinander. Deutsch ohne Umlaute — die
 * Pixelschrift hat keine.
 */
(function (global) {
  "use strict";

  /** Die vier Rahmenstufen in ihrer Reihenfolge (CardFrame). */
  var FRAMES = ["SCHLICHT", "DOPPELLINIE", "ZINNEN", "PRACHT"];

  /**
   * Ab wie vielen gesammelten Skins die naechste Stufe greift. Gezaehlt
   * wird ohne Saison- und Goenner-Skins (DotSkin.unlockedCount): Ein
   * gekaufter Rahmen waere etwas anderes als ein verdienter.
   */
  var FRAME_STEPS = [10, 20, 30];

  /**
   * Die Beinamen in ihrer Rangfolge. Die Reihenfolge IST die
   * Entscheidung: Getragen wird der erste Eintrag, dessen Bedingung
   * erfuellt ist. Oben steht deshalb das Seltenste.
   */
  var EPITHETS = [
    { key: "LEGENDE", de: "LEGENDE", en: "LEGEND", axis: "bestScore", target: 80 },
    { key: "UHRWERK", de: "UHRWERK", en: "CLOCKWORK", axis: "bestPerfectStreak", target: 15 },
    { key: "UNBEIRRBAR", de: "UNBEIRRBAR", en: "UNSHAKEN", axis: "bestDailyStreak", target: 30 },
    {
      key: "STEHAUFMAENNCHEN", de: "STEHAUFMAENNCHEN", en: "COMEBACK KID",
      axis: "runCount", target: 500
    },
    {
      key: "PUNKTESAMMLER", de: "PUNKTESAMMLER", en: "POINT COLLECTOR",
      axis: "totalScore", target: 10000
    },
    {
      key: "SCHARFSCHUETZE", de: "SCHARFSCHUETZE", en: "SHARPSHOOTER",
      axis: "bestPerfectStreak", target: 8
    },
    { key: "STAMMGAST", de: "STAMMGAST", en: "REGULAR", axis: "daysPlayed", target: 30 },
    { key: "EINGESPIELT", de: "EINGESPIELT", en: "SEASONED", axis: "runCount", target: 25 }
  ];

  /** Rahmenstufe zu einem Sammlungsstand (Zahl) oder einem Spielstand. */
  function frame(input) {
    var gesammelt = typeof input === "number"
      ? input
      : global.DotSkin.unlockedCount(input || {});
    var stufe = 0;
    FRAME_STEPS.forEach(function (schwelle) {
      if (gesammelt >= schwelle) stufe++;
    });
    return FRAMES[stufe];
  }

  /** Traegt dieser Beiname bei diesem Spielstand? */
  function qualifies(entry, stats) {
    return (stats[entry.axis] || 0) >= entry.target;
  }

  /**
   * Der Beiname dieses Spielstands — null in den ersten Laeufen. Dass ganz
   * am Anfang keiner steht, ist gewollt: Der erste Titel soll ein
   * Ereignis sein und kein Begruessungsgeschenk.
   */
  function epithet(stats) {
    var st = stats || {};
    for (var i = 0; i < EPITHETS.length; i++) {
      if (qualifies(EPITHETS[i], st)) return EPITHETS[i];
    }
    return null;
  }

  /** Der Titel in der Sprache der Oberflaeche. */
  function label(entry, german) {
    return german ? entry.de : entry.en;
  }

  var CardStyle = {
    FRAMES: FRAMES,
    FRAME_STEPS: FRAME_STEPS,
    EPITHETS: EPITHETS,
    frame: frame,
    qualifies: qualifies,
    epithet: epithet,
    label: label
  };

  global.CardStyle = CardStyle;
  if (typeof module !== "undefined" && module.exports) {
    module.exports = CardStyle;
  }
})(typeof globalThis !== "undefined" ? globalThis : this);

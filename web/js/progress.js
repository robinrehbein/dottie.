/*
 * Port von core/.../Progress.kt: Welche Freischaltung als Naechstes
 * faellt und wie weit es noch ist.
 *
 * Die vier Ausdauer-Achsen (Laeufe, Punkte insgesamt, gespielte Tage,
 * verschiedene Monate) liefen seit v2.20 unsichtbar mit — isUnlocked
 * beantwortet nur "offen oder nicht", nie "wie weit noch". Genau diese
 * Luecke schliesst diese Datei, und zwar mit derselben Reihenfolge wie
 * Android und iOS.
 *
 * Zwei Regeln stecken in der Auswahl, nicht in der Anzeige: Goenner-Skins
 * tauchen nie auf (die kauft man, die erreicht man nicht), Saison-Skins
 * nur in ihrem Monat — "noch 5 Tage im Oktober" waere im Maerz gelogen.
 */
(function (global) {
  "use strict";

  var AXIS = {
    BEST_SCORE: "BEST_SCORE",
    PERFECT_STREAK: "PERFECT_STREAK",
    DAILY_STREAK: "DAILY_STREAK",
    RUN_COUNT: "RUN_COUNT",
    TOTAL_SCORE: "TOTAL_SCORE",
    DAYS_PLAYED: "DAYS_PLAYED",
    MONTHS_PLAYED: "MONTHS_PLAYED",
    SEASON_DAYS: "SEASON_DAYS",
    SKIN_COLLECTION: "SKIN_COLLECTION",
    SCENE_COLLECTION: "SCENE_COLLECTION"
  };

  /** Wie viele Ziele die Statistik-Seite zeigt (Progress.PAGE_GOALS). */
  var PAGE_GOALS = 3;

  /**
   * Aus wie vielen Bloecken der Fortschrittsbalken besteht. Er rastet auf
   * ganze Bloecke ein — ein weicher Balken waere der einzige stufenlose
   * Verlauf im ganzen Spiel. Dieselbe Zahl wie in :core, damit derselbe
   * Stand hier so weit gefuellt ist wie in der App.
   */
  var BAR_BLOCKS = 24;

  /** Wie viele Bloecke bei diesem Anteil leuchten. */
  function filledBlocks(fraction) {
    return Math.floor(Math.max(0, Math.min(1, fraction)) * BAR_BLOCKS);
  }

  /**
   * Die Schwellen, gespiegelt aus DotSkin.isUnlocked — in der Reihenfolge
   * der Sammlung, damit die Liste bei Gleichstand vorhersagbar bleibt.
   * Dass die Zahlen hier ein zweites Mal stehen, prueft der Testlauf
   * gegen isUnlocked ab.
   */
  var SKIN_THRESHOLDS = [
    ["MINZE", AXIS.BEST_SCORE, 10],
    ["LAVA", AXIS.BEST_SCORE, 20],
    ["GOLD", AXIS.BEST_SCORE, 30],
    ["FROST", AXIS.BEST_SCORE, 40],
    ["SCHATTEN", AXIS.PERFECT_STREAK, 4],
    ["PRISMA", AXIS.DAILY_STREAK, 3],

    ["BIENE", AXIS.PERFECT_STREAK, 6],
    ["MELONE", AXIS.BEST_SCORE, 25],
    ["PILZ", AXIS.BEST_SCORE, 35],
    ["KOI", AXIS.DAILY_STREAK, 7],
    ["GALAXIE", AXIS.BEST_SCORE, 50],
    ["KARO", AXIS.PERFECT_STREAK, 10],
    ["EI", AXIS.RUN_COUNT, 25],
    ["TIGER", AXIS.RUN_COUNT, 100],
    ["PINGUIN", AXIS.BEST_SCORE, 65],
    ["FUSSBALL", AXIS.RUN_COUNT, 300],
    ["DONUT", AXIS.TOTAL_SCORE, 1000],

    ["AURORA", AXIS.DAILY_STREAK, 14],
    ["MAGMA", AXIS.BEST_SCORE, 60],
    ["NEON", AXIS.PERFECT_STREAK, 12],
    ["CHROM", AXIS.BEST_SCORE, 45],
    ["WELLE", AXIS.BEST_SCORE, 70],
    ["GEWITTER", AXIS.PERFECT_STREAK, 15],
    ["KONFETTI", AXIS.TOTAL_SCORE, 5000],
    ["DISCO", AXIS.DAILY_STREAK, 21],
    ["HOLO", AXIS.BEST_SCORE, 80],

    ["CHAMAELEON", AXIS.BEST_SCORE, 30],
    ["KOMBO", AXIS.PERFECT_STREAK, 8],
    ["TINTE", AXIS.BEST_SCORE, 55],
    ["THERMO", AXIS.BEST_SCORE, 75],
    ["MEDAILLE", AXIS.RUN_COUNT, 200],
    ["TAGESZEIT", AXIS.DAYS_PLAYED, 7],
    ["JAHRESZEIT", AXIS.MONTHS_PLAYED, 3]
  ];

  /** Dieselbe Tabelle fuer die Kulissen (siehe DotScene.isUnlocked). */
  var SCENE_THRESHOLDS = [
    ["WUESTE", AXIS.RUN_COUNT, 500],
    ["MEER", AXIS.TOTAL_SCORE, 10000],
    ["BERG", AXIS.DAILY_STREAK, 30],
    ["STADT", AXIS.BEST_SCORE, 85]
  ];

  /**
   * Und dieselbe fuer die Ton-Sets (siehe DotSound.isUnlocked). Beide
   * Schwellen liegen auf Zahlen, die sonst nirgends vorkommen: Fiele ein
   * Ton-Set zusammen mit einem Skin oder einer Kulisse, hoerte niemand
   * das neue Set — er saehe den neuen Vogel.
   */
  var SOUND_THRESHOLDS = [
    ["GLOCKE", AXIS.PERFECT_STREAK, 20],
    ["AMBOSS", AXIS.TOTAL_SCORE, 25000]
  ];

  function value(axis, stats, seasonDays) {
    switch (axis) {
      case AXIS.BEST_SCORE: return stats.bestScore || 0;
      case AXIS.PERFECT_STREAK: return stats.bestPerfectStreak || 0;
      case AXIS.DAILY_STREAK: return stats.bestDailyStreak || 0;
      case AXIS.RUN_COUNT: return stats.runCount || 0;
      case AXIS.TOTAL_SCORE: return stats.totalScore || 0;
      case AXIS.DAYS_PLAYED: return stats.daysPlayed || 0;
      case AXIS.MONTHS_PLAYED: return stats.monthsPlayed || 0;
      case AXIS.SEASON_DAYS: return seasonDays;
      case AXIS.SKIN_COLLECTION: return global.DotSkin.unlockedCount(stats);
      case AXIS.SCENE_COLLECTION: return global.DotScene.unlockedCount(stats);
    }
    return 0;
  }

  /**
   * Ein Ziel; genau eines von skin/scene/sound ist gesetzt (Namen, keine
   * Objekte).
   */
  function goal(skin, scene, sound, axis, current, target) {
    // Ein Balken zeigt nie mehr als voll: Der Rohwert kann die Schwelle
    // nur ueberholen, wenn das Ziel laengst offen ist.
    var stand = Math.max(0, Math.min(current, target));
    return {
      skin: skin,
      scene: scene,
      sound: sound,
      axis: axis,
      current: stand,
      target: target,
      remaining: Math.max(0, target - stand),
      fraction: target <= 0 ? 1 : Math.max(0, Math.min(1, stand / target))
    };
  }

  function skinIndex(name) {
    for (var i = 0; i < global.DotSkin.SKINS.length; i++) {
      if (global.DotSkin.SKINS[i].name === name) return i;
    }
    return global.DotSkin.SKINS.length;
  }

  function sceneIndex(name) {
    for (var i = 0; i < global.DotScene.SCENES.length; i++) {
      if (global.DotScene.SCENES[i].name === name) return i;
    }
    return global.DotScene.SCENES.length;
  }

  function soundIndex(name) {
    for (var i = 0; i < global.DotSound.SETS.length; i++) {
      if (global.DotSound.SETS[i].name === name) return i;
    }
    return global.DotSound.SETS.length;
  }

  /**
   * Die Reihenfolge der Sammlungen als eine Zahl: erst die Skins, dann
   * die Kulissen, dann die Ton-Sets. Sie entscheidet nur bei Gleichstand.
   */
  function order(entry) {
    if (entry.skin) return skinIndex(entry.skin);
    if (entry.scene) return global.DotSkin.SKINS.length + sceneIndex(entry.scene);
    return global.DotSkin.SKINS.length + global.DotScene.SCENES.length +
      soundIndex(entry.sound);
  }

  /**
   * Naehe zum Ziel zuerst. Der Anteil entscheidet und nicht der Restweg,
   * weil "5 von 7 Tagen" naeher dran ist als "4.800 von 5.000 Punkten".
   * Bei Gleichstand erst der kleinere Rest, dann die Reihenfolge der
   * Sammlung — sonst springt die Liste zwischen zwei Aufrufen.
   */
  function nearestFirst(a, b) {
    if (a.fraction !== b.fraction) return b.fraction - a.fraction;
    if (a.remaining !== b.remaining) return a.remaining - b.remaining;
    return order(a) - order(b);
  }

  /**
   * Alle noch offenen Ziele, das naechstliegende zuerst. month ist der
   * Kalendermonat 1-12 (0 = kein Kalender, dann keine Saison-Ziele),
   * seasonDays der Tageszaehler des laufenden Saison-Fensters.
   */
  function goals(rawStats, month, seasonDays) {
    var stats = rawStats || {};
    var days = seasonDays || 0;
    var open = [];

    SKIN_THRESHOLDS.forEach(function (row) {
      var skin = global.DotSkin.fromName(row[0]);
      if (!global.DotSkin.isUnlocked(skin, stats)) {
        open.push(goal(row[0], null, null, row[1], value(row[1], stats, days), row[2]));
      }
    });

    // Saison: nur im eigenen Monat, und nur solange das Bit fehlt.
    var season = global.DotSkin.seasonForMonth(month || 0);
    if (season && !global.DotSkin.isUnlocked(global.DotSkin.fromName(season.skin), stats)) {
      open.push(goal(season.skin, null, null, AXIS.SEASON_DAYS, days, season.requiredDays));
    }

    // Der REGENBOGEN ist der Abschluss der Sammlung: Er zaehlt selbst
    // mit, also fehlt zum Ziel genau er — daher collectableCount - 1.
    if (!global.DotSkin.isUnlocked(global.DotSkin.fromName("REGENBOGEN"), stats)) {
      open.push(goal(
        "REGENBOGEN", null, null, AXIS.SKIN_COLLECTION,
        global.DotSkin.unlockedCount(stats), global.DotSkin.collectableCount() - 1
      ));
    }

    SCENE_THRESHOLDS.forEach(function (row) {
      var scene = global.DotScene.fromName(row[0]);
      if (!global.DotScene.isUnlocked(scene, stats)) {
        open.push(goal(null, row[0], null, row[1], value(row[1], stats, days), row[2]));
      }
    });

    // Der WELTRAUM steht zu den Kulissen wie der REGENBOGEN zu den Skins.
    if (!global.DotScene.isUnlocked(global.DotScene.fromName("WELTRAUM"), stats)) {
      open.push(goal(
        null, "WELTRAUM", null, AXIS.SCENE_COLLECTION,
        global.DotScene.unlockedCount(stats), global.DotScene.SCENES.length - 1
      ));
    }

    // Die Ton-Sets haben keinen Abschluss wie REGENBOGEN und WELTRAUM:
    // Drei Sets sind zu wenig fuer ein Sammel-Ziel.
    SOUND_THRESHOLDS.forEach(function (row) {
      var set = global.DotSound.fromName(row[0]);
      if (!global.DotSound.isUnlocked(set, stats)) {
        open.push(goal(null, null, row[0], row[1], value(row[1], stats, days), row[2]));
      }
    });

    return open.sort(nearestFirst);
  }

  var Progress = {
    AXIS: AXIS,
    PAGE_GOALS: PAGE_GOALS,
    BAR_BLOCKS: BAR_BLOCKS,
    filledBlocks: filledBlocks,
    goals: goals,

    /** Die vordersten limit Ziele — das Kurzformat fuer Seite und Game-Over. */
    nextGoals: function (stats, month, seasonDays, limit) {
      var max = limit === undefined ? PAGE_GOALS : Math.max(0, limit);
      return goals(stats, month, seasonDays).slice(0, max);
    },

    /** Das eine Ziel fuer die Zeile im Game-Over — null, wenn alles offen ist. */
    nextGoal: function (stats, month, seasonDays) {
      var list = goals(stats, month, seasonDays);
      return list.length > 0 ? list[0] : null;
    },

    /** Der Beschriftungs-Schluessel der Belohnung eines Ziels. */
    titleKey: function (entry) {
      if (entry.skin) return global.DotSkin.fromName(entry.skin).titleKey;
      if (entry.scene) return global.DotScene.fromName(entry.scene).titleKey;
      return global.DotSound.fromName(entry.sound).titleKey;
    }
  };

  global.Progress = Progress;
  if (typeof module !== "undefined" && module.exports) {
    module.exports = Progress;
  }
})(typeof globalThis !== "undefined" ? globalThis : this);

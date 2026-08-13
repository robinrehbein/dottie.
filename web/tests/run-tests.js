/*
 * Logik-Tests für die Web-Version — mit Node ausführen:
 *   node tests/run-tests.js   (aus dem web/-Ordner)
 *
 * Prüft die 1:1-Portierung von TimingGame, DailyChallenge, ChipSynth,
 * DotSkin/MedalTier und die Store-/Taunt-Logik gegen das Verhalten der
 * Kotlin-Quellen (siehe app/src/test/ im Repo).
 */
"use strict";

var TimingGame = require("../js/game.js");
var DailyChallenge = require("../js/daily.js");
var ChipSynth = require("../js/synth.js");
var skins = require("../js/skins.js");
var Strings = require("../js/strings.js");
var ScoreStore = require("../js/store.js");
var DotSkin = skins.DotSkin;
var MedalTier = skins.MedalTier;
var C = TimingGame.C;

var failures = 0;
var checks = 0;

function assert(cond, msg) {
  checks++;
  if (!cond) {
    failures++;
    console.error("FAIL: " + msg);
  }
}

function assertEq(actual, expected, msg) {
  assert(actual === expected, msg + " (expected " + expected + ", got " + actual + ")");
}

function approx(a, b, eps) { return Math.abs(a - b) <= (eps || 1e-6); }

// ===== Hilfen =====

/** Fährt den Punkt bis in die Zonenmitte und tappt (perfekter Treffer). */
function driveToZoneAndTap(game) {
  var guard = 0;
  // kleine Schritte, damit wir die Mitte präzise erwischen
  while (guard++ < 200000) {
    var before = game.relativeToZone();
    game.update(0.002);
    if (game.phase !== "RUNNING") return null;
    var rel = game.relativeToZone();
    if (rel >= -0.005 && before < rel && Math.abs(rel) <= game.effectiveZoneHalf()) {
      return game.tap();
    }
  }
  throw new Error("Zone nie erreicht");
}

// ===== TimingGame: Phasen und Start =====
(function () {
  var g = new TimingGame(new TimingGame.Rng(1n));
  assertEq(g.phase, "READY", "startet in READY");
  var ev = g.tap();
  assertEq(ev, "Started", "erster Tap startet");
  assertEq(g.phase, "RUNNING", "RUNNING nach Start");
  // Started-Event wird mit dem nächsten update ausgeliefert
  var events = g.update(0.016);
  assert(events.indexOf("Started") >= 0, "Started im Event-Puffer");
})();

// ===== Treffer, Richtungswechsel, Zone-Schrumpfen =====
(function () {
  var g = new TimingGame(new TimingGame.Rng(7n));
  g.twistOverride = [];
  g.tap();
  var dirBefore = g.direction;
  var ev = driveToZoneAndTap(g);
  assert(ev === "Hit" || ev === "PerfectHit", "Tap in der Zone trifft: " + ev);
  assertEq(g.hits, 1, "hits nach erstem Treffer");
  assertEq(g.direction, -dirBefore, "Richtung dreht nach Treffer");
  assert(approx(g.zoneHalfWidth, C.BASE_ZONE_HALF - C.ZONE_SHRINK_PER_HIT),
    "Zone schrumpft pro Treffer");
  assert(g.currentSpeed() > C.BASE_SPEED, "Tempo steigt mit Treffern");
})();

// ===== Perfekt-Serie: +2, +3, +4, +5, Deckel +5 =====
(function () {
  var g = new TimingGame(new TimingGame.Rng(11n));
  g.twistOverride = [];
  g.tap();
  var expected = [2, 3, 4, 5, 5, 5];
  var score = 0;
  for (var i = 0; i < expected.length; i++) {
    var ev = driveToZoneAndTap(g);
    assertEq(ev, "PerfectHit", "Treffer " + (i + 1) + " ist perfekt");
    assertEq(g.lastHitPoints, expected[i], "Serienbonus Stufe " + (i + 1));
    score += expected[i];
  }
  assertEq(g.score, score, "Score = Summe der Serienpunkte");
  assertEq(g.perfectStreak, expected.length, "Serie zaehlt hoch");
})();

// ===== Normaler Treffer bricht die Serie (ohne Strafe) =====
(function () {
  var g = new TimingGame(new TimingGame.Rng(3n));
  g.twistOverride = [];
  g.tap();
  driveToZoneAndTap(g); // perfekt (+2)
  // Frueh in der Zone tappen (Rand, nicht Kern):
  var guard = 0;
  while (guard++ < 200000) {
    game_step(g);
    var rel = g.relativeToZone();
    var half = g.effectiveZoneHalf();
    if (rel > -half && rel < -half * C.PERFECT_SHARE - 0.02) {
      var ev = g.tap();
      assertEq(ev, "Hit", "Randtreffer ist normal");
      break;
    }
  }
  assertEq(g.perfectStreak, 0, "Serie endet nach normalem Treffer");
  assertEq(g.lastHitPoints, 1, "normaler Treffer +1");
  function game_step(gg) { gg.update(0.002); }
})();

// ===== Tap daneben toetet, DYING -> OVER, Restart-Lock =====
(function () {
  var g = new TimingGame(new TimingGame.Rng(5n));
  g.twistOverride = [];
  g.tap();
  g.update(0.001); // Punkt weit weg von der Zone (Mindestabstand 1.1 rad)
  var ev = g.tap();
  assertEq(ev, "Died", "Tap ausserhalb toetet");
  assertEq(g.phase, "DYING", "DYING nach Tod");
  assertEq(g.tap(), null, "Tap in DYING wird ignoriert");

  // Freeze + Fall abwarten
  var settled = false;
  for (var i = 0; i < 200; i++) {
    var evs = g.update(1 / 60);
    if (evs.indexOf("Settled") >= 0) settled = true;
  }
  assert(settled, "Settled nach Freeze+Fall");
  assertEq(g.phase, "OVER", "OVER nach Settled");

  // Restart-Lock: direkt nach OVER kein Neustart
  g.elapsed = 0;
  assertEq(g.tap(), null, "Restart-Lock blockt Wut-Taps");
  g.elapsed = C.RESTART_LOCK_SECONDS;
  assertEq(g.tap(), "Started", "Neustart nach Lock");
  assertEq(g.score, 0, "Score nach Neustart 0");
})();

// ===== Ueberfahren ohne Tap toetet =====
(function () {
  var g = new TimingGame(new TimingGame.Rng(9n));
  g.twistOverride = [];
  g.tap();
  var died = false;
  for (var i = 0; i < 5000 && !died; i++) {
    var evs = g.update(1 / 120);
    if (evs.indexOf("Died") >= 0) died = true;
  }
  assert(died, "Zone ueberfahren -> Tod");
})();

// ===== Twist-Freischaltung bei Score 5/10/15/20/25 =====
(function () {
  assertEq(TimingGame.unlockScore("PULSE"), 5, "PULSE ab 5");
  assertEq(TimingGame.unlockScore("DRIFT"), 10, "DRIFT ab 10");
  assertEq(TimingGame.unlockScore("GHOST"), 15, "GHOST ab 15");
  assertEq(TimingGame.unlockScore("FAKE"), 20, "FAKE ab 20");
  assertEq(TimingGame.unlockScore("CHAIN"), 25, "CHAIN ab 25");

  // Langer Lauf: alle Twists werden genau einmal angekuendigt.
  var g = new TimingGame(new TimingGame.Rng(1234n));
  g.tap();
  var announced = [];
  var guard = 0;
  while (g.score < 60 && guard++ < 500) {
    driveToZoneAndTap(g);
    if (g.phase !== "RUNNING") break;
    var evs = g.update(0.001);
    evs.forEach(function (e) {
      if (e && e.type === "TwistUnlocked") announced.push(e.twist);
    });
  }
  assertEq(announced.length, 5, "5 Twist-Ankuendigungen");
  ["PULSE", "DRIFT", "GHOST", "FAKE", "CHAIN"].forEach(function (tw, i) {
    assertEq(announced[i], tw, "Ankuendigung " + i + " ist " + tw);
  });
  assert(g.activeTwists.size <= C.MAX_ACTIVE_TWISTS, "max. 2 Twists aktiv");
})();

// ===== GEIST + FALLE nie zusammen =====
(function () {
  for (var seed = 0; seed < 40; seed++) {
    var g = new TimingGame(new TimingGame.Rng(BigInt(seed)));
    g.tap();
    var guard = 0;
    while (g.score < 60 && guard++ < 500) {
      driveToZoneAndTap(g);
      if (g.phase !== "RUNNING") break;
      g.update(0.001);
      assert(!(g.activeTwists.has("GHOST") && g.activeTwists.has("FAKE")),
        "GHOST+FAKE verboten (Seed " + seed + ")");
    }
  }
})();

// ===== PULSE: effektive Breite pulsiert im erlaubten Band =====
(function () {
  var g = new TimingGame(new TimingGame.Rng(2n));
  g.twistOverride = ["PULSE"];
  g.tap();
  var min = Infinity, max = -Infinity;
  for (var i = 0; i < 400; i++) {
    g.update(0.005);
    if (g.phase !== "RUNNING") break;
    var eff = g.effectiveZoneHalf();
    min = Math.min(min, eff);
    max = Math.max(max, eff);
  }
  assert(min >= g.zoneHalfWidth * C.PULSE_MIN_SHARE - 1e-6, "Puls-Minimum");
  assert(max <= g.zoneHalfWidth + 1e-6, "Puls-Maximum");
  assert(max - min > 0.01, "Puls bewegt sich");
})();

// ===== GHOST: Punkt blinkt nur in RUNNING =====
(function () {
  var g = new TimingGame(new TimingGame.Rng(2n));
  g.twistOverride = ["GHOST"];
  assert(g.isDotVisible(), "in READY immer sichtbar");
  g.tap();
  var seenHidden = false, seenVisible = false;
  for (var i = 0; i < 300; i++) {
    g.update(0.004);
    if (g.phase !== "RUNNING") break;
    if (g.isDotVisible()) seenVisible = true; else seenHidden = true;
  }
  assert(seenHidden && seenVisible, "GHOST blinkt (sichtbar und unsichtbar)");
})();

// ===== CHAIN: Folge-Zone ohne Richtungswechsel =====
(function () {
  var g = new TimingGame(new TimingGame.Rng(4n));
  g.twistOverride = ["CHAIN"];
  g.tap();
  var dir = g.direction;
  driveToZoneAndTap(g);
  var evs = g.update(0.001);
  assert(evs.indexOf("ChainNext") >= 0, "ChainNext nach erstem Ketten-Treffer");
  assertEq(g.direction, dir, "Richtung bleibt in der Kette");
  assertEq(g.chainRemaining, 0, "eine Folge-Zone (CHAIN_LENGTH=1)");
  driveToZoneAndTap(g);
  assertEq(g.direction, -dir, "Richtung dreht nach Ketten-Ende");
})();

// ===== Determinismus: gleicher Seed -> gleiche Abfolge =====
(function () {
  function record(seed) {
    var g = new TimingGame(new TimingGame.Rng(seed));
    g.tap();
    var zones = [];
    for (var i = 0; i < 8; i++) {
      driveToZoneAndTap(g);
      if (g.phase !== "RUNNING") break;
      g.update(0.001);
      zones.push(g.zoneCenter.toFixed(9) + ":" +
        Array.from(g.activeTwists).sort().join(","));
    }
    return zones.join("|");
  }
  var seed = DailyChallenge.seedFor(20370);
  assertEq(record(seed), record(seed), "Daily-Seed deterministisch");
  assert(record(seed) !== record(DailyChallenge.seedFor(20371)),
    "verschiedene Tage -> verschiedene Abfolgen");
})();

// ===== Spaeter Tap: Gnadenfenster =====
(function () {
  var g = new TimingGame(new TimingGame.Rng(6n));
  g.twistOverride = [];
  g.tap();
  // bis knapp HINTER die Zone fahren (im Gnadenfenster)
  var guard = 0;
  while (guard++ < 400000) {
    g.update(0.001);
    if (g.phase !== "RUNNING") break;
    var rel = g.relativeToZone();
    var half = g.effectiveZoneHalf();
    if (rel > half + 0.001 &&
        rel <= half + g.currentSpeed() * C.LATE_TAP_FORGIVENESS_SECONDS - 0.005) {
      var ev = g.tap();
      assertEq(ev, "Hit", "spaeter Tap im Gnadenfenster zaehlt als Hit");
      assertEq(g.lastHitPerfect, false, "Gnade ist nie perfekt");
      break;
    }
  }
  assert(g.hits === 1, "Gnadenfenster-Treffer registriert");
})();

// ===== DailyChallenge =====
(function () {
  assert(DailyChallenge.seedFor(1) !== DailyChallenge.seedFor(2), "Seeds streuen");
  assertEq(typeof DailyChallenge.seedFor(20000), "bigint", "Seed ist 64-Bit (BigInt)");

  assertEq(DailyChallenge.nextStreak(0, 0, 100), 1, "nie gespielt -> 1");
  assertEq(DailyChallenge.nextStreak(-5, 3, 100), 1, "negativ -> 1");
  assertEq(DailyChallenge.nextStreak(100, 4, 100), 4, "gleicher Tag -> unveraendert");
  assertEq(DailyChallenge.nextStreak(100, 0, 100), 1, "gleicher Tag, min. 1");
  assertEq(DailyChallenge.nextStreak(100, 4, 101), 5, "Folgetag -> +1");
  assertEq(DailyChallenge.nextStreak(100, 4, 103), 1, "Luecke -> zurueck auf 1");
})();

// ===== ChipSynth =====
(function () {
  var fx = ChipSynth.effects();
  var names = ["start", "hit", "perfect", "chain", "unlock", "record", "death", "thud"];
  names.forEach(function (n) {
    var s = fx[n];
    assert(s && s.length > 0, "Effekt " + n + " vorhanden");
    var ok = true;
    for (var i = 0; i < s.length; i++) {
      if (!(s[i] >= -1 && s[i] <= 1)) { ok = false; break; }
    }
    assert(ok, "Effekt " + n + " in [-1, 1]");
  });
  assertEq(fx.hit.length, Math.floor(0.07 * ChipSynth.SAMPLE_RATE), "hit-Laenge");
  assertEq(fx.perfect.length,
    Math.floor(0.06 * 22050) + Math.floor(0.16 * 22050), "perfect = concat");
  assertEq(fx.death.length,
    Math.max(Math.floor(0.35 * 22050), Math.floor(0.12 * 22050)), "death = mix");

  assert(approx(ChipSynth.hitRate(0), 1), "hitRate(0)=1");
  assert(approx(ChipSynth.hitRate(2), Math.pow(2, 4 / 12)), "hitRate Pentatonik");
  assert(approx(ChipSynth.hitRate(5), 1), "hitRate wickelt pro 5er-Stufe");
  assert(approx(ChipSynth.perfectRate(1), 1), "perfectRate(1)=1");
  assert(approx(ChipSynth.perfectRate(3), Math.pow(2, 4 / 12)), "perfectRate +2HT/Stufe");
  assert(approx(ChipSynth.perfectRate(99), Math.pow(2, 8 / 12)), "perfectRate Deckel");

  // Deterministisch: zweimal rendern ergibt exakt dieselben Samples.
  var a = ChipSynth.effects().death;
  var b = ChipSynth.effects().death;
  var same = a.length === b.length;
  for (var i = 0; same && i < a.length; i++) same = a[i] === b[i];
  assert(same, "Synthese deterministisch (Noise-Seed 42)");
})();

// ===== DotSkin / MedalTier =====
(function () {
  var none = { bestScore: 0, bestPerfectStreak: 0, bestDailyStreak: 0 };
  assertEq(DotSkin.unlockedCount(none), 1, "nur KLASSIK am Anfang");
  assertEq(DotSkin.unlockedCount({ bestScore: 10, bestPerfectStreak: 0, bestDailyStreak: 0 }), 2, "MINZE ab Rekord 10");
  // Rekord 40 oeffnet MINZE(10), LAVA(20), MELONE(25), GOLD/CHAMAELEON(30),
  // PILZ(35) und FROST(40) — mit KLASSIK sind das acht.
  assertEq(DotSkin.unlockedCount({ bestScore: 40, bestPerfectStreak: 0, bestDailyStreak: 0 }), 8, "Rekord-Skins bei 40");
  assertEq(DotSkin.unlockedCount({ bestScore: 0, bestPerfectStreak: 4, bestDailyStreak: 0 }), 2, "SCHATTEN ab Serie 4");
  assertEq(DotSkin.unlockedCount({ bestScore: 0, bestPerfectStreak: 0, bestDailyStreak: 3 }), 2, "PRISMA ab Daily-Serie 3");
  assertEq(DotSkin.unlockedCount({ bestScore: 40, bestPerfectStreak: 4, bestDailyStreak: 3 }), 10, "Bestand plus Muster");
  assertEq(
    DotSkin.unlockedCount({ bestScore: 60, bestPerfectStreak: 12, bestDailyStreak: 14 }),
    DotSkin.SKINS.length,
    "alle " + DotSkin.SKINS.length + " Skins"
  );

  // Der Regenbogen kommt erst, wenn alles andere offen ist.
  var fastAlles = { bestScore: 999, bestPerfectStreak: 99, bestDailyStreak: 13 };
  assert(!DotSkin.isUnlocked(DotSkin.fromName("REGENBOGEN"), fastAlles), "Regenbogen wartet auf Aurora");
  assert(
    DotSkin.isUnlocked(DotSkin.fromName("REGENBOGEN"), { bestScore: 60, bestPerfectStreak: 12, bestDailyStreak: 14 }),
    "Regenbogen schliesst die Sammlung ab"
  );

  // Jedes Feld jedes Skins liefert eine gueltige Farbe — auch bewegte und
  // reagierende, in jedem Zustand.
  var zustaende = [
    undefined,
    { elapsed: 0.4, score: 0, perfectStreak: 0 },
    { elapsed: 2.7, score: 33, perfectStreak: 4 },
    { elapsed: 9.9, score: 99, perfectStreak: 12 }
  ];
  var felder = 0;
  DotSkin.SKINS.forEach(function (s) {
    zustaende.forEach(function (st) {
      for (var row = 0; row < DotSkin.GRID; row++) {
        for (var col = 0; col < DotSkin.GRID; col++) {
          if (!/^#[0-9A-Fa-f]{6}$/.test(DotSkin.cell(s, col, row, st))) {
            assert(false, "Skin " + s.name + " liefert bei (" + col + "," + row + ") keine Farbe");
            return;
          }
          felder++;
        }
      }
      if (!/^#[0-9A-Fa-f]{6}$/.test(DotSkin.shine(s, st))) {
        assert(false, "Skin " + s.name + " hat keinen Glanzpunkt");
      }
    });
  });
  assertEq(felder, DotSkin.SKINS.length * zustaende.length * DotSkin.GRID * DotSkin.GRID,
    "alle Felder aller Skins geprueft");

  // Standbilder duerfen sich ohne Zeitanteil nicht bewegen.
  DotSkin.SKINS.forEach(function (s) {
    if (s.animated) return;
    assertEq(
      DotSkin.cell(s, 4, 4, { elapsed: 0 }),
      DotSkin.cell(s, 4, 4, { elapsed: 5.5 }),
      "Skin " + s.name + " steht still"
    );
  });
  assertEq(DotSkin.fromName("LAVA").name, "LAVA", "fromName findet");
  assertEq(DotSkin.fromName("quatsch").name, "KLASSIK", "fromName-Fallback");

  assertEq(MedalTier.forScore(9), null, "keine Medaille unter 10");
  assertEq(MedalTier.forScore(10).name, "BRONZE", "Bronze ab 10");
  assertEq(MedalTier.forScore(39).name, "GOLD", "Gold bei 39");
  assertEq(MedalTier.forScore(40).name, "PLATINUM", "Platin ab 40");
  assertEq(MedalTier.next(0).name, "BRONZE", "naechste Stufe Bronze");
  assertEq(MedalTier.next(40), null, "keine naechste ab Platin");
  assert(MedalTier.isUpgrade(10, 9), "10 nach 9 ist Upgrade");
  assert(!MedalTier.isUpgrade(15, 12), "15 nach 12 kein Upgrade");
  assert(MedalTier.isUpgrade(20, 15), "20 nach 15 ist Upgrade");
})();

// ===== Strings / Taunts =====
(function () {
  Strings.setLang("de");
  assertEq(Strings.t("best_score", 12), "REKORD: 12", "Platzhalter DE");
  assertEq(Strings.streakLabel(1), "SERIE: 1 TAG", "Streak Singular");
  assertEq(Strings.streakLabel(3), "SERIE: 3 TAGE", "Streak Plural");
  assertEq(Strings.pickTaunt(5, 5, true), "NEUER REKORD!", "Rekord-Text");
  assert(Strings.pickTaunt(0, 10, false).length > 0, "Zero-Taunt vorhanden");
  var close = Strings.pickTaunt(8, 10, false);
  assert(close.indexOf("2") >= 0, "Close-Taunt traegt die Luecke: " + close);
  Strings.setLang("en");
  assertEq(Strings.t("best_score", 12), "BEST: 12", "Platzhalter EN");
})();

// ===== ScoreStore (Memory-Backend in Node) =====
(function () {
  assertEq(ScoreStore.bestScore, 0, "Store startet leer");
  assertEq(ScoreStore.submitRun(5), true, "erster Lauf ist Rekord");
  assertEq(ScoreStore.submitRun(3), false, "kleinerer Lauf kein Rekord");
  assertEq(ScoreStore.bestScore, 5, "Rekord bleibt 5");
  assertEq(ScoreStore.runCount, 2, "zwei Laeufe gezaehlt");

  ScoreStore.submitPerfectStreak(3);
  ScoreStore.submitPerfectStreak(2);
  assertEq(ScoreStore.bestPerfectStreak, 3, "beste Perfekt-Serie");

  ScoreStore.submitDailyRun(100, 7);
  assertEq(ScoreStore.dailyBestFor(100), 7, "Daily-Tagesbest");
  assertEq(ScoreStore.dailyBestFor(101), 0, "anderer Tag -> 0");
  assertEq(ScoreStore.dailyStreak, 1, "Streak 1 nach erstem Tag");
  ScoreStore.submitDailyRun(100, 4);
  assertEq(ScoreStore.dailyBestFor(100), 7, "kleinerer zweiter Lauf aendert nichts");
  ScoreStore.submitDailyRun(101, 2);
  assertEq(ScoreStore.dailyStreak, 2, "Folgetag -> Streak 2");
  assertEq(ScoreStore.dailyBestFor(101), 2, "neuer Tag, neuer Tagesbest");
  assertEq(ScoreStore.dailyStreakPreviewFor(102), 2, "Preview am Folgetag");
  assertEq(ScoreStore.dailyStreakPreviewFor(105), 0, "Preview nach Luecke");
})();

// ===== Pixel-Rahmen der Knöpfe (PixelButton.kt) =====
(function () {
  var PixelButton = require("../js/pixelbutton.js");

  /**
   * Referenz: die Treppenstufen einzeln wie drawLeft/RightSteppedBorder in
   * Kotlin. Unsere zusammengefassten Rechtecke müssen exakt dieselbe
   * Fläche abdecken.
   */
  function kotlinRects(w, h, b, overlap) {
    var out = [
      { x: 0, y: 0, w: w, h: b },
      { x: 0, y: h - b, w: w, h: b }
    ];
    var steps = Math.trunc((h - 2 * b) / b);
    var stepH = (h - 2 * b) / steps;
    for (var i = 0; i < steps; i++) {
      var y = b + i * stepH;
      var sw = (i < Math.trunc(steps / 4)) ? b * 2
        : (i < Math.trunc((steps * 3) / 4)) ? b : b * 2;
      out.push({ x: 0, y: y, w: sw, h: stepH + overlap });
      out.push({ x: w - sw, y: y, w: sw, h: stepH + overlap });
    }
    return out;
  }

  function covered(rects, x, y) {
    for (var i = 0; i < rects.length; i++) {
      var r = rects[i];
      if (x >= r.x && x < r.x + r.w && y >= r.y && y < r.y + r.h) return true;
    }
    return false;
  }

  [[116, 48, 3], [116, 52, 3], [48, 48, 3], [244, 48, 3], [200, 60, 4]]
    .forEach(function (dim) {
      var w = dim[0], h = dim[1], b = dim[2];
      var mine = PixelButton.steppedBorderRects(w, h, b, 1);
      var ref = kotlinRects(w, h, b, 1);
      var same = true;
      for (var y = 0; y < h && same; y += 0.5) {
        for (var x = 0; x < w && same; x += 0.5) {
          if (covered(mine, x, y) !== covered(ref, x, y)) same = false;
        }
      }
      assert(same, "Treppen-Rahmen deckt sich mit Kotlin (" + w + "x" + h + ")");
    });

  // 48x48 mit 3 dp Rahmen: steps = 14, also 3 breite Stufen oben (y 3..12),
  // schmale Mitte und 4 breite unten (y 33..45).
  var r48 = PixelButton.steppedBorderRects(48, 48, 3, 0);
  assertEq(r48.length, 8, "oben, unten und drei Kanten-Abschnitte je Seite");
  assertEq(r48[2].w, 6, "oberes Viertel ist doppelt so breit");
  assertEq(r48[2].y, 3, "Kante beginnt unter dem oberen Balken");
  assertEq(r48[2].h, 9, "drei Stufen breit oben");
  assertEq(r48[4].w, 3, "Mitte hat die einfache Rahmenbreite");
  assertEq(r48[6].w, 6, "unteres Viertel wieder doppelt");
  assertEq(r48[6].y, 33, "unterer Abschnitt startet bei 33");
  assertEq(r48[3].x, 42, "rechte Kante sitzt bündig am Rand");

  var css = PixelButton.borderCss(116, 48, 3, 1, "#543847");
  assertEq(css.image.split("linear-gradient").length - 1, 8, "acht Rahmen-Layer");
  assert(css.size.indexOf("100% 3px") === 0, "oberer Balken über die volle Breite");
  // Regression: Die rechte Kante muss über "100%" bündig ausgerichtet
  // werden. Ein "calc(100% - Breite)" sieht richtig aus, ist es aber
  // nicht — background-position mischt Prozent und Länge als
  // (Knopf - Bild) * 100% + Länge und zieht die Kante dadurch um ihre
  // eigene Breite nach innen; rechts blitzt dann die Füllfarbe durch.
  assert(css.position.indexOf("calc(") < 0, "keine calc-Position im Rahmen");
  var xs = css.position.split(",").map(function (p) { return p.trim().split(" ")[0]; });
  assertEq(xs.filter(function (x) { return x === "100%"; }).length, 3,
    "drei rechte Kanten-Abschnitte sitzen bündig rechts");
  assertEq(xs.filter(function (x) { return x === "0px"; }).length, 5,
    "oben, unten und drei linke Abschnitte sitzen links");

  var svg = PixelButton.speakerSvg(true, "#543847", "#E53935");
  assertEq(svg.split("#E53935").length - 1, 6, "sechs Blöcke Durchstreichung bei TON: AUS");
  assert(PixelButton.speakerSvg(false, "#543847", "#E53935").indexOf("#E53935") < 0,
    "keine Durchstreichung bei TON: AN");
  assert(svg.indexOf('viewBox="0 0 16 16"') > 0, "Motiv auf dem 16er-Raster");
})();

// ===== Farb- und Text-Parität mit den Android-Quellen =====
(function () {
  var fs = require("fs");
  var path = require("path");
  var Renderer = require("../js/render.js");
  var app = path.join(__dirname, "..", "..", "app", "src", "main");

  function read(rel) {
    try {
      return fs.readFileSync(path.join(app, rel), "utf8");
    } catch (e) {
      return null;
    }
  }

  var overlays = read("java/de/robinrehbein/punkt/ui/screens/GameOverlays.kt");
  var screen = read("java/de/robinrehbein/punkt/ui/screens/TimingGameScreen.kt");
  var skinsKt = read("java/de/robinrehbein/punkt/game/DotSkin.kt");
  var paintKt = (function () {
    try {
      return fs.readFileSync(
        path.join(__dirname, "..", "..", "core", "src", "main", "kotlin",
          "de", "robinrehbein", "punkt", "game", "SkinPaint.kt"),
        "utf8"
      );
    } catch (e) {
      return null;
    }
  })();
  var stringsEn = read("res/values/strings.xml");
  var stringsDe = read("res/values-de/strings.xml");

  if (!overlays || !screen || !skinsKt || !paintKt || !stringsEn || !stringsDe) {
    // Die Tests laufen auch außerhalb des Repos (nur web/ ausgeliefert).
    console.log("Hinweis: app/-Quellen nicht gefunden — Paritätstests übersprungen.");
    return;
  }

  function hexes(source, re) {
    var found = {}, m;
    while ((m = re.exec(source)) !== null) found[m[1]] = "#" + m[2].toUpperCase();
    return found;
  }

  var P = Renderer.Palette;
  var kotlinColors = hexes(
    overlays, /internal val (\w+) = Color\(0x[Ff]{2}([0-9A-Fa-f]{6})\)/g
  );
  Object.keys(kotlinColors).forEach(function (name) {
    if (P[name] === undefined) return; // nicht jede Farbe braucht die PWA
    assertEq(P[name], kotlinColors[name], "Palette " + name);
  });
  assert(Object.keys(kotlinColors).length > 15, "Palette aus GameOverlays.kt gelesen");

  var zoneColors = hexes(
    screen, /private val (FakeZone\w*) = Color\(0x[Ff]{2}([0-9A-Fa-f]{6})\)/g
  );
  assertEq(P.FakeZoneColor, zoneColors.FakeZoneColor, "Köder-Zone");
  assertEq(P.FakeZoneCoreColor, zoneColors.FakeZoneCoreColor, "Köder-Zone (Kern)");

  var skyBlock = screen.slice(screen.indexOf("SkyStages = listOf"));
  skyBlock = skyBlock.slice(0, skyBlock.indexOf("\n)"));
  var sky = (skyBlock.match(/0x[Ff]{2}[0-9A-Fa-f]{6}/g) || [])
    .map(function (h) { return "#" + h.slice(4).toUpperCase(); });
  assertEq(sky.length, 7, "sieben Himmels-Stufen in Kotlin");
  sky.forEach(function (hex, i) {
    assertEq(P.SkyStages[i], hex, "Himmel Stufe " + i);
  });

  // Skins: Reihenfolge, Kennungen, Stellvertreter-Farben und Schwellen
  // kommen aus SkinPaint.kt (:core) — die PWA muss sie 1:1 spiegeln, sonst
  // heisst derselbe gespeicherte Name auf beiden Seiten etwas anderes.
  // Die Enum-Namen in :app muessen zu SkinId passen — die Auswahl wird
  // unter diesem Namen gespeichert.
  var appIds = (skinsKt.match(/^\s{4}(\w+)\(SkinId\.\w+,/gm) || [])
    .map(function (zeile) { return zeile.trim().split("(")[0]; });

  var kotlinIds = (function () {
    var block = paintKt.slice(paintKt.indexOf("enum class SkinId {"));
    block = block.slice(0, block.indexOf("}"));
    return (block.match(/\b[A-Z][A-Z_]+\b/g) || []).filter(function (n) {
      return n !== "SKIN" && n !== "ID";
    });
  })();
  assertEq(
    DotSkin.SKINS.map(function (s) { return s.name; }).join(","),
    kotlinIds.join(","),
    "Skin-Reihenfolge deckt sich mit SkinId"
  );
  assertEq(appIds.join(","), kotlinIds.join(","), "DotSkin.kt fuehrt dieselben Skins in derselben Reihenfolge");

  /** Liest einen when-Block aus SkinPaint.kt als { SKIN: "wert" }. */
  function whenBlock(head) {
    var start = paintKt.indexOf(head);
    assert(start >= 0, "Block '" + head + "' in SkinPaint.kt gefunden");
    var block = paintKt.slice(start + head.length);
    block = block.slice(0, block.indexOf("\n    }"));
    var out = {}, wm;
    var re = /SkinId\.(\w+) -> ([^\n]+)/g;
    while ((wm = re.exec(block)) !== null) out[wm[1]] = wm[2].trim();
    return out;
  }

  ["body", "shade", "shine"].forEach(function (rolle) {
    var head = rolle === "shine"
      ? "fun shine(id: SkinId, state: SkinState = SkinState()): Long = when (id) {"
      : "fun " + rolle + "(id: SkinId): Long = when (id) {";
    var werte = whenBlock(head);
    assertEq(Object.keys(werte).length, kotlinIds.length, "SkinPaint." + rolle + " deckt alle Skins ab");
    Object.keys(werte).forEach(function (id) {
      var hex = werte[id].match(/0x[Ff]{2}([0-9A-Fa-f]{6})/);
      if (!hex) return; // NEON holt seinen Glanz aus einer Funktion
      assertEq(DotSkin.fromName(id)[rolle], "#" + hex[1].toUpperCase(), "Skin " + id + " " + rolle);
    });
  });

  var kotlinSky = (paintKt.slice(paintKt.indexOf("val SKY_STAGES = longArrayOf("))
    .match(/0x[Ff]{2}[0-9A-Fa-f]{6}/g) || []).slice(0, 7)
    .map(function (h) { return "#" + h.slice(4).toUpperCase(); });
  assertEq(kotlinSky.join(","), DotSkin.SKY_STAGES.join(","), "Chamaeleon nutzt dieselben Himmelsstufen");

  // Freischalt-Schwellen: jede Regel aus Kotlin wird an ihrer Kante geprueft.
  var regeln = whenBlock("fun isUnlocked(id: SkinId, stats: SkinStats): Boolean = when (id) {");
  var felder2 = {
    "stats.bestScore": "bestScore",
    "stats.bestPerfectStreak": "bestPerfectStreak",
    "stats.bestDailyStreak": "bestDailyStreak"
  };
  var geprueft = 0;
  Object.keys(regeln).forEach(function (id) {
    var rm = regeln[id].match(/(stats\.\w+) >= (\d+)/);
    if (!rm) return; // KLASSIK (immer offen) und REGENBOGEN (Sammlung)
    var feld = felder2[rm[1]];
    var schwelle = parseInt(rm[2], 10);
    var skin = DotSkin.fromName(id);
    function stats(wert) {
      var st = { bestScore: 0, bestPerfectStreak: 0, bestDailyStreak: 0 };
      st[feld] = wert;
      return st;
    }
    assert(!DotSkin.isUnlocked(skin, stats(schwelle - 1)), "Skin " + id + " bleibt unter " + schwelle + " zu");
    assert(DotSkin.isUnlocked(skin, stats(schwelle)), "Skin " + id + " oeffnet bei " + schwelle);
    geprueft++;
  });
  assertEq(geprueft, kotlinIds.length - 2, "alle Schwellen ausser Klassik und Regenbogen geprueft");

  var m;

  var medalRe = /MedalTier\.(\w+) -> Color\(0x[Ff]{2}([0-9A-Fa-f]{6})\) to Color\(0x[Ff]{2}([0-9A-Fa-f]{6})\)/g;
  var medals = 0;
  while ((m = medalRe.exec(overlays)) !== null) {
    var tier = MedalTier.MEDALS.filter(function (t) { return t.name === m[1]; })[0];
    assert(!!tier, "Medaille " + m[1] + " existiert im Web");
    assertEq(tier.body, "#" + m[2].toUpperCase(), "Medaille " + m[1] + " body");
    assertEq(tier.shade, "#" + m[3].toUpperCase(), "Medaille " + m[1] + " shade");
    medals++;
  }
  assertEq(medals, 4, "vier Medaillen-Stufen geprüft");

  // Texte: jeder Key, den die PWA kennt, muss wortgleich in strings.xml stehen.
  function xmlStrings(xml) {
    var out = {}, sm;
    var re = /<string name="([\w_]+)"[^>]*>([\s\S]*?)<\/string>/g;
    while ((sm = re.exec(xml)) !== null) {
      out[sm[1]] = sm[2].replace(/\\'/g, "'").replace(/&amp;/g, "&");
    }
    var are = /<string-array name="([\w_]+)">([\s\S]*?)<\/string-array>/g;
    while ((sm = are.exec(xml)) !== null) {
      out[sm[1]] = (sm[2].match(/<item>([\s\S]*?)<\/item>/g) || [])
        .map(function (it) {
          return it.replace(/<\/?item>/g, "").replace(/\\'/g, "'");
        });
    }
    return out;
  }

  [["en", stringsEn], ["de", stringsDe]].forEach(function (pair) {
    var lang = pair[0];
    var xml = xmlStrings(pair[1]);
    var web = Strings.STRINGS[lang];
    var compared = 0;
    Object.keys(web).forEach(function (key) {
      if (xml[key] === undefined) return; // app_name steht nur in values/
      if (Array.isArray(web[key])) {
        assertEq(web[key].join("|"), xml[key].join("|"), lang + ": " + key);
      } else {
        assertEq(web[key], xml[key], lang + ": " + key);
      }
      compared++;
    });
    assert(compared > 50, lang + ": genug Texte verglichen (" + compared + ")");
  });
})();

console.log(checks + " Checks, " + failures + " Fehler");
process.exit(failures === 0 ? 0 : 1);

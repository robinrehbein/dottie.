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
  /** Ein Stand, der alles verdient hat, was sich verdienen laesst. */
  var alles = {
    bestScore: 80, bestPerfectStreak: 15, bestDailyStreak: 21,
    runCount: 300, totalScore: 5000, daysPlayed: 7, monthsPlayed: 3
  };

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
    DotSkin.unlockedCount(alles),
    DotSkin.collectableCount(),
    "alle " + DotSkin.collectableCount() + " sammelbaren Skins"
  );
  assertEq(DotSkin.collectableCount(), DotSkin.SKINS.length - 7,
    "Saison (4) und Goenner (3) zaehlen nicht fuer die Sammlung");

  // Gekauft ist nicht verdient: Das Goenner-Paket oeffnet drei Skins,
  // bewegt den Sammlungsstand aber um keinen Zaehler.
  var mitPaket = Object.assign({}, alles, { patronOwned: true });
  assertEq(DotSkin.unlockedCount(mitPaket), DotSkin.collectableCount(),
    "Goenner-Skins zaehlen nicht im Sammlungsstand");
  ["DIAMANT", "PHOENIX", "ONYX"].forEach(function (name) {
    assert(!DotSkin.isUnlocked(DotSkin.fromName(name), alles), name + " bleibt ohne Paket zu");
    assert(DotSkin.isUnlocked(DotSkin.fromName(name), mitPaket), name + " oeffnet mit Paket");
  });

  // Saison: nur die Maske oeffnet, und sie oeffnet genau ihren Skin.
  DotSkin.SEASONS.forEach(function (season) {
    var skin = DotSkin.fromName(season.skin);
    assert(!DotSkin.isUnlocked(skin, alles), season.skin + " bleibt ohne Bit zu");
    assert(DotSkin.isUnlocked(skin, { seasonEarned: season.bit }), season.skin + " oeffnet mit seinem Bit");
    DotSkin.SEASONS.forEach(function (other) {
      if (other === season) return;
      assert(!DotSkin.isUnlocked(DotSkin.fromName(other.skin), { seasonEarned: season.bit }),
        season.skin + "-Bit oeffnet nicht " + other.skin);
    });
  });
  assertEq(DotSkin.unlockedCount({ seasonEarned: 15 }), 1,
    "alle Saison-Bits aendern den Sammlungsstand nicht");

  // Die Ausdauer-Achsen zaehlen unabhaengig vom Rekord.
  assert(DotSkin.isUnlocked(DotSkin.fromName("EI"), { runCount: 25 }), "EI ab 25 Laeufen");
  assert(!DotSkin.isUnlocked(DotSkin.fromName("EI"), { runCount: 24 }), "EI nicht bei 24 Laeufen");
  assert(DotSkin.isUnlocked(DotSkin.fromName("DONUT"), { totalScore: 1000 }), "DONUT ab 1000 Punkten");
  assert(!DotSkin.isUnlocked(DotSkin.fromName("DONUT"), { totalScore: 999 }), "DONUT nicht bei 999");
  assert(DotSkin.isUnlocked(DotSkin.fromName("TAGESZEIT"), { daysPlayed: 7 }), "TAGESZEIT ab 7 Tagen");
  assert(DotSkin.isUnlocked(DotSkin.fromName("JAHRESZEIT"), { monthsPlayed: 3 }), "JAHRESZEIT ab 3 Monaten");

  // Der Regenbogen kommt erst, wenn alles andere offen ist.
  var fastAlles = { bestScore: 999, bestPerfectStreak: 99, bestDailyStreak: 13 };
  assert(!DotSkin.isUnlocked(DotSkin.fromName("REGENBOGEN"), fastAlles), "Regenbogen wartet auf Aurora");
  assert(
    !DotSkin.isUnlocked(DotSkin.fromName("REGENBOGEN"),
      { bestScore: 999, bestPerfectStreak: 99, bestDailyStreak: 99 }),
    "Regenbogen wartet auch auf die Ausdauer-Skins"
  );
  assert(DotSkin.isUnlocked(DotSkin.fromName("REGENBOGEN"), alles),
    "Regenbogen schliesst die Sammlung ab");
  assert(DotSkin.isUnlocked(DotSkin.fromName("REGENBOGEN"),
    Object.assign({}, alles, { seasonEarned: 0, patronOwned: false })),
    "Regenbogen haengt nicht an Saison und Goennern");

  // Jedes Feld jedes Skins liefert eine gueltige Farbe — auch bewegte und
  // reagierende, in jedem Zustand (inklusive aller Stunden und Monate).
  var zustaende = [
    undefined,
    { elapsed: 0.4, score: 0, perfectStreak: 0 },
    { elapsed: 2.7, score: 33, perfectStreak: 4 },
    { elapsed: 9.9, score: 99, perfectStreak: 12 },
    { elapsed: 1.3, score: 12, perfectStreak: 2, hour: 0, month: 1 },
    { elapsed: 4.6, score: 45, perfectStreak: 6, hour: 7, month: 4 },
    { elapsed: 7.2, score: 21, perfectStreak: 1, hour: 18, month: 10 },
    { elapsed: 20.5, score: 80, perfectStreak: 9, hour: 23, month: 12 }
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

  // Die Augen-Kontur haengt an der Helligkeit des Koerpers und muss in
  // beiden Ports dieselben Skins treffen.
  assertEq(
    DotSkin.SKINS.filter(function (s) { return DotSkin.needsEyeOutline(s); })
      .map(function (s) { return s.name; }).join(","),
    "PILZ,KOI,KARO,EI,PINGUIN,FUSSBALL,DONUT,CHROM,WELLE,KONFETTI,DISCO," +
      "ZUCKERSTANGE,HERZ,OSTEREI,DIAMANT",
    "Augen-Kontur trifft dieselben Skins wie in Kotlin"
  );

  // Standbilder duerfen sich ohne Zeitanteil nicht bewegen.
  DotSkin.SKINS.forEach(function (s) {
    if (s.animated) return;
    assertEq(
      DotSkin.cell(s, 4, 4, { elapsed: 0 }),
      DotSkin.cell(s, 4, 4, { elapsed: 5.5 }),
      "Skin " + s.name + " steht still"
    );
  });
  // Das Skin-Menue haengt an den Familien: Jeder Skin muss unter genau
  // einer Ueberschrift landen, und keine darf leer bleiben.
  var familien = 0, offen = null, zugeordnet = 0;
  DotSkin.SKINS.forEach(function (s) {
    var key = DotSkin.familyTitleKey(s);
    if (key) {
      familien++;
      offen = key;
    }
    assert(offen !== null, "Skin " + s.name + " steht unter einer Ueberschrift");
    zugeordnet++;
  });
  assertEq(familien, DotSkin.FAMILIES.length, "jede Familie beginnt genau einmal");
  assertEq(zugeordnet, DotSkin.SKINS.length, "alle Skins sind einsortiert");
  assertEq(DotSkin.familyTitleKey(DotSkin.fromName("KLASSIK")), "skin_family_einfarbig",
    "die Liste beginnt mit den einfarbigen");
  DotSkin.FAMILIES.forEach(function (f) {
    assert(Strings.STRINGS.de[f.titleKey] && Strings.STRINGS.en[f.titleKey],
      "Ueberschrift " + f.titleKey + " gibt es in beiden Sprachen");
  });

  // Namen und Hinweise: jeder Skin traegt beide Texte in beiden Sprachen.
  DotSkin.SKINS.forEach(function (s) {
    ["de", "en"].forEach(function (lang) {
      assert(!!Strings.STRINGS[lang][s.titleKey], lang + ": Name fuer " + s.name);
      if (s.hintKey) {
        assert(!!Strings.STRINGS[lang][s.hintKey], lang + ": Hinweis fuer " + s.name);
      }
    });
    assert(s.name === "KLASSIK" || !!s.hintKey, "nur KLASSIK kommt ohne Hinweis aus");
  });

  assertEq(DotSkin.fromName("LAVA").name, "LAVA", "fromName findet");
  assertEq(DotSkin.fromName("quatsch").name, "KLASSIK", "fromName-Fallback");

  // Das Rauschen von KONFETTI und DIAMANT rechnet in Kotlin in 32-Bit-Int
  // mit Ueberlauf. Gegengeprueft wird hier mit BigInt — eine zweite,
  // unabhaengige Rechnung, die den Ueberlauf exakt nachbildet. Ohne
  // Math.imul und ">>>" liefe der Port ab 2^53 auseinander.
  function noiseRef(col, row, seed) {
    function i32(v) { return BigInt.asIntN(32, v); }
    function ushr(v, k) { return BigInt.asUintN(32, v) >> BigInt(k); }
    var n = i32(BigInt(col) * 73856093n) ^ i32(BigInt(row) * 19349663n) ^
      i32(BigInt(seed) * 83492791n);
    n = i32(n);
    n = i32((n ^ ushr(n, 13)) * 1274126177n);
    var v = i32(n ^ ushr(n, 16));
    return v < 0n ? i32(-v) : v;
  }
  var rauschen = 0;
  [0, 1, 2, 7, 41, 137, 1000, 65535, 123456789].forEach(function (seed) {
    for (var row = 0; row < DotSkin.GRID; row++) {
      for (var col = 0; col < DotSkin.GRID; col++) {
        assertEq(BigInt(DotSkin.noise(col, row, seed)), noiseRef(col, row, seed),
          "Rauschen (" + col + "," + row + "," + seed + ")");
        rauschen++;
      }
    }
  });
  assertEq(rauschen, 9 * DotSkin.GRID * DotSkin.GRID, "Rauschen breit geprueft");
  // Es muss auch streuen, sonst waere KONFETTI eine einfarbige Kugel.
  var bunt = 0;
  for (var f = 0; f < DotSkin.GRID * DotSkin.GRID; f++) {
    if (DotSkin.noise(f % DotSkin.GRID, Math.floor(f / DotSkin.GRID), 3) % 100 < 38) bunt++;
  }
  assert(bunt > 20 && bunt < 140, "Konfetti trifft einen Teil der Felder (" + bunt + ")");

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

  // ===== Ausdauer-Achsen =====
  assertEq(ScoreStore.totalScore, 8, "Punkte summieren sich (5 + 3)");
  assertEq(ScoreStore.daysPlayed, 0, "ohne Kalender kein Tages-Zaehler");

  // 15.06.2026 ist Epoch-Day 20619 — zwei Laeufe am selben Tag zaehlen
  // einen Tag, der Folgetag einen zweiten.
  ScoreStore.submitRun(4, 20619, 2026, 6);
  ScoreStore.submitRun(2, 20619, 2026, 6);
  assertEq(ScoreStore.totalScore, 14, "Punktesumme waechst weiter");
  assertEq(ScoreStore.daysPlayed, 1, "zwei Laeufe am selben Tag = ein Tag");
  ScoreStore.submitRun(1, 20620, 2026, 6);
  assertEq(ScoreStore.daysPlayed, 2, "Folgetag zaehlt einen Tag dazu");
  assertEq(ScoreStore.stats().monthsPlayed, 1, "ein Monat gesehen");
  ScoreStore.submitRun(1, 20650, 2026, 7);
  assertEq(ScoreStore.stats().monthsPlayed, 2, "zweiter Monat gesehen");
  ScoreStore.submitRun(1, 21015, 2027, 7);
  assertEq(ScoreStore.stats().monthsPlayed, 2, "derselbe Monat im Folgejahr zaehlt nicht doppelt");

  // Saison: fuenf Tage im Oktober oeffnen den Kuerbis — und er bleibt.
  var kuerbis = DotSkin.SEASONS[0];
  assertEq(ScoreStore.seasonEarned, 0, "noch keine Saison verdient");
  for (var tag = 0; tag < 4; tag++) ScoreStore.submitRun(3, 20730 + tag, 2026, 10);
  assertEq(ScoreStore.seasonEarned, 0, "vier Tage reichen dem Kuerbis nicht");
  ScoreStore.submitRun(3, 20734, 2026, 10);
  assertEq(ScoreStore.seasonEarned, kuerbis.bit, "fuenf Tage im Oktober oeffnen den Kuerbis");
  assert(DotSkin.isUnlocked(DotSkin.fromName("KUERBIS"), ScoreStore.stats()),
    "KUERBIS ist damit freigeschaltet");
  ScoreStore.submitRun(3, 20760, 2026, 11);
  assertEq(ScoreStore.seasonEarned, kuerbis.bit, "im November bleibt der Kuerbis");
  ScoreStore.submitRun(3, 21095, 2027, 10);
  assertEq(ScoreStore.seasonEarned, kuerbis.bit,
    "das neue Fenster faengt bei null an, ohne das Bit zu verlieren");

  assertEq(ScoreStore.patronOwned, false, "im Web gibt es kein Goenner-Paket");
  assert(!DotSkin.isUnlocked(DotSkin.fromName("ONYX"), ScoreStore.stats()),
    "Goenner-Skins bleiben im Web gesperrt");
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
  assertEq(kotlinSky.join(","), DotSkin.SKY_STAGES.join(","), "dieselben Himmelsstufen wie in Kotlin");

  // Der Himmel laeuft im Umlauf statt in der Nacht stehenzubleiben — die
  // Laenge des Umlaufs steht in beiden Quellen und muss uebereinstimmen.
  var kotlinCycle = parseInt(
    (paintKt.match(/const val SKY_CYCLE = (\d+)/) || [])[1], 10
  );
  assertEq(DotSkin.SKY_CYCLE, kotlinCycle, "gleiche Umlauf-Laenge wie in Kotlin");

  var folge = [];
  for (var punkte = 0; punkte <= 60; punkte += 5) folge.push(DotSkin.skyStage(punkte));
  assertEq(folge.join(","), "0,1,2,3,4,5,6,5,4,3,2,1,0", "hoch bis zur Nacht und zurueck zum Tag");
  assertEq(DotSkin.skyStage(4), 0, "unter fuenf Punkten bleibt es Tag");
  assertEq(DotSkin.skyStage(90), 6, "nach eineinhalb Umlaeufen wieder Nacht");
  var imBereich = true;
  for (var sc = 0; sc <= 1000; sc++) {
    var st = DotSkin.skyStage(sc);
    if (st < 0 || st >= DotSkin.SKY_STAGES.length) imBereich = false;
  }
  assert(imBereich, "die Stufe bleibt immer in der Farbtabelle");

  // Freischalt-Schwellen: jede Regel aus Kotlin wird an ihrer Kante geprueft.
  var regeln = whenBlock("fun isUnlocked(id: SkinId, stats: SkinStats): Boolean = when (id) {");
  var felder2 = {
    "stats.bestScore": "bestScore",
    "stats.bestPerfectStreak": "bestPerfectStreak",
    "stats.bestDailyStreak": "bestDailyStreak",
    "stats.runCount": "runCount",
    "stats.totalScore": "totalScore",
    "stats.daysPlayed": "daysPlayed",
    "stats.monthsPlayed": "monthsPlayed"
  };
  var geprueft = 0;
  Object.keys(regeln).forEach(function (id) {
    // Kotlin schreibt grosse Schwellen als 1_000 — der Unterstrich gehoert
    // zur Zahl, sonst laese der Test hier eine 1.
    var rm = regeln[id].match(/(stats\.\w+) >= ([\d_]+)/);
    if (!rm) return; // KLASSIK, REGENBOGEN, Saison und Goenner
    var feld = felder2[rm[1]];
    assert(!!feld, "Achse " + rm[1] + " ist im Web bekannt");
    var schwelle = parseInt(rm[2].replace(/_/g, ""), 10);
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
  // Ohne Schwelle bleiben KLASSIK (immer offen), REGENBOGEN (Sammlung),
  // die vier Saison- und die drei Goenner-Skins.
  assertEq(geprueft, kotlinIds.length - 9,
    "alle Schwellen ausser Klassik, Regenbogen, Saison und Goenner geprueft");

  // ===== Die neuen Skins: Muster, Tabellen und Achsen aus :core =====

  /** "0xFF4EC0CA, 0xFF..., 5" -> ["#4EC0CA", ..., 5] */
  function kotlinValues(text) {
    return text.split(",").map(function (raw) {
      var v = raw.trim();
      var hex = v.match(/^0x[Ff]{2}([0-9A-Fa-f]{6})$/);
      return hex ? "#" + hex[1].toUpperCase() : parseInt(v, 10);
    });
  }

  /** Inhalt eines longArrayOf(...) hinter "val NAME". */
  function kotlinTable(name) {
    var i = paintKt.indexOf("val " + name);
    assert(i >= 0, "Tabelle " + name + " in SkinPaint.kt gefunden");
    var open = paintKt.indexOf("longArrayOf(", i);
    return kotlinValues(paintKt.slice(open + 12, paintKt.indexOf(")", open)));
  }

  /** Alle longArrayOf(...)-Zeilen eines arrayOf(...)-Blocks. */
  function kotlinTable2d(name) {
    var i = paintKt.indexOf("val " + name);
    assert(i >= 0, "Tabelle " + name + " in SkinPaint.kt gefunden");
    var block = paintKt.slice(i, paintKt.indexOf("\n    )", i));
    return (block.match(/longArrayOf\(([^)]*)\)/g) || []).map(function (row) {
      return kotlinValues(row.slice(12, -1));
    });
  }

  [
    ["SPRINKLE_COLORS", DotSkin.SPRINKLE_COLORS],
    ["CONFETTI_COLORS", DotSkin.CONFETTI_COLORS],
    ["DISCO_COLORS", DotSkin.DISCO_COLORS],
    ["DIAMOND_COLORS", DotSkin.DIAMOND_COLORS]
  ].forEach(function (pair) {
    assertEq(pair[1].join(","), kotlinTable(pair[0]).join(","), "Tabelle " + pair[0]);
  });

  [
    ["EASTER_COLORS", DotSkin.EASTER_COLORS],
    ["MEDAL_COLORS", DotSkin.MEDAL_COLORS]
  ].forEach(function (pair) {
    var kt = kotlinTable2d(pair[0]);
    assertEq(pair[1].length, kt.length, "Tabelle " + pair[0] + " hat gleich viele Zeilen");
    kt.forEach(function (row, i) {
      assertEq(pair[1][i].join(","), row.join(","), "Tabelle " + pair[0] + " Zeile " + i);
    });
  });

  // Die beiden Paletten werden ueber ihren ganzen Wertebereich verglichen —
  // eine verrutschte Stundengrenze faellt sonst erst abends auf.
  function paletteBranches(head) {
    var block = paintKt.slice(paintKt.indexOf(head));
    block = block.slice(0, block.indexOf("\n    }"));
    // Die Bedingung steht mal als Bereich (in 5..8), mal als Aufzaehlung
    // (3, 4, 5) und einmal als else — alle drei Formen lesen.
    var out = [], bm;
    var re = /([^\n]+?) -> longArrayOf\(([^)]*)\)/g;
    while ((bm = re.exec(block)) !== null) {
      var head = bm[1].trim();
      var range = head.match(/^in (\d+)\.\.(\d+)$/);
      out.push({
        werte: head === "else" ? null
          : range ? { from: parseInt(range[1], 10), to: parseInt(range[2], 10) }
            : head.split(",").map(function (v) { return parseInt(v.trim(), 10); }),
        values: kotlinValues(bm[2])
      });
    }
    return out;
  }

  function paletteFor(branches, value) {
    for (var i = 0; i < branches.length; i++) {
      var b = branches[i];
      if (b.werte === null) return b.values;
      if (Array.isArray(b.werte)) {
        if (b.werte.indexOf(value) >= 0) return b.values;
      } else if (value >= b.werte.from && value <= b.werte.to) {
        return b.values;
      }
    }
    return null;
  }

  var dayBranches = paletteBranches("private fun dayPalette(hour: Int): LongArray = when (hour) {");
  assertEq(dayBranches.length, 4, "vier Tageszeiten in Kotlin");
  for (var hour = 0; hour < 24; hour++) {
    assertEq(DotSkin.dayPalette(hour).join(","), paletteFor(dayBranches, hour).join(","),
      "Tages-Palette Stunde " + hour);
  }

  var seasonBranches = paletteBranches("private fun seasonPalette(month: Int): LongArray = when (month) {");
  assertEq(seasonBranches.length, 4, "vier Jahreszeiten in Kotlin");
  for (var mo = 1; mo <= 12; mo++) {
    assertEq(DotSkin.seasonPalette(mo).join(","), paletteFor(seasonBranches, mo).join(","),
      "Jahreszeit-Palette Monat " + mo);
  }

  // Saison-Fenster: Monat, geforderte Tage und Bit muessen sich decken,
  // sonst verdient das Web den Kuerbis im falschen Monat oder setzt das
  // Bit des Herzens.
  var seasonEnum = paintKt.slice(paintKt.indexOf("enum class Season("));
  seasonEnum = seasonEnum.slice(0, seasonEnum.indexOf(";"));
  var seasonRows = [], sm;
  var seasonRe = /\n    (\w+)\(SkinId\.(\w+), (\d+), (\d+)\)/g;
  while ((sm = seasonRe.exec(seasonEnum)) !== null) {
    seasonRows.push({ skin: sm[2], month: parseInt(sm[3], 10), days: parseInt(sm[4], 10) });
  }
  assertEq(seasonRows.length, 4, "vier Saison-Eintraege in Kotlin");
  assertEq(DotSkin.SEASONS.length, seasonRows.length, "gleich viele Saisons im Web");
  seasonRows.forEach(function (row, i) {
    var web = DotSkin.SEASONS[i];
    assertEq(web.skin, row.skin, "Saison " + i + " gehoert zu " + row.skin);
    assertEq(web.month, row.month, "Saison " + row.skin + " Monat");
    assertEq(web.requiredDays, row.days, "Saison " + row.skin + " Tage");
    // Season.bit ist 1 shl ordinal — die Reihenfolge ist das Bit.
    assertEq(web.bit, 1 << i, "Saison " + row.skin + " Bit");
    assertEq(DotSkin.seasonForMonth(row.month).skin, row.skin, "Monat " + row.month + " gehoert " + row.skin);
  });
  assertEq(DotSkin.seasonForMonth(1), null, "im Januar gibt es nichts zu verdienen");

  /** Namen aus einem einzeiligen/mehrzeiligen when-Zweig von SkinPaint. */
  function idsIn(head, stop) {
    var block = paintKt.slice(paintKt.indexOf(head) + head.length);
    block = block.slice(0, block.indexOf(stop));
    return (block.match(/SkinId\.(\w+)/g) || []).map(function (s) { return s.slice(7); });
  }

  function webNames(pruefer) {
    return DotSkin.SKINS.filter(pruefer).map(function (s) { return s.name; }).join(",");
  }

  assertEq(
    webNames(DotSkin.isAnimated),
    idsIn("fun isAnimated(id: SkinId): Boolean = when (id) {", "else -> false").join(","),
    "dieselben bewegten Skins wie in Kotlin"
  );
  assertEq(
    webNames(DotSkin.hasTrail),
    idsIn("fun hasTrail(id: SkinId): Boolean =", "\n").join(","),
    "dieselben Schweif-Skins wie in Kotlin"
  );
  assertEq(
    webNames(DotSkin.isPatron),
    idsIn("fun isPatron(id: SkinId): Boolean = when (id) {", "-> true").join(","),
    "dieselben Goenner-Skins wie in Kotlin"
  );
  assertEq(
    webNames(DotSkin.isSeasonal),
    seasonRows.map(function (r) { return r.skin; }).join(","),
    "dieselben Saison-Skins wie in Kotlin"
  );
  assertEq(
    DotSkin.collectableCount(),
    kotlinIds.length - seasonRows.length - 3,
    "gleich viele sammelbare Skins wie in Kotlin"
  );

  assertEq(DotSkin.HEAT_SCORE,
    parseInt((paintKt.match(/const val HEAT_SCORE = (\d+)/) || [])[1], 10),
    "THERMO glueht bei derselben Punktzahl durch");
  assertEq(DotSkin.TRAIL_STEPS,
    parseInt((paintKt.match(/const val TRAIL_STEPS = (\d+)/) || [])[1], 10),
    "gleich viele Schweif-Nachbilder");

  // Medaillenstufe: dieselben Schwellen wie SkinPaint.medalTier.
  var tierBlock = paintKt.slice(paintKt.indexOf("fun medalTier(score: Int): Int = when {"));
  tierBlock = tierBlock.slice(0, tierBlock.indexOf("\n    }"));
  var tierRe = /score >= (\d+) -> (\d+)/g;
  var tierRows = [], tm;
  while ((tm = tierRe.exec(tierBlock)) !== null) {
    tierRows.push([parseInt(tm[1], 10), parseInt(tm[2], 10)]);
  }
  assertEq(tierRows.length, 4, "vier Medaillen-Schwellen in Kotlin");
  tierRows.forEach(function (row) {
    assertEq(DotSkin.medalTier(row[0]), row[1], "Stufe " + row[1] + " ab " + row[0]);
    assertEq(DotSkin.medalTier(row[0] - 1), row[1] - 1, "unter " + row[0] + " noch Stufe " + (row[1] - 1));
  });
  assertEq(DotSkin.medalTier(0), 0, "ohne Punkte keine Medaille");
  assertEq(DotSkin.MEDAL_COLORS.length, tierRows.length + 1, "eine Legierung je Stufe");

  // Muster-Koordinaten: Die Listen im Web muessen Feld fuer Feld denselben
  // Flecken treffen wie die Hilfsfunktionen in Kotlin.
  function kotlinPairs(fn) {
    var i = paintKt.indexOf("private fun " + fn + "(col: Int, row: Int): Boolean");
    assert(i >= 0, "Hilfsfunktion " + fn + " gefunden");
    var block = paintKt.slice(i, paintKt.indexOf("\n\n", i));
    var out = [], pm;
    var re = /\(col == (\d+) && row == (\d+)\)/g;
    while ((pm = re.exec(block)) !== null) out.push(pm[1] + "," + pm[2]);
    return out.join("|");
  }

  function webPairs(list) {
    return list.map(function (p) { return p[0] + "," + p[1]; }).join("|");
  }

  var skinsSrc = fs.readFileSync(path.join(__dirname, "..", "js", "skins.js"), "utf8");
  function webList(name) {
    var m2 = skinsSrc.match(new RegExp("var " + name + " = (\\[[\\s\\S]*?\\]);\\n"));
    assert(!!m2, "Liste " + name + " in skins.js gefunden");
    return eval(m2[1]); // eslint-disable-line no-eval
  }

  [
    ["isSeed", "MELON_SEEDS"], ["isDot", "MUSHROOM_DOTS"],
    ["isRedPatch", "KOI_RED"], ["isOrangePatch", "KOI_ORANGE"],
    ["isStar", "GALAXY_STARS"], ["isNebula", "GALAXY_NEBULA"],
    ["isBallPatch", "BALL_PATCHES"], ["isSprinkle", "DONUT_SPRINKLES"],
    ["isBolt", "BOLT"]
  ].forEach(function (pair) {
    assertEq(webPairs(webList(pair[1])), kotlinPairs(pair[0]),
      "Muster " + pair[1] + " deckt sich mit " + pair[0]);
  });

  // Farb-Literale je Skin: Der Zweig in SkinPaint.cell und die cell-Funktion
  // im Web muessen dieselben Farben nennen. Zweige ohne eigenes Literal
  // (die einfarbigen, die body/shade nachschlagen) sind schon oben geprueft.
  var cellStart = paintKt.indexOf("fun cell(id: SkinId, col: Int, row: Int, state: SkinState = SkinState()): Long {");
  var cellBlock = paintKt.slice(cellStart, paintKt.indexOf("// ===== Muster-Details =====", cellStart));
  var branchIds = [], branchBuf = "", branches = {};
  function closeBranch() {
    if (!branchIds.length) return;
    var hexes = (branchBuf.match(/0x[Ff]{2}[0-9A-Fa-f]{6}/g) || [])
      .map(function (h) { return "#" + h.slice(4).toUpperCase(); });
    branchIds.forEach(function (id) { branches[id] = hexes; });
  }
  cellBlock.split("\n").forEach(function (line) {
    // Ein Zweig beginnt erst, wenn der vorige seinen Pfeil hatte — die
    // einfarbigen Skins teilen sich einen Kopf ueber zwei Zeilen.
    var head = /^ {12}SkinId\./.test(line);
    if (head && branchBuf.indexOf("->") >= 0) {
      closeBranch();
      branchIds = [];
      branchBuf = "";
    }
    branchBuf += line + "\n";
    if (branchIds.length === 0 && branchBuf.indexOf("->") >= 0) {
      branchIds = (branchBuf.slice(0, branchBuf.indexOf("->")).match(/SkinId\.(\w+)/g) || [])
        .map(function (s) { return s.slice(7); });
    }
  });
  closeBranch();
  assertEq(Object.keys(branches).length, kotlinIds.length, "jeder Skin hat einen Zweig in cell()");

  var verglichen = 0;
  Object.keys(branches).forEach(function (id) {
    var kt = branches[id];
    if (!kt.length) return; // einfarbig: holt body/shade aus den Tabellen
    var web = (DotSkin.fromName(id).cell.toString().match(/#[0-9A-F]{6}/g) || []);
    function unique(list) {
      return list.filter(function (v, i) { return list.indexOf(v) === i; }).sort().join(",");
    }
    assertEq(unique(web), unique(kt), "Skin " + id + " nennt dieselben Farben wie SkinPaint");
    verglichen++;
  });
  assert(verglichen >= 25, "genug Skin-Zweige verglichen (" + verglichen + ")");

  // Medaillen-Farben standen früher hier: Sie wurden aus medalColors() in
  // GameOverlays.kt gelesen. Seit MedalPaint (:core) die einzige Quelle
  // ist, kommen Schwellen und Farben aus parity/golden-vectors.txt —
  // siehe den Paritäts-Abschnitt weiter unten.

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

// ===== Paritäts-Vektoren aus :core (parity/golden-vectors.txt) =====
//
// Dieselbe Datei prüft der Swift-Port in ios/DottieTests. Was hier nicht
// vorkommt: die Abschnitte rng.* und trace.*. Der Web-Port baut Kotlins
// XorWow-Generator bewusst nicht nach (siehe js/game.js), seine Daily
// Challenge hat also eine eigene Zonen-Abfolge — Regeln, Farben und
// Konstanten müssen trotzdem überall gleich sein. Siehe parity/README.md.
(function () {
  var fs = require("fs");
  var path = require("path");

  var file = path.join(__dirname, "..", "..", "parity", "golden-vectors.txt");
  var text;
  try {
    text = fs.readFileSync(file, "utf8");
  } catch (e) {
    // Die Tests laufen auch außerhalb des Repos (nur web/ ausgeliefert).
    console.log("Hinweis: parity/golden-vectors.txt nicht gefunden — übersprungen.");
    return;
  }

  var V = {};
  var keys = [];
  text.split("\n").forEach(function (raw) {
    var line = raw.trim();
    if (!line || line.charAt(0) === "#") return;
    var parts = line.split(" ");
    V[parts[0]] = parts.slice(1);
    keys.push(parts[0]);
  });

  function one(key) { return (V[key] || [])[0]; }
  function num(key) { return parseFloat(one(key)); }

  /** "0xAARRGGBB" aus der Vektor-Datei als "#RRGGBB" wie im Web-Port. */
  function rgb(token) {
    return "#" + token.slice(-6).toUpperCase();
  }

  /** Farbvergleich mit ±2 pro Kanal (Float- vs. Double-Rundung). */
  function sameColor(expected, actual) {
    if (!actual) return false;
    var a = expected.replace("#", "");
    var b = actual.replace("#", "").toUpperCase();
    if (a.length !== 6 || b.length !== 6) return false;
    for (var i = 0; i < 6; i += 2) {
      if (Math.abs(parseInt(a.substr(i, 2), 16) - parseInt(b.substr(i, 2), 16)) > 2) {
        return false;
      }
    }
    return true;
  }

  assertEq(parseInt(one("version"), 10), 2, "Paritäts-Vektoren: bekanntes Format");

  // --- Konstanten der Engine: gleiche Namen wie in Kotlin und Swift.
  var constCount = 0;
  keys.forEach(function (key) {
    if (key.indexOf("const.") !== 0) return;
    var name = key.slice("const.".length);
    assert(C[name] !== undefined, "js/game.js kennt Konstante " + name);
    if (C[name] === undefined) return;
    assert(approx(C[name], num(key), 1e-5), "Konstante " + name +
      " (erwartet " + num(key) + ", ist " + C[name] + ")");
    constCount++;
  });
  assert(constCount > 25, "genug Konstanten verglichen (" + constCount + ")");

  // --- Twists
  keys.forEach(function (key) {
    if (key.indexOf("twist.unlock.") !== 0) return;
    var twist = key.slice("twist.unlock.".length);
    assertEq(TimingGame.unlockScore(twist), parseInt(one(key), 10),
      "Freischalt-Score " + twist);
  });

  // --- Daily Challenge: Kotlin schreibt den Seed vorzeichenbehaftet,
  //     der Web-Port rechnet vorzeichenlos — asIntN bringt beide zusammen.
  keys.forEach(function (key) {
    if (key.indexOf("daily.seed.") !== 0) return;
    var day = parseInt(key.slice("daily.seed.".length), 10);
    var actual = BigInt.asIntN(64, DailyChallenge.seedFor(day));
    assertEq(actual.toString(), one(key), "Tages-Seed für Epoch-Day " + day);
  });
  keys.forEach(function (key) {
    if (key.indexOf("daily.streak.") !== 0) return;
    var p = key.slice("daily.streak.".length).split(".");
    assertEq(
      DailyChallenge.nextStreak(parseInt(p[0], 10), parseInt(p[1], 10), parseInt(p[2], 10)),
      parseInt(one(key), 10),
      "Serien-Regel " + key
    );
  });

  // --- Medaillen
  MedalTier.MEDALS.forEach(function (medal) {
    var row = V["medal." + medal.name];
    assert(row !== undefined, "Vektoren kennen Medaille " + medal.name);
    if (!row) return;
    assertEq(medal.threshold, parseInt(row[0], 10), "Schwelle " + medal.name);
    assert(sameColor(rgb(row[1]), medal.body), "Münzfarbe " + medal.name);
    assert(sameColor(rgb(row[2]), medal.shade), "Schattenfarbe " + medal.name);
  });
  keys.forEach(function (key) {
    if (key.indexOf("medal.forScore.") !== 0) return;
    var score = parseInt(key.slice("medal.forScore.".length), 10);
    var current = MedalTier.forScore(score);
    var next = MedalTier.next(score);
    assertEq(current ? current.name : "-", V[key][0], "Medaille bei Score " + score);
    assertEq(next ? next.name : "-", V[key][1], "nächste Medaille bei Score " + score);
  });

  // --- Himmel
  assertEq(DotSkin.SKY_CYCLE, parseInt(one("sky.cycle"), 10), "Länge des Himmels-Umlaufs");
  V["sky.stages"].forEach(function (token, i) {
    assert(sameColor(rgb(token), DotSkin.SKY_STAGES[i]), "Himmelsstufe " + i);
  });
  V["sky.stageForScore"].forEach(function (expected, i) {
    assertEq(DotSkin.skyStage(i * 5), parseInt(expected, 10),
      "Himmelsstufe bei Score " + (i * 5));
  });

  // --- Skins: Reihenfolge, Stellvertreterfarben, Eigenschaften
  assertEq(DotSkin.SKINS.map(function (s) { return s.name; }).join(","),
    V["skin.order"].join(","), "Reihenfolge der Skins");
  assertEq(DotSkin.GRID, parseInt(one("skin.grid"), 10), "Rastergröße");

  assertEq(DotSkin.collectableCount(), parseInt(one("skin.collectableCount"), 10),
    "Zahl der sammelbaren Skins (daran hängt der REGENBOGEN)");

  DotSkin.SKINS.forEach(function (skin) {
    var row = V["skin.chips." + skin.name];
    assert(row !== undefined, "Vektoren kennen Skin " + skin.name);
    if (!row) return;
    assert(sameColor(rgb(row[0]), skin.body), skin.name + ": Körperfarbe");
    assert(sameColor(rgb(row[1]), skin.shade), skin.name + ": Schattenfarbe");
    assert(sameColor(rgb(row[2]), DotSkin.shine(skin)), skin.name + ": Glanzfarbe");
    assertEq(DotSkin.hasTrail(skin), row[3] === "trail", skin.name + ": Schweif");
    assertEq(DotSkin.needsEyeOutline(skin), row[4] === "eyeoutline",
      skin.name + ": Augen-Kontur");
    assertEq(DotSkin.isAnimated(skin), row[5] === "animated", skin.name + ": animiert");
    assertEq(DotSkin.isSeasonal(skin), row[6] === "seasonal", skin.name + ": Saison");
    assertEq(DotSkin.isPatron(skin), row[7] === "patron", skin.name + ": Gönner");
    assertEq(DotSkin.countsForCollection(skin), row[8] === "collectable",
      skin.name + ": zählt für die Sammlung");
  });

  // --- Saison: nur die Maske entscheidet, nie der Kalender
  DotSkin.SEASONS.forEach(function (season) {
    var row = V["season." + season.skin];
    assert(row !== undefined, "Vektoren kennen Saison " + season.skin);
    if (!row) return;
    assertEq(season.month, parseInt(row[0], 10), season.skin + ": Monat");
    assertEq(season.bit, parseInt(row[1], 10), season.skin + ": Bit");
    assertEq(season.requiredDays, parseInt(row[2], 10), season.skin + ": Tage");
  });
  V["season.forMonth"].forEach(function (expected, i) {
    var season = DotSkin.seasonForMonth(i + 1);
    assertEq(season ? season.skin : "-", expected, "Saison in Monat " + (i + 1));
  });

  // --- Abgetastete Rasterfarben je Zustand
  var CELLS = [[2, 6], [4, 3], [6, 2], [6, 6], [8, 5], [9, 8], [6, 10], [10, 6]];
  var stateIndex = 0;
  var cellChecks = 0;
  while (V["skin.state." + stateIndex]) {
    var s = V["skin.state." + stateIndex];
    var state = {
      elapsed: parseFloat(s[0]),
      score: parseInt(s[1], 10),
      perfectStreak: parseInt(s[2], 10),
      hour: parseInt(s[3], 10),
      month: parseInt(s[4], 10)
    };
    /* eslint-disable no-loop-func */
    (function (index, state) {
      DotSkin.SKINS.forEach(function (skin) {
        var row = V["skin.cells." + index + "." + skin.name];
        if (!row) return;
        CELLS.forEach(function (cell, i) {
          assert(
            sameColor(rgb(row[i]), DotSkin.cell(skin, cell[0], cell[1], state)),
            skin.name + " Feld (" + cell[0] + "," + cell[1] + ") Zustand " + index
          );
          cellChecks++;
        });
        var shineRow = V["skin.shine." + index + "." + skin.name];
        if (shineRow) {
          assert(sameColor(rgb(shineRow[0]), DotSkin.shine(skin, state)),
            skin.name + " Glanz Zustand " + index);
        }
      });
    })(stateIndex, state);
    stateIndex++;
  }
  assert(cellChecks > 100, "genug Rasterfarben verglichen (" + cellChecks + ")");

  // --- Freischaltungen: je Probe die neun Bestleistungen, dann der
  //     Sammlungsstand und die Liste der offenen Skins.
  var probe = 0;
  while (V["skin.probe." + probe]) {
    var p = V["skin.probe." + probe];
    var key = "skin.unlocked." + probe;
    var stats = {
      bestScore: parseInt(p[0], 10),
      bestPerfectStreak: parseInt(p[1], 10),
      bestDailyStreak: parseInt(p[2], 10),
      runCount: parseInt(p[3], 10),
      totalScore: parseInt(p[4], 10),
      daysPlayed: parseInt(p[5], 10),
      monthsPlayed: parseInt(p[6], 10),
      seasonEarned: parseInt(p[7], 10),
      patronOwned: p[8] === "1"
    };
    var open = DotSkin.SKINS.filter(function (skin) {
      return DotSkin.isUnlocked(skin, stats);
    }).map(function (skin) { return skin.name; });
    assertEq(DotSkin.unlockedCount(stats), parseInt(V[key][0], 10),
      "Sammlungsstand bei " + key);
    assertEq(open.join(","), V[key].slice(1).join(","), "offene Skins bei " + key);
    probe++;
  }
  assert(probe > 0, "Freischalt-Proben in der Datei gefunden");
})();

// ===== PWA-Auslieferung: Service Worker und Manifest =====
(function () {
  var fs = require("fs");
  var path = require("path");
  var webDir = path.join(__dirname, "..");

  // Alle Dateien, die deploy-pages.yml nach _site kopiert (tests/ und
  // README.md fliegen dort raus).
  function shipped(dir, prefix) {
    var out = [];
    fs.readdirSync(dir, { withFileTypes: true }).forEach(function (entry) {
      if (entry.name === "tests" || entry.name === "README.md") return;
      var rel = prefix + entry.name;
      if (entry.isDirectory()) {
        out = out.concat(shipped(path.join(dir, entry.name), rel + "/"));
      } else {
        out.push(rel);
      }
    });
    return out;
  }

  var sw = fs.readFileSync(path.join(webDir, "sw.js"), "utf8");
  var assets = (sw.slice(sw.indexOf("var ASSETS = ["), sw.indexOf("];"))
    .match(/"\.\/[^"]*"/g) || []).map(function (q) {
      return q.slice(3, -1); // ohne Anführungszeichen und führendes "./"
    });

  // Nicht im Cache erwartet: sw.js selbst wird von der Registrierung
  // geladen, nicht aus dem Cache bedient, und app-ads.txt holt sich nur
  // der Crawler eines Werbenetzes — offline braucht die Datei niemand.
  var NICHT_GECACHT = ["sw.js", "app-ads.txt"];

  // Alles andere muss in der Liste stehen, sonst fehlt es offline.
  shipped(webDir, "").forEach(function (file) {
    if (NICHT_GECACHT.indexOf(file) !== -1) return;
    assert(
      assets.indexOf(file) !== -1,
      "sw.js cacht " + file + " (ASSETS-Liste unvollständig)"
    );
  });

  assets.forEach(function (asset) {
    if (asset === "") return; // "./" — die Startseite
    assert(
      fs.existsSync(path.join(webDir, asset)),
      "sw.js listet nur existierende Dateien: " + asset
    );
  });

  var manifest = JSON.parse(
    fs.readFileSync(path.join(webDir, "manifest.webmanifest"), "utf8")
  );
  var icons = manifest.icons || [];
  icons.forEach(function (icon) {
    assert(
      fs.existsSync(path.join(webDir, icon.src)),
      "Manifest-Icon existiert: " + icon.src
    );
    assert(
      assets.indexOf(icon.src) !== -1,
      "Manifest-Icon ist offline verfügbar: " + icon.src
    );
  });
  assert(
    icons.some(function (i) { return (i.purpose || "").indexOf("maskable") !== -1; }),
    "Manifest hat ein maskable-Icon (sonst schrumpft Android das Icon in einen weißen Kreis)"
  );
  assert(
    icons.some(function (i) { return i.sizes === "192x192"; }) &&
      icons.some(function (i) { return i.sizes === "512x512"; }),
    "Manifest hat 192er- und 512er-Icon"
  );
  assertEq(manifest.theme_color, "#4EC0CA", "Manifest-Theme = Himmelblau");
})();

console.log(checks + " Checks, " + failures + " Fehler");
process.exit(failures === 0 ? 0 : 1);

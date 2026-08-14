/*
 * 1:1-Port der Spiellogik aus core/src/main/kotlin/.../TimingGame.kt.
 * Gleiche Konstanten, gleiches Verhalten — bitte Änderungen immer parallel
 * zur Kotlin-Quelle pflegen.
 *
 * Läuft als plain <script> im Browser (Globals) und via require() in Node
 * (für die Tests in web/tests/).
 */
(function (global) {
  "use strict";

  var TWO_PI = Math.PI * 2;

  /** Normalisiert auf (-PI, PI]. */
  function wrapToPi(value) {
    var v = value % TWO_PI;
    if (v <= -Math.PI) v += TWO_PI;
    if (v > Math.PI) v -= TWO_PI;
    return v;
  }

  /** Normalisiert auf [0, 2*PI). */
  function wrapTwoPi(value) {
    var v = value % TWO_PI;
    if (v < 0) v += TWO_PI;
    return v;
  }

  /*
   * Zufallsquelle. Ohne Seed: Math.random. Mit Seed (BigInt/Number):
   * deterministischer splitmix64 — jeder Versuch mit demselben Seed
   * bekommt dieselbe Zonen- und Twist-Abfolge (Daily Challenge).
   * (Bewusste Abweichung: nicht bit-identisch zu kotlin.random.Random,
   * aber mit derselben Eigenschaft: fester Seed => feste Abfolge.)
   */
  function Rng(seed) {
    if (seed === null || seed === undefined) {
      this._state = null;
    } else {
      this._state = BigInt.asUintN(64, BigInt(seed));
    }
  }

  Rng.prototype._next64 = function () {
    // splitmix64
    this._state = BigInt.asUintN(64, this._state + 0x9e3779b97f4a7c15n);
    var z = this._state;
    z = BigInt.asUintN(64, (z ^ (z >> 30n)) * 0xbf58476d1ce4e5b9n);
    z = BigInt.asUintN(64, (z ^ (z >> 27n)) * 0x94d049bb133111ebn);
    return BigInt.asUintN(64, z ^ (z >> 31n));
  };

  /** Gleichverteilt in [0, 1). */
  Rng.prototype.nextFloat = function () {
    if (this._state === null) return Math.random();
    return Number(this._next64() >> 40n) / 16777216; // 24 Bit Mantisse
  };

  Rng.prototype.nextBoolean = function () {
    return this.nextFloat() < 0.5;
  };

  /** Ganzzahl in [0, bound). */
  Rng.prototype.nextInt = function (bound) {
    return Math.floor(this.nextFloat() * bound);
  };

  /** Fisher-Yates, wie Kotlins List.shuffled(random). */
  Rng.prototype.shuffled = function (list) {
    var out = list.slice();
    for (var i = out.length - 1; i >= 1; i--) {
      var j = this.nextInt(i + 1);
      var tmp = out[i];
      out[i] = out[j];
      out[j] = tmp;
    }
    return out;
  };

  // ===== Konstanten (identisch zu TimingGame.companion) =====
  var C = {
    MAX_DELTA: 1 / 30,

    // Tempo (Radiant pro Sekunde)
    BASE_SPEED: 2.4,
    SPEED_PER_HIT: 0.07,
    MAX_SPEED: 5.2,
    READY_SPEED: 1.2,

    // Zielzone (Radiant)
    BASE_ZONE_HALF: 0.4,
    ZONE_SHRINK_PER_HIT: 0.005,
    MIN_ZONE_HALF: 0.15,
    PERFECT_SHARE: 0.35,
    // Aus wie vielen Bloecken die Bahn besteht, und die halbe Winkelbreite
    // eines Blocks. Stand bisher nur im Renderer; seit der PERFEKT-Kern
    // auch gewertet wird, ist die Zahl Spielregel.
    TRACK_SEGMENTS: 60,
    SEGMENT_HALF: Math.PI / 60,
    MIN_ZONE_DISTANCE: 1.1,
    MAX_ZONE_DISTANCE: 2.8,

    // Fairness (Sekunden)
    MIN_REACTION_SECONDS: 0.45,
    LATE_TAP_FORGIVENESS_SECONDS: 0.07,
    PASS_BUFFER_SECONDS: 0.09,

    // Scoring: erster Perfekt +2, jeder weitere +1 mehr, Deckel +5.
    PERFECT_BASE_SCORE: 2,
    PERFECT_MAX_SCORE: 5,

    // Twists
    MAX_ACTIVE_TWISTS: 2,
    TWIST_PROBABILITY: 0.45,
    PULSE_SPEED: 5,
    PULSE_MIN_SHARE: 0.62,
    DRIFT_SPEED: 0.35,
    GHOST_BLINK_SPEED: 1.6,
    GHOST_VISIBLE_SHARE: 0.62,
    FAKE_MIN_DISTANCE: 0.55,
    CHAIN_LENGTH: 1,
    CHAIN_MIN_DISTANCE: 1.0,
    CHAIN_MAX_DISTANCE: 1.8,

    DEATH_FREEZE_SECONDS: 0.5,
    DEATH_FALL_SECONDS: 1.0,
    RESTART_LOCK_SECONDS: 0.55
  };

  var Phase = { READY: "READY", RUNNING: "RUNNING", DYING: "DYING", OVER: "OVER" };

  /** Reihenfolge = Twist.entries in Kotlin. */
  var TWISTS = ["PULSE", "DRIFT", "GHOST", "FAKE", "CHAIN"];

  /** Nie zusammen aktive Twist-Paare. */
  var FORBIDDEN_COMBOS = [["GHOST", "FAKE"]];

  /** Ab welchem Score ein Twist ins Spiel kommt. */
  function unlockScore(twist) {
    switch (twist) {
      case "PULSE": return 5;
      case "DRIFT": return 10;
      case "GHOST": return 15;
      case "FAKE": return 20;
      case "CHAIN": return 25;
    }
    return Infinity;
  }

  function TimingGame(rng) {
    this.random = rng || new Rng(null);

    this.phase = Phase.READY;
    this.angle = 0;
    this.direction = 1;
    this.zoneCenter = 1.8;
    this.zoneHalfWidth = C.BASE_ZONE_HALF;
    this.score = 0;
    this.hits = 0;
    this.perfectStreak = 0;
    this.lastHitPoints = 0;
    this.elapsed = 0;
    this.timeSinceHit = 99;
    this.lastHitPerfect = false;
    this.activeTwists = new Set();
    this.hasFakeZone = false;
    this.fakeZoneCenter = 0;
    this.chainRemaining = 0;

    this._driftSign = 1;
    this._announcedTwists = new Set();
    this._pendingEvents = [];

    /** Nur für Tests: erzwingt ein festes Twist-Set (Array) oder null. */
    this.twistOverride = null;
  }

  /** Relative Position des Punkts zur Zone: negativ = davor, 0 = Mitte. */
  TimingGame.prototype.relativeToZone = function () {
    return wrapToPi(this.direction * (this.angle - this.zoneCenter));
  };

  /** Effektive halbe Zonenbreite — pulsiert, wenn PULSE aktiv ist. */
  TimingGame.prototype.effectiveZoneHalf = function () {
    if (!this.activeTwists.has("PULSE")) return this.zoneHalfWidth;
    var pulse = C.PULSE_MIN_SHARE + (1 - C.PULSE_MIN_SHARE) *
      (0.5 + 0.5 * Math.sin(this.elapsed * C.PULSE_SPEED));
    return this.zoneHalfWidth * pulse;
  };

  /** Steht der Punkt gerade in der (effektiven) Zielzone? */
  TimingGame.prototype.isInZone = function () {
    return Math.abs(this.relativeToZone()) <= this.effectiveZoneHalf();
  };

  /**
   * Halbe Breite des PERFEKT-Fensters — genau die, die der Renderer als
   * hellen Kern zeichnet. Die Aufrundung auf ein halbes Segment stammt aus
   * der Zeichnung: Die Bahn besteht aus TRACK_SEGMENTS Bloecken, ein
   * schmalerer Kern liesse sich nicht darstellen. Frueher rundete nur das
   * Bild auf, gewertet wurde ohne — unter PULS war der leuchtende Kern
   * dadurch bis zu 61 % breiter als das Fenster, das er versprach. Jetzt
   * gilt die Aufrundung fuer beide.
   */
  TimingGame.prototype.perfectHalf = function () {
    var half = this.effectiveZoneHalf();
    return Math.min(half, Math.max(half * C.PERFECT_SHARE, C.SEGMENT_HALF));
  };

  /**
   * Halbe Breite der Fallen-Zone — dieselbe wie die der echten Zone,
   * Pulsieren eingeschlossen. Vorher stand die Falle still, waehrend die
   * Zone atmete, und war dadurch fast immer die breitere von beiden.
   */
  TimingGame.prototype.fakeZoneHalf = function () {
    return this.effectiveZoneHalf();
  };

  /** Ist der Punkt gerade sichtbar? Blinkt nur im GHOST-Twist. */
  TimingGame.prototype.isDotVisible = function () {
    return this.phase !== Phase.RUNNING || !this.activeTwists.has("GHOST") ||
      (this.elapsed * C.GHOST_BLINK_SPEED) % 1 < C.GHOST_VISIBLE_SHARE;
  };

  TimingGame.prototype.currentSpeed = function () {
    return Math.min(C.BASE_SPEED + this.hits * C.SPEED_PER_HIT, C.MAX_SPEED);
  };

  /**
   * Verarbeitet einen Tap. In READY startet er den Lauf, in RUNNING ist
   * er der Stopp-Versuch, in OVER (nach kurzer Sperre gegen Wut-Taps)
   * geht es zurück in den READY-Zustand. Events: Strings bzw.
   * {type:"TwistUnlocked", twist}.
   */
  TimingGame.prototype.tap = function () {
    var event = null;
    switch (this.phase) {
      case Phase.READY:
        this.phase = Phase.RUNNING;
        this.elapsed = 0;
        this._spawnZone();
        event = "Started";
        break;
      case Phase.RUNNING: {
        var rel = this.relativeToZone();
        var half = this.effectiveZoneHalf();
        if (Math.abs(rel) <= half) {
          var perfect = Math.abs(rel) <= this.perfectHalf();
          this._registerHit(perfect);
          event = perfect ? "PerfectHit" : "Hit";
        } else if (rel > half &&
                   rel <= half + this.currentSpeed() * C.LATE_TAP_FORGIVENESS_SECONDS) {
          // Touch-Latenz-Gnade auf der Auslauf-Seite.
          this._registerHit(false);
          event = "Hit";
        } else {
          this._die();
          event = "Died";
        }
        break;
      }
      case Phase.DYING:
        event = null;
        break;
      case Phase.OVER:
        if (this.elapsed >= C.RESTART_LOCK_SECONDS) {
          this.reset();
          this.phase = Phase.RUNNING;
          this.elapsed = 0;
          this._spawnZone();
          event = "Started";
        }
        break;
    }
    if (event !== null) this._pendingEvents.push(event);
    return event;
  };

  /**
   * Ersetzt die Zufallsquelle — vor jedem Daily-Lauf mit dem Tages-Seed
   * aufgerufen. `null` stellt echten Zufall wieder her.
   */
  TimingGame.prototype.reseed = function (seed) {
    this.random = new Rng(seed === null || seed === undefined ? null : seed);
  };

  /** Setzt alles auf den READY-Zustand zurück (Rekord bleibt beim Store). */
  TimingGame.prototype.reset = function () {
    this.phase = Phase.READY;
    this.angle = 0;
    this.direction = 1;
    this.zoneCenter = 1.8;
    this.zoneHalfWidth = C.BASE_ZONE_HALF;
    this.score = 0;
    this.hits = 0;
    this.perfectStreak = 0;
    this.lastHitPoints = 0;
    this.elapsed = 0;
    this.timeSinceHit = 99;
    this.lastHitPerfect = false;
    this.activeTwists.clear();
    this.hasFakeZone = false;
    this.chainRemaining = 0;
    this._announcedTwists.clear();
    this._pendingEvents.length = 0;
  };

  /** Schreibt einen Frame fort und liefert die dabei aufgetretenen Events. */
  TimingGame.prototype.update = function (deltaSeconds) {
    var dt = Math.min(Math.max(deltaSeconds, 0), C.MAX_DELTA);
    this.elapsed += dt;
    this.timeSinceHit += dt;
    var events = this._pendingEvents.slice();
    this._pendingEvents.length = 0;

    switch (this.phase) {
      case Phase.READY:
        this.angle = wrapTwoPi(this.angle + this.direction * C.READY_SPEED * dt);
        break;
      case Phase.RUNNING:
        this.angle = wrapTwoPi(this.angle + this.direction * this.currentSpeed() * dt);
        if (this.activeTwists.has("DRIFT")) {
          this.zoneCenter = wrapTwoPi(
            this.zoneCenter + this.direction * this._driftSign * C.DRIFT_SPEED * dt
          );
        }
        // Zone ohne Tap überfahren → vorbei (zeitbasierter Puffer).
        if (this.relativeToZone() >
            this.zoneHalfWidth + this.currentSpeed() * C.PASS_BUFFER_SECONDS) {
          this._die();
          events.push("Died");
        }
        break;
      case Phase.DYING:
        if (this.elapsed >= C.DEATH_FREEZE_SECONDS + C.DEATH_FALL_SECONDS) {
          this.phase = Phase.OVER;
          this.elapsed = 0;
          events.push("Settled");
        }
        break;
      case Phase.OVER:
        break;
    }
    return events;
  };

  TimingGame.prototype._registerHit = function (perfect) {
    this.hits++;
    if (perfect) {
      this.perfectStreak++;
      this.lastHitPoints = Math.min(
        C.PERFECT_BASE_SCORE - 1 + this.perfectStreak,
        C.PERFECT_MAX_SCORE
      );
    } else {
      this.perfectStreak = 0;
      this.lastHitPoints = 1;
    }
    this.score += this.lastHitPoints;
    this.timeSinceHit = 0;
    this.lastHitPerfect = perfect;
    this.zoneHalfWidth = Math.max(
      C.BASE_ZONE_HALF - this.hits * C.ZONE_SHRINK_PER_HIT,
      C.MIN_ZONE_HALF
    );

    if (this.chainRemaining > 0) {
      // Ketten-Zone: gleiche Richtung, die nächste kommt sofort.
      this.chainRemaining--;
      this.hasFakeZone = false;
      this.activeTwists.delete("FAKE");
      this._spawnChainZone();
      this._pendingEvents.push("ChainNext");
    } else {
      this.direction = -this.direction;
      this._spawnZone();
    }
  };

  TimingGame.prototype._spawnZone = function () {
    // Mindestabstand zeitbasiert: immer MIN_REACTION_SECONDS bis zur Zone.
    var minDistance = Math.max(C.MIN_ZONE_DISTANCE,
      this.currentSpeed() * C.MIN_REACTION_SECONDS);
    var maxDistance = Math.max(C.MAX_ZONE_DISTANCE, minDistance + 0.4);
    var distance = minDistance + this.random.nextFloat() * (maxDistance - minDistance);
    this.zoneCenter = wrapTwoPi(this.angle + this.direction * distance);
    this._chooseTwists();

    this._driftSign = this.random.nextBoolean() ? 1 : -1;
    this.chainRemaining = this.activeTwists.has("CHAIN") ? C.CHAIN_LENGTH : 0;

    this.hasFakeZone = false;
    if (this.activeTwists.has("FAKE")) {
      var maxFakeDistance = distance - this.zoneHalfWidth * 3;
      if (maxFakeDistance > C.FAKE_MIN_DISTANCE) {
        var fakeDistance = C.FAKE_MIN_DISTANCE +
          this.random.nextFloat() * (maxFakeDistance - C.FAKE_MIN_DISTANCE);
        this.fakeZoneCenter = wrapTwoPi(this.angle + this.direction * fakeDistance);
        this.hasFakeZone = true;
      }
    }
  };

  /** Folge-Zone einer Kette: näher dran, keine neue Twist-Auswahl. */
  TimingGame.prototype._spawnChainZone = function () {
    var minDistance = Math.max(C.CHAIN_MIN_DISTANCE,
      this.currentSpeed() * C.MIN_REACTION_SECONDS);
    var maxDistance = Math.max(C.CHAIN_MAX_DISTANCE, minDistance + 0.3);
    var distance = minDistance + this.random.nextFloat() * (maxDistance - minDistance);
    this.zoneCenter = wrapTwoPi(this.angle + this.direction * distance);
  };

  TimingGame.prototype._chooseTwists = function () {
    this.activeTwists.clear();
    if (this.twistOverride !== null) {
      for (var i = 0; i < this.twistOverride.length; i++) {
        this.activeTwists.add(this.twistOverride[i]);
      }
      return;
    }

    var self = this;
    var unlocked = TWISTS.filter(function (t) { return self.score >= unlockScore(t); });

    // Ein frisch freigeschalteter Twist wird garantiert gezeigt
    // und einmalig angekündigt.
    var fresh = null;
    for (var f = 0; f < unlocked.length; f++) {
      if (!this._announcedTwists.has(unlocked[f])) { fresh = unlocked[f]; break; }
    }
    if (fresh !== null) {
      this.activeTwists.add(fresh);
      this._announcedTwists.add(fresh);
      this._pendingEvents.push({ type: "TwistUnlocked", twist: fresh });
    }

    var shuffled = this.random.shuffled(unlocked);
    for (var k = 0; k < shuffled.length; k++) {
      var twist = shuffled[k];
      if (this.activeTwists.size >= C.MAX_ACTIVE_TWISTS) break;
      if (this.activeTwists.has(twist)) continue;
      if (this._conflictsWithActive(twist)) continue;
      if (this.random.nextFloat() < C.TWIST_PROBABILITY) {
        this.activeTwists.add(twist);
      }
    }
  };

  /** GEIST + FALLE nie zusammen (siehe Kotlin-Kommentar). */
  TimingGame.prototype._conflictsWithActive = function (candidate) {
    var active = this.activeTwists;
    return FORBIDDEN_COMBOS.some(function (pair) {
      if (pair.indexOf(candidate) < 0) return false;
      var hit = false;
      active.forEach(function (t) {
        if (t !== candidate && pair.indexOf(t) >= 0) hit = true;
      });
      return hit;
    });
  };

  TimingGame.prototype._die = function () {
    if (this.phase !== Phase.RUNNING) return;
    this.phase = Phase.DYING;
    this.elapsed = 0;
  };

  TimingGame.Phase = Phase;
  TimingGame.TWISTS = TWISTS;
  TimingGame.C = C;
  TimingGame.unlockScore = unlockScore;
  TimingGame.wrapToPi = wrapToPi;
  TimingGame.wrapTwoPi = wrapTwoPi;
  TimingGame.Rng = Rng;

  global.TimingGame = TimingGame;
  if (typeof module !== "undefined" && module.exports) {
    module.exports = TimingGame;
  }
})(typeof globalThis !== "undefined" ? globalThis : this);

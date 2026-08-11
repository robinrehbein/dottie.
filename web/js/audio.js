/*
 * Abspiel-Schicht für ChipSynth via WebAudio (Pendant zu GameAudio.kt)
 * plus Haptik via navigator.vibrate (Pendant zu GameHaptics.kt — die
 * Muster entsprechen den VibrationEffect-Timings; iOS ignoriert
 * navigator.vibrate still, Android-Browser vibrieren).
 *
 * iOS-Besonderheit: Der AudioContext darf erst nach der ersten
 * User-Interaktion laufen — unlock() wird beim ersten Tap gerufen.
 */
(function (global) {
  "use strict";

  function GameAudio() {
    this.muted = false;
    this.ctx = null;
    this.buffers = null;
  }

  /** Beim ersten Tap rufen: erzeugt/entsperrt den AudioContext. */
  GameAudio.prototype.unlock = function () {
    var AC = global.AudioContext || global.webkitAudioContext;
    if (!AC) return;
    if (!this.ctx) {
      this.ctx = new AC();
      this._build();
    }
    if (this.ctx.state === "suspended") {
      this.ctx.resume();
    }
  };

  GameAudio.prototype._build = function () {
    var effects = global.ChipSynth.effects();
    this.buffers = {};
    for (var name in effects) {
      if (!Object.prototype.hasOwnProperty.call(effects, name)) continue;
      var samples = effects[name];
      var buf = this.ctx.createBuffer(1, samples.length, global.ChipSynth.SAMPLE_RATE);
      buf.getChannelData(0).set(samples);
      this.buffers[name] = buf;
    }
  };

  GameAudio.prototype._play = function (name, rate) {
    if (this.muted || !this.ctx || !this.buffers || !this.buffers[name]) return;
    if (this.ctx.state !== "running") return;
    var src = this.ctx.createBufferSource();
    src.buffer = this.buffers[name];
    // Wie SoundPool: Rate hart auf [0.5, 2.0] begrenzt.
    src.playbackRate.value = Math.min(2, Math.max(0.5, rate || 1));
    src.connect(this.ctx.destination);
    src.start();
  };

  GameAudio.prototype.start = function () { this._play("start"); };

  /** Treffer-Blip; die Tonhöhe klettert pro 5er-Stufe eine Pentatonik hoch. */
  GameAudio.prototype.hit = function (score) {
    this._play("hit", global.ChipSynth.hitRate(score));
  };

  /** Münz-Sound; jede Serien-Stufe klingt zwei Halbtöne höher. */
  GameAudio.prototype.perfect = function (streak) {
    this._play("perfect", global.ChipSynth.perfectRate(streak));
  };

  GameAudio.prototype.chain = function () { this._play("chain"); };
  GameAudio.prototype.unlockSound = function () { this._play("unlock"); };
  GameAudio.prototype.death = function () { this._play("death"); };
  GameAudio.prototype.thud = function () { this._play("thud"); };
  GameAudio.prototype.newRecord = function () { this._play("record"); };

  // ===== Haptik (GameHaptics.kt: Timings ohne Amplituden — Web kann
  // keine Amplitudensteuerung, nur An/Aus-Muster) =====
  var Haptics = {
    _vibrate: function (pattern) {
      if (typeof navigator !== "undefined" && navigator.vibrate) {
        try { navigator.vibrate(pattern); } catch (e) { /* still bleiben */ }
      }
    },
    /** Kurzer, satter Blip bei einem Treffer in der Zone. */
    score: function () { this._vibrate(28); },
    /** Doppel-Tick für einen perfekten Treffer. */
    perfect: function () { this._vibrate([20, 40, 35]); },
    /** Harter Schlag beim Aufprall — der Rage-Moment. */
    death: function () { this._vibrate([70, 40, 130]); },
    /** Dumpfer Thud, wenn der Punkt nach dem Aus am Boden aufschlägt. */
    thud: function () { this._vibrate(50); },
    /** Aufsteigende Fanfare bei Twist/Stufe. */
    unlock: function () { this._vibrate([25, 45, 25, 45, 25, 45, 90]); },
    /** Feier-Muster für einen neuen Rekord. */
    newRecord: function () { this._vibrate([40, 60, 40, 60, 80]); }
  };

  global.GameAudio = GameAudio;
  global.Haptics = Haptics;
  if (typeof module !== "undefined" && module.exports) {
    module.exports = { GameAudio: GameAudio, Haptics: Haptics };
  }
})(typeof globalThis !== "undefined" ? globalThis : this);

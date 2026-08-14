/*
 * Port von core/.../ChipSynth.kt: Chiptune-Soundeffekte im NES-Stil,
 * Rechteckwellen und Rauschen, zur Laufzeit erzeugt — keine Audio-Assets.
 * Alle Hüllkurven sind aus der Kotlin-Quelle übernommen; Frequenzen und
 * Dauern der einzelnen Klänge stehen seit den Ton-Sets in sounds.js
 * (Port von SoundSet.kt). Liefert Float32Array-Mono-Samples in [-1, 1]
 * bei 22050 Hz; die Abspiel-Schicht (audio.js) packt sie in
 * WebAudio-AudioBuffer.
 */
(function (global) {
  "use strict";

  var SAMPLE_RATE = 22050;

  /** Attack-Rampe (~1,5 ms) gegen Knackser am Ton-Anfang. */
  var ATTACK_SAMPLES = 32;

  /** Lineares Fade-Out (~3 ms) gegen Knackser am Ton-Ende. */
  var FADE_OUT_SAMPLES = 64;

  /** Attack-Rampe, exponentielles Abklingen und End-Fade in einem. */
  function envelope(index, total, t, decay) {
    var attack = index < ATTACK_SAMPLES ? index / ATTACK_SAMPLES : 1;
    var remaining = total - index;
    var fadeOut = remaining < FADE_OUT_SAMPLES ? remaining / FADE_OUT_SAMPLES : 1;
    return attack * fadeOut * Math.exp(-decay * t);
  }

  /** Rendert eine Rechteckwelle; voice(progress) => [freq, duty]. */
  function render(seconds, volume, decay, voice) {
    var n = Math.floor(seconds * SAMPLE_RATE);
    var out = new Float32Array(n);
    var phase = 0;
    for (var i = 0; i < n; i++) {
      var t = i / SAMPLE_RATE;
      var fd = voice(n > 1 ? i / (n - 1) : 0);
      var freq = fd[0];
      var duty = fd[1];
      var wave = phase < duty ? 1 : -1;
      out[i] = wave * volume * envelope(i, n, t, decay);
      phase += freq / SAMPLE_RATE;
      if (phase >= 1) phase -= 1;
    }
    return out;
  }

  /** Rechteckwelle mit fester Frequenz. decay = Abklingrate pro Sekunde. */
  function square(freqHz, seconds, volume, decay, duty) {
    if (volume === undefined) volume = 0.4;
    if (decay === undefined) decay = 14;
    if (duty === undefined) duty = 0.5;
    return render(seconds, volume, decay, function () { return [freqHz, duty]; });
  }

  /** Rechteckwelle, deren Frequenz linear von fromHz nach toHz gleitet. */
  function sweep(fromHz, toHz, seconds, volume, decay) {
    if (volume === undefined) volume = 0.4;
    if (decay === undefined) decay = 5;
    return render(seconds, volume, decay, function (progress) {
      return [fromHz + (toHz - fromHz) * progress, 0.5];
    });
  }

  /** Rausch-Burst (deterministischer Seed wie in Kotlin: Random(42)). */
  function noise(seconds, volume, decay) {
    if (volume === undefined) volume = 0.3;
    if (decay === undefined) decay = 18;
    // mulberry32 mit Seed 42 — deterministisch, damit der Sound stabil ist.
    var s = 42 >>> 0;
    function rnd() {
      s = (s + 0x6d2b79f5) >>> 0;
      var z = s;
      z = Math.imul(z ^ (z >>> 15), z | 1);
      z ^= z + Math.imul(z ^ (z >>> 7), z | 61);
      return ((z ^ (z >>> 14)) >>> 0) / 4294967296;
    }
    var n = Math.floor(seconds * SAMPLE_RATE);
    var out = new Float32Array(n);
    for (var i = 0; i < n; i++) {
      var t = i / SAMPLE_RATE;
      out[i] = (rnd() * 2 - 1) * volume * envelope(i, n, t, decay);
    }
    return out;
  }

  /** Hängt mehrere Klänge nahtlos aneinander. */
  function concat() {
    var total = 0;
    for (var i = 0; i < arguments.length; i++) total += arguments[i].length;
    var out = new Float32Array(total);
    var offset = 0;
    for (var k = 0; k < arguments.length; k++) {
      out.set(arguments[k], offset);
      offset += arguments[k].length;
    }
    return out;
  }

  /** Mischt zwei Klänge übereinander (Summe, hart auf [-1, 1] begrenzt). */
  function mix(a, b) {
    var n = Math.max(a.length, b.length);
    var out = new Float32Array(n);
    for (var i = 0; i < n; i++) {
      var sum = (i < a.length ? a[i] : 0) + (i < b.length ? b[i] : 0);
      out[i] = Math.min(1, Math.max(-1, sum));
    }
    return out;
  }

  /**
   * Abspielrate für den Treffer-Blip: klettert innerhalb jeder 5er-Stufe
   * eine Pentatonik hinauf (0, 2, 4, 7, 9 Halbtöne).
   */
  function hitRate(score) {
    var pentatonic = [0, 2, 4, 7, 9];
    var semitones = pentatonic[((score % 5) + 5) % 5];
    return Math.pow(2, semitones / 12);
  }

  /** Abspielrate für den Perfekt-Sound: +2 Halbtöne pro Serien-Stufe. */
  function perfectRate(streak) {
    var s = Math.min(4, Math.max(0, streak - 1));
    return Math.pow(2, (s * 2) / 12);
  }

  /**
   * Ein Ereignis-Klang aus der Tabelle (sounds.js): Töne hintereinander,
   * Rauschen darüber. Die einzige Stelle, an der aus einer Voice Samples
   * werden — Kotlin und Swift tun exakt dasselbe, damit ein neues Ton-Set
   * nirgends nachgebaut werden muss.
   */
  function renderVoice(voice) {
    var parts = voice.tones.map(function (t) {
      return t.fromHz === t.toHz
        ? square(t.fromHz, t.seconds, t.volume, t.decay, t.duty)
        : sweep(t.fromHz, t.toHz, t.seconds, t.volume, t.decay);
    });
    var tones = concat.apply(null, parts);
    if (!voice.noise) return tones;
    return mix(tones, noise(voice.noise.seconds, voice.noise.volume, voice.noise.decay));
  }

  /**
   * Alle Spiel-Sounds eines Ton-Sets, benannt — identisch zu
   * ChipSynth.effects(set) in Kotlin. Ohne Angabe das Standard-Set, also
   * der Bestand.
   */
  function effects(set) {
    var gewaehlt = set || global.DotSound.SETS[0];
    var out = {};
    global.DotSound.EVENTS.forEach(function (event) {
      out[event] = renderVoice(global.DotSound.voice(gewaehlt, event));
    });
    return out;
  }

  var ChipSynth = {
    SAMPLE_RATE: SAMPLE_RATE,
    square: square,
    sweep: sweep,
    noise: noise,
    concat: concat,
    mix: mix,
    hitRate: hitRate,
    perfectRate: perfectRate,
    renderVoice: renderVoice,
    effects: effects
  };

  global.ChipSynth = ChipSynth;
  if (typeof module !== "undefined" && module.exports) {
    module.exports = ChipSynth;
  }
})(typeof globalThis !== "undefined" ? globalThis : this);

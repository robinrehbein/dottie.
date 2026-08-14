/*
 * Port von SoundSet.kt (:core): das Klangwerk aller Ton-Sets — dieselben
 * acht Ereignisse in drei Charakteren, als reine Zahlentabelle.
 *
 * Ein Ton-Set entscheidet nie ueber einen Treffer: Jedes Set meldet jedes
 * Ereignis, nur eben anders. Genau deshalb darf es verdient werden und
 * die Zonenbreite nicht.
 *
 * Die Klaenge sind Daten, kein Synthese-Code: fromHz/toHz, Dauer,
 * Lautstaerke, Abklingrate und Pulsbreite je Ton, dazu ein optionaler
 * Rauschanteil. synth.js wirft dieselbe Tabelle in denselben Baukasten
 * wie Kotlin und Swift — Rechteck, Gleitton, Rauschen.
 */
(function (global) {
  "use strict";

  /** Grenzen, die jedes Set einhalten muss (siehe SoundSetTest in :core). */
  var MIN_HZ = 50;
  var MAX_HZ = 2500;
  var MIN_SECONDS = 0.02;
  var MAX_SECONDS = 0.8;
  var MIN_VOLUME = 0.05;
  var MAX_VOLUME = 0.6;
  var MAX_DECAY = 40;

  /**
   * Wie weit zwei Sets bei demselben Ereignis mindestens auseinander
   * liegen — als Frequenzverhaeltnis des ersten Tons, also eine Quarte.
   */
  var MIN_PITCH_RATIO = 1.25;

  /** Die Ereignisse in der Reihenfolge von SoundEvent (:core). */
  var EVENTS = [
    "start", "hit", "perfect", "chain", "unlock", "record", "death", "thud"
  ];

  /** Eine Rechteckwelle fester Hoehe. */
  function tone(hz, seconds, volume, decay, duty) {
    return {
      fromHz: hz,
      toHz: hz,
      seconds: seconds,
      volume: volume,
      decay: decay,
      duty: duty === undefined ? 0.5 : duty
    };
  }

  /** Ein Gleitton; die Pulsbreite steht fest, weil sweep keine kennt. */
  function glide(fromHz, toHz, seconds, volume, decay) {
    return {
      fromHz: fromHz,
      toHz: toHz,
      seconds: seconds,
      volume: volume,
      decay: decay,
      duty: 0.5
    };
  }

  function noise(seconds, volume, decay) {
    return { seconds: seconds, volume: volume, decay: decay };
  }

  /** Toene hintereinander, Rauschen darueber (null = keins). */
  function voice(tones, rausch) {
    return { tones: tones, noise: rausch || null };
  }

  var SETS = [
    {
      // Der Bestand. Jeder Wert stammt aus ChipSynth.effects() vor der
      // Einfuehrung der Ton-Sets: NES-Blips, mittlere Lage, volle
      // Pulsbreite, kurzes perkussives Abklingen.
      name: "KLASSIK", titleKey: "sound_klassik", hintKey: null,
      voices: {
        start: voice([tone(440, 0.06, 0.22, 20)]),
        hit: voice([tone(660, 0.07, 0.38, 18)]),
        perfect: voice([tone(988, 0.06, 0.32, 12), tone(1319, 0.16, 0.38, 9)]),
        chain: voice([tone(880, 0.05, 0.3, 20), tone(1175, 0.07, 0.3, 18)]),
        unlock: voice([
          tone(523, 0.07, 0.3, 14), tone(659, 0.07, 0.3, 14),
          tone(784, 0.07, 0.3, 14), tone(1046, 0.2, 0.34, 8)
        ]),
        record: voice([
          tone(784, 0.09, 0.32, 10), tone(1046, 0.09, 0.32, 10),
          tone(1319, 0.09, 0.32, 10), tone(1568, 0.3, 0.36, 6)
        ]),
        death: voice([glide(700, 90, 0.35, 0.42, 4)], noise(0.12, 0.32, 22)),
        thud: voice([tone(100, 0.09, 0.5, 14)])
      }
    },
    {
      // Glocke: weich und rund. Eine Oktave ueber dem Bestand, volle
      // Pulsbreite, langes Nachklingen — statt zu tickern, singt das Set.
      // Kein Ereignis traegt Rauschen, auch der Tod nicht: Hier
      // zerbricht nichts, hier geht das Licht aus.
      name: "GLOCKE", titleKey: "sound_glocke", hintKey: "sound_hint_glocke",
      voices: {
        start: voice([tone(659, 0.16, 0.18, 5)]),
        hit: voice([tone(988, 0.2, 0.26, 5)]),
        perfect: voice([tone(1319, 0.14, 0.24, 4), tone(1976, 0.3, 0.26, 3)]),
        chain: voice([tone(1568, 0.12, 0.22, 5), tone(2093, 0.18, 0.22, 4)]),
        // Drei Toene statt vier: Eine Fanfare, die nachklingt, braucht
        // weniger Stufen, sonst verwischen sie ineinander.
        unlock: voice([
          tone(784, 0.14, 0.2, 4), tone(1047, 0.14, 0.2, 4), tone(1568, 0.36, 0.24, 2.5)
        ]),
        record: voice([
          tone(1047, 0.16, 0.22, 3), tone(1319, 0.16, 0.22, 3), tone(2093, 0.5, 0.26, 2)
        ]),
        death: voice([glide(932, 294, 0.55, 0.28, 2.5)]),
        thud: voice([tone(220, 0.26, 0.3, 5)])
      }
    },
    {
      // Amboss: hart, tief und sparsam. Jeder Ton unter 450 Hz, jede
      // Pulsbreite hoechstens ein Viertel, nichts hallt nach. Treffer und
      // Tod bekommen Rauschen — das macht aus dem Ton einen Schlag.
      name: "AMBOSS", titleKey: "sound_amboss", hintKey: "sound_hint_amboss",
      voices: {
        start: voice([tone(110, 0.05, 0.3, 30, 0.125)]),
        hit: voice([tone(220, 0.05, 0.4, 34, 0.125)], noise(0.03, 0.18, 40)),
        perfect: voice([tone(330, 0.05, 0.38, 30, 0.25), tone(440, 0.1, 0.42, 22, 0.25)]),
        chain: voice([tone(262, 0.04, 0.34, 36, 0.125), tone(392, 0.05, 0.34, 32, 0.125)]),
        unlock: voice([
          tone(147, 0.06, 0.36, 22, 0.25), tone(220, 0.06, 0.36, 22, 0.25),
          tone(294, 0.16, 0.4, 12, 0.25)
        ]),
        record: voice([
          tone(196, 0.07, 0.38, 18, 0.25), tone(294, 0.07, 0.38, 18, 0.25),
          tone(392, 0.26, 0.42, 9, 0.25)
        ]),
        death: voice([glide(300, 60, 0.3, 0.44, 6)], noise(0.18, 0.4, 12)),
        thud: voice([tone(70, 0.12, 0.55, 10, 0.25)])
      }
    }
  ];

  /** Ton-Set zu einem gespeicherten Namen, KLASSIK als Fallback. */
  function fromName(name) {
    for (var i = 0; i < SETS.length; i++) {
      if (SETS[i].name === name) return SETS[i];
    }
    return SETS[0];
  }

  /** Der Klang eines Ereignisses — der Weg, den alle Ports gehen. */
  function voiceFor(set, event) {
    return set.voices[event];
  }

  /**
   * Drei Balkenhoehen (0..1) fuer die Vorschau-Kachel: Treffer, Perfekt
   * und Rekord. Ein Ton-Set hat kein Bild — die Kachel zeigt, wo das Set
   * liegt. Gemessen wird in Oktaven, nicht in Hertz: Zwischen 200 und
   * 400 liegt fuers Ohr derselbe Schritt wie zwischen 1000 und 2000.
   */
  function chips(set) {
    var spanne = Math.log(MAX_HZ / MIN_HZ) / Math.LN2;
    return ["hit", "perfect", "record"].map(function (event) {
      var hz = set.voices[event].tones[0].fromHz;
      var anteil = (Math.log(hz / MIN_HZ) / Math.LN2) / spanne;
      return Math.max(0, Math.min(1, anteil));
    });
  }

  /**
   * Ton-Sets werden verdient wie Kulissen, aber an eigenen Achsen und an
   * Schwellen, auf denen sonst nichts liegt: Die Glocke haengt am Koennen,
   * der Amboss an der Ausdauer.
   */
  function isUnlocked(set, rawStats) {
    var stats = rawStats || {};
    switch (set.name) {
      case "KLASSIK": return true;
      case "GLOCKE": return (stats.bestPerfectStreak || 0) >= 20;
      case "AMBOSS": return (stats.totalScore || 0) >= 25000;
    }
    return false;
  }

  function unlockedCount(stats) {
    return SETS.filter(function (s) { return isUnlocked(s, stats); }).length;
  }

  var DotSound = {
    SETS: SETS,
    EVENTS: EVENTS,
    MIN_HZ: MIN_HZ,
    MAX_HZ: MAX_HZ,
    MIN_SECONDS: MIN_SECONDS,
    MAX_SECONDS: MAX_SECONDS,
    MIN_VOLUME: MIN_VOLUME,
    MAX_VOLUME: MAX_VOLUME,
    MAX_DECAY: MAX_DECAY,
    MIN_PITCH_RATIO: MIN_PITCH_RATIO,
    fromName: fromName,
    voice: voiceFor,
    chips: chips,
    isUnlocked: isUnlocked,
    unlockedCount: unlockedCount
  };

  global.DotSound = DotSound;
  if (typeof module !== "undefined" && module.exports) {
    module.exports = DotSound;
  }
})(typeof globalThis !== "undefined" ? globalThis : this);

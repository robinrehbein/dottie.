/*
 * Port von ScoreStore.kt: Rekord, Daily-Stand, Bestleistungen, Skin-Wahl
 * und Ton an/aus — in localStorage statt SharedPreferences. Gleiche
 * Semantik (submitRun, submitDailyRun, Streak-Preview) wie am Phone.
 */
(function (global) {
  "use strict";

  var PREFIX = "dottie_";

  // localStorage kann im Private-Mode oder in Node fehlen/geworfen werden —
  // dann fällt der Store still auf ein In-Memory-Objekt zurück.
  var backend;
  try {
    if (typeof localStorage !== "undefined") {
      localStorage.setItem(PREFIX + "probe", "1");
      localStorage.removeItem(PREFIX + "probe");
      backend = localStorage;
    }
  } catch (e) { /* fällt unten auf Memory zurück */ }
  if (!backend) {
    var mem = {};
    backend = {
      getItem: function (k) { return Object.prototype.hasOwnProperty.call(mem, k) ? mem[k] : null; },
      setItem: function (k, v) { mem[k] = String(v); },
      removeItem: function (k) { delete mem[k]; }
    };
  }

  function getInt(key) {
    var v = parseInt(backend.getItem(PREFIX + key), 10);
    return isNaN(v) ? 0 : v;
  }
  function setInt(key, value) { backend.setItem(PREFIX + key, String(value)); }
  function getBool(key) { return backend.getItem(PREFIX + key) === "1"; }
  function setBool(key, value) { backend.setItem(PREFIX + key, value ? "1" : "0"); }

  /** Anzahl gesetzter Bits — aus der Monatsmaske wird so eine Anzahl. */
  function bitCount(mask) {
    var n = 0;
    for (var i = 0; i < 32; i++) {
      if (mask & (1 << i)) n++;
    }
    return n;
  }

  var ScoreStore = {
    get bestScore() { return getInt("best_score"); },
    get runCount() { return getInt("run_count"); },

    /** Beste jemals erreichte Perfekt-Serie (für Skin-Freischaltungen). */
    get bestPerfectStreak() { return getInt("best_perfect_streak"); },

    // ===== Ausdauer-Achsen (siehe SkinStats in :core) =====

    /** Summe aller je erspielten Punkte. */
    get totalScore() { return getInt("total_score"); },

    /** Anzahl Kalendertage mit mindestens einem Lauf. */
    get daysPlayed() { return getInt("days_played"); },

    /** Letzter gespielter Kalendertag als Epoch-Day, 0 = noch nie. */
    get lastPlayedDay() { return getInt("last_played_day"); },

    /** Bitmaske der Monate mit mindestens einem Lauf (Bit 0 = Januar). */
    get monthsPlayed() { return getInt("months_played"); },

    /**
     * Bitmaske der verdienten Saison-Skins (siehe DotSkin.SEASONS). Wird
     * nur gesetzt, nie gelöscht: Verdient ist verdient, auch im nächsten
     * Monat.
     */
    get seasonEarned() { return getInt("season_earned"); },

    /**
     * Gönner-Paket gekauft? Im Web immer false — es gibt hier kein
     * Billing. Die Gönner-Skins bleiben sichtbar, aber gesperrt, mit dem
     * Hinweis, dass es sie nur in der App gibt.
     */
    get patronOwned() { return false; },

    /** Ton an/aus — überlebt Neustarts. */
    get soundMuted() { return getBool("sound_muted"); },
    set soundMuted(v) { setBool("sound_muted", v); },

    /** Gewählter Punkt-Skin (Name), KLASSIK als Fallback via DotSkin.fromName. */
    get selectedSkinName() { return backend.getItem(PREFIX + "selected_skin"); },
    set selectedSkinName(name) { backend.setItem(PREFIX + "selected_skin", name); },

    /**
     * Gewählte Kulisse (Name), WIESE als Fallback via DotScene.fromName.
     * Wie die Skin-Wahl eine Entscheidung, keine Bestleistung — im Web
     * gibt es keinen Abgleich mit der Uhr, deshalb reicht der Name ohne
     * Zeitstempel.
     */
    get selectedSceneName() { return backend.getItem(PREFIX + "selected_scene"); },
    set selectedSceneName(name) { backend.setItem(PREFIX + "selected_scene", name); },

    /**
     * Gewaehltes Ton-Set (Name), KLASSIK als Fallback via
     * DotSound.fromName — die dritte Wahl nach demselben Muster.
     */
    get selectedSoundName() { return backend.getItem(PREFIX + "selected_sound"); },
    set selectedSoundName(name) { backend.setItem(PREFIX + "selected_sound", name); },

    // ===== Daily Challenge =====

    /** Tagesbest-Score — gilt nur für den in dailyDay gespeicherten Tag. */
    get dailyBest() { return getInt("daily_best"); },

    /** Epoch-Day, zu dem dailyBest gehört. */
    get dailyDay() { return getInt("daily_day"); },

    /** Aktuelle Serie an Tagen mit mindestens einem Daily-Lauf. */
    get dailyStreak() { return getInt("daily_streak"); },

    /** Tagesbest für einen konkreten Tag — 0, wenn der Tag nicht passt. */
    dailyBestFor: function (epochDay) {
      return this.dailyDay === epochDay ? this.dailyBest : 0;
    },

    /**
     * Die Serie, wie sie ein Daily-Lauf HEUTE fortschreiben würde:
     * War gestern der letzte Lauf, läuft die Serie noch; liegt er länger
     * zurück, ist sie faktisch gerissen.
     */
    dailyStreakPreviewFor: function (epochDay) {
      if (this.dailyDay === epochDay) return this.dailyStreak;
      if (this.dailyDay === epochDay - 1) return this.dailyStreak;
      return 0;
    },

    /**
     * Meldet einen beendeten Lauf; liefert true bei neuem Rekord.
     *
     * Kalendertag, Jahr und Monat kommen von der Geräte-Uhr und tragen
     * die Ausdauer-Achsen: gespielte Tage, gespielte Monate und den
     * Saison-Fortschritt. Ohne sie (Tests, Alt-Aufrufe) zählen nur Lauf
     * und Punktesumme.
     */
    submitRun: function (score, epochDay, year, month) {
      setInt("run_count", this.runCount + 1);
      setInt("total_score", this.totalScore + Math.max(0, score));
      if (epochDay !== undefined && epochDay !== null) {
        if (epochDay !== this.lastPlayedDay) {
          setInt("days_played", this.daysPlayed + 1);
          setInt("last_played_day", epochDay);
        }
        setInt("months_played", this.monthsPlayed | (1 << (month - 1)));
        this.trackSeason(epochDay, year, month);
      }
      if (score > this.bestScore) {
        setInt("best_score", score);
        return true;
      }
      return false;
    },

    /**
     * Saison-Fortschritt: Tage mit Lauf im aktiven Saison-Monat zählen.
     * Der Fenster-Schlüssel (Jahr*100+Monat) setzt den Zähler zurück,
     * sobald ein neues Fenster beginnt — das Bit selbst bleibt dagegen
     * für immer stehen, sonst wäre der Kürbis im November wieder weg.
     */
    trackSeason: function (epochDay, year, month) {
      var season = global.DotSkin.seasonForMonth(month);
      if (!season) return;
      var key = year * 100 + month;
      var days = getInt("season_days");
      if (getInt("season_window") !== key) {
        setInt("season_window", key);
        days = 0;
        setInt("season_days", 0);
        setInt("season_last_day", 0);
      }
      if (getInt("season_last_day") !== epochDay) {
        days += 1;
        setInt("season_days", days);
        setInt("season_last_day", epochDay);
      }
      if (days >= season.requiredDays) {
        setInt("season_earned", this.seasonEarned | season.bit);
      }
    },

    /** Meldet die höchste Perfekt-Serie eines Laufs. */
    submitPerfectStreak: function (streak) {
      if (streak > this.bestPerfectStreak) {
        setInt("best_perfect_streak", streak);
      }
    },

    /**
     * Meldet einen beendeten Daily-Lauf: schreibt die Tages-Serie fort
     * (nur der erste Lauf des Tages zählt dafür) und aktualisiert den
     * Tagesbest-Score. Liefert true bei neuem Tagesbest.
     */
    submitDailyRun: function (epochDay, score) {
      var firstRunToday = this.dailyDay !== epochDay;
      if (firstRunToday) {
        var streak = global.DailyChallenge.nextStreak(
          this.dailyDay, this.dailyStreak, epochDay
        );
        setInt("daily_streak", streak);
        setInt("daily_day", epochDay);
        setInt("daily_best", score);
        return score > 0;
      }
      if (score > this.dailyBest) {
        setInt("daily_best", score);
        return true;
      }
      return false;
    },

    /**
     * Tage mit Lauf im laufenden Saison-Fenster — 0, sobald der Kalender
     * weitergezogen ist. Der Wert gehört nicht in stats(): Er verfällt mit
     * dem Monat und taugt für keine Freischaltung, nur für die Anzeige des
     * Saison-Ziels (siehe progress.js).
     */
    seasonDaysFor: function (year, month) {
      return getInt("season_window") === year * 100 + month ? getInt("season_days") : 0;
    },

    /** Bestleistungen und Ausdauer gebündelt, für Skin-Freischaltungen. */
    stats: function () {
      return {
        bestScore: this.bestScore,
        bestPerfectStreak: this.bestPerfectStreak,
        bestDailyStreak: this.dailyStreak,
        runCount: this.runCount,
        totalScore: this.totalScore,
        daysPlayed: this.daysPlayed,
        // monthsPlayed ist in SkinStats eine Anzahl, gespeichert wird eine
        // Maske — sonst zählte ein zweiter Januar doppelt.
        monthsPlayed: bitCount(this.monthsPlayed),
        seasonEarned: this.seasonEarned,
        patronOwned: this.patronOwned
      };
    }
  };

  global.ScoreStore = ScoreStore;
  if (typeof module !== "undefined" && module.exports) {
    module.exports = ScoreStore;
  }
})(typeof globalThis !== "undefined" ? globalThis : this);

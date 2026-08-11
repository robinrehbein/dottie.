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

  var ScoreStore = {
    get bestScore() { return getInt("best_score"); },
    get runCount() { return getInt("run_count"); },

    /** Beste jemals erreichte Perfekt-Serie (für Skin-Freischaltungen). */
    get bestPerfectStreak() { return getInt("best_perfect_streak"); },

    /** Ton an/aus — überlebt Neustarts. */
    get soundMuted() { return getBool("sound_muted"); },
    set soundMuted(v) { setBool("sound_muted", v); },

    /** Gewählter Punkt-Skin (Name), KLASSIK als Fallback via DotSkin.fromName. */
    get selectedSkinName() { return backend.getItem(PREFIX + "selected_skin"); },
    set selectedSkinName(name) { backend.setItem(PREFIX + "selected_skin", name); },

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

    /** Meldet einen beendeten Lauf; liefert true bei neuem Rekord. */
    submitRun: function (score) {
      setInt("run_count", this.runCount + 1);
      if (score > this.bestScore) {
        setInt("best_score", score);
        return true;
      }
      return false;
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

    /** Aktuelle Bestleistungen gebündelt, für Skin-Freischaltungen. */
    stats: function () {
      return {
        bestScore: this.bestScore,
        bestPerfectStreak: this.bestPerfectStreak,
        bestDailyStreak: this.dailyStreak
      };
    }
  };

  global.ScoreStore = ScoreStore;
  if (typeof module !== "undefined" && module.exports) {
    module.exports = ScoreStore;
  }
})(typeof globalThis !== "undefined" ? globalThis : this);

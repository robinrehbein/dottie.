/*
 * Port von core/.../DailyChallenge.kt: Ein Kalendertag bestimmt einen
 * festen Zufalls-Seed — alle Versuche des Tages bekommen dieselbe
 * Zonen- und Twist-Abfolge. Serien-Regeln identisch zur Kotlin-Quelle.
 */
(function (global) {
  "use strict";

  var DailyChallenge = {
    /**
     * Seed für einen Kalendertag (Epoch-Day, lokale Zeitzone). Wie in
     * Kotlin mit der goldenen Primzahl gespreizt; 64-Bit via BigInt.
     */
    seedFor: function (epochDay) {
      return BigInt.asUintN(64, BigInt(epochDay) * 0x9e3779b97f4a7c15n);
    },

    /**
     * Fortschreibung der Tages-Serie beim ersten Daily-Lauf eines Tages:
     * direkt aufeinanderfolgende Tage zählen hoch, derselbe Tag ändert
     * nichts, eine Lücke setzt auf 1 zurück. `lastPlayedEpochDay <= 0`
     * heißt: noch nie gespielt.
     */
    nextStreak: function (lastPlayedEpochDay, currentStreak, todayEpochDay) {
      if (lastPlayedEpochDay <= 0) return 1;
      if (todayEpochDay === lastPlayedEpochDay) return Math.max(currentStreak, 1);
      if (todayEpochDay === lastPlayedEpochDay + 1) return Math.max(currentStreak, 0) + 1;
      return 1;
    },

    /** Heutiger Epoch-Day in lokaler Zeit (wie LocalDate.now().toEpochDay()). */
    todayEpochDay: function () {
      var d = new Date();
      return Math.floor((d.getTime() - d.getTimezoneOffset() * 60000) / 86400000);
    }
  };

  global.DailyChallenge = DailyChallenge;
  if (typeof module !== "undefined" && module.exports) {
    module.exports = DailyChallenge;
  }
})(typeof globalThis !== "undefined" ? globalThis : this);

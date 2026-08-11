/*
 * Port von DotSkin.kt und MedalTier.kt: Skins mit den Phone-Farben und
 * Freischalt-Bedingungen, Medaillen-Stufen ab 10/20/30/40 Punkten.
 */
(function (global) {
  "use strict";

  // Reihenfolge = DotSkin.entries in Kotlin.
  var SKINS = [
    { name: "KLASSIK",  titleKey: "skin_klassik",  hintKey: null,                body: "#FFD847", shade: "#F5A623", shine: "#FFF3B8" },
    { name: "MINZE",    titleKey: "skin_minze",    hintKey: "skin_hint_minze",   body: "#4BE38C", shade: "#2BA55E", shine: "#C8FFE0" },
    { name: "LAVA",     titleKey: "skin_lava",     hintKey: "skin_hint_lava",    body: "#FF5A36", shade: "#C22F12", shine: "#FFC9A3" },
    { name: "GOLD",     titleKey: "skin_gold",     hintKey: "skin_hint_gold",    body: "#FFC400", shade: "#CC8F00", shine: "#FFF7CC" },
    { name: "FROST",    titleKey: "skin_frost",    hintKey: "skin_hint_frost",   body: "#8FD8FF", shade: "#4FA3D8", shine: "#E8F9FF" },
    { name: "SCHATTEN", titleKey: "skin_schatten", hintKey: "skin_hint_schatten", body: "#6B4F8A", shade: "#43315C", shine: "#CBB8E8" },
    { name: "PRISMA",   titleKey: "skin_prisma",   hintKey: "skin_hint_prisma",  body: "#FF6FD8", shade: "#C93BAA", shine: "#B8F3FF" }
  ];

  /** stats = { bestScore, bestPerfectStreak, bestDailyStreak } */
  function isUnlocked(skin, stats) {
    switch (skin.name) {
      case "KLASSIK": return true;
      case "MINZE": return stats.bestScore >= 10;
      case "LAVA": return stats.bestScore >= 20;
      case "GOLD": return stats.bestScore >= 30;
      case "FROST": return stats.bestScore >= 40;
      case "SCHATTEN": return stats.bestPerfectStreak >= 4;
      case "PRISMA": return stats.bestDailyStreak >= 3;
    }
    return false;
  }

  /** Skin zu einem gespeicherten Namen, KLASSIK als Fallback. */
  function fromName(name) {
    for (var i = 0; i < SKINS.length; i++) {
      if (SKINS[i].name === name) return SKINS[i];
    }
    return SKINS[0];
  }

  function unlockedCount(stats) {
    return SKINS.filter(function (s) { return isUnlocked(s, stats); }).length;
  }

  var DotSkin = {
    SKINS: SKINS,
    isUnlocked: isUnlocked,
    fromName: fromName,
    unlockedCount: unlockedCount
  };

  // ===== Medaillen (MedalTier.kt) =====

  var MEDALS = [
    { name: "BRONZE",   threshold: 10, nameKey: "medal_bronze",   body: "#CD7F32", shade: "#9C5A1E" },
    { name: "SILVER",   threshold: 20, nameKey: "medal_silver",   body: "#C0C0C0", shade: "#8F8F9C" },
    { name: "GOLD",     threshold: 30, nameKey: "medal_gold",     body: "#FFD700", shade: "#C9A400" },
    { name: "PLATINUM", threshold: 40, nameKey: "medal_platinum", body: "#E5E4E2", shade: "#ADB5C4" }
  ];

  var MedalTier = {
    MEDALS: MEDALS,

    /** Höchste erreichte Stufe, null unterhalb von Bronze. */
    forScore: function (score) {
      var found = null;
      for (var i = 0; i < MEDALS.length; i++) {
        if (score >= MEDALS[i].threshold) found = MEDALS[i];
      }
      return found;
    },

    /** Nächste noch nicht erreichte Stufe, null ab Platin. */
    next: function (score) {
      for (var i = 0; i < MEDALS.length; i++) {
        if (score < MEDALS[i].threshold) return MEDALS[i];
      }
      return null;
    },

    /** Bringt dieser Score eine höhere Stufe als der bisherige Bestwert? */
    isUpgrade: function (score, previousBest) {
      var a = MedalTier.forScore(score);
      var b = MedalTier.forScore(previousBest);
      return (a ? MEDALS.indexOf(a) : -1) > (b ? MEDALS.indexOf(b) : -1);
    }
  };

  global.DotSkin = DotSkin;
  global.MedalTier = MedalTier;
  if (typeof module !== "undefined" && module.exports) {
    module.exports = { DotSkin: DotSkin, MedalTier: MedalTier };
  }
})(typeof globalThis !== "undefined" ? globalThis : this);

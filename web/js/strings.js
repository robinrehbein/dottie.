/*
 * Texte EN/DE — übernommen aus app/src/main/res/values/strings.xml und
 * values-de/strings.xml (ohne Ranglisten-/Notification-/Score-Card-Strings,
 * die es in der Web-Version nicht gibt). Die Browser-Sprache entscheidet.
 */
(function (global) {
  "use strict";

  var STRINGS = {
    en: {
      app_name: "Dottie.",
      ready_hint: "STOP THE DOT IN THE GREEN ZONE",
      sound_on: "SOUND: ON",
      sound_off: "SOUND: OFF",
      best_score: "BEST: %1$d",
      daily: "DAILY",
      skins: "SKINS",
      today_score: "TODAY: %1$d",
      streak_one: "STREAK: 1 DAY",
      streak_many: "STREAK: %1$d DAYS",
      run_number: "RUN #%1$d",

      perfect_plus: "PERFECT! +%1$d",
      banner_chain: "ONE MORE!",
      banner_record: "RECORD BROKEN!",
      banner_stage: "NEW STAGE!",
      banner_twist_pulse: "NEW: PULSE ZONE!",
      banner_twist_drift: "NEW: DRIFTING ZONE!",
      banner_twist_ghost: "NEW: GHOST DOT!",
      banner_twist_fake: "NEW: TRAP ZONE!",
      banner_twist_chain: "NEW: CHAIN ZONE!",

      game_over: "GAME OVER",
      medal: "MEDAL",
      medal_bronze: "BRONZE",
      medal_silver: "SILVER",
      medal_gold: "GOLD",
      medal_platinum: "PLATINUM",
      medal_next: "%1$d MORE FOR %2$s",
      new_medal: "NEW MEDAL!",
      points_label: "SCORE",
      record_label: "BEST",
      new_record: "NEW RECORD!",
      new_skin_unlocked: "NEW SKIN UNLOCKED!",
      tap_retry: "TAP = RETRY",
      share: "SHARE",
      menu: "MENU",
      share_text: "%1$d points in DOTTIE. — can you beat that?",
      share_text_daily: "Daily Challenge in DOTTIE.: %1$d points — can you beat that?",

      taunts_zero: ["SERIOUSLY?", "THAT WAS FAST.", "WAS THAT ON PURPOSE?", "WARM-UPS DON'T COUNT."],
      taunts_close: ["SO CLOSE! ONLY %1$d SHORT!", "ALMOST! JUST %1$d MORE!", "AAARGH! %1$d TOO FEW!"],
      taunts_low: ["THAT WAS NOTHING.", "YOU CAN DO BETTER.", "FORGOT HOW ALREADY?"],
      taunts_default: ["AGAIN!", "NEXT ONE'S YOURS!", "THAT CAME OUT OF NOWHERE.", "DON'T GIVE UP!"],

      help_title: "HOW TO PLAY",
      help_line1: "THE DOT CIRCLES ON ITS OWN.",
      help_line2: "TAP WHILE IT IS IN THE GREEN ZONE — TAP OUTSIDE OR OVERSHOOT = GAME OVER.",
      help_line3: "BRIGHT ZONE CENTER = PERFECT: +2 POINTS.",
      help_line4: "PERFECT STREAK? +3, +4, UP TO +5 PER HIT!",
      help_line5: "MEDALS: BRONZE AT 10, SILVER AT 20, GOLD AT 30, PLATINUM AT 40.",
      help_twists: "THE TWISTS",
      twist_pulse_title: "PULSE (AT 5)",
      twist_pulse_text: "THE ZONE BREATHES — GROWS AND SHRINKS.",
      twist_drift_title: "DRIFT (AT 10)",
      twist_drift_text: "THE ZONE SLOWLY WANDERS ALONG THE TRACK.",
      twist_ghost_title: "GHOST (AT 15)",
      twist_ghost_text: "THE DOT BLINKS AWAY — KEEP THE TRACK IN YOUR HEAD.",
      twist_fake_title: "TRAP (AT 20)",
      twist_fake_text: "PURPLE DECOY ZONE: NEVER TAP INTO IT!",
      twist_chain_title: "CHAIN (AT 25)",
      twist_chain_text: "TWO ZONES IN A ROW — SAME DIRECTION.",
      help_max_twists: "AT MOST TWO TWISTS AT ONCE — RANDOMLY MIXED.",
      tap_to_close: "TAP TO CLOSE",

      skin_selected: "SELECTED",
      skin_tap_select: "TAP TO SELECT",
      skin_klassik: "CLASSIC",
      skin_minze: "MINT",
      skin_lava: "LAVA",
      skin_gold: "GOLD",
      skin_frost: "FROST",
      skin_schatten: "SHADOW",
      skin_prisma: "PRISM",
      skin_hint_minze: "BEST 10 (BRONZE)",
      skin_hint_lava: "BEST 20 (SILVER)",
      skin_hint_gold: "BEST 30 (GOLD)",
      skin_hint_frost: "BEST 40 (PLATINUM)",
      skin_hint_schatten: "4 PERFECTS IN A ROW",
      skin_hint_prisma: "DAILY STREAK: 3 DAYS",
      skin_biene: "BEE",
      skin_melone: "MELON",
      skin_pilz: "TOADSTOOL",
      skin_koi: "KOI",
      skin_galaxie: "GALAXY",
      skin_karo: "CHECKER",
      skin_regenbogen: "RAINBOW",
      skin_aurora: "AURORA",
      skin_magma: "MAGMA",
      skin_neon: "NEON",
      skin_chrom: "CHROME",
      skin_chamaeleon: "CHAMELEON",
      skin_kombo: "COMBO",
      skin_tinte: "INK",
      skin_hint_biene: "6 PERFECTS IN A ROW",
      skin_hint_melone: "BEST 25",
      skin_hint_pilz: "BEST 35",
      skin_hint_koi: "DAILY STREAK: 7 DAYS",
      skin_hint_galaxie: "BEST 50",
      skin_hint_karo: "10 PERFECTS IN A ROW",
      skin_hint_regenbogen: "COLLECT EVERY OTHER SKIN",
      skin_hint_aurora: "DAILY STREAK: 14 DAYS",
      skin_hint_magma: "BEST 60",
      skin_hint_neon: "12 PERFECTS IN A ROW",
      skin_hint_chrom: "BEST 45",
      skin_hint_chamaeleon: "BEST 30",
      skin_hint_kombo: "8 PERFECTS IN A ROW",
      skin_hint_tinte: "BEST 55",
      skin_ei: "EGG",
      skin_tiger: "TIGER",
      skin_pinguin: "PENGUIN",
      skin_fussball: "FOOTBALL",
      skin_donut: "DONUT",
      skin_welle: "WAVE",
      skin_gewitter: "STORM",
      skin_konfetti: "CONFETTI",
      skin_disco: "DISCO",
      skin_holo: "HOLO",
      skin_thermo: "THERMO",
      skin_medaille: "MEDAL",
      skin_tageszeit: "DAYTIME",
      skin_jahreszeit: "SEASON",
      skin_kuerbis: "PUMPKIN",
      skin_zuckerstange: "CANDY CANE",
      skin_herz: "HEART",
      skin_osterei: "EASTER EGG",
      skin_diamant: "DIAMOND",
      skin_phoenix: "PHOENIX",
      skin_onyx: "ONYX",
      skin_hint_ei: "25 RUNS",
      skin_hint_tiger: "100 RUNS",
      skin_hint_pinguin: "BEST 65",
      skin_hint_fussball: "300 RUNS",
      skin_hint_donut: "1,000 POINTS TOTAL",
      skin_hint_welle: "BEST 70",
      skin_hint_gewitter: "15 PERFECTS IN A ROW",
      skin_hint_konfetti: "5,000 POINTS TOTAL",
      skin_hint_disco: "DAILY STREAK: 21 DAYS",
      skin_hint_holo: "BEST 80",
      skin_hint_thermo: "BEST 75",
      skin_hint_medaille: "200 RUNS",
      skin_hint_tageszeit: "PLAYED ON 7 DAYS",
      skin_hint_jahreszeit: "PLAYED IN 3 MONTHS",
      skin_hint_kuerbis: "OCTOBER: PLAY ON 5 DAYS",
      skin_hint_zuckerstange: "DECEMBER: PLAY ON 5 DAYS",
      skin_hint_herz: "FEBRUARY: PLAY ON 3 DAYS",
      skin_hint_osterei: "APRIL: PLAY ON 5 DAYS",
      skin_hint_diamant: "PATRON PACK",
      skin_hint_phoenix: "PATRON PACK",
      skin_hint_onyx: "PATRON PACK",

      // Ueberschriften der sechs Familien im Skin-Menue.
      skin_family_einfarbig: "SOLID",
      skin_family_gemustert: "PATTERNED",
      skin_family_bewegt: "ANIMATED",
      skin_family_reagierend: "REACTIVE",
      skin_family_saison: "SEASONAL",
      skin_family_goenner: "PATRON",

      // Nur im Web: Das Goenner-Paket gibt es hier nicht zu kaufen.
      skin_patron_web_only: "IN THE APP ONLY",

      // Kulissen: die zweite Sammlung. Sie entscheidet nie ueber einen
      // Treffer — deshalb darf sie verkauft werden und die Bahn nicht.
      scenes: "SCENES",
      scene_wiese: "MEADOW",
      scene_wueste: "DESERT",
      scene_meer: "SEA",
      scene_berg: "MOUNTAIN",
      scene_stadt: "CITY",
      scene_weltraum: "SPACE",
      scene_hint_wueste: "500 RUNS",
      scene_hint_meer: "10,000 POINTS TOTAL",
      scene_hint_berg: "DAILY STREAK: 30 DAYS",
      scene_hint_stadt: "BEST 85",
      scene_hint_weltraum: "COLLECT EVERY OTHER SCENE"
    },
    de: {
      app_name: "Dottie.",
      ready_hint: "STOPPE DEN PUNKT IN DER GRUENEN ZONE",
      sound_on: "TON: AN",
      sound_off: "TON: AUS",
      best_score: "REKORD: %1$d",
      daily: "DAILY",
      skins: "SKINS",
      today_score: "HEUTE: %1$d",
      streak_one: "SERIE: 1 TAG",
      streak_many: "SERIE: %1$d TAGE",
      run_number: "VERSUCH #%1$d",

      perfect_plus: "PERFEKT! +%1$d",
      banner_chain: "NOCH EINE!",
      banner_record: "REKORD GEKNACKT!",
      banner_stage: "NEUE STUFE!",
      banner_twist_pulse: "NEU: PULS-ZONE!",
      banner_twist_drift: "NEU: WANDERNDE ZONE!",
      banner_twist_ghost: "NEU: GEISTER-PUNKT!",
      banner_twist_fake: "NEU: FALLEN-ZONE!",
      banner_twist_chain: "NEU: KETTEN-ZONE!",

      game_over: "GAME OVER",
      medal: "MEDAILLE",
      medal_bronze: "BRONZE",
      medal_silver: "SILBER",
      medal_gold: "GOLD",
      medal_platinum: "PLATIN",
      medal_next: "NOCH %1$d BIS %2$s",
      new_medal: "NEUE MEDAILLE!",
      points_label: "PUNKTE",
      record_label: "REKORD",
      new_record: "NEUER REKORD!",
      new_skin_unlocked: "NEUER SKIN FREIGESCHALTET!",
      tap_retry: "TIPPEN = NOCHMAL",
      share: "TEILEN",
      menu: "MENUE",
      share_text: "%1$d Punkte in DOTTIE. — schaffst du mehr?",
      share_text_daily: "Daily Challenge in DOTTIE.: %1$d Punkte — schaffst du mehr?",

      taunts_zero: ["ERNSTHAFT?", "DAS GING SCHNELL.", "WAR DAS ABSICHT?", "AUFWAERMEN ZAEHLT NICHT."],
      taunts_close: ["SO NAH! NUR %1$d GEFEHLT!", "FAST! NOCH %1$d!", "AAARGH! %1$d ZU WENIG!"],
      taunts_low: ["DAS WAR NIX.", "DU KANNST MEHR.", "SCHON VERGESSEN WIE?"],
      taunts_default: ["NOCHMAL!", "GLEICH KLAPPTS!", "DAS KAM AUS DEM NICHTS.", "NICHT AUFGEBEN!"],

      help_title: "SO GEHT STOPP",
      help_line1: "DER PUNKT KREIST VON ALLEIN.",
      help_line2: "TIPPE, WENN ER IN DER GRUENEN ZONE IST — DANEBEN GETIPPT ODER ZONE VERPASST = AUS.",
      help_line3: "HELLE MITTE DER ZONE = PERFEKT: +2 PUNKTE.",
      help_line4: "PERFEKT IN SERIE? +3, +4, BIS ZU +5 PRO TREFFER!",
      help_line5: "MEDAILLEN: BRONZE AB 10, SILBER AB 20, GOLD AB 30, PLATIN AB 40.",
      help_twists: "DIE TWISTS",
      twist_pulse_title: "PULS (AB 5)",
      twist_pulse_text: "DIE ZONE ATMET — WIRD GROESSER UND KLEINER.",
      twist_drift_title: "DRIFT (AB 10)",
      twist_drift_text: "DIE ZONE WANDERT LANGSAM UEBER DIE BAHN.",
      twist_ghost_title: "GEIST (AB 15)",
      twist_ghost_text: "DER PUNKT BLINKT WEG — BAHN IM KOPF BEHALTEN.",
      twist_fake_title: "FALLE (AB 20)",
      twist_fake_text: "VIOLETTE KOEDER-ZONE: NIE HINEINTIPPEN!",
      twist_chain_title: "KETTE (AB 25)",
      twist_chain_text: "ZWEI ZONEN NACHEINANDER — GLEICHE RICHTUNG.",
      help_max_twists: "MAXIMAL ZWEI TWISTS GLEICHZEITIG — ZUFAELLIG GEMISCHT.",
      tap_to_close: "TIPPEN ZUM SCHLIESSEN",

      skin_selected: "AUSGEWAEHLT",
      skin_tap_select: "TIPPEN ZUM WAEHLEN",
      skin_klassik: "KLASSIK",
      skin_minze: "MINZE",
      skin_lava: "LAVA",
      skin_gold: "GOLD",
      skin_frost: "FROST",
      skin_schatten: "SCHATTEN",
      skin_prisma: "PRISMA",
      skin_hint_minze: "REKORD 10 (BRONZE)",
      skin_hint_lava: "REKORD 20 (SILBER)",
      skin_hint_gold: "REKORD 30 (GOLD)",
      skin_hint_frost: "REKORD 40 (PLATIN)",
      skin_hint_schatten: "4 PERFEKTE IN SERIE",
      skin_hint_prisma: "DAILY-SERIE: 3 TAGE",
      skin_biene: "BIENE",
      skin_melone: "MELONE",
      skin_pilz: "FLIEGENPILZ",
      skin_koi: "KOI",
      skin_galaxie: "GALAXIE",
      skin_karo: "KARO",
      skin_regenbogen: "REGENBOGEN",
      skin_aurora: "AURORA",
      skin_magma: "MAGMA",
      skin_neon: "NEON",
      skin_chrom: "CHROM",
      skin_chamaeleon: "CHAMAELEON",
      skin_kombo: "KOMBO",
      skin_tinte: "TINTE",
      skin_hint_biene: "6 PERFEKTE IN SERIE",
      skin_hint_melone: "REKORD 25",
      skin_hint_pilz: "REKORD 35",
      skin_hint_koi: "DAILY-SERIE: 7 TAGE",
      skin_hint_galaxie: "REKORD 50",
      skin_hint_karo: "10 PERFEKTE IN SERIE",
      skin_hint_regenbogen: "ALLE ANDEREN SKINS SAMMELN",
      skin_hint_aurora: "DAILY-SERIE: 14 TAGE",
      skin_hint_magma: "REKORD 60",
      skin_hint_neon: "12 PERFEKTE IN SERIE",
      skin_hint_chrom: "REKORD 45",
      skin_hint_chamaeleon: "REKORD 30",
      skin_hint_kombo: "8 PERFEKTE IN SERIE",
      skin_hint_tinte: "REKORD 55",
      skin_ei: "EI",
      skin_tiger: "TIGER",
      skin_pinguin: "PINGUIN",
      skin_fussball: "FUSSBALL",
      skin_donut: "DONUT",
      skin_welle: "WELLE",
      skin_gewitter: "GEWITTER",
      skin_konfetti: "KONFETTI",
      skin_disco: "DISCO",
      skin_holo: "HOLO",
      skin_thermo: "THERMO",
      skin_medaille: "MEDAILLE",
      skin_tageszeit: "TAGESZEIT",
      skin_jahreszeit: "JAHRESZEIT",
      skin_kuerbis: "KUERBIS",
      skin_zuckerstange: "ZUCKERSTANGE",
      skin_herz: "HERZ",
      skin_osterei: "OSTEREI",
      skin_diamant: "DIAMANT",
      skin_phoenix: "PHOENIX",
      skin_onyx: "ONYX",
      skin_hint_ei: "25 LAEUFE",
      skin_hint_tiger: "100 LAEUFE",
      skin_hint_pinguin: "REKORD 65",
      skin_hint_fussball: "300 LAEUFE",
      skin_hint_donut: "1.000 PUNKTE INSGESAMT",
      skin_hint_welle: "REKORD 70",
      skin_hint_gewitter: "15 PERFEKTE IN SERIE",
      skin_hint_konfetti: "5.000 PUNKTE INSGESAMT",
      skin_hint_disco: "DAILY-SERIE: 21 TAGE",
      skin_hint_holo: "REKORD 80",
      skin_hint_thermo: "REKORD 75",
      skin_hint_medaille: "200 LAEUFE",
      skin_hint_tageszeit: "AN 7 TAGEN GESPIELT",
      skin_hint_jahreszeit: "IN 3 MONATEN GESPIELT",
      skin_hint_kuerbis: "IM OKTOBER AN 5 TAGEN SPIELEN",
      skin_hint_zuckerstange: "IM DEZEMBER AN 5 TAGEN SPIELEN",
      skin_hint_herz: "IM FEBRUAR AN 3 TAGEN SPIELEN",
      skin_hint_osterei: "IM APRIL AN 5 TAGEN SPIELEN",
      skin_hint_diamant: "GOENNER-PAKET",
      skin_hint_phoenix: "GOENNER-PAKET",
      skin_hint_onyx: "GOENNER-PAKET",

      // Ueberschriften der sechs Familien im Skin-Menue.
      skin_family_einfarbig: "EINFARBIG",
      skin_family_gemustert: "GEMUSTERT",
      skin_family_bewegt: "BEWEGT",
      skin_family_reagierend: "REAGIEREND",
      skin_family_saison: "SAISON",
      skin_family_goenner: "GOENNER",

      // Nur im Web: Das Goenner-Paket gibt es hier nicht zu kaufen.
      skin_patron_web_only: "NUR IN DER APP",

      // Kulissen: die zweite Sammlung. Sie entscheidet nie ueber einen
      // Treffer — deshalb darf sie verkauft werden und die Bahn nicht.
      scenes: "KULISSEN",
      scene_wiese: "WIESE",
      scene_wueste: "WUESTE",
      scene_meer: "MEER",
      scene_berg: "BERG",
      scene_stadt: "STADT",
      scene_weltraum: "WELTRAUM",
      scene_hint_wueste: "500 LAEUFE",
      scene_hint_meer: "10.000 PUNKTE INSGESAMT",
      scene_hint_berg: "DAILY-SERIE: 30 TAGE",
      scene_hint_stadt: "REKORD 85",
      scene_hint_weltraum: "ALLE ANDEREN KULISSEN SAMMELN"
    }
  };

  var lang = "en";
  if (typeof navigator !== "undefined" && navigator.language &&
      navigator.language.toLowerCase().indexOf("de") === 0) {
    lang = "de";
  }

  /** t("best_score", 12) => "REKORD: 12". Platzhalter wie in Android: %1$d/%1$s. */
  function t(key) {
    var s = STRINGS[lang][key];
    if (s === undefined) s = STRINGS.en[key];
    if (s === undefined) return key;
    for (var i = 1; i < arguments.length; i++) {
      s = s.replace("%" + i + "$d", arguments[i]).replace("%" + i + "$s", arguments[i]);
    }
    return s;
  }

  /** "SERIE: n TAG/TAGE" bzw. "STREAK: n DAY/DAYS", sprachrichtig. */
  function streakLabel(days) {
    return days === 1 ? t("streak_one") : t("streak_many", days);
  }

  /**
   * Port von pickTaunt() aus GameOverlays.kt: Spott-Text deterministisch
   * über (score + previousBest) aus dem passenden Pool.
   */
  function pickTaunt(score, previousBest, isNewRecord) {
    if (isNewRecord) return t("new_record");
    var gap = previousBest - score;
    var pool;
    if (score === 0) pool = STRINGS[lang].taunts_zero;
    else if (gap >= 1 && gap <= 3) pool = STRINGS[lang].taunts_close;
    else if (score < Math.floor(previousBest / 2)) pool = STRINGS[lang].taunts_low;
    else pool = STRINGS[lang].taunts_default;
    var line = pool[(score + previousBest) % pool.length];
    // Nur die "knapp daneben"-Zeilen tragen einen %1$d-Platzhalter.
    return line.indexOf("%1$d") >= 0 ? line.replace("%1$d", gap) : line;
  }

  var Strings = {
    STRINGS: STRINGS,
    get lang() { return lang; },
    setLang: function (l) { lang = STRINGS[l] ? l : "en"; },
    t: t,
    streakLabel: streakLabel,
    pickTaunt: pickTaunt
  };

  global.Strings = Strings;
  if (typeof module !== "undefined" && module.exports) {
    module.exports = Strings;
  }
})(typeof globalThis !== "undefined" ? globalThis : this);

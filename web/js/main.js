/*
 * Haupt-Controller — Port des UI-Loops aus TimingGameScreen.kt:
 * Frame-Loop, Event-Verarbeitung (Treffer, Perfekt, Twists, Tod),
 * Banner mit Priorität, Live-Rekord-Feier, Stufen-Feedback, Overlays
 * (Ready, HUD, Game-Over, Hilfe, Skins) und Persistenz.
 */
(function () {
  "use strict";

  var t = Strings.t;

  // ===== Spiel-Zustand =====
  var game = new TimingGame();
  var audio = new GameAudio();
  audio.muted = ScoreStore.soundMuted;

  var fx = { flashAlpha: 0, shakeTime: 0, celebrateTime: 0, deathTime: -1 };
  var bannerState = { timeLeft: 0, priority: 0, lastStage: 0, recordCelebrated: false };
  var runState = { epochDay: 0, maxPerfect: 0 };

  var dailyMode = false;
  var isNewRecord = false;
  var taunt = "";
  var perfectPoints = 2;
  var bannerText = "";
  var skin = DotSkin.fromName(ScoreStore.selectedSkinName);
  var skinUnlockedThisRun = false;
  var newMedalThisRun = false;
  var uiBestScore = ScoreStore.bestScore;

  // ===== DOM =====
  var $ = function (id) { return document.getElementById(id); };
  var stage = $("stage");
  var canvas = $("game");
  var ctx = canvas.getContext("2d");

  var el = {
    hud: $("hud"),
    score: $("score"),
    hudDaily: $("hud-daily"),
    banner: $("banner"),
    perfect: $("perfect"),
    ready: $("overlay-ready"),
    btnSound: $("btn-sound"),
    btnHelp: $("btn-help"),
    bestLine: $("best-line"),
    hint: $("hint"),
    btnDaily: $("btn-daily"),
    btnSkins: $("btn-skins"),
    dailyLine: $("daily-line"),
    runLine: $("run-line"),
    over: $("overlay-gameover"),
    btnHelpOver: $("btn-help-over"),
    goMedal: $("go-medal"),
    goMedalName: $("go-medal-name"),
    goMedalNext: $("go-medal-next"),
    goScoreLabel: $("go-score-label"),
    goScore: $("go-score"),
    goBestLabel: $("go-best-label"),
    goBest: $("go-best"),
    goTaunt: $("go-taunt"),
    goDailyLine: $("go-daily-line"),
    goNewMedal: $("go-new-medal"),
    goNewSkin: $("go-new-skin"),
    goRetry: $("go-retry"),
    btnMenu: $("btn-menu"),
    help: $("overlay-help"),
    helpContent: $("help-content"),
    skins: $("overlay-skins"),
    skinList: $("skin-list")
  };

  // ===== Statische Texte =====
  document.title = t("app_name");
  el.hint.textContent = t("ready_hint");
  el.btnDaily.textContent = t("daily");
  el.btnSkins.textContent = t("skins");
  el.hudDaily.textContent = t("daily");
  el.goScoreLabel.textContent = t("points_label");
  el.goRetry.textContent = t("tap_retry");
  el.btnMenu.textContent = t("menu");
  $("go-title").textContent = t("game_over");
  $("skins-title").textContent = t("skins");
  $("skins-close").textContent = t("tap_to_close");

  function updateSoundButton() {
    el.btnSound.textContent = t(audio.muted ? "sound_off" : "sound_on");
  }
  updateSoundButton();

  // ===== Hilfe-Overlay-Inhalt =====
  (function buildHelp() {
    var twists = [
      ["#9DE85A", "twist_pulse_title", "twist_pulse_text"],
      ["#5B9BD5", "twist_drift_title", "twist_drift_text"],
      ["#E9FCFD", "twist_ghost_title", "twist_ghost_text"],
      ["#B44FD8", "twist_fake_title", "twist_fake_text"],
      ["#FF8A3C", "twist_chain_title", "twist_chain_text"]
    ];
    var html = '<div class="help-heading">' + t("help_title") + "</div>";
    ["help_line1", "help_line2", "help_line3", "help_line4", "help_line5"]
      .forEach(function (key, i) {
        var cls = (i === 2 || i === 3) ? "help-line gold" : "help-line";
        html += '<div class="' + cls + '">' + t(key) + "</div>";
      });
    html += '<div class="help-twists">' + t("help_twists") + "</div>";
    twists.forEach(function (row) {
      html += '<div class="twist-row"><span class="twist-chip" style="background:' +
        row[0] + '"></span><span class="twist-texts"><span class="twist-title" style="color:' +
        row[0] + '">' + t(row[1]) + '</span><span class="twist-text">' + t(row[2]) +
        "</span></span></div>";
    });
    html += '<div class="help-line">' + t("help_max_twists") + "</div>";
    html += '<div class="help-close">' + t("tap_to_close") + "</div>";
    el.helpContent.innerHTML = html;
  })();

  // ===== Skin-Overlay =====
  function buildSkinList() {
    var stats = ScoreStore.stats();
    el.skinList.innerHTML = "";
    DotSkin.SKINS.forEach(function (s) {
      var unlocked = DotSkin.isUnlocked(s, stats);
      var row = document.createElement("div");
      row.className = "skin-row" + (unlocked ? "" : " locked");

      var chip = document.createElement("span");
      chip.className = "skin-chip";
      chip.style.background = s.body;
      if (!unlocked) chip.style.opacity = "0.25";
      row.appendChild(chip);

      var texts = document.createElement("span");
      texts.className = "skin-texts";
      var title = document.createElement("span");
      title.className = "skin-title";
      title.textContent = t(s.titleKey);
      var sub = document.createElement("span");
      sub.className = "skin-sub";
      if (s.name === skin.name) {
        sub.textContent = t("skin_selected");
        sub.classList.add("selected");
      } else if (unlocked) {
        sub.textContent = t("skin_tap_select");
      } else {
        sub.textContent = s.hintKey ? t(s.hintKey) : "";
      }
      texts.appendChild(title);
      texts.appendChild(sub);
      row.appendChild(texts);

      if (unlocked) {
        row.addEventListener("pointerdown", function (e) { e.stopPropagation(); });
        row.addEventListener("click", function (e) {
          e.stopPropagation();
          skin = s;
          ScoreStore.selectedSkinName = s.name;
          hide(el.skins);
        });
      }
      el.skinList.appendChild(row);
    });
  }

  // ===== Sichtbarkeits-Helfer =====
  function show(node) { node.classList.remove("hidden"); }
  function hide(node) { node.classList.add("hidden"); }
  function setVisible(node, visible) {
    node.classList.toggle("hidden", !visible);
  }

  // ===== Ready-/Game-Over-Anzeigen =====
  function updateReadyUI() {
    var best = ScoreStore.bestScore;
    setVisible(el.bestLine, best > 0);
    el.bestLine.textContent = t("best_score", best);

    var today = DailyChallenge.todayEpochDay();
    var dailyBest = ScoreStore.dailyBestFor(today);
    var dailyStreak = ScoreStore.dailyStreakPreviewFor(today);
    var parts = [];
    if (dailyBest > 0) parts.push(t("today_score", dailyBest));
    if (dailyStreak > 0) parts.push(Strings.streakLabel(dailyStreak));
    setVisible(el.dailyLine, parts.length > 0);
    el.dailyLine.textContent = parts.join("  ·  ");

    var runs = ScoreStore.runCount;
    setVisible(el.runLine, runs > 0);
    el.runLine.textContent = t("run_number", runs + 1);
  }

  function updateGameOverUI() {
    var score = game.score;
    Renderer.drawMedal(el.goMedal, score);
    // Medaillen-Pop neu anstoßen
    el.goMedal.classList.remove("pop");
    void el.goMedal.offsetWidth;
    el.goMedal.classList.add("pop");

    var tier = MedalTier.forScore(score);
    el.goMedalName.textContent = tier ? t(tier.nameKey) : t("medal");
    var next = MedalTier.next(score);
    setVisible(el.goMedalNext, !!next);
    if (next) {
      el.goMedalNext.textContent = t("medal_next", next.threshold - score, t(next.nameKey));
    }

    el.goScore.textContent = String(score);
    el.goBestLabel.textContent = t("record_label");
    el.goBest.textContent = String(ScoreStore.bestScore);
    el.goBestLabel.classList.toggle("record", isNewRecord);
    el.goBest.classList.toggle("record", isNewRecord);

    el.goTaunt.textContent = isNewRecord ? t("new_record") : taunt;
    el.goTaunt.classList.toggle("gold", isNewRecord);

    if (dailyMode) {
      var today = runState.epochDay;
      var parts = [t("daily"), t("today_score", ScoreStore.dailyBestFor(today))];
      var streak = ScoreStore.dailyStreak;
      if (streak > 0) parts.push(Strings.streakLabel(streak));
      el.goDailyLine.textContent = parts.join("  ·  ");
      show(el.goDailyLine);
    } else {
      hide(el.goDailyLine);
    }

    setVisible(el.goNewMedal, newMedalThisRun);
    el.goNewMedal.textContent = t("new_medal");
    setVisible(el.goNewSkin, skinUnlockedThisRun);
    el.goNewSkin.textContent = t("new_skin_unlocked");
  }

  // ===== Banner mit Priorität =====
  function showBanner(text, seconds, priority) {
    if (bannerState.timeLeft > 0 && bannerState.priority > priority) return;
    bannerText = text;
    bannerState.timeLeft = seconds;
    bannerState.priority = priority;
  }

  function twistBannerText(twist) {
    switch (twist) {
      case "PULSE": return t("banner_twist_pulse");
      case "DRIFT": return t("banner_twist_drift");
      case "GHOST": return t("banner_twist_ghost");
      case "FAKE": return t("banner_twist_fake");
      case "CHAIN": return t("banner_twist_chain");
    }
    return "";
  }

  // ===== Lauf-Vorbereitung (Seed passend zum Modus) =====
  function prepareRun() {
    var today = DailyChallenge.todayEpochDay();
    runState.epochDay = today;
    game.reseed(dailyMode ? DailyChallenge.seedFor(today) : null);
  }

  // ===== Canvas-Größe (devicePixelRatio, Hochformat) =====
  function resize() {
    var dpr = Math.min(window.devicePixelRatio || 1, 3);
    var w = stage.clientWidth;
    var h = stage.clientHeight;
    canvas.width = Math.max(1, Math.round(w * dpr));
    canvas.height = Math.max(1, Math.round(h * dpr));
    canvas.style.width = w + "px";
    canvas.style.height = h + "px";
    ctx.imageSmoothingEnabled = false;
  }
  window.addEventListener("resize", resize);
  window.addEventListener("orientationchange", function () {
    setTimeout(resize, 250);
  });
  resize();

  // ===== Event-Verarbeitung (Port des LaunchedEffect-Loops) =====
  function handleEvents(events) {
    var twistUnlockedThisFrame = false;
    events.forEach(function (event) {
      var type = typeof event === "string" ? event : event.type;
      switch (type) {
        case "Started":
          bannerState.lastStage = 0;
          bannerState.recordCelebrated = false;
          bannerState.timeLeft = 0;
          bannerText = "";
          runState.maxPerfect = 0;
          skinUnlockedThisRun = false;
          newMedalThisRun = false;
          fx.deathTime = -1;
          audio.start();
          break;
        case "Hit":
          Haptics.score();
          audio.hit(game.score);
          break;
        case "PerfectHit":
          Haptics.perfect();
          audio.perfect(game.perfectStreak);
          perfectPoints = game.lastHitPoints;
          runState.maxPerfect = Math.max(runState.maxPerfect, game.perfectStreak);
          break;
        case "ChainNext":
          showBanner(t("banner_chain"), 1.2, 1);
          audio.chain();
          break;
        case "TwistUnlocked":
          twistUnlockedThisFrame = true;
          showBanner(twistBannerText(event.twist), 2.2, 2);
          fx.celebrateTime = Renderer.CELEBRATE_SECONDS;
          Haptics.unlock();
          audio.unlockSound();
          break;
        case "Died": {
          Haptics.death();
          audio.death();
          fx.flashAlpha = 1;
          fx.shakeTime = 0.4;
          fx.celebrateTime = 0;
          fx.deathTime = 0;
          var previousBest = ScoreStore.bestScore;
          newMedalThisRun = MedalTier.isUpgrade(game.score, previousBest);
          var unlockedBefore = DotSkin.unlockedCount(ScoreStore.stats());
          isNewRecord = ScoreStore.submitRun(game.score);
          ScoreStore.submitPerfectStreak(runState.maxPerfect);
          if (dailyMode) {
            ScoreStore.submitDailyRun(runState.epochDay, game.score);
          }
          skinUnlockedThisRun =
            DotSkin.unlockedCount(ScoreStore.stats()) > unlockedBefore;
          taunt = Strings.pickTaunt(game.score, previousBest, isNewRecord);
          uiBestScore = ScoreStore.bestScore;
          if (isNewRecord && !bannerState.recordCelebrated) {
            Haptics.newRecord();
          }
          break;
        }
        case "Settled":
          Haptics.thud();
          // Der Rekord-Jingle lief meist schon live im Lauf; sonst jetzt.
          if (isNewRecord && !bannerState.recordCelebrated) {
            audio.newRecord();
          } else {
            audio.thud();
          }
          break;
      }
    });
    return twistUnlockedThisFrame;
  }

  // ===== UI-Synchronisation pro Frame =====
  var lastPhase = null;
  var lastScoreText = null;
  var lastBannerShown = null;

  function syncUI() {
    var phase = game.phase;
    if (phase !== lastPhase) {
      lastPhase = phase;
      setVisible(el.ready, phase === "READY");
      setVisible(el.hud, phase === "RUNNING" || phase === "DYING");
      setVisible(el.over, phase === "OVER");
      setVisible(el.hudDaily, dailyMode);
      if (phase === "READY") updateReadyUI();
      if (phase === "OVER") updateGameOverUI();
    }

    var scoreText = String(game.score);
    if (scoreText !== lastScoreText) {
      lastScoreText = scoreText;
      el.score.textContent = scoreText;
    }

    var bannerShown = game.phase === "RUNNING" ? bannerText : "";
    if (bannerShown !== lastBannerShown) {
      lastBannerShown = bannerShown;
      el.banner.textContent = bannerShown;
      setVisible(el.banner, bannerShown !== "");
    }

    var showPerfect = game.lastHitPerfect && game.timeSinceHit < 0.6 &&
      game.phase === "RUNNING";
    setVisible(el.perfect, showPerfect);
    if (showPerfect) {
      el.perfect.textContent = t("perfect_plus", perfectPoints);
    }
  }

  // ===== Frame-Loop =====
  var lastFrame = 0;
  function frame(now) {
    var dt = lastFrame === 0 ? 0 : (now - lastFrame) / 1000;
    lastFrame = now;

    var events = game.update(dt);
    fx.flashAlpha = Math.max(0, fx.flashAlpha - dt * 3.5);
    fx.shakeTime = Math.max(0, fx.shakeTime - dt);
    fx.celebrateTime = Math.max(0, fx.celebrateTime - dt);
    if (fx.deathTime >= 0) fx.deathTime += dt;
    bannerState.timeLeft = Math.max(0, bannerState.timeLeft - dt);
    if (bannerState.timeLeft <= 0 && bannerText !== "") {
      bannerText = "";
    }

    var twistUnlockedThisFrame = handleEvents(events);

    // Rekord live feiern: sobald der Lauf den alten Bestwert überholt.
    if (game.phase === "RUNNING" && !bannerState.recordCelebrated &&
        uiBestScore > 0 && game.score > uiBestScore) {
      bannerState.recordCelebrated = true;
      showBanner(t("banner_record"), 2.2, 2);
      fx.celebrateTime = Renderer.CELEBRATE_SECONDS;
      Haptics.newRecord();
      audio.newRecord();
    }

    // Stufen-Feedback: jede 5er-Stufe färbt den Himmel um.
    var stage5 = Math.floor(game.score / 5);
    if (game.phase === "RUNNING" && stage5 > bannerState.lastStage) {
      bannerState.lastStage = stage5;
      if (!twistUnlockedThisFrame) {
        showBanner(t("banner_stage"), 1.6, 1);
        fx.celebrateTime = Renderer.CELEBRATE_SECONDS;
        Haptics.unlock();
        audio.unlockSound();
      }
    }
    if (game.phase === "READY") {
      bannerState.lastStage = 0;
    }

    syncUI();
    Renderer.drawWorld(ctx, canvas.width, canvas.height, game, fx, skin);
    requestAnimationFrame(frame);
  }
  requestAnimationFrame(frame);

  // ===== Eingabe =====

  // Audio erst nach erster User-Interaktion entsperren (iOS).
  document.addEventListener("pointerdown", function () { audio.unlock(); }, true);

  // Spiel-Tap: irgendwo auf der Bühne. In READY/OVER startet er einen
  // Lauf — vorher Seed und Tag für den aktuellen Modus setzen.
  stage.addEventListener("pointerdown", function () {
    if (game.phase === "READY" || game.phase === "OVER") {
      prepareRun();
    }
    game.tap();
  });

  /** Knopf, dessen Tap NICHT als Spiel-Tap durchschlagen darf. */
  function button(node, onClick) {
    node.addEventListener("pointerdown", function (e) { e.stopPropagation(); });
    node.addEventListener("click", function (e) {
      e.stopPropagation();
      onClick();
    });
  }

  button(el.btnSound, function () {
    audio.muted = !audio.muted;
    ScoreStore.soundMuted = audio.muted;
    updateSoundButton();
  });

  button(el.btnHelp, function () { show(el.help); });
  button(el.btnHelpOver, function () { show(el.help); });

  button(el.btnDaily, function () {
    dailyMode = true;
    setVisible(el.hudDaily, true);
    prepareRun();
    game.tap();
  });

  button(el.btnSkins, function () {
    buildSkinList();
    show(el.skins);
  });

  button(el.btnMenu, function () {
    dailyMode = false;
    game.reset();
  });

  // Overlays: Tap irgendwo schließt — und wird konsumiert, damit er
  // nicht gleichzeitig als Spiel-Tap (Sofort-Neustart!) durchschlägt.
  [el.help, el.skins].forEach(function (overlay) {
    overlay.addEventListener("pointerdown", function (e) { e.stopPropagation(); });
    overlay.addEventListener("click", function (e) {
      e.stopPropagation();
      hide(overlay);
    });
  });

  // Doppeltipp-Zoom auf iOS unterbinden.
  document.addEventListener("dblclick", function (e) { e.preventDefault(); });

  // ===== Service Worker (Cache-First, komplett offline spielbar) =====
  if ("serviceWorker" in navigator) {
    window.addEventListener("load", function () {
      navigator.serviceWorker.register("sw.js").catch(function () {
        // Offline-Cache ist optional — das Spiel läuft auch ohne.
      });
    });
  }

  updateReadyUI();
})();

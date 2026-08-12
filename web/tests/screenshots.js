/*
 * Erzeugt die Vergleichs-Screenshots unter tests/screenshots/ — damit man
 * die PWA später wieder neben die Android-App (store/screenshots/) legen
 * kann, ohne jedes Mal von Hand durch alle Overlays zu klicken.
 *
 * Voraussetzung: ein lokaler Server auf dem web/-Ordner und Playwright.
 *
 *   cd web
 *   python3 -m http.server 8765 &
 *   npx playwright install chromium   # nur beim ersten Mal
 *   node tests/screenshots.js
 *
 * Auflösung: 393x852 CSS-Pixel bei dpr 3 = 1179x2556 echte Pixel, also
 * ein aktuelles Handy — dieselbe Größenordnung wie die Play-Store-Bilder
 * (1080x1920). Der Auto-Spieler greift sich die Spielinstanz aus main.js
 * und tappt in der Zonenmitte, damit "im Lauf" reproduzierbar aussieht.
 */
"use strict";

const path = require("path");
const fs = require("fs");
const { chromium } = require("playwright");

const OUT = path.join(__dirname, "screenshots");
const URL = process.env.DOTTIE_URL || "http://localhost:8765/index.html";
const LOCALE = process.env.DOTTIE_LOCALE || "de-DE";

(async () => {
  fs.mkdirSync(OUT, { recursive: true });
  const browser = await chromium.launch();
  const ctx = await browser.newContext({
    viewport: { width: 393, height: 852 },
    deviceScaleFactor: 3,
    locale: LOCALE,
    isMobile: true,
    hasTouch: true
  });
  const page = await ctx.newPage();
  page.on("pageerror", (e) => console.log("PAGEERROR:", e.message));

  // main.js hält die Spielinstanz in einer Closure — hier wird der
  // Konstruktor beim Setzen von window.TimingGame eingepackt, damit der
  // Auto-Spieler an das Objekt kommt.
  await page.addInitScript(() => {
    let real;
    Object.defineProperty(window, "TimingGame", {
      configurable: true,
      get() { return real; },
      set(v) {
        const Wrapped = function (...args) {
          const inst = new v(...args);
          window.__game = inst;
          return inst;
        };
        Wrapped.prototype = v.prototype;
        Object.getOwnPropertyNames(v).forEach((k) => {
          if (!["length", "name", "prototype"].includes(k)) Wrapped[k] = v[k];
        });
        real = Wrapped;
      }
    });
  });

  const shot = (name) => page.screenshot({ path: path.join(OUT, name) });

  // 1) Startscreen, frischer Zustand
  await page.goto(URL);
  await page.waitForTimeout(900);
  await shot("01-start.png");

  // 2) Hilfe
  await page.click("#btn-help");
  await page.waitForTimeout(400);
  await shot("04-help.png");
  await page.mouse.click(196, 780);
  await page.waitForTimeout(300);

  // 3) Skin-Picker
  await page.click("#btn-skins");
  await page.waitForTimeout(400);
  await shot("05-skins.png");
  await page.mouse.click(196, 40);
  await page.waitForTimeout(300);

  // 4) Startscreen mit Rekord-, Daily- und Versuchszeile
  await page.evaluate(() => {
    const today = Math.floor(Date.now() / 86400000);
    localStorage.setItem("dottie_best_score", "23");
    localStorage.setItem("dottie_run_count", "17");
    localStorage.setItem("dottie_best_perfect_streak", "4");
    localStorage.setItem("dottie_daily_day", String(today));
    localStorage.setItem("dottie_daily_best", "12");
    localStorage.setItem("dottie_daily_streak", "3");
  });
  await page.goto(URL);
  await page.waitForTimeout(900);
  await shot("02-start-record.png");

  // 5) Im Lauf: treffen, bis Stufe und Twist-Banner da sind
  await page.evaluate(() => {
    const g = window.__game;
    const stage = document.getElementById("stage");
    window.__auto = true;
    stage.dispatchEvent(new PointerEvent("pointerdown", { bubbles: true }));
    (function loop() {
      if (window.__auto && g.phase === "RUNNING" && g.timeSinceHit > 0.25 &&
          Math.abs(g.relativeToZone()) <= g.effectiveZoneHalf() * 0.3) {
        stage.dispatchEvent(new PointerEvent("pointerdown", { bubbles: true }));
      }
      requestAnimationFrame(loop);
    })();
  });
  await page.waitForFunction(
    () => window.__game.score >= 7 || window.__game.phase !== "RUNNING",
    null, { timeout: 30000 }
  ).catch(() => {});
  await page.waitForTimeout(120);
  await shot("03-run.png");

  // 6) Game Over: Auto-Spieler aus, nächste Zone verpassen
  await page.evaluate(() => { window.__auto = false; });
  await page.waitForFunction(() => window.__game.phase === "OVER", null, { timeout: 20000 });
  await page.waitForTimeout(1200);
  await shot("06-gameover.png");

  await browser.close();
  console.log("Screenshots in " + OUT);
})();

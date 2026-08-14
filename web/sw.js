/*
 * Service Worker: Cache-First für alle Assets — das Spiel ist nach dem
 * ersten Besuch komplett offline spielbar. Bei einer neuen Version die
 * CACHE_VERSION hochzählen, damit alte Caches ersetzt werden.
 *
 * Ausnahme sind Navigationen (der Aufruf der Seite selbst): Die laufen
 * Network-First mit Cache als Rückfall. Sonst bleiben installierte
 * Spieler:innen an einer alten index.html hängen, sobald der Bump der
 * CACHE_VERSION einmal vergessen wird — die Seite lädt dann für immer aus
 * dem Cache und der neue Service Worker kommt nie zum Zug. Offline
 * ändert sich nichts: Ohne Netz antwortet weiter der Cache.
 *
 * Die ASSETS-Liste muss alle ausgelieferten Dateien enthalten; ein Test
 * in tests/run-tests.js vergleicht sie mit dem Inhalt von web/.
 */
var CACHE_VERSION = "dottie-v16";

var ASSETS = [
  "./",
  "./index.html",
  "./style.css",
  "./manifest.webmanifest",
  "./bytesized_regular.ttf",
  "./icon-180.png",
  "./icon-192.png",
  "./icon-512.png",
  "./icon-maskable-512.png",
  "./js/strings.js",
  "./js/skins.js",
  "./js/scenes.js",
  "./js/progress.js",
  "./js/cardstyle.js",
  "./js/game.js",
  "./js/daily.js",
  "./js/synth.js",
  "./js/store.js",
  "./js/render.js",
  "./js/pixelbutton.js",
  "./js/audio.js",
  "./js/main.js"
];

self.addEventListener("install", function (event) {
  event.waitUntil(
    caches.open(CACHE_VERSION).then(function (cache) {
      return cache.addAll(ASSETS);
    }).then(function () {
      return self.skipWaiting();
    })
  );
});

self.addEventListener("activate", function (event) {
  event.waitUntil(
    caches.keys().then(function (keys) {
      return Promise.all(keys.map(function (key) {
        if (key !== CACHE_VERSION) return caches.delete(key);
      }));
    }).then(function () {
      return self.clients.claim();
    })
  );
});

/** Antwort im aktuellen Cache ablegen (nur eigene, erfolgreiche). */
function cacheIfOwn(request, response) {
  if (response.ok && request.url.indexOf(self.location.origin) === 0) {
    var copy = response.clone();
    caches.open(CACHE_VERSION).then(function (cache) {
      cache.put(request, copy);
    });
  }
  return response;
}

self.addEventListener("fetch", function (event) {
  if (event.request.method !== "GET") return;

  // Navigation: erst Netz, dann Cache. Damit landet eine neue Version
  // sofort auf dem Gerät, statt hinter einem alten Cache zu warten.
  if (event.request.mode === "navigate") {
    event.respondWith(
      fetch(event.request).then(function (response) {
        return cacheIfOwn(event.request, response);
      }).catch(function () {
        return caches.match(event.request, { ignoreSearch: true }).then(function (cached) {
          // Offline und die genaue URL ist nicht im Cache (z. B. mit
          // Query-String gestartet): auf die Startseite zurückfallen.
          return cached || caches.match("./index.html");
        });
      })
    );
    return;
  }

  event.respondWith(
    caches.match(event.request, { ignoreSearch: true }).then(function (cached) {
      if (cached) return cached;
      return fetch(event.request).then(function (response) {
        return cacheIfOwn(event.request, response);
      });
    })
  );
});

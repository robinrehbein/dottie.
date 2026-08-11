/*
 * Service Worker: Cache-First für alle Assets — das Spiel ist nach dem
 * ersten Besuch komplett offline spielbar. Bei einer neuen Version die
 * CACHE_VERSION hochzählen, damit alte Caches ersetzt werden.
 */
var CACHE_VERSION = "dottie-v1";

var ASSETS = [
  "./",
  "./index.html",
  "./style.css",
  "./manifest.webmanifest",
  "./bytesized_regular.ttf",
  "./icon-180.png",
  "./icon-512.png",
  "./js/strings.js",
  "./js/skins.js",
  "./js/game.js",
  "./js/daily.js",
  "./js/synth.js",
  "./js/store.js",
  "./js/render.js",
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

self.addEventListener("fetch", function (event) {
  if (event.request.method !== "GET") return;
  event.respondWith(
    caches.match(event.request, { ignoreSearch: true }).then(function (cached) {
      if (cached) return cached;
      return fetch(event.request).then(function (response) {
        // Nur eigene, erfolgreiche Antworten nachträglich cachen.
        if (response.ok && event.request.url.indexOf(self.location.origin) === 0) {
          var copy = response.clone();
          caches.open(CACHE_VERSION).then(function (cache) {
            cache.put(event.request, copy);
          });
        }
        return response;
      });
    })
  );
});

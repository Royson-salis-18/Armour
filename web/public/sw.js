// Minimal service worker: makes ARMOR installable, caches the app shell and
// lets the page raise notifications while it is in the background.
const SHELL = 'armor-shell-v11';
const FILES = ['/', '/app.css', '/app.js', '/icon.svg', '/manifest.webmanifest', '/vendor/leaflet/leaflet.js', '/vendor/leaflet/leaflet.css', '/vendor/maplibre/maplibre-gl.js', '/vendor/maplibre/maplibre-gl.css', '/vendor/maplibre/leaflet-maplibre-gl.js'];

self.addEventListener('install', (e) => {
  e.waitUntil(caches.open(SHELL).then((c) => c.addAll(FILES)).then(() => self.skipWaiting()));
});
self.addEventListener('activate', (e) => {
  e.waitUntil(caches.keys().then((keys) => Promise.all(keys.filter((k) => k !== SHELL).map((k) => caches.delete(k)))).then(() => self.clients.claim()));
});
self.addEventListener('fetch', (e) => {
  const url = new URL(e.request.url);
  if (e.request.method !== 'GET' || url.origin !== location.origin || url.pathname.startsWith('/api/')) return;
  // Network first so updates land straight away; cache when offline.
  e.respondWith(fetch(e.request).then((res) => {
    const copy = res.clone();
    if (res.ok) caches.open(SHELL).then((c) => c.put(url.pathname.startsWith('/r/') ? '/' : e.request, copy));
    return res;
  }).catch(() => caches.match(url.pathname.startsWith('/r/') ? '/' : e.request)));
});
self.addEventListener('notificationclick', (e) => {
  e.notification.close();
  e.waitUntil(self.clients.matchAll({ type: 'window' }).then((list) => (list[0] ? list[0].focus() : self.clients.openWindow('/'))));
});

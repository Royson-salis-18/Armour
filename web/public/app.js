/* ARMOR web app: live group rides on a real map.
   Talks to server.js; state is polled every 2 s. */
'use strict';

const $ = (s, el = document) => el.querySelector(s);
const $$ = (s, el = document) => [...el.querySelectorAll(s)];
const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

const POLL_MS = 2000;
const SEND_EVERY_MS = 3000;
const STALE_MS = 60000;
const HAZARD_WARN_M = 350;
const CRASH_G = 4;
const CRASH_COUNTDOWN_S = 30;
const LOW_BATTERY = 15;

const HAZARD_TYPES = {
  POTHOLE: ['pothole', 'Pothole'], GRAVEL: ['gravel', 'Gravel'], OIL: ['oil', 'Oil / slick'], WATER: ['water', 'Water'],
  ANIMAL: ['animal', 'Animal'], POLICE: ['police', 'Police'], ACCIDENT: ['accident', 'Accident'], ROADWORK: ['roadwork', 'Roadwork'], OTHER: ['alert', 'Other'],
};
const STOP_REASONS = [['fuel', 'Fuel'], ['coffee', 'Break'], ['food', 'Food'], ['group', 'Regroup'], ['camera', 'Photo'], ['wrench', 'Bike issue']];
const COLORS = ['#FF6B1A', '#4FC3F7', '#7BD88F', '#F06292', '#FFD54F', '#BA68C8', '#4DB6AC', '#FF8A65', '#90A4AE', '#AED581'];

// ---- icons (stroke, 24x24) -----------------------------------------------
const ICONS = {
  fuel: '<path d="M14 22V4a2 2 0 00-2-2H6a2 2 0 00-2 2v18M3 22h12M4 9h10M14 13h2a2 2 0 012 2v2a2 2 0 002 2 2 2 0 002-2V9.8a2 2 0 00-.6-1.4L18 5"/>',
  coffee: '<path d="M17 8h1a4 4 0 010 8h-1M3 8h14v9a4 4 0 01-4 4H7a4 4 0 01-4-4V8zM6 2v2M10 2v2M14 2v2"/>',
  food: '<path d="M3 2v7a2 2 0 002 2h4a2 2 0 002-2V2M7 2v20M21 15V2a5 5 0 00-5 5v6a2 2 0 002 2h3zm0 0v7"/>',
  group: '<path d="M16 21v-2a4 4 0 00-4-4H6a4 4 0 00-4 4v2M9 11a4 4 0 100-8 4 4 0 000 8zM22 21v-2a4 4 0 00-3-3.9M16 3.1a4 4 0 010 7.8"/>',
  camera: '<path d="M23 19a2 2 0 01-2 2H3a2 2 0 01-2-2V8a2 2 0 012-2h4l2-3h6l2 3h4a2 2 0 012 2zM12 17a4 4 0 100-8 4 4 0 000 8z"/>',
  wrench: '<path d="M14.7 6.3a1 1 0 000 1.4l1.6 1.6a1 1 0 001.4 0l3.8-3.8a6 6 0 01-7.9 7.9l-6.9 6.9a2.1 2.1 0 01-3-3l6.9-6.9a6 6 0 017.9-7.9z"/>',
  flag: '<path d="M4 15s1-1 4-1 5 2 8 2 4-1 4-1V3s-1 1-4 1-5-2-8-2-4 1-4 1zM4 22v-7"/>',
  pin: '<path d="M20 10c0 6-8 12-8 12S4 16 4 10a8 8 0 0116 0z"/><circle cx="12" cy="10" r="3"/>',
  bike: '<circle cx="18.5" cy="17.5" r="3.5"/><circle cx="5.5" cy="17.5" r="3.5"/><circle cx="15" cy="5" r="1"/><path d="M12 17.5V14l-3-3 4-3 2 3h2"/>',
  hospital: '<rect x="3" y="3" width="18" height="18" rx="3"/><path d="M12 8v8M8 12h8"/>',
  atm: '<rect x="2" y="5" width="20" height="14" rx="2"/><path d="M2 10h20"/>',
  tyre: '<circle cx="12" cy="12" r="9"/><circle cx="12" cy="12" r="3"/><path d="M12 3v6M12 15v6M3 12h6M15 12h6"/>',
  compass: '<circle cx="12" cy="12" r="10"/><path d="M16.2 7.8l-2 6.3-6.4 2.1 2-6.3z"/>',
  pulse: '<path d="M19 14c1.5-1.5 3-3.2 3-5.5A5.5 5.5 0 0016.5 3c-1.8 0-3 .5-4.5 2-1.5-1.5-2.7-2-4.5-2A5.5 5.5 0 002 8.5c0 2.3 1.5 4 3 5.5l7 7z"/><path d="M3.2 12h4l2-3 3 6 2-3h4.6"/>',
  hand: '<path d="M18 11V6a2 2 0 00-4 0v5M14 10V4a2 2 0 00-4 0v6M10 10.5V6a2 2 0 00-4 0v8M18 8a2 2 0 114 0v6a8 8 0 01-8 8h-2c-2.8 0-4.5-.9-5.9-2.4L3.4 16a2 2 0 013-2.8L8 15"/>',
  stop: '<path d="M7.9 2h8.2L22 7.9v8.2L16.1 22H7.9L2 16.1V7.9z"/><path d="M9 9h6v6H9z"/>',
  timer: '<circle cx="12" cy="13" r="8"/><path d="M12 9v4l2 2M9 2h6"/>',
  mic: '<rect x="9" y="2" width="6" height="12" rx="3"/><path d="M5 11a7 7 0 0014 0M12 18v4"/>',
  walk: '<circle cx="12" cy="5" r="1.5"/><path d="M9 20l3-6 3 6M6 8l6 2 6-2M12 10v4"/>',
  exit: '<path d="M9 21H5a2 2 0 01-2-2V5a2 2 0 012-2h4M16 17l5-5-5-5M21 12H9"/>',
  pause: '<path d="M6 4h4v16H6zM14 4h4v16h-4z"/>',
  play: '<path d="M6 4l14 8-14 8z"/>',
  close: '<path d="M18 6L6 18M6 6l12 12"/>',
  siren: '<circle cx="12" cy="12" r="10"/><path d="M12 7v6M12 16.5h.01"/>',
  battery: '<rect x="2" y="7" width="16" height="10" rx="2"/><path d="M22 11v2M6 11v2"/>',
  route: '<circle cx="6" cy="19" r="3"/><circle cx="18" cy="5" r="3"/><path d="M9 19h8.5a3.5 3.5 0 000-7h-11a3.5 3.5 0 010-7H15"/>',
  alert: '<path d="M10.3 3.9L1.8 18a2 2 0 001.7 3h17a2 2 0 001.7-3L13.7 3.9a2 2 0 00-3.4 0zM12 9v4M12 17h.01"/>',
  pothole: '<ellipse cx="12" cy="14" rx="9" ry="5"/><ellipse cx="12" cy="14" rx="4" ry="1.8"/><path d="M12 3v4"/>',
  gravel: '<circle cx="7" cy="16" r="2.5"/><circle cx="16" cy="17" r="3"/><circle cx="11" cy="8" r="3"/><circle cx="18.5" cy="8" r="1.5"/>',
  oil: '<path d="M12 2.7l5.7 5.7a8 8 0 11-11.4 0z"/>',
  water: '<path d="M2 8c2 0 2 2 5 2s3-2 5-2 3 2 5 2 3-2 5-2M2 14c2 0 2 2 5 2s3-2 5-2 3 2 5 2 3-2 5-2"/>',
  animal: '<circle cx="11" cy="4" r="2"/><circle cx="18" cy="8" r="2"/><circle cx="20" cy="16" r="2"/><path d="M9 10a5 5 0 015 5v3.5a3.5 3.5 0 01-6.8 1Q6.5 17.5 4.5 16.8A3.5 3.5 0 015.5 10z"/>',
  police: '<path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>',
  accident: '<path d="M13 2L3 14h9l-1 8 10-12h-9z"/>',
  roadwork: '<path d="M12 3l6 17H6zM8.5 13h7M7 17h10M5 21h14"/>',
  nav: '<path d="M3 11l19-9-9 19-2-8-8-2z"/>',
  message: '<path d="M21 15a2 2 0 01-2 2H7l-4 4V5a2 2 0 012-2h14a2 2 0 012 2z"/>',
  share: '<circle cx="18" cy="5" r="3"/><circle cx="6" cy="12" r="3"/><circle cx="18" cy="19" r="3"/><path d="M8.6 13.5l6.8 4M15.4 6.5l-6.8 4"/>',
  search: '<circle cx="11" cy="11" r="8"/><path d="M21 21l-4.3-4.3"/>',
  check: '<path d="M20 6L9 17l-5-5"/>',
  chevron: '<path d="M9 18l6-6-6-6"/>',
  more: '<g fill="currentColor" stroke="none"><circle cx="5" cy="12" r="1.8"/><circle cx="12" cy="12" r="1.8"/><circle cx="19" cy="12" r="1.8"/></g>',
  link: '<path d="M10 13a5 5 0 007 0l3-3a5 5 0 00-7-7l-1 1M14 11a5 5 0 00-7 0l-3 3a5 5 0 007 7l1-1"/>',
  user: '<circle cx="12" cy="8" r="4"/><path d="M4 21a8 8 0 0116 0"/>',
  layers: '<path d="M12 3l9 5-9 5-9-5 9-5z"/><path d="M3 13l9 5 9-5"/>',
  menu: '<path d="M4 7h16M4 12h16M4 17h16"/>',
  headset: '<path d="M3 14v-2a9 9 0 0118 0v2"/><path d="M21 15a2 2 0 01-2 2h-1v-6h1a2 2 0 012 2zM3 15a2 2 0 002 2h1v-6H5a2 2 0 00-2 2zM18 17v1a3 3 0 01-3 3h-2"/>',
};
const ic = (name) => `<svg class="i" viewBox="0 0 24 24" aria-hidden="true">${ICONS[name] || ICONS.pin}</svg>`;

// The ARMOR mark: a location pin carrying an open wheel rim; the A's legs run out of the rim's ends toward the point.
const LOGO = (h, c, face = 'var(--bg)') => `<svg style="width:${h * 68 / 91}px;height:${h}px;stroke:none" viewBox="16 6 68 91" role="img" aria-label="ARMOR"><defs><linearGradient id="lg" x1="16" y1="6" x2="84" y2="97" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="#FFA55E"/><stop offset=".5" stop-color="#FF6B1A"/><stop offset="1" stop-color="#F24E00"/></linearGradient></defs><path d="M50 97 C50 97 16 64 16 40 A34 34 0 1 1 84 40 C84 64 50 97 50 97 Z" fill="${c || 'url(#lg)'}"/><g fill="none" style="stroke:${face}" stroke-width="5" stroke-linecap="round" stroke-linejoin="round"><path d="M59.4 60.2 A22 22 0 1 0 40.6 60.2"/><path d="M59.4 60.2 L50 25 L40.6 60.2 M44.4 47 L55.6 47"/></g></svg>`;

// ---- storage -------------------------------------------------------------

const store = {
  get(k, d) { try { const v = localStorage.getItem('armor.' + k); return v == null ? d : JSON.parse(v); } catch { return d; } },
  set(k, v) { try { localStorage.setItem('armor.' + k, JSON.stringify(v)); } catch { /* private mode */ } },
  del(k) { try { localStorage.removeItem('armor.' + k); } catch { /* private mode */ } },
};

let profile = Object.assign({ name: '', contactName: '', contactPhone: '', emergency: '112', blood: '', allergies: '', conditions: '', meds: '', readAloud: false, crash: true, assistant: true, air: false, mediaKeys: true }, store.get('profile', {}));
let session = store.get('session', null); // {code, riderId, token}

// ---- runtime state -------------------------------------------------------

const S = {
  ride: null,
  messages: new Map(),
  lastMsgAt: 0,
  seenMsgAt: store.get('seenMsgAt', 0),
  pos: null,          // {lat, lon, acc, speed, heading, at}
  lastSent: 0,
  sentPos: null,
  battery: null,
  charging: false,
  lowSent: false,
  trip: null,         // local stats for the current ride
  screen: null,
  follow: true,
  handled: new Set(store.get('handled', [])), // alert ids I've dealt with
  warnedHazards: new Set(),
  stopSeen: null,
  pollTimer: null,
  pollFails: 0,
  wakeLock: null,
  crash: null,        // active crash countdown
  lastFast: 0,
  panel: null,
};

// ---- api -----------------------------------------------------------------

async function api(path, body) {
  const opts = body === undefined ? {} : { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) };
  const res = await fetch('/api' + path, opts);
  const data = await res.json().catch(() => ({}));
  if (!res.ok) throw Object.assign(new Error(data.error || 'Network error'), { status: res.status });
  return data;
}

async function act(action, extra = {}) {
  if (!session) return;
  try {
    const snap = await api(`/rides/${session.code}/${action}`, { riderId: session.riderId, token: session.token, since: S.lastMsgAt, ...extra });
    apply(snap);
    return snap;
  } catch (e) {
    toast(e.message);
    throw e;
  }
}

// ---- helpers -------------------------------------------------------------

function haversine(a, b) {
  const R = 6371000, t = Math.PI / 180;
  const dLat = (b.lat - a.lat) * t, dLon = (b.lon - a.lon) * t;
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(a.lat * t) * Math.cos(b.lat * t) * Math.sin(dLon / 2) ** 2;
  return 2 * R * Math.asin(Math.sqrt(h));
}
const fmtDist = (m) => m == null ? '-' : m < 1000 ? `${Math.round(m)} m` : `${(m / 1000).toFixed(m < 10000 ? 1 : 0)} km`;
const fmtDur = (ms) => { const m = Math.floor(ms / 60000); return `${Math.floor(m / 60)}:${String(m % 60).padStart(2, '0')}`; };
const ago = (t) => { const s = Math.round((Date.now() - t) / 1000); return s < 60 ? 'just now' : s < 3600 ? `${Math.round(s / 60)} min ago` : `${Math.round(s / 3600)} h ago`; };
const clock = (t) => new Date(t).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
const colorOf = (id) => { let h = 0; for (const c of String(id)) h = (h * 31 + c.charCodeAt(0)) >>> 0; return COLORS[h % COLORS.length]; };
const initials = (n) => String(n || '?').trim().split(/\s+/).map((w) => w[0]).join('').slice(0, 2).toUpperCase();
const me = () => S.ride?.riders.find((r) => r.id === session?.riderId);
const isLead = () => S.ride && session && S.ride.leadId === session.riderId;
const lead = () => S.ride?.riders.find((r) => r.id === S.ride.leadId);
const hasFix = (r) => r && r.lat != null && r.lon != null;
const mapsLink = (lat, lon) => `https://www.google.com/maps/search/?api=1&query=${lat.toFixed(6)},${lon.toFixed(6)}`;
const directions = (lat, lon) => `https://www.google.com/maps/dir/?api=1&destination=${lat.toFixed(6)},${lon.toFixed(6)}&travelmode=driving`;
const inviteUrl = () => `${location.origin}/r/${session.code}`;

function toast(text, top = false) {
  const t = $('#toast');
  t.textContent = text;
  // Top toasts would cover headlines on full-screen alerts; use the bottom there.
  t.classList.toggle('top', top && $('#critical').classList.contains('hidden') && $('#sheetLayer').classList.contains('hidden'));
  t.classList.add('show');
  clearTimeout(toast.t);
  toast.t = setTimeout(() => t.classList.remove('show'), 2600);
}

// Audio/speech/media need a user gesture once; do it on the first tap.
function unlockAudio() {
  try { audioCtx = audioCtx || new (window.AudioContext || window.webkitAudioContext)(); audioCtx.resume(); } catch { /* none */ }
  try { speechSynthesis.speak(Object.assign(new SpeechSynthesisUtterance(''), { volume: 0 })); } catch { /* none */ }
  if (S.screen === 'ride' && profile.mediaKeys !== false) startMediaKeys();
}
document.addEventListener('pointerdown', unlockAudio, { once: true });

function vibrate(p) { try { navigator.vibrate && navigator.vibrate(p); } catch { /* unsupported */ } }

let audioCtx = null;
function beep(kind = 'alert') {
  try {
    audioCtx = audioCtx || new (window.AudioContext || window.webkitAudioContext)();
    const seq = kind === 'sos' ? [880, 660, 880, 660, 880] : kind === 'stop' ? [740, 740, 988] : [660, 880];
    seq.forEach((f, i) => {
      const o = audioCtx.createOscillator(), g = audioCtx.createGain();
      o.type = 'square'; o.frequency.value = f;
      const t0 = audioCtx.currentTime + i * 0.22;
      g.gain.setValueAtTime(0.0001, t0);
      g.gain.exponentialRampToValueAtTime(0.25, t0 + 0.02);
      g.gain.exponentialRampToValueAtTime(0.0001, t0 + 0.18);
      o.connect(g).connect(audioCtx.destination);
      o.start(t0); o.stop(t0 + 0.2);
    });
  } catch { /* no audio */ }
}

async function notify(title, body) {
  if (document.visibilityState === 'visible') return;
  try {
    if (Notification.permission !== 'granted') return;
    const reg = await navigator.serviceWorker?.getRegistration();
    if (reg) reg.showNotification(title, { body, icon: '/icon.svg', badge: '/icon.svg', tag: title, renotify: true, vibrate: [300, 100, 300] });
    else new Notification(title, { body });
  } catch { /* unsupported */ }
}

function saveProfile() { store.set('profile', profile); }
function markHandled(id) { S.handled.add(id); store.set('handled', [...S.handled].slice(-200)); }

// ---- screens -------------------------------------------------------------

function show(name) {
  if (S.screen === name) return;
  S.screen = name;
  for (const id of ['welcome', 'lobby', 'ride', 'summary']) $('#' + id).classList.toggle('hidden', id !== name);
  if (name === 'ride') { initMap(); setTimeout(() => map.invalidateSize(), 50); requestWake(); setTimeout(applyHandsFree, 300); }
  else { releaseWake(); stopContinuous(); stopGestures(); }
}

// ---- welcome -------------------------------------------------------------

$('#brandBig').innerHTML = LOGO(36) + '<span>ARMOR</span>';
const linkCode = (location.pathname.match(/^\/r\/([A-Za-z0-9]{6})/) || [])[1];
$('#nameIn').value = profile.name;
if (linkCode) {
  $('#codeIn').value = linkCode.toUpperCase();
  $('#joinCodeShow').textContent = linkCode.toUpperCase();
  $('#joinBox').classList.remove('hidden');
  $('#joinBtn').classList.replace('ghost', 'primary');
  $('#createBtn').classList.replace('primary', 'ghost');
}

function needName() {
  const n = $('#nameIn').value.trim();
  if (!n) { $('#welErr').textContent = 'Enter your name so the group knows who you are'; $('#nameIn').focus(); return null; }
  profile.name = n; saveProfile();
  return n;
}

$('#createBtn').onclick = async () => {
  const name = needName(); if (!name) return;
  $('#createBtn').disabled = true;
  try {
    const res = await api('/rides', { name: `${name}'s ride`, riderName: name });
    enterRide(res);
  } catch (e) { $('#welErr').textContent = e.message; }
  $('#createBtn').disabled = false;
};

$('#joinBtn').onclick = async () => {
  const name = needName(); if (!name) return;
  const code = $('#codeIn').value.trim().toUpperCase();
  if (code.length !== 6) { $('#welErr').textContent = 'Ride codes are 6 characters'; return; }
  $('#joinBtn').disabled = true;
  try {
    const prev = session && session.code === code ? session : {};
    const res = await api(`/rides/${code}/join`, { riderName: name, riderId: prev.riderId, token: prev.token });
    enterRide(res);
  } catch (e) { $('#welErr').textContent = e.status === 404 ? 'No ride with that code. Check it with the lead.' : e.message; }
  $('#joinBtn').disabled = false;
};
$('#codeIn').addEventListener('keydown', (e) => { if (e.key === 'Enter') $('#joinBtn').click(); });

function enterRide(res) {
  session = { code: res.code, riderId: res.riderId, token: res.token };
  store.set('session', session);
  S.messages.clear(); S.lastMsgAt = 0;
  history.replaceState(null, '', '/r/' + res.code);
  apply(res.ride);
  startGps();
  startPolling();
  initBattery();
  askNotify();
}

function leaveLocal() {
  if (VC.on) { VC.on = false; for (const id of [...VC.peers.keys()]) vcDrop(id); VC.local?.getTracks().forEach((t) => t.stop()); VC.local = null; try { VC.es?.close(); } catch { /* closed */ } VC.es = null; }
  stopPolling();
  session = null; store.del('session');
  S.ride = null; S.trip = null; S.screen = null;
  history.replaceState(null, '', '/');
  $('#joinBox').classList.add('hidden');
  show('welcome');
}

// ---- lobby ---------------------------------------------------------------

$('#lobbyLeave').onclick = () => confirmPanel('Leave this ride?', 'You can rejoin with the code while the ride is on.', 'Leave ride', async () => {
  await act('leave').catch(() => {});
  leaveLocal();
});
$('#shareBtn').onclick = async () => {
  const text = `Join my ARMOR ride. Code ${session.code}`;
  if (navigator.share) { try { await navigator.share({ title: 'ARMOR ride', text, url: inviteUrl() }); return; } catch { return; } }
  copy(`${text}: ${inviteUrl()}`);
};
$('#copyBtn').onclick = () => copy(inviteUrl());
function copy(text) {
  (navigator.clipboard?.writeText(text) || Promise.reject()).then(() => toast('Link copied'), () => prompt('Copy this link', text));
}
$('#startBtn').onclick = async () => {
  unlockAudio();
  // Ask for the microphone now, with a tap, so "Armor" works hands-free once riding.
  try { if (profile.assistant !== false) (await navigator.mediaDevices.getUserMedia({ audio: true })).getTracks().forEach((t) => t.stop()); } catch { /* denied */ }
  await askMotion();
  await act('start').catch(() => {});
};

function renderLobby() {
  const r = S.ride;
  $('#lobbyName').textContent = r.name;
  $('#lobbyCode').textContent = r.code;
  $('#riderCount').textContent = `${r.riders.length} joined`;
  $('#lobbyRiders').innerHTML = r.riders.map((x) => riderRow(x, true)).join('');
  $('#startBtn').classList.toggle('hidden', !isLead());
  $('#waitLead').classList.toggle('hidden', isLead());
  renderMsgs($('#lobbyMsgs'));
}

function riderRow(x, lobby) {
  const mine = x.id === session.riderId;
  const pos = S.pos;
  const d = hasFix(x) && pos && !mine ? fmtDist(haversine(pos, x)) : null;
  const tags = [];
  if (x.id === S.ride.leadId) tags.push('<span class="tag lead">Lead</span>');
  if (x.state === 'STOPPED') tags.push('<span class="tag">Stopped</span>');
  if (x.state === 'OFF_BIKE') tags.push('<span class="tag">Off bike</span>');
  if (openAlerts().some((a) => a.riderId === x.id && a.kind === 'CATCH_UP')) tags.push('<span class="tag lead">Catching up</span>');
  if (openAlerts().some((a) => a.riderId === x.id && (a.kind === 'SOS' || a.kind === 'CRASH'))) tags.push('<span class="tag bad">SOS</span>');
  const stale = !hasFix(x) ? 'No GPS yet' : Date.now() - x.fixAt > STALE_MS ? `Last seen ${ago(x.fixAt)}` : x.speed != null ? `${Math.round(x.speed * 3.6)} km/h` : 'Live';
  const sub = [mine ? 'You' : null, d, stale, x.battery != null ? `${Math.round(x.battery)}% battery` : null].filter(Boolean).join(' · ');
  const btn = isLead() && !mine ? `<button class="mini" data-lead="${x.id}">Make lead</button>`
    : !lobby && !mine && hasFix(x) ? `<a class="mini" href="${directions(x.lat, x.lon)}" target="_blank" rel="noopener">Navigate</a>` : '';
  return `<div class="rider" data-focus="${x.id}"><div class="av" style="background:${colorOf(x.id)}">${esc(initials(x.name))}</div>
    <div class="grow"><div class="row gap8"><span class="nm">${esc(x.name)}</span>${tags.join('')}</div><div class="sub">${esc(sub)}</div></div>${btn}</div>`;
}

document.addEventListener('click', (e) => {
  const lb = e.target.closest('[data-lead]');
  if (lb) {
    e.stopPropagation();
    const r = S.ride.riders.find((x) => x.id === lb.dataset.lead);
    confirmPanel(`Make ${r?.name} the lead?`, 'The lead calls team stops, sets regroup points and ends the ride.', 'Hand over lead', () => act('lead', { toId: lb.dataset.lead }));
    return;
  }
  const f = e.target.closest('[data-focus]');
  if (f && S.screen === 'ride' && !e.target.closest('a,button')) {
    const r = S.ride.riders.find((x) => x.id === f.dataset.focus);
    if (hasFix(r)) { S.follow = false; $('#meBtn').classList.remove('on'); focusOn([r.lat, r.lon], 16); }
  }
});

// ---- chat ----------------------------------------------------------------

function renderMsgs(box) {
  const atBottom = box.scrollHeight - box.scrollTop - box.clientHeight < 60;
  const list = [...S.messages.values()].sort((a, b) => a.at - b.at).slice(-150);
  box.innerHTML = list.map((m) => m.system
    ? `<div class="msg sys">${esc(m.text)} · ${clock(m.at)}</div>`
    : `<div class="msg ${m.riderId === session.riderId ? 'me' : ''}"><div class="who">${esc(m.name)}</div>${esc(m.text)}<time>${clock(m.at)}</time></div>`).join('');
  if (atBottom || !box.dataset.init) { box.scrollTop = box.scrollHeight; box.dataset.init = 1; }
}

document.addEventListener('submit', async (e) => {
  const f = e.target.closest('[data-chat]');
  if (!f) return;
  e.preventDefault();
  const input = $('input', f);
  const text = input.value.trim();
  if (!text) return;
  input.value = '';
  await act('message', { text }).catch(() => { input.value = text; });
});

function sendQuick(text) { return act('message', { text, quick: true }).then(() => toast('Sent to group')); }

// ---- polling -------------------------------------------------------------

function startPolling() {
  stopPolling();
  const tick = async () => {
    if (!session) return;
    try {
      const snap = await api(`/rides/${session.code}?since=${S.lastMsgAt}`);
      S.pollFails = 0;
      apply(snap);
    } catch (e) {
      S.pollFails++;
      if (e.status === 404) { toast('This ride no longer exists'); leaveLocal(); return; }
    }
    $('.live-dot')?.classList.toggle('off', S.pollFails > 2);
    S.pollTimer = setTimeout(tick, document.visibilityState === 'visible' ? POLL_MS : POLL_MS * 3);
  };
  tick();
}
function stopPolling() { clearTimeout(S.pollTimer); S.pollTimer = null; }
document.addEventListener('visibilitychange', () => {
  if (document.visibilityState === 'visible' && session) { startPolling(); if (S.screen === 'ride') requestWake(); }
});

function apply(snap) {
  if (!snap || !session) return;
  const prevStatus = S.ride?.status;
  const fresh = [];
  for (const m of snap.messages) {
    if (!S.messages.has(m.id)) { S.messages.set(m.id, m); fresh.push(m); }
    if (m.at > S.lastMsgAt) S.lastMsgAt = m.at;
  }
  S.ride = snap;
  if (!snap.riders.some((r) => r.id === session.riderId)) { toast('You left this ride'); leaveLocal(); return; }

  if (snap.status === 'riding' && !S.trip) S.trip = loadTrip();
  if (snap.status === 'lobby') show('lobby'), renderLobby();
  else if (snap.status === 'riding') show('ride'), renderRide(fresh);
  else if (snap.status === 'ended') {
    if (prevStatus === 'riding' || S.screen !== 'summary') renderSummary();
    show('summary');
  }
  if (prevStatus === 'lobby' && snap.status === 'riding') { toast('Ride started. Stay safe.', true); vibrate(200); }
}


// ---- GPS -----------------------------------------------------------------

let gpsWatch = null;
function startGps() {
  if (gpsWatch != null) return;
  const note = $('#lobbyGps');
  if (!navigator.geolocation) { note.textContent = 'This browser has no GPS access'; note.className = 'gps-note bad'; return; }
  gpsWatch = navigator.geolocation.watchPosition(onPos, (err) => {
    note.className = 'gps-note bad';
    note.textContent = err.code === 1 ? 'Location blocked. Allow location for this site in your browser settings.' : 'Looking for GPS signal...';
    if (err.code === 1) toast('Location permission is needed to share your position');
  }, { enableHighAccuracy: true, maximumAge: 2000, timeout: 20000 });
}

function onPos(p) {
  const c = p.coords;
  const prev = S.pos;
  const pos = { lat: c.latitude, lon: c.longitude, acc: c.accuracy, heading: c.heading, at: p.timestamp || Date.now() };
  let speed = c.speed;
  if ((speed == null || Number.isNaN(speed)) && prev) {
    const dt = (pos.at - prev.at) / 1000;
    speed = dt > 0 ? haversine(prev, pos) / dt : 0;
  }
  pos.speed = Math.max(0, speed || 0);
  if (pos.speed < 0.6) pos.speed = 0; // GPS jitter while parked
  S.pos = pos;
  if (pos.speed > 4) S.lastFast = Date.now();

  const note = $('#lobbyGps');
  note.className = 'gps-note ok';
  note.textContent = `GPS locked · ±${Math.round(c.accuracy)} m`;

  if (S.trip && S.ride?.status === 'riding') trackTrip(pos, prev);

  const moved = S.sentPos ? haversine(S.sentPos, pos) : Infinity;
  if (Date.now() - S.lastSent > SEND_EVERY_MS || moved > 40) sendPos();
  if (S.screen === 'ride') { drawMe(); updateStats(); checkHazards(); autoState(); }
}

function sendPos() {
  if (!session || !S.pos) return;
  S.lastSent = Date.now(); S.sentPos = S.pos;
  const p = S.pos;
  api(`/rides/${session.code}/location`, {
    riderId: session.riderId, token: session.token,
    lat: p.lat, lon: p.lon, acc: p.acc, speed: p.speed, heading: p.heading ?? undefined, battery: S.battery ?? undefined,
  }).catch(() => {});
}
setInterval(() => { if (S.pos && Date.now() - S.lastSent > 15000) sendPos(); }, 5000);

// ---- trip stats ----------------------------------------------------------

function loadTrip() {
  const t = store.get('trip', null);
  if (t && t.code === session.code) return t;
  return { code: session.code, dist: 0, max: 0, moving: 0, track: [], startedAt: S.ride.startedAt || Date.now() };
}
function trackTrip(pos, prev) {
  const t = S.trip;
  if (prev && pos.acc < 50) {
    const d = haversine(prev, pos);
    const dt = (pos.at - prev.at) / 1000;
    if (d < 500 && dt > 0 && d / dt < 80 && pos.speed > 1) { t.dist += d; t.moving += dt * 1000; }
  }
  t.max = Math.max(t.max, pos.speed);
  const last = t.track[t.track.length - 1];
  if (!last || haversine({ lat: last[0], lon: last[1] }, pos) > 25) {
    t.track.push([+pos.lat.toFixed(6), +pos.lon.toFixed(6)]);
    if (t.track.length > 4000) t.track.splice(0, t.track.length - 4000);
    trackLine?.setLatLngs(t.track); trackCase?.setLatLngs(t.track);
  }
  store.set('trip', t);
}

// ---- map -----------------------------------------------------------------

let map = null, trackLine = null, trackCase = null, meMarker = null, accCircle = null, routeLine = null;
const markers = new Map();
const hazardMarkers = new Map();
let regroupMarker = null, destMarker = null, routeFor = null;
// Base maps need no key. The two road maps are vector tiles (OpenFreeMap, OpenStreetMap data) that we recolour here
// into a quiet Uber-like palette: pale land, white roads, grey blocks. If vectors can't load we fall back to raster tiles.
const OSM_ATTR = '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors';
const VEC_ATTR = '<a href="https://openfreemap.org" target="_blank" rel="noopener">OpenFreeMap</a> &copy; <a href="https://openmaptiles.org/" target="_blank" rel="noopener">OpenMapTiles</a> Data from <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener">OpenStreetMap</a>';
const VECTOR_STYLE_URL = 'https://tiles.openfreemap.org/styles/positron';
const PALETTES = {
  light: { land: '#F1F1F2', block: '#E5E5E8', water: '#D2DBE3', park: '#E2EAE1', road: '#FFFFFF', casing: '#E0E0E4', label: '#6D6F76', halo: '#FFFFFF' },
  dark: { land: '#16171A', block: '#1E1F23', water: '#0F1A23', park: '#1A211B', road: '#2D2E33', casing: '#101113', label: '#9A9CA4', halo: '#16171A' },
};
const ESRI_IMAGERY = 'https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}';
const ESRI_LABELS = 'https://server.arcgisonline.com/ArcGIS/rest/services/Reference/World_Boundaries_and_Places/MapServer/tile/{z}/{y}/{x}';
const ESRI_ATTR = 'Imagery &copy; Esri, Maxar, Earthstar Geographics';
const TOPO_ATTR = 'Map data &copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors, <a href="https://viewfinderpanoramas.org">SRTM</a> | Style &copy; <a href="https://opentopomap.org">OpenTopoMap</a> (CC-BY-SA)';
// The ways to look at the map. "imagery" maps are dark underneath, so routes and trails are drawn light on them.
const TILES = [
  { id: 'light', name: 'Map', desc: 'Clean and light', kind: 'vector', scheme: 'light' },
  { id: 'dark', name: 'Night', desc: 'Easy on the eyes', kind: 'vector', scheme: 'dark' },
  { id: 'streets', name: 'Streets', desc: 'Every road and place name', kind: 'raster', url: 'https://tile.openstreetmap.org/{z}/{x}/{y}.png', attribution: OSM_ATTR, opts: { maxZoom: 19 } },
  { id: 'terrain', name: 'Terrain', desc: 'Hills, contours and trails', kind: 'raster', url: 'https://{s}.tile.opentopomap.org/{z}/{x}/{y}.png', attribution: TOPO_ATTR, opts: { maxZoom: 17, subdomains: 'abc' } },
  { id: 'satellite', name: 'Satellite', desc: 'Aerial photos', kind: 'raster', imagery: true, url: ESRI_IMAGERY, attribution: ESRI_ATTR, opts: { maxZoom: 19 } },
  { id: 'hybrid', name: 'Hybrid', desc: 'Aerial with place names', kind: 'raster', imagery: true, url: ESRI_IMAGERY, attribution: ESRI_ATTR, opts: { maxZoom: 19 }, overlay: { url: ESRI_LABELS, opts: { maxZoom: 19 } } },
];
const RASTER_FALLBACK = {
  light: { kind: 'raster', url: 'https://tile.openstreetmap.org/{z}/{x}/{y}.png', attribution: OSM_ATTR, opts: { maxZoom: 19, className: 'tiles-uber' } },
  dark: { kind: 'raster', url: 'https://tile.openstreetmap.org/{z}/{x}/{y}.png', attribution: OSM_ATTR, opts: { maxZoom: 19, className: 'tiles-dark' } },
};
const PREFERS_DARK = window.matchMedia && matchMedia('(prefers-color-scheme: dark)').matches;
const idxOf = (id) => Math.max(0, TILES.findIndex((t) => t.id === id));
let tileIdx = idxOf(store.get('basemap', PREFERS_DARK ? 'dark' : 'light')), tileLayer = null, tileToken = 0, styleCache = null;
const mapIsDark = () => TILES[tileIdx].scheme === 'dark' || !!TILES[tileIdx].imagery;

// Recolour a positron-style vector style by layer id/type. Unknown layers keep their stock look.
function recolor(style, p) {
  const road = /road|highway|street|motorway|trunk|primary|secondary|tertiary|minor|service|link|bridge|tunnel|path|track|pedestrian|transit/i;
  for (const l of style.layers) {
    l.paint = l.paint || {};
    const id = l.id || '';
    if (l.type === 'background') l.paint['background-color'] = p.land;
    else if (l.type === 'fill') {
      if (/water/i.test(id)) l.paint['fill-color'] = p.water;
      else if (/park|wood|forest|grass|green|landcover|cemetery|pitch|garden|golf|nature/i.test(id)) l.paint['fill-color'] = p.park;
      else if (/building/i.test(id)) { l.paint['fill-color'] = p.block; l.paint['fill-outline-color'] = p.block; l.paint['fill-opacity'] = 1; }
      else if (/landuse|residential|industrial|commercial|aeroway|area|land/i.test(id)) l.paint['fill-color'] = p.land;
    } else if (l.type === 'line') {
      if (/boundary|admin/i.test(id)) { l.layout = Object.assign({}, l.layout, { visibility: 'none' }); }
      else if (/waterway|river|stream|canal/i.test(id)) l.paint['line-color'] = p.water;
      else if (/rail/i.test(id)) l.paint['line-color'] = p.casing;
      else if (/casing/i.test(id)) l.paint['line-color'] = p.casing;
      else if (road.test(id)) l.paint['line-color'] = p.road;
    } else if (l.type === 'symbol') {
      l.paint['text-color'] = p.label; l.paint['text-halo-color'] = p.halo; l.paint['text-halo-width'] = 1.5;
      l.paint['icon-opacity'] = 0.6;
    }
  }
  return style;
}
function loadVectorStyle() {
  styleCache = styleCache || fetch(VECTOR_STYLE_URL).then((r) => { if (!r.ok) throw new Error('style ' + r.status); return r.json(); }).catch((e) => { styleCache = null; throw e; });
  return styleCache.then((st) => JSON.parse(JSON.stringify(st)));
}
function addRaster(t) {
  const base = L.tileLayer(t.url, { attribution: t.attribution, ...t.opts });
  tileLayer = (t.overlay ? L.layerGroup([base, L.tileLayer(t.overlay.url, { ...t.overlay.opts })]) : base).addTo(map);
}

// The bottom sheet covers part of the map on phones, so centre things in the visible part above it.
const sheetPad = () => (S.screen === 'ride' && innerWidth < 900 ? ($('#sheet')?.offsetHeight || 0) : 0);
function focusOn(ll, zoom, animate = true, pan = false) {
  const z = zoom ?? map.getZoom();
  const c = map.unproject(map.project(ll, z).add([0, sheetPad() / 2]), z);
  if (!animate) map.setView(c, z, { animate: false });
  else if (pan) map.panTo(c, { animate: true, duration: 0.5 });
  else map.flyTo(c, z, { duration: 0.7 });
}

// Your trail: orange with a thin halo, like a route line on a ride-hailing map.
function drawTrail() {
  if (!map) return;
  const pts = S.trip?.track || (trackLine ? trackLine.getLatLngs() : []);
  if (trackCase) map.removeLayer(trackCase);
  if (trackLine) map.removeLayer(trackLine);
  const halo = mapIsDark() ? '#16171A' : '#FFFFFF';
  trackCase = L.polyline(pts, { color: halo, weight: 9, opacity: 0.9, lineCap: 'round', lineJoin: 'round', interactive: false }).addTo(map);
  trackLine = L.polyline(pts, { color: '#FF6B1A', weight: 5, opacity: 0.95, lineCap: 'round', lineJoin: 'round', interactive: false }).addTo(map);
}

function initMap() {
  if (map) return;
  map = L.map('map', { zoomControl: false, attributionControl: true, worldCopyJump: true }).setView(S.pos ? [S.pos.lat, S.pos.lon] : [20.6, 78.9], S.pos ? 16 : 5);
  setTiles(tileIdx);
  drawTrail();
  map.on('dragstart', () => { S.follow = false; $('#meBtn').classList.remove('on'); });
  $('#meBtn').classList.add('on');
  map.on('click', (e) => { if (S.pickRegroup) { S.pickRegroup = false; toast('Regroup point set'); act('regroup', { lat: e.latlng.lat, lon: e.latlng.lng, label: 'Regroup here' }); } });
}
async function setTiles(i) {
  tileIdx = i; store.set('basemap', TILES[i].id);
  const my = ++tileToken;
  if (tileLayer) { map.removeLayer(tileLayer); tileLayer = null; }
  const t = TILES[i];
  document.documentElement.dataset.map = t.scheme || t.id;
  if (t.kind === 'raster') addRaster(t);
  else {
    const fallback = () => { if (my !== tileToken) return; if (tileLayer) map.removeLayer(tileLayer); addRaster(RASTER_FALLBACK[t.scheme]); };
    try {
      if (!window.maplibregl || !L.maplibreGL || (maplibregl.supported && !maplibregl.supported())) throw new Error('no webgl');
      const style = recolor(await loadVectorStyle(), PALETTES[t.scheme]);
      if (my !== tileToken) return;
      tileLayer = L.maplibreGL({ style, attribution: VEC_ATTR }).addTo(map);
      let ready = false;
      const gl = tileLayer.getMaplibreMap();
      gl.once('load', () => { ready = true; });
      gl.on('error', (e) => { if (!ready && e && e.error && /style|Failed to fetch|sprite|glyph/i.test(String(e.error.message))) fallback(); });
      setTimeout(() => { if (!ready) fallback(); }, 12000);
    } catch { fallback(); }
  }
  if (S.ride?.destination) { routeFor = null; drawRoute(S.ride.destination); }
  drawTrail();
  const ls = $('#layerSub'); if (ls) ls.textContent = TILES[i].name;
}
// A tile near you, so each choice previews the real place. Vector maps get a drawn swatch.
function previewTile(t) {
  const z = 12, lat = S.pos?.lat ?? 15.49, lon = S.pos?.lon ?? 73.83, r = lat * Math.PI / 180;
  const x = Math.floor((lon + 180) / 360 * 2 ** z), y = Math.floor((1 - Math.log(Math.tan(r) + 1 / Math.cos(r)) / Math.PI) / 2 * 2 ** z);
  return t.url.replace('{s}', 'a').replace('{z}', z).replace('{x}', x).replace('{y}', y);
}
function mapStylePanel() {
  const card = (t, i) => {
    const bg = t.kind === 'vector' ? '' : `style="background-image:url('${previewTile(t)}')"`;
    return `<button class="style-card ${i === tileIdx ? 'on' : ''}" data-i="${i}"><span class="pv ${t.kind === 'vector' ? 'pv-' + t.scheme : ''}" ${bg}></span><b>${t.name}</b><small>${t.desc}</small></button>`;
  };
  openPanel('mapstyle', `<h2>Map view</h2><div class="grid2">${TILES.map(card).join('')}</div><button class="btn ghost" data-close>Close</button>`, (p) => {
    $$('.style-card', p).forEach((b) => (b.onclick = () => { setTiles(+b.dataset.i); closePanel(); toast(`${TILES[tileIdx].name} view`); }));
  });
}
$('#layerBtn').onclick = mapStylePanel;
$('#layerFab').onclick = mapStylePanel;
$('#meBtn').onclick = () => { S.follow = true; $('#meBtn').classList.add('on'); if (S.pos) focusOn([S.pos.lat, S.pos.lon], Math.max(map.getZoom(), 16)); };
$('#fitBtn').onclick = () => {
  const pts = S.ride.riders.filter(hasFix).map((r) => [r.lat, r.lon]);
  if (S.pos) pts.push([S.pos.lat, S.pos.lon]);
  if (S.ride.regroup) pts.push([S.ride.regroup.lat, S.ride.regroup.lon]);
  if (!pts.length) return toast('No one has a GPS fix yet');
  S.follow = false; $('#meBtn').classList.remove('on');
  map.flyToBounds(L.latLngBounds(pts), { paddingTopLeft: [40, 90], paddingBottomRight: [40, sheetPad() + 40], maxZoom: 16, duration: 0.8 });
};

function riderIcon(r, mine) {
  const stale = !mine && Date.now() - (r.fixAt || 0) > STALE_MS;
  const alarm = openAlerts().some((a) => a.riderId === r.id && (a.kind === 'SOS' || a.kind === 'CRASH'));
  const head = r.heading != null && (r.speed || 0) > 1.5 ? `<i class="hd" style="color:${colorOf(r.id)};transform:rotate(${r.heading}deg)"></i>` : '';
  const crown = r.id === S.ride.leadId ? '<i class="crown"></i>' : '';
  return L.divIcon({
    className: '', iconSize: [38, 38], iconAnchor: [19, 19],
    html: `<div class="mk ${mine ? 'me' : ''} ${stale ? 'stale' : ''} ${alarm ? 'alarm' : ''}">${head}<div class="dot" style="background:${colorOf(r.id)}">${esc(initials(r.name))}</div>${crown}</div>`,
  });
}

function drawMe() {
  if (!map || !S.pos) return;
  const r = Object.assign({}, me() || { id: session.riderId, name: profile.name }, { lat: S.pos.lat, lon: S.pos.lon, heading: S.pos.heading, speed: S.pos.speed });
  const ll = [S.pos.lat, S.pos.lon];
  if (!meMarker) {
    accCircle = L.circle(ll, { radius: S.pos.acc, color: '#FF6B1A', weight: 1, opacity: 0.4, fillOpacity: 0.08, interactive: false }).addTo(map);
    meMarker = L.marker(ll, { icon: riderIcon(r, true), zIndexOffset: 1000 }).addTo(map);
    focusOn(ll, 16, false);
  } else {
    meMarker.setLatLng(ll).setIcon(riderIcon(r, true));
    accCircle.setLatLng(ll).setRadius(S.pos.acc);
  }
  if (S.follow) focusOn(ll, map.getZoom(), true, true);
}

function drawRiders() {
  const seen = new Set();
  for (const r of S.ride.riders) {
    if (r.id === session.riderId || !hasFix(r)) continue;
    seen.add(r.id);
    const ll = [r.lat, r.lon];
    const pop = `<b>${esc(r.name)}</b>${r.id === S.ride.leadId ? ' · Lead' : ''}<br>${r.speed != null ? Math.round(r.speed * 3.6) + ' km/h · ' : ''}${ago(r.fixAt)}${S.pos ? ' · ' + fmtDist(haversine(S.pos, r)) : ''}<br><a href="${directions(r.lat, r.lon)}" target="_blank" rel="noopener">Navigate in Google Maps</a>`;
    let m = markers.get(r.id);
    if (!m) { m = L.marker(ll, { icon: riderIcon(r) }).addTo(map).bindPopup(pop); markers.set(r.id, m); }
    else { m.setLatLng(ll).setIcon(riderIcon(r)); m.getPopup().setContent(pop); }
  }
  for (const [id, m] of markers) if (!seen.has(id)) { map.removeLayer(m); markers.delete(id); }

  const hz = new Set();
  for (const h of S.ride.hazards) {
    hz.add(h.id);
    if (hazardMarkers.has(h.id)) continue;
    const [em, label] = HAZARD_TYPES[h.type] || HAZARD_TYPES.OTHER;
    hazardMarkers.set(h.id, L.marker([h.lat, h.lon], { icon: L.divIcon({ className: '', iconSize: [34, 34], iconAnchor: [17, 17], html: `<div class="pin-ic">${ic(em)}</div>` }) })
      .addTo(map).bindPopup(`<b>${label}</b><br>Reported by ${esc(h.by)} · ${ago(h.at)}`));
  }
  for (const [id, m] of hazardMarkers) if (!hz.has(id)) { map.removeLayer(m); hazardMarkers.delete(id); }

  const rg = S.ride.regroup;
  if (rg) {
    const pop = `<b>${esc(rg.label)}</b><br>Set by ${esc(rg.by)}<br><a href="${directions(rg.lat, rg.lon)}" target="_blank" rel="noopener">Navigate in Google Maps</a>`;
    if (!regroupMarker) regroupMarker = L.marker([rg.lat, rg.lon], { icon: L.divIcon({ className: '', iconSize: [34, 34], iconAnchor: [17, 17], html: `<div class="pin-ic rg">${ic('group')}</div>` }) }).addTo(map).bindPopup(pop);
    else regroupMarker.setLatLng([rg.lat, rg.lon]).getPopup().setContent(pop);
  } else if (regroupMarker) { map.removeLayer(regroupMarker); regroupMarker = null; }

  const ds = S.ride.destination;
  if (ds) {
    const pop = `<b>${esc(ds.label)}</b><br><a href="${directions(ds.lat, ds.lon)}" target="_blank" rel="noopener">Navigate in Google Maps</a>`;
    if (!destMarker) destMarker = L.marker([ds.lat, ds.lon], { icon: L.divIcon({ className: '', iconSize: [34, 34], iconAnchor: [17, 17], html: `<div class="pin-ic dest">${ic('flag')}</div>` }) }).addTo(map).bindPopup(pop);
    else destMarker.setLatLng([ds.lat, ds.lon]).getPopup().setContent(pop);
    drawRoute(ds);
    destTip();
  } else {
    if (destMarker) { map.removeLayer(destMarker); destMarker = null; }
    if (routeLine) { map.removeLayer(routeLine); routeLine = null; routeFor = null; }
  }
}

// Distance pill on the destination, like the "4.5 miles" label on a ride-hailing route.
function destTip() {
  const ds = S.ride?.destination;
  if (!destMarker || !ds) return;
  const text = S.routeInfo ? `${fmtDist(S.routeInfo.dist)} · ${Math.max(1, Math.round(S.routeInfo.dur / 60))} min` : S.pos ? fmtDist(haversine(S.pos, ds)) : '';
  if (!text || destMarker._tipText === text) return;
  destMarker._tipText = text;
  destMarker.unbindTooltip().bindTooltip(esc(text), { permanent: true, direction: 'top', offset: [0, -20], className: 'route-tip' });
}

// Road route to the destination from the public OSRM server; best effort.
async function drawRoute(ds) {
  const key = `${ds.lat},${ds.lon}`;
  if (routeFor === key || !S.pos) return;
  routeFor = key;
  try {
    const res = await fetch(`https://router.project-osrm.org/route/v1/driving/${S.pos.lon},${S.pos.lat};${ds.lon},${ds.lat}?overview=full&geometries=geojson`);
    const data = await res.json();
    const route = data.routes?.[0];
    if (!route) return;
    if (routeLine) map.removeLayer(routeLine);
    const pts = route.geometry.coordinates.map(([x, y]) => [y, x]);
    const dark = mapIsDark();
    routeLine = L.layerGroup([
      L.polyline(pts, { color: dark ? '#16171A' : '#FFFFFF', weight: 10, opacity: 0.9, lineCap: 'round', lineJoin: 'round', interactive: false }),
      L.polyline(pts, { color: dark ? '#ECECEE' : '#2B2B2E', weight: 5, opacity: 1, lineCap: 'round', lineJoin: 'round', interactive: false }),
    ]).addTo(map);
    S.routeInfo = { dist: route.distance, dur: route.duration };
    destTip();
  } catch { routeFor = null; }
}

// ---- ride screen ---------------------------------------------------------
//
// Every moment of a ride maps to exactly one "my state" below. The status
// card always shows that state and its next action, so Stop <-> Ride is one
// tap (or one voice command) away whatever happened before.
//
//   SOS        my SOS/crash alert is open           -> I'm OK / call
//   CONFIRM    lead called a team stop, not confirmed -> I've stopped
//   TEAM       team stop, I've stopped              -> lead: Resume all / Cancel
//                                                      rider: Ride on anyway
//   OFF_BIKE   walking around, crash detection off  -> Back on the bike
//   STOPPED    personal stop (reason)               -> Start riding / change reason
//   RIDING     default                              -> Stop (me or team)

function openAlerts() { return (S.ride?.alerts || []).filter((a) => !a.resolvedAt); }
const myAlert = (kind) => openAlerts().find((a) => a.riderId === session.riderId && a.kind === kind);

function myState() {
  const r = S.ride, m = me();
  if (myAlert('SOS') || myAlert('CRASH')) return 'SOS';
  if (myAlert('CATCH_UP') && (!r.teamStop || r.teamStop.waitForId === session.riderId)) return 'CATCHUP';
  if (r.teamStop && !r.teamStop.confirmed.includes(session.riderId)) return 'CONFIRM';
  if (r.teamStop) return 'TEAM';
  if (m?.state === 'OFF_BIKE') return 'OFF_BIKE';
  if (m?.state === 'STOPPED') return 'STOPPED';
  if (myAlert('CATCH_UP')) return 'CATCHUP';
  return 'RIDING';
}

// Distance from me to the nearest rider who is ahead/with the group.
function gapToGroup() {
  if (!S.pos) return null;
  const others = S.ride.riders.filter((x) => x.id !== session.riderId && hasFix(x) && Date.now() - x.fixAt < STALE_MS * 3);
  if (!others.length) return null;
  return Math.min(...others.map((x) => haversine(S.pos, x)));
}
// Distance between a catching-up rider and the closest other rider.
function riderGap(id) {
  const r = S.ride.riders.find((x) => x.id === id);
  if (!hasFix(r)) return null;
  const others = S.ride.riders.filter((x) => x.id !== id && hasFix(x));
  return others.length ? Math.min(...others.map((x) => haversine(r, x))) : null;
}

function statusModel() {
  const r = S.ride, m = me(), ts = r.teamStop, sp = S.pos ? Math.round(S.pos.speed * 3.6) : 0;
  const since = m?.stateAt ? ` · ${fmtDur(Date.now() - m.stateAt).replace(/^0:/, '')} min` : '';
  switch (myState()) {
    case 'SOS': return { cls: 'sos', ic: 'siren', t: 'SOS active', s: `Group alerted · ${openAlerts().find((a) => a.riderId === session.riderId && (a.kind === 'SOS' || a.kind === 'CRASH')).acks.length} responding`,
      a: ['I\'m OK', 'imok', 'go'], b: [`Call ${profile.emergency || '112'}`, 'call'] };
    case 'CONFIRM': return { cls: 'team', ic: 'stop', t: 'Team stop', s: `${ts.by}: ${ts.reason}. Pull over.`, a: ['I\'ve stopped', 'confirm', 'primary'] };
    case 'TEAM': {
      const n = ts.confirmed.length, total = r.riders.length;
      if (isLead()) return { cls: 'team', ic: 'stop', t: `Team stop · ${ts.reason}`, s: `${n}/${total} stopped · ${ago(ts.at).replace(' ago', '')}`, a: ['Resume all', 'resume', 'go'], b: ['Cancel stop', 'cancelstop'] };
      return { cls: 'team', ic: 'stop', t: `Team stop · ${ts.reason}`, s: `Waiting for ${ts.by} to resume · ${n}/${total} stopped`, a: ['Ride on anyway', 'rideon', 'ghost'] };
    }
    case 'OFF_BIKE': return { cls: 'stopped', ic: 'walk', t: 'Off the bike', s: 'Crash detection paused' + since, a: ['Back on bike', 'ride', 'go'] };
    case 'STOPPED': return { cls: 'stopped', ic: '⏸️', t: m.stopReason ? `Stopped · ${m.stopReason}` : 'Stopped', s: 'Group can see you have pulled over' + since,
      a: ['Start riding', 'ride', 'go'], b: ['Change reason', 'stopme'] };
    case 'CATCHUP': {
      const g = gapToGroup();
      const waiting = S.ride.teamStop?.waitForId === session.riderId;
      return { cls: 'team', ic: 'timer', t: waiting ? 'Group is waiting' : 'Catching up', s: `${g != null ? fmtDist(g) + ' to the group · ' : ''}${waiting ? 'they have stopped for you' : 'group told to wait'}`, a: ['I\'ve caught up', 'caughtup', 'go'], b: ['Cancel', 'cancelcatch'] };
    }
    default: return { cls: 'riding', ic: 'bike', t: 'Riding', s: sp > 2 ? `${sp} km/h · sharing live location` : 'Sharing live location', a: ['Stop', 'stop', 'primary'],
      b: isLead() ? ['Stop team', 'teamstop'] : ['Wait for me', 'catchup'] };
  }
}

function renderStatus() {
  const st = statusModel();
  $('#status').className = 'status ' + st.cls;
  $('#status').innerHTML = `<div class="st-ic">${ic(st.ic)}</div><div class="grow"><div class="st-t">${esc(st.t)}</div><div class="st-s">${esc(st.s)}</div></div>
    <div class="st-btns"><button class="btn ${st.a[2]}" data-st="${st.a[1]}">${esc(st.a[0])}</button>${st.b ? `<button class="btn ghost sm" data-st="${st.b[1]}">${esc(st.b[0])}</button>` : ''}</div>`;
}

const doState = {
  stop: () => stopChooser(),
  stopme: () => stopMePanel(),
  ride: () => act('state', { state: 'OK' }).then(() => say('Riding')),
  confirm: () => { const ts = S.ride.teamStop; closeCritical(); act('confirmStop', { stopId: ts.id }).then(() => say('Stopped for the team')); },
  resume: () => act('resume').then(() => say('Everyone is rolling again')),
  cancelstop: () => confirmPanel('Cancel the team stop?', 'Everyone is told the stop is off and can keep riding.', 'Cancel team stop', () => act('resume', { cancel: true })),
  rideon: () => confirmPanel('Ride on without the group?', `${S.ride.teamStop?.by || 'The lead'} still has the team stopped. The group will see that you left the stop.`, 'Ride on', () => act('state', { state: 'OK' })),
  imok: () => cancelMySos(),
  teamstop: () => teamStopPanel(),
  catchup: () => askWait(),
  caughtup: () => { const a = myAlert('CATCH_UP'); if (a) act('resolve', { alertId: a.id, caughtUp: true }).then(() => say('Caught up. Nice.')); },
  cancelcatch: () => { const a = myAlert('CATCH_UP'); if (a) act('resolve', { alertId: a.id }).then(() => say('Wait request cancelled')); },
  call: () => { location.href = `tel:${profile.emergency || '112'}`; },
};
document.addEventListener('click', (e) => { const b = e.target.closest('[data-st]'); if (b) doState[b.dataset.st]?.(); });

function askWait() {
  if (myAlert('CATCH_UP')) return say('The group already knows you are catching up');
  return act('alert', { kind: 'CATCH_UP', lat: S.pos?.lat, lon: S.pos?.lon }).then(() => { toast('Group told to wait for you'); say('The group knows you are catching up'); });
}

// Active plans, each with its own cancel or answer.
function renderPlans() {
  const r = S.ride, out = [];
  for (const a of openAlerts().filter((x) => x.kind === 'CATCH_UP' && x.riderId !== session.riderId)) {
    const g = riderGap(a.riderId);
    if (!S.spokenCatch?.has(a.id)) { (S.spokenCatch ||= new Set()).add(a.id); say(`${a.name} is catching up. ${isLead() ? 'Slow down or stop and wait.' : 'Ease off.'}`, false, true); vibrate([200, 100, 200]); }
    out.push(`<div class="plan ask"><span class="em">${ic('timer')}</span><div class="grow"><b>${esc(a.name)}</b> is catching up<small>${g != null ? fmtDist(g) + ' behind the group' : 'Waiting for their GPS'} · ${ago(a.at)}</small></div>
      ${isLead() ? `<button class="btn ghost sm" data-slow="${esc(a.name)}">Slow down</button><button class="btn primary sm" data-wait="${esc(a.name)}" data-wait-id="${a.riderId}">Stop &amp; wait</button>` : `<button class="btn ghost sm" data-slow="${esc(a.name)}">Easing off</button>`}</div>`);
  }
  if (S.behindHint && !myAlert('CATCH_UP') && myState() === 'RIDING') out.push(`<div class="plan ask"><span class="em">${ic('route')}</span><div class="grow">Falling behind?<small>${fmtDist(gapToGroup() || 0)} from the group</small></div><button class="btn primary sm" data-st="catchup">Ask them to wait</button><button class="btn ghost sm" data-do="nohint">I'm fine</button></div>`);
  for (const a of openAlerts().filter((x) => x.kind === 'STOP_REQUEST')) {
    if (a.riderId === session.riderId) out.push(`<div class="plan ask"><span class="em">${ic('hand')}</span><div class="grow">You asked for a stop: ${esc(a.detail || 'Stop')}<small>Waiting for ${esc(lead()?.name || 'the lead')}</small></div><button class="btn ghost sm" data-resolve="${a.id}">Cancel</button></div>`);
    else if (isLead()) out.push(`<div class="plan ask"><span class="em">${ic('hand')}</span><div class="grow"><b>${esc(a.name)}</b> asks to stop: ${esc(a.detail || 'Stop')}<small>${ago(a.at)}</small></div><button class="btn primary sm" data-accept="${a.id}">Stop team</button><button class="btn ghost sm" data-resolve="${a.id}">Decline</button></div>`);
  }
  const help = myAlert('HELP');
  if (help) out.push(`<div class="plan ask"><span class="em">${ic('hand')}</span><div class="grow">You asked for a hand: ${esc(help.detail || '')}<small>${help.acks.length ? esc(help.acks.map((x) => x.name).join(', ')) + ' coming' : 'Waiting for someone'}</small></div><button class="btn ghost sm" data-resolve="${help.id}">Cancel</button></div>`);
  if (r.regroup) {
    const d = S.pos ? fmtDist(haversine(S.pos, r.regroup)) : '';
    out.push(`<div class="plan"><span class="em">${ic('group')}</span><div class="grow">${esc(r.regroup.label)}<small>${d}${d ? ' · ' : ''}set by ${esc(r.regroup.by)}</small></div><a class="btn ghost sm" href="${directions(r.regroup.lat, r.regroup.lon)}" target="_blank" rel="noopener">Go</a>${isLead() ? '<button class="btn ghost sm" data-do="clearregroup">Cancel</button>' : ''}</div>`);
  }
  if (r.destination) {
    const ri = S.routeInfo ? `${fmtDist(S.routeInfo.dist)} · ~${Math.round(S.routeInfo.dur / 60)} min` : S.pos ? fmtDist(haversine(S.pos, r.destination)) : '';
    out.push(`<div class="plan"><span class="em">${ic('flag')}</span><div class="grow">${esc(r.destination.label)}<small>${ri}</small></div><a class="btn ghost sm" href="${directions(r.destination.lat, r.destination.lon)}" target="_blank" rel="noopener">Go</a>${isLead() ? '<button class="btn ghost sm" data-do="cleardest">Cancel</button>' : ''}</div>`);
  }
  $('#plans').innerHTML = out.join('');
}

function renderTiles() {
  const r = S.ride, L1 = isLead(), m = me();
  const pending = myAlert('STOP_REQUEST');
  $('#teamLbl').textContent = L1 ? (r.teamStop ? 'Resume the team' : 'Stop the team') : pending ? 'Stop requested' : 'Ask for a team stop';
  $('#teamSub').textContent = L1 ? (r.teamStop ? `${r.teamStop.confirmed.length}/${r.riders.length} stopped` : 'Everyone must pull over') : pending ? 'Tap to cancel' : `${lead()?.name || 'Lead'} decides`;
  $('#teamBtn').classList.toggle('on', !!r.teamStop || !!pending);
  $('#regroupLbl').textContent = r.regroup ? 'Regroup set' : 'Regroup point';
  $('#regroupSub').textContent = r.regroup ? r.regroup.label : L1 ? 'Set where everyone meets' : 'Lead sets it';
  $('#regroupBtn').classList.toggle('on', !!r.regroup);
  $('#regroupBtn').classList.toggle('locked', !L1 && !r.regroup);
  $('#destLbl').textContent = r.destination ? r.destination.label : 'Destination';
  $('#destSub').textContent = r.destination ? (L1 ? 'Change or cancel' : 'Set by lead') : L1 ? 'Search a place' : 'Lead sets it';
  $('#destBtn').classList.toggle('locked', !L1 && !r.destination);
  const waiting = myAlert('CATCH_UP');
  $('#waitLbl').textContent = L1 ? 'Slow the group' : waiting ? 'Catching up' : 'Wait for me';
  $('#waitSub').textContent = L1 ? 'Tell everyone to ease off' : waiting ? 'Tap when you have caught up' : 'Ask the group to wait up';
  $('#waitBtn').classList.toggle('on', !!waiting);
  const vn = S.ride.voice.length;
  $('#vcLbl').textContent = VC.on ? 'Voice chat is live' : vn ? 'Join voice chat' : 'Voice chat';
  $('#vcSub').textContent = VC.on ? `${vn} connected${VC.muted ? ' · you are muted' : ''}` : vn ? `${vn} talking now` : 'Talk to the whole group';
  $('#vcBtn').classList.toggle('on', VC.on);
  $('#ridersSub').textContent = `${r.riders.length} on this ride`;
  $('#offLbl').textContent = m?.state === 'OFF_BIKE' ? 'Back on the bike' : 'Off the bike';
  $('#offBtn').classList.toggle('on', m?.state === 'OFF_BIKE');
  $('#setSub').textContent = profile.contactPhone ? `${profile.contactName || 'Contact'} · ${profile.contactPhone}` : 'Add emergency contact';
  $('#exitLbl').textContent = L1 ? 'End ride' : 'Leave ride';
  $('#exitSub').textContent = L1 ? 'For everyone · summary' : 'Stop sharing location';
  $('#grpNote').textContent = L1 ? 'You lead this ride' : `${lead()?.name || '-'} leads`;
  $('#helpBtn').classList.toggle('on', !!myAlert('HELP'));
}

function renderRide(fresh = []) {
  const r = S.ride;
  $('#rideTitle').textContent = r.name;
  $('#rideRiders').textContent = `${r.riders.length} rider${r.riders.length === 1 ? '' : 's'}`;
  if (map) { drawRiders(); drawMe(); }
  updateStats();
  renderStatus(); renderPlans(); renderTiles(); renderHf(); vcSync();

  const unread = [...S.messages.values()].filter((m) => !m.system && m.riderId !== session.riderId && m.at > S.seenMsgAt).length;
  $('#chatBadge').textContent = unread; $('#chatBadge').classList.toggle('hidden', !unread || S.panel === 'chat');
  if (S.panel === 'chat') { renderMsgs($('#panel .msgs')); markRead(); }
  if (S.panel === 'riders') $('#panel .riders').innerHTML = r.riders.map((x) => riderRow(x, false)).join('');
  for (const m of fresh) {
    if (m.at <= S.seenMsgAt) continue;
    if (m.riderId && m.riderId !== session.riderId) { if (S.panel !== 'chat') toast(`${m.name}: ${m.text}`, true); say(`${m.name} says: ${m.text}`); }
    else if (m.system && !m.text.startsWith(profile.name + ' ')) say(m.text, true);
  }
  // Sheet height drives where the hands-free chips sit above it.
  requestAnimationFrame(() => document.documentElement.style.setProperty('--sheet-h', $('#sheet').offsetHeight + 'px'));

  renderBanners();
  handleCritical();
}

function updateStats() {
  if (S.screen !== 'ride') return;
  const sp = S.pos ? Math.round(S.pos.speed * 3.6) : 0;
  $('#spd').textContent = sp;
  $('#dist').textContent = ((S.trip?.dist || 0) / 1000).toFixed(1);
  $('#dur').textContent = fmtDur(Date.now() - (S.ride.startedAt || Date.now()));
  const l = lead();
  if (!isLead() && l && hasFix(l) && S.pos) { $('#gap').textContent = fmtDist(haversine(S.pos, l)).replace(' ', ''); $('#gapLbl').textContent = 'to lead'; }
  else {
    const far = S.pos ? Math.max(0, ...S.ride.riders.filter((x) => hasFix(x) && x.id !== session.riderId).map((x) => haversine(S.pos, x))) : 0;
    $('#gap').textContent = far ? fmtDist(far).replace(' ', '') : '-'; $('#gapLbl').textContent = 'group spread';
  }
}
setInterval(() => { updateStats(); if (S.screen === 'ride') renderStatus(); }, 15000);

// Moving while marked stopped means the rider forgot to tap: catch up.
let movingSince = 0;
function autoState() {
  if (S.ride?.status !== 'riding' || !S.pos) return;
  const st = myState();
  const gap = gapToGroup();
  if (myAlert('CATCH_UP') && gap != null && gap < 250 && !S.caughtUpSent) { S.caughtUpSent = true; doState.caughtup(); toast('You caught up with the group'); setTimeout(() => (S.caughtUpSent = false), 5000); }
  if (!isLead() && st === 'RIDING' && gap != null && gap > 1500) {
    S.behindSince = S.behindSince || Date.now();
    if (!S.behindHint && Date.now() - S.behindSince > 60000 && Date.now() - (S.behindSnooze || 0) > 10 * 60000) {
      S.behindHint = true; renderPlans(); say(`You are ${fmtDist(gap)} behind the group. Say wait for me to tell them.`, false, true);
    }
  } else { S.behindSince = 0; if (S.behindHint) { S.behindHint = false; renderPlans(); } }
  if (S.pos.speed > 5) movingSince = movingSince || Date.now(); else movingSince = 0;
  const moving = movingSince && Date.now() - movingSince > 12000;
  if (!moving) return;
  if (st === 'STOPPED' || st === 'OFF_BIKE') { movingSince = 0; act('state', { state: 'OK' }).then(() => { toast('You are moving, so you are back to riding'); say('You are moving. Marked as riding.'); }); }
  else if (st === 'TEAM' && isLead() && !S.leadMoveHinted) { S.leadMoveHinted = true; toast('You are moving. Resume the team from the status card.', true); say('You are moving. Say resume to get everyone rolling.'); }
  else if (st === 'TEAM' && !isLead() && Date.now() - movingSince > 25000) { movingSince = 0; act('state', { state: 'OK' }); toast('You left the team stop'); }
}

function renderBanners() {
  const out = [];
  const vn = S.ride?.voice?.length || 0;
  if (VC.on) out.push(`<div class="banner glass vc"><span class="bi">${ic('headset')}</span><div class="grow"><b>Voice chat</b> · ${vn} connected${VC.muted ? ' · muted' : ''}</div><button class="btn ghost sm" data-vc="mute">${VC.muted ? 'Unmute' : 'Mute'}</button><button class="btn primary sm" data-vc="leave">Leave</button></div>`);
  else if (vn) out.push(`<div class="banner glass vc"><span class="bi">${ic('headset')}</span><div class="grow"><b>Voice chat</b> · ${vn} talking</div><button class="btn primary sm" data-vc="join">Join</button></div>`);
  for (const a of openAlerts()) {
    if (a.riderId === session.riderId) continue;
    const d = a.lat != null && S.pos ? ` · ${fmtDist(haversine(S.pos, a))}` : '';
    const nav = a.lat != null ? `<a class="btn primary sm" href="${directions(a.lat, a.lon)}" target="_blank" rel="noopener">Go</a>` : '';
    if (a.kind === 'HELP') out.push(`<div class="banner glass warn"><span class="bi">${ic('hand')}</span><div class="grow"><b>${esc(a.name)} needs a hand</b> ${esc(a.detail || '')}${d}</div>${nav}${a.acks.some((x) => x.riderId === session.riderId) ? '' : `<button class="btn ghost sm" data-ack="${a.id}">On it</button>`}</div>`);
    if (a.kind === 'LOW_BATTERY') out.push(`<div class="banner glass"><span class="bi">${ic('battery')}</span><div class="grow"><b>${esc(a.name)}</b> is on low battery (${esc(a.detail)}). Keep them close.</div></div>`);
    if ((a.kind === 'SOS' || a.kind === 'CRASH') && S.handled.has(a.id)) out.push(`<div class="banner glass bad"><span class="bi">${ic('siren')}</span><div class="grow"><b>${esc(a.name)}: ${a.kind === 'SOS' ? 'SOS' : 'possible crash'}</b>${d}${a.acks.length ? '<br>Responding: ' + esc(a.acks.map((x) => x.name).join(', ')) : ''}</div>${nav}${isLead() ? `<button class="btn ghost sm" data-resolve="${a.id}">Clear</button>` : ''}</div>`);
  }
  if (S.pickRegroup) out.push(`<div class="banner glass warn"><span class="bi">${ic('pin')}</span><div class="grow">Tap the map to place the regroup point</div><button class="btn ghost sm" data-do="cancelpick">Cancel</button></div>`);
  $('#banners').innerHTML = out.join('');
}

document.addEventListener('click', (e) => {
  const t = e.target.closest('[data-do],[data-ack],[data-resolve],[data-accept],[data-slow],[data-wait]');
  if (!t) return;
  if (t.dataset.slow) sendQuick(isLead() ? `Slowing down for ${t.dataset.slow}` : `Easing off for ${t.dataset.slow}`);
  if (t.dataset.wait) act('teamStop', { reason: `Waiting for ${t.dataset.wait}`.slice(0, 40), waitForId: t.dataset.waitId });
  if (t.dataset.do === 'nohint') { S.behindHint = false; S.behindSnooze = Date.now(); renderPlans(); }
  if (t.dataset.ack) act('ack', { alertId: t.dataset.ack }).then(() => say('They know you are coming'));
  if (t.dataset.resolve) act('resolve', { alertId: t.dataset.resolve });
  if (t.dataset.accept) { const a = S.ride.alerts.find((x) => x.id === t.dataset.accept); act('teamStop', { reason: a?.detail || 'Stop' }); }
  const d = t.dataset.do;
  if (d === 'cancelpick') { S.pickRegroup = false; renderBanners(); }
  if (d === 'clearregroup') act('regroup', { clear: true });
  if (d === 'cleardest') act('destination', { clear: true });
});

function closeCritical() { const box = $('#critical'); box.classList.add('hidden'); box.dataset.id = ''; }

// Critical full-screen moments: team stop to confirm, a rider's SOS or crash.
function handleCritical() {
  if (S.crash || S.mySos) return; // my own emergency screens win
  const r = S.ride, box = $('#critical');
  const ts = r.teamStop;
  if (ts && ts.waitForId === session.riderId && !S.spokenWait?.has(ts.id)) {
    (S.spokenWait ||= new Set()).add(ts.id);
    say(`${ts.by} stopped the group to wait for you. Ride safe, no rush.`, false, true);
  }
  if (ts && !ts.confirmed.includes(session.riderId)) {
    if (box.dataset.id !== ts.id) {
      box.dataset.id = ts.id;
      box.className = 'critical stop';
      box.innerHTML = `<div class="top-txt"><div class="eyebrow" style="color:#FFB38A">Team stop · called by ${esc(ts.by)}</div><h1>Pull over safely</h1>
        <p style="font-size:20px"><b>${esc(ts.reason)}</b></p><p class="mid" style="margin-top:10px">The lead has stopped the whole group. Find a safe spot, then confirm. Say "stopped" or wave over the phone if your hands are busy.</p></div>
        <div class="btns"><button class="btn primary big" data-st="confirm">I've stopped</button>${hasFix(lead()) ? `<a class="btn ghost" href="${directions(lead().lat, lead().lon)}" target="_blank" rel="noopener">Navigate to ${esc(ts.by)}</a>` : ''}
        <button class="btn ghost" id="cfLater">Hide, I'm finding a spot</button></div>`;
      $('#cfLater').onclick = () => { box.classList.add('hidden'); };
      beep('stop'); vibrate([400, 150, 400, 150, 400]); say(`Team stop called by ${ts.by}. ${ts.reason}. Say stopped when you have pulled over.`, false, true);
      notify('Team stop', `${ts.by}: ${ts.reason}. Pull over and confirm.`);
    }
    return;
  }
  const a = openAlerts().find((x) => (x.kind === 'SOS' || x.kind === 'CRASH') && x.riderId !== session.riderId && !S.handled.has(x.id));
  if (a) {
    if (box.dataset.id !== a.id) {
      box.dataset.id = a.id;
      box.className = 'critical';
      const d = a.lat != null && S.pos ? fmtDist(haversine(S.pos, a)) + ' away' : 'Location unknown';
      box.innerHTML = `<div class="top-txt"><div class="eyebrow" style="color:#FF9A96">${a.kind === 'SOS' ? 'SOS' : 'Possible crash'} · ${clock(a.at)}</div><h1>${esc(a.name)} needs help</h1>
        <p style="font-size:18px">${esc(d)}</p>${a.detail ? `<div class="med">${esc(a.detail)}</div>` : ''}</div>
        <div class="btns">${a.lat != null ? `<a class="btn danger big" href="${directions(a.lat, a.lon)}" target="_blank" rel="noopener" id="goSos">Navigate to ${esc(a.name)}</a>` : ''}
        <button class="btn primary" id="ackSos">I'm responding</button>
        <a class="btn ghost" href="tel:${esc(profile.emergency || '112')}">Call ${esc(profile.emergency || '112')}</a>
        <button class="btn ghost" id="dismissSos">Dismiss</button></div>`;
      const close = () => { markHandled(a.id); closeCritical(); renderBanners(); };
      $('#ackSos').onclick = () => { act('ack', { alertId: a.id }); close(); };
      $('#goSos') && ($('#goSos').onclick = () => { act('ack', { alertId: a.id }); close(); });
      $('#dismissSos').onclick = close;
      beep('sos'); vibrate([600, 200, 600, 200, 600]); say(`Emergency. ${a.name} needs help, ${d}. Say responding to tell them you are coming.`, false, true);
      notify(`${a.name} needs help`, a.kind === 'SOS' ? 'SOS sent. Open ARMOR for their location.' : 'Possible crash detected.');
    }
    return;
  }
  if (box.dataset.id && !['mine', 'crash'].includes(box.dataset.id)) closeCritical();
}

// ---- actions -------------------------------------------------------------

// Highlight the nav item for whatever is open.
function syncNav() {
  const open = $('#sheet')?.classList.contains('open');
  $('#chatBtn')?.classList.toggle('on', S.panel === 'chat');
  $('#ridersNav')?.classList.toggle('on', S.panel === 'riders');
  $('#expandBtn')?.classList.toggle('on', !!open);
}
$('#ridersNav').onclick = () => $('#ridersBtn').click();

function openPanel(kind, html, after) {
  S.panel = kind; syncNav();
  $('#panel').className = 'panel ' + kind + '-panel';
  $('#panel').innerHTML = html;
  $('#sheetLayer').classList.remove('hidden');
  after && after($('#panel'));
}
function closePanel() { S.panel = null; syncNav(); $('#sheetLayer').classList.add('hidden'); if (S.screen === 'ride' && S.ride?.status === 'riding') renderRide(); }
$('#sheetLayer').addEventListener('click', (e) => { if (e.target.closest('[data-close]')) closePanel(); });

function confirmPanel(title, body, okText, ok) {
  openPanel('confirm', `<h2>${esc(title)}</h2><p class="mid" style="margin:0">${esc(body)}</p><button class="btn danger big" id="okBtn">${esc(okText)}</button><button class="btn ghost" data-close>Not now</button>`,
    (p) => { $('#okBtn', p).onclick = () => { closePanel(); ok(); }; });
}

const reasonGrid = (id) => `<div class="grid3" id="${id}">${STOP_REASONS.map(([em, t]) => `<button class="opt" data-r="${t}"><span class="em">${ic(em)}</span>${t}</button>`).join('')}</div>`;

// "Stop" from the status card: who is stopping, then why.
function stopChooser() {
  if (!isLead()) return stopMePanel();
  openPanel('stop', `<h2>Who is stopping?</h2>
    <button class="list-btn" id="chTeam"><span class="ic">${ic('stop')}</span><span><b>The whole team</b><small>Every rider gets a full-screen alert and must confirm</small></span></button>
    <button class="list-btn" id="chMe"><span class="ic">${ic('hand')}</span><span><b>Just me</b><small>The group keeps riding and sees you have pulled over</small></span></button>
    <button class="btn ghost" data-close>Keep riding</button>`, (p) => {
    $('#chTeam', p).onclick = teamStopPanel;
    $('#chMe', p).onclick = stopMePanel;
  });
}

function teamStopPanel() {
  openPanel('stop', `<h2>Stop the team</h2><p class="mid" style="margin:0;font-size:13px">Pick a reason. Riders see it and are told to pull over.</p>${reasonGrid('teamR')}<button class="btn ghost" data-close>Cancel</button>`, (p) => {
    $$('#teamR .opt', p).forEach((b) => (b.onclick = () => { closePanel(); act('teamStop', { reason: b.dataset.r }).then(() => say(`Team stop called. ${b.dataset.r}`)); }));
  });
}

function stopMePanel() {
  const stopped = me()?.state === 'STOPPED';
  openPanel('stop', `<h2>${stopped ? 'Why are you stopped?' : 'Stop just me'}</h2><p class="mid" style="margin:0;font-size:13px">The group sees your reason. You stay on the ride and can start again with one tap.</p>${reasonGrid('meR')}
    <button class="list-btn" id="noReason"><span class="ic">⏸️</span><span><b>No reason</b><small>Just pulling over</small></span></button><button class="btn ghost" data-close>${stopped ? 'Close' : 'Keep riding'}</button>`, (p) => {
    $$('#meR .opt', p).forEach((b) => (b.onclick = () => { closePanel(); act('state', { state: 'STOPPED', reason: b.dataset.r }).then(() => say(`Stopped for ${b.dataset.r}`)); }));
    $('#noReason', p).onclick = () => { closePanel(); act('state', { state: 'STOPPED', reason: '' }).then(() => say('Stopped')); };
  });
}

function askStopPanel() {
  const pending = myAlert('STOP_REQUEST');
  if (pending) return confirmPanel('Cancel your stop request?', `${lead()?.name || 'The lead'} will see it was cancelled.`, 'Cancel request', () => act('resolve', { alertId: pending.id }));
  openPanel('stop', `<h2>Ask for a team stop</h2><p class="mid" style="margin:0;font-size:13px">${esc(lead()?.name || 'The lead')} gets your request and can stop the team or decline. You can cancel it any time.</p>${reasonGrid('askR')}<button class="btn ghost" data-close>Cancel</button>`, (p) => {
    $$('#askR .opt', p).forEach((b) => (b.onclick = () => { closePanel(); act('alert', { kind: 'STOP_REQUEST', detail: b.dataset.r }).then(() => say(`Asked ${lead()?.name || 'the lead'} for a stop`)); }));
  });
}

$('#teamBtn').onclick = () => {
  if (!isLead()) return askStopPanel();
  if (S.ride.teamStop) return doState.resume();
  teamStopPanel();
};

$('#helpBtn').onclick = () => {
  const mineHelp = myAlert('HELP');
  if (mineHelp) return confirmPanel('Cancel your help request?', 'The group is told you are sorted.', 'Cancel request', () => act('resolve', { alertId: mineHelp.id }));
  openPanel('help', `<h2>Need a hand</h2><p class="mid" style="margin:0;font-size:13px">Not an emergency. The group sees where you are and someone can come back for you. For danger, hold SOS instead.</p>
    <div class="grid2" id="helpR">${[['wrench', 'Breakdown'], ['tyre', 'Flat tyre'], ['fuel', 'Out of fuel'], ['compass', 'Lost the group'], ['pulse', 'Feeling unwell'], ['hand', 'Other']].map(([i, t]) => `<button class="opt" data-h="${t}"><span class="em">${ic(i)}</span>${t}</button>`).join('')}</div>
    <button class="btn ghost" data-close>Cancel</button>`, (p) => {
    $$('#helpR .opt', p).forEach((b) => (b.onclick = () => { closePanel(); requestHelp(b.dataset.h); }));
  });
};
function requestHelp(detail) { return act('alert', { kind: 'HELP', detail, lat: S.pos?.lat, lon: S.pos?.lon }).then(() => { toast('The group has been told'); say('The group knows you need a hand'); }); }

$('#hazBtn').onclick = () => {
  if (!S.pos) return toast('Waiting for GPS');
  openPanel('haz', `<h2>Report hazard</h2><p class="mid" style="margin:0;font-size:13px">Drops a marker where you are. Riders behind you get a warning as they get close.</p>
    <div class="grid3" id="hazR">${Object.entries(HAZARD_TYPES).map(([k, [em, t]]) => `<button class="opt" data-k="${k}"><span class="em">${ic(em)}</span>${t}</button>`).join('')}</div><button class="btn ghost" data-close>Cancel</button>`, (p) => {
    $$('#hazR .opt', p).forEach((b) => (b.onclick = () => { closePanel(); reportHazard(b.dataset.k); }));
  });
};
function reportHazard(k) {
  if (!S.pos) return say('No GPS yet');
  return act('alert', { kind: 'HAZARD', hazard: k, lat: S.pos.lat, lon: S.pos.lon }).then(() => { toast('Hazard marked for the group'); say(`${HAZARD_TYPES[k][1]} marked`); });
}

function checkHazards() {
  if (!S.pos || !S.ride) return;
  for (const h of S.ride.hazards) {
    if (h.riderId === session.riderId || S.warnedHazards.has(h.id)) continue;
    const d = haversine(S.pos, h);
    if (d < HAZARD_WARN_M) {
      S.warnedHazards.add(h.id);
      const [em, label] = HAZARD_TYPES[h.type] || HAZARD_TYPES.OTHER;
      toast(`${label} ahead · ${fmtDist(d)}`, true); beep(); vibrate([200, 100, 200]); say(`Caution. ${label} ahead, ${Math.round(d / 10) * 10} metres`, false, true);
    }
  }
}

function markRead() {
  const last = Math.max(S.seenMsgAt, ...[...S.messages.values()].map((m) => m.at));
  S.seenMsgAt = last; store.set('seenMsgAt', last);
  $('#chatBadge').classList.add('hidden');
}

$('#chatBtn').onclick = () => {
  const moving = (S.pos?.speed || 0) > 3;
  const q = ['All good', 'Need fuel', 'Break soon?', 'Slow down', 'Waiting at the junction', 'Lost you, where are you?'];
  openPanel('chat', `<div class="row between"><h2>Chat</h2><button class="icon-btn" data-close aria-label="Close"><svg viewBox="0 0 24 24"><path d="M6 6l12 12M18 6L6 18"/></svg></button></div>
    <div class="msgs"></div><div class="quick ${moving ? 'big' : ''}">${q.map((t) => `<button data-q="${esc(t)}">${esc(t)}</button>`).join('')}</div>
    ${moving ? `<p class="mid" style="margin:0;font-size:13px">You are moving, so typing is off. Tap a reply or speak.</p><button class="btn primary big" id="chatVoice">${ic('mic')} Reply by voice</button>`
      : `<form class="row gap8 chat-form" data-chat><input class="in" maxlength="280" placeholder="Message the group" aria-label="Message"><button class="btn primary sm">Send</button></form>`}`, (p) => {
    $('#chatVoice', p) && ($('#chatVoice', p).onclick = () => { closePanel(); listenOnce(); });
    renderMsgs($('.msgs', p)); markRead();
    $$('[data-q]', p).forEach((b) => (b.onclick = () => sendQuick(b.dataset.q)));
  });
};

$('#waitBtn').onclick = () => {
  if (isLead()) return sendQuick('Ease off, let the group close up');
  return myAlert('CATCH_UP') ? doState.caughtup() : askWait();
};

$('#ridersBtn').onclick = () => openPanel('riders', `<div class="row between"><h2>Riders</h2><button class="icon-btn" data-close aria-label="Close"><svg viewBox="0 0 24 24"><path d="M6 6l12 12M18 6L6 18"/></svg></button></div>
  <p class="mid" style="margin:0;font-size:13px">Tap a rider to find them on the map.${isLead() ? ' You can hand the lead to someone else.' : ''}</p><div class="riders">${S.ride.riders.map((x) => riderRow(x, false)).join('')}</div>`, (p) => {
  p.addEventListener('click', (e) => { if (e.target.closest('[data-focus]') && !e.target.closest('a,button')) closePanel(); });
});

$('#regroupBtn').onclick = () => {
  const r = S.ride;
  if (!isLead()) return r.regroup ? window.open(directions(r.regroup.lat, r.regroup.lon), '_blank') : toast(`${lead()?.name || 'The lead'} sets the regroup point`);
  openPanel('regroup', `<h2>Regroup point</h2><p class="mid" style="margin:0;font-size:13px">Everyone sees it on the map and gets one-tap directions.</p>
    <button class="list-btn" id="rgHere"><span class="ic">${ic('pin')}</span><span><b>Here, where I am</b><small>Uses your GPS position</small></span></button>
    <button class="list-btn" id="rgTap"><span class="ic">${ic('pin')}</span><span><b>Pick on the map</b><small>Tap anywhere on the map</small></span></button>
    ${r.regroup ? `<button class="list-btn" id="rgClr"><span class="ic">${ic('close')}</span><span><b>Cancel regroup point</b><small>${esc(r.regroup.label)}</small></span></button>` : ''}<button class="btn ghost" data-close>Close</button>`, (p) => {
    $('#rgHere', p).onclick = () => { closePanel(); regroupHere(); };
    $('#rgTap', p).onclick = () => { closePanel(); S.pickRegroup = true; $('#sheet').classList.remove('open'); renderBanners(); };
    $('#rgClr', p) && ($('#rgClr', p).onclick = () => { closePanel(); act('regroup', { clear: true }); });
  });
};
function regroupHere() { return S.pos ? act('regroup', { lat: S.pos.lat, lon: S.pos.lon, label: 'Regroup here' }).then(() => say('Regroup point set here')) : say('No GPS yet'); }

$('#destBtn').onclick = () => {
  const ds = S.ride.destination;
  if (!isLead()) return ds ? window.open(directions(ds.lat, ds.lon), '_blank') : toast(`${lead()?.name || 'The lead'} sets the destination`);
  destPanel();
};
$('#nearBtn').onclick = () => nearbyPanel();
$('#offBtn').onclick = () => act('state', { state: me()?.state === 'OFF_BIKE' ? 'OK' : 'OFF_BIKE' });
$('#locBtn').onclick = () => shareLocation();
$('#setBtn').onclick = () => settingsPanel();
$('#exitBtn').onclick = () => isLead()
  ? confirmPanel('End the ride for everyone?', 'Everyone sees the summary and stops sharing location. To leave but keep the ride going, hand the lead to someone in Riders first.', 'End ride', () => act('end'))
  : confirmPanel('Leave the ride?', 'The group stops seeing you on the map. You can rejoin with the code.', 'Leave ride', async () => { await act('leave').catch(() => {}); leaveLocal(); });

$('#navBtn').onclick = () => {
  const r = S.ride, items = [];
  if (r.destination) items.push([r.destination, 'flag', r.destination.label, S.routeInfo ? `${fmtDist(S.routeInfo.dist)} · about ${Math.round(S.routeInfo.dur / 60)} min` : 'Destination']);
  if (r.regroup) items.push([r.regroup, 'group', r.regroup.label, `Set by ${r.regroup.by}`]);
  const l = lead();
  if (l && l.id !== session.riderId && hasFix(l)) items.push([l, 'bike', `${l.name} (lead)`, S.pos ? fmtDist(haversine(S.pos, l)) : '']);
  for (const x of r.riders) if (x.id !== session.riderId && x.id !== l?.id && hasFix(x)) items.push([x, 'bike', x.name, S.pos ? fmtDist(haversine(S.pos, x)) : '']);
  openPanel('nav', `<h2>Navigate</h2><p class="mid" style="margin:0;font-size:13px">Opens turn-by-turn directions in Google Maps. ARMOR keeps sharing your location.</p><div class="results">${items.map(([p, em, t, s]) =>
    `<a class="list-btn" href="${directions(p.lat, p.lon)}" target="_blank" rel="noopener"><span class="ic">${ic(em)}</span><span>${esc(t)}<small>${esc(s)}</small></span></a>`).join('') || '<p class="mid">No destination or riders with GPS yet.</p>'}</div><button class="btn ghost" data-close>Close</button>`);
};

function nearbyPanel() {
  const opts = [['fuel', 'Fuel', 'petrol pump'], ['hospital', 'Hospital', 'hospital'], ['wrench', 'Mechanic', 'motorcycle repair'], ['food', 'Food', 'restaurant'], ['atm', 'ATM', 'atm'], ['police', 'Police', 'police station']];
  const at = S.pos ? `/@${S.pos.lat.toFixed(5)},${S.pos.lon.toFixed(5)},14z` : '';
  openPanel('nearby', `<h2>Nearby</h2><div class="grid3">${opts.map(([em, t, q]) => `<a class="opt" target="_blank" rel="noopener" href="https://www.google.com/maps/search/${encodeURIComponent(q)}${at}"><span class="em">${ic(em)}</span>${t}</a>`).join('')}</div><button class="btn ghost" data-close>Close</button>`);
}

function destPanel() {
  openPanel('dest', `<h2>Destination</h2><form class="row gap8" id="destForm"><input class="in" id="destQ" placeholder="Search a place" autocomplete="off"><button class="btn primary sm">Search</button></form>
    <div class="results" id="destRes"></div>${S.ride.destination ? '<button class="btn ghost" id="clrDest">Cancel destination</button>' : ''}<button class="btn ghost" data-close>Close</button>`, (p) => {
    $('#destQ', p).focus();
    $('#clrDest', p) && ($('#clrDest', p).onclick = () => { closePanel(); act('destination', { clear: true }); });
    $('#destForm', p).onsubmit = async (e) => {
      e.preventDefault();
      const q = $('#destQ', p).value.trim(); if (!q) return;
      $('#destRes', p).innerHTML = '<p class="mid">Searching...</p>';
      try {
        const near = S.pos ? `&viewbox=${S.pos.lon - 2},${S.pos.lat + 2},${S.pos.lon + 2},${S.pos.lat - 2}` : '';
        const res = await fetch(`https://nominatim.openstreetmap.org/search?format=jsonv2&limit=6&q=${encodeURIComponent(q)}${near}`, { headers: { 'Accept-Language': navigator.language } });
        const list = await res.json();
        $('#destRes', p).innerHTML = list.map((x, i) => `<button class="list-btn" data-i="${i}"><span class="ic">${ic('pin')}</span><span>${esc(x.name || x.display_name.split(',')[0])}<small>${esc(x.display_name)}</small></span></button>`).join('') || '<p class="mid">Nothing found.</p>';
        $$('[data-i]', p).forEach((b) => (b.onclick = () => {
          const x = list[b.dataset.i];
          closePanel();
          routeFor = null;
          act('destination', { lat: +x.lat, lon: +x.lon, label: x.name || x.display_name.split(',')[0] });
        }));
      } catch { $('#destRes', p).innerHTML = '<p class="err">Search failed. Check your connection.</p>'; }
    };
  });
}

async function shareLocation() {
  if (!S.pos) return toast('Waiting for GPS');
  const text = `I'm riding with ARMOR. My location: ${mapsLink(S.pos.lat, S.pos.lon)}`;
  if (navigator.share) { try { await navigator.share({ text }); } catch { /* cancelled */ } return; }
  location.href = `sms:?&body=${encodeURIComponent(text)}`;
}

const toggleSheet = () => { const o = $('#sheet').classList.toggle('open'); $('#expandLbl').textContent = o ? 'Close' : 'Menu'; $('#expandIc').innerHTML = ic(o ? 'close' : 'menu'); syncNav(); setTimeout(() => document.documentElement.style.setProperty('--sheet-h', $('#sheet').offsetHeight + 'px'), 420); };
$('#grab').onclick = toggleSheet;
$('#expandBtn').onclick = toggleSheet;

// ---- SOS -----------------------------------------------------------------

let sosTimer = null;
const sosBtn = $('#sosBtn');
const sosStart = (e) => { e.preventDefault(); sosBtn.classList.add('holding'); vibrate(30); sosTimer = setTimeout(() => { sosBtn.classList.remove('holding'); triggerSos('SOS'); }, 1500); };
const sosEnd = () => { clearTimeout(sosTimer); sosBtn.classList.remove('holding'); };
sosBtn.addEventListener('pointerdown', sosStart);
['pointerup', 'pointerleave', 'pointercancel'].forEach((ev) => sosBtn.addEventListener(ev, sosEnd));
sosBtn.addEventListener('contextmenu', (e) => e.preventDefault());

function medicalLine() {
  const p = profile;
  return [p.blood && `Blood ${p.blood}`, p.allergies && `Allergies: ${p.allergies}`, p.conditions && `Conditions: ${p.conditions}`, p.meds && `Meds: ${p.meds}`].filter(Boolean).join('. ');
}

function sosText(kind) {
  const where = S.pos ? mapsLink(S.pos.lat, S.pos.lon) : 'location unavailable';
  const med = medicalLine();
  return `${kind === 'CRASH' ? 'ARMOR: possible crash' : 'ARMOR SOS'}. ${profile.name || 'A rider'} needs help. Location: ${where}${med ? '. ' + med : ''}`;
}

async function triggerSos(kind) {
  beep('sos'); vibrate([500, 200, 500]);
  S.mySos = true;
  try { await act('alert', { kind, lat: S.pos?.lat, lon: S.pos?.lon, detail: medicalLine() }); } catch { /* still show local help */ }
  const box = $('#critical');
  const phone = profile.contactPhone.replace(/[^\d+]/g, '');
  const sms = `sms:${phone}${/iPhone|iPad/.test(navigator.userAgent) ? '&' : '?'}body=${encodeURIComponent(sosText(kind))}`;
  box.dataset.id = 'mine';
  box.className = 'critical';
  box.innerHTML = `<div class="top-txt"><div class="eyebrow" style="color:#FF9A96">${kind === 'CRASH' ? 'Crash detected' : 'SOS sent'} · ${clock(Date.now())}</div><h1>Help is on the way</h1>
    <p>Your group can see your location and has been alerted.${phone ? '' : ' Add an emergency contact in settings so you can text them in one tap.'}</p>
    ${medicalLine() ? `<div class="med"><b>Medical ID</b><br>${esc(medicalLine())}</div>` : ''}${S.pos ? `<div class="med">Location ${S.pos.lat.toFixed(5)}, ${S.pos.lon.toFixed(5)} · ±${Math.round(S.pos.acc)} m</div>` : ''}</div>
    <div class="btns"><a class="btn danger big" href="tel:${esc(profile.emergency || '112')}">Call ${esc(profile.emergency || '112')}</a>
    ${phone ? `<a class="btn primary" href="${sms}">Text ${esc(profile.contactName || profile.contactPhone)}</a>` : `<a class="btn primary" href="sms:?body=${encodeURIComponent(sosText(kind))}">Text someone my location</a>`}
    <button class="btn ghost" id="imOk">I'm OK, cancel the alert</button></div>`;
  $('#imOk').onclick = cancelMySos;
  say(phone ? `S O S sent to your group. Call ${profile.emergency || '112'} from the screen.` : 'S O S sent to your group.', false, true);
}

function cancelMySos() {
  S.mySos = false;
  const box = $('#critical');
  if (box.dataset.id === 'mine') closeCritical();
  for (const a of openAlerts()) if (a.riderId === session.riderId && (a.kind === 'SOS' || a.kind === 'CRASH')) act('resolve', { alertId: a.id });
  toast('Alert cancelled. Group told you are OK.');
  say('Alert cancelled. The group knows you are OK.', false, true);
}

// ---- crash detection -----------------------------------------------------

async function askMotion() {
  try { if (typeof DeviceMotionEvent?.requestPermission === 'function') await DeviceMotionEvent.requestPermission(); } catch { /* denied */ }
}
window.addEventListener('devicemotion', (e) => {
  if (!profile.crash || S.ride?.status !== 'riding' || S.crash || S.mySos || me()?.state === 'OFF_BIKE') return;
  const a = e.accelerationIncludingGravity;
  if (!a || a.x == null) return;
  const g = Math.sqrt(a.x * a.x + a.y * a.y + a.z * a.z) / 9.81;
  // A hard hit only counts if we were riding in the last few seconds.
  if (g > CRASH_G && Date.now() - S.lastFast < 8000) {
    S.crash = { at: Date.now() };
    setTimeout(() => { if ((S.pos?.speed || 0) < 2.5) crashCountdown(); else S.crash = null; }, 4000);
  }
});
document.addEventListener('keydown', (e) => { if (e.key === 'C' && e.shiftKey && e.altKey && S.ride?.status === 'riding') { S.crash = { at: Date.now() }; crashCountdown(); } });

function crashCountdown() {
  let left = CRASH_COUNTDOWN_S;
  const box = $('#critical');
  box.className = 'critical'; box.dataset.id = 'crash';
  box.innerHTML = `<div class="top-txt"><div class="eyebrow" style="color:#FF9A96">Hard impact detected</div><h1>Are you OK?</h1><p>If you don't answer, ARMOR alerts your group with your location.</p></div>
    <div class="count" id="cdN">${left}</div><div class="btns"><button class="btn primary big" id="cdOk">I'm OK</button><button class="btn danger" id="cdSend">Send SOS now</button></div>`;
  beep('sos'); vibrate([800, 200, 800, 200, 800]); say('Hard impact detected. Are you OK? Say I am OK to cancel.', false, true);
  const iv = setInterval(() => {
    left--; $('#cdN') && ($('#cdN').textContent = left);
    if (left % 5 === 0) { beep(); vibrate(400); }
    if (left <= 0) { clearInterval(iv); S.crash = null; S.crashCancel = null; triggerSos('CRASH'); }
  }, 1000);
  S.crashCancel = () => { clearInterval(iv); S.crash = null; S.crashCancel = null; closeCritical(); toast('Glad you are OK'); say('Glad you are OK', false, true); };
  $('#cdOk').onclick = S.crashCancel;
  $('#cdSend').onclick = () => { clearInterval(iv); S.crash = null; S.crashCancel = null; triggerSos('CRASH'); };
}

// ---- hands-free: voice, helmet intercom buttons, air gestures ------------
//
// Bluetooth helmet intercoms (Cardo, Sena, ...) pair with the phone as a
// headset, so:
//   * speech we synthesise plays in the helmet,
//   * the mic we listen on is the helmet mic,
//   * the intercom's play/pause and next/previous buttons arrive as media
//     session actions while ARMOR holds the media session (a silent loop).
// Button map:  play/pause -> talk to ARMOR,  next -> status read-out,
//              previous -> repeat the last message.
// Air gestures use the front camera at 32x24 px, just looking at brightness:
//   one wave -> talk to ARMOR,  two waves -> status,  cover 3 s -> SOS countdown.

const Speech = window.SpeechRecognition || window.webkitSpeechRecognition;
const VC = { on: false, muted: false, local: null, peers: new Map(), seen: new Set(), es: null, ice: null, announced: false };
const HF = { rec: null, listening: false, continuous: false, wakeUntil: 0, lastSaid: '', cam: null, media: false };

function say(text, isSystem = false, urgent = false) {
  HF.lastSaid = text;
  if (!window.speechSynthesis) return;
  if (!profile.readAloud && !urgent && !HF.continuous && !HF.media) return;
  if (isSystem && !profile.readAloud) return;
  try {
    if (urgent) speechSynthesis.cancel();
    const u = new SpeechSynthesisUtterance(text);
    u.rate = 1.05; u.lang = navigator.language || 'en-US';
    // Don't let the hands-free mic hear our own voice.
    u.onstart = () => { HF.speaking = true; };
    u.onend = () => { HF.speaking = false; };
    speechSynthesis.speak(u);
  } catch { /* no tts */ }
}

function statusReadout() {
  const st = statusModel(), parts = [st.t + '.'];
  if (S.pos) parts.push(`${Math.round(S.pos.speed * 3.6)} kilometres per hour.`);
  const l = lead();
  if (l && !isLead() && hasFix(l) && S.pos) parts.push(`${l.name} is ${fmtDist(haversine(S.pos, l)).replace('km', 'kilometres').replace(' m', ' metres')} away.`);
  if (S.ride.destination && S.routeInfo) parts.push(`Destination ${S.ride.destination.label}, about ${Math.round(S.routeInfo.dur / 60)} minutes.`);
  const unread = [...S.messages.values()].filter((m) => !m.system && m.riderId !== session.riderId && m.at > S.seenMsgAt);
  if (unread.length) parts.push(`${unread.length} new message${unread.length > 1 ? 's' : ''}. Last from ${unread[unread.length - 1].name}: ${unread[unread.length - 1].text}`);
  const alerts = openAlerts().filter((a) => a.riderId !== session.riderId && !['LOW_BATTERY'].includes(a.kind));
  for (const a of alerts) parts.push(`${a.name} ${a.kind === 'HELP' ? 'needs a hand' : a.kind === 'STOP_REQUEST' ? 'asked for a stop' : 'needs help'}.`);
  say(parts.join(' '), false, true);
  markRead();
}

function voiceLive(text, on = true) {
  $('#voiceTxt').textContent = text;
  $('#voiceLive').classList.toggle('hidden', !on);
}

// One-shot listen (button, helmet button, wave).
function listenOnce() {
  if (!Speech) { say('Voice commands need Chrome or Safari', false, true); return toast('Voice commands need Chrome or Safari'); }
  if (HF.continuous) { wakeStart(); return; }
  if (HF.listening) return;
  try { speechSynthesis.cancel(); } catch { /* none */ }
  const rec = new Speech();
  rec.lang = navigator.language || 'en-US'; rec.interimResults = true; rec.maxAlternatives = 1;
  HF.listening = true; $('#micBtn')?.classList.add('on'); voiceLive('Listening...'); beep();
  let final = '';
  rec.onresult = (e) => { const r = e.results[e.results.length - 1]; voiceLive(`"${r[0].transcript}"`); if (r.isFinal) final = r[0].transcript; };
  rec.onerror = (e) => { if (e.error === 'not-allowed') toast('Microphone blocked. Allow it for this site.'); };
  rec.onend = () => {
    HF.listening = false; $('#micBtn')?.classList.remove('on');
    if (final) runVoice(final); else { voiceLive('', false); say('Did not catch that', false, true); }
    setTimeout(() => voiceLive('', false), 1400);
  };
  rec.start();
}

// Always-on: only acts on phrases that start with the wake word.
function startContinuous() {
  if (!Speech || HF.rec) return;
  HF.continuous = true;
  const rec = new Speech();
  rec.lang = navigator.language || 'en-US'; rec.continuous = true; rec.interimResults = false;
  rec.onresult = (e) => {
    if (HF.speaking) return;
    HF.fails = 0;
    const text = e.results[e.results.length - 1][0].transcript.trim();
    // "Armor" (or how recognisers often hear it) at the start wakes the assistant; the rest of the sentence is the command.
    const m = text.match(/^(?:hey |ok |okay |hi )?(?:armou?r|amor|armer|ammar|armour's|armor's|arm or)\b[\s,.:;-]*(.*)$/i);
    if (m) { if (m[1]) { wakeEnd(); runVoice(m[1]); } else wakeStart(); }
    else if (Date.now() < HF.wakeUntil) { wakeEnd(); runVoice(text); }
  };
  rec.onerror = (e) => {
    if (e.error === 'not-allowed' || e.error === 'service-not-allowed') { stopContinuous(); toast('Microphone blocked. Allow it to use "Armor".'); }
    else if (e.error !== 'no-speech' && e.error !== 'aborted') HF.fails = (HF.fails || 0) + 1;
  };
  // Browsers stop recognition every so often; keep it going while enabled, backing off if the speech service is failing.
  rec.onend = () => { if (HF.continuous) setTimeout(() => { try { rec.start(); } catch { /* already */ } }, Math.min(300 * 2 ** (HF.fails || 0), 10000)); };
  try { rec.start(); } catch { /* already */ }
  HF.rec = rec;
  renderHf();
}
function wakeStart() {
  HF.wakeUntil = Date.now() + 8000; HF.awake = true; $('#micBtn')?.classList.add('on'); voiceLive('Listening... say a command'); beep(); vibrate(40); renderHf();
  clearTimeout(HF.wakeTimer); HF.wakeTimer = setTimeout(wakeEnd, 8200);
}
function wakeEnd() { HF.wakeUntil = 0; HF.awake = false; clearTimeout(HF.wakeTimer); $('#micBtn')?.classList.remove('on'); renderHf(); }
function stopContinuous() { HF.continuous = false; try { HF.rec?.stop(); } catch { /* none */ } HF.rec = null; renderHf(); }

// Helmet intercom buttons via the media session.
function startMediaKeys() {
  if (!('mediaSession' in navigator) || HF.media) return;
  const el = $('#mediaKeepAlive');
  el.src = silentWav(); el.volume = 0.01;
  el.play().then(() => {
    HF.media = true;
    navigator.mediaSession.metadata = new MediaMetadata({ title: 'ARMOR ride', artist: 'Press to talk to ARMOR', album: S.ride?.name || 'ARMOR' });
    const h = (fn) => () => { fn(); el.play().catch(() => {}); };
    for (const [a, fn] of [['play', listenOnce], ['pause', listenOnce], ['nexttrack', statusReadout], ['previoustrack', () => say(HF.lastSaid || 'Nothing to repeat', false, true)]]) {
      try { navigator.mediaSession.setActionHandler(a, h(fn)); } catch { /* unsupported */ }
    }
    renderHf();
  }).catch(() => {});
}
function stopMediaKeys() { HF.media = false; try { $('#mediaKeepAlive').pause(); } catch { /* none */ } renderHf(); }
function silentWav() {
  const rate = 8000, n = rate; // one second
  const buf = new DataView(new ArrayBuffer(44 + n));
  const w = (o, str) => [...str].forEach((c, i) => buf.setUint8(o + i, c.charCodeAt(0)));
  w(0, 'RIFF'); buf.setUint32(4, 36 + n, true); w(8, 'WAVEfmt '); buf.setUint32(16, 16, true); buf.setUint16(20, 1, true); buf.setUint16(22, 1, true);
  buf.setUint32(24, rate, true); buf.setUint32(28, rate, true); buf.setUint16(32, 1, true); buf.setUint16(34, 8, true); w(36, 'data'); buf.setUint32(40, n, true);
  for (let i = 0; i < n; i++) buf.setUint8(44 + i, 128);
  return URL.createObjectURL(new Blob([buf], { type: 'audio/wav' }));
}

// Air gestures from the front camera's brightness.
async function startGestures() {
  if (HF.cam) return;
  try {
    const stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'user', width: 160, height: 120, frameRate: 15 }, audio: false });
    const v = $('#gestureCam'); v.srcObject = stream; await v.play();
    const c = document.createElement('canvas'); c.width = 32; c.height = 24;
    const ctx = c.getContext('2d', { willReadFrequently: true });
    let base = null, covered = false, coverAt = 0, waves = [], sosFired = false;
    const tick = () => {
      if (!HF.cam) return;
      ctx.drawImage(v, 0, 0, 32, 24);
      const d = ctx.getImageData(0, 0, 32, 24).data;
      let sum = 0; for (let i = 0; i < d.length; i += 4) sum += d[i] + d[i + 1] + d[i + 2];
      const lum = sum / (d.length / 4) / 3;
      base = base == null ? lum : covered ? base : base * 0.97 + lum * 0.03;
      const dark = lum < Math.max(18, base * 0.45);
      const t = Date.now();
      if (dark && !covered) { covered = true; coverAt = t; sosFired = false; }
      if (covered && dark && !sosFired && t - coverAt > 3000) { sosFired = true; gestureSos(); }
      if (!dark && covered) {
        covered = false;
        const held = t - coverAt;
        if (held > 80 && held < 900) {
          waves.push(t); waves = waves.filter((x) => t - x < 1500);
          clearTimeout(HF.waveTimer);
          HF.waveTimer = setTimeout(() => { const n = waves.length; waves = []; if (n >= 2) statusReadout(); else listenOnce(); }, 650);
        }
      }
      HF.camRaf = setTimeout(tick, 66);
    };
    HF.cam = stream; tick(); renderHf();
    toast('Air gestures on: wave to talk, wave twice for status');
  } catch { profile.air = false; saveProfile(); toast('Camera blocked, so air gestures are off'); }
}
function stopGestures() { clearTimeout(HF.camRaf); HF.cam?.getTracks().forEach((t) => t.stop()); HF.cam = null; renderHf(); }

function gestureSos() {
  if (S.mySos || S.crash) return;
  let left = 10;
  const box = $('#critical');
  box.className = 'critical'; box.dataset.id = 'crash'; S.crash = { at: Date.now() };
  box.innerHTML = `<div class="top-txt"><div class="eyebrow" style="color:#FF9A96">Air gesture</div><h1>Sending SOS</h1><p>You covered the phone for 3 seconds. Say "cancel" or tap below to stop it.</p></div>
    <div class="count" id="gsN">${left}</div><div class="btns"><button class="btn primary big" id="gsCancel">Cancel</button><button class="btn danger" id="gsNow">Send now</button></div>`;
  say('SOS in 10 seconds. Say cancel to stop.', false, true); vibrate([500, 200, 500]);
  const iv = setInterval(() => { left--; $('#gsN') && ($('#gsN').textContent = left); if (left <= 0) { clearInterval(iv); S.crash = null; triggerSos('SOS'); } }, 1000);
  HF.cancelCountdown = () => { clearInterval(iv); S.crash = null; closeCritical(); say('SOS cancelled', false, true); HF.cancelCountdown = null; };
  $('#gsCancel').onclick = HF.cancelCountdown;
  $('#gsNow').onclick = () => { clearInterval(iv); S.crash = null; HF.cancelCountdown = null; triggerSos('SOS'); };
}

// ---- group voice chat (WebRTC mesh; signalling over server-sent events) ----

async function vcIce() {
  if (!VC.ice) { try { VC.ice = (await api('/ice')).iceServers; } catch { VC.ice = [{ urls: 'stun:stun.l.google.com:19302' }]; } }
  return VC.ice;
}

function vcSend(to, data) {
  return fetch(`/api/rides/${session.code}/signal`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ riderId: session.riderId, token: session.token, to, data }) }).catch(() => {});
}

// Peers are created synchronously (ICE servers are loaded before joining) so two signals for the same rider can never
// race into two connections.
function vcPeer(id) {
  let p = VC.peers.get(id);
  if (p) return p;
  const pc = new RTCPeerConnection({ iceServers: VC.ice || [{ urls: 'stun:stun.l.google.com:19302' }] });
  const audio = new Audio();
  audio.autoplay = true; audio.playsInline = true;
  VC.local.getTracks().forEach((t) => pc.addTrack(t, VC.local));
  p = { pc, audio, pending: [], q: Promise.resolve() };
  pc.ontrack = (e) => { audio.srcObject = e.streams[0]; audio.play().catch(() => {}); renderHf(); };
  pc.onicecandidate = (e) => { if (e.candidate) vcSend(id, { k: 'ice', c: e.candidate }); };
  pc.onconnectionstatechange = () => { if (pc.connectionState === 'failed') vcDrop(id); renderHf(); renderBanners(); };
  VC.peers.set(id, p);
  return p;
}

function vcDrop(id) {
  const p = VC.peers.get(id);
  if (!p) return;
  VC.peers.delete(id);
  try { p.pc.close(); } catch { /* closed */ }
  p.audio.srcObject = null;
}

function vcOffer(id) {
  const p = vcPeer(id);
  p.q = p.q.then(async () => {
    const offer = await p.pc.createOffer();
    await p.pc.setLocalDescription(offer);
    vcSend(id, { k: 'offer', sdp: p.pc.localDescription });
  }).catch(() => {});
}

// Each rider's signals are handled one at a time, in the order they arrive.
function vcSignal({ from, data }) {
  if (!VC.on || !data) return;
  if (data.k === 'bye') return vcDrop(from);
  const p = vcPeer(from);
  p.q = p.q.then(async () => {
    if (data.k === 'offer') {
      await p.pc.setRemoteDescription(data.sdp);
      for (const c of p.pending.splice(0)) await p.pc.addIceCandidate(c).catch(() => {});
      const answer = await p.pc.createAnswer();
      await p.pc.setLocalDescription(answer);
      vcSend(from, { k: 'answer', sdp: p.pc.localDescription });
    } else if (data.k === 'answer') {
      await p.pc.setRemoteDescription(data.sdp);
      for (const c of p.pending.splice(0)) await p.pc.addIceCandidate(c).catch(() => {});
    } else if (data.k === 'ice') {
      if (p.pc.remoteDescription) await p.pc.addIceCandidate(data.c).catch(() => {});
      else p.pending.push(data.c);
    }
  }).catch(() => { /* a bad signal should not break the call */ });
}

async function vcJoin() {
  if (VC.on) return;
  if (!navigator.mediaDevices?.getUserMedia || !window.RTCPeerConnection) { say('Voice chat is not supported in this browser', false, true); return toast('Voice chat is not supported in this browser'); }
  try { VC.local = await navigator.mediaDevices.getUserMedia({ audio: { echoCancellation: true, noiseSuppression: true, autoGainControl: true } }); }
  catch { say('Microphone blocked', false, true); return toast('Microphone blocked. Allow it for this site.'); }
  await vcIce();
  VC.on = true; VC.muted = false; VC.seen.clear();
  VC.es = new EventSource(`/api/rides/${session.code}/events?riderId=${encodeURIComponent(session.riderId)}&token=${encodeURIComponent(session.token)}`);
  VC.es.onmessage = (e) => { try { vcSignal(JSON.parse(e.data)); } catch { /* ignore */ } };
  await new Promise((r) => { VC.es.onopen = r; setTimeout(r, 2500); });
  await act('voiceJoin').catch(() => {});
  // I am the newcomer, so I call everyone already in.
  for (const id of S.ride.voice) if (id !== session.riderId) vcOffer(id);
  say('Voice chat on. You are live.', false, true); toast('Voice chat on');
  renderHf(); renderBanners(); renderTiles();
}

function vcMute(on) {
  if (!VC.on) return say('You are not in voice chat');
  VC.muted = on;
  VC.local.getAudioTracks().forEach((t) => { t.enabled = !on; });
  say(on ? 'Muted' : 'Mic on', false, true); renderHf(); renderBanners(); renderTiles();
}

async function vcLeave() {
  if (!VC.on) return;
  for (const id of [...VC.peers.keys()]) { vcSend(id, { k: 'bye' }); vcDrop(id); }
  VC.on = false; VC.muted = false;
  VC.local?.getTracks().forEach((t) => t.stop()); VC.local = null;
  try { VC.es?.close(); } catch { /* closed */ } VC.es = null;
  await act('voiceLeave').catch(() => {});
  say('Left voice chat', false, true); renderHf(); renderBanners(); renderTiles();
}

// Keep the call in step with who the server says is in it.
function vcSync() {
  if (!S.ride) return;
  const ids = new Set(S.ride.voice);
  if (VC.on) {
    // Only drop riders we have seen in the call and who are now gone: a rider can offer to us a moment before our
    // next poll shows them in the list.
    for (const id of ids) VC.seen.add(id);
    for (const id of [...VC.peers.keys()]) if (VC.seen.has(id) && !ids.has(id)) { VC.seen.delete(id); vcDrop(id); }
    if (VC.seen.has(session.riderId) && !ids.has(session.riderId)) { vcLeave(); return; }
  }
  if (ids.size && !VC.on && !VC.announced) { VC.announced = true; say('Voice chat started. Say voice chat to join.', false, true); beep(); }
  if (!ids.size) VC.announced = false;
}

function vcPanel() {
  const n = S.ride.voice.length;
  openPanel('vc', `<h2>Voice chat</h2><p class="mid" style="margin:0;font-size:13px">${n} connected. Everyone in the call hears each other. Wear a headset so the phone speaker doesn't echo.</p>
    <button class="btn primary big" id="vcMute">${VC.muted ? 'Unmute' : 'Mute'}</button><button class="btn ghost" id="vcLeaveBtn">Leave voice chat</button><button class="btn ghost" data-close>Close</button>`, (p) => {
    $('#vcMute', p).onclick = () => { closePanel(); vcMute(!VC.muted); };
    $('#vcLeaveBtn', p).onclick = () => { closePanel(); vcLeave(); };
  });
}

$('#vcBtn').onclick = () => (VC.on ? vcPanel() : vcJoin());
$('#micBtn').onclick = () => listenOnce();
document.addEventListener('click', (e) => {
  const b = e.target.closest('[data-vc]');
  if (!b) return;
  if (b.dataset.vc === 'join') vcJoin();
  if (b.dataset.vc === 'leave') vcLeave();
  if (b.dataset.vc === 'mute') vcMute(!VC.muted);
});

function applyHandsFree() {
  if (S.screen !== 'ride') { stopContinuous(); stopGestures(); return; }
  profile.assistant !== false ? startContinuous() : stopContinuous();
  profile.air ? startGestures() : stopGestures();
  if (profile.mediaKeys !== false) startMediaKeys();
}

function renderHf() {
  // Quiet by default: a chip appears only while the assistant is listening or you are in a call.
  const chips = [];
  if (HF.awake) chips.push('<span class="glass listening"><i class="on-dot"></i>Listening</span>');
  if (VC.on) chips.push(`<span class="glass"><i class="on-dot"></i>Voice chat${VC.muted ? ' · muted' : ''}</span>`);
  $('#hf') && ($('#hf').innerHTML = chips.join(''));
}

$('#voiceBtn').onclick = () => voicePanel();

function voicePanel() {
  openPanel('voice', `<h2>Hands-free</h2><p class="mid" style="margin:0;font-size:13px">Pair your helmet intercom (Cardo, Sena, any Bluetooth headset) with this phone. ARMOR talks through it and listens on its mic.</p>
    <button class="btn primary big" id="vNow">${ic('mic')} Talk now</button>
    <label class="switch"><input type="checkbox" id="vHf" ${profile.assistant !== false ? 'checked' : ''}><span><b>Voice assistant</b><small>Say "Armor", then a command. Or in one go: "Armor, lets stop for a while"</small></span></label>
    <label class="switch"><input type="checkbox" id="vKeys" ${profile.mediaKeys !== false ? 'checked' : ''}><span><b>Helmet button</b><small>Play/pause: talk · Next: status · Previous: repeat</small></span></label>
    <label class="switch"><input type="checkbox" id="vAir" ${profile.air ? 'checked' : ''}><span><b>Air gestures</b><small>Wave over the phone: talk · Two waves: status · Cover 3 s: SOS countdown</small></span></label>
    <label class="switch"><input type="checkbox" id="vRead" ${profile.readAloud ? 'checked' : ''}><span><b>Read everything aloud</b><small>Chat, group events, hazards. Emergencies are always spoken.</small></span></label>
    <div class="card help-list"><div class="lbl" style="margin-bottom:4px">Things you can say</div>
      <div><b>"Armor, lets stop for a while nearby"</b> · asks the group to stop and tells them</div>
      <div><b>"Armor, voice chat"</b> · join the group call · <b>"leave voice chat"</b> · <b>"mute"</b></div>
      <div><b>"Stop"</b> / <b>"Stopping for fuel"</b> · just you</div>
      <div><b>"Riding"</b> / <b>"Let's go"</b> · start moving again</div>
      <div><b>"Team stop food"</b> · lead stops everyone; riders ask the lead</div>
      <div><b>"Stopped"</b> · confirm a team stop</div>
      <div><b>"Resume"</b> / <b>"Cancel stop"</b> · lead restarts the group</div>
      <div><b>"Status"</b> · speed, lead distance, messages</div>
      <div><b>"Read messages"</b> · latest chat</div>
      <div><b>"Say on my way"</b> · send a message</div>
      <div><b>"Pothole"</b>, <b>"Oil"</b>, <b>"Police"</b> · mark a hazard</div>
      <div><b>"Need a hand"</b> / <b>"Flat tyre"</b> · ask the group</div>
      <div><b>"Regroup here"</b> · lead sets regroup point</div>
      <div><b>"Wait for me"</b> / <b>"Catching up"</b> · group waits; clears when you're back</div>
      <div><b>"Caught up"</b> · tell them you're back</div>
      <div><b>"Responding"</b> · tell a rider in trouble you're coming</div>
      <div><b>"I'm OK"</b> / <b>"Cancel"</b> · cancel your alert or countdown</div>
      <div><b>"SOS"</b> · asks to confirm, then alerts everyone</div></div>
    <button class="btn ghost" data-close>Close</button>`, (p) => {
    $('#vNow', p).onclick = () => { closePanel(); listenOnce(); };
    const bind = (id, key, fn) => ($('#' + id, p).onchange = (e) => { profile[key] = e.target.checked; saveProfile(); fn && fn(); });
    bind('vHf', 'assistant', applyHandsFree);
    bind('vKeys', 'mediaKeys', () => (profile.mediaKeys ? startMediaKeys() : stopMediaKeys()));
    bind('vAir', 'air', applyHandsFree);
    bind('vRead', 'readAloud');
  });
}

// Same phrase rules as the Android app's VoiceCommands parser, plus ride words.
function parseVoice(raw) {
  const t = ' ' + raw.toLowerCase().replace(/[’']/g, '').replace(/[^a-z0-9 ]/g, ' ').replace(/\s+/g, ' ').trim() + ' ';
  const has = (...w) => w.some((x) => t.includes(' ' + x + ' '));
  const reason = has('fuel', 'petrol', 'gas', 'diesel') ? 'Fuel' : has('food', 'lunch', 'eat', 'dinner', 'breakfast', 'hungry') ? 'Food' : has('photo', 'picture', 'pic') ? 'Photo' : has('chai', 'tea', 'coffee', 'break', 'rest') ? 'Break' : has('bike issue', 'problem', 'issue') ? 'Bike issue' : '';
  const sayM = raw.match(/^\s*(say|tell (the )?(group|everyone)|message|send)\s+(.+)/i);
  if (sayM) return { k: 'say', text: sayM[4] };
  if (has('cancel', 'abort', 'never mind', 'nevermind')) return { k: 'cancel' };
  // group voice chat
  if (has('leave voice chat', 'end voice chat', 'stop voice chat', 'close voice chat', 'leave the call', 'hang up', 'disconnect voice')) return { k: 'vcleave' };
  if (has('voice chat', 'voice call', 'join voice', 'join the call', 'group call', 'open the mic', 'talk to everyone', 'talk to the group', 'start voice', 'open voice')) return { k: 'vc' };
  if (VC.on && has('unmute', 'mic on', 'unmute me')) return { k: 'unmute' };
  if (VC.on && has('mute', 'mute me', 'mic off', 'mute mic')) return { k: 'mute' };
  if (has('sos', 'emergency', 'mayday')) return { k: 'sos' };
  if (has('status', 'where am i', 'update')) return { k: 'status' };
  if (has('read messages', 'read message', 'read chat', 'messages', 'any messages')) return { k: 'read' };
  if (has('responding', 'on my way to', 'coming to help', 'im coming')) return { k: 'respond' };
  if (has('caught up', 'i caught up', 'with you now', 'im back with you')) return { k: 'caughtup' };
  if (has('wait for me', 'wait up', 'wait guys', 'catching up', 'im catching up', 'hold up', 'im behind', 'fell behind', 'slow down for me')) return { k: 'catchup' };
  if (has('team stop', 'stop everyone', 'everyone stop', 'all stop', 'group stop', 'stop the team', 'stop team')) return { k: 'team', reason: reason || 'Break' };
  // Everyday ways of asking for a stop. For the lead this stops the team; for riders it asks the lead. The sentence is relayed too.
  if (has('lets stop', 'let us stop', 'stop for a while', 'stop for some time', 'stop for a bit', 'stop for a minute', 'stop for a moment', 'stop nearby', 'stop somewhere', 'stop here', 'stop at the next', 'stop at next', 'take a break', 'take break', 'need a break', 'break nearby', 'want a break', 'pull over nearby', 'find a place to stop', 'chai break', 'tea break', 'coffee break', 'fuel stop', 'need fuel', 'need petrol', 'low on fuel', 'low on petrol', 'running low on fuel', 'need to eat', 'im hungry', 'hungry', 'lunch break', 'can we stop', 'should we stop', 'shall we stop', 'wanna stop', 'want to stop', 'need to stop')) return { k: 'team', reason: reason || 'Break', relay: true };
  if (has('resume', 'rolling', 'lets go', 'lets roll', 'moving again', 'riding', 'start riding', 'go go', 'back on')) return { k: 'resume' };
  if (has('stopped', 'i have stopped', 'ive stopped', 'pulled over')) return { k: 'stopped' };
  if (has('im ok', 'im okay', 'i am ok', 'i am okay', 'all good', 'im fine')) return { k: 'ok' };
  if (has('regroup here', 'regroup point', 'meet here')) return { k: 'regroup' };
  const hz = [['POTHOLE', 'pothole', 'potholes', 'hole'], ['GRAVEL', 'gravel', 'sand', 'stones'], ['OIL', 'oil', 'slick', 'diesel spill'], ['WATER', 'water', 'flooded', 'puddle'], ['ANIMAL', 'animal', 'cow', 'dog', 'cattle', 'goat'], ['POLICE', 'police', 'cops', 'checkpoint'], ['ACCIDENT', 'accident', 'crash ahead'], ['ROADWORK', 'roadwork', 'road work', 'construction', 'diversion']]
    .find(([, ...w]) => has(...w));
  if (hz && !has('stop')) return { k: 'hazard', hazard: hz[0] };
  if (has('need a hand', 'need help', 'help me', 'breakdown', 'broke down', 'flat tyre', 'flat tire', 'puncture', 'out of fuel', 'lost')) return { k: 'help', detail: has('flat tyre', 'flat tire', 'puncture') ? 'Flat tyre' : has('out of fuel') ? 'Out of fuel' : has('lost') ? 'Lost the group' : has('breakdown', 'broke down') ? 'Breakdown' : 'Other' };
  if (has('off bike', 'off the bike', 'walking')) return { k: 'offbike' };
  if (has('stop', 'stopping', 'pull over', 'pulling over', 'pause')) return { k: 'stop', reason };
  return { k: 'say', text: raw.trim() };
}

function runVoice(raw) {
  voiceLive(`"${raw}"`); setTimeout(() => voiceLive('', false), 1600);
  const i = parseVoice(raw), st = myState(), L1 = isLead();
  if (i.k === 'vc') return VC.on ? say('You are already in voice chat') : vcJoin();
  if (i.k === 'vcleave') return VC.on ? vcLeave() : say('You are not in voice chat');
  if (i.k === 'mute' || i.k === 'unmute') return vcMute(i.k === 'mute');
  if (i.k === 'cancel') {
    if (HF.cancelCountdown) return HF.cancelCountdown();
    if (S.crash && S.crashCancel) return S.crashCancel();
    if (st === 'SOS') return cancelMySos();
    const req = myAlert('STOP_REQUEST') || myAlert('CATCH_UP') || myAlert('HELP');
    if (req) return act('resolve', { alertId: req.id }).then(() => say('Cancelled'));
    if (L1 && S.ride.teamStop) return act('resume', { cancel: true }).then(() => say('Team stop cancelled'));
    return say('Nothing to cancel');
  }
  if (i.k === 'say') return sendQuick(i.text).then(() => say('Sent'));
  if (i.k === 'status') return statusReadout();
  if (i.k === 'read') {
    const last = [...S.messages.values()].filter((m) => !m.system && m.riderId !== session.riderId).sort((a, b) => a.at - b.at).slice(-3);
    markRead();
    return say(last.length ? last.map((m) => `${m.name}: ${m.text}`).join('. ') : 'No messages yet', false, true);
  }
  if (i.k === 'sos') { say('Say yes to send S O S', false, true); return confirmPanel('Send SOS?', `Heard: "${raw}"`, 'Send SOS', () => triggerSos('SOS')); }
  if (i.k === 'respond') {
    const a = openAlerts().find((x) => x.riderId !== session.riderId && ['SOS', 'CRASH', 'HELP'].includes(x.kind));
    if (!a) return say('No one needs help right now');
    markHandled(a.id); closeCritical();
    return act('ack', { alertId: a.id }).then(() => { say(`${a.name} knows you are coming. Opening directions.`); if (a.lat != null) window.open(directions(a.lat, a.lon), '_blank'); });
  }
  if (i.k === 'team') {
    const cap = raw.trim().replace(/^./, (c) => c.toUpperCase());
    let done;
    if (L1) done = act('teamStop', { reason: i.reason }).then(() => say(`Team stop called. ${i.reason}`));
    else if (myAlert('STOP_REQUEST')) done = Promise.resolve(say('You already asked the lead'));
    else done = act('alert', { kind: 'STOP_REQUEST', detail: i.reason }).then(() => say(`Asked ${lead()?.name || 'the lead'} to stop for ${i.reason}`));
    // Natural sentences ("lets stop for a while nearby") are passed on to the group as well as acted on.
    return done.then(() => (i.relay ? sendQuick(cap) : null));
  }
  if (i.k === 'stopped') {
    if (st === 'CONFIRM') return doState.confirm();
    return act('state', { state: 'STOPPED', reason: '' }).then(() => say('Stopped'));
  }
  if (i.k === 'resume') {
    if (st === 'TEAM' && L1) return doState.resume();
    if (st === 'TEAM') return say(`${S.ride.teamStop.by} still has the team stopped. Say ride on anyway to leave.`, false, true);
    if (st === 'STOPPED' || st === 'OFF_BIKE') return doState.ride();
    return say('You are already riding');
  }
  if (i.k === 'ok') {
    if (HF.cancelCountdown) return HF.cancelCountdown();
    if (S.crash && S.crashCancel) return S.crashCancel();
    if (st === 'SOS') return cancelMySos();
    return say('Good to hear');
  }
  if (i.k === 'catchup') return isLead() ? sendQuick('Ease off, let the group close up').then(() => say('Told the group to ease off')) : askWait();
  if (i.k === 'caughtup') return myAlert('CATCH_UP') ? doState.caughtup() : say('Good. You are with the group.');
  if (i.k === 'regroup') return L1 ? regroupHere() : say(`Only ${lead()?.name || 'the lead'} can set the regroup point`);
  if (i.k === 'hazard') return reportHazard(i.hazard);
  if (i.k === 'help') return myAlert('HELP') ? say('The group already knows') : requestHelp(i.detail);
  if (i.k === 'offbike') return act('state', { state: 'OFF_BIKE' }).then(() => say('Off the bike. Crash detection paused.'));
  if (i.k === 'stop') {
    if (st === 'CONFIRM') return doState.confirm();
    return act('state', { state: 'STOPPED', reason: i.reason }).then(() => say(i.reason ? `Stopped for ${i.reason}` : 'Stopped. Say riding when you go again.'));
  }
}

// ---- battery, wake lock, notifications -----------------------------------

async function initBattery() {
  try {
    const b = await navigator.getBattery?.();
    if (!b) return;
    const upd = () => {
      S.battery = Math.round(b.level * 100); S.charging = b.charging;
      if (S.battery <= LOW_BATTERY && !b.charging && !S.lowSent && S.ride?.status === 'riding') { S.lowSent = true; act('alert', { kind: 'LOW_BATTERY', detail: `${S.battery}%` }).catch(() => {}); }
      if (S.battery > LOW_BATTERY + 10 || b.charging) {
        const a = openAlerts().find((x) => x.riderId === session?.riderId && x.kind === 'LOW_BATTERY');
        if (a) act('resolve', { alertId: a.id }).catch(() => {});
        S.lowSent = false;
      }
    };
    upd(); b.addEventListener('levelchange', upd); b.addEventListener('chargingchange', upd);
  } catch { /* unsupported */ }
}

async function requestWake() {
  try { if ('wakeLock' in navigator && !S.wakeLock) { S.wakeLock = await navigator.wakeLock.request('screen'); S.wakeLock.addEventListener('release', () => (S.wakeLock = null)); } } catch { /* denied */ }
}
function releaseWake() { try { S.wakeLock?.release(); } catch { /* none */ } S.wakeLock = null; }

function askNotify() { try { if ('Notification' in window && Notification.permission === 'default') Notification.requestPermission(); } catch { /* unsupported */ } }

if ('serviceWorker' in navigator) navigator.serviceWorker.register('/sw.js').catch(() => {});

// ---- summary -------------------------------------------------------------

function renderSummary() {
  const r = S.ride, t = S.trip || loadTrip();
  $('#sumTitle').textContent = r.name;
  const dur = (r.endedAt || Date.now()) - (r.startedAt || r.createdAt);
  $('#sumDist').textContent = (t.dist / 1000).toFixed(1);
  $('#sumDur').textContent = fmtDur(dur);
  $('#sumMax').textContent = Math.round(t.max * 3.6);
  $('#sumAvg').textContent = t.moving > 60000 ? Math.round(t.dist / (t.moving / 1000) * 3.6) : 0;
  $('#sumEvents').innerHTML = [...S.messages.values()].filter((m) => m.system).sort((a, b) => a.at - b.at).slice(-40)
    .map((m) => `<div class="msg sys" style="align-self:flex-start;text-align:left">${clock(m.at)} · ${esc(m.text)}</div>`).join('');
}
$('#newRideBtn').onclick = () => { store.del('trip'); leaveLocal(); };

// ---- settings ------------------------------------------------------------

function settingsPanel() {
  const f = (k, label, ph, type = 'text') => `<label class="lbl" for="st_${k}">${label}</label><input class="in" id="st_${k}" type="${type}" value="${esc(profile[k])}" placeholder="${esc(ph)}">`;
  openPanel('settings', `<h2>Safety profile</h2><p class="mid" style="margin:0;font-size:13px">Stored only on this phone. Shown on your SOS screen and added to SOS texts.</p>
    ${f('name', 'Your name', 'Name the group sees')}
    ${f('contactName', 'Emergency contact name', 'e.g. Mum')}${f('contactPhone', 'Emergency contact phone', '+91 98xxxxxxx', 'tel')}
    ${f('emergency', 'Local emergency number', '112', 'tel')}
    ${f('blood', 'Blood group', 'e.g. O+')}${f('allergies', 'Allergies', 'e.g. Penicillin')}${f('conditions', 'Conditions', 'e.g. Asthma')}${f('meds', 'Medications', 'e.g. Inhaler')}
    <label class="switch"><input type="checkbox" id="st_crash" ${profile.crash ? 'checked' : ''}><span>Crash detection<small>Hard impact then stopped starts a 30 s countdown, then SOS</small></span></label>
    <button class="btn primary big" id="stSave">Save</button><button class="btn ghost" data-close>Cancel</button>`, (p) => {
    $('#stSave', p).onclick = () => {
      for (const k of ['name', 'contactName', 'contactPhone', 'emergency', 'blood', 'allergies', 'conditions', 'meds']) profile[k] = $('#st_' + k, p).value.trim();
      profile.crash = $('#st_crash', p).checked;
      saveProfile(); $('#nameIn').value = profile.name; closePanel(); toast('Saved');
    };
  });
}
$('#openSettings1').onclick = settingsPanel;
$('#openSettings2').onclick = settingsPanel;

$$('[data-ic]').forEach((el) => { el.innerHTML = ic(el.dataset.ic); });

// ---- boot ----------------------------------------------------------------

(async function boot() {
  if (session && (!linkCode || linkCode.toUpperCase() === session.code)) {
    try {
      const res = await api(`/rides/${session.code}/join`, { riderName: profile.name, riderId: session.riderId, token: session.token });
      enterRide(res);
      return;
    } catch { store.del('session'); session = null; }
  }
  show('welcome');
})();

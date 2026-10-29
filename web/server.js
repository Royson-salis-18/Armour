// ARMOR web: a dependency-free Node server for group rides.
// Serves the web app from ./public and keeps rides in memory, keyed by a
// six-character ride code. Phones poll GET /api/rides/:code every ~2 s.
// Data is lost when the process restarts; that is fine for testing rides.

'use strict';

const http = require('http');
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const store = require('./store').create(process.env.DATABASE_URL);

const PORT = process.env.PORT ? Number(process.env.PORT) : 8080;
const PUBLIC = path.join(__dirname, 'public');
const RIDE_TTL_MS = 24 * 60 * 60 * 1000;
const MAX_BODY = 16 * 1024;
const MAX_MESSAGES = 300;
const MAX_ALERTS = 200;
const MAX_RIDERS = 30;

const ALERT_KINDS = new Set(['SOS', 'CRASH', 'HELP', 'HAZARD', 'LOW_BATTERY', 'STATIONARY', 'STOP_REQUEST', 'CATCH_UP']);
const HAZARDS = new Set(['POTHOLE', 'GRAVEL', 'OIL', 'WATER', 'ANIMAL', 'POLICE', 'ACCIDENT', 'ROADWORK', 'OTHER']);

/** @type {Map<string, any>} */
const rides = new Map();

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.png': 'image/png',
  '.svg': 'image/svg+xml',
  '.json': 'application/json',
  '.webmanifest': 'application/manifest+json',
  '.ico': 'image/x-icon',
};

// No 0/O/1/I so codes read cleanly over a helmet intercom.
const CODE_CHARS = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';

function newCode() {
  for (;;) {
    let code = '';
    const bytes = crypto.randomBytes(6);
    for (const b of bytes) code += CODE_CHARS[b % CODE_CHARS.length];
    if (!rides.has(code)) return code;
  }
}

const newId = () => crypto.randomBytes(8).toString('hex');
const now = () => Date.now();

function clean(value, max) {
  return String(value == null ? '' : value).replace(/[\u0000-\u001f]/g, ' ').trim().slice(0, max);
}

function num(value, min, max) {
  const n = Number(value);
  return Number.isFinite(n) && n >= min && n <= max ? n : null;
}

function addRider(ride, name, phone) {
  const rider = {
    id: newId(),
    token: newId() + newId(),
    name: clean(name, 24) || 'Rider',
    // Shared with the ride so the others can text and call this rider in an SOS.
    phone: clean(phone, 20).replace(/[^0-9+]/g, ''),
    joinedAt: now(),
    seenAt: now(),
    lat: null, lon: null, acc: null, speed: null, heading: null, fixAt: null,
    battery: null,
    state: 'OK', // OK | STOPPED | OFF_BIKE | LEFT
    stopReason: '',
    stateAt: now(),
    stopConfirmed: false,
  };
  ride.riders.set(rider.id, rider);
  return rider;
}

function setState(rider, state, reason) {
  if (rider.state !== state) rider.stateAt = now();
  rider.state = state;
  rider.stopReason = state === 'OK' ? '' : reason || '';
}

function publicRider(r) {
  const { token, ...rest } = r;
  return rest;
}

function touch(ride) {
  ride.version += 1;
  ride.updatedAt = now();
}

function systemMessage(ride, text) {
  ride.messages.push({ id: newId(), riderId: null, name: 'ARMOR', text, at: now(), system: true });
  if (ride.messages.length > MAX_MESSAGES) ride.messages.splice(0, ride.messages.length - MAX_MESSAGES);
}

function snapshot(ride, since) {
  return {
    code: ride.code,
    name: ride.name,
    status: ride.status,
    leadId: ride.leadId,
    createdAt: ride.createdAt,
    startedAt: ride.startedAt,
    endedAt: ride.endedAt,
    version: ride.version,
    serverTime: now(),
    riders: [...ride.riders.values()].filter((r) => r.state !== 'LEFT').map(publicRider),
    alerts: ride.alerts.filter((a) => !a.resolvedAt || now() - a.resolvedAt < 10 * 60 * 1000),
    hazards: ride.hazards.filter((h) => now() - h.at < 3 * 60 * 60 * 1000),
    messages: since ? ride.messages.filter((m) => m.at > since) : ride.messages.slice(-80),
    teamStop: ride.teamStop,
    voice: [...ride.voice],
    regroup: ride.regroup,
    destination: ride.destination,
  };
}

function send(res, status, body) {
  const data = JSON.stringify(body);
  res.writeHead(status, {
    'Content-Type': 'application/json; charset=utf-8',
    'Cache-Control': 'no-store',
  });
  res.end(data);
}

function readBody(req) {
  return new Promise((resolve, reject) => {
    let size = 0;
    const chunks = [];
    req.on('data', (c) => {
      size += c.length;
      if (size > MAX_BODY) {
        reject(Object.assign(new Error('Body too large'), { status: 413 }));
        req.destroy();
        return;
      }
      chunks.push(c);
    });
    req.on('end', () => {
      if (!chunks.length) return resolve({});
      try {
        resolve(JSON.parse(Buffer.concat(chunks).toString('utf8')));
      } catch {
        reject(Object.assign(new Error('Bad JSON'), { status: 400 }));
      }
    });
    req.on('error', reject);
  });
}

function fail(status, message) {
  return Object.assign(new Error(message), { status });
}

function findRide(code) {
  const ride = rides.get(String(code || '').toUpperCase());
  if (!ride) throw fail(404, 'No ride with that code');
  return ride;
}

function auth(ride, body) {
  const rider = ride.riders.get(String(body.riderId || ''));
  if (!rider || rider.token !== body.token) throw fail(403, 'Not a member of this ride');
  rider.seenAt = now();
  return rider;
}

function requireLead(ride, rider) {
  if (ride.leadId !== rider.id) throw fail(403, 'Only the lead can do that');
}

// ---- actions --------------------------------------------------------------

const actions = {
  location(ride, rider, b) {
    const lat = num(b.lat, -90, 90);
    const lon = num(b.lon, -180, 180);
    if (lat == null || lon == null) throw fail(400, 'Bad position');
    rider.lat = lat;
    rider.lon = lon;
    rider.acc = num(b.acc, 0, 100000);
    rider.speed = num(b.speed, 0, 150);
    rider.heading = num(b.heading, 0, 360);
    rider.fixAt = now();
    const battery = num(b.battery, 0, 100);
    if (battery != null) rider.battery = battery;
    // Positions don't bump the version on their own; clients poll anyway.
    ride.updatedAt = now();
  },

  message(ride, rider, b) {
    const text = clean(b.text, 280);
    if (!text) throw fail(400, 'Empty message');
    ride.messages.push({ id: newId(), riderId: rider.id, name: rider.name, text, at: now(), quick: !!b.quick });
    if (ride.messages.length > MAX_MESSAGES) ride.messages.splice(0, ride.messages.length - MAX_MESSAGES);
    touch(ride);
  },

  alert(ride, rider, b) {
    const kind = String(b.kind || '').toUpperCase();
    if (!ALERT_KINDS.has(kind)) throw fail(400, 'Unknown alert');
    if (kind === 'HAZARD') {
      const type = HAZARDS.has(String(b.hazard).toUpperCase()) ? String(b.hazard).toUpperCase() : 'OTHER';
      const lat = num(b.lat, -90, 90) ?? rider.lat;
      const lon = num(b.lon, -180, 180) ?? rider.lon;
      if (lat == null || lon == null) throw fail(400, 'No position for hazard');
      ride.hazards.push({ id: newId(), type, lat, lon, by: rider.name, riderId: rider.id, at: now() });
      systemMessage(ride, `${rider.name} reported ${type.toLowerCase()} on the road`);
      touch(ride);
      return;
    }
    // One open alert of a kind per rider: repeat presses refresh it.
    let alert = ride.alerts.find((a) => a.riderId === rider.id && a.kind === kind && !a.resolvedAt);
    if (!alert) {
      alert = { id: newId(), kind, riderId: rider.id, name: rider.name, at: now(), acks: [], resolvedAt: null };
      ride.alerts.push(alert);
      if (ride.alerts.length > MAX_ALERTS) ride.alerts.splice(0, ride.alerts.length - MAX_ALERTS);
    }
    alert.lat = num(b.lat, -90, 90) ?? rider.lat;
    alert.lon = num(b.lon, -180, 180) ?? rider.lon;
    alert.detail = clean(b.detail, 160);
    alert.updatedAt = now();
    const label = { SOS: 'sent an SOS', CRASH: 'may have crashed', HELP: 'needs a hand', LOW_BATTERY: 'is on low battery', STATIONARY: 'has not moved for a while', STOP_REQUEST: 'asks the lead to stop', CATCH_UP: 'is catching up. Wait up' }[kind];
    systemMessage(ride, `${rider.name} ${label}${alert.detail && !['SOS', 'CRASH', 'CATCH_UP'].includes(kind) ? `: ${alert.detail}` : ''}`);
    touch(ride);
  },

  ack(ride, rider, b) {
    const alert = ride.alerts.find((a) => a.id === b.alertId);
    if (!alert) throw fail(404, 'No such alert');
    if (!alert.acks.some((a) => a.riderId === rider.id)) {
      alert.acks.push({ riderId: rider.id, name: rider.name, at: now() });
      if (alert.riderId !== rider.id) systemMessage(ride, `${rider.name} is responding to ${alert.name}`);
    }
    touch(ride);
  },

  resolve(ride, rider, b) {
    const alert = ride.alerts.find((a) => a.id === b.alertId);
    if (!alert) throw fail(404, 'No such alert');
    if (alert.riderId !== rider.id && ride.leadId !== rider.id) throw fail(403, 'Only the rider or the lead can clear this');
    if (!alert.resolvedAt) {
      alert.resolvedAt = now();
      alert.resolvedBy = rider.name;
      const own = alert.riderId === rider.id;
      if (alert.kind === 'STOP_REQUEST') systemMessage(ride, own ? `${rider.name} cancelled their stop request` : `${rider.name} declined ${alert.name}'s stop request`);
      else if (alert.kind === 'CATCH_UP') systemMessage(ride, own ? `${rider.name} ${b.caughtUp ? 'caught up with the group' : 'no longer needs the group to wait'}` : `${rider.name} cleared ${alert.name}'s wait request`);
      else if (alert.kind === 'HELP') systemMessage(ride, own ? `${rider.name} no longer needs a hand` : `${rider.name} closed ${alert.name}'s help request`);
      else if (alert.kind !== 'LOW_BATTERY') systemMessage(ride, own ? `${rider.name} is OK` : `${rider.name} cleared ${alert.name}'s alert`);
    }
    touch(ride);
  },

  start(ride, rider) {
    requireLead(ride, rider);
    if (ride.status === 'lobby') {
      ride.status = 'riding';
      ride.startedAt = now();
      systemMessage(ride, `${rider.name} started the ride`);
      touch(ride);
    }
  },

  end(ride, rider) {
    requireLead(ride, rider);
    ride.status = 'ended';
    ride.endedAt = now();
    ride.teamStop = null;
    ride.voice.clear();
    for (const a of ride.alerts) if (!a.resolvedAt && a.kind !== 'SOS' && a.kind !== 'CRASH') a.resolvedAt = now();
    systemMessage(ride, `${rider.name} ended the ride`);
    touch(ride);
  },

  voiceJoin(ride, rider) {
    if (!ride.voice.has(rider.id)) {
      ride.voice.add(rider.id);
      systemMessage(ride, `${rider.name} joined voice chat`);
      touch(ride);
    }
  },

  voiceLeave(ride, rider) {
    if (ride.voice.delete(rider.id)) {
      systemMessage(ride, `${rider.name} left voice chat`);
      touch(ride);
    }
  },

  teamStop(ride, rider, b) {
    requireLead(ride, rider);
    const reason = clean(b.reason, 40) || 'Stop';
    // "Stop & wait" for a rider who is catching up: that rider keeps riding.
    const waitFor = ride.riders.get(String(b.waitForId || ''));
    ride.teamStop = { id: newId(), reason, by: rider.name, byId: rider.id, at: now(), confirmed: [], waitForId: waitFor ? waitFor.id : null };
    if (waitFor) ride.teamStop.confirmed.push(waitFor.id);
    // The lead and anyone already parked count as stopped straight away.
    for (const r of ride.riders.values()) {
      r.stopConfirmed = r.id === rider.id || r.state === 'STOPPED' || r.state === 'OFF_BIKE' || r.id === ride.teamStop.waitForId;
      if (r.stopConfirmed && !ride.teamStop.confirmed.includes(r.id)) ride.teamStop.confirmed.push(r.id);
    }
    setState(rider, 'STOPPED', reason);
    rider.stoppedForTeam = true;
    // Accepting a rider's stop request closes it.
    for (const a of ride.alerts) if (a.kind === 'STOP_REQUEST' && !a.resolvedAt) { a.resolvedAt = now(); a.resolvedBy = rider.name; a.accepted = true; }
    systemMessage(ride, `${rider.name} called a team stop: ${reason}`);
    touch(ride);
  },

  confirmStop(ride, rider, b) {
    const stop = ride.teamStop;
    if (!stop || stop.id !== b.stopId) throw fail(409, 'That stop is over');
    if (!stop.confirmed.includes(rider.id)) stop.confirmed.push(rider.id);
    rider.stopConfirmed = true;
    if (rider.state === 'OK') {
      setState(rider, 'STOPPED', stop.reason);
      rider.stoppedForTeam = true;
    }
    touch(ride);
  },

  resume(ride, rider, b) {
    requireLead(ride, rider);
    if (ride.teamStop) {
      ride.teamStop = null;
      // Everyone who stopped for the team rolls again; personal stops stay.
      for (const r of ride.riders.values()) {
        if (r.stoppedForTeam && r.state === 'STOPPED') setState(r, 'OK', '');
        r.stopConfirmed = false;
        r.stoppedForTeam = false;
      }
      systemMessage(ride, b.cancel ? `${rider.name} cancelled the team stop` : `${rider.name}: rolling again`);
      touch(ride);
    }
  },

  state(ride, rider, b) {
    const state = String(b.state || '').toUpperCase();
    if (!['OK', 'STOPPED', 'OFF_BIKE'].includes(state)) throw fail(400, 'Bad state');
    const reason = clean(b.reason, 40);
    if (rider.state === state && rider.stopReason === reason) return;
    const was = rider.state;
    setState(rider, state, reason);
    if (state === 'STOPPED') systemMessage(ride, `${rider.name} ${was === 'STOPPED' ? 'is stopped' : 'stopped'}${reason ? `: ${reason}` : ''}`);
    if (state === 'OFF_BIKE') systemMessage(ride, `${rider.name} is off the bike`);
    if (state === 'OK') {
      const leftStop = ride.teamStop && rider.stopConfirmed && rider.id !== ride.leadId;
      if (leftStop) {
        rider.stopConfirmed = false;
        rider.stoppedForTeam = false;
        ride.teamStop.confirmed = ride.teamStop.confirmed.filter((id) => id !== rider.id);
      }
      systemMessage(ride, leftStop ? `${rider.name} left the team stop and is riding` : `${rider.name} is riding again`);
    }
    touch(ride);
  },

  regroup(ride, rider, b) {
    requireLead(ride, rider);
    if (b.clear) {
      ride.regroup = null;
    } else {
      const lat = num(b.lat, -90, 90);
      const lon = num(b.lon, -180, 180);
      if (lat == null || lon == null) throw fail(400, 'Bad position');
      ride.regroup = { lat, lon, label: clean(b.label, 40) || 'Regroup point', by: rider.name, at: now() };
      systemMessage(ride, `${rider.name} set a regroup point: ${ride.regroup.label}`);
    }
    touch(ride);
  },

  destination(ride, rider, b) {
    requireLead(ride, rider);
    if (b.clear) {
      ride.destination = null;
    } else {
      const lat = num(b.lat, -90, 90);
      const lon = num(b.lon, -180, 180);
      if (lat == null || lon == null) throw fail(400, 'Bad position');
      ride.destination = { lat, lon, label: clean(b.label, 60) || 'Destination', at: now() };
      systemMessage(ride, `Destination set: ${ride.destination.label}`);
    }
    touch(ride);
  },

  lead(ride, rider, b) {
    requireLead(ride, rider);
    const next = ride.riders.get(String(b.toId || ''));
    if (!next || next.state === 'LEFT') throw fail(404, 'No such rider');
    ride.leadId = next.id;
    systemMessage(ride, `${next.name} is now leading`);
    touch(ride);
  },

  leave(ride, rider) {
    rider.state = 'LEFT';
    ride.voice.delete(rider.id);
    if (ride.teamStop) ride.teamStop.confirmed = ride.teamStop.confirmed.filter((id) => id !== rider.id);
    for (const a of ride.alerts) if (a.riderId === rider.id && !a.resolvedAt && a.kind !== 'SOS' && a.kind !== 'CRASH') a.resolvedAt = now();
    systemMessage(ride, `${rider.name} left the ride`);
    if (ride.leadId === rider.id) {
      const next = [...ride.riders.values()].find((r) => r.state !== 'LEFT');
      if (next) {
        ride.leadId = next.id;
        systemMessage(ride, `${next.name} is now leading`);
      }
    }
    touch(ride);
  },
};

// ---- routing --------------------------------------------------------------

// STUN is enough for most networks. Set TURN_URLS (comma separated), TURN_USERNAME and TURN_CREDENTIAL to add a relay
// for phones behind strict carrier NAT.
function iceServers() {
  const list = [{ urls: ['stun:stun.l.google.com:19302', 'stun:stun1.l.google.com:19302'] }];
  const urls = (process.env.TURN_URLS || '').split(',').map((u) => u.trim()).filter(Boolean);
  if (urls.length) list.push({ urls, username: process.env.TURN_USERNAME || '', credential: process.env.TURN_CREDENTIAL || '' });
  return list;
}

// Server-sent events carry voice-chat signalling (WebRTC offers, answers, ICE) to one rider at a time.
function openEvents(req, res, ride, rider) {
  res.writeHead(200, {
    'Content-Type': 'text/event-stream; charset=utf-8',
    'Cache-Control': 'no-store, no-transform',
    Connection: 'keep-alive',
    'X-Accel-Buffering': 'no',
  });
  res.write(': connected\n\n');
  const old = ride.sse.get(rider.id);
  if (old) old.end();
  ride.sse.set(rider.id, res);
  const ping = setInterval(() => res.write(': ping\n\n'), 20000);
  req.on('close', () => {
    clearInterval(ping);
    if (ride.sse.get(rider.id) === res) ride.sse.delete(rider.id);
  });
}

async function api(req, res, url) {
  const parts = url.pathname.split('/').filter(Boolean); // ['api','rides',code?,action?]
  if (parts[1] === 'ice' && parts.length === 2) return send(res, 200, { iceServers: iceServers() });
  if (parts[1] !== 'rides') throw fail(404, 'Not found');

  if (parts.length === 2 && req.method === 'POST') {
    const b = await readBody(req);
    const code = newCode();
    const ride = {
      code,
      name: clean(b.name, 40) || 'Group ride',
      status: 'lobby',
      createdAt: now(), updatedAt: now(), startedAt: null, endedAt: null,
      version: 1,
      riders: new Map(), alerts: [], hazards: [], messages: [], voice: new Set(), sse: new Map(),
      teamStop: null, regroup: null, destination: null,
      leadId: null,
    };
    const rider = addRider(ride, b.riderName, b.phone);
    ride.leadId = rider.id;
    systemMessage(ride, `${rider.name} created the ride. Share code ${code} with the group.`);
    rides.set(code, ride);
    return send(res, 201, { code, riderId: rider.id, token: rider.token, ride: snapshot(ride) });
  }

  const ride = findRide(parts[2]);

  if (parts.length === 3 && req.method === 'GET') {
    const since = Number(url.searchParams.get('since')) || 0;
    return send(res, 200, snapshot(ride, since));
  }

  if (parts.length === 4 && parts[3] === 'events' && req.method === 'GET') {
    const rider = auth(ride, { riderId: url.searchParams.get('riderId'), token: url.searchParams.get('token') });
    return openEvents(req, res, ride, rider);
  }

  if (parts.length === 4 && parts[3] === 'signal' && req.method === 'POST') {
    const b = await readBody(req);
    const rider = auth(ride, b);
    if (!ride.voice.has(rider.id) || !ride.voice.has(String(b.to))) throw fail(409, 'Both riders must be in voice chat');
    const target = ride.sse.get(String(b.to));
    if (!target) throw fail(409, 'That rider is not connected yet');
    target.write(`data: ${JSON.stringify({ from: rider.id, data: b.data })}\n\n`);
    return send(res, 200, { ok: true });
  }

  if (parts.length === 4 && parts[3] === 'join' && req.method === 'POST') {
    const b = await readBody(req);
    // Rejoining with saved credentials keeps the same rider.
    const existing = ride.riders.get(String(b.riderId || ''));
    if (existing && existing.token === b.token) {
      if (existing.state === 'LEFT') {
        existing.state = 'OK';
        systemMessage(ride, `${existing.name} rejoined`);
        touch(ride);
      }
      return send(res, 200, { code: ride.code, riderId: existing.id, token: existing.token, ride: snapshot(ride) });
    }
    if (ride.status === 'ended') throw fail(410, 'That ride has ended');
    const active = [...ride.riders.values()].filter((r) => r.state !== 'LEFT').length;
    if (active >= MAX_RIDERS) throw fail(409, 'Ride is full');
    const rider = addRider(ride, b.riderName, b.phone);
    systemMessage(ride, `${rider.name} joined`);
    touch(ride);
    return send(res, 201, { code: ride.code, riderId: rider.id, token: rider.token, ride: snapshot(ride) });
  }

  if (parts.length === 4 && req.method === 'POST' && Object.hasOwn(actions, parts[3])) {
    const b = await readBody(req);
    const rider = auth(ride, b);
    actions[parts[3]](ride, rider, b);
    return send(res, 200, snapshot(ride, Number(b.since) || 0));
  }

  throw fail(404, 'Not found');
}

function serveStatic(req, res, url) {
  let rel = decodeURIComponent(url.pathname);
  // Share links look like /r/ABC123; the app reads the code from the path.
  if (rel === '/' || rel.startsWith('/r/')) rel = '/index.html';
  const file = path.normalize(path.join(PUBLIC, rel));
  if (!file.startsWith(PUBLIC + path.sep)) {
    res.writeHead(403);
    return res.end();
  }
  fs.readFile(file, (err, data) => {
    if (err) {
      res.writeHead(404, { 'Content-Type': 'text/plain' });
      return res.end('Not found');
    }
    const ext = path.extname(file);
    res.writeHead(200, {
      'Content-Type': MIME[ext] || 'application/octet-stream',
      // App files revalidate every time so a deploy shows up straight away; only vendored libraries are cached.
      'Cache-Control': file.includes(`${path.sep}vendor${path.sep}`) ? 'public, max-age=86400' : 'no-cache',
    });
    res.end(data);
  });
}

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, 'http://localhost');
  res.setHeader('X-Content-Type-Options', 'nosniff');
  // OpenStreetMap's tile servers want a Referer, so send the origin only.
  res.setHeader('Referrer-Policy', 'strict-origin-when-cross-origin');
  res.setHeader('Permissions-Policy', 'geolocation=(self), microphone=(self), camera=(self), accelerometer=(self)');
  try {
    if (url.pathname === '/healthz') return send(res, 200, { ok: true, rides: rides.size, database: store.enabled });
    if (url.pathname.startsWith('/api/')) return await api(req, res, url);
    if (req.method !== 'GET' && req.method !== 'HEAD') throw fail(405, 'Method not allowed');
    serveStatic(req, res, url);
  } catch (err) {
    const status = err.status || 500;
    if (status === 500) console.error(err);
    send(res, status, { error: status === 500 ? 'Server error' : err.message });
  }
});

// Drop rides nobody has touched for a day.
setInterval(() => {
  const cutoff = now() - RIDE_TTL_MS;
  for (const [code, ride] of rides) if (ride.updatedAt < cutoff) { rides.delete(code); store.remove(code); }
}, 10 * 60 * 1000).unref();

// Bring back the rides saved before the last restart, then start serving.
store.load(RIDE_TTL_MS)
  .then((saved) => {
    for (const ride of saved) rides.set(ride.code, ride);
    if (store.enabled) console.log(`database: restored ${saved.length} ride(s)`);
  })
  .catch((e) => console.error('database load failed, rides will not survive a restart:', e.message))
  .finally(() => {
    store.start(rides);
    server.listen(PORT, () => console.log(`ARMOR web listening on :${PORT}`));
  });

// Save everything before Render stops the process for a redeploy.
process.on('SIGTERM', () => store.flush().finally(() => process.exit(0)));

module.exports = server;

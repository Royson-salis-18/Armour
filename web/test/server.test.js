'use strict';
// End-to-end checks of the ride state machine through the HTTP API.
const { test, before, after } = require('node:test');
const assert = require('node:assert/strict');

process.env.PORT = '0';
const server = require('../server');
let base;

before(() => new Promise((r) => server.on('listening', () => { base = `http://127.0.0.1:${server.address().port}`; r(); })));
after(() => server.close());

async function call(path, body) {
  const res = await fetch(base + '/api' + path, body === undefined ? {} : { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
  const data = await res.json();
  return { status: res.status, data };
}

async function ride() {
  const lead = (await call('/rides', { name: 'Coast', riderName: 'Asha' })).data;
  const code = lead.code;
  const bob = (await call(`/rides/${code}/join`, { riderName: 'Bob' })).data;
  const cy = (await call(`/rides/${code}/join`, { riderName: 'Cy' })).data;
  const as = (who) => (action, extra = {}) => call(`/rides/${code}/${action}`, { riderId: who.riderId, token: who.token, ...extra });
  return { code, lead: as(lead), bob: as(bob), cy: as(cy), ids: { lead: lead.riderId, bob: bob.riderId, cy: cy.riderId }, creds: { lead, bob, cy } };
}
const rider = (snap, id) => snap.riders.find((r) => r.id === id);

test('only the lead starts, and joining needs a real code', async () => {
  const r = await ride();
  assert.equal((await r.bob('start')).status, 403);
  assert.equal((await r.lead('start')).data.status, 'riding');
  assert.equal((await call('/rides/ZZZZZZ/join', { riderName: 'x' })).status, 404);
});

test('personal stop and resume toggle freely', async () => {
  const r = await ride();
  await r.lead('start');
  let s = (await r.bob('state', { state: 'STOPPED', reason: 'Fuel' })).data;
  assert.equal(rider(s, r.ids.bob).state, 'STOPPED');
  assert.equal(rider(s, r.ids.bob).stopReason, 'Fuel');
  s = (await r.bob('state', { state: 'STOPPED', reason: 'Photo' })).data;
  assert.equal(rider(s, r.ids.bob).stopReason, 'Photo');
  s = (await r.bob('state', { state: 'OK' })).data;
  assert.equal(rider(s, r.ids.bob).state, 'OK');
  assert.equal(rider(s, r.ids.bob).stopReason, '');
});

test('team stop: confirm, resume restores riders but keeps personal stops', async () => {
  const r = await ride();
  await r.lead('start');
  await r.cy('state', { state: 'STOPPED', reason: 'Photo' }); // already parked
  let s = (await r.lead('teamStop', { reason: 'Fuel' })).data;
  assert.ok(s.teamStop.confirmed.includes(r.ids.lead));
  assert.ok(s.teamStop.confirmed.includes(r.ids.cy), 'already-stopped rider auto-confirms');
  assert.ok(!s.teamStop.confirmed.includes(r.ids.bob));
  s = (await r.bob('confirmStop', { stopId: s.teamStop.id })).data;
  assert.equal(rider(s, r.ids.bob).state, 'STOPPED');
  s = (await r.lead('resume')).data;
  assert.equal(s.teamStop, null);
  assert.equal(rider(s, r.ids.bob).state, 'OK');
  assert.equal(rider(s, r.ids.lead).state, 'OK');
  assert.equal(rider(s, r.ids.cy).state, 'STOPPED', 'personal stop survives the team resume');
});

test('rider can leave a team stop; lead can cancel it', async () => {
  const r = await ride();
  await r.lead('start');
  let s = (await r.lead('teamStop', { reason: 'Break' })).data;
  await r.bob('confirmStop', { stopId: s.teamStop.id });
  s = (await r.bob('state', { state: 'OK' })).data;
  assert.ok(!s.teamStop.confirmed.includes(r.ids.bob));
  assert.ok(s.messages.some((m) => /left the team stop/.test(m.text)));
  assert.equal((await r.bob('resume')).status, 403);
  s = (await r.lead('resume', { cancel: true })).data;
  assert.equal(s.teamStop, null);
  assert.ok(s.messages.some((m) => /cancelled the team stop/.test(m.text)));
});

test('stop request: rider asks, cancels, asks again, lead accepts', async () => {
  const r = await ride();
  await r.lead('start');
  let s = (await r.bob('alert', { kind: 'STOP_REQUEST', detail: 'Food' })).data;
  const req = s.alerts.find((a) => a.kind === 'STOP_REQUEST' && !a.resolvedAt);
  s = (await r.bob('resolve', { alertId: req.id })).data;
  assert.ok(s.messages.some((m) => /cancelled their stop request/.test(m.text)));
  s = (await r.bob('alert', { kind: 'STOP_REQUEST', detail: 'Food' })).data;
  s = (await r.lead('teamStop', { reason: 'Food' })).data;
  assert.ok(s.alerts.filter((a) => a.kind === 'STOP_REQUEST').every((a) => a.resolvedAt));
  assert.equal(s.teamStop.reason, 'Food');
});

test('help, SOS and lead hand-over; leaving lead passes it on', async () => {
  const r = await ride();
  await r.lead('start');
  let s = (await r.bob('alert', { kind: 'SOS', lat: 12.9, lon: 77.6 })).data;
  const sos = s.alerts.find((a) => a.kind === 'SOS');
  s = (await r.cy('ack', { alertId: sos.id })).data;
  assert.equal(s.alerts.find((a) => a.id === sos.id).acks[0].name, 'Cy');
  assert.equal((await r.cy('resolve', { alertId: sos.id })).status, 403, 'only the rider or lead clears an SOS');
  await r.bob('resolve', { alertId: sos.id });
  s = (await r.lead('lead', { toId: r.ids.bob })).data;
  assert.equal(s.leadId, r.ids.bob);
  s = (await r.bob('leave')).data;
  assert.notEqual(s.leadId, r.ids.bob);
});

test('regroup and destination can be set and cancelled by the lead only', async () => {
  const r = await ride();
  await r.lead('start');
  assert.equal((await r.bob('regroup', { lat: 1, lon: 2 })).status, 403);
  let s = (await r.lead('regroup', { lat: 1, lon: 2, label: 'Dhaba' })).data;
  assert.equal(s.regroup.label, 'Dhaba');
  s = (await r.lead('regroup', { clear: true })).data;
  assert.equal(s.regroup, null);
  s = (await r.lead('destination', { lat: 3, lon: 4, label: 'Goa' })).data;
  assert.equal(s.destination.label, 'Goa');
  s = (await r.lead('destination', { clear: true })).data;
  assert.equal(s.destination, null);
  s = (await r.lead('end')).data;
  assert.equal(s.status, 'ended');
});

test('catching up: rider asks the group to wait, lead stops and waits, rider catches up', async () => {
  const r = await ride();
  await r.lead('start');
  let s = (await r.bob('alert', { kind: 'CATCH_UP' })).data;
  const a = s.alerts.find((x) => x.kind === 'CATCH_UP' && !x.resolvedAt);
  assert.ok(a);
  assert.ok(s.messages.some((m) => /Bob is catching up/.test(m.text)));
  s = (await r.lead('teamStop', { reason: 'Waiting for Bob' })).data;
  assert.ok(s.alerts.find((x) => x.id === a.id && !x.resolvedAt), 'stop does not clear the catch-up');
  s = (await r.bob('resolve', { alertId: a.id, caughtUp: true })).data;
  assert.ok(s.messages.some((m) => /Bob caught up with the group/.test(m.text)));
});

test('stop & wait for a catching-up rider exempts that rider from the stop', async () => {
  const r = await ride();
  await r.lead('start');
  await r.bob('alert', { kind: 'CATCH_UP' });
  const s = (await r.lead('teamStop', { reason: 'Waiting for Bob', waitForId: r.ids.bob })).data;
  assert.equal(s.teamStop.waitForId, r.ids.bob);
  assert.ok(s.teamStop.confirmed.includes(r.ids.bob));
  assert.equal(rider(s, r.ids.bob).state, 'OK', 'Bob keeps riding');
  assert.ok(!s.teamStop.confirmed.includes(r.ids.cy), 'Cy still has to stop');
});

test('voice chat: membership, ICE config and signalling between two riders', async () => {
  const r = await ride();
  await r.lead('start');
  assert.ok((await call('/ice')).data.iceServers[0].urls.length > 0);
  let s = (await r.lead('voiceJoin')).data;
  assert.deepEqual(s.voice, [r.ids.lead]);
  s = (await r.bob('voiceJoin')).data;
  assert.equal(s.voice.length, 2);
  assert.ok(s.messages.some((m) => /Bob joined voice chat/.test(m.text)));

  // Cy is not in the voice channel, so cannot signal Bob.
  assert.equal((await r.cy('signal', { to: r.ids.bob, data: { k: 'offer' } })).status, 409);

  // Bob listens for signals; the lead sends one.
  const ctrl = new AbortController();
  const bobCreds = r.creds.bob;
  const res = await fetch(`${base}/api/rides/${r.code}/events?riderId=${bobCreds.riderId}&token=${bobCreds.token}`, { signal: ctrl.signal });
  assert.equal(res.headers.get('content-type').split(';')[0], 'text/event-stream');
  const reader = res.body.getReader();
  await reader.read(); // ": connected"
  assert.equal((await r.lead('signal', { to: r.ids.bob, data: { k: 'offer', sdp: 'x' } })).status, 200);
  const { value } = await reader.read();
  const msg = JSON.parse(new TextDecoder().decode(value).replace(/^data: /, '').trim());
  assert.equal(msg.from, r.ids.lead);
  assert.equal(msg.data.k, 'offer');
  ctrl.abort();

  s = (await r.bob('voiceLeave')).data;
  assert.deepEqual(s.voice, [r.ids.lead]);
  s = (await r.lead('end')).data;
  assert.deepEqual(s.voice, []);
});

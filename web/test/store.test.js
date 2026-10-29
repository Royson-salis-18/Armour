'use strict';
// Saving and restoring a ride keeps riders (with their tokens) and drops live-only state.
const { test } = require('node:test');
const assert = require('node:assert/strict');
const { serialize, hydrate } = require('../store');

test('a ride survives a save and restore', () => {
  const ride = {
    code: 'ABC234', name: 'Coast', status: 'riding', updatedAt: 5, version: 3, leadId: 'r1', messages: [{ text: 'hi' }],
    riders: new Map([['r1', { id: 'r1', token: 't1', name: 'Asha', phone: '+91980', lat: 12.9, lon: 77.5 }]]),
    voice: new Set(['r1']), sse: new Map([['r1', {}]]), _savedAt: 1,
  };
  const back = hydrate(JSON.parse(JSON.stringify(serialize(ride))));
  assert.equal(back.code, 'ABC234');
  assert.equal(back.riders.get('r1').token, 't1');
  assert.equal(back.riders.get('r1').phone, '+91980');
  assert.equal(back.messages[0].text, 'hi');
  assert.equal(back.voice.size, 0, 'voice calls do not survive a restart');
  assert.equal(back.sse.size, 0);
  assert.equal(back._savedAt, 5, 'a restored ride is not written straight back');
});

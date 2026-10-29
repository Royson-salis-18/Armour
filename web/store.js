// Keeps rides in Postgres so a server restart or redeploy doesn't lose them.
// Set DATABASE_URL to enable it; without it rides live in memory only (tests, local runs).
// Rides stay in memory for speed; changed rides are written out every few seconds and loaded back on start.

'use strict';

const SAVE_EVERY_MS = 4000;

function serialize(ride) {
  const { riders, voice, sse, _savedAt, ...rest } = ride;
  return { ...rest, riders: [...riders.values()] };
}

function hydrate(data) {
  return { ...data, riders: new Map(data.riders.map((r) => [r.id, r])), voice: new Set(), sse: new Map(), _savedAt: data.updatedAt };
}

function create(url) {
  if (!url) return { enabled: false, async load() { return []; }, start() {}, remove() {}, async flush() {} };
  const { Pool } = require('pg');
  const pool = new Pool({ connectionString: url, ssl: /localhost|127\.0\.0\.1/.test(url) ? false : { rejectUnauthorized: false }, max: 3 });
  pool.on('error', (e) => console.error('database:', e.message));
  let rides = null;
  let busy = false;

  async function flush() {
    if (busy || !rides) return;
    busy = true;
    try {
      for (const ride of rides.values()) {
        if (ride._savedAt === ride.updatedAt) continue;
        const stamp = ride.updatedAt;
        await pool.query(
          'INSERT INTO rides (code, data, updated_at) VALUES ($1, $2, to_timestamp($3 / 1000.0)) ON CONFLICT (code) DO UPDATE SET data = EXCLUDED.data, updated_at = EXCLUDED.updated_at',
          [ride.code, JSON.stringify(serialize(ride)), stamp],
        );
        ride._savedAt = stamp;
      }
    } catch (e) {
      console.error('database save failed:', e.message);
    } finally {
      busy = false;
    }
  }

  return {
    enabled: true,
    /** Creates the table if needed and returns the rides younger than maxAgeMs. */
    async load(maxAgeMs) {
      await pool.query('CREATE TABLE IF NOT EXISTS rides (code text PRIMARY KEY, data jsonb NOT NULL, updated_at timestamptz NOT NULL)');
      await pool.query("DELETE FROM rides WHERE updated_at < now() - ($1 || ' milliseconds')::interval", [String(maxAgeMs)]);
      const { rows } = await pool.query('SELECT data FROM rides');
      return rows.map((r) => hydrate(r.data));
    },
    start(map) {
      rides = map;
      setInterval(flush, SAVE_EVERY_MS).unref();
    },
    remove(code) {
      pool.query('DELETE FROM rides WHERE code = $1', [code]).catch((e) => console.error('database delete failed:', e.message));
    },
    flush,
  };
}

module.exports = { create, serialize, hydrate };

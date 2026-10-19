import { test } from 'vitest';
import assert from 'node:assert/strict';
import { persistProgress } from '../src/lib/storage.js';
import { createState, STORAGE_KEY, STATE_VERSION } from '../src/lib/core.js';

test('an unreadable old record is preserved before fresh progress replaces it', () => {
  const data = new Map([[STORAGE_KEY, '{damaged']]);
  const storage = { setItem: (key, value) => data.set(key, value) };
  persistProgress(storage, createState(), '{damaged');
  assert.equal(data.get(`${STORAGE_KEY}.recovery`), '{damaged');
  assert.equal(JSON.parse(data.get(STORAGE_KEY)).version, STATE_VERSION);
});

test('if recovery cannot be saved, the original record is never overwritten', () => {
  const data = new Map([[STORAGE_KEY, '{damaged']]);
  const storage = { setItem: (key, value) => {
    if (key.endsWith('.recovery')) throw new Error('quota exceeded');
    data.set(key, value);
  } };
  assert.throws(() => persistProgress(storage, createState(), '{damaged'));
  assert.equal(data.get(STORAGE_KEY), '{damaged');
});

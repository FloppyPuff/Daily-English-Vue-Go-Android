import { test } from 'vitest';
import assert from 'node:assert/strict';
import { selectLocalVoice } from '../src/lib/speech.js';

test('English speech uses local voices and never falls back to a remote voice', () => {
  const remote = { lang: 'en-US', localService: false };
  const local = { lang: 'en-GB', localService: true };
  assert.equal(selectLocalVoice([remote, local]), local);
  assert.equal(selectLocalVoice([remote]), undefined);
  assert.equal(selectLocalVoice([]), undefined);
});

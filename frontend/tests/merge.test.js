import { test } from 'vitest';
import assert from 'node:assert/strict';
import { createState, mergeStates, parseBackup, rateWord, recordSentence, recordPaper, MAX_EVENTS } from '../src/lib/core.js';

const DAY = 86400000;
const T = 1700000000000;

// Catches a merge that lets an older device overwrite a newer review.
test('word records keep the later review, per word', () => {
  const local = rateWord(createState(), 'sustain', 'good', T);
  const remote = rateWord(createState(), 'climate', 'easy', T + DAY);
  const newer = rateWord(local, 'sustain', 'again', T + DAY);
  const merged = mergeStates(newer, remote);
  assert.deepEqual(merged.records.sustain, newer.records.sustain);
  assert.deepEqual(merged.records.climate, remote.records.climate);
});

// Catches a merge that drops one device's work, or counts shared practice twice.
test('progress, bookmarks and events are unioned without duplicates', () => {
  const shared = rateWord(createState(), 'sustain', 'good', T);
  const local = { ...rateWord(shared, 'climate', 'hard', T + 1), savedWords: ['by-product'] };
  const remote = { ...rateWord(shared, 'hot', 'easy', T + 2), savedSentences: ['s001'] };
  const merged = mergeStates(local, remote);
  assert.deepEqual(Object.keys(merged.records).sort(), ['climate', 'hot', 'sustain']);
  assert.deepEqual(merged.savedWords, ['by-product']);
  assert.deepEqual(merged.savedSentences, ['s001']);
  assert.deepEqual(merged.events.map(e => e.id).sort(), ['climate', 'hot', 'sustain']);
  assert.deepEqual(merged.events.map(e => e.at), [T, T + 1, T + 2]);
});

// Catches a draft from one device hiding an answer the other device already graded.
test('sentence records keep the newer answer, and its draft travels with it', () => {
  const base = createState();
  const local = recordSentence(base, 's001', '设备 A 的译文', 'review', T);
  const remote = recordSentence({ ...base, drafts: { s002: '只在 A 上的草稿' } }, 's001', '设备 B 的译文', 'mastered', T + DAY);
  const merged = mergeStates(local, remote);
  assert.equal(merged.sentenceRecords.s001.answer, '设备 B 的译文');
  assert.equal(merged.drafts.s001, '设备 B 的译文');
  assert.equal(merged.drafts.s002, '只在 A 上的草稿');
});

// Catches a merged state the server would reject, which would strand the device.
test('the merge result is a valid backup and keeps this device’s plan', () => {
  const local = rateWord(createState(), 'sustain', 'good', T);
  local.settings.dailyGoal = 7;
  const remote = rateWord(createState(), 'climate', 'again', T + DAY);
  remote.settings.dailyGoal = 30;
  const merged = mergeStates(local, remote);
  assert.deepEqual(parseBackup(JSON.stringify(merged)), merged);
  assert.equal(merged.settings.dailyGoal, 7);
});

// Catches an ever-growing history that permanently fails validation.
test('a merged history stays inside the backup limit, newest first', () => {
  const at = i => ({ type: 'word', id: 'sustain', quality: 'good', at: T + i * 1000, exam: 'cet6', isNew: false });
  const remote = { ...createState(), events: Array.from({ length: MAX_EVENTS }, (_, i) => at(i)) };
  const local = { ...createState(), events: [at(MAX_EVENTS), at(MAX_EVENTS + 1)] };
  const merged = mergeStates(local, remote);
  assert.equal(merged.events.length, MAX_EVENTS);
  assert.deepEqual(merged.events.slice(-2), [at(MAX_EVENTS), at(MAX_EVENTS + 1)]);
});

// Catches a device that is a day behind wiping the sheet you just graded.
test('paper records keep the newer attempt per sheet and both sheets survive', () => {
  const base = createState();
  const questions = [{ number: 1, options: { A: 'a', B: 'b' }, answer: 'a' }];
  const shared = recordPaper(base, { id: '2024-1#0-0', answers: { 1: 'b' }, questions }, T);
  const local = recordPaper(shared, { id: '2024-1#0-0', answers: { 1: 'a' }, questions }, T + DAY);
  const remote = recordPaper(shared, { id: '2023-1#0-0', answers: { 1: 'a' }, questions }, T + 1);
  const merged = mergeStates(local, remote);
  assert.deepEqual(merged.papers['2024-1#0-0'].answers, { 1: 'a' });
  assert.equal(Object.keys(merged.papers).length, 2);
  assert.equal(merged.events.filter(e => e.type === 'paper').length, 3);
});

import { test } from 'vitest';
import assert from 'node:assert/strict';
import { createState, rateWord, recordSentence, buildQueue, parseBackup, isWordId, isSentenceId, STORAGE_KEY, STATE_VERSION } from '../src/lib/core.js';

const at = new Date(2026, 9, 17, 12).getTime();

// Real content is keyed by headword and by past-paper question numbers, so the
// demo-era /^w\d{3}$/ and /^s\d{3}$/ guards would reject every genuine item.
test('word ids accept headwords, hyphenated entries and the legacy demo keys', () => {
  for (const id of ['sustain', 'by-product', 'father-in-law', 'w001', 'meticulous']) assert.equal(isWordId(id), true, id);
  for (const id of ['', 'Sustain', '-abandon', 'a'.repeat(40), 'sus tain', 'w0011', 'sus/1', '🍺word']) assert.equal(isWordId(id), false, id);
});

test('sentence ids accept curated keys, paper question keys and CET-6 keys', () => {
  for (const id of ['s001', '1998-1#31', '2025-2#1', 'cet6-202512-1']) assert.equal(isSentenceId(id), true, id);
  for (const id of ['', '#31', 's1#', '2025-1#31#7', 'x'.repeat(40)]) assert.equal(isSentenceId(id), false, id);
});

test('rating a real headword and a paper sentence is accepted', () => {
  const state = rateWord(createState(), 'sustain', 'good', at);
  assert.equal(state.records.sustain.due, at + 3 * 86400000);
  const withSentence = recordSentence(state, '1998-1#31', 'the past', 'review', at);
  assert.ok(withSentence.sentenceRecords['1998-1#31']);
});

test('a version 1 backup is migrated onto headword keys', () => {
  const v1 = {
    ...createState(), version: 1,
    records: { w001: { attempts: 2, interval: 3, due: at, streak: 1, lapses: 0, difficult: false, lastReview: at } },
    savedWords: ['w001'],
    events: [{ type: 'word', id: 'w001', quality: 'good', at, exam: 'cet6', isNew: false }],
  };
  const migrated = parseBackup(JSON.stringify(v1));
  assert.equal(migrated.version, STATE_VERSION);
  assert.equal(migrated.records.sustain.attempts, 2);
  assert.equal(migrated.records.w001, undefined);
  assert.deepEqual(migrated.savedWords, ['sustain']);
  assert.equal(migrated.events[0].id, 'sustain');
});

test('sentence records and drafts survive the migration untouched', () => {
  const v1 = {
    ...createState(), version: 1,
    sentenceRecords: { s001: { answer: 'answer', rating: 'mastered', at } },
    drafts: { s001: 'answer' },
    savedSentences: ['s001'],
  };
  const migrated = parseBackup(JSON.stringify(v1));
  assert.equal(migrated.sentenceRecords.s001.rating, 'mastered');
  assert.equal(migrated.drafts.s001, 'answer');
  assert.deepEqual(migrated.savedSentences, ['s001']);
});

test('an old key whose headword already has progress keeps the more recent record', () => {
  const v1 = {
    ...createState(), version: 1,
    records: {
      w001: { attempts: 5, interval: 9, due: at, streak: 3, lapses: 1, difficult: false, lastReview: at - 86400000 },
      sustain: { attempts: 1, interval: 3, due: at, streak: 1, lapses: 0, difficult: false, lastReview: at },
    },
  };
  const migrated = parseBackup(JSON.stringify(v1));
  assert.equal(Object.keys(migrated.records).length, 1);
  assert.equal(migrated.records.sustain.attempts, 1);
});

test('an unmigratable key is preserved rather than discarded', () => {
  const v1 = { ...createState(), version: 1, records: { w091: { attempts: 1, interval: 3, due: at, streak: 0, lapses: 0, difficult: false, lastReview: at } } };
  assert.equal(parseBackup(JSON.stringify(v1)).records.w091.attempts, 1);
});

test('a version 2 backup round-trips without renumbering', () => {
  const state = recordSentence(rateWord(createState(), 'by-product', 'hard', at), 'cet6-202512-1', '翻译', 'review', at);
  assert.deepEqual(parseBackup(JSON.stringify(state)), state);
});

// The browser of someone who studied before sheets existed: their progress moves up
// to the new version with an empty paper bank, not a rejected restore.
test('a version 2 backup without papers reads as a current state', () => {
  const v2 = { ...rateWord(createState(), 'sustain', 'good', at), version: 2 };
  delete v2.papers;
  const restored = parseBackup(JSON.stringify(v2));
  assert.equal(restored.version, STATE_VERSION);
  assert.deepEqual(restored.papers, {});
  assert.equal(restored.records.sustain.attempts, 1);
});

test('new words are studied by past-paper frequency before rarer ones', () => {
  const bank = [
    { id: 'rare', exams: ['cet6'] },
    { id: 'hot', exams: ['cet6'], hits: { cet6: 6 } },
    { id: 'warm', exams: ['cet6'], hits: { cet6: 2 } },
  ];
  assert.deepEqual(buildQueue(bank, createState(), 'new', at).map(w => w.id), ['hot', 'warm', 'rare']);
});

test('the merged queue includes both study tracks', () => {
  const bank = [{ id: 'only-cet6', exams: ['cet6'], hits: { cet6: 9 } }, { id: 'only-postgrad', exams: ['postgrad'] }];
  const state = { ...createState(), settings: { ...createState().settings, exam: 'postgrad' } };
  assert.deepEqual(buildQueue(bank, state, 'new', at).map(w => w.id), ['only-cet6', 'only-postgrad']);
});

test('the storage key stays put so an existing browser record is still found', () => {
  assert.equal(STORAGE_KEY, 'daily-english.v1');
  assert.equal(STATE_VERSION, 3);
});

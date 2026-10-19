import { test } from 'vitest';
import assert from 'node:assert/strict';
import { createState, rateWord, buildQueue, statistics, parseBackup, checkSpelling, recordSentence, gradePaper, recordPaper } from '../src/lib/core.js';

const at = new Date(2026, 9, 17, 12).getTime();
const bank = [{ id: 'w001', exams: ['cet6', 'postgrad'] }, { id: 'w002', exams: ['cet6'] }, { id: 'w003', exams: ['postgrad'] }];

test('a first review schedules a future review and does not mutate old progress', () => {
  const initial = createState();
  const next = rateWord(initial, 'w001', 'good', at);
  assert.equal(next.records.w001.due, at + 3 * 86400000);
  assert.equal(next.records.w001.attempts, 1);
  assert.equal(initial.records.w001, undefined);
  assert.equal(next.events[0].isNew, true);
});

test('forgetting puts a word into the difficult queue and resets its streak', () => {
  let state = rateWord(createState(), 'w001', 'easy', at);
  state = rateWord(state, 'w001', 'again', at + 1000);
  assert.equal(state.records.w001.due, at + 1000 + 600000);
  assert.equal(state.records.w001.streak, 0);
  assert.equal(state.records.w001.difficult, true);
  assert.equal(state.records.w001.lapses, 1);
});

test('review queues exclude future and unstudied words across the merged bank', () => {
  let state = rateWord(createState(), 'w001', 'again', at - 700000);
  state = rateWord(state, 'w003', 'again', at - 700000);
  assert.deepEqual(buildQueue(bank, state, 'review', at).map(w => w.id), ['w001', 'w003']);
  assert.deepEqual(buildQueue(bank, state, 'new', at).map(w => w.id), ['w002']);
});

test('statistics can show one track or the merged bank without losing progress', () => {
  let state = rateWord(createState(), 'w001', 'good', at);
  state = rateWord(state, 'w003', 'good', at + 1000);
  const all = statistics(state, bank, at + 1000);
  assert.equal(all.total, 3);
  assert.equal(all.learned, 2);
  const cet6 = statistics(state, bank, at + 1000, 'cet6');
  assert.equal(cet6.total, 2);
  assert.equal(cet6.learned, 1);
  assert.equal(cet6.todayWords, 1);
  const postgrad = statistics(state, bank, at + 1000, 'postgrad');
  assert.equal(postgrad.total, 2);
  assert.equal(postgrad.learned, 2);
  assert.equal(postgrad.todayWords, 2);
});

test('daily counts count distinct words, not repeated ratings', () => {
  let state = rateWord(createState(), 'w001', 'again', at);
  state = rateWord(state, 'w001', 'good', at + 1000);
  const stats = statistics(state, bank, at);
  assert.equal(stats.todayWords, 1);
  assert.equal(stats.todayNew, 1);
  assert.equal(stats.streak, 1);
  assert.equal(statistics(state, bank, at + 2 * 86400000).streak, 0);
});

test('backup restore round-trips progress but rejects malformed nested fields', () => {
  const state = rateWord(createState(), 'w001', 'good', at);
  const restored = parseBackup(JSON.stringify(state));
  assert.equal(restored.records.w001.attempts, 1);
  assert.throws(() => parseBackup('{"version":99}'));
  const bad = structuredClone(state);
  bad.records.w001.due = 'tomorrow';
  assert.throws(() => parseBackup(JSON.stringify(bad)));
  assert.throws(() => parseBackup('{"version":1,"settings":null}'));
});

test('spelling ignores outer spaces and letter case but not missing letters', () => {
  assert.equal(checkSpelling('  Sustain  ', 'sustain'), true);
  assert.equal(checkSpelling('sustan', 'sustain'), false);
  assert.equal(checkSpelling('', 'sustain'), false);
});

test('sentence self-assessment records real answers and counts a sentence once', () => {
  let state = recordSentence(createState(), 's001', '持续投入有助于维持增长。', 'review', at);
  state = recordSentence(state, 's001', '长期投入有助于维持增长。', 'mastered', at + 1000);
  assert.equal(state.sentenceRecords.s001.answer, '长期投入有助于维持增长。');
  assert.equal(statistics(state, bank, at).todaySentences, 1);
  assert.equal(state.sentenceRecords.s001.rating, 'mastered');
});

test('uncertain recall breaks a consecutive successful mastery streak', () => {
  let state = createState();
  for (const [i, quality] of ['good', 'good', 'hard', 'good', 'hard'].entries()) state = rateWord(state, 'w001', quality, at + i * 1000);
  assert.equal(statistics(state, bank, at).mastered, 0);
  assert.equal(state.records.w001.streak, 0);
});

test('short focus segments add up to one active learning day', () => {
  const state = createState();
  state.events = [
    { type: 'focus', exam: 'cet6', at, seconds: 30 },
    { type: 'focus', exam: 'cet6', at: at + 100000, seconds: 30 },
  ];
  const stats = statistics(state, bank, at);
  assert.equal(stats.todayMinutes, 1);
  assert.equal(stats.activeDays, 1);
  assert.equal(stats.streak, 1);
});

test('successive spelling groups move past recently attempted vocabulary', () => {
  const state = rateWord(createState(), 'w001', 'good', at);
  assert.deepEqual(buildQueue(bank, state, 'all', at).map(w => w.id), ['w002', 'w003', 'w001']);
  state.savedWords = ['w001', 'w002'];
  assert.equal(buildQueue(bank, state, 'saved', at)[0].id, 'w002');
});

// Past-paper practice: the sheet is graded here so a reload cannot hand back a
// greener score than the answers deserve.
const clozeQuestions = [{ number: 1, options: { A: 'Through', B: 'Despite', C: 'Besides', D: 'Without' }, answer: 'D' }, { number: 2, options: { A: 'a', B: 'b', C: 'c', D: 'd' }, answer: 'A' }];

test('a paper sheet is graded against its own answer key', () => {
  assert.deepEqual(gradePaper({ 1: 'D', 2: 'B' }, clozeQuestions), { correct: 1, total: 2 });
  assert.deepEqual(gradePaper({}, clozeQuestions), { correct: 0, total: 2 });
  // A translation group has no key to grade: it is self-assessed.
  assert.deepEqual(gradePaper({ 46: '我的译文' }, [{ number: 46, answer: '参考译文' }]), { correct: 0, total: 0 });
});

test('submitting a sheet stores the score, the wrong numbers and one event', () => {
  const initial = createState();
  const next = recordPaper(initial, { id: '2024-1#0-0', answers: { 1: 'D', 2: 'B' }, questions: clozeQuestions, words: ['despite'] }, at, 'postgrad');
  const record = next.papers['2024-1#0-0'];
  assert.deepEqual(record.answers, { 1: 'D', 2: 'B' });
  assert.equal(record.correct, 1);
  assert.equal(record.total, 2);
  assert.deepEqual(record.wrong, [2]);
  assert.deepEqual(record.words, ['despite']);
  assert.deepEqual(next.events, [{ type: 'paper', id: '2024-1#0-0', at, exam: 'postgrad', correct: 1, total: 2 }]);
  assert.deepEqual(initial.papers, {});
});

test('a translation sheet keeps the written answer and the self-assessment', () => {
  const state = recordPaper(createState(), { id: '2024-1#2-0', answers: { 46: '它们有时会行走六十多英里。' }, questions: [{ number: 46, answer: '参考译文' }], rating: 'mastered' }, at);
  const record = state.papers['2024-1#2-0'];
  assert.equal(record.correct, 0);
  assert.equal(record.total, 0);
  assert.equal(record.rating, 'mastered');
  assert.equal(statistics(state, bank, at).todayPapers, 1);
});

test('paper records survive a backup round-trip and a malformed one is refused', () => {
  const state = recordPaper(createState(), { id: '2024-1#0-0', answers: { 1: 'D' }, questions: clozeQuestions }, at);
  assert.deepEqual(parseBackup(JSON.stringify(state)).papers, state.papers);
  // Backups written before this feature simply have no papers to restore.
  const old = structuredClone(state); delete old.papers;
  assert.deepEqual(parseBackup(JSON.stringify(old)).papers, {});
  for (const broken of [['2024-1#0-0', 'nope'], ['../../etc/passwd', { answers: {} }], ['2024-1#0-0', { answers: { 1: {} } }], ['2024-1#0-0', { answers: { 1: 'D' }, correct: 5, total: 2 }]]) {
    const bad = structuredClone(state);
    bad.papers[broken[0]] = broken[1];
    assert.throws(() => parseBackup(JSON.stringify(bad)), 'rejects a forged paper record');
  }
});

test('paper accuracy counts graded questions only', () => {
  let state = recordPaper(createState(), { id: '2024-1#0-0', answers: { 1: 'D', 2: 'B' }, questions: clozeQuestions }, at);
  state = recordPaper(state, { id: '2024-1#2-0', answers: { 46: '译文' }, questions: [{ number: 46, answer: '参考' }], rating: 'review' }, at + 1000);
  const stats = statistics(state, bank, at);
  assert.equal(stats.todayPapers, 2);
  assert.equal(stats.paperCorrect, 1);
  assert.equal(stats.paperTotal, 2);
});

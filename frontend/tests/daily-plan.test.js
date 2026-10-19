import { test } from 'vitest';
import assert from 'node:assert/strict';
import { createState, dailyPlan, rateWord, recordSentence } from '../src/lib/core.js';
const at = new Date(2026, 9, 17, 12).getTime();
const words = [
  { id: 'w001', exams: ['cet6', 'postgrad'] },
  { id: 'w002', exams: ['cet6'] },
  { id: 'w003', exams: ['cet6'] },
  { id: 'w004', exams: ['postgrad'] },
];
const sentences = [{ id: 's001', exams: ['cet6'] }, { id: 's002', exams: ['cet6'] }, { id: 's003', exams: ['postgrad'] }];

test('today starts with new words when there is nothing due, without changing saved progress', () => {
  const state = createState();
  const before = JSON.stringify(state);
  const plan = dailyPlan(state, words, sentences, at);
  assert.equal(plan.active, 'new');
  assert.deepEqual(plan.steps.map(s => s.id), ['review', 'new', 'sentences']);
  assert.equal(plan.steps[0].total, 0);
  assert.equal(plan.steps[1].total, 4);
  assert.equal(JSON.stringify(state), before);
});

test('due reviews come first, and reviewed words drop out of the pending queue', () => {
  let state = rateWord(createState(), 'w001', 'again', at - 700000);
  let plan = dailyPlan(state, words, sentences, at);
  assert.equal(plan.active, 'review');
  assert.equal(plan.review[0].id, 'w001');
  state = rateWord(state, 'w001', 'good', at);
  plan = dailyPlan(state, words, sentences, at);
  assert.equal(plan.active, 'new');
  assert.equal(plan.steps[0].done, 1);
  assert.equal(plan.steps[0].total, 1);
});

test('new-word sessions use the remaining daily target, not a fresh full group', () => {
  let state = createState(); state.settings.dailyGoal = 2;
  state = rateWord(state, 'w001', 'good', at);
  const plan = dailyPlan(state, words, sentences, at);
  assert.equal(plan.newWords.length, 1);
  assert.equal(plan.newWords[0].id, 'w002');
  assert.equal(plan.steps[1].done, 1);
  assert.equal(plan.steps[1].total, 2);
});

test('sentence practice excludes completed sentences today and offers a finished state', () => {
  let state = createState(); state.settings.dailyGoal = 1; state.settings.sentenceGoal = 1;
  state = rateWord(state, 'w001', 'good', at);
  let plan = dailyPlan(state, words, sentences, at);
  assert.equal(plan.active, 'sentences');
  assert.equal(plan.sentences[0].id, 's001');
  state = recordSentence(state, 's001', '我的译文', 'review', at);
  plan = dailyPlan(state, words, sentences, at);
  assert.equal(plan.active, 'done');
  assert.equal(plan.sentences.length, 0);
  assert.equal(plan.steps[2].done, 1);
});

test('daily plans merge both exam tracks and keep shared progress', () => {
  let state = rateWord(createState(), 'w001', 'good', at);
  // The legacy settings value must not shrink the merged bank.
  state.settings.exam = 'postgrad';
  const plan = dailyPlan(state, words, sentences, at);
  assert.deepEqual(plan.newWords.map(w => w.id), ['w002', 'w003', 'w004']);
  assert.deepEqual(plan.sentences.map(s => s.id), ['s001', 's002', 's003']);
  assert.equal(plan.steps[1].done, 1);
});

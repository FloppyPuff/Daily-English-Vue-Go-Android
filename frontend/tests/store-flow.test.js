// @vitest-environment jsdom
import { test, expect, beforeEach } from 'vitest';
import { app, stats, currentWord, initSession, revealWord, rateCurrentWord, selectSentence, setSentenceDraft, rateCurrentSentence, openPaper, openPaperGroup, pickPaperOption, submitPaper, setStatsExam } from '../src/stores/app.js';
import { createState } from '../src/lib/core.js';

const words = [
  { id: 'alpha', meaning: '阿尔法', exams: ['cet6'], tiers: { cet6: '高频' }, hits: { cet6: 3 } },
  { id: 'beta', meaning: '贝塔', exams: ['postgrad'], tiers: { postgrad: '中频' }, hits: { postgrad: 2 } },
];
const sentences = [{ id: 's001', exams: ['cet6'], direction: 'en2zh', en: 'A sentence.', zh: '一个句子。' }];
const paper = {
  id: '2024-1',
  year: 2024,
  exam: '英语一',
  sections: [{
    title: 'Section I',
    groups: [{
      type: 'cloze',
      passage: ['A {{1}} and {{2}}.'],
      questions: [
        { number: 1, options: { A: 'one', B: 'two' }, answer: 'A' },
        { number: 2, options: { A: 'one', B: 'two' }, answer: 'B' },
      ],
    }],
  }],
};

const wordById = new Map(words.map(word => [word.id, word]));
const sentenceById = new Map(sentences.map(sentence => [sentence.id, sentence]));

beforeEach(() => {
  app.content = {
    words,
    sentences,
    paperSets: [{ id: '2024-1', year: 2024, exam: '英语一' }],
    wordById: id => wordById.get(id),
    sentenceById: id => sentenceById.get(id),
    paper: async () => paper,
  };
  app.state = createState();
  app.ui.wordMode = 'new';
  app.ui.wordQueue = [];
  app.ui.wordIndex = 0;
  app.ui.revealed = false;
  app.ui.sentenceId = '';
  app.ui.sentenceDraft = '';
  app.ui.paperSet = '';
  app.ui.paper = null;
  app.ui.paperGroup = '';
  app.ui.paperAnswers = {};
  app.ui.paperChecked = false;
  app.ui.statsExam = 'all';
});

test('a merged queue rates both exam tracks and reports merged totals', () => {
  initSession('new');
  expect(app.ui.wordQueue).toEqual(['alpha', 'beta']);
  expect(currentWord.value.id).toBe('alpha');
  revealWord();
  rateCurrentWord('good');
  expect(app.state.records.alpha.attempts).toBe(1);
  expect(app.state.events.at(-1).id).toBe('alpha');
  setStatsExam('cet6');
  expect(stats.value.total).toBe(1);
  expect(stats.value.learned).toBe(1);
});

test('a sentence draft and assessment are kept in the shared state', () => {
  selectSentence('s001');
  setSentenceDraft('先写下译文。');
  rateCurrentSentence('mastered');
  expect(app.state.sentenceRecords.s001.rating).toBe('mastered');
  expect(app.state.drafts.s001).toBe('先写下译文。');
});

test('paper answers are graded and written back into the account state', async () => {
  await openPaper('2024-1');
  openPaperGroup('0-0');
  pickPaperOption(1, 'A');
  pickPaperOption(2, 'A');
  submitPaper({ key: '2024-1#0-0', type: 'cloze', questions: paper.sections[0].groups[0].questions, translation: false });
  const record = app.state.papers['2024-1#0-0'];
  expect(record.correct).toBe(1);
  expect(record.total).toBe(2);
  expect(app.state.events.at(-1).exam).toBe('postgrad');
});

import { LEGACY_WORD_IDS } from './legacy-ids.js';

const DAY = 86400000;
export const STORAGE_KEY = 'daily-english.v1';
// Version 1 keyed vocabulary by w001…w090, version 2 moved onto headwords, and
// version 3 added past-paper sheets. Only 1 → 2 rewrites data, so any backup at or
// below the current version can be read forward.
export const STATE_VERSION = 3;
export const EXAMS = { cet6: 'CET-6', postgrad: '考研英语' };
// Content is keyed by headword ("sustain", "by-product") and by past-paper
// question ("1998-1#31", "cet6-202512-1"); the demo-era /^w\d{3}$/ guards would
// reject every real item, so the accepted shapes are stated once, here, and
// shared with the server, which treats them as the payload boundary.
const WORD_ID = /^(?:w\d{3}|[a-z][a-z'-]{1,31})$/;
const SENTENCE_ID = /^(?:s\d{3}|\d{4}-[12]#\d{1,3}|cet6-\d{6}-\d{1,2})$/;
// A paper sheet is "setId#section-group": 2024-1#0-0 is Section I of the 2024
// 英语一 paper. The set id comes from the shipped index, so the shape is checkable
// without loading the sheet.
const PAPER_KEY = /^\d{4}-[12]#\d{1,2}-\d{1,2}$/;
export const isWordId = id => typeof id === 'string' && WORD_ID.test(id);
export const isSentenceId = id => typeof id === 'string' && SENTENCE_ID.test(id);
export const isPaperKey = key => typeof key === 'string' && PAPER_KEY.test(key);
// One ceiling, shared by the validator and the merge, so two long-lived devices
// can never grow a history that the server refuses to accept.
export const MAX_EVENTS = 100000;
export const localDay = (at = Date.now()) => {
  const d = new Date(at);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
};
export function createState() {
  return { version: STATE_VERSION, settings: { exam: 'cet6', dailyGoal: 20, sentenceGoal: 3, focusMinutes: 25 }, records: {}, sentenceRecords: {}, papers: {}, drafts: {}, savedWords: [], savedSentences: [], events: [] };
}
export function rateWord(state, id, quality, at = Date.now()) {
  if (!['again', 'hard', 'good', 'easy'].includes(quality) || !isWordId(id)) throw new Error('无效的记忆记录');
  const next = structuredClone(state);
  const old = next.records[id];
  const interval = quality === 'again' ? 0 : quality === 'hard' ? 1 : quality === 'good' ? Math.max(3, Math.round((old?.interval || 0) * 2.2)) : Math.max(7, Math.round((old?.interval || 0) * 2.8));
  next.records[id] = {
    attempts: (old?.attempts || 0) + 1,
    interval: Math.min(interval, 365),
    due: at + (quality === 'again' ? 600000 : Math.min(interval, 365) * DAY),
    streak: quality === 'again' || quality === 'hard' ? 0 : (old?.streak || 0) + 1,
    lapses: (old?.lapses || 0) + (quality === 'again' ? 1 : 0),
    difficult: quality === 'again' || quality === 'hard',
    lastReview: at,
  };
  next.events.push({ type: 'word', id, quality, at, exam: next.settings.exam, isNew: !old });
  return next;
}
export function recordSentence(state, id, answer, rating, at = Date.now()) {
  if (!isSentenceId(id) || !['review', 'mastered'].includes(rating)) throw new Error('无效的翻译记录');
  const next = structuredClone(state);
  next.sentenceRecords[id] = { answer: answer.slice(0, 10000), rating, at };
  next.drafts[id] = answer.slice(0, 10000);
  next.events.push({ type: 'sentence', id, rating, at, exam: next.settings.exam });
  return next;
}

// A multiple-choice question carries an answer letter; a translation question
// carries the reference version, which is read and self-assessed, never graded.
const isObjective = question => !!question.options && typeof question.answer === 'string' && question.answer.length === 1;
const answerText = value => (typeof value === 'string' ? value : '').trim();
export function gradePaper(answers, questions) {
  const scored = questions.filter(isObjective);
  return { correct: scored.filter(q => answerText(answers[q.number]) === q.answer).length, total: scored.length };
}
export function recordPaper(state, { id, answers = {}, questions = [], words = [], rating = '' }, at = Date.now(), exam = state.settings.exam) {
  if (!isPaperKey(id)) throw new Error('无效的真题记录');
  if (!['', 'review', 'mastered'].includes(rating)) throw new Error('无效的真题自评');
  const next = structuredClone(state);
  const scored = questions.filter(isObjective);
  const { correct, total } = gradePaper(answers, scored);
  next.papers[id] = {
    answers: Object.fromEntries(Object.entries(answers)
      .filter(([number, value]) => /^\d{1,3}$/.test(number) && typeof value === 'string' && value.length <= 10000).slice(0, 60)),
    correct, total,
    wrong: scored.filter(q => answerText(answers[q.number]) !== q.answer).map(q => q.number),
    words: [...new Set(words.filter(isWordId))].slice(0, 60),
    rating, at,
  };
  next.events.push({ type: 'paper', id, at, exam, correct, total });
  return next;
}
// The word bank is studied as one merged CET-6 + 考研 track. The records are
// keyed by headword, so a word shared by both exams already carries one shared
// review history; filtering by settings.exam here would only hide half the bank.
export function buildQueue(words, state, mode = 'new', at = Date.now()) {
  return words.filter(w => {
    const rec = state.records[w.id];
    if (mode === 'review') return rec && rec.due <= at;
    if (mode === 'mistakes') return rec?.difficult;
    if (mode === 'saved') return state.savedWords.includes(w.id);
    if (mode === 'all') return true;
    return !rec;
  }).sort((a, b) => {
    if (mode === 'review') return state.records[a.id].due - state.records[b.id].due;
    if (['all', 'saved', 'mistakes'].includes(mode)) return (state.records[a.id]?.lastReview || 0) - (state.records[b.id]?.lastReview || 0);
    if (mode === 'new') return paperHits(b) - paperHits(a); // Array#sort is stable: equal hits keep syllabus order.
    return 0;
  });
}
const paperHits = word => word.hits ? (word.hits.cet6 || 0) + (word.hits.postgrad || 0) : 0;
// Stats keep a CET-6 / 考研 filter, but a record belongs to a track by the item's
// own exam tags rather than by whichever track happened to be active when it was
// practiced. Focus time is not directional, so it counts in every view.
export function statistics(state, words, at = Date.now(), examFilter = 'all', sentences = []) {
  const today = localDay(at);
  const wordById = new Map(words.map(word => [word.id, word]));
  const sentenceById = new Map(sentences.map(sentence => [sentence.id, sentence]));
  const inTrack = event => {
    if (examFilter === 'all' || event.type === 'focus') return true;
    if (event.type === 'word') return wordById.get(event.id)?.exams.includes(examFilter) ?? event.exam === examFilter;
    if (event.type === 'sentence') return sentenceById.get(event.id)?.exams.includes(examFilter) ?? event.exam === examFilter;
    // All shipped papers are 考研英语, including events written before papers
    // started tagging themselves with their own track.
    if (event.type === 'paper') return examFilter === 'postgrad';
    return event.exam === examFilter;
  };
  const relevant = state.events.filter(inTrack);
  const todayEvents = relevant.filter(e => localDay(e.at) === today);
  const unique = (list, type) => new Set(list.filter(e => e.type === type).map(e => e.id)).size;
  const active = new Set(state.events.filter(e => e.type !== 'focus').map(e => localDay(e.at)));
  const focusByDay = new Map();
  for (const event of state.events.filter(e => e.type === 'focus')) {
    const day = localDay(event.at);
    focusByDay.set(day, (focusByDay.get(day) || 0) + event.seconds);
  }
  for (const [day, seconds] of focusByDay) if (seconds >= 60) active.add(day);
  const cursor = new Date(at);
  if (!active.has(today)) cursor.setDate(cursor.getDate() - 1);
  let streak = 0;
  while (active.has(localDay(cursor.getTime()))) { streak++; cursor.setDate(cursor.getDate() - 1); }
  const bank = examFilter === 'all' ? words : words.filter(w => w.exams.includes(examFilter));
  const learned = bank.filter(w => state.records[w.id]);
  const week = Array.from({ length: 7 }, (_, i) => {
    const d = new Date(at); d.setDate(d.getDate() - 6 + i);
    const day = localDay(d.getTime());
    const events = relevant.filter(e => localDay(e.at) === day);
    return { day, label: ['日', '一', '二', '三', '四', '五', '六'][d.getDay()], words: unique(events, 'word'), sentences: unique(events, 'sentence') };
  });
  const dailyCounts = {};
  for (const event of relevant) {
    if (event.type === 'focus') continue;
    const day = localDay(event.at);
    (dailyCounts[day] ||= new Set()).add(`${event.type}:${event.id}`);
  }
  return {
    todayWords: unique(todayEvents, 'word'), todayNew: unique(todayEvents.filter(e => e.isNew), 'word'),
    todaySentences: unique(todayEvents, 'sentence'),
    todayPapers: unique(todayEvents, 'paper'),
    paperCorrect: relevant.filter(e => e.type === 'paper').reduce((sum, e) => sum + e.correct, 0),
    paperTotal: relevant.filter(e => e.type === 'paper').reduce((sum, e) => sum + e.total, 0),
    todayMinutes: Math.floor(todayEvents.filter(e => e.type === 'focus').reduce((sum, e) => sum + e.seconds, 0) / 60),
    totalMinutes: Math.floor(relevant.filter(e => e.type === 'focus').reduce((sum, e) => sum + e.seconds, 0) / 60),
    learned: learned.length, mastered: learned.filter(w => state.records[w.id].streak >= 3).length,
    due: bank.filter(w => { const rec = state.records[w.id]; return rec && rec.due <= at; }).length,
    difficult: learned.filter(w => state.records[w.id].difficult).length,
    total: bank.length, streak, week, activeDays: active.size,
    dailyCounts: Object.fromEntries(Object.entries(dailyCounts).map(([day, ids]) => [day, ids.size])),
  };
}
export const checkSpelling = (answer, word) => answer.trim().toLowerCase() === word.toLowerCase();

export function dailyPlan(state, words, sentences, at = Date.now()) {
  const today = localDay(at);
  // Today's plan is one merged queue, so practice from either exam track counts
  // toward the same review, new-word and translation targets.
  const events = state.events.filter(event => localDay(event.at) === today);
  const review = buildQueue(words, state, 'review', at);
  const dueIds = new Set(review.map(word => word.id));
  const reviewedIds = new Set(events.filter(event => event.type === 'word' && !event.isNew).map(event => event.id));
  const reviewDone = [...reviewedIds].filter(id => !dueIds.has(id)).length;
  const newDone = new Set(events.filter(event => event.type === 'word' && event.isNew).map(event => event.id)).size;
  const unseen = buildQueue(words, state, 'new', at);
  const newTarget = Math.min(state.settings.dailyGoal, newDone + unseen.length);
  const newWords = unseen.slice(0, Math.max(0, newTarget - newDone));
  const bank = sentences;
  const sentenceIds = new Set(events.filter(event => event.type === 'sentence').map(event => event.id));
  const sentenceDone = bank.filter(sentence => sentenceIds.has(sentence.id)).length;
  const sentenceTarget = Math.min(state.settings.sentenceGoal, bank.length);
  const pendingSentences = bank.filter(sentence => !sentenceIds.has(sentence.id))
    .sort((a, b) => (state.sentenceRecords[a.id]?.at || 0) - (state.sentenceRecords[b.id]?.at || 0))
    .slice(0, Math.max(0, sentenceTarget - sentenceDone));
  const steps = [
    { id: 'review', label: '复习旧词', done: reviewDone, total: reviewDone + review.length, pending: review.length, unit: '词' },
    { id: 'new', label: '学习新词', done: Math.min(newDone, newTarget), total: newTarget, pending: newWords.length, unit: '词' },
    { id: 'sentences', label: '翻译句子', done: Math.min(sentenceDone, sentenceTarget), total: sentenceTarget, pending: pendingSentences.length, unit: '句' },
  ];
  return { active: steps.find(step => step.pending > 0)?.id || 'done', steps, review, newWords, sentences: pendingSentences };
}

// Backups are untrusted input. Validate every nested field before restoring and
// copy only supported fields; never merge objects supplied by the import file.
export function parseBackup(text) {
  if (typeof text !== 'string' || text.length > 5 * 1024 * 1024) throw new Error('备份文件过大（上限 5 MB）');
  let raw;
  try { raw = JSON.parse(text); } catch { throw new Error('文件不是有效的 JSON 备份'); }
  const fail = () => { throw new Error('备份格式不兼容或数据损坏，未覆盖当前进度'); };
  const object = x => x !== null && typeof x === 'object' && !Array.isArray(x);
  const number = x => typeof x === 'number' && Number.isFinite(x) && x >= 0;
  const time = x => number(x) && x <= 8640000000000000;
  const textValue = x => typeof x === 'string' && x.length <= 10000;
  const exam = x => ['cet6', 'postgrad'].includes(x);
  if (!object(raw) || !Number.isInteger(raw.version) || raw.version < 1 || raw.version > STATE_VERSION || !object(raw.settings)) fail();
  const s = raw.settings;
  if (!exam(s.exam) || !Number.isInteger(s.dailyGoal) || s.dailyGoal < 1 || s.dailyGoal > 100 || !Number.isInteger(s.sentenceGoal) || s.sentenceGoal < 1 || s.sentenceGoal > 20 || !Number.isInteger(s.focusMinutes) || s.focusMinutes < 1 || s.focusMinutes > 120) fail();
  // Version 1 keyed progress by w001…w090; those records follow their headword.
  // A key with no alias is kept as written — losing a study history is worse
  // than carrying an id the current bank does not list.
  const headword = raw.version === 1 ? id => LEGACY_WORD_IDS[id] || id : id => id;
  const result = createState();
  result.settings = { exam: s.exam, dailyGoal: s.dailyGoal, sentenceGoal: s.sentenceGoal, focusMinutes: s.focusMinutes };
  for (const field of ['records', 'sentenceRecords', 'drafts']) if (!object(raw[field])) fail();
  for (const [id, r] of Object.entries(raw.records)) {
    if (!isWordId(id) || !object(r) || !['attempts', 'interval', 'streak', 'lapses'].every(k => number(r[k])) || !time(r.due) || !time(r.lastReview) || typeof r.difficult !== 'boolean') fail();
    const clean = { attempts: r.attempts, interval: r.interval, due: r.due, streak: r.streak, lapses: r.lapses, difficult: r.difficult, lastReview: r.lastReview };
    const kept = result.records[headword(id)];
    if (!kept || kept.lastReview < clean.lastReview) result.records[headword(id)] = clean;
  }
  for (const [id, r] of Object.entries(raw.sentenceRecords)) {
    if (!isSentenceId(id) || !object(r) || !textValue(r.answer) || !['review', 'mastered'].includes(r.rating) || !time(r.at)) fail();
    result.sentenceRecords[id] = { answer: r.answer, rating: r.rating, at: r.at };
  }
  for (const [id, value] of Object.entries(raw.drafts)) { if (!isSentenceId(id) || !textValue(value)) fail(); result.drafts[id] = value; }
  // A sheet written before this feature simply has no papers to restore, so the
  // field is optional; when it is there, every part of it is checked.
  if (raw.papers !== undefined && !object(raw.papers)) fail();
  for (const [id, r] of Object.entries(raw.papers || {})) {
    if (!isPaperKey(id) || !object(r) || !number(r.correct) || !number(r.total) || r.correct > r.total) fail();
    if (!object(r.answers) || !Object.entries(r.answers).every(([number_, value]) => /^\d{1,3}$/.test(number_) && textValue(value))) fail();
    if (!Array.isArray(r.wrong) || !r.wrong.every(n => Number.isInteger(n) && n >= 1 && n <= 100)) fail();
    if (!Array.isArray(r.words) || !r.words.every(isWordId)) fail();
    if (!['', 'review', 'mastered'].includes(r.rating ?? '') || !time(r.at)) fail();
    result.papers[id] = { answers: { ...r.answers }, correct: r.correct, total: r.total, wrong: [...r.wrong], words: [...new Set(r.words)], rating: r.rating ?? '', at: r.at };
  }
  for (const [field, check] of [['savedWords', isWordId], ['savedSentences', isSentenceId]]) {
    if (!Array.isArray(raw[field]) || !raw[field].every(id => typeof id === 'string' && check(id))) fail();
    result[field] = [...new Set(raw[field].map(field === 'savedWords' ? headword : id => id))];
  }
  if (!Array.isArray(raw.events) || raw.events.length > MAX_EVENTS) fail();
  for (const e of raw.events) {
    if (!object(e) || !time(e.at) || !exam(e.exam)) fail();
    if (e.type === 'word') {
      if (!isWordId(e.id) || !['again', 'hard', 'good', 'easy'].includes(e.quality) || typeof e.isNew !== 'boolean') fail();
      result.events.push({ type: e.type, id: headword(e.id), quality: e.quality, at: e.at, exam: e.exam, isNew: e.isNew });
    } else if (e.type === 'sentence') {
      if (!isSentenceId(e.id) || !['review', 'mastered'].includes(e.rating)) fail();
      result.events.push({ type: e.type, id: e.id, rating: e.rating, at: e.at, exam: e.exam });
    } else if (e.type === 'paper') {
      if (!isPaperKey(e.id) || !number(e.correct) || !number(e.total) || e.correct > e.total) fail();
      result.events.push({ type: e.type, id: e.id, at: e.at, exam: e.exam, correct: e.correct, total: e.total });
    } else if (e.type === 'focus') {
      if (!number(e.seconds) || e.seconds > 7200) fail();
      result.events.push({ type: e.type, seconds: e.seconds, at: e.at, exam: e.exam });
    } else fail();
  }
  return result;
}

// Two devices that both studied since their last sync: neither copy is the truth,
// so nothing is dropped. Every key survives, and inside a key the later entry
// wins. Inputs are already validated states (parseBackup output or createState),
// so their shape is trusted here.
//
// Settings follow the device doing the merge — that is the plan being studied
// right now. Bookmarks are unioned, which can resurrect a bookmark removed on
// the other device; losing a star is a smaller harm than losing a term from
// every notebook.
export function mergeStates(local, remote) {
  const later = (a, b, stamp) => (!b || (a && stamp(a) >= stamp(b)) ? a : b);
  const keys = (a, b) => [...new Set([...Object.keys(a), ...Object.keys(b)])];
  const merged = createState();
  merged.settings = { ...local.settings };
  for (const id of keys(local.records, remote.records)) merged.records[id] = later(local.records[id], remote.records[id], r => r.lastReview);
  for (const id of keys(local.sentenceRecords, remote.sentenceRecords)) merged.sentenceRecords[id] = later(local.sentenceRecords[id], remote.sentenceRecords[id], r => r.at);
  for (const id of keys(local.papers, remote.papers)) merged.papers[id] = later(local.papers[id], remote.papers[id], r => r.at);
  for (const id of keys(local.drafts, remote.drafts)) {
    const mine = local.drafts[id];
    const theirs = remote.drafts[id];
    // A graded sentence carries its own answer; keep the draft that belongs to the
    // side whose grading is newer, so an old half-typed line cannot hide it.
    const preferred = (remote.sentenceRecords[id]?.at || 0) > (local.sentenceRecords[id]?.at || 0) ? theirs : mine;
    const value = preferred ?? mine ?? theirs;
    if (value) merged.drafts[id] = value;
  }
  for (const field of ['savedWords', 'savedSentences']) merged[field] = [...new Set([...local[field], ...remote[field]])];
  const seen = new Set();
  const events = [...local.events, ...remote.events].sort((a, b) => a.at - b.at).filter(event => {
    // Both devices upload from the same base revision, so the shared morning
    // practice arrives twice; the timestamp makes it recognisable.
    const key = `${event.type}|${event.id ?? ''}|${event.quality ?? event.rating ?? event.seconds ?? ''}|${event.at}`;
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });
  merged.events = events.slice(-MAX_EVENTS);
  return merged;
}

import { computed, reactive, shallowReactive } from 'vue';
import { dailyPlan, buildQueue, createState, parseBackup, rateWord, recordPaper, recordSentence, statistics, STORAGE_KEY, checkSpelling, localDay } from '../lib/core.js';
import { loadContent, paperGroups } from '../lib/content.js';
import { createAccount, syncLabel } from '../lib/sync.js';
import { persistProgress } from '../lib/storage.js';
import { selectLocalVoice } from '../lib/speech.js';

const GUEST_KEY = 'daily-english.guest';
const apiBase = globalThis.DAILY_ENGLISH_API_BASE || import.meta.env.VITE_API_BASE || '';
let recoveryRaw = null;
let account = null;
let toastTimer = 0;
let tickTimer = 0;
let draftTimer = 0;
let paperDraftTimer = 0;

const guestPass = () => {
  try { return sessionStorage.getItem(GUEST_KEY) === '1'; } catch { return false; }
};
const keepGuest = () => { try { sessionStorage.setItem(GUEST_KEY, '1'); } catch {} };
const dropGuest = () => { try { sessionStorage.removeItem(GUEST_KEY); } catch {} };

export const app = shallowReactive({
  ready: false,
  error: '',
  content: null,
  state: createState(),
  storageError: '',
  sync: reactive({ mode: 'anonymous', email: '', label: '未登录', tone: 'local', problem: '', status: 'idle' }),
  auth: reactive({ checked: false, mode: 'register', busy: false, error: '', email: '', guest: guestPass() }),
  ui: reactive({
    wordMode: 'new',
    wordQueue: [],
    wordIndex: 0,
    revealed: false,
    spellingInput: '',
    spellingChecked: false,
    spellingCorrect: null,
    sentenceId: '',
    sentenceRevealed: false,
    sentenceDraft: '',
    libraryQuery: '',
    libraryFilter: 'all',
    libraryPage: 0,
    notebookTab: 'saved',
    statsExam: 'all',
    paperFilter: 'all',
    paperSet: '',
    paper: null,
    paperLoading: false,
    paperGroup: '',
    paperAnswers: {},
    paperChecked: false,
    homeStage: '',
    guidedSession: false,
    homeSentenceRecorded: false,
    mobileNav: false,
    moreOpen: false,
    focusOpen: false,
  }),
  focus: reactive({
    running: false,
    totalSeconds: 25 * 60,
    remainingSeconds: 25 * 60,
    deadline: 0,
    checkpoint: 0,
  }),
  toast: reactive({ message: '', visible: false }),
});

export const stats = computed(() => app.content
  ? statistics(app.state, app.content.words, Date.now(), app.ui.statsExam, app.content.sentences)
  : null);

export const plan = computed(() => app.content
  ? dailyPlan(app.state, app.content.words, app.content.sentences)
  : null);

export const currentWord = computed(() => {
  const id = app.ui.wordQueue[app.ui.wordIndex];
  return id && app.content ? app.content.wordById(id) : null;
});

export const currentSentence = computed(() => {
  const list = app.content?.sentences || [];
  return list.find(sentence => sentence.id === app.ui.sentenceId) || list[0] || null;
});

export const dueWords = computed(() => app.content
  ? buildQueue(app.content.words, app.state, 'review')
  : []);

export const hasMoreWords = computed(() => app.content
  ? buildQueue(app.content.words, app.state, app.ui.wordMode === 'spell' ? 'all' : app.ui.wordMode).length > 0
  : false);

export const libraryList = computed(() => {
  if (!app.content) return [];
  const needle = app.ui.libraryQuery.trim().toLowerCase();
  return app.content.words.filter(word => {
    const haystack = `${word.id} ${word.meaning} ${word.pos || ''} ${word.topic || ''} ${(word.senses || []).join(' ')}`.toLowerCase();
    const learned = !!app.state.records[word.id];
    const matches = !needle || haystack.includes(needle);
    const filter = app.ui.libraryFilter === 'new' ? !learned : app.ui.libraryFilter === 'learned' ? learned : true;
    return matches && filter;
  });
});

export const notebookWords = computed(() => {
  if (!app.content) return [];
  const mistakes = app.ui.notebookTab === 'mistakes';
  const paperWords = new Set(mistakes ? Object.values(app.state.papers).flatMap(record => record.words || []) : []);
  return app.content.words.filter(word => mistakes
    ? app.state.records[word.id]?.difficult || paperWords.has(word.id)
    : app.state.savedWords.includes(word.id));
});

export const notebookSentences = computed(() => {
  if (!app.content) return [];
  const mistakes = app.ui.notebookTab === 'mistakes';
  return app.content.sentences.filter(sentence => mistakes
    ? app.state.sentenceRecords[sentence.id]?.rating === 'review'
    : app.state.savedSentences.includes(sentence.id));
});

export const currentPaperGroups = computed(() => app.ui.paper ? paperGroups(app.ui.paper) : []);

export const locked = computed(() => app.auth.checked && app.sync.mode !== 'member' && !app.auth.guest);

export function notify(message) {
  app.toast.message = message;
  app.toast.visible = true;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => { app.toast.visible = false; }, 3600);
}

function persist() {
  try {
    persistProgress(localStorage, app.state, recoveryRaw);
    recoveryRaw = null;
    app.storageError = '';
    return true;
  } catch {
    app.storageError = '浏览器未能保存进度，请导出备份。';
    return false;
  }
}

function save() {
  if (!persist()) notify('进度暂未保存，请导出备份。');
  account?.schedule();
}

function setState(next, { saveNow = true } = {}) {
  app.state = next;
  if (saveNow) save();
}

function toneOf(next) {
  return next.mode === 'member'
    ? (next.status === 'offline' ? 'warn' : next.status === 'saving' || next.status === 'dirty' ? 'busy' : 'ok')
    : next.status === 'signed-out' ? 'warn' : 'local';
}

function syncView() {
  if (!account) return;
  app.sync.mode = account.mode;
  app.sync.email = account.email;
  app.sync.status = account.status;
  app.sync.problem = account.problem;
  app.sync.label = syncLabel(account);
  app.sync.tone = toneOf(account);
}

function syncFocusTarget() {
  const target = app.state.settings.focusMinutes * 60;
  if (!app.focus.running && app.focus.remainingSeconds === app.focus.totalSeconds) {
    app.focus.totalSeconds = target;
    app.focus.remainingSeconds = target;
  } else if (!app.focus.running && app.focus.totalSeconds !== target && app.focus.remainingSeconds > target) {
    app.focus.totalSeconds = target;
    app.focus.remainingSeconds = target;
  }
}

function applyRemote(next) {
  if (!next || typeof next !== 'object') return;
  app.state = next;
  syncFocusTarget();
  persist();
  relearn();
}

function relearn() {
  if (!app.content) return;
  if (app.ui.wordQueue.some(id => !app.content.wordById(id))) {
    initSession(app.ui.wordMode === 'library' ? 'new' : app.ui.wordMode);
  }
  if (app.ui.sentenceId && !app.content.sentenceById(app.ui.sentenceId)) {
    app.ui.sentenceId = '';
    app.ui.sentenceRevealed = false;
    app.ui.sentenceDraft = '';
  }
  if (app.ui.paperSet && !(app.content.paperSets || []).some(set => set.id === app.ui.paperSet)) {
    resetPaper();
  }
}

export function initSession(mode = 'new', custom = null) {
  if (!app.content) return;
  app.ui.guidedSession = false;
  app.ui.wordMode = mode;
  const source = custom || buildQueue(app.content.words, app.state, mode === 'spell' ? 'all' : mode);
  app.ui.wordQueue = source.slice(0, app.state.settings.dailyGoal).map(word => word.id);
  app.ui.wordIndex = 0;
  app.ui.revealed = false;
  app.ui.spellingInput = '';
  app.ui.spellingChecked = false;
  app.ui.spellingCorrect = null;
}

export function initDaily(requestedStage = '') {
  const currentPlan = plan.value;
  if (!currentPlan) return;
  const stage = ['review', 'new', 'sentences'].includes(requestedStage) ? requestedStage : currentPlan.active;
  initSession(stage === 'review' ? 'review' : 'new', stage === 'review' ? currentPlan.review : stage === 'new' ? currentPlan.newWords : []);
  app.ui.homeStage = stage;
  app.ui.guidedSession = true;
  app.ui.homeSentenceRecorded = false;
  app.ui.sentenceRevealed = false;
  if (stage === 'sentences') {
    const id = currentPlan.sentences[0]?.id;
    if (id) selectSentence(id); else app.ui.sentenceId = '';
  }
}

export function nextWord() {
  app.ui.wordIndex += 1;
  app.ui.revealed = false;
  app.ui.spellingInput = '';
  app.ui.spellingChecked = false;
  app.ui.spellingCorrect = null;
}

export function revealWord() {
  if (currentWord.value) app.ui.revealed = true;
}

export function setSpellingInput(value) {
  app.ui.spellingInput = value;
  app.ui.spellingChecked = false;
  app.ui.spellingCorrect = null;
}

export function checkSpellingAnswer() {
  const word = currentWord.value;
  if (!word) return;
  app.ui.spellingChecked = true;
  app.ui.spellingCorrect = checkSpelling(app.ui.spellingInput, word.id);
}

export function rateCurrentWord(quality) {
  const word = currentWord.value;
  if (!word) return;
  const ready = app.ui.wordMode === 'spell' ? app.ui.spellingChecked : app.ui.revealed;
  if (!ready) return;
  setState(rateWord(app.state, word.id, quality));
  nextWord();
}

export function nextWordSession() {
  initSession(app.ui.wordMode === 'library' ? 'new' : app.ui.wordMode);
}

export function openWord(id) {
  if (!app.content?.wordById(id)) return;
  app.ui.wordMode = 'browse';
  app.ui.wordQueue = [id];
  app.ui.wordIndex = 0;
  app.ui.revealed = false;
  app.ui.spellingInput = '';
  app.ui.spellingChecked = false;
  app.ui.spellingCorrect = null;
}

export function toggleSavedWord(id) {
  if (!id) return;
  const saved = app.state.savedWords.includes(id);
  const next = structuredClone(app.state);
  next.savedWords = saved ? next.savedWords.filter(item => item !== id) : [...next.savedWords, id];
  setState(next);
  notify(saved ? '已移出收藏' : '已加入收藏');
}

export function selectSentence(id) {
  if (!app.content) return;
  app.ui.sentenceId = id;
  app.ui.sentenceRevealed = false;
  app.ui.sentenceDraft = app.state.drafts[id] ?? app.state.sentenceRecords[id]?.answer ?? '';
}

export function ensureSentence() {
  if (app.ui.sentenceId && app.content?.sentenceById(app.ui.sentenceId)) return;
  const id = plan.value?.sentences[0]?.id || app.content?.sentences[0]?.id;
  if (id) selectSentence(id);
}

export function setSentenceDraft(text) {
  app.ui.sentenceDraft = text;
  const id = app.ui.sentenceId;
  if (!id) return;
  clearTimeout(draftTimer);
  draftTimer = setTimeout(() => {
    if (app.ui.sentenceId !== id) return;
    const next = structuredClone(app.state);
    next.drafts[id] = text;
    setState(next);
  }, 420);
}

export function revealSentence() {
  app.ui.sentenceRevealed = !app.ui.sentenceRevealed;
}

export function rateCurrentSentence(rating) {
  const sentence = currentSentence.value;
  if (!sentence) return;
  if (!app.ui.sentenceDraft.trim()) {
    notify('先写下译文，再完成自评。');
    return;
  }
  setState(recordSentence(app.state, sentence.id, app.ui.sentenceDraft, rating));
  if (app.ui.guidedSession) app.ui.homeSentenceRecorded = true;
  notify(rating === 'mastered' ? '已记录：已经理解。' : '已记录：留待巩固。');
}

export function nextSentence() {
  const list = app.content?.sentences || [];
  if (!list.length) return;
  const index = list.findIndex(sentence => sentence.id === app.ui.sentenceId);
  const next = list[(index + 1 + list.length) % list.length];
  selectSentence(next.id);
}

export function toggleSavedSentence() {
  const sentence = currentSentence.value;
  if (!sentence) return;
  const saved = app.state.savedSentences.includes(sentence.id);
  const next = structuredClone(app.state);
  next.savedSentences = saved ? next.savedSentences.filter(id => id !== sentence.id) : [...next.savedSentences, sentence.id];
  setState(next);
  notify(saved ? '已取消收藏' : '已收藏句子');
}

export function setStatsExam(exam) {
  app.ui.statsExam = ['cet6', 'postgrad'].includes(exam) ? exam : 'all';
}

export function setLibraryQuery(value) {
  app.ui.libraryQuery = value;
  app.ui.libraryPage = 0;
}

export function setLibraryFilter(value) {
  app.ui.libraryFilter = value;
  app.ui.libraryPage = 0;
}

export function setPaperFilter(value) {
  app.ui.paperFilter = value;
}

export async function openPaper(id) {
  app.ui.paperSet = id;
  app.ui.paper = null;
  app.ui.paperGroup = '';
  app.ui.paperAnswers = {};
  app.ui.paperChecked = false;
  app.ui.paperLoading = true;
  try {
    const sheet = await app.content.paper(id);
    if (app.ui.paperSet === id) app.ui.paper = sheet;
  } catch {
    if (app.ui.paperSet === id) app.ui.paper = null;
  } finally {
    if (app.ui.paperSet === id) app.ui.paperLoading = false;
  }
}

export function resetPaper() {
  app.ui.paperSet = '';
  app.ui.paper = null;
  app.ui.paperLoading = false;
  app.ui.paperGroup = '';
  app.ui.paperAnswers = {};
  app.ui.paperChecked = false;
}

export function openPaperGroup(index) {
  const group = currentPaperGroups.value.find(item => item.index === index);
  if (!group) return;
  const record = app.state.papers[group.key];
  app.ui.paperGroup = index;
  app.ui.paperAnswers = structuredClone(record?.answers || {});
  app.ui.paperChecked = !!record && (record.total > 0 || !!record.rating);
}

export function closePaperGroup() {
  app.ui.paperGroup = '';
  app.ui.paperAnswers = {};
  app.ui.paperChecked = false;
}

export function pickPaperOption(number, choice) {
  app.ui.paperAnswers = { ...app.ui.paperAnswers, [String(number)]: choice };
  app.ui.paperChecked = false;
}

export function setPaperAnswer(number, value) {
  app.ui.paperAnswers = { ...app.ui.paperAnswers, [String(number)]: value };
  const group = currentPaperGroups.value.find(item => item.index === app.ui.paperGroup);
  if (!group) return;
  const record = app.state.papers[group.key];
  if (record && (record.total > 0 || record.rating)) return;
  clearTimeout(paperDraftTimer);
  paperDraftTimer = setTimeout(() => {
    const answers = paperAnswersFor(group);
    if (!Object.keys(answers).length) return;
    const previous = app.state.papers[group.key] || { answers: {}, correct: 0, total: 0, wrong: [], words: [], rating: '', at: Date.now() };
    if (previous.total > 0 || previous.rating) return;
    const next = structuredClone(app.state);
    next.papers[group.key] = { ...previous, answers, at: Date.now() };
    setState(next);
  }, 600);
}

export function togglePaperCheck() {
  app.ui.paperChecked = !app.ui.paperChecked;
}

function paperAnswersFor(group) {
  return Object.fromEntries(group.questions
    .map(question => [String(question.number), String(app.ui.paperAnswers[question.number] || '').trim()])
    .filter(([, text]) => text));
}

export function submitPaper(group) {
  if (!group || group.translation) return;
  const answers = paperAnswersFor(group);
  if (Object.keys(answers).length !== group.questions.length) {
    notify('先答完这一组，再提交。');
    return;
  }
  const missed = group.type === 'cloze' ? group.questions
    .filter(question => question.answer && answers[question.number] && answers[question.number] !== question.answer)
    .map(question => String(question.options[question.answer] || '').trim().toLowerCase())
    .map(text => app.content.wordById(text)?.id).filter(Boolean) : [];
  setState(recordPaper(app.state, { id: group.key, answers, questions: group.questions, words: missed }, Date.now(), 'postgrad'));
  app.ui.paperChecked = true;
  notify('这一组已批改。');
}

export function ratePaper(group, rating) {
  if (!group || !group.translation) return;
  const answers = paperAnswersFor(group);
  if (!Object.keys(answers).length) {
    notify('先写下译文，再自评。');
    return;
  }
  if (app.state.papers[group.key]?.rating === rating) return;
  setState(recordPaper(app.state, { id: group.key, answers, questions: group.questions, rating }, Date.now(), 'postgrad'));
  notify(rating === 'mastered' ? '已记录：这一组已经读懂。' : '已记录：留待巩固。');
}

export function checkpointFocus(now = Date.now()) {
  if (!app.focus.running) return;
  const end = Math.min(now, app.focus.deadline);
  const seconds = Math.floor((end - app.focus.checkpoint) / 1000);
  if (seconds <= 0) return;
  const next = structuredClone(app.state);
  next.events.push({ type: 'focus', seconds, at: end, exam: next.settings.exam });
  app.focus.checkpoint += seconds * 1000;
  setState(next);
}

function tickFocus() {
  if (!app.focus.running) return;
  if (Date.now() - app.focus.checkpoint >= 60000) checkpointFocus();
  app.focus.remainingSeconds = Math.max(0, Math.ceil((app.focus.deadline - Date.now()) / 1000));
  if (app.focus.remainingSeconds === 0) {
    pauseFocus();
    notify('这段专注已完成。');
  }
}

export function startFocus() {
  if (app.focus.running) return;
  if (app.focus.remainingSeconds <= 0) app.focus.remainingSeconds = app.focus.totalSeconds;
  app.focus.running = true;
  app.focus.deadline = Date.now() + app.focus.remainingSeconds * 1000;
  app.focus.checkpoint = Date.now();
  clearInterval(tickTimer);
  tickTimer = setInterval(tickFocus, 1000);
}

export function pauseFocus() {
  checkpointFocus();
  app.focus.remainingSeconds = Math.max(0, Math.ceil((app.focus.deadline - Date.now()) / 1000));
  app.focus.running = false;
  clearInterval(tickTimer);
  tickTimer = 0;
}

export function resetFocus() {
  pauseFocus();
  app.focus.totalSeconds = app.state.settings.focusMinutes * 60;
  app.focus.remainingSeconds = app.focus.totalSeconds;
}

export function setGoals({ dailyGoal, sentenceGoal, focusMinutes }) {
  const next = structuredClone(app.state);
  next.settings = { ...next.settings, dailyGoal, sentenceGoal, focusMinutes };
  setState(next);
  syncFocusTarget();
}

export function speakWord(word) {
  if (!word) return;
  speak(word.id);
}

export function speakSentence(sentence) {
  if (!sentence || sentence.direction === 'zh2en') return;
  speak(sentence.en);
}

function speak(text) {
  if (!text) return;
  if (typeof globalThis.DAILY_ENGLISH_SPEAK === 'function') {
    Promise.resolve(globalThis.DAILY_ENGLISH_SPEAK(text)).catch(() => notify('朗读暂不可用。'));
    return;
  }
  if (!('speechSynthesis' in window)) { notify('当前浏览器不支持朗读。'); return; }
  window.speechSynthesis.cancel();
  const voice = selectLocalVoice(window.speechSynthesis.getVoices());
  if (!voice) { notify('没有可用的本地英语语音。'); return; }
  const utterance = new SpeechSynthesisUtterance(text);
  utterance.lang = voice.lang;
  utterance.rate = 0.86;
  utterance.voice = voice;
  utterance.onerror = event => { if (!['interrupted', 'canceled'].includes(event.error)) notify('朗读暂不可用。'); };
  window.speechSynthesis.speak(utterance);
}

export async function submitAuth(email, password) {
  if (!account || app.auth.busy) return false;
  app.auth.busy = true;
  app.auth.error = '';
  try {
    await account[app.auth.mode === 'register' ? 'register' : 'login'](email, password);
    app.auth.guest = false;
    dropGuest();
    app.auth.email = '';
    syncView();
    initDaily();
    notify(app.auth.mode === 'register' ? '账号已创建，进度开始同步。' : '已登录，进度继续。');
    return true;
  } catch (error) {
    app.auth.error = error?.message || '暂时无法完成，请稍后再试。';
    return false;
  } finally {
    app.auth.busy = false;
  }
}

export function continueGuest() {
  keepGuest();
  app.auth.guest = true;
  app.auth.error = '';
  initDaily();
}

export function requestAuth() {
  dropGuest();
  app.auth.guest = false;
  app.auth.mode = 'login';
  app.auth.error = '';
}

export async function logout() {
  await account?.logout();
  dropGuest();
  app.auth.guest = false;
  app.auth.mode = 'login';
  app.auth.error = '';
  syncView();
  notify('已退出登录，本机进度保留。');
}

export async function changePassword(current, next) {
  if (!account) return false;
  try {
    await account.changePassword(current, next);
    notify('密码已更新。');
    return true;
  } catch (error) {
    notify(error?.message || '密码未修改。');
    return false;
  }
}

export async function syncNow() {
  if (!account || account.mode !== 'member') {
    notify('登录后才会同步进度。');
    return;
  }
  account.schedule();
  await account.flush();
  await account.wait();
  syncView();
  notify(account.status === 'offline' ? account.problem || '仍然离线，进度留在本机。' : '已同步。');
}

export function exportBackup() {
  checkpointFocus();
  const blob = new Blob([JSON.stringify(app.state, null, 2)], { type: 'application/json' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = `daily-english-${localDay()}.json`;
  link.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
  notify('备份已导出。');
}

export function importBackup(text) {
  checkpointFocus();
  try {
    const next = parseBackup(text);
    app.state = next;
    syncFocusTarget();
    persist();
    account?.schedule();
    relearn();
    initDaily();
    notify('备份已恢复。');
    return true;
  } catch (error) {
    notify(error?.message || '备份格式不兼容。');
    return false;
  }
}

export function checkpointAndPersist() {
  checkpointFocus();
  clearTimeout(draftTimer);
  persist();
  account?.flush({ keepalive: true });
}

export async function init() {
  try {
    app.content = await loadContent();
  } catch (error) {
    app.error = error?.message || '学习内容没有加载出来。';
    return;
  }

  let initial = createState();
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (raw) initial = parseBackup(raw);
  } catch {
    app.storageError = '无法读取本地进度，请检查浏览器存储设置。';
    try { recoveryRaw = localStorage.getItem(STORAGE_KEY); } catch {}
  }
  app.state = initial;
  syncFocusTarget();

  account = createAccount({
    getState: () => app.state,
    applyRemote,
    onChange: syncView,
    notify,
    baseUrl: apiBase,
    request: globalThis.DAILY_ENGLISH_REQUEST,
  });

  syncView();
  initSession('new');
  app.ready = true;

  window.addEventListener('pagehide', checkpointAndPersist);
  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'hidden') checkpointAndPersist();
  });

  account.open().catch(() => {}).finally(() => {
    app.auth.checked = true;
    syncView();
    initDaily();
  });
}

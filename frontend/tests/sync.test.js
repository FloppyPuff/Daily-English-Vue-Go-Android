import { test } from 'vitest';
import assert from 'node:assert/strict';
import { createState, mergeStates, rateWord, recordSentence } from '../src/lib/core.js';
import { createAccount, syncLabel } from '../src/lib/sync.js';

const T = 1700000000000;
const EMAIL = 'learner@example.com';
const memberMe = { 'GET /api/me': () => ({ status: 200, data: { user: { email: EMAIL } } }) };
const stateOnServer = (revision, payload) => ({ 'GET /api/state': () => ({ status: 200, data: { revision, payload } }) });

function timers() {
  const queue = [];
  return {
    set(fn) { queue.push({ fn }); return queue.length; },
    clear(handle) { if (handle) queue[handle - 1] = { fn: null }; },
    fire() { for (const item of queue.splice(0)) item.fn?.(); },
    get pending() { return queue.filter(item => item.fn).length; },
  };
}

function harness(routes, initial = createState()) {
  const calls = [];
  const applied = [];
  const notices = [];
  const clock = timers();
  let local = initial;
  const account = createAccount({
    request: async (path, { method = 'GET', body, keepalive = false } = {}) => {
      calls.push({ path, method, body, keepalive });
      const route = routes[`${method} ${path}`];
      if (!route) throw new Error(`界面请求了未预期的 ${method} ${path}`);
      return route({ body, calls });
    },
    getState: () => local,
    applyRemote: payload => { local = payload; applied.push(payload); },
    notify: message => notices.push(message),
    setTimer: clock.set,
    clearTimer: clock.clear,
  });
  return { account, calls, applied, notices, clock, local: () => local, setLocal: value => { local = value; } };
}

const saves = start => ({ 'PUT /api/state': ({ body }) => ({ status: 200, data: { revision: body.revision + start } }) });

// Catches an anonymous visitor being blocked by a server they never signed up for.
test('without an account the site stays local and never uploads', async () => {
  const h = harness({ 'GET /api/me': () => ({ status: 401, data: { error: '请先登录' } }) });
  await h.account.open();
  assert.equal(h.account.mode, 'anonymous');
  h.account.schedule();
  h.clock.fire();
  await h.account.flush();
  assert.deepEqual(h.calls.map(c => `${c.method} ${c.path}`), ['GET /api/me']);
  assert.equal(syncLabel(h.account), '未登录 · 进度在本机');
});

// Catches a returning learner being answered with an empty plan.
test('an account with history downloads it instead of overwriting it', async () => {
  const remote = rateWord(createState(), 'sustain', 'good', T);
  const h = harness({ ...memberMe, ...stateOnServer(4, remote) });
  await h.account.open();
  assert.deepEqual(h.applied[0], remote);
  assert.equal(h.account.revision, 4);
  assert.equal(h.account.status, 'idle');
  assert.deepEqual(h.calls.map(c => c.method), ['GET', 'GET']);
  assert.equal(syncLabel(h.account), '已同步');
});

// Catches demo-era progress being thrown away at the first sign-up.
test('a new account uploads the progress made on this device', async () => {
  const local = rateWord(createState(), 'sustain', 'again', T);
  const h = harness({ ...memberMe, ...stateOnServer(0, createState()), 'PUT /api/state': () => ({ status: 200, data: { revision: 1 } }) }, local);
  await h.account.open();
  const upload = h.calls.find(c => c.method === 'PUT');
  assert.equal(upload.body.revision, 0);
  assert.deepEqual(upload.body.payload.records, local.records);
  assert.equal(h.account.revision, 1);
  assert.equal(h.applied.length, 0);
});

// Catches a rejected sign-in looking like a success.
test('signing in reports the server’s reason and stays local until it works', async () => {
  const h = harness({ 'POST /api/login': () => ({ status: 401, data: { error: '邮箱或密码不正确' } }) });
  await assert.rejects(() => h.account.login(EMAIL, 'hunter22'), { name: 'AccountError', message: '邮箱或密码不正确', status: 401 });
  assert.equal(h.account.mode, 'anonymous');
  assert.equal(h.calls.length, 1);
});

test('a successful sign-in reconciles with the account’s own data', async () => {
  const remote = recordSentence(createState(), 's001', '已完成的译文', 'mastered');
  const h = harness({ 'POST /api/login': () => ({ status: 200, data: { user: { email: EMAIL } } }), ...stateOnServer(2, remote) });
  assert.equal(await h.account.login(EMAIL, 'hunter22'), EMAIL);
  assert.equal(h.account.mode, 'member');
  assert.deepEqual(h.applied[0], remote);
});

// Catches every keystroke becoming a request.
test('study actions are batched into one save', async () => {
  const h = harness({ ...memberMe, ...stateOnServer(1, createState()), ...saves(1) });
  await h.account.open();
  h.account.schedule();
  h.account.schedule();
  h.account.schedule();
  assert.equal(h.clock.pending, 1);
  h.clock.fire();
  await h.account.wait();
  assert.deepEqual(h.calls.filter(c => c.method === 'PUT').map(c => c.body.revision), [1]);
  assert.equal(h.account.revision, 2);
  assert.equal(h.account.status, 'idle');
});

// Catches a second device’s work being erased by this one.
test('a concurrent device is merged, not overwritten', async () => {
  const base = rateWord(createState(), 'sustain', 'good', T);
  const mine = rateWord(base, 'climate', 'hard', T + 1000);
  const theirs = rateWord(base, 'hot', 'easy', T + 2000);
  let conflict = true;
  const h = harness({
    'PUT /api/state': () => conflict
      ? (conflict = false, { status: 409, data: { revision: 5, payload: theirs } })
      : { status: 200, data: { revision: 6 } },
  }, mine);
  h.account.mode = 'member';
  h.account.revision = 4;
  h.account.schedule();
  h.clock.fire();
  await h.account.wait();
  assert.deepEqual(h.applied[0], mergeStates(mine, theirs));
  assert.deepEqual(Object.keys(h.local().records).sort(), ['climate', 'hot', 'sustain']);
  assert.deepEqual(h.calls.map(c => c.body.revision), [4, 5]);
  assert.equal(h.account.revision, 6);
  assert.equal(h.account.status, 'idle');
});

// Catches an endless retry loop against a cookie the server already refused.
test('an expired session stops syncing and says so once', async () => {
  const h = harness({ 'PUT /api/state': () => ({ status: 401, data: { error: '请先登录' } }) });
  h.account.mode = 'member';
  h.account.schedule();
  h.clock.fire();
  await h.account.wait();
  h.account.schedule();
  await h.account.wait();
  assert.equal(h.calls.filter(c => c.method === 'PUT').length, 1);
  assert.equal(h.account.mode, 'anonymous');
  assert.equal(h.notices.length, 1);
  assert.match(h.notices[0], /重新登录/);
});

// Catches a dropped connection silently discarding the pending practice.
test('the network coming back resumes the pending save', async () => {
  let down = true;
  const h = harness({ 'PUT /api/state': () => { if (down) { down = false; throw new TypeError('fetch failed'); } return { status: 200, data: { revision: 1 } }; } });
  h.account.mode = 'member';
  h.account.schedule();
  h.clock.fire();
  await h.account.wait();
  assert.equal(h.account.status, 'offline');
  assert.match(syncLabel(h.account), /离线/);
  h.clock.fire();
  await h.account.wait();
  assert.equal(h.account.status, 'idle');
  assert.equal(h.calls.filter(c => c.method === 'PUT').length, 2);
});

// Catches sign-out wiping the local plan or continuing to post.
test('signing out keeps this device’s progress and stops uploads', async () => {
  const local = rateWord(createState(), 'sustain', 'good', T);
  const h = harness({ 'POST /api/logout': () => ({ status: 204, data: null }), 'PUT /api/state': () => ({ status: 200, data: { revision: 1 } }) }, local);
  h.account.mode = 'member';
  h.account.revision = 1;
  await h.account.logout();
  h.account.schedule();
  h.clock.fire();
  await h.account.wait();
  assert.equal(h.account.mode, 'anonymous');
  assert.deepEqual(h.local(), local);
  assert.equal(h.applied.length, 0);
  assert.deepEqual(h.calls.map(c => `${c.method} ${c.path}`), ['POST /api/logout']);
});

test('a taken email and a wrong current password are both reported', async () => {
  const h = harness({ 'POST /api/register': () => ({ status: 409, data: { error: '这个邮箱已经注册过了' } }), 'POST /api/password': () => ({ status: 401, data: { error: '当前密码不正确' } }) });
  await assert.rejects(() => h.account.register(EMAIL, 'hunter22'), { message: '这个邮箱已经注册过了' });
  h.account.mode = 'member';
  await assert.rejects(() => h.account.changePassword('wrong123', 'better456'), { message: '当前密码不正确' });
  assert.deepEqual(h.notices, []);
});

// Catches a closed tab taking the last minute of study with it.
test('the closing tab gets one last, best-effort save', async () => {
  const h = harness({ 'PUT /api/state': () => ({ status: 200, data: { revision: 3 } }) });
  h.account.mode = 'member';
  h.account.revision = 2;
  h.account.schedule();
  await h.account.flush({ keepalive: true });
  assert.equal(h.clock.pending, 0);
  assert.equal(h.calls[0].keepalive, true);
  assert.equal(h.account.status, 'idle');
});

test('a sign-up request that never answers is not mistaken for success', async () => {
  const h = harness({ 'POST /api/login': () => { throw new TypeError('fetch failed'); } });
  await assert.rejects(() => h.account.login(EMAIL, 'hunter22'), { name: 'AccountError', status: 0 });
  assert.equal(h.account.mode, 'anonymous');
});

// Catches practice done during a slow save being left on the device forever.
test('changes made while a save is in flight are saved afterwards', async () => {
  let release;
  const gate = new Promise(resolve => { release = resolve; });
  let first = true;
  const h = harness({
    'PUT /api/state': async () => {
      if (first) { first = false; await gate; }
      return { status: 200, data: { revision: 1 } };
    },
  });
  h.account.mode = 'member';
  h.account.schedule();
  const pending = h.account.flush();
  h.setLocal(rateWord(createState(), 'climate', 'good', T));
  h.account.schedule();
  release();
  await pending;
  h.clock.fire();
  await h.account.wait();
  assert.equal(h.calls.filter(c => c.method === 'PUT').length, 2);
  assert.equal(h.account.status, 'idle');
});

// Catches a flaky first probe leaving a signed-in learner looking like a guest.
test('a probe that fails is retried instead of ending the sign-in', async () => {
  const remote = rateWord(createState(), 'sustain', 'easy', T);
  let down = true;
  const h = harness({
    'GET /api/me': () => { if (down) { down = false; throw new TypeError('fetch failed'); } return { status: 200, data: { user: { email: EMAIL } } }; },
    ...stateOnServer(3, remote),
  });
  await h.account.open();
  assert.equal(h.account.mode, 'anonymous');
  h.clock.fire();
  await h.account.wait();
  assert.equal(h.account.mode, 'member');
  assert.deepEqual(h.applied[0], remote);
  assert.equal(h.account.status, 'idle');
});

// Guards the fakes above against drifting away from the real protocol.
// Node’s fetch has no cookie jar, so this stands in for the browser that would
// carry the session; everything else is the account layer’s own request shape.
function jar() {
  let cookie = '';
  const apply = values => {
    const jarMap = new Map(cookie.split('; ').filter(Boolean).map(pair => [pair.split('=')[0], pair]));
    for (const entry of values) {
      const pair = entry.split(';')[0];
      if (pair.endsWith('=') || /max-age=0/i.test(entry)) jarMap.delete(pair.split('=')[0]);
      else jarMap.set(pair.split('=')[0], pair);
    }
    cookie = [...jarMap.values()].join('; ');
  };
  // sync.mjs already resolves the full url from its own baseUrl, as a browser would.
  return async (url, { method = 'GET', body } = {}) => {
    const headers = {};
    if (body !== undefined) headers['content-type'] = 'application/json';
    if (cookie) headers.cookie = cookie;
    const response = await fetch(url, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
    apply(response.headers.getSetCookie());
    const text = await response.text();
    return { status: response.status, data: text ? JSON.parse(text) : null };
  };
}

// The real HTTP protocol is covered by the Go backend's httptest suite.
// This file checks the browser account layer against an in-memory transport.

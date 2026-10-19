import { mergeStates } from './core.js';

// The account layer owns one thing: getting this device’s study state to the
// server, and the server’s copy back. Local progress is written by the app in
// the same instant it happens, so a dropped request costs nothing but patience.
const BASE = '/api';
const DEBOUNCE_MS = 1500;
const RETRY_MS = 20000;
const PROBE_RETRIES = 2;

export class AccountError extends Error {
  constructor(status, message) {
    super(message);
    this.name = 'AccountError';
    this.status = status;
  }
}

// The server answers every route with JSON, including the empty 204.
async function browserRequest(url, { method = 'GET', body, keepalive = false } = {}) {
  const response = await fetch(url, {
    method,
    credentials: 'same-origin',
    keepalive,
    headers: body === undefined ? {} : { 'content-type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await response.text();
  let data = null;
  try { data = text ? JSON.parse(text) : null; } catch { data = null; }
  return { status: response.status, data };
}

export const syncLabel = account => {
  if (account.mode !== 'member') return account.status === 'signed-out' ? '登录已过期 · 进度在本机' : '未登录 · 进度在本机';
  if (account.status === 'saving' || account.status === 'dirty') return '正在保存…';
  if (account.status === 'offline') return '离线，稍后自动重试';
  return '已同步';
};

export function createAccount({
  request = browserRequest,
  baseUrl = '',
  getState,
  applyRemote = () => {},
  onChange = () => {},
  notify = () => {},
  setTimer = setTimeout,
  clearTimer = clearTimeout,
  debounceMs = DEBOUNCE_MS,
  retryMs = RETRY_MS,
  now = Date.now,
} = {}) {
  if (typeof getState !== 'function') throw new Error('createAccount 需要 getState');
  let inflight = null;
  let debounceTimer = 0;
  let probeRetries = 0;
  // One quiet moment, however the work nests: open() calls reconcile(), which may
  // call flush(). A caller waiting for “nothing is happening” must not be told the
  // air is clear in the gap between two of those requests.
  let depth = 0;
  let idle = Promise.resolve();
  let releaseIdle = () => {};
  const begin = operation => {
    depth += 1;
    if (depth === 1) idle = new Promise(resolve => { releaseIdle = resolve; });
    return Promise.resolve(operation()).finally(() => {
      depth -= 1;
      if (depth === 0) { const release = releaseIdle; releaseIdle = () => {}; release(); }
    });
  };
  // Background attempts must never become an unhandled rejection: the learner is
  // still studying, and the state is already on this device.
  const later = (run, ms) => setTimer(() => { run().catch(() => {}); }, ms);
  const path = route => `${baseUrl}${BASE}${route}`;
  const call = (route, options) => request(path(route), options);
  const settle = status => { account.status = status; onChange(); };
  const stopDebounce = () => { clearTimer(debounceTimer); debounceTimer = 0; };

  const account = {
    mode: 'anonymous',
    email: '',
    revision: 0,
    status: 'idle',
    dirty: false,
    problem: '',
    lastSyncedAt: 0,

    // A visitor who signed in on this browser keeps the cookie, so boot only has to
    // ask who is here. An account whose server copy already has history wins: this
    // device becomes a window onto that history. A brand-new account takes the local
    // plan and uploads it, which is how demo-era progress survives sign-up.
    open() {
      return begin(async () => {
        let response;
        try {
          response = await call('/me');
        } catch {
          if (probeRetries++ < PROBE_RETRIES) later(account.open, retryMs);
          else account.problem = '暂时无法确认登录状态，进度先留在这台设备上。';
          settle('idle');
          return account;
        }
        probeRetries = PROBE_RETRIES;
        if (response.status === 200) {
          account.mode = 'member';
          account.email = response.data.user.email;
          await account.reconcile();
        } else {
          account.mode = 'anonymous';
          settle('idle');
        }
        return account;
      });
    },

    reconcile() {
      return begin(async () => {
        let response;
        try {
          response = await call('/state');
        } catch {
          settle('offline');
          later(account.reconcile, retryMs);
          return;
        }
        if (response.status !== 200) {
          if (response.status === 401) { account.mode = 'anonymous'; settle('signed-out'); }
          else settle('offline');
          return;
        }
        account.revision = response.data.revision;
        if (response.data.revision > 0) {
          applyRemote(response.data.payload);
          settle('idle');
        } else {
          account.schedule();
          await account.flush();
        }
      });
    },

    sign(route, email, password) {
      return begin(async () => {
        let response;
        try {
          response = await call(route, { method: 'POST', body: { email, password } });
        } catch {
          throw new AccountError(0, '无法连接服务器，请检查网络后重试');
        }
        if (response.status >= 400) throw new AccountError(response.status, response.data?.error || '暂时无法完成，请稍后再试');
        account.mode = 'member';
        account.email = response.data.user.email;
        account.problem = '';
        settle('idle');
        await account.reconcile();
        return account.email;
      });
    },

    register(email, password) { return account.sign('/register', email, password); },
    login(email, password) { return account.sign('/login', email, password); },

    logout() {
      return begin(async () => {
        try { await call('/logout', { method: 'POST' }); } catch {}
        // The local plan is the learner’s own copy and stays exactly where it is.
        account.mode = 'anonymous';
        account.email = '';
        account.revision = 0;
        account.dirty = false;
        account.problem = '';
        stopDebounce();
        settle('idle');
      });
    },

    changePassword(current, next) {
      return begin(async () => {
        let response;
        try {
          response = await call('/password', { method: 'POST', body: { current, next } });
        } catch {
          throw new AccountError(0, '无法连接服务器，密码未修改');
        }
        if (response.status >= 400) throw new AccountError(response.status, response.data?.error || '密码未修改');
        notify('密码已更新，其他设备需要重新登录。');
        return response.data.user.email;
      });
    },

    schedule() {
      if (account.mode !== 'member') return;
      account.dirty = true;
      if (account.status !== 'saving') settle('dirty');
      stopDebounce();
      debounceTimer = later(account.flush, debounceMs);
    },

    // keepalive is for a tab that is closing: the browser sends what it can and
    // drops the rest. localStorage already holds the same bytes, so the worst case
    // is that the save happens on the next visit instead of in this one.
    flush({ keepalive = false } = {}) {
      if (inflight) { account.dirty = true; return inflight; }
      if (account.mode !== 'member' || !account.dirty) return Promise.resolve();
      inflight = begin(() => save(keepalive)).finally(() => { inflight = null; });
      return inflight;
    },

    wait() { return depth === 0 ? Promise.resolve() : idle.then(() => account.wait()); },
  };

  async function save(keepalive) {
    stopDebounce();
    // Two rounds cover a genuine race with another device. A third conflict means
    // the account is being edited faster than this tab can catch up, so back off
    // rather than spend the learner’s data connection on it.
    for (let round = 0; round < 2; round += 1) {
      const payload = getState();
      account.dirty = false;
      settle('saving');
      let response;
      try {
        response = await call('/state', { method: 'PUT', body: { revision: account.revision, payload }, keepalive });
      } catch {
        return offline('请求没有送达服务器');
      }
      if (response.status === 200) {
        account.revision = response.data.revision;
        account.lastSyncedAt = now();
        account.problem = '';
        settle('idle');
        return account.dirty ? save(keepalive) : undefined;
      }
      if (response.status === 409) {
        account.revision = response.data.revision;
        applyRemote(mergeStates(payload, response.data.payload));
        continue;
      }
      if (response.status === 401) {
        account.mode = 'anonymous';
        account.dirty = false;
        stopDebounce();
        settle('signed-out');
        notify('登录状态已过期，请重新登录。这台设备上的进度仍然保留。');
        return;
      }
      return offline(response.data?.error || '服务器没有接受这次保存');
    }
    return offline('另一台设备正在同时学习，稍后会自动重试');
  }

  function offline(problem) {
    account.dirty = true;
    account.problem = problem;
    settle('offline');
    stopDebounce();
    debounceTimer = later(account.flush, retryMs);
  }

  return account;
}

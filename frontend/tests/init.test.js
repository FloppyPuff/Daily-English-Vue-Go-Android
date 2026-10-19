// @vitest-environment jsdom
import { test, expect, beforeAll, vi } from 'vitest';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { app, init, plan } from '../src/stores/app.js';

const load = name => JSON.parse(readFileSync(join(process.cwd(), '..', 'content', name), 'utf8'));
const wordDoc = load('words.json');
const sentenceDoc = load('sentences.json');
const paperIndex = load('papers/index.json');

function response(body, status = 200) {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: async () => body,
    text: async () => JSON.stringify(body),
  };
}

beforeAll(() => {
  localStorage.clear();
  sessionStorage.clear();
  globalThis.fetch = vi.fn(async url => {
    if (url === '/api/me') return response({ error: '未登录' }, 401);
    if (url === 'data/words.json') return response(wordDoc);
    if (url === 'data/sentences.json') return response(sentenceDoc);
    if (url === 'data/papers/index.json') return response(paperIndex);
    return response({ error: 'not found' }, 404);
  });
});

test('boot loads the real content, local state and the account gate', async () => {
  await init();
  await new Promise(resolve => setTimeout(resolve, 0));
  expect(app.content.words.length).toBe(7291);
  expect(app.content.sentences.length).toBe(253);
  expect(app.ready).toBe(true);
  expect(app.auth.checked).toBe(true);
  expect(app.sync.mode).toBe('anonymous');
  expect(plan.value.newWords.length).toBe(20);
  expect(plan.value.sentences.length).toBe(3);
});

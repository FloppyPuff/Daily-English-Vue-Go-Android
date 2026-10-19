// @vitest-environment jsdom
import { test, expect, beforeAll } from 'vitest';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { mount, flushPromises } from '@vue/test-utils';
import App from '../src/App.vue';
import { router } from '../src/router.js';
import { app } from '../src/stores/app.js';
import { createState } from '../src/lib/core.js';

const load = name => JSON.parse(readFileSync(join(process.cwd(), '..', 'content', name), 'utf8'));

beforeAll(async () => {
  const wordDoc = load('words.json');
  const sentenceDoc = load('sentences.json');
  const papers = load('papers/index.json');
  const paper2024 = load('papers/2024-1.json');
  const words = wordDoc.words;
  const sentences = sentenceDoc.items;
  const wordById = new Map(words.map(word => [word.id, word]));
  const sentenceById = new Map(sentences.map(sentence => [sentence.id, sentence]));
  app.content = {
    words,
    sentences,
    counts: wordDoc.counts,
    sentenceCounts: sentenceDoc.counts,
    paperSets: papers.sets,
    wordById: id => wordById.get(id),
    sentenceById: id => sentenceById.get(id),
    paper: async id => (id === '2024-1' ? paper2024 : { id, sections: [] }),
  };
  app.state = createState();
  app.ready = true;
  app.error = '';
  app.auth.checked = true;
  app.auth.guest = true;
  app.sync.mode = 'anonymous';
  window.scrollTo = () => {};
  window.location.hash = '#/';
  router.push('/');
  await router.isReady();
});

test('the Vue shell renders today, words, stats and settings', async () => {
  const wrapper = mount(App, { global: { plugins: [router] } });
  await flushPromises();
  expect(wrapper.text()).toContain('今日');
  expect(wrapper.find('.word-card').exists()).toBe(true);

  await router.push('/words');
  await flushPromises();
  expect(wrapper.text()).toContain('词汇');

  await router.push('/sentences');
  await flushPromises();
  expect(wrapper.text()).toContain('句子');
  expect(wrapper.find('.sentence-card').exists()).toBe(true);

  await router.push('/review');
  await flushPromises();
  expect(wrapper.text()).toContain('复习');

  await router.push('/notebook');
  await flushPromises();
  expect(wrapper.text()).toContain('笔记');

  await router.push('/papers');
  await flushPromises();
  expect(wrapper.text()).toContain('真题');
  expect(wrapper.text()).toContain('2024 年 英语一');

  await router.push('/papers/2024-1');
  await flushPromises();
  expect(wrapper.text()).toContain('2024 年 英语一');

  await router.push('/papers/2024-1/0-0');
  await flushPromises();
  expect(wrapper.find('.exercise').exists()).toBe(true);

  await router.push('/stats');
  await flushPromises();
  expect(wrapper.text()).toContain('统计');
  expect(wrapper.text()).toContain('综合词库');

  await router.push('/settings');
  await flushPromises();
  expect(wrapper.text()).toContain('设置');
  wrapper.unmount();
});

test('the account gate appears when no member or guest session exists', async () => {
  app.auth.checked = true;
  app.auth.guest = false;
  app.auth.mode = 'login';
  app.sync.mode = 'anonymous';
  window.location.hash = '#/';
  await router.push('/');
  const wrapper = mount(App, { global: { plugins: [router] } });
  await flushPromises();
  expect(wrapper.text()).toContain('继续学习');
  expect(wrapper.text()).toContain('以游客身份使用');
  wrapper.unmount();
  app.auth.guest = true;
});

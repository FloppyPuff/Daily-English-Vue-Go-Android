import { createApp } from 'vue';
import App from './App.vue';
import { router, normalizeLegacyHash } from './router.js';
import { init } from './stores/app.js';
import { startAutoTheme } from './theme.js';
import './styles/tokens.css';
import './styles/base.css';

const titles = {
  today: '今日',
  words: '词汇',
  sentences: '句子',
  papers: '真题',
  'paper-set': '真题',
  'paper-group': '真题',
  review: '复习',
  notebook: '笔记',
  stats: '统计',
  settings: '设置',
};

router.afterEach(to => {
  document.title = `${titles[to.name] || 'Daily English'} · Daily English`;
});

startAutoTheme();
normalizeLegacyHash();
await init();

createApp(App).use(router).mount('#app');

if (import.meta.env.PROD && !globalThis.DAILY_ENGLISH_NATIVE && 'serviceWorker' in navigator) {
  import('virtual:pwa-register')
    .then(({ registerSW }) => registerSW({ immediate: true }))
    .catch(() => {});
}

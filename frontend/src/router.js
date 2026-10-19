import { createRouter, createWebHashHistory } from 'vue-router';

export const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: '/', name: 'today', component: () => import('./views/TodayView.vue') },
    { path: '/words', name: 'words', component: () => import('./views/WordsView.vue') },
    { path: '/sentences', name: 'sentences', component: () => import('./views/SentencesView.vue') },
    { path: '/papers', name: 'papers', component: () => import('./views/PapersView.vue') },
    { path: '/papers/:setId', name: 'paper-set', component: () => import('./views/PaperSetView.vue') },
    { path: '/papers/:setId/:groupIndex', name: 'paper-group', component: () => import('./views/PaperGroupView.vue') },
    { path: '/review', name: 'review', component: () => import('./views/ReviewView.vue') },
    { path: '/notebook', name: 'notebook', component: () => import('./views/NotebookView.vue') },
    { path: '/stats', name: 'stats', component: () => import('./views/StatsView.vue') },
    { path: '/settings', name: 'settings', component: () => import('./views/SettingsView.vue') },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
  scrollBehavior() {
    return { top: 0 };
  },
});

const legacy = {
  '#home': '#/',
  '#words': '#/words',
  '#sentences': '#/sentences',
  '#papers': '#/papers',
  '#review': '#/review',
  '#notebook': '#/notebook',
  '#stats': '#/stats',
};

export function normalizeLegacyHash() {
  const mapped = legacy[window.location.hash];
  if (mapped) history.replaceState(null, '', `${window.location.pathname}${window.location.search}${mapped}`);
}

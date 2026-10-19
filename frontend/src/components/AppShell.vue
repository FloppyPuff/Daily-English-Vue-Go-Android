<script setup>
import { computed } from 'vue';
import { useRoute } from 'vue-router';
import { app, syncNow } from '../stores/app.js';
import Icon from './Icon.vue';
import FocusDialog from './FocusDialog.vue';

const route = useRoute();

const nav = [
  { to: '/', label: '今日', icon: 'home', match: 'today' },
  { to: '/words', label: '词汇', icon: 'cards', match: 'words' },
  { to: '/sentences', label: '句子', icon: 'translate', match: 'sentences' },
  { to: '/papers', label: '真题', icon: 'target', match: 'papers' },
  { to: '/review', label: '复习', icon: 'repeat', match: 'review' },
  { to: '/notebook', label: '笔记', icon: 'bookmark', match: 'notebook' },
  { to: '/stats', label: '统计', icon: 'chart', match: 'stats' },
];

const bottomNav = [
  { to: '/', label: '今日', icon: 'home', match: 'today' },
  { to: '/words', label: '词汇', icon: 'cards', match: 'words' },
  { to: '/sentences', label: '句子', icon: 'translate', match: 'sentences' },
];

const pageLabel = computed(() => {
  const found = [...nav, { to: '/settings', label: '设置', match: 'settings' }].find(item => item.match === route.name || (route.name === 'paper-set' || route.name === 'paper-group') && item.match === 'papers');
  return found?.label || '今日';
});

const focusLabel = computed(() => {
  if (!app.focus.running) return '专注';
  const seconds = app.focus.remainingSeconds;
  return `${String(Math.floor(seconds / 60)).padStart(2, '0')}:${String(seconds % 60).padStart(2, '0')}`;
});

function closeMore() {
  app.ui.moreOpen = false;
}

function skip() {
  document.querySelector('#main')?.focus();
}
</script>

<template>
  <div class="shell">
    <a class="skip" href="#main" @click.prevent="skip">跳到内容</a>

    <aside class="rail">
      <RouterLink to="/" class="brand" aria-label="Daily English 首页">
        <span class="brand-mark">D<span>.</span></span>
        <span class="brand-copy">Daily<br />English</span>
      </RouterLink>

      <nav class="rail-nav" aria-label="主导航">
        <RouterLink
          v-for="item in nav"
          :key="item.to"
          :to="item.to"
          class="rail-link"
          :class="{ on: route.name === item.match || ((route.name === 'paper-set' || route.name === 'paper-group') && item.match === 'papers') }"
        >
          <Icon :name="item.icon" :size="17" />
          <span>{{ item.label }}</span>
        </RouterLink>
      </nav>

      <div class="rail-foot">
        <button class="sync-line" type="button" :class="app.sync.tone" @click="app.sync.mode === 'member' ? syncNow() : $router.push('/settings')">
          <i aria-hidden="true" />
          <span>{{ app.sync.mode === 'member' ? app.sync.email || app.sync.label : '登录以同步' }}</span>
          <Icon v-if="app.sync.mode === 'member'" name="repeat" :size="13" />
        </button>
        <RouterLink to="/settings" class="rail-settings" aria-label="设置">
          <Icon name="settings" :size="17" /> 设置
        </RouterLink>
      </div>
    </aside>

    <div class="main">
      <header class="topbar">
        <RouterLink to="/" class="mobile-brand" aria-label="Daily English 首页">
          <span class="brand-mark small">D<span>.</span></span>
        </RouterLink>
        <span class="topbar-title">{{ pageLabel }}</span>
        <div class="topbar-actions">
          <button class="focus-pill" type="button" :class="{ on: app.focus.running }" @click="app.ui.focusOpen = true">
            <Icon name="clock" :size="15" />
            <span class="num">{{ focusLabel }}</span>
          </button>
          <RouterLink to="/settings" class="icon-btn" aria-label="设置"><Icon name="settings" /></RouterLink>
        </div>
      </header>

      <main id="main" class="main-content" tabindex="-1">
        <slot />
      </main>
    </div>

    <nav class="bottom-nav" aria-label="移动导航">
      <RouterLink v-for="item in bottomNav" :key="item.to" :to="item.to" :class="{ on: route.name === item.match }">
        <Icon :name="item.icon" :size="20" />
        <span>{{ item.label }}</span>
      </RouterLink>
      <button type="button" :class="{ on: app.ui.moreOpen || ['papers', 'paper-set', 'paper-group', 'review', 'notebook', 'stats', 'settings'].includes(route.name) }" @click="app.ui.moreOpen = true">
        <Icon name="more" :size="20" />
        <span>更多</span>
      </button>
    </nav>

    <Teleport to="body">
      <Transition name="sheet">
        <div v-if="app.ui.moreOpen" class="more-wrap" @click.self="closeMore">
          <section class="more-sheet" aria-label="更多">
            <div class="spread">
              <span class="eyebrow">MORE</span>
              <button class="icon-btn" type="button" aria-label="关闭" @click="closeMore"><Icon name="close" /></button>
            </div>
            <div class="more-links">
              <RouterLink to="/papers" @click="closeMore"><Icon name="target" /> 真题</RouterLink>
              <RouterLink to="/review" @click="closeMore"><Icon name="repeat" /> 复习</RouterLink>
              <RouterLink to="/notebook" @click="closeMore"><Icon name="bookmark" /> 笔记</RouterLink>
              <RouterLink to="/stats" @click="closeMore"><Icon name="chart" /> 统计</RouterLink>
              <RouterLink to="/settings" @click="closeMore"><Icon name="settings" /> 设置</RouterLink>
            </div>
          </section>
        </div>
      </Transition>
    </Teleport>

    <FocusDialog />
  </div>
</template>

<style scoped>
.shell { min-height: 100vh; display: grid; grid-template-columns: var(--rail) minmax(0, 1fr); }

.skip {
  position: fixed;
  top: -60px;
  left: 12px;
  z-index: 100;
  padding: 8px 12px;
  border-radius: 8px;
  background: var(--ink);
  color: var(--paper);
}
.skip:focus { top: 12px; }

.rail {
  position: sticky;
  top: 0;
  height: 100vh;
  display: flex;
  flex-direction: column;
  padding: 30px 22px 22px;
  border-right: 1px solid var(--line);
  background: color-mix(in srgb, var(--paper) 70%, var(--paper-raised));
}

.brand { display: inline-flex; align-items: center; gap: 12px; color: var(--ink); text-decoration: none; }
.brand:hover { text-decoration: none; }
.brand-mark {
  font-family: var(--font-display);
  font-size: 32px;
  line-height: 1;
  letter-spacing: -0.06em;
}
.brand-mark span { color: var(--correction); }
.brand-mark.small { font-size: 26px; }
.brand-copy {
  font-size: 11px;
  line-height: 1.25;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: var(--ink-soft);
}

.rail-nav { display: flex; flex-direction: column; gap: 2px; margin-top: 46px; }
.rail-link {
  display: flex;
  align-items: center;
  gap: 11px;
  min-height: 40px;
  padding: 0 11px;
  border-radius: 9px;
  color: var(--ink-soft);
  font-size: 13.5px;
  text-decoration: none;
  transition: background 140ms ease, color 140ms ease;
}
.rail-link:hover { background: var(--paper-sunken); color: var(--ink); text-decoration: none; }
.rail-link.on { background: var(--moss-wash); color: var(--moss-strong); }

.rail-foot { margin-top: auto; display: grid; gap: 10px; padding-top: 20px; border-top: 1px solid var(--line); }
.sync-line {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--ink-soft);
  font-size: 11.5px;
  text-align: left;
}
.sync-line span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.sync-line i { width: 6px; height: 6px; border-radius: 50%; background: var(--line-strong); flex: none; }
.sync-line.ok i { background: var(--moss); }
.sync-line.busy i { background: var(--marker); }
.sync-line.warn i { background: var(--correction); }
.rail-settings {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 6px 0;
  color: var(--ink-soft);
  font-size: 12.5px;
  text-decoration: none;
}
.rail-settings:hover { color: var(--ink); text-decoration: none; }

.main { min-width: 0; }
.topbar {
  position: sticky;
  top: 0;
  z-index: 20;
  display: flex;
  align-items: center;
  gap: 12px;
  height: 58px;
  padding: 0 var(--pad);
  border-bottom: 1px solid color-mix(in srgb, var(--line) 72%, transparent);
  background: color-mix(in srgb, var(--paper) 92%, transparent);
  backdrop-filter: blur(10px);
}
.mobile-brand { display: none; }
.topbar-title { font-size: 12px; letter-spacing: 0.14em; text-transform: uppercase; color: var(--ink-faint); }
.topbar-actions { margin-left: auto; display: flex; align-items: center; gap: 6px; }
.focus-pill {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  height: 34px;
  padding: 0 11px;
  border: 1px solid var(--line);
  border-radius: 999px;
  background: transparent;
  color: var(--ink-soft);
  font-size: 12px;
}
.focus-pill:hover { background: var(--paper-sunken); color: var(--ink); }
.focus-pill.on { border-color: color-mix(in srgb, var(--moss) 40%, var(--line)); color: var(--moss-strong); background: var(--moss-wash); }

.main-content { min-height: calc(100vh - 58px); }

.bottom-nav { display: none; }

.more-wrap {
  position: fixed;
  inset: 0;
  z-index: 50;
  display: flex;
  align-items: flex-end;
  background: color-mix(in srgb, var(--ink) 18%, transparent);
}
.more-sheet {
  width: 100%;
  padding: 20px 20px calc(22px + env(safe-area-inset-bottom));
  border-radius: 22px 22px 0 0;
  background: var(--paper-raised);
  box-shadow: var(--shadow-lift);
}
.more-links { display: grid; grid-template-columns: repeat(2, 1fr); gap: 8px; margin-top: 16px; }
.more-links a {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 48px;
  padding: 0 13px;
  border: 1px solid var(--line);
  border-radius: 11px;
  color: var(--ink);
  font-size: 13.5px;
}
.more-links a:hover { background: var(--paper-sunken); text-decoration: none; }

.sheet-enter-active, .sheet-leave-active { transition: opacity 180ms ease; }
.sheet-enter-active .more-sheet, .sheet-leave-active .more-sheet { transition: transform 220ms ease; }
.sheet-enter-from, .sheet-leave-to { opacity: 0; }
.sheet-enter-from .more-sheet, .sheet-leave-to .more-sheet { transform: translateY(18px); }

@media (max-width: 900px) {
  .shell { display: block; }
  .rail { display: none; }
  .topbar { padding-inline: 18px; }
  .mobile-brand { display: inline-flex; }
  .topbar-title { display: none; }
  .bottom-nav {
    position: fixed;
    left: 0;
    right: 0;
    bottom: 0;
    z-index: 40;
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    padding: 6px 8px calc(6px + env(safe-area-inset-bottom));
    border-top: 1px solid var(--line);
    background: color-mix(in srgb, var(--paper-raised) 96%, transparent);
    backdrop-filter: blur(12px);
  }
  .bottom-nav a, .bottom-nav button {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 3px;
    padding: 5px 0;
    border: 0;
    background: transparent;
    color: var(--ink-faint);
    font-size: 10px;
    text-decoration: none;
  }
  .bottom-nav a.on, .bottom-nav button.on { color: var(--moss-strong); }
  .bottom-nav a:hover { text-decoration: none; }
  .more-wrap { z-index: 55; }
}
</style>

<script setup>
import { app, locked, syncNow } from './stores/app.js';
import AppShell from './components/AppShell.vue';
import AuthView from './views/AuthView.vue';
import Toast from './components/Toast.vue';
import Icon from './components/Icon.vue';

function reload() {
  window.location.reload();
}
</script>

<template>
  <div v-if="app.error" class="boot-error">
    <Icon name="info" :size="24" />
    <h1>内容没有加载出来</h1>
    <p>{{ app.error }}</p>
    <button class="btn primary" type="button" @click="reload">重新加载</button>
  </div>

  <div v-else-if="!app.ready || !app.auth.checked" class="boot">
    <span class="boot-mark">D<span>.</span></span>
    <p>正在整理今天的学习内容…</p>
  </div>

  <AuthView v-else-if="locked" />

  <AppShell v-else>
    <div v-if="app.storageError || app.sync.problem" class="banner" role="alert">
      <Icon name="info" :size="16" />
      <span>{{ app.storageError || app.sync.problem }}</span>
      <button v-if="app.sync.problem" class="btn sm" type="button" @click="syncNow">重试</button>
    </div>
    <RouterView v-slot="{ Component }">
      <Transition name="page" mode="out-in">
        <component :is="Component" />
      </Transition>
    </RouterView>
  </AppShell>

  <Toast />
</template>

<style scoped>
.boot, .boot-error {
  min-height: 100vh;
  display: grid;
  place-content: center;
  justify-items: center;
  gap: 12px;
  padding: 24px;
  text-align: center;
  color: var(--ink-soft);
  font-size: 13px;
}
.boot-mark { font-family: var(--font-display); font-size: 44px; line-height: 1; color: var(--ink); letter-spacing: -0.06em; }
.boot-mark span { color: var(--correction); }
.boot-error h1 { font-family: var(--font-display); font-size: 26px; font-weight: 500; color: var(--ink); }
.boot-error p { max-width: 42ch; }
.banner {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 16px var(--pad) 0;
  padding: 10px 13px;
  border: 1px solid color-mix(in srgb, var(--correction) 28%, var(--line));
  border-radius: 10px;
  background: var(--correction-wash);
  color: var(--correction);
  font-size: 12.5px;
}
.banner span { flex: 1; }
</style>

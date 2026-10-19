<script setup>
import { computed, onBeforeUnmount, onMounted, watch } from 'vue';
import { app, startFocus, pauseFocus, resetFocus } from '../stores/app.js';
import Icon from './Icon.vue';

const clock = computed(() => {
  const seconds = app.focus.remainingSeconds;
  return `${String(Math.floor(seconds / 60)).padStart(2, '0')}:${String(Math.max(0, seconds % 60)).padStart(2, '0')}`;
});

const progress = computed(() => {
  if (!app.focus.totalSeconds) return 0;
  return Math.min(1, Math.max(0, app.focus.remainingSeconds / app.focus.totalSeconds));
});

function close() {
  app.ui.focusOpen = false;
}

function onKey(event) {
  if (event.key === 'Escape' && app.ui.focusOpen) close();
}

watch(() => app.ui.focusOpen, open => {
  document.body.style.overflow = open ? 'hidden' : '';
  if (open && !app.focus.running && app.focus.remainingSeconds === app.focus.totalSeconds) {
    const target = app.state.settings.focusMinutes * 60;
    app.focus.totalSeconds = target;
    app.focus.remainingSeconds = target;
  }
});

onMounted(() => window.addEventListener('keydown', onKey));
onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKey);
  document.body.style.overflow = '';
});
</script>

<template>
  <Teleport to="body">
    <Transition name="fade">
      <div v-if="app.ui.focusOpen" class="focus-screen" role="dialog" aria-modal="true" aria-label="专注计时" @click.self="close">
        <section class="focus-box">
          <div class="focus-head">
            <h2>专注</h2>
            <button class="icon-btn" type="button" aria-label="关闭" @click="close"><Icon name="close" /></button>
          </div>
          <p class="focus-clock num">{{ clock }}</p>
          <div class="focus-track" aria-hidden="true"><i :style="{ transform: `scaleX(${progress})` }" /></div>
          <p class="focus-note">关闭窗口后继续计时；关闭网页会结束本次计时。</p>
          <div class="focus-actions">
            <button class="btn primary grow" type="button" @click="app.focus.running ? pauseFocus() : startFocus()">
              <Icon :name="app.focus.running ? 'pause' : 'play'" :size="16" />
              {{ app.focus.running ? '暂停' : '开始专注' }}
            </button>
            <button class="btn" type="button" @click="resetFocus"><Icon name="repeat" :size="16" /> 重新计时</button>
          </div>
        </section>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.focus-screen {
  position: fixed;
  inset: 0;
  z-index: 60;
  display: grid;
  place-items: center;
  padding: 24px;
  background: color-mix(in srgb, var(--paper) 82%, transparent);
  backdrop-filter: blur(8px);
}
.focus-box {
  width: min(420px, 100%);
  padding: 24px;
  background: var(--paper-raised);
  border: 1px solid var(--line);
  border-radius: 22px;
  box-shadow: var(--shadow-lift);
}
.focus-head { display: flex; align-items: center; justify-content: space-between; }
.focus-head h2 { font-size: 13px; font-weight: 600; letter-spacing: 0.02em; }
.focus-clock {
  margin: 26px 0 18px;
  font-size: clamp(56px, 12vw, 76px);
  line-height: 1;
  letter-spacing: -0.04em;
  text-align: center;
  color: var(--ink);
}
.focus-track { height: 2px; background: var(--line); overflow: hidden; }
.focus-track i {
  display: block;
  height: 100%;
  background: var(--moss);
  transform-origin: left center;
  transition: transform 1s linear;
}
.focus-note { margin-top: 14px; color: var(--ink-faint); font-size: 12px; text-align: center; }
.focus-actions { display: flex; gap: 8px; margin-top: 22px; }
.fade-enter-active, .fade-leave-active { transition: opacity 160ms ease; }
.fade-enter-from, .fade-leave-to { opacity: 0; }
</style>

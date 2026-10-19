<script setup>
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { app, submitAuth, continueGuest } from '../stores/app.js';
import Icon from '../components/Icon.vue';

const router = useRouter();
const email = ref('');
const password = ref('');

async function submit() {
  const ok = await submitAuth(email.value.trim(), password.value);
  if (ok) router.push('/');
}

function guest() {
  continueGuest();
  router.push('/');
}
</script>

<template>
  <main class="auth">
    <div class="auth-rail" aria-hidden="true">
      <span>DAILY ENGLISH</span>
      <i />
      <span>CET-6 / 考研</span>
    </div>

    <section class="auth-card">
      <div class="auth-brand">
        <span class="brand-mark">D<span>.</span></span>
        <span class="brand-copy">Daily<br />English</span>
      </div>

      <div class="auth-copy">
        <h1>{{ app.auth.mode === 'register' ? '创建账号' : '继续学习' }}</h1>
        <p>登录后，进度会在你的设备间同步。</p>
      </div>

      <div class="seg auth-tabs" role="tablist" aria-label="登录或注册">
        <button type="button" :class="{ on: app.auth.mode === 'login' }" @click="app.auth.mode = 'login'; app.auth.error = ''">登录</button>
        <button type="button" :class="{ on: app.auth.mode === 'register' }" @click="app.auth.mode = 'register'; app.auth.error = ''">注册</button>
      </div>

      <form class="auth-form" @submit.prevent="submit">
        <label class="field">
          <span>邮箱</span>
          <input v-model="email" class="input" type="email" autocomplete="email" required placeholder="you@example.com" />
        </label>
        <label class="field">
          <span>密码</span>
          <input v-model="password" class="input" type="password" :autocomplete="app.auth.mode === 'register' ? 'new-password' : 'current-password'" required minlength="8" placeholder="至少 8 位" />
        </label>
        <p v-if="app.auth.error" class="auth-error" role="alert">{{ app.auth.error }}</p>
        <button class="btn primary wide" type="submit" :disabled="app.auth.busy">
          {{ app.auth.busy ? '请稍候…' : app.auth.mode === 'register' ? '创建账号' : '登录' }}
        </button>
      </form>

      <button class="guest" type="button" @click="guest">
        以游客身份使用
        <Icon name="arrow" :size="15" />
      </button>

      <p class="auth-note">游客进度只保存在这台设备，注册后自动上传。</p>
    </section>
  </main>
</template>

<style scoped>
.auth {
  min-height: 100vh;
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(360px, 480px);
  background: var(--paper);
}

.auth-rail {
  position: relative;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 46px 52px 40px;
  border-right: 1px solid var(--line);
  color: var(--ink-faint);
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.18em;
  background:
    linear-gradient(var(--line) 1px, transparent 1px) 0 0 / 100% 42px,
    var(--paper-sunken);
}
.auth-rail::after {
  content: "";
  position: absolute;
  inset: 0 auto 0 0;
  width: 3px;
  background: var(--correction);
  opacity: 0.55;
}
.auth-rail i { flex: 1; }

.auth-card {
  align-self: center;
  width: min(420px, calc(100% - 44px));
  margin: 0 auto;
  padding: 40px 0 60px;
}

.auth-brand { display: inline-flex; align-items: center; gap: 12px; margin-bottom: 48px; }
.brand-mark { font-family: var(--font-display); font-size: 34px; line-height: 1; letter-spacing: -0.06em; }
.brand-mark span { color: var(--correction); }
.brand-copy { color: var(--ink-soft); font-size: 10px; line-height: 1.25; letter-spacing: 0.16em; text-transform: uppercase; }

.auth-copy h1 { margin-top: 10px; font-size: 30px; font-weight: 600; letter-spacing: -0.03em; }
.auth-copy > p:last-child { margin-top: 8px; color: var(--ink-soft); font-size: 13.5px; }

.auth-tabs { margin-top: 26px; width: 100%; }
.auth-tabs button { flex: 1; }
.auth-form { display: grid; gap: 16px; margin-top: 20px; }
.auth-error { color: var(--correction); font-size: 12.5px; }

.guest {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  margin-top: 22px;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--moss-strong);
  font-size: 13px;
}
.auth-note { margin-top: 10px; color: var(--ink-faint); font-size: 11.5px; }

@media (max-width: 820px) {
  .auth { display: block; }
  .auth-rail { display: none; }
  .auth-card { padding-top: 34px; }
}
</style>

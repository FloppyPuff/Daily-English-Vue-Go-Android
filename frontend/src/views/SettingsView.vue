<script setup>
import { ref, watch } from 'vue';
import { app, setGoals, syncNow, logout, changePassword, exportBackup, importBackup, requestAuth } from '../stores/app.js';
import PageHead from '../components/PageHead.vue';
import Icon from '../components/Icon.vue';

const dailyGoal = ref(String(app.state.settings.dailyGoal));
const sentenceGoal = ref(String(app.state.settings.sentenceGoal));
const focusMinutes = ref(String(app.state.settings.focusMinutes));
const currentPassword = ref('');
const nextPassword = ref('');
const importInput = ref(null);

watch(() => app.state.settings, settings => {
  dailyGoal.value = String(settings.dailyGoal);
  sentenceGoal.value = String(settings.sentenceGoal);
  focusMinutes.value = String(settings.focusMinutes);
}, { deep: true });

function save() {
  setGoals({
    dailyGoal: Math.min(100, Math.max(1, Number(dailyGoal.value) || app.state.settings.dailyGoal)),
    sentenceGoal: Math.min(20, Math.max(1, Number(sentenceGoal.value) || app.state.settings.sentenceGoal)),
    focusMinutes: Math.min(120, Math.max(1, Number(focusMinutes.value) || app.state.settings.focusMinutes)),
  });
}

async function submitPassword() {
  const ok = await changePassword(currentPassword.value, nextPassword.value);
  if (ok) {
    currentPassword.value = '';
    nextPassword.value = '';
  }
}

async function chooseImport(event) {
  const file = event.target.files?.[0];
  if (!file) return;
  if (file.size > 5 * 1024 * 1024) {
    app.toast.message = '备份文件超过 5 MB。';
    app.toast.visible = true;
    event.target.value = '';
    return;
  }
  const text = await file.text();
  importBackup(text);
  event.target.value = '';
}
</script>

<template>
  <div class="page settings-page">
    <PageHead title="设置" />

    <section class="sheet block">
      <h2>学习计划</h2>
      <div class="goal-grid">
        <label class="field">
          <span>每日新词</span>
          <input v-model="dailyGoal" class="input" type="number" min="1" max="100" inputmode="numeric" />
          <small class="hint">1–100 词</small>
        </label>
        <label class="field">
          <span>每日翻译</span>
          <input v-model="sentenceGoal" class="input" type="number" min="1" max="20" inputmode="numeric" />
          <small class="hint">1–20 句</small>
        </label>
        <label class="field">
          <span>专注时长</span>
          <input v-model="focusMinutes" class="input" type="number" min="1" max="120" inputmode="numeric" />
          <small class="hint">1–120 分钟</small>
        </label>
      </div>
      <button class="btn primary" type="button" @click="save">保存计划</button>
    </section>

    <section class="sheet block">
      <div class="spread">
        <h2>账号</h2>
        <span v-if="app.sync.mode === 'member'" class="tag moss">已登录</span>
        <span v-else class="tag">本机</span>
      </div>

      <template v-if="app.sync.mode === 'member'">
        <p class="account-mail">{{ app.sync.email }}</p>
        <p class="muted small">{{ app.sync.label }}</p>
        <div class="row wrap">
          <button class="btn sm" type="button" @click="syncNow"><Icon name="repeat" :size="14" /> 立即同步</button>
          <button class="btn sm danger" type="button" @click="logout">退出登录</button>
        </div>
        <form class="password-form" @submit.prevent="submitPassword">
          <span class="small muted">修改密码</span>
          <div class="password-row">
            <input v-model="currentPassword" class="input" type="password" placeholder="当前密码" autocomplete="current-password" required />
            <input v-model="nextPassword" class="input" type="password" placeholder="新密码，至少 8 位" autocomplete="new-password" required minlength="8" />
            <button class="btn sm" type="submit">更新</button>
          </div>
        </form>
      </template>

      <template v-else>
        <p class="muted small">游客进度只保存在这台设备。登录后会随账号同步。</p>
        <button class="btn primary" type="button" @click="requestAuth">登录或注册</button>
      </template>
    </section>

    <section class="sheet block">
      <h2>数据</h2>
      <p class="muted small">导出备份可换设备或迁移服务器；恢复会覆盖当前进度。</p>
      <div class="row wrap">
        <button class="btn" type="button" @click="exportBackup"><Icon name="download" :size="14" /> 导出备份</button>
        <button class="btn" type="button" @click="importInput.click()"><Icon name="upload" :size="14" /> 恢复备份</button>
        <input ref="importInput" class="sr-only" type="file" accept="application/json,.json" @change="chooseImport" />
      </div>
    </section>

    <section class="about">
      <span class="num">{{ app.content?.counts?.total || 0 }} 词</span>
      <span class="num">{{ app.content?.sentenceCounts?.total || 0 }} 句</span>
      <span class="num">{{ app.content?.paperSets?.length || 0 }} 套真题</span>
    </section>
  </div>
</template>

<style scoped>
.settings-page { max-width: 720px; }
.block { padding: 20px 22px; margin-bottom: 14px; display: grid; gap: 10px; }
.block h2 { font-size: 13px; font-weight: 600; }
.goal-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px; margin: 4px 0 6px; }
.account-mail { font-family: var(--font-display); font-size: 20px; }
.row.wrap { flex-wrap: wrap; }
.password-form { margin-top: 6px; padding-top: 14px; border-top: 1px solid var(--line); display: grid; gap: 8px; }
.password-row { display: grid; grid-template-columns: 1fr 1fr auto; gap: 8px; }
.about { display: flex; gap: 18px; color: var(--ink-faint); font-size: 10.5px; padding: 4px 2px; }
@media (max-width: 620px) {
  .goal-grid { grid-template-columns: 1fr; }
  .password-row { grid-template-columns: 1fr; }
}
</style>

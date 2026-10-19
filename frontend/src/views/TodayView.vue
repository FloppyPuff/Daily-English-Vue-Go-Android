<script setup>
import { computed, onBeforeUnmount, onMounted } from 'vue';
import { app, plan, stats, currentWord, currentSentence, initDaily, revealWord, rateCurrentWord } from '../stores/app.js';
import WordCard from '../components/WordCard.vue';
import SentenceCard from '../components/SentenceCard.vue';
import PageHead from '../components/PageHead.vue';
import EmptyState from '../components/EmptyState.vue';
import Icon from '../components/Icon.vue';

const day = new Date().toLocaleDateString('zh-CN', { month: 'long', day: 'numeric', weekday: 'long' });
const stage = computed(() => app.ui.homeStage || plan.value?.active || 'done');
const step = computed(() => plan.value?.steps.find(item => item.id === stage.value) || null);
const complete = computed(() => stage.value === 'done');

function enter(id) {
  initDaily(id);
}

function onKey(event) {
  const tag = event.target?.tagName;
  if (['INPUT', 'TEXTAREA', 'SELECT', 'BUTTON'].includes(tag) || event.target?.isContentEditable) return;
  if (stage.value === 'sentences') return;
  if (event.code === 'Space') {
    event.preventDefault();
    revealWord();
  }
  const rating = { Digit1: 'again', Digit2: 'hard', Digit3: 'good', Digit4: 'easy' }[event.code];
  if (rating && app.ui.revealed) {
    event.preventDefault();
    rateCurrentWord(rating);
  }
}

onMounted(() => {
  initDaily();
  window.addEventListener('keydown', onKey);
});
onBeforeUnmount(() => window.removeEventListener('keydown', onKey));
</script>

<template>
  <div class="page">
    <PageHead title="今日" :lede="`${day} · CET-6 + 考研`" />

    <ol v-if="plan" class="steps" aria-label="今日计划">
      <li
        v-for="(item, index) in plan.steps"
        :key="item.id"
        :class="{ on: stage === item.id, done: item.pending === 0 && item.total > 0 }"
      >
        <button type="button" @click="enter(item.id)">
          <span class="step-index num">{{ String(index + 1).padStart(2, '0') }}</span>
          <span class="step-copy">
            <strong>{{ item.label }}</strong>
            <small>{{ item.total ? `${item.done} / ${item.total} ${item.unit}` : item.id === 'review' ? '暂无到期' : '暂无内容' }}</small>
          </span>
          <Icon v-if="item.pending === 0 && item.total > 0" name="check" :size="15" class="tick" />
          <span v-else class="step-pending num">{{ item.pending }}</span>
        </button>
      </li>
    </ol>

    <section v-if="complete || (stage !== 'sentences' && !currentWord)" class="finish sheet">
      <h2>{{ complete ? '今天的计划完成了。' : '这一组完成了。' }}</h2>
      <p class="muted">
        {{ complete ? `已学 ${stats?.learned ?? 0} 词 · 今日专注 ${stats?.todayMinutes ?? 0} 分钟` : '继续下一步，或换一组自由练习。' }}
      </p>
      <div class="finish-actions">
        <button v-if="!complete" class="btn primary" type="button" @click="initDaily()">继续下一步 <Icon name="arrow" :size="15" /></button>
        <RouterLink v-else to="/stats" class="btn primary">查看统计 <Icon name="arrow" :size="15" /></RouterLink>
        <RouterLink to="/words" class="btn ghost">自由练习</RouterLink>
      </div>
    </section>

    <SentenceCard
      v-else-if="stage === 'sentences' && currentSentence"
      :sentence="currentSentence"
      guided
      :recorded="app.ui.homeSentenceRecorded"
      @continue="initDaily()"
    />

    <WordCard
      v-else-if="currentWord"
      :word="currentWord"
      :index="app.ui.wordIndex"
      :total="app.ui.wordQueue.length"
    />

    <EmptyState v-else title="今天没有待学内容" detail="可以去自由练习里继续。" />

    <footer class="today-foot">
      <span>连续 <b class="num">{{ stats?.streak ?? 0 }}</b> 天</span>
      <span>已学 <b class="num">{{ stats?.learned ?? 0 }}</b> 词</span>
      <span>专注 <b class="num">{{ stats?.todayMinutes ?? 0 }}</b> 分钟</span>
    </footer>
  </div>
</template>

<style scoped>
.steps {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 0;
  margin: 0 0 22px;
  padding: 0;
  list-style: none;
  border-top: 1px solid var(--line);
  border-bottom: 1px solid var(--line);
}
.steps li { border-right: 1px solid var(--line); }
.steps li:last-child { border-right: 0; }
.steps button {
  display: flex;
  align-items: center;
  gap: 11px;
  width: 100%;
  min-height: 74px;
  padding: 12px 14px;
  border: 0;
  background: transparent;
  text-align: left;
}
.steps li.on { background: var(--marker-wash); }
.step-index { color: var(--ink-faint); font-size: 10.5px; }
.step-copy { display: grid; gap: 1px; min-width: 0; }
.step-copy strong { font-size: 13px; font-weight: 500; }
.step-copy small { color: var(--ink-faint); font-size: 11px; }
.step-pending { margin-left: auto; color: var(--ink-soft); font-size: 12px; }
.step-pending::after { content: " 待"; font-family: var(--font-body); font-size: 10px; color: var(--ink-faint); }
.tick { margin-left: auto; color: var(--moss-strong); }
.steps li.done .step-index { color: var(--moss-strong); }

.finish { padding: clamp(26px, 5vw, 42px); }
.finish h2 { font-size: clamp(24px, 4vw, 30px); font-weight: 600; letter-spacing: -0.03em; }
.finish p.muted { margin-top: 8px; font-size: 13.5px; }
.finish-actions { display: flex; gap: 8px; margin-top: 24px; flex-wrap: wrap; }

.today-foot {
  display: flex;
  gap: 22px;
  margin-top: 20px;
  color: var(--ink-faint);
  font-size: 11.5px;
}
.today-foot b { color: var(--ink-soft); font-weight: 500; }

@media (max-width: 700px) {
  .steps { grid-template-columns: 1fr; }
  .steps li { border-right: 0; border-bottom: 1px solid var(--line); }
  .steps li:last-child { border-bottom: 0; }
  .steps button { min-height: 58px; }
  .today-foot { justify-content: space-between; gap: 8px; }
}
</style>

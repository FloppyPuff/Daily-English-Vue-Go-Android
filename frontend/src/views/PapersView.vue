<script setup>
import { computed } from 'vue';
import { useRouter } from 'vue-router';
import { app, setPaperFilter } from '../stores/app.js';
import PageHead from '../components/PageHead.vue';
import Icon from '../components/Icon.vue';

const router = useRouter();
const sets = computed(() => app.content?.paperSets || []);
const exams = computed(() => ['all', ...new Set(sets.value.map(set => set.exam))]);
const list = computed(() => sets.value.filter(set => app.ui.paperFilter === 'all' || set.exam === app.ui.paperFilter));

function doneCount(set) {
  return Object.entries(app.state.papers)
    .filter(([key, record]) => key.startsWith(`${set.id}#`) && (record.total > 0 || record.rating))
    .length;
}

function open(set) {
  router.push(`/papers/${set.id}`);
}
</script>

<template>
  <div class="page">
    <PageHead title="真题" :lede="`${sets.length} 套考研英语真题`">
      <select class="select filter" :value="app.ui.paperFilter" aria-label="筛选试卷" @change="setPaperFilter($event.target.value)">
        <option v-for="exam in exams" :key="exam" :value="exam">{{ exam === 'all' ? '全部试卷' : exam }}</option>
      </select>
    </PageHead>

    <section class="sheet catalog">
      <button v-for="set in list" :key="set.id" class="paper-row" type="button" @click="open(set)">
        <span class="year num">{{ set.year }}</span>
        <span class="paper-main">
          <strong>{{ set.year }} 年 {{ set.exam }}</strong>
          <small>{{ set.questions ? `${set.questions} 题` : '' }}{{ set.counts?.cloze ? ` · 完形 ${set.counts.cloze}` : '' }}{{ set.counts?.reading ? ` · 阅读 ${set.counts.reading}` : '' }}</small>
        </span>
        <span class="state">{{ doneCount(set) ? `已练 ${doneCount(set)} 组` : '未开始' }}</span>
        <Icon name="chevron" :size="15" />
      </button>
      <p v-if="!list.length" class="empty-note">这个筛选下没有试卷。</p>
    </section>

    <p class="source-note">来源：公开整理的考研英语真题；答案以原卷为准。</p>
  </div>
</template>

<style scoped>
.filter { width: 132px; }
.catalog { padding: 4px 18px; }
.paper-row {
  display: flex;
  align-items: center;
  gap: 14px;
  width: 100%;
  padding: 14px 4px;
  border: 0;
  border-bottom: 1px solid var(--line);
  background: transparent;
  text-align: left;
  color: var(--ink);
}
.paper-row:last-of-type { border-bottom: 0; }
.paper-row:hover .paper-main strong { color: var(--moss-strong); }
.year { width: 46px; color: var(--ink-faint); font-size: 18px; }
.paper-main { flex: 1; display: grid; gap: 1px; min-width: 0; }
.paper-main strong { font-size: 14px; font-weight: 500; }
.paper-main small { color: var(--ink-faint); font-size: 11px; }
.state { color: var(--ink-faint); font-size: 11px; white-space: nowrap; }
.source-note { margin-top: 16px; color: var(--ink-faint); font-size: 11px; }
.empty-note { padding: 24px 4px; color: var(--ink-faint); font-size: 12.5px; }
@media (max-width: 520px) {
  .state { display: none; }
}
</style>

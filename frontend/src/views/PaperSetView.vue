<script setup>
import { computed, onMounted, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { app, openPaper } from '../stores/app.js';
import { paperGroupLabel, paperGroups } from '../lib/content.js';
import PageHead from '../components/PageHead.vue';
import EmptyState from '../components/EmptyState.vue';
import Icon from '../components/Icon.vue';

const route = useRoute();
const router = useRouter();
const setId = computed(() => String(route.params.setId || ''));
const paper = computed(() => app.ui.paperSet === setId.value ? app.ui.paper : null);
const groups = computed(() => paper.value ? paperGroups(paper.value) : []);

function status(group) {
  const record = app.state.papers[group.key];
  if (!record) return '未开始';
  if (record.total) return `已批改 ${record.correct} / ${record.total}`;
  if (record.rating) return record.rating === 'mastered' ? '已理解' : '待巩固';
  return '草稿';
}

async function load() {
  if (!setId.value) return;
  if (app.ui.paperSet === setId.value && app.ui.paper) return;
  await openPaper(setId.value);
}

function openGroup(group) {
  router.push(`/papers/${setId.value}/${group.index}`);
}

onMounted(load);
watch(setId, load);
</script>

<template>
  <div class="page">
    <div class="back-row">
      <RouterLink to="/papers" class="btn ghost sm"><Icon name="arrow" :size="14" style="transform: rotate(180deg)" /> 全部真题</RouterLink>
    </div>

    <template v-if="paper">
      <PageHead
        :title="`${paper.year} 年 ${paper.exam}`"
        :lede="`${groups.length} 组练习`"
      />

      <section class="sheet group-list">
        <button v-for="group in groups" :key="group.index" class="group-row" type="button" @click="openGroup(group)">
          <span class="kind">{{ paperGroupLabel(group) }}</span>
          <span class="group-main">
            <strong>{{ group.section || paperGroupLabel(group) }}</strong>
            <small>{{ group.questions.length }} 题{{ group.sectionScore ? ` · ${group.sectionScore} 分` : '' }}{{ group.answerable ? '' : ' · 仅原文' }}</small>
          </span>
          <span class="state">{{ status(group) }}</span>
          <Icon name="chevron" :size="15" />
        </button>
      </section>
    </template>

    <div v-else-if="app.ui.paperLoading" class="loading">正在取出试卷…</div>
    <EmptyState v-else title="这套试卷暂时打不开" detail="检查网络后重试。">
      <button class="btn" type="button" @click="load">重试</button>
    </EmptyState>
  </div>
</template>

<style scoped>
.back-row { margin-bottom: 14px; }
.group-list { padding: 4px 18px; }
.group-row {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  padding: 13px 4px;
  border: 0;
  border-bottom: 1px solid var(--line);
  background: transparent;
  color: var(--ink);
  text-align: left;
}
.group-row:last-child { border-bottom: 0; }
.group-row:hover .group-main strong { color: var(--moss-strong); }
.kind { width: 86px; flex: none; color: var(--ink-faint); font-size: 10.5px; }
.group-main { flex: 1; display: grid; gap: 1px; min-width: 0; }
.group-main strong { font-size: 13.5px; font-weight: 500; }
.group-main small { color: var(--ink-faint); font-size: 11px; }
.state { color: var(--ink-faint); font-size: 10.5px; white-space: nowrap; }
.loading { padding: 40px 0; color: var(--ink-faint); font-size: 13px; }
@media (max-width: 620px) {
  .kind { display: none; }
  .state { display: none; }
}
</style>

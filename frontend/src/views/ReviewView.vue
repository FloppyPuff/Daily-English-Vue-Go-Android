<script setup>
import { computed } from 'vue';
import { useRouter } from 'vue-router';
import { app, dueWords, initSession, openWord, toggleSavedWord, speakWord } from '../stores/app.js';
import PageHead from '../components/PageHead.vue';
import WordRow from '../components/WordRow.vue';
import EmptyState from '../components/EmptyState.vue';
import Icon from '../components/Icon.vue';

const router = useRouter();
const upcoming = computed(() => {
  const now = Date.now();
  return (app.content?.words || [])
    .filter(word => app.state.records[word.id]?.due > now)
    .sort((a, b) => app.state.records[a.id].due - app.state.records[b.id].due)
    .slice(0, 8);
});

function startReview() {
  initSession('review');
  router.push('/words');
}

function open(id) {
  openWord(id);
  router.push('/words');
}
</script>

<template>
  <div class="page">
    <PageHead title="复习" :lede="`${dueWords.length} 词到期`">
      <button v-if="dueWords.length" class="btn primary sm" type="button" @click="startReview">开始复习 <Icon name="arrow" :size="14" /></button>
    </PageHead>

    <section v-if="dueWords.length" class="sheet list">
      <WordRow
        v-for="word in dueWords.slice(0, 60)"
        :key="word.id"
        :word="word"
        status="到期"
        :saved="app.state.savedWords.includes(word.id)"
        @open="open"
        @speak="id => speakWord(app.content.wordById(id))"
        @toggle-save="toggleSavedWord"
      />
      <p v-if="dueWords.length > 60" class="more">显示前 60 词，开始复习会安排全部。</p>
    </section>

    <EmptyState v-else title="暂时没有到期单词" detail="新词会在到期后自动出现。">
      <button class="btn" type="button" @click="initSession('new'); router.push('/words')">学习新词</button>
    </EmptyState>

    <details v-if="upcoming.length" class="upcoming sheet">
      <summary>之后的复习 <span class="num">{{ upcoming.length }}</span></summary>
      <div v-for="word in upcoming" :key="word.id" class="upcoming-row">
        <strong>{{ word.id }}</strong>
        <span>{{ word.meaning }}</span>
        <time class="num">{{ new Date(app.state.records[word.id].due).toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' }) }}</time>
      </div>
    </details>
  </div>
</template>

<style scoped>
.list { padding: 8px 22px; }
.more { padding: 12px 0; color: var(--ink-faint); font-size: 11.5px; }
.upcoming { margin-top: 16px; padding: 14px 20px; }
.upcoming summary { cursor: pointer; color: var(--ink-soft); font-size: 12.5px; }
.upcoming-row { display: grid; grid-template-columns: 120px 1fr auto; gap: 12px; padding: 9px 0; border-top: 1px solid var(--line); font-size: 12px; }
.upcoming-row strong { font-family: var(--font-display); font-size: 15px; font-weight: 500; }
.upcoming-row span { color: var(--ink-soft); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.upcoming-row time { color: var(--ink-faint); }
@media (max-width: 560px) {
  .upcoming-row { grid-template-columns: 100px 1fr; }
  .upcoming-row time { display: none; }
}
</style>

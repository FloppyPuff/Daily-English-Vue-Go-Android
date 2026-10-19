<script setup>
import { computed, onBeforeUnmount, onMounted } from 'vue';
import { app, currentWord, stats, libraryList, initSession, nextWordSession, openWord, setLibraryQuery, setLibraryFilter, revealWord, rateCurrentWord, toggleSavedWord, speakWord } from '../stores/app.js';
import PageHead from '../components/PageHead.vue';
import WordCard from '../components/WordCard.vue';
import WordRow from '../components/WordRow.vue';
import EmptyState from '../components/EmptyState.vue';
import Icon from '../components/Icon.vue';

const PAGE = 60;
const mode = computed(() => {
  if (app.ui.wordMode === 'browse') return 'browse';
  if (app.ui.wordMode === 'library') return 'library';
  if (app.ui.wordMode === 'spell') return 'spell';
  if (['review', 'mistakes', 'saved'].includes(app.ui.wordMode)) return 'review';
  return 'new';
});
const rows = computed(() => {
  const start = app.ui.libraryPage * PAGE;
  return libraryList.value.slice(start, start + PAGE);
});
const pages = computed(() => Math.max(1, Math.ceil(libraryList.value.length / PAGE)));
const queueLabel = computed(() => app.ui.wordMode === 'mistakes' ? '薄弱词' : app.ui.wordMode === 'saved' ? '收藏词' : '到期复习');

function statusOf(word) {
  const record = app.state.records[word.id];
  return record?.difficult ? '待巩固' : record?.streak >= 3 ? '较熟悉' : record ? '已学习' : '未学习';
}

function chooseMode(value) {
  if (value === 'library') {
    app.ui.wordMode = 'library';
    return;
  }
  initSession(value);
}

function openFromLibrary(id) {
  openWord(id);
}

function backToLibrary() {
  app.ui.wordMode = 'library';
}

function onKey(event) {
  const tag = event.target?.tagName;
  if (['INPUT', 'TEXTAREA', 'SELECT'].includes(tag) || event.target?.isContentEditable) return;
  if (mode.value !== 'new' && mode.value !== 'review' && mode.value !== 'browse') return;
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
  if (mode.value !== 'library' && mode.value !== 'browse' && !app.ui.wordQueue.length) {
    initSession(['review', 'mistakes', 'saved'].includes(app.ui.wordMode) ? app.ui.wordMode : 'new');
  }
  window.addEventListener('keydown', onKey);
});
onBeforeUnmount(() => window.removeEventListener('keydown', onKey));
</script>

<template>
  <div class="page">
    <PageHead title="词汇" :lede="`${stats?.learned ?? 0} / ${stats?.total ?? 0} 词已学`">
      <RouterLink to="/review" class="btn sm">到期复习 <span class="num">{{ stats?.due ?? 0 }}</span></RouterLink>
    </PageHead>

    <div class="toolbar">
      <div class="seg" role="tablist" aria-label="练习方式">
        <button type="button" :class="{ on: mode === 'new' || mode === 'review' }" @click="chooseMode('new')">闪卡</button>
        <button type="button" :class="{ on: mode === 'spell' }" @click="chooseMode('spell')">拼写</button>
        <button type="button" :class="{ on: mode === 'library' }" @click="chooseMode('library')">词库</button>
      </div>
      <div class="row" v-if="mode !== 'library' && mode !== 'browse'">
        <span v-if="mode === 'review'" class="tag red">{{ queueLabel }}</span>
        <button class="btn ghost sm" type="button" @click="nextWordSession">换一组 <Icon name="repeat" :size="14" /></button>
      </div>
      <button v-else-if="mode === 'browse'" class="btn ghost sm" type="button" @click="backToLibrary">返回词库</button>
    </div>

    <template v-if="mode === 'library'">
      <section class="library sheet">
        <div class="library-tools">
          <label class="search">
            <Icon name="search" :size="16" />
            <input :value="app.ui.libraryQuery" type="search" placeholder="搜索单词、释义或主题" aria-label="搜索词库" @input="setLibraryQuery($event.target.value)" />
          </label>
          <select class="select" :value="app.ui.libraryFilter" aria-label="筛选学习状态" @change="setLibraryFilter($event.target.value)">
            <option value="all">全部</option>
            <option value="new">未学习</option>
            <option value="learned">已学习</option>
          </select>
          <span class="num count">{{ libraryList.length }} 词</span>
        </div>
        <div class="rows">
          <WordRow
            v-for="word in rows"
            :key="word.id"
            :word="word"
            :status="statusOf(word)"
            :saved="app.state.savedWords.includes(word.id)"
            @open="openFromLibrary"
            @speak="id => speakWord(app.content.wordById(id))"
            @toggle-save="toggleSavedWord"
          />
        </div>
        <div v-if="!rows.length" class="empty">
          <h3>没有找到这个词</h3>
          <p>换一个关键词或筛选条件。</p>
        </div>
        <div class="pager">
          <button class="btn ghost sm" type="button" :disabled="app.ui.libraryPage === 0" @click="app.ui.libraryPage--">上一页</button>
          <span class="num">{{ app.ui.libraryPage + 1 }} / {{ pages }}</span>
          <button class="btn ghost sm" type="button" :disabled="app.ui.libraryPage >= pages - 1" @click="app.ui.libraryPage++">下一页</button>
        </div>
      </section>
    </template>

    <template v-else-if="currentWord">
      <WordCard :word="currentWord" :index="app.ui.wordIndex" :total="app.ui.wordQueue.length" :spelling="mode === 'spell'" />
    </template>

    <EmptyState
      v-else
      :title="mode === 'review' ? '暂时没有到期单词' : '这一组学完了'"
      :detail="mode === 'review' ? '新词会在到期后自动出现。' : '换一组继续，或去词库浏览。'"
    >
      <button class="btn primary" type="button" @click="mode === 'review' ? chooseMode('new') : nextWordSession">继续学习</button>
    </EmptyState>
  </div>
</template>

<style scoped>
.toolbar { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 18px; flex-wrap: wrap; }
.library { padding: 20px 22px; }
.library-tools { display: flex; align-items: center; gap: 8px; padding-bottom: 14px; border-bottom: 1px solid var(--line); }
.search { flex: 1; display: flex; align-items: center; gap: 8px; padding: 0 11px; border: 1px solid var(--line); border-radius: 9px; background: var(--paper-sunken); color: var(--ink-faint); }
.search input { width: 100%; height: 38px; border: 0; background: transparent; outline: none; color: var(--ink); font-size: 13px; }
.library-tools .select { width: 120px; height: 38px; padding-block: 0; }
.count { color: var(--ink-faint); font-size: 11px; white-space: nowrap; }
.rows { padding-top: 4px; }
.pager { display: flex; align-items: center; justify-content: space-between; padding-top: 14px; border-top: 1px solid var(--line); color: var(--ink-faint); font-size: 11.5px; }
.empty { padding: 30px 0; }
@media (max-width: 620px) {
  .library-tools { flex-wrap: wrap; }
  .search { min-width: 100%; }
}
</style>

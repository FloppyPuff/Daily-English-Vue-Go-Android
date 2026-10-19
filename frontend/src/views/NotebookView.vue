<script setup>
import { computed } from 'vue';
import { useRouter } from 'vue-router';
import { app, notebookWords, notebookSentences, openWord, selectSentence, toggleSavedWord, speakWord, initSession } from '../stores/app.js';
import { kindLabel, promptText } from '../lib/content.js';
import PageHead from '../components/PageHead.vue';
import WordRow from '../components/WordRow.vue';
import EmptyState from '../components/EmptyState.vue';
import Icon from '../components/Icon.vue';

const router = useRouter();
const mistakes = computed(() => app.ui.notebookTab === 'mistakes');
const words = computed(() => notebookWords.value.slice(0, 60));
const sentences = computed(() => notebookSentences.value.slice(0, 60));

function excerpt(sentence) {
  const text = promptText(sentence).replace(/\s+/g, ' ').trim();
  return text.length > 90 ? `${text.slice(0, 90)}…` : text;
}

function sentenceStatus(sentence) {
  const record = app.state.sentenceRecords[sentence.id];
  if (record?.rating === 'mastered') return '已理解';
  if (record?.rating === 'review') return '待巩固';
  return app.state.drafts[sentence.id] ? '草稿' : '未完成';
}

function wordStatus(word) {
  return app.state.records[word.id]?.difficult ? '薄弱' : '收藏';
}

function openNotebookWord(id) {
  openWord(id);
  router.push('/words');
}

function openNotebookSentence(id) {
  selectSentence(id);
  router.push('/sentences');
}

function startMistakes() {
  initSession(mistakes.value ? 'mistakes' : 'saved');
  router.push('/words');
}
</script>

<template>
  <div class="page">
    <PageHead title="笔记" :lede="`${notebookWords.length} 词 · ${notebookSentences.length} 句`" />

    <div class="seg tabs">
      <button type="button" :class="{ on: !mistakes }" @click="app.ui.notebookTab = 'saved'">收藏</button>
      <button type="button" :class="{ on: mistakes }" @click="app.ui.notebookTab = 'mistakes'">薄弱与错题</button>
    </div>

    <section class="sheet block">
      <div class="spread block-head">
        <h2>单词 <span class="num">{{ notebookWords.length }}</span></h2>
        <button v-if="words.length" class="btn sm" type="button" @click="startMistakes">练习这些词</button>
      </div>
      <WordRow
        v-for="word in words"
        :key="word.id"
        :word="word"
        :status="wordStatus(word)"
        :saved="app.state.savedWords.includes(word.id)"
        @open="openNotebookWord"
        @speak="id => speakWord(app.content.wordById(id))"
        @toggle-save="toggleSavedWord"
      />
      <EmptyState
        v-if="!words.length"
        :title="mistakes ? '还没有薄弱词' : '收藏会留在这里'"
        :detail="mistakes ? '评价为“忘记”“模糊”，或真题里答错的词会出现在这里。' : '在词卡或词库点击书签。'"
      />
    </section>

    <section class="sheet block">
      <div class="spread block-head">
        <h2>句子 <span class="num">{{ notebookSentences.length }}</span></h2>
      </div>
      <button v-for="sentence in sentences" :key="sentence.id" class="sentence-row" type="button" @click="openNotebookSentence(sentence.id)">
        <span class="tag moss">{{ kindLabel(sentence) }}</span>
        <span class="sentence-main">
          <strong>{{ excerpt(sentence) }}</strong>
          <small>{{ sentenceStatus(sentence) }}</small>
        </span>
        <Icon name="chevron" :size="15" />
      </button>
      <EmptyState
        v-if="!sentences.length"
        :title="mistakes ? '没有待巩固的句子' : '收藏句子会留在这里'"
        :detail="mistakes ? '翻译自评选择“还需巩固”后出现。' : '在句子页点击收藏。'"
      />
    </section>
  </div>
</template>

<style scoped>
.tabs { margin-bottom: 16px; }
.block { padding: 16px 20px; margin-bottom: 16px; }
.block-head { padding-bottom: 10px; border-bottom: 1px solid var(--line); }
.block-head h2 { font-size: 13px; font-weight: 600; letter-spacing: 0.02em; }
.block-head .num { color: var(--ink-faint); font-weight: 400; font-size: 11px; }
.sentence-row { display: flex; align-items: flex-start; gap: 10px; width: 100%; padding: 12px 0; border: 0; border-bottom: 1px solid var(--line); background: transparent; text-align: left; color: var(--ink); }
.sentence-row:last-child { border-bottom: 0; }
.sentence-row:hover strong { color: var(--moss-strong); }
.sentence-main { flex: 1; min-width: 0; display: grid; gap: 2px; }
.sentence-main strong { font-family: var(--font-display); font-size: 14.5px; font-weight: 500; line-height: 1.5; }
.sentence-main small { color: var(--ink-faint); font-size: 10.5px; }
</style>

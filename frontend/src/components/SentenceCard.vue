<script setup>
import { computed } from 'vue';
import { app, setSentenceDraft, revealSentence, rateCurrentSentence, nextSentence, toggleSavedSentence, speakSentence } from '../stores/app.js';
import { answerLabel, isBackTranslation, kindLabel, promptText, referenceText, sourceLabel } from '../lib/content.js';
import Icon from './Icon.vue';

const props = defineProps({
  sentence: { type: Object, required: true },
  guided: { type: Boolean, default: false },
  recorded: { type: Boolean, default: false },
});
const emit = defineEmits(['next', 'continue']);

const draft = computed({
  get: () => app.ui.sentenceDraft,
  set: value => setSentenceDraft(value),
});
const back = computed(() => isBackTranslation(props.sentence));
const reference = computed(() => referenceText(props.sentence));
const saved = computed(() => app.state.savedSentences.includes(props.sentence.id));
const analysis = computed(() => props.sentence.analysis || []);
const keywords = computed(() => props.sentence.keywords || []);
</script>

<template>
  <article class="sentence-card sheet">
    <div class="card-top">
      <div class="row wrap">
        <span class="tag moss">{{ kindLabel(sentence) }}</span>
        <span v-if="sentence.level" class="tag">{{ sentence.level }}</span>
        <span v-if="sentence.topic" class="tag">{{ sentence.topic }}</span>
      </div>
      <span class="source">{{ sourceLabel(sentence) }}</span>
    </div>

    <div class="prompt">
      <p :class="back ? 'prompt-zh' : 'prompt-en'">{{ promptText(sentence) }}</p>
      <button v-if="!back" class="btn ghost sm" type="button" @click="speakSentence(sentence)">
        <Icon name="volume" :size="14" /> 朗读
      </button>
    </div>

    <div class="writing">
      <label class="field">
        <span>{{ answerLabel(sentence) }}</span>
        <textarea v-model="draft" class="textarea tall" maxlength="10000" :placeholder="back ? '先搭主干，再补修饰。' : '先理清主干，再写下来。'" :readonly="guided && recorded" />
      </label>
      <div class="writing-actions">
        <span class="hint">{{ reference ? '先自己写，再看参考。' : '这题没有参考译文，写完后自评。' }}</span>
        <button class="btn sm" type="button" :class="{ primary: !app.ui.sentenceRevealed }" @click="revealSentence">
          {{ app.ui.sentenceRevealed ? '收起参考' : reference ? '查看参考' : '对照自评' }}
        </button>
      </div>
    </div>

    <div v-if="app.ui.sentenceRevealed" class="reference">
      <div class="spread">
        <span class="eyebrow">REFERENCE</span>
        <span class="tag">并非唯一答案</span>
      </div>
      <p v-if="reference" class="reference-text">{{ reference }}</p>
      <p v-else class="no-reference"><Icon name="info" :size="15" /> 公开来源只有题目，没有参考译文。</p>
      <details v-if="analysis.length" class="analysis">
        <summary>句法拆解与重点词</summary>
        <div v-for="row in analysis" :key="row.label" class="analysis-row">
          <span>{{ row.label }}</span>
          <p>{{ row.text }}</p>
        </div>
        <div v-if="keywords.length" class="keywords">
          <span v-for="keyword in keywords" :key="keyword.word">{{ keyword.word }} <small>{{ keyword.meaning }}</small></span>
        </div>
      </details>
    </div>

    <footer class="sentence-foot">
      <button class="btn ghost sm" type="button" @click="toggleSavedSentence">
        <Icon name="bookmark" :size="14" /> {{ saved ? '已收藏' : '收藏' }}
      </button>
      <div class="foot-actions">
        <template v-if="guided && recorded">
          <span class="recorded"><Icon name="check" :size="14" /> 已记录</span>
          <button class="btn primary sm" type="button" @click="emit('continue')">继续今日学习 <Icon name="arrow" :size="14" /></button>
        </template>
        <template v-else>
          <button class="btn sm" type="button" @click="rateCurrentSentence('review')">还需巩固</button>
          <button class="btn primary sm" type="button" @click="rateCurrentSentence('mastered')">已经理解</button>
          <button v-if="!guided" class="btn ghost sm" type="button" @click="emit('next')">下一句 <Icon name="arrow" :size="14" /></button>
        </template>
      </div>
    </footer>
  </article>
</template>

<style scoped>
.sentence-card { padding: clamp(22px, 4vw, 34px); }
.card-top { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; }
.wrap { flex-wrap: wrap; }
.source { color: var(--ink-faint); font-size: 11px; text-align: right; }

.prompt { padding: 26px 0 22px; border-bottom: 1px solid var(--line); }
.prompt-en { font-family: var(--font-display); font-size: clamp(20px, 3vw, 26px); line-height: 1.6; }
.prompt-zh { font-size: 16px; line-height: 1.9; }
.prompt .btn { margin-top: 12px; }

.writing { padding: 20px 0; }
.writing-actions { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-top: 10px; }
.hint { color: var(--ink-faint); font-size: 11.5px; }

.reference { padding: 18px 0; border-top: 1px solid var(--line); }
.reference-text { margin-top: 12px; font-size: 15px; line-height: 1.8; }
.no-reference { display: flex; align-items: center; gap: 7px; margin-top: 12px; color: var(--ink-faint); font-size: 12.5px; }
.analysis { margin-top: 14px; }
.analysis summary { color: var(--ink-soft); font-size: 12.5px; cursor: pointer; }
.analysis-row { display: grid; grid-template-columns: 64px 1fr; gap: 10px; margin-top: 10px; font-size: 12.5px; }
.analysis-row span { color: var(--ink-faint); }
.analysis-row p { color: var(--ink-soft); }
.keywords { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 12px; }
.keywords span { padding: 3px 8px; border: 1px solid var(--line); border-radius: 999px; font-size: 11px; color: var(--ink-soft); }
.keywords small { color: var(--ink-faint); }

.sentence-foot { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding-top: 16px; border-top: 1px solid var(--line); }
.foot-actions { display: flex; align-items: center; gap: 7px; flex-wrap: wrap; justify-content: flex-end; }
.recorded { display: inline-flex; align-items: center; gap: 4px; color: var(--moss-strong); font-size: 12px; }

@media (max-width: 620px) {
  .card-top { flex-direction: column; }
  .source { text-align: left; }
  .sentence-foot { flex-direction: column; align-items: stretch; }
  .foot-actions { justify-content: stretch; }
  .foot-actions .btn { flex: 1; }
}
</style>

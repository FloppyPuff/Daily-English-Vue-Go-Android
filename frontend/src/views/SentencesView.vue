<script setup>
import { computed, onMounted } from 'vue';
import { app, currentSentence, ensureSentence, selectSentence, nextSentence } from '../stores/app.js';
import { promptText } from '../lib/content.js';
import PageHead from '../components/PageHead.vue';
import SentenceCard from '../components/SentenceCard.vue';
import Icon from '../components/Icon.vue';

const list = computed(() => app.content?.sentences || []);
const index = computed(() => Math.max(0, list.value.findIndex(item => item.id === app.ui.sentenceId)));

function excerpt(sentence) {
  const text = promptText(sentence).replace(/\s+/g, ' ').trim();
  return text.length > 70 ? `${text.slice(0, 70)}…` : text;
}

function choose(id) {
  selectSentence(id);
}

onMounted(() => ensureSentence());
</script>

<template>
  <div class="page">
    <PageHead title="句子" :lede="`${list.length} 句长难句与翻译段落`">
      <button class="btn sm" type="button" @click="nextSentence"><Icon name="repeat" :size="14" /> 下一句</button>
    </PageHead>

    <div class="catalog">
      <select class="select" :value="app.ui.sentenceId" aria-label="选择句子" @change="choose($event.target.value)">
        <option v-for="(sentence, i) in list" :key="sentence.id" :value="sentence.id">
          {{ String(i + 1).padStart(2, '0') }} · {{ excerpt(sentence) }}
        </option>
      </select>
      <span class="num counter">{{ index + 1 }} / {{ list.length }}</span>
    </div>

    <SentenceCard
      v-if="currentSentence"
      :sentence="currentSentence"
      @next="nextSentence"
    />
  </div>
</template>

<style scoped>
.catalog { display: flex; align-items: center; gap: 10px; margin-bottom: 16px; }
.catalog .select { flex: 1; }
.counter { color: var(--ink-faint); font-size: 11px; white-space: nowrap; }
</style>

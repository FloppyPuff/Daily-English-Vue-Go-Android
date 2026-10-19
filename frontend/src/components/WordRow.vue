<script setup>
import { computed } from 'vue';
import { bestTier } from '../lib/content.js';
import Icon from './Icon.vue';

const props = defineProps({
  word: { type: Object, required: true },
  status: { type: String, default: '' },
  saved: { type: Boolean, default: false },
});
defineEmits(['open', 'speak', 'toggle-save']);
const tier = computed(() => bestTier(props.word));
</script>

<template>
  <div class="word-row">
    <button class="row-main" type="button" @click="$emit('open', word.id)">
      <span class="word">
        {{ word.id }}
        <small v-if="word.phonetic" class="num">{{ word.phonetic }}</small>
      </span>
      <span class="meaning">
        {{ word.meaning }}
        <i v-if="tier">{{ tier }}</i>
      </span>
    </button>
    <span v-if="status" class="status">{{ status }}</span>
    <button class="icon-btn" type="button" :aria-label="`朗读 ${word.id}`" @click="$emit('speak', word.id)"><Icon name="volume" :size="16" /></button>
    <button class="icon-btn" :class="{ on: saved }" type="button" :aria-label="saved ? `取消收藏 ${word.id}` : `收藏 ${word.id}`" @click="$emit('toggle-save', word.id)">
      <Icon name="bookmark" :size="16" />
    </button>
  </div>
</template>

<style scoped>
.word-row {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 2px;
  border-bottom: 1px solid var(--line);
}
.word-row:last-child { border-bottom: 0; }
.row-main {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: baseline;
  gap: 16px;
  padding: 0;
  border: 0;
  background: transparent;
  text-align: left;
}
.row-main:hover .word { color: var(--moss-strong); }
.word { flex: none; font-family: var(--font-display); font-size: 18px; color: var(--ink); }
.word small { margin-left: 7px; color: var(--ink-faint); font-family: var(--font-mono); font-size: 10px; }
.meaning { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: var(--ink-soft); font-size: 12.5px; }
.meaning i { margin-left: 8px; color: var(--moss-strong); font-style: normal; font-size: 10.5px; }
.status { flex: none; color: var(--ink-faint); font-size: 10.5px; }

@media (max-width: 560px) {
  .row-main { flex-direction: column; align-items: flex-start; gap: 1px; }
  .meaning { max-width: 100%; }
  .status { display: none; }
}
</style>

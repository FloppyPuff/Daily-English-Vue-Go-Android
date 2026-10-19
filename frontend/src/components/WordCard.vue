<script setup>
import { computed } from 'vue';
import { app, revealWord, rateCurrentWord, setSpellingInput, checkSpellingAnswer, toggleSavedWord, speakWord } from '../stores/app.js';
import { bestTier, paperHits } from '../lib/content.js';
import Icon from './Icon.vue';

const props = defineProps({
  word: { type: Object, required: true },
  index: { type: Number, default: 0 },
  total: { type: Number, default: 0 },
  spelling: { type: Boolean, default: false },
});

const tier = computed(() => bestTier(props.word));
const paperParts = computed(() => ['cet6', 'postgrad']
  .map(exam => {
    const hits = paperHits(props.word, exam);
    return hits ? `${exam === 'cet6' ? 'CET-6' : '考研'} ${hits} 次` : '';
  })
  .filter(Boolean));
const saved = computed(() => app.state.savedWords.includes(props.word.id));
const spellingValue = computed({
  get: () => app.ui.spellingInput,
  set: value => setSpellingInput(value),
});

function submitSpelling() {
  if (app.ui.spellingInput.trim()) checkSpellingAnswer();
}
</script>

<template>
  <article class="word-card sheet">
    <div class="card-top">
      <div class="row">
        <span v-if="tier" class="tag moss">{{ tier }}</span>
        <span class="tag">{{ spelling ? '拼写' : '闪卡' }}</span>
      </div>
      <div class="row">
        <span class="num counter">{{ String(index + 1).padStart(2, '0') }} / {{ String(total).padStart(2, '0') }}</span>
        <button v-if="!spelling" class="icon-btn" type="button" aria-label="朗读" @click="speakWord(word)"><Icon name="volume" /></button>
        <button class="icon-btn" :class="{ on: saved }" type="button" :aria-label="saved ? '取消收藏' : '收藏'" @click="toggleSavedWord(word.id)">
          <Icon name="bookmark" />
        </button>
      </div>
    </div>

    <div class="word-body">
      <template v-if="!spelling || app.ui.spellingChecked">
        <h2 class="headword">{{ word.id }}</h2>
        <div class="word-meta">
          <span v-if="word.phonetic" class="num">{{ word.phonetic }}</span>
          <span v-if="word.pos">{{ word.pos }}</span>
          <span v-if="word.oxford" class="tag">牛津核心</span>
        </div>
      </template>

      <template v-if="spelling">
        <p class="meaning" :class="{ prompt: !app.ui.spellingChecked }">{{ word.meaning }}</p>
        <form class="spell-form" @submit.prevent="submitSpelling">
          <input
            v-model="spellingValue"
            class="input"
            autocomplete="off"
            autocapitalize="off"
            spellcheck="false"
            :disabled="app.ui.spellingChecked"
            placeholder="写出英文单词"
            aria-label="输入英文拼写"
          />
          <button v-if="!app.ui.spellingChecked" class="btn" type="submit" :disabled="!app.ui.spellingInput.trim()">检查</button>
        </form>
        <p v-if="app.ui.spellingChecked" class="spell-result" :class="app.ui.spellingCorrect ? 'ok' : 'bad'">
          <Icon :name="app.ui.spellingCorrect ? 'check' : 'close'" :size="15" />
          {{ app.ui.spellingCorrect ? '拼写正确' : `正确拼写：${word.id}` }}
        </p>
      </template>

      <template v-else-if="app.ui.revealed">
        <p class="meaning">{{ word.meaning }}</p>
        <p v-if="word.senses?.length > 1" class="senses">{{ word.senses.slice(1).join('；') }}</p>
        <div v-if="word.example?.en" class="example">
          <p class="example-en">{{ word.example.en }}</p>
          <p v-if="word.example.zh" class="example-zh">{{ word.example.zh }}</p>
          <p v-if="word.example.source" class="example-source">{{ word.example.source }}</p>
        </div>
        <p v-if="word.example?.collocation" class="word-note">搭配 · {{ word.example.collocation }}</p>
        <p v-if="word.example?.memory" class="word-note">记忆 · {{ word.example.memory }}</p>
        <p v-if="paperParts.length" class="paper-note">真题 · {{ paperParts.join(' · ') }}</p>
        <p v-if="word.forms?.length" class="forms">
          <span v-for="form in word.forms" :key="form.label + form.value">{{ form.label }} <b>{{ form.value }}</b></span>
        </p>
      </template>

      <button v-else class="btn primary reveal" type="button" @click="revealWord">
        显示释义 <kbd>Space</kbd>
      </button>
    </div>

    <div v-if="app.ui.revealed || app.ui.spellingChecked" class="ratings" aria-label="记忆评价">
      <button type="button" class="rate again" @click="rateCurrentWord('again')"><span>忘记</span><small>10 分钟后</small></button>
      <button type="button" class="rate hard" @click="rateCurrentWord('hard')"><span>模糊</span><small>1 天后</small></button>
      <button type="button" class="rate good" @click="rateCurrentWord('good')"><span>记住</span><small>至少 3 天</small></button>
      <button type="button" class="rate easy" @click="rateCurrentWord('easy')"><span>熟悉</span><small>至少 7 天</small></button>
    </div>
  </article>
</template>

<style scoped>
.word-card { padding: clamp(22px, 4vw, 34px); }
.card-top { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.counter { color: var(--ink-faint); font-size: 11px; }
.word-body { min-height: 260px; display: flex; flex-direction: column; justify-content: center; align-items: flex-start; padding: 26px 0 22px; }
.headword {
  font-family: var(--font-display);
  font-size: clamp(42px, 7vw, 64px);
  font-weight: 500;
  line-height: 1.02;
  letter-spacing: -0.035em;
  word-break: break-word;
}
.word-meta { display: flex; flex-wrap: wrap; align-items: center; gap: 9px; margin-top: 12px; color: var(--ink-soft); font-size: 13px; }
.meaning { margin-top: 20px; max-width: 56ch; font-size: 16px; line-height: 1.7; color: var(--ink); }
.meaning.prompt { margin-top: 0; font-size: clamp(22px, 4vw, 28px); font-weight: 600; letter-spacing: -0.02em; line-height: 1.45; }
.senses { margin-top: 8px; max-width: 60ch; color: var(--ink-soft); font-size: 13px; line-height: 1.7; }
.word-note { margin-top: 10px; max-width: 60ch; color: var(--ink-soft); font-size: 12px; line-height: 1.6; }
.paper-note { margin-top: 10px; color: var(--ink-faint); font-size: 11px; }

.reveal { margin-top: 24px; align-self: stretch; max-width: 260px; }
.reveal kbd {
  padding: 2px 6px;
  border: 1px solid color-mix(in srgb, var(--paper) 40%, transparent);
  border-radius: 5px;
  font-family: var(--font-mono);
  font-size: 10px;
  opacity: 0.75;
}

.example { margin-top: 24px; padding-left: 16px; border-left: 2px solid var(--line); max-width: 60ch; }
.example-en { font-family: var(--font-display); font-size: 16px; line-height: 1.6; }
.example-zh { margin-top: 6px; color: var(--ink-soft); font-size: 13px; }
.example-source { margin-top: 7px; color: var(--ink-faint); font-size: 11px; }
.forms { display: flex; flex-wrap: wrap; gap: 6px 14px; margin-top: 18px; color: var(--ink-faint); font-size: 11.5px; }
.forms b { color: var(--ink-soft); font-weight: 500; }

.spell-form { display: flex; gap: 8px; width: 100%; max-width: 480px; margin-top: 18px; }
.spell-form .input { font-family: var(--font-mono); }
.spell-result { display: inline-flex; align-items: center; gap: 6px; margin-top: 14px; font-size: 13px; }
.spell-result.ok { color: var(--moss-strong); }
.spell-result.bad { color: var(--correction); }

.ratings { display: grid; grid-template-columns: repeat(4, 1fr); gap: 8px; padding-top: 20px; border-top: 1px solid var(--line); }
.rate {
  display: grid;
  gap: 2px;
  min-height: 52px;
  padding: 8px 10px;
  border: 1px solid var(--line);
  border-radius: 10px;
  background: transparent;
  text-align: left;
  color: var(--ink);
  transition: background 140ms ease, border-color 140ms ease;
}
.rate span { font-size: 13px; font-weight: 500; }
.rate small { color: var(--ink-faint); font-size: 10px; }
.rate:hover { background: var(--paper-sunken); }
.rate.good:hover { border-color: color-mix(in srgb, var(--moss) 40%, var(--line)); background: var(--moss-wash); }
.rate.again:hover { border-color: color-mix(in srgb, var(--correction) 40%, var(--line)); background: var(--correction-wash); }

@media (max-width: 640px) {
  .word-body { min-height: 210px; padding-block: 20px; }
  .ratings { grid-template-columns: repeat(2, 1fr); }
}
</style>

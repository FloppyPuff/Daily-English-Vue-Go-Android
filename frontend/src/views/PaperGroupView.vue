<script setup>
import { computed, onMounted, watch } from 'vue';
import { useRoute } from 'vue-router';
import { app, currentPaperGroups, openPaper, openPaperGroup, pickPaperOption, setPaperAnswer, togglePaperCheck, submitPaper, ratePaper } from '../stores/app.js';
import { paperGroupLabel, paperPassage } from '../lib/content.js';
import PageHead from '../components/PageHead.vue';
import EmptyState from '../components/EmptyState.vue';
import Icon from '../components/Icon.vue';

const route = useRoute();
const setId = computed(() => String(route.params.setId || ''));
const groupIndex = computed(() => String(route.params.groupIndex || ''));
const group = computed(() => currentPaperGroups.value.find(item => item.index === groupIndex.value) || null);
const passage = computed(() => group.value ? paperPassage(group.value, { answers: app.ui.paperAnswers, checked: app.ui.paperChecked }) : '');
const record = computed(() => group.value ? app.state.papers[group.value.key] : null);
const answered = computed(() => group.value
  ? group.value.questions.filter(question => String(app.ui.paperAnswers[question.number] || '').trim()).length
  : 0);
const complete = computed(() => group.value ? answered.value === group.value.questions.length : false);

function right(question) {
  return String(app.ui.paperAnswers[question.number] || '') === question.answer;
}

function referenceOf(question) {
  return question.answer;
}

function answerText(number, value) {
  setPaperAnswer(number, value);
}

async function load() {
  if (!setId.value) return;
  if (app.ui.paperSet !== setId.value || !app.ui.paper) await openPaper(setId.value);
  openPaperGroup(groupIndex.value);
}

onMounted(load);
watch([setId, groupIndex], load);
</script>

<template>
  <div class="page paper-page">
    <div class="back-row">
      <RouterLink :to="`/papers/${setId}`" class="btn ghost sm"><Icon name="arrow" :size="14" style="transform: rotate(180deg)" /> 返回这套试卷</RouterLink>
    </div>

    <template v-if="group">
      <PageHead :eyebrow="paperGroupLabel(group)" :title="group.section || paperGroupLabel(group)" :lede="`${group.questions.length} 题${group.sectionScore ? ` · ${group.sectionScore} 分` : ''}`">
        <span v-if="record?.total" class="tag moss">已批改 {{ record.correct }} / {{ record.total }}</span>
        <span v-else-if="record?.rating" class="tag">{{ record.rating === 'mastered' ? '已理解' : '待巩固' }}</span>
      </PageHead>

      <article class="sheet exercise">
        <div v-if="!group.answerable" class="unavailable">
          <Icon name="info" :size="15" /> 公开来源只有答案字母，没有选项句子；这里只保留原文精读。
        </div>

        <div v-if="group.passage?.length" class="passage" v-html="passage" />
        <p v-if="group.instructions" class="instructions">{{ group.instructions }}</p>

        <template v-if="group.answerable && group.translation">
          <div v-for="question in group.questions" :key="question.number" class="translation-item">
            <p v-if="question.stem" class="translation-stem">{{ question.stem }}</p>
            <label class="field">
              <span>{{ question.stem ? '你的译文' : '全文译文' }}</span>
              <textarea
                class="textarea"
                :value="app.ui.paperAnswers[question.number] || ''"
                placeholder="先理清主干，再写通顺。"
                @input="answerText(question.number, $event.target.value)"
              />
            </label>
            <p v-if="app.ui.paperChecked" class="reference">{{ referenceOf(question) }}</p>
            <p v-if="record?.rating" class="rated">{{ record.rating === 'mastered' ? '已理解' : '待巩固' }}</p>
          </div>
          <div class="exercise-actions">
            <button class="btn sm" type="button" @click="togglePaperCheck">{{ app.ui.paperChecked ? '收起参考' : '对照参考' }}</button>
            <div class="row">
              <button class="btn sm" type="button" @click="ratePaper(group, 'review')">还需巩固</button>
              <button class="btn primary sm" type="button" @click="ratePaper(group, 'mastered')">已经理解</button>
            </div>
          </div>
        </template>

        <template v-else-if="group.answerable">
          <ol class="questions">
            <li v-for="question in group.questions" :key="question.number" class="question">
              <div class="q-head">
                <span class="q-number num">{{ question.number }}</span>
                <p v-if="question.stem">{{ question.stem }}</p>
              </div>
              <div v-if="question.options" class="options">
                <button
                  v-for="(text, letter) in question.options"
                  :key="letter"
                  type="button"
                  class="option"
                  :class="{
                    picked: String(app.ui.paperAnswers[question.number] || '') === letter,
                    correct: app.ui.paperChecked && letter === question.answer,
                    wrong: app.ui.paperChecked && String(app.ui.paperAnswers[question.number] || '') === letter && letter !== question.answer,
                  }"
                  @click="pickPaperOption(question.number, letter)"
                >
                  <b class="num">{{ letter }}</b>
                  <span>{{ text }}</span>
                  <Icon v-if="app.ui.paperChecked && letter === question.answer" name="check" :size="14" />
                </button>
              </div>
              <p v-if="app.ui.paperChecked && app.ui.paperAnswers[question.number] && !right(question)" class="answer-hint">
                正确答案是 {{ question.answer }}
              </p>
            </li>
          </ol>

          <div class="exercise-actions">
            <span class="muted small">{{ answered }} / {{ group.questions.length }} 已作答</span>
            <div class="row">
              <button v-if="app.ui.paperChecked" class="btn sm" type="button" @click="app.ui.paperAnswers = {}; app.ui.paperChecked = false">重新作答</button>
              <button v-else class="btn primary sm" type="button" :disabled="!complete" @click="submitPaper(group)">提交答案</button>
            </div>
          </div>
        </template>
      </article>
    </template>

    <EmptyState v-else title="这一组暂时打不开" detail="返回试卷后重试。">
      <RouterLink :to="`/papers/${setId}`" class="btn">返回试卷</RouterLink>
    </EmptyState>
  </div>
</template>

<style scoped>
.paper-page { max-width: 820px; }
.back-row { margin-bottom: 14px; }
.exercise { padding: clamp(20px, 4vw, 34px); }
.passage { font-family: var(--font-display); font-size: 16.5px; line-height: 1.85; color: var(--ink); }
.passage :deep(.paper-blank) {
  display: inline-grid;
  place-items: center;
  min-width: 24px;
  height: 22px;
  margin: 0 2px;
  border-bottom: 1px solid var(--line-strong);
  color: var(--ink-faint);
  font-family: var(--font-mono);
  font-size: 11px;
}
.passage :deep(.paper-blank.is-picked) { color: var(--moss-strong); border-color: var(--moss); }
.passage :deep(.paper-blank.is-correct) { color: var(--moss-strong); border-color: var(--moss); background: var(--moss-wash); }
.passage :deep(.paper-blank.is-wrong) { color: var(--correction); border-color: var(--correction); background: var(--correction-wash); }
.passage :deep(.paper-underlined) { text-decoration: underline; text-underline-offset: 3px; font-style: normal; }
.instructions { margin-top: 16px; color: var(--ink-faint); font-size: 12px; }
.unavailable { display: flex; align-items: center; gap: 8px; margin-bottom: 16px; padding: 10px 12px; border: 1px solid var(--line); border-radius: 9px; color: var(--ink-faint); font-size: 12px; }
.questions { list-style: none; margin: 24px 0 0; padding: 0; display: grid; gap: 22px; }
.question { padding-top: 20px; border-top: 1px solid var(--line); }
.q-head { display: flex; gap: 10px; align-items: flex-start; }
.q-number { flex: none; width: 22px; color: var(--ink-faint); font-size: 11px; padding-top: 2px; }
.q-head p { font-size: 14px; line-height: 1.6; }
.options { display: grid; gap: 6px; margin-top: 12px; }
.option {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 9px 12px;
  border: 1px solid var(--line);
  border-radius: 9px;
  background: transparent;
  color: var(--ink-soft);
  text-align: left;
  font-size: 13px;
  line-height: 1.5;
}
.option b { flex: none; width: 16px; color: var(--ink-faint); font-weight: 500; }
.option:hover { background: var(--paper-sunken); color: var(--ink); }
.option.picked { border-color: var(--moss); color: var(--ink); background: var(--moss-wash); }
.option.correct { border-color: var(--moss); background: var(--moss-wash); color: var(--ink); }
.option.wrong { border-color: var(--correction); background: var(--correction-wash); color: var(--ink); }
.option .icon { margin-left: auto; flex: none; color: var(--moss-strong); }
.answer-hint { margin-top: 8px; color: var(--correction); font-size: 11.5px; }
.exercise-actions { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-top: 24px; padding-top: 18px; border-top: 1px solid var(--line); flex-wrap: wrap; }
.translation-item { display: grid; gap: 10px; padding-top: 18px; margin-top: 18px; border-top: 1px solid var(--line); }
.translation-stem { font-family: var(--font-display); font-size: 16px; line-height: 1.7; }
.reference { padding: 12px 14px; border-left: 2px solid var(--moss); background: var(--moss-wash); font-size: 14px; line-height: 1.7; }
.rated { color: var(--moss-strong); font-size: 11.5px; }
</style>

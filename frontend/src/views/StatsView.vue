<script setup>
import { computed } from 'vue';
import { app, stats, setStatsExam } from '../stores/app.js';
import { localDay } from '../lib/core.js';
import PageHead from '../components/PageHead.vue';

const examLabel = computed(() => app.ui.statsExam === 'cet6' ? 'CET-6' : app.ui.statsExam === 'postgrad' ? '考研英语' : '综合词库');
const percent = computed(() => !stats.value?.total ? 0 : Math.round(stats.value.learned / stats.value.total * 100));
const weekMax = computed(() => Math.max(1, ...(stats.value?.week || []).flatMap(day => [day.words, day.sentences])));

function mondayIndex(day) {
  const date = new Date(`${day}T12:00:00`);
  return (date.getDay() + 6) % 7;
}

const heatCells = computed(() => {
  const counts = stats.value?.dailyCounts || {};
  const days = Array.from({ length: 84 }, (_, i) => {
    const date = new Date();
    date.setDate(date.getDate() - 83 + i);
    const day = localDay(date.getTime());
    return { day, count: counts[day] || 0 };
  });
  const leading = days.length ? mondayIndex(days[0].day) : 0;
  const cells = [...Array.from({ length: leading }, () => null), ...days];
  while (cells.length % 7) cells.push(null);
  return cells;
});

const heatWeeks = computed(() => Math.ceil(heatCells.value.length / 7));

function level(count) {
  return count > 20 ? 3 : count > 5 ? 2 : count > 0 ? 1 : 0;
}
</script>

<template>
  <div class="page">
    <PageHead title="统计">
      <div class="seg">
        <button type="button" :class="{ on: app.ui.statsExam === 'all' }" @click="setStatsExam('all')">全部</button>
        <button type="button" :class="{ on: app.ui.statsExam === 'cet6' }" @click="setStatsExam('cet6')">CET-6</button>
        <button type="button" :class="{ on: app.ui.statsExam === 'postgrad' }" @click="setStatsExam('postgrad')">考研</button>
      </div>
    </PageHead>

    <section v-if="stats" class="tiles">
      <div class="tile"><span>已学</span><strong class="num">{{ stats.learned }}</strong><small>共 {{ stats.total }} 词</small></div>
      <div class="tile"><span>较熟悉</span><strong class="num">{{ stats.mastered }}</strong><small>连续 3 次记住</small></div>
      <div class="tile"><span>连续</span><strong class="num">{{ stats.streak }}</strong><small>天 · 累计 {{ stats.activeDays }}</small></div>
      <div class="tile"><span>专注</span><strong class="num">{{ stats.totalMinutes }}</strong><small>分钟 · 今天 {{ stats.todayMinutes }}</small></div>
      <div class="tile"><span>真题</span><strong class="num">{{ stats.paperTotal }}</strong><small>题 · 答对 {{ stats.paperCorrect }}</small></div>
    </section>

    <section v-if="stats" class="grid">
      <div class="sheet panel week">
        <div class="spread panel-head"><h2>最近七天</h2><span class="small faint">词 / 句</span></div>
        <div class="week-chart">
          <div v-for="day in stats.week" :key="day.day" class="day">
            <div class="bars">
              <i class="bar word" :style="{ height: `${Math.max(3, day.words / weekMax * 100)}%` }" :title="`${day.words} 词`" />
              <i class="bar sentence" :style="{ height: `${Math.max(2, day.sentences / weekMax * 100)}%` }" :title="`${day.sentences} 句`" />
            </div>
            <span>{{ day.label }}</span>
          </div>
        </div>
      </div>

      <div class="sheet panel mastery">
        <div class="spread panel-head"><h2>掌握情况</h2><span class="small faint">{{ examLabel }}</span></div>
        <p class="percent num">{{ percent }}<span>%</span></p>
        <div class="track"><i :style="{ width: `${percent}%` }" /></div>
        <div class="legend">
          <span><i class="dot strong" />较熟悉 <b class="num">{{ stats.mastered }}</b></span>
          <span><i class="dot mid" />学习中 <b class="num">{{ stats.learned - stats.mastered }}</b></span>
          <span><i class="dot" />未开始 <b class="num">{{ stats.total - stats.learned }}</b></span>
        </div>
      </div>
    </section>

    <section v-if="stats" class="sheet panel heat">
      <div class="spread panel-head"><h2>学习热力图</h2><span class="small faint">最近 12 周</span></div>
      <div class="heat-wrap">
        <div class="weekdays" aria-hidden="true"><span>一</span><span /><span>三</span><span /><span>五</span><span /><span>日</span></div>
        <div
          class="heat-grid"
          role="img"
          aria-label="最近十二周学习热力图"
          :style="{ gridTemplateColumns: `repeat(${heatWeeks}, minmax(0, 1fr))` }"
        >
          <span
            v-for="(cell, index) in heatCells"
            :key="index"
            class="cell"
            :class="cell ? `level-${level(cell.count)}` : 'blank'"
            :title="cell ? `${cell.day}：${cell.count} 项` : ''"
          />
        </div>
      </div>
      <div class="heat-foot"><span>每一格是一天</span><span class="scale">少 <i class="cell level-0" /><i class="cell level-1" /><i class="cell level-2" /><i class="cell level-3" /> 多</span></div>
    </section>
  </div>
</template>

<style scoped>
.tiles { display: grid; grid-template-columns: repeat(5, 1fr); gap: 10px; margin-bottom: 16px; }
.tile { padding: 14px 16px; background: var(--paper-raised); border: 1px solid var(--line); border-radius: 13px; display: grid; gap: 3px; }
.tile span { color: var(--ink-faint); font-size: 11px; }
.tile strong { font-size: 26px; font-weight: 500; line-height: 1.1; }
.tile small { color: var(--ink-faint); font-size: 10.5px; }
.grid { display: grid; grid-template-columns: 1.25fr 0.9fr; gap: 12px; margin-bottom: 12px; }
.panel { padding: 18px 20px; }
.panel-head h2 { font-size: 13px; font-weight: 600; }
.week-chart { display: flex; align-items: flex-end; justify-content: space-between; gap: 6px; height: 150px; margin-top: 18px; }
.day { flex: 1; display: grid; grid-template-rows: 1fr auto; justify-items: center; gap: 7px; height: 100%; }
.bars { display: flex; align-items: flex-end; gap: 3px; width: 100%; height: 100%; justify-content: center; }
.bar { display: block; width: 8px; min-height: 3px; border-radius: 3px 3px 1px 1px; }
.bar.word { background: var(--moss); }
.bar.sentence { background: var(--marker); }
.day span { color: var(--ink-faint); font-size: 10px; }
.percent { margin-top: 18px; font-size: 46px; line-height: 1; letter-spacing: -0.04em; }
.percent span { margin-left: 2px; font-size: 18px; color: var(--ink-faint); }
.track { height: 6px; margin: 16px 0; border-radius: 999px; background: var(--paper-sunken); overflow: hidden; }
.track i { display: block; height: 100%; background: var(--moss); border-radius: inherit; }
.legend { display: grid; gap: 7px; color: var(--ink-soft); font-size: 11.5px; }
.legend span { display: flex; align-items: center; gap: 7px; }
.legend b { margin-left: auto; color: var(--ink); font-weight: 500; }
.dot { width: 7px; height: 7px; border-radius: 50%; background: var(--line-strong); }
.dot.strong { background: var(--moss); }
.dot.mid { background: color-mix(in srgb, var(--moss) 40%, var(--paper)); }
.heat-wrap { display: flex; align-items: stretch; justify-content: center; gap: 10px; margin-top: 16px; }
.weekdays { display: grid; flex: 0 0 12px; grid-template-rows: repeat(7, 1fr); gap: 4px; color: var(--ink-faint); font-size: 9px; }
.weekdays span { display: flex; align-items: center; justify-content: flex-end; }
.heat-grid { display: grid; flex: 1 1 560px; grid-auto-flow: column; grid-template-rows: repeat(7, auto); gap: 4px; min-width: 0; max-width: 560px; }
.cell { display: block; width: 100%; height: auto; aspect-ratio: 1; border-radius: 3px; background: var(--paper-sunken); }
.cell.blank { background: transparent; }
.cell.level-1 { background: color-mix(in srgb, var(--moss) 30%, var(--paper)); }
.cell.level-2 { background: color-mix(in srgb, var(--moss) 60%, var(--paper)); }
.cell.level-3 { background: var(--moss); }
.heat-foot { display: flex; justify-content: space-between; margin-top: 10px; color: var(--ink-faint); font-size: 10.5px; }
.scale { display: inline-flex; align-items: center; gap: 3px; }
.scale .cell { width: 10px; height: 10px; }
@media (max-width: 820px) {
  .tiles { grid-template-columns: repeat(3, 1fr); }
  .grid { grid-template-columns: 1fr; }
}
@media (max-width: 520px) {
  .tiles { grid-template-columns: repeat(2, 1fr); }
  .week-chart { height: 120px; }
}
</style>

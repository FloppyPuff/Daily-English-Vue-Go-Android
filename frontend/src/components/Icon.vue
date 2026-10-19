<script setup>
import { computed } from 'vue';

const props = defineProps({
  name: { type: String, required: true },
  size: { type: [Number, String], default: 18 },
  stroke: { type: [Number, String], default: 1.6 },
});

const paths = {
  home: '<rect x="3" y="3" width="7" height="7" rx="1.5"/><rect x="14" y="3" width="7" height="7" rx="1.5"/><rect x="3" y="14" width="7" height="7" rx="1.5"/><rect x="14" y="14" width="7" height="7" rx="1.5"/>',
  cards: '<rect x="7" y="5" width="13" height="16" rx="2"/><path d="M4 17V4a2 2 0 0 1 2-2h10M11 10h5m-5 4h3"/>',
  translate: '<path d="M3 5h12M9 3v2M5 5c1 5 4 8 8 10M13 5c-1 5-5 9-10 11m12 5 4-11 4 11m-6.5-4h5"/>',
  repeat: '<path d="M20 8a8 8 0 0 0-14-3L3 8m0-5v5h5M4 16a8 8 0 0 0 14 3l3-3m0 5v-5h-5"/>',
  bookmark: '<path d="M6 4a1 1 0 0 1 1-1h10a1 1 0 0 1 1 1v17l-6-4-6 4Z"/>',
  chart: '<path d="M4 3v18h17M8 16v-5m5 5V6m5 10v-8"/>',
  settings: '<path d="m9 3-.5 3-3 1-2.5-1-2 3 2.5 2v3L1 16l2 3 3-1 2.5 1 .5 3h4l.5-3 3-1 2.5 1 2-3-2.5-2v-3L21 9l-2-3-3 1-2.5-1-.5-3Z" transform="translate(1 -1) scale(.95)"/><circle cx="11.5" cy="11.5" r="3"/>',
  arrow: '<path d="M4 12h15m-5-5 5 5-5 5"/>',
  chevron: '<path d="m9 5 7 7-7 7"/>',
  down: '<path d="m6 9 6 6 6-6"/>',
  volume: '<path d="m11 4-6 5H2v6h3l6 5ZM15 8a6 6 0 0 1 0 8m3-11a10 10 0 0 1 0 14"/>',
  check: '<path d="m5 12 4 4L19 6"/>',
  clock: '<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>',
  flame: '<path d="M12 2c2 5 7 7 7 12a7 7 0 0 1-14 0c0-3 2-6 4-8 0 3 1 4 2 5 2-3 2-6 1-9Z"/>',
  target: '<circle cx="12" cy="12" r="9"/><circle cx="12" cy="12" r="5"/><circle cx="12" cy="12" r="1"/>',
  leaf: '<path d="M20 3c-7-1-15 2-15 9a6 6 0 0 0 6 6c7 0 10-8 9-15ZM3 21 14 10"/>',
  search: '<circle cx="10" cy="10" r="6"/><path d="m15 15 5 5"/>',
  close: '<path d="m6 6 12 12M6 18 18 6"/>',
  play: '<path d="m8 4 12 8-12 8Z"/>',
  pause: '<path d="M8 5v14M16 5v14"/>',
  download: '<path d="M12 3v12m-5-5 5 5 5-5M4 16v5h16v-5"/>',
  upload: '<path d="M12 16V4m-5 5 5-5 5 5M4 16v5h16v-5"/>',
  info: '<circle cx="12" cy="12" r="9"/><path d="M12 11v6m0-11v1"/>',
  keyboard: '<rect x="2" y="5" width="20" height="14" rx="3"/><path d="M6 9h1m4 0h1m4 0h1M6 13h1m4 0h1m4 0h1M8 16h8"/>',
  menu: '<path d="M4 6h16M4 12h16M4 18h16"/>',
  more: '<circle cx="5" cy="12" r="1"/><circle cx="12" cy="12" r="1"/><circle cx="19" cy="12" r="1"/>',
  user: '<circle cx="12" cy="8" r="4"/><path d="M4 21a8 8 0 0 1 16 0"/>',
  sun: '<circle cx="12" cy="12" r="4"/><path d="M12 2v2m0 16v2M4.9 4.9l1.4 1.4m11.4 11.4 1.4 1.4M2 12h2m16 0h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4"/>',
  moon: '<path d="M20 14.5A8.5 8.5 0 0 1 9.5 4 8.5 8.5 0 1 0 20 14.5Z"/>',
};

const path = computed(() => paths[props.name] || paths.cards);
</script>

<template>
  <svg
    class="icon"
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    :stroke-width="stroke"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
    v-html="path"
  />
</template>

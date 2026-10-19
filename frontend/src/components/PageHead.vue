<script setup>
defineProps({
  eyebrow: { type: String, default: '' },
  title: { type: String, required: true },
  lede: { type: String, default: '' },
});
</script>

<template>
  <header class="page-head">
    <div>
      <p v-if="eyebrow" class="eyebrow">{{ eyebrow }}</p>
      <h1>{{ title }}</h1>
      <p v-if="lede" class="lede">{{ lede }}</p>
    </div>
    <div v-if="$slots.default" class="head-actions"><slot /></div>
  </header>
</template>

<style scoped>
.head-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
</style>

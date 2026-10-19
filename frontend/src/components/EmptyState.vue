<script setup>
import Icon from './Icon.vue';
defineProps({
  icon: { type: String, default: 'leaf' },
  title: { type: String, required: true },
  detail: { type: String, default: '' },
});
</script>

<template>
  <div class="empty">
    <Icon :name="icon" :size="22" />
    <h3>{{ title }}</h3>
    <p v-if="detail">{{ detail }}</p>
    <slot />
  </div>
</template>

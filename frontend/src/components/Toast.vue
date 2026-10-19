<script setup>
import { app } from '../stores/app.js';
</script>

<template>
  <div class="toast" :class="{ show: app.toast.visible }" role="status" aria-live="polite">
    {{ app.toast.message }}
  </div>
</template>

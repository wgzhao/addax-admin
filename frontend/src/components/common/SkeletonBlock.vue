<template>
  <div
    class="skeleton-block"
    :style="{ minHeight: `${height}px` }"
    aria-busy="true"
    aria-live="polite"
  >
    <v-skeleton-loader type="article" class="ds-skeleton" />
  </div>
</template>

<script setup lang="ts">
  withDefaults(defineProps<{ height?: number }>(), { height: 120 });
</script>

<style scoped>
  .skeleton-block {
    width: 100%;
    overflow: hidden;
    border-radius: 8px;
  }
</style>

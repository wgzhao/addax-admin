<script setup lang="ts">
import { APP_VERSION } from '../config/version';
</script>

<template>
  <div class="app-version text-caption text-disabled d-flex justify-end px-4 py-1">
    <span>Version: {{ APP_VERSION }}</span>
  </div>
</template>

<style scoped>
.app-version {
  font-size: 0.75rem;
}
</style>

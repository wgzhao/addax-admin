<template>
  <v-card flat class="ds-card page-header-card">
    <v-card-text class="ds-card__content">
      <div class="page-header flex-wrap">
        <div class="page-header__text">
          <h1 class="page-title">{{ title }}</h1>
          <p v-if="subtitle" class="page-subtitle">{{ subtitle }}</p>
        </div>
        <div v-if="$slots.default" class="ds-actions">
          <slot />
        </div>
      </div>
    </v-card-text>
  </v-card>
</template>

<script setup lang="ts">
  defineProps<{ title: string; subtitle?: string }>();
</script>

<style scoped>
  .page-header__text {
    min-width: 0;
  }

  .page-title {
    margin: 0;
  }

  .page-subtitle {
    margin: 2px 0 0;
  }
</style>

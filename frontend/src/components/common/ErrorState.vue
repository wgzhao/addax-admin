<template>
  <div class="error-state" role="alert">
    <div class="error-icon">
      <v-icon size="22">mdi-alert-circle-outline</v-icon>
    </div>
    <div class="error-copy">
      <div class="error-title">{{ title }}</div>
      <div v-if="message" class="error-message">{{ message }}</div>
    </div>
    <v-btn v-if="retryLabel" size="small" variant="tonal" @click="$emit('retry')">
      {{ retryLabel }}
    </v-btn>
    <v-btn v-if="helpUrl" size="small" variant="text" :href="helpUrl" target="_blank" rel="noopener">
      帮助
    </v-btn>
  </div>
</template>

<script setup lang="ts">
  withDefaults(
    defineProps<{
      title?: string;
      message?: string;
      retryLabel?: string;
      helpUrl?: string;
    }>(),
    {
      title: '加载失败',
      message: '',
      retryLabel: '重试',
      helpUrl: '',
    }
  );

  defineEmits<{ retry: [] }>();
</script>

<style scoped>
  .error-state {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 14px 16px;
    border: 1px solid rgba(var(--v-theme-error), 0.24);
    border-radius: 10px;
    background: rgba(var(--v-theme-error), 0.05);
  }

  .error-icon {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 34px;
    height: 34px;
    border-radius: 9px;
    background: rgba(var(--v-theme-error), 0.1);
    color: rgb(var(--v-theme-error));
    flex: none;
  }

  .error-copy {
    flex: 1 1 auto;
    min-width: 0;
  }

  .error-title {
    font-size: 13px;
    font-weight: 600;
    color: rgb(var(--v-theme-on-surface));
  }

  .error-message {
    margin-top: 2px;
    font-size: 12px;
    line-height: 1.5;
    color: var(--ds-text-2);
    word-break: break-word;
  }
</style>

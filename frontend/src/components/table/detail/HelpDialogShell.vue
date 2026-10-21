<template>
  <v-dialog v-model="open" :max-width="maxWidth" :aria-labelledby="titleId">
    <v-card class="help-dialog">
      <header class="help-dialog__header">
        <span class="help-dialog__icon">
          <v-icon size="20">{{ icon }}</v-icon>
        </span>
        <div class="help-dialog__heading">
          <h2 :id="titleId" class="help-dialog__title">{{ title }}</h2>
          <p v-if="subtitle" class="help-dialog__subtitle">{{ subtitle }}</p>
        </div>
        <v-btn
          icon="mdi-close"
          variant="text"
          size="small"
          aria-label="关闭"
          @click="open = false"
        />
      </header>

      <div class="help-dialog__body">
        <slot />
      </div>

      <footer class="help-dialog__footer">
        <span class="help-dialog__hint">
          <slot name="hint">按 Esc 或点击右上角关闭</slot>
        </span>
        <v-btn variant="text" @click="open = false">关闭</v-btn>
      </footer>
    </v-card>
  </v-dialog>
</template>

<script setup lang="ts">
  import { useId } from 'vue';

  defineProps<{
    title: string;
    subtitle?: string;
    icon: string;
    maxWidth?: number | string;
  }>();

  const open = defineModel<boolean>({ default: false });
  const titleId = useId();
</script>

<style scoped>
  .help-dialog {
    display: flex;
    flex-direction: column;
    max-height: min(86vh, 760px);
    overflow: hidden;
  }

  .help-dialog__header {
    display: flex;
    align-items: flex-start;
    gap: 12px;
    flex: none;
    padding: 18px 20px 16px;
  }

  .help-dialog__icon {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 38px;
    height: 38px;
    flex: none;
    border-radius: 12px;
    background: rgba(var(--v-theme-primary), 0.1);
    color: rgb(var(--v-theme-primary));
  }

  .help-dialog__heading {
    flex: 1;
    min-width: 0;
  }

  .help-dialog__title {
    margin: 0;
    color: rgb(var(--v-theme-on-surface));
    font-size: 1.1rem;
    font-weight: 700;
    line-height: 1.4;
  }

  .help-dialog__subtitle {
    margin: 3px 0 0;
    color: var(--ds-text-2);
    font-size: 0.82rem;
    line-height: 1.55;
  }

  .help-dialog__body {
    flex: 1 1 auto;
    min-height: 0;
    padding: 18px 20px 22px;
    overflow-y: auto;
    border-top: 1px solid var(--ds-border-subtle);
    border-bottom: 1px solid var(--ds-border-subtle);
  }

  .help-dialog__footer {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    flex: none;
    padding: 10px 16px 10px 20px;
    background: var(--ds-surface-raised);
  }

  .help-dialog__hint {
    color: var(--ds-text-3);
    font-size: 0.78rem;
  }

  @media (max-width: 760px) {
    .help-dialog {
      max-height: calc(100dvh - 24px);
    }

    .help-dialog__header {
      padding: 16px 16px 14px;
    }

    .help-dialog__body {
      padding: 16px;
    }

    .help-dialog__footer {
      padding: 8px 12px 8px 16px;
    }

    .help-dialog__hint {
      display: none;
    }
  }
</style>

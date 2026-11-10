<template>
  <section class="section-card" :class="{ 'section-card--flat': flat }">
    <header class="section-head">
      <div class="section-heading">
        <div class="section-title-row">
          <v-icon v-if="icon" size="18" class="section-icon">{{ icon }}</v-icon>
          <h2 class="section-title">{{ title }}</h2>
        </div>
        <p v-if="subtitle" class="section-subtitle">{{ subtitle }}</p>
      </div>
      <div v-if="$slots.actions" class="section-actions">
        <slot name="actions" />
      </div>
    </header>

    <div class="section-body">
      <slot />
    </div>

    <footer v-if="$slots.footer" class="section-footer">
      <slot name="footer" />
    </footer>
  </section>
</template>

<script setup lang="ts">
  withDefaults(
    defineProps<{
      title: string;
      subtitle?: string;
      icon?: string;
      flat?: boolean;
    }>(),
    { subtitle: '', icon: '', flat: false }
  );
</script>

<style scoped>
  .section-card {
    display: flex;
    flex-direction: column;
    min-width: 0;
    border: 1px solid var(--ds-border-default);
    border-radius: 12px;
    background: var(--ds-surface-raised);
  }

  .section-card--flat {
    border-color: var(--ds-border-subtle);
    box-shadow: none;
  }

  .section-head {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 12px;
    padding: 14px 16px 0;
  }

  .section-heading {
    min-width: 0;
  }

  .section-title-row {
    display: flex;
    align-items: center;
    gap: 7px;
    min-width: 0;
  }

  .section-icon {
    color: var(--ds-text-3);
    flex: none;
  }

  .section-title {
    margin: 0;
    font-size: 15px;
    line-height: 1.4;
    font-weight: 600;
    color: rgb(var(--v-theme-on-surface));
  }

  .section-subtitle {
    margin: 3px 0 0;
    font-size: 12px;
    line-height: 1.5;
    color: var(--ds-text-3);
  }

  .section-actions {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    flex: none;
  }

  .section-body {
    flex: 1 1 auto;
    min-width: 0;
    padding: 12px 16px 16px;
  }

  .section-footer {
    border-top: 1px solid var(--ds-border-subtle);
    padding: 10px 16px;
    font-size: 12px;
    color: var(--ds-text-3);
  }
</style>

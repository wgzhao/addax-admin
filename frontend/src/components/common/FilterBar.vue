<template>
  <div class="filter-bar">
    <div class="filter-group">
      <span class="filter-label">{{ label }}</span>
      <v-btn-toggle
        :model-value="modelValue"
        mandatory
        density="compact"
        variant="outlined"
        divided
        class="filter-toggle"
        @update:model-value="onChange"
      >
        <v-btn v-for="range in ranges" :key="range.value" :value="range.value" size="small">
          {{ range.label }}
        </v-btn>
      </v-btn-toggle>
    </div>

    <v-chip v-if="envLabel" size="small" variant="tonal" color="primary" class="filter-env">
      {{ envLabel }}
    </v-chip>

    <v-spacer />

    <span v-if="updatedAt" class="filter-updated">数据截至 {{ updatedAt }}</span>
    <v-btn
      icon
      size="small"
      variant="text"
      :loading="loading"
      aria-label="刷新数据"
      title="刷新数据"
      @click="$emit('refresh')"
    >
      <v-icon size="18">mdi-refresh</v-icon>
    </v-btn>
  </div>
</template>

<script setup lang="ts">
  withDefaults(
    defineProps<{
      modelValue: number;
      ranges: { label: string; value: number }[];
      label?: string;
      updatedAt?: string;
      envLabel?: string;
      loading?: boolean;
    }>(),
    { label: '趋势范围', updatedAt: '', envLabel: '', loading: false }
  );

  const emit = defineEmits<{
    'update:modelValue': [value: number];
    refresh: [];
  }>();

  const onChange = (value: unknown) => {
    if (typeof value === 'number') emit('update:modelValue', value);
  };
</script>

<style scoped>
  .filter-bar {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 10px 14px;
    padding: 8px 10px;
    border: 1px solid var(--ds-border-subtle);
    border-radius: 10px;
    background: rgba(var(--v-theme-on-surface), 0.02);
  }

  .filter-group {
    display: inline-flex;
    align-items: center;
    gap: 8px;
  }

  .filter-label {
    font-size: 12px;
    color: var(--ds-text-3);
    white-space: nowrap;
  }

  .filter-toggle {
    border-radius: 8px;
  }

  .filter-env {
    letter-spacing: 0;
  }

  .filter-updated {
    font-size: 12px;
    color: var(--ds-text-3);
    font-variant-numeric: tabular-nums;
  }
</style>

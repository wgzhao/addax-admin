<template>
  <div v-if="chips.length" class="filter-chips">
    <span class="filter-chips__count">共 {{ total }} 条</span>
    <v-chip
      v-for="chip in chips"
      :key="chip.key"
      size="small"
      closable
      variant="tonal"
      @click:close="clear(chip.key)"
    >
      <span class="filter-chips__label">{{ chip.label }}</span>
      {{ chip.text }}
    </v-chip>
    <v-btn
      size="small"
      variant="text"
      prepend-icon="mdi-filter-remove-outline"
      @click="clearAll"
    >
      清除全部
    </v-btn>
  </div>
</template>

<script setup lang="ts">
  import { computed } from 'vue';
  import {
    buildFilterChips,
    clearFilterChip,
    type ChipKey,
  } from '@/components/table/filter-fields';
  import { useTableFilters } from '@/composables/useTableFilters';

  interface Option {
    value: number;
    label: string;
  }

  const props = defineProps<{
    sources: Option[];
    targets: Option[];
    total: number;
  }>();

  const emit = defineEmits<{ cleared: [] }>();

  const { filters, reset } = useTableFilters();

  const chips = computed(() =>
    buildFilterChips(filters, {
      sourceNames: new Map(props.sources.map(item => [item.value, item.label])),
      targetNames: new Map(props.targets.map(item => [item.value, item.label])),
    })
  );

  function clear(key: ChipKey): void {
    clearFilterChip(filters, key);
    emit('cleared');
  }

  function clearAll(): void {
    reset();
    emit('cleared');
  }
</script>

<style scoped>
  .filter-chips {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 8px;
    margin-top: 10px;
  }

  .filter-chips__count {
    margin-right: 4px;
    font-size: 13px;
    color: rgb(var(--v-theme-on-surface-variant));
    font-variant-numeric: tabular-nums;
  }

  .filter-chips__label {
    margin-right: 6px;
    color: rgb(var(--v-theme-on-surface-variant));
  }
</style>

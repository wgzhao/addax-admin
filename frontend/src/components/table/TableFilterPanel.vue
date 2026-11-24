<template>
  <v-card flat class="filter-panel">
    <v-card-text class="filter-panel__body">
      <section class="filter-group">
        <h4 class="filter-group__title">目标端</h4>
        <div class="filter-group__row">
          <span class="filter-group__label">目标端</span>
          <v-select
            v-model="filters.targetIds"
            :items="targets"
            item-title="label"
            item-value="value"
            multiple
            chips
            closable-chips
            density="compact"
            variant="outlined"
            hide-details
            placeholder="不限"
          />
        </div>
        <div class="filter-group__row">
          <span class="filter-group__label">目标库</span>
          <v-text-field
            v-model="filters.targetDb"
            density="compact"
            variant="outlined"
            hide-details
            clearable
            placeholder="包含，如 dw_"
          />
        </div>
        <div class="filter-group__row">
          <span class="filter-group__label">目标端状态</span>
          <v-btn-toggle v-model="filters.targetEnabled" mandatory density="compact" variant="outlined">
            <v-btn v-for="option in TRISTATE_OPTIONS" :key="option.value" :value="option.value" size="small">
              {{ option.label }}
            </v-btn>
          </v-btn-toggle>
        </div>
      </section>

      <section class="filter-group">
        <h4 class="filter-group__title">采集源</h4>
        <div class="filter-group__row">
          <span class="filter-group__label">采集源</span>
          <v-select
            v-model="filters.sourceIds"
            :items="sources"
            item-title="label"
            item-value="value"
            multiple
            chips
            closable-chips
            density="compact"
            variant="outlined"
            hide-details
            placeholder="不限"
          />
        </div>
        <div class="filter-group__row">
          <span class="filter-group__label">源状态</span>
          <v-btn-toggle v-model="filters.sourceEnabled" mandatory density="compact" variant="outlined">
            <v-btn v-for="option in TRISTATE_OPTIONS" :key="option.value" :value="option.value" size="small">
              {{ option.label }}
            </v-btn>
          </v-btn-toggle>
        </div>
      </section>

      <section class="filter-group">
        <h4 class="filter-group__title">采集行为</h4>
        <div class="filter-group__row">
          <span class="filter-group__label">写入模式</span>
          <v-select
            v-model="filters.writeModes"
            :items="WRITE_MODE_OPTIONS"
            item-title="label"
            item-value="value"
            multiple
            chips
            closable-chips
            density="compact"
            variant="outlined"
            hide-details
            placeholder="不限"
          />
        </div>
        <div class="filter-group__row">
          <span class="filter-group__label">结构管理</span>
          <v-select
            v-model="filters.schemaModes"
            :items="SCHEMA_MODE_OPTIONS"
            item-title="label"
            item-value="value"
            multiple
            chips
            closable-chips
            density="compact"
            variant="outlined"
            hide-details
            placeholder="不限"
          />
        </div>
        <div class="filter-group__row">
          <span class="filter-group__label">耗时(s)</span>
          <div class="filter-group__range">
            <v-text-field
              v-model="filters.durationFrom"
              type="number"
              min="0"
              step="1"
              label="最短"
              suffix="秒"
              density="compact"
              variant="outlined"
              hide-details
              clearable
              @click:clear="filters.durationFrom = ''"
            />
            <span class="filter-group__tilde">~</span>
            <v-text-field
              v-model="filters.durationTo"
              type="number"
              min="0"
              step="1"
              label="最长"
              suffix="秒"
              density="compact"
              variant="outlined"
              hide-details
              clearable
              @click:clear="filters.durationTo = ''"
            />
          </div>
        </div>
      </section>

      <section class="filter-group">
        <h4 class="filter-group__title">时间范围</h4>
        <div v-for="range in DATE_RANGES" :key="range.key" class="filter-group__row">
          <span class="filter-group__label">{{ range.label }}</span>
          <div class="filter-group__range">
            <!-- No placeholder: native date inputs ignore it, and an empty field must read as
                 "not set" rather than as a value. The muted empty class does that; chips and the
                 clear icon confirm which ranges the user actually picked. -->
            <v-text-field
              v-model="filters[range.from]"
              type="date"
              density="compact"
              variant="outlined"
              hide-details
              clearable
              :aria-label="`${range.label}起始日期`"
              :class="filters[range.from] ? 'filter-date--filled' : 'filter-date--empty'"
              @click:clear="filters[range.from] = ''"
            />
            <span class="filter-group__tilde">~</span>
            <v-text-field
              v-model="filters[range.to]"
              type="date"
              density="compact"
              variant="outlined"
              hide-details
              clearable
              :aria-label="`${range.label}结束日期`"
              :class="filters[range.to] ? 'filter-date--filled' : 'filter-date--empty'"
              @click:clear="filters[range.to] = ''"
            />
          </div>
        </div>
      </section>
    </v-card-text>
    <v-divider />
    <v-card-actions class="filter-panel__actions">
      <v-btn variant="text" @click="reset">重置</v-btn>
      <v-spacer />
      <v-btn color="primary" variant="flat" @click="$emit('close')">完成</v-btn>
    </v-card-actions>
  </v-card>
</template>

<script setup lang="ts">
  import {
    SCHEMA_MODE_OPTIONS,
    TRISTATE_OPTIONS,
    WRITE_MODE_OPTIONS,
  } from '@/components/table/filter-fields';
  import { useTableFilters } from '@/composables/useTableFilters';

  interface Option {
    value: number;
    label: string;
  }

  defineProps<{
    sources: Option[];
    targets: Option[];
  }>();

  defineEmits<{ close: [] }>();

  const { filters, reset } = useTableFilters();

  const DATE_RANGES = [
    { key: 'end', label: '完成时间', from: 'endFrom', to: 'endTo' },
    { key: 'created', label: '创建时间', from: 'createdFrom', to: 'createdTo' },
    { key: 'updated', label: '更新时间', from: 'updatedFrom', to: 'updatedTo' },
  ] as const;
</script>

<style scoped>
  .filter-panel {
    width: 560px;
    max-width: 92vw;
  }

  .filter-panel__body {
    display: flex;
    flex-direction: column;
    gap: 18px;
    max-height: min(70vh, 640px);
    overflow-y: auto;
    padding: 16px 20px;
  }

  .filter-group {
    display: flex;
    flex-direction: column;
    gap: 10px;
  }

  .filter-group__title {
    margin: 0;
    font-size: 12px;
    font-weight: 600;
    letter-spacing: 0.04em;
    text-transform: uppercase;
    color: rgb(var(--v-theme-on-surface-variant));
  }

  .filter-group__row {
    display: grid;
    grid-template-columns: 84px minmax(0, 1fr);
    align-items: center;
    gap: 12px;
  }

  .filter-group__label {
    font-size: 13px;
    color: rgb(var(--v-theme-on-surface-variant));
  }

  .filter-group__range {
    display: grid;
    grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr);
    align-items: center;
    gap: 8px;
  }

  .filter-group__tilde {
    color: rgb(var(--v-theme-on-surface-variant));
  }

  /* The browser's empty date text inherits the input color, so a chosen date and "yyyy/mm/dd"
     look identical. Mute the empty state and make a chosen date heavier. */
  .filter-date--empty :deep(input) {
    color: rgb(var(--v-theme-on-surface-variant));
    font-weight: 400;
  }

  .filter-date--filled :deep(input) {
    color: rgb(var(--v-theme-on-surface));
    font-weight: 600;
  }

  .filter-panel__actions {
    padding: 8px 16px;
  }
</style>

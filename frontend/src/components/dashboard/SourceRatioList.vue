<template>
  <div v-if="visibleRows.length" class="progress-stack">
    <div
      v-for="ratio in visibleRows"
      :key="ratio.source_name"
      class="progress-item"
      :class="{ 'progress-item--alert': failed(ratio) > 0 }"
      :title="tooltip(ratio)"
    >
      <div class="progress-header">
        <span class="progress-name">{{ ratio.source_name }}</span>
        <span class="progress-metrics">
          <span v-if="total(ratio) > 0" class="progress-count">
            {{ done(ratio) }} / {{ total(ratio) }}
          </span>
          <span v-if="gap(ratio) > 0" class="progress-gap">{{ gapText(ratio) }}</span>
          <span class="progress-value" :class="toneClass(ratio)">{{ percentText(ratio) }}</span>
        </span>
      </div>
      <div
        class="progress-track"
        role="progressbar"
        :aria-label="`${ratio.source_name} 完成率`"
        :aria-valuenow="percent(ratio)"
        aria-valuemin="0"
        aria-valuemax="100"
      >
        <span class="progress-seg progress-seg--done" :style="{ flexGrow: done(ratio) }" />
        <span
          v-if="failed(ratio) > 0"
          class="progress-seg progress-seg--failed"
          :style="{ flexGrow: failed(ratio) }"
        />
        <span
          v-if="rest(ratio) > 0"
          class="progress-seg progress-seg--rest"
          :style="{ flexGrow: rest(ratio) }"
        />
      </div>
    </div>
  </div>
  <div v-else class="progress-empty">{{ emptyText || '暂无采集源数据' }}</div>
</template>

<script setup lang="ts">
  import { computed } from 'vue';

  export interface SourceRatio {
    source_name: string;
    pct?: number;
    total?: number;
    done?: number;
    failed?: number;
    running?: number;
    pending?: number;
  }

  const props = defineProps<{
    ratios: SourceRatio[];
    limit?: number;
    emptyText?: string;
  }>();

  const total = (ratio: SourceRatio) => Number(ratio.total ?? 0);
  const done = (ratio: SourceRatio) => Number(ratio.done ?? 0);
  const failed = (ratio: SourceRatio) => Number(ratio.failed ?? 0);
  const gap = (ratio: SourceRatio) => Math.max(total(ratio) - done(ratio), 0);
  const rest = (ratio: SourceRatio) => Math.max(total(ratio) - done(ratio) - failed(ratio), 0);

  // Why: a source with a single missing table must never read as 100%, so floor to
  // one decimal and treat the counts as the source of truth
  const percent = (ratio: SourceRatio) => {
    const count = total(ratio);
    if (!count) return Number(ratio.pct ?? 0);
    if (done(ratio) >= count) return 100;
    return Math.floor((done(ratio) / count) * 1000) / 10;
  };

  const percentText = (ratio: SourceRatio) => {
    if (!total(ratio)) return '—';
    if (done(ratio) >= total(ratio)) return '100%';
    const value = percent(ratio);
    if (value === 0) return done(ratio) === 0 ? '0%' : '＜0.1%';
    return `${value.toFixed(1)}%`;
  };

  const toneClass = (ratio: SourceRatio) => {
    if (!total(ratio)) return 'is-muted';
    if (gap(ratio) === 0) return 'is-complete';
    if (failed(ratio) > 0) return 'is-failed';
    return 'is-incomplete';
  };

  const gapText = (ratio: SourceRatio) => {
    if (failed(ratio) > 0) return `${failed(ratio)} 张失败`;
    return `差 ${gap(ratio)} 张`;
  };

  const tooltip = (ratio: SourceRatio) => {
    if (!total(ratio)) return ratio.source_name;
    return `${ratio.source_name}：已完成 ${done(ratio)} / ${total(ratio)}，失败 ${failed(
      ratio
    )}，未采集 ${Number(ratio.pending ?? 0)}，排队/进行中 ${Number(ratio.running ?? 0)}`;
  };

  // Why: the overview deliberately shows the worst sources first, so a stalled source
  // can never hide behind a long tail of healthy ones
  const sorted = computed(() =>
    [...props.ratios].sort(
      (a, b) =>
        percent(a) - percent(b) || gap(b) - gap(a) || a.source_name.localeCompare(b.source_name)
    )
  );

  const visibleRows = computed(() =>
    props.limit && props.limit > 0 ? sorted.value.slice(0, props.limit) : sorted.value
  );
</script>

<style scoped>
  .progress-stack {
    display: flex;
    flex-direction: column;
    gap: 6px;
  }

  .progress-empty {
    padding: 24px 0;
    text-align: center;
    font-size: 13px;
    color: var(--ds-text-2);
  }

  .progress-item {
    padding: 6px 10px;
    border-radius: 12px;
    background: rgba(var(--v-theme-on-surface), 0.04);
    border: 1px solid rgba(var(--v-theme-on-surface), 0.06);
  }

  .progress-item--alert {
    border-color: rgba(var(--v-theme-error), 0.35);
    background: rgba(var(--v-theme-error), 0.05);
  }

  .progress-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 4px;
    gap: 8px;
  }

  .progress-name {
    font-weight: 600;
    color: rgb(var(--v-theme-on-surface));
    font-size: 0.85rem;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .progress-metrics {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    font-variant-numeric: tabular-nums;
    white-space: nowrap;
    flex: none;
  }

  .progress-count {
    color: var(--ds-text-3);
    font-size: 12px;
  }

  .progress-gap {
    color: rgb(var(--v-theme-warning));
    font-size: 11px;
  }

  .progress-item--alert .progress-gap {
    color: rgb(var(--v-theme-error));
  }

  .progress-value {
    font-weight: 600;
    color: rgba(var(--v-theme-on-surface), 0.7);
    font-size: 0.8rem;
  }

  .progress-value.is-complete {
    color: rgb(var(--v-theme-success));
  }

  .progress-value.is-incomplete {
    color: rgb(var(--v-theme-warning));
  }

  .progress-value.is-failed {
    color: rgb(var(--v-theme-error));
  }

  .progress-value.is-muted {
    color: var(--ds-text-3);
  }

  .progress-track {
    display: flex;
    height: 10px;
    border-radius: 999px;
    background: rgba(var(--v-theme-on-surface), 0.12);
    overflow: hidden;
  }

  .progress-seg {
    flex-basis: 0;
    min-width: 0;
  }

  .progress-seg--done {
    background: rgb(var(--v-theme-success));
  }

  .progress-seg--failed {
    background: rgb(var(--v-theme-error));
    /* Why: one failed table in a large source must stay visible on a 500px bar */
    min-width: 4px;
  }

  .progress-seg--rest {
    background: rgba(var(--v-theme-on-surface), 0.2);
    /* Why: keep a visible tail so "almost done" never looks like a full bar */
    min-width: 3px;
  }
</style>

<template>
  <div class="stat-tile" :class="{ 'stat-tile--loading': loading }">
    <div class="stat-head">
      <span class="stat-label">{{ label }}</span>
      <v-icon v-if="icon" size="18" class="stat-icon">{{ icon }}</v-icon>
    </div>

    <div v-if="loading" class="stat-loading">
      <v-skeleton-loader type="text" class="ds-skeleton" />
    </div>
    <template v-else>
      <div class="stat-value-row">
        <span class="stat-value">{{ value }}</span>
        <span v-if="unit" class="stat-unit">{{ unit }}</span>
      </div>
      <div v-if="hasDelta || hint" class="stat-foot">
        <span v-if="hasDelta" class="stat-delta" :class="deltaClass">
          <v-icon size="14">{{ deltaIcon }}</v-icon>
          {{ deltaText }}
        </span>
        <span v-if="hint" class="stat-hint">{{ hint }}</span>
      </div>
      <svg
        v-if="sparkline && sparkline.length > 1"
        class="stat-spark"
        viewBox="0 0 100 28"
        preserveAspectRatio="none"
        aria-hidden="true"
      >
        <polyline
          :points="sparkPoints"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
          stroke-linecap="round"
          stroke-linejoin="round"
          vector-effect="non-scaling-stroke"
        />
      </svg>
    </template>
  </div>
</template>

<script setup lang="ts">
  import { computed } from 'vue';

  const props = withDefaults(
    defineProps<{
      label: string;
      value: string | number;
      unit?: string;
      delta?: number | null;
      deltaLabel?: string;
      deltaTone?: 'auto' | 'up-good' | 'down-good';
      hint?: string;
      icon?: string;
      loading?: boolean;
      sparkline?: number[];
    }>(),
    {
      delta: null,
      deltaLabel: '',
      deltaTone: 'auto',
      hint: '',
      icon: '',
      loading: false,
      sparkline: undefined,
    }
  );

  const hasDelta = computed(() => props.delta !== null && Number.isFinite(Number(props.delta)));
  const deltaValue = computed(() => Number(props.delta ?? 0));
  const deltaText = computed(() => {
    const sign = deltaValue.value > 0 ? '+' : '';
    const label = props.deltaLabel ? ` ${props.deltaLabel}` : '';
    return `${sign}${deltaValue.value.toFixed(1)}%${label}`;
  });
  const deltaIcon = computed(() => {
    if (deltaValue.value === 0) return 'mdi-minus';
    return deltaValue.value > 0 ? 'mdi-arrow-up' : 'mdi-arrow-down';
  });
  const deltaClass = computed(() => {
    if (deltaValue.value === 0) return 'stat-delta--flat';
    const up = deltaValue.value > 0;
    const good =
      props.deltaTone === 'up-good'
        ? up
        : props.deltaTone === 'down-good'
        ? !up
        : up;
    return good ? 'stat-delta--good' : 'stat-delta--bad';
  });

  const sparkPoints = computed(() => {
    const values = props.sparkline ?? [];
    if (values.length < 2) return '';
    const min = Math.min(...values);
    const max = Math.max(...values);
    const span = max - min || 1;
    return values
      .map((value, index) => {
        const x = (index / (values.length - 1)) * 100;
        const y = 26 - ((value - min) / span) * 24;
        return `${x.toFixed(2)},${y.toFixed(2)}`;
      })
      .join(' ');
  });
</script>

<style scoped>
  .stat-tile {
    display: flex;
    flex-direction: column;
    gap: 6px;
    min-height: 116px;
    padding: 14px 16px 12px;
    border: 1px solid var(--ds-border-default);
    border-radius: 12px;
    background: var(--ds-surface-raised);
    transition: border-color var(--ds-duration-fast) var(--ds-ease);
  }

  .stat-tile:hover {
    border-color: var(--ds-border-strong);
  }

  .stat-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
  }

  .stat-label {
    font-size: 13px;
    font-weight: 500;
    color: var(--ds-text-2);
  }

  .stat-icon {
    color: var(--ds-text-3);
  }

  .stat-value-row {
    display: flex;
    align-items: baseline;
    gap: 6px;
    min-width: 0;
  }

  .stat-value {
    font-size: 28px;
    line-height: 1.1;
    font-weight: 700;
    letter-spacing: -0.02em;
    color: rgb(var(--v-theme-on-surface));
    white-space: nowrap;
  }

  .stat-unit {
    font-size: 14px;
    font-weight: 600;
    color: var(--ds-text-2);
  }

  .stat-foot {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 8px;
    min-height: 18px;
  }

  .stat-delta {
    display: inline-flex;
    align-items: center;
    gap: 2px;
    font-size: 12px;
    font-weight: 600;
    font-variant-numeric: tabular-nums;
  }

  .stat-delta--good {
    color: rgb(var(--v-theme-success));
  }

  .stat-delta--bad {
    color: rgb(var(--v-theme-error));
  }

  .stat-delta--flat {
    color: var(--ds-text-3);
  }

  .stat-hint {
    font-size: 11px;
    color: var(--ds-text-3);
  }

  .stat-spark {
    width: 100%;
    height: 26px;
    margin-top: auto;
    color: rgba(var(--v-theme-primary), 0.55);
  }

  .stat-loading {
    padding-top: 4px;
  }
</style>

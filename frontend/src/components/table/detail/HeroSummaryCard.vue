<template>
  <v-card flat class="ds-card hero-card">
    <v-card-text class="hero-card__content">
      <div class="hero-main">
        <div class="hero-kicker">源表</div>
        <div class="hero-title-row">
          <div class="hero-title">{{ sourceIdentity }}</div>
          <v-chip size="small" :color="statusColor" variant="tonal">{{ statusLabel }}</v-chip>
        </div>
      </div>
      <div class="hero-route">
        <div class="hero-route__label">目标映射</div>
        <div class="hero-route__value">{{ targetIdentity }}</div>
      </div>
    </v-card-text>
  </v-card>

  <div class="summary-grid">
    <div class="summary-card">
      <span class="summary-label">源系统</span>
      <strong>{{ sourceSystemLabel }}</strong>
    </div>
    <div class="summary-card">
      <span class="summary-label">目标端</span>
      <strong>{{ targetLabel }}</strong>
    </div>
    <div class="summary-card">
      <span class="summary-label">调度时间</span>
      <strong>{{ scheduleLabel }}</strong>
    </div>
    <div class="summary-card">
      <span class="summary-label">最近运行</span>
      <strong>{{ latestRunLabel }}</strong>
    </div>
  </div>
</template>

<script setup lang="ts">
  import { computed } from 'vue';
  import type { VEtlWithSource } from '@/types/database';
  import {
    sourceIdentity as sourceIdentityOf,
    targetIdentity as targetIdentityOf,
    sourceSystemLabel as sourceSystemLabelOf,
    targetLabel as targetLabelOf,
    scheduleLabel as scheduleLabelOf,
    latestRunLabel as latestRunLabelOf,
    statusLabel as statusLabelOf,
    statusColor as statusColorOf,
  } from './table-labels';

  const props = defineProps<{
    targetOptions: { label: string; value: number }[];
  }>();

  // Object-level model: mutate fields only, never assign the whole object —
  // the parent replaces it on sync and the model ref follows the prop.
  const table = defineModel<VEtlWithSource>('table', { required: true });

  const sourceIdentity = computed(() => sourceIdentityOf(table.value));
  const targetIdentity = computed(() => targetIdentityOf(table.value));
  const sourceSystemLabel = computed(() => sourceSystemLabelOf(table.value));
  const targetLabel = computed(() => targetLabelOf(table.value, props.targetOptions));
  const scheduleLabel = computed(() => scheduleLabelOf(table.value));
  const latestRunLabel = computed(() => latestRunLabelOf(table.value));
  const statusLabel = computed(() => statusLabelOf(table.value));
  const statusColor = computed(() => statusColorOf(table.value));
</script>

<style scoped>
  .hero-card__content {
    display: flex;
    align-items: stretch;
    justify-content: space-between;
    gap: 18px;
    padding: 20px;
    background: linear-gradient(180deg, rgba(var(--v-theme-primary), 0.06), transparent 86%);
  }

  .hero-main {
    flex: 1;
    min-width: 0;
  }

  .hero-kicker {
    font-size: 0.76rem;
    letter-spacing: 0.08em;
    text-transform: uppercase;
    color: rgba(var(--v-theme-primary), 0.9);
  }

  .hero-title-row {
    display: flex;
    align-items: center;
    gap: 10px;
    flex-wrap: wrap;
    margin-top: 6px;
  }

  .hero-title {
    font-size: 1.16rem;
    font-weight: 700;
    color: rgb(var(--v-theme-on-surface));
    word-break: break-word;
  }

  .hero-route {
    min-width: 260px;
    padding: 16px 18px;
    border-radius: 18px;
    border: 1px solid rgba(var(--v-theme-on-surface), 0.08);
    background: rgba(var(--v-theme-surface), 0.86);
  }

  .hero-route__label {
    font-size: 0.76rem;
    letter-spacing: 0.04em;
    text-transform: uppercase;
    color: rgba(var(--v-theme-on-surface), 0.56);
  }

  .hero-route__value {
    margin-top: 8px;
    font-family: 'SF Mono', 'Monaco', 'Inconsolata', 'Roboto Mono', 'Consolas', 'Courier New',
      monospace;
    font-size: 0.96rem;
    font-weight: 600;
    color: rgb(var(--v-theme-primary));
    word-break: break-word;
  }

  .summary-grid {
    display: grid;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: 12px;
  }

  .summary-card {
    padding: 14px 16px;
    border-radius: 16px;
    border: 1px solid rgba(var(--v-theme-on-surface), 0.08);
    background: linear-gradient(
        180deg,
        rgba(var(--v-theme-primary), 0.055),
        rgba(var(--v-theme-primary), 0.01)
      ),
      rgb(var(--v-theme-surface));
  }

  .summary-label {
    display: block;
    margin-bottom: 6px;
    font-size: 0.76rem;
    letter-spacing: 0.04em;
    text-transform: uppercase;
    color: rgba(var(--v-theme-on-surface), 0.56);
  }

  @media (max-width: 1180px) {
    .hero-card__content {
      flex-direction: column;
    }

    .summary-grid {
      grid-template-columns: 1fr;
    }

    .hero-route {
      min-width: 0;
    }
  }

  @media (max-width: 760px) {
    .hero-card__content {
      padding: 16px;
    }

    .summary-grid {
      grid-template-columns: 1fr;
    }
  }
</style>

<template>
  <section class="health-hero">
    <div class="health-main">
      <div class="health-label">今日采集健康度</div>
      <div class="health-value-row">
        <span class="health-value">{{ healthRate.toFixed(1) }}</span>
        <span class="health-unit">%</span>
        <v-chip
          :color="status.color"
          size="small"
          variant="tonal"
          class="health-status"
          :prepend-icon="status.icon"
        >
          {{ status.label }}
        </v-chip>
      </div>
      <div class="health-message">{{ status.message }}</div>
      <div class="health-counts">
        <span
          ><i class="dot dot--done" />已采集 <b>{{ totals.done }}</b></span
        >
        <span
          ><i class="dot dot--running" />采集中 <b>{{ totals.running }}</b></span
        >
        <span :class="{ 'health-counts--alert': totals.failed > 0 }">
          <i class="dot dot--failed" />失败 <b>{{ totals.failed }}</b>
        </span>
        <span
          ><i class="dot dot--pending" />未开始 <b>{{ totals.pending }}</b></span
        >
        <span class="health-total">共 {{ totals.total }} 张表</span>
      </div>
    </div>

    <div class="health-meter" aria-hidden="true">
      <div class="health-meter-head">
        <span>采集进度</span>
        <span>{{ totals.done }}/{{ totals.total }}</span>
      </div>
      <div class="health-track">
        <span class="seg seg--done" :style="{ flexGrow: Math.max(totals.done, 0) }" />
        <span class="seg seg--failed" :style="{ flexGrow: Math.max(totals.failed, 0) }" />
        <span
          class="seg seg--rest"
          :style="{ flexGrow: Math.max(totals.total - totals.done - totals.failed, 0) }"
        />
      </div>
      <div class="health-meter-foot">
        <span>成功率 {{ healthRate.toFixed(1) }}%</span>
        <span class="health-meter-note">按当前业务日采集表状态计算</span>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
  defineProps<{
    healthRate: number;
    totals: { total: number; done: number; failed: number; running: number; pending: number };
    status: { label: string; color: string; icon: string; message: string };
  }>();
</script>

<style scoped>
  .health-hero {
    display: grid;
    grid-template-columns: minmax(0, 1.25fr) minmax(280px, 0.75fr);
    gap: 20px;
    align-items: center;
    padding: 16px 20px;
    border: 1px solid var(--ds-border-default);
    border-radius: 14px;
    background:
      radial-gradient(circle at 8% 20%, rgba(var(--v-theme-primary), 0.08), transparent 42%),
      var(--ds-surface-raised);
  }

  .health-label {
    font-size: 13px;
    font-weight: 500;
    color: var(--ds-text-2);
  }

  .health-value-row {
    display: flex;
    align-items: baseline;
    gap: 4px;
    margin-top: 6px;
  }

  .health-value {
    font-size: 44px;
    line-height: 1;
    font-weight: 750;
    letter-spacing: -0.04em;
    color: rgb(var(--v-theme-on-surface));
  }

  .health-unit {
    font-size: 20px;
    font-weight: 650;
    color: var(--ds-text-2);
  }

  .health-status {
    margin-inline-start: 10px;
    letter-spacing: 0;
    transform: translateY(-4px);
  }

  .health-message {
    margin-top: 6px;
    font-size: 13px;
    color: var(--ds-text-2);
  }

  .health-counts {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 14px;
    margin-top: 12px;
    font-size: 12px;
    color: var(--ds-text-2);
  }

  .health-counts span {
    display: inline-flex;
    align-items: center;
    gap: 5px;
  }

  .health-counts b {
    color: rgb(var(--v-theme-on-surface));
    font-weight: 650;
    font-variant-numeric: tabular-nums;
  }

  .health-counts--alert b {
    color: rgb(var(--v-theme-error));
  }

  .health-total {
    color: var(--ds-text-3);
  }

  .dot {
    width: 7px;
    height: 7px;
    border-radius: 50%;
    display: inline-block;
  }

  .dot--done {
    background: rgb(var(--v-theme-success));
  }

  .dot--running {
    background: rgb(var(--v-theme-info));
  }

  .dot--failed {
    background: rgb(var(--v-theme-error));
  }

  .dot--pending {
    background: rgba(var(--v-theme-on-surface), 0.28);
  }

  .health-meter {
    padding: 14px 16px;
    border: 1px solid var(--ds-border-subtle);
    border-radius: 10px;
    background: rgba(var(--v-theme-on-surface), 0.025);
  }

  .health-meter-head,
  .health-meter-foot {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    font-size: 11px;
    color: var(--ds-text-3);
  }

  .health-meter-head {
    margin-bottom: 8px;
  }

  .health-meter-foot {
    margin-top: 8px;
  }

  .health-meter-note {
    text-align: right;
  }

  .health-track {
    display: flex;
    gap: 2px;
    height: 12px;
    border-radius: 999px;
    overflow: hidden;
    background: rgba(var(--v-theme-on-surface), 0.08);
  }

  .seg {
    flex-basis: 0;
    min-width: 0;
  }

  .seg--done {
    background: rgb(var(--v-theme-success));
  }

  .seg--failed {
    background: rgb(var(--v-theme-error));
    min-width: 4px;
  }

  .seg--rest {
    background: rgba(var(--v-theme-on-surface), 0.18);
    min-width: 3px;
  }

  @media (max-width: 960px) {
    .health-hero {
      grid-template-columns: 1fr;
      gap: 18px;
    }
  }
</style>

<template>
  <SectionCard title="规模与价值" subtitle="系统累计运行规模" icon="mdi-chart-donut-variant">
    <div class="value-grid">
      <div class="value-item">
        <span class="value-label">累计采集量</span>
        <span class="value-number">{{ totalData.value }}<small>{{ totalData.unit }}</small></span>
      </div>
      <div class="value-item">
        <span class="value-label">累计采集天数</span>
        <span class="value-number">{{ totalDays }}<small>天</small></span>
      </div>
      <div class="value-item">
        <span class="value-label">采集源 / 采集表</span>
        <span class="value-number">{{ sourceCount }}<small>源</small> · {{ coverage }}<small>表</small></span>
      </div>
      <div class="value-item">
        <span class="value-label">今日采集健康度</span>
        <span class="value-number">{{ healthRate.toFixed(1) }}<small>%</small></span>
      </div>
    </div>
    <template #footer>
      累计值来自采集统计；采集表覆盖率为有效表 / 全部表。
    </template>
  </SectionCard>
</template>

<script setup lang="ts">
  import SectionCard from '@/components/common/SectionCard.vue';
  import type { ValueWithUnit } from '@/utils/format';

  defineProps<{
    totalData: ValueWithUnit;
    totalDays: number;
    sourceCount: number;
    coverage: string;
    healthRate: number;
  }>();
</script>

<style scoped>
  .value-grid {
    display: grid;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: 16px;
  }

  .value-item {
    display: flex;
    flex-direction: column;
    gap: 5px;
    min-width: 0;
  }

  .value-label {
    font-size: 11px;
    color: var(--ds-text-3);
  }

  .value-number {
    font-size: 22px;
    line-height: 1.15;
    font-weight: 700;
    letter-spacing: -0.02em;
    color: rgb(var(--v-theme-on-surface));
    white-space: nowrap;
  }

  .value-number small {
    margin-inline-start: 4px;
    font-size: 12px;
    font-weight: 600;
    color: var(--ds-text-2);
  }

  @media (max-width: 960px) {
    .value-grid {
      grid-template-columns: repeat(2, minmax(0, 1fr));
    }
  }

  @media (max-width: 480px) {
    .value-grid {
      grid-template-columns: 1fr;
    }
  }
</style>

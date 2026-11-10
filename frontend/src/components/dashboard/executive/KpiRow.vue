<template>
  <div class="kpi-row">
    <StatTile
      label="今日数据量"
      :value="todayData.value"
      :unit="todayData.unit"
      :delta="volumeDelta"
      delta-label="较前 7 天"
      :sparkline="volumeSparkline"
      icon="mdi-database-arrow-up-outline"
      :loading="loading"
    />
    <StatTile
      label="今日完成表数"
      :value="`${done}/${total}`"
      :hint="`成功率 ${healthRate.toFixed(1)}%`"
      icon="mdi-table-check"
      :loading="loading"
    />
    <StatTile
      label="平均单表耗时"
      :value="avgTime.value"
      :unit="avgTime.unit"
      :delta="avgDelta"
      delta-label="较 30 天 P50"
      delta-tone="down-good"
      icon="mdi-timer-outline"
      :loading="loading"
    />
    <StatTile
      label="累计数据量"
      :value="totalData.value"
      :unit="totalData.unit"
      :hint="`覆盖 ${coverage} 张表 · ${totalDays} 个业务日`"
      icon="mdi-database-check-outline"
      :loading="loading"
    />
  </div>
</template>

<script setup lang="ts">
  import StatTile from '@/components/common/StatTile.vue';
  import type { ValueWithUnit } from '@/utils/format';

  withDefaults(
    defineProps<{
      todayData: ValueWithUnit;
      totalData: ValueWithUnit;
      avgTime: ValueWithUnit;
      avgDelta?: number | null;
      volumeDelta?: number | null;
      volumeSparkline: number[];
      totalDays: number;
      coverage: string;
      healthRate: number;
      done: number;
      total: number;
      loading?: boolean;
    }>(),
    { loading: false, avgDelta: null, volumeDelta: null }
  );
</script>

<style scoped>
  .kpi-row {
    display: grid;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: 10px;
  }

  @media (max-width: 1280px) {
    .kpi-row {
      grid-template-columns: repeat(2, minmax(0, 1fr));
    }
  }

  @media (max-width: 640px) {
    .kpi-row {
      grid-template-columns: 1fr;
    }
  }
</style>

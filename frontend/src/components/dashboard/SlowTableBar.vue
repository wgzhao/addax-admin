<template>
  <DashboardChartCard
    title="慢表 Top 10"
    subtitle="近 30 天平均单次耗时"
    :height="340"
    :state="state"
    :error="error"
  >
    <DashboardBarChart
      :labels="labels"
      :series="series"
      horizontal
      y-title="耗时"
      :value-formatter="formatSeconds"
      :tooltip-after="tooltipAfter"
    />
  </DashboardChartCard>
</template>

<script setup lang="ts">
  import { computed } from 'vue';
  import DashboardChartCard from './charts/DashboardChartCard.vue';
  import DashboardBarChart from './charts/DashboardBarChart.vue';
  import type { SlowTableRow } from '@/types/dashboard';
  import { useChartData } from '@/composables/useChartData';
  import { useChartTheme } from '@/composables/useChartTheme';
  import { dashboardService } from '@/service/dashboard-service';
  import { formatSeconds } from '@/utils/chart-colors';

  const { raw, state, error } = useChartData(() => dashboardService.fetchSlowTopTables());
  const { resolve } = useChartTheme();

  const rows = computed<SlowTableRow[]>(() => raw.value ?? []);

  const labelFor = (row: SlowTableRow) => {
    const full = `${row.source_db}.${row.source_table}`;
    return full.length > 30 ? `${full.slice(0, 29)}…` : full;
  };

  const labels = computed(() => rows.value.map(labelFor));

  const series = computed(() => [
    {
      key: 'avg_secs',
      label: '平均耗时',
      data: rows.value.map(r => r.avg_secs),
      color: resolve('primary', '#176FD6'),
    },
  ]);

  const tooltipAfter = (items: { dataIndex: number }[]): string[] => {
    const row = rows.value[items[0]?.dataIndex ?? -1];
    if (!row) return [];
    return [
      `最大耗时：${formatSeconds(row.max_secs)}`,
      `累计耗时：${formatSeconds(row.total_secs)} / ${row.days} 天`,
      `源：${row.source_name} (${row.source_code})`,
    ];
  };
</script>

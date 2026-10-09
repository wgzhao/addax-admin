<template>
  <DashboardChartCard :title="title" subtitle="近 5 天" :height="300" :state="state" :error="error">
    <DashboardBarChart :labels="labels" :series="series" :y-title="yTitle" />
  </DashboardChartCard>
</template>

<script setup lang="ts">
  import { computed } from 'vue';
  import DashboardChartCard from './charts/DashboardChartCard.vue';
  import DashboardBarChart from './charts/DashboardBarChart.vue';
  import type { ChartSeries } from '@/types/dashboard';
  import { useChartData } from '@/composables/useChartData';
  import { useChartTheme } from '@/composables/useChartTheme';
  import { dashboardService } from '@/service/dashboard-service';

  const props = withDefaults(defineProps<{ mode?: 'time' | 'data' }>(), { mode: 'time' });

  const { raw, state, error } = useChartData(() =>
    props.mode === 'time' ? dashboardService.fetchLast5dTime() : dashboardService.fetchLast5dData()
  );
  const { palette } = useChartTheme();

  const title = computed(() =>
    props.mode === 'time' ? '数据采集耗时分析' : '数据采集数量分析 (MB)'
  );
  const yTitle = computed(() => (props.mode === 'time' ? '耗时 (秒)' : '采集量 (MB)'));

  // 每个 biz_date 一个数据集，x 轴为采集源（保持原有图表形态）
  const labels = computed(() => raw.value?.[0]?.sources ?? []);

  const series = computed<ChartSeries[]>(() => {
    const isTime = props.mode === 'time';
    return (raw.value ?? []).map((row, index) => ({
      key: row.biz_date,
      label: row.biz_date,
      data: (isTime ? row.total_secs : row.total_bytes) ?? [],
      color: palette.value[index % palette.value.length],
    }));
  });
</script>

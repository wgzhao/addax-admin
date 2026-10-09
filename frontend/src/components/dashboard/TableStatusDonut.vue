<template>
  <DashboardChartCard
    title="当前采集状态分布"
    subtitle="实时"
    :height="300"
    :state="state"
    :error="error"
  >
    <DashboardDoughnutChart
      :labels="labels"
      :values="values"
      :colors="colors"
      :center-text="String(total)"
      center-label="采集表"
    />
  </DashboardChartCard>
</template>

<script setup lang="ts">
  import { computed } from 'vue';
  import DashboardChartCard from './charts/DashboardChartCard.vue';
  import DashboardDoughnutChart from './charts/DashboardDoughnutChart.vue';
  import { useChartData } from '@/composables/useChartData';
  import { useChartTheme } from '@/composables/useChartTheme';
  import { dashboardService } from '@/service/dashboard-service';
  import { STATUS_META, STATUS_ORDER } from '@/utils/chart-colors';

  const { raw, state, error } = useChartData(() => dashboardService.fetchTableStatusDist());
  const { resolve } = useChartTheme();

  // 按固定状态顺序排列，保证颜色稳定
  const ordered = computed(() => {
    const rows = raw.value ?? [];
    return STATUS_ORDER.map(status => {
      const row = rows.find(r => r.status === status);
      return { status, cnt: row?.cnt ?? 0, meta: STATUS_META[status] };
    }).filter(item => item.meta);
  });

  const labels = computed(() => ordered.value.map(item => `${item.meta.label} ${item.cnt}`));
  const values = computed(() => ordered.value.map(item => item.cnt));
  const colors = computed(() =>
    ordered.value.map(item => resolve(item.meta.token, item.meta.fallback))
  );
  const total = computed(() => values.value.reduce((sum, value) => sum + value, 0));
</script>

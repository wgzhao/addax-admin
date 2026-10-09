<template>
  <div class="doughnut-wrapper">
    <Doughnut v-if="ready" :data="chartData" :options="options" />
    <div v-if="centerText" class="doughnut-center">
      <div class="doughnut-center-value">{{ centerText }}</div>
      <div v-if="centerLabel" class="doughnut-center-label">{{ centerLabel }}</div>
    </div>
  </div>
</template>

<script setup lang="ts">
  import { computed } from 'vue';
  import type { ChartData, ChartOptions } from 'chart.js';
  import { useChartTheme } from '@/composables/useChartTheme';
  import { loadChartComponent } from '@/utils/dashboard-charts';

  const props = withDefaults(
    defineProps<{
      labels: string[];
      values: number[];
      colors: string[];
      centerText?: string;
      centerLabel?: string;
    }>(),
    {}
  );

  const Doughnut = loadChartComponent('Doughnut', chart => {
    chart.Chart.register(chart.DoughnutController, chart.ArcElement, chart.Tooltip, chart.Legend);
  });

  const { onSurface, tooltip } = useChartTheme();

  const ready = computed(() => props.labels.length > 0 && props.values.some(v => v > 0));

  const chartData = computed<ChartData<'doughnut'>>(() => ({
    labels: props.labels,
    datasets: [
      {
        data: props.values,
        backgroundColor: props.colors,
        borderColor: 'transparent',
        borderWidth: 1,
        hoverOffset: 4,
      },
    ],
  }));

  const options = computed<ChartOptions<'doughnut'>>(() => ({
    responsive: true,
    maintainAspectRatio: false,
    cutout: '62%',
    plugins: {
      legend: {
        position: 'right',
        labels: {
          color: onSurface.value,
          usePointStyle: true,
          boxWidth: 10,
          boxHeight: 10,
          padding: 12,
          font: { size: 12 },
        },
      },
      tooltip: tooltip.value,
    },
  }));
</script>

<style scoped>
  .doughnut-wrapper {
    position: relative;
    height: 100%;
    width: 100%;
  }

  .doughnut-center {
    position: absolute;
    top: 50%;
    left: 38%;
    transform: translate(-50%, -50%);
    text-align: center;
    pointer-events: none;
  }

  .doughnut-center-value {
    font-size: 22px;
    font-weight: 700;
    color: rgb(var(--v-theme-on-surface));
  }

  .doughnut-center-label {
    font-size: 11px;
    color: rgb(var(--v-theme-on-surface-muted));
  }
</style>

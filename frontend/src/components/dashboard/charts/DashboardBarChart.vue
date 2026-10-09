<template>
  <Bar v-if="ready" :data="chartData" :options="options" :plugins="barPlugins" />
</template>

<script setup lang="ts">
  import { computed } from 'vue';
  import type { ChartData, ChartDataset, ChartOptions, TooltipItem } from 'chart.js';
  import type { ChartSeries } from '@/types/dashboard';
  import { useChartTheme } from '@/composables/useChartTheme';
  import { loadChartComponent } from '@/utils/dashboard-charts';
  import { valueMarkerPlugin, type ValueMarker } from '@/utils/chart-plugins';

  const props = withDefaults(
    defineProps<{
      labels: string[];
      series: ChartSeries[];
      horizontal?: boolean;
      stacked?: boolean;
      yTitle?: string;
      beginAtZero?: boolean;
      valueFormatter?: (value: number) => string;
      markers?: ValueMarker[];
      tickFontSize?: number;
      tooltipAfter?: (items: TooltipItem<'bar'>[]) => string[];
    }>(),
    { horizontal: false, stacked: false, beginAtZero: true, tickFontSize: 11 }
  );

  const Bar = loadChartComponent('Bar', chart => {
    chart.Chart.register(
      chart.BarController,
      chart.BarElement,
      chart.LineElement,
      chart.PointElement,
      chart.CategoryScale,
      chart.LinearScale,
      chart.Tooltip,
      chart.Legend
    );
  });

  const { onSurface, grid, palette, tooltip, hexToRgba } = useChartTheme();

  const barPlugins = computed(() => (props.markers?.length ? [valueMarkerPlugin] : []));
  const ready = computed(() => props.labels.length > 0 && props.series.length > 0);

  const format = (value: unknown) =>
    props.valueFormatter ? props.valueFormatter(Number(value)) : String(value);

  const chartData = computed<ChartData<'bar'>>(() => ({
    labels: props.labels,
    datasets: props.series.map((s, index) => {
      const color = s.color ?? palette.value[index % palette.value.length];
      return {
        label: s.label,
        data: s.data as number[],
        backgroundColor: hexToRgba(color, props.stacked ? 0.85 : 0.7),
        borderColor: color,
        borderWidth: 1,
        borderRadius: props.horizontal ? 4 : 6,
        borderSkipped: false,
        maxBarThickness: props.horizontal ? 18 : 26,
      } as ChartDataset<'bar'>;
    }),
  }));

  const options = computed<ChartOptions<'bar'>>(() => ({
    indexAxis: props.horizontal ? ('y' as const) : ('x' as const),
    responsive: true,
    maintainAspectRatio: false,
    layout: { padding: { left: 10, right: 25, top: 25, bottom: 0 } },
    scales: {
      x: {
        stacked: props.stacked,
        grid: { display: false },
        ticks: {
          color: onSurface.value,
          font: { size: props.tickFontSize },
          maxRotation: props.horizontal ? 0 : 30,
          autoSkipPadding: 8,
        },
      },
      y: {
        stacked: props.stacked,
        beginAtZero: props.beginAtZero,
        grid: { color: grid.value },
        ticks: {
          color: onSurface.value,
          font: { size: props.tickFontSize },
          callback: value => format(value),
        },
        title: props.yTitle
          ? { display: true, text: props.yTitle, color: onSurface.value }
          : { display: false },
      },
    },
    plugins: {
      legend: {
        display: props.series.length > 1,
        position: 'bottom',
        labels: {
          color: onSurface.value,
          usePointStyle: true,
          boxWidth: 8,
          boxHeight: 8,
          padding: 12,
          font: { size: 11 },
        },
      },
      tooltip: {
        intersect: false,
        ...tooltip.value,
        footerColor: onSurface.value,
        callbacks: props.tooltipAfter ? { footer: props.tooltipAfter } : {},
      },
      ...(props.markers?.length ? { valueMarker: { markers: props.markers } } : {}),
    },
  }));
</script>

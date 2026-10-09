<template>
  <Line v-if="ready" :data="chartData" :options="options" />
</template>

<script setup lang="ts">
  import { computed } from 'vue';
  import type { ChartData, ChartOptions } from 'chart.js';
  import type { ChartSeries } from '@/types/dashboard';
  import { useChartTheme } from '@/composables/useChartTheme';
  import { loadChartComponent } from '@/utils/dashboard-charts';

  const props = withDefaults(
    defineProps<{
      labels: string[];
      series: ChartSeries[];
      yTitle?: string;
      y1Title?: string;
      stacked?: boolean;
      valueFormatter?: (value: number) => string;
      y1Formatter?: (value: number) => string;
      // 追加到 tooltip 底部的补充行（如绝对计数），由调用方的原始数据行提供
      extraTooltip?: (index: number) => string[];
    }>(),
    { stacked: false }
  );

  const Line = loadChartComponent('Line', chart => {
    chart.Chart.register(
      chart.LineController,
      chart.LineElement,
      chart.PointElement,
      chart.BarController,
      chart.BarElement,
      chart.CategoryScale,
      chart.LinearScale,
      chart.Filler,
      chart.Tooltip,
      chart.Legend
    );
  });

  const { onSurface, grid, palette, tooltip, hexToRgba } = useChartTheme();

  const ready = computed(() => props.labels.length > 0 && props.series.length > 0);
  const dualAxis = computed(() => props.series.some(s => s.yAxisID === 'y1'));

  const format = (formatter: ((value: number) => string) | undefined, value: unknown) =>
    formatter ? formatter(Number(value)) : String(value);

  const chartData = computed(() => {
    const datasets = props.series.map((s, index) => {
      const color = s.color ?? palette.value[index % palette.value.length];
      const common = {
        label: s.label,
        data: s.data as number[],
        borderColor: color,
        yAxisID: s.yAxisID ?? 'y',
      };
      if (s.type === 'bar') {
        return {
          ...common,
          type: 'bar',
          borderWidth: 1,
          backgroundColor: hexToRgba(color, 0.75),
          borderRadius: 4,
          maxBarThickness: 26,
        };
      }
      const areaAlpha = s.fill ? Math.min(0.2 + index * 0.06, 0.45) : 0.12;
      return {
        ...common,
        type: 'line',
        borderWidth: 2,
        backgroundColor: hexToRgba(color, areaAlpha),
        pointBackgroundColor: color,
        pointBorderColor: color,
        pointHoverBackgroundColor: color,
        pointHoverBorderColor: color,
        tension: 0.35,
        cubicInterpolationMode: 'monotone' as const,
        pointRadius: 3,
        pointHoverRadius: 5,
        pointHitRadius: 10,
        pointBorderWidth: 2,
        fill: s.fill ?? false,
      };
    });
    return {
      labels: props.labels,
      datasets,
    } as unknown as ChartData<'line'>;
  });

  const options = computed<ChartOptions<'line'>>(() => ({
    responsive: true,
    maintainAspectRatio: false,
    interaction: { mode: 'index', intersect: false },
    layout: { padding: { left: 10, right: 25, top: 25, bottom: 0 } },
    scales: {
      x: {
        grid: { display: false },
        ticks: { color: onSurface.value, maxRotation: 0, autoSkipPadding: 12 },
        stacked: props.stacked,
      },
      y: {
        beginAtZero: true,
        stacked: props.stacked,
        grid: { color: grid.value },
        ticks: {
          color: onSurface.value,
          callback: value => format(props.valueFormatter, value),
        },
        title: props.yTitle
          ? { display: true, text: props.yTitle, color: onSurface.value }
          : { display: false },
      },
      ...(dualAxis.value
        ? {
            y1: {
              position: 'right' as const,
              beginAtZero: true,
              grid: { drawOnChartArea: false },
              ticks: {
                color: onSurface.value,
                callback: (value: unknown) => format(props.y1Formatter, value),
              },
              title: props.y1Title
                ? { display: true, text: props.y1Title, color: onSurface.value }
                : { display: false },
            },
          }
        : {}),
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
          padding: 16,
          font: { size: 11 },
        },
      },
      tooltip: {
        intersect: false,
        ...tooltip.value,
        footerColor: onSurface.value,
        callbacks: props.extraTooltip
          ? {
              footer: items =>
                items.length > 0 ? props.extraTooltip?.(items[0].dataIndex) ?? [] : [],
            }
          : {},
      },
    },
  }));
</script>

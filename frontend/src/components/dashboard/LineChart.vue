<template>
  <div class="chart-wrapper" :class="{ 'dark-mode': isDark }">
    <Line v-if="chartReady" :data="areaData" :options="areaOptions" />
  </div>
</template>

<script setup lang="ts">
  import request from '@/utils/requests';
  import { ref, computed, onMounted, defineAsyncComponent } from 'vue';
  import { useTheme } from 'vuetify';

  const Line = defineAsyncComponent(async () => {
    const [{ Line }, chart] = await Promise.all([import('vue-chartjs'), import('chart.js')]);
    chart.Chart.register(
      chart.CategoryScale,
      chart.LinearScale,
      chart.PointElement,
      chart.LineElement,
      chart.Title,
      chart.Tooltip,
      chart.Legend,
      chart.Filler
    );
    return Line;
  });

  const vuetifyTheme = useTheme();
  const isDark = computed(() => vuetifyTheme.current.value.dark);
  const themeColors = computed(() => vuetifyTheme.current.value.colors);

  const normalizeColor = (value: unknown, fallback: string) => {
    return typeof value === 'string' ? value : fallback;
  };

  const getColor = (name: string, fallback: string) => {
    return normalizeColor(themeColors.value[name], fallback);
  };

  const hexToRgba = (hex: string, alpha: number) => {
    const normalized = hex.replace('#', '').trim();
    const fullHex =
      normalized.length === 3
        ? normalized
            .split('')
            .map(ch => ch + ch)
            .join('')
        : normalized;

    if (!/^[0-9a-fA-F]{6}$/.test(fullHex)) {
      return `rgba(11, 107, 203, ${alpha})`;
    }

    const intValue = Number.parseInt(fullHex, 16);
    const r = (intValue >> 16) & 255;
    const g = (intValue >> 8) & 255;
    const b = intValue & 255;
    return `rgba(${r}, ${g}, ${b}, ${alpha})`;
  };

  const last12MonthsEtlData = ref([]);
  const chartReady = ref(false);

  const areaData = computed(() => {
    const primary = getColor('primary', '#0B6BCB');
    const areaAlpha = isDark.value ? 0.2 : 0.12;

    return {
      labels: last12MonthsEtlData.value.map(item => item.month),
      datasets: [
        {
          label: 'Data Collection (GiB)',
          backgroundColor: hexToRgba(primary, areaAlpha),
          borderColor: primary,
          tension: 0.35,
          cubicInterpolationMode: 'monotone' as const,
          pointRadius: 3,
          pointBackgroundColor: primary,
          pointBorderColor: primary,
          pointHoverRadius: 5,
          pointHoverBackgroundColor: primary,
          pointHoverBorderColor: primary,
          pointHitRadius: 10,
          pointBorderWidth: 2,
          fill: true,
          data: last12MonthsEtlData.value.map(item => item.total_gb),
        },
      ],
    };
  });

  const areaOptions = computed(() => {
    const onSurface = getColor('on-surface', isDark.value ? '#F0F4F8' : '#171A1C');
    const surface = getColor('surface', isDark.value ? '#010409' : '#EFF2F5');

    return {
      maintainAspectRatio: false,
      layout: {
        padding: {
          left: 10,
          right: 25,
          top: 25,
          bottom: 0,
        },
      },
      scales: {
        x: {
          grid: {
            display: false,
          },
          ticks: {
            color: onSurface,
          },
        },
        y: {
          grid: {
            color: hexToRgba(onSurface, isDark.value ? 0.18 : 0.1),
          },
          ticks: {
            color: onSurface,
          },
          title: {
            display: true,
            text: 'Data (GiB)',
            color: onSurface,
          },
        },
      },
      plugins: {
        legend: {
          display: false,
          labels: {
            color: onSurface,
          },
        },
        tooltip: {
          intersect: false,
          backgroundColor: hexToRgba(surface, isDark.value ? 0.95 : 0.98),
          borderColor: hexToRgba(onSurface, 0.2),
          borderWidth: 1,
          titleColor: onSurface,
          bodyColor: onSurface,
        },
        title: {
          display: false,
          text: 'Last 12 Months Data Collection',
          color: onSurface,
          font: {
            size: 16,
          },
        },
      },
    };
  });

  const fetchAccumData = async () => {
    try {
      const res = await request.get('/dashboard/last-12m-collect-data');
      last12MonthsEtlData.value = res;
      chartReady.value = true;
    } catch (err) {
      console.error('Error fetching data:', err);
    }
  };

  onMounted(() => {
    fetchAccumData();
  });
</script>

<style scoped>
  .chart-wrapper {
    position: relative;
    height: 400px;
    width: 100%;
    border-radius: 12px;
    border: 1px solid rgba(var(--v-theme-on-surface), 0.12);
    transition: background-color 0.3s ease, border-color 0.3s ease;
  }

  .dark-mode {
    background-color: rgb(var(--v-theme-surface-variant));
  }

  .chart-wrapper:not(.dark-mode) {
    background-color: rgb(var(--v-theme-surface));
  }
</style>

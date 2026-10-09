<template>
  <DashboardChartCard
    title="耗时分布"
    subtitle="近 30 天，每表每日一次采集"
    :height="320"
    :state="state"
    :error="error"
  >
    <DashboardBarChart :labels="labels" :series="series" y-title="采集次数" :markers="markers" />
    <template #footer>
      <div class="histogram-footer">
        <v-chip size="x-small" variant="tonal" color="info">P50 {{ fmt(p50) }}</v-chip>
        <v-chip size="x-small" variant="tonal" color="warning">P95 {{ fmt(p95) }}</v-chip>
        <v-chip size="x-small" variant="tonal">最大 {{ fmt(maxSecs) }}</v-chip>
        <v-chip size="x-small" variant="tonal">
          样本 {{ (total ?? 0).toLocaleString('zh-CN') }}
        </v-chip>
      </div>
    </template>
  </DashboardChartCard>
</template>

<script setup lang="ts">
  import { computed } from 'vue';
  import DashboardChartCard from './charts/DashboardChartCard.vue';
  import DashboardBarChart from './charts/DashboardBarChart.vue';
  import type { ValueMarker } from '@/utils/chart-plugins';
  import { useChartData } from '@/composables/useChartData';
  import { useChartTheme } from '@/composables/useChartTheme';
  import { dashboardService } from '@/service/dashboard-service';
  import { formatSeconds } from '@/utils/chart-colors';

  const { raw, state, error } = useChartData(() => dashboardService.fetchTakeSecsHistogram());
  const { resolve } = useChartTheme();

  const buckets = computed(() => raw.value?.buckets ?? []);
  const labels = computed(() => buckets.value.map(b => b.label));
  const series = computed(() => [
    {
      key: 'cnt',
      label: '采集次数',
      data: buckets.value.map(b => b.cnt),
      color: resolve('primary', '#176FD6'),
    },
  ]);

  const p50 = computed(() => raw.value?.p50 ?? null);
  const p95 = computed(() => raw.value?.p95 ?? null);
  const maxSecs = computed(() => raw.value?.max_secs ?? null);
  const total = computed(() => raw.value?.total ?? null);

  // 分位数落在哪个桶，标记线就画在哪个桶的中心（x 轴是分类轴）
  const bucketIndexFor = (secs: number | null): number | null => {
    if (secs === null) return null;
    const index = buckets.value.findIndex(b => secs < bucketEnd(b.label));
    return index === -1 ? buckets.value.length - 1 : index;
  };

  const bucketEnd = (label: string): number => {
    const map: Record<string, number> = {
      '<10s': 10,
      '10-30s': 30,
      '30-60s': 60,
      '1-2m': 120,
      '2-5m': 300,
      '5-10m': 600,
      '10-30m': 1800,
      '30m-1h': 3600,
      '>=1h': Number.MAX_SAFE_INTEGER,
    };
    return map[label] ?? Number.MAX_SAFE_INTEGER;
  };

  const markers = computed<ValueMarker[]>(() => {
    const result: ValueMarker[] = [];
    const p50Index = bucketIndexFor(p50.value);
    const p95Index = bucketIndexFor(p95.value);
    if (p50Index !== null) {
      result.push({ value: p50Index, label: `P50 ${formatSeconds(p50.value)}`, color: '#1E67B3' });
    }
    if (p95Index !== null && p95Index !== p50Index) {
      result.push({ value: p95Index, label: `P95 ${formatSeconds(p95.value)}`, color: '#A86717' });
    }
    return result;
  });

  const fmt = (secs: number | null) => formatSeconds(secs);
</script>

<style scoped>
  .histogram-footer {
    display: flex;
    gap: 8px;
    padding: 0 8px 4px;
  }
</style>

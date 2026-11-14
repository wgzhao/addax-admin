<template>
  <SectionCard
    title="采集量趋势"
    :subtitle="`近 ${rangeDays} 天 · 每日采集量（GiB）`"
    icon="mdi-chart-areaspline"
  >
    <template #actions>
      <v-btn size="x-small" variant="text" @click="showTable = !showTable">
        {{ showTable ? '图表' : '表格' }}
      </v-btn>
    </template>

    <SkeletonBlock v-if="loading" :height="280" />
    <ErrorState v-else-if="error" :message="error" @retry="$emit('retry')" />
    <div v-else-if="points.length === 0" class="trend-empty">暂无采集量数据</div>
    <template v-else>
      <div v-if="!showTable" class="chart-box" :data-chart-state="chartState">
        <DashboardLineChart
          :labels="labels"
          :series="chartSeries"
          y-title="GiB"
          :value-formatter="formatGb"
          :show-points="false"
        />
      </div>
      <div v-else class="table-box">
        <table class="mini-table">
          <thead>
            <tr>
              <th>业务日期</th>
              <th class="num">采集量 (GiB)</th>
              <th class="num">7 日均值 (GiB)</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="point in points" :key="point.date">
              <td>{{ point.date }}</td>
              <td class="num">{{ point.gib.toFixed(2) }}</td>
              <td class="num">{{ point.avg.toFixed(2) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>

    <template #footer> 按业务日期汇总；7 日均值为滚动窗口，用于观察趋势而非精确对账。 </template>
  </SectionCard>
</template>

<script setup lang="ts">
  import { computed, ref } from 'vue';
  import SectionCard from '@/components/common/SectionCard.vue';
  import SkeletonBlock from '@/components/common/SkeletonBlock.vue';
  import ErrorState from '@/components/common/ErrorState.vue';
  import DashboardLineChart from '@/components/dashboard/charts/DashboardLineChart.vue';
  import type { ChartSeries } from '@/types/dashboard';
  import { useChartTheme } from '@/composables/useChartTheme';

  const props = withDefaults(
    defineProps<{
      series: { date: string; gib: number }[];
      rangeDays: number;
      loading?: boolean;
      error?: string | null;
    }>(),
    { loading: false, error: null }
  );

  defineEmits<{ retry: [] }>();

  const showTable = ref(false);
  const { emphasis, muted } = useChartTheme();

  const points = computed(() => {
    const values = props.series;
    return values.map((item, index) => {
      const windowValues = values.slice(Math.max(0, index - 6), index + 1).map(v => v.gib);
      const avg = windowValues.reduce((total, value) => total + value, 0) / windowValues.length;
      return { ...item, avg };
    });
  });

  const chartState = computed(() =>
    props.loading ? 'loading' : props.error ? 'error' : points.value.length ? 'ready' : 'empty'
  );

  const labels = computed(() => points.value.map(item => item.date.slice(5)));
  const chartSeries = computed<ChartSeries[]>(() => [
    {
      key: 'daily',
      label: '每日采集量',
      data: points.value.map(item => item.gib),
      color: emphasis.value,
      fill: true,
    },
    {
      key: 'avg7',
      label: '7 日均值',
      data: points.value.map(item => item.avg),
      color: muted.value,
      fill: false,
    },
  ]);

  const formatGb = (value: number) => `${value.toFixed(1)}`;
</script>

<style scoped>
  .chart-box {
    height: 280px;
  }

  .table-box {
    max-height: 280px;
    overflow: auto;
  }

  .mini-table {
    width: 100%;
    border-collapse: collapse;
    font-size: 12px;
    font-variant-numeric: tabular-nums;
  }

  .mini-table th,
  .mini-table td {
    padding: 7px 8px;
    border-bottom: 1px solid var(--ds-border-subtle);
    text-align: left;
  }

  .mini-table th {
    position: sticky;
    top: 0;
    background: var(--ds-surface-raised);
    font-weight: 600;
    color: var(--ds-text-2);
  }

  .mini-table .num {
    text-align: right;
  }

  .trend-empty {
    display: flex;
    align-items: center;
    justify-content: center;
    height: 280px;
    font-size: 13px;
    color: var(--ds-text-3);
  }
</style>

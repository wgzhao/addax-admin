<template>
  <DashboardChartCard
    title="各采集源批次耗时"
    subtitle="最近一次采集 · 墙钟口径"
    :height="360"
    :state="state"
    :error="error"
  >
    <template #actions>
      <v-btn
        size="x-small"
        variant="text"
        class="duration-filter"
        :class="{ 'duration-filter--on': onlySlower }"
        @click="onlySlower = !onlySlower"
      >
        {{ onlySlower ? '显示全部' : '仅看变慢' }}
      </v-btn>
    </template>

    <div class="duration-scroll">
      <v-data-table
        :items="visibleRows"
        :headers="headers"
        :sort-by="[{ key: 'delta_sort', order: 'desc' }]"
        density="compact"
        :items-per-page="-1"
        item-value="sid"
        hide-default-footer
        class="duration-table"
      >
        <template #item.source_name="{ item }">
          <div class="cell-source">
            <span class="cell-name" :title="item.source_name">{{ item.source_name }}</span>
            <span class="cell-sub">
              <template v-if="item.in_progress">进行中 · </template>
              {{ item.table_count ?? 0 }}/{{ item.total_tables }} 表
            </span>
          </div>
        </template>
        <template #item.biz_date="{ item }">
          <div class="cell-source">
            <span class="cell-date" :title="item.biz_date ?? ''">{{
              shortDate(item.biz_date)
            }}</span>
            <span class="cell-sub">{{ timeRange(item) }}</span>
          </div>
        </template>
        <template #item.take_secs="{ item }">
          <span class="cell-duration">
            {{ item.take_secs === null ? '—' : formatSeconds(item.take_secs) }}
          </span>
        </template>
        <template #item.prev_take_secs="{ item }">
          <span class="cell-prev" :title="item.prev_biz_date ?? ''">
            {{ item.prev_take_secs === null ? '—' : formatSeconds(item.prev_take_secs) }}
          </span>
        </template>
        <template #item.delta_sort="{ item }">
          <span v-if="item.delta_pct === null" class="cell-delta cell-delta--flat">—</span>
          <span v-else class="cell-delta" :class="deltaClass(item.delta_pct)">
            <v-icon size="13">
              {{
                item.delta_pct > 0
                  ? 'mdi-arrow-up'
                  : item.delta_pct < 0
                    ? 'mdi-arrow-down'
                    : 'mdi-minus'
              }}
            </v-icon>
            {{ Math.abs(item.delta_pct).toFixed(1) }}%
          </span>
        </template>
        <template #item.trend_secs="{ item }">
          <svg
            v-if="item.trend_secs.length > 1"
            class="cell-spark"
            viewBox="0 0 100 24"
            preserveAspectRatio="none"
            aria-hidden="true"
          >
            <polyline
              :points="sparkPoints(item.trend_secs)"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
              stroke-linecap="round"
              stroke-linejoin="round"
              vector-effect="non-scaling-stroke"
            />
          </svg>
          <span v-else class="cell-spark-empty">—</span>
        </template>
      </v-data-table>
    </div>
    <template #footer>
      <p class="duration-note">
        批次耗时 = 该源当日第一张表开始到最后一张表结束。进行中的批次不计入环比与趋势。
      </p>
    </template>
  </DashboardChartCard>
</template>

<script setup lang="ts">
  import { computed, ref } from 'vue';
  import type { DataTableHeader } from 'vuetify';
  import DashboardChartCard from './charts/DashboardChartCard.vue';
  import { useChartData } from '@/composables/useChartData';
  import { dashboardService } from '@/service/dashboard-service';
  import { formatSeconds } from '@/utils/chart-colors';
  import type { SourceDurationRow } from '@/types/dashboard';

  type Row = SourceDurationRow & { delta_sort: number };

  const { raw, state, error } = useChartData(() => dashboardService.fetchSourceDurations());
  const onlySlower = ref(false);

  const rows = computed<Row[]>(() =>
    (raw.value ?? []).map(row => ({
      ...row,
      // Why: sources without a previous batch must sort last, not first. Vuetify turns values
      // into strings before comparing, and "-Infinity" is not numeric, so use a finite floor.
      delta_sort: row.delta_pct ?? -Number.MAX_SAFE_INTEGER,
    }))
  );

  const visibleRows = computed(() =>
    onlySlower.value
      ? rows.value.filter(row => row.delta_pct !== null && row.delta_pct > 0)
      : rows.value
  );

  const headers: DataTableHeader[] = [
    { title: '采集源', key: 'source_name', sortable: false },
    { title: '最近批次', key: 'biz_date', sortable: false, width: 132 },
    { title: '批次耗时', key: 'take_secs', align: 'end', width: 92 },
    { title: '上次耗时', key: 'prev_take_secs', align: 'end', width: 92 },
    { title: '环比', key: 'delta_sort', align: 'end', width: 96 },
    { title: '近 7 次', key: 'trend_secs', sortable: false, width: 112 },
  ];

  const shortDate = (value: string | null) => (value ? value.slice(5) : '—');

  const timeRange = (row: Row) => {
    const begin = row.begin_at?.slice(11, 16);
    const finish = row.finish_at?.slice(11, 16);
    if (!begin) return '';
    return finish ? `${begin}–${finish}` : `${begin}–…`;
  };

  const deltaClass = (delta: number) => {
    if (delta > 0) return 'cell-delta--slow';
    if (delta < 0) return 'cell-delta--fast';
    return 'cell-delta--flat';
  };

  const sparkPoints = (values: number[]) => {
    const min = Math.min(...values);
    const max = Math.max(...values);
    const span = max - min || 1;
    return values
      .map((value, index) => {
        const x = (index / (values.length - 1)) * 100;
        const y = 22 - ((value - min) / span) * 20;
        return `${x.toFixed(2)},${y.toFixed(2)}`;
      })
      .join(' ');
  };
</script>

<style scoped>
  .duration-filter {
    letter-spacing: 0;
    font-size: 12px;
    color: var(--ds-text-2);
  }

  .duration-filter--on {
    color: rgb(var(--v-theme-primary));
    font-weight: 600;
  }

  .duration-scroll {
    height: 100%;
    overflow: auto;
  }

  .duration-table {
    font-size: 12px;
  }

  .duration-table :deep(th) {
    white-space: nowrap;
  }

  .cell-source {
    display: flex;
    flex-direction: column;
    line-height: 1.25;
    min-width: 0;
  }

  .cell-name {
    font-weight: 600;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .cell-sub,
  .cell-prev {
    color: var(--ds-text-3);
    font-size: 11px;
    white-space: nowrap;
  }

  .cell-date {
    font-variant-numeric: tabular-nums;
  }

  .cell-duration {
    font-size: 13px;
    font-weight: 600;
    font-variant-numeric: tabular-nums;
  }

  .cell-delta {
    display: inline-flex;
    align-items: center;
    gap: 2px;
    font-weight: 600;
    font-variant-numeric: tabular-nums;
  }

  .cell-delta--slow {
    color: rgb(var(--v-theme-error));
  }

  .cell-delta--fast {
    color: rgb(var(--v-theme-success));
  }

  .cell-delta--flat {
    color: var(--ds-text-3);
    font-weight: 500;
  }

  .cell-spark {
    width: 100%;
    height: 22px;
    color: rgb(var(--v-theme-primary));
  }

  .cell-spark-empty {
    color: var(--ds-text-3);
  }

  .duration-note {
    margin: 0;
    font-size: 11px;
    line-height: 1.5;
    color: var(--ds-text-3);
  }
</style>

<template>
  <DashboardChartCard
    title="采集缺失日历"
    subtitle="近 8 周，颜色越深缺失越多"
    :height="300"
    :state="state"
    :error="error"
  >
    <div class="calendar-wrap">
      <div class="calendar-grid" :style="gridStyle">
        <div v-for="weekday in weekdayHeaders" :key="weekday" class="calendar-weekday">
          {{ weekday }}
        </div>
        <template v-for="(week, weekIndex) in weeks" :key="weekIndex">
          <div v-for="(cell, dayIndex) in week" :key="`${weekIndex}-${dayIndex}`">
            <v-tooltip v-if="cell" location="top">
              <template #activator="{ props: tooltipProps }">
                <div
                  v-bind="tooltipProps"
                  class="calendar-cell"
                  :style="{ backgroundColor: cell.color }"
                  :data-missing="cell.missing"
                />
              </template>
              <span>{{ cell.bizDate }}：缺失 {{ cell.missing }} / 应采 {{ cell.expected }}</span>
            </v-tooltip>
            <div v-else class="calendar-cell calendar-cell-empty" />
          </div>
        </template>
      </div>
      <div class="calendar-footer">
        <v-chip size="x-small" variant="tonal" color="success">无缺失</v-chip>
        <v-chip size="x-small" variant="tonal" color="error">
          近 8 周共缺失 {{ totalMissing }} 表·天
        </v-chip>
      </div>
    </div>
  </DashboardChartCard>
</template>

<script setup lang="ts">
  import { computed } from 'vue';
  import dayjs from 'dayjs';
  import DashboardChartCard from './charts/DashboardChartCard.vue';
  import { useChartData } from '@/composables/useChartData';
  import { useChartTheme } from '@/composables/useChartTheme';
  import { dashboardService } from '@/service/dashboard-service';

  const { raw, state, error } = useChartData(() => dashboardService.fetchMissingCalendar());
  const { resolve, hexToRgba } = useChartTheme();

  const weekdayHeaders = ['一', '二', '三', '四', '五', '六', '日'];

  interface Cell {
    bizDate: string;
    missing: number;
    expected: number;
    color: string;
  }

  const rows = computed(() => raw.value ?? []);

  const totalMissing = computed(() =>
    rows.value.reduce((sum, row) => sum + Math.max(row.missing, 0), 0)
  );

  // 按周（周一为首日）排列成 7 行 × N 列的网格
  const weeks = computed<(Cell | null)[][]>(() => {
    const successColor = resolve('success', '#217A38');
    const errorColor = resolve('error', '#C92A2A');
    const days: Cell[] = rows.value.map(row => {
      if (row.expected === 0) {
        return { bizDate: row.biz_date, missing: 0, expected: 0, color: 'transparent' };
      }
      const ratio = Math.min(Math.max(row.missing / row.expected, 0), 1);
      const color =
        ratio === 0 ? hexToRgba(successColor, 0.15) : hexToRgba(errorColor, 0.15 + ratio * 0.7);
      return { bizDate: row.biz_date, missing: row.missing, expected: row.expected, color };
    });
    if (!days.length) return [];

    const grid: (Cell | null)[][] = weekdayHeaders.map(() => []);
    const firstDayOffset = (dayjs(days[0].bizDate).day() + 6) % 7; // 周一为 0
    for (let i = 0; i < firstDayOffset; i++) {
      grid[i].push(null);
    }
    for (const day of days) {
      const weekday = (dayjs(day.bizDate).day() + 6) % 7;
      grid[weekday].push(day);
    }
    // 行转列：让每周成为一列
    const columns: (Cell | null)[][] = [];
    const columnCount = grid[0].length;
    for (let c = 0; c < columnCount; c++) {
      columns.push(weekdayHeaders.map((_, w) => grid[w][c] ?? null));
    }
    return columns;
  });

  const gridStyle = computed(() => ({
    gridTemplateColumns: `auto repeat(${Math.max(weeks.value.length, 1)}, 1fr)`,
  }));
</script>

<style scoped>
  .calendar-wrap {
    height: 100%;
    display: flex;
    flex-direction: column;
    justify-content: center;
    gap: 10px;
    padding: 0 8px;
  }

  .calendar-grid {
    display: grid;
    grid-template-columns: auto repeat(8, 1fr);
    grid-auto-rows: 24px;
    gap: 4px;
    align-items: center;
  }

  .calendar-weekday {
    font-size: 11px;
    color: rgb(var(--v-theme-on-surface-muted));
    text-align: right;
    padding-right: 4px;
  }

  .calendar-cell {
    height: 100%;
    border-radius: 4px;
    border: 1px solid rgba(var(--v-theme-on-surface), 0.08);
    cursor: default;
  }

  .calendar-cell-empty {
    border-color: transparent;
  }

  .calendar-footer {
    display: flex;
    gap: 8px;
    justify-content: flex-end;
  }
</style>

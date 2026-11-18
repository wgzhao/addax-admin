<template>
  <DashboardChartCard
    title="采集缺失日历"
    subtitle="近 8 周，越红缺失越多"
    :height="340"
    :state="state"
    :error="error"
  >
    <div class="calendar-wrap">
      <div class="calendar-grid">
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
                  :data-level="cell.level"
                  :data-missing="cell.missing"
                />
              </template>
              <span>{{ cellTooltip(cell) }}</span>
            </v-tooltip>
            <div v-else class="calendar-cell calendar-cell-placeholder" />
          </div>
        </template>
      </div>
      <div class="calendar-footer">
        <div class="calendar-legend">
          <span class="calendar-legend-item">
            <span class="calendar-swatch" data-level="0" />
            无缺失
          </span>
          <span class="calendar-legend-item">
            <span class="calendar-swatch" data-level="-1" />
            无应采
          </span>
          <span class="calendar-legend-item">
            缺失少
            <span class="calendar-scale-bar" />
            缺失多
          </span>
        </div>
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
  import { dashboardService } from '@/service/dashboard-service';

  // Severity buckets for days that missed something; level 0 is reserved for "nothing missing"
  // so a clean day never shares a hue with a slightly-missing one.
  const SEVERITY_LEVELS = 6;

  const { raw, state, error } = useChartData(() => dashboardService.fetchMissingCalendar());

  const weekdayHeaders = ['一', '二', '三', '四', '五', '六', '日'];

  interface Cell {
    bizDate: string;
    missing: number;
    expected: number;
    /** -1 no tables expected, 0 nothing missing, 1..6 missing severity */
    level: number;
  }

  function cellTooltip(cell: Cell): string {
    if (cell.expected === 0) return `${cell.bizDate}：无应采表`;
    if (cell.missing === 0) return `${cell.bizDate}：应采 ${cell.expected}，无缺失`;
    return `${cell.bizDate}：缺失 ${cell.missing} / 应采 ${cell.expected}`;
  }

  const rows = computed(() => raw.value ?? []);

  const totalMissing = computed(() =>
    rows.value.reduce((sum, row) => sum + Math.max(row.missing, 0), 0)
  );

  // 按周（周一为首日）切分成 N 行 × 7 列，首尾不足一周的位置留空
  const weeks = computed<(Cell | null)[][]>(() => {
    const days: Cell[] = rows.value
      .map(row => {
        if (row.expected === 0) {
          return { bizDate: row.biz_date, missing: 0, expected: 0, level: -1 };
        }
        const ratio = Math.min(Math.max(row.missing / row.expected, 0), 1);
        const level =
          row.missing === 0 ? 0 : Math.min(Math.ceil(ratio * SEVERITY_LEVELS), SEVERITY_LEVELS);
        return {
          bizDate: row.biz_date,
          missing: row.missing,
          expected: row.expected,
          level,
        };
      })
      .sort((a, b) => a.bizDate.localeCompare(b.bizDate));
    if (!days.length) return [];

    const result: (Cell | null)[][] = [];
    let currentWeek: (Cell | null)[] = [];
    const firstDayOffset = (dayjs(days[0].bizDate).day() + 6) % 7; // 周一为 0
    for (let i = 0; i < firstDayOffset; i++) {
      currentWeek.push(null);
    }
    for (const day of days) {
      currentWeek.push(day);
      if (currentWeek.length === 7) {
        result.push(currentWeek);
        currentWeek = [];
      }
    }
    if (currentWeek.length) {
      while (currentWeek.length < 7) {
        currentWeek.push(null);
      }
      result.push(currentWeek);
    }
    return result;
  });
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
    grid-template-columns: repeat(7, minmax(0, 1fr));
    grid-auto-rows: 26px;
    gap: 4px;
    align-items: center;
  }

  .calendar-weekday {
    font-size: 11px;
    color: rgb(var(--v-theme-on-surface-muted));
    text-align: center;
  }

  .calendar-cell {
    height: 26px;
    border-radius: 4px;
    border: 1px solid rgba(var(--v-theme-on-surface), 0.08);
    cursor: default;
  }

  /* Why: only missing days spend ink; a clean day stays neutral so the eye reads
     red as "something is missing" instead of "this day has data". */
  .calendar-cell[data-level='0'],
  .calendar-swatch[data-level='0'] {
    background: rgba(var(--v-theme-on-surface), 0.07);
  }

  .calendar-cell[data-level='-1'],
  .calendar-swatch[data-level='-1'] {
    background: transparent;
  }

  .calendar-cell[data-level='1'],
  .calendar-swatch[data-level='1'] {
    background: rgba(var(--v-theme-error), 0.16);
  }

  .calendar-cell[data-level='2'],
  .calendar-swatch[data-level='2'] {
    background: rgba(var(--v-theme-error), 0.3);
  }

  .calendar-cell[data-level='3'],
  .calendar-swatch[data-level='3'] {
    background: rgba(var(--v-theme-error), 0.46);
  }

  .calendar-cell[data-level='4'],
  .calendar-swatch[data-level='4'] {
    background: rgba(var(--v-theme-error), 0.62);
  }

  .calendar-cell[data-level='5'],
  .calendar-swatch[data-level='5'] {
    background: rgba(var(--v-theme-error), 0.78);
  }

  .calendar-cell[data-level='6'],
  .calendar-swatch[data-level='6'] {
    background: rgba(var(--v-theme-error), 1);
  }

  .calendar-cell-placeholder {
    border-color: transparent;
  }

  .calendar-footer {
    display: flex;
    align-items: center;
    gap: 12px;
    justify-content: space-between;
  }

  .calendar-legend {
    display: inline-flex;
    align-items: center;
    gap: 14px;
    font-size: 11px;
    color: var(--ds-text-3);
  }

  .calendar-legend-item {
    display: inline-flex;
    align-items: center;
    gap: 6px;
  }

  .calendar-swatch {
    width: 12px;
    height: 12px;
    border-radius: 3px;
    border: 1px solid rgba(var(--v-theme-on-surface), 0.12);
  }

  .calendar-scale-bar {
    width: 96px;
    height: 8px;
    border-radius: 999px;
    border: 1px solid var(--ds-border-subtle);
    background: linear-gradient(
      90deg,
      rgba(var(--v-theme-error), 0.16),
      rgba(var(--v-theme-error), 1)
    );
  }
</style>

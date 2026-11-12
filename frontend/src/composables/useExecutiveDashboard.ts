import { computed, onMounted, ref } from 'vue';
import { dashboardService } from '@/service/dashboard-service';
import type {
  DashboardSummary,
  FailTopRow,
  MissingCalendarRow,
  SlowTableRow,
  SourceContributionRow,
  TakeSecsHistogram,
} from '@/types/dashboard';
import { formatBytes, formatDuration } from '@/utils/format';
import { formatSeconds } from '@/utils/chart-colors';

export type ExecutiveRange = 7 | 30;

export interface RiskItem {
  id: string;
  title: string;
  detail: string;
  severity: 'critical' | 'warning' | 'info';
  to?: string;
}

const sum = (values: number[]) => values.reduce((total, value) => total + value, 0);

const truncate = (value: string, max = 64) =>
  value.length > max ? `${value.slice(0, max)}…` : value;

export function useExecutiveDashboard() {
  const summary = ref<DashboardSummary | null>(null);
  const volumeRows = ref<SourceContributionRow[]>([]);
  const failRows = ref<FailTopRow[]>([]);
  const missingRows = ref<MissingCalendarRow[]>([]);
  const slowRows = ref<SlowTableRow[]>([]);
  const histogram = ref<TakeSecsHistogram | null>(null);

  const loading = ref(true);
  const refreshing = ref(false);
  const error = ref<string | null>(null);
  const riskError = ref<string | null>(null);
  const volumeError = ref<string | null>(null);
  const updatedAt = ref('');
  const rangeDays = ref<ExecutiveRange>(30);

  const ratios = computed(() => summary.value?.ratios ?? []);

  const totals = computed(() => {
    const result = { total: 0, done: 0, failed: 0, running: 0, pending: 0 };
    for (const ratio of ratios.value) {
      result.total += Number(ratio.total ?? 0);
      result.done += Number(ratio.done ?? 0);
      result.failed += Number(ratio.failed ?? 0);
      result.running += Number(ratio.running ?? 0);
      result.pending += Number(ratio.pending ?? 0);
    }
    return result;
  });

  const healthRate = computed(() =>
    totals.value.total > 0 ? (totals.value.done / totals.value.total) * 100 : 0
  );

  const healthStatus = computed(() => {
    if (totals.value.failed > 0) {
      return {
        key: 'attention',
        label: '需关注',
        color: 'error',
        icon: 'mdi-alert-circle-outline',
        message: `${totals.value.failed} 张采集表失败`,
      };
    }
    if (totals.value.running > 0) {
      return {
        key: 'running',
        label: '进行中',
        color: 'info',
        icon: 'mdi-progress-clock',
        message: `${totals.value.running} 张采集表采集中`,
      };
    }
    if (totals.value.pending > 0) {
      return {
        key: 'pending',
        label: '待完成',
        color: 'warning',
        icon: 'mdi-clock-outline',
        message: `${totals.value.pending} 张采集表未开始`,
      };
    }
    return {
      key: 'healthy',
      label: '健康',
      color: 'success',
      icon: 'mdi-check-circle-outline',
      message: '今日采集全部完成',
    };
  });

  const volumeByDay = computed(() => {
    const map = new Map<string, number>();
    for (const row of volumeRows.value) {
      if (!row.biz_date) continue;
      map.set(row.biz_date, (map.get(row.biz_date) ?? 0) + Number(row.gib ?? 0));
    }
    return [...map.entries()]
      .sort((a, b) => a[0].localeCompare(b[0]))
      .map(([date, gib]) => ({ date, gib }));
  });

  const volumeSeries = computed(() => volumeByDay.value.slice(-rangeDays.value));

  const volumeDelta = computed(() => {
    const values = volumeByDay.value.map(item => item.gib);
    if (values.length < 8) return null;
    const last7 = sum(values.slice(-7));
    const prev7 = sum(values.slice(-14, -7));
    if (!prev7) return null;
    return ((last7 - prev7) / prev7) * 100;
  });

  const volumeSparkline = computed(() => volumeByDay.value.slice(-12).map(item => item.gib));

  const sourceContribution = computed(() => {
    const days = new Set(volumeSeries.value.map(item => item.date));
    const map = new Map<string, { key: string; name: string; gib: number }>();
    for (const row of volumeRows.value) {
      if (!days.has(row.biz_date)) continue;
      // Why: the backend fills empty calendar days with a null placeholder row;
      // folding those would create a bogus 未知 source with 0 bytes
      if (row.source_id == null && !row.name) continue;
      const key = row.source_id != null ? String(row.source_id) : row.name || 'UNKNOWN';
      const current = map.get(key) ?? {
        key,
        name: row.name || '未知',
        gib: 0,
      };
      current.gib += Number(row.gib ?? 0);
      map.set(key, current);
    }
    const sorted = [...map.values()].sort((a, b) => b.gib - a.gib);
    const top = sorted.slice(0, 8);
    const rest = sum(sorted.slice(8).map(item => item.gib));
    return rest > 0 ? [...top, { key: 'OTHER', name: '其他', gib: rest }] : top;
  });

  const missingTotal = computed(() =>
    missingRows.value.reduce((total, row) => total + Math.max(Number(row.missing ?? 0), 0), 0)
  );

  const riskItems = computed<RiskItem[]>(() => {
    const items: RiskItem[] = [];

    for (const row of failRows.value.slice(0, 3)) {
      items.push({
        id: `fail-${row.tid}`,
        title: `${row.source_db}.${row.source_table}`,
        detail: `近 14 天失败 ${row.fail_cnt} 次${
          row.last_error ? ` · ${truncate(row.last_error)}` : ''
        }`,
        severity: 'critical',
        to: `/table/detail/${row.tid}`,
      });
    }

    if (missingTotal.value > 0) {
      items.push({
        id: 'missing',
        title: '采集缺失',
        detail: `近 7 天共有 ${missingTotal.value} 表·天未采集`,
        severity: 'warning',
        to: '/table',
      });
    }

    for (const row of slowRows.value.slice(0, 2)) {
      items.push({
        id: `slow-${row.tid}`,
        title: `${row.source_db}.${row.source_table}`,
        detail: `平均耗时 ${formatSeconds(row.avg_secs)}，近 ${row.days} 天偏慢`,
        severity: 'warning',
        to: `/table/detail/${row.tid}`,
      });
    }

    return items.slice(0, 6);
  });

  const todayData = computed(() => formatBytes(summary.value?.lastDataBytes ?? 0));
  const totalData = computed(() => formatBytes(summary.value?.totalDataBytes ?? 0));
  const avgSeconds = computed(() => {
    if (!summary.value || totals.value.done <= 0) return 0;
    return Number(summary.value.totalTimeSecs ?? 0) / totals.value.done;
  });
  const avgTime = computed(() => {
    const secs = avgSeconds.value;
    if (secs <= 0) return { value: '—', unit: '' };
    if (secs < 60) return { value: String(Math.round(secs)), unit: '秒' };
    return formatDuration(secs);
  });
  const avgDelta = computed(() => {
    const p50 = Number(histogram.value?.p50 ?? 0);
    if (!p50 || !avgSeconds.value) return null;
    return ((avgSeconds.value - p50) / p50) * 100;
  });

  const coverage = computed(() => {
    const active = Number(summary.value?.tableCount ?? 0);
    const all = Number(summary.value?.allTableCount ?? 0);
    return all > 0 ? `${active}/${all}` : '0/0';
  });

  const load = async (manual = false) => {
    if (manual) refreshing.value = true;
    else loading.value = true;
    error.value = null;
    riskError.value = null;
    volumeError.value = null;

    const results = await Promise.allSettled([
      dashboardService.fetchSummary(),
      dashboardService.fetchSourceContribution30d(),
      dashboardService.fetchFailTopTables({ days: 14, limit: 3 }),
      dashboardService.fetchMissingCalendar({ days: 7 }),
      dashboardService.fetchSlowTopTables({ days: 30, limit: 3 }),
      dashboardService.fetchTakeSecsHistogram({ days: 30 }),
    ] as const);

    const [summaryResult, volumeResult, failResult, missingResult, slowResult, histResult] =
      results;

    if (summaryResult.status === 'fulfilled') summary.value = summaryResult.value;
    if (volumeResult.status === 'fulfilled') volumeRows.value = volumeResult.value ?? [];
    if (failResult.status === 'fulfilled') failRows.value = failResult.value ?? [];
    if (missingResult.status === 'fulfilled') missingRows.value = missingResult.value ?? [];
    if (slowResult.status === 'fulfilled') slowRows.value = slowResult.value ?? [];
    if (histResult.status === 'fulfilled') histogram.value = histResult.value ?? null;

    const messageOf = (reason: unknown) =>
      reason instanceof Error ? reason.message : String(reason);

    if (summaryResult.status === 'rejected') {
      error.value = messageOf(summaryResult.reason);
    }

    if (
      failResult.status === 'rejected' ||
      missingResult.status === 'rejected' ||
      slowResult.status === 'rejected'
    ) {
      riskError.value = '风险数据加载失败，可重试';
    }

    if (volumeResult.status === 'rejected') {
      volumeError.value = '采集量趋势加载失败，可重试';
    }

    updatedAt.value = new Date().toLocaleTimeString('zh-CN', {
      hour: '2-digit',
      minute: '2-digit',
    });
    loading.value = false;
    refreshing.value = false;
  };

  onMounted(() => void load());

  return {
    // state
    summary,
    loading,
    refreshing,
    error,
    riskError,
    volumeError,
    updatedAt,
    rangeDays,
    ratios,
    totals,
    healthRate,
    healthStatus,
    volumeByDay,
    volumeSeries,
    volumeDelta,
    volumeSparkline,
    sourceContribution,
    failRows,
    missingRows,
    missingTotal,
    slowRows,
    histogram,
    riskItems,
    todayData,
    totalData,
    avgTime,
    avgDelta,
    coverage,
    reload: () => load(true),
  };
}

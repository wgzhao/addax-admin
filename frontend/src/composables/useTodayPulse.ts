import { computed, ref } from 'vue';
import { monitorService } from '@/service/monitor-service';
import settingsService from '@/service/settings-service';

interface Row {
  sys_name?: string;
  start_at?: string;
  total_cnt?: number | string;
  succ_cnt?: number | string;
  run_cnt?: number | string;
  fail_cnt?: number | string;
  no_run_cnt?: number | string;
  t_begin_at?: string | null;
  t_finish_at?: string | null;
}

export interface PulseRow {
  key: string;
  name: string;
  fullName: string;
  total: number;
  done: number;
  failed: number;
  state: 'done' | 'running' | 'failed' | 'pending';
  startPct: number;
  barStyle: Record<string, string>;
  timeLabel: string;
  timeTitle: string;
}

const DAY_MINUTES = 24 * 60;
const ALL_TICKS = Array.from({ length: 7 }, (_, i) => i * 240);

// Why: the overview summary and the detail tab render the same payload, so the state lives
// at module scope and is fetched once per page load
const loading = ref(true);
const raw = ref<Row[]>([]);
const switchTime = ref('');
let requested = false;

// Why: the API mixes "HH:mm:ss" and "yyyy-MM-dd HH:mm:ss"; taking the last token keeps
// the date digits from being read as hours.
const timeToMinutes = (value?: string | null): number | null => {
  if (!value) return null;
  const time = value.includes(' ') ? value.split(' ').pop()! : value;
  const [h, m] = time.split(':').map(Number);
  if (Number.isNaN(h) || Number.isNaN(m)) return null;
  return h * 60 + m;
};

const toNumber = (value?: number | string) => {
  const n = Number(value ?? 0);
  return Number.isFinite(n) ? n : 0;
};

export const rows = computed<PulseRow[]>(() => {
  const nowMinutes = new Date().getHours() * 60 + new Date().getMinutes();
  return raw.value
    .map(item => {
      const fullName = item.sys_name || '未知采集源';
      const match = fullName.match(/^(.*)\(([^()]*)\)$/);
      const name = (match?.[1] || fullName).trim();
      const total = toNumber(item.total_cnt);
      const done = toNumber(item.succ_cnt);
      const failed = toNumber(item.fail_cnt);
      const running = toNumber(item.run_cnt);

      const start = timeToMinutes(item.start_at) ?? 0;
      const begin = timeToMinutes(item.t_begin_at) ?? start;
      let finish = timeToMinutes(item.t_finish_at);
      if (finish !== null && finish < begin) finish = DAY_MINUTES;
      const end = running > 0 ? Math.max(begin, nowMinutes) : finish ?? begin;

      const state: PulseRow['state'] =
        failed > 0
          ? 'failed'
          : running > 0
          ? 'running'
          : done >= total && total > 0
          ? 'done'
          : 'pending';

      const left = (Math.min(begin, DAY_MINUTES) / DAY_MINUTES) * 100;
      const width = (Math.max(end - begin, 4) / DAY_MINUTES) * 100;

      const timeLabel =
        item.t_begin_at && item.t_finish_at
          ? `${item.t_begin_at.split(' ').pop()?.slice(0, 5)}–${item.t_finish_at
              .split(' ')
              .pop()
              ?.slice(0, 5)}`
          : (item.start_at || '--:--').slice(0, 5);

      return {
        key: `${fullName}-${item.start_at}`,
        name,
        fullName,
        total,
        done,
        failed,
        state,
        startPct: (start / DAY_MINUTES) * 100,
        barStyle: { left: `${left}%`, width: `${width}%` },
        timeLabel,
        timeTitle: `${fullName} · 计划 ${item.start_at || '--'} · 已采 ${done}/${total}${
          failed ? ` · 失败 ${failed}` : ''
        }`,
      };
    })
    .sort((a, b) => a.startPct - b.startPct);
});

export const counts = computed(() => {
  const result = { done: 0, running: 0, failed: 0, pending: 0 };
  for (const row of rows.value) result[row.state] += 1;
  return result;
});

export const switchLabel = computed(() => switchTime.value.slice(0, 5));

export const cutoverPct = computed(() => {
  const minutes = timeToMinutes(switchTime.value);
  return minutes === null ? null : (minutes / DAY_MINUTES) * 100;
});

// Why: the cutover label sits at its exact minute, so drop the hour tick it would collide with
export const ticks = computed(() => {
  const cutoverMinutes = timeToMinutes(switchTime.value);
  return ALL_TICKS.filter(tick => cutoverMinutes === null || Math.abs(tick - cutoverMinutes) > 60);
});

export const nowPct = computed(() => {
  const now = new Date();
  return ((now.getHours() * 60 + now.getMinutes()) / DAY_MINUTES) * 100;
});

export async function loadTodayPulse(force = false) {
  if (requested && !force) return;
  requested = true;
  loading.value = true;
  const [result, config] = await Promise.allSettled([
    monitorService.fetchAccomplishList(),
    settingsService.getSettings(),
  ]);
  raw.value =
    result.status === 'fulfilled' ? (((result.value as unknown as Row[]) || []) as Row[]) : [];
  if (config.status === 'fulfilled') {
    const value = (config.value as { SWITCH_TIME?: string } | null)?.SWITCH_TIME;
    if (value) switchTime.value = String(value);
  }
  loading.value = false;
}

export function useTodayPulse() {
  void loadTodayPulse();
  return { loading, rows, counts, switchTime, switchLabel, cutoverPct, ticks, nowPct };
}

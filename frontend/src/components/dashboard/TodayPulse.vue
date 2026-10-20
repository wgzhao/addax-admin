<template>
  <v-card flat class="ds-card pulse-card">
    <v-card-title class="pulse-head">
      <div class="pulse-head__text">
        <span class="pulse-title">今日运行 · 日切心跳</span>
        <span class="pulse-subtitle">{{ subtitleText }}</span>
      </div>
      <div class="pulse-tools">
        <span class="pulse-summary">
          <span class="legend-item"><i class="dot dot--done" />已完成 {{ counts.done }}</span>
          <span class="legend-item"><i class="dot dot--running" />进行中 {{ counts.running }}</span>
          <span class="legend-item" :class="{ 'legend-item--alert': counts.failed > 0 }">
            <i class="dot dot--failed" />有失败 {{ counts.failed }}
          </span>
          <span class="legend-item"><i class="dot dot--pending" />未开始 {{ counts.pending }}</span>
        </span>
        <v-btn
          size="small"
          variant="text"
          class="pulse-filter"
          :class="{ 'pulse-filter--on': onlyUnfinished }"
          @click="onlyUnfinished = !onlyUnfinished"
        >
          {{ onlyUnfinished ? '显示全部' : '仅看未完成' }}
        </v-btn>
      </div>
    </v-card-title>
    <v-card-text class="pulse-body">
      <div v-if="loading" class="pulse-loading">
        <v-skeleton-loader type="list-item-two-line@4" />
      </div>
      <div v-else-if="!rows.length" class="pulse-empty">今日没有采集源计划</div>
      <div v-else class="pulse-scroll">
        <div class="pulse-axis-strip">
          <div class="pulse-axis">
            <span
              v-for="tick in ticks"
              :key="tick"
              class="pulse-tick"
              :style="{ left: `${(tick / 1440) * 100}%` }"
            >
              {{ String(tick / 60).padStart(2, '0') }}:00
            </span>
            <span
              v-if="cutoverPct !== null"
              class="pulse-tick pulse-tick--cutover"
              :style="{ left: `${cutoverPct}%` }"
              >切日 {{ switchLabel }}</span
            >
          </div>
        </div>
        <div class="pulse-rows">
          <div v-for="row in visibleRows" :key="row.key" class="pulse-row">
            <div class="pulse-meta">
              <span class="pulse-name" :title="row.fullName">{{ row.name }}</span>
              <span class="pulse-count" :class="{ 'pulse-count--bad': row.failed > 0 }">
                {{ row.done }}/{{ row.total }}
              </span>
            </div>
            <div class="pulse-track">
              <span
                v-for="tick in ticks"
                :key="`g-${tick}`"
                class="pulse-grid"
                :style="{ left: `${(tick / 1440) * 100}%` }"
              />
              <span
                v-if="cutoverPct !== null"
                class="pulse-cutover"
                :style="{ left: `${cutoverPct}%` }"
              />
              <span class="pulse-bar" :class="`pulse-bar--${row.state}`" :style="row.barStyle" />
              <span
                class="pulse-dot"
                :class="`pulse-dot--${row.state}`"
                :style="{ left: `${row.startPct}%` }"
              />
              <span v-if="nowPct !== null" class="pulse-now" :style="{ left: `${nowPct}%` }" />
            </div>
            <div class="pulse-time" :title="row.timeTitle">{{ row.timeLabel }}</div>
          </div>
          <div v-if="!visibleRows.length" class="pulse-empty pulse-empty--inline">
            没有未完成的采集源
          </div>
        </div>
      </div>
    </v-card-text>
  </v-card>
</template>

<script setup lang="ts">
  import { computed, onMounted, ref } from 'vue';
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

  interface PulseRow {
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

  const loading = ref(true);
  const raw = ref<Row[]>([]);
  const switchTime = ref('');
  const onlyUnfinished = ref(false);

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

  const rows = computed<PulseRow[]>(() => {
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

  const nowPct = computed(() => {
    const now = new Date();
    return ((now.getHours() * 60 + now.getMinutes()) / DAY_MINUTES) * 100;
  });

  const switchLabel = computed(() => switchTime.value.slice(0, 5));

  const counts = computed(() => {
    const result = { done: 0, running: 0, failed: 0, pending: 0 };
    for (const row of rows.value) result[row.state] += 1;
    return result;
  });

  const visibleRows = computed(() =>
    onlyUnfinished.value ? rows.value.filter(row => row.state !== 'done') : rows.value
  );

  const subtitleText = computed(() =>
    switchLabel.value
      ? `按采集源开始时间排列，横轴为 24 小时；切日 ${switchLabel.value} 之前的记录属于上一业务日`
      : '按采集源开始时间排列，横轴为 24 小时'
  );

  const cutoverPct = computed(() => {
    const minutes = timeToMinutes(switchTime.value);
    return minutes === null ? null : (minutes / DAY_MINUTES) * 100;
  });

  const cutoverMinutes = computed(() => timeToMinutes(switchTime.value));

  // Why: the cutover label sits at its exact minute, so drop the hour tick it would collide with
  const ticks = computed(() =>
    ALL_TICKS.filter(
      tick => cutoverMinutes.value === null || Math.abs(tick - cutoverMinutes.value) > 60
    )
  );

  onMounted(async () => {
    const [rows, config] = await Promise.allSettled([
      monitorService.fetchAccomplishList(),
      settingsService.getSettings(),
    ]);
    raw.value =
      rows.status === 'fulfilled' ? (((rows.value as unknown as Row[]) || []) as Row[]) : [];
    if (config.status === 'fulfilled') {
      const value = (config.value as { SWITCH_TIME?: string } | null)?.SWITCH_TIME;
      if (value) switchTime.value = String(value);
    }
    loading.value = false;
  });
</script>

<style scoped>
  .pulse-head {
    display: flex;
    flex-wrap: wrap;
    align-items: baseline;
    justify-content: space-between;
    gap: 8px 16px;
    padding: 14px 16px 4px;
  }

  .pulse-title {
    font-size: 15px;
    font-weight: 600;
  }

  .pulse-subtitle {
    margin-left: 10px;
    font-size: 12px;
    color: var(--ds-text-2);
  }

  .pulse-tools {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    justify-content: flex-end;
    gap: 8px 12px;
  }

  .pulse-summary {
    display: inline-flex;
    flex-wrap: wrap;
    gap: 12px;
    font-size: 12px;
    color: var(--ds-text-2);
  }

  .legend-item {
    display: inline-flex;
    align-items: center;
    gap: 6px;
  }

  .legend-item--alert {
    color: rgb(var(--v-theme-error));
    font-weight: 600;
  }

  .pulse-filter {
    letter-spacing: 0;
    font-size: 12px;
    color: var(--ds-text-2);
  }

  .pulse-filter--on {
    color: rgb(var(--v-theme-primary));
    font-weight: 600;
  }

  .dot {
    width: 8px;
    height: 8px;
    border-radius: 50%;
    display: inline-block;
  }

  .dot--done {
    background: rgb(var(--v-theme-success));
  }

  .dot--running {
    background: rgb(var(--v-theme-info));
  }

  .dot--failed {
    background: rgb(var(--v-theme-error));
  }

  .dot--pending {
    background: rgb(var(--v-theme-on-surface-variant));
    opacity: 0.45;
  }

  .pulse-body {
    padding: 4px 16px 16px;
  }

  .pulse-scroll {
    max-height: 372px;
    overflow-y: auto;
    overscroll-behavior: contain;
  }

  .pulse-axis-strip {
    position: sticky;
    top: 0;
    z-index: 2;
    margin: 0 -16px;
    padding: 0 16px;
    background: rgb(var(--v-theme-surface));
  }

  .pulse-axis {
    position: relative;
    height: 30px;
    margin-left: 200px;
    margin-right: 92px;
    font-size: 11px;
    color: var(--ds-text-3);
  }

  .pulse-tick {
    position: absolute;
    bottom: 0;
    transform: translateX(-50%);
  }

  .pulse-tick--cutover {
    top: 0;
    bottom: auto;
    color: rgb(var(--v-theme-primary));
    font-weight: 600;
    transform: translateX(-50%);
  }

  .pulse-cutover {
    position: absolute;
    top: -1px;
    bottom: -1px;
    width: 0;
    border-left: 1px dashed rgba(var(--v-theme-primary), 0.55);
  }

  .pulse-row {
    display: grid;
    grid-template-columns: 188px minmax(240px, 1fr) 84px;
    align-items: center;
    gap: 12px;
    padding: 3px 0;
  }

  .pulse-row + .pulse-row {
    border-top: 1px dashed var(--ds-border-subtle);
  }

  .pulse-meta {
    display: flex;
    align-items: baseline;
    justify-content: space-between;
    gap: 8px;
    min-width: 0;
  }

  .pulse-name {
    font-size: 13px;
    font-weight: 500;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .pulse-count {
    font-size: 12px;
    color: var(--ds-text-2);
    font-variant-numeric: tabular-nums;
  }

  .pulse-count--bad {
    color: rgb(var(--v-theme-error));
    font-weight: 600;
  }

  .pulse-track {
    position: relative;
    height: 18px;
    border-radius: 4px;
    background: rgba(var(--v-theme-on-surface), 0.04);
  }

  .pulse-grid {
    position: absolute;
    top: 0;
    bottom: 0;
    width: 1px;
    background: var(--ds-border-subtle);
  }

  .pulse-bar {
    position: absolute;
    top: 5px;
    height: 8px;
    border-radius: 4px;
    min-width: 4px;
  }

  .pulse-bar--done {
    background: rgba(var(--v-theme-success), 0.55);
  }

  .pulse-bar--running {
    background: rgba(var(--v-theme-info), 0.5);
  }

  .pulse-bar--failed {
    background: rgba(var(--v-theme-error), 0.45);
  }

  .pulse-bar--pending {
    background: rgba(var(--v-theme-on-surface), 0.16);
  }

  .pulse-dot {
    position: absolute;
    top: 5px;
    width: 8px;
    height: 8px;
    border-radius: 50%;
    transform: translateX(-50%);
    box-shadow: 0 0 0 2px rgb(var(--v-theme-surface));
  }

  .pulse-dot--done {
    background: rgb(var(--v-theme-success));
  }

  .pulse-dot--running {
    background: rgb(var(--v-theme-info));
  }

  .pulse-dot--failed {
    background: rgb(var(--v-theme-error));
  }

  .pulse-dot--pending {
    background: rgb(var(--v-theme-on-surface-variant));
    opacity: 0.5;
  }

  .pulse-now {
    position: absolute;
    top: -2px;
    bottom: -2px;
    width: 1px;
    background: rgb(var(--v-theme-primary));
    opacity: 0.7;
  }

  .pulse-time {
    font-size: 12px;
    color: var(--ds-text-2);
    text-align: right;
    font-variant-numeric: tabular-nums;
  }

  .pulse-empty {
    padding: 24px 0;
    text-align: center;
    color: var(--ds-text-2);
    font-size: 13px;
  }

  .pulse-empty--inline {
    padding: 14px 0;
  }

  @media (max-width: 1100px) {
    .pulse-axis {
      margin-left: 160px;
    }

    .pulse-row {
      grid-template-columns: 150px minmax(180px, 1fr) 76px;
      gap: 8px;
    }
  }
</style>

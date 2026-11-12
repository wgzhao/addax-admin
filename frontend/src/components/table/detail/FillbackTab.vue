<template>
  <div class="fillback-tab">
    <v-alert v-if="capabilityLoaded && !supported" type="warning" variant="tonal" class="mb-4">
      {{ progress?.reason || '该表不支持补数' }}
    </v-alert>
    <v-alert v-else-if="progress?.sourceEnabled === false" type="warning" variant="tonal" class="mb-4">
      采集源已停用，仍可手工补数；恢复采集源后才会参与自动调度。
    </v-alert>
    <v-alert v-else-if="progress?.tableStatus === 'X'" type="warning" variant="tonal" class="mb-4">
      该采集表已停采，仍可手工补数；恢复后才会参与自动调度。
    </v-alert>

    <v-alert v-if="submitResult" :type="submitResult.totalEnqueued > 0 ? 'success' : 'warning'" variant="tonal" class="mb-4">
      已入队 {{ submitResult.totalEnqueued }} 个任务
      <template v-if="submitResult.skipped > 0">，跳过 {{ submitResult.skipped }} 个</template>。
      <span v-if="skippedDates.length"> 跳过日期：{{ skippedDates.join('、') }}。</span>
      <span v-if="submitResult.details.some(d => d.skipped)">
        被跳过的表：{{ submitResult.details.filter(d => d.skipped).map(d => `${d.table}（${d.reason}）`).join('；') }}
      </span>
    </v-alert>

    <v-card flat class="ds-card fillback-form-card">
      <v-card-title class="fillback-card-title">
        <span>补数日期</span>
        <v-chip v-if="selectedDates.length" size="small" variant="tonal" color="primary">
          已选 {{ selectedDates.length }} 天
        </v-chip>
      </v-card-title>
      <v-card-text>
        <v-alert type="info" variant="tonal" density="comfortable" class="mb-4">
          按所选日期逐日回补：目标日期作为 <code>biz_*</code>，<code>curr_*</code> 使用该次补数生成任务时的真实时间，
          并随 <code>biz_*</code> 一起冻结进任务文件；补数按系统模板重新生成，不包含手工编辑的作业内容。
        </v-alert>

        <div class="date-picker-layout">
          <v-date-picker
            v-model="pendingRange"
            multiple="range"
            first-day-of-week="1"
            show-adjacent-months
            :max="maxDate || undefined"
            color="primary"
            class="fillback-picker"
          >
            <!-- Keep the compact "选择日期" title, drop the large "输入日期" header. -->
            <template #header><span /></template>
          </v-date-picker>
          <div class="date-picker-side">
            <p class="picker-hint">
              点选一天作为起点，再点一天作为终点；只点一天可直接添加为单日。可以分多次添加不连续的区间。
              <template v-if="maxDate">
                可选日期不晚于业务日期 {{ maxDate }}。
              </template>
            </p>
            <div class="picker-actions">
              <v-btn
                color="primary"
                variant="flat"
                :disabled="pendingRange.length === 0"
                @click="addSelection"
              >
                添加
              </v-btn>
              <v-btn variant="text" :disabled="pendingRange.length === 0" @click="pendingRange = []">
                清空选择
              </v-btn>
            </div>
            <div v-if="exceedsLimit" class="picker-warning">
              已超过上限 {{ MAX_TASKS }} 天，请删除部分日期后再提交。
            </div>
          </div>
        </div>

        <div v-if="groupedRanges.length" class="selected-ranges">
          <div class="selected-ranges__head">
            <span class="selected-ranges__title">已选日期（{{ selectedDates.length }} 天）</span>
            <v-btn size="small" variant="text" @click="clearSelection">清空全部</v-btn>
          </div>
          <div class="range-chips">
            <v-chip
              v-for="range in groupedRanges"
              :key="range.start + '~' + range.end"
              closable
              size="small"
              variant="tonal"
              color="primary"
              @click:close="removeRange(range)"
            >
              {{ rangeLabel(range) }}
            </v-chip>
          </div>
        </div>

        <div class="selection-summary" :class="{ 'selection-summary--error': exceedsLimit }">
          <v-icon size="18" :color="exceedsLimit ? 'error' : 'success'" class="mr-2">
            {{ exceedsLimit ? 'mdi-alert-circle' : 'mdi-check-circle' }}
          </v-icon>
          共 {{ selectedDates.length }} 天 × 1 张表 = {{ selectedDates.length }} 个任务（上限
          {{ MAX_TASKS }}）
        </div>

        <v-alert v-if="submitError" type="error" variant="tonal" class="mt-3">
          {{ submitError }}
        </v-alert>
      </v-card-text>
      <v-card-actions v-if="authStore.canWrite" class="fillback-actions">
        <v-spacer />
        <v-btn
          color="primary"
          variant="flat"
          :loading="submitting"
          :disabled="!canSubmit"
          @click="submit"
        >
          开始补数
        </v-btn>
      </v-card-actions>
    </v-card>

    <v-card v-if="progress" flat class="ds-card progress-card mt-4">
      <v-card-title class="fillback-card-title">
        <span>补数进度</span>
        <v-chip size="small" :color="stateMeta.color" variant="tonal">{{ stateMeta.label }}</v-chip>
      </v-card-title>
      <v-card-text>
        <div v-if="progress.found" class="progress-meta">
          <span class="progress-batch" :title="progress.batchId || ''">
            批次 {{ shortBatch }}
          </span>
          <span v-if="progress.submitter">提交人：{{ progress.submitter }}</span>
          <span v-if="progress.submittedAt">提交时间：{{ formatTime(progress.submittedAt) }}</span>
          <v-spacer />
          <v-btn size="small" variant="text" prepend-icon="mdi-refresh" :loading="loading" @click="loadProgress(currentBatchId || undefined)">
            刷新
          </v-btn>
        </div>

        <template v-if="progress.found">
          <div class="count-chips">
            <v-chip
              v-for="chip in countChips"
              :key="chip.label"
              size="small"
              :color="chip.color"
              variant="tonal"
            >
              {{ chip.label }} {{ chip.value }}
            </v-chip>
          </div>

          <v-progress-linear
            :model-value="progressPercent"
            :color="progressColor"
            height="10"
            rounded
            class="mt-4"
          />
          <div class="progress-caption">
            已完成 {{ doneCount }} / {{ progress.counts.total }}
            <span v-if="progress.counts.unresolved > 0">
              （{{ progress.counts.unresolved }} 个日期未入队或明细已清理）
            </span>
          </div>

          <v-table v-if="progress.items.length" density="compact" class="item-table mt-3">
            <thead>
              <tr>
                <th>日期</th>
                <th>状态</th>
                <th>尝试</th>
                <th>开始时间</th>
                <th>结束时间</th>
                <th>错误</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in progress.items" :key="item.date">
                <td class="text-no-wrap">{{ item.date }}</td>
                <td>
                  <v-chip
                    size="small"
                    :color="statusMeta(item.status).color"
                    :prepend-icon="statusMeta(item.status).icon"
                    variant="tonal"
                  >
                    {{ statusMeta(item.status).label }}
                  </v-chip>
                </td>
                <td class="text-no-wrap">{{ item.attempts }} / {{ item.maxAttempts }}</td>
                <td class="text-no-wrap">{{ formatTime(item.startedAt) }}</td>
                <td class="text-no-wrap">{{ formatTime(item.finishedAt) }}</td>
                <td class="error-cell" :title="item.lastError || ''">
                  {{ item.lastError || '-' }}
                </td>
              </tr>
            </tbody>
          </v-table>
        </template>

        <v-alert v-else type="info" variant="tonal" class="mt-2">
          尚无补数记录。选择日期后提交；如果刚提交的任务全部被跳过，刷新后不会保留进度明细。
        </v-alert>

        <v-alert v-if="progressError" type="error" variant="tonal" class="mt-3">
          {{ progressError }}
        </v-alert>
      </v-card-text>
    </v-card>

    <v-card v-else flat class="ds-card progress-card mt-4">
      <v-card-text v-if="progressError" class="loading-state">
        <v-alert type="error" variant="tonal" class="flex-grow-1">
          {{ progressError }}
          <template #append>
            <v-btn size="small" variant="text" @click="loadProgress()">重试</v-btn>
          </template>
        </v-alert>
      </v-card-text>
      <v-card-text v-else class="loading-state">
        <v-progress-circular indeterminate color="primary" size="22" class="mr-2" />
        正在加载补数信息…
      </v-card-text>
    </v-card>
  </div>
</template>

<script setup lang="ts">
  import { computed, onUnmounted, ref, watch } from 'vue';
  import { useRoute } from 'vue-router';
  import dayjs from 'dayjs';
  import { notify } from '@/stores/notifier';
  import { useAuthStore } from '@/stores/auth';
  import taskService from '@/service/task-service';
  import type {
    FillbackProgress,
    FillbackResult,
  } from '@/service/task-service';

  const MAX_TASKS = 20;
  const POLL_INTERVAL = 3000;

  const props = withDefaults(defineProps<{ active?: boolean }>(), { active: true });
  const route = useRoute();
  const authStore = useAuthStore();
  const tid = computed(() => Number((route.params as Record<string, string>).tid));
  const routeBatchId =
    typeof route.query.batchId === 'string' ? route.query.batchId : undefined;

  const pendingRange = ref<unknown[]>([]);
  const selectedDates = ref<string[]>([]);
  const submitting = ref(false);
  const submitResult = ref<FillbackResult | null>(null);
  const submitError = ref('');
  const progress = ref<FillbackProgress | null>(null);
  const progressError = ref('');
  const loading = ref(false);
  const capabilityLoaded = ref(false);
  const currentBatchId = ref<string | null>(null);

  let pollTimer: ReturnType<typeof setTimeout> | null = null;
  let pollToken = 0;

  const supported = computed(() => progress.value?.supported !== false);
  const maxDate = computed(() => progress.value?.maxDate || null);
  const exceedsLimit = computed(() => selectedDates.value.length > MAX_TASKS);
  const datesWithinLimit = computed(() => {
    if (!maxDate.value) return true;
    const max = dayjs(maxDate.value);
    return selectedDates.value.every(date => !dayjs(date).isAfter(max, 'day'));
  });
  const canSubmit = computed(
    () =>
      capabilityLoaded.value &&
      supported.value &&
      selectedDates.value.length > 0 &&
      !exceedsLimit.value &&
      datesWithinLimit.value &&
      !submitting.value
  );

  interface DateRange {
    start: string;
    end: string;
    days: number;
  }

  const groupedRanges = computed(() => groupDates(selectedDates.value));
  const skippedDates = computed(() => {
    if (!submitResult.value) return [];
    const enqueued = new Set(submitResult.value.details.flatMap(d => d.dates));
    return selectedDates.value.filter(date => !enqueued.has(date));
  });

  const doneCount = computed(() => {
    const counts = progress.value?.counts;
    return counts ? counts.completed + counts.failed + counts.cancelled : 0;
  });
  const progressPercent = computed(() => {
    const counts = progress.value?.counts;
    if (!counts || counts.total === 0) return 0;
    return Math.round((doneCount.value / counts.total) * 100);
  });
  const progressColor = computed(() => {
    const counts = progress.value?.counts;
    if (!counts) return 'info';
    if (counts.failed > 0 && counts.pending + counts.running === 0) return 'error';
    if (counts.completed === counts.total) return 'success';
    return 'info';
  });
  const countChips = computed(() => {
    const counts = progress.value?.counts;
    if (!counts) return [];
    return [
      { label: '等待', value: counts.pending, color: 'secondary' },
      { label: '执行中', value: counts.running, color: 'info' },
      { label: '成功', value: counts.completed, color: 'success' },
      { label: '失败', value: counts.failed, color: 'error' },
      { label: '取消', value: counts.cancelled, color: 'warning' },
      { label: '未入队', value: counts.unresolved, color: 'grey' },
    ];
  });
  const shortBatch = computed(() =>
    progress.value?.batchId ? progress.value.batchId.slice(0, 8) : '-'
  );

  const stateLabels: Record<string, { label: string; color: string }> = {
    UNKNOWN: { label: '未知', color: 'grey' },
    SKIPPED: { label: '全部跳过', color: 'warning' },
    PENDING: { label: '排队中', color: 'secondary' },
    RUNNING: { label: '执行中', color: 'info' },
    COMPLETED: { label: '已完成', color: 'success' },
    PARTIAL_SUCCESS: { label: '部分成功', color: 'warning' },
    FAILED: { label: '失败', color: 'error' },
    CANCELLED: { label: '已取消', color: 'warning' },
    HISTORY_PRUNED: { label: '明细已清理', color: 'grey' },
  };
  const stateMeta = computed(
    () => stateLabels[progress.value?.state || 'UNKNOWN'] || stateLabels.UNKNOWN
  );

  const statusLabels: Record<string, { label: string; color: string; icon: string }> = {
    pending: { label: '等待中', color: 'secondary', icon: 'mdi-clock-outline' },
    running: { label: '执行中', color: 'info', icon: 'mdi-progress-clock' },
    completed: { label: '成功', color: 'success', icon: 'mdi-check-circle' },
    failed: { label: '失败', color: 'error', icon: 'mdi-alert-circle' },
    cancelled: { label: '已取消', color: 'warning', icon: 'mdi-cancel' },
    unresolved: {
      label: '未入队/明细已清理',
      color: 'grey',
      icon: 'mdi-help-circle-outline',
    },
  };

  function statusMeta(status: string) {
    return (
      statusLabels[status] || {
        label: status,
        color: 'grey',
        icon: 'mdi-help-circle-outline',
      }
    );
  }

  function normalizeDate(value: unknown): string | null {
    if (value === null || value === undefined) return null;
    const parsed = dayjs(value as string | Date);
    return parsed.isValid() ? parsed.format('YYYY-MM-DD') : null;
  }

  function expandDates(picked: string[]): string[] {
    if (picked.length === 0) return [];
    const start = dayjs(picked[0]);
    const end = dayjs(picked[picked.length - 1]);
    if (!start.isValid() || !end.isValid()) return [];
    const dates: string[] = [];
    for (let current = start; current.isBefore(end) || current.isSame(end); current = current.add(1, 'day')) {
      dates.push(current.format('YYYY-MM-DD'));
    }
    return dates;
  }

  function addSelection() {
    const picked = pendingRange.value
      .map(normalizeDate)
      .filter((date): date is string => !!date)
      .sort();
    if (picked.length === 0) return;

    const expanded = expandDates(picked);
    if (maxDate.value) {
      const max = dayjs(maxDate.value);
      if (expanded.some(date => dayjs(date).isAfter(max, 'day'))) {
        notify(`补数日期不能晚于业务日期 ${maxDate.value}`, 'warning');
        return;
      }
    }

    const merged = new Set([...selectedDates.value, ...expanded]);
    if (merged.size > MAX_TASKS) {
      notify(`补数日期最多 ${MAX_TASKS} 天，请减少选择`, 'warning');
      return;
    }
    selectedDates.value = [...merged].sort();
    pendingRange.value = [];
    submitResult.value = null;
  }

  function clearSelection() {
    selectedDates.value = [];
    submitResult.value = null;
  }

  function groupDates(dates: string[]): DateRange[] {
    const sorted = [...dates].sort();
    const ranges: DateRange[] = [];
    for (const date of sorted) {
      const last = ranges[ranges.length - 1];
      if (last && dayjs(date).diff(dayjs(last.end), 'day') === 1) {
        last.end = date;
        last.days += 1;
      } else {
        ranges.push({ start: date, end: date, days: 1 });
      }
    }
    return ranges;
  }

  function rangeLabel(range: DateRange) {
    return range.days > 1
      ? `${range.start} ~ ${range.end}（${range.days} 天）`
      : `${range.start}（单日）`;
  }

  function removeRange(range: DateRange) {
    const start = dayjs(range.start);
    const end = dayjs(range.end);
    selectedDates.value = selectedDates.value.filter(date => {
      const current = dayjs(date);
      return current.isBefore(start) || current.isAfter(end);
    });
    submitResult.value = null;
  }

  function formatTime(value: string | null | undefined) {
    if (!value) return '-';
    const parsed = dayjs(value);
    return parsed.isValid() ? parsed.format('YYYY-MM-DD HH:mm:ss') : value;
  }

  function hasActiveRows() {
    const counts = progress.value?.counts;
    return !!progress.value?.found && !!counts && counts.pending + counts.running > 0;
  }

  function stopPolling() {
    pollToken += 1;
    if (pollTimer) {
      clearTimeout(pollTimer);
      pollTimer = null;
    }
  }

  function startPolling() {
    stopPolling();
    if (!props.active || !hasActiveRows()) return;
    const token = pollToken;
    pollTimer = setTimeout(() => pollOnce(token), POLL_INTERVAL);
  }

  async function pollOnce(token: number) {
    pollTimer = null;
    if (token !== pollToken || !props.active || !hasActiveRows()) return;
    try {
      const res = await taskService.getFillbackProgress(
        tid.value,
        currentBatchId.value || undefined
      );
      if (token !== pollToken) return;
      progress.value = res.data;
      progressError.value = '';
      if (res.data.batchId) currentBatchId.value = res.data.batchId;
    } catch (e) {
      if (token === pollToken) {
        progressError.value = `刷新补数进度失败：${(e as Error)?.message || String(e)}`;
      }
    }
    if (token === pollToken) startPolling();
  }

  async function loadProgress(batchId?: string) {
    loading.value = !progress.value;
    progressError.value = '';
    try {
      const res = await taskService.getFillbackProgress(tid.value, batchId || routeBatchId);
      progress.value = res.data;
      capabilityLoaded.value = true;
      if (res.data.batchId) currentBatchId.value = res.data.batchId;
      // Restore the batch's dates only when the form is empty: a running batch must not overwrite
      // dates the user is already picking for the next submission.
      if (res.data.found && res.data.items.length && selectedDates.value.length === 0) {
        selectedDates.value = res.data.items.map(item => item.date).sort();
      }
      startPolling();
    } catch (e) {
      progressError.value = `加载补数进度失败：${(e as Error)?.message || String(e)}`;
      capabilityLoaded.value = true;
    } finally {
      loading.value = false;
    }
  }

  async function submit() {
    if (!canSubmit.value) return;
    submitting.value = true;
    submitError.value = '';
    try {
      const res = await taskService.fillback({
        tids: [tid.value],
        dates: [...selectedDates.value],
      });
      submitResult.value = res.data;
      currentBatchId.value = res.data.batchId;
      if (res.data.totalEnqueued > 0) {
        notify(`补数任务已提交 ${res.data.totalEnqueued} 个`, 'success');
      } else {
        notify('所有日期均被跳过，请查看原因', 'warning');
      }
      await loadProgress(res.data.batchId);
    } catch (e) {
      submitError.value = `补数提交失败：${(e as Error)?.message || String(e)}`;
      notify(submitError.value, 'error');
    } finally {
      submitting.value = false;
    }
  }

  watch(
    () => props.active,
    active => {
      if (active) {
        if (!progress.value && !loading.value) {
          loadProgress();
        } else {
          startPolling();
        }
      } else {
        stopPolling();
      }
    },
    { immediate: true }
  );

  watch(tid, () => {
    stopPolling();
    progress.value = null;
    progressError.value = '';
    submitResult.value = null;
    submitError.value = '';
    selectedDates.value = [];
    pendingRange.value = [];
    currentBatchId.value = null;
    capabilityLoaded.value = false;
    if (props.active) loadProgress();
  });

  onUnmounted(stopPolling);
</script>

<style scoped>
  .fillback-card-title {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
    flex-wrap: wrap;
  }

  .date-picker-layout {
    display: grid;
    grid-template-columns: minmax(0, 420px) minmax(220px, 1fr);
    gap: 20px;
    align-items: start;
  }

  .fillback-picker {
    width: 100%;
    border: 1px solid rgba(var(--v-theme-on-surface), 0.08);
    border-radius: 14px;
  }

  .date-picker-side {
    display: flex;
    flex-direction: column;
    gap: 12px;
  }

  .picker-hint {
    margin: 0;
    color: rgba(var(--v-theme-on-surface), 0.64);
    font-size: 0.86rem;
    line-height: 1.7;
  }

  .picker-actions {
    display: flex;
    align-items: center;
    gap: 4px;
  }

  .picker-warning {
    color: rgb(var(--v-theme-error));
    font-size: 0.82rem;
    font-weight: 600;
  }

  .selected-ranges {
    margin-top: 20px;
    padding-top: 16px;
    border-top: 1px solid rgba(var(--v-theme-on-surface), 0.08);
  }

  .selected-ranges__head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
  }

  .selected-ranges__title {
    font-weight: 600;
    font-size: 0.88rem;
  }

  .range-chips {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    margin-top: 8px;
  }

  .selection-summary {
    display: flex;
    align-items: center;
    margin-top: 16px;
    padding: 10px 14px;
    border-radius: 10px;
    background: rgba(var(--v-theme-on-surface), 0.02);
    color: rgba(var(--v-theme-on-surface), 0.72);
    font-size: 0.86rem;
  }

  .selection-summary--error {
    color: rgb(var(--v-theme-error));
    background: rgba(var(--v-theme-error), 0.06);
  }

  .fillback-actions {
    padding: 12px 16px 16px;
  }

  .progress-meta {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 12px;
    color: rgba(var(--v-theme-on-surface), 0.6);
    font-size: 0.82rem;
  }

  .progress-batch {
    font-family: 'SF Mono', Monaco, Consolas, monospace;
  }

  .count-chips {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    margin-top: 12px;
  }

  .progress-caption {
    margin-top: 8px;
    color: rgba(var(--v-theme-on-surface), 0.6);
    font-size: 0.82rem;
  }

  .item-table :deep(th) {
    white-space: nowrap;
  }

  .error-cell {
    max-width: 320px;
    overflow: hidden;
    color: rgb(var(--v-theme-error));
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .loading-state {
    display: flex;
    align-items: center;
    color: rgba(var(--v-theme-on-surface), 0.6);
    font-size: 0.9rem;
  }

  @media (max-width: 960px) {
    .date-picker-layout {
      grid-template-columns: 1fr;
    }
  }
</style>

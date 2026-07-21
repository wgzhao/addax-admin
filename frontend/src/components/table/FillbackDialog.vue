<template>
  <v-card flat class="fillback-card">
    <div class="fillback-head">
      <div>
        <div class="fillback-title-row">
          <div class="fillback-title">补采数据</div>
          <v-chip size="small" variant="tonal" color="primary">{{ selectedCount }} 张表</v-chip>
        </div>
      </div>
    </div>

    <v-alert type="info" variant="tonal" density="comfortable" class="mx-6 mt-2 mb-0">
      对选中的采集表在指定日期范围内逐日重新采集，使用补数日期作为业务日期。非分区表会被自动跳过。
    </v-alert>

    <div class="form-grid">
      <div class="field-card">
        <div class="field-card__head">
          <div class="field-card__title">开始日期</div>
          <div class="field-card__desc">补数起始日（含）</div>
        </div>
        <v-text-field
          v-model="startDate"
          type="date"
          density="comfortable"
          variant="outlined"
          hide-details
          :max="endDate || undefined"
        />
      </div>

      <div class="field-card">
        <div class="field-card__head">
          <div class="field-card__title">结束日期</div>
          <div class="field-card__desc">补数截止日（含）</div>
        </div>
        <v-text-field
          v-model="endDate"
          type="date"
          density="comfortable"
          variant="outlined"
          hide-details
          :min="startDate || undefined"
        />
      </div>
    </div>

    <!-- live task count preview -->
    <div class="preview-strip mx-6 mt-4">
      <template v-if="dayCount > 0">
        <v-icon size="18" :color="exceedsLimit ? 'error' : 'success'" class="mr-2">
          {{ exceedsLimit ? 'mdi-alert-circle' : 'mdi-check-circle' }}
        </v-icon>
        <span :class="exceedsLimit ? 'preview-text--error' : 'preview-text--ok'">
          共 {{ selectedCount }} 张表 × {{ dayCount }} 天 = {{ totalTasks }} 个任务
        </span>
        <span v-if="exceedsLimit" class="preview-hint ml-2">
          （超过上限 {{ MAX_TASKS }}，请缩小范围）
        </span>
      </template>
      <template v-else>
        <span class="preview-hint">请选择日期范围</span>
      </template>
    </div>

    <!-- result section -->
    <template v-if="result">
      <v-divider class="mt-4" />
      <div class="result-section mx-6 my-4">
        <div class="result-summary">
          <v-chip size="small" color="success" variant="tonal" class="mr-2">
            入队 {{ result.totalEnqueued }}
          </v-chip>
          <v-chip v-if="result.skipped > 0" size="small" color="warning" variant="tonal">
            跳过 {{ result.skipped }}
          </v-chip>
        </div>
        <div class="result-list mt-3">
          <div
            v-for="d in result.details"
            :key="d.tid"
            class="result-item"
            :class="{ 'result-item--skipped': d.skipped }"
          >
            <div class="result-item__main">
              <span class="result-item__table">{{ d.table }}</span>
              <span v-if="d.skipped" class="result-item__reason">{{ d.reason }}</span>
              <span v-else class="result-item__dates">{{ d.dates.join(', ') }}</span>
            </div>
            <v-chip
              size="x-small"
              :color="d.skipped ? 'warning' : 'success'"
              variant="flat"
            >
              {{ d.skipped ? '跳过' : `${d.enqueued} 个` }}
            </v-chip>
          </div>
        </div>
      </div>
    </template>

    <v-card-actions class="fillback-actions">
      <v-spacer />
      <v-btn variant="text" @click="emit('closeDialog')">取消</v-btn>
      <v-btn
        v-if="!result"
        color="primary"
        variant="flat"
        :disabled="!canSubmit"
        :loading="loading"
        @click="submit"
      >
        提交补采
      </v-btn>
      <v-btn v-else color="primary" variant="flat" @click="emit('closeDialog')">
        完成
      </v-btn>
    </v-card-actions>
  </v-card>
</template>

<script setup lang="ts">
  import { ref, computed } from 'vue';
  import { notify } from '@/stores/notifier';
  import taskService from '@/service/task-service';
  import type { FillbackResult } from '@/service/task-service';

  const MAX_TASKS = 20;

  const props = defineProps({
    tid: {
      type: Array as () => number[],
      required: true,
    },
  });

  const emit = defineEmits(['closeDialog']);

  const startDate = ref('');
  const endDate = ref('');
  const loading = ref(false);
  const result = ref<FillbackResult | null>(null);

  const selectedCount = computed(() => (props.tid ? props.tid.length : 0));

  const dayCount = computed(() => {
    if (!startDate.value || !endDate.value) return 0;
    const start = new Date(startDate.value);
    const end = new Date(endDate.value);
    if (start > end) return 0;
    const diff = Math.floor((end.getTime() - start.getTime()) / (1000 * 60 * 60 * 24)) + 1;
    return diff;
  });

  const totalTasks = computed(() => selectedCount.value * dayCount.value);
  const exceedsLimit = computed(() => totalTasks.value > MAX_TASKS);
  const canSubmit = computed(
    () => startDate.value && endDate.value && dayCount.value > 0 && !exceedsLimit.value
  );

  async function submit() {
    if (!canSubmit.value) return;
    loading.value = true;
    try {
      const res = await taskService.fillback({
        tids: [...props.tid],
        startDate: startDate.value,
        endDate: endDate.value,
      });
      result.value = res.data;
      if (res.data.totalEnqueued > 0) {
        notify(`补采任务已提交 ${res.data.totalEnqueued} 个`, 'success');
      } else if (res.data.skipped > 0) {
        notify('所有表均被跳过，请查看详情', 'warning');
      }
    } catch (err) {
      const msg = err instanceof Error ? err.message : String(err);
      notify(`补采提交失败: ${msg}`, 'error');
    } finally {
      loading.value = false;
    }
  }
</script>

<style scoped>
  .fillback-card {
    background: rgb(var(--v-theme-surface));
  }

  .fillback-head {
    padding: 22px 24px 14px;
    border-bottom: 1px solid rgba(var(--v-theme-on-surface), 0.08);
    background: linear-gradient(180deg, rgba(var(--v-theme-primary), 0.06), transparent 95%);
  }

  .fillback-title-row {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
  }

  .fillback-title {
    font-size: 1.05rem;
    font-weight: 700;
    color: rgb(var(--v-theme-on-surface));
  }

  .form-grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 16px;
    padding: 18px 24px 0;
  }

  .field-card {
    padding: 18px;
    border-radius: 16px;
    border: 1px solid rgba(var(--v-theme-on-surface), 0.08);
    background: rgba(var(--v-theme-on-surface), 0.015);
  }

  .field-card__head {
    margin-bottom: 14px;
  }

  .field-card__title {
    font-weight: 600;
    color: rgb(var(--v-theme-on-surface));
  }

  .field-card__desc {
    margin-top: 4px;
    font-size: 0.84rem;
    line-height: 1.6;
    color: rgba(var(--v-theme-on-surface), 0.62);
  }

  .preview-strip {
    display: flex;
    align-items: center;
    padding: 10px 16px;
    border-radius: 12px;
    border: 1px solid rgba(var(--v-theme-on-surface), 0.08);
    background: rgba(var(--v-theme-on-surface), 0.02);
    font-size: 0.88rem;
  }

  .preview-text--ok {
    color: rgb(var(--v-theme-success));
    font-weight: 600;
  }

  .preview-text--error {
    color: rgb(var(--v-theme-error));
    font-weight: 600;
  }

  .preview-hint {
    color: rgba(var(--v-theme-on-surface), 0.5);
    font-size: 0.82rem;
  }

  .result-section {
    display: flex;
    flex-direction: column;
  }

  .result-summary {
    display: flex;
    align-items: center;
    gap: 4px;
  }

  .result-list {
    display: flex;
    flex-direction: column;
    gap: 8px;
  }

  .result-item {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 10px 14px;
    border-radius: 10px;
    border: 1px solid rgba(var(--v-theme-on-surface), 0.06);
    background: rgba(var(--v-theme-on-surface), 0.015);
  }

  .result-item--skipped {
    border-color: rgba(var(--v-theme-warning), 0.2);
    background: rgba(var(--v-theme-warning), 0.04);
  }

  .result-item__main {
    display: flex;
    flex-direction: column;
    gap: 2px;
    min-width: 0;
  }

  .result-item__table {
    font-weight: 600;
    font-size: 0.86rem;
    color: rgb(var(--v-theme-on-surface));
  }

  .result-item__dates {
    font-size: 0.78rem;
    color: rgba(var(--v-theme-on-surface), 0.55);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .result-item__reason {
    font-size: 0.78rem;
    color: rgb(var(--v-theme-warning));
  }

  .fillback-actions {
    padding: 14px 24px 20px;
    border-top: 1px solid rgba(var(--v-theme-on-surface), 0.08);
  }

  @media (max-width: 760px) {
    .form-grid {
      grid-template-columns: 1fr;
    }

    .fillback-head,
    .form-grid,
    .fillback-actions {
      padding-left: 16px;
      padding-right: 16px;
    }
  }
</style>

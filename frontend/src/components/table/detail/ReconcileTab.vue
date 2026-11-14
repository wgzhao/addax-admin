<template>
  <div class="reconcile-tab">
    <div class="reconcile-tab__toolbar">
      <v-alert type="info" variant="tonal" density="compact" class="reconcile-tab__hint">
        对账核对 Addax 上报的「读取行数」与「写入行数」是否一致（写入行数 = 读取行数 − 错误行数）；有错误行数会进入失败记录并可触发告警。
      </v-alert>
      <v-spacer />
      <v-btn variant="text" size="small" prepend-icon="mdi-refresh" :loading="loading" @click="load">
        刷新
      </v-btn>
    </div>

    <v-alert v-if="error" type="error" variant="tonal" class="mb-3">{{ error }}</v-alert>

    <v-card v-if="canWrite" flat class="ds-card reconcile-run-card mb-4">
      <v-card-text class="reconcile-run-card__content">
        <v-text-field
          v-model="bizDate"
          type="date"
          label="业务日期"
          density="compact"
          variant="outlined"
          hide-details
          class="reconcile-run-card__date"
        />
        <v-btn color="primary" variant="flat" :loading="running" :disabled="!bizDate" @click="run">
          立即对账
        </v-btn>
      </v-card-text>
    </v-card>

    <v-data-table
      :headers="headers"
      :items="items"
      :loading="loading"
      item-value="id"
      density="comfortable"
      no-data-text="该采集表暂无对账记录"
      class="reconcile-tab__table"
    >
      <template #item.bizDate="{ item }">
        <span class="text-no-wrap">{{ item.bizDate }}</span>
      </template>
      <template #item.status="{ item }">
        <v-chip size="small" :color="statusMeta(item.status).color" variant="tonal">
          {{ statusMeta(item.status).label }}
        </v-chip>
      </template>
      <template #item.sourceRows="{ item }">
        <span class="text-no-wrap">{{ formatCount(item.sourceRows) }}</span>
      </template>
      <template #item.targetRows="{ item }">
        <span class="text-no-wrap">{{ formatCount(item.targetRows) }}</span>
      </template>
      <template #item.diff="{ item }">
        <span v-if="item.diff === null || item.diff === undefined" class="text-no-wrap">-</span>
        <span v-else class="text-no-wrap" :class="{ 'text-error': item.diff !== 0 }">
          {{ item.diff }}
        </span>
      </template>
      <template #item.createdAt="{ item }">
        <span class="text-no-wrap">{{ formatTime(item.createdAt) }}</span>
      </template>
      <template #item.message="{ item }">
        <span class="reconcile-tab__msg" :title="item.message || ''">{{ item.message || '-' }}</span>
      </template>
    </v-data-table>
  </div>
</template>

<script setup lang="ts">
  import { computed, ref, watch } from 'vue';
  import { useRoute } from 'vue-router';
  import dayjs from 'dayjs';
  import { notify } from '@/stores/notifier';
  import reconcileService, { type ReconcileRecord } from '@/service/reconcile-service';

  const props = withDefaults(defineProps<{ active?: boolean; canWrite?: boolean }>(), {
    active: true,
    canWrite: false,
  });

  const route = useRoute();
  const tid = computed(() => Number((route.params as Record<string, string>).tid));

  const items = ref<ReconcileRecord[]>([]);
  const loading = ref(false);
  const error = ref('');
  const running = ref(false);
  const bizDate = ref(dayjs().format('YYYY-MM-DD'));

  const statusLabels: Record<string, { label: string; color: string }> = {
    PASS: { label: '一致', color: 'success' },
    FAIL: { label: '不一致', color: 'error' },
  };

  const headers = [
    { title: '业务日期', key: 'bizDate', width: 110, sortable: false },
    { title: '状态', key: 'status', width: 90, sortable: false },
    { title: '读取行数', key: 'sourceRows', width: 110, sortable: false, align: 'end' as const },
    { title: '写入行数', key: 'targetRows', width: 120, sortable: false, align: 'end' as const },
    { title: '差异', key: 'diff', width: 90, sortable: false, align: 'end' as const },
    { title: '对账时间', key: 'createdAt', width: 170, sortable: false },
    { title: '说明', key: 'message', sortable: false },
  ];

  function statusMeta(status: string) {
    return statusLabels[status] || { label: status, color: 'grey' };
  }

  function formatCount(value: number | null | undefined) {
    if (value === null || value === undefined) return '-';
    return value.toLocaleString();
  }

  function formatTime(value: string | null | undefined) {
    if (!value) return '-';
    const parsed = dayjs(value);
    return parsed.isValid() ? parsed.format('YYYY-MM-DD HH:mm:ss') : value;
  }

  async function load() {
    if (!tid.value) return;
    loading.value = true;
    error.value = '';
    try {
      items.value = await reconcileService.list(tid.value);
    } catch (e) {
      error.value = `加载对账记录失败：${(e as Error)?.message || String(e)}`;
    } finally {
      loading.value = false;
    }
  }

  async function run() {
    if (!bizDate.value || running.value) return;
    running.value = true;
    error.value = '';
    try {
      const record = await reconcileService.run(tid.value, bizDate.value);
      if (record.status === 'FAIL') {
        notify(`对账不一致：${record.message || ''}`, 'error');
      } else {
        notify('对账一致', 'success');
      }
      await load();
    } catch (e) {
      error.value = `对账失败：${(e as Error)?.message || String(e)}`;
      notify(error.value, 'error');
    } finally {
      running.value = false;
    }
  }

  watch(
    () => props.active,
    active => {
      if (active && !items.value.length) load();
    }
  );

  watch(tid, () => {
    items.value = [];
    error.value = '';
    if (props.active) load();
  });
</script>

<style scoped>
  .reconcile-tab__toolbar {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-bottom: 16px;
  }

  .reconcile-tab__hint {
    flex: 1;
    min-width: 0;
  }

  .reconcile-run-card__content {
    display: flex;
    align-items: center;
    gap: 12px;
    flex-wrap: wrap;
  }

  .reconcile-run-card__date {
    max-width: 220px;
    min-width: 180px;
  }

  .reconcile-tab__msg {
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
    word-break: break-word;
  }
</style>

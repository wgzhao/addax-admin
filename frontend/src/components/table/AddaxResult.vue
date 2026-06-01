<template>
  <v-card flat class="ds-card table-panel addax-result-panel">
    <v-card-text class="table-panel__content">
      <div class="summary-grid">
        <div class="summary-card">
          <span class="summary-label">最近业务日期</span>
          <strong>{{ latestResult?.runDate || '-' }}</strong>
        </div>
        <div class="summary-card">
          <span class="summary-label">累计记录数</span>
          <strong>{{ formatNumber(totalRecords) }}</strong>
        </div>
        <div class="summary-card">
          <span class="summary-label">累计错误数</span>
          <strong :class="{ 'text-error': totalErrors > 0 }">{{
            formatNumber(totalErrors)
          }}</strong>
        </div>
        <div class="summary-card">
          <span class="summary-label">峰值行速</span>
          <strong>{{ formatNumber(peakRecSpeed) }} 行/秒</strong>
        </div>
      </div>

      <div v-if="loading" class="panel-state">
        <v-progress-circular indeterminate color="primary" size="42" width="4" />
        <div class="panel-state__title">正在加载统计结果</div>
      </div>

      <div v-else-if="error" class="panel-state panel-state--error">
        <v-icon size="44" color="error">mdi-alert-circle-outline</v-icon>
        <div class="panel-state__title">结果加载失败</div>
        <v-alert type="error" variant="tonal" class="mt-3">
          {{ error }}
        </v-alert>
      </div>

      <div v-else-if="results.length" class="result-table-shell ds-table-wrap">
        <div class="result-table-shell__header">
          <div>
            <div class="result-table-shell__title">结果明细表</div>
          </div>
          <v-btn
            size="small"
            variant="tonal"
            color="primary"
            prepend-icon="mdi-download"
            :disabled="!results.length"
            @click="exportCsv"
          >
            导出 CSV
          </v-btn>
        </div>

        <v-data-table-virtual
          :items="sortedResults"
          :headers="headers"
          density="comfortable"
          class="result-table"
        >
          <template #item.runDate="{ item }">
            <span class="cell-emphasis">{{ item.runDate || '-' }}</span>
          </template>

          <template #item.startAt="{ item }">
            <span>{{ item.startAt || '-' }}</span>
          </template>

          <template #item.endAt="{ item }">
            <span>{{ item.endAt || '-' }}</span>
          </template>

          <template #item.takeSecs="{ item }">
            <v-chip size="small" variant="tonal" color="primary">
              {{ item.takeSecs ?? 0 }}s
            </v-chip>
          </template>

          <template #item.byteSpeed="{ item }">
            <span class="mono-cell">{{ formatNumber(item.byteSpeed) }}</span>
          </template>

          <template #item.recSpeed="{ item }">
            <span class="mono-cell">{{ formatNumber(item.recSpeed) }}</span>
          </template>

          <template #item.totalRecs="{ item }">
            <span class="cell-emphasis">{{ formatNumber(item.totalRecs) }}</span>
          </template>

          <template #item.totalErrors="{ item }">
            <v-chip
              size="small"
              :color="(item.totalErrors || 0) > 0 ? 'error' : 'success'"
              :variant="(item.totalErrors || 0) > 0 ? 'tonal' : 'outlined'"
            >
              {{ formatNumber(item.totalErrors) }}
            </v-chip>
          </template>
        </v-data-table-virtual>
      </div>

      <div v-else class="panel-state">
        <v-icon size="48" class="panel-state__icon">mdi-chart-box-outline</v-icon>
        <div class="panel-state__title">暂无采集结果</div>
        <div class="panel-state__desc">当前采集表还没有可展示的执行统计记录。</div>
      </div>
    </v-card-text>
  </v-card>
</template>

<script setup lang="ts">
  import { ref, onMounted, computed } from 'vue';
  import { useRoute } from 'vue-router';
  import type { DataTableHeader } from 'vuetify';
  import { notify } from '@/stores/notifier';
  import tableService from '@/service/table-service';
  import type { EtlStatistic } from '@/types/database';

  const route = useRoute();
  const tid = String(route.params.tid);

  const headers: DataTableHeader[] = [
    { title: '业务日期', key: 'runDate', sortable: true },
    { title: '任务开始时间', key: 'startAt', sortable: true },
    { title: '任务结束时间', key: 'endAt', sortable: true },
    { title: '运行耗时', key: 'takeSecs', sortable: true },
    { title: '字节/秒', key: 'byteSpeed', sortable: true },
    { title: '行/秒', key: 'recSpeed', sortable: true },
    { title: '总记录数', key: 'totalRecs', sortable: true },
    { title: '错误记录数', key: 'totalErrors', sortable: true },
  ];

  const results = ref<EtlStatistic[]>([]);
  const loading = ref(false);
  const error = ref('');

  const sortedResults = computed(() => [...results.value]);
  const latestResult = computed(() => sortedResults.value[0]);
  const totalRecords = computed(() =>
    results.value.reduce((sum, item) => sum + Number(item.totalRecs || 0), 0)
  );
  const totalErrors = computed(() =>
    results.value.reduce((sum, item) => sum + Number(item.totalErrors || 0), 0)
  );
  const peakRecSpeed = computed(() =>
    results.value.reduce((max, item) => Math.max(max, Number(item.recSpeed || 0)), 0)
  );

  const formatNumber = (value?: number) =>
    new Intl.NumberFormat('zh-CN').format(Number(value || 0));

  const escapeCsvCell = (value: unknown) => {
    const text = String(value ?? '');
    return `"${text.split('"').join('""')}"`;
  };

  const exportCsv = () => {
    if (!results.value.length) return;
    const columns = headers.map(header => String(header.key));
    const titleRow = headers.map(header => escapeCsvCell(header.title));
    const rows = results.value.map(row =>
      columns.map(key => escapeCsvCell((row as Record<string, unknown>)[key])).join(',')
    );
    const csv = [titleRow.join(','), ...rows].join('\n');
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `addax-result-${tid || 'export'}.csv`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
  };

  onMounted(async () => {
    loading.value = true;
    error.value = '';
    try {
      results.value = await tableService.fetchAddaxResult(Number(tid));
    } catch (err) {
      error.value = (err as Error).message || String(err);
      notify(`加载采集结果失败: ${err}`, 'error');
    } finally {
      loading.value = false;
    }
  });
</script>

<style scoped>
  .table-panel__content {
    display: flex;
    flex-direction: column;
    gap: 16px;
    padding: 18px;
  }

  .panel-head {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 16px;
    flex-wrap: wrap;
  }

  .panel-title-row {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
  }

  .panel-title {
    font-size: 1rem;
    font-weight: 700;
    color: rgb(var(--v-theme-on-surface));
  }

  .panel-subtitle {
    margin-top: 6px;
    max-width: 680px;
    color: rgba(var(--v-theme-on-surface), 0.68);
    line-height: 1.6;
  }

  .panel-actions {
    display: inline-flex;
    align-items: center;
    gap: 8px;
  }

  .summary-grid {
    display: grid;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: 12px;
  }

  .summary-card {
    padding: 14px 16px;
    border-radius: 14px;
    border: 1px solid rgba(var(--v-theme-on-surface), 0.08);
    background:
      linear-gradient(
        180deg,
        rgba(var(--v-theme-primary), 0.06),
        rgba(var(--v-theme-primary), 0.01)
      ),
      rgb(var(--v-theme-surface));
  }

  .summary-label {
    display: block;
    margin-bottom: 6px;
    font-size: 0.76rem;
    letter-spacing: 0.04em;
    text-transform: uppercase;
    color: rgba(var(--v-theme-on-surface), 0.56);
  }

  .result-table-shell {
    border-radius: 18px;
    border: 1px solid rgba(var(--v-theme-on-surface), 0.08);
    overflow: hidden;
    background: rgb(var(--v-theme-surface));
  }

  .result-table-shell__header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    padding: 16px 18px;
    border-bottom: 1px solid rgba(var(--v-theme-on-surface), 0.08);
  }

  .result-table-shell__title {
    font-weight: 600;
    color: rgb(var(--v-theme-on-surface));
  }

  .result-table-shell__caption {
    margin-top: 2px;
    font-size: 0.82rem;
    color: rgba(var(--v-theme-on-surface), 0.62);
  }

  .result-table :deep(th) {
    white-space: nowrap;
  }

  .result-table :deep(.v-table__wrapper) {
    max-height: 62vh;
  }

  .cell-emphasis {
    font-weight: 600;
    color: rgb(var(--v-theme-on-surface));
  }

  .mono-cell {
    font-family:
      'SF Mono', 'Monaco', 'Inconsolata', 'Roboto Mono', 'Consolas', 'Courier New', monospace;
  }

  .panel-state {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 10px;
    min-height: 240px;
    padding: 24px;
    text-align: center;
    border-radius: 18px;
    border: 1px dashed rgba(var(--v-theme-on-surface), 0.16);
    background: rgba(var(--v-theme-on-surface), 0.02);
  }

  .panel-state--error {
    border-style: solid;
    background: rgba(var(--v-theme-error), 0.04);
  }

  .panel-state__icon {
    color: rgba(var(--v-theme-on-surface), 0.28);
  }

  .panel-state__title {
    font-size: 1rem;
    font-weight: 600;
    color: rgb(var(--v-theme-on-surface));
  }

  .panel-state__desc {
    color: rgba(var(--v-theme-on-surface), 0.64);
    line-height: 1.6;
  }

  @media (max-width: 1100px) {
    .summary-grid {
      grid-template-columns: repeat(2, minmax(0, 1fr));
    }
  }

  @media (max-width: 700px) {
    .table-panel__content {
      padding: 16px;
    }

    .summary-grid {
      grid-template-columns: 1fr;
    }

    .result-table-shell__header {
      flex-direction: column;
      align-items: flex-start;
    }
  }
</style>

<template>
  <v-card flat class="ds-card table-panel fields-compare-panel">
    <v-card-text class="table-panel__content">
      <div class="panel-head">
        <div>
          <div class="panel-title-row">
            <div class="panel-title">字段对比</div>
            <v-chip size="small" variant="tonal" color="primary">{{ fields.length }} 个字段</v-chip>
          </div>
        </div>
        <div class="panel-actions">
          <v-switch v-model="showOnlyDiff" hide-details inset color="primary" label="仅看差异" />
        </div>
      </div>

      <div class="summary-grid">
        <div class="summary-card">
          <span class="summary-label">字段总数</span>
          <strong>{{ fields.length }}</strong>
        </div>
        <div class="summary-card">
          <span class="summary-label">存在差异</span>
          <strong :class="{ 'text-error': diffCount > 0 }">{{ diffCount }}</strong>
        </div>
        <div class="summary-card">
          <span class="summary-label">完全一致</span>
          <strong>{{ sameCount }}</strong>
        </div>
      </div>

      <div v-if="loading" class="panel-state">
        <v-progress-circular indeterminate color="primary" size="42" width="4" />
        <div class="panel-state__title">正在加载字段对比</div>
      </div>

      <div v-else-if="error" class="panel-state panel-state--error">
        <v-icon size="44" color="error">mdi-alert-circle-outline</v-icon>
        <div class="panel-state__title">字段对比加载失败</div>
        <v-alert type="error" variant="tonal" class="mt-3">
          {{ error }}
        </v-alert>
      </div>

      <div v-else-if="displayFields.length" class="compare-table-shell ds-table-wrap">
        <v-data-table
          :headers="headers"
          :items="displayFields"
          density="comfortable"
          class="compare-table"
          no-data-text="无字段数据"
          :items-per-page="-1"
          hide-default-footer
        >
          <template #item.columnId="{ item }">
            <div class="order-cell">#{{ item.columnId }}</div>
          </template>

          <template #item.columnName="{ item }">
            <div class="field-cell">
              <div class="field-name">{{ item.columnName || '-' }}</div>
              <div class="field-comment">{{ item.colComment || '无字段备注' }}</div>
            </div>
          </template>

          <template #item.sourceDescriptor="{ item }">
            <div class="type-stack">
              <div class="type-pill type-pill--source">{{ item.sourceDescriptor }}</div>
              <div class="type-meta">{{ item.precisionDescriptor }}</div>
            </div>
          </template>

          <template #item.targetDescriptor="{ item }">
            <div class="type-stack">
              <div class="type-pill type-pill--target">{{ item.targetDescriptor }}</div>
            </div>
          </template>

          <template #item.diffLabel="{ item }">
            <v-chip
              size="small"
              :color="item.hasDiff ? 'warning' : 'success'"
              :variant="item.hasDiff ? 'tonal' : 'outlined'"
            >
              {{ item.diffLabel }}
            </v-chip>
          </template>
        </v-data-table>
      </div>

      <div v-else class="panel-state">
        <v-icon size="48" class="panel-state__icon">mdi-table-column-remove</v-icon>
        <div class="panel-state__title">暂无字段差异数据</div>
        <div class="panel-state__desc">请确认当前采集表已完成字段信息同步。</div>
      </div>
    </v-card-text>
  </v-card>
</template>

<script setup lang="ts">
  import { ref, onMounted, computed } from 'vue';
  import { useRoute } from 'vue-router';
  import { notify } from '@/stores/notifier';
  import tableService from '@/service/table-service';
  import type { EtlColumn } from '@/types/database';
  import type { DataTableHeader } from 'vuetify';

  const route = useRoute();
  const tid = Number(route.params.tid);

  const fields = ref<EtlColumn[]>([]);
  const loading = ref(false);
  const error = ref('');
  const showOnlyDiff = ref(false);

  const normalizeType = (value?: string) =>
    String(value || '')
      .replace(/\s+/g, '')
      .toLowerCase();

  const mappedFields = computed(() =>
    fields.value.map(item => {
      const sourceDescriptor = item.sourceType || '-';
      const targetDescriptor = item.targetTypeFull || item.targetType || '-';
      const precisionDescriptor =
        [
          item.dataLength != null ? `长度 ${item.dataLength}` : null,
          item.dataPrecision != null ? `精度 ${item.dataPrecision}` : null,
          item.dataScale != null ? `小数 ${item.dataScale}` : null,
        ]
          .filter(Boolean)
          .join(' / ') || '无长度与精度信息';

      const tableDescriptor = item.tblComment || '无表备注';
      const hasDiff =
        normalizeType(item.sourceType) !== normalizeType(item.targetTypeFull || item.targetType);

      return {
        ...item,
        sourceDescriptor,
        targetDescriptor,
        precisionDescriptor,
        tableDescriptor,
        hasDiff,
        diffLabel: hasDiff ? '类型有差异' : '类型一致',
      };
    })
  );

  const displayFields = computed(() =>
    showOnlyDiff.value ? mappedFields.value.filter(item => item.hasDiff) : mappedFields.value
  );

  const diffCount = computed(() => mappedFields.value.filter(item => item.hasDiff).length);
  const sameCount = computed(() => Math.max(mappedFields.value.length - diffCount.value, 0));

  const headers: DataTableHeader[] = [
    { title: '序号', key: 'columnId', width: '88px' },
    { title: '字段', key: 'columnName' },
    { title: '源字段类型', key: 'sourceDescriptor' },
    { title: '目标字段类型', key: 'targetDescriptor' },
    { title: '差异状态', key: 'diffLabel', width: '132px' },
  ];

  onMounted(async () => {
    loading.value = true;
    error.value = '';
    try {
      fields.value = await tableService.fetchFieldsCompare(tid);
    } catch (err) {
      error.value = (err as Error).message || String(err);
      notify(`加载字段对比失败: ${err}`, 'error');
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
    max-width: 760px;
    color: rgba(var(--v-theme-on-surface), 0.68);
    line-height: 1.6;
  }

  .summary-grid {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
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

  .compare-table-shell {
    border-radius: 18px;
    border: 1px solid rgba(var(--v-theme-on-surface), 0.08);
    overflow: hidden;
    background: rgb(var(--v-theme-surface));
  }

  .compare-table :deep(th) {
    white-space: nowrap;
  }

  .compare-table :deep(tr:has(.type-pill--source)) {
    transition: background-color 0.2s ease;
  }

  .compare-table :deep(tr:hover) {
    background: rgba(var(--v-theme-primary), 0.04);
  }

  .order-cell {
    font-weight: 700;
    color: rgba(var(--v-theme-on-surface), 0.58);
  }

  .field-cell {
    display: flex;
    flex-direction: column;
    gap: 4px;
  }

  .field-name {
    font-weight: 600;
    color: rgb(var(--v-theme-on-surface));
  }

  .field-comment {
    font-size: 0.82rem;
    color: rgba(var(--v-theme-on-surface), 0.62);
  }

  .type-stack {
    display: flex;
    flex-direction: column;
    gap: 6px;
  }

  .type-pill {
    display: inline-flex;
    width: fit-content;
    max-width: 100%;
    padding: 6px 10px;
    border-radius: 999px;
    font-family:
      'SF Mono', 'Monaco', 'Inconsolata', 'Roboto Mono', 'Consolas', 'Courier New', monospace;
    font-size: 0.8rem;
    line-height: 1.35;
    word-break: break-all;
  }

  .type-pill--source {
    background: rgba(var(--v-theme-primary), 0.1);
    color: rgb(var(--v-theme-primary));
  }

  .type-pill--target {
    background: rgba(var(--v-theme-secondary), 0.12);
    color: rgb(var(--v-theme-secondary));
  }

  .type-meta {
    font-size: 0.8rem;
    color: rgba(var(--v-theme-on-surface), 0.6);
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

  @media (max-width: 960px) {
    .table-panel__content {
      padding: 16px;
    }

    .summary-grid {
      grid-template-columns: 1fr;
    }
  }
</style>

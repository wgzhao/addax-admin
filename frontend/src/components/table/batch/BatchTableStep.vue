<template>
  <div class="step-layout">
    <div class="step-hero">
      <div class="step-kicker">Step 2</div>
      <h2 class="step-title">选择采集表</h2>
      <div class="step-desc">
        只展示当前采集路由下尚未纳入采集的表。已退役定义可以恢复，系统不会为同一路由重复创建。
      </div>
    </div>

    <v-alert v-if="retiredTables.length > 0" type="warning" variant="tonal" class="step-alert">
      <div class="retired-head">
        有 {{ retiredTables.length }} 张表已退役。恢复会复用原采集定义，请勿重复新增。
      </div>
      <div class="retired-list">
        <div
          v-for="item in retiredTables"
          :key="item.definitionId || item.name"
          class="retired-row"
        >
          <div class="retired-meta">
            <span class="table-name">{{ item.name }}</span>
            <span class="retired-comment">{{ item.comment || '无表注释' }}</span>
            <span v-if="item.retiredAt" class="retired-at">
              退役于 {{ item.retiredAt
              }}<template v-if="item.retiredBy"> · {{ item.retiredBy }}</template>
            </span>
          </div>
          <v-btn
            size="small"
            variant="tonal"
            color="warning"
            :loading="restoringId === item.definitionId"
            @click="$emit('restore', item)"
          >
            恢复
          </v-btn>
        </div>
      </div>
    </v-alert>

    <div v-if="loading" class="panel-state">
      <v-progress-circular indeterminate color="primary" size="44" width="4" />
      <div class="panel-state__title">正在连接数据库并加载表列表</div>
      <div class="panel-state__desc">请稍候，系统正在拉取该数据库下尚未纳入采集的表。</div>
    </div>

    <v-alert v-else-if="loadError" type="error" variant="tonal" class="step-alert">
      <div class="alert-action">
        <span>{{ loadError }}</span>
        <v-btn size="small" variant="text" color="error" @click="$emit('retry')">重试</v-btn>
      </div>
    </v-alert>

    <div v-else-if="tables.length > 0" class="table-stage">
      <div class="table-stage__toolbar">
        <div class="table-stage__toolbar-left">
          <v-text-field
            v-model="search"
            aria-label="搜索表名或注释"
            placeholder="搜索表名 / 注释…"
            prepend-inner-icon="mdi-magnify"
            single-line
            hide-details
            density="comfortable"
            variant="outlined"
            clearable
            class="search-field"
          />
        </div>
      </div>

      <v-data-table
        v-model="selected"
        v-model:items-per-page="pageSize"
        :items="tables"
        :headers="headers"
        :items-per-page-options="[15, 30, 50, 100]"
        density="comfortable"
        show-select
        :search="search"
        item-value="sourceTable"
        return-object
        class="step2-table"
      >
        <template #item.sourceTable="{ item }">
          <div class="table-name">{{ item.sourceTable }}</div>
        </template>

        <template #item.tblComment="{ item }">
          <div class="comment-cell" :title="item.tblComment || undefined">
            {{ item.tblComment || '无表注释' }}
          </div>
        </template>

        <template #item.approxRowCount="{ item }">
          <span class="mono-data">{{ formatRowCount(item.approxRowCount) }}</span>
        </template>
      </v-data-table>
    </div>

    <div v-else class="panel-state">
      <v-icon size="48" class="panel-state__icon">mdi-table-off</v-icon>
      <div class="panel-state__title">当前采集路由下没有可新增的表</div>
      <div class="panel-state__desc">
        可以返回上一步切换源库、目标端或目标库；如果表已退役，请在上方恢复原采集定义。
      </div>
      <v-btn variant="text" color="primary" prepend-icon="mdi-chevron-left" @click="$emit('back')">
        返回上一步
      </v-btn>
    </div>
  </div>
</template>

<script setup lang="ts">
  import { ref } from 'vue';
  import type { DataTableHeader } from 'vuetify';
  import type { CollectableTable, EtlTableView } from '@/types/database';

  defineProps<{
    tables: EtlTableView[];
    retiredTables: CollectableTable[];
    restoringId: number | null;
    loading: boolean;
    loadError: string;
  }>();

  defineEmits(['retry', 'back', 'restore']);

  const selected = defineModel<EtlTableView[]>('selected', { required: true });
  const search = defineModel<string>('search');
  const pageSize = ref(15);
  const rowCountFormatter = new Intl.NumberFormat('zh-CN');

  const formatRowCount = (value?: number | null) =>
    value == null ? '-' : rowCountFormatter.format(value);

  const headers: DataTableHeader[] = [
    { title: '源表名', key: 'sourceTable', width: '48%' },
    { title: '表注释', key: 'tblComment' },
    { title: '近似行数', key: 'approxRowCount', align: 'end', width: 140 },
  ];
</script>

<style lang="scss" scoped>
  @use './batch-step-shared' as *;

  .table-stage__toolbar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    padding: 18px;
    border-bottom: 1px solid rgba(var(--v-theme-on-surface), 0.08);
  }

  .table-stage__toolbar-left {
    display: flex;
    align-items: center;
    gap: 12px;
    flex: 1;
    min-width: 0;
  }

  .search-field {
    max-width: 360px;
  }

  .step2-table :deep(.v-table__wrapper) {
    max-height: 62vh;
    overflow: auto;
  }

  .step2-table :deep(thead th) {
    position: sticky;
    top: 0;
    z-index: 1;
    background: rgb(var(--v-theme-surface));
  }

  .table-name {
    font-weight: 600;
    color: rgb(var(--v-theme-on-surface));
  }

  .comment-cell {
    color: rgba(var(--v-theme-on-surface), 0.62);
  }

  .comment-cell {
    max-width: 280px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .mono-data {
    font-family: 'SF Mono', 'Monaco', 'Inconsolata', 'Roboto Mono', 'Consolas', 'Courier New',
      monospace;
    font-variant-numeric: tabular-nums;
  }

  .retired-head {
    margin-bottom: 10px;
    font-weight: 600;
  }

  .retired-list {
    display: flex;
    flex-direction: column;
    gap: 8px;
  }

  .retired-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16px;
    padding: 8px 10px;
    border: 1px solid rgba(var(--v-theme-warning), 0.22);
    border-radius: 10px;
    background: rgba(var(--v-theme-warning), 0.05);
  }

  .retired-meta {
    display: flex;
    flex-wrap: wrap;
    align-items: baseline;
    gap: 8px 14px;
    min-width: 0;
  }

  .retired-comment,
  .retired-at {
    color: rgba(var(--v-theme-on-surface), 0.62);
    font-size: 12px;
  }

  @media (max-width: 760px) {
    .table-stage__toolbar,
    .table-stage__toolbar-left {
      flex-direction: column;
      align-items: stretch;
    }

    .search-field {
      max-width: none;
    }
  }
</style>

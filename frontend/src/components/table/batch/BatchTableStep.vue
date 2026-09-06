<template>
  <div class="step-layout">
    <div class="step-hero">
      <div class="step-kicker">Step 2</div>
      <div class="step-title">筛选并选择待采集表</div>
    </div>

    <div v-if="loading" class="panel-state">
      <v-progress-circular indeterminate color="primary" size="44" width="4" />
      <div class="panel-state__title">正在连接数据库并加载表列表</div>
      <div class="panel-state__desc">请稍候，系统正在拉取该数据库下尚未纳入采集的表。</div>
    </div>

    <v-alert v-else-if="loadError" type="error" variant="tonal" class="step-alert">
      {{ loadError }}
    </v-alert>

    <div v-else-if="tables.length > 0" class="table-stage">
      <div class="table-stage__toolbar">
        <div class="table-stage__toolbar-left">
          <v-text-field
            v-model="search"
            placeholder="搜索表名 / 注释"
            prepend-inner-icon="mdi-magnify"
            single-line
            hide-details
            density="comfortable"
            variant="outlined"
            clearable
            class="search-field"
          />
        </div>
        <div class="table-stage__toolbar-right">
          <v-chip color="primary" variant="tonal">已选 {{ selectedCnt }}</v-chip>
        </div>
      </div>

      <v-data-table
        v-model="selected"
        :items="tables"
        :headers="headers"
        :items-per-page="15"
        density="comfortable"
        show-select
        :search="search"
        item-value="sourceTable"
        return-object
        class="step2-table"
      >
        <template #item.sourceTable="{ item }">
          <div class="table-name-cell">
            <div class="table-name">{{ item.sourceTable }}</div>
            <div class="table-meta">{{ item.sourceDb }}</div>
          </div>
        </template>

        <template #item.tblComment="{ item }">
          <div class="comment-cell">{{ item.tblComment || '无表注释' }}</div>
        </template>

        <template #item.approxRowCount="{ item }">
          <span class="mono-data">{{ item.approxRowCount ?? '-' }}</span>
        </template>
      </v-data-table>
    </div>

    <div v-else class="panel-state">
      <v-icon size="48" class="panel-state__icon">mdi-table-off</v-icon>
      <div class="panel-state__title">该数据库下未找到待采集的表</div>
      <div class="panel-state__desc">
        可以返回上一步重新选择数据源，或确认当前数据库是否已经全部纳入采集。
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
  import type { DataTableHeader } from 'vuetify';
  import type { EtlTableView } from '@/types/database';

  defineProps<{
    tables: EtlTableView[];
    loading: boolean;
    loadError: string;
    selectedCnt: number;
  }>();

  const selected = defineModel<EtlTableView[]>('selected', { required: true });
  const search = defineModel<string>('search');

  const headers: DataTableHeader[] = [
    { title: '源系统', key: 'sid' },
    { title: '源筛选', key: 'filter' },
    { title: '源用户', key: 'sourceDb' },
    { title: '源表名', key: 'sourceTable' },
    { title: '表注释', key: 'tblComment' },
    { title: '近似行数', key: 'approxRowCount' },
    { title: '目标库', key: 'targetDb' },
    { title: '目标表', key: 'targetTable' },
  ];
</script>

<style lang="scss" scoped>
  @import './_batch-step-shared.scss';

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

  .table-stage__toolbar-right {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
  }

  .toolbar-tip {
    font-size: 0.82rem;
    color: rgba(var(--v-theme-on-surface), 0.58);
    white-space: nowrap;
  }

  .search-field {
    max-width: 360px;
  }

  .step2-table :deep(.v-table__wrapper) {
    max-height: 62vh;
  }

  .table-name-cell {
    display: flex;
    flex-direction: column;
    gap: 3px;
  }

  .table-name {
    font-weight: 600;
    color: rgb(var(--v-theme-on-surface));
  }

  .table-meta,
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
  }

  @media (max-width: 760px) {
    .table-stage__toolbar,
    .table-stage__toolbar-left {
      flex-direction: column;
      align-items: stretch;
    }

    .toolbar-tip {
      white-space: normal;
    }

    .search-field {
      max-width: none;
    }
  }
</style>

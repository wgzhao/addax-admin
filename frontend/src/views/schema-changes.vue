<template>
  <div class="page-shell schema-changes-page">
    <PageHeader
      title="表结构变更"
      subtitle="采集表字段的新增、删除与类型变化；用于评估下游影响。"
    />

    <v-card flat class="ds-card filter-card">
      <v-card-text class="ds-card__content filter-body">
        <v-text-field
          v-model="filters.keyword"
          label="库名 / 表名 / 字段名"
          density="compact"
          variant="outlined"
          hide-details
          clearable
          class="filter-keyword"
          @keyup.enter="search"
        />
        <v-select
          v-model="filters.changeType"
          :items="CHANGE_TYPE_OPTIONS"
          item-title="title"
          item-value="value"
          label="变更类型"
          density="compact"
          variant="outlined"
          hide-details
          clearable
          class="filter-select"
        />
        <v-text-field
          v-model="filters.from"
          label="开始日期"
          type="date"
          density="compact"
          variant="outlined"
          hide-details
          class="filter-date"
        />
        <v-text-field
          v-model="filters.to"
          label="结束日期"
          type="date"
          density="compact"
          variant="outlined"
          hide-details
          class="filter-date"
        />
        <v-btn color="primary" variant="flat" prepend-icon="mdi-magnify" @click="search">
          查询
        </v-btn>
        <v-btn variant="text" prepend-icon="mdi-refresh" @click="resetFilters">重置</v-btn>
      </v-card-text>
    </v-card>

    <AppDataTable
      v-model:page="page"
      v-model:items-per-page="pageSize"
      mode="server"
      :sort-by="sortBy"
      :headers="headers"
      :items="items"
      :total="total"
      :loading="loading"
      :error="error"
      :items-per-page-options="[20, 50, 100]"
      empty-title="暂无表结构变更"
      empty-description="源表字段发生新增、删除或类型变化后，会在这里留下记录。"
      @update:error="error = ''"
      @update:options="handleOptions"
    >
      <template #item.changeAt="{ item }">
        <span class="text-no-wrap">{{ item.changeAt || '—' }}</span>
      </template>
      <template #item.tid="{ item }">
        <router-link :to="`/table/detail/${item.tid}`" class="table-link"
          >#{{ item.tid }}</router-link
        >
      </template>
      <template #item.changeType="{ item }">
        <v-chip size="small" :color="changeTypeColor(item.changeType)" variant="tonal">
          {{ changeTypeLabel(item.changeType) }}
        </v-chip>
      </template>
      <template #item.change_summary="{ item }">
        <span class="change-summary" :title="changeSummary(item)">{{ changeSummary(item) }}</span>
      </template>
    </AppDataTable>
  </div>
</template>

<script setup lang="ts">
  import PageHeader from '@/components/PageHeader.vue';
  import AppDataTable from '@/components/common/AppDataTable.vue';
  import { ref } from 'vue';
  import type { DataTableHeader } from 'vuetify';
  import { SortItem } from 'vuetify/lib/components/VDataTable/composables/sort.mjs';
  import { schemaChangeService, type SchemaChangeRow } from '@/service/schema-change-service';

  const CHANGE_TYPE_OPTIONS = [
    { value: 'ADD', title: '新增字段' },
    { value: 'DELETE', title: '删除字段' },
    { value: 'TYPE_CHANGE', title: '类型变更' },
  ];

  const headers: DataTableHeader[] = [
    { title: '变更时间', key: 'changeAt', width: 170 },
    { title: '采集表', key: 'tid', width: 100 },
    { title: '库名', key: 'sourceDb' },
    { title: '表名', key: 'sourceTable' },
    { title: '字段名', key: 'columnName' },
    { title: '变更类型', key: 'changeType', width: 120 },
    { title: '变更说明', key: 'change_summary', sortable: false },
  ];

  const filters = ref({
    keyword: '',
    changeType: null as string | null,
    from: '',
    to: '',
  });

  const page = ref(1);
  const pageSize = ref(20);
  const sortBy = ref<SortItem[]>([{ key: 'changeAt', order: 'desc' }]);
  const items = ref<SchemaChangeRow[]>([]);
  const total = ref(0);
  const loading = ref(false);
  const error = ref('');

  const changeTypeLabel = (type?: string) =>
    CHANGE_TYPE_OPTIONS.find(option => option.value === type)?.title || type || '—';

  const changeTypeColor = (type?: string) => {
    if (type === 'ADD') return 'success';
    if (type === 'DELETE') return 'error';
    if (type === 'TYPE_CHANGE') return 'warning';
    return 'secondary';
  };

  // The backend stores one row per column change; this folds the dimension
  // fields into a single readable sentence for the table.
  const changeSummary = (item: SchemaChangeRow) => {
    const parts: string[] = [];
    if (item.oldSourceType || item.newSourceType) {
      parts.push(`类型: ${item.oldSourceType ?? '—'} → ${item.newSourceType ?? '—'}`);
    }
    if (item.oldDataLength != null || item.newDataLength != null) {
      parts.push(`长度: ${item.oldDataLength ?? '—'} → ${item.newDataLength ?? '—'}`);
    }
    if (item.oldDataPrecision != null || item.newDataPrecision != null) {
      parts.push(`精度: ${item.oldDataPrecision ?? '—'} → ${item.newDataPrecision ?? '—'}`);
    }
    if (item.oldDataScale != null || item.newDataScale != null) {
      parts.push(`小数位: ${item.oldDataScale ?? '—'} → ${item.newDataScale ?? '—'}`);
    }
    return parts.join('； ') || '—';
  };

  const load = async () => {
    loading.value = true;
    error.value = '';
    try {
      const sort = sortBy.value[0];
      const result = await schemaChangeService.list({
        page: page.value - 1,
        pageSize: pageSize.value,
        changeType: filters.value.changeType || undefined,
        keyword: filters.value.keyword || undefined,
        from: filters.value.from || undefined,
        to: filters.value.to || undefined,
        sortField: sort?.key,
        sortOrder: typeof sort?.order === 'string' ? sort.order : undefined,
      });
      items.value = result.content ?? [];
      total.value = result.totalElements ?? 0;
    } catch (e) {
      items.value = [];
      total.value = 0;
      error.value = `加载表结构变更失败：${(e as Error)?.message || String(e)}`;
    } finally {
      loading.value = false;
    }
  };

  function handleOptions(options: { page: number; itemsPerPage: number; sortBy: SortItem[] }) {
    // The table's page model can update before update:options arrives, so a
    // "did anything change" guard would swallow the click that triggered it.
    page.value = options.page;
    pageSize.value = options.itemsPerPage;
    sortBy.value = options.sortBy || [];
    void load();
  }

  function search() {
    page.value = 1;
    void load();
  }

  function resetFilters() {
    filters.value = { keyword: '', changeType: null, from: '', to: '' };
    page.value = 1;
    void load();
  }
</script>

<route lang="json">
{
  "meta": {
    "title": "表结构变更",
    "icon": "mdi-table-edit",
    "requiresAuth": true,
    "navGroup": "data",
    "navOrder": 30
  }
}
</route>

<style scoped>
  .filter-card {
    margin-bottom: 12px;
  }

  .filter-body {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 12px;
  }

  .filter-keyword {
    width: 240px;
  }

  .filter-select {
    width: 170px;
  }

  .filter-date {
    width: 160px;
  }

  .table-link {
    color: rgb(var(--v-theme-primary));
    text-decoration: none;
    font-variant-numeric: tabular-nums;
  }

  .table-link:hover {
    text-decoration: underline;
  }

  .change-summary {
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
    word-break: break-word;
  }

  @media (max-width: 960px) {
    .filter-keyword,
    .filter-select,
    .filter-date {
      width: 100%;
    }
  }
</style>

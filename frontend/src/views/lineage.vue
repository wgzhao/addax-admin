<template>
  <div class="lineage-page page-shell">
    <PageHeader title="数据血缘" subtitle="按字段名搜索，查看字段从哪个源表来、落到哪个 ODS 表。" />

    <v-card flat class="ds-card search-card">
      <v-card-text class="ds-card__content">
        <v-text-field
          v-model="keyword"
          label="字段名（源字段或目标字段）"
          prepend-inner-icon="mdi-magnify"
          density="comfortable"
          variant="outlined"
          hide-details
          clearable
          @keyup.enter="search"
          @click:clear="clearSearch"
        >
          <template #append>
            <v-btn
              color="primary"
              variant="flat"
              :loading="loading"
              :disabled="!keyword.trim()"
              @click="search"
            >
              搜索
            </v-btn>
          </template>
        </v-text-field>
      </v-card-text>
    </v-card>

    <v-alert v-if="error" type="error" variant="tonal" class="mt-3">{{ error }}</v-alert>

    <v-card flat class="ds-card lineage-card">
      <v-card-text class="ds-card__content">
        <div v-if="searched && !loading" class="lineage-summary">
          共 {{ items.length }} 条字段血缘{{ items.length >= 200 ? '（已达上限，请缩小范围）' : '' }}
        </div>

        <v-data-table
          :headers="headers"
          :items="items"
          :loading="loading"
          density="comfortable"
          no-data-text="输入字段名开始搜索"
          class="lineage-table"
        >
          <template #item.column="{ item }">
            <div class="column-path">
              <code class="column-name">{{ item.sourceColumn }}</code>
              <v-icon size="16" class="arrow-icon">mdi-arrow-right</v-icon>
              <code class="column-name">{{ item.targetColumn }}</code>
            </div>
          </template>
          <template #item.source="{ item }">
            <div class="table-ref">
              <span class="table-ref__label">{{ item.sourceName }}</span>
              <code class="table-ref__name">{{ item.sourceDb }}.{{ item.sourceTable }}</code>
            </div>
          </template>
          <template #item.target="{ item }">
            <div class="table-ref">
              <span class="table-ref__label">{{ item.targetName }}</span>
              <code class="table-ref__name">{{ item.targetDb }}.{{ item.targetTable }}</code>
            </div>
          </template>
          <template #item.actions="{ item }">
            <v-btn
              size="small"
              variant="text"
              color="primary"
              :to="`/table/detail/${item.tid}?tab=fields`"
            >
              查看
            </v-btn>
          </template>
        </v-data-table>
      </v-card-text>
    </v-card>
  </div>
</template>

<script setup lang="ts">
  import { ref } from 'vue';
  import PageHeader from '@/components/PageHeader.vue';
  import lineageService, { type ColumnLineage } from '@/service/lineage-service';

  const keyword = ref('');
  const items = ref<ColumnLineage[]>([]);
  const loading = ref(false);
  const error = ref('');
  const searched = ref(false);

  const headers = [
    { title: '字段', key: 'column', sortable: false },
    { title: '源表', key: 'source', sortable: false },
    { title: '目标表', key: 'target', sortable: false },
    { title: '', key: 'actions', width: 80, sortable: false },
  ];

  async function search() {
    const q = keyword.value.trim();
    if (!q) return;
    loading.value = true;
    error.value = '';
    searched.value = true;
    try {
      items.value = await lineageService.searchColumns(q);
    } catch (e) {
      error.value = `搜索失败：${(e as Error)?.message || String(e)}`;
      items.value = [];
    } finally {
      loading.value = false;
    }
  }

  function clearSearch() {
    keyword.value = '';
    items.value = [];
    searched.value = false;
  }
</script>

<route lang="json">
{
  "meta": {
    "title": "数据血缘",
    "icon": "mdi-source-branch",
    "requiresAuth": true,
    "navGroup": "data",
    "navOrder": 20
  }
}
</route>

<style scoped>
  .lineage-page {
    min-width: 0;
  }

  .lineage-card {
    margin-top: 12px;
  }

  .lineage-summary {
    margin-bottom: 12px;
    color: rgba(var(--v-theme-on-surface), 0.6);
    font-size: 0.86rem;
  }

  .column-path {
    display: flex;
    align-items: center;
    gap: 4px;
  }

  .column-name {
    font-family: 'SF Mono', Monaco, Consolas, monospace;
    font-size: 0.86rem;
  }

  .arrow-icon {
    color: rgba(var(--v-theme-on-surface), 0.4);
  }

  .table-ref {
    display: flex;
    flex-direction: column;
    gap: 2px;
  }

  .table-ref__label {
    color: rgba(var(--v-theme-on-surface), 0.6);
    font-size: 0.8rem;
  }

  .table-ref__name {
    font-family: 'SF Mono', Monaco, Consolas, monospace;
    font-size: 0.86rem;
  }
</style>

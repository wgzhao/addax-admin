<template>
  <DashboardChartCard
    title="失败任务排行"
    subtitle="近 14 天"
    :height="320"
    :state="state"
    :error="error"
  >
    <template #actions>
      <v-btn
        size="x-small"
        variant="text"
        color="secondary"
        prepend-icon="mdi-download"
        :disabled="!rows.length"
        @click="exportCsv"
      >
        导出
      </v-btn>
    </template>
    <div class="fail-top-scroll">
      <v-data-table
        :items="rows"
        :headers="headers"
        density="compact"
        :items-per-page="10"
        item-value="tid"
        hide-default-footer
        class="fail-top-table fail-top-table--clickable"
        @click:row="openTable"
      >
        <template #item.table="{ item }">
          <div class="cell-table">
            <span class="cell-primary">{{ item.source_db }}.{{ item.source_table }}</span>
            <span class="cell-secondary">{{ item.target_db }}.{{ item.target_table }}</span>
          </div>
        </template>
        <template #item.fail_cnt="{ item }">
          <div class="cell-fail">
            <v-chip size="x-small" color="error" variant="tonal">{{ item.fail_cnt }}</v-chip>
            <span class="cell-kind">{{ kindLabel(item.last_kind) }}</span>
          </div>
        </template>
        <template #item.last_fail_at="{ item }">
          <span class="cell-time" :title="item.last_fail_at ?? ''">{{
            shortTime(item.last_fail_at)
          }}</span>
        </template>
        <template #item.last_error="{ item }">
          <v-tooltip :text="item.last_error || '-'" location="top" max-width="480">
            <template #activator="{ props: tooltipProps }">
              <span v-bind="tooltipProps" class="cell-error">{{ item.last_error || '-' }}</span>
            </template>
          </v-tooltip>
        </template>
      </v-data-table>
    </div>
  </DashboardChartCard>
</template>

<script setup lang="ts">
  import { computed } from 'vue';
  import type { DataTableHeader } from 'vuetify';
  import DashboardChartCard from './charts/DashboardChartCard.vue';
  import { useChartData } from '@/composables/useChartData';
  import { dashboardService } from '@/service/dashboard-service';
  import { buildCsv, downloadCsv } from '@/utils/csv';
  import { JOUR_KIND_META } from '@/utils/chart-colors';
  import { useRouter } from 'vue-router';

  const router = useRouter();

  // Why: the ranking is the entry point for triage — jump straight to the
  // filtered table list instead of making the operator copy the name across
  const openTable = (_event: Event, payload: { item?: Record<string, any> }) => {
    const sourceTable = String(payload?.item?.source_table || '').trim();
    if (!sourceTable) return;
    router.push({ path: '/table', query: { q: sourceTable } });
  };

  const { raw, state, error } = useChartData(() => dashboardService.fetchFailTopTables());
  const rows = computed(() => raw.value ?? []);

  // Why: this card shares the row with a 7-column chart, so six columns made
  // headers wrap vertically at 1440px; keep four and fold the kind into the
  // failure count cell.
  const headers: DataTableHeader[] = [
    { title: '源库.表', key: 'table', sortable: false },
    { title: '失败次数', key: 'fail_cnt', align: 'center', width: 84 },
    { title: '最近失败', key: 'last_fail_at', width: 92 },
    { title: '错误摘要', key: 'last_error', sortable: false, width: 140 },
  ];

  // The CSV keeps the fields that no longer have their own column.
  const csvHeaders: DataTableHeader[] = [
    { title: '源库.表', key: 'table' },
    { title: '目标库.表', key: 'targetTable' },
    { title: '失败次数', key: 'fail_cnt' },
    { title: '重试余量', key: 'retry_cnt' },
    { title: '最近失败', key: 'last_fail_at' },
    { title: '类型', key: 'last_kind' },
    { title: '错误摘要', key: 'last_error' },
  ];

  const kindLabel = (kind: string | null) => (kind ? (JOUR_KIND_META[kind]?.label ?? kind) : '-');

  const shortTime = (value?: string | null) => (value ? value.slice(5, 16) : '-');

  const exportCsv = () => {
    const exportRows = rows.value.map(row => ({
      ...row,
      table: `${row.source_db}.${row.source_table}`,
      targetTable: `${row.target_db}.${row.target_table}`,
      last_kind: kindLabel(row.last_kind),
    }));
    downloadCsv('fail-top-tables', buildCsv(csvHeaders, exportRows));
  };
</script>

<style scoped>
  .fail-top-scroll {
    height: 100%;
    overflow: auto;
  }

  .fail-top-table {
    font-size: 12px;
  }

  .fail-top-table :deep(th) {
    white-space: nowrap;
  }

  /* Why: fixed layout caps the error column at the card edge instead of
     letting the table overflow and clip the summary */
  .fail-top-table :deep(table) {
    table-layout: fixed;
    width: 100%;
  }

  .fail-top-table :deep(td),
  .fail-top-table :deep(th) {
    overflow: hidden;
  }

  .fail-top-table--clickable :deep(tbody tr) {
    cursor: pointer;
  }

  .cell-table {
    display: flex;
    flex-direction: column;
    line-height: 1.25;
  }

  .cell-primary {
    font-weight: 600;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .cell-secondary,
  .cell-time {
    color: rgb(var(--v-theme-on-surface-muted));
    font-size: 11px;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .cell-fail {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 2px;
  }

  .cell-kind {
    color: rgb(var(--v-theme-on-surface-muted));
    font-size: 10px;
    white-space: nowrap;
  }

  .cell-error {
    display: inline-block;
    max-width: 100%;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    vertical-align: bottom;
  }
</style>

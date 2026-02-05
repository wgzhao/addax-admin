<template>
  <v-card prepend-icon="mdi-database-search" title="采集结果" class="addax-result-card" density="comfortable">
    <v-card-text class="addax-result-body">
      <v-sheet class="form-section" rounded="lg" border>
        <div class="section-header">
          <v-icon size="18" color="primary">mdi-table</v-icon>
          <span>结果明细</span>
          <v-spacer />
          <div class="header-actions">
            <v-btn
              size="small"
              variant="tonal"
              color="primary"
              prepend-icon="mdi-download"
              :disabled="!results.length"
              @click="exportCsv"
            >
              导出
            </v-btn>
          </div>
        </div>
        <v-divider />
        <div class="section-body">
          <v-data-table-virtual v-if="tid" :items="results" :headers="headers" density="compact" class="elevation-1">
          </v-data-table-virtual>
        </div>
      </v-sheet>
    </v-card-text>
  </v-card>
  <!-- </dialog-comp> -->
</template>
<script setup lang="ts">
import { ref, onMounted } from "vue";
import type { DataTableHeader } from "vuetify";
import { notify } from "@/stores/notifier";
import tableService from "@/service/table-service";

const props = defineProps({ tid: String });

const headers: DataTableHeader[] = [
  { title: "业务日期", key: "bizDate", sortable: true },
  { title: "任务开始时间", key: "startAt", sortable: true },
  { title: "任务结束时间", key: "endAt", sortable: true },
  { title: "运行耗时", key: "takeSecs", sortable: true },
  { title: "字节/秒", key: "byteSpeed", sortable: true },
  { title: "行/秒", key: "recSpeed", sortable: true },
  { title: "总记录数", key: "totalRecs", sortable: true },
  { title: "错误记录数", key: "totalErrors", sortable: true }
];

const results = ref([]);

const exportCsv = () => {
  if (!results.value || results.value.length === 0) return;
  const columns = headers.map((h) => h.key);
  const titleRow = headers.map((h) => h.title);
  const rows = results.value.map((row: any) =>
    columns.map((key) => (row[key] ?? '')).join(',')
  );
  const csv = [titleRow.join(','), ...rows].join('\n');
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = `addax-result-${props.tid || 'export'}.csv`;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
};

onMounted(() => {
  tableService.fetchAddaxResult(Number(props.tid)).then(res => {
    results.value = res;
  }).catch(err => {
    notify(`加载采集结果失败: ${err}`, 'error');
  });
});
</script>
<style scoped>
.addax-result-card {
  background: rgb(var(--v-theme-surface));
}

.addax-result-body {
  background: transparent;
}

.form-section {
  background: rgb(var(--v-theme-surface-variant));
  border-color: rgba(var(--v-theme-on-surface), 0.08);
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}

.form-section:hover {
  border-color: rgba(var(--v-theme-primary), 0.2);
  box-shadow: 0 6px 18px rgba(15, 23, 42, 0.08);
}

.section-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  font-weight: 600;
  color: rgb(var(--v-theme-on-surface));
}

.header-actions {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.section-body {
  padding: 12px 14px 14px;
}
</style>

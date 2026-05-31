<template>
  <v-card density="comfortable">
    <v-card-text>
      <v-sheet rounded="lg" border>
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
        <div>
          <v-data-table-virtual v-if="tid" :items="results" :headers="headers" density="default" class="elevation-1">
          </v-data-table-virtual>
        </div>
      </v-sheet>
    </v-card-text>
  </v-card>
  <!-- </dialog-comp> -->
</template>
<script setup lang="ts">
import { ref, onMounted } from "vue";
import { useRoute } from 'vue-router'
import type { DataTableHeader } from "vuetify";
import { notify } from "@/stores/notifier";
import tableService from "@/service/table-service";

const route = useRoute()
const tid = String(route.params.tid)

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
  link.download = `addax-result-${tid || 'export'}.csv`;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
};

onMounted(() => {
  tableService.fetchAddaxResult(Number(tid)).then(res => {
    results.value = res;
  }).catch(err => {
    notify(`加载采集结果失败: ${err}`, 'error');
  });
});
</script>
<style scoped>
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

</style>

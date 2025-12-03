<template>
  <v-card prepend-icon="mdi-database-search" title="采集结果">
    <v-card-text>
  <v-data-table-virtual v-if="tid" :items="results" :headers="headers" density="compact" class="elevation-1">
  </v-data-table-virtual>
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
  { title: "日期", key: "runDate", sortable: true },
  { title: "任务开始时间", key: "startAt", sortable: true },
  { title: "任务结束时间", key: "endAt", sortable: true },
  { title: "运行耗时", key: "takeSecs", sortable: true },
  { title: "字节/秒", key: "byteSpeed", sortable: true },
  { title: "行/秒", key: "recSpeed", sortable: true },
  { title: "总记录数", key: "totalRecs", sortable: true },
  { title: "错误记录数", key: "totalErrors", sortable: true }
];

const results = ref([]);

onMounted(() => {
  tableService.fetchAddaxResult(Number(props.tid)).then(res => {
    results.value = res;
  }).catch(err => {
    notify(`加载采集结果失败: ${err}`, 'error');
  });
});
</script>
<style></style>

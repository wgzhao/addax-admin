<template>
  <!-- 字段对比 -->
  <v-data-table :headers="headers" :items="fields" hide-default-footer density="default" no-data-text="无数据"
    :items-per-page="-1">
    <template v-slot:item="{ item }">
      <tr>
        <td rowspan="3">{{ item.columnId }}</td>
      </tr>
      <tr class="source-row">
        <td>源表</td>
        <td>{{ item.columnName }}</td>
        <td>{{ item.sourceType }}</td>
        <td>{{ item.dataLength }}</td>
        <td>{{ item.dataPrecision }}</td>
        <td>{{ item.dataScale }}</td>
        <td>{{ item.colComment }}</td>
        <td>{{ item.tblComment }}</td>
      </tr>
      <tr class="target-row">
        <td>目标表</td>
        <td>{{ item.columnName }}</td>
        <td>{{ item.targetTypeFull }}</td>
        <td></td>
        <td></td>
        <td></td>
        <td>{{ item.colComment }}</td>
        <td>{{ item.tblComment }}</td>
      </tr>
    </template>
  </v-data-table>
  <!-- </dialog-comp> -->
</template>
<script setup lang="ts">
import { ref, onMounted } from "vue";
import { useRoute } from 'vue-router'
import { notify } from "@/stores/notifier";
import tableService from "@/service/table-service";
import type { EtlColumn } from "@/types/database";
import type { DataTableHeader } from "vuetify";

const route = useRoute()
const tid = Number(route.params.tid)

const fields = ref<EtlColumn[]>();

const headers: DataTableHeader[] = [
  { title: "序号", key: "columnId" },
  { title: "表来源", key: "tid" },
  { title: "字段名", key: "columnName" },
  { title: "字段类型", key: "dataType" },
  { title: "字段长度", key: "dataLength" },
  { title: "数值长度", key: "dataPrecision" },
  { title: "数值精度", key: "dataScale" },
  { title: "字段备注", key: "columnComment" }
];

onMounted(() => {
  tableService.fetchFieldsCompare(tid)
    .then(res => {
      fields.value = res;
    })
    .catch(err => {
      console.log(err);
      notify(`加载字段对比失败: ${err}`, 'error');
    });
});
</script>
<style scoped>

/* 使用主题色的轻微底色区分源/目标行，保持对比度温和 */
.source-row {
  background-color: rgba(var(--v-theme-primary), 0.4);
}
.target-row {
  background-color: rgba(var(--v-theme-secondary), 0.06);
}

/* 深色模式下略微增强可见度 */
.v-theme--dark .source-row {
  background-color: rgba(var(--v-theme-primary), 0.3);
}
.v-theme--dark .target-row {
  background-color: rgba(var(--v-theme-secondary), 0.12);
}
</style>

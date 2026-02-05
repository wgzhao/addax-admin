<template>
  <!-- 字段对比 -->
  <!-- <dialog-comp title="字段对比" v-model="dialog"> -->
  <v-card prepend-icon="mdi-table-column" title="字段对比" class="fields-compare-card" density="comfortable">
    <v-card-text class="fields-compare-body">
  <v-data-table :headers="headers" :items="fields" hide-default-footer density="compact" no-data-text="无数据"
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
  </v-card-text>
</v-card>
  <!-- </dialog-comp> -->
</template>
<script setup lang="ts">
import { ref, onMounted } from "vue";
import { notify } from "@/stores/notifier";
import tableService from "@/service/table-service";
import type { EtlColumn } from "@/types/database";
import type { DataTableHeader } from "vuetify";
// import DialogComp from "./DialogComp.vue";

const props = defineProps({ tid: String });


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
  tableService.fetchFieldsCompare(Number(props.tid))
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
.fields-compare-card {
  background: rgb(var(--v-theme-surface));
}

.fields-compare-body {
  background: transparent;
}

/* Better row grouping visual */
.fields-compare-body :deep(table) {
  border-collapse: separate;
  border-spacing: 0 8px;
}

.fields-compare-body :deep(tbody tr) {
  box-shadow: 0 1px 0 rgba(var(--v-theme-on-surface), 0.06);
}

.fields-compare-body :deep(tbody tr td:first-child) {
  border-top-left-radius: 8px;
  border-bottom-left-radius: 8px;
}

.fields-compare-body :deep(tbody tr td:last-child) {
  border-top-right-radius: 8px;
  border-bottom-right-radius: 8px;
}

/* 使用主题色的轻微底色区分源/目标行，保持对比度温和 */
.source-row {
  background-color: rgba(var(--v-theme-primary), 0.06);
}
.target-row {
  background-color: rgba(var(--v-theme-secondary), 0.06);
}

/* 保持行间距和可读性 */
.source-row td,
.target-row td {
  padding-top: 6px;
  padding-bottom: 6px;
}

/* 深色模式下略微增强可见度 */
.v-theme--dark .source-row {
  background-color: rgba(var(--v-theme-primary), 0.12);
}
.v-theme--dark .target-row {
  background-color: rgba(var(--v-theme-secondary), 0.12);
}
</style>

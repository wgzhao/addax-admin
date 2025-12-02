<template>
  <!-- 字段对比 -->
  <!-- <dialog-comp title="字段对比" v-model="dialog"> -->
  <v-data-table :headers="headers" :items="fields" hide-default-footer density="compact" no-data-text="无数据"
    :items-per-page="-1">
    <template v-slot:item="{ item }">
      <tr>
        <td rowspan="3">{{ item.columnId }}</td>
      </tr>
      <tr class="bg-gray">
        <td>源表</td>
        <td>{{ item.columnName }}</td>
        <td>{{ item.sourceType }}</td>
        <td>{{ item.dataLength }}</td>
        <td>{{ item.dataPrecision }}</td>
        <td>{{ item.dataScale }}</td>
        <td>{{ item.colComment }}</td>
        <td>{{ item.tblComment }}</td>
      </tr>
      <tr class="bg-gray-500">
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
<style></style>

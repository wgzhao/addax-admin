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

const headers1 = ref([
  {
    title: "序号",
    value: "idx"
  },
  {
    title: "目标表",
    align: "center",
    children: [
      { title: "字段名", value: "columnName" },
      { title: "字段类型", value: "colTypeFull" },
      { title: "字段长度", value: "data_length" },
      { title: "数值长度", value: "data_precision" },
      { title: "数值精度", value: "data_scale" },
      { title: "字段备注", value: "colComment" },
      { title: "表备注", value: "tblComment" }
    ]
  },
  {
    title: "源表",
    align: "center",
    children: [
      { title: "字段名", value: "columnName" },
      { title: "字段类型", value: "data_type" },
      { title: "字段长度", value: "data_length" },
      { title: "数值长度", value: "data_precision" },
      { title: "数值精度", value: "data_scale" },
      { title: "字段备注", value: "colComment" },
      { title: "表备注", value: "tblComment" }
    ]
  }
  // {
  //     title: "映射信息",
  //     align: "center",
  //     children: [
  //         {title: "映射类型", value: "DEST_TYPE"},
  //         {title: "完整类型", value: "DEST_TYPE_FULL"},
  //     ]

  // }
]);
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

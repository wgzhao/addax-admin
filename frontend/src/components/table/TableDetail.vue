<template>
  <!-- ODS 主表信息 ODS采集主表配置-->
  <!-- <v-sheet class="mx-auto"> -->
  <v-form fast-fail @submit.prevent="saveOds" title="采集主表配置">
    <v-container>
      <v-row>
        <v-col cols="12" md="3" class="m-0">
          <v-text-field v-model="table.name" label="源系统"></v-text-field>
        </v-col>
        <v-col cols="12" md="3">
          <v-text-field v-model="table.filter" label="过滤规则"></v-text-field>
        </v-col>
        <v-col cols="12" md="3">
          <v-select v-model="table.status" :items="statusOptions" item-title="label" item-value="value"
            label="状态"></v-select>
        </v-col>
        <v-col cols="12" md="3">
          <v-text-field v-model="table.retryCnt" label="剩余次数"></v-text-field>
        </v-col>
      </v-row>

      <v-row>
        <v-col cols="12" md="3">
          <v-text-field v-model="table.sourceDb" label="源库"></v-text-field>
        </v-col>
        <v-col cols="12" md="3">
          <v-text-field v-model="table.sourceTable" label="源表"></v-text-field>
        </v-col>
        <v-col cols="12" md="3">
          <v-text-field v-model="table.targetDb" label="目标库"></v-text-field>
        </v-col>
        <v-col cols="12" md="3">
          <v-text-field v-model="table.targetTable" label="目标表"></v-text-field>
        </v-col>
      </v-row>

      <v-row>
        <v-col cols="12" md="3">
          <v-text-field v-model="table.partKind" label="分区类型"></v-text-field>
        </v-col>
        <v-col cols="12" md="3">
          <v-text-field v-model="table.partName" label="分区字段"></v-text-field>
        </v-col>
        <v-col cols="12" md="3">
          <v-text-field v-model="table.partFormat" label="分区格式"></v-text-field>
        </v-col>
        <v-col cols="12" md="3">
          <v-select v-model="table.kind" :items="collectionModeOptions" item-title="label" item-value="value"
            label="采集模式"></v-select>
        </v-col>
      </v-row>

      <v-row>
        <v-col cols="12" md="6">
          <v-text-field v-model="table.storageFormat" label="存储格式"></v-text-field>
        </v-col>
        <v-col cols="12" md="6">
          <v-text-field v-model="table.compressFormat" label="压缩格式"></v-text-field>
        </v-col>
      </v-row>

      <v-row>
        <v-col cols="12" md="3">
          <v-text-field v-model="table.startTime" label="开始时间"></v-text-field>
        </v-col>
        <v-col cols="12" md="3">
          <v-text-field v-model="table.endTime" label="结束时间"></v-text-field>
        </v-col>
        <v-col cols="12" md="3">
          <v-text-field v-model="table.duration" label="运行耗时"></v-text-field>
        </v-col>
        <v-col cols="12" md="3">
          <v-text-field v-model="table.remark" label="备注"></v-text-field>
        </v-col>
      </v-row>
    </v-container>
    <v-card-actions>
      <v-spacer></v-spacer>
      <v-btn type="reset" class="bg-primary">取消</v-btn>
      <v-btn type="submit" class="btn btn-primary bg-primary">
        保存</v-btn>
    </v-card-actions>
  </v-form>
  <!-- </v-sheet> -->
  <!-- </dialog-comp> -->
</template>
<script setup lang="ts">
import { ref, watch } from "vue";
import { notify } from '@/stores/notifier';
import tableService from "@/service/table-service";
import { VEtlWithSource, EtlTable } from "@/types/database";
import { TABLE_STATUS_OPTIONS, COLLECTION_MODE_OPTIONS } from "@/utils";

const props = defineProps({
  table: {
    type: Object as () => VEtlWithSource,
    required: true,
  }
});

// 使用公共的状态选项
const statusOptions = TABLE_STATUS_OPTIONS;
// 使用公共的采集模式选项
const collectionModeOptions = COLLECTION_MODE_OPTIONS;

// 创建本地的响应式副本用于编辑
const table = ref<VEtlWithSource>({ ...props.table });

// 监听props变化，同步更新本地副本
watch(() => props.table, (newTable) => {
  table.value = { ...newTable };
}, { deep: true });

// define emit
const emit = defineEmits(["closeDialog", "update:record"]);

const saveOds = () => {
  // 从VEtlWithSource中提取EtlTable相关的属性
  const etlTableData: Partial<EtlTable> = {
    id: table.value.id,
    sourceDb: table.value.sourceDb,
    sourceTable: table.value.sourceTable,
    targetDb: table.value.targetDb,
    targetTable: table.value.targetTable,
    partKind: table.value.partKind,
    partName: table.value.partName,
    partFormat: table.value.partFormat,
    storageFormat: table.value.storageFormat,
    compressFormat: table.value.compressFormat,
    filter: table.value.filter,
    status: table.value.status,
    kind: table.value.kind,
    retryCnt: table.value.retryCnt,
    startTime: table.value.startTime,
    endTime: table.value.endTime,
    maxRuntime: table.value.maxRuntime,
    sid: table.value.sid,
    duration: table.value.duration
  };

  tableService.save(etlTableData as EtlTable)
    .then((updatedRecord) => {
      notify('保存成功', 'success');
      emit('closeDialog');
      emit('update:record', { ...table.value, ...updatedRecord });
    })
    .catch(err => {
      notify('保存失败: ' + err, 'error');
    });
};
</script>
<style>
.v-input .v-field {
  font-size: 0.9rem;
}
</style>

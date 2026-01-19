<template>
  <v-card prepend-icon="mdi-table" title="采集表详情" dense>
    <v-form fast-fail @submit.prevent="saveOds" ref="formRef" tag="form">
      <v-card-text>
        <v-row>
          <!-- Full width form -->
          <v-col cols="12" md="12">
            <v-card density="compact" class="pa-3">
              <v-row>
                <v-col cols="12" md="3">
                  <v-text-field variant="underlined" v-model="table.name" label="源系统"></v-text-field>
                </v-col>
                <v-col cols="12" md="3">
                  <v-text-field variant="underlined" v-model="table.sourceDb" label="源库"></v-text-field>
                </v-col>
                <v-col cols="12" md="3">
                  <v-text-field variant="underlined" v-model="table.sourceTable" label="源表"></v-text-field>
                </v-col>
                <v-col cols="12" md="3">
                  <v-text-field variant="underlined" v-model="table.filter" label="过滤规则"></v-text-field>
                </v-col>

                <v-col cols="12" md="3">
                  <v-text-field variant="underlined" v-model="table.targetDb" label="目标库"></v-text-field>
                </v-col>
                <v-col cols="12" md="3">
                  <v-text-field variant="underlined" v-model="table.targetTable" label="目标表"></v-text-field>
                </v-col>
                <v-col cols="12" md="3">
                  <v-text-field variant="underlined" v-model="table.splitPk" label="切分字段"></v-text-field>
                </v-col>
                <v-col cols="12" md="3" class="d-flex align-center">
                  <v-switch v-model="table.autoPk" inset label="自动获取切分字段" :color="table.autoPk ? 'primary' : undefined" density="compact" />
                </v-col>

                <v-col cols="12" md="3">
                  <v-text-field variant="underlined" v-model="table.partName" label="分区字段"></v-text-field>
                </v-col>
                <v-col cols="12" md="3">
                  <v-select variant="underlined" v-model="table.partFormat" :items="PARTITION_FORMATS" label="分区格式"></v-select>
                </v-col>
                <v-col cols="12" md="3">
                  <v-select variant="underlined" v-model="table.storageFormat" :items="storageOptions" label="存储格式"></v-select>
                </v-col>
                <v-col cols="12" md="3">
                  <v-select variant="underlined" v-model="table.compressFormat" :items="compressFormats" label="压缩格式"></v-select>
                </v-col>



                <v-col cols="12" md="3">
                  <v-select v-model="table.status" :items="statusOptions" item-title="label" item-value="value" label="采集状态"></v-select>
                </v-col>
                <v-col cols="12" md="2">
                  <v-text-field variant="underlined" v-model="table.retryCnt" label="剩余次数" :rules="[rules.nonNegative]"></v-text-field>
                </v-col>
                <v-col cols="12" md="3">
                  <v-text-field variant="underlined" v-model="table.maxRuntime" label="最大运行时(s)"></v-text-field>
                </v-col>
                <v-col cols="12" md="2">
                  <v-text-field variant="underlined" v-model="table.startTime" label="最近采集开始时间" readonly></v-text-field>
                </v-col>
                <v-col cols="12" md="2">
                  <v-text-field variant="underlined" v-model="table.endTime" label="最近采集结束时间" readonly></v-text-field>
                </v-col>

                <v-col cols="12">
                  <v-textarea variant="underlined" v-model="table.remark" label="备注" rows="2"></v-textarea>
                </v-col>


              </v-row>
            </v-card>
          </v-col>
        </v-row>

      </v-card-text>

      <v-card-actions class="action-bar">
        <v-spacer />
        <v-btn color="primary" @click="saveOds">保存</v-btn>
        <v-btn variant="plain" @click="emit('closeDialog')">关闭</v-btn>
      </v-card-actions>
    </v-form>
  </v-card>
</template>

<script setup lang="ts">
import { ref, watch } from "vue";
import { notify } from '@/stores/notifier';
import tableService from "@/service/table-service";
import { VEtlWithSource, EtlTable } from "@/types/database";
import { TABLE_STATUS_OPTIONS, PARTITION_FORMATS, HDFS_COMPRESS_FORMATS } from "@/utils";

const props = defineProps({
  table: {
    type: Object as () => VEtlWithSource,
    required: true,
  }
});

// 使用公共的状态选项
const statusOptions = TABLE_STATUS_OPTIONS;

// storage options as requested
const storageOptions = ['orc', 'parquet', 'text']

// reuse compress formats from constants
const compressFormats = HDFS_COMPRESS_FORMATS || []

// validation rules
const rules = {
  required: (v) => !!v || '此字段为必填项',
  nonNegative: (v) => {
    if (v === null || v === undefined || v === '') return true
    const n = Number(v)
    return (!Number.isNaN(n) && n >= 0) || '必须为非负数'
  },
  dateFormat: (v) => PARTITION_FORMATS.includes(v) || '请选择有效的日期格式'
}

// 创建本地的响应式副本用于编辑
const table = ref<VEtlWithSource>({ ...props.table });

// 监听props变化，同步更新本地副本
watch(() => props.table, (newTable) => {
  table.value = { ...newTable };
}, { deep: true });

// define emit
const emit = defineEmits(["closeDialog", "update:record", "openSchema"]);

// form ref for programmatic validation
const formRef = ref(null)

// 如果用户手动填写了切分字段(splitPk)，自动获取切分字段应被关闭并禁用
watch(
  () => table.value.splitPk,
  (val) => {
    if (val) {
      table.value.autoPk = false
    } else {
      table.value.autoPk = true
    }
  }
)

const saveOds = async () => {
  if (formRef.value && typeof formRef.value.validate === 'function') {
    const valid = await formRef.value.validate()
    if (!valid) {
      notify('请修正表单错误', 'error')
      return
    }
  }

  const etlTableData: Partial<EtlTable> = {
    id: table.value.id,
    sourceDb: table.value.sourceDb,
    sourceTable: table.value.sourceTable,
    targetDb: table.value.targetDb,
    targetTable: table.value.targetTable,
    partKind: table.value.partKind,
    partName: table.value.partName,
    partFormat: table.value.partFormat,
    splitPk: table.value.splitPk,
    autoPk: table.value.autoPk,
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

<style scoped>
.table-title {
  margin: 0;
  font-weight: 600;
}
.meta {
  color: var(--v-theme-on-surface-variant, #9aa0a6);
  font-size: 0.9rem;
}
.action-bar {
  border-top: 1px solid rgba(0,0,0,0.06);
  padding-top: 12px;
}
</style>
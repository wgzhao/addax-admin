<template>
  <!-- ODS 采集 - 批量新增表 -->
  <v-card flat>
    <v-card-text>
      <v-row>
        <v-col cols="3">
          <v-select :items="sourceSystemList" item-title="name" v-model="selectedSourceId" item-value="id"
            density="compact" return-object single-line>
            <template #prepend>
              <span class="me-2">选择采集源</span>
            </template>
          </v-select>
        </v-col>
        <v-col cols="3">
          <v-select :items="sourceDbs" :disabled="!selectedSourceId?.url" v-model="selectedDb" density="compact"
            single-line>
            <template #prepend>
              <span class="me-2">选择库</span>
            </template>
            <template #append>
              <v-btn color="primary" @click="getTables" :loading="loadingTables" :disabled="!selectedDb">获取表</v-btn>
            </template>
          </v-select>
        </v-col>

        <v-spacer />
        <v-col cols="4">
          <v-btn color="primary" @click="saveItems" :loading="loadingSave"
            :disabled="selectedCnt === 0 || !targetDb">保存</v-btn>
        </v-col>
      </v-row>
      <v-row>
        <!-- 目标库名设置行 -->

        <v-col cols="2">
          <v-text-field v-model="targetDb" label="目标库名" density="compact" hint="默认库名: ods + 源系统编号" persistent-hint
            :rules="[rules.required]">
          </v-text-field>
        </v-col>
        <v-col cols="2">
          <v-text-field v-model="partName" label="分区字段名" density="compact" hint="目标表的分区字段" persistent-hint>
          </v-text-field>
        </v-col>
        <v-col cols="2">
          <v-select v-model="partFormat" :items="partitionFormats" label="分区格式" density="compact" hint="分区日期格式"
            persistent-hint>
            <template #append>
              <span class="text-caption">{{ partitionFormatExample }}</span>
            </template>
          </v-select>
        </v-col>
        <v-col cols="2">
          <v-combobox v-model="storageFormat" :items="storageFormats" label="存储格式" density="compact" hint="目标表存储格式"
            persistent-hint>
          </v-combobox>
        </v-col>
        <v-col cols="2">
          <v-combobox v-model="compressFormat" :items="compressFormats" label="压缩格式" density="compact" hint="目标表压缩格式"
            persistent-hint>
          </v-combobox>
        </v-col>

      </v-row>

      <!-- Search field for tables - fixed width to prevent icon overlap -->
      <v-row v-if="tables.length > 0" class="mb-2">
        <v-col cols="4">
          <v-text-field v-model="search" label="搜索表" append-icon="mdi-magnify" single-line hide-details
            density="compact" clearable />
        </v-col>
        <v-col cols="4" class="d-flex align-center">
          <v-chip variant="text" color="primary" class="text-body-1">
            已选择 {{ selectedCnt }} 个表
          </v-chip>
        </v-col>
      </v-row>

      <!-- Tables container with fixed height to prevent layout jumps -->
      <div class="table-container">
        <!-- tables -->
        <v-data-table :items="tables" :headers="headers" :items-per-page="15" density="compact" show-select
          v-model="selectedTables" :search="search" item-value="name" v-if="tables.length > 0" return-object>
          <template #item.tblComment="{ item }">
            <div class="comment-cell">{{ item.tblComment }}</div>
          </template>
        </v-data-table>

        <v-alert v-else-if="loadingTables" type="info" variant="tonal" class="mt-4">
          正在加载表列表，请稍候...
        </v-alert>

        <v-alert v-else-if="tableLoadError" type="error" variant="tonal" class="mt-4">
          {{ tableLoadError }}
        </v-alert>

        <v-alert v-else type="info" variant="tonal" class="mt-4">
          请选择源系统和数据库，然后点击"获取表"按钮加载表列表
        </v-alert>
      </div>
    </v-card-text>
  </v-card>

  <!-- Success Message Dialog -->
  <v-dialog v-model="showSuccessDialog" max-width="500px" :retain-focus="false" persistent>
    <v-card>
      <v-card-title class="text-h5 bg-success text-white">
        操作成功
      </v-card-title>
      <v-card-text class="pt-4">
        {{ successMessage }}
      </v-card-text>
      <v-card-actions>
        <v-spacer></v-spacer>
        <v-btn color="primary" @click="handleSuccessConfirm">确定</v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>
<script setup lang="ts">
import { ref, onMounted, computed, watch } from "vue";
import { notify } from '@/stores/notifier';
import tableService from "@/service/table-service";
import sourceService from "@/service/source-service";
import dictService from "@/service/dict-service";
import { EtlSource, EtlTable, TableMeta } from "@/types/database";
import { DataTableHeader } from "vuetify";
const props = defineProps({
  tid: {
    type: String,
    required: false,
  }
});

// Define emits for parent component communication
const emit = defineEmits(['closeDialog', 'refresh-data']);

const headers: DataTableHeader[] = [
  { title: "源系统", key: "sid" },
  { title: "源筛选", key: "filter" },
  { title: "源用户", key: "sourceDb" },
  { title: "源表名", key: "sourceTable" },
  { title: "表注释", key: "tblComment" },
  { title: "目标库", key: "targetDb" },
  { title: "目标表", key: "targetTable" }
];

// Loading states
const loadingTables = ref(false);
const loadingSave = ref(false);
const tableLoadError = ref('');
const showSuccessDialog = ref(false);
const successMessage = ref('');
const search = ref(''); // 新增：搜索关键字
const selectedTables = ref<EtlTable[]>([]); // 新增：已选择的表格
const targetDb = ref(''); // 新增：目标库名
const partName = ref('logdate'); // 新增：分区字段名
const partFormat = ref('yyyyMMdd'); // 新增：分区格式
const storageFormat = ref(''); // 新增：存储格式
const compressFormat = ref(''); // 新增：压缩格式
const partitionFormats = ref(['yyyyMMdd', 'yyyy-MM-dd', 'yyyy/MM/dd']);
const storageFormats = ref(['orc', 'parquet', 'avro', 'textfile']);
const compressFormats = ref(['lz4', 'snappy', 'gzip', 'zstd', 'zlib']);

const partitionFormatExample = computed(() => {
  if (!partFormat.value) return '';
  const now = new Date();
  const year = now.getFullYear();
  const month = (now.getMonth() + 1).toString().padStart(2, '0');
  const day = now.getDate().toString().padStart(2, '0');

  return partFormat.value
    .replace('yyyy', year.toString())
    .replace('MM', month)
    .replace('dd', day);
});

// 表单验证规则
const rules = {
  required: (value: string) => !!value || '此字段为必填项'
};


// 选择的采集源ID
const selectedSourceId = ref<EtlSource | null>(null);

const selectedDb = ref()

const tables = ref<EtlTable[]>([]);

const defaultItem = ref<EtlTable>({
  id: null,
  sourceDb: "",
  sourceTable: "",
  targetDb: "",
  targetTable: "",
  partKind: "D",
  partName: "logdate",
  partFormat: "yyyyMMdd",
  storageFormat: "parquet",
  compressFormat: "SNAPPY",
  filter: "1=1",
  status: "U",
  kind: "A",
  retryCnt: 3,
  sid: null,
  duration: 0,
  tblComment: ""
});

const sourceSystemList = ref([]);

const sourceDbs = ref([]);

const selectedCnt = computed(() => selectedTables.value.length);

watch(selectedSourceId, (val) => {
  if (val?.id) {
    getDbsBySourceId();
  }
  // 自动设置目标库名
  if (val?.code) {
    targetDb.value = 'ods' + val.code.toLowerCase();
  } else {
    targetDb.value = '';
  }
});

const getDbsBySourceId = async () => {
  if (!selectedSourceId.value) return;

  try {
    sourceDbs.value = await sourceService.fetchDatabasesBySource(selectedSourceId.value.id);
  } catch (error) {
    console.error("获取数据库列表失败", error);
    notify(`获取数据库列表失败: ${error}`, 'error');
    sourceDbs.value = [];
  }
};

const fetchSourceData = () => {
  sourceService.listActiveSources().then(res => {
    sourceSystemList.value = res;
  }).catch(error => {
    console.error("获取源系统列表失败", error);
    notify(`获取源系统列表失败: ${error}`, 'error');
  });
};

const saveItems = async () => {
  if (selectedCnt.value === 0) {
    notify('请选择至少一个表', 'warning');
    return;
  }

  if (!targetDb.value) {
    notify('请设置目标库名', 'warning');
    return;
  }

  loadingSave.value = true;

  // set destPardKind for each item and fix destTablename
  const itemsToSave = selectedTables.value.map(item => {
    const saveItem = { ...item };
    // set targetDb for all items
    saveItem.targetDb = targetDb.value;
    saveItem.partName = partName.value;
    saveItem.partFormat = partFormat.value;
    saveItem.storageFormat = storageFormat.value;
    saveItem.compressFormat = compressFormat.value;
    // set destTablename
    if (saveItem.targetTable == "") {
      saveItem.targetTable = saveItem.sourceTable;
    }
    return saveItem;
  });

  try {
    // save data
    const response = await tableService.batchSave(itemsToSave);

    // Show success message
    successMessage.value = `成功添加 ${response} 个表到目标库 ${targetDb.value}`;
    showSuccessDialog.value = true;

  } catch (error) {
    console.error('保存失败', error);
    notify('保存失败: ' + (error || '未知错误'), 'error');
  } finally {
    loadingSave.value = false;
  }
};

const handleSuccessConfirm = () => {
  showSuccessDialog.value = false;
  // emit('closeDialog');
  emit('refresh-data');
  selectedTables.value = [];
};

const getTables = async () => {
  if (!selectedSourceId.value?.id || !selectedDb.value) {
    tableLoadError.value = '请选择源系统和数据库';
    return;
  }

  loadingTables.value = true;
  tableLoadError.value = '';

  try {
    // clear tables
    tables.value = [];

    const res = await sourceService.fetchUncollectedTables(selectedSourceId.value.id, selectedDb.value);

    if (res && res.length > 0) {
      res.forEach((element: TableMeta) => {
        // new defaultItem and populate it
        const newItem = { ...defaultItem.value };
        newItem.sid = selectedSourceId.value!.id;
        newItem.sourceDb = selectedDb.value;
        newItem.sourceTable = element.name;
        newItem.targetDb = targetDb.value; // 设置目标库名
        newItem.partName = partName.value;
        newItem.partFormat = partFormat.value;
        newItem.storageFormat = storageFormat.value;
        newItem.compressFormat = compressFormat.value;
        newItem.targetTable = element.name;
        newItem.tblComment = element.comment;
        tables.value.push(newItem);
      });
      // Show feedback
      tableLoadError.value = '';


    } else {
      tableLoadError.value = '未找到任何表';
    }
  } catch (error) {
    console.error("获取表失败", error);
    tableLoadError.value = '获取表失败: ' + (error || "未知错误");
  } finally {
    loadingTables.value = false;
  }
};

onMounted(() => {
  fetchSourceData();
  dictService.getHdfsStorageDefaults().then(res => {
    storageFormat.value = res.storageFormat;
    compressFormat.value = res.compressFormat;
  });
});
</script>
<style scoped>
.v-data-table {
  margin-top: 16px;
}

.table-container {
  min-height: 400px;
  position: relative;
}

/* Ensure dialog has stable positioning */
.v-dialog {
  position: fixed;
  z-index: 2000;
}
</style>
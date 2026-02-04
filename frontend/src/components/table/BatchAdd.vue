<template>
  <!-- ODS 采集 - 批量新增表 -->
  <v-card flat title="批量新增表" class="batch-add-card">
    <v-card-text class="batch-add-body">
      <v-row dense class="section-grid">
        <v-col cols="12">
          <v-sheet class="form-section" rounded="lg" border>
            <div class="section-header">
              <v-icon size="18" color="primary">mdi-database-arrow-right</v-icon>
              <span>采集源与加载</span>
              <v-spacer />
              <div class="header-actions">
                <v-btn color="primary" size="small" @click="saveItems" :loading="loadingSave"
                  :disabled="selectedCnt === 0 || !targetDb">保存</v-btn>
                <v-btn color="secondary" size="small" variant="tonal" @click="closeDialog">关闭</v-btn>
              </div>
            </div>
            <v-divider />
            <v-row dense class="section-body">
              <v-col cols="12" md="4">
                <v-select :items="sourceSystemList" item-title="name"
                  :item-props="item => ({
                    title: `${item.code}_${item.name}`,
                  })" v-model="selectedSourceId" item-value="id"
                  density="compact" return-object single-line>
                  <template #prepend>
                    <span class="me-2">选择采集源</span>
                  </template>
                </v-select>
              </v-col>
              <v-col cols="12" md="4">
                <v-select :items="sourceDbs" :disabled="!selectedSourceId?.url" v-model="selectedDb" density="compact"
                  single-line>
                  <template #prepend>
                    <span class="me-2">选择源库</span>
                  </template>
                  <template #append>
                    <v-btn color="primary" @click="getTables" :loading="loadingTables" :disabled="!selectedDb">获取表</v-btn>
                  </template>
                </v-select>
              </v-col>
              <v-col cols="12" md="4" class="d-flex align-center justify-end">
                <v-chip variant="tonal" color="primary" class="text-body-2">
                  已选择 {{ selectedCnt }} 个表
                </v-chip>
              </v-col>
            </v-row>
          </v-sheet>
        </v-col>

        <v-col cols="12">
          <v-sheet class="form-section" rounded="lg" border>
            <div class="section-header">
              <v-icon size="18" color="primary">mdi-cog-outline</v-icon>
              <span>目标设置</span>
            </div>
            <v-divider />
            <v-row dense class="section-body tight-row">
              <v-col cols="12" md="2">
                <v-text-field v-model="partName" label="分区字段名" density="compact" hint="目标表的分区字段" persistent-hint class="tight-field">
                  <template #append>
                    <v-tooltip bottom>
                      <template #activator="{ props }">
                        <v-icon v-bind="props" color="info" size="small" @click="showPartitionInfo">
                          mdi-information-outline
                        </v-icon>
                      </template>
                      <span>如果为空，则表示选中的表将创建为非分区表</span>
                    </v-tooltip>
                  </template>
                </v-text-field>
              </v-col>
              <v-col cols="12" md="2">
                <v-select v-model="partFormat" :items="PARTITION_FORMATS" label="分区日期格式" density="compact"
                  persistent-hint>
                </v-select>
                <div class="format-example" v-if="partitionFormatExample">
                  示例：{{ partitionFormatExample }}
                </div>
              </v-col>
              <v-col cols="12" md="1">
                <v-combobox v-model="storageFormat" :items="storageFormats" label="存储格式" density="compact"
                  persistent-hint>
                </v-combobox>
              </v-col>
              <v-col cols="12" md="1">
                <v-combobox v-model="compressFormat" :items="compressFormats" label="压缩格式" density="compact"
                  persistent-hint>
                </v-combobox>
              </v-col>
              <v-col cols="12" md="2">
                <v-text-field v-model="targetDb" label="目标库名" density="compact" placeholder="ods + 源系统编号" persistent-hint
                  :rules="[rules.required]" class="tight-field">
                </v-text-field>
              </v-col>
              <v-col cols="12" md="4">
                <v-text-field v-model="targetTableTemplate" label="目标表名模板" density="compact"
                  hint="如: ods_${table}_di 或 ${db}_${table}" persistent-hint
                  placeholder="${table}" class="tight-field">
                  <template #append>
                    <v-btn size="small" color="primary" @click="applyTargetTableTemplate"
                      :disabled="!targetTableTemplate || selectedCnt === 0">应用</v-btn>
                  </template>
                </v-text-field>
              </v-col>
            </v-row>
          </v-sheet>
        </v-col>

        <v-col cols="12">
          <v-sheet class="form-section" rounded="lg" border>
            <div class="section-header">
              <v-icon size="18" color="primary">mdi-table</v-icon>
              <span>表清单</span>
            </div>
            <v-divider />
            <v-row dense class="section-body" v-if="tables.length > 0">
              <v-col cols="12" md="4">
                <v-text-field v-model="search" label="搜索表" append-icon="mdi-magnify" single-line hide-details
                  density="compact" clearable />
              </v-col>
            </v-row>

            <div class="table-container">
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
          </v-sheet>
        </v-col>
      </v-row>
    </v-card-text>

    <v-card-actions class="pa-3 action-bar">
      <v-spacer />
      <v-btn color="secondary" @click="closeDialog" variant="tonal">关闭</v-btn>
    </v-card-actions>
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

  <!-- Partition Info Dialog -->
  <v-dialog v-model="showPartitionInfoDialog" max-width="600px">
    <v-card>
      <v-card-title class="text-h6 bg-info text-white">
        <v-icon class="me-2">mdi-information-outline</v-icon>
        分区字段说明
      </v-card-title>
      <v-card-text class="pt-4">
        <div class="mb-3">
          <h4 class="text-h6 mb-2">分区表 vs 非分区表</h4>
        </div>

        <v-row>
          <v-col cols="6">
            <v-card variant="outlined" class="pa-3">
              <h5 class="text-subtitle-1 text-success mb-2">
                <v-icon class="me-1" color="success">mdi-folder-table</v-icon>
                分区表
              </h5>
              <p class="text-body-2 mb-2"><strong>分区字段名:</strong> 有值（如：logdate、dt）</p>
              <p class="text-body-2 mb-2"><strong>数据组织:</strong> 按分区目录存储</p>
              <p class="text-body-2 mb-2"><strong>查询优势:</strong> 可按分区过滤，提高查询效率</p>
              <p class="text-body-2"><strong>适用场景:</strong> 时间序列数据、大数据量表</p>
            </v-card>
          </v-col>

          <v-col cols="6">
            <v-card variant="outlined" class="pa-3">
              <h5 class="text-subtitle-1 text-primary mb-2">
                <v-icon class="me-1" color="primary">mdi-table</v-icon>
                非分区表
              </h5>
              <p class="text-body-2 mb-2"><strong>分区字段名:</strong> 为空</p>
              <p class="text-body-2 mb-2"><strong>数据组织:</strong> 直接存储在表目录下</p>
              <p class="text-body-2 mb-2"><strong>结构简单:</strong> 无分区维护成本</p>
              <p class="text-body-2"><strong>适用场景:</strong> 配置表、小数据量表</p>
            </v-card>
          </v-col>
        </v-row>

        <v-alert type="info" variant="tonal" class="mt-4">
          <v-icon>mdi-lightbulb-outline</v-icon>
          <strong>提示：</strong>如果分区字段名为空，系统将为所有选中的表创建非分区表。
        </v-alert>
      </v-card-text>
      <v-card-actions>
        <v-spacer></v-spacer>
        <v-btn color="primary" @click="showPartitionInfoDialog = false">知道了</v-btn>
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
import { HDFS_STORAGE_FORMATS, HDFS_COMPRESS_FORMATS, PARTITION_FORMATS } from "@/utils";
import { EtlSource, EtlTable, TableMeta } from "@/types/database";
import { DataTableHeader } from "vuetify";
import dayjs from 'dayjs'

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
  { title: "近似行数", key: "approxRowCount" },
  { title: "目标库", key: "targetDb" },
  { title: "目标表", key: "targetTable" }
];

// Loading states
const loadingTables = ref(false);
const loadingSave = ref(false);
const tableLoadError = ref('');
const showSuccessDialog = ref(false);
const successMessage = ref('');
const showPartitionInfoDialog = ref(false);
const search = ref(''); // 新增：搜索关键字
type EtlTableView = EtlTable & { approxRowCount?: number };
const selectedTables = ref<EtlTableView[]>([]); // 新增：已选择的表格
const targetDb = ref(''); // 新增：目标库名
const targetTableTemplate = ref('${table}'); // 新增：目标表名模板
const partName = ref('logdate'); // 新增：分区字段名
const partFormat = ref('yyyyMMdd'); // 新增：分区格式
const storageFormat = ref(''); // 新增：存储格式
const compressFormat = ref(''); // 新增：压缩格式
const storageFormats = ref(HDFS_STORAGE_FORMATS);
const compressFormats = ref(HDFS_COMPRESS_FORMATS);


const partitionFormatExample = computed(() => {
  if (!partFormat.value) return '';
  return dayjs('2025-03-12').format(partFormat.value.replace(/y/g, 'Y').replace(/d/g, 'D'));
})

// 表单验证规则
const rules = {
  required: (value: string) => !!value || '此字段为必填项'
};


// 选择的采集源ID
const selectedSourceId = ref<EtlSource | null>(null);

const selectedDb = ref()

const tables = ref<EtlTableView[]>([]);

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
  tblComment: "",
  writeMode: "overwrite"
});

const tableRows = ref<number[]>([])

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

// Emit to parent to request closing the dialog containing this component
const closeDialog = () => {
  emit('closeDialog');
};

const showPartitionInfo = () => {
  showPartitionInfoDialog.value = true;
};

/**
 * Apply target table name template and target database to all selected tables
 * Supports placeholders: ${table} for source table name, ${db} for source database name
 */
const applyTargetTableTemplate = () => {
  if (!targetTableTemplate.value || selectedTables.value.length === 0) {
    notify('请输入模板并选择表', 'warning');
    return;
  }

  if (!targetDb.value) {
    notify('请先设置目标库名', 'warning');
    return;
  }

  let appliedCount = 0;
  selectedTables.value.forEach(item => {
    // Apply target database name
    item.targetDb = targetDb.value;

    // Apply target table name template
    const targetName = targetTableTemplate.value
      .replace(/\$\{table\}/g, item.sourceTable)
      .replace(/\$\{db\}/g, item.sourceDb);
    item.targetTable = targetName;
    appliedCount++;
  });

  notify(`已应用目标库名和表名模板到 ${appliedCount} 个表`, 'success');
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
        const viewItem: EtlTableView = { ...newItem, approxRowCount: element.approxRowCount };
        tables.value.push(viewItem);
        tableRows.value.push(element.approxRowCount);
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
.batch-add-card {
  background: rgb(var(--v-theme-surface));
}

.batch-add-body {
  padding-top: 8px;
}

.section-grid {
  row-gap: 12px;
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
  padding: 12px 14px 6px;
}

.tight-row :deep(.v-col) {
  padding-top: 4px;
  padding-bottom: 4px;
}

.tight-field :deep(.v-field) {
  min-height: 40px;
}

.tight-field :deep(.v-field__input) {
  padding-top: 8px;
  padding-bottom: 8px;
  line-height: 1.2;
}

.format-example {
  margin-top: -6px;
  padding-left: 4px;
  font-size: 12px;
  color: rgba(var(--v-theme-on-surface), 0.6);
}

.action-bar {
  border-top: 1px solid rgba(var(--v-theme-on-surface), 0.06);
}

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

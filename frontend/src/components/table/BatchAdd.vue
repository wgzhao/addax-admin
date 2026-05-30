<template>
  <!-- 批量新增采集表 —— 页面模式 -->
  <v-card flat class="batch-add-card">
    <v-stepper v-model="currentStep" flat class="batch-stepper">

      <!-- 步骤头 -->
      <v-stepper-header class="stepper-header">
        <v-stepper-item title="选择数据源" :value="1" :complete="currentStep > 1" color="primary" />
        <v-divider />
        <v-stepper-item title="选择采集表" :value="2" :complete="currentStep > 2" color="primary" />
        <v-divider />
        <v-stepper-item title="配置并提交" :value="3" :complete="currentStep > 3" color="primary" />
        <v-divider />
        <v-stepper-item title="完成" :value="4" color="primary" />
      </v-stepper-header>

      <!-- 步骤内容 -->
      <v-stepper-window class="stepper-window">

        <!-- Step 1 -->
        <v-stepper-window-item :value="1">
          <div class="step-body">
            <p class="step-hint text-body-2 text-medium-emphasis mb-5">
              选择要采集数据的源系统；选定后系统将自动列出该源下的可用数据库。
            </p>
            <v-row dense class="field-rows">
              <v-col cols="12" md="6">
                <v-select
                  :items="sourceSystemList"
                  :item-props="item => ({ title: `${item.code} — ${item.name}` })"
                  v-model="selectedSourceId"
                  item-value="id"
                  label="采集源系统 *"
                  density="compact"
                  variant="outlined"
                  return-object
                  hide-details="auto"
                  prepend-inner-icon="mdi-database-arrow-right"
                />
              </v-col>
              <v-col cols="12" md="6">
                <v-select
                  :items="sourceDbs"
                  :disabled="!selectedSourceId || loadingDbs"
                  :loading="loadingDbs"
                  v-model="selectedDb"
                  label="源数据库 *"
                  density="compact"
                  variant="outlined"
                  hide-details="auto"
                  prepend-inner-icon="mdi-database"
                  :placeholder="loadingDbs ? '正在加载...' : (!selectedSourceId ? '请先选择采集源系统' : '请选择数据库')"
                />
              </v-col>
            </v-row>

            <Transition name="fade">
              <v-alert
                v-if="selectedSourceId && selectedDb"
                type="success"
                variant="tonal"
                density="compact"
                icon="mdi-check-circle-outline"
                class="mt-5"
              >
                <strong>{{ selectedSourceId.code }}_{{ selectedSourceId.name }}</strong>
                &ensp;/&ensp;
                <strong>{{ selectedDb }}</strong>
                &emsp;—&emsp;点击「下一步」加载表列表
              </v-alert>
            </Transition>
          </div>
        </v-stepper-window-item>

        <!-- Step 2 -->
        <v-stepper-window-item :value="2">
          <div class="step-body step-body--table">

            <div class="table-toolbar" v-if="!loadingTables && tables.length > 0">
              <v-text-field
                v-model="search"
                placeholder="搜索表名 / 注释"
                prepend-inner-icon="mdi-magnify"
                single-line
                hide-details
                density="compact"
                variant="outlined"
                clearable
                class="search-field"
              />
              <v-chip color="primary" variant="tonal" size="small">
                已选 {{ selectedCnt }} / {{ tables.length }}
              </v-chip>
            </div>

            <div v-if="loadingTables" class="async-state">
              <v-progress-circular indeterminate color="primary" size="40" width="3" />
              <span class="text-body-2 text-medium-emphasis">正在连接数据库并加载表列表…</span>
            </div>

            <v-alert v-else-if="tableLoadError" type="error" variant="tonal" class="ma-4">
              {{ tableLoadError }}
            </v-alert>

            <v-data-table
              v-else-if="tables.length > 0"
              :items="tables"
              :headers="headers"
              :items-per-page="15"
              density="compact"
              show-select
              v-model="selectedTables"
              :search="search"
              item-value="sourceTable"
              return-object
              class="step2-table"
            >
              <template #item.tblComment="{ item }">
                <div class="comment-cell">{{ item.tblComment }}</div>
              </template>
            </v-data-table>

            <div v-else class="async-state">
              <v-icon size="48" color="grey-lighten-1">mdi-table-off</v-icon>
              <span class="text-body-2 text-medium-emphasis">该数据库下未找到待采集的表</span>
            </div>
          </div>
        </v-stepper-window-item>

        <!-- Step 3 -->
        <v-stepper-window-item :value="3">
          <div class="step-body">
            <v-alert type="info" variant="tonal" density="compact" icon="mdi-table-multiple" class="mb-5">
              共 <strong>{{ selectedCnt }}</strong> 张表待添加
              <template v-if="targetDb">，目标库：<strong>{{ targetDb }}</strong></template>
            </v-alert>
            <v-row class="field-rows">
              <v-col cols="12" md="3">
                <v-select
                  v-model="targetId"
                  :items="targetOptions"
                  item-title="label"
                  item-value="value"
                  label="目标端 *"
                  density="compact"
                  variant="outlined"
                  :rules="[rules.required]"
                  hide-details="auto"
                />
              </v-col>
              <v-col cols="12" md="3">
                <v-text-field
                  v-model="targetDb"
                  label="目标库名 *"
                  density="compact"
                  variant="outlined"
                  :rules="[rules.required]"
                  hide-details="auto"
                />
              </v-col>

              <v-col cols="12" md="3">
                <v-text-field
                  v-model="partName"
                  label="分区字段名"
                  density="compact"
                  variant="outlined"
                  hint="为空则创建非分区表"
                  persistent-hint
                >
                  <template #append-inner>
                    <v-tooltip location="bottom">
                      <template #activator="{ props }">
                        <v-icon
                          v-bind="props"
                          color="info"
                          size="small"
                          style="cursor:pointer"
                          @click="showPartitionInfo"
                        >mdi-information-outline</v-icon>
                      </template>
                      <span>为空时系统将为所有选中的表创建非分区表</span>
                    </v-tooltip>
                  </template>
                </v-text-field>
              </v-col>

              <v-col cols="12" md="3">
                <v-select
                  v-model="partFormat"
                  :items="PARTITION_FORMATS"
                  label="分区日期格式"
                  density="compact"
                  variant="outlined"
                  :hint="partitionFormatExample ? `示例：${partitionFormatExample}` : ''"
                  persistent-hint
                />
              </v-col>
            </v-row>
            <v-row>
              <v-col cols="12" md="4">
                <v-combobox
                  v-model="storageFormat"
                  :items="storageFormats"
                  label="存储格式"
                  density="compact"
                  variant="outlined"
                  hide-details="auto"
                />
              </v-col>
              <v-col cols="12" md="4">
                <v-combobox
                  v-model="compressFormat"
                  :items="compressFormats"
                  label="压缩格式"
                  density="compact"
                  variant="outlined"
                  hide-details="auto"
                />
              </v-col>
              <v-col cols="12" md="4">
                <v-text-field
                  v-model="targetTableTemplate"
                  label="目标表名模板"
                  density="compact"
                  variant="outlined"
                  hint="支持 ${table}（源表名）和 ${db}（源库名）占位符；提交时自动应用"
                  persistent-hint
                  placeholder="${table}"
                />
              </v-col>
            </v-row>

            <div class="preview-section mt-4">
              <div class="preview-header">
                <v-icon size="16" color="primary">mdi-eye-outline</v-icon>
                <span class="text-body-2 font-weight-medium">目标表名预览</span>
                <span class="text-caption text-medium-emphasis ml-2">（提交时自动应用模板）</span>
              </div>
              <div class="preview-table-wrap">
                <table class="preview-table">
                  <thead>
                    <tr>
                      <th>源表名</th>
                      <th>→</th>
                      <th>目标库.目标表</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="row in previewRows" :key="row.sourceTable">
                      <td class="src-col">{{ row.sourceTable }}</td>
                      <td class="arrow-col">→</td>
                      <td class="dst-col">{{ row.targetDb }}<span class="sep">.</span>{{ row.targetTable }}</td>
                    </tr>
                    <tr v-if="selectedCnt > PREVIEW_LIMIT">
                      <td colspan="3" class="more-row">…还有 {{ selectedCnt - PREVIEW_LIMIT }} 张表</td>
                    </tr>
                    <tr v-if="selectedCnt === 0">
                      <td colspan="3" class="more-row">请在上一步选择要采集的表</td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        </v-stepper-window-item>

        <!-- Step 4: 完成 / 状态 -->
        <v-stepper-window-item :value="4">
          <v-row justify="center">
            <v-col cols="12" md="8">
              <v-card class="pa-8 text-center">
                <v-icon size="48" color="success">mdi-check-circle-outline</v-icon>
                <div class="text-h5 mt-3">成功添加 {{ savedCount }} 张表</div>
                <div class="text-subtitle-1 my-3">系统已接收请求，后端正在初始化目标表。</div>
                <div class="d-flex justify-center gap-3 mt-4">
                  <v-btn variant="text" @click="router.push({ path: '/table', query: { refresh: Date.now() } })">返回采集表</v-btn>
                  <v-btn color="primary" @click="() => { selectedTables = []; currentStep = 1 }">继续新增</v-btn>
                </div>
              </v-card>
            </v-col>
          </v-row>
        </v-stepper-window-item>

      </v-stepper-window>
    </v-stepper>

    <v-divider />
    <v-card-actions class="step-actions">
      <v-btn
        v-if="currentStep > 1 && currentStep < 4"
        variant="text"
        prepend-icon="mdi-chevron-left"
        @click="prevStep"
      >上一步</v-btn>
      <v-spacer />
      <v-btn variant="text" @click="router.push('/table')">取消</v-btn>
      <v-btn
        v-if="currentStep < 3"
        color="primary"
        variant="flat"
        :disabled="!canProceedToNext"
        :loading="loadingTables"
        append-icon="mdi-chevron-right"
        @click="nextStep"
      >下一步</v-btn>

      <v-btn
        v-if="currentStep === 3"
        color="primary"
        variant="flat"
        :loading="loadingSave"
        :disabled="!targetDb || !targetId"
        prepend-icon="mdi-check"
        @click="saveItems"
      >提交</v-btn>
    </v-card-actions>

    <!-- 分区字段说明对话框 -->
    <v-dialog v-model="showPartitionInfoDialog" max-width="600px">
      <v-card>
        <v-card-title class="text-h6 bg-info text-white">
            <h4 class="text-h6 mb-2">分区表 vs 非分区表</h4>
        </v-card-title>
        <v-card-text>
          <v-row>
            <v-col cols="6">
              <v-card variant="outlined" class="pa-3">
                <h5 class="text-subtitle-1 text-success mb-2">
                  <v-icon class="me-1" color="success">mdi-folder-table</v-icon>分区表
                </h5>
                <p class="text-body-2 mb-2"><strong>分区字段名:</strong> 有值（如：logdate、dt）</p>
                <p class="text-body-2 mb-2"><strong>数据组织:</strong> 按分区目录存储</p>
              </v-card>
            </v-col>

            <v-col cols="6">
              <v-card variant="outlined" class="pa-3">
                <h5 class="text-subtitle-1 text-primary mb-2">
                  <v-icon class="me-1" color="primary">mdi-table</v-icon>非分区表
                </h5>
                <p class="text-body-2 mb-2"><strong>分区字段名:</strong> 为空</p>
                <p class="text-body-2 mb-2"><strong>数据组织:</strong> 直接存储在表目录下</p>
              </v-card>
            </v-col>
          </v-row>
          <v-alert type="info" variant="tonal" class="mt-4">
            <v-icon>mdi-lightbulb-outline</v-icon>
            <strong>提示：</strong>如果分区字段名为空，系统将为所有选中的表创建非分区表。
          </v-alert>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn color="primary" @click="showPartitionInfoDialog = false">知道了</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </v-card>
</template>

<script setup lang="ts">
import { ref, onMounted, computed, watch } from "vue";
import { useRouter } from 'vue-router'
import { notify } from '@/stores/notifier';
import tableService from "@/service/table-service";
import sourceService from "@/service/source-service";
import targetService from "@/service/target-service";
import dictService from "@/service/dict-service";
import { HDFS_STORAGE_FORMATS, HDFS_COMPRESS_FORMATS, PARTITION_FORMATS } from "@/utils";
import { EtlSource, EtlTable, TableMeta, EtlTarget } from "@/types/database";
import { DataTableHeader } from "vuetify";
import dayjs from 'dayjs';

defineProps({
  tid: { type: String, required: false }
});

// emits kept for backward compatibility (unused in page mode)
const emit = defineEmits(['refresh-data']);
const router = useRouter();

// Stepper state
const currentStep = ref(1);
const savedCount = ref(0);

const canProceedToNext = computed(() => {
  if (currentStep.value === 1) return !!selectedSourceId.value && !!selectedDb.value;
  if (currentStep.value === 2) return selectedCnt.value > 0;
  return false;
});

const nextStep = async () => {
  if (currentStep.value === 1) {
    currentStep.value = 2;
    await getTables();
  } else if (currentStep.value === 2) {
    currentStep.value = 3;
  }
};

const prevStep = () => { if (currentStep.value > 1) currentStep.value--; };

// Table headers
const headers: DataTableHeader[] = [
  { title: "源系统",  key: "sid" },
  { title: "源筛选",  key: "filter" },
  { title: "源用户",  key: "sourceDb" },
  { title: "源表名",  key: "sourceTable" },
  { title: "表注释",  key: "tblComment" },
  { title: "近似行数", key: "approxRowCount" },
  { title: "目标库",  key: "targetDb" },
  { title: "目标表",  key: "targetTable" },
];

// UI state
const loadingTables        = ref(false);
const loadingDbs           = ref(false);
const loadingSave          = ref(false);
const tableLoadError       = ref('');
const showPartitionInfoDialog = ref(false);
const search               = ref('');

// Form data
type EtlTableView = EtlTable & { approxRowCount?: number };

const selectedTables      = ref<EtlTableView[]>([]);
const targetDb            = ref('');
const targetTableTemplate = ref('${table}');
const partName            = ref('logdate');
const partFormat          = ref('yyyyMMdd');
const storageFormat       = ref('');
const compressFormat      = ref('');
const storageFormats      = ref(HDFS_STORAGE_FORMATS);
const compressFormats     = ref(HDFS_COMPRESS_FORMATS);
const targetOptions       = ref<{ label: string; value: number }[]>([]);
const targetId            = ref<number | null>(null);

const selectedSourceId = ref<EtlSource | null>(null);
const selectedDb       = ref<string | null>(null);
const tables           = ref<EtlTableView[]>([]);
const tableRows        = ref<number[]>([]);
const sourceSystemList = ref<EtlSource[]>([]);
const sourceDbs        = ref<string[]>([]);

const selectedCnt = computed(() => selectedTables.value.length);

const partitionFormatExample = computed(() => {
  if (!partFormat.value) return '';
  return dayjs('2025-03-12').format(
    partFormat.value.replace(/y/g, 'Y').replace(/d/g, 'D')
  );
});

// Validation rules
const rules = {
  required: (value: any) => !!value || '此字段为必填项',
};

const defaultItem: EtlTable = {
  id: null, sourceDb: '', sourceTable: '', targetDb: '', targetTable: '',
  partKind: 'D', partName: 'logdate', partFormat: 'yyyyMMdd',
  storageFormat: 'parquet', compressFormat: 'SNAPPY',
  filter: '1=1', status: 'U', kind: 'A', retryCnt: 3,
  sid: null, duration: 0, tblComment: '', writeMode: 'overwrite',
};

// Watchers
watch(selectedSourceId, (val) => {
  selectedDb.value = null;
  tables.value = [];
  selectedTables.value = [];
  sourceDbs.value = [];

  targetDb.value = val?.code ? 'ods' + val.code.toLowerCase() : '';

  if (val?.id) getDbsBySourceId();
});

watch(selectedDb, () => {
  tables.value = [];
  selectedTables.value = [];
});

watch(targetId, (val) => {
  tables.value.forEach(item => { item.targetId = val; });
  selectedTables.value.forEach(item => { item.targetId = val; });
});

// API
const getDbsBySourceId = async () => {
  if (!selectedSourceId.value) return;
  loadingDbs.value = true;
  try {
    sourceDbs.value = await sourceService.fetchDatabasesBySource(selectedSourceId.value.id);
  } catch (error) {
    console.error("获取数据库列表失败", error);
    notify(`获取数据库列表失败: ${error}`, 'error');
    sourceDbs.value = [];
  } finally {
    loadingDbs.value = false;
  }
};

const fetchSourceData = () => {
  sourceService.listActiveSources()
    .then(res => { sourceSystemList.value = res; })
    .catch(error => notify(`获取源系统列表失败: ${error}`, 'error'));
};

const getTables = async () => {
  if (!selectedSourceId.value?.id || !selectedDb.value) {
    tableLoadError.value = '请选择源系统和数据库';
    return;
  }

  loadingTables.value = true;
  tableLoadError.value = '';
  tables.value = [];
  tableRows.value = [];
  try {
    const res = await sourceService.fetchUncollectedTables(
      selectedSourceId.value.id, selectedDb.value
    );
    if (res?.length) {
      res.forEach((element: TableMeta) => {
        const newItem: EtlTable = { ...defaultItem };
        newItem.sid          = selectedSourceId.value!.id;
        newItem.sourceDb     = selectedDb.value!;
        newItem.sourceTable  = element.name;
        newItem.targetDb     = targetDb.value;
        newItem.partName     = partName.value;
        newItem.partFormat   = partFormat.value;
        newItem.storageFormat = storageFormat.value;
        newItem.compressFormat = compressFormat.value;
        newItem.targetId     = targetId.value;
        newItem.targetTable  = element.name;
        newItem.tblComment   = element.comment;
        tables.value.push({ ...newItem, approxRowCount: element.approxRowCount });
        tableRows.value.push(element.approxRowCount);
      });
    } else {
      tableLoadError.value = '未找到任何待采集的表';
    }
  } catch (error) {
    tableLoadError.value = '获取表失败: ' + (error || '未知错误');
  } finally {
    loadingTables.value = false;
  }
};

const applyTargetTableTemplate = () => {
  if (!selectedTables.value.length) return;
  const tpl = targetTableTemplate.value || '${table}';
  selectedTables.value.forEach(item => {
    item.targetDb    = targetDb.value;
    item.targetTable = tpl.replace(/\$\{table\}/g, item.sourceTable).replace(/\$\{db\}/g, item.sourceDb);
  });
};

const saveItems = async () => {
  if (!selectedCnt.value) { notify('请选择至少一个表', 'warning'); return; }
  if (!targetDb.value) { notify('请设置目标库名', 'warning'); return; }
  if (!targetId.value) { notify('请选择目标端', 'warning'); return; }

  // Apply template before saving
  applyTargetTableTemplate();

  loadingSave.value = true;
  const itemsToSave = selectedTables.value.map(item => {
    const s = { ...item };
    s.targetDb = targetDb.value;
    s.partName = partName.value;
    s.partFormat = partFormat.value;
    s.storageFormat = storageFormat.value;
    s.compressFormat = compressFormat.value;
    s.targetId = targetId.value;
    if (!s.targetTable) s.targetTable = s.sourceTable;
    return s;
  });
  try {
    const response = await tableService.batchSave(itemsToSave);
    const saved = Number(response) || itemsToSave.length;
    savedCount.value = saved;
    // move to final status step
    currentStep.value = 4;
  } catch (error) {
    notify('保存失败: ' + (error || '未知错误'), 'error');
  } finally {
    loadingSave.value = false;
  }
};

const showPartitionInfo = () => { showPartitionInfoDialog.value = true; };

// Preview
const PREVIEW_LIMIT = 8;

const resolveTargetTable = (item: EtlTableView) => {
  const tpl = targetTableTemplate.value || '${table}';
  return tpl
    .replace(/\$\{table\}/g, item.sourceTable)
    .replace(/\$\{db\}/g, item.sourceDb);
};

const previewRows = computed(() =>
  selectedTables.value.slice(0, PREVIEW_LIMIT).map(item => ({
    sourceTable: item.sourceTable,
    targetDb: targetDb.value || item.targetDb,
    targetTable: resolveTargetTable(item),
  }))
);

onMounted(() => {
  fetchSourceData();
  targetService.list(true)
    .then(res => {
      targetOptions.value = res
        .filter((t: EtlTarget) => t.id !== undefined)
        .map((t: EtlTarget) => ({ label: `${t.name} (${t.targetType})`, value: t.id as number }));
      if (targetOptions.value.length > 0) targetId.value = targetOptions.value[0].value;
    })
    .catch(error => notify(`获取目标端列表失败: ${error}`, 'error'));

  dictService.getHdfsStorageDefaults().then(res => {
    storageFormat.value  = res.storageFormat;
    compressFormat.value = res.compressFormat;
  });
});
</script>
<style scoped>
.batch-add-card {
  display: flex;
  flex-direction: column;
  /* Fill parent card's content area so stepper matches the v-card width */
  width: 100%;
  max-width: none;
  margin: 0;
  position: relative;
}

.batch-stepper {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
}

.batch-stepper :deep(.v-stepper-window) {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  margin-block: 0;
}

.stepper-header {
  border-bottom: 1px solid rgba(var(--v-theme-on-surface), 0.08);
  box-shadow: none !important;
  flex-shrink: 0;
}

.step-body {
  /* reduce horizontal padding so step content uses more width */
  padding: 12px 12px 16px;
  min-height: 300px;
}

.step-body--table {
  padding: 12px 0 0;
  display: flex;
  flex-direction: column;
}

.step-hint { line-height: 1.7; }
.field-rows { row-gap: 16px; }

.table-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  /* align with reduced step-body horizontal padding */
  padding: 0 12px 10px;
  flex-shrink: 0;
}

.search-field { max-width: 320px; flex: 1; }

.async-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 14px;
  padding: 72px 24px;
}

.step2-table { border-top: 1px solid rgba(var(--v-theme-on-surface), 0.07); }

.comment-cell { max-width: 220px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

.advanced-panel :deep(.v-expansion-panel) {
  border: 1px dashed rgba(var(--v-theme-on-surface), 0.18);
  background: rgb(var(--v-theme-surface));
}
.advanced-panel :deep(.v-expansion-panel-title) { min-height: 40px; }

.step-actions { padding: 10px 16px; flex-shrink: 0; }

.preview-section {
  border: 1px solid rgba(var(--v-theme-on-surface), 0.10);
  border-radius: 8px;
  overflow: hidden;
}

.preview-header { display: flex; align-items: center; gap: 6px; padding: 8px 12px; background: rgba(var(--v-theme-primary), 0.05); border-bottom: 1px solid rgba(var(--v-theme-on-surface), 0.08); }

.preview-table-wrap { overflow-x: auto; max-height: 220px; overflow-y: auto; }
.preview-table { width: 100%; border-collapse: collapse; font-size: 0.8rem; }
.preview-table thead tr { background: rgba(var(--v-theme-on-surface), 0.03); }

.preview-table th { padding: 6px 12px; text-align: left; font-weight: 600; color: rgb(var(--v-theme-on-surface-variant)); white-space: nowrap; border-bottom: 1px solid rgba(var(--v-theme-on-surface), 0.08); }
.preview-table td { padding: 5px 12px; border-bottom: 1px solid rgba(var(--v-theme-on-surface), 0.05); vertical-align: middle; }
.preview-table tbody tr:last-child td { border-bottom: none; }

.src-col { color: rgb(var(--v-theme-on-surface-variant)); font-family: monospace; }
.arrow-col { width: 24px; text-align: center; color: rgba(var(--v-theme-on-surface), 0.3); }
.dst-col { font-family: monospace; color: rgb(var(--v-theme-primary)); }
.dst-col .sep { color: rgba(var(--v-theme-on-surface), 0.4); margin: 0 1px; }

.more-row { text-align: center; color: rgb(var(--v-theme-on-surface-variant)); font-style: italic; padding: 8px; }

.fade-enter-active, .fade-leave-active { transition: opacity 0.25s ease; }
.fade-enter-from, .fade-leave-to { opacity: 0; }
</style>

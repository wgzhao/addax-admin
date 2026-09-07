<template>
  <v-card flat class="batch-add-shell">
    <div class="workflow-head">
      <div></div>
      <div class="overview-grid">
        <div class="overview-card">
          <span class="overview-label">采集源</span>
          <strong>{{ selectedSourceLabel }}</strong>
        </div>
        <div class="overview-card">
          <span class="overview-label">源数据库</span>
          <strong>{{ selectedDb || '未选择' }}</strong>
        </div>
        <div class="overview-card">
          <span class="overview-label">已选表</span>
          <strong>{{ selectedCnt }} / {{ tables.length }}</strong>
        </div>
        <div class="overview-card">
          <span class="overview-label">目标库</span>
          <strong>{{ targetDb || '待配置' }}</strong>
        </div>
      </div>
    </div>

    <v-stepper v-model="currentStep" flat class="batch-stepper">
      <v-stepper-header class="stepper-header">
        <v-stepper-item title="选择数据源" :value="1" :complete="currentStep > 1" color="primary" />
        <v-divider />
        <v-stepper-item title="选择采集表" :value="2" :complete="currentStep > 2" color="primary" />
        <v-divider />
        <v-stepper-item title="配置并提交" :value="3" :complete="currentStep > 3" color="primary" />
        <v-divider />
        <v-stepper-item title="完成" :value="4" color="primary" />
      </v-stepper-header>

      <v-stepper-window class="stepper-window">
        <v-stepper-window-item :value="1">
          <BatchSourceStep
            v-model:selected-source-id="selectedSourceId"
            v-model:selected-db="selectedDb"
            :source-system-list="sourceSystemList"
            :source-dbs="sourceDbs"
            :loading-dbs="loadingDbs"
          />
        </v-stepper-window-item>

        <v-stepper-window-item :value="2">
          <BatchTableStep
            v-model:selected="selectedTables"
            v-model:search="search"
            :tables="tables"
            :loading="loadingTables"
            :load-error="tableLoadError"
            :selected-cnt="selectedCnt"
          />
        </v-stepper-window-item>

        <v-stepper-window-item :value="3">
          <BatchConfigStep
            v-model:target-id="targetId"
            v-model:target-db="targetDb"
            v-model:target-table-template="targetTableTemplate"
            v-model:part-name="partName"
            v-model:part-format="partFormat"
            v-model:storage-format="storageFormat"
            v-model:compress-format="compressFormat"
            v-model:selected="selectedTables"
            :target-options="targetOptions"
            :storage-formats="storageFormats"
            :compress-formats="compressFormats"
          />
        </v-stepper-window-item>

        <v-stepper-window-item :value="4">
          <BatchDoneStep
            :saved-count="savedCount"
            :target-db="targetDb"
            @restart="resetFlow"
            @back="router.push({ path: '/table', query: { refresh: Date.now() } })"
          />
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
        >上一步</v-btn
      >
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
        >下一步</v-btn
      >

      <v-btn
        v-if="currentStep === 3"
        color="primary"
        variant="flat"
        :loading="loadingSave"
        :disabled="!targetDb || !targetId"
        prepend-icon="mdi-check"
        @click="saveItems"
        >提交</v-btn
      >
    </v-card-actions>
  </v-card>
</template>

<script setup lang="ts">
  import { ref, onMounted, computed, watch } from 'vue';
  import { useRouter } from 'vue-router';
  import { notify } from '@/stores/notifier';
  import tableService from '@/service/table-service';
  import sourceService from '@/service/source-service';
  import targetService from '@/service/target-service';
  import dictService from '@/service/dict-service';
  import { HDFS_STORAGE_FORMATS, HDFS_COMPRESS_FORMATS } from '@/utils';
  import {
    EtlSource,
    EtlTable,
    EtlTableView,
    TableMeta,
    EtlTarget,
  } from '@/types/database';
  import BatchSourceStep from './batch/BatchSourceStep.vue';
  import BatchTableStep from './batch/BatchTableStep.vue';
  import BatchConfigStep from './batch/BatchConfigStep.vue';
  import BatchDoneStep from './batch/BatchDoneStep.vue';

  defineProps({
    tid: { type: String, required: false },
  });

  const emit = defineEmits(['refresh-data']);
  void emit;
  const router = useRouter();

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

  const prevStep = () => {
    if (currentStep.value > 1) currentStep.value--;
  };

  const loadingTables = ref(false);
  const loadingDbs = ref(false);
  const loadingSave = ref(false);
  const tableLoadError = ref('');
  const search = ref('');

  const selectedTables = ref<EtlTableView[]>([]);
  const targetDb = ref('');
  const targetTableTemplate = ref('${table}');
  const partName = ref('logdate');
  const partFormat = ref('yyyyMMdd');
  const storageFormat = ref('');
  const compressFormat = ref('');
  const storageFormats = ref(HDFS_STORAGE_FORMATS);
  const compressFormats = ref(HDFS_COMPRESS_FORMATS);
  const targetOptions = ref<{ label: string; value: number }[]>([]);
  const targetId = ref<number | null>(null);

  const selectedSourceId = ref<EtlSource | null>(null);
  const selectedDb = ref<string | null>(null);
  const tables = ref<EtlTableView[]>([]);
  const tableRows = ref<number[]>([]);
  const sourceSystemList = ref<EtlSource[]>([]);
  const sourceDbs = ref<string[]>([]);

  const selectedCnt = computed(() => selectedTables.value.length);
  const selectedSourceLabel = computed(() =>
    selectedSourceId.value
      ? `${selectedSourceId.value.code} · ${selectedSourceId.value.name}`
      : '未选择'
  );

  const defaultItem: EtlTable = {
    id: null,
    sourceDb: '',
    sourceTable: '',
    targetDb: '',
    targetTable: '',
    partKind: 'D',
    partName: 'logdate',
    partFormat: 'yyyyMMdd',
    storageFormat: 'parquet',
    compressFormat: 'SNAPPY',
    filter: '1=1',
    status: 'U',
    kind: 'A',
    retryCnt: 3,
    sid: null,
    duration: 0,
    tblComment: '',
    writeMode: 'overwrite',
  };

  watch(selectedSourceId, val => {
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

  watch(targetId, val => {
    tables.value.forEach(item => {
      item.targetId = val;
    });
    selectedTables.value.forEach(item => {
      item.targetId = val;
    });
  });

  const getDbsBySourceId = async () => {
    if (!selectedSourceId.value) return;
    loadingDbs.value = true;
    try {
      sourceDbs.value = await sourceService.fetchDatabasesBySource(selectedSourceId.value.id);
    } catch (error) {
      console.error('获取数据库列表失败', error);
      notify(`获取数据库列表失败: ${error}`, 'error');
      sourceDbs.value = [];
    } finally {
      loadingDbs.value = false;
    }
  };

  const fetchSourceData = () => {
    sourceService
      .listActiveSources()
      .then(res => {
        sourceSystemList.value = res;
      })
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
        selectedSourceId.value.id,
        selectedDb.value
      );
      if (res?.length) {
        res.forEach((element: TableMeta) => {
          const newItem: EtlTable = { ...defaultItem };
          newItem.sid = selectedSourceId.value!.id;
          newItem.sourceDb = selectedDb.value!;
          newItem.sourceTable = element.name;
          newItem.targetDb = targetDb.value;
          newItem.partName = partName.value;
          newItem.partFormat = partFormat.value;
          newItem.storageFormat = storageFormat.value;
          newItem.compressFormat = compressFormat.value;
          newItem.targetId = targetId.value;
          newItem.targetTable = element.name;
          newItem.tblComment = element.comment;
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
      item.targetDb = targetDb.value;
      item.targetTable = tpl
        .replace(/\$\{table\}/g, item.sourceTable)
        .replace(/\$\{db\}/g, item.sourceDb);
    });
  };

  const saveItems = async () => {
    if (!selectedCnt.value) {
      notify('请选择至少一个表', 'warning');
      return;
    }
    if (!targetDb.value) {
      notify('请设置目标库名', 'warning');
      return;
    }
    if (!targetId.value) {
      notify('请选择目标端', 'warning');
      return;
    }

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
      currentStep.value = 4;
    } catch (error) {
      notify('保存失败: ' + (error || '未知错误'), 'error');
    } finally {
      loadingSave.value = false;
    }
  };

  const resetFlow = () => {
    selectedTables.value = [];
    savedCount.value = 0;
    currentStep.value = 1;
    search.value = '';
  };

  onMounted(() => {
    fetchSourceData();
    targetService
      .list(true)
      .then(res => {
        targetOptions.value = res
          .filter((t: EtlTarget) => t.id !== undefined)
          .map((t: EtlTarget) => ({ label: `${t.name} (${t.targetType})`, value: t.id as number }));
        if (targetOptions.value.length > 0) targetId.value = targetOptions.value[0].value;
      })
      .catch(error => notify(`获取目标端列表失败: ${error}`, 'error'));

    dictService.getHdfsStorageDefaults().then(res => {
      storageFormat.value = res.storageFormat;
      compressFormat.value = res.compressFormat;
    });
  });
</script>

<style scoped>
  .batch-add-shell {
    display: flex;
    flex-direction: column;
    gap: 18px;
  }

  .workflow-head {
    display: flex;
    flex-direction: column;
    gap: 14px;
  }

  .workflow-kicker {
    font-size: 0.76rem;
    letter-spacing: 0.08em;
    text-transform: uppercase;
    color: rgba(var(--v-theme-primary), 0.9);
  }

  .workflow-title {
    margin-top: 4px;
    font-size: 1.12rem;
    font-weight: 700;
    color: rgb(var(--v-theme-on-surface));
  }

  .workflow-subtitle {
    margin-top: 8px;
    max-width: 780px;
    color: rgba(var(--v-theme-on-surface), 0.68);
    line-height: 1.7;
  }

  .overview-grid {
    display: grid;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: 12px;
  }

  .overview-card {
    padding: 14px 16px;
    border-radius: 16px;
    border: 1px solid rgba(var(--v-theme-on-surface), 0.08);
    background: linear-gradient(
        180deg,
        rgba(var(--v-theme-primary), 0.06),
        rgba(var(--v-theme-primary), 0.01)
      ),
      rgba(var(--v-theme-surface), 0.96);
  }

  .overview-label {
    display: block;
    margin-bottom: 6px;
    font-size: 0.76rem;
    letter-spacing: 0.04em;
    text-transform: uppercase;
    color: rgba(var(--v-theme-on-surface), 0.56);
  }

  .batch-stepper {
    border-radius: 18px;
    border: 1px solid rgba(var(--v-theme-on-surface), 0.08);
    background: rgba(var(--v-theme-surface), 0.96);
    overflow: hidden;
  }

  .stepper-header {
    border-bottom: 1px solid rgba(var(--v-theme-on-surface), 0.08);
    background: linear-gradient(180deg, rgba(var(--v-theme-primary), 0.035), transparent 92%);
  }

  .batch-stepper :deep(.v-stepper-header) {
    box-shadow: none;
  }

  .batch-stepper :deep(.v-stepper-window) {
    min-height: 0;
  }

  .step-actions {
    padding: 14px 18px;
  }

  @media (max-width: 1120px) {
    .overview-grid {
      grid-template-columns: 1fr;
    }
  }

  @media (max-width: 760px) {
    .step-actions {
      padding: 12px 16px;
    }
  }
</style>

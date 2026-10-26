<template>
  <v-card flat class="batch-add-shell">
    <div class="workflow-head">
      <div class="context-row">
        <span class="context-chip" :title="selectedSourceLabel">
          <v-icon size="14">mdi-database</v-icon>
          {{ selectedSourceLabel }}
        </span>
        <span class="context-chip" :title="selectedDb || '未选择源库'">
          <v-icon size="14">mdi-database-search-outline</v-icon>
          {{ selectedDb || '未选择源库' }}
        </span>
        <span
          class="context-chip"
          :class="{ 'context-chip--strong': selectedCnt > 0 }"
          :title="`已选 ${selectedCnt} / ${tables.length}`"
        >
          <v-icon size="14">mdi-table-check</v-icon>
          已选 {{ selectedCnt }} / {{ tables.length }}
        </span>
        <span class="context-chip" :title="targetDb || '目标库待配置'">
          <v-icon size="14">mdi-database-arrow-right-outline</v-icon>
          {{ targetDb || '目标库待配置' }}
        </span>
      </div>
    </div>

    <v-stepper v-model="currentStep" flat class="batch-stepper">
      <v-stepper-header class="stepper-header">
        <v-stepper-item title="选择采集源" :value="1" :complete="currentStep > 1" color="primary" />
        <v-divider />
        <v-stepper-item title="选择采集表" :value="2" :complete="currentStep > 2" color="primary" />
        <v-divider />
        <v-stepper-item
          title="配置目标策略"
          :value="3"
          :complete="currentStep > 3"
          color="primary"
        />
        <v-divider />
        <v-stepper-item title="预览与校验" :value="4" :complete="currentStep > 4" color="primary" />
        <v-divider />
        <v-stepper-item title="创建结果" :value="5" color="primary" />
      </v-stepper-header>

      <v-stepper-window class="stepper-window">
        <v-stepper-window-item :value="1">
          <BatchSourceStep
            v-model:selected-source-id="selectedSourceId"
            v-model:selected-db="selectedDb"
            :source-system-list="sourceSystemList"
            :source-dbs="sourceDbs"
            :loading-dbs="loadingDbs"
            :loading-sources="loadingSources"
            :source-load-error="sourceLoadError"
            @retry="fetchSourceData"
          />
        </v-stepper-window-item>

        <v-stepper-window-item :value="2">
          <BatchTableStep
            v-model:selected="selectedTables"
            v-model:search="search"
            :tables="tables"
            :loading="loadingTables"
            :load-error="tableLoadError"
            @retry="getTables"
            @back="currentStep = 1"
          />
        </v-stepper-window-item>

        <v-stepper-window-item :value="3">
          <BatchConfigStep
            v-model:target-id="targetId"
            v-model:target-db="targetDb"
            v-model:target-table-template="targetTableTemplate"
            v-model:schema-management-mode="schemaManagementMode"
            v-model:options="tableOptions"
            v-model:selected="selectedTables"
            :targets="targetEndpoints"
            :storage-formats="storageFormats"
            :compress-formats="compressFormats"
            :duplicate-target-names="duplicateTargetNames"
            :target-name-issue="targetNameIssue"
          />
        </v-stepper-window-item>

        <v-stepper-window-item :value="4">
          <BatchReviewStep v-model:rows="reviewRows" />
        </v-stepper-window-item>

        <v-stepper-window-item :value="5">
          <BatchDoneStep
            :results="commitResults"
            :target-db="targetDb"
            @restart="resetFlow"
            @retry="retryFailed"
            @back="router.push({ path: '/table', query: { refresh: Date.now() } })"
          />
        </v-stepper-window-item>
      </v-stepper-window>
    </v-stepper>

    <v-divider />
    <v-card-actions v-if="currentStep < 5" class="step-actions">
      <v-btn
        v-if="currentStep > 1 && currentStep < 5"
        variant="text"
        prepend-icon="mdi-chevron-left"
        @click="prevStep"
        >上一步</v-btn
      >
      <span v-if="actionHint" class="step-hint">{{ actionHint }}</span>
      <v-spacer />
      <v-btn variant="text" @click="requestCancel">取消</v-btn>
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
        :disabled="!targetDb || !targetId || !!targetNameIssue"
        append-icon="mdi-shield-search"
        @click="preparePlans"
        >生成预览并校验</v-btn
      >
      <v-btn
        v-if="currentStep === 4"
        color="primary"
        variant="flat"
        :loading="loadingSave"
        :disabled="includedCount === 0 || invalidIncludedCount > 0"
        prepend-icon="mdi-database-check-outline"
        @click="commitPlans"
        >确认创建 {{ includedCount }} 张表</v-btn
      >
    </v-card-actions>

    <ConfirmDialog
      v-model="cancelOpen"
      title="放弃本次新增"
      message="已选择的采集表与配置不会保存，确定离开？"
      confirm-text="放弃并离开"
      cancel-text="继续配置"
      @confirm="confirmLeave"
    />
  </v-card>
</template>

<script setup lang="ts">
  import { ref, onMounted, onBeforeUnmount, computed, watch } from 'vue';
  import { onBeforeRouteLeave, useRouter } from 'vue-router';
  import { notify } from '@/stores/notifier';
  import { reviewRowIssue, reviewRowIssueLabel, reviewRowValid } from '@/utils/collection-plan';
  import ConfirmDialog from '@/components/ConfirmDialog.vue';
  import tableService from '@/service/table-service';
  import sourceService from '@/service/source-service';
  import targetService from '@/service/target-service';
  import dictService from '@/service/dict-service';
  import { HDFS_STORAGE_FORMATS, HDFS_COMPRESS_FORMATS } from '@/utils';
  import { normalizeTargetOptions, pickTargetOptions } from '@/utils/target-options';
  import {
    EtlSource,
    EtlTable,
    EtlTableView,
    TableMeta,
    EtlTarget,
    EtlTargetOptions,
    CollectionPlanReviewRow,
    CollectionPlanCommitResult,
  } from '@/types/database';
  import BatchSourceStep from './batch/BatchSourceStep.vue';
  import BatchTableStep from './batch/BatchTableStep.vue';
  import BatchConfigStep from './batch/BatchConfigStep.vue';
  import BatchDoneStep from './batch/BatchDoneStep.vue';
  import BatchReviewStep from './batch/BatchReviewStep.vue';

  defineProps({
    tid: { type: String, required: false },
  });

  const emit = defineEmits(['refresh-data']);
  void emit;
  const router = useRouter();

  const currentStep = ref(1);

  const canProceedToNext = computed(() => {
    if (currentStep.value === 1) return !!selectedSourceId.value && !!selectedDb.value;
    if (currentStep.value === 2) return selectedCnt.value > 0;
    return false;
  });

  const actionHint = computed(() => {
    if (currentStep.value === 1 && !canProceedToNext.value) return '请选择采集源与源数据库';
    if (currentStep.value === 2 && !canProceedToNext.value) return '请至少选择一张采集表';
    if (currentStep.value === 3) {
      if (!targetId.value || !targetDb.value) return '请选择目标端并确认目标库';
      if (targetNameIssue.value) return targetNameIssue.value;
    }
    if (currentStep.value === 4) {
      if (invalidIncludedCount.value > 0) {
        const issue = firstInvalidIssueLabel.value;
        return issue
          ? `有 ${invalidIncludedCount.value} 张表存在「${issue}」，修正后才能创建`
          : `有 ${invalidIncludedCount.value} 张表存在映射问题，修正后才能创建`;
      }
      if (includedCount.value === 0) return '请至少纳入一张表';
    }
    return '';
  });

  const cancelOpen = ref(false);
  const pendingRoute = ref('/table');
  const allowLeave = ref(false);
  // Why: only prompt when the user has built up state that would be expensive to lose.
  const hasPendingWork = () =>
    currentStep.value < 5 && (currentStep.value > 1 || selectedTables.value.length > 0);

  const requestCancel = () => {
    if (!hasPendingWork()) {
      allowLeave.value = true;
      router.push('/table');
      return;
    }
    pendingRoute.value = '/table';
    cancelOpen.value = true;
  };

  const confirmLeave = () => {
    allowLeave.value = true;
    router.push(pendingRoute.value);
  };

  onBeforeRouteLeave(to => {
    if (allowLeave.value || !hasPendingWork()) return true;
    pendingRoute.value = to.fullPath;
    cancelOpen.value = true;
    return false;
  });

  const onBeforeUnload = (event: BeforeUnloadEvent) => {
    if (!hasPendingWork()) return;
    event.preventDefault();
    event.returnValue = '';
  };

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
  const tableOptions = ref<EtlTargetOptions>(normalizeTargetOptions());
  const storageFormats = ref(HDFS_STORAGE_FORMATS);
  const compressFormats = ref(HDFS_COMPRESS_FORMATS);
  const targetEndpoints = ref<{ label: string; value: number; targetType: string }[]>([]);
  const targetId = ref<number | null>(null);
  const schemaManagementMode = ref<'MANAGED' | 'EXTERNAL'>('MANAGED');
  const reviewRows = ref<CollectionPlanReviewRow[]>([]);
  const commitResults = ref<CollectionPlanCommitResult[]>([]);
  const selectedSourceId = ref<EtlSource | null>(null);
  const selectedDb = ref<string | null>(null);
  const tables = ref<EtlTableView[]>([]);
  const tableRows = ref<number[]>([]);
  const sourceSystemList = ref<EtlSource[]>([]);
  const sourceDbs = ref<string[]>([]);

  const selectedCnt = computed(() => selectedTables.value.length);
  const includedCount = computed(() => reviewRows.value.filter(row => row.included).length);
  const invalidIncludedCount = computed(
    () => reviewRows.value.filter(row => row.included && !reviewRowValid(row)).length
  );
  const firstInvalidIssueLabel = computed(() => {
    const invalid = reviewRows.value.find(row => row.included && !reviewRowValid(row));
    return invalid ? reviewRowIssueLabel(reviewRowIssue(invalid)) : '';
  });

  const selectedTargetType = computed(
    () => targetEndpoints.value.find(t => t.value === targetId.value)?.targetType
  );
  const selectedSourceLabel = computed(() =>
    selectedSourceId.value
      ? `${selectedSourceId.value.code} · ${selectedSourceId.value.name}`
      : '未选择'
  );

  const resolveTargetTable = (item: EtlTableView) => {
    const tpl = targetTableTemplate.value || '${table}';
    return tpl
      .replace(/\$\{table\}/g, item.sourceTable)
      .replace(/\$\{db\}/g, item.sourceDb)
      .trim();
  };

  // Why: a template without ${table} silently maps several sources onto one target table.
  const duplicateTargetNames = computed(() => {
    const counts = new Map<string, number>();
    selectedTables.value.forEach(item => {
      const name = resolveTargetTable(item).toLowerCase();
      if (!name) return;
      counts.set(name, (counts.get(name) ?? 0) + 1);
    });
    return [...counts.entries()].filter(([, count]) => count > 1).map(([name]) => name);
  });

  const targetNameIssue = computed(() => {
    if (!selectedTables.value.length) return '';
    if (selectedTables.value.some(item => !resolveTargetTable(item))) return '目标表名不能为空';
    if (duplicateTargetNames.value.length) {
      return `目标表名重复：${duplicateTargetNames.value.join('、')}`;
    }
    return '';
  });

  const defaultItem: EtlTable = {
    id: null,
    sourceDb: '',
    sourceTable: '',
    targetDb: '',
    targetTable: '',
    targetOptions: normalizeTargetOptions(),
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
    search.value = '';

    targetDb.value = val?.code ? 'ods' + val.code.toLowerCase() : '';

    if (val?.id) getDbsBySourceId();
  });

  watch(selectedDb, () => {
    tables.value = [];
    selectedTables.value = [];
    search.value = '';
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

  const loadingSources = ref(false);
  const sourceLoadError = ref('');

  const fetchSourceData = async () => {
    loadingSources.value = true;
    sourceLoadError.value = '';
    try {
      sourceSystemList.value = await sourceService.listActiveSources();
    } catch (error) {
      sourceSystemList.value = [];
      sourceLoadError.value = `获取源系统列表失败：${(error as Error)?.message || error}`;
    } finally {
      loadingSources.value = false;
    }
  };

  const getTables = async () => {
    if (!selectedSourceId.value?.id || !selectedDb.value) {
      tableLoadError.value = '请选择源系统和数据库';
      return;
    }

    loadingTables.value = true;
    tableLoadError.value = '';
    // Why: a reload can return different rows; keeping the old object refs would leave
    // invisible selections that still count towards the next step.
    tables.value = [];
    tableRows.value = [];
    selectedTables.value = [];
    search.value = '';
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
          newItem.targetOptions = pickTargetOptions(tableOptions.value, selectedTargetType.value);
          newItem.targetId = targetId.value;
          newItem.targetTable = element.name;
          newItem.tblComment = element.comment;
          tables.value.push({ ...newItem, approxRowCount: element.approxRowCount });
          tableRows.value.push(element.approxRowCount);
        });
      }
    } catch (error) {
      tableLoadError.value = `获取表失败：${(error as Error)?.message || error || '未知错误'}`;
    } finally {
      loadingTables.value = false;
    }
  };

  const applyTargetTableTemplate = () => {
    if (!selectedTables.value.length) return;
    selectedTables.value.forEach(item => {
      item.targetDb = targetDb.value;
      item.targetTable = resolveTargetTable(item);
    });
  };

  const preparePlans = async () => {
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
    if (targetNameIssue.value) {
      notify(targetNameIssue.value, 'warning');
      return;
    }

    applyTargetTableTemplate();

    loadingSave.value = true;
    const itemsToSave = selectedTables.value.map(item => {
      const s = { ...item };
      s.targetDb = targetDb.value;
      s.targetOptions = pickTargetOptions(tableOptions.value, selectedTargetType.value);
      s.targetId = targetId.value;
      s.schemaManagementMode = schemaManagementMode.value;
      if (!s.targetTable) s.targetTable = s.sourceTable;
      return s;
    });
    try {
      const plans = await tableService.previewCollectionPlans(
        itemsToSave.map(item => ({
          sourceId: item.sid as number,
          sourceDb: item.sourceDb,
          sourceTable: item.sourceTable,
          targetId: item.targetId as number,
          targetDb: item.targetDb,
          targetTable: item.targetTable,
          schemaManagementMode: schemaManagementMode.value,
          targetOptions: item.targetOptions || {},
        }))
      );
      reviewRows.value = itemsToSave.map((table, index) => {
        const plan = plans[index];
        return {
          table,
          plan,
          mappings: plan.mappings.map((mapping, mappingIndex) => ({
            sourceColumnName: mapping.sourceColumnName,
            targetColumnName: mapping.targetColumnName,
            ordinalNo: mappingIndex + 1,
          })),
          included: plan.status !== 'ERROR',
        };
      });
      currentStep.value = 4;
    } catch (error) {
      notify('生成预览失败：' + ((error as Error)?.message || error || '未知错误'), 'error');
    } finally {
      loadingSave.value = false;
    }
  };

  const commitPlans = async () => {
    const selected = reviewRows.value.filter(row => row.included);
    if (!selected.length || invalidIncludedCount.value > 0) {
      notify('请先处理本次纳入创建的阻断项', 'warning');
      return;
    }
    loadingSave.value = true;
    try {
      const results = await tableService.commitCollectionPlans(
        selected.map(row => ({
          table: row.table as EtlTable,
          mappings: row.mappings.map((mapping, index) => ({
            sourceColumnName: mapping.sourceColumnName,
            targetColumnName: mapping.targetColumnName!,
            ordinalNo: index + 1,
          })),
          targetSchemaFingerprint: row.plan.targetSchemaFingerprint,
        }))
      );
      // Why: a retry only commits failed rows; merge so the result screen keeps the earlier successes.
      const byTable = new Map(commitResults.value.map(result => [result.sourceTable, result]));
      results.forEach(result => byTable.set(result.sourceTable, result));
      commitResults.value = [...byTable.values()];
      currentStep.value = 5;
    } catch (error) {
      notify(`创建失败: ${(error as Error)?.message || String(error)}`, 'error');
    } finally {
      loadingSave.value = false;
    }
  };

  const retryFailed = () => {
    const failed = new Set(
      commitResults.value
        .filter(result => result.status === 'FAILED')
        .map(result => result.sourceTable)
    );
    if (!failed.size) return;
    // Why: keep the successful rows out of the retry so a second commit cannot duplicate them.
    reviewRows.value.forEach(row => {
      row.included = failed.has(row.table.sourceTable);
    });
    currentStep.value = 4;
  };

  const resetFlow = () => {
    selectedTables.value = [];
    reviewRows.value = [];
    commitResults.value = [];
    tables.value = [];
    tableLoadError.value = '';
    currentStep.value = 1;
    search.value = '';
  };

  onMounted(() => {
    window.addEventListener('beforeunload', onBeforeUnload);
    fetchSourceData();
    targetService
      .list(true)
      .then(res => {
        targetEndpoints.value = res
          .filter((t: EtlTarget) => t.id !== undefined)
          .map((t: EtlTarget) => ({
            label: `${t.name} (${t.targetType})`,
            value: t.id as number,
            targetType: t.targetType,
          }));
        if (targetEndpoints.value.length > 0) targetId.value = targetEndpoints.value[0].value;
      })
      .catch(error => notify(`获取目标端列表失败: ${error}`, 'error'));

    dictService
      .getHdfsStorageDefaults()
      .then(res => {
        tableOptions.value.hdfs.storageFormat = res.storageFormat;
        tableOptions.value.hdfs.compressFormat = res.compressFormat;
      })
      .catch(() => {
        // Why: storage defaults are optional; the form still works with the built-in values.
      });
  });

  onBeforeUnmount(() => {
    window.removeEventListener('beforeunload', onBeforeUnload);
  });
</script>

<style scoped>
  .batch-add-shell {
    display: flex;
    flex-direction: column;
    gap: 18px;
    /* Why: v-card clips overflow by default, which disables the sticky action bar */
    overflow: visible;
  }

  .workflow-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
  }

  .context-row {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
    min-height: 34px;
  }

  .context-chip {
    display: inline-flex;
    align-items: center;
    gap: 5px;
    max-width: 260px;
    padding: 4px 10px;
    border: 1px solid var(--ds-border-subtle);
    border-radius: 999px;
    background: rgba(var(--v-theme-on-surface), 0.03);
    color: var(--ds-text-2);
    font-size: 12px;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .context-chip--strong {
    border-color: rgba(var(--v-theme-primary), 0.25);
    background: rgba(var(--v-theme-primary), 0.08);
    color: rgb(var(--v-theme-primary));
    font-weight: 500;
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
    position: sticky;
    /* Why: the app footer is fixed at the bottom; stick above it instead of underneath it. */
    bottom: var(--v-layout-bottom, 0px);
    z-index: 3;
    padding: 12px 18px;
    border-top: 1px solid var(--ds-border-subtle);
    background: var(--ds-surface-raised);
    box-shadow: 0 -6px 16px rgba(15, 23, 42, 0.06);
  }

  .step-hint {
    margin-left: 8px;
    color: var(--ds-text-3);
    font-size: 12px;
  }

  @media (max-width: 1120px) {
    .context-row {
      gap: 6px;
    }
  }

  @media (max-width: 760px) {
    .step-actions {
      padding: 12px 16px;
    }
  }
</style>

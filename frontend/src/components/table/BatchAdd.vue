<template>
  <v-card flat class="batch-add-shell">
    <div class="workflow-head">
      <div>
        <div class="workflow-kicker">批量新增流程</div>
        <div class="workflow-title">按“源 → 表 → 目标”的顺序完成批量采集表创建</div>
        <div class="workflow-subtitle">
          采用更清晰的步骤分层：先确定来源，再筛选表，最后只聚焦真正影响结果的目标配置与预览。
        </div>
      </div>

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
          <div class="step-layout">
            <div class="step-hero">
              <div class="step-kicker">Step 1</div>
              <div class="step-title">确定采集来源</div>
              <div class="step-desc">
                先选源系统，再选对应数据库。完成后再进入表选择，避免第二步信息过载。
              </div>
            </div>

            <div class="stage-grid stage-grid--2">
              <section class="stage-panel">
                <div class="stage-panel__header">
                  <div>
                    <div class="stage-panel__title">采集源系统</div>
                    <div class="stage-panel__caption">系统会根据所选采集源自动联动可用数据库。</div>
                  </div>
                </div>
                <v-select
                  :items="sourceSystemList"
                  :item-props="item => ({ title: `${item.code} — ${item.name}` })"
                  v-model="selectedSourceId"
                  item-value="id"
                  label="采集源系统 *"
                  density="comfortable"
                  variant="outlined"
                  return-object
                  hide-details="auto"
                  prepend-inner-icon="mdi-database-arrow-right"
                />
              </section>

              <section class="stage-panel">
                <div class="stage-panel__header">
                  <div>
                    <div class="stage-panel__title">源数据库</div>
                    <div class="stage-panel__caption">
                      只有在确定采集源后，才会加载对应数据库列表。
                    </div>
                  </div>
                </div>
                <v-select
                  :items="sourceDbs"
                  :disabled="!selectedSourceId || loadingDbs"
                  :loading="loadingDbs"
                  v-model="selectedDb"
                  label="源数据库 *"
                  density="comfortable"
                  variant="outlined"
                  hide-details="auto"
                  prepend-inner-icon="mdi-database"
                  :placeholder="
                    loadingDbs
                      ? '正在加载...'
                      : !selectedSourceId
                        ? '请先选择采集源系统'
                        : '请选择数据库'
                  "
                />
              </section>
            </div>

            <Transition name="fade">
              <v-alert
                v-if="selectedSourceId && selectedDb"
                type="success"
                variant="tonal"
                density="comfortable"
                icon="mdi-check-circle-outline"
                class="step-alert"
              >
                当前已锁定
                <strong>{{ selectedSourceId.code }} · {{ selectedSourceId.name }}</strong> 与数据库
                <strong>{{ selectedDb }}</strong
                >，点击「下一步」加载待采集表。
              </v-alert>
            </Transition>
          </div>
        </v-stepper-window-item>

        <v-stepper-window-item :value="2">
          <div class="step-layout">
            <div class="step-hero">
              <div class="step-kicker">Step 2</div>
              <div class="step-title">筛选并选择待采集表</div>
              <div class="step-desc">
                把搜索、选择反馈和表清单放在同一层，用户可以快速定位重点而不是淹没在表格里。
              </div>
            </div>

            <div v-if="loadingTables" class="panel-state">
              <v-progress-circular indeterminate color="primary" size="44" width="4" />
              <div class="panel-state__title">正在连接数据库并加载表列表</div>
              <div class="panel-state__desc">请稍候，系统正在拉取该数据库下尚未纳入采集的表。</div>
            </div>

            <v-alert v-else-if="tableLoadError" type="error" variant="tonal" class="step-alert">
              {{ tableLoadError }}
            </v-alert>

            <div v-else-if="tables.length > 0" class="table-stage">
              <div class="table-stage__toolbar">
                <div class="table-stage__toolbar-left">
                  <v-text-field
                    v-model="search"
                    placeholder="搜索表名 / 注释"
                    prepend-inner-icon="mdi-magnify"
                    single-line
                    hide-details
                    density="comfortable"
                    variant="outlined"
                    clearable
                    class="search-field"
                  />
                  <div class="toolbar-tip">优先通过表名或注释缩小范围，再做批量勾选。</div>
                </div>
                <div class="table-stage__toolbar-right">
                  <v-chip color="primary" variant="tonal" size="small"
                    >已选 {{ selectedCnt }}</v-chip
                  >
                  <v-chip variant="outlined" size="small">候选 {{ tables.length }}</v-chip>
                </div>
              </div>

              <v-data-table
                :items="tables"
                :headers="headers"
                :items-per-page="15"
                density="comfortable"
                show-select
                v-model="selectedTables"
                :search="search"
                item-value="sourceTable"
                return-object
                class="step2-table"
              >
                <template #item.sourceTable="{ item }">
                  <div class="table-name-cell">
                    <div class="table-name">{{ item.sourceTable }}</div>
                    <div class="table-meta">{{ item.sourceDb }}</div>
                  </div>
                </template>

                <template #item.tblComment="{ item }">
                  <div class="comment-cell">{{ item.tblComment || '无表注释' }}</div>
                </template>

                <template #item.approxRowCount="{ item }">
                  <span class="mono-data">{{ item.approxRowCount ?? '-' }}</span>
                </template>
              </v-data-table>
            </div>

            <div v-else class="panel-state">
              <v-icon size="48" class="panel-state__icon">mdi-table-off</v-icon>
              <div class="panel-state__title">该数据库下未找到待采集的表</div>
              <div class="panel-state__desc">
                可以返回上一步重新选择数据源，或确认当前数据库是否已经全部纳入采集。
              </div>
            </div>
          </div>
        </v-stepper-window-item>

        <v-stepper-window-item :value="3">
          <div class="step-layout step-layout--config">
            <div class="step-hero">
              <div class="step-kicker">Step 3</div>
              <div class="step-title">配置目标端并检查预览</div>
              <div class="step-desc">
                把真正影响落库结果的配置放在主区，把命名预览固定在右侧，避免提交前失焦。
              </div>
            </div>

            <v-alert
              type="info"
              variant="tonal"
              density="comfortable"
              icon="mdi-table-multiple"
              class="step-alert"
            >
              共 <strong>{{ selectedCnt }}</strong> 张表待添加
              <template v-if="targetDb"
                >，目标库：<strong>{{ targetDb }}</strong></template
              >
            </v-alert>

            <div class="config-grid">
              <div class="config-main">
                <section class="stage-panel">
                  <div class="stage-panel__header">
                    <div>
                      <div class="stage-panel__title">目标配置</div>
                      <div class="stage-panel__caption">
                        优先处理目标端、目标库和目标表模板，这是本步骤最核心的三项。
                      </div>
                    </div>
                  </div>
                  <v-row dense>
                    <v-col cols="12" md="4">
                      <v-select
                        v-model="targetId"
                        :items="targetOptions"
                        item-title="label"
                        item-value="value"
                        label="目标端 *"
                        density="comfortable"
                        variant="outlined"
                        :rules="[rules.required]"
                        hide-details="auto"
                      />
                    </v-col>
                    <v-col cols="12" md="4">
                      <v-text-field
                        v-model="targetDb"
                        label="目标库名 *"
                        density="comfortable"
                        variant="outlined"
                        :rules="[rules.required]"
                        hide-details="auto"
                      />
                    </v-col>
                    <v-col cols="12" md="4">
                      <v-text-field
                        v-model="targetTableTemplate"
                        label="目标表名模板"
                        density="comfortable"
                        variant="outlined"
                        hint="支持 ${table}（源表名）与 ${db}（源库名）占位符"
                        persistent-hint
                        placeholder="${table}"
                      />
                    </v-col>
                  </v-row>
                </section>

                <section class="stage-panel">
                  <div class="stage-panel__header">
                    <div>
                      <div class="stage-panel__title">分区与存储</div>
                      <div class="stage-panel__caption">
                        这些配置决定了目标表的分区方式、格式与压缩策略，作为第二层信息展示。
                      </div>
                    </div>
                  </div>
                  <v-row dense>
                    <v-col cols="12" md="6">
                      <v-text-field
                        v-model="partName"
                        label="分区字段名"
                        density="comfortable"
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
                                style="cursor: pointer"
                                @click="showPartitionInfo"
                                >mdi-information-outline</v-icon
                              >
                            </template>
                            <span>为空时系统将为所有选中的表创建非分区表</span>
                          </v-tooltip>
                        </template>
                      </v-text-field>
                    </v-col>
                    <v-col cols="12" md="6">
                      <v-select
                        v-model="partFormat"
                        :items="PARTITION_FORMATS"
                        label="分区日期格式"
                        density="comfortable"
                        variant="outlined"
                        :hint="partitionFormatExample ? `示例：${partitionFormatExample}` : ''"
                        persistent-hint
                      />
                    </v-col>
                    <v-col cols="12" md="6">
                      <v-combobox
                        v-model="storageFormat"
                        :items="storageFormats"
                        label="存储格式"
                        density="comfortable"
                        variant="outlined"
                        hide-details="auto"
                      />
                    </v-col>
                    <v-col cols="12" md="6">
                      <v-combobox
                        v-model="compressFormat"
                        :items="compressFormats"
                        label="压缩格式"
                        density="comfortable"
                        variant="outlined"
                        hide-details="auto"
                      />
                    </v-col>
                  </v-row>
                </section>
              </div>

              <aside class="config-side">
                <section class="preview-card">
                  <div class="preview-card__header">
                    <div>
                      <div class="preview-card__title">目标表名预览</div>
                      <div class="preview-card__caption">提交前优先核对命名模板是否符合预期。</div>
                    </div>
                    <v-chip size="small" variant="tonal" color="primary"
                      >预览 {{ Math.min(selectedCnt, PREVIEW_LIMIT) }} 项</v-chip
                    >
                  </div>

                  <div class="preview-highlights">
                    <v-chip size="small" variant="outlined">目标库 {{ targetDb || '-' }}</v-chip>
                    <v-chip size="small" variant="outlined"
                      >模板 {{ targetTableTemplate || '${table}' }}</v-chip
                    >
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
                          <td class="dst-col">
                            {{ row.targetDb }}<span class="sep">.</span>{{ row.targetTable }}
                          </td>
                        </tr>
                        <tr v-if="selectedCnt > PREVIEW_LIMIT">
                          <td colspan="3" class="more-row">
                            …还有 {{ selectedCnt - PREVIEW_LIMIT }} 张表未展开
                          </td>
                        </tr>
                        <tr v-if="selectedCnt === 0">
                          <td colspan="3" class="more-row">请先在上一步选择要采集的表</td>
                        </tr>
                      </tbody>
                    </table>
                  </div>
                </section>
              </aside>
            </div>
          </div>
        </v-stepper-window-item>

        <v-stepper-window-item :value="4">
          <div class="success-shell">
            <v-icon size="56" color="success">mdi-check-circle-outline</v-icon>
            <div class="success-title">成功添加 {{ savedCount }} 张表</div>
            <div class="success-desc">请求已经提交，系统将继续在后端初始化目标表与对应配置。</div>
            <div class="success-meta">
              <v-chip size="small" variant="tonal" color="success"
                >目标库 {{ targetDb || '-' }}</v-chip
              >
              <v-chip size="small" variant="outlined">表数量 {{ savedCount }}</v-chip>
            </div>
            <div class="success-actions">
              <v-btn
                variant="text"
                @click="router.push({ path: '/table', query: { refresh: Date.now() } })"
                >返回采集表</v-btn
              >
              <v-btn color="primary" variant="flat" @click="resetFlow">继续新增</v-btn>
            </div>
          </div>
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
  import { ref, onMounted, computed, watch } from 'vue';
  import { useRouter } from 'vue-router';
  import { notify } from '@/stores/notifier';
  import tableService from '@/service/table-service';
  import sourceService from '@/service/source-service';
  import targetService from '@/service/target-service';
  import dictService from '@/service/dict-service';
  import { HDFS_STORAGE_FORMATS, HDFS_COMPRESS_FORMATS, PARTITION_FORMATS } from '@/utils';
  import { EtlSource, EtlTable, TableMeta, EtlTarget } from '@/types/database';
  import { DataTableHeader } from 'vuetify';
  import dayjs from 'dayjs';

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

  const headers: DataTableHeader[] = [
    { title: '源系统', key: 'sid' },
    { title: '源筛选', key: 'filter' },
    { title: '源用户', key: 'sourceDb' },
    { title: '源表名', key: 'sourceTable' },
    { title: '表注释', key: 'tblComment' },
    { title: '近似行数', key: 'approxRowCount' },
    { title: '目标库', key: 'targetDb' },
    { title: '目标表', key: 'targetTable' },
  ];

  const loadingTables = ref(false);
  const loadingDbs = ref(false);
  const loadingSave = ref(false);
  const tableLoadError = ref('');
  const showPartitionInfoDialog = ref(false);
  const search = ref('');

  type EtlTableView = EtlTable & { approxRowCount?: number };

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

  const partitionFormatExample = computed(() => {
    if (!partFormat.value) return '';
    return dayjs('2025-03-12').format(partFormat.value.replace(/y/g, 'Y').replace(/d/g, 'D'));
  });

  const rules = {
    required: (value: any) => !!value || '此字段为必填项',
  };

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

  const showPartitionInfo = () => {
    showPartitionInfoDialog.value = true;
  };

  const PREVIEW_LIMIT = 8;

  const resolveTargetTable = (item: EtlTableView) => {
    const tpl = targetTableTemplate.value || '${table}';
    return tpl.replace(/\$\{table\}/g, item.sourceTable).replace(/\$\{db\}/g, item.sourceDb);
  };

  const previewRows = computed(() =>
    selectedTables.value.slice(0, PREVIEW_LIMIT).map(item => ({
      sourceTable: item.sourceTable,
      targetDb: targetDb.value || item.targetDb,
      targetTable: resolveTargetTable(item),
    }))
  );

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
    background:
      linear-gradient(
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

  .step-layout {
    display: flex;
    flex-direction: column;
    gap: 18px;
    padding: 20px;
  }

  .step-layout--config {
    gap: 16px;
  }

  .step-hero {
    display: flex;
    flex-direction: column;
    gap: 6px;
  }

  .step-kicker {
    font-size: 0.75rem;
    letter-spacing: 0.08em;
    text-transform: uppercase;
    color: rgba(var(--v-theme-primary), 0.86);
  }

  .step-title {
    font-size: 1.02rem;
    font-weight: 700;
    color: rgb(var(--v-theme-on-surface));
  }

  .step-desc {
    max-width: 760px;
    color: rgba(var(--v-theme-on-surface), 0.66);
    line-height: 1.65;
  }

  .stage-grid {
    display: grid;
    gap: 16px;
  }

  .stage-grid--2 {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .stage-panel,
  .preview-card,
  .table-stage {
    border-radius: 18px;
    border: 1px solid rgba(var(--v-theme-on-surface), 0.08);
    background: rgba(var(--v-theme-surface), 0.98);
    overflow: hidden;
  }

  .stage-panel {
    padding: 18px;
  }

  .stage-panel__header {
    margin-bottom: 14px;
  }

  .stage-panel__title,
  .preview-card__title {
    font-weight: 600;
    color: rgb(var(--v-theme-on-surface));
  }

  .stage-panel__caption,
  .preview-card__caption {
    margin-top: 4px;
    font-size: 0.84rem;
    color: rgba(var(--v-theme-on-surface), 0.62);
    line-height: 1.6;
  }

  .step-alert {
    border-radius: 16px;
  }

  .table-stage__toolbar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    padding: 18px;
    border-bottom: 1px solid rgba(var(--v-theme-on-surface), 0.08);
  }

  .table-stage__toolbar-left {
    display: flex;
    align-items: center;
    gap: 12px;
    flex: 1;
    min-width: 0;
  }

  .table-stage__toolbar-right {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
  }

  .toolbar-tip {
    font-size: 0.82rem;
    color: rgba(var(--v-theme-on-surface), 0.58);
    white-space: nowrap;
  }

  .search-field {
    max-width: 360px;
  }

  .step2-table :deep(.v-table__wrapper) {
    max-height: 62vh;
  }

  .table-name-cell {
    display: flex;
    flex-direction: column;
    gap: 3px;
  }

  .table-name {
    font-weight: 600;
    color: rgb(var(--v-theme-on-surface));
  }

  .table-meta,
  .comment-cell {
    color: rgba(var(--v-theme-on-surface), 0.62);
  }

  .comment-cell {
    max-width: 280px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .mono-data {
    font-family:
      'SF Mono', 'Monaco', 'Inconsolata', 'Roboto Mono', 'Consolas', 'Courier New', monospace;
  }

  .config-grid {
    display: grid;
    grid-template-columns: minmax(0, 1.35fr) minmax(320px, 0.9fr);
    gap: 16px;
    align-items: start;
  }

  .config-main {
    display: flex;
    flex-direction: column;
    gap: 16px;
  }

  .config-side {
    min-width: 0;
  }

  .preview-card {
    position: sticky;
    top: 12px;
  }

  .preview-card__header {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 12px;
    padding: 18px;
    border-bottom: 1px solid rgba(var(--v-theme-on-surface), 0.08);
  }

  .preview-highlights {
    display: flex;
    gap: 8px;
    flex-wrap: wrap;
    padding: 14px 18px 0;
  }

  .preview-table-wrap {
    max-height: 420px;
    overflow: auto;
    padding: 14px 18px 18px;
  }

  .preview-table {
    width: 100%;
    border-collapse: collapse;
    font-size: 0.83rem;
  }

  .preview-table thead tr {
    background: rgba(var(--v-theme-on-surface), 0.03);
  }

  .preview-table th {
    padding: 9px 10px;
    text-align: left;
    font-weight: 600;
    color: rgba(var(--v-theme-on-surface), 0.72);
    white-space: nowrap;
    border-bottom: 1px solid rgba(var(--v-theme-on-surface), 0.08);
  }

  .preview-table td {
    padding: 9px 10px;
    border-bottom: 1px solid rgba(var(--v-theme-on-surface), 0.05);
    vertical-align: middle;
  }

  .src-col {
    color: rgba(var(--v-theme-on-surface), 0.7);
    font-family:
      'SF Mono', 'Monaco', 'Inconsolata', 'Roboto Mono', 'Consolas', 'Courier New', monospace;
  }

  .arrow-col {
    width: 24px;
    text-align: center;
    color: rgba(var(--v-theme-on-surface), 0.34);
  }

  .dst-col {
    font-family:
      'SF Mono', 'Monaco', 'Inconsolata', 'Roboto Mono', 'Consolas', 'Courier New', monospace;
    color: rgb(var(--v-theme-primary));
  }

  .sep {
    color: rgba(var(--v-theme-on-surface), 0.4);
    margin: 0 1px;
  }

  .more-row {
    text-align: center;
    color: rgba(var(--v-theme-on-surface), 0.62);
    font-style: italic;
  }

  .panel-state {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 10px;
    min-height: 260px;
    padding: 24px;
    text-align: center;
    border-radius: 18px;
    border: 1px dashed rgba(var(--v-theme-on-surface), 0.16);
    background: rgba(var(--v-theme-on-surface), 0.02);
  }

  .panel-state__icon {
    color: rgba(var(--v-theme-on-surface), 0.28);
  }

  .panel-state__title {
    font-size: 1rem;
    font-weight: 600;
    color: rgb(var(--v-theme-on-surface));
  }

  .panel-state__desc {
    max-width: 520px;
    color: rgba(var(--v-theme-on-surface), 0.64);
    line-height: 1.6;
  }

  .success-shell {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 12px;
    min-height: 420px;
    padding: 32px 20px;
    text-align: center;
  }

  .success-title {
    font-size: 1.18rem;
    font-weight: 700;
    color: rgb(var(--v-theme-on-surface));
  }

  .success-desc {
    max-width: 560px;
    color: rgba(var(--v-theme-on-surface), 0.66);
    line-height: 1.7;
  }

  .success-meta,
  .success-actions {
    display: flex;
    align-items: center;
    gap: 10px;
    flex-wrap: wrap;
    justify-content: center;
  }

  .step-actions {
    padding: 14px 18px;
  }

  .fade-enter-active,
  .fade-leave-active {
    transition: opacity 0.25s ease;
  }

  .fade-enter-from,
  .fade-leave-to {
    opacity: 0;
  }

  @media (max-width: 1120px) {
    .overview-grid,
    .stage-grid--2,
    .config-grid {
      grid-template-columns: 1fr;
    }

    .preview-card {
      position: static;
    }
  }

  @media (max-width: 760px) {
    .table-stage__toolbar,
    .table-stage__toolbar-left {
      flex-direction: column;
      align-items: stretch;
    }

    .toolbar-tip {
      white-space: normal;
    }

    .search-field {
      max-width: none;
    }

    .step-layout {
      padding: 16px;
    }

    .step-actions {
      padding: 12px 16px;
    }
  }
</style>

<template>
  <div class="step-layout step-layout--config">
    <div class="step-hero">
      <div class="step-kicker">Step 3</div>
      <h2 class="step-title">配置目标策略</h2>
      <div class="step-desc">
        先确定目标表由谁维护，再设置目标位置与命名规则。字段和结构校验将在下一步统一展示。
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
                这些配置将应用到本批次选择的 {{ selectedCnt }} 张表。
              </div>
            </div>
          </div>
          <div class="target-field-grid">
            <v-select
              v-model="schemaManagementMode"
              class="target-field"
              :items="[
                { title: '平台创建并维护', value: 'MANAGED' },
                { title: '目标表已存在，仅校验', value: 'EXTERNAL' },
              ]"
              label="目标表结构管理"
              density="comfortable"
              variant="outlined"
              :required="true"
              hide-details="auto"
            />
            <v-select
              v-model="targetId"
              class="target-field"
              :items="targets"
              item-title="label"
              item-value="value"
              label="目标端"
              density="comfortable"
              variant="outlined"
              :required="true"
              :rules="[rules.required]"
              hide-details="auto"
            />
            <v-text-field
              v-model="targetDb"
              class="target-field"
              label="目标库名"
              density="comfortable"
              variant="outlined"
              :required="true"
              :rules="[rules.required]"
              hide-details="auto"
              autocomplete="off"
              spellcheck="false"
            />
            <v-text-field
              v-model="targetTableTemplate"
              class="target-field"
              label="目标表名模板"
              density="comfortable"
              variant="outlined"
              hint="支持 ${table}（源表名）与 ${db}（源库名）占位符"
              persistent-hint
              placeholder="${table}"
              autocomplete="off"
              spellcheck="false"
            />
          </div>
          <v-alert
            v-if="schemaManagementMode === 'EXTERNAL'"
            type="info"
            variant="tonal"
            density="comfortable"
            class="mt-3"
          >
            提交前会逐表读取目标元数据并要求确认字段映射；目标表不存在或目标结构不兼容时不会保存。
          </v-alert>
        </section>

        <section class="stage-panel">
          <div class="stage-panel__header">
            <div>
              <div class="stage-panel__title">分区与存储</div>
              <div class="stage-panel__caption">
                {{
                  family === 'hdfs'
                    ? '支持多个分区键，其中最多一个业务日期分区；其余使用固定值。'
                    : '配置快照字段与目标表主键策略。'
                }}
              </div>
            </div>
          </div>
          <v-row v-if="family === 'hdfs'" density="comfortable">
            <v-col cols="12">
              <div class="binding-head">
                <span>分区绑定</span>
                <div class="binding-head__actions">
                  <v-select
                    v-model="options.hdfs.partitionCreationMode"
                    :items="[
                      { title: '平台创建', value: 'PLATFORM' },
                      { title: '外部创建', value: 'EXTERNAL' },
                    ]"
                    label="分区创建"
                    density="compact"
                    variant="outlined"
                    hide-details
                    class="binding-mode"
                  />
                  <v-btn
                    size="small"
                    variant="tonal"
                    color="primary"
                    prepend-icon="mdi-plus"
                    @click="addPartitionBinding"
                  >
                    增加分区键
                  </v-btn>
                </div>
              </div>
              <div v-if="!partitionBindings.length" class="binding-empty">
                未配置分区键，目标表将按非分区表校验。
              </div>
              <v-row v-for="(binding, index) in partitionBindings" :key="index" density="compact">
                <v-col cols="12" md="4">
                  <v-text-field
                    v-model="binding.name"
                    label="目标分区键"
                    density="comfortable"
                    variant="outlined"
                    :rules="[rules.required]"
                    hide-details="auto"
                    autocomplete="off"
                    spellcheck="false"
                  />
                </v-col>
                <v-col cols="12" md="3">
                  <v-select
                    v-model="binding.valueType"
                    :items="valueTypeItems(index)"
                    label="取值方式"
                    density="comfortable"
                    variant="outlined"
                    hide-details
                  />
                </v-col>
                <v-col cols="12" md="4">
                  <v-text-field
                    v-if="binding.valueType === 'BIZ_DATE'"
                    v-model="binding.format"
                    label="日期格式"
                    density="comfortable"
                    variant="outlined"
                    :rules="[rules.required]"
                    hide-details="auto"
                    autocomplete="off"
                    spellcheck="false"
                  />
                  <v-text-field
                    v-else
                    v-model="binding.value"
                    label="固定值"
                    density="comfortable"
                    variant="outlined"
                    :rules="[rules.required]"
                    hide-details="auto"
                    autocomplete="off"
                    spellcheck="false"
                  />
                </v-col>
                <v-col cols="12" md="1">
                  <v-btn
                    icon="mdi-delete-outline"
                    variant="text"
                    color="error"
                    aria-label="删除分区绑定"
                    @click="partitionBindings.splice(index, 1)"
                  />
                </v-col>
              </v-row>
            </v-col>
            <v-col cols="12" md="6">
              <v-combobox
                v-model="options.hdfs.storageFormat"
                :items="storageFormats"
                label="存储格式"
                density="comfortable"
                variant="outlined"
                hide-details="auto"
              />
            </v-col>
            <v-col cols="12" md="6">
              <v-combobox
                v-model="options.hdfs.compressFormat"
                :items="compressFormats"
                label="压缩格式"
                density="comfortable"
                variant="outlined"
                hide-details="auto"
              />
            </v-col>
          </v-row>
          <v-row v-else density="comfortable">
            <v-col cols="12" md="6">
              <v-text-field
                v-model="options.rdbms.businessDateColumn"
                label="快照业务日期列"
                density="comfortable"
                variant="outlined"
                hint="RDBMS 普通快照字段，填充后每行写入本次业务日期；它不是数据库分区字段，也不参与主键"
                persistent-hint
              />
            </v-col>
            <v-col cols="12" md="6">
              <v-switch
                v-model="options.rdbms.createPrimaryKey"
                inset
                label="按源端主键在目标表建主键"
                :color="options.rdbms.createPrimaryKey ? 'primary' : undefined"
                hide-details
                class="config-switch"
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

          <v-alert
            v-if="targetNameIssue"
            type="warning"
            variant="tonal"
            density="comfortable"
            class="preview-warning"
          >
            {{ targetNameIssue }}。请调整目标表名模板，确保每张源表映射到唯一目标表。
          </v-alert>

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
                <tr
                  v-for="row in previewRows"
                  :key="row.sourceTable"
                  :class="{
                    'preview-row--duplicate': duplicateSet.has(row.targetTable.toLowerCase()),
                  }"
                >
                  <td class="src-col">{{ row.sourceTable }}</td>
                  <td class="arrow-col">→</td>
                  <td class="dst-col">
                    {{ row.targetDb }}<span class="sep">.</span>{{ row.targetTable }}
                  </td>
                </tr>
                <tr v-if="selectedCnt > PREVIEW_LIMIT">
                  <td colspan="3" class="more-row">
                    还有 {{ selectedCnt - PREVIEW_LIMIT }} 张表，将在下一步逐表确认
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
</template>

<script setup lang="ts">
  import { computed } from 'vue';
  import { targetFamily } from '@/utils/target-options';
  import type { EtlTableView, EtlTargetOptions, HdfsPartitionBinding } from '@/types/database';

  const props = defineProps<{
    targets: { label: string; value: number; targetType: string }[];
    storageFormats: string[];
    compressFormats: string[];
    duplicateTargetNames: string[];
    targetNameIssue: string;
  }>();

  const targetId = defineModel<number | null>('targetId');
  const targetDb = defineModel<string>('targetDb');
  const targetTableTemplate = defineModel<string>('targetTableTemplate');
  const schemaManagementMode = defineModel<'MANAGED' | 'EXTERNAL'>('schemaManagementMode', {
    required: true,
  });
  const options = defineModel<EtlTargetOptions>('options', { required: true });
  const selectedTables = defineModel<EtlTableView[]>('selected', { required: true });

  const selectedCnt = computed(() => selectedTables.value.length);
  const duplicateSet = computed(
    () => new Set(props.duplicateTargetNames.map(name => name.toLowerCase()))
  );

  const family = computed(() => {
    const picked = props.targets.find(t => t.value === targetId.value);
    return targetFamily(picked?.targetType);
  });

  const partitionBindings = computed(() => options.value.hdfs.partitionBindings || []);

  // Why: the target contract rejects more than one BIZ_DATE partition, so prevent building it here.
  const valueTypeItems = (index: number) => [
    {
      title: '业务日期',
      value: 'BIZ_DATE',
      props: {
        disabled: partitionBindings.value.some(
          (binding, other) => other !== index && binding.valueType === 'BIZ_DATE'
        ),
      },
    },
    { title: '固定值', value: 'CONSTANT' },
  ];

  const addPartitionBinding = () => {
    if (!options.value.hdfs.partitionBindings) {
      options.value.hdfs.partitionBindings = [];
    }
    const hasBusinessDate = partitionBindings.value.some(
      binding => binding.valueType === 'BIZ_DATE'
    );
    partitionBindings.value.push({
      name: '',
      valueType: hasBusinessDate ? 'CONSTANT' : 'BIZ_DATE',
      value: '',
      format: hasBusinessDate ? '' : 'yyyyMMdd',
    } as HdfsPartitionBinding);
  };

  const rules = {
    required: (value: any) => !!value || '此字段为必填项',
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
</script>

<style lang="scss" scoped>
  @use './batch-step-shared' as *;

  .binding-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    margin-bottom: 10px;
    font-weight: 600;
  }

  .binding-head__actions {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 8px;
  }

  .binding-mode {
    width: 150px;
  }

  .binding-empty {
    margin-bottom: 12px;
    color: rgba(var(--v-theme-on-surface), 0.62);
    font-size: 0.84rem;
  }

  .config-grid {
    display: grid;
    grid-template-columns: minmax(0, 1.45fr) minmax(320px, 0.8fr);
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

  .target-field-grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    align-items: start;
    gap: 16px;
  }

  .target-field {
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

  .preview-warning {
    margin: 12px 18px 0;
  }

  .preview-row--duplicate .dst-col {
    color: rgb(var(--v-theme-error));
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
    font-family: 'SF Mono', 'Monaco', 'Inconsolata', 'Roboto Mono', 'Consolas', 'Courier New',
      monospace;
  }

  .arrow-col {
    width: 24px;
    text-align: center;
    color: rgba(var(--v-theme-on-surface), 0.34);
  }

  .dst-col {
    font-family: 'SF Mono', 'Monaco', 'Inconsolata', 'Roboto Mono', 'Consolas', 'Courier New',
      monospace;
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

  @media (max-width: 760px) {
    .target-field-grid {
      grid-template-columns: 1fr;
    }
  }
</style>

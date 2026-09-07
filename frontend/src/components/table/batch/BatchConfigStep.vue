<template>
  <div class="step-layout step-layout--config">
    <div class="step-hero">
      <div class="step-kicker">Step 3</div>
      <div class="step-title">配置目标端并检查预览</div>
    </div>

    <v-alert
      type="info"
      variant="tonal"
      density="comfortable"
      icon="mdi-table-multiple"
      class="step-alert"
    >
      共 <strong>{{ selectedCnt }}</strong> 张表待添加
      <template v-if="targetDb">，目标库：<strong>{{ targetDb }}</strong></template>
    </v-alert>

    <div class="config-grid">
      <div class="config-main">
        <section class="stage-panel">
          <div class="stage-panel__header">
            <div>
              <div class="stage-panel__title">目标配置</div>
            </div>
          </div>
          <v-row density="comfortable">
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
            </div>
          </div>
          <v-row density="comfortable">
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

    <v-dialog v-model="showPartitionInfoDialog" max-width="700px">
      <v-card>
        <v-card-text>
          <v-row>
            <v-col cols="6">
              <v-card variant="outlined" class="pa-3">
                <h5 class="text-subtitle-1 text-success mb-2">
                  <v-icon class="me-1" color="success">mdi-folder-table</v-icon>分区表
                </h5>
                <p class="text-body-2 mb-2"><strong>分区字段名:</strong> 有值(如: logdate、dt)</p>
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
            如果分区字段名为空，系统将为所有选中的表创建非分区表。
          </v-alert>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn color="primary" @click="showPartitionInfoDialog = false">知道了</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>

<script setup lang="ts">
  import { computed, ref } from 'vue';
  import dayjs from 'dayjs';
  import { PARTITION_FORMATS } from '@/utils';
  import type { EtlTableView } from '@/types/database';

  defineProps<{
    targetOptions: { label: string; value: number }[];
    storageFormats: string[];
    compressFormats: string[];
  }>();

  const targetId = defineModel<number | null>('targetId');
  const targetDb = defineModel<string>('targetDb');
  const targetTableTemplate = defineModel<string>('targetTableTemplate');
  const partName = defineModel<string>('partName');
  const partFormat = defineModel<string>('partFormat');
  const storageFormat = defineModel<string>('storageFormat');
  const compressFormat = defineModel<string>('compressFormat');
  const selectedTables = defineModel<EtlTableView[]>('selected', { required: true });

  const selectedCnt = computed(() => selectedTables.value.length);

  const showPartitionInfoDialog = ref(false);
  const showPartitionInfo = () => {
    showPartitionInfoDialog.value = true;
  };

  const partitionFormatExample = computed(() => {
    if (!partFormat.value) return '';
    return dayjs('2025-03-12').format(partFormat.value.replace(/y/g, 'Y').replace(/d/g, 'D'));
  });

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
</style>

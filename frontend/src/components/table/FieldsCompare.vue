<template>
  <v-card flat class="ds-card table-panel fields-compare-panel">
    <v-card-text class="table-panel__content">
      <div class="panel-head">
        <div>
          <div class="panel-title-row">
            <div class="panel-title">字段映射与目标契约</div>
            <v-chip size="small" variant="tonal" color="primary">
              {{ mappings.length }} 个映射
            </v-chip>
            <v-chip v-if="contractReport" size="small" variant="tonal" :color="contractColor">
              {{ contractLabel }}
            </v-chip>
            <v-chip size="small" variant="tonal" :color="editing ? 'warning' : undefined">
              {{ editing ? '编辑中' : '只读' }}
            </v-chip>
          </div>
          <div class="panel-subtitle">
            源字段通过显式映射写入目标字段。外部管理的目标表只校验结构，不执行表或字段 DDL。
          </div>
        </div>
        <div class="panel-actions">
          <v-btn
            variant="tonal"
            color="primary"
            prepend-icon="mdi-shield-search"
            :loading="checking"
            @click="checkContract"
          >
            检查目标表
          </v-btn>
          <template v-if="editing">
            <v-btn variant="text" :disabled="saving" @click="cancelEdit">取消</v-btn>
            <v-btn
              color="primary"
              prepend-icon="mdi-content-save-outline"
              :loading="saving"
              :disabled="!dirty"
              @click="saveMappings"
            >
              保存映射
            </v-btn>
          </template>
          <v-btn
            v-else
            color="primary"
            variant="flat"
            prepend-icon="mdi-pencil-outline"
            @click="beginEdit"
          >
            编辑映射
          </v-btn>
        </div>
      </div>

      <v-alert
        v-if="contractReport?.findings.length"
        :type="contractReport.status === 'ERROR' ? 'error' : 'warning'"
        variant="tonal"
        density="comfortable"
      >
        <div v-for="finding in contractReport.findings" :key="finding.code + finding.column">
          {{ finding.message }}
        </div>
      </v-alert>

      <div v-if="loading" class="panel-state">
        <v-progress-circular indeterminate color="primary" size="42" width="4" />
        <div class="panel-state__title">正在加载字段映射</div>
      </div>

      <div v-else-if="error" class="panel-state panel-state--error">
        <v-icon size="44" color="error">mdi-alert-circle-outline</v-icon>
        <div class="panel-state__title">字段映射加载失败</div>
        <v-alert type="error" variant="tonal" class="mt-3">{{ error }}</v-alert>
      </div>

      <div v-else-if="mappings.length" class="compare-table-shell ds-table-wrap">
        <v-data-table
          :headers="headers"
          :items="mappings"
          density="comfortable"
          class="compare-table"
          no-data-text="无字段映射"
          :items-per-page="-1"
          hide-default-footer
        >
          <template #item.ordinalNo="{ item }">
            <div class="order-cell">#{{ item.ordinalNo }}</div>
          </template>

          <template #item.sourceColumnName="{ item }">
            <div class="field-cell">
              <div class="field-name">{{ item.sourceColumnName }}</div>
              <div class="field-comment">{{ item.comment || '无字段备注' }}</div>
              <v-chip
                v-if="!item.sourceActive"
                size="x-small"
                color="warning"
                variant="tonal"
                class="mt-1"
              >
                源字段已删除，写入 NULL
              </v-chip>
            </div>
          </template>

          <template #item.targetColumnName="{ item }">
            <v-combobox
              v-if="editing"
              v-model="item.targetColumnName"
              :items="targetColumnNames"
              density="compact"
              variant="outlined"
              hide-details
              aria-label="目标字段"
              no-data-text="请先检查目标表或直接输入字段名"
            />
            <span v-else class="target-field-value" :title="item.targetColumnName || ''">
              {{ item.targetColumnName || '-' }}
            </span>
          </template>

          <template #item.sourceType="{ item }">
            <span class="type-pill type-pill--source">{{ item.sourceType || '-' }}</span>
          </template>

          <template #item.targetType="{ item }">
            <span class="type-pill type-pill--target">{{ targetTypeOf(item) || '-' }}</span>
          </template>

          <template #item.status="{ item }">
            <div class="d-flex align-center ga-2">
              <v-chip
                size="small"
                :color="rowStatus(item).color"
                :variant="rowStatus(item).color === 'success' ? 'outlined' : 'tonal'"
              >
                {{ rowStatus(item).label }}
              </v-chip>
              <v-btn
                v-if="editing && !item.sourceActive"
                icon="mdi-link-off"
                size="x-small"
                variant="text"
                color="error"
                aria-label="移除已删除源字段的映射"
                @click="removeInactiveMapping(item)"
              />
            </div>
          </template>
        </v-data-table>
      </div>

      <div v-else class="panel-state">
        <v-icon size="48" class="panel-state__icon">mdi-table-column-remove</v-icon>
        <div class="panel-state__title">暂无字段映射</div>
        <div class="panel-state__desc">请先刷新源表字段，或切换到外部管理后预检目标表。</div>
      </div>
    </v-card-text>
  </v-card>
</template>

<script setup lang="ts">
  import { computed, onMounted, ref } from 'vue';
  import { useRoute } from 'vue-router';
  import type { DataTableHeader } from 'vuetify';
  import { notify } from '@/stores/notifier';
  import tableService from '@/service/table-service';
  import type { ColumnMapping, TargetContractReport, TargetTableColumn } from '@/types/database';

  const route = useRoute();
  const tid = Number((route.params as Record<string, string>).tid);

  const mappings = ref<ColumnMapping[]>([]);
  const contractReport = ref<TargetContractReport | null>(null);
  const loading = ref(false);
  const checking = ref(false);
  const saving = ref(false);
  const editing = ref(false);
  const baselineMappings = ref<ColumnMapping[]>([]);
  const baselineSignature = ref('');
  const error = ref('');

  const cloneMappings = (value: ColumnMapping[]): ColumnMapping[] =>
    JSON.parse(JSON.stringify(value)) as ColumnMapping[];
  const mappingSignature = (value: ColumnMapping[]) =>
    JSON.stringify(
      value.map(mapping => ({
        sourceColumnName: mapping.sourceColumnName,
        targetColumnName: mapping.targetColumnName,
        ordinalNo: mapping.ordinalNo,
      }))
    );
  const dirty = computed(
    () => editing.value && mappingSignature(mappings.value) !== baselineSignature.value
  );

  const targetColumns = computed(() => contractReport.value?.target.columns || []);
  const targetColumnNames = computed(() => targetColumns.value.map(column => column.name));
  const contractColor = computed(() => {
    if (contractReport.value?.status === 'ERROR') return 'error';
    if (contractReport.value?.status === 'WARNING') return 'warning';
    return 'success';
  });
  const contractLabel = computed(() => {
    if (contractReport.value?.status === 'ERROR') return '目标契约不兼容';
    if (contractReport.value?.status === 'WARNING') return '目标契约有告警';
    return '目标契约通过';
  });

  const loadMappings = async () => {
    mappings.value = await tableService.fetchColumnMappings(tid);
    baselineMappings.value = cloneMappings(mappings.value);
    baselineSignature.value = mappingSignature(mappings.value);
    editing.value = false;
  };

  const beginEdit = () => {
    baselineMappings.value = cloneMappings(mappings.value);
    baselineSignature.value = mappingSignature(mappings.value);
    editing.value = true;
  };

  const cancelEdit = () => {
    mappings.value = cloneMappings(baselineMappings.value);
    editing.value = false;
  };

  const checkContract = async () => {
    checking.value = true;
    try {
      contractReport.value = await tableService.checkTargetContract(tid);
      if (contractReport.value.status === 'ERROR') {
        notify('目标表结构不兼容，采集将被阻断', 'error');
      } else {
        notify(
          contractLabel.value,
          contractReport.value.status === 'WARNING' ? 'warning' : 'success'
        );
      }
    } catch (err) {
      notify(`目标表检查失败: ${(err as Error).message || String(err)}`, 'error');
    } finally {
      checking.value = false;
    }
  };

  const saveMappings = async () => {
    if (!dirty.value) return;
    if (mappings.value.some(mapping => !mapping.targetColumnName?.trim())) {
      notify('所有源字段都必须指定目标字段', 'warning');
      return;
    }
    saving.value = true;
    try {
      mappings.value = await tableService.saveColumnMappings(
        tid,
        mappings.value.map((mapping, index) => ({
          sourceColumnName: mapping.sourceColumnName,
          targetColumnName: mapping.targetColumnName.trim(),
          ordinalNo: index + 1,
        }))
      );
      baselineMappings.value = cloneMappings(mappings.value);
      baselineSignature.value = mappingSignature(mappings.value);
      editing.value = false;
      await checkContract();
      notify('字段映射已保存并重新生成采集任务', 'success');
    } catch (err) {
      notify(`字段映射保存失败: ${(err as Error).message || String(err)}`, 'error');
    } finally {
      saving.value = false;
    }
  };

  const targetColumnOf = (mapping: ColumnMapping): TargetTableColumn | undefined =>
    targetColumns.value.find(
      column => column.name.toLowerCase() === mapping.targetColumnName?.trim().toLowerCase()
    );

  const targetTypeOf = (mapping: ColumnMapping) => targetColumnOf(mapping)?.type;

  const rowStatus = (mapping: ColumnMapping) => {
    if (!mapping.sourceActive) return { label: '源字段已删除', color: 'warning' };
    if (contractReport.value && !targetColumnOf(mapping)) {
      return { label: '目标字段缺失', color: 'error' };
    }
    return { label: '映射有效', color: 'success' };
  };

  const removeInactiveMapping = (mapping: ColumnMapping) => {
    mappings.value = mappings.value.filter(item => item !== mapping);
  };

  const headers: DataTableHeader[] = [
    { title: '顺序', key: 'ordinalNo', width: '7%' },
    { title: '源字段', key: 'sourceColumnName', width: '27%' },
    { title: '目标字段', key: 'targetColumnName', width: '28%' },
    { title: '源类型', key: 'sourceType', width: '14%' },
    { title: '实际目标类型', key: 'targetType', width: '14%' },
    { title: '状态', key: 'status', width: '10%' },
  ];

  onMounted(async () => {
    loading.value = true;
    error.value = '';
    try {
      await loadMappings();
      await checkContract();
    } catch (err) {
      error.value = (err as Error).message || String(err);
    } finally {
      loading.value = false;
    }
  });
</script>

<style scoped>
  .table-panel__content {
    display: flex;
    flex-direction: column;
    gap: 16px;
    padding: 18px;
  }

  .panel-head {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 16px;
    flex-wrap: wrap;
  }

  .panel-title-row,
  .panel-actions {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
  }

  .panel-title {
    font-size: 1rem;
    font-weight: 700;
  }

  .panel-subtitle {
    margin-top: 6px;
    max-width: 760px;
    color: rgba(var(--v-theme-on-surface), 0.68);
    line-height: 1.6;
  }

  .compare-table-shell {
    overflow-x: auto;
  }

  .field-cell {
    padding: 6px 0;
  }

  .field-name {
    font-weight: 600;
  }

  .field-comment {
    color: rgba(var(--v-theme-on-surface), 0.58);
    font-size: 0.75rem;
  }

  .type-pill {
    display: inline-flex;
    padding: 3px 8px;
    border-radius: 999px;
    font-size: 0.75rem;
  }

  .type-pill--source {
    background: rgba(var(--v-theme-primary), 0.1);
  }

  .type-pill--target {
    background: rgba(var(--v-theme-secondary), 0.12);
  }

  .order-cell {
    color: rgba(var(--v-theme-on-surface), 0.56);
    font-variant-numeric: tabular-nums;
  }

  .target-field-value {
    display: block;
    padding: 5px 0;
    overflow: hidden;
    font-family: 'SF Mono', 'Monaco', 'Consolas', monospace;
    font-size: 0.78rem;
    font-weight: 600;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .panel-state {
    display: flex;
    min-height: 180px;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 8px;
  }

  .panel-state--error {
    color: rgb(var(--v-theme-error));
  }

  .panel-state__title {
    font-weight: 600;
  }

  .panel-state__desc {
    color: rgba(var(--v-theme-on-surface), 0.62);
  }
</style>

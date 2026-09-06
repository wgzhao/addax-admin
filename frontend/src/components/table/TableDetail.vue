<template>
  <v-form ref="formRef" fast-fail tag="form" class="table-detail-shell" @submit.prevent="saveOds">
    <HeroSummaryCard v-model:table="table" :target-options="targetOptions" />

    <v-row density="comfortable" class="detail-grid">
      <v-col cols="12" lg="7" class="panel-stack">
        <BasicFieldsCard v-model:table="table" @show-placeholder-help="showPlaceholderInfo = true" />
        <MappingScheduleCard v-model:table="table" :target-options="targetOptions" />
        <ExtractStorageCard v-model:table="table" @show-filter-help="showFilterRuleInfo = true" />
      </v-col>

      <v-col cols="12" lg="5" class="panel-stack">
        <PluginConfigCard
          v-model:reader-plugin-config-text="readerPluginConfigText"
          v-model:writer-plugin-config-text="writerPluginConfigText"
        />
        <SnapshotRemarkCard
          v-model:table="table"
          @show-placeholder-help="showPlaceholderInfo = true"
          @show-filter-help="showFilterRuleInfo = true"
        />
      </v-col>
    </v-row>

    <v-card flat class="ds-card action-card">
      <v-card-actions class="action-bar">
        <v-spacer />
        <v-btn color="primary" variant="flat" @click="saveOds">保存更改</v-btn>
      </v-card-actions>
    </v-card>

    <DynamicNameHelpDialog v-model="showPlaceholderInfo" />
    <FilterRuleHelpDialog v-model="showFilterRuleInfo" />
  </v-form>
</template>

<script setup lang="ts">
  import { computed, onMounted, ref } from 'vue';
  import { useRoute } from 'vue-router';
  import { notify } from '@/stores/notifier';
  import tableService from '@/service/table-service';
  import targetService from '@/service/target-service';
  import { VEtlWithSource, EtlTable, EtlTarget } from '@/types/database';
  import { toJsonText, parseJsonObjectOrNull } from '@/utils/json-plugin';
  import HeroSummaryCard from './detail/HeroSummaryCard.vue';
  import BasicFieldsCard from './detail/BasicFieldsCard.vue';
  import MappingScheduleCard from './detail/MappingScheduleCard.vue';
  import ExtractStorageCard from './detail/ExtractStorageCard.vue';
  import PluginConfigCard from './detail/PluginConfigCard.vue';
  import SnapshotRemarkCard from './detail/SnapshotRemarkCard.vue';
  import DynamicNameHelpDialog from './detail/DynamicNameHelpDialog.vue';
  import FilterRuleHelpDialog from './detail/FilterRuleHelpDialog.vue';

  const route = useRoute();
  // cast: unplugin-vue-router does not type custom route-block path params
  const tid = computed(() => Number((route.params as Record<string, string>).tid));

  const targetOptions = ref<{ label: string; value: number }[]>([]);

  const table = ref<VEtlWithSource>({} as VEtlWithSource);
  const readerPluginConfigText = ref('');
  const writerPluginConfigText = ref('');
  const showPlaceholderInfo = ref(false);
  const showFilterRuleInfo = ref(false);

  const syncTableData = (newTable: VEtlWithSource) => {
    table.value = { ...newTable };
    readerPluginConfigText.value = toJsonText(newTable.readerPluginConfig);
    writerPluginConfigText.value = toJsonText(newTable.writerPluginConfig);
  };

  onMounted(async () => {
    if (!tid.value) return;
    try {
      const details = await tableService.fetchTableDetail(tid.value);
      syncTableData(details);
    } catch (e) {
      notify('加载采集表详情失败: ' + ((e as Error)?.message || String(e)), 'error');
    }

    try {
      const targets = await targetService.list(true);
      targetOptions.value = targets
        .filter(t => t.id !== undefined)
        .map((t: EtlTarget) => ({
          label: `${t.name} (${t.targetType})`,
          value: t.id as number,
        }));
    } catch {
      notify('加载目标端列表失败', 'warning');
    }
  });

  const saveOds = async () => {
    if (typeof (formRef?.value as any)?.validate === 'function') {
      const valid = await (formRef?.value as any).validate();
      if (!valid.valid) {
        notify('请修正表单错误', 'error');
        return;
      }
    }

    let readerPluginConfig: Record<string, unknown> | null;
    let writerPluginConfig: Record<string, unknown> | null;
    try {
      readerPluginConfig = parseJsonObjectOrNull(readerPluginConfigText.value, '读取插件配置');
      writerPluginConfig = parseJsonObjectOrNull(writerPluginConfigText.value, '写入插件配置');
    } catch (e) {
      notify((e as Error).message, 'error');
      return;
    }

    table.value.readerPluginConfig = readerPluginConfig;
    table.value.writerPluginConfig = writerPluginConfig;

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
      duration: table.value.duration,
      writeMode: table.value.writeMode || 'overwrite',
      startAt: table.value.startAt || null,
      targetId: table.value.targetId ?? null,
      readerPluginConfig,
      writerPluginConfig,
      createdAt: table.value.createdAt,
      updatedAt: table.value.updatedAt,
    };

    try {
      const updatedRecord = await tableService.save(etlTableData as EtlTable);
      notify('保存成功', 'success');
      syncTableData({ ...table.value, ...updatedRecord } as VEtlWithSource);
    } catch (err) {
      notify('保存失败: ' + ((err as Error)?.message || String(err)), 'error');
    }
  };

  const formRef = ref(null);
</script>

<style scoped>
  .table-detail-shell {
    display: flex;
    flex-direction: column;
    gap: 14px;
  }

  .panel-stack {
    display: flex;
    flex-direction: column;
    gap: 12px;
  }

  .action-card {
    position: sticky;
    bottom: 0;
    z-index: 2;
  }

  .action-bar {
    padding: 14px 18px;
  }

  .action-copy {
    color: rgba(var(--v-theme-on-surface), 0.62);
  }

  @media (max-width: 760px) {
    .action-bar {
      padding: 16px;
    }
  }
</style>

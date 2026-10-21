<template>
  <v-form ref="formRef" fast-fail tag="form" class="table-detail-shell" @submit.prevent="saveOds">
    <TableDetailState
      v-if="loading || loadError"
      :loading="loading"
      :error="loadError"
      @retry="loadDetail"
    />

    <template v-else>
      <HeroSummaryCard v-model:table="table" :target-options="targetOptions" />

      <TableDetailCommandBar
        :editing="editing"
        :dirty="dirty"
        :saving="saving"
        :retired="isRetired"
        :updated-at="table.updatedAt"
        @edit="beginEdit"
        @cancel="cancelEdit"
        @save="saveOds"
      />

      <DetailOverviewCard
        v-if="!editing"
        :table="table"
        :reader-plugin-config-text="readerPluginConfigText"
        :writer-plugin-config-text="writerPluginConfigText"
        @show-placeholder-help="showPlaceholderInfo = true"
        @show-filter-help="showFilterRuleInfo = true"
      />

      <TableDetailEditForm
        v-else
        v-model:table="table"
        v-model:reader-plugin-config-text="readerPluginConfigText"
        v-model:writer-plugin-config-text="writerPluginConfigText"
        :target-options="targetOptions"
        :target-type="selectedTargetType"
        @show-placeholder-help="showPlaceholderInfo = true"
        @show-filter-help="showFilterRuleInfo = true"
      />
    </template>

    <DynamicNameHelpDialog v-model="showPlaceholderInfo" />
    <FilterRuleHelpDialog v-model="showFilterRuleInfo" />
  </v-form>
</template>

<script setup lang="ts">
  import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
  import { onBeforeRouteLeave, useRoute } from 'vue-router';
  import { notify } from '@/stores/notifier';
  import tableService from '@/service/table-service';
  import targetService from '@/service/target-service';
  import { VEtlWithSource, EtlTable, EtlTarget, EtlTargetOptions } from '@/types/database';
  import { toJsonText, parseJsonObjectOrNull } from '@/utils/json-plugin';
  import { normalizeTargetOptions, pickTargetOptions } from '@/utils/target-options';
  import HeroSummaryCard from './detail/HeroSummaryCard.vue';
  import DynamicNameHelpDialog from './detail/DynamicNameHelpDialog.vue';
  import FilterRuleHelpDialog from './detail/FilterRuleHelpDialog.vue';
  import DetailOverviewCard from './detail/DetailOverviewCard.vue';
  import TableDetailCommandBar from './detail/TableDetailCommandBar.vue';
  import TableDetailEditForm from './detail/TableDetailEditForm.vue';
  import TableDetailState from './detail/TableDetailState.vue';

  const route = useRoute();
  // cast: unplugin-vue-router does not type custom route-block path params
  const tid = computed(() => Number((route.params as Record<string, string>).tid));

  const targetOptions = ref<{ label: string; value: number; targetType: string }[]>([]);

  // Render with a normalized options bag so the target-specific fields always have an object to bind to.
  const table = ref<VEtlWithSource>({ targetOptions: normalizeTargetOptions() } as VEtlWithSource);
  // The bag as stored on the row, so a save keeps the section of the other target family untouched.
  const storedOptions = ref<Partial<EtlTargetOptions>>({});
  const readerPluginConfigText = ref('');
  const writerPluginConfigText = ref('');
  const showPlaceholderInfo = ref(false);
  const showFilterRuleInfo = ref(false);
  const loading = ref(true);
  const loadError = ref('');
  const editing = ref(false);
  const dirty = ref(false);
  const saving = ref(false);
  const cleanSignature = ref('');
  const originalTable = ref<VEtlWithSource | null>(null);

  // The loaded value is a Vue proxy; a JSON round-trip provides a detached editable snapshot.
  const cloneTable = (value: VEtlWithSource) => JSON.parse(JSON.stringify(value)) as VEtlWithSource;

  const currentSignature = () =>
    JSON.stringify({
      table: table.value,
      readerPluginConfigText: readerPluginConfigText.value,
      writerPluginConfigText: writerPluginConfigText.value,
    });

  const syncTableData = (newTable: VEtlWithSource) => {
    storedOptions.value = newTable.targetOptions ?? {};
    table.value = { ...newTable, targetOptions: normalizeTargetOptions(newTable.targetOptions) };
    readerPluginConfigText.value = toJsonText(newTable.readerPluginConfig);
    writerPluginConfigText.value = toJsonText(newTable.writerPluginConfig);
  };

  const markClean = () => {
    cleanSignature.value = currentSignature();
    dirty.value = false;
  };

  const beginEdit = () => {
    if (isRetired.value) return;
    originalTable.value = cloneTable(table.value);
    markClean();
    editing.value = true;
  };

  const cancelEdit = () => {
    if (originalTable.value) {
      syncTableData(cloneTable(originalTable.value));
    }
    editing.value = false;
    markClean();
  };

  const canLeave = () => !dirty.value || window.confirm('还有未保存的修改，确定要离开当前编辑吗？');

  watch(
    [table, readerPluginConfigText, writerPluginConfigText],
    () => {
      dirty.value = editing.value && currentSignature() !== cleanSignature.value;
    },
    { deep: true }
  );

  // The card re-renders on an unsaved target change, so prefer the picked endpoint over the persisted type.
  const selectedTargetType = computed(() => {
    const picked = targetOptions.value.find(t => t.value === table.value.targetId);
    return picked?.targetType || table.value.targetType;
  });

  const isRetired = computed(() => !!table.value.retiredAt);

  const loadDetail = async () => {
    if (!tid.value) {
      loading.value = false;
      loadError.value = '缺少采集表编号';
      return;
    }

    loading.value = true;
    loadError.value = '';
    editing.value = false;

    try {
      const details = await tableService.fetchTableDetail(tid.value);
      originalTable.value = cloneTable(details);
      syncTableData(details);
      markClean();
    } catch (e) {
      loadError.value = (e as Error)?.message || String(e);
    } finally {
      loading.value = false;
    }

    try {
      const targets = await targetService.list(true);
      targetOptions.value = targets
        .filter(t => t.id !== undefined)
        .map((t: EtlTarget) => ({
          label: `${t.name} (${t.targetType})`,
          value: t.id as number,
          targetType: t.targetType,
        }));
    } catch {
      notify('加载目标端列表失败', 'warning');
    }
  };

  const onBeforeUnload = (event: BeforeUnloadEvent) => {
    if (!dirty.value) return;
    event.preventDefault();
    event.returnValue = '';
  };

  onMounted(() => {
    loadDetail();
    window.addEventListener('beforeunload', onBeforeUnload);
  });

  onBeforeUnmount(() => {
    window.removeEventListener('beforeunload', onBeforeUnload);
  });

  onBeforeRouteLeave(() => canLeave());

  defineExpose({ canLeave });

  const saveOds = async () => {
    if (!editing.value || isRetired.value) return;

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
      // Merge over the stored bag: switching target families must not drop the other family's settings.
      targetOptions: {
        ...storedOptions.value,
        ...pickTargetOptions(table.value.targetOptions, selectedTargetType.value),
      },
      splitPk: table.value.splitPk,
      autoPk: table.value.autoPk,
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

    saving.value = true;
    try {
      const updatedRecord = await tableService.save(etlTableData as EtlTable);
      notify('保存成功', 'success');
      syncTableData({ ...table.value, ...updatedRecord } as VEtlWithSource);
      originalTable.value = cloneTable(table.value);
      editing.value = false;
      markClean();
    } catch (err) {
      notify('保存失败: ' + ((err as Error)?.message || String(err)), 'error');
    } finally {
      saving.value = false;
    }
  };

  const formRef = ref(null);
</script>

<style scoped>
  .table-detail-shell {
    display: flex;
    flex-direction: column;
    gap: 12px;
  }
</style>

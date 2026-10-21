<template>
  <v-row density="comfortable" class="detail-grid">
    <v-col cols="12" md="7" class="panel-stack">
      <BasicFieldsCard
        v-model:table="table"
        @show-placeholder-help="$emit('show-placeholder-help')"
      />
      <MappingScheduleCard v-model:table="table" :target-options="targetOptions" />
      <ExtractStorageCard
        v-model:table="table"
        :target-type="targetType"
        @show-filter-help="$emit('show-filter-help')"
      />
    </v-col>

    <v-col cols="12" md="5" class="panel-stack">
      <PluginConfigCard
        v-model:reader-plugin-config-text="readerPluginConfigText"
        v-model:writer-plugin-config-text="writerPluginConfigText"
      />
      <SnapshotRemarkCard
        v-model:table="table"
        @show-placeholder-help="$emit('show-placeholder-help')"
        @show-filter-help="$emit('show-filter-help')"
      />
    </v-col>
  </v-row>
</template>

<script setup lang="ts">
  import type { VEtlWithSource } from '@/types/database';
  import BasicFieldsCard from './BasicFieldsCard.vue';
  import MappingScheduleCard from './MappingScheduleCard.vue';
  import ExtractStorageCard from './ExtractStorageCard.vue';
  import PluginConfigCard from './PluginConfigCard.vue';
  import SnapshotRemarkCard from './SnapshotRemarkCard.vue';

  defineProps<{
    targetOptions: { label: string; value: number; targetType: string }[];
    targetType?: string | null;
  }>();

  defineEmits(['show-placeholder-help', 'show-filter-help']);

  const table = defineModel<VEtlWithSource>('table', { required: true });
  const readerPluginConfigText = defineModel<string>('readerPluginConfigText', { required: true });
  const writerPluginConfigText = defineModel<string>('writerPluginConfigText', { required: true });
</script>

<style scoped>
  .panel-stack {
    display: flex;
    flex-direction: column;
    gap: 12px;
  }
</style>

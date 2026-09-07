<template>
  <v-card flat class="ds-card section-card">
    <v-card-text class="section-body">
      <div class="section-head">
        <div>
          <div class="section-title">目标与调度</div>
        </div>
      </div>

      <v-row density="comfortable">
        <v-col cols="12" md="6">
          <div class="field-block">
            <div class="field-label">目标端</div>
            <v-select
              v-model="table.targetId"
              variant="outlined"
              density="comfortable"
              :items="targetOptions"
              item-title="label"
              item-value="value"
              placeholder="请选择目标端"
              clearable
              :rules="[required]"
              hide-details="auto"
            />
          </div>
        </v-col>

        <v-col cols="12" md="6">
          <div class="field-block">
            <div class="field-label">写入模式</div>
            <v-select
              v-model="table.writeMode"
              variant="outlined"
              density="comfortable"
              :items="writeModeOptions"
              item-title="label"
              item-value="value"
              placeholder="请选择写入模式"
              hide-details="auto"
            />
          </div>
        </v-col>

        <v-col cols="12" md="6">
          <div class="field-block">
            <div class="field-label">目标库</div>
            <v-text-field
              v-model="table.targetDb"
              variant="outlined"
              density="comfortable"
              placeholder="请输入目标库"
              hide-details="auto"
            />
          </div>
        </v-col>

        <v-col cols="12" md="6">
          <div class="field-block">
            <div class="field-label">目标表</div>
            <v-text-field
              v-model="table.targetTable"
              variant="outlined"
              density="comfortable"
              placeholder="请输入目标表"
              hide-details="auto"
            />
          </div>
        </v-col>

        <v-col cols="12" md="6">
          <div class="field-block">
            <div class="field-label">调度时间 (HH:mm)</div>
            <v-text-field
              v-model="table.startAt"
              variant="outlined"
              density="comfortable"
              placeholder="为空则继承数据源"
              clearable
              persistent-hint
              :hint="`继承值：${inheritedStartAt || '-'}`"
            />
          </div>
        </v-col>

        <v-col cols="12" md="6">
          <div class="field-block">
            <div class="field-label">最大运行时 (s)</div>
            <v-text-field
              v-model="table.maxRuntime"
              variant="outlined"
              density="comfortable"
              placeholder="不填默认 2000 秒"
              hide-details="auto"
            />
          </div>
        </v-col>
      </v-row>
    </v-card-text>
  </v-card>
</template>

<script setup lang="ts">
  import { computed } from 'vue';
  import type { VEtlWithSource } from '@/types/database';
  import { inheritedStartAt as inheritedStartAtOf } from './table-labels';

  const props = defineProps<{
    targetOptions: { label: string; value: number }[];
  }>();

  // Object-level model: mutate fields only, never assign the whole object
  const table = defineModel<VEtlWithSource>('table', { required: true });

  const inheritedStartAt = computed(() => inheritedStartAtOf(table.value));

  const writeModeOptions = [
    { label: '覆盖 (overwrite)', value: 'overwrite' },
    { label: '追加 (append)', value: 'append' },
    { label: '无冲突追加 (nonConflict)', value: 'nonConflict' },
  ];

  const required = (v: unknown) => !!v || '此字段为必填项';
</script>

<style lang="scss" scoped>
  @use './detail-shared' as *;
</style>

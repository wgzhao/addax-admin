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
              :rules="[required]"
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
              :rules="[required]"
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
              :rules="[required]"
              hide-details="auto"
            />
          </div>
        </v-col>

        <v-col cols="12" md="6">
          <div class="field-block">
            <div class="field-label">调度时间</div>
            <v-text-field
              v-model="table.startAt"
              variant="outlined"
              density="comfortable"
              placeholder="HH:mm 或 HH:mm:ss"
              clearable
              persistent-hint
              :hint="`留空则继承采集源（当前：${inheritedStartAt || '未设置'}）`"
              :rules="[optionalTime]"
            />
          </div>
        </v-col>

        <v-col cols="12" md="6">
          <div class="field-block">
            <div class="field-label">最大运行时长</div>
            <v-text-field
              v-model.number="table.maxRuntime"
              variant="outlined"
              density="comfortable"
              type="number"
              min="1"
              suffix="秒"
              placeholder="默认 2000 秒"
              :rules="[optionalPositiveInteger]"
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

  const required = (v: unknown) => !!String(v ?? '').trim() || '此字段为必填项';

  const optionalTime = (value: unknown) => {
    if (value === null || value === undefined || value === '') return true;
    const text = String(value).trim();
    const format = /^([01]\d|2[0-3]):[0-5]\d(:[0-5]\d)?$/;
    return format.test(text) || '时间格式不正确，请使用 HH:mm 或 HH:mm:ss';
  };

  const optionalPositiveInteger = (value: unknown) => {
    if (value === null || value === undefined || value === '') return true;
    const number = Number(value);
    return (Number.isInteger(number) && number > 0) || '必须为大于 0 的整数';
  };
</script>

<style lang="scss" scoped>
  @use './detail-shared' as *;
</style>

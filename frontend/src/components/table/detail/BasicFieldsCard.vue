<template>
  <v-card flat class="ds-card section-card">
    <v-card-text class="section-body">
      <div class="section-head">
        <div>
          <div class="section-title">基础信息</div>
        </div>
      </div>

      <v-row density="comfortable">
        <v-col cols="12" md="6">
          <div class="field-block">
            <div class="field-label">源系统</div>
            <v-text-field
              v-model="table.name"
              variant="outlined"
              density="comfortable"
              placeholder="请输入源系统"
              hide-details="auto"
            />
          </div>
        </v-col>

        <v-col cols="12" md="6">
          <div class="field-block">
            <div class="field-label">采集状态</div>
            <v-select
              v-model="table.status"
              variant="outlined"
              density="comfortable"
              :items="TABLE_STATUS_OPTIONS"
              item-title="label"
              item-value="value"
              placeholder="请选择采集状态"
              hide-details="auto"
            />
          </div>
        </v-col>

        <v-col cols="12" md="6">
          <div class="field-block">
            <div class="field-label">源库</div>
            <v-text-field
              v-model="table.sourceDb"
              variant="outlined"
              density="comfortable"
              placeholder="请输入源库"
              hide-details="auto"
            />
          </div>
        </v-col>

        <v-col cols="12" md="6">
          <div class="field-block">
            <div class="field-label">剩余次数</div>
            <v-text-field
              v-model="table.retryCnt"
              variant="outlined"
              density="comfortable"
              type="number"
              placeholder="请输入剩余次数"
              :rules="[nonNegative]"
              hide-details="auto"
            />
          </div>
        </v-col>

        <v-col cols="12">
          <div class="field-block">
            <div class="field-label field-label-row">
              <span>源表</span>
              <v-btn
                size="x-small"
                variant="text"
                color="info"
                prepend-icon="mdi-information-outline"
                @click="$emit('show-placeholder-help')"
              >
                动态命名说明
              </v-btn>
            </div>
            <v-text-field
              v-model="table.sourceTable"
              variant="outlined"
              density="comfortable"
              placeholder="请输入源表"
              hide-details="auto"
            />
          </div>
        </v-col>
      </v-row>
    </v-card-text>
  </v-card>
</template>

<script setup lang="ts">
  import { TABLE_STATUS_OPTIONS } from '@/utils';
  import type { VEtlWithSource } from '@/types/database';

  defineEmits(['show-placeholder-help']);

  // Object-level model: mutate fields only, never assign the whole object
  const table = defineModel<VEtlWithSource>('table', { required: true });

  const nonNegative = (v: unknown) => {
    if (v === null || v === undefined || v === '') return true;
    const n = Number(v);
    return (!Number.isNaN(n) && n >= 0) || '必须为非负数';
  };
</script>

<style lang="scss" scoped>
  @use './detail-shared' as *;
</style>

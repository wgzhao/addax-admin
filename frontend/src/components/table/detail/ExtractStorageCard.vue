<template>
  <v-card flat class="ds-card section-card">
    <v-card-text class="section-body">
      <div class="section-head">
        <div>
          <div class="section-title">抽取与存储配置</div>
        </div>
      </div>

      <v-row density="comfortable">
        <v-col cols="12" md="12">
          <div class="field-block">
            <div class="field-label field-label-row">
              <span>过滤规则</span>
              <v-btn
                size="x-small"
                variant="text"
                color="info"
                prepend-icon="mdi-information-outline"
                @click="$emit('show-filter-help')"
              >
                规则说明
              </v-btn>
            </div>
            <v-text-field
              v-model="table.filter"
              variant="outlined"
              density="comfortable"
              placeholder="可选，例如 dt='${biz_date_dash}'"
              hide-details="auto"
            />
          </div>
        </v-col>

        <template v-if="family === 'hdfs'">
          <v-col cols="12" md="4">
            <div class="field-block">
              <div class="field-label">分区字段</div>
              <v-text-field
                v-model="table.targetOptions.hdfs.partName"
                variant="outlined"
                density="comfortable"
                placeholder="例如 dt"
                hide-details="auto"
              />
            </div>
          </v-col>

          <v-col cols="12" md="4">
            <div class="field-block">
              <div class="field-label">分区格式</div>
              <v-select
                v-model="table.targetOptions.hdfs.partFormat"
                variant="outlined"
                density="comfortable"
                :items="PARTITION_FORMATS"
                placeholder="请选择分区格式"
                hide-details="auto"
              />
            </div>
          </v-col>
        </template>

        <v-col v-else cols="12" md="4">
          <div class="field-block">
            <div class="field-label">快照业务日期列</div>
            <v-text-field
              v-model="table.targetOptions.rdbms.businessDateColumn"
              variant="outlined"
              density="comfortable"
              placeholder="例如 logdate，为空表示不承载日期"
              hide-details="auto"
            />
          </div>
        </v-col>

        <v-col cols="12" md="4">
          <div class="field-block">
            <div class="field-label">切分字段</div>
            <v-text-field
              v-model="table.splitPk"
              variant="outlined"
              density="comfortable"
              placeholder="为空则自动获取"
              hide-details="auto"
            />
          </div>
        </v-col>

        <template v-if="family === 'hdfs'">
          <v-col cols="12" md="4">
            <div class="field-block">
              <div class="field-label">存储格式</div>
              <v-select
                v-model="table.targetOptions.hdfs.storageFormat"
                variant="outlined"
                density="comfortable"
                :items="storageOptions"
                placeholder="请选择存储格式"
                hide-details="auto"
              />
            </div>
          </v-col>

          <v-col cols="12" md="4">
            <div class="field-block">
              <div class="field-label">压缩格式</div>
              <v-select
                v-model="table.targetOptions.hdfs.compressFormat"
                variant="outlined"
                density="comfortable"
                :items="compressFormats"
                placeholder="请选择压缩格式"
                hide-details="auto"
              />
            </div>
          </v-col>
        </template>

        <v-col v-else cols="12" md="4">
          <div class="field-block field-block--switch">
            <div class="field-label">目标表主键</div>
            <div class="switch-shell">
              <v-switch
                v-model="table.targetOptions.rdbms.createPrimaryKey"
                inset
                label="按源端主键建主键"
                :color="table.targetOptions.rdbms.createPrimaryKey ? 'primary' : undefined"
                hide-details
                class="mt-0"
              />
            </div>
          </div>
        </v-col>

        <v-col cols="12" md="4">
          <div class="field-block field-block--switch">
            <div class="field-label">切分策略</div>
            <div class="switch-shell">
              <v-switch
                v-model="table.autoPk"
                inset
                label="自动获取切分字段"
                :color="table.autoPk ? 'primary' : undefined"
                hide-details
                class="mt-0"
              />
            </div>
          </div>
        </v-col>
      </v-row>
    </v-card-text>
  </v-card>
</template>

<script setup lang="ts">
  import { PARTITION_FORMATS, HDFS_COMPRESS_FORMATS } from '@/utils';
  import { targetFamily } from '@/utils/target-options';
  import type { VEtlWithSource } from '@/types/database';
  import { computed } from 'vue';

  defineEmits(['show-filter-help']);

  const props = defineProps<{
    targetType?: string | null;
  }>();

  // Object-level model: mutate fields only, never assign the whole object
  const table = defineModel<VEtlWithSource>('table', { required: true });

  const storageOptions = ['orc', 'parquet', 'text'];
  const compressFormats = HDFS_COMPRESS_FORMATS || [];
  const family = computed(() => targetFamily(props.targetType || table.value.targetType));
</script>

<style lang="scss" scoped>
  @use './detail-shared' as *;
</style>

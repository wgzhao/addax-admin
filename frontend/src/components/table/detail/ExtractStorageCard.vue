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
          <v-col cols="12">
            <div class="field-block">
              <div class="field-label field-label-row">
                <span>分区绑定</span>
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
              <div v-if="!partitionBindings.length" class="empty-partitions">
                未配置分区键，目标表将按非分区表校验。
              </div>
              <v-row v-for="(binding, index) in partitionBindings" :key="index" density="compact">
                <v-col cols="12" md="3">
                  <v-text-field
                    v-model="binding.name"
                    label="目标分区键"
                    variant="outlined"
                    density="comfortable"
                    hide-details="auto"
                  />
                </v-col>
                <v-col cols="12" md="3">
                  <v-select
                    v-model="binding.valueType"
                    label="取值方式"
                    variant="outlined"
                    density="comfortable"
                    :items="valueTypeItems(index)"
                    hide-details="auto"
                  />
                </v-col>
                <v-col cols="12" md="3">
                  <v-text-field
                    v-if="binding.valueType === 'BIZ_DATE'"
                    v-model="binding.format"
                    label="日期格式"
                    placeholder="yyyyMMdd"
                    variant="outlined"
                    density="comfortable"
                    hide-details="auto"
                  />
                  <v-text-field
                    v-else
                    v-model="binding.value"
                    label="固定值"
                    variant="outlined"
                    density="comfortable"
                    hide-details="auto"
                  />
                </v-col>
                <v-col cols="12" md="2">
                  <v-select
                    v-model="table.targetOptions.hdfs.partitionCreationMode"
                    label="分区创建"
                    variant="outlined"
                    density="comfortable"
                    :items="[
                      { title: '平台创建', value: 'PLATFORM' },
                      { title: '外部创建', value: 'EXTERNAL' },
                    ]"
                    hide-details="auto"
                  />
                </v-col>
                <v-col cols="12" md="1" class="d-flex align-center">
                  <v-btn
                    icon="mdi-delete-outline"
                    variant="text"
                    color="error"
                    aria-label="删除分区绑定"
                    @click="removePartitionBinding(index)"
                  />
                </v-col>
              </v-row>
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
              placeholder="例如 logdate（分区式属性，不参与主键）；为空表示不承载日期"
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
  import { HDFS_COMPRESS_FORMATS } from '@/utils';
  import { targetFamily } from '@/utils/target-options';
  import type { HdfsPartitionBinding, VEtlWithSource } from '@/types/database';
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
  const partitionBindings = computed(() => table.value.targetOptions.hdfs.partitionBindings || []);

  // Why: the target contract rejects more than one BIZ_DATE partition.
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
    const hdfs = table.value.targetOptions.hdfs;
    if (!hdfs.partitionBindings) {
      hdfs.partitionBindings = [];
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

  const removePartitionBinding = (index: number) => {
    partitionBindings.value.splice(index, 1);
  };
</script>

<style lang="scss" scoped>
  @use './detail-shared' as *;

  .empty-partitions {
    margin-bottom: 12px;
    color: rgba(var(--v-theme-on-surface), 0.62);
    font-size: 0.86rem;
  }
</style>

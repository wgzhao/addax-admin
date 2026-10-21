<template>
  <v-card flat class="ds-card overview-card">
    <v-card-text class="overview-body">
      <div class="overview-head">
        <div>
          <h2 class="section-title">执行配置</h2>
          <p class="section-copy">日常采集使用的目标、调度和运行策略。</p>
        </div>
        <v-chip size="small" variant="tonal" color="primary">
          {{ writeModeLabel }}
        </v-chip>
      </div>

      <dl class="overview-grid">
        <div v-for="item in overviewItems" :key="item.label" class="overview-item">
          <dt>{{ item.label }}</dt>
          <dd>{{ item.value }}</dd>
        </div>
      </dl>
    </v-card-text>
  </v-card>

  <v-card flat class="ds-card overview-card">
    <v-card-text class="overview-body">
      <div class="overview-head">
        <div>
          <h2 class="section-title">抽取与存储</h2>
          <p class="section-copy">源端过滤条件和目标端落表方式。</p>
        </div>
        <div class="help-actions">
          <v-btn
            size="x-small"
            variant="text"
            color="info"
            prepend-icon="mdi-information-outline"
            @click="$emit('show-placeholder-help')"
          >
            动态命名
          </v-btn>
          <v-btn
            size="x-small"
            variant="text"
            color="info"
            prepend-icon="mdi-filter-outline"
            @click="$emit('show-filter-help')"
          >
            过滤规则
          </v-btn>
        </div>
      </div>

      <div class="filter-block">
        <div class="detail-label">过滤规则</div>
        <code v-if="table.filter" class="code-value">{{ table.filter }}</code>
        <span v-else class="empty-value">未设置，按源表全量读取</span>
      </div>

      <dl class="overview-grid overview-grid--compact">
        <div v-for="item in storageItems" :key="item.label" class="overview-item">
          <dt>{{ item.label }}</dt>
          <dd>{{ item.value }}</dd>
        </div>
      </dl>
    </v-card-text>
  </v-card>

  <v-card flat class="ds-card overview-card overview-card--advanced">
    <v-card-text class="overview-body">
      <div class="overview-head">
        <div>
          <h2 class="section-title">高级配置</h2>
          <p class="section-copy">插件参数与人工备注，仅在排障或定制时需要关注。</p>
        </div>
      </div>

      <v-expansion-panels variant="accordion" flat>
        <v-expansion-panel title="插件配置" value="plugins">
          <v-expansion-panel-text>
            <div class="json-block">
              <div class="detail-label">读取插件配置</div>
              <pre>{{ readerPluginConfigText || '{}' }}</pre>
            </div>
            <div class="json-block">
              <div class="detail-label">写入插件配置</div>
              <pre>{{ writerPluginConfigText || '{}' }}</pre>
            </div>
          </v-expansion-panel-text>
        </v-expansion-panel>
        <v-expansion-panel title="备注" value="remark">
          <v-expansion-panel-text>
            <p v-if="table.remark" class="remark-value">{{ table.remark }}</p>
            <p v-else class="empty-value">暂无备注</p>
          </v-expansion-panel-text>
        </v-expansion-panel>
      </v-expansion-panels>
    </v-card-text>
  </v-card>
</template>

<script setup lang="ts">
  import { computed } from 'vue';
  import type { VEtlWithSource } from '@/types/database';
  import { targetFamily } from '@/utils/target-options';
  import { scheduleLabel, statusLabel } from './table-labels';

  const props = defineProps<{
    table: VEtlWithSource;
    readerPluginConfigText: string;
    writerPluginConfigText: string;
  }>();

  defineEmits(['show-placeholder-help', 'show-filter-help']);

  const writeModeLabel = computed(() => {
    const labels: Record<string, string> = {
      overwrite: '覆盖写入',
      append: '追加写入',
      nonConflict: '无冲突追加',
    };
    return labels[props.table.writeMode] || props.table.writeMode || '未设置';
  });

  const overallState = computed(() => {
    if (props.table.retiredAt) return '已退役';
    if (props.table.enabled === false) return '采集源已停用';
    return statusLabel(props.table);
  });

  const targetName = computed(() => {
    if (props.table.targetName) {
      return props.table.targetType
        ? `${props.table.targetName} (${props.table.targetType})`
        : props.table.targetName;
    }
    return '未选择目标端';
  });

  const overviewItems = computed(() => [
    { label: '当前状态', value: overallState.value },
    { label: '目标端', value: targetName.value },
    { label: '调度时间', value: scheduleLabel(props.table) },
    {
      label: '最大运行时长',
      value: props.table.maxRuntime ? `${props.table.maxRuntime} 秒` : '默认 2000 秒',
    },
    {
      label: '剩余重试次数',
      value:
        props.table.retryCnt === undefined || props.table.retryCnt === null
          ? '-'
          : String(props.table.retryCnt),
    },
    { label: '自动切分', value: props.table.autoPk ? '已启用' : '未启用' },
  ]);

  const storageItems = computed(() => {
    if (targetFamily(props.table.targetType) === 'rdbms') {
      return [
        { label: '目标端类型', value: '关系型数据库' },
        {
          label: '快照业务日期列',
          value: props.table.targetOptions.rdbms.businessDateColumn || '不承载日期',
        },
        { label: '切分字段', value: props.table.splitPk || '自动获取' },
        {
          label: '目标表主键',
          value: props.table.targetOptions.rdbms.createPrimaryKey ? '按源端主键创建' : '不创建',
        },
      ];
    }

    return [
      { label: '目标端类型', value: 'Hive / HDFS' },
      { label: '分区字段', value: props.table.targetOptions.hdfs.partName || '非分区表' },
      { label: '分区格式', value: props.table.targetOptions.hdfs.partFormat || '-' },
      { label: '切分字段', value: props.table.splitPk || '自动获取' },
      { label: '存储格式', value: props.table.targetOptions.hdfs.storageFormat || '继承系统设置' },
      { label: '压缩格式', value: props.table.targetOptions.hdfs.compressFormat || '继承系统设置' },
    ];
  });
</script>

<style scoped>
  .overview-body {
    display: flex;
    flex-direction: column;
    gap: 18px;
    padding: 18px;
  }

  .overview-head {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 16px;
  }

  .section-title {
    margin: 0;
    color: rgb(var(--v-theme-on-surface));
    font-size: 1rem;
    font-weight: 700;
    line-height: 1.45;
  }

  .section-copy {
    margin: 3px 0 0;
    color: rgba(var(--v-theme-on-surface), 0.66);
    font-size: 0.82rem;
    line-height: 1.5;
  }

  .overview-grid {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
    gap: 1px;
    overflow: hidden;
    margin: 0;
    border: 1px solid var(--ds-border-default);
    border-radius: 14px;
    background: var(--ds-border-default);
  }

  .overview-grid--compact {
    grid-template-columns: repeat(auto-fit, minmax(165px, 1fr));
  }

  .overview-item {
    min-width: 0;
    padding: 14px 16px;
    background: rgb(var(--v-theme-surface));
  }

  .overview-item dt,
  .detail-label {
    margin: 0 0 5px;
    color: rgba(var(--v-theme-on-surface), 0.68);
    font-size: 0.78rem;
    font-weight: 600;
    line-height: 1.4;
  }

  .overview-item dd {
    margin: 0;
    color: rgb(var(--v-theme-on-surface));
    font-size: 0.94rem;
    font-weight: 600;
    line-height: 1.55;
    overflow-wrap: anywhere;
  }

  .filter-block {
    display: flex;
    flex-direction: column;
    gap: 8px;
  }

  .code-value,
  pre {
    display: block;
    max-width: 100%;
    overflow-x: auto;
    margin: 0;
    padding: 12px 14px;
    border: 1px solid rgba(var(--v-theme-on-surface), 0.09);
    border-radius: 12px;
    background: rgba(var(--v-theme-on-surface), 0.035);
    color: rgb(var(--v-theme-on-surface));
    font-family: 'SF Mono', 'Monaco', 'Inconsolata', 'Roboto Mono', 'Consolas', monospace;
    font-size: 0.82rem;
    line-height: 1.65;
    white-space: pre-wrap;
    overflow-wrap: anywhere;
  }

  .empty-value {
    color: rgba(var(--v-theme-on-surface), 0.58);
    font-size: 0.86rem;
  }

  .help-actions {
    display: flex;
    flex-wrap: wrap;
    justify-content: flex-end;
    gap: 4px;
  }

  .overview-card--advanced :deep(.v-expansion-panel) {
    background: transparent;
  }

  .overview-card--advanced :deep(.v-expansion-panel-title) {
    min-height: 48px;
    padding-inline: 0;
  }

  .overview-card--advanced :deep(.v-expansion-panel-text__wrapper) {
    padding: 0 0 12px;
  }

  .json-block + .json-block {
    margin-top: 14px;
  }

  .remark-value {
    margin: 0;
    color: rgba(var(--v-theme-on-surface), 0.8);
    line-height: 1.75;
    white-space: pre-wrap;
  }

  @media (max-width: 760px) {
    .overview-head {
      flex-direction: column;
      gap: 10px;
    }

    .help-actions {
      justify-content: flex-start;
    }

    .overview-grid,
    .overview-grid--compact {
      grid-template-columns: repeat(2, minmax(0, 1fr));
    }
  }
</style>

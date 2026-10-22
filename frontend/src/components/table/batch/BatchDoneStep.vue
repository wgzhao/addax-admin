<template>
  <div class="success-shell">
    <v-icon size="56" :color="failedCount ? 'warning' : 'success'">
      {{ failedCount ? 'mdi-alert-circle-check-outline' : 'mdi-check-decagram-outline' }}
    </v-icon>
    <div class="success-title">
      {{
        failedCount
          ? `已创建 ${createdCount} 张，${failedCount} 张需要处理`
          : `已创建 ${createdCount} 张表`
      }}
    </div>
    <div class="success-desc">
      {{
        failedCount
          ? '成功的采集定义已经完成初始化；失败项保留了具体原因，可返回列表处理后重新添加。'
          : '目标表、字段映射和采集任务均已按确认的预览完成初始化。'
      }}
    </div>
    <div class="success-meta">
      <v-chip size="small" variant="tonal" color="success">目标库 {{ targetDb || '-' }}</v-chip>
      <v-chip size="small" variant="outlined">完成 {{ createdCount }}</v-chip>
      <v-chip v-if="failedCount" size="small" variant="tonal" color="error">
        失败 {{ failedCount }}
      </v-chip>
    </div>
    <div v-if="results.length" class="result-list">
      <div v-for="result in results" :key="result.sourceTable" class="result-row">
        <v-icon size="18" :color="result.status === 'CREATED' ? 'success' : 'error'">
          {{ result.status === 'CREATED' ? 'mdi-check-circle' : 'mdi-alert-circle' }}
        </v-icon>
        <span class="result-name">{{ result.sourceTable }}</span>
        <v-icon size="14" color="primary">mdi-arrow-right</v-icon>
        <span class="result-target">{{ result.targetTable }}</span>
        <span class="result-message">{{ result.message }}</span>
      </div>
    </div>
    <div class="success-actions">
      <v-btn variant="text" @click="$emit('back')">返回采集表</v-btn>
      <v-btn color="primary" variant="flat" @click="$emit('restart')">继续新增</v-btn>
    </div>
  </div>
</template>

<script setup lang="ts">
  import { computed } from 'vue';
  import type { CollectionPlanCommitResult } from '@/types/database';

  const props = defineProps<{
    results: CollectionPlanCommitResult[];
    targetDb: string;
  }>();
  defineEmits(['restart', 'back']);

  const createdCount = computed(
    () => props.results.filter(result => result.status === 'CREATED').length
  );
  const failedCount = computed(
    () => props.results.filter(result => result.status === 'FAILED').length
  );
</script>

<style scoped>
  .success-shell {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 12px;
    min-height: 420px;
    padding: 32px 20px;
    text-align: center;
  }

  .success-title {
    font-size: 1.18rem;
    font-weight: 700;
    color: rgb(var(--v-theme-on-surface));
  }

  .success-desc {
    max-width: 560px;
    color: rgba(var(--v-theme-on-surface), 0.66);
    line-height: 1.7;
  }

  .result-list {
    display: flex;
    width: min(860px, 100%);
    max-height: 280px;
    flex-direction: column;
    overflow: auto;
    border: 1px solid var(--ds-border-subtle);
    border-radius: 14px;
    background: rgba(var(--v-theme-surface), 0.8);
    text-align: left;
  }

  .result-row {
    display: grid;
    grid-template-columns: 22px minmax(130px, 1fr) 18px minmax(150px, 1fr) minmax(180px, 1.4fr);
    align-items: center;
    gap: 8px;
    padding: 10px 14px;
    font-size: 0.78rem;
  }

  .result-row + .result-row {
    border-top: 1px solid var(--ds-border-subtle);
  }

  .result-name,
  .result-target {
    overflow: hidden;
    font-family: 'SF Mono', 'Monaco', 'Consolas', monospace;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .result-message {
    overflow: hidden;
    color: var(--ds-text-2);
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .success-meta,
  .success-actions {
    display: flex;
    align-items: center;
    gap: 10px;
    flex-wrap: wrap;
    justify-content: center;
  }
</style>

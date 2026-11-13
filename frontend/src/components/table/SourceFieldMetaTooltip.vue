<template>
  <v-tooltip v-if="meta" location="top" max-width="360">
    <template #activator="{ props: activatorProps }">
      <v-icon
        v-bind="activatorProps"
        size="14"
        icon="mdi-information-outline"
        class="ml-1 text-medium-emphasis"
        aria-label="字段采样信息"
      />
    </template>
    <div class="source-meta-tooltip">
      <div class="source-meta-tooltip__title">采样 {{ sampleSize }} 条文档</div>
      <div v-for="line in lines" :key="line.label" class="source-meta-tooltip__line">
        <span>{{ line.label }}</span>
        <span>{{ line.count }} ({{ line.percent }})</span>
      </div>
      <div v-if="lines.length === 0" class="source-meta-tooltip__line">无采样数据</div>
    </div>
  </v-tooltip>
</template>

<script setup lang="ts">
  import { computed } from 'vue';
  import type { SourceFieldMeta } from '@/types/database';

  interface MetaLine {
    label: string;
    count: number;
    percent: string;
  }

  const props = defineProps<{ meta?: SourceFieldMeta | null }>();

  const sampleSize = computed(() => props.meta?.sampleSize || 0);

  const pct = (count: number, size: number) => {
    if (size <= 0) return '0%';
    return `${(Math.round((count * 1000) / size) / 10).toFixed(1).replace(/\.0$/, '')}%`;
  };

  const lines = computed<MetaLine[]>(() => {
    const meta = props.meta;
    if (!meta) return [];
    const result: MetaLine[] = Object.entries(meta.types || {}).map(([label, count]) => ({
      label,
      count,
      percent: pct(count, meta.sampleSize),
    }));
    if (meta.nullCount > 0) {
      result.push({
        label: 'null',
        count: meta.nullCount,
        percent: pct(meta.nullCount, meta.sampleSize),
      });
    }
    if (meta.missing > 0) {
      result.push({
        label: '缺失',
        count: meta.missing,
        percent: pct(meta.missing, meta.sampleSize),
      });
    }
    return result;
  });
</script>

<style scoped>
  .source-meta-tooltip {
    font-size: 12px;
    line-height: 1.5;
  }

  .source-meta-tooltip__title {
    font-weight: 600;
    margin-bottom: 4px;
  }

  .source-meta-tooltip__line {
    display: flex;
    justify-content: space-between;
    gap: 16px;
  }
</style>

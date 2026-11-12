<template>
  <SectionCard
    title="数据源贡献"
    :subtitle="`近 ${rangeDays} 天 · Top 8 + 其他`"
    icon="mdi-chart-bar"
  >
    <SkeletonBlock v-if="loading" :height="280" />
    <ErrorState v-else-if="error" :message="error" @retry="$emit('retry')" />
    <div v-else-if="items.length === 0" class="contribution-empty">暂无数据源贡献数据</div>
    <ul v-else class="contribution-list" :data-chart-state="chartState">
      <li v-for="(item, index) in items" :key="item.key" class="contribution-row">
        <div class="contribution-meta">
          <span class="contribution-name" :title="item.name">{{ item.name }}</span>
          <span class="contribution-value">{{ format(item.gib) }}</span>
        </div>
        <div class="contribution-track">
          <span
            class="contribution-bar"
            :class="{
              'contribution-bar--top': index === 0,
              'contribution-bar--other': item.key === 'OTHER',
            }"
            :style="{ width: `${widthFor(item.gib)}%` }"
          />
        </div>
      </li>
    </ul>
    <template #footer>
      按采集源汇总；尾部数据折叠为「其他」，避免颜色数量超过可辨识上限。
    </template>
  </SectionCard>
</template>

<script setup lang="ts">
  import { computed } from 'vue';
  import SectionCard from '@/components/common/SectionCard.vue';
  import SkeletonBlock from '@/components/common/SkeletonBlock.vue';
  import ErrorState from '@/components/common/ErrorState.vue';

  const props = withDefaults(
    defineProps<{
      items: { key: string; name: string; gib: number }[];
      rangeDays: number;
      loading?: boolean;
      error?: string | null;
    }>(),
    { loading: false, error: null }
  );

  defineEmits<{ retry: [] }>();

  const chartState = computed(() =>
    props.loading ? 'loading' : props.error ? 'error' : props.items.length ? 'ready' : 'empty'
  );

  const max = computed(() => Math.max(...props.items.map(item => item.gib), 1));
  const widthFor = (value: number) => Math.max((value / max.value) * 100, value > 0 ? 1.5 : 0);

  const format = (value: number) => {
    if (value >= 1024) return `${(value / 1024).toFixed(2)} TiB`;
    return `${value.toFixed(2)} GiB`;
  };
</script>

<style scoped>
  .contribution-list {
    display: flex;
    flex-direction: column;
    gap: 10px;
    margin: 0;
    padding: 0;
    list-style: none;
  }

  .contribution-row {
    display: flex;
    flex-direction: column;
    gap: 5px;
  }

  .contribution-meta {
    display: flex;
    align-items: baseline;
    justify-content: space-between;
    gap: 12px;
    font-size: 12px;
  }

  .contribution-name {
    color: var(--ds-text-2);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .contribution-value {
    color: rgb(var(--v-theme-on-surface));
    font-weight: 600;
    font-variant-numeric: tabular-nums;
    white-space: nowrap;
  }

  .contribution-track {
    height: 8px;
    border-radius: 999px;
    background: rgba(var(--v-theme-on-surface), 0.06);
    overflow: hidden;
  }

  .contribution-bar {
    display: block;
    height: 100%;
    border-radius: 999px;
    background: rgba(var(--v-theme-primary), 0.62);
    transition: width var(--ds-duration-base) var(--ds-ease);
  }

  .contribution-bar--top {
    background: rgb(var(--v-theme-primary));
  }

  .contribution-bar--other {
    background: rgba(var(--v-theme-on-surface), 0.22);
  }

  .contribution-empty {
    display: flex;
    align-items: center;
    justify-content: center;
    height: 280px;
    font-size: 13px;
    color: var(--ds-text-3);
  }
</style>
